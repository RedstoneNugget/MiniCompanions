package de.minicompanions.streamer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anonyme, rein lesende Verbindung zum Kick-Chat: Kanalname -> Chatroom-ID (öffentliche Kanal-Abfrage),
 * dann der öffentliche Pusher-WebSocket, den auch die Kick-Webseite nutzt. Es wird nie geschrieben.
 */
public final class KickChatSource extends AbstractChatSource {
    /** Öffentlicher Pusher-Schlüssel der Kick-Webseite (kein Geheimnis). */
    static final String PUSHER_URL = "wss://ws-us2.pusher.com/app/32cbd69e4b950bf97679?protocol=7&client=js&version=8.4.0&flash=false";
    private static final Pattern SLUG = Pattern.compile("[a-z0-9_-]{2,30}");
    private static final Pattern EMOTE = Pattern.compile("\\[emote:\\d+:([^\\]]{1,40})]");

    private volatile WebSocket ws;
    /** Zuordnung Nachrichten-ID -> Login (Kick meldet gelöschte Nachrichten nur mit ID). */
    private final Map<String, String> recentIds = new LinkedHashMap<>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 300;
        }
    };

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.KICK;
    }

    @Override
    protected String normalize(String input) {
        if (input == null) {
            return null;
        }
        String s = input.strip().toLowerCase(Locale.ROOT);
        int i = s.indexOf("kick.com/");
        if (i >= 0) {
            s = s.substring(i + 9);
        }
        if (s.startsWith("@")) {
            s = s.substring(1);
        }
        int cut = s.indexOf('/');
        if (cut >= 0) {
            s = s.substring(0, cut);
        }
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        return SLUG.matcher(s).matches() ? s : null;
    }

    @Override
    protected void session(String slug, Consumer<ChatEvent> sink) throws Exception {
        long chatroom = lookupChatroom(slug);
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>(2000);
        StringBuilder partial = new StringBuilder();
        WebSocket socket = ChatHttp.CLIENT.newWebSocketBuilder()
                .header("User-Agent", ChatHttp.USER_AGENT)
                .header("Origin", "https://kick.com")
                .buildAsync(URI.create(PUSHER_URL), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                        partial.append(data);
                        if (last) {
                            if (partial.length() <= 200_000) {
                                inbox.offer(partial.toString());
                            }
                            partial.setLength(0);
                        }
                        w.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
                        inbox.offer("\u0000closed:" + code);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket w, Throwable error) {
                        inbox.offer("\u0000error:" + error.getClass().getSimpleName());
                    }
                }).get(15, TimeUnit.SECONDS);
        ws = socket;
        long lastRx = System.currentTimeMillis();
        boolean pinged = false;
        while (running()) {
            String msg = inbox.poll(5, TimeUnit.SECONDS);
            long now = System.currentTimeMillis();
            if (msg == null) {
                if (now - lastRx > 150_000) {
                    throw new IOException("timeout");
                }
                if (now - lastRx > 60_000 && !pinged) {
                    socket.sendText("{\"event\":\"pusher:ping\",\"data\":{}}", true);
                    pinged = true;
                }
                continue;
            }
            if (msg.startsWith("\u0000")) {
                throw new IOException(msg.substring(1));
            }
            lastRx = now;
            pinged = false;
            JsonObject frame;
            try {
                frame = JsonParser.parseString(msg).getAsJsonObject();
            } catch (RuntimeException e) {
                continue;
            }
            String event = str(frame, "event");
            switch (event) {
                case "pusher:connection_established" -> socket.sendText(
                        "{\"event\":\"pusher:subscribe\",\"data\":{\"auth\":\"\",\"channel\":\"chatrooms." + chatroom + ".v2\"}}", true);
                case "pusher_internal:subscription_succeeded" -> markConnected();
                case "pusher:ping" -> socket.sendText("{\"event\":\"pusher:pong\",\"data\":{}}", true);
                case "pusher:error" -> log.warn("[MiniCompanions] Kick-Pusher-Fehler: {}", shorten(msg, 200));
                default -> {
                    for (ChatEvent ev : parseEvent(event, frame.get("data"), recentIds)) {
                        sink.accept(ev);
                    }
                }
            }
        }
    }

    /** Kanalname -> Chatroom-ID über die öffentliche Kanal-Abfrage der Kick-Webseite. */
    private long lookupChatroom(String slug) throws IOException, InterruptedException {
        ChatHttp.Response r = ChatHttp.get("https://kick.com/api/v2/channels/" + slug, "Accept", "application/json");
        if (r.status() == 404) {
            throw new FatalException("Kick-Kanal \"" + slug + "\" nicht gefunden");
        }
        if (r.status() == 403 || r.status() == 429) {
            throw new IOException("Kick blockiert die Kanal-Abfrage gerade (HTTP " + r.status() + ")");
        }
        if (r.status() != 200) {
            throw new IOException("HTTP " + r.status());
        }
        try {
            JsonObject o = JsonParser.parseString(r.text()).getAsJsonObject();
            return o.getAsJsonObject("chatroom").get("id").getAsLong();
        } catch (RuntimeException e) {
            throw new IOException("Kick-Antwort unbekannt");
        }
    }

    /** Ein Pusher-Ereignis in Chat-Ereignisse übersetzen (öffentlich für Tests). */
    static List<ChatEvent> parseEvent(String event, JsonElement dataEl, Map<String, String> recentIds) {
        List<ChatEvent> out = new ArrayList<>();
        JsonObject data;
        try {
            // Pusher liefert "data" als JSON-Text
            data = dataEl == null ? null : dataEl.isJsonPrimitive()
                    ? JsonParser.parseString(dataEl.getAsString()).getAsJsonObject() : dataEl.getAsJsonObject();
        } catch (RuntimeException e) {
            return out;
        }
        if (data == null) {
            return out;
        }
        switch (event) {
            case "App\\Events\\ChatMessageEvent" -> {
                JsonObject sender = obj(data, "sender");
                if (sender == null) {
                    return out;
                }
                String slug = str(sender, "slug");
                String username = str(sender, "username");
                String name = !slug.isEmpty() ? slug : username;
                String type = str(data, "type");
                if (name.isEmpty() || !(type.isEmpty() || "message".equals(type) || "reply".equals(type))) {
                    return out;
                }
                String login = ChatPlatform.KICK.login(name.replaceAll("[^A-Za-z0-9_.-]", "_"));
                String id = str(data, "id");
                JsonObject identity = obj(sender, "identity");
                boolean sub = false;
                boolean vip = false;
                boolean mod = false;
                boolean bc = false;
                Integer color = null;
                if (identity != null) {
                    color = parseColor(str(identity, "color"));
                    JsonArray badges = identity.has("badges") && identity.get("badges").isJsonArray() ? identity.getAsJsonArray("badges") : null;
                    if (badges != null) {
                        for (JsonElement b : badges) {
                            if (!b.isJsonObject()) {
                                continue;
                            }
                            switch (str(b.getAsJsonObject(), "type")) {
                                case "subscriber", "founder", "og" -> sub = true;
                                case "vip" -> vip = true;
                                case "moderator" -> mod = true;
                                case "broadcaster" -> bc = true;
                                default -> {
                                }
                            }
                        }
                    }
                }
                if (!id.isEmpty() && recentIds != null) {
                    synchronized (recentIds) {
                        recentIds.put(id, login);
                    }
                }
                out.add(new ChatEvent.Message(ChatPlatform.KICK, login, username.isEmpty() ? name : username,
                        cleanEmotes(str(data, "content")), new ChatRoles(sub, vip, mod, bc), color, id.isEmpty() ? null : id));
            }
            case "App\\Events\\MessageDeletedEvent" -> {
                JsonObject m = obj(data, "message");
                String id = m == null ? "" : str(m, "id");
                if (!id.isEmpty()) {
                    String login = null;
                    if (recentIds != null) {
                        synchronized (recentIds) {
                            login = recentIds.get(id);
                        }
                    }
                    if (login != null) {
                        out.add(new ChatEvent.MessageDeleted(login, id));
                    }
                }
            }
            case "App\\Events\\UserBannedEvent" -> {
                JsonObject u = obj(data, "user");
                String slug = u == null ? "" : !str(u, "slug").isEmpty() ? str(u, "slug") : str(u, "username");
                if (!slug.isEmpty()) {
                    out.add(new ChatEvent.UserCleared(ChatPlatform.KICK.login(slug.replaceAll("[^A-Za-z0-9_.-]", "_"))));
                }
            }
            case "App\\Events\\ChatroomClearEvent" -> out.add(new ChatEvent.ChatCleared());
            default -> {
            }
        }
        return out;
    }

    /** Kick-Emotes "[emote:123:KEKW]" als reinen Namen anzeigen. */
    static String cleanEmotes(String content) {
        Matcher m = EMOTE.matcher(content);
        return m.replaceAll(r -> Matcher.quoteReplacement(r.group(1)));
    }

    private static Integer parseColor(String s) {
        if (s == null || !s.matches("#[0-9A-Fa-f]{6}")) {
            return null;
        }
        return Integer.parseInt(s.substring(1), 16);
    }

    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    @Override
    protected void abortSession() {
        WebSocket w = ws;
        ws = null;
        if (w != null) {
            w.abort();
        }
    }
}

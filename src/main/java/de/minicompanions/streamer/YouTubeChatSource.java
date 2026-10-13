package de.minicompanions.streamer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anonyme, rein lesende Verbindung zum YouTube-Livechat, so wie ihn der Popout-Chat im Browser lädt:
 * Kanal (@Handle, Kanal-ID oder Video-Link) -> aktueller Livestream -> Chat-Fortsetzungen abfragen.
 * Ist der Kanal gerade nicht live, wird es jede Minute erneut versucht.
 */
public final class YouTubeChatSource extends AbstractChatSource {
    private static final String YT = "https://www.youtube.com/";
    /** Cookie gegen die EU-Zustimmungsseite (nur Ablehnung von Personalisierung). */
    private static final String CONSENT = "SOCS=CAI; CONSENT=YES+1";
    private static final Pattern HANDLE = Pattern.compile("[A-Za-z0-9._-]{3,30}");
    private static final Pattern CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_-]{22}");
    private static final Pattern CANONICAL = Pattern.compile("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})\"");

    private final Map<String, String> recentIds = new LinkedHashMap<>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 300;
        }
    };

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.YOUTUBE;
    }

    /** "h:handle", "c:UC…" oder "v:videoId". */
    @Override
    protected String normalize(String input) {
        if (input == null) {
            return null;
        }
        String s = input.strip();
        Matcher m;
        if ((m = Pattern.compile("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|live/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})").matcher(s)).find()) {
            return "v:" + m.group(1);
        }
        if ((m = Pattern.compile("youtube\\.com/channel/(UC[A-Za-z0-9_-]{22})").matcher(s)).find()) {
            return "c:" + m.group(1);
        }
        if ((m = Pattern.compile("youtube\\.com/@([A-Za-z0-9._-]{3,30})").matcher(s)).find()) {
            return "h:" + m.group(1);
        }
        if (CHANNEL_ID.matcher(s).matches()) {
            return "c:" + s;
        }
        if (s.startsWith("@")) {
            s = s.substring(1);
        }
        return HANDLE.matcher(s).matches() ? "h:" + s : null;
    }

    @Override
    protected void session(String target, Consumer<ChatEvent> sink) throws Exception {
        String videoId = target.startsWith("v:") ? target.substring(2) : findLiveVideo(target);
        ChatHttp.Response page = ChatHttp.get(YT + "live_chat?is_popout=1&v=" + videoId, "Cookie", CONSENT);
        if (page.status() != 200) {
            throw new IOException("HTTP " + page.status());
        }
        String html = page.text();
        JsonObject data = extractJson(html, "ytInitialData");
        JsonObject chat = data == null ? null : path(data, "contents", "liveChatRenderer");
        if (chat == null) {
            throw new OfflineException("YouTube-Chat nicht verfügbar (Stream nicht live?)");
        }
        String apiKey = find(html, "\"INNERTUBE_API_KEY\":\"([A-Za-z0-9_-]{10,80})\"");
        String version = find(html, "\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]{4,30})\"");
        if (version == null) {
            version = find(html, "\"clientVersion\":\"([0-9.]{4,30})\"");
        }
        if (version == null) {
            version = "2.20260101.00.00";
        }
        String continuation = liveChatContinuation(chat);
        if (continuation == null) {
            throw new OfflineException("YouTube-Chat nicht verfügbar");
        }
        markConnected();
        boolean first = true;
        String url = YT + "youtubei/v1/live_chat/get_live_chat?prettyPrint=false" + (apiKey == null ? "" : "&key=" + apiKey);
        while (running()) {
            String body = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"" + version
                    + "\",\"hl\":\"de\",\"gl\":\"DE\"}},\"continuation\":" + quote(continuation) + "}";
            ChatHttp.Response r = ChatHttp.postJson(url, body, "Cookie", CONSENT, "Origin", "https://www.youtube.com");
            if (r.status() != 200) {
                throw new IOException("HTTP " + r.status());
            }
            JsonObject root = JsonParser.parseString(r.text()).getAsJsonObject();
            JsonObject cont = path(root, "continuationContents", "liveChatContinuation");
            if (cont == null) {
                throw new OfflineException("YouTube-Stream beendet");
            }
            JsonArray actions = cont.has("actions") ? cont.getAsJsonArray("actions") : new JsonArray();
            List<ChatEvent> events = parseActions(actions, recentIds);
            if (!first) {
                // Die erste Antwort enthält den bisherigen Chatverlauf - alte Befehle nicht ausführen
                events.forEach(sink);
            }
            first = false;
            long timeout = 3000;
            String next = null;
            JsonArray conts = cont.has("continuations") ? cont.getAsJsonArray("continuations") : null;
            if (conts != null && !conts.isEmpty() && conts.get(0).isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : conts.get(0).getAsJsonObject().entrySet()) {
                    if (e.getValue().isJsonObject()) {
                        JsonObject c = e.getValue().getAsJsonObject();
                        if (c.has("continuation")) {
                            next = c.get("continuation").getAsString();
                        }
                        if (c.has("timeoutMs")) {
                            timeout = c.get("timeoutMs").getAsLong();
                        }
                    }
                }
            }
            if (next == null) {
                throw new OfflineException("YouTube-Stream beendet");
            }
            continuation = next;
            Thread.sleep(Math.max(1500, Math.min(6000, timeout)));
        }
    }

    /** Kanal -> Video-ID des laufenden Livestreams (sonst OfflineException). */
    private String findLiveVideo(String target) throws IOException, InterruptedException {
        String path = target.startsWith("c:") ? "channel/" + target.substring(2) : "@" + target.substring(2);
        ChatHttp.Response r = ChatHttp.get(YT + path + "/live", "Cookie", CONSENT);
        if (r.status() == 404) {
            throw new FatalException("YouTube-Kanal \"" + target.substring(2) + "\" nicht gefunden");
        }
        if (r.status() != 200) {
            throw new IOException("HTTP " + r.status());
        }
        String vid = find(r.text(), CANONICAL);
        if (vid == null) {
            throw new OfflineException("YouTube-Kanal ist gerade nicht live");
        }
        return vid;
    }

    /** Fortsetzung für "Livechat" (alle Nachrichten) statt "Top-Chat", sonst die Standard-Fortsetzung. */
    static String liveChatContinuation(JsonObject chat) {
        JsonObject selector = path(chat, "header", "liveChatHeaderRenderer", "viewSelector", "sortFilterSubMenuRenderer");
        if (selector != null && selector.has("subMenuItems")) {
            JsonArray items = selector.getAsJsonArray("subMenuItems");
            if (items.size() > 1 && items.get(1).isJsonObject()) {
                JsonObject c = path(items.get(1).getAsJsonObject(), "continuation", "reloadContinuationData");
                if (c != null && c.has("continuation")) {
                    return c.get("continuation").getAsString();
                }
            }
        }
        JsonArray conts = chat.has("continuations") ? chat.getAsJsonArray("continuations") : null;
        if (conts != null && !conts.isEmpty() && conts.get(0).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : conts.get(0).getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("continuation")) {
                    return e.getValue().getAsJsonObject().get("continuation").getAsString();
                }
            }
        }
        return null;
    }

    /** YouTube-Aktionen in Chat-Ereignisse übersetzen (öffentlich für Tests). */
    static List<ChatEvent> parseActions(JsonArray actions, Map<String, String> recentIds) {
        List<ChatEvent> out = new ArrayList<>();
        for (JsonElement el : actions) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject a = el.getAsJsonObject();
            JsonObject item = path(a, "addChatItemAction", "item");
            if (item != null) {
                JsonObject m = item.has("liveChatTextMessageRenderer") ? item.getAsJsonObject("liveChatTextMessageRenderer")
                        : item.has("liveChatPaidMessageRenderer") ? item.getAsJsonObject("liveChatPaidMessageRenderer") : null;
                if (m != null) {
                    ChatEvent.Message msg = toMessage(m);
                    if (msg != null) {
                        if (msg.messageId() != null && recentIds != null) {
                            synchronized (recentIds) {
                                recentIds.put(msg.messageId(), msg.login());
                            }
                        }
                        out.add(msg);
                    }
                }
                continue;
            }
            String deletedId = str(path(a, "markChatItemAsDeletedAction"), "targetItemId");
            if (deletedId.isEmpty()) {
                deletedId = str(path(a, "removeChatItemAction"), "targetItemId");
            }
            if (!deletedId.isEmpty() && recentIds != null) {
                String login;
                synchronized (recentIds) {
                    login = recentIds.get(deletedId);
                }
                if (login != null) {
                    out.add(new ChatEvent.MessageDeleted(login, deletedId));
                }
                continue;
            }
            String author = str(path(a, "markChatItemsByAuthorAsDeletedAction"), "externalChannelId");
            if (author.isEmpty()) {
                author = str(path(a, "removeChatItemByAuthorAction"), "externalChannelId");
            }
            if (!author.isEmpty() && CHANNEL_ID.matcher(author).matches()) {
                out.add(new ChatEvent.UserCleared(ChatPlatform.YOUTUBE.login(author)));
            }
        }
        return out;
    }

    private static ChatEvent.Message toMessage(JsonObject m) {
        String channelId = str(m, "authorExternalChannelId");
        if (!CHANNEL_ID.matcher(channelId).matches()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        JsonObject message = m.has("message") && m.get("message").isJsonObject() ? m.getAsJsonObject("message") : null;
        if (message != null && message.has("runs")) {
            for (JsonElement r : message.getAsJsonArray("runs")) {
                if (!r.isJsonObject()) {
                    continue;
                }
                JsonObject run = r.getAsJsonObject();
                if (run.has("text")) {
                    text.append(run.get("text").getAsString());
                } else if (run.has("emoji") && run.get("emoji").isJsonObject()) {
                    JsonObject emoji = run.getAsJsonObject("emoji");
                    boolean custom = emoji.has("isCustomEmoji") && emoji.get("isCustomEmoji").getAsBoolean();
                    JsonArray shortcuts = emoji.has("shortcuts") ? emoji.getAsJsonArray("shortcuts") : null;
                    if (!custom && emoji.has("emojiId")) {
                        text.append(emoji.get("emojiId").getAsString());
                    } else if (shortcuts != null && !shortcuts.isEmpty()) {
                        text.append(shortcuts.get(0).getAsString());
                    }
                }
            }
        }
        if (text.isEmpty()) {
            return null;
        }
        String name = str(path(m, "authorName"), "simpleText");
        if (name.startsWith("@")) {
            name = name.substring(1);
        }
        boolean owner = false;
        boolean mod = false;
        boolean member = false;
        if (m.has("authorBadges")) {
            for (JsonElement b : m.getAsJsonArray("authorBadges")) {
                JsonObject br = b.isJsonObject() ? path(b.getAsJsonObject(), "liveChatAuthorBadgeRenderer") : null;
                if (br == null) {
                    continue;
                }
                String icon = str(path(br, "icon"), "iconType");
                if ("OWNER".equals(icon)) {
                    owner = true;
                } else if ("MODERATOR".equals(icon)) {
                    mod = true;
                } else if (br.has("customThumbnail")) {
                    member = true;
                }
            }
        }
        String id = str(m, "id");
        return new ChatEvent.Message(ChatPlatform.YOUTUBE, ChatPlatform.YOUTUBE.login(channelId), name.isEmpty() ? channelId : name,
                text.toString(), new ChatRoles(member, false, mod, owner), null, id.isEmpty() ? null : id);
    }

    /** Ein eingebettetes JSON-Objekt ("ytInitialData") aus der Seite lesen. */
    static JsonObject extractJson(String html, String name) {
        int i = html.indexOf(name);
        if (i < 0) {
            return null;
        }
        int start = html.indexOf('{', i);
        if (start < 0) {
            return null;
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(html.substring(start)));
            JsonElement e = JsonParser.parseReader(reader);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static JsonObject path(JsonObject o, String... keys) {
        JsonObject cur = o;
        for (String k : keys) {
            if (cur == null || !cur.has(k) || !cur.get(k).isJsonObject()) {
                return null;
            }
            cur = cur.getAsJsonObject(k);
        }
        return cur;
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private static String find(String s, String regex) {
        return find(s, Pattern.compile(regex));
    }

    private static String find(String s, Pattern p) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}

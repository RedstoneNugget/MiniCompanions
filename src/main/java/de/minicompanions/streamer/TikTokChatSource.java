package de.minicompanions.streamer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Rein lesende Verbindung zum TikTok-LIVE-Chat. TikTok verlangt für die Live-Verbindung eine Signatur;
 * die liefert der Dienst Euler Stream (kostenlos, ohne Konto; optional mit eigenem API-Schlüssel für
 * mehr Kontingent). Ablauf: Benutzername -> Raum-ID (TikTok) -> signierte Verbindung (Euler Stream)
 * -> TikToks Live-WebSocket (Protobuf). An Euler Stream wird nur die Raum-ID des Streams geschickt.
 * Es wird nie in den Chat geschrieben.
 */
public final class TikTokChatSource extends AbstractChatSource {
    private static final Pattern NAME = Pattern.compile("[a-z0-9._]{2,24}");
    private static final String SIGN_URL = "https://api.eulerstream.com/webcast/rooms/%s/connect?client=minicompanions&client_enter=true&platform=web";
    /** Optionaler Euler-Stream-Schlüssel (aus den Einstellungen). */
    static volatile String apiKey = "";

    private volatile WebSocket ws;
    private final Set<Long> seen = new LinkedHashSet<>();

    public static void setApiKey(String key) {
        apiKey = key == null ? "" : key.strip();
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.TIKTOK;
    }

    @Override
    protected String normalize(String input) {
        if (input == null) {
            return null;
        }
        String s = input.strip().toLowerCase(Locale.ROOT);
        int i = s.indexOf("tiktok.com/");
        if (i >= 0) {
            s = s.substring(i + 11);
        }
        if (s.startsWith("@")) {
            s = s.substring(1);
        }
        for (char c : new char[] {'/', '?'}) {
            int cut = s.indexOf(c);
            if (cut >= 0) {
                s = s.substring(0, cut);
            }
        }
        return NAME.matcher(s).matches() ? s : null;
    }

    @Override
    protected void session(String name, Consumer<ChatEvent> sink) throws Exception {
        long roomId = findLiveRoom(name);
        // Signierte Verbindung holen (enthält Server-Adresse, Parameter und Cookie)
        String url = String.format(Locale.ROOT, SIGN_URL, roomId);
        String key = apiKey;
        if (!key.isEmpty() && key.matches("[A-Za-z0-9_.-]{8,200}")) {
            url += "&apiKey=" + key;
        }
        ChatHttp.Response sign = ChatHttp.get(url, "Accept", "application/protobuf,application/json");
        if (sign.status() == 429) {
            throw new IOException("Signierdienst: Limit erreicht, neuer Versuch gleich");
        }
        if (sign.status() != 200) {
            String msg = "";
            try {
                JsonObject o = JsonParser.parseString(sign.text()).getAsJsonObject();
                msg = o.has("message") ? o.get("message").getAsString() : "";
            } catch (RuntimeException ignored) {
                // keine JSON-Antwort
            }
            throw new IOException("Signierdienst HTTP " + sign.status() + (msg.isEmpty() ? "" : ": " + shorten(msg, 100)));
        }
        FetchResult fr = FetchResult.parse(sign.body());
        if (fr.pushServer.isEmpty() || !fr.pushServer.startsWith("wss://")) {
            throw new IOException("keine Live-Verbindung erhalten");
        }
        String cookie = sign.header("x-set-tt-cookie");
        String wsUrl = fr.pushServer + "?" + params(roomId, fr) + "&version_code=270000";

        BlockingQueue<Object> inbox = new LinkedBlockingQueue<>(2000);
        ByteArrayOutputStream partial = new ByteArrayOutputStream();
        WebSocket.Builder b = ChatHttp.CLIENT.newWebSocketBuilder()
                .header("User-Agent", ChatHttp.USER_AGENT)
                .header("Origin", "https://www.tiktok.com");
        if (cookie != null && cookie.matches("[\\x20-\\x7E]{1,4000}")) {
            b.header("Cookie", cookie);
        }
        WebSocket socket = b.buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);
                if (partial.size() + chunk.length <= 4 * 1024 * 1024) {
                    partial.write(chunk, 0, chunk.length);
                }
                if (last) {
                    inbox.offer(partial.toByteArray());
                    partial.reset();
                }
                w.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
                inbox.offer("closed:" + code);
                return null;
            }

            @Override
            public void onError(WebSocket w, Throwable error) {
                inbox.offer("error:" + error.getClass().getSimpleName());
            }
        }).get(15, TimeUnit.SECONDS);
        ws = socket;
        socket.sendBinary(ByteBuffer.wrap(enterRoomFrame(roomId)), true);
        markConnected();
        long seq = 1;
        long nextHeartbeat = System.currentTimeMillis() + 10_000;
        long lastRx = System.currentTimeMillis();
        while (running()) {
            Object item = inbox.poll(1, TimeUnit.SECONDS);
            long now = System.currentTimeMillis();
            if (now >= nextHeartbeat) {
                socket.sendBinary(ByteBuffer.wrap(heartbeatFrame(roomId, seq++)), true);
                nextHeartbeat = now + 10_000;
            }
            if (item == null) {
                if (now - lastRx > 90_000) {
                    throw new IOException("timeout");
                }
                continue;
            }
            if (item instanceof String s) {
                throw new IOException(s);
            }
            lastRx = now;
            PushFrame frame;
            try {
                frame = PushFrame.parse((byte[]) item);
            } catch (IOException e) {
                continue;
            }
            if (!"msg".equals(frame.payloadType)) {
                continue;
            }
            FetchResult res;
            try {
                res = FetchResult.parse(gunzip(frame.payload));
            } catch (IOException e) {
                continue;
            }
            if (res.needAck && frame.logId != 0 && !res.internalExt.isEmpty()) {
                socket.sendBinary(ByteBuffer.wrap(ackFrame(frame.logId, res.internalExt)), true);
            }
            for (ChatEvent ev : toEvents(res, name, seen)) {
                sink.accept(ev);
            }
            if (res.streamEnded) {
                throw new OfflineException("TikTok-Stream beendet");
            }
        }
    }

    /** Benutzername -> Raum-ID, nur wenn das Konto gerade live ist. */
    private long findLiveRoom(String name) throws IOException, InterruptedException {
        ChatHttp.Response r = ChatHttp.get("https://www.tiktok.com/api-live/user/room/?aid=1988&sourceType=54&uniqueId="
                + URLEncoder.encode(name, StandardCharsets.UTF_8), "Accept", "application/json");
        if (r.status() != 200) {
            throw new IOException("HTTP " + r.status());
        }
        JsonObject o;
        try {
            o = JsonParser.parseString(r.text()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("TikTok-Antwort unbekannt");
        }
        int code = o.has("statusCode") ? o.get("statusCode").getAsInt() : 0;
        JsonObject data = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : null;
        JsonObject user = data != null && data.has("user") && data.get("user").isJsonObject() ? data.getAsJsonObject("user") : null;
        if (user == null) {
            if (code == 19881007 || code == 10221) {
                throw new FatalException("TikTok-Konto \"" + name + "\" nicht gefunden");
            }
            throw new OfflineException("TikTok-Konto ist gerade nicht live");
        }
        String room = user.has("roomId") ? user.get("roomId").getAsString() : "";
        int status = 0;
        if (data.has("liveRoom") && data.get("liveRoom").isJsonObject() && data.getAsJsonObject("liveRoom").has("status")) {
            status = data.getAsJsonObject("liveRoom").get("status").getAsInt();
        } else if (user.has("status")) {
            status = user.get("status").getAsInt();
        }
        if (!room.matches("\\d{5,25}") || status != 2) {
            throw new OfflineException("TikTok-Konto ist gerade nicht live");
        }
        return Long.parseLong(room);
    }

    private static String params(long roomId, FetchResult fr) {
        Map<String, String> p = new LinkedHashMap<>();
        String ua = ChatHttp.USER_AGENT;
        p.put("version_code", "180800");
        p.put("aid", "1988");
        p.put("app_language", "de-DE");
        p.put("app_name", "tiktok_web");
        p.put("browser_platform", "Win32");
        p.put("browser_language", "de-DE");
        p.put("browser_name", "Mozilla");
        p.put("browser_version", ua.substring(ua.indexOf('/') + 1));
        p.put("browser_online", "true");
        p.put("cookie_enabled", "true");
        p.put("tz_name", "Europe/Berlin");
        p.put("device_platform", "web");
        p.put("identity", "audience");
        p.put("live_id", "12");
        p.put("webcast_language", "de-DE");
        p.put("ws_direct", "0");
        p.put("sup_ws_ds_opt", "1");
        p.put("update_version_code", "2.0.0");
        p.put("did_rule", "3");
        p.put("screen_height", "1080");
        p.put("screen_width", "1920");
        p.put("heartbeat_duration", "0");
        p.put("resp_content_type", "protobuf");
        p.put("history_comment_count", "0");
        p.put("client_enter", "1");
        p.put("last_rtt", String.valueOf(100 + ThreadLocalRandom.current().nextInt(100)));
        p.put("room_id", String.valueOf(roomId));
        p.put("cursor", fr.cursor);
        p.put("internal_ext", fr.internalExt);
        p.putAll(fr.routeParams);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Protobuf

    /** Rahmen des Live-WebSockets (WebcastPushFrame). */
    record PushFrame(long logId, String payloadType, byte[] payload) {
        static PushFrame parse(byte[] data) throws IOException {
            Proto.Reader r = new Proto.Reader(data);
            long logId = 0;
            String type = "";
            byte[] payload = new byte[0];
            while (r.next()) {
                switch (r.field()) {
                    case 2 -> logId = r.int64();
                    case 7 -> type = r.string();
                    case 8 -> payload = r.bytes();
                    default -> r.skip();
                }
            }
            return new PushFrame(logId, type, payload);
        }
    }

    /** Inhalt einer Antwort (ProtoMessageFetchResult). */
    static final class FetchResult {
        final List<Object[]> messages = new ArrayList<>(); // {method, payload, msgId, isHistory}
        String cursor = "";
        String internalExt = "";
        final Map<String, String> routeParams = new LinkedHashMap<>();
        boolean needAck;
        String pushServer = "";
        boolean streamEnded;

        static FetchResult parse(byte[] data) throws IOException {
            FetchResult f = new FetchResult();
            Proto.Reader r = new Proto.Reader(data);
            while (r.next()) {
                switch (r.field()) {
                    case 1 -> {
                        Proto.Reader m = r.message();
                        String method = "";
                        byte[] payload = new byte[0];
                        long msgId = 0;
                        boolean history = false;
                        while (m.next()) {
                            switch (m.field()) {
                                case 1 -> method = m.string();
                                case 2 -> payload = m.bytes();
                                case 3 -> msgId = m.int64();
                                case 6 -> history = m.bool();
                                default -> m.skip();
                            }
                        }
                        f.messages.add(new Object[] {method, payload, msgId, history});
                    }
                    case 2 -> f.cursor = r.string();
                    case 5 -> f.internalExt = r.string();
                    case 7 -> {
                        Proto.Reader e = r.message();
                        String k = "";
                        String v = "";
                        while (e.next()) {
                            switch (e.field()) {
                                case 1 -> k = e.string();
                                case 2 -> v = e.string();
                                default -> e.skip();
                            }
                        }
                        if (!k.isEmpty() && k.length() < 100 && v.length() < 2000) {
                            f.routeParams.put(k, v);
                        }
                    }
                    case 9 -> f.needAck = r.bool();
                    case 10 -> f.pushServer = r.string();
                    default -> r.skip();
                }
            }
            return f;
        }
    }

    /** Chat-Nachrichten aus einer Antwort lesen (öffentlich für Tests). */
    static List<ChatEvent> toEvents(FetchResult res, String streamer, Set<Long> seen) throws IOException {
        List<ChatEvent> out = new ArrayList<>();
        for (Object[] m : res.messages) {
            String method = (String) m[0];
            byte[] payload = (byte[]) m[1];
            long msgId = (long) m[2];
            boolean history = (boolean) m[3];
            if ("WebcastControlMessage".equals(method)) {
                Proto.Reader r = new Proto.Reader(payload);
                while (r.next()) {
                    if (r.field() == 2 && r.wire() == 0) {
                        if (r.int64() == 3) {
                            res.streamEnded = true;
                        }
                    } else {
                        r.skip();
                    }
                }
                continue;
            }
            if (!"WebcastChatMessage".equals(method) || history) {
                continue;
            }
            if (seen != null && msgId != 0) {
                synchronized (seen) {
                    if (!seen.add(msgId)) {
                        continue;
                    }
                    if (seen.size() > 500) {
                        seen.remove(seen.iterator().next());
                    }
                }
            }
            String nickname = "";
            String uniqueId = "";
            boolean admin = false;
            String content = "";
            Proto.Reader r = new Proto.Reader(payload);
            while (r.next()) {
                switch (r.field()) {
                    case 2 -> {
                        Proto.Reader u = r.message();
                        while (u.next()) {
                            switch (u.field()) {
                                case 3 -> nickname = u.string();
                                case 38 -> uniqueId = u.string();
                                case 32 -> {
                                    Proto.Reader a = u.message();
                                    while (a.next()) {
                                        if ((a.field() == 2 || a.field() == 3) && a.wire() == 0) {
                                            admin |= a.bool();
                                        } else {
                                            a.skip();
                                        }
                                    }
                                }
                                default -> u.skip();
                            }
                        }
                    }
                    case 3 -> content = r.string();
                    default -> r.skip();
                }
            }
            String id = uniqueId.toLowerCase(Locale.ROOT);
            if (!NAME.matcher(id).matches() || content.isEmpty()) {
                continue;
            }
            boolean broadcaster = id.equals(streamer);
            out.add(new ChatEvent.Message(ChatPlatform.TIKTOK, ChatPlatform.TIKTOK.login(id), nickname.isBlank() ? uniqueId : nickname,
                    content, new ChatRoles(false, false, admin, broadcaster), null, msgId == 0 ? null : Long.toString(msgId)));
        }
        return out;
    }

    private static byte[] frame(long logId, String type, byte[] payload) {
        Proto.Writer w = new Proto.Writer();
        if (logId != 0) {
            w.int64(2, logId);
        }
        return w.string(6, "pb").string(7, type).bytes(8, payload).toBytes();
    }

    static byte[] enterRoomFrame(long roomId) {
        byte[] enter = new Proto.Writer()
                .int64(1, roomId)
                .int64(4, 12)
                .string(5, "audience")
                .int64(8, ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE))
                .string(9, "0")
                .toBytes();
        return frame(0, "im_enter_room", enter);
    }

    static byte[] heartbeatFrame(long roomId, long seq) {
        return frame(0, "hb", new Proto.Writer().int64(1, roomId).int64(2, seq).toBytes());
    }

    static byte[] ackFrame(long logId, String internalExt) {
        return frame(logId, "ack", internalExt.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        if (data.length < 3 || (data[0] & 0xFF) != 0x1F || (data[1] & 0xFF) != 0x8B) {
            return data;
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] out = in.readNBytes(8 * 1024 * 1024 + 1);
            if (out.length > 8 * 1024 * 1024) {
                throw new IOException("too_large");
            }
            return out;
        }
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

package de.minicompanions.streamer;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Übersetzung der Chat-Formate von Kick, YouTube und TikTok (ohne Netzwerk). */
class PlatformParserTest {

    @Test
    void kickChatMessageAndModeration() {
        Map<String, String> ids = new HashMap<>();
        String data = "{\"id\":\"m1\",\"chatroom_id\":5,\"content\":\"hi [emote:37226:KEKW] !skin jeb_\",\"type\":\"message\","
                + "\"sender\":{\"id\":1,\"username\":\"Some_User\",\"slug\":\"some-user\",\"identity\":{\"color\":\"#FF9D00\","
                + "\"badges\":[{\"type\":\"moderator\",\"text\":\"Moderator\"},{\"type\":\"subscriber\",\"count\":3}]}}}";
        List<ChatEvent> ev = KickChatSource.parseEvent("App\\Events\\ChatMessageEvent", new JsonPrimitive(data), ids);
        assertEquals(1, ev.size());
        ChatEvent.Message m = (ChatEvent.Message) ev.get(0);
        assertEquals(ChatPlatform.KICK, m.platform());
        assertEquals("kick:some-user", m.login());
        assertEquals("Some_User", m.displayName());
        assertEquals("hi KEKW !skin jeb_", m.text());
        assertTrue(m.roles().moderator() && m.roles().subscriber());
        assertEquals(0xFF9D00, m.colorRgb());

        List<ChatEvent> del = KickChatSource.parseEvent("App\\Events\\MessageDeletedEvent",
                new JsonPrimitive("{\"id\":\"x\",\"message\":{\"id\":\"m1\"}}"), ids);
        assertEquals(new ChatEvent.MessageDeleted("kick:some-user", "m1"), del.get(0));
        List<ChatEvent> ban = KickChatSource.parseEvent("App\\Events\\UserBannedEvent",
                new JsonPrimitive("{\"id\":\"x\",\"user\":{\"id\":1,\"username\":\"Some_User\",\"slug\":\"some-user\"}}"), ids);
        assertEquals(new ChatEvent.UserCleared("kick:some-user"), ban.get(0));
        assertTrue(KickChatSource.parseEvent("App\\Events\\ChatroomClearEvent", new JsonPrimitive("{\"id\":\"x\"}"), ids).get(0)
                instanceof ChatEvent.ChatCleared);
        // Müll wird ignoriert
        assertTrue(KickChatSource.parseEvent("App\\Events\\ChatMessageEvent", new JsonPrimitive("{kaputt"), ids).isEmpty());
    }

    @Test
    void youtubeActions() {
        Map<String, String> ids = new HashMap<>();
        String json = "[{\"addChatItemAction\":{\"item\":{\"liveChatTextMessageRenderer\":{\"message\":{\"runs\":["
                + "{\"text\":\"!skin \"},{\"text\":\"Notch\"},{\"emoji\":{\"emojiId\":\"x\",\"shortcuts\":[\":yt:\"],\"isCustomEmoji\":true}}]},"
                + "\"authorName\":{\"simpleText\":\"@Streamfan\"},\"authorExternalChannelId\":\"UCabcdefghijklmnopqrstuv\",\"id\":\"msg1\","
                + "\"authorBadges\":[{\"liveChatAuthorBadgeRenderer\":{\"icon\":{\"iconType\":\"MODERATOR\"}}}]}}}},"
                + "{\"markChatItemAsDeletedAction\":{\"targetItemId\":\"msg1\"}},"
                + "{\"markChatItemsByAuthorAsDeletedAction\":{\"externalChannelId\":\"UCabcdefghijklmnopqrstuv\"}}]";
        JsonArray actions = JsonParser.parseString(json).getAsJsonArray();
        List<ChatEvent> ev = YouTubeChatSource.parseActions(actions, ids);
        assertEquals(3, ev.size());
        ChatEvent.Message m = (ChatEvent.Message) ev.get(0);
        assertEquals("yt:ucabcdefghijklmnopqrstuv", m.login());
        assertEquals("Streamfan", m.displayName());
        assertEquals("!skin Notch:yt:", m.text());
        assertTrue(m.roles().moderator());
        assertEquals(new ChatEvent.MessageDeleted("yt:ucabcdefghijklmnopqrstuv", "msg1"), ev.get(1));
        assertEquals(new ChatEvent.UserCleared("yt:ucabcdefghijklmnopqrstuv"), ev.get(2));
    }

    @Test
    void youtubePrefersAllMessagesChat() {
        String chat = "{\"header\":{\"liveChatHeaderRenderer\":{\"viewSelector\":{\"sortFilterSubMenuRenderer\":{\"subMenuItems\":["
                + "{\"title\":\"Top\",\"continuation\":{\"reloadContinuationData\":{\"continuation\":\"TOP\"}}},"
                + "{\"title\":\"Live\",\"continuation\":{\"reloadContinuationData\":{\"continuation\":\"ALL\"}}}]}}}},"
                + "\"continuations\":[{\"invalidationContinuationData\":{\"continuation\":\"DEFAULT\"}}]}";
        assertEquals("ALL", YouTubeChatSource.liveChatContinuation(JsonParser.parseString(chat).getAsJsonObject()));
        String plain = "{\"continuations\":[{\"timedContinuationData\":{\"continuation\":\"DEFAULT\",\"timeoutMs\":5000}}]}";
        assertEquals("DEFAULT", YouTubeChatSource.liveChatContinuation(JsonParser.parseString(plain).getAsJsonObject()));
        assertNotNull(YouTubeChatSource.extractJson("<script>window[\"ytInitialData\"] = {\"a\":{\"b\":1}};</script>", "ytInitialData"));
    }

    @Test
    void tiktokProtobufChat() throws Exception {
        byte[] user = new Proto.Writer().int64(1, 42).string(3, "Nick Name").string(38, "Some.User_1")
                .bytes(32, new Proto.Writer().bool(2, true).toBytes()).toBytes();
        byte[] chat = new Proto.Writer().bytes(2, user).string(3, "!skin jeb_").toBytes();
        byte[] control = new Proto.Writer().int64(2, 3).toBytes();
        byte[] fetch = new Proto.Writer()
                .bytes(1, new Proto.Writer().string(1, "WebcastChatMessage").bytes(2, chat).int64(3, 777).toBytes())
                .bytes(1, new Proto.Writer().string(1, "WebcastChatMessage").bytes(2, chat).int64(3, 777).toBytes()) // doppelt
                .bytes(1, new Proto.Writer().string(1, "WebcastChatMessage").bytes(2, chat).int64(3, 778).bool(6, true).toBytes()) // Verlauf
                .bytes(1, new Proto.Writer().string(1, "WebcastControlMessage").bytes(2, control).toBytes())
                .string(2, "cur").string(5, "ext")
                .bytes(7, new Proto.Writer().string(1, "imprp").string(2, "abc").toBytes())
                .bool(9, true).string(10, "wss://example.tiktok.com/ws").toBytes();
        TikTokChatSource.FetchResult fr = TikTokChatSource.FetchResult.parse(fetch);
        assertEquals("cur", fr.cursor);
        assertEquals("ext", fr.internalExt);
        assertEquals("abc", fr.routeParams.get("imprp"));
        assertTrue(fr.needAck);
        assertEquals("wss://example.tiktok.com/ws", fr.pushServer);
        List<ChatEvent> ev = TikTokChatSource.toEvents(fr, "streamer", new HashSet<>());
        assertEquals(1, ev.size(), "doppelte und alte Nachrichten werden übersprungen");
        ChatEvent.Message m = (ChatEvent.Message) ev.get(0);
        assertEquals("tt:some.user_1", m.login());
        assertEquals("Nick Name", m.displayName());
        assertEquals("!skin jeb_", m.text());
        assertTrue(m.roles().moderator());
        assertTrue(fr.streamEnded, "Steuer-Nachricht 3 = Stream beendet");

        TikTokChatSource.PushFrame pf = TikTokChatSource.PushFrame.parse(TikTokChatSource.ackFrame(99, "ext"));
        assertEquals(99, pf.logId());
        assertEquals("ack", pf.payloadType());
        assertEquals("ext", new String(pf.payload(), StandardCharsets.UTF_8));
        // kaputte Daten -> Ausnahme statt Absturz
        assertThrows(java.io.IOException.class, () -> TikTokChatSource.FetchResult.parse(new byte[] {0x0A, 0x7F, 0x01}));
    }

    @Test
    void channelInputsAreNormalized() {
        assertEquals("some-streamer", new KickChatSource().normalize("https://kick.com/Some-Streamer"));
        assertNull(new KickChatSource().normalize("../../etc"));
        assertEquals("h:LofiGirl", new YouTubeChatSource().normalize("@LofiGirl"));
        assertEquals("h:LofiGirl", new YouTubeChatSource().normalize("https://www.youtube.com/@LofiGirl/streams"));
        assertEquals("v:1-LpQekNa9g", new YouTubeChatSource().normalize("https://www.youtube.com/watch?v=1-LpQekNa9g"));
        assertEquals("c:UCabcdefghijklmnopqrstuv", new YouTubeChatSource().normalize("UCabcdefghijklmnopqrstuv"));
        assertNull(new YouTubeChatSource().normalize("<script>"));
        assertEquals("some.user", new TikTokChatSource().normalize("https://www.tiktok.com/@Some.User/live"));
        assertNull(new TikTokChatSource().normalize("a b"));
        assertEquals(ChatPlatform.KICK, ChatPlatform.ofLogin("kick:x"));
        assertEquals(ChatPlatform.TWITCH, ChatPlatform.ofLogin("x"));
        assertEquals("x", ChatPlatform.plainLogin("tt:x"));
    }

    @Test
    void multiPlatformConfig() {
        StreamerConfig c = new StreamerConfig();
        c.channel = "redstone_nugget";
        c.kickChannel = "kick_name";
        c.tiktokChannel = "tiktok.name";
        c.tiktokOn = false;
        c.validate();
        assertEquals(2, c.activePlatforms().size());
        assertTrue(c.autoConnect, "automatisch verbinden ist Standard");
        assertEquals("MULTI", c.platformNameColors);
    }
}

package de.minicompanions.streamer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static de.minicompanions.streamer.StreamerPipeline.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simulierte Stream-Chat-Nachrichten: Streamer-Modus aus/an, gültige und ungültige Namen, Spam,
 * Limit, Panik, Nachrichten nur von Zuschauern mit Figur, Filter, Stummschalten, Ausschalten.
 */
class StreamerPipelineTest {
    private StreamerConfig cfg;
    private ModerationService mod;
    private FakeWorld world;
    private AtomicLong clock;
    private AtomicBoolean enabled;
    private StreamerPipeline p;

    @BeforeEach
    void setUp() {
        cfg = new StreamerConfig();
        cfg.validate();
        mod = new ModerationService(new ModerationLists(), null);
        world = new FakeWorld();
        clock = new AtomicLong(1_000_000);
        enabled = new AtomicBoolean(true);
        p = new StreamerPipeline(cfg, mod, world, clock::get, enabled::get);
    }

    private static ChatEvent.Message msg(String login, String text) {
        return new ChatEvent.Message(ChatPlatform.TWITCH, login, login, text, ChatRoles.NONE, null, null);
    }

    private void tickAll() {
        for (int i = 0; i < 50; i++) {
            clock.addAndGet(1000);
            p.tick();
        }
    }

    @Test
    void streamerModeOffDoesNothing() {
        enabled.set(false);
        assertEquals(IGNORED_DISABLED, p.onEvent(msg("anna", "!skin Notch")));
        tickAll();
        assertTrue(world.figures.isEmpty());
        assertEquals(IGNORED_DISABLED, p.onEvent(msg("anna", "hallo")));
        assertTrue(world.bubbles.isEmpty());
    }

    @Test
    void validSkinCommandSpawns() {
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("anna", "!skin Notch")));
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("bob", "!skin jeb_")));
        tickAll();
        assertEquals("Notch", world.figures.get("anna"));
        assertEquals("jeb_", world.figures.get("bob"));
    }

    @Test
    void invalidNamesAreIgnored() {
        assertEquals(IGNORED_INVALID_NAME, p.onEvent(msg("a", "!skin ab")));
        assertEquals(IGNORED_INVALID_NAME, p.onEvent(msg("a", "!skin ../../etc/passwd")));
        assertEquals(IGNORED_INVALID_NAME, p.onEvent(msg("a", "!skin https://evil.example/x.png")));
        assertEquals(IGNORED_INVALID_NAME, p.onEvent(msg("a", "!skin ThisNameIsWayTooLong")));
        assertEquals(IGNORED_INVALID_NAME, p.onEvent(msg("a", "!skin Notch extra")));
        assertEquals(IGNORED_INVALID_NAME, p.onEvent(msg("a", "!skin")));
        assertEquals(IGNORED_INVALID_NAME, p.onEvent(msg("a", "!skin Nö§ch")));
        tickAll();
        assertTrue(world.figures.isEmpty());
    }

    @Test
    void cooldownBlocksSpam() {
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("anna", "!skin Notch")));
        assertEquals(IGNORED_COOLDOWN, p.onEvent(msg("anna", "!skin jeb_")));
        clock.addAndGet(31_000);
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("anna", "!skin jeb_")));
        tickAll();
        // ein Zuschauer = höchstens eine Figur (neue ersetzt alte)
        assertEquals(1, world.figures.size());
        assertEquals("jeb_", world.figures.get("anna"));
    }

    @Test
    void limitIgnoresOrReplaces() {
        cfg.viewerLimit = 2;
        cfg.cooldownSeconds = 0;
        p.onEvent(msg("a1", "!skin Notch"));
        p.onEvent(msg("a2", "!skin Notch"));
        tickAll();
        assertEquals(2, world.figures.size());
        assertEquals(IGNORED_LIMIT, p.onEvent(msg("a3", "!skin Notch")));
        assertTrue(world.notices.contains("limit_reached"));
        cfg.limitPolicy = "REPLACE_OLDEST";
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("a3", "!skin Notch")));
        tickAll();
        assertEquals(2, world.figures.size());
        assertFalse(world.figures.containsKey("a1"));
        assertTrue(world.figures.containsKey("a3"));
    }

    @Test
    void limitZeroSpawnsNothing() {
        cfg.viewerLimit = 0;
        assertEquals(IGNORED_LIMIT, p.onEvent(msg("anna", "!skin Notch")));
        tickAll();
        assertTrue(world.figures.isEmpty());
    }

    @Test
    void globalRateLimitPerSecond() {
        cfg.cooldownSeconds = 0;
        cfg.maxSpawnsPerSecond = 2;
        cfg.viewerLimit = 50;
        for (int i = 0; i < 10; i++) {
            p.onEvent(msg("v" + i, "!skin Notch"));
        }
        p.tick();
        assertEquals(2, world.spawnCalls, "nur 2 Spawns pro Sekunde");
        tickAll();
        assertEquals(10, world.figures.size());
    }

    @Test
    void queueHasMaximum() {
        cfg.cooldownSeconds = 0;
        cfg.spawnQueueMax = 3;
        cfg.viewerLimit = 50;
        for (int i = 0; i < 3; i++) {
            assertEquals(SPAWN_QUEUED, p.onEvent(msg("v" + i, "!skin Notch")));
        }
        assertEquals(IGNORED_QUEUE_FULL, p.onEvent(msg("v9", "!skin Notch")));
    }

    @Test
    void panicRemovesEverythingAndPauses() {
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        p.onEvent(msg("anna", "hallo zusammen"));
        assertFalse(world.bubbles.isEmpty());
        p.onEvent(msg("bob", "!skin jeb_"));
        p.panic();
        assertTrue(world.figures.isEmpty());
        assertTrue(world.bubbles.isEmpty());
        tickAll();
        assertTrue(world.figures.isEmpty(), "Warteschlange wurde geleert");
        assertEquals(IGNORED_PAUSED, p.onEvent(msg("carl", "!skin Notch")));
        mod.setSpawnsPaused(false);
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("carl", "!skin Notch")));
    }

    @Test
    void messagesOnlyFromViewersWithFigure() {
        assertEquals(IGNORED_NO_FIGURE, p.onEvent(msg("anna", "hallo")));
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        assertEquals(BUBBLE_SHOWN, p.onEvent(msg("anna", "hallo")));
        assertEquals("hallo", world.bubbles.get("anna"));
        assertEquals(IGNORED_NO_FIGURE, p.onEvent(msg("bob", "ich habe keine Figur")));
    }

    @Test
    void commandsNeverShownAsBubble() {
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        world.bubbles.clear();
        assertEquals(IGNORED_COMMAND, p.onEvent(msg("anna", "!help")));
        assertEquals(IGNORED_COMMAND, p.onEvent(msg("anna", "/ban streamer")));
        assertTrue(world.bubbles.isEmpty());
    }

    @Test
    void messageFilteringAndTruncation() {
        cfg.minBubbleIntervalSeconds = 0;
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        p.onEvent(msg("anna", "schau mal https://evil.example/abc und www.test.de"));
        assertEquals("schau mal [Link] und [Link]", world.bubbles.get("anna"));
        clock.addAndGet(1000);
        p.onEvent(msg("anna", "§cRot §lFett"));
        assertEquals("Rot Fett", world.bubbles.get("anna"));
        clock.addAndGet(1000);
        p.onEvent(msg("anna", "x".repeat(10) + " " + "a b ".repeat(80)));
        assertTrue(world.bubbles.get("anna").length() <= cfg.maxChars);
        assertTrue(world.bubbles.get("anna").endsWith("…"));
        clock.addAndGet(1000);
        p.onEvent(msg("anna", "mail mich an test@example.com"));
        assertEquals("mail mich an [Link]", world.bubbles.get("anna"));
    }

    @Test
    void wordFilterCensorsOrDrops() {
        cfg.minBubbleIntervalSeconds = 0;
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        p.onEvent(msg("anna", "du bist ein arschloch"));
        assertEquals("du bist ein ***", world.bubbles.get("anna"));
        cfg.censorMode = "DROP";
        clock.addAndGet(1000);
        assertEquals(IGNORED_FILTERED, p.onEvent(msg("anna", "F.U.C.K dich")));
    }

    @Test
    void quickMessagesAreAllShownNewestWins() {
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        // Standard: kein Mindestabstand, jede neue Nachricht wird sofort gezeigt
        assertEquals(BUBBLE_SHOWN, p.onEvent(msg("anna", "hallo")));
        assertEquals(BUBBLE_SHOWN, p.onEvent(msg("anna", "wie gehts")));
        assertEquals(BUBBLE_SHOWN, p.onEvent(msg("anna", "cooler stream")));
        assertEquals("cooler stream", world.bubbles.get("anna"));
        assertEquals(1, cfg.bubbleQueueSize, "neue Nachricht ersetzt die alte Blase");
    }

    @Test
    void duplicateAndRateLimitedMessages() {
        cfg.minBubbleIntervalSeconds = 2;
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        assertEquals(BUBBLE_SHOWN, p.onEvent(msg("anna", "hallo")));
        // zu schnell hintereinander (Mindestabstand 2 s, falls eingestellt)
        assertEquals(IGNORED_RATE, p.onEvent(msg("anna", "etwas anderes")));
        clock.addAndGet(3000);
        // gleiche Nachricht (auch in Großbuchstaben mit Satzzeichen) = Spam
        assertEquals(IGNORED_DUPLICATE, p.onEvent(msg("anna", "HALLO!!!")));
        assertEquals(BUBBLE_SHOWN, p.onEvent(msg("anna", "etwas anderes")));
    }

    @Test
    void sameNameOnDifferentPlatformsAreDifferentViewers() {
        assertEquals(SPAWN_QUEUED, p.onEvent(new ChatEvent.Message(ChatPlatform.TWITCH, "anna", "anna", "!skin Notch", ChatRoles.NONE, null, null)));
        assertEquals(SPAWN_QUEUED, p.onEvent(new ChatEvent.Message(ChatPlatform.KICK, "kick:anna", "anna", "!skin jeb_", ChatRoles.NONE, null, null)));
        assertEquals(SPAWN_QUEUED, p.onEvent(new ChatEvent.Message(ChatPlatform.TIKTOK, "tt:anna.tt", "Anna", "!skin Dinnerbone", ChatRoles.NONE, null, null)));
        assertEquals(SPAWN_QUEUED, p.onEvent(new ChatEvent.Message(ChatPlatform.YOUTUBE, "yt:ucabcdefghijklmnopqrstuv", "Anna", "!skin Notch", ChatRoles.NONE, null, null)));
        tickAll();
        assertTrue(world.hasFigure("anna") && world.hasFigure("kick:anna") && world.hasFigure("tt:anna.tt") && world.hasFigure("yt:ucabcdefghijklmnopqrstuv"));
        // ungültige Logins (fremde Präfixe, Sonderzeichen) werden verworfen
        assertEquals(IGNORED_INVALID, p.onEvent(new ChatEvent.Message(ChatPlatform.KICK, "evil:x", "x", "!skin Notch", ChatRoles.NONE, null, null)));
        assertEquals(IGNORED_INVALID, p.onEvent(new ChatEvent.Message(ChatPlatform.KICK, "kick:a/b", "x", "!skin Notch", ChatRoles.NONE, null, null)));
    }

    @Test
    void oldBubbleDefaultsMigrate() {
        StreamerConfig old = new StreamerConfig();
        old.formatVersion = 2;
        old.minBubbleIntervalSeconds = 2;
        old.bubbleQueueSize = 3;
        old.validate();
        assertEquals(0, old.minBubbleIntervalSeconds);
        assertEquals(1, old.bubbleQueueSize);
    }

    @Test
    void muteHidesMessagesButKeepsFigure() {
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        mod.mute("anna");
        assertEquals(IGNORED_MUTED, p.onEvent(msg("anna", "hallo")));
        assertTrue(world.figures.containsKey("anna"));
        mod.unmute("anna");
        assertEquals(BUBBLE_SHOWN, p.onEvent(msg("anna", "wieder da")));
    }

    @Test
    void messagesOffSwitch() {
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        mod.setMessagesHidden(true);
        assertEquals(IGNORED_MESSAGES_OFF, p.onEvent(msg("anna", "hallo")));
    }

    @Test
    void banAndSkinBanPersistInLists() {
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        p.ban("anna");
        assertFalse(world.figures.containsKey("anna"));
        assertTrue(mod.lists().bannedViewers.contains("anna"));
        clock.addAndGet(60_000);
        assertEquals(IGNORED_BANNED, p.onEvent(msg("anna", "!skin jeb_")));
        p.banSkin("jeb_");
        assertEquals(IGNORED_BANNED_SKIN, p.onEvent(msg("bob", "!skin JEB_")));
    }

    @Test
    void channelModerationRemovesFigure() {
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        p.onEvent(msg("anna", "hallo"));
        assertEquals(MODERATION_APPLIED, p.onEvent(new ChatEvent.MessageDeleted("anna", "id1")));
        assertFalse(world.bubbles.containsKey("anna"));
        assertEquals(MODERATION_APPLIED, p.onEvent(new ChatEvent.UserCleared("anna")));
        assertFalse(world.figures.containsKey("anna"));
    }

    @Test
    void roleFilterAndWhitelist() {
        cfg.roleFilter = "SUBS";
        assertEquals(IGNORED_ROLE, p.onEvent(msg("anna", "!skin Notch")));
        ChatRoles sub = new ChatRoles(true, false, false, false);
        assertEquals(SPAWN_QUEUED, p.onEvent(new ChatEvent.Message(ChatPlatform.TWITCH, "bob", "Bob", "!skin Notch", sub, null, null)));
        cfg.roleFilter = "ALL";
        cfg.useWhitelist = true;
        assertEquals(IGNORED_WHITELIST, p.onEvent(msg("carl", "!skin Notch")));
        mod.lists().whitelist.add("carl");
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("carl", "!skin Notch")));
    }

    @Test
    void approvalQueue() {
        cfg.approvalMode = true;
        assertEquals(SPAWN_AWAITING_APPROVAL, p.onEvent(msg("anna", "!skin Notch")));
        tickAll();
        assertTrue(world.figures.isEmpty());
        assertTrue(p.approve("anna"));
        tickAll();
        assertTrue(world.figures.containsKey("anna"));
    }

    @Test
    void shutdownRemovesEverything() {
        p.onEvent(msg("anna", "!skin Notch"));
        tickAll();
        p.onEvent(msg("anna", "hallo"));
        p.onEvent(msg("bob", "!skin jeb_"));
        p.shutdown();
        enabled.set(false);
        tickAll();
        assertTrue(world.figures.isEmpty());
        assertTrue(world.bubbles.isEmpty());
    }

    @Test
    void offensiveLoginIsBlocked() {
        assertEquals(IGNORED_BANNED, p.onEvent(msg("fuck_you_123", "!skin Notch")));
    }

    @Test
    void customCommandNameAndPrefix() {
        cfg.commandName = "figur";
        cfg.commandPrefixes = "#";
        cfg.validate();
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("anna", "#figur Notch")));
        assertEquals(IGNORED_COMMAND, p.onEvent(msg("bob", "!skin Notch")));
    }

    @Test
    void defaultCommandIsBangSkin() {
        cfg.validate();
        assertEquals("!", cfg.commandPrefixes);
        assertEquals(IGNORED_COMMAND, p.onEvent(msg("anna", "/skin Notch")));
        assertFalse(world.hasFigure("anna"));
        assertEquals(SPAWN_QUEUED, p.onEvent(msg("anna", "!skin Notch")));
    }

    @Test
    void oldConfigMigratesToBangSkin() {
        StreamerConfig old = new StreamerConfig();
        old.commandPrefixes = "/!";
        old.formatVersion = 0;
        old.validate();
        assertEquals("!", old.commandPrefixes);
        // nach der Umstellung bleibt eine bewusste Wahl erhalten
        old.commandPrefixes = "/!";
        old.validate();
        assertEquals("/!", old.commandPrefixes);
    }
}

package de.minicompanions.streamer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Twitch-IRC-Parser, Befehlserkennung, Bereinigung und Wortfilter. */
class ParserAndFilterTest {

    @Test
    void parsesPrivmsgWithTagsAndRoles() {
        String line = "@badge-info=subscriber/8;badges=subscriber/6,vip/1;color=#1E90FF;display-name=Anna\\sB;id=abc-123;mod=0;"
                + "subscriber=1;user-type= :anna!anna@anna.tmi.twitch.tv PRIVMSG #kanal :!skin Notch";
        ChatEvent e = TwitchIrcParser.toEvent(TwitchIrcParser.parse(line));
        ChatEvent.Message m = assertInstanceOf(ChatEvent.Message.class, e);
        assertEquals("anna", m.login());
        assertEquals("Anna B", m.displayName());
        assertEquals("!skin Notch", m.text());
        assertTrue(m.roles().subscriber());
        assertTrue(m.roles().vip());
        assertFalse(m.roles().moderator());
        assertEquals(0x1E90FF, m.colorRgb());
        assertEquals("abc-123", m.messageId());
    }

    @Test
    void parsesModerationEvents() {
        assertInstanceOf(ChatEvent.UserCleared.class,
                TwitchIrcParser.toEvent(TwitchIrcParser.parse("@ban-duration=600 :tmi.twitch.tv CLEARCHAT #kanal :BadGuy")));
        assertInstanceOf(ChatEvent.ChatCleared.class,
                TwitchIrcParser.toEvent(TwitchIrcParser.parse(":tmi.twitch.tv CLEARCHAT #kanal")));
        ChatEvent.MessageDeleted d = assertInstanceOf(ChatEvent.MessageDeleted.class,
                TwitchIrcParser.toEvent(TwitchIrcParser.parse("@login=anna;target-msg-id=xyz :tmi.twitch.tv CLEARMSG #kanal :text")));
        assertEquals("anna", d.login());
        assertEquals("xyz", d.messageId());
    }

    @Test
    void ignoresGarbage() {
        assertNull(TwitchIrcParser.parse(null));
        assertNull(TwitchIrcParser.parse(""));
        assertNull(TwitchIrcParser.toEvent(TwitchIrcParser.parse(":tmi.twitch.tv 001 justinfan123 :Welcome")));
        assertNull(TwitchIrcParser.toEvent(TwitchIrcParser.parse("@a=b")));
        assertNull(TwitchIrcParser.parse("x".repeat(TwitchIrcParser.MAX_LINE + 1)));
    }

    @Test
    void channelValidation() {
        assertTrue(TwitchIrcParser.isValidChannel("mein_kanal"));
        assertFalse(TwitchIrcParser.isValidChannel("a"));
        assertFalse(TwitchIrcParser.isValidChannel("kanal\r\nPRIVMSG #x :hi"));
        assertFalse(TwitchIrcParser.isValidChannel("../etc"));
    }

    @Test
    void commandParser() {
        assertEquals(CommandParser.Kind.SPAWN_VALID, CommandParser.parse("!skin Notch", "!", "skin").kind());
        assertEquals("Notch", CommandParser.parse("!skin Notch", "!", "skin").mcName());
        assertEquals(CommandParser.Kind.SPAWN_VALID, CommandParser.parse("  !SKIN   jeb_ ", "!", "skin").kind());
        assertEquals(CommandParser.Kind.NOT_COMMAND, CommandParser.parse("skin Notch", "!", "skin").kind());
        assertEquals(CommandParser.Kind.OTHER_COMMAND, CommandParser.parse("!other", "!", "skin").kind());
        // /skin ist nicht mehr der Befehl, wird aber auch nicht als Blase gezeigt
        assertEquals(CommandParser.Kind.OTHER_COMMAND, CommandParser.parse("/skin Notch", "!", "skin").kind());
        assertEquals(CommandParser.Kind.SPAWN_VALID, CommandParser.parse("/skin Notch", "/!", "skin").kind());
        assertEquals(CommandParser.Kind.SPAWN_INVALID, CommandParser.parse("!skin C:\\x.png", "!", "skin").kind());
        assertEquals("skin", CommandParser.sanitizeCommandName("!/bad name"));
    }

    @Test
    void sanitizerRemovesDangerousContent() {
        assertEquals("Hallo", MessageSanitizer.sanitize("§aHal§rlo", 100));
        assertEquals("ab", MessageSanitizer.sanitize("a\u202Eb\u0000", 100));
        assertEquals("[Link]", MessageSanitizer.sanitize("http://a.b/c https://d.e/f", 100));
        assertEquals("cool!!!", MessageSanitizer.sanitize("cool!!!!!!!!!!", 100));
        assertEquals("hör auf zu schreien bitte", MessageSanitizer.sanitize("HÖR AUF ZU SCHREIEN BITTE", 100));
        assertEquals("abcdefghijkl…", MessageSanitizer.sanitize("abcdefghijklmnopqrstuvwxyzabcdefghij", 100));
        assertEquals("abc…", MessageSanitizer.sanitize("abc defgh", 4));
        assertEquals("", MessageSanitizer.sanitize("§§§§", 100));
    }

    @Test
    void wordFilterHandlesLeetAndScunthorpeCases() {
        WordFilter f = new WordFilter(List.of("arschloch", "fuck", "dick"));
        assertTrue(f.apply("du arschl0ch").matched(), "Leetspeak wird erkannt");
        assertEquals("***", f.apply("FuCk").text());
        assertFalse(f.apply("Schöner Klassiker").matched());
        assertFalse(f.apply("Cocktail").matched());
        assertTrue(f.containsBadWord("xXfuckXx"));
    }
}

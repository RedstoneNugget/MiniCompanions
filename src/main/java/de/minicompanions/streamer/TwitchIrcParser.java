package de.minicompanions.streamer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parser für Zeilen des Twitch-IRC (mit IRCv3-Tags). Aufbau nach dem Vorbild von
 * TwitchChatClient aus "Take Your Stream Chat" (ijustseen, MIT-Lizenz), angepasst:
 * zusätzlich Moderations-Ereignisse (CLEARCHAT, CLEARMSG), Tag-Unescaping und strengere Prüfungen.
 *
 * <p>Reine Logik - in Unit-Tests ohne Netzwerk prüfbar.</p>
 */
public final class TwitchIrcParser {
    /** Maximale Länge einer Zeile, die überhaupt verarbeitet wird (Twitch erlaubt ~ 500 Zeichen Text). */
    public static final int MAX_LINE = 8192;

    private TwitchIrcParser() {
    }

    /** Ergebnis der Analyse einer Zeile. */
    public record Line(Map<String, String> tags, String prefix, String command, String[] params, String trailing) {
        public String tag(String k) {
            return tags.get(k);
        }

        public String nick() {
            if (prefix == null) {
                return null;
            }
            int ex = prefix.indexOf('!');
            return ex > 0 ? prefix.substring(0, ex) : prefix;
        }
    }

    /** Zerlegt eine IRC-Zeile; null bei ungültiger Zeile. */
    public static Line parse(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_LINE) {
            return null;
        }
        String s = raw;
        Map<String, String> tags = new HashMap<>();
        int pos = 0;
        if (s.startsWith("@")) {
            int sp = s.indexOf(' ');
            if (sp < 0) {
                return null;
            }
            for (String t : s.substring(1, sp).split(";")) {
                int eq = t.indexOf('=');
                String k = eq >= 0 ? t.substring(0, eq) : t;
                String v = eq >= 0 ? unescapeTag(t.substring(eq + 1)) : "";
                if (!k.isEmpty()) {
                    tags.put(k, v);
                }
            }
            pos = sp + 1;
        }
        while (pos < s.length() && s.charAt(pos) == ' ') {
            pos++;
        }
        String prefix = null;
        if (pos < s.length() && s.charAt(pos) == ':') {
            int sp = s.indexOf(' ', pos);
            if (sp < 0) {
                return null;
            }
            prefix = s.substring(pos + 1, sp);
            pos = sp + 1;
        }
        String trailing = null;
        int tr = s.indexOf(" :", pos);
        String middle;
        if (tr >= 0) {
            trailing = s.substring(tr + 2);
            middle = s.substring(pos, tr);
        } else {
            middle = s.substring(pos);
        }
        String[] parts = middle.trim().split(" +");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        String command = parts[0].toUpperCase(Locale.ROOT);
        String[] params = new String[parts.length - 1];
        System.arraycopy(parts, 1, params, 0, params.length);
        return new Line(tags, prefix, command, params, trailing);
    }

    /**
     * Wandelt eine Zeile in ein {@link ChatEvent} um (oder null, wenn für uns uninteressant).
     */
    public static ChatEvent toEvent(Line l) {
        if (l == null) {
            return null;
        }
        switch (l.command()) {
            case "PRIVMSG" -> {
                String login = l.nick();
                if (login == null || login.isEmpty() || l.trailing() == null) {
                    return null;
                }
                login = login.toLowerCase(Locale.ROOT);
                String display = l.tag("display-name");
                if (display == null || display.isBlank()) {
                    display = login;
                }
                String text = l.trailing();
                // /me-Aktionen: "\u0001ACTION text\u0001"
                if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) {
                    text = text.substring(8, text.length() - 1);
                }
                Integer color = null;
                String c = l.tag("color");
                if (c != null && c.length() == 7 && c.startsWith("#")) {
                    try {
                        color = Integer.parseInt(c.substring(1), 16);
                    } catch (NumberFormatException ignored) {
                        color = null;
                    }
                }
                ChatRoles roles = ChatRoles.fromTwitch(l.tag("badges"), l.tag("mod"), l.tag("subscriber"), l.tag("vip"));
                return new ChatEvent.Message(ChatPlatform.TWITCH, login, display, text, roles, color, l.tag("id"));
            }
            case "CLEARCHAT" -> {
                if (l.trailing() != null && !l.trailing().isBlank()) {
                    return new ChatEvent.UserCleared(l.trailing().trim().toLowerCase(Locale.ROOT));
                }
                return new ChatEvent.ChatCleared();
            }
            case "CLEARMSG" -> {
                String login = l.tag("login");
                return new ChatEvent.MessageDeleted(login == null ? null : login.toLowerCase(Locale.ROOT), l.tag("target-msg-id"));
            }
            default -> {
                return null;
            }
        }
    }

    /** IRCv3-Tag-Werte entschlüsseln (\s = Leerzeichen, \: = Semikolon ...). */
    public static String unescapeTag(String v) {
        if (v.indexOf('\\') < 0) {
            return v;
        }
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '\\' && i + 1 < v.length()) {
                char n = v.charAt(++i);
                switch (n) {
                    case 's' -> sb.append(' ');
                    case ':' -> sb.append(';');
                    case '\\' -> sb.append('\\');
                    case 'r', 'n' -> sb.append(' ');
                    default -> sb.append(n);
                }
            } else if (ch != '\\') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** Gültiger Twitch-Kanalname (nur Kleinbuchstaben, Ziffern, Unterstrich; 3-25 Zeichen). */
    public static boolean isValidChannel(String channel) {
        return channel != null && channel.matches("[A-Za-z0-9_]{3,25}");
    }
}

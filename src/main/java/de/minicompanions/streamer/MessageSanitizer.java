package de.minicompanions.streamer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Macht Zuschauer-Nachrichten anzeigesicher: nur reiner Text. Entfernt Minecraft-Formatcodes (§),
 * Steuer- und unsichtbare Formatzeichen (z. B. Bidi-Overrides), ersetzt Links und E-Mail-Adressen
 * durch "[Link]", kürzt sehr lange Zeichenketten, Zeichen-Wiederholungen und Großbuchstaben-Flut
 * und begrenzt die Länge mit "…".
 */
public final class MessageSanitizer {
    public static final String LINK = "[Link]";
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}0-9._%+-]+@[\\p{L}0-9.-]+\\.[\\p{L}]{2,}");
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:(?:https?|ftp|file)://\\S+|www\\.\\S+"
                    + "|[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|net|org|de|at|ch|tv|gg|io|ly|me|co|uk|xyz|ru|info|biz|app|dev|link|site|online|shop|live|stream|gl|be|to|cc|eu|us|fr|nl|pl|es|it|tk|ml|ga|cf|top|club|fun|art|ai)\\b(?:[/:?#]\\S*)?)");
    private static final Pattern REPEAT = Pattern.compile("(.)\\1{3,}");
    private static final int LONG_TOKEN = 24;

    private MessageSanitizer() {
    }

    /** @return bereinigter Text oder "" wenn nichts Anzeigbares übrig bleibt. */
    public static String sanitize(String input, int maxChars) {
        if (input == null) {
            return "";
        }
        String s = input.length() > 2000 ? input.substring(0, 2000) : input;
        s = stripFormatting(s);
        s = EMAIL.matcher(s).replaceAll(Matcher.quoteReplacement(LINK));
        s = URL.matcher(s).replaceAll(Matcher.quoteReplacement(LINK));
        s = REPEAT.matcher(s).replaceAll("$1$1$1");
        s = shortenLongTokens(s);
        s = calmCaps(s);
        s = s.replaceAll("\\s+", " ").strip();
        s = collapseLinks(s);
        return truncate(s, Math.max(1, maxChars));
    }

    /** Entfernt §-Codes, Steuerzeichen, unsichtbare Format-Zeichen und private Zeichen. */
    public static String stripFormatting(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int len = Character.charCount(cp);
            if (cp == '§') {
                // Formatcode samt folgendem Zeichen entfernen
                i += len;
                if (i < s.length()) {
                    i += Character.charCount(s.codePointAt(i));
                }
                continue;
            }
            int type = Character.getType(cp);
            boolean bad = Character.isISOControl(cp)
                    || type == Character.FORMAT
                    || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE
                    || type == Character.UNASSIGNED
                    || cp == '�';
            if (bad) {
                if (cp == '\t' || cp == '\n' || cp == '\r') {
                    sb.append(' ');
                }
            } else {
                sb.appendCodePoint(cp);
            }
            i += len;
        }
        return sb.toString();
    }

    private static String shortenLongTokens(String s) {
        String[] tokens = s.split(" ");
        StringBuilder sb = new StringBuilder(s.length());
        for (String t : tokens) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (t.codePointCount(0, t.length()) > LONG_TOKEN && !t.equals(LINK)) {
                sb.append(t, 0, t.offsetByCodePoints(0, 12)).append('…');
            } else {
                sb.append(t);
            }
        }
        return sb.toString();
    }

    /** Großbuchstaben-Flut abmildern. */
    public static String calmCaps(String s) {
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        if (letters >= 8 && upper > letters * 0.7) {
            String lower = s.toLowerCase(Locale.ROOT);
            return lower.replace("[link]", LINK);
        }
        return s;
    }

    private static String collapseLinks(String s) {
        String dbl = LINK + " " + LINK;
        while (s.contains(dbl)) {
            s = s.replace(dbl, LINK);
        }
        return s;
    }

    public static String truncate(String s, int maxChars) {
        if (s.codePointCount(0, s.length()) <= maxChars) {
            return s;
        }
        int end = s.offsetByCodePoints(0, Math.max(1, maxChars - 1));
        return s.substring(0, end).stripTrailing() + "…";
    }

    /** Normalisierte Form zum Erkennen von Wiederholungen (Spam). */
    public static String normalizeForSpam(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}

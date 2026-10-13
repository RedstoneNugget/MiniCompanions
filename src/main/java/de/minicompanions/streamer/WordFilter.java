package de.minicompanions.streamer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wort-Blacklist mit einfacher Normalisierung (Groß/Klein, Leetspeak, Trennzeichen).
 * Treffer werden als *** zensiert oder führen zum Verwerfen der Nachricht.
 */
public final class WordFilter {
    /** Kleine Standardliste gängiger Schimpfwörter (Deutsch/Englisch). Erweiterbar per GUI/Datei. */
    public static final List<String> DEFAULT_WORDS = List.of(
            "fuck", "fucking", "motherfucker", "shit", "bitch", "bastard", "asshole", "dick", "cunt", "whore", "slut",
            "retard", "faggot", "nigger", "nigga", "wanker", "twat", "pussy", "cock",
            "scheisse", "scheiße", "arschloch", "fotze", "hurensohn", "hure", "wichser", "missgeburt", "spast",
            "schlampe", "ficken", "fick", "bastard", "behindert", "schwuchtel", "nazi", "heil hitler", "sieg heil", "kys");

    public enum Mode { CENSOR, DROP }

    public record Result(String text, boolean matched) {
    }

    private final Set<String> words = new LinkedHashSet<>();

    public WordFilter(Collection<String> list) {
        set(list);
    }

    public void set(Collection<String> list) {
        words.clear();
        if (list != null) {
            for (String w : list) {
                String n = normalize(w).strip();
                if (n.length() >= 2) {
                    words.add(n);
                }
            }
        }
    }

    public boolean isEmpty() {
        return words.isEmpty();
    }

    /** Prüft den Text und zensiert gefundene Wörter. */
    public Result apply(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return new Result(text, false);
        }
        // Wir arbeiten auf Token-Ebene, damit die Original-Schreibweise erhalten bleibt.
        String[] tokens = text.split(" ");
        boolean matched = false;
        List<String> out = new ArrayList<>(tokens.length);
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i];
            String norm = normalize(tok).replaceAll("[^\\p{L}\\p{N}]", "");
            boolean bad = false;
            for (String w : words) {
                if (w.contains(" ")) {
                    continue;
                }
                if (norm.equals(w)
                        || (w.length() >= 4 && norm.startsWith(w) && norm.length() - w.length() <= 3)
                        || (w.length() >= 6 && norm.contains(w))) {
                    bad = true;
                    break;
                }
            }
            if (bad) {
                matched = true;
                out.add("***");
            } else {
                out.add(tok);
            }
        }
        String joined = String.join(" ", out);
        // Mehrwort-Einträge (z. B. "sieg heil") im normalisierten Gesamttext prüfen
        String whole = " " + normalize(joined).replaceAll("[^\\p{L}\\p{N}]+", " ") + " ";
        for (String w : words) {
            if (w.contains(" ") && whole.contains(" " + w + " ")) {
                matched = true;
                joined = "***";
                break;
            }
        }
        return new Result(joined, matched);
    }

    /** Prüft nur, ob ein Text (z. B. ein Minecraft-Name) ein gesperrtes Wort enthält. */
    public boolean containsBadWord(String text) {
        if (text == null) {
            return false;
        }
        String norm = normalize(text).replaceAll("[^\\p{L}\\p{N}]", "");
        for (String w : words) {
            String ww = w.replace(" ", "");
            if (ww.length() >= 4 ? norm.contains(ww) : norm.equals(ww)) {
                return true;
            }
        }
        return false;
    }

    /** Kleinbuchstaben + einfache Leetspeak-Ersetzung. */
    public static String normalize(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(l.length());
        for (int i = 0; i < l.length(); i++) {
            char c = l.charAt(i);
            sb.append(switch (c) {
                case '0' -> 'o';
                case '1', '!', '|' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                case '8' -> 'b';
                default -> c;
            });
        }
        return sb.toString();
    }
}

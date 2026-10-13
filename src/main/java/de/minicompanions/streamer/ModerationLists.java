package de.minicompanions.streamer;

import java.util.ArrayList;
import java.util.List;

/**
 * Dauerhaft gespeicherte Sperrlisten (config/minicompanions/moderation.json).
 * Das ist die einzige Zuschauer-Information, die dauerhaft gespeichert wird.
 */
public final class ModerationLists {
    public List<String> bannedViewers = new ArrayList<>();
    public List<String> bannedSkins = new ArrayList<>();
    public List<String> bannedWords = new ArrayList<>();
    public List<String> whitelist = new ArrayList<>();
    public boolean useDefaultWords = true;

    public void validate() {
        bannedViewers = clean(bannedViewers, 40);
        bannedSkins = clean(bannedSkins, 16);
        bannedWords = clean(bannedWords, 40);
        whitelist = clean(whitelist, 40);
    }

    private static List<String> clean(List<String> in, int maxLen) {
        List<String> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        for (String s : in) {
            if (s == null) {
                continue;
            }
            String t = s.strip().toLowerCase(java.util.Locale.ROOT);
            if (!t.isEmpty() && t.length() <= maxLen && !out.contains(t) && out.size() < 5000) {
                out.add(t);
            }
        }
        return out;
    }
}

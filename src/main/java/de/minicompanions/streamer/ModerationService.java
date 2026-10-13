package de.minicompanions.streamer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Blacklist, Whitelist, Wortfilter, Stummschalten, Pause und "Nachrichten aus".
 * Sperren werden über {@link #onChange} gespeichert, Stummschaltungen gelten nur für die Sitzung.
 */
public final class ModerationService {
    private ModerationLists lists;
    private final Runnable onChange;
    private final Set<String> muted = new HashSet<>();
    private final WordFilter filter;
    private volatile boolean spawnsPaused;
    private volatile boolean messagesHidden;

    public ModerationService(ModerationLists lists, Runnable onChange) {
        this.lists = lists;
        this.onChange = onChange == null ? () -> { } : onChange;
        this.filter = new WordFilter(List.of());
        rebuildFilter();
    }

    public ModerationLists lists() {
        return lists;
    }

    /** Nach Hot-Reload der Datei: neue Listen übernehmen. */
    public void replaceLists(ModerationLists newLists) {
        this.lists = newLists == null ? new ModerationLists() : newLists;
        this.lists.validate();
        rebuildFilter();
    }

    public void rebuildFilter() {
        List<String> all = new ArrayList<>(lists.bannedWords);
        if (lists.useDefaultWords) {
            all.addAll(WordFilter.DEFAULT_WORDS);
        }
        filter.set(all);
    }

    public WordFilter filter() {
        return filter;
    }

    private static String key(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT);
    }

    // --- Zuschauer ---
    public boolean isViewerBanned(String login) {
        return lists.bannedViewers.contains(key(login));
    }

    public void banViewer(String login) {
        String k = key(login);
        if (!k.isEmpty() && !lists.bannedViewers.contains(k)) {
            lists.bannedViewers.add(k);
            onChange.run();
        }
    }

    public boolean unbanViewer(String login) {
        boolean r = lists.bannedViewers.remove(key(login));
        if (r) {
            onChange.run();
        }
        return r;
    }

    public boolean isWhitelisted(String login) {
        return lists.whitelist.contains(key(login));
    }

    // --- Skins (Minecraft-Namen) ---
    public boolean isSkinBanned(String mcName) {
        return lists.bannedSkins.contains(key(mcName)) || filter.containsBadWord(mcName);
    }

    public void banSkin(String mcName) {
        String k = key(mcName);
        if (!k.isEmpty() && !lists.bannedSkins.contains(k)) {
            lists.bannedSkins.add(k);
            onChange.run();
        }
    }

    // --- Wörter ---
    public void addWord(String w) {
        String k = key(w);
        if (k.length() >= 2 && !lists.bannedWords.contains(k)) {
            lists.bannedWords.add(k);
            rebuildFilter();
            onChange.run();
        }
    }

    // --- Stummschalten (Sitzung) ---
    public boolean isMuted(String login) {
        return muted.contains(key(login));
    }

    public void mute(String login) {
        muted.add(key(login));
    }

    public void unmute(String login) {
        muted.remove(key(login));
    }

    // --- Pause / Nachrichten aus ---
    public boolean spawnsPaused() {
        return spawnsPaused;
    }

    public void setSpawnsPaused(boolean v) {
        spawnsPaused = v;
    }

    public boolean messagesHidden() {
        return messagesHidden;
    }

    public void setMessagesHidden(boolean v) {
        messagesHidden = v;
    }

    /** Nach dem Bearbeiten der Listen in der GUI. */
    public void listsChanged() {
        lists.validate();
        rebuildFilter();
        onChange.run();
    }
}

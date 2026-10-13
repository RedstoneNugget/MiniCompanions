package de.minicompanions.streamer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Attrappe der Zuschauer-Welt für Tests: merkt sich Figuren und gezeigte Blasen. */
final class FakeWorld implements ViewerWorld {
    final Map<String, String> figures = new LinkedHashMap<>();
    final Map<String, String> bubbles = new LinkedHashMap<>();
    final List<String> notices = new ArrayList<>();
    /** Wie die echte Welt: erzeugte Figuren werden gemerkt (falls gesetzt). */
    KnownViewers known;
    long now;
    int spawnCalls;

    @Override
    public boolean hasFigure(String login) {
        return figures.containsKey(login);
    }

    @Override
    public int viewerFigureCount() {
        return figures.size();
    }

    @Override
    public void spawn(SpawnQueue.Request r) {
        spawnCalls++;
        figures.put(r.login(), r.mcName());
        if (known != null) {
            known.remember(r.login(), r.mcName(), now);
        }
    }

    @Override
    public void remove(String login) {
        figures.remove(login);
        bubbles.remove(login);
    }

    @Override
    public boolean removeOldest() {
        if (figures.isEmpty()) {
            return false;
        }
        remove(figures.keySet().iterator().next());
        return true;
    }

    @Override
    public void removeAll() {
        figures.clear();
        bubbles.clear();
    }

    @Override
    public void showMessage(String login, String text, ChatRoles roles, Integer colorRgb) {
        bubbles.put(login, text);
    }

    @Override
    public void hideMessages(String login) {
        bubbles.remove(login);
    }

    @Override
    public void hideAllMessages() {
        bubbles.clear();
    }

    @Override
    public void removeBySkin(String mcName) {
        figures.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(mcName));
    }

    @Override
    public void notice(String key, Object... args) {
        notices.add(key);
    }
}

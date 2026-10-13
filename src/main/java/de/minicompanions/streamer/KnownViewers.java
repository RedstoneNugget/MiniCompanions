package de.minicompanions.streamer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gemerkte Zuschauer (config/minicompanions/viewers.json): welcher Zuschauer welchen Minecraft-Skin
 * gewählt hat. Schreibt ein Zuschauer, dessen Figur nicht (mehr) da ist - z. B. nach einem Neustart des
 * Spiels oder nachdem der Streamer-Modus aus war -, kommt seine Figur automatisch zurück.
 *
 * <p>Gespeichert werden nur Login und Minecraft-Name, keine Nachrichten. Einträge verfallen nach
 * {@link #MAX_AGE_DAYS} Tagen ohne Chat-Aktivität; Kick, Sperre und "Vergessen" löschen sie sofort.</p>
 */
public final class KnownViewers {
    public static final int MAX_ENTRIES = 1000;
    public static final int MAX_AGE_DAYS = 60;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final String LOGIN = "[a-z0-9_]{1,40}|(?:kick|yt|tt):[a-z0-9_.-]{1,64}";

    /** Login -> Eintrag, älteste zuerst. */
    public LinkedHashMap<String, Entry> viewers = new LinkedHashMap<>();
    private transient boolean dirty;

    public static final class Entry {
        public String skin;
        public long seen;

        public Entry() {
        }

        Entry(String skin, long seen) {
            this.skin = skin;
            this.seen = seen;
        }
    }

    /** Gewählter Minecraft-Name des Zuschauers oder null. */
    public String skinOf(String login) {
        Entry e = login == null ? null : viewers.get(login);
        return e == null ? null : e.skin;
    }

    /** Figur wurde erzeugt: Skin merken (bzw. aktualisieren). */
    public void remember(String login, String skin, long now) {
        if (login == null || !login.matches(LOGIN) || !CommandParser.isValidMcName(skin)) {
            return;
        }
        Entry old = viewers.remove(login);
        viewers.put(login, new Entry(skin, now));
        while (viewers.size() > MAX_ENTRIES) {
            viewers.remove(viewers.keySet().iterator().next());
        }
        if (old == null || !skin.equals(old.skin) || now - old.seen > DAY_MS) {
            dirty = true;
        }
    }

    /** Zuschauer war im Chat aktiv: Verfallszeit verlängern (höchstens einmal am Tag gespeichert). */
    public void touch(String login, long now) {
        Entry e = login == null ? null : viewers.get(login);
        if (e != null && now - e.seen > DAY_MS) {
            remember(login, e.skin, now);
        }
    }

    public void forget(String login) {
        if (login != null && viewers.remove(login) != null) {
            dirty = true;
        }
    }

    public void forgetSkin(String mcName) {
        if (mcName != null && viewers.values().removeIf(e -> e.skin.equalsIgnoreCase(mcName))) {
            dirty = true;
        }
    }

    public void clear() {
        if (!viewers.isEmpty()) {
            viewers.clear();
            dirty = true;
        }
    }

    public int size() {
        return viewers.size();
    }

    /** @return true, wenn sich seit dem letzten Aufruf etwas geändert hat (dann speichern). */
    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** Nach dem Laden: ungültige und alte Einträge entfernen. */
    public void validate(long now) {
        if (viewers == null) {
            viewers = new LinkedHashMap<>();
            return;
        }
        Iterator<Map.Entry<String, Entry>> it = viewers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            Entry v = e.getValue();
            String login = e.getKey();
            if (login == null || v == null || !login.equals(login.toLowerCase(Locale.ROOT)) || !login.matches(LOGIN)
                    || !CommandParser.isValidMcName(v.skin) || now - v.seen > MAX_AGE_DAYS * DAY_MS) {
                it.remove();
            }
        }
        while (viewers.size() > MAX_ENTRIES) {
            viewers.remove(viewers.keySet().iterator().next());
        }
    }
}

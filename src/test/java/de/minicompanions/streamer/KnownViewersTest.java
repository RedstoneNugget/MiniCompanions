package de.minicompanions.streamer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Gemerkte Zuschauer: Speichern/Laden, Aufräumen alter und ungültiger Einträge, Obergrenze. */
class KnownViewersTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void survivesSaveAndLoad() {
        KnownViewers k = new KnownViewers();
        k.remember("anna", "Notch", 1000);
        k.remember("kick:bob", "jeb_", 2000);
        k.remember("tt:lina.tt", "Grumm", 3000);
        assertTrue(k.takeDirty());
        assertFalse(k.takeDirty());
        KnownViewers loaded = GSON.fromJson(GSON.toJson(k), KnownViewers.class);
        loaded.validate(5000);
        assertEquals("Notch", loaded.skinOf("anna"));
        assertEquals("jeb_", loaded.skinOf("kick:bob"));
        assertEquals("Grumm", loaded.skinOf("tt:lina.tt"));
        assertNull(loaded.skinOf("unbekannt"));
        assertFalse(GSON.toJson(k).contains("dirty"), "interner Zustand wird nicht gespeichert");
    }

    @Test
    void invalidAndOldEntriesAreDropped() {
        String json = """
                {"viewers": {
                  "anna": {"skin": "Notch", "seen": %d},
                  "alt": {"skin": "jeb_", "seen": %d},
                  "Gross": {"skin": "Notch", "seen": %d},
                  "evil:x": {"skin": "Notch", "seen": %d},
                  "bob": {"skin": "../../x", "seen": %d},
                  "cara": null
                }}""".formatted(100 * DAY, 100 * DAY - (KnownViewers.MAX_AGE_DAYS + 1) * DAY, 100 * DAY, 100 * DAY, 100 * DAY);
        KnownViewers k = GSON.fromJson(json, KnownViewers.class);
        k.validate(100 * DAY);
        assertEquals(1, k.size());
        assertEquals("Notch", k.skinOf("anna"));
        // kaputte Datei-Inhalte
        KnownViewers empty = GSON.fromJson("{\"viewers\": null}", KnownViewers.class);
        empty.validate(0);
        assertEquals(0, empty.size());
    }

    @Test
    void invalidNamesAreNotRemembered() {
        KnownViewers k = new KnownViewers();
        k.remember("anna", "x", 0);
        k.remember("böse/../", "Notch", 0);
        k.remember(null, "Notch", 0);
        assertEquals(0, k.size());
    }

    @Test
    void sizeIsLimitedOldestGoFirst() {
        KnownViewers k = new KnownViewers();
        for (int i = 0; i < KnownViewers.MAX_ENTRIES + 50; i++) {
            k.remember("v" + i, "Notch", i);
        }
        assertEquals(KnownViewers.MAX_ENTRIES, k.size());
        assertNull(k.skinOf("v0"));
        assertEquals("Notch", k.skinOf("v" + (KnownViewers.MAX_ENTRIES + 49)));
        // wer wieder aktiv ist, rückt nach hinten und wird nicht als Nächstes verdrängt
        k.remember("v50", "jeb_", 10 * DAY);
        k.remember("neu", "Notch", 10 * DAY);
        assertEquals("jeb_", k.skinOf("v50"));
        assertNull(k.skinOf("v51"));
    }

    @Test
    void touchKeepsActiveViewersFresh() {
        KnownViewers k = new KnownViewers();
        k.remember("anna", "Notch", 0);
        k.takeDirty();
        k.touch("anna", DAY / 2);
        assertFalse(k.takeDirty(), "nicht bei jeder Nachricht speichern");
        k.touch("anna", 2 * DAY);
        assertTrue(k.takeDirty());
        k.validate(2 * DAY + (KnownViewers.MAX_AGE_DAYS - 1) * DAY);
        assertEquals("Notch", k.skinOf("anna"));
    }
}

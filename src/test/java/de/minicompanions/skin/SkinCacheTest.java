package de.minicompanions.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Beim Spielstart wird der Skin-Cache geleert - aber nur heruntergeladene Skins, sonst nichts. */
class SkinCacheTest {

    @Test
    void startupClearsDownloadedSkinsOnly(@TempDir Path dir) throws Exception {
        Path cache = Files.createDirectories(dir.resolve("cache/skins"));
        Files.write(cache.resolve("notch.png"), new byte[]{1, 2, 3});
        Files.write(cache.resolve("notch.wide"), new byte[0]);
        Files.write(cache.resolve("dia.png"), new byte[]{1});
        Files.write(cache.resolve("dia.slim"), new byte[0]);
        Files.writeString(cache.resolve("fremd.txt"), "bleibt");
        Files.createDirectories(cache.resolve("unterordner.png"));
        Path ownSkins = Files.createDirectories(dir.resolve("skins"));
        Files.write(ownSkins.resolve("meinskin.png"), new byte[]{1});

        assertEquals(2, SkinManager.clearDiskCache(cache));
        assertFalse(Files.exists(cache.resolve("notch.png")));
        assertFalse(Files.exists(cache.resolve("notch.wide")));
        assertFalse(Files.exists(cache.resolve("dia.slim")));
        assertTrue(Files.exists(cache.resolve("fremd.txt")), "andere Dateien bleiben");
        assertTrue(Files.isDirectory(cache.resolve("unterordner.png")), "Ordner bleiben");
        assertTrue(Files.exists(ownSkins.resolve("meinskin.png")), "eigene Skins bleiben");
        assertEquals(0, SkinManager.clearDiskCache(cache));
        assertEquals(0, SkinManager.clearDiskCache(dir.resolve("gibtsnicht")));
    }
}

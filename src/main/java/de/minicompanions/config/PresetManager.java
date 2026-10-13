package de.minicompanions.config;

import de.minicompanions.Mini;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Presets/Profile: Figurengruppen (inkl. Zimmer-Position) speichern, laden, als Datei oder als
 * Teilen-Code exportieren und importieren. Zuschauer-Figuren werden nie gespeichert.
 */
public final class PresetManager {
    public static final String CODE_PREFIX = "MINI1:";
    private static final int MAX_CODE = 200_000;

    /** Inhalt eines Presets. */
    public static final class Preset {
        public String name = "preset";
        public List<FigureData> figures = new ArrayList<>();
        public Integer roomX;
        public Integer roomY;
        public Integer roomW;
        public Integer roomH;
    }

    private Path dir() {
        return Mini.configs.sub("presets");
    }

    public static String safeName(String name) {
        String n = name == null ? "" : name.strip().replaceAll("[^A-Za-z0-9 _-]", "");
        if (n.length() > 32) {
            n = n.substring(0, 32);
        }
        return n.isBlank() ? "preset" : n;
    }

    public List<String> list() {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir(), "*.json")) {
            for (Path p : ds) {
                String f = p.getFileName().toString();
                out.add(f.substring(0, f.length() - 5));
            }
        } catch (IOException ignored) {
            // leer
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Aktuelle eigene Figuren als Preset. */
    public Preset current(String name) {
        Preset p = new Preset();
        p.name = safeName(name);
        for (FigureData d : Mini.configs.figures) {
            FigureData c = d.copy();
            c.id = d.id;
            p.figures.add(c);
        }
        MiniConfig cfg = Mini.cfg();
        p.roomX = cfg.roomX;
        p.roomY = cfg.roomY;
        p.roomW = cfg.roomW;
        p.roomH = cfg.roomH;
        return p;
    }

    public void save(String name) {
        Preset p = current(name);
        Mini.configs.write(dir().resolve(p.name + ".json"), p);
    }

    public Preset read(String name) {
        Path f = dir().resolve(safeName(name) + ".json");
        if (!Files.exists(f)) {
            return null;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return sanitize(ConfigManager.GSON.fromJson(r, Preset.class));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(dir().resolve(safeName(name) + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    /** Preset anwenden: ersetzen oder hinzufügen. @return Anzahl Figuren */
    public int apply(Preset p, boolean replace) {
        if (p == null) {
            return 0;
        }
        if (replace) {
            Mini.figures.clearOwn();
        }
        int n = 0;
        for (FigureData d : p.figures) {
            FigureData c = d.copy();
            c.name = Mini.figures.uniqueName(d.name);
            if (Mini.figures.add(c) != null) {
                n++;
            }
        }
        if (p.roomX != null && p.roomY != null && p.roomW != null && p.roomH != null) {
            MiniConfig cfg = Mini.cfg();
            cfg.roomX = p.roomX;
            cfg.roomY = p.roomY;
            cfg.roomW = p.roomW;
            cfg.roomH = p.roomH;
            cfg.validate();
            Mini.configs.markDirty(ConfigManager.Part.CONFIG);
        }
        return n;
    }

    // ------------------------------------------------------------------ Teilen-Code

    public String exportCode(Preset p) {
        try {
            byte[] json = ConfigManager.GSON.toJson(p).getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(json);
            }
            return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    public Preset importCode(String code) {
        if (code == null) {
            return null;
        }
        String c = code.strip();
        if (!c.startsWith(CODE_PREFIX) || c.length() > MAX_CODE) {
            return null;
        }
        try {
            byte[] data = Base64.getUrlDecoder().decode(c.substring(CODE_PREFIX.length()));
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
                byte[] json = gz.readNBytes(2_000_000);
                return sanitize(ConfigManager.GSON.fromJson(new String(json, StandardCharsets.UTF_8), Preset.class));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Importierte Daten absichern: nur gültige Felder, begrenzte Anzahl, keine Pfade in Dateinamen. */
    private static Preset sanitize(Preset p) {
        if (p == null || p.figures == null) {
            return null;
        }
        p.name = safeName(p.name);
        List<FigureData> clean = new ArrayList<>();
        for (FigureData d : p.figures) {
            if (d == null || clean.size() >= 500) {
                continue;
            }
            d.validate();
            if ("FILE".equals(d.skinType) && !d.skinValue.matches("[A-Za-z0-9 _.()-]{1,80}")) {
                d.skinType = "DEFAULT";
                d.skinValue = "";
            }
            if ("URL".equals(d.skinType) && !(d.skinValue.startsWith("https://") || d.skinValue.startsWith("http://"))) {
                d.skinType = "DEFAULT";
                d.skinValue = "";
            }
            d.accessories.removeIf(s -> s == null || s.length() > 80 || (s.startsWith("custom:") && !s.substring(7).matches("[A-Za-z0-9 _.()-]{1,64}")));
            clean.add(d);
        }
        p.figures = clean;
        return p;
    }
}

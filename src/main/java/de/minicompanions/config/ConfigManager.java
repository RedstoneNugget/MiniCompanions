package de.minicompanions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.minicompanions.streamer.KnownViewers;
import de.minicompanions.streamer.ModerationLists;
import de.minicompanions.streamer.StreamerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lädt und speichert alle Daten unter config/minicompanions/. Robust gegen kaputte Dateien:
 * Eine defekte Datei wird als *.broken-<Zeit>.json gesichert, dann wird das letzte Backup (*.bak)
 * oder der Standard geladen. Externe Änderungen werden erkannt (Hot-Reload).
 */
public final class ConfigManager {
    public static final Logger LOG = LoggerFactory.getLogger("MiniCompanions");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public enum Part { CONFIG, FIGURES, STREAMER, MODERATION, VIEWERS }

    private final Path dir;
    public MiniConfig config = new MiniConfig();
    public List<FigureData> figures = new ArrayList<>();
    public StreamerConfig streamer = new StreamerConfig();
    public ModerationLists moderation = new ModerationLists();
    public KnownViewers viewers = new KnownViewers();
    public final List<String> warnings = new ArrayList<>();

    private final EnumSet<Part> dirty = EnumSet.noneOf(Part.class);
    private long dirtySince;
    private final Map<Path, Long> knownMtime = new HashMap<>();
    private long lastHotCheck;

    public ConfigManager(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    public Path sub(String name) {
        Path p = dir.resolve(name);
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            LOG.warn("[MiniCompanions] Ordner {} nicht anlegbar: {}", p, e.toString());
        }
        return p;
    }

    private Path file(Part part) {
        return switch (part) {
            case CONFIG -> dir.resolve("config.json");
            case FIGURES -> dir.resolve("figures.json");
            case STREAMER -> dir.resolve("streamer.json");
            case MODERATION -> dir.resolve("moderation.json");
            case VIEWERS -> dir.resolve("viewers.json");
        };
    }

    // ------------------------------------------------------------------ Laden

    public void loadAll() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOG.error("[MiniCompanions] Config-Ordner nicht anlegbar", e);
        }
        for (String s : new String[]{"skins", "presets", "cache", "accessories", "lines"}) {
            sub(s);
        }
        for (Part p : Part.values()) {
            load(p);
        }
    }

    public void load(Part part) {
        switch (part) {
            case CONFIG -> {
                config = read(file(part), MiniConfig.class, MiniConfig::new);
                config.validate();
            }
            case FIGURES -> {
                Type t = new TypeToken<List<FigureData>>() {
                }.getType();
                List<FigureData> l = read(file(part), t, ArrayList::new);
                l.removeIf(java.util.Objects::isNull);
                l.forEach(FigureData::validate);
                figures = new ArrayList<>(l);
            }
            case STREAMER -> {
                streamer = read(file(part), StreamerConfig.class, StreamerConfig::new);
                streamer.validate();
            }
            case MODERATION -> {
                moderation = read(file(part), ModerationLists.class, ModerationLists::new);
                moderation.validate();
            }
            case VIEWERS -> {
                viewers = read(file(part), KnownViewers.class, KnownViewers::new);
                viewers.validate(System.currentTimeMillis());
            }
        }
    }

    private <T> T read(Path p, Type type, Supplier<T> def) {
        if (!Files.exists(p)) {
            return def.get();
        }
        try {
            T v = parse(p, type);
            if (v != null) {
                remember(p);
                return v;
            }
            throw new IOException("leer");
        } catch (Exception e) {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Path broken = p.resolveSibling(p.getFileName() + ".broken-" + stamp);
            try {
                Files.copy(p, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // egal
            }
            warnings.add(p.getFileName().toString());
            LOG.warn("[MiniCompanions] Defekte Datei {} gesichert als {} ({})", p, broken.getFileName(), e.toString());
            Path bak = p.resolveSibling(p.getFileName() + ".bak");
            if (Files.exists(bak)) {
                try {
                    T v = parse(bak, type);
                    if (v != null) {
                        LOG.info("[MiniCompanions] Backup {} geladen", bak.getFileName());
                        return v;
                    }
                } catch (Exception ignored) {
                    // Backup ebenfalls kaputt
                }
            }
            return def.get();
        }
    }

    private static <T> T parse(Path p, Type type) throws IOException {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        }
    }

    // ------------------------------------------------------------------ Speichern

    public void markDirty(Part part) {
        if (dirty.isEmpty()) {
            dirtySince = System.currentTimeMillis();
        }
        dirty.add(part);
    }

    /** Aus dem Client-Tick: gebündeltes Speichern (max. 1x pro Sekunde). */
    public void tick() {
        long now = System.currentTimeMillis();
        if (!dirty.isEmpty() && now - dirtySince > 1000) {
            flush();
        }
        if (now - lastHotCheck > 2000) {
            lastHotCheck = now;
            hotReloadCheck();
        }
    }

    public void flush() {
        for (Part p : EnumSet.copyOf(dirty.isEmpty() ? EnumSet.of(Part.CONFIG) : dirty)) {
            if (dirty.contains(p)) {
                save(p);
            }
        }
        dirty.clear();
    }

    public void saveAll() {
        for (Part p : Part.values()) {
            save(p);
        }
        dirty.clear();
    }

    public void save(Part part) {
        switch (part) {
            case CONFIG -> write(file(part), config);
            case FIGURES -> write(file(part), figures);
            case STREAMER -> write(file(part), streamer);
            case MODERATION -> write(file(part), moderation);
            case VIEWERS -> write(file(part), viewers);
        }
    }

    public void write(Path p, Object value) {
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(value, w);
            }
            if (Files.exists(p)) {
                Files.copy(p, p.resolveSibling(p.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
            remember(p);
        } catch (IOException e) {
            LOG.error("[MiniCompanions] Speichern fehlgeschlagen: {}", p, e);
        }
    }

    private void remember(Path p) {
        try {
            knownMtime.put(p, Files.getLastModifiedTime(p).toMillis());
        } catch (IOException ignored) {
            // egal
        }
    }

    // ------------------------------------------------------------------ Hot-Reload

    /** Listener für extern geänderte Dateien. */
    public interface ReloadListener {
        void reloaded(Part part);
    }

    private ReloadListener reloadListener = p -> { };

    public void setReloadListener(ReloadListener l) {
        reloadListener = l;
    }

    private void hotReloadCheck() {
        for (Part p : new Part[]{Part.CONFIG, Part.FIGURES, Part.STREAMER, Part.MODERATION}) {
            Path f = file(p);
            if (dirty.contains(p) || !Files.exists(f)) {
                continue;
            }
            try {
                long m = Files.getLastModifiedTime(f).toMillis();
                Long known = knownMtime.get(f);
                if (known != null && m != known) {
                    LOG.info("[MiniCompanions] {} wurde extern geändert - neu geladen", f.getFileName());
                    load(p);
                    remember(f);
                    reloadListener.reloaded(p);
                }
            } catch (IOException ignored) {
                // egal
            }
        }
    }

    /** Alles auf Standard zurücksetzen (vorher Sicherung in backup-<Zeit>/). */
    public Path resetAll() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Path backup = sub("backup-" + stamp);
        for (Part p : new Part[]{Part.CONFIG, Part.FIGURES, Part.STREAMER}) {
            Path f = file(p);
            try {
                if (Files.exists(f)) {
                    Files.copy(f, backup.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                // egal
            }
        }
        config = new MiniConfig();
        figures = new ArrayList<>();
        streamer = new StreamerConfig();
        saveAll();
        return backup;
    }
}

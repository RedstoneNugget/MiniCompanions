package de.minicompanions.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.minicompanions.figure.FigureEnums;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Lädt Skins asynchron (Spielername über Mojang, lokale PNG, Link) und cached sie als Texturen.
 * Netzwerk und Dekodierung laufen im Hintergrund, nur das Hochladen der Textur passiert auf dem
 * Render-Thread. 64x32-Skins werden automatisch in das 64x64-Format umgewandelt.
 */
public final class SkinManager {
    private static final Logger LOG = LoggerFactory.getLogger("MiniCompanions/Skins");
    public static final Identifier STEVE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");
    public static final Identifier ALEX = Identifier.ofVanilla("textures/entity/player/slim/alex.png");
    private static final int MAX_BYTES = 1024 * 1024;
    private static final long PLAYER_CACHE_MS = 24L * 60 * 60 * 1000;
    private static final String USER_AGENT = "MiniCompanions-Mod";
    private static final String NAME_LOOKUP = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String NAME_LOOKUP_ALT = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static final String PROFILE_LOOKUP = "https://sessionserver.mojang.com/session/minecraft/profile/";
    /** Wartezeiten für spätere Versuche, wenn Mojang gerade nicht antwortet. */
    private static final int[] RETRY_SECONDS = {15, 60, 240};

    public enum State { LOADING, READY, FAILED }

    /** Ein (ggf. noch ladender) Skin. Felder werden vom Render-Thread gelesen. */
    public static final class Skin {
        public final String key;
        public volatile Identifier texture = STEVE;
        public volatile boolean slim;
        public volatile State state = State.LOADING;
        public volatile String error;
        /** Laden ist vorübergehend fehlgeschlagen (Timeout, Mojang überlastet) und wird später erneut versucht. */
        public volatile boolean retrying;
        volatile int retries;
        volatile long fileMtime;
        /** Wird abgeschlossen, sobald der erste Ladevorgang fertig (oder fehlgeschlagen) ist. */
        public final CompletableFuture<Skin> done = new CompletableFuture<>();

        void finish(State st) {
            state = st;
            done.complete(this);
        }

        Skin(String key) {
            this.key = key;
        }

        public boolean ready() {
            return state == State.READY;
        }
    }

    private final Path skinsDir;
    private final Path cacheDir;
    private final Map<String, Skin> cache = new ConcurrentHashMap<>();
    private final Map<String, String> uuidCache = new ConcurrentHashMap<>();
    private final ExecutorService pool;
    private final HttpClient http;
    private final AtomicInteger counter = new AtomicInteger();
    private Consumer<String> errorReporter = s -> { };

    /**
     * Beim Spielstart: alle heruntergeladenen Spieler-Skins im Cache löschen. Sie werden bei Bedarf neu von
     * Mojang geladen. Eigene Skin-Dateien (Ordner skins/) sind davon nicht betroffen.
     * @return Anzahl gelöschter Skins
     */
    public static int clearDiskCache(Path cacheDir) {
        int n = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(cacheDir, "*.{png,slim,wide}")) {
            for (Path p : files) {
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && Files.deleteIfExists(p) && p.toString().endsWith(".png")) {
                    n++;
                }
            }
        } catch (IOException e) {
            LOG.warn("[MiniCompanions] Skin-Cache nicht vollständig gelöscht: {}", e.toString());
        }
        return n;
    }

    public SkinManager(Path skinsDir, Path cacheDir) {
        this.skinsDir = skinsDir;
        this.cacheDir = cacheDir;
        this.pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "MiniCompanions-Skins-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Meldungen (z. B. "Skin nicht gefunden") an den Chat weitergeben. */
    public void setErrorReporter(Consumer<String> reporter) {
        this.errorReporter = reporter;
    }

    public Path skinsDir() {
        return skinsDir;
    }

    public static String key(FigureEnums.SkinType type, String value) {
        return switch (type) {
            case DEFAULT -> "default";
            case PLAYER -> "player:" + value.toLowerCase(Locale.ROOT);
            case FILE -> "file:" + value;
            case URL -> "url:" + value;
        };
    }

    /** Liefert den Skin (startet beim ersten Aufruf das Laden). */
    public Skin get(FigureEnums.SkinType type, String value) {
        if (type == FigureEnums.SkinType.DEFAULT || value == null || value.isBlank()) {
            return defaultSkin();
        }
        String k = key(type, value.strip());
        Skin s = cache.get(k);
        if (s != null) {
            return s;
        }
        Skin created = new Skin(k);
        Skin prev = cache.putIfAbsent(k, created);
        if (prev != null) {
            return prev;
        }
        switch (type) {
            case PLAYER -> loadPlayer(created, value.strip());
            case FILE -> loadFile(created, value.strip());
            case URL -> loadUrl(created, value.strip());
            default -> created.finish(State.READY);
        }
        return created;
    }

    private Skin defaultSkin() {
        return cache.computeIfAbsent("default", k -> {
            Skin s = new Skin(k);
            s.finish(State.READY);
            return s;
        });
    }

    /**
     * Für Zuschauer-Spawns: lädt den Skin eines Minecraft-Spielers und meldet das Ergebnis.
     * Der Name muss vorher als gültiger Minecraft-Name geprüft sein.
     */
    public CompletableFuture<Skin> loadPlayerAsync(String name) {
        Skin s = get(FigureEnums.SkinType.PLAYER, name);
        if (s.state != State.LOADING) {
            return CompletableFuture.completedFuture(s);
        }
        return s.done;
    }

    // ------------------------------------------------------------------ Spieler

    private void loadPlayer(Skin s, String name) {
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            fail(s, "invalid_name", name);
            return;
        }
        pool.execute(() -> fetchPlayer(s, name));
    }

    /**
     * Holt den Skin zum Minecraft-Namen: Name -> UUID (Mojang), UUID -> Skin-Link (Sessionserver),
     * dann das PNG von textures.minecraft.net. Ergebnis wird 24 h auf der Platte gecacht.
     */
    private void fetchPlayer(Skin s, String name) {
        try {
            String lower = name.toLowerCase(Locale.ROOT);
            Path png = cacheDir.resolve(lower + ".png");
            Path meta = cacheDir.resolve(lower + ".slim");
            Path wide = cacheDir.resolve(lower + ".wide");
            if (Files.exists(png) && System.currentTimeMillis() - Files.getLastModifiedTime(png).toMillis() < PLAYER_CACHE_MS) {
                byte[] bytes = Files.readAllBytes(png);
                Boolean slim = Files.exists(meta) ? Boolean.TRUE : (Files.exists(wide) ? Boolean.FALSE : null);
                decodeAndUpload(s, bytes, slim);
                return;
            }
            String uuid = uuidCache.get(lower);
            if (uuid == null) {
                String body = getJson(NAME_LOOKUP + name, NAME_LOOKUP_ALT + name);
                if (body == null) {
                    uuidCache.put(lower, "");
                    fail(s, "unknown_player", name);
                    return;
                }
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                uuid = o.get("id").getAsString();
                if (!uuid.matches("[0-9a-fA-F]{32}")) {
                    fail(s, "bad_uuid", name);
                    return;
                }
                uuidCache.put(lower, uuid);
            } else if (uuid.isEmpty()) {
                fail(s, "unknown_player", name);
                return;
            }
            String profileBody = getJson(PROFILE_LOOKUP + uuid);
            if (profileBody == null) {
                fail(s, "unknown_player", name);
                return;
            }
            JsonObject profile = JsonParser.parseString(profileBody).getAsJsonObject();
            String skinUrl = null;
            boolean slim = false;
            JsonArray props = profile.getAsJsonArray("properties");
            if (props != null) {
                for (JsonElement e : props) {
                    JsonObject p = e.getAsJsonObject();
                    if ("textures".equals(p.get("name").getAsString())) {
                        String json = new String(Base64.getDecoder().decode(p.get("value").getAsString()), StandardCharsets.UTF_8);
                        JsonObject tex = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                        if (tex != null && tex.has("SKIN")) {
                            JsonObject skin = tex.getAsJsonObject("SKIN");
                            skinUrl = skin.get("url").getAsString();
                            JsonObject md = skin.getAsJsonObject("metadata");
                            slim = md != null && md.has("model") && "slim".equals(md.get("model").getAsString());
                        }
                    }
                }
            }
            if (skinUrl == null) {
                // Spieler ohne eigenen Skin -> Standard
                s.texture = STEVE;
                s.slim = false;
                s.finish(State.READY);
                return;
            }
            URI uri = URI.create(skinUrl.replace("http://", "https://"));
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.endsWith(".minecraft.net") && !host.equals("minecraft.net")) {
                fail(s, "bad_host", name);
                return;
            }
            byte[] bytes = download(uri);
            Files.write(png, bytes);
            Files.deleteIfExists(slim ? wide : meta);
            Files.write(slim ? meta : wide, new byte[0]);
            decodeAndUpload(s, bytes, slim);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(s, "interrupted", name);
        } catch (Exception e) {
            // Timeout, Mojang überlastet, kaputte Antwort ... -> später erneut versuchen
            failTransient(s, reasonOf(e), name);
        }
    }

    /**
     * GET mit kurzen Wiederholungen (auch über Ersatz-Adressen).
     * @return Antwort bei 200, {@code null} wenn Mojang den Spieler nicht kennt (204/404)
     */
    private String getJson(String... urls) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                Thread.sleep(1000L << attempt);
            }
            for (String url : urls) {
                try {
                    HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                            .header("User-Agent", USER_AGENT).GET().build(), HttpResponse.BodyHandlers.ofString());
                    int code = r.statusCode();
                    if (code == 200) {
                        return r.body();
                    }
                    if (code == 204 || code == 404) {
                        return null;
                    }
                    last = new IOException("http_" + code);
                } catch (IOException e) {
                    last = e;
                }
            }
        }
        throw last;
    }

    private static String reasonOf(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException) {
            return "timeout";
        }
        String m = e.getMessage();
        return m != null && m.startsWith("http_") ? m : e.getClass().getSimpleName();
    }

    /** Vorübergehender Fehler: Figur zeigt solange Steve, der Skin wird später automatisch nachgeladen. */
    private void failTransient(Skin s, String reason, String name) {
        int n = s.retries++;
        if (n >= RETRY_SECONDS.length) {
            s.retrying = false;
            fail(s, reason, name);
            return;
        }
        s.error = reason;
        s.retrying = true;
        s.finish(State.FAILED);
        LOG.info("[MiniCompanions] Skin '{}' gerade nicht ladbar ({}), neuer Versuch in {} s", name, reason, RETRY_SECONDS[n]);
        CompletableFuture.delayedExecutor(RETRY_SECONDS[n], TimeUnit.SECONDS).execute(() -> {
            if (cache.get(s.key) != s) {
                return; // inzwischen verworfen (/mini reload)
            }
            try {
                pool.execute(() -> fetchPlayer(s, name));
            } catch (RejectedExecutionException ignored) {
                // Spiel wird beendet
            }
        });
    }

    // ------------------------------------------------------------------ Datei

    /** Prüft einen Dateinamen streng: nur Name im skins-Ordner, keine Pfade. */
    public Path resolveSkinFile(String fileName) {
        if (fileName == null || !fileName.matches("[A-Za-z0-9 _.()-]{1,80}") || fileName.contains("..")) {
            return null;
        }
        String f = fileName.toLowerCase(Locale.ROOT).endsWith(".png") ? fileName : fileName + ".png";
        Path p = skinsDir.resolve(f).normalize();
        return p.startsWith(skinsDir.normalize()) ? p : null;
    }

    private void loadFile(Skin s, String fileName) {
        Path p = resolveSkinFile(fileName);
        if (p == null) {
            fail(s, "invalid_file", fileName);
            return;
        }
        pool.execute(() -> {
            try {
                if (!Files.exists(p)) {
                    fail(s, "file_missing", fileName);
                    return;
                }
                if (Files.size(p) > MAX_BYTES) {
                    fail(s, "too_large", fileName);
                    return;
                }
                s.fileMtime = Files.getLastModifiedTime(p).toMillis();
                decodeAndUpload(s, Files.readAllBytes(p), null);
            } catch (IOException e) {
                fail(s, e.getClass().getSimpleName(), fileName);
            }
        });
    }

    // ------------------------------------------------------------------ Link

    private void loadUrl(Skin s, String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException e) {
            fail(s, "invalid_url", url);
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http") || uri.getHost() == null) {
            fail(s, "invalid_url", url);
            return;
        }
        pool.execute(() -> {
            try {
                decodeAndUpload(s, download(uri), null);
            } catch (Exception e) {
                fail(s, e.getClass().getSimpleName(), url);
            }
        });
    }

    private byte[] download(URI uri) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            try {
                return downloadOnce(uri);
            } catch (IOException e) {
                if (attempt >= 2 || "too_large".equals(e.getMessage())) {
                    throw e;
                }
                Thread.sleep(1000L << attempt);
            }
        }
    }

    private byte[] downloadOnce(URI uri) throws IOException, InterruptedException {
        HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                .header("User-Agent", USER_AGENT).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) {
            r.body().close();
            throw new IOException("http_" + r.statusCode());
        }
        try (InputStream in = r.body()) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                throw new IOException("too_large");
            }
            return data;
        }
    }

    // ------------------------------------------------------------------ Dekodieren

    private void decodeAndUpload(Skin s, byte[] bytes, Boolean slimHint) {
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            fail(s, "not_png", s.key);
            return;
        }
        NativeImage img;
        try {
            img = NativeImage.read(bytes);
        } catch (IOException | RuntimeException e) {
            fail(s, "not_png", s.key);
            return;
        }
        try {
            NativeImage converted = SkinImages.normalize(img);
            if (converted == null) {
                img.close();
                fail(s, "bad_size", s.key);
                return;
            }
            if (converted != img) {
                img.close();
            }
            boolean slim = slimHint != null ? slimHint : SkinImages.detectSlim(converted);
            Identifier id = Identifier.of("minicompanions", "skins/" + hash(s.key));
            MinecraftClient.getInstance().execute(() -> {
                try {
                    var tm = MinecraftClient.getInstance().getTextureManager();
                    tm.destroyTexture(id);
                    tm.registerTexture(id, new NativeImageBackedTexture(() -> "minicompanions:" + s.key, converted));
                    s.texture = id;
                    s.slim = slim;
                    s.error = null;
                    s.retrying = false;
                    s.finish(State.READY);
                } catch (RuntimeException e) {
                    converted.close();
                    fail(s, "upload", s.key);
                }
            });
        } catch (RuntimeException e) {
            img.close();
            fail(s, "decode", s.key);
        }
    }

    private void fail(Skin s, String reason, String what) {
        s.error = reason;
        s.texture = STEVE;
        s.slim = false;
        s.finish(State.FAILED);
        LOG.info("[MiniCompanions] Skin '{}' nicht ladbar: {}", what, reason);
        MinecraftClient.getInstance().execute(() -> errorReporter.accept(reason + "|" + what));
    }

    private static String hash(String key) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 24);
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode());
        }
    }

    // ------------------------------------------------------------------ Hot-Reload

    /** Prüft lokale Skin-Dateien auf Änderungen und lädt sie neu (Hot-Reload ohne Neustart). */
    public void checkFileChanges() {
        for (Skin s : cache.values()) {
            if (!s.key.startsWith("file:") || s.state == State.LOADING) {
                continue;
            }
            Path p = resolveSkinFile(s.key.substring(5));
            try {
                if (p != null && Files.exists(p) && Files.getLastModifiedTime(p).toMillis() != s.fileMtime) {
                    s.state = State.LOADING;
                    loadFile(s, s.key.substring(5));
                }
            } catch (IOException ignored) {
                // egal
            }
        }
    }

    /** Alle Skins verwerfen und neu laden (Befehl /mini reload). */
    public void reloadAll() {
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (Skin s : cache.values()) {
            if (s.texture != STEVE && s.texture != ALEX) {
                tm.destroyTexture(s.texture);
            }
        }
        cache.clear();
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}

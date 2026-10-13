package de.minicompanions.streamer;

import de.minicompanions.Mini;
import de.minicompanions.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hauptschalter des Streamer-Modus. Steuert Start und Stopp aller Streamer-Teile:
 * Ist der Modus AUS, gibt es keine Verbindung, keinen Thread, keine Queue-Verarbeitung und keine
 * Zuschauer-Figuren. Beim Ausschalten wird alles sofort entfernt (wie bei Panik).
 */
public final class StreamerModeManager {
    private final ModerationService moderation;
    private final ViewerFigureManager viewers;
    private final BlockingQueue<ChatEvent> inbound = new ArrayBlockingQueue<>(1000);
    /** Laufende Verbindungen, eine pro Plattform (nur auf dem Client-Thread verändert). */
    private final Map<ChatPlatform, Running> running = new EnumMap<>(ChatPlatform.class);
    /** Nutzer hat "Trennen" gedrückt: nicht automatisch neu verbinden. */
    private boolean manualOff;
    private String wantedKey;
    private long wantedSince;
    private StreamerPipeline pipeline;
    private boolean enabled;
    private int dropped;
    private String error;

    public StreamerModeManager() {
        this.moderation = new ModerationService(Mini.configs.moderation, () -> Mini.configs.markDirty(ConfigManager.Part.MODERATION));
        this.viewers = new ViewerFigureManager(this);
    }

    public StreamerConfig cfg() {
        return Mini.configs.streamer;
    }

    public ModerationService moderation() {
        return moderation;
    }

    public ViewerFigureManager viewers() {
        return viewers;
    }

    public StreamerPipeline pipeline() {
        return pipeline;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Beim Spielstart: gespeicherten Zustand bzw. Auto-Einschalten wiederherstellen. */
    public void onStartup() {
        if (cfg().streamerMode || cfg().autoEnableOnStart) {
            enable();
        }
    }

    /** streamer.json wurde extern geändert: Pipeline mit den neuen Einstellungen neu aufbauen. */
    public void configReloaded() {
        if (enabled) {
            pipeline = new StreamerPipeline(cfg(), moderation, viewers, System::currentTimeMillis, () -> enabled);
        }
    }

    public void setEnabled(boolean on) {
        if (on) {
            enable();
        } else {
            disable();
        }
    }

    private void enable() {
        if (!enabled) {
            enabled = true;
            cfg().streamerMode = true;
            Mini.configs.markDirty(ConfigManager.Part.STREAMER);
            pipeline = new StreamerPipeline(cfg(), moderation, viewers, System::currentTimeMillis, () -> enabled);
            Mini.chat(Mini.t("msg.streamer_on", "Streamer-Modus AN."));
        }
        manualOff = false;
        // sofort (ohne Wartezeit) mit allen eingetragenen Plattformen verbinden
        wantedKey = null;
        reconcile(true);
    }

    private void disable() {
        if (!enabled) {
            return;
        }
        enabled = false;
        cfg().streamerMode = false;
        Mini.configs.markDirty(ConfigManager.Part.STREAMER);
        stopAll();
        inbound.clear();
        if (pipeline != null) {
            pipeline.shutdown();
        }
        pipeline = null;
        viewers.removeAll();
        Mini.chat(Mini.t("msg.streamer_off", "Streamer-Modus AUS - Verbindung getrennt, alle Zuschauer-Figuren entfernt."));
    }

    /** Knopf "Verbinden": alle eingetragenen Plattformen (neu) verbinden. */
    public void connect() {
        if (!enabled) {
            error = Mini.t("streamer.err.disabled", "Streamer-Modus ist aus.").getString();
            return;
        }
        manualOff = false;
        stopAll();
        wantedKey = null;
        reconcile(true);
    }

    /** Knopf "Trennen": alle Verbindungen trennen; automatisch verbunden wird erst wieder nach "Verbinden". */
    public void disconnect() {
        manualOff = true;
        stopAll();
    }

    /**
     * Gewünschte Verbindungen (eingetragene, eingeschaltete Plattformen) mit den laufenden abgleichen.
     * Änderungen am Kanalnamen werden erst nach kurzer Pause übernommen, damit beim Tippen nicht für jeden
     * Buchstaben neu verbunden wird.
     */
    private void reconcile(boolean immediate) {
        Map<ChatPlatform, String> wanted = cfg().activePlatforms();
        String key = wanted.toString() + "|" + cfg().tiktokApiKey;
        long now = System.currentTimeMillis();
        if (!key.equals(wantedKey)) {
            wantedKey = key;
            wantedSince = now;
            if (!immediate) {
                return;
            }
        }
        if (!immediate && now - wantedSince < 1500) {
            return;
        }
        TikTokChatSource.setApiKey(cfg().tiktokApiKey);
        error = wanted.isEmpty() ? Mini.t("streamer.err.no_channel", "Kein Kanal eingetragen.").getString() : null;
        for (ChatPlatform p : ChatPlatform.values()) {
            String want = wanted.get(p);
            Running cur = running.get(p);
            if (want == null) {
                if (cur != null) {
                    stop(p);
                }
            } else if (cur == null || !cur.input.equalsIgnoreCase(want) || !cur.key.equals(key)) {
                start(p, want, key);
            }
        }
    }

    private record Running(ChatSource source, String input, String key, AtomicBoolean alive) {
    }

    private void start(ChatPlatform p, String input, String key) {
        stop(p);
        ChatSource s = p.create();
        AtomicBoolean alive = new AtomicBoolean(true);
        running.put(p, new Running(s, input, key, alive));
        // Ereignisse einer gestoppten Verbindung werden verworfen
        s.start(input, ev -> {
            if (alive.get() && !inbound.offer(ev)) {
                dropped++;
            }
        });
    }

    private void stop(ChatPlatform p) {
        Running r = running.remove(p);
        if (r == null) {
            return;
        }
        r.alive.set(false);
        // Beenden im Hintergrund, damit der Client-Thread nie blockiert
        Thread t = new Thread(r.source::stop, "MiniCompanions-ChatStop");
        t.setDaemon(true);
        t.start();
    }

    private void stopAll() {
        for (ChatPlatform p : ChatPlatform.values()) {
            stop(p);
        }
        inbound.clear();
    }

    /** Mindestens eine Plattform verbunden? */
    public boolean isConnected() {
        for (Running r : running.values()) {
            if (r.source.status() == ChatSource.Status.CONNECTED) {
                return true;
            }
        }
        return false;
    }

    /** Status einer Plattform (DISCONNECTED, wenn sie nicht läuft). */
    public ChatSource.Status status(ChatPlatform p) {
        Running r = running.get(p);
        return r == null ? ChatSource.Status.DISCONNECTED : r.source.status();
    }

    public String lastError(ChatPlatform p) {
        Running r = running.get(p);
        return r == null ? null : r.source.lastError();
    }

    /** Gesamtstatus für das HUD-Symbol: verbunden, wenn alle laufenden verbunden (oder offline) sind. */
    public ChatSource.Status status() {
        if (running.isEmpty()) {
            return error != null && enabled && !manualOff && !cfg().activePlatforms().isEmpty()
                    ? ChatSource.Status.ERROR : ChatSource.Status.DISCONNECTED;
        }
        boolean anyConnected = false;
        boolean anyPending = false;
        boolean anyError = false;
        for (Running r : running.values()) {
            switch (r.source.status()) {
                case CONNECTED -> anyConnected = true;
                case CONNECTING, RECONNECTING -> anyPending = true;
                case ERROR -> anyError = true;
                default -> {
                }
            }
        }
        if (anyError && !anyConnected) {
            return ChatSource.Status.ERROR;
        }
        if (anyConnected) {
            return ChatSource.Status.CONNECTED;
        }
        return anyPending ? ChatSource.Status.CONNECTING : ChatSource.Status.OFFLINE;
    }

    public String lastError() {
        for (Running r : running.values()) {
            if (r.source.lastError() != null) {
                return r.source.platform().displayName() + ": " + r.source.lastError();
            }
        }
        return error;
    }

    /** Anzahl eingeschalteter Plattformen mit eingetragenem Kanal. */
    public int activePlatformCount() {
        return cfg().activePlatforms().size();
    }

    public boolean manuallyDisconnected() {
        return manualOff;
    }

    /** Läuft gerade mindestens eine Verbindung (auch beim Verbinden oder offline)? */
    public boolean hasConnections() {
        return !running.isEmpty();
    }

    public int dropped() {
        return dropped;
    }

    /** Pro Client-Tick: Ereignisse (max. 50) auf dem Client-Thread verarbeiten. */
    public void tick() {
        if (!enabled || pipeline == null) {
            if (!inbound.isEmpty()) {
                inbound.clear();
            }
            return;
        }
        if (cfg().autoConnect && !manualOff) {
            reconcile(false);
        }
        for (int i = 0; i < 50; i++) {
            ChatEvent e = inbound.poll();
            if (e == null) {
                break;
            }
            pipeline.onEvent(e);
        }
        pipeline.tick();
        viewers.tick();
    }

    /** Nachricht simulieren (Test-Befehl /mini streamtest) - nur im Streamer-Modus. */
    public StreamerPipeline.Outcome simulate(String login, String text) {
        if (!enabled || pipeline == null) {
            return StreamerPipeline.Outcome.IGNORED_DISABLED;
        }
        return simulate(ChatPlatform.TWITCH, login, text);
    }

    /** Nachricht einer bestimmten Plattform simulieren (Tests). */
    public StreamerPipeline.Outcome simulate(ChatPlatform platform, String login, String text) {
        if (!enabled || pipeline == null) {
            return StreamerPipeline.Outcome.IGNORED_DISABLED;
        }
        return pipeline.onEvent(new ChatEvent.Message(platform, platform.login(login), login, text, ChatRoles.NONE, null, null));
    }

    /** @return Anzahl entfernter Zuschauer-Figuren */
    public int panic() {
        int n = viewers.list().size();
        inbound.clear();
        if (pipeline != null) {
            pipeline.panic();
        } else {
            viewers.removeAll();
        }
        moderation.setSpawnsPaused(true);
        return n;
    }

    public void kick(String login) {
        String l = login.toLowerCase(java.util.Locale.ROOT);
        if (pipeline != null) {
            pipeline.kick(l);
        } else {
            viewers.remove(l);
        }
    }

    public void ban(String login) {
        String l = login.toLowerCase(java.util.Locale.ROOT);
        if (pipeline != null) {
            pipeline.ban(l);
        } else {
            moderation.banViewer(l);
            viewers.remove(l);
        }
    }

    public void banSkin(String mcName) {
        if (pipeline != null) {
            pipeline.banSkin(mcName);
        } else {
            moderation.banSkin(mcName);
            viewers.removeBySkin(mcName);
        }
    }

    /** Werden Zuschauer-Blasen gerade angezeigt? */
    public boolean messagesVisible() {
        return enabled && cfg().showMessages && !moderation.messagesHidden();
    }

    public static boolean takeYourStreamChatLoaded() {
        return FabricLoader.getInstance().isModLoaded("take-your-stream-chat");
    }

    /** Zusammenfassung aller Plattformen, z. B. "Twitch: verbunden · TikTok: nicht live". */
    public Text statusText() {
        if (!enabled) {
            return Mini.t("streamer.status.mode_off", "Streamer-Modus ist aus");
        }
        if (manualOff) {
            return Mini.t("streamer.status.manual_off", "Getrennt - \"Verbinden\" drücken");
        }
        if (running.isEmpty()) {
            return error != null ? Text.literal(error) : Mini.t("streamer.status.disconnected", "Getrennt");
        }
        MutableText out = Text.empty();
        boolean first = true;
        for (ChatPlatform p : running.keySet()) {
            if (!first) {
                out.append(Text.literal("  ·  ").withColor(0x808080));
            }
            first = false;
            out.append(Text.literal(p.displayName() + ": ").withColor(p.color() & 0xFFFFFF));
            out.append(platformStatusText(p));
        }
        return out;
    }

    /** Status einer einzelnen Plattform als Text. */
    public Text platformStatusText(ChatPlatform p) {
        if (!enabled) {
            return Mini.t("streamer.status.off_short", "aus");
        }
        if (!cfg().isOn(p)) {
            return Mini.t("streamer.status.platform_off", "ausgeschaltet");
        }
        if (cfg().channelOf(p) == null || cfg().channelOf(p).isBlank()) {
            return Mini.t("streamer.status.no_channel", "kein Name eingetragen");
        }
        String err = lastError(p);
        return switch (status(p)) {
            case CONNECTED -> Mini.t("streamer.status.connected_short", "verbunden");
            case CONNECTING -> Mini.t("streamer.status.connecting", "verbinde …");
            case RECONNECTING -> Mini.t("streamer.status.reconnecting_short", "verbinde neu …");
            case OFFLINE -> Mini.t("streamer.status.offline", "nicht live - prüfe jede Minute");
            case ERROR -> "invalid_channel".equals(err) ? Mini.t("streamer.status.invalid", "ungültiger Name")
                    : Mini.t("streamer.status.error", "Fehler: %s", err == null ? "?" : err);
            default -> manualOff ? Mini.t("streamer.status.disconnected", "Getrennt") : Mini.t("streamer.status.waiting", "wartet …");
        };
    }

    public void shutdown() {
        stopAll();
    }
}

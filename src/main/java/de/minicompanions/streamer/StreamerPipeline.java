package de.minicompanions.streamer;

import de.minicompanions.util.RateLimiter;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Verteilt Stream-Chat-Ereignisse: Spawn-Befehl -> {@link SpawnQueue}, normale Nachrichten ->
 * {@link ViewerMessageService}, Moderations-Ereignisse des Kanals -> Figuren/Blasen entfernen.
 * Läuft ausschließlich auf dem Client-Thread und nur bei eingeschaltetem Streamer-Modus.
 *
 * <p>Reine Logik - mit einer {@link ViewerWorld}-Attrappe in Unit-Tests prüfbar.</p>
 */
public final class StreamerPipeline {
    /** Was mit einem Ereignis passiert ist (für Tests und Statistik). */
    public enum Outcome {
        SPAWN_QUEUED, SPAWN_AWAITING_APPROVAL, BUBBLE_SHOWN, MESSAGE_AWAITING_APPROVAL, MODERATION_APPLIED,
        IGNORED_DISABLED, IGNORED_PAUSED, IGNORED_INVALID_NAME, IGNORED_BANNED, IGNORED_BANNED_SKIN, IGNORED_COOLDOWN,
        IGNORED_ROLE, IGNORED_WHITELIST, IGNORED_LIMIT, IGNORED_QUEUE_FULL, IGNORED_NO_FIGURE, IGNORED_MUTED,
        IGNORED_COMMAND, IGNORED_FILTERED, IGNORED_RATE, IGNORED_DUPLICATE, IGNORED_EMPTY, IGNORED_MESSAGES_OFF,
        IGNORED_INVALID
    }

    private final StreamerConfig cfg;
    private final ModerationService mod;
    private final ViewerWorld world;
    private final LongSupplier clock;
    private final BooleanSupplier enabled;
    private final KnownViewers known;
    private final SpawnQueue queue = new SpawnQueue();
    private final ViewerMessageService messages;
    private final RateLimiter commandCooldown = new RateLimiter();
    private final RateLimiter.Window spawnWindow = new RateLimiter.Window(32);
    private final java.util.EnumMap<Outcome, Integer> stats = new java.util.EnumMap<>(Outcome.class);

    public StreamerPipeline(StreamerConfig cfg, ModerationService mod, ViewerWorld world, LongSupplier clock, BooleanSupplier enabled) {
        this(cfg, mod, world, new KnownViewers(), clock, enabled);
    }

    public StreamerPipeline(StreamerConfig cfg, ModerationService mod, ViewerWorld world, KnownViewers known, LongSupplier clock,
                            BooleanSupplier enabled) {
        this.cfg = cfg;
        this.mod = mod;
        this.world = world;
        this.known = known;
        this.clock = clock;
        this.enabled = enabled;
        this.messages = new ViewerMessageService(cfg, mod, world, queue);
    }

    public SpawnQueue queue() {
        return queue;
    }

    public int stat(Outcome o) {
        return stats.getOrDefault(o, 0);
    }

    /** Ein Ereignis aus der Chat-Warteschlange verarbeiten. */
    public Outcome onEvent(ChatEvent e) {
        Outcome o = handle(e);
        stats.merge(o, 1, Integer::sum);
        return o;
    }

    private Outcome handle(ChatEvent e) {
        if (!enabled.getAsBoolean()) {
            return Outcome.IGNORED_DISABLED;
        }
        if (e instanceof ChatEvent.UserCleared uc) {
            // Kanal-Moderation hat den Nutzer gebannt/getimeoutet: Figur und Blasen sofort weg
            if (uc.login() != null) {
                world.remove(uc.login());
                queue.removeViewer(uc.login());
                known.forget(uc.login());
            }
            return Outcome.MODERATION_APPLIED;
        }
        if (e instanceof ChatEvent.MessageDeleted md) {
            if (md.login() != null) {
                world.hideMessages(md.login());
            }
            return Outcome.MODERATION_APPLIED;
        }
        if (e instanceof ChatEvent.ChatCleared) {
            world.hideAllMessages();
            return Outcome.MODERATION_APPLIED;
        }
        if (!(e instanceof ChatEvent.Message m) || m.login() == null || m.text() == null) {
            return Outcome.IGNORED_INVALID;
        }
        if (!m.login().matches("[a-z0-9_]{1,40}|(?:kick|yt|tt):[a-z0-9_.-]{1,64}")) {
            return Outcome.IGNORED_INVALID;
        }
        long now = clock.getAsLong();
        CommandParser.Result cmd = CommandParser.parse(m.text(), cfg.commandPrefixes, cfg.commandName);
        switch (cmd.kind()) {
            case SPAWN_VALID -> {
                Outcome o = handleSpawn(m, cmd.mcName(), now);
                if (cfg.showSkinCommandBubble && (o == Outcome.SPAWN_QUEUED) && world.hasFigure(m.login())) {
                    messages.process(m, now);
                }
                return o;
            }
            case SPAWN_INVALID -> {
                return Outcome.IGNORED_INVALID_NAME;
            }
            case OTHER_COMMAND -> {
                // Nachrichten mit Befehlspräfix werden nie als Sprechblase angezeigt
                return Outcome.IGNORED_COMMAND;
            }
            default -> {
                if (!world.hasFigure(m.login())) {
                    bringBack(m, now);
                } else {
                    known.touch(m.login(), now);
                }
                return messages.process(m, now);
            }
        }
    }

    /**
     * Zuschauer schreibt, hat aber (noch) keine Figur - z. B. nach einem Neustart des Spiels oder nachdem
     * der Streamer-Modus aus war: Hat er früher schon eine Figur gehabt, kommt sie mit demselben Skin zurück
     * und zeigt die Nachricht, sobald sie da ist. Eine Freigabe ist nicht nötig, sie war es beim ersten Mal.
     */
    private void bringBack(ChatEvent.Message m, long now) {
        String mcName = cfg.rememberViewers ? known.skinOf(m.login()) : null;
        if (mcName == null || spawnBlocked(m, mcName, false) != null
                || !spawnWindow.tryAcquire(now, cfg.maxSpawnsPerSecond, 1000)) {
            return;
        }
        spawnNow(new SpawnQueue.Request(m.login(), m.displayName(), mcName, m.roles(), m.colorRgb(), now));
    }

    /** Prüft Pause, Sperren, Whitelist, Rollen und Limit. @return Grund der Ablehnung oder null */
    private Outcome spawnBlocked(ChatEvent.Message m, String mcName, boolean notify) {
        String login = m.login();
        if (mod.spawnsPaused()) {
            return Outcome.IGNORED_PAUSED;
        }
        if (mod.isViewerBanned(login) || mod.filter().containsBadWord(login)) {
            return Outcome.IGNORED_BANNED;
        }
        if (cfg.useWhitelist && !mod.isWhitelisted(login)) {
            return Outcome.IGNORED_WHITELIST;
        }
        if (!m.roles().passes(cfg.roleFilter())) {
            return Outcome.IGNORED_ROLE;
        }
        if (mod.isSkinBanned(mcName)) {
            return Outcome.IGNORED_BANNED_SKIN;
        }
        if (cfg.viewerLimit <= 0) {
            if (notify) {
                world.notice("limit_reached", 0);
            }
            return Outcome.IGNORED_LIMIT;
        }
        boolean replacing = world.hasFigure(login);
        if (!replacing && world.viewerFigureCount() >= cfg.viewerLimit && !"REPLACE_OLDEST".equals(cfg.limitPolicy)) {
            if (notify) {
                world.notice("limit_reached", cfg.viewerLimit);
            }
            return Outcome.IGNORED_LIMIT;
        }
        return null;
    }

    private Outcome handleSpawn(ChatEvent.Message m, String mcName, long now) {
        String login = m.login();
        Outcome blocked = spawnBlocked(m, mcName, true);
        if (blocked != null) {
            return blocked;
        }
        if (!commandCooldown.tryAcquire(login, now, cfg.cooldownSeconds * 1000L)) {
            return Outcome.IGNORED_COOLDOWN;
        }
        SpawnQueue.Request r = new SpawnQueue.Request(login, m.displayName(), mcName, m.roles(), m.colorRgb(), now);
        if (cfg.approvalMode && !m.roles().broadcaster()) {
            return queue.awaitApproval(r, 50) ? Outcome.SPAWN_AWAITING_APPROVAL : Outcome.IGNORED_QUEUE_FULL;
        }
        return queue.offer(r, cfg.spawnQueueMax) ? Outcome.SPAWN_QUEUED : Outcome.IGNORED_QUEUE_FULL;
    }

    /** Pro Client-Tick: wartende Spawns mit globalem Limit pro Sekunde abarbeiten. */
    public void tick() {
        if (!enabled.getAsBoolean() || mod.spawnsPaused()) {
            return;
        }
        long now = clock.getAsLong();
        while (queue.size() > 0) {
            if (!spawnWindow.tryAcquire(now, cfg.maxSpawnsPerSecond, 1000)) {
                return;
            }
            SpawnQueue.Request r = queue.poll();
            if (r == null) {
                return;
            }
            spawnNow(r);
        }
    }

    /** Eine (freigegebene) Anfrage ausführen und dabei das Limit erneut prüfen. */
    public boolean spawnNow(SpawnQueue.Request r) {
        if (mod.isViewerBanned(r.login()) || mod.isSkinBanned(r.mcName()) || cfg.viewerLimit <= 0) {
            return false;
        }
        boolean replacing = world.hasFigure(r.login());
        if (!replacing && world.viewerFigureCount() >= cfg.viewerLimit) {
            if (!"REPLACE_OLDEST".equals(cfg.limitPolicy) || !world.removeOldest()) {
                world.notice("limit_reached", cfg.viewerLimit);
                return false;
            }
        }
        world.spawn(r);
        return true;
    }

    /** Freigabe aus dem Streamer-Fenster. */
    public boolean approve(String login) {
        SpawnQueue.Request r = queue.takeApproval(login);
        return r != null && queue.offer(r, cfg.spawnQueueMax);
    }

    public void deny(String login) {
        queue.takeApproval(login);
    }

    public void approveMessage(int index) {
        SpawnQueue.PendingMessage m = queue.takeMessageApproval(index);
        if (m != null && world.hasFigure(m.login()) && !mod.isMuted(m.login())) {
            world.showMessage(m.login(), m.text(), m.roles(), m.colorRgb());
        }
    }

    public void denyMessage(int index) {
        queue.takeMessageApproval(index);
    }

    /** Panik: alles sofort weg, Spawns pausieren. */
    public void panic() {
        mod.setSpawnsPaused(true);
        queue.clear();
        world.removeAll();
        world.hideAllMessages();
        messages.clear();
    }

    /** Zuschauer entfernen (Kick). Seine Figur kommt erst mit einem neuen !skin-Befehl zurück. */
    public void kick(String login) {
        queue.removeViewer(login);
        world.remove(login);
        messages.forget(login);
        known.forget(login);
    }

    /** Zuschauer entfernen und sperren (Ban). */
    public void ban(String login) {
        mod.banViewer(login);
        kick(login);
    }

    public void banSkin(String mcName) {
        mod.banSkin(mcName);
        world.removeBySkin(mcName);
        known.forgetSkin(mcName);
    }

    /** Beim Ausschalten des Streamer-Modus. */
    public void shutdown() {
        queue.clear();
        messages.clear();
        commandCooldown.clear();
        spawnWindow.clear();
        world.removeAll();
        world.hideAllMessages();
    }
}

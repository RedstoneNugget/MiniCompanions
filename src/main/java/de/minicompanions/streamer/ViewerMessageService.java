package de.minicompanions.streamer;

import de.minicompanions.util.RateLimiter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filtert normale Stream-Chat-Nachrichten und ordnet sie der Figur des Zuschauers als Sprechblase zu.
 * Zuschauer ohne aktive Figur werden ignoriert. Nachrichten lösen nie Aktionen aus, sie sind reine
 * Anzeige und werden nicht gespeichert.
 */
public final class ViewerMessageService {
    private final StreamerConfig cfg;
    private final ModerationService mod;
    private final ViewerWorld world;
    private final SpawnQueue queue;
    private final RateLimiter perViewer = new RateLimiter();
    private final RateLimiter.Window global = new RateLimiter.Window(64);
    /** Letzte normalisierte Nachricht pro Zuschauer (Spam-Erkennung), begrenzt. */
    private final Map<String, String> lastText = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 500;
        }
    };

    public ViewerMessageService(StreamerConfig cfg, ModerationService mod, ViewerWorld world, SpawnQueue queue) {
        this.cfg = cfg;
        this.mod = mod;
        this.world = world;
        this.queue = queue;
    }

    /** Verarbeitet eine Nachricht, die KEIN Befehl ist (oder den !skin-Befehl, falls dessen Anzeige erlaubt ist). */
    public StreamerPipeline.Outcome process(ChatEvent.Message m, long now) {
        String login = m.login();
        if (!cfg.showMessages || mod.messagesHidden()) {
            return StreamerPipeline.Outcome.IGNORED_MESSAGES_OFF;
        }
        if (!world.hasFigure(login)) {
            return StreamerPipeline.Outcome.IGNORED_NO_FIGURE;
        }
        if (mod.isMuted(login) || mod.isViewerBanned(login)) {
            return StreamerPipeline.Outcome.IGNORED_MUTED;
        }
        if (!roleAllowed(m.roles())) {
            return StreamerPipeline.Outcome.IGNORED_ROLE;
        }
        String text = MessageSanitizer.sanitize(m.text(), cfg.maxChars);
        if (text.isBlank()) {
            return StreamerPipeline.Outcome.IGNORED_EMPTY;
        }
        String norm = MessageSanitizer.normalizeForSpam(text);
        if (!norm.isEmpty() && norm.equals(lastText.get(login))) {
            return StreamerPipeline.Outcome.IGNORED_DUPLICATE;
        }
        WordFilter.Result fr = mod.filter().apply(text);
        if (fr.matched()) {
            if ("DROP".equals(cfg.censorMode)) {
                return StreamerPipeline.Outcome.IGNORED_FILTERED;
            }
            text = fr.text();
        }
        if (!perViewer.tryAcquire(login, now, cfg.minBubbleIntervalSeconds * 1000L)) {
            return StreamerPipeline.Outcome.IGNORED_RATE;
        }
        if (!global.tryAcquire(now, cfg.maxMessagesPerSecond, 1000)) {
            return StreamerPipeline.Outcome.IGNORED_RATE;
        }
        lastText.put(login, norm);
        if (cfg.approvalForMessages) {
            boolean ok = queue.awaitMessageApproval(new SpawnQueue.PendingMessage(login, m.displayName(), text, m.roles(), m.colorRgb(), now), 50);
            return ok ? StreamerPipeline.Outcome.MESSAGE_AWAITING_APPROVAL : StreamerPipeline.Outcome.IGNORED_QUEUE_FULL;
        }
        world.showMessage(login, text, m.roles(), m.colorRgb());
        return StreamerPipeline.Outcome.BUBBLE_SHOWN;
    }

    /** Anzeige pro Rolle abschaltbar. */
    private boolean roleAllowed(ChatRoles r) {
        if (r == null || r.isRegular()) {
            return cfg.bubblesRegular;
        }
        return (r.moderator() || r.broadcaster()) && cfg.bubblesMods
                || r.vip() && cfg.bubblesVips
                || r.subscriber() && cfg.bubblesSubs;
    }

    public void clear() {
        perViewer.clear();
        global.clear();
        lastText.clear();
    }

    public void forget(String login) {
        perViewer.forget(login);
        lastText.remove(login);
    }
}

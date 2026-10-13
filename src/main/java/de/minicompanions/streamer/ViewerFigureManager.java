package de.minicompanions.streamer;

import de.minicompanions.Mini;
import de.minicompanions.ai.Personality;
import de.minicompanions.config.FigureData;
import de.minicompanions.figure.BubbleQueue;
import de.minicompanions.figure.Emote;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureEnums;
import de.minicompanions.render.OverlayRenderer;
import de.minicompanions.skin.SkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Zuschauer-Figuren auf dem Bildschirm. Sie verhalten sich wie normale Figuren, können aber vom Chat
 * nicht gesteuert werden, lösen nie Inventar-/Welt-Aktionen aus und werden nie gespeichert.
 */
public final class ViewerFigureManager implements ViewerWorld {
    private static final Random RNG = new Random();
    private final Map<String, Figure> active = new LinkedHashMap<>();
    private final Set<String> pending = new HashSet<>();
    /** Nachrichten von Zuschauern, deren Figur gerade noch lädt: werden gezeigt, sobald sie da ist. */
    private final Map<String, Waiting> waiting = new HashMap<>();

    private record Waiting(String text, ChatRoles roles, Integer colorRgb) {
    }
    private final StreamerModeManager owner;
    private String lastNotice = "";
    private long lastNoticeTime;

    public ViewerFigureManager(StreamerModeManager owner) {
        this.owner = owner;
    }

    private StreamerConfig cfg() {
        return owner.cfg();
    }

    public List<Figure> list() {
        return new ArrayList<>(active.values());
    }

    public int count() {
        return active.size() + pending.size();
    }

    public String lastNotice() {
        return System.currentTimeMillis() - lastNoticeTime < 20_000 ? lastNotice : "";
    }

    @Override
    public boolean hasFigure(String login) {
        return active.containsKey(login) || pending.contains(login);
    }

    @Override
    public int viewerFigureCount() {
        return count();
    }

    @Override
    public void spawn(SpawnQueue.Request r) {
        String login = r.login();
        pending.add(login);
        Mini.skins.loadPlayerAsync(r.mcName()).thenAccept(skin -> MinecraftClient.getInstance().execute(() -> finish(r, skin)));
    }

    private void finish(SpawnQueue.Request r, SkinManager.Skin skin) {
        Waiting msg = waiting.remove(r.login());
        if (!pending.remove(r.login()) || !owner.isEnabled()) {
            return; // inzwischen gelöscht/gesperrt/Panik oder Streamer-Modus aus
        }
        if (owner.moderation().isViewerBanned(r.login()) || owner.moderation().isSkinBanned(r.mcName())) {
            return;
        }
        // Mojang antwortet gerade nicht: Figur trotzdem zeigen, der echte Skin wird automatisch nachgeladen
        boolean failed = skin.state == SkinManager.State.FAILED && !skin.retrying;
        if (failed && !cfg().unknownNameDefaultSkin) {
            notice("unknown_name", r.mcName());
            return;
        }
        if (!failed && cfg().rememberViewers) {
            // Skin merken: schreibt der Zuschauer später ohne Figur (z. B. nach einem Neustart), kommt sie zurück
            Mini.configs.viewers.remember(r.login(), r.mcName(), System.currentTimeMillis());
        }
        Figure old = active.remove(r.login());
        if (old != null) {
            Mini.figures.remove(old);
        }
        FigureData d = new FigureData();
        d.name = r.displayName();
        d.skinType = failed ? FigureEnums.SkinType.DEFAULT.name() : FigureEnums.SkinType.PLAYER.name();
        d.skinValue = failed ? "" : r.mcName();
        d.personality = Personality.random(RNG).name();
        d.validate();
        Figure f = new Figure(d, true);
        f.viewerLogin = r.login();
        f.viewerPlatform = ChatPlatform.ofLogin(r.login());
        f.viewerDisplay = sanitizeName(r.displayName(), r.login());
        f.viewerMcName = r.mcName();
        f.viewerRoles = r.roles();
        f.viewerColor = r.colorRgb();
        f.spawnedAt = System.currentTimeMillis();
        Mini.figures.addViewer(f);
        active.put(r.login(), f);
        f.showEmote(Emote.SPARKLE, 1.5f);
        if (msg != null) {
            // zurückgeholte Figur: gleich die Nachricht zeigen statt "Neu: ..."
            showMessage(r.login(), msg.text(), msg.roles(), msg.colorRgb());
        } else if (cfg().newViewerBubble || cfg().spawnGreeting) {
            String text = cfg().newViewerBubble ? Mini.t("viewer.new", "Neu: %s", f.viewerDisplay).getString()
                    : Mini.t("viewer.hello", "Hallo!").getString();
            long now = System.currentTimeMillis();
            f.bubbles.push(new BubbleQueue.Bubble(text, false, 0xFFFFF3B0, 0xFF302000, 0, 2500, now), 1, now);
        }
    }

    /** Zuschauer-Anzeigename: nur sichere Zeichen, begrenzte Länge. */
    private static String sanitizeName(String display, String login) {
        String s = MessageSanitizer.stripFormatting(display == null ? "" : display).strip();
        if (s.isEmpty() || s.length() > 25 || !s.matches("[\\p{L}\\p{N}_ .\\-]+")) {
            // Login ohne Plattform-Präfix ("kick:name" -> "name")
            return ChatPlatform.plainLogin(login);
        }
        // Wortfilter auch auf Anzeigenamen anwenden
        return Mini.streamer != null && Mini.streamer.moderation().filter().containsBadWord(s.toLowerCase(Locale.ROOT)) ? "***" : s;
    }

    /** Lebensdauer prüfen und verschwundene Figuren austragen. */
    public void tick() {
        int lifetime = cfg().lifetimeMinutes;
        long now = System.currentTimeMillis();
        var it = active.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Figure f = e.getValue();
            if (!Mini.figures.all().contains(f)) {
                it.remove();
                continue;
            }
            if (lifetime > 0 && now - f.spawnedAt > lifetime * 60_000L) {
                Mini.figures.remove(f);
                Mini.particles.sparkle(f.sx(), f.sTop(), 8);
                it.remove();
            }
            if (f.bubbles.isEmpty()) {
                f.lastViewerMessage = "";
            }
        }
    }

    @Override
    public void remove(String login) {
        pending.remove(login);
        waiting.remove(login);
        Figure f = active.remove(login);
        if (f != null) {
            Mini.figures.remove(f);
        }
    }

    @Override
    public boolean removeOldest() {
        if (active.isEmpty()) {
            return false;
        }
        String first = active.keySet().iterator().next();
        remove(first);
        return true;
    }

    @Override
    public void removeAll() {
        pending.clear();
        waiting.clear();
        active.clear();
        Mini.figures.removeAllViewers();
    }

    @Override
    public void showMessage(String login, String text, ChatRoles roles, Integer colorRgb) {
        Figure f = active.get(login);
        if (f == null && pending.contains(login)) {
            // Figur lädt noch (z. B. gerade zurückgeholt): Nachricht zeigen, sobald sie da ist
            waiting.put(login, new Waiting(text, roles, colorRgb));
            return;
        }
        if (f == null || !owner.messagesVisible()) {
            return;
        }
        StreamerConfig c = cfg();
        long now = System.currentTimeMillis();
        long dur = c.messageSeconds * 1000L + Math.min(4000, text.length() * 35L);
        int accent = c.roleColors && roles != null && !roles.isRegular() ? OverlayRenderer.roleColor(f, 0) : 0;
        f.bubbles.push(new BubbleQueue.Bubble(text, true, c.bubbleColor, c.textColor, accent, dur, now), c.bubbleQueueSize, now);
        f.hiddenMessages = false;
        f.lastViewerMessage = text;
    }

    @Override
    public void hideMessages(String login) {
        waiting.remove(login);
        Figure f = active.get(login);
        if (f != null) {
            f.bubbles.clearViewer();
            f.lastViewerMessage = "";
        }
    }

    @Override
    public void hideAllMessages() {
        waiting.clear();
        for (Figure f : active.values()) {
            f.bubbles.clearViewer();
            f.lastViewerMessage = "";
        }
    }

    @Override
    public void removeBySkin(String mcName) {
        List<String> logins = new ArrayList<>();
        for (var e : active.entrySet()) {
            if (e.getValue().viewerMcName != null && e.getValue().viewerMcName.equalsIgnoreCase(mcName)) {
                logins.add(e.getKey());
            }
        }
        logins.forEach(this::remove);
    }

    @Override
    public void notice(String key, Object... args) {
        Text t = switch (key) {
            case "limit_reached" -> Mini.t("streamer.notice.limit", "Limit erreicht (%s) - neue Befehle werden ignoriert.", args);
            case "unknown_name" -> Mini.t("streamer.notice.unknown", "Unbekannter Minecraft-Name: %s", args);
            default -> Text.literal(key);
        };
        lastNotice = t.getString();
        lastNoticeTime = System.currentTimeMillis();
    }
}

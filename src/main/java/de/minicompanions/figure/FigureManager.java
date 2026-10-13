package de.minicompanions.figure;

import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.ai.Brain;
import de.minicompanions.ai.Personality;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.config.FigureData;
import de.minicompanions.config.MiniConfig;
import de.minicompanions.physics.Body;
import de.minicompanions.physics.Ledge;
import de.minicompanions.physics.Physics;
import de.minicompanions.physics.PlatformSet;
import de.minicompanions.physics.ScreenPlatforms;
import de.minicompanions.room.RoomWindow;
import de.minicompanions.util.Rect;
import de.minicompanions.util.MathUtil;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Verwaltet alle Figuren (eigene + Zuschauer), baut pro Frame die Plattform-Welt des Bildschirms
 * und aktualisiert KI, Physik, Haustiere und Sprechblasen. Läuft ausschließlich auf dem Client-Thread.
 */
public final class FigureManager {
    private static final Random RNG = new Random();

    private final List<Figure> figures = new ArrayList<>();
    private final Map<String, Figure> byId = new HashMap<>();
    public final WorldView screen = new WorldView();
    private float saveTimer;
    private long lastFrame;
    private float walkedPx;
    private WorldView.Kind lastKind = WorldView.Kind.GAME;

    // ------------------------------------------------------------------ Verwaltung

    /** Eigene Figuren aus der Config (neu) aufbauen. Zuschauer-Figuren bleiben erhalten. */
    public void syncFromConfig() {
        Map<String, Figure> old = new HashMap<>();
        for (Figure f : figures) {
            if (!f.viewer) {
                old.put(f.id(), f);
            }
        }
        figures.removeIf(f -> !f.viewer);
        byId.values().removeIf(f -> !f.viewer);
        for (FigureData d : Mini.configs.figures) {
            Figure prev = old.get(d.id);
            Figure f;
            if (prev != null && prev.data == d) {
                f = prev;
            } else {
                f = new Figure(d, false);
                if (prev != null) {
                    f.body.x = prev.body.x;
                    f.body.y = prev.body.y;
                    f.body.leaveGround();
                } else {
                    placeNew(f);
                }
            }
            figures.add(f);
            byId.put(f.id(), f);
        }
    }

    public List<Figure> all() {
        return figures;
    }

    public Figure byId(String id) {
        return byId.get(id);
    }

    public int ownCount() {
        int n = 0;
        for (Figure f : figures) {
            if (!f.viewer) {
                n++;
            }
        }
        return n;
    }

    public boolean canAddOwn() {
        int max = Mini.cfg().maxFigures;
        return max <= 0 || ownCount() < max;
    }

    /** Neue eigene Figur hinzufügen (speichert die Config). */
    public Figure add(FigureData d) {
        if (!canAddOwn()) {
            Mini.chat(Mini.t("msg.max_figures", "Maximale Anzahl an Figuren erreicht (%s).", Mini.cfg().maxFigures));
            return null;
        }
        d.validate();
        Mini.configs.figures.add(d);
        Figure f = new Figure(d, false);
        placeNew(f);
        figures.add(f);
        byId.put(f.id(), f);
        Mini.configs.markDirty(ConfigManager.Part.FIGURES);
        return f;
    }

    /** Zuschauer-Figur hinzufügen (wird nie gespeichert). */
    public void addViewer(Figure f) {
        placeNew(f);
        figures.add(f);
        byId.put(f.id(), f);
    }

    public void remove(Figure f) {
        if (f == null) {
            return;
        }
        figures.remove(f);
        byId.remove(f.id());
        for (Figure o : figures) {
            if (o.rideOn == f) {
                o.rideOn = null;
                o.body.held = false;
            }
        }
        if (!f.viewer) {
            Mini.configs.figures.remove(f.data);
            Mini.configs.markDirty(ConfigManager.Part.FIGURES);
        }
    }

    public Figure duplicate(Figure f) {
        if (f == null || f.viewer) {
            return null;
        }
        FigureData d = f.data.copy();
        d.name = uniqueName(f.data.name);
        Figure n = add(d);
        if (n != null) {
            n.body.x = f.body.x + 8;
            n.body.y = f.body.y - 4;
            n.body.leaveGround();
        }
        return n;
    }

    public void clearOwn() {
        List<Figure> own = new ArrayList<>();
        for (Figure f : figures) {
            if (!f.viewer) {
                own.add(f);
            }
        }
        own.forEach(this::remove);
    }

    public Figure findOwn(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (Figure f : figures) {
            if (!f.viewer && f.data.name.toLowerCase(Locale.ROOT).equals(n)) {
                return f;
            }
        }
        for (Figure f : figures) {
            if (!f.viewer && f.data.name.toLowerCase(Locale.ROOT).startsWith(n)) {
                return f;
            }
        }
        return null;
    }

    public String uniqueName(String base) {
        String b = base == null || base.isBlank() ? "Mini" : base.replaceAll(" \\d+$", "");
        if (findExact(b) == null) {
            return b;
        }
        for (int i = 2; i < 10000; i++) {
            if (findExact(b + " " + i) == null) {
                return b + " " + i;
            }
        }
        return b;
    }

    private Figure findExact(String name) {
        for (Figure f : figures) {
            if (!f.viewer && f.data.name.equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    /** Zufällige Figur mit zufälligem Namen, Skin und Persönlichkeit. */
    public Figure addRandom() {
        String[] names = {"Pixel", "Krümel", "Bit", "Fips", "Nugget", "Momo", "Toast", "Kiwi", "Bobo", "Lumi", "Zap", "Moppel", "Tiny", "Wuschel"};
        String[] skins = {"Notch", "jeb_", "Dinnerbone", "Grumm", "Technoblade", "Dream", "GeorgeNotFound", "CaptainSparklez", "Herobrine"};
        FigureData d = new FigureData();
        d.name = uniqueName(names[RNG.nextInt(names.length)]);
        d.personality = Personality.random(RNG).name();
        if (RNG.nextInt(4) == 0) {
            d.skinType = FigureEnums.SkinType.DEFAULT.name();
        } else {
            d.skinType = FigureEnums.SkinType.PLAYER.name();
            d.skinValue = skins[RNG.nextInt(skins.length)];
        }
        if (RNG.nextInt(3) == 0) {
            Accessory[] acc = Accessory.values();
            d.accessories.add(acc[RNG.nextInt(acc.length)].name());
        }
        return add(d);
    }

    private void placeNew(Figure f) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float w = mc.getWindow() == null ? 320 : mc.getWindow().getScaledWidth();
        f.body.x = 20 + RNG.nextFloat() * Math.max(1, w - 40);
        f.body.y = 20;
        f.body.leaveGround();
        f.setActivity(Activity.SPAWN, 0.8f);
        f.petX = f.body.x;
        f.petY = f.body.y;
        if (Mini.particles != null) {
            Mini.particles.sparkle(f.body.x, f.body.y - 10, 12);
        }
    }

    /** Größe setzen (null = Standard). @return Anzahl betroffener Figuren */
    public int setSize(String nameOrAll, Integer percent) {
        int n = 0;
        MiniConfig cfg = Mini.cfg();
        Integer p = percent == null ? null : Math.max(cfg.minSize, Math.min(cfg.maxSize, percent));
        boolean all = "all".equalsIgnoreCase(nameOrAll) || "alle".equalsIgnoreCase(nameOrAll);
        for (Figure f : figures) {
            if (f.viewer) {
                continue;
            }
            if (all || f.data.name.equalsIgnoreCase(nameOrAll)) {
                f.data.sizePercent = p;
                n++;
            }
        }
        if (n == 0 && !all) {
            Figure f = findOwn(nameOrAll);
            if (f != null) {
                f.data.sizePercent = p;
                n = 1;
            }
        }
        if (n > 0) {
            Mini.configs.markDirty(ConfigManager.Part.FIGURES);
        }
        return n;
    }

    /** Reaktion aller Figuren (z. B. auf Spielereignisse); höchstens maxSpeakers sprechen. */
    public void reactAll(String situation, Activity a, float duration, Emote e, int maxSpeakers) {
        int speakers = 0;
        for (Figure f : figures) {
            if (f.carried) {
                continue;
            }
            boolean speak = situation != null && speakers < maxSpeakers && RNG.nextFloat() < 0.6f;
            if (speak) {
                speakers++;
            }
            f.brain.react(speak ? situation : null, a, duration, e);
        }
    }

    /** Zufällige Figur (für Kommentare). */
    public Figure randomOnScreen(boolean ownOnly) {
        List<Figure> l = new ArrayList<>();
        for (Figure f : figures) {
            if (!f.carried && (!f.inRoom || Mini.room.contentVisible()) && (!ownOnly || !f.viewer)) {
                l.add(f);
            }
        }
        return l.isEmpty() ? null : l.get(RNG.nextInt(l.size()));
    }

    // ------------------------------------------------------------------ Frame

    /** Ein Simulationsschritt pro Bild. */
    public void frame(float mouseX, float mouseY) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        MiniConfig cfg = Mini.cfg();
        double gs = mc.getWindow().getScaleFactor();
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();

        // Der Boden (unterer Rand) existiert immer als Sicherheitsnetz, damit keine Figur ins Leere fällt.
        PlatformSet ps = new PlatformSet(sw, sh);
        screen.platforms = ps;
        screen.kind = ScreenPlatforms.build(mc, cfg, ps);
        RoomWindow room = Mini.room;
        room.clamp(sw, sh);
        if (room.isOpen() && cfg.walkRoomWindow) {
            // Figuren auf dem Bildschirm können auf dem Zimmer-Fenster stehen und an ihm hochklettern
            ps.add(new Ledge("room:top", room.x(), room.x() + room.w(), room.y(), room.y() + room.h(), Ledge.Kind.OVERLAY_WINDOW, true));
        }
        for (int[] z : cfg.hideZones) {
            ps.addBlockedZone(z[0], z[1], z[2], z[3]);
        }
        screen.mouseX = mouseX;
        screen.mouseY = mouseY;
        screen.night = mc.world != null && isNight(mc);
        screen.figures.clear();

        // Zimmer-Welt: Boden des Zimmers, Koordinaten relativ zum Inhalt
        WorldView rw = room.world;
        rw.figures.clear();
        Rect wa = room.walkArea();
        rw.platforms = new PlatformSet(wa.w(), wa.h());
        rw.originX = wa.x();
        rw.originY = wa.y();
        rw.mouseX = mouseX - wa.x();
        rw.mouseY = mouseY - wa.y();
        rw.night = screen.night;
        Figure.roomOriginX = wa.x();
        Figure.roomOriginY = wa.y();
        for (Figure f : figures) {
            if (f.inRoom && !f.carried) {
                rw.figures.add(f);
            } else {
                screen.figures.add(f);
            }
        }
        addHeads(screen, cfg);
        addHeads(rw, cfg);

        Brain.planBudget = 6 + Math.min(10, figures.size() / 20);
        if (screen.kind != lastKind) {
            // Neuer Screen: Figuren suchen sich sofort neue Plattformen (Inventar, Chat, Menüs)
            lastKind = screen.kind;
            for (Figure f : screen.figures) {
                if (cfg.behaviorEnabled && f.data.behavior && !cfg.restMode) {
                    f.brain.onScreenChanged(screen);
                }
            }
        }
        simulate(screen, dt, cfg, gs);
        if (room.contentVisible()) {
            simulate(rw, dt, cfg, gs);
        }
        trackWalking(dt);

        saveTimer += dt;
        if (saveTimer > 30) {
            saveTimer = 0;
            Mini.configs.markDirty(ConfigManager.Part.FIGURES);
        }
    }

    /** Gelaufene Strecke der eigenen Figuren (für Tagesaufgaben "Laufe 100 Blöcke"; 16 px = 1 Block). */
    private void trackWalking(float dt) {
        for (Figure f : figures) {
            if (!f.viewer && f.body.onGround && !f.carried && (f.activity == Activity.WALK || f.activity == Activity.RUN)) {
                walkedPx += Math.abs(f.body.vx) * dt;
            }
        }
        if (walkedPx >= 16) {
            int blocks = (int) (walkedPx / 16);
            walkedPx -= blocks * 16;
        }
    }

    private static boolean isNight(MinecraftClient mc) {
        long t = mc.world.getTimeOfDay() % 24000L;
        return t > 13000 && t < 23000;
    }

    /** Köpfe anderer Figuren als Plattformen (Huckepack, Räuberleiter, Menschenturm). */
    private static void addHeads(WorldView w, MiniConfig cfg) {
        if (!cfg.stacking || w.figures.size() > 64) {
            return;
        }
        for (Figure f : w.figures) {
            Body b = f.body;
            if (b.onGround && !f.carried && !b.climbing && f.activity != Activity.SLEEP && f.activity != Activity.HANG) {
                float half = Math.max(2f, b.w * 0.6f);
                w.platforms.add(new Ledge("fig:" + f.id(), b.x - half, b.x + half, b.y - b.h, b.y, Ledge.Kind.FIGURE, false));
            }
        }
    }

    /** Welt einer Figur (Bildschirm oder Zimmer). */
    public WorldView worldOf(Figure f) {
        return f.inRoom && !f.carried ? Mini.room.world : screen;
    }

    /** Figur ins Zimmer setzen (x/y in Zimmer-Koordinaten). */
    public void moveToRoom(Figure f, float x, float y) {
        setRoom(f, true, x, y);
    }

    /** Figur zurück auf den Bildschirm (x/y in Bildschirm-Koordinaten). */
    public void moveToScreen(Figure f, float x, float y) {
        setRoom(f, false, x, y);
    }

    private void setRoom(Figure f, boolean inRoom, float x, float y) {
        f.inRoom = inRoom;
        if (!f.viewer) {
            f.data.inRoom = inRoom;
            Mini.configs.markDirty(ConfigManager.Part.FIGURES);
        }
        f.brain.stopNavigation();
        f.body.x = x;
        f.body.y = y;
        f.body.climbing = false;
        f.body.leaveGround();
        f.petX = x;
        f.petY = y;
        f.rideOn = null;
    }

    private void simulate(WorldView w, float dt, MiniConfig cfg, double gs) {
        long nowMs = System.currentTimeMillis();
        for (Figure f : w.figures) {
            float unit = f.unit(cfg, gs);
            f.applySize(unit);
            f.refreshSkin(Mini.skins);
            f.bubbles.update(nowMs);
            f.brain.tickLater(dt);
            Body b = f.body;
            if (f.carried && de.minicompanions.input.InputRouter.carried() != f) {
                // niemand hält die Figur mehr fest: loslassen statt in der Luft hängen zu bleiben
                f.carried = false;
                b.held = false;
                b.leaveGround();
                f.setActivity(Activity.FALL, 1);
            }
            if (f.carried) {
                f.activity = Activity.CARRIED;
                f.brain.update(w, dt);
                updatePet(f, dt);
                continue;
            }
            if (f.rideCursor) {
                b.held = true;
                b.x = MathUtil.approach(b.x, w.mouseX, 14, dt);
                b.y = MathUtil.approach(b.y, w.mouseY + 2, 14, dt);
                f.activity = Activity.RIDE;
                Physics.clamp(b, w.platforms);
                updatePet(f, dt);
                continue;
            }
            if (f.rideOn != null) {
                Figure o = f.rideOn;
                if (!figures.contains(o) || o.rideOn == f) {
                    f.rideOn = null;
                    b.held = false;
                } else {
                    b.held = true;
                    b.x = o.body.x;
                    b.y = o.body.y - o.body.h + 1;
                    f.activity = Activity.RIDE;
                    f.facing = o.facing;
                    updatePet(f, dt);
                    continue;
                }
            }
            if (f.minecart && b.onGround && f.activity != Activity.WORK && !f.brain.isHopping()) {
                Ledge l = w.platforms.get(b.ledgeId);
                if (l != null) {
                    if (f.facing == 0) {
                        f.facing = 1;
                    }
                    if (b.x <= l.x1 + b.w || b.x >= l.x2 - b.w) {
                        f.facing = b.x <= l.x1 + b.w ? 1 : -1;
                    }
                    b.vx = f.facing * 45f;
                    f.activity = Activity.RIDE;
                    Physics.step(b, w.platforms, dt, 0.4f, 0);
                    updatePet(f, dt);
                    continue;
                }
            }
            f.brain.update(w, dt);
            boolean walking = f.activity == Activity.WALK || f.activity == Activity.RUN || b.vx != 0 && f.activity == Activity.JUMP;
            if (!b.held) {
                Physics.step(b, w.platforms, dt, cfg.throwing ? 0.5f : 0f, walking ? 0 : 6f);
            }
            if (b.lastImpact > 200 && Mini.particles != null) {
                Mini.particles.dust(w.originX + b.x, w.originY + b.y, 4);
            }
            // Ausblenden-Zonen: herausschieben
            if (!b.held && w.platforms.overlapsBlocked(b.x, b.y, b.w / 2, b.h)) {
                pushOutOfZones(f, w.platforms);
            }
            updatePet(f, dt);
        }
    }

    private static void pushOutOfZones(Figure f, PlatformSet ps) {
        Body b = f.body;
        for (float[] z : ps.blockedZones()) {
            if (b.x + b.w / 2 > z[0] && b.x - b.w / 2 < z[2] && b.y > z[1] && b.y - b.h < z[3]) {
                float left = z[0] - b.w / 2 - 1;
                float right = z[2] + b.w / 2 + 1;
                b.x = (Math.abs(b.x - left) < Math.abs(b.x - right) && left > b.w) ? left : (right < ps.width - b.w ? right : left);
                b.leaveGround();
            }
        }
    }

    private void updatePet(Figure f, float dt) {
        FigureEnums.PetType type = FigureEnums.parse(FigureEnums.PetType.class, f.data.pet, FigureEnums.PetType.NONE);
        if (type == FigureEnums.PetType.NONE || !Mini.cfg().pets) {
            return;
        }
        Body b = f.body;
        f.petAnim += dt;
        boolean ownerMoving = Math.abs(b.vx) > 1 || !b.onGround;
        if (type == FigureEnums.PetType.PARROT) {
            f.petOnShoulder = !ownerMoving && f.activity != Activity.SLEEP && f.activity != Activity.CARRIED;
            float tx = f.petOnShoulder ? b.x + b.w * 0.6f : b.x - f.facing * b.w * 1.5f;
            float ty = f.petOnShoulder ? b.y - b.h * 0.72f : b.y - b.h * 1.1f + (float) Math.sin(f.petAnim * 6) * 2;
            f.petX = MathUtil.approach(f.petX, tx, 6, dt);
            f.petY = MathUtil.approach(f.petY, ty, 6, dt);
            return;
        }
        float tx = b.x - (f.facing == 0 ? 1 : f.facing) * b.w * 1.7f;
        float speed = 30 + Math.abs(b.vx) * 1.2f;
        float dx = tx - f.petX;
        if (Math.abs(dx) > 1.5f) {
            f.petX += Math.signum(dx) * Math.min(Math.abs(dx), speed * dt + Math.abs(dx) * dt * 2);
        }
        float ty = b.onGround || b.held ? b.y : f.petY;
        if (Math.abs(f.petX - b.x) > 200 || Math.abs(f.petY - b.y) > 200) {
            f.petX = b.x;
            f.petY = b.y;
        }
        f.petY = MathUtil.approach(f.petY, ty, 8, dt);
    }

    /** Figur an Position, oberste zuerst. */
    public Figure figureAt(double mx, double my) {
        for (int i = figures.size() - 1; i >= 0; i--) {
            Figure f = figures.get(i);
            if (!f.inRoom && f.hit(mx, my)) {
                return f;
            }
        }
        return null;
    }

    /** Figur im Zimmer unter der Maus (Bildschirm-Koordinaten). */
    public Figure figureAtInRoom(double mx, double my) {
        if (!Mini.room.contentVisible()) {
            return null;
        }
        double lx = mx - Figure.roomOriginX;
        double ly = my - Figure.roomOriginY;
        for (int i = figures.size() - 1; i >= 0; i--) {
            Figure f = figures.get(i);
            if (f.inRoom && f.hit(lx, ly)) {
                return f;
            }
        }
        return null;
    }

    /** Alle Zuschauer-Figuren auf einen Schlag entfernen (Panik / Streamer-Modus aus). */
    public int removeAllViewers() {
        int n = 0;
        Iterator<Figure> it = figures.iterator();
        while (it.hasNext()) {
            Figure f = it.next();
            if (f.viewer) {
                it.remove();
                byId.remove(f.id());
                n++;
            }
        }
        for (Figure f : figures) {
            if (f.rideOn != null && f.rideOn.viewer) {
                f.rideOn = null;
                f.body.held = false;
            }
        }
        return n;
    }

    /** Fotomodus: alle Figuren stellen sich nebeneinander auf und posieren. */
    public void photoPose() {
        List<Figure> l = new ArrayList<>();
        for (Figure f : figures) {
            if (!f.carried && !f.inRoom) {
                l.add(f);
            }
        }
        if (l.isEmpty()) {
            return;
        }
        float sw = screen.platforms.width;
        float total = 0;
        for (Figure f : l) {
            total += f.body.w * 2.2f;
        }
        float x = Math.max(4, (sw - total) / 2);
        for (Figure f : l) {
            f.brain.stopNavigation();
            f.brain.cancelHop();
            f.rideCursor = false;
            f.rideOn = null;
            f.body.held = false;
            f.body.x = Math.min(sw - f.body.w, x + f.body.w);
            f.body.setGround(screen.platforms.ground());
            f.facing = 0;
            f.setActivity(Activity.POSE, 6);
            x += f.body.w * 2.2f;
        }
        Mini.chat(Mini.t("msg.photo", "Fotomodus: Alle posieren! (F2 für ein Bild)"));
    }

    /** Text an eine Figur (Befehl /mini say) - wird wie Chat-Text bereinigt. */
    public static void say(Figure f, String text) {
        String clean = de.minicompanions.streamer.MessageSanitizer.sanitize(text, 120);
        f.brain.sayText(clean);
    }
}

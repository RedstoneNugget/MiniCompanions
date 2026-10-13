package de.minicompanions.input;

import de.minicompanions.Actions;
import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.figure.Emote;
import de.minicompanions.figure.Figure;
import de.minicompanions.gui.ActionMenu;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

/**
 * Leitet Maus- und Tastatureingaben an Overlay-Fenster, Figuren und das Aktionsmenü.
 * Eingaben werden nur verbraucht, wenn sie wirklich ein Overlay-Element treffen; alles andere
 * geht unverändert an Minecraft bzw. das geöffnete GUI.
 */
public final class InputRouter {
    private static Figure pressFigure;
    private static long pressTime;
    private static double pressX;
    private static double pressY;
    private static Figure carried;
    private static final double[] histX = new double[6];
    private static final double[] histY = new double[6];
    private static final long[] histT = new long[6];
    private static int histN;
    private static Figure lastClicked;
    private static long lastClickTime;
    private static float mouseX = -1000;
    private static float mouseY = -1000;
    /** Wartet auf die Auswahl einer zweiten Figur (Befreunden/Heiraten/Duell). */
    private static Figure pairSource;
    private static String pairAction;

    private InputRouter() {
    }

    public static Figure carried() {
        return carried;
    }

    /**
     * Sicherung, einmal pro Bild: Wird eine Figur noch getragen, obwohl die linke Maustaste schon losgelassen ist
     * (z. B. Fenster mit ESC, J oder K gewechselt, während man sie hielt), wird sie fallen gelassen. Sonst bliebe
     * sie in der Luft hängen.
     */
    /** Linke Maustaste gerade gedrückt (über MouseMixin mitgelesen, auch in Screens). */
    private static volatile boolean leftDown;

    public static void setLeftDown(boolean down) {
        leftDown = down;
    }

    public static void checkCarry() {
        if (leftDown) {
            return;
        }
        pressFigure = null;
        if (carried != null) {
            Figure f = carried;
            carried = null;
            histN = 0;
            drop(f, mouseX, mouseY);
        }
    }

    public static void updateMouse(float x, float y) {
        mouseX = x;
        mouseY = y;
    }

    public static float mouseX() {
        return mouseX;
    }

    public static float mouseY() {
        return mouseY;
    }

    public static void startPairSelection(Figure source, String action) {
        pairSource = source;
        pairAction = action;
        Mini.chat(Mini.t("msg.pick_partner", "Klicke jetzt auf eine zweite Figur."));
    }

    /** Für jeden Screen die Fabric-Ereignisse registrieren. */
    public static void attach(Screen screen) {
        ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !mouseClicked(click.x(), click.y(), click.button()));
        ScreenMouseEvents.allowMouseRelease(screen).register((s, click) -> !mouseReleased(click.x(), click.y(), click.button()));
        ScreenMouseEvents.allowMouseDrag(screen).register((s, click, dx, dy) -> !mouseDragged(click.x(), click.y(), click.button()));
        ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> !mouseScrolled(mx, my, v));
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> !keyPressed(s, key));
    }

    // =================================================================== Maus

    public static boolean mouseClicked(double mx, double my, int button) {
        if (Mini.configs == null || !Mini.cfg().enabled || de.minicompanions.render.RecordingMode.isHidden()) {
            return false;
        }
        // Reiten auf dem Mauszeiger beenden
        for (Figure f : Mini.figures.all()) {
            if (f.rideCursor) {
                f.rideCursor = false;
                f.body.held = false;
                f.body.leaveGround();
                f.setActivity(Activity.FALL, 1);
                return true;
            }
        }
        if (ActionMenu.isOpen()) {
            if (ActionMenu.mouseClicked(mx, my, button)) {
                return true;
            }
            ActionMenu.close();
            return true;
        }
        if (Mini.cfg().figureClicks && !guiWantsClick(mx, my)) {
            Figure f = Mini.figures.figureAt(mx, my);
            if (f == null) {
                f = Mini.figures.figureAtInRoom(mx, my);
            }
            if (f != null) {
                return figurePressed(f, mx, my, button);
            }
        }
        // Ein Klick daneben beendet den Maus-Modus NICHT (sonst wäre man nach einem knappen Fehlklick
        // sofort wieder im Spiel); verlassen wird er mit ESC, der Maus-Modus-Taste oder der Zimmer-Taste.
        return Mini.room.mouseClicked(mx, my, button);
    }

    /**
     * Liegt unter der Maus ein Knopf, Textfeld, Listeneintrag oder Slot? Dann hat das GUI Vorrang und
     * eine darauf stehende Figur fängt den Klick nicht ab. Mit gedrückter Alt-Taste greift man die Figur trotzdem.
     */
    private static boolean guiWantsClick(double mx, double my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen s = mc.currentScreen;
        if (s == null || s instanceof OverlayInteractScreen || Keys.altDown()) {
            return false;
        }
        if (s.hoveredElement(mx, my).isPresent()) {
            return true;
        }
        if (s instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> hs) {
            var acc = (de.minicompanions.mixin.HandledScreenAccessor) hs;
            int x = acc.minicompanions$getX();
            int y = acc.minicompanions$getY();
            for (var slot : hs.getScreenHandler().slots) {
                if (slot.isEnabled() && mx >= x + slot.x - 1 && mx < x + slot.x + 17 && my >= y + slot.y - 1 && my < y + slot.y + 17) {
                    // Leerer Slot und nichts am Mauszeiger: Der Klick würde nichts bewirken, also darf die Figur ihn haben
                    return slot.hasStack() || !hs.getScreenHandler().getCursorStack().isEmpty();
                }
            }
        }
        return false;
    }

    /**
     * Klick auf eine Figur (Bildschirm oder Zimmer).
     */
    public static boolean figurePressed(Figure f, double mx, double my, int button) {
        if (pairSource != null && f != pairSource) {
            completePair(f);
            return true;
        }
        switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> {
                ActionMenu.open(f, mx, my);
                return true;
            }
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> {
                pet(f);
                return true;
            }
            default -> {
                pressFigure = f;
                pressTime = System.currentTimeMillis();
                pressX = mx;
                pressY = my;
                histN = 0;
                return true;
            }
        }
    }

    private static void completePair(Figure other) {
        Figure src = pairSource;
        String action = pairAction;
        pairSource = null;
        pairAction = null;
        if (other.viewer || src.viewer) {
            return;
        }
        switch (action) {
            case "friend" -> src.brain.befriend(other);
            case "marry" -> src.brain.marry(other);
            case "duel" -> src.brain.duel(other);
            default -> {
            }
        }
    }

    public static void pet(Figure f) {
        f.brain.stopNavigation();
        f.setActivity(Activity.PETTED, 2.2f);
        f.showEmote(Emote.HEART, 2);
        f.data.mood = Math.min(100, f.data.mood + 12);
        f.brain.say("petted");
        Mini.particles.hearts(f.sx(), f.sTop(), 3);
        Mini.sounds.figure(f, "happy");
    }

    public static void tickle(Figure f) {
        f.brain.stopNavigation();
        f.setActivity(Activity.TICKLED, 2f);
        f.data.mood = Math.min(100, f.data.mood + 8);
        f.brain.say("tickled");
        Mini.sounds.figure(f, "happy");
    }

    public static boolean mouseDragged(double mx, double my, int button) {
        if (Mini.configs == null) {
            return false;
        }
        if (ActionMenu.isOpen()) {
            return ActionMenu.mouseDragged(mx, my);
        }
        float sw = MinecraftClient.getInstance().getWindow().getScaledWidth();
        float sh = MinecraftClient.getInstance().getWindow().getScaledHeight();
        if (carried == null && pressFigure != null) {
            boolean moved = Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3;
            if (moved || System.currentTimeMillis() - pressTime > 250) {
                grab(pressFigure);
            }
        }
        if (carried != null) {
            moveCarried(mx, my);
            return true;
        }
        if (Mini.room.mouseDragged(mx, my, sw, sh)) {
            return true;
        }
        return pressFigure != null;
    }

    private static void grab(Figure f) {
        carried = f;
        pressFigure = null;
        if (f.inRoom) {
            // aus dem Zimmer heraus: in Bildschirm-Koordinaten umrechnen
            Mini.figures.moveToScreen(f, f.sx(), f.body.y + Figure.roomOriginY);
        }
        f.carried = true;
        f.body.held = true;
        f.body.climbing = false;
        f.rideOn = null;
        f.brain.stopNavigation();
        f.brain.cancelHop();
        f.setActivity(Activity.CARRIED, 99);
        f.brain.say("picked");
        Mini.sounds.figure(f, "pickup");
    }

    private static void moveCarried(double mx, double my) {
        Figure f = carried;
        f.body.x = (float) mx;
        f.body.y = (float) my + f.body.h * 0.85f;
        System.arraycopy(histX, 0, histX, 1, histX.length - 1);
        System.arraycopy(histY, 0, histY, 1, histY.length - 1);
        System.arraycopy(histT, 0, histT, 1, histT.length - 1);
        histX[0] = mx;
        histY[0] = my;
        histT[0] = System.nanoTime();
        histN = Math.min(histN + 1, histX.length);
    }

    public static boolean mouseReleased(double mx, double my, int button) {
        if (Mini.configs == null) {
            return false;
        }
        if (ActionMenu.isOpen()) {
            return ActionMenu.mouseReleased(mx, my);
        }
        if (carried != null) {
            drop(carried, mx, my);
            carried = null;
            return true;
        }
        if (pressFigure != null) {
            Figure f = pressFigure;
            pressFigure = null;
            long now = System.currentTimeMillis();
            if (lastClicked == f && now - lastClickTime < 350) {
                tickle(f);
                lastClicked = null;
            } else {
                poke(f);
                lastClicked = f;
                lastClickTime = now;
            }
            return true;
        }
        return Mini.room.mouseReleased();
    }

    private static void poke(Figure f) {
        if (f.activity == Activity.SLEEP) {
            f.setActivity(Activity.YAWN, 1.2f);
            f.emote = null;
            return;
        }
        f.brain.stopNavigation();
        if (f.body.onGround) {
            f.body.leaveGround();
            f.body.vy = -110 * (float) Math.sqrt(Math.max(0.4f, f.body.h / 16f));
        }
        f.showEmote(Emote.EXCLAMATION, 1);
        f.brain.say("poke");
    }

    private static void drop(Figure f, double mx, double my) {
        f.carried = false;
        f.body.held = false;
        float vx = 0;
        float vy = 0;
        if (histN >= 2 && Mini.cfg().throwing) {
            int last = histN - 1;
            double dtSec = (histT[0] - histT[last]) / 1e9;
            if (dtSec > 0.001 && dtSec < 0.3) {
                vx = (float) ((histX[0] - histX[last]) / dtSec);
                vy = (float) ((histY[0] - histY[last]) / dtSec);
            }
        }
        float max = 700;
        vx = Math.max(-max, Math.min(max, vx));
        vy = Math.max(-max, Math.min(max, vy));
        // Ins Zimmer fallen lassen? (eigene und Zuschauer-Figuren; das Zimmer ist rein optisch)
        if (Mini.room.contentHit(mx, my)) {
            Figure.roomOriginX = Mini.room.walkArea().x();
            Figure.roomOriginY = Mini.room.walkArea().y();
            Mini.figures.moveToRoom(f, (float) mx - Figure.roomOriginX, Math.min((float) my - Figure.roomOriginY + f.body.h * 0.85f, Mini.room.walkArea().h()));
        } else {
            f.body.leaveGround();
        }
        f.body.vx = vx * 0.6f;
        f.body.vy = vy * 0.6f;
        boolean thrown = Math.hypot(vx, vy) > 220;
        f.setActivity(thrown ? Activity.THROWN : Activity.FALL, 1);
        if (thrown) {
            f.brain.say("thrown");
        }
        histN = 0;
    }

    public static boolean mouseScrolled(double mx, double my, double amount) {
        if (Mini.configs == null || !Mini.cfg().enabled) {
            return false;
        }
        boolean shift = Keys.shiftDown();
        if (ActionMenu.isOpen() && ActionMenu.mouseScrolled(mx, my, amount)) {
            return true;
        }
        if (shift && Mini.cfg().figureClicks) {
            Figure f = Mini.figures.figureAt(mx, my);
            if (f == null) {
                f = Mini.figures.figureAtInRoom(mx, my);
            }
            if (f != null) {
                resize(f, amount > 0 ? 10 : -10);
                return true;
            }
        }
        return Mini.room.hit(mx, my);
    }

    /** Größe live ändern (Umschalt + Mausrad). */
    public static void resize(Figure f, int deltaPercent) {
        var cfg = Mini.cfg();
        int cur = f.data.sizePercent != null ? f.data.sizePercent : (f.viewer ? cfg.viewerDefaultSize : cfg.globalSize);
        f.data.sizePercent = Math.max(cfg.minSize, Math.min(cfg.maxSize, cur + deltaPercent));
        if (!f.viewer) {
            Mini.configs.markDirty(de.minicompanions.config.ConfigManager.Part.FIGURES);
        }
    }

    // =================================================================== Tastatur in Screens

    private static boolean keyPressed(Screen screen, KeyInput key) {
        if (Mini.configs == null) {
            return false;
        }
        // Not-Tasten funktionieren in JEDEM Screen (auch im Chat und Inventar)
        if (Keys.matches(Keys.panic, key) && Keys.panicModifierOk()) {
            Actions.panic();
            return true;
        }
        if (Keys.matches(Keys.emergencyStop, key) && Mini.inventory.isRunning()) {
            Mini.inventory.emergencyStop(true);
            return true;
        }
        boolean typing = screen.getFocused() instanceof TextFieldWidget;
        if (typing) {
            return false;
        }
        if (ActionMenu.isOpen() && key.key() == GLFW.GLFW_KEY_ESCAPE) {
            ActionMenu.close();
            return true;
        }
        if (Keys.matches(Keys.messagesOff, key)) {
            Actions.toggleMessagesOff();
            return true;
        }
        if (Keys.matches(Keys.pauseSpawns, key)) {
            Actions.togglePauseSpawns();
            return true;
        }
        if (Keys.matches(Keys.overlayToggle, key)) {
            Actions.toggleOverlay();
            return true;
        }
        if (screen instanceof OverlayInteractScreen) {
            return Actions.handleGlobalKey(key);
        }
        return false;
    }
}

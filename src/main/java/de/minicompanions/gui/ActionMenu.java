package de.minicompanions.gui;

import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.figure.Emote;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureEnums;
import de.minicompanions.input.InputRouter;
import de.minicompanions.inventory.InventoryTasks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Rechtsklick-Menü an einer Figur. Eigene Figuren: Größe (Slider/Knöpfe), Aktionen, Aufgaben im Inventar,
 * Bearbeiten/Duplizieren/Entfernen. Zuschauer-Figuren: sofortige Moderation (Löschen, Sperren,
 * Skin sperren, Nachrichten ausblenden, Stummschalten) - ohne Bestätigungsdialog.
 */
public final class ActionMenu {
    private static final int ROW = 11;
    private static final int W = 128;

    private record Item(Text label, Runnable action, int color, List<Item> sub, boolean sizeRow) {
        static Item of(Text label, Runnable a) {
            return new Item(label, a, Ui.TEXT, null, false);
        }

        static Item danger(Text label, Runnable a) {
            return new Item(label, a, 0xFFFF8080, null, false);
        }

        static Item sub(Text label, List<Item> items) {
            return new Item(label.copy().append(" ▸"), null, Ui.TEXT, items, false);
        }
    }

    private static Figure target;
    private static List<Item> items = new ArrayList<>();
    private static final List<List<Item>> stack = new ArrayList<>();
    private static int x;
    private static int y;
    private static boolean draggingSlider;

    private ActionMenu() {
    }

    public static boolean isOpen() {
        return target != null;
    }

    public static void close() {
        target = null;
        stack.clear();
        draggingSlider = false;
    }

    public static void open(Figure f, double mx, double my) {
        target = f;
        stack.clear();
        items = f.viewer ? viewerItems(f) : ownItems(f);
        x = (int) mx + 4;
        y = (int) my;
    }

    // ------------------------------------------------------------------ Einträge

    private static List<Item> viewerItems(Figure f) {
        List<Item> l = new ArrayList<>();
        var st = Mini.streamer;
        l.add(new Item(Text.literal(f.viewerDisplay + " (MC: " + f.viewerMcName + ")"), null, Ui.ACCENT, null, false));
        l.add(new Item(Text.empty(), null, 0, null, true));
        l.add(Item.danger(Mini.t("menu.delete", "Löschen"), () -> st.kick(f.viewerLogin)));
        l.add(Item.danger(Mini.t("menu.delete_ban", "Löschen und Zuschauer sperren"), () -> st.ban(f.viewerLogin)));
        l.add(Item.danger(Mini.t("menu.ban_skin", "Skin sperren"), () -> st.banSkin(f.viewerMcName)));
        l.add(Item.of(Mini.t("menu.hide_message", "Nachricht ausblenden"), () -> f.bubbles.clearViewer()));
        boolean muted = st.moderation().isMuted(f.viewerLogin);
        l.add(Item.of(muted ? Mini.t("menu.unmute", "Stummschaltung aufheben") : Mini.t("menu.mute", "Zuschauer stummschalten"), () -> {
            if (muted) {
                st.moderation().unmute(f.viewerLogin);
            } else {
                st.moderation().mute(f.viewerLogin);
                f.bubbles.clearViewer();
            }
        }));
        return l;
    }

    private static List<Item> ownItems(Figure f) {
        List<Item> l = new ArrayList<>();
        l.add(new Item(Text.literal(f.data.name), null, Ui.ACCENT, null, false));
        l.add(new Item(Text.empty(), null, 0, null, true));
        List<Item> actions = new ArrayList<>();
        actions.add(Item.of(Mini.t("menu.talk", "Sprechen"), () -> f.brain.say("idle")));
        actions.add(Item.of(Mini.t("menu.dance", "Tanzen"), () -> act(f, Activity.DANCE, 5, Emote.NOTE)));
        actions.add(Item.of(Mini.t("menu.wave", "Winken"), () -> act(f, Activity.WAVE, 2, null)));
        actions.add(Item.of(Mini.t("menu.cheer", "Jubeln"), () -> act(f, Activity.CHEER, 2, Emote.STAR)));
        actions.add(Item.of(Mini.t("menu.follow", "Folgen (Maus)"), () -> f.brain.followMouse(20)));
        actions.add(Item.of(Mini.t("menu.pose", "Posieren"), () -> act(f, Activity.POSE, 5, null)));
        l.add(Item.sub(Mini.t("menu.actions", "Aktionen"), actions));

        List<Item> care = new ArrayList<>();
        care.add(Item.of(Mini.t("menu.feed", "Füttern"), () -> f.brain.feed()));
        care.add(Item.of(Mini.t("menu.pet", "Streicheln"), () -> InputRouter.pet(f)));
        care.add(Item.of(Mini.t("menu.tickle", "Kitzeln"), () -> InputRouter.tickle(f)));
        l.add(Item.sub(Mini.t("menu.care", "Pflegen"), care));

        if (Mini.cfg().vehicles) {
            List<Item> ride = new ArrayList<>();
            ride.add(Item.of(Mini.t("menu.ride_cursor", "Auf dem Mauszeiger reiten"), () -> {
                f.rideCursor = true;
                f.brain.stopNavigation();
            }));
            ride.add(Item.of(f.minecart ? Mini.t("menu.leave_cart", "Aus der Lore steigen") : Mini.t("menu.minecart", "Mini-Lore fahren"), () -> f.minecart = !f.minecart));
            ride.add(Item.of(Mini.t("menu.piggyback", "Huckepack (nächste Figur)"), () -> {
                Figure o = nearestOther(f);
                if (o != null) {
                    f.rideOn = o;
                    f.brain.stopNavigation();
                }
            }));
            l.add(Item.sub(Mini.t("menu.ride", "Fahren & Reiten"), ride));
        }

        List<Item> social = new ArrayList<>();
        social.add(Item.of(Mini.t("menu.befriend", "Befreunden mit …"), () -> InputRouter.startPairSelection(f, "friend")));
        social.add(Item.of(Mini.t("menu.marry", "Heiraten mit …"), () -> InputRouter.startPairSelection(f, "marry")));
        social.add(Item.of(Mini.t("menu.duel", "Duell gegen …"), () -> InputRouter.startPairSelection(f, "duel")));
        l.add(Item.sub(Mini.t("menu.social", "Freunde"), social));

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof HandledScreen<?> && Mini.inventory.available()) {
            l.add(Item.sub(Mini.t("menu.tasks", "Aufgaben"), taskItems(f)));
        }

        List<Item> look = new ArrayList<>();
        look.add(Item.of(Mini.t("menu.infoboard", "Infotafel: ").copy().append(Mini.t("info." + f.data.infoBoard.toLowerCase(), f.data.infoBoard)), () -> {
            f.data.infoBoard = FigureEnums.next(FigureEnums.parse(FigureEnums.InfoBoard.class, f.data.infoBoard, FigureEnums.InfoBoard.NONE)).name();
            dirty();
            reopen(f);
        }));
        look.add(Item.of(Mini.t("menu.pet_cycle", "Haustier: ").copy().append(Mini.t("pet." + f.data.pet.toLowerCase(), f.data.pet)), () -> {
            f.data.pet = FigureEnums.next(FigureEnums.parse(FigureEnums.PetType.class, f.data.pet, FigureEnums.PetType.NONE)).name();
            f.petX = f.body.x;
            f.petY = f.body.y;
            dirty();
            reopen(f);
        }));
        look.add(Item.of(Mini.t("menu.filter_cycle", "Farbfilter: ").copy().append(Mini.t("filter." + f.data.colorFilter.toLowerCase(), f.data.colorFilter)), () -> {
            f.data.colorFilter = FigureEnums.next(FigureEnums.parse(FigureEnums.ColorFilter.class, f.data.colorFilter, FigureEnums.ColorFilter.NONE)).name();
            dirty();
            reopen(f);
        }));
        de.minicompanions.figure.ArmorMode armor = f.data.armor.isEmpty() ? null : de.minicompanions.figure.ArmorMode.parse(f.data.armor);
        look.add(Item.of(Mini.t("menu.armor", "Rüstung: ").copy().append(armor == null ? Mini.t("armor.default", "wie in den Einstellungen")
                : Mini.t("armor." + armor.key(), armor.name())), () -> {
            // durchschalten: Einstellung -> Keine -> wie Spieler -> Leder -> ... -> Einstellung
            var all = de.minicompanions.figure.ArmorMode.values();
            f.data.armor = armor == null ? all[0].name() : armor.ordinal() + 1 < all.length ? all[armor.ordinal() + 1].name() : "";
            dirty();
            reopen(f);
        }));
        l.add(Item.sub(Mini.t("menu.look", "Aussehen"), look));

        l.add(Item.of(Mini.t("menu.edit", "Bearbeiten …"), () -> mc.setScreen(new FigureEditScreen(mc.currentScreen, f.data))));
        l.add(Item.of(Mini.t("menu.duplicate", "Duplizieren"), () -> Mini.figures.duplicate(f)));
        l.add(Item.danger(Mini.t("menu.remove", "Entfernen"), () -> Mini.figures.remove(f)));
        return l;
    }

    private static List<Item> taskItems(Figure f) {
        List<Item> t = new ArrayList<>();
        for (InventoryTasks.TaskType type : InventoryTasks.TaskType.values()) {
            if (type == InventoryTasks.TaskType.BRING_ITEM) {
                continue;
            }
            t.add(Item.of(Mini.t("task." + type.key(), type.key()), () -> Mini.inventory.start(type, f, null)));
        }
        // "Bring mir Item X in die Hotbar": Liste der Items außerhalb der Hotbar
        List<Item> bring = new ArrayList<>();
        for (ItemStack s : Mini.inventory.bringCandidates(8)) {
            ItemStack copy = s.copy();
            bring.add(Item.of(copy.getName(), () -> Mini.inventory.start(InventoryTasks.TaskType.BRING_ITEM, f, copy)));
        }
        if (!bring.isEmpty()) {
            t.add(Item.sub(Mini.t("task.bring_item", "Item in die Hotbar bringen"), bring));
        }
        return t;
    }

    private static Figure nearestOther(Figure f) {
        Figure best = null;
        float bd = Float.MAX_VALUE;
        for (Figure o : Mini.figures.all()) {
            if (o == f || o.rideOn == f) {
                continue;
            }
            float d = Math.abs(o.body.x - f.body.x) + Math.abs(o.body.y - f.body.y);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    private static void act(Figure f, Activity a, float dur, Emote e) {
        f.brain.stopNavigation();
        f.body.vx = 0;
        f.setActivity(a, dur);
        if (e != null) {
            f.showEmote(e, dur);
        }
    }

    private static void dirty() {
        Mini.configs.markDirty(de.minicompanions.config.ConfigManager.Part.FIGURES);
    }

    private static void reopen(Figure f) {
        int ox = x;
        int oy = y;
        open(f, ox - 4, oy);
    }

    // ------------------------------------------------------------------ Darstellung

    private static int height() {
        return items.size() * ROW + 4;
    }

    private static void clampPos() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        x = Math.max(2, Math.min(x, sw - W - 2));
        y = Math.max(2, Math.min(y, sh - height() - 2));
    }

    public static void renderCurrent(DrawContext ctx, TextRenderer tr, int mx, int my) {
        if (target == null) {
            return;
        }
        if (!Mini.figures.all().contains(target)) {
            close();
            return;
        }
        ctx.createNewRootLayer();
        clampPos();
        Ui.panel(ctx, x, y, W, height(), 0xF0181C26);
        int ry = y + 2;
        for (Item it : items) {
            if (it.sizeRow) {
                renderSizeRow(ctx, tr, ry, mx, my);
            } else {
                boolean hover = it.action != null || it.sub != null ? Ui.inside(mx, my, x + 1, ry, W - 2, ROW) : false;
                if (hover) {
                    ctx.fill(x + 1, ry, x + W - 1, ry + ROW, Ui.HOVER);
                }
                ctx.drawText(tr, Text.literal(Ui.fit(tr, it.label.getString(), W - 8)), x + 4, ry + 2, it.color, false);
            }
            ry += ROW;
        }
    }

    private static void renderSizeRow(DrawContext ctx, TextRenderer tr, int ry, int mx, int my) {
        var cfg = Mini.cfg();
        int size = currentSize();
        boolean own = target.data.sizePercent != null;
        // [-] [====slider====] [+] [R]
        Ui.button(ctx, tr, x + 3, ry + 1, 10, 9, Text.literal("-"), mx, my, true);
        int sx = x + 15;
        int sw = W - 46;
        ctx.fill(sx, ry + 5, sx + sw, ry + 6, 0xFF6A7898);
        float t = (size - cfg.minSize) / (float) Math.max(1, cfg.maxSize - cfg.minSize);
        int kx = sx + Math.round(t * sw);
        ctx.fill(kx - 1, ry + 2, kx + 2, ry + 9, own ? Ui.ACCENT : 0xFFC0C0C0);
        String label = size + "%";
        Ui.button(ctx, tr, x + W - 30, ry + 1, 10, 9, Text.literal("+"), mx, my, true);
        Ui.button(ctx, tr, x + W - 18, ry + 1, 15, 9, Text.literal("↺"), mx, my, own);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(sx + sw / 2f, ry + 1);
        m.scale(0.5f, 0.5f);
        ctx.drawText(tr, label, -tr.getWidth(label) / 2, 0, 0xFFFFFFFF, true);
        m.popMatrix();
    }

    private static int currentSize() {
        var cfg = Mini.cfg();
        Integer s = target.data.sizePercent;
        return s != null ? s : (target.viewer ? cfg.viewerDefaultSize : cfg.globalSize);
    }

    // ------------------------------------------------------------------ Eingabe

    public static boolean mouseClicked(double mx, double my, int button) {
        if (target == null) {
            return false;
        }
        if (!Ui.inside(mx, my, x, y, W, height())) {
            return false;
        }
        int idx = (int) ((my - y - 2) / ROW);
        if (idx < 0 || idx >= items.size()) {
            return true;
        }
        Item it = items.get(idx);
        int ry = y + 2 + idx * ROW;
        if (it.sizeRow) {
            sizeRowClick(mx, ry);
            return true;
        }
        if (it.sub != null) {
            stack.add(items);
            List<Item> sub = new ArrayList<>();
            sub.add(Item.of(Mini.t("menu.back", "◂ Zurück"), () -> {
                if (!stack.isEmpty()) {
                    items = stack.remove(stack.size() - 1);
                }
            }));
            sub.addAll(it.sub);
            items = sub;
            return true;
        }
        if (it.action != null) {
            Figure f = target;
            boolean isBack = it.label.getString().startsWith("◂");
            it.action.run();
            Mini.sounds.ui("click");
            // Umschalter (Aussehen: "Haustier: Katze") lassen das Menü offen, alles andere schließt es
            if (!isBack && target == f && !it.label.getString().contains(":")) {
                close();
            }
        }
        return true;
    }

    private static void sizeRowClick(double mx, int ry) {
        var cfg = Mini.cfg();
        if (Ui.inside(mx, ry + 2, x + 3, ry + 1, 10, 9)) {
            InputRouter.resize(target, -10);
        } else if (Ui.inside(mx, ry + 2, x + W - 30, ry + 1, 10, 9)) {
            InputRouter.resize(target, 10);
        } else if (Ui.inside(mx, ry + 2, x + W - 18, ry + 1, 15, 9)) {
            target.data.sizePercent = null;
            dirty();
        } else {
            draggingSlider = true;
            setFromSlider(mx);
        }
        if (target.data.sizePercent != null) {
            target.data.sizePercent = Math.max(cfg.minSize, Math.min(cfg.maxSize, target.data.sizePercent));
        }
    }

    private static void setFromSlider(double mx) {
        var cfg = Mini.cfg();
        int sx = x + 15;
        int sw = W - 46;
        float t = (float) Math.max(0, Math.min(1, (mx - sx) / sw));
        int v = Math.round((cfg.minSize + t * (cfg.maxSize - cfg.minSize)) / 5f) * 5;
        target.data.sizePercent = Math.max(cfg.minSize, Math.min(cfg.maxSize, v));
        if (!target.viewer) {
            dirty();
        }
    }

    public static boolean mouseDragged(double mx, double my) {
        if (draggingSlider && target != null) {
            setFromSlider(mx);
        }
        return true;
    }

    public static boolean mouseReleased(double mx, double my) {
        draggingSlider = false;
        return true;
    }

    public static boolean mouseScrolled(double mx, double my, double amount) {
        if (target != null && Ui.inside(mx, my, x, y, W, height())) {
            InputRouter.resize(target, amount > 0 ? 5 : -5);
            return true;
        }
        return false;
    }
}

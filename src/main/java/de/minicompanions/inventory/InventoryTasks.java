package de.minicompanions.inventory;

import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.figure.Emote;
import de.minicompanions.figure.Figure;
import de.minicompanions.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Inventar-Aufgaben der eigenen Figuren (nie Zuschauer-Figuren!). Es werden ausschließlich die normalen
 * Slot-Klick-Pakete verwendet (PICKUP, SWAP, QUICK_MOVE), genau wie bei einem Spieler, in menschlichem
 * Tempo mit einstellbarer Verzögerung. Nie THROW, nie Klicks außerhalb des Fensters - es wird nichts
 * gedroppt. Nach jedem Schritt wird geprüft, ob das Ergebnis stimmt (keine Items verloren, Cursor leer,
 * erwartete Belegung) - sonst Abbruch.
 */
public final class InventoryTasks {
    private static final Random RNG = new Random();

    public enum TaskType {
        SORT, STACK, BRING_ITEM, TIDY_CHEST, EQUIP_ARMOR, FOOD_TO_HOTBAR, CRAFT_PLANKS;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private enum Kind { MOVE, SWAP, QUICK, CRAFT }

    /** Ein Arbeitsschritt (Handler-Slot-IDs). */
    private record Move(Kind kind, int from, int to, int button) {
    }

    private final Deque<Move> moves = new ArrayDeque<>();
    private final List<Figure> team = new ArrayList<>();
    private Move current;
    private int phase;
    private long nextAt;
    private long phaseStart;
    private int syncId = -1;
    private TaskType type;
    private int done;
    private int total;
    private Map<String, Integer> totalsBefore;
    private ItemStack snapFrom = ItemStack.EMPTY;
    private ItemStack snapTo = ItemStack.EMPTY;
    private int teamIndex;

    // =================================================================== Status

    public boolean isRunning() {
        return type != null;
    }

    public Text statusText() {
        return Mini.t("task.running", "Aufgabe: %s (%s/%s)", Mini.t("task." + (type == null ? "none" : type.key()), type == null ? "" : type.key()), done, total);
    }

    /** Dürfen Inventar-Aktionen gerade ausgeführt werden? */
    public boolean available() {
        var cfg = Mini.cfg();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.inventoryActions || mc.player == null || mc.interactionManager == null) {
            return false;
        }
        if (cfg.inventoryOffInMultiplayer && Mini.isMultiplayer()) {
            return false;
        }
        return mc.currentScreen instanceof HandledScreen<?>;
    }

    // =================================================================== Start

    public void start(TaskType t, Figure figure, ItemStack arg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (figure != null && figure.viewer) {
            return; // Zuschauer-Figuren dürfen grundsätzlich nichts im Inventar tun
        }
        if (!available()) {
            Mini.chat(Mini.cfg().inventoryOffInMultiplayer && Mini.isMultiplayer()
                    ? Mini.t("task.off_mp", "Inventar-Aktionen sind auf Multiplayer-Servern ausgeschaltet (Einstellungen).")
                    : Mini.t("task.unavailable", "Inventar-Aktionen sind ausgeschaltet oder kein Inventar offen."));
            return;
        }
        if (isRunning()) {
            emergencyStop(false);
        }
        HandledScreen<?> hs = (HandledScreen<?>) mc.currentScreen;
        ScreenHandler h = hs.getScreenHandler();
        if (!h.getCursorStack().isEmpty()) {
            Mini.chat(Mini.t("task.cursor_busy", "Bitte zuerst das Item am Mauszeiger ablegen."));
            return;
        }
        moves.clear();
        switch (t) {
            case SORT -> planSort(h, true);
            case STACK -> planStack(h, true);
            case TIDY_CHEST -> {
                if (containerSlots(h).isEmpty()) {
                    Mini.chat(Mini.t("task.no_chest", "Hier gibt es keine Kiste zum Aufräumen."));
                    return;
                }
                planSort(h, false);
            }
            case BRING_ITEM -> planBring(h, arg);
            case EQUIP_ARMOR -> {
                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    Mini.chat(Mini.t("task.need_inventory", "Dafür bitte das normale Inventar (E) öffnen."));
                    return;
                }
                planArmor(h);
            }
            case FOOD_TO_HOTBAR -> planFood(h);
            case CRAFT_PLANKS -> planCraft(h);
        }
        if (moves.isEmpty()) {
            Mini.chat(Mini.t("task.nothing", "Nichts zu tun - schon alles ordentlich!"));
            if (figure != null) {
                figure.brain.say("workdone");
            }
            return;
        }
        type = t;
        syncId = h.syncId;
        done = 0;
        total = moves.size();
        current = null;
        phase = 0;
        nextAt = System.currentTimeMillis() + 300;
        team.clear();
        if (figure != null) {
            team.add(figure);
        }
        // Teamarbeit: bis zu 2 weitere eigene Figuren helfen sichtbar mit
        for (Figure f : Mini.figures.all()) {
            if (team.size() >= 3) {
                break;
            }
            if (!f.viewer && f != figure && !f.carried && f.data.behavior) {
                team.add(f);
            }
        }
        for (Figure f : team) {
            f.brain.stopNavigation();
            f.setActivity(Activity.WORK, 999);
            f.brain.say("work");
        }
    }

    // =================================================================== Planung

    private static boolean isPlayerMain(Slot s) {
        return s.inventory instanceof PlayerInventory && s.getIndex() >= 9 && s.getIndex() < 36;
    }

    private static boolean isHotbar(Slot s) {
        return s.inventory instanceof PlayerInventory && s.getIndex() >= 0 && s.getIndex() < 9;
    }

    private static List<Slot> containerSlots(ScreenHandler h) {
        List<Slot> l = new ArrayList<>();
        if (h instanceof PlayerScreenHandler || h instanceof CraftingScreenHandler) {
            return l;
        }
        for (Slot s : h.slots) {
            if (!(s.inventory instanceof PlayerInventory) && s.isEnabled() && s.canTakeItems(MinecraftClient.getInstance().player)) {
                l.add(s);
            }
        }
        // nur echte Lager (Kisten, Fässer, Shulker): mindestens 9 Slots, sonst z. B. Ofen/Amboss
        return l.size() >= 9 ? l : new ArrayList<>();
    }

    private static String key(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).toString();
    }

    /** Stapeln: Teilstapel gleicher Items zusammenführen. */
    private void planStack(ScreenHandler h, boolean player) {
        List<Slot> slots = new ArrayList<>();
        for (Slot s : h.slots) {
            if (player ? (isPlayerMain(s) || isHotbar(s)) : containerSlots(h).contains(s)) {
                slots.add(s);
            }
        }
        // Simulation der Belegung, damit die Planung zum erwarteten Ergebnis passt
        Map<Integer, ItemStack> sim = new HashMap<>();
        for (Slot s : slots) {
            sim.put(s.id, s.getStack().copy());
        }
        for (int i = 0; i < slots.size(); i++) {
            ItemStack a = sim.get(slots.get(i).id);
            if (a.isEmpty() || a.getCount() >= a.getMaxCount()) {
                continue;
            }
            for (int j = slots.size() - 1; j > i; j--) {
                ItemStack b = sim.get(slots.get(j).id);
                if (b.isEmpty() || !ItemStack.areItemsAndComponentsEqual(a, b)) {
                    continue;
                }
                int space = a.getMaxCount() - a.getCount();
                if (space <= 0) {
                    break;
                }
                int moved = Math.min(space, b.getCount());
                moves.add(new Move(Kind.MOVE, slots.get(j).id, slots.get(i).id, 0));
                a.increment(moved);
                b.decrement(moved);
                if (moves.size() > 60) {
                    return;
                }
            }
        }
    }

    /** Sortieren: erst stapeln, dann nach Item-ID ordnen (Auswahlsortierung über Tausch-Schritte). */
    private void planSort(ScreenHandler h, boolean player) {
        planStack(h, player);
        List<Slot> slots = new ArrayList<>();
        if (player) {
            for (Slot s : h.slots) {
                if (isPlayerMain(s)) {
                    slots.add(s);
                }
            }
        } else {
            slots.addAll(containerSlots(h));
        }
        slots.sort(Comparator.comparingInt(Slot::getIndex));
        // erwartete Belegung nach dem Stapeln simulieren
        Map<Integer, ItemStack> sim = new HashMap<>();
        for (Slot s : slots) {
            sim.put(s.id, s.getStack().copy());
        }
        for (Move m : moves) {
            ItemStack from = sim.get(m.from);
            ItemStack to = sim.get(m.to);
            if (from == null || to == null) {
                continue;
            }
            int moved = Math.min(to.getMaxCount() - to.getCount(), from.getCount());
            to.increment(moved);
            from.decrement(moved);
        }
        Comparator<ItemStack> order = Comparator.<ItemStack, Boolean>comparing(ItemStack::isEmpty)
                .thenComparing(InventoryTasks::key)
                .thenComparing(s -> -s.getCount());
        for (int i = 0; i < slots.size(); i++) {
            int best = i;
            for (int j = i + 1; j < slots.size(); j++) {
                if (order.compare(sim.get(slots.get(j).id), sim.get(slots.get(best).id)) < 0) {
                    best = j;
                }
            }
            if (best != i) {
                ItemStack a = sim.get(slots.get(i).id);
                ItemStack b = sim.get(slots.get(best).id);
                if (!a.isEmpty() && ItemStack.areItemsAndComponentsEqual(a, b)) {
                    continue; // gleiche Items: nicht tauschen (würde stapeln)
                }
                moves.add(new Move(Kind.MOVE, slots.get(best).id, slots.get(i).id, 0));
                sim.put(slots.get(i).id, b);
                sim.put(slots.get(best).id, a);
            }
            if (moves.size() > 80) {
                return;
            }
        }
    }

    private int freeHotbar(ScreenHandler h) {
        for (Slot s : h.slots) {
            if (isHotbar(s) && !s.hasStack()) {
                return s.getIndex();
            }
        }
        return -1;
    }

    private void planBring(ScreenHandler h, ItemStack want) {
        if (want == null || want.isEmpty()) {
            return;
        }
        for (Slot s : h.slots) {
            if (isPlayerMain(s) && s.hasStack() && ItemStack.areItemsAndComponentsEqual(s.getStack(), want)) {
                int hb = freeHotbar(h);
                if (hb < 0) {
                    hb = MinecraftClient.getInstance().player.getInventory().getSelectedSlot();
                }
                moves.add(new Move(Kind.SWAP, s.id, -1, hb));
                return;
            }
        }
    }

    private void planFood(ScreenHandler h) {
        List<Integer> freeHb = new ArrayList<>();
        for (Slot s : h.slots) {
            if (isHotbar(s) && !s.hasStack()) {
                freeHb.add(s.getIndex());
            }
        }
        for (Slot s : h.slots) {
            if (freeHb.isEmpty()) {
                return;
            }
            if (isPlayerMain(s) && s.hasStack() && s.getStack().contains(DataComponentTypes.FOOD)) {
                moves.add(new Move(Kind.SWAP, s.id, -1, freeHb.remove(0)));
            }
        }
    }

    private void planArmor(ScreenHandler h) {
        var pl = MinecraftClient.getInstance().player;
        java.util.Set<net.minecraft.entity.EquipmentSlot> planned = new java.util.HashSet<>();
        for (Slot s : h.slots) {
            if (!(isPlayerMain(s) || isHotbar(s)) || !s.hasStack()) {
                continue;
            }
            var eq = s.getStack().get(DataComponentTypes.EQUIPPABLE);
            if (eq == null) {
                continue;
            }
            var slot = eq.slot();
            if (slot.getType() != net.minecraft.entity.EquipmentSlot.Type.HUMANOID_ARMOR || planned.contains(slot)) {
                continue;
            }
            if (pl.getEquippedStack(slot).isEmpty()) {
                planned.add(slot);
                moves.add(new Move(Kind.QUICK, s.id, -1, 0));
            }
        }
    }

    private void planCraft(ScreenHandler h) {
        int grid;
        if (h instanceof PlayerScreenHandler || h instanceof CraftingScreenHandler) {
            grid = 1;
        } else {
            Mini.chat(Mini.t("task.need_crafting", "Dafür bitte Inventar oder Werkbank öffnen."));
            return;
        }
        if (h.getSlot(grid).hasStack() || h.getSlot(0).hasStack()) {
            Mini.chat(Mini.t("task.grid_busy", "Das Crafting-Raster ist nicht leer."));
            return;
        }
        for (Slot s : h.slots) {
            if ((isPlayerMain(s) || isHotbar(s)) && s.hasStack()) {
                String id = key(s.getStack());
                if (id.endsWith("_log") || id.endsWith("_stem") || id.endsWith("_wood") || id.endsWith("_hyphae")) {
                    int n = Math.min(4, s.getStack().getCount());
                    for (int i = 0; i < n; i++) {
                        moves.add(new Move(Kind.CRAFT, s.id, grid, 0));
                    }
                    return;
                }
            }
        }
    }

    /** Kandidaten für "Item in die Hotbar bringen". */
    public List<ItemStack> bringCandidates(int max) {
        List<ItemStack> out = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?> hs)) {
            return out;
        }
        for (Slot s : hs.getScreenHandler().slots) {
            if (isPlayerMain(s) && s.hasStack()) {
                boolean dup = false;
                for (ItemStack o : out) {
                    if (ItemStack.areItemsAndComponentsEqual(o, s.getStack())) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    out.add(s.getStack());
                }
                if (out.size() >= max) {
                    break;
                }
            }
        }
        return out;
    }

    // =================================================================== Ausführung (pro Bild)

    public void frame(float dt) {
        if (type == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?> hs) || hs.getScreenHandler().syncId != syncId || mc.player == null
                || !Mini.cfg().inventoryActions || (Mini.cfg().inventoryOffInMultiplayer && Mini.isMultiplayer())) {
            finish(false, Mini.t("task.aborted_screen", "Aufgabe abgebrochen (Inventar geschlossen)."));
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextAt) {
            return;
        }
        ScreenHandler h = hs.getScreenHandler();
        if (current == null) {
            current = moves.pollFirst();
            phase = 0;
            phaseStart = now;
            if (current == null) {
                finish(true, Mini.t("task.done", "Aufgabe erledigt!"));
                return;
            }
        }
        step(hs, h, now);
    }

    private long delay() {
        return Mini.cfg().inventoryClickDelayMs + RNG.nextInt(Mini.cfg().inventoryJitterMs + 1);
    }

    private long settle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int ping = 50;
        if (mc.getNetworkHandler() != null && mc.player != null) {
            var e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) {
                ping = e.getLatency();
            }
        }
        return Math.max(150, Math.min(1500, (long) (ping * 1.6) + 60));
    }

    private void click(ScreenHandler h, int slot, int button, SlotActionType action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (slot < 0 || slot >= h.slots.size() || action == SlotActionType.THROW) {
            throw new IllegalStateException("ungültiger Klick");
        }
        mc.interactionManager.clickSlot(h.syncId, slot, button, action, mc.player);
    }

    private void step(HandledScreen<?> hs, ScreenHandler h, long now) {
        Move m = current;
        try {
            switch (m.kind) {
                case MOVE -> {
                    switch (phase) {
                        case 0 -> {
                            totalsBefore = totals(h);
                            snapFrom = h.getSlot(m.from).getStack().copy();
                            snapTo = h.getSlot(m.to).getStack().copy();
                            if (snapFrom.isEmpty()) {
                                nextMove(now, false);
                                return;
                            }
                            visualize(hs, h, m.from, snapFrom, true);
                            click(h, m.from, 0, SlotActionType.PICKUP);
                            advance(now);
                        }
                        case 1 -> {
                            visualize(hs, h, m.to, ItemStack.EMPTY, false);
                            click(h, m.to, 0, SlotActionType.PICKUP);
                            advance(now);
                        }
                        case 2 -> {
                            if (!h.getCursorStack().isEmpty()) {
                                click(h, m.from, 0, SlotActionType.PICKUP);
                            }
                            phase = 3;
                            nextAt = now + settle();
                        }
                        default -> verifyMove(h, now);
                    }
                }
                case SWAP -> {
                    if (phase == 0) {
                        totalsBefore = totals(h);
                        snapFrom = h.getSlot(m.from).getStack().copy();
                        visualize(hs, h, m.from, snapFrom, true);
                        click(h, m.from, m.button, SlotActionType.SWAP);
                        phase = 3;
                        nextAt = now + settle();
                    } else {
                        verifyTotals(h, now);
                    }
                }
                case QUICK -> {
                    if (phase == 0) {
                        totalsBefore = totals(h);
                        visualize(hs, h, m.from, h.getSlot(m.from).getStack().copy(), true);
                        click(h, m.from, 0, SlotActionType.QUICK_MOVE);
                        phase = 3;
                        nextAt = now + settle();
                    } else {
                        verifyTotals(h, now);
                    }
                }
                case CRAFT -> craftStep(hs, h, m, now);
            }
        } catch (RuntimeException e) {
            recover(h);
            finish(false, Mini.t("task.error", "Aufgabe abgebrochen: %s", e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    private void craftStep(HandledScreen<?> hs, ScreenHandler h, Move m, long now) {
        switch (phase) {
            case 0 -> {
                if (!h.getSlot(m.from).hasStack() || h.getSlot(m.to).hasStack()) {
                    nextMove(now, false);
                    return;
                }
                totalsBefore = null;
                visualize(hs, h, m.from, h.getSlot(m.from).getStack().copyWithCount(1), true);
                click(h, m.from, 0, SlotActionType.PICKUP);
                advance(now);
            }
            case 1 -> {
                visualize(hs, h, m.to, ItemStack.EMPTY, false);
                click(h, m.to, 1, SlotActionType.PICKUP); // Rechtsklick: genau ein Item ins Raster
                advance(now);
            }
            case 2 -> {
                if (!h.getCursorStack().isEmpty()) {
                    click(h, m.from, 0, SlotActionType.PICKUP);
                }
                phase = 3;
                phaseStart = now;
                nextAt = now + settle();
            }
            case 3 -> {
                if (h.getSlot(0).hasStack()) {
                    visualize(hs, h, 0, h.getSlot(0).getStack().copy(), true);
                    click(h, 0, 0, SlotActionType.QUICK_MOVE);
                    phase = 4;
                    nextAt = now + settle();
                } else if (now - phaseStart > 1500) {
                    // Kein Rezept-Ergebnis: Item zurücklegen und abbrechen
                    recover(h);
                    finish(false, Mini.t("task.craft_failed", "Kein Ergebnis im Crafting-Feld - abgebrochen."));
                } else {
                    nextAt = now + 100;
                }
            }
            default -> {
                if (!h.getCursorStack().isEmpty()) {
                    recover(h);
                    finish(false, Mini.t("task.verify_failed", "Server hat das Ergebnis nicht bestätigt - abgebrochen."));
                    return;
                }
                nextMove(now, true);
            }
        }
    }

    private void advance(long now) {
        phase++;
        nextAt = now + delay();
    }

    private void verifyMove(ScreenHandler h, long now) {
        if (!h.getCursorStack().isEmpty() || !totals(h).equals(totalsBefore)) {
            recover(h);
            finish(false, Mini.t("task.verify_failed", "Server hat das Ergebnis nicht bestätigt - abgebrochen."));
            return;
        }
        ItemStack from = h.getSlot(current.from).getStack();
        ItemStack to = h.getSlot(current.to).getStack();
        boolean ok;
        if (snapTo.isEmpty()) {
            ok = from.isEmpty() && ItemStack.areEqual(to, snapFrom);
        } else if (ItemStack.areItemsAndComponentsEqual(snapFrom, snapTo)) {
            int sum = snapFrom.getCount() + snapTo.getCount();
            int expTo = Math.min(snapTo.getMaxCount(), sum);
            ok = to.getCount() == expTo && (sum - expTo == 0 ? from.isEmpty() : from.getCount() == sum - expTo);
        } else {
            ok = ItemStack.areEqual(from, snapTo) && ItemStack.areEqual(to, snapFrom);
        }
        if (!ok) {
            finish(false, Mini.t("task.verify_failed", "Server hat das Ergebnis nicht bestätigt - abgebrochen."));
            return;
        }
        nextMove(now, true);
    }

    private void verifyTotals(ScreenHandler h, long now) {
        if (!h.getCursorStack().isEmpty() || !totals(h).equals(totalsBefore)) {
            recover(h);
            finish(false, Mini.t("task.verify_failed", "Server hat das Ergebnis nicht bestätigt - abgebrochen."));
            return;
        }
        nextMove(now, true);
    }

    private void nextMove(long now, boolean counted) {
        if (counted) {
            done++;
        } else {
            total = Math.max(done, total - 1);
        }
        for (Figure f : team) {
            f.carriedItem = ItemStack.EMPTY;
        }
        current = null;
        phase = 0;
        nextAt = now + delay();
    }

    /** Zählt alle Items im Handler inkl. Cursor (Schutz: nichts darf verloren gehen). */
    private static Map<String, Integer> totals(ScreenHandler h) {
        Map<String, Integer> m = new HashMap<>();
        for (Slot s : h.slots) {
            ItemStack st = s.getStack();
            if (!st.isEmpty()) {
                m.merge(key(st), st.getCount(), Integer::sum);
            }
        }
        ItemStack c = h.getCursorStack();
        if (!c.isEmpty()) {
            m.merge(key(c), c.getCount(), Integer::sum);
        }
        return m;
    }

    /** Item am Cursor sicher zurücklegen (Ursprungs-Slot oder erster freier Spieler-Slot) - nie droppen. */
    private void recover(ScreenHandler h) {
        if (h.getCursorStack().isEmpty() || MinecraftClient.getInstance().player == null) {
            return;
        }
        try {
            if (current != null && current.from >= 0 && current.from < h.slots.size()) {
                Slot origin = h.getSlot(current.from);
                if (!origin.hasStack() || ItemStack.areItemsAndComponentsEqual(origin.getStack(), h.getCursorStack())) {
                    click(h, current.from, 0, SlotActionType.PICKUP);
                    return;
                }
            }
            for (Slot s : h.slots) {
                if ((isPlayerMain(s) || isHotbar(s)) && !s.hasStack()) {
                    click(h, s.id, 0, SlotActionType.PICKUP);
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // nichts weiter tun - Vanilla legt das Item beim Schließen zurück ins Inventar
        }
    }

    /** Figur sichtbar zum Slot hüpfen lassen und ggf. das Item tragen. */
    private void visualize(HandledScreen<?> hs, ScreenHandler h, int slotId, ItemStack carry, boolean pick) {
        if (team.isEmpty() || !Mini.cfg().inventoryVisualize) {
            return;
        }
        Figure f = team.get(teamIndex % team.size());
        if (!pick) {
            // derselbe Helfer bringt das Item ans Ziel
        } else {
            teamIndex++;
            f = team.get(teamIndex % team.size());
        }
        if (f.carried) {
            return;
        }
        HandledScreenAccessor acc = (HandledScreenAccessor) hs;
        Slot s = h.getSlot(slotId);
        float tx = acc.minicompanions$getX() + s.x + 8;
        float ty = acc.minicompanions$getY() + s.y - 1;
        final Figure fig = f;
        fig.carriedItem = pick ? carry : fig.carriedItem;
        fig.brain.hopTo(tx, ty, Math.max(0.12f, Mini.cfg().inventoryClickDelayMs / 1000f * 0.9f), () -> {
            if (!pick) {
                fig.carriedItem = ItemStack.EMPTY;
            }
            fig.setActivity(Activity.WORK, 999);
        });
    }

    private void finish(boolean success, Text msg) {
        TaskType t = type;
        type = null;
        moves.clear();
        current = null;
        for (Figure f : team) {
            f.carriedItem = ItemStack.EMPTY;
            f.brain.cancelHop();
            f.setActivity(success ? Activity.CHEER : Activity.SAD, 1.5f);
            if (success) {
                f.brain.say("workdone");
                f.data.tasksDone++;
            } else {
                f.showEmote(Emote.QUESTION, 1.5f);
            }
        }
        if (success && t != null) {
        }
        team.clear();
        if (!msg.getString().isEmpty()) {
            Mini.chat(msg);
        }
    }

    /** Not-Stopp: alle Aktionen sofort abbrechen. */
    public void emergencyStop(boolean message) {
        if (type == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof HandledScreen<?> hs) {
            recover(hs.getScreenHandler());
        }
        finish(false, message ? Mini.t("task.stopped", "Not-Stopp: alle Inventar-Aktionen abgebrochen.") : Text.empty());
    }
}

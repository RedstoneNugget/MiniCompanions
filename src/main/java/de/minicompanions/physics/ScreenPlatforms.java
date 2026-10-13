package de.minicompanions.physics;

import de.minicompanions.config.MiniConfig;
import de.minicompanions.figure.WorldView;
import de.minicompanions.mixin.BossBarHudAccessor;
import de.minicompanions.mixin.ChatHudAccessor;
import de.minicompanions.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Liefert pro Frame die begehbaren Flächen des Bildschirms: Boden, HUD-Elemente, Container-Fenster und
 * Slots, Chat-Zeilen und Eingabefeld sowie Widgets beliebiger Screens.
 * Positionen werden live aus den echten Koordinaten gelesen und passen sich jeder GUI-Skalierung an.
 * Unbekannte Screens oder Fehler führen nie zum Absturz - im Zweifel gilt nur der Boden.
 */
public final class ScreenPlatforms {
    private ScreenPlatforms() {
    }

    public static WorldView.Kind build(MinecraftClient mc, MiniConfig cfg, PlatformSet ps) {
        Screen screen = mc.currentScreen;
        WorldView.Kind kind = WorldView.Kind.GAME;
        try {
            if (screen == null) {
                if (cfg.walkHud && !mc.options.hudHidden) {
                    hud(mc, ps);
                }
            } else if (screen instanceof HandledScreen<?> hs) {
                kind = WorldView.Kind.INVENTORY;
                if (cfg.walkInventory) {
                    container(hs, ps);
                    widgets(screen, ps);
                }
            } else if (screen instanceof ChatScreen) {
                kind = WorldView.Kind.CHAT;
                if (cfg.walkChat) {
                    chat(mc, screen, ps);
                }
            } else {
                kind = WorldView.Kind.OTHER_SCREEN;
                if (cfg.walkOtherScreens) {
                    widgets(screen, ps);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // Fremde/unbekannte Screens: nur Boden
        }
        return kind;
    }

    // ------------------------------------------------------------------ HUD

    private static void hud(MinecraftClient mc, PlatformSet ps) {
        int w = (int) ps.width;
        int h = (int) ps.height;
        int cx = w / 2;
        ps.add(new Ledge("hud:hotbar", cx - 91, cx + 91, h - 22, h, Ledge.Kind.HUD, true));
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) {
            return;
        }
        if (!p.getOffHandStack().isEmpty()) {
            boolean left = p.getMainArm() == net.minecraft.util.Arm.RIGHT;
            int ox = left ? cx - 91 - 29 : cx + 91 + 6;
            ps.add(new Ledge("hud:offhand", ox, ox + 23, h - 23, h, Ledge.Kind.HUD, false));
        }
        boolean survival = mc.interactionManager.hasStatusBars();
        if (survival) {
            ps.add(new Ledge("hud:xp", cx - 91, cx + 91, h - 29, h - 24, Ledge.Kind.HUD, false));
            int n = h - 39;
            float maxHealth = Math.max(p.getMaxHealth(), p.getHealth());
            int absorb = MathHelper.ceil(p.getAbsorptionAmount());
            int rows = MathHelper.ceil((maxHealth + absorb) / 2.0F / 10.0F);
            int spacing = Math.max(10 - (rows - 2), 3);
            int heartsTop = n - (rows - 1) * spacing;
            ps.add(new Ledge("hud:hearts", cx - 91, cx - 91 + 81, heartsTop, n + 9, Ledge.Kind.HUD, false));
            if (p.getArmor() > 0) {
                ps.add(new Ledge("hud:armor", cx - 91, cx - 91 + 81, heartsTop - 10, heartsTop - 1, Ledge.Kind.HUD, false));
            }
            if (!p.hasVehicle()) {
                ps.add(new Ledge("hud:food", cx + 91 - 81, cx + 91, n, n + 9, Ledge.Kind.HUD, false));
                if (p.getAir() < p.getMaxAir() || p.isSubmergedInWater()) {
                    ps.add(new Ledge("hud:air", cx + 91 - 81, cx + 91, n - 10, n - 1, Ledge.Kind.HUD, false));
                }
            }
        }
        // Bossbars oben
        try {
            int count = ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).minicompanions$getBossBars().size();
            for (int i = 0; i < count && 12 + i * 19 < h / 3; i++) {
                int by = 12 + i * 19;
                ps.add(new Ledge("hud:boss" + i, cx - 91, cx + 91, by, by + 5, Ledge.Kind.HUD, false));
            }
        } catch (RuntimeException ignored) {
            // keine Bossbars
        }
        // Scoreboard rechts
        try {
            if (mc.world != null) {
                ScoreboardObjective obj = mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                if (obj != null) {
                    int entries = Math.min(15, mc.world.getScoreboard().getScoreboardEntries(obj).size());
                    int boxH = (entries + 1) * 9;
                    int top = h / 2 - boxH / 3 - 9;
                    int width = Math.max(60, mc.textRenderer.getWidth(obj.getDisplayName()) + 30);
                    ps.add(new Ledge("hud:scoreboard", w - width - 3, w - 1, top, top + boxH + 9, Ledge.Kind.HUD, true));
                }
            }
        } catch (RuntimeException ignored) {
            // kein Scoreboard
        }
    }

    // ------------------------------------------------------------------ Container

    private static void container(HandledScreen<?> hs, PlatformSet ps) {
        HandledScreenAccessor acc = (HandledScreenAccessor) hs;
        int x = acc.minicompanions$getX();
        int y = acc.minicompanions$getY();
        int bw = acc.minicompanions$getBackgroundWidth();
        int bh = acc.minicompanions$getBackgroundHeight();
        if (bw > 0 && bh > 0) {
            ps.add(new Ledge("inv:window", x, x + bw, y, y + bh, Ledge.Kind.WINDOW, true));
        }
        // Slots zeilenweise zu durchgehenden Kanten zusammenfassen
        List<int[]> boxes = new ArrayList<>();
        for (Slot s : hs.getScreenHandler().slots) {
            if (s.isEnabled()) {
                boxes.add(new int[]{x + s.x - 1, y + s.y - 1});
            }
        }
        boxes.sort(Comparator.<int[]>comparingInt(a -> a[1]).thenComparingInt(a -> a[0]));
        int i = 0;
        while (i < boxes.size()) {
            int rowY = boxes.get(i)[1];
            int start = boxes.get(i)[0];
            int end = start + 18;
            int j = i + 1;
            while (j < boxes.size() && boxes.get(j)[1] == rowY && boxes.get(j)[0] <= end + 3) {
                end = Math.max(end, boxes.get(j)[0] + 18);
                j++;
            }
            ps.add(new Ledge("slots:" + rowY + ":" + start, start, end, rowY, rowY + 18, Ledge.Kind.SLOT, false));
            i = j;
        }
    }

    // ------------------------------------------------------------------ Chat

    private static void chat(MinecraftClient mc, Screen screen, PlatformSet ps) {
        ChatHud chat = mc.inGameHud.getChatHud();
        ChatHudAccessor acc = (ChatHudAccessor) chat;
        List<ChatHudLine.Visible> lines = acc.minicompanions$getVisibleMessages();
        int scrolled = acc.minicompanions$getScrolledLines();
        double scale = mc.options.getChatScale().getValue();
        double spacing = mc.options.getChatLineSpacing().getValue();
        int lineH = (int) (9.0 * (spacing + 1.0));
        int chatW = ChatHud.getWidth(mc.options.getChatWidth().getValue());
        int visibleCount = Math.min(lines.size() - scrolled, chat.getVisibleLineCount());
        int k = MathHelper.floor((ps.height - 40) / scale);
        int right = (int) Math.ceil((MathHelper.ceil(chatW / scale) + 12) * scale);
        int topMost = (int) ps.height - 40;
        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrolled;
            if (idx < 0 || idx >= lines.size()) {
                break;
            }
            ChatHudLine.Visible line = lines.get(idx);
            float top = (float) ((k - (i + 1) * lineH) * scale);
            float bottom = (float) ((k - i * lineH) * scale);
            int textW = (int) ((mc.textRenderer.getWidth(line.content()) + 8) * scale);
            ps.add(new Ledge("chat:" + System.identityHashCode(line), 0, Math.max(30, Math.min(right, textW + 4)), top, bottom,
                    Ledge.Kind.CHAT, false));
            topMost = (int) top;
        }
        if (visibleCount > 0) {
            ps.add(new Ledge("chat:box", 0, right, topMost - 1, (float) (k * scale), Ledge.Kind.CHAT, true));
        }
        for (Element e : screen.children()) {
            if (e instanceof TextFieldWidget tf && tf.visible) {
                ps.add(new Ledge("chat:input", tf.getX() - 2, tf.getX() + tf.getWidth() + 2, tf.getY() - 2, tf.getY() + tf.getHeight(),
                        Ledge.Kind.CHAT_INPUT, true));
            }
        }
    }

    // ------------------------------------------------------------------ Widgets

    private static void widgets(Screen screen, PlatformSet ps) {
        int n = 0;
        for (Element e : screen.children()) {
            if (n > 120) {
                break;
            }
            if (e instanceof EntryListWidget<?> list && list.visible) {
                n += listRows(list, ps);
                continue;
            }
            if (e instanceof ClickableWidget cw && cw.visible && cw.getWidth() >= 8 && cw.getHeight() >= 6) {
                ps.add(new Ledge("w:" + System.identityHashCode(cw), cw.getX(), cw.getX() + cw.getWidth(), cw.getY(),
                        cw.getY() + cw.getHeight(), Ledge.Kind.WIDGET, cw.getHeight() >= 16));
                n++;
            }
        }
    }

    private static int listRows(EntryListWidget<?> list, PlatformSet ps) {
        int added = 0;
        int top = list.getY();
        int bottom = list.getBottom();
        int left = list.getRowLeft();
        int right = list.getRowRight();
        for (Object o : list.children()) {
            if (!(o instanceof net.minecraft.client.gui.widget.Widget entry)) {
                continue;
            }
            int ey = entry.getY();
            if (ey < top || ey > bottom - 4) {
                continue;
            }
            ps.add(new Ledge("row:" + System.identityHashCode(entry), left, right, ey, ey + entry.getHeight(), Ledge.Kind.WIDGET, false));
            if (++added > 40) {
                break;
            }
        }
        return added;
    }
}

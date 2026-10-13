package de.minicompanions.render;

import de.minicompanions.Mini;
import de.minicompanions.input.OverlayInteractScreen;
import de.minicompanions.streamer.ChatSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Kleine Statusanzeigen: Streamer-Modus-Symbol, Zähler "Zuschauer-Figuren: 3/10", Pause/Nachrichten aus,
 * laufende Inventar-Aufgabe und Hinweis zum Maus-Modus.
 */
public final class HudStatus {
    private HudStatus() {
    }

    public static void render(DrawContext ctx, TextRenderer tr, float sw, float sh) {
        int x = (int) sw - 4;
        int y = 4;
        var st = Mini.streamer;
        if (st.isEnabled() && st.cfg().showHudIcon && Mini.cfg().hudStatusIcon) {
            ChatSource.Status status = st.status();
            int dot = switch (status) {
                case CONNECTED -> 0xFF40E040;
                case CONNECTING, RECONNECTING -> 0xFFE0C040;
                case ERROR -> 0xFFE04040;
                default -> 0xFF909090;
            };
            String label = "Stream";
            int w = tr.getWidth(label) + 12;
            ctx.fill(x - w, y, x, y + 11, 0x90000000);
            ctx.fill(x - w + 3, y + 3, x - w + 8, y + 8, dot);
            ctx.drawText(tr, label, x - w + 10, y + 2, 0xFFFFFFFF, false);
            y += 12;
            if (st.cfg().showCounter) {
                Text t = Mini.t("hud.viewer_count", "Zuschauer-Figuren: %s/%s", st.viewers().count(), st.cfg().viewerLimit);
                int tw = tr.getWidth(t) + 6;
                ctx.fill(x - tw, y, x, y + 11, 0x90000000);
                ctx.drawText(tr, t, x - tw + 3, y + 2, 0xFFE0E0E0, false);
                y += 12;
            }
            if (st.moderation().spawnsPaused()) {
                y = badge(ctx, tr, x, y, Mini.t("hud.paused", "Chat-Spawns pausiert"), 0xFFFFB040);
            }
            if (st.moderation().messagesHidden()) {
                y = badge(ctx, tr, x, y, Mini.t("hud.messages_off", "Nachrichten aus"), 0xFFFFB040);
            }
        }
        if (Mini.inventory.isRunning()) {
            y = badge(ctx, tr, x, y, Mini.inventory.statusText(), 0xFF80FF80);
        }
        if (!"NORMAL".equals(Mini.cfg().sizeMode)) {
            y = badge(ctx, tr, x, y, "GIANT".equals(Mini.cfg().sizeMode) ? Mini.t("hud.giant", "Riesen-Modus") : Mini.t("hud.mini", "Mini-Modus"), 0xFFB0D0FF);
        }
        if (Mini.cfg().restMode) {
            badge(ctx, tr, x, y, Mini.t("hud.rest", "Ruhemodus"), 0xFFB0B0FF);
        }
        if (MinecraftClient.getInstance().currentScreen instanceof OverlayInteractScreen) {
            Text t = Mini.t("hud.mouse_mode", "Maus-Modus aktiv - ESC zum Beenden");
            int tw = tr.getWidth(t);
            ctx.fill((int) sw / 2 - tw / 2 - 3, 2, (int) sw / 2 + tw / 2 + 3, 13, 0xA0000000);
            ctx.drawText(tr, t, (int) sw / 2 - tw / 2, 4, 0xFFFFE080, false);
        }
    }

    private static int badge(DrawContext ctx, TextRenderer tr, int x, int y, Text t, int color) {
        int tw = tr.getWidth(t) + 6;
        ctx.fill(x - tw, y, x, y + 11, 0x90000000);
        ctx.drawText(tr, t, x - tw + 3, y + 2, color, false);
        return y + 12;
    }
}

package de.minicompanions.render;

import de.minicompanions.Mini;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.WorldView;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Debug-Overlay: erkannte Plattformen, Kollisionsboxen und pro Figur Zustand, Bedürfnisse und Größe.
 */
public final class DebugOverlay {
    private DebugOverlay() {
    }

    public static void render(DrawContext ctx, TextRenderer tr, WorldView w, float ox, float oy) {
        OverlayRenderer.drawLedges(ctx, w, ox, oy);
        for (float[] z : w.platforms.blockedZones()) {
            ctx.fill(Math.round(ox + z[0]), Math.round(oy + z[1]), Math.round(ox + z[2]), Math.round(oy + z[3]), 0x30FF0000);
        }
        var m = ctx.getMatrices();
        for (Figure f : w.figures) {
            var b = f.body;
            int x1 = Math.round(ox + b.x - b.w / 2);
            int x2 = Math.round(ox + b.x + b.w / 2);
            int y1 = Math.round(oy + b.y - b.h);
            int y2 = Math.round(oy + b.y);
            ctx.drawStrokedRectangle(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1), 0xA0FFFF00);
            String s = f.activity.name() + (f.brain.isNavigating() ? "*" : "") + " " + Math.round(f.sizePercent(Mini.cfg())) + "%";
            String n = "E" + Math.round(f.data.energy) + " H" + Math.round(f.data.hunger) + " L" + Math.round(f.data.mood)
 + (b.ledgeId != null ? " " + b.ledgeId : "");
            m.pushMatrix();
            m.translate(x2 + 2, y1);
            m.scale(0.5f, 0.5f);
            ctx.drawText(tr, s, 0, 0, 0xFFFFFF60, true);
            ctx.drawText(tr, n, 0, 9, 0xFFB0FFB0, true);
            m.popMatrix();
        }
        ctx.drawText(tr, "Mini Debug: " + w.kind + " ledges=" + w.platforms.ledges().size() + " figs=" + w.figures.size(),
                Math.round(ox + 2), Math.round(oy + 2), 0xFFFFFF80, true);
    }
}

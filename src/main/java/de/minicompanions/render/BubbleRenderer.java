package de.minicompanions.render;

import de.minicompanions.figure.BubbleQueue;
import de.minicompanions.util.Colors;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Sprechblasen mit Pfeil zur Figur, automatischem Zeilenumbruch, Ein-/Ausblenden und Skalierung
 * mit der Figurengröße. Wird für eigene Sprüche, Minecraft-Chat und Zuschauer-Nachrichten genutzt.
 */
public final class BubbleRenderer {
    private BubbleRenderer() {
    }

    /**
     * @param anchorX Mitte über dem Kopf (GUI-Koordinaten)
     * @param anchorY Unterkante der Blase (Pfeilspitze)
     * @param scale   Gesamtskalierung (Figurengröße x Einstellung)
     * @param opacity Deckkraft 0..1 der Blase
     * @return Höhe der Blase in GUI-Pixeln (für weitere Elemente darüber)
     */
    public static float render(DrawContext ctx, TextRenderer tr, BubbleQueue.Bubble b, float alpha, float anchorX, float anchorY,
                               float scale, float opacity, float screenW) {
        if (b == null || alpha <= 0.02f) {
            return 0;
        }
        List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(b.text()), 118);
        if (lines.size() > 5) {
            lines = lines.subList(0, 5);
        }
        int textW = 0;
        for (OrderedText l : lines) {
            textW = Math.max(textW, tr.getWidth(l));
        }
        int w = textW + 8;
        int h = lines.size() * 9 + 5;
        float s = Math.max(0.4f, scale);
        float boxW = w * s;
        float left = anchorX - boxW / 2f;
        left = Math.max(2, Math.min(screenW - boxW - 2, left));
        float top = anchorY - (h + 4) * s;
        float pointerX = (anchorX - left) / s;

        int a = Math.round(alpha * opacity * 255);
        int bg = Colors.withAlpha(b.bgColor(), Math.min(Colors.alpha(b.bgColor()), a));
        int border = Colors.withAlpha(Colors.darken(b.bgColor(), 0.55f), a);
        int ta = Math.max(16, Math.round(alpha * 255));
        int text = Colors.withAlpha(b.textColor(), ta);

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(left, top);
        m.scale(s, s);
        // abgerundeter Kasten
        ctx.fill(1, 0, w - 1, h, bg);
        ctx.fill(0, 1, w, h - 1, bg);
        ctx.fill(1, -1, w - 1, 0, border);
        ctx.fill(1, h, w - 1, h + 1, border);
        ctx.fill(-1, 1, 0, h - 1, border);
        ctx.fill(w, 1, w + 1, h - 1, border);
        if (b.accentColor() != 0) {
            ctx.fill(1, 1, 3, h - 1, Colors.withAlpha(b.accentColor(), a));
        }
        // Pfeil
        int px = Math.max(3, Math.min(w - 4, Math.round(pointerX)));
        ctx.fill(px - 2, h, px + 2, h + 1, bg);
        ctx.fill(px - 1, h + 1, px + 1, h + 2, bg);
        ctx.fill(px, h + 2, px + 1, h + 3, border);
        ctx.fill(px - 3, h, px - 2, h + 1, border);
        ctx.fill(px + 2, h, px + 3, h + 1, border);
        int y = 3;
        for (OrderedText l : lines) {
            ctx.drawText(tr, l, b.accentColor() != 0 ? 5 : 4, y, text, false);
            y += 9;
        }
        m.popMatrix();
        return (h + 5) * s;
    }

    /** Namensschild über der Figur. @return Höhe */
    public static float nameTag(DrawContext ctx, TextRenderer tr, String name, int color, float cx, float bottom, float scale, float alpha) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        float s = Math.max(0.4f, scale);
        int w = tr.getWidth(name);
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, bottom - 10 * s);
        m.scale(s, s);
        ctx.fill(-w / 2 - 2, -1, w / 2 + 2, 9, Colors.withAlpha(0x000000, Math.round(0x70 * alpha)));
        ctx.drawText(tr, name, -w / 2, 0, Colors.withAlpha(color, Math.max(16, Math.round(255 * alpha))), false);
        m.popMatrix();
        return 11 * s;
    }
}

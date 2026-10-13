package de.minicompanions.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Zeichen-Helfer für einheitliche Fenster, Knöpfe und Rahmen. */
public final class Ui {
    public static final int PANEL = 0xF0202430;
    public static final int PANEL_LIGHT = 0xF02C3242;
    public static final int BORDER = 0xFF5A6A8A;
    public static final int TITLE = 0xFF34405A;
    public static final int TITLE_FOCUS = 0xFF3E5A8A;
    public static final int TEXT = 0xFFEFEFEF;
    public static final int TEXT_DIM = 0xFFA8B0C0;
    public static final int ACCENT = 0xFFFFC94A;
    public static final int RED = 0xFFE04848;
    public static final int GREEN = 0xFF5CCB5C;
    public static final int HOVER = 0x40FFFFFF;

    private Ui() {
    }

    public static void panel(DrawContext ctx, int x, int y, int w, int h, int bg) {
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x50000000);
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawStrokedRectangle(x, y, w, h, BORDER);
    }

    public static boolean button(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, Text label, int mx, int my, boolean active) {
        boolean hover = active && mx >= x && mx < x + w && my >= y && my < y + h;
        int bg = !active ? 0xC0303030 : (hover ? 0xE0506080 : 0xE03A4560);
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawStrokedRectangle(x, y, w, h, hover ? ACCENT : 0xFF6A7898);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, label, x + (w - tw) / 2, y + (h - 8) / 2, active ? TEXT : 0xFF808080, false);
        return hover;
    }

    public static boolean colorButton(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, Text label, int mx, int my, int color) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        ctx.fill(x, y, x + w, y + h, hover ? brighten(color) : color);
        ctx.drawStrokedRectangle(x, y, w, h, 0xFF000000);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFFFF, true);
        return hover;
    }

    public static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 40);
        int b = Math.min(255, (c & 0xFF) + 40);
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Text gekürzt auf maximale Breite. */
    public static String fit(TextRenderer tr, String s, int maxW) {
        if (tr.getWidth(s) <= maxW) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && tr.getWidth(t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }
}

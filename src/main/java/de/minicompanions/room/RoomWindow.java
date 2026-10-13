package de.minicompanions.room;

import de.minicompanions.Mini;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.config.MiniConfig;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.WorldView;
import de.minicompanions.gui.Ui;
import de.minicompanions.render.OverlayRenderer;
import de.minicompanions.util.Rect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Das Zimmer: ein frei verschiebbares und skalierbares Overlay-Fenster, in das man Figuren hineinziehen
 * kann. Darin laufen sie auf dem Boden herum. Position, Größe und Zustand werden in der Config
 * gespeichert; das Fenster bleibt immer vollständig im sichtbaren Bereich.
 * Rein lokal und optisch - keine Auswirkung auf Spielwelt oder Server.
 */
public final class RoomWindow {
    public static final int TITLE_H = 12;
    public static final int FLOOR_H = 10;
    public static final int MIN_W = 120;
    public static final int MIN_H = 80;
    private static final int GRIP = 6;

    public final WorldView world = new WorldView();
    private boolean dragging;
    private int resizeCorner;
    private float offX;
    private float offY;
    private float startX;
    private float startY;
    private float startW;
    private float startH;
    private double startMx;
    private double startMy;

    public RoomWindow() {
        world.kind = WorldView.Kind.ROOM;
    }

    private static MiniConfig cfg() {
        return Mini.cfg();
    }

    // ------------------------------------------------------------------ Zustand

    public boolean isOpen() {
        return cfg().roomOpen;
    }

    /** In den eigenen Einstellungs-Fenstern wird das Zimmer ausgeblendet, damit es nichts verdeckt. */
    public static boolean suppressed() {
        return net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof de.minicompanions.gui.BaseScreen;
    }

    /** Inhalt sichtbar (offen und nicht minimiert)? Nur dann leben die Zimmer-Figuren. */
    public boolean contentVisible() {
        return cfg().roomOpen && !cfg().roomMinimized;
    }

    public void setOpen(boolean open) {
        cfg().roomOpen = open;
        if (open) {
            cfg().roomMinimized = false;
        }
        save();
    }

    public void toggle() {
        setOpen(!isOpen());
    }

    public void toggleMinimized() {
        if (!isOpen()) {
            setOpen(true);
            return;
        }
        cfg().roomMinimized = !cfg().roomMinimized;
        save();
    }

    public void resetPosition() {
        MiniConfig c = cfg();
        c.roomX = 20;
        c.roomY = 20;
        c.roomW = 240;
        c.roomH = 150;
        c.roomMinimized = false;
        save();
    }

    private static void save() {
        Mini.configs.markDirty(ConfigManager.Part.CONFIG);
    }

    public float x() {
        return cfg().roomX;
    }

    public float y() {
        return cfg().roomY;
    }

    public float w() {
        return cfg().roomW;
    }

    public float h() {
        return cfg().roomMinimized ? TITLE_H : cfg().roomH;
    }

    /** Inhaltsbereich (unter der Titelleiste). */
    public Rect content() {
        return new Rect(x(), y() + TITLE_H, w(), cfg().roomH - TITLE_H);
    }

    /** Begehbarer Bereich für Figuren (über dem Boden). */
    public Rect walkArea() {
        Rect c = content();
        return new Rect(c.x(), c.y(), c.w(), c.h() - FLOOR_H);
    }

    public int figureCount() {
        int n = 0;
        for (Figure f : Mini.figures.all()) {
            if (f.inRoom) {
                n++;
            }
        }
        return n;
    }

    /** Fenster vollständig sichtbar halten (auch nach Änderung der Fenster- oder GUI-Größe). */
    public void clamp(float sw, float sh) {
        MiniConfig c = cfg();
        int ow = c.roomW;
        int oh = c.roomH;
        int ox = c.roomX;
        int oy = c.roomY;
        c.roomW = (int) Math.max(MIN_W, Math.min(c.roomW, sw));
        c.roomH = (int) Math.max(MIN_H, Math.min(c.roomH, sh));
        c.roomX = (int) Math.max(0, Math.min(c.roomX, sw - c.roomW));
        c.roomY = (int) Math.max(0, Math.min(c.roomY, sh - h()));
        if ((ow != c.roomW || oh != c.roomH || ox != c.roomX || oy != c.roomY) && !dragging && resizeCorner == 0) {
            save();
        }
    }

    // ------------------------------------------------------------------ Treffer

    public boolean hit(double mx, double my) {
        return isOpen() && !suppressed() && mx >= x() && mx < x() + w() && my >= y() && my < y() + h();
    }

    public boolean contentHit(double mx, double my) {
        return contentVisible() && !suppressed() && content().contains(mx, my);
    }

    private boolean titleHit(double mx, double my) {
        return isOpen() && mx >= x() && mx < x() + w() && my >= y() && my < y() + TITLE_H;
    }

    private boolean closeHit(double mx, double my) {
        return Ui.inside(mx, my, (int) (x() + w() - 11), (int) y() + 1, 10, 10);
    }

    private boolean minimizeHit(double mx, double my) {
        return Ui.inside(mx, my, (int) (x() + w() - 22), (int) y() + 1, 10, 10);
    }

    /** 0 = keine Ecke, 1 = oben links, 2 = oben rechts, 3 = unten links, 4 = unten rechts. */
    private int cornerHit(double mx, double my) {
        if (!contentVisible()) {
            return 0;
        }
        float x = x();
        float y = y();
        float w = w();
        float h = h();
        boolean left = mx >= x - 2 && mx < x + GRIP;
        boolean right = mx > x + w - GRIP && mx <= x + w + 2;
        boolean top = my >= y - 2 && my < y + 3;
        boolean bottom = my > y + h - GRIP && my <= y + h + 2;
        if (bottom && right) {
            return 4;
        }
        if (bottom && left) {
            return 3;
        }
        if (top && right) {
            return 2;
        }
        if (top && left) {
            return 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ Eingabe

    /** Klick auf Titelleiste/Ecken (Figuren im Inhalt behandelt der InputRouter). @return verbraucht */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isOpen() || suppressed()) {
            return false;
        }
        int corner = button == 0 ? cornerHit(mx, my) : 0;
        if (corner != 0) {
            resizeCorner = corner;
            startX = x();
            startY = y();
            startW = w();
            startH = cfg().roomH;
            startMx = mx;
            startMy = my;
            return true;
        }
        if (!hit(mx, my)) {
            return false;
        }
        if (titleHit(mx, my) && button == 0) {
            if (closeHit(mx, my)) {
                setOpen(false);
                return true;
            }
            if (minimizeHit(mx, my)) {
                toggleMinimized();
                return true;
            }
            dragging = true;
            offX = (float) (mx - x());
            offY = (float) (my - y());
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my, float sw, float sh) {
        MiniConfig c = cfg();
        if (dragging) {
            c.roomX = Math.round((float) (mx - offX));
            c.roomY = Math.round((float) (my - offY));
            clamp(sw, sh);
            return true;
        }
        if (resizeCorner != 0) {
            float dx = (float) (mx - startMx);
            float dy = (float) (my - startMy);
            float nx = startX;
            float ny = startY;
            float nw;
            float nh;
            if (resizeCorner == 2 || resizeCorner == 4) {
                nw = startW + dx;
            } else {
                nw = startW - dx;
                nx = startX + dx;
            }
            if (resizeCorner == 3 || resizeCorner == 4) {
                nh = startH + dy;
            } else {
                nh = startH - dy;
                ny = startY + dy;
            }
            if (nw < MIN_W) {
                if (resizeCorner == 1 || resizeCorner == 3) {
                    nx -= MIN_W - nw;
                }
                nw = MIN_W;
            }
            if (nh < MIN_H) {
                if (resizeCorner == 1 || resizeCorner == 2) {
                    ny -= MIN_H - nh;
                }
                nh = MIN_H;
            }
            c.roomX = Math.round(nx);
            c.roomY = Math.round(ny);
            c.roomW = Math.round(nw);
            c.roomH = Math.round(nh);
            clamp(sw, sh);
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (dragging || resizeCorner != 0) {
            dragging = false;
            resizeCorner = 0;
            save();
            return true;
        }
        return false;
    }

    public boolean isInteracting() {
        return dragging || resizeCorner != 0;
    }

    // ------------------------------------------------------------------ Darstellung

    public void render(DrawContext ctx, TextRenderer tr, int mx, int my, double gs) {
        if (!isOpen() || suppressed()) {
            return;
        }
        int ix = Math.round(x());
        int iy = Math.round(y());
        int iw = Math.round(w());
        int ih = Math.round(h());
        boolean active = dragging || resizeCorner != 0;
        ctx.fill(ix + 2, iy + 2, ix + iw + 2, iy + ih + 2, 0x50000000);
        ctx.fill(ix, iy, ix + iw, iy + TITLE_H, active ? Ui.TITLE_FOCUS : Ui.TITLE);
        Text title = Mini.t("room.title", "Mini-Zimmer (%s)", figureCount());
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen == null) {
            // Maus ist im Spiel gefangen: zeigen, wie man Figuren hineinziehen kann
            title = Mini.t("room.title_hint", "Mini-Zimmer (%s) - %s: Figuren ziehen", figureCount(),
                    de.minicompanions.input.Keys.room.getBoundKeyLocalizedText());
        }
        ctx.drawText(tr, Text.literal(Ui.fit(tr, title.getString(), iw - 30)), ix + 3, iy + 2, Ui.TEXT, false);
        titleButton(ctx, tr, ix + iw - 22, iy + 1, cfg().roomMinimized ? "□" : "_", mx, my, Ui.TEXT_DIM);
        titleButton(ctx, tr, ix + iw - 11, iy + 1, "×", mx, my, 0xFFFF8080);
        if (contentVisible()) {
            Rect c = content();
            int cx = Math.round(c.x());
            int cy = Math.round(c.y());
            int cw = Math.round(c.w());
            int ch = Math.round(c.h());
            ctx.enableScissor(cx, cy, cx + cw, cy + ch);
            drawBackground(ctx, cx, cy, cw, ch);
            Rect wa = walkArea();
            OverlayRenderer.drawWorld(ctx, tr, world, wa.x(), wa.y(), gs, cfg(), wa.w());
            // Hinweis bei leerem Zimmer
            if (world.figures.isEmpty()) {
                Text hint = Mini.t("room.empty", "Figuren hier hineinziehen");
                ctx.drawText(tr, hint, cx + (cw - tr.getWidth(hint)) / 2, cy + ch / 2 - 8, 0xA0503A20, false);
            }
            ctx.disableScissor();
            // Griff unten rechts
            ctx.fill(ix + iw - 5, iy + ih - 1, ix + iw, iy + ih, 0xFFB0B0B0);
            ctx.fill(ix + iw - 1, iy + ih - 5, ix + iw, iy + ih, 0xFFB0B0B0);
        }
        ctx.drawStrokedRectangle(ix, iy, iw, ih, active ? Ui.ACCENT : Ui.BORDER);
    }

    /** Hervorhebung, wenn eine getragene Figur über dem Zimmer schwebt. */
    public void renderDropHint(DrawContext ctx) {
        if (!contentVisible()) {
            return;
        }
        Rect c = content();
        ctx.drawStrokedRectangle(Math.round(c.x()) + 1, Math.round(c.y()) + 1, Math.round(c.w()) - 2, Math.round(c.h()) - 2, Ui.ACCENT);
    }

    private static void drawBackground(DrawContext ctx, int x, int y, int w, int h) {
        // Tapete mit Streifen
        ctx.fill(x, y, x + w, y + h, 0xFFE8D8B8);
        for (int i = 0; i < w; i += 16) {
            ctx.fill(x + i, y, x + i + 8, y + h, 0x14A07850);
        }
        // Fenster mit Himmel
        int wx = x + 14;
        int wy = y + 10;
        ctx.fill(wx - 2, wy - 2, wx + 34, wy + 26, 0xFF7A5228);
        ctx.fill(wx, wy, wx + 32, wy + 24, 0xFFA0D0F0);
        ctx.fill(wx + 15, wy, wx + 17, wy + 24, 0xFF7A5228);
        ctx.fill(wx, wy + 11, wx + 32, wy + 13, 0xFF7A5228);
        // Holzboden
        int fy = y + h - FLOOR_H;
        ctx.fill(x, fy, x + w, y + h, 0xFF9A6A3A);
        ctx.fill(x, fy, x + w, fy + 1, 0xFF7A5228);
        for (int i = 0; i < w; i += 24) {
            ctx.fill(x + i, fy, x + i + 1, y + h, 0xFF7A5228);
        }
        // Teppich
        int rw = Math.min(60, w / 3);
        ctx.fill(x + w - rw - 16, fy - 1, x + w - 16, fy + 1, 0xFFB03030);
    }

    private static void titleButton(DrawContext ctx, TextRenderer tr, int bx, int by, String s, int mx, int my, int color) {
        boolean hover = Ui.inside(mx, my, bx, by, 10, 10);
        if (hover) {
            ctx.fill(bx, by, bx + 10, by + 10, Ui.HOVER);
        }
        ctx.drawText(tr, s, bx + (10 - tr.getWidth(s)) / 2 + 1, by + 1, color, false);
    }
}

package de.minicompanions.render;

import de.minicompanions.Mini;
import de.minicompanions.util.Colors;
import net.minecraft.client.gui.DrawContext;


import java.util.Random;

/**
 * Einfaches, speicherschonendes Partikelsystem (feste Arrays, keine Allokation pro Partikel):
 * Funken, Herzchen, Noten, Konfetti, Staub, Luftblasen und Feuerwerk.
 */
public final class Particles {
    private static final int MAX = 1500;
    private static final Random RNG = new Random();

    private final float[] x = new float[MAX];
    private final float[] y = new float[MAX];
    private final float[] vx = new float[MAX];
    private final float[] vy = new float[MAX];
    private final float[] life = new float[MAX];
    private final float[] maxLife = new float[MAX];
    private final int[] color = new int[MAX];
    private final byte[] kind = new byte[MAX];
    private final float[] grav = new float[MAX];

    private int count;

    private static final byte DOT = 0;
    private static final byte HEART = 1;
    private static final byte NOTE = 2;

    private boolean enabled() {
        return Mini.cfg().particles;
    }

    private void spawn(float px, float py, float pvx, float pvy, float l, int col, byte k, float g) {
        if (count >= MAX) {
            return;
        }
        int i = count++;
        x[i] = px;
        y[i] = py;
        vx[i] = pvx;
        vy[i] = pvy;
        life[i] = l;
        maxLife[i] = l;
        color[i] = col;
        kind[i] = k;
        grav[i] = g;

    }

    public void sparkle(float px, float py, int n) {
        if (!enabled()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float a = RNG.nextFloat() * 6.283f;
            float s = 15 + RNG.nextFloat() * 30;
            spawn(px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0.5f + RNG.nextFloat() * 0.4f,
                    RNG.nextBoolean() ? 0xFFFFF4A0 : 0xFFFFFFFF, DOT, 20);
        }
    }

    public void hearts(float px, float py, int n) {
        if (!enabled()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            spawn(px + (RNG.nextFloat() - 0.5f) * 10, py, (RNG.nextFloat() - 0.5f) * 12, -18 - RNG.nextFloat() * 14,
                    1.2f + RNG.nextFloat() * 0.6f, 0xFFE8344C, HEART, -4);
        }
    }

    public void notes(float px, float py, int n) {
        if (!enabled()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            spawn(px, py, (RNG.nextFloat() - 0.5f) * 14, -14 - RNG.nextFloat() * 8, 1.4f, 0xFFFFFFFF, NOTE, -2);
        }
    }

    public void confetti(float px, float py, int n) {
        if (!enabled()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float a = (float) (-Math.PI / 2 + (RNG.nextFloat() - 0.5f) * 2.2f);
            float s = 50 + RNG.nextFloat() * 70;
            spawn(px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 1.5f + RNG.nextFloat(),
                    Colors.hsv(RNG.nextFloat(), 0.8f, 1f, 255), DOT, 120);
        }
    }

    public void fireworks(float px, float py) {
        if (!enabled()) {
            return;
        }
        int col = Colors.hsv(RNG.nextFloat(), 0.7f, 1f, 255);
        for (int i = 0; i < 28; i++) {
            float a = i / 28f * 6.283f;
            float s = 40 + RNG.nextFloat() * 15;
            spawn(px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0.9f + RNG.nextFloat() * 0.3f, col, DOT, 30);
        }
        Mini.sounds.ui("firework");
    }

    public void dust(float px, float py, int n) {
        if (!enabled()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            spawn(px + (RNG.nextFloat() - 0.5f) * 6, py - 1, (RNG.nextFloat() - 0.5f) * 30, -RNG.nextFloat() * 12,
                    0.35f, 0xA0D8D0C0, DOT, 40);
        }
    }

    public void bubbles(float px, float py, int n) {
        if (!enabled()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            spawn(px + (RNG.nextFloat() - 0.5f) * 4, py, (RNG.nextFloat() - 0.5f) * 4, -12 - RNG.nextFloat() * 8, 1.2f,
                    0xA0B0E8FF, DOT, -6);
        }
    }

    /** Schneeball von (x0,y0) nach (x1,y1) im Bogen. */
    public void snowball(float x0, float y0, float x1, float y1) {
        if (!enabled()) {
            return;
        }
        float t = 0.6f;
        float g = 160;
        float vx = (x1 - x0) / t;
        float vy = (y1 - y0 - 0.5f * g * t * t) / t;
        for (int i = 0; i < 3; i++) {
            spawn(x0 + i * 0.5f, y0 + (i % 2) * 0.5f, vx, vy, t + 0.1f, 0xFFF4F8FF, DOT, g);
        }
    }

    public void sweat(float px, float py) {
        if (!enabled()) {
            return;
        }
        spawn(px, py, (RNG.nextFloat() - 0.5f) * 20, -10, 0.6f, 0xFF6EC8FF, DOT, 60);
    }

    public void update(float dt) {
        int i = 0;
        while (i < count) {
            life[i] -= dt;
            if (life[i] <= 0) {
                int last = --count;
                x[i] = x[last];
                y[i] = y[last];
                vx[i] = vx[last];
                vy[i] = vy[last];
                life[i] = life[last];
                maxLife[i] = maxLife[last];
                color[i] = color[last];
                kind[i] = kind[last];
                grav[i] = grav[last];


                continue;
            }
            vy[i] += grav[i] * dt;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            vx[i] *= (float) Math.exp(-1.5 * dt);
            i++;
        }
    }

    /** Alle Partikel zeichnen. */
    public void render(DrawContext ctx, float ox, float oy) {
        for (int i = 0; i < count; i++) {
            float f = life[i] / maxLife[i];
            int c = Colors.mulAlpha(color[i], Math.min(1f, f * 2f));
            int px = Math.round(ox + x[i]);
            int py = Math.round(oy + y[i]);
            switch (kind[i]) {
                case HEART -> {
                    var m = ctx.getMatrices();
                    m.pushMatrix();
                    m.translate(px, py);
                    m.scale(0.5f, 0.5f);
                    PixelSprites.emote(ctx, de.minicompanions.figure.Emote.HEART, 0);
                    m.popMatrix();
                }
                case NOTE -> {
                    var m = ctx.getMatrices();
                    m.pushMatrix();
                    m.translate(px, py);
                    m.scale(0.5f, 0.5f);
                    PixelSprites.emote(ctx, de.minicompanions.figure.Emote.NOTE, 0);
                    m.popMatrix();
                }
                default -> ctx.fill(px, py, px + 1, py + 1, c);
            }
        }
    }

    public void clear() {
        count = 0;
    }
}

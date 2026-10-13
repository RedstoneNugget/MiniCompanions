package de.minicompanions.util;

/** Farb-Hilfen (ARGB). */
public final class Colors {
    private Colors() {
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int mulAlpha(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
    }

    public static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (alpha(a) + (alpha(b) - alpha(a)) * t);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    /** HSV (h 0..1) nach ARGB. */
    public static int hsv(float h, float s, float v, int alpha) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float hp = h * 6f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        float m = v - c;
        int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
        return (alpha << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /** "#RRGGBB" oder "RRGGBB" parsen, sonst Fallback. */
    public static int parseRgb(String s, int fallback) {
        if (s == null) {
            return fallback;
        }
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6) {
            return fallback;
        }
        try {
            return 0xFF000000 | Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String toHex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }
}

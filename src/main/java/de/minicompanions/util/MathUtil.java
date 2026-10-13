package de.minicompanions.util;

/** Kleine Mathe-Helfer. */
public final class MathUtil {
    private MathUtil() {
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Annäherung mit Rate pro Sekunde (bildratenunabhängig). */
    public static float approach(float cur, float target, float ratePerSec, float dt) {
        float k = 1f - (float) Math.exp(-ratePerSec * dt);
        return cur + (target - cur) * k;
    }

    public static float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}

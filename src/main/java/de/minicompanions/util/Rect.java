package de.minicompanions.util;

/** Einfaches Rechteck in GUI-Koordinaten (float). Reine Logik, ohne Minecraft-Abhängigkeit. */
public record Rect(float x, float y, float w, float h) {
    public float right() {
        return x + w;
    }

    public float bottom() {
        return y + h;
    }

    public float centerX() {
        return x + w / 2f;
    }

    public float centerY() {
        return y + h / 2f;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public boolean intersects(Rect o) {
        return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
    }

    public Rect offset(float dx, float dy) {
        return new Rect(x + dx, y + dy, w, h);
    }
}

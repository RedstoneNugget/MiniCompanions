package de.minicompanions.physics;

/**
 * Eine begehbare Oberkante (Plattform). Plattformen sind "Einweg"-Kanten: Figuren können von unten
 * hindurchspringen und von oben darauf landen. Dadurch kann eine Figur nie in einer Plattform
 * eingeklemmt werden. {@code bottom} beschreibt die Unterkante des zugehörigen Elements, an dessen
 * Seiten eine Figur hochklettern kann (falls {@link #climbable}).
 *
 * <p>Reine Logik - keine Minecraft-Klassen.</p>
 */
public final class Ledge {
    public enum Kind { GROUND, HUD, SLOT, WINDOW, CHAT, CHAT_INPUT, WIDGET, OVERLAY_WINDOW, FIGURE }

    public final String id;
    public final float x1;
    public final float x2;
    public final float y;
    public final float bottom;
    public final Kind kind;
    public final boolean climbable;

    public Ledge(String id, float x1, float x2, float y, float bottom, Kind kind, boolean climbable) {
        this.id = id;
        this.x1 = Math.min(x1, x2);
        this.x2 = Math.max(x1, x2);
        this.y = y;
        this.bottom = Math.max(y, bottom);
        this.kind = kind;
        this.climbable = climbable;
    }

    public float width() {
        return x2 - x1;
    }

    public float centerX() {
        return (x1 + x2) / 2f;
    }

    public boolean containsX(float x) {
        return x >= x1 && x <= x2;
    }

    /** Kann eine Figur hier schlafen oder sitzen? (nicht auf anderen Figuren) */
    public boolean restable() {
        return kind != Kind.FIGURE;
    }

    @Override
    public String toString() {
        return id + "[" + x1 + ".." + x2 + " @" + y + "]";
    }
}

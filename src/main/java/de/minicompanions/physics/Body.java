package de.minicompanions.physics;

/** Physikalischer Körper einer Figur: Position = Fußmitte, y wächst nach unten (GUI-Koordinaten). */
public final class Body {
    public float x;
    public float y;
    public float vx;
    public float vy;
    public float w = 4;
    public float h = 16;
    public boolean onGround;
    /** ID der Kante, auf der die Figur steht (oder null). */
    public String ledgeId;
    /** Zuletzt bekannte Lage der Kante, um bewegten Plattformen zu folgen. */
    public float ledgeX1;
    public float ledgeY;
    /** Diese Kante beim nächsten Landen ignorieren (Hinunterspringen durch die Plattform). */
    public String dropThrough;
    /** Wird gerade getragen (Maus) - keine Physik. */
    public boolean held;
    /** Klettert an einer senkrechten Kante - keine Schwerkraft. */
    public boolean climbing;
    /** Letzte Landegeschwindigkeit (für Effekte wie Abprallen). */
    public float lastImpact;

    public void setGround(Ledge l) {
        onGround = true;
        climbing = false;
        ledgeId = l.id;
        ledgeX1 = l.x1;
        ledgeY = l.y;
        y = l.y;
        vy = 0;
        dropThrough = null;
    }

    public void leaveGround() {
        onGround = false;
        ledgeId = null;
    }
}

package de.minicompanions.figure;

/**
 * Darstellungs-Pose einer Figur für ein Bild. Winkel in Radiant, Offsets in Skin-Pixeln.
 * Wird vom Animator berechnet und vom Renderer gezeichnet (wiederverwendet, keine Allokation pro Frame).
 */
public final class Pose {
    public enum View { FRONT, LEFT, RIGHT, BACK }

    public View view = View.FRONT;
    public float head;
    public float headY;
    public float body;
    public float armR;
    public float armL;
    public float legR;
    public float legL;
    /** Verschiebung des ganzen Körpers nach oben (negativ) / unten (positiv). */
    public float bobY;
    public float shiftX;
    /** Beine verkürzt (sitzend, von vorne gesehen). */
    public float legScale = 1f;
    /** Ganze Figur gedreht (z. B. liegend beim Schlafen, Wirbeln beim Werfen). */
    public float roll;
    /** Stauchen/Strecken beim Landen/Springen. */
    public float squash = 1f;
    /** Augen geschlossen (Schlaf, Blinzeln). */
    public boolean eyesClosed;

    public void reset(View v) {
        view = v;
        head = headY = body = armR = armL = legR = legL = bobY = shiftX = roll = 0;
        legScale = 1f;
        squash = 1f;
        eyesClosed = false;
    }
}

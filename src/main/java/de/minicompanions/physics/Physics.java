package de.minicompanions.physics;

/**
 * Einfache 2D-Physik: Schwerkraft, Einweg-Plattformen, Abprallen an Bildschirmrändern.
 * Garantiert: Ein Körper ist nach jedem Schritt innerhalb des Weltbereichs und nie in einer Plattform.
 */
public final class Physics {
    public static final float GRAVITY = 520f;
    public static final float MAX_FALL = 900f;

    private Physics() {
    }

    /**
     * Ein Physikschritt.
     *
     * @param bounce   Abprall-Faktor an Wänden und beim harten Landen (0..1)
     * @param friction Bodenreibung (pro Sekunde), wenn die KI nicht aktiv läuft
     */
    public static void step(Body b, PlatformSet world, float dt, float bounce, float friction) {
        if (b.held) {
            return;
        }
        dt = Math.min(dt, 0.05f);
        float half = b.w / 2f;
        b.lastImpact = 0;

        if (b.climbing) {
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            clamp(b, world);
            return;
        }

        if (b.onGround) {
            Ledge l = world.get(b.ledgeId);
            if (l == null) {
                // Plattform verschwunden (Screen geschlossen, gescrollt ...) -> fallen
                b.leaveGround();
            } else {
                // Bewegten Plattformen folgen (Chat-Scroll, verschobene Fenster, andere Figuren)
                b.x += l.x1 - b.ledgeX1;
                b.ledgeX1 = l.x1;
                b.ledgeY = l.y;
                b.y = l.y;
                b.x += b.vx * dt;
                if (friction > 0) {
                    b.vx *= (float) Math.exp(-friction * dt);
                }
                if (b.x < l.x1 - 0.5f || b.x > l.x2 + 0.5f) {
                    // Nahtlos auf eine angrenzende Kante gleicher Höhe wechseln (z. B. Slot-Reihen, Chat-Zeilen)
                    Ledge next = null;
                    for (Ledge o : world.ledges()) {
                        if (o != l && Math.abs(o.y - l.y) <= 1.5f && o.x1 - half <= b.x && o.x2 + half >= b.x
                                && o.kind != Ledge.Kind.FIGURE) {
                            next = o;
                            break;
                        }
                    }
                    if (next != null && (next.containsX(b.x) || Math.abs(next.y - l.y) <= 1.5f)) {
                        float keepX = Math.max(next.x1, Math.min(next.x2, b.x));
                        b.setGround(next);
                        b.x = keepX;
                    } else {
                        b.leaveGround();
                    }
                }
                b.vy = 0;
            }
        }

        if (!b.onGround) {
            float oldY = b.y;
            // Exakte Wurfparabel (Mittelwert der Geschwindigkeit): Sprünge sind bei jeder Bildrate gleich hoch
            // und weit, sonst fallen sie bei niedrigen FPS zu kurz aus und die Figur verfehlt ihr Ziel.
            float vy0 = b.vy;
            b.vy = Math.min(MAX_FALL, vy0 + GRAVITY * dt);
            b.x += b.vx * dt;
            b.y += (vy0 + b.vy) * 0.5f * dt;
            if (b.vy >= 0) {
                Ledge hit = null;
                for (Ledge l : world.ledges()) {
                    if (l.y >= oldY - 0.01f && l.y <= b.y && l.containsX(b.x) && !l.id.equals(b.dropThrough)
                            && (hit == null || l.y < hit.y)) {
                        hit = l;
                    }
                }
                if (hit != null) {
                    float impact = b.vy;
                    b.setGround(hit);
                    b.lastImpact = impact;
                    if (impact > 280 && bounce > 0) {
                        // weiches Abprallen beim Fallenlassen/Werfen
                        b.leaveGround();
                        b.vy = -impact * bounce * 0.45f;
                        b.y = hit.y - 0.5f;
                    }
                }
            }
        }

        // Wände links/rechts
        if (b.x - half < 0) {
            b.x = half;
            if (b.vx < 0) {
                b.vx = -b.vx * bounce;
            }
        } else if (b.x + half > world.width) {
            b.x = world.width - half;
            if (b.vx > 0) {
                b.vx = -b.vx * bounce;
            }
        }
        clamp(b, world);
    }

    /** Hält den Körper im sichtbaren Bereich (auch nach Fenstergrößen-Änderung). */
    public static void clamp(Body b, PlatformSet world) {
        float half = b.w / 2f;
        if (b.x < half) {
            b.x = half;
        }
        if (b.x > world.width - half) {
            b.x = Math.max(half, world.width - half);
        }
        if (b.y - b.h < 0) {
            b.y = Math.min(world.height, b.h);
            if (b.vy < 0) {
                b.vy = 0;
            }
        }
        if (b.y > world.height) {
            b.setGround(world.ground());
            b.y = world.height;
        }
    }

    /**
     * Startgeschwindigkeit für einen Sprung von (x0,y0) nach (x1,y1) mit Scheitel {@code apex} Pixel
     * über dem höheren der beiden Punkte. @return {vx, vy}
     */
    public static float[] jumpVelocity(float x0, float y0, float x1, float y1, float apex) {
        float top = Math.min(y0, y1) - Math.max(2f, apex);
        float up = y0 - top;
        float down = y1 - top;
        float vy = (float) -Math.sqrt(2 * GRAVITY * up);
        float tUp = -vy / GRAVITY;
        float tDown = (float) Math.sqrt(2 * Math.max(0.01f, down) / GRAVITY);
        float vx = (x1 - x0) / Math.max(0.05f, tUp + tDown);
        return new float[]{vx, vy};
    }

    /** Maximale Sprunghöhe zu einer Startgeschwindigkeit. */
    public static float jumpHeightFor(float vy) {
        return vy * vy / (2 * GRAVITY);
    }
}

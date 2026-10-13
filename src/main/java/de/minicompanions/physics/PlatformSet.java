package de.minicompanions.physics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alle Plattformen einer Welt (Bildschirm oder Zimmer-Fenster) für ein Bild.
 * Wird pro Frame neu aufgebaut; über die stabile Ledge-ID können Figuren bewegten Plattformen folgen.
 */
public final class PlatformSet {
    public final float width;
    public final float height;
    private final List<Ledge> ledges = new ArrayList<>();
    private final Map<String, Ledge> byId = new HashMap<>();
    /** Bereiche, die Figuren nicht betreten sollen (Ausblenden-Zonen). */
    private final List<float[]> blockedZones = new ArrayList<>();

    public PlatformSet(float width, float height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        Ledge ground = new Ledge("ground", -10000, 10000, this.height, this.height, Ledge.Kind.GROUND, false);
        ledges.add(ground);
        byId.put(ground.id, ground);
    }

    public void add(Ledge l) {
        if (l == null || l.width() < 2 || byId.containsKey(l.id)) {
            return;
        }
        // Kanten außerhalb des sichtbaren Bereichs werden ignoriert (sonst laufen Figuren aus dem Bild).
        if (l.y < 2 || l.y > height || l.x2 < 0 || l.x1 > width) {
            return;
        }
        ledges.add(l);
        byId.put(l.id, l);
    }

    public void addBlockedZone(float x1, float y1, float x2, float y2) {
        blockedZones.add(new float[]{Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)});
    }

    public boolean isBlocked(float x, float y) {
        for (float[] z : blockedZones) {
            if (x >= z[0] && x <= z[2] && y >= z[1] && y <= z[3]) {
                return true;
            }
        }
        return false;
    }

    /** Liegt ein Bereich [x-half, x+half] x [y-h, y] in einer gesperrten Zone? */
    public boolean overlapsBlocked(float x, float y, float half, float h) {
        for (float[] z : blockedZones) {
            if (x + half > z[0] && x - half < z[2] && y > z[1] && y - h < z[3]) {
                return true;
            }
        }
        return false;
    }

    public List<float[]> blockedZones() {
        return Collections.unmodifiableList(blockedZones);
    }

    public List<Ledge> ledges() {
        return ledges;
    }

    public Ledge get(String id) {
        return id == null ? null : byId.get(id);
    }

    public Ledge ground() {
        return byId.get("ground");
    }

    /** Erste Kante unterhalb (oder auf) y an Position x, optional ohne eine bestimmte ID. */
    public Ledge firstBelow(float x, float y, String exclude) {
        Ledge best = null;
        for (Ledge l : ledges) {
            if (l.y >= y && l.containsX(x) && !l.id.equals(exclude) && (best == null || l.y < best.y)) {
                best = l;
            }
        }
        return best;
    }

    /**
     * Kopffreiheit über einer Kante an Position x: Abstand zur nächsten Kante darüber,
     * die die Figurbreite überlappt. Große Figuren passen so nicht in enge Lücken.
     */
    public float headroom(Ledge on, float x, float halfWidth) {
        float best = on.y;
        for (Ledge l : ledges) {
            if (l == on || l.y >= on.y || l.kind == Ledge.Kind.FIGURE) {
                continue;
            }
            if (l.x2 > x - halfWidth && l.x1 < x + halfWidth) {
                best = Math.min(best, on.y - l.y);
            }
        }
        return best;
    }
}

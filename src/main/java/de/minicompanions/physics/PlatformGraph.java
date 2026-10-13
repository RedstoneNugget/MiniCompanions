package de.minicompanions.physics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Plattform-Graph für die Kletter-KI: findet einen Weg von Kante zu Kante (Laufen, Springen,
 * Hinunterspringen, Klettern). Große Figuren meiden Kanten ohne genug Kopffreiheit, damit sie nicht
 * in enge Lücken (z. B. zwischen Slot-Reihen) steigen.
 *
 * <p>Reine Logik - keine Minecraft-Klassen.</p>
 */
public final class PlatformGraph {
    public enum MoveType { JUMP, DROP, CLIMB }

    /** Ein Wegschritt: zu launchX laufen, dann Aktion ausführen und bei landX auf Kante toId ankommen. */
    public record Step(MoveType type, String fromId, String toId, float launchX, float landX, float toY) {
    }

    /** Fähigkeiten der Figur (abhängig von ihrer Größe). */
    public record Spec(float halfWidth, float height, float jumpHeight, float jumpReach, boolean useFigures) {
    }

    private PlatformGraph() {
    }

    /** Darf eine Figur dieser Größe auf der Kante an Position x stehen? */
    public static boolean fits(PlatformSet set, Ledge l, float x, Spec spec) {
        if (l.kind == Ledge.Kind.GROUND) {
            return true;
        }
        if (l.width() < spec.halfWidth * 2f) {
            return false;
        }
        if (set.overlapsBlocked(x, l.y, spec.halfWidth, spec.height)) {
            return false;
        }
        return set.headroom(l, x, spec.halfWidth) >= spec.height * 0.92f;
    }

    /**
     * Sucht einen Weg. @return Liste der Schritte (leer = gleiche Kante), oder null, wenn unerreichbar.
     */
    public static List<Step> findPath(PlatformSet set, Ledge start, float startX, Ledge goal, float goalX, Spec spec, int maxExpanded) {
        return findPath(set, start, startX, goal, goalX, spec, maxExpanded, null);
    }

    /** Wie oben; {@code avoid} schließt Kanten aus (z. B. nach mehreren verfehlten Sprüngen). */
    public static List<Step> findPath(PlatformSet set, Ledge start, float startX, Ledge goal, float goalX, Spec spec, int maxExpanded,
                                      java.util.function.Predicate<String> avoid) {
        if (start == null || goal == null) {
            return null;
        }
        if (start.id.equals(goal.id)) {
            return Collections.emptyList();
        }
        Map<String, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Float.compare(a.cost, b.cost));
        Node s = new Node(start, startX, 0, null, null);
        nodes.put(start.id, s);
        open.add(s);
        int expanded = 0;
        List<Step> tmp = new ArrayList<>();
        while (!open.isEmpty() && expanded < maxExpanded) {
            Node n = open.poll();
            if (n.closed) {
                continue;
            }
            n.closed = true;
            expanded++;
            if (n.ledge.id.equals(goal.id)) {
                List<Step> path = new ArrayList<>();
                for (Node c = n; c.via != null; c = c.prev) {
                    path.add(c.via);
                }
                Collections.reverse(path);
                return path;
            }
            tmp.clear();
            neighbors(set, n.ledge, n.x, spec, tmp);
            for (Step st : tmp) {
                Ledge to = set.get(st.toId());
                if (to == null || avoid != null && avoid.test(to.id)) {
                    continue;
                }
                float c = n.cost + Math.abs(n.x - st.launchX()) + moveCost(st, n.ledge, to);
                if (to.id.equals(goal.id)) {
                    c += Math.abs(st.landX() - goalX) * 0.5f;
                }
                Node old = nodes.get(to.id);
                if (old == null || (!old.closed && c < old.cost)) {
                    Node nn = new Node(to, st.landX(), c, n, st);
                    nodes.put(to.id, nn);
                    open.add(nn);
                }
            }
        }
        return null;
    }

    private static float moveCost(Step st, Ledge from, Ledge to) {
        float dy = Math.abs(from.y - to.y);
        return switch (st.type()) {
            case JUMP -> 18 + dy * 0.8f;
            case DROP -> 8 + dy * 0.2f;
            case CLIMB -> 30 + dy * 1.4f;
        } + (to.kind == Ledge.Kind.FIGURE ? 40 : 0);
    }

    /** Alle direkt erreichbaren Kanten von a aus. */
    public static void neighbors(PlatformSet set, Ledge a, float ax, Spec spec, List<Step> out) {
        float half = spec.halfWidth;
        for (Ledge b : set.ledges()) {
            if (b == a || (b.kind == Ledge.Kind.FIGURE && !spec.useFigures)) {
                continue;
            }
            float dy = a.y - b.y; // > 0: b liegt höher
            float ox1 = Math.max(a.x1, b.x1) + half;
            float ox2 = Math.min(a.x2, b.x2) - half;
            if (dy > 0.5f) {
                if (dy <= spec.jumpHeight - 2) {
                    if (ox2 >= ox1) {
                        float lx = clamp(ax, ox1, ox2);
                        if (fits(set, b, lx, spec)) {
                            out.add(new Step(MoveType.JUMP, a.id, b.id, lx, lx, b.y));
                        }
                        continue;
                    }
                    float gapR = b.x1 - a.x2;
                    if (gapR >= -half * 2 && gapR <= spec.jumpReach && a.width() >= half * 2) {
                        float lx = b.x1 + Math.max(half, Math.min(6, b.width() / 2));
                        if (fits(set, b, lx, spec)) {
                            out.add(new Step(MoveType.JUMP, a.id, b.id, a.x2 - half, lx, b.y));
                        }
                        continue;
                    }
                    float gapL = a.x1 - b.x2;
                    if (gapL >= -half * 2 && gapL <= spec.jumpReach && a.width() >= half * 2) {
                        float lx = b.x2 - Math.max(half, Math.min(6, b.width() / 2));
                        if (fits(set, b, lx, spec)) {
                            out.add(new Step(MoveType.JUMP, a.id, b.id, a.x1 + half, lx, b.y));
                        }
                        continue;
                    }
                }
                // Klettern an den Seiten hoher Elemente (Inventar-Fenster, Chat-Box, Zimmer-Fenster)
                if (b.climbable && a.y >= b.bottom - 1.5f && a.y - b.y < 600) {
                    float left = b.x1 - half - 0.5f;
                    float right = b.x2 + half + 0.5f;
                    boolean canLeft = left >= a.x1 && left <= a.x2;
                    boolean canRight = right >= a.x1 && right <= a.x2;
                    if (canLeft && (!canRight || Math.abs(ax - left) <= Math.abs(ax - right))) {
                        float lx = b.x1 + half + 1;
                        if (fits(set, b, lx, spec)) {
                            out.add(new Step(MoveType.CLIMB, a.id, b.id, left, lx, b.y));
                        }
                    } else if (canRight) {
                        float lx = b.x2 - half - 1;
                        if (fits(set, b, lx, spec)) {
                            out.add(new Step(MoveType.CLIMB, a.id, b.id, right, lx, b.y));
                        }
                    }
                }
            } else if (dy < -0.5f) {
                // b liegt tiefer: hinunterspringen (durch die Kante) oder über die Kante laufen
                if (ox2 >= ox1) {
                    float lx = clamp(ax, ox1, ox2);
                    Ledge first = set.firstBelow(lx, a.y + 0.1f, a.id);
                    if (first == b && fits(set, b, lx, spec)) {
                        out.add(new Step(MoveType.DROP, a.id, b.id, lx, lx, b.y));
                        continue;
                    }
                }
                float pr = a.x2 + half + 1;
                if (b.containsX(pr) && set.firstBelow(pr, a.y + 0.1f, a.id) == b && fits(set, b, pr, spec)) {
                    out.add(new Step(MoveType.DROP, a.id, b.id, a.x2, pr, b.y));
                    continue;
                }
                float pl = a.x1 - half - 1;
                if (b.containsX(pl) && set.firstBelow(pl, a.y + 0.1f, a.id) == b && fits(set, b, pl, spec)) {
                    out.add(new Step(MoveType.DROP, a.id, b.id, a.x1, pl, b.y));
                    continue;
                }
                // Sprung nach unten über eine Lücke
                float gapR = b.x1 - a.x2;
                if (gapR > 0 && gapR <= spec.jumpReach * 1.5f) {
                    float lx = b.x1 + Math.max(half, Math.min(6, b.width() / 2));
                    if (fits(set, b, lx, spec)) {
                        out.add(new Step(MoveType.JUMP, a.id, b.id, a.x2 - half, lx, b.y));
                    }
                    continue;
                }
                float gapL = a.x1 - b.x2;
                if (gapL > 0 && gapL <= spec.jumpReach * 1.5f) {
                    float lx = b.x2 - Math.max(half, Math.min(6, b.width() / 2));
                    if (fits(set, b, lx, spec)) {
                        out.add(new Step(MoveType.JUMP, a.id, b.id, a.x1 + half, lx, b.y));
                    }
                }
            } else {
                // gleiche Höhe: kleine Lücke überspringen
                float gapR = b.x1 - a.x2;
                float gapL = a.x1 - b.x2;
                if (gapR >= 0 && gapR <= spec.jumpReach) {
                    float lx = b.x1 + Math.max(half, Math.min(4, b.width() / 2));
                    if (fits(set, b, lx, spec)) {
                        out.add(new Step(MoveType.JUMP, a.id, b.id, a.x2 - half, lx, b.y));
                    }
                } else if (gapL >= 0 && gapL <= spec.jumpReach) {
                    float lx = b.x2 - Math.max(half, Math.min(4, b.width() / 2));
                    if (fits(set, b, lx, spec)) {
                        out.add(new Step(MoveType.JUMP, a.id, b.id, a.x1 + half, lx, b.y));
                    }
                }
            }
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static final class Node {
        final Ledge ledge;
        final float x;
        final float cost;
        final Node prev;
        final Step via;
        boolean closed;

        Node(Ledge ledge, float x, float cost, Node prev, Step via) {
            this.ledge = ledge;
            this.x = x;
            this.cost = cost;
            this.prev = prev;
            this.via = via;
        }
    }
}

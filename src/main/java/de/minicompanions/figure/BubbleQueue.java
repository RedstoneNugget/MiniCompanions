package de.minicompanions.figure;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sprechblasen einer Figur: eine sichtbare Blase plus kleine Warteschlange.
 * Wird von eigenen Sprüchen, Minecraft-Chat-Reaktionen und Zuschauer-Nachrichten gemeinsam genutzt.
 * Zuschauer-Texte werden nur so lange im Speicher gehalten, wie die Blase sichtbar ist.
 */
public final class BubbleQueue {
    public static final long FADE_IN = 150;
    public static final long FADE_OUT = 300;

    /** Eine Blase. Text ist bereits bereinigt (nur reiner Text). */
    public record Bubble(String text, boolean viewer, int bgColor, int textColor, int accentColor, long durationMs, long createdMs) {
    }

    private Bubble current;
    private long shownAt;
    private final Deque<Bubble> queue = new ArrayDeque<>();

    /** Neue Blase: ersetzt die aktuelle (maxQueue = 1) oder reiht sich ein. */
    public void push(Bubble b, int maxQueue, long now) {
        if (current == null || maxQueue <= 1) {
            current = b;
            shownAt = now;
            queue.clear();
            return;
        }
        while (queue.size() >= maxQueue - 1) {
            queue.pollFirst();
        }
        queue.addLast(b);
    }

    public void update(long now) {
        if (current != null && now - shownAt > current.durationMs() + FADE_OUT) {
            current = queue.pollFirst();
            shownAt = now;
        }
    }

    public Bubble current() {
        return current;
    }

    /** Deckkraft 0..1 inkl. Ein-/Ausblenden. */
    public float alpha(long now) {
        if (current == null) {
            return 0;
        }
        long t = now - shownAt;
        if (t < FADE_IN) {
            return Math.max(0.05f, t / (float) FADE_IN);
        }
        long end = current.durationMs();
        if (t > end) {
            return Math.max(0f, 1f - (t - end) / (float) FADE_OUT);
        }
        return 1f;
    }

    /** Nur Zuschauer-Blasen entfernen (Moderation, Streamer-Modus aus, Panik). */
    public void clearViewer() {
        queue.removeIf(Bubble::viewer);
        if (current != null && current.viewer()) {
            current = queue.pollFirst();
            shownAt = System.currentTimeMillis();
        }
    }

    public void clear() {
        current = null;
        queue.clear();
    }

    public boolean isEmpty() {
        return current == null;
    }
}

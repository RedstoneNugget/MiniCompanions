package de.minicompanions.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cooldown pro Schlüssel (z. B. pro Zuschauer). Reine Logik und daher in Unit-Tests prüfbar.
 * Alte Einträge werden regelmäßig entfernt, damit der Speicher nicht wächst.
 */
public final class RateLimiter {
    private final Map<String, Long> last = new HashMap<>();
    private long lastCleanup;

    /** @return true, wenn der Schlüssel jetzt wieder darf (und merkt sich den Zeitpunkt). */
    public boolean tryAcquire(String key, long now, long cooldownMs) {
        cleanup(now, Math.max(cooldownMs, 60_000));
        Long prev = last.get(key);
        if (prev != null && now - prev < cooldownMs) {
            return false;
        }
        last.put(key, now);
        return true;
    }

    /** Verbleibende Wartezeit in ms (0 = darf sofort). */
    public long remaining(String key, long now, long cooldownMs) {
        Long prev = last.get(key);
        return prev == null ? 0 : Math.max(0, cooldownMs - (now - prev));
    }

    public void clear() {
        last.clear();
    }

    public void forget(String key) {
        last.remove(key);
    }

    private void cleanup(long now, long maxAge) {
        if (now - lastCleanup < 30_000) {
            return;
        }
        lastCleanup = now;
        Iterator<Map.Entry<String, Long>> it = last.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > maxAge) {
                it.remove();
            }
        }
    }

    /** Globales Limit „höchstens n Ereignisse pro Zeitfenster“ (gleitendes Fenster). */
    public static final class Window {
        private final long[] stamps;
        private int head;
        private int size;

        public Window(int capacity) {
            stamps = new long[Math.max(1, capacity)];
        }

        public boolean tryAcquire(long now, int maxEvents, long windowMs) {
            int max = Math.min(maxEvents, stamps.length);
            while (size > 0 && now - stamps[(head - size + stamps.length) % stamps.length] >= windowMs) {
                size--;
            }
            if (size >= max) {
                return false;
            }
            stamps[head] = now;
            head = (head + 1) % stamps.length;
            size = Math.min(size + 1, stamps.length);
            return true;
        }

        public void clear() {
            size = 0;
        }
    }
}

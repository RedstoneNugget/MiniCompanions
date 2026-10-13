package de.minicompanions.figure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sprechblasen: neue Zuschauer-Nachricht ersetzt sofort die alte (Warteschlange 1). */
class BubbleQueueTest {
    private static BubbleQueue.Bubble b(String text, long now) {
        return new BubbleQueue.Bubble(text, true, 0xFFFFFFFF, 0xFF202020, 0, 6000, now);
    }

    @Test
    void newMessageReplacesOldImmediately() {
        BubbleQueue q = new BubbleQueue();
        q.push(b("hallo", 0), 1, 0);
        q.push(b("wie gehts", 100), 1, 100);
        q.push(b("cooler stream", 200), 1, 200);
        assertEquals("cooler stream", q.current().text());
        q.update(300);
        assertEquals("cooler stream", q.current().text());
        // nach Ablauf verschwindet sie, es kommt nichts Altes nach
        q.update(200 + 6000 + BubbleQueue.FADE_OUT + 1);
        assertTrue(q.isEmpty());
    }

    @Test
    void queueModeShowsMessagesOneAfterAnother() {
        BubbleQueue q = new BubbleQueue();
        q.push(b("eins", 0), 3, 0);
        q.push(b("zwei", 10), 3, 10);
        assertEquals("eins", q.current().text());
        q.update(6000 + BubbleQueue.FADE_OUT + 1);
        assertEquals("zwei", q.current().text());
    }
}

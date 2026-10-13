package de.minicompanions.streamer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Warteschlange für Spawn-Anfragen (mit Maximalgröße) und Freigabe-Warteschlange
 * für den Sicherheitsmodus. Pro Zuschauer gibt es höchstens einen wartenden Eintrag.
 */
public final class SpawnQueue {
    /** Eine geprüfte Spawn-Anfrage. {@code mcName} ist bereits als gültiger Name validiert. */
    public record Request(String login, String displayName, String mcName, ChatRoles roles, Integer colorRgb, long time) {
    }

    /** Eine Nachricht, die im Sicherheitsmodus auf Freigabe wartet. */
    public record PendingMessage(String login, String displayName, String text, ChatRoles roles, Integer colorRgb, long time) {
    }

    private final Deque<Request> queue = new ArrayDeque<>();
    private final List<Request> approvals = new ArrayList<>();
    private final List<PendingMessage> messageApprovals = new ArrayList<>();

    /** @return false, wenn die Warteschlange voll ist. */
    public synchronized boolean offer(Request r, int maxSize) {
        removeLogin(queue, r.login());
        if (queue.size() >= maxSize) {
            return false;
        }
        queue.addLast(r);
        return true;
    }

    public synchronized Request poll() {
        return queue.pollFirst();
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean awaitApproval(Request r, int maxSize) {
        approvals.removeIf(a -> a.login().equals(r.login()));
        if (approvals.size() >= maxSize) {
            return false;
        }
        approvals.add(r);
        return true;
    }

    public synchronized List<Request> approvals() {
        return new ArrayList<>(approvals);
    }

    public synchronized Request takeApproval(String login) {
        Iterator<Request> it = approvals.iterator();
        while (it.hasNext()) {
            Request r = it.next();
            if (r.login().equals(login)) {
                it.remove();
                return r;
            }
        }
        return null;
    }

    public synchronized boolean awaitMessageApproval(PendingMessage m, int maxSize) {
        if (messageApprovals.size() >= maxSize) {
            return false;
        }
        messageApprovals.add(m);
        return true;
    }

    public synchronized List<PendingMessage> messageApprovals() {
        return new ArrayList<>(messageApprovals);
    }

    public synchronized PendingMessage takeMessageApproval(int index) {
        if (index < 0 || index >= messageApprovals.size()) {
            return null;
        }
        return messageApprovals.remove(index);
    }

    /** Alles entfernen, was von diesem Zuschauer wartet. */
    public synchronized void removeViewer(String login) {
        removeLogin(queue, login);
        approvals.removeIf(a -> a.login().equals(login));
        messageApprovals.removeIf(m -> m.login().equals(login));
    }

    public synchronized void clear() {
        queue.clear();
        approvals.clear();
        messageApprovals.clear();
    }

    private static void removeLogin(Deque<Request> q, String login) {
        q.removeIf(r -> r.login().equals(login));
    }
}

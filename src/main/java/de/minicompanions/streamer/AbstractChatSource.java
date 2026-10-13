package de.minicompanions.streamer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Gemeinsame Basis für lesende Chat-Verbindungen (Kick, YouTube, TikTok): eigener Hintergrund-Thread,
 * Wiederverbinden mit Backoff, Statusanzeige und sauberes Beenden. Unterklassen implementieren nur
 * {@link #session}. Es wird nie in einen Chat geschrieben.
 */
public abstract class AbstractChatSource implements ChatSource {
    protected final Logger log = LoggerFactory.getLogger("MiniCompanions/" + getClass().getSimpleName());

    /** Stream ist gerade nicht live: später erneut versuchen, ohne als Fehler zu gelten. */
    public static final class OfflineException extends IOException {
        public OfflineException(String msg) {
            super(msg);
        }
    }

    /** Dauerhafter Fehler (Kanal existiert nicht, ungültiger Name): nicht weiter versuchen. */
    public static final class FatalException extends IOException {
        public FatalException(String msg) {
            super(msg);
        }
    }

    private volatile Status status = Status.DISCONNECTED;
    private volatile String lastError;
    private volatile boolean running;
    private volatile String channel = "";
    private Thread thread;

    /** Eingabe (Name, @Name oder Link) prüfen und vereinheitlichen; null = ungültig. */
    protected abstract String normalize(String input);

    /** Eine Verbindung aufbauen und lesen, bis sie endet (Rückkehr oder Ausnahme = neu verbinden). */
    protected abstract void session(String channel, Consumer<ChatEvent> sink) throws Exception;

    /** Offene Verbindungen sofort schließen (wird von {@link #stop()} aufgerufen). */
    protected void abortSession() {
    }

    /** Wartezeit, wenn der Stream gerade nicht live ist. */
    protected long offlineRetryMs() {
        return 60_000;
    }

    @Override
    public synchronized void start(String input, Consumer<ChatEvent> sink) {
        stop();
        String ch = normalize(input);
        if (ch == null) {
            status = Status.ERROR;
            lastError = "invalid_channel";
            return;
        }
        channel = ch;
        running = true;
        lastError = null;
        status = Status.CONNECTING;
        thread = new Thread(() -> loop(sink), "MiniCompanions-" + platform().name() + "Chat");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop(Consumer<ChatEvent> sink) {
        long backoff = 2000;
        while (running) {
            long wait;
            try {
                session(channel, sink);
                backoff = 2000;
                wait = 1000;
            } catch (OfflineException e) {
                status = Status.OFFLINE;
                lastError = e.getMessage();
                backoff = 2000;
                wait = offlineRetryMs();
            } catch (FatalException e) {
                status = Status.ERROR;
                lastError = e.getMessage();
                log.info("[MiniCompanions] {}-Chat: {}", platform().displayName(), e.getMessage());
                running = false;
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                lastError = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                log.warn("[MiniCompanions] {}-Chat getrennt: {}", platform().displayName(), lastError);
                status = Status.RECONNECTING;
                wait = backoff + ThreadLocalRandom.current().nextLong(1000);
                backoff = Math.min(60_000, backoff * 2);
            } finally {
                abortSession();
            }
            if (!running) {
                break;
            }
            if (status == Status.CONNECTED || status == Status.CONNECTING) {
                status = Status.RECONNECTING;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (status != Status.ERROR) {
            status = Status.DISCONNECTED;
        }
    }

    /** Von der Unterklasse aufrufen, sobald Nachrichten ankommen können. */
    protected void markConnected() {
        status = Status.CONNECTED;
        lastError = null;
    }

    protected boolean running() {
        return running && !Thread.currentThread().isInterrupted();
    }

    @Override
    public synchronized void stop() {
        running = false;
        abortSession();
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (status != Status.ERROR) {
            status = Status.DISCONNECTED;
        }
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public String lastError() {
        return lastError;
    }

    @Override
    public String channel() {
        return channel;
    }

    /** Text auf eine sichere Länge kürzen (für Fehlermeldungen aus fremden Antworten). */
    protected static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}

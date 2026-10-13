package de.minicompanions.streamer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Anonyme, rein lesende Verbindung zum Twitch-Chat über IRC (TLS, Port 6697).
 *
 * <p>Prinzip übernommen aus "Take Your Stream Chat" von ijustseen (MIT-Lizenz): anonymer Login als
 * {@code justinfanNNNNN}, {@code CAP REQ} für Tags/Commands, {@code JOIN #kanal}, PING/PONG.
 * Eigene Erweiterungen: Wiederverbinden mit exponentiellem Backoff, Lese-Timeout, sauberes Beenden,
 * Statusanzeige, Behandlung von RECONNECT/NOTICE sowie Moderations-Ereignissen.</p>
 *
 * <p>Es wird NIE in den Chat geschrieben - nur Protokollbefehle (CAP, PASS, NICK, JOIN, PONG, PART).</p>
 */
public final class TwitchChatSource implements ChatSource {
    private static final Logger LOG = LoggerFactory.getLogger("MiniCompanions/Twitch");
    private static final String HOST = "irc.chat.twitch.tv";
    private static final int PORT = 6697;

    private volatile Status status = Status.DISCONNECTED;
    private volatile String lastError;
    private volatile boolean running;
    private volatile SSLSocket socket;
    private volatile String channel = "";
    private Thread thread;

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.TWITCH;
    }

    @Override
    public synchronized void start(String channelName, Consumer<ChatEvent> sink) {
        stop();
        if (!TwitchIrcParser.isValidChannel(channelName)) {
            status = Status.ERROR;
            lastError = "invalid_channel";
            return;
        }
        this.channel = channelName.toLowerCase(Locale.ROOT);
        running = true;
        lastError = null;
        status = Status.CONNECTING;
        thread = new Thread(() -> loop(sink), "MiniCompanions-TwitchChat");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop(Consumer<ChatEvent> sink) {
        long backoff = 1000;
        while (running) {
            try {
                connectAndRead(sink);
                backoff = 1000;
            } catch (IOException e) {
                if (running) {
                    lastError = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                    LOG.warn("[MiniCompanions] Twitch-Chat getrennt: {}", lastError);
                }
            } catch (RuntimeException e) {
                lastError = e.toString();
                LOG.error("[MiniCompanions] Fehler im Twitch-Chat-Thread", e);
            } finally {
                closeSocket();
            }
            if (!running) {
                break;
            }
            status = Status.RECONNECTING;
            try {
                Thread.sleep(backoff + ThreadLocalRandom.current().nextLong(500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            backoff = Math.min(60_000, backoff * 2);
        }
        status = Status.DISCONNECTED;
    }

    private void connectAndRead(Consumer<ChatEvent> sink) throws IOException {
        status = status == Status.RECONNECTING ? Status.RECONNECTING : Status.CONNECTING;
        SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        socket = s;
        s.connect(new InetSocketAddress(HOST, PORT), 10_000);
        s.setSoTimeout(30_000);
        s.startHandshake();
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        send(out, "CAP REQ :twitch.tv/tags twitch.tv/commands");
        send(out, "PASS SCHMOOPIIE");
        send(out, "NICK justinfan" + (10000 + ThreadLocalRandom.current().nextInt(89999)));
        send(out, "JOIN #" + channel);
        long lastRx = System.currentTimeMillis();
        boolean pingSent = false;
        while (running) {
            String line;
            try {
                line = in.readLine();
            } catch (SocketTimeoutException timeout) {
                // Keepalive: Twitch sendet alle ~5 Minuten PING; wir prüfen zusätzlich selbst.
                long idle = System.currentTimeMillis() - lastRx;
                if (idle > 6 * 60_000) {
                    throw new IOException("timeout");
                }
                if (idle > 4 * 60_000 && !pingSent) {
                    send(out, "PING :tmi.twitch.tv");
                    pingSent = true;
                }
                continue;
            }
            if (line == null) {
                throw new IOException("closed");
            }
            lastRx = System.currentTimeMillis();
            pingSent = false;
            if (line.length() > TwitchIrcParser.MAX_LINE) {
                continue;
            }
            if (line.startsWith("PING")) {
                send(out, "PONG" + line.substring(4));
                continue;
            }
            TwitchIrcParser.Line l = TwitchIrcParser.parse(line);
            if (l == null) {
                continue;
            }
            switch (l.command()) {
                case "001" -> {
                    // Willkommen - noch nicht im Kanal
                }
                case "JOIN", "ROOMSTATE" -> {
                    status = Status.CONNECTED;
                    lastError = null;
                }
                case "RECONNECT" -> throw new IOException("server_reconnect");
                case "NOTICE" -> {
                    String t = l.trailing();
                    if (t != null && (t.toLowerCase(Locale.ROOT).contains("does not exist")
                            || t.toLowerCase(Locale.ROOT).contains("suspended") || t.contains("Login unsuccessful"))) {
                        lastError = t.length() > 120 ? t.substring(0, 120) : t;
                        status = Status.ERROR;
                    }
                }
                default -> {
                    ChatEvent ev = TwitchIrcParser.toEvent(l);
                    if (ev != null) {
                        sink.accept(ev);
                    }
                }
            }
        }
        try {
            send(out, "PART #" + channel);
        } catch (IOException ignored) {
            // beim Beenden egal
        }
    }

    private static void send(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    private void closeSocket() {
        SSLSocket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // egal
            }
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        closeSocket();
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
        status = Status.DISCONNECTED;
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
}

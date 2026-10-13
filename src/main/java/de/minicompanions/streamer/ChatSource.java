package de.minicompanions.streamer;

import java.util.function.Consumer;

/**
 * Lesende Verbindung zu einem Stream-Chat. Implementierungen laufen strikt in einem eigenen
 * Hintergrund-Thread, schreiben nie in den Chat und liefern Ereignisse über einen thread-sicheren
 * Consumer (Queue) an den Client-Thread.
 */
public interface ChatSource {
    enum Status { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, OFFLINE, ERROR }

    ChatPlatform platform();

    /** Startet die Verbindung im Hintergrund (kehrt sofort zurück). */
    void start(String channel, Consumer<ChatEvent> sink);

    /** Trennt die Verbindung und beendet den Thread sauber. */
    void stop();

    Status status();

    /** Letzte Fehlermeldung (für die Statusanzeige) oder null. */
    String lastError();

    String channel();
}

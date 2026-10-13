package de.minicompanions.streamer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live-Test gegen echte Streams (nur auf Wunsch, braucht Internet und gerade laufende Streams):
 * {@code gradlew test --tests '*LiveChatProbe*' -Dlive=true -Dkick=kanal -Dyoutube=@kanal -Dtiktok=name}.
 */
@EnabledIfSystemProperty(named = "live", matches = "true")
class LiveChatProbeTest {

    private static void probe(ChatPlatform p, String channel) throws InterruptedException {
        if (channel == null || channel.isBlank()) {
            return;
        }
        int seconds = Integer.parseInt(System.getProperty("seconds", "40"));
        List<ChatEvent> events = Collections.synchronizedList(new ArrayList<>());
        ChatSource s = p.create();
        s.start(channel, events::add);
        ChatSource.Status best = ChatSource.Status.DISCONNECTED;
        for (int i = 0; i < seconds * 2; i++) {
            Thread.sleep(500);
            if (s.status() == ChatSource.Status.CONNECTED) {
                best = ChatSource.Status.CONNECTED;
            }
            if (best == ChatSource.Status.CONNECTED && events.size() >= 5) {
                break;
            }
        }
        ChatSource.Status last = s.status();
        String err = s.lastError();
        s.stop();
        System.out.println("[LIVE] " + p.displayName() + " '" + channel + "': status=" + last + " (best " + best + ") error=" + err
                + " events=" + events.size());
        synchronized (events) {
            events.stream().limit(8).forEach(e -> System.out.println("[LIVE]   " + e));
        }
        assertEquals(ChatSource.Status.CONNECTED, best, p.displayName() + " nicht verbunden: " + err);
        assertFalse(events.isEmpty(), p.displayName() + ": keine Nachrichten empfangen");
        for (ChatEvent e : events) {
            if (e instanceof ChatEvent.Message m) {
                assertTrue(m.login().startsWith(p.loginPrefix()), "Login mit Präfix: " + m.login());
                assertTrue(m.login().matches("[a-z0-9_]{1,40}|(?:kick|yt|tt):[a-z0-9_.-]{1,64}"), "gültiger Login: " + m.login());
            }
        }
    }

    @Test
    void kick() throws InterruptedException {
        probe(ChatPlatform.KICK, System.getProperty("kick"));
    }

    @Test
    void youtube() throws InterruptedException {
        probe(ChatPlatform.YOUTUBE, System.getProperty("youtube"));
    }

    @Test
    void tiktok() throws InterruptedException {
        probe(ChatPlatform.TIKTOK, System.getProperty("tiktok"));
    }

    @Test
    void twitch() throws InterruptedException {
        probe(ChatPlatform.TWITCH, System.getProperty("twitch"));
    }
}

package de.minicompanions.streamer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP-Hilfen für die Chat-Verbindungen: gemeinsamer Client, Browser-Kennung, Größenlimit für Antworten.
 * Es werden nur lesende Abfragen an die jeweilige Plattform (bzw. den TikTok-Signierdienst) gestellt.
 */
public final class ChatHttp {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    /** Größte erlaubte Antwort (YouTube-Seiten sind groß, aber nie mehr als ein paar MB). */
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ChatHttp() {
    }

    /** Antwort mit Statuscode, Inhalt und Kopfzeilen. */
    public record Response(int status, byte[] body, HttpResponse<?> raw) {
        public String text() {
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }

        public String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }
    }

    public static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8");
    }

    public static Response send(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<InputStream> r = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = r.body()) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                throw new IOException("response_too_large");
            }
            return new Response(r.statusCode(), data, r);
        }
    }

    public static Response get(String url, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = request(url).GET();
        for (int i = 0; i + 1 < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return send(b.build());
    }

    public static Response postJson(String url, String json, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = request(url).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return send(b.build());
    }
}

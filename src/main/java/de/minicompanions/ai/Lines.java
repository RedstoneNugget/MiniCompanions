package de.minicompanions.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Sprüche der Figuren pro Persönlichkeit und Situation. Standard-Sprüche liegen in
 * assets/minicompanions/lines/&lt;sprache&gt;.json; eigene Sprüche-Dateien in config/minicompanions/lines/
 * ergänzen oder ersetzen sie (z. B. "de_de.json" oder "de_de_happy.json" für eine Persönlichkeit).
 */
public final class Lines {
    private static final Logger LOG = LoggerFactory.getLogger("MiniCompanions/Lines");
    /** personality ("default", "happy" ...) -> situation -> Sprüche */
    private final Map<String, Map<String, List<String>>> lines = new HashMap<>();
    private final Random rng = new Random();
    private String lang = "de_de";

    public void load(String language, Path userDir) {
        lines.clear();
        lang = language;
        loadResource("en_us");
        if (!"en_us".equals(language)) {
            Map<String, Map<String, List<String>>> fallback = new HashMap<>(lines);
            lines.clear();
            loadResource(language);
            // fehlende Situationen aus Englisch ergänzen
            for (var e : fallback.entrySet()) {
                lines.computeIfAbsent(e.getKey(), k -> new HashMap<>());
                for (var s : e.getValue().entrySet()) {
                    lines.get(e.getKey()).putIfAbsent(s.getKey(), s.getValue());
                }
            }
        }
        if (userDir != null && Files.isDirectory(userDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(userDir, "*.json")) {
                for (Path p : ds) {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!n.startsWith(language)) {
                        continue;
                    }
                    String suffix = n.substring(language.length()).replace(".json", "");
                    String forPersonality = suffix.startsWith("_") ? suffix.substring(1) : null;
                    try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        merge(JsonParser.parseReader(r).getAsJsonObject(), forPersonality);
                    } catch (Exception e) {
                        LOG.warn("[MiniCompanions] Sprüche-Datei {} fehlerhaft: {}", p.getFileName(), e.toString());
                    }
                }
            } catch (Exception e) {
                LOG.warn("[MiniCompanions] Sprüche-Ordner nicht lesbar: {}", e.toString());
            }
        }
    }

    public String language() {
        return lang;
    }

    private void loadResource(String language) {
        try (InputStream in = Lines.class.getResourceAsStream("/assets/minicompanions/lines/" + language + ".json")) {
            if (in == null) {
                return;
            }
            merge(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject(), null);
        } catch (Exception e) {
            LOG.warn("[MiniCompanions] Sprüche {} nicht ladbar: {}", language, e.toString());
        }
    }

    /**
     * @param forPersonality wenn gesetzt, ist die Datei eine reine Situation->Sprüche-Liste für diese Persönlichkeit
     */
    private void merge(JsonObject root, String forPersonality) {
        if (forPersonality != null) {
            addSituations(forPersonality, root);
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (e.getValue().isJsonObject()) {
                addSituations(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonObject());
            }
        }
    }

    private void addSituations(String personality, JsonObject obj) {
        Map<String, List<String>> map = lines.computeIfAbsent(personality, k -> new HashMap<>());
        for (Map.Entry<String, JsonElement> s : obj.entrySet()) {
            if (!s.getValue().isJsonArray()) {
                continue;
            }
            List<String> list = new ArrayList<>();
            JsonArray arr = s.getValue().getAsJsonArray();
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) {
                    String t = el.getAsString().replaceAll("[\\p{Cntrl}§]", "");
                    if (!t.isBlank() && t.length() <= 120) {
                        list.add(t);
                    }
                }
            }
            if (!list.isEmpty()) {
                map.put(s.getKey(), list);
            }
        }
    }

    /** Zufälliger Spruch für Persönlichkeit + Situation (oder null). */
    public String get(Personality p, String situation) {
        List<String> l = null;
        if (p != null) {
            Map<String, List<String>> m = lines.get(p.key());
            if (m != null) {
                l = m.get(situation);
            }
        }
        if (l == null || (p != null && rng.nextInt(4) == 0)) {
            Map<String, List<String>> d = lines.get("default");
            List<String> dl = d == null ? null : d.get(situation);
            if (dl != null && (l == null || rng.nextBoolean())) {
                l = dl;
            }
        }
        if (l == null || l.isEmpty()) {
            return null;
        }
        return l.get(rng.nextInt(l.size()));
    }
}

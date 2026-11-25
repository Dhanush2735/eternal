package com.eternalengine.timeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Holds every authored "what if" scenario the server knows about, indexed by the pin that
 * triggers it.
 *
 * <p>Scenarios live as JSON so they can be written, reviewed, and contributed without touching
 * code. The registry only loads and indexes — it never invents a consequence.
 *
 * <p>Coverage is expected to be sparse and is reported honestly. Most pins have no scenario, and
 * that is a fact about how much history has been written down, not a defect.
 */
public final class ScenarioRegistry {

    private final Map<String, ScenarioDefinition> byPin = new LinkedHashMap<>();
    private final Map<String, ScenarioDefinition> byId = new LinkedHashMap<>();
    private final List<String> loadErrors = new ArrayList<>();

    /** Load every {@code *.json} scenario in a directory. Missing directory is not an error. */
    public int loadFromDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0;
        }
        int before = byId.size();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                 .sorted()
                 .forEach(p -> {
                     try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                         parseInto(JsonParser.parseReader(r), p.getFileName().toString());
                     } catch (Exception e) {
                         loadErrors.add(p.getFileName() + ": " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            loadErrors.add("directory " + dir + ": " + e.getMessage());
        }
        return byId.size() - before;
    }

    /** Parse one scenario JSON document into the registry. Exposed for tests and callers. */
    public void parseInto(JsonElement root, String origin) {
        if (root == null || root.isJsonNull()) {
            loadErrors.add(origin + ": empty document");
            return;
        }
        if (root.isJsonArray()) {
            JsonArray arr = root.getAsJsonArray();
            for (JsonElement el : arr) {
                parseSingle(el, origin);
            }
            return;
        }
        parseSingle(root, origin);
    }

    private void parseSingle(JsonElement element, String origin) {
        if (element == null || !element.isJsonObject()) {
            loadErrors.add(origin + ": expected an object");
            return;
        }
        JsonObject o = element.getAsJsonObject();

        String id = str(o, "scenarioId", "");
        String pin = str(o, "triggerPin", "");
        if (id.isBlank() || pin.isBlank()) {
            loadErrors.add(origin + ": requires scenarioId and triggerPin");
            return;
        }

        int triggerYear = intOr(o, "triggerYear", 0);
        List<ScenarioEvent> events = new ArrayList<>();

        if (o.has("events") && o.get("events").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("events")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject e = el.getAsJsonObject();

                // Absolute "year" wins; otherwise "offset" is measured from the trigger year.
                int year = e.has("year")
                        ? intOr(e, "year", triggerYear)
                        : triggerYear + intOr(e, "offset", 0);

                events.add(new ScenarioEvent(
                        year,
                        parseForm(str(e, "form", "NEW")),
                        str(e, "name", ""),
                        str(e, "description", ""),
                        Provenance.parse(str(e, "provenance", "")),
                        str(e, "source", ""),
                        stringMap(e, "factionChanges")));
            }
        }

        ScenarioDefinition def = new ScenarioDefinition(
                id, pin, triggerYear,
                str(o, "title", ""),
                str(o, "tagline", ""),
                str(o, "author", ""),
                Provenance.parse(str(o, "provenance", "authored")),
                intOr(o, "authoredUntilYear", 0),
                str(o, "epilogue", ""),
                events);

        if (!def.isUsable()) {
            loadErrors.add(origin + ": scenario " + id + " is not usable");
            return;
        }
        byId.put(def.scenarioId(), def);
        byPin.put(def.triggerPin(), def);
    }

    private static AlternativeEvent.EventForm parseForm(String raw) {
        if (raw == null || raw.isBlank()) {
            return AlternativeEvent.EventForm.NEW;
        }
        try {
            return AlternativeEvent.EventForm.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AlternativeEvent.EventForm.NEW;
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Map<String, String> stringMap(JsonObject o, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        if (o == null || !o.has(key) || !o.get(key).isJsonObject()) {
            return out;
        }
        JsonObject m = o.getAsJsonObject(key);
        for (Map.Entry<String, JsonElement> e : m.entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().getAsString());
            } catch (Exception ignored) {
                // A non-scalar change is dropped rather than crashing the load.
            }
        }
        return out;
    }

    public Optional<ScenarioDefinition> forPin(String pinId) {
        return Optional.ofNullable(byPin.get(pinId));
    }

    public Optional<ScenarioDefinition> byId(String scenarioId) {
        return Optional.ofNullable(byId.get(scenarioId));
    }

    public boolean hasScenarioFor(String pinId) {
        return byPin.containsKey(pinId);
    }

    public int size() {
        return byId.size();
    }

    public List<String> loadErrors() {
        return Collections.unmodifiableList(loadErrors);
    }

    public Map<String, ScenarioDefinition> all() {
        return Collections.unmodifiableMap(byId);
    }

    /** Pins that have a written story — the ones the game can narrate rather than merely model. */
    public java.util.Set<String> authoredPins() {
        return Collections.unmodifiableSet(byPin.keySet());
    }

    /** Coverage report: how much of the world has a written story. */
    public String coverageReport(int totalPins) {
        int covered = byId.size();
        double pct = totalPins <= 0 ? 0.0d : (100.0d * covered / totalPins);
        return "Scenarios loaded: " + covered
                + (totalPins > 0 ? String.format(" (%.2f%% of %d known pins)", pct, totalPins) : "")
                + (loadErrors.isEmpty() ? "" : " | " + loadErrors.size() + " load error(s)");
    }
}

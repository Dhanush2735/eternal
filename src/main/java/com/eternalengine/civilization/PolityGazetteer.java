package com.eternalengine.civilization;

import com.eternalengine.EternalEngineMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The second polity register: polities the primary source does not hold.
 *
 * <h3>Why (2026-10-24, requirement 2, step R2-a2)</h3>
 * The ruler register is built from one source, Cliopatria v0.2.1. Eighteen site hints name polities that
 * source does not carry — Mewar, Marwar, Travancore, the Holkars, Dogra Kashmir and the rest — so
 * {@link CivHints} had to list them as <b>declared absent</b>, and a person at such a site had no state
 * to belong to. They are not obscure: they are among the best documented states of South Asia.
 *
 * <p>This register closes them from a second source. One row each: the name, the years it stood, the
 * centre of its territory, and <b>how confident the row is</b> — {@code recorded} when a source states
 * the span, {@code estimate} when sources disagree or the span is conventional. An estimate says so; it
 * never borrows the certainty of a recorded row. That is the project's turn-6 rule applied to a polity.
 *
 * <h3>What it is not</h3>
 * It does not make these polities simulated civilizations. It answers three questions — name, years,
 * centre — exactly as {@link PolityFacts} does for the primary register's polities, and nothing ticks.
 */
public final class PolityGazetteer {

    /** One row of the second register. */
    public record Entry(String id, String name, int startYear, int endYear, double lat, double lon,
                        String capital, boolean recorded, String note) {
    }

    /** Where the shipped rows live. */
    public static final String RESOURCE =
            "/data/eternalengine/eternal/civilizations/polities_second_register.json";

    private static volatile Map<String, Entry> entries;

    private PolityGazetteer() {
    }

    /** Every row, keyed by polity id. Empty when the pack could not be read. */
    public static Map<String, Entry> all() {
        Map<String, Entry> local = entries;
        if (local != null) {
            return local;
        }
        synchronized (PolityGazetteer.class) {
            if (entries == null) {
                entries = load();
            }
            return entries;
        }
    }

    /** The row for a polity id, or {@code null}. */
    public static Entry get(String civId) {
        if (civId == null || civId.isBlank()) {
            return null;
        }
        return all().get(RulerRegister.civHintFor(civId));
    }

    public static String name(String civId) {
        Entry entry = get(civId);
        return entry == null ? null : entry.name();
    }

    public static int[] span(String civId) {
        Entry entry = get(civId);
        return entry == null ? null : new int[] {entry.startYear(), entry.endYear()};
    }

    public static double[] centre(String civId) {
        Entry entry = get(civId);
        return entry == null ? null : new double[] {entry.lat(), entry.lon()};
    }

    /** How many rows are recorded facts rather than estimates. */
    public static int recordedCount() {
        return (int) all().values().stream().filter(Entry::recorded).count();
    }

    public static int size() {
        return all().size();
    }

    /**
     * True when this id is covered by the second register. Used by the vocabulary check to tell a
     * polity the second source closes apart from one that is simply absent everywhere.
     */
    public static boolean covers(String civId) {
        return get(civId) != null;
    }

    private static Map<String, Entry> load() {
        Map<String, Entry> out = new LinkedHashMap<>();
        try (InputStream in = PolityGazetteer.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log("the second register is not on the classpath (" + RESOURCE + ")");
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                JsonArray rows = root.getAsJsonObject().getAsJsonArray("polities");
                for (JsonElement element : rows) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject row = element.getAsJsonObject();
                    String id = str(row, "id");
                    String name = str(row, "name");
                    if (id == null || name == null) {
                        continue;
                    }
                    out.put(RulerRegister.civHintFor(id), new Entry(
                            id, name,
                            (int) num(row, "startYear"), (int) num(row, "endYear"),
                            num(row, "lat"), num(row, "lon"),
                            str(row, "capital"),
                            "recorded".equalsIgnoreCase(str(row, "confidence")),
                            str(row, "note")));
                }
            }
        } catch (Exception e) {
            log("the second register could not be read: " + e.getMessage());
            return Map.of();
        }
        log("second register: " + out.size() + " polities (" + recordedCount(out)
                + " recorded, " + (out.size() - recordedCount(out)) + " estimates)");
        return Map.copyOf(out);
    }

    private static int recordedCount(Map<String, Entry> map) {
        int n = 0;
        for (Entry entry : map.values()) {
            if (entry.recorded()) {
                n++;
            }
        }
        return n;
    }

    private static String str(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : null;
    }

    private static double num(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsDouble() : 0.0d;
    }

    private static void log(String message) {
        try {
            EternalEngineMod.getLogger().info("[PolityGazetteer] " + message);
        } catch (RuntimeException ignored) {
            // logging must never be the reason a register fails to load
        }
    }
}

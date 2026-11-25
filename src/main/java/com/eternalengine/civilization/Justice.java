package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 42 (F3, law and crime by era): <b>who keeps the peace here, who judges here, and what is
 * done to a man here</b>.
 *
 * <p>F3 names three things - guards, courts and punishment - and they are always asked together,
 * so this register holds all three in one pack, keyed exactly the way {@code SocialOrder} and
 * {@code CultureAtlas} are keyed: {@code CountryGrid.codeAt(lat, lon)} gives a country, and the row
 * is the one whose {@code where} holds it, whose years hold the year, and which has the highest
 * {@code priority}.
 *
 * <h3>What was there before</h3>
 *
 * <p>Nothing. {@code RoyalCourtManager} is a <b>throne room</b> - a ruler, a bodyguard and a
 * chancellor standing in a room - and not a court of law. There is no guard, no watch, no
 * magistrate, no punishment and no crime anywhere in the mod: a search for all of them finds the
 * law register written three parts ago and nothing else.
 *
 * <h3>The one question this pack does not answer</h3>
 *
 * <p>Every row here is <b>a reading</b> of the reference work named on it, and every row is
 * estimated - no source states, for a given place and century, that the peace was kept by a kindred
 * or by a paid watch.
 *
 * <p><b>Except whether a place still puts men to death.</b> That is not read from this table at
 * all. It comes from the C18 law pack through {@link LawRegister}, which carries real abolition
 * dates for real polities: all 121 capital-punishment rows in that pack are dated abolitions.
 * {@link #punishmentsHere(double, double, int)} asks it, so *a judicial death* simply stops being
 * an option in a country the year that country abolished it, and this table does not have to be
 * right about it. That is the whole reason F3 was scheduled after C18.
 *
 * <h3>Punishment is a repertoire, not a choice</h3>
 *
 * <p>{@link #punishments} returns <b>every</b> punishment available in a place and year, strongest
 * first, and not the single best one. A blood-price, a flogging, a fine and a hanging all sat side
 * by side for most of history, and what was done to a man depended on the crime and on who he was.
 * Picking one answer silently deleted the other five - and it killed the fallback row outright,
 * because two of the rows cover every country from before history.
 */
public final class Justice {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/justice.json.gz";

    /**
     * One row of any of the three tables.
     *
     * @param note a limit or a caveat this row wants to say about itself, or null
     * @param death true only for a judicial killing, which the law register gates
     */
    public record Row(String id, String name, String who, String line, String source, String note,
                      int start, Integer end, int priority, boolean death) {

        /** "the hundred and the shire: the tithing, the constable, the sheriff". */
        public String head() {
            return name + ": " + who;
        }

        /** The line, with the work it was read from where there is one. */
        public String line() {
            return source == null || source.isBlank() ? line
                    : line + " [read from " + source + "]";
        }
    }

    /** One answer from each of the three tables, at a place and year. */
    public record Here(Row guards, Row courts, List<Row> punishments, boolean deathPenalty,
                       Integer abolishedIn) {

        /** The whole thing as one paragraph, in the order a man would meet it. */
        public String line() {
            StringBuilder sb = new StringBuilder();
            sb.append("The peace is kept by ").append(guards.name()).append(". ");
            sb.append(guards.line()).append(' ');
            sb.append("You are judged by ").append(courts.name()).append(". ");
            sb.append(courts.line()).append(' ');
            sb.append("And they can ").append(String.join(", ",
                    punishments.stream().map(Row::name).toList())).append('.');
            if (abolishedIn != null) {
                sb.append(" This country gave up hanging in ").append(abolishedIn).append('.');
            }
            return sb.toString();
        }
    }

    private static final Justice INSTANCE = new Justice();

    /** table name -> (country -> rows, highest priority first) */
    private final Map<String, Map<String, List<Row>>> byCountry = new LinkedHashMap<>();
    private final Map<String, Row> defaults = new HashMap<>();
    private final List<String> gaps = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private int rowCount;
    private boolean loaded;
    private String source = "missing";

    private Justice() {}

    public static Justice get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public String source() {
        ensureLoaded();
        return source;
    }

    public int rowCount() {
        ensureLoaded();
        return rowCount;
    }

    public Map<String, Integer> counts() {
        ensureLoaded();
        return Collections.unmodifiableMap(counts);
    }

    public List<String> gaps() {
        ensureLoaded();
        return Collections.unmodifiableList(gaps);
    }

    public boolean hasData() {
        ensureLoaded();
        return rowCount > 0;
    }

    // ------------------------------------------------------------------ the questions

    /** Who keeps the peace in this country in this year. */
    public Row guards(String countryCode, int year) {
        return one("guards", countryCode, year);
    }

    /** Who judges in this country in this year. */
    public Row courts(String countryCode, int year) {
        return one("courts", countryCode, year);
    }

    /**
     * Every punishment available in this country in this year, strongest first.
     *
     * <p>Not gated by the law register: this is the repertoire as the table has it. Use
     * {@link #punishmentsHere(double, double, int)} for what is actually still lawful.
     */
    public List<Row> punishments(String countryCode, int year) {
        ensureLoaded();
        Map<String, List<Row>> table = byCountry.get("punishments");
        List<Row> held = table == null ? null : table.get(countryCode);
        List<Row> out = new ArrayList<>();
        if (held != null) {
            for (Row r : held) {
                if (r.start() <= year && (r.end() == null || year < r.end())) {
                    out.add(r);
                }
            }
        }
        return out.isEmpty() ? List.of(defaults.get("punishments")) : out;
    }

    /** Who keeps the peace at this point in this year. */
    public Row guardsAt(double lat, double lon, int year) {
        return guards(CountryGrid.get().codeAt(lat, lon), year);
    }

    /** Who judges at this point in this year. */
    public Row courtsAt(double lat, double lon, int year) {
        return courts(CountryGrid.get().codeAt(lat, lon), year);
    }

    /**
     * What can lawfully be done to a man at this point in this year.
     *
     * <p>This is the repertoire with the one hard fact applied: if the state ruling here has
     * abolished capital punishment by this year, *a judicial death* is not in the list, and the
     * year it went is carried in the answer so the register can say why.
     */
    public List<Row> punishmentsHere(double lat, double lon, int year) {
        String code = CountryGrid.get().codeAt(lat, lon);
        Integer gone = LawRegister.get().abolitionYear(
                com.eternalengine.civilization.WhoRuled.hit(lat, lon, year).map(RulerRegister.Hit::wikidata)
                        .orElse(null), year);
        List<Row> out = new ArrayList<>();
        for (Row r : punishments(code, year)) {
            if (r.death() && gone != null) {
                continue;                          // abolished here: the table has no say
            }
            out.add(r);
        }
        return out;
    }

    /** All three answers at once, which is how they are usually wanted. */
    public Here hereAt(double lat, double lon, int year) {
        String code = CountryGrid.get().codeAt(lat, lon);
        Integer gone = LawRegister.get().abolitionYear(
                com.eternalengine.civilization.WhoRuled.hit(lat, lon, year).map(RulerRegister.Hit::wikidata)
                        .orElse(null), year);
        return new Here(guards(code, year), courts(code, year),
                punishmentsHere(lat, lon, year), gone == null, gone);
    }

    /** Whether a man can still be put to death at this point in this year. */
    public boolean deathPenaltyAt(double lat, double lon, int year) {
        return punishmentsHere(lat, lon, year).stream().anyMatch(Row::death);
    }

    // ------------------------------------------------------------------ the file

    private Row one(String table, String countryCode, int year) {
        ensureLoaded();
        Map<String, List<Row>> rows = byCountry.get(table);
        List<Row> held = rows == null ? null : rows.get(countryCode);
        if (held != null) {
            for (Row r : held) {
                if (r.start() <= year && (r.end() == null || year < r.end())) {
                    return r;
                }
            }
        }
        return defaults.get(table);
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = Justice.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            JsonObject doc;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                doc = JsonParser.parseReader(r).getAsJsonObject();
            }
            install(doc);
            source = RESOURCE;
        } catch (IOException | IllegalStateException | NullPointerException e) {
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
        }
    }

    private void install(JsonObject doc) {
        JsonObject c = doc.getAsJsonObject("counts");
        for (Map.Entry<String, JsonElement> e : c.entrySet()) {
            counts.put(e.getKey(), e.getValue().getAsInt());
        }
        for (JsonElement e : arr(doc, "gaps")) {
            gaps.add(e.getAsString());
        }
        for (String table : new String[] {"guards", "courts", "punishments"}) {
            Map<String, List<Row>> table0 = new LinkedHashMap<>();
            JsonArray rows = arr(doc, table);
            for (JsonElement e : rows) {
                Row r = row(e.getAsJsonObject());
                rowCount++;
                for (String code : codes(e.getAsJsonObject())) {
                    table0.computeIfAbsent(code, k -> new ArrayList<>()).add(r);
                }
            }
            // Highest priority first, so `one()` can simply take the first match.
            for (List<Row> list : table0.values()) {
                list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
            }
            byCountry.put(table, table0);
            JsonElement d = doc.get("default_" + table);
            if (d != null && d.isJsonObject()) {
                defaults.put(table, row(d.getAsJsonObject()));
            }
        }
    }

    private static List<String> codes(JsonObject o) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr(o, "where")) {
            out.add(e.getAsString());
        }
        return out;
    }

    private static Row row(JsonObject o) {
        return new Row(
                str(o, "id"), str(o, "name"), str(o, "who"), str(o, "line"),
                str(o, "source"), str(o, "note"),
                o.has("start") && !o.get("start").isJsonNull() ? o.get("start").getAsInt() : -10000,
                o.has("end") && !o.get("end").isJsonNull() ? o.get("end").getAsInt() : null,
                o.has("priority") ? o.get("priority").getAsInt() : 0,
                o.has("death") && o.get("death").getAsBoolean());
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonArray() ? new JsonArray() : e.getAsJsonArray();
    }

    @Override
    public String toString() {
        ensureLoaded();
        return String.format(Locale.ROOT, "Justice[%d rows: %d guards, %d courts, %d punishments, %s]",
                rowCount, counts.getOrDefault("guards", 0), counts.getOrDefault("courts", 0),
                counts.getOrDefault("punishments", 0), source);
    }
}

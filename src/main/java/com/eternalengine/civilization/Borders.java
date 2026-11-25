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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Step 42 (F8, borders and permits): <b>what the crossing demands of you</b>, and where the line
 * you are about to cross actually runs.
 *
 * <h3>Two halves, and only one of them is data</h3>
 *
 * <p>The first half is the pack: twelve kinds of crossing, keyed the way every other step-42 pack
 * is keyed - {@code CountryGrid.codeAt(lat, lon)} gives a country, and the row is the one whose
 * {@code where} holds it, whose years hold the year, and which has the highest {@code priority}.
 *
 * <p>The second half is <b>not in the pack at all</b>, and this is the point of the design. Where a
 * border runs is derived at runtime from {@link RulerRegister}: if the place a day's walk away is
 * ruled by somebody else, there is a line between. The step-42 survey said F8 needed a neighbouring
 * state, and the temptation was to build a new adjacency graph of the world. That would have been a
 * second opinion that could quietly disagree with the first, so instead {@link #borderBetween}
 * asks the ruler register, which already is a state graph - 9,322 timelines over 1,581 polities -
 * and cannot disagree with itself.
 *
 * <h3>A crossing is not one thing</h3>
 *
 * <p>A row says what the crossing <i>is</i> ({@code what}), what it <i>takes</i> ({@code demand}),
 * and carries up to five marks:
 *
 * <ul>
 *   <li>{@code closed} - you are not getting through at all</li>
 *   <li>{@code papers} - it wants to know who you are, not merely what you carry</li>
 *   <li>{@code quarantine} - it holds you for as long as the plague takes to show</li>
 *   <li>{@code sea} - it stands at a port, and only meets you if you arrive by water</li>
 *   <li>{@code internal} - it is a toll <i>inside</i> one state, not a line between two</li>
 * </ul>
 *
 * <p>The last two are why the pack works. Before they existed the priority ordering had to choose
 * between a lazaretto and a turnpike, and it chose wrong everywhere: from 1377 to 1860 every
 * Englishman who crossed anything was told he was being quarantined. A man riding north through
 * England in 1800 met turnpike gates; a ship putting in at Liverpool met a customs house; a ship
 * putting in at Venice met forty days in a walled yard. Same year, three answers, and the only
 * thing that separates them is how you arrived.
 *
 * <h3>Every row is a reading</h3>
 *
 * <p>There is no source that states, as a fact, that the frontier of a given place in a given
 * century was a gate where a gift was expected. Every row is estimated and says so. The <i>dates</i>
 * on a handful of rows are not readings, though: they were read out of the articles themselves -
 * 1377 for the first quarantine at Ragusa, 1914 and 1920 for the passport, 1834 for the Zollverein,
 * 1633 to 1853 for sakoku, 1757 to 1842 for Canton - and the builder checks each one.
 */
public final class Borders {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/border.json.gz";

    /**
     * One kind of crossing.
     *
     * @param end    the first year this row does <i>not</i> apply, or null for "still going"
     * @param sea    true if it stands at a port and only meets a traveller arriving by water
     * @param internal true if it is a toll inside one state rather than a line between two
     */
    public record Row(String id, String name, String what, String demand, String line,
                      String source, String note, int start, Integer end, int priority,
                      boolean papers, boolean quarantine, boolean closed, boolean sea,
                      boolean internal) {

        /** "the octroi" / "a toll on everything you carry in to sell". */
        public String head() {
            return name + ": " + demand;
        }

        /** The line, with the work it was read from where there is one. */
        public String line() {
            return source == null || source.isBlank() ? line
                    : line + " [read from " + source + "]";
        }

        /** True if this crossing would stop this traveller at all. */
        public boolean stopsYou() {
            return !"open_ground".equals(id);
        }
    }

    /**
     * A line between two rulers, found by walking the ground and asking who rules it.
     *
     * @param from      who rules the near side
     * @param to        who rules the far side
     * @param lat, lon  where the line falls, to within about the sampling distance
     * @param km        how far along the leg the line is
     */
    public record Border(String from, String to, double lat, double lon, double km, int year) {

        /** "the Kingdom of France gives way to the Holy Roman Empire". */
        public String line() {
            return from + " gives way to " + to;
        }
    }

    /**
     * What one step costs you.
     *
     * @param border  the line crossed, or null if both ends of the step are in the same hands
     * @param post    what stands on that line, or the kind of crossing this country has anyway
     * @param bySea   whether the traveller arrived by water
     */
    public record Cross(Border border, Row post, boolean bySea) {

        /** True if this step crosses from one ruler's ground into another's. */
        public boolean isBorder() {
            return border != null;
        }

        /** True if you are turned back outright. */
        public boolean refused() {
            return post.closed();
        }

        /** The whole thing as one paragraph, in the order a man would meet it. */
        public String line() {
            StringBuilder sb = new StringBuilder();
            if (isBorder()) {
                sb.append(border.line()).append(". ");
            }
            if (!post.stopsYou()) {
                sb.append(isBorder()
                        ? "There is a line here, and nothing on it. "
                        : "There is nothing here to stop you. ");
                sb.append(post.line());
                return sb.toString();
            }
            sb.append("There is ").append(post.name()).append(" here: ").append(post.what())
              .append(". ").append(post.line());
            if (post.closed()) {
                sb.append(" You will not be allowed through.");
            } else if (post.quarantine()) {
                sb.append(" And they hold you.");
            } else if (post.papers()) {
                sb.append(" And they want to see who you are.");
            }
            return sb.toString();
        }
    }

    private static final Borders INSTANCE = new Borders();

    /** country code -> rows, highest priority first */
    private final Map<String, List<Row>> byCountry = new LinkedHashMap<>();
    private Row fallback;
    private final List<String> gaps = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private int rowCount;
    private boolean loaded;
    private String source = "missing";

    /** How many times {@link #borderBetween} samples the ground before it bisects. */
    private static final int SAMPLES = 24;
    /** How many times it bisects after that. 12 halvings of a 24-way scan is well under a km. */
    private static final int BISECTIONS = 12;
    /** Two rulers this close together are not a border, they are the same place twice. */
    private static final double MIN_BORDER_KM = 5.0d;

    private Borders() {}

    public static Borders get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    // ------------------------------------------------------------------ loading

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream raw = Borders.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return;
            }
            try (GZIPInputStream gz = new GZIPInputStream(raw);
                 InputStreamReader r = new InputStreamReader(gz, StandardCharsets.UTF_8)) {
                read(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            source = "unreadable: " + e.getMessage();
        }
    }

    private void read(JsonObject root) {
        if (root.has("source")) {
            source = root.get("source").getAsString();
        }
        JsonArray rows = root.getAsJsonArray("crossings");
        if (rows == null) {
            source = "no crossings table";
            return;
        }
        for (JsonElement el : rows) {
            JsonObject o = el.getAsJsonObject();
            Row row = new Row(
                    str(o, "id"), str(o, "name"), str(o, "what"), str(o, "demand"),
                    str(o, "line"), str(o, "source"), str(o, "note"),
                    intOr(o, "start", Integer.MIN_VALUE),
                    o.has("end") && !o.get("end").isJsonNull() ? o.get("end").getAsInt() : null,
                    intOr(o, "priority", 0),
                    bool(o, "papers"), bool(o, "quarantine"), bool(o, "closed"),
                    bool(o, "sea"), bool(o, "internal"));
            for (JsonElement c : o.getAsJsonArray("where")) {
                byCountry.computeIfAbsent(c.getAsString().toUpperCase(Locale.ROOT),
                        k -> new ArrayList<>()).add(row);
            }
            rowCount++;
        }
        for (List<Row> list : byCountry.values()) {
            list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        }
        JsonObject def = root.getAsJsonObject("default_crossings");
        fallback = def == null ? null : new Row(
                str(def, "id"), str(def, "name"), str(def, "what"), str(def, "demand"),
                str(def, "line"), str(def, "source"), str(def, "note"),
                intOr(def, "start", Integer.MIN_VALUE),
                null, -1, false, false, false, false, false);
        if (root.has("gaps")) {
            for (JsonElement g : root.getAsJsonArray("gaps")) {
                gaps.add(g.getAsString());
            }
        }
        if (root.has("counts")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("counts").entrySet()) {
                if (!e.getValue().isJsonNull()) {
                    counts.put(e.getKey(), e.getValue().getAsInt());
                }
            }
        }
    }

    /** A JSON null is not an int, and `getAsInt` on one throws - the default row has `start: null`. */
    private static int intOr(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsInt();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    // ------------------------------------------------------------------ what a crossing is

    /**
     * The kind of crossing this country had in this year.
     *
     * @param bySea true if the traveller comes off a ship; see the class note for why it matters
     */
    public Row post(String countryCode, int year, boolean bySea) {
        ensureLoaded();
        if (countryCode == null || countryCode.isBlank()) {
            return fallback();
        }
        List<Row> rows = byCountry.get(countryCode.toUpperCase(Locale.ROOT));
        if (rows == null) {
            return fallback();
        }
        for (Row r : rows) {
            if (year < r.start()) {
                continue;
            }
            if (r.end() != null && year >= r.end()) {
                continue;
            }
            if (bySea && r.internal()) {
                continue;
            }
            if (!bySea && r.sea()) {
                continue;
            }
            return r;
        }
        return fallback();
    }

    /** The kind of crossing at a place, in a year. */
    public Row postAt(double lat, double lon, int year, boolean bySea) {
        return post(CountryGrid.get().codeAt(lat, lon), year, bySea);
    }

    /** The row that answers when no other does. Never null once the pack is read. */
    public Row fallback() {
        ensureLoaded();
        if (fallback != null) {
            return fallback;
        }
        return new Row("open_ground", "open ground", "no line, and nobody keeping it", "nothing",
                "there is no border here", "Freedom of movement", null, Integer.MIN_VALUE, null,
                -1, false, false, false, false, false);
    }

    // ------------------------------------------------------------------ where the line runs

    /**
     * Walk from one place to another and say where the ruler changes, or empty if it never does.
     *
     * <p>This is derived, not stored. The pack says what a crossing is <i>like</i>; this says
     * whether you are at one. It samples {@value #SAMPLES} points along the leg, finds the first
     * where the ruler differs from the one you started under, then bisects between the last place
     * it did not and the first place it did.
     */
    public Optional<Border> borderBetween(double fromLat, double fromLon, double toLat,
                                          double toLon, int year) {
        ensureLoaded();
        double totalKm = RulerRegister.distanceKm(fromLat, fromLon, toLat, toLon);
        if (totalKm < MIN_BORDER_KM) {
            return Optional.empty();
        }
        String here = rulerAt(fromLat, fromLon, year);
        String there = rulerAt(toLat, toLon, year);
        if (here == null || there == null || here.equals(there)) {
            return Optional.empty();
        }
        // A coarse scan first. Bisection alone assumes the handover is monotonic, and real
        // frontiers are not: an enclave or a salient changes hands twice along one leg.
        double lo = 0.0d;
        double hi = 1.0d;
        boolean found = false;
        for (int i = 1; i <= SAMPLES && !found; i++) {
            double t = (double) i / SAMPLES;
            String at = rulerAt(lerp(fromLat, toLat, t), lerpLon(fromLon, toLon, t), year);
            if (at == null || !at.equals(here)) {
                hi = t;
                lo = (double) (i - 1) / SAMPLES;
                found = true;
            }
        }
        if (!found) {
            return Optional.empty();
        }
        for (int i = 0; i < BISECTIONS; i++) {
            double mid = (lo + hi) / 2.0d;
            String at = rulerAt(lerp(fromLat, toLat, mid), lerpLon(fromLon, toLon, mid), year);
            if (at != null && at.equals(here)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double t = hi;
        // Not `there`. The far end of a long leg is not who you meet on the other side of the
        // line: a man walking from Rome to Krakow in 1500 leaves the Papal States somewhere in
        // Umbria, and the Jagiellons are a thousand miles further on. Ask who rules a step past.
        double past = Math.min(1.0d, t + (1.0d / SAMPLES) / 8.0d);
        String over = rulerAt(lerp(fromLat, toLat, past), lerpLon(fromLon, toLon, past), year);
        return Optional.of(new Border(here, over == null ? there : over,
                lerp(fromLat, toLat, t), lerpLon(fromLon, toLon, t), totalKm * t, year));
    }

    /**
     * What one step costs you: whether it crosses a line, and what stands on that line.
     */
    public Cross crossing(double fromLat, double fromLon, double toLat, double toLon, int year,
                          boolean bySea) {
        Optional<Border> b = borderBetween(fromLat, fromLon, toLat, toLon, year);
        // The post is looked up at the DESTINATION, not at the line. A line drawn across open
        // water has no country under it - `CountryGrid.codeAt` of the Channel is sea - so
        // Shanghai to Edo in 1800 fell through to "nothing here to stop you" when what stands at
        // the far end of that voyage is the edict that shut the whole country. It is also simply
        // truer: the crossing you are made to satisfy belongs to the state you are entering.
        return new Cross(b.orElse(null), postAt(toLat, toLon, year, bySea), bySea);
    }

    /** True if this step crosses from one ruler's ground into another's. */
    public boolean crosses(double fromLat, double fromLon, double toLat, double toLon, int year) {
        return borderBetween(fromLat, fromLon, toLat, toLon, year).isPresent();
    }

    private static String rulerAt(double lat, double lon, int year) {
        return com.eternalengine.civilization.WhoRuled.hit(lat, lon, year).map(RulerRegister.Hit::ruler).orElse(null);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Interpolate longitude the short way round, so a leg across the Pacific is not 300 degrees. */
    private static double lerpLon(double a, double b, double t) {
        double d = b - a;
        while (d > 180.0d) {
            d -= 360.0d;
        }
        while (d < -180.0d) {
            d += 360.0d;
        }
        return a + d * t;
    }

    // ------------------------------------------------------------------ introspection

    public boolean loaded() {
        ensureLoaded();
        return fallback != null && rowCount > 0;
    }

    public int rowCount() {
        ensureLoaded();
        return rowCount;
    }

    public String source() {
        ensureLoaded();
        return source;
    }

    public List<String> gaps() {
        ensureLoaded();
        return Collections.unmodifiableList(gaps);
    }

    public Map<String, Integer> counts() {
        ensureLoaded();
        return Collections.unmodifiableMap(counts);
    }

    /** Every row in the pack, highest priority first - what {@code /ee border list} shows. */
    public List<Row> rows() {
        ensureLoaded();
        Map<String, Row> seen = new LinkedHashMap<>();
        for (List<Row> list : byCountry.values()) {
            for (Row r : list) {
                seen.putIfAbsent(r.id(), r);
            }
        }
        List<Row> out = new ArrayList<>(seen.values());
        out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return out;
    }
}

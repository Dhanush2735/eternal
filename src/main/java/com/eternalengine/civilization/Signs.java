package com.eternalengine.civilization;

import com.eternalengine.money.ProductAtlas;
import com.eternalengine.technology.TechnologyAtlas;
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
import java.util.zip.GZIPInputStream;

/**
 * Step 43 (J3): <b>what the signs at a place are written in, and whether there are any</b>.
 *
 * <p>There was nothing here at all before. Not a stub, not a wrong answer — the word "sign" does
 * not appear anywhere in the mod outside a ship's pennant.
 *
 * <h3>Two questions, two answers</h3>
 *
 * <p><b>1. Is there writing here?</b> Not from this pack. It is the technology atlas's
 * {@code writing} technology, which is dated and per country: Sumer −3027, Egypt −3193,
 * Rome −509, Paris −598, Beijing −877, Yucatán −300 (Maya), Tenochtitlan 1300 (Aztec),
 * <b>Cuzco 1576</b> — the Inca had no writing, and the atlas says so. It also knows when writing
 * was <i>lost</i> ({@code lostAt}: the Indus script after 1900 BC, Greece's dark age).
 *
 * <p><b>2. In what script?</b> Two tiers, and the order is the design:
 *
 * <ul>
 *   <li><b>Tier 1 — measured and specific.</b> Place → ruler → the polity's own languages →
 *       {@code CultureAtlas.scriptOf(language, year)}. Dated and exact: <i>"Middle Chinese in
 *       Chinese characters"</i> at Beijing in 1000, <i>"Persian in the Perso-Arabic script"</i> at
 *       Delhi in 1700. The script table carries year spans, so Turkish is in Arabic letters until
 *       1928 and Tamil is Tamil-Brahmi until 600.</li>
 *   <li><b>Tier 2 — this pack.</b> A hand-written area-and-era table, used only where tier 1 has
 *       no answer.</li>
 * </ul>
 *
 * <h3>Why tier 2 exists, and why tier 1 cannot be used alone</h3>
 *
 * <p>Both were measured, and both numbers matter.
 *
 * <p>Tier 1 is a <i>modern</i> chain: the language of a state today, projected back through its
 * whole span. Against the share of land where writing had arrived:
 *
 * <pre>
 *        year   writing   tier 1   both
 *       -2500       2%       0%     0%
 *        1000      38%      24%    21%
 *        1300      49%      30%    24%
 *        1900     100%      65%    65%
 *        2000     100%      96%    96%
 * </pre>
 *
 * <p>So at AD 1000, 38% of the world could write and only 24% could be given a script. The gap is
 * "we cannot name it", not "nobody was writing". The obvious fix — the nearest living language —
 * measures <b>12%</b> and is worse than nothing: only 18% of land has a major language within
 * 300 km, and just 5% of the 6,741 languages match a script key at all.
 *
 * <p><b>And tier 1 alone is actively wrong in the Andes.</b> Asked about Cuzco in 1400 it answers
 * <i>"Aymara in the Latin alphabet"</i> — a century before any alphabet reached Peru, because
 * {@code CultureAtlas.scriptOf} falls back to "the Latin alphabet" for 55 languages with no year
 * bound. Gating every answer on the writing technology having arrived is what stops that: there,
 * the honest answer is that there is no script, and the officials keep the count on knotted cords.
 *
 * <h3>What is a reading</h3>
 *
 * <p>Whether writing is here is <b>not</b> a reading. Which script it is <b>is</b>, for every tier-2
 * row, and for tier 1 it is only as good as a modern state's language list projected backwards.
 */
public final class Signs {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/scripts.json.gz";

    /** One script in one area for a span of years. {@code end} null means "still so". */
    public record Script(String area, Integer start, Integer end, String name, String what,
                         String line, String source, String note) {

        public String head() {
            return name;
        }

        /** The line, with the work it was read from. */
        public String line() {
            return source == null || source.isBlank() ? line
                    : line + " [read from " + source + "]";
        }
    }

    /**
     * A kind of sign.
     *
     * @param literacy the share of people who would have to read for this sign to be worth putting
     *                 up — a posted notice needs readers, a boundary stone does not
     * @param needs    technologies that must have arrived here first (paper, for a notice)
     */
    public record Sign(String id, String name, String what, String line, String source,
                       Integer start, Integer end, List<String> areas, double literacy,
                       List<String> needs, String note) {

        public String head() {
            return name + " — " + what;
        }
    }

    /**
     * The signs of one place in one year.
     *
     * @param writing       whether writing has reached this place yet
     * @param arrived       the year it reached here, or null if it never did
     * @param script        the script, or null where there is no writing or none can be named
     * @param why           what the script was worked out from
     * @param fromTable     true where the answer came from the area-and-era fallback
     */
    public record Here(double lat, double lon, int year, boolean writing, Integer arrived,
                       String script, String why, boolean fromTable, String ruler,
                       List<String> languages, List<Sign> signs) {

        /** True where there is writing but this mod cannot name the script. A recorded gap. */
        public boolean unnamed() {
            return writing && script == null;
        }

        /** What is carved or hung here, as one paragraph. */
        public String line() {
            if (!writing) {
                return "Nothing is written here: what is known is remembered and told.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(script == null
                    ? "Something is written here, but the mod cannot say in what."
                    : "The signs here are written in " + script + ".");
            // One sentence per sign. The table's lines are written without a full stop so they
            // can be joined, which is why the stop is added here and not there.
            for (Sign s : signs) {
                String line = s.line();
                if (line == null || line.isBlank()) {
                    continue;
                }
                sb.append(" ").append(line.trim());
                if (!line.endsWith(".")) {
                    sb.append(".");
                }
            }
            return sb.toString();
        }
    }

    private static final Signs INSTANCE = new Signs();

    private final List<Script> scripts = new ArrayList<>();
    private final List<Sign> signKinds = new ArrayList<>();
    private final List<String> gaps = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private boolean loaded;
    private String source = "missing";

    private Signs() {}

    public static Signs get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    // ------------------------------------------------------------------ loading

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream raw = Signs.class.getResourceAsStream(RESOURCE)) {
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
        JsonArray ss = root.getAsJsonArray("scripts");
        if (ss != null) {
            for (JsonElement el : ss) {
                JsonObject o = el.getAsJsonObject();
                scripts.add(new Script(
                        str(o, "area"), intOrNull(o, "start"), intOrNull(o, "end"),
                        str(o, "name"), str(o, "what"), str(o, "line"), str(o, "source"),
                        str(o, "note")));
            }
        }
        JsonArray sg = root.getAsJsonArray("signs");
        if (sg != null) {
            for (JsonElement el : sg) {
                JsonObject o = el.getAsJsonObject();
                List<String> areas = new ArrayList<>();
                if (o.has("areas")) {
                    for (JsonElement a : o.getAsJsonArray("areas")) {
                        areas.add(a.getAsString());
                    }
                }
                List<String> needs = new ArrayList<>();
                if (o.has("needs")) {
                    for (JsonElement n : o.getAsJsonArray("needs")) {
                        needs.add(n.getAsString());
                    }
                }
                signKinds.add(new Sign(str(o, "id"), str(o, "name"), str(o, "what"), str(o, "line"),
                        str(o, "source"), intOrNull(o, "start"), intOrNull(o, "end"), areas,
                        o.has("literacy") ? o.get("literacy").getAsDouble() : 0.0d, needs,
                        str(o, "note")));
            }
        }
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

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static Integer intOrNull(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsInt();
    }

    // ------------------------------------------------------------------ the question

    /**
     * The signs of a place in a year.
     *
     * @param rank how big the settlement is, which the literacy figures need
     */
    public Here hereAt(double lat, double lon, int year, SettlementRank rank) {
        ensureLoaded();
        TechnologyAtlas atlas = TechnologyAtlas.get();
        TechnologyAtlas.Tech writing = atlas.tech("writing");
        if (writing == null) {
            return new Here(lat, lon, year, false, null, null, "the atlas has no writing technology",
                    false, null, List.of(), List.of());
        }
        int arrived = atlas.arrivalAt(writing, lat, lon);
        boolean writingHere = arrived < TechnologyAtlas.NEVER && year >= arrived
                && !atlas.lostAt(writing, lat, lon, year);

        String ruler = null;
        List<String> languages = List.of();
        String script = null;
        String why = null;
        boolean fromTable = false;

        if (writingHere) {
            // ---- tier 1: the language of the state ruling here, dated and specific
            var hit = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year);
            if (hit.isPresent()) {
                ruler = hit.get().ruler();
                var facts = LeaderRegister.get().facts(hit.get().wikidata());
                if (facts.isPresent()) {
                    languages = facts.get().languages();
                    for (String l : languages) {
                        String s = CultureAtlas.get().scriptOf(l, year);
                        if (s != null) {
                            script = s;
                            why = l + " is written in " + s;
                            break;
                        }
                    }
                }
            }
            // ---- tier 2: the area and the era, where the ruler's languages say nothing
            if (script == null) {
                String area = ProductAtlas.get().place(lat, lon, year).area();
                for (Script s : scripts) {
                    if (!s.area().equals(area)) {
                        continue;
                    }
                    if (s.start() != null && year < s.start()) {
                        continue;
                    }
                    if (s.end() != null && year >= s.end()) {
                        continue;
                    }
                    script = s.name();
                    why = "this is " + area + " and the year is " + yearOf(year);
                    fromTable = true;
                    break;
                }
            }
        } else {
            why = arrived >= TechnologyAtlas.NEVER
                    ? "writing never reaches this place"
                    : "writing reaches this place in " + yearOf(arrived);
        }

        return new Here(lat, lon, year, writingHere,
                arrived >= TechnologyAtlas.NEVER ? null : arrived,
                script, why, fromTable, ruler, languages, signsHere(lat, lon, year, rank));
    }

    /** The script at a place and year, or null where there is no writing or none can be named. */
    public String scriptAt(double lat, double lon, int year) {
        return hereAt(lat, lon, year, SettlementRank.TOWN).script();
    }

    /** The whole answer as one paragraph. */
    public String lineAt(double lat, double lon, int year, SettlementRank rank) {
        return hereAt(lat, lon, year, rank).line();
    }

    /** Which kinds of sign are standing here, in the order they would be met. */
    private List<Sign> signsHere(double lat, double lon, int year, SettlementRank rank) {
        List<Sign> out = new ArrayList<>();
        String area = ProductAtlas.get().place(lat, lon, year).area();
        double[] lit = CultureAtlas.get().literacy(lat, lon, year, rank);
        double readers = lit == null ? 0.0d : lit[0];
        TechnologyAtlas atlas = TechnologyAtlas.get();
        for (Sign s : signKinds) {
            if (s.start() != null && year < s.start()) {
                continue;
            }
            if (s.end() != null && year >= s.end()) {
                continue;
            }
            if (!s.areas().isEmpty() && !s.areas().contains(area)) {
                continue;
            }
            if (s.literacy() > 0.0d && readers < s.literacy()) {
                continue;   // a notice nobody can read is not put up, and would say so if it were
            }
            boolean ready = true;
            for (String need : s.needs()) {
                TechnologyAtlas.Tech t = atlas.tech(need);
                if (t == null || year < atlas.arrivalAt(t, lat, lon)) {
                    ready = false;
                    break;
                }
            }
            if (!ready) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    /** "1827" for a positive year, "300BC" for a negative one. */
    private static String yearOf(int year) {
        return year < 0 ? (-year) + "BC" : String.valueOf(year);
    }

    // ------------------------------------------------------------------ introspection

    public boolean loaded() {
        ensureLoaded();
        return !scripts.isEmpty();
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

    public List<Script> scripts() {
        ensureLoaded();
        return Collections.unmodifiableList(scripts);
    }

    public List<Sign> signKinds() {
        ensureLoaded();
        return Collections.unmodifiableList(signKinds);
    }

    /** Script rows for one area, earliest first. */
    public List<Script> scriptsIn(String area) {
        ensureLoaded();
        List<Script> out = new ArrayList<>();
        for (Script s : scripts) {
            if (s.area().equals(area)) {
                out.add(s);
            }
        }
        out.sort((a, b) -> Integer.compare(a.start() == null ? -9999 : a.start(),
                b.start() == null ? -9999 : b.start()));
        return out;
    }
}

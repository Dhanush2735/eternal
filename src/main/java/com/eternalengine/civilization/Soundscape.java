package com.eternalengine.civilization;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 43 (J2, sounds of the era): <b>what a place sounds like, and why it sounds that way</b>.
 *
 * <p>What was there before: two things, and only one of them was real. {@code AmbientCrowdSoundManager}
 * is good work from step 17 — it scales a murmur by the <i>simulated</i> population rather than the
 * number of bodies drawn, and it was reworked from once a tick to once a second because it had
 * become the costliest system in {@code /ee performance}. That half is reused here, not rewritten.
 *
 * <p>{@code HistoricalAudioDirector} is the other half, and it is a stub worse than nothing. It
 * picked one of six soundscapes <b>from the year alone</b>:
 * {@code determineSoundscape(double x, double z, int year, boolean nearActiveWar)} takes {@code x}
 * and {@code z} and <b>never reads either of them</b>. So China in 1000 came back
 * <i>Classical Mediterranean</i>, Norway in 1200 came back <i>Medieval Islamic</i>, the Aztecs in
 * 1500 came back <i>Renaissance Europe</i>, and — the one that gives it away — <b>everything after
 * 1920 came back {@code PASTORAL_ANCIENT}</b>, the two loudest centuries there have ever been filed
 * as prehistoric countryside. It plays note-block sounds as stand-ins and prints a chat message
 * describing the soundscape, which is how you can tell it was never wired to anything.
 *
 * <h3>Whether it is there is not a reading</h3>
 *
 * <p>What a sound <i>is</i> is this table's reading: no source states that a given square in a
 * given century was loud with bells, so every row is estimated and says so.
 *
 * <p><b>Whether it is there at all is not a reading.</b> Every row hangs off a register the mod
 * already ships, and one that answers by place:
 *
 * <ul>
 *   <li><b>voice</b> — the faith of the state ruling here.
 *       {@code RulerRegister.rulerAt(lat, lon, year).wikidata()} →
 *       {@code LeaderRegister.facts(qid)}, then the dated survey if there is one (1945 on, with
 *       real percentages) and otherwise the state's faith. Measured: <b>960 polities carry a
 *       religion and 959 of them — 100% — join to the ruler register.</b></li>
 *   <li><b>machine</b> — the technology atlas, per country and dated, so a place hears engines only
 *       once the engine has reached <i>its</i> country (Britain 1712, India 1874).</li>
 *   <li><b>crowd</b> — step 17's own {@code SettlementDensity.intensity}.</li>
 *   <li><b>ground</b> — {@code CountryGrid.nearSea}, which is a real question with a real answer.</li>
 * </ul>
 *
 * <p>So this pack can be wrong about <i>what</i> something sounds like and still be right about
 * <i>that</i> it is there — which is the opposite of the code it replaces.
 *
 * <h3>What it cannot do</h3>
 *
 * <p><b>The mod has no audio assets, and this step cannot record any.</b> What J2 fixes is <i>which</i>
 * sound belongs here and <i>why</i> — the part that was nonsense. What is actually heard is still
 * played with whatever sounds exist, and that is recorded as a limit rather than hidden.
 */
public final class Soundscape {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/soundscape.json.gz";

    /**
     * One sound a place can have.
     *
     * @param kind    ground, crowd, voice or machine
     * @param when    when in the day it happens — "before dawn", "on the hour", "always". A
     *                soundscape is a day and not a list, and this is the field that says so.
     * @param loud    0 (nearly nothing) to 3 (it drowns everything else)
     * @param tech    the technology that must have reached this country before it is heard
     * @param industrial true for the rows that are the Industrial Revolution. A water mill is a
     *                   machine and is not an age: without this the mill, which reaches most of
     *                   Eurasia in antiquity, would make every place industrial at once.
     * @param coast   true for the shore, false for inland, null for either
     * @param faiths  the faith families that raise this voice
     * @param catchAll true for the one row that stands for a faith the mod cannot name
     */
    public record Row(String id, String kind, String name, String what, String when, String line,
                      String source, String note, int loud, String tech, boolean industrial,
                      double minIntensity, Boolean coast, List<String> faiths, boolean catchAll) {

        /** "the church bells — bronze bells in a tower". */
        public String head() {
            return name + " — " + what;
        }

        /** The line, with the work it was read from where there is one. */
        public String line() {
            return source == null || source.isBlank() ? line
                    : line + " [read from " + source + "]";
        }
    }

    /** One sound that is actually sounding here, and why. */
    public record Sound(Row row, String why) {

        /** "the church bells, because this is a Catholic country". */
        public String head() {
            return row.name() + (why == null || why.isBlank() ? "" : ", because " + why);
        }
    }

    /**
     * The soundscape of one place in one year.
     *
     * @param ruler  who rules here, which is what the voice was worked out from
     * @param faiths the faiths of the place and their shares, where the record gives shares
     */
    public record Here(double lat, double lon, int year, String ruler, List<String> faiths,
                       List<Sound> ground, List<Sound> crowd, List<Sound> voice,
                       List<Sound> machine) {

        /** Every sound here, in the order a man would meet it. */
        public List<Sound> all() {
            List<Sound> out = new ArrayList<>(ground);
            out.addAll(crowd);
            out.addAll(voice);
            out.addAll(machine);
            return out;
        }

        /**
         * True once an industrial machine has reached this place — the change J2 is really about.
         * A water mill does not count; see {@link Row#industrial()}.
         */
        public boolean industrial() {
            for (Sound s : machine) {
                if (s.row().industrial()) {
                    return true;
                }
            }
            return false;
        }

        /** The loudest thing here, which is what you would notice first. */
        public Optional<Sound> loudest() {
            Sound best = null;
            for (Sound s : all()) {
                if (best == null || s.row().loud() > best.row().loud()) {
                    best = s;
                }
            }
            return Optional.ofNullable(best);
        }

        /** The whole day as one paragraph. */
        public String line() {
            List<Sound> all = all();
            if (all.isEmpty()) {
                return "Nothing here but the weather.";
            }
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (Sound s : all) {
                sb.append(sep).append(s.row().name()).append(", ").append(s.row().when());
                sep = "; ";
            }
            return sb.toString();
        }
    }

    private static final Soundscape INSTANCE = new Soundscape();

    /**
     * Every row, loudest first. There is no country key here on purpose: a row is a sound, and
     * whether it sounds is worked out at the place, from the ruler's faith and the technology
     * atlas. Filing the same bell under 200 countries would be how the old code ended up
     * answering from the century.
     */
    private final List<Row> rows = new ArrayList<>();
    private Row fallback;
    private final List<String> gaps = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private int rowCount;
    private boolean loaded;
    private String source = "missing";

    private Soundscape() {}

    public static Soundscape get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    // ------------------------------------------------------------------ loading

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream raw = Soundscape.class.getResourceAsStream(RESOURCE)) {
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
        JsonArray table = root.getAsJsonArray("sounds");
        if (table == null) {
            source = "no sounds table";
            return;
        }
        for (JsonElement el : table) {
            JsonObject o = el.getAsJsonObject();
            List<String> faiths = new ArrayList<>();
            if (o.has("faiths")) {
                for (JsonElement f : o.getAsJsonArray("faiths")) {
                    faiths.add(f.getAsString());
                }
            }
            Row row = new Row(
                    str(o, "id"), str(o, "kind"), str(o, "name"), str(o, "what"), str(o, "when"),
                    str(o, "line"), str(o, "source"), str(o, "note"),
                    o.has("loud") ? o.get("loud").getAsInt() : 1,
                    str(o, "tech"),
                    !o.has("industrial") || o.get("industrial").getAsBoolean(),
                    o.has("minIntensity") ? o.get("minIntensity").getAsDouble() : 0.0d,
                    o.has("coast") && !o.get("coast").isJsonNull() ? o.get("coast").getAsBoolean() : null,
                    faiths,
                    o.has("catchAll") && o.get("catchAll").getAsBoolean());
            rows.add(row);
            rowCount++;
        }
        rows.sort((a, b) -> Integer.compare(b.loud(), a.loud()));
        JsonObject def = root.getAsJsonObject("default_sounds");
        if (def != null) {
            fallback = new Row(str(def, "id"), str(def, "kind"), str(def, "name"), str(def, "what"),
                    str(def, "when"), str(def, "line"), str(def, "source"), str(def, "note"),
                    def.has("loud") ? def.get("loud").getAsInt() : 1, null, false, 0.0d, null,
                    List.of(), false);
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

    /** "1827" for a positive year, "300BC" for a negative one. */
    private static String yearOf(int year) {
        return year < 0 ? (-year) + "BC" : String.valueOf(year);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    // ------------------------------------------------------------------ the question

    /**
     * The soundscape of a place in a year.
     *
     * @param rank       how big the settlement is, which the technology atlas needs — a village
     *                   does not have a tram, however early the tram reached its country
     * @param population the simulated population of the place; the crowd row is scaled from it
     */
    public Here hereAt(double lat, double lon, int year, SettlementRank rank, long population) {
        ensureLoaded();
        String code = CountryGrid.get().codeAt(lat, lon);
        SettlementRank r = rank == null ? SettlementRank.VILLAGE : rank;

        List<Sound> ground = new ArrayList<>();
        List<Sound> crowd = new ArrayList<>();
        List<Sound> voice = new ArrayList<>();
        List<Sound> machine = new ArrayList<>();

        // ---- the ground: every place has one, so a place with nobody in it is not a silent place
        for (Row row : rows) {
            if (!"ground".equals(row.kind())) {
                continue;
            }
            if (row.coast() != null && row.coast() != CountryGrid.get().nearSea(lat, lon, 3)) {
                continue;
            }
            ground.add(new Sound(row, row.coast() == null ? "there is always weather"
                    : (row.coast() ? "the shore is here" : "this is inland")));
        }

        // ---- the crowd: step 17 already worked out how busy a place is. Ask it, do not re-derive.
        double intensity = com.eternalengine.population.SettlementDensity.intensity(population, 0);
        for (Row row : rows) {
            if (!"crowd".equals(row.kind())) {
                continue;
            }
            if (intensity < row.minIntensity()) {
                continue;
            }
            crowd.add(new Sound(row, "there are people here"));
        }

        // ---- the voice: what stands here first, then who rules here
        //
        // Holy places are the better instrument, and they were not the obvious one. The ruler's
        // faith comes from a state-level survey of modern countries, so Mecca in 1900 — ruled by
        // the Ottomans, who carry no religion in that survey — came back with no voice at all.
        // The holy-places table is place-based and dated instead: 19,828 sites, each with the
        // years it stood and the faith that kept it. Mecca is a mosque in every year it has had
        // one, whatever the survey says about its government.
        Optional<RulerRegister.Hit> hit = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year);
        String ruler = hit.map(RulerRegister.Hit::ruler).orElse(null);

        Map<String, String> famWhy = new LinkedHashMap<>();
        Set<String> fams = new LinkedHashSet<>();
        for (CultureAtlas.Holy h : CultureAtlas.get().holyPlacesNear(lat, lon, year, 25.0d, 4)) {
            if (h.faith() == null || h.faith().isBlank()) {
                continue;   // 1,100-odd sites carry a kind but no family
            }
            fams.add(h.faith());
            famWhy.putIfAbsent(h.faith(), h.name() + " stands here");
        }
        // The ruler's faith fills in what no recorded holy place reaches, and is the only source
        // that speaks for a countryside with no temple in it.
        for (String f : hit.map(h2 -> faithsHere(h2.wikidata(), year)).orElse(List.of())) {
            for (CultureAtlas.Faith fam : CultureAtlas.get().faithsOf(List.of(f))) {
                if (fams.add(fam.key())) {
                    famWhy.put(fam.key(), "the state is " + fam.word().toLowerCase(Locale.ROOT));
                }
            }
        }

        List<String> faiths = new ArrayList<>(fams);
        for (String fam : fams) {
            for (Row row : rows) {
                if ("voice".equals(row.kind()) && !row.catchAll()
                        && row.faiths() != null && row.faiths().contains(fam)) {
                    voice.add(new Sound(row, famWhy.getOrDefault(fam, "this is its country")));
                }
            }
        }
        // 27 of the 960 polities carry a faith the mod has no family for. Say so rather than
        // leaving the place silent — a silent place is a lie about a place with a temple in it.
        if (!faiths.isEmpty() && voice.isEmpty()) {
            for (Row row : rows) {
                if ("voice".equals(row.kind()) && row.catchAll()) {
                    voice.add(new Sound(row, "there is a faith here the mod cannot name"));
                }
            }
        }

        // ---- the machine: the year the thing reached this country
        //
        // Not its share, and not present(). Two measurements said so: the atlas's share of cars
        // peaks below 0.08 before 2000, so a 15% threshold meant nobody ever heard traffic; and
        // present() draws for infrastructure, so it said the railway was absent from York in 1870
        // at a share of 0.57. arrivalAt() is dated, per country and deterministic: York 1827,
        // Delhi 1864, Tokyo 1872. That is the question being asked.
        TechnologyAtlas atlas = TechnologyAtlas.get();
        for (Row row : rows) {
            if (!"machine".equals(row.kind()) || row.tech() == null) {
                continue;
            }
            TechnologyAtlas.Tech t = atlas.tech(row.tech());
            if (t == null) {
                continue;
            }
            int arrived = atlas.arrivalAt(t, lat, lon);
            if (arrived >= TechnologyAtlas.NEVER || year < arrived) {
                continue;
            }
            machine.add(new Sound(row, t.name + " reached here in " + yearOf(arrived)));
        }

        return new Here(lat, lon, year, ruler, faiths, ground, crowd, voice, machine);
    }

    /** The soundscape of a place, as one paragraph. */
    public String lineAt(double lat, double lon, int year, SettlementRank rank, long population) {
        return hereAt(lat, lon, year, rank, population).line();
    }

    /**
     * The faiths of a state in a year, as names.
     *
     * <p>Where the Correlates of War survey reaches (1945 on) it gives real percentages and is
     * preferred; it is the only dated religion data in the mod. Otherwise {@code Facts.faithIn}
     * answers, which prefers the twelve polities with hand-written dated periods and falls back to
     * the state's listed faith. The {@code rel} list itself has no years, so it is dated by the
     * polity's span in the ruler register — the trick step 42 part 6 used to fix the law pack.
     */
    private static List<String> faithsHere(String qid, int year) {
        if (qid == null) {
            return List.of();
        }
        Optional<LeaderRegister.Facts> facts = LeaderRegister.get().facts(qid);
        if (facts.isEmpty()) {
            return List.of();
        }
        LeaderRegister.Facts f = facts.get();
        Optional<LeaderRegister.ReligionYear> survey = f.religionSharesIn(year);
        if (survey.isPresent()) {
            List<String> out = new ArrayList<>();
            for (LeaderRegister.Share s : survey.get().shares()) {
                if (s.percent() >= 5.0d) {
                    out.add(s.faith());
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return f.faithIn(year).map(List::of).orElse(List.of());
    }

    /** The row that answers when nothing else does. */
    public Row fallback() {
        ensureLoaded();
        return fallback;
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

    /** Every row in the pack, loudest first — what {@code /ee sound list} shows. */
    public List<Row> rows() {
        ensureLoaded();
        return Collections.unmodifiableList(rows);
    }
}

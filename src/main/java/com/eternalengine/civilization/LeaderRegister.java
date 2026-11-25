package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Who ruled a state in a year, and what its people spoke and believed.
 *
 * <p>Keyed by the same Wikidata ids {@link RulerRegister} answers with, so "which state rules
 * this spot" ({@link RulerRegister.Hit#wikidata()}) leads straight to its facts. Built by
 * {@code worldfill/build_polity_facts.py} from each state's own Wikipedia infobox (languages,
 * religion, capital, leaderN/year_leaderN, deputyN/year_deputyN) and from the accession lines on
 * the Wikipedia year pages ("Peter I becomes King of Portugal"), a reign lasting until the next
 * accession to that title, at most 50 years, or the ruler's recorded death.
 *
 * <p>A reign is only claimed inside its years. Between two known reigns the register has no name,
 * and callers say nothing rather than guess one.
 */
public final class LeaderRegister {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/polity_facts.json.gz";

    /**
     * The last year the lists record (they were built in October 2026); later years keep its
     * heads. This is the end of the RECORD, not the end of the world: the world runs to
     * {@link com.eternalengine.clock.Timeline#LAST_YEAR} and the years in between are answered
     * from {@link com.eternalengine.clock.Timeline#RECORDED_UNTIL} and marked projected. It has
     * the same value as {@code Timeline.RECORDED_UNTIL} by definition.
     */
    public static final int LAST_YEAR = com.eternalengine.clock.Timeline.RECORDED_UNTIL;

    /**
     * One reign: first and last year inclusive. {@code estimated} (step 26, the A5 mark) when the list gave no end
     * and the builder guessed one (start + 25 years): never shown as a source, counted by the checks and
     * {@code /ee who}.
     */
    public record Reign(String name, String title, int start, int end, boolean estimated) {
        public Reign(String name, String title, int start, int end) {
            this(name, title, start, end, false);
        }

        public boolean covers(int year) {
            return year >= start && year <= end;
        }

        /** "Aurangzeb (Emperor)", or the bare name when the source gives no title. */
        public String display() {
            return title == null || title.isBlank() ? name : name + " (" + title + ")";
        }
    }

    /** One faith's share of the people, in percent. */
    public record Share(String faith, double percent) {}

    /** The faiths of a state in one survey year (Correlates of War World Religion Data, 1945-2010). */
    public record ReligionYear(int year, List<Share> shares) {}

    /** The main faith of a state in a span of years (step 25: England turned Anglican in 1534, Sweden Lutheran ...). */
    public record FaithPeriod(int start, int end, String faith) {}

    /**
     * What most people followed where the court followed another faith (step 25: Islam at the Mughal court, Hinduism
     * for most people), inside a box of {latMin, latMax, lonMin, lonMax}, or everywhere when the box is null.
     */
    public record FolkFaith(String faith, double[] box) {
        public boolean covers(double lat, double lon) {
            return box == null || (lat >= box[0] && lat <= box[1] && lon >= box[2] && lon <= box[3]);
        }
    }

    /** What the register knows about one state. Lists are never null. */
    public record Facts(String qid, String name, String article, List<String> languages,
                        List<String> religions, String capital, List<Reign> heads, List<Reign> governments,
                        List<ReligionYear> religionYears, List<FaithPeriod> faithPeriods, FolkFaith folkFaith) {

        /** The state's main faith in this year: the period that covers it (step 25), else its first-listed faith. */
        public Optional<String> faithIn(int year) {
            for (FaithPeriod p : faithPeriods) {
                if (year >= p.start() && year <= p.end()) {
                    return Optional.of(p.faith());
                }
            }
            for (String r : religions) {
                if (!r.toLowerCase(java.util.Locale.ROOT).matches("(various|several|many|mixed|official|state).*")) {
                    return Optional.of(r);
                }
            }
            return Optional.empty();
        }

        /** The latest survey at or before this year (from 1945 on; 2010's still answers after 2010). */
        public Optional<ReligionYear> religionSharesIn(int year) {
            ReligionYear best = null;
            for (ReligionYear r : religionYears) {
                if (r.year() <= year && (best == null || r.year() > best.year())) {
                    best = r;
                }
            }
            return Optional.ofNullable(best);
        }

        /**
         * Head of state this year: of the reigns covering it, the most recent accession. Years after the last
         * recorded one ({@link #LAST_YEAR}) keep that year's head, so the land is not left without one in 2027.
         */
        public Optional<Reign> headIn(int year) {
            return latest(heads, Math.min(year, LAST_YEAR));
        }

        /** Head of government (grand vizier, prime minister...) this year, if recorded. */
        public Optional<Reign> governmentIn(int year) {
            return latest(governments, Math.min(year, LAST_YEAR));
        }

        private static Optional<Reign> latest(List<Reign> reigns, int year) {
            Reign best = null;
            for (Reign r : reigns) {
                if (r.covers(year) && (best == null || r.start() > best.start())) {
                    best = r;
                }
            }
            return Optional.ofNullable(best);
        }
    }

    private static final LeaderRegister INSTANCE = new LeaderRegister();

    private boolean loaded;
    private String source = "missing";
    private Map<String, Facts> byQid = Map.of();

    private LeaderRegister() {}

    public static LeaderRegister get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** A register read from an explicit file - for checks and tools, never for the game. */
    public static LeaderRegister fromFile(Path file) {
        LeaderRegister r = new LeaderRegister();
        r.loaded = true;
        try (InputStream in = Files.newInputStream(file)) {
            r.byQid = read(in);
            r.source = file.toString();
        } catch (Exception e) {
            r.source = file + " failed: " + e.getMessage();
        }
        return r;
    }

    public Optional<Facts> facts(String qid) {
        if (qid == null || qid.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byQid.get(qid));
    }

    public int size() {
        return byQid.size();
    }

    /** Every state's facts (read-only), for the runtime checks ({@link HistoryChecks}). */
    public java.util.Collection<Facts> all() {
        return byQid.values();
    }

    /** Where the register was read from, or why it could not be. */
    public String source() {
        return source;
    }

    public String summary() {
        int heads = 0;
        int langs = 0;
        int rels = 0;
        for (Facts f : byQid.values()) {
            heads += f.heads().isEmpty() ? 0 : 1;
            langs += f.languages().isEmpty() ? 0 : 1;
            rels += f.religions().isEmpty() ? 0 : 1;
        }
        return "leader register: " + byQid.size() + " states (" + heads + " with named rulers, " + langs
                + " with languages, " + rels + " with religions) from " + source;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = LeaderRegister.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            byQid = read(in);
            source = RESOURCE;
        } catch (Exception e) {
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
            byQid = Map.of();
        }
    }

    private static Map<String, Facts> read(InputStream raw) throws java.io.IOException {
        Map<String, Facts> out = new HashMap<>();
        try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray polities = root.getAsJsonArray("polities");
            if (polities == null) {
                return Map.of();
            }
            for (JsonElement el : polities) {
                JsonObject o = el.getAsJsonObject();
                String qid = o.get("q").getAsString();
                String name = o.has("n") ? o.get("n").getAsString() : qid;
                out.put(qid, new Facts(qid, name,
                        o.has("a") ? o.get("a").getAsString() : name,
                        strings(o, "lang"), strings(o, "rel"),
                        o.has("cap") ? o.get("cap").getAsString() : "",
                        reigns(o, "head"), reigns(o, "gov"), religionYears(o), faithPeriods(o), folkFaith(o)));
            }
        }
        return Map.copyOf(out);
    }

    private static List<String> strings(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray(key)) {
            out.add(e.getAsString());
        }
        return List.copyOf(out);
    }

    private static List<ReligionYear> religionYears(JsonObject o) {
        if (!o.has("relY") || !o.get("relY").isJsonArray()) {
            return List.of();
        }
        List<ReligionYear> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("relY")) {
            JsonArray row = e.getAsJsonArray();
            List<Share> shares = new ArrayList<>();
            for (JsonElement s : row.get(1).getAsJsonArray()) {
                JsonArray a = s.getAsJsonArray();
                shares.add(new Share(a.get(0).getAsString(), a.get(1).getAsDouble()));
            }
            out.add(new ReligionYear(row.get(0).getAsInt(), List.copyOf(shares)));
        }
        return List.copyOf(out);
    }

    /** "relP": [[start, end, faith], ...] (step 25). */
    private static List<FaithPeriod> faithPeriods(JsonObject o) {
        if (!o.has("relP") || !o.get("relP").isJsonArray()) {
            return List.of();
        }
        List<FaithPeriod> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("relP")) {
            JsonArray a = e.getAsJsonArray();
            out.add(new FaithPeriod(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsString()));
        }
        return List.copyOf(out);
    }

    /** "relFolk": {"faith": ..., "box": [latMin, latMax, lonMin, lonMax]} (step 25; the box is optional). */
    private static FolkFaith folkFaith(JsonObject o) {
        if (!o.has("relFolk") || !o.get("relFolk").isJsonObject()) {
            return null;
        }
        JsonObject f = o.getAsJsonObject("relFolk");
        double[] box = null;
        if (f.has("box") && f.get("box").isJsonArray() && f.getAsJsonArray("box").size() == 4) {
            JsonArray b = f.getAsJsonArray("box");
            box = new double[] {b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                    b.get(3).getAsDouble()};
        }
        return new FolkFaith(f.get("faith").getAsString(), box);
    }

    private static List<Reign> reigns(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return List.of();
        }
        List<Reign> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray(key)) {
            JsonArray a = e.getAsJsonArray();
            if (a.size() < 4 || a.get(2).isJsonNull() || a.get(3).isJsonNull()) {
                continue;
            }
            boolean estimated = a.size() > 4 && !a.get(4).isJsonNull() && a.get(4).getAsInt() == 1;
            out.add(new Reign(a.get(0).getAsString(), a.get(1).isJsonNull() ? "" : a.get(1).getAsString(),
                    a.get(2).getAsInt(), a.get(3).getAsInt(), estimated));
        }
        return List.copyOf(out);
    }
}

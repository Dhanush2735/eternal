package com.eternalengine.story;

import com.eternalengine.timeline.Provenance;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>The join part 2 was missing: a story read out of the packs instead of handed to them.</b>
 *
 * <p>Part 1 recorded the limit, part 2 restated it: {@link BranchStory.Fact} was supplied by a caller, so nothing
 * in the mod could actually read a pack and make a story. This is that reader. It takes the three history packs
 * the mod already ships and turns them into dated facts about one place:
 *
 * <pre>
 *   towns.json.gz             30,151 towns, 3,892 with names by era and 2,582 of those dated, each with a point
 *   buildings_famous.json.gz  23,535 dated buildings, 2,569 of them with an end year
 *   ways_works.json.gz       110,447 works, each with the year it opened and a mark for estimated years
 * </pre>
 *
 * <h2>What it will not do</h2>
 *
 * <p>It invents nothing, and it will not silently promote a guess to a fact. A building date that Wikidata
 * records is {@link Provenance#OBSERVED}; a founding year recorded only to the century is
 * {@link Provenance#STRUCTURAL}, because "in the 1300s" is a bound and not a day; a work's year taken from the
 * <b>technology atlas</b> instead of from Wikidata is {@link Provenance#MODELLED}, because the pack's own note
 * says it is estimated. The story keeps the mark, and {@link BranchStory.Story#asMarkedText()} shows it.
 *
 * <h2>Distance, and why this is a plain scan</h2>
 *
 * <p>A fact counts as near a place within a radius measured in kilometres, using a flat approximation of the
 * degree at that latitude. Over a radius of 25 km the error is metres and it does not matter; what matters is
 * that a story is built when a player asks for one, and not every tick. So buildings and works are scanned
 * linearly, 133,982 rows at worst, and the class says so rather than growing a spatial index it does not need
 * yet. <b>If a later step reads a story for every town in a region, this is the line to come back to.</b>
 */
public final class PlaceRecord {

    /** One stretch of years under one name. {@code from} or {@code to} null means open. {@code to} is exclusive. */
    public record Named(Integer from, Integer to, String name) {

        public boolean holds(int year) {
            return (from == null || from <= year) && (to == null || year < to);
        }

        public boolean dated() {
            return from != null;
        }
    }

    /** A town as the pack holds it: a point, a name today, and the names it has had. */
    public record Town(String qid, String nameNow, double lat, double lon, Integer founded,
                       int foundedPrecision, List<Named> names) {

        /** 9 in the pack's own scale: the founding year is recorded to the year, and not to a decade or century. */
        public boolean foundedToTheYear() {
            return founded != null && foundedPrecision == 9;
        }

        /**
         * The name this place had in a year, from the pack's own dated names. Before the first dated stretch the
         * pack's own rule is the one to keep: the name it has today.
         */
        public String nameIn(int year) {
            Named best = null;
            for (Named n : names) {
                if (n.holds(year)) {
                    best = n;
                }
            }
            return best == null ? nameNow : best.name();
        }

        /** The year the name this place has today came in, where the pack dates it, for a naming line. */
        public Integer nowNameCameIn() {
            for (int i = names.size() - 1; i >= 0; i--) {
                Named n = names.get(i);
                if (n.to() != null) {
                    return n.to();
                }
            }
            return null;
        }

        /** How many of its names the pack does not date, and which therefore cannot be told as fact. */
        public int undatedNames() {
            int n = 0;
            for (Named x : names) {
                if (!x.dated()) n++;
            }
            return n;
        }
    }

    /** A structure with a year it was built or opened, and a year it ended where the pack has one. */
    public record Building(String name, double lat, double lon, String kind, Integer from, Integer to,
                           String source) {
    }

    /** A work: a line, a port, a mill, a dam. {@code estimated} is the pack's own flag. */
    public record Work(double lat, double lon, String kind, Integer from, Integer to, String name,
                       boolean estimated) {
    }

    private static final double KM_PER_DEGREE = 111.32;

    /**
     * <b>The last year the record is a record.</b> Seventeen rows in the two packs carry a later year: two
     * buildings (a bridge dated 2028 and a memorial 2100) and fifteen works between 2027 and 2033, all of them
     * planned or under construction when Wikidata was read. A story told in AD 2900 would otherwise hand a player a railway that
     * was still a drawing in 2026 and let them walk past it as history, which is the one thing the whole timeline
     * exists to prevent.
     *
     * <p>So a row dated after this year is <b>not told</b>. It is counted and reported in {@link #future()} rather
     * than dropped in silence, because the packs do hold it and a reader is entitled to know that the object they
     * are looking at stops where the record stops.
     */
    public static final int LAST_RECORDED = 2026;

    private final Map<String, Town> towns;
    private final List<Building> buildings;
    private final List<Work> works;
    private int undatedBuildings;
    private int undatedWorks;
    private int unnamedWorks;
    private int futureRows;
    private int earliestFewer = 0;

    private PlaceRecord(Map<String, Town> towns, List<Building> buildings, List<Work> works) {
        this.towns = towns;
        this.buildings = buildings;
        this.works = works;
        // Counted once, here, rather than left as fields that nobody ever sets. A counter that is read and never
        // written is worse than no counter: it prints a confident zero.
        for (Building b : buildings) {
            if (b.from() == null) undatedBuildings++;
        }
        for (Work w : works) {
            if (w.from() == null) undatedWorks++;
            if (w.name().isEmpty()) unnamedWorks++;
        }
        for (Building b : buildings) {
            if (b.from() != null && b.from() > LAST_RECORDED) futureRows++;
        }
        for (Work w : works) {
            if (w.from() != null && w.from() > LAST_RECORDED) futureRows++;
        }
    }

    // ------------------------------------------------------------------ reading the packs

    /** Read all three packs. A short one is a fault in the build, not a fact about the world, so it fails here. */
    public static PlaceRecord load(InputStream towns, InputStream buildings, InputStream works) {
        JsonObject t = json(towns, "towns");
        JsonObject b = json(buildings, "buildings_famous");
        JsonObject w = json(works, "ways_works");
        return new PlaceRecord(readTowns(t), readBuildings(b), readWorks(w));
    }

    static JsonObject json(InputStream in, String what) {
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement e = JsonParser.parseReader(r);
            if (!e.isJsonObject()) {
                throw new IllegalStateException(what + " is not a JSON object");
            }
            return e.getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + what, e);
        }
    }

    private static List<Named> readNames(JsonArray a) {
        List<Named> out = new ArrayList<>();
        for (JsonElement e : a) {
            JsonArray row = e.getAsJsonArray();
            Integer from = row.get(0).isJsonNull() ? null : row.get(0).getAsInt();
            Integer to = row.size() > 1 && !row.get(1).isJsonNull() ? row.get(1).getAsInt() : null;
            String name = row.get(2).getAsString();
            if (!name.isBlank()) {
                out.add(new Named(from, to, name));
            }
        }
        return out;
    }

    private static Map<String, Town> readTowns(JsonObject o) {
        Map<String, Town> out = new LinkedHashMap<>();
        for (JsonElement e : o.getAsJsonArray("towns")) {
            JsonArray r = e.getAsJsonArray();
            String qid = r.get(0).getAsString();
            String name = r.get(1).getAsString();
            double lat = r.get(2).getAsDouble() / 1000.0;
            double lon = r.get(3).getAsDouble() / 1000.0;
            Integer founded = r.get(5).isJsonNull() ? null : r.get(5).getAsInt();
            int precision = r.get(6).isJsonNull() ? 0 : r.get(6).getAsInt();
            List<Named> names = readNames(r.get(7).getAsJsonArray());
            out.put(qid, new Town(qid, name, lat, lon, founded, precision, names));
        }
        return out;
    }

    private static List<Building> readBuildings(JsonObject o) {
        List<Building> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("buildings")) {
            JsonArray r = e.getAsJsonArray();
            String name = r.get(0).getAsString();
            double lat = r.get(1).getAsDouble() / 1000.0;
            double lon = r.get(2).getAsDouble() / 1000.0;
            String kind = r.get(3).getAsString();
            Integer from = r.get(4).isJsonNull() ? null : r.get(4).getAsInt();
            Integer to = r.get(5).isJsonNull() ? null : r.get(5).getAsInt();
            String source = r.get(7).isJsonNull() ? "inception" : r.get(7).getAsString();
            out.add(new Building(name, lat, lon, kind, from, to, source));
        }
        return out;
    }

    private static List<Work> readWorks(JsonObject o) {
        List<String> kinds = new ArrayList<>();
        for (JsonElement k : o.getAsJsonArray("kinds")) {
            kinds.add(k.getAsString());
        }
        List<Work> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("works")) {
            JsonArray r = e.getAsJsonArray();
            int kind = r.get(2).getAsInt();
            Integer from = r.get(3).isJsonNull() ? null : r.get(3).getAsInt();
            Integer to = r.get(4).isJsonNull() ? null : r.get(4).getAsInt();
            String name = r.get(5).isJsonNull() ? "" : r.get(5).getAsString();
            boolean estimated = r.get(7).getAsInt() == 1;
            out.add(new Work(r.get(0).getAsDouble(), r.get(1).getAsDouble(),
                    kind >= 0 && kind < kinds.size() ? kinds.get(kind) : "work", from, to, name, estimated));
        }
        return out;
    }

    // ------------------------------------------------------------------ looking at it

    public Town town(String qid) {
        return towns.get(qid);
    }

    /**
     * The nearest town to a point. Used to get from a place a probe knows by its latitude to the row the packs
     * hold, without matching on a name: a name is a label and a point is where the thing is.
     */
    public Town nearest(double lat, double lon) {
        Town best = null;
        double bestKm = Double.MAX_VALUE;
        for (Town t : towns.values()) {
            double d = km(lat, lon, t.lat(), t.lon());
            if (d < bestKm) {
                bestKm = d;
                best = t;
            }
        }
        return best;
    }

    public int townCount() {
        return towns.size();
    }

    public int buildingCount() {
        return buildings.size();
    }

    public int workCount() {
        return works.size();
    }

    /**
     * How many towns carry names at all. 3,892, and the number is not the same as the next one: a town can carry
     * a name the pack does not date, and an undated name cannot be a sentence with a year on it.
     */
    public int townsWithNames() {
        int n = 0;
        for (Town t : towns.values()) {
            if (!t.names().isEmpty()) n++;
        }
        return n;
    }

    /** How many towns have at least one <b>dated</b> name, which is what a story needs to be told in place. */
    public int townsWithDatedNames() {
        int n = 0;
        for (Town t : towns.values()) {
            for (Named x : t.names()) {
                if (x.dated()) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double dLat = (lat2 - lat1) * KM_PER_DEGREE;
        double dLon = (lon2 - lon1) * KM_PER_DEGREE * Math.cos(Math.toRadians((lat1 + lat2) / 2.0));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    /**
     * Every dated fact about one place: its own dated names, the buildings within the radius, the works within
     * it. Undated rows are counted and left out, because an undated row cannot be a sentence with a year on it.
     *
     * @param town     the place
     * @param radiusKm how far out a row still counts as being about this place
     * @param until    nothing later than this year is returned; a story will drop its own future anyway
     */
    public List<BranchStory.Fact> facts(Town town, double radiusKm, int until) {
        List<BranchStory.Fact> out = new ArrayList<>();
        if (town == null) {
            return out;
        }
        for (Named n : town.names()) {
            if (n.dated() && n.from() <= until) {
                out.add(new BranchStory.Fact(n.from(), n.to(), "the name " + n.name(), "name", "towns",
                        Provenance.OBSERVED));
            }
        }
        if (town.founded() != null && town.founded() <= until) {
            // A founding year recorded to the century is a bound and not a date: "in the 1300s" is not 1348.
            out.add(new BranchStory.Fact(town.founded(), null, "the founding of " + town.nameNow(), "town",
                    "towns", town.foundedToTheYear() ? Provenance.OBSERVED : Provenance.STRUCTURAL));
        }
        for (Building b : buildings) {
            if (b.from() == null || b.from() > until || b.from() > LAST_RECORDED) {
                continue;
            }
            if (km(town.lat(), town.lon(), b.lat(), b.lon()) > radiusKm) {
                continue;
            }
            // The pack's own note: a building whose year comes from the gazetteer's occupation window for its
            // site is estimated, and says so in this column. Nothing here promotes it.
            boolean estimated = "gazetteer".equalsIgnoreCase(b.source());
            out.add(new BranchStory.Fact(b.from(), b.to(), b.name(), b.kind(), "buildings_famous",
                    estimated ? Provenance.MODELLED : Provenance.OBSERVED));
        }
        for (Work w : works) {
            if (w.from() == null || w.from() > until || w.from() > LAST_RECORDED) {
                continue;
            }
            if (km(town.lat(), town.lon(), w.lat(), w.lon()) > radiusKm) {
                continue;
            }
            out.add(new BranchStory.Fact(w.from(), w.to(), w.name(), w.kind(), "ways_works",
                    w.estimated() ? Provenance.MODELLED : Provenance.OBSERVED));
        }
        return out;
    }

    /**
     * The story a player gets when they ask this place to tell them about itself: real rows, the name it had
     * that year, and the voice they asked for.
     */
    public BranchStory.Story story(String qid, int year, BranchStory.Voice voice, double radiusKm, int limit) {
        Town t = town(qid);
        if (t == null) {
            throw new IllegalArgumentException("no town " + qid + " in the towns pack");
        }
        return BranchStory.tell(t.nameNow(), t.nameIn(year), year, voice, facts(t, radiusKm, year), limit);
    }

    /** The naming line for a place and a year, from the pack's own dates. */
    public String namingLine(String qid, int year) {
        Town t = town(qid);
        if (t == null) {
            throw new IllegalArgumentException("no town " + qid + " in the towns pack");
        }
        return BranchStory.namingLine(t.nameNow(), t.nameIn(year), year, t.nowNameCameIn());
    }

    /** How many rows the packs hold dated after {@link #LAST_RECORDED}, which are plans and are never told. */
    public int futureRowCount() {
        return futureRows;
    }

    /** That number, said plainly, for the notes and for a screen. */
    public String future() {
        return futureRows + " rows in the packs are dated after " + LAST_RECORDED + ": plans and things "
                + "unfinished when the record was read. A story stops there, because a plan is not something that "
                + "happened.";
    }

    /** Rows the packs hold that cannot be told as fact because they carry no year, for the notes. */
    public String undated() {
        int noYear = 0;
        for (Building b : buildings) {
            if (b.from() == null) noYear++;
        }
        for (Work w : works) {
            if (w.from() == null) noYear++;
        }
        return String.format(Locale.ROOT,
                "%d rows carry no year at all (%d buildings, %d works), so nothing here is undated in that sense: "
                        + "all three packs are built on the rule that a row without a year is not shipped. "
                        + "%d works have no name in English and cannot be told as a sentence",
                noYear, undatedBuildings, undatedWorks, unnamedWorks);
    }
}

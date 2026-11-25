package com.eternalengine.towns;

import com.eternalengine.civilization.CountryGrid;
import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.technology.TechnologyAtlas;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * What a town is made of in a place and a year (systems B9 houses, B11 insides, B13 streets and
 * their surfacing, B20 lighting, E14 markets, E16 materials, B25 water).
 *
 * <p>{@code history/building_culture.json.gz} (built by {@code worldfill/build_building_culture.py}
 * from {@code worldfill/building_culture_table.py}) holds 107 building cultures: each says where it
 * is, the years it runs, and the words for the eleven things a visitor would notice -- the house,
 * its walls, roof and floor, how the town is laid out, what the lanes are surfaced with, where the
 * water comes from, what it is lit by after dark, what is inside a home, where people trade, and how
 * many storeys. Every row names the English Wikipedia article it was written from.
 *
 * <p>This is the half that no dataset can give. Wikidata can say there was a qanat at Bam; it cannot
 * say that a house in Bam in 1600 had a windcatcher over its cistern, that its walls were of mud
 * brick renewed after every rain, or that the lane outside was swept earth with a channel down the
 * middle. B11 (what is inside a home) has no dataset at all. So the rows are written by hand, from
 * the articles, and every one is dated: a place is answered by the row that covers its country (and
 * its years), falling back to the nearest thing that does.
 *
 * <p>Three things the caller should know:
 * <ul>
 *   <li><b>Nothing the technology atlas already knows is here.</b> Fired brick, cement, steel, glass,
 *       paved and tarred roads, candles, gas light, paraffin lamps, electric light and running water
 *       have a year of arrival for each of 258 countries and a share by the size of the town. The
 *       register asks {@link TechnologyAtlas} for each of them and lifts the words itself, so a
 *       village keeps its thatch after the city has turned to concrete, and the answer changes with
 *       the year inside one row. What is stored is only what no atlas can know.</li>
 *   <li><b>A row with a box speaks only inside it.</b> Cusco is inside the rectangle that holds the
 *       Amazon, but Cusco in 1500 is Inca, not a maloca; the Inca row has no box and the maloca row
 *       does, so the box wins where it holds the point and is silent where it does not.</li>
 *   <li><b>Where several rows reach the same point and year</b>, the one whose box holds the point
 *       beats one without; then the higher rank (3 a country inside a country -- South India, the
 *       Pueblo country, the Tibetan plateau; 2 a nation or a culture; 1 a continental era; 0 the
 *       modern world and the two rows of prehistory); then the later start year; then the row
 *       covering fewer countries. Under that rule Gothic Europe does not answer for a Polish village
 *       of 1300 whose houses were of logs, and the Mughal haveli does not answer for a Tamil house
 *       of 1600.</li>
 * </ul>
 *
 * <p>Loaded once, lazily, and kept. 22 kB gzipped: 107 rows, 1,704 country-slices, 258 countries.
 */
public final class BuildingCulture {

    public static final String FILE = "/data/eternalengine/eternal/history/building_culture.json.gz";

    /**
     * One building culture: the words a town is made of. These are the words as written, before the
     * technology atlas lifts them; use {@link Reading} to get what a caller should say.
     */
    public record Words(String id, String house, String wall, String roof, String floor, String plan,
                        String surface, String water, String light, String inside, String market,
                        int storeys, String source) {

        /** How tall the houses are, as a villager would say it: "one storey", "three storeys". */
        public String storeysLine() {
            return storeys <= 1 ? "one storey" : storeys + " storeys";
        }
    }

    /**
     * What a place is like in a year: the row that reached it, and what the technology atlas has
     * added to it. Every upgrade flag is the atlas's own answer for this point, year and size of
     * town -- never a guess made here.
     *
     * @param firedBrick fired brick is in use here (atlas: {@code fired_brick})
     * @param concrete   Portland cement is in use here (atlas: {@code cement})
     * @param paved      the roads are paved (atlas: {@code paved_roads})
     * @param tarred     the roads are tarred (atlas: {@code tarmac})
     * @param piped      running water (atlas: {@code running_water})
     * @param gas        gas street lighting (atlas: {@code gas_light})
     * @param paraffin   paraffin lamps in homes (atlas: {@code kerosene_lamp})
     * @param electric   electric light (atlas: {@code electricity})
     * @param glazed     window glass (atlas: {@code glass})
     */
    public record Reading(Words base, String country, boolean firedBrick, boolean concrete, boolean steel,
                          boolean paved, boolean tarred, boolean piped, boolean gas, boolean paraffin,
                          boolean electric, boolean glazed, int storeys, boolean raised) {

        /** The house, with the atlas's own answer where it has one. */
        public String house() {
            return base.house();
        }

        /**
         * What the walls are made of. Cement is asked for, not assumed: an English village of 1890
         * had brick laid in cement mortar, and concrete block came a generation later, so both are
         * named and neither is claimed for the whole place.
         */
        public String wall() {
            if (concrete) {
                return "brick or stone laid in cement, and concrete block in the newer houses";
            }
            if (firedBrick && earthy(base.wall())) {
                return base.wall() + "; fired brick where the old walls are rebuilt";
            }
            return base.wall();
        }

        /**
         * What the roof is made of. Corrugated iron comes with the industrial sheet mills, so the
         * signal is Portland cement, not the atlas's own "steel": that one is a craft, and in
         * Zimbabwe or in India it arrived a thousand years before a rolled iron sheet did.
         */
        public String roof() {
            if (concrete && thatched(base.roof())) {
                return base.roof() + "; corrugated iron on the newer houses";
            }
            return base.roof();
        }

        /** What the floor is: concrete or tiles where cement has come. */
        public String floor() {
            if (concrete && earthy(base.floor())) {
                return "concrete, or tiles where the house can afford them";
            }
            return base.floor();
        }

        /** How the town is laid out: never a modern fact, so always the row's own words. */
        public String plan() {
            return base.plan();
        }

        /**
         * What the lanes are surfaced with. Paving is added to the row's own words rather than
         * replacing them: "setts and cobbles" is a European phrase, and Chang'an's roads of 750
         * were of rammed earth with a few of stone, not a Flemish street.
         */
        public String surface() {
            if (tarred) {
                return "asphalt, and concrete in the new streets";
            }
            if (paved && earthy(base.surface())) {
                return base.surface() + ", paved with stone or brick in the main streets";
            }
            return base.surface();
        }

        /** Where the water comes from: a tap in the house where the mains have come. */
        public String water() {
            if (piped) {
                return "a tap in the house, and the main under the street";
            }
            return base.water();
        }

        /** What it is lit by after dark: the newest light the atlas says this town has. */
        public String light() {
            if (electric) {
                return "electric light";
            }
            if (gas) {
                return "gas lamps in the street, and oil lamps indoors";
            }
            if (paraffin) {
                return "paraffin lamps";
            }
            return base.light();
        }

        /** What is inside a home: glass in the windows, then the row's own words. */
        public String inside() {
            if (glazed && !base.inside().contains("glass")) {
                return base.inside() + ", and the windows glazed";
            }
            return base.inside();
        }

        /** Where people trade: never a modern fact, so always the row's own words. */
        public String market() {
            return base.market();
        }

        /** How many storeys: taller where lifts and steel frames have come. */
        public int storeys() {
            return storeys;
        }

        /** "a courtyard house of two storeys, its rooms opening off the court". */
        public String houseLine() {
            return house();
        }

        /** The whole answer as one sentence a villager could say. */
        public String line() {
            return house() + ". Its walls are " + wall() + " and its roof is " + roof()
                    + ". The lanes are " + surface() + ", and " + plan() + ". Water: " + water()
                    + ". After dark, " + light() + ".";
        }

        /** The article the row was written from. */
        public String source() {
            return base.source();
        }

        /** The row's own name, for checks and for the debug line. */
        public String id() {
            return base.id();
        }

        private static boolean earthy(String s) {
            String v = s.toLowerCase(Locale.ROOT);
            return v.contains("mud") || v.contains("earth") || v.contains("daub") || v.contains("wattle")
                    || v.contains("adobe") || v.contains("clay") || v.contains("sand") || v.contains("daga")
                    || v.contains("turf") || v.contains("coral gravel");
        }

        private static boolean thatched(String s) {
            String v = s.toLowerCase(Locale.ROOT);
            return v.contains("thatch") || v.contains("grass") || v.contains("palm") || v.contains("reed")
                    || v.contains("shingle") || v.contains("brush");
        }
    }

    /** One country-slice: a row, the years it runs, its box, its rank and how many countries it covers. */
    private record Slice(int from, int to, int row, int[] box, int rank, int countries) {

        boolean holds(double lat, double lon) {
            if (box == null || box.length < 4) {
                return false;
            }
            int la = (int) Math.round(lat * 10.0d);
            int lo = (int) Math.round(lon * 10.0d);
            return la >= box[0] && la <= box[1] && lo >= box[2] && lo <= box[3];
        }

        boolean applies(double lat, double lon, int year) {
            if (year < from || year >= to) {
                return false;
            }
            return box == null || box.length < 4 || holds(lat, lon);
        }
    }

    private static volatile BuildingCulture instance;

    private boolean loaded;
    private String source = "resource " + FILE + " absent";
    private String problem = source;
    private List<Words> rows = List.of();
    private Map<String, List<Slice>> byCountry = Map.of();
    private int defaultRow = -1;
    private int sliceCount;
    private long loadMillis;

    private BuildingCulture() {
    }

    public static BuildingCulture get() {
        BuildingCulture b = instance;
        if (b == null) {
            synchronized (BuildingCulture.class) {
                b = instance;
                if (b == null) {
                    b = new BuildingCulture();
                    b.load();
                    instance = b;
                }
            }
        }
        return b;
    }

    /**
     * What a town here is made of, in this year, for a settlement of this size. Empty only if the
     * pack or the country grid is missing; anywhere on Earth in any year has an answer, the last one
     * being the world's own default.
     */
    public Optional<Reading> at(double lat, double lon, int year, SettlementRank rank) {
        CountryGrid grid = CountryGrid.get();
        if (!grid.loaded()) {
            return Optional.empty();
        }
        String code = grid.codeAt(lat, lon);
        return at(code, lat, lon, year, rank);
    }

    /** The answer for a named country of today: for checks, and for callers that already know it. */
    public Optional<Reading> at(String country, double lat, double lon, int year, SettlementRank rank) {
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<Slice> here = country == null ? null : byCountry.get(country);
        int row = defaultRow;
        boolean boxed = false;
        int bestRank = Integer.MIN_VALUE;
        int bestFrom = Integer.MIN_VALUE;
        int bestCountries = Integer.MAX_VALUE;
        if (here != null) {
            for (Slice s : here) {
                if (!s.applies(lat, lon, year)) {
                    continue;
                }
                boolean inBox = s.box() != null && s.box().length >= 4;
                // 1 a box that holds the point beats no box; 2 a higher rank; 3 the later start;
                // 4 the row covering fewer countries. Strictly better only, so the row written
                // later in the table wins a tie.
                boolean better = false;
                if (inBox != boxed) {
                    better = inBox;
                } else if (s.rank() != bestRank) {
                    better = s.rank() > bestRank;
                } else if (s.from() != bestFrom) {
                    better = s.from() > bestFrom;
                } else {
                    better = s.countries() < bestCountries;
                }
                if (better) {
                    row = s.row();
                    boxed = inBox;
                    bestRank = s.rank();
                    bestFrom = s.from();
                    bestCountries = s.countries();
                }
            }
        }
        if (row < 0 || row >= rows.size()) {
            return Optional.empty();
        }
        return Optional.of(lift(rows.get(row), country, lat, lon, year, rank));
    }

    /** Ask the technology atlas for each thing it knows, and lift the words where it says yes. */
    private Reading lift(Words w, String country, double lat, double lon, int year, SettlementRank rank) {
        TechnologyAtlas atlas = TechnologyAtlas.get();
        SettlementRank r = rank == null ? SettlementRank.TOWN : rank;
        boolean has = atlas.loaded();
        boolean firedBrick = has && atlas.present("fired_brick", lat, lon, year, r);
        boolean concrete = has && atlas.present("cement", lat, lon, year, r);
        boolean steel = has && atlas.present("steel", lat, lon, year, r);
        boolean paved = has && atlas.present("paved_roads", lat, lon, year, r);
        boolean tarred = has && atlas.present("tarmac", lat, lon, year, r);
        boolean piped = has && atlas.present("running_water", lat, lon, year, r);
        boolean gas = has && atlas.present("gas_light", lat, lon, year, r);
        boolean paraffin = has && atlas.present("kerosene_lamp", lat, lon, year, r);
        boolean electric = has && atlas.present("electricity", lat, lon, year, r);
        boolean glazed = has && atlas.present("glass", lat, lon, year, r);
        int storeys = w.storeys();
        boolean raised = false;
        if (has && r == SettlementRank.CITY && atlas.present("elevator", lat, lon, year, r)
                && atlas.present("skyscraper", lat, lon, year, r)) {
            storeys = Math.max(storeys, 8);
            raised = true;
        } else if (has && atlas.present("skyscraper", lat, lon, year, r)) {
            storeys = Math.max(storeys, 4);
            raised = true;
        }
        return new Reading(w, country == null ? "" : country, firedBrick, concrete, steel, paved, tarred,
                piped, gas, paraffin, electric, glazed, storeys, raised);
    }

    /** Every building culture in the pack, in the order written: for the checks. */
    public List<Words> rows() {
        return rows;
    }

    /** The articles the rows were written from, for the credits. */
    public List<String> sources() {
        List<String> out = new ArrayList<>();
        for (Words w : rows) {
            if (!out.contains(w.source())) {
                out.add(w.source());
            }
        }
        Collections.sort(out);
        return out;
    }

    public int rowCount() {
        return rows.size();
    }

    public int countryCount() {
        return byCountry.size();
    }

    public int sliceCount() {
        return sliceCount;
    }

    public boolean loaded() {
        return loaded;
    }

    /** Where the pack was read from, or why it could not be. */
    public String problem() {
        return problem;
    }

    public String source() {
        return source;
    }

    public long loadMillis() {
        return loadMillis;
    }

    public String summary() {
        return "building culture: " + rows.size() + " rows, " + byCountry.size() + " countries, "
                + sliceCount + " slices from " + source;
    }

    private synchronized void load() {
        long t0 = System.nanoTime();
        try (InputStream raw = BuildingCulture.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                source = "resource " + FILE + " absent";
                problem = source;
                return;
            }
            try (InputStreamReader reader =
                         new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                List<Words> w = new ArrayList<>();
                for (JsonElement el : root.getAsJsonArray("rows")) {
                    JsonObject o = el.getAsJsonObject();
                    w.add(new Words(
                            text(o, "id"), text(o, "house"), text(o, "wall"), text(o, "roof"),
                            text(o, "floor"), text(o, "plan"), text(o, "surface"), text(o, "water"),
                            text(o, "light"), text(o, "inside"), text(o, "market"),
                            o.has("storeys") ? o.get("storeys").getAsInt() : 1, text(o, "source")));
                }
                Map<String, List<Slice>> by = new LinkedHashMap<>();
                int n = 0;
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("countries").entrySet()) {
                    List<Slice> list = new ArrayList<>();
                    for (JsonElement el : e.getValue().getAsJsonArray()) {
                        JsonArray a = el.getAsJsonArray();
                        int[] box = null;
                        if (a.size() > 3 && a.get(3).isJsonArray() && a.get(3).getAsJsonArray().size() >= 4) {
                            JsonArray b = a.get(3).getAsJsonArray();
                            box = new int[] {b.get(0).getAsInt(), b.get(1).getAsInt(),
                                    b.get(2).getAsInt(), b.get(3).getAsInt()};
                        }
                        list.add(new Slice(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                                box, a.get(4).getAsInt(), a.get(5).getAsInt()));
                    }
                    by.put(e.getKey(), List.copyOf(list));
                    n += list.size();
                }
                Map<String, List<Slice>> keep = new HashMap<>(by);
                rows = List.copyOf(w);
                byCountry = keep;
                sliceCount = n;
                defaultRow = root.has("default") ? root.get("default").getAsInt() : w.size() - 1;
                if (defaultRow < 0 || defaultRow >= w.size()) {
                    defaultRow = w.size() - 1;
                }
                loaded = rows.size() > 1 && !byCountry.isEmpty();
                source = FILE;
                problem = loaded ? "ok" : "pack " + FILE + " held nothing";
            }
        } catch (Exception e) {
            source = "resource " + FILE + " failed: " + e.getMessage();
            problem = source;
            rows = List.of();
            byCountry = Map.of();
        } finally {
            loadMillis = (System.nanoTime() - t0) / 1_000_000L;
        }
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}

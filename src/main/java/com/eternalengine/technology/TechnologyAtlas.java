package com.eternalengine.technology;

import com.eternalengine.civilization.CountryGrid;
import com.eternalengine.civilization.SettlementRank;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 30a (system C16): the technology atlas - which technology is in use at a place, in a year, and how common it
 * is there.
 *
 * <p>{@code history/technology_atlas.json.gz} (built by {@code worldfill/build_technology_atlas.py}) keeps 130
 * technologies in 16 fields, from farming, bronze and iron to railways, radio and smartphones, with the year each
 * reached each of today's countries: recorded (English Wikipedia's articles, written by hand), from CHAT's figures for
 * the modern ones, or estimated from the nearest land that had it at the speed of its era, never before the outside
 * world reached a land (nothing from the Old World in the Americas before 1492, in Australia before 1788).
 *
 * <p>Inside a country a technology spreads from the place it began there (a record's place: Bombay for India's
 * railway, Goa for its printing press) or from the country's biggest town, at the same speeds: slower for what a town
 * must build (railways, power stations), faster for what people buy. How common it is: half of the towns some years
 * after it arrived (cities sooner, villages later), or, for what households have (radio, cars, telephones), CHAT's
 * figures per person. An older technology fades as its newer one spreads (gas lights as electric light comes).
 *
 * <p>The question every system asks (key A1): "which technology is here, at this date; how common is it?"
 */
public final class TechnologyAtlas {

    public static final String FILE = "/data/eternalengine/eternal/history/technology_atlas.json.gz";
    public static final int NEVER = Integer.MAX_VALUE;

    public enum Kind { CRAFT, FARM, INFRA, HOME, WEAPON, MEDICINE, KNOWLEDGE }

    /** One technology. */
    public static final class Tech {
        public final String id;
        public final String name;
        public final String field;
        public final Kind kind;
        public final int d50;
        public final int fame;
        public final double maxShare;
        public final int first;
        final int[] arrive;
        final String marks;
        final Map<Integer, double[][]> places;
        final Map<Integer, List<int[]>> lost;
        final Set<Integer> nativeLands;
        final Map<Integer, int[]> share;
        final List<String> vehicles;
        final List<String> infra;
        int[] replaces = new int[0];
        int[] replacedBy = new int[0];
        int[] needs = new int[0];
        int index;

        Tech(String id, String name, String field, Kind kind, int d50, int fame, double maxShare, int first, int[] arrive,
             String marks, Map<Integer, double[][]> places, Map<Integer, List<int[]>> lost, Set<Integer> nativeLands,
             Map<Integer, int[]> share, List<String> vehicles, List<String> infra) {
            this.id = id;
            this.name = name;
            this.field = field;
            this.kind = kind;
            this.d50 = Math.max(1, d50);
            this.fame = fame;
            this.maxShare = maxShare;
            this.first = first;
            this.arrive = arrive;
            this.marks = marks;
            this.places = places;
            this.lost = lost;
            this.nativeLands = nativeLands;
            this.share = share;
            this.vehicles = vehicles;
            this.infra = infra;
        }

        /** 'R' recorded, 'C' CHAT, 'B' CHAT's "by then", 'E' estimated, 'N' never, for a country of the atlas. */
        public char mark(int country) {
            return country < 0 || country >= marks.length() ? 'N' : marks.charAt(country);
        }

        public int arrivalInCountry(int country) {
            return country < 0 || country >= arrive.length ? NEVER : arrive[country];
        }
    }

    private static volatile TechnologyAtlas instance;

    private final Tech[] techs;
    private final Map<String, Tech> byId;
    private final String[] countries;
    private final int[] atlasOfGrid;
    private final double[][] speeds;
    private final Map<String, Integer> zones;
    private final String problem;
    private final long loadMillis;

    private TechnologyAtlas(Tech[] techs, String[] countries, int[] atlasOfGrid, double[][] speeds,
                            Map<String, Integer> zones, String problem, long loadMillis) {
        this.techs = techs;
        this.countries = countries;
        this.atlasOfGrid = atlasOfGrid;
        this.speeds = speeds;
        this.zones = zones;
        this.problem = problem;
        this.loadMillis = loadMillis;
        Map<String, Tech> m = new HashMap<>();
        for (Tech t : techs) {
            m.put(t.id, t);
        }
        this.byId = m;
    }

    public static TechnologyAtlas get() {
        TechnologyAtlas a = instance;
        if (a == null) {
            synchronized (TechnologyAtlas.class) {
                if (instance == null) {
                    instance = load();
                }
                a = instance;
            }
        }
        return a;
    }

    public boolean loaded() {
        return problem == null && techs.length > 0;
    }

    public String problem() {
        return problem;
    }

    public int size() {
        return techs.length;
    }

    public Tech tech(String id) {
        return byId.get(id);
    }

    public List<Tech> all() {
        return List.of(techs);
    }

    public String summary() {
        int recorded = 0;
        int chat = 0;
        int estimated = 0;
        for (Tech t : techs) {
            for (int i = 0; i < t.marks.length(); i++) {
                char c = t.marks.charAt(i);
                recorded += c == 'R' ? 1 : 0;
                chat += c == 'C' ? 1 : 0;
                estimated += c == 'E' || c == 'B' ? 1 : 0;
            }
        }
        return techs.length + " technologies in " + countries.length + " countries: " + recorded + " arrivals recorded, "
                + chat + " from CHAT, " + estimated + " estimated (loaded in " + loadMillis + " ms)";
    }

    /** The atlas's number of the country a point lies in (-1 at sea). */
    public int countryAt(double lat, double lon) {
        int g = CountryGrid.get().indexAt(lat, lon);
        return g < 0 || g >= atlasOfGrid.length ? -1 : atlasOfGrid[g];
    }

    // ================================================================== the questions

    /**
     * The year a technology reached this point: its country's year, plus the time it took to spread from where it
     * began there; never before the outside world reached the point's own part of the world. {@link #NEVER} if not
     * by 2026.
     */
    public int arrivalAt(Tech t, double lat, double lon) {
        if (t == null) {
            return NEVER;
        }
        int own = ownArrivalAt(t, lat, lon);
        // A thing for the home never comes before what the town must build for it, at this very place (a television
        // set before electric light is no use). Elsewhere what a technology needs is a matter of the land (checked
        // when the atlas was built): a railway town has the steam engines of its locomotives.
        if (t.kind == Kind.HOME) {
            for (int nd : t.needs) {
                if (own == NEVER) {
                    break;
                }
                if (techs[nd].kind == Kind.INFRA) {
                    own = Math.max(own, arrivalAt(techs[nd], lat, lon));
                }
            }
        }
        return own;
    }

    private int ownArrivalAt(Tech t, double lat, double lon) {
        int c = countryAt(lat, lon);
        if (c < 0) {
            return NEVER;
        }
        int a = t.arrive[c];
        if (a == NEVER) {
            return NEVER;
        }
        double factor = switch (t.kind) {
            case INFRA -> 0.4d;
            case KNOWLEDGE -> 0.5d;
            case HOME -> 1.5d;
            default -> 1.0d;
        };
        int g = CountryGrid.get().indexAt(lat, lon);
        double[][] from = t.places.get(c);
        double year;
        double fLat;
        double fLon;
        if (from == null || from.length == 0) {
            fLat = CountryGrid.get().mainLat(g);
            fLon = CountryGrid.get().mainLon(g);
            year = travel(a, km(lat, lon, fLat, fLon), factor);
        } else {
            // the nearest in time of the places it began in that country (Bombay 1853, Calcutta 1854 ... Hyderabad
            // 1874 for India's railway)
            year = Double.MAX_VALUE;
            fLat = from[0][0];
            fLon = from[0][1];
            double nearest = Double.MAX_VALUE;
            for (double[] p : from) {
                double d = km(lat, lon, p[0], p[1]);
                double y = travel(p[2], d, factor);
                if (d < 25.0d && d < nearest) {
                    // the town a record names: its own year (Hyderabad's railway of 1874, not Bombay's spread)
                    nearest = d;
                    year = p[2];
                    fLat = p[0];
                    fLon = p[1];
                } else if (nearest == Double.MAX_VALUE && y < year) {
                    year = y;
                    fLat = p[0];
                    fLon = p[1];
                }
            }
        }
        // A land's own invention before the outside world came stayed in its own region: Mesoamerica's writing and
        // bark paper did not reach the deserts of northern Mexico before the Spanish.
        if (t.nativeLands.contains(c) && (t.kind == Kind.KNOWLEDGE || t.kind == Kind.CRAFT)
                && km(lat, lon, fLat, fLon) > 400.0d) {
            Integer floor = zones.get(zoneOf(lat, lon));
            if (floor != null && year < floor) {
                year = floor;
            }
        }
        String pointZone = zoneOf(lat, lon);
        String homeZone = zoneOf(fLat, fLon);
        if (!pointZone.equals(homeZone) && !t.nativeLands.contains(c)) {
            Integer floor = zones.get(pointZone);
            if (floor != null && year < floor) {
                year = floor;
            }
        }
        return year >= 9999 ? NEVER : (int) Math.floor(year);
    }

    /** Forgotten here in this year (writing in Greece's dark age, the Indus script after 1900 BCE ...). */
    public boolean lostAt(Tech t, double lat, double lon, int year) {
        int c = countryAt(lat, lon);
        List<int[]> spans = c < 0 ? null : t.lost.get(c);
        if (spans == null) {
            return false;
        }
        for (int[] s : spans) {
            if (s[0] <= year && year < s[1]) {
                return true;
            }
        }
        return false;
    }

    /** How common it is here, 0 to 1: the share of towns of this rank that have it, or of households. */
    public double share(Tech t, double lat, double lon, int year, SettlementRank rank) {
        return share(t, lat, lon, year, rank, 0);
    }

    private double share(Tech t, double lat, double lon, int year, SettlementRank rank, int depth) {
        if (t == null) {
            return 0.0d;
        }
        int arrived = arrivalAt(t, lat, lon);
        if (arrived == NEVER || year < arrived || lostAt(t, lat, lon, year)) {
            return 0.0d;
        }
        SettlementRank r = rank == null ? SettlementRank.TOWN : rank;
        int c = countryAt(lat, lon);
        int[] series = c < 0 ? null : t.share.get(c);
        double s;
        if (series != null && series.length > 2) {
            double national = seriesAt(t, series, year, arrived);
            s = switch (r) {
                case CITY -> Math.min(t.maxShare, national * 2.0d + 0.002d);
                case TOWN -> national;
                case VILLAGE, COUNTRYSIDE -> national * 0.4d;
            };
        } else {
            double d = t.d50 * (r == SettlementRank.CITY ? 0.5d : r == SettlementRank.TOWN ? 1.0d : 2.0d);
            s = t.maxShare / (1.0d + Math.exp(-(year - arrived - d) / (d / 4.0d)));
        }
        if (depth == 0) {
            for (int newer : t.replacedBy) {
                s *= 1.0d - share(techs[newer], lat, lon, year, r, 1);
            }
        }
        return Math.max(0.0d, Math.min(1.0d, s));
    }

    /** CHAT's national figure for the year: between counts a straight line; after the last a curve on to the top. */
    private static double seriesAt(Tech t, int[] series, int year, int arrived) {
        int y0 = series[0];
        int step = Math.max(1, series[1]);
        int n = series.length - 2;
        double first = series[2] / 1000.0d;
        if (year < y0) {
            // before the first count: from nothing at the arrival up to the first count
            return year <= arrived ? 0.0d : first * Math.min(1.0d, (year - arrived) / (double) Math.max(1, y0 - arrived));
        }
        double k = (year - y0) / (double) step;
        if (k >= n - 1) {
            double last = series[series.length - 1] / 1000.0d;
            int lastYear = y0 + step * (n - 1);
            double grow = 1.0d - Math.exp(-(year - lastYear) / (double) t.d50);
            return last + (t.maxShare - last) * Math.max(0.0d, grow);
        }
        int a = (int) Math.floor(k);
        double f = k - a;
        double v0 = series[2 + a] / 1000.0d;
        double v1 = series[3 + a] / 1000.0d;
        return v0 + (v1 - v0) * f;
    }

    /**
     * Whether people here have it: for what a town builds (railways, gas lights), this town has it or not (a fixed
     * draw per town against the share of towns of its rank that have it); for what households own, at least a few
     * (one in a thousand); for skills and knowledge, when one town in ten knows it.
     */
    public boolean present(Tech t, double lat, double lon, int year, SettlementRank rank) {
        double s = share(t, lat, lon, year, rank);
        if (s <= 0.0d) {
            return false;
        }
        if (t.kind == Kind.INFRA && "water".equals(t.field) && !CountryGrid.get().nearSea(lat, lon, 3)) {
            return false;   // steamships and motor ships call at ports (the rivers are not on the grid)
        }
        return switch (t.kind) {
            case INFRA -> draw(t.id, lat, lon) < s || recordedPlaceNear(t, lat, lon, year);
            case HOME -> s >= 0.001d;
            default -> s >= 0.10d;
        };
    }

    /** The town a record names (Bombay's railway of 1853, Addis Ababa's of 1917) has it from that year. */
    private boolean recordedPlaceNear(Tech t, double lat, double lon, int year) {
        int c = countryAt(lat, lon);
        double[][] from = c < 0 ? null : t.places.get(c);
        if (from == null) {
            return false;
        }
        for (double[] p : from) {
            if (year >= p[2] && km(lat, lon, p[0], p[1]) < 15.0d) {
                return true;
            }
        }
        return false;
    }

    public boolean present(String id, double lat, double lon, int year, SettlementRank rank) {
        Tech t = byId.get(id);
        return t != null && present(t, lat, lon, year, rank);
    }

    /** A fixed number in [0, 1) for a town (about 5 km squares) and a technology. */
    static double draw(String id, double lat, double lon) {
        long h = 1125899906842597L;
        h = 31 * h + Math.round(lat * 20.0d);
        h = 31 * h + Math.round(lon * 20.0d);
        h = 31 * h + id.hashCode();
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return (h >>> 11) / (double) (1L << 53);
    }

    /** Every technology people here have in this year. */
    public List<Tech> presentAt(double lat, double lon, int year, SettlementRank rank) {
        List<Tech> out = new ArrayList<>();
        for (Tech t : techs) {
            if (present(t, lat, lon, year, rank)) {
                out.add(t);
            }
        }
        return out;
    }

    // ================================================================== talk

    /**
     * "People here have the railway, the telegraph and electric light. A few rich people have motor cars. Some homes
     * have radio. Nobody here has television yet." Empty when the atlas is not loaded.
     */
    public String talkLine(double lat, double lon, int year, SettlementRank rank) {
        if (!loaded() || countryAt(lat, lon) < 0) {
            return "";
        }
        List<Tech> here = new ArrayList<>();
        boolean[] has = new boolean[techs.length];
        for (Tech t : techs) {
            if (present(t, lat, lon, year, rank)) {
                has[t.index] = true;
            }
        }
        // what a technology here needs is here too (a railway town has steam engines)
        for (int pass = 0; pass < 3; pass++) {
            for (Tech t : techs) {
                if (has[t.index]) {
                    for (int nd : t.needs) {
                        int a = arrivalAt(techs[nd], lat, lon);
                        if (a != NEVER && a <= year && !lostAt(techs[nd], lat, lon, year)) {
                            has[nd] = true;
                        }
                    }
                }
            }
        }
        for (Tech t : techs) {
            if (has[t.index]) {
                here.add(t);
            }
        }
        // the newest and best known first, at most two from a field; what came in the last 300 years is news, older
        // things only while the list is short
        here.sort((a, b) -> Double.compare(score(b, lat, lon, year), score(a, lat, lon, year)));
        Map<String, Integer> perField = new HashMap<>();
        List<Tech> have = new ArrayList<>();
        List<Tech> owned = new ArrayList<>();
        for (Tech t : here) {
            int age = year - arrivalAt(t, lat, lon);
            if (age > 300 && (t.fame < 3 || have.size() + owned.size() >= 3)) {
                continue;
            }
            if (replacedHere(t, here)) {
                continue;   // a newer one is here (no "mobile phones" where everyone has a smartphone)
            }
            int k = perField.getOrDefault(t.field, 0);
            if (k >= 2 || have.size() + owned.size() >= 7) {
                continue;
            }
            perField.put(t.field, k + 1);
            if (t.kind == Kind.HOME && share(t, lat, lon, year, rank) < 0.5d) {
                owned.add(t);
            } else {
                have.add(t);
            }
        }
        List<String> parts = new ArrayList<>();
        if (!have.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Tech t : have) {
                names.add(t.name);
            }
            parts.add("People here have " + list(names) + ".");
        }
        Map<String, List<String>> byWords = new java.util.LinkedHashMap<>();
        for (Tech t : owned) {
            double s = share(t, lat, lon, year, rank);
            String who = s < 0.01d ? "A few rich people have" : s < 0.10d ? "Some homes have" : "Many homes have";
            byWords.computeIfAbsent(who, k -> new ArrayList<>()).add(t.name);
        }
        byWords.forEach((who, names) -> parts.add(who + " " + list(names) + "."));
        // What exists elsewhere for ten years or more, but not here yet: the best known, newest first.
        List<Tech> missing = new ArrayList<>();
        for (Tech t : techs) {
            if (t.fame >= 3 && t.first <= year - 10 && !here.contains(t) && !replacedHere(t, here)
                    && arrivalAt(t, lat, lon) > year) {
                missing.add(t);
            }
        }
        missing.sort((a, b) -> Integer.compare(b.first, a.first));
        if (!missing.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Tech t : missing.subList(0, Math.min(3, missing.size()))) {
                names.add(t.name);
            }
            parts.add("Nobody here has " + list(names, "or") + " yet.");
        }
        return String.join(" ", parts);
    }

    private boolean replacedHere(Tech t, List<Tech> here) {
        for (int newer : t.replacedBy) {
            if (here.contains(techs[newer])) {
                return true;
            }
        }
        return false;
    }

    private double score(Tech t, double lat, double lon, int year) {
        int age = Math.max(0, year - arrivalAt(t, lat, lon));
        return t.fame * 100.0d - Math.min(age, 400) * 0.25d;
    }

    static String list(List<String> names) {
        return list(names, "and");
    }

    static String list(List<String> names, String word) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " " + word + " " + names.get(names.size() - 1);
    }

    // ================================================================== geography

    /** Which part of the world a point is in, for the year the outside world reached it. */
    static String zoneOf(double lat, double lon) {
        if (lat > 51.0d && lon < -129.0d && lon > -170.0d) {
            return "alaska";
        }
        if (lat > 18.0d && lat < 23.0d && lon < -154.0d && lon > -161.0d) {
            return "hawaii";
        }
        if (lon > -170.0d && lon < -30.0d && lat > -60.0d) {
            return "americas";
        }
        if (lat < -10.0d && lat > -45.0d && lon > 112.0d && lon < 154.0d) {
            return "australia";
        }
        if ((lon > 154.0d || lon < -120.0d) && lat < 30.0d && lat > -50.0d) {
            return "pacific";
        }
        return "old";
    }

    /**
     * When a thing that set out in {@code startYear} and travelled {@code km} arrives, moving at
     * {@code factor} times the speed of its era. The curve is stepwise: within each band of years
     * the speed is that band's figure, and a journey that outlasts a band carries on into the next
     * one at the new speed - so a voyage begun in 1440 finishes part of itself at 25 km a day.
     *
     * <p><b>This is the atlas's spread function, not a journey.</b> It answers how a technology
     * creeps across a continent over centuries, and it does that in the atlas's own terms. For a
     * journey made in one era - a letter, a rider, a ship - ask {@link #daysToCross}, which is
     * the same curve read correctly.
     *
     * @return the year of arrival, as a fraction, in the atlas's own terms
     */
    public double arrivesIn(double startYear, double km, double factor) {
        return travel(startYear, km, factor);
    }

    /** Days in a year: the speeds are kilometres a day and the bands are years. */
    private static final double YEAR_DAYS = 365.25d;

    /**
     * How many days it takes to go {@code km}, setting out in {@code year} and moving at
     * {@code factor} times the pace of the era. The pace is that of the band of years the journey
     * starts in, and a journey long enough to outlast a band carries on into the next one at the
     * new pace - which is what a voyage begun in 1440 does.
     *
     * <p>The speeds are the speed of <b>news</b>: how fast a piece of information actually moved
     * in that era, which is the pace of the people and ships carrying it and not of the fastest
     * thing that ever existed. 8 km a day in the year 500 is news going with travellers; 25 by
     * 1450 is news going with the Venetian galleys and the merchants of the fairs; 150 in 1850 is
     * the mail by rail and steamship.
     */
    public double daysToCross(double startYear, double km, double factor) {
        if (km <= 0.0d) {
            return 0.0d;
        }
        double t = startYear;
        double left = km;
        double days = 0.0d;
        for (int i = 0; i < speeds.length; i++) {
            double bandStart = speeds[i][0];
            double v = speeds[i][1] * factor;
            double bandEnd = i + 1 < speeds.length ? speeds[i + 1][0] : Double.POSITIVE_INFINITY;
            if (t >= bandEnd) {
                continue;
            }
            if (t < bandStart) {
                return days + left / v;              // the era of this band has not begun yet
            }
            double yearsLeft = bandEnd - t;
            double canCover = yearsLeft * YEAR_DAYS * v;
            if (canCover >= left) {
                return days + left / v;
            }
            left -= canCover;
            days += yearsLeft * YEAR_DAYS;
            t = bandEnd;
        }
        return days + left / (speeds[speeds.length - 1][1] * factor);
    }

    /** The pace of an era, in kilometres a day: how fast ordinary news moved then. */
    public double eraSpeed(double year) {
        double v = speeds[0][1];
        for (int i = 0; i < speeds.length; i++) {
            if (year >= speeds[i][0]) {
                v = speeds[i][1];
            }
        }
        return v;
    }

    /** Great-circle distance between two points, in kilometres. */
    public static double kmBetween(double la1, double lo1, double la2, double lo2) {
        return km(la1, lo1, la2, lo2);
    }

    double travel(double t0, double km, double factor) {
        if (km <= 0.0d) {
            return t0;
        }
        double t = t0;
        double left = km;
        for (int i = 0; i < speeds.length; i++) {
            double start = speeds[i][0];
            double v = speeds[i][1] * factor;
            double end = i + 1 < speeds.length ? speeds[i + 1][0] : 1.0e9d;
            if (t >= end) {
                continue;
            }
            t = Math.max(t, start);
            double can = (end - t) * v;
            if (can >= left) {
                return t + left / v;
            }
            left -= can;
            t = end;
        }
        return t;
    }

    static double km(double la1, double lo1, double la2, double lo2) {
        double p1 = Math.toRadians(la1);
        double p2 = Math.toRadians(la2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371.0d * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ================================================================== loading

    private static TechnologyAtlas load() {
        long t0 = System.nanoTime();
        try (InputStream raw = TechnologyAtlas.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                return empty(FILE + " is missing");
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray cs = root.getAsJsonArray("countries");
            String[] countries = new String[cs.size()];
            Map<String, Integer> atlasIndex = new HashMap<>();
            for (int i = 0; i < cs.size(); i++) {
                countries[i] = cs.get(i).getAsString();
                atlasIndex.put(countries[i], i);
            }
            CountryGrid grid = CountryGrid.get();
            int[] atlasOfGrid = new int[grid.size()];
            for (int g = 0; g < grid.size(); g++) {
                atlasOfGrid[g] = atlasIndex.getOrDefault(grid.code(g), -1);
            }
            JsonArray sp = root.getAsJsonArray("speeds");
            double[][] speeds = new double[sp.size()][];
            for (int i = 0; i < sp.size(); i++) {
                speeds[i] = new double[]{sp.get(i).getAsJsonArray().get(0).getAsDouble(),
                        sp.get(i).getAsJsonArray().get(1).getAsDouble()};
            }
            Map<String, Integer> zones = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("zones").entrySet()) {
                zones.put(e.getKey(), e.getValue().getAsInt());
            }
            List<Tech> list = new ArrayList<>();
            Map<String, List<String>> replacesIds = new HashMap<>();
            Map<String, List<String>> needsIds = new HashMap<>();
            for (JsonElement te : root.getAsJsonArray("techs")) {
                JsonObject o = te.getAsJsonObject();
                JsonArray ar = o.getAsJsonArray("arrive");
                int[] arrive = new int[ar.size()];
                for (int i = 0; i < ar.size(); i++) {
                    arrive[i] = ar.get(i).isJsonNull() ? NEVER : ar.get(i).getAsInt();
                }
                Map<Integer, double[][]> places = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("places").entrySet()) {
                    JsonArray ps = e.getValue().getAsJsonArray();
                    double[][] arr = new double[ps.size()][];
                    for (int i = 0; i < ps.size(); i++) {
                        JsonArray p = ps.get(i).getAsJsonArray();
                        arr[i] = new double[]{p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()};
                    }
                    places.put(Integer.parseInt(e.getKey()), arr);
                }
                Map<Integer, List<int[]>> lost = new HashMap<>();
                for (JsonElement le : o.getAsJsonArray("lost")) {
                    JsonArray l = le.getAsJsonArray();
                    lost.computeIfAbsent(l.get(0).getAsInt(), k -> new ArrayList<>())
                            .add(new int[]{l.get(1).getAsInt(), l.get(2).getAsInt()});
                }
                Set<Integer> nat = new HashSet<>();
                for (JsonElement ne : o.getAsJsonArray("native")) {
                    nat.add(ne.getAsInt());
                }
                Map<Integer, int[]> share = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("share").entrySet()) {
                    JsonArray v = e.getValue().getAsJsonArray();
                    int[] s = new int[v.size()];
                    for (int i = 0; i < v.size(); i++) {
                        s[i] = v.get(i).getAsInt();
                    }
                    share.put(Integer.parseInt(e.getKey()), s);
                }
                JsonObject game = o.getAsJsonObject("game");
                List<String> vehicles = strings(game, "vehicles");
                List<String> infra = strings(game, "infra");
                Kind kind = Kind.valueOf(o.get("kind").getAsString().toUpperCase(Locale.ROOT));
                Tech t = new Tech(o.get("id").getAsString(), o.get("name").getAsString(), o.get("field").getAsString(),
                        kind, o.get("d50").getAsInt(), o.get("fame").getAsInt(), o.get("maxShare").getAsDouble(),
                        o.get("first").getAsInt(), arrive, o.get("mark").getAsString(), places, lost, nat, share,
                        vehicles, infra);
                t.index = list.size();
                list.add(t);
                replacesIds.put(t.id, strings(o, "replaces"));
                needsIds.put(t.id, strings(o, "needs"));
            }
            Tech[] arr = list.toArray(new Tech[0]);
            Map<String, Integer> pos = new HashMap<>();
            for (Tech t : arr) {
                pos.put(t.id, t.index);
            }
            Map<Integer, List<Integer>> newerOf = new HashMap<>();
            for (Tech t : arr) {
                List<Integer> r = new ArrayList<>();
                for (String id : replacesIds.getOrDefault(t.id, List.of())) {
                    Integer p = pos.get(id);
                    if (p != null) {
                        r.add(p);
                        newerOf.computeIfAbsent(p, k -> new ArrayList<>()).add(t.index);
                    }
                }
                t.replaces = r.stream().mapToInt(Integer::intValue).toArray();
            }
            for (Tech t : arr) {
                t.replacedBy = newerOf.getOrDefault(t.index, List.of()).stream().mapToInt(Integer::intValue).toArray();
                List<Integer> nd = new ArrayList<>();
                for (String id : needsIds.getOrDefault(t.id, List.of())) {
                    Integer p = pos.get(id);
                    if (p != null) {
                        nd.add(p);
                    }
                }
                t.needs = nd.stream().mapToInt(Integer::intValue).toArray();
            }
            String problem = grid.loaded() ? null : "the country grid did not load: " + grid.problem();
            return new TechnologyAtlas(arr, countries, atlasOfGrid, speeds, zones, problem,
                    (System.nanoTime() - t0) / 1_000_000L);
        } catch (Exception e) {
            return empty("cannot read " + FILE + ": " + e);
        }
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o != null && o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    private static TechnologyAtlas empty(String problem) {
        return new TechnologyAtlas(new Tech[0], new String[0], new int[0], new double[0][], Map.of(), problem, 0L);
    }
}

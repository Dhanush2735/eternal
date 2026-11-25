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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Famous people of a land: who was alive (as an adult) in a year, and who died that year.
 *
 * <p>Built by {@code worldfill/build_people.py} from the Births and Deaths sections of the
 * Wikipedia year pages. Each person carries the Wikidata id of the state their home land belonged
 * to in their active years (the id {@link RulerRegister} answers with), so a villager anywhere in
 * that state can speak of them; people with no state fall back to a circle around their land.
 *
 * <p>"Alive as an adult" is from age 20 to death. With only a death year, the 40 years before it;
 * with only a birth year, ages 20 to 70.
 */
public final class FamousPeople {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/famous_people.json.gz";
    public static final int UNKNOWN = Integer.MIN_VALUE;

    public record Person(String name, String description, int birth, int death, String state,
                         double lat, double lon, int radiusKm, int row) {
        public int activeFrom() {
            return birth != UNKNOWN ? birth + 20 : death - 40;
        }

        public int activeTo() {
            return death != UNKNOWN ? death : birth + 70;
        }

        public boolean livingAdultIn(int year) {
            return year >= activeFrom() && year <= activeTo();
        }

        public boolean diedIn(int year) {
            return death != UNKNOWN && death == year;
        }

        /** "Robert Burns, Scottish poet". */
        public String display() {
            return description == null || description.isBlank() ? name : name + ", " + description;
        }
    }

    private static final FamousPeople INSTANCE = new FamousPeople();

    /** A person and how far their place is from the point being asked about: distance computed once. */
    private record Near(Person p, double km) {}
    /**
     * Step 26 (A4): the register as tiles by state (or box of the map) and century, built from the master
     * {@link #RESOURCE} by {@code worldfill/build_tiles.py}. Only the compressed tiles stay in memory (8 MB instead
     * of 40 MB); a tile opens when a question needs that land and era ({@link com.eternalengine.data.TileCache}).
     * The answers are the same as the master's, in the same order (worldfill/probe_tiles.sh compares them).
     */
    public static final String TILES = "/data/eternalengine/eternal/history/famous_people.tiles";
    private static final java.util.Comparator<Person> BY_ACTIVE_FROM =
            java.util.Comparator.comparingInt(Person::activeFrom).thenComparingInt(Person::row);
    private static final java.util.Comparator<Person> BY_ROW = java.util.Comparator.comparingInt(Person::row);

    private com.eternalengine.data.TileStore tiles;
    private int livingBack = 90;
    private int diedBack = 90;
    private int diedAhead = 20;
    private int box = 5;
    /**
     * The largest circle any person carries (metres of map a row may stand for). Read from the tile
     * file's meta; a query asks every geographic box within this many kilometres of the point.
     */
    private int maxRadiusKm = 800;
    private int stateCount;
    private int statelessCount;

    private boolean loaded;
    private String source = "missing";
    private int count;
    /** People by state id, each array sorted by activeFrom. */
    private Map<String, Person[]> byState = Map.of();
    /** People whose land had no recorded state: matched by distance instead. */
    private Person[] stateless = new Person[0];
    /** Every person, in master order: used only by the no-tiles land fallback in {@link #candidates}. */
    private Person[] placed = new Person[0];
    /** People with an exact birthplace, by 1-degree cell. */
    private Map<Long, Person[]> birthGrid = Map.of();
    /** Every person by the one-degree cell of their own place, so the land question stays fast
     *  without the tile file (see {@link #candidates}). */
    private Map<Long, Person[]> landGrid = Map.of();
    private int birthplaces;

    private FamousPeople() {}

    public static FamousPeople get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** A copy read from an explicit file - for checks and tools, never for the game. */
    public static FamousPeople fromFile(Path file) {
        FamousPeople p = new FamousPeople();
        p.loaded = true;
        try (InputStream in = Files.newInputStream(file)) {
            p.install(read(in));
            p.source = file.toString();
        } catch (Exception e) {
            p.source = file + " failed: " + e.getMessage();
        }
        return p;
    }

    /**
     * People of this land alive as adults in this year: of the state (when known), else of the
     * stateless people whose circle covers the point. In file order (by death year).
     */
    public List<Person> livingIn(String stateQid, double lat, double lon, int year) {
        if (tiles != null) {
            return tiledLand(stateQid, lat, lon, year - livingBack, year, p -> p.livingAdultIn(year));
        }
        List<Person> out = new ArrayList<>();
        for (Person p : candidates(stateQid, lat, lon)) {
            if (p.livingAdultIn(year)) {
                out.add(p);
            }
        }
        // The same order as the tiles (tiledLand): a state's people by first adult year, the land's
        // people by master row. The old early break on the state array was what made the two
        // disagree once the land fallback was appended behind it.
        out.sort(named(stateQid) ? BY_ACTIVE_FROM : BY_ROW);
        return out;
    }

    /** Rows with this radius or less carry an exact birthplace (the notable-people database). */
    public static final int BIRTHPLACE_KM = 30;
    private static final double CELL = 1.0d;

    /**
     * People born within {@code km} of this point, at or before this year (exact birthplaces only),
     * nearest first: "X, the painter, was born near here."
     */
    public List<Person> bornNear(double lat, double lon, int year, double km) {
        if (tiles != null) {
            return tiledBornNear(lat, lon, year, km);
        }
        List<Person> out = new ArrayList<>();
        int ring = (int) Math.ceil(km / (CELL * 111.0d)) + 1;
        long cy = (long) Math.floor((lat + 90.0d) / CELL);
        long cx = (long) Math.floor((lon + 180.0d) / CELL);
        long cols = (long) Math.ceil(360.0d / CELL);
        for (long dy = -ring; dy <= ring; dy++) {
            for (long dx = -ring; dx <= ring; dx++) {
                Person[] in = birthGrid.get((cy + dy) * 1000L + Math.floorMod(cx + dx, cols));
                if (in == null) {
                    continue;
                }
                for (Person p : in) {
                    int born = p.birth() != UNKNOWN ? p.birth() : p.death() - 40;
                    if (born <= year && Math.abs(p.lat() - lat) * 111.0d - 0.5d <= km
                            && HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon()) <= km) {
                        out.add(p);
                    }
                }
            }
        }
        // one distance each, not one per comparison: the sort used to recompute the haversine of
        // both sides on every comparison (the profile of a page's answer put 3 of its 5.7 ms here)
        List<Near> cand = new ArrayList<>(out.size());
        for (Person p : out) {
            cand.add(new Near(p, HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon())));
        }
        cand.sort(Comparator.comparingDouble(Near::km));
        List<Person> sorted = new ArrayList<>(cand.size());
        for (Near n : cand) {
            sorted.add(n.p());
        }
        return sorted;
    }

    /** People of this land whose death is recorded in this year. */
    public List<Person> diedIn(String stateQid, double lat, double lon, int year) {
        if (tiles != null) {
            return tiledLand(stateQid, lat, lon, year - diedBack, year + diedAhead, p -> p.diedIn(year));
        }
        List<Person> out = new ArrayList<>();
        for (Person p : candidates(stateQid, lat, lon)) {
            if (p.diedIn(year)) {
                out.add(p);
            }
        }
        out.sort(named(stateQid) ? BY_ACTIVE_FROM : BY_ROW);
        return out;
    }

    /** Does the master hold this state's people? (The tile path asks {@code tiles.has(group)}.) */
    private boolean named(String stateQid) {
        return stateQid != null && byState.containsKey(stateQid);
    }

    private List<Person> candidates(String stateQid, double lat, double lon) {
        Person[] of = stateQid == null ? null : byState.get(stateQid);
        List<Person> near = new ArrayList<>();
        if (of != null) {
            near.addAll(java.util.Arrays.asList(of));
            // The same land fallback as appendLandFallback: the state's array alone is what made one
            // person in five invisible (see that method). Without the tile file there is no
            // geographic group, so the people are found by distance over the whole master.
            java.util.Set<Integer> seen = new java.util.HashSet<>(near.size() * 2);
            for (Person p : near) {
                seen.add(p.row());
            }
            for (Person p : placed) {
                if (!seen.contains(p.row())
                        && HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon()) <= p.radiusKm()) {
                    seen.add(p.row());
                    near.add(p);
                }
            }
            return near;
        }
        // No state named for this place-year (2.1% of the joins): the tiles answer with everyone
        // of this land whose own circle covers the point, whatever the pack calls their state
        // (tiledLand, the "x" branch). The master said only the stateless people, so the same
        // question got two different answers with and without the tile file. Both are asked for
        // everybody now, and the distance filter is what keeps it honest.
        for (Person p : landing(lat, lon)) {
            if (HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon()) <= p.radiusKm()) {
                near.add(p);
            }
        }
        return near;
    }

    /**
     * The people whose one-degree cell is close enough that their circle could cover this point: the
     * grid keeps the "who of this land" question from reading all 175,008 rows (which made the
     * master answer 30 ms a query, and the whole comparison probe half an hour).
     */
    private Iterable<Person> landing(double lat, double lon) {
        if (landGrid.isEmpty()) {
            return java.util.Arrays.asList(placed);
        }
        List<Person> out = new ArrayList<>();
        int ring = (int) Math.ceil(maxRadiusKm / (CELL * 111.0d)) + 1;
        double cos = Math.max(0.15d, Math.cos(Math.toRadians(Math.max(-75.0d, Math.min(75.0d, lat)))));
        int ringX = (int) Math.ceil(maxRadiusKm / (CELL * 111.0d * cos)) + 1;
        long cy = (long) Math.floor(lat + 90.0d);
        long cx = (long) Math.floor(lon + 180.0d);
        long cols = (long) Math.ceil(360.0d / CELL);
        for (long dy = -ring; dy <= ring; dy++) {
            for (long dx = -ringX; dx <= ringX; dx++) {
                Person[] in = landGrid.get((cy + dy) * 1000L + Math.floorMod(cx + dx, cols));
                if (in != null) {
                    out.addAll(java.util.Arrays.asList(in));
                }
            }
        }
        // master row order, so the caller's own sort or filter sees a stable list
        out.sort(BY_ROW);
        return out;
    }

    // ------------------------------------------------------------------ step 26: the tiles

    /**
     * People of a land (the state's tiles, or the people placed by distance whose circle covers the point) whose
     * first adult year falls in {@code fromYear..toYear} and who pass the test: the same people, in the same order,
     * as the arrays of the master (a state's people by first adult year then master order; the others in master
     * order).
     */
    private List<Person> tiledLand(String stateQid, double lat, double lon, int fromYear, int toYear,
                                   java.util.function.Predicate<Person> test) {
        String group = stateQid == null ? null : "s/" + stateQid;
        boolean state = group != null && tiles.has(group);
        List<Person> out = new ArrayList<>();
        for (com.eternalengine.data.TileStore.Tile t : tiles.tiles(state ? group : "x",
                Math.floorDiv(fromYear, 100), Math.floorDiv(toYear, 100))) {
            for (Person p : open(t)) {
                // The cheap test first: "was this person alive as an adult in this year" is two
                // integer comparisons, the distance is a haversine. Same answer, a fraction of the
                // arithmetic (the speed check calls this for every place a villager stands in).
                if (!test.test(p)) {
                    continue;
                }
                if (!state && (tooFarInLatitude(lat, p)
                        || HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon()) > p.radiusKm())) {
                    continue;
                }
                out.add(p);
            }
        }
        if (state) {
            appendLandFallback(out, lat, lon, fromYear, toYear, test);
        } else {
            // No state named at all for this place-year (2.1% of the joins): the old code returned
            // only the stateless people and dropped everyone else. The land fallback answers the same
            // question for everybody, so it replaces the "x" read rather than adding to it.
            out.clear();
            appendLandFallback(out, lat, lon, fromYear, toYear, test);
        }
        out.sort(state ? BY_ACTIVE_FROM : BY_ROW);
        return out;
    }

    /**
     * The people of this LAND, whatever their state was called.
     *
     * <p>Added 2026-10-22 after the marking turn measured the join: the pack stores one state id per
     * person, and the game asks {@code RulerRegister} for the state that ruled this point in the year
     * the player is standing in. Measured over all 175,008 people with a state, the two ids agree
     * for only 78.7% - and asking the same register about a different year of the same life
     * reproduces the pack's id for 75.7% of the misses, so the history is right and the moment is
     * wrong. One id per lifetime cannot match one id per world-year.
     *
     * <p>So the state's own people are asked first (unchanged, and in the same order); then the
     * geographic tiles are asked for everyone whose circle covers this point and who passes the same
     * test, whatever state the pack files them under. The distance filter is what keeps this honest:
     * a person is added only if their own place circle covers the point being read, so this adds
     * people of this land and never strangers. Rows already returned by the state tier are skipped.
     */
    private void appendLandFallback(List<Person> out, double lat, double lon, int fromYear, int toYear,
                                    java.util.function.Predicate<Person> test) {
        java.util.Set<Integer> seen = new java.util.HashSet<>(out.size() * 2);
        for (Person p : out) {
            seen.add(p.row());
        }
        // FIX (2026-10-23, the ninth marking): the steps below are BOXES, not degrees. The old loop
        // walked +-ring degrees and then divided by the box (5 degrees), so a ring meant to reach
        // 800 km reached 2 degrees: the land fallback promised every person whose circle covers the
        // point and delivered only those within about 220 km of it (Cura Ocllo, 700 km away and
        // dead in 1539, was invisible on an Inca page for 1500).
        int ring = (int) Math.ceil(maxRadiusKm / (box * 111.0d));
        // A degree of longitude is 111 km at the equator and 45 km at 66 degrees north: a ring
        // counted in latitude boxes reaches only half as far east and west up there (a man 700 km
        // from a point in Iceland was missed). The east-west ring is widened by 1/cos(latitude).
        double cos = Math.max(0.15d, Math.cos(Math.toRadians(Math.max(-75.0d, Math.min(75.0d, lat)))));
        int ringX = (int) Math.ceil(maxRadiusKm / (box * 111.0d * cos));
        long cy = (long) Math.floor(lat + 90.0d);
        long cx = (long) Math.floor(lon + 180.0d);
        long by = Math.floorDiv(cy, box);
        long bx = Math.floorDiv(cx, box);
        long boxes = (long) Math.ceil(360.0d / box);
        int from = Math.floorDiv(fromYear, 100);
        int to = Math.floorDiv(toYear, 100);
        List<Person> land = new ArrayList<>();
        for (long dy = -ring; dy <= ring; dy++) {
            for (long dx = -ringX; dx <= ringX; dx++) {
                String group = "g/" + (by + dy) + "/" + Math.floorMod(bx + dx, boxes);
                for (com.eternalengine.data.TileStore.Tile t : tiles.tiles(group, from, to)) {
                    for (Person p : open(t)) {
                        if (seen.contains(p.row())) {
                            continue;
                        }
                        // the year test before the haversine, as in tiledLand
                        if (!test.test(p)) {
                            continue;
                        }
                        if (tooFarInLatitude(lat, p) || HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon()) > p.radiusKm()) {
                            continue;
                        }
                        seen.add(p.row());
                        land.add(p);
                    }
                }
            }
        }
        land.sort(BY_ROW);
        out.addAll(land);
    }

    /** {@link #bornNear} from the birthplace tiles: cells in the master's order, each in master order, then by distance. */
    private List<Person> tiledBornNear(double lat, double lon, int year, double km) {
        int ring = (int) Math.ceil(km / (CELL * 111.0d)) + 1;
        long cy = (long) Math.floor((lat + 90.0d) / CELL);
        long cx = (long) Math.floor((lon + 180.0d) / CELL);
        long cols = (long) Math.ceil(360.0d / CELL);
        java.util.LinkedHashMap<Long, List<Person>> cells = new java.util.LinkedHashMap<>();
        java.util.LinkedHashSet<String> boxes = new java.util.LinkedHashSet<>();
        for (long dy = -ring; dy <= ring; dy++) {
            for (long dx = -ring; dx <= ring; dx++) {
                long y = cy + dy;
                long x = Math.floorMod(cx + dx, cols);
                cells.put(y * 1000L + x, new ArrayList<>());
                boxes.add("b/" + Math.floorDiv(y, box) + "/" + Math.floorDiv(x, box));
            }
        }
        for (String b : boxes) {
            for (com.eternalengine.data.TileStore.Tile t : tiles.tiles(b, Integer.MIN_VALUE, Math.floorDiv(year, 100))) {
                for (Person p : open(t)) {
                    long key = (long) Math.floor((p.lat() + 90.0d) / CELL) * 1000L
                            + (long) Math.floor((p.lon() + 180.0d) / CELL);
                    List<Person> in = cells.get(key);
                    if (in == null) {
                        continue;
                    }
                    int born = p.birth() != UNKNOWN ? p.birth() : p.death() - 40;
                    if (born <= year && HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon()) <= km) {
                        in.add(p);
                    }
                }
            }
        }
        List<Near> cand = new ArrayList<>();
        for (List<Person> in : cells.values()) {
            in.sort(BY_ROW);
            for (Person p : in) {
                cand.add(new Near(p, HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon())));
            }
        }
        cand.sort(Comparator.comparingDouble(Near::km));
        List<Person> out = new ArrayList<>(cand.size());
        for (Near n : cand) {
            out.add(n.p());
        }
        return out;
    }


    /**
     * A cheap "this person cannot be within their own circle" test: 111 km a degree of latitude, so a
     * difference of more than radius/111 degrees settles it without any trigonometry. Conservative by
     * half a kilometre, so it never rejects a row the haversine would have kept.
     */
    private static boolean tooFarInLatitude(double lat, Person p) {
        return Math.abs(p.lat() - lat) * 111.0d - 0.5d > p.radiusKm();
    }

    /** One opened tile, through the shared cache (opened from the compressed file when it is not open). */
    private Person[] open(com.eternalengine.data.TileStore.Tile t) {
        return com.eternalengine.data.TileCache.SHARED.get("famous_people|" + t.group() + "|" + t.first(),
                com.eternalengine.data.TileCache.estimate(t), () -> decode(tiles.inflate(t), t.rows()));
    }

    private static Person[] decode(byte[] raw, int rows) {
        Person[] out = new Person[rows];
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(raw);
        for (int i = 0; i < rows; i++) {
            int row = b.getInt();
            String name = str(b);
            String desc = str(b);
            int birth = b.getInt();
            int death = b.getInt();
            String state = str(b);
            double la = b.getInt() / 1000.0d;
            double lo = b.getInt() / 1000.0d;
            int radius = b.getShort();
            out[i] = new Person(name, desc, birth, death, state.isEmpty() ? null : state, la, lo, radius, row);
        }
        if (b.hasRemaining()) {
            throw new IllegalStateException("famous people tile has " + b.remaining() + " bytes left over");
        }
        return out;
    }

    private static String str(java.nio.ByteBuffer b) {
        int n = b.getShort() & 0xFFFF;
        String s = new String(b.array(), b.position(), n, StandardCharsets.UTF_8);
        b.position(b.position() + n);
        return s;
    }

    /** The tile file in use, or null when the register was read from the master (tools, or no tile file). */
    public com.eternalengine.data.TileStore tileStore() {
        return tiles;
    }

    public int size() {
        return count;
    }

    /** Everyone, for the runtime checks (step 25: no impossible lifetimes). Tiles are read, not kept open. */
    public java.util.stream.Stream<Person> all() {
        if (tiles != null) {
            // "b/" is the birthplace index and "g/" the geographic index added on 2026-10-22: both
            // hold the SAME rows as the state groups, so listing them here returned every person
            // twice (measured: 368,120 for a master of 184,060). An index is not a source.
            return tiles.groupNames().stream()
                    .filter(g -> !g.startsWith("b/") && !g.startsWith("g/")).sorted()
                    .flatMap(g -> java.util.Arrays.stream(tiles.tiles(g)))
                    .flatMap(t -> java.util.Arrays.stream(decode(tiles.inflate(t), t.rows())));
        }
        return java.util.stream.Stream.concat(byState.values().stream().flatMap(java.util.Arrays::stream),
                java.util.Arrays.stream(stateless));
    }

    /** People with an exact birthplace (within {@link #BIRTHPLACE_KM}), for the runtime checks. */
    public int birthplaceCount() {
        return birthplaces;
    }

    /** Where the register was read from, or why it could not be. */
    public String source() {
        return source;
    }

    public String summary() {
        if (tiles != null) {
            return "famous people: " + count + " people of " + stateCount + " states (+" + statelessCount
                    + " placed by distance; " + birthplaces + " with an exact birthplace) in " + tiles.tileCount()
                    + " tiles of " + tiles.groupCount() + " lands and boxes ("
                    + String.format(java.util.Locale.ROOT, "%.1f", tiles.compressedBytes() / 1048576.0d)
                    + " MB in memory) from " + source;
        }
        return "famous people: " + count + " people of " + byState.size() + " states (+" + stateless.length
                + " placed by distance; " + birthplaces + " with an exact birthplace) from " + source;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = FamousPeople.class.getResourceAsStream(TILES)) {
            if (in != null) {
                com.eternalengine.data.TileStore t = com.eternalengine.data.TileStore.read(in);
                livingBack = t.metaInt("livingBack", livingBack);
                diedBack = t.metaInt("diedBack", diedBack);
                diedAhead = t.metaInt("diedAhead", diedAhead);
                box = t.metaInt("box", box);
                maxRadiusKm = t.metaInt("maxRadius", maxRadiusKm);
                stateCount = t.metaInt("states", 0);
                statelessCount = t.metaInt("stateless", 0);
                birthplaces = t.metaInt("birthplaces", 0);
                count = t.rows();
                tiles = t;
                source = TILES;
                return;
            }
        } catch (Exception e) {
            source = "resource " + TILES + " failed: " + e.getMessage() + "; ";
        }
        try (InputStream in = FamousPeople.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            install(read(in));
            source = RESOURCE;
        } catch (Exception e) {
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
        }
    }

    private void install(List<Person> people) {
        Map<String, List<Person>> by = new HashMap<>();
        List<Person> none = new ArrayList<>();
        for (Person p : people) {
            if (p.state() == null) {
                none.add(p);
            } else {
                by.computeIfAbsent(p.state(), k -> new ArrayList<>()).add(p);
            }
        }
        Map<String, Person[]> packed = new HashMap<>();
        by.forEach((q, list) -> {
            list.sort(Comparator.comparingInt(Person::activeFrom));
            packed.put(q, list.toArray(new Person[0]));
        });
        byState = Map.copyOf(packed);
        stateless = none.toArray(new Person[0]);
        placed = people.toArray(new Person[0]);
        count = people.size();
        Map<Long, List<Person>> cells = new HashMap<>();
        for (Person p : people) {
            if (p.radiusKm() <= BIRTHPLACE_KM) {
                long key = (long) Math.floor((p.lat() + 90.0d) / CELL) * 1000L
                        + (long) Math.floor((p.lon() + 180.0d) / CELL);
                cells.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            }
        }
        Map<Long, Person[]> bg = new HashMap<>();
        cells.forEach((k, v) -> bg.put(k, v.toArray(new Person[0])));
        birthGrid = bg;
        Map<Long, List<Person>> land = new HashMap<>();
        for (Person p : people) {
            long key = (long) Math.floor((p.lat() + 90.0d) / CELL) * 1000L
                    + (long) Math.floor((p.lon() + 180.0d) / CELL);
            land.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        Map<Long, Person[]> lg = new HashMap<>();
        land.forEach((k, v) -> lg.put(k, v.toArray(new Person[0])));
        landGrid = lg;
        birthplaces = cells.values().stream().mapToInt(List::size).sum();
    }

    private static List<Person> read(InputStream raw) throws java.io.IOException {
        List<Person> out = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray rows = root.getAsJsonArray("people");
            if (rows == null) {
                return out;
            }
            for (JsonElement el : rows) {
                JsonArray a = el.getAsJsonArray();
                int birth = a.get(2).isJsonNull() ? UNKNOWN : a.get(2).getAsInt();
                int death = a.get(3).isJsonNull() ? UNKNOWN : a.get(3).getAsInt();
                if (birth == UNKNOWN && death == UNKNOWN) {
                    continue;
                }
                out.add(new Person(a.get(0).getAsString(), a.get(1).isJsonNull() ? "" : a.get(1).getAsString(),
                        birth, death, a.get(4).isJsonNull() ? null : a.get(4).getAsString(),
                        a.get(5).getAsDouble(), a.get(6).getAsDouble(), a.get(7).getAsInt(), out.size()));
            }
        }
        return out;
    }
}

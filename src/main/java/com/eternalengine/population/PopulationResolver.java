package com.eternalengine.population;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.civilization.SettlementRank;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Population derivation ladder.
 *
 * <p><b>Why this exists.</b> The corpus has no per-settlement population: a grep for
 * {@code "population"} across all thirteen history packs returns nothing. So standing in a city
 * that the gazetteer can name, date and rank, and asking how many people live there, returned zero.
 *
 * <p><b>This class answers a question, it does not make a decision.</b> It produces one number —
 * how many people live in a place in a year — and labels how that number was obtained. It does not
 * decide how many people to render: the spawner places its people by Minecraft's own mob spawning
 * rules, and did so before this class existed. Anyone tempted to feed this number into a spawn
 * count should read {@link com.eternalengine.villager.PresenceSpawning} first.
 *
 * <p><b>This is the same problem {@code SettlementYearEstimator} already solves for years.</b>
 * That class derives an occupancy start from gravity, diffusion lag and parent boxes, and labels
 * the result {@code estimated}. Population gets the identical treatment, with the identical
 * discipline: a value plus a statement of how it was obtained.
 *
 * <h3>The ladder</h3>
 * Tiers are tried in order; the first that produces a value wins.
 * <ol>
 *   <li><b>T0 SOURCED</b> — an attributed census/gazetteer point for this place and this year.</li>
 *   <li><b>T1 INTERPOLATED</b> — two attributed points bracket the year; log-linear between them.</li>
 *   <li><b>T2 MODELLED</b> — rank-size against the nearest <em>settlement</em> anchor of equal or
 *       greater rank, decayed by distance.</li>
 *   <li><b>T3 ESTIMATED</b> — inverse-square gravity mean of qualifying settlement anchors.</li>
 *   <li><b>T5 INFERRED</b> — parent aggregate (district/mandal) divided by its settlement count.</li>
 *   <li><b>T4 GRIDDED</b> — villages only: the HYDE 3.2.1 population model ({@link HydeGrid}), people
 *       per km2 in the place's 0.5-degree cell that year times the 10 km2 a village farms. Tried
 *       <em>after</em> T5, so a curated district total keeps answering wherever one exists; HYDE
 *       replaces only the era band.</li>
 *   <li><b>T6 FLOOR</b> — era x rank band. <b>Cannot fail.</b></li>
 * </ol>
 *
 * <p><b>The guarantee is T6.</b> If a place is occupied, this class returns a positive number. It
 * never returns zero for an occupied place, because zero is a modelling failure, not a statement.
 *
 * <p><b>Determinism.</b> T6 picks within its band from a hash of {@code (siteId, year)}, so a
 * value is stable across restarts and identical on every machine. Same discipline as divergence
 * seeds.
 *
 * @see com.eternalengine.civilization.SettlementYearEstimator
 */
public final class PopulationResolver {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private static final String INDEX = "/data/eternalengine/eternal/population/index.json";
    private static final String PACK_PREFIX = "/data/eternalengine/eternal/population/";

    private static final PopulationResolver INSTANCE = new PopulationResolver();

    /** How a value was obtained. Never omit this from a display. */
    public enum Confidence {
        /** An attributed census or gazetteer figure, exact year. */
        SOURCED,
        /** Between two attributed figures. */
        INTERPOLATED,
        /** Rank-size against a real neighbouring anchor. */
        MODELLED,
        /** Inverse-square gravity over several anchors. */
        ESTIMATED,
        /** External gridded raster: the HYDE 3.2.1 population model (see {@link HydeGrid}). */
        GRIDDED,
        /** Parent aggregate divided by its settlement count. */
        INFERRED,
        /** Era x rank band. A presence, not a measurement. */
        FLOOR;

        public boolean isRecord() {
            return this == SOURCED;
        }

        public String label() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Result(long population, Confidence confidence, String source) {
        public String display() {
            return "population~" + population + " (" + confidence.label() + ": " + source + ")";
        }
    }

    public record SeriesPoint(int year, long population, String source) {}

    public record Region(String id, String name, String kind, SettlementRank rank,
                         double lat, double lon, double radiusKm, int settlementCount,
                         Integer startYear, Integer endYear, List<SeriesPoint> series) {
        public boolean isSettlement() { return "settlement".equals(kind); }
        public boolean isAggregate()  { return "aggregate".equals(kind); }
        public boolean isPolity()     { return "polity".equals(kind); }
    }

    private record Band(int fromYear, int toYear, long villageMin, long villageMax,
                        long townMin, long townMax, long cityMin, long cityMax) {
        long min(SettlementRank r) {
            return r == SettlementRank.CITY ? cityMin : r == SettlementRank.TOWN ? townMin
                    : r == SettlementRank.VILLAGE ? villageMin : COUNTRY_MIN;
        }
        long max(SettlementRank r) {
            return r == SettlementRank.CITY ? cityMax : r == SettlementRank.TOWN ? townMax
                    : r == SettlementRank.VILLAGE ? villageMax : COUNTRY_MAX;
        }
    }

    /**
     * The extent used when a place has no region of its own: the same default the loader applies.
     * A crowd has to be spread over <em>some</em> area, so it is spread over this.
     */
    public static final double DEFAULT_PLACE_RADIUS_KM = 10.0d;

    private final List<Region> regions = new ArrayList<>();
    private final List<Band> bands = new ArrayList<>();
    private double unitsVillage = 1.0d;
    private double unitsTown = 26.0d;
    private double unitsCity = 2500.0d;
    private double gravityDecayKm = 250.0d;
    private double gravityRadiusKm = 250.0d;
    private int maxInterpolationGapYears = 60;
    private boolean loaded;
    private int loadErrors;

    /** When set, packs are read from a world folder. Null means the copy inside the jar. */
    private Path sourceDir;

    private PopulationResolver() {}

    public static PopulationResolver get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /**
     * Load the pack from the world folder, falling back to the copy inside the jar.
     *
     * <p>This is what makes the installed pack the one that counts: the file
     * {@code /ee foundation install} writes into {@code <world>/eternalengine/population/} is the
     * file the resolver reads, so a figure can be corrected without rebuilding the mod. The jar
     * copy remains the fallback, and if the world copy is unreadable or yields no regions the jar
     * copy is used and the log says so -- a broken edit must never switch the resolver off.
     */
    public synchronized void loadFromDirectory(Path dir) {
        this.sourceDir = dir;
        this.loaded = false;
        this.regions.clear();
        this.bands.clear();
        this.loadErrors = 0;
        ensureLoaded();
    }

    /**
     * Force the pack load now.
     *
     * <p>Called at server start so a pack problem is reported at startup next to the other
     * loaders, instead of surfacing mid-play the first time somebody asks for a population.
     */
    public void warmUp() {
        ensureLoaded();
    }

    /** Which copy answered the last load: {@code jar}, or {@code <folder> folder}. */
    public String loadedFrom() {
        return sourceDir != null && loadedFromWorld ? sourceDir.getFileName() + " folder" : "jar";
    }

    private boolean loadedFromWorld;

    // ==================== the ladder ====================

    /**
     * Resolve a population for one occupied place in one year.
     *
     * @param siteId gazetteer site id; used for region matching and for the deterministic T6 pick.
     *               May be null, in which case T6 still returns a stable value from coordinates.
     * @param rank   the rank the gazetteer resolved for this place and year. Null is treated as village.
     * @return never null, never zero for a non-null rank.
     */
    /**
     * How far this place extends, in kilometres — the area its people are spread over.
     *
     * <p>This is not the population number, and it is deliberately a separate call: the number is one
     * fact about a place, its extent is another, and the crowd needs both. The lookup is the same one
     * {@link #resolve} does and it is deterministic, so the extent always belongs to the same region
     * the number came from. A place with no region of its own gets
     * {@link #DEFAULT_PLACE_RADIUS_KM}.
     */
    public double radiusKm(String siteId, double lat, double lon) {
        ensureLoaded();
        return findRegion(siteId, lat, lon).map(Region::radiusKm).orElse(DEFAULT_PLACE_RADIUS_KM);
    }

    public Result resolve(String siteId, double lat, double lon, int year, SettlementRank rank) {
        ensureLoaded();
        SettlementRank effectiveRank = rank == null ? SettlementRank.VILLAGE : rank;

        Optional<Region> match = findRegion(siteId, lat, lon);

        if (match.isPresent()) {
            Region r = match.get();
            Optional<SeriesPoint> exact = exactPoint(r, year);
            if (exact.isPresent()) {
                SeriesPoint p = exact.get();
                return new Result(p.population(), Confidence.SOURCED,
                        r.name() + " " + year + " — " + p.source());
            }
            Optional<Result> interp = interpolate(r, year);
            if (interp.isPresent()) {
                return interp.get();
            }
        }

        // Open country asks the grid first (turn 231): HYDE density is the one tier built for
        // land with no settlement on it. Rank-size and gravity extrapolate downward from towns
        // and would dress a camp in a town's numbers; for COUNTRYSIDE they stay behind the model.
        if (effectiveRank == SettlementRank.COUNTRYSIDE) {
            Optional<Result> country = gridded(lat, lon, year, effectiveRank);
            if (country.isPresent()) {
                return country.get();
            }
        }

        Optional<Result> rankSize = rankSize(lat, lon, year, effectiveRank);
        if (rankSize.isPresent()) {
            return rankSize.get();
        }

        Optional<Result> gravity = gravity(lat, lon, year, effectiveRank);
        if (gravity.isPresent()) {
            return gravity.get();
        }

        Optional<Result> aggregate = aggregateApportionment(lat, lon, year, effectiveRank);
        if (aggregate.isPresent()) {
            return aggregate.get();
        }

        // T4 GRIDDED sits after T5 on purpose: a curated district total is better evidence than a
        // global model, so HYDE only replaces the era band.
        Optional<Result> gridded = gridded(lat, lon, year, effectiveRank);
        if (gridded.isPresent()) {
            return gridded.get();
        }

        return floorBand(siteId, lat, lon, year, effectiveRank);
    }

    /** The parent polity's own population at a year, if one is on record. Not a settlement figure. */
    public Optional<Result> polityPopulation(double lat, double lon, int year) {
        ensureLoaded();
        for (Region r : regions) {
            if (!r.isPolity()) continue;
            if (r.startYear() != null && year < r.startYear()) continue;
            if (r.endYear() != null && year >= r.endYear()) continue;
            if (HistoricalPresence.haversineKm(lat, lon, r.lat(), r.lon()) > r.radiusKm()) continue;
            Optional<SeriesPoint> p = nearestWithinGap(r, year);
            if (p.isPresent()) {
                return Optional.of(new Result(p.get().population(), Confidence.SOURCED,
                        r.name() + " " + p.get().year() + " — " + p.get().source()));
            }
        }
        return Optional.empty();
    }

    // ==================== tiers ====================

    /**
     * Region matching for the <b>place</b> tiers only: id first (so Secunderabad matches
     * {@code secunderabad_17434_78501} from the OSM pack as well as {@code secunderabad}), then
     * radius.
     *
     * <p><b>Settlements only.</b> Aggregates such as a district are deliberately excluded here.
     * A district figure is the population of a whole district, not of the place standing in it,
     * and returning it as this place's value was a real bug: a point in empty countryside inside
     * Atraf-i-Balda resolved to 612,493 — the entire district — and every rank returned the same
     * number. Aggregates are reached only through {@link #aggregateApportionment}, which divides by
     * the district's own documented settlement count.
     */
    private Optional<Region> findRegion(String siteId, double lat, double lon) {
        if (siteId != null && !siteId.isBlank()) {
            String key = siteId.toLowerCase(java.util.Locale.ROOT);
            Region best = null;
            double bestKm = Double.MAX_VALUE;
            for (Region r : regions) {
                if (!r.isSettlement()) continue;
                if (!key.startsWith(r.id().toLowerCase(java.util.Locale.ROOT))) continue;
                double km = HistoricalPresence.haversineKm(lat, lon, r.lat(), r.lon());
                if (km < bestKm) { bestKm = km; best = r; }
            }
            if (best != null) return Optional.of(best);
        }
        Region best = null;
        double bestKm = Double.MAX_VALUE;
        for (Region r : regions) {
            if (!r.isSettlement()) continue;
            double km = HistoricalPresence.haversineKm(lat, lon, r.lat(), r.lon());
            if (km > r.radiusKm()) continue;
            if (km < bestKm) { bestKm = km; best = r; }
        }
        return Optional.ofNullable(best);
    }

    private Optional<SeriesPoint> exactPoint(Region r, int year) {
        for (SeriesPoint p : r.series()) {
            if (p.year() == year && p.population() > 0L) return Optional.of(p);
        }
        return Optional.empty();
    }

    /** Nearest point within the gap allowance, either side. Used as a soft anchor. */
    private Optional<SeriesPoint> nearestWithinGap(Region r, int year) {
        SeriesPoint best = null;
        int bestGap = Integer.MAX_VALUE;
        for (SeriesPoint p : r.series()) {
            if (p.population() <= 0L) continue;
            int gap = Math.abs(p.year() - year);
            if (gap < bestGap) { bestGap = gap; best = p; }
        }
        return best != null && bestGap <= maxInterpolationGapYears ? Optional.of(best) : Optional.empty();
    }

    /**
     * T1. Bracketing points interpolate log-linearly (population growth is multiplicative, not
     * additive). A single point inside the gap allowance is used as-is and still labelled
     * interpolated, with the gap stated, because it is not this year's figure.
     */
    private Optional<Result> interpolate(Region r, int year) {
        List<SeriesPoint> pts = new ArrayList<>();
        for (SeriesPoint p : r.series()) if (p.population() > 0L) pts.add(p);
        if (pts.isEmpty()) return Optional.empty();
        pts.sort(Comparator.comparingInt(SeriesPoint::year));

        SeriesPoint lo = null;
        SeriesPoint hi = null;
        for (SeriesPoint p : pts) {
            if (p.year() <= year) lo = p;
            if (p.year() >= year && hi == null) hi = p;
        }

        if (lo != null && hi != null && lo.year() != hi.year()) {
            int span = hi.year() - lo.year();
            if (span > maxInterpolationGapYears) return Optional.empty();
            double t = (year - lo.year()) / (double) span;
            long value = logLerp(lo.population(), hi.population(), t);
            return Optional.of(new Result(value, Confidence.INTERPOLATED,
                    "between " + r.name() + " " + lo.year() + " (" + lo.population() + ") and "
                            + hi.year() + " (" + hi.population() + ")" + footprintNote(r, lo, hi)));
        }

        Optional<SeriesPoint> near = nearestWithinGap(r, year);
        if (near.isPresent()) {
            SeriesPoint p = near.get();
            return Optional.of(new Result(p.population(), Confidence.INTERPOLATED,
                    "nearest record is " + r.name() + " " + p.year() + " (" + p.population()
                            + "), " + Math.abs(p.year() - year) + "y away"));
        }
        return Optional.empty();
    }

    /**
     * T2. Rank-size against the nearest real anchor of equal or greater rank.
     *
     * <p>A city is never invented from a village. If no anchor of sufficient rank is within range,
     * this tier misses and the ladder continues — which is the honest outcome.
     */
    private Optional<Result> rankSize(double lat, double lon, int year, SettlementRank rank) {
        double units = unitsFor(rank);
        Region best = null;
        long bestValue = 0L;
        double bestKm = Double.MAX_VALUE;
        String bestWhy = null;

        for (Region r : regions) {
            if (!r.isSettlement()) continue;
            if (unitsFor(r.rank()) < units) continue;   // never extrapolate upward in class
            Optional<ResolvedAnchor> anchor = anchorValue(r, year);
            if (anchor.isEmpty()) continue;
            double km = HistoricalPresence.haversineKm(lat, lon, r.lat(), r.lon());
            if (km > gravityRadiusKm) continue;
            if (km < bestKm) {
                bestKm = km;
                best = r;
                bestValue = anchor.get().value();
                bestWhy = anchor.get().why();
            }
        }
        if (best == null) return Optional.empty();

        double decay = 1.0d / (1.0d + bestKm / gravityDecayKm);
        long value = Math.max(1L, Math.round(bestValue * (units / unitsFor(best.rank())) * decay));
        return Optional.of(new Result(value, Confidence.MODELLED,
                "rank-size from " + best.name() + " (" + bestValue + ", " + bestWhy + "), "
                        + String.format(java.util.Locale.ROOT, "%.0f", bestKm) + "km, decay "
                        + String.format(java.util.Locale.ROOT, "%.2f", decay)
                        + " — calibration constants, not a record"));
    }

    /** T3. Inverse-square gravity mean over qualifying settlement anchors. */
    private Optional<Result> gravity(double lat, double lon, int year, SettlementRank rank) {
        double units = unitsFor(rank);
        double weighted = 0.0d;
        double weightSum = 0.0d;
        int used = 0;

        for (Region r : regions) {
            if (!r.isSettlement()) continue;
            if (unitsFor(r.rank()) < units) continue;
            Optional<ResolvedAnchor> anchor = anchorValue(r, year);
            if (anchor.isEmpty()) continue;
            double km = HistoricalPresence.haversineKm(lat, lon, r.lat(), r.lon());
            if (km > gravityRadiusKm) continue;
            double d = Math.max(km, 0.5d);
            double w = 1.0d / (d * d);
            weighted += Math.log(Math.max(1.0d, anchor.get().value() * (units / unitsFor(r.rank())))) * w;
            weightSum += w;
            used++;
        }
        if (weightSum < 1e-9 || used == 0) return Optional.empty();

        long value = Math.max(1L, Math.round(Math.exp(weighted / weightSum)));
        return Optional.of(new Result(value, Confidence.ESTIMATED,
                "gravity over " + used + " anchor(s) within "
                        + String.format(java.util.Locale.ROOT, "%.0f", gravityRadiusKm) + "km"));
    }

    /** T5. Parent aggregate divided by the number of settlements it is documented to contain. */
    private Optional<Result> aggregateApportionment(double lat, double lon, int year, SettlementRank rank) {
        double units = unitsFor(rank);
        for (Region r : regions) {
            if (!r.isAggregate() || r.settlementCount() <= 0) continue;
            double km = HistoricalPresence.haversineKm(lat, lon, r.lat(), r.lon());
            if (km > r.radiusKm()) continue;
            Optional<ResolvedAnchor> anchor = anchorValue(r, year);
            if (anchor.isEmpty()) continue;
            long perSettlement = anchor.get().value() / r.settlementCount();
            long value = Math.max(1L, Math.round(perSettlement * (units / unitsVillage)));
            return Optional.of(new Result(value, Confidence.INFERRED,
                    r.name() + " " + anchor.get().value() + " / " + r.settlementCount()
                            + " settlements (avg per settlement), scaled by rank"));
        }
        return Optional.empty();
    }

    /** Land a village's people live on and farm, for the T4 tier (km2). */
    private static final double GRID_CATCHMENT_VILLAGE_KM2 = 10.0d;

    /**
     * The open-country band for T6 (turn 231). A camp of a few families. The village band's 200
     * floor is a village's claim, not the countryside's: wearing the village's clothes, that floor
     * is what invented "about 250 people" at Secunderabad in 3000 BC.
     */
    private static final long COUNTRY_MIN = 1L;
    private static final long COUNTRY_MAX = 30L;

    /**
     * T4. For a village: HYDE 3.2.1 density in its 0.5-degree cell (interpolated to the year) times
     * the 10 km2 its people farm - a hamlet of a few people on thin land, a few hundred on dense
     * land. Towns and cities are left to the era band: a cell's average density says nothing about
     * how many people crowd into one town. Misses where the model has nobody.
     */
    private Optional<Result> gridded(double lat, double lon, int year, SettlementRank rank) {
        if (rank != SettlementRank.VILLAGE && rank != SettlementRank.COUNTRYSIDE) {
            return Optional.empty();
        }
        Optional<HydeGrid.Reading> reading = HydeGrid.get().reading(lat, lon, year);
        if (reading.isEmpty() || reading.get().density() < HydeGrid.MIN_DENSITY) {
            return Optional.empty();
        }
        HydeGrid.Reading r = reading.get();
        long value = Math.max(1L, Math.round(r.density() * GRID_CATCHMENT_VILLAGE_KM2));
        return Optional.of(new Result(value, Confidence.GRIDDED,
                r.display() + " x " + String.format(java.util.Locale.ROOT, "%.0f", GRID_CATCHMENT_VILLAGE_KM2)
                        + (rank == SettlementRank.COUNTRYSIDE
                                ? " km2 of open country - a population model, not a count"
                                : " km2 farmed by a village - a population model, not a count")));
    }

    /**
     * T6. Era x rank band. This tier cannot fail, which is what makes the ladder total.
     * The position inside the band is a deterministic hash of (siteId, year) so the value is
     * stable across restarts and identical on every machine.
     */
    private Result floorBand(String siteId, double lat, double lon, int year, SettlementRank rank) {
        if (rank == SettlementRank.COUNTRYSIDE) {
            long frac = deterministicFraction(siteId, lat, lon, year);
            long value = COUNTRY_MIN + Math.round((COUNTRY_MAX - COUNTRY_MIN) * (frac / 999.0d));
            return new Result(value, Confidence.FLOOR,
                    "open country band " + COUNTRY_MIN + "-" + COUNTRY_MAX
                            + " (a camp of a few families), deterministic pick \u2014 a presence, not a measurement");
        }
        Band band = bandFor(year);
        long min = band.min(rank);
        long max = band.max(rank);
        long span = Math.max(0L, max - min);
        long frac = deterministicFraction(siteId, lat, lon, year);
        long value = min + Math.round(span * (frac / 999.0d));
        return new Result(value, Confidence.FLOOR,
                "era band " + band.fromYear() + ".." + (band.toYear() == 9999 ? "present" : band.toYear())
                        + " for " + rank.name().toLowerCase(java.util.Locale.ROOT)
                        + " (" + min + "-" + max + "), deterministic pick — a presence, not a measurement");
    }

    private record ResolvedAnchor(long value, String why) {}

    private Optional<ResolvedAnchor> anchorValue(Region r, int year) {
        Optional<SeriesPoint> exact = exactPoint(r, year);
        if (exact.isPresent()) {
            return Optional.of(new ResolvedAnchor(exact.get().population(), "sourced " + year));
        }
        Optional<Result> interp = interpolate(r, year);
        if (interp.isPresent()) {
            return Optional.of(new ResolvedAnchor(interp.get().population(), interp.get().confidence().label()));
        }
        return Optional.empty();
    }

    private Band bandFor(int year) {
        for (Band b : bands) {
            if (year >= b.fromYear() && year < b.toYear()) return b;
        }
        return bands.isEmpty()
                ? new Band(Integer.MIN_VALUE, Integer.MAX_VALUE, 200, 1500, 5000, 40000, 80000, 5000000)
                : bands.get(bands.size() - 1);
    }

    private double unitsFor(SettlementRank rank) {
        if (rank == SettlementRank.CITY) return unitsCity;
        if (rank == SettlementRank.TOWN) return unitsTown;
        return unitsVillage;
    }

    /**
     * Stable 0..999 from the place identity and year. Not {@code Objects.hash}: this must be
     * reproducible across JVM versions and machines, so the mix is written out explicitly.
     */
    private static long deterministicFraction(String siteId, double lat, double lon, int year) {
        long h = 0L;
        if (siteId != null && !siteId.isBlank()) {
            for (int i = 0; i < siteId.length(); i++) h = 31L * h + siteId.charAt(i);
        } else {
            h = Math.round(lat * 10000.0d) * 31L + Math.round(lon * 10000.0d);
        }
        h = 31L * h + year;
        return Math.floorMod(h, 1000L);
    }

    private static long logLerp(long a, long b, double t) {
        if (a <= 0L || b <= 0L) {
            return Math.max(1L, Math.round(a + (b - a) * t));
        }
        return Math.max(1L, Math.round(a * Math.pow((double) b / (double) a, t)));
    }

    /** Warn in the source string when the two endpoints are not the same footprint. */
    private String footprintNote(Region r, SeriesPoint lo, SeriesPoint hi) {
        if (lo.year() < 1960 && hi.year() >= 2000) {
            return " — NOTE: endpoints have different boundary footprints, treat as approximate";
        }
        return "";
    }

    // ==================== loading ====================

    public int regionCount() { return regions.size(); }
    public int loadErrors()  { return loadErrors; }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        boolean world = false;
        try {
            JsonObject index = null;
            Path worldIndex = sourceDir == null ? null : sourceDir.resolve("index.json");
            if (worldIndex != null && Files.isRegularFile(worldIndex)) {
                try (Reader reader = Files.newBufferedReader(worldIndex, StandardCharsets.UTF_8)) {
                    index = JsonParser.parseReader(reader).getAsJsonObject();
                    world = true;
                } catch (Exception e) {
                    LOGGER.warn("[PopulationResolver] World pack index unreadable ({}); using the jar copy",
                            e.getMessage());
                }
            }
            if (index == null) {
                try (InputStream in = PopulationResolver.class.getResourceAsStream(INDEX)) {
                    if (in == null) {
                        LOGGER.warn("[PopulationResolver] No population index at {} — floor bands only", INDEX);
                        return;
                    }
                    index = JsonParser.parseReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
            JsonArray packs = index.getAsJsonArray("packs");
            if (packs != null) {
                for (JsonElement el : packs) {
                    loadPack(el.getAsString());
                }
            }
        } catch (Exception e) {
            LOGGER.error("[PopulationResolver] Failed to read index: {}", e.getMessage());
        }
        // A world copy that produced nothing is worse than no world copy: it would leave the
        // resolver on floor bands only. Fall back and say so.
        if (world && regions.isEmpty()) {
            LOGGER.warn("[PopulationResolver] World pack gave 0 regions — falling back to the jar copy");
            sourceDir = null;
            loaded = false;
            ensureLoaded();
            return;
        }
        loadedFromWorld = world;
        LOGGER.info("[PopulationResolver] Loaded {} region(s), {} band(s), {} error(s) from {}",
                regions.size(), bands.size(), loadErrors, loadedFrom());
    }

    /** One pack, from the world folder when there is one, otherwise from the jar. */
    private void loadPack(String name) {
        try {
            JsonObject pack;
            if (sourceDir != null && loadedFromWorld) {
                Path file = sourceDir.resolve(name);
                if (!Files.isRegularFile(file)) {
                    LOGGER.warn("[PopulationResolver] Missing world pack {}", file);
                    loadErrors++;
                    return;
                }
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    pack = JsonParser.parseReader(reader).getAsJsonObject();
                }
            } else {
                try (InputStream in = PopulationResolver.class.getResourceAsStream(PACK_PREFIX + name)) {
                    if (in == null) {
                        LOGGER.warn("[PopulationResolver] Missing pack {}", PACK_PREFIX + name);
                        loadErrors++;
                        return;
                    }
                    pack = JsonParser.parseReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
            loadCalibration(pack);
            loadBands(pack);
            loadRegions(pack);
        } catch (Exception e) {
            LOGGER.error("[PopulationResolver] Failed to load {}: {}", name, e.getMessage());
            loadErrors++;
        }
    }

    private void loadCalibration(JsonObject pack) {
        if (!pack.has("calibration") || !pack.get("calibration").isJsonObject()) return;
        JsonObject cal = pack.getAsJsonObject("calibration");
        if (cal.has("gravityDecayKm")) gravityDecayKm = cal.get("gravityDecayKm").getAsDouble();
        if (cal.has("gravityRadiusKm")) gravityRadiusKm = cal.get("gravityRadiusKm").getAsDouble();
        if (cal.has("maxInterpolationGapYears")) maxInterpolationGapYears = cal.get("maxInterpolationGapYears").getAsInt();
        if (cal.has("rankUnits") && cal.get("rankUnits").isJsonObject()) {
            JsonObject u = cal.getAsJsonObject("rankUnits");
            if (u.has("village")) unitsVillage = u.get("village").getAsDouble();
            if (u.has("town")) unitsTown = u.get("town").getAsDouble();
            if (u.has("city")) unitsCity = u.get("city").getAsDouble();
        }
    }

    private void loadBands(JsonObject pack) {
        if (!pack.has("defaults") || !pack.get("defaults").getAsJsonObject().has("bands")) return;
        JsonArray arr = pack.getAsJsonObject("defaults").getAsJsonArray("bands");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject b = el.getAsJsonObject();
            bands.add(new Band(
                    optInt(b, "fromYear", Integer.MIN_VALUE),
                    optInt(b, "toYear", Integer.MAX_VALUE),
                    pair(b, "village", 0), pair(b, "village", 1),
                    pair(b, "town", 0), pair(b, "town", 1),
                    pair(b, "city", 0), pair(b, "city", 1)));
        }
        bands.sort(Comparator.comparingInt(Band::fromYear));
    }

    private void loadRegions(JsonObject pack) {
        if (!pack.has("regions")) return;
        for (JsonElement el : pack.getAsJsonArray("regions")) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            List<SeriesPoint> series = new ArrayList<>();
            if (r.has("series") && r.get("series").isJsonArray()) {
                for (JsonElement se : r.getAsJsonArray("series")) {
                    JsonObject s = se.getAsJsonObject();
                    String source = s.has("source") ? s.get("source").getAsString() : "";
                    // Attribution is mandatory on a sourced row. An unattributed row is rejected
                    // rather than silently trusted, mirroring validateChains() on event chains.
                    if (source.isBlank()) {
                        LOGGER.error("[PopulationResolver] Rejected unattributed row: region={} year={}",
                                optStr(r, "id", "?"), optInt(s, "year", 0));
                        loadErrors++;
                        continue;
                    }
                    series.add(new SeriesPoint(optInt(s, "year", 0),
                            s.has("population") ? s.get("population").getAsLong() : 0L, source));
                }
            }
            series.sort(Comparator.comparingInt(SeriesPoint::year));
            regions.add(new Region(
                    optStr(r, "id", "unknown"),
                    optStr(r, "name", optStr(r, "id", "unknown")),
                    optStr(r, "kind", "settlement"),
                    SettlementRank.parse(optStr(r, "rank", "village")),
                    r.has("lat") ? r.get("lat").getAsDouble() : 0.0d,
                    r.has("lon") ? r.get("lon").getAsDouble() : 0.0d,
                    r.has("radiusKm") ? r.get("radiusKm").getAsDouble() : 10.0d,
                    optInt(r, "settlementCount", 0),
                    r.has("startYear") ? r.get("startYear").getAsInt() : null,
                    r.has("endYear") ? r.get("endYear").getAsInt() : null,
                    List.copyOf(series)));
        }
    }

    private static long pair(JsonObject band, String key, int index) {
        if (!band.has(key) || !band.get(key).isJsonArray()) return 1L;
        JsonArray a = band.getAsJsonArray(key);
        return index < a.size() ? a.get(index).getAsLong() : 1L;
    }

    private static int optInt(JsonObject o, String key, int fallback) {
        try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : fallback; }
        catch (Exception e) { return fallback; }
    }

    private static String optStr(JsonObject o, String key, String fallback) {
        try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback; }
        catch (Exception e) { return fallback; }
    }
}

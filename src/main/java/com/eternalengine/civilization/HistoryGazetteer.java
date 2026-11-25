package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Loads sourced history packs (sites + year ranges). Not template events.
 * <p>
 * Missing {@code occupiedStart} is never the beginning of time.
 * Undated living names get an <em>estimated</em> start (gravity + diffusion + parent box).
 */
public final class HistoryGazetteer {

    private static final String INDEX = "/data/eternalengine/eternal/history/index.json";
    private static final HistoryGazetteer INSTANCE = new HistoryGazetteer();

    private final List<Site> sites = new ArrayList<>();
    private final List<Trait> traits = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Border> borders = new ArrayList<>();
    private double hinterlandKm = 80.0d;
    private double gravityKm = 250.0d;
    /**
     * Packs that declare {@code "estimatorAnchor": false}: their dated places are towns on the map
     * like any other, but the settlement-year estimator does not pull undated places toward them.
     * The world-fill packs (Pleiades, CHGIS, Reba) say this so that adding them leaves the curated
     * estimates for the present-day Telangana places exactly as they were.
     */
    private final Set<String> nonAnchorPacks = new java.util.HashSet<>();
    /** Sites the estimator may pull from, in load order; filled at the end of the load. */
    private List<Site> anchorSites = List.of();
    /**
     * Event pins by year, each array in load order; built once at the end of the load. A lookup
     * then scans only the pins active in that year (a few thousand at most) instead of all of
     * them, with exactly the same answer: same filter, same order, same tie-breaking.
     * Null means "scan the whole list" (used if some pin spans more than {@link #INDEX_MAX_SPAN}).
     */
    private java.util.Map<Integer, Event[]> eventsByYear;
    private static final int INDEX_MAX_SPAN = 2000;
    private boolean loaded;

    private HistoryGazetteer() {}

    public static HistoryGazetteer get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public double hinterlandKm() {
        return hinterlandKm;
    }

    public double gravityKm() {
        return gravityKm;
    }

    /**
     * Kilometres per degree of latitude on the sphere {@link HistoricalPresence#haversineKm} uses
     * (R = 6371 km). A great-circle distance is never shorter than its north-south component,
     * R x |dLat|, so a point whose latitude alone is farther away than the search radius can be
     * skipped before the haversine without changing any answer. With tens of thousands of dated
     * places loaded, this is what keeps the linear scans below (and the settlement-year estimator
     * at load) cheap: almost every row is rejected by one subtraction.
     */
    static final double KM_PER_DEGREE_LATITUDE = 6371.0d * Math.PI / 180.0d;

    /** True when latitude alone puts the two points more than {@code km} apart. */
    static boolean beyondByLatitude(double lat1, double lat2, double km) {
        return Math.abs(lat1 - lat2) * KM_PER_DEGREE_LATITUDE > km + 1e-6d;
    }

    /** Gravity + diffusion + parent box. {@link SettlementYearEstimator#UNKNOWN} if nothing pulls. */
    public int estimateYear(double lat, double lon, SettlementRank rank) {
        ensureLoaded();
        return SettlementYearEstimator.estimate(lat, lon, rank, anchorSites, borders, gravityKm);
    }

    public record Site(
            String packId,
            String id,
            String name,
            double lat,
            double lon,
            int occupiedStart,
            int occupiedEnd,
            Integer urbanStart,
            Integer urbanEnd,
            String rankOverride,
            String civHint,
            double radiusKm,
            String confidence,
            boolean presentOnly,
            List<String> partOf
    ) {
        public boolean occupiedIn(int year) {
            return year >= occupiedStart && year < occupiedEnd;
        }

        public String displayName() {
            return name == null || name.isBlank() ? id : name;
        }

        /**
         * Village if occupied but not yet/no longer urban; city in urban years.
         * Explicit rank= on the JSON wins. Famous sourced sites with no urban window default to city.
         * Present-only rows default to village unless rank is set.
         */
        public SettlementRank rankAt(int year) {
            if (!occupiedIn(year)) {
                return SettlementRank.VILLAGE;
            }
            SettlementRank forced = SettlementRank.parse(rankOverride);
            if (forced != null) {
                return forced;
            }
            if (presentOnly) {
                return SettlementRank.VILLAGE;
            }
            if (urbanStart != null && urbanEnd != null) {
                if (year >= urbanStart && year < urbanEnd) {
                    return SettlementRank.CITY;
                }
                return SettlementRank.VILLAGE;
            }
            return SettlementRank.CITY;
        }
    }

    public record Hit(Site site, double distanceKm) {}

    /**
     * A short, sourced incident. Not a town. Does not found a city.
     * {@code effect} is civilian only: note, lockdown, memorial, disruption, military_presence.
     * Never used to spawn attackers.
     */
    public record Event(
            String packId,
            String id,
            String name,
            double lat,
            double lon,
            int start,
            int end,
            double radiusKm,
            String kind,
            String effect,
            String confidence
    ) {
        public boolean activeIn(int year) {
            return year >= start && year < end;
        }
    }

    public record EventHit(Event event, double distanceKm) {}

    public record Trait(String id, String kind, int from, int until, List<String> siteIds) {
        public boolean activeOn(String siteId, int year) {
            if (year < from || year >= until) {
                return false;
            }
            return siteIds == null || siteIds.isEmpty() || siteIds.contains(siteId);
        }
    }

    public record Border(
            String id,
            String civId,
            int start,
            int end,
            String kind,
            double minLat,
            double maxLat,
            double minLon,
            double maxLon,
            SettlementRank rank
    ) {
        public boolean contains(double lat, double lon, int year) {
            if (year < start || year >= end) {
                return false;
            }
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }
    }

    public Optional<Hit> nearestOccupied(double lat, double lon, int year) {
        Hit best = null;
        int bestPri = 99;
        for (Site site : sites) {
            if (!site.occupiedIn(year) || beyondByLatitude(lat, site.lat(), site.radiusKm())) {
                continue;
            }
            double km = HistoricalPresence.haversineKm(lat, lon, site.lat(), site.lon());
            if (km > site.radiusKm()) {
                continue;
            }
            int pri = occupancyPriority(site);
            if (best == null || pri < bestPri || (pri == bestPri && km < best.distanceKm())) {
                best = new Hit(site, km);
                bestPri = pri;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Sourced JSON years beat estimated OSM nicknames when both cover the player. */
    private static int occupancyPriority(Site site) {
        if (site.presentOnly() || "estimated".equalsIgnoreCase(site.confidence())) {
            return 1;
        }
        return 0;
    }

    /**
     * Gravity / expansion hinterland: unnamed countryside near a dated city or town.
     * Does not name the player's hamlet. Parent boxes still supply civ if this is empty.
     */
    public Optional<Hit> estimatedHinterland(double lat, double lon, int year) {
        Hit best = null;
        for (Site site : sites) {
            if (site.presentOnly()) {
                continue;
            }
            if (!site.occupiedIn(year) || beyondByLatitude(lat, site.lat(), hinterlandKm)) {
                continue;
            }
            SettlementRank rank = site.rankAt(year);
            if (rank == SettlementRank.VILLAGE) {
                continue;
            }
            double km = HistoricalPresence.haversineKm(lat, lon, site.lat(), site.lon());
            if (km > hinterlandKm) {
                continue;
            }
            int lag = 20 + (int) Math.round(km * 0.5d);
            if (year < site.occupiedStart() + lag) {
                continue;
            }
            if (best == null || km < best.distanceKm()) {
                best = new Hit(site, km);
            }
        }
        return Optional.ofNullable(best);
    }

    public int siteCount() {
        return sites.size();
    }

    public int eventCount() {
        return events.size();
    }

    /**
     * Every event pin, for building indexes over the whole corpus.
     * Callers must not mutate; the list is the gazetteer's own storage.
     */
    public List<Event> allEvents() {
        return java.util.Collections.unmodifiableList(events);
    }

    /** Every site, for building indexes over the whole corpus. */
    public List<Site> allSites() {
        return java.util.Collections.unmodifiableList(sites);
    }

    /** Every border box, so callers can reconcile the civ table against what the corpus cites. */
    public List<Border> allBorders() {
        return java.util.Collections.unmodifiableList(borders);
    }

    /**
     * Optional branch filter over pin ids.
     *
     * <p>Set by the timeline system so a suppressed pin stops rendering without this package
     * depending on {@code timeline} — the gazetteer is loaded long before any branch exists, and
     * the history corpus has no business knowing about divergence.
     *
     * <p>This single hook is what makes the event overlay branch-aware. Because
     * {@code PresenceLocalSpawner} already despawns the whole event scene when no event is present,
     * hiding the pin here is sufficient to empty the square: no crowd, no guards, no rumour, no
     * memorial. The absence is the consequence.
     */
    private static volatile java.util.function.Predicate<String> suppressionFilter = id -> false;

    /** Install (or clear, with {@code null}) the active-branch suppression filter. */
    public static void setSuppressionFilter(java.util.function.Predicate<String> filter) {
        suppressionFilter = filter == null ? id -> false : filter;
    }

    public static java.util.function.Predicate<String> suppressionFilter() {
        return suppressionFilter;
    }

    /** True if this pin is visible in the active branch. */
    public static boolean isPinVisible(String pinId) {
        return !suppressionFilter.test(pinId);
    }

    /**
     * Confidence of a pin that stands for a whole region, state or country rather than a spot:
     * a Wikipedia year-page line that names no exact place ("plague reaches France"), placed by
     * worldfill/build_regional.py at the region, or at the state's extent in that year.
     */
    public static final String REGIONAL_CONFIDENCE = "wikipedia_region";

    /** True for a regional pin: news heard across a region, never a local event to stand in. */
    public static boolean isRegional(Event e) {
        // step 28: also "wikidata_region", a natural event placed only by its country
        return e != null && e.confidence() != null && (REGIONAL_CONFIDENCE.equals(e.confidence())
                || e.confidence().endsWith("_region"));
    }

    /**
     * Nearest event pin containing this point in this year.
     *
     * <p>Local pins come first. A regional pin ({@link #isRegional}) is returned only when no
     * local pin contains the point, so country-wide news never hides a battle down the road.
     *
     * <p>Suppressed pins are skipped: in a branch where the event was prevented, it must not be
     * found, or the overlay will keep rendering the version of history the player undid.
     */
    public Optional<EventHit> nearestEvent(double lat, double lon, int year) {
        return nearestEvent(lat, lon, year, suppressionFilter);
    }

    /**
     * Step 26 (A1, the key): the same lookup for one timeline. {@code hidden} says which pins that timeline
     * prevented ({@link com.eternalengine.core.WorldQuery} asks the timeline's own suppression list), so a question
     * about the main timeline can be answered while a branch is active, and the other way round.
     */
    public Optional<EventHit> nearestEvent(double lat, double lon, int year,
                                           java.util.function.Predicate<String> hidden) {
        Optional<EventHit> local = nearest(lat, lon, year, false, hidden);
        return local.isPresent() ? local : nearest(lat, lon, year, true, hidden);
    }

    /**
     * Nearest local pin only: what a player can actually be standing in. Regional pins are
     * news from a whole region or country; nobody can stop them from one spot.
     */
    public Optional<EventHit> nearestLocalEvent(double lat, double lon, int year) {
        return nearest(lat, lon, year, false, suppressionFilter);
    }

    private Optional<EventHit> nearest(double lat, double lon, int year, boolean regional,
                                       java.util.function.Predicate<String> hidden) {
        EventHit best = null;
        for (Event e : eventsActiveIn(year)) {
            if (isRegional(e) != regional || !e.activeIn(year)
                    || beyondByLatitude(lat, e.lat(), e.radiusKm())
                    || hidden.test(e.id())) {
                continue;
            }
            double km = HistoricalPresence.haversineKm(lat, lon, e.lat(), e.lon());
            if (km <= e.radiusKm() && (best == null || km < best.distanceKm())) {
                best = new EventHit(e, km);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Event pins within {@code km} of a point in a year, skipped by branch suppression. */
    public List<EventHit> eventsNear(double lat, double lon, int year, double km) {
        List<EventHit> out = new ArrayList<>();
        for (Event e : eventsActiveIn(year)) {
            if (!e.activeIn(year) || beyondByLatitude(lat, e.lat(), km)
                    || suppressionFilter.test(e.id())) {
                continue;
            }
            double d = HistoricalPresence.haversineKm(lat, lon, e.lat(), e.lon());
            if (d <= km) {
                out.add(new EventHit(e, d));
            }
        }
        return List.copyOf(out);
    }

    public List<Trait> traitsFor(String siteId, int year) {
        List<Trait> out = new ArrayList<>();
        for (Trait t : traits) {
            if (t.activeOn(siteId, year)) {
                out.add(t);
            }
        }
        return List.copyOf(out);
    }

    /** Rank roles plus trait jobs (dock → merchant, brick/drain → artisan). */
    public String[] spawnRoles(Site site, int year) {
        Set<String> roles = new LinkedHashSet<>();
        for (String r : site.rankAt(year).roles()) {
            roles.add(r);
        }
        for (Trait t : traitsFor(site.id(), year)) {
            String key = (t.kind() + " " + t.id()).toLowerCase(java.util.Locale.ROOT);
            if (containsAny(key, "dock", "trade", "weight", "caravan", "maritime", "meluhha", "bead")) {
                roles.add("merchant");
            }
            if (containsAny(key, "brick", "drain", "pyramid", "ziggurat", "palace", "bath",
                    "granary", "architecture", "reservoir", "wall", "stupa", "minaret", "ashlar")) {
                roles.add("artisan");
            }
            if (containsAny(key, "temple", "religious", "priest", "hieroglyph")) {
                roles.add("priest");
            }
        }
        return roles.toArray(String[]::new);
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = HistoryGazetteer.class.getResourceAsStream(INDEX)) {
            if (in != null) {
                JsonObject index = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                if (index.has("estimatedHinterlandKm")) {
                    hinterlandKm = index.get("estimatedHinterlandKm").getAsDouble();
                }
                if (index.has("estimatedGravityKm")) {
                    gravityKm = index.get("estimatedGravityKm").getAsDouble();
                }
                JsonArray packs = index.getAsJsonArray("packs");
                if (packs != null) {
                    for (JsonElement el : packs) {
                        loadPack("/data/eternalengine/eternal/history/" + el.getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
            // Packs are optional at runtime.
        }
        loadBorders("/data/eternalengine/eternal/history/borders.json");
        resolveEstimatedYears();
        buildEventIndex();
    }

    private void buildEventIndex() {
        java.util.Map<Integer, List<Event>> byYear = new java.util.HashMap<>();
        for (Event e : events) {
            if ((long) e.end() - (long) e.start() > INDEX_MAX_SPAN) {
                eventsByYear = null;
                return;
            }
            for (int y = e.start(); y < e.end(); y++) {
                byYear.computeIfAbsent(y, k -> new ArrayList<>()).add(e);
            }
        }
        java.util.Map<Integer, Event[]> out = new java.util.HashMap<>(byYear.size() * 2);
        byYear.forEach((y, list) -> out.put(y, list.toArray(new Event[0])));
        eventsByYear = out;
    }

    /** The pins that can be active in this year, in load order. */
    private List<Event> eventsActiveIn(int year) {
        java.util.Map<Integer, Event[]> index = eventsByYear;
        if (index == null) {
            return events;
        }
        Event[] arr = index.get(year);
        return arr == null ? List.of() : java.util.Arrays.asList(arr);
    }

    private void resolveEstimatedYears() {
        // The estimator only ever pulls from sourced, non-present-only sites inside the gravity
        // radius. Index those once by latitude and hand each estimate only the anchors in its
        // latitude band, in their original order -- the same rows the estimator would have kept
        // from the full list, visited in the same order, so every estimated year is unchanged.
        List<Site> anchors = new ArrayList<>();
        for (Site s : sites) {
            if (!s.presentOnly() && !"estimated".equalsIgnoreCase(s.confidence())
                    && !nonAnchorPacks.contains(s.packId())) {
                anchors.add(s);
            }
        }
        anchorSites = List.copyOf(anchors);
        Integer[] order = new Integer[anchors.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(i -> anchors.get(i).lat()));
        double[] lats = new double[order.length];
        for (int i = 0; i < order.length; i++) {
            lats[i] = anchors.get(order[i]).lat();
        }
        double band = gravityKm / KM_PER_DEGREE_LATITUDE + 1e-6d;

        List<Site> next = new ArrayList<>(sites.size());
        for (Site s : sites) {
            if (s.occupiedStart() != SettlementYearEstimator.UNKNOWN) {
                next.add(s);
                continue;
            }
            SettlementRank rank = SettlementRank.parse(s.rankOverride());
            if (rank == null) {
                rank = SettlementRank.VILLAGE;
            }
            int from = lowerBound(lats, s.lat() - band);
            int to = lowerBound(lats, Math.nextUp(s.lat() + band));
            int[] picked = new int[Math.max(0, to - from)];
            for (int i = from; i < to; i++) {
                picked[i - from] = order[i];
            }
            java.util.Arrays.sort(picked);
            List<Site> near = new ArrayList<>(picked.length);
            for (int i : picked) {
                near.add(anchors.get(i));
            }
            int start = SettlementYearEstimator.estimate(
                    s.lat(), s.lon(), rank, near, borders, gravityKm);
            if (start == SettlementYearEstimator.UNKNOWN) {
                continue;
            }
            next.add(new Site(
                    s.packId(), s.id(), s.name(), s.lat(), s.lon(),
                    start, s.occupiedEnd(), s.urbanStart(), s.urbanEnd(),
                    s.rankOverride(), s.civHint(), s.radiusKm(),
                    "estimated", true, s.partOf()));
        }
        sites.clear();
        sites.addAll(next);
    }

    /** First index whose value is >= key (values sorted ascending). */
    private static int lowerBound(double[] values, double key) {
        int lo = 0;
        int hi = values.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (values[mid] < key) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * The tightest border box covering this point in this year.
     *
     * <p>Returning the <i>first</i> box that matched made the answer depend on the order rows
     * happen to sit in {@code borders.json}. For a point in Hyderabad in 1948 two boxes match:
     * {@code india} (8..35.5 N, 68..97 E -- 798 square degrees) and {@code hyderabad_state}
     * (15.5..19.8 N, 76.5..81.5 E -- 22 square degrees). First-match picked whichever row came
     * first; smallest-area picks the specific claim, which is the one that is actually about this
     * ground. Ties fall back to file order, so the result stays deterministic.
     */
    public Optional<Border> borderAt(double lat, double lon, int year) {
        Border best = null;
        double bestArea = Double.MAX_VALUE;
        for (Border b : borders) {
            if (!b.contains(lat, lon, year)) {
                continue;
            }
            double area = (b.maxLat() - b.minLat()) * (b.maxLon() - b.minLon());
            if (area < bestArea) {
                bestArea = area;
                best = b;
            }
        }
        return Optional.ofNullable(best);
    }

    public int borderCount() {
        return borders.size();
    }

    private void loadBorders(String resource) {
        try (InputStream in = HistoryGazetteer.class.getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("borders");
            if (arr == null) {
                return;
            }
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                SettlementRank rank = SettlementRank.parse(o.has("rank") ? o.get("rank").getAsString() : "village");
                if (rank == null) {
                    rank = SettlementRank.VILLAGE;
                }
                borders.add(new Border(
                        o.has("id") ? o.get("id").getAsString() : "border",
                        o.has("civId") ? o.get("civId").getAsString() : "neutral",
                        o.has("start") ? o.get("start").getAsInt() : Integer.MIN_VALUE,
                        o.has("end") ? o.get("end").getAsInt() : Integer.MAX_VALUE,
                        o.has("kind") ? o.get("kind").getAsString() : "box",
                        o.has("minLat") ? o.get("minLat").getAsDouble() : -90,
                        o.has("maxLat") ? o.get("maxLat").getAsDouble() : 90,
                        o.has("minLon") ? o.get("minLon").getAsDouble() : -180,
                        o.has("maxLon") ? o.get("maxLon").getAsDouble() : 180,
                        rank
                ));
            }
        } catch (Exception ignored) {
            // Optional.
        }
    }

    private void loadPack(String resource) {
        try (InputStream in = HistoryGazetteer.class.getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            // A pack listed as "name.json.gz" is read through gzip: the large generated packs are
            // stored compressed so the repository stays small; the jar compresses either way.
            InputStream src = resource.endsWith(".gz") ? new java.util.zip.GZIPInputStream(in, 1 << 16) : in;
            JsonObject pack = JsonParser.parseReader(new InputStreamReader(src, StandardCharsets.UTF_8)).getAsJsonObject();
            String packId = pack.has("id") ? pack.get("id").getAsString() : "unknown";
            boolean packPresentOnly = pack.has("presentOnly") && pack.get("presentOnly").getAsBoolean();
            if (pack.has("estimatorAnchor") && !pack.get("estimatorAnchor").getAsBoolean()) {
                nonAnchorPacks.add(packId);
            }
            double radius = pack.has("spawnRadiusKm") ? pack.get("spawnRadiusKm").getAsDouble() : 30.0d;
            loadTraits(pack);
            loadEvents(pack, packId);
            JsonArray arr = pack.getAsJsonArray("sites");
            if (arr == null) {
                return;
            }
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject s = el.getAsJsonObject();
                if (!s.has("lat") || !s.has("lon")) {
                    continue;
                }
                boolean presentOnly = packPresentOnly
                        || (s.has("confidence") && "present_only".equalsIgnoreCase(s.get("confidence").getAsString()))
                        || (s.has("presentOnly") && s.get("presentOnly").getAsBoolean());
                Integer start = optionalInt(s, "occupiedStart");
                if (start == null) {
                    if (!presentOnly) {
                        continue;
                    }
                    start = SettlementYearEstimator.UNKNOWN;
                }
                int end = s.has("occupiedEnd") ? s.get("occupiedEnd").getAsInt() : Integer.MAX_VALUE;
                List<String> partOf = new ArrayList<>();
                if (s.has("partOf") && s.get("partOf").isJsonArray()) {
                    for (JsonElement p : s.getAsJsonArray("partOf")) {
                        partOf.add(p.getAsString());
                    }
                }
                String confidence = s.has("confidence") ? s.get("confidence").getAsString()
                        : (presentOnly ? "estimated" : "sourced");
                String id = s.has("id") ? s.get("id").getAsString() : "site";
                String name = s.has("name") ? s.get("name").getAsString() : id;
                sites.add(new Site(
                        packId,
                        id,
                        name,
                        s.get("lat").getAsDouble(),
                        s.get("lon").getAsDouble(),
                        start,
                        end,
                        optionalInt(s, "urbanStart"),
                        optionalInt(s, "urbanEnd"),
                        s.has("rank") && !s.get("rank").isJsonNull() ? s.get("rank").getAsString() : null,
                        s.has("civHint") ? s.get("civHint").getAsString() : packId,
                        radius,
                        confidence,
                        presentOnly,
                        List.copyOf(partOf)
                ));
            }
        } catch (Exception ignored) {
            // Skip broken pack.
        }
    }

    private void loadEvents(JsonObject pack, String packId) {
        JsonArray arr = pack.getAsJsonArray("events");
        if (arr == null) {
            return;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            if (!o.has("lat") || !o.has("lon")) {
                continue;
            }
            Integer year = optionalInt(o, "year");
            Integer start = optionalInt(o, "start");
            Integer end = optionalInt(o, "end");
            if (start == null) {
                start = year;
            }
            if (start == null) {
                continue;
            }
            if (end == null) {
                end = start + 1;
            }
            if (end <= start) {
                end = start + 1;
            }
            String effect = o.has("effect") ? o.get("effect").getAsString() : "note";
            if (effect.toLowerCase(java.util.Locale.ROOT).contains("attack")
                    || effect.toLowerCase(java.util.Locale.ROOT).contains("terror")) {
                effect = "lockdown";
            }
            events.add(new Event(
                    packId,
                    o.has("id") ? o.get("id").getAsString() : "event",
                    o.has("name") ? o.get("name").getAsString() : "event",
                    o.get("lat").getAsDouble(),
                    o.get("lon").getAsDouble(),
                    start,
                    end,
                    o.has("radiusKm") ? o.get("radiusKm").getAsDouble() : 25.0d,
                    o.has("kind") ? o.get("kind").getAsString() : "event",
                    effect,
                    o.has("confidence") ? o.get("confidence").getAsString() : "high"
            ));
        }
    }

    private void loadTraits(JsonObject pack) {
        JsonArray arr = pack.getAsJsonArray("traits");
        if (arr == null) {
            return;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject t = el.getAsJsonObject();
            List<String> siteIds = new ArrayList<>();
            if (t.has("sites") && t.get("sites").isJsonArray()) {
                for (JsonElement s : t.getAsJsonArray("sites")) {
                    siteIds.add(s.getAsString());
                }
            }
            traits.add(new Trait(
                    t.has("id") ? t.get("id").getAsString() : "trait",
                    t.has("kind") ? t.get("kind").getAsString() : "",
                    t.has("from") ? t.get("from").getAsInt() : Integer.MIN_VALUE,
                    t.has("until") ? t.get("until").getAsInt() : Integer.MAX_VALUE,
                    List.copyOf(siteIds)
            ));
        }
    }

    private static Integer optionalInt(JsonObject s, String key) {
        if (!s.has(key) || s.get(key).isJsonNull()) {
            return null;
        }
        try {
            return s.get(key).getAsInt();
        } catch (Exception e) {
            return null;
        }
    }
}

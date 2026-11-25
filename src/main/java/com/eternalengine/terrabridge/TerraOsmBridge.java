package com.eternalengine.terrabridge;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.buildings.FamousBuildings;
import com.eternalengine.civilization.LandUseGrid;
import com.eternalengine.civilization.Minerals;
import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.civilization.WaterChanges;
import com.eternalengine.buildings.BuildingLife;
import com.eternalengine.buildings.PlayerEdits;
import com.eternalengine.civilization.Disasters;
import com.eternalengine.civilization.WaterWorks;
import com.eternalengine.civilization.WaysGrid;
import com.eternalengine.civilization.WaysWorks;
import com.eternalengine.climate.ClimatePeriods;
import com.eternalengine.sky.SkyEclipses;
import com.eternalengine.sky.SkyStars;
import com.eternalengine.config.EternalEngineConfig;
import com.eternalengine.technology.TechnologyAtlas;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge for accessing OpenStreetMap data via Terra++ or Overpass API.
 * 
 * Provides access to:
 * - Building footprints
 * - Roads and railways
 * - Historical structures
 * - Points of interest
 */
public class TerraOsmBridge {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static TerraOsmBridge instance;
    
    // Overpass API endpoint
    private static final String OVERPASS_API = "https://overpass-api.de/api/interpreter";
    
    // HTTP client for API requests
    private final HttpClient httpClient;
    
    // Cache for query results
    private final ConcurrentHashMap<String, CachedResult> queryCache;
    
    // Cache expiration time (5 minutes)
    private static final long CACHE_EXPIRATION_MS = 5 * 60 * 1000;
    
    private TerraOsmBridge() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.queryCache = new ConcurrentHashMap<>();
    }
    
    public static void initialize() {
        instance = new TerraOsmBridge();
        LOGGER.info("[TerraOsmBridge] Initialized");
    }
    
    public static TerraOsmBridge getInstance() {
        if (instance == null) {
            initialize();
        }
        return instance;
    }
    
    /**
     * Get structures within a bounding box
     * 
     * @param minLat Minimum latitude
     * @param minLon Minimum longitude
     * @param maxLat Maximum latitude
     * @param maxLon Maximum longitude
     * @param timeoutMs Timeout in milliseconds
     * @return List of OsmStructure, never null (empty on failure)
     */
    public List<OsmStructure> getStructuresForRegion(double minLat, double minLon, 
                                                      double maxLat, double maxLon, 
                                                      int timeoutMs) {
        // Validate bounds
        if (!TerraCoordinateHelper.isValidCoordinates(minLat, minLon) ||
            !TerraCoordinateHelper.isValidCoordinates(maxLat, maxLon)) {
            LOGGER.debug("[TerraOsmBridge] Invalid bounds provided");
            return Collections.emptyList();
        }
        
        // Check cache
        String cacheKey = String.format("structures_%.4f_%.4f_%.4f_%.4f", minLat, minLon, maxLat, maxLon);
        CachedResult cached = queryCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.structures;
        }
        
        try {
            // Build Overpass query
            String query = buildStructuresQuery(minLat, minLon, maxLat, maxLon);
            
            // Execute query
            String response = executeOverpassQuery(query, timeoutMs);
            if (response == null) {
                return Collections.emptyList();
            }
            
            // Parse results
            List<OsmStructure> structures = parseStructuresResponse(response);
            
            // Cache results
            queryCache.put(cacheKey, new CachedResult(structures));
            
            return structures;
            
        } catch (Exception e) {
            LOGGER.debug("[TerraOsmBridge] Failed to get structures: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get structures asynchronously
     */
    public CompletableFuture<List<OsmStructure>> getStructuresForRegionAsync(
            double minLat, double minLon, double maxLat, double maxLon) {
        int timeout = EternalEngineConfig.COMMON.osmTimeoutMs.get();
        return CompletableFuture.supplyAsync(() -> 
            getStructuresForRegion(minLat, minLon, maxLat, maxLon, timeout));
    }
    
    /**
     * Get historic structures (tagged with historic=*)
     */
    public List<OsmStructure> getHistoricStructures(double minLat, double minLon,
                                                     double maxLat, double maxLon) {
        int timeout = EternalEngineConfig.COMMON.osmTimeoutMs.get();
        
        try {
            String query = String.format(
                "[out:json][timeout:%d];" +
                "(" +
                "  way[\"historic\"](%.6f,%.6f,%.6f,%.6f);" +
                "  node[\"historic\"](%.6f,%.6f,%.6f,%.6f);" +
                "  relation[\"historic\"](%.6f,%.6f,%.6f,%.6f);" +
                ");" +
                "out body;>;out skel qt;",
                timeout / 1000,
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon,
                minLat, minLon, maxLat, maxLon
            );
            
            String response = executeOverpassQuery(query, timeout);
            if (response == null) {
                return Collections.emptyList();
            }
            
            return parseStructuresResponse(response);
            
        } catch (Exception e) {
            LOGGER.debug("[TerraOsmBridge] Failed to get historic structures: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get building footprints
     */
    public List<OsmStructure> getBuildingFootprints(double minLat, double minLon,
                                                     double maxLat, double maxLon) {
        int timeout = EternalEngineConfig.COMMON.osmTimeoutMs.get();
        
        try {
            String query = String.format(
                "[out:json][timeout:%d];" +
                "way[\"building\"](%.6f,%.6f,%.6f,%.6f);" +
                "out body;>;out skel qt;",
                timeout / 1000,
                minLat, minLon, maxLat, maxLon
            );
            
            String response = executeOverpassQuery(query, timeout);
            if (response == null) {
                return Collections.emptyList();
            }
            
            return parseStructuresResponse(response);
            
        } catch (Exception e) {
            LOGGER.debug("[TerraOsmBridge] Failed to get building footprints: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get roads
     */
    public List<OsmStructure> getRoads(double minLat, double minLon,
                                        double maxLat, double maxLon) {
        int timeout = EternalEngineConfig.COMMON.osmTimeoutMs.get();
        
        try {
            String query = String.format(
                "[out:json][timeout:%d];" +
                "way[\"highway\"](%.6f,%.6f,%.6f,%.6f);" +
                "out body;>;out skel qt;",
                timeout / 1000,
                minLat, minLon, maxLat, maxLon
            );
            
            String response = executeOverpassQuery(query, timeout);
            if (response == null) {
                return Collections.emptyList();
            }
            
            return parseStructuresResponse(response);
            
        } catch (Exception e) {
            LOGGER.debug("[TerraOsmBridge] Failed to get roads: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get railways
     */
    public List<OsmStructure> getRailways(double minLat, double minLon,
                                           double maxLat, double maxLon) {
        int timeout = EternalEngineConfig.COMMON.osmTimeoutMs.get();
        
        try {
            String query = String.format(
                "[out:json][timeout:%d];" +
                "way[\"railway\"](%.6f,%.6f,%.6f,%.6f);" +
                "out body;>;out skel qt;",
                timeout / 1000,
                minLat, minLon, maxLat, maxLon
            );
            
            String response = executeOverpassQuery(query, timeout);
            if (response == null) {
                return Collections.emptyList();
            }
            
            return parseStructuresResponse(response);
            
        } catch (Exception e) {
            LOGGER.debug("[TerraOsmBridge] Failed to get railways: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Build Overpass query for general structures
     */
    private String buildStructuresQuery(double minLat, double minLon, 
                                        double maxLat, double maxLon) {
        int timeout = EternalEngineConfig.COMMON.osmTimeoutMs.get() / 1000;
        
        return String.format(
            "[out:json][timeout:%d];" +
            "(" +
            "  way[\"building\"](%.6f,%.6f,%.6f,%.6f);" +
            "  way[\"historic\"](%.6f,%.6f,%.6f,%.6f);" +
            "  node[\"historic\"](%.6f,%.6f,%.6f,%.6f);" +
            "  way[\"amenity\"](%.6f,%.6f,%.6f,%.6f);" +
            ");" +
            "out body;>;out skel qt;",
            timeout,
            minLat, minLon, maxLat, maxLon,
            minLat, minLon, maxLat, maxLon,
            minLat, minLon, maxLat, maxLon,
            minLat, minLon, maxLat, maxLon
        );
    }
    
    /**
     * Execute an Overpass API query
     */
    @Nullable
    private String executeOverpassQuery(String query, int timeoutMs) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OVERPASS_API))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("data=" + query))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                LOGGER.debug("[TerraOsmBridge] Overpass returned status {}", response.statusCode());
                return null;
            }
            
            return response.body();
            
        } catch (Exception e) {
            LOGGER.debug("[TerraOsmBridge] Overpass query failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse Overpass JSON response into OsmStructure list
     */
    private List<OsmStructure> parseStructuresResponse(String jsonResponse) {
        List<OsmStructure> structures = new ArrayList<>();
        
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray elements = root.getAsJsonArray("elements");
            
            if (elements == null) {
                return structures;
            }
            
            for (JsonElement element : elements) {
                JsonObject obj = element.getAsJsonObject();
                OsmStructure structure = parseElement(obj);
                if (structure != null) {
                    structures.add(structure);
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("[TerraOsmBridge] Failed to parse response: {}", e.getMessage());
        }
        
        return structures;
    }
    
    /**
     * Parse a single OSM element
     */
    @Nullable
    private OsmStructure parseElement(JsonObject element) {
        try {
            String type = element.has("type") ? element.get("type").getAsString() : null;
            long id = element.has("id") ? element.get("id").getAsLong() : 0;
            
            if (type == null || id == 0) {
                return null;
            }
            
            OsmStructure structure = new OsmStructure();
            structure.osmId = id;
            structure.type = type;
            
            // Get coordinates for nodes
            if ("node".equals(type)) {
                if (element.has("lat") && element.has("lon")) {
                    structure.latitude = element.get("lat").getAsDouble();
                    structure.longitude = element.get("lon").getAsDouble();
                }
            }
            
            // Parse tags
            if (element.has("tags")) {
                JsonObject tags = element.getAsJsonObject("tags");
                
                if (tags.has("name")) {
                    structure.name = tags.get("name").getAsString();
                }
                if (tags.has("building")) {
                    structure.buildingType = tags.get("building").getAsString();
                }
                if (tags.has("historic")) {
                    structure.historicType = tags.get("historic").getAsString();
                }
                if (tags.has("start_date")) {
                    structure.startDate = tags.get("start_date").getAsString();
                }
                if (tags.has("wikidata")) {
                    structure.wikidataId = tags.get("wikidata").getAsString();
                }
                if (tags.has("heritage")) {
                    structure.heritageDesignation = tags.get("heritage").getAsString();
                }
            }
            
            return structure;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Clear the cache
     */
    public void clearCache() {
        queryCache.clear();
    }
    
    /**
     * OSM Structure data class
     */
    public static class OsmStructure {
        public long osmId;
        public String type; // node, way, relation
        public double latitude;
        public double longitude;
        public String name;
        public String buildingType;
        public String historicType;
        public String startDate;
        /** Step 34: the year it ended, where a source knows one; null where it is still standing. */
        public String endDate;
        public String wikidataId;
        public String heritageDesignation;
        public List<double[]> geometry; // For ways/relations
        
        public boolean hasHistoricData() {
            return historicType != null || startDate != null;
        }
        
        public boolean hasWikidata() {
            return wikidataId != null && !wikidataId.isEmpty();
        }
    }
    
    /**
     * Famous buildings standing here in that year (system B8, step 32).
     *
     * <p>This is the public hook between the map and the dated buildings. Terra++ draws the world
     * from OpenStreetMap, and step 31 measured what that means: 0.9% of mapped structures carry a
     * start date, so the rest fall to a regional population floor and today's skyline is drawn in
     * antiquity. This method is the dated answer, offered in Terra++'s own {@link OsmStructure}
     * shape so nothing downstream has to learn a second type.
     *
     * <p>These are not OSM structures and the caller must not treat them as one:
     * {@code osmId} is a hash of the name, {@code type} is {@code "famous"}, and the geometry is a
     * single point -- there is no footprint, because Wikidata gives a point and no outline. What
     * they carry that an OSM structure usually does not is a year: {@code startDate} is always set.
     *
     * <p>Reads are local and cached in memory by {@link FamousBuildings}; there is no network call
     * and no Overpass query here.
     *
     * @param lat  centre latitude
     * @param lon  centre longitude
     * @param year the year to ask about; only buildings standing in it are returned
     * @param km   radius in kilometres
     * @return famous buildings standing here then, most famous first; never null
     */
    public List<OsmStructure> getFamousBuildings(double lat, double lon, int year, double km) {
        List<OsmStructure> out = new ArrayList<>();
        FamousBuildings register = FamousBuildings.get();
        if (!register.loaded()) {
            LOGGER.warn("[TerraOsmBridge] famous buildings not available: {}", register.problem());
            return out;
        }
        for (FamousBuildings.Building b : register.near(lat, lon, year, km, Integer.MAX_VALUE)) {
            OsmStructure s = new OsmStructure();
            s.osmId = Math.abs(b.name().hashCode());   // not an OSM id; see the javadoc
            s.type = "famous";
            s.latitude = b.lat();
            s.longitude = b.lon();
            s.name = b.name();
            s.buildingType = b.kind();
            s.historicType = b.kind();
            s.startDate = String.valueOf(b.from());
            s.geometry = new ArrayList<>();
            s.geometry.add(new double[]{b.lon(), b.lat()});   // Terra++ order: [lon, lat]
            out.add(s);
        }
        return out;
    }

    /**
     * Whether a road, a railway or a canal may be drawn at this point in this year (systems B12,
     * B14, B15, step 34).
     *
     * <p><b>This is the gate.</b> Terra++ draws the world from today's OpenStreetMap, and step 31
     * measured what that costs: Sydney in 1850 was given two railways, though Australia's first
     * opened in 1854; Patna in 300 BC was given 98 modern roads. Only 0.9% of mapped structures
     * carry a start date, so everything else falls through to today's map. This method answers the
     * question the map should ask first: <i>did this kind of network exist here yet?</i>
     *
     * <p>Two things are consulted:
     * <ul>
     *   <li>{@link WaysGrid} — the year a railway, a dated road or a canal first reached this
     *       0.1-degree square, merged from OpenHistoricalMap's dated ways, the mod's Itiner-e Roman
     *       roads and Wikidata's dated works. <b>This is what decides.</b></li>
     *   <li>{@link TechnologyAtlas} — for a railway, a veto: a square cannot carry a railway before
     *       the railway (or the tram) has reached its country. For a road it is <i>not</i> a veto —
     *       people had roads long before they had paving — it answers the separate question
     *       {@link WaysAllowed#paved()}, whether the road may be drawn surfaced.</li>
     * </ul>
     *
     * <p>Where neither knows anything the answer is {@code false}, and that is deliberate: a missing
     * year is not permission to draw, it is absence. Ur, Mohenjo-daro, Angkor, Timbuktu and the
     * Sahara all answer {@code false} today, because no source that can be shipped says otherwise.
     *
     * @param lat  centre latitude
     * @param lon  centre longitude
     * @param year the year the player is standing in
     * @return what may be drawn here then, and the year each was first recorded
     */
    public WaysAllowed waysAllowed(double lat, double lon, int year) {
        WaysGrid grid = WaysGrid.get();
        int rail = grid.loaded() ? grid.railFirst(lat, lon) : WaysGrid.NONE;
        int road = grid.loaded() ? grid.roadFirst(lat, lon) : WaysGrid.NONE;
        int canal = grid.loaded() ? grid.canalFirst(lat, lon) : WaysGrid.NONE;
        boolean okRail = rail != WaysGrid.NONE && rail <= year;
        boolean okRoad = road != WaysGrid.NONE && road <= year;
        boolean okCanal = canal != WaysGrid.NONE && canal <= year;
        boolean paved = false;
        TechnologyAtlas atlas = TechnologyAtlas.get();
        if (atlas.loaded()) {
            okRail &= atlas.present("railway", lat, lon, year, SettlementRank.TOWN)
                    || atlas.present("tram", lat, lon, year, SettlementRank.TOWN);
            paved = atlas.present("paved_roads", lat, lon, year, SettlementRank.TOWN)
                    || atlas.present("tarmac", lat, lon, year, SettlementRank.TOWN)
                    || atlas.present("motorway", lat, lon, year, SettlementRank.TOWN);
        }
        return new WaysAllowed(okRail, okRoad, okCanal, paved, rail, road, canal);
    }

    /**
     * What may be drawn at a point in a year, and from when: the answer of {@link #waysAllowed}.
     *
     * @param rail     a railway may be drawn here in that year
     * @param road     a road may be drawn here in that year (surfaced only if {@code paved})
     * @param canal    a canal may be drawn here in that year
     * @param paved    the road may be drawn surfaced: paving, tarmac or motorway has reached here
     * @param railFrom the year a railway was first recorded in this square, or {@link WaysGrid#NONE}
     * @param roadFrom the year a road was first recorded in this square, or {@link WaysGrid#NONE}
     * @param canalFrom the year a canal was first recorded in this square, or {@link WaysGrid#NONE}
     */
    public record WaysAllowed(boolean rail, boolean road, boolean canal, boolean paved,
                              int railFrom, int roadFrom, int canalFrom) {

        /** Anything at all? Where this is false, the map should fall back to tracks and paths. */
        public boolean any() {
            return rail || road || canal;
        }
    }

    /**
     * The dated works standing here in that year — stations, bridges, canals, ports, lighthouses,
     * dams, factories, power stations, mills, shipyards, airports, mines, caravanserais (systems
     * B14, B15, B18, B26, step 34) — in Terra++'s own {@link OsmStructure} shape.
     *
     * <p>Every one carries a year, which is the whole point: Wikidata holds 172 railway stations
     * and 76 metro stations within 25 km of Rome, and all of them are dated after 1830, so Rome in
     * AD 100 is offered three bridges, two ports and a Roman road and nothing else. Where a work's
     * year is estimated (from the arrival of its technology in its own country) rather than recorded,
     * {@code historicType} ends in {@code " (estimated)"}.
     *
     * @param lat  centre latitude
     * @param lon  centre longitude
     * @param year the year to ask about; only works standing in it are returned
     * @param km   radius in kilometres
     * @return the works standing here then, best known first; never null
     */
    public List<OsmStructure> getDatedWorks(double lat, double lon, int year, double km) {
        List<OsmStructure> out = new ArrayList<>();
        WaysWorks register = WaysWorks.get();
        if (!register.loaded()) {
            LOGGER.warn("[TerraOsmBridge] dated works not available: {}", register.problem());
            return out;
        }
        for (WaysWorks.Work w : register.near(lat, lon, km, year)) {
            OsmStructure s = new OsmStructure();
            s.osmId = Math.abs((w.name() == null ? w.kind() : w.name()).hashCode());
            s.type = "work";
            s.latitude = w.lat();
            s.longitude = w.lon();
            s.name = w.name() == null ? w.kind() : w.name();
            s.buildingType = w.kind();
            s.historicType = w.kind() + (w.estimated() ? " (estimated)" : "");
            s.startDate = String.valueOf(w.start());
            if (w.end() != WaysWorks.NO_END) {
                s.endDate = String.valueOf(w.end());
            }
            s.geometry = new ArrayList<>();
            s.geometry.add(new double[]{w.lon(), w.lat()});   // Terra++ order: [lon, lat]
            out.add(s);
        }
        return out;
    }

    /**
     * What lies under the ground within {@code km} of a point (system B24, step 34): the minerals
     * of USGS's Mineral Resources Data System, the 122 famous deposits and coalfields written by
     * hand from Wikipedia, and Wikidata's oil fields.
     *
     * <p>The geology does not change, so this takes no year. Whether anyone could have <i>worked</i>
     * it in a given year is a different question and is {@link Minerals#worked}'s to answer.
     *
     * @return the mineral names, in no particular order; empty where no source records anything
     */
    public List<String> mineralsUnder(double lat, double lon, double km) {
        Minerals register = Minerals.get();
        if (!register.loaded()) {
            LOGGER.warn("[TerraOsmBridge] minerals not available: {}", register.problem());
            return Collections.emptyList();
        }
        return new ArrayList<>(register.mineralsUnder(lat, lon, km));
    }

    // ---------------------------------------------------------------- step 35: land, water, climate, sky

    /**
     * What the land was used for at a point in a year (system B3, step 35) — the anthromes of
     * Ellis, Beusen and Klein Goldewijk at 5 arcminutes, in 73 steps from 10,000 BCE to 2015 CE.
     *
     * <p>This is the hook for "may I put a field / a village / a forest here?". The answer is the
     * anthrome's own name ("rice villages", "wild woodlands", "irrigated cropland"), its level
     * ("villages", "cropland", "wild", "ice", "town"), and a short word for it ("town", "ice",
     * "wild", "fields"). A place the grid calls nothing at all — the open sea — answers
     * {@code null}.
     *
     * @param lat  centre latitude
     * @param lon  centre longitude
     * @param year the year the player is standing in
     * @return the class of the land, or null where the register is missing or the point is water
     */
    @Nullable
    public LandUse landUseAt(double lat, double lon, int year) {
        LandUseGrid grid = LandUseGrid.get();
        if (!grid.loaded()) {
            LOGGER.warn("[TerraOsmBridge] land use not available: {}", grid.problem());
            return null;
        }
        LandUseGrid.LandClass c = grid.landClass(lat, lon, year);
        if (c == null) {
            return null;
        }
        return new LandUse(c.code(), c.name(), c.level(), c.shortName(),
                c.isSettled(), c.isFarmed(), c.isPasture(), c.isWild());
    }

    /**
     * What the land was used for at a point in a year: the answer of {@link #landUseAt}.
     *
     * @param name     the anthrome's own name, e.g. "rice villages"
     * @param level    the coarse level: "villages", "cropland", "rangeland", "wild", "ice", "town"
     * @param shortName a short word for it: "town", "ice", "wild", "fields", "pasture"
     * @param settled  people live here densely
     * @param farmed   the land is cropped
     * @param pasture  the land is grazed
     * @param wild     the land is left alone
     */
    public record LandUse(int code, String name, String level, String shortName,
                          boolean settled, boolean farmed, boolean pasture, boolean wild) {}

    /**
     * The changes to the water standing at a point in a year (system B2, step 35): the sea that
     * came, the lake that went, the river that moved, the marsh that was drained.
     *
     * <p>Each carries the year it happened and, where it was undone, the year it was undone — the
     * Zuiderzee is "flooded" from 1287 and "closed" from 1932, and the Aral is one sea to 1986 and
     * a split one from 1987. This is the hook for "is there water here in this year?".
     *
     * @param lat  centre latitude
     * @param lon  centre longitude
     * @param year the year to ask about
     * @return the changes standing then, nearest first; never null
     */
    public List<WaterChange> waterChangedBy(double lat, double lon, int year) {
        List<WaterChange> out = new ArrayList<>();
        WaterChanges register = WaterChanges.get();
        if (!register.loaded()) {
            LOGGER.warn("[TerraOsmBridge] water changes not available: {}", register.problem());
            return out;
        }
        for (WaterChanges.Change c : register.standing(lat, lon, year)) {
            out.add(new WaterChange(c.name(), c.kind(), c.change(), c.year(),
                    c.endYear() == WaterChanges.STILL ? null : Integer.valueOf(c.endYear()),
                    c.estimated(), c.note()));
        }
        return out;
    }

    /** One change to the water: the answer of {@link #waterChangedBy}. */
    public record WaterChange(String name, String kind, String change, int year,
                              @Nullable Integer endYear, boolean estimated, @Nullable String note) {

        /** Whether it is still going: never undone. */
        public boolean stillStanding() {
            return endYear == null;
        }
    }

    /**
     * The water works standing within {@code km} of a point in a year (system B25, step 35) —
     * wells, tanks, stepwells, cisterns, qanats, aqueducts, baths, dams, canals, dikes, drains.
     *
     * <p>Rome in AD 100 is offered eleven aqueducts; Sydney in 1850 is offered nothing, which is the
     * truth of the Tank Stream. Where the year was estimated from the place's own window rather than
     * recorded by Wikidata, {@code estimated} is true.
     *
     * @param lat  centre latitude
     * @param lon  centre longitude
     * @param year the year to ask about; only works standing in it are returned
     * @param km   radius in kilometres
     * @return the works standing then, nearest first; never null
     */
    public List<WaterWork> worksNear(double lat, double lon, int year, double km) {
        List<WaterWork> out = new ArrayList<>();
        WaterWorks register = WaterWorks.get();
        if (!register.loaded()) {
            LOGGER.warn("[TerraOsmBridge] water works not available: {}", register.problem());
            return out;
        }
        for (WaterWorks.Work w : register.near(lat, lon, year, km)) {
            out.add(new WaterWork(w.name(), w.kind(), w.lat(), w.lon(), w.from(),
                    w.to() == WaterWorks.STILL ? null : Integer.valueOf(w.to()),
                    register.groupOf(w.kind()), w.dated() ? null : Boolean.TRUE));
        }
        return out;
    }

    /** One water work: the answer of {@link #worksNear}. */
    public record WaterWork(@Nullable String name, String kind, double lat, double lon, int from,
                            @Nullable Integer to, @Nullable String group, @Nullable Boolean estimated) {}

    /**
     * The climate of a place in a year (system B21, step 35): which named periods were running
     * there, and what they did to the temperature, the rain and the harvest.
     *
     * <p>The periods are regional, because the Little Ice Age was: Cusco in 1650 feels the Andean
     * one and not Europe's, and it is not an error that it feels neither of the other's. Where no
     * period reaches a place the list is empty, and that is an answer too — Ur in 2500 BC has none,
     * and nothing is invented for it.
     *
     * @param lat  centre latitude
     * @param lon  centre longitude
     * @param year the year to ask about
     * @return the periods running there then, and their combined effect; never null
     */
    public Climate periodsAt(double lat, double lon, int year) {
        ClimatePeriods register = ClimatePeriods.get();
        if (!register.loaded()) {
            LOGGER.warn("[TerraOsmBridge] climate not available: {}", register.problem());
            return new Climate(List.of(), 0.0d, 0.0d, 1.0d, null);
        }
        List<ClimatePeriods.Period> act = register.active(lat, lon, year);
        List<ClimatePeriod> out = new ArrayList<>();
        for (ClimatePeriods.Period p : act) {
            out.add(new ClimatePeriod(p.id(), p.name(), p.type(), p.startYear(), p.endYear(),
                    p.tempChange(), p.precipChange(), p.yieldImpact(), p.severity(), p.note(),
                    p.estimated()));
        }
        return new Climate(List.copyOf(out), register.temperatureModifier(lat, lon, year),
                register.precipModifier(lat, lon, year), register.yieldModifier(lat, lon, year),
                act.isEmpty() ? null : register.describe(lat, lon, year));
    }

    /** The climate of a place in a year: the answer of {@link #periodsAt}. */
    public record Climate(List<ClimatePeriod> periods, double temperatureCelsius,
                          double rainPercent, double harvestFraction, @Nullable String describe) {

        /** Nothing named reaches this place in this year. */
        public boolean plain() {
            return periods.isEmpty();
        }
    }

    /** One named period of climate, inside {@link Climate}. */
    public record ClimatePeriod(String id, String name, String type, int startYear, int endYear,
                                double temperatureCelsius, double rainPercent,
                                double harvestFraction, double severity, @Nullable String note,
                                boolean estimated) {}

    /**
     * The sky over a place at an instant (systems B22 and B23, step 35): the naked-eye stars above
     * the horizon, turned to the age by precession and proper motion, and the pole star of that age
     * — Thuban in 3000 BC, Kappa Draconis in 1500 BC, Kochab at the turn of the era, Polaris now.
     *
     * <p>{@code jd} is a Julian day; {@link SkyStars#julianDay} builds one from a year, month, day
     * and hour. Ask for a {@code minAltitudeDeg} above about ten degrees: below that, trees.
     *
     * @param lat             centre latitude
     * @param lon             centre longitude
     * @param jd              the instant, as a Julian day
     * @param minAltitudeDeg  how far above the horizon a star must be to count
     * @param faintestMagnitude the faintest star to include; 4.0 is a town sky, 6.0 a dark one
     * @return the stars overhead then, brightest first, and the pole star; never null
     */
    public Sky starsOver(double lat, double lon, double jd, double minAltitudeDeg,
                         double faintestMagnitude) {
        SkyStars register = SkyStars.get();
        if (!register.loaded()) {
            LOGGER.warn("[TerraOsmBridge] stars not available: {}", register.problem());
            return new Sky(List.of(), null, null);
        }
        List<Star> out = new ArrayList<>();
        for (SkyStars.Placement p : register.above(lat, lon, jd, minAltitudeDeg, faintestMagnitude)) {
            out.add(new Star(p.star().label(), p.star().magnitude(), p.star().colour(),
                    p.star().constellation(), p.altitudeDeg(), p.azimuthDeg(), p.where()));
        }
        SkyStars.Placement pole = register.poleStar(jd, faintestMagnitude);
        return new Sky(List.copyOf(out),
                pole == null ? null : new Star(pole.star().label(), pole.star().magnitude(),
                        pole.star().colour(), pole.star().constellation(), 0.0d, 0.0d, null),
                register.describe(lat, lon, jd));
    }

    /** The sky over a place at an instant: the answer of {@link #starsOver}. */
    public record Sky(List<Star> above, @Nullable Star poleStar, @Nullable String describe) {

        /** The brightest thing overhead, or null under a thick sky. */
        @Nullable
        public Star brightest() {
            return above.isEmpty() ? null : above.get(0);
        }
    }

    /** One star as seen from the ground: inside {@link Sky}. */
    public record Star(String name, double magnitude, @Nullable String colour,
                       @Nullable String constellation, double altitudeDeg, double azimuthDeg,
                       @Nullable String where) {}

    /**
     * The eclipses a place sees between two years (system B23, step 35), deepest first, from
     * NASA's Five Millennium Catalog: 11,898 solar eclipses from 1999 BCE to AD 3000, with the
     * Besselian elements to work out what any one place saw.
     *
     * <p>This is the hook for "was anything happening in the sky that year?". Each answer carries
     * its date, how much of the Sun went, and the hour in Universal Time — enough to darken the
     * world for the length of it. Modern eclipses agree with NASA's own published track to 0.002
     * in magnitude; ancient ones agree with an independent ephemeris to about 0.03, which is the
     * uncertainty in delta-T three thousand years back.
     *
     * @param lat          centre latitude
     * @param lon          centre longitude
     * @param fromYear     first year to look at
     * @param toYear       last year to look at
     * @param minMagnitude the least depth worth reporting; 0.05 hides the barely-there ones
     * @return the eclipses seen there, deepest first; never null
     */
    public List<Eclipse> eclipseAt(double lat, double lon, int fromYear, int toYear,
                                   double minMagnitude) {
        List<Eclipse> out = new ArrayList<>();
        SkyEclipses register = SkyEclipses.get();
        if (!register.loaded()) {
            LOGGER.warn("[TerraOsmBridge] eclipses not available: {}", register.problem());
            return out;
        }
        for (SkyEclipses.Sighting s : register.solarSeen(lat, lon, fromYear, toYear, minMagnitude)) {
            SkyEclipses.Solar e = s.eclipse();
            out.add(new Eclipse(e.year(), e.month(), e.day(), e.type(), s.magnitude(),
                    s.utHours(), s.visible(), e.nasaMagnitude(), s.howMuch()));
        }
        return out;
    }

    /** One eclipse as a place saw it: the answer of {@link #eclipseAt}. */
    public record Eclipse(int year, int month, int day, String type, double magnitude,
                          double utHours, boolean visible, double catalogueMagnitude,
                          String howMuch) {

        /** A total eclipse, or one of the hybrid kind that was total here. */
        public boolean total() {
            return magnitude >= 1.0d;
        }
    }

    /**
     * Cached result wrapper
     */
    // ------------------------------------------------- step 36: what struck here, and what became of it

    /**
     * B27: what struck this place in this year, worst first (step 36).
     *
     * <p>Over 27,000 disasters, 3000 BC to AD 2026 (the record's end, not the world's: the world
     * runs to {@link com.eternalengine.clock.Timeline#LAST_YEAR}) — earthquakes, eruptions, tsunamis, floods,
     * plagues, epidemics, pandemics, famines and fires, each with how strong a thing the record
     * says and how far it reached. Ask this before you settle a place into a year: a town that
     * has just been through one looks nothing like a town that has not.
     *
     * @return empty where nothing is recorded, which is an answer and not permission
     */
    public List<Disaster> disastersAt(double lat, double lon, int year) {
        Disasters d = Disasters.get();
        if (!d.loaded()) {
            LOGGER.warn("[TerraOsmBridge] disasters not available: {}", d.problem());
            return List.of();
        }
        List<Disaster> out = new ArrayList<>();
        for (com.eternalengine.civilization.Disasters.Disaster q : d.at(lat, lon, year)) {
            Disasters.Effect e = Disasters.effect(q.kind());
            out.add(new Disaster(q.name(), q.kind().label(), q.year(), q.severity(),
                    q.severityEstimated(), q.dead(), d.damage(q, lat, lon), e.stopsRepair(),
                    e.what()));
        }
        return List.copyOf(out);
    }

    /** One sentence for a player standing here in this year, or empty when nothing happened. */
    public String disasterLine(double lat, double lon, int year) {
        Disasters d = Disasters.get();
        return d.loaded() ? d.describe(lat, lon, year) : "";
    }

    /**
     * B10: what state the famous buildings around a place are in, in this year (step 36).
     *
     * <p>The six states of building life: going up, standing, damaged, ruin, gone, or mended after
     * being hurt. A square whose buildings are ruins must not be given a whole town, and a town
     * that has just been through a plague must not be given sound houses.
     *
     * @param km  how far around the point to look
     * @param max how many to answer with
     */
    public List<BuildingState> buildingLifeAt(double lat, double lon, int year, double km, int max) {
        FamousBuildings fb = FamousBuildings.get();
        if (!fb.loaded()) {
            LOGGER.warn("[TerraOsmBridge] famous buildings not available: {}", fb.problem());
            return List.of();
        }
        List<BuildingState> out = new ArrayList<>();
        for (BuildingLife.Life l : BuildingLife.at(lat, lon, year, km, max)) {
            out.add(new BuildingState(l.subject().name(), l.phase().label(), l.condition(),
                    l.cause() == null ? "" : l.cause().name(), l.line(), l.estimated()));
        }
        return List.copyOf(out);
    }

    /** One line for a player: "2 standing, 3 damaged, 1 ruin". */
    public String buildingLifeLine(double lat, double lon, int year, double km, int max) {
        return BuildingLife.line(lat, lon, year, km, max);
    }

    /**
     * B21: the world's record of what the player built. The mod sets this when a world is opened
     * and saves it with the world; everything here reads it.
     */
    public static void setPlayerEdits(PlayerEdits edits) {
        worldEdits = edits == null ? new PlayerEdits() : edits;
    }

    /** B21: the world's record of what the player built. */
    public static PlayerEdits playerEdits() {
        return worldEdits;
    }

    /**
     * B21: what the player has standing here, in this year, on this branch (step 36).
     *
     * <p>The buildings that should be in the world: what they built, less what they have pulled
     * down, less what has not been built yet. Jump back before a thing was built and it is not in
     * the answer; jump forward and it is there again, because going back is not undoing.
     *
     * @param branches  the branch chain from the root down to the one being played
     * @param divergeAt the year each of those branches split from its child, or {@link Integer#MAX_VALUE}
     *                  for the last; the same length as {@code branches}
     */
    public List<PlayerBuilding> playerBuildingsIn(int year, List<String> branches,
                                                  List<Integer> divergeAt) {
        List<PlayerBuilding> out = new ArrayList<>();
        for (PlayerEdits.Edit e : worldEdits.standingIn(year, chainOf(branches, divergeAt))) {
            BuildingLife.Life l = worldEdits.lifeOf(e, year);
            out.add(new PlayerBuilding(e.name(), e.lat(), e.lon(), e.year(),
                    l.phase().label(), l.condition(),
                    l.cause() == null ? "" : l.cause().name(), l.line()));
        }
        return List.copyOf(out);
    }

    /**
     * B21: what a year jump has to do to the world — take these out, put these in.
     *
     * <p>Run this before moving the clock, act on it, and the player's buildings survive their own
     * history. {@link PlayerEdits.Jump#line()} says what to tell them.
     */
    public PlayerEdits.Jump playerJump(int fromYear, int toYear, List<String> branches,
                                       List<Integer> divergeAt) {
        return worldEdits.jump(fromYear, toYear, chainOf(branches, divergeAt));
    }

    private static List<PlayerEdits.Branch> chainOf(List<String> branches, List<Integer> divergeAt) {
        List<PlayerEdits.Branch> chain = new ArrayList<>();
        if (branches == null) {
            return chain;
        }
        for (int i = 0; i < branches.size(); i++) {
            int at = divergeAt != null && i < divergeAt.size()
                    ? divergeAt.get(i) : PlayerEdits.NO_DIVERGENCE;
            chain.add(new PlayerEdits.Branch(branches.get(i), at));
        }
        return chain;
    }

    private static volatile PlayerEdits worldEdits = new PlayerEdits();

    /**
     * B27: one disaster that reached a place (step 36).
     *
     * @param severity           0 to 1, from the magnitude or VEI where the record carries one
     * @param severityEstimated  true where it is the kind's own middle rather than a measurement
     * @param damage             how much of a building's fabric it took here, 0 to 1
     * @param stopsRepair        whether it left nobody to mend anything — the mark of disease and
     *                           hunger, which damage no timber at all
     */
    public record Disaster(String name, String kind, int year, double severity,
                           boolean severityEstimated, int dead, double damage,
                           boolean stopsRepair, String what) {}

    /**
     * B10: one building's state in a year (step 36).
     *
     * @param phase     "unbuilt", "works", "standing", "damaged", "ruin", "gone" or "repaired"
     * @param condition 1 is whole, 0 is gone
     * @param cause     the disaster blamed, or empty where the register has none to name
     * @param estimated true where any part of the answer is worked out rather than recorded
     */
    public record BuildingState(String name, String phase, double condition, String cause,
                                String line, boolean estimated) {}

    /**
     * B21: one thing the player built, and how it has fared (step 36).
     *
     * <p>Subject to the same history as everything else: a house put up in the shadow of Vesuvius
     * in AD 78 is a ruin in AD 79, and the register says so.
     */
    public record PlayerBuilding(String name, double lat, double lon, int year, String phase,
                                 double condition, String cause, String line) {}

    private static class CachedResult {
        final List<OsmStructure> structures;
        final long timestamp;
        
        CachedResult(List<OsmStructure> structures) {
            this.structures = structures;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS;
        }
    }
}

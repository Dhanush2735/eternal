package com.eternalengine.terrabridge;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
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
     * Cached result wrapper
     */
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

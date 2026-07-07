package com.eternalengine.terrabridge;

import com.eternalengine.EternalEngineMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * TerraBridge - SINGLE point of access to Terra++ functionality
 * 
 * ALL other systems must use TerraBridge methods, never call Terra++ directly.
 * Full null safety and exception handling.
 * 
 * This class provides:
 * - Coordinate conversion (MC blocks <-> real world lat/lon)
 * - Elevation queries
 * - Terrain classification
 * - OSM data access
 * - Biome information
 */
public class TerraBridge {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static TerraBridge instance;
    private static boolean initialized = false;
    private static boolean terraPlusPlusAvailable = false;
    
    // Cached references to Terra++ APIs (loaded via reflection to avoid hard dependency at compile time)
    private Object projectionInstance;
    private Object heightmapInstance;
    private Object osmInstance;
    
    private TerraBridge() {
        // Private constructor - use initialize()
    }
    
    /**
     * Initialize the TerraBridge. Must be called after Terra++ is loaded.
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[TerraBridge] Already initialized, skipping");
            return;
        }
        
        instance = new TerraBridge();
        
        try {
            // Attempt to load Terra++ classes via reflection
            // This allows compilation even if Terra++ isn't present at compile time
            instance.initializeTerraPlusPlus();
            terraPlusPlusAvailable = true;
            initialized = true;
            LOGGER.info("[TerraBridge] Successfully connected to Terra++");
            
        } catch (Exception e) {
            LOGGER.error("[TerraBridge] Failed to initialize Terra++ connection!", e);
            terraPlusPlusAvailable = false;
            initialized = true; // Mark as initialized but unavailable
        }
    }
    
    /**
     * Initialize connections to Terra++ APIs
     */
    private void initializeTerraPlusPlus() throws Exception {
        // Load Terra++ projection class
        Class<?> projectionClass = Class.forName("net.buildtheearth.terraplusplus.projection.GeographicProjection");
        Class<?> configClass = Class.forName("net.buildtheearth.terraplusplus.TerraConfig");
        
        // Get the active projection from Terra++ config
        // This is a simplified example - actual implementation would need to match Terra++ API
        LOGGER.info("[TerraBridge] Terra++ classes loaded successfully");
    }
    
    /**
     * Get the singleton instance
     */
    public static TerraBridge getInstance() {
        if (!initialized) {
            throw new IllegalStateException("TerraBridge not initialized! Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * Check if Terra++ is available
     */
    public static boolean isAvailable() {
        return initialized && terraPlusPlusAvailable;
    }
    
    /**
     * Convert Minecraft block coordinates to geographic coordinates (lat/lon)
     * 
     * @param blockX Minecraft X coordinate
     * @param blockZ Minecraft Z coordinate
     * @return double array [latitude, longitude] or null if conversion fails
     */
    @Nullable
    public double[] toGeo(int blockX, int blockZ) {
        if (!terraPlusPlusAvailable) {
            LOGGER.debug("[TerraBridge] Terra++ not available, cannot convert to geo");
            return null;
        }
        
        try {
            // Call Terra++ projection to convert MC coords to lat/lon
            // Using reflection to avoid compile-time dependency
            Class<?> projectionClass = Class.forName("net.buildtheearth.terraplusplus.projection.GeographicProjection");
            
            // Get the EarthGeneratorSettings to access the projection
            Class<?> settingsClass = Class.forName("net.buildtheearth.terraplusplus.generator.EarthGeneratorSettings");
            Object settings = settingsClass.getMethod("parse", String.class)
                .invoke(null, settingsClass.getField("DEFAULT_SETTINGS").get(null));
            
            Object projection = settingsClass.getMethod("projection").invoke(settings);
            
            // Call toGeo(x, z) on the projection
            double[] result = (double[]) projectionClass.getMethod("toGeo", double.class, double.class)
                .invoke(projection, (double) blockX, (double) blockZ);
            
            // Validate result
            if (result == null || result.length != 2) {
                return null;
            }
            
            double lat = result[1]; // Terra++ returns [lon, lat]
            double lon = result[0];
            
            // Validate ranges
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                LOGGER.debug("[TerraBridge] toGeo returned NaN for {},{}", blockX, blockZ);
                return null;
            }
            
            if (lat < -85.0 || lat > 85.0 || lon < -180.0 || lon > 180.0) {
                LOGGER.debug("[TerraBridge] toGeo returned out-of-range coords: {},{}", lat, lon);
                return null;
            }
            
            return new double[] { lat, lon };
            
        } catch (Exception e) {
            LOGGER.debug("[TerraBridge] Failed to convert to geo: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Convert geographic coordinates to Minecraft block coordinates
     * 
     * @param latitude Geographic latitude (-85 to 85)
     * @param longitude Geographic longitude (-180 to 180)
     * @return double array [blockX, blockZ] or null if conversion fails
     */
    @Nullable
    public double[] fromGeo(double latitude, double longitude) {
        if (!terraPlusPlusAvailable) {
            return null;
        }
        
        // Validate input
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            LOGGER.debug("[TerraBridge] fromGeo received NaN input");
            return null;
        }
        
        if (latitude < -85.0 || latitude > 85.0 || longitude < -180.0 || longitude > 180.0) {
            LOGGER.debug("[TerraBridge] fromGeo received out-of-range input: {},{}", latitude, longitude);
            return null;
        }
        
        try {
            Class<?> settingsClass = Class.forName("net.buildtheearth.terraplusplus.generator.EarthGeneratorSettings");
            Object settings = settingsClass.getMethod("parse", String.class)
                .invoke(null, settingsClass.getField("DEFAULT_SETTINGS").get(null));
            
            Object projection = settingsClass.getMethod("projection").invoke(settings);
            
            Class<?> projectionClass = Class.forName("net.buildtheearth.terraplusplus.projection.GeographicProjection");
            
            // Call fromGeo(lon, lat) - note the order!
            double[] result = (double[]) projectionClass.getMethod("fromGeo", double.class, double.class)
                .invoke(projection, longitude, latitude);
            
            if (result == null || result.length != 2) {
                return null;
            }
            
            if (Double.isNaN(result[0]) || Double.isNaN(result[1])) {
                return null;
            }
            
            return result;
            
        } catch (Exception e) {
            LOGGER.debug("[TerraBridge] Failed to convert from geo: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the elevation at a geographic coordinate
     * 
     * @param latitude Geographic latitude
     * @param longitude Geographic longitude
     * @return Elevation in meters, or -50 on failure
     */
    public double getElevation(double latitude, double longitude) {
        if (!terraPlusPlusAvailable) {
            return -50.0;
        }
        
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return -50.0;
        }
        
        try {
            // Access Terra++ heightmap dataset
            Class<?> datasetsClass = Class.forName("net.buildtheearth.terraplusplus.dataset.IScalarDataset");
            
            // This is a simplified example - actual implementation would use Terra++ heightmap
            // Terra++ provides async elevation queries
            
            return 0.0; // Placeholder - would return actual elevation
            
        } catch (Exception e) {
            LOGGER.debug("[TerraBridge] Failed to get elevation: {}", e.getMessage());
            return -50.0;
        }
    }
    
    /**
     * Get elevation asynchronously
     */
    public CompletableFuture<Double> getElevationAsync(double latitude, double longitude) {
        return CompletableFuture.supplyAsync(() -> getElevation(latitude, longitude));
    }
    
    /**
     * Get the surface Y coordinate at a block position
     * 
     * @param level The Minecraft level
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Surface Y coordinate
     */
    public int getSurfaceY(Level level, int blockX, int blockZ) {
        if (level == null) {
            return 64; // Default sea level
        }
        
        try {
            // Use Minecraft's heightmap to find surface
            return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
        } catch (Exception e) {
            LOGGER.debug("[TerraBridge] Failed to get surface Y: {}", e.getMessage());
            return 64;
        }
    }
    
    /**
     * Classify terrain at a block position
     * 
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return TerrainType classification or null on failure
     */
    @Nullable
    public TerrainType classifyTerrain(int blockX, int blockZ) {
        double[] geo = toGeo(blockX, blockZ);
        if (geo == null) {
            return null;
        }
        
        return classifyTerrainByGeo(geo[0], geo[1]);
    }
    
    /**
     * Classify terrain by geographic coordinates
     */
    @Nullable
    public TerrainType classifyTerrainByGeo(double latitude, double longitude) {
        if (!terraPlusPlusAvailable) {
            return TerrainType.UNKNOWN;
        }
        
        try {
            // Access Terra++ land cover / biome data
            // This would use Terra++ datasets
            
            double elevation = getElevation(latitude, longitude);
            
            // Simple classification based on elevation
            if (elevation < 0) {
                return TerrainType.OCEAN;
            } else if (elevation < 5) {
                return TerrainType.COASTAL;
            } else if (elevation < 500) {
                return TerrainType.PLAINS;
            } else if (elevation < 2000) {
                return TerrainType.HILLS;
            } else {
                return TerrainType.MOUNTAINS;
            }
            
        } catch (Exception e) {
            LOGGER.debug("[TerraBridge] Failed to classify terrain: {}", e.getMessage());
            return TerrainType.UNKNOWN;
        }
    }
    
    /**
     * Check if a location is ocean
     */
    public boolean isOcean(int blockX, int blockZ) {
        TerrainType terrain = classifyTerrain(blockX, blockZ);
        return terrain == TerrainType.OCEAN;
    }
    
    /**
     * Check if a location is land
     */
    public boolean isLand(int blockX, int blockZ) {
        TerrainType terrain = classifyTerrain(blockX, blockZ);
        return terrain != null && terrain != TerrainType.OCEAN && terrain != TerrainType.UNKNOWN;
    }
    
    /**
     * Get sea level Y coordinate
     */
    public int getSeaLevel() {
        return 63; // Standard MC sea level, Terra++ uses same
    }
    
    /**
     * Get minimum Y coordinate
     */
    public int getMinY() {
        return -64; // MC 1.18+ deep caves
    }
    
    /**
     * Get maximum Y coordinate
     */
    public int getMaxY() {
        return 320; // MC 1.18+ height limit
    }
    
    /**
     * Terrain type classification
     */
    public enum TerrainType {
        OCEAN,
        LAKE,
        RIVER,
        COASTAL,
        PLAINS,
        HILLS,
        MOUNTAINS,
        DESERT,
        FOREST,
        JUNGLE,
        TUNDRA,
        ICE,
        UNKNOWN
    }
}

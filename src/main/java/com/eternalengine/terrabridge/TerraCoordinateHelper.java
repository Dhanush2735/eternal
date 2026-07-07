package com.eternalengine.terrabridge;

import com.eternalengine.EternalEngineMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Helper class for coordinate conversions using TerraBridge.
 * Provides convenient static methods for common coordinate operations.
 */
public class TerraCoordinateHelper {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    // Earth radius in meters
    public static final double EARTH_RADIUS = 6371000.0;
    
    // Latitude/Longitude bounds
    public static final double MIN_LATITUDE = -85.0;
    public static final double MAX_LATITUDE = 85.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;
    
    /**
     * Convert block position to geographic coordinates
     * 
     * @param pos Block position
     * @return [latitude, longitude] or null on failure
     */
    @Nullable
    public static double[] toGeo(BlockPos pos) {
        return toGeo(pos.getX(), pos.getZ());
    }
    
    /**
     * Convert block coordinates to geographic coordinates
     * 
     * @param blockX X coordinate
     * @param blockZ Z coordinate
     * @return [latitude, longitude] or null on failure
     */
    @Nullable
    public static double[] toGeo(int blockX, int blockZ) {
        if (!TerraBridge.isAvailable()) {
            LOGGER.debug("[TerraCoordHelper] TerraBridge not available");
            return null;
        }
        
        return TerraBridge.getInstance().toGeo(blockX, blockZ);
    }
    
    /**
     * Convert geographic coordinates to block position
     * 
     * @param latitude Latitude (-85 to 85)
     * @param longitude Longitude (-180 to 180)
     * @param level Minecraft level for Y coordinate
     * @return BlockPos or null on failure
     */
    @Nullable
    public static BlockPos fromGeo(double latitude, double longitude, @Nullable Level level) {
        if (!TerraBridge.isAvailable()) {
            return null;
        }
        
        // Validate input
        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
            LOGGER.debug("[TerraCoordHelper] Invalid coordinates: {}, {}", latitude, longitude);
            return null;
        }
        
        double[] coords = TerraBridge.getInstance().fromGeo(latitude, longitude);
        if (coords == null || coords.length != 2) {
            return null;
        }
        
        int blockX = (int) Math.floor(coords[0]);
        int blockZ = (int) Math.floor(coords[1]);
        int blockY = 64; // Default Y
        
        if (level != null) {
            blockY = TerraBridge.getInstance().getSurfaceY(level, blockX, blockZ);
        }
        
        return new BlockPos(blockX, blockY, blockZ);
    }
    
    /**
     * Convert geographic coordinates to MC coordinates without Y
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @return [x, z] or null on failure
     */
    @Nullable
    public static double[] fromGeo(double latitude, double longitude) {
        if (!TerraBridge.isAvailable()) {
            return null;
        }
        
        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
            return null;
        }
        
        return TerraBridge.getInstance().fromGeo(latitude, longitude);
    }
    
    /**
     * Get elevation at geographic coordinates
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @return Elevation in meters, -50 on failure
     */
    public static double getElevation(double latitude, double longitude) {
        if (!TerraBridge.isAvailable()) {
            return -50.0;
        }
        
        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
            return -50.0;
        }
        
        return TerraBridge.getInstance().getElevation(latitude, longitude);
    }
    
    /**
     * Get elevation at block position
     */
    public static double getElevation(BlockPos pos) {
        double[] geo = toGeo(pos);
        if (geo == null) {
            return -50.0;
        }
        return getElevation(geo[0], geo[1]);
    }
    
    /**
     * Get surface Y coordinate at block position
     */
    public static int getSurfaceY(Level level, int blockX, int blockZ) {
        if (!TerraBridge.isAvailable()) {
            return 64;
        }
        return TerraBridge.getInstance().getSurfaceY(level, blockX, blockZ);
    }
    
    /**
     * Classify terrain at block position
     */
    @Nullable
    public static TerraBridge.TerrainType classifyTerrain(int blockX, int blockZ) {
        if (!TerraBridge.isAvailable()) {
            return null;
        }
        return TerraBridge.getInstance().classifyTerrain(blockX, blockZ);
    }
    
    /**
     * Check if location is ocean
     */
    public static boolean isOcean(int blockX, int blockZ) {
        if (!TerraBridge.isAvailable()) {
            return false;
        }
        return TerraBridge.getInstance().isOcean(blockX, blockZ);
    }
    
    /**
     * Check if location is river
     */
    public static boolean isRiver(int blockX, int blockZ) {
        TerraBridge.TerrainType terrain = classifyTerrain(blockX, blockZ);
        return terrain == TerraBridge.TerrainType.RIVER;
    }
    
    /**
     * Check if location is lake
     */
    public static boolean isLake(int blockX, int blockZ) {
        TerraBridge.TerrainType terrain = classifyTerrain(blockX, blockZ);
        return terrain == TerraBridge.TerrainType.LAKE;
    }
    
    /**
     * Check if location is land
     */
    public static boolean isLand(int blockX, int blockZ) {
        if (!TerraBridge.isAvailable()) {
            return true; // Assume land if can't check
        }
        return TerraBridge.getInstance().isLand(blockX, blockZ);
    }
    
    /**
     * Validate latitude
     */
    public static boolean isValidLatitude(double latitude) {
        return !Double.isNaN(latitude) && latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE;
    }
    
    /**
     * Validate longitude
     */
    public static boolean isValidLongitude(double longitude) {
        return !Double.isNaN(longitude) && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }
    
    /**
     * Validate both lat and lon
     */
    public static boolean isValidCoordinates(double latitude, double longitude) {
        return isValidLatitude(latitude) && isValidLongitude(longitude);
    }
    
    /**
     * Calculate distance between two geographic points (Haversine formula)
     * 
     * @return Distance in meters
     */
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        if (!isValidCoordinates(lat1, lon1) || !isValidCoordinates(lat2, lon2)) {
            return Double.MAX_VALUE;
        }
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }
    
    /**
     * Calculate distance between two block positions in real-world meters
     */
    public static double distanceInMeters(BlockPos pos1, BlockPos pos2) {
        double[] geo1 = toGeo(pos1);
        double[] geo2 = toGeo(pos2);
        
        if (geo1 == null || geo2 == null) {
            // Fall back to MC block distance
            return pos1.distManhattan(pos2);
        }
        
        return haversineDistance(geo1[0], geo1[1], geo2[0], geo2[1]);
    }
    
    /**
     * Get bearing from point 1 to point 2
     * 
     * @return Bearing in degrees (0-360, 0 = North)
     */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        
        double y = Math.sin(dLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }
    
    /**
     * Calculate a point at a given distance and bearing from start point
     * 
     * @param startLat Starting latitude
     * @param startLon Starting longitude
     * @param distanceMeters Distance in meters
     * @param bearingDegrees Bearing in degrees (0 = North)
     * @return [lat, lon] of destination point
     */
    public static double[] destinationPoint(double startLat, double startLon, 
                                            double distanceMeters, double bearingDegrees) {
        double angularDistance = distanceMeters / EARTH_RADIUS;
        double bearingRad = Math.toRadians(bearingDegrees);
        double lat1Rad = Math.toRadians(startLat);
        double lon1Rad = Math.toRadians(startLon);
        
        double lat2Rad = Math.asin(
            Math.sin(lat1Rad) * Math.cos(angularDistance) +
            Math.cos(lat1Rad) * Math.sin(angularDistance) * Math.cos(bearingRad)
        );
        
        double lon2Rad = lon1Rad + Math.atan2(
            Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(lat1Rad),
            Math.cos(angularDistance) - Math.sin(lat1Rad) * Math.sin(lat2Rad)
        );
        
        double lat2 = Math.toDegrees(lat2Rad);
        double lon2 = Math.toDegrees(lon2Rad);
        
        // Normalize longitude to -180 to 180
        lon2 = ((lon2 + 540) % 360) - 180;
        
        return new double[] { lat2, lon2 };
    }
    
    /**
     * Get sea level Y coordinate
     */
    public static int getSeaLevel() {
        if (!TerraBridge.isAvailable()) {
            return 63;
        }
        return TerraBridge.getInstance().getSeaLevel();
    }
    
    /**
     * Get minimum Y coordinate
     */
    public static int getMinY() {
        if (!TerraBridge.isAvailable()) {
            return -64;
        }
        return TerraBridge.getInstance().getMinY();
    }
    
    /**
     * Get maximum Y coordinate
     */
    public static int getMaxY() {
        if (!TerraBridge.isAvailable()) {
            return 320;
        }
        return TerraBridge.getInstance().getMaxY();
    }
}

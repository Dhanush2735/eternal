package com.eternalengine.terrabridge;

import com.eternalengine.EternalEngineMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Static coordinate helper backed only by TerraBridge.
 *
 * Raw toGeo arrays use Terra++ order: [longitude, latitude].
 * Prefer toGeoPoint() for new code to avoid lat/lon swaps.
 */
public final class TerraCoordinateHelper {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    public static final double EARTH_RADIUS = 6_371_000.0d;
    public static final double MIN_LATITUDE = -85.0d;
    public static final double MAX_LATITUDE = 85.0d;
    public static final double MIN_LONGITUDE = -180.0d;
    public static final double MAX_LONGITUDE = 180.0d;

    private TerraCoordinateHelper() {
    }

    @Nullable
    public static double[] toGeo(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return toGeo(pos.getX(), pos.getZ());
    }

    /**
     * @return [longitude, latitude], or null on failure.
     */
    @Nullable
    public static double[] toGeo(int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            return null;
        }
        return bridge.toGeo(blockX, blockZ);
    }

    @Nullable
    public static TerraBridge.GeoPoint toGeoPoint(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return toGeoPoint(pos.getX(), pos.getZ());
    }

    @Nullable
    public static TerraBridge.GeoPoint toGeoPoint(int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            return null;
        }
        return bridge.toGeoPoint(blockX, blockZ);
    }

    /**
     * Converts lat/lon to block coordinates and uses surface Y when a level is supplied.
     */
    @Nullable
    public static BlockPos fromGeo(double latitude, double longitude, @Nullable Level level) {
        double[] coords = fromGeo(latitude, longitude);
        if (coords == null) {
            return null;
        }
        int blockX = (int) Math.floor(coords[0]);
        int blockZ = (int) Math.floor(coords[1]);
        int blockY = getSurfaceY(level, blockX, blockZ);
        return new BlockPos(blockX, blockY, blockZ);
    }

    /**
     * @return [blockX, blockZ], or null on failure.
     */
    @Nullable
    public static double[] fromGeo(double latitude, double longitude) {
        if (!isValidCoordinates(latitude, longitude)) {
            return null;
        }
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            return null;
        }
        return bridge.fromGeo(latitude, longitude);
    }

    public static double getElevation(double latitude, double longitude) {
        if (!isValidCoordinates(latitude, longitude)) {
            return -50.0d;
        }
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            return -50.0d;
        }
        return bridge.getElevation(latitude, longitude);
    }

    public static double getElevation(BlockPos pos) {
        TerraBridge.GeoPoint point = toGeoPoint(pos);
        if (point == null || !point.valid()) {
            return -50.0d;
        }
        return getElevation(point.latitude(), point.longitude());
    }

    /**
     * Returns the top existing surface block Y. For placement, use getPlacementY().
     */
    public static int getSurfaceY(@Nullable Level level, int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            return 63;
        }
        return bridge.getSurfaceY(level, blockX, blockZ);
    }

    public static int getPlacementY(@Nullable Level level, int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            return 64;
        }
        return bridge.getPlacementY(level, blockX, blockZ);
    }

    public static TerraBridge.TerrainType classifyTerrain(int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            return TerraBridge.TerrainType.UNKNOWN;
        }
        return bridge.classifyTerrain(blockX, blockZ);
    }

    @Nullable
    public static TerraBridge.TerrainSnapshot probe(int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        if (bridge == null || !TerraBridge.isAvailable()) {
            return null;
        }
        return bridge.probe(blockX, blockZ);
    }

    public static boolean isOcean(int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge != null && TerraBridge.isAvailable() && bridge.isOcean(blockX, blockZ);
    }

    public static boolean isRiver(int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge != null && TerraBridge.isAvailable() && bridge.isRiver(blockX, blockZ);
    }

    public static boolean isLake(int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge != null && TerraBridge.isAvailable() && bridge.isLake(blockX, blockZ);
    }

    public static boolean isLand(int blockX, int blockZ) {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge != null && TerraBridge.isAvailable() && bridge.isLand(blockX, blockZ);
    }

    public static boolean isOcean(double latitude, double longitude) {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge != null && TerraBridge.isAvailable() && bridge.isOcean(latitude, longitude);
    }

    public static boolean isRiver(double latitude, double longitude) {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge != null && TerraBridge.isAvailable() && bridge.isRiver(latitude, longitude);
    }

    public static boolean isLake(double latitude, double longitude) {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge != null && TerraBridge.isAvailable() && bridge.isLake(latitude, longitude);
    }

    public static boolean isLand(double latitude, double longitude) {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge != null && TerraBridge.isAvailable() && bridge.isLand(latitude, longitude);
    }

    public static boolean isValidLatitude(double latitude) {
        return !Double.isNaN(latitude) && latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE;
    }

    public static boolean isValidLongitude(double longitude) {
        return !Double.isNaN(longitude) && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    public static boolean isValidCoordinates(double latitude, double longitude) {
        return isValidLatitude(latitude) && isValidLongitude(longitude);
    }

    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        if (!isValidCoordinates(lat1, lon1) || !isValidCoordinates(lat2, lon2)) {
            return Double.MAX_VALUE;
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        return EARTH_RADIUS * c;
    }

    public static double distanceInMeters(BlockPos pos1, BlockPos pos2) {
        if (pos1 == null || pos2 == null) {
            return Double.MAX_VALUE;
        }
        TerraBridge.GeoPoint geo1 = toGeoPoint(pos1);
        TerraBridge.GeoPoint geo2 = toGeoPoint(pos2);
        if (geo1 == null || geo2 == null || !geo1.valid() || !geo2.valid()) {
            return pos1.distManhattan(pos2);
        }
        return haversineDistance(geo1.latitude(), geo1.longitude(), geo2.latitude(), geo2.longitude());
    }

    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        if (!isValidCoordinates(lat1, lon1) || !isValidCoordinates(lat2, lon2)) {
            return 0.0d;
        }
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double y = Math.sin(dLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad)
            - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0d) % 360.0d;
    }

    /**
     * @return [latitude, longitude] destination point.
     */
    public static double[] destinationPoint(double startLat, double startLon,
                                            double distanceMeters, double bearingDegrees) {
        if (!isValidCoordinates(startLat, startLon) || distanceMeters < 0.0d) {
            LOGGER.debug("destinationPoint: invalid input {},{} distance={}", startLat, startLon, distanceMeters);
            return new double[] { startLat, startLon };
        }
        double angularDistance = distanceMeters / EARTH_RADIUS;
        double bearingRad = Math.toRadians(bearingDegrees);
        double lat1Rad = Math.toRadians(startLat);
        double lon1Rad = Math.toRadians(startLon);
        double lat2Rad = Math.asin(
            Math.sin(lat1Rad) * Math.cos(angularDistance)
                + Math.cos(lat1Rad) * Math.sin(angularDistance) * Math.cos(bearingRad)
        );
        double lon2Rad = lon1Rad + Math.atan2(
            Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(lat1Rad),
            Math.cos(angularDistance) - Math.sin(lat1Rad) * Math.sin(lat2Rad)
        );
        double lat2 = Math.toDegrees(lat2Rad);
        double lon2 = Math.toDegrees(lon2Rad);
        lon2 = ((lon2 + 540.0d) % 360.0d) - 180.0d;
        return new double[] { lat2, lon2 };
    }

    public static int getSeaLevel() {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge == null ? 63 : bridge.getSeaLevel();
    }

    public static int getMinY() {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge == null ? -2032 : bridge.getMinY();
    }

    public static int getMaxY() {
        TerraBridge bridge = TerraBridge.getInstance();
        return bridge == null ? 2032 : bridge.getMaxY();
    }
}

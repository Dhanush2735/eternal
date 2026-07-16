package com.eternalengine.terrabridge;

import com.eternalengine.EternalEngineMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TerraBridge is Eternal Engine's single source-of-truth access layer for Terra++.
 *
 * Important rules enforced here:
 * - Eternal Engine does not perform its own projection math.
 * - Eternal Engine does not perform its own elevation classification.
 * - Raw toGeo arrays always use Terra++ order: [longitude, latitude].
 * - High-level code should prefer GeoPoint to avoid lat/lon swaps.
 * - All methods are null-safe and return documented fallback values.
 *
 * This class intentionally uses reflection for Terra++ internals that are private
 * in the current generator implementation, while still calling the real Terra++
 * classes and methods. It must never contain temporary/scratch-script logic.
 */
public final class TerraBridge {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    private static final String TERRA_CHUNK_GENERATOR_CLASS =
        "net.buildtheearth.terraplusplus.generator.TerraChunkGenerator";
    private static final String TERRA_DENSITY_FUNCTIONS_CLASS =
        "net.buildtheearth.terraplusplus.generator.TerraDensityFunctions";
    private static final String ESA_WORLD_COVER_CLASS =
        "net.buildtheearth.terraplusplus.generator.EsaWorldCoverIndex";
    private static final String TMM_EARTH_SETTINGS_CLASS =
        "net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings";
    private static final String TERRA_PUBLIC_API_CLASS =
        "net.buildtheearth.terraplusplus.api.TerraPlusPlusAPI";

    private static final int DEFAULT_ZOOM = 15;
    private static final int FALLBACK_SEA_LEVEL = 63;
    private static final int FALLBACK_MIN_Y = -2032;
    private static final int FALLBACK_MAX_Y = 2032;
    private static final double FALLBACK_ELEVATION = -50.0d;

    private static volatile TerraBridge instance;
    private static volatile boolean initialized;
    private static volatile boolean terraAvailable;

    private final AtomicInteger warningCounter = new AtomicInteger();
    private volatile MinecraftServer server;
    private volatile Object terraGenerator;
    private volatile Object densityFunctions;
    private volatile Object worldCover;
    private volatile int detectedZoom = DEFAULT_ZOOM;

    private TerraBridge() {
    }

    /**
     * Initializes class availability only. The real generator is usually found
     * later from initialize(MinecraftServer) or TerraChunkGenerator.activeInstance.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        TerraBridge bridge = new TerraBridge();
        instance = bridge;
        initialized = true;
        terraAvailable = bridge.checkTerraClasses();
        if (terraAvailable) {
            LOGGER.info("[TerraBridge] Terra++ classes available; waiting for active generator");
        } else {
            LOGGER.error("[TerraBridge] Terra++ classes are not available");
        }
    }

    /**
     * Initializes and binds to the active server/generator when available.
     */
    public static synchronized boolean initialize(MinecraftServer server) {
        if (!initialized) {
            initialize();
        }
        TerraBridge bridge = getInstance();
        if (bridge == null) {
            return false;
        }
        bridge.server = server;
        boolean found = bridge.resolveGenerator() != null;
        if (found) {
            bridge.resolveDensityFunctions();
            bridge.resolveWorldCover();
            LOGGER.info("[TerraBridge] Bound to Terra++ generator, zoom={}, Y=[{},{}]",
                bridge.detectedZoom, bridge.getMinY(), bridge.getMaxY());
        } else {
            bridge.warnLimited("initialize(server): Terra++ generator not found yet");
        }
        return found;
    }

    @Nullable
    public static TerraBridge getInstance() {
        if (!initialized) {
            initialize();
        }
        return instance;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isAvailable() {
        return initialized && terraAvailable;
    }

    private boolean checkTerraClasses() {
        try {
            Class.forName(TERRA_CHUNK_GENERATOR_CLASS);
            Class.forName(TERRA_DENSITY_FUNCTIONS_CLASS);
            Class.forName(ESA_WORLD_COVER_CLASS);
            Class.forName(TMM_EARTH_SETTINGS_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.error("[TerraBridge] Required Terra++ class missing: {}", e.getMessage());
            return false;
        } catch (Throwable t) {
            LOGGER.error("[TerraBridge] Terra++ class check failed: {}", t.getMessage());
            return false;
        }
    }

    @Nullable
    private Object resolveGenerator() {
        if (!terraAvailable) {
            return null;
        }
        Object cached = this.terraGenerator;
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> generatorClass = Class.forName(TERRA_CHUNK_GENERATOR_CLASS);

            // Terra++ keeps an activeInstance in the current codebase.
            try {
                Field active = generatorClass.getDeclaredField("activeInstance");
                active.setAccessible(true);
                Object activeGenerator = active.get(null);
                if (activeGenerator != null && generatorClass.isInstance(activeGenerator)) {
                    this.terraGenerator = activeGenerator;
                    this.detectedZoom = readIntField(activeGenerator, "zoom", DEFAULT_ZOOM);
                    return activeGenerator;
                }
            } catch (NoSuchFieldException ignored) {
                // Fall through to server scan.
            }

            MinecraftServer currentServer = this.server;
            if (currentServer != null) {
                for (ServerLevel level : currentServer.getAllLevels()) {
                    Object generator = level.getChunkSource().getGenerator();
                    if (generatorClass.isInstance(generator)) {
                        this.terraGenerator = generator;
                        this.detectedZoom = readIntField(generator, "zoom", DEFAULT_ZOOM);
                        return generator;
                    }
                }
            }
        } catch (Throwable t) {
            warnLimited("resolveGenerator failed: " + t.getMessage());
        }
        return null;
    }

    @Nullable
    private Object resolveDensityFunctions() {
        if (!terraAvailable) {
            return null;
        }
        Object cached = this.densityFunctions;
        if (cached != null) {
            return cached;
        }
        try {
            Object generator = resolveGenerator();
            if (generator != null) {
                try {
                    Method getDf = generator.getClass().getDeclaredMethod("getDf");
                    getDf.setAccessible(true);
                    Object df = getDf.invoke(generator);
                    if (df != null) {
                        this.densityFunctions = df;
                        return df;
                    }
                } catch (NoSuchMethodException ignored) {
                    Field dfField = generator.getClass().getDeclaredField("df");
                    dfField.setAccessible(true);
                    Object df = dfField.get(generator);
                    if (df != null) {
                        this.densityFunctions = df;
                        return df;
                    }
                }
            }

            // Fallback still uses the real TerraDensityFunctions, not custom math.
            Class<?> dfClass = Class.forName(TERRA_DENSITY_FUNCTIONS_CLASS);
            Object df = dfClass.getConstructor(int.class).newInstance(detectedZoom);
            this.densityFunctions = df;
            return df;
        } catch (Throwable t) {
            warnLimited("resolveDensityFunctions failed: " + t.getMessage());
            return null;
        }
    }

    @Nullable
    private Object resolveWorldCover() {
        if (!terraAvailable) {
            return null;
        }
        Object cached = this.worldCover;
        if (cached != null) {
            return cached;
        }
        try {
            Object generator = resolveGenerator();
            if (generator != null) {
                try {
                    Method getWorldCover = generator.getClass().getDeclaredMethod("getWorldCover");
                    getWorldCover.setAccessible(true);
                    Object wc = getWorldCover.invoke(generator);
                    if (wc != null) {
                        this.worldCover = wc;
                        return wc;
                    }
                } catch (NoSuchMethodException ignored) {
                    Field wcField = generator.getClass().getDeclaredField("worldCover");
                    wcField.setAccessible(true);
                    Object wc = wcField.get(generator);
                    if (wc != null) {
                        this.worldCover = wc;
                        return wc;
                    }
                }
            }

            Class<?> wcClass = Class.forName(ESA_WORLD_COVER_CLASS);
            Object wc = wcClass.getConstructor().newInstance();
            this.worldCover = wc;
            return wc;
        } catch (Throwable t) {
            warnLimited("resolveWorldCover failed: " + t.getMessage());
            return null;
        }
    }

    /**
     * Converts Minecraft block coordinates to Terra++ geographic coordinates.
     *
     * @return raw Terra++ order [longitude, latitude], or null on failure.
     */
    @Nullable
    public double[] toGeo(int blockX, int blockZ) {
        double[] apiResult = apiRawToGeoLonLat(blockX, blockZ);
        if (apiResult != null) {
            return apiResult;
        }
        try {
            Object df = resolveDensityFunctions();
            if (df == null) {
                return null;
            }
            Method method = df.getClass().getMethod("toGeo", int.class, int.class);
            double[] result = (double[]) method.invoke(df, blockX, blockZ);
            if (!isValidLonLatArray(result)) {
                return null;
            }
            return new double[] { result[0], result[1] };
        } catch (Throwable t) {
            warnLimited("toGeo failed for " + blockX + "," + blockZ + ": " + t.getMessage());
            return null;
        }
    }

    @Nullable
    public GeoPoint toGeoPoint(int blockX, int blockZ) {
        double[] raw = toGeo(blockX, blockZ);
        if (raw == null) {
            return null;
        }
        return new GeoPoint(raw[1], raw[0]);
    }

    /**
     * Converts geographic coordinates to Minecraft projected coordinates.
     *
     * @param latitude latitude in degrees
     * @param longitude longitude in degrees
     * @return [blockX, blockZ], or null on failure.
     */
    @Nullable
    public double[] fromGeo(double latitude, double longitude) {
        if (!isValidLatLon(latitude, longitude)) {
            return null;
        }
        double[] apiResult = apiFromGeo(latitude, longitude);
        if (apiResult != null) {
            return apiResult;
        }
        try {
            Object df = resolveDensityFunctions();
            if (df == null) {
                return null;
            }
            Method method = df.getClass().getMethod("fromGeo", double.class, double.class);
            double[] result = (double[]) method.invoke(df, latitude, longitude);
            if (result == null || result.length < 2 || Double.isNaN(result[0]) || Double.isNaN(result[1])) {
                return null;
            }
            return new double[] { result[0], result[1] };
        } catch (Throwable t) {
            warnLimited("fromGeo failed for " + latitude + "," + longitude + ": " + t.getMessage());
            return null;
        }
    }

    public double getElevation(double latitude, double longitude) {
        if (!isValidLatLon(latitude, longitude)) {
            return FALLBACK_ELEVATION;
        }
        try {
            Object df = resolveDensityFunctions();
            if (df == null) {
                return FALLBACK_ELEVATION;
            }
            Method method = df.getClass().getMethod("getElevationFast", double.class, double.class);
            Object value = method.invoke(df, latitude, longitude);
            if (!(value instanceof Number number)) {
                return FALLBACK_ELEVATION;
            }
            double elevation = number.doubleValue();
            return Double.isNaN(elevation) ? FALLBACK_ELEVATION : elevation;
        } catch (Throwable t) {
            warnLimited("getElevation failed for " + latitude + "," + longitude + ": " + t.getMessage());
            return FALLBACK_ELEVATION;
        }
    }

    public CompletableFuture<Double> getElevationAsync(double latitude, double longitude) {
        return CompletableFuture.supplyAsync(() -> getElevation(latitude, longitude));
    }

    /**
     * Returns the top existing surface block Y. For placement above terrain, use getPlacementY().
     */
    public int getSurfaceY(@Nullable Level level, int blockX, int blockZ) {
        // Prefer the new Terra++ public API when present. It exposes explicit
        // Terra terrain surface Y and avoids reflection into generator internals.
        Integer apiSurfaceY = apiSurfaceY(blockX, blockZ);
        if (apiSurfaceY != null) {
            return clampY(apiSurfaceY);
        }

        // Terra++ terrain column is the source of truth for terrain surface.
        // Vanilla WORLD_SURFACE includes trees/leaves/buildings and caused Eternal
        // building plans to start on top of tree canopies instead of terrain.
        TerrainSnapshot snapshot = probe(blockX, blockZ);
        if (snapshot.valid()) {
            return clampY(snapshot.surfaceY());
        }

        if (level != null) {
            try {
                int firstAir = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
                return clampY(firstAir - 1);
            } catch (Throwable t) {
                warnLimited("level heightmap failed: " + t.getMessage());
            }
        }
        return FALLBACK_SEA_LEVEL;
    }

    /**
     * Returns Y coordinate one block above the surface.
     */
    public int getPlacementY(@Nullable Level level, int blockX, int blockZ) {
        return clampY(getSurfaceY(level, blockX, blockZ) + 1);
    }

    @Nullable
    public Object classifyTerrainColumn(int blockX, int blockZ) {
        try {
            Object df = resolveDensityFunctions();
            if (df == null) {
                return null;
            }
            Method method = df.getClass().getMethod("classify", int.class, int.class);
            return method.invoke(df, blockX, blockZ);
        } catch (Throwable t) {
            warnLimited("classifyTerrainColumn failed for " + blockX + "," + blockZ + ": " + t.getMessage());
            return null;
        }
    }

    public TerrainType classifyTerrain(int blockX, int blockZ) {
        Object column = classifyTerrainColumn(blockX, blockZ);
        if (column == null) {
            return TerrainType.UNKNOWN;
        }
        String nativeName = readEnumFieldName(column, "type", "UNKNOWN");
        return mapNativeTerrainType(nativeName);
    }

    public TerrainType classifyTerrainByGeo(double latitude, double longitude) {
        double[] block = fromGeo(latitude, longitude);
        if (block == null) {
            return TerrainType.UNKNOWN;
        }
        return classifyTerrain((int) Math.floor(block[0]), (int) Math.floor(block[1]));
    }

    public boolean isOcean(int blockX, int blockZ) {
        TerrainType type = classifyTerrain(blockX, blockZ);
        return type == TerrainType.OCEAN || type == TerrainType.SHALLOW_WATER;
    }

    public boolean isLand(int blockX, int blockZ) {
        return classifyTerrain(blockX, blockZ) == TerrainType.LAND;
    }

    public boolean isRiver(int blockX, int blockZ) {
        return classifyTerrain(blockX, blockZ) == TerrainType.RIVER;
    }

    public boolean isLake(int blockX, int blockZ) {
        return classifyTerrain(blockX, blockZ) == TerrainType.LAKE;
    }

    public boolean isOcean(double latitude, double longitude) {
        double[] block = fromGeo(latitude, longitude);
        return block != null && isOcean((int) Math.floor(block[0]), (int) Math.floor(block[1]));
    }

    public boolean isRiver(double latitude, double longitude) {
        double[] block = fromGeo(latitude, longitude);
        return block != null && isRiver((int) Math.floor(block[0]), (int) Math.floor(block[1]));
    }

    public boolean isLake(double latitude, double longitude) {
        double[] block = fromGeo(latitude, longitude);
        return block != null && isLake((int) Math.floor(block[0]), (int) Math.floor(block[1]));
    }

    public boolean isLand(double latitude, double longitude) {
        double[] block = fromGeo(latitude, longitude);
        return block != null && isLand((int) Math.floor(block[0]), (int) Math.floor(block[1]));
    }

    public String getWorldCoverClass(double latitude, double longitude) {
        if (!isValidLatLon(latitude, longitude)) {
            return "UNKNOWN";
        }
        try {
            Object wc = resolveWorldCover();
            if (wc == null) {
                return "UNKNOWN";
            }
            Object value;
            try {
                Method blocking = wc.getClass().getMethod("getClassBlocking", double.class, double.class, long.class);
                value = blocking.invoke(wc, latitude, longitude, 500L);
            } catch (NoSuchMethodException ignored) {
                Method nonBlocking = wc.getClass().getMethod("getClass", double.class, double.class);
                value = nonBlocking.invoke(wc, latitude, longitude);
            }
            return value == null ? "UNKNOWN" : String.valueOf(value);
        } catch (Throwable t) {
            warnLimited("getWorldCoverClass failed: " + t.getMessage());
            return "UNKNOWN";
        }
    }

    public TerrainSnapshot probe(int blockX, int blockZ) {
        Object column = classifyTerrainColumn(blockX, blockZ);
        double[] rawGeo = toGeo(blockX, blockZ);
        double lon = rawGeo != null ? rawGeo[0] : Double.NaN;
        double lat = rawGeo != null ? rawGeo[1] : Double.NaN;

        if (column == null) {
            return new TerrainSnapshot(blockX, blockZ, lon, lat, FALLBACK_ELEVATION,
                FALLBACK_SEA_LEVEL, TerrainType.UNKNOWN, "UNKNOWN", "UNKNOWN", false);
        }

        double elevation = readDoubleField(column, "elevationMeters", FALLBACK_ELEVATION);
        int surfaceY = readIntField(column, "surfaceY", FALLBACK_SEA_LEVEL);
        String nativeType = readEnumFieldName(column, "type", "UNKNOWN");
        TerrainType mappedType = mapNativeTerrainType(nativeType);
        String worldCoverClass = isValidLatLon(lat, lon) ? getWorldCoverClass(lat, lon) : "UNKNOWN";

        return new TerrainSnapshot(blockX, blockZ, lon, lat, elevation, surfaceY,
            mappedType, nativeType, worldCoverClass, true);
    }

    @Nullable
    private double[] apiRawToGeoLonLat(int blockX, int blockZ) {
        try {
            Class<?> api = Class.forName(TERRA_PUBLIC_API_CLASS);
            Method method = api.getMethod("rawToGeoLonLat", int.class, int.class);
            Object value = method.invoke(null, blockX, blockZ);
            if (!(value instanceof double[] raw) || !isValidLonLatArray(raw)) {
                return null;
            }
            return new double[] { raw[0], raw[1] };
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private double[] apiFromGeo(double latitude, double longitude) {
        try {
            Class<?> api = Class.forName(TERRA_PUBLIC_API_CLASS);
            Method method = api.getMethod("fromGeo", double.class, double.class);
            Object value = method.invoke(null, latitude, longitude);
            if (!(value instanceof double[] block) || block.length < 2
                || Double.isNaN(block[0]) || Double.isNaN(block[1])) {
                return null;
            }
            return new double[] { block[0], block[1] };
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private Integer apiSurfaceY(int blockX, int blockZ) {
        try {
            Class<?> api = Class.forName(TERRA_PUBLIC_API_CLASS);
            Method surfaceAt = api.getMethod("surfaceAt", int.class, int.class);
            Object sample = surfaceAt.invoke(null, blockX, blockZ);
            if (sample == null) {
                return null;
            }
            Method valid = sample.getClass().getMethod("valid");
            Object isValid = valid.invoke(sample);
            if (!(isValid instanceof Boolean ok) || !ok) {
                return null;
            }
            Method surfaceY = sample.getClass().getMethod("surfaceY");
            Object value = surfaceY.invoke(sample);
            return value instanceof Number number ? number.intValue() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public int getSeaLevel() {
        return readStaticInt(TERRA_DENSITY_FUNCTIONS_CLASS, "SEA_LEVEL", FALLBACK_SEA_LEVEL);
    }

    public int getMinY() {
        return readStaticInt(TERRA_DENSITY_FUNCTIONS_CLASS, "MIN_Y", FALLBACK_MIN_Y);
    }

    public int getMaxY() {
        return readStaticInt(TERRA_DENSITY_FUNCTIONS_CLASS, "MAX_Y", FALLBACK_MAX_Y);
    }

    private TerrainType mapNativeTerrainType(String nativeName) {
        if (nativeName == null) {
            return TerrainType.UNKNOWN;
        }
        return switch (nativeName) {
            case "LAND" -> TerrainType.LAND;
            case "OCEAN" -> TerrainType.OCEAN;
            case "SHALLOW_WATER" -> TerrainType.SHALLOW_WATER;
            case "RIVER", "RIVER_BANK", "RIVER_ISLAND_SHORE" -> TerrainType.RIVER;
            case "LAKE", "LAKE_SHORE", "LAKE_ISLAND_SHORE" -> TerrainType.LAKE;
            case "COASTLINE", "BELOW_SEA_WATER", "ISLAND_STEP" -> TerrainType.COASTAL;
            case "INVALID" -> TerrainType.INVALID;
            default -> TerrainType.UNKNOWN;
        };
    }

    private boolean isValidLonLatArray(@Nullable double[] value) {
        if (value == null || value.length < 2) {
            return false;
        }
        return isValidLatLon(value[1], value[0]);
    }

    public static boolean isValidLatLon(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
            && latitude >= -85.0d && latitude <= 85.0d
            && longitude >= -180.0d && longitude <= 180.0d;
    }

    private int clampY(int y) {
        return Math.max(getMinY(), Math.min(getMaxY(), y));
    }

    private int readStaticInt(String className, String fieldName, int fallback) {
        try {
            Class<?> cls = Class.forName(className);
            Field field = cls.getField(fieldName);
            Object value = field.get(null);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int readIntField(Object target, String fieldName, int fallback) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private double readDoubleField(Object target, String fieldName, double fallback) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof Number number ? number.doubleValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String readEnumFieldName(Object target, String fieldName, String fallback) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? fallback : String.valueOf(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void warnLimited(String message) {
        if (warningCounter.getAndIncrement() < 50) {
            LOGGER.warn("[TerraBridge] {}", message);
        } else {
            LOGGER.debug("[TerraBridge] {}", message);
        }
    }

    public enum TerrainType {
        LAND,
        OCEAN,
        SHALLOW_WATER,
        RIVER,
        LAKE,
        COASTAL,
        INVALID,
        UNKNOWN
    }

    /** High-level safe coordinate type. */
    public static final class GeoPoint {
        private final double latitude;
        private final double longitude;

        public GeoPoint(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double latitude() {
            return latitude;
        }

        public double longitude() {
            return longitude;
        }

        public boolean valid() {
            return isValidLatLon(latitude, longitude);
        }
    }

    /** Probe result used by future telemetry/anomaly systems. */
    public static final class TerrainSnapshot {
        private final int blockX;
        private final int blockZ;
        private final double longitude;
        private final double latitude;
        private final double elevationMeters;
        private final int surfaceY;
        private final TerrainType terrainType;
        private final String nativeTerrainType;
        private final String worldCoverClass;
        private final boolean valid;

        public TerrainSnapshot(int blockX, int blockZ, double longitude, double latitude,
                               double elevationMeters, int surfaceY, TerrainType terrainType,
                               String nativeTerrainType, String worldCoverClass, boolean valid) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.longitude = longitude;
            this.latitude = latitude;
            this.elevationMeters = elevationMeters;
            this.surfaceY = surfaceY;
            this.terrainType = terrainType;
            this.nativeTerrainType = nativeTerrainType;
            this.worldCoverClass = worldCoverClass;
            this.valid = valid;
        }

        public int blockX() { return blockX; }
        public int blockZ() { return blockZ; }
        public double longitude() { return longitude; }
        public double latitude() { return latitude; }
        public double elevationMeters() { return elevationMeters; }
        public int surfaceY() { return surfaceY; }
        public TerrainType terrainType() { return terrainType; }
        public String nativeTerrainType() { return nativeTerrainType; }
        public String worldCoverClass() { return worldCoverClass; }
        public boolean valid() { return valid; }
    }
}

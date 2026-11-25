package com.eternalengine.terrabridge;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.knowledge.KnowledgeDate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Bridge to Terra++ historical time.
 *
 * Terra++ terrain/structure refresh uses TerraTimeManager, so Eternal Engine
 * must mirror and command that same clock instead of running an unrelated year.
 */
public final class TerraTimeBridge {
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    private static final String TERRA_TIME_MANAGER = "net.buildtheearth.terraplusplus.generator.historical.TerraTimeManager";
    private static final String PERSISTENT_REFRESH = "net.buildtheearth.terraplusplus.generator.historical.PersistentChunkRefreshIndex";

    /**
     * The Terra++ time class and its methods, looked up once (step 14). Every year look-up used to search
     * for the class again; without the Terra++ fork each search threw and caught an exception (about 6
     * microseconds each in a plain JVM, more inside Forge), many times per tick. Whether the class exists
     * cannot change while the game runs.
     */
    private static final class Handles {
        final Class<?> cls;
        final Method getCurrentYear;
        final Method getTimeScale;
        final Method setTimeScale;
        final Method isTimeEnabled;
        final Method forceRecalculate;
        final int baseYear;
        final int daysPerYear;

        Handles(Class<?> cls) throws ReflectiveOperationException {
            this.cls = cls;
            this.getCurrentYear = cls.getMethod("getCurrentYear");
            this.getTimeScale = cls.getMethod("getTimeScale");
            this.setTimeScale = cls.getMethod("setTimeScale", int.class);
            this.isTimeEnabled = optional(cls, "isTimeEnabled");
            this.forceRecalculate = cls.getMethod("forceRecalculate", ServerLevel.class);
            this.baseYear = staticInt(cls, "BASE_YEAR", -3000);
            this.daysPerYear = staticInt(cls, "DAYS_PER_YEAR", 365);
        }

        private static Method optional(Class<?> cls, String name) {
            try {
                return cls.getMethod(name);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        private static int staticInt(Class<?> cls, String field, int fallback) {
            try {
                Object v = cls.getField(field).get(null);
                return v instanceof Number n ? n.intValue() : fallback;
            } catch (ReflectiveOperationException e) {
                return fallback;
            }
        }
    }

    private static volatile boolean looked;
    private static volatile Handles handles;

    private TerraTimeBridge() {
    }

    private static Handles handles() {
        if (!looked) {
            synchronized (TerraTimeBridge.class) {
                if (!looked) {
                    try {
                        handles = new Handles(Class.forName(TERRA_TIME_MANAGER));
                        LOGGER.info("[TerraTimeBridge] Terra++ time found: the world year can follow the Minecraft day count");
                    } catch (Throwable t) {
                        handles = null;
                        LOGGER.info("[TerraTimeBridge] No Terra++ time in this game ({}); the Eternal Engine clock runs the year",
                            t.getClass().getSimpleName());
                    }
                    looked = true;
                }
            }
        }
        return handles;
    }

    /**
     * Whether the year comes from Terra++ time: the Terra++ fork is installed and its time is switched on
     * (with it switched off the Terra++ year stands still, so the Eternal Engine clock runs the year).
     */
    public static boolean isAvailable() {
        Handles h = handles();
        if (h == null) {
            return false;
        }
        if (h.isTimeEnabled == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(h.isTimeEnabled.invoke(null));
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getCurrentYear(int fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        try {
            Object value = handles.getCurrentYear.invoke(null);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static int getTimeScale(int fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        try {
            Object value = handles.getTimeScale.invoke(null);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static int setTimeScale(int requestedScale, int fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        try {
            handles.setTimeScale.invoke(null, requestedScale);
            return getTimeScale(fallback);
        } catch (Throwable t) {
            LOGGER.debug("[TerraTimeBridge] setTimeScale failed: {}", t.getMessage());
            return fallback;
        }
    }

    /**
     * Change the Terra++ time scale and keep the date (step 14 kept the year; step 18d keeps the day too). The Terra++
     * year is {@code BASE_YEAR + dayCount * scale / DAYS_PER_YEAR}, so a new scale alone moves the date (10x to 25x
     * in 1948 lands past 9000). After the new scale the day count is set to the first day that shows the same date
     * or a later one at that scale (at 5x one Minecraft day is five days of history), keeping the time of day: the
     * date never goes back. Step 14 used the first day of the year, so 14 September became 1 January.
     *
     * @return the scale Terra++ uses now (it rounds to its own valid scales), or {@code fallback}
     */
    public static int setTimeScaleKeepingDate(MinecraftServer server, int requestedScale, int fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        ServerLevel level = server == null ? null : server.overworld();
        int oldScale = Math.max(1, getTimeScale(1));
        int scale = setTimeScale(requestedScale, fallback);
        if (level == null || Math.max(1, scale) == oldScale) {
            return scale;
        }
        long dayCount = Math.floorDiv(level.getDayTime(), 24000L);
        try {
            long worldDay = dayKeepingDate(dayCount, oldScale, scale, handles.baseYear, handles.daysPerYear);
            if (worldDay >= 0) {
                long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
                level.setDayTime(worldDay * 24000L + timeOfDay);
                handles.forceRecalculate.invoke(null, level);
            }
        } catch (Throwable t) {
            LOGGER.warn("[TerraTimeBridge] Could not keep the date {} at scale {}: {}", KnowledgeDate.label(
                KnowledgeDate.fromTerraDay(dayCount, oldScale, handles.baseYear, handles.daysPerYear)), scale, t.getMessage());
        }
        return scale;
    }

    /**
     * The Minecraft day count that shows the date of day {@code dayCount} at {@code oldScale} again at {@code newScale},
     * or the first later date it can (step 18d; pure, so the probe tests it without Terra++). terraDayFor keeps inside
     * the year; near its end (or on 31 December of a leap year, which a 365-day Terra++ year does not have) that can be
     * a day before the date: then the next day, even in the next year. -1 before the base year.
     */
    public static long dayKeepingDate(long dayCount, int oldScale, int newScale, int baseYear, int daysPerYear) {
        int key = KnowledgeDate.fromTerraDay(dayCount, Math.max(1, oldScale), baseYear, daysPerYear);
        int s = Math.max(1, newScale);
        long day = KnowledgeDate.terraDayFor(key, s, baseYear, daysPerYear);
        if (day < 0) {
            return -1L;
        }
        for (int i = 0; i < 3 && KnowledgeDate.fromTerraDay(day, s, baseYear, daysPerYear) < key; i++) {
            day++;
        }
        return day;
    }

    /** Tell Terra++ that the day count changed (step 18d: after /time set, or the calendar putting the day right). */
    public static void recalculate(MinecraftServer server) {
        if (server == null || !isAvailable()) {
            return;
        }
        try {
            ServerLevel level = server.overworld();
            if (level != null) {
                handles.forceRecalculate.invoke(null, level);
            }
        } catch (Throwable t) {
            LOGGER.debug("[TerraTimeBridge] recalculate failed: {}", t.getMessage());
        }
    }

    /**
     * The first Minecraft day of {@code year} at {@code scale}: the smallest day with
     * {@code baseYear + day * scale / daysPerYear == year}. Rounded up: rounding down (as before) could land
     * on the last day of the year before (1948 at 25x became 1947). Negative before the base year.
     */
    public static long dayForYear(int year, int baseYear, int daysPerYear, int scale) {
        long yearsFromBase = (long) year - baseYear;
        if (yearsFromBase < 0) {
            return -1L;
        }
        return Math.floorDiv(yearsFromBase * daysPerYear + scale - 1, scale);
    }

    /** Terra++'s first year (BASE_YEAR, -3000 in the fork), for working out the day inside a year (step 18b). */
    public static int baseYear() {
        Handles h = handles();
        return h == null ? -3000 : h.baseYear;
    }

    /** Days in a Terra++ year (DAYS_PER_YEAR, 365). */
    public static int daysPerYear() {
        Handles h = handles();
        return h == null ? 365 : Math.max(1, h.daysPerYear);
    }

    /**
     * Set the Minecraft day count, keeping the time of day (step 18b: /ee date inside the current year). The
     * year must not change, so no terrain refresh is needed: Terra++ only recalculates its year.
     */
    public static boolean setDayCount(MinecraftServer server, long worldDay) {
        if (server == null || worldDay < 0 || !isAvailable()) {
            return false;
        }
        try {
            ServerLevel level = server.overworld();
            if (level == null) {
                return false;
            }
            long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
            level.setDayTime(worldDay * 24000L + timeOfDay);
            handles.forceRecalculate.invoke(null, level);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[TerraTimeBridge] setDayCount failed: {}", t.getMessage());
            return false;
        }
    }

    /**
     * Go to the first day of {@code targetYear} with Terra++ time, keeping the time of day, and do the rest of Terra++'s
     * time jump (step 18e: {@link #refreshAfterJump}). The day is EE's own: rounded up, so it is never the last day of
     * the year before (Terra++'s command rounds down).
     */
    public static boolean jumpToYear(MinecraftServer server, int targetYear) {
        if (server == null || !isAvailable()) {
            return false;
        }
        try {
            ServerLevel level = server.overworld();
            if (level == null) {
                return false;
            }
            int scale = Math.max(1, getTimeScale(1));
            long worldDay = dayForYear(targetYear, handles.baseYear, handles.daysPerYear, scale);
            if (worldDay < 0) {
                return false;
            }
            long currentTimeOfDay = level.getDayTime() % 24000L;
            level.setDayTime(worldDay * 24000L + currentTimeOfDay);

            handles.forceRecalculate.invoke(null, level);

            int actualYear = getCurrentYear(targetYear);
            refreshAfterJump(level, actualYear);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[TerraTimeBridge] jumpToYear failed: {}", t.getMessage());
            return false;
        }
    }

    private static final String CHUNK_GENERATOR = "net.buildtheearth.terraplusplus.generator.TerraChunkGenerator";

    /** One step of Terra++'s time jump, called by reflection. */
    @FunctionalInterface
    private interface JumpStep {
        void run() throws Exception;
    }

    /**
     * After a year jump, what Terra++'s own {@code /terra timejump} does, in its order (step 18e). Before, only the refresh
     * generation moved, so the land around the players kept the old year until it was loaded again; now that /ee date is
     * the only way back, it must do the whole jump. A new refresh generation for the year, the jump audit, Terra++'s
     * rebuild queues emptied, the ring at the edge of the loaded land marked, every loaded chunk queued for rebuilding
     * (nearest first), the rescan of half-loaded chunks after 10 seconds, and Terra++'s time saved. A step this Terra++
     * does not have is skipped.
     */
    private static void refreshAfterJump(ServerLevel level, int year) {
        Class<?> refresh = classOrNull(PERSISTENT_REFRESH);
        Class<?> generator = classOrNull(CHUNK_GENERATOR);
        int done = 0;
        done += jumpStep("advanceRefreshGeneration", () -> refresh.getMethod("advanceRefreshGeneration", ServerLevel.class,
            int.class).invoke(null, level, year));
        done += jumpStep("beginTimeJumpAudit", () -> {
            Object generation = refresh.getMethod("getGeneration").invoke(null);
            refresh.getMethod("beginTimeJumpAudit", int.class, int.class).invoke(null, ((Number) generation).intValue(), year);
        });
        done += jumpStep("resetRefreshQueuesForTimeJump", () -> generator.getMethod("resetRefreshQueuesForTimeJump").invoke(null));
        done += jumpStep("captureTimeJumpBoundaryRing", () -> generator.getMethod("captureTimeJumpBoundaryRing",
            ServerLevel.class).invoke(null, level));
        done += jumpStep("scheduleLoadedChunkRefresh", () -> generator.getMethod("scheduleLoadedChunkRefresh",
            ServerLevel.class).invoke(null, level));
        done += jumpStep("postJumpRescanCountdown", () -> generator.getField("postJumpRescanCountdown").setInt(null, 200));
        done += jumpStep("save", () -> handles.cls.getMethod("save").invoke(null));
        Object queued = null;
        try {
            queued = generator == null ? null : generator.getMethod("getRefreshQueueSize").invoke(null);
        } catch (Throwable ignored) {
            // only for the log line
        }
        LOGGER.info("[TerraTimeBridge] Year jump to {}: {} of 7 Terra++ time-jump steps done{}", year, done,
            queued == null ? "" : "; " + queued + " loaded chunks queued for rebuilding, as /terra timejump does");
    }

    private static int jumpStep(String name, JumpStep step) {
        try {
            step.run();
            return 1;
        } catch (Throwable t) {
            LOGGER.debug("[TerraTimeBridge] Time-jump step {} skipped: {}", name, t.toString());
            return 0;
        }
    }

    @javax.annotation.Nullable
    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Determines if a 53-Carto infrastructure element is technologically valid in the given simulation year.
     */
    public static boolean isInfrastructureAllowed(String structureType, int year) {
        if (structureType == null) return true;
        String type = structureType.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "POWER_LINE", "POWER_TOWER", "PYLON", "SUBSTATION" -> year >= 1882;
            case "AERIALWAY", "CHAIR_LIFT", "CABLE_CAR" -> year >= 1900;
            case "RAILWAY", "RAIL", "LIGHT_RAIL", "SUBWAY" -> year >= 1825;
            case "AEROWAY", "RUNWAY", "APRON", "TAXIWAY" -> year >= 1910;
            case "MOTORWAY", "FREEWAY" -> year >= 1924;
            case "PIPELINE" -> year >= 1865;
            case "SOLAR_PANEL", "WIND_TURBINE" -> year >= 1970;
            default -> true;
        };
    }

    /**
     * Step 30a: the same, at a place: railways, power lines, runways, motorways, solar panels and wind turbines from the
     * year the technology atlas says they reached it (Hyderabad's railway from the 1870s, not 1825); the world-wide
     * year for the rest, and when the atlas is missing.
     */
    public static boolean isInfrastructureAllowed(String structureType, double lat, double lon, int year) {
        if (!isInfrastructureAllowed(structureType, year)) {
            return false;
        }
        if (structureType == null) {
            return true;
        }
        String id = switch (structureType.toUpperCase(Locale.ROOT)) {
            case "RAILWAY", "RAIL" -> "railway";
            case "LIGHT_RAIL" -> "tram";
            case "POWER_LINE", "POWER_TOWER", "PYLON", "SUBSTATION" -> "electricity";
            case "AEROWAY", "RUNWAY", "APRON", "TAXIWAY" -> "air_travel";
            case "MOTORWAY", "FREEWAY" -> "motorway";
            case "SOLAR_PANEL" -> "solar_panels";
            case "WIND_TURBINE" -> "wind_turbines";
            default -> null;
        };
        if (id == null) {
            return true;
        }
        try {
            var atlas = com.eternalengine.technology.TechnologyAtlas.get();
            var tech = atlas.tech(id);
            return !atlas.loaded() || tech == null
                    || atlas.share(tech, lat, lon, year, com.eternalengine.civilization.SettlementRank.CITY) > 0.0d;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Maps an OSM highway classification to a period-accurate roadway surface block.
     */
    public static BlockState getRoadBlockForEra(String highwayTag, int year) {
        String tag = highwayTag == null ? "road" : highwayTag.toLowerCase(Locale.ROOT);
        boolean isMajor = tag.contains("motorway") || tag.contains("trunk") || tag.contains("primary");

        if (year < 500) {
            // Classical Antiquity: Roman stone pavers for highways, dirt tracks for minor
            return isMajor ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
        } else if (year < 1850) {
            // Medieval & Early Modern: Cobblestone & packed mud
            return isMajor ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
        } else if (year < 1925) {
            // Industrial Era: Belgian paving stones, stone bricks, macadam gravel
            return isMajor ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        } else {
            // Modern Era: Asphalt concrete & smooth stone
            return isMajor ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        }
    }

    /**
     * Maps street lighting to period-accurate illumination sources.
     */
    public static BlockState getLampBlockForEra(int year) {
        if (year < 1880) {
            return Blocks.LANTERN.defaultBlockState();
        } else {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
    }
}

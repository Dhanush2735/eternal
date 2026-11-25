package com.eternalengine.core;

import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Step 26 (system A1): the one key every answer depends on. What is true here, at this date, in this timeline and in
 * this universe? Every game system asks {@link WorldQuery} with a key instead of a bare latitude, longitude and year.
 *
 * <ul>
 *   <li><b>place</b>: latitude and longitude on the real Earth (NaN when a position has no place on the map);</li>
 *   <li><b>date</b>: year, month and day ({@link KnowledgeDate});</li>
 *   <li><b>timeline</b>: the branch of history ("main" is history nobody changed; a player's intervention forks a
 *       branch, step 14);</li>
 *   <li><b>universe</b>: decision 3 of the systems plan, universes are dimensions. The overworld is the prime
 *       universe, where recorded history lives; a broken-paradox universe (systems H) will be a dimension of its
 *       own with its own history.</li>
 * </ul>
 */
public record WorldKey(double lat, double lon, int year, int month, int day, String timeline, String universe) {

    /** The timeline nobody changed. */
    public static final String MAIN = "main";
    /** The overworld: the universe of recorded history. */
    public static final String PRIME = "prime";

    public WorldKey {
        timeline = timeline == null || timeline.isBlank() ? MAIN : timeline;
        universe = universe == null || universe.isBlank() ? PRIME : universe;
        month = Math.max(1, Math.min(12, month));
        day = Math.max(1, Math.min(31, day));
    }

    /** The record itself: main timeline, prime universe (checks and tools). */
    public static WorldKey prime(double lat, double lon, int year) {
        return new WorldKey(lat, lon, year, 1, 1, MAIN, PRIME);
    }

    /** A place and year in the world as it is now: the active timeline, the prime universe. */
    public static WorldKey at(double lat, double lon, int year) {
        return new WorldKey(lat, lon, year, 1, 1, activeTimeline(), PRIME);
    }

    /** A place on a date key ({@link KnowledgeDate#key}) in the active timeline, the prime universe. */
    public static WorldKey onDate(double lat, double lon, int dateKey) {
        return new WorldKey(lat, lon, KnowledgeDate.year(dateKey), KnowledgeDate.month(dateKey),
                KnowledgeDate.day(dateKey), activeTimeline(), PRIME);
    }

    /** A block in a level, in a year: its place on the map, the active timeline, the level's universe. */
    public static WorldKey at(Level level, BlockPos pos, int year) {
        double lat = Double.NaN;
        double lon = Double.NaN;
        try {
            var geo = TerraCoordinateHelper.toGeoPoint(pos);
            if (geo != null) {
                lat = geo.latitude();
                lon = geo.longitude();
            }
        } catch (RuntimeException ignored) {
            // no projection (no Terra++ world): the key has no place
        }
        return new WorldKey(lat, lon, year, 1, 1, activeTimeline(), universeOf(level));
    }

    /** The universe a level belongs to: the overworld is {@link #PRIME}, any other dimension its own id. */
    public static String universeOf(Level level) {
        if (level == null) {
            return PRIME;
        }
        return universeOf(level.dimension());
    }

    public static String universeOf(ResourceKey<Level> dimension) {
        if (dimension == null || Level.OVERWORLD.equals(dimension)) {
            return PRIME;
        }
        return dimension.location().toString();
    }

    /** The timeline the world is on now ("main" before the engine starts). */
    public static String activeTimeline() {
        try {
            if (SimulationEngine.isInitialized()) {
                var tm = SimulationEngine.getInstance().getTimelineManager();
                if (tm != null) {
                    return tm.activeBranchId();
                }
            }
        } catch (RuntimeException ignored) {
            // engine shutting down
        }
        return MAIN;
    }

    public boolean placed() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    public boolean isPrime() {
        return PRIME.equals(universe);
    }

    public boolean isMain() {
        return MAIN.equals(timeline);
    }

    public int dateKey() {
        return KnowledgeDate.key(year, month, day);
    }

    public WorldKey withYear(int newYear) {
        return new WorldKey(lat, lon, newYear, month, day, timeline, universe);
    }

    public WorldKey withPlace(double newLat, double newLon) {
        return new WorldKey(newLat, newLon, year, month, day, timeline, universe);
    }

    public WorldKey inTimeline(String branch) {
        return new WorldKey(lat, lon, year, month, day, branch, universe);
    }

    public WorldKey inUniverse(String newUniverse) {
        return new WorldKey(lat, lon, year, month, day, timeline, newUniverse);
    }

    /** "18.94, 72.83 in 1948-01-01, timeline main, universe prime". */
    public String display() {
        return String.format(java.util.Locale.ROOT, "%.4f, %.4f in %d-%02d-%02d, timeline %s, universe %s",
                lat, lon, year, month, day, timeline, universe);
    }
}

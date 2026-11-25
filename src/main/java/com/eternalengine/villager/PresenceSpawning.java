package com.eternalengine.villager;

/**
 * Minecraft's mob spawning rules, expressed on their own so the spawner can follow them.
 *
 * <h3>Why this exists</h3>
 * The spawner needs to answer two questions — where may a person appear, and when do they go away —
 * and vanilla Minecraft already answers both, in ways players recognise:
 *
 * <table>
 *   <tr><th>vanilla</th><th>value</th></tr>
 *   <tr><td>nothing spawns within this many blocks of a player</td><td>24</td></tr>
 *   <tr><td>spawn distance, and instant despawn beyond it</td><td>128</td></tr>
 *   <tr><td>no random despawn closer than this</td><td>32</td></tr>
 *   <tr><td>random despawn chance per tick, past 32 blocks</td><td>1 in 800</td></tr>
 *   <tr><td>ticks with no player within 32 blocks before the random despawn starts</td><td>600</td></tr>
 * </table>
 *
 * <p>The constants are asserted in the test suite, so a later edit cannot quietly turn them into
 * something that is no longer vanilla's behaviour.
 *
 * <p>No Minecraft types here on purpose: this is the part of the decision that can be tested
 * without a running server.
 */
public final class PresenceSpawning {

    /** Vanilla: a mob never spawns within 24 blocks of a player. */
    public static final int MIN_SPAWN_BLOCKS = 24;

    /** Vanilla: the spawn distance, and the distance beyond which a mob despawns immediately. */
    public static final int MAX_SPAWN_BLOCKS = 128;

    /** Vanilla: a mob inside 32 blocks is never randomly despawned. */
    public static final int RANDOM_DESPAWN_BLOCKS = 32;

    /** Vanilla: one chance in this many per tick, past 32 blocks. */
    public static final int DESPAWN_ONE_IN = 800;

    /** Vanilla: 600 ticks — 30 seconds — with no player in range before that chance begins. */
    public static final int DESPAWN_DELAY_TICKS = 600;

    /** Candidate positions tried before a cycle gives up. Vanilla retries across a 17x17 chunk area. */
    public static final int ATTEMPTS = 12;

    /**
     * The grid a crowd district is snapped to, in blocks — two chunks.
     *
     * <p>Snapping is what stops the crowd from being a ring that follows the player. An unsnapped
     * centre would move one block for every block the player moved, so people placed a moment ago
     * would fall outside their own district and be replaced: churn instead of a crowd. Snapped, the
     * district stays put while the player moves around inside it, and the people stay where they
     * were put.
     */
    public static final int DISTRICT_GRID = 32;

    /** An offset from the player, or from a district centre, in blocks. */
    public record Spot(double dx, double dz, double distance) {}

    private PresenceSpawning() {}

    /** The centre of the grid cell this coordinate falls in: a stable place, not a moving one. */
    public static int snapToGrid(int value, int grid) {
        int cell = Math.max(1, grid);
        return Math.floorDiv(value, cell) * cell + cell / 2;
    }

    /**
     * A position inside a district: uniform over the <i>area</i> of a disc of this radius, so the
     * crowd fills the area rather than ringing its edge.
     */
    public static Spot spotInDisc(long seed, int index, int attempt, int radius) {
        int r = Math.max(1, radius);
        double area = unit(seed, index, attempt, 7);
        double angle = 2.0d * Math.PI * unit(seed, index, attempt, 8);
        double distance = Math.sqrt(area) * r;
        return new Spot(Math.cos(angle) * distance, Math.sin(angle) * distance, distance);
    }

    /**
     * How far apart two knots of a crowd stand, in blocks.
     *
     * <p>A knot is three blocks across, so at four blocks the groups would touch and read as one
     * shapeless mass, and two could land on the same block — which renders as one person, not two.
     * Five leaves a two-block alley between neighbours: close enough to look like a crowd, separate
     * enough to count its groups, the way people actually stand in a market or a temple yard.
     */
    public static final int KNOT_CLEARANCE = 5;

    /**
     * Whether a knot may stand here, given the knots already placed this cycle.
     *
     * <p>Pure so it can be checked directly: the anchor positions the spawner draws are random, but
     * this rule about them is not.
     */
    public static boolean clearOf(int x, int z, java.util.List<int[]> taken, int clearance) {
        double limit = (double) clearance * clearance;
        for (int[] other : taken) {
            double dx = x - other[0];
            double dz = z - other[1];
            if (dx * dx + dz * dz < limit) {
                return false;
            }
        }
        return true;
    }

    public static boolean withinDisc(double dx, double dz, int radius) {
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /**
     * The core of a district: where the knots are placed.
     *
     * <p>Tighter than the district itself on purpose. The district is the area the crowd belongs to
     * and is leashed to; the core is where thirty people actually stand. Two thirds of the radius
     * means roughly four times the density for the same count — and density, not count, is what the
     * eye reads as a crowd.
     */
    public static int coreRadius(int districtRadius) {
        return Math.max(6, (int) Math.round(districtRadius * 0.66d));
    }

    /**
     * A candidate position for the {@code attempt}-th try at NPC {@code index}.
     *
     * <p>Uniform over the <i>area</i> of the ring between {@code minBlocks} and {@code maxBlocks} —
     * the same distribution a uniformly chosen block within the spawn distance gives once the
     * 24-block exclusion is applied. Deterministic in {@code seed}, so the same inputs always give
     * the same spot and a test can check it.
     */
    public static Spot spot(long seed, int index, int attempt, int minBlocks, int maxBlocks) {
        int min = Math.max(0, minBlocks);
        int max = Math.max(min + 1, maxBlocks);
        double area = unit(seed, index, attempt, 1);
        double angle = 2.0d * Math.PI * unit(seed, index, attempt, 2);
        double radius = Math.sqrt((double) min * min + area * ((double) max * max - (double) min * min));
        return new Spot(Math.cos(angle) * radius, Math.sin(angle) * radius, radius);
    }

    /**
     * Whether a crowd member should stop thinking.
     *
     * <p>The awake bubble is deliberately the same 32 blocks as vanilla's no-random-despawn bubble:
     * you pay for the thinking of exactly the people vanilla would refuse to despawn anyway. Inside
     * that ring they are in play — looking at you, walking their district; outside it they are
     * scenery, and scenery does not need a goal selector.
     *
     * <p>{@code groundGone} overrides everything: NoAI also stops a mob falling, so anyone whose
     * ground has been dug out stays awake and sorts itself out rather than hanging in the air.
     * Vanilla's own rule is the same shape — mobs outside the simulation distance are not ticked
     * at all.
     */
    public static boolean shouldSettle(double nearestPlayerDistanceSq, double settleBlocks, boolean groundGone) {
        if (groundGone) {
            return false;
        }
        return nearestPlayerDistanceSq > settleBlocks * settleBlocks;
    }

    /** Vanilla's instant rule: further than the spawn distance from every player means gone now. */
    public static boolean farBeyond(double distanceSq) {
        return distanceSq > (double) MAX_SPAWN_BLOCKS * MAX_SPAWN_BLOCKS;
    }

    /**
     * Vanilla's random despawn: past 32 blocks, after 600 ticks away, one chance in 800.
     *
     * <p>A cycle is {@code cycleTicks} vanilla ticks, so the roll is compared against
     * {@code cycleTicks} rather than 1: forty separate 1-in-800 chances are the same as one 40-in-800
     * chance, and the expected lifetime is unchanged.
     *
     * @param distanceSq  to the nearest player
     * @param roll        {@code random.nextInt(DESPAWN_ONE_IN)}
     * @param awayTicks   ticks spent outside the 32-block bubble
     * @param cycleTicks  how many ticks this one check stands in for
     */
    public static boolean shouldDespawn(double distanceSq, int roll, int awayTicks, int cycleTicks) {
        if (farBeyond(distanceSq)) {
            return true;
        }
        if (distanceSq <= (double) RANDOM_DESPAWN_BLOCKS * RANDOM_DESPAWN_BLOCKS) {
            return false;
        }
        if (awayTicks < DESPAWN_DELAY_TICKS) {
            return false;
        }
        return roll < cycleTicks;
    }

    /** A value in {@code [0, 1)} from the seed and two ints. No state, no allocation. */
    static double unit(long seed, int a, int b, int salt) {
        long h = hash(seed, a * 4 + salt, b);
        return (h >>> 11) * 0x1.0p-53;
    }

    /** splitmix-style mix. */
    static long hash(long seed, int a, int b) {
        long h = seed + 0x9E3779B97F4A7C15L * (a + 1L) + 0xBF58476D1CE4E5B9L * (b + 1L);
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return h;
    }
}

package com.eternalengine.population;

import com.eternalengine.civilization.SettlementRank;

/**
 * How many people to render for a place, given how many the simulation says live there.
 *
 * <h3>Rendered people are a sample, never the population</h3>
 * A Minecraft server degrades long before a thousand ticking entities, so the count cannot grow
 * linearly with population. It grows with the <em>logarithm</em>: a hamlet of 350 gets five people,
 * a metropolis of a million gets forty. What that buys is the difference between an empty
 * field and a city at the scale the player actually perceives — and past the ceiling, the rest of
 * the crowd is carried by {@code AmbientCrowdSoundManager} and by nobody being nameable from a
 * distance, not by more bodies.
 *
 * <h3>Why the numbers are what they are</h3>
 * Vanilla Minecraft runs a cap of 70 monsters and 10 creatures around one player and stays smooth.
 * These ceilings sit well inside that: 10 / 20 / 40. A human NPC is more expensive than a zombie —
 * it strolls, looks at the player, wears armour layers, carries a custom name — so the ceiling is
 * deliberately about half of vanilla's most expensive category. The floors exist so a place that is
 * occupied always has somebody in it, whatever the population says.
 *
 * <table>
 *   <tr><th>place</th><th>population</th><th>rank</th><th>rendered</th></tr>
 *   <tr><td>Akanpalle, 2011 (real)</td><td>1,047</td><td>village</td><td>6</td></tr>
 *   <tr><td>Maheswaram mandal</td><td>8,795</td><td>town</td><td>15</td></tr>
 *   <tr><td>Secunderabad, 1948</td><td>142,174</td><td>city</td><td>35</td></tr>
 *   <tr><td>Hyderabad, 1948</td><td>980,600</td><td>city</td><td>40</td></tr>
 * </table>
 *
 * <p>This curve <b>is</b> the spawner's population input: the resolver produces the number, this
 * turns it into a count, and {@code PresenceLocalSpawner} places that many people using Minecraft's
 * mob spawning rules. Split out of the spawner so all of it can be tested without a running server.
 */
public final class SettlementDensity {

    private SettlementDensity() {}

    /** Minimum rendered people for a place of this rank, whatever the population. */
    public static int floor(SettlementRank rank) {
        if (rank == SettlementRank.CITY) return 8;
        if (rank == SettlementRank.TOWN) return 4;
        if (rank == SettlementRank.COUNTRYSIDE) return 1;   // a camp, not a village (turn 231)
        return 2;
    }

    /**
     * Maximum rendered people, no matter how large the population. Tick budget is the ceiling.
     *
     * <p>10 / 20 / 40. The city figure is the one that matters and it is a deliberate 25% raise on
     * 32, landing in the band the field agrees on: the popular NPC mods report their comfort zone
     * at 40-50 people and degrade past it, and a 200-person colony halves a server's tick rate —
     * and those mods let their people pathfind, which ours no longer do.
     *
     * <p>Not larger, for a physical reason rather than a cautious one: past about 40 the square
     * stops being crowded and starts being clipped. In one 28-block district, 40 people means
     * roughly 25 m² each; 64 would be 16 m² each, which is people standing inside each other.
     *
     * <p>The spawner applies {@code presenceCrowdCeiling} on top of this, so the number can be
     * tuned down in-game without a rebuild.
     */
    public static int ceiling(SettlementRank rank) {
        if (rank == SettlementRank.CITY) return 40;
        if (rank == SettlementRank.TOWN) return 20;
        if (rank == SettlementRank.COUNTRYSIDE) return 4;   // open country draws at most 4 (turn 231)
        return 10;
    }

    /**
     * The cap for this place: how many people may stand here at once.
     *
     * @param population resolved population, or 0 if unknown
     * @param rank       the gazetteer's rank for this place and year; null is treated as village
     */
    public static int npcCap(long population, SettlementRank rank) {
        SettlementRank r = rank == null ? SettlementRank.VILLAGE : rank;
        int min = floor(r);
        int max = ceiling(r);
        if (population <= 0L) {
            return min;
        }
        return (int) Math.round(min + (max - min) * saturation(population));
    }

    /** How full the log scale is for this population: 0 at 10 people, 1 at a million. */
    public static double saturation(long population) {
        return Math.min(1.0d, Math.log10(Math.max(10L, population)) / 6.0d);
    }

    /**
     * How many people stand together, in one knot.
     *
     * <p>Vanilla spawns mobs in packs — a group of two to four at one position — and a knot of
     * people is also what a market square looks like. Spreading thirty people evenly over a
     * two-hundred-block ring would read as thirty isolated figures, which is the opposite of a
     * crowd.
     */
    public static int packSize(SettlementRank rank) {
        if (rank == SettlementRank.CITY) return 4;
        if (rank == SettlementRank.TOWN) return 3;
        return 2;
    }

    /**
     * How many knots to place this cycle.
     *
     * <p>Not all at once: filling a city's thirty-two people in one tick is a visible hitch and a
     * wall of bodies appearing together. Vanilla spreads spawning over ticks and chunks; four packs
     * per cycle fills a metropolis in two cycles — four seconds — while a village is complete in
     * one.
     */
    public static int packsPerCycle(int missing, SettlementRank rank) {
        if (missing <= 0) {
            return 0;
        }
        int packSize = packSize(rank);
        int needed = (int) Math.ceil(missing / (double) packSize);
        return Math.min(4, needed);
    }

    /**
     * How strongly this place registers, on one 0..1 scale shared by everything the player
     * perceives: how many people stand here, how loud they are, how often they are heard.
     *
     * <p>Sharing the scale is the point. A place that resolves to thirty-two rendered people also
     * sounds like a crowd, and a place that resolves to five does not — instead of the sound having
     * its own unrelated thresholds, which is how a hamlet and a metropolis could end up sounding
     * the same.
     *
     * @param visiblePeople people actually standing near the player, used as a floor so a genuine
     *                      knot of actors is never silent while the resolver has nothing to say
     */
    public static double intensity(long population, int visiblePeople) {
        double fromPopulation = population <= 0L ? 0.0d : saturation(population);
        double fromVisible = Math.min(1.0d, Math.max(0, visiblePeople) / 32.0d);
        return Math.max(fromPopulation, fromVisible);
    }

    /** Volume for the ambient crowd bed: quiet at the edge of the scale, present at the top. */
    public static float soundVolume(double intensity) {
        return (float) (0.08d + 0.32d * clamp01(intensity));
    }

    /** How often the ambient bed speaks: a busy crowd murmurs twice as often as a quiet one. */
    public static int soundIntervalTicks(double intensity) {
        return (int) Math.round(120.0d - 80.0d * clamp01(intensity));
    }

    /** Whether this place is busy enough for the low murmur rather than an occasional voice. */
    public static boolean murmurBand(double intensity) {
        return clamp01(intensity) >= 0.55d;
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    /**
     * How many of each role to render, so the crowd keeps the composition the gazetteer asked for
     * instead of being one role repeated. Sum is {@code budget}; the remainder is spread over the
     * first roles, so a budget of 29 over 4 roles gives 8,7,7,7.
     */
    /**
     * Like {@link #roleSlots(int, int)} for named roles, but a money-changer (step 19b) gets one place, and none below
     * three people: a market has a money-changer, not a quarter of its crowd.
     */
    public static int[] roleSlots(int budget, String[] roles) {
        if (roles == null) {
            return new int[0];
        }
        // Step 19b and 19d: the money-changer and the one person who brings the news get one place each, none in a
        // crowd below three; the other roles share the rest as before.
        boolean[] one = new boolean[roles.length];
        int few = 0;
        for (int i = 0; i < roles.length; i++) {
            one[i] = roles[i] != null && ONE_EACH.contains(roles[i].toLowerCase(java.util.Locale.ROOT));
            few += one[i] ? 1 : 0;
        }
        if (few == 0 || roles.length - few < 1) {
            return roleSlots(budget, roles.length);
        }
        int each = budget >= 3 ? 1 : 0;
        int fewSlots = Math.min(few * each, Math.max(0, budget - 1));
        int[] rest = roleSlots(budget - fewSlots, roles.length - few);
        int[] out = new int[roles.length];
        int given = 0;
        for (int i = 0, j = 0; i < roles.length; i++) {
            if (one[i]) {
                out[i] = given < fewSlots ? 1 : 0;
                given += out[i];
            } else {
                out[i] = j < rest.length ? rest[j++] : 0;
            }
        }
        return out;
    }

    /** Roles that stand once in a crowd (step 19b, 19d; the town trader and the village pedlar, step 19g). */
    public static final java.util.Set<String> ONE_EACH = java.util.Set.of("money_changer", "paper_seller", "news_writer",
            "town_crier", "trader", "pedlar");

    public static int[] roleSlots(int budget, int roleCount) {
        if (roleCount <= 0 || budget <= 0) {
            return new int[0];
        }
        int[] slots = new int[roleCount];
        int each = budget / roleCount;
        int remainder = budget % roleCount;
        for (int i = 0; i < roleCount; i++) {
            slots[i] = each + (i < remainder ? 1 : 0);
        }
        return slots;
    }
}

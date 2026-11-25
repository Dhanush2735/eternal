package com.eternalengine.skin;

import com.eternalengine.people.LifeTable;

/**
 * How old this person is, in years, and what that makes them.
 *
 * <p>Before this class the skin took an age from a hash of the person's own number: a twentieth
 * young, two thirds grown, a seventh old, in every century and every trade alike, so a village of
 * the Bronze Age and a street of today had exactly the same age structure, and a soldier was as
 * likely to be seventy as a scholar. Nothing about that was a fact about the place.
 *
 * <p>The mod already owns a proper answer: {@link LifeTable}, a survival table per place and year,
 * sourced where it can be and marked estimated where it cannot. This class asks it. The age of a
 * living person is drawn from that table's own survivor curve - the chance of being alive at a given
 * age is exactly the share who reach it - so the ages follow the era and the ground, and they move
 * when the table moves.
 *
 * <p>Children are <b>counted</b> but not yet <b>spawned</b>: a child needs a smaller model, and the
 * model belongs to the crowd, which is frozen. {@link #childShare} says how many a street should
 * have, so the gap is measured rather than forgotten.
 */
public final class Ages {

    /** The youngest age this system will put in the street until the owner allows a child's model. */
    public static final int YOUNGEST_SPAWNED = 16;
    /** The age at which a person is drawn as old: grey hair, a heavier face, a beard grown out. */
    public static final int OLD_FROM = 55;
    /** The age below which a person is drawn as young. */
    public static final int GROWN_FROM = 20;

    private Ages() {
    }

    /**
     * The age in years of a person alive at this place and this year.
     *
     * @param seed the person's own number - the same person always gets the same age
     * @param role their trade, because a soldier is young and a scholar is not
     */
    public static int years(long seed, int year, double lat, double lon, String role) {
        LifeTable life = LifeTable.get();
        LifeTable.Mortality m = life.mortalityAt(lat, lon, year);
        double[] s = m == null ? null : life.survival(m);
        int max = s == null ? 72 : Math.max(YOUNGEST_SPAWNED + 2, s.length - 1);
        int span = max - YOUNGEST_SPAWNED + 1;

        // how likely a living person of this age is: the share of newborns who reached it
        double[] w = new double[span];
        double total = 0.0d;
        for (int k = 0; k < span; k++) {
            int age = YOUNGEST_SPAWNED + k;
            double survive = s == null ? Math.exp(-(age - YOUNGEST_SPAWNED) / 24.0d)
                    : (age < s.length ? Math.max(0.0d, s[age]) : 0.0d);
            w[k] = survive * roleWeight(role, age);
            total += w[k];
        }
        if (total <= 0.0d) {
            return 30;
        }
        double r = uniform(mix(seed ^ 0xA6E4C31BL)) * total;
        for (int k = 0; k < span; k++) {
            r -= w[k];
            if (r <= 0.0d) {
                return YOUNGEST_SPAWNED + k;
            }
        }
        return max;
    }

    /** A soldier is young and a scholar is not: the trade leans the table, it does not overturn it. */
    private static double roleWeight(String role, int age) {
        if (role == null) {
            return 1.0d;
        }
        return switch (role) {
            case "soldier", "guard" -> Math.exp(-(age - YOUNGEST_SPAWNED) / 9.0d);
            case "scribe", "scholar", "priest", "advisor", "healer" ->
                    Math.pow(age / 30.0d, 1.4d);
            case "elder" -> Math.pow(age / 20.0d, 2.0d);
            default -> 1.0d;
        };
    }

    /** Step 2's bucket, from the number of years: one age, so the record and the face cannot disagree. */
    public static NpcProfile.Age bucket(int years) {
        if (years < YOUNGEST_SPAWNED) {
            return NpcProfile.Age.CHILD;
        }
        if (years < GROWN_FROM) {
            return NpcProfile.Age.YOUTH;      // sixteen to nineteen: grown enough to work, not a man yet
        }
        return years < OLD_FROM ? NpcProfile.Age.ADULT : NpcProfile.Age.ELDER;
    }

    /** What the painter draws: 0 young, 1 grown, 2 old. Derived from the same years as the bucket. */
    public static int paintAge(int years) {
        if (years < GROWN_FROM) {
            return 0;
        }
        return years < OLD_FROM ? 1 : 2;
    }

    /**
     * The share of the living who are children, from the same life table.
     *
     * <p>This is what a street of this place and year <b>should</b> look like. The street spawns
     * none of them yet, because a child needs a smaller model and the crowd is frozen.
     */
    public static double childShare(double lat, double lon, int year) {
        LifeTable life = LifeTable.get();
        LifeTable.Mortality m = life.mortalityAt(lat, lon, year);
        double[] s = m == null ? null : life.survival(m);
        if (s == null) {
            return 0.35d;
        }
        double child = 0.0d, all = 0.0d;
        for (int a = 0; a < s.length; a++) {
            all += Math.max(0.0d, s[a]);
            if (a < YOUNGEST_SPAWNED) {
                child += Math.max(0.0d, s[a]);
            }
        }
        return all <= 0.0d ? 0.0d : child / all;
    }

    /** The life table's own words for this place and year, for a report. */
    public static String line(double lat, double lon, int year) {
        LifeTable life = LifeTable.get();
        LifeTable.Mortality m = life.mortalityAt(lat, lon, year);
        if (m == null) {
            return "no table for this ground";
        }
        return String.format(java.util.Locale.ROOT, "%s, %.1f years at birth, %s", m.title(),
                m.atBirth(), m.estimated() ? "estimated" : "sourced");
    }

    // ------------------------------------------------------------------ numbers

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static double uniform(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }
}

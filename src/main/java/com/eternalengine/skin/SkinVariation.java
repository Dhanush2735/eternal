package com.eternalengine.skin;

import java.util.List;

/**
 * The variation of one person, drawn from who they are.
 *
 * <h3>Why this exists</h3>
 * The painter could put 319 different people in front of a player. The reason was not a shortage of
 * drawings: it was that a person was described by <b>three</b> things - a tone, a hair colour and a
 * garment name - and everything else about them was rounded to the nearest one of those. Two farmers
 * from the same town in the same year were the same man.
 *
 * <p>This class gives every person <b>eleven</b> more things to differ in, chosen by their own UUID so
 * they never change from day to day: how their eyes are set, the line of the brow, the nose, the mouth,
 * the beard, their age, their build, how much hair they carry and how it falls, the weave of the cloth
 * they wear, the jewellery their family wears, and whether the women of their house veil.
 *
 * <h3>The rule it obeys</h3>
 * A face never changes; clothes change every day. Everything here is a function of the <b>person</b>
 * (the seed) and of <b>where they are</b> (the culture), never of the day - which is the rule the pool
 * already keeps for tone and hair, extended to the rest of the face.
 */
public record SkinVariation(int eye, int brow, int nose, int mouth, int beard, int age,
                            int build, int hairVolume, int pattern, int patternStrength,
                            int jewelSet, boolean veil, int seed) {

    /** Eyes: how the two or three pixels of an eye are set. */
    public static final String[] EYES = {"round", "almond", "narrow", "heavy-lidded", "wide-set", "close-set"};

    /** The line of the brow. */
    public static final String[] BROWS = {"level", "arched", "heavy", "fine", "one raised"};

    /** The nose: the two pixels between the eyes and the mouth. */
    public static final String[] NOSES = {"small", "straight", "broad", "hooked", "tip-up"};

    /** The mouth. */
    public static final String[] MOUTHS = {"level", "full", "thin", "down-turned", "smiling"};

    /** The beard, for the men of a culture whose styles carry one. */
    public static final String[] BEARDS = {"clean", "stubble", "moustache", "goatee", "full", "forked"};

    public static final String[] AGES = {"young", "adult", "elder"};
    public static final String[] BUILDS = {"slim", "normal", "broad"};

    /**
     * The weaves, as a name per culture. Which cloth a place knows is a fact about the place, not about
     * the person - a Deccan village in 1400 knows block print; Rome knows a plain weave with a stripe
     * down it. The person decides only how strong it is.
     */
    public static final String[] PATTERNS = {"plain", "stripe_v", "stripe_h", "check", "dots",
            "ikat", "brocade", "wave", "diamond", "weave", "blocks"};

    private static final java.util.Map<String, int[]> CULTURE_WEAVE = new java.util.HashMap<>();

    static {
        CULTURE_WEAVE.put("deccan", new int[]{5, 10, 1, 4});     // block print, blocks, a stripe, dots
        CULTURE_WEAVE.put("indic", new int[]{10, 1, 4, 0});
        CULTURE_WEAVE.put("maurya", new int[]{0, 1, 10, 4});
        CULTURE_WEAVE.put("persianate", new int[]{6, 4, 0, 3});  // brocade above all
        CULTURE_WEAVE.put("mesopotamian", new int[]{9, 0, 3, 2});
        CULTURE_WEAVE.put("mediterranean", new int[]{9, 1, 3, 8});
        CULTURE_WEAVE.put("rome", new int[]{1, 0, 9, 3});        // the clavi: a stripe down the tunic
        CULTURE_WEAVE.put("north_african", new int[]{1, 2, 3, 10});
        CULTURE_WEAVE.put("african", new int[]{3, 1, 2, 10});    // kente checks and strip-weave
        CULTURE_WEAVE.put("east_asian", new int[]{6, 7, 0, 8});
        CULTURE_WEAVE.put("americas", new int[]{8, 1, 5, 3});    // step-fret diamonds
        CULTURE_WEAVE.put("global", new int[]{0, 9, 1, 3});
    }

    /** A 64-bit mix: the same seed always gives the same person, and nearby seeds do not look alike. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static int pick(long seed, String salt, int n) {
        return (int) Math.floorMod(mix(seed ^ salt.hashCode() * 0x9E3779B97F4A7C15L), n);
    }

    /** One in {@code n} people. */
    private static boolean chance(long seed, String salt, int n) {
        return pick(seed, salt, n) == 0;
    }

    /**
     * The person, from their seed and their place.
     *
     * @param seed        the person's own number, stable for life
     * @param culture     the pool they draw from
     * @param style       their hair style, which already says whether a beard belongs on them
     * @param female      the cut of the clothes, and whether the jewellery list applies
     * @param jewels      the culture's own jewellery list - {@code bindi_nose_bangle} and the like
     * @param year        only to keep an elder out of the cradle and a child off the throne
     */
    public static SkinVariation of(long seed, String culture, String style, boolean female, int year,
                                   String jewels) {
        return of(seed, culture, style, female, year, jewels, -1);
    }

    /**
     * The same, with the person's age in years.
     *
     * @param years how old they are, from {@link Ages}; -1 asks this class to keep its own old roll,
     *              which is what a caller that has no life table can still do
     */
    public static SkinVariation of(long seed, String culture, String style, boolean female, int year,
                                   String jewels, int years) {
        int[] weaves = CULTURE_WEAVE.getOrDefault(culture == null ? "global" : culture,
                CULTURE_WEAVE.get("global"));
        // the age: read off the person's years where we know them, so the grey hair agrees with the
        // record. This used to be a second, independent roll, and the two disagreed for one person in
        // three: a man recorded as an elder drawn smooth-faced, a young man drawn grey.
        int age;
        if (years >= 0) {
            age = Ages.paintAge(years);
        } else {
            int ageRoll = pick(seed, "age", 10);
            age = ageRoll < 2 ? 0 : ageRoll < 8 ? 1 : 2;
        }
        // A beard is a fashion, not a face: how many men of a place wear one at a year comes from
        // Traditions, and which one is the person's own. Where the century says few beards, a man is
        // clean or stubbled; where it says many, he carries a full one.
        int beard;
        double share = Traditions.beardShare(culture, year);
        // but a beard is a grown man's, whatever the century thinks of beards: a boy of sixteen was
        // being given a full one in four cases out of five. The century sets the fashion, the age
        // sets who is old enough to follow it.
        if (years >= 0) {
            if (years < Ages.GROWN_FROM) {
                share *= 0.22;
            } else if (years >= Ages.OLD_FROM) {
                share = Math.min(1.0, share * 1.15);
            }
        }
        boolean wears = pick(seed, "wearbeard", 100) < Math.round(share * 100);
        // the century decides whether a man wears a beard at all; the man decides which. His hair
        // style ("short_beard") only nudges him towards one - it must not decide for him, or every man
        // in every century is bearded and the fashion never moves.
        if (female) {
            beard = 0;
        } else if (!wears) {
            beard = chance(seed, "stubble", 4) ? 1 : 0;               // clean, or a day's stubble
        } else if (age == 2) {
            beard = chance(seed, "fork", 4) ? 5 : 4;                  // an elder's full beard, sometimes forked
        } else if (chance(seed, "mous", 3)) {
            beard = 2;                                                // a moustache on its own
        } else {
            beard = 3 + pick(seed, "beard", 2);                       // goatee or full
        }
        // the weave: chosen from the place's own list, then a strength of 1..2
        int pattern = weaves[pick(seed, "weave", weaves.length)];
        int strength = 1 + pick(seed, "strength", 2);
        // a woman veils where her family's jewellery list names a veil, and sometimes not
        boolean veil = female && jewels != null && jewels.contains("veil") && !chance(seed, "noveil", 5);
        return new SkinVariation(
                pick(seed, "eye", EYES.length),
                pick(seed, "brow", BROWS.length),
                pick(seed, "nose", NOSES.length),
                pick(seed, "mouth", MOUTHS.length),
                beard,
                age,
                pick(seed, "build", BUILDS.length),
                1 + pick(seed, "volume", 3),
                pattern,
                strength,
                pick(seed, "jewel", 4),
                veil,
                (int) (seed & 0x7FFFFFFF));
    }

    /** A short line naming the person's variation, for the probes and the logs. */
    public String describe() {
        return AGES[age] + " " + BUILDS[build] + " " + EYES[eye] + " " + BROWS[brow] + " " + NOSES[nose]
                + " " + MOUTHS[mouth] + " " + BEARDS[beard] + " " + PATTERNS[pattern];
    }

    /** The jewellery list of a culture, as booleans, since the pool keeps it as one string. */
    public boolean jewel(String name, String jewels) {
        return jewels != null && jewels.contains(name);
    }

    static List<String> none() {
        return List.of();
    }
}

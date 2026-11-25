package com.eternalengine.skin;

/**
 * One NPC, as the seven steps describe him: the stored facts, the classification, and what each later
 * step needs in order to paint him.
 *
 * <h3>The seven steps, and where each lives</h3>
 * <ol>
 *   <li><b>Store the information about the NPC</b> - this record. Seed, place, year, trade, sex, and the
 *       answers to the six steps below, kept together so the painter never asks the same question
 *       twice and never answers it differently.</li>
 *   <li><b>Classify by gender and by age</b> - {@link #female} and {@link Age} here, from the seed.</li>
 *   <li><b>Fill the skin with a human skin colour, by location</b> - {@code tone}, from the pool's tones
 *       for this place (the sub-pool's own where it has them).</li>
 *   <li><b>Fill the face</b> (eyes, brow, nose, mouth, facial hair, by location and year) -
 *       {@link SkinVariation}, whose beard follows {@link Traditions#beardShare} for the century.</li>
 *   <li><b>Fill the hair</b> (by location and year) - {@link #style} and {@link #hair}, from
 *       {@link Traditions#styles}.</li>
 *   <li><b>Fill the clothes</b> (fashion, by location and year) - {@link #garment} names what the register
 *       says he should be wearing; the painter draws the nearest shape it has.</li>
 *   <li><b>Fill the jewellery</b> (by location and year) - {@link #jewels}, from
 *       {@link Traditions#jewels}.</li>
 * </ol>
 *
 * <p>Nothing here is drawn. This is the step-1 record: everything later steps read.
 */
public record NpcProfile(long seed, String culture, String subpool, String role, boolean female,
                         int year, Age age, int years, int tone, int hair, String style, String garment,
                         String jewels, SkinVariation variation) {

    /** The classification of step 2. */
    public enum Age {
        CHILD, YOUTH, ADULT, ELDER;

        public String label() {
            return switch (this) {
                case CHILD -> "child";
                case YOUTH -> "youth";
                case ADULT -> "adult";
                case ELDER -> "elder";
            };
        }
    }

    /**
     * The person, from their number and their place.
     *
     * @param seed    the person's own number - the same person always comes out the same
     * @param culture the pool they draw from
     * @param role    their trade
     * @param female  step 2, the sex
     * @param year    the year they hold
     * @param pool    their culture's pool
     * @param tone    step 3, the tone their place gives them
     */
    public static NpcProfile of(long seed, String culture, String role, boolean female, int year,
                                SkinPoolDatabase.Pool pool, int tone) {
        return of(seed, culture, null, role, female, year, pool, tone, 0.0d, 0.0d);
    }

    public static NpcProfile of(long seed, String culture, String subpool, String role, boolean female,
                                int year, SkinPoolDatabase.Pool pool, int tone) {
        return of(seed, culture, subpool, role, female, year, pool, tone, 0.0d, 0.0d);
    }

    /**
     * The person, from their number, their place and their ground.
     *
     * @param lat where they stand - the age comes from the life table of this ground and this year
     */
    public static NpcProfile of(long seed, String culture, String subpool, String role, boolean female,
                                int year, SkinPoolDatabase.Pool pool, int tone, double lat,
                                double lon) {
        // ---- step 2: the age, in years, from the life table of this place and this year. One number,
        // and everything else about age is read off it, so the record and the face cannot disagree.
        // A child is classified but never spawned by the skin system: a child is a different size, and
        // the model's size belongs to the crowd, which is frozen. When the owner permits a child's
        // model, the classification is already here.
        int years = Ages.years(seed, year, lat, lon, role);
        Age age = Ages.bucket(years);

        // ---- step 5: the hair, by place and year
        String[] styles = Traditions.styles(culture, year, female);
        String[] poolStyles = female ? pool.hairStylesFemale() : pool.hairStylesMale();
        String style;
        if (styles.length == 0) {
            style = poolStyles.length == 0 ? "short" : poolStyles[(int) Math.floorMod(mix(seed), poolStyles.length)];
        } else {
            style = styles[(int) Math.floorMod(mix(seed ^ 0x2545F491L), styles.length)];
        }
        if (age == Age.ELDER && style.startsWith("long")) {
            style = "short";                       // an old man's hair is shorter than a boy's
        }
        int hair = pool.hairs()[(int) Math.floorMod(mix(seed ^ 0x9E3779B9L), pool.hairs().length)];

        // ---- step 4 and 7: the face's own choices, and the jewellery of the century
        String jewels = female ? Traditions.jewels(culture, year, pool.femaleJewels()) : "";
        SkinVariation v = SkinVariation.of(seed, culture, style, female, year, pool.femaleJewels(),
                years);

        // ---- step 6: the garment type, from the catalogue of the place and the year
        Garments.Garment g = year >= 1873 ? null : Garments.pick(culture, subpool, year, role, female, seed);
        String garment = g == null ? "modern wardrobe" : g.label();

        return new NpcProfile(seed, culture, subpool, role, female, year, age, years, tone, hair,
                style, garment, jewels, v);
    }

    /** The person's own number, from a UUID - the one place the seed is made, so every step agrees. */
    public static long seedOf(java.util.UUID uuid) {
        return uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 17);
    }

    /** Step 1, printed: every fact this NPC carries, one per line, for the logs and the probes. */
    public String report() {
        return "npc " + Long.toHexString(seed) + "\n"
                + "  place      " + culture + " | year " + year + " | trade " + role + "\n"
                + "  step 2     " + (female ? "woman" : "man") + ", " + age.label() + "\n"
                + "  step 3     tone #" + String.format("%06X", tone) + "\n"
                + "  step 4     " + SkinVariation.EYES[variation.eye()] + " eyes, "
                + SkinVariation.BROWS[variation.brow()] + " brow, "
                + SkinVariation.NOSES[variation.nose()] + " nose, "
                + SkinVariation.MOUTHS[variation.mouth()] + " mouth, "
                + SkinVariation.BEARDS[variation.beard()] + " beard\n"
                + "  step 5     " + style + " hair, #" + String.format("%06X", hair) + "\n"
                + "  step 6     " + garment + "\n"
                + "  step 7     " + (jewels.isEmpty() ? "none" : jewels) + " (" + SkinVariation.PATTERNS[variation.pattern()] + " cloth)";
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}

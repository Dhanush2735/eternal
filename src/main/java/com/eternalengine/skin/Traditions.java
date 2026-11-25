package com.eternalengine.skin;

/**
 * What each place and each century actually wore and carried - by the year, not only by the culture.
 *
 * <h3>Why this exists</h3>
 * The pool says what a <i>people</i> are like: their tones, their cloths, their jewellery list. It says
 * nothing about <i>when</i>. So a Deccan farmer of 1400 and a Deccan farmer of 1900 were given the same
 * hair style, the same beard and the same jewellery, and the only thing that changed between them was
 * the cut of the cloth.
 *
 * <p>This table answers three of the owner's seven steps by year: the <b>face</b> (step 4 - beards were
 * a fashion), the <b>hair</b> (step 5 - a turban wrap belongs to a period) and the <b>jewellery</b>
 * (step 7 - a nose ring is not of every century).
 *
 * <h3>How honest this is</h3>
 * The culture's own lists come from the shipped pool. The <i>dates</i> here are my choices, and they are
 * labelled {@code MINE} in the comments so the owner can overrule any of them. Nothing here is sourced
 * to a document; where the wardrobe register in {@code names/wardrobe.json.gz} has a date, this follows
 * it.
 */
public final class Traditions {

    private Traditions() {
    }

    /** A window of years: from, to (either may be open). */
    public record Window(int from, int to) {
        public boolean holds(int year) {
            return year >= from && year <= to;
        }
    }

    private static final Window ANY = new Window(-100000, 100000);

    /**
     * The hair styles a place knows at a year, in the pool's own vocabulary.
     *
     * <p>A style is offered only inside the years of its period; a person's seed picks among those. An
     * empty answer means the culture has no style there, and the painter falls back to the pool's list.
     */
    public static String[] styles(String culture, int year, boolean female) {
        if (female) {
            return new String[]{"long", "bun", "loose", "braid", "curly", "plait_over"};
        }
        return switch (culture == null ? "global" : culture) {
            case "deccan", "indic", "maurya" -> {
                if (year < 1200) {
                    yield new String[]{"short", "long", "short_beard", "bun"};
                } else if (year < 1900) {
                    yield new String[]{"turban_wrap", "short_beard", "long", "short"};   // MINE: the courts
                } else if (year < 1970) {
                    yield new String[]{"short", "short_beard"};                         // MINE: the crop
                }
                yield new String[]{"short", "short_beard"};
            }
            case "persianate" -> year < 1850
                    ? new String[]{"turban_wrap", "short_beard", "long"}
                    : new String[]{"short", "short_beard"};
            case "north_african" -> year < 1900
                    ? new String[]{"wrapped_head", "short_beard", "short"}
                    : new String[]{"short", "short_beard"};
            case "east_asian" -> year < 1000
                    ? new String[]{"bun", "long"}                      // MINE: the topknot of the Han
                    : year < 1644 ? new String[]{"bun", "long", "short_beard"}   // MINE: the Ming cap and beard
                    : year < 1912 ? new String[]{"long", "short_beard"}          // MINE: the Qing queue
                    : new String[]{"short"};
            case "african", "americas" -> year < 1900
                    ? new String[]{"curly", "long", "braid", "short_beard"}
                    : new String[]{"curly", "short"};
            default -> year < 1800
                    ? new String[]{"short", "curly", "long", "short_beard"}
                    : new String[]{"short", "short_beard"};
        };
    }

    /**
     * The headwear the era asks for, as a share of people. Zero means bare heads.
     * This does not overrule the pool's own {@code wrap}; the larger of the two is used.
     */
    public static double headwearShare(String culture, int year) {
        return switch (culture == null ? "global" : culture) {
            case "deccan", "indic", "maurya" -> year >= 1200 && year < 1950 ? 0.55 : 0.10;   // MINE
            case "persianate" -> year < 1935 ? 0.75 : 0.05;                                  // MINE
            case "north_african" -> year < 1950 ? 0.65 : 0.10;
            case "mediterranean", "rome" -> year < 500 ? 0.25 : year < 1800 ? 0.10 : 0.02;    // MINE: the pileus
            default -> 0.05;
        };
    }

    /**
     * How much of a century's men wear a beard. A beard is a fashion, not a face: it moves with the
     * century, where the eyes and the nose never do.
     */
    public static double beardShare(String culture, int year) {
        return switch (culture == null ? "global" : culture) {
            // a beard is a fashion with a shape, not one step: it rises with the courts, thins with the
            // razors of the 1900s and the army of the 1940s. All of these are MINE.
            case "deccan", "indic", "maurya" -> year < 1200 ? 0.70 : year < 1700 ? 0.78
                    : year < 1900 ? 0.62 : year < 1960 ? 0.50 : 0.25;
            case "persianate" -> year < 1500 ? 0.72 : year < 1900 ? 0.80 : year < 1940 ? 0.55 : 0.30;
            case "north_african" -> year < 1500 ? 0.70 : year < 1900 ? 0.66 : year < 1960 ? 0.52 : 0.28;
            case "mesopotamian" -> year < 500 ? 0.80 : year < 1900 ? 0.60 : 0.25;
            case "east_asian" -> year < 1000 ? 0.55 : year < 1912 ? 0.78 : year < 1960 ? 0.25 : 0.08;
            case "rome" -> year < 200 ? 0.35 : year < 500 ? 0.55 : year < 1900 ? 0.60 : 0.30;
            case "mediterranean" -> year < 500 ? 0.50 : year < 1600 ? 0.55 : year < 1900 ? 0.65 : 0.28;
            case "african", "americas" -> year < 1900 ? 0.45 : 0.20;
            default -> year < 1900 ? 0.45 : 0.20;
        };
    }

    /**
     * The jewellery a place's people carried at a year, as the pool spells it: a {@code +} list of
     * {@code bindi, nose, bangle, earring, veil, necklace}.
     */
    public static String jewels(String culture, int year, String fallback) {
        String base = fallback == null ? "earring" : fallback;
        StringBuilder sb = new StringBuilder(base);
        // an earring is worn in every century this game covers; a necklace of beads older still
        if (year < 1200 && cultureOf(culture, "deccan", "indic", "maurya", "mesopotamian")) {
            add(sb, "bangle");                 // MINE: the oldest ornament in the register's own region
        }
        // a nose ring is old jewellery and survives; it is not of every century, and not of every place
        if (year < 1950 && (cultureOf(culture, "deccan", "indic", "maurya", "north_african"))) {
            add(sb, "nose");
        }
        // bangles: everywhere until the twentieth century made them a festival thing
        if (year < 1970 || cultureOf(culture, "deccan", "indic", "maurya")) {
            add(sb, "bangle");
        }
        // a necklace of metal or beads: older than any of the rest
        add(sb, "necklace");
        if (cultureOf(culture, "persianate", "north_african") && year < 1960) {
            add(sb, "veil");
        }
        return sb.toString();
    }

    private static boolean cultureOf(String culture, String... ids) {
        for (String id : ids) {
            if (id.equals(culture)) {
                return true;
            }
        }
        return false;
    }

    private static void add(StringBuilder sb, String item) {
        if (!sb.toString().contains(item)) {
            sb.append('+').append(item);
        }
    }

    /**
     * Which named garment this place wore at this year, for the owner's step 6.
     *
     * <p>These are the names in {@code wardrobe.json.gz}. The painter does not yet have a shape for all
     * of them ({@code R3_SKIN_PLAN.md}, phase A); what it uses this for now is the <b>length and weight
     * of the cloth</b>, which it can draw, and a log line naming what the person is supposed to be
     * wearing so the gap stays visible instead of being silently rounded.
     */
    public static String garment(String culture, int year, String role) {
        boolean labour = role == null || role.equals("farmer") || role.equals("artisan")
                || role.equals("citizen") || role.equals("villager");
        return switch (culture == null ? "global" : culture) {
            case "deccan", "indic", "maurya" -> year < 1000 ? "dhoti_top" : year < 1900 ? "kurta+kaftan"
                    : year < 1950 ? "kurta+sherwani" : "modern";
            case "persianate" -> year < 1900 ? "kaftan+paijama" : "modern";
            case "mesopotamian" -> year < 500 ? "tunic" : year < 1900 ? "kaftan" : "modern";
            case "mediterranean" -> year < 500 ? "chiton" : year < 1900 ? "tunic" : "modern";
            case "rome" -> year < 500 ? (labour ? "tunic" : "tunic+toga") : year < 1800 ? "tunic" : "modern";
            case "north_african" -> year < 1900 ? "tunic+kaftan" : "modern";
            case "african" -> year < 1900 ? "wrapper_top" : "modern";
            case "east_asian" -> year < 1912 ? "hanfu" : "modern";
            case "americas" -> year < 1520 ? "huipil" : year < 1900 ? "poncho" : "modern";
            default -> year < 1900 ? "tunic" : "modern";
        };
    }
}

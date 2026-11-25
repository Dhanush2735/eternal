package com.eternalengine.skin;

import java.util.ArrayList;
import java.util.List;

/**
 * The clothing catalogue: every garment type by place and by year, in four categories.
 *
 * <h3>The four categories</h3>
 * <ul>
 *   <li>{@link Part#TOP} - a garment for the upper body only. It ends at the waist, so the lower
 *       cloth is a separate thing and the person needs one.</li>
 *   <li>{@link Part#LOWER} - a garment for the lower body only: a dhoti, pyjama trousers, a kilt, a
 *       skirt. It brings an upper garment with it, because nobody wears a lower cloth alone.</li>
 *   <li>{@link Part#DRESS} - one piece: one cloth from the shoulders to below the knee. A chiton, a
 *       hanfu, a kaftan, a sari, a boubou.</li>
 *   <li>{@link Part#OUTER} - worn over whatever is underneath: a toga, a cloak, a poncho, a shoulder
 *       cloth, an apron, a shawl.</li>
 * </ul>
 *
 * <h3>What a garment is</h3>
 * Six decisions, not a picture: how the collar closes, how long the sleeve is, where the cloth
 * stops, what holds it in, whether it brings a lower cloth of its own, and whether it hangs loose or
 * is cut to the body. Two garments with the same six numbers are the same garment whatever they are
 * called, and that is deliberate: the register names many clothes whose only real difference is the
 * collar and the cloth.
 *
 * <h3>Where the dates come from</h3>
 * Where the wardrobe register in the game dates a garment, this follows it ({@code tunic} -3000,
 * {@code toga} -600, {@code chiton} -800, {@code hanfu} -1000, {@code doublet} 1340, {@code sherwani}
 * 1600). Every other date, and every line of the wardrobe table, is my reading of the record, for the
 * owner to overrule.
 */
public final class Garments {

    private Garments() {
    }

    /** How a garment closes at the neck. */
    public enum Collar { NONE, SQUARE, V_NECK, CROSSED, STAND, OPEN_FRONT, WRAP, BOAT, HIGH, SHAWL }

    /** What the garment is: the four categories the owner asked for. */
    public enum Part { TOP, DRESS, OUTER, LOWER }

    /**
     * One garment type.
     *
     * @param id     the name the register uses where there is one
     * @param part   top, lower, one piece, or outer
     * @param collar how it closes
     * @param sleeve rows of sleeve, 0 bare-armed to 11 at the wrist
     * @param hem    where the cloth stops on the torso, 12 = at the waist
     * @param belt   how it is held in: 0 none, 1 a tie, 2 a sash, 3 a girdle
     * @param drape  rows of lower cloth it brings with it, -1 for none
     * @param loose  true if it hangs over the body (drawn proud, on the second layer)
     * @param from   first year it is worn
     * @param to     last year, 9999 for still worn
     */
    public record Garment(String id, Part part, Collar collar, int sleeve, int hem, int belt,
                          int drape, boolean loose, int from, int to) {

        /** What a person would call it. */
        public String label() {
            return id.replace('_', ' ');
        }

        public boolean holds(int year) {
            return year >= from && year <= to;
        }

        /** The window this place wears it in, which can narrow the garment's own dates. */
        public boolean holds(String culture, int year) {
            int[] w = WINDOWS.get(place(culture) + ":" + id);
            if (w != null) {
                return year >= w[0] && year <= w[1];
            }
            return holds(year);
        }
    }

    // =====================================================================================
    // The shapes. One line each: id, part, collar, sleeve, hem, belt, drape, loose, from, to.
    // =====================================================================================

    // ---- India and the Deccan -----------------------------------------------------------
    static final Garment TUNIC       = g("tunic",         Part.TOP,   Collar.SQUARE,     6, 12, 0, -1, false, -3000, 1400);
    static final Garment TUNIC_LONG  = g("tunic_long",    Part.TOP,   Collar.SQUARE,     6, 12, 1, 10, false, -3000,  900);
    static final Garment DHOTI       = g("dhoti",         Part.LOWER, Collar.NONE,       0,  0, 3, 11, false, -3000, 9999);
    static final Garment DHOTI_BARE  = g("dhoti_top",     Part.LOWER, Collar.NONE,       0,  0, 3, 11, false, -3000,  900);
    static final Garment KACCHA      = g("kaccha",        Part.LOWER, Collar.NONE,       0,  0, 3, 10, false, -1000, 1200);
    static final Garment KURTA       = g("kurta",         Part.TOP,   Collar.STAND,      8, 12, 1,  6, false,  1200, 9999);
    static final Garment KURTI       = g("kurti",         Part.TOP,   Collar.STAND,      6, 11, 1,  6, false,  1500, 9999);
    static final Garment KAMEEZ      = g("kameez",        Part.DRESS, Collar.V_NECK,     9, 12, 1, 12, false,  1300, 9999);
    static final Garment KANCHUKI    = g("kanchuki",      Part.TOP,   Collar.BOAT,       3,  9, 1, -1, false,  -500, 9999);
    static final Garment CHOLI       = g("choli",         Part.TOP,   Collar.V_NECK,     4, 10, 1, -1, false,  1200, 9999);
    static final Garment SAREE       = g("sari",          Part.DRESS, Collar.WRAP,       4, 12, 2, 12, false, -1000, 9999);
    static final Garment GHA_GRA      = g("ghagra",       Part.DRESS, Collar.SQUARE,     6, 12, 2, 12, false,  1500, 9999);
    static final Garment PAIJAMA     = g("paijama",       Part.LOWER, Collar.NONE,       0,  0, 1, 12, false,  1300, 9999);
    static final Garment CHURIDAR    = g("churidar",      Part.LOWER, Collar.NONE,       0,  0, 1, 12, false,  1600, 9999);
    static final Garment SALWAR      = g("salwar",        Part.LOWER, Collar.NONE,       0,  0, 1, 12, false,  1700, 9999);
    static final Garment LUNGI       = g("lungi",         Part.LOWER, Collar.NONE,       0,  0, 2, 11, false,  1200, 9999);
    static final Garment SARONG      = g("sarong",        Part.LOWER, Collar.NONE,       0,  0, 2, 11, false, -1000, 1400);
    static final Garment SHERWANI    = g("sherwani",      Part.DRESS, Collar.STAND,     11, 12, 2, 11, false,  1600, 9999);
    static final Garment ACHKAN      = g("achkan",        Part.DRESS, Collar.STAND,     11, 12, 1, 12, false,  1700, 9999);
    static final Garment JAMA        = g("jama",          Part.DRESS, Collar.CROSSED,   10, 12, 2, 11, false,  1550, 1850);
    static final Garment ANGARKHA    = g("angarkha",      Part.TOP,   Collar.WRAP,       9, 12, 2,  8, false,  1600, 9999);
    static final Garment CHAPKAN     = g("chapkan",       Part.DRESS, Collar.STAND,     11, 12, 2, 12, false,  1650, 1950);
    static final Garment KAFTAN      = g("kaftan",        Part.DRESS, Collar.OPEN_FRONT,12, 12, 2, 12, true,    500, 1900);
    static final Garment SADRI       = g("sadri",         Part.TOP,   Collar.OPEN_FRONT, 6, 11, 0, -1, false,  1800, 9999);
    static final Garment BANDI       = g("bandi",         Part.TOP,   Collar.OPEN_FRONT, 5, 11, 0, -1, false,  1850, 9999);
    static final Garment PHIRAN      = g("phiran",        Part.DRESS, Collar.OPEN_FRONT,12, 12, 2, 12, true,   1500, 1900);
    static final Garment UTARIYA     = g("uttariya",      Part.OUTER, Collar.SHAWL,      0, 12, 0, -1, true,  -1000, 1400);
    static final Garment ANGAVASTRAM = g("angavastram",   Part.OUTER, Collar.SHAWL,      0, 12, 0, -1, true,   -500, 9999);
    static final Garment DUPATTA     = g("dupatta",       Part.OUTER, Collar.SHAWL,      0, 12, 0, -1, true,   1300, 9999);
    static final Garment CHADOR      = g("chador",        Part.OUTER, Collar.NONE,      12, 12, 0, -1, true,   1200, 1950);
    static final Garment SHAWL_KA    = g("shawl_kashmiri",Part.OUTER, Collar.SHAWL,     12, 12, 0, -1, true,   1400, 9999);
    static final Garment CHOGA       = g("choga",         Part.OUTER, Collar.OPEN_FRONT,12, 12, 2, -1, true,   1600, 1900);
    static final Garment VESTMENT    = g("vestment",      Part.DRESS, Collar.STAND,     11, 12, 2, 12, true,    300, 9999);

    // ---- Iran and Central Asia -----------------------------------------------------------
    static final Garment PIRAHAN     = g("pirahan",       Part.TOP,   Collar.SQUARE,     9, 12, 1, -1, false, -1000, 1400);
    static final Garment QAMIS       = g("qamis",         Part.TOP,   Collar.STAND,      8, 12, 1,  6, false,   700, 1500);
    static final Garment TUNBAN      = g("tunban",        Part.LOWER, Collar.NONE,       0,  0, 1, 12, false,  -500, 1400);
    static final Garment SHALWAR     = g("shalwar",       Part.LOWER, Collar.NONE,       0,  0, 1, 12, false,   900, 9999);
    static final Garment QABA        = g("qaba",          Part.DRESS, Collar.OPEN_FRONT,11, 12, 2, 12, false,   900, 1900);
    static final Garment JUBBA       = g("jubba",         Part.DRESS, Collar.CROSSED,   11, 12, 2, 12, false,   800, 1900);
    static final Garment ROBE        = g("robe",          Part.DRESS, Collar.CROSSED,   11, 12, 2, 12, true,    500, 9999);
    static final Garment ARKHALIG    = g("arkhalig",      Part.TOP,   Collar.OPEN_FRONT, 9, 12, 1,  6, false,  1700, 1950);
    static final Garment CHAPAN      = g("chapan",        Part.OUTER, Collar.OPEN_FRONT,12, 12, 2, -1, true,   1200, 9999);
    static final Garment ABA         = g("aba",           Part.OUTER, Collar.NONE,      12, 12, 0, -1, true,   1000, 9999);
    static final Garment PUSTIN      = g("pustin",        Part.OUTER, Collar.SHAWL,     12, 12, 0, -1, true,   1500, 1950);
    static final Garment CLOAK       = g("cloak",         Part.OUTER, Collar.NONE,      12, 12, 0, -1, true,  -3000, 1700);

    // ---- Mesopotamia and the Levant ------------------------------------------------------
    static final Garment KA_UNAKES   = g("kaunakes",      Part.DRESS, Collar.NONE,       6, 12, 1, 12, false, -3000, -1200);
    static final Garment SHENDYT     = g("shendyt",       Part.LOWER, Collar.NONE,       0,  0, 0,  8, false, -3000,  -800);
    static final Garment WRAPPER     = g("wrapper_top",   Part.TOP,   Collar.WRAP,       5, 12, 2, 10, false, -3000, 2026);
    static final Garment KANDYS      = g("kandys",        Part.OUTER, Collar.WRAP,      12, 12, 0, -1, true,   -800,   400);
    static final Garment SHAWL_MESO  = g("shawl_mesopotamian", Part.OUTER, Collar.SHAWL,  0, 12, 0, -1, true,  -2000,   900);

    // ---- the Mediterranean and Europe ----------------------------------------------------
    static final Garment CHITON      = g("chiton",        Part.DRESS, Collar.BOAT,       4, 12, 2, 11, false,  -800,   600);
    static final Garment CHITONISKOS = g("chitoniskos",   Part.DRESS, Collar.BOAT,       4, 10, 1,  8, false,  -800,   300);
    static final Garment PEPLOS      = g("peplos",        Part.DRESS, Collar.CROSSED,    5, 12, 2, 12, false,  -700,   200);
    static final Garment HIMATION    = g("himation",      Part.OUTER, Collar.WRAP,      12, 12, 0, -1, true,   -800,   400);
    static final Garment CHLAMYS     = g("chlamys",       Part.OUTER, Collar.NONE,      12, 12, 0, -1, true,   -600,   400);
    static final Garment TUNICA      = g("tunica",        Part.TOP,   Collar.SQUARE,     6, 12, 0, -1, false, -3000,  1400);
    static final Garment DALMATICA   = g("dalmatica",     Part.DRESS, Collar.BOAT,      11, 12, 2, 12, false,   200,  1200);
    static final Garment STOLA       = g("stola",         Part.DRESS, Collar.BOAT,       8, 12, 2, 12, false,  -200,   400);
    static final Garment PALLA       = g("palla",         Part.OUTER, Collar.SHAWL,     12, 12, 0, -1, true,   -200,   400);
    static final Garment PALLIUM     = g("pallium",       Part.OUTER, Collar.SHAWL,     12, 12, 0, -1, true,   -300,   400);
    static final Garment CHEMISE     = g("chemise",       Part.DRESS, Collar.BOAT,       8, 12, 0, 12, false,   800,  1900);
    static final Garment CAMICIA     = g("camicia",       Part.TOP,   Collar.BOAT,       7, 12, 0, -1, false,  1200,  1700);
    static final Garment DOUBLE_T    = g("doublet",       Part.TOP,   Collar.HIGH,       9, 10, 2,  6, false,  1340,  1700);
    static final Garment JERKIN      = g("jerkin",        Part.TOP,   Collar.OPEN_FRONT, 8, 11, 1, -1, false,  1400,  1700);
    static final Garment COTEHARDIE  = g("cotehardie",    Part.DRESS, Collar.HIGH,       9, 12, 2, 10, false,  1340,  1550);
    static final Garment HOUPPELANDE = g("houppelande",   Part.DRESS, Collar.HIGH,      12, 12, 2, 12, true,   1360,  1500);
    static final Garment KIRTLE      = g("kirtle",        Part.DRESS, Collar.SQUARE,     8, 12, 2, 12, false,  1200,  1600);
    static final Garment BLIAUT      = g("bliaut",        Part.DRESS, Collar.BOAT,      11, 12, 2, 12, true,   1100,  1300);
    static final Garment SURCOTE     = g("surcote",       Part.OUTER, Collar.NONE,      12, 12, 0, -1, true,   1200,  1450);
    static final Garment MANTLE      = g("mantle",        Part.OUTER, Collar.NONE,      12, 12, 0, -1, true,    900,  1600);
    static final Garment CAPE_HOOD   = g("cape_hooded",   Part.OUTER, Collar.HIGH,      12, 12, 0, -1, true,   1300,  1900);
    static final Garment APRON       = g("apron",         Part.OUTER, Collar.NONE,       0, 12, 0, -1, true,    900,  1900);
    static final Garment SHIRT       = g("shirt",         Part.TOP,   Collar.STAND,      9, 12, 0, -1, false,  1300, 9999);
    static final Garment BODICE      = g("bodice",        Part.TOP,   Collar.V_NECK,     7, 10, 2, -1, false,  1500,  1850);
    static final Garment GOWN        = g("gown",          Part.DRESS, Collar.SQUARE,     9, 12, 2, 12, false,  1200,  1900);
    static final Garment PETTICOAT   = g("petticoat",     Part.DRESS, Collar.SQUARE,     0, 12, 1, 12, false,  1500,  1850);
    static final Garment HOSE        = g("hose",          Part.LOWER, Collar.NONE,       0,  0, 0, 12, false,  1340,  1700);
    static final Garment BRAIES      = g("braies",        Part.LOWER, Collar.NONE,       0,  0, 1, 10, false,   900,  1550);
    static final Garment BREECHES    = g("breeches",      Part.LOWER, Collar.NONE,       0,  0, 1, 10, false,  1500,  1800);
    static final Garment TROUSERS_L  = g("trousers",      Part.LOWER, Collar.NONE,       0,  0, 1, 12, false,  1400, 9999);

    // ---- Rome itself ---------------------------------------------------------------------
    static final Garment TOGA        = g("toga",          Part.OUTER, Collar.WRAP,      12, 12, 0, -1, true,   -600,   400);
    static final Garment TOGA_PICTA  = g("toga_picta",    Part.OUTER, Collar.WRAP,      12, 12, 0, -1, true,   -500,   300);

    // ---- Egypt and the Maghreb ------------------------------------------------------------
    static final Garment GANDOURA    = g("gandoura",      Part.DRESS, Collar.SQUARE,     9, 12, 1, 12, false,   800, 9999);
    static final Garment DJELLABA    = g("djellaba",      Part.DRESS, Collar.OPEN_FRONT,12, 12, 1, 12, true,   1100, 9999);
    static final Garment BURNOUS     = g("burnous",       Part.OUTER, Collar.NONE,      12, 12, 0, -1, true,    900, 9999);
    static final Garment HAIK        = g("haik",          Part.OUTER, Collar.SHAWL,     12, 12, 0, -1, true,   1000, 9999);
    static final Garment SIRWAL      = g("sirwal",        Part.LOWER, Collar.NONE,       0,  0, 1, 11, false,  1000, 9999);

    // ---- West Africa and the Sahel --------------------------------------------------------
    static final Garment BOUBOU      = g("boubou",        Part.DRESS, Collar.V_NECK,    10, 12, 2, 12, true,   -500, 9999);
    static final Garment AGBADA      = g("agbada",        Part.DRESS, Collar.BOAT,      11, 12, 2, 12, true,   1200, 9999);
    static final Garment DASHIKI     = g("dashiki",       Part.TOP,   Collar.V_NECK,     7, 12, 0, -1, false,  1500, 9999);
    static final Garment KENTE       = g("kente",         Part.OUTER, Collar.SHAWL,      0, 12, 0, -1, true,   1400, 9999);
    static final Garment WRAP_SKIRT  = g("wrapper_skirt", Part.LOWER, Collar.NONE,       0,  0, 2, 11, false, -2000, 9999);

    // ---- China, Korea, Vietnam -------------------------------------------------------------
    static final Garment HANFU       = g("hanfu",         Part.DRESS, Collar.CROSSED,   12, 12, 2, 12, false, -1000, 1912);
    static final Garment HANFU_WIDE  = g("hanfu_wide",    Part.DRESS, Collar.CROSSED,   11, 12, 2, 12, true,  -1000, 1644);
    static final Garment SHENYI      = g("shenyi",        Part.DRESS, Collar.CROSSED,   12, 12, 2, 12, true,   -300, 1800);
    static final Garment ROUND_COLL  = g("round_collar",  Part.DRESS, Collar.STAND,     10, 12, 1, 12, false,   600, 1900);
    static final Garment RU          = g("ru",            Part.TOP,   Collar.CROSSED,    8, 12, 1,  6, false,  -500, 1900);
    static final Garment AO          = g("ao",            Part.TOP,   Collar.CROSSED,    9, 12, 1,  6, false,   700, 9999);
    static final Garment BEIZI       = g("beizi",         Part.OUTER, Collar.OPEN_FRONT,11, 12, 2, -1, true,    960, 1900);
    static final Garment BANBI       = g("banbi",         Part.TOP,   Collar.CROSSED,    6, 10, 1, -1, false,   600, 1300);
    static final Garment DAXIU       = g("daxiushan",     Part.OUTER, Collar.OPEN_FRONT,11, 12, 2, -1, true,    960, 1600);
    static final Garment KU          = g("ku",            Part.LOWER, Collar.NONE,       0,  0, 1, 12, false, -1000, 9999);
    static final Garment QIPAO       = g("qipao",         Part.DRESS, Collar.HIGH,       8, 12, 2, 12, false,  1920, 9999);
    static final Garment AO_DAI      = g("ao_dai",        Part.DRESS, Collar.HIGH,      11, 12, 1, 12, false,  1800, 9999);

    // ---- Japan -----------------------------------------------------------------------------
    static final Garment KIMONO      = g("kimono",        Part.DRESS, Collar.CROSSED,   11, 12, 2, 12, true,    794, 9999);
    static final Garment KOSODE      = g("kosode",        Part.DRESS, Collar.CROSSED,   10, 12, 2, 12, false,  1300, 1900);
    static final Garment YUKATA      = g("yukata",        Part.DRESS, Collar.CROSSED,    8, 12, 1, 12, true,   1600, 9999);
    static final Garment UCHIKAKE    = g("uchikake",      Part.OUTER, Collar.OPEN_FRONT,12, 12, 2, -1, true,   1500, 9999);
    static final Garment HAORI       = g("haori",         Part.OUTER, Collar.OPEN_FRONT,11, 12, 0, -1, true,   1600, 9999);
    static final Garment HAKAMA      = g("hakama",        Part.LOWER, Collar.NONE,       0,  0, 1, 12, false,   700, 9999);

    // ---- the Americas -----------------------------------------------------------------------
    static final Garment HUIPIL      = g("huipil",        Part.DRESS, Collar.BOAT,       4, 12, 0, 12, false, -1000, 1900);
    static final Garment CUEITL      = g("cueitl",        Part.LOWER, Collar.NONE,       0,  0, 2, 11, false, -1000, 1600);
    static final Garment QUECHQUE    = g("quechquemitl",  Part.OUTER, Collar.NONE,       0, 12, 0, -1, true,   -500, 1600);
    static final Garment PONCHO      = g("poncho",        Part.OUTER, Collar.NONE,       0, 12, 0, -1, true,   -500, 1900);
    static final Garment MANTLE_MESO = g("mantle_mesoamerican", Part.OUTER, Collar.NONE, 6, 12, 0, -1, true,  -1000, 1700);
    static final Garment TILMATLI    = g("tilmatli",      Part.OUTER, Collar.HIGH,       6, 12, 0, -1, true,   -800, 1520);
    static final Garment ANACU       = g("anacu",          Part.DRESS, Collar.BOAT,       4, 12, 1, 12, false,  -800, 1533);


    // ---- additions: more of every category, so no place runs dry in any century -----------------
    static final Garment PANCHA      = g("pancha",        Part.LOWER, Collar.NONE,       0,  0, 3, 10, false, -1000, 1900);
    static final Garment LEHENGA     = g("lehenga",       Part.DRESS, Collar.SQUARE,     6, 12, 2, 12, false,  1600, 9999);
    static final Garment ANARKALI    = g("anarkali",      Part.DRESS, Collar.V_NECK,     9, 12, 1, 12, false,  1700, 9999);
    static final Garment NEHRU       = g("nehru_jacket",  Part.TOP,   Collar.STAND,      6, 11, 0, -1, false,  1900, 9999);
    static final Garment THAWB       = g("thawb",         Part.DRESS, Collar.SQUARE,     9, 12, 1, 12, false,   700, 9999);
    static final Garment ABAYA       = g("abaya",         Part.OUTER, Collar.NONE,      12, 12, 0, -1, true,    700, 9999);
    static final Garment HIJAB       = g("hijab",         Part.OUTER, Collar.SHAWL,      0, 12, 0, -1, true,    700, 9999);
    static final Garment WAISTCOAT   = g("waistcoat",     Part.TOP,   Collar.OPEN_FRONT, 6, 11, 0, -1, false,  1700, 1900);
    static final Garment COAT        = g("coat",          Part.OUTER, Collar.OPEN_FRONT,12, 12, 0, -1, true,   1650, 1900);
    static final Garment GREATCOAT   = g("greatcoat",     Part.OUTER, Collar.HIGH,      12, 12, 0, -1, true,   1750, 1900);
    static final Garment SMOCK       = g("smock",         Part.DRESS, Collar.BOAT,       8, 12, 0, 12, false,  1000, 1700);
    static final Garment GELE        = g("gele",          Part.OUTER, Collar.SHAWL,      0, 12, 0, -1, true,   1700, 9999);
    static final Garment LLICLLA     = g("lliclla",       Part.OUTER, Collar.SHAWL,     12, 12, 0, -1, true,   -800, 1600);
    static final Garment UNCU        = g("uncu",          Part.TOP,   Collar.SQUARE,     6, 12, 0, -1, false,  -800, 1533);
    static final Garment MO          = g("mo",            Part.LOWER, Collar.NONE,       0,  0, 2, 10, false,  -400,  900);
    static final Garment JUNIHITOE   = g("junihitoe",     Part.DRESS, Collar.CROSSED,   12, 12, 2, 12, true,    794, 1600);
    static final Garment DUDOU       = g("dudou",         Part.TOP,   Collar.BOAT,       2,  9, 0, -1, false,  -500, 1900);
    static final Garment JEOGORI     = g("jeogori",       Part.TOP,   Collar.CROSSED,    8, 11, 1, -1, false,  1400, 9999);
    static final Garment CHIMA       = g("chima",         Part.DRESS, Collar.SQUARE,     0, 12, 2, 12, false,  1400, 9999);
    static final Garment AO_TU_THAN  = g("ao_tu_than",    Part.DRESS, Collar.HIGH,      11, 12, 1, 12, false,  1700, 9999);
    static final Garment SUBLIGACULUM = g("subligaculum", Part.LOWER, Collar.NONE,       0,  0, 1,  7, false,  -600,  400);
    static final Garment UCHIGI      = g("uchigi",        Part.DRESS, Collar.CROSSED,    6, 12, 1, 12, false,  -500, 1000);
    static final Garment SUIKAN      = g("suikan",        Part.OUTER, Collar.OPEN_FRONT,12, 12, 0, -1, true,   1000, 1600);
    static final Garment HANTEN      = g("hanten",        Part.OUTER, Collar.OPEN_FRONT, 9, 12, 0, -1, true,   1700, 2026);
    static final Garment KARIGINU    = g("kariginu",      Part.OUTER, Collar.OPEN_FRONT,12, 12, 0, -1, true,    900, 1600);
    static final Garment SHITAGASANE = g("shitagasane",   Part.TOP,   Collar.CROSSED,    9, 12, 1, -1, false,   900, 1400);
    static final Garment KANTOI      = g("kantoi",        Part.DRESS, Collar.NONE,       6, 12, 1, 12, false,  -300,  700);
    static final Garment XICOLI      = g("xicoli",        Part.TOP,   Collar.NONE,       0, 11, 0, -1, false,  -1000, 1520);
    static final Garment KALASIRIS   = g("kalasiris",     Part.DRESS, Collar.BOAT,       4, 12, 1, 12, false, -3000,  -30);

    /** Every garment type, once, so an id in the table below can find its shape. */
    private static final List<Garment> ALL = List.of(
            TUNIC, TUNIC_LONG, DHOTI, DHOTI_BARE, KACCHA, KURTA, KURTI, KAMEEZ, KANCHUKI, CHOLI, SAREE,
            GHA_GRA, PAIJAMA, CHURIDAR, SALWAR, LUNGI, SARONG, SHERWANI, ACHKAN, JAMA, ANGARKHA,
            CHAPKAN, KAFTAN, SADRI, BANDI, PHIRAN, UTARIYA, ANGAVASTRAM, DUPATTA, CHADOR, SHAWL_KA,
            CHOGA, VESTMENT,
            PIRAHAN, QAMIS, TUNBAN, SHALWAR, QABA, JUBBA, ROBE, ARKHALIG, CHAPAN, ABA, PUSTIN, CLOAK,
            KA_UNAKES, SHENDYT, WRAPPER, KANDYS, SHAWL_MESO,
            CHITON, CHITONISKOS, PEPLOS, HIMATION, CHLAMYS, TUNICA, DALMATICA, STOLA, PALLA, PALLIUM,
            CHEMISE, CAMICIA, DOUBLE_T, JERKIN, COTEHARDIE, HOUPPELANDE, KIRTLE, BLIAUT, SURCOTE,
            MANTLE, CAPE_HOOD, APRON, SHIRT, BODICE, GOWN, PETTICOAT, HOSE, BRAIES, BREECHES, TROUSERS_L,
            TOGA, TOGA_PICTA,
            GANDOURA, DJELLABA, BURNOUS, HAIK, SIRWAL,
            BOUBOU, AGBADA, DASHIKI, KENTE, WRAP_SKIRT,
            HANFU, HANFU_WIDE, SHENYI, ROUND_COLL, RU, AO, BEIZI, BANBI, DAXIU, KU, QIPAO, AO_DAI,
            KIMONO, KOSODE, YUKATA, UCHIKAKE, HAORI, HAKAMA,
            HUIPIL, CUEITL, QUECHQUE, PONCHO, MANTLE_MESO, TILMATLI, ANACU,
            PANCHA, LEHENGA, ANARKALI, NEHRU, THAWB, ABAYA, HIJAB, WAISTCOAT, COAT, GREATCOAT, SMOCK,
            GELE, LLICLLA, UNCU, MO, JUNIHITOE, DUDOU, JEOGORI, CHIMA, AO_TU_THAN,
            SUBLIGACULUM, UCHIGI, KALASIRIS, SUIKAN, HANTEN, XICOLI, KARIGINU, SHITAGASANE,
            KANTOI);

    // =====================================================================================
    // The wardrobe: one line per place. Each garment carries the years that place wore it.
    // This table is the only authority - "what" and "when" are read from the same line.
    // =====================================================================================
    private static final String[][] WARDROBE = {
        {"deccan",
                // tops
                "tunic -2000 1200", "tunic_long -2000 900", "kanchuki -500 2026", "choli 1200 2026",
                "kurta 1200 2026", "kurti 1500 2026", "angarkha 1600 2026", "sadri 1800 2026",
                "bandi 1850 2026", "nehru_jacket 1900 2026",
                // bottoms
                "dhoti_top -3000 900", "kaccha -1000 1200", "pancha -1000 1900", "dhoti -1000 2026",
                "sarong 0 1400", "lungi 1200 2026", "paijama 1300 2026", "churidar 1600 2026",
                "salwar 1700 2026", "trousers 1800 2026",
                // one piece
                "sari -1000 2026", "kameez 1300 2026", "kaftan 1300 1900", "ghagra 1500 2026",
                "jama 1550 1850", "sherwani 1600 2026", "lehenga 1600 2026", "chapkan 1650 1950",
                "achkan 1700 2026", "anarkali 1700 2026", "phiran 1500 1900", "vestment 300 2026",
                // outer
                "uttariya -1000 1400", "angavastram -500 2026", "chador 1200 1950",
                "dupatta 1300 2026", "shawl_kashmiri 1400 2026", "choga 1600 1900", "shirt 1750 2026"},
        {"persianate",
                "tunic -3000 800", "pirahan -1000 1400", "qamis 700 1500", "arkhalig 1700 1950",
                "kurta 1700 2026", "shirt 1750 2026", "dudou 1400 1900",
                "tunban -500 1400", "shalwar 900 2026", "paijama 500 2026", "trousers 1800 2026",
                "thawb 700 2026", "gandoura 800 2026", "jubba 800 1900", "qaba 900 1900",
                "kaftan 500 2026", "robe -500 2026", "gown 1200 1900", "vestment 300 2026",
                "kalasiris -3000 -30",
                "chapan 1200 2026", "aba 1000 2026", "abaya 700 2026", "hijab 700 2026",
                "pustin 1500 1950", "cloak -3000 1700"},
        {"mesopotamian",
                "tunic -3000 1900", "tunic_long -3000 700", "wrapper_top -3000 1500",
                "shalwar 900 2026", "sirwal 900 2026",
                "kaunakes -3000 -500", "shendyt -3000 -800", "sarong -3000 1200",
                "shawl_mesopotamian -2000 900", "kandys -800 400", "angavastram -2000 900",
                "cloak -3000 1700", "thawb 700 2026", "vestment 300 2026",
                "trousers 1800 2026", "shirt 1850 2026", "coat 1700 1900", "cape_hooded 1300 1900"},
        {"mediterranean",
                // tops
                "tunica -3000 1400", "tunic -3000 1400", "camicia 1200 1700", "doublet 1340 1700",
                "jerkin 1400 1700", "shirt 1300 2026", "bodice 1500 1850", "waistcoat 1700 1900",
                "smock 1000 1700",
                // bottoms
                "braies 900 1550", "hose 1340 1700", "breeches 1500 1800", "trousers 1400 2026",
                // one piece
                "chiton -800 600", "chitoniskos -800 300", "peplos -700 200", "dalmatica 200 1200",
                "subligaculum -600 400",
                "stola -200 400", "chemise 800 1900", "bliaut 1100 1300", "kirtle 1200 1600",
                "cotehardie 1340 1550", "houppelande 1360 1500", "gown 1200 1900",
                "petticoat 1500 1850", "vestment 300 2026",
                // outer
                "himation -800 400", "chlamys -600 400", "pallium -300 400", "palla -200 400",
                "mantle 900 1600", "surcote 1200 1450", "cape_hooded 1300 1900", "apron 900 1900",
                "coat 1650 1900", "greatcoat 1750 1900", "cloak -3000 1700"},
        {"rome",
                "tunica -500 1400", "tunic -500 500", "camicia 1200 1700", "doublet 1340 1700",
                "jerkin 1400 1700", "shirt 1300 2026", "bodice 1500 1850", "waistcoat 1700 1900",
                "braies 900 1550", "hose 1340 1700", "breeches 1500 1800", "trousers 1400 2026",
                "chiton -800 400", "peplos -700 200", "dalmatica 200 1200", "stola -200 400",
                "subligaculum -600 400", "kalasiris -3000 -30",
                "chemise 800 1900", "kirtle 1200 1600", "cotehardie 1340 1550",
                "houppelande 1360 1500", "gown 1200 1900", "petticoat 1500 1850",
                "vestment 300 2026", "toga -600 400", "toga_picta -500 300", "palla -200 400",
                "pallium -300 400", "mantle 900 1600", "cape_hooded 1300 1900", "apron 900 1900",
                "coat 1650 1900", "greatcoat 1750 1900", "cloak -600 1700"},
        {"north_african",
                "tunic -3000 1500", "tunic_long -3000 1400", "wrapper_top -3000 1200",
                "qamis 700 1500", "shirt 1700 2026", "dudou 1400 1900",
                "sirwal 1000 2026", "paijama 1300 2026", "trousers 1800 2026", "shalwar 900 2026",
                "shendyt -3000 -30",
                "thawb 700 2026", "gandoura 800 2026", "jubba 900 1900", "kaftan 1000 2026",
                "kalasiris -3000 -30",
                "robe 900 2026", "djellaba 1100 2026", "gown 1200 1900", "vestment 300 2026",
                "burnous 900 2026", "haik 1000 2026", "aba 1000 1900", "abaya 700 2026",
                "hijab 700 2026", "cloak -3000 1700"},
        {"african",
                "tunic -3000 900", "wrapper_top -3000 2026", "dashiki 1500 2026", "shirt 1750 2026",
                "wrapper_skirt -2000 2026", "sarong -1000 2026", "trousers 1400 2026",
                "boubou -500 2026", "agbada 1200 2026", "robe 900 2026", "gandoura 1200 2026",
                "kaftan 1300 2026", "vestment 900 2026",
                "kente -500 2026", "gele 1700 2026", "hijab 1300 2026", "burnous 1400 2026",
                "haik 700 2026"},
        {"east_asian",
                "tunic -3000 500", "wrapper_top -2000 700", "dudou -500 1900", "ru -500 1900",
                "banbi 600 1300", "ao 700 2026", "jeogori 1400 2026", "shirt 1850 2026",
                "ku -1000 2026", "mo -500 900", "hakama 700 2026", "trousers 300 2026",
                "uchigi -500 1000",
                "hanfu -1000 1912", "hanfu_wide -1000 1644", "shenyi -300 1800", "round_collar 600 1900",
                "kimono 794 2026", "junihitoe 794 1600", "kosode 1300 1900", "yukata 1600 2026",
                "chima 1400 2026", "ao_tu_than 1700 2026", "ao_dai 1800 2026", "qipao 1920 2026",
                "robe 500 2026", "vestment 300 2026",
                "beizi -500 1900", "daxiushan 960 1600", "uchikake 1500 2026", "haori 1600 2026",
                "suikan 1000 1600", "hanten 1700 2026", "kariginu 900 1600",
                "shitagasane 900 1400", "kantoi -300 700"},
        {"americas",
                "tunic -1000 1520", "uncu -800 1533", "xicoli -1000 1520", "wrapper_top -3000 2026",
                "shirt 1850 2026",
                "sarong -1000 2026", "cueitl -1000 1600",
                "huipil -1000 1900", "anacu -800 1533", "vestment 1520 2026",
                "quechquemitl -500 1600", "poncho -500 1900", "mantle_mesoamerican -1000 1700",
                "tilmatli -800 1520", "lliclla -800 1600", "cloak -1000 1700"},
        {"global",
                "tunic -3000 1400", "tunic_long -3000 900", "wrapper_top -3000 2026",
                "shirt 1300 2026", "sarong -1000 2026", "trousers 1400 2026",
                "chemise 800 1900", "gown 1200 1900", "kaftan 500 1900", "robe 500 2026",
                "vestment 300 2026", "cloak -3000 1700", "shawl_mesopotamian -2000 900",
                "cape_hooded 1300 1900"},
    };

    private static final java.util.Map<String, int[]> WINDOWS = new java.util.HashMap<>();
    private static final java.util.Map<String, List<Garment>> BY_PLACE = new java.util.HashMap<>();

    static {
        for (String[] row : WARDROBE) {
            List<Garment> list = new ArrayList<>();
            for (int i = 1; i < row.length; i++) {
                String[] bits = row[i].split(" ");
                Garment gm = byId(bits[0]);
                list.add(gm);
                WINDOWS.put(row[0] + ":" + gm.id(),
                        new int[]{Integer.parseInt(bits[1]), Integer.parseInt(bits[2])});
            }
            BY_PLACE.put(row[0], List.copyOf(list));
        }
    }

    private static Garment byId(String id) {
        for (Garment gm : ALL) {
            if (gm.id().equals(id)) {
                return gm;
            }
        }
        throw new IllegalArgumentException("no garment called " + id);
    }

    private static Garment g(String id, Part part, Collar collar, int sleeve, int hem, int belt,
                             int drape, boolean loose, int from, int to) {
        return new Garment(id, part, collar, sleeve, hem, belt, drape, loose, from, to);
    }


    // =====================================================================================
    // The two classes the catalogue did not have: the head and the feet.
    // =====================================================================================

    /** The shape of a piece of headwear. */
    public enum Hat { TURBAN, HEADCLOTH, CAP, FEZ, CONICAL, STRAW, HOOD, LAUREL, CROWN, VEIL, FILLET,
        FUR, SNOOD }

    /** One kind of headwear, and the years a place wore it. */
    public record Head(String id, Hat hat, int from, int to) { }

    /** The shape of a shoe. */
    public enum Shoe { BAREFOOT, SANDALS, SHOES, BOOTS, CLOGS, MOCCASINS, WRAPS }

    /** One kind of footwear. */
    public record Foot(String id, Shoe shoe, int from, int to) { }

    /** The silhouette of a garment: how wide the shoulders are, and how far the hem swings. */
    public record Shape(int shoulder, int flare) { }

    private static final java.util.Map<String, List<Head>> HEADS = new java.util.HashMap<>();
    private static final java.util.Map<String, List<Foot>> FEET = new java.util.HashMap<>();
    private static final java.util.Map<String, Shape> SHAPES = new java.util.HashMap<>();

    private static void heads(String place, String... spec) {
        List<Head> list = new ArrayList<>();
        for (String s : spec) {
            String[] b = s.split(" ");
            list.add(new Head(b[0], Hat.valueOf(b[1]), Integer.parseInt(b[2]),
                    Integer.parseInt(b[3])));
        }
        HEADS.put(place, List.copyOf(list));
    }

    private static void feet(String place, String... spec) {
        List<Foot> list = new ArrayList<>();
        for (String s : spec) {
            String[] b = s.split(" ");
            list.add(new Foot(b[0], Shoe.valueOf(b[1]), Integer.parseInt(b[2]),
                    Integer.parseInt(b[3])));
        }
        FEET.put(place, List.copyOf(list));
    }

    private static void shape(String id, int shoulder, int flare) {
        SHAPES.put(id, new Shape(shoulder, flare));
    }

    static {
        heads("deccan", "headcloth HEADCLOTH -1000 1200", "turban TURBAN 1200 2026",
                "cap CAP 1300 1900", "veil VEIL 1200 2026", "fillet FILLET -1000 -200");
        heads("persianate", "headcloth HEADCLOTH -1000 700", "turban TURBAN 700 1900",
                "cap CAP 1300 1900", "fur FUR 900 1900", "veil VEIL 700 2026",
                "conical CONICAL -500 700");
        heads("mesopotamian", "fillet FILLET -3000 -1000", "cap CAP -1000 600",
                "conical CONICAL -3000 -600", "veil VEIL -2000 900",
                "headcloth HEADCLOTH 600 1400", "turban TURBAN 1400 2026", "fez FEZ 1500 1950");
        heads("mediterranean", "laurel LAUREL -800 -100", "fillet FILLET -500 400",
                "veil VEIL -300 1400", "snood SNOOD 1200 1500", "cap CAP 1300 1700",
                "hood HOOD 1000 1600", "straw STRAW 1400 1900", "bonnet CAP 1700 1900");
        heads("rome", "laurel LAUREL -600 400", "veil VEIL -200 400", "hood HOOD 1000 1500",
                "cap CAP 1400 1700", "straw STRAW 1500 1900");
        heads("north_african", "headcloth HEADCLOTH -2000 700", "turban TURBAN 700 2026",
                "fez FEZ 1500 2026", "veil VEIL 700 2026", "hood HOOD -3000 -500");
        heads("african", "headcloth HEADCLOTH -1000 2026", "cap CAP 1200 2026",
                "turban TURBAN 1300 1900", "veil VEIL 1300 2026", "crown CROWN 1200 1900");
        heads("east_asian", "conical CONICAL -1000 1900", "cap CAP -500 1900",
                "crown CROWN 600 1900", "headcloth HEADCLOTH -1000 2026", "straw STRAW 1400 2026");
        heads("americas", "fillet FILLET -1000 1520", "crown CROWN -800 1520",
                "headcloth HEADCLOTH -1000 1900", "cap CAP 1600 1900");
        heads("global", "headcloth HEADCLOTH -2000 1400", "cap CAP 1400 2026");

        feet("deccan", "bare BAREFOOT -3000 900", "sandals SANDALS -1000 2026",
                "shoes SHOES 1300 2026", "wraps WRAPS 1200 1900");
        feet("persianate", "sandals SANDALS -1000 900", "shoes SHOES 900 2026",
                "boots BOOTS 1200 1900");
        feet("mesopotamian", "bare BAREFOOT -3000 -2000", "sandals SANDALS -2000 700",
                "shoes SHOES 700 1400", "shoes SHOES 1400 2026", "boots BOOTS 1500 1900");
        feet("mediterranean", "sandals SANDALS -800 500", "shoes SHOES 500 1800",
                "boots BOOTS 1200 1700", "clogs CLOGS 1300 1900", "bare BAREFOOT -800 -300");
        feet("rome", "sandals SANDALS -600 500", "shoes SHOES 500 1700", "boots BOOTS 1200 1600");
        feet("north_african", "sandals SANDALS -3000 900", "shoes SHOES 900 2026",
                "boots BOOTS 1200 1800");
        feet("african", "bare BAREFOOT -2000 1700", "sandals SANDALS -500 2026",
                "shoes SHOES 1700 2026");
        feet("east_asian", "shoes SHOES -1000 2026", "clogs CLOGS 600 1900",
                "boots BOOTS 1300 1900");
        feet("americas", "bare BAREFOOT -1000 1520", "sandals SANDALS -800 1700",
                "moccasins MOCCASINS -1000 1900", "shoes SHOES 1700 2026");
        feet("global", "bare BAREFOOT -3000 500", "sandals SANDALS -1000 1400",
                "shoes SHOES 1200 2026");

        shape("boubou", 2, 3); shape("agbada", 2, 3); shape("kaftan", 2, 2); shape("djellaba", 2, 2);
        shape("chapan", 2, 2); shape("aba", 1, 3); shape("burnous", 1, 3); shape("haik", 1, 2);
        shape("houppelande", 1, 3); shape("gown", 1, 2); shape("kirtle", 0, 2);
        shape("cotehardie", 0, 1); shape("sherwani", 1, 1); shape("achkan", 1, 1); shape("jama", 1, 2);
        shape("chapkan", 1, 1); shape("kurta", 0, 0); shape("kurti", 0, 0); shape("kameez", 0, 0);
        shape("hanfu", 1, 1); shape("hanfu_wide", 2, 2); shape("shenyi", 1, 2); shape("kimono", 1, 1);
        shape("junihitoe", 2, 3); shape("kosode", 1, 1); shape("yukata", 0, 1);
        shape("robe", 1, 2); shape("qaba", 1, 1); shape("phiran", 1, 2); shape("sari", 0, 2);
        shape("lehenga", 1, 3); shape("ghagra", 1, 3); shape("vestment", 1, 2); shape("chiton", 0, 2);
        shape("peplos", 0, 2); shape("stola", 0, 2); shape("huipil", 0, 1); shape("anacu", 0, 2);
        shape("toga", 1, 2); shape("himation", 1, 2); shape("chlamys", 0, 1); shape("palla", 1, 2);
        shape("poncho", 2, 1); shape("angavastram", 0, 0); shape("dupatta", 0, 0);
        shape("chador", 1, 2); shape("mantle", 1, 2); shape("cape_hooded", 1, 2); shape("cloak", 1, 2);
        shape("coat", 0, 1); shape("greatcoat", 1, 2); shape("apron", 0, 0); shape("doublet", 0, 0);
        shape("tunic", 0, 0); shape("tunica", 0, 0); shape("tunic_long", 0, 1); shape("chemise", 0, 1);
    }

    /**
     * The wardrobe a culture draws from. Several cultures share one wardrobe: a Chola farmer and a
     * Mughal noble wear different things, but both come out of the Indian list, and the year decides.
     */
    private static String place(String culture) {
        if (culture == null) {
            return "global";
        }
        return switch (culture) {
            case "indic", "maurya", "chandela", "chola", "vijayanagara", "mughal", "maratha",
                 "hyderabad", "delhi_sultanate", "gond", "rajput" -> "deccan";
            case "japan", "china", "korea", "yamato", "qing", "ming", "song", "tang", "han" ->
                    "east_asian";
            case "persian", "sogdian", "seljuk", "safavid", "timurid", "khwarezm", "ottoman" ->
                    "persianate";
            case "sumerian", "akkadian", "babylonian", "assyrian", "aramean", "levantine",
                 "hittite", "phoenician" -> "mesopotamian";
            case "greek", "hellenic", "athenian", "spartan", "byzantine", "italian", "florence",
                 "venice", "castilian", "french", "english", "german", "iberian", "dutch",
                 "portuguese", "spanish", "polish", "hungarian", "norse", "viking" -> "mediterranean";
            case "roman", "roman_republic", "roman_empire", "spqr" -> "rome";
            case "egyptian", "mamluk", "fatimid", "ayyubid", "ottoman_egypt", "maghreb", "berber",
                 "moroccan", "tunisian", "libyan", "nubian" -> "north_african";
            case "songhai", "songhai_empire", "mali", "ghana", "kanem", "zulu", "yoruba", "benin",
                 "ethiopian", "swahili", "hausa", "kongo", "zimbabwe", "dahomey", "somali" ->
                    "african";
            case "aztec", "maya", "inca", "olmec", "toltec", "andean", "inuit", "iroquois",
                 "zapotec", "mixtec", "chimu", "muisca", "pueblo", "mississippian" -> "americas";
            default -> culture;
        };
    }

    /** Every garment a place knows, in the order of the wardrobe table, whatever the year. */
    public static List<Garment> all(String culture) {
        List<Garment> out = BY_PLACE.get(place(culture));
        return out == null ? BY_PLACE.get("global") : out;
    }

    /** The garments of one category that fit a person: their place, year, trade and sex. */
    public static List<Garment> of(String culture, String subpool, int year, String role,
                                   boolean female, Part want) {
        List<Garment> out = new ArrayList<>();
        for (Garment gm : forPerson(culture, subpool, year, role, female)) {
            if (gm.part() == want) {
                out.add(gm);
            }
        }
        return out;
    }

    public static List<Garment> forPerson(String culture, int year, String role, boolean female) {
        return forPerson(culture, null, year, role, female);
    }

    /**
     * The garments that fit one person, down to the sub-place where it matters: a kimono belongs to the
     * islands of east Asia and a hanfu to the mainland, so the sub-pool decides between them.
     */
    public static List<Garment> forPerson(String culture, String subpool, int year, String role,
                                          boolean female) {
        List<Garment> out = new ArrayList<>();
        boolean uniform = "soldier".equals(role) || "guard".equals(role);
        boolean priest = "priest".equals(role) || "healer".equals(role);
        boolean islands = "islands".equals(subpool);
        String pl = place(culture);
        for (Garment gm : all(culture)) {
            if (!gm.holds(culture, year)) {
                continue;
            }
            if (priest && gm.part() != Part.DRESS && gm.part() != Part.OUTER) {
                continue;                                   // a priest wears one long cloth and a shawl
            }
            if (uniform && gm.part() == Part.DRESS) {
                continue;                                   // a soldier does not fight in a robe
            }
            if (female && MALE_ONLY.contains(gm.id())) {
                continue;
            }
            if (!female && FEMALE_ONLY.contains(gm.id())) {
                continue;
            }
            // ---- east Asia is three wardrobes, not one. The islands wear the kimono family; the
            // Chinese mainland wears the crossed collar and the round collar; the southern coast wears
            // the Vietnamese ao. Before this, a farmer outside Beijing could be given court dress from
            // Kyoto, which is the kind of leak that makes every place look the same.
            boolean japanese = JAPAN.contains(gm.id());
            boolean chinese = CHINA.contains(gm.id());
            boolean vietnamese = VIETNAM.contains(gm.id());
            if ("east_asian".equals(pl)) {
                if (japanese && !islands) {
                    continue;
                }
                if (chinese && !"east".equals(subpool) && !subpoolAllowed(subpool)) {
                    continue;
                }
                if (vietnamese && !("south".equals(subpool) || islands)) {
                    continue;
                }
            }
            // ---- the Americas are two worlds: Mesoamerica wove the huipil and the quechquemitl, the
            // Andes wove the anacu and the uncu. A cloth from Cusco is not worn in Tenochtitlan.
            if ("americas".equals(pl)) {
                if (MESO.contains(gm.id()) && "andean".equals(subpool)) {
                    continue;
                }
                if (ANDEAN.contains(gm.id()) && "meso".equals(subpool)) {
                    continue;
                }
            }
            if (gm == APRON && !("mediterranean".equals(pl) || "rome".equals(pl))) {
                continue;
            }
            // ---- the sub-place. The table above is by place; a sub-pool can do better where the
            // record does: Bengal's fine cotton, the Maghreb's burnous and haik against Egypt's long
            // galabiya, the Swahili kanga, the Yoruba wrapper. Anything not named here is unchanged.
            if (!holdsSub(subpool, gm.id(), year)) {
                continue;
            }
            out.add(gm);
        }
        if (out.isEmpty()) {
            out.add(TUNIC);
        }
        // ---- the trade decides the register of the cloth. Until this, a farmer, a merchant and a
        // scholar in the same town and year drew from exactly the same list, so the trade never reached
        // the garment at all: six of the thirteen trades came out as the identical picture and a scribe,
        // a scholar and an advisor as one man. A working trade now draws the short working cloth, the
        // mercantile and the lettered draw the long one, and the middling trades draw either.
        if (!uniform && role != null) {
            int want = register(role);
            List<Garment> pick = new ArrayList<>();
            for (Garment gm : out) {
                if (registerOf(gm) == want) {
                    pick.add(gm);
                }
            }
            if (!pick.isEmpty()) {
                out = pick;
            }
        }
        return out;
    }

    /** 0 for a trade that works with its hands, 1 for a middling one, 2 for a lettered or a mercantile. */
    private static int register(String role) {
        return switch (role.toLowerCase(java.util.Locale.ROOT)) {
            case "scholar", "scribe", "advisor", "elder", "priest", "monk", "imam", "rabbi", "clerk",
                 "teacher", "noble" -> 2;
            case "merchant", "trader", "pedlar", "healer", "midwife", "apothecary", "citizen",
                 "townsman", "resident" -> 1;
            default -> 0;                    // a farmer, a herder, an artisan, a villager, a labourer
        };
    }

    /** The same scale, read off the garment: how much cloth it is and how far down it comes. */
    private static int registerOf(Garment gm) {
        if (gm.part() == Part.LOWER) {
            return 1;                        // a lower garment is worn in every register; it decides nothing
        }
        int bulk = gm.sleeve() + gm.hem() + (gm.part() == Part.DRESS ? 6 : 0) + (gm.loose() ? 2 : 0);
        return bulk >= 23 ? 2 : bulk >= 18 ? 1 : 0;
    }

    /** Garments of one category, by place and year, ignoring the person. For catalogues and probes. */
    public static List<Garment> of(String culture, int year, Part want) {
        List<Garment> out = new ArrayList<>();
        for (Garment gm : all(culture)) {
            if (gm.part() == want && gm.holds(culture, year)) {
                out.add(gm);
            }
        }
        return out;
    }

    /** The kimono family, worn on the islands. */
    private static final java.util.Set<String> JAPAN = java.util.Set.of("kimono", "kosode", "yukata",
            "uchikake", "haori", "hakama", "junihitoe", "uchigi", "jeogori", "chima", "kariginu",
            "shitagasane", "kantoi", "suikan", "hanten");

    /** The mainland wardrobe: the crossed collar, the round collar, the jacket and trousers. */
    private static final java.util.Set<String> CHINA = java.util.Set.of("hanfu", "hanfu_wide", "shenyi",
            "round_collar", "ru", "ao", "beizi", "daxiushan", "banbi", "ku", "dudou", "qipao");

    /** The southern coast: the Vietnamese ao and its two-piece cousin. */
    private static final java.util.Set<String> VIETNAM = java.util.Set.of("ao_dai", "ao_tu_than");

    /** Mesoamerican cloth. */
    private static final java.util.Set<String> MESO = java.util.Set.of("huipil", "cueitl", "xicoli",
            "quechquemitl", "tilmatli", "mantle_mesoamerican");

    /** Andean cloth. */
    private static final java.util.Set<String> ANDEAN = java.util.Set.of("anacu", "uncu", "lliclla");

    /** A sub-pool the table above does not name shares the mainland wardrobe. */
    private static boolean subpoolAllowed(String subpool) {
        return subpool == null || subpool.isBlank();
    }

    /**
     * The sub-place window. A garment the table gives a place can still be narrowed or denied to one
     * of that place's regions: a burnous is Maghreb, not Egypt, and a kanga is the Swahili coast.
     */
    private static boolean holdsSub(String subpool, String id, int year) {
        if (subpool == null) {
            return true;
        }
        switch (subpool + ":" + id) {
            case "delta:burnous", "delta:haik", "delta:djellaba", "delta:sirwal" -> {
                return year > 1900;                     // these are the Maghreb's, not the Nile's
            }
            case "east:kanga" -> {
                return year >= 1800;
            }
            default -> {
                return true;
            }
        }
    }

    private static final java.util.Set<String> MALE_ONLY = java.util.Set.of(
            "toga", "toga_picta", "doublet", "jama", "achkan", "sherwani", "braies", "breeches",
            "hose", "kandys", "choga", "chapan", "arkhalig", "angarkha", "chapkan", "uncu",
            "subligaculum", "warrior_tunic");

    private static final java.util.Set<String> FEMALE_ONLY = java.util.Set.of(
            "stola", "palla", "choli", "kanchuki", "ghagra", "huipil", "sari", "cueitl", "petticoat",
            "bodice", "anacu", "chima", "gele");

    /** One person's pick, from their own number: the same person always wears the same type. */
    public static Garment pick(String culture, int year, String role, boolean female, long seed) {
        return pick(culture, null, year, role, female, seed);
    }

    public static Garment pick(String culture, String subpool, int year, String role, boolean female,
                               long seed) {
        List<Garment> options = forPerson(culture, subpool, year, role, female);
        return options.get((int) Math.floorMod(seed ^ 0x51AF3DL, options.size()));
    }

    /** A person's pick out of one category: what they put on over the top, and what on the legs. */
    public static Garment pick(String culture, String subpool, int year, String role, boolean female,
                               long seed, Part want) {
        List<Garment> options = forPerson(culture, subpool, year, role, female);
        List<Garment> cand = new ArrayList<>();
        for (Garment gm : options) {
            if (gm.part() == want) {
                cand.add(gm);
            }
        }
        if (cand.isEmpty()) {
            return null;
        }
        return cand.get((int) Math.floorMod(seed ^ 0x51AF3DL ^ want.ordinal(), cand.size()));
    }


    /** What a place puts on a head in a given year, or none. */
    public static Head head(String culture, String subpool, int year, boolean female, long seed) {
        List<Head> list = HEADS.get(place(culture));
        if (list == null) {
            list = HEADS.get("global");
        }
        List<Head> ok = new ArrayList<>();
        for (Head h : list) {
            if (year < h.from() || year > h.to()) {
                continue;
            }
            if (female ? h.hat() == Hat.LAUREL || h.hat() == Hat.CROWN : h.hat() == Hat.VEIL
                    || h.hat() == Hat.SNOOD) {
                continue;
            }
            if ("islands".equals(subpool) && h.hat() == Hat.CONICAL) {
                continue;
            }
            if ("east".equals(subpool) && h.hat() == Hat.CONICAL && year > 1700) {
                continue;
            }
            ok.add(h);
        }
        return ok.isEmpty() ? null : ok.get((int) Math.floorMod(seed ^ 0x2B7F1DL, ok.size()));
    }

    /** What a place puts on its feet in a given year. */
    public static Foot foot(String culture, int year, long seed) {
        List<Foot> list = FEET.get(place(culture));
        if (list == null) {
            list = FEET.get("global");
        }
        List<Foot> ok = new ArrayList<>();
        for (Foot f : list) {
            if (year >= f.from() && year <= f.to()) {
                ok.add(f);
            }
        }
        return ok.isEmpty() ? new Foot("bare", Shoe.BAREFOOT, year, year)
                : ok.get((int) Math.floorMod(seed ^ 0x6D3A9FL, ok.size()));
    }

    /** How wide the shoulders of a garment are and how far its hem swings. */
    public static Shape shape(String id) {
        Shape s = SHAPES.get(id);
        return s == null ? new Shape(0, 0) : s;
    }
    /** Every distinct garment type in the catalogue. */
    public static int catalogueSize() {
        return ALL.size();
    }

    /** The places with a wardrobe of their own. */
    public static List<String> places() {
        return new ArrayList<>(BY_PLACE.keySet());
    }
}

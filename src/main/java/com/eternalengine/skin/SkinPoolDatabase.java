package com.eternalengine.skin;

import com.eternalengine.EternalEngineMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The skin pool - the names pattern, applied to faces and clothes.
 *
 * <h3>The pattern being copied</h3>
 * {@code VillagerNameDatabase} keeps, per region: {@code firstNamesMale}, {@code firstNamesFemale},
 * {@code surnames} and {@code rolesTitles{role -> words}}; loads them world-pack → jar → code defaults;
 * resolves an id through <b>its own region → its culture group → generic</b>; and draws at spawn from a
 * {@code Random}. This class is that, for skins:
 *
 * <pre>
 *   firstNamesMale / firstNamesFemale   ->  tones and hairs
 *   surnames                            ->  cloths (the colours of a culture's cloth)
 *   rolesTitles{role}                   ->  garments{role} and uniforms{role}
 *   poolFor / cultureFor                ->  the same two functions, called here
 *   pack -> jar -> code defaults        ->  the same three levels
 * </pre>
 *
 * <h3>Two rules that come from the user, not from the pattern</h3>
 * <ol>
 *   <li><b>Clothes change every day; a face never does.</b> The face (tone, hair colour, hair style) is
 *       a function of the person's UUID alone. The clothes are a function of the UUID <i>and the day</i>.</li>
 *   <li><b>A uniform does not dress by the day.</b> Soldier, guard and priest wear one cloth always -
 *       that is what makes it a uniform, and it is checked in {@code skin_pools.py}.</li>
 * </ol>
 *
 * <h3>Place, not just culture</h3>
 * The pool a person draws from is chosen by <i>where he is</i>, not only by his civ id: see
 * {@link SkinPlaceResolver}. This class holds the pools; the resolver says which one applies.
 *
 * <p>The colours and the turning points here are plausible and labelled: they are the user's call, and
 * this file (or the JSON beside it) is the one place to change them.
 */
public final class SkinPoolDatabase {

    /** Where the shipped pool lives inside the jar, beside the names pack. */
    private static final String PACKAGED_POOL = "/data/eternalengine/eternal/names/skin_pool.json";

    /** The last resort, reachable only by an id that is not a civ at all - the names' `global`. */
    public static final String GENERIC = "global";

    private final ConcurrentHashMap<String, Pool> pools = new ConcurrentHashMap<>();

    public SkinPoolDatabase() {
        registerDefaults();
    }

    // ==================== the pool ====================

    /**
     * One culture's pool.
     *
     * @param tones          skin tones, as 0xRRGGBB
     * @param hairs          hair colours, as 0xRRGGBB
     * @param cloths         the colours of its cloth, as 0xRRGGBB
     * @param trim           the metal and the trim: gold, brass, bone
     * @param hairStylesMale / hairStylesFemale   the styles its men and women wear
     * @param garments       role -> the garments that trade may wear
     * @param uniforms       role -> the one set that trade wears every day
     * @param uniformNotes   role -> why (a log line, and honest documentation)
     * @param lower / femaleLower   the historical lower garment
     * @param femaleJewels   which marks the historical dress wears
     * @param era            the twelve turning points: classical, imperial, early, colonial, modern,
     *                       robe, wrap, drape, keep_hat, revival, arm_end_m, arm_end_f
     * @param cultural       garments named here but NOT drawn yet - declared, never claimed
     */
    public record Pool(int[] tones, int[] hairs, int[] cloths, int trim,
                       String[] hairStylesMale, String[] hairStylesFemale,
                       Map<String, List<String>> garments, Map<String, List<String>> uniforms,
                       Map<String, String> uniformNotes, String lower, String femaleLower,
                       String femaleJewels, Map<String, Integer> era, List<String> cultural,
                       String culture) {

        /** A pool that does not know its own id - the shape before culture ids were carried. */
        public Pool(int[] tones, int[] hairs, int[] cloths, int trim,
                    String[] hairStylesMale, String[] hairStylesFemale,
                    Map<String, List<String>> garments, Map<String, List<String>> uniforms,
                    Map<String, String> uniformNotes, String lower, String femaleLower,
                    String femaleJewels, Map<String, Integer> era, List<String> cultural) {
            this(tones, hairs, cloths, trim, hairStylesMale, hairStylesFemale, garments, uniforms,
                    uniformNotes, lower, femaleLower, femaleJewels, era, cultural, null);
        }

        public int era(String key) {
            return era.getOrDefault(key, 0);
        }
    }

    // ==================== the wardrobe (ported from skins_v6.py) ====================
    /**
     * One garment: the name the sheets use, who wears it, <b>the year it arrives</b>, which part of the
     * body it covers, and the trades that favour it.
     *
     * <p>The arrival years are the load-bearing numbers here. They are why a person in 1912 and the same
     * person in 2025 are not wearing the same thing: the wardrobe grows with the year, part by part. This
     * table is the Python's, name for name - {@code wide_jeans} is a woman's cur, not a synonym, and
     * {@code SkinPainter} draws each cut rather than substituting a shape and calling it the same cloth.
     */
    public record Garment(String name, boolean female, int arrival, String part, List<String> roles) {}

    /** The order the parts are decided in, the Python's order. */
    private static final List<String> PART_ORDER = List.of("top", "outer", "bottom", "foot", "head",
            "extra", "dress");

    private static final List<Garment> WARDROBE = List.of(
            new Garment("crew_tee", false, 1913, "top", List.of("farmer", "artisan", "guard")),
            new Garment("graphic_tee", false, 1965, "top", List.of("artisan", "citizen", "scribe")),
            new Garment("polo", false, 1926, "top", List.of("merchant", "healer", "soldier")),
            new Garment("mock_turtleneck", false, 1966, "top", List.of("scholar", "scribe", "advisor")),
            new Garment("hoodie", false, 1970, "top", List.of("artisan", "guard", "citizen", "villager")),
            new Garment("sweatshirt", false, 1926, "top", List.of("farmer", "guard", "soldier")),
            new Garment("cardigan", false, 1920, "top", List.of("scholar", "healer", "advisor", "priest")),
            new Garment("button_down", false, 1900, "top", List.of("merchant", "scholar", "priest", "advisor", "scribe")),
            new Garment("trench", false, 1914, "outer", List.of("merchant", "scholar", "advisor")),
            new Garment("bomber", false, 1950, "outer", List.of("soldier", "guard")),
            new Garment("shacket", false, 2010, "outer", List.of("farmer", "artisan")),
            new Garment("blazer", false, 1980, "outer", List.of("merchant", "advisor", "scribe")),
            new Garment("puffer_vest", false, 1975, "outer", List.of("guard", "soldier", "citizen")),
            new Garment("jeans", false, 1873, "bottom", List.of("farmer", "artisan", "citizen", "villager", "guard")),
            new Garment("trousers", false, 1920, "bottom", List.of("merchant", "scholar", "priest", "advisor", "scribe")),
            new Garment("cargo", false, 1985, "bottom", List.of("soldier", "artisan")),
            new Garment("joggers", false, 1980, "bottom", List.of("guard", "citizen", "villager")),
            new Garment("shorts", false, 1960, "bottom", List.of("farmer", "citizen")),
            new Garment("sneakers", false, 1950, "foot", List.of("citizen", "villager", "artisan")),
            new Garment("chelsea", false, 1955, "foot", List.of("merchant", "scholar", "advisor")),
            new Garment("crossbody", false, 1995, "extra", List.of("merchant", "scribe", "scholar")),
            new Garment("cap", false, 1954, "head", List.of("farmer", "artisan", "guard")),

            new Garment("crop_top", true, 1985, "top", List.of("artisan", "citizen")),
            new Garment("tank", true, 1970, "top", List.of("farmer", "citizen", "villager")),
            new Garment("camisole", true, 1993, "top", List.of("scribe", "citizen")),
            new Garment("v_neck_tee", true, 1955, "top", List.of("healer", "merchant", "citizen")),
            new Garment("oversized_shirt", true, 1985, "top", List.of("scholar", "advisor", "merchant")),
            new Garment("ribbed_cardigan", true, 2000, "top", List.of("scholar", "healer", "advisor", "priest")),
            new Garment("trench_w", true, 1914, "outer", List.of("merchant", "scholar", "advisor")),
            new Garment("oversized_blazer", true, 1988, "outer", List.of("advisor", "scribe", "merchant")),
            new Garment("cropped_puffer", true, 2015, "outer", List.of("citizen", "guard", "farmer")),
            new Garment("sheath_dress", true, 1955, "dress", List.of("healer", "priest", "scribe")),
            new Garment("slip_dress", true, 1993, "dress", List.of("citizen", "scribe")),
            new Garment("wide_jeans", true, 1975, "bottom", List.of("citizen", "artisan", "villager", "farmer")),
            new Garment("mom_denim", true, 1988, "bottom", List.of("artisan", "citizen", "scribe")),
            new Garment("pleated_w", true, 1955, "bottom", List.of("scholar", "advisor", "merchant")),
            new Garment("midi_skirt", true, 1995, "bottom", List.of("merchant", "scribe", "citizen")),
            new Garment("biker_shorts", true, 1990, "bottom", List.of("citizen", "guard", "artisan")),
            new Garment("loafers", true, 1993, "foot", List.of("merchant", "scholar", "advisor")),
            new Garment("dad_sneakers", true, 1995, "foot", List.of("citizen", "villager", "artisan", "healer")),
            new Garment("shoulder_bag", true, 2000, "extra", List.of("merchant", "scribe", "advisor")),
            new Garment("bucket_hat", true, 1965, "head", List.of("farmer", "artisan", "citizen")));

    /** How many of a year's garments a gender has: the "the wardrobe grows with the year" number. */
    public static int wardrobeSize(boolean female, int year) {
        int n = 0;
        for (Garment g : WARDROBE) {
            if (g.female() == female && g.arrival() <= year) {
                n++;
            }
        }
        return n;
    }

    // ==================== loading ====================


    /**
     * Read the pool from a world folder, falling back to the copy in the jar, falling back to the code
     * defaults already registered. The same three levels as the names pack.
     *
     * @return how many cultures were loaded, 0 if neither copy could be read
     */
    public int loadFrom(Path dir) {
        JsonObject root = null;
        Path file = dir == null ? null : dir.resolve("skin_pool.json");
        if (file != null && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                EternalEngineMod.getLogger().warn(
                        "[SkinPoolDatabase] World skin pack unreadable ({}); using the packaged copy",
                        e.getMessage());
            }
        }
        if (root == null) {
            try (InputStream in = SkinPoolDatabase.class.getResourceAsStream(PACKAGED_POOL)) {
                if (in == null) {
                    EternalEngineMod.getLogger().warn(
                            "[SkinPoolDatabase] No skin pack at {} — built-in pools only", PACKAGED_POOL);
                    return 0;
                }
                root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                EternalEngineMod.getLogger().error(
                        "[SkinPoolDatabase] Failed to read the packaged skin pack: {}", e.getMessage());
                return 0;
            }
        }
        int loaded = loadJson(root);
        EternalEngineMod.getLogger().info(
                "[SkinPoolDatabase] Loaded {} skin pool(s), {} in total", loaded, pools.size());
        return loaded;
    }

    /** Read a pack in the shape the shipped file uses: one object per culture. */
    public int loadJson(JsonObject root) {
        if (root == null) {
            return 0;
        }
        int loaded = 0;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String culture = entry.getKey();
            if (culture.startsWith("_") || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject o = entry.getValue().getAsJsonObject();
            int[] tones = colours(o, "tones");
            int[] hairs = colours(o, "hairs");
            int[] cloths = colours(o, "cloths");
            if (tones.length == 0 || hairs.length == 0) {
                continue;                                  // one malformed entry is skipped, not fatal
            }
            put(culture.toLowerCase(Locale.ROOT), new Pool(
                    tones, hairs, cloths.length == 0 ? tones : cloths,
                    colour(o.has("trim") ? o.get("trim").getAsString() : "c9a227", 0xC9A227),
                    strings(o, "hairStylesMale"), strings(o, "hairStylesFemale"),
                    roleMap(o, "garments"), roleMap(o, "uniforms"), roleNotes(o, "uniforms"),
                    o.has("lower") ? o.get("lower").getAsString() : "trousers",
                    o.has("femaleLower") ? o.get("femaleLower").getAsString() : "skirt",
                    o.has("femaleJewels") ? o.get("femaleJewels").getAsString() : "earring",
                    eraMap(o), List.of(strings(o, "cultural")), culture.toLowerCase(Locale.ROOT)));
            loaded++;
        }
        return loaded;
    }

    public void put(String cultureId, Pool pool) {
        if (cultureId != null && pool != null) {
            pools.put(cultureId.toLowerCase(Locale.ROOT), pool);
        }
    }

    // ==================== lookup: the names rule ====================

    /** Its own pool first, then its culture group, then generic - exactly {@code poolFor} for names. */
    public String poolFor(String id) {
        if (id == null || id.isBlank()) {
            return GENERIC;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (pools.containsKey(key)) {
            return key;
        }
        String culture = cultureFor(key);
        return pools.containsKey(culture) ? culture : GENERIC;
    }

    public Pool pool(String cultureId) {
        return pools.getOrDefault(poolFor(cultureId), pools.get(GENERIC));
    }

    public int cultureCount() {
        return pools.size();
    }

    /** Every culture the pool knows. For the self-test's drift check, not for the game's path. */
    public java.util.Set<String> cultures() {
        return java.util.Collections.unmodifiableSet(pools.keySet());
    }

    /**
     * The culture group an unknown civ id belongs to, by the shape of its name.
     *
     * <p><b>Two ids this rule got wrong, and how.</b> The names version matched plain substrings, so
     * {@code chandela} matched "han" and became east_asian, and {@code songhai_empire} matched "song"
     * and became east_asian. Both are fixed here twice over: a name-by-name list for the strays, and a
     * rule - a needle shorter than five letters must match a whole word. Measured against the corpus:
     * 103 of 103 civ ids in the history packs resolve to a real pool, none to {@code global}.
     */
    public static String cultureFor(String civId) {
        if (civId == null || civId.isBlank()) {
            return GENERIC;
        }
        String id = civId.toLowerCase(Locale.ROOT);
        switch (id) {
            case "chandela" -> { return "indic"; }
            case "songhai_empire", "songhai" -> { return "african"; }
            // Rome has a pool of its own (the toga, the tunica) and was being folded into the general
            // Mediterranean one, so the toga could never be painted anywhere in the game.
            case "rome", "roman_republic", "roman_empire", "spqr" -> { return "rome"; }
            default -> { }
        }
        String[] words = id.replace('-', '_').split("_");

        if (hit(id, words, "india", "hyderabad", "deccan", "golconda", "qutb", "bahmani", "kakatiya",
                "chalukya", "rashtrakuta", "satavahana", "vijayanagara", "hoysala", "kadamba",
                "kakani", "andhra", "telangana", "telugu", "mysore", "wodeyar", "akkanna", "chandela",
                "shahi")) {
            return "deccan";
        }
        if (hit(id, words, "maurya", "gupta", "mughal", "maratha", "delhi", "rajput", "sikh", "chola",
                "chera", "cheras", "pandya", "pallava", "ganga", "sunga", "kushan", "nanda", "harsha",
                "gurjara", "ahir", "mewar", "jaipur", "indus", "harappa", "vedic", "aryan", "magadha",
                "pala", "vajji", "vatsa", "kosala", "kalinga", "ikshvaku", "yadava", "gujarat",
                "chaulukya", "malwa", "jaunpur", "bengal", "awadh", "bundela", "holkar", "bhopal",
                "amber", "marwar", "bhati", "tomara", "somavamshi", "kamarupa", "ahom", "manipur",
                "karkota", "dogra", "ladakh", "nepal", "anuradhapura", "polonnaruwa", "kandy", "jaffna",
                "sinhala", "ceylon", "zamorin", "travancore", "kolathiri", "malayali", "talpur",
                "samma", "sindh")) {
            return "indic";
        }
        if (hit(id, words, "persia", "sassanid", "achaemenid", "safavid", "timurid", "ottoman", "seljuk",
                "abbasid", "umayyad", "fatimid", "bahri", "mamluk", "shah", "afghan", "durrani",
                "khanate", "bukhara", "khiva", "samarkand", "central_asian")) {
            return "persianate";
        }
        if (hit(id, words, "china", "chinese", "han", "qin", "tang", "song", "ming", "qing", "zhou",
                "shang", "yuan", "korea", "japan", "yamato", "vietnam", "mongol", "xiongnu")) {
            return "east_asian";
        }
        if (hit(id, words, "rome", "roman", "byzant", "greek", "greece", "athens", "sparta", "macedon",
                "hellen", "carthage", "phoenic", "etrusc", "illyria", "gaul", "iberia", "ital",
                "venice", "genoa", "florence", "florentine", "tuscany", "tuscan", "siena", "pisa",
                "milan", "naples", "sicily", "lombard", "burgundy", "castile", "aragon", "leon",
                "portugal", "portuguese", "spain", "france", "england", "british",
                "dutch", "europe", "norman", "viking", "celtic", "poland", "hungary", "minoan",
                "mycenae", "aegean", "crete", "seleucid", "ptolemaic")) {
            return "mediterranean";
        }
        if (hit(id, words, "egypt", "nubia", "kush", "berber", "morocco", "tunis", "libya", "carthag",
                "numidia", "copt")) {
            return "north_african";
        }
        if (hit(id, words, "mali", "songhai", "ghana", "axum", "ethiopia", "swahili", "zulu", "kongo",
                "benin", "yoruba", "hausa", "kanem", "bornu", "nok", "africa", "somali", "kushite",
                "zimbabwe", "mutapa", "monomotapa", "buganda", "dahomey")) {
            return "african";
        }
        if (hit(id, words, "aztec", "maya", "inca", "olmec", "zapotec", "toltec", "iroquois",
                "cherokee", "navajo", "sioux", "hopi", "pueblo", "inuit", "mississip", "cahokia",
                "america", "maori", "hawaii", "polynesian", "nan_madol", "teotihuacan", "mixtec",
                "caral", "chimu", "muisca")) {
            return "americas";
        }
        if (hit(id, words, "sumer", "akkad", "babylon", "assyria", "mesopotam", "hittite", "elam",
                "urartu", "phoenic", "ugarit", "canaan", "hebrew", "israel", "judah", "aramaic")) {
            return "mesopotamian";
        }
        return GENERIC;
    }

    /** A needle of five letters or more may sit inside a longer word; below that it must be a word. */
    private static boolean hit(String id, String[] words, String... needles) {
        for (String n : needles) {
            if (id.contains(n)) {
                if (n.length() >= 5) {
                    return true;
                }
                for (String w : words) {
                    if (w.equals(n)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ==================== the draws ====================

    /** A person's face: tone, hair colour, hair style. A function of his UUID - it never changes. */
    public record Face(int tone, int hair, String style, int mouth, int blush) {}

    /** What a person wears on one day: garment names, or empty when the era's dress applies. */
    public record Outfit(List<String> garments, boolean uniform, String note) {}

    /**
     * The face. The tone and the hair colour come from <b>the place</b> (its flavour cell - neighbours
     * share; a sub-pool with its own colours overrides the culture's), the style from <b>the person</b>.
     * Neither takes the day, which is the point.
     *
     * @param tonePool the sub-pool's own tones, or null to use the culture's
     * @param hairPool the sub-pool's own hairs, or null to use the culture's
     */
    public Face faceFor(java.util.UUID uuid, boolean female, String cultureId, String flavour,
                        int[] tonePool, int[] hairPool) {
        Pool p = pool(cultureId);
        String who = uuid == null ? "unspawned" : uuid.toString();
        int[] toneSource = tonePool != null && tonePool.length > 0 ? tonePool : p.tones();
        int[] hairSource = hairPool != null && hairPool.length > 0 ? hairPool : p.hairs();
        int tone = toneSource[SkinHashes.pick(flavour + "|tone", toneSource.length)];
        int hair = hairSource[SkinHashes.pick(flavour + "|hair" + (female ? "F" : "M"),
                hairSource.length)];
        String[] styles = female ? p.hairStylesFemale() : p.hairStylesMale();
        if (styles.length == 0) {
            styles = new String[]{"short"};
        }
        String style = styles[SkinHashes.pick(who + "|style", styles.length)];
        // the person's own jitter, so a crowd of one village is not one face
        int j = (SkinHashes.pick(who + "|j", 9)) - 4;
        tone = shade(tone, j);
        int mouth = female ? 0xB05A5A : shade(tone, -30);
        int blush = female ? 0xC97A6A : 0;
        return new Face(tone, hair, style, mouth, blush);
    }

    /** The culture's own colours, when the place has no sub-pool of its own. */
    public Face faceFor(java.util.UUID uuid, boolean female, String cultureId, String flavour) {
        return faceFor(uuid, female, cultureId, flavour, null, null);
    }

    /**
     * The clothes. A uniform trade wears the same thing every day; everyone else draws from his own
     * small wardrobe, salted with the day. Empty means the historical dress stands.
     */
    public Outfit outfitFor(java.util.UUID uuid, boolean female, String role, String cultureId,
                            int year, long day) {
        Pool p = pool(cultureId);
        String who = uuid == null ? "unspawned" : uuid.toString();
        String r = role == null || role.isBlank() ? "citizen" : role.toLowerCase(Locale.ROOT);
        if ("money_changer".equals(r) || "paper_seller".equals(r) || "trader".equals(r) || "pedlar".equals(r)) {
            r = "merchant"; // step 19b: the money-changer dresses like a merchant; step 19d: the paper seller too;
                            // step 19g: the town trader and the village pedlar too
        } else if ("news_writer".equals(r)) {
            r = "scribe"; // step 19d: a newsletter writer is a scribe
        } else if ("town_crier".equals(r)) {
            r = "citizen"; // step 19d: the town crier wears what townspeople wear
        }

        List<String> uniform = p.uniforms().get(r);
        if (uniform != null && !uniform.isEmpty()) {
            return new Outfit(uniform, true, p.uniformNotes().getOrDefault(r, "the trade's own cloth"));
        }
        if (year < p.era("modern")) {
            // STEP 47 PART 4 (E12). This used to return NOTHING, and every person in the world
            // before the pool's modern turning point was dressed in nothing: every garment in
            // WARDROBE arrives in 1873 or later, so there was nothing else it could do.
            //
            // The period wardrobe answers for those years instead. It is a separate register
            // because it is a different subject (a tunic is not a T-shirt), and it is consulted
            // here rather than merged into WARDROBE because the modern list's arrival years are
            // the reason a person in 1912 and the same person in 2025 dress differently, and
            // rewriting them would lose that.
            //
            // Empty still means "the historical dress stands": if the wardrobe has nothing for this
            // culture and year, the caller draws the pool's own `lower` and that is the truth. The
            // difference is that now it usually has something.
            List<String> period = Wardrobe.get().outfitFor(cultureId, year, female, r);
            if (!period.isEmpty()) {
                return new Outfit(period, false, "the period wardrobe: " + Wardrobe.get().summary());
            }
            return new Outfit(List.of(), false, "the year has not reached the modern wardrobe");
        }
        List<String> licence = p.garments().getOrDefault(r,
                p.garments().getOrDefault("citizen", List.of()));
        Map<String, String> chosen = new LinkedHashMap<>();
        for (String part : PART_ORDER) {
            List<Garment> options = new ArrayList<>();
            for (Garment g : WARDROBE) {
                if (g.female() == female && g.arrival() <= year && g.part().equals(part)
                        && SkinPainter.has(g.name())) {
                    options.add(g);
                }
            }
            if (options.isEmpty()) {
                continue;                    // this part of the body has not arrived by this year
            }
            List<Garment> liked = new ArrayList<>();
            for (Garment g : options) {
                if (licence.contains(g.name()) || g.roles().contains(r)) {
                    liked.add(g);
                }
            }
            // 70% of the time he wears what his trade favours, the rest of the time whatever the year
            // has reached - the Python's rule, and the reason a market street is not a uniform
            List<Garment> from = !liked.isEmpty()
                    && SkinHashes.pick(who + "|" + part + "|" + r, 100) < 70 ? liked : options;
            chosen.put(part, from.get(SkinHashes.pick(who + "|" + part + "|" + day,
                    from.size())).name());
        }
        if (chosen.containsKey("dress")) {   // a dress is the top and the bottom at once
            chosen.remove("top");
            chosen.remove("bottom");
        }
        if (chosen.isEmpty()) {
            return new Outfit(List.of(), false, "nothing of this year's wardrobe has arrived");
        }
        List<String> garments = new ArrayList<>(chosen.values());
        return new Outfit(garments, false, String.join(" + ", garments));
    }

    /** The colours of the cloth. A uniform takes its colour from the trade, not from the day. */
    public int[] paletteFor(String cultureId, String flavour, java.util.UUID uuid, long day,
                            boolean uniform) {
        Pool p = pool(cultureId);
        int[] cloths = p.cloths();
        String who = uuid == null ? "unspawned" : uuid.toString();
        long d = uniform ? 0L : day;
        return new int[]{
                cloths[SkinHashes.pick(flavour + "|top|" + who + "|" + d, cloths.length)],
                cloths[SkinHashes.pick(flavour + "|outer|" + who + "|" + d, cloths.length)],
                cloths[SkinHashes.pick(flavour + "|bottom|" + who + "|" + d, cloths.length)],
                p.trim()
        };
    }

    // ==================== JSON helpers ====================

    private static int colour(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int[] colours(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) {
            return new int[0];
        }
        JsonArray arr = o.getAsJsonArray(key);
        int[] out = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = colour(arr.get(i).getAsString(), 0xA9714B);
        }
        return out;
    }

    private static String[] strings(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) {
            return new String[0];
        }
        JsonArray arr = o.getAsJsonArray(key);
        List<String> out = new ArrayList<>();
        for (JsonElement el : arr) {
            String s = el.getAsString();
            if (s != null && !s.isBlank()) {
                out.add(s);
            }
        }
        return out.toArray(new String[0]);
    }

    private static Map<String, List<String>> roleMap(JsonObject o, String key) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (o == null || !o.has(key) || !o.get(key).isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
            if (e.getValue().isJsonArray()) {
                out.put(e.getKey().toLowerCase(Locale.ROOT), List.of(strings(e.getValue().getAsJsonArray())));
            } else if (e.getValue().isJsonObject()) {          // uniforms carry a note as well
                JsonObject u = e.getValue().getAsJsonObject();
                out.put(e.getKey().toLowerCase(Locale.ROOT), List.of(strings(u, "garments")));
            }
        }
        return out;
    }

    private static Map<String, String> roleNotes(JsonObject o, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        if (o == null || !o.has(key) || !o.get(key).isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
            if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("note")) {
                out.put(e.getKey().toLowerCase(Locale.ROOT),
                        e.getValue().getAsJsonObject().get("note").getAsString());
            }
        }
        return out;
    }

    private static Map<String, Integer> eraMap(JsonObject o) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (o == null || !o.has("era") || !o.get("era").isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("era").entrySet()) {
            try {
                out.put(e.getKey(), turningPoint(e.getKey(), e.getValue().getAsDouble()));
            } catch (Exception ignored) {
                // skip a malformed turning point rather than losing the pool
            }
        }
        return out;
    }

    /**
     * The five shape curves are fractions in the pack (1.00, 0.85 - the Python's own numbers) and whole
     * percentages in the code (100, 85) so an integer map can hold them. Read both: two or less is a
     * fraction, more than that is already a percentage.
     *
     * <p>The first run of the self-test caught the naive read: it truncated every culture's robe, wrap,
     * drape, keep-hat and revival to zero, which would have put the whole world in plain European dress
     * and left every sheet a picture of a different world.
     */
    static int turningPoint(String key, double v) {
        boolean shape = key.equals("robe") || key.equals("wrap") || key.equals("drape")
                || key.equals("keep_hat") || key.equals("revival");
        return (int) Math.round(shape && v <= 2.0 ? v * 100.0 : v);
    }

    private static String[] strings(JsonArray arr) {
        List<String> out = new ArrayList<>();
        for (JsonElement el : arr) {
            String s = el.getAsString();
            if (s != null && !s.isBlank()) {
                out.add(s);
            }
        }
        return out.toArray(new String[0]);
    }

    /** Lighten or darken an 0xRRGGBB by {@code delta} per channel, clamped. */
    public static int shade(int rgb, int delta) {
        int r = Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) + delta));
        int g = Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) + delta));
        int b = Math.max(0, Math.min(255, (rgb & 0xFF) + delta));
        return (r << 16) | (g << 8) | b;
    }

    // ==================== the built-in pools ====================
    // Ported from skin_pools.py, which printed the sheets that were approved. Colours are hex there and
    // 0xRRGGBB here; nothing else changed. The user's call, and this is the place to change it.

    private void registerDefaults() {
        put(GENERIC, new Pool(
                new int[]{0xA9714B, 0xC08A5E, 0x8D5A39}, new int[]{0x1A1410, 0x2B1D14},
                new int[]{0x888888, 0xB3472F, 0x2F6EA8, 0xD9B25A, 0x6B7F4A, 0x8E3B57, 0xE0D3A8,
                        0x4A4436, 0xC4762B, 0x3D5F7A, 0x5B7A5A, 0x7A2F3F}, 0xC9A227,
                new String[]{"short"}, new String[]{"loose"},
                Map.of("citizen", List.of("crew_tee"), "villager", List.of("crew_tee")),
                Map.of(), Map.of(), "trousers", "skirt", "earring",
                era(0, 900, 1450, 1850, 1945, 1.0, 0.5, 0.3, 0.1, 0.0, 2, 2), List.of(), "global"));

        put("deccan", new Pool(
                new int[]{0xC08A5E, 0xA9714B, 0x8D5A39, 0xB57F55, 0x6F4526},
                new int[]{0x1A1410, 0x2B1D14, 0x3A2A1C, 0x141010},
                new int[]{0xB3472F, 0x2F6EA8, 0xE0D3A8, 0x6B7F4A, 0x8E3B57, 0xD9B25A, 0x4A4436, 0xC4762B, 0x3D5F7A, 0xA8452F, 0x5B7A5A, 0x7A2F3F, 0xE8C37A, 0x87A3B8, 0x2F4A3A, 0xC9A2A0},
                0xD9B25A,
                new String[]{"short", "short_beard", "turban_wrap", "long"},
                new String[]{"plait_over", "bun", "loose", "wrapped", "long"},
                Map.of("farmer", List.of("crew_tee"), "artisan", List.of("crew_tee", "graphic_tee"),
                        "merchant", List.of("button_down", "polo"), "scholar", List.of("mock_turtleneck", "button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee", "tank"),
                        "advisor", List.of("button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee", "tank")),
                Map.of("soldier", List.of("uniform_khaki"), "guard", List.of("uniform_khaki"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the regiment's khaki", "guard", "the same cloth as the soldier",
                        "priest", "the temple's vestment"),
                "dhoti", "sari", "bindi_nose_bangle",
                era(300, 1200, 1600, 1850, 1945, 1.00, 0.85, 0.55, 0.30, 0.10, 1, 2),
                List.of("kameez", "lungi", "angavastram", "sari"), "deccan"));

        put("indic", new Pool(
                new int[]{0xC08A5E, 0xA9714B, 0x8D5A39, 0xBB8760, 0x6F4526},
                new int[]{0x1A1410, 0x2B1D14, 0x3A2A1C},
                new int[]{0xC8452F, 0xE8A13A, 0x2F6EA8, 0xE0D3A8, 0x6B7F4A, 0x7D3F6B, 0xD96F2B, 0x3F6B6A, 0xB02F4A, 0xF0D9A0, 0x4A5B8A, 0x8A6B2F, 0xC9B08A, 0x5F7A3F, 0x9A3F5F, 0xC86A2A, 0x3A6B8A, 0xE8D8A8, 0x7A4A6A}, 0xD9B25A,
                new String[]{"short", "short_beard", "turban_wrap", "long"},
                new String[]{"plait_over", "bun", "loose", "wrapped", "long"},
                Map.of("farmer", List.of("crew_tee"), "artisan", List.of("graphic_tee", "crew_tee"),
                        "merchant", List.of("button_down"), "scholar", List.of("button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee"),
                        "advisor", List.of("button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee")),
                Map.of("soldier", List.of("uniform_khaki"), "guard", List.of("uniform_khaki"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the regiment's khaki", "guard", "the same cloth as the soldier",
                        "priest", "the temple's vestment"),
                "dhoti", "sari", "bindi_nose_bangle",
                era(200, 1100, 1600, 1850, 1950, 1.00, 0.95, 0.90, 0.10, 0.15, 1, 2), List.of(), "indic"));

        put("maurya", new Pool(
                new int[]{0xB57F55, 0xA9714B, 0x8D5A39}, new int[]{0x1A1410, 0x2B1D14},
                new int[]{0xD9B25A, 0xB3472F, 0x4A4436, 0xE0D3A8, 0x8A5A2F, 0x3F5B7A, 0x7A3F3F, 0xC9A06A, 0x5F6B4A, 0xA8783F, 0xB03A2E, 0x2F5F8A, 0xE8C34A, 0x9A4A3A, 0x7F8F5A, 0x4A4A52, 0xEFE6D0, 0x8A6A3A, 0xC2622E, 0x5A3F6B}, 0xC9A227,
                new String[]{"short", "short_beard", "turban_wrap", "long"},
                new String[]{"braid", "bun", "loose", "wrapped", "long"},
                Map.of("farmer", List.of("crew_tee"), "artisan", List.of("crew_tee"),
                        "merchant", List.of("button_down"), "scholar", List.of("button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee"),
                        "advisor", List.of("button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee")),
                Map.of("soldier", List.of("uniform_khaki"), "guard", List.of("uniform_khaki"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the regiment's khaki", "guard", "the same cloth as the soldier",
                        "priest", "the temple's vestment"),
                "dhoti", "sari", "bindi_nose_bangle",
                era(100, 800, 1400, 1800, 1950, 0.90, 1.00, 0.60, 0.15, 0.10, 1, 2), List.of(), "maurya"));

        put("persianate", new Pool(
                new int[]{0xC08A5E, 0xA9714B, 0xD09A70, 0x8D5A39}, new int[]{0x141010, 0x2B1D14, 0x3A2A1C},
                new int[]{0x2F4F7A, 0x7A2F3F, 0xE0D3A8, 0x3F6B5A, 0x4A4436, 0xD9B25A, 0x8A3F6A, 0x1F3F5F, 0xB8763F, 0x6A8A7A, 0xC9A2B0, 0x3F4A6A, 0xA83F3F, 0xE8D9C0, 0x2E7A7A, 0x8A2F4A, 0xD8B04A, 0x6B8A5A, 0xE8DCC0, 0x4A4A7A}, 0xD9B25A,
                new String[]{"short_beard", "turban_wrap", "short", "long"},
                new String[]{"wrapped", "bun", "loose", "braid", "long"},
                Map.of("farmer", List.of("crew_tee"), "artisan", List.of("crew_tee"),
                        "merchant", List.of("button_down", "polo"), "scholar", List.of("mock_turtleneck", "button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee"),
                        "advisor", List.of("button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee")),
                Map.of("soldier", List.of("uniform_khaki"), "guard", List.of("uniform_khaki"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the regiment's khaki", "guard", "the same cloth as the soldier",
                        "priest", "the mosque's vestment - a plain robe"),
                "paijama", "skirt", "earring_veil",
                era(500, 1000, 1600, 1850, 1935, 1.20, 0.70, 0.60, 0.55, 0.85, 1, 3), List.of(), "persianate"));

        put("mesopotamian", new Pool(
                new int[]{0xC08A5E, 0xA9714B, 0x8D5A39}, new int[]{0x141010, 0x2B1D14},
                new int[]{0xC8A06A, 0x8E5A3B, 0x4A5B6B, 0x7D3F3F, 0xB08A5A, 0x3F4F5F, 0x9A6B4A, 0x5F7A6A, 0xD9C08A, 0x6B4A3F, 0x2F2A26, 0xE8E0CC, 0x3A5F8A, 0xC08A2E, 0x4A7A6A, 0x6B7A4A, 0x9A4A2E, 0xD9A83A, 0x7A2F4A, 0x5A6A7A}, 0xC9A227,
                new String[]{"curly", "short_beard", "long", "short"},
                new String[]{"braid", "bun", "loose", "wrapped", "long"},
                Map.of("farmer", List.of("crew_tee"), "artisan", List.of("crew_tee"),
                        "merchant", List.of("button_down"), "scholar", List.of("button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee"),
                        "advisor", List.of("button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee")),
                Map.of("soldier", List.of("uniform_khaki"), "guard", List.of("uniform_khaki"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the regiment's khaki", "guard", "the same cloth as the soldier",
                        "priest", "the temple's vestment"),
                "skirt", "skirt", "earring",
                era(500, 1000, 1500, 1850, 1940, 1.10, 0.80, 0.50, 0.35, 0.20, 1, 3), List.of(), "mesopotamian"));

        put("mediterranean", new Pool(
                new int[]{0xE0B08A, 0xC99A72, 0xA9714B, 0xD9A97F}, new int[]{0x2B1D14, 0x4A3524, 0x1A1410, 0x6B4A2C},
                new int[]{0xE8E2D2, 0x2F4F7A, 0x8E3B3B, 0xD9B25A, 0x4A6B5A, 0xA85A3F, 0x3F5F8A, 0x6B8A7A, 0xC9B89A, 0x8A2F4A, 0x5A4A3A, 0xB0C0C8, 0x5A2F5A, 0x3A6B5A, 0xC8A03A, 0x8A4A2F}, 0xC9A227,
                new String[]{"short", "short_beard", "curly", "long"},
                new String[]{"bun", "braid", "loose", "wrapped", "long"},
                Map.of("farmer", List.of("crew_tee", "shorts"), "artisan", List.of("crew_tee", "graphic_tee"),
                        "merchant", List.of("button_down", "blazer"), "scholar", List.of("button_down", "cardigan"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee", "polo"),
                        "advisor", List.of("blazer", "button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee", "tank")),
                Map.of("soldier", List.of("uniform_green"), "guard", List.of("uniform_green"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the field uniform", "guard", "the same field uniform",
                        "priest", "the church's vestment"),
                "trousers", "skirt", "earring",
                era(0, 700, 1450, 1750, 1945, 1.00, 0.50, 0.20, 0.05, 0.0, 2, 2), List.of(), "mediterranean"));

        put("rome", new Pool(
                new int[]{0xD9A97F, 0xC99A72, 0xA9714B}, new int[]{0x2B1D14, 0x4A3524, 0x1A1410},
                new int[]{0xE8E2D2, 0x8E3B3B, 0x3F4F7A, 0xC8A06A, 0xA85A2F, 0x5F6B4A, 0x7A3F5F, 0xD9C8A8, 0x4A5A6A, 0x8A7A5F, 0x5F2F5A, 0x4A6A8A, 0xB0452F, 0xE8C86A, 0x6B4A2F, 0x7A8A5A, 0x5A5A62, 0x8A2F3A, 0xF0ECE0, 0xC08A4A}, 0xC9A227,
                new String[]{"short", "curly", "short_beard", "long"},
                new String[]{"bun", "braid", "loose", "wrapped", "long"},
                Map.of("farmer", List.of("crew_tee"), "artisan", List.of("crew_tee"),
                        "merchant", List.of("button_down"), "scholar", List.of("button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee"),
                        "advisor", List.of("blazer"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee")),
                Map.of("soldier", List.of("uniform_green"), "guard", List.of("uniform_green"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the field uniform", "guard", "the same field uniform",
                        "priest", "the temple's vestment"),
                "tunic", "skirt", "earring",
                era(0, 700, 1450, 1750, 1945, 1.00, 0.50, 0.15, 0.05, 0.0, 2, 2), List.of(), "rome"));

        put("north_african", new Pool(
                new int[]{0xB57F55, 0xA9714B, 0x8D5A39, 0xC08A5E}, new int[]{0x141010, 0x2B1D14, 0x3A2A1C},
                new int[]{0xE0D3A8, 0x2F6EA8, 0x8E5A3B, 0x3F6B5A, 0x7A2F3F, 0xC8873F, 0x4A4A6A, 0xA83F5F, 0x6B8A9A, 0xD9C08A, 0x5A3F2F, 0x8A6BA8, 0x2F5A7A, 0xC88A3A, 0x7A3A4A, 0xEFE4C8}, 0xD9B25A,
                new String[]{"short_beard", "wrapped_head", "short", "long"},
                new String[]{"wrapped", "braid", "bun", "loose", "long"},
                Map.of("farmer", List.of("crew_tee"), "artisan", List.of("crew_tee"),
                        "merchant", List.of("button_down"), "scholar", List.of("button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee"),
                        "advisor", List.of("button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee")),
                Map.of("soldier", List.of("uniform_khaki"), "guard", List.of("uniform_khaki"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the regiment's khaki", "guard", "the same cloth as the soldier",
                        "priest", "the mosque's plain robe"),
                "wrap", "wrap", "earring_veil",
                era(0, 1000, 1500, 1880, 1950, 1.20, 0.75, 0.65, 0.50, 0.80, 1, 3), List.of(), "north_african"));

        put("african", new Pool(
                new int[]{0x6F4526, 0x8D5A39, 0x4A2C18, 0xA9714B}, new int[]{0x141010, 0x1A1410},
                new int[]{0xD9A13A, 0x8E3B57, 0x3F6B5A, 0xC8452F, 0x2F4F7A, 0xE8B84A, 0x7A2F2F, 0x3F7A5F, 0xA85A2F, 0x5F3F6A, 0xC9D9A0, 0x8A4A2F, 0x2F6B8A, 0x3A6B3A, 0xC85A2A, 0xE8C85A, 0x4A3A6A}, 0xD9B25A,
                new String[]{"curly", "short", "short_beard", "long"},
                new String[]{"braid", "loose", "wrapped", "bun", "long"},
                Map.of("farmer", List.of("crew_tee", "tank"), "artisan", List.of("crew_tee", "graphic_tee"),
                        "merchant", List.of("polo", "button_down"), "scholar", List.of("button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee", "tank"),
                        "advisor", List.of("button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee", "tank")),
                Map.of("soldier", List.of("uniform_green"), "guard", List.of("uniform_green"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the field uniform", "guard", "the same field uniform",
                        "priest", "the church's vestment"),
                "wrapper", "wrapper", "earring_bangle",
                era(0, 900, 1500, 1900, 1960, 0.90, 0.90, 0.45, 0.45, 0.60, 2, 2), List.of(), "african"));

        put("east_asian", new Pool(
                new int[]{0xE8C9A0, 0xD9B98A, 0xC8A06A}, new int[]{0x141010, 0x1A1410, 0x2B1D14},
                new int[]{0x2F4F7A, 0x8E3B3B, 0xE8E2D2, 0x3F6B5A, 0x4A4436, 0xC8A06A, 0x5F7A8A, 0xA85A4A, 0xD9D0B8, 0x2F6B5F, 0x7A4A6A, 0x8A8A6A, 0x8A2F3A, 0x2F5A6B, 0xD8C8A0, 0x5A7A4A}, 0xC9A227,
                new String[]{"bun", "short", "long", "short_beard"},
                new String[]{"bun", "loose", "braid", "plait_over", "long"},
                Map.of("farmer", List.of("crew_tee", "shorts"), "artisan", List.of("crew_tee"),
                        "merchant", List.of("button_down", "polo"), "scholar", List.of("button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee"),
                        "advisor", List.of("blazer"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee")),
                Map.of("soldier", List.of("uniform_green"), "guard", List.of("uniform_green"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the field uniform", "guard", "the same field uniform",
                        "priest", "the temple's vestment"),
                "robe", "robe", "earring",
                era(0, 900, 1450, 1900, 1912, 1.30, 0.40, 0.10, 0.10, 0.15, 2, 2), List.of(), "east_asian"));

        put("americas", new Pool(
                new int[]{0xA9714B, 0x8D5A39, 0xC08A5E, 0x6F4526}, new int[]{0x141010, 0x1A1410, 0x2B1D14},
                new int[]{0xC8A06A, 0x8E5A3B, 0x7D3F3F, 0x3F6B5A, 0xD9A13A, 0xA83F4A, 0x2F5F8A, 0xE0C89A, 0x6B4A6A, 0x8A7A3F, 0xC85A2F, 0x4A3F2F, 0x8A2F2F, 0x3A5A8A, 0xD8A83A, 0x4A6A4A}, 0xC9A227,
                new String[]{"long", "short", "braid", "short_beard"},
                new String[]{"braid", "loose", "wrapped", "bun", "long"},
                Map.of("farmer", List.of("crew_tee", "tank"), "artisan", List.of("crew_tee", "graphic_tee"),
                        "merchant", List.of("button_down"), "scholar", List.of("button_down"),
                        "healer", List.of("v_neck_tee"), "citizen", List.of("crew_tee", "tank"),
                        "advisor", List.of("button_down"), "scribe", List.of("button_down"),
                        "villager", List.of("crew_tee", "tank")),
                Map.of("soldier", List.of("uniform_khaki"), "guard", List.of("uniform_khaki"),
                        "priest", List.of("vestment")),
                Map.of("soldier", "the regiment's khaki", "guard", "the same cloth as the soldier",
                        "priest", "the church's vestment"),
                "wrapper", "wrapper", "earring",
                era(0, 900, 1450, 1520, 1950, 0.80, 0.70, 0.25, 0.20, 0.40, 2, 2), List.of(), "americas"));
    }

    /** The twelve turning points, in the order the Python declares them. */
    private static Map<String, Integer> era(int classical, int imperial, int early, int colonial,
                                            int modern, double robe, double wrap, double drape,
                                            double keepHat, double revival, int armEndM, int armEndF) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("classical", classical);
        m.put("imperial", imperial);
        m.put("early", early);
        m.put("colonial", colonial);
        m.put("modern", modern);
        m.put("robe", (int) Math.round(robe * 100));
        m.put("wrap", (int) Math.round(wrap * 100));
        m.put("drape", (int) Math.round(drape * 100));
        m.put("keep_hat", (int) Math.round(keepHat * 100));
        m.put("revival", (int) Math.round(revival * 100));
        m.put("arm_end_m", armEndM);
        m.put("arm_end_f", armEndF);
        return m;
    }
}

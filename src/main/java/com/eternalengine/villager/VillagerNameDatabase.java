package com.eternalengine.villager;

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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Region/civilization name foundation for villager agents.
 *
 * <h3>What was wrong</h3>
 * Two things, and together they are the whole of "Villager Alex Stone".
 * <ol>
 *   <li>{@code names/foundation_names.json} was installed into the world by
 *       {@code FoundationDataInstaller} and <b>never read by anything</b>. The only pools that
 *       existed were the two hardcoded here: {@code rome} and {@code maurya}.</li>
 *   <li>Every other civ id — {@code india}, {@code hyderabad_state}, {@code british_india},
 *       {@code witness}, all 103 the corpus cites — fell through to {@code global}, whose pool is
 *       Alex, Sam, Stone, River and the title Villager. Hence the log line
 *       {@code <Villager Alex Stone>}.</li>
 * </ol>
 *
 * <h3>What it does now</h3>
 * The pack is loaded at server start, from the world folder with the jar as fallback, the same way
 * the population pack and the civilization table are. On top of that, a civ with no pool of its own
 * resolves through a <b>culture group</b> ({@link #cultureFor}) rather than straight to the generic
 * pool, so an unknown civ still gets names from its region instead of from nowhere. The Deccan pool
 * is written out in full because that is the crowd this project exists for: Hyderabad, 1724–1948.
 *
 * <p>Names and occupational titles here are common names of the region and period — illustrative,
 * in the same sense the population ladder is: derived and labelled, not quoted from a census table.
 *
 * <p>The generic pool remains the last resort, and is now reachable only by an id that is not a
 * civ at all.
 */
public final class VillagerNameDatabase {

    /** Where the shipped pool lives inside the jar. */
    private static final String PACKAGED_NAMES = "/data/eternalengine/eternal/names/foundation_names.json";

    /** Used when a civ has no pool and no culture could be worked out. */
    public static final String GENERIC = "global";

    private final ConcurrentHashMap<String, NameData> regionNames = new ConcurrentHashMap<>();

    /**
     * The place-true register (Permission 04): names by the language spoken where the person
     * stands, era-bound, sex-filtered. Loaded from the same world folder, jar as fallback,
     * and answering in front of the legacy pools whenever it has an answer for the place.
     */
    private final PlaceNameResolver placeResolver = new PlaceNameResolver();

    public VillagerNameDatabase() {
        registerDefaults();
    }

    // ==================== loading ====================

    /**
     * Read the pool from a world folder, falling back to the copy in the jar.
     *
     * @return how many regions were loaded, 0 if neither copy could be read
     */
    public int loadFrom(Path dir) {
        // The place-true register is a separate pack; it loads regardless of the foundation pack's
        // fate, and falls back to its copy in the jar exactly like this one.
        placeResolver.loadFrom(dir);
        JsonObject root = null;
        Path file = dir == null ? null : dir.resolve("foundation_names.json");
        if (file != null && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                EternalEngineMod.getLogger().warn(
                        "[VillagerNameDatabase] World names pack unreadable ({}); using the packaged copy",
                        e.getMessage());
            }
        }
        if (root == null) {
            try (InputStream in = VillagerNameDatabase.class.getResourceAsStream(PACKAGED_NAMES)) {
                if (in == null) {
                    EternalEngineMod.getLogger().warn(
                            "[VillagerNameDatabase] No names pack at {} — built-in pools only", PACKAGED_NAMES);
                    return 0;
                }
                root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                EternalEngineMod.getLogger().error(
                        "[VillagerNameDatabase] Failed to read the packaged names pack: {}", e.getMessage());
                return 0;
            }
        }
        int loaded = loadJson(root);
        EternalEngineMod.getLogger().info(
                "[VillagerNameDatabase] Loaded {} named region(s), {} in total", loaded, regionNames.size());
        return loaded;
    }

    /** Read a pack in the shape the shipped file uses: one object per region. */
    public int loadJson(JsonObject root) {
        if (root == null) {
            return 0;
        }
        int loaded = 0;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            String region = entry.getKey();
            if (region.startsWith("_")) {
                continue;
            }
            JsonObject o = entry.getValue().getAsJsonObject();
            List<String> male = strings(o, "firstNamesMale");
            List<String> female = strings(o, "firstNamesFemale");
            List<String> surnames = strings(o, "surnames");
            if (male.isEmpty() && female.isEmpty() && surnames.isEmpty()) {
                continue;
            }
            Map<String, List<String>> titles = new LinkedHashMap<>();
            if (o.has("rolesTitles") && o.get("rolesTitles").isJsonObject()) {
                for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("rolesTitles").entrySet()) {
                    titles.put(t.getKey().toLowerCase(Locale.ROOT), strings(t.getValue()));
                }
            }
            putRegion(region.toLowerCase(Locale.ROOT),
                    new NameData(male, female.isEmpty() ? male : female, surnames, titles));
            loaded++;
        }
        return loaded;
    }

    private static List<String> strings(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) {
            return List.of();
        }
        return strings(o.getAsJsonArray(key));
    }

    /** One malformed entry is skipped rather than losing the whole pool. */
    private static List<String> strings(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray arr = element.getAsJsonArray();
        List<String> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            try {
                String s = el.getAsString();
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            } catch (Exception ignored) {
                // Skip it.
            }
        }
        return out;
    }

    // ==================== lookup ====================

    public void putRegion(String regionId, NameData data) {
        if (regionId != null && data != null) {
            regionNames.put(regionId.toLowerCase(Locale.ROOT), data);
        }
    }

    /**
     * Which pool this id draws from: its own region first, then its culture group, then generic.
     * Exposed so a test can assert that no civ in the corpus lands on {@code global}.
     */
    /**
     * The name list for a person standing at a place (step 17): the language people speak there, from 1500 on
     * (Glottolog; the same answer as the talk's "People here speak Bengali"), when one of the lists fits that
     * language; otherwise the culture of the state that rules the place ({@link #poolFor}). Until step 17 only the
     * ruling state counted: Calcutta under British India got Telugu names, and a state no rule knew got the
     * placeholder list ("Alex Stone").
     */
    public String poolForPlace(double lat, double lon, int year, String civId) {
        try {
            var hit = com.eternalengine.civilization.LocalLanguages.get().peoplesLanguage(lat, lon, year);
            if (hit.isPresent()) {
                String pool = poolForLanguage(hit.get().language().name());
                if (pool != null && regionNames.containsKey(pool)) {
                    return pool;
                }
            }
        } catch (Throwable ignored) {
            // no language data: the ruling state decides, as before
        }
        return poolFor(civId);
    }

    /**
     * Which of the name lists fits a language, or null when none does (then the ruling state decides). The lists
     * are few: the Deccan's (Telugu, Deccani, Marathi and the southern languages), the rest of South Asia's, East
     * Asia's, the Persian and Turkic world's, the Mediterranean's, Africa's and the Americas'.
     */
    public static String poolForLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String l = language.toLowerCase(Locale.ROOT);
        if (containsAny(l, "telugu", "kannada", "dakhini", "deccan", "tamil", "malayalam", "tulu", "gondi",
                "kodava", "badaga")) {
            return "deccan";
        }
        // Marathi and Konkani are not Telugu: the Deccan list answers a Marathi speaker in Bombay with
        // "Ryot Mallaiah Jung" and "Sri Laxmaiah Raju". The other South Asian list carries the Marathi
        // surnames the sample should have (Shinde, Jadhav, Deshmukh) and is the nearer of the two.
        if (containsAny(l, "marathi", "konkani", "hindi", "bengali", "bangla", "panjabi", "punjabi", "gujarati", "oriya", "odia", "assamese",
                "nepali", "sindhi", "urdu", "bhojpuri", "maithili", "magahi", "awadhi", "rajasthani", "marwari",
                "kashmiri", "sinhala", "chhattisgarhi", "haryanvi", "bundeli", "malvi", "garhwali", "kumaoni", "dogri",
                "sylheti", "chittagonian", "bagri", "mewari", "dhundari", "hadothi", "bhili", "kangri", "sadri")) {
            return "indic";
        }
        if (containsAny(l, "mandarin", "chinese", "cantonese", "yue", "hakka", "japanese", "korean", "vietnamese")) {
            return "east_asian";
        }
        if (containsAny(l, "persian", "farsi", "dari", "pashto", "tajik", "turkish", "azerbaijani", "azeri", "uzbek",
                "turkmen", "kazakh", "kirghiz", "kyrgyz", "uighur", "uyghur", "kurdish", "kurmanji", "sorani",
                "baluchi", "balochi")) {
            return "persianate";
        }
        if (containsAny(l, "greek", "italian", "sicilian", "neapolitan")) {
            return "mediterranean";
        }
        if (containsAny(l, "swahili", "yoruba", "hausa", "igbo", "zulu", "xhosa", "akan", "twi", "wolof", "fula",
                "pulaar", "lingala", "kongo", "shona", "amharic", "oromo", "somali", "tigrinya", "bambara", "mandinka",
                "ewe", "fon", "kikuyu", "luganda", "ganda", "sotho", "tswana", "malagasy")) {
            return "african";
        }
        if (containsAny(l, "nahuatl", "quechua", "aymara", "maya", "yucatec", "k'iche", "kiche", "guarani", "mapudungun",
                "zapotec", "mixtec", "otomi", "tzotzil", "tzeltal")) {
            return "americas";
        }
        return null;
    }

    public String poolFor(String id) {
        if (id == null || id.isBlank()) {
            return GENERIC;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (regionNames.containsKey(key)) {
            return key;
        }
        String culture = cultureFor(key);
        if (regionNames.containsKey(culture)) {
            return culture;
        }
        return GENERIC;
    }

    public String getRandomName(String region, String era, String role, Random random) {
        return getRandomName(region, era, role, false, random);
    }

    /**
     * A name in the form the original used — title, first name, surname — with the title omitted
     * when the pool has no word for the role. {@code Ryot Ramaiah Reddy} or {@code Zuri... } no:
     * {@code Ramaiah Reddy}. "Villager Alex Stone" is what this is here to stop producing.
     */
    public String getRandomName(String region, String era, String role, boolean female, Random random) {
        Random rng = random == null ? new Random() : random;
        NameData data = regionNames.getOrDefault(poolFor(region), NameData.global());
        List<String> firsts = female && !data.firstNamesFemale().isEmpty()
                ? data.firstNamesFemale() : data.firstNamesMale();
        String first = choose(firsts, rng, "Alex");
        String surname = choose(data.surnames(), rng, "Worker");
        String title = chooseTitle(data, role, rng);
        return title == null || title.isBlank() ? first + " " + surname : title + " " + first + " " + surname;
    }

    /**
     * A name from the place-true register (Permission 04): the language spoken where the person
     * stands, era- and sex-bound, with the pool's own occupational word in front when the pool
     * has one ({@code Ryot Vivek Mudaliar}, not just {@code Vivek Mudaliar}). The register's
     * answer always goes in front of the legacy pools; when it has none for the place it says so
     * (returns null) and the caller falls back to {@link #getRandomName}.
     *
     * @param used bare names (given + surname, no title) already spoken in this village, to keep
     *             the names distinct; the chosen name is added to it. May be null.
     * @return the name, title included where the pool has one; null when the register cannot
     *         answer for the place
     */
    public String getRandomPlaceName(String pool, double lat, double lon, int year, String role,
                                     boolean female, Random random, Set<String> used) {
        if (!placeResolver.isLoaded()) {
            return null;
        }
        Random rng = random == null ? new Random() : random;
        String bare = placeResolver.nameFor(lat, lon, year, female ? "f" : "m", rng, used);
        if (bare == null) {
            return null;
        }
        if (used != null) {
            used.add(bare);
        }
        NameData data = regionNames.getOrDefault(poolFor(pool), NameData.global());
        String title = chooseTitle(data, role, rng);
        return title == null || title.isBlank() ? bare : title + " " + bare;
    }

    public String getRandomPlaceName(String pool, double lat, double lon, int year, String role,
                                     boolean female, Random random) {
        return getRandomPlaceName(pool, lat, lon, year, role, female, random, null);
    }

    /**
     * The occupational word this pool has for this role, in the same order the legacy path used:
     * the role's own titles, then the merchant's for the trading roles, then the citizen's.
     * Null when the pool has no word for the role at all.
     */
    private String chooseTitle(NameData data, String role, Random rng) {
        String roleKey = role == null || role.isBlank() ? "villager" : role.toLowerCase(Locale.ROOT);
        List<String> titles = data.rolesTitles().get(roleKey);
        if (titles == null && ("money_changer".equals(roleKey) || "paper_seller".equals(roleKey)
                || "trader".equals(roleKey) || "pedlar".equals(roleKey))) {
            titles = data.rolesTitles().get("merchant"); // step 19b/19d/19g: titled like a merchant
        }
        if (titles == null) {
            titles = data.rolesTitles().get("citizen");
        }
        return choose(titles, rng, null);
    }

    /** Whether the place-true register is loaded and answering. */
    public boolean placeNamesLoaded() {
        return placeResolver.isLoaded();
    }

    /** The register, exposed for the "why this name" answer and for tests. */
    public PlaceNameResolver placeResolver() {
        return placeResolver;
    }

    public String getRandomFirstName(String region) {
        return choose(regionNames.getOrDefault(poolFor(region), NameData.global()).firstNamesMale(),
                new Random(), "Alex");
    }

    public String getRandomSurname(String region) {
        return choose(regionNames.getOrDefault(poolFor(region), NameData.global()).surnames(),
                new Random(), "Worker");
    }

    public int regionCount() {
        return regionNames.size();
    }

    /** True when this id resolves to a real pool rather than the generic fallback. */
    public boolean hasPool(String id) {
        return !GENERIC.equals(poolFor(id));
    }

    private String choose(List<String> values, Random random, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.get(Math.floorMod(random.nextInt(), values.size()));
    }

    // ==================== culture resolution ====================

    /**
     * The culture group an unknown civ id belongs to, by the shape of its name.
     *
     * <p>Coarse on purpose. There are 191 civs and eleven pools; a civ getting the right region's
     * names is worth far more than a pool per dynasty, and a wrong-but-neighbouring pool is still
     * better than {@code global}.
     */
    public static String cultureFor(String civId) {
        if (civId == null || civId.isBlank()) {
            return GENERIC;
        }
        String id = civId.toLowerCase(Locale.ROOT);

        // Not "india": until step 17 every state with "india" in its id (british_india, republic_of_india) got
        // the Deccan's Telugu names, also in Calcutta and Delhi. India as a whole goes to the indic list below.
        if (containsAny(id, "hyderabad", "deccan", "golconda", "qutb", "bahmani", "kakatiya",
                "chalukya", "rashtrakuta", "satavahana", "vijayanagara", "hoysala", "kadamba",
                "kakani", "andhra", "telangana", "telugu", "mysore", "wodeyar", "akkanna")) {
            return "deccan";
        }
        if (containsAny(id, "india", "british_raj", "east_india", "maurya", "gupta", "mughal", "maratha", "delhi",
                "rajput", "sikh", "chola",
                "chera", "pandya", "pallava", "ganga", "sunga", "kushan", "nanda", "harsha", "gurjara",
                "ahir", "mewar", "jaipur", "indus", "harappa", "vedic", "aryan",
                // the northern and eastern dynasties, and the states that followed them
                "magadha", "pala", "vajji", "vatsa", "kosala", "kalinga", "ikshvaku", "yadava",
                "gujarat", "chaulukya", "malwa", "jaunpur", "bengal", "awadh", "bundela", "holkar",
                "bhopal", "amber", "marwar", "bhati", "tomara", "somavamshi", "kamarupa", "ahom",
                "manipur", "karkota", "dogra", "ladakh", "nepal",
                // Lanka and the southern coasts
                "anuradhapura", "polonnaruwa", "kandy", "jaffna", "sinhala", "ceylon", "zamorin",
                "travancore", "kolathiri", "malayali", "talpur", "samma", "sindh",
                // world ruler register (Cliopatria) polity names
                "shunga", "vakataka", "tughlaq", "lodi_", "sayyid_dynasty", "khalji", "pratihara",
                "kashmir", "sind_", "pakistan", "bangladesh", "indo_scythian", "western_satraps",
                "kanva", "kalachuri", "paramara", "sena_dynasty", "suri_empire", "durrani_empire_of_india")) {
            return "indic";
        }
        if (containsAny(id, "persia", "sassanid", "achaemenid", "safavid", "timurid", "ottoman",
                "seljuk", "abbasid", "umayyad", "fatimid", "bahri", "mamluk", "shah", "afghan",
                "durrani", "khanate", "bukhara", "khiva", "samarkand", "central_asian",
                // world ruler register (Cliopatria) polity names
                "sasanian", "parthia", "median_kingdom", "samanid", "ghaznavid", "ghurid", "khwarezm",
                "buyid", "qajar", "pahlavi", "iran", "afsharid", "zand_dynasty", "qoyunlu", "ilkhan",
                "moghulistan", "chagatai", "horde", "kipchak", "kimek", "nogai", "kazakh", "uzbek",
                "shaybanid", "turkey", "turkmen", "gokturk", "turgesh", "oghuz", "khazar", "uyghur",
                "kyrgyz", "rashidun", "hamdanid", "ayyubid", "rasulid", "hadhramaut", "saudi", "arabia",
                "lakhmid", "ghassanid", "himyar", "yemen", "sultanate_of_oman", "scythia", "sarmatia",
                "yuezhi", "hephthal", "huns", "avar_khaganate", "indo_sasanian", "tahirid", "saffarid",
                "kara_khanid", "qarakhanid", "zengid", "artuqid", "danishmend", "rum_sultanate")) {
            return "persianate";
        }
        // Names that hold an East Asian needle by accident - "song" in Songhai, "han" in Ghana,
        // "tang" in Tanganyika, "viet" in Soviet - are routed before that block can see them.
        if (containsAny(id, "songhai", "ghana", "tanganyika")) {
            return "african";
        }
        if (containsAny(id, "soviet")) {
            return "mediterranean";
        }
        if (containsAny(id, "china", "chinese", "han", "qin", "tang", "song", "ming", "qing", "zhou",
                "shang", "yuan", "korea", "japan", "yamato", "vietnam", "mongol", "xiongnu",
                // world ruler register (Cliopatria) polity names
                "xia", "jin_", "_jin", "liao", "sui_dynasty", "northern_wei", "eastern_wei",
                "western_wei", "cao_wei", "liang_dynasty", "southern_qi", "northern_qi", "xin_dynasty",
                "eastern_wu", "shu_han", "chu", "goguryeo", "joseon", "balhae", "silla", "baekje",
                "goryeo", "tokugawa", "shogun", "ashikaga", "dali", "nanzhao", "tibet", "khitan",
                "oirat", "tumed", "xianbei", "rouran", "tuyuhun", "manchu", "jurchen", "dai_viet", "annam",
                "protectorate_of_the_western_regions", "qocho", "tangut")) {
            return "east_asian";
        }
        if (containsAny(id, "rome", "roman", "byzant", "greek", "greece", "athens", "sparta", "macedon",
                "hellen", "carthage", "phoenic", "etrusc", "illyria", "gaul", "iberia", "ital",
                "venice", "genoa", "portugal", "spain", "france", "england", "british", "dutch",
                "europe", "norman", "viking", "celtic", "poland", "hungary",
                "minoan", "mycenae", "aegean", "crete", "seleucid", "ptolemaic", "portuguese",
                // world ruler register (Cliopatria) polity names; this is the European pool, so the
                // European colonial and successor states of the Americas and Australia use it too
                "russia", "soviet", "moscow", "muscovy", "novgorod", "kievan", "tsardom", "vladimir",
                "denmark", "danish", "norway", "norweg", "sweden", "swedish", "kalmar", "finland",
                "iceland", "greenland", "frank", "visigoth", "ostrogoth", "gothia", "lombard",
                "burgund", "habsburg", "jagiellon", "lithuania", "bulgaria", "serbia", "croatia",
                "bohemia", "austria", "prussia", "bavaria", "saxony", "castile", "aragon", "navarre",
                "leon", "spanish", "scotland", "ireland", "wales", "netherland", "belgi", "swiss",
                "switzerland", "german", "holy_roman", "armenia", "georgia", "greco", "romania",
                "wallachia", "moldavia", "galicia", "halych", "volhynia", "teutonic", "livonia",
                "canada", "australia", "new_zealand", "united_states", "confederate_states", "brazil",
                "mexico", "argentin", "chile", "peru", "colombia", "venezuela", "bolivia", "ecuador",
                "uruguay", "paraguay", "cuba", "guatemala", "estado_novo", "napoleon", "sicily",
                "sardinia", "savoy", "papal", "milan", "tuscany", "kingdom_of_italy", "yugoslav",
                "czech", "slovak", "estonia", "latvia", "ukrain", "belarus", "byelorussia")) {
            return "mediterranean";
        }
        if (containsAny(id, "egypt", "nubia", "kush", "berber", "morocco", "tunis", "libya", "carthag",
                "numidia", "copt",
                // world ruler register (Cliopatria) polity names
                "almoravid", "almohad", "hafsid", "wattasid", "marinid", "zayyanid", "saadi", "alawi",
                "algeria", "mauretania", "makuria", "alodia", "nobatia", "funj", "sudan",
                "muhammad_ali", "aghlabid", "idrisid", "rustamid", "tulunid", "ikhshidid")) {
            return "north_african";
        }
        if (containsAny(id, "mali", "songhai", "ghana", "axum", "ethiopia", "swahili", "zulu", "kongo",
                "benin", "yoruba", "hausa", "kanem", "bornu", "nok", "africa", "somali", "kushite",
                "zimbabwe", "mutapa", "monomotapa", "buganda", "dahomey",
                // world ruler register (Cliopatria) polity names
                "congo", "wadai", "darfur", "chad", "sokoto", "oyo", "asante", "zanzibar", "rwanda",
                "abyssinia", "adal", "ajuran", "kilwa", "bunyoro", "luba", "lunda", "kuba", "mossi",
                "bamana", "ndongo", "merina", "madagascar", "nigeria", "ghana", "kenya", "tanzania",
                "uganda", "mozambique", "angola", "zambia", "namibia", "botswana", "lesotho", "eswatini",
                "south_africa", "orange_free_state", "transvaal", "liberia", "sierra_leone", "senegal",
                "niger", "jolof", "kaabu", "fula", "macina", "tukulor", "kongo")) {
            return "african";
        }
        if (containsAny(id, "aztec", "maya", "inca", "olmec", "zapotec", "toltec", "iroquois",
                "cherokee", "navajo", "sioux", "hopi", "pueblo", "inuit", "mississip", "cahokia",
                "america", "maori", "hawaii", "polynesian", "nan_madol",
                "teotihuacan", "toltec", "mixtec", "caral", "chimu", "muisca")) {
            return "americas";
        }
        if (containsAny(id, "sumer", "akkad", "babylon", "assyria", "mesopotam", "hittite", "elam",
                "urartu", "phoenic", "ugarit", "canaan", "hebrew", "israel", "judah", "aramaic")) {
            return "mesopotamian";
        }
        // Turn 2026-10-24 (R2-d): the keyword lists above are a fixed table and the register is not.
        // Measured on the shipped data, 818 of 1,567 polities fell past every list into the generic
        // pool, so a town in Ahmadnagar was peopled by "Villager Alex Stone". The last step is now
        // WHERE the polity ruled: the register records the centre of the cells it held, and
        // PlaceNamePools turns that into one of these same eleven pools. It runs only when every
        // keyword has already failed, so an id that was named correctly yesterday is named the same
        // today.
        String byPlace = PlaceNamePools.cultureFor(id);
        if (byPlace != null && !GENERIC.equals(byPlace)) {
            return byPlace;
        }
        return GENERIC;
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n)) {
                return true;
            }
        }
        return false;
    }

    // ==================== built-in pools ====================

    private void registerDefaults() {
        putRegion(GENERIC, NameData.global());

        // ---- the Deccan: Hyderabad and the sultanates that preceded it. Telugu and
        // Perso-Deccani names, and the occupational words the period used: a cultivator was a
        // ryot, a trader a vepari, a soldier a sipahi, a scribe a munshi. ----
        putRegion("deccan", new NameData(
            List.of("Ramaiah", "Venkataiah", "Yellaiah", "Mallaiah", "Narsimha", "Kishtaiah",
                    "Rajanna", "Anjaiah", "Balaiah", "Pentaiah", "Laxmaiah", "Bhadraiah",
                    "Chandraiah", "Komuraiah", "Muthaiah", "Sailu", "Ahmed", "Mohammed", "Ali",
                    "Hasan", "Hussain", "Mirza", "Yaqub", "Abdul", "Karim", "Iqbal", "Rahmat"),
            List.of("Lakshmamma", "Yellamma", "Padmamma", "Satyamma", "Buchamma", "Rangamma",
                    "Anasuyamma", "Manemma", "Kistamma", "Rajamma", "Nagamma", "Santhamma",
                    "Bhagyamma", "Fatima", "Ayesha", "Zainab", "Khadija", "Sultana", "Nasreen",
                    "Shabana", "Rahimunnisa"),
            List.of("Reddy", "Gowd", "Goud", "Naidu", "Rao", "Raju", "Yadav", "Mudiraj", "Sharma",
                    "Shastri", "Deshmukh", "Pawar", "Jadhav", "Kulkarni", "Iyer", "Iyengar", "Nair",
                    "Khan", "Hussain", "Ansari", "Qureshi", "Siddiqui", "Baig", "Mirza", "Pasha",
                    "Jung", "Vepari", "Setty"),
            Map.of("farmer", List.of("Ryot"),
                   "artisan", List.of("Shilpi"),
                   "merchant", List.of("Vepari", "Setty"),
                   "priest", List.of("Purohit"),
                   "soldier", List.of("Sipahi"),
                   "guard", List.of("Sipahi"),
                   "citizen", List.of("Sri"),
                   "scholar", List.of("Pandit"),
                   "villager", List.of("Sri"))
        ));

        // ---- north and west India: the Mauryan-to-Maratha line ----
        putRegion("indic", new NameData(
            List.of("Arjun", "Ravi", "Suresh", "Ram", "Shyam", "Vijay", "Bhaskar", "Dinesh", "Ganesh",
                    "Karan", "Mohan", "Narayan", "Prakash", "Rajesh", "Vikram", "Baji", "Tanaji",
                    "Hari", "Devraj"),
            List.of("Meera", "Radha", "Sita", "Gita", "Anita", "Sunita", "Kamla", "Shanti", "Lakshmi",
                    "Parvati", "Durga", "Savitri", "Indira", "Tara", "Uma"),
            List.of("Sharma", "Verma", "Gupta", "Singh", "Patil", "Deshmukh", "Joshi", "Kulkarni",
                    "Shinde", "Pawar", "More", "Jadhav", "Chavan", "Rao", "Yadav", "Mishra", "Trivedi",
                    "Chauhan", "Rathore"),
            Map.of("farmer", List.of("Kisan"),
                   "merchant", List.of("Vyapari"),
                   "soldier", List.of("Sipahi"),
                   "guard", List.of("Sipahi"),
                   "priest", List.of("Pandit"),
                   "scholar", List.of("Pandit"),
                   "artisan", List.of("Karigar"),
                   "citizen", List.of("Shri"),
                   "villager", List.of("Shri"))
        ));

        // ---- the Persianate world: Delhi, the Mughals, the Deccan sultanates' courts ----
        putRegion("persianate", new NameData(
            List.of("Akbar", "Aurangzeb", "Babur", "Humayun", "Jahangir", "Farrukh", "Asaf", "Nizam",
                    "Salar", "Rahim", "Yusuf", "Ismail", "Tahir", "Arslan", "Bahram"),
            List.of("Nur", "Jahanara", "Roshanara", "Mumtaz", "Gulbadan", "Zebunissa", "Chand",
                    "Mah", "Gulnar", "Shirin"),
            List.of("Khan", "Beg", "Mirza", "Jung", "Bakht", "Yar", "Quraishi", "Turani", "Shirazi",
                    "Ansari", "Siddiqui", "Pasha", "Baig"),
            Map.of("farmer", List.of("Rayat"),
                   "merchant", List.of("Saudagar"),
                   "artisan", List.of("Karigar"),
                   "scholar", List.of("Munshi"),
                   "priest", List.of("Imam"),
                   "soldier", List.of("Sipahi"),
                   "guard", List.of("Sipahi"),
                   "citizen", List.of("Mir"),
                   "villager", List.of("Mir"))
        ));

        // ---- Mesopotamia and the Levant ----
        putRegion("mesopotamian", new NameData(
            List.of("Enlil", "Shamash", "Nabu", "Sin", "Adad", "Ur-Nammu", "Sargon", "Hammurabi",
                    "Ashur", "Nebuchadnezzar", "Tiglath", "Esarhaddon"),
            List.of("Ishtar", "Inanna", "Ninsun", "Adad-guppi", "Sammuramat", "Naqia", "Zakutu"),
            List.of("of Ur", "of Akkad", "of Babylon", "of Nineveh", "bar Sin", "ben Adad", "of Lagash"),
            Map.of("farmer", List.of("Farmer"), "merchant", List.of("Tamkarum"),
                   "priest", List.of("Sangamahhu"), "scribe", List.of("Dubsar"),
                   "artisan", List.of("Gugallu"), "citizen", List.of("Citizen"))
        ));

        putRegion("mediterranean", new NameData(
            List.of("Nikos", "Dimitrios", "Konstantinos", "Andreas", "Petros", "Marcus", "Lucius",
                    "Quintus", "Titus", "Aurelius", "Darius", "Xerxes", "Cyrus"),
            List.of("Eleni", "Maria", "Anna", "Sophia", "Aikaterina", "Athenais", "Theodora", "Livia"),
            List.of("Papadopoulos", "Nikolaou", "Georgiou", "Vasiliou", "Julius", "Cornelius",
                    "Aurelius", "Valerius", "Pahlavi"),
            Map.of("soldier", List.of("Legionary", "Hoplite"), "guard", List.of("Phylax"),
                   "merchant", List.of("Emporos"), "citizen", List.of("Polites"),
                   "farmer", List.of("Georgoi"), "scholar", List.of("Scholar"))
        ));

        putRegion("north_african", new NameData(
            List.of("Amenhotep", "Ramesses", "Thutmose", "Senusret", "Ptahhotep", "Khaemwaset",
                    "Amasis", "Taharqa", "Massinissa", "Juba"),
            List.of("Nefertari", "Hatshepsut", "Meritamun", "Nofret", "Iset", "Tiy", "Amanishakheto"),
            List.of("of Thebes", "of Memphis", "of Kush", "of Napata", "son of Ra"),
            Map.of("farmer", List.of("Sekhet"), "priest", List.of("Wab"),
                   "scribe", List.of("Sesh"), "artisan", List.of("Hemuty"),
                   "soldier", List.of("Masha"), "citizen", List.of("Citizen"))
        ));

        // ---- South-East Asia and the archipelago (added 2026-10-24, R2-d). The register names
        // ~100 polities from Ayutthaya and Angkor to Majapahit and Demak whose names matched no
        // pool, so their people were called "Villager Alex Stone". Coarse by design, like every
        // other pool here: the region's common names across the centuries, not a table per court. ----
        putRegion("southeast_asian", new NameData(
            List.of("Somchai", "Anurak", "Chaiya", "Suphan", "Narai", "Ramkhamhaeng", "Jayavarman",
                    "Suryavarman", "Indravarman", "Hayam", "Ken", "Arok", "Raden", "Sultan",
                    "Agung", "Trunojoyo", "Le", "Ly", "Tran", "Nguyen", "Suriyothai", "Thammaraja"),
            List.of("Sri", "Chandra", "Devi", "Kanya", "Ratna", "Malee", "Sumalee", "Sita", "Gita",
                    "Anisa", "Nur", "Ayu", "Putri", "Kesuma", "Lina"),
            List.of("Thep", "Ratcha", "Chai", "Phraya", "Varman", "Ahmat", "Syah", "Wijaya", "Kartawijaya",
                    "Dinata", "Susilo", "Nguyen", "Tran", "Pham", "Lim", "Tan"),
            Map.of("farmer", List.of("Rice grower"), "merchant", List.of("Trader"),
                   "soldier", List.of("Warrior"), "priest", List.of("Monk"),
                   "citizen", List.of("Citizen"))
        ));

        putRegion("african", new NameData(
            List.of("Kwame", "Kofi", "Yaw", "Musa", "Sundiata", "Askia", "Osei", "Shaka", "Senzangakhona",
                    "Nzinga", "Ewuare", "Oba"),
            List.of("Amina", "Nandi", "Yaa", "Makeda", "Zewditu", "Fatoumata", "Adaeze", "Mwanamwema"),
            List.of("Konate", "Traore", "Diallo", "Kone", "Osei", "Zulu", "Mensah", "Okafor", "Adeyemi"),
            Map.of("farmer", List.of("Farming man"), "merchant", List.of("Trader"),
                   "soldier", List.of("Warrior"), "priest", List.of("Elder"),
                   "citizen", List.of("Citizen"))
        ));

        putRegion("americas", new NameData(
            List.of("Cuauhtemoc", "Moctezuma", "Itzcoatl", "Ahuitzotl", "Pacal", "Jasaw", "Yax",
                    "Atahualpa", "Huayna", "Tupac", "Pachacuti", "Deganawida", "Hiawatha", "Nan Madol"),
            List.of("Tecuichpo", "Isabel", "Coya", "Chimpu", "Rauha", "Ahuiliztli", "Xochitl"),
            List.of("of Tenochtitlan", "of Cusco", "of Tikal", "of Palenque", "of the Longhouse"),
            Map.of("farmer", List.of("Milpa"), "priest", List.of("Tlamacazqui"),
                   "soldier", List.of("Yaqui"), "artisan", List.of("Toltecatl"),
                   "citizen", List.of("Citizen"))
        ));

        putRegion("east_asian", new NameData(
            List.of("Li Wei", "Wang", "Zhang", "Chen", "Liu", "Zhao", "Sun", "Zhou", "Wu", "Huang",
                    "Xu", "Ma", "Zhu", "Guo"),
            List.of("Mei", "Lan", "Xiu", "Ying", "Fang", "Hong", "Jing", "Yan", "Ling", "Hua"),
            List.of("Li", "Wang", "Zhang", "Liu", "Chen", "Yang", "Huang", "Zhao", "Wu", "Zhou", "Xu"),
            Map.of("farmer", List.of("Farmer"), "merchant", List.of("Merchant"),
                   "soldier", List.of("Soldier"), "scholar", List.of("Scholar"),
                   "citizen", List.of("Citizen"))
        ));

        // The two pools the original shipped, kept exactly as they were.
        putRegion("rome", new NameData(
            List.of("Gaius", "Marcus", "Lucius"),
            List.of("Julia", "Aurelia", "Cornelia"),
            List.of("Julius", "Cornelius", "Aurelius"),
            Map.of("soldier", List.of("Legionary", "Centurion"), "merchant", List.of("Trader"),
                   "advisor", List.of("Senator"), "villager", List.of("Citizen"))
        ));
        putRegion("maurya", new NameData(
            List.of("Chandra", "Bindu", "Ashoka"),
            List.of("Devi", "Kaurwaki", "Subhadrangi"),
            List.of("Maurya", "Gupta", "Sharma"),
            Map.of("soldier", List.of("Guard"), "merchant", List.of("Vanij"),
                   "advisor", List.of("Mantri"), "villager", List.of("Villager"))
        ));
    }

    public record NameData(List<String> firstNamesMale, List<String> firstNamesFemale,
                           List<String> surnames, Map<String, List<String>> rolesTitles) {
        public static NameData global() {
            return new NameData(List.of("Alex", "Sam"), List.of("Alex", "Sam"),
                List.of("Stone", "River"), Map.of("villager", List.of("Villager"), "advisor", List.of("Advisor")));
        }
    }
}

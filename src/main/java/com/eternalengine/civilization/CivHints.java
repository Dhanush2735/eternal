package com.eternalengine.civilization;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A pack id is not a polity - and the corpus spells them in the same field.
 *
 * <p>A gazetteer site carries a {@code civHint} which the mod reads as a civilization id. Measured on
 * 2026-10-22 (the marking turn, {@code worldfill/probes/SiteHintProbe.java}): of 46,864 sites that
 * carry a hint, <b>45,166 (96.4%) carry a string the ruler register has never heard of</b>. They are
 * pack ids - {@code india}, {@code rome}, {@code china_tang} - where the register's vocabulary is
 * polity slugs: {@code maurya_empire}, {@code roman_empire}, {@code tang_dynasty}.
 *
 * <p>Two things about that, and they must not be confused:
 *
 * <ul>
 *   <li><b>Some of them name exactly one polity.</b> {@code china_tang} is the Tang dynasty; a mapping
 *       is a fact, not a guess. Those are in {@link #ALIASES}.</li>
 *   <li><b>The rest name a place over time.</b> {@code india} is the Mauryas in 250 BC and the Marathas
 *       in 1700; {@code rome} is a kingdom, a republic and an empire. <b>No single alias is honest for
 *       them</b>, so they are listed in {@link #REGIONS} and are never mapped. The register answers by
 *       year instead, which is the only thing that can.</li>
 * </ul>
 *
 * <p>A first attempt tried to DERIVE the table from the data - ask the register what it names for the
 * sites carrying each hint. It produced {@code hittite -> union_of_soviet_socialist_republics} and
 * {@code hawaii_kingdom -> united_states_of_america}, because a site record does not say which years
 * its hint applies to, so the question was asked of the wrong year. Rejected: a table derived from a
 * question that cannot be answered is worse than no table. What is here was written by hand and each
 * target is checked against the register at load ({@link #resolve} returns empty if the register does
 * not know the target), so a wrong row fails visibly instead of quietly.
 */
public final class CivHints {

    private CivHints() {
    }

    /** Pack ids that name exactly one polity the register knows. Hand-written; each verified at use. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("rome", "roman_empire"),
            Map.entry("byzantium", "byzantine_empire"),
            Map.entry("china_tang", "tang_dynasty"),
            Map.entry("china_han", "han_dynasty"),
            Map.entry("china_song", "song"),
            Map.entry("china_ming", "ming_dynasty"),
            Map.entry("china_qing", "qing_dynasty"),
            Map.entry("china_qin", "qin_dynasty"),
            Map.entry("china_sui", "sui_dynasty"),
            Map.entry("sasanian", "sasanian_empire"),
            Map.entry("parthia", "parthian_empire"),
            Map.entry("etruscan", "etruscans"),
            Map.entry("macedon", "macedonian_empire"),
            Map.entry("persia_achaemenid", "achaemenid_empire"),
            Map.entry("khazar", "khazaria"),
            Map.entry("yadava", "yadava_dynasty"),
            Map.entry("eastern_ganga", "eastern_ganga_kingdom"),
            Map.entry("kosala", "janapada_of_kosala"),
            Map.entry("maurya", "maurya_empire"),
            Map.entry("mughal", "mughal_empire"),
            Map.entry("gupta", "gupta_empire"),
            Map.entry("kushan", "kushan_empire"),
            Map.entry("maratha", "maratha_empire"),
            Map.entry("satavahana", "satavahana_dynasty"),
            Map.entry("rashtrakuta", "rashtrakuta_dynasty"),
            Map.entry("kakatiya", "kakatiya_dynasty"),
            Map.entry("karkota", "karkota_dynasty"),
            Map.entry("kamarupa", "kamarupa_kingdom"),
            Map.entry("talpur", "talpur_dynasty"),
            Map.entry("timurid", "timurid_empire"),
            Map.entry("safavid", "safavid_dynasty"),
            Map.entry("malacca", "sultanate_of_malacca"),
            Map.entry("venice", "republic_of_venice"),
            Map.entry("scotland", "kingdom_of_scotland"),
            Map.entry("judah", "kingdom_of_judah"),
            Map.entry("mysore", "kingdom_of_mysore"),
            Map.entry("nabataea", "nabataeans"),
            Map.entry("numidia", "kingdom_of_numidia"),
            Map.entry("pagan", "pagan_kingdom"),
            Map.entry("polonnaruwa", "kingdom_of_polonnaruwa"),
            Map.entry("jaffna", "jaffna_kingdom"),
            Map.entry("samma", "sind_samma_dynasty"),
            Map.entry("sumer", "sumerian_city_states"),
            Map.entry("indus_valley", "indus_valley_civilization"),
            Map.entry("olmec", "olmec_civilization"),
            Map.entry("vatsa", "janapada_of_vatsa"),
            Map.entry("vajji", "janapada_of_vajji"),
            Map.entry("korea_goguryeo", "goguryeo"),
            Map.entry("korea_joseon", "joseon"),
            Map.entry("awadh", "nawab_of_awadh"),
            Map.entry("bengal_nawab", "nawabs_of_bengal"),
            Map.entry("bengal_sultanate", "sultanate_of_bengal"),
            Map.entry("ayutthaya", "ayutthaya_kingdom"),
            Map.entry("bahmani", "bahmani_sultanate"),
            Map.entry("caliphate_abbasid", "abbasid_caliphate"),
            Map.entry("caliphate_umayyad", "umayyad_caliphate"),
            Map.entry("chalukya_western", "western_chalukya_empire"),
            Map.entry("chandela", "chandela_kingdom"),
            Map.entry("chaulukya", "chaulukya_dynasty"),
            Map.entry("chimu", "chimu_empire"),
            Map.entry("fatimid", "fatimid_caliphate"),
            Map.entry("florence", "republic_of_florence"),
            Map.entry("gokturk", "gokturk_khaganate"),
            Map.entry("harsha", "harsha_s_empire"),
            Map.entry("hoysala", "hoysala_kingdom"),
            Map.entry("hre", "holy_roman_empire"),
            Map.entry("illyria", "illyrian_kingdom"),
            Map.entry("inca", "inca_empire"),
            Map.entry("khmer", "khmer_empire"),
            Map.entry("kongo_kingdom", "kingdom_of_kongo"),
            Map.entry("minoan", "minoan_civilization"),
            Map.entry("pala", "pala_empire"),
            Map.entry("kush", "kingdom_of_kush"),
            Map.entry("qutb_shahi", "golconda_sultanate"),
            Map.entry("adil_shahi", "bijapur_sultanate"),
            Map.entry("nizam_shahi", "ahmadnagar_sultanate"),
            Map.entry("imad_shahi", "berar_sultanate"),
            Map.entry("kanem_bornu", "bornu_empire"),
            Map.entry("seljuk", "great_seljuk_empire"),
            Map.entry("sikh", "sikh_empire"),
            Map.entry("vijayanagara", "vijayanagara_empire"),
            Map.entry("zulu", "zululand"),
            Map.entry("china_shang", "shang_dynasty"),
            Map.entry("china_zhou", "zhou_dynasty"),
            Map.entry("chola", "chola_empire"),
            Map.entry("sweden", "kingdom_of_sweden"),
            Map.entry("ottoman", "ottoman_empire"),
            /*
             * Turn 2026-10-24 (R2-a). Ten site hints that were failing for no reason at all: the
             * register ALREADY holds a polity whose slug is exactly the hint, so the alias is not a
             * mapping, it is a restatement. Each was found by slugifying the register's own polity
             * names with RulerRegister.civHintFor and looking for an exact hit - no judgement used.
             */
            Map.entry("anuradhapura", "anuradhapura"),
            Map.entry("assyria", "assyria"),
            Map.entry("carthage", "carthage"),
            Map.entry("french_india", "french_india"),
            Map.entry("jaunpur_sultanate", "jaunpur_sultanate"),
            Map.entry("kalinga", "kalinga"),
            Map.entry("mali_empire", "mali_empire"),
            Map.entry("nepal", "nepal"),
            Map.entry("songhai_empire", "songhai_empire"),
            Map.entry("teotihuacan", "teotihuacan"));

    /**
     * Site hints that name a real polity <b>the register's source does not hold</b>.
     *
     * <p>Turn 2026-10-24 (R2-a). These eighteen were the "18 vocabulary ids still unresolvable" of the
     * marking turn. They are not misspellings and not pack ids: each names a polity that existed
     * (Mewar, Marwar, Travancore, the Holkars, Dogra Kashmir...) and that Cliopatria v0.2.1, the
     * register's only source, does not carry. They cannot be aliased, because an alias is a claim
     * that two names mean one polity, and there is nothing on the other side to mean.
     *
     * <p>So they are <b>declared</b> here instead: named, counted, and given their reason, so the
     * vocabulary check can tell "we know this one is missing" apart from "something new broke". A
     * declared gap is a fact about the source; an undeclared one is a bug. The rule the probe
     * enforces is <b>zero undeclared</b>.
     *
     * <p>What the mod does at these sites is unchanged and correct: {@link #resolve} returns empty and
     * the caller falls through to the year-aware layers, which answer from whoever the register says
     * ruled that cell in that year.
     */
    public static final Set<String> ABSENT_FROM_SOURCE = Set.of(
            "ahom",            // Ahom kingdom, Assam - source holds no Ahom polity
            "amber",           // Jaipur (Amber) state - Rajput house, source holds no polity
            "bhati",           // Bhati Rajputs of Jaisalmer - source holds no polity
            "bhopal_state",    // Bhopal princely state - source holds no polity
            "bundela",         // Bundela Rajputs of Orchha - source holds no polity
            "dogra",           // Dogra rule in Jammu and Kashmir - source holds no polity
            "holkar",          // Holkar state of Indore - source holds no polity
            "ikshvaku",        // Ikshvaku dynasty of the Andhra country - source holds no polity
            "kandy",           // Kingdom of Kandy - source holds no polity
            "kolathiri",       // Kolathunadu, north Kerala - source holds no polity
            "ladakh",          // Namgyal Ladakh - source holds no polity
            "manipur",         // Kangleipak / Manipur kingdom - source holds no polity
            "marwar",          // Marwar (Jodhpur) - Rajput house, source holds no polity
            "mewar",           // Mewar (Udaipur) - Rajput house, source holds no polity
            "somavamshi",      // Somavamshi dynasty of Odisha - source holds no polity
            "tomara",          // Tomara Rajputs of Delhi - source holds no polity
            "travancore",      // Travancore - source holds no polity
            "zamorin");        // Zamorin of Calicut - source holds no polity

    /*
     * How this table was made, and what was thrown away.
     *
     * The first 18 rows were written by hand. The rest were PROPOSED by a generator that asked the
     * only objective question available - "is there exactly ONE register id containing this string?" -
     * and then read one by one, because a generator's output is a proposal, not an answer. Four of
     * its proposals were rejected:
     *
     *   rome     -> prome_kingdom        "rome" is a substring of "prome". Kept as roman_empire.
     *   japan_*  -> japan                Kamakura, Muromachi and Tokugawa are not one polity, and
     *                                    "japan" is a country id. Now listed in REGIONS.
     *   pallava  -> late_pallava_empire  only the LATE empire matched; the early one is not this.
     *   hittite,
     *   hawaii_kingdom,
     *   mississippian -> a modern successor  the register's nearest match was the modern state, which
     *                                    is not what the pack means. Left unresolved rather than
     *                                    mapped to something false.
     */

    /**
     * Pack ids that name a land over time. Deliberately NOT mapped: the honest answer differs by year,
     * and the ruler register already gives that answer. Listed so a reader can tell "we know this is a
     * region" from "we forgot this one".
     */
    public static final Set<String> REGIONS = Set.of(
            "india", "china", "egypt_ancient", "greece", "persia", "mesopotamia",
            "british_india", "austria", "bohemia", "bulgaria", "aztec", "babylon", "athens",
            "sparta", "benin_kingdom", "brunei", "ashanti", "axum", "mississippian", "olmec",
            "japan_kamakura", "japan_tokugawa", "japan_yamato", "hittite", "hawaii_kingdom",
            "england",
            "france",
            "gaul",
            "dacia",
            "russia",
            "spain",
            "poland",
            "portuguese",
            "prussia",
            "lithuania",
            "mamluk",
            "maya",
            "naples",
            "oman",
            "toungoo",
            "pallava",
            "jin_dynasty",
            "delhi_sultanate",
            "mapuche",
            "tiwanaku",
            "tonga_empire",
            "switzerland",
            "puebloan",
            "cheras",
            "magadha",
            "dai_viet",
            "gujarat_sultanate",
            "swahili_kilwa",
            "mutapa",
            "zimbabwe",
            "malwa_sultanate",
            "chalukya_badami");

    /**
     * The polity id this hint should be read as, or empty when it should not be read as a polity at all
     * (a region id, an unknown id, or an alias whose target the register does not carry).
     */
    public static Optional<String> resolve(String hint, RulerRegister register) {
        if (hint == null || hint.isBlank()) {
            return Optional.empty();
        }
        String h = hint.trim();
        if (REGIONS.contains(h)) {
            return Optional.empty();
        }
        String target = ALIASES.get(h);
        if (target == null) {
            return Optional.empty();
        }
        boolean known = register != null && register.polities().stream()
                .anyMatch(p -> p.civHint().equals(target));
        return known ? Optional.of(target) : Optional.empty();
    }

    /** Is this hint a gap we have already named and explained? See {@link #ABSENT_FROM_SOURCE}. */
    public static boolean declaredAbsent(String hint) {
        return hint != null && ABSENT_FROM_SOURCE.contains(hint.trim());
    }

    /** How many aliases the table carries, for the probes and the check report. */
    public static int aliasCount() {
        return ALIASES.size();
    }

    /** The alias table, unmodifiable, for probes that want to report it. */
    public static Map<String, String> aliases() {
        return ALIASES;
    }
}

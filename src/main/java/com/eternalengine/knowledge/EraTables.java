package com.eternalengine.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The approved prototype's fixed tables, point to point: the thirty-six roles and their
 * counter lines (the person register's "his counter" page), the ten region names, and the
 * era tables the register reads — the cloth of the heat, the thing the hands are on, the
 * temperament drawn once, the town's hour, the water, and the prayer.
 *
 * <p>The role and trade lists load from {@code era_register.json} (the prototype's own data);
 * when it is missing the lines fall back to the role's id, the talk keeps working.
 */
public final class EraTables {

    private static final Logger LOGGER = LoggerFactory.getLogger("eternalengine");
    private static final String PATH = "/data/eternalengine/eternal/knowledge/era_register.json";

    /** One role of the prototype's thirty-six: its name, sign, verb, tier, and what the hands know. */
    public record RoleDef(String n, String g, String v, int t, String k) {
    }

    /** One role's counter (the prototype's TRADE table): what it sells, takes, and how the street runs. */
    public record TradeDef(List<String> sell, List<String> buy, String npc, String sellNone, String buyNone) {
    }

    private static final Map<String, RoleDef> ROLES = new HashMap<>();
    private static final Map<String, TradeDef> TRADE = new HashMap<>();
    private static final Map<String, String> REGIONS = new HashMap<>();
    private static boolean loaded;

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        try (InputStream raw = EraTables.class.getResourceAsStream(PATH)) {
            if (raw != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject roles = root.getAsJsonObject("roles");
                if (roles != null) {
                    for (String id : roles.keySet()) {
                        JsonObject o = roles.getAsJsonObject(id);
                        ROLES.put(id, new RoleDef(str(o, "n"), str(o, "g"), str(o, "v"),
                                o.get("t") == null ? 1 : o.get("t").getAsInt(), str(o, "k")));
                    }
                }
                JsonObject trade = root.getAsJsonObject("trade");
                if (trade != null) {
                    for (String id : trade.keySet()) {
                        JsonObject o = trade.getAsJsonObject(id);
                        TRADE.put(id, new TradeDef(strs(o, "sell"), strs(o, "buy"),
                                str(o, "npc"), str(o, "sellNone"), str(o, "buyNone")));
                    }
                }
                JsonObject regions = root.getAsJsonObject("regions");
                if (regions != null) {
                    for (String id : regions.keySet()) {
                        REGIONS.put(id, regions.get(id).getAsString());
                    }
                }
                LOGGER.info("era register tables loaded: {} roles, {} regions", ROLES.size(), REGIONS.size());
            }
        } catch (Exception e) {
            LOGGER.warn("era_register.json could not be read: {}", e.toString());
        }
        loaded = true;
    }

    private EraTables() {
    }

    // ------------------------------------------------------------------ the roles

    /** The role's display name ("Weaver"), or its id when the table is missing. */
    public static String roleName(String id) {
        load();
        RoleDef d = ROLES.get(id);
        return d == null ? id : d.n();
    }

    /** The role's name in the lower case the prototype's lines wear ("weaver"). */
    public static String roleWord(String id) {
        return roleName(id).toLowerCase(Locale.ROOT);
    }

    /** The role's fixed character (the prototype's TEMPER), drawn once. */
    public static String temper(String id) {
        return switch (id) {
            case "citizen" -> "quiet · steady · watchful";
            case "farmer" -> "patient · thrifty · proud";
            case "forager" -> "lone · watchful · kind";
            case "artisan" -> "careful · proud · slow to anger";
            case "merchant" -> "quick · sharp · fair";
            case "trader" -> "road-wise · fair · quick to deal";
            case "pedlar" -> "chatty · friendly · road-wise";
            case "money_changer" -> "exact · quiet · trusts the coin";
            case "paper_seller" -> "quick with words · sharp-eyed";
            case "news_writer" -> "careful with words · steady";
            case "town_crier" -> "loud · friendly · quick";
            case "weaver" -> "patient · thrifty · proud";
            case "smith" -> "strong · blunt · loyal";
            case "mason" -> "steady · sure-handed · slow to anger";
            case "scribe" -> "quiet · exact · reserved";
            case "scholar" -> "curious · patient · soft-spoken";
            case "clerk" -> "methodical · fair · dry";
            case "teacher" -> "patient · kind · firm";
            case "priest" -> "calm · kind · steady";
            case "doctor" -> "steady · calm · careful";
            case "healer" -> "kind · patient · gentle";
            case "quarantine_inspector" -> "stern · exact · fair";
            case "soldier" -> "disciplined · blunt · loyal";
            case "guard" -> "watchful · steady · dry";
            case "royal_guard" -> "disciplined · proud · silent";
            case "commander" -> "sure · cold · commanding";
            case "ruler" -> "measured · proud · watchful";
            case "sovereign_ruler" -> "measured · proud · watchful";
            case "court_chancellor" -> "exact · reserved · cold";
            case "caravan_master" -> "road-wise · sure · fair";
            case "caravan_guard" -> "watchful · blunt · loyal";
            case "ship_captain" -> "sea-wise · steady · blunt";
            case "ship_marine" -> "rough · loud · loyal";
            case "imperial_mason" -> "steady · sure-handed · proud";
            case "bandit" -> "wary · quick · hard";
            case "rival_infiltrator" -> "quiet · watchful · unreadable";
            default -> "quiet · steady";
        };
    }

    /** The thing the hands are on, by role (the prototype's WORK). */
    public static String work(String id) {
        return switch (id) {
            case "citizen" -> "the street";
            case "farmer" -> "the field";
            case "forager" -> "the mountain path";
            case "artisan" -> "the bench";
            case "merchant" -> "the counter";
            case "trader" -> "the road";
            case "pedlar" -> "the basket and the road";
            case "money_changer" -> "the scale";
            case "paper_seller" -> "the paper stall";
            case "news_writer" -> "the pen";
            case "town_crier" -> "the voice and the horn";
            case "weaver" -> "the loom";
            case "smith" -> "the forge";
            case "mason" -> "the stone";
            case "scribe" -> "the desk";
            case "scholar" -> "the texts";
            case "clerk" -> "the ledgers";
            case "teacher" -> "the letters";
            case "priest" -> "the prayer";
            case "doctor" -> "the sick";
            case "healer" -> "the herbs";
            case "quarantine_inspector" -> "the gate";
            case "soldier" -> "the drill";
            case "guard" -> "the beat";
            case "royal_guard" -> "the palace gate";
            case "commander" -> "the army";
            case "ruler" -> "the decree";
            case "sovereign_ruler" -> "the crown";
            case "court_chancellor" -> "the seal";
            case "caravan_master" -> "the caravan";
            case "caravan_guard" -> "the caravan";
            case "ship_captain" -> "the deck";
            case "ship_marine" -> "the ship";
            case "imperial_mason" -> "the great works";
            case "bandit" -> "the hidden roads";
            case "rival_infiltrator" -> "the shadow";
            default -> "the work";
        };
    }

    /** One role's counter (the prototype's TRADE); the citizen's where it is missing. */
    public static TradeDef trade(String id) {
        load();
        TradeDef d = TRADE.get(id);
        return d != null ? d : TRADE.get("citizen");
    }

    /** The region's name the way the register prints it ("The Deccan · the plateau"). */
    public static String regionName(String id) {
        load();
        String s = REGIONS.get(id);
        return s == null ? id : s;
    }

    // ------------------------------------------------------------------ the era tables

    /** The cloth of the era's heat (the prototype's CLOTH), by era class. */
    public static String cloth(char eraClass) {
        return switch (eraClass) {
            case 'a' -> "plain cloth wrapped at the waist, bare feet, the colour of the dust";
            case 'm' -> "a white cotton shirt, dark cloth at the waist";
            case 'e' -> "a white shirt, a dark coat over it, boots at the door";
            default -> "a pressed shirt, a dark coat, the face of the hour";
        };
    }

    /** The town's hour (the prototype's MINDHOUR), by the time of day. */
    public static String mindHour(char hour) {
        return switch (hour) {
            case 'm' -> "the morning is his, and the work takes the rest";
            case 'n' -> "the day is done, and the count sits on the table";
            case 'e' -> "the evening is quiet, and the watch is what the town is";
            default -> "the night is his own, and the town's news is on the road";
        };
    }

    /** The water of the era (the prototype's WATER): the tap, the pump, or the well. */
    public static String water(char eraClass) {
        return switch (eraClass) {
            case 'n' -> "the tap";
            case 'e' -> "the pump";
            default -> "the well";
        };
    }

    /** The prayer of the region (the prototype's PRAYER): the qibla, the temple, or the church. */
    public static String prayer(String region) {
        return switch (region) {
            case "mesopotamian", "deccan", "persianate", "north_african", "african" -> "the qibla";
            case "americas", "mediterranean" -> "the church";
            default -> "the temple";
        };
    }

    /** The year, the way the register prints it ("550 BCE", "1948 CE"). */
    public static String yearLbl(int year) {
        return year < 0 ? (-year) + " BCE" : year + " CE";
    }

    /** The year of birth, the way the register prints it. */
    public static String bornLbl(int year, int age) {
        int born = year - age;
        return born < 0 ? (-born) + " BCE" : born + " CE";
    }

    /**
     * The time of day of the game clock, the prototype's four hours: morning, noon, evening,
     * night. Tick 0 is 06:00; the hours run 6–11, 12–16, 17–20, and the rest night.
     */
    public static char hourOf(long dayTime) {
        int h = (int) (dayTime / 1000L + 6L) % 24;
        if (h >= 6 && h < 12) {
            return 'm';
        }
        if (h >= 12 && h < 17) {
            return 'n';
        }
        if (h >= 17 && h < 21) {
            return 'e';
        }
        return 'g';
    }

    /** The small of the mind line: when, in the words of the day. */
    public static String hourLbl(char hour) {
        return switch (hour) {
            case 'm' -> "this morning";
            case 'n' -> "at noon";
            case 'e' -> "this afternoon";
            default -> "tonight";
        };
    }

    // ------------------------------------------------------------------ small

    private static String str(JsonObject o, String key) {
        JsonElement je = o.get(key);
        return je == null || je.isJsonNull() ? "" : je.getAsString();
    }

    private static List<String> strs(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement je = o.get(key);
        if (je != null && je.isJsonArray()) {
            for (JsonElement e : je.getAsJsonArray()) {
                out.add(e.getAsString());
            }
        }
        return List.copyOf(out);
    }
}

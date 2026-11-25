package com.eternalengine.knowledge;

import com.eternalengine.villager.PlaceNamePools;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The world by year and place: forty-two bands from 3000 BC to 3000 AD — who rules at each
 * place-year, where the court stands, what the year does, who is famous, what stands, and the
 * words of greeting. With it the counter register: twenty-three roles hold counters, and what
 * each of them sells and buys in each band — the recorded prices (tag "r") or the estimated
 * ones ("e") — and, where the trade does not exist at that place-year, the honest line instead
 * of goods ("y" the thing is not born yet, "h" there is no such trade here).
 *
 * <p>The data is the approved prototype's, point to point. {@code counters[role]} is indexed
 * like {@code worlds}: the same index is the same band. The Deccan 1948 band carries the
 * recorded prices of the pack; the deep past is estimated and labelled.
 *
 * <p>Loaded once, read afterwards. When the pack is missing the answers stay general — talk
 * keeps working, the world lines go quiet.
 */
public final class EraWorld {

    private static final Logger LOGGER = LoggerFactory.getLogger("eternalengine");
    private static final String PATH = "/data/eternalengine/eternal/knowledge/era_world.json";

    /** One band: a place-year and what is true there. */
    public record World(String region, int from, int to, int year, String place, String date,
                        String ruler, String court, String courtClk, String event,
                        String famous, String famousWho, String landmark, String greet, int age,
                        List<Where> where) {
    }

    /** The household of one band (the person register's "his life" page): what a person's home
     * holds in that place-year, index aligned with {@code worlds}. */
    public record House(String wife, String hope, String home, String stage, String belief,
                        String paper, String gather) {
    }

    /** The lane's register: a person of a role, and where to find them. */
    public record Where(String role, String name, String place) {
    }

    /** One good of the counter: its name and its price in the words of the place. */
    public record Good(String item, String price) {
    }

    /** One counter of one role in one band; the band's year is the band's, the index carries it.
     * {@code line} is the register's prices line; empty {@code sell}/{@code buy} with a note:
     * the honest screen, where there is nothing to trade at that counter. */
    public record Entry(String line, List<Good> sell, List<Good> buy, String note, String tag, String reg) {
        /** True when the counter has goods and prices, not only the honest line. */
        public boolean priced() {
            return !sell.isEmpty();
        }
    }

    /** The pack is missing: nothing answered from it. */
    public static final EraWorld EMPTY = new EraWorld(List.of(), new String[0], new Entry[0][0], List.of());

    private final List<World> worlds;
    private final String[] roles;
    private final Entry[][] counters;
    private final List<House> houses;

    private EraWorld(List<World> worlds, String[] roles, Entry[][] counters, List<House> houses) {
        this.worlds = worlds;
        this.roles = roles;
        this.counters = counters;
        this.houses = houses;
    }

    /** Reads the pack, or {@link #EMPTY} when it is missing or broken — never a crash. */
    public static EraWorld load() {
        try (InputStream raw = EraWorld.class.getResourceAsStream(PATH)) {
            if (raw == null) {
                LOGGER.warn("era_world.json not found — the world-by-year answers stay general");
                return EMPTY;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray ws = root.getAsJsonArray("worlds");
            List<World> worlds = new ArrayList<>(ws.size());
            for (JsonElement je : ws) {
                JsonObject o = je.getAsJsonObject();
                JsonArray b = o.getAsJsonArray("b");
                List<Where> where = new ArrayList<>(3);
                JsonElement wj = o.get("where");
                if (wj != null && wj.isJsonArray()) {
                    for (JsonElement we : wj.getAsJsonArray()) {
                        JsonObject wo = we.getAsJsonObject();
                        where.add(new Where(str(wo, "r"), str(wo, "n"), str(wo, "p")));
                    }
                }
                worlds.add(new World(str(o, "r"), b.get(0).getAsInt(), b.get(1).getAsInt(),
                        o.get("year").getAsInt(), str(o, "place"), str(o, "date"), str(o, "ruler"),
                        str(o, "court"), str(o, "courtClk"), str(o, "event"), str(o, "famous"),
                        str(o, "famousWho"), str(o, "landmark"), str(o, "greet"), o.get("age") == null ? 0 : o.get("age").getAsInt(),
                        List.copyOf(where)));
            }
            JsonObject co = root.getAsJsonObject("counters");
            String[] roles = new String[co.size()];
            Entry[][] counters = new Entry[roles.length][];
            int ri = 0;
            for (Map.Entry<String, JsonElement> en : co.entrySet()) {
                JsonArray arr = en.getValue().getAsJsonArray();
                roles[ri] = en.getKey();
                counters[ri] = new Entry[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    counters[ri][i] = new Entry(str(o, "k"), pairs(o, "s"), pairs(o, "b"),
                            str(o, "note"), str(o, "tag"), str(o, "reg"));
                }
                ri++;
            }
            List<House> houses = new ArrayList<>();
            JsonElement hj = root.get("household");
            if (hj != null && hj.isJsonArray()) {
                for (JsonElement he : hj.getAsJsonArray()) {
                    JsonObject ho = he.getAsJsonObject();
                    houses.add(new House(str(ho, "w"), str(ho, "h"), str(ho, "m"), str(ho, "s"),
                            str(ho, "b"), str(ho, "p"), str(ho, "g")));
                }
            }
            LOGGER.info("era world loaded: {} bands, {} counters", worlds.size(), roles.length);
            return new EraWorld(List.copyOf(worlds), roles, counters, List.copyOf(houses));
        } catch (Exception e) {
            LOGGER.warn("era_world.json could not be read: {}", e.toString());
            return EMPTY;
        }
    }

    /** True when the pack is with us. */
    public boolean has() {
        return !worlds.isEmpty();
    }

    /** The band for a point and a year: the pool of the point, the nearest year in it; the
     * nearest year anywhere when no pool holds the point. */
    @Nullable
    public World world(double lat, double lon, int year) {
        int i = pick(lat, lon, year);
        return i < 0 ? null : worlds.get(i);
    }

    /** The household of the band of a point and a year, or {@code null} (pack missing, or the
     * household table is short). */
    @Nullable
    public House house(double lat, double lon, int year) {
        int i = pick(lat, lon, year);
        if (i < 0 || i >= houses.size()) {
            return null;
        }
        return houses.get(i);
    }

    /** The counter of a role at a point and a year, or {@code null} when the role holds no
     * counter or the pack is missing. */
    @Nullable
    public Entry counter(String role, double lat, double lon, int year) {
        if (roles.length == 0) {
            return null;
        }
        int ri = roleIndex(role);
        if (ri < 0) {
            return null;
        }
        int i = pick(lat, lon, year);
        if (i < 0 || i >= counters[ri].length) {
            return null;
        }
        return counters[ri][i];
    }

    /** The roles that hold counters. */
    public List<String> roles() {
        return List.of(roles);
    }

    /** The band index for a point and a year, or -1. */
    public int pick(double lat, double lon, int year) {
        if (worlds.isEmpty()) {
            return -1;
        }
        String pool = PlaceNamePools.poolFor(lat, lon);
        int best = -1;
        long bestKey = Long.MIN_VALUE;
        for (int i = 0; i < worlds.size(); i++) {
            World w = worlds.get(i);
            long key = (pool != null && w.region().equals(pool) ? 10_000_000L : 0L)
                    - Math.abs(year - w.year());
            if (best < 0 || key > bestKey) {
                best = i;
                bestKey = key;
            }
        }
        return best;
    }

    private int roleIndex(String role) {
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equals(role)) {
                return i;
            }
        }
        return -1;
    }

    /** A list of goods: each entry is a pair, the item and its price in words. */
    private static List<Good> pairs(JsonObject o, String key) {
        List<Good> out = new ArrayList<>();
        JsonElement je = o.get(key);
        if (je != null && je.isJsonArray()) {
            for (JsonElement e : je.getAsJsonArray()) {
                if (e.isJsonArray()) {
                    JsonArray pa = e.getAsJsonArray();
                    if (pa.size() >= 2) {
                        out.add(new Good(strOf(pa.get(0)), strOf(pa.get(1))));
                    } else if (pa.size() == 1) {
                        out.add(new Good(strOf(pa.get(0)), ""));
                    }
                } else if (e.isJsonPrimitive()) {
                    out.add(new Good(e.getAsString(), ""));
                }
            }
        }
        return List.copyOf(out);
    }

    private static String strOf(JsonElement e) {
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static String str(JsonObject o, String key) {
        JsonElement je = o.get(key);
        return je == null || je.isJsonNull() ? "" : je.getAsString();
    }
}

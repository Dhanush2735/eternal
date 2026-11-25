package com.eternalengine.money;

import com.eternalengine.trade.CommodityPricingEngine;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Step 19b: what merchants buy. One entry per good of the trade engine ({@link CommodityPricingEngine}): its names,
 * the Minecraft items that count as it, and where and when nobody buys it (no wheat in the Americas before the
 * Spanish, no iron before the Iron Age). Prices are never in this table: they come from the engine
 * ({@link MarketService#worth}). Written by {@code worldfill/build_goods_table.py}.
 */
public final class GoodsTable {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/goods.json";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Nobody buys the good before {@code before} (exclusive), everywhere or only inside {@code box}. */
    public record Rule(int before, double[] box) {
        /** True when this rule stops the good at this point in this year (an unknown place: only box-less rules). */
        public boolean blocks(double lat, double lon, int year) {
            if (year >= before) {
                return false;
            }
            if (box == null) {
                return true;
            }
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                return false;
            }
            return lat >= box[0] && lat <= box[2] && lon >= box[1] && lon <= box[3];
        }
    }

    /** One good: names, the items that count as it, how many items make one engine unit, and its rules. */
    public record Good(String id, String name, String one, String many, List<String> items, double unitItems, int want,
                       List<Rule> not) {
        /** False for goods with no Minecraft item yet (wine, silk, spices). */
        public boolean sellable() {
            return !items.isEmpty();
        }

        /** True when merchants buy it at this point in this year. */
        public boolean boughtAt(double lat, double lon, int year) {
            for (Rule r : not) {
                if (r.blocks(lat, lon, year)) {
                    return false;
                }
            }
            return true;
        }

        /** "1 sheaf of wheat", "23 sheaves of wheat". */
        public String count(long n) {
            return n == 1 ? "1 " + one : n + " " + many;
        }

        /** "a sheaf of wheat", "an emerald"; several: "16 logs". */
        public String some(long n) {
            if (n != 1) {
                return n + " " + many;
            }
            return ("aeiou".indexOf(Character.toLowerCase(one.charAt(0))) >= 0 ? "an " : "a ") + one;
        }

        /** The item ids (no tags). */
        public List<String> ids() {
            List<String> out = new ArrayList<>();
            for (String s : items) {
                if (!s.startsWith("#")) {
                    out.add(s);
                }
            }
            return out;
        }

        /** The item tags, without the "#". */
        public List<String> tags() {
            List<String> out = new ArrayList<>();
            for (String s : items) {
                if (s.startsWith("#")) {
                    out.add(s.substring(1));
                }
            }
            return out;
        }
    }

    public record Summary(int goods, int withItems, boolean loaded, String source) {
    }

    private static volatile GoodsTable instance;

    private final List<Good> goods;
    private final Map<String, Good> byId;
    private final List<String> problems;
    private final String source;

    private GoodsTable(List<Good> goods, List<String> problems, String source) {
        this.goods = List.copyOf(goods);
        Map<String, Good> m = new LinkedHashMap<>();
        for (Good g : goods) {
            m.put(g.id(), g);
        }
        this.byId = Collections.unmodifiableMap(m);
        this.problems = List.copyOf(problems);
        this.source = source;
    }

    /** The game's table, read from the mod jar the first time it is asked for. */
    public static GoodsTable get() {
        GoodsTable t = instance;
        if (t == null) {
            synchronized (GoodsTable.class) {
                t = instance;
                if (t == null) {
                    t = loadResource();
                    instance = t;
                }
            }
        }
        return t;
    }

    /** Read the table again (/ee reload). */
    public static GoodsTable reload() {
        synchronized (GoodsTable.class) {
            instance = loadResource();
            return instance;
        }
    }

    private static GoodsTable loadResource() {
        try (InputStream in = GoodsTable.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.warn("[Money] {} is missing from the mod jar: merchants buy nothing", RESOURCE);
                return new GoodsTable(List.of(), List.of("missing " + RESOURCE), "none");
            }
            GoodsTable t = read(new InputStreamReader(in, StandardCharsets.UTF_8), RESOURCE);
            LOGGER.info("[Money] goods table: {} goods, {} with items{}", t.goods.size(), t.summary().withItems(),
                    t.problems.isEmpty() ? "" : "; problems: " + String.join("; ", t.problems));
            return t;
        } catch (Exception e) {
            LOGGER.warn("[Money] could not read {}: {}", RESOURCE, e.toString());
            return new GoodsTable(List.of(), List.of(RESOURCE + " failed: " + e), "none");
        }
    }

    /** Read a table (for the game and for checks). Problems are collected, not thrown. */
    public static GoodsTable read(Reader reader, String source) {
        List<String> problems = new ArrayList<>();
        List<Good> goods = new ArrayList<>();
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonArray arr = root.has("goods") ? root.getAsJsonArray("goods") : new JsonArray();
        CommodityPricingEngine engine = new CommodityPricingEngine();
        for (JsonElement el : arr) {
            try {
                JsonObject o = el.getAsJsonObject();
                String id = o.get("id").getAsString().toUpperCase(Locale.ROOT);
                if (engine.getSpec(id) == null) {
                    problems.add(id + ": not a good of the trade engine");
                    continue;
                }
                List<String> items = new ArrayList<>();
                if (o.has("items")) {
                    for (JsonElement it : o.getAsJsonArray("items")) {
                        String s = it.getAsString().trim();
                        if (!s.contains(":")) {
                            problems.add(id + ": item \"" + s + "\" has no namespace");
                            continue;
                        }
                        items.add(s);
                    }
                }
                List<Rule> rules = new ArrayList<>();
                if (o.has("not")) {
                    for (JsonElement r : o.getAsJsonArray("not")) {
                        JsonObject ro = r.getAsJsonObject();
                        double[] box = null;
                        if (ro.has("box")) {
                            JsonArray b = ro.getAsJsonArray("box");
                            box = new double[] {b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                                    b.get(3).getAsDouble()};
                            if (box[0] >= box[2] || box[1] >= box[3]) {
                                problems.add(id + ": a box with no area");
                            }
                        }
                        rules.add(new Rule(ro.get("before").getAsInt(), box));
                    }
                }
                double unitItems = o.has("unitItems") ? o.get("unitItems").getAsDouble() : 1.0d;
                int want = o.has("want") ? o.get("want").getAsInt() : 16;
                if (unitItems <= 0 || want <= 0) {
                    problems.add(id + ": unitItems and want must be above 0");
                    continue;
                }
                String name = o.get("name").getAsString();
                goods.add(new Good(id, name, o.has("one") ? o.get("one").getAsString() : name,
                        o.has("many") ? o.get("many").getAsString() : name, List.copyOf(items), unitItems, want,
                        List.copyOf(rules)));
            } catch (Exception e) {
                problems.add("a good could not be read: " + e);
            }
        }
        return new GoodsTable(goods, problems, source);
    }

    public List<Good> all() {
        return goods;
    }

    public Good good(String id) {
        return id == null ? null : byId.get(id.toUpperCase(Locale.ROOT));
    }

    /** The goods with items that merchants buy at this point in this year, in table order. */
    public List<Good> boughtAt(double lat, double lon, int year) {
        List<Good> out = new ArrayList<>();
        for (Good g : goods) {
            if (g.sellable() && g.boughtAt(lat, lon, year)) {
                out.add(g);
            }
        }
        return out;
    }

    public List<String> problems() {
        return problems;
    }

    public Summary summary() {
        int with = 0;
        for (Good g : goods) {
            if (g.sellable()) {
                with++;
            }
        }
        return new Summary(goods.size(), with, !goods.isEmpty(), source);
    }

    /**
     * The table's own check: no problems while reading; at least ten goods can be sold; wheat is bought in Hyderabad
     * in 1948 but not in Tenochtitlan in 1500 (it is in 1600); iron is not bought in Babylon in 1750 BCE nor in
     * China in 1000 BCE, but is in Rome in 100. Null when all is well.
     */
    public String selfTest() {
        if (!problems.isEmpty()) {
            return problems.size() + " problem(s) in the goods table: " + String.join("; ",
                    problems.subList(0, Math.min(5, problems.size())));
        }
        if (summary().withItems() < 10) {
            return "only " + summary().withItems() + " goods can be sold";
        }
        Good wheat = good("WHEAT");
        Good iron = good("IRON_INGOT");
        if (wheat == null || iron == null) {
            return "no wheat or iron in the goods table";
        }
        if (!wheat.boughtAt(17.3616, 78.4747, 1948) || wheat.boughtAt(19.43, -99.13, 1500)
                || !wheat.boughtAt(19.43, -99.13, 1600)) {
            return "wheat: Hyderabad 1948 " + wheat.boughtAt(17.3616, 78.4747, 1948) + ", Tenochtitlan 1500 "
                    + wheat.boughtAt(19.43, -99.13, 1500) + ", Mexico 1600 " + wheat.boughtAt(19.43, -99.13, 1600)
                    + "; expected true, false, true";
        }
        if (iron.boughtAt(32.54, 44.42, -1750) || iron.boughtAt(39.9, 116.4, -1000) || !iron.boughtAt(41.89, 12.49, 100)) {
            return "iron: Babylon 1750 BCE " + iron.boughtAt(32.54, 44.42, -1750) + ", China 1000 BCE "
                    + iron.boughtAt(39.9, 116.4, -1000) + ", Rome 100 " + iron.boughtAt(41.89, 12.49, 100)
                    + "; expected false, false, true";
        }
        return null;
    }
}

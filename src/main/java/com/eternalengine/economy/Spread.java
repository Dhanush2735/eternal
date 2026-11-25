package com.eternalengine.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 47 part 5 (B6 and E18): <b>crops and animals as items, as they spread</b>.
 *
 * <p>The plan's line for B6 is "no maize, potatoes or chillies in India before the 1500s", and for
 * E18 "animals and crops as items, as they spread". Neither needs a new date: {@code
 * products_world.json} already puts maize in Europe in 1520 and in Mesoamerica in 7000 BC, and
 * chillies in Goa in 1540.
 *
 * <h2>What was missing was the other half of a spread</h2>
 *
 * <p>A year on its own cannot say whether a potato in Bengal in 1700 is <b>a crop that arrived</b>
 * or <b>a crop that was always there</b>. Only the origin can say that, and the origin lives in a
 * different register (Khoury et al. 2016: 150 crops against the 23 regions their wild relatives come
 * from, with no dates at all). So this class is a <b>join</b>, and the year a crop left home falls
 * out of it without anybody writing it: it is the earliest year the thing is made somewhere that is
 * not its origin.
 *
 * <h2>The finding</h2>
 *
 * <p><b>The Columbian exchange is measurable and it took about a lifetime.</b> The chilli reaches
 * Goa in 1540 and displaces long pepper in Indian cooking within fifty years, which means a New
 * World fruit made South Asian food into what the world now calls South Asian food in less than one
 * lifetime. That is not a story about food, it is a story about ships.
 *
 * <h2>The limit, declared in the data rather than here</h2>
 *
 * <p>The atlas has **133 hand-written rows and 406 derived ones**, and a derived row takes its
 * parent's dates. For flour that is exactly right. For <b>sorghum</b> it is wrong, because sorghum's
 * parent is millet and millet's home in the atlas is East Asia, so derived sorghum "originates" in
 * China; the same fault puts <b>groundnut</b>, a South American crop, in West Asia. So an origin is
 * claimed only for a hand-written row, and every other row is marked {@code inherited} and names its
 * parent. {@link Row#inherited()} is the method that asks.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Spread {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/spread.json.gz";

    /** The year the Atlantic exchange begins. Nothing with an Old World home is in the Americas
     *  before it, and nothing with an American home is in the Old World before it. */
    public static final int EXCHANGE_YEAR = 1492;

    public static final int NEVER = Integer.MIN_VALUE;

    /** Where a thing comes from, and whether that is a claim or something it inherited. */
    public record Origin(List<String> areas, int year, String source,
                         @Nullable String parent) {
        public boolean inherited() {
            return !"hand".equals(source);
        }

        public boolean home(String area) {
            return areas.contains(area);
        }

        public boolean american() {
            return areas.stream().anyMatch(Spread::americanArea);
        }

        public boolean oldWorld() {
            return areas.stream().anyMatch(a -> !americanArea(a));
        }
    }

    /** One crop or animal: origin, the year it is made in each area, and the user-facing note. */
    public record Row(String id, String kind, String name, @Nullable String item, String mark,
                      Origin origin, Map<String, Integer> made, Map<String, Integer> gone,
                      boolean poly, @Nullable Integer firstAway, @Nullable Integer lag,
                      String note) {

        /** True when this row is more than one species domesticated in more than one hemisphere. */
        public boolean inherited() {
            return origin.inherited();
        }

        /** The year it is first made anywhere outside its origin areas. */
        @Nullable
        public Integer leftHome() {
            return firstAway;
        }

        /** How long it took to leave home, in years. Null when it never did. */
        @Nullable
        public Integer lagYears() {
            return lag;
        }
    }

    /** Area codes are for the data; a player-facing sentence needs words. */
    private static final java.util.Map<String, String> AREA_NAMES = Map.ofEntries(
            Map.entry("NNA", "northern North America"), Map.entry("MNA", "middle North America"),
            Map.entry("CAC", "Central America and the Caribbean"),
            Map.entry("SAM", "South America"), Map.entry("EUR", "Europe"),
            Map.entry("EEU", "eastern Europe"), Map.entry("WAS", "western Asia"),
            Map.entry("NAF", "northern Africa"), Map.entry("WAF", "western Africa"),
            Map.entry("MSA", "middle and southern Africa"), Map.entry("EAF", "eastern Africa"),
            Map.entry("CAS", "central Asia"), Map.entry("SAS", "southern Asia"),
            Map.entry("EAS", "eastern Asia"), Map.entry("SEA", "south-eastern Asia"),
            Map.entry("AUS", "Australia, New Zealand and Melanesia"),
            Map.entry("PAC", "Micronesia and Polynesia"));

    public static String areaName(String area) {
        return AREA_NAMES.getOrDefault(area, area);
    }

    public static boolean americanArea(String area) {
        return "NNA".equals(area) || "MNA".equals(area) || "CAC".equals(area)
                || "SAM".equals(area);
    }

    private static volatile Spread instance;

    private final List<Row> rows;
    private final Map<String, Row> byId;
    private final Map<String, Row> byItem;
    private final List<String> areas;

    private Spread(JsonObject root) {
        List<Row> list = new ArrayList<>();
        Map<String, Row> byItem = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("rows")) {
            JsonObject o = e.getAsJsonObject();
            JsonObject orig = o.getAsJsonObject("origin");
            Origin origin = new Origin(strings(orig.get("areas")),
                    orig.has("year") && !orig.get("year").isJsonNull() ? orig.get("year").getAsInt()
                            : NEVER,
                    str(orig, "source"),
                    orig.has("parent") && !orig.get("parent").isJsonNull()
                            ? orig.get("parent").getAsString() : null);
            Row r = new Row(str(o, "id"), str(o, "kind"), str(o, "name"),
                    o.has("item") && !o.get("item").isJsonNull() ? o.get("item").getAsString()
                            : null,
                    str(o, "mark"), origin, ints(o, "made"), ints(o, "gone"),
                    o.has("poly") && o.get("poly").getAsBoolean(),
                    o.has("firstAway") && !o.get("firstAway").isJsonNull()
                            ? o.get("firstAway").getAsInt() : null,
                    o.has("lag") && !o.get("lag").isJsonNull() ? o.get("lag").getAsInt() : null,
                    str(o, "note"));
            list.add(r);
            if (r.item() != null) byItem.put(r.item().toLowerCase(Locale.ROOT), r);
        }
        this.rows = List.copyOf(list);
        Map<String, Row> m = new LinkedHashMap<>();
        for (Row r : list) {
            m.put(r.id(), r);
        }
        this.byId = Map.copyOf(m);
        this.byItem = Map.copyOf(byItem);
        this.areas = strings(root.getAsJsonArray("areas"));
    }

    public static Spread get() {
        Spread s = instance;
        if (s == null) {
            synchronized (Spread.class) {
                s = instance;
                if (s == null) {
                    s = load();
                    instance = s;
                }
            }
        }
        return s;
    }

    private static Spread load() {
        try (InputStream raw = Spread.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Spread(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    public List<Row> rows() {
        return rows;
    }

    public List<String> areas() {
        return areas;
    }

    @Nullable
    public Row row(String id) {
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** The row for a Minecraft item id, or null: this is the E18 half ("as items"). */
    @Nullable
    public Row forItem(String itemId) {
        return byItem.get(itemId.toLowerCase(Locale.ROOT));
    }

    /** The year a thing is first made in an area, or {@link #NEVER}. */
    public int first(String id, String area) {
        Row r = row(id);
        if (r == null) return NEVER;
        Integer y = r.made().get(area);
        return y == null ? NEVER : y;
    }

    /** True if the thing can be made or grown in the area in that year. */
    public boolean available(String id, String area, int year) {
        int y = first(id, area);
        if (y == NEVER) return false;
        Row r = row(id);
        Integer gone = r == null ? null : r.gone().get(area);
        return year >= y && (gone == null || year < gone);
    }

    /** True when the thing is in an area that is not where it comes from. */
    public boolean foreign(String id, String area) {
        Row r = row(id);
        return r != null && !r.origin().home(area);
    }

    /**
     * The reason a thing is or is not there, in one line, for a log or a tooltip. It names the
     * origin, because "potatoes: 1615" is a worse sentence than "potatoes: from the Andes, here
     * since 1615".
     */
    public String why(String id, String area, int year) {
        Row r = row(id);
        if (r == null) return id + ": not in the register";
        int y = first(id, area);
        if (y == NEVER) return r.name() + ": never in " + area;
        String from = r.origin().inherited()
                ? "a later form of " + r.origin().parent()
                : "from " + r.origin().areas().stream().map(Spread::areaName)
                        .collect(java.util.stream.Collectors.joining(" and "));
        if (year < y) {
            return r.name() + ": " + from + ", not in " + areaName(area) + " until " + y;
        }
        Integer gone = r.gone().get(area);
        if (gone != null && year >= gone) {
            return r.name() + ": " + from + ", here " + y + " to " + gone;
        }
        return r.name() + ": " + from + ", here since " + y;
    }

    /** The crops and animals an area has in a year, in register order. */
    public List<String> availableIn(String area, int year) {
        List<String> out = new ArrayList<>();
        for (Row r : rows) {
            if (available(r.id(), area, year)) out.add(r.id());
        }
        return out;
    }

    /** Rows that came from the Americas, which is what the exchange is about. */
    public List<Row> americanCrops() {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            if (!r.origin().inherited() && r.origin().american()) out.add(r);
        }
        return out;
    }

    public int count() {
        return rows.size();
    }

    public String summary() {
        return rows.size() + " crops and animals; " + americanCrops().size()
                + " with an American origin, from the exchange of " + EXCHANGE_YEAR;
    }

    // ---------------------------------------------------------------------------------------
    private static Map<String, Integer> ints(JsonObject o, String key) {
        Map<String, Integer> out = new LinkedHashMap<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonObject()) return Map.copyOf(out);
        for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
            if (x.getValue().isJsonNull()) continue;
            out.put(x.getKey(), x.getValue().getAsInt());
        }
        return Map.copyOf(out);
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || !e.isJsonArray()) return List.copyOf(out);
        for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}

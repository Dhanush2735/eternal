package com.eternalengine.money;

import com.eternalengine.civilization.WaysOfLife;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Product atlas, part B (step 19): is a product here, at this place and in this year?
 *
 * <p>{@code economy/products_world.json} (built by {@code worldfill/build_product_atlas.py}) lists about 130 products
 * in 14 families. For each of the 17 large areas of the ways-of-life survey it gives the year a product is first made
 * there, the year it is first brought there by trade, and the year it is gone (replaced). The answer, in this order:
 * <ol>
 *   <li>GONE when it was replaced there by that year;</li>
 *   <li>MADE HERE when it is made in the area by then, the people of the region do what it needs (farm, herd, live in
 *   towns, or anything but only hunt and gather: from the survey; after 1899 every place does; a need the survey
 *   cannot know about, because its date is older than the product there, counts as met where people trade), and one
 *   of each group of its inputs is here (made or brought);</li>
 *   <li>BROUGHT FROM FAR when it is brought there by then, or made elsewhere in the area while it cannot be made here
 *   but the people trade (they farm or herd at least commonly, or have towns);</li>
 *   <li>else NOT HERE YET, with the year it comes (if it ever does).</li>
 * </ol>
 * A place outside every survey region (the sea, small islands) takes the area of a rough box ({@link #boxArea}).
 *
 * <p>Merchants also sell the products that have a Minecraft item and are not one of their goods already: made here at
 * their price in days of work, brought from far at {@link #BROUGHT_MARKUP} times that (the journey), {@link #LOTS}
 * lots a day.
 */
public final class ProductAtlas {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String RESOURCE = "/data/eternalengine/eternal/economy/products_world.json";
    /** No year: never. */
    public static final int NEVER = Integer.MAX_VALUE;
    /** A product brought from far costs this many times its price made here. */
    public static final int BROUGHT_MARKUP = 2;
    /** Lots of one atlas product a merchant has in a Minecraft day. */
    public static final int LOTS = 2;
    /** A merchant carries at most this many atlas products, picked by who he is (merchants differ). */
    public static final int MERCHANT_MAX = 8;
    /** The years the checks walk through, for every survey region. */
    public static final int[] CHECK_YEARS = {-3000, -2000, -1000, 0, 500, 1000, 1300, 1500, 1650, 1750, 1850, 1899,
            1900, 1948, 2000};
    public static final List<String> STAPLES = List.of("food", "cloth", "fuel", "light");
    /** What people must do for a product to be made where they live; "settled": farm, herd or have towns. */
    public static final List<String> NEEDS = List.of("farm", "herd", "town", "settled");
    /** Inputs are followed this deep (a book needs paper, paper needs cloth...). */
    private static final int MAX_DEPTH = 8;

    public enum Status {
        MADE_HERE("made here"), BROUGHT("brought from far"), NOT_YET("not here yet"), GONE("gone");

        public final String words;

        Status(String words) {
            this.words = words;
        }

        /** True when people here can get it: made here or brought. */
        public boolean present() {
            return this == MADE_HERE || this == BROUGHT;
        }
    }

    /** One product: per area (NNA ... PAC) the year it is first made, first brought, and gone; absent means never. */
    public record Product(String id, String name, String family, Map<String, Integer> made, Map<String, Integer> brought,
                          Map<String, Integer> gone, String staple, List<String> needs, List<List<String>> inputs,
                          String item, int count, double days, List<String> sellers, String shortName, boolean rename,
                          boolean estimate, String note, String one, String many, String work, String workplace,
                          String workplaceName) {

        /** The kind of work that makes this, in plain words ("a smith", "a farmer"). */
        public String workWord() {
            for (int i = 0; i < com.eternalengine.people.Jobs.WORKS.length; i++) {
                if (com.eternalengine.people.Jobs.WORKS[i].equals(work)) {
                    return com.eternalengine.people.Jobs.PLAIN[i];
                }
            }
            return "maker";
        }

        /** "Pottery is made in a kiln, by a craftsman." */
        public String madeBy() {
            return name() + " is made in " + (workplaceName.isBlank() ? "a workshop" : workplaceName)
                    + ", by " + article(workWord());
        }

        private static String article(String w) {
            return (w.startsWith("a") || w.startsWith("e") || w.startsWith("i")
                    || w.startsWith("o") || w.startsWith("u") ? "an " : "a ") + w;
        }

        public int madeIn(String area) {
            return made.getOrDefault(area, NEVER);
        }

        public int broughtIn(String area) {
            return brought.getOrDefault(area, NEVER);
        }

        public int goneIn(String area) {
            return gone.getOrDefault(area, NEVER);
        }

        /** The first year people in the area have it at all (made or brought), or {@link #NEVER}. */
        public int firstIn(String area) {
            return Math.min(madeIn(area), broughtIn(area));
        }

        public boolean soldBy(String role) {
            return sellers.contains(role == null ? "" : role.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Where and when the question is asked: the area, the survey region (blank outside every region), the year, and
     * what the people there do: farm, herd, live in towns, and trade (they farm or herd at least commonly, or have
     * towns). After 1899, or with no reading, every place does everything.
     */
    public record Place(String area, String region, int year, boolean farm, boolean herd, boolean town, boolean trade,
                        @Nullable WaysOfLife.Reading reading) {
        /** The first need people here do not meet ("farm"), or null when they meet them all. */
        @Nullable
        public String missing(List<String> needs) {
            for (String n : needs) {
                if ((n.equals("farm") && !farm) || (n.equals("herd") && !herd) || (n.equals("town") && !town)
                        || (n.equals("settled") && !(farm || herd || town))) {
                    return n;
                }
            }
            return null;
        }
    }

    /** Why a need is not met, in plain words. */
    static String unmet(String need) {
        return switch (need) {
            case "farm" -> "people here do not farm";
            case "herd" -> "people here do not keep herds";
            case "town" -> "there are no towns here";
            case "settled" -> "people here live by hunting and gathering";
            default -> "people here do not " + need;
        };
    }

    /** The answer for one product at a place: its status, the year that status began (or comes), and why. */
    public record Answer(Product product, Status status, int since, String why) {
    }

    private static volatile ProductAtlas instance;

    private final List<Product> products;
    private final Map<String, Product> byId;
    private final Map<String, String> areaNames;
    private final Map<String, String> worldToArea;
    private final List<String> families;
    private final List<String> problems;
    private final String source;

    private ProductAtlas(List<Product> products, Map<String, String> areaNames, List<String> families,
                         List<String> problems, String source) {
        this.products = List.copyOf(products);
        Map<String, Product> m = new LinkedHashMap<>();
        for (Product p : products) {
            if (m.put(p.id(), p) != null) {
                problems.add("product " + p.id() + " is listed twice");
            }
        }
        this.byId = Collections.unmodifiableMap(m);
        this.areaNames = Collections.unmodifiableMap(new LinkedHashMap<>(areaNames));
        Map<String, String> w = new HashMap<>();
        areaNames.forEach((code, name) -> w.put(name, code));
        this.worldToArea = Collections.unmodifiableMap(w);
        this.families = List.copyOf(families);
        this.problems = List.copyOf(problems);
        this.source = source;
    }

    public static ProductAtlas get() {
        ProductAtlas a = instance;
        if (a == null) {
            synchronized (ProductAtlas.class) {
                if (instance == null) {
                    instance = load();
                }
                a = instance;
            }
        }
        return a;
    }

    /** Read the atlas again (/ee reload). */
    public static ProductAtlas reload() {
        synchronized (ProductAtlas.class) {
            instance = load();
            return instance;
        }
    }

    private static ProductAtlas load() {
        List<String> problems = new ArrayList<>();
        List<Product> out = new ArrayList<>();
        Map<String, String> areas = new LinkedHashMap<>();
        List<String> families = new ArrayList<>();
        try (InputStream in = ProductAtlas.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                problems.add("missing " + RESOURCE);
            } else {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("areas")) {
                    JsonObject o = e.getAsJsonObject();
                    areas.put(o.get("id").getAsString(), o.get("name").getAsString());
                }
                for (JsonElement e : root.getAsJsonArray("families")) {
                    families.add(e.getAsString());
                }
                for (JsonElement e : root.getAsJsonArray("products")) {
                    JsonObject o = e.getAsJsonObject();
                    try {
                        out.add(product(o));
                    } catch (Exception ex) {
                        problems.add(str(o, "id") + ": " + ex);
                    }
                }
            }
        } catch (Exception e) {
            problems.add(RESOURCE + ": " + e);
        }
        ProductAtlas a = new ProductAtlas(out, areas, families, problems, RESOURCE);
        LOGGER.info("[Money] product atlas: {} products in {} areas{}", a.products.size(), a.areaNames.size(),
                a.problems.isEmpty() ? "" : "; problems: " + a.problems);
        return a;
    }

    private static Product product(JsonObject o) {
        List<String> needs = strings(o, "needs");
        List<List<String>> inputs = new ArrayList<>();
        if (o.has("inputs")) {
            for (JsonElement g : o.getAsJsonArray("inputs")) {
                List<String> alt = new ArrayList<>();
                for (JsonElement x : g.getAsJsonArray()) {
                    alt.add(x.getAsString());
                }
                inputs.add(List.copyOf(alt));
            }
        }
        return new Product(str(o, "id"), str(o, "name"), str(o, "family"), years(o, "made"), years(o, "brought"),
                years(o, "gone"), str(o, "staple"), needs, List.copyOf(inputs), str(o, "item"),
                o.has("count") ? o.get("count").getAsInt() : 1, o.has("days") ? o.get("days").getAsDouble() : 0d,
                o.has("sellers") ? strings(o, "sellers") : List.of("merchant"),
                o.has("short") ? str(o, "short") : str(o, "name"), o.has("rename") && o.get("rename").getAsBoolean(),
                o.has("estimate") && o.get("estimate").getAsBoolean(), str(o, "note"),
                o.has("one") ? str(o, "one") : lower(o.has("short") ? str(o, "short") : str(o, "name")),
                o.has("many") ? str(o, "many") : lower(o.has("short") ? str(o, "short") : str(o, "name")),
                str(o, "work"), str(o, "workplace"), str(o, "workplaceName"));
    }

    /** Step 19g: "Iron axe" as "iron axe" (a buyer's words when the table gives none). */
    private static String lower(String s) {
        return s == null || s.isEmpty() ? "" : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static Map<String, Integer> years(JsonObject o, String key) {
        Map<String, Integer> m = new HashMap<>();
        if (o.has(key)) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
                m.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        return Collections.unmodifiableMap(m);
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) {
            JsonArray a = o.getAsJsonArray(key);
            for (JsonElement e : a) {
                out.add(e.getAsString().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    // ---------------------------------------------------------------------------------------------- reading

    public List<Product> products() {
        return products;
    }

    @Nullable
    public Product product(String id) {
        return byId.get(id);
    }

    /** True when the pack loaded with nothing to complain about. */
    public boolean loaded() {
        return problems.isEmpty();
    }

    /** Why the pack did not load, or null when it did. */
    @Nullable
    public String problem() {
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    /**
     * The product a player meant by these words: an exact id, then an exact name, then the first
     * product whose name contains them. Null when nothing matches. Commands need this, because a
     * player types "wheat", not "wheat_and_barley".
     */
    @Nullable
    public Product byName(String words) {
        if (words == null || words.isBlank()) {
            return null;
        }
        String q = words.trim().toLowerCase(Locale.ROOT);
        String under = q.replace(' ', '_');
        Product exact = byId.get(under);
        if (exact != null) {
            return exact;
        }
        for (Product p : products) {
            if (p.name().toLowerCase(Locale.ROOT).equals(q)) {
                return p;
            }
        }
        for (Product p : products) {
            if (p.name().toLowerCase(Locale.ROOT).contains(q)) {
                return p;
            }
        }
        return null;
    }

    public List<String> problems() {
        return problems;
    }

    public String source() {
        return source;
    }

    /** Area codes in order (NNA ... PAC). */
    public List<String> areas() {
        return new ArrayList<>(areaNames.keySet());
    }

    public String areaName(String code) {
        return areaNames.getOrDefault(code, code);
    }

    /** The area of a survey "world" name, or null. */
    @Nullable
    public String areaOfWorld(String world) {
        return worldToArea.get(world);
    }

    public String summary() {
        int items = 0;
        for (Product p : products) {
            if (!p.item().isBlank()) {
                items++;
            }
        }
        return products.size() + " products in " + families.size() + " families and " + areaNames.size() + " areas ("
                + items + " sold as items)";
    }

    // ------------------------------------------------------------------------------------------------ places

    /** Steps tried around a point on the coast that the survey's outlines just miss (Bombay): up to a degree away. */
    private static final double[][] NEAR = {{0.25, 0}, {-0.25, 0}, {0, 0.25}, {0, -0.25}, {0.5, 0}, {-0.5, 0}, {0, 0.5},
            {0, -0.5}, {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}, {-0.5, -0.5}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * The place for a point: its survey region and area, and the survey reading. A point just off the survey's
     * outlines (a coast) takes the region a step away; a point far from every region (the sea) takes a rough box and
     * meets every need.
     */
    public Place place(double lat, double lon, int year) {
        WaysOfLife w = WaysOfLife.get();
        Optional<WaysOfLife.Place> wp = Double.isNaN(lat) || Double.isNaN(lon) ? Optional.empty() : w.placeAt(lat, lon);
        for (int i = 0; wp.isEmpty() && !Double.isNaN(lat) && !Double.isNaN(lon) && i < NEAR.length; i++) {
            wp = w.placeAt(lat + NEAR[i][0], lon + NEAR[i][1]);
        }
        String area = wp.map(x -> worldToArea.get(x.world())).orElse(null);
        if (area == null) {
            area = boxArea(lat, lon);
        }
        WaysOfLife.Reading r = wp.isEmpty() ? null : w.readingFor(wp.get().region(), year).orElse(null);
        return place(area, wp.map(WaysOfLife.Place::region).orElse(""), year, r);
    }

    /** A place from a survey reading (null: after 1899 or outside the survey, so every need is met). */
    public static Place place(String area, String region, int year, @Nullable WaysOfLife.Reading r) {
        if (r == null) {
            return new Place(area, region, year, true, true, true, true, null);
        }
        int farming = Math.max(level(r.shiftingFarming()), level(r.intensiveFarming()));
        int herding = level(r.herding());
        boolean towns = r.towns() != 0;
        return new Place(area, region, year, farming >= 1, herding >= 1, towns,
                farming >= 2 || herding >= 2 || r.towns() == 1, r);
    }

    /** A survey level, with unknown (-1) read as "minimal". */
    private static int level(int v) {
        return v < 0 ? 1 : v;
    }

    /** A rough area for a point outside every survey region: the sea and small islands. */
    public static String boxArea(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return "SAS";
        }
        if (lon < -130 || lon >= 170) {
            if (lat >= 50) {
                return lon >= 170 ? "EEU" : "NNA";
            }
            return lat <= -32 ? "AUS" : "PAC";
        }
        if (lon < -25) {
            if (lat >= 55) {
                return "NNA";
            }
            if (lat >= 24) {
                return "MNA";
            }
            return lat >= 13 || (lat >= 7 && lon < -77) ? "CAC" : "SAM";
        }
        if (lat >= 55) {
            return lon < 25 ? "EUR" : "EEU";
        }
        if (lon < 40 && lat >= 36) {
            return lon < 26 ? "EUR" : lat >= 43 ? "EEU" : "WAS";
        }
        if (lat < 37 && lon < 52 && (lon < 34 || lat < 12.5)) {
            if (lat >= 18 || (lat >= 9 && lon >= 22 && lon < 36)) {
                return "NAF";
            }
            if (lat >= 0 && lon < 22) {
                return "WAF";
            }
            return lon >= 30 && lat >= -27 ? "EAF" : "MSA";
        }
        if (lon < 63 && lat < 43) {
            return "WAS";
        }
        if (lat >= 45) {
            return lon < 50 ? "EEU" : lon < 90 ? "CAS" : "EAS";
        }
        if (lon < 92.5) {
            if (lat >= 37 || lon < 60) {
                return "CAS";
            }
            return lat >= 30.5 && lon >= 78 ? "EAS" : "SAS";
        }
        if (lon < 98) {
            return lat >= 28 ? "EAS" : "SEA";
        }
        if (lon < 150) {
            if (lat >= 21.5 || (lat >= 18 && lon >= 108 && lon < 117)) {
                return "EAS";
            }
            if (lat >= -11 && lon < 141) {
                return "SEA";
            }
            return lat >= 0 ? "PAC" : "AUS";
        }
        if (lat >= 30) {
            return "EAS";
        }
        return lat >= 0 ? "PAC" : "AUS";
    }

    // ----------------------------------------------------------------------------------------------- answers

    public static String yearText(int y) {
        return y == NEVER ? "never" : y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y);
    }

    /** Is this product here? See the class comment for the order of the rules. */
    public Answer status(Product p, Place at) {
        return status(p, at, 0);
    }

    private Answer status(Product p, Place at, int depth) {
        int y = at.year();
        String a = at.area();
        int gone = p.goneIn(a);
        int made = p.madeIn(a);
        int brought = p.broughtIn(a);
        if (gone <= y) {
            return new Answer(p, Status.GONE, gone, "replaced in " + yearText(gone));
        }
        if (made <= y) {
            String miss = at.missing(p.needs());
            if (miss != null && at.trade() && at.reading() != null && at.reading().sliceYear() < made) {
                // The survey date is older than the product here, so it cannot know (horses in Mexico in 1600).
                miss = null;
            }
            String why = miss == null ? null : unmet(miss);
            if (why == null && depth < MAX_DEPTH) {
                String lacking = lacking(p, at, depth);
                if (lacking != null) {
                    why = "there is no " + lacking + " here to make it";
                }
            }
            if (why == null) {
                return new Answer(p, Status.MADE_HERE, made, "");
            }
            if (brought <= y) {
                return new Answer(p, Status.BROUGHT, brought, why);
            }
            if (at.trade()) {
                return new Answer(p, Status.BROUGHT, made, why + "; it is made elsewhere in the land");
            }
            return new Answer(p, Status.NOT_YET, NEVER, why + ", and they trade little");
        }
        if (brought <= y) {
            return new Answer(p, Status.BROUGHT, brought, "");
        }
        int next = Math.min(made, brought);
        return new Answer(p, Status.NOT_YET, next, next == NEVER ? "never comes" : "comes in " + yearText(next));
    }

    /** The name of the first input group with nothing here (made or brought), or null when every group has one. */
    @Nullable
    private String lacking(Product p, Place at, int depth) {
        for (List<String> group : p.inputs()) {
            boolean any = false;
            for (String id : group) {
                Product in = byId.get(id);
                if (in != null && status(in, at, depth + 1).status().present()) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                Product first = byId.get(group.get(0));
                return first == null ? group.get(0) : first.name().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ step 44 part D: makers

    /**
     * Step 44 part D: who makes this here, and how many of them there are.
     *
     * <p>The survey for this step found the mod had sellers, buyers and workers but **no makers**
     * — nothing turned an input into an output. This is the missing half. The workplace comes
     * from the product, and the number of people who do the work comes from the jobs register,
     * which already knows, for any place and year, what share of working people are smiths or
     * farmers or clerks.
     *
     * <p>{@code people} is how many working people there are here; the share is applied to it.
     * A share of two in the hundred among twenty-five foragers is nobody, and this says so
     * rather than inventing a smith.
     */
    public record Maker(Product product, String work, String word, String workplace, int share,
                        int people) {

        /** True when there is nobody here doing this work. */
        public boolean none() {
            return people <= 0;
        }

        /**
         * "Pottery is made in a kiln by a craftsman; about two people here do that work."
         * Or, where there is nobody: "there is nobody here who can make pottery."
         */
        public String sentence() {
            if (none()) {
                return "there is nobody here who could make " + product.name().toLowerCase(java.util.Locale.ROOT);
            }
            String howMany = people == 1 ? "one person here" : "about " + people + " people here";
            return product.name() + " is made in " + (workplace.isBlank() ? "a workshop" : workplace)
                    + " by " + Product.article(word) + "; " + howMany + " do that work";
        }
    }

    /** Who makes this product at this place, from the jobs register. */
    public Maker maker(Product p, double lat, double lon, int year, boolean urban, int people) {
        return maker(p, lat, lon, year, urban, null, people);
    }

    /**
     * Who makes this product here, and how many of them there are, from the jobs register.
     *
     * <p>{@code levels} is the ArchaeoGLOBE reading for the place — {@code {foraging, farming,
     * pastoral}}, each 0 to 3 — or {@code null} when it is not known. The jobs in this mod hang
     * off the subsistence map, not off a list: a place where nobody farms has no farmers,
     * whatever the row for its era says.
     */
    public Maker maker(Product p, double lat, double lon, int year, boolean urban, int[] levels,
                       int people) {
        String work = p.work() == null || p.work().isBlank() ? "craft" : p.work();
        int share = com.eternalengine.people.Jobs.get().shareOf(lat, lon, year, urban, work, levels);
        // Floored, not rounded: half a person is nobody, and a scene that wants a smith in a
        // camp of ten must be told there is none rather than given one who was rounded up.
        return new Maker(p, work, p.workWord(), p.workplaceName(), share,
                people <= 0 ? 0 : (int) (people * share / 100.0));
    }

    /** The ArchaeoGLOBE triple for a place, or null when the survey does not cover it. */
    public static int[] levelsOf(@Nullable Place at) {
        if (at == null || at.reading() == null) {
            return null;
        }
        WaysOfLife.Reading r = at.reading();
        return new int[]{level(r.foraging()),
                Math.max(level(r.shiftingFarming()), level(r.intensiveFarming())),
                level(r.herding())};
    }

    /** Who makes this product at this point and year, in the town or country the point lies in. */
    public Maker maker(Product p, double lat, double lon, int year, Place at, int people) {
        return maker(p, lat, lon, year, at.town(), levelsOf(at), people);
    }

    /**
     * The chain in words: what this is made from, and what that is made from, to the raw
     * material. "Flour is made from wheat." Nothing here is invented; it is the inputs walked
     * back to something with none.
     */
    public String chain(Product p) {
        if (p.inputs().isEmpty()) {
            return p.name() + " is not made from anything else";
        }
        StringBuilder sb = new StringBuilder(p.name()).append(" is made from ");
        List<String> parts = new ArrayList<>();
        for (List<String> group : p.inputs()) {
            List<String> names = new ArrayList<>();
            for (String id : group) {
                Product in = byId.get(id);
                names.add(in == null ? id : in.name().toLowerCase(java.util.Locale.ROOT));
            }
            parts.add(String.join(" or ", names));
        }
        return sb.append(String.join(", and from ", parts)).append(".").toString();
    }

    /** Every product this one is eventually made from, walking the whole chain back. */
    public java.util.Set<String> allInputs(Product p) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        java.util.Deque<String> todo = new java.util.ArrayDeque<>();
        for (List<String> group : p.inputs()) {
            todo.addAll(group);
        }
        int guard = 0;
        while (!todo.isEmpty() && guard++ < 4000) {
            String id = todo.poll();
            if (!out.add(id)) {
                continue;
            }
            Product in = byId.get(id);
            if (in != null) {
                for (List<String> group : in.inputs()) {
                    for (String m : group) {
                        if (!out.contains(m)) {
                            todo.add(m);
                        }
                    }
                }
            }
        }
        return out;
    }

    public List<Answer> all(Place at) {
        List<Answer> out = new ArrayList<>();
        for (Product p : products) {
            out.add(status(p, at));
        }
        return out;
    }

    /**
     * What a seller of this role can sell here: products with an item, made here or brought, one per item (made here
     * before brought; the later product before the earlier, so a kerosene lamp before an oil lamp).
     */
    public List<Answer> forSale(String role, double lat, double lon, int year) {
        Place at = place(lat, lon, year);
        Map<String, Answer> byItem = new LinkedHashMap<>();
        for (Product p : products) {
            if (p.item().isBlank() || p.days() <= 0 || !p.soldBy(role)) {
                continue;
            }
            Answer ans = status(p, at);
            if (!ans.status().present()) {
                continue;
            }
            Answer had = byItem.get(p.item());
            if (had == null || (ans.status() == Status.MADE_HERE && had.status() != Status.MADE_HERE)
                    || (ans.status() == had.status() && ans.since() >= had.since())) {
                byItem.put(p.item(), ans);
            }
        }
        return new ArrayList<>(byItem.values());
    }

    /**
     * Up to {@code max} of these answers, picked by a seller's number (the same seller always carries the same kinds),
     * kept in atlas order.
     */
    public static List<Answer> carried(List<Answer> all, long salt, int max) {
        if (all.size() <= max) {
            return all;
        }
        List<Answer> sorted = new ArrayList<>(all);
        sorted.sort(java.util.Comparator.comparingLong(a -> mix(salt ^ a.product().id().hashCode())));
        List<Answer> keep = sorted.subList(0, Math.max(0, max));
        List<Answer> out = new ArrayList<>();
        for (Answer a : all) {
            if (keep.contains(a)) {
                out.add(a);
            }
        }
        return out;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /** "Southern Asia (Southern India), 1948: 95 made here, 15 brought from far, 15 not here yet, 4 gone." */
    public String summaryAt(Place at) {
        int[] n = new int[Status.values().length];
        for (Answer a : all(at)) {
            n[a.status().ordinal()]++;
        }
        return areaName(at.area()) + (at.region().isBlank() ? "" : " (" + at.region() + ")") + ", " + yearText(at.year())
                + ": " + n[0] + " made here, " + n[1] + " brought from far, " + n[2] + " not here yet, " + n[3] + " gone";
    }

    /** The staples here: the first food, cloth, fuel and light people can get ("food: wheat and barley, rice"). */
    public String staplesAt(Place at) {
        List<String> parts = new ArrayList<>();
        for (String s : STAPLES) {
            List<String> names = new ArrayList<>();
            for (Answer a : all(at)) {
                if (s.equals(a.product().staple()) && a.status().present()) {
                    names.add(a.product().name().toLowerCase(Locale.ROOT));
                }
            }
            parts.add(s + ": " + (names.isEmpty() ? "NONE" : String.join(", ", names)));
        }
        return String.join("; ", parts);
    }

    // ------------------------------------------------------------------------------------------------ checks

    /** How many region and year pairs {@link #checkStaples} walks. */
    public static int stapleChecks() {
        return WaysOfLife.get().regionList().size() * CHECK_YEARS.length;
    }

    /** Every survey region has food, cloth, fuel and light in every check year. Null when all is well. */
    @Nullable
    public String checkStaples() {
        List<WaysOfLife.Place> regions = WaysOfLife.get().regionList();
        if (regions.size() < 100) {
            return "the ways-of-life survey has only " + regions.size() + " regions";
        }
        List<String> bad = new ArrayList<>();
        for (WaysOfLife.Place rp : regions) {
            String area = worldToArea.get(rp.world());
            if (area == null) {
                bad.add(rp.region() + ": unknown area \"" + rp.world() + "\"");
                continue;
            }
            for (int y : CHECK_YEARS) {
                Place at = place(area, rp.region(), y, WaysOfLife.get().readingFor(rp.region(), y).orElse(null));
                for (String s : STAPLES) {
                    boolean has = false;
                    for (Product p : products) {
                        if (s.equals(p.staple()) && status(p, at).status().present()) {
                            has = true;
                            break;
                        }
                    }
                    if (!has) {
                        bad.add(rp.region() + " in " + yearText(y) + ": no " + s);
                    }
                }
            }
        }
        return bad.isEmpty() ? null : bad.size() + " gaps: " + String.join("; ", bad.subList(0, Math.min(6, bad.size())));
    }

    /**
     * The data is in order: at least 100 products, known families and areas, years in range, gone only after it came,
     * every staple kind present; and no product is here before its first year, nor missing at its made year (when the
     * people meet its needs), in any area.
     */
    @Nullable
    public String checkOrder() {
        List<String> bad = new ArrayList<>(problems);
        if (products.size() < 100) {
            bad.add("only " + products.size() + " products");
        }
        if (areaNames.size() != 17) {
            bad.add(areaNames.size() + " areas, not 17");
        }
        for (String s : STAPLES) {
            if (products.stream().noneMatch(p -> s.equals(p.staple()))) {
                bad.add("no " + s + " product");
            }
        }
        for (Product p : products) {
            if (!families.contains(p.family())) {
                bad.add(p.id() + ": unknown family " + p.family());
            }
            if (p.name().isBlank()) {
                bad.add(p.id() + ": no name");
            }
            for (String n : p.needs()) {
                if (!NEEDS.contains(n)) {
                    bad.add(p.id() + ": unknown need " + n);
                }
            }
            if (!p.staple().isBlank() && !STAPLES.contains(p.staple())) {
                bad.add(p.id() + ": unknown staple " + p.staple());
            }
            if (!p.item().isBlank() && (p.days() <= 0 || p.count() < 1 || p.count() > 64)) {
                bad.add(p.id() + ": an item needs days of work and a count of 1 to 64");
            }
            for (Map<String, Integer> m : List.of(p.made(), p.brought(), p.gone())) {
                for (Map.Entry<String, Integer> e : m.entrySet()) {
                    if (!areaNames.containsKey(e.getKey())) {
                        bad.add(p.id() + ": unknown area " + e.getKey());
                    }
                    if (e.getValue() < -12000 || e.getValue() > 2100) {
                        bad.add(p.id() + ": year " + e.getValue() + " out of range in " + e.getKey());
                    }
                }
            }
            for (String a : areaNames.keySet()) {
                int first = p.firstIn(a);
                if (p.goneIn(a) != NEVER && (first == NEVER || p.goneIn(a) <= first)) {
                    bad.add(p.id() + ": gone in " + a + " before it came");
                }
                if (first != NEVER && first > -12000) {
                    Answer before = status(p, place(a, "", first - 1, null));
                    if (before.status() != Status.NOT_YET && before.status() != Status.GONE) {
                        bad.add(p.id() + ": here in " + a + " in " + yearText(first - 1) + ", before its first year");
                    }
                }
                int made = p.madeIn(a);
                if (made != NEVER && made < p.goneIn(a) && status(p, place(a, "", made, null)).status() != Status.MADE_HERE) {
                    bad.add(p.id() + ": not made in " + a + " in its year " + yearText(made));
                }
            }
        }
        return bad.isEmpty() ? null : bad.size() + " problems: " + String.join("; ", bad.subList(0, Math.min(6, bad.size())));
    }

    /**
     * Every input group of a product has one product made or brought (and not gone) in the area by the year it is made
     * there.
     */
    @Nullable
    public String checkInputs() {
        List<String> bad = new ArrayList<>();
        for (Product p : products) {
            for (List<String> group : p.inputs()) {
                for (String id : group) {
                    if (!byId.containsKey(id)) {
                        bad.add(p.id() + ": unknown input " + id);
                    }
                }
            }
            for (Map.Entry<String, Integer> e : p.made().entrySet()) {
                for (List<String> group : p.inputs()) {
                    int first = NEVER;
                    for (String id : group) {
                        Product in = byId.get(id);
                        if (in != null && in.goneIn(e.getKey()) > e.getValue()) {
                            first = Math.min(first, in.firstIn(e.getKey()));
                        }
                    }
                    if (first > e.getValue()) {
                        bad.add(p.id() + " in " + e.getKey() + " in " + yearText(e.getValue()) + " needs one of " + group
                                + " (there from " + yearText(first) + ")");
                    }
                }
            }
        }
        return bad.isEmpty() ? null : bad.size() + " problems: " + String.join("; ", bad.subList(0, Math.min(6, bad.size())));
    }

    /** How many input links {@link #checkInputs} looks at (product, area, input group). */
    public int inputLinks() {
        int n = 0;
        for (Product p : products) {
            n += p.made().size() * p.inputs().size();
        }
        return n;
    }

    /**
     * The plan's examples, at real places with the survey: Harappa in 2500 BCE makes wheat, cotton, fired bricks, copper
     * and bronze, and has no iron, coins, paper, sugar, chillies or tea yet; Rome in 100 has pepper and silk brought
     * from far, no paper or sugar; London in 1850 makes newspapers, gas light, machines, beer and bread, brings tea and
     * cotton, and has no electric light, telephone, cars or plastics; Hyderabad in 1948 brings kerosene, petrol and
     * radios, and has no television, nylon or plastics yet. Null when all is well.
     */
    @Nullable
    public String selfTest() {
        if (!problems.isEmpty()) {
            return "the atlas has problems: " + problems;
        }
        Object[][] cases = {
                {"Harappa", 30.63, 72.86, -2500, "MADE_HERE", "wheat cotton fired_brick copper bronze"},
                {"Harappa", 30.63, 72.86, -2500, "NOT_YET", "iron coins paper sugar chillies tea"},
                {"Rome", 41.89, 12.49, 100, "BROUGHT", "spices silk"},
                {"Rome", 41.89, 12.49, 100, "NOT_YET", "paper sugar"},
                {"London", 51.51, -0.13, 1850, "MADE_HERE", "newspaper gas_light machines beer bread"},
                {"London", 51.51, -0.13, 1850, "BROUGHT", "tea cotton"},
                {"London", 51.51, -0.13, 1850, "NOT_YET", "electricity telephone car plastics"},
                {"Hyderabad", 17.3616, 78.4747, 1948, "BROUGHT", "kerosene petrol radio"},
                {"Hyderabad", 17.3616, 78.4747, 1948, "NOT_YET", "television nylon plastics"},
        };
        List<String> bad = new ArrayList<>();
        for (Object[] c : cases) {
            Place at = place((Double) c[1], (Double) c[2], (Integer) c[3]);
            Status want = Status.valueOf((String) c[4]);
            for (String id : ((String) c[5]).split(" ")) {
                Product p = byId.get(id);
                if (p == null) {
                    bad.add("no product " + id);
                    continue;
                }
                Answer a = status(p, at);
                if (a.status() != want) {
                    bad.add(c[0] + " " + yearText((Integer) c[3]) + ": " + id + " is " + a.status().words + ", not "
                            + want.words + (a.why().isBlank() ? "" : " (" + a.why() + ")") + " [" + at.area() + "]");
                }
            }
        }
        if (!"SAS".equals(place(17.3616, 78.4747, 1948).area()) || !"EUR".equals(place(51.51, -0.13, 1850).area())) {
            bad.add("Hyderabad should be in Southern Asia and London in Europe");
        }
        if (!"PAC".equals(boxArea(-17.6, -149.5)) || !"SAS".equals(boxArea(10, 75)) || !"AUS".equals(boxArea(-40, 175))
                || !"MNA".equals(boxArea(35, -60))) {
            bad.add("the boxes for the sea: Tahiti Pacific, the Arabian Sea Southern Asia, New Zealand Australia");
        }
        return bad.isEmpty() ? null : String.join("; ", bad);
    }
}

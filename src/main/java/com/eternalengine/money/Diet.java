package com.eternalengine.money;

import com.eternalengine.money.ProductAtlas.Answer;
import com.eternalengine.money.ProductAtlas.Place;
import com.eternalengine.money.ProductAtlas.Product;
import com.eternalengine.money.ProductAtlas.Status;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 44 part E6: what a place eats.
 *
 * <p>The atlas knows 539 products and when each of them reaches a place, and part E knows what
 * happens when one of them stops arriving. What neither knew is that a place with wheat, lentils,
 * ghee and chillies has a <i>meal</i> to describe, while a place with wheat alone does not — and
 * that the difference is not a date. It is which parts of a meal are filled.
 *
 * <p>So every food is given a seat at the table. The seats are the ones a meal actually has — the
 * grain, the pulse, the greens, the fruit, the meat or fish, the fat it is cooked in, what sharpens
 * it, the sweet, the seasoning, the drink — and a food may hold more than one (coconut is a fat and
 * a fruit; yoghurt is a protein and a sour). They are hand-written, because no register carries
 * them, and they ship in a pack so this class and the table that wrote it cannot drift apart.
 *
 * <p>Which food sits in a seat is <b>not</b> hand-written. It is read from the atlas: whatever this
 * place can get in this year, with what is made here preferred over what is brought from far. A
 * place's diet therefore changes as the world does — maize arrives in Italy and takes the seat of
 * the grain alongside wheat, and the same code that says so says when.
 *
 * <p>An empty seat is a fact about the place, not a gap in the table. A meal is only called a meal
 * at all when the seats in {@code bareMinimum} are filled; below that the answer is honestly "there
 * is nothing here to speak of", which for a steppe in 4000 BCE is the truth.
 */
public final class Diet {

    public static final String RESOURCE = "/data/eternalengine/eternal/food/seats.json.gz";

    /**
     * How many foods to name for one seat. Three: the thing itself, and the two others if there
     * are two. Two was too few — it cut the tomato out of Rome in 1550, where it had arrived six
     * years before, and cut the sweet potato out of Tahiti in 1300, which is the one place in the
     * Pacific it most matters that it appears.
     */
    public static final int PER_SEAT = 3;

    private static volatile Diet instance;

    /** Seat id -> the food ids that can fill it, in the order a meal is described in. */
    private final Map<String, List<String>> bySeat;
    private final List<String> order;
    private final Map<String, String> seatWords;
    private final List<String> bareMinimum;
    private final String problem;
    private final int foods;

    /**
     * One food at the table.
     *
     * @param id       the product id
     * @param name     its name, as the atlas writes it
     * @param seat     which part of the meal it is
     * @param madeHere whether this place grows or makes it, rather than buying it from far
     * @param since    the year it reached this place
     * @param tilled   whether it is grown or kept, rather than hunted, fished or gathered
     * @param used     whether an earlier seat at this meal already named it
     */
    public record Serving(String id, String name, String seat, boolean madeHere, int since,
                          boolean tilled, boolean used) {
    }

    /**
     * Whether two names are the same food. True when they share a word that is not a joiner --
     * "breadfruit" in "taro, yams and breadfruit", "ghee" in "clarified butter (ghee)" and "milk,
     * butter and ghee", "pepper" in "spices (pepper, cinnamon, cloves)" and "black pepper".
     */
    static boolean sameFood(String a, String b) {
        return !common(words(a), words(b)).isEmpty();
    }

    private static final List<String> JOINERS = List.of("and", "or", "the", "of", "a", "in", "with");

    /** The meaningful words of a product name, lower case. */
    private static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        for (String w : name.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (w.length() > 2 && !JOINERS.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private static List<String> common(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>();
        for (String w : a) {
            if (b.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * The better of two foods at one seat.
     *
     * <p>Cultivated outranks <i>made here</i>, and that is deliberate even though it sounds
     * backwards: a place that has to bring its grain in by boat is still a place that eats grain,
     * not a place that eats acorns. Putting "made here" first gave Tahiti acorns instead of the
     * sweet potato that reached it in 1210, and put acorns into Tenochtitlan, because every
     * register dates a gathered food as present from the beginning of the world.
     */
    private static int better(Serving a, Serving b) {
        if (a.used() != b.used()) {
            return a.used() ? 1 : -1;
        }
        if (a.tilled() != b.tilled()) {
            return a.tilled() ? -1 : 1;
        }
        if (a.madeHere() != b.madeHere()) {
            return a.madeHere() ? -1 : 1;
        }
        return Integer.compare(a.since(), b.since());
    }

    /**
     * The meal at a place in a year.
     *
     * @param place    where and when
     * @param seats    seat id -> what fills it, best first. Seats with nothing in them are absent,
     *                 not empty: that is the whole point of the record.
     * @param thin     true when the seats in {@code bareMinimum} are not all filled, so this is not
     *                 really a meal but the little there is
     * @param unmapped true when the ways-of-life survey has no region here, so the answer
     *                 describes the whole area rather than this place
     */
    public record Meal(Place place, Map<String, List<Serving>> seats, boolean thin, boolean unmapped) {

        /** Whether the survey reaches this point at all. */
        public boolean mapped() {
            return !unmapped;
        }

        /** What fills one seat, or an empty list. */
        public List<Serving> at(String seat) {
            return seats.getOrDefault(seat, List.of());
        }

        /** Every food named at this table, seat by seat, with no repeats. */
        public List<Serving> all() {
            List<Serving> out = new ArrayList<>();
            for (List<Serving> s : seats.values()) {
                for (Serving v : s) {
                    boolean dupe = false;
                    for (Serving had : out) {
                        if (had.id().equals(v.id())) {
                            dupe = true;
                            break;
                        }
                    }
                    if (!dupe) {
                        out.add(v);
                    }
                }
            }
            return out;
        }
    }

    private Diet(Map<String, List<String>> bySeat, List<String> order, Map<String, String> seatWords,
                 List<String> bareMinimum, String problem, int foods) {
        this.bySeat = bySeat;
        this.order = order;
        this.seatWords = seatWords;
        this.bareMinimum = bareMinimum;
        this.problem = problem;
        this.foods = foods;
    }

    // -------------------------------------------------------------------------------------------------- reading

    public static Diet get() {
        Diet d = instance;
        if (d == null) {
            synchronized (Diet.class) {
                d = instance;
                if (d == null) {
                    d = load();
                    instance = d;
                }
            }
        }
        return d;
    }

    /** Forget the pack and read it again. */
    public static Diet reload() {
        synchronized (Diet.class) {
            instance = load();
            return instance;
        }
    }

    private static Diet load() {
        Map<String, List<String>> bySeat = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        Map<String, String> seatWords = new LinkedHashMap<>();
        List<String> bare = new ArrayList<>();
        try (InputStream raw = Diet.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new Diet(Map.of(), List.of(), Map.of(), List.of(),
                        "resource " + RESOURCE + " is absent", 0);
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            if (root.has("order")) {
                for (JsonElement e : root.getAsJsonArray("order")) {
                    order.add(e.getAsString());
                }
            }
            if (root.has("seatWords")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("seatWords").entrySet()) {
                    seatWords.put(e.getKey(), e.getValue().getAsString());
                }
            }
            if (root.has("bareMinimum")) {
                for (JsonElement e : root.getAsJsonArray("bareMinimum")) {
                    bare.add(e.getAsString());
                }
            }
            int n = 0;
            if (root.has("foods")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("foods").entrySet()) {
                    JsonObject f = e.getValue().getAsJsonObject();
                    n++;
                    if (!f.has("seats")) {
                        continue;
                    }
                    for (JsonElement s : f.getAsJsonArray("seats")) {
                        bySeat.computeIfAbsent(s.getAsString(), k -> new ArrayList<>()).add(e.getKey());
                    }
                }
            }
            return new Diet(bySeat, order, seatWords, bare, null, n);
        } catch (Exception e) {
            return new Diet(Map.of(), List.of(), Map.of(), List.of(),
                    "read of " + RESOURCE + " failed: " + e, 0);
        }
    }

    /** Why the pack cannot be read, or null when it read cleanly. */
    @Nullable
    public String problem() {
        return problem;
    }

    public boolean loaded() {
        return problem == null && !bySeat.isEmpty();
    }

    public int foods() {
        return foods;
    }

    /** The seats, in the order a meal is described in. */
    public List<String> seats() {
        return Collections.unmodifiableList(order);
    }

    /** The name of a seat, for a sentence: "the grain", "the fat it is cooked in". */
    public String seatWord(String seat) {
        return seatWords.getOrDefault(seat, seat);
    }

    public String summary() {
        return foods + " foods filling " + bySeat.size() + " seats" + (loaded() ? "" : " (" + problem + ")");
    }

    // -------------------------------------------------------------------------------------------------- the meal

    /**
     * True when a food is grown or kept rather than hunted, fished or gathered.
     *
     * <p>The distinction decides the whole answer. Every register this reads dates wild foods as
     * present from the beginning of the world, so the earliest-established food at any place is
     * always acorns — and a table sorted by "here longest" makes acorns the grain of imperial
     * Rome, of the Punjab, and of Tahiti alike. That is not a diet, it is a list sorted the wrong
     * way. A place that farms eats what it farms; the wild is what it falls back on when the
     * harvest fails, which is the one thing the seat order has to get right.
     */
    private static boolean cultivated(Product p) {
        return p != null && p.work() != null && !p.work().equals("foraging");
    }

    /**
     * What is eaten at a point in a year.
     *
     * <p>Three things order the answer, and the order between them matters more than any of them:
     *
     * <ol>
     *   <li><b>not already named</b> — a food fills one seat. Peas are a pulse or a green, not
     *       both at one meal, and wild food is one thing on the plate rather than the grain, the
     *       greens and the meat of it.</li>
     *   <li><b>cultivated before foraged</b> — what is grown before what is gathered, so the wild
     *       is the fallback and not the staple.</li>
     *   <li><b>made here before brought from far</b> — a place's own food first.</li>
     *   <li><b>then longest established</b> — the food this place has known longest.</li>
     * </ol>
     *
     * <p>A food already named at an earlier seat drops behind the others at the next, so a
     * catch-all like wild food fills one seat rather than turning up as the grain, the greens and
     * the meat of the same meal.
     */
    public Meal at(double lat, double lon, int year) {
        ProductAtlas atlas = ProductAtlas.get();
        Place place = atlas.place(lat, lon, year);
        Map<String, List<Serving>> seats = new LinkedHashMap<>();
        List<String> used = new ArrayList<>();

        // A blank region means the ways-of-life survey does not reach this point: open water, or
        // an island too small for its 146 regions. It is not a reason to refuse an answer, and
        // trying to make it one went wrong at once -- the mid Atlantic and Tahiti both have a
        // blank region, and Tahiti is exactly the place whose dinner this is about. What is true
        // is that the answer describes the *area*, not the place, and the meal says so.
        String region = place.region();
        boolean unmapped = region == null || region.isBlank();

        for (String seat : order) {
            List<Serving> rows = new ArrayList<>();
            for (String id : bySeat.getOrDefault(seat, List.of())) {
                Product p = atlas.product(id);
                if (p == null) {
                    continue;
                }
                Answer a = atlas.status(p, place);
                if (!a.status().present()) {
                    continue;
                }
                rows.add(new Serving(p.id(), p.name(), seat, a.status() == Status.MADE_HERE, a.since(),
                        cultivated(p), used.contains(id)));
            }
            rows.sort(Diet::better);
            List<Serving> top = new ArrayList<>();
            for (Serving s : rows) {
                if (top.size() >= PER_SEAT) {
                    break;
                }
                // One seat, one food. The atlas carries both a category and the thing in it --
                // "cacao and chocolate" and "chocolate", "taro, yams and breadfruit" and
                // "breadfruit", "spices (pepper, cinnamon, cloves)" and "black pepper" -- and
                // naming both is not a description of a meal, it is a list of the pack. A food
                // that shares a name with one already at the seat is the same food.
                boolean same = false;
                for (Serving had : top) {
                    if (sameFood(had.name(), s.name())) {
                        same = true;
                        break;
                    }
                }
                if (!same) {
                    top.add(s);
                }
            }
            if (!top.isEmpty()) {
                seats.put(seat, top);
                for (Serving s : top) {
                    used.add(s.id());
                }
            }
        }

        boolean thin = false;
        for (String need : bareMinimum) {
            if (!seats.containsKey(need)) {
                thin = true;
                break;
            }
        }
        return new Meal(place, seats, thin, unmapped);
    }

    /**
     * The meal as a sentence. A place with nothing at the table says so and stops; a thin table
     * says what little there is; a full one is read out seat by seat.
     */
    public static String sentence(Meal meal) {
        if (meal == null) {
            return "there is nothing here to speak of";
        }
        List<String> parts = new ArrayList<>();
        for (String seat : meal.seats().keySet()) {
            List<Serving> ss = meal.at(seat);
            if (ss.isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (Serving s : ss) {
                names.add(s.name().toLowerCase(Locale.ROOT));
            }
            parts.add(String.join(" or ", names) + " as " + wordFor(seat));
        }
        if (parts.isEmpty()) {
            return "there is nothing here to speak of";
        }
        String joined = String.join("; ", parts);
        if (meal.thin()) {
            return "little: " + joined;
        }
        return joined;
    }

    /** The word for a seat in a sentence, without needing the instance. */
    private static String wordFor(String seat) {
        Diet d = instance;
        if (d != null) {
            return d.seatWord(seat);
        }
        return seat;
    }

    /** Every food filling a seat, for a listing command. */
    public Map<String, List<String>> table() {
        return Collections.unmodifiableMap(bySeat);
    }
}

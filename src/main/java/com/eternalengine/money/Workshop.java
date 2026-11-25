package com.eternalengine.money;

import com.eternalengine.money.ProductAtlas.Maker;
import com.eternalengine.money.ProductAtlas.Place;
import com.eternalengine.money.ProductAtlas.Product;
import com.eternalengine.money.ProductAtlas.Status;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Step 44 part 7 (D16): a workplace that turns inputs into outputs.
 *
 * <p>The survey for this step found the economy three-quarters built. Sellers are there
 * ({@link ShopService}, {@link MarketService}), buyers are there, and workers are there —
 * {@code jobs.json.gz} carries 23 job tables over twelve kinds of work, so the mod has always
 * known how many people in a place farm and how many work metal. What it had never had is a
 * <i>maker</i>: nothing turned an input into an output. A merchant's stock ran down and nothing
 * put anything back.
 *
 * <p>Parts C, D and E supplied the pieces and left them inert. Part D wrote on every product the
 * work that makes it, the workplace it is made in, and what it is made <i>from</i>. Part E worked
 * out what a failed monsoon does to a shelf. This is the class that reads all three at once and
 * answers the question a working economy has to answer:
 *
 * <blockquote>this workshop, with the hands it has and the material it can get, turns out how
 * much — and what is it short of?</blockquote>
 *
 * <h2>Where the rate comes from, and where it does not</h2>
 *
 * <p>Every product carries {@code days}: what it is worth in days of a worker's time. A saddle is
 * two days, a piece of flint a fiftieth. Twenty-one products carry a measured value. Reading them
 * back to front gives the obvious throughput — one person who takes two days over a saddle makes
 * half a saddle in a day.
 *
 * <p>That only covers 21 of 539. The rest get the median of their own kind of work, and only when
 * that kind has {@link #MIN_SAMPLES} measured products or more to take a median from. In practice
 * that is <b>craft</b>, with fifteen measurements, and <b>farming</b>, with three. For the kinds
 * of work where the mod has no measured rate at all — the miner, the herder, the clerk — the
 * workshop reports that it is unrated rather than inventing a number, because a mine's output
 * depends on the seam and a herd's on the pasture, and neither is a thing this step can guess.
 *
 * <p>Using a value in labour time as a throughput is an assumption, and it is the labour theory of
 * value. It is defensible for a pre-industrial economy, it is what the mod already does when it
 * marks a brought good up by the journey, and it is applied <i>only</i> where it is measured.
 *
 * <h2>What limits a workshop</h2>
 *
 * <p>Three things, and the answer names which one is binding:
 *
 * <ul>
 *   <li><b>hands</b> — how many people here do this work, from the jobs register.</li>
 *   <li><b>material</b> — whether the inputs are to be had. Part D's inputs are groups, and the
 *       members of a group are alternatives: bread needs grain <i>and</i> something to burn, and
 *       either wood or charcoal will do for the burning. A group is as available as the best of
 *       its members; the workshop is as available as its worst group.</li>
 *   <li><b>the world</b> — part E's shock: what a famine, a plague, a burnt workshop or a closed
 *       road has done to the material coming in and to the work being done.</li>
 * </ul>
 */
public final class Workshop {

    /**
     * How many measured products a kind of work needs before its median is used as a rate. Three,
     * because with one or two a median is just that number wearing a hat.
     */
    public static final int MIN_SAMPLES = 3;

    /** Below this many units in a day, a workshop is not producing to speak of. */
    public static final double TRIFLING = 0.05d;

    /** Days of work for a product whose value was never measured. */
    public static final double UNMEASURED = -1d;

    private static volatile Map<String, Double> medians;
    private static volatile int rated;

    /**
     * What a workshop turns out in a day.
     *
     * @param product   what is being made
     * @param workplace part D's workplace — the kiln, the forge, the loom
     * @param work      the kind of work, one of the twelve
     * @param hands     how many people here do this kind of work at all
     * @param perHead   how many one of them turns out in a day, with the material and the
     *                  world taken into account
     * @param rivals    how many products here share those hands
     * @param scarce    what is binding: {@code "hands"}, {@code "material"}, {@code "the world"}
     *                  or {@code "nothing"}
     * @param why       the reason, when something is wrong
     * @param rated     false when the mod has no measured rate for this kind of work, so
     *                  {@code aDay} is {@link #UNMEASURED} rather than a guess
     * @param rivals    how many products here share those hands.
     *
     * <h3>Why this is a rate per head and not a total</h3>
     *
     * <p>The jobs register says how many people in a place work metal or work clay. It does not
     * say how they divide between pots and bricks, and no register this mod carries does either
     * — the 23 job tables stop at the twelve kinds of work. Dividing the hands equally among the
     * products that want them was tried and is a lie in both directions: in South Asia in 1600
     * there are 353 products made here, so a city of five thousand has five hundred craftsmen
     * who are each a seventh of a baker, a seventh of a potter and a seventh of a glassmaker at
     * once. Narrowing it to what is made here rather than brought does not help; the atlas makes
     * nearly everything here.
     *
     * <p>So what is reported is what one person turns out in a day — which <i>is</i> measured,
     * from the days of work each product carries — and the most the whole workforce could turn
     * out if every one of them were set to it, offered as the ceiling it is.
     */
    public record Output(Product product, String workplace, String work, int hands,
                         double perHead, String scarce, String why, boolean rated, int rivals) {

        /** True when nothing is holding this workshop back but the length of the day. */
        public boolean full() {
            return "nothing".equals(scarce);
        }

        /** True when it makes nothing at all. */
        public boolean idle() {
            return perHead <= TRIFLING;
        }

        /**
         * The most this place could turn out in a day, if every person here who does this kind
         * of work were set to this one product. A ceiling, not a forecast: {@link #rivals} says
         * how many other products want the same hands.
         */
        public double ceiling() {
            return hands * Math.max(0d, perHead);
        }

        /**
         * "<i>Bread from bakeries, in the bakehouse: about 72 people here, about 196 a day, but
         * there is only some wheat and barley.</i>"
         *
         * <p>The product's name is used exactly as the atlas writes it and never pluralised.
         * Only the twenty-one holdable items carry a singular and a plural; the rest are mass
         * nouns already — "pottery", "cheese", "bread from bakeries" — and adding an s to them
         * produced "potterys" and "bread from bakeriess".
         */
        public String sentence() {
            Product p = product;
            String where = workplace == null || workplace.isBlank() ? "workshop" : workplace;
            if (hands <= 0) {
                return "there is nobody here who could make " + p.name().toLowerCase(Locale.ROOT);
            }
            if (!rated) {
                return p.name() + ", in " + where + ": about " + hands
                        + " people here do that work, but the mod has no measured rate for " + work
                        + " and will not invent one";
            }
            String head = p.name() + ", in " + where + ": about " + words(perHead)
                    + " a day for each of the " + hands + " here who do that work";
            return switch (scarce) {
                case "hands" -> head + ", and hands are the limit of it";
                case "material" -> head + ", but " + why;
                case "the world" -> head + ", though " + why;
                case "unknown" -> p.name() + ", in " + where + ": " + why;
                default -> head + ", and nothing is wanting";
            };
        }

        private static String words(double v) {
            if (v < 0.5d) {
                return v < 0.15d ? "hardly any" : "a part of one";
            }
            if (v < 1.5d) {
                return "one";
            }
            return v < 8d ? String.valueOf(Math.round(v)) : "about " + Math.round(v);
        }
    }

    private Workshop() {
    }

    // ------------------------------------------------------------------------------------------------- the rate

    /**
     * Days of work for one of a product, or {@link #UNMEASURED}.
     *
     * <p>The product's own measured value if it has one; otherwise the median for its kind of
     * work, when that kind has {@link #MIN_SAMPLES} or more; otherwise {@link #UNMEASURED}.
     */
    public static double daysOf(Product p) {
        if (p == null) {
            return UNMEASURED;
        }
        if (p.days() > 0) {
            return p.days();
        }
        Double m = medians().get(p.work());
        return m == null ? UNMEASURED : m;
    }

    /** How many of a product one person makes in a day, or {@link #UNMEASURED}. */
    public static double rateOf(Product p) {
        double d = daysOf(p);
        return d <= 0 ? UNMEASURED : 1d / d;
    }

    /**
     * The median measured {@code days} for each kind of work, read from the products themselves.
     * Computed once from whatever the atlas loaded, so this class and the pack cannot drift.
     */
    private static synchronized Map<String, Double> medians() {
        Map<String, Double> m = medians;
        if (m != null) {
            return m;
        }
        Map<String, List<Double>> by = new HashMap<>();
        for (Product p : ProductAtlas.get().products()) {
            if (p.days() > 0 && p.work() != null && !p.work().isBlank()) {
                by.computeIfAbsent(p.work(), k -> new ArrayList<>()).add(p.days());
            }
        }
        Map<String, Double> out = new HashMap<>();
        int n = 0;
        for (Map.Entry<String, List<Double>> e : by.entrySet()) {
            List<Double> v = new ArrayList<>(e.getValue());
            if (v.size() < MIN_SAMPLES) {
                continue;                      // two measurements are not a median
            }
            v.sort(Double::compareTo);
            out.put(e.getKey(), v.get(v.size() / 2));
            n += v.size();
        }
        rated = n;
        medians = out;
        return out;
    }

    /** How many products carry a rate, measured or taken from their kind of work. */
    public static int rated() {
        medians();
        return rated;
    }

    /** The kinds of work the mod can put a rate on, and the days each is taken to be. */
    public static Map<String, Double> rates() {
        return new HashMap<>(medians());
    }

    // ------------------------------------------------------------------------------------------------ the output

    /** What this workshop turns out in a day at a point, with {@code people} living there. */
    public static Output at(Product p, double lat, double lon, int year, int people) {
        return at(p, lat, lon, year, null, people);
    }

    /**
     * What this workshop turns out in a day.
     *
     * @param at the place, when it is already to hand; null to look it up
     */
    public static Output at(Product p, double lat, double lon, int year, @Nullable Place at,
                            int people) {
        if (p == null) {
            return null;
        }
        ProductAtlas atlas = ProductAtlas.get();
        Place place = at != null ? at : atlas.place(lat, lon, year);

        String work = p.work() == null || p.work().isBlank() ? "craft" : p.work();
        Maker maker = atlas.maker(p, lat, lon, year, place, people);
        int rivals = rivals(p, place, work);
        int hands = maker.people();
        String workplace = p.workplaceName() == null || p.workplaceName().isBlank()
                ? "workshop" : p.workplaceName();

        if (hands <= 0) {
            return new Output(p, workplace, work, 0, 0d, "hands",
                    "there is nobody here who does that work", rateOf(p) > 0, rivals);
        }

        // the thing itself has to be made here, or brought here, before anybody makes any
        Status st = atlas.status(p, place).status();
        if (!st.present()) {
            return new Output(p, workplace, work, hands, 0d, "unknown",
                    p.name().toLowerCase(Locale.ROOT) + " is not known here yet", rateOf(p) > 0,
                    rivals);
        }

        Shortages.Supply own = Shortages.get().supplyOf(p, Shortages.get().shockAt(lat, lon, year),
                place);
        double shock = own == null ? 1d : Math.max(0d, Math.min(1d, own.supply()));

        // the inputs: each group is a need, the members of a group are alternatives
        double material = 1d;
        String worst = null;
        double worstHave = 1d;
        for (List<String> group : p.inputs()) {
            double best = 0d;
            String bestName = null;
            for (String id : group) {
                Product in = atlas.product(id);
                if (in == null) {
                    continue;
                }
                Status s = atlas.status(in, place).status();
                if (!s.present()) {
                    continue;
                }
                Shortages.Supply sup = Shortages.get().supplyOf(in,
                        Shortages.get().shockAt(lat, lon, year), place);
                double have = sup == null ? 1d : Math.max(0d, Math.min(1d, sup.supply()));
                if (have > best) {
                    best = have;
                    bestName = in.name().toLowerCase(Locale.ROOT);
                }
            }
            if (best <= 0d) {
                return new Output(p, workplace, work, hands, 0d, "material",
                        "there is no " + String.join(" or ", group).replace('_', ' ')
                                + " to be had", rateOf(p) > 0, rivals);
            }
            if (best < worstHave) {
                worstHave = best;
                worst = bestName;
            }
            material = Math.min(material, best);
        }

        double rate = rateOf(p);
        if (rate <= 0) {
            return new Output(p, workplace, work, hands, UNMEASURED, "nothing", null, false,
                    rivals);
        }

        double perHead = rate * material * shock;
        double made = perHead;

        String scarce;
        String why;
        if (made <= TRIFLING) {
            scarce = shock < material ? "the world" : "material";
            why = shock < material
                    ? (own == null ? "the world is against it" : own.why())
                    : ("there is " + (worst == null ? "nothing to hand" : "little " + worst));
        } else if (material <= shock && material < 1d - TRIFLING) {
            scarce = "material";
            why = (worst == null ? "the material runs short" : "there is only some " + worst);
        } else if (shock < 1d - TRIFLING) {
            scarce = "the world";
            why = own == null ? "the world is against it" : own.why();
        } else {
            scarce = "nothing";
            why = null;
        }
        return new Output(p, workplace, work, hands, made, scarce, why, true, rivals);
    }

    /**
     * How many products are competing for the same hands here.
     *
     * <p>Counted per area and year, because the worker pool itself is an area-level number:
     * {@code Jobs.shareOf} gives the share of a place's people doing each of the twelve kinds of
     * work, and nothing finer. Cached, since the coverage sweep asks thousands of times.
     */
    private static int rivals(Product p, Place place, String work) {
        String key = place.area() + "|" + place.year() + "|" + work;
        Integer had = rivalCache.get(key);
        if (had != null) {
            return had;
        }
        ProductAtlas atlas = ProductAtlas.get();
        int n = 0;
        for (Product other : atlas.products()) {
            if (other == p) {
                continue;
            }
            if (!work.equals(other.work())) {
                continue;
            }
            if (!atlas.status(other, place).status().present()) {
                continue;
            }
            n++;
        }
        int total = Math.max(1, n);
        rivalCache.put(key, total);
        return total;
    }

    private static final Map<String, Integer> rivalCache =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    /**
     * What every workshop here is short of, worst first. This is the list a player would want:
     * not what a place makes, but what is stopping it making more.
     */
    public static List<Output> shortfalls(double lat, double lon, int year, int people) {
        ProductAtlas atlas = ProductAtlas.get();
        Place place = atlas.place(lat, lon, year);
        List<Output> out = new ArrayList<>();
        for (Product p : atlas.products()) {
            if (p.inputs().isEmpty()) {
                continue;
            }
            Output o = at(p, lat, lon, year, place, people);
            if (o != null && !o.full() && !o.idle()) {
                out.add(o);
            }
        }
        out.sort((a, b) -> Double.compare(a.perHead(), b.perHead()));
        return out;
    }

    public static String summary() {
        Map<String, Double> m = rates();
        StringBuilder b = new StringBuilder();
        b.append(rated()).append(" products carry a measured rate; ");
        for (Map.Entry<String, Double> e : new java.util.TreeMap<>(m).entrySet()) {
            b.append(e.getKey()).append(' ');
            b.append(String.format(Locale.ROOT, "%.2f", e.getValue())).append("d ");
        }
        return b.toString().trim();
    }
}

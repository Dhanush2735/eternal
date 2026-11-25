package com.eternalengine.war;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Step 40 (D10 armies in action): <b>why men stand, and what makes them stop</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>{@code BattleSimulator} handed morale three integers and no reason:
 *
 * <pre>
 * int attackerMorale = outcome == ATTACKER_VICTORY ? 10 : outcome == DRAW ? -2 : -15;
 * int defenderMorale = outcome == DEFENDER_VICTORY ? 10 : outcome == DRAW ? -2 : -15;
 * </pre>
 *
 * <p>An army that has not been paid for four months and an army that has just taken a town got
 * the same ten points. {@code Army} had a morale number, a size, a supply level and an
 * {@code equipmentEra} that nothing read. It did not know where the army came from, who raised
 * it, what it was paid in, what it had been through, or what it was fighting for.
 *
 * <p>Part 6 of this step wired it up: {@code BattleSimulator} and {@code Battles} now call
 * {@link #factorWeight(String, int)} and scale the loser's charge by how it actually went against
 * {@link #loserLoss()}, and {@code Army} carries what it lost through {@code takeLosses}, so that
 * a rout can be known later.
 *
 * <h3>What this register does</h3>
 *
 * <p>It reads the army's circumstances and returns a number <i>with the reasons attached</i>. A
 * {@code +10} is not an answer; "helped by the men beside him (+18) and by depth behind the front
 * rank (+12), hurt by pay in arrears (-20)" is.
 *
 * <p>Three things in it come from the sources rather than from this file, and each one is checked
 * in the build:
 *
 * <ol>
 * <li><b>Two different kinds of motivation.</b> Berkovich's three stages of a soldier's war are
 *     to enlist, to fight, and — the one that "covers the bulk of one's military service" — to
 *     put up with the hardships of daily army life. The Civil War historiography splits it the
 *     same way: convictions of duty, honour, patriotism and ideology were the principal
 *     <i>sustaining</i> motivation, while courage, self-respect and group cohesion were the main
 *     sources of <i>combat</i> motivation. {@code kind} keeps them apart.</li>
 * <li><b>Winning is itself a factor, and losing is worse than winning is good.</b> "Soldiers will
 *     endure almost any privation while they are winning... Lee's men fought hungry through much
 *     of the Civil War, cheerfully and without complaint; it was only when Grant and Sherman
 *     began to close in that they deserted."</li>
 * <li><b>Dispersion <i>costs</i> morale rather than granting it.</b> du Picq: "since the
 *     invention of fire arms... the more men think themselves isolated, the more need they have
 *     of high morale. We are brought by dispersion to the need of a cohesion greater than ever
 *     before." The skirmish line needs <i>more</i> of it than the phalanx did, not less.</li>
 * </ol>
 *
 * <p>The fourth claim is the one the mod had no way to express at all, and it is the largest
 * single fact about battle casualties:
 *
 * <blockquote>The rout is where the dying happens. "Demoralized and not feeling themselves
 * supported, the ranks engaged followed them, and the routed mass let itself be slaughtered. The
 * weapons fell from their hands." — du Picq, on Cannae</blockquote>
 *
 * <p>Krentz's measured figures for hoplite battles give 3–10% for the winners and 10–20% for the
 * losers, and the whole gap between the two is the pursuit. {@link #winnerLoss()} and
 * {@link #loserLoss()} carry those numbers, and any battle model that charges both sides the same
 * percentage is modelling the fighting and not the battle.
 *
 * <h3>What this register will refuse to do</h3>
 *
 * <p>It will not pretend to know a commander. du Picq, and everyone who has written since, would
 * say that is where most of this lives, and no row in the pack can express it. It will not
 * produce a number without saying where every point of it came from, and it will not answer for
 * Oceania, for the Americas before 1491, or for sub-Saharan Africa, because nothing was read for
 * them.
 */
public final class Morale {

    /**
     * One thing that bears on whether a body of troops will stand.
     *
     * <p>{@code when} names the single circumstance that turns the factor on, and it is one of a
     * closed set that {@link State} can actually answer — a factor keyed on something the mod
     * cannot see would be a factor that never fires.
     */
    public record Factor(String id, String name, String kind, String when, int weight,
                         int start, int end, double[] box, String needs, String doing,
                         String note, String source, String mark) {

        public boolean inForce(int year) {
            return year >= start && year <= end;
        }

        public boolean here(double lat, double lon) {
            return lat >= box[0] && lat <= box[1] && lon >= box[2] && lon <= box[3];
        }

        public boolean helps() {
            return weight > 0;
        }
    }

    /**
     * One way of raising an army.
     *
     * <p>{@code cohesion} is the number the reading starts from — it is what this kind of body is
     * worth before anything has been done to it. {@code desertion} is the share that goes in a
     * year, and {@code paidIn} is the thing that keeps them, which is what decides whether pay in
     * arrears will bite.
     */
    public record Raised(String id, String name, int start, int end, double[] box, String paidIn,
                         int cohesion, double desertion, boolean willTreat, String needs,
                         String doing, String note, String source, String mark) {

        public boolean inForce(int year) {
            return year >= start && year <= end;
        }

        public boolean here(double lat, double lon) {
            return lat >= box[0] && lat <= box[1] && lon >= box[2] && lon <= box[3];
        }

        /** Men who are only there for the money are the ones who go when it stops. */
        public boolean paidInCoin() {
            return "pay".equals(paidIn) || "wage".equals(paidIn);
        }
    }

    /**
     * One measured desertion rate. {@code perYear}, {@code totalPct} and {@code count} are each
     * null where the record did not give that figure: the British 77,696 cases is a count and the
     * register will not divide it by a denominator nobody settled.
     */
    public record Rate(String id, String army, int start, int end, Double perYear,
                       Double totalPct, Long count, String raised, String note, String source,
                       String mark) {

        public boolean inForce(int year) {
            return year >= start && year <= end;
        }
    }

    /**
     * One way of stopping. {@code lossPct} is the share of the army gone before it happens, and
     * it is <b>0 for the two that are not caused by casualties at all</b>: a mutiny is about pay
     * and a mass desertion is about why the men were there, and neither waits for a battle.
     */
    public record Break(String id, String name, double lossPct, int start, int end, String needs,
                        String doing, String note, String source, String mark) {

        public boolean inForce(int year) {
            return year >= start && year <= end;
        }

        public boolean onCasualties() {
            return lossPct > 0.0d;
        }
    }

    /**
     * Everything the mod can actually know about a body of troops at this moment.
     *
     * <p>Every field is a thing that exists somewhere else in the simulation — where the army is,
     * how far it is from home, what shape it is standing in, whether the campaign is going its
     * way, whether the treasury has kept up. Nothing here is a judgement; the register does the
     * judging.
     */
    public record State(int year, double lat, double lon, String raised, FormationType formation,
                        int ranks, boolean winning, boolean losing, boolean lastVictory,
                        boolean lastDefeat, boolean home, boolean paid, boolean plunderPromised,
                        boolean plundered, boolean besieged, boolean hungry, boolean sick,
                        boolean drafted, boolean hired, boolean blooded, boolean raw,
                        boolean longWar) {

        /** A body with no formation named is standing in no particular order at all. */
        public FormationType formation() {
            return formation == null ? FormationType.LINE : formation;
        }

        /**
         * du Picq's measure: how far apart the men are. The phalanx at 1.1 m is close, the line
         * at 1.8 m and the square at 2.0 m are close enough, and the skirmish line at 3.0 m is
         * dispersed — and it is the dispersed one that needs the most morale, not the least.
         */
        public boolean dispersed() {
            return formation().fileSpacing() >= 2.5d;
        }

        public boolean closeOrder() {
            return !dispersed();
        }

        public boolean deep() {
            return ranks >= 3;
        }

        public boolean thin() {
            return ranks <= 2;
        }
    }

    /** What came of reading an army: a number, and every point of it accounted for. */
    public record Reading(int morale, Raised raised, List<Factor> helping, List<Factor> hurting,
                          String line) {

        /** How much of the number is the things holding it up. */
        public int helpSum() {
            return helping.stream().mapToInt(Factor::weight).sum();
        }

        /** How much of it is the things dragging it down. */
        public int hurtSum() {
            return hurting.stream().mapToInt(Factor::weight).sum();
        }

        /** The body's own worth before anything was done to it. */
        public int base() {
            return morale - helpSum() - hurtSum();
        }
    }

    // ================================================================== the register

    private static final Morale INSTANCE = new Morale();
    private final List<Factor> factors;
    private final List<Raised> raised;
    private final List<Rate> rates;
    private final List<Break> breaking;
    private final double winnerPct;
    private final double loserPct;
    private final double routAt;

    private Morale() {
        List<Factor> fs = new ArrayList<>();
        List<Raised> rs = new ArrayList<>();
        List<Rate> ds = new ArrayList<>();
        List<Break> bs = new ArrayList<>();
        double wpct = 6.5d;
        double lpct = 15.0d;
        double rat = 12.0d;

        JsonObject jo = read(RESOURCE);
        if (jo != null) {
            if (jo.has("factors")) {
                for (JsonElement e : jo.getAsJsonArray("factors")) {
                    JsonObject o = e.getAsJsonObject();
                    fs.add(new Factor(str(o, "id"), str(o, "name"), str(o, "kind"),
                        str(o, "when"), o.get("weight").getAsInt(), o.get("start").getAsInt(),
                        o.get("end").getAsInt(), box(o), str(o, "needs"), str(o, "doing"),
                        str(o, "note"), str(o, "src"), str(o, "mark")));
                }
            }
            if (jo.has("raised")) {
                for (JsonElement e : jo.getAsJsonArray("raised")) {
                    JsonObject o = e.getAsJsonObject();
                    rs.add(new Raised(str(o, "id"), str(o, "name"), o.get("start").getAsInt(),
                        o.get("end").getAsInt(), box(o), str(o, "paidIn"),
                        o.get("cohesion").getAsInt(), o.get("desertion").getAsDouble(),
                        o.has("willTreat") && o.get("willTreat").getAsBoolean(), str(o, "needs"),
                        str(o, "doing"), str(o, "note"), str(o, "src"), str(o, "mark")));
                }
            }
            if (jo.has("desertion")) {
                for (JsonElement e : jo.getAsJsonArray("desertion")) {
                    JsonObject o = e.getAsJsonObject();
                    ds.add(new Rate(str(o, "id"), str(o, "army"), o.get("start").getAsInt(),
                        o.get("end").getAsInt(), dbl(o, "perYear"), dbl(o, "totalPct"),
                        lng(o, "count"), str(o, "raised"), str(o, "note"), str(o, "src"),
                        str(o, "mark")));
                }
            }
            if (jo.has("breaking")) {
                for (JsonElement e : jo.getAsJsonArray("breaking")) {
                    JsonObject o = e.getAsJsonObject();
                    bs.add(new Break(str(o, "id"), str(o, "name"), o.get("lossPct").getAsDouble(),
                        o.get("start").getAsInt(), o.get("end").getAsInt(), str(o, "needs"),
                        str(o, "doing"), str(o, "note"), str(o, "src"), str(o, "mark")));
                }
            }
            if (jo.has("casualties")) {
                JsonObject c = jo.getAsJsonObject("casualties");
                wpct = num(c, "winnerPct", wpct);
                lpct = num(c, "loserPct", lpct);
                rat = num(c, "routAt", rat);
            }
        }
        this.factors = Collections.unmodifiableList(fs);
        this.raised = Collections.unmodifiableList(rs);
        this.rates = Collections.unmodifiableList(ds);
        this.breaking = Collections.unmodifiableList(bs);
        this.winnerPct = wpct;
        this.loserPct = lpct;
        this.routAt = rat;
    }

    public static Morale get() {
        return INSTANCE;
    }

    public static List<Factor> factors() {
        return get().factors;
    }

    public static List<Raised> raised() {
        return get().raised;
    }

    public static List<Rate> rates() {
        return get().rates;
    }

    public static List<Break> breaking() {
        return get().breaking;
    }

    public static int factorCount() {
        return get().factors.size();
    }

    public static int raisedCount() {
        return get().raised.size();
    }

    public static int rateCount() {
        return get().rates.size();
    }

    // ================================================================== how it was raised

    /** The named way of raising an army, or the warlord band if there is no such row. */
    public static Raised raised(String id) {
        for (Raised r : get().raised) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * The ordinary way of raising an army in this place and year: the <b>narrowest</b> box that
     * covers the place, and only then the latest start — the same two rules the wall register
     * uses, for the same reason. A register asked about a Swiss valley in 1450 must answer with
     * the citizen militia and not with the warlord band that merely happens to be drawn over the
     * whole world and dated later.
     */
    public static Raised raisedAt(double lat, double lon, int year) {
        Raised best = null;
        for (Raised r : get().raised) {
            if (!r.inForce(year) || !r.here(lat, lon)) {
                continue;
            }
            if (best == null || area(r.box()) < area(best.box())
                    || (area(r.box()) == area(best.box()) && r.start() > best.start())) {
                best = r;
            }
        }
        return best;
    }

    /**
     * The measured rate for this kind of army in this year, if the record has one.
     *
     * <p>Preference order: a measured rate that covers this exact way of raising and this year,
     * then any measured rate covering the year, then null — in which case the caller falls back
     * on the {@code raised} row's own figure, which is this register's reading rather than a
     * measurement.
     */
    public static Rate rateFor(Raised r, int year) {
        if (r == null) {
            return null;
        }
        Rate best = null;
        for (Rate d : get().rates) {
            if (!d.inForce(year)) {
                continue;
            }
            boolean exact = r.id().equals(d.raised());
            if (best == null || (exact && !r.id().equals(best.raised()))) {
                best = d;
            }
        }
        return best;
    }

    /** One factor by name, so a caller can read a weight out of the register rather than repeat it. */
    public static Factor factor(String id) {
        if (id == null) {
            return null;
        }
        for (Factor f : get().factors) {
            if (f.id().equals(id)) {
                return f;
            }
        }
        return null;
    }

    /**
     * A factor's weight by name, with a fallback for the day the row is not in the pack.
     *
     * <p>Nothing in the mod should carry a copy of a number this register already holds: the
     * battle registers read {@code victory} and {@code defeat} from here rather than writing
     * {@code +10} and {@code -15} into their own source, so that the two move together.
     */
    public static int factorWeight(String id, int fallback) {
        Factor f = factor(id);
        return f == null ? fallback : f.weight();
    }

    /** One measured rate by name, so a caller can say which army it is quoting. */
    public static Rate rate(String id) {
        for (Rate d : get().rates) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    /**
     * The share that goes across a whole war, where the record gave one.
     *
     * <p>These are not annual rates and must not be read as one: the Confederate 12.5 per cent is
     * a share of everyone who ever served across four years, and the French 20 per cent across
     * sixty-three. Returns null where the record only gave a rate or a count — the British 77,696
     * cases is a count and the register will not divide it by a denominator nobody settled.
     */
    public static Double totalDesertion(Raised r, int year) {
        Rate measured = rateFor(r, year);
        return measured == null ? null : measured.totalPct();
    }

    /**
     * The share of this army that goes in a year, as a percentage.
     *
     * <p>Three adjustments, each of them read rather than invented. A body paid in coin that has
     * not been paid loses men faster, because "hired troops could passively refuse to fight,
     * mutiny, desert to a higher bidder" — and the register doubles it, since that is the one
     * thing the record is unambiguous about. An army that is losing loses men faster than one
     * that is winning, which is the whole of the Grant and Sherman passage. An army a long way
     * from home loses them faster still, because the third of the three wellsprings of desertion
     * is opportunity outside the service and there is less of it the further the men have to walk.
     */
    public static double desertionPerYear(State s) {
        Raised r = raised(s.raised());
        Rate measured = rateFor(r, s.year());
        if (measured != null && measured.perYear() != null) {
            return measured.perYear();
        }
        double base = r == null ? 12.0d : r.desertion();
        if (r != null && r.paidInCoin() && !s.paid()) {
            base *= 2.0d;
        }
        if (s.losing()) {
            base *= 1.6d;
        } else if (s.winning()) {
            base *= 0.6d;
        }
        if (!s.home()) {
            base *= 1.25d;
        }
        return base;
    }

    // ================================================================== the reading

    /** Does this factor bear on this army at this moment? */
    public static boolean applies(Factor f, State s) {
        if (!f.inForce(s.year()) || !f.here(s.lat(), s.lon())) {
            return false;
        }
        switch (f.when()) {
            case "always":
                return true;
            case "winning":
                return s.winning();
            case "losing":
                return s.losing();
            case "victory":
                return s.lastVictory();
            case "defeat":
                return s.lastDefeat();
            case "home":
                return s.home();
            case "foreign":
                return !s.home();
            case "paid":
                return s.paid();
            case "unpaid":
                return !s.paid();
            case "plunder":
                return s.plundered();
            case "noplunder":
                return s.plunderPromised() && !s.plundered();
            case "besieged":
                return s.besieged();
            case "dispersed":
                return s.dispersed();
            case "close":
                return s.closeOrder();
            case "deep":
                return s.deep();
            case "thin":
                return s.thin();
            case "hungry":
                return s.hungry();
            case "sick":
                return s.sick();
            case "long":
                return s.longWar();
            case "drafted":
                return s.drafted();
            case "hired":
                return s.hired();
            case "raw":
                return s.raw();
            case "blooded":
                return s.blooded();
            default:
                return false;
        }
    }

    /**
     * Read an army and say what it is resting on.
     *
     * <p>The reading starts from the cohesion of how the men were raised — that is what the body
     * is worth before anything has happened to it — and then every factor in the register that
     * bears on this army, in this place, in this year, moves it. The result is clamped to 0..100
     * and every point of it is in {@code helping} or {@code hurting}.
     */
    public static Reading read(State s) {
        Raised r = raised(s.raised());
        if (r == null) {
            r = raisedAt(s.lat(), s.lon(), s.year());
        }
        int base = r == null ? 40 : r.cohesion();
        List<Factor> up = new ArrayList<>();
        List<Factor> down = new ArrayList<>();
        for (Factor f : get().factors) {
            if (applies(f, s)) {
                (f.helps() ? up : down).add(f);
            }
        }
        int sum = base + up.stream().mapToInt(Factor::weight).sum()
                + down.stream().mapToInt(Factor::weight).sum();
        int morale = Math.max(0, Math.min(100, sum));
        return new Reading(morale, r, Collections.unmodifiableList(up),
            Collections.unmodifiableList(down), line(morale, base, r, up, down));
    }

    private static String line(int morale, int base, Raised r, List<Factor> up,
                               List<Factor> down) {
        StringBuilder sb = new StringBuilder();
        sb.append(r == null ? "a body of men" : r.name()).append(", ").append(base)
            .append(marked(r == null ? "recorded" : r.mark()));
        if (!up.isEmpty()) {
            sb.append("; helped by ").append(join(up));
        }
        if (!down.isEmpty()) {
            sb.append("; hurt by ").append(join(down));
        }
        sb.append(" -- ").append(morale).append(" out of 100");
        return sb.toString();
    }

    private static String join(List<Factor> fs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fs.size(); i++) {
            Factor f = fs.get(i);
            if (i > 0) {
                sb.append(i == fs.size() - 1 ? " and " : ", ");
            }
            sb.append(f.name().trim()).append(" (").append(f.weight() > 0 ? "+" : "")
                .append(f.weight()).append(")").append(marked(f.mark()));
        }
        return sb.toString();
    }

    /** A figure this register read off rather than measured says so, every time it is printed. */
    private static String marked(String mark) {
        return "estimated".equals(mark) ? " [est]" : "mixed".equals(mark) ? " [mixed]" : "";
    }

    // ================================================================== breaking

    /**
     * The share of an army gone before the formation gives way: Krentz's measured breaking point
     * for hoplite battles, 12 per cent.
     */
    public static double routThreshold() {
        return get().routAt;
    }

    /** Has this body lost enough of itself to come apart? */
    public static boolean routs(double lossShare) {
        return lossShare >= get().routAt;
    }

    /**
     * The winners' share: what a battle costs the side that holds. Krentz gives 3 to 10 per cent
     * for hoplite battles and the register takes the midpoint.
     */
    public static double winnerLoss() {
        return get().winnerPct;
    }

    /**
     * The losers' share, and it is not the same number. Krentz gives 10 to 20 per cent; the
     * register takes the midpoint; and <b>the gap between this and {@link #winnerLoss()} is the
     * pursuit and nothing else</b> — men killed from behind, after the decision, with their
     * shields on their backs.
     */
    public static double loserLoss() {
        return get().loserPct;
    }

    /** The pursuit alone: the part of the losers' losses that the fighting does not explain. */
    public static double pursuitLoss() {
        return Math.max(0.0d, get().loserPct - get().winnerPct);
    }

    /**
     * How this body is coming apart, or null if it is not.
     *
     * <p>Only the ways of breaking that are caused by casualties are considered here. Mutiny and
     * mass desertion have a loss percentage of zero precisely because they are <i>not</i> caused
     * by casualties — a mutiny is about pay and a mass desertion is about why the men were there.
     */
    public static Break breakingAt(double lossShare, int year) {
        Break best = null;
        for (Break b : get().breaking) {
            if (!b.inForce(year) || !b.onCasualties() || lossShare < b.lossPct()) {
                continue;
            }
            if (best == null || b.lossPct() > best.lossPct()) {
                best = b;
            }
        }
        return best;
    }

    /** The ways of stopping that do not wait for a battle. */
    public static List<Break> notOnCasualties(int year) {
        List<Break> out = new ArrayList<>();
        for (Break b : get().breaking) {
            if (b.inForce(year) && !b.onCasualties()) {
                out.add(b);
            }
        }
        return out;
    }

    // ================================================================== plumbing

    private static double area(double[] b) {
        return Math.max(0.01d, (b[1] - b[0]) * (b[3] - b[2]));
    }

    private static double[] box(JsonObject o) {
        double[] b = new double[4];
        if (o.has("box")) {
            JsonArray a = o.getAsJsonArray("box");
            for (int i = 0; i < 4 && i < a.size(); i++) {
                b[i] = a.get(i).getAsDouble();
            }
        }
        return b;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : fallback;
    }

    private static Double dbl(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : null;
    }

    private static Long lng(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : null;
    }

    private static JsonObject read(String resource) {
        try (InputStream in = Morale.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader r = new InputStreamReader(
                    new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    private static final String RESOURCE =
        "/data/eternalengine/eternal/history/morale.json.gz";
}

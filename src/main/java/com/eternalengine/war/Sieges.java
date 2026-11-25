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
 * Step 40 (D10 armies in action): <b>how a siege comes out, and when</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>{@code SiegeEngineController} treats a siege as block damage. Every hundred ticks it picks a
 * random spot within five metres of the target and cracks it, gravelises it or deletes it. There
 * is no food, no water, no disease, no relieving army, no negotiation, no date it has to end, and
 * no way it ends at all: a siege simply continues until somebody calls {@code endSiege}.
 *
 * <h3>What a siege actually is</h3>
 *
 * <p>A race between two clocks. The besiegers are working on the wall; the town is eating its
 * stores. Whichever runs out first decides how it ends — unless help arrives, or the people
 * outside give up first, which they very often did.
 *
 * <p>And the ending that a player will guess last is the commonest by a wide margin. Under the
 * accepted rules of war <b>a town taken by force could be sacked, and one that surrendered could
 * not</b>. So the ordinary ending was an agreement: the garrison offers to open the gates on a
 * named day if no relieving army has arrived by then, and marches out with its arms and its lives.
 * That is the mechanic. It makes the defender's decision <i>when</i> to surrender, and it makes
 * waiting one more day a gamble with the town on the table.
 *
 * <h3>The five endings</h3>
 *
 * <ol>
 * <li><b>terms</b> — the gates open on a date. The town is not sacked. The commonest.</li>
 * <li><b>starvation</b> — the last bread is gone and the garrison is past bargaining.</li>
 * <li><b>relief</b> — an army of the town's own arrives and the besiegers go.</li>
 * <li><b>abandonment</b> — the besiegers go anyway: disease, winter, their own supply, a war
 *     somewhere else. Most sieges in history were lost by the people outside.</li>
 * <li><b>storm</b> — a practicable breach and the attackers are in. The rarest, and it costs
 *     everything, because a place taken by force could be ransacked.</li>
 * </ol>
 *
 * <h3>What this register will refuse to do</h3>
 *
 * <p>It will not invent a wall clock where the wall cannot be reached. Kenilworth in 1266 was
 * ringed by its mere: it could not be mined and no tower or ram could be brought against it, so
 * the register says <i>no method here can open a breach</i> and hands the siege to hunger — which
 * is exactly what decided it, after 172 days and with two days of food in the magazine.
 */
public final class Sieges {

    /** A kind of fortification: where and when it stood, and what it is worth. */
    public record Wall(String id, String name, int start, int end, double[] box, String scale,
                       String mine, double storm, double resist, String note, String source,
                       String mark, String scope) {

        public boolean inForce(int year) {
            return year >= start && year <= end;
        }

        public boolean here(double lat, double lon) {
            return lat >= box[0] && lat <= box[1] && lon >= box[2] && lon <= box[3];
        }

        /** Under ten metres, which is the height an escalade stops working at. */
        public boolean low() {
            return "low".equals(scale);
        }
    }

    /** A way of going about a wall. */
    public record Method(String id, String name, int start, int end, int days, boolean assault,
                         double risk, String needs, String note, String source, String mark) {

        public boolean inForce(int year) {
            return year >= start && year <= end;
        }

        public boolean breaching() {
            return days > 0;
        }
    }

    /** One of the five ways a siege stops. */
    public record Ending(String id, String name, double share, boolean sack, String when,
                         String note, String source, String mark) {
    }

    /**
     * The place being besieged.
     *
     * <p>{@code ground} is what the approaches are made of: {@code dry} can be mined and walked
     * over, {@code wet} (a moat, a mere, a marsh) can be neither, and {@code rock} cannot be mined
     * at all. {@code supply} is how well the town can be fed from outside while the ring is around
     * it: 0 is fully cut off, and a place at 90 is being supplied by sea and has no real food
     * clock, which is why Candia lasted twenty-one years.
     */
    public record Town(String name, double lat, double lon, long mouths, long garrison,
                       int foodDays, int morale, int supply, String ground, String party) {

        /** A town that nobody has named a party for. */
        public Town(String name, double lat, double lon, long mouths, long garrison,
                    int foodDays, int morale, int supply, String ground) {
            this(name, lat, lon, mouths, garrison, foodDays, morale, supply, ground, null);
        }

        /**
         * What kind of party this town is, in this year.
         *
         * <p>Named if the caller knows — a commune knows it is a commune — and otherwise the
         * ordinary party of the place and year, which is the same rule {@code Sides.partyAt}
         * uses everywhere else.
         */
        public Sides.Party party(int year) {
            Sides.Party named = party == null ? null : Sides.party(party);
            return named != null ? named : Sides.partyAt(lat, lon, year);
        }

        /**
         * Can this town be held to an agreement?
         *
         * <p>This is the question the whole {@code terms} ending rests on, and it is why part 5
         * built the sides register. A conditional surrender is a bargain between two parties who
         * can each be held to it. A warlord band has nothing to hold and no one to hold it; an
         * unpaid hired company cannot be delivered by the captain who signed for it; a rising
         * has no machinery that can be bound. The gates of such a place do not open on a named
         * day — it is stormed, or it starves.
         */
        public boolean canTreat(int year) {
            Sides.Party p = party(year);
            // Where the register does not know what kind of party this is, it declines rather
            // than answering "cannot treat". Those are not the same thing: the second is a
            // finding about a warlord band or an unpaid company, and the first is the register
            // saying it has no row for this place and year. A gate that fires on silence would
            // be refusing to surrender a town for a reason nobody gave.
            return p == null || p.willTreat();
        }
    }

    /** What came of it. */
    public record Siege(String endingId, Ending ending, Wall wall, List<Method> methods,
                        Method worked, int days, long mouths0, long mouthsNow, long garrison0,
                        long garrisonNow, long besiegers0, long besiegersNow, long dead,
                        long expelled, int breachDay, int reliefDay, int promisedDay,
                        String line) {

        public boolean sacked() {
            return ending != null && ending.sack();
        }

        /** The share of the town that did not come through it. */
        public double lostShare() {
            return mouths0 <= 0 ? 0.0d : dead / (double) mouths0;
        }
    }

    // ================================================================== the register

    private static final Sieges INSTANCE = new Sieges();
    private final List<Wall> walls;
    private final List<Method> methods;
    private final List<Ending> endings;
    private final double grain;
    private final int cutAtDays;
    private final double rationCut;
    private final int uselessLastYear;
    private final int noSurrender;
    private final double famineDeaths;
    private final double uselessShare;
    private final double uselessGain;
    private final int waterDays;
    private final double besiegerBase;
    private final double besiegerCurve;
    private final double stormBase;
    private final double stormPerMan;
    private final double stormNoBreach;
    private final double stormCost;
    private final int bargaining;

    /** Days between two attempts at the breach. */
    private static final int ASSAULT_GAP = 3;
    /** Days a conditional surrender usually asks for, so that help has a chance to come. */
    private static final int TERMS_NOTICE = 30;
    /** No siege in the register runs past this without the register saying it cannot tell. */
    private static final int MAX_DAYS = 6000;
    /** -1 when no method available here can open a breach at all. */
    public static final int NO_BREACH = -1;

    private Sieges() {
        List<Wall> ws = new ArrayList<>();
        List<Method> ms = new ArrayList<>();
        List<Ending> es = new ArrayList<>();
        double gr = 0.9d;
        int cutAt = 21;
        double cut = 0.5d;
        int uLast = 1789;
        int noSurr = 90;
        double fam = 0.0015d;
        double ushare = 0.30d;
        double ugain = 1.43d;
        int water = 3;
        double bbase = 90.0d;
        double bcurve = 6.0d;
        double sbase = 0.35d;
        double sperm = 0.15d;
        double snone = 0.15d;
        double scost = 0.05d;
        int barg = 35;

        JsonObject jo = read(RESOURCE);
        if (jo != null) {
            if (jo.has("walls")) {
                for (JsonElement e : jo.getAsJsonArray("walls")) {
                    JsonObject o = e.getAsJsonObject();
                    double[] box = new double[4];
                    JsonArray b = o.getAsJsonArray("box");
                    for (int i = 0; i < 4 && i < b.size(); i++) {
                        box[i] = b.get(i).getAsDouble();
                    }
                    ws.add(new Wall(str(o, "id"), str(o, "name"), o.get("start").getAsInt(),
                        o.get("end").getAsInt(), box, str(o, "scale"), str(o, "mine"),
                        o.get("storm").getAsDouble(), o.get("resist").getAsDouble(),
                        str(o, "note"), str(o, "source"), str(o, "mark"),
                        str(o, "scope").isEmpty() ? "general" : str(o, "scope")));
                }
            }
            if (jo.has("methods")) {
                for (JsonElement e : jo.getAsJsonArray("methods")) {
                    JsonObject o = e.getAsJsonObject();
                    ms.add(new Method(str(o, "id"), str(o, "name"), o.get("start").getAsInt(),
                        o.get("end").getAsInt(), o.get("days").getAsInt(),
                        o.has("assault") && o.get("assault").getAsBoolean(),
                        o.get("risk").getAsDouble(), str(o, "needs"), str(o, "note"),
                        str(o, "source"), str(o, "mark")));
                }
            }
            if (jo.has("endings")) {
                for (JsonElement e : jo.getAsJsonArray("endings")) {
                    JsonObject o = e.getAsJsonObject();
                    es.add(new Ending(str(o, "id"), str(o, "name"), o.get("share").getAsDouble(),
                        o.has("sack") && o.get("sack").getAsBoolean(), str(o, "when"),
                        str(o, "note"), str(o, "source"), str(o, "mark")));
                }
            }
            if (jo.has("food")) {
                JsonObject f = jo.getAsJsonObject("food");
                gr = num(f, "grainKgPerHeadPerDay", gr);
                cutAt = (int) num(f, "cutAtDays", cutAt);
                cut = num(f, "rationCut", cut);
                uLast = (int) num(f, "uselessLastYear", uLast);
                noSurr = (int) num(f, "noSurrenderMorale", noSurr);
                fam = num(f, "famineDeathsPerDay", fam);
                ushare = num(f, "uselessShare", ushare);
                ugain = num(f, "uselessGain", ugain);
                water = (int) num(f, "waterDays", water);
            }
            bbase = num(jo, "besiegerBaseDays", bbase);
            bcurve = num(jo, "besiegerSupplyCurve", bcurve);
            sbase = num(jo, "stormBase", sbase);
            sperm = num(jo, "stormPerMan", sperm);
            snone = num(jo, "stormNoBreach", snone);
            scost = num(jo, "stormCost", scost);
            barg = (int) num(jo, "bargainingMorale", barg);
        }
        this.walls = Collections.unmodifiableList(ws);
        this.methods = Collections.unmodifiableList(ms);
        this.endings = Collections.unmodifiableList(es);
        this.grain = gr;
        this.cutAtDays = cutAt;
        this.rationCut = cut;
        this.uselessLastYear = uLast;
        this.noSurrender = noSurr;
        this.famineDeaths = fam;
        this.uselessShare = ushare;
        this.uselessGain = ugain;
        this.waterDays = water;
        this.besiegerBase = bbase;
        this.besiegerCurve = bcurve;
        this.stormBase = sbase;
        this.stormPerMan = sperm;
        this.stormNoBreach = snone;
        this.stormCost = scost;
        this.bargaining = barg;
    }

    public static Sieges get() {
        return INSTANCE;
    }

    public static List<Wall> walls() {
        return get().walls;
    }

    public static List<Method> methods() {
        return get().methods;
    }

    public static List<Ending> endings() {
        return get().endings;
    }

    public static int wallCount() {
        return get().walls.size();
    }

    /**
     * The ordinary fortification of this place and year.
     *
     * <p>Two rules, in this order: the <b>narrowest</b> box that covers the place wins, and only
     * then the latest start. The narrowest first, because a register asked about Pingyao in 1500
     * must answer with the rammed earth of north China and not with a trace italienne that merely
     * happens to be dated later and drawn over the whole world.
     *
     * <p>Specialist walls are left out, and that is deliberate. Nothing in a latitude and a year
     * tells you that this castle is a concentric one and its neighbour is not, so a wall marked
     * {@code special} has to be named by a caller who knows. A settlement that knows it has a
     * concentric castle says so; the map does not.
     */
    public static Wall wallAt(double lat, double lon, int year) {
        Wall best = null;
        for (Wall w : get().walls) {
            if (!w.inForce(year) || !w.here(lat, lon) || !"general".equals(w.scope())) {
                continue;
            }
            if (best == null || area(w) < area(best)
                    || (area(w) == area(best) && w.start() > best.start())) {
                best = w;
            }
        }
        return best;
    }

    /** A wall a caller names because it knows: Krak des Chevaliers is concentric, and nothing else. */
    public static Wall wallNamed(String id) {
        for (Wall w : get().walls) {
            if (w.id().equals(id)) {
                return w;
            }
        }
        return null;
    }

    private static double area(Wall w) {
        return (w.box()[1] - w.box()[0]) * (w.box()[3] - w.box()[2]);
    }

    /** Everything a besieger of this year knows how to do. */
    public static List<Method> methodsInForce(int year) {
        List<Method> out = new ArrayList<>();
        for (Method m : get().methods) {
            if (m.inForce(year)) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * Whether this method can be used against this wall at this place.
     *
     * <p>A mine needs dry ground to dig in and a wall that can be undermined; a ram and a siege
     * tower have to be brought up to the foot of the wall, which water will not allow; and an
     * escalade stops working at about ten metres, which is why the trace italienne needs its
     * ditch and its glacis rather than its height.
     */
    public static boolean usable(Method m, Wall w, Town t) {
        if (m == null || w == null || t == null) {
            return false;
        }
        String ground = t.ground() == null ? "dry" : t.ground();
        switch (m.needs() == null ? "" : m.needs()) {
            case "ground":
                return !"no".equals(w.mine()) && "dry".equals(ground);
            case "approach":
                return "dry".equals(ground) || "rock".equals(ground);
            case "low":
                return w.low();
            default:
                return true;
        }
    }

    /** The fastest breaching method available here, or null when none can open a wall. */
    public static Method bestMethod(Wall w, Town t, int year) {
        Method best = null;
        for (Method m : methodsInForce(year)) {
            if (!m.breaching() || !usable(m, w, t)) {
                continue;
            }
            if (best == null || m.days() < best.days()) {
                best = m;
            }
        }
        return best;
    }

    /**
     * How many days the besiegers need to open a practicable breach, or {@link #NO_BREACH} when
     * nothing they have can do it.
     *
     * <p>The best method sets the pace and everything else they are doing helps a little: a
     * besieger who is mining, battering and blockading at once is not simply as slow as his
     * slowest engine. Effort is how many men he has for the work, measured against the garrison.
     */
    public static int daysToBreach(Wall w, Town t, Army besiegers, int year) {
        if (w == null || t == null || besiegers == null) {
            return NO_BREACH;
        }
        Method best = bestMethod(w, t, year);
        if (best == null) {
            return NO_BREACH;
        }
        int working = 0;
        for (Method m : methodsInForce(year)) {
            if (usable(m, w, t) && (m.breaching() || m.assault())) {
                working++;
            }
        }
        double effort = clamp(besiegers.size() / (double) Math.max(1L, t.garrison() * 4L),
            0.4d, 1.5d);
        double combo = 1.0d + 0.12d * Math.max(0, working - 1);
        return Math.max(1, (int) Math.round(best.days() * w.resist() / (effort * combo)));
    }

    /** How long this army will sit there before disease, winter or another war takes it away. */
    public static int patienceDays(Army besiegers) {
        if (besiegers == null) {
            return (int) get().besiegerBase;
        }
        double s = Math.max(0.0d, Math.min(100.0d, besiegers.supplyLevel())) / 100.0d;
        return (int) Math.round(get().besiegerBase * (1.0d + get().besiegerCurve * s * s));
    }

    /**
     * Run a siege a day at a time.
     *
     * @param reliefDay the day an army of the town's own arrives, or a negative number for
     *                  never
     */
    public static Siege besiege(Town town, Army besiegers, int year, int reliefDay, long salt) {
        return besiege(town, besiegers, year, reliefDay, 0L, salt);
    }

    /**
     * Run a siege a day at a time.
     *
     * @param reliefDay  the day help arrives, or a negative number for never
     * @param reliefSize how big the relieving army is; if the besiegers outnumber it they may
     *                   choose to fight rather than go
     */
    public static Siege besiege(Town town, Army besiegers, int year, int reliefDay,
                                long reliefSize, long salt) {
        if (town == null || besiegers == null) {
            return null;
        }
        Wall wall = wallAt(town.lat(), town.lon(), year);
        List<Method> usable = new ArrayList<>();
        Method worked = null;
        if (wall != null) {
            for (Method m : methodsInForce(year)) {
                if (usable(m, wall, town)) {
                    usable.add(m);
                }
            }
            worked = bestMethod(wall, town, year);
        }
        int breachDay = wall == null ? 1 : daysToBreach(wall, town, besiegers, year);

        double stores = Math.max(0.0d, town.foodDays()) * town.mouths() * grain();
        double blockade = Math.max(0.05d, 1.0d - clamp01(town.supply() / 100.0d));
        double mouths = town.mouths();
        double garrison = Math.min(town.garrison(), town.mouths());
        double garrison0 = garrison;
        double men = besiegers.size();
        double men0 = men;
        int patience = patienceDays(besiegers);
        long dead = 0L;
        long expelled = 0L;
        boolean cut = false;
        boolean mouthsOut = false;
        int promisedDay = -1;
        int nextAssault = -1;
        int day = 0;

        while (day < MAX_DAYS) {
            day++;

            // ---- help arrives. A relief too weak to be fought through is fought through.
            if (reliefDay > 0 && day >= reliefDay) {
                boolean driven = reliefSize > 0 && reliefSize < men * 0.4d;
                if (!driven) {
                    return finish("relief", wall, usable, worked, day, town.mouths(),
                        (long) mouths, (long) garrison0, (long) garrison, (long) men0, (long) men,
                        dead, expelled, breachDay, reliefDay, promisedDay, town, reliefSize);
                }
                men -= (long) (reliefSize * 0.3d);
                reliefDay = -1;
                if (men <= Math.max(1.0d, garrison)) {
                    return finish("abandonment", wall, usable, worked, day, town.mouths(),
                        (long) mouths, (long) garrison0, (long) garrison, (long) men0,
                        Math.max(0L, (long) men), dead, expelled, breachDay, reliefDay,
                        promisedDay, town, reliefSize);
                }
            }
            // ---- the day the gates were promised for
            if (promisedDay > 0 && day >= promisedDay) {
                return finish("terms", wall, usable, worked, day, town.mouths(),
                    (long) mouths, (long) garrison0, (long) garrison, (long) men0, (long) men,
                    dead, expelled, breachDay, reliefDay, promisedDay, town, reliefSize);
            }

            // ---- the food. A commander halves the loaf while there is still bread to halve.
            double perDay = mouths * grain() * blockade;
            double fullLeft = perDay <= 0.0d ? 0.0d : stores / perDay;
            if (!cut && fullLeft <= cutAtDays()) {
                cut = true;
                // de Monluc at Siena: put the useless mouths out, and the bread lasts longer.
                // It takes a hard commander and an obedient town, so it needs morale. And it is
                // a measure of the age of the fortified town: after 1789 a state evacuates its
                // people instead of driving them out through its own gates.
                if (!mouthsOut && mouths > garrison && town.morale() >= 45
                        && year <= uselessLastYear()) {
                    mouthsOut = true;
                    long out = (long) (mouths * uselessShare());
                    expelled += out;
                    mouths -= out;
                }
            }
            double eat = mouths * grain() * (cut ? rationCut() : 1.0d) * blockade;
            stores -= eat;
            if (stores < 0.0d) {
                stores = 0.0d;
            }
            if (cut) {
                long dying = (long) (mouths * famineDeaths());
                mouths -= dying;
                dead += dying;
                garrison = Math.min(garrison, mouths);
            }

            // ---- the last bread is gone
            if (cut && stores <= 0.0d && mouths > 0) {
                // A garrison with any fight left in it opens the gates rather than starve; it is
                // the one that has come apart that lets the town be taken.
                String how = town.morale() >= noSurrenderMorale() ? "starvation"
                    : town.morale() >= bargainingMorale() && town.canTreat(year) ? "terms"
                    : "starvation";
                return finish(how, wall, usable, worked, day, town.mouths(), (long) mouths,
                    (long) garrison0, (long) garrison, (long) men0, (long) men, dead, expelled,
                    breachDay, reliefDay, promisedDay, town, reliefSize);
            }
            if (mouths <= 0) {
                return finish("starvation", wall, usable, worked, day, town.mouths(), 0L,
                    (long) garrison0, 0L, (long) men0, (long) men, dead, expelled, breachDay,
                    reliefDay, promisedDay, town, reliefSize);
            }

            // ---- the besiegers' own clock
            if (day > patience) {
                return finish("abandonment", wall, usable, worked, day, town.mouths(),
                    (long) mouths, (long) garrison0, (long) garrison, (long) men0, (long) men,
                    dead, expelled, breachDay, reliefDay, promisedDay, town, reliefSize);
            }

            // ---- the wall
            boolean open = breachDay > 0 && day >= breachDay;
            // a wall with almost no one left on it can be climbed whatever state it is in
            boolean thinGarrison = garrison < garrison0 * 0.4d;

            // ---- while the wall is still going to hold, a commander with no help coming talks
            // A commander talks while he still has a wall to bargain with, which is the whole
            // trick of it: surrender after the breach is open is not a bargain, it is a
            // capitulation, and the rules of war do not protect it. So the offer goes in well
            // before the engines have finished, and it names a day so that help has a chance.
            // and only if the people inside can be held to it. A conditional surrender signed by
            // a warlord, a rising, or an unpaid company is not a bargain, so such a place gets
            // no offer: it is stormed, or it starves.
            if (promisedDay < 0 && breachDay > 0 && day >= Math.max(1, (int) (breachDay * 0.6d))
                    && town.morale() < noSurrenderMorale() && town.canTreat(year)
                    && !(reliefDay > 0 && reliefDay <= day + TERMS_NOTICE)) {
                promisedDay = Math.max(day + 1, Math.min(day + TERMS_NOTICE, breachDay - 3));
            }

            // ---- the assault
            if ((open || thinGarrison) && promisedDay < 0) {
                if (nextAssault < 0) {
                    nextAssault = day;
                }
                if (day >= nextAssault) {
                    double over = men / Math.max(1.0d, garrison)
                        / (wall == null ? 1.0d : wall.storm());
                    double odds = clamp01((open ? stormBase() : stormNoBreach())
                        + stormPerMan() * over);
                    if (uniform(salt * 31L + day, 2246822519L) < odds) {
                        return finish("storm", wall, usable, worked, day, town.mouths(),
                            (long) mouths, (long) garrison0, (long) garrison, (long) men0,
                            (long) men, dead, expelled, breachDay, reliefDay, promisedDay, town,
                            reliefSize);
                    }
                    long lost = (long) (men * stormCost() * (wall == null ? 1.0d : wall.storm()));
                    men -= lost;
                    nextAssault = day + ASSAULT_GAP;
                    if (men <= 0) {
                        return finish("abandonment", wall, usable, worked, day, town.mouths(),
                            (long) mouths, (long) garrison0, (long) garrison, (long) men0, 0L,
                            dead, expelled, breachDay, reliefDay, promisedDay, town, reliefSize);
                    }
                }
            }
        }
        return finish("abandonment", wall, usable, worked, MAX_DAYS, town.mouths(), (long) mouths,
            (long) garrison0, (long) garrison, (long) men0, (long) men, dead, expelled, breachDay,
            reliefDay, promisedDay, town, reliefSize);
    }

    private static Siege finish(String id, Wall wall, List<Method> usable, Method worked, int day,
                                long mouths0, long mouthsNow, long garrison0, long garrisonNow,
                                long men0, long menNow, long dead, long expelled, int breachDay,
                                int reliefDay, int promisedDay, Town town, long reliefSize) {
        Ending e = ending(id);
        return new Siege(id, e, wall, Collections.unmodifiableList(usable), worked, day, mouths0,
            mouthsNow, garrison0, garrisonNow, men0, menNow, dead, expelled, breachDay, reliefDay,
            promisedDay, buildLine(id, e, wall, worked, day, mouths0, mouthsNow, garrison0,
                men0, menNow, dead, expelled, breachDay, reliefDay, promisedDay, town));
    }

    public static Ending ending(String id) {
        for (Ending e : get().endings) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static String buildLine(String id, Ending e, Wall wall, Method worked, int day,
                                    long mouths0, long mouthsNow, long garrison0, long men0,
                                    long menNow, long dead, long expelled, int breachDay,
                                    int reliefDay, int promisedDay, Town town) {
        StringBuilder sb = new StringBuilder();
        sb.append(day).append(day == 1 ? " day" : " days").append(". ");
        if (breachDay == NO_BREACH && wall != null && wall.resist() >= 60.0d) {
            sb.append("There was no wall here to open - ").append(wall.name())
              .append(". This was never a question of walls. ");
        } else if (breachDay == NO_BREACH) {
            sb.append("No method the besiegers had could open a breach in ")
              .append(wall == null ? "this place" : wall.name())
              .append(", so this was a hunger siege or it was nothing. ");
        } else if (worked != null && breachDay > 0) {
            sb.append("The breach was practicable on day ").append(breachDay)
              .append(", opened by ").append(worked.name()).append(". ");
        }
        if ("terms".equals(id)) {
            sb.append(promisedDay > 0
                ? "The garrison offered to open the gates on day " + promisedDay
                    + " if no relief came, and it did not come. "
                : "The garrison opened the gates rather than starve. ");
            sb.append("They marched out with their arms and their lives, and the town was not "
                + "sacked: a place that surrendered could not be ransacked. ");
        } else if ("starvation".equals(id)) {
            sb.append("The last bread went, and the garrison was past bargaining. ");
            sb.append("The town was at discretion. ");
        } else if ("relief".equals(id)) {
            sb.append("An army of their own arrived on day ").append(reliefDay)
              .append(", the besiegers would not stand to be caught against the walls, and the "
                  + "siege was lifted. ");
        } else if ("abandonment".equals(id)) {
            sb.append("The besiegers went home: disease, winter, their own supply, or a war "
                + "somewhere else. ");
        } else if ("storm".equals(id)) {
            sb.append("The assault went in and the place was carried. ");
            sb.append("A town taken by force could be ransacked, and this one was. ");
        }
        sb.append("Of ").append(mouths0).append(" people, ").append(dead)
          .append(dead == 1 ? " was dead" : " were dead").append(", and ")
          .append(Math.max(0L, mouthsNow)).append(" came through");
        if (expelled > 0) {
            sb.append("; ").append(expelled)
              .append(" were put out of the gates as useless mouths, and more than half of them "
                  + "died in the ditch between the two armies");
        }
        sb.append(". The besiegers came with ").append(men0).append(" and left with ")
          .append(Math.max(0L, menNow)).append('.');
        return sb.toString();
    }

    /** The register's answer in one line, for a tooltip. */
    public static String card(Town town, Army besiegers, int year, int reliefDay, long salt) {
        Siege s = besiege(town, besiegers, year, reliefDay, salt);
        if (s == null || s.ending() == null) {
            return "siege: nothing to besiege";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(s.wall() == null ? "no recorded wall" : s.wall().name())
          .append(" against ").append(s.methods().size()).append(" methods");
        sb.append("\n  ").append(s.days()).append(" days | ").append(s.ending().name())
          .append(s.sacked() ? " *" : "");
        if (s.breachDay() == NO_BREACH) {
            sb.append(" | no breach possible");
        }
        return sb.toString();
    }

    // ================================================================== accessors

    public static double grain() {
        return get().grain;
    }

    public static double rationCut() {
        return get().rationCut;
    }

    public static int cutAtDays() {
        return get().cutAtDays;
    }

    public static int uselessLastYear() {
        return get().uselessLastYear;
    }

    public static int noSurrenderMorale() {
        return get().noSurrender;
    }

    public static double famineDeaths() {
        return get().famineDeaths;
    }

    public static double uselessShare() {
        return get().uselessShare;
    }

    public static double uselessGain() {
        return get().uselessGain;
    }

    public static int waterDays() {
        return get().waterDays;
    }

    public static double stormBase() {
        return get().stormBase;
    }

    public static double stormCost() {
        return get().stormCost;
    }

    public static double stormPerMan() {
        return get().stormPerMan;
    }

    public static double stormNoBreach() {
        return get().stormNoBreach;
    }

    public static int bargainingMorale() {
        return get().bargaining;
    }

    // ================================================================== plumbing

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp01(double v) {
        return Math.max(0.0d, Math.min(1.0d, v));
    }

    private static double uniform(long salt, long tweak) {
        long x = salt * 0x9E3779B97F4A7C15L ^ tweak * 0xC2B2AE356165667CL;
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null ? "" : e.getAsString();
    }

    private static double num(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsDouble();
    }

    private static JsonObject read(String resource) {
        try (InputStream in = Sieges.class.getResourceAsStream(resource)) {
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
        "/data/eternalengine/eternal/history/sieges.json.gz";
}

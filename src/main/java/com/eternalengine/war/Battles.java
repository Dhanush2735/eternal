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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 40 (D10 armies in action): <b>how a battle comes out, and why</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>{@code BattleSimulator} works out a win probability from the size ratio, morale, supply, a
 * technology gap and a terrain bonus, rolls once, and then sets the losses at
 * <b>8% for the winner and 18% for the loser, unconditionally</b>. It is one dice roll and two
 * constants. It does not know what year it is, what either army is carrying, how either army
 * stands, or what happened to the one that lost.
 *
 * <p>That last one is the whole defect. Peter Krentz gathered every surviving casualty figure from
 * Classical Greek sources and found the winner lost about <b>5%</b> and the loser about
 * <b>14%</b> — honestly a range of 3–10% against 10–20%. The gap between those two numbers is not
 * marksmanship. It is that <b>most of the killing in pre-modern battle happened after one side
 * broke and ran</b>. A constant cannot say that; a model of the break can.
 *
 * <h3>What this register does</h3>
 *
 * <ol>
 * <li>asks what formation each army would actually have used — place, year and all, from
 *     {@code history/formations.json.gz};</li>
 * <li>weighs the numbers, the morale, the supply, <b>which kind of formation has the better of
 *     which</b>, and <b>whether each army is standing on the ground it wants</b>;</li>
 * <li>rolls, because a battle is not determined by arithmetic and a register that says it is will
 *     produce the same winner every time;</li>
 * <li>decides <b>how the loser goes</b> — gave ground in order, broke and ran, broken in the open
 *     with horse behind it, or encircled — and that, not the dice, is what sets the casualty
 *     list;</li>
 * <li>and says all of it out loud, marking the parts that are this register's reading rather than
 *     any source's.</li>
 * </ol>
 *
 * <h3>The finding that will surprise a player</h3>
 *
 * <p><b>Casualty rates in land combat have been falling since the seventeenth century.</b> Weapons
 * became enormously more lethal and losses went down, because armies dispersed faster than
 * lethality rose: Dupuy's daily loss rates run about 21–23 per thousand in the Napoleonic and
 * Civil War eras — the Civil War being the case that proves the rule, since it threw massed
 * formations against strong positions — then 12 in the First World War and 9 in the Second. So the
 * rifle band in this register is <i>lower</i> than the musket band, and that is deliberate.
 *
 * <p><b>It does not stage the fighting.</b> Step 45 owns weapons and armour as things, and step 39
 * owns the scene. This answers what happened and why, and hands the answer to both.
 */
public final class Battles {

    /** One way of standing, in one place, for a stretch of years (part 2's table). */
    public record Formation(String id, String name, String shape, int ranks, int start, int end,
                            double[] box, String arm, String troop, String purpose, String ground,
                            String needs, String doing, String note, String source, String mark) {
        public boolean covers(int year) {
            return start <= year && year <= end;
        }

        public boolean here(double lat, double lon) {
            return box[0] <= lat && lat <= box[1] && box[2] <= lon && lon <= box[3];
        }

        /** "sixteen ranks", or "" where the record does not say. */
        public String depth() {
            return ranks <= 0 ? "" : ranks + (ranks == 1 ? " rank" : " ranks");
        }
    }

    /** How many of each side are lost, for the arms of the era. */
    public record Band(String id, String name, List<String> arms, double wlo, double whi,
                       double llo, double lhi, String note, String source, String mark) {}

    /** How the losing army goes, and what that costs it. */
    public record How(String id, String name, double mult, String when, String note,
                      String source, String mark) {}

    /** What a battle came to. */
    public record Outcome(int winner, Formation aForm, Formation dForm, Band band, How how,
                          long attackerLosses, long defenderLosses, double chance,
                          String line) {
        /** 1 the attacker won, 2 the defender won, 0 a draw. */
        public boolean draw() {
            return winner == 0;
        }

        /** The share of the losing army that is gone. */
        public double loserShare(long loserBefore) {
            long gone = winner == 2 ? attackerLosses : defenderLosses;
            return loserBefore <= 0 ? 0.0d : Math.min(1.0d, gone / (double) loserBefore);
        }
    }

    private static final Battles INSTANCE = new Battles();
    private final List<Formation> forms;
    private final List<Band> bands;
    private final List<How> breaks;
    private final Map<String, Double> matchup;
    private final Map<String, Double> groundCost;
    private final double groundBonus;
    private final double rollLow;
    private final double rollHigh;
    private final double shattered;
    private final double routMorale;
    private final double routSupply;
    private final double routRatio;
    private final double intactMorale;
    private final double intactSupply;
    private final double escapeChance;
    private final double surroundedOdds;
    private final double pursuedOdds;
    private final double narrowOdds;
    private final double ceiling;

    private Battles() {
        List<Formation> f = new ArrayList<>();
        JsonObject fo = read(RESOURCE_FORMATIONS);
        if (fo != null && fo.has("formations")) {
            for (JsonElement e : fo.getAsJsonArray("formations")) {
                JsonObject o = e.getAsJsonObject();
                JsonArray b = o.getAsJsonArray("box");
                f.add(new Formation(str(o, "id"), str(o, "name"), str(o, "shape"),
                    o.has("ranks") ? o.get("ranks").getAsInt() : 0,
                    o.get("start").getAsInt(), o.get("end").getAsInt(),
                    new double[] {b.get(0).getAsDouble(), b.get(1).getAsDouble(),
                        b.get(2).getAsDouble(), b.get(3).getAsDouble()},
                    str(o, "arm"), str(o, "troop"), str(o, "purpose"), str(o, "ground"),
                    str(o, "needs"), str(o, "doing"), str(o, "note"), str(o, "source"),
                    str(o, "mark")));
            }
        }
        this.forms = Collections.unmodifiableList(f);

        List<Band> bs = new ArrayList<>();
        List<How> ks = new ArrayList<>();
        Map<String, Double> mu = new LinkedHashMap<>();
        Map<String, Double> gc = new LinkedHashMap<>();
        double gbon = 0.0d;
        double rlo = 0.05d;
        double rhi = 0.95d;
        double shat = 0.45d;
        double rMor = 50.0d;
        double rSup = 35.0d;
        double rRat = 1.5d;
        double iMor = 55.0d;
        double iSup = 45.0d;
        double esc = 0.75d;
        double sur = 0.15d;
        double pur = 0.08d;
        double nar = 0.10d;
        double ceil = 0.85d;
        JsonObject bo = read(RESOURCE_BATTLES);
        if (bo != null) {
            if (bo.has("bands")) {
                for (JsonElement e : bo.getAsJsonArray("bands")) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> arms = new ArrayList<>();
                    for (JsonElement a : o.getAsJsonArray("arms")) {
                        arms.add(a.getAsString());
                    }
                    JsonArray w = o.getAsJsonArray("winner");
                    JsonArray l = o.getAsJsonArray("loser");
                    bs.add(new Band(str(o, "id"), str(o, "name"), arms,
                        w.get(0).getAsDouble(), w.get(1).getAsDouble(),
                        l.get(0).getAsDouble(), l.get(1).getAsDouble(),
                        str(o, "note"), str(o, "source"), str(o, "mark")));
                }
            }
            if (bo.has("breaks")) {
                for (JsonElement e : bo.getAsJsonArray("breaks")) {
                    JsonObject o = e.getAsJsonObject();
                    ks.add(new How(str(o, "id"), str(o, "name"), o.get("mult").getAsDouble(),
                        str(o, "when"), str(o, "note"), str(o, "source"), str(o, "mark")));
                }
            }
            if (bo.has("matchup")) {
                for (JsonElement e : bo.getAsJsonArray("matchup")) {
                    JsonObject o = e.getAsJsonObject();
                    mu.put(o.get("a").getAsString() + "|" + o.get("b").getAsString(),
                        o.get("adv").getAsDouble());
                }
            }
            if (bo.has("ground")) {
                for (JsonElement e : bo.getAsJsonArray("ground")) {
                    JsonObject o = e.getAsJsonObject();
                    gc.put(o.get("wants").getAsString() + "|" + o.get("on").getAsString(),
                        o.get("cost").getAsDouble());
                }
            }
            if (bo.has("groundBonus")) {
                gbon = bo.get("groundBonus").getAsDouble();
            }
            if (bo.has("roll")) {
                JsonArray r = bo.getAsJsonArray("roll");
                rlo = r.get(0).getAsDouble();
                rhi = r.get(1).getAsDouble();
            }
            if (bo.has("shattered")) {
                shat = bo.get("shattered").getAsDouble();
            }
            if (bo.has("breakRules")) {
                JsonObject br = bo.getAsJsonObject("breakRules");
                rMor = num(br, "routMorale", rMor);
                rSup = num(br, "routSupply", rSup);
                rRat = num(br, "routRatio", rRat);
                iMor = num(br, "intactMorale", iMor);
                iSup = num(br, "intactSupply", iSup);
                esc = num(br, "escapeChance", esc);
                sur = num(br, "surroundedOdds", sur);
                pur = num(br, "pursuedOdds", pur);
                nar = num(br, "narrowOdds", nar);
                ceil = num(br, "ceiling", ceil);
            }
        }
        this.bands = Collections.unmodifiableList(bs);
        this.breaks = Collections.unmodifiableList(ks);
        this.matchup = Collections.unmodifiableMap(mu);
        this.groundCost = Collections.unmodifiableMap(gc);
        this.groundBonus = gbon;
        this.rollLow = rlo;
        this.rollHigh = rhi;
        this.shattered = shat;
        this.routMorale = rMor;
        this.routSupply = rSup;
        this.routRatio = rRat;
        this.intactMorale = iMor;
        this.intactSupply = iSup;
        this.escapeChance = esc;
        this.surroundedOdds = sur;
        this.pursuedOdds = pur;
        this.narrowOdds = nar;
        this.ceiling = ceil;
    }

    /** The one register. */
    public static Battles get() {
        return INSTANCE;
    }

    public static List<Formation> formations() {
        return get().forms;
    }

    public static List<Band> bands() {
        return get().bands;
    }

    public static List<How> breaks() {
        return get().breaks;
    }

    public static int formationCount() {
        return get().forms.size();
    }

    // ================================================================== what stands where, when

    /** The formation this place and year would have used, or null where the table has no answer.
     * Where two are in force, the later one wins: an army does not go back to the phalanx. */
    public static Formation formationAt(double lat, double lon, int year) {
        Formation best = null;
        for (Formation f : get().forms) {
            if (!f.covers(year) || !f.here(lat, lon)) {
                continue;
            }
            if (best == null || f.start() > best.start()) {
                best = f;
            }
        }
        return best;
    }

    /** Every formation in force here in this year, oldest first. */
    public static List<Formation> formationsAt(double lat, double lon, int year) {
        List<Formation> out = new ArrayList<>();
        for (Formation f : get().forms) {
            if (f.covers(year) && f.here(lat, lon)) {
                out.add(f);
            }
        }
        out.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return out;
    }

    /** The casualty band for this weapon. */
    public static Band bandFor(String arm) {
        for (Band b : get().bands) {
            if (b.arms().contains(arm)) {
                return b;
            }
        }
        return get().bands.isEmpty() ? null : get().bands.get(0);
    }

    /** The band an army fights under: its own formation's weapon, or the shock band where nothing
     * is known. */
    public static Band bandOf(Formation f) {
        return bandFor(f == null ? "spear" : f.arm());
    }

    /**
     * The casualty band for a fight between two arms.
     *
     * <p>When the two sides carry different weapons, the deadlier arm present sets the scale of
     * the casualty list: put bowmen against spearmen and the losses are the bow fight's losses,
     * not a tidy average of the two. When either side is unknown the answer falls back to the one
     * that is known, and then to the shock band \u2014 the only band in this register that any
     * source actually measured.
     */
    public static Band bandForTwo(Formation af, Formation df) {
        if (af == null && df == null) {
            return bandFor("spear");
        }
        if (af == null) {
            return bandOf(df);
        }
        if (df == null) {
            return bandOf(af);
        }
        Band a = bandOf(af);
        Band d = bandOf(df);
        return d.lhi() > a.lhi() ? d : a;
    }

    // ================================================================== the fight

    /**
     * Fight it out.
     *
     * @param ground the ground it is fought on: {@code level}, {@code open}, {@code broken} or
     *               {@code close}
     */
    public static Outcome fight(Army attacker, Army defender, String ground, int year,
                                long salt) {
        if (attacker == null || defender == null) {
            return null;
        }
        return fight(attacker, defender,
            formationAt(attacker.latitude(), attacker.longitude(), year),
            formationAt(defender.latitude(), defender.longitude(), year), ground, year, salt);
    }

    /**
     * Fight it out, with each army's formation given.
     *
     * <p>The shorter form assumes both armies stand the way armies stood <i>where the battle is</i>
     * \u2014 which is usually right, and is what the civilization engine can know. But a campaign
     * is fought by an army that came from somewhere: a Roman legion in Macedon still fights in
     * maniples. Callers that know where each army came from should pass each formation here.
     *
     * @param ground the ground it is fought on: {@code level}, {@code open}, {@code broken} or
     *               {@code close}
     */
    public static Outcome fight(Army attacker, Army defender, Formation af, Formation df,
                                String ground, int year, long salt) {
        if (attacker == null || defender == null) {
            return null;
        }
        Band band = bandForTwo(af, df);

        double sizeRatio = attacker.size()
            / (double) Math.max(1L, attacker.size() + defender.size());
        double morale = (attacker.morale() - defender.morale()) / 300.0d;
        double supply = (attacker.supplyLevel() - defender.supplyLevel()) / 300.0d;
        double match = matchup(af, df);
        double groundEdge = groundEdge(af, df, ground);

        double p = clamp(sizeRatio + morale + supply + match + groundEdge);
        double roll = roll(salt, attacker.armyId(), defender.armyId(), year);
        int winner;
        if (Math.abs(roll - p) < 0.08d) {
            winner = 0;
        } else {
            winner = roll < p ? 1 : 2;
        }

        Army won = winner == 1 ? attacker : defender;
        Army lost = winner == 1 ? defender : attacker;
        Formation wf = winner == 1 ? af : df;
        Formation lf = winner == 1 ? df : af;

        How how = howItBroke(won, lost, wf, lf, ground, p, roll, salt);
        long winLoss = Math.round(won.size() * pick(band.wlo(), band.whi(), 1.0d - p, salt + 11L)
            / 100.0d);
        double share = pick(band.llo(), band.lhi(), 1.0d - p, salt + 29L) / 100.0d;
        share = Math.min(get().ceiling, share * how.mult());
        long loseLoss = Math.round(lost.size() * share);

        winLoss = Math.min(winLoss, won.size());
        loseLoss = Math.min(loseLoss, lost.size());

        return new Outcome(winner, af, df, band, how,
            winner == 1 ? winLoss : loseLoss, winner == 1 ? loseLoss : winLoss, p,
            buildLine(winner, af, df, band, how, winLoss, loseLoss, p,
                winner == 1 ? attacker : defender, winner == 1 ? defender : attacker));
    }

    /** Which kind of formation has the better of which, in points of probability. */
    public static double matchup(Formation a, Formation d) {
        if (a == null || d == null) {
            return 0.0d;
        }
        Double v = get().matchup.get(a.purpose() + "|" + d.purpose());
        return v == null ? 0.0d : v.doubleValue();
    }

    /** The difference between what each army wants underfoot and what it has got. */
    public static double groundEdge(Formation a, Formation d, String ground) {
        return groundFor(a, ground) - groundFor(d, ground);
    }

    private static double groundFor(Formation f, String ground) {
        if (f == null || ground == null) {
            return 0.0d;
        }
        if (f.ground().equals(ground)) {
            return get().groundBonus;
        }
        Double v = get().groundCost.get(f.ground() + "|" + ground);
        return v == null ? 0.0d : v.doubleValue();
    }

    /**
     * How the loser goes, which is what sets the casualty list.
     *
     * <p>An army that is enveloped dies where it stands. One that is broken in the open with horse
     * behind it is ridden down. One that is merely beaten by a slightly better army, in the dark,
     * with its officers still alive, goes back the way it came and is an army next week. The dice
     * decides who wins; this decides what winning was worth.
     */
    public static How howItBroke(Army won, Army lost, Formation wf, Formation lf, String ground,
                                 double p, double roll, long salt) {
        List<How> all = get().breaks;
        if (all.isEmpty()) {
            return null;
        }
        How idOf = all.get(0);
        for (How h : all) {
            if ("withdrawn".equals(h.id())) {
                idOf = h;
            }
        }
        String id = "broken";
        boolean enveloped = wf != null && "envelope".equals(wf.purpose());
        boolean horse = wf != null && ("cavalry".equals(wf.troop()) || "horse".equals(wf.troop()));
        boolean open = "open".equals(ground) || "level".equals(ground);
        double odds = Math.abs(p - roll);

        // A rout needs an army that cannot hold a line: too few, too tired, or too hungry to
        // form one. Without this gate every lopsided battle is a massacre, and the whole point
        // of Krentz's figure is that most battles were not. An army of roughly equal size, well
        // fed and still led, goes back the way it came - beaten, not destroyed.
        Battles reg = get();
        boolean routed = lost.morale() < reg.routMorale || lost.supplyLevel() < reg.routSupply
            || won.size() > lost.size() * reg.routRatio;
        boolean intact = lost.morale() >= reg.intactMorale
            && lost.supplyLevel() >= reg.intactSupply;
        // Whether a beaten but intact line actually gets away is its own dice roll, and that
        // is the honest answer: two defeats that look identical on paper end differently
        // because of the light, the ground behind them, and whether an officer held them.
        boolean escaped = uniform(salt * 31L + (int) (odds * 1000.0d), 15485863L)
            < reg.escapeChance;

        if (enveloped && routed && odds > reg.surroundedOdds) {
            id = "surrounded";       // both horns got round the back and closed
        } else if ((enveloped || (horse && open)) && routed && odds > reg.pursuedOdds) {
            id = "pursued";          // it went in daylight with something faster behind it
        } else if (intact && escaped) {
            id = "withdrawn";        // still fed, still led: it gives ground and stays an army
        } else if (odds <= reg.narrowOdds) {
            id = "withdrawn";        // so narrow a verdict that the loser simply gave ground
        } else {
            id = "broken";           // shaky, hungry or out-generalled: the line comes apart
        }
        for (How h : all) {
            if (h.id().equals(id)) {
                return h;
            }
        }
        return idOf;
    }

    /** Formation names in the pack carry their own article; never print it twice. */
    private static String plain(Formation f) {
        if (f == null) {
            return "army with no recorded way of standing";
        }
        String n = f.name() == null ? f.id() : f.name();
        if (n.length() > 4 && (n.startsWith("the ") || n.startsWith("The "))) {
            n = n.substring(4);
        }
        return n;
    }

    private static String buildLine(int winner, Formation af, Formation df, Band band, How how,
                                    long winLoss, long loseLoss, double p, Army won, Army lost) {
        StringBuilder sb = new StringBuilder();
        boolean same = af != null && df != null && af.id().equals(df.id());
        if (winner == 0) {
            sb.append(same ? "the " + plain(af) + " on both sides"
                           : "the " + plain(af) + " and the " + plain(df))
              .append(" fought to a standstill: neither line went, and both went back.");
        } else if (same) {
            sb.append("the ").append(plain(af)).append(", on both sides, broke its like")
              .append(" — ").append(how.name()).append(": ").append(how.when())
              .append('.');
        } else {
            sb.append("the ").append(plain(af)).append(" broke the ").append(plain(df))
              .append(" — ").append(how.name()).append(": ").append(how.when())
              .append('.');
        }
        if (winner == 0) {
            sb.append(" The side that gave less lost ").append(pct(winLoss, won.size()))
              .append(", the other ").append(pct(loseLoss, lost.size())).append('.');
            if (!"recorded".equals(band.mark())) {
                sb.append(" The casualty band is estimated, not a source's figure.");
            }
            return sb.toString();
        }
        sb.append(" The winner lost ").append(pct(winLoss, won.size())).append(", the loser ")
          .append(pct(loseLoss, lost.size())).append('.');
        if (how != null && how.mult() > 1.0d) {
            sb.append(" Most of the loser's casualties were taken after it broke, not during the "
                + "fighting: that is what a pursuit is.");
        }
        if (!"recorded".equals(band == null ? "" : band.mark())) {
            sb.append(" The casualty band is ").append(band.mark())
              .append(", not a source's figure.");
        }
        return sb.toString();
    }

    private static String pct(long n, long of) {
        if (of <= 0) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%.0f%%", 100.0d * n / (double) of);
    }

    /** The register's answer in one sentence, for a tooltip. */
    public static String card(Army attacker, Army defender, String ground, int year, long salt) {
        return card(attacker, defender,
            attacker == null ? null
                : formationAt(attacker.latitude(), attacker.longitude(), year),
            defender == null ? null
                : formationAt(defender.latitude(), defender.longitude(), year),
            ground, year, salt);
    }

    /** The register's answer in one sentence, for a tooltip, with each army's formation given. */
    public static String card(Army attacker, Army defender, Formation af, Formation df,
                              String ground, int year, long salt) {
        Outcome o = fight(attacker, defender, af, df, ground, year, salt);
        if (o == null) {
            return "battle: nothing to fight";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(plain(o.aForm())).append(" against ").append(plain(o.dForm()));
        sb.append("\n  odds ").append(String.format(Locale.ROOT, "%.0f%%", o.chance() * 100.0d))
          .append(" | ").append(o.how() == null ? "?" : o.how().name());
        return sb.toString();
    }

    /** Apply the outcome to the two armies, the way the old simulator did. */
    /**
     * Put the outcome on the two armies.
     *
     * <p>Step 40 part 6 wired this to {@link Morale}, and it is worth saying what was there
     * before: {@code +10} for the winner, {@code -15} for the loser, {@code -2} each for a draw,
     * with no reference to how badly either had been hurt. Three integers, in a register whose
     * whole subject is how much men will stand.
     *
     * <p>Now the winner is credited with the register's {@code victory} factor, the loser is
     * charged its {@code defeat} factor, and <b>the loser's charge is scaled by how badly it
     * actually went</b> against the measured loser's rate — so an army that gave ground in order
     * loses a little morale and an army that was ridden down loses twice as much, which is the
     * difference Krentz's two bands are measuring. Losses go through {@link Army#takeLosses} so
     * that an army carries what it has lost and {@link Army#routing} can be asked later.
     */
    public static void apply(Outcome o, Army attacker, Army defender) {
        if (o == null || attacker == null || defender == null) {
            return;
        }
        long aBefore = attacker.size();
        long dBefore = defender.size();
        attacker.takeLosses(Math.min(aBefore, o.attackerLosses()));
        defender.takeLosses(Math.min(dBefore, o.defenderLosses()));
        int[] delta = moraleChange(o, aBefore, dBefore);
        attacker.adjustMorale(delta[0]);
        defender.adjustMorale(delta[1]);
    }

    /** { attacker, defender }. */
    public static int[] moraleChange(Outcome o, long aBefore, long dBefore) {
        int win = Morale.factorWeight("victory", 8);
        int lose = Morale.factorWeight("defeat", -12);
        if (o.draw()) {
            // du Picq: "that mutual weariness which brings about, with tacit accord, falling back
            // for a breathing spell on both sides". No source puts a number on a drawn battle, so
            // the register charges each side half of what winning would have gained and says here
            // that the number is its own.
            int d = -Math.max(1, Math.abs(win) / 2);
            return new int[] { d, d };
        }
        long lostBefore = o.winner() == 1 ? dBefore : aBefore;
        double share = o.loserShare(lostBefore) * 100.0d;
        double scale = Math.min(2.0d, share / Math.max(0.1d, Morale.loserLoss()));
        int lost = (int) Math.round(lose * scale);
        return o.winner() == 1 ? new int[] { win, lost } : new int[] { lost, win };
    }

    // ================================================================== numbers

    private static double num(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsDouble();
    }

    private static double pick(double lo, double hi, double t, long salt) {
        double x = uniform(salt, 7919L);
        double m = Math.max(0.0d, Math.min(1.0d, t));
        return lo + (hi - lo) * (0.35d * x + 0.65d * m);
    }

    private static double roll(long salt, String a, String b, int year) {
        long h = salt;
        h = h * 31L + (a == null ? 0 : a.hashCode());
        h = h * 31L + (b == null ? 0 : b.hashCode());
        h = h * 131L + year;
        return rollLow() + (rollHigh() - rollLow()) * uniform(h, 104729L);
    }

    private static double rollLow() {
        return get().rollLow;
    }

    private static double rollHigh() {
        return get().rollHigh;
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

    private static double clamp(double v) {
        return Math.max(rollLow(), Math.min(rollHigh(), v));
    }

    private static JsonObject read(String resource) {
        try (InputStream in = Battles.class.getResourceAsStream(resource)) {
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

    private static final String RESOURCE_FORMATIONS =
        "/data/eternalengine/eternal/history/formations.json.gz";
    private static final String RESOURCE_BATTLES =
        "/data/eternalengine/eternal/history/battles.json.gz";

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}

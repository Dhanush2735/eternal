package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * <b>G3: the what-if engine.</b> A player changed something; what follows?
 *
 * <h2>What this class does not do</h2>
 *
 * <p>It does not simulate a society, and it does not re-derive physics the mod already has. The strength of
 * an army is <b>given</b> to it, as a win probability from the battle model, because that model exists in
 * {@code war/BattleSimulator} and already weighs size, morale, supply, formation matchup, ground, technology
 * and terrain. Recomputing it here would be a second answer to the same question, which this project has
 * refused at every step.
 *
 * <p>What it does is <b>recompute the registers</b>: given the world as the real systems describe it, and a
 * change a player made, it says what is different afterwards, in five places, and refuses two.
 *
 * <h2>The seven things G3 lists, and what happens to each</h2>
 *
 * <table>
 *   <tr><th>field</th><th>computed from</th></tr>
 *   <tr><td><b>war</b></td><td>the given win probability, moved by the change, clamped</td></tr>
 *   <tr><td><b>border</b></td><td>whether the probability crosses the halfway mark, and what the loser
 *       holds of the disputed places</td></tr>
 *   <tr><td><b>heir</b></td><td>four succession laws, applied to the heirs the caller has</td></tr>
 *   <tr><td><b>technology</b></td><td>the arrival year the atlas already gives, moved, then spread by
 *       distance</td></tr>
 *   <tr><td><b>faith</b></td><td>a share moved, then spread by distance</td></tr>
 *   <tr><td><b>population</b></td><td><b>refused</b>: a population figure past the last measured year is
 *       the guardrail's oldest refusal</td></tr>
 *   <tr><td><b>money</b></td><td><b>refused</b>: a price is the other one, and a what-if that invented one
 *       would be inventing the thing every price in this project refused to invent</td></tr>
 * </table>
 *
 * <p>The survey in part 1 said one of the seven could not be computed. That was wrong, and the correction is
 * recorded here: <b>two</b> cannot, population and money, and the money one is refused for the same reason
 * the price tables are silent past their last measurement.
 *
 * <h2>Two rules that keep the answer small</h2>
 *
 * <p><b>Effects fade with distance and time</b>: every consequence carries how far it is from the change, in
 * km and in years, and the size of the effect is scaled by both. <b>Big trends resist</b>: the caller says
 * how strong the trend a change pushes against is, and a change smaller than the trend is reported as
 * <i>absorbed</i> rather than as a consequence, because most of what a person does is absorbed.
 */
public final class WhatIf {

    /** The seven fields G3 names. Five are computed and two are refused, by name. */
    public enum Field {
        WAR("the war"),
        BORDER("the border"),
        HEIR("the succession"),
        TECHNOLOGY("the invention"),
        FAITH("the faith"),
        POPULATION("the people"),
        MONEY("the money");

        private final String words;

        Field(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }

        /** True for the two the guardrail will not let this class compute. */
        public boolean refused() {
            return this == POPULATION || this == MONEY;
        }
    }

    /** What a what-if is allowed to look at, supplied by the caller from the real systems. */
    public record World(int year, String place, double lat, double lon,
                        String attacker, String defender, String disputedPlace,
                        double warProbability, double trendStrength,
                        @Nullable String rulerName, List<Heir> heirs, SuccessionLaw law,
                        @Nullable String invention, int inventionArrivalYear,
                        @Nullable String faithName, double faithShare) {

        public World {
            place = place == null ? "here" : place;
            attacker = attacker == null ? "" : attacker;
            defender = defender == null ? "" : defender;
            disputedPlace = disputedPlace == null ? "" : disputedPlace;
            heirs = heirs == null ? List.of() : List.copyOf(heirs);
            warProbability = clamp01(warProbability);
            trendStrength = clamp01(trendStrength);
            faithShare = clamp01(faithShare);
            law = law == null ? SuccessionLaw.PRIMOGENITURE : law;
        }

        /** The floor: a place with nothing happening in it. */
        public static World quiet(int year, String place, double lat, double lon) {
            return new World(year, place, lat, lon, "", "", "", 0.5, 0.0, null, List.of(),
                    SuccessionLaw.PRIMOGENITURE, null, Integer.MIN_VALUE, null, 0.0);
        }

        public World withWar(String attacker, String defender, String disputed, double probability) {
            return new World(year, place, lat, lon, attacker, defender, disputed, probability,
                    trendStrength, rulerName, heirs, law, invention, inventionArrivalYear, faithName,
                    faithShare);
        }

        public World withTrend(double strength) {
            return new World(year, place, lat, lon, attacker, defender, disputedPlace, warProbability,
                    strength, rulerName, heirs, law, invention, inventionArrivalYear, faithName,
                    faithShare);
        }

        public World withSuccession(String ruler, List<Heir> h, SuccessionLaw l) {
            return new World(year, place, lat, lon, attacker, defender, disputedPlace, warProbability,
                    trendStrength, ruler, h, l, invention, inventionArrivalYear, faithName, faithShare);
        }

        public World withInvention(String name, int arrivalYear) {
            return new World(year, place, lat, lon, attacker, defender, disputedPlace, warProbability,
                    trendStrength, rulerName, heirs, law, name, arrivalYear, faithName, faithShare);
        }

        public World withFaith(String name, double share) {
            return new World(year, place, lat, lon, attacker, defender, disputedPlace, warProbability,
                    trendStrength, rulerName, heirs, law, invention, inventionArrivalYear, name, share);
        }
    }

    /** Four succession laws, the same four the sovereign engine implements. */
    public enum SuccessionLaw {
        PRIMOGENITURE("the eldest child inherits"),
        ELECTIVE_SENATE("the strongest claim is elected"),
        SENIORITY("the eldest of the family inherits"),
        USURPATION_COUP("whoever can take it, takes it");

        private final String words;

        SuccessionLaw(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** An heir, with the three things a law chooses between: age order, claim and legitimacy. */
    public record Heir(String name, double claim, int legitimacy, int bornYear) {
        public Heir {
            name = name == null ? "" : name;
            claim = clamp01(claim);
        }
    }

    /** One consequence, with how sure it is and how far it travelled. */
    public record Effect(Field field, String subject, String before, String after, boolean absorbed,
                         double confidence, double km, int years, String why) {

        public String line() {
            if (field.refused()) {
                return field.words() + " at " + subject + ": refused. " + why;
            }
            if (absorbed) {
                return field.words() + " at " + subject + ": " + why;
            }
            return field.words() + " at " + subject + ": " + before + " becomes " + after + " ("
                    + Math.round(confidence * 100) + "% sure). " + why;
        }
    }

    /** The whole answer. */
    public record Result(List<Effect> effects, List<Field> refused, String summary) {

        public boolean changed() {
            int n = 0;
            for (Effect e : effects) {
                if (e.field().refused()) continue;   // a refusal is not a consequence
                if (!e.absorbed()) n++;
            }
            return n > 0;
        }

        public Effect first(Field f) {
            for (Effect e : effects) if (e.field() == f) return e;
            return null;
        }
    }

    private WhatIf() {
    }

    static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ------------------------------------------------------------------ the engine

    /**
     * Apply a change and say what follows. The act is judged by {@link #strengthOf} for how hard it pushes,
     * and every computed field is then recomputed from the world the caller supplied.
     */
    public static Result apply(@Nullable ChangeRecord.Act act, World world) {
        List<Effect> effects = new ArrayList<>();
        List<Field> refused = List.of(Field.POPULATION, Field.MONEY);
        if (act == null || world == null) {
            return new Result(List.of(), refused, "Nothing was changed, so nothing follows.");
        }

        double push = strengthOf(act);
        boolean absorbed = Math.abs(push) < world.trendStrength();

        // ---- WAR: the given probability, moved. This is not a battle model, it is arithmetic on one.
        if (!world.attacker().isEmpty() && !world.defender().isEmpty()) {
            double before = world.warProbability();
            double after = clamp01(before + push);
            double confident = confidence(world.trendStrength(), Math.abs(push), 0.0, 0);
            effects.add(new Effect(Field.WAR, world.disputedPlace().isEmpty() ? world.place()
                    : world.disputedPlace(), percent(before), percent(after), absorbed, confident, 0.0, 0,
                    absorbed
                            ? "the change is smaller than what the war was already pushing toward, so it "
                                    + "is swallowed: " + act.words()
                            : "the chance the attacker wins moves by the strength of the change"));

            // ---- BORDER: only a crossing of the halfway mark changes hands. Near it, nothing does.
            boolean wasAttacker = before > 0.5;
            boolean isAttacker = after > 0.5;
            String holder = isAttacker ? world.attacker() : world.defender();
            String other = isAttacker ? world.defender() : world.attacker();
            effects.add(new Effect(Field.BORDER, world.disputedPlace().isEmpty() ? world.place()
                    : world.disputedPlace(),
                    wasAttacker ? world.attacker() : world.defender(), holder, absorbed, confident, 0.0, 0,
                    wasAttacker == isAttacker
                            ? "the border does not move: " + holder + " held it before and holds it after"
                            : "the border moves: " + other + " loses " + world.disputedPlace() + " to "
                                    + holder));
        }

        // ---- HEIR: a ruler removed or a claim changed, decided by the law.
        // No heirs is not "nothing to say": it is the open succession, which is the answer a ruler's death
        // with nobody behind them actually produces. The first run of the part 4 probe found this missing.
        if (world.rulerName() != null && act.what() == ChangeRecord.What.PERSON) {
            Heir next = successor(world.heirs(), world.law());
            double confident = confidence(world.trendStrength(), Math.abs(push), 0.0, 0);
            effects.add(new Effect(Field.HEIR, world.place(), world.rulerName(),
                    next == null ? "nobody the record admits" : next.name(), absorbed && next != null,
                    confident, 0.0, 0,
                    next == null
                            ? "no heir fits " + world.law().words() + ", so the succession is open, which "
                                    + "is the most dangerous answer there is"
                            : world.law().words() + ", and " + next.name() + " fits it best"));
        }

        // ---- TECHNOLOGY: the arrival year the atlas gives, moved, then spread by distance.
        if (world.invention() != null && world.inventionArrivalYear() != Integer.MIN_VALUE) {
            int shift = (int) Math.round(push * 40.0);   // a full push is about forty years
            int arrival = world.inventionArrivalYear() - shift;
            int spread = yearsToSpread(Math.abs(shift));
            double confident = confidence(world.trendStrength(), Math.abs(push), 0.0, 0);
            effects.add(new Effect(Field.TECHNOLOGY, world.invention(),
                    "arrives " + world.inventionArrivalYear(), "arrives " + arrival,
                    absorbed || shift == 0, confident, 0.0, 0,
                    absorbed
                            ? "the change is smaller than the trend it pushes against, so the date does not "
                                    + "move: " + world.invention() + " still arrives when the atlas says"
                            : "the atlas dates it, and a change of this size moves that date by "
                                    + Math.abs(shift) + " years; the change reaches places beyond "
                                    + world.place() + " about " + spread + " years later"));
        }

        // ---- FAITH: a share moved, then spread by distance.
        if (world.faithName() != null && world.faithShare() > 0) {
            double after = clamp01(world.faithShare() + push * 0.25);
            double confident = confidence(world.trendStrength(), Math.abs(push), 0.0, 0);
            effects.add(new Effect(Field.FAITH, world.faithName(), percent(world.faithShare()),
                    percent(after), absorbed, confident, 0.0, 0,
                    "a share of the people, moved by the change and not by a claim about what they "
                            + "believe"));
        }

        // ---- THE TWO REFUSALS, in the answer and not only in a comment.
        for (Field f : refused) {
            effects.add(new Effect(f, world.place(), "not computed", "not computed", false, 0.0, 0.0, 0,
                    f == Field.POPULATION
                            ? "a population figure past the last measured year is the refusal this project "
                                    + "has held since the guardrail was written. A change can be said to "
                                    + "make a place grow or shrink and never to how many."
                            : "a price is refused for the same reason the price tables stop where they "
                                    + "stop. A what-if can say trade is disrupted and cannot say what that "
                                    + "costs."));
        }

        String summary = summarise(act, world, effects);
        return new Result(effects, refused, summary);
    }

    /** How hard a change pushes, in the units the fields use: -1 to 1. */
    public static double strengthOf(@Nullable ChangeRecord.Act act) {
        if (act == null) return 0.0;
        double size = Math.min(1.0, Math.abs(act.magnitude()) / 10.0);
        return switch (act.how()) {
            case ADD, BIGGER -> 0.1 + 0.4 * size;
            case REMOVE, SMALLER -> -(0.1 + 0.4 * size);
            case EARLIER -> 0.1 + 0.4 * size;
            case LATER -> -(0.1 + 0.4 * size);
        };
    }

    /** The most sure this engine will ever be. Nothing that has not happened is certain. */
    public static final double MOST_SURE = 0.9;

    /**
     * How sure a consequence is: less sure against a big trend, and less sure when smaller. Capped at
     * {@link #MOST_SURE}, because the first version of this gauge printed <b>100% sure</b> about the outcome
     * of a war, which is the kind of certainty this project exists to avoid.
     */
    public static double confidence(double trend, double push, double km, int years) {
        double base = 0.8 * clamp01(push * 2.0) + 0.2;
        double faded = base * Math.max(0.1, 1.0 - trend);
        return Math.min(MOST_SURE, clamp01(faded));
    }

    /** How many years a change takes to reach places beyond it. Distance in time, for a screen. */
    public static int yearsToSpread(int shiftYears) {
        return Math.max(1, Math.abs(shiftYears) / 2);
    }

    /** The successor the law picks, from the heirs the caller has. Null when nobody fits. */
    @Nullable
    public static Heir successor(List<Heir> heirs, SuccessionLaw law) {
        if (heirs == null || heirs.isEmpty()) return null;
        Heir best = null;
        for (Heir h : heirs) {
            if (h.name().isBlank()) continue;
            if (best == null) {
                best = h;
                continue;
            }
            if (better(h, best, law)) best = h;
        }
        return best;
    }

    private static boolean better(Heir a, Heir b, SuccessionLaw law) {
        return switch (law) {
            // The eldest: the earliest birth year.
            case PRIMOGENITURE, SENIORITY -> a.bornYear() < b.bornYear();
            // The strongest claim, legitimacy breaking a tie.
            case ELECTIVE_SENATE -> a.claim() > b.claim()
                    || (a.claim() == b.claim() && a.legitimacy() > b.legitimacy());
            // Whoever can take it: the best claim, and legitimacy counts for less than force.
            case USURPATION_COUP -> a.claim() * 0.5 + a.legitimacy() / 100.0
                    > b.claim() * 0.5 + b.legitimacy() / 100.0;
        };
    }

    /** A share as a percentage with a whole number, because a what-if must not pretend to precision. */
    static String percent(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String summarise(ChangeRecord.Act act, World world, List<Effect> effects) {
        StringBuilder b = new StringBuilder();
        b.append("You ").append(act.words()).append(". ");
        int computed = 0;
        for (Effect e : effects) {
            if (e.field().refused()) continue;
            if (e.absorbed()) continue;
            computed++;
        }
        if (computed == 0) {
            b.append("Nothing follows that this world can compute: the change is smaller than what was "
                    + "already happening, or the place has nothing in it to change.");
        } else {
            b.append(computed).append(" thing").append(computed == 1 ? "" : "s").append(" follow. ");
        }
        b.append("Two things are refused and said so: how many people there are, and what anything costs.");
        return b.toString();
    }

    /** One line, for a screen, that says what the engine will and will not answer. */
    public static String ruleLine() {
        return "It will tell you what follows for the war, the border, the succession, an invention and a "
                + "faith, from the registers this world already keeps. It will not tell you how many people "
                + "there are or what anything costs, because those are the two things this project refuses "
                + "to invent.";
    }

    /** Every field and whether it is computed, for a screen or a probe to walk. */
    public static List<String> fields() {
        List<String> out = new ArrayList<>();
        for (Field f : Field.values()) {
            out.add(String.format(Locale.ROOT, "%-11s %s", f.name(),
                    f.refused() ? "refused" : "computed"));
        }
        return out;
    }
}

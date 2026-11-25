package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * <b>D12: rulers decide.</b> A ruler weighs a tax, a war and a claim, and the three things that decide it
 * are the same three every time.
 *
 * <h2>Why a separate class, when a succession engine already exists</h2>
 *
 * <p>{@code sovereign/DynasticSuccessionEngine} already resolves a succession: four laws, heirs, and a
 * usurpation roll. It is old working code and this class does not replace it. What it does is answer a
 * different question, <b>why a ruler chooses what they choose</b>, so that a decision can be explained to a
 * player rather than rolled. Two properties of the existing engine are the reason:
 *
 * <ol>
 *   <li>Its {@code USURPATION_COUP} and {@code SENIORITY} laws both fall through to <b>{@code heirs.get(0)}</b>,
 *       which is whichever heir was registered first. That is not a law, it is a list order, and it only
 *       shows on the two laws it affects.</li>
 *   <li>{@code attemptUsurpation} rolls <b>{@code Math.random()}</b>, 75% with an army and 35% without, so the
 *       same ruler in the same year can succeed or fail on a reload, and no answer can be given for why.</li>
 * </ol>
 *
 * <p>Both are <b>recorded as defects and left alone</b>: they are old, tested code and changing them is a
 * different step's work. What this class offers instead is a rule with a reason attached, and
 * {@link #defects()} states the two problems out loud so nobody has to discover them again.
 *
 * <h2>The model, which is small on purpose</h2>
 *
 * <p>A ruler has three things: how <b>secure</b> they are on the throne, how much <b>treasure</b> there is,
 * and how <b>strong</b> the army is. Every decision is made against those three, and every decision carries
 * the sentence that explains it. No roll, no random: the same ruler in the same year always decides the same
 * way, which is what makes a decision arguable.
 */
public final class Rulers {

    /** What a ruler may be asked to decide. Three, because three are what the record supports. */
    public enum Decision {
        RAISE_TAX("raise the tax"),
        GO_TO_WAR("go to war"),
        NAME_HEIR("name an heir");

        private final String words;

        Decision(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** A ruler, as the world can describe one: a name, and the three numbers a decision turns on. */
    public record Ruler(String name, double security, long treasure, double armyStrength) {
        public Ruler {
            name = name == null || name.isBlank() ? "a ruler nobody named" : name;
            security = Math.max(0.0, Math.min(1.0, security));
            armyStrength = Math.max(0.0, Math.min(1.0, armyStrength));
            treasure = Math.max(0L, treasure);
        }

        /** A ruler with nothing to go on. The floor, and the commonest case in the record. */
        public static Ruler unknown() {
            return new Ruler("a ruler nobody named", 0.5, 0L, 0.3);
        }
    }

    /** What a ruler is being asked about: the war in front of them, the tax they could set, the heirs. */
    public record Question(Decision decision, int year, String place, double warChance,
                           @Nullable String enemy, double taxNow, double taxWanted) {
        public Question {
            place = place == null ? "here" : place;
            warChance = Math.max(0.0, Math.min(1.0, warChance));
            taxNow = Math.max(0.0, taxNow);
            taxWanted = Math.max(0.0, taxWanted);
        }

        public static Question tax(int year, String place, double now, double wanted) {
            return new Question(Decision.RAISE_TAX, year, place, 0.5, null, now, wanted);
        }

        public static Question war(int year, String place, String enemy, double chance) {
            return new Question(Decision.GO_TO_WAR, year, place, chance, enemy, 0.15, 0.15);
        }

        public static Question heir(int year, String place) {
            return new Question(Decision.NAME_HEIR, year, place, 0.5, null, 0.15, 0.15);
        }
    }

    /** A decision, with the reason a player can argue with. */
    public record Choice(Decision decision, boolean yes, String words, String why, double confidence) {
        public String line() {
            return words + " " + (yes ? "yes" : "no") + ". " + why;
        }
    }

    /** How much security a ruler will risk before a decision becomes impossible. */
    public static final double SECURITY_FLOOR = 0.30;
    /** How many years of treasure a war is worth without a clear advantage. */
    public static final long TREASURE_FOR_WAR = 100L;
    /** How far a tax can be pushed before the treasure it raises costs more than it brings. */
    public static final double TAX_CEILING = 0.35;

    private Rulers() {
    }

    // ------------------------------------------------------------------ the three decisions

    /**
     * Decide, and say why. The order of the reasons is the order of what a ruler actually worries about:
     * staying on the throne, then paying for it, then whether they can win.
     */
    public static Choice decide(Ruler ruler, Question q) {
        if (ruler == null || q == null) return new Choice(Decision.RAISE_TAX, false, "nothing to decide",
                "there is no ruler and no question.", 0.0);
        return switch (q.decision()) {
            case RAISE_TAX -> tax(ruler, q);
            case GO_TO_WAR -> war(ruler, q);
            case NAME_HEIR -> heir(ruler, q);
        };
    }

    private static Choice tax(Ruler ruler, Question q) {
        String words = "Raise the tax at " + q.place() + " from " + pct(q.taxNow()) + " to "
                + pct(q.taxWanted());
        // A ruler who is not secure cannot afford to be hated.
        if (ruler.security() < SECURITY_FLOOR) {
            return new Choice(Decision.RAISE_TAX, false, words,
                    ruler.name() + " is not safe on the throne: at " + pct(ruler.security())
                            + " security, a new tax is the thing that turns a grumble into a rising. A "
                            + "ruler in that position lowers the tax or does nothing.",
                    0.75);
        }
        if (q.taxWanted() > TAX_CEILING) {
            return new Choice(Decision.RAISE_TAX, false, words,
                    "the wanted rate is above " + pct(TAX_CEILING) + ", and above that a tax collects less "
                            + "than it costs: people stop trading in the open and the treasure falls while "
                            + "the number on the order rises.", 0.7);
        }
        if (ruler.treasure() > TREASURE_FOR_WAR * 3) {
            return new Choice(Decision.RAISE_TAX, false, words,
                    "the treasury holds " + ruler.treasure() + ", which is enough. A ruler with money does "
                            + "not raise a tax to get money; they raise one when the money is going out "
                            + "faster than it comes in.", 0.65);
        }
        return new Choice(Decision.RAISE_TAX, true, words,
                "the treasury holds " + ruler.treasure() + ", which is less than three years of what a war "
                        + "costs, and " + ruler.name() + " is secure enough at " + pct(ruler.security())
                        + " to survive being disliked.", 0.7);
    }

    private static Choice war(Ruler ruler, Question q) {
        String against = q.enemy() == null || q.enemy().isBlank() ? "the enemy" : q.enemy();
        String words = "Go to war against " + against + " in " + q.year();
        if (ruler.security() < SECURITY_FLOOR) {
            return new Choice(Decision.GO_TO_WAR, false, words,
                    "a ruler at " + pct(ruler.security()) + " security who leaves the country comes back "
                            + "to a different one. Wars are declared by secure rulers, not by worried ones.",
                    0.8);
        }
        if (ruler.treasure() < TREASURE_FOR_WAR) {
            return new Choice(Decision.GO_TO_WAR, false, words,
                    "the treasury holds " + ruler.treasure() + " against the " + TREASURE_FOR_WAR
                            + " a war needs before the first battle is fought. An army that is not paid "
                            + "is an army that is somewhere else.", 0.75);
        }
        // A war is worth it when the chance of winning is clearly better than even, or when the army is
        // much stronger than the ruler's own position suggests.
        if (q.warChance() > 0.55) {
            return new Choice(Decision.GO_TO_WAR, true, words,
                    "the army's chance of winning is " + pct(q.warChance()) + ", and a secure ruler with "
                            + ruler.treasure() + " in hand takes a war they expect to win.", 0.7);
        }
        if (ruler.armyStrength() > 0.7 && q.warChance() > 0.45) {
            return new Choice(Decision.GO_TO_WAR, true, words,
                    "the chance is only " + pct(q.warChance()) + " but the army is strong ("
                            + pct(ruler.armyStrength()) + "), and a strong army makes its own odds as the "
                            + "war goes on.", 0.55);
        }
        return new Choice(Decision.GO_TO_WAR, false, words,
                "the chance of winning is " + pct(q.warChance()) + " and the army is " + pct(ruler.armyStrength())
                        + ". A ruler who takes that war is buying a coin toss with other people's lives.", 0.7);
    }

    private static Choice heir(Ruler ruler, Question q) {
        String words = "Name an heir at " + q.place();
        if (ruler.security() < SECURITY_FLOOR) {
            return new Choice(Decision.NAME_HEIR, true, words,
                    "naming an heir is the one thing a worried ruler can do that steadies the throne: it "
                            + "tells everybody who the alternative is, which is usually the whole argument.",
                    0.7);
        }
        if (ruler.security() > 0.8) {
            return new Choice(Decision.NAME_HEIR, false, words,
                    "a ruler at " + pct(ruler.security()) + " does not need to name anybody yet, and naming "
                            + "one early makes a focus for everybody who wants the throne sooner.", 0.6);
        }
        return new Choice(Decision.NAME_HEIR, true, words,
                "at " + pct(ruler.security()) + " the succession is worth settling while it is still the "
                        + "ruler's choice rather than somebody else's.", 0.65);
    }

    /** A ruler's decision about a change a player made: do they accept it, resist it, or ignore it? */
    public static Choice reactTo(Ruler ruler, ChangeRecord.Act act, double actStrength) {
        if (act == null) return new Choice(Decision.GO_TO_WAR, false, "nothing happened",
                "there is no act to react to.", 0.0);
        boolean threatens = actStrength > 0.5 && act.what() == ChangeRecord.What.PERSON;
        String words = "React to " + act.words();
        if (threatens && ruler.security() > SECURITY_FLOOR) {
            return new Choice(Decision.GO_TO_WAR, true, words,
                    "a change this large to a person is the kind of thing a secure ruler answers, because "
                            + "not answering invites the next one.", 0.6);
        }
        if (threatens) {
            return new Choice(Decision.GO_TO_WAR, false, words,
                    "a change this large is real, and " + ruler.name() + " at " + pct(ruler.security())
                            + " cannot afford to fight it as well as everything else.", 0.55);
        }
        return new Choice(Decision.GO_TO_WAR, false, words,
                "the change does not touch the throne, so the ruler does nothing, which is what most "
                        + "rulers do about most things.", 0.65);
    }

    /** Every decision a ruler can be asked for, one line each, from this ruler's own numbers. */
    public static List<String> card(Ruler ruler, int year, String place, double warChance, String enemy) {
        List<String> out = new ArrayList<>();
        out.add(ruler.name() + " at " + place + " in " + year + ": security " + pct(ruler.security())
                + ", treasure " + ruler.treasure() + ", army " + pct(ruler.armyStrength()) + ".");
        out.add(decide(ruler, Question.tax(year, place, 0.15, 0.25)).line());
        out.add(decide(ruler, Question.war(year, place, enemy, warChance)).line());
        out.add(decide(ruler, Question.heir(year, place)).line());
        return out;
    }

    /**
     * The two defects in the old engine, stated where a reader will find them. This class does not fix them:
     * it answers a different question and says what it found.
     */
    public static List<String> defects() {
        return List.of(
                "DynasticSuccessionEngine: USURPATION_COUP and SENIORITY both fall through to heirs.get(0), "
                        + "which is whichever heir was registered first. That is a list order and not a law, "
                        + "and it shows on two of the four laws.",
                "DynasticSuccessionEngine.attemptUsurpation rolls Math.random() at 75% with an army and 35% "
                        + "without, so the same usurpation in the same year can succeed or fail on a reload "
                        + "and there is no answer to why. A decision that cannot be explained cannot be "
                        + "argued with, which is why this class decides without a roll.");
    }

    /** One line, for a screen, that says what a ruler is and is not. */
    public static String ruleLine() {
        return "A ruler decides from three things: how safe they are on the throne, how much treasure they "
                + "hold, and how strong the army is. The same ruler in the same year always decides the same "
                + "way, so the reason is the answer.";
    }

    static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    /** A line about a place and a year, for a log. Kept short on purpose. */
    public static String summary(Ruler ruler, int year, String place) {
        return String.format(Locale.ROOT, "%s rules %s in %d, secure %s, army %s", ruler.name(), place,
                year, pct(ruler.security()), pct(ruler.armyStrength()));
    }
}

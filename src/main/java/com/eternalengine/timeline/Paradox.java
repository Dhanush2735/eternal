package com.eternalengine.timeline;

import com.eternalengine.player.WhoYouAre;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>The five paradox kinds, as detectors.</b> H1.
 *
 * <h2>Why this class exists</h2>
 *
 * H1 names five mistakes, and today the mod can only count one of them (Terra++'s broken dated buildings).
 * Worse, nobody agrees on what a paradox IS here: step 51 made ordinary history-changing a clean branch act,
 * and {@code clock/TimeTravel} moves the whole world without duplicating anybody, so the ordinary acts of play
 * make no paradox at all. These five are the exceptions, and each is a <b>specific act</b> with a
 * <b>specific test</b>:
 *
 * <ol>
 *   <li><b>Grandfather</b>: undo the cause of your own arrival (your ancestor before the next of your line is
 *       born; your traveller's named cause), <b>or</b> destroy a dated fixed point (G9's force).</li>
 *   <li><b>Causal loop</b>: give people a thing or idea that has no origin - one given before its own first
 *       year in the record, or one the record dates nowhere at all.</li>
 *   <li><b>Predestination</b>: your attempt to stop an event is what caused it.</li>
 *   <li><b>Meeting yourself</b>: one person stamped twice at one place and year.</li>
 *   <li><b>Telling the future</b>: telling people of a thing before the year the record knows it from.</li>
 * </ol>
 *
 * <h2>How the tests work</h2>
 *
 * <p>Every test answers with a {@link Check}: either the paradox found (its kind, whose, where, when, why and
 * what it costs {@link Stress}) or <b>the reason there is none</b>. A test that returns silence would leave a
 * caller guessing; a test that returns "Daulat died in 1840 and this is 1857: there is nothing to kill" cannot
 * be mistaken for a broken detector. The grandfather test reads {@link WhoYouAre}'s line - the card step 52
 * made and froze - so it cannot be argued with afterwards.
 *
 * <h2>Weights</h2>
 *
 * <p>Each kind carries a default weight for the counter. They are <b>game defaults</b>, chosen so that undoing
 * your own existence counts for more than gossip about the future, and not one of them is a fact of history.
 * A destroyed fixed point carries the weight its point declared, not the kind's default.
 */
public final class Paradox {

    /** <b>The five kinds H1 names.</b> Keep their names; a grandfather is not a loop. */
    public enum Kind {
        GRANDFATHER("grandfather", 5),
        CAUSAL_LOOP("causal loop", 2),
        PREDESTINATION("predestination", 2),
        MEETING_SELF("meeting yourself", 3),
        TELLING_FUTURE("telling the future", 1);

        private final String words;
        private final int weight;

        Kind(String words, int weight) {
            this.words = words;
            this.weight = weight;
        }

        public String words() {
            return words;
        }

        /** The default weight for the counter: a game default, not history. */
        public int weight() {
            return weight;
        }
    }

    /** <b>One paradox found.</b> */
    public record Found(Kind kind, String who, int year, String what, String why, int weight) {

        public String line() {
            return kind.words() + " · " + who + " · " + year + " · " + what + " · " + weight + " · " + why;
        }
    }

    /**
     * <b>The outcome of one test.</b> Either a paradox, or the words that clear it. Never both, never
     * neither.
     */
    public record Check(Found found, String clear) {
        public boolean paradox() {
            return found != null;
        }

        public String line() {
            return found != null ? found.line() : "no paradox: " + clear;
        }
    }

    private Paradox() {
    }

    // ------------------------------------------------------------------ 1. grandfather

    /**
     * <b>Kill an ancestor before the next of your line is born.</b> The test reads the frozen card: is this
     * name yours, were they alive then, and is this year before the birth of the one after them. Killing
     * somebody else's grandfather, or yours after the next of the line is already born, is a murder and not a
     * paradox, and the answer says so.
     */
    public static Check undoAncestor(WhoYouAre who, String ancestor, int year) {
        if (who == null) {
            return new Check(null, "there is nobody whose line this could be");
        }
        if (ancestor == null || ancestor.trim().isEmpty()) {
            return new Check(null, "no name was given, so there is nobody to check");
        }
        if (!who.isAncestor(ancestor)) {
            return new Check(null, ancestor + " is not in " + who.name() + "'s line (" + who.lineOrigin()
                    + "); ending them undoes nothing of theirs");
        }
        WhoYouAre.Entry e = null;
        for (WhoYouAre.Entry x : who.line()) {
            if (x.name().equalsIgnoreCase(ancestor.trim())) {
                e = x;
            }
        }
        if (year < e.born()) {
            return new Check(null, e.name() + " was not born until " + e.born() + ", and this is " + year
                    + ": there is nobody to end");
        }
        if (e.died() != null && year > e.died()) {
            return new Check(null, e.name() + " died in " + e.died() + " and this is " + year
                    + ": there is nobody to end");
        }
        // The next of the chain toward you is the nearest relation above you and below the ancestor: the
        // greatest depth still under theirs. (The first version took the earliest-born of every nearer
        // relation, which is not the chain and would have cleared or blamed the wrong year.)
        int nextBorn = who.born();
        String nextName = who.name();
        int best = 0;
        for (WhoYouAre.Entry x : who.line()) {
            if (x.depth() < e.depth() && x.depth() > best) {
                best = x.depth();
                nextBorn = x.born();
                nextName = x.name();
            }
        }
        if (year < nextBorn) {
            return new Check(new Found(Kind.GRANDFATHER, who.name(), year, e.name(),
                    "ending " + e.name() + " in " + year + " is before " + nextName + " was born (" + nextBorn
                            + "), and " + nextName + " is why " + who.name() + " is here: the cause of their "
                            + "own arrival", Kind.GRANDFATHER.weight()), "");
        }
        return new Check(null, nextName + " was already born in " + nextBorn + ", so ending " + e.name()
                + " in " + year + " undoes nothing of " + who.name() + "'s arrival: it is a death and not a "
                + "paradox");
    }

    /**
     * <b>Undo a traveller's named cause.</b> A traveller's line is carried, not recorded (step 52), so the
     * cause of their arrival is the thing that brought them: end it and they never came. The engine names the
     * cause; this class keeps the rule that undoing it is grandfather kind.
     */
    public static Check undoArrival(WhoYouAre who, String cause, int year) {
        if (who == null) {
            return new Check(null, "there is nobody who could have arrived");
        }
        if (cause == null || cause.trim().isEmpty()) {
            return new Check(null, "no cause of arrival was named, so there is nothing here to undo");
        }
        return new Check(new Found(Kind.GRANDFATHER, who.name(), year, cause.trim(),
                "undoing " + cause.trim() + " undoes the cause of " + who.name() + "'s own arrival",
                Kind.GRANDFATHER.weight()), "");
    }

    /**
     * <b>Destroy a dated fixed point.</b> H1's second grandfather trigger, straight from G9's force: the
     * weight is the point's own, not the kind's default.
     */
    public static Check destroyedFixedPoint(FixedPoints.Forced forced) {
        if (forced == null || !forced.ok()) {
            return new Check(null, "nothing was forced, so nothing was destroyed");
        }
        return new Check(new Found(Kind.GRANDFATHER, forced.by(), forced.point().year(), forced.point().what(),
                forced.by() + " forced " + forced.point().id() + " (" + forced.point().year()
                        + "), and a dated fixed point destroyed is grandfather kind: " + forced.why(),
                forced.point().weight()), "");
    }

    // ------------------------------------------------------------------ 2. causal loop

    /**
     * <b>Give people a thing or idea that has no origin.</b> Two ways: one the record dates to a later year
     * than the giving, and one the record does not date at all. The origin years come from the technology
     * atlas's own {@code first} field (130 techs, each with one).
     */
    public static Check givenBeforeOrigin(String who, String what, int givenYear, Integer originYear) {
        if (what == null || what.trim().isEmpty()) {
            return new Check(null, "no thing was given, so no loop was opened");
        }
        if (originYear == null) {
            return new Check(new Found(Kind.CAUSAL_LOOP, who == null ? "nobody" : who, givenYear,
                    what.trim(), what.trim() + " has no origin year anywhere in the record, and giving it in "
                            + givenYear + " starts a loop: it came from nowhere",
                    Kind.CAUSAL_LOOP.weight()), "");
        }
        if (givenYear < originYear) {
            return new Check(new Found(Kind.CAUSAL_LOOP, who == null ? "nobody" : who, givenYear,
                    what.trim(), what.trim() + " was given in " + givenYear + ", before its own origin in "
                            + originYear + ": it came from nowhere",
                    Kind.CAUSAL_LOOP.weight()), "");
        }
        return new Check(null, what.trim() + " is known from " + originYear + ", and " + givenYear
                + " is not before that");
    }

    // ------------------------------------------------------------------ 3. predestination

    /**
     * <b>Your attempt to stop an event is what caused it.</b> The engine observes the fact (the action
     * detectors already watch what players do); this class keeps the rule and the record. An attempt named
     * nothing, or an event named nothing, cannot be a paradox.
     */
    public static Check attemptCaused(String who, String attempt, String event, int year) {
        if (attempt == null || attempt.trim().isEmpty() || event == null || event.trim().isEmpty()) {
            return new Check(null, "the attempt or the event is unnamed, so nothing can be shown to have "
                    + "caused anything");
        }
        return new Check(new Found(Kind.PREDESTINATION, who == null ? "nobody" : who, year, event.trim(),
                "the attempt (" + attempt.trim() + ") to stop " + event.trim() + " is what caused it",
                Kind.PREDESTINATION.weight()), "");
    }

    // ------------------------------------------------------------------ 4. meeting yourself

    /**
     * <b>One person stamped twice at one place and year.</b> Two stamps of one name at one place-year means
     * there are two of them to meet. One stamp is the ordinary fact of standing somewhere.
     */
    public static Check metSelf(String who, String place, int year, int stampsHere) {
        if (stampsHere < 2) {
            return new Check(null, "one copy of " + (who == null ? "nobody" : who) + " is stamped in "
                    + place + " in " + year + ": nobody meets themselves");
        }
        return new Check(new Found(Kind.MEETING_SELF, who == null ? "nobody" : who, year, place,
                who + " is stamped " + stampsHere + " times in " + place + " in " + year
                        + ": there are two of them to meet",
                Kind.MEETING_SELF.weight()), "");
    }

    // ------------------------------------------------------------------ 5. telling the future

    /**
     * <b>Tell people of a thing before the record knows it.</b> The book already guards this for itself (the
     * "what comes later" view is a time traveller's setting); this test guards speech.
     */
    public static Check toldFuture(String who, String what, int knownFrom, int toldYear) {
        if (what == null || what.trim().isEmpty()) {
            return new Check(null, "nothing was told");
        }
        if (toldYear < knownFrom) {
            return new Check(new Found(Kind.TELLING_FUTURE, who == null ? "nobody" : who, toldYear,
                    what.trim(), what.trim() + " was told in " + toldYear + ", and the record does not know "
                            + "it until " + knownFrom,
                    Kind.TELLING_FUTURE.weight()), "");
        }
        return new Check(null, what.trim() + " is known from " + knownFrom + ", and " + toldYear
                + " is not before that");
    }

    // ------------------------------------------------------------------ the page

    /** Sort a pile of checks into one page: every paradox by kind, then the clears in one line each. */
    public static List<String> page(List<Check> checks) {
        List<String> out = new ArrayList<>();
        List<Found> found = new ArrayList<>();
        List<String> clear = new ArrayList<>();
        int total = 0;
        for (Check c : checks == null ? List.<Check>of() : checks) {
            if (c == null) {
                continue;
            }
            if (c.paradox()) {
                found.add(c.found());
                total += c.found().weight();
            } else {
                clear.add(c.clear());
            }
        }
        out.add(found.size() + " paradoxes · " + clear.size() + " cleared · " + total + " stress");
        for (Kind k : Kind.values()) {
            for (Found f : found) {
                if (f.kind() == k) {
                    out.add("  " + f.line());
                }
            }
        }
        for (String c : clear) {
            out.add("  cleared: " + c);
        }
        return out;
    }

    public static String ruleLine() {
        return "Ordinary play makes no paradox: a branch is clean and the world's date moves everybody without "
                + "duplicating anybody. The five kinds are five specific acts, each with a test, and every "
                + "test answers with the paradox or with the words that clear it.";
    }
}

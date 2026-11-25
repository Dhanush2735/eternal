package com.eternalengine.timeline;

import com.eternalengine.player.Permissions;
import com.eternalengine.player.Permissions.Action;
import com.eternalengine.player.Permissions.Actor;
import com.eternalengine.player.Permissions.Decision;
import com.eternalengine.player.WhoYouAre;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>The one surface game code would call.</b> H1 + H2 + G9 together.
 *
 * <h2>Why this class exists</h2>
 *
 * Step 53's parts 2 to 4 built the three pieces: {@link FixedPoints} (what resists), {@link Paradox} (the five
 * tests) and {@link Stress} (the one counter). A caller that had to know all three, and the order to ask them
 * in, would get it wrong in a new way every time. This class is the order, once.
 *
 * <h2>What each call returns</h2>
 *
 * <p>A {@link Verdict}: the decision (did the act stand?), every paradox found, and every row booked on the
 * counter. The division of policy is deliberate and narrow:
 *
 * <ul>
 *   <li><b>Forcing a fixed point</b> is the one act this class judges: it rewrites what is recorded, so
 *       {@link Permissions} says whether this hand may (an operator's), and the answer is refused or done.</li>
 *   <li><b>Every other act stands</b> if it happened: ending a life, handing over a thing, telling a tale.
 *       These are ordinary acts of play; the class finds what is paradoxical in them and books it. The world's
 *       resistance lives in {@link FixedPoints}, not here.</li>
 *   <li><b>Nothing here decides a consequence.</b> The counter rises and falls; what the number does is
 *       policy, and the policy is off (decision 7).</li>
 * </ul>
 */
public final class ParadoxEngine {

    /**
     * <b>What came of asking.</b> The decision, every paradox found, every row booked. A verdict with no
     * paradoxes in it is the ordinary case, and says so.
     */
    public record Verdict(Decision decision, List<Paradox.Found> found, List<Stress.Changed> booked) {

        public boolean ok() {
            return decision.allowed();
        }

        public String line() {
            StringBuilder b = new StringBuilder(decision.why());
            for (Paradox.Found f : found) {
                b.append(" | ").append(f.line());
            }
            return b.toString();
        }
    }

    private ParadoxEngine() {
    }

    /**
     * <b>Try to change a fixed point.</b> The safe answer first: take it to a branch. An operator may force
     * it, and forcing it is grandfather kind on the books.
     */
    public static Verdict attemptFixed(FixedPoints pts, Stress stress, String id, String how) {
        Decision refused = pts.attempt(id, how);
        return new Verdict(refused, List.of(), List.of());
    }

    /**
     * <b>Force a fixed point.</b> The one act this class judges: an operator's hand or a refusal in words.
     * The paradox and its weight go on the counter either way they exist - and if the force is refused, no
     * paradox exists and nothing is booked.
     */
    public static Verdict forceFixed(Actor by, FixedPoints pts, Stress stress, String id, String why,
                                     int year) {
        Decision may = Permissions.mayAlone(by, Action.CHANGE_HISTORY, 1);
        if (!may.allowed()) {
            return new Verdict(Decision.no(may.why() + "; a forced fixed point rewrites what is recorded"),
                    List.of(), List.of());
        }
        FixedPoints.Forced f = pts.force(by == null ? "" : by.name(), id, why);
        if (!f.ok()) {
            return new Verdict(Decision.no(f.refused()), List.of(), List.of());
        }
        Paradox.Check c = Paradox.destroyedFixedPoint(f);
        List<Paradox.Found> found = new ArrayList<>();
        List<Stress.Changed> booked = new ArrayList<>();
        if (c.paradox()) {
            found.add(c.found());
            booked.add(stress.add(c.found()));
        }
        return new Verdict(Decision.yes(by.name() + " forced " + pts.find(id).id() + " in " + year
                + ": " + f.why()), found, booked);
    }

    /**
     * <b>End a life at a year.</b> The grandfather test reads the frozen card. If the test finds the line
     * undone, the act still stands - it happened - and the paradox is on the books in words.
     */
    public static Verdict endAncestor(WhoYouAre who, Stress stress, String by, String ancestor, int year) {
        Paradox.Check c = Paradox.undoAncestor(who, ancestor, year);
        return finish(c, by, stress, "ending " + ancestor + " in " + year);
    }

    /**
     * <b>Undo a traveller's named cause.</b> The second grandfather trigger, for a line that is carried and
     * not recorded.
     */
    public static Verdict undoArrival(WhoYouAre who, Stress stress, String by, String cause, int year) {
        Paradox.Check c = Paradox.undoArrival(who, cause, year);
        return finish(c, by, stress, "undoing " + (cause == null ? "?" : cause.trim()) + " in " + year);
    }

    /**
     * <b>Give people a thing or idea.</b> The causal-loop test, against the technology atlas's own origin
     * years (or their absence).
     */
    public static Verdict handOver(Stress stress, String by, String who, String what, int givenYear,
                                   Integer originYear) {
        Paradox.Check c = Paradox.givenBeforeOrigin(who, what, givenYear, originYear);
        return finish(c, by, stress, "giving " + (what == null ? "?" : what.trim()) + " in " + givenYear);
    }

    /** <b>An attempt that caused its own event.</b> The predestination test. */
    public static Verdict attemptCaused(Stress stress, String by, String who, String attempt, String event,
                                        int year) {
        Paradox.Check c = Paradox.attemptCaused(who, attempt, event, year);
        return finish(c, by, stress, "the attempt on " + (event == null ? "?" : event.trim()));
    }

    /** <b>One more stamp of one person at one place-year.</b> The meeting-yourself test. */
    public static Verdict stamp(Stress stress, String by, String who, String place, int year,
                                int stampsHere) {
        Paradox.Check c = Paradox.metSelf(who, place, year, stampsHere);
        return finish(c, by, stress, "stamping " + (who == null ? "?" : who) + " in " + place + " in " + year);
    }

    /** <b>Tell people of a thing.</b> The telling-the-future test. */
    public static Verdict tell(Stress stress, String by, String who, String what, int knownFrom,
                               int toldYear) {
        Paradox.Check c = Paradox.toldFuture(who, what, knownFrom, toldYear);
        return finish(c, by, stress, "telling " + (what == null ? "?" : what.trim()) + " in " + toldYear);
    }

    /**
     * <b>Rebuild what was forced.</b> H8's arithmetic in one call: the point stands again and its weight
     * comes back off the counter.
     */
    public static Verdict rebuild(Actor by, FixedPoints pts, Stress stress, String id, String why) {
        if (pts.find(id) == null) {
            return new Verdict(Decision.no("there is no fixed point called " + id + " to rebuild"),
                    List.of(), List.of());
        }
        int weight = pts.weightOf(id);
        Decision d = pts.restore(by == null ? "" : by.name(), id, why);
        if (!d.allowed()) {
            return new Verdict(d, List.of(), List.of());
        }
        Stress.Changed back = stress.repair(by == null ? "" : by.name(), pts.find(id).year(),
                pts.find(id).what(), weight, "rebuilt " + pts.find(id).id());
        List<Stress.Changed> booked = new ArrayList<>();
        booked.add(back);
        return new Verdict(d, List.of(), booked);
    }

    /** The page: every verdict's findings in one pile, as {@link Paradox} sorts them. */
    public static List<String> page(List<Verdict> verdicts) {
        List<Paradox.Check> pile = new ArrayList<>();
        for (Verdict v : verdicts == null ? List.<Verdict>of() : verdicts) {
            if (v == null) {
                continue;
            }
            for (Paradox.Found f : v.found()) {
                pile.add(new Paradox.Check(f, ""));
            }
        }
        return Paradox.page(pile);
    }

    public static String ruleLine() {
        return "One call per risky act: the safe answer first for a fixed point, an operator's hand to force "
                + "one, and every other act stands with its paradox on the books. The counter counts; nothing "
                + "here decides what the number means.";
    }

    // ------------------------------------------------------------------ internals

    private static Verdict finish(Paradox.Check c, String by, Stress stress, String act) {
        List<Paradox.Found> found = new ArrayList<>();
        List<Stress.Changed> booked = new ArrayList<>();
        if (!c.paradox()) {
            return new Verdict(Decision.yes(act + " stands, and clears: " + c.clear()), found, booked);
        }
        found.add(c.found());
        Stress.Changed row = stress.add(c.found());
        booked.add(row);
        return new Verdict(Decision.yes(act + " stands, and " + c.found().weight() + " of stress is on the "
                + "books: " + c.found().why()), found, booked);
    }
}

package com.eternalengine.chrononexus;

import com.eternalengine.timeline.Stress;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>The story campaign of Chrono-Nexus, retold on the new systems.</b> F6, and decision 7.
 *
 * <h2>Why this class exists</h2>
 *
 * The old campaign lives in {@code ChronoNexusCampaignEngine}: seven phases, rival incursions, moral
 * endings, and "Operation Ouroboros", a fifteen-minute countdown that erases players. Decision 7 says
 * fold the useful parts into time travel and paradoxes and <b>switch the fantasy off</b>. The useful
 * part is already built elsewhere: pressure that accumulates ({@link Stress}) and can be relieved
 * ({@code Repairs}). What this class adds is the <b>story</b> the pressure belongs to.
 *
 * <h2>The three refusals, in code</h2>
 *
 * <ul>
 *   <li><b>A player is never erased.</b> There is no method here that removes anybody, and the rule
 *       line says so in those words.</li>
 *   <li><b>No countdown deletes anybody.</b> The counter is a gauge ({@link #pressure}); it reads the
 *       stress and it never triggers anything.</li>
 *   <li><b>A failed chapter continues the story.</b> An attempt either keeps the chapter or continues
 *       it; there is no "game over" state anywhere in this class.</li>
 * </ul>
 *
 * <h2>The chapters</h2>
 *
 * <p>Six stations across the whole timeline (not one favourite place or year), each asking for one act
 * the mod already has: to stand in the record, to cross and come home (H7), to judge a paradox (H1),
 * to rebuild and watch the counter fall (H8), to rupture with a copy taken (step 54), and to close the
 * story in words. The chapters may be walked in any order: this is a story, not a rail.
 */
public final class Campaign {

    /** What a chapter asks the player to do. */
    public enum Kind {
        STORY("to stand where the record is, and see it"),
        TRAVEL("to cross between universes, and come home"),
        PARADOX("to judge what an attempt caused"),
        REPAIR("to rebuild what was broken, and watch the counter fall"),
        RUPTURE("to break open a universe, with its copy taken first");

        private final String ask;

        Kind(String ask) {
            this.ask = ask;
        }

        public String ask() {
            return ask;
        }
    }

    /** <b>One station of the story.</b> */
    public record Chapter(int index, String title, String place, int year, Kind kind, String ask) {

        public String line() {
            return index + ". " + title + " · " + place + ", " + year + " · " + kind.ask();
        }
    }

    /**
     * <b>Where the story stands.</b> What is kept, what is continued (a chapter the story went past,
     * never lost), and the ending in words once one is spoken.
     */
    public record State(List<Integer> kept, List<Integer> continued, String ending) {

        public State {
            kept = kept == null ? List.of() : List.copyOf(kept);
            continued = continued == null ? List.of() : List.copyOf(continued);
            ending = ending == null ? "" : ending.trim();
        }

        public String line() {
            return "kept " + kept.size() + ", continued " + continued.size()
                    + (ending.isEmpty() ? ", the story goes on" : ", " + ending);
        }
    }

    /** <b>The outcome of telling a chapter.</b> */
    public record Told(State state, String refused) {
        public boolean ok() {
            return state != null;
        }
    }

    private Campaign() {
    }

    /** The six stations, in story order. */
    public static List<Chapter> chapters() {
        List<Chapter> out = new ArrayList<>();
        out.add(new Chapter(1, "The dawn of the record", "Ur", -2900, Kind.STORY,
                "stand where the record begins and read it whole"));
        out.add(new Chapter(2, "The first crossing", "Cuzco", 1576, Kind.TRAVEL,
                "cross to the ruptured universe and come home along your own ticket"));
        out.add(new Chapter(3, "The paradox", "Delhi", 1857, Kind.PARADOX,
                "judge what your attempt caused, and book it"));
        out.add(new Chapter(4, "The broken point", "Delhi", 1857, Kind.REPAIR,
                "rebuild what was broken and watch the counter fall"));
        out.add(new Chapter(5, "The rupture", "Hyderabad", 2026, Kind.RUPTURE,
                "break open a universe, its copy taken first"));
        out.add(new Chapter(6, "The way home", "The far shore", 2100, Kind.STORY,
                "close the story in words, and carry none of it into the record"));
        return out;
    }

    /**
     * <b>Tell a chapter.</b> A chapter told is kept or continued; there is no third outcome and no
     * state in which the story stops. Refused: no such chapter, and a story told twice over (say so,
     * and the story still goes on).
     */
    public static Told tell(State state, int chapter, boolean kept) {
        State s = state == null ? new State(List.of(), List.of(), "") : state;
        Chapter c = null;
        for (Chapter x : chapters()) {
            if (x.index() == chapter) {
                c = x;
            }
        }
        if (c == null) {
            return new Told(null, "there is no chapter " + chapter + " in this story");
        }
        if (s.kept().contains(chapter) || s.continued().contains(chapter)) {
            return new Told(null, "chapter " + chapter + " (" + c.title() + ") is already told: a story "
                    + "tells its chapters once, and the story goes on");
        }
        List<Integer> keptList = new ArrayList<>(s.kept());
        List<Integer> contList = new ArrayList<>(s.continued());
        if (kept) {
            keptList.add(chapter);
        } else {
            // the decision-7 rule, enforced by construction: a chapter not kept is CONTINUED, never
            // failed and never erased.
            contList.add(chapter);
        }
        return new Told(new State(keptList, contList, s.ending()), "");
    }

    /** The pressure, read as a gauge. It rises, it falls, and nothing here ever fires. */
    public static String pressure(Stress stress) {
        int total = stress == null ? 0 : stress.total();
        return "pressure: " + total + ". It rises and it falls. Nothing here erases you.";
    }

    /**
     * <b>Close the story, in words.</b> Refused: a story with nothing told ("it is unopened"), and a
     * story already closed. The ending is words only; no ending changes anybody or anything.
     */
    public static Told close(State state, String words) {
        if (state == null || (state.kept().isEmpty() && state.continued().isEmpty())) {
            return new Told(null, "a story with no chapter told is not ended, it is unopened");
        }
        if (state != null && !state.ending().isEmpty()) {
            return new Told(null, "this story is already closed: " + state.ending());
        }
        String ending = (words == null || words.trim().isEmpty())
                ? (state.continued().isEmpty()
                        ? "every chapter kept, and the world intact"
                        : "the story went on, differently, and nobody was erased")
                : words.trim();
        return new Told(new State(state.kept(), state.continued(), ending), "");
    }

    /** The page: every chapter in words, and where the story stands. */
    public static List<String> page(State state) {
        List<String> out = new ArrayList<>();
        for (Chapter c : chapters()) {
            String mark = state != null && state.kept().contains(c.index()) ? " [kept]"
                    : state != null && state.continued().contains(c.index()) ? " [continued]" : "";
            out.add(c.line() + mark);
        }
        out.add(state == null ? "the story goes on" : state.line());
        return out;
    }

    public static String ruleLine() {
        return "The campaign is a story in six stations, walked in any order. A player is never erased; "
                + "no countdown deletes anybody; a chapter not kept is continued, never failed; and the "
                + "pressure is a gauge that fires nothing.";
    }
}

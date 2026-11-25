package com.eternalengine.player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * <b>First steps for a new player.</b> J5. A short route through the systems that already ship.
 *
 * <h2>Why this exists</h2>
 *
 * The mod has a command tree of several hundred literals and no path through it. This is the path:
 * six steps, each naming <b>what to do</b> (and the exact command), <b>what it teaches</b> (the rule
 * behind it), and <b>the named fact</b> that counts it done. It is a route like the old campaign and
 * decision 7's six stations: the player walks it in order or out of it, and the page always shows
 * what is left.
 *
 * <h2>The rules (the project's own decisions)</h2>
 *
 * <ul>
 *   <li><b>Notes are written once.</b> A finished step is never un-finished. There is no reset.</li>
 *   <li><b>The next step is the first one not done</b> (a sequence). Marking the same fact twice is
 *       "already counted", never a double.</li>
 *   <li><b>The graduation line is earned once</b>, only when all six are counted, and it names what
 *       the player can now do.</li>
 *   <li>A fact outside the closed vocabulary is <b>refused by name</b>: "there is no such fact to
 *       count" lists the six.</li>
 * </ul>
 *
 * <p>Progress is held in memory by player name. It does not survive a server restart (the
 * capability layer is Forge and out of this sandbox's reach); that limit is said out loud in the
 * README, not hidden.
 */
public final class FirstSteps {

    /** <b>The six facts, a closed vocabulary.</b> One per step. */
    public enum Fact {
        SEEN_DATE,
        SPOKE_TO_SOMEONE,
        LOOKED_AT_THE_PURSE,
        BOUGHT_A_BOOK,
        HEARD_THE_ERA,
        HEARD_TIME_RULES;

        public String words() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    /**
     * <b>One step of the route.</b> {@code command} is the exact command to run (without the leading
     * slash); {@code doneWhen} names the fact that counts it done.
     */
    public record Step(int index, String title, String doThis, String command, String teaches,
                       Fact doneWhen) {

        public Step {
            title = title == null ? "" : title.trim();
            doThis = doThis == null ? "" : doThis.trim();
            command = command == null ? "" : command.trim();
            teaches = teaches == null ? "" : teaches.trim();
        }

        public String line(boolean done) {
            return index + ". " + title + " - " + doThis + " (/ee " + command + ") - teaches: "
                    + teaches + " - " + (done ? "done" : "not yet");
        }
    }

    /** <b>One answer.</b> {@code refused} is empty when the request was sane. */
    public record Answer(String line, String refused) {

        public Answer {
            line = line == null ? "" : line.trim();
            refused = refused == null ? "" : refused.trim();
        }

        public boolean ok() {
            return refused.isEmpty();
        }
    }

    /** What is counted for one player. Facts are written once and never removed. */
    public record Progress(String name, Set<Fact> done) {

        public Progress {
            name = name == null ? "" : name.trim();
            done = Set.copyOf(done == null ? Set.of() : done);
        }

        public int count() {
            return done.size();
        }

        public boolean has(Fact fact) {
            return done.contains(fact);
        }
    }

    private static final Map<String, Set<Fact>> PROGRESS = new LinkedHashMap<>();
    private static final Set<String> GRADUATED = new LinkedHashSet<>();

    private FirstSteps() {
    }

    /** The six steps, in the order a new player meets them. */
    public static List<Step> steps() {
        List<Step> out = new ArrayList<>();
        out.add(new Step(1, "Where and when you are",
                "ask the date: the world has one date and this place may use its own calendar",
                "date",
                "one shared date and timeline per world (decision 3), and your calendar may differ",
                Fact.SEEN_DATE));
        out.add(new Step(2, "Talk to a person",
                "walk up to someone and talk",
                "talk",
                "people stay while you talk, and what they know is dated to their own time",
                Fact.SPOKE_TO_SOMEONE));
        out.add(new Step(3, "Your purse",
                "look at your purse and the money of this place",
                "purse",
                "money is everywhere (401 kinds), and a purse cannot go below zero",
                Fact.LOOKED_AT_THE_PURSE));
        out.add(new Step(4, "Buy the history book",
                "buy the history book from a villager, then read what you were sold",
                "book",
                "the history book is bought, not given: 13 forms by place and year",
                Fact.BOUGHT_A_BOOK));
        out.add(new Step(5, "Look and listen to the era",
                "read the signs and listen: the signs are in the local script and the sounds "
                        + "belong to the year",
                "signs",
                "the world shows its own era: scripts, sounds and what people can do are dated",
                Fact.HEARD_THE_ERA));
        out.add(new Step(6, "How time works here",
                "read the rules of time before you try to change anything",
                "help",
                "one shared date and timeline per world; three kinds of act (moves only you, "
                        + "moves the shared world, rewrites what is recorded); a branch is a "
                        + "difference from its parent; a person moves time only if they may",
                Fact.HEARD_TIME_RULES));
        return out;
    }

    private static void requireName(String player, String what) {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException("a " + what + " with no name cannot be counted");
        }
    }

    /** What is counted for this player. Every fact is false until it is marked. */
    public static Progress progress(String player) {
        requireName(player, "player");
        Set<Fact> done = PROGRESS.computeIfAbsent(player.trim(), k -> new LinkedHashSet<>());
        return new Progress(player.trim(), done);
    }

    /**
     * <b>Count one fact.</b> Marking it twice says "already counted" and changes nothing; a name
     * outside the six is refused and the six are listed. A player with no name is refused too.
     */
    public static Answer mark(String player, String fact) {
        if (player == null || player.isBlank()) {
            return new Answer("", "a player with no name cannot be counted");
        }
        Fact parsed;
        try {
            parsed = parseFact(fact);
        } catch (IllegalArgumentException e) {
            return new Answer("", e.getMessage());
        }
        Set<Fact> done = PROGRESS.computeIfAbsent(player.trim(), k -> new LinkedHashSet<>());
        if (done.contains(parsed)) {
            return new Answer("already counted: " + parsed.words() + " (" + done.size()
                    + " of " + steps().size() + " steps)", "");
        }
        done.add(parsed);
        String left = "";
        Step next = next(player);
        if (next != null) {
            left = " Next: " + next.index() + ". " + next.title() + " (/ee " + next.command() + ").";
        } else {
            left = " The route is finished: /ee firststeps graduate.";
        }
        return new Answer("counted: " + parsed.words() + " (" + done.size() + " of "
                + steps().size() + " steps)." + left, "");
    }

    private static Fact parseFact(String fact) {
        if (fact == null || fact.isBlank()) {
            throw new IllegalArgumentException("a fact with no name is not a fact to count");
        }
        String key = fact.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (Fact f : Fact.values()) {
            if (f.name().equals(key)) {
                return f;
            }
        }
        throw new IllegalArgumentException("there is no such fact to count: \"" + fact.trim()
                + "\". The six are: " + factList());
    }

    private static String factList() {
        StringBuilder sb = new StringBuilder();
        for (Fact f : Fact.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(f.words());
        }
        return sb.toString();
    }

    /** <b>The next step:</b> the first one whose fact is not counted yet, or null when finished. */
    public static Step next(String player) {
        Progress progress = progress(player);
        for (Step step : steps()) {
            if (!progress.has(step.doneWhen())) {
                return step;
            }
        }
        return null;
    }

    /** <b>The page:</b> every step with its done mark, and the graduation line when it is earned. */
    public static List<String> page(String player) {
        Progress progress = progress(player);
        List<String> out = new ArrayList<>();
        out.add("First steps for " + progress.name() + ": " + progress.count() + " of "
                + steps().size() + " done.");
        for (Step step : steps()) {
            out.add(step.line(progress.has(step.doneWhen())));
        }
        if (next(player) == null) {
            out.add(graduation().line());
        }
        return out;
    }

    /** <b>The graduation line,</b> earned once all six facts are counted. */
    public static Answer graduation() {
        return new Answer("You have your bearings. The world runs 3000 BC to AD 3000 and the record "
                + "ends at 2026: past that everything is marked projected. Ask /ee help for the rest, "
                + "buy another history book in another year, and remember: a purse cannot go below "
                + "zero, notes are written once, and a branch is a difference from its parent.", "");
    }

    /**
     * <b>Graduate one player.</b> Refused while any step is left: the line is earned, not printed.
     * Graduating twice says so and does not reprint the line: it was earned once and stands.
     */
    public static Answer graduate(String player) {
        if (player == null || player.isBlank()) {
            return new Answer("", "a player with no name cannot be counted");
        }
        Progress progress = progress(player);
        Step next = next(player);
        if (next != null) {
            return new Answer("", "the route is not finished: " + next.index() + ". "
                    + next.title() + " (/ee " + next.command() + ") is left ("
                    + progress.count() + " of " + steps().size() + " done)");
        }
        if (GRADUATED.contains(player.trim())) {
            return new Answer("already graduated: the line was earned once and stands", "");
        }
        GRADUATED.add(player.trim());
        return new Answer("counted: the route is finished. " + graduation().line(), "");
    }

    public static String ruleLine() {
        return "Six first steps, each naming its command and the rule behind it. Notes are written "
                + "once: a finished step is never un-finished and there is no reset. The next step is "
                + "the first one not done; the graduation line is earned, not printed.";
    }
}

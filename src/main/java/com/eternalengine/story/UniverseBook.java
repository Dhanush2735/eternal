package com.eternalengine.story;

import com.eternalengine.clock.Timeline;
import com.eternalengine.timeline.BrokenLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>The papers and the book of one ruptured universe, read through its rule set.</b> H6 second half,
 * and the tie to H4.
 *
 * <h2>Why this class exists</h2>
 *
 * H6 says the new universe has <b>its own history, people, papers and book</b>. {@link UniverseStory} is
 * the history and the people; this class is the other two surfaces and the reading of all four through the
 * universe's broken logic ({@link BrokenLogic}).
 *
 * <h2>Reading through rules</h2>
 *
 * <p>{@link Reading#through} bends a surface the way the universe's rule set says, and <b>always names
 * every rule it applied</b>. Four kinds bend something a reader can see (backwards time reverses the
 * reading order; scrambled languages shifts vowels one along; money upside down reads prices inverted;
 * eras mixed marks lines from another age). Three kinds bend nothing a reader can see - out of order,
 * effects before causes and doubles are what {@link BrokenLogic#scan} proves from the entries - and the
 * reading names them too, because a rule the reader cannot see is still a rule, and silence is never the
 * answer.
 *
 * <h2>The guardrail travels with the words</h2>
 *
 * <p>Every story a paper prints asks {@link UniverseStory#guardRefusal} first: past 2026 a paper may
 * continue a state but never name a new ruler, war, price or population.
 */
public final class UniverseBook {

    /** <b>One paper of one universe for one date.</b> The mark is computed, never declared. */
    public record Sheet(String universe, String place, int date, String mark, List<String> stories) {

        public Sheet {
            universe = universe == null ? "" : universe.trim();
            place = place == null ? "" : place.trim();
            stories = stories == null ? List.of() : List.copyOf(stories);
            mark = Timeline.markFor(date);
        }

        public List<String> page() {
            List<String> out = new ArrayList<>();
            out.add("the paper of " + place + ", " + date + ", in " + universe + " [" + mark + "]");
            for (String s : stories) {
                out.add("  " + s);
            }
            return out;
        }
    }

    /** <b>The outcome of printing a paper.</b> */
    public record Printed(Sheet sheet, List<String> refused) {
        public boolean ok() {
            return sheet != null;
        }
    }

    /**
     * <b>A surface read through a universe's rule set.</b> Built once per reading; every bent output is
     * one the rules asked for, and {@link #applied} names them all.
     */
    public record Reading(List<String> applied, List<BrokenLogic.Kind> kinds) {

        /** Read through a rule set. The applied lines are the kinds, each naming what it bends. */
        public static Reading through(BrokenLogic.Spec spec) {
            List<BrokenLogic.Kind> on = new ArrayList<>();
            for (BrokenLogic.Kind k : BrokenLogic.Kind.values()) {
                if (spec != null && spec.has(k)) {
                    on.add(k);
                }
            }
            return new Reading(BrokenLogic.changesFromEarth(spec), List.copyOf(on));
        }

        /** The history page as this universe reads it. Backwards time reads newest first. */
        public List<String> history(List<String> lines) {
            List<String> out = new ArrayList<>(lines == null ? List.of() : lines);
            if (kinds.contains(BrokenLogic.Kind.TIME_BACKWARDS)) {
                java.util.Collections.reverse(out);
            }
            return out;
        }

        /** Words as this universe speaks them. Scrambled languages shifts every vowel one along. */
        public String words(String text) {
            if (!kinds.contains(BrokenLogic.Kind.SCRAMBLED_LANGUAGES) || text == null) {
                return text;
            }
            StringBuilder b = new StringBuilder();
            for (char c : text.toCharArray()) {
                b.append(shift(c));
            }
            return b.toString();
        }

        /** A price as this universe reads it. Upside down, the value is read inverted. */
        public String money(String amount) {
            if (!kinds.contains(BrokenLogic.Kind.MONEY_UPSIDE_DOWN) || amount == null) {
                return amount;
            }
            try {
                double v = Double.parseDouble(amount.trim());
                if (v == 0) {
                    return amount + " (read upside down: no value inverts to no value)";
                }
                return amount + " (read upside down: " + trim(1.0 / v) + ")";
            } catch (NumberFormatException e) {
                return amount + " (read upside down)";
            }
        }

        /** A line as this universe dates it. Eras mixed marks lines from another age. */
        public String era(String line) {
            if (!kinds.contains(BrokenLogic.Kind.ERAS_MIXED) || line == null) {
                return line;
            }
            return "from another age: " + line;
        }

        /** Every rule this reading applied, in words. Never empty of meaning: none on is said too. */
        public List<String> applied() {
            return applied;
        }

        public String line() {
            return applied.isEmpty() ? "read through the logic of the unbroken world"
                    : "read through: " + String.join("; ", applied);
        }

        private static char shift(char c) {
            String vowels = "aeiou";
            String shifted = "eioua";
            String up = vowels.toUpperCase(Locale.ROOT);
            String upShifted = shifted.toUpperCase(Locale.ROOT);
            int i = vowels.indexOf(c);
            if (i >= 0) {
                return shifted.charAt(i);
            }
            int j = up.indexOf(c);
            return j >= 0 ? upShifted.charAt(j) : c;
        }

        private static String trim(double v) {
            String s = String.valueOf(v);
            return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
        }
    }

    private UniverseBook() {
    }

    /**
     * <b>Print the paper.</b> Every story asks the guardrail first; a story that trips it is refused by
     * name and the rest of the paper still prints.
     */
    public static Printed paper(String universe, String place, int date, List<String> stories) {
        List<String> refused = new ArrayList<>();
        List<String> printed = new ArrayList<>();
        if (universe == null || universe.trim().isEmpty()) {
            refused.add("a paper that says which universe it is from: name the universe first");
        }
        if (place == null || place.trim().isEmpty()) {
            refused.add("a paper that says where: name the place");
        }
        for (String story : stories == null ? List.<String>of() : stories) {
            String guard = UniverseStory.guardRefusal(date, story);
            if (guard != null) {
                refused.add(guard);
            } else if (story != null && !story.trim().isEmpty()) {
                printed.add(story.trim());
            }
        }
        if (universe == null || universe.trim().isEmpty() || place == null || place.trim().isEmpty()) {
            return new Printed(null, refused);
        }
        return new Printed(new Sheet(universe, place, date, Timeline.markFor(date), printed), refused);
    }

    /**
     * <b>The book page for one place and date.</b> What the universe broke, its history for the place,
     * its people, and the projection note where one is owed. A page with nothing recorded says so; it
     * never fills itself.
     */
    public static List<String> book(String universe, String place, int year, List<String> changes,
                                    UniverseStory story) {
        List<String> out = new ArrayList<>();
        String u = universe == null ? "" : universe.trim();
        out.add("the book of " + (place == null ? "" : place.trim()) + ", " + year + ", in "
                + (u.isEmpty() ? "no universe named" : u) + " [" + Timeline.markFor(year) + "]");
        if (changes != null && !changes.isEmpty()) {
            out.add("this universe broke off from its parent: " + String.join("; ", changes));
        }
        if (story != null && story.universe().equals(u)) {
            int shown = 0;
            for (UniverseStory.Line l : story.history()) {
                if (place == null || l.place().equalsIgnoreCase(place.trim())) {
                    out.add("  " + l.line());
                    shown++;
                }
            }
            for (UniverseStory.Person p : story.people()) {
                out.add("  " + p.line());
                shown++;
            }
            if (shown == 0) {
                out.add("  nothing is recorded here yet");
            }
        } else {
            out.add("  nothing is recorded here yet");
        }
        String note = Timeline.projectionNote(year);
        if (note != null) {
            out.add(note);
        }
        return out;
    }

    public static String ruleLine() {
        return "A universe's papers and book are its own, and every surface read through its rule set "
                + "names every rule applied. Four kinds bend what a reader sees; three are what the scan "
                + "proves from the entries, and the reading names those too rather than staying silent.";
    }
}

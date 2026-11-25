package com.eternalengine.terrabridge;

import com.eternalengine.story.BranchStory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * <b>G6 and A12: the world follows the timeline, as far as this project can honestly take it.</b>
 *
 * <h2>What the two systems ask for, and what can be done</h2>
 *
 * <p>G6 is "the world follows the timeline: buildings, landmarks, roads". A12 is the Terra++ link, of which heights
 * and places are done and <b>branches and universes are not</b>. The honest split is this:
 *
 * <ul>
 *   <li><b>What belongs on a branch is the mod's business.</b> Given the rows of the record and a branch's
 *       statements about what did and did not happen, the set of things that stand is computable here, and this
 *       class computes it.</li>
 *   <li><b>Drawing it is Terra++'s business.</b> The mod does not generate terrain. So everything that reaches
 *       the world goes through one narrow {@link Port}, which is the contract Terra++ would implement, and which
 *       in a probe is a fake. <b>No probe in this project can prove that Terra++ draws the other town</b>, and the
 *       notes say so rather than implying otherwise.</li>
 * </ul>
 *
 * <h2>How a branch is joined to the record, and the weakness of that join</h2>
 *
 * <p>A branch speaks in pins and in names ("the railway did not happen"); the record holds rows with names and
 * years. <b>The only join available is the name</b>, and matching on a name is the one thing this project has
 * recorded as a mistake often enough to distrust. So the match is:
 *
 * <ul>
 *   <li>case-insensitive and exact after trimming, never fuzzy and never a substring;</li>
 *   <li><b>every unmatched statement is reported</b>, both ways: a branch statement with no row, and a row the
 *       branch says nothing about, which simply stands;</li>
 *   <li>a statement about a thing the record has <b>twice</b> near the same point is refused rather than applied,
 *       because "the bridge did not happen" with two bridges is a question, not an instruction.</li>
 * </ul>
 *
 * <p>Two names that match is a real case in the packs: Manchester has two stations called the same thing, and a
 * silent pick between them is exactly the sort of quiet wrongness this project exists to avoid.
 */
public final class BranchWorld {

    /** What a branch says about one thing. {@code happens} false means it did not, or no longer stands there. */
    public record Statement(String name, String kind, int year, boolean happens, String why) {

        public Statement {
            name = name == null ? "" : name.trim();
            kind = kind == null || kind.isBlank() ? "thing" : kind.trim().toLowerCase(Locale.ROOT);
            why = why == null ? "" : why;
        }
    }

    /** One row of the record, and whether it stands on this branch. */
    public record Decision(String name, String kind, int year, String record, boolean stands, String why) {

        public String line() {
            return (stands ? "stands: " : "does not stand: ") + name + " (" + kind + ", " + year + ", " + record
                    + ")" + (why.isBlank() ? "" : " · " + why);
        }
    }

    /** A statement the plan could not place in the record, and why. Never dropped. */
    public record Unplaced(String name, String kind, int year, String why) {

        public String line() {
            return name + " (" + kind + ", " + year + "): " + why;
        }
    }

    /** What the world on one branch should show. */
    public record Plan(String branch, int year, List<Decision> decisions, List<Unplaced> unplaced) {

        public Plan {
            decisions = List.copyOf(decisions == null ? List.of() : decisions);
            unplaced = List.copyOf(unplaced == null ? List.of() : unplaced);
        }

        public List<Decision> standing() {
            List<Decision> out = new ArrayList<>();
            for (Decision d : decisions) {
                if (d.stands()) out.add(d);
            }
            return out;
        }

        public List<Decision> gone() {
            List<Decision> out = new ArrayList<>();
            for (Decision d : decisions) {
                if (!d.stands()) out.add(d);
            }
            return out;
        }

        public boolean empty() {
            return decisions.isEmpty();
        }

        public String line() {
            return "On " + branch + " in " + year + ": " + standing().size() + " of " + decisions.size()
                    + " things still stand, " + gone().size() + " do not, and " + unplaced.size()
                    + " statement" + (unplaced.size() == 1 ? "" : "s") + " could not be placed in the record.";
        }
    }

    /**
     * <b>The seam Terra++ would implement.</b> Two calls, because those are the two things a world needs to be
     * told: something that was there is not, and something that was not there is.
     *
     * <p>Deliberately small. Every method added here is a promise that the mod knows something about terrain, and
     * it does not: the mod knows what stands, and Terra++ knows where the ground is.
     */
    public interface Port {

        /** Take away a thing the record has and this branch does not. Returns false where it could not. */
        boolean remove(Decision decision);

        /** Put in place a thing this branch has and the record does not. Returns false where it could not. */
        boolean add(Decision decision);

        /** A name for the log: which implementation answered, and whether it was real. */
        String describe();
    }

    /** What happened when a plan was handed to a port. */
    public record Applied(int removed, int added, int refused, List<String> log) {

        public Applied {
            log = List.copyOf(log == null ? List.of() : log);
        }

        public boolean clean() {
            return refused == 0;
        }

        public String line() {
            return removed + " taken away, " + added + " put in place, " + refused + " refused"
                    + (clean() ? "" : ", and the world is not the branch's until those are answered");
        }
    }

    private BranchWorld() {
    }

    /**
     * Work out what stands on a branch, from the rows of the record and what the branch says.
     *
     * @param branch      the branch's id, for the log
     * @param year        the year the world is showing
     * @param facts       the record's dated rows near the place, from {@code PlaceRecord.facts}
     * @param statements  what the branch says happened or did not
     */
    public static Plan plan(String branch, int year, List<BranchStory.Fact> facts, List<Statement> statements) {
        List<Decision> decisions = new ArrayList<>();
        List<Unplaced> unplaced = new ArrayList<>();
        List<Statement> said = new ArrayList<>(statements == null ? List.of() : statements);
        List<String> claimed = new ArrayList<>();
        List<BranchStory.Fact> rows = facts == null ? List.of() : facts;

        for (BranchStory.Fact f : rows) {
            if (f == null || f.year() > year) {
                continue;
            }
            Statement match = null;
            for (Statement s : said) {
                if (s.name().equalsIgnoreCase(f.name().trim())) {
                    match = s;
                }
            }
            if (match != null) {
                claimed.add(match.name());
            }
            // 1. The record's own end year needs no statement from anybody. This is checked before the branch is
            //    consulted, because a thing the record already ends is gone whatever a branch says about it.
            if (f.endYear() != null && f.until() < year) {
                decisions.add(new Decision(f.name(), f.kind(), f.year(), f.record(), false,
                        "the record itself ends it in " + f.until()));
                continue;
            }
            // 2. Nothing said about it: it stands, and the plan says why it stands (nobody argued).
            if (match == null) {
                decisions.add(new Decision(f.name(), f.kind(), f.year(), f.record(), true,
                        "the branch says nothing about it"));
                continue;
            }
            // 3. A name the record holds more than once is a question, not an instruction. The thing still stands,
            //    because refusing a statement is not the same as believing its opposite, and the unplaced list says
            //    what was refused. One entry per name, not one per row.
            int same = 0;
            for (BranchStory.Fact g : rows) {
                if (g != null && g.year() <= year && g.name().equalsIgnoreCase(f.name())) {
                    same++;
                }
            }
            if (same > 1) {
                boolean already = false;
                for (Unplaced u : unplaced) {
                    if (u.name().equalsIgnoreCase(match.name())) already = true;
                }
                if (!already) {
                    unplaced.add(new Unplaced(match.name(), match.kind(), match.year(),
                            "the record holds " + same + " things by that name here, so \"it did not happen\" is "
                                    + "a question and not an answer"));
                }
                decisions.add(new Decision(f.name(), f.kind(), f.year(), f.record(), true,
                        "the branch's statement about it could not be placed: the name is not unique here"));
                continue;
            }
            // 4. The ordinary case: the branch says it did or did not happen, and the branch is believed.
            decisions.add(new Decision(f.name(), f.kind(), f.year(), f.record(), match.happens(), match.why()));
        }

        for (Statement s : said) {
            boolean used = false;
            for (String c : claimed) {
                if (c.equalsIgnoreCase(s.name())) {
                    used = true;
                }
            }
            if (!used) {
                unplaced.add(new Unplaced(s.name(), s.kind(), s.year(),
                        "no row by that name is in the record here, so there is nothing in the world to change"));
            }
        }
        return new Plan(branch, year, decisions, unplaced);
    }

    /**
     * Hand the plan to a port. A refusal is counted and logged, never swallowed: a world that silently failed to
     * remove the thing the branch removed is a world that says one thing and shows another.
     */
    public static Applied apply(Plan plan, Port port) {
        if (plan == null || port == null) {
            throw new IllegalArgumentException("a plan and a port are both needed");
        }
        int removed = 0, added = 0, refused = 0;
        List<String> log = new ArrayList<>();
        for (Decision d : plan.decisions()) {
            boolean done = d.stands() ? port.add(d) : port.remove(d);
            if (done) {
                if (d.stands()) added++;
                else removed++;
            } else {
                refused++;
                log.add("the world refused to " + (d.stands() ? "keep " : "take away ") + d.name());
            }
        }
        for (Unplaced u : plan.unplaced()) {
            log.add("unplaced: " + u.line());
        }
        return new Applied(removed, added, refused, log);
    }

    /**
     * A port that does nothing, for a world with no Terra++, and which <b>says</b> it does nothing. Returning a
     * quiet success would be the worst of the three possible behaviours: the caller could not tell a world that
     * obeyed from a world that was never asked.
     */
    public static Port absent() {
        return new Port() {
            @Override
            public boolean remove(Decision decision) {
                return false;
            }

            @Override
            public boolean add(Decision decision) {
                return false;
            }

            @Override
            public String describe() {
                return "no world port: Terra++ is not connected, so nothing on this branch reaches the ground";
            }
        };
    }

    /** One line for a screen, saying what this class will and will not do. */
    public static String ruleLine() {
        return "The mod decides what stands on a branch and Terra++ draws it. Where Terra++ is not connected, "
                + "nothing is drawn and the count of refusals says so rather than the world pretending to have "
                + "changed.";
    }

    /** Counts a plan by kind, for a log line that says what a branch actually changes in the world. */
    public static Map<String, Integer> byKind(Plan plan) {
        Map<String, Integer> out = new TreeMap<>();
        for (Decision d : plan.gone()) {
            out.merge(d.kind(), 1, Integer::sum);
        }
        return new LinkedHashMap<>(out);
    }
}

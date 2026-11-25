package com.eternalengine.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>What the record says inside a player-caused fork.</b> 2026-10-27.
 *
 * <h2>The two rules this class exists to enforce</h2>
 *
 * <ol>
 *   <li><b>Nothing before the fork changes.</b> Ask about 1800 in a fork that happens in 1948 and the answer is
 *       the record's answer, word for word. A branch that quietly rewrites the deep past is not a branch, it is
 *       a lie about history.</li>
 *   <li><b>The sky is identical in every timeline.</b> The eclipse of 2 August 2027, Apophis on 13 April 2029
 *       and Halley in 2061 happen on the same dates whether Gandhi lived or died. Only human events diverge.
 *       This is what makes {@code history/future_story.json} valid in every branch and it is the one invariant
 *       a player can check for themselves from any branch.</li>
 * </ol>
 *
 * <p>Everything else here is presentation: two accounts of the same year and place, so a player can see what
 * they changed and what they did not.
 */
public final class DivergedHistory {

    private DivergedHistory() {
    }

    /** Two ways of telling the same year: the record, and this fork. */
    public record TwoAccounts(String forkId, int year, String record, String branch, boolean sameAsRecord) {

        public String line() {
            if (sameAsRecord) {
                return year + ": both timelines agree - " + record;
            }
            return year + ": the record has " + record + "; here, " + branch;
        }
    }

    /**
     * Is this year on the far side of the fork?
     *
     * <p>Every question about a place and year begins here, because the answer decides whether the record may be
     * read at all.
     */
    public static boolean after(com.eternalengine.story.Divergence fork, int year) {
        return year >= fork.triggerYear();
    }

    /**
     * The two accounts of one year.
     *
     * <p>Before the fork: identical, and said so plainly. On or after it: the record's line is what really
     * happened (the scenario's own {@code [fact]} text), and the branch line is what this fork puts in its place.
     */
    public static TwoAccounts accounts(com.eternalengine.story.Divergence fork, int year) {
        if (!after(fork, year)) {
            return new TwoAccounts(fork.id(), year,
                    "the year as it really was (" + year + " is before this fork)", "", true);
        }
        String record = "";
        String branch = "";
        for (Divergence.Consequence c : fork.consequences()) {
            if (c.year() != year) {
                continue;
            }
            if (record.isEmpty()) {
                record = factPart(c.text());
                branch = c.name() + " - " + plainPart(c.text());
            }
        }
        if (record.isEmpty()) {
            // A year inside the fork's reach for which the scenario authors nothing: say so rather than invent.
            return new TwoAccounts(fork.id(), year,
                    "the record carries nothing for " + year + " at this place", "", false);
        }
        if (record.startsWith("[plausible]") || record.startsWith("[causal]")) {
            // Found by the probe on 2026-10-27: a line marked [plausible] or [causal] is the scenario's own
            // reasoning, NOT the record. Printing it in the record column labelled speculation as history.
            record = "nothing of its own - this year's line is the fork's reasoning, not the record";
            branch = stripMarks(branch);
        }
        return new TwoAccounts(fork.id(), year, record, branch, false);
    }

    /** The {@code [fact]} half of a scenario line: what actually happened. */
    private static String factPart(String text) {
        int i = text.indexOf("[fact]");
        if (i < 0) {
            return text;
        }
        int j = text.indexOf("[plausible]", i);
        String out = (j < 0 ? text.substring(i) : text.substring(i, j)).replace("[fact]", "").trim();
        return out.isEmpty() ? text : out;
    }

    private static String stripMarks(String text) {
        return text.replace("[plausible]", "").replace("[causal]", "").replace("[fact]", "").trim();
    }

    /** The {@code [plausible]} half: what this fork says instead. */
    private static String plainPart(String text) {
        int i = text.indexOf("[plausible]");
        if (i < 0) {
            return "(nothing authored for this year)";
        }
        return text.substring(i).replace("[plausible]", "").trim();
    }

    /**
     * Every year in which this fork says something different from the record.
     *
     * <p>A fork that answers nothing is not a fork; the check refuses it.
     */
    public static List<Integer> changedYears(com.eternalengine.story.Divergence fork) {
        List<Integer> out = new ArrayList<>();
        for (Divergence.Consequence c : fork.consequences()) {
            if (!out.contains(c.year())) {
                out.add(c.year());
            }
        }
        out.sort(Integer::compareTo);
        return out;
    }

    /**
     * The sky is the same in every timeline.
     *
     * <p>This returns the dates of the computed astronomy, unchanged, for any fork - by construction, because
     * {@code future_story.json} is not a function of the fork and nothing in this package may touch it. The
     * point of returning it here is that a test can assert the two are identical rather than trusting a comment.
     */
    public static List<String> skyDates() {
        // Found by PlayerForkProbe on 2026-10-27: this used to concatenate every fork's sky, so three forks
        // reported 363 dates and read as if the sky had changed. It is ONE sky; return it once, unique.
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (com.eternalengine.story.Divergence f : com.eternalengine.story.Divergence.all()) {
            unique.addAll(skyDates(f));
        }
        if (unique.isEmpty()) {
            // No shipped fork loaded (a bare probe): ask for the sky once, from the file itself.
            unique.addAll(skyDates((com.eternalengine.story.Divergence) null));
        }
        List<String> out = new ArrayList<>(unique);
        java.util.Collections.sort(out);
        return out;
    }

    /** The same sky, asked from inside one fork. Identical by design; asserted by the check. */
    public static List<String> skyDates(com.eternalengine.story.Divergence fork) {
        List<String> dates = new ArrayList<>();   // the argument is deliberately unused: the sky never forks
        try (java.io.InputStream in = DivergedHistory.class.getResourceAsStream(
                "/data/eternalengine/eternal/history/future_story.json")) {
            if (in == null) {
                return dates;
            }
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(
                    new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (com.google.gson.JsonElement el : o.getAsJsonArray("computed")) {
                dates.add(el.getAsJsonObject().get("date").getAsString());
            }
        } catch (Exception e) {
            return dates;
        }
        java.util.Collections.sort(dates);
        return dates;
    }

    /** One line a player can be shown when standing in a fork. */
    public static String banner(com.eternalengine.story.Divergence fork) {
        return String.format(Locale.ROOT,
                "You are in a fork: %s (%d). Before %d this world is the record; from %d it is what somebody "
                        + "wrote. The sky is unchanged - the eclipse of 2027 still falls on 2 August.",
                fork.title(), fork.triggerYear(), fork.triggerYear(), fork.triggerYear());
    }
}

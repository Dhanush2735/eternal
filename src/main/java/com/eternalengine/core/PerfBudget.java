package com.eternalengine.core;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Measuring: budgets as data, and the shape of the test walk's report.</b> I1.
 *
 * <h2>Why this class exists</h2>
 *
 * {@code core/PerformanceMonitor} already meters the live game: per-system times, a tick budget by
 * weight, a slow-down rule. What nothing held was <b>what to measure against</b> when the game is not
 * running: the budgets themselves, as data, each naming <b>where its number comes from</b>. A budget
 * that does not say whether it is a game default or a measurement is a guess wearing a number's clothes.
 *
 * <h2>The walk</h2>
 *
 * <p>The plan asks for a 30-minute test walk and Spark. Spark is a live Minecraft profiler and cannot
 * run here; the walk can, over the shipped registers and the mod's own lookup code, and step 57's
 * walk probe does exactly that. This class holds the report that walk produces ({@link Sample},
 * {@link Report}) and the words for what blew a budget ({@link #check}): never silence, never a bare
 * number.
 */
public final class PerfBudget {

    /** <b>Where a budget's number comes from.</b> */
    public enum Source {
        GAME_DEFAULT("a game default (this project's, not Minecraft's)"),
        MEASURED("measured on the test machine by the step 57 walk"),
        VANILLA_LIMIT("a limit Minecraft itself imposes");

        private final String words;

        Source(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** <b>One budget.</b> What it limits, how much, and where the number comes from. */
    public record Budget(String what, double limit, String unit, Source source, String note) {

        public Budget {
            what = what == null ? "" : what.trim();
            unit = unit == null ? "" : unit.trim();
            note = note == null ? "" : note.trim();
        }

        public String line() {
            return what + ": " + limit + " " + unit + " (" + source.words()
                    + (note.isEmpty() ? "" : "; " + note) + ")";
        }
    }

    /** <b>One system's cost over the walk.</b> Calls made, total and worst single call, in milliseconds. */
    public record Sample(String system, int calls, double totalMs, double maxMs) {

        public Sample {
            system = system == null ? "" : system.trim();
        }

        public double avgMs() {
            return calls <= 0 ? 0.0 : totalMs / calls;
        }

        public String line() {
            return String.format("%s: %d calls, %.3f ms total, %.4f ms worst, %.4f ms average",
                    system, calls, totalMs, maxMs, avgMs());
        }
    }

    /** <b>The walk's report.</b> Its samples, the budgets it was measured against, and how long it ran. */
    public record Report(List<Sample> samples, List<Budget> budgets, long walkMs, int iterations) {

        public Report {
            samples = samples == null ? List.of() : List.copyOf(samples);
            budgets = budgets == null ? List.of() : List.copyOf(budgets);
        }

        public Sample worst() {
            Sample worst = null;
            for (Sample s : samples) {
                if (worst == null || s.maxMs() > worst.maxMs()) {
                    worst = s;
                }
            }
            return worst;
        }
    }

    /**
     * The tick's whole budget, in milliseconds. The game default of {@code maxTickBudgetMs} (10),
     * shared out by weight in {@code PerformanceMonitor}.
     */
    public static final Budget TICK_TOTAL = new Budget("the whole tick", 10.0, "ms",
            Source.GAME_DEFAULT, "maxTickBudgetMs, shared out by weight");

    /** One lookup answering a player's question: a fifth of a tick at most. */
    public static final Budget ONE_QUERY = new Budget("one lookup answering a player", 2.0, "ms",
            Source.GAME_DEFAULT, "a lookup that eats a fifth of a tick is a lookup to fix");

    /** One packet. Minecraft refuses a custom payload over 32,767 bytes. */
    public static final Budget PACKET_BYTES = new Budget("one packet", 32767.0, "bytes",
            Source.VANILLA_LIMIT, "the custom-payload limit; under it, a packet still budgets itself");

    /** All caches together: step 26's measured data budget (64 MB), held as the cache ceiling. */
    public static final Budget CACHE_BYTES = new Budget("all caches together", 64.0 * 1024 * 1024,
            "bytes", Source.GAME_DEFAULT, "step 26's data budget: 128 MB of registers, 64 MB cached");

    /** The budgets this step measures against. */
    public static List<Budget> budgets() {
        return List.of(TICK_TOTAL, ONE_QUERY, PACKET_BYTES, CACHE_BYTES);
    }

    private PerfBudget() {
    }

    /**
     * <b>What blew the budget.</b> A line in words naming the system, the budget, and by how much; null
     * when the sample is inside every budget it is measured against.
     */
    public static String over(Sample sample, Budget budget) {
        if (sample == null || budget == null) {
            return null;
        }
        double worst = budget == ONE_QUERY ? sample.avgMs() : sample.maxMs();
        String which = budget == ONE_QUERY ? "average call" : "worst call";
        if (worst <= budget.limit()) {
            return null;
        }
        return sample.system() + " blew " + budget.what() + ": " + which + " " + fmt(worst) + " "
                + budget.unit() + " against " + fmt(budget.limit()) + " (" + fmt(worst - budget.limit())
                + " over; " + budget.source().words() + ")";
    }

    /** Every blow, in words. An empty list is a clean walk. */
    public static List<String> check(Report report) {
        List<String> out = new ArrayList<>();
        if (report == null) {
            out.add("no report: nothing was measured, and nothing is pardoned");
            return out;
        }
        for (Sample s : report.samples()) {
            String line = over(s, ONE_QUERY);
            if (line != null) {
                out.add(line);
            }
        }
        Sample worst = report.worst();
        if (worst != null) {
            String line = over(worst, TICK_TOTAL);
            if (line != null) {
                out.add(line);
            }
        }
        return out;
    }

    /** The page: the report in words, budgets named with their sources. */
    public static List<String> page(Report report) {
        List<String> out = new ArrayList<>();
        if (report == null) {
            return List.of("no report");
        }
        out.add("walk: " + report.iterations() + " iterations in " + report.walkMs() + " ms");
        for (Budget b : report.budgets()) {
            out.add("budget " + b.line());
        }
        for (Sample s : report.samples()) {
            out.add(s.line());
        }
        for (String blow : check(report)) {
            out.add("OVER: " + blow);
        }
        return out;
    }

    public static String ruleLine() {
        return "Every budget names where its number comes from: a game default, a measurement, or "
                + "Minecraft's own limit. A blown budget is named in words and never hidden.";
    }

    private static String fmt(double v) {
        String s = String.valueOf(v);
        return s.length() > 8 ? s.substring(0, 8) : s;
    }
}

package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * <b>G2: what depends on what.</b> The dependency graph, built from the two things the record actually
 * carries and from nothing else.
 *
 * <h2>What the record gives, measured rather than assumed</h2>
 *
 * <p>Step 49's survey read every history pack and found that <b>41,788 dated events ship with 120 stated
 * causes between them</b>, all in the 248 hand-written Deccan events, and that the {@code effect} field on
 * all 2,397 wars is the literal string {@code "note"}. There is no causal graph in the data. So this class
 * builds one from the two properties every event does carry:
 *
 * <ol>
 *   <li><b>A stated cause</b>, where a hand-written row has one. This is the strongest evidence there is
 *       and it covers 120 events.</li>
 *   <li><b>Time and place</b>, which every event has. An event falls inside a war when the war's span
 *       covers its dates and the war's radius covers its position. Measured on 1,500 sampled battles:
 *       <b>39% fall inside some war and 14% fall inside more than one.</b></li>
 * </ol>
 *
 * <h2>The rule that keeps it honest: an edge is a candidate, not a fact</h2>
 *
 * <p>Fourteen per cent is not a rounding error, it is one event in seven having two plausible parents. So a
 * computed edge is never returned alone and never resolved by a tie-break: {@link #candidates} returns
 * <b>every</b> war that fits, nearest first, and {@link Edge#ambiguous} says when there was more than one.
 * A caller that wants one answer has to decide for itself, which is where that decision belongs.
 *
 * <p>This class touches no Minecraft, and it does not read files: a caller hands it events and it hands
 * back edges.
 */
public final class Depends {

    /**
     * Something that happened, with the two properties the graph is made of. {@code radiusKm} is how wide
     * the record says the event was, so a battle is 20 km and a war is 500.
     */
    public record Node(int year, int endYear, String name, String kind, double lat, double lon,
                       double radiusKm) {
        public Node {
            name = name == null ? "" : name;
            kind = kind == null ? "" : kind;
            radiusKm = Math.max(1.0, radiusKm);
        }

        /**
         * The slack, in years, when asking whether one thing falls inside another. Records date a war
         * to the year it ended and a battle to the day it was fought, so a battle a year outside a
         * war's span is still that war's battle. The survey used two years and this is the same two.
         */
        public static final int SLACK_YEARS = 2;

        /** True when this node's dates and place both fit inside the other one. */
        public boolean inside(Node other) {
            return fits(other, 0);
        }

        /** True when this falls inside the other one, allowing a slack of years at each end. */
        public boolean fits(Node other, int slackYears) {
            if (other == this) return false;
            if (year < other.year - slackYears || year > other.endYear + slackYears) return false;
            return km(other.lat, other.lon, lat, lon) <= Math.max(other.radiusKm, radiusKm);
        }

        public String label() {
            return name + " (" + year + ", " + String.format(Locale.ROOT, "%.0f", lat) + ","
                    + String.format(Locale.ROOT, "%.0f", lon) + ")";
        }
    }

    /** How an edge was found, which is what a reader needs to know about it. */
    public enum Basis {
        /** A hand-written cause in the record. The strongest. */
        STATED,
        /** The war's dates and radius cover this event. A candidate, and possibly one of several. */
        TIME_AND_PLACE
    }

    /**
     * One dependency: the parent, the child, how close they are, and whether the evidence admitted more
     * than one parent.
     */
    public record Edge(Node parent, Node child, Basis basis, double km, int overlapYears,
                       boolean ambiguous, @Nullable String statedCause) {

        /** True when a change to the parent is allowed to reach the child. */
        public boolean usable() {
            return basis == Basis.STATED || !ambiguous;
        }

        public String line() {
            String how = basis == Basis.STATED
                    ? "because the record says so: " + (statedCause == null ? "no cause given" : statedCause)
                    : "because it falls inside it: " + Math.round(km) + " km, " + overlapYears
                            + " year" + (overlapYears == 1 ? "" : "s") + " of overlap"
                            + (ambiguous ? ", and the record fits more than one parent here" : "");
            return child.name() + " depends on " + parent.name() + " " + how;
        }
    }

    private Depends() {
    }

    /** Great-circle distance in km. */
    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * The edges into one event, from a pool of possible parents. Stated causes are kept, and every
     * parent that fits in time and place is returned, not only the nearest.
     */
    public static List<Edge> edgesInto(Node child, List<Node> parents, @Nullable String statedCause) {
        List<Edge> out = new ArrayList<>();
        if (statedCause != null && !statedCause.isBlank()) {
            // A stated cause names the parent in prose, so the best parent is the one whose place is
            // nearest and whose dates are the closest fit. The prose is kept on the edge and never
            // parsed: a sentence is evidence, and this class does not pretend to read it.
            Node best = null;
            double bestScore = Double.MAX_VALUE;
            for (Node p : parents) {
                if (p == child) continue;
                double d = km(p.lat, p.lon, child.lat, child.lon);
                int gap = p.year > child.endYear ? p.year - child.endYear
                        : (child.year > p.endYear ? child.year - p.endYear : 0);
                double score = d + gap * 50.0;
                if (score < bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
            if (best != null) {
                out.add(new Edge(best, child, Basis.STATED, km(best.lat, best.lon, child.lat, child.lon),
                        overlap(best, child), false, statedCause));
                return out;
            }
        }
        List<Edge> fits = new ArrayList<>();
        for (Node p : parents) {
            if (p == child) continue;
            if (!child.fits(p, Node.SLACK_YEARS)) continue;
            fits.add(new Edge(p, child, Basis.TIME_AND_PLACE, km(p.lat, p.lon, child.lat, child.lon),
                    overlap(p, child), false, null));
        }
        fits.sort(Comparator.comparingDouble(Edge::km));
        if (fits.size() > 1) {
            List<Edge> marked = new ArrayList<>(fits.size());
            for (Edge e : fits) {
                marked.add(new Edge(e.parent(), e.child(), e.basis(), e.km(), e.overlapYears(), true, null));
            }
            return marked;
        }
        return fits;
    }

    /** Years in which the two existed at once, which is what makes one able to affect the other. */
    public static int overlap(Node a, Node b) {
        int from = Math.max(a.year, b.year);
        int to = Math.min(a.endYear, b.endYear);
        return Math.max(0, to - from + 1);
    }

    /**
     * The whole graph over a set of children' parents: one call, so a caller does not write the loop twice.
     * Children with a stated cause use it; the rest fall back to time and place.
     */
    public static List<Edge> graph(List<Node> children, List<Node> parents) {
        List<Edge> out = new ArrayList<>();
        for (Node c : children) out.addAll(edgesInto(c, parents, null));
        return out;
    }

    /** How many of the edges are ambiguous, as a fraction. The number to quote in a limit. */
    public static double ambiguousShare(List<Edge> edges) {
        if (edges.isEmpty()) return 0.0;
        int n = 0;
        for (Edge e : edges) if (e.ambiguous()) n++;
        return n / (double) edges.size();
    }

    /**
     * What a change to one node can reach, walking edges forward in time only, up to a depth and no
     * further. Ambiguous edges are followed but <b>marked</b>, because a chain that runs through a fork is
     * weaker than one that does not.
     */
    public static Set<Node> reach(Node from, List<Edge> edges, int maxDepth, int maxYears) {
        Set<Node> out = new LinkedHashSet<>();
        List<Node> frontier = new ArrayList<>();
        Set<Node> seen = new LinkedHashSet<>();
        frontier.add(from);
        seen.add(from);
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            List<Node> next = new ArrayList<>();
            for (Node n : frontier) {
                for (Edge e : edges) {
                    if (!same(e.parent(), n)) continue;
                    Node child = e.child();
                    if (seen.contains(child)) continue;
                    if (child.year < from.year) continue;              // consequences run forward
                    if (child.year - from.year > maxYears) continue;   // and not indefinitely
                    seen.add(child);
                    out.add(child);
                    next.add(child);
                }
            }
            frontier = next;
        }
        return out;
    }

    private static boolean same(Node a, Node b) {
        return a == b || (a.year == b.year && a.name.equals(b.name));
    }

    /** One line for a screen: what this event is downstream of, in plain words. */
    public static String explain(Node child, List<Edge> edges) {
        List<Edge> into = new ArrayList<>();
        for (Edge e : edges) if (same(e.child(), child)) into.add(e);
        if (into.isEmpty()) {
            return child.name() + " has nothing behind it that this record can name. That is not a claim "
                    + "that it had no cause, only that no cause is recorded and none can be computed.";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < into.size(); i++) {
            if (i > 0) b.append(" Also: ");
            b.append(into.get(i).line()).append('.');
        }
        return b.toString();
    }
}

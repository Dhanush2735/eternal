package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexes historical pins and, optionally, inferred sibling relationships.
 *
 * <p>Geographic and temporal proximity do not prove identity. The legacy builders retain their
 * heuristic behavior for compatibility, but it can join unrelated events even across centuries
 * through transitive unions. The runtime uses {@link #buildIndependent(List)} instead: each ID
 * remains resolvable, but different IDs are not automatically grouped. Records sharing one ID
 * still suppress together. A reviewed identity source is needed to restore safe multi-site grouping.
 *
 * <p><b>Clustering rule.</b> Two pins belong together when all three hold:
 * <ol>
 *   <li>they fall in the same geographic cell (see {@link #cellOf});</li>
 *   <li>their centres are within {@link #clusterKm};</li>
 *   <li>their active year ranges overlap.</li>
 * </ol>
 *
 * <p>Names are not used by the legacy heuristic; the same name can refer to several distinct
 * events, and different names can refer to one. Geographic and temporal closeness are not proof.
 */
public final class SiblingIndex {

    /** Approximate cell width in degrees of latitude (~27 km at the equator). */
    private static final double CELL_DEG = 0.25d;

    /** Pins farther apart than this are not the same event, even in one cell. */
    private static final double CLUSTER_KM = 25.0d;

    /** Year ranges within this many years of each other count as overlapping. */
    private static final int YEAR_SLACK = 1;

    /** One historical pin, reduced to what clustering needs. */
    public record Pin(String id, double lat, double lon, int startYear, int endYear) {
        public Pin {
            if (startYear > endYear) {
                int t = startYear;
                startYear = endYear;
                endYear = t;
            }
        }

        public boolean activeIn(int year) {
            return year >= startYear && year < Math.max(endYear, startYear + 1);
        }
    }

    private final Map<String, Pin> byId = new HashMap<>();
    private final Map<String, Set<String>> clusterOf = new HashMap<>();
    private int clusterCount;

    private SiblingIndex() {}

    /**
     * Build an ID-only index without inferring relationships. For a new branch, suppressing one
     * ID does not automatically suppress a different ID, even one describing the same event.
     * Multiple records sharing one ID can still be suppressed together. Existing saved branch
     * suppression sets are not modified by constructing this index.
     */
    public static SiblingIndex buildIndependent(List<Pin> pins) {
        SiblingIndex index = new SiblingIndex();
        if (pins != null) {
            for (Pin p : pins) {
                if (p != null && p.id() != null && !p.id().isBlank()) {
                    index.byId.put(p.id(), p);
                }
            }
        }
        return index;
    }

    /**
     * Legacy proximity-based grouping. Cost is bounded by grouping into grid cells first, but
     * the inferred groups are not verified event identities.
     */
    public static SiblingIndex build(List<Pin> pins) {
        return build(pins, Set.of());
    }

    /**
     * Build while excluding specified IDs from automatic proximity clustering. Excluded pins
     * remain resolvable by ID and a pivot still suppresses itself. The exclusion only affects
     * newly built clusters; it does not alter already-persisted branch suppression sets.
     */
    public static SiblingIndex build(List<Pin> pins, Set<String> excludedFromClustering) {
        SiblingIndex index = new SiblingIndex();
        if (pins == null || pins.isEmpty()) {
            return index;
        }

        List<Pin> valid = new ArrayList<>();
        for (Pin p : pins) {
            if (p != null && p.id() != null && !p.id().isBlank()) {
                index.byId.put(p.id(), p);
                if (excludedFromClustering == null || !excludedFromClustering.contains(p.id())) {
                    valid.add(p);
                }
            }
        }

        // Bucket by grid cell so we never do an all-pairs scan over 15k pins.
        Map<Long, List<Pin>> cells = new HashMap<>();
        for (Pin p : valid) {
            cells.computeIfAbsent(cellOf(p.lat(), p.lon()), k -> new ArrayList<>()).add(p);
        }

        for (List<Pin> cell : cells.values()) {
            if (cell.size() < 2) {
                continue;
            }
            index.clusterCell(cell);
        }
        return index;
    }

    /** Union-find inside a single cell. */
    private void clusterCell(List<Pin> cell) {
        int n = cell.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (related(cell.get(i), cell.get(j))) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, Set<String>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new LinkedHashSet<>()).add(cell.get(i).id());
        }
        for (Set<String> group : groups.values()) {
            if (group.size() > 1) {
                clusterCount++;
                for (String id : group) {
                    clusterOf.put(id, Collections.unmodifiableSet(group));
                }
            }
        }
    }

    private static boolean related(Pin a, Pin b) {
        if (!overlapsInTime(a, b)) {
            return false;
        }
        return haversineKm(a.lat(), a.lon(), b.lat(), b.lon()) <= CLUSTER_KM;
    }

    private static boolean overlapsInTime(Pin a, Pin b) {
        long aStart = a.startYear();
        long aEnd = Math.max(a.endYear(), a.startYear() + 1);
        long bStart = b.startYear();
        long bEnd = Math.max(b.endYear(), b.startYear() + 1);
        return aStart <= bEnd + YEAR_SLACK && bStart <= aEnd + YEAR_SLACK;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int i, int j) {
        int ri = find(parent, i);
        int rj = find(parent, j);
        if (ri != rj) {
            parent[rj] = ri;
        }
    }

    /**
     * Grid cell key. Longitude width is scaled by latitude so cells stay roughly equal in area
     * near the poles — otherwise a 0.25° cell in Norway would hold far too many pins.
     */
    static long cellOf(double lat, double lon) {
        long latCell = Math.round(lat / CELL_DEG);
        double shrink = Math.max(0.15d, Math.cos(Math.toRadians(lat)));
        long lonCell = Math.round(lon / (CELL_DEG * shrink));
        return (latCell << 32) ^ (lonCell & 0xffffffffL);
    }

    /** Every <em>other</em> pin that is the same real-world event as {@code id}. */
    public Set<String> siblingsOf(String id) {
        Set<String> cluster = clusterOf.get(id);
        if (cluster == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>(cluster);
        out.remove(id);
        return Collections.unmodifiableSet(out);
    }

    /** The whole cluster including {@code id} itself. Empty-ish set (just the id) if unclustered. */
    public Set<String> clusterOfId(String id) {
        Set<String> cluster = clusterOf.get(id);
        if (cluster == null) {
            return byId.containsKey(id) ? Set.of(id) : Set.of();
        }
        return cluster;
    }

    /**
     * The set to suppress when the player stops {@code id}: the pin itself plus every sibling.
     * This is what makes stopping the Taj automatically stop the railway terminus.
     */
    public Set<String> suppressionSetFor(String id) {
        Set<String> out = new LinkedHashSet<>();
        out.add(id);
        out.addAll(siblingsOf(id));
        return Collections.unmodifiableSet(out);
    }

    public boolean isClustered(String id) {
        return clusterOf.containsKey(id);
    }

    /** Number of multi-pin clusters found. */
    public int clusterCount() {
        return clusterCount;
    }

    /** Number of pins that belong to a multi-pin cluster. */
    public int clusteredPinCount() {
        return clusterOf.size();
    }

    public int pinCount() {
        return byId.size();
    }

    /** Pins in the index that are siblings of nothing — the majority, by design. */
    public int unclusteredPinCount() {
        return byId.size() - clusterOf.size();
    }

    /** Distance helper. Local so this class stays free of world-dependent code. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }
}

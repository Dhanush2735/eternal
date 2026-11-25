package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ruler register: who ruled a place in a year.
 *
 * <h3>What it reads</h3>
 * <b>Two files, one index.</b> {@code world_rulers.json.gz} is the same Cliopatria v0.2.1 record
 * and the same smallest-polity rule for the whole world, on 0.5-degree cell centres (62,647
 * places, 8,451 timelines, compact "format 2"; built by {@code worldfill/build_rulers.py}).
 * Cells that hold a Deccan place are left out of it, so the Deccan keeps its own record below.
 * Both load into one index and the nearest recorded place still wins; a world place's id starts
 * with {@link #GRID_PREFIX}.
 *
 * <p>{@code deccan_rulers.json.gz} - the two-level record built from Cliopatria v0.2.1 (CC-BY-4.0):
 * <b>274 distinct ruler sequences shared by 15,741 places</b>, each place carrying its latitude,
 * longitude and the id of its sequence. The flat engine-shaped form of the same record is
 * <b>306,669 rows / 37.6 MB</b>, because a Deccan place changed hands 18-29 times; this class was
 * written to load the two-level form instead and resolve per place on demand. See
 * {@code verification/population/alternatives/history_deep/RULERS_BUILD.md}.
 *
 * <h3>The rule it inherits</h3>
 * The smallest plain polity by polygon area covering the place that year: a sultanate beats the
 * empire it sat inside. Parenthesised entries (alliances, allegiances, colonial claims) were kept
 * as facts, never as rulers - so nothing here reports a claim as a government. A year no polity
 * covers returns {@link Optional#empty()} on purpose: the gap is the answer.
 *
 * <h3>How a query is answered</h3>
 * Places are bucketed into 0.5-degree grid cells at load; a query walks the cells around the point
 * and keeps the nearest place, then scans that place's sequence (18-29 spans) for the year. A
 * point more than {@link #MAX_KM} from any recorded place gets nothing, so the Atlantic does not
 * inherit a Deccan ruler.
 */
public final class RulerRegister {

    /** Package resource, beside the other history packs. */
    public static final String RESOURCE = "/data/eternalengine/eternal/history/deccan_rulers.json.gz";

    /** The world record: same source and rule, 0.5-degree cell centres, gzipped compact form. */
    public static final String WORLD_RESOURCE = "/data/eternalengine/eternal/history/world_rulers.json.gz";

    /** Place ids of the world record start with this; Deccan place ids never do. */
    public static final String GRID_PREFIX = "cell_";

    /** Beyond this distance from the nearest recorded place, the register declines to answer. */
    public static final double MAX_KM = 60.0d;

    private static final double CELL = 0.5d;
    private static final double KM_PER_DEG_LAT = 110.57d;

    /** One ruler span, as the build wrote it. */
    public record Span(String name, int start, int end, String wikidata) {}

    /**
     * Spans the source gets wrong, and the year a named source says they really end.
     *
     * <p>The register records a place's rulers as a chain of slices. Where a slice runs past the end of the
     * polity, the engine keeps answering with a state that no longer existed, and it looks like a ranking
     * bug when it is a data bug. Found by {@code SpanHonestyProbe}: an interval where the polity is never
     * the answer in its own core, for all of its length.
     *
     * <p>Nothing here deletes a slice of the <b>source</b>: the file on disk is untouched and still matches
     * its checksum. The correction lives here, named, dated and cited, so it can be argued with.
     */
    private static final Map<String, int[]> SPAN_WRONG = buildSpanWrong();

    private static Map<String, int[]> buildSpanWrong() {
        Map<String, int[]> m = new java.util.HashMap<>();
            // name -> {earliest start it may have, latest end it may have}; 0 means "no constraint".
            m.put("Khmer Empire", new int[] {0, 1431});
            m.put("Golden Horde", new int[] {0, 1502});
            m.put("Ghaznavid Empire", new int[] {0, 1186});
            // Added 2026-10-27 from AskProbe: Istanbul in 1460 answered "Byzantine Empire", twenty-one
            // years after the fall of Constantinople. en.wikipedia.org/wiki/Byzantine_Empire: "330-1453",
            // "it endured until the fall of Constantinople to the Ottoman Empire in 1453".
            m.put("Byzantine Empire", new int[] {0, 1453});
            // Palermo 1092 answered "Norman Italy" over a recorded "Emirate of Sicily" ending 1093; the
            // Norman conquest of the island was complete in 1091.
            m.put("Emirate of Sicily", new int[] {0, 1091});
            // From TailProbe's core-cell list, then read in the source. en.wikipedia.org/wiki/Kingdom_of_Arles:
            // "a realm established in 933 by the unification of Lower Burgundy with the Upper Burgundy", and
            // Upper Burgundy is listed among Arles' predecessors - so Upper Burgundy does not outlive 933.
            m.put("Upper Burgundy", new int[] {0, 933});
            // The four below came from the Wikipedia-wide cross-check of the tail list
            // (testrun/verify/wiki/TAILS_CROSSCHECK.md), infobox field read by hand, not by regex.
            // Huns: infobox "370s ... 469", event_end "Dengizich, son of Attila, dies". The register
            // records a Hunnic state to 673, two centuries after any Hunnic polity.
            m.put("Hunnic Empire", new int[] {0, 469});
            // New Netherland: life_span "1614-1667, 1673-1674". The register runs to 1802.
            m.put("New Netherland", new int[] {0, 1674});
            // Taifa of Almeria: infobox 1010-1147, "annexed by Kingdom of Castile". Register 1015-1249.
            m.put("Taifa of Almería", new int[] {0, 1147});
            // Taifa of Cordoba: infobox 1031-1091. The register's only spans are 1147-1249, which is a
            // century after the taifa ended, so every span is dropped and Cordoba resolves to whoever
            // really held it then (the Almohads). A false ruler is worse than no ruler from this polity.
            m.put("Taifa of Córdoba", new int[] {0, 1091});
            // en.wikipedia.org/wiki/Despotate_of_Epirus: "1205-1337, 1356-1479", last despot 1448-1479,
            // Ottoman conquest of Vonitsa 1479. The register runs two years past the end AND starts its
            // second period in 1385 rather than 1356. Both bounds are corrected here; note the classifier
            // proposed 1442, which is when Ioannina and Arta fell, not when the despotate did.
            m.put("Despotate of Epirus", new int[] {1356, 1479});
            // WITHDRAWN 2026-10-27, the same hour it was written - a clamp on a NAMING problem, which is
            // the trap this table must not fall into. The plan was to push "Kingdom of Naples (Napoleonic)"
            // forward to 1806, because the source dates it from 1285. Reading the data first showed the
            // real shape: the source holds "Kingdom of Naples" 1285-1449 and 1459-1501, and "Kingdom of
            // Naples (Napoleonic)" 1762-1798 and 1762-1814. Those 1762 spans are the BOURBON kingdom, and
            // clamping them to 1806 would have deleted seventy years of real Neapolitan coverage to fix a
            // label. The span is not wrong; the name is. Both names fold to one polity id (civHintFor
            // drops parentheticals), so this is the duplicate-name class, not a date class.
        return Map.copyOf(m);
    }

    /** The latest end year this polity really has, if the source is known to overrun it. */
    public static int correctedEnd(String polityName, int recordedEnd) {
        int[] fix = SPAN_WRONG.get(polityName);
        if (fix == null || fix[1] == 0 || recordedEnd <= fix[1]) {
            return recordedEnd;
        }
        return fix[1];
    }

    /** The earliest start year this polity really has, if the source records it too early. */
    public static int correctedStart(String polityName, int recordedStart) {
        int[] fix = SPAN_WRONG.get(polityName);
        if (fix == null || fix[0] == 0 || recordedStart >= fix[0]) {
            return recordedStart;
        }
        return fix[0];
    }

    /** A polity the register can name: its source spelling and the id the index uses. */
    public record Polity(String civHint, String name) {}

    /**
     * Where and when a polity appears in the register: first and last year of its spans
     * (inclusive), the mean position of the places it ruled, and how many places that is.
     */
    public record Extent(String name, int start, int end, double lat, double lon, int places,
                         int coreYear, int longestSpan) {
        /** Kept so callers that only need the box still compile unchanged. */
        public Extent(String name, int start, int end, double lat, double lon, int places) {
            this(name, start, end, lat, lon, places, (start + end) / 2, end - start);
        }

        /**
         * A year the polity certainly existed: the midpoint of its <b>longest single span</b>.
         *
         * <p>{@link #start} and {@link #end} are the union of every span the polity has anywhere, so for a
         * polity that came and went - the Taifa of Niebla, 1023-1053, 1145-1150 and 1234-1262 - the union
         * midpoint 1132 falls in a gap when the taifa did not exist. Asking "who ruled here in 1142" and
         * getting the Almoravids was the right answer to a question nobody asked. Measure here instead.
         */
        public int core() { return coreYear; }
    }

    /** One recorded ruler for one place in one year. */
    public record Hit(String place, double distanceKm, String ruler, int start, int end,
                      String civHint, String wikidata, boolean estimated) {
        /** The usual case: a recorded span, not a stretch carried in from a neighbour. */
        public Hit(String place, double distanceKm, String ruler, int start, int end,
                   String civHint, String wikidata) {
            this(place, distanceKm, ruler, start, end, civHint, wikidata, false);
        }
    }

    /** What the register holds, for the display line and for checks. */
    public record Summary(int places, int timelines, int spans, boolean loaded, String source) {
        public String display() {
            return loaded
                    ? "ruler register: " + places + " places, " + timelines + " timelines, "
                      + spans + " spans from " + source
                    : "ruler register: not loaded (" + source + ")";
        }
    }

    private static final RulerRegister INSTANCE = new RulerRegister();
    private static final RulerRegister EMPTY = new RulerRegister();

    public static RulerRegister get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** A register loaded from explicit files - for checks and tools, never for the game. */
    public static RulerRegister fromFile(Path... files) {
        RulerRegister r = new RulerRegister();
        r.loaded = true;
        List<Part> parts = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        for (Path file : files) {
            try (InputStream in = Files.newInputStream(file)) {
                Part part = readPart(in, file.getFileName().toString());
                if (part.problem() == null) {
                    parts.add(part);
                    sources.add(file.toString());
                } else {
                    sources.add(part.problem());
                }
            } catch (Exception e) {
                sources.add(file + " failed: " + e.getMessage());
            }
        }
        r.install(parts);
        r.source = String.join(" + ", sources);
        return r;
    }

    private boolean loaded;
    private String source = "missing";

    private List<List<Span>> timelines = List.of();
    private double[] lat = new double[0];
    private double[] lon = new double[0];
    private int[] timelineOf = new int[0];
    private String[] placeIds = new String[0];
    private Map<Long, int[]> grid = Map.of();
    /** The last year any span reaches (2026 since step 25); later years are answered as that year. */
    private int lastYear = Integer.MAX_VALUE;

    /** Polled on demand: each polity's box, its merged intervals, and its cells weighted by years. */
    private Map<String, Extent> extents;
    private Map<String, List<int[]>> spanIndex;
    private Map<String, Map<String, double[]>> placesIndex;

    private RulerRegister() {}

    /** The last year the record covers; a later year is answered as this one (the states of today go on). */
    public int lastYear() {
        return lastYear;
    }

    /** One file's worth of places and timelines, before the files are merged into one index. */
    private record Part(List<List<Span>> timelines, double[] lat, double[] lon, int[] timelineOf,
                        String[] placeIds, String problem, int recordedUntil, Map<Integer, int[]> marks) {
        Part(List<List<Span>> timelines, double[] lat, double[] lon, int[] timelineOf, String[] placeIds,
             String problem) {
            this(timelines, lat, lon, timelineOf, placeIds, problem, Integer.MAX_VALUE, Map.of());
        }

        static Part failed(String problem) {
            return new Part(List.of(), new double[0], new double[0], new int[0], new String[0], problem);
        }

        /** The same part with the step-26 marks of its file (recordedUntil, estimated stretches). */
        Part marked(JsonObject root) {
            int until = root.has("recordedUntil") ? root.get("recordedUntil").getAsInt() : Integer.MAX_VALUE;
            Map<Integer, List<int[]>> by = new java.util.HashMap<>();
            if (root.has("estimated") && root.get("estimated").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("estimated")) {
                    JsonArray a = e.getAsJsonArray();
                    by.computeIfAbsent(a.get(0).getAsInt(), k -> new ArrayList<>())
                            .add(new int[] {a.get(1).getAsInt(), a.get(2).getAsInt()});
                }
            }
            Map<Integer, int[]> flat = new java.util.HashMap<>();
            by.forEach((t, list) -> {
                int[] f = new int[list.size() * 2];
                for (int i = 0; i < list.size(); i++) {
                    f[2 * i] = list.get(i)[0];
                    f[2 * i + 1] = list.get(i)[1];
                }
                flat.put(t, f);
            });
            return new Part(timelines, lat, lon, timelineOf, placeIds, problem, until, Map.copyOf(flat));
        }
    }

    /**
     * Step 26 (A5), the recorded/estimated mark: per timeline, the last year its file records (later years carry
     * that year's states forward) and the stretches a coastal square took from its neighbours.
     */
    private int[] recordedUntilOf = new int[0];
    private Map<Integer, int[]> estimatedStretches = Map.of();
    private int recordedUntil = Integer.MAX_VALUE;

    /** The last year the source of the record covers (Cliopatria v0.2.1: 2024); later answers are estimates. */
    public int recordedUntil() {
        return recordedUntil;
    }

    /** How many stretches of years are marked estimated (taken from neighbouring squares). */
    public int estimatedStretchCount() {
        return estimatedStretches.values().stream().mapToInt(a -> a.length / 2).sum();
    }

    /** True when the answer for this timeline and year is an estimate rather than the record. */
    private boolean estimatedAt(int timeline, int askedYear) {
        if (timeline < recordedUntilOf.length && askedYear > recordedUntilOf[timeline]) {
            return true;
        }
        int[] f = estimatedStretches.get(timeline);
        if (f != null) {
            for (int i = 0; i + 1 < f.length; i += 2) {
                if (askedYear >= f[i] && askedYear <= f[i + 1]) {
                    return true;
                }
            }
        }
        return false;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        List<Part> parts = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        for (String resource : new String[] {RESOURCE, WORLD_RESOURCE}) {
            try (InputStream in = RulerRegister.class.getResourceAsStream(resource)) {
                if (in == null) {
                    sources.add("resource " + resource + " absent");
                    continue;
                }
                Part part = readPart(in, resource);
                if (part.problem() == null) {
                    parts.add(part);
                    sources.add(resource);
                } else {
                    sources.add(part.problem());
                }
            } catch (Exception e) {
                sources.add("resource " + resource + " failed: " + e.getMessage());
            }
        }
        install(parts);
        source = String.join(" + ", sources);
    }

    private static Part readPart(InputStream raw, String where) throws java.io.IOException {
        InputStream in = where.endsWith(".gz") ? new GZIPInputStream(raw, 1 << 16) : raw;
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("format") && root.get("format").getAsInt() == 2) {
                return parseCompact(root, where).marked(root);
            }
            return parse(root, where).marked(root);
        }
    }

    /**
     * Runs of one polity with no other polity between them, as one span.
     *
     * <p>The source records a place's history as a chain of slices, and the same polity comes back
     * in several slices (Hyderabad State 1744-1751, then again 1752-1759, ... 1947-1947, 1948-1948).
     * Asked for the year of a rule, {@code Calendars.regnal} counts from the span's start, so every
     * slice restarted the count: a paper in Hyderabad in 1948 was dated "the first year of
     * Hyderabad State rule" when the state had ruled there for 224 years. 85 of the 5,415
     * same-polity year-steps in a twelve-city sample restarted this way. Merging touching slices
     * of the same polity is the fix; a different polity between them still ends a rule.
     */
    static List<Span> merge(List<Span> spans) {
        List<Span> out = new ArrayList<>(spans.size());
        for (Span sp : spans) {
            if (!out.isEmpty()) {
                Span prev = out.get(out.size() - 1);
                boolean same = prev.name().equals(sp.name())
                        && java.util.Objects.equals(prev.wikidata(), sp.wikidata())
                        && sp.start() <= prev.end() + 1;
                if (same) {
                    out.set(out.size() - 1, new Span(prev.name(), Math.min(prev.start(), sp.start()),
                            Math.max(prev.end(), sp.end()), prev.wikidata()));
                    continue;
                }
            }
            out.add(sp);
        }
        return out;
    }

    private static Part parse(JsonObject root, String where) {
        JsonArray timelineArray = root.getAsJsonArray("timelines");
        JsonArray placeArray = root.getAsJsonArray("places");
        if (timelineArray == null || placeArray == null) {
            return Part.failed(where + " is not a ruler register (no timelines/places)");
        }
        List<List<Span>> seqs = new ArrayList<>(timelineArray.size());
        for (JsonElement t : timelineArray) {
            JsonArray spans = t.getAsJsonArray();
            List<Span> out = new ArrayList<>(spans.size());
            for (JsonElement s : spans) {
                JsonObject o = s.getAsJsonObject();
                out.add(new Span(o.get("name").getAsString(), o.get("start").getAsInt(),
                        o.get("end").getAsInt(),
                        o.has("wikidata") && !o.get("wikidata").isJsonNull()
                                ? o.get("wikidata").getAsString() : null));
            }
            seqs.add(List.copyOf(clamp(merge(out))));
        }

        int n = placeArray.size();
        double[] la = new double[n];
        double[] lo = new double[n];
        int[] tl = new int[n];
        String[] ids = new String[n];
        for (int i = 0; i < n; i++) {
            JsonObject p = placeArray.get(i).getAsJsonObject();
            la[i] = p.get("lat").getAsDouble();
            lo[i] = p.get("lon").getAsDouble();
            tl[i] = p.get("timeline").getAsInt();
            ids[i] = p.get("place").getAsString();
        }
        return new Part(List.copyOf(seqs), la, lo, tl, ids, null);
    }

    private static Part parseCompact(JsonObject root, String where) {
        JsonArray pol = root.getAsJsonArray("polities");
        JsonArray tls = root.getAsJsonArray("timelines");
        JsonArray pls = root.getAsJsonArray("places");
        if (pol == null || tls == null || pls == null) {
            return Part.failed(where + " is not a format-2 ruler register (no polities/timelines/places)");
        }
        String[] names = new String[pol.size()];
        String[] qids = new String[pol.size()];
        for (int i = 0; i < names.length; i++) {
            JsonArray p = pol.get(i).getAsJsonArray();
            names[i] = p.get(0).getAsString();
            String q = p.size() > 1 && !p.get(1).isJsonNull() ? p.get(1).getAsString() : "";
            qids[i] = q.isBlank() ? null : q;
        }
        List<List<Span>> seqs = new ArrayList<>(tls.size());
        for (JsonElement t : tls) {
            JsonArray flat = t.getAsJsonArray();
            List<Span> out = new ArrayList<>(flat.size() / 3);
            for (int k = 0; k + 2 < flat.size(); k += 3) {
                int p = flat.get(k).getAsInt();
                out.add(new Span(names[p], flat.get(k + 1).getAsInt(), flat.get(k + 2).getAsInt(), qids[p]));
            }
            seqs.add(List.copyOf(clamp(merge(out))));
        }
        int n = pls.size() / 3;
        double[] la = new double[n];
        double[] lo = new double[n];
        int[] tl = new int[n];
        String[] ids = new String[n];
        for (int i = 0; i < n; i++) {
            la[i] = pls.get(3 * i).getAsInt() / 100.0d;
            lo[i] = pls.get(3 * i + 1).getAsInt() / 100.0d;
            tl[i] = pls.get(3 * i + 2).getAsInt();
            ids[i] = GRID_PREFIX + String.format(java.util.Locale.ROOT, "%.2f_%.2f", la[i], lo[i]);
        }
        return new Part(List.copyOf(seqs), la, lo, tl, ids, null);
    }

    /** Merge the parts into one index: timelines renumbered, places bucketed into the grid. */
    private void install(List<Part> parts) {
        List<List<Span>> seqs = new ArrayList<>();
        int n = 0;
        for (Part p : parts) {
            n += p.lat().length;
            seqs.addAll(p.timelines());
        }
        double[] la = new double[n];
        double[] lo = new double[n];
        int[] tl = new int[n];
        String[] ids = new String[n];
        int at = 0;
        int offset = 0;
        java.util.List<int[]> untils = new ArrayList<>();
        Map<Integer, int[]> marks = new java.util.HashMap<>();
        int until = Integer.MAX_VALUE;
        for (Part p : parts) {
            for (int i = 0; i < p.lat().length; i++) {
                la[at] = p.lat()[i];
                lo[at] = p.lon()[i];
                tl[at] = p.timelineOf()[i] + offset;
                ids[at] = p.placeIds()[i];
                at++;
            }
            for (int t = 0; t < p.timelines().size(); t++) {
                untils.add(new int[] {p.recordedUntil()});
                int[] m = p.marks().get(t);
                if (m != null) {
                    marks.put(t + offset, m);
                }
            }
            offset += p.timelines().size();
            until = Math.min(until, p.recordedUntil());
        }
        recordedUntilOf = new int[untils.size()];
        for (int i = 0; i < recordedUntilOf.length; i++) {
            recordedUntilOf[i] = untils.get(i)[0];
        }
        estimatedStretches = Map.copyOf(marks);
        recordedUntil = until;

        // Places bucketed by 0.5-degree cell, so rulerAt can look at two rings of cells instead of
        // scanning all 78,495 places.
        Map<Long, List<Integer>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            buckets.computeIfAbsent(cellKey(la[i], lo[i]), k -> new ArrayList<>()).add(i);
        }
        Map<Long, int[]> packed = new java.util.HashMap<>(buckets.size() * 2);
        buckets.forEach((k, v) -> {
            int[] a = new int[v.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = v.get(i);
            }
            packed.put(k, a);
        });

        int last = Integer.MIN_VALUE;
        for (List<Span> seq : seqs) {
            for (Span sp : seq) {
                if (sp.end() > last) {
                    last = sp.end();
                }
            }
        }
        extents = null;
        spanIndex = null;
        placesIndex = null;
        this.recordedUntil = recordedUntil;
        this.timelines = List.copyOf(seqs);
        this.lat = la;
        this.lon = lo;
        this.timelineOf = tl;
        this.placeIds = ids;
        this.grid = packed;
        this.lastYear = last == Integer.MIN_VALUE ? Integer.MAX_VALUE : last;
    }

    /**
     * Apply the corrections to one timeline. A slice that runs past the real end is cut, a slice that
     * starts before the real start is pushed forward, and a slice left empty by either is dropped.
     */
    static List<Span> clamp(List<Span> spans) {
        List<Span> out = new ArrayList<>(spans.size());
        for (Span sp : spans) {
            int start = correctedStart(sp.name(), sp.start());
            int end = correctedEnd(sp.name(), sp.end());
            if (start > end) {
                continue;
            }
            out.add(start == sp.start() && end == sp.end() ? sp
                    : new Span(sp.name(), start, end, sp.wikidata()));
        }
        return out;
    }

    /**
     * The years a polity actually held somewhere, as merged {start, end} intervals - the register's own
     * claim, un-collapsed. {@link #extentOf} flattens these to first-to-last, which loses every gap.
     */
    public synchronized List<int[]> spansOf(String civHint) {
        extentOf(civHint);                       // builds spanIndex too
        List<int[]> v = spanIndex.get(civHint);
        return v == null ? List.of() : List.copyOf(v);
    }

    /**
     * The cells a polity is recorded on, <b>spread</b> across its territory rather than clustered.
     *
     * <p>Element 0 is the cell that records this polity for the most years - the metropole, for a normal
     * state. For a colonial empire that can still be a possession, so callers must not read element 0 alone
     * as "the homeland". The rest are farthest-point samples, so a polity that holds its capital and loses
     * only a colony is not reported as losing everywhere.
     */
    public synchronized List<double[]> placesOf(String civHint, int max) {
        extentOf(civHint);
        Map<String, double[]> cells = placesIndex.get(civHint);
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        List<double[]> all = new ArrayList<>(cells.values());
        all.sort((x, y) -> Double.compare(y[2], x[2]));
        List<double[]> out = new ArrayList<>();
        out.add(new double[] {all.get(0)[0], all.get(0)[1]});
        while (out.size() < max && out.size() < all.size()) {
            double[] pick = null;
            double pickBest = -1;
            for (double[] c : all) {
                boolean chosen = false;
                for (double[] o : out) {
                    if (o[0] == c[0] && o[1] == c[1]) {
                        chosen = true;
                        break;
                    }
                }
                if (chosen) {
                    continue;
                }
                double nearest = Double.MAX_VALUE;
                for (double[] o : out) {
                    nearest = Math.min(nearest, distanceKm(o[0], o[1], c[0], c[1]));
                }
                if (nearest > pickBest) {
                    pickBest = nearest;
                    pick = c;
                }
            }
            if (pick == null) {
                break;
            }
            out.add(new double[] {pick[0], pick[1]});
        }
        return out;
    }

    /** The recorded extent of a polity id (as {@link #civHintFor} spells it), if the register has it. */
    public synchronized Optional<Extent> extentOf(String civHint) {
        if (extents == null) {
            // start, end, then sumLat, sumLon, count for world cells and again for Deccan places.
            // The position comes from the world cells when the polity has any: they sample the
            // land evenly, while 15,741 Deccan villages would drag every Indian empire's centre
            // to Telangana.
            Map<String, double[]> acc = new LinkedHashMap<>();
            Map<String, String> names = new LinkedHashMap<>();
            Map<String, List<int[]>> rawSpans = new LinkedHashMap<>();
            Map<String, Map<String, double[]>> rawPlaces = new LinkedHashMap<>();
            for (int i = 0; i < placeIds.length; i++) {
                java.util.Set<String> seen = new java.util.HashSet<>();
                int base = isGridPlace(placeIds[i]) ? 2 : 5;
                for (Span sp : timelines.get(timelineOf[i])) {
                    String id = civHintFor(sp.name());
                    double[] a = acc.computeIfAbsent(id,
                            k -> new double[] {Integer.MAX_VALUE, Integer.MIN_VALUE, 0, 0, 0, 0, 0, 0, 0, 0});
                    names.putIfAbsent(id, sp.name());
                    rawSpans.computeIfAbsent(id, k -> new ArrayList<>()).add(new int[] {sp.start(), sp.end()});
                    if (isGridPlace(placeIds[i])) {
                        // Keep, per cell, how many years of this polity the cell records. The cell with
                        // the most years is the polity's core: the metropole records a polity for
                        // centuries, a colony or an occupation for a few years.
                        Map<String, double[]> cells =
                                rawPlaces.computeIfAbsent(id, k -> new LinkedHashMap<>());
                        String ck = lat[i] + "," + lon[i];
                        double[] cell = cells.get(ck);
                        if (cell == null) {
                            cell = new double[] {lat[i], lon[i], 0};
                            cells.put(ck, cell);
                        }
                        cell[2] += Math.max(1, sp.end() - sp.start());
                    }
                    a[0] = Math.min(a[0], sp.start());
                    a[1] = Math.max(a[1], sp.end());
                    // a[8] = a year it certainly existed (midpoint of its longest single span);
                    // a[9] = that span's length. Indices 2-4 and 5-7 are already the coordinate sums.
                    int len = sp.end() - sp.start();
                    if (len > a[9]) {
                        a[9] = len;
                        a[8] = sp.start() + len / 2;
                    }
                    if (seen.add(id)) {
                        a[base] += lat[i];
                        a[base + 1] += lon[i];
                        a[base + 2] += 1;
                    }
                }
            }
            // Same pass, but keep the intervals: a polity that came and went must not be measured
            // in its gap. Merge overlaps, keep gaps.
            Map<String, List<int[]>> raw = new LinkedHashMap<>();
            for (Map.Entry<String, List<int[]>> e : rawSpans.entrySet()) {
                List<int[]> v = new ArrayList<>(e.getValue());
                v.sort((x, y) -> Integer.compare(x[0], y[0]));
                List<int[]> merged = new ArrayList<>();
                for (int[] iv : v) {
                    if (!merged.isEmpty() && iv[0] <= merged.get(merged.size() - 1)[1] + 1) {
                        int[] last = merged.get(merged.size() - 1);
                        last[1] = Math.max(last[1], iv[1]);
                    } else {
                        merged.add(new int[] {iv[0], iv[1]});
                    }
                }
                raw.put(e.getKey(), merged);
            }
            spanIndex = raw;
            placesIndex = rawPlaces;
            Map<String, Extent> out = new LinkedHashMap<>();
            for (Map.Entry<String, double[]> e : acc.entrySet()) {
                double[] a = e.getValue();
                int b = a[4] > 0 ? 2 : 5;
                out.put(e.getKey(), new Extent(names.get(e.getKey()), (int) a[0], (int) a[1],
                        a[b] / a[b + 2], a[b + 1] / a[b + 2], (int) (a[4] + a[7]),
                        (int) a[8], (int) a[9]));
            }
            extents = out;
        }
        return Optional.ofNullable(extents.get(civHint));
    }

    /** True for a place of the world record (a 0.5-degree cell centre), false for a Deccan place. */
    public static boolean isGridPlace(String placeId) {
        return placeId != null && placeId.startsWith(GRID_PREFIX);
    }

    private static long cellKey(double lat, double lon) {
        long cy = (long) Math.floor((lat + 90.0d) / CELL);
        long cx = (long) Math.floor((lon + 180.0d) / CELL);
        return cy * 10000L + cx;
    }

    /**
     * The Wikidata id the source gives for a polity id, or null when it gives none.
     *
     * <p>Added for the wiring (2026-10-24): the layered answers carry a polity id but not always a Wikidata
     * id, and callers such as {@code LawRegister} and {@code Justice} key entire systems on it.
     */
    public String wikidataOf(String civHint) {
        if (civHint == null) {
            return null;
        }
        for (List<Span> seq : timelines) {
            for (Span sp : seq) {
                if (sp.wikidata() != null && civHint.equals(civHintFor(sp.name()))) {
                    return sp.wikidata();
                }
            }
        }
        return null;
    }

    /**
     * The nearest place where <b>one named polity</b> held power in this year, or empty.
     *
     * <p>Added 2026-10-24 (R2-a5) after a source check of eight places: at Legnica - the capital of the
     * Duchy of Legnica - the register answered "Holy Roman Empire", although the Duchy is in the register
     * and held the town until 1675. {@link #rulerAt} answers with the polity its own search prefers, which is
     * sometimes the suzerain. This method asks a narrower question - "where, and how near, did <i>this</i>
     * polity govern?" - so a caller can prefer a vassal to its overlord with the register's own evidence
     * rather than with a guess.
     *
     * @param civHint the polity id as {@code civHintFor} spells it
     * @return the nearest covering place within {@link #MAX_KM}, its span, and the distance
     */
    public Optional<Hit> rulerNear(String civHint, double lat, double lon, int year) {
        if (civHint == null || timelines.isEmpty()) {
            return Optional.empty();
        }
        int best = -1;
        double bestKm = Double.MAX_VALUE;
        Span bestSpan = null;
        for (int i = 0; i < placeIds.length; i++) {
            double km = distanceKm(lat, lon, this.lat[i], this.lon[i]);
            if (km > MAX_KM || km >= bestKm) {
                continue;
            }
            for (Span sp : timelines.get(timelineOf[i])) {
                if (year >= sp.start() && year <= sp.end() && civHint.equals(civHintFor(sp.name()))) {
                    bestKm = km;
                    best = i;
                    bestSpan = sp;
                    break;
                }
            }
        }
        if (best < 0 || bestSpan == null) {
            return Optional.empty();
        }
        return Optional.of(new Hit(placeIds[best], bestKm, bestSpan.name(), bestSpan.start(),
                bestSpan.end(), civHintFor(bestSpan.name()), bestSpan.wikidata()));
    }

    /**
     * Who ruled this point in this year, or empty when the register has nobody there.
     *
     * <p>The nearest recorded place wins; ties between equidistant places are broken by the
     * place id, so the answer never depends on iteration order.
     */
    public Optional<Hit> rulerAt(double lat, double lon, int year) {
        if (timelines.isEmpty()) {
            return Optional.empty();
        }
        int asked = year;
        year = Math.min(year, lastYear);
        int best = -1;
        double bestKm = Double.MAX_VALUE;
        for (int ring = 0; ring <= 2; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (ring > 0 && Math.abs(dy) != ring && Math.abs(dx) != ring) {
                        continue;
                    }
                    long key = cellKey(lat + dy * CELL, lon + dx * CELL);
                    int[] in = grid.get(key);
                    if (in == null) {
                        continue;
                    }
                    for (int i : in) {
                        double km = distanceKm(lat, lon, this.lat[i], this.lon[i]);
                        if (km < bestKm || (km == bestKm && best >= 0
                                && placeIds[i].compareTo(placeIds[best]) < 0)) {
                            bestKm = km;
                            best = i;
                        }
                    }
                }
            }
            if (best >= 0 && bestKm <= ring * CELL * KM_PER_DEG_LAT) {
                break;
            }
        }
        if (best < 0 || bestKm > MAX_KM) {
            return Optional.empty();
        }
        Optional<Hit> hit = spanAt(best, bestKm, year, asked);
        if (hit.isPresent()) {
            return hit;
        }
        // The nearest place has no ruler this year. A point between grid cells (Rome lies exactly
        // between a Roman cell and an empty coastal one) then takes the nearest place that has one,
        // if that place is at most NEIGHBOUR_KM farther: a rasterising artefact, not open land.
        int alt = -1;
        double altKm = Double.MAX_VALUE;
        double limit = Math.min(MAX_KM, bestKm + NEIGHBOUR_KM);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int[] in = grid.get(cellKey(lat + dy * CELL, lon + dx * CELL));
                if (in == null) {
                    continue;
                }
                for (int i : in) {
                    if (i == best) {
                        continue;
                    }
                    double km = distanceKm(lat, lon, this.lat[i], this.lon[i]);
                    if (km > limit || km > altKm || (km == altKm && placeIds[i].compareTo(placeIds[alt]) > 0)) {
                        continue;
                    }
                    if (spanAt(i, km, year, asked).isPresent()) {
                        alt = i;
                        altKm = km;
                    }
                }
            }
        }
        return alt >= 0 ? spanAt(alt, altKm, year, asked) : Optional.empty();
    }

    /** How much farther than the nearest place a ruled neighbour may lie (see {@link #rulerAt}). */
    public static final double NEIGHBOUR_KM = 20.0d;

    private Optional<Hit> spanAt(int place, double km, int year, int askedYear) {
        int t = timelineOf[place];
        for (Span span : timelines.get(t)) {
            if (year >= span.start() && year <= span.end()) {
                return Optional.of(new Hit(placeIds[place], km, span.name(), span.start(),
                        span.end(), civHintFor(span.name()), span.wikidata(), estimatedAt(t, askedYear)));
            }
        }
        return Optional.empty();
    }

    /** Distance from a point to the nearest recorded place, whether or not a ruler covers it. */
    public Optional<Hit> placeSample(double lat, double lon) {
        if (timelines.isEmpty()) {
            return Optional.empty();
        }
        int best = -1;
        double bestKm = Double.MAX_VALUE;
        long key = cellKey(lat, lon);
        int[] in = grid.get(key);
        if (in != null) {
            for (int i : in) {
                double km = distanceKm(lat, lon, this.lat[i], this.lon[i]);
                if (km < bestKm) {
                    bestKm = km;
                    best = i;
                }
            }
        }
        if (best < 0) {
            return Optional.empty();
        }
        return Optional.of(new Hit(placeIds[best], bestKm, null, 0, 0, null, null));
    }

    /**
     * Polities named by the Deccan record only (places whose id is not a {@link #GRID_PREFIX} cell),
     * for {@link CivSynthesis}. The world record's ~1,500 polities are left out of the civilization
     * table on purpose: CivSynthesis registers every polity it is handed as a simulated
     * civilization, and the population, agriculture, economy, migration and technology engines all
     * iterate every registered civilization. An NPC standing under a world polity still carries its
     * id, and its name comes from VillagerNameDatabase.cultureFor, which needs no record.
     */
    public List<Polity> deccanPolities() {
        Map<String, String> byId = new LinkedHashMap<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < placeIds.length; i++) {
            if (isGridPlace(placeIds[i]) || !seen.add(timelineOf[i])) {
                continue;
            }
            for (Span s : timelines.get(timelineOf[i])) {
                byId.putIfAbsent(civHintFor(s.name()), s.name());
            }
        }
        List<Polity> out = new ArrayList<>(byId.size());
        byId.forEach((id, name) -> out.add(new Polity(id, name)));
        return List.copyOf(out);
    }

    /** Every polity the register can name, Deccan and world records together. */
    public List<Polity> polities() {
        Map<String, String> byId = new LinkedHashMap<>();
        for (List<Span> seq : timelines) {
            for (Span s : seq) {
                byId.putIfAbsent(civHintFor(s.name()), s.name());
            }
        }
        List<Polity> out = new ArrayList<>(byId.size());
        byId.forEach((id, name) -> out.add(new Polity(id, name)));
        return List.copyOf(out);
    }

    public Summary summary() {
        int spans = 0;
        for (List<Span> seq : timelines) {
            spans += seq.size();
        }
        return new Summary(placeIds.length, timelines.size(), spans, !timelines.isEmpty(), source);
    }

    /**
     * The polity id this engine keys on: the source name lowercased, accent-folded, parentheticals
     * dropped, everything else collapsed to underscores. "W. Chalukya (Kalyani)" becomes
     * {@code w_chalukya}. Never empty: an unnamed span keys on {@code unknown_polity}.
     */
    public static String civHintFor(String name) {
        if (name == null || name.isBlank()) {
            return "unknown_polity";
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT);
        int paren = s.indexOf('(');
        if (paren > 0) {
            s = s.substring(0, paren);
        }
        s = s.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "unknown_polity" : s;
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double midRad = Math.toRadians((lat1 + lat2) / 2.0d);
        double dy = (lat1 - lat2) * KM_PER_DEG_LAT;
        double dx = (lon1 - lon2) * KM_PER_DEG_LAT * Math.cos(midRad);
        return Math.sqrt(dx * dx + dy * dy);
    }
}

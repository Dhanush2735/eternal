package com.eternalengine.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * The dated events that can be told as news at a place, in a year.
 *
 * <h3>Why (2026-10-24, requirement 2, step R2-e1)</h3>
 * The card coverage test set measured the news card at <b>0 of 50 towns</b>, and the cause was not the data:
 * {@code LocalCards.build} never set {@code newsCard} at all, because in game the news arrives through the
 * pack and talk paths. That left the headless card builder - the thing a test can run - unable to produce the
 * one card that says what has just happened.
 *
 * <p>An index of all thirteen event files showed <b>129,232 events</b> carrying a place and a year, and
 * <b>34 of the 50 test towns had one inside its own radius within two years</b>. So the news is a wiring job.
 * This class is the wiring: it answers "what happened near here, about now?" from the shipped event files.
 *
 * <h3>What it will not do</h3>
 * It will not invent a date. Every event file is <b>year-granular</b> - there is no day, month or date field in
 * any of them (measured, board 31) - so the answer is dated by <b>year</b> and the text says "in this year's
 * news" rather than pretending to a day it does not have.
 */
public final class NewsIndex {

    /** Where the event files live, relative to the data root. */
    private static final String DIR = "/data/eternalengine/eternal/history/";

    /**
     * The files, all of them year-granular and all in the same shape: {@code start}, {@code end},
     * {@code lat}, {@code lon}, {@code radiusKm}, {@code name}.
     */
    private static final String[] FILES = {
            "events.json",
            "events_world.json.gz",
            "events_wikidata_more.json.gz",
            "events_wikipedia.json.gz",
            "events_wikipedia_decades.json.gz",
            "events_wikipedia_regional.json.gz",
            "events_wars.json.gz",
            "events_ucdp.json.gz",
            "events_hazards.json.gz",
            "events_nature.json.gz",
            "events_storms.json.gz",
            "disaster_supplement.json.gz",
            "deccan_events.json.gz",
    };

    /** How far outside an event's own radius a place may be and still hear of it. */
    public static final double HEAR_KM = 50.0d;

    /** How many years after an event ends it is still talked about. News never runs ahead of itself. */
    public static final int NEWS_YEARS = 2;

    /** One event, as the files state it. */
    public record Event(String name, int start, int end, double lat, double lon, double radiusKm, String kind) {
    }

    private static List<Event> index;

    /**
     * The events, bucketed so the news question does not read all 129,232 of them.
     *
     * <p>Added 2026-10-27 with the day-aware news: the card builder now asks the news question for every
     * card, and a plain scan took one place's full answer to 6.4 ms average, 33 ms worst - over budget.
     *
     * <p>Bucket size follows the event's own reach, because the answer is only faithful if the ring of
     * cells around the place is <b>wider than everything the event can be heard from</b>. An event with a
     * 900 km radius can be heard 950 km away, so it belongs in a huge cell; one with no radius only needs
     * the 50 km of {@link #HEAR_KM}. Three grids, each ~55, ~222 and ~888 km across, take every event that
     * is heard from at most that far; the handful that reach further are read one by one.
     *
     * <p>Two real losses were found by {@code worldfill/probes/NewsGridCheckProbe.java} while building
     * this and are fixed here: a single 0.5-degree grid silently dropped a Lima earthquake (its 55 km cell
     * is not enough for 50 km + radius), and a fixed 10-degree grid dropped two Baltic events (10 degrees
     * of longitude is 556 km at 60 north, not 1,110). The check now proves 3,496 town-years agree with a
     * brute-force read.
     */
    private static final double[] LADDER = {0.5d, 2.0d, 8.0d};
    private static final double KM_PER_DEGREE = 111.0d;
    private static Map<Long, int[]>[] grids;
    private static int[] huge;

    private NewsIndex() {
    }

    /** Every event the shipped files hold, in the order they were read. */
    public static synchronized List<Event> all() {
        if (index == null) {
            List<Event> out = new ArrayList<>();
            for (String file : FILES) {
                read(DIR + file, out);
            }
            index = List.copyOf(out);
        }
        return index;
    }

    @SuppressWarnings("unchecked")
    private static synchronized void buildGrids() {
        if (grids != null) {
            return;
        }
        Map<Long, List<Integer>>[] buckets = new Map[LADDER.length];
        for (int g = 0; g < LADDER.length; g++) {
            buckets[g] = new java.util.LinkedHashMap<>();
        }
        List<Integer> h = new ArrayList<>();
        List<Event> ev = all();
        for (int i = 0; i < ev.size(); i++) {
            Event e = ev.get(i);
            double reach = e.radiusKm() + HEAR_KM;
            int g = -1;
            for (int k = 0; k < LADDER.length; k++) {
                if (LADDER[k] * KM_PER_DEGREE >= reach) {
                    g = k;
                    break;
                }
            }
            if (g < 0) {
                h.add(i);                       // reaches further than any grid promises: read it every time
                continue;
            }
            buckets[g].computeIfAbsent(cellKey(e.lat(), e.lon(), LADDER[g]), k -> new ArrayList<>()).add(i);
        }
        Map<Long, int[]>[] built = new Map[LADDER.length];
        for (int g = 0; g < LADDER.length; g++) {
            built[g] = pack(buckets[g]);
        }
        huge = new int[h.size()];
        for (int i = 0; i < huge.length; i++) {
            huge[i] = h.get(i);
        }
        grids = built;
    }

    private static Map<Long, int[]> pack(Map<Long, List<Integer>> buckets) {
        Map<Long, int[]> out = new java.util.HashMap<>(buckets.size() * 2);
        buckets.forEach((k, v) -> {
            int[] a = new int[v.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = v.get(i);
            }
            out.put(k, a);
        });
        return out;
    }

    /** Row of the latitude grid, from -90 to 90. */
    private static long rowIndex(double lat, double cell) {
        double y = Math.max(-90.0d, Math.min(90.0d, lat));
        return (long) Math.floor((y + 90.0d) / cell);
    }

    /**
     * Width of a cell in degrees of longitude on this row, and how many of them fit round the world.
     *
     * <p>Widened by 1/cos(latitude) so a cell is the same number of kilometres both ways, and taken from
     * the row's centre so every point in a row agrees on where the columns are.
     */
    private static double lonStep(long cy, double cell) {
        double rowLat = Math.max(-89.0d, Math.min(89.0d, -90.0d + (cy + 0.5d) * cell));
        return cell / Math.max(0.15d, Math.cos(Math.toRadians(rowLat)));
    }

    /** Key of the cell a point falls in; wrapped, so no column is ever negative and no two rows collide. */
    private static long cellKey(double lat, double lon, double cell) {
        double x = lon;
        while (x < -180.0d) {
            x += 360.0d;
        }
        while (x >= 180.0d) {
            x -= 360.0d;
        }
        long cy = rowIndex(lat, cell);
        double step = lonStep(cy, cell);
        long columns = Math.max(1L, Math.round(360.0d / step));
        long cx = (long) Math.floor((x + 180.0d) / step);
        cx = ((cx % columns) + columns) % columns;
        return cy * 1000000L + cx;
    }

    /** Every candidate near enough to matter: the 3x3 ring of every grid, plus the unbucketed few. */
    private static List<int[]> ring(double lat, double lon) {
        buildGrids();
        List<int[]> out = new ArrayList<>(LADDER.length * 9 + 1);
        if (huge.length > 0) {
            out.add(huge);
        }
        double x = lon;
        while (x < -180.0d) {
            x += 360.0d;
        }
        while (x >= 180.0d) {
            x -= 360.0d;
        }
        for (int g = 0; g < LADDER.length; g++) {
            double cell = LADDER[g];
            long cy = rowIndex(lat, cell);
            // Each row has its own step, so each row must work out its own column: a column index means
            // nothing in the row above. Doing this in degrees instead lost 105 town-years.
            for (int dy = -1; dy <= 1; dy++) {
                long row = cy + dy;
                if (row < 0) {
                    continue;
                }
                double step = lonStep(row, cell);
                long columns = Math.max(1L, Math.round(360.0d / step));
                long cx = (long) Math.floor((x + 180.0d) / step);
                for (int dx = -1; dx <= 1; dx++) {
                    long col = ((cx + dx) % columns + columns) % columns;
                    int[] in = grids[g].get(row * 1000000L + col);
                    if (in != null) {
                        out.add(in);
                    }
                }
            }
        }
        return out;
    }

    /**
     * The nearest event that is news at this place and year, or empty.
     *
     * <p>"News" means the year falls inside the event's own span, give or take {@link #NEWS_YEARS}, and the
     * place is inside the event's radius plus {@link #HEAR_KM}. Nearest wins, so a battle in the next valley
     * is told before a storm three provinces away.
     */
    public static Optional<Event> near(double lat, double lon, int year) {
        Event best = null;
        double bestKm = Double.MAX_VALUE;
        List<Event> ev = all();
        for (int[] bucket : ring(lat, lon)) {
            for (int i : bucket) {
                Event e = ev.get(i);
                // An event that has not begun is not news yet. Found by hand: at Legnica in 1619 the index
                // first offered "Plague outbreak in Wroclaw, 1620-1621" - a year in the future, as news.
                if (year < e.start() || year > e.end() + NEWS_YEARS) {
                    continue;
                }
                double km = distanceKm(lat, lon, e.lat(), e.lon());
                if (km > e.radiusKm() + HEAR_KM) {
                    continue;
                }
                if (km < bestKm - 1e-9d
                        || (Math.abs(km - bestKm) <= 1e-9d && best != null && e.name().compareTo(best.name()) < 0)) {
                    bestKm = km;
                    best = e;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * News on a <b>day</b>, in two tiers (R2-7, 2026-10-27).
     *
     * <p>The year-only index cannot answer "what is the news <i>today</i>", and pretending otherwise is how a
     * player standing in June hears about a battle fought in November. So the day is asked of the dated
     * events first, and the year-only files are only allowed to speak about a year that is <b>over</b>:
     *
     * <ul>
     *   <li><b>today's news</b> - a dated event whose day has arrived ({@link DatedEvents}); never future;</li>
     *   <li><b>older news</b> - a year-only event from a year that has finished, which cannot be future
     *       because every day of it has passed.</li>
     * </ul>
     *
     * <p>The wording differs too, so the card never claims a day the source does not give.
     */
    /**
     * One answer to "what is the news here today".
     *
     * <p>{@code dated} - the event is dated to the day, not just the year.
     * {@code fresh} - it is recent enough to be called news at all; a dated event from 1760 told in the
     * year 3000 is the town's past, not the news (added 2026-10-27).
     */
    public record OnDay(String text, String name, double lat, double lon, int year, boolean dated, boolean fresh) {}

    public static Optional<OnDay> onDay(double lat, double lon, int todayKey) {
        int today = KnowledgeDate.year(todayKey);
        Optional<DatedEvents.Dated> dated = DatedEvents.get().near(lat, lon, todayKey);
        if (dated.isPresent()) {
            DatedEvents.Dated e = dated.get();
            return Optional.of(new OnDay(DatedEvents.line(e, today), e.name(), e.lat(), e.lon(), e.y(), true,
                    DatedEvents.fresh(e, today)));
        }
        // Nothing dated has happened here yet. The year-only record may speak only about a finished year:
        // this year or later may still be future, and four centuries ago is not news at all.
        Event best = null;
        double bestKm = Double.MAX_VALUE;
        List<Event> ev = all();
        for (int[] bucket : ring(lat, lon)) {
            for (int i : bucket) {
                Event e = ev.get(i);
                if (e.end() >= today || e.end() < today - NEWS_YEARS) {
                    continue;       // still running, or far behind: either way it is not today's news
                }
                double km = distanceKm(lat, lon, e.lat(), e.lon());
                if (km > e.radiusKm() + HEAR_KM) {
                    continue;
                }
                if (km < bestKm - 1e-9d
                        || (Math.abs(km - bestKm) <= 1e-9d && best != null && e.name().compareTo(best.name()) < 0)) {
                    bestKm = km;
                    best = e;
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        String when = best.start() == best.end() ? "in " + best.start()
                : "between " + best.start() + " and " + best.end();
        return Optional.of(new OnDay("Word has come of " + best.name() + ", " + when
                + " (dated to the year, not the day).", best.name(), best.lat(), best.lon(),
                best.end(), false, true));
    }

    /** The one line a villager would say about it. Year-dated, because the files have no days. */
    public static String line(Event e, int year) {
        if (e == null) {
            return "";
        }
        String when = e.start() == e.end() ? "in " + e.start()
                : "between " + e.start() + " and " + e.end();
        return "In this year's news: " + e.name() + ", " + when + ".";
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = (lat1 - lat2) * 110.57d;
        double dLon = (lon1 - lon2) * 111.32d * Math.cos(Math.toRadians((lat1 + lat2) / 2.0d));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    private static void read(String path, List<Event> out) {
        try (InputStream raw = NewsIndex.class.getResourceAsStream(path)) {
            if (raw == null) {
                return;
            }
            InputStream in = path.endsWith(".gz") ? new GZIPInputStream(raw) : raw;
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                JsonArray list = root.isJsonObject() && root.getAsJsonObject().has("events")
                        ? root.getAsJsonObject().getAsJsonArray("events")
                        : root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
                for (JsonElement element : list) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    var row = element.getAsJsonObject();
                    if (!row.has("lat") || !row.has("lon") || !row.has("name")) {
                        continue;
                    }
                    JsonElement startEl = row.has("start") ? row.get("start") : row.get("year");
                    if (startEl == null || startEl.isJsonNull()) {
                        continue;
                    }
                    try {
                        int start = startEl.getAsInt();
                        int end = row.has("end") && !row.get("end").isJsonNull() ? row.get("end").getAsInt() : start;
                        // These Wikipedia packs use the HistoryGazetteer convention [start, end).
                        // NewsIndex.Event uses an inclusive end, so a one-year row 1883..1884 is
                        // news about 1883, not "between 1883 and 1884". Other packs may use
                        // different conventions (e.g. the hand-authored Deccan data), so do not
                        // apply this conversion to every source indiscriminately.
                        if ((path.endsWith("/events_wikipedia.json.gz")
                                || path.endsWith("/events_wikipedia_decades.json.gz")
                                || path.endsWith("/events_wikipedia_regional.json.gz")) && end > start) {
                            end -= 1;
                        }
                        double radius = row.has("radiusKm") && !row.get("radiusKm").isJsonNull()
                                ? row.get("radiusKm").getAsDouble() : 20.0d;
                        out.add(new Event(row.get("name").getAsString(), start, Math.max(start, end),
                                row.get("lat").getAsDouble(), row.get("lon").getAsDouble(), radius,
                                row.has("kind") ? row.get("kind").getAsString() : "event"));
                    } catch (RuntimeException ignored) {
                        // A row whose numbers are not numbers is skipped rather than breaking the index.
                    }
                }
            }
        } catch (Exception ignored) {
            // A missing or damaged file leaves the rest of the index intact.
        }
    }
}

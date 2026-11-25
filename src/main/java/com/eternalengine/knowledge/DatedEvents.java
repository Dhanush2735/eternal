package com.eternalengine.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Events that know the <b>day</b> they happened, so a card can tell only what has already happened (R2-7).
 *
 * <p>The engine's original thirteen event files are year-granular: 129,232 events, not one of them with a
 * month or a day. That makes "news by the day" impossible to build honestly on them, because a year-only
 * filter will happily report a battle fought in November to a player standing in June of the same year. The
 * first fix - "an event that has not begun is not news" - closed the gap between years and left the gap
 * <b>inside</b> the year wide open, which is the defect this class exists to remove.
 *
 * <p>The clock already had the resolution: {@link KnowledgeDate} keys a date as
 * {@code year * 10000 + month * 100 + day}, and {@code WorldCalendar.today()} produces one. What was missing
 * was data that could be compared against it.
 *
 * <h2>The rule, and why it never tells the future</h2>
 *
 * <p>Each event carries the precision its source actually supports: 11 a day, 10 a month, 9 a year. The
 * instant an event can be said to have <b>happened</b> is the last moment it could still have been about to
 * happen:
 *
 * <ul>
 *   <li>a day-dated event has happened from that day;</li>
 *   <li>a month-dated event has happened from the last day of that month;</li>
 *   <li>a year-dated event has happened from the last day of that year.</li>
 * </ul>
 *
 * <p>So an event is news only when {@code happened <= today}. A day-dated battle in November is silent in
 * June; a year-dated event stays silent until the year is out. That is the price of not lying, and it is why
 * the dated data matters: only a day-dated event can be news <i>this</i> year.
 */
public final class DatedEvents {

    /** 0.5-degree cells, the same bucket size the ruler register uses. */
    private static final double CELL = 0.5d;

    /** How far news travels: an event is heard within its own radius plus this. */
    public static final double HEAR_KM = 50.0d;

    /**
     * One event, with the precision the source gives.
     *
     * @param prec 11 day, 10 month, 9 year - anything coarser is not used
     */
    public record Dated(String name, int y, int m, int d, double lat, double lon, int prec, String kind) {

        /** The moment this event can first be said to have happened. */
        public int happenedKey() {
            return switch (prec) {
                case 11 -> KnowledgeDate.key(y, Math.max(1, m), Math.max(1, d));
                case 10 -> KnowledgeDate.key(y, Math.max(1, m), 28);
                default -> KnowledgeDate.key(y, 12, 31);
            };
        }

        /** How a villager says it, given the precision - never claiming a day the source does not give. */
        public String when() {
            String[] months = {"", "January", "February", "March", "April", "May", "June", "July",
                    "August", "September", "October", "November", "December"};
            if (y < 0) {
                return (prec == 9 ? "sometime in " + (-y) + " BC" : "in " + (-y) + " BC");
            }
            if (prec == 11 && m >= 1 && m <= 12) {
                return "on " + d + " " + months[m] + " " + y;
            }
            if (prec == 10 && m >= 1 && m <= 12) {
                return "in " + months[m] + " " + y;
            }
            return "sometime in " + y;
        }
    }

    private static final String RESOURCE = "/data/eternalengine/eternal/history/dated_events.json";
    private static DatedEvents INSTANCE;

    private final List<Dated> all = new ArrayList<>();
    private final Map<Long, int[]> grid = new LinkedHashMap<>();
    private final String source;

    private DatedEvents(List<Dated> events, String source) {
        this.all.addAll(events);
        this.source = source;
        Map<Long, List<Integer>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < all.size(); i++) {
            Dated e = all.get(i);
            buckets.computeIfAbsent(cellKey(e.lat(), e.lon()), k -> new ArrayList<>()).add(i);
        }
        buckets.forEach((k, v) -> {
            int[] a = new int[v.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = v.get(i);
            }
            grid.put(k, a);
        });
    }

    private static DatedEvents empty() {
        return new DatedEvents(List.of(), "not loaded");
    }

    /** The shared index, loaded once. */
    public static synchronized DatedEvents get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    private static DatedEvents load() {
        try (InputStream raw = DatedEvents.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return empty();
            }
            try (InputStreamReader r = new InputStreamReader(raw, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                JsonArray arr = root.getAsJsonArray("events");
                List<Dated> out = new ArrayList<>(arr.size());
                for (JsonElement el : arr) {
                    JsonArray a = el.getAsJsonArray();
                    int prec = a.get(6).getAsInt();
                    if (prec < 9 || prec > 11) {
                        continue;
                    }
                    out.add(new Dated(a.get(0).getAsString(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                            a.get(3).getAsInt(), a.get(4).getAsDouble(), a.get(5).getAsDouble(),
                            prec, a.get(7).getAsString()));
                }
                String src = root.has("source") ? root.get("source").getAsString() : RESOURCE;
                return new DatedEvents(out, src);
            }
        } catch (Exception e) {
            return new DatedEvents(List.of(), RESOURCE + " failed: " + e.getMessage());
        }
    }

    /** Every dated event, in date order. */
    public List<Dated> all() {
        return List.copyOf(all);
    }

    public int size() {
        return all.size();
    }

    public int dayPrecise() {
        return (int) all.stream().filter(e -> e.prec() == 11).count();
    }

    public String source() {
        return source;
    }

    /**
     * The nearest event that has <b>definitely</b> happened by {@code todayKey} and is close enough to hear.
     *
     * <p>This is the whole point of the class: the comparison is on the day, not the year, so a November
     * battle cannot be told in June. The event's own {@link Dated#happenedKey()} is the last moment it could
     * have been future, so {@code happened <= today} is exactly "it is not future news".
     */
    public Optional<Dated> near(double lat, double lon, int todayKey) {
        Dated best = null;
        double bestKm = Double.MAX_VALUE;
        int today = Math.min(todayKey, Integer.MAX_VALUE);
        for (int[] in : neighbours(lat, lon)) {
            for (int i : in) {
                Dated e = all.get(i);
                if (e.happenedKey() > today) {
                    continue;
                }
                double km = distanceKm(lat, lon, e.lat(), e.lon());
                if (km <= HEAR_KM && km < bestKm) {
                    bestKm = km;
                    best = e;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The events already happened by {@code todayKey} within reach, nearest first. */
    public List<Dated> recent(double lat, double lon, int todayKey, int limit) {
        List<Dated> out = new ArrayList<>();
        for (int[] in : neighbours(lat, lon)) {
            for (int i : in) {
                Dated e = all.get(i);
                if (e.happenedKey() > todayKey) {
                    continue;
                }
                if (distanceKm(lat, lon, e.lat(), e.lon()) <= HEAR_KM) {
                    out.add(e);
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b.happenedKey(), a.happenedKey()));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    /** The one line a villager would say about it. */
    /** How recently an event must have happened to be called news rather than history. */
    public static final int FRESH_YEARS = 2;

    public static String line(Dated e) {
        return "Word has come of " + e.name() + ", " + e.when() + ".";
    }

    /**
     * The card's line, which must not call four hundred years ago "news" (2026-10-27).
     *
     * <p>Found by asking what the game says in 2027 and beyond: standing in Legnica in the year 3000 the
     * news card said "Word has come of Battle of Liegnitz (1760)". The day tier had a floor but no recency
     * rule, so the nearest dated event in hearing was told as today's news however old it was - and
     * measured over 2,432 town-years, only 3.2% of day-dated answers were within two years. A stale line
     * is still worth showing (it is the town's past, and it is true), but it is not news, and the wording
     * must say so.
     */
    public static String line(Dated e, int todayYear) {
        if (Math.abs(todayYear - e.y()) <= FRESH_YEARS) {
            return line(e);
        }
        return "Not news - this place's past: " + e.name() + ", " + e.when() + ".";
    }

    /** Whether this event is recent enough to be called news at the given year. */
    public static boolean fresh(Dated e, int todayYear) {
        return Math.abs(todayYear - e.y()) <= FRESH_YEARS;
    }

    private Iterable<int[]> neighbours(double lat, double lon) {
        List<int[]> out = new ArrayList<>(9);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int[] in = grid.get(cellKey(lat + dy * CELL, lon + dx * CELL));
                if (in != null) {
                    out.add(in);
                }
            }
        }
        return out;
    }

    private static long cellKey(double lat, double lon) {
        long cy = Math.round(Math.floor((lat + 90.0d) / CELL));
        long cx = Math.round(Math.floor((lon + 180.0d) / CELL));
        return cy * 10000L + cx;
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = (lat1 - lat2) * 110.57d;
        double dLon = (lon1 - lon2) * 111.32d * Math.cos(Math.toRadians((lat1 + lat2) / 2.0d));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    private DatedEvents() {
        this(List.of(), "no");
    }

    /** For a check that wants to know whether the dated file is present at all. */
    public boolean loaded() {
        return !all.isEmpty();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "dated events: %,d (%,d dated to the day) from %s",
                all.size(), dayPrecise(), source);
    }
}

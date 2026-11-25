package com.eternalengine.people;

import com.eternalengine.news.NewsTravel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Step 38 (C7 migrations and refugees): <b>the roads people took when they had to go</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>{@code population/MigrationSystem} models migration the way economists do: a push in one
 * region, a pull in another, and a flow of people per year between the two. It has no distance, no
 * route, no date and no names. It cannot say that in August 1947 the road from Lahore to Amritsar
 * carried the largest movement of people in the history of the world, or how long that road took
 * to walk, or that the people on it did not choose to go. This register can, because it is built
 * the other way round: not from a rate but from a record.
 *
 * <h3>What a record is</h3>
 *
 * <p>Every row in {@code migrations.json.gz} is <b>one movement of people, on one route, in one
 * stretch of years</b> — where they left, where they arrived, what year, how many, whether anybody
 * made them go, and what kind of going it was. Thirty-eight records, from the Bantu expansion to
 * the refugees of the Syrian civil war, and every one of them carries the source it came from.
 * Where an entry is marked {@code estimated} the number is this table's own and no source's.
 *
 * <p>The route is the point. A migration here is not a line on a map but a number of days,
 * because this register hands each route to {@link NewsTravel} and asks how long it took in the
 * year it happened: on foot at thirty kilometres a day before the railways, by the post from 1490,
 * down a railway from 1825, by air after 1919. The road from Lahore to Amritsar is about fifty
 * kilometres and took two days on foot in 1947 — two days for the largest movement of people there
 * has ever been, which is the sentence this class exists to be able to say.
 *
 * <h3>What it does not claim</h3>
 *
 * <p><b>This is a table of records, not a field of migration.</b> A place in a year with no record
 * is not a place where nobody moved; it is a place where nobody counted, and the register says so
 * out loud rather than returning a quiet zero. Most migration in history was not recorded at all.
 *
 * <p>The counts are the number of people who made <b>this route</b> in <b>this window</b>, not a
 * net flow; the same people can appear in two records where a source counted both legs. Where a
 * figure is only part of a larger total — the four Partition rows, the three Atlantic routes — the
 * note says so and the parts add to the published whole.
 */
public final class Migrations {

    /** How far off a route a place can be and still count as on it. */
    public static final double ON_THE_ROAD_KM = 400.0d;

    /** What kind of going it was. */
    public enum Kind {
        /** People running from a war, a massacre or a border drawn overnight. */
        FLIGHT("flight from danger"),
        /** People driven out by the state that ruled them. */
        EXPULSION("an expulsion"),
        /** Two states agreeing to swap populations, as at Lausanne in 1923. */
        TRANSFER("an agreed exchange"),
        /** People deported by force to somewhere they did not choose. */
        DEPORTATION("a deportation"),
        /** People sold and shipped. */
        TRADE("the slave trade"),
        /** People signed to a term of years and shipped. */
        INDENTURE("indentured labour"),
        /** People leaving a famine. */
        FAMINE("a famine flight"),
        /** People moving for work. */
        ECONOMIC("a movement for work"),
        /** A people spreading, with or without an army. */
        SETTLEMENT("a settlement"),
        /** People moved by conquest. */
        CONQUEST("a conquest"),
        /** Anything else the sources will not name. */
        OTHER("a movement of people");

        private final String phrase;

        Kind(String phrase) {
            this.phrase = phrase;
        }

        /** "... a flight from danger", for a sentence. */
        public String phrase() {
            return phrase;
        }

        /** Does anybody make them go? Expulsions, transfers, deportations and the slave trade are
         * not chosen by the people in them. */
        public boolean forced() {
            return this == EXPULSION || this == TRANSFER || this == DEPORTATION
                || this == TRADE || this == CONQUEST;
        }

        static Kind of(String s) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(s)) {
                    return k;
                }
            }
            return OTHER;
        }
    }

    /**
     * One movement of people, on one route, in one stretch of years.
     *
     * <p>{@code mark} is {@code recorded} where the number is a published figure, {@code mixed}
     * where the sources disagree and the lower one is used, and {@code estimated} where it is this
     * table's own reading and no source's. Callers should repeat the mark when they print a
     * number, because a figure offered without it is a figure offered as better than it is.
     */
    public record Migration(
        String id,
        String name,
        double fromLat, double fromLon, String fromName,
        double toLat, double toLon, String toName,
        int year, int endYear,
        long people,
        Kind kind,
        boolean forced,
        boolean sea,
        String mark,
        String note,
        String source,
        double routeKm
    ) {
        /** The last year of the movement, or the first if it lasted a year or less. */
        public int lastYear() {
            return endYear > year ? endYear : year;
        }

        /** Was this going on, or had it happened, in this year? */
        public boolean byYear(int y) {
            return y >= year && y <= lastYear() + 1;
        }

        /** How many years it ran. */
        public int span() {
            return lastYear() - year + 1;
        }

        /** The year to measure the road in: the year asked for, held inside the movement's own
         * span, so a question about 1955 does not measure a 1947 road in 1955's world. */
        public int measuredIn(int y) {
            int e = lastYear();
            return Math.max(year, e == Integer.MAX_VALUE ? Math.max(year, y) : Math.min(e, y));
        }

        /**
         * <b>How these people actually moved.</b> Not the fastest channel in the year: a channel
         * that carries a message is not a channel that carries a family.
         *
         * <ul>
         * <li><b>Over water</b> — by sail before 1807 and by steamship after, because that is
         *     what carried them, whether they were chained in a hold or had paid for a berth.</li>
         * <li><b>Driven or fleeing over land</b> — on foot. A column of refugees with carts and
         *     children does not move at the speed of a mail train, and the largest movement of
         *     people in the history of the world was made by people walking.</li>
         * <li><b>A conquest</b> — mounted, because conquests were.</li>
         * <li><b>People who chose to go and could pay</b> — the quickest thing that carries
         *     bodies: the railway from 1825, the motor from 1912. They took the train. The wire
         *     and the airwave are excluded here on purpose: neither of them can carry a person,
         *     and neither could the post.</li>
         * </ul>
         */
        public NewsTravel.Mode mode(int y) {
            int when = measuredIn(y);
            if (sea) {
                return NewsTravel.leg(NewsTravel.Mode.STEAMSHIP, when,
                    fromLat, fromLon, toLat, toLon, true).isPresent()
                    ? NewsTravel.Mode.STEAMSHIP : NewsTravel.Mode.SAIL;
            }
            if (kind == Kind.CONQUEST) {
                return NewsTravel.Mode.RIDER;
            }
            if (forced) {
                return NewsTravel.Mode.FOOT;
            }
            NewsTravel.Mode best = NewsTravel.Mode.FOOT;
            double bestDays = Double.MAX_VALUE;
            for (NewsTravel.Leg l : NewsTravel.all(when, fromLat, fromLon, toLat, toLon, false)) {
                NewsTravel.Mode m = l.mode();
                if (m == NewsTravel.Mode.WIRE || m == NewsTravel.Mode.AIRWAVE
                    || m == NewsTravel.Mode.POST || m == NewsTravel.Mode.TRAVELLER) {
                    continue;
                }
                if (l.days() < bestDays) {
                    bestDays = l.days();
                    best = m;
                }
            }
            return best;
        }

        /** The route as the people on it took it: a number of days, in the year it happened. */
        public NewsTravel.Leg route(int y) {
            int when = measuredIn(y);
            return NewsTravel.leg(mode(y), when, fromLat, fromLon, toLat, toLon, sea)
                .orElseGet(() -> NewsTravel.traveller(when, fromLat, fromLon, toLat, toLon, sea));
        }

        /** The fastest channel that existed in that year — the post, the railway, the wire, the
         * air — whether or not the people on the road could use it. */
        public NewsTravel.Leg fast(int y) {
            return NewsTravel.fastest(measuredIn(y), fromLat, fromLon, toLat, toLon, sea);
        }

        /** The route as a leg, described. */
        public String routeLine(int y) {
            NewsTravel.Leg l = route(y);
            NewsTravel.Leg f = fast(y);
            String base = String.format(Locale.ROOT, "%s to %s, %s",
                fromName, toName, l.text());
            if (f.days() + 0.5d < l.days()) {
                base += String.format(Locale.ROOT,
                    " — though in %d the quickest thing on that road took %s",
                    measuredIn(y), f.text());
            }
            return base;
        }

        /** The number, with the mark that keeps it honest. */
        public String peopleLine() {
            String n = String.format(Locale.ROOT, "%,d", people);
            return switch (mark) {
                case "mixed" -> n + " (the sources disagree; the lower figure is used)";
                case "estimated" -> "perhaps " + n + " (estimated by this table, not by any source)";
                default -> n;
            };
        }

        /** One sentence: who, where, when, how many, and whether they chose it. */
        public String line(int y) {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(": ").append(peopleLine()).append(" people from ")
              .append(fromName).append(" to ").append(toName)
              .append(" in ").append(yearLabel());
            if (forced) {
                sb.append(", ").append(kind == Kind.TRADE
                    ? "carried there by force" : "driven out, not choosing to go");
            }
            sb.append(". ").append(routeLine(y)).append(".");
            return sb.toString();
        }

        /** The stretch of years, as a label. */
        public String yearLabel() {
            int e = lastYear();
            if (e <= year) {
                return year < 0 ? "BC " + (-year) : "AD " + year;
            }
            return (year < 0 ? "BC " + (-year) : "AD " + year) + " to "
                + (e < 0 ? "BC " + (-e) : "AD " + e);
        }

        /** Why, in the source's own terms. */
        public String why() {
            return note == null || note.isEmpty() ? kind.phrase() : note;
        }
    }

    private static final Migrations INSTANCE = new Migrations();
    private final List<Migration> rows;

    private Migrations() {
        List<Migration> out = new ArrayList<>();
        JsonObject root = read();
        if (root != null && root.has("migrations")) {
            JsonArray arr = root.getAsJsonArray("migrations");
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                out.add(new Migration(
                    str(o, "id"), str(o, "name"),
                    dbl(o, "flat"), dbl(o, "flon"), str(o, "fname"),
                    dbl(o, "tlat"), dbl(o, "tlon"), str(o, "tname"),
                    (int) Math.round(dbl(o, "year")), endOf(o),
                    (long) Math.round(dbl(o, "people")),
                    Kind.of(str(o, "kind")),
                    o.has("forced") && o.get("forced").getAsBoolean(),
                    o.has("sea") && o.get("sea").getAsBoolean(),
                    str(o, "mark"), str(o, "note"), str(o, "source"),
                    dbl(o, "km")));
            }
        }
        out.sort((a, b) -> a.people != b.people
            ? Long.compare(b.people, a.people) : a.id().compareTo(b.id()));
        this.rows = Collections.unmodifiableList(out);
    }

    /** The one register. */
    public static Migrations get() {
        return INSTANCE;
    }

    /** Every record we hold, largest first. */
    public static List<Migration> all() {
        return get().rows;
    }

    /** How many records there are. */
    public static int count() {
        return get().rows.size();
    }

    /** The record with this id, or null. */
    public static Migration find(String id) {
        for (Migration m : get().rows) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }

    /** How many kilometres between two points, great-circle. */
    public static double kmBetween(double la1, double lo1, double la2, double lo2) {
        double p = Math.PI / 180.0d;
        double h = 0.5d - Math.cos((la2 - la1) * p) / 2.0d
            + Math.cos(la1 * p) * Math.cos(la2 * p) * (1.0d - Math.cos((lo2 - lo1) * p)) / 2.0d;
        return 12742.0d * Math.asin(Math.sqrt(Math.max(0.0d, h)));
    }

    /**
     * How far this place is from the road a migration took, in kilometres.
     *
     * <p>Over land the road is the great circle between the two ends, which is a fiction but a
     * serviceable one at four hundred kilometres. <b>Over water it is not, and pretending
     * otherwise produces nonsense</b>: the great circle from Calcutta to British Guiana runs
     * clean over the Punjab, and no ship ever did. For a sea crossing we know the two ports and
     * not the water between them, so a place counts as on the road only if it is near one of the
     * two ends.
     */
    public static double kmFromRoute(Migration m, double lat, double lon) {
        double dFrom = kmBetween(m.fromLat(), m.fromLon(), lat, lon);
        double dTo = kmBetween(m.toLat(), m.toLon(), lat, lon);
        if (m.sea()) {
            return Math.min(dFrom, dTo);
        }
        double p = Math.PI / 180.0d;
        double r = 6371.0d;
        double d12 = kmBetween(m.fromLat(), m.fromLon(), m.toLat(), m.toLon()) / r;
        if (d12 <= 0.0d) {
            return kmBetween(m.fromLat(), m.fromLon(), lat, lon);
        }
        double d13 = kmBetween(m.fromLat(), m.fromLon(), lat, lon) / r;
        double t13 = bearing(m.fromLat(), m.fromLon(), lat, lon);
        double t12 = bearing(m.fromLat(), m.fromLon(), m.toLat(), m.toLon());
        double xt = Math.asin(Math.max(-1.0d, Math.min(1.0d, Math.sin(d13) * Math.sin(t13 - t12))));
        double cosXt = Math.cos(xt);
        // how far along the route the nearest point is; if it is off either end, that end is nearer
        double at = Math.abs(cosXt) < 1e-12d ? 0.0d
            : Math.acos(Math.max(-1.0d, Math.min(1.0d, Math.cos(d13) / cosXt)));
        if (at < 0.0d) {
            return dFrom;
        }
        if (at > d12) {
            return dTo;
        }
        return Math.abs(xt) * r;
    }

    private static double bearing(double la1, double lo1, double la2, double lo2) {
        double p = Math.PI / 180.0d;
        double y = Math.sin((lo2 - lo1) * p) * Math.cos(la2 * p);
        double x = Math.cos(la1 * p) * Math.sin(la2 * p)
            - Math.sin(la1 * p) * Math.cos(la2 * p) * Math.cos((lo2 - lo1) * p);
        return Math.atan2(y, x);
    }

    /** Every movement going on, or recent, in this year that passes within
     * {@link #ON_THE_ROAD_KM} of this place — largest first. Empty is an honest answer: it means
     * no movement on this road was ever counted, not that nobody moved. */
    public static List<Migration> near(double lat, double lon, int year) {
        return near(lat, lon, year, ON_THE_ROAD_KM);
    }

    /** As {@link #near(double, double, int)} but with the caller's own idea of "on the road". */
    public static List<Migration> near(double lat, double lon, int year, double km) {
        List<Migration> out = new ArrayList<>();
        for (Migration m : get().rows) {
            if (!m.byYear(year)) {
                continue;
            }
            if (kmFromRoute(m, lat, lon) <= km) {
                out.add(m);
            }
        }
        return out;
    }

    /** Movements that began within {@code km} of this place in this year. */
    public static List<Migration> leaving(double lat, double lon, int year) {
        return leaving(lat, lon, year, ON_THE_ROAD_KM);
    }

    /** As {@link #leaving(double, double, int)} with the caller's radius. */
    public static List<Migration> leaving(double lat, double lon, int year, double km) {
        List<Migration> out = new ArrayList<>();
        for (Migration m : get().rows) {
            if (m.byYear(year) && kmBetween(lat, lon, m.fromLat(), m.fromLon()) <= km) {
                out.add(m);
            }
        }
        return out;
    }

    /** Movements that ended within {@code km} of this place in this year. */
    public static List<Migration> arriving(double lat, double lon, int year) {
        return arriving(lat, lon, year, ON_THE_ROAD_KM);
    }

    /** As {@link #arriving(double, double, int)} with the caller's radius. */
    public static List<Migration> arriving(double lat, double lon, int year, double km) {
        List<Migration> out = new ArrayList<>();
        for (Migration m : get().rows) {
            if (m.byYear(year) && kmBetween(lat, lon, m.toLat(), m.toLon()) <= km) {
                out.add(m);
            }
        }
        return out;
    }

    /** The largest movement that passed this place in this year, or null. */
    public static Migration largestNear(double lat, double lon, int year) {
        List<Migration> n = near(lat, lon, year);
        return n.isEmpty() ? null : n.get(0);
    }

    /** How many people in all were on the move past this place in this year. This is a count of
     * counted migrations, and it undercounts by design: put the word "at least" in front of it. */
    public static long peopleNear(double lat, double lon, int year) {
        long t = 0L;
        for (Migration m : near(lat, lon, year)) {
            t += m.people();
        }
        return t;
    }

    /** One sentence about the roads out of this place in this year. Says plainly when nothing was
     * counted, because a silence is not an empty road. */
    public static String line(double lat, double lon, int year) {
        List<Migration> n = near(lat, lon, year);
        if (n.isEmpty()) {
            return "No movement of people on this road was ever counted here, which is not the "
                + "same as nobody leaving.";
        }
        Migration m = n.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("The road here carries ").append(m.name()).append(": ").append(m.peopleLine())
          .append(" people from ").append(m.fromName()).append(" to ").append(m.toName())
          .append(" in ").append(m.yearLabel()).append(". ").append(m.routeLine(year)).append(".");
        if (n.size() > 1) {
            sb.append(" At least ").append(n.size() - 1)
              .append(n.size() == 2 ? " other movement" : " other movements")
              .append(" crossed it in these years too.");
        }
        return sb.toString();
    }

    /** A short card: the movements on this road in this year, one line each. */
    public static String card(double lat, double lon, int year) {
        List<Migration> n = near(lat, lon, year);
        if (n.isEmpty()) {
            return "people on the road: none counted";
        }
        StringBuilder sb = new StringBuilder("people on the road, ").append(year).append(':');
        for (Migration m : n) {
            sb.append("\n  ").append(m.name()).append(" - ").append(m.peopleLine())
              .append(", ").append(m.route(year).days() < 1.0d
                  ? "the same day" : NewsTravel.phrase(m.route(year).days()))
              .append(m.forced() ? ", not by choice" : "");
        }
        return sb.toString();
    }

    private static JsonObject read() {
        for (String path : new String[] {RESOURCE, RESOURCE_LEGACY}) {
            try (InputStream in = Migrations.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                try (InputStreamReader r = new InputStreamReader(
                        new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(r).getAsJsonObject();
                }
            } catch (IOException | IllegalStateException e) {
                // try the next path
            }
        }
        return null;
    }

    private static final String RESOURCE =
        "/data/eternalengine/eternal/history/migrations.json.gz";
    private static final String RESOURCE_LEGACY =
        "/assets/eternalengine/eternal/history/migrations.json.gz";

    /** The last year of a movement, or {@link Integer#MAX_VALUE} while it is still going. */
    private static int endOf(JsonObject o) {
        return o.has("end") && !o.get("end").isJsonNull()
            ? (int) Math.round(o.get("end").getAsDouble())
            : Integer.MAX_VALUE;
    }

    private static String str(JsonObject o, String k) {
        return str(o, k, "");
    }

    private static String str(JsonObject o, String k, String dflt) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : dflt;
    }

    private static double dbl(JsonObject o, String k) {
        return dbl(o, k, 0.0d);
    }

    private static double dbl(JsonObject o, String k, double dflt) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : dflt;
    }

    /** Every migration in the pack, in file order, for the probe. */
    public static List<Migration> inFileOrder() {
        List<Migration> out = new ArrayList<>(get().rows);
        out.sort((a, b) -> a.year() != b.year()
            ? Integer.compare(a.year(), b.year()) : a.id().compareTo(b.id()));
        return out;
    }

}

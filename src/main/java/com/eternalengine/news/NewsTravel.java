package com.eternalengine.news;

import com.eternalengine.technology.TechnologyAtlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Step 37 (C24 the speed of news, E20 post and messages): <b>how long news takes to get there</b>.
 *
 * <h3>What it answers</h3>
 *
 * <p>News travels at the speed of the era it travels in and of the road it takes. A word spoken
 * in Rome reached Antioch by the slow road of whoever happened to be going that way; the same
 * word in 1870 went down a wire and arrived before the speaker's letter had left the building.
 * This register turns a distance and a year into a number of days, and says which road it took.
 *
 * <h3>Two different speeds, and why</h3>
 *
 * <p><b>A traveller</b> — news that goes with whoever happens to be going that way, which is how
 * most news moved for most of history — moves at the pace of its era, and that pace is step 30a's
 * technology atlas: 3 km a day before the roads, 5 to the year 500, 8 to 1450, 25 to 1750, 60 to
 * 1850, 150 to 1914, 300 to 1950, 500 after. It is slow because it is not a journey anyone is
 * making on purpose: it waits for a caravan to form, a ship to be loaded, a friend to decide to
 * go. That is the curve, and it is what rumour runs on.
 *
 * <p><b>Everything else is a deliberate messenger, and a deliberate messenger has a speed.</b>
 * Those speeds do not rise with the era the way the incidental curve does — a post horse in 1700
 * was not slower than one in 1500 — so they are set here in kilometres a day, and each is gated on
 * the year its technology reached the place:
 *
 * <pre>
 *   on foot       30 km/day   always
 *   a rider       60 km/day   always - one horse, changed at inns, not at stages
 *   by sea        90 km/day   always - a merchantman averaging two knots made good, all seasons
 *   the post     180 km/day   from 1490, where the public post reached (relays of fresh horses;
 *                             the London-Edinburgh post took three days in the 1690s)
 *   steamship    400 km/day   from 1807
 *   railway      700 km/day   from 1825 (the mail train, plus the last mile by cart)
 *   motor        900 km/day   from 1912
 *   the wire        one day   from 1839 - written, sent, and carried by hand at the far end
 *   the air      the same day from 1919
 * </pre>
 *
 * <p>Checked against the record: Rome to Antioch by sea in AD 100 comes out at about a month,
 * which is what it took; the London post reached Edinburgh in three and a half days in 1700,
 * which is what it took; and the wire put London within a day of Calcutta from 1870, which is
 * what happened.
 *
 * <h3>What it does not claim</h3>
 *
 * <p>It does not know where the roads, the railways or the wires actually ran. It asks whether
 * the technology had reached <b>both ends</b> of the journey and takes the later of the two
 * years, which is a fair approximation and a generous one: the line may not have been built
 * between them at all. Where a caller knows better — a named Roman road, a named railway — it
 * should say so and not ask this class.
 *
 * <p>Sea distances are the great-circle distance times {@link #SEA_DETOUR}, because a ship cannot
 * sail through a continent, and the caller decides whether a route is over land or over water:
 * this class has no map of the land.
 */
public final class NewsTravel {

    /** Ships cannot sail through continents; this is the detour. */
    public static final double SEA_DETOUR = 1.35d;

    /** Days in a year, for turning the atlas's fractional arrival year into days. */
    private static final double YEAR = 365.25d;

    /** Whether a mode is asked for on land, on water, or on either. */
    public enum Domain { LAND, SEA, EITHER }

    /** How a piece of news moved. */
    public enum Mode {
        /** News that goes with whoever was going that way anyway: the incidental channel, and
         * the one most rumour ran on. It moves at the speed of its era, so it has no figure of
         * its own. */
        TRAVELLER("with a traveller", 0.0d, null, Domain.EITHER),
        /** A messenger on foot: thirty kilometres a day on a good road. */
        FOOT("on foot", 30.0d, null, Domain.LAND),
        /** One rider on one horse, changed at inns rather than at stages. */
        RIDER("by a rider", 60.0d, null, Domain.LAND),
        /** A merchantman, all seasons and all weathers. */
        SAIL("by sea", 90.0d, null, Domain.SEA),
        /** Relays of fresh horses at stages: the public post, from 1490. */
        POST("by post", 180.0d, "post", Domain.LAND),
        /** From 1807, where the steamship had reached. */
        STEAMSHIP("by steamship", 400.0d, "steamship", Domain.SEA),
        /** From 1825, where the railway had reached. */
        RAILWAY("by railway", 700.0d, "railway", Domain.LAND),
        /** Motor vehicles and motor ships, from 1912. */
        MOTOR("by motor", 900.0d, "motor_ship", Domain.LAND),
        /** The telegraph: written, sent, and carried by hand at the far end, so a day. */
        WIRE("down the wire", -1.0d, "telegraph", Domain.EITHER),
        /** Radio: heard as it is said, so the same day. */
        AIRWAVE("over the air", -2.0d, "radio", Domain.EITHER);

        private final String phrase;
        private final double kmPerDay;
        private final String tech;
        private final Domain domain;

        Mode(String phrase, double kmPerDay, String tech, Domain domain) {
            this.phrase = phrase;
            this.kmPerDay = kmPerDay;
            this.tech = tech;
            this.domain = domain;
        }

        /** "... carried by post", for a sentence. */
        public String phrase() {
            return phrase;
        }

        /** Kilometres a day, or 0 for the incidental channel and negative for the instant ones. */
        public double kmPerDay() {
            return kmPerDay;
        }

        /** The technology this mode needs, or null where it needs none. */
        public String techId() {
            return tech;
        }

        /** Is this the kind that arrives the same day whatever the distance? */
        public boolean instant() {
            return kmPerDay < 0.0d;
        }

        /** Does this mode run at the speed of its era rather than a speed of its own? */
        public boolean atEraSpeed() {
            return kmPerDay == 0.0d;
        }

        public Domain domain() {
            return domain;
        }
    }

    /**
     * One way the news could go, and how long it would take.
     *
     * @param mode  which road it took
     * @param days  how long, or 0 where the mode arrives the same day
     * @param text  "in about nine days", "the same day"
     */
    public record Leg(Mode mode, double days, String text) {

        /** "by post, in about four days". */
        public String describe() {
            return mode.phrase() + ", " + text;
        }
    }

    private NewsTravel() {}

    /**
     * Has this mode's technology reached a place by a year? Modes that need no technology — a
     * traveller, a walker, a rider, a sailing ship — are always there.
     */
    public static boolean available(Mode mode, double lat, double lon, int year) {
        return arrivalOf(mode, lat, lon) <= year;
    }

    /**
     * The year this mode's technology reached a place: {@link Integer#MIN_VALUE} where it needs
     * nothing, {@link TechnologyAtlas#NEVER} where it never came.
     */
    public static int arrivalOf(Mode mode, double lat, double lon) {
        if (mode.tech == null) {
            return Integer.MIN_VALUE;
        }
        TechnologyAtlas atlas = TechnologyAtlas.get();
        TechnologyAtlas.Tech t = atlas.tech(mode.tech);
        if (t == null) {
            return TechnologyAtlas.NEVER;
        }
        return atlas.arrivalAt(t, lat, lon);
    }

    /**
     * How long this mode would take, in days. Empty where the mode does not run that way — a
     * ship on a land route, a rider on a sea one — or where its technology had not reached one of
     * the two ends by the year.
     *
     * @param sea true for a sea route, which lengthens the distance by {@link #SEA_DETOUR}
     */
    public static Optional<Leg> leg(Mode mode, int year, double la1, double lo1, double la2,
                                    double lo2, boolean sea) {
        if (mode.domain() != Domain.EITHER && mode.domain() != (sea ? Domain.SEA : Domain.LAND)) {
            return Optional.empty();
        }
        if (!available(mode, la1, lo1, year) || !available(mode, la2, lo2, year)) {
            return Optional.empty();
        }
        double km = TechnologyAtlas.kmBetween(la1, lo1, la2, lo2) * (sea ? SEA_DETOUR : 1.0d);
        double days;
        if (mode == Mode.WIRE) {
            days = 1.0d;
        } else if (mode.instant()) {
            days = 0.0d;
        } else if (mode.atEraSpeed()) {
            days = TechnologyAtlas.get().daysToCross(year, km, 1.0d);
        } else {
            days = km / mode.kmPerDay();
        }
        return Optional.of(new Leg(mode, days, phrase(days)));
    }

    /**
     * The fastest way the news would actually have gone that year: the quickest of every mode
     * whose technology had reached both ends. Never empty — a traveller is always available on
     * either kind of route, so there is always an answer, and in the year 500 it is a slow one.
     */
    public static Leg fastest(int year, double la1, double lo1, double la2, double lo2,
                              boolean sea) {
        Leg best = null;
        for (Mode m : Mode.values()) {
            Optional<Leg> l = leg(m, year, la1, lo1, la2, lo2, sea);
            if (l.isPresent() && (best == null || l.get().days() < best.days())) {
                best = l.get();
            }
        }
        return best == null ? null : best;
    }

    /**
     * The incidental channel on its own: how long news takes that nobody is carrying on purpose.
     * This is the one rumours run on, and it is always the slowest answer.
     */
    public static Leg traveller(int year, double la1, double lo1, double la2, double lo2,
                                boolean sea) {
        return leg(Mode.TRAVELLER, year, la1, lo1, la2, lo2, sea).orElse(null);
    }

    /** Every mode that could carry it that year, quickest first. */
    public static List<Leg> all(int year, double la1, double lo1, double la2, double lo2,
                                boolean sea) {
        List<Leg> out = new ArrayList<>();
        for (Mode m : Mode.values()) {
            leg(m, year, la1, lo1, la2, lo2, sea).ifPresent(out::add);
        }
        out.sort((a, b) -> Double.compare(a.days(), b.days()));
        return out;
    }

    /**
     * "in about nine days", "in about three weeks", "the same day". Under half a day is the same
     * day; under a day and a half is the next day.
     */
    public static String phrase(double days) {
        if (days <= 0.0001d) {
            return "the same day";
        }
        if (days < 0.5d) {
            return "within the hour";
        }
        if (days < 1.5d) {
            return "the next day";
        }
        if (days < 14.0d) {
            return "in about " + (int) Math.round(days) + " days";
        }
        if (days < 60.0d) {
            return "in about " + (int) Math.round(days / 7.0d) + " weeks";
        }
        if (days < 730.0d) {
            return "in about " + (int) Math.round(days / 30.0d) + " months";
        }
        return String.format(Locale.ROOT, "in about %.1f years", days / YEAR);
    }
}

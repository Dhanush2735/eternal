package com.eternalengine.reputation;

import com.eternalengine.civilization.CountryGrid;
import com.eternalengine.news.NewsTravel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Step 42 (F2, reputation per land): <b>what a land thinks of you, and how far the tale has run</b>.
 *
 * <p>F2 asks for reputation <b>per land</b>. Step 40 had already built the <i>sides</i> half of F2
 * ({@code war/Sides}, 14 kinds of party), so what is left is reputation and loyalty, and the shape
 * of what was there: {@code ReputationEngine}'s 21 dimensions are <b>one global number per player
 * with no place and no year</b>. A village you have never visited feels about you exactly as your
 * home village does, and a man in 3000 BC feels exactly as a man in 1900.
 *
 * <h3>The model</h3>
 *
 * <p>A <b>deed</b> is a thing done, <i>where</i>, <i>when</i>, and what it was worth. Nothing else
 * is stored. A land's opinion is then <b>derived</b> and never written down, from two rules:
 *
 * <ul>
 *   <li><b>Fame decays.</b> An opinion halves every {@value #HALF_LIFE_YEARS} years — not because a
 *       source says so, but because that is about as long as a man who saw the thing is still there
 *       to swear to it. This is a modelling choice and is named as one.</li>
 *   <li><b>Fame travels, and not faster than news.</b> A deed is worth nothing where it has not
 *       been heard. The arrival is not guessed: it is {@code NewsTravel.fastest(...)}, the step-37
 *       register that knows how fast a thing could actually have gone that year — the post from
 *       1490, the telegraph where the wire had reached, a traveller's feet before either.</li>
 * </ul>
 *
 * <p>So a deed done in Bordeaux in 1400 is <b>not</b> known in Constantinople in 1400, is known
 * there later and weaker than at home, and is forgotten in Bordeaux by about 1500. That is the
 * whole of "reputation per land", and it is why the store is small: deeds, not a number per land
 * per year.
 *
 * <h3>Why the land and not the state</h3>
 *
 * <p>A land is a {@code CountryGrid} country: 258 of them, and they endure. The states on them do
 * not — {@code RulerRegister} knows 1,581 polities over 5,600 years, and keying reputation to a
 * polity would mean a deed done in the Kingdom of France was unknown in the French Republic, three
 * fields away. Reputation belongs to the ground. <b>Loyalty is the other way round</b> and is owed
 * to a ruler, so it is keyed by party and lives beside this in {@code LandReputation}.
 */
public final class Fame {

    private Fame() {}

    /**
     * An opinion halves every this many years.
     *
     * <p><b>Not from a source.</b> No table was read for this; it is the length of time a man who
     * saw the deed is likely still to be believed, and it is named here so that it can be argued
     * with. Everything else in this step is read from somewhere; this number is not.
     */
    public static final int HALF_LIFE_YEARS = 30;

    /**
     * How far a tale runs before it is half as strong: about the width of Europe.
     *
     * <p><b>Also not from a source</b>, and for the same reason. Fame is full where the thing was
     * done and half as strong two thousand kilometres off, which is roughly the difference between
     * a deed the whole province talks about and one a traveller mentions once.
     */
    public static final double HALF_REACH_KM = 2000.0d;

    /**
     * An oath halves every this many years: twice a name's, because a tale needs a witness and a
     * promise does not. <b>Not from a source either</b>, for the same reason as the other.
     */
    public static final int OATH_HALF_LIFE_YEARS = 60;

    public static final int MIN = -100;
    public static final int MAX = 100;

    /** Days in a year, for turning a journey of days into a year it lands in. */
    public static final double DAYS_PER_YEAR = 365.25d;

    /**
     * One thing done.
     *
     * @param delta what it was worth: positive for praise, negative for blame, -100..100
     */
    public record Deed(String what, int year, double lat, double lon, int delta) {

        public boolean isPraise() {
            return delta > 0;
        }

        /** "saved the bridge at 44.8, -0.6 in 1400, worth +30". */
        public String line() {
            return (what == null || what.isBlank() ? "a deed" : what) + " at "
                    + String.format(Locale.ROOT, "%.1f, %.1f", lat, lon) + " in " + year
                    + ", worth " + (delta > 0 ? "+" : "") + delta;
        }

        /** The land it happened in, which is where it is known best. */
        public String land() {
            return CountryGrid.get().codeAt(lat, lon);
        }
    }

    // ------------------------------------------------------------------ decay

    /**
     * What an opinion is worth after this long.
     *
     * <p>Towards nought and never through it: a good name fades to being unknown, it does not turn
     * into a bad one.
     */
    public static int decay(int value, int fromYear, int toYear) {
        return decay(value, fromYear, toYear, HALF_LIFE_YEARS);
    }

    /**
     * The same, at a half-life of the caller's choosing.
     *
     * <p>Loyalty uses a longer one: a man's name fades faster than his oath, because a tale needs
     * someone still alive to tell it and an oath does not.
     */
    public static int decay(int value, int fromYear, int toYear, int halfLifeYears) {
        if (value == 0 || toYear <= fromYear || halfLifeYears <= 0) {
            return value;
        }
        double halves = (double) (toYear - fromYear) / (double) halfLifeYears;
        double kept = Math.pow(0.5d, halves);
        return (int) Math.round(value * kept);
    }

    // ------------------------------------------------------------------ travel

    /** Kilometres between two points on the sphere. */
    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * The year the tale of this deed lands at this point, or {@code Integer.MAX_VALUE} when nothing
     * that year could carry it.
     *
     * <p><b>On the rumour channel and not the post.</b> {@code NewsTravel.fastest} is how quickly a
     * king's dispatch could go — and in 1400 that gets a rider from Bordeaux to Sydney in ten
     * months, so at the year grain this mod works at, everything arrives the same year and the
     * whole question of travel disappears. That is not how a reputation spreads. A reputation is
     * what people say to each other, so this asks {@code NewsTravel.traveller}: <i>"how long news
     * takes that nobody is carrying on purpose... the one rumours run on"</i>. That gives three
     * years from Bordeaux to Beijing in 1400, five to Sydney, and two weeks to Constantinople once
     * there are coaches — which is the difference between a name known across the world and a name
     * known in the next valley, and is exactly what F2 is asking about.
     *
     * <p>A journey of days is rounded down to whole years, because a year is the grain the whole
     * mod works at.
     */
    public static int arrivalYear(Deed deed, double lat, double lon) {
        if (deed == null) {
            return Integer.MAX_VALUE;
        }
        NewsTravel.Leg leg = NewsTravel.traveller(deed.year(), deed.lat(), deed.lon(), lat, lon, false);
        if (leg == null) {
            return Integer.MAX_VALUE;
        }
        return deed.year() + (int) Math.floor(leg.days() / DAYS_PER_YEAR);
    }

    /** Whether the tale has got here by this year. */
    public static boolean heard(Deed deed, double lat, double lon, int year) {
        return deed != null && deed.year() <= year && year >= arrivalYear(deed, lat, lon);
    }

    /**
     * How strongly a deed counts here, 0 to 1: nought where it has not been heard, full where it
     * was done, and falling with distance from there.
     *
     * <p>Distance and not a border, because a tale does not stop at a line — and because a land is
     * compact enough that "known in France" falls out of "known near where it happened" without a
     * second rule.
     */
    public static double reach(Deed deed, double lat, double lon, int year) {
        if (!heard(deed, lat, lon, year)) {
            return 0.0d;
        }
        double d = km(deed.lat(), deed.lon(), lat, lon);
        return 1.0d / (1.0d + d / HALF_REACH_KM);
    }

    // ------------------------------------------------------------------ the opinion

    /**
     * What they think of you at this point in this year: every deed that has been heard here,
     * decayed to now and discounted by how far the tale has come.
     *
     * <p>Clamped to {@value #MIN}..{@value #MAX}: a land that already hates you cannot hate you
     * more, and the same in the other direction.
     */
    public static int opinion(List<Deed> deeds, double lat, double lon, int year) {
        if (deeds == null || deeds.isEmpty()) {
            return 0;
        }
        double sum = 0.0d;
        for (Deed d : deeds) {
            if (d == null || d.year() > year) {
                continue;                          // a deed not yet done is not yet known
            }
            sum += decay(d.delta(), d.year(), year) * reach(d, lat, lon, year);
        }
        return clamp((int) Math.round(sum));
    }

    /** The same, asked of a land: the opinion at the middle of it. */
    public static int landOpinion(List<Deed> deeds, String landCode, int year) {
        CountryGrid grid = CountryGrid.get();
        int i = grid.indexOf(landCode);
        if (i < 0) {
            return 0;
        }
        return opinion(deeds, grid.mainLat(i), grid.mainLon(i), year);
    }

    /** The lands that have any opinion of you at all, strongest first, for a command or a check. */
    public static List<String> lands(List<Deed> deeds, int year) {
        Set<String> seen = new LinkedHashSet<>();
        if (deeds != null) {
            for (Deed d : deeds) {
                if (d != null && d.year() <= year) {
                    String land = d.land();
                    if (land != null && !land.isEmpty()) {
                        seen.add(land);
                    }
                }
            }
        }
        List<String> out = new ArrayList<>(seen);
        out.sort((a, b) -> Integer.compare(Math.abs(landOpinion(deeds, b, year)),
                Math.abs(landOpinion(deeds, a, year))));
        return out;
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    // ------------------------------------------------------------------ the words

    /** What a number means, for a line of output. */
    public static String phrase(int value) {
        if (value >= 75) {
            return "they tell stories about you";
        }
        if (value >= 40) {
            return "you are well spoken of";
        }
        if (value >= 15) {
            return "you are spoken of kindly";
        }
        if (value > -15) {
            return "nobody here has settled an opinion of you";
        }
        if (value > -40) {
            return "you are spoken of unkindly";
        }
        if (value > -75) {
            return "you are ill spoken of";
        }
        return "your name is a curse here";
    }
}

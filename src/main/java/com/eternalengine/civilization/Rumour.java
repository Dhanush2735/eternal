package com.eternalengine.civilization;

import com.eternalengine.news.NewsTravel;

import java.util.Locale;

/**
 * Step 37 (D8): <b>a rumour passed from person to person</b>.
 *
 * <p>{@link EventNews#rumor} writes the line a villager says about something that happened. What
 * it has never asked is the two questions that make a rumour a rumour rather than a report:
 * <b>how long it took to get here</b>, and <b>what is left of it by the time it arrives</b>.
 *
 * <p>The first is {@link NewsTravel}: a rumour rides the incidental channel, because nobody is
 * carrying it on purpose. It goes with a carter, a pilgrim, a soldier going home, a pedlar with a
 * pack, and it arrives when they do.
 *
 * <p>The second is the ladder below. Every hop — roughly a day's walk, one village to the next —
 * costs something. A thing seen is a thing seen; a thing heard from the man who saw it is nearly
 * as good; a thing heard in a market three valleys away is a story; and a thing that has crossed
 * a continent is a rumour of something, more or less as it was told, and nobody can swear to any
 * of it.
 *
 * <pre>
 *   0-1 hop    Have you heard? X.
 *   2-3        They say X.
 *   4-7        It is said that X - though nobody here has seen it.
 *   8-15       There is a story going about, of X. Whether it is true, who can say.
 *   16 or more Word comes of something that happened away over there. They call it X, or some such.
 * </pre>
 *
 * <p>And when the journey has taken more than two years, the thing is over by the time you hear
 * of it, and the line says so — which is the ordinary condition of most news for most of history.
 */
public final class Rumour {

    /** A day's walk: how far a rumour hops, from one village to the next. */
    public static final double HOP_KM = 25.0d;

    /** Longer than this and whatever it was is over. */
    public static final double STALE_DAYS = 730.0d;

    private Rumour() {}

    /**
     * A rumour that has arrived.
     *
     * @param km     how far it came
     * @param days   how long it took
     * @param hops   how many times it changed hands
     * @param text   the line a villager says
     * @param howLong "in about nine days", "in about two years"
     * @param stale  true where it took long enough that the thing is over
     */
    public record Carried(double km, double days, int hops, String text, String howLong,
                          boolean stale) {

        /** "They say X - nine days on the road." */
        public String line() {
            return text + " " + Capitalise.of("it came " + howLong.replace("in about ", "") + ".")
                    .replace("it came the same day.", "it came the very day.");
        }
    }

    /**
     * Carry a thing from where it happened to here.
     *
     * @param what    the name of the thing, as the record spells it
     * @param year    the year it happened
     * @param sea     true when the road between the two places is over water
     */
    public static Carried carried(String what, int year, double fromLat, double fromLon,
                                  double toLat, double toLon, boolean sea) {
        NewsTravel.Leg leg = NewsTravel.traveller(year, fromLat, fromLon, toLat, toLon, sea);
        double days = leg == null ? Double.NaN : leg.days();
        double km = com.eternalengine.technology.TechnologyAtlas.kmBetween(fromLat, fromLon,
                toLat, toLon) * (sea ? NewsTravel.SEA_DETOUR : 1.0d);
        int hops = (int) Math.floor(km / HOP_KM);
        String howLong = NewsTravel.phrase(days);
        String text = told(what, hops);
        boolean stale = days > STALE_DAYS;
        if (stale) {
            text = text + " Long over by now, whatever it was.";
        }
        return new Carried(km, days, hops, text, howLong, stale);
    }

    /**
     * What is left of a thing after this many changes of hands. A number in the name goes first:
     * "three hundred died" becomes "a great many died", because a round number is the first thing
     * a retelling changes.
     */
    public static String told(String what, int hops) {
        String name = what == null || what.isBlank() ? "something that happened" : what.trim();
        if (hops >= 16) {
            name = roundNumbers(name);
            return "Word comes of something that happened away over there. They call it "
                    + name + ", or some such.";
        }
        if (hops >= 8) {
            name = roundNumbers(name);
            return "There is a story going about, of " + name
                    + ". Whether it is true, who can say.";
        }
        if (hops >= 4) {
            return "It is said that " + lower(name) + " - though nobody here has seen it.";
        }
        if (hops >= 2) {
            return "They say " + lower(name) + ".";
        }
        return "Have you heard? " + name + ".";
    }

    /** "Three hundred died" to "a great many died": the first thing a retelling changes. */
    public static String roundNumbers(String name) {
        String out = name;
        String[] words = out.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            String bare = w.replaceAll("[^\\w'-]", "");
            if (!bare.isEmpty() && Character.isDigit(bare.charAt(0))) {
                sb.append("a great many");
            } else {
                sb.append(w);
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private static String lower(String s) {
        if (s.isEmpty()) {
            return s;
        }
        String head = s.substring(0, 1);
        return head.toLowerCase(Locale.ROOT).equals(head) ? head + s.substring(1)
                : head.toLowerCase(Locale.ROOT) + s.substring(1);
    }

    /** A capital letter, for starting a sentence. */
    static final class Capitalise {
        static String of(String s) {
            if (s == null || s.isEmpty()) {
                return s;
            }
            return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
        }
    }
}

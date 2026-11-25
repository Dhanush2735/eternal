package com.eternalengine.news;

import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.technology.TechnologyAtlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 37 (C23 news by era): <b>how the news reaches people here, in this year</b>, and how old it
 * is when it gets to them.
 *
 * <p>Step 19d's {@link NewsEras} already knew three ways: a crier calling it in the street, a
 * newsletter written by hand, a paper printed. This adds what was missing — the inscription on a
 * wall, the wire, the wireless and the television — and, more importantly, it says for each one
 * <b>how old the news is by the time you hear it</b>, which is the part that makes an era feel
 * like an era. A stone that says who won was carved months afterwards and will say so for a
 * thousand years; a wireless bulletin is as old as the minute you are in.
 *
 * <pre>
 *   inscription   cut in stone     writing, and a ruler to pay for it; the news is months old
 *   crier         called out      always, where there is a state to announce; a day or two old
 *   newsletter    written by hand  step 19d's regions, from 1566; weeks old
 *   paper         printed         step 19d's regions; days old by post, minutes by wire
 *   wire          telegraphed     the telegraph, from 1839; a day
 *   wireless      radio           from 1919; the same hour
 *   television    on television   from 1935; the same hour, with pictures
 * </pre>
 *
 * <p><b>Which table governs which.</b> Papers and newsletters are governed by {@link NewsEras},
 * which is a hand-built table of the year the first paper appeared in each region, sourced to the
 * history of the press. The wire, the wireless and the television have no such table, so they are
 * governed by step 30a's technology atlas and the year each reached the country. Where the two
 * could disagree, the hand table wins for papers, because it is the one with a source.
 *
 * <p>Not every channel reaches everywhere. An inscription stands in a capital and says what the
 * king wants said; a crier reaches anyone within earshot; a wireless reaches a farmhouse with a
 * set in the corner. Villages never get a paper, and never did.
 */
public final class NewsChannels {

    /** Who a channel reaches. */
    public enum Reach {
        /** Only the capital and the great cities: what a ruler puts up. */
        CAPITAL,
        /** Cities, and towns some years later. */
        TOWNS,
        /** Anyone at all, at once, given the thing to hear it on. */
        EVERYWHERE,
        /** Anyone within earshot of the crier. */
        EARSHOT
    }

    /** How the news reaches people. */
    public enum Channel {
        /** Cut in stone, or into a wall: what a ruler wants remembered. Months old when it is
         * cut, and it says so for a thousand years. */
        INSCRIPTION("cut in stone", Reach.CAPITAL, "writing", -3200, 8.0d, 30.0d, 1),
        /** Called out in the street. Always, where there is a state with something to announce. */
        CRIER("called in the street", Reach.EARSHOT, null, Integer.MIN_VALUE, 30.0d, 1.0d, 4),
        /** A newsletter written by hand and sold in the city: step 19d's years, from 1566. */
        NEWSLETTER("written by hand", Reach.CAPITAL, null, 1566, 90.0d, 3.0d, 3),
        /** A printed paper: step 19d's years, from 1605 in Strasbourg. */
        NEWSPAPER("printed", Reach.TOWNS, null, 1605, 180.0d, 1.0d, 8),
        /** Down the wire, from 1839: a day, whatever the distance. */
        WIRE("down the wire", Reach.TOWNS, "telegraph", 1839, 0.0d, 1.0d, 6),
        /** On the wireless, from 1919: the same hour, in every house with a set. */
        WIRELESS("on the wireless", Reach.EVERYWHERE, "radio", 1919, 0.0d, 0.0d, 6),
        /** On television, from 1935: the same hour, with pictures. */
        TELEVISION("on television", Reach.EVERYWHERE, "television", 1935, 0.0d, 0.0d, 6);

        private final String phrase;
        private final Reach reach;
        private final String tech;
        private final int first;
        private final double kmPerDay;
        private final double lagDays;
        private final int stories;

        Channel(String phrase, Reach reach, String tech, int first, double kmPerDay,
                double lagDays, int stories) {
            this.phrase = phrase;
            this.reach = reach;
            this.tech = tech;
            this.first = first;
            this.kmPerDay = kmPerDay;
            this.lagDays = lagDays;
            this.stories = stories;
        }

        /** "printed", "cut in stone", for a sentence. */
        public String phrase() {
            return phrase;
        }

        public Reach reach() {
            return reach;
        }

        /** The technology this channel needs from the atlas, or null where a table governs it. */
        public String techId() {
            return tech;
        }

        /** The year it first existed anywhere. */
        public int firstYear() {
            return first;
        }

        /** How many separate items it carries: a stone carries one, a paper eight. */
        public int stories() {
            return stories;
        }
    }

    private NewsChannels() {}

    /**
     * Is this channel here, in this year? The wire, the wireless and the television ask the
     * technology atlas when they reached the country; papers and newsletters ask step 19d's
     * table; a crier needs a state with something to announce; an inscription needs writing and a
     * capital to stand in.
     */
    public static boolean here(Channel c, SettlementRank rank, boolean ruled, double lat,
                               double lon, int year) {
        if (rank == null) {
            rank = SettlementRank.VILLAGE;
        }
        switch (c) {
            case CRIER -> {
                return ruled;
            }
            case INSCRIPTION -> {
                if (!ruled || rank != SettlementRank.CITY) {
                    return false;
                }
                TechnologyAtlas.Tech writing = TechnologyAtlas.get().tech("writing");
                return writing != null
                        && TechnologyAtlas.get().arrivalAt(writing, lat, lon) <= year;
            }
            case NEWSLETTER -> {
                return rank == SettlementRank.CITY && NewsEras.handwritten(lat, lon, year);
            }
            case NEWSPAPER -> {
                return year >= NewsEras.printYear(rank, lat, lon);
            }
            default -> {
                if (c.tech == null) {
                    return false;
                }
                TechnologyAtlas.Tech t = TechnologyAtlas.get().tech(c.tech);
                if (t == null) {
                    return false;
                }
                int arrived = TechnologyAtlas.get().arrivalAt(t, lat, lon);
                if (arrived == TechnologyAtlas.NEVER || arrived > year) {
                    return false;
                }
                return c.reach() == Reach.EVERYWHERE
                        || rank == SettlementRank.CITY
                        || (rank == SettlementRank.TOWN && year >= arrived + 15);
            }
        }
    }

    /** Every channel that reaches this place in this year, richest last. */
    public static List<Channel> inUse(SettlementRank rank, boolean ruled, double lat, double lon,
                                      int year) {
        List<Channel> out = new ArrayList<>();
        for (Channel c : Channel.values()) {
            if (here(c, rank, ruled, lat, lon, year)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * The channel a caller should use: the newest thing that reaches here. A village with a
     * wireless gets the wireless; the same village in 1600 gets the crier; a city in 1700 gets
     * the paper and the crier both, and the paper carries the more.
     */
    public static Channel main(SettlementRank rank, boolean ruled, double lat, double lon,
                               int year) {
        Channel best = null;
        for (Channel c : Channel.values()) {
            if (here(c, rank, ruled, lat, lon, year)) {
                best = c;
            }
        }
        return best;
    }

    /**
     * How old a story is, in days, by the time this channel brings it from {@code km} away.
     * Months for a stone, a day for the wire, the same hour for the wireless.
     */
    public static double ageDays(Channel c, double km) {
        if (c.kmPerDay <= 0.0d) {
            return c.lagDays;
        }
        return c.lagDays + km / c.kmPerDay;
    }

    /** How old a story is, in words: "three weeks old", "the same hour". */
    public static String ageText(Channel c, double km) {
        double days = ageDays(c, km);
        if (days <= 0.0001d) {
            return "the same hour";
        }
        if (days < 1.5d) {
            return "a day old";
        }
        if (days < 14.0d) {
            return (int) Math.round(days) + " days old";
        }
        if (days < 70.0d) {
            return (int) Math.round(days / 7.0d) + " weeks old";
        }
        if (days < 730.0d) {
            return (int) Math.round(days / 30.0d) + " months old";
        }
        return String.format(Locale.ROOT, "%.0f years old", days / 365.25d);
    }

    /** "The news here is printed", for a card or a tooltip. */
    public static String describe(SettlementRank rank, boolean ruled, double lat, double lon,
                                  int year) {
        Channel c = main(rank, ruled, lat, lon, year);
        return c == null ? "no news reaches here" : "the news here is " + c.phrase();
    }
}

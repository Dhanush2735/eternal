package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.eternalengine.clock.Timeline;

/**
 * <b>F11: errands tied to real history.</b> Work a player can do that only exists because a real thing is
 * really there, in the year they are standing in it.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A quest in most games is authored: somebody writes a list of tasks and the world is arranged around
 * them. This project has 23,535 famous buildings with dated lives and 184,060 people with dated lives, so
 * the errands do not have to be authored. **They can be read out of the record**: a letter can be carried
 * to a building that stands this year, a scaffold can be helped with on a building that is going up this
 * decade, and a person can be met in the years they were alive.
 *
 * <p>The rule that makes it work is the same one that makes the rest of the project honest: <b>an errand
 * may only be offered if the record says the thing is there</b>. No letter to the Red Fort in 1600, when
 * it was begun in 1639. Nobody to meet in 2400, because nobody is recorded there.
 *
 * <h2>What an errand is not</h2>
 *
 * <p>It is not a reward economy. There is no pay in coins, because a price is one of the four things this
 * project refuses to invent; an errand pays in <b>deeds</b>, the same five counters that
 * {@link PlayerRoles} reads, so doing errands is how a player becomes a merchant or a scholar or a
 * courtier. And <b>an errand is not history</b>: carrying one letter is not a line in a chronicle, and
 * {@link Errand#whyNotHistory()} says so on every one.
 */
public final class Errands {

    /** A dated building. `to` is null for one that still stands. */
    public record Landmark(String name, String kind, double lat, double lon, int from, Integer to) {

        public Landmark {
            name = name == null ? "" : name;
            kind = kind == null || kind.isEmpty() ? "building" : kind;
        }

        /** True in the years the record says it stood. Before `from` it is a building site at best. */
        public boolean standsIn(int year) {
            if (year < from) return false;
            return to == null || year <= to;
        }

        /** True while it is being built: the twenty-five years before it opens. */
        public boolean buildingIn(int year) {
            return year >= from - 25 && year < from;
        }
    }

    /** A dated person. */
    public record Life(String name, String what, int born, int died, double lat, double lon) {

        public Life {
            name = name == null ? "" : name;
            what = what == null ? "" : what;
        }

        public boolean aliveIn(int year) {
            return year >= born && year <= died;
        }
    }

    /** What kind of work it is. Three, and F11 names two of them. */
    public enum Kind {
        /** Carry a letter or a word to a building that stands this year. */
        DELIVER("carry a letter"),
        /** Help with a building that is going up. */
        HELP_BUILD("help build"),
        /** Find a person in the years they were alive. */
        MEET("find somebody");

        private final String words;

        Kind(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** The `to` of a thing that still stands, or a person who is still alive: no end year in the record. */
    public static final int OPEN = Integer.MAX_VALUE;

    /**
     * One piece of work, with everything a caller needs to offer it and nothing more.
     *
     * <p>{@code from} and {@code to} are <b>the dates of the thing the errand is about</b>, carried on the
     * errand itself. The first version of this record did not carry them and the part 6 probe could only
     * check the rule by matching on a name, which is ambiguous: two buildings in two cities can share
     * one. A caller that wants to verify an offer should never have to guess which landmark was meant.
     */
    public record Errand(Kind kind, String title, String body, String where, double km, int year,
                         String record, int from, int to) {

        /** True when the thing this errand is about exists in the year the errand is offered. */
        public boolean dated() {
            return from <= year && year <= to;
        }

        /** How far it is, in the words a player would use. */
        public String distance() {
            if (km < 2) return "here";
            if (km < 25) return "a walk away, " + Math.round(km) + " km";
            if (km < 120) return "a day's travel, " + Math.round(km) + " km";
            return Math.round(km) + " km away";
        }

        /** What doing it is worth, in the counters a role is read from. Never money. */
        public PlayerRoles.Deeds earned(int deedYear) {
            return switch (kind) {
                case DELIVER -> new PlayerRoles.Deeds(0, 0, 1, 0, false, deedYear);
                case HELP_BUILD -> new PlayerRoles.Deeds(1, 0, 0, 0, false, deedYear);
                case MEET -> new PlayerRoles.Deeds(0, 0, 0, 1, false, deedYear);
            };
        }

        /** Always false, and the sentence says why, because a player will ask. */
        public boolean records() {
            return false;
        }

        public String whyNotHistory() {
            return "This is not history and will not be written down: one letter carried, one day on a "
                    + "scaffold and one conversation are real work and they are not events. What they do "
                    + "is make you into something, and the chronicle only records what the world did.";
        }

        public String line() {
            return title + " at " + where + " (" + distance() + "). " + body;
        }
    }

    /** The furthest a letter is worth carrying, in km. Beyond this the errand is not offered. */
    public static final double REACH_KM = 1500;
    /** How many errands are offered at once, so a screen is a choice and not a list. */
    public static final int OFFER = 3;
    /** How many years ahead a building counts as going up. */
    public static final int BUILD_WINDOW = 25;

    private Errands() {
    }

    // ------------------------------------------------------------------ the facts an offer needs

    /** The sentence for work on a building going up, in one place so both entry points say the same. */
    static String buildBody(Landmark l) {
        return l.name() + " is going up and the record dates it to " + l.from() + ", so the scaffolding "
                + "is up now and there is a stone to be carried. A day of that is a day of the building.";
    }

    /** Great-circle distance in km, good enough to say "a day's travel" and not good enough to navigate. */
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
     * Is this kind of errand possible at all in that year? Past the seam the record has no people in it,
     * so a task that needs one is not offered rather than invented.
     */
    public static boolean possible(Kind kind, int year) {
        if (!Timeline.inSpan(year)) return false;
        if (kind == Kind.MEET) return year <= Timeline.RECORDED_UNTIL;
        return true;
    }

    /** The sentence for a kind that is not possible, which is a refusal a player will hit. */
    public static String impossibleBecause(Kind kind, int year) {
        if (!Timeline.inSpan(year)) {
            return "this world runs from " + Timeline.FIRST_YEAR + " to AD " + Timeline.LAST_YEAR;
        }
        if (kind == Kind.MEET) {
            return "nobody is recorded after AD " + Timeline.RECORDED_UNTIL + ", and this project does "
                    + "not invent people, so there is nobody to be sent to find. There is work in the "
                    + "buildings that still stand and in the towns people build themselves.";
        }
        return "possible";
    }

    // ------------------------------------------------------------------ offering work

    /**
     * The work available in a year at a place: three errands at most, nearest first, each one justified by
     * a line of the record.
     */
    public static List<Errand> offers(int year, double lat, double lon, List<Landmark> landmarks,
                                      List<Life> lives) {
        List<Errand> out = new ArrayList<>();
        if (!Timeline.inSpan(year)) return out;

        for (Landmark l : landmarks) {
            double d = km(lat, lon, l.lat(), l.lon());
            if (d > REACH_KM) continue;
            if (l.standsIn(year) && possible(Kind.DELIVER, year)) {
                out.add(new Errand(Kind.DELIVER, "A letter for the " + l.kind(),
                        "It is written, sealed and addressed to whoever keeps the " + l.kind() + " at "
                                + l.name() + ". The record says it has stood there since " + l.from()
                                + (l.to() == null ? " and that it stands." : " until " + l.to() + "."),
                        l.name(), d, year, l.name() + " (" + l.kind() + ", from " + l.from() + ")",
                        l.from(), l.to() == null ? OPEN : l.to()));
            } else if (l.buildingIn(year) && possible(Kind.HELP_BUILD, year)) {
                out.add(new Errand(Kind.HELP_BUILD, "Work on the " + l.kind(), buildBody(l),
                        l.name(), d, year,
                        l.name() + " (begun " + l.from() + ")", l.from(), l.to() == null ? OPEN : l.to()));
            }
        }

        if (possible(Kind.MEET, year)) {
            for (Life p : lives) {
                if (!p.aliveIn(year)) continue;
                double d = km(lat, lon, p.lat(), p.lon());
                if (d > REACH_KM) continue;
                out.add(new Errand(Kind.MEET, "Word with " + p.name(),
                        (p.what().isEmpty() ? p.name() : p.name() + ", " + p.what()) + ", is alive: "
                                + p.born() + " to " + p.died() + ". They are in the record for these "
                                + "years and this is one of them.", p.name(), d, year,
                        p.name() + " (" + p.born() + " to " + p.died() + ")", p.born(), p.died()));
            }
        }

        out.sort(Comparator.comparingDouble(Errand::km));
        return out.size() <= OFFER ? out : new ArrayList<>(out.subList(0, OFFER));
    }

    /** Every errand available, unsorted and uncapped, for a probe or a map. */
    public static List<Errand> allOffers(int year, double lat, double lon, List<Landmark> landmarks,
                                         List<Life> lives) {
        List<Errand> out = new ArrayList<>();
        for (Landmark l : landmarks) {
            double d = km(lat, lon, l.lat(), l.lon());
            if (d > REACH_KM) continue;
            int to = l.to() == null ? OPEN : l.to();
            if (l.standsIn(year)) {
                out.add(new Errand(Kind.DELIVER, "A letter for the " + l.kind(),
                        "the record says it has stood there since " + l.from() + ".", l.name(), d, year,
                        l.name(), l.from(), to));
            } else if (l.buildingIn(year)) {
                out.add(new Errand(Kind.HELP_BUILD, "Work on the " + l.kind(), buildBody(l),
                        l.name(), d, year, l.name(), l.from(), to));
            }
        }
        if (possible(Kind.MEET, year)) {
            for (Life p : lives) {
                if (!p.aliveIn(year)) continue;
                double d = km(lat, lon, p.lat(), p.lon());
                if (d > REACH_KM) continue;
                out.add(new Errand(Kind.MEET, "Word with " + p.name(), "alive " + p.born() + " to "
                        + p.died() + ".", p.name(), d, year, p.name(), p.born(), p.died()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ what is refused, and why

    /** The errands a designer will want and this project will not offer. */
    public enum Refused {
        A_LETTER_TO_A_BUILDING_NOT_YET_BEGUN("The Red Fort in 1600 is a field. An errand that sends a "
                + "player to a building the record dates to 1648 is the anachronism this whole project "
                + "exists to avoid, and it is the first thing anybody will ask for. The year is the "
                + "pack's own, not one remembered: this refusal first said 1639 and the pack says 1648."),
        A_PERSON_AFTER_THE_SEAM("No person is recorded after AD 2026, so there is nobody to send anybody "
                + "to find. Inventing a contact would be inventing a person, which the guardrail "
                + "refuses."),
        A_REWARD_IN_MONEY("An errand pays in deeds and not in coins. A price is one of the four things "
                + "this project refuses to invent, and a pay rate for a fictional job is exactly that."),
        A_TASK_WITH_NO_RECORD("Every errand carries the line of the record it came from. A task with "
                + "nothing behind it is the authored quest list this project does not want."),
        A_LETTER_ACROSS_THE_WORLD("Beyond " + (int) REACH_KM + " km a letter is a year's journey and a "
                + "different game. The errand is not offered rather than offered and then failed.");

        private final String why;

        Refused(String why) {
            this.why = why;
        }

        public String why() {
            return why;
        }
    }

    /** One line, for a screen, that says what an errand is. */
    public static String ruleLine(int year) {
        return "Work here is read out of the record for " + year + ": a building that stands, a building "
                + "going up, or a person alive this year. It pays in what you learn to do, not in coin.";
    }

    /** A one-line summary of how much work a year offers, for a log or a probe. */
    public static String summary(int year, double lat, double lon, List<Landmark> landmarks,
                                 List<Life> lives) {
        List<Errand> offers = offers(year, lat, lon, landmarks, lives);
        if (offers.isEmpty()) {
            return "No work at " + Math.round(lat) + "," + Math.round(lon) + " in " + year
                    + ": nothing in the record is within " + (int) REACH_KM + " km.";
        }
        StringBuilder b = new StringBuilder();
        b.append(offers.size()).append(" errand").append(offers.size() == 1 ? "" : "s")
                .append(" in ").append(year).append(": ");
        for (int i = 0; i < offers.size(); i++) {
            if (i > 0) b.append("; ");
            b.append(offers.get(i).kind().name().toLowerCase(Locale.ROOT)).append(" at ")
                    .append(offers.get(i).where());
        }
        return b.append('.').toString();
    }
}

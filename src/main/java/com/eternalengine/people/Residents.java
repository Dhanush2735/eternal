package com.eternalengine.people;

import com.eternalengine.civilization.Presence;
import com.eternalengine.knowledge.CardStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Step R3-a: <b>who is actually here, by name</b>.
 *
 * <h3>What was missing</h3>
 *
 * <p>Two registers already answer "who was here": {@code FamousPeople} (born here) and step 41's
 * {@link Presence} (was here, and why). Nothing in the mod ever turned either of them into a
 * <b>person standing in front of the player</b>. {@code vip/VipSystem} registers two claimable
 * slots, which is not a world.
 *
 * <h3>What this is, and what it deliberately is not</h3>
 *
 * <p>It is the join, and only the join: a presence stay carries a {@code qid} and no name; a card
 * carries the name. Ask {@link #hereIn} and you get <b>named residents of a place in a year, most
 * famous first, each with the reason the record puts them there</b> — "lived here", "worked here",
 * "died here".
 *
 * <p><b>It is not a spawner and it does not touch the crowd.</b> This class reads two registers and
 * returns a list; nothing in it creates an entity, changes a head count, a spacing or a spread. The
 * body that stands these people up is a separate step and has to be a separate entity path, because
 * a named resident is not a crowd figure and must never be one.
 *
 * <h3>Two rules, both inherited</h3>
 *
 * <p><b>A person born here is reported, and marked.</b> Presence's own rule is that "from here" and
 * "here now" are different answers. Both are useful to a scene — the man born in this house in 1591
 * is as much of the place as the governor who died here — so neither is dropped; the reason is
 * carried on every row and {@link #notBornHere} filters to the ones who <em>came</em>.
 *
 * <p><b>A body seated here is not a person standing here.</b> Five of Presence's ten reasons mean
 * "a body this person worked for, served in, belonged to or founded was <em>seated</em> here" — the
 * register says so itself in its own gaps list. A member of the Royal Society is in London because
 * the Society is; he may have read the proceedings in Cumbria. Leaving those rows in would stand
 * Isaac Newton in London in 1666, which is false: he was at Woolsthorpe and Cambridge that year.
 * So {@link #physicallyHere} admits only the five reasons that mean the person was on the ground,
 * and {@link #hereInBody} is the list a scene may cast from. The looser {@link #hereIn} stays, for
 * a card that is asking "who is this place known for" rather than "who is in this street".
 *
 * <p><b>An undated stay says so.</b> 32% of presence rows carry the person's own birth and death
 * years because nobody dated them. Those rows come back with {@link Resident#estimated()} true, and
 * {@link Resident#line()} writes "at some time in his life" rather than a year nobody gave it.
 */
public final class Residents {

    /** How near a stay has to be to count as "here" when a place is asked who is in it. */
    public static final double HERE_KM = 25.0d;

    /** Never hand a scene an unbounded list: the front of it is the famous end. */
    public static final int MAX = 64;

    /**
     * One named person the record places here. {@code why} is one of {@link Presence}'s reasons;
     * {@code estimated} means the years are the person's own life, not a dated residence.
     */
    public record Resident(String name, int qid, int fame, double lat, double lon, String place,
                           int from, int to, int why, boolean estimated, double km) {

        /** Why the record puts them here, in words a player reads. */
        public String reason() {
            return switch (why) {
                case Presence.BORN -> "born here";
                case Presence.DIED -> "died here";
                case Presence.STUDIED -> "studied here";
                case Presence.WORKED_FOR -> "worked for a body seated here";
                case Presence.SERVED_IN -> "served in a body seated here";
                case Presence.MEMBER_OF -> "was a member of a body seated here";
                case Presence.PARTY -> "was of the party seated here";
                case Presence.FOUNDED -> "founded the body seated here";
                case Presence.LIVED_AT -> "lived here";
                case Presence.WORKED_IN -> "worked here";
                default -> "was here";
            };
        }

        /**
         * One line for a card, a town line or a log. A stay with no dates of its own says so; it
         * does not borrow a year the record never gave it.
         */
        public String line() {
            String years;
            if (estimated) {
                years = " (at some time in their life)";
            } else if (from == Presence.UNKNOWN && to == Presence.UNKNOWN) {
                years = "";
            } else if (from == Presence.UNKNOWN) {
                years = " (to " + year(to) + ")";
            } else if (to == Presence.UNKNOWN) {
                years = " (from " + year(from) + ")";
            } else {
                years = " (" + year(from) + " - " + year(to) + ")";
            }
            return name + " - " + reason() + years;
        }

        /** "431 BC", "AD 1666" - the same convention the clock uses. */
        public static String year(int y) {
            return y < 0 ? (-y) + " BC" : "AD " + y;
        }
    }

    private Residents() {}

    /**
     * Does this reason mean the person was <b>on the ground here</b>, or only that a body they
     * belonged to was seated here? Born, died, studied, lived and worked in are presence. Worked
     * for / served in / member of / party / founded are not: they are the register's own declared
     * gap, and a scene that ignores it misplaces people.
     */
    public static boolean physicallyHere(int why) {
        return why == Presence.BORN || why == Presence.DIED || why == Presence.STUDIED
                || why == Presence.LIVED_AT || why == Presence.WORKED_IN;
    }

    /** The registers are both readable: the join cannot invent a name it does not hold. */
    public static boolean ready() {
        return Presence.get().hasData() && CardStore.get().size() > 0;
    }

    /**
     * <b>Who the record places at this point in this year, by name.</b> Most famous first, then
     * nearest. A person appears once however many rows the pack files for them here.
     */
    public static List<Resident> hereIn(double lat, double lon, int year, double km) {
        return join(Presence.get().hereIn(lat, lon, year, km), lat, lon, false);
    }

    /**
     * <b>Who may be stood up here</b>: the people the record (a) puts on the ground — see
     * {@link #physicallyHere} — and (b) dates to a year, not to a lifetime. This is the list a
     * scene or a resident NPC casts from. Both conditions are load-bearing, and each one on its
     * own has already stood the wrong man in the wrong street.
     */
    public static List<Resident> hereInBody(double lat, double lon, int year, double km) {
        return body(join(Presence.get().hereIn(lat, lon, year, km), lat, lon, false), true);
    }

    /** The same, but keeping the undated stays — for a card, never for a body. */
    public static List<Resident> hereInBodyAnyYear(double lat, double lon, int year, double km) {
        return body(join(Presence.get().hereIn(lat, lon, year, km), lat, lon, false), false);
    }

    private static List<Resident> body(List<Resident> all, boolean datedOnly) {
        List<Resident> out = new ArrayList<>();
        for (Resident r : all) {
            if (!physicallyHere(r.why())) {
                continue;
            }
            if (datedOnly && r.estimated()) {
                continue;
            }
            out.add(r);
        }
        return List.copyOf(out);
    }

    /** The same list with the people who were <em>born</em> here removed: the ones who arrived. */
    public static List<Resident> notBornHere(double lat, double lon, int year, double km) {
        return join(Presence.get().hereIn(lat, lon, year, km), lat, lon, true);
    }

    /** The nearest {@code n} residents, for a town line rather than a page. */
    public static List<Resident> front(List<Resident> all, int n) {
        return all.size() <= n ? all : new ArrayList<>(all.subList(0, n));
    }

    /** "Mir Osman Ali Khan - lived here (1886 - 1967); Sarojini Naidu - was born here (1879 - 1949)" */
    public static String sentence(List<Resident> residents, int n) {
        List<Resident> use = front(residents, n);
        if (use.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Resident r : use) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(r.line());
        }
        return sb.toString();
    }

    /**
     * The join itself. Priority is fame, then distance, then name, so the list is stable: the same
     * place and year always yields the same order, which is what a check can pin.
     */
    private static List<Resident> join(List<Presence.Stay> stays, double lat, double lon, boolean dropBorn) {
        if (stays.isEmpty()) {
            return List.of();
        }
        CardStore cards = CardStore.get();
        List<Resident> out = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (Presence.Stay s : stays) {
            if (dropBorn && s.why() == Presence.BORN) {
                continue;
            }
            if (!seen.add(s.qid())) {
                continue;
            }
            CardStore.Card c = cards.byQid(s.qid());
            if (c == null || !c.person() || c.name() == null || c.name().isBlank()) {
                continue;                       // the register names a body, not a person
            }
            out.add(new Resident(c.name(), s.qid(), c.fame(),
                    s.place().lat(), s.place().lon(), s.place().label(),
                    s.from(), s.to(), s.why(), s.estimated(),
                    Presence.haversineKm(lat, lon, s.place().lat(), s.place().lon())));
        }
        out.sort(Comparator.comparingInt(Resident::fame).reversed()
                .thenComparingDouble(Resident::km)
                .thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
        return out.size() <= MAX ? List.copyOf(out) : List.copyOf(out.subList(0, MAX));
    }

    /** The one-line answer a check can print: how many named residents, and the first three. */
    public static String summary(double lat, double lon, int year, double km) {
        List<Resident> all = hereIn(lat, lon, year, km);
        if (all.isEmpty()) {
            return "nobody the record names is here in " + Resident.year(year);
        }
        return all.size() + " named resident" + (all.size() == 1 ? "" : "s")
                + " in " + Resident.year(year) + ": " + sentence(all, 3);
    }
}

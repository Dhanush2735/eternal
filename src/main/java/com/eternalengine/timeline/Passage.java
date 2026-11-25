package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Travel between universes: portals and rifts, and the way back.</b> H7.
 *
 * <h2>Why this class exists</h2>
 *
 * H7 says "travel: portals and rifts, the way back". The rift particles already exist
 * ({@code RealityRiftController}, Minecraft-bound) and Terra++ owns the commands; what nothing holds is
 * the <b>crossing itself</b> and its last three words. Step 51's {@link BranchTree} travels between
 * timelines; this class travels between <b>universes</b>, step 54's ruptures.
 *
 * <h2>The rule: the way back is booked before the way out</h2>
 *
 * <p>A portal without a way back is a door that closes behind you. So one {@code travel} books
 * <b>both legs at once</b>, in one ticket, and a return is only ever along your own. There is no state
 * in which a traveller is stranded: {@link #outstanding()} is the honest list of who is away and holds
 * their way home, and it is a list of people with tickets, not of people lost.
 *
 * <h2>What travel is, and is not</h2>
 *
 * <p>Travel is a <b>moves only you</b> act (the three kinds of act, decision 3): it changes where you
 * stand, not what the world records. So it takes no before-change copy - the ticket IS the record. And
 * you cannot stand in a universe before it was born: a passage's year must be at least the year that
 * universe ruptured, or it is refused with the year named.
 */
public final class Passage {

    /** <b>How you cross.</b> A portal is deliberate; a rift is the wild kind, and its ticket says so. */
    public enum Way {
        PORTAL("a portal", "a portal crossing: deliberate, and the way home is clear"),
        RIFT("a rift", "a rift crossing: the way home is booked, but a rift is unstable and this ticket "
                + "says so");

        private final String words;
        private final String ticketNote;

        Way(String words, String ticketNote) {
            this.words = words;
            this.ticketNote = ticketNote;
        }

        public String words() {
            return words;
        }

        public String ticketNote() {
            return ticketNote;
        }
    }

    /** <b>One crossing, both legs.</b> Open until the traveller comes home along it. */
    public record Ticket(String id, String traveller, String from, String to, int year, Way way,
                         boolean open, String why) {

        public Ticket {
            id = id == null ? "" : id.trim();
            traveller = traveller == null ? "" : traveller.trim();
            from = from == null ? "" : from.trim();
            to = to == null ? "" : to.trim();
            why = why == null ? "" : why.trim();
        }

        public String line() {
            return id + " · " + traveller + " · " + from + " -> " + to + " (" + year + ", " + way.words()
                    + ") " + (open ? "away, with the way home booked" : "home") + (why.isEmpty() ? ""
                    : " · " + why);
        }
    }

    /** <b>The outcome of a crossing.</b> Either a ticket, or every reason there is none. */
    public record Travelled(Ticket ticket, String refused) {
        public boolean ok() {
            return ticket != null;
        }
    }

    /** <b>The outcome of a return.</b> */
    public record Returned(Ticket ticket, String refused) {
        public boolean ok() {
            return ticket != null;
        }
    }

    private final Ruptures worlds;
    private final Map<String, Ticket> tickets = new LinkedHashMap<>();
    private int counter = 0;

    private Passage(Ruptures worlds) {
        this.worlds = worlds;
    }

    /** The book of crossings for one register of universes. */
    public static Passage over(Ruptures worlds) {
        return new Passage(worlds == null ? Ruptures.empty() : worlds);
    }

    /**
     * <b>Cross, both ways at once.</b> Refused: no traveller named; a universe that does not exist (on
     * either end); a year before the far universe was born; a traveller already away; and no way of
     * getting home (which cannot happen through this method, because this method always books one - the
     * check exists so that any future caller that forgets is refused rather than trusted).
     */
    public synchronized Travelled travel(String traveller, String from, String to, int year, Way way,
                                         String why) {
        if (traveller == null || traveller.trim().isEmpty()) {
            return new Travelled(null, "a crossing with nobody on it: say who travels");
        }
        if (from == null || from.trim().isEmpty() || to == null || to.trim().isEmpty()) {
            return new Travelled(null, "a crossing must say where it is from and where it goes");
        }
        if (worlds.find(from.trim()) == null) {
            return new Travelled(null, "there is no universe called " + from.trim() + " to leave");
        }
        Ruptures.Universe far = worlds.find(to.trim());
        if (far == null) {
            return new Travelled(null, "there is no universe called " + to.trim() + " to travel to");
        }
        if (year < far.year()) {
            return new Travelled(null, to.trim() + " was born in " + far.year() + ": nobody stands in a "
                    + "universe before it ruptured, and this crossing is dated " + year);
        }
        if (from.trim().equals(to.trim())) {
            return new Travelled(null, traveller.trim() + " is already in " + from.trim());
        }
        Ticket open = openTicket(traveller.trim());
        if (open != null) {
            return new Travelled(null, traveller.trim() + " is away in " + open.to() + " on " + open.id()
                    + ", and nobody is in two universes at once: come home first");
        }
        if (way == null) {
            return new Travelled(null, "say how: a portal or a rift");
        }
        counter++;
        Ticket t = new Ticket("ticket-" + counter, traveller.trim(), from.trim(), to.trim(), year, way,
                true, why == null ? "" : why.trim());
        tickets.put(t.id(), t);
        return new Travelled(t, "");
    }

    /**
     * <b>Come home.</b> Only along your own ticket: the id must name a ticket of this traveller's, and
     * the ticket must still be open. Coming home closes it.
     */
    public synchronized Returned back(String traveller, String ticketId) {
        Ticket t = ticketId == null ? null : tickets.get(ticketId.trim());
        if (t == null) {
            return new Returned(null, "there is no ticket called " + ticketId);
        }
        if (traveller == null || !t.traveller().equals(traveller.trim())) {
            return new Returned(null, ticketId + " belongs to " + t.traveller() + ": a return is only "
                    + "along your own ticket");
        }
        if (!t.open()) {
            return new Returned(null, t.traveller() + " is already home on " + t.id());
        }
        Ticket home = new Ticket(t.id(), t.traveller(), t.to(), t.from(), t.year(), t.way(), false,
                t.why());
        tickets.put(home.id(), home);
        return new Returned(home, "");
    }

    /** Where this traveller stands now: the universe they crossed to, or the one they came home to. */
    public synchronized String whereIs(String traveller) {
        if (traveller == null) {
            return null;
        }
        String where = null;
        for (Ticket t : tickets.values()) {
            if (t.traveller().equals(traveller.trim())) {
                // the ticket's "to" is where they last went, out or home; open or closed, it stands.
                where = t.to();
            }
        }
        return where;
    }

    /** This traveller's open ticket, or null when they are home. */
    public synchronized Ticket openTicket(String traveller) {
        if (traveller == null) {
            return null;
        }
        Ticket found = null;
        for (Ticket t : tickets.values()) {
            if (t.traveller().equals(traveller.trim()) && t.open()) {
                found = t;
            }
        }
        return found;
    }

    /** Who is away, with the way home each of them holds. Nobody here is stranded. */
    public synchronized List<Ticket> outstanding() {
        List<Ticket> out = new ArrayList<>();
        for (Ticket t : tickets.values()) {
            if (t.open()) {
                out.add(t);
            }
        }
        return out;
    }

    public synchronized List<String> lines() {
        List<String> out = new ArrayList<>();
        for (Ticket t : tickets.values()) {
            out.add(t.line());
        }
        return out;
    }

    public static String ruleLine() {
        return "A crossing books both legs at once: the way back is booked before the way out. A return "
                + "is only along your own ticket, nobody is in two universes at once, and nobody stands "
                + "in a universe before it ruptured.";
    }
}

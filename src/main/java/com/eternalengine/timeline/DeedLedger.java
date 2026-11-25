package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * <b>The join step 48 left undone: what a player has actually done, counted where it happens.</b>
 *
 * <h2>Why this class exists</h2>
 *
 * <p>Step 48 built {@link PlayerRoles}, whose whole rule is "a role is earned by doing the thing". It also
 * left a hole it named out loud: <b>nothing filled {@link PlayerRoles.Deeds}</b>, so every player was an
 * outsider and four of the ten ways to change history were permanently out of reach. Five registers in the
 * mod already count the five things, and this class is the one place they meet:
 *
 * <table>
 *   <tr><th>deed</th><th>register that already counts it</th><th>call</th></tr>
 *   <tr><td>a sale</td><td>{@code money/MarketLedger}</td><td>{@link #sale}</td></tr>
 *   <tr><td>a fight</td><td>the battle and intervention path</td><td>{@link #fight}</td></tr>
 *   <tr><td>a court day</td><td>{@code sovereign/RoyalCourtManager}</td><td>{@link #courtDay}</td></tr>
 *   <tr><td>a card learned</td><td>{@code knowledge/KnowledgeCard}</td><td>{@link #learned}</td></tr>
 *   <tr><td>an office held</td><td>the towns pack's office holders</td><td>{@link #office}</td></tr>
 * </table>
 *
 * <p>It is deliberately <b>not</b> a second register of sales or fights. It stores only the five counters and
 * the year of the last one, because the truth of a sale is in the ledger and the only thing missing was a
 * count of how many a player had. A caller passes in what happened; this class never asks the world.
 *
 * <h2>And the other half: this is the first caller {@link Chronicler#report} ever had</h2>
 *
 * <p>{@link #record} turns a deed worth recording into a {@link Chronicler.Change}, so the recorder built in
 * step 47e finally has a caller. Only two deeds are history: <b>an intervention</b>, where a player changed
 * what happened, and <b>taking an office</b>, which is a line in any chronicle ever written. Carrying a
 * letter or reading a card is work and not history, and {@link #record} returns null for them, which is the
 * recorder's own judgement applied rather than a second rule written here.
 */
public final class DeedLedger {

    /** What one player has done, and where. Owned here; read through {@link #deedsOf}. */
    private record Row(int sales, int fights, int courtDays, int cards, boolean office, int officeFrom,
                       String place, int lastYear) {
        // NOTE ON lastYear: it is the last year of a PRACTICE, not of an office. Taking an office in 1410
        // must not make a merchant's ten sales of 1300 look recent, which is the second bug the part 2
        // probe found in the same place as the first.
        /**
         * The counters as of a year. An office only counts <b>from the year it was taken</b>: the first
         * version of this class handed back the office for every year, so a player made ruler in 1410 was
         * already a ruler in 1400, which the part 2 probe caught.
         */
        PlayerRoles.Deeds deedsAt(int year) {
            boolean held = office && year >= officeFrom;
            // An office in hand stamps its own year, because that is the year the ruler is judged by.
            int stamp = held ? Math.max(lastYear, officeFrom) : lastYear;
            return new PlayerRoles.Deeds(sales, fights, courtDays, cards, held, stamp);
        }
    }

    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final Map<String, Chronicler> chronicles = new LinkedHashMap<>();

    public DeedLedger() {
    }

    private Row row(String player) {
        String key = key(player);
        return rows.getOrDefault(key, new Row(0, 0, 0, 0, false, Integer.MAX_VALUE, "",
                Integer.MIN_VALUE / 2));
    }

    private static String key(@Nullable String player) {
        return player == null || player.isBlank() ? "-" : player.toLowerCase(java.util.Locale.ROOT);
    }

    private void put(String player, Row r) {
        rows.put(key(player), r);
    }

    // ------------------------------------------------------------------ the five deeds

    /** A sale, however small. The ledger knows the money; this counts the trade. */
    public void sale(@Nullable String player, String place, int year) {
        Row r = row(player);
        put(player, new Row(r.sales() + 1, r.fights(), r.courtDays(), r.cards(), r.office(),
                r.officeFrom(), place == null ? r.place() : place, year));
    }

    /** A fight somebody was in, win or lose. */
    public void fight(@Nullable String player, String place, int year) {
        Row r = row(player);
        put(player, new Row(r.sales(), r.fights() + 1, r.courtDays(), r.cards(), r.office(),
                r.officeFrom(), place == null ? r.place() : place, year));
    }

    /** A day at a court, which is what makes a courtier. */
    public void courtDay(@Nullable String player, String place, int year) {
        Row r = row(player);
        put(player, new Row(r.sales(), r.fights(), r.courtDays() + 1, r.cards(), r.office(),
                r.officeFrom(), place == null ? r.place() : place, year));
    }

    /** A knowledge card actually learned, not merely seen. */
    public void learned(@Nullable String player, String place, int year) {
        Row r = row(player);
        put(player, new Row(r.sales(), r.fights(), r.courtDays(), r.cards() + 1, r.office(),
                r.officeFrom(), place == null ? r.place() : place, year));
    }

    /**
     * An office taken or given up. Taking one is the only path to {@link PlayerRoles.Role#RULER}, and it is
     * the one deed here that a player cannot do alone: it needs a place that has an office to hold.
     */
    @Nullable
    public Chronicler.Line office(@Nullable String player, String place, int year, boolean held,
                                  String branch) {
        Row r = row(player);
        // The office year is kept in officeFrom and NOT in lastYear: an office is not a practice, and
        // taking one must not refresh the twenty-five-year window on everything else.
        put(player, new Row(r.sales(), r.fights(), r.courtDays(), r.cards(), held,
                held ? year : Integer.MAX_VALUE, place == null ? r.place() : place, r.lastYear()));
        if (!held) return null;
        // Taking an office is a line in any chronicle ever written, so it is a change and not a deed.
        return record(branch, new Chronicler.Change(year, Chronicler.Kind.PERSON,
                "the office at " + place, place, 1, Provenance.AUTHORED));
    }

    // ------------------------------------------------------------------ reading it back

    /** The counters, with an office counted only from the year it was taken. */
    public PlayerRoles.Deeds deedsAt(@Nullable String player, int year) {
        return row(player).deedsAt(year);
    }

    /** The raw counters, with whatever office the player holds today. For a ledger, not for a year. */
    public PlayerRoles.Deeds deedsOf(@Nullable String player) {
        return row(player).deedsAt(Integer.MAX_VALUE);
    }

    public PlayerRoles.Role roleOf(@Nullable String player, int year) {
        return PlayerRoles.held(deedsAt(player, year), year);
    }

    /** The situation part 3 asks for, built from this player's own history. */
    public Agency.Situation situationOf(@Nullable String player, int year, String place, long peopleHere) {
        return PlayerRoles.situation(year, place, peopleHere, deedsAt(player, year));
    }

    /** Everything this player can do here and now, through part 3's rules. */
    public List<ChangeRecord.Way> waysOf(@Nullable String player, int year, String place,
                                         long peopleHere) {
        return PlayerRoles.openWays(year, place, peopleHere, deedsAt(player, year));
    }

    public int players() {
        return rows.size();
    }

    /** Every player who is something, with what. Used by a screen and by the probe. */
    public List<String> whoIsWhat(int year) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Row> e : rows.entrySet()) {
            PlayerRoles.Role r = PlayerRoles.held(e.getValue().deedsAt(year), year);
            if (r != PlayerRoles.Role.NONE) out.add(e.getKey() + " is " + r.words());
        }
        return out;
    }

    public void clear() {
        rows.clear();
        chronicles.clear();
    }

    // ------------------------------------------------------------------ the first caller of the recorder

    /**
     * Report a change to this branch's chronicle. Returns the line it became, or null when the recorder
     * judges it not worth one, which is most of the time and is the point.
     */
    @Nullable
    public Chronicler.Line record(@Nullable String branch, Chronicler.Change change) {
        if (change == null) return null;
        return chroniclerFor(branch).report(change);
    }

    /** The chronicle for a branch, created on demand. One per branch, like the branch's own history. */
    public Chronicler chroniclerFor(@Nullable String branch) {
        String key = branch == null || branch.isBlank() ? "main" : branch.toLowerCase(java.util.Locale.ROOT);
        return chronicles.computeIfAbsent(key, k -> new Chronicler());
    }

    /** Every line a branch has written, so a caller can show the two most recent. */
    public List<Chronicler.Line> lines(String branch) {
        return chroniclerFor(branch).lines();
    }

    /**
     * An intervention: a player changed what happened, which is the one deed the recorder already has a
     * kind for. Returns the line, or null if it was too small to be history.
     *
     * <p>The magnitude is the force the intervention detector measured, so the threshold in
     * {@link Chronicler.Kind} does the judging and nothing here decides what is big.
     */
    @Nullable
    public Chronicler.Line intervention(@Nullable String branch, @Nullable String player, int year,
                                        String subject, String place, int force) {
        Chronicler.Line line = record(branch, new Chronicler.Change(year, Chronicler.Kind.WAR, subject,
                place, force, Provenance.AUTHORED));
        if (line != null) fight(player, place, year);
        return line;
    }

    /** The sentence a player reads about themselves, from their own ledger. */
    public String lineFor(@Nullable String player, int year, String place, long peopleHere) {
        PlayerRoles.Deeds d = deedsAt(player, year);
        return PlayerRoles.progressLine(d, year) + " " + PlayerRoles.summary(year, place, peopleHere, d);
    }
}

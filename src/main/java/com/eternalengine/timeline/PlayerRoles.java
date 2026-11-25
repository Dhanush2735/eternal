package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <b>Player roles: what a player is, and what being it lets them do.</b> F5.
 *
 * <h2>Why this class exists</h2>
 *
 * Step 48 part 3 said who can change what, but it read a player's standing out of a field nobody sets.
 * Every player in the mod is an `OUTSIDER` today, so four of the ten ways to change history are
 * permanently out of reach and the other six are open to a stranger on the day they arrive. That is not
 * a rule, it is a hole where a rule should be.
 *
 * <p>So a role is <b>earned by doing the thing the role does</b>, counted from registers the mod already
 * keeps, and it is <b>lost by stopping</b>. A merchant is somebody who has sold goods, not somebody who
 * clicked a button marked merchant. A soldier is somebody who has been in fights. A courtier has stood in
 * a court. A scholar has learned what the place knows. A ruler holds a place.
 *
 * <h2>What a role is not</h2>
 *
 * <p>It is not a class, a perk tree, a GUI or a licence. Roles overlap, a player may hold more than one,
 * and the <b>highest standing</b> they hold is the one that counts, because standing is ordered and a
 * courtier who also keeps a shop does not stop being a courtier when the shop is busy.
 *
 * <h2>Where the numbers come from</h2>
 *
 * <p>{@link Deeds} has five counters, and each maps to something that already exists:
 * {@code sales} from {@code money/MarketLedger}, {@code fights} from the battle and intervention path,
 * {@code courtDays} from {@code sovereign/RoyalCourtManager}, {@code cardsLearned} from
 * {@code knowledge/KnowledgeCard}, and {@code holdsOffice} from the towns pack's own office holders. None
 * of them is invented here; this class only decides what a count of them makes a person.
 */
public final class PlayerRoles {

    /**
     * <b>What a player is.</b> The five roles F5 names, plus {@link #NONE}, which is what everybody is
     * before they have done anything.
     */
    public enum Role {
        NONE("nothing in particular", Agency.Standing.OUTSIDER),
        MERCHANT("a merchant", Agency.Standing.RESIDENT),
        SCHOLAR("a scholar", Agency.Standing.RESIDENT),
        SOLDIER("a soldier", Agency.Standing.NOTABLE),
        COURTIER("a courtier", Agency.Standing.OFFICE),
        RULER("its ruler", Agency.Standing.RULER);

        private final String words;
        private final Agency.Standing standing;

        Role(String words, Agency.Standing standing) {
            this.words = words;
            this.standing = standing;
        }

        public String words() {
            return words;
        }

        /** What this role makes a player, for {@link Agency.Situation}. */
        public Agency.Standing standing() {
            return standing;
        }
    }

    /**
     * What a player has actually done. Five counts, each one already kept somewhere in the mod, and the
     * year the last of them happened, which is how a role lapses.
     */
    public record Deeds(int sales, int fights, int courtDays, int cardsLearned, boolean holdsOffice,
                        int lastDeedYear) {
        public Deeds {
            sales = Math.max(0, sales);
            fights = Math.max(0, fights);
            courtDays = Math.max(0, courtDays);
            cardsLearned = Math.max(0, cardsLearned);
        }

        public static Deeds none() {
            return new Deeds(0, 0, 0, 0, false, Integer.MIN_VALUE / 2);
        }

        public Deeds plusSale(int year) {
            return new Deeds(sales + 1, fights, courtDays, cardsLearned, holdsOffice, year);
        }

        public Deeds plusFight(int year) {
            return new Deeds(sales, fights + 1, courtDays, cardsLearned, holdsOffice, year);
        }

        public Deeds plusCourtDay(int year) {
            return new Deeds(sales, fights, courtDays + 1, cardsLearned, holdsOffice, year);
        }

        public Deeds plusCard(int year) {
            return new Deeds(sales, fights, courtDays, cardsLearned + 1, holdsOffice, year);
        }

        public Deeds withOffice(boolean office, int year) {
            return new Deeds(sales, fights, courtDays, cardsLearned, office, year);
        }

        /** The biggest single thing this player has done, in the role's own units. */
        public int best() {
            return Math.max(Math.max(sales, fights), Math.max(courtDays, cardsLearned));
        }
    }

    /** Sales of any kind before a player counts as a merchant. */
    public static final int SALES_FOR_MERCHANT = 10;
    /** Fights before a player counts as a soldier. */
    public static final int FIGHTS_FOR_SOLDIER = 3;
    /** Court days before a player counts as a courtier. */
    public static final int COURT_FOR_COURTIER = 20;
    /** Cards learned before a player counts as a scholar. */
    public static final int CARDS_FOR_SCHOLAR = 10;
    /** Years of silence after which a role is given up. A role is a practice, not a badge. */
    public static final int LAPSE_YEARS = 25;

    private PlayerRoles() {
    }

    // ------------------------------------------------------------------ earning a role

    /** The role these deeds earn, before any lapse. Independent of the year. */
    public static Role earned(Deeds d) {
        if (d == null) return Role.NONE;
        if (d.holdsOffice()) return Role.RULER;
        // The highest standing wins, and the tie between merchant and scholar is broken by the count:
        // whoever the player has done more of is what they are.
        if (d.courtDays() >= COURT_FOR_COURTIER) return Role.COURTIER;
        boolean soldier = d.fights() >= FIGHTS_FOR_SOLDIER;
        boolean scholar = d.cardsLearned() >= CARDS_FOR_SCHOLAR;
        boolean merchant = d.sales() >= SALES_FOR_MERCHANT;
        if (soldier) return Role.SOLDIER;
        if (scholar && merchant) {
            return d.cardsLearned() >= d.sales() ? Role.SCHOLAR : Role.MERCHANT;
        }
        if (scholar) return Role.SCHOLAR;
        if (merchant) return Role.MERCHANT;
        return Role.NONE;
    }

    /**
     * The role a player holds in a given year, which is the earned role <b>unless they have stopped</b>.
     * A merchant who has not sold anything for twenty-five years is a shopkeeper who has given it up, and
     * the ways that need a merchant go with it.
     */
    public static Role held(Deeds d, int year) {
        Role r = earned(d);
        if (r == Role.NONE) return Role.NONE;
        // An office refreshes the window it is judged by, which the ledger does by stamping the office
        // year when the office is held: a ruler with no practice behind them is still ruler on the day
        // they take it. Step 49's part 2 found this, as a ruler who lapsed on the day of the coronation.
        if (d.holdsOffice() && year - d.lastDeedYear() <= LAPSE_YEARS) return Role.RULER;
        if (d.holdsOffice()) return Role.NONE;      // the office is gone too, and it takes everything
        return year - d.lastDeedYear() <= LAPSE_YEARS ? r : Role.NONE;
    }

    /** True if the role has lapsed rather than never being earned. Different things to a player. */
    public static boolean lapsed(Deeds d, int year) {
        return earned(d) != Role.NONE && held(d, year) == Role.NONE;
    }

    /** The sentence a player reads. It says what to do next, not what they are not. */
    public static String progressLine(Deeds d, int year) {
        Role r = held(d, year);
        if (r != Role.NONE) {
            return "You are " + r.words() + ", which gives you " + r.standing().words() + ".";
        }
        if (lapsed(d, year)) {
            return "You were " + earned(d).words() + " and have not done it since " + d.lastDeedYear()
                    + ". Do it again and it comes back.";
        }
        // The nearest thing they can reach, so the line is a way forward rather than a verdict.
        int best = Integer.MAX_VALUE;
        String what = "";
        int left = 0;
        int[][] gaps = {
                {SALES_FOR_MERCHANT - d.sales(), 0},
                {FIGHTS_FOR_SOLDIER - d.fights(), 1},
                {CARDS_FOR_SCHOLAR - d.cardsLearned(), 2},
                {COURT_FOR_COURTIER - d.courtDays(), 3},
        };
        String[] names = {"more sales", "more fights", "more of what the place knows", "more days at court"};
        for (int k = 0; k < gaps.length; k++) {
            if (gaps[k][0] > 0 && gaps[k][0] < best) {
                best = gaps[k][0];
                what = names[gaps[k][1]];
                left = gaps[k][0];
            }
        }
        if (what.isEmpty()) return "You are " + Role.NONE.words() + ".";
        return "Nothing yet. The nearest thing you could be is " + left + " " + what + " away.";
    }

    // ------------------------------------------------------------------ what a role opens

    /**
     * The ways a role supplies the missing ingredient for, so a player is not asked for a thing they
     * already are. This is the whole of F5's "what each may do that the others may not".
     */
    public static Set<ChangeRecord.Way> supplies(Role role) {
        Set<ChangeRecord.Way> out = new LinkedHashSet<>();
        if (role == null) return out;
        switch (role) {
            case MERCHANT -> {
                out.add(ChangeRecord.Way.TRADE_OR_SABOTAGE_GOODS);
                out.add(ChangeRecord.Way.FOUND_A_TOWN);
            }
            case SCHOLAR -> {
                out.add(ChangeRecord.Way.BRING_AN_INVENTION_EARLY);
                out.add(ChangeRecord.Way.TELL_PEOPLE_THE_FUTURE);
            }
            case SOLDIER -> {
                out.add(ChangeRecord.Way.STOP_AN_EVENT);
                out.add(ChangeRecord.Way.START_OR_ESCALATE_A_WAR);
                out.add(ChangeRecord.Way.SABOTAGE_OR_EMPTY_A_PLACE);
            }
            case COURTIER -> {
                out.add(ChangeRecord.Way.MARRY);
                out.add(ChangeRecord.Way.STOP_AN_EVENT);
                out.add(ChangeRecord.Way.FOUND_A_TOWN);
            }
            case RULER -> {
                for (ChangeRecord.Way w : ChangeRecord.Way.values()) out.add(w);
                out.remove(ChangeRecord.Way.KILL_OR_SAVE_A_PERSON);
            }
            case NONE -> {
            }
        }
        return out;
    }

    /**
     * A soldier brings people. This is not free standing: it is capped, because a player who has been in
     * three fights has a warband and a player who has been in three hundred does not have a country.
     */
    public static int followers(Role role, Deeds d) {
        if (role == null || d == null) return 0;
        return switch (role) {
            case SOLDIER -> Math.min(50, d.fights() * 10);
            case MERCHANT -> Math.min(30, d.sales());
            // A courtier's court is a dozen people at most, so a founding is out of reach for them, which
            // is the finding of step 48 part 4 kept in the code. A ruler's following is the office itself.
            case RULER -> Math.min(200, 100 + d.courtDays());
            case COURTIER -> Math.min(20, d.courtDays() / 4);
            default -> 0;
        };
    }

    /** A scholar's study counts as a workshop, and a merchant's warehouse does not. */
    public static boolean workshop(Role role) {
        return role == Role.SCHOLAR || role == Role.RULER;
    }

    /**
     * A merchant's purse is not bigger, it is simply not empty. A scholar has one too, and the part 4
     * probe is what established it: a study needs materials, and without a purse a scholar could learn
     * ten cards and still not bring an invention early, which made the one thing a scholar is for the
     * one thing they could not do.
     */
    public static long purse(Role role, Deeds d) {
        if (role == null || d == null) return 0L;
        return switch (role) {
            case MERCHANT -> Math.min(100L, 10L * d.sales());
            case SCHOLAR -> Math.min(50L, 5L * d.cardsLearned());
            case SOLDIER -> Math.min(20L, d.fights() * 5L);
            case RULER -> 500L;
            case COURTIER -> 50L;
            default -> 0L;
        };
    }

    // ------------------------------------------------------------------ the bridge to part 3

    /**
     * The situation a player is in, built from what they are and where they are. This is the join that
     * part 3 was missing: standing, followers, a workshop and a purse all come from the role now, and
     * only the people count comes from the world.
     */
    public static Agency.Situation situation(int year, String place, long peopleHere, Deeds d) {
        Role r = held(d, year);
        Agency.Standing standing = r.standing();
        // A role's standing is the floor, and the deeds can raise it one step: a soldier who has fought
        // twenty times is a notable even before a role says so.
        // The deeds can raise a role's standing one step, and only while the role is HELD: a lapsed
        // merchant is a stranger again, not a notable who has stopped trading. The first run of the
        // part 4 probe showed exactly that, with a courtier who had done twenty court days and
        // nothing since reading as NOTABLE two hundred years later.
        if (r != Role.NONE && r != Role.RULER && r != Role.COURTIER && d != null && d.best() >= 20) {
            if (standing.ordinal() < Agency.Standing.NOTABLE.ordinal()) {
                standing = Agency.Standing.NOTABLE;
            }
        }
        return new Agency.Situation(year, place, peopleHere, standing, followers(r, d), workshop(r),
                purse(r, d));
    }

    /** Every way this player can do right now, through part 3's own rules. */
    public static List<ChangeRecord.Way> openWays(int year, String place, long peopleHere, Deeds d) {
        return Agency.open(situation(year, place, peopleHere, d));
    }

    /** The ways the role supplies the ingredient for, among those the world also allows. */
    public static List<ChangeRecord.Way> fromRole(int year, String place, long peopleHere, Deeds d) {
        Role r = held(d, year);
        List<ChangeRecord.Way> out = new ArrayList<>();
        for (ChangeRecord.Way w : openWays(year, place, peopleHere, d)) {
            if (supplies(r).contains(w)) out.add(w);
        }
        return out;
    }

    /** One line naming the player, what they are and what it opens. */
    public static String summary(int year, String place, long peopleHere, Deeds d) {
        Role r = held(d, year);
        List<ChangeRecord.Way> open = openWays(year, place, peopleHere, d);
        return "In " + year + " at " + place + " you are " + r.words() + " and can "
                + (open.isEmpty() ? "change nothing yet" : String.join(", ", open.stream()
                        .map(w -> w.name().toLowerCase(Locale.ROOT)).toList()))
                + ".";
    }
}

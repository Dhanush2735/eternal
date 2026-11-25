package com.eternalengine.buildings;

import com.eternalengine.civilization.Disasters;
import com.eternalengine.civilization.Disasters.Disaster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 36 (B10): <b>the life of a building</b> — works, standing, damaged, ruin, gone, repaired.
 *
 * <p>Step 32 gave the world 23,534 famous buildings, each with the year it was begun and, for
 * 2,569 of them, the year it ended. That is two dates and nothing between them. A player standing
 * in Rome in 1349 should not be told the Colosseum is merely "standing", because an earthquake
 * took the south side of it out that year and it has never been put back. A player standing in a
 * village in 1360, eleven years after the Black Death, should find damaged houses going to ruin,
 * because there is nobody left to mend them.
 *
 * <p>So this register joins step 32's dates to step 36's {@link Disasters}: a building's state in
 * a year is its own dates first, then the worst thing that has struck it, then whether anybody was
 * left to put it right.
 *
 * <h2>The model</h2>
 *
 * <p>A building carries a <b>condition</b> from 1 (whole) to 0 (gone). It starts at 1. Every
 * disaster that reaches it takes its {@link Disasters#damage} off. Between disasters it moves in
 * one of two directions:
 *
 * <ul>
 *   <li><b>mended</b> — if the place still has people to spare, condition climbs back towards 1
 *       over a repair time that grows with how much was broken: a cracked wall in a proud city is
 *       mended in a couple of years, a collapsed cathedral takes forty.</li>
 *   <li><b>decaying</b> — if it does not, condition falls towards 0 over a ruin time set by how
 *       much the place looks after itself. Wood rots in a century; dressed stone stands for five.</li>
 * </ul>
 *
 * <p>That is the whole mechanism, and it is enough to produce the two behaviours that make B10
 * worth having. A town shaken in 1349 has damaged houses by nightfall and sound ones by 1400. A
 * town emptied in 1349 has <i>sound</i> houses in 1349 and <b>ruins by 1380</b>, because the
 * plague broke nothing at all and simply removed everyone who would have thatched a roof. The
 * difference is carried entirely by {@link Disasters#nobodyToMend}.
 *
 * <p>Condition becomes a phase like this:
 *
 * <pre>
 *   1.00 ......... 0.85 ......... 0.40 ......... 0.05 ......... 0.00
 *   |  standing          |  damaged     |  ruin        |  gone
 * </pre>
 *
 * <p>and a building that has been below 0.85 and climbed back above it is {@link Phase#REPAIRED}
 * rather than {@link Phase#STANDING}, because a mended building is not a new one — the patch shows.
 *
 * <h2>What is recorded and what is worked out</h2>
 *
 * <p>Where step 32 knows an end year, that year is final: the building is {@link Phase#GONE} from
 * it, and if a disaster reached it within three years of that year the disaster is named as the
 * <b>attributed cause</b>. Where no disaster matches, the cause is unknown and the register says
 * so rather than inventing one.
 *
 * <p>Everything else is a model, and every answer carries {@link Life#estimated()} saying so. No
 * data set records which buildings a given earthquake actually touched; 77% of the hazards carry
 * no severity at all; and the repair and ruin times are this register's own arithmetic. What is
 * genuinely known is the two dates, the disasters that reached the place, and — where the record
 * gives one — how strong they were.
 *
 * <p><b>The rule for a caller: ask this before you place a building.</b> A square whose buildings
 * are ruins must not be given a whole town, and a town that has just been through a plague must
 * not be given sound houses. Where nothing is recorded there is no answer, and no answer is not
 * permission — it means the pack does not know.
 */
public final class BuildingLife {

    /** The six states of the plan, plus the one before the first of them. */
    public enum Phase {
        /** Before its start year: the ground is empty. */
        UNBUILT,
        /** Going up. The plan's "works". */
        WORKS,
        /** Whole and sound. */
        STANDING,
        /** Hurt but standing; what it looks like depends on how badly. */
        DAMAGED,
        /** A shell. Nobody is going to mend it as it stands. */
        RUIN,
        /** Down. There is nothing here but footings. */
        GONE,
        /** Was hurt, and has been put right. The patch shows. */
        REPAIRED;

        /** Is there anything to see? A ruin is something to see; a footprint is not. */
        public boolean visible() {
            return this != UNBUILT && this != GONE;
        }

        public String label() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Condition at or above which a building counts as sound. */
    public static final double WHOLE = 0.85d;
    /** Condition below which a building is a shell rather than a damaged one. */
    public static final double SHELL = 0.40d;
    /** Condition at or below which there is nothing left. */
    public static final double NOTHING = 0.05d;
    /**
     * A strike lighter than this is absorbed by the ordinary maintenance of an inhabited place
     * and never shows. Real towns live for centuries in country that shakes: the Colosseum has
     * been through a dozen Roman earthquakes and is two thirds standing. Only what maintenance
     * cannot keep up with is worth carrying.
     */
    public static final double MINOR = 0.30d;

    /**
     * How much a place looks after itself, 0 to 1, where nothing is known. A rich city replaces a
     * collapsed roof; a village abandoned by a plague does not. Callers who know better should say
     * so — this is the register's own middle, not a measurement.
     */
    public static final double DEFAULT_CARE = 0.50d;

    /** How long a sound building weathers with nobody mending it before it starts to rot. */
    public static final int NEGLECT_GRACE = 8;

    /**
     * The damage one event has to do before it can erase a building outright. Below it, the worst
     * a strike can do is leave a shell: people rebuild their houses, and only a catastrophe or a
     * long abandonment takes a building off the map.
     */
    /**
     * The damage one event has to do before a place walks away from the building rather than
     * rebuilding it. Set by the cases: Vesuvius at 0.75 and the Great Fire of London at 0.87 are
     * catastrophes that were not rebuilt, while the 1531 Lisbon earthquake at 0.63 wrecked a
     * capital that then put itself back up. 0.70 separates them.
     */
    public static final double CATASTROPHIC = 0.70d;

    /**
     * How much a place has to look after itself before it will rebuild something a catastrophe
     * flattened rather than walk away from it. Pompeii was not rebuilt; Lisbon and San Francisco
     * were. Below this, a building brought to a shell by one catastrophic event is abandoned, and
     * from there it only decays.
     */
    public static final double REBUILD_ANYTHING = 0.75d;

    /** How many years a building of each kind takes to go up, very roughly. */
    private static final int BUILD_YEARS_CATHEDRAL = 60;
    private static final int BUILD_YEARS_FORTIFICATION = 12;
    private static final int BUILD_YEARS_DEFAULT = 8;

    /**
     * Anything with a life: a famous building from step 32's pack, or a house the player put up
     * themselves. Part 4 (B21) needs both to go through the same arithmetic, so the model asks
     * only for this and not for the thing it came from.
     *
     * @param defence whether it is part of a place's defences rather than a building inside them
     */
    public record Subject(String name, double lat, double lon, String kind, int from, int to,
                          boolean defence, boolean estimated) {

        public static Subject of(FamousBuildings.Building b) {
            return new Subject(b.name(), b.lat(), b.lon(), b.kind(), b.from(), b.to(),
                    b.isDefence(), b.estimated());
        }
    }

    /**
     * One building's state in one year, and what made it that way.
     *
     * @param condition 1 is whole, 0 is gone
     * @param cause     the disaster blamed, or null where the register has none to name
     * @param estimated true where any part of the answer is worked out rather than recorded —
     *                  which is to say, nearly always
     */
    public record Life(Subject subject, Phase phase, int year,
                       Disaster cause, double condition, String note, boolean estimated) {

        /** One line for a player: "the Colosseum, damaged — the ground shook and walls came down". */
        public String line() {
            StringBuilder sb = new StringBuilder(subject.name());
            switch (phase) {
                case UNBUILT -> sb.append(" is not here yet");
                case WORKS -> sb.append(" is going up");
                case STANDING -> sb.append(" stands");
                case DAMAGED -> sb.append(" is damaged");
                case RUIN -> sb.append(" is a ruin");
                case GONE -> sb.append(" is gone");
                case REPAIRED -> sb.append(" stands, mended");
            }
            if (cause != null && phase != Phase.STANDING) {
                sb.append(" — ").append(Disasters.effect(cause.kind()).what());
                if (phase == Phase.GONE || phase == Phase.RUIN) {
                    sb.append(", ").append(cause.yearLabel());
                }
            }
            if (note != null && !note.isEmpty()) {
                sb.append(" (").append(note).append(")");
            }
            return sb.toString();
        }
    }

    private BuildingLife() {
    }

    // ------------------------------------------------------------------ asking

    /** What state is this building in, in this year? Assumes an ordinary middling place. */
    public static Life of(FamousBuildings.Building b, int year) {
        return of(b, year, DEFAULT_CARE);
    }

    /** As {@link #of(FamousBuildings.Building, int)}, for a place that looks after itself. */
    public static Life of(FamousBuildings.Building b, int year, double care) {
        return of(Subject.of(b), year, care);
    }

    /**
     * What state is this building in, in this year, in a place that looks after itself this much?
     *
     * <p>Takes a {@link Subject} rather than a famous building, because what the player builds is
     * not in step 32's pack and has to be asked about in exactly the same words.
     *
     * @param care 0 to 1; below a fifth a place cannot mend anything however hard it tries
     */
    public static Life of(Subject b, int year, double care) {
        Disasters d = Disasters.get();
        if (!d.loaded()) {
            return new Life(b, Phase.STANDING, year, null, 1.0d,
                    "no disaster record; the building's own dates are all that is known", true);
        }
        int from = b.from();
        int to = b.to();

        if (year < from) {
            return new Life(b, Phase.UNBUILT, year, null, 1.0d, "", false);
        }
        int works = buildYears(b);
        // where step 32 knows an end year, that year is final
        if (to != FamousBuildings.NO_END && year >= to) {
            Disaster blame = blame(b, to, d);
            return new Life(b, Phase.GONE, year, blame, 0.0d,
                    blame != null ? "fell in " + yearText(to) + "; the record names no cause"
                                  : "fell in " + yearText(to),
                    blame == null);
        }

        double c = Math.max(0.20d, Math.min(1.0d, care));
        List<int[]> shortOfHands = d.shortOfHandsSpans(b.lat(), b.lon(), from, year);
        List<Disaster> strikes = strikes(b, from, year, d);

        double condition = 1.0d;
        int since = from;
        Disaster lastHit = null;
        boolean everHurt = false;
        boolean guessed = false;
        boolean abandoned = false;

        for (Disaster hit : strikes) {
            condition = drift(condition, since, hit.year(), c, shortOfHands, abandoned);
            since = hit.year();
            double hurt = d.damage(hit, b.lat(), b.lon());
            if (hurt >= MINOR) {
                double before = condition;
                condition -= hurt;
                if (hurt < CATASTROPHIC) {
                    // people rebuild their houses; only a catastrophe erases one
                    condition = Math.max(condition, Math.min(before, SHELL));
                } else if (condition < SHELL && c < REBUILD_ANYTHING) {
                    // Pompeii was not rebuilt; Lisbon and San Francisco were
                    abandoned = true;
                }
                lastHit = hit;
                everHurt = true;
                if (hit.severityEstimated()) {
                    guessed = true;
                }
            } else if (d.nobodyToMend(b.lat(), b.lon(), hit.year())) {
                lastHit = hit;        // broke nothing, took the menders away
                guessed = true;
            }
            if (condition <= NOTHING) {
                return new Life(b, Phase.GONE, year, lastHit, 0.0d,
                        "destroyed by " + (lastHit == null ? "one blow"
                                : lastHit.name() + ", " + yearText(hit.year()))
                                + "; the record names no rebuilding", true);
            }
        }
        condition = drift(condition, since, year, c, shortOfHands, abandoned);
        condition = Math.max(0.0d, Math.min(1.0d, condition));

        Phase phase;
        String note = "";
        if (condition <= NOTHING) {
            phase = Phase.GONE;
            note = "rotted away with nobody to mend it";
        } else if (condition < SHELL) {
            phase = Phase.RUIN;
            if (abandoned) {
                note = "abandoned where it fell";
            }
        } else if (condition < WHOLE) {
            phase = Phase.DAMAGED;
        } else if (everHurt) {
            phase = Phase.REPAIRED;
        } else {
            phase = Phase.STANDING;
        }
        // Scaffolding can burn. The plan's "works" is what a building is called while it is going
        // up and nothing has hurt it; a half-built house that an eruption has just buried is a
        // ruin, not a building site.
        if (year < from + works && phase != Phase.RUIN && phase != Phase.GONE) {
            phase = Phase.WORKS;
        }
        return new Life(b, phase, year, phase == Phase.STANDING ? null : lastHit,
                condition, note, guessed || b.estimated());
    }

    /**
     * How a building's condition moves over an interval with no disaster in it: up towards whole
     * for the years the place had hands, down towards nothing for the years it did not.
     *
     * <p>The accounting is in years, and only in the years the neglect actually covered. A plague
     * in 541 out of a 283-year stretch of Roman history is not 283 years of rot. Each episode is
     * also given {@link #NEGLECT_GRACE} years of grace: a sound building weathers a bad decade
     * without falling down, because it is the decades of nobody mending it afterwards that do the
     * harm, not the bad decade itself.
     */
    private static double drift(double condition, int fromYear, int toYear, double care,
                                List<int[]> shortOfHands, boolean abandoned) {
        int dt = toYear - fromYear;
        if (dt <= 0) {
            return condition;
        }
        int neglected = 0;
        for (int[] span : shortOfHands) {
            int s = Math.max(fromYear, span[0]);
            int e = Math.min(toYear, span[1]);
            if (e >= s) {
                neglected += Math.max(0, e - s + 1 - NEGLECT_GRACE);
            }
        }
        int mended = abandoned ? 0 : Math.max(0, dt - neglected);
        if (condition < 1.0d && mended > 0) {
            // A cracked wall in a proud city is mended in a couple of years; a collapsed
            // cathedral takes thirty. This has to outpace the background rate of hazards or every
            // old city ends up a ruin, which is what happened the first time this was tried.
            double repairYears = (1.0d + 8.0d * (1.0d - condition)) / care;
            condition = Math.min(1.0d, condition + mended / repairYears);
        }
        if (neglected > 0 || abandoned) {
            double ruinYears = 60.0d + 400.0d * care;
            condition = Math.max(0.0d, condition - (abandoned ? dt : neglected) / ruinYears);
        }
        return condition;
    }

    /**
     * The disasters that reached this building between two years, in order, **once each**.
     *
     * <p>The year index holds an event under every year it ran, so a sixteen-year plague comes
     * back sixteen times from a query across its span. Applying it sixteen times is how the first
     * version of this register destroyed the Colosseum in the reign of Gallienus. Events are
     * keyed by id and kept at their first year.
     */
    private static List<Disaster> strikes(Subject b, int from, int to, Disasters d) {
        Map<String, Disaster> seen = new LinkedHashMap<>();
        for (Disaster s : d.near(b.lat(), b.lon(), from, to, 20000.0d)) {
            if (s.year() < from || seen.containsKey(s.id())) {
                continue;
            }
            if (d.damage(s, b.lat(), b.lon()) >= MINOR
                    || d.nobodyToMend(b.lat(), b.lon(), s.year())) {
                seen.put(s.id(), s);
            }
        }
        List<Disaster> out = new ArrayList<>(seen.values());
        out.sort(Comparator.comparingInt(Disaster::year));
        return out;
    }

    /** The disaster to blame for a building that ended in a known year, if one is there to name. */
    private static Disaster blame(Subject b, int endYear, Disasters d) {
        Disaster best = null;
        double bestDamage = MINOR;
        for (int y = endYear - 3; y <= endYear + 3; y++) {
            for (Disaster s : d.inYear(y)) {
                if (!s.reaches(b.lat(), b.lon())) {
                    continue;
                }
                double v = d.damage(s, b.lat(), b.lon());
                if (v > bestDamage) {
                    bestDamage = v;
                    best = s;
                }
            }
        }
        return best;
    }

    private static int buildYears(Subject b) {
        String kind = b.kind() == null ? "" : b.kind().toLowerCase(java.util.Locale.ROOT);
        if (b.defence()) {
            return BUILD_YEARS_FORTIFICATION;
        }
        if (kind.contains("cathedral") || kind.contains("basilica") || kind.contains("temple")
                || kind.contains("pyramid") || kind.contains("abbey") || kind.contains("mosque")) {
            return BUILD_YEARS_CATHEDRAL;
        }
        return BUILD_YEARS_DEFAULT;
    }

    private static String yearText(int year) {
        return year < 0 ? (-year) + " BC" : "AD " + year;
    }

    // ------------------------------------------------------------------ whole places

    /** Every famous building near a place, with its state in this year. */
    public static List<Life> at(double lat, double lon, int year, double km, int max) {
        return at(lat, lon, year, km, max, DEFAULT_CARE);
    }

    /** As {@link #at(double, double, int, double, int)}, for a place that looks after itself. */
    public static List<Life> at(double lat, double lon, int year, double km, int max,
                                double care) {
        List<Life> out = new ArrayList<>();
        for (FamousBuildings.Building b
                : FamousBuildings.get().near(lat, lon, year, km, max)) {
            out.add(of(Subject.of(b), year, care));
        }
        return List.copyOf(out);
    }

    /** One line for a player standing here: what the buildings around them are like. */
    public static String line(double lat, double lon, int year, double km, int max) {
        List<Life> lives = at(lat, lon, year, km, max);
        if (lives.isEmpty()) {
            return "";
        }
        int stand = 0;
        int damaged = 0;
        int ruined = 0;
        int gone = 0;
        for (Life l : lives) {
            switch (l.phase()) {
                case STANDING, REPAIRED -> stand++;
                case DAMAGED -> damaged++;
                case RUIN -> ruined++;
                case GONE -> gone++;
                default -> { }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (stand > 0) {
            sb.append(stand).append(" standing");
        }
        if (damaged > 0) {
            sb.append(sb.length() > 0 ? ", " : "").append(damaged).append(" damaged");
        }
        if (ruined > 0) {
            sb.append(sb.length() > 0 ? ", " : "").append(ruined).append(" ruin")
              .append(ruined == 1 ? "" : "s");
        }
        if (gone > 0) {
            sb.append(sb.length() > 0 ? ", " : "").append(gone).append(" gone");
        }
        return sb.toString();
    }
}

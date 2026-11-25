package com.eternalengine.people;

import com.eternalengine.civilization.Disasters;
import com.eternalengine.civilization.Disasters.Disaster;
import com.eternalengine.civilization.Disasters.Effect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Step 38 (D6): needs and mind — food, safety, faith, money, mood — and the town-situation reader.
 *
 * <p>Planned since turn 57 and never built. The point of this register is that it invents almost
 * nothing: every number in it is read off a register that already exists and is already sourced.
 * Food comes from the hazard pack's own effect table. Safety comes from the same place. Faith is
 * measured by how many times the day stops for prayer, which is a fact about the place and not a
 * guess about the person. Money is the one that cannot be sourced, and it says so.
 *
 * <p><b>The rule that makes food honest:</b> the same failed harvest does not hit everybody the
 * same way. It takes everything from the person who grew it and it takes the price from the
 * person who buys it. A famine in a farming village and a famine in a town four miles away are
 * not the same event, and this register says different things about them.
 *
 * <p><b>The rule that makes safety honest:</b> a plague and an earthquake are both disasters and
 * they are not the same kind of fear. One takes the people and leaves the houses standing; the
 * other takes the houses. The hazard pack already separates them — {@code stopsRepair} is its own
 * word for the difference — and this register uses that rather than one blunt "danger" number.
 */
public final class Needs {

    private static final Needs INSTANCE = new Needs();

    private Needs() {
    }

    public static synchronized Needs get() {
        return INSTANCE;
    }

    /** Where one need stands, 0 gone to 1 whole, and the one line that explains it. */
    public record Need(String id, String word, double level, String why, boolean applies) {

        public Need(String id, String word, double level, String why) {
            this(id, word, level, why, true);
        }

        /** A need that does not apply here: money, where nothing is bought or sold. */
        public boolean counts() {
            return applies && level > 0.0d;
        }

        /** The level in words: good, fair, thin, poor or gone. */
        public String state() {
            return level >= 0.80d ? "good" : level >= 0.60d ? "fair"
                    : level >= 0.40d ? "thin" : level >= 0.20d ? "poor" : "gone";
        }

        public String describe() {
            return word + " is " + state() + " - " + why;
        }
    }

    /** The state of a place, read off everything the mod already knows about it. */
    public record Situation(int year, double lat, double lon, String job, List<Need> needs,
                            Disaster pressing, String mood, String line) {

        public Need need(String id) {
            for (Need n : needs) {
                if (n.id().equals(id)) {
                    return n;
                }
            }
            return new Need(id, id, 0.5d, "nothing is known here", true);
        }

        /** The need that is worst, which is the one a person thinks about. */
        public Need worst() {
            Need worst = null;
            for (Need n : needs) {
                if (n.id().equals("mood")) {
                    continue;                       // mood is the answer, not one of the causes
                }
                if (!n.counts()) {
                    continue;
                }
                if (worst == null || n.level() < worst.level()) {
                    worst = n;
                }
            }
            return worst == null ? new Need("none", "nothing", 1.0d, "") : worst;
        }
    }

    /**
     * Read the situation of a place for one person in it. {@code job} is what they do
     * (from {@link Jobs#pick}); {@code faith} is whose hours keep their day.
     */
    public Situation at(double lat, double lon, int year, boolean urban, String job,
                        String faith) {
        List<Need> needs = new ArrayList<>();

        // ---------------------------------------------------------------- what is happening here
        Disaster pressing = pressing(lat, lon, year);
        double fieldHit = 0.0d;
        double deathHit = 0.0d;
        double wallHit = 0.0d;
        if (pressing != null) {
            Effect e = Disasters.effect(pressing.kind());
            double near = nearness(pressing, lat, lon);
            fieldHit = e.fieldsPeak() * pressing.severity() * near;
            deathHit = e.mortalityPeak() * pressing.severity() * near;
            wallHit = e.structurePeak() * pressing.severity() * near;
        }

        // ---------------------------------------------------------------- food
        // a failed harvest takes everything from the grower and the price from the buyer
        double exposure = growsFood(job) ? 1.0d : 0.5d;
        double food = clamp(1.0d - fieldHit * exposure, 0.0d, 1.0d);
        String foodWhy;
        if (pressing != null && fieldHit > 0.02d) {
            foodWhy = growsFood(job)
                    ? Disasters.effect(pressing.kind()).what() + ", and it was your crop"
                    : Disasters.effect(pressing.kind()).what()
                    + ", so bread is dear but there is still bread";
        } else {
            foodWhy = growsFood(job) ? "the harvest is in and you grew it yourself"
                    : "the market has bread and you have the coin for it";
        }
        needs.add(new Need("food", "food", food, foodWhy));

        // ---------------------------------------------------------------- safety
        // mortality counts whole, the walls half: a plague frightens more than a fallen barn
        double safety = clamp(1.0d - deathHit - 0.5d * wallHit, 0.0d, 1.0d);
        String safeWhy;
        if (pressing != null && (deathHit > 0.02d || wallHit > 0.02d)) {
            Effect e = Disasters.effect(pressing.kind());
            safeWhy = e.what() + (e.stopsRepair()
                    ? ", and there is nobody left to mend what it broke"
                    : ", but the people are whole and the walls can go up again");
        } else {
            safeWhy = "nothing here is killing anybody at the moment";
        }
        needs.add(new Need("safety", "safety", safety, safeWhy));

        // ---------------------------------------------------------------- faith
        // measured by how many times the day stops, which is a fact about the place
        DayRound.Prayers p = DayRound.get().prayers(faith);
        double faithLevel = switch (p.count()) {
            case 0 -> 0.50d;
            case 1, 2 -> 0.50d;
            case 3 -> 0.62d;
            case 4, 5 -> 0.75d;
            case 6, 7 -> 0.80d;
            default -> 0.85d;
        };
        String faithWhy;
        if (pressing != null && deathHit > 0.05d) {
            faithLevel = clamp(faithLevel + 0.15d, 0.0d, 1.0d);
            faithWhy = "the day stops " + p.count() + " times to pray, and more than that "
                    + "while the sickness is here";
        } else if (p.count() == 0) {
            faithWhy = "the day here has no set hour of prayer, which is not the same as "
                    + "having no gods";
        } else {
            faithWhy = "the day stops " + p.count() + " times to pray: "
                    + String.join(", ", p.names());
        }
        needs.add(new Need("faith", "faith", faithLevel, faithWhy));

        // ---------------------------------------------------------------- money
        // The one need no register can source. Built from the day round alone, which is sourced:
        // whether there is a market at all, whether there is a day off, and how much of the day
        // the work takes. Where nothing is bought or sold there is no money, and that is not a
        // low score but a different kind of life.
        DayRound.Day day = DayRound.get().at(lat, lon, year, urban);
        int hours = day == null ? 9 : day.hours();
        boolean hasRest = day != null && day.hasRestDay();
        boolean noMarket = day == null || !day.hasMarket();
        double money;
        String moneyWhy;
        if (noMarket) {
            money = 0.0d;      // not a low score: the thing itself is not here
            moneyWhy = "there is no buying and selling here on any fixed day: what you have, "
                    + "you made or grew or were given";
        } else {
            money = 0.25d;
            if (hasRest) {
                money += 0.10d;
            }
            money += hours >= 12 ? 0.0d : hours >= 10 ? 0.05d : hours >= 8 ? 0.15d : 0.25d;
            if ("scholar".equals(job) || "official".equals(job) || "trade".equals(job)
                    || "clergy".equals(job)) {
                money += 0.10d;
            } else if ("craft".equals(job)) {
                money += 0.05d;
            }
            money = clamp(money, 0.0d, 1.0d);
            moneyWhy = "there is a market, " + (hasRest ? "and a day of the week that is not "
                    + "worked" : "and no day off") + ", and the working day is " + hours
                    + " hours" + (money < 0.45d ? " - long hours and no margin" : " - enough");
        }
        needs.add(new Need("money", "money", money, moneyWhy, !noMarket));

        // ---------------------------------------------------------------- mood
        // Not an average. A person with no food is not content because they pray five times a
        // day: the worst need dominates how anybody actually feels, and the rest barely counts.
        double sum = 0.0d;
        double worst = 1.0d;
        int counted = 0;
        for (Need n : needs) {
            if (!n.counts()) {
                continue;
            }
            sum += n.level();
            worst = Math.min(worst, n.level());
            counted++;
        }
        if (counted == 0) {
            counted = 1;
        }
        double moodLevel = clamp(0.65d * worst + 0.35d * (sum / counted), 0.0d, 1.0d);
        // and a sickness or a hunger that leaves nobody to mend anything frightens worse than
        // one that does not, because there is no end in sight to it
        if (pressing != null && Disasters.effect(pressing.kind()).stopsRepair()) {
            moodLevel = clamp(moodLevel - 0.10d * nearness(pressing, lat, lon), 0.0d, 1.0d);
        }
        // whose house it is: the household for this place, which is the same every time the
        // question is asked of the same place and year
        Households.Home home = Households.get().home(saltFor(lat, lon, year), year, lat, lon,
                urban);
        boolean alone = home != null && home.size() == 1;
        boolean children = home != null && home.has("child");
        if (alone) {
            moodLevel = clamp(moodLevel - 0.10d, 0.0d, 1.0d);
        }
        if (children) {
            moodLevel = clamp(moodLevel + 0.05d, 0.0d, 1.0d);
        }
        needs.add(new Need("mood", "mood", moodLevel, "on the whole, " + moodWord(moodLevel)));
        String mood = moodWord(moodLevel);
        if (alone) {
            mood += ", and alone";
        }
        if (children) {
            mood += ", with children in the house";
        }

        String line = buildLine(needs, pressing, mood);
        return new Situation(year, lat, lon, job, List.copyOf(needs), pressing, mood, line);
    }

    /** The disaster that matters most here and now, or null if nothing is in force. */
    public Disaster pressing(double lat, double lon, int year) {
        Disasters d = Disasters.get();
        Disaster best = null;
        double bestScore = 0.0d;
        for (Disaster x : d.all()) {
            if (!x.inForceIn(year) || !x.reaches(lat, lon)) {
                continue;
            }
            double score = x.severity() * nearness(x, lat, lon);
            if (score > bestScore) {
                bestScore = score;
                best = x;
            }
        }
        return best;
    }

    /** How near the worst of it: 1 at the centre, falling to 0 at the edge of its reach. */
    private static double nearness(Disaster d, double lat, double lon) {
        double km = d.distanceKm(lat, lon);
        double reach = Math.max(1.0d, d.radiusKm());
        return clamp(1.0d - km / reach, 0.0d, 1.0d);
    }

    /** A stable salt for a place and year, so the same place reads the same every time. */
    private static long saltFor(double lat, double lon, int year) {
        long bits = Double.doubleToLongBits(lat) ^ (Double.doubleToLongBits(lon) * 31L);
        return bits ^ ((long) year * 0x9E3779B97F4A7C15L);
    }

    private static String moodWord(double level) {
        return level >= 0.75d ? "content" : level >= 0.55d ? "steady"
                : level >= 0.35d ? "anxious" : level >= 0.18d ? "afraid" : "desperate";
    }

    /**
     * Whether the job is food on the stalk: the person a failed harvest takes everything from,
     * as opposed to the person it only takes the price from. Matches both vocabularies of the
     * work world (the kinds in {@link Jobs#WORKS} and their plain names in {@link Jobs#PLAIN})
     * and the craft names villagers actually carry (farmer, gatherer, fisherman).
     */
    private static boolean growsFood(String job) {
        String j = job == null ? "" : job.trim().toLowerCase(java.util.Locale.ROOT);
        return j.equals("farming") || j.equals("herding") || j.equals("foraging")
                || j.startsWith("farm") || j.startsWith("herd") || j.startsWith("forag")
                || j.startsWith("gather") || j.startsWith("hunt") || j.startsWith("fish");
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    // ------------------------------------------------------------------ what it says

    /** One person's own answer, in the first person. */
    public String personLine(double lat, double lon, int year, boolean urban, String job,
                             String faith) {
        Situation s = at(lat, lon, year, urban, job, faith);
        Need worst = s.worst();
        return "I am a " + Jobs.plain(job) + ". " + cap(worst.describe())
                + ". On the whole I am " + s.mood() + ".";
    }

    /**
     * The town-situation reader: one paragraph, read off everything the mod knows. This is what a
     * player is shown when they ask what life is like here.
     */
    private static String buildLine(List<Need> needs, Disaster pressing, String mood) {
        StringBuilder sb = new StringBuilder();
        List<Need> ordered = new ArrayList<>(needs);
        ordered.sort(Comparator.comparingDouble(Need::level));       // worst first
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(cap(ordered.get(i).describe())).append('.');
        }
        if (pressing != null) {
            sb.append(" What is on everybody's mind is the ").append(pressing.name())
              .append(" of ").append(pressing.yearLabel()).append('.');
        }
        sb.append(" On the whole, people here are ").append(mood).append('.');
        return sb.toString();
    }

    /** The town-situation reader: one paragraph, read off everything the mod knows. */
    public String line(double lat, double lon, int year, boolean urban, String job, String faith) {
        return at(lat, lon, year, urban, job, faith).line();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A short card: the five needs as numbers, for a debug screen or a probe. */
    public String card(double lat, double lon, int year, boolean urban, String job, String faith) {
        Situation s = at(lat, lon, year, urban, job, faith);
        StringBuilder sb = new StringBuilder();
        for (Need n : s.needs()) {
            sb.append(String.format(Locale.ROOT, "%-8s %-6s %.2f  %s%n", n.id(), n.level(),
                    n.level(), n.why()));
        }
        sb.append(String.format(Locale.ROOT, "mood     %s%n", s.mood()));
        return sb.toString();
    }
}

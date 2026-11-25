package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Step 36 (B27): <b>the disasters that change the land</b>, and what each of them does.
 *
 * <p>A world that can be set to any date cannot assume the ground is as it is today. Vesuvius
 * buried Pompeii in AD 79; the 1138 Aleppo earthquake killed a quarter of a million people; the
 * 1755 Lisbon earthquake destroyed a capital from an epicentre three hundred kilometres out to
 * sea; the Black Death emptied a third of Europe and left its villages to rot. Every one of those
 * is a year and a place, and a town that has just been through one looks nothing like a town that
 * has not.
 *
 * <p>{@code history/events_hazards.json.gz} (built by {@code worldfill/build_hazards.py} in an
 * earlier step, revised by the November 2026 review in {@code A/r2_wikipedia}) holds
 * <b>25,457 of them, 3000 BCE to AD 2026</b> — the record's end, not the
 * world's, which runs to {@link com.eternalengine.clock.Timeline#LAST_YEAR}; there are no
 * disasters after AD 2026 because none are recorded, and none are invented: 11,748 earthquakes,
 * 5,540 volcanic eruptions, 5,087 plagues, 1,912 tsunamis, 395 famines, 339 pandemics, 254 floods
 * and 129 epidemics, each with a point, a year and a radius. The review demoted unverified rows to
 * indexed notes (26,997 when the register was written, 25,457 now). This register reads that pack
 * and turns it into an answer: <i>what struck here, when, and how badly?</i>
 *
 * <h2>Three defects in the pack, and what this register does about each</h2>
 *
 * <p><b>It carries no severity.</b> {@code build_hazards.py} wrote the magnitude into the event's
 * name — "Earthquake at Jordan: Bab-A-Daraa,Al-Karak (magnitude 7.3)", "Eruption of Ksudach
 * (VEI 4)" — and threw the number away as a field. Only <b>1,700 of the earthquakes and 3,911 of
 * the eruptions</b> have one to recover. Where they do, it is used. Where they do not, the
 * <b>death toll</b> is used instead, because an earthquake that killed forty thousand people was
 * not a small one: 2,726 events name a number of dead. Where neither survives, the kind's own
 * middle is used. {@link Disaster#severityFrom()} says which of the three, and every answer that
 * rests on a default is an estimate.
 *
 * <p><b>Its radii do not scale, and that is the defect that hurts.</b> The pack gives every event
 * of a kind the same radius — 50 km for an earthquake — so a magnitude 9 rupture that destroyed a
 * capital from far offshore does not reach the capital it destroyed. Lisbon is 344 km from the
 * epicentre of the 1755 earthquake. So for the four kinds whose reach is physics and not people —
 * earthquake, volcano, tsunami, flood — this register <b>widens the radius with the severity</b>,
 * up to ten times for the very worst. Plague, epidemic, pandemic and famine keep the pack's own
 * radius: they spread along roads, and inflating them by magnitude would be nonsense.
 *
 * <p><b>One hundred and eighteen events lost their year in the parsing</b> and were filed under
 * AD 1 to 99 — "Tsunami in Lisbon, Portugal (10,000 dead)" in the year 1, "1811–1812 New Madrid
 * earthquakes" in the year 7. The true years are not recoverable from here, so they are
 * <b>quarantined rather than guessed</b>: any event dated AD 0 to 99 that either names a thousand
 * dead or is a tsunami is left out of every answer (53 of the 118 in the reviewed pack).
 * {@link #quarantined()} says how many.
 *
 * <p>And the pack is a bulk resource, not a roll of the great disasters: of thirty famous ones
 * checked against it, <b>fourteen are absent</b> — Vesuvius in AD 79, Krakatau in 1883, San
 * Francisco in 1906, Tangshan in 1976, Haiti in 2010, Tohoku in 2011. That gap is filled by
 * {@code history/disasters_hand.json.gz}, a hand-written supplement; see part 2 of the step-36
 * working notes.
 *
 * <h2>The distinction the whole system turns on</h2>
 *
 * <p>An earthquake, an eruption, a tsunami and a flood <i>break things</i>. A plague, an epidemic,
 * a pandemic and a famine break nothing at all, and that is the point — they empty a place of the
 * people who would have mended it. A town shaken in 1349 has damaged houses by nightfall. A town
 * emptied in 1349 has sound houses in 1349 and ruins by 1380, because nobody is left to thatch a
 * roof. B10's repair rule is what carries the difference, and {@link Effect#mortalityPeak()} is
 * what tells it how many are gone.
 *
 * <p>Indexed by year, not by place: 27,000 events spread over five thousand years are about five
 * to a year, so a question about one year costs almost nothing, while the radii run to a thousand
 * kilometres and a place index would have to expand every query across most of a continent.
 */
public final class Disasters {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/events_hazards.json.gz";

    /** The hand-written supplement: the great disasters the scraped pack misses. */
    public static final String SUPPLEMENT = "/data/eternalengine/eternal/history/disaster_supplement.json.gz";

    /** The end year of an event the record gives no end to. */
    public static final int NO_END = Integer.MAX_VALUE;

    /** The least damage worth reporting: below this, a place noticed and forgot. */
    public static final double NOTICEABLE = 0.02d;

    /** Below this fraction of the people gone, there is still somebody to mend a roof. */
    public static final double NOBODY_LEFT = 0.15d;

    private static volatile Disasters instance;

    private final List<Disaster> all;
    private final Map<Integer, List<Disaster>> byYear;
    private final Map<Integer, List<Disaster>> byYearShortOfHands;
    private final String problem;
    private final int fromSeverity;
    private final int fromDeaths;
    private final int quarantined;
    private final int supplement;

    // ------------------------------------------------------------------ what a disaster is

    /** The kinds the pack carries, as this register names them. */
    public enum Kind {
        EARTHQUAKE, VOLCANO, TSUNAMI, FLOOD, PLAGUE, EPIDEMIC, PANDEMIC, FAMINE, FIRE, OTHER;

        static Kind of(String raw) {
            if (raw == null) {
                return OTHER;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "earthquake" -> EARTHQUAKE;
                case "volcano" -> VOLCANO;
                case "tsunami" -> TSUNAMI;
                case "flood" -> FLOOD;
                case "plague" -> PLAGUE;
                case "epidemic" -> EPIDEMIC;
                case "pandemic" -> PANDEMIC;
                case "famine" -> FAMINE;
                case "fire" -> FIRE;
                default -> OTHER;
            };
        }

        /** What the record calls it, so a sentence can be built from it. */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Does its reach grow with how big it was, or with how many people carried it? */
        public boolean reachesBySeverity() {
            return this == EARTHQUAKE || this == VOLCANO || this == TSUNAMI || this == FLOOD;
        }
    }

    /** Where a severity came from, in the order this register trusts them. */
    public enum SeverityFrom { MAGNITUDE, DEATHS, HAND, KIND_DEFAULT }

    /**
     * One disaster: where, when, how wide a thing it was, and how strong a thing the record says.
     *
     * @param severity      0 to 1
     * @param severityFrom  whether that came from a magnitude or VEI in the name, from the number
     *                      of dead, or from the kind's own middle; only the first is not an estimate
     * @param radiusKm      how far its effects reached — the pack's own radius, widened for the
     *                      kinds whose reach is physics
     * @param dead          how many the record says died, or -1 where it says nothing
     */
    public record Disaster(String id, String name, Kind kind, int year, int endYear,
                           double lat, double lon, double radiusKm, double severity,
                           SeverityFrom severityFrom, int dead, String confidence) {

        /** Is it happening in this year? A disaster with no end year is treated as that year only. */
        public boolean inForceIn(int atYear) {
            return atYear >= year && atYear <= (endYear == NO_END ? year : endYear);
        }

        /** Had it happened by this year? This is the question damage is asked with. */
        public boolean happenedBy(int atYear) {
            return atYear >= year;
        }

        public double distanceKm(double atLat, double atLon) {
            return haversineKm(lat, lon, atLat, atLon);
        }

        /** Whether this place is close enough to have felt it at all. */
        public boolean reaches(double atLat, double atLon) {
            return distanceKm(atLat, atLon) <= radiusKm;
        }

        /** True where the severity is the kind's own middle rather than something the record says. */
        public boolean severityEstimated() {
            return severityFrom == SeverityFrom.KIND_DEFAULT;
        }

        public String yearLabel() {
            return year < 0 ? (-year) + " BC" : "AD " + year;
        }
    }

    /**
     * What a kind of disaster does. Written by hand, because no data set has it, and kept small
     * enough to read in one place.
     *
     * @param structurePeak how much of a building's fabric the worst of it can take, 0 to 1
     * @param fieldsPeak    how much of a harvest it can take, 0 to 1
     * @param mortalityPeak what share of the people it can take, 0 to 1
     * @param movesLand     whether it changes the ground: a river a new course, a coast a new
     *                      shape, a mountain a new cone
     * @param stopsRepair   whether it leaves nobody to mend what is broken — the mark of disease
     *                      and hunger, which damage no timber at all
     * @param lastsYears    how many years the place is still feeling it
     * @param what          one line for a player
     */
    public record Effect(double structurePeak, double fieldsPeak, double mortalityPeak,
                         boolean movesLand, boolean stopsRepair, int lastsYears, String what) {}

    /**
     * The effect table. Eight kinds, and the rule that separates them: the four that hit the
     * ground take a building apart, and the four that hit the people leave the building standing
     * and let it rot.
     */
    private static final Map<Kind, Effect> EFFECTS = Map.of(
            Kind.EARTHQUAKE, new Effect(0.85d, 0.10d, 0.25d, false, false, 1,
                    "the ground shook and walls came down"),
            Kind.VOLCANO, new Effect(0.90d, 0.80d, 0.30d, true, false, 5,
                    "the mountain threw ash and stone, and the ash buried the fields"),
            Kind.TSUNAMI, new Effect(0.95d, 0.90d, 0.50d, false, false, 1,
                    "the sea came in and took what stood near it"),
            Kind.FLOOD, new Effect(0.55d, 0.95d, 0.20d, true, false, 2,
                    "the water rose, and where it ran longest it left a new channel"),
            Kind.PLAGUE, new Effect(0.0d, 0.10d, 0.60d, false, true, 3,
                    "the sickness emptied the place; the houses stood and rotted"),
            Kind.EPIDEMIC, new Effect(0.0d, 0.10d, 0.35d, false, true, 2,
                    "the sickness ran through the place and left too few to mend it"),
            Kind.PANDEMIC, new Effect(0.0d, 0.15d, 0.50d, false, true, 5,
                    "the sickness went everywhere, and for years there was nobody to spare"),
            Kind.FAMINE, new Effect(0.0d, 1.00d, 0.30d, false, true, 3,
                    "the harvest failed; the people left, and the roofs went with them"),
            Kind.FIRE, new Effect(0.95d, 0.10d, 0.05d, false, false, 1,
                    "the fire ran through the town and left the houses on the ground"),
            Kind.OTHER, new Effect(0.30d, 0.30d, 0.20d, false, false, 1,
                    "something happened here that the record does not name"));

    /** The severity used where the record gives none: the kind's own middle. */
    private static final Map<Kind, Double> DEFAULT_SEVERITY = Map.of(
            Kind.EARTHQUAKE, 0.35d,     // about magnitude 5.8
            Kind.VOLCANO, 0.30d,        // about VEI 1.8
            Kind.TSUNAMI, 0.40d,
            Kind.FLOOD, 0.35d,
            Kind.PLAGUE, 0.35d,
            Kind.EPIDEMIC, 0.25d,
            Kind.PANDEMIC, 0.45d,
            Kind.FAMINE, 0.35d,
            Kind.FIRE, 0.50d,
            Kind.OTHER, 0.25d);

    public static Effect effect(Kind kind) {
        return EFFECTS.getOrDefault(kind, EFFECTS.get(Kind.OTHER));
    }

    // ------------------------------------------------------------------ asking

    private Disasters(List<Disaster> all, Map<Integer, List<Disaster>> byYear, String problem,
                      Map<Integer, List<Disaster>> byYearShortOfHands,
                      int fromSeverity, int fromDeaths, int quarantined, int supplement) {
        this.all = List.copyOf(all);
        this.byYear = byYear;
        this.byYearShortOfHands = byYearShortOfHands;
        this.problem = problem;
        this.fromSeverity = fromSeverity;
        this.fromDeaths = fromDeaths;
        this.quarantined = quarantined;
        this.supplement = supplement;
    }

    public static Disasters get() {
        Disasters d = instance;
        if (d == null) {
            synchronized (Disasters.class) {
                if (instance == null) {
                    instance = load();
                }
                d = instance;
            }
        }
        return d;
    }

    public boolean loaded() {
        return problem == null && !all.isEmpty();
    }

    public String problem() {
        return problem;
    }

    public int count() {
        return all.size();
    }

    public List<Disaster> all() {
        return all;
    }

    /** How many carry a magnitude or VEI the record actually gives. */
    public int fromMagnitude() {
        return fromSeverity;
    }

    /** How many had their severity taken from the number of dead. */
    public int fromDeathToll() {
        return fromDeaths;
    }

    /** How many were dropped because the year did not survive the parsing. */
    public int quarantined() {
        return quarantined;
    }

    /** How many came from the hand-written supplement rather than the scraped pack. */
    public int fromSupplement() {
        return supplement;
    }

    /** Every disaster the record puts in this year, anywhere in the world. */
    public List<Disaster> inYear(int year) {
        return byYear.getOrDefault(year, List.of());
    }

    /**
     * The disasters that reached this place between two years, worst first.
     *
     * @param km how far from the place a disaster's edge may lie; the disaster's own radius
     *           decides whether the place was inside it, this only bounds how far to look
     */
    public List<Disaster> near(double lat, double lon, int fromYear, int toYear, double km) {
        List<Disaster> out = new ArrayList<>();
        for (int y = fromYear; y <= toYear; y++) {
            for (Disaster d : inYear(y)) {
                if (d.distanceKm(lat, lon) <= Math.min(km, d.radiusKm)) {
                    out.add(d);
                }
            }
        }
        out.sort((a, b) -> Double.compare(damage(b, lat, lon), damage(a, lat, lon)));
        return List.copyOf(out);
    }

    /** What is happening at this place in this year, worst first. */
    public List<Disaster> at(double lat, double lon, int year) {
        List<Disaster> out = new ArrayList<>();
        for (Disaster d : inYear(year)) {
            if (d.reaches(lat, lon)) {
                out.add(d);
            }
        }
        out.sort((a, b) -> Double.compare(damage(b, lat, lon), damage(a, lat, lon)));
        return List.copyOf(out);
    }

    /** The worst thing that has struck this place up to this year, or null if nothing has. */
    public Disaster worstBy(double lat, double lon, int year) {
        Disaster best = null;
        double bestDamage = NOTICEABLE;
        for (Disaster d : all) {
            if (!d.happenedBy(year) || !d.reaches(lat, lon)) {
                continue;
            }
            double v = damage(d, lat, lon);
            if (v > bestDamage) {
                bestDamage = v;
                best = d;
            }
        }
        return best;
    }

    /**
     * How far the effects reach, given how big the event was. Only the four kinds whose reach is
     * physics are widened; the four that travel by road keep the radius the pack gave them.
     *
     * <p>A magnitude 9 rupture is felt a thousand kilometres off and the pack would have given it
     * fifty. The worst events are widened tenfold, which is what puts Lisbon inside the earthquake
     * that destroyed it.
     */
    public static double reach(double packRadiusKm, Kind kind, double severity) {
        if (!kind.reachesBySeverity()) {
            return packRadiusKm;
        }
        double widen = 1.0d + 9.0d * Math.pow(Math.max(0.0d, Math.min(1.0d, severity)), 1.5d);
        return Math.min(1200.0d, packRadiusKm * widen);
    }

    /** How much of a building's fabric one disaster takes at one place, 0 to 1. */
    public double damage(Disaster d, double atLat, double atLon) {
        return falloff(d, atLat, atLon) * effect(d.kind()).structurePeak() * d.severity();
    }

    /** How much of a harvest one disaster takes at one place, 0 to 1. */
    public double fieldLoss(Disaster d, double atLat, double atLon) {
        return falloff(d, atLat, atLon) * effect(d.kind()).fieldsPeak() * d.severity();
    }

    /** What share of the people one disaster takes at one place, 0 to 1. */
    public double mortality(Disaster d, double atLat, double atLon) {
        return falloff(d, atLat, atLon) * effect(d.kind()).mortalityPeak() * d.severity();
    }

    /**
     * Peak at the centre, falling off as the square of the distance to the edge: a place a tenth of
     * the way out is barely touched, one half way out takes a quarter of the peak.
     */
    private static double falloff(Disaster d, double atLat, double atLon) {
        double f = d.distanceKm(atLat, atLon) / Math.max(1.0d, d.radiusKm());
        if (f >= 1.0d) {
            return 0.0d;
        }
        if (f <= 0.25d) {
            return 1.0d;
        }
        double t = (1.0d - f) / 0.75d;
        // A shock dies away fast; something carried along a road does not. The Black Death at
        // 5,500 km from the Crimea still has to be felt in London and Delhi, and a squared
        // falloff would put it out before it reached either.
        return d.kind().reachesBySeverity() ? t * t : Math.pow(t, 0.8d);
    }

    /**
     * Whether this place had nobody left to mend anything in this year: disease or hunger.
     *
     * <p>Not merely "a disaster happened" — the place has to have lost a tenth of its people, or
     * the harvest that feeds them. A famine two hundred and fifty kilometres away does not stop a
     * village mending its roof.
     */
    public boolean nobodyToMend(double lat, double lon, int year) {
        for (Disaster d : byYearShortOfHands.getOrDefault(year, List.of())) {
            if (!effect(d.kind()).stopsRepair() || !d.reaches(lat, lon)) {
                continue;
            }
            if (mortality(d, lat, lon) + fieldLoss(d, lat, lon) * 0.20d >= NOBODY_LEFT) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many years a place stays short of hands after the sickness itself has passed.
     *
     * <p>A plague is over in three years; the shortage of hands it leaves is not. Nobody rethatch
     * a roof for a generation after a third of the parish is dead, and that tail — not the event
     * — is what turns a plague year into ruins a lifetime later. It is scaled by how bad the
     * event was and capped at sixty years, so that a scattered outbreak does not stand in for the
     * Black Death.
     */
    public static int tailYears(Disaster d) {
        Effect e = effect(d.kind());
        if (!e.stopsRepair()) {
            return 0;
        }
        return Math.min(60, (int) Math.round(e.lastsYears() * (1.0d + 8.0d * d.severity())));
    }

    /** The last year on which this event leaves a place short of hands. */
    public static int shortOfHandsUntil(Disaster d) {
        int last = d.endYear() == NO_END ? d.year() : d.endYear();
        return Math.min(d.year() + 80, last + tailYears(d));
    }

    /**
     * The stretches of years, inside a range, on which this place was short of hands — merged and
     * clipped, so a caller can ask how long the neglect lasted rather than whether it happened.
     *
     * <p>This is the answer B10 needs, and "did anything happen in this interval" is not it. A
     * plague in 541 out of a 283-year stretch of Roman history is not 283 years of rot, which is
     * what the first version of the repair rule concluded, and which is why the Colosseum came out
     * destroyed by the Plague of Justinian.
     */
    public List<int[]> shortOfHandsSpans(double lat, double lon, int fromYear, int toYear) {
        if (toYear <= fromYear) {
            return List.of();
        }
        Map<String, Disaster> seen = new LinkedHashMap<>();
        for (int y = fromYear; y <= toYear; y++) {
            for (Disaster d : byYearShortOfHands.getOrDefault(y, List.of())) {
                if (!effect(d.kind()).stopsRepair() || !d.reaches(lat, lon)) {
                    continue;
                }
                if (mortality(d, lat, lon) + fieldLoss(d, lat, lon) * 0.20d < NOBODY_LEFT) {
                    continue;
                }
                seen.putIfAbsent(d.id(), d);
            }
        }
        List<int[]> spans = new ArrayList<>();
        for (Disaster d : seen.values()) {
            int s = Math.max(fromYear, d.year());
            int e = Math.min(toYear, shortOfHandsUntil(d));
            if (e >= s) {
                spans.add(new int[]{s, e});
            }
        }
        spans.sort(Comparator.comparingInt(sp -> sp[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] sp : spans) {
            int n = merged.size();
            if (n > 0 && sp[0] <= merged.get(n - 1)[1] + 1) {
                merged.get(n - 1)[1] = Math.max(merged.get(n - 1)[1], sp[1]);
            } else {
                merged.add(new int[]{sp[0], sp[1]});
            }
        }
        return List.copyOf(merged);
    }

    /**
     * B27's other half: what did the ground itself do here. A flood that leaves a new channel, a
     * coast that moves, a delta that shifts — step 35's {@link WaterChanges} carries 158 of them,
     * and this is where a disaster and a change to the water are the same event seen twice.
     *
     * @return the changes to the water standing here in this year; empty where none is recorded
     */
    public List<WaterChanges.Change> landChanged(double lat, double lon, int year) {
        WaterChanges water = WaterChanges.get();
        if (!water.loaded()) {
            return List.of();
        }
        return water.standing(lat, lon, year);
    }

    /** One sentence for a player standing here in this year, or empty when nothing happened. */
    public String describe(double lat, double lon, int year) {
        List<Disaster> now = at(lat, lon, year);
        if (now.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, now.size()); i++) {
            Disaster d = now.get(i);
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(d.name()).append(": ").append(effect(d.kind()).what());
            if (d.dead() > 0) {
                sb.append(", ").append(String.format(Locale.ROOT, "%,d", d.dead())).append(" dead");
            }
            sb.append(". ");
        }
        if (nobodyToMend(lat, lon, year)) {
            sb.append("There is nobody left here to mend what is broken.");
        }
        return sb.toString().trim();
    }

    public String summary() {
        return count() + " disasters, " + fromSeverity + " with a magnitude or VEI, "
                + fromDeaths + " rated by their dead, " + supplement + " written by hand, "
                + quarantined + " set aside";
    }

    // ------------------------------------------------------------------ loading

    private static final Pattern MAGNITUDE = Pattern.compile("magnitude\\s+([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern VEI = Pattern.compile("VEI\\s+([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern DEAD = Pattern.compile("([0-9][0-9,]*)\\s+dead");

    /**
     * Severity, from the three places it can still be found, in the order they are trusted.
     *
     * <p>First the magnitude or VEI, which the builder left in the name and nowhere else: an
     * earthquake's magnitude runs 2.1 to 8.8 in this pack and a volcano's VEI 0 to 6, and both are
     * put on 0 to 1 with a magnitude 4 and a VEI 0 at nothing and the top of each range at 1.
     *
     * <p>Failing that the number of dead, because what killed forty thousand people was not a
     * small event whatever the record forgot: a hundred dead is a sixth, a thousand a half, a
     * hundred thousand five sixths, and a million the whole.
     *
     * <p>Failing both, the kind's own middle — and that, and only that, is an estimate.
     */
    static Severity severityOf(Kind kind, String name) {
        if (name != null) {
            Matcher m = kind == Kind.EARTHQUAKE ? MAGNITUDE.matcher(name)
                    : kind == Kind.VOLCANO ? VEI.matcher(name) : null;
            if (m != null && m.find()) {
                double raw = Double.parseDouble(m.group(1));
                double sev = kind == Kind.EARTHQUAKE ? (raw - 4.0d) / 5.0d : raw / 6.0d;
                return new Severity(clamp01(sev), SeverityFrom.MAGNITUDE);
            }
            int dead = deadIn(name);
            if (dead >= 10) {
                return new Severity(clamp01(Math.log10(dead) / 6.0d), SeverityFrom.DEATHS);
            }
        }
        return new Severity(DEFAULT_SEVERITY.getOrDefault(kind, 0.30d), SeverityFrom.KIND_DEFAULT);
    }

    /** Severity and where it came from, so a caller can tell a measurement from a guess. */
    public record Severity(double value, SeverityFrom from) {}

    /** How many the record says died, or -1 where it says nothing. */
    static int deadIn(String name) {
        if (name == null) {
            return -1;
        }
        Matcher m = DEAD.matcher(name);
        if (!m.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Was this event's year lost by the parser? A.D. 1 to 99 is where they landed: a tsunami in
     * the year 1 with ten thousand dead in Lisbon, the New Madrid earthquakes in the year 7.
     * There is no recovering the true year from here, so the event is set aside instead.
     */
    static boolean yearLost(Kind kind, int start, int dead) {
        return start >= 0 && start <= 99 && (dead >= 1000 || kind == Kind.TSUNAMI);
    }

    private static double clamp01(double v) {
        return v < 0.0d ? 0.0d : (v > 1.0d ? 1.0d : v);
    }

    private static Disasters load() {
        List<Disaster> out = new ArrayList<>();
        Map<Integer, List<Disaster>> byYear = new HashMap<>();
        int[] tally = new int[4];           // magnitude, deaths, hand, quarantined
        String problem = readPack(RESOURCE, false, out, byYear, tally);
        if (problem != null) {
            return new Disasters(List.of(), Map.<Integer, List<Disaster>>of(), problem,
                        Map.<Integer, List<Disaster>>of(), 0, 0, 0, 0);
        }
        readPack(SUPPLEMENT, true, out, byYear, tally);
        if (out.isEmpty()) {
            return new Disasters(List.of(), Map.<Integer, List<Disaster>>of(),
                        RESOURCE + " held no disasters",
                        Map.<Integer, List<Disaster>>of(), 0, 0, 0, 0);
        }
        Map<Integer, List<Disaster>> frozen = new HashMap<>();
        byYear.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        Map<Integer, List<Disaster>> hands = new HashMap<>();
        for (Disaster d : out) {
            if (!effect(d.kind()).stopsRepair()) {
                continue;
            }
            int last = d.endYear() == NO_END ? d.year() : d.endYear();
            int until = Math.min(last + tailYears(d), d.year() + 80);
            for (int y = d.year(); y <= until; y++) {
                hands.computeIfAbsent(y, k -> new ArrayList<>()).add(d);
            }
        }
        Map<Integer, List<Disaster>> frozenHands = new HashMap<>();
        hands.forEach((k, v) -> frozenHands.put(k, List.copyOf(v)));
        return new Disasters(out, Collections.unmodifiableMap(frozen), null,
                Collections.unmodifiableMap(frozenHands),
                tally[0], tally[1], tally[3], tally[2]);
    }

    /**
     * Reads one pack into the shared list and year index. The scraped pack and the hand-written
     * supplement differ in where their severity comes from and nowhere else, so they are read by
     * the same code with a flag.
     *
     * @return a problem, or null when the pack read cleanly
     */
    private static String readPack(String resource, boolean hand, List<Disaster> out,
                                   Map<Integer, List<Disaster>> byYear, int[] tally) {
        try (InputStream raw = Disasters.class.getResourceAsStream(resource)) {
            if (raw == null) {
                return resource + " is missing";
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(
                    new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonArray events = root.has("events") ? root.getAsJsonArray("events") : new JsonArray();
            for (JsonElement el : events) {
                JsonObject o = el.getAsJsonObject();
                Kind kind = Kind.of(text(o, "kind"));
                String name = text(o, "name");
                int start = o.has("start") ? o.get("start").getAsInt() : 0;

                int dead;
                Severity sev;
                if (hand) {
                    // the hand table says these outright, in their own fields: an explicit
                    // severity first, then the magnitude or VEI, then the dead
                    dead = o.has("dead") ? o.get("dead").getAsInt() : -1;
                    if (o.has("sev")) {
                        sev = new Severity(clamp01(o.get("sev").getAsDouble()), SeverityFrom.HAND);
                    } else if (o.has("mag")) {
                        double m = o.get("mag").getAsDouble();
                        sev = new Severity(clamp01(kind == Kind.EARTHQUAKE ? (m - 4.0d) / 5.0d
                                : m / 6.0d), SeverityFrom.HAND);
                    } else if (dead >= 10) {
                        sev = new Severity(clamp01(Math.log10(dead) / 6.0d), SeverityFrom.HAND);
                    } else {
                        sev = new Severity(DEFAULT_SEVERITY.getOrDefault(kind, 0.30d),
                                SeverityFrom.KIND_DEFAULT);
                    }
                } else {
                    dead = deadIn(name);
                    if (yearLost(kind, start, dead)) {
                        tally[3]++;
                        continue;               // the year did not survive; do not guess it
                    }
                    sev = severityOf(kind, name);
                }
                if (sev.from() == SeverityFrom.MAGNITUDE) {
                    tally[0]++;
                } else if (sev.from() == SeverityFrom.DEATHS) {
                    tally[1]++;
                } else if (sev.from() == SeverityFrom.HAND) {
                    tally[2]++;
                }

                double packRadius = o.has("radiusKm") ? o.get("radiusKm").getAsDouble() : 25.0d;
                Disaster d = new Disaster(
                        text(o, "id"), name, kind, start,
                        o.has("end") ? o.get("end").getAsInt() : NO_END,
                        o.has("lat") ? o.get("lat").getAsDouble() : 0.0d,
                        o.has("lon") ? o.get("lon").getAsDouble() : 0.0d,
                        hand ? packRadius : reach(packRadius, kind, sev.value()),
                        sev.value(), sev.from(), dead, text(o, "confidence"));
                out.add(d);
                // a disaster with no end year is treated as the year it is recorded in
                int last = d.endYear() == NO_END ? d.year() : d.endYear();
                for (int y = d.year(); y <= Math.min(last, d.year() + 20); y++) {
                    byYear.computeIfAbsent(y, k -> new ArrayList<>()).add(d);
                }
            }
            return null;
        } catch (Exception e) { // the mod must run without this file
            return resource + ": " + e;
        }
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    /** Kilometres between two points on the Earth. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0088d;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2.0d) * Math.sin(dp / 2.0d)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2.0d) * Math.sin(dl / 2.0d);
        return 2.0d * r * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }
}

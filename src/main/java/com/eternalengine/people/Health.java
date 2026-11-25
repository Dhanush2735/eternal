package com.eternalengine.people;

import com.eternalengine.civilization.Disasters;
import com.eternalengine.civilization.Disasters.Disaster;
import com.eternalengine.technology.TechnologyAtlas;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Step 38 part 7 (C20 diseases and medicine): <b>what can kill you here, and what anybody can do
 * about it</b>.
 *
 * <h3>The defect this fixes</h3>
 *
 * <p>{@code MedicineSystem.getMedicineTier("global", simYear, 5)} decides how good the medicine is
 * by the year alone: <b>in 1945 the entire planet became modern on the same day</b>. Antibiotics in
 * the Amazon, in rural Bihar, in Vienna — all of it one number, everywhere at once, because the
 * question it asks is "what year is it" and not "where are we".
 *
 * <p>That is the world-wide year tier, and this register replaces it. A remedy is not a date; it is
 * a thing that was learned in a place and then took a century to cross a continent. So every row in
 * {@code health.json.gz} is either
 *
 * <ul>
 * <li>a technology step 30a's atlas already knows — vaccination, anaesthesia, antiseptic surgery,
 *     antibiotics, children's vaccines — in which case the atlas is asked, per place and per year,
 *     when it got there; or</li>
 * <li>a year and a place of origin, in which case the atlas's own spread function,
 *     {@code arrivesIn(startYear, km, factor)}, is asked how long it took to reach anywhere else.
 *     The pace is a twentieth of the pace of news, because an idea is not a letter: quarantine
 *     reached Ragusa in 1377 and Marseille in 1476.</li>
 * </ul>
 *
 * <p>The consequence is the one that matters: <b>a person in 1945 with a infected cut in rural
 * Bihar does not have penicillin</b>, and this register says so instead of curing them.
 *
 * <h3>What is in the pack</h3>
 *
 * <p>Fourteen remedies, from the medicine of the four humours to oral rehydration, and eighteen
 * named diseases with what they are, how they move, where they were first seen, how many of the
 * people who caught them died, and which remedy ended them. Every row carries its source; where a
 * number is this table's own reading and no source's it is marked {@code estimated} and the printed
 * sentence repeats the word.
 *
 * <h3>What it does not claim</h3>
 *
 * <p>Nothing here is a diagnosis and nothing here is a prognosis. The case fatality is the
 * published figure for people who caught the disease <b>untreated and often malnourished</b>, not
 * a number about you. The join to step 36's hazard pack is by the kind of the hazard — plague,
 * epidemic, pandemic — and then to the disease whose name the hazard carries; where the hazard pack
 * has a sickness in force whose name matches nothing in this table, the register says the sickness
 * is unnamed rather than guessing which one it was.
 *
 * <p>The tier is the era of the newest medicine that has <b>reached this place</b>. It is not a
 * quality of the local doctor.
 */
public final class Health {

    /**
     * How fast an idea spreads, as a multiple of the technology atlas's own figure.
     *
     * <p><b>Read the atlas's units first.</b> {@code TechnologyAtlas.arrivesIn} measures the
     * atlas's speed table in <b>kilometres a year</b> — its loop compares {@code (end - t) * v}
     * against a distance without multiplying by the days in a year — while {@code daysToCross},
     * reading the very same table, measures it in kilometres a day. So the factor here is not a
     * fraction of the pace of news, it is a multiple of a number that is 365 times too small.
     * Three is what puts the arrivals where the record has them: quarantine, learned at Ragusa in
     * 1377, reaches England about 1447 and Marseille about 1476, which is what happened; the germ
     * theory, published in Paris in 1861, reaches Bihar about 1878.
     *
     * <p>That inconsistency in the atlas is a wart worth recording against step 30a, next to the
     * one already noted against {@code TechnologyAtlas.travel}.
     */
    public static final double SPREAD = 3.0d;

    /** The year a ship first carried the Old World's diseases to a place that had none of them.
     * Smallpox reached Tenochtitlan in 1520 and killed more of the Aztecs than the Spanish did;
     * there was no smallpox in the Americas in AD 1000, and a register that says otherwise is not
     * a register of disease. */
    private static final int CONTACT_AMERICAS = 1492;
    private static final int CONTACT_ALASKA = 1741;
    private static final int CONTACT_HAWAII = 1778;
    private static final int CONTACT_AUSTRALIA = 1788;
    private static final int CONTACT_PACIFIC = 1770;

    /** The eras, as this register reads them: the newest medicine that has reached the place. */
    public enum Tier {
        /** Nothing has reached here at all. */
        STONE_AGE("nothing anybody can do"),
        /** The humours, or a building kept for the sick. */
        ANCIENT("the medicine of the humours"),
        /** Quarantine: ships made to wait, for reasons nobody could yet explain. */
        MEDIEVAL("quarantine and what the old books say"),
        /** Variolation: giving the disease on purpose, and surviving it. */
        RENAISSANCE("variolation, and surgery without pain"),
        /** Clean water as a duty of the state, and the counting of cases. */
        INDUSTRIAL("public health, and the germ theory"),
        /** Penicillin and after. */
        MODERN("penicillin and vaccines");

        private final String phrase;

        Tier(String phrase) {
            this.phrase = phrase;
        }

        /** "... the medicine of the humours", for a sentence. */
        public String phrase() {
            return phrase;
        }
    }

    /**
     * One remedy: a thing people learned to do, in a place, in a year.
     *
     * <p>{@code arrivedAt} is the whole point. It is the year this reached <b>this place</b>, which
     * for the six remedies the technology atlas already holds is the atlas's own answer and for the
     * rest is the atlas's spread function run from the origin.
     */
    public record Remedy(String id, String name, int year, String place, double lat, double lon,
                         String applies, double mult, String note, String source, String mark,
                         String atlas) {

        /** The year this reached this place, or {@link Integer#MAX_VALUE} if it never did.
         * A remedy is carried by people, so it cannot arrive anywhere before the people could:
         * the medicine of the four humours did not reach the Amazon in BC 229. */
        public int arrivedAt(double atLat, double atLon) {
            TechnologyAtlas a = TechnologyAtlas.get();
            TechnologyAtlas.Tech tech = null;
            for (TechnologyAtlas.Tech t : a.all()) {
                if (t.id.equals(atlas)) {
                    tech = t;
                    break;
                }
            }
            int base;
            if (tech != null) {
                base = a.arrivalAt(tech, atLat, atLon);          // the atlas knows this one
            } else {
                double km = Migrations.kmBetween(lat, lon, atLat, atLon);
                base = km < 1.0d ? year : (int) Math.ceil(a.arrivesIn(year, km, SPREAD));
            }
            int contact = contactYear(atLat, atLon);
            if (contact > 0 && contactYear(lat, lon) != contact) {
                base = Math.max(base, contact);
            }
            return base;
        }

        /** Has it reached here by this year? */
        public boolean here(double atLat, double atLon, int inYear) {
            return arrivedAt(atLat, atLon) <= inYear;
        }

        /** Does it do anything for this class of disease? */
        public boolean helps(String diseaseClass) {
            return "all".equals(applies) || applies.equals(diseaseClass);
        }

        /** The number, and the mark that keeps it honest. */
        public String yearLine(double atLat, double atLon) {
            int y = arrivedAt(atLat, atLon);
            if (y == Integer.MAX_VALUE) {
                return "which never reached here";
            }
            return "which reached here in " + (y < 0 ? "BC " + (-y) : "AD " + y);
        }
    }

    /** One named disease. */
    public record Illness(String id, String name, String also, String germ, String route,
                          int originYear, String originPlace, double originLat, double originLon,
                          double cfr, String cls, String endedBy, String note, String source,
                          String mark, boolean endemic) {

        /**
         * Could this disease be here in this year? Three things can say no: it has not been seen
         * anywhere yet; it is carried by a flea or a mosquito and this is outside the warm belt;
         * or <b>it is a disease of the Old World and this place is over an ocean nobody had
         * crossed yet.</b>
         *
         * <p>That last one is the largest single fact in the history of disease. The Americas had
         * no smallpox, no measles, no typhus and no malaria before 1492, which is why somewhere
         * between half and nine tenths of the people living there died within a century of a ship
         * arriving. A register that gives Tenochtitlan smallpox in AD 1000 is worse than no
         * register at all.
         */
        public boolean possibleAt(double lat, double lon, int year) {
            if (year < originYear) {
                return false;
            }
            if (!endemic) {
                return false;                  // a pandemic is an event, not something "here"
            }
            int contact = contactYear(lat, lon);
            if (contact > 0 && contactYear(originLat, originLon) != contact && year < contact) {
                return false;
            }
            if (!"vector".equals(route)) {
                return true;
            }
            double a = Math.abs(lat);
            return "malaria".equals(id) || "yellow_fever".equals(id) ? a <= 40.0d : a <= 55.0d;
        }

        /** How many of the people who catch it die of it, <b>here and now</b>: the published
         * untreated figure, better by the best remedy that has reached this place and works on this
         * class of disease. */
        public double fatalityAt(double lat, double lon, int year) {
            double best = 1.0d;
            for (Remedy r : allRemedies()) {
                if (r.here(lat, lon, year) && r.helps(cls)) {
                    best = Math.min(best, r.mult());
                }
            }
            return cfr * best;
        }

        /** What fraction of the dying the medicine here takes away, or 0 if it takes none. */
        public double helpAt(double lat, double lon, int year) {
            return 1.0d - fatalityAt(lat, lon, year) / cfr;
        }

        /** The year the remedy that ends it reached this place, or MAX_VALUE if none ever did or
         * none ever will. */
        public int endedAt(double lat, double lon) {
            if (endedBy == null || endedBy.isEmpty()) {
                return Integer.MAX_VALUE;
            }
            for (Remedy r : allRemedies()) {
                if (endedBy.equals(r.id())) {
                    return r.arrivedAt(lat, lon);
                }
            }
            return Integer.MAX_VALUE;
        }

        /** Is it over here, in this year? Only smallpox can answer yes. */
        public boolean overBy(double lat, double lon, int year) {
            return endedAt(lat, lon) <= year;
        }

        /** The case fatality as a sentence, with the mark that keeps it honest. */
        public String cfrLine() {
            String n = String.format(Locale.ROOT, "%.0f%%", cfr * 100.0d);
            return switch (mark) {
                case "mixed" -> "kills about " + n + " of the people it reaches (the sources "
                    + "disagree; the middle figure is used)";
                case "estimated" -> "kills perhaps " + n + " of the people it reaches (estimated "
                    + "by this table, not by any source)";
                default -> "kills about " + n + " of the people it reaches";
            };
        }

        /** "in the water", "on the rat flea", for a sentence. A flea, a louse and a mosquito are
         * three different animals and three different histories. */
        public String routeLine() {
            if ("vector".equals(route)) {
                return "malaria".equals(id) || "yellow_fever".equals(id) ? "on a mosquito"
                    : "typhus".equals(id) ? "on the body louse, and therefore on crowded people"
                    : "on the rat flea";
            }
            return switch (route) {
                case "filth" -> "in the water";
                case "air" -> "in the air";
                case "blood" -> "in the blood";
                default -> "from one person to the next";
            };
        }

        /** "a bacterium", "a virus", for a sentence. */
        public String germLine() {
            return switch (germ) {
                case "viral" -> "a virus";
                case "parasitic" -> "a parasite";
                case "fungal" -> "a fungus";
                case "prion" -> "a prion";
                default -> "a bacterium";
            };
        }
    }

    /** What the register says about one place in one year. */
    public record Situation(double lat, double lon, int year, Tier tier, List<Remedy> known,
                            Illness worst, Disaster sickness, String line) {
        /** The best thing anybody here can do, or null where there is nothing. */
        public Remedy best() {
            Remedy best = null;
            for (Remedy r : known) {
                if (best == null || r.year() > best.year()) {
                    best = r;
                }
            }
            return best;
        }

        /** Is there anything at all here? */
        public boolean anyMedicine() {
            return !known.isEmpty();
        }
    }

    private static final Health INSTANCE = new Health();
    private final List<Remedy> remedies;
    private final List<Illness> illnesses;

    private Health() {
        List<Remedy> rr = new ArrayList<>();
        List<Illness> dd = new ArrayList<>();
        JsonObject root = read();
        if (root != null) {
            if (root.has("remedies")) {
                for (JsonElement e : root.getAsJsonArray("remedies")) {
                    JsonObject o = e.getAsJsonObject();
                    rr.add(new Remedy(
                        str(o, "id"), str(o, "name"), (int) Math.round(dbl(o, "year")),
                        str(o, "place"), dbl(o, "lat"), dbl(o, "lon"),
                        str(o, "applies", "all"), dbl(o, "mult", 1.0d),
                        str(o, "note"), str(o, "source"), str(o, "mark"), str(o, "atlas")));
                }
            }
            if (root.has("diseases")) {
                for (JsonElement e : root.getAsJsonArray("diseases")) {
                    JsonObject o = e.getAsJsonObject();
                    dd.add(new Illness(
                        str(o, "id"), str(o, "name"), str(o, "also"), str(o, "germ"),
                        str(o, "route"), (int) Math.round(dbl(o, "oyear")), str(o, "oplace"),
                        dbl(o, "olat"), dbl(o, "olon"), dbl(o, "cfr"), str(o, "cls", "all"),
                        str(o, "ended"), str(o, "note"), str(o, "source"), str(o, "mark"),
                        o.has("endemic") && o.get("endemic").getAsBoolean()));
                }
            }
        }
        rr.sort(Comparator.comparingInt(Remedy::year));
        dd.sort(Comparator.comparingInt(Illness::originYear));
        this.remedies = Collections.unmodifiableList(rr);
        this.illnesses = Collections.unmodifiableList(dd);
    }

    /** The one register. */
    public static Health get() {
        return INSTANCE;
    }

    /** Every remedy in the pack, earliest first. */
    public static List<Remedy> allRemedies() {
        return get().remedies;
    }

    /** Every disease in the pack, earliest first. */
    public static List<Illness> allIllnesses() {
        return get().illnesses;
    }

    /** How many remedies and how many diseases we hold. */
    public static int remedyCount() {
        return get().remedies.size();
    }

    public static int illnessCount() {
        return get().illnesses.size();
    }

    /** The disease with this id, or null. */
    public static Illness find(String id) {
        for (Illness d : get().illnesses) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    /** The remedy with this id, or null. */
    public static Remedy remedy(String id) {
        for (Remedy r : get().remedies) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** Everything that has reached this place by this year, earliest first. */
    public static List<Remedy> knownAt(double lat, double lon, int year) {
        List<Remedy> out = new ArrayList<>();
        for (Remedy r : get().remedies) {
            if (r.here(lat, lon, year)) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * <b>The medicine tier for this place in this year</b> — the replacement for the world-wide
     * year tier. It is the era of the newest medicine that has actually arrived here, so in 1945
     * it is MODERN in Oxford and INDUSTRIAL in much of the world, and the register will not cure
     * a person with penicillin that has never been within a thousand miles of them.
     */
    public static Tier tierAt(double lat, double lon, int year) {
        int newest = Integer.MIN_VALUE;
        for (Remedy r : get().remedies) {
            if (r.here(lat, lon, year)) {
                newest = Math.max(newest, r.year());
            }
        }
        if (newest == Integer.MIN_VALUE) {
            return Tier.STONE_AGE;
        }
        if (newest >= 1941) {
            return Tier.MODERN;
        }
        if (newest >= 1846) {
            return Tier.INDUSTRIAL;
        }
        if (newest >= 1721) {
            return Tier.RENAISSANCE;
        }
        if (newest >= 1377) {
            return Tier.MEDIEVAL;
        }
        return Tier.ANCIENT;
    }

    /**
     * The multiplier this place's medicine applies to dying of anything: 1.0 where nothing has
     * arrived, and the best multiplier on offer among everything that has. This is the number
     * {@code MedicineSystem} should have been using all along.
     */
    public static double careAt(double lat, double lon, int year) {
        double best = 1.0d;
        for (Remedy r : get().remedies) {
            if (r.here(lat, lon, year) && "all".equals(r.applies())) {
                best = Math.min(best, r.mult());
            }
        }
        return best;
    }

    /** The deadliest disease that could be here in this year, or null where none could. */
    public static Illness worstAt(double lat, double lon, int year) {
        Illness worst = null;
        double worstF = 0.0d;
        for (Illness d : get().illnesses) {
            if (!d.possibleAt(lat, lon, year) || d.overBy(lat, lon, year)) {
                continue;
            }
            double f = d.fatalityAt(lat, lon, year);
            if (f > worstF) {
                worstF = f;
                worst = d;
            }
        }
        return worst;
    }

    /** The sickness actually in force here and now, from step 36's hazard pack, or null. */
    public static Disaster sicknessAt(double lat, double lon, int year) {
        for (Disaster d : Disasters.get().all()) {
            if (!d.inForceIn(year) || !d.reaches(lat, lon)) {
                continue;
            }
            switch (d.kind()) {
                case PLAGUE, EPIDEMIC, PANDEMIC -> {
                    return d;
                }
                default -> {
                    // not a sickness: keep looking
                }
            }
        }
        return null;
    }

    /** Which named disease a hazard is, if the packs can agree by name. Null means the sickness is
     * in force and this table does not know what it was — which the prose says rather than guessing. */
    public static Illness nameOf(Disaster d) {
        if (d == null) {
            return null;
        }
        String n = d.name() == null ? "" : d.name().toLowerCase(Locale.ROOT);
        if (n.contains("black death") || n.contains("justinian") || n.contains("plague")) {
            return n.contains("justinian") ? find("plague_justinian") : find("black_death");
        }
        if (n.contains("cholera")) {
            return find("cholera");
        }
        if (n.contains("influenza") || n.contains("flu")) {
            return find("influenza_1918");
        }
        if (n.contains("smallpox")) {
            return find("smallpox");
        }
        if (n.contains("typhus")) {
            return find("typhus");
        }
        if (n.contains("typhoid")) {
            return find("typhoid");
        }
        if (n.contains("tubercul")) {
            return find("tuberculosis");
        }
        if (n.contains("covid") || n.contains("coronavirus")) {
            return find("covid_19");
        }
        if (n.contains("aids") || n.contains("hiv")) {
            return find("hiv_aids");
        }
        return null;
    }

    /** One place in one year, read whole. */
    public static Situation at(double lat, double lon, int year) {
        Tier tier = tierAt(lat, lon, year);
        List<Remedy> known = knownAt(lat, lon, year);
        Illness worst = worstAt(lat, lon, year);
        Disaster sickness = sicknessAt(lat, lon, year);
        return new Situation(lat, lon, year, tier, known, worst, sickness,
            buildLine(lat, lon, year, tier, known, worst, sickness));
    }

    private static String buildLine(double lat, double lon, int year, Tier tier,
                                    List<Remedy> known, Illness worst, Disaster sickness) {
        StringBuilder sb = new StringBuilder();
        sb.append("The medicine here is ").append(tier.phrase()).append('.');
        if (sickness != null) {
            Illness named = nameOf(sickness);
            sb.append(" What is on them is the ").append(sickness.name()).append(" of ")
              .append(sickness.yearLabel()).append(", ");
            if (named == null) {
                sb.append("and this table does not know which sickness that was.");
            } else {
                sb.append("which is ").append(named.name()).append(" — ").append(named.germLine())
                  .append(", carried ").append(named.routeLine()).append(", and it ")
                  .append(named.cfrLine()).append('.');
                double help = named.helpAt(lat, lon, year);
                if (help < 0.05d) {
                    sb.append(" Almost nothing in this place can touch it.");
                } else {
                    sb.append(String.format(Locale.ROOT,
                        " What is here takes about %.0f%% of the dying away.", help * 100.0d));
                }
            }
        } else if (worst != null) {
            sb.append(" Of the diseases that can reach them, the deadliest is ")
              .append(worst.name()).append(", which ").append(worst.cfrLine()).append('.');
        }
        if (!known.isEmpty()) {
            Remedy newest = known.get(known.size() - 1);
            sb.append(" The newest thing that has reached them is ").append(newest.name())
              .append(", ").append(newest.yearLine(lat, lon)).append('.');
        } else {
            sb.append(" Nothing anybody has ever learned has reached them.");
        }
        return sb.toString();
    }

    /** One sentence about this place in this year: what the medicine is, what is on them, and
     * whether anything here can touch it. */
    public static String line(double lat, double lon, int year) {
        return at(lat, lon, year).line();
    }

    /** The short form, for a card or a tooltip. */
    public static String card(double lat, double lon, int year) {
        Situation s = at(lat, lon, year);
        StringBuilder sb = new StringBuilder();
        sb.append("medicine: ").append(s.tier().phrase());
        sb.append(String.format(Locale.ROOT, " (care x%.2f)", careAt(lat, lon, year)));
        if (s.worst() != null) {
            sb.append("\n  worst: ").append(s.worst().name())
              .append(String.format(Locale.ROOT, " - %.0f%% fatal here",
                  s.worst().fatalityAt(lat, lon, year) * 100.0d));
        }
        if (s.sickness() != null) {
            sb.append("\n  in force: ").append(s.sickness().name()).append(' ')
              .append(s.sickness().yearLabel());
        }
        return sb.toString();
    }

    /** Which side of the world a point is on, on the atlas's own terms. */
    static String zoneOf(double lat, double lon) {
        if (lat > 51.0d && lon < -129.0d && lon > -170.0d) {
            return "alaska";
        }
        if (lat > 18.0d && lat < 23.0d && lon < -154.0d && lon > -161.0d) {
            return "hawaii";
        }
        if (lon > -170.0d && lon < -30.0d && lat > -60.0d) {
            return "americas";
        }
        if (lat < -10.0d && lat > -45.0d && lon > 112.0d && lon < 154.0d) {
            return "australia";
        }
        if ((lon > 154.0d || lon < -120.0d) && lat < 30.0d && lat > -50.0d) {
            return "pacific";
        }
        return "old";
    }

    /** The year the Old World's diseases could first have reached this place: 1492 in the
     * Americas, and 0 in the Old World, where there was never an ocean in the way. */
    static int contactYear(double lat, double lon) {
        return switch (zoneOf(lat, lon)) {
            case "americas" -> CONTACT_AMERICAS;
            case "alaska" -> CONTACT_ALASKA;
            case "hawaii" -> CONTACT_HAWAII;
            case "australia" -> CONTACT_AUSTRALIA;
            case "pacific" -> CONTACT_PACIFIC;
            default -> 0;
        };
    }

    private static JsonObject read() {
        try (InputStream in = Health.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader r = new InputStreamReader(
                    new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    private static final String RESOURCE =
        "/data/eternalengine/eternal/history/health.json.gz";

    private static String str(JsonObject o, String k) {
        return str(o, k, "");
    }

    private static String str(JsonObject o, String k, String dflt) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : dflt;
    }

    private static double dbl(JsonObject o, String k) {
        return dbl(o, k, 0.0d);
    }

    private static double dbl(JsonObject o, String k, double dflt) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : dflt;
    }

}

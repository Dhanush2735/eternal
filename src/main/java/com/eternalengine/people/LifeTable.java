package com.eternalengine.people;

import com.eternalengine.knowledge.KnowledgeLevels;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 38 (D2, D17, and the life-span half of C20): how long people lived, and when they married.
 *
 * <p>Before this class nobody in the mod had ever been born. {@code VirtualVillager} lost a point
 * of health every twenty years and died at zero, so a person had no age, no marriage, no children
 * and no cause of death. This is the register that replaces that with something drawn from the
 * record: life expectancy by place and year, and the age at first marriage.
 *
 * <p><b>The distinction the whole table turns on.</b> Life expectancy at birth is not the adult
 * lifespan. In a population whose e0 is 26, a fifteen-year-old expects another thirty-odd years,
 * and the most common age of adult death in England between 1600 and 1800 was about seventy. A
 * register that rolls deaths from e0 alone builds a world with no grandparents in it, so every row
 * carries at least two anchors — at birth and at an adult age — and the curve between them is what
 * a death is drawn from.
 *
 * <p><b>The curve is derived, not invented.</b> With {@code e(x)} the years of life remaining at
 * age {@code x}, the force of mortality follows exactly from the definition of a life table:
 * <pre>
 *     mu(x) = (1 + e'(x)) / e(x)
 * </pre>
 * so a piecewise-linear {@code e(x)} gives a piecewise-constant {@code mu(x)} that can be
 * integrated in closed form, and survival is {@code S(x) = exp(-integral of mu)}. Nothing here is
 * fitted or guessed beyond the anchors themselves, which are the published figures in
 * {@code life_table.py}.
 */
public final class LifeTable {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/life_table.json.gz";

    private static final LifeTable INSTANCE = new LifeTable();

    private final List<Mortality> mortality = new ArrayList<>();
    private final List<Marriage> marriage = new ArrayList<>();
    private final Map<String, double[]> survivalCache = new HashMap<>();
    private boolean loaded;

    private LifeTable() {
    }

    // ------------------------------------------------------------------ the rows

    /**
     * How long people lived: {@code anchors} are pairs (age, years of life remaining at that
     * age), the first of which is always (0, e0). {@code boxes} empty means the whole world.
     */
    public record Mortality(String id, String name, double[][] boxes, int from, Integer to,
                            double[][] anchors, int infant, boolean urban, String mark,
                            String note, String source) {

        /** "the world in 1800". */
        public String title() {
            return name;
        }

        /** Years of life remaining at birth. */
        public double atBirth() {
            return anchors[0][1];
        }

        /** Recorded, mixed or estimated — how much of this row is measured. */
        public boolean estimated() {
            return "estimated".equals(mark);
        }
    }

    /**
     * When they married and how many children they had. {@code wife} and {@code husband} are mean
     * ages at first marriage; {@code celibate} is the share, in per cent, who never marry.
     */
    public record Marriage(String id, String name, double[][] boxes, int from, Integer to,
                           double wife, double husband, int celibate, double births, String mark,
                           String note, String source) {

        public boolean estimated() {
            return "estimated".equals(mark);
        }
    }

    // ------------------------------------------------------------------ loading

    public static synchronized LifeTable get() {
        INSTANCE.load();
        return INSTANCE;
    }

    private synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = LifeTable.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            try (GZIPInputStream gz = new GZIPInputStream(in);
                 InputStreamReader r = new InputStreamReader(gz, StandardCharsets.UTF_8)) {
                JsonObject doc = JsonParser.parseReader(r).getAsJsonObject();
                readMortality(doc.getAsJsonArray("mortality"));
                readMarriage(doc.getAsJsonArray("marriage"));
            }
        } catch (Exception e) {
            mortality.clear();
            marriage.clear();
        }
    }

    private void readMortality(JsonArray rows) {
        if (rows == null) {
            return;
        }
        for (JsonElement e : rows) {
            JsonObject m = e.getAsJsonObject();
            mortality.add(new Mortality(
                    str(m, "id"), str(m, "name"), boxes(m.getAsJsonArray("boxes")),
                    (int) dbl(m, "from"), m.get("to").isJsonNull() ? null : (int) dbl(m, "to"),
                    pairs(m.getAsJsonArray("anchors")), (int) dbl(m, "infant"),
                    m.has("urban") && m.get("urban").getAsBoolean(),
                    str(m, "mark"), str(m, "note"), str(m, "source")));
        }
    }

    private void readMarriage(JsonArray rows) {
        if (rows == null) {
            return;
        }
        for (JsonElement e : rows) {
            JsonObject m = e.getAsJsonObject();
            marriage.add(new Marriage(
                    str(m, "id"), str(m, "name"), boxes(m.getAsJsonArray("boxes")),
                    (int) dbl(m, "from"), m.get("to").isJsonNull() ? null : (int) dbl(m, "to"),
                    dbl(m, "wife"), dbl(m, "husband"), (int) dbl(m, "celibate"),
                    dbl(m, "births"), str(m, "mark"), str(m, "note"), str(m, "source")));
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static double dbl(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0.0d : e.getAsDouble();
    }

    private static double[][] boxes(JsonArray arr) {
        if (arr == null || arr.isEmpty()) {
            return new double[0][];
        }
        double[][] out = new double[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = doubles(arr.get(i).getAsJsonArray());
        }
        return out;
    }

    private static double[][] pairs(JsonArray arr) {
        if (arr == null) {
            return new double[0][];
        }
        double[][] out = new double[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = doubles(arr.get(i).getAsJsonArray());
        }
        return out;
    }

    private static double[] doubles(JsonArray arr) {
        double[] out = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = arr.get(i).getAsDouble();
        }
        return out;
    }

    // ------------------------------------------------------------------ lookup

    /** How long people lived at this place in this year. Empty when the pack is missing. */
    public Mortality mortalityAt(double lat, double lon, int year) {
        return mortalityAt(lat, lon, year, false);
    }

    /**
     * How long people lived in this place — a town, if {@code urban} is set, otherwise the land
     * around it. The distinction is not decoration: a child born in Liverpool in 1840 could
     * expect twelve fewer years than one born twenty miles away in the Lancashire countryside,
     * and the two answers come from the same published series.
     */
    public Mortality mortalityAt(double lat, double lon, int year, boolean urban) {
        Mortality best = null;
        double[] bestKey = null;
        for (Mortality row : mortality) {
            if (!covers(row.from(), row.to(), row.boxes(), lat, lon, year)) {
                continue;
            }
            double[] key = key(row.urban() == urban, row.boxes(), row.from());
            if (bestKey == null || less(key, bestKey)) {
                best = row;
                bestKey = key;
            }
        }
        return best;
    }

    /** When they married at this place in this year. Empty when the pack is missing. */
    public Marriage marriageAt(double lat, double lon, int year) {
        Marriage best = null;
        double[] bestKey = null;
        for (Marriage row : marriage) {
            if (!covers(row.from(), row.to(), row.boxes(), lat, lon, year)) {
                continue;
            }
            double[] key = key(true, row.boxes(), row.from());
            if (bestKey == null || less(key, bestKey)) {
                best = row;
                bestKey = key;
            }
        }
        return best;
    }

    private static boolean covers(int from, Integer to, double[][] boxes, double lat, double lon,
                                  int year) {
        if (year < from || (to != null && year > to)) {
            return false;
        }
        return boxes.length == 0 || inside(boxes, lat, lon);
    }

    /**
     * The most specific row that holds: a row of the right kind of settlement first (a town row
     * for a town, a country row for the countryside), then the smallest box that contains the
     * place, then the later start. The whole world scores as infinitely large, so a named box
     * always beats it.
     */
    private static double[] key(boolean kindMatches, double[][] boxes, int from) {
        return new double[]{kindMatches ? 0.0d : 1.0d,
                boxes.length == 0 ? Double.MAX_VALUE : area(boxes), from};
    }

    private static boolean less(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i];
            }
        }
        return false;
    }

    private static boolean inside(double[][] boxes, double lat, double lon) {
        for (double[] b : boxes) {
            if (lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3]) {
                return true;
            }
        }
        return false;
    }

    private static double area(double[][] boxes) {
        double a = 0.0d;
        for (double[] b : boxes) {
            a += (b[1] - b[0]) * (b[3] - b[2]);
        }
        return a;
    }

    // ------------------------------------------------------------------ the curve

    /**
     * Years of life remaining at this age, on the row's own anchors, held along the last slope
     * past the last of them.
     */
    public double expectancy(Mortality m, double age) {
        if (m == null || m.anchors().length == 0) {
            return 0.0d;
        }
        double[][] a = m.anchors();
        if (age <= a[0][0]) {
            return a[0][1];
        }
        for (int i = 1; i < a.length; i++) {
            if (age <= a[i][0]) {
                double span = a[i][0] - a[i - 1][0];
                double f = span <= 0.0d ? 0.0d : (age - a[i - 1][0]) / span;
                return a[i - 1][1] + f * (a[i][1] - a[i - 1][1]);
            }
        }
        // past the last anchor: carry the last slope until there is nothing left
        int n = a.length - 1;
        double span = a[n][0] - a[n - 1][0];
        double slope = span <= 0.0d ? -1.0d : (a[n][1] - a[n - 1][1]) / span;
        if (slope >= 0.0d) {
            slope = -1.0d;
        }
        return Math.max(0.0d, a[n][1] + slope * (age - a[n][0]));
    }

    /** The age at which there is no life left to expect: the oldest anyone here gets. */
    public int maxAge(Mortality m) {
        if (m == null) {
            return 0;
        }
        for (int age = 1; age < 130; age++) {
            if (expectancy(m, age) <= 0.0d) {
                return age;
            }
        }
        return 129;
    }

    /**
     * The cumulative hazard to age {@code x}: the integral of {@code mu = (1 + e') / e}. On a
     * stretch where {@code e} runs linearly from {@code e0} to {@code e1} over {@code span} years
     * this integrates in closed form to {@code span/(e1-e0) * ln(e1/e0) + ln(e1/e0)}.
     */
    private static double hazard(double age0, double e0, double age1, double e1) {
        if (age1 <= age0) {
            return 0.0d;
        }
        double span = age1 - age0;
        if (e0 <= 0.0d || e1 <= 0.0d) {
            return Double.POSITIVE_INFINITY;
        }
        double log = Math.log(e1 / e0);
        double integral;
        if (Math.abs(e1 - e0) < 1.0e-9d) {
            integral = span / e0;
        } else {
            integral = span / (e1 - e0) * log;
        }
        return integral + log;
    }

    /**
     * The share of newborns still alive at each age, indexed by age in years. Cached: this is the
     * whole curve, and a village asks for it thousands of times.
     */
    public synchronized double[] survival(Mortality m) {
        double[] hit = survivalCache.get(m.id());
        if (hit != null) {
            return hit;
        }
        int max = Math.max(1, maxAge(m));
        double[] s = new double[max + 1];
        double[][] a = m.anchors();
        s[0] = 1.0d;
        double cumulative = 0.0d;
        int seg = 0;
        for (int age = 1; age <= max; age++) {
            // walk the anchors, accumulating hazard one year at a time
            while (seg < a.length - 2 && age > a[seg + 1][0]) {
                seg++;
            }
            double age0 = Math.max(a[seg][0], age - 1);
            double e0 = expectancy(m, age0);
            double e1 = expectancy(m, age);
            cumulative += hazard(age0, e0, age, e1);
            s[age] = Math.exp(-cumulative);
        }
        survivalCache.put(m.id(), s);
        return s;
    }

    /** The share of newborns who reach this age. */
    public double survives(Mortality m, int age) {
        double[] s = survival(m);
        if (age <= 0) {
            return 1.0d;
        }
        return age >= s.length ? 0.0d : s[age];
    }

    // ------------------------------------------------------------------ a life

    /**
     * The age this person dies at, drawn from the survivor curve of their own place and year.
     * Anything that kills them early — a plague year, a famine, a war — is the caller's business;
     * this is the ordinary death that comes for everyone.
     */
    public int deathAge(Mortality m, long salt) {
        if (m == null) {
            return 60;
        }
        double[] s = survival(m);
        double r = uniform(salt * 0x9E3779B97F4A7C15L >>> 11);
        for (int age = 1; age < s.length; age++) {
            if (r > s[age]) {
                return age;
            }
        }
        return s.length - 1;
    }

    /** The age this person marries at, or {@code -1} if they never do. */
    public int marriageAge(Marriage g, boolean male, long salt) {
        if (g == null) {
            return -1;
        }
        if (uniform((salt * 0x2545F4914F6CDD1DL) >>> 11) * 100.0d < g.celibate()) {
            return -1;
        }
        double mean = male ? g.husband() : g.wife();
        // a triangular spread of about three years either side: marriage age clusters hard on
        // the mean, and a wide one puts brides of seventeen at twelve, which never happened
        double u1 = uniform((salt * 0x27D4EB2F165667C5L) >>> 11);
        double u2 = uniform((salt * 0x165667C527D4EB2FL) >>> 11);
        double spread = (u1 + u2 - 1.0d) * 3.0d;
        return (int) Math.round(Math.max(Math.min(14.0d, mean - 1.0d), mean + spread));
    }

    /**
     * How many children this person has, drawn from the regime's completed family size. A couple
     * that marries late, or one of them not at all, has fewer; the row's {@code births} is for a
     * woman who married and survived her childbearing years.
     */
    public int children(Marriage g, int marriageAge, long salt) {
        if (g == null || marriageAge < 0) {
            return 0;
        }
        double mean = g.births();
        // Childbearing runs from the marriage to about forty-five. The row's own figure is what
        // a woman marrying at the row's own mean age bears, so the span is measured against that
        // and not against an arbitrary number of years: marrying young is worth about a fifth
        // more children, which is what the English record shows.
        double span = Math.max(1.0d, 45.0d - g.wife());
        double years = Math.max(0.0d, Math.min(32.0d, 45.0d - marriageAge));
        double scaled = mean * years / span;
        double r = uniform((salt * 0x85EBCA6B2F6CDD1DL) >>> 11);
        // a Poisson-ish spread around the mean, capped at what a body can bear
        double u = uniform((salt * 0xC2B2AE356165667CL) >>> 11);
        // a spread about as wide as a Poisson's about this mean, and no wider: a jitter of three
        // left a fifth of all couples childless, which is not childlessness, it is a broken dice
        double jitter = 1.0d + 1.2d * (u + r - 1.0d);
        return (int) Math.max(0, Math.min(15, Math.round(scaled * jitter)));
    }

    private static double uniform(long bits) {
        // a deterministic hash to [0,1), so the same salt always gives the same person
        long x = bits ^ (bits >>> 33);
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= x >>> 33;
        return (x >>> 11) * 0x1.0p-53;
    }

    /**
     * One life, drawn from the place and year it was born into: when they were born, how long
     * they had, when they married, and how many children they had. This is what D17 asks for and
     * what the mod has never had.
     */
    public record Life(int birthYear, int deathYear, int marriageYear, int children, boolean male,
                       Mortality mortality, Marriage marriage) {

        /** How long it turned out to be. */
        public int lifespan() {
            return deathYear - birthYear;
        }

        public boolean married() {
            return marriageYear > 0;
        }

        /** "born 1348, died 1391, married at 24, four children" — in the mod's own words. */
        public String describe() {
            StringBuilder sb = new StringBuilder("born ").append(birthYear)
                    .append(", died ").append(deathYear).append(" at ").append(lifespan());
            if (married()) {
                sb.append(", married at ").append(marriageYear - birthYear)
                  .append(", ").append(children == 1 ? "one child" : children + " children");
            } else {
                sb.append(", never married");
            }
            return sb.toString();
        }
    }

    /**
     * Draw one life. The salt makes it stable: the same salt, place and year always give the same
     * person, so a remembered villager does not come back someone else.
     */
    public Life life(long salt, int birthYear, double lat, double lon, boolean male) {
        Mortality m = mortalityAt(lat, lon, birthYear);
        Marriage g = marriageAt(lat, lon, birthYear);
        int death = deathAge(m, salt);
        int husband = marriageAge(g, true, salt + 1L);
        int wife = marriageAge(g, false, salt);
        int mine = male ? husband : wife;
        // nobody marries in their grave, and nobody marries who never did
        boolean married = mine >= 0 && mine < death;
        // The children are the couple's, so a man's are bounded by his wife's fertile span and
        // not his own: a man of thirty married to a girl of twenty has her childbearing years
        // ahead of him, not his.
        int kids = married ? children(g, male ? (int) Math.round(g.wife()) : mine, salt + 2L) : 0;
        return new Life(birthYear, birthYear + death, married ? birthYear + mine : 0, kids, male,
                m, g);
    }

    // ------------------------------------------------------------------ words

    /** "People here expect about 26 years of life, but a child who reaches fifteen sees 47." */
    public String mortalityLine(double lat, double lon, int year) {
        Mortality m = mortalityAt(lat, lon, year);
        if (m == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("People here expect about ")
                .append(Math.round(m.atBirth())).append(" years of life, but one who reaches "
                        + "fifteen sees ").append(Math.round(15.0d + expectancy(m, 15)));
        sb.append('.');
        if (m.estimated()) {
            sb.append(" (estimated: nothing is published for this place and year.)");
        }
        return sb.toString();
    }

    /** "Women marry at about 25 and men at about 27; one in seven never marries at all." */
    public String marriageLine(double lat, double lon, int year) {
        Marriage g = marriageAt(lat, lon, year);
        if (g == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Women marry at about ").append(Math.round(g.wife()))
                .append(" and men at about ").append(Math.round(g.husband())).append(';');
        if (g.celibate() >= 10) {
            sb.append(" about one in ").append(Math.round(100.0d / g.celibate()))
              .append(" never marries at all.");
        } else if (g.celibate() >= 3) {
            sb.append(" a few never marry at all.");
        } else {
            sb.append(" almost everybody marries.");
        }
        return sb.toString();
    }

    /** What a person of this standing would say about how long people live. Never a source name. */
    public String says(String role, double lat, double lon, int year) {
        Mortality m = mortalityAt(lat, lon, year);
        if (m == null) {
            return "";
        }
        String line = mortalityLine(lat, lon, year);
        if (KnowledgeLevels.of(role) >= 2) {
            return "It is reckoned that " + Character.toLowerCase(line.charAt(0))
                    + line.substring(1);
        }
        return "Folk here say " + Character.toLowerCase(line.charAt(0)) + line.substring(1);
    }

    /** "the world in 1800" — the row's own name, for the card. */
    public String regime(double lat, double lon, int year) {
        Mortality m = mortalityAt(lat, lon, year);
        return m == null ? "" : m.name();
    }

    public int mortalityCount() {
        return mortality.size();
    }

    public int marriageCount() {
        return marriage.size();
    }

    public String source() {
        return String.format(Locale.ROOT, "%d mortality rows, %d marriage rows",
                mortality.size(), marriage.size());
    }
}

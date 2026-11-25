package com.eternalengine.people;

import com.eternalengine.people.LifeTable.Marriage;
import com.eternalengine.people.LifeTable.Mortality;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Step 38 (D3): families and homes — what a household is made of, by place and year.
 *
 * <p>Before this the mod remembered sixty-four people per square and called that a population.
 * Walk into a house and there was nobody inside who was anybody to anybody: no spouse, no
 * children, no parents, no servants, no home. This register fills a house with people who are
 * related to each other, at ages the life table is willing to vouch for.
 *
 * <p><b>The thing almost everybody gets wrong.</b> The extended family is not the traditional
 * family. Laslett's null hypothesis, from the Cambridge Group's reconstruction of a hundred
 * English communities, is that the household was "always and invariably nuclear unless the
 * contrary can be proven". Mean household size in England and Wales sat at 4.75 or a little
 * below from the sixteenth century to 1911 — through the whole of the Industrial Revolution —
 * and only a tenth of pre-industrial households were extended, which is the same tenth as
 * England in 1966. The Industrial Revolution did not smash the extended family; there was no
 * extended family to smash.
 *
 * <p>What did vary is where the exceptions sat, and three words carry it: <b>nuclear</b> (a man,
 * his wife and their children), <b>stem</b> (one married child stays and inherits, in Japan and
 * across a band of Europe from the Pyrenees to Bohemia), and <b>joint</b> (married brothers with
 * their wives and children in one property-holding unit, in India).
 */
public final class Households {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/households.json.gz";

    private static final Households INSTANCE = new Households();

    private final List<Household> rows = new ArrayList<>();
    private boolean loaded;

    private Households() {
    }

    /**
     * What a household is like here: how many people, and how they are arranged. The four shares
     * — {@code nuclear}, {@code stem}, {@code joint}, {@code solo} — add to a hundred.
     */
    public record Household(String id, String name, double[][] boxes, int from, Integer to,
                            double size, int nuclear, int stem, int joint, int solo, int leave,
                            int servants, int widow, boolean urban, String mark, String note,
                            String source, boolean pub) {

        /** Whether {@code size} was published for this place and year, or is the table's own. */
        public boolean sizePublished() {
            return pub;
        }

        /** Recorded, mixed or estimated — how much of this row is measured. */
        public boolean estimated() {
            return "estimated".equals(mark);
        }

        /** The share of households that hold more than one conjugal family. */
        public int complex() {
            return stem + joint;
        }
    }

    /** One person in a house: who they are to the head, and how old they are. */
    public record Member(String kin, String relation, boolean male, int age) {

        /** "his wife, 36". */
        public String describe() {
            return relation + ", " + age;
        }
    }

    /** One house and everybody in it. */
    public record Home(Household kind, String structure, List<Member> members, int away) {

        public int size() {
            return members.size();
        }

        /** Whether anybody of this kind lives here: "servant", "child", "parent", "spouse". */
        public boolean has(String kin) {
            for (Member m : members) {
                if (kin.equals(m.kin())) {
                    return true;
                }
            }
            return false;
        }

        /** "a man of 41, his wife of 36, their sons of 12 and 7, and his mother of 71." */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < members.size(); i++) {
                Member m = members.get(i);
                if (i == 0) {
                    sb.append(m.male() ? "a man of " : "a woman of ").append(m.age());
                } else {
                    sb.append(i == members.size() - 1 && members.size() > 1 ? " and " : ", ")
                      .append(m.relation()).append(" of ").append(m.age());
                }
            }
            sb.append('.');
            if (away > 0) {
                sb.append(away == 1 ? " One has left home." : " " + away + " have left home.");
            }
            return sb.toString();
        }

        /**
         * The same house, said by the person who lives in it: "There are five of us at home:
         * my wife, our son of 12, our daughter of 7 and my mother of 71."
         */
        public String firstPerson() {
            if (members.isEmpty()) {
                return "";
            }
            if (members.size() == 1) {
                return "I live alone.";
            }
            StringBuilder sb = new StringBuilder("There are ").append(members.size())
                    .append(" of us at home: ");
            for (int i = 1; i < members.size(); i++) {
                Member m = members.get(i);
                if (i > 1) {
                    sb.append(i == members.size() - 1 ? " and " : ", ");
                }
                sb.append(mine(m.relation())).append(" of ").append(m.age());
            }
            sb.append('.');
            if (away > 0) {
                sb.append(away == 1 ? " One has left home." : " " + away + " have left home.");
            }
            return sb.toString();
        }

        /** "his wife" -> "my wife"; "their son" -> "our son"; "a servant" stays as it is. */
        private static String mine(String relation) {
            if (relation.startsWith("his ") || relation.startsWith("her ")) {
                return "my " + relation.substring(4);
            }
            if (relation.startsWith("their ")) {
                return "our " + relation.substring(6);
            }
            return relation;
        }
    }

    // ------------------------------------------------------------------ loading

    public static synchronized Households get() {
        INSTANCE.load();
        return INSTANCE;
    }

    private synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = Households.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            try (GZIPInputStream gz = new GZIPInputStream(in);
                 InputStreamReader r = new InputStreamReader(gz, StandardCharsets.UTF_8)) {
                JsonArray arr = JsonParser.parseReader(r).getAsJsonObject()
                        .getAsJsonArray("households");
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    rows.add(new Household(
                            str(o, "id"), str(o, "name"), boxes(o.getAsJsonArray("boxes")),
                            (int) dbl(o, "from"), o.get("to").isJsonNull() ? null
                            : (int) dbl(o, "to"),
                            dbl(o, "size"), (int) dbl(o, "nuclear"), (int) dbl(o, "stem"),
                            (int) dbl(o, "joint"), (int) dbl(o, "solo"), (int) dbl(o, "leave"),
                            (int) dbl(o, "servants"), (int) dbl(o, "widow"),
                            o.has("urban") && o.get("urban").getAsBoolean(),
                            str(o, "mark"), str(o, "note"), str(o, "source"),
                            o.has("pub") && o.get("pub").getAsBoolean()));
                }
            }
        } catch (Exception e) {
            rows.clear();
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
            JsonArray b = arr.get(i).getAsJsonArray();
            out[i] = new double[]{b.get(0).getAsDouble(), b.get(1).getAsDouble(),
                    b.get(2).getAsDouble(), b.get(3).getAsDouble()};
        }
        return out;
    }

    // ------------------------------------------------------------------ lookup

    public Household at(double lat, double lon, int year) {
        return at(lat, lon, year, false);
    }

    /**
     * The most specific row that holds: a town row for a town first, then the smallest box, then
     * the later start. The whole world scores as infinitely large, so a named box always beats it.
     */
    public Household at(double lat, double lon, int year, boolean urban) {
        Household best = null;
        double[] bestKey = null;
        for (Household row : rows) {
            if (year < row.from() || (row.to() != null && year > row.to())) {
                continue;
            }
            if (row.boxes().length > 0 && !inside(row.boxes(), lat, lon)) {
                continue;
            }
            double[] key = {row.urban() == urban ? 0.0d : 1.0d,
                    row.boxes().length == 0 ? Double.MAX_VALUE : area(row.boxes()), -row.from()};
            if (bestKey == null || less(key, bestKey)) {
                best = row;
                bestKey = key;
            }
        }
        return best;
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

    private static boolean less(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i];
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ a house

    /**
     * Fill one house. The people in it are drawn from the life table for their own place and
     * year, so a household in 1850 India and one in 1850 Yorkshire differ in who is still alive
     * and at what age, and not only in how many there are.
     */
    public Home home(long salt, int year, double lat, double lon, boolean urban) {
        Household h = at(lat, lon, year, urban);
        if (h == null) {
            return new Home(null, "nuclear", List.of(), 0);
        }
        LifeTable life = LifeTable.get();
        Marriage g = life.marriageAt(lat, lon, year);
        Mortality m = life.mortalityAt(lat, lon, year, urban);

        String structure = pickStructure(h, salt);
        List<Member> out = new ArrayList<>();
        int away = 0;

        // the head: usually a man, sometimes a widow
        boolean widowHead = "solo".equals(structure) || pct(salt, 3L) < h.widow();
        boolean maleHead = !widowHead;
        int headAge = headAge(m, salt, g);
        out.add(new Member(maleHead ? "head" : "widow",
                maleHead ? "the man of the house" : "the widow of the house",
                maleHead, headAge));

        if ("solo".equals(structure)) {
            return new Home(h, structure, List.copyOf(out), 0);
        }

        // a wife — unless the head is a widow, in which case there is nobody to add
        double gap = g == null ? 3.0d : Math.max(0.0d, g.husband() - g.wife());
        int spouseAge = maleHead ? (int) Math.round(headAge - gap) : headAge + 3;
        spouseAge = Math.max(16, Math.min(80, spouseAge));
        if (maleHead) {
            out.add(new Member("spouse", "his wife", false, spouseAge));
        }

        // how long they have been married, and how many children that has made
        // how long they have been married, and how many children that has made
        double wifeAge = maleHead ? spouseAge : headAge;
        double wifeMarried = g == null ? 22.0d : g.wife();
        int born = life.children(g, (int) Math.round(wifeMarried), salt + 11L);
        // children come about two and a half years apart, and only the ones still alive and still
        // young enough to be at home are in the house
        for (int i = 0; i < born; i++) {
            int ageNow = (int) Math.round(wifeAge - wifeMarried - 1.0d - 2.5d * i);
            if (ageNow < 0) {
                continue;
            }
            if (ageNow > h.leave()) {
                away++;
                continue;
            }
            double survived = m == null ? 1.0d : life.survives(m, ageNow) / Math.max(1.0e-9d,
                    life.survives(m, 0));
            if (uniform(salt, 17L + i) > survived) {
                continue;
            }
            boolean son = uniform(salt, 41L + i) < 0.5d;
            out.add(new Member("child", son ? (out.size() > 2 ? "their son" : "their son")
                    : "their daughter", son, ageNow));
        }

        // stem: the head's widowed parent is still in the house, if the years allow it
        if ("stem".equals(structure) && g != null) {
            // the head's mother was about the marriage age of her own day when he was born
            int parentAge = (int) Math.round(headAge + (g.wife() < 20 ? 26.0d : g.wife() + 2));
            int ceiling = m == null ? 82 : life.maxAge(m) - 2;
            parentAge = Math.min(parentAge, ceiling);
            // and the question is not "would a woman of that age be alive" but "is she alive
            // *now that her son is the age he is*" — survival from her age at his birth to now
            double bornAt = Math.max(15, parentAge - headAge);
            double alive = m == null ? 0.6d
                    : life.survives(m, parentAge) / Math.max(1.0e-9d, life.survives(m,
                    (int) Math.round(bornAt)));
            if (uniform(salt, 53L) < alive) {
                out.add(new Member("parent", "his mother", false, parentAge));
            }
        }

        // joint: a married brother and his own family under the same roof
        if ("joint".equals(structure)) {
            int brotherAge = headAge - 3 + (int) Math.round(uniform(salt, 61L) * 6.0d);
            brotherAge = Math.max(20, brotherAge);
            out.add(new Member("brother", "his brother", true, brotherAge));
            int sisterAge = Math.max(16, brotherAge - (int) Math.round(gap));
            out.add(new Member("in-law", "his brother's wife", false, sisterAge));
            int nephews = life.children(g, (int) Math.round(wifeMarried), salt + 71L);
            for (int i = 0; i < Math.min(nephews, 3); i++) {
                int ageNow = (int) Math.round(sisterAge - wifeMarried - 1.0d - 2.5d * i);
                if (ageNow < 0 || ageNow > h.leave()) {
                    continue;
                }
                out.add(new Member("nephew", uniform(salt, 83L + i) < 0.5d ? "his nephew"
                        : "his niece", true, ageNow));
            }
        }

        // servants, apprentices and lodgers: a household with room took them in
        if (pct(salt, 97L) < h.servants()) {
            int n = 1 + (int) Math.round(uniform(salt, 101L));
            for (int i = 0; i < n; i++) {
                int age = 12 + (int) Math.round(uniform(salt, 103L + i) * 14.0d);
                out.add(new Member("servant", i == 0 ? "a servant" : "another servant",
                        uniform(salt, 107L + i) < 0.5d, age));
            }
        }
        // A house the draw called a family has to have a family in it. The one way it can come
        // out with a single person is a widow head whose children have all left or died or were
        // never born — and a woman alone is a one-person household, which is what the published
        // figures count her as. So she keeps a child at home, and the share of one-person houses
        // stays where the sources put it instead of drifting three points above them.
        if (!"solo".equals(structure) && out.size() == 1) {
            int eldest = headAge - 12;                 // a child cannot be born to a child
            if (eldest >= 1) {
                int age = 1 + (int) Math.round(uniform(salt, 109L)
                        * Math.min(eldest - 1, 13.0d));
                boolean son = uniform(salt, 113L) < 0.5d;
                out.add(new Member("child", son ? "her son" : "her daughter", son, age));
            }
        }
        // and if it is still one person, then it is one person, whatever the draw said
        if (out.size() == 1) {
            structure = "solo";
        }
        return new Home(h, structure, List.copyOf(out), away);
    }

    /**
     * How old a householder is. Not a flat draw between two ages: a cross-section of households
     * at any instant holds the living population aged from first marriage to death, weighted by
     * the chance of still being alive at each age, and that weighting is what puts young couples
     * with one baby beside old couples whose children have all gone. A flat draw overstates the
     * size of the household badly, because it misses both ends.
     */
    private static int headAge(Mortality m, long salt, Marriage g) {
        int lo = g == null ? 25 : (int) Math.max(20, Math.round(g.husband()) + 1);
        int hi = m == null ? 80 : Math.min(80, LifeTable.get().maxAge(m) - 3);
        double[] w = new double[Math.max(2, hi - lo + 1)];
        double total = 0.0d;
        for (int a = lo; a <= hi; a++) {
            w[a - lo] = m == null ? 1.0d : LifeTable.get().survives(m, a);
            total += w[a - lo];
        }
        double r = uniform(salt, 5L) * total;
        for (int a = lo; a <= hi; a++) {
            r -= w[a - lo];
            if (r <= 0.0d) {
                return a;
            }
        }
        return hi;
    }

    private static String pickStructure(Household h, long salt) {
        int r = (int) Math.floor(uniform(salt, 13L) * 100.0d);
        if (r < h.nuclear()) {
            return "nuclear";
        }
        if (r < h.nuclear() + h.stem()) {
            return "stem";
        }
        if (r < h.nuclear() + h.stem() + h.joint()) {
            return "joint";
        }
        return "solo";
    }

    private static int pct(long salt, long tweak) {
        return (int) Math.floor(uniform(salt, tweak) * 100.0d);
    }

    private static double uniform(long salt, long tweak) {
        long x = salt * 0x9E3779B97F4A7C15L ^ tweak * 0xC2B2AE356165667CL;
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }

    // ------------------------------------------------------------------ words

    /** "Four or five to a house: a man, his wife and their children, and a servant sometimes." */
    public String line(double lat, double lon, int year, boolean urban) {
        Household h = at(lat, lon, year, urban);
        if (h == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%.1f", h.size()))
                .append(" to a house: ");
        if (h.nuclear() >= 55) {
            sb.append("a man, his wife and their children");
        } else if (h.joint() >= 30) {
            sb.append("married brothers with their wives and children, all under one roof");
        } else {
            sb.append("a couple and their children");
        }
        if (h.stem() >= 25) {
            sb.append(", and often the old mother too");
        }
        if (h.servants() >= 15) {
            sb.append(", with a servant or two");
        }
        sb.append('.');
        if (h.solo() >= 25) {
            sb.append(" One house in ").append(Math.round(100.0d / Math.max(1, h.solo())))
              .append(" holds a single person.");
        }
        if (h.estimated()) {
            sb.append(" (estimated: nothing is published for this place and year.)");
        }
        return sb.toString();
    }

    public int count() {
        return rows.size();
    }
}

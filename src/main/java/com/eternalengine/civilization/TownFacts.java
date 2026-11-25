package com.eternalengine.civilization;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Step 29 (systems C2, C3, C4, C5, C6): the towns of the world through time.
 *
 * <p>{@code history/towns.json.gz} (built by {@code worldfill/build_towns.py}) keeps about 30,000 towns: every human
 * settlement with 20,000 people or more, every renamed town with 8 or more Wikipedia articles, and the cities of step
 * 26. For each: its names by era (Bombay until 1995, Edo until 1868), its population figures with their years, when
 * it was founded, the provinces and districts it lay in by era, and who held the office of its head; the provinces'
 * heads too (governors, chief ministers, nizams).
 *
 * <p>Nothing is guessed beyond the data: a name holds only for the years it is dated for; a population figure is a
 * straight line between two counts, never before the first and at most 15 years after the last; a head is named only
 * while the term lasts (or, with no end year, until the next holder and at most 12 years).
 */
public final class TownFacts {

    public static final String FILE = "/data/eternalengine/eternal/history/towns.json.gz";
    private static final int NONE = Integer.MIN_VALUE;
    private static final int OPEN = Integer.MAX_VALUE;
    /** A town's name is used for a gazetteer site this far away at most (the site and the town are one place). */
    public static final double MATCH_KM = 30.0d;

    private static volatile TownFacts instance;

    // towns
    private final int[] lat;
    private final int[] lon;
    private final String[] label;
    private final int[] founded;
    private final byte[] precision;
    private final int[][] nameYears;
    private final String[][] names;
    private final int[][] alsoYears;
    private final String[][] also;
    private final int[][] pops;
    private final int[][] divs;
    private final int[] head;
    // provinces
    private final String[] divName;
    private final int[] divOffice;
    private final int[] divParent;
    private final int[] divParentFrom;
    // offices
    private final String[] offTitle;
    private final int[][] offYears;
    private final String[][] offHolders;
    private final Map<Integer, int[]> grid;
    private final String problem;
    private final long loadMillis;

    private TownFacts(Builder b, String problem, long loadMillis) {
        int n = b.label.size();
        lat = toInts(b.lat);
        lon = toInts(b.lon);
        label = b.label.toArray(new String[0]);
        founded = toInts(b.founded);
        precision = new byte[n];
        for (int i = 0; i < n; i++) {
            precision[i] = (byte) (int) b.precision.get(i);
        }
        nameYears = b.nameYears.toArray(new int[0][]);
        names = b.names.toArray(new String[0][]);
        alsoYears = b.alsoYears.toArray(new int[0][]);
        also = b.also.toArray(new String[0][]);
        pops = b.pops.toArray(new int[0][]);
        divs = b.divs.toArray(new int[0][]);
        head = toInts(b.head);
        divName = b.divName.toArray(new String[0]);
        divOffice = toInts(b.divOffice);
        divParent = toInts(b.divParent);
        divParentFrom = toInts(b.divParentFrom);
        offTitle = b.offTitle.toArray(new String[0]);
        offYears = b.offYears.toArray(new int[0][]);
        offHolders = b.offHolders.toArray(new String[0][]);
        Map<Integer, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < n; i++) {
            cells.computeIfAbsent(cell(lat[i] / 1000.0d, lon[i] / 1000.0d), k -> new ArrayList<>()).add(i);
        }
        Map<Integer, int[]> g = new HashMap<>();
        cells.forEach((k, v) -> g.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
        grid = g;
        this.problem = problem;
        this.loadMillis = loadMillis;
    }

    private static int[] toInts(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    public static TownFacts get() {
        TownFacts t = instance;
        if (t == null) {
            synchronized (TownFacts.class) {
                if (instance == null) {
                    instance = load();
                }
                t = instance;
            }
        }
        return t;
    }

    public boolean loaded() {
        return problem == null && label.length > 0;
    }

    public String problem() {
        return problem;
    }

    public int size() {
        return label.length;
    }

    public int provinces() {
        return divName.length;
    }

    public int offices() {
        return offTitle.length;
    }

    public int holders() {
        int n = 0;
        for (String[] h : offHolders) {
            n += h.length;
        }
        return n;
    }

    public String summary() {
        return size() + " towns, " + provinces() + " provinces, " + offices() + " offices with " + holders()
                + " holders (loaded in " + loadMillis + " ms)";
    }

    /** Rough bytes held (for the memory table). */
    public long approxBytes() {
        long b = 16L * size();
        for (int i = 0; i < size(); i++) {
            b += 40 + 2L * label[i].length() + 4L * (nameYears[i].length + pops[i].length + divs[i].length);
            for (String s : names[i]) {
                b += 40 + 2L * s.length();
            }
        }
        for (String s : divName) {
            b += 56 + 2L * s.length();
        }
        for (int i = 0; i < offTitle.length; i++) {
            b += 56 + 2L * offTitle[i].length() + 4L * offYears[i].length;
            for (String s : offHolders[i]) {
                b += 40 + 2L * s.length();
            }
        }
        return b;
    }

    private static int cell(double la, double lo) {
        return (int) Math.floor(la + 90.0d) * 1000 + (int) Math.floor(lo + 180.0d);
    }

    // ================================================================== finding a town

    /** One town, read through its number. */
    public final class Town {
        private final int i;

        private Town(int i) {
            this.i = i;
        }

        public String label() {
            return label[i];
        }

        public double lat() {
            return lat[i] / 1000.0d;
        }

        public double lon() {
            return lon[i] / 1000.0d;
        }

        /** The town's name in that year: a dated name (Bombay in 1990) or today's (Mumbai). */
        public String nameIn(int year) {
            return datedNameIn(year).orElse(label[i]);
        }

        /** A name the data dates to this year, if any (Edo in 1700; nothing for Tokyo in 1900). */
        public Optional<String> datedNameIn(int year) {
            int[] ys = nameYears[i];
            for (int k = 0; k < names[i].length; k++) {
                if (ys[2 * k] <= year && year < ys[2 * k + 1]) {
                    return Optional.of(names[i][k]);
                }
            }
            return Optional.empty();
        }

        /** "It was called Bombay until 1995." for a later year; empty when it had no other name before. */
        public String formerNameLine(int year) {
            int[] ys = nameYears[i];
            int best = -1;
            for (int k = 0; k < names[i].length; k++) {
                int from = ys[2 * k];
                int to = ys[2 * k + 1];
                if (to == OPEN || to > year || (from != NONE && to - from < 10)) {
                    continue;
                }
                if (best < 0 || to > ys[2 * best + 1]) {
                    best = k;
                }
            }
            if (best < 0 || names[i][best].equals(nameIn(year))) {
                return "";
            }
            return "It was called " + names[i][best] + " until " + yearText(ys[2 * best + 1]) + ".";
        }

        /** "Travellers also call it Bhagnagar." in the years the data gives another name in use. */
        public String alsoLine(int year) {
            int[] ys = alsoYears[i];
            for (int k = 0; k < also[i].length; k++) {
                if (ys[2 * k] <= year && year < ys[2 * k + 1]) {
                    return "Travellers also call it " + also[i][k] + ".";
                }
            }
            return "";
        }

        /** Known from: the founding year, else the first count of its people, else its first dated name. */
        public int knownFrom() {
            if (founded[i] != NONE) {
                return founded[i];
            }
            int from = OPEN;
            if (pops[i].length > 0) {
                from = pops[i][0];
            }
            for (int k = 0; k < names[i].length; k++) {
                int f = nameYears[i][2 * k];
                if (f != NONE && f < from) {
                    from = f;
                }
            }
            return from;
        }

        public boolean existsIn(int year) {
            return year >= knownFrom();
        }

        /** People living in the town that year (a straight line between counts); -1 when the data does not say. */
        public long peopleIn(int year) {
            int[] p = pops[i];
            if (p.length == 0 || year < p[0]) {
                return -1L;
            }
            int last = p.length - 2;
            if (year >= p[last]) {
                return year - p[last] <= 15 ? p[last + 1] : -1L;
            }
            for (int k = 0; k < last; k += 2) {
                int y0 = p[k];
                int y1 = p[k + 2];
                if (y0 <= year && year < y1) {
                    if (y1 - y0 > 100) {
                        // Centuries between two counts: no straight line, only a count close by.
                        if (year - y0 <= 15) {
                            return p[k + 1];
                        }
                        return y1 - year <= 15 ? p[k + 3] : -1L;
                    }
                    double t = (year - y0) / (double) (y1 - y0);
                    return Math.round(p[k + 1] + t * (p[k + 3] - p[k + 1]));
                }
            }
            return -1L;
        }

        /**
         * The town's reach on the map, from its people that year (3 to 25 km). Only for finding the town: after the
         * last count the last one, up to 30 years before the first count the first one, else 3 km.
         */
        public double radiusKm(int year) {
            long people = peopleIn(year);
            if (people < 0 && pops[i].length > 0) {
                if (year >= pops[i][0]) {
                    people = pops[i][pops[i].length - 1];
                } else if (year >= pops[i][0] - 30) {
                    people = pops[i][1];
                }
            }
            if (people <= 0) {
                return 3.0d;
            }
            return Math.max(3.0d, Math.min(25.0d, 2.0d + Math.sqrt(people) / 150.0d));
        }

        public String peopleLine(int year, String shownName) {
            long n = peopleIn(year);
            if (n <= 0) {
                return "";
            }
            return "About " + roundPeople(n) + " people live in " + shownName + ".";
        }

        /** Growing or shrinking: its people now against twenty years ago, when the data has both. */
        public String growthLine(int year) {
            long now = peopleIn(year);
            long before = peopleIn(year - 20);
            if (now <= 0 || before <= 0) {
                return "";
            }
            double ratio = now / (double) before;
            if (ratio >= 1.5d) {
                return "The town is growing fast.";
            }
            if (ratio >= 1.15d) {
                return "The town is growing.";
            }
            if (ratio <= 0.85d) {
                return "Fewer people live here than twenty years ago.";
            }
            return "";
        }

        public String foundedLine(int year) {
            int f = founded[i];
            if (f == NONE || year < f) {
                return "";
            }
            if (year == f && precision[i] >= 9) {
                return "The town was founded this year.";
            }
            // "People say": a founding date is what the town's own story says (Rome, 753 BCE).
            return switch (precision[i]) {
                case 9 -> "People say the town was founded in " + yearText(f) + ".";
                case 8 -> f > 0 ? "People say the town was founded in the " + (f / 10 * 10) + "s." : "";
                case 7 -> "People say the town was founded in the " + century(f) + ".";
                default -> "";
            };
        }

        /** "It lies in the Bombay Presidency." / "It lies in Hyderabad district, in Telangana." */
        public String provinceLine(int year) {
            return provinceLine(year, null);
        }

        /**
         * The same, leaving out a province with the ruling state's own name (the ruler line says that already:
         * "Hyderabad State" under the Nizam, "the Kingdom of Bohemia" under its king).
         */
        public String provinceLine(int year, String rulingState) {
            int d = divisionIn(year);
            if (d < 0 || (rulingState != null && EventNews.articleFits(divName[d], rulingState)
                    && EventNews.articleFits(rulingState, divName[d]))) {
                return "";
            }
            String out = "It lies in " + provinceName(divName[d]);
            int p = divParent[d];
            if (p >= 0 && p != d && year >= divParentFrom[d]) {
                out += ", in " + provinceName(divName[p]);
            }
            return out + ".";
        }

        public int divisionIn(int year) {
            int[] dv = divs[i];
            for (int k = 0; k + 2 < dv.length; k += 3) {
                if (dv[k] <= year && year < dv[k + 1]) {
                    return dv[k + 2];
                }
            }
            return -1;
        }

        /** Who runs the town that year, and who runs its province: up to two short sentences. */
        public String headLine(int year) {
            List<String> out = new ArrayList<>();
            String own = holderLine(head[i], year);
            if (!own.isEmpty()) {
                out.add(own);
            }
            int d = divisionIn(year);
            if (d >= 0) {
                String prov = holderLine(divOffice[d], year);
                if (prov.isEmpty() && divParent[d] >= 0 && year >= divParentFrom[d]) {
                    prov = holderLine(divOffice[divParent[d]], year);
                }
                if (!prov.isEmpty() && !out.contains(prov)) {
                    out.add(prov);
                }
            }
            return String.join(" ", out);
        }
    }

    /** "Anne Hidalgo is the Mayor of Paris (since 2014)." or "" when nobody is known for that year. */
    String holderLine(int office, int year) {
        if (office < 0 || office >= offTitle.length) {
            return "";
        }
        int[] ys = offYears[office];
        String[] who = offHolders[office];
        int best = -1;
        for (int k = 0; k < who.length; k++) {
            int from = ys[2 * k];
            int to = ys[2 * k + 1];
            if (from > year) {
                break;
            }
            boolean inOffice;
            if (to != OPEN) {
                inOffice = year <= to;
            } else {
                int next = OPEN;
                for (int m = k + 1; m < who.length; m++) {
                    if (ys[2 * m] > from) {
                        next = ys[2 * m];
                        break;
                    }
                }
                inOffice = year < next && year - from <= 12;
            }
            if (inOffice) {
                best = k;
            }
        }
        if (best < 0) {
            return "";
        }
        String title = prettyTitle(offTitle[office]);
        if (title == null || title.isBlank()) {
            return "";
        }
        int from = ys[2 * best];
        String the = title.toLowerCase(Locale.ROOT).startsWith("the ") ? "" : "the ";
        String since = from == year ? " (from this year)" : " (since " + yearText(from) + ")";
        return who[best] + " is " + the + title + since + ".";
    }

    /**
     * <b>Problem 60's wording pass.</b> Wikidata's office names arrive as the record typed them:
     * some in lower case ("the mayor of Paris"), some shouted ("MAYOR OF PARIS"), some long. This
     * makes them titles without inventing them:
     *
     * <ul>
     *   <li>blank stays blank (never invented);</li>
     *   <li>a leading "the " is kept (the caller's article logic reads it) and the title after it
     *       is title-cased;</li>
     *   <li>words are title-cased around a small-words rule (of, and, the, de, la, van, ...), the
     *       first word always capitalized; a shouted word is quieted first ("MAYOR" becomes "Mayor",
     *       so "NATO" becomes "Nato", which is the documented trade-off);</li>
     *   <li>a title longer than 56 characters is cut at its first comma ("Commander of the
     *       Honourable Order of the Bath, and of the Order of Merit" becomes "Commander of the
     *       Honourable Order of the Bath"); without a comma the long title stands, because cutting
     *       without one would be inventing.</li>
     * </ul>
     */
    static String prettyTitle(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return "";
        }
        String article = "";
        String rest = t;
        if (rest.length() > 4 && rest.substring(0, 4).equalsIgnoreCase("the ")) {
            article = "the ";
            rest = rest.substring(4).trim();
        }
        if (rest.isEmpty()) {
            return "";
        }
        if (rest.length() > 56) {
            int comma = rest.indexOf(',');
            if (comma > 0) {
                rest = rest.substring(0, comma).trim();
            }
        }
        String[] words = rest.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) {
                continue;
            }
            boolean small = i > 0 && SMALL_WORDS.contains(w.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z\u00e0-\u00ff]", ""));
            String word = w;
            boolean shouty = word.length() > 1
                    && word.equals(word.toUpperCase(Locale.ROOT))
                    && word.chars().anyMatch(Character::isLetter);
            if (shouty) {
                word = word.toLowerCase(Locale.ROOT);
            }
            if (!small) {
                word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word);
        }
        String result = out.toString();
        return result.isEmpty() ? "" : article + result;
    }

    /** The small words of a title, kept lower case unless they come first. */
    private static final java.util.Set<String> SMALL_WORDS = java.util.Set.of(
            "of", "and", "or", "the", "a", "an", "in", "on", "at", "to", "for", "with",
            "de", "da", "du", "del", "della", "des", "di", "van", "von", "der", "den", "het",
            "le", "la", "les", "el", "al", "bin", "ibn", "af", "av", "e", "y");

    /**
     * The town a gazetteer site is (the same place under its names through time): a town within {@link #MATCH_KM}
     * whose name today, or one of its dated names, is the site's name.
     */
    public Optional<Town> matching(String siteName, double siteLat, double siteLon) {
        String want = norm(siteName);
        if (want.isEmpty()) {
            return Optional.empty();
        }
        int best = -1;
        double bestKm = MATCH_KM;
        for (int c : around(siteLat, siteLon)) {
            int[] list = grid.get(c);
            if (list == null) {
                continue;
            }
            for (int i : list) {
                double km = km(siteLat, siteLon, lat[i] / 1000.0d, lon[i] / 1000.0d);
                if (km > bestKm) {
                    continue;
                }
                boolean same = same(label[i], want);
                for (int k = 0; !same && k < names[i].length; k++) {
                    same = same(names[i][k], want);
                }
                for (int k = 0; !same && k < also[i].length; k++) {
                    same = same(also[i][k], want);
                }
                if (same) {
                    best = i;
                    bestKm = km;
                }
            }
        }
        return best < 0 ? Optional.empty() : Optional.of(new Town(best));
    }

    /** The town this point lies in that year: within the town's reach, and the town known by then. */
    public Optional<Town> near(double la, double lo, int year) {
        int best = -1;
        double bestScore = 1.0d;
        for (int c : around(la, lo)) {
            int[] list = grid.get(c);
            if (list == null) {
                continue;
            }
            for (int i : list) {
                Town t = new Town(i);
                double km = km(la, lo, lat[i] / 1000.0d, lon[i] / 1000.0d);
                if (km > 25.0d) {
                    continue;
                }
                double score = km / t.radiusKm(year);
                if (score <= bestScore && t.existsIn(year)) {
                    best = i;
                    bestScore = score;
                }
            }
        }
        return best < 0 ? Optional.empty() : Optional.of(new Town(best));
    }

    /** Town number i (0 .. size()-1), for the checks and probes. */
    public Town town(int i) {
        return new Town(i);
    }

    /** A town by one of its names near a point (for the checks). */
    public Optional<Town> byLabelNear(String name, double la, double lo) {
        return matching(name, la, lo);
    }

    private static int[] around(double la, double lo) {
        int[] out = new int[9];
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                double y = la + dy * 0.5d;
                double x = lo + dx * 0.5d;
                int c = cell(y, x);
                boolean seen = false;
                for (int k = 0; k < n; k++) {
                    seen |= out[k] == c;
                }
                if (!seen) {
                    out[n++] = c;
                }
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    static double km(double la1, double lo1, double la2, double lo2) {
        double p1 = Math.toRadians(la1);
        double p2 = Math.toRadians(la2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371.0d * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Lower case, no accents, letters and digits only; "saint" and "st" are one word; ł is l, ø is o, ß is ss. */
    static String norm(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("ł", "l").replace("Ł", "L").replace("ø", "o").replace("Ø", "O").replace("æ", "ae")
                .replace("Æ", "AE").replace("ß", "ss").replace("đ", "d").replace("Đ", "D").replace("ı", "i")
                .replace("œ", "oe").replace("þ", "th").replace("ð", "d");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace("saint ", "st ").replace("st. ", "st ");
        return t.replaceAll("[^a-z0-9]", "");
    }

    /** A name matches: the same letters, or the same without a closing "City" ("Ho Chi Minh" is Ho Chi Minh City). */
    private static boolean same(String name, String want) {
        String n = norm(name);
        if (n.equals(want)) {
            return true;
        }
        return n.endsWith("city") && n.length() > 7 && n.substring(0, n.length() - 4).equals(want);
    }

    static String yearText(int y) {
        return y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y);
    }

    static String century(int y) {
        int c = y > 0 ? (y - 1) / 100 + 1 : (-y - 1) / 100 + 1;
        String suffix = (c % 100 >= 11 && c % 100 <= 13) ? "th" : switch (c % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return c + suffix + " century" + (y < 0 ? " BCE" : "");
    }

    /** "about 12.4 million", "about 780,000", "about 4,900". */
    static String roundPeople(long n) {
        if (n >= 1_000_000L) {
            double m = Math.round(n / 100_000.0d) / 10.0d;
            String s = m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m);
            return s + " million";
        }
        long step = n >= 100_000L ? 10_000L : n >= 10_000L ? 1_000L : 100L;
        long r = Math.max(step, Math.round(n / (double) step) * step);
        return String.format(Locale.ROOT, "%,d", r);
    }

    private static final java.util.regex.Pattern THE_PROVINCE = java.util.regex.Pattern.compile(
            "(?i)^(province|county|duchy|kingdom|principality|presidency|governorate|government|territory|state|"
                    + "district|region|department|electorate|margraviate|landgraviate|archduchy|bishopric|"
                    + "prince-bishopric|emirate|sultanate|khanate|march|banate|colony|protectorate|free state|"
                    + "grand duchy|viceroyalty|captaincy|commonwealth|republic|land|khedivate|eyalet|vilayet|sanjak|"
                    + "pashalik|beylerbeylik|satrapy|intendancy|regency|imamate|despotate|tsardom|lordship|barony|"
                    + "earldom|shire|theme|exarchate|diocese|archbishopric|electorate) of\\b|"
                    + "(?i)\\b(presidency|voivodeship|governorate|province|region|territory|agency|residency|"
                    + "protectorate|colony|mandate|viceroyalty|captaincy general)$");

    /** "the Bombay Presidency", "the Province of Rome", "Maharashtra", "Hyderabad district". */
    static String provinceName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        if (name.toLowerCase(Locale.ROOT).startsWith("the ")) {
            return name;
        }
        return THE_PROVINCE.matcher(name).find() ? "the " + name : name;
    }

    // ================================================================== loading

    private static final class Builder {
        final List<Integer> lat = new ArrayList<>();
        final List<Integer> lon = new ArrayList<>();
        final List<String> label = new ArrayList<>();
        final List<Integer> founded = new ArrayList<>();
        final List<Integer> precision = new ArrayList<>();
        final List<int[]> nameYears = new ArrayList<>();
        final List<String[]> names = new ArrayList<>();
        final List<int[]> alsoYears = new ArrayList<>();
        final List<String[]> also = new ArrayList<>();
        final List<int[]> pops = new ArrayList<>();
        final List<int[]> divs = new ArrayList<>();
        final List<Integer> head = new ArrayList<>();
        final List<String> divName = new ArrayList<>();
        final List<Integer> divOffice = new ArrayList<>();
        final List<Integer> divParent = new ArrayList<>();
        final List<Integer> divParentFrom = new ArrayList<>();
        final List<String> offTitle = new ArrayList<>();
        final List<int[]> offYears = new ArrayList<>();
        final List<String[]> offHolders = new ArrayList<>();
    }

    private static TownFacts load() {
        long t0 = System.nanoTime();
        Builder b = new Builder();
        String problem = null;
        try (InputStream raw = TownFacts.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                problem = FILE + " is missing";
            } else {
                JsonReader r = new JsonReader(new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8));
                r.beginObject();
                while (r.hasNext()) {
                    String key = r.nextName();
                    switch (key) {
                        case "towns" -> readTowns(r, b);
                        case "divisions" -> readDivisions(r, b);
                        case "offices" -> readOffices(r, b);
                        default -> r.skipValue();
                    }
                }
                r.endObject();
            }
        } catch (Exception e) {
            problem = "cannot read " + FILE + ": " + e;
            b = new Builder();
        }
        return new TownFacts(b, problem, (System.nanoTime() - t0) / 1_000_000L);
    }

    private static int intOr(JsonReader r, int dflt) throws IOException {
        if (r.peek() == JsonToken.NULL) {
            r.nextNull();
            return dflt;
        }
        return r.nextInt();
    }

    /** [[from, to, name], ...] into years (from, to pairs: NONE / OPEN for open ends) and names. */
    private static void readSpans(JsonReader r, List<int[]> years, List<String[]> texts) throws IOException {
        List<Integer> ys = new ArrayList<>();
        List<String> ns = new ArrayList<>();
        r.beginArray();
        while (r.hasNext()) {
            r.beginArray();
            ys.add(intOr(r, NONE));
            ys.add(intOr(r, OPEN));
            ns.add(r.nextString());
            r.endArray();
        }
        r.endArray();
        years.add(toInts(ys));
        texts.add(ns.toArray(new String[0]));
    }

    private static void readTowns(JsonReader r, Builder b) throws IOException {
        r.beginArray();
        while (r.hasNext()) {
            r.beginArray();
            r.nextString(); // qid
            b.label.add(r.nextString());
            b.lat.add(r.nextInt());
            b.lon.add(r.nextInt());
            r.nextInt(); // flags
            b.founded.add(intOr(r, NONE));
            b.precision.add(intOr(r, 0));
            readSpans(r, b.nameYears, b.names);
            List<Integer> p = new ArrayList<>();
            r.beginArray();
            while (r.hasNext()) {
                p.add(r.nextInt());
            }
            r.endArray();
            b.pops.add(toInts(p));
            List<Integer> d = new ArrayList<>();
            r.beginArray();
            while (r.hasNext()) {
                r.beginArray();
                d.add(intOr(r, NONE));
                d.add(intOr(r, OPEN));
                d.add(r.nextInt());
                r.endArray();
            }
            r.endArray();
            b.divs.add(toInts(d));
            b.head.add(r.nextInt());
            if (r.hasNext()) {
                readSpans(r, b.alsoYears, b.also);
            } else {
                b.alsoYears.add(new int[0]);
                b.also.add(new String[0]);
            }
            while (r.hasNext()) {
                r.skipValue();
            }
            r.endArray();
        }
        r.endArray();
    }

    private static void readDivisions(JsonReader r, Builder b) throws IOException {
        r.beginArray();
        while (r.hasNext()) {
            r.beginArray();
            r.nextString(); // qid
            b.divName.add(r.nextString());
            b.divOffice.add(r.nextInt());
            b.divParent.add(r.nextInt());
            b.divParentFrom.add(intOr(r, OPEN));
            while (r.hasNext()) {
                r.skipValue();
            }
            r.endArray();
        }
        r.endArray();
    }

    private static void readOffices(JsonReader r, Builder b) throws IOException {
        r.beginArray();
        while (r.hasNext()) {
            r.beginArray();
            r.nextString(); // qid
            b.offTitle.add(r.nextString());
            List<Integer> ys = new ArrayList<>();
            List<String> who = new ArrayList<>();
            r.beginArray();
            while (r.hasNext()) {
                r.beginArray();
                ys.add(intOr(r, NONE));
                ys.add(intOr(r, OPEN));
                who.add(r.nextString());
                r.endArray();
            }
            r.endArray();
            b.offYears.add(toInts(ys));
            b.offHolders.add(who.toArray(new String[0]));
            while (r.hasNext()) {
                r.skipValue();
            }
            r.endArray();
        }
        r.endArray();
    }
}

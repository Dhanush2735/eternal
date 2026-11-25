package com.eternalengine.civilization;

import com.eternalengine.technology.TechnologyAtlas;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 30b (systems C13, C14, C17, C26, C27): who can read and in which script, how people measure, how well off they
 * are, and their holy places, festivals and pilgrimages - for every place and year.
 *
 * <p>{@code history/culture.json.gz} and {@code history/holy_places.json.gz} (built by
 * {@code worldfill/build_culture.py}):
 * <ul>
 *   <li>literacy by country and year (Our World in Data, from UNESCO and van Zanden); before the first figure the
 *   technology atlas decides: nobody reads where there is no writing, a few priests and scribes before printing, and
 *   from the printing press a slow rise to the first figure; cities more, villages fewer, men more than women (the
 *   gap closes as more people read);</li>
 *   <li>the script each language is written in, by years (Turkish in Arabic letters until 1928);</li>
 *   <li>weights, measures and distances by land and era (the kos and the seer, the verst and the pood, the li and the
 *   jin ...) and the year each country took up metres and kilograms;</li>
 *   <li>how well off people are (the Maddison Project's GDP per person) and the hard times (hyperinflations,
 *   depressions);</li>
 *   <li>21,000 holy places (Wikidata), the great festivals of each faith, its pilgrimages, and the Christian and Islamic
 *   feast days by the calendar.</li>
 * </ul>
 */
public final class CultureAtlas {

    public static final String FILE = "/data/eternalengine/eternal/history/culture.json.gz";
    public static final String HOLY_FILE = "/data/eternalengine/eternal/history/holy_places.json.gz";
    private static volatile CultureAtlas instance;

    private final Map<String, double[]> literacy = new HashMap<>();
    private final Map<String, String> literacyMark = new HashMap<>();
    private final Map<String, double[]> wealth = new HashMap<>();
    private final Map<String, Integer> metric = new HashMap<>();
    private final Set<String> notMetric = new HashSet<>();
    private int stillSaid = 40;
    private String noSystem = "";
    private final List<Units> units = new ArrayList<>();
    private final Map<String, List<Object[]>> scripts = new HashMap<>();
    private final List<String> latin = new ArrayList<>();
    private final List<Faith> faiths = new ArrayList<>();
    private final List<Crisis> crises = new ArrayList<>();
    private final List<Base> bases = new ArrayList<>();
    private double baseDefault = 0.02d;
    private final List<Holy> holy = new ArrayList<>();
    private final Map<Integer, List<Holy>> holyGrid = new HashMap<>();
    private String problem;

    record Units(String id, Set<String> where, int start, int end, int priority, String[] distance, String[] length,
                 String[] weight, String[] big, String[] small, String[] land, Map<String, Integer> starts,
                 Map<String, Integer> ends) {
        int startIn(String c) {
            return starts.getOrDefault(c, start);
        }

        int endIn(String c) {
            return ends.getOrDefault(c, end);
        }
    }

    record Base(Set<String> where, int start, int end, double share) {
    }

    record Faith(String key, List<String> match, String word, List<Object[]> festivals, List<Object[]> pilgrimages) {
    }

    record Crisis(Set<String> where, int start, int end, String kind, String line) {
    }

    /** A holy place: its name, point, kind (mosque, Hindu temple ...), faith family, years, Wikipedia articles. */
    public record Holy(String name, double lat, double lon, String kind, String faith, int from, int to, int articles) {
        boolean standsIn(int year, int defaultFrom) {
            int f = from == Integer.MIN_VALUE ? defaultFrom : from;   // without a founding year: only today's
            return year >= f && (to == Integer.MAX_VALUE || year < to);
        }
    }

    private int holyDefaultFrom = 1900;

    public static CultureAtlas get() {
        CultureAtlas c = instance;
        if (c == null) {
            synchronized (CultureAtlas.class) {
                if (instance == null) {
                    instance = new CultureAtlas();
                    instance.load();
                }
                c = instance;
            }
        }
        return c;
    }

    public boolean loaded() {
        return problem == null && !literacy.isEmpty();
    }

    public String problem() {
        return problem;
    }

    public String summary() {
        return "literacy for " + literacy.size() + " countries, wealth for " + wealth.size() + ", metric years for "
                + metric.size() + ", " + units.size() + " systems of measures, " + scripts.size() + " scripts, "
                + faiths.size() + " faiths, " + holy.size() + " holy places";
    }

    public int holyPlaces() {
        return holy.size();
    }

    // ================================================================== reading and writing (C27, C14)

    /** Who can read here: {everyone, men, women}, 0 to 1; null when the data knows nothing of the land. */
    public double[] literacy(double lat, double lon, int year, SettlementRank rank) {
        String c = CountryGrid.get().codeAt(lat, lon);
        double[] s = literacy.get(c);
        if (s == null) {
            return null;
        }
        TechnologyAtlas atlas = TechnologyAtlas.get();
        TechnologyAtlas.Tech writing = atlas.tech("writing");
        if (writing != null) {
            int w = atlas.arrivalAt(writing, lat, lon);
            if (w == TechnologyAtlas.NEVER || w > year || atlas.lostAt(writing, lat, lon, year)) {
                return new double[]{0.0d, 0.0d, 0.0d};
            }
        }
        double l;
        int y0 = (int) s[0];
        double v0 = s[1] / 100.0d;
        if (year >= y0) {
            l = at(s, year) / 100.0d;
        } else {
            int printing = TechnologyAtlas.NEVER;
            for (String id : new String[]{"printing_press", "woodblock", "movable_type"}) {
                TechnologyAtlas.Tech t = atlas.tech(id);
                if (t != null) {
                    printing = Math.min(printing, atlas.arrivalAt(t, lat, lon));
                }
            }
            double base = Math.min(baseShare(c, year), v0);
            if (printing <= year) {
                // after printing came: back from the first figure, falling by about a third every 50 years (Japan
                // about 60% in the cities of 1850, India 2% in 1600), never below the old share of priests and scribes
                l = Math.max(base, Math.min(v0, v0 * Math.exp(-(y0 - year) / 120.0d)));
            } else {
                l = base;
            }
        }
        double factor = rank == SettlementRank.CITY ? 1.4d : rank == SettlementRank.VILLAGE ? 0.7d : 1.0d;
        l = Math.max(0.0d, Math.min(0.99d, l * factor));
        double r = 0.1d + 0.9d * l;
        double men = Math.min(1.0d, 2.0d * l / (1.0d + r));
        double women = Math.min(1.0d, r * men);
        return new double[]{l, men, women};
    }

    /** Who could read before printing: about one in ten in the Roman world, a few scribes elsewhere. */
    double baseShare(String c, int year) {
        for (Base b : bases) {
            if (b.where.contains(c) && b.start <= year && year < b.end) {
                return b.share;
            }
        }
        return baseDefault;
    }

    /** "R" from the country's own figures, "S" from its subregion's, "" none. */
    public String literacyMark(double lat, double lon) {
        return literacyMark.getOrDefault(CountryGrid.get().codeAt(lat, lon), "");
    }

    /**
     * "About 9% of people here can read and write: one man in seven, hardly any women. They write Telugu in the Telugu
     * script; the court writes Persian in the Perso-Arabic script."
     */
    public String readLine(double lat, double lon, int year, SettlementRank rank, String peoplesLanguage,
                           String courtLanguage) {
        double[] l = literacy(lat, lon, year, rank);
        if (l == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (l[0] <= 0.0d) {
            String c = CountryGrid.get().codeAt(lat, lon);
            boolean quipu = year < 1540 && Set.of("PER", "BOL", "ECU", "CHL").contains(c);
            sb.append(quipu ? "Nobody here reads or writes: the officials keep their records on knotted cords (quipu)."
                    : "Nobody here can read or write: people remember and tell, and count on their fingers and on sticks.");
            return sb.toString();
        }
        if (l[0] < 0.01d) {
            sb.append("Hardly anyone here can read or write: only a few priests, scribes and officials.");
        } else if (l[0] >= 0.95d) {
            sb.append("Nearly everyone here can read and write.");
        } else {
            sb.append(String.format(Locale.ROOT, "About %d%% of people here can read and write: %s, %s.",
                    Math.round(l[0] * 100), share(l[1], "man", "men"), share(l[2], "woman", "women")));
        }
        String ps = peoplesLanguage == null ? null : scriptOf(peoplesLanguage, year);
        String cs = courtLanguage == null ? null : scriptOf(courtLanguage, year);
        if (ps != null) {
            sb.append(" They write ").append(peoplesLanguage).append(" in ").append(ps);
            if (cs != null && courtLanguage != null && !courtLanguage.equalsIgnoreCase(peoplesLanguage) && !cs.equals(ps)) {
                sb.append(year >= 1800 ? "; the government writes " : "; the court writes ").append(courtLanguage)
                        .append(" in ").append(cs);
            }
            sb.append(".");
        } else if (cs != null && courtLanguage != null) {
            sb.append(" Those who write, write ").append(courtLanguage).append(" in ").append(cs).append(".");
        }
        return sb.toString();
    }

    private static String share(double v, String one, String many) {
        if (v >= 0.9d) {
            return "nearly all " + many;
        }
        if (v >= 0.6d) {
            return "most " + many;
        }
        if (v >= 0.4d) {
            return "about half the " + many;
        }
        if (v < 0.01d) {
            return "hardly any " + many;
        }
        long n = Math.round(1.0d / v);
        return "one " + one + " in " + n;
    }

    /** The script a language is written in that year ("the Arabic script" for Turkish in 1900), or null. */
    public String scriptOf(String language, int year) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String name = language.toLowerCase(Locale.ROOT);
        String bestKey = null;
        for (String key : scripts.keySet()) {
            boolean all = true;
            for (String w : key.split(" ")) {
                if (!name.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
            }
        }
        if (bestKey != null) {
            for (Object[] p : scripts.get(bestKey)) {
                int from = (Integer) p[0];
                int to = (Integer) p[1];
                if (from <= year && year < to) {
                    return (String) p[2];
                }
            }
            return null;
        }
        for (String l : latin) {
            if (name.contains(l)) {
                return "the Latin alphabet";
            }
        }
        return null;
    }

    // ================================================================== measures (C26)

    /** "People here count distance in the kos (about 3 km) ..." or the metric line, by land and year. */
    public String measureLine(double lat, double lon, int year) {
        String c = CountryGrid.get().codeAt(lat, lon);
        if (c.isEmpty()) {
            return "";
        }
        Units u = null;
        for (Units x : units) {
            if (x.where.contains(c) && x.startIn(c) <= year && year < x.endIn(c) && (u == null || x.priority > u.priority
                    || (x.priority == u.priority && x.startIn(c) > u.startIn(c)))) {
                u = x;
            }
        }
        Integer m = metric.get(c);
        boolean metricLand = m != null && !notMetric.contains(c) && year >= m;
        if (metricLand && year >= m + stillSaid) {
            return "People here measure in metres, kilograms and kilometres.";
        }
        if (u == null) {
            return metricLand ? "People here measure in metres, kilograms and kilometres." : noSystem;
        }
        if (metricLand) {
            List<String> old = new ArrayList<>();
            for (String[] p : new String[][]{u.weight, u.big, u.distance}) {
                if (p != null) {
                    old.add("the " + p[0]);
                }
            }
            return "The law says metres and kilograms (since " + m + "), but in the market people still speak of "
                    + TechnologyAtlasLists.join(old) + ".";
        }
        List<String> parts = new ArrayList<>();
        if (u.distance != null) {
            parts.add("count distance in the " + u.distance[0] + " (" + about(Double.parseDouble(u.distance[1]), "km") + ")");
        }
        if (u.length != null) {
            parts.add("measure cloth by the " + u.length[0] + " (" + about(Double.parseDouble(u.length[1]), "m") + ")");
        }
        if (u.weight != null) {
            String w = "weigh goods by the " + u.weight[0] + " (" + about(Double.parseDouble(u.weight[1]), "kg") + ")";
            if (u.big != null) {
                w += " and the " + u.big[0] + " (" + about(Double.parseDouble(u.big[1]), "kg") + ")";
            }
            parts.add(w);
        }
        if (u.land != null) {
            parts.add("measure land by the " + u.land[0] + " (" + about(Double.parseDouble(u.land[1]), "ha") + ")");
        }
        if (parts.isEmpty()) {
            return noSystem;
        }
        String out = "People here " + TechnologyAtlasLists.join(parts) + ".";
        if (u.small != null) {
            out += " Gold and silver go by the " + u.small[0] + " (" + about(Double.parseDouble(u.small[1]), "kg") + ").";
        }
        return out;
    }

    /** "about 3 km", "about 90 cm", "about 37 kg", "about 12 g", "about a third of a hectare". */
    static String about(double v, String unit) {
        switch (unit) {
            case "km":
                return v < 1.0d ? "about " + Math.round(v * 1000) + " m" : "about " + trim(v) + " km";
            case "m":
                return v < 1.0d ? "about " + Math.round(v * 100) + " cm" : "about " + trim(v) + " m";
            case "kg":
                return v < 1.0d ? "about " + (v < 0.01d ? trim(v * 1000) : String.valueOf(Math.round(v * 1000))) + " g"
                        : "about " + trim(v) + " kg";
            case "ha":
                if (v < 0.01d) {
                    return "about " + Math.round(v * 10000) + " square metres";
                }
                return v < 1.0d ? "about " + trim(v) + " of a hectare" : "about " + trim(v) + " hectares";
            default:
                return "about " + trim(v) + " " + unit;
        }
    }

    private static String trim(double v) {
        double r = v >= 10 ? Math.round(v) : Math.round(v * 10) / 10.0d;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    // ================================================================== wealth and hard times (C17)

    /** GDP per person in 2011 dollars that year (the Maddison Project), or -1. */
    public double wealth(double lat, double lon, int year) {
        double[] s = wealth.get(CountryGrid.get().codeAt(lat, lon));
        return s == null ? -1.0d : at(s, year);
    }

    /** "Most people here are poor farmers ... Life is getting better quickly." plus the hard times of the year. */
    public String wealthLine(double lat, double lon, int year) {
        double g = wealth(lat, lon, year);
        StringBuilder sb = new StringBuilder();
        if (g > 0) {
            if (g < 1000) {
                sb.append("Most people here are very poor: they grow their own food and have little else.");
            } else if (g < 1600) {
                sb.append("Most people here are poor farmers; a few landlords and merchants are rich.");
            } else if (g < 3000) {
                sb.append("Most people here get by; craftsmen and merchants have some money to spare.");
            } else if (g < 7000) {
                sb.append("Most people here have enough to eat, and many work in workshops, mills and offices.");
            } else if (g < 15000) {
                sb.append("Most people here live well.");
            } else if (g < 35000) {
                sb.append("This is a rich land: most people have more than they need.");
            } else {
                sb.append("This is one of the richest lands in the world.");
            }
            double before = wealth(lat, lon, year - 30);
            if (before > 0) {
                double ratio = g / before;
                if (ratio >= 1.6d) {
                    sb.append(" Life is getting better quickly: people have far more than their parents had.");
                } else if (ratio <= 0.85d) {
                    sb.append(" People here are poorer than their parents were.");
                }
            }
        }
        String c = CountryGrid.get().codeAt(lat, lon);
        for (Crisis k : crises) {
            if (k.where.contains(c) && k.start <= year && year < k.end) {
                sb.append(sb.length() > 0 ? " " : "").append(k.line);
                break;
            }
        }
        return sb.toString();
    }

    // ================================================================== holy places, festivals, pilgrimages (C13)

    /** The best known holy places standing within {@code km} that year (at most {@code max}). */
    public List<Holy> holyPlacesNear(double lat, double lon, int year, double km, int max) {
        List<Holy> out = new ArrayList<>();
        int cy = (int) Math.floor(lat);
        int cx = (int) Math.floor(lon);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                List<Holy> cell = holyGrid.get(key(cy + dy, cx + dx));
                if (cell == null) {
                    continue;
                }
                for (Holy h : cell) {
                    if (h.standsIn(year, holyDefaultFrom) && kmBetween(lat, lon, h.lat, h.lon) <= km) {
                        out.add(h);
                    }
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b.articles, a.articles));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /**
     * "The holy places here: the Mecca Masjid (a mosque, built in 1687) and ... Hindus here keep Holi, Diwali and
     * Dussehra; Muslims keep Ramadan, Eid al-Fitr and Eid al-Adha. Muslims here go on the Hajj to Mecca, about 3,600 km
     * away." Then today's feast, when the talk is on its day.
     */
    public String holyLine(double lat, double lon, int year, int month, int day, List<String> faithNames, double km) {
        List<String> parts = new ArrayList<>();
        List<Holy> near = holyPlacesNear(lat, lon, year, km, 3);
        if (!near.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Holy h : near) {
                String n = withArticle(h.name);
                String kind = h.kind == null || h.kind.isBlank() ? "" : article(h.kind) + " " + h.kind;
                String built = h.from != Integer.MIN_VALUE ? "built in " + yearText(h.from) : "";
                String in = kind.isEmpty() && built.isEmpty() ? "" : " (" + kind + (kind.isEmpty() || built.isEmpty() ? "" : ", ")
                        + built + ")";
                names.add(n + in);
            }
            parts.add("The holy places here: " + TechnologyAtlasLists.join(names) + ".");
        }
        List<Faith> fs = faithsOf(faithNames);
        if (fs.isEmpty()) {
            // no faith in the record for this land: the faith of the holy places standing around (within 50 km)
            Map<String, Integer> votes = new HashMap<>();
            for (Holy h : holyPlacesNear(lat, lon, year, 50.0d, 30)) {
                if (!h.faith.isEmpty()) {
                    votes.merge(h.faith, h.articles, Integer::sum);
                }
            }
            votes.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(2)
                    .forEach(e -> faiths.stream().filter(f -> f.key.equals(e.getKey())).findFirst().ifPresent(fs::add));
        }
        List<String> keeps = new ArrayList<>();
        for (Faith f : fs) {
            List<String> fest = new ArrayList<>();
            for (Object[] x : f.festivals) {
                if ((Integer) x[1] <= year && year < (Integer) x[2]) {
                    fest.add((String) x[0]);
                }
            }
            if (!fest.isEmpty()) {
                String who = f.word.equals("people here") ? (keeps.isEmpty() ? "People here" : "others") : f.word;
                keeps.add(who + (keeps.isEmpty() && !f.word.equals("people here") ? " here" : "") + " keep "
                        + TechnologyAtlasLists.join(fest));
            }
            if (keeps.size() >= 2) {
                break;
            }
        }
        if (!keeps.isEmpty()) {
            String s = String.join("; ", keeps) + ".";
            parts.add(Character.toUpperCase(s.charAt(0)) + s.substring(1));
        }
        for (Faith f : fs) {
            String p = pilgrimage(f, lat, lon, year);
            if (!p.isEmpty()) {
                parts.add(p);
                break;
            }
        }
        String today = todaysFeast(fs, year, month, day);
        if (!today.isEmpty()) {
            parts.add(today);
        }
        return String.join(" ", parts);
    }

    /**
     * Today's feast here, or "" when the calendar has nothing on.
     *
     * <p>Step 39 (D9) needs the feast on its own, without the holy places and pilgrimages
     * {@link #holyLine(double, double, int, int, int, List, double)} wraps it in: a festival scene
     * is on when the calendar has something, and is not on the other three hundred and fifty days.
     * Where no faith is in the record for this land, this reads the faith of the holy places
     * standing within 50 km, as {@code holyLine} does.
     */
    public String feastOn(double lat, double lon, int year, int month, int day,
                          List<String> faithNames) {
        List<Faith> fs = faithsOf(faithNames);
        if (fs.isEmpty()) {
            Map<String, Integer> votes = new HashMap<>();
            for (Holy h : holyPlacesNear(lat, lon, year, 50.0d, 30)) {
                if (!h.faith.isEmpty()) {
                    votes.merge(h.faith, h.articles, Integer::sum);
                }
            }
            votes.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(2)
                    .forEach(e -> faiths.stream().filter(f -> f.key.equals(e.getKey()))
                            .findFirst().ifPresent(fs::add));
        }
        return todaysFeast(fs, year, month, day);
    }

    private static final java.util.regex.Pattern GENERIC = java.util.regex.Pattern.compile(
            "(?i)\\b(mosque|masjid|temple|synagogue|shrine|pagoda|gurdwara|mandir|stupa|jinja|kovil|devasthanam)$");
    private static final java.util.regex.Pattern OF = java.util.regex.Pattern.compile(
            "(?i)^(temple|mosque|church|cathedral|basilica|shrine|abbey|monastery|synagogue|great mosque|great temple) of ");

    /**
     * "the Mecca Masjid", "the Golden Temple", "the Temple of Castor and Pollux"; but "Westminster Abbey", "Notre-Dame
     * de Paris", "St Paul's Cathedral", "Hagia Sophia", "Charminar".
     */
    static String withArticle(String name) {
        String low = name.toLowerCase(Locale.ROOT);
        if (low.startsWith("the ")) {
            return name;
        }
        if (OF.matcher(name).find()) {
            return "the " + name;
        }
        if (name.contains("'s ") || name.contains("’s ") || low.startsWith("st ") || low.startsWith("st. ")
                || low.startsWith("saint") || low.startsWith("san ") || low.startsWith("santa ") || low.startsWith("são ")
                || !GENERIC.matcher(name.replaceAll(",.*$", "")).find()) {
            return name;
        }
        return "the " + name;
    }

    private static String article(String kind) {
        return "aeiou".indexOf(Character.toLowerCase(kind.charAt(0))) >= 0 ? "an" : "a";
    }

    private String pilgrimage(Faith f, double lat, double lon, int year) {
        // The Hajj is every Muslim's; for the others the nearest holy journey of the faith, and before steamships and
        // railways (1900) only within about 3,000 km (Japanese Buddhists went to Mount Kōya, not to Bodh Gaya).
        List<Object[]> options = new ArrayList<>(f.pilgrimages);
        if (!f.key.equals("muslim")) {
            options.sort((a, b) -> Double.compare(kmBetween(lat, lon, (Double) a[2], (Double) a[3]),
                    kmBetween(lat, lon, (Double) b[2], (Double) b[3])));
        }
        for (Object[] p : options) {
            int from = (Integer) p[4];
            int to = (Integer) p[5];
            if (from > year || year >= to) {
                continue;
            }
            double km = kmBetween(lat, lon, (Double) p[2], (Double) p[3]);
            if (!f.key.equals("muslim") && year < 1900 && km > 3000.0d) {
                continue;
            }
            if (km < 30.0d) {
                return "Pilgrims come here from far away for " + p[0] + ".";
            }
            String who = f.word.equals("people here") ? "People here" : f.word + " here";
            return who + " go on pilgrimage to " + p[0] + String.format(Locale.ROOT, ", about %,d km away.",
                    Math.round(km / 100.0d) * 100);
        }
        return "";
    }

    /** The faith families of a place's faith names, in order (Shia adds to Muslim). */
    List<Faith> faithsOf(List<String> names) {
        Set<Faith> out = new LinkedHashSet<>();
        if (names != null) {
            for (String n : names) {
                String t = n == null ? "" : n.toLowerCase(Locale.ROOT);
                for (Faith f : faiths) {
                    for (String m : f.match) {
                        if (t.contains(m)) {
                            out.add(f);
                            break;
                        }
                    }
                    if (!out.isEmpty() && out.contains(f) && !f.key.equals("shia") && !f.key.equals("theravada")) {
                        // the first family that matches is the faith; Shia and Theravada add to Islam and Buddhism
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** "Today is Christmas." / "It is Ramadan: Muslims here fast from dawn to dusk." */
    String todaysFeast(List<Faith> fs, int year, int month, int day) {
        if (month <= 0 || day <= 0) {
            return "";
        }
        long jdn = jdnGregorian(year, month, day);
        for (Faith f : fs) {
            switch (f.key) {
                case "christian_west", "christian_east" -> {
                    boolean east = f.key.equals("christian_east");
                    boolean gregorian = !east && year >= 1583;
                    long christmas = gregorian ? jdnGregorian(year, 12, 25) : jdnJulian(year, 12, 25);
                    long christmasPrev = gregorian ? jdnGregorian(year - 1, 12, 25) : jdnJulian(year - 1, 12, 25);
                    if (year >= 336 && (jdn == christmas || jdn == christmasPrev)) {
                        return "Today is Christmas: people go to church and eat together.";
                    }
                    long easter = gregorian ? easterGregorian(year) : easterJulian(year);
                    if (year >= 150 && jdn == easter) {
                        return "Today is Easter Sunday: the church bells ring all morning.";
                    }
                    if (year >= 150 && jdn == easter - 2) {
                        return "Today is Good Friday: the churches are full and quiet.";
                    }
                }
                case "muslim", "shia" -> {
                    if (year < 624) {
                        continue;
                    }
                    int[] h = hijri(jdn);
                    if (h[1] == 9) {
                        return "It is Ramadan: Muslims here fast from dawn to dusk and eat together after sunset.";
                    }
                    if (h[1] == 10 && h[2] <= 3) {
                        return "It is Eid al-Fitr: people here put on new clothes, pray together and visit their families.";
                    }
                    if (h[1] == 12 && h[2] >= 10 && h[2] <= 13) {
                        return "It is Eid al-Adha: families here share the meat of the sacrifice with the poor.";
                    }
                    if (h[1] == 1 && h[2] == 10 && year >= 680) {
                        return "Today is Ashura: Shia Muslims mourn Husayn, and many Sunnis fast.";
                    }
                }
                default -> {
                }
            }
        }
        return "";
    }

    // ================================================================== calendars

    static long jdnGregorian(int y, int m, int d) {
        int a = (14 - m) / 12;
        long yy = y + 4800L - a;
        int mm = m + 12 * a - 3;
        return d + (153L * mm + 2) / 5 + 365L * yy + Math.floorDiv(yy, 4) - Math.floorDiv(yy, 100) + Math.floorDiv(yy, 400)
                - 32045;
    }

    static long jdnJulian(int y, int m, int d) {
        int a = (14 - m) / 12;
        long yy = y + 4800L - a;
        int mm = m + 12 * a - 3;
        return d + (153L * mm + 2) / 5 + 365L * yy + Math.floorDiv(yy, 4) - 32083;
    }

    /** Easter Sunday in the Gregorian calendar (the anonymous Gregorian algorithm), as a day number. */
    static long easterGregorian(int y) {
        int a = y % 19;
        int b = y / 100;
        int c = y % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return jdnGregorian(y, month, day);
    }

    /** Easter Sunday in the Julian calendar (Meeus), as a day number (so it can be compared with Gregorian days). */
    static long easterJulian(int y) {
        int a = Math.floorMod(y, 4);
        int b = Math.floorMod(y, 7);
        int c = Math.floorMod(y, 19);
        int d = (19 * c + 15) % 30;
        int e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31;
        int day = ((d + e + 114) % 31) + 1;
        return jdnJulian(y, month, day);
    }

    /** The day of the tabular Islamic calendar: {year, month, day}. */
    static int[] hijri(long jdn) {
        long l = jdn - 1948440 + 10632;
        long n = Math.floorDiv(l - 1, 10631);
        l = l - 10631 * n + 354;
        long j = (Math.floorDiv(10985 - l, 5316)) * (Math.floorDiv(50 * l, 17719))
                + (Math.floorDiv(l, 5670)) * (Math.floorDiv(43 * l, 15238));
        l = l - (Math.floorDiv(30 - j, 15)) * (Math.floorDiv(17719 * j, 50))
                - (Math.floorDiv(j, 16)) * (Math.floorDiv(15238 * j, 43)) + 29;
        long month = Math.floorDiv(24 * l, 709);
        long day = l - Math.floorDiv(709 * month, 24);
        long year = 30 * n + j - 30;
        return new int[]{(int) year, (int) month, (int) day};
    }

    // ================================================================== helpers and loading

    static double at(double[] s, int year) {
        if (year <= s[0]) {
            return s[1];
        }
        for (int k = 0; k + 3 < s.length; k += 2) {
            double y0 = s[k];
            double y1 = s[k + 2];
            if (y0 <= year && year <= y1) {
                return s[k + 1] + (s[k + 3] - s[k + 1]) * (year - y0) / Math.max(1.0d, y1 - y0);
            }
        }
        return s[s.length - 1];
    }

    static String yearText(int y) {
        return y < 0 ? (-y) + " BCE" : String.valueOf(y);
    }

    private static int key(int y, int x) {
        return (y + 90) * 1000 + (x + 180);
    }

    static double kmBetween(double la1, double lo1, double la2, double lo2) {
        double p1 = Math.toRadians(la1);
        double p2 = Math.toRadians(la2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371.0d * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double[] series(JsonArray a) {
        double[] s = new double[a.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = a.get(i).getAsDouble();
        }
        return s;
    }

    private static String[] pair(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonArray a = o.getAsJsonArray(key);
        return new String[]{a.get(0).getAsString(), a.get(1).getAsString()};
    }

    private static Map<String, Integer> years(JsonObject o, String key) {
        Map<String, Integer> out = new HashMap<>();
        if (o.has(key) && o.get(key).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
                out.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        return out;
    }

    private static int intOr(JsonElement e, int dflt) {
        return e == null || e.isJsonNull() ? dflt : e.getAsInt();
    }

    private void load() {
        try (InputStream raw = CultureAtlas.class.getResourceAsStream(FILE)) {
            if (raw == null) {
                problem = FILE + " is missing";
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("literacy").entrySet()) {
                literacy.put(e.getKey(), series(e.getValue().getAsJsonArray()));
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("literacyMark").entrySet()) {
                literacyMark.put(e.getKey(), e.getValue().getAsString());
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("wealth").entrySet()) {
                wealth.put(e.getKey(), series(e.getValue().getAsJsonArray()));
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("metric").entrySet()) {
                metric.put(e.getKey(), e.getValue().getAsInt());
            }
            for (JsonElement e : root.getAsJsonArray("notMetric")) {
                notMetric.add(e.getAsString());
            }
            stillSaid = root.get("stillSaid").getAsInt();
            noSystem = root.get("noSystem").getAsString();
            for (JsonElement ue : root.getAsJsonArray("units")) {
                JsonObject o = ue.getAsJsonObject();
                Set<String> where = new HashSet<>();
                for (JsonElement w : o.getAsJsonArray("where")) {
                    where.add(w.getAsString());
                }
                units.add(new Units(o.get("id").getAsString(), where, intOr(o.get("start"), Integer.MIN_VALUE),
                        intOr(o.get("end"), Integer.MAX_VALUE), o.has("priority") ? o.get("priority").getAsInt() : 0,
                        pair(o, "distance"), pair(o, "length"), pair(o, "weight"), pair(o, "big"), pair(o, "small"),
                        pair(o, "land"), years(o, "starts"), years(o, "ends")));
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("scripts").entrySet()) {
                List<Object[]> ps = new ArrayList<>();
                for (JsonElement pe : e.getValue().getAsJsonArray()) {
                    JsonArray p = pe.getAsJsonArray();
                    ps.add(new Object[]{intOr(p.get(0), Integer.MIN_VALUE), intOr(p.get(1), Integer.MAX_VALUE),
                            p.get(2).getAsString()});
                }
                scripts.put(e.getKey(), ps);
            }
            for (JsonElement e : root.getAsJsonArray("latin")) {
                latin.add(e.getAsString());
            }
            for (JsonElement fe : root.getAsJsonArray("faiths")) {
                JsonObject o = fe.getAsJsonObject();
                List<String> match = new ArrayList<>();
                for (JsonElement m : o.getAsJsonArray("match")) {
                    match.add(m.getAsString());
                }
                List<Object[]> fest = new ArrayList<>();
                for (JsonElement x : o.getAsJsonArray("festivals")) {
                    JsonArray a = x.getAsJsonArray();
                    fest.add(new Object[]{a.get(0).getAsString(), intOr(a.get(1), Integer.MIN_VALUE),
                            intOr(a.get(2), Integer.MAX_VALUE)});
                }
                List<Object[]> pil = new ArrayList<>();
                for (JsonElement x : o.getAsJsonArray("pilgrimages")) {
                    JsonArray a = x.getAsJsonArray();
                    pil.add(new Object[]{a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsDouble(),
                            a.get(3).getAsDouble(), intOr(a.get(4), Integer.MIN_VALUE), intOr(a.get(5), Integer.MAX_VALUE)});
                }
                faiths.add(new Faith(o.get("key").getAsString(), match, o.get("word").getAsString(), fest, pil));
            }
            for (JsonElement be : root.getAsJsonArray("literacyBase")) {
                JsonObject o = be.getAsJsonObject();
                Set<String> where = new HashSet<>();
                for (JsonElement w : o.getAsJsonArray("where")) {
                    where.add(w.getAsString());
                }
                bases.add(new Base(where, o.get("start").getAsInt(), o.get("end").getAsInt(), o.get("share").getAsDouble()));
            }
            baseDefault = root.get("literacyBaseDefault").getAsDouble();
            if (root.has("holyDefaultFrom")) {
                holyDefaultFrom = root.get("holyDefaultFrom").getAsInt();
            }
            for (JsonElement ce : root.getAsJsonArray("crises")) {
                JsonObject o = ce.getAsJsonObject();
                Set<String> where = new HashSet<>();
                for (JsonElement w : o.getAsJsonArray("where")) {
                    where.add(w.getAsString());
                }
                crises.add(new Crisis(where, o.get("start").getAsInt(), o.get("end").getAsInt(),
                        o.get("kind").getAsString(), o.get("line").getAsString()));
            }
        } catch (Exception e) {
            problem = "cannot read " + FILE + ": " + e;
            return;
        }
        try (InputStream raw = CultureAtlas.class.getResourceAsStream(HOLY_FILE)) {
            if (raw == null) {
                problem = HOLY_FILE + " is missing";
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement he : root.getAsJsonArray("places")) {
                JsonArray a = he.getAsJsonArray();
                Holy h = new Holy(a.get(0).getAsString(), a.get(1).getAsInt() / 1000.0d, a.get(2).getAsInt() / 1000.0d,
                        a.get(3).getAsString(), a.get(4).getAsString(), intOr(a.get(5), Integer.MIN_VALUE),
                        intOr(a.get(6), Integer.MAX_VALUE), a.get(7).getAsInt());
                holy.add(h);
                holyGrid.computeIfAbsent(key((int) Math.floor(h.lat), (int) Math.floor(h.lon)), k -> new ArrayList<>()).add(h);
            }
        } catch (Exception e) {
            problem = "cannot read " + HOLY_FILE + ": " + e;
        }
    }

    /** Lists in plain words ("a, b and c"). */
    static final class TechnologyAtlasLists {
        private TechnologyAtlasLists() {
        }

        static String join(List<String> names) {
            if (names.isEmpty()) {
                return "";
            }
            if (names.size() == 1) {
                return names.get(0);
            }
            return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
        }
    }
}

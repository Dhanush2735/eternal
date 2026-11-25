package com.eternalengine.knowledge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hand-checked knowledge packs from the mod jar ({@code data/eternalengine/eternal/knowledge}).
 *
 * <p>A pack covers a region and a date range, for example Hyderabad State from August 1947 to the end of
 * 1948. Inside it, villagers talk with the pack's cards and news lines; outside, they use the basic cards
 * built from the history data ({@link LocalCards}). Packs are read once, on first use.
 */
public final class KnowledgePacks {

    public static final String DIR = "/data/eternalengine/eternal/knowledge/";
    private static final Pattern LINK = Pattern.compile("\\[\\[(\\w+)\\|([^\\]]+)\\]\\]");
    private static final Pattern DIST = Pattern.compile("\\{d:(\\w+)\\}");

    /** A news line from {@code from} to {@code to} (both included; {@link KnowledgeCard#OPEN}: until a newer line). */
    public record News(int from, int to, int level, String text) {
    }

    public record Story(String headline, String text, List<String> places) {
    }

    public record PaperDay(int date, List<Story> stories) {
    }

    /** Step 19e: a town on a pack's map; {@code major} towns get a bigger dot and name. */
    public record MapTown(String name, double lat, double lon, boolean major) {
    }

    /** Step 19e: the hand-made map of a pack (towns and roads of its time); null when the pack has none. */
    public record PackMap(String title, String subtitle, double minLat, double maxLat, double minLon, double maxLon,
                          List<MapTown> towns, List<double[]> roads) {
    }

    public record Pack(String id, String title, double lat, double lon, double radiusKm, int from, int to,
                       Map<String, KnowledgeCard> cards, List<News> news, List<PaperDay> papers, String referText, String referTrack,
                       Map<String, String> topics, String paperName, PackMap map) {
        public boolean covers(double atLat, double atLon, int date) {
            return date >= from && date <= to && Geo.km(lat, lon, atLat, atLon) <= radiusKm;
        }

        /** The news line a villager of this level opens with on this date, or null. */
        public News newsFor(int date, int level) {
            News best = null;
            for (News n : news) {
                if (n.from() <= date && n.level() <= level && (best == null || n.from() > best.from()
                        || (n.from() == best.from() && n.level() > best.level()))) {
                    best = n;
                }
            }
            // The newest line, if it still holds: an older line is staler still ("yesterday" said a day late).
            return best != null && date <= best.to() ? best : null;
        }
    }

    private static volatile List<Pack> packs;
    private static final List<String> loadProblems = new ArrayList<>();

    private KnowledgePacks() {
    }

    public static List<Pack> all() {
        List<Pack> p = packs;
        if (p == null) {
            synchronized (KnowledgePacks.class) {
                if (packs == null) {
                    packs = load();
                }
                p = packs;
            }
        }
        return p;
    }

    /** Packs whose region and dates hold this spot and day. */
    public static List<Pack> covering(double lat, double lon, int date) {
        List<Pack> out = new ArrayList<>();
        for (Pack p : all()) {
            if (p.covers(lat, lon, date)) {
                out.add(p);
            }
        }
        return out;
    }

    public static List<String> loadProblems() {
        all();
        synchronized (loadProblems) {
            return List.copyOf(loadProblems);
        }
    }

    private static List<Pack> load() {
        List<Pack> out = new ArrayList<>();
        JsonObject index = read("index.json");
        if (index == null) {
            problem("index.json is missing");
            return out;
        }
        for (JsonElement e : KnowledgeCard.arr(index, "packs")) {
            String file = e.getAsString();
            try {
                JsonObject o = read(file);
                if (o == null) {
                    problem(file + " is missing");
                    continue;
                }
                out.add(parse(o));
            } catch (Exception ex) {
                problem(file + ": " + ex.getMessage());
            }
        }
        return List.copyOf(out);
    }

    static Pack parse(JsonObject o) {
        JsonObject region = o.getAsJsonObject("region");
        Map<String, KnowledgeCard> cards = new LinkedHashMap<>();
        for (JsonElement ce : KnowledgeCard.arr(o, "cards")) {
            KnowledgeCard c = KnowledgeCard.fromJson(ce.getAsJsonObject());
            cards.put(c.id(), c);
        }
        List<News> news = new ArrayList<>();
        for (JsonElement ne : KnowledgeCard.arr(o, "news")) {
            JsonObject n = ne.getAsJsonObject();
            news.add(new News(KnowledgeCard.date(n, "from"), KnowledgeCard.until(n), n.has("level") ? n.get("level").getAsInt() : 1,
                    KnowledgeCard.str(n, "text")));
        }
        List<PaperDay> papers = new ArrayList<>();
        for (JsonElement pe : KnowledgeCard.arr(o, "papers")) {
            JsonObject p = pe.getAsJsonObject();
            List<Story> stories = new ArrayList<>();
            for (JsonElement se : KnowledgeCard.arr(p, "stories")) {
                JsonObject s = se.getAsJsonObject();
                stories.add(new Story(KnowledgeCard.str(s, "headline"), KnowledgeCard.str(s, "text"), KnowledgeCard.strings(s, "places")));
            }
            papers.add(new PaperDay(KnowledgeCard.date(p, "date"), List.copyOf(stories)));
        }
        Map<String, String> topics = new LinkedHashMap<>();
        if (o.has("topics") && o.get("topics").isJsonObject()) {
            for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("topics").entrySet()) {
                topics.put(t.getKey(), t.getValue().getAsString());
            }
        }
        String referText = null, referTrack = null;
        if (o.has("refer") && o.get("refer").isJsonObject()) {
            referText = KnowledgeCard.str(o.getAsJsonObject("refer"), "text");
            referTrack = KnowledgeCard.str(o.getAsJsonObject("refer"), "track");
        }
        return new Pack(KnowledgeCard.str(o, "id"), KnowledgeCard.str(o, "title"), region.get("lat").getAsDouble(), region.get("lon").getAsDouble(),
                region.get("radiusKm").getAsDouble(), KnowledgeCard.date(o, "from"), KnowledgeCard.date(o, "to"),
                Map.copyOf(cards), List.copyOf(news), List.copyOf(papers), referText, referTrack, Map.copyOf(topics),
                KnowledgeCard.str(o, "paperName"), packMap(o));
    }

    /** Step 19e: the pack's map ({"title", "subtitle", "box": [minLat, maxLat, minLon, maxLon], "towns", "roads"}). */
    static PackMap packMap(JsonObject o) {
        if (!o.has("map") || !o.get("map").isJsonObject()) {
            return null;
        }
        JsonObject m = o.getAsJsonObject("map");
        var box = m.getAsJsonArray("box");
        List<MapTown> towns = new ArrayList<>();
        for (JsonElement te : KnowledgeCard.arr(m, "towns")) {
            var t = te.getAsJsonArray();
            towns.add(new MapTown(t.get(0).getAsString(), t.get(1).getAsDouble(), t.get(2).getAsDouble(),
                    t.size() > 3 && t.get(3).getAsInt() != 0));
        }
        List<double[]> roads = new ArrayList<>();
        for (JsonElement re : KnowledgeCard.arr(m, "roads")) {
            var r = re.getAsJsonArray();
            double[] pts = new double[r.size() * 2];
            for (int i = 0; i < r.size(); i++) {
                pts[2 * i] = r.get(i).getAsJsonArray().get(0).getAsDouble();
                pts[2 * i + 1] = r.get(i).getAsJsonArray().get(1).getAsDouble();
            }
            roads.add(pts);
        }
        return new PackMap(KnowledgeCard.str(m, "title"), KnowledgeCard.str(m, "subtitle"), box.get(0).getAsDouble(),
                box.get(1).getAsDouble(), box.get(2).getAsDouble(), box.get(3).getAsDouble(), List.copyOf(towns),
                List.copyOf(roads));
    }

    /**
     * Problems a pack has: links to cards that do not exist, distances to cards without a location,
     * tracks without a location. Empty when the pack is sound. Used by /ee check all and the probe.
     */
    public static List<String> validate(Pack p) {
        List<String> out = new ArrayList<>();
        List<String[]> texts = new ArrayList<>();
        for (KnowledgeCard c : p.cards().values()) {
            for (String l : c.links()) {
                if (!p.cards().containsKey(l)) {
                    out.add(c.id() + " links to missing card " + l);
                }
            }
            for (KnowledgeCard.Question q : c.questions()) {
                if (q.answers().isEmpty()) {
                    out.add(c.id() + "." + q.id() + " has no answer");
                }
                List<KnowledgeCard.Answer> as = q.answers();
                for (int i = 0; i < as.size(); i++) {
                    KnowledgeCard.Answer a = as.get(i);
                    String where = c.id() + "." + q.id() + " (" + (a.from() == KnowledgeDate.ALWAYS ? "always" : KnowledgeDate.label(a.from())) + ")";
                    dayProblems(where, a.from(), a.to(), a.text(), out);
                    // Step 18b: no silent days. Once a question has an answer, some answer holds every day after,
                    // to the end of the pack: in a Terra++ world every day comes.
                    int next = i + 1 < as.size() ? as.get(i + 1).from() : KnowledgeDate.nextDay(p.to());
                    if (a.to() != KnowledgeCard.OPEN && KnowledgeDate.nextDay(a.to()) < next) {
                        out.add(where + " ends on " + KnowledgeDate.label(a.to()) + ", and nothing answers until "
                                + (i + 1 < as.size() ? KnowledgeDate.label(next) : "the end of the pack"));
                    }
                }
                for (KnowledgeCard.Answer a : q.answers()) {
                    texts.add(new String[]{c.id() + "." + q.id(), a.text()});
                    if (a.track() != null && !a.track().isEmpty()) {
                        KnowledgeCard t = p.cards().get(a.track());
                        if (t == null || !t.hasLocation()) {
                            out.add(c.id() + "." + q.id() + " tracks " + a.track() + ", which has no location");
                        }
                    }
                }
            }
        }
        for (News n : p.news()) {
            texts.add(new String[]{"news " + KnowledgeDate.label(n.from()), n.text()});
            dayProblems("news (" + KnowledgeDate.label(n.from()) + ", level " + n.level() + ")", n.from(), n.to(), n.text(), out);
        }
        for (PaperDay d : p.papers()) {
            for (Story s : d.stories()) {
                texts.add(new String[]{"paper " + KnowledgeDate.label(d.date()), s.text()});
            }
        }
        if (p.referText() != null) {
            texts.add(new String[]{"refer", p.referText()});
        }
        for (Map.Entry<String, String> t : p.topics().entrySet()) {
            String[] parts = t.getValue().split("\\.", 2);
            KnowledgeCard c = p.cards().get(parts[0]);
            if (c == null || parts.length < 2 || c.question(parts[1]) == null) {
                out.add("topic " + t.getKey() + " points to missing question " + t.getValue());
            }
        }
        for (String[] t : texts) {
            Matcher m = LINK.matcher(t[1]);
            while (m.find()) {
                if (!p.cards().containsKey(m.group(1))) {
                    out.add(t[0] + " links to missing card " + m.group(1));
                }
            }
            Matcher d = DIST.matcher(t[1]);
            while (d.find()) {
                KnowledgeCard c = p.cards().get(d.group(1));
                if (c == null || !c.hasLocation()) {
                    out.add(t[0] + " measures a distance to " + d.group(1) + ", which has no location");
                }
            }
            if (t[1].contains("[[") && !LINK.matcher(t[1]).find()) {
                out.add(t[0] + " has a broken link");
            }
        }
        return out;
    }

    /** A line with "today", "yesterday" and the like must end on its own day; and no line may end before it starts. */
    private static void dayProblems(String where, int from, int to, String text, List<String> out) {
        if (to != KnowledgeCard.OPEN && to < from) {
            out.add(where + " ends before it starts");
        }
        Matcher m = KnowledgeCard.DAY_WORDS.matcher(text == null ? "" : text);
        if (m.find() && (from == KnowledgeDate.ALWAYS || to != from)) {
            out.add(where + " says \"" + m.group(1) + "\" but holds for more than its own day (give it \"to\": its day)");
        }
    }

    private static JsonObject read(String name) {
        try (InputStream in = KnowledgePacks.class.getResourceAsStream(DIR + name)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            problem(name + ": " + e.getMessage());
            return null;
        }
    }

    private static void problem(String text) {
        synchronized (loadProblems) {
            loadProblems.add(text);
        }
    }

    /** Distances and directions on the globe. */
    public static final class Geo {
        private static final String[] DIRS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

        private Geo() {
        }

        public static double km(double lat1, double lon1, double lat2, double lon2) {
            double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
            double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            return 2 * 6371.0d * Math.asin(Math.min(1.0d, Math.sqrt(h)));
        }

        public static double bearing(double lat1, double lon1, double lat2, double lon2) {
            double y = Math.sin(Math.toRadians(lon2 - lon1)) * Math.cos(Math.toRadians(lat2));
            double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                    - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lon2 - lon1));
            return (Math.toDegrees(Math.atan2(y, x)) + 360.0d) % 360.0d;
        }

        public static String direction(double deg) {
            return DIRS[(int) Math.round(deg / 45.0d) % 8];
        }

        public static String kmText(double km) {
            if (km < 10) {
                String s = String.valueOf(Math.round(km * 2) / 2.0d);
                return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + " km";
            }
            if (km < 100) {
                return Math.round(km / 5) * 5 + " km";
            }
            return Math.round(km / 10) * 10 + " km";
        }

        /** "about 240 km west of here", or "right here". */
        public static String phrase(double fromLat, double fromLon, double toLat, double toLon) {
            double km = km(fromLat, fromLon, toLat, toLon);
            if (km < 0.35d) {
                return "right here";
            }
            return "about " + kmText(km) + " " + direction(bearing(fromLat, fromLon, toLat, toLon)) + " of here";
        }
    }
}

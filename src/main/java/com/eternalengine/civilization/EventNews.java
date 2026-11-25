package com.eternalengine.civilization;

import java.util.ArrayList;
import java.util.List;

/**
 * Civilian rumor lines for sourced events. News only — never an attack script.
 */
public final class EventNews {

    private EventNews() {}

    public static String rumor(HistoryGazetteer.Event event, int year) {
        if (event == null) {
            return "";
        }
        String name = event.name() == null || event.name().isBlank() ? "something that happened here" : event.name();
        String fx = event.effect() == null ? "note" : event.effect().toLowerCase(java.util.Locale.ROOT);
        if (HistoryGazetteer.isRegional(event)) {
            // News from a whole region or country, not something on this street.
            if (fx.contains("political")) {
                return "Travellers say the whole land is talking about " + name + ".";
            }
            if (fx.contains("disruption")) {
                return "Bad news from afar: " + name + ". Trade and travel are suffering for it.";
            }
            return "News from afar this year: " + name + ".";
        }
        if (fx.contains("lockdown")) {
            return "Have you heard? " + name + ". People are talking of little else this year. Stay off the streets if you can.";
        }
        if (fx.contains("memorial")) {
            return "This year we speak of " + name + ". We remember. We do not take up arms over it.";
        }
        if (fx.contains("military")) {
            return "Word from nearby: " + name + ". Soldiers on the roads, not a new town.";
        }
        if (fx.contains("political")) {
            return "Everyone is talking about " + name + " this year.";
        }
        if (fx.contains("disruption")) {
            return "Markets and travel are disrupted because of " + name + ".";
        }
        return "The news this year is " + name + ".";
    }

    public static String placeLine(HistoricalPresence.Result result) {
        if (result == null || !result.occupied()) {
            return "I have not heard of a town here in this year.";
        }
        if (result.site().isPresent()) {
            return "This is " + placeName(result) + ", year " + result.year() + ".";
        }
        if (result.presentMap().isPresent()) {
            return "This named place is " + result.presentMap().get().name() + ".";
        }
        // Step 29: no site in the history data, but a town of the town list stands here by then.
        var town = townHere(result);
        if (town.isPresent() && town.get().isSite()) {
            return "This is " + town.get().town().nameIn(result.year()) + ", year " + result.year() + ".";
        }
        return "People live around here in year " + result.year() + ".";
    }

    // ---------------------------------------------------------------- step 29: towns through time (C2-C6)

    /**
     * Step 29 (C4): the name of the history data's site in that year. A site that is a town of the town list takes
     * the town's dated name (Bombay until 1995, Edo until 1868, Leopoldville until 1966, Byzantium before 330), else
     * the town's name today ("mumbai" is Mumbai); a site that is no town of the list keeps its own name.
     */
    public static String placeName(HistoricalPresence.Result result) {
        if (result == null || result.site().isEmpty()) {
            return "";
        }
        var s = result.site().get().site();
        String raw = s.displayName();
        try {
            var t = TownFacts.get().matching(raw, s.lat(), s.lon());
            if (t.isPresent()) {
                // The dated name of that year, else the town's own name today ("mumbai" is Mumbai, "Wroclaw"
                // Wrocław, "Madras" in 2010 Chennai).
                return t.get().datedNameIn(result.year()).orElse(t.get().label());
            }
        } catch (Throwable ignored) {
            // No town file: the site's own name.
        }
        return raw;
    }

    /** The town of the town list a place is ({@code isSite}: "This is Bombay") or lies in ("in the town of ..."). */
    public record TownHere(TownFacts.Town town, boolean isSite) {
    }

    /**
     * Step 29: the town for this place and year. A history site whose name is a town's (now or in an earlier era) is
     * that town, whatever the year; otherwise the town whose reach (from its people that year) holds the point and
     * that is known by then. Without a site or a present-day name, that town is the place itself.
     */
    public static java.util.Optional<TownHere> townHere(HistoricalPresence.Result result) {
        if (result == null || !result.occupied()) {
            return java.util.Optional.empty();
        }
        try {
            TownFacts tf = TownFacts.get();
            if (!tf.loaded()) {
                return java.util.Optional.empty();
            }
            if (result.site().isPresent()) {
                var s = result.site().get().site();
                var m = tf.matching(s.displayName(), s.lat(), s.lon());
                if (m.isPresent()) {
                    return java.util.Optional.of(new TownHere(m.get(), true));
                }
            }
            var n = tf.near(result.lat(), result.lon(), result.year());
            boolean itself = result.site().isEmpty() && result.presentMap().isEmpty();
            return n.map(t -> new TownHere(t, itself));
        } catch (Throwable ignored) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Step 29 (C4, C5, C6): what a villager knows of the town: the town this place lies in (when it is a part of
     * it), how many people live there that year, whether it grows or shrinks, when it was founded, its former name,
     * and another name people use. Empty when the town list has nothing here.
     */
    public static String townLine(HistoricalPresence.Result result) {
        var here = townHere(result);
        if (here.isEmpty()) {
            return "";
        }
        TownFacts.Town town = here.get().town();
        int year = result.year();
        String name = town.nameIn(year);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!here.get().isSite()) {
            out.add("It lies in the town of " + name + ".");
        }
        for (String line : new String[]{town.peopleLine(year, name), town.growthLine(year), town.foundedLine(year),
                town.formerNameLine(year), town.alsoLine(year)}) {
            if (!line.isBlank()) {
                out.add(line);
            }
        }
        return String.join(" ", out);
    }

    /**
     * Step 29 (C3): "It lies in the Bombay Presidency." for the town of this place in that year; nothing when the
     * province has the ruling state's own name.
     */
    public static String provinceLine(HistoricalPresence.Result result) {
        String state = result == null ? null : result.ruler().map(RulerRegister.Hit::ruler).orElse(null);
        return townHere(result).map(t -> t.town().provinceLine(result.year(), state)).orElse("");
    }

    /**
     * Step 30a: the size of this place in that year, for how common a technology is here: the history site's own
     * rank, else the town list's count of its people (a city from 100,000, a town from 10,000), else a village.
     */
    public static SettlementRank settlementRank(HistoricalPresence.Result result) {
        if (result == null) {
            return SettlementRank.VILLAGE;
        }
        if (result.site().isPresent()) {
            return result.site().get().site().rankAt(result.year());
        }
        var town = townHere(result);
        if (town.isPresent()) {
            long people = town.get().town().peopleIn(result.year());
            if (people >= 100_000L) {
                return SettlementRank.CITY;
            }
            if (people >= 10_000L || people < 0) {
                return SettlementRank.TOWN;
            }
        }
        return SettlementRank.VILLAGE;
    }

    // ---------------------------------------------------------------- step 30b: culture (C13, C14, C17, C26, C27)

    /** Step 30b: the faiths of this place that year, the biggest first (the survey's, else the state's and the people's). */
    public static List<String> faithNames(HistoricalPresence.Result result) {
        List<String> out = new ArrayList<>();
        var f = cultureFacts(result);
        if (f.isEmpty()) {
            return out;
        }
        var survey = f.get().religionSharesIn(result.year());
        if (survey.isPresent()) {
            for (LeaderRegister.Share s : survey.get().shares()) {
                if (!"no religion".equals(s.faith()) && !"other faiths".equals(s.faith()) && s.percent() >= 5.0d) {
                    out.add(s.faith());
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        var folk = f.get().folkFaith();
        var main = f.get().faithIn(result.year());
        if (folk != null && folk.covers(result.lat(), result.lon())) {
            out.add(folk.faith());
        }
        main.ifPresent(out::add);
        return out;
    }

    /** Step 30b (C27, C14): who can read here, and in which script people write. */
    public static String readLine(HistoricalPresence.Result result) {
        if (result == null || !result.occupied()) {
            return "";
        }
        try {
            String local = LocalLanguages.get().peoplesLanguage(result.lat(), result.lon(), result.year())
                    .map(h -> h.language().name()).orElse(null);
            String court = cultureFacts(result).flatMap(x -> x.languages().stream().findFirst()).orElse(null);
            return CultureAtlas.get().readLine(result.lat(), result.lon(), result.year(), settlementRank(result), local, court);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Step 30b (C26): how people here weigh and measure. */
    public static String measureLine(HistoricalPresence.Result result) {
        if (result == null || !result.occupied()) {
            return "";
        }
        try {
            return CultureAtlas.get().measureLine(result.lat(), result.lon(), result.year());
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Step 30b (C17): how well off people here are, and the hard times. */
    public static String wealthLine(HistoricalPresence.Result result) {
        if (result == null || !result.occupied()) {
            return "";
        }
        try {
            return CultureAtlas.get().wealthLine(result.lat(), result.lon(), result.year());
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Step 30b (C13): the holy places here, the festivals people keep, their pilgrimages, today's feast. */
    public static String holyLine(HistoricalPresence.Result result) {
        if (result == null || !result.occupied()) {
            return "";
        }
        try {
            int today = com.eternalengine.knowledge.KnowledgeDate.today();
            boolean sameYear = com.eternalengine.knowledge.KnowledgeDate.year(today) == result.year();
            int month = sameYear ? com.eternalengine.knowledge.KnowledgeDate.month(today) : 0;
            int day = sameYear ? com.eternalengine.knowledge.KnowledgeDate.day(today) : 0;
            double km = switch (settlementRank(result)) {
                case CITY -> 12.0d;
                case TOWN -> 6.0d;
                case VILLAGE, COUNTRYSIDE -> 3.0d;
            };
            return CultureAtlas.get().holyLine(result.lat(), result.lon(), result.year(), month, day, faithNames(result), km);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Step 30a (C16): "People here have the railway ... Nobody here has television yet." */
    public static String technologyLine(HistoricalPresence.Result result) {
        if (result == null || !result.occupied()) {
            return "";
        }
        try {
            return com.eternalengine.technology.TechnologyAtlas.get().talkLine(result.lat(), result.lon(),
                    result.year(), settlementRank(result));
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Step 29 (C2): who runs the town (a mayor, a governor ...) and its province in that year. */
    public static String headLine(HistoricalPresence.Result result) {
        return townHere(result).map(t -> t.town().headLine(result.year())).orElse("");
    }

    // ---------------------------------------------------------------- talk of the land
    // Lines a villager can say about where they live. Each is empty when the data has nothing,
    // and nothing is invented: rulers, languages, religions and people come from the registers
    // built from Wikipedia; only the way of life is an estimate, and it is said as one.

    private static final java.util.regex.Pattern STATE_WORD = java.util.regex.Pattern.compile(
            "(?i)\\b(empire|kingdom|dynasty|republic|sultanate|caliphate|khanate|confederacy|confederation|"
                    + "federation|states|union|duchy|principality|emirate|shogunate|commonwealth|realm|islands|"
                    + "netherlands|philippines|league|city-states|tribes|beylik|grand duchy|county|march|raj)\\b");

    /** "the Mughal Empire", "Hokuzan": an article only before names like Empire or Kingdom. */
    static String stateName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        boolean people = lower.equals("taliban") || lower.matches(".*\\b\\w+(ids|ads|uks)$");   // "the Abbasids"
        return (STATE_WORD.matcher(name).find() || people) && !lower.startsWith("the ")
                ? "the " + name : name;
    }

    private static java.util.Optional<LeaderRegister.Facts> facts(HistoricalPresence.Result result) {
        if (result == null || result.ruler().isEmpty()) {
            return java.util.Optional.empty();
        }
        return LeaderRegister.get().facts(result.ruler().get().wikidata());
    }

    private static final java.util.Set<String> NAME_STOP = java.util.Set.of("the", "of", "and", "empire", "kingdom",
            "dynasty", "state", "states", "republic", "sultanate", "caliphate", "principality", "duchy", "great",
            "first", "second", "third", "later", "early", "late", "new", "old", "united", "minor", "period", "in");

    /**
     * True when the infobox the facts came from is this state's own article: a few states share a
     * Wikidata id with another (the Roman Empire with the Byzantine Empire), and that article's
     * languages and faiths would then be wrong for them.
     */
    static boolean articleFits(String stateName, String article) {
        if (stateName == null || article == null) {
            return false;
        }
        java.util.Set<String> a = words(stateName);
        java.util.Set<String> b = words(article);
        for (String x : a) {
            for (String y : b) {
                if (x.equals(y) || (x.length() >= 5 && y.length() >= 5 && (x.startsWith(y) || y.startsWith(x)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static java.util.Set<String> words(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        String norm = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        for (String w : norm.toLowerCase(java.util.Locale.ROOT).split("[^a-z]+")) {
            if (w.length() > 2 && !NAME_STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /** The facts, only if their article is the state's own (see {@link #articleFits}). */
    private static java.util.Optional<LeaderRegister.Facts> cultureFacts(HistoricalPresence.Result result) {
        return facts(result).filter(f -> articleFits(result.ruler().get().ruler(), f.article())
                || articleFits(result.ruler().get().ruler(), f.name()) && f.article().equals(f.name()));
    }

    /** "This land belongs to the Mughal Empire, ruled by Aurangzeb (Emperor)." */
    public static String rulerLine(HistoricalPresence.Result result) {
        if (result == null || result.ruler().isEmpty() || result.ruler().get().ruler() == null) {
            return "";
        }
        // Step 28 (known problem 39): land one state holds and another claims has its own line.
        var disputed = OccupiedLands.get().disputedLine(result.lat(), result.lon(), result.year());
        if (disputed.isPresent()) {
            return disputed.get();
        }
        String state = stateName(result.ruler().get().ruler());
        var head = facts(result).flatMap(f -> f.headIn(result.year()));
        String line = head.map(h -> "This land belongs to " + state + ", ruled by " + h.display() + ".")
                .orElse("This land belongs to " + state + ".");
        // Step 28 (known problem 40): the state went on, but troops of another power held the land.
        var held = OccupiedLands.get().heldIn(result.ruler().get().place(), result.lat(), result.lon(), result.year());
        if (held.isPresent()) {
            line += " " + held.get().who() + " troops have held this land since " + held.get().from() + ".";
        }
        return line;
    }

    /** Step 27: the name of the head of the state here in this year ("Aurangzeb"), for a link to the ruler's card. */
    public static java.util.Optional<String> rulerName(HistoricalPresence.Result result) {
        if (result == null || result.ruler().isEmpty() || result.ruler().get().ruler() == null) {
            return java.util.Optional.empty();
        }
        return facts(result).flatMap(f -> f.headIn(result.year())).map(LeaderRegister.Reign::name)
                .filter(n -> n != null && !n.isBlank());
    }

    /**
     * Languages and faiths of the place.
     *
     * <p>Languages: the state's own (its Wikipedia infobox); from AD 1500 on, also the big language
     * spoken around here (Glottolog) when it differs: "The court speaks Persian; people here speak
     * Telugu." Faiths: from 1945 the measured shares (Correlates of War World Religion Data), else the
     * state's first religion only (an infobox lists every faith over the state's whole life).
     */
    public static String cultureLine(HistoricalPresence.Result result) {
        if (result == null) {
            return "";
        }
        var f = cultureFacts(result);
        List<String> langs = f.map(x -> x.languages().stream().limit(2).toList()).orElse(List.of());
        String local = LocalLanguages.get().peoplesLanguage(result.lat(), result.lon(), result.year())
                .map(h -> h.language().name()).orElse(null);
        StringBuilder sb = new StringBuilder();
        if (local != null && !langs.isEmpty() && langs.stream().noneMatch(l -> sameLanguage(l, local))) {
            sb.append(result.year() >= 1800 ? "The government uses " : "The court speaks ")
                    .append(String.join(" and ", langs)).append("; people here speak ").append(local).append(".");
        } else if (!langs.isEmpty()) {
            sb.append("People here speak ").append(String.join(" or ", langs)).append(".");
        } else if (local != null) {
            sb.append("People here speak ").append(local).append(".");
        }
        String faith = faithSentence(result, f);
        if (!faith.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(faith);
        }
        return sb.toString();
    }

    static boolean sameLanguage(String a, String b) {
        String x = a.toLowerCase(java.util.Locale.ROOT);
        String y = b.toLowerCase(java.util.Locale.ROOT);
        return x.contains(y) || y.contains(x);
    }

    private static String faithSentence(HistoricalPresence.Result result, java.util.Optional<LeaderRegister.Facts> f) {
        if (f.isEmpty()) {
            return "";
        }
        var survey = f.get().religionSharesIn(result.year());
        if (survey.isPresent()) {
            double none = 0.0d;
            List<LeaderRegister.Share> faiths = new ArrayList<>();
            for (LeaderRegister.Share s : survey.get().shares()) {
                if ("no religion".equals(s.faith())) {
                    none = s.percent();
                } else if (!"other faiths".equals(s.faith())) {
                    faiths.add(s);
                }
            }
            String lead = none >= 40.0d ? String.format(java.util.Locale.ROOT,
                    "Many people here follow no religion (about %.0f%%). ", none) : "";
            if (faiths.isEmpty()) {
                return lead.trim();
            }
            LeaderRegister.Share a = faiths.get(0);
            if (faiths.size() > 1 && faiths.get(1).percent() >= 10.0d) {
                return lead + String.format(java.util.Locale.ROOT, "The main faiths here are %s (%.0f%%) and %s (%.0f%%).",
                        a.faith(), a.percent(), faiths.get(1).faith(), faiths.get(1).percent());
            }
            return lead + String.format(java.util.Locale.ROOT, "The main faith here is %s (about %.0f%% of people).",
                    a.faith(), a.percent());
        }
        // Step 25: the faith of the year (England is Anglican from 1534), and where the court's faith was not the
        // people's (the Mughal court was Muslim, most of its people Hindu), both.
        var main = f.get().faithIn(result.year());
        if (main.isEmpty()) {
            return "";
        }
        var folk = f.get().folkFaith();
        if (folk != null && folk.covers(result.lat(), result.lon()) && !sameLanguage(folk.faith(), main.get())) {
            return "The court follows " + main.get() + "; most people here follow " + folk.faith() + ".";
        }
        return "The main faith here is " + main.get() + ".";
    }

    /**
     * A famous person of this land: someone who died this year first; else, by turns, someone born
     * near here (exact birthplaces, within 15 km) or someone of this land alive now.
     */
    public static String famousLine(HistoricalPresence.Result result, long seed) {
        if (result == null) {
            return "";
        }
        String qid = result.ruler().map(RulerRegister.Hit::wikidata).orElse(null);
        FamousPeople people = FamousPeople.get();
        // Step 25: news of a death comes from within a few weeks' travel (a New Zealand chief's death is not told in
        // Hyderabad), and the people talked about are from this part of the land when there are any.
        List<FamousPeople.Person> died = near(people.diedIn(qid, result.lat(), result.lon(), result.year()),
                result.lat(), result.lon(), DEATH_NEWS_KM, false);
        if (!died.isEmpty()) {
            FamousPeople.Person d = died.get(Math.floorMod(seed, died.size()));
            return "Sad news this year: " + d.display() + (d.description() == null || d.description().isBlank()
                    ? " has died." : ", has died.");
        }
        List<FamousPeople.Person> born = people.bornNear(result.lat(), result.lon(), result.year(), 15.0d);
        List<FamousPeople.Person> living = near(people.livingIn(qid, result.lat(), result.lon(), result.year()),
                result.lat(), result.lon(), TALKED_ABOUT_KM, true);
        if (!born.isEmpty() && (living.isEmpty() || (seed & 1L) == 0L)) {
            FamousPeople.Person p = born.get(Math.floorMod(seed >>> 1, Math.min(born.size(), 8)));
            String who = p.description() == null || p.description().isBlank()
                    ? p.name() : p.name() + ", the " + p.description() + ",";
            int bornYear = p.birth() != FamousPeople.UNKNOWN ? p.birth() : p.death() - 40;
            return who + (result.year() - bornYear > 100 ? " was born near here, long ago." : " was born near here.");
        }
        if (living.isEmpty()) {
            return "";
        }
        return "People here talk about " + living.get(Math.floorMod(seed, living.size())).display() + ".";
    }

    /** Deaths further away than this are not told as this year's news (step 25). */
    static final double DEATH_NEWS_KM = 1500.0d;
    /** People from within this distance are talked about first (step 25); others only when there is nobody nearer. */
    static final double TALKED_ABOUT_KM = 600.0d;

    /** The people within {@code km} of the point; when there are none, everyone if {@code orAll}, else nobody. */
    static List<FamousPeople.Person> near(List<FamousPeople.Person> people, double lat, double lon, double km,
                                          boolean orAll) {
        List<FamousPeople.Person> out = new ArrayList<>();
        for (FamousPeople.Person p : people) {
            if (HistoricalPresence.haversineKm(lat, lon, p.lat(), p.lon()) <= km) {
                out.add(p);
            }
        }
        return out.isEmpty() && orAll ? people : out;
    }

    /** Trade routes: "Merchants on the Silk Road pass through here." */
    public static String tradeLine(HistoricalPresence.Result result) {
        if (result == null) {
            return "";
        }
        var route = TradeRoutes.get().routeNear(result.lat(), result.lon(), result.year());
        if (route.isPresent()) {
            TradeRoutes.Route r = route.get().route();
            String name = r.name();
            if ("league".equals(r.kind())) {
                // Step 25: the route's towns include the League's trading posts (London's Steelyard, Bruges, Bergen,
                // Novgorod) and ports where its merchants traded, not only member towns (Wikipedia: Hanseatic League).
                return "Merchants of the " + name + " trade here.";
            }
            if ("sea".equals(r.kind())) {
                return "Ships of the " + name + " call here.";
            }
            if (name.toLowerCase(java.util.Locale.ROOT).endsWith(" trade")) {
                return "Caravans of the " + name + " pass through here.";
            }
            return "Merchants on the " + name + " pass through here.";
        }
        return "";
    }

    /** Roads: "A Roman road runs through here, from Tingi to Tamuda." */
    public static String roadLine(HistoricalPresence.Result result) {
        if (result == null) {
            return "";
        }
        var road = TradeRoutes.get().roadNear(result.lat(), result.lon(), result.year());
        if (road.isPresent()) {
            TradeRoutes.RoadHit h = road.get();
            String what = h.inUse() ? (h.road().main() ? "A Roman road" : "A Roman side road") : "An old Roman road";
            String between = h.between();
            return what + " runs through here" + (between.isEmpty() ? "" : ", " + between) + ".";
        }
        return "";
    }

    /**
     * The year towns here live from mills and offices (step 25): 1850 in Europe and North America, where factories
     * spread from Britain after 1800; 1900 elsewhere (India's first mills opened in Bombay in 1854 and Calcutta in
     * 1855, Japan's after 1870), so Hyderabad in 1850 still lives by crafts and trade.
     */
    static int industrialFrom(double lat, double lon) {
        boolean europe = lat > 35.0d && lat < 72.0d && lon > -11.0d && lon < 40.0d;
        boolean northAmerica = lat > 25.0d && lat < 60.0d && lon > -130.0d && lon < -50.0d;
        return europe || northAmerica ? 1850 : 1900;
    }

    /**
     * How most people here make a living, as a predicate ("are farmers living in villages"): the
     * place's rank for towns, else an estimate from the population model (HYDE density) and the era.
     */
    public static String wayOfLife(HistoricalPresence.Result result) {
        if (result == null) {
            return "";
        }
        int year = result.year();
        double lat = result.lat();
        double lon = result.lon();
        if (isTown(result)) {
            return year >= industrialFrom(lat, lon) ? "work in workshops, mills, shops and offices"
                    : "live by crafts and trade, fed by the farms around the town";
        }
        var reading = result.people();
        if (reading.isEmpty() || !reading.get().present()) {
            return "";
        }
        double d = reading.get().density();
        boolean australia = lon > 112.0d && lon < 155.0d && lat < -10.0d && lat > -45.0d;
        boolean oldWorld = lon > -30.0d && !australia && !(lon > 165.0d || (lon > 130.0d && lat < 0.0d));
        if (australia && year < 1788) {
            return "live in small bands that hunt, fish and gather";
        }
        if (year >= 1900 && d >= 150.0d) {
            return "work in factories, shops and offices, or farm the land nearby";
        }
        if (d >= 20.0d) {
            return year >= 1900 ? "farm the land, with markets and small towns nearby"
                    : "are farmers living in villages and working the fields";
        }
        if (d >= 2.0d) {
            return oldWorld ? "farm small fields and keep herds of animals" : "farm small fields, and hunt and fish";
        }
        if (d >= 0.2d) {
            return oldWorld ? "are herders who move with their animals, with a few hunters and farmers"
                    : "hunt, fish and gather, with a little farming";
        }
        return "live in small bands that hunt, fish and gather";
    }

    private static boolean isTown(HistoricalPresence.Result result) {
        return result.site().isPresent() && result.occupied()
                && (result.rank() == SettlementRank.CITY || result.rank() == SettlementRank.TOWN);
    }

    /**
     * The way-of-life sentence: a town's own trades; else the ArchaeoGLOBE expert survey for the
     * region and era (8050 BC to AD 1850); else the population-model estimate.
     */
    public static String wayOfLifeLine(HistoricalPresence.Result result) {
        if (result == null) {
            return "";
        }
        if (!isTown(result)) {
            var survey = WaysOfLife.get().at(result.lat(), result.lon(), result.year());
            if (survey.isPresent()) {
                String s = survey.get().sentence();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        String w = wayOfLife(result);
        return w.isEmpty() ? "" : "Most people around here " + w + ".";
    }

    /** Where the way-of-life sentence comes from, for /ee who. */
    public static String wayOfLifeSource(HistoricalPresence.Result result) {
        if (result == null) {
            return "";
        }
        if (isTown(result)) {
            return "the place's rank";
        }
        return WaysOfLife.get().at(result.lat(), result.lon(), result.year())
                .filter(r -> !r.sentence().isEmpty())
                .map(r -> "ArchaeoGLOBE expert survey, region " + r.region() + ", survey year " + r.sliceYear())
                .orElse("estimated from the population model, not a record");
    }

    /** One line of town talk, picked by the seed among those the data can support. */
    public static String townTalk(HistoricalPresence.Result result, long seed) {
        List<String> lines = new ArrayList<>(6);
        for (String line : new String[] {rulerLine(result), cultureLine(result),
                famousLine(result, seed >>> 3), wayOfLifeLine(result), tradeLine(result), roadLine(result)}) {
            if (line != null && !line.isBlank()) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? "" : lines.get(Math.floorMod(seed, lines.size()));
    }
}

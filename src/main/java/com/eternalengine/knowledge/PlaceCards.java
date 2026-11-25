package com.eternalengine.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Step 27 (systems C10, C12, C22): knowledge cards for every place, in the mod's own words.
 *
 * <p>Who is talked about here, and which groups people know (companies, schools, papers, parties, armies, societies,
 * orders), come from {@link CardStore}. Each person and group has a card of its own: who or what it is, what the person
 * has done, where they come from (with the distance, and a place the compass can point to), and links to the cards of
 * the groups and people named. Every answer is for the date of the talk: nobody is talked about before people have
 * heard of them, an office or a work counts only from its year, a line that names an office waits for that office
 * ("president of the United States" is said of Kennedy from 1961), and a death is told only from the year it happened.
 *
 * <p>Ids: {@code pc_<qid>} for a person, {@code oc_<qid>} for a group (the Wikidata number; stable, so notes in the
 * history book join up). No source names appear anywhere (turn 56).
 */
public final class PlaceCards {

    public static final String PERSON_ID = "pc_";
    public static final String GROUP_ID = "oc_";
    /** Step 28: an event card (cards/events.tiles). */
    public static final String EVENT_ID = "ec_";
    /** Step 28: past events told at a place, within this distance. */
    static final double PAST_KM = 25.0d;
    static final int MAX_PAST = 4;
    /** People from within this distance are "from here". */
    static final double FROM_HERE_KM = 60.0d;
    /** People talked about: alive and within this distance. */
    static final double TALKED_ABOUT_KM = 300.0d;
    /** With fewer than two people talked about nearby, the very famous of a wider land are talked about too. */
    static final double FAMOUS_FAR_KM = 1000.0d;
    static final int FAMOUS_FAR_FAME = 50;
    /** People who lived long ago, when nobody alive is talked about. */
    static final double LONG_AGO_KM = 300.0d;
    static final double GROUPS_KM = 40.0d;
    static final double GROUPS_FAR_KM = 200.0d;
    static final int MAX_PEOPLE = 4;
    static final int MAX_GROUPS = 4;
    static final int MAX_DEEDS = 5;

    /** Who and what is talked about at a place in a year. */
    public record Lists(List<CardStore.Card> people, boolean longAgo, List<CardStore.Card> groups, boolean groupsFar) {
        public static final Lists EMPTY = new Lists(List.of(), false, List.of(), false);
    }

    private PlaceCards() {
    }

    public static boolean isStoreId(String id) {
        return id != null && (id.startsWith(PERSON_ID) || id.startsWith(GROUP_ID) || id.startsWith(EVENT_ID));
    }

    public static String id(CardStore.Card c) {
        return (c.isEvent() ? EVENT_ID : c.person() ? PERSON_ID : GROUP_ID) + c.qid();
    }

    /** The card for a stored id, worded for {@code date} as seen from a spot (NaN: no distances), or null. */
    public static KnowledgeCard resolve(String id, int date, double atLat, double atLon) {
        if (!isStoreId(id)) {
            return null;
        }
        int qid;
        try {
            qid = Integer.parseInt(id.substring(3));
        } catch (NumberFormatException e) {
            return null;
        }
        if (id.startsWith(EVENT_ID)) {
            CardStore.Card e = CardStore.events().byQid(qid);
            return e == null || !e.isEvent() ? null : event(e, date, atLat, atLon);
        }
        CardStore.Card c = CardStore.get().byQid(qid);
        if (c == null || c.person() != id.startsWith(PERSON_ID)) {
            return null;
        }
        return card(c, date, atLat, atLon);
    }

    // -------------------------------------------------------------------------------------------- the place's lists

    /** The people talked about and the groups known at a spot in a year (empty lists when there are none). */
    public static Lists lists(double lat, double lon, int year) {
        CardStore store = CardStore.get();
        if (!store.loaded() || Double.isNaN(lat) || Double.isNaN(lon)) {
            return Lists.EMPTY;
        }
        int now = Math.floorDiv(year, 100);
        // Alive people (or dead in the last two years) from the last 120 years of buckets, best known and nearest first.
        List<CardStore.Card> people = new ArrayList<>();
        List<double[]> scores = new ArrayList<>();
        for (CardStore.Card c : store.near(lat, lon, TALKED_ABOUT_KM, Math.floorDiv(year - 120, 100), now)) {
            if (!c.person() || !c.knownIn(year)) {
                continue;
            }
            boolean recent = c.to() != CardStore.UNKNOWN && year >= c.to() && year - c.to() <= 2;
            if (!c.aliveIn(year) && !recent) {
                continue;
            }
            people.add(c);
        }
        people.sort(byScore(lat, lon));
        if (people.size() < 2) {
            // A quiet land: its people talk about the very famous of the wider land (a king, a poet, a pope).
            List<CardStore.Far> far = new ArrayList<>();
            for (CardStore.Far f : store.famousNear(lat, lon, FAMOUS_FAR_KM, Math.floorDiv(year - 120, 100), now)) {
                if (f.fame() >= FAMOUS_FAR_FAME && f.known() <= year && f.aliveIn(year)) {
                    far.add(f);
                }
            }
            far.sort(Comparator.comparingInt((CardStore.Far f) -> -f.fame()).thenComparingInt(CardStore.Far::qid));
            for (CardStore.Far f : far) {
                if (people.size() >= MAX_PEOPLE) {
                    break;
                }
                boolean have = false;
                for (CardStore.Card c : people) {
                    have |= c.qid() == f.qid();
                }
                if (!have) {
                    people.add(sketch(f)); // the name is enough for the list; the whole card opens when asked about
                }
            }
        }
        boolean longAgo = false;
        if (people.isEmpty()) {
            for (CardStore.Card c : store.near(lat, lon, LONG_AGO_KM, Integer.MIN_VALUE, now)) {
                if (c.person() && c.knownIn(year) && !c.aliveIn(year) && (c.to() == CardStore.UNKNOWN || c.to() <= year)
                        && (c.from() == CardStore.UNKNOWN || c.from() <= year)) {
                    people.add(c);
                }
            }
            people.sort(byScore(lat, lon));
            longAgo = !people.isEmpty();
        }
        // Step 41 (D11): the people who were HERE, whether or not they were born here. Everything
        // above is placed by birthplace, which is SYSTEMS_PLAN line 507: someone born in Bombay who
        // lived in England is talked about in Bombay and never in Cambridge. The presence register
        // knows where a person actually was and why, so the two lists are joined here - the people
        // who come from here first, then the people who came.
        if (people.size() < MAX_PEOPLE) {
            for (com.eternalengine.civilization.Presence.Stay s : com.eternalengine.civilization.Presence.get()
                    .hereIn(lat, lon, year, TALKED_ABOUT_KM)) {
                if (people.size() >= MAX_PEOPLE) {
                    break;
                }
                if (s.why() == com.eternalengine.civilization.Presence.BORN
                        || s.why() == com.eternalengine.civilization.Presence.DIED) {
                    continue;                       // already in the list, or already said in the card
                }
                boolean have = false;
                for (CardStore.Card c : people) {
                    have |= c.qid() == s.qid();
                }
                if (have) {
                    continue;
                }
                CardStore.Card c = store.byQid(s.qid());
                if (c != null && c.person() && c.knownIn(year)) {
                    people.add(c);
                }
            }
        }
        if (people.size() > MAX_PEOPLE) {
            people = new ArrayList<>(people.subList(0, MAX_PEOPLE));
        }
        List<CardStore.Card> groups = groupsNear(store, lat, lon, year, GROUPS_KM);
        boolean far = false;
        if (groups.isEmpty()) {
            groups = groupsNear(store, lat, lon, year, GROUPS_FAR_KM);
            far = !groups.isEmpty();
        }
        return new Lists(List.copyOf(people), longAgo, List.copyOf(groups), far);
    }

    /** A person known far and wide, as much as a list needs (the card itself comes from {@link #resolve}). */
    static CardStore.Card sketch(CardStore.Far f) {
        return new CardStore.Card(f.qid(), CardStore.PERSON, f.fame(), f.name(), "", CardStore.UNKNOWN, "", f.from(), f.to(),
                f.known(), 0, f.lat(), f.lon(), "", Double.NaN, Double.NaN, "", List.of(), List.of(), List.of(),
                CardStore.EventFacts.NONE);
    }

    private static List<CardStore.Card> groupsNear(CardStore store, double lat, double lon, int year, double km) {
        List<CardStore.Card> out = new ArrayList<>();
        for (CardStore.Card c : store.near(lat, lon, km, Integer.MIN_VALUE, Math.floorDiv(year, 100))) {
            if (!c.person() && c.knownIn(year) && c.existsIn(year)) {
                out.add(c);
            }
        }
        out.sort(byScore(lat, lon));
        return out.size() > MAX_GROUPS ? new ArrayList<>(out.subList(0, MAX_GROUPS)) : out;
    }

    /** Best known first; people from close by count three times, from the region one and a half times. */
    private static Comparator<CardStore.Card> byScore(double lat, double lon) {
        return Comparator.comparingDouble((CardStore.Card c) -> {
            double km = KnowledgePacks.Geo.km(lat, lon, c.lat(), c.lon());
            double w = km <= FROM_HERE_KM ? 3.0d : km <= 150.0d ? 1.5d : 1.0d;
            return -c.fame() * w;
        }).thenComparingInt(CardStore.Card::qid);
    }

    /** "People here talk about [[pc_307|Galileo Galilei]] and ..." or "" when nobody is known. */
    public static String peopleText(Lists l) {
        if (l.people().isEmpty()) {
            return "";
        }
        String names = join(l.people());
        if (l.longAgo()) {
            return "People here still tell of " + names + ", who lived long ago.";
        }
        return "People here talk about " + names + ".";
    }

    /** "People here know of [[oc_131262|University of Bologna]] and ..." or "". */
    public static String groupsText(Lists l) {
        if (l.groups().isEmpty()) {
            return "";
        }
        String names = join(l.groups());
        return l.groupsFar() ? "Not in this town, but not far away: " + names + "." : "People here know of " + names + ".";
    }

    /** The ids named in the lists (so a typed name can find them). */
    public static List<String> ids(Lists l) {
        List<String> out = new ArrayList<>();
        for (CardStore.Card c : l.people()) {
            out.add(id(c));
        }
        for (CardStore.Card c : l.groups()) {
            out.add(id(c));
        }
        return out;
    }

    private static String join(List<CardStore.Card> cards) {
        List<String> parts = new ArrayList<>();
        for (CardStore.Card c : cards) {
            parts.add(link(c));
        }
        return and(parts);
    }

    static String link(CardStore.Card c) {
        String l = "[[" + id(c) + "|" + shown(c.name()) + "]]";
        return c.isEvent() && takesThe(c.name()) ? "the " + l : l;
    }

    /** Step 28: event names take "the" in a sentence ("the Battle of Verdun", "the Russian Revolution"), but not
     * "World War I", "Operation Polo", "Hurricane Katrina" or a name that has its own article. */
    static boolean takesThe(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return false;
        }
        for (String w : new String[]{"The ", "A ", "An ", "World War", "Operation ", "Hurricane ", "Typhoon ", "Cyclone ",
                "Tropical Storm ", "Super Typhoon ", "Storm ", "Project ", "Expo "}) {
            if (n.startsWith(w)) {
                return false;
            }
        }
        return true;
    }

    /** The first letter of a sentence made upper case ("the Republic of Venice ..." -> "The Republic ..."). */
    static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A name as shown inside a link: no brackets or bars, which would break the link. */
    static String shown(String name) {
        String s = name == null ? "" : name.replace('[', '(').replace(']', ')').replace('|', '/').trim();
        return s.isEmpty() ? "someone" : s;
    }

    static String and(List<String> parts) {
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    /**
     * The card of a ruler named in "who rules here?" ("Aurangzeb"), or null: a person of that name with an office held
     * in that year, or else alive and known then; the best known if there are several.
     */
    public static CardStore.Card ruler(String name, int year) {
        CardStore.Card best = null;
        int bestScore = Integer.MIN_VALUE;
        for (CardStore.Card c : CardStore.get().byName(name)) {
            if (!c.person() || (c.from() != CardStore.UNKNOWN && c.from() > year)) {
                continue;
            }
            boolean inOffice = false;
            for (CardStore.Office o : c.offices()) {
                if (o.from() != CardStore.UNKNOWN && o.from() <= year + 1 && (o.to() == CardStore.UNKNOWN || o.to() >= year)) {
                    inOffice = true;
                }
            }
            boolean alive = c.aliveIn(year) || (c.to() != CardStore.UNKNOWN && c.to() == year);
            if (!inOffice && !alive) {
                continue;
            }
            int score = (inOffice ? 1_000_000 : 0) + c.fame();
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    // -------------------------------------------------------------------------------------------- one card

    /** The card of a person or a group, worded for {@code date}, distances from the spot (NaN: none). */
    public static KnowledgeCard card(CardStore.Card c, int date, double atLat, double atLon) {
        return c.isEvent() ? event(c, date, atLat, atLon) : c.person() ? person(c, date, atLat, atLon)
                : group(c, date, atLat, atLon);
    }

    /** He, she or they, with the verbs that go with them. */
    record Pronoun(String subj, String is, String was, String has, String comes) {
        static Pronoun of(CardStore.Card c) {
            if (c.female()) {
                return new Pronoun("she", "is", "was", "has", "comes");
            }
            if (c.male()) {
                return new Pronoun("he", "is", "was", "has", "comes");
            }
            return new Pronoun("they", "are", "were", "have", "come");
        }

        String cap() {
            return Character.toUpperCase(subj.charAt(0)) + subj.substring(1);
        }
    }

    static KnowledgeCard person(CardStore.Card c, int date, double atLat, double atLon) {
        int year = KnowledgeDate.year(date);
        boolean alive = c.aliveIn(year);
        Pronoun p = Pronoun.of(c);
        String name = shown(c.name());
        List<KnowledgeCard.Question> qs = new ArrayList<>();
        Set<String> links = new LinkedHashSet<>();

        // Who
        StringBuilder who = new StringBuilder();
        String line = lineFor(c, year);
        String home = c.home() == null ? "" : c.home().trim();
        boolean homeSaid = false;
        if (!line.isBlank()) {
            String art = alive && holdsNow(c, line, year) ? "the " : article(line);
            who.append(name).append(alive ? " is " : " was ").append(art).append(line).append('.');
        } else if (!home.isBlank()) {
            who.append(name).append(alive ? " is from " : " was from ").append(home).append('.');
            homeSaid = true;
        } else {
            who.append(name).append(alive ? " is someone people here talk about." : " was someone people talked about.");
        }
        String bornIn = homeSaid || home.isBlank() ? "" : " in " + home;
        if (c.from() != CardStore.UNKNOWN && c.from() <= year) {
            who.append(' ').append(p.cap()).append(' ').append(p.subj().equals("they") ? "were" : "was").append(" born")
                    .append(bornIn).append(" in ").append(yearWord(c.from(), c.fromAround()));
            if (alive) {
                int age = span(c.from(), year);
                if (!c.fromAround() && age >= 1 && age <= 105) {
                    who.append(", so ").append(p.subj()).append(' ').append(p.is()).append(" about ").append(age)
                            .append(" now");
                }
            } else if (c.to() != CardStore.UNKNOWN && c.to() <= year) {
                who.append(" and died");
                if (c.deathPlace() != null && !c.deathPlace().isBlank()) {
                    who.append(" in ").append(c.deathPlace());
                }
                who.append(" in ").append(yearWord(c.to(), c.toAround())).append(ago(span(c.to(), year)));
            }
            who.append('.');
        } else if (!alive && c.to() != CardStore.UNKNOWN && c.to() <= year) {
            who.append(' ').append(p.cap()).append(" died in ").append(yearWord(c.to(), c.toAround()))
                    .append(ago(span(c.to(), year))).append('.');
        }
        add(qs, "who", alive ? "Who " + p.is() + " " + p.subj() + "?" : "Who " + p.was() + " " + p.subj() + "?",
                who.toString());

        // What they have done, by this year
        List<String> deeds = deeds(c, year, alive, p, links);
        if (!deeds.isEmpty()) {
            add(qs, "deeds", alive ? "What " + p.has() + " " + p.subj() + " done?" : "What did " + p.subj() + " do?",
                    String.join(" ", deeds));
        }

        // Where from
        String phrase = Double.isNaN(atLat) || Double.isNaN(atLon) ? ""
                : KnowledgePacks.Geo.phrase(atLat, atLon, c.lat(), c.lon());
        StringBuilder where = new StringBuilder(p.cap()).append(' ').append(alive ? p.comes() : "came").append(" from ");
        if (!home.isBlank()) {
            where.append(home).append(phrase.isEmpty() ? "" : ", " + phrase);
        } else {
            where.append(phrase.isEmpty() ? "a place I could not name" : "a place " + phrase);
        }
        where.append('.');
        if (!alive && c.to() != CardStore.UNKNOWN && c.to() <= year && c.hasDeathPlace() && c.deathPlace() != null
                && !c.deathPlace().isBlank() && KnowledgePacks.Geo.km(c.lat(), c.lon(), c.deathLat(), c.deathLon()) > 50.0d) {
            where.append(' ').append(p.cap()).append(" died in ").append(c.deathPlace()).append('.');
        }
        add(qs, "where", alive ? "Where " + p.is() + " " + p.subj() + " from?" : "Where " + p.was() + " " + p.subj()
                + " from?", where.toString());

        // Step 41 (C11): his family, from blood. Until now the only family in the mod was the card
        // links of a prose family - whoever happened to be named on the card - and the succession
        // engine generated heirs with no parents at all. 48,075 people with a card have one.
        com.eternalengine.civilization.Kinship kin = com.eternalengine.civilization.Kinship.get();
        StringBuilder fam = new StringBuilder();
        List<com.eternalengine.civilization.Kinship.Kinsman> parents = kin.parentsOf(c.qid());
        List<com.eternalengine.civilization.Kinship.Marriage> wed = kin.spousesOf(c.qid());
        List<com.eternalengine.civilization.Kinship.Kinsman> kids = kin.childrenOf(c.qid());
        if (!parents.isEmpty()) {
            fam.append(p.cap()).append(alive ? " is" : " was").append(" the child of ")
                    .append(kinNames(parents)).append('.');
        }
        if (!wed.isEmpty()) {
            com.eternalengine.civilization.Kinship.Marriage m = wed.get(0);
            if (fam.length() > 0) {
                fam.append(' ');
            }
            fam.append(p.cap()).append(alive ? " is" : " was").append(" married to ")
                    .append(kinName(m.spouse(), year));
            String wedYears = wedYears(m, year);
            if (!wedYears.isEmpty()) {
                fam.append(", ").append(wedYears);
            }
            fam.append('.');
            if (wed.size() > 1) {
                fam.append(' ').append(p.cap()).append(" married ").append(wed.size() - 1)
                        .append(wed.size() == 2 ? " other time" : " other times").append('.');
            }
        }
        if (!kids.isEmpty()) {
            if (fam.length() > 0) {
                fam.append(' ');
            }
            fam.append(p.cap()).append(alive ? " has " : " had ").append(kids.size())
                    .append(kids.size() == 1 ? " child: " : " children: ").append(kinNames(kids))
                    .append('.');
        }
        com.eternalengine.civilization.Kinship.House h = kin.houseOf(c.qid());
        if (h != null && !h.name().isBlank()) {
            if (fam.length() > 0) {
                fam.append(' ');
            }
            fam.append(p.cap()).append(alive ? " is of " : " was of ").append(h.name()).append('.');
        }
        List<com.eternalengine.civilization.Kinship.Dynasty> dyn = kin.dynastyOf(c.qid());
        if (!dyn.isEmpty()) {
            com.eternalengine.civilization.Kinship.Dynasty d = dyn.get(0);
            if (fam.length() > 0) {
                fam.append(' ');
            }
            fam.append("The family held ").append(d.officeName()).append(" across ")
                    .append(d.generations()).append(d.generations() == 1 ? " generation" : " generations")
                    .append(d.size() > 1 ? ", " + d.size() + " of them written down" : "").append('.');
        }
        if (fam.length() > 0) {
            add(qs, "family", alive ? "Who " + p.is() + " " + p.subj() + "'s family?"
                    : "Who was " + p.subj() + "'s family?", fam.toString());
            for (com.eternalengine.civilization.Kinship.Kinsman k : parents) {
                links.add(k.named() ? id(k.qid()) : "");
            }
            for (com.eternalengine.civilization.Kinship.Marriage m : wed) {
                links.add(m.spouse().named() ? id(m.spouse().qid()) : "");
            }
            for (com.eternalengine.civilization.Kinship.Kinsman k : kids) {
                links.add(k.named() ? id(k.qid()) : "");
            }
            links.remove("");
        }

        // Step 41 (D11): why he is HERE. Everything above is about where he comes from, which is
        // the only question the cards can answer by themselves - a card places a person at his
        // birthplace for the whole of his life. This one needs the presence register, and it is
        // left out when the register knows no reason for him to be here beyond being born here.
        if (!Double.isNaN(atLat) && !Double.isNaN(atLon)) {
            com.eternalengine.civilization.Presence.Stay stay =
                    com.eternalengine.civilization.Presence.get().stayNear(c.qid(), atLat, atLon, year);
            if (stay != null && stay.why() != com.eternalengine.civilization.Presence.BORN) {
                add(qs, "here", alive ? "Why " + p.is() + " " + p.subj() + " here?"
                        : "Why was " + p.subj() + " here?", stay.sentence(p.cap(), year));
            }
        }

        return new KnowledgeCard(id(c), name, name, "Person", 1, knownDate(c), c.lat(), c.lon(), qs, List.copyOf(links),
                List.of(name), List.of(), false);
    }

    /** Step 41 (C11): the id of a relative named by the kinship register, so the page can link to him. */
    static String id(int qid) {
        return PERSON_ID + qid;
    }

    /** Step 41 (C11): "Humayun and Hamida Banu Begum", "Jahangir I, Prince Daniyal and Murad Mirza". */
    static String kinNames(List<com.eternalengine.civilization.Kinship.Kinsman> people) {
        List<String> names = new ArrayList<>();
        for (com.eternalengine.civilization.Kinship.Kinsman k : people) {
            names.add(k.named() ? k.name() : "someone the record does not name");
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and "
                + names.get(names.size() - 1);
    }

    static List<String> deeds(CardStore.Card c, int year, boolean alive, Pronoun p, Set<String> links) {
        List<String> out = new ArrayList<>();
        int age = c.from() == CardStore.UNKNOWN ? 40 : span(c.from(), year);
        for (CardStore.Office o : c.offices()) {
            if (o.name() == null || o.name().isBlank()) {
                continue;
            }
            String office = officeWord(o.name());
            if (o.from() != CardStore.UNKNOWN && o.from() <= year) {
                boolean ongoing = alive && (o.to() == CardStore.UNKNOWN || o.to() > year);
                if (ongoing) {
                    out.add(p.cap() + " " + p.has() + " been " + office + " since " + yearText(o.from()) + ".");
                } else if (o.to() != CardStore.UNKNOWN && o.to() <= year) {
                    out.add(p.cap() + " " + p.was() + " " + office + " from " + yearText(o.from()) + " to " + yearText(o.to())
                            + ".");
                } else if (!alive) {
                    out.add(p.cap() + " " + p.was() + " " + office + " from " + yearText(o.from()) + ".");
                }
            } else if (o.from() == CardStore.UNKNOWN && !alive && c.to() != CardStore.UNKNOWN && c.to() <= year) {
                out.add(p.cap() + " " + p.was() + " " + office + ".");
            }
        }
        List<String> works = new ArrayList<>();
        for (CardStore.Work w : c.works()) {
            if (w.year() != CardStore.UNKNOWN && w.year() <= year && w.title() != null && !w.title().isBlank()) {
                works.add(w.title() + " (" + yearText(w.year()) + ")");
            }
        }
        if (!works.isEmpty()) {
            out.add(p.cap() + " " + p.is() + " known for " + and(works) + ".");
        }
        CardStore store = CardStore.get();
        for (CardStore.Link l : c.links()) {
            if (out.size() >= MAX_DEEDS) {
                break;
            }
            CardStore.Card g = store.byQid(l.qid());
            if (g == null || g.person() || !g.knownIn(year) || (g.from() != CardStore.UNKNOWN && g.from() > year)) {
                continue;
            }
            boolean dated = l.from() != CardStore.UNKNOWN;
            String s = switch (l.role()) {
                case CardStore.FOUNDED -> {
                    int when = dated ? l.from() : g.from();
                    yield when != CardStore.UNKNOWN && when <= year ? p.cap() + " founded " + link(g) + " in " + yearText(when) + "."
                            : null;
                }
                case CardStore.PARTY_OF -> (dated ? l.from() <= year : !alive || age >= 35)
                        ? p.cap() + (alive ? (p.subj().equals("they") ? " belong to " : " belongs to ") : " belonged to ") + link(g) + "."
                        : null;
                case CardStore.STUDIED -> (dated ? l.from() <= year : !alive || age >= 25)
                        ? p.cap() + " studied at " + link(g) + "." : null;
                case CardStore.SERVED -> (dated ? l.from() <= year : !alive || age >= 25)
                        ? p.cap() + " served in " + link(g) + "." : null;
                case CardStore.MEMBER -> (dated ? l.from() <= year : !alive)
                        ? p.cap() + " " + (alive && (l.to() == CardStore.UNKNOWN || l.to() > year) ? p.is() : p.was())
                        + " a member of " + link(g) + "." : null;
                case CardStore.WORKED -> (dated ? l.from() <= year : !alive || age >= 40)
                        ? p.cap() + (alive && (l.to() == CardStore.UNKNOWN || l.to() > year)
                        ? (p.subj().equals("they") ? " work for " : " works for ") : " worked for ") + link(g) + "."
                        : null;
                default -> null;
            };
            if (s != null) {
                out.add(s);
                links.add(id(g));
            }
        }
        return out.size() > MAX_DEEDS ? new ArrayList<>(out.subList(0, MAX_DEEDS)) : out;
    }

    static KnowledgeCard group(CardStore.Card c, int date, double atLat, double atLon) {
        int year = KnowledgeDate.year(date);
        boolean closed = c.closedIn(year);
        String name = shown(c.name());
        List<KnowledgeCard.Question> qs = new ArrayList<>();
        Set<String> links = new LinkedHashSet<>();
        String line = lineFor(c, year);
        if (line.isBlank()) {
            line = kindWord(c.kind());
        }
        StringBuilder what = new StringBuilder(name).append(closed ? " was " : " is ").append(article(line)).append(line)
                .append('.');
        if (c.from() != CardStore.UNKNOWN && c.from() <= year) {
            what.append(" It was founded in ").append(yearWord(c.from(), c.fromAround()));
            List<String> founders = new ArrayList<>();
            CardStore store = CardStore.get();
            for (CardStore.Link l : c.links()) {
                if (l.role() != CardStore.FOUNDED_BY) {
                    continue;
                }
                CardStore.Card f = store.byQid(l.qid());
                if (f != null && f.person() && f.knownIn(year) && founders.size() < 2) {
                    founders.add(link(f));
                    links.add(id(f));
                }
            }
            if (!founders.isEmpty()) {
                what.append(" by ").append(and(founders));
            }
            what.append('.');
        }
        if (closed) {
            what.append(" It closed in ").append(yearWord(c.to(), c.toAround())).append(ago(span(c.to(), year))).append('.');
        }
        add(qs, "what", closed ? "What was it?" : "What is it?", what.toString());
        String phrase = Double.isNaN(atLat) || Double.isNaN(atLon) ? ""
                : KnowledgePacks.Geo.phrase(atLat, atLon, c.lat(), c.lon());
        String home = c.home() == null ? "" : c.home().trim();
        String where;
        if (!home.isBlank()) {
            where = (closed ? "It was in " : "It is in ") + home + (phrase.isEmpty() ? "" : ", " + phrase) + ".";
        } else {
            where = (closed ? "It was " : "It is ") + (phrase.isEmpty() ? "somewhere I could not name" : phrase) + ".";
        }
        add(qs, "where", closed ? "Where was it?" : "Where is it?", where);
        return new KnowledgeCard(id(c), name, name, "Group", 1, knownDate(c), c.lat(), c.lon(), qs, List.copyOf(links),
                List.of(name), List.of(), false);
    }

    // -------------------------------------------------------------------------------------------- events (step 28)

    /** The event card of a history data event id ("q330", "q15136651_2"), or null when it has none. */
    public static CardStore.Card eventOf(String gazetteerId) {
        if (gazetteerId == null || gazetteerId.length() < 2 || gazetteerId.charAt(0) != 'q') {
            return null;
        }
        int end = gazetteerId.indexOf('_');
        try {
            int qid = Integer.parseInt(end < 0 ? gazetteerId.substring(1) : gazetteerId.substring(1, end));
            CardStore.Card c = CardStore.events().byQid(qid);
            return c != null && c.isEvent() ? c : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The best known events that happened within 25 km of a spot and were over before this year. */
    public static List<CardStore.Card> pastEvents(double lat, double lon, int year) {
        CardStore store = CardStore.events();
        List<CardStore.Card> out = new ArrayList<>();
        if (!store.loaded() || Double.isNaN(lat) || Double.isNaN(lon)) {
            return out;
        }
        for (CardStore.Card c : store.near(lat, lon, PAST_KM, Integer.MIN_VALUE, Math.floorDiv(year, 100))) {
            if (c.isEvent() && c.to() < year && c.from() <= year) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparingInt((CardStore.Card c) -> -c.fame()).thenComparingInt(CardStore.Card::qid));
        return out.size() > MAX_PAST ? new ArrayList<>(out.subList(0, MAX_PAST)) : out;
    }

    /** "People here remember [[ec_83216|Battle of Hastings]] (1066) and ..." or "". */
    public static String pastText(List<CardStore.Card> past) {
        if (past.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (CardStore.Card c : past) {
            parts.add(link(c) + " (" + yearText(c.from()) + ")");
        }
        return "People here remember " + and(parts) + ".";
    }

    /** The cards an event links to with one role, known in this year (causes: started by then). */
    static List<CardStore.Card> linked(CardStore.Card c, int role, int year) {
        List<CardStore.Card> out = new ArrayList<>();
        CardStore events = CardStore.events();
        CardStore cards = CardStore.get();
        for (CardStore.Link l : c.links()) {
            if (l.role() != role) {
                continue;
            }
            CardStore.Card t = role == CardStore.TOOK_PART || role == CardStore.WON ? cards.byQid(l.qid()) : events.byQid(l.qid());
            if (t != null && t.knownIn(year)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Names of states and sides: "the Kingdom of France", "Venice". */
    static String side(String name) {
        String n = name.trim();
        String first = n.contains(" ") ? n.substring(0, n.indexOf(' ')) : n;
        for (String w : new String[]{"Kingdom", "Republic", "Empire", "Duchy", "Grand", "Principality", "Sultanate",
                "Caliphate", "Khanate", "Old", "Union", "Confederate", "Confederacy", "United", "County", "Holy",
                "Electorate", "Emirate", "State", "Free", "Imperial", "Commonwealth", "Polish-Lithuanian", "Crown",
                "Allies", "Axis", "Central", "British", "French", "Royal", "Soviet", "Ottoman", "Mughal", "Dutch",
                "Spanish", "Portuguese", "Roman", "Byzantine", "Teutonic", "Order", "League", "Army"}) {
            if (first.equals(w)) {
                return "the " + n;
            }
        }
        return n;
    }

    static String number(int n) {
        if (n < 1000) {
            return String.valueOf(n);
        }
        double mag = Math.pow(10, Math.floor(Math.log10(n)) - 1);
        long r = Math.round(n / mag) * (long) mag;
        if (r >= 1_000_000) {
            double m = r / 1_000_000.0d;
            return (m == Math.floor(m) ? String.valueOf((long) m) : String.format(Locale.ROOT, "%.1f", m)) + " million";
        }
        return String.format(Locale.ROOT, "%,d", r);
    }

    public static KnowledgeCard event(CardStore.Card c, int date, double atLat, double atLon) {
        int year = KnowledgeDate.year(date);
        String name = shown(c.name());
        CardStore.EventFacts f = c.event() == null ? CardStore.EventFacts.NONE : c.event();
        boolean over = c.to() < year;
        boolean placeless = !c.hasLocation();
        List<KnowledgeCard.Question> qs = new ArrayList<>();
        Set<String> links = new LinkedHashSet<>();

        // What it is, what it was part of, how it ended
        String line = lineFor(c, year);
        if (line.isBlank()) {
            line = "event";
        }
        StringBuilder what = new StringBuilder(takesThe(name) ? "The " + name : name).append(over ? " was " : " is ")
                .append(article(line)).append(line).append('.');
        for (CardStore.Card p : linked(c, CardStore.PART_OF, year)) {
            if (!line.toLowerCase(Locale.ROOT).contains(p.name().toLowerCase(Locale.ROOT))) {
                what.append(over ? " It was part of " : " It is part of ").append(link(p)).append('.');
                links.add(id(p));
            }
            break;
        }
        if (linked(c, CardStore.PART_OF, year).isEmpty() && !f.partOf().isBlank()
                && !line.toLowerCase(Locale.ROOT).contains(f.partOf().toLowerCase(Locale.ROOT))) {
            what.append(over ? " It was part of " : " It is part of ").append(f.partOf()).append('.');
        }
        if (over) {
            List<String> won = new ArrayList<>();
            for (CardStore.Card w : linked(c, CardStore.WON, year)) {
                won.add(link(w));
                links.add(id(w));
            }
            if (!won.isEmpty()) {
                what.append(' ').append(cap(and(won))).append(" won.");
            }
            if (f.deaths() != CardStore.UNKNOWN && f.deaths() > 0) {
                what.append(" About ").append(number(f.deaths())).append(f.deaths() == 1 ? " person died." : " people died.");
            }
        }
        add(qs, "what", over ? "What was it?" : "What is it?", what.toString());

        // When
        String when;
        String from = yearWord(c.from(), c.fromAround());
        if (c.from() == year && c.to() <= year) {
            when = "This year.";
        } else if (c.from() == year) {
            when = "It began this year, and it is still going on.";
        } else if (!over) {
            when = "It began in " + from + ", and it is still going on.";
        } else if (c.to() == c.from()) {
            when = "It was in " + from + ago(span(c.from(), year)) + ".";
        } else {
            when = "It lasted from " + from + " to " + yearWord(c.to(), c.toAround()) + ".";
        }
        add(qs, "when", over ? "When was it?" : "When did it begin?", when);

        // Where
        if (!placeless) {
            String phrase = Double.isNaN(atLat) || Double.isNaN(atLon) ? ""
                    : KnowledgePacks.Geo.phrase(atLat, atLon, c.lat(), c.lon());
            String home = c.home() == null ? "" : c.home().trim();
            String where;
            if (!home.isBlank()) {
                where = "At " + home + (phrase.isEmpty() ? "" : ", " + phrase) + ".";
            } else if (!phrase.isEmpty()) {
                where = "It " + (over ? "was " : "is ") + phrase + (f.country().isBlank() ? "" : ", in " + f.country()) + ".";
            } else {
                where = f.country().isBlank() ? "" : "In " + f.country() + ".";
            }
            add(qs, "where", over ? "Where was it?" : "Where is it?", where);
        } else if (!f.country().isBlank() && f.sides().split("; ").length < 3) {
            add(qs, "where", over ? "Where was it?" : "Where is it?", "In " + f.country() + ", in more than one place.");
        }

        // Who took part
        List<String> who = new ArrayList<>();
        for (CardStore.Card p : linked(c, CardStore.TOOK_PART, year)) {
            if (who.size() < 4) {
                who.add(link(p));
                links.add(id(p));
            }
        }
        if (!f.sides().isBlank()) {
            for (String sd : f.sides().split("; ")) {
                if (who.size() < 6 && !sd.isBlank()) {
                    who.add(side(sd));
                }
            }
        }
        if (!who.isEmpty()) {
            add(qs, "who", "Who took part?", cap(and(who)) + (over ? " took part." : " take part."));
        }

        // Why: causes that came first (their year by now), causes known only by name, the larger event
        List<String> why = new ArrayList<>();
        for (CardStore.Card k : linked(c, CardStore.CAUSE, year)) {
            if (k.from() <= year && k.from() <= c.to() && why.size() < 4) {
                why.add(link(k) + " (" + yearText(k.from()) + ")");
                links.add(id(k));
            }
        }
        if (!f.causes().isBlank()) {
            for (String k : f.causes().split("; ")) {
                if (why.size() < 4 && !k.isBlank()) {
                    why.add(k);
                }
            }
        }
        for (CardStore.Card a : linked(c, CardStore.AFTER, year)) {
            if (why.isEmpty() && a.from() <= year) {
                add(qs, "before", "What came before it?", "Before it came " + link(a) + " (" + yearText(a.from()) + ").");
                links.add(id(a));
            }
            break;
        }
        if (!why.isEmpty()) {
            add(qs, "why", "Why did it happen?", "It came about because of " + and(why) + ".");
        }

        // What came of it: only what had begun before this year
        List<String> came = new ArrayList<>();
        for (int role : new int[]{CardStore.EFFECT, CardStore.BEFORE}) {
            for (CardStore.Card e : linked(c, role, year)) {
                if (e.from() < year && e.from() >= c.from() && came.size() < 4 && !links.contains(id(e))) {
                    came.add(link(e) + " (" + yearText(e.from()) + ")");
                    links.add(id(e));
                }
            }
        }
        if (!came.isEmpty()) {
            add(qs, "came", "What came of it?", "After it came " + and(came) + ".");
        }

        // Its parts (a war's battles), as far as they have happened
        List<String> parts = new ArrayList<>();
        for (CardStore.Card p : linked(c, CardStore.HAS_PART, year)) {
            if (p.from() <= year && parts.size() < 4) {
                parts.add(link(p) + " (" + yearText(p.from()) + ")");
                links.add(id(p));
            }
        }
        if (!parts.isEmpty()) {
            add(qs, "parts", "What happened in it?", (over ? "Among its events were " : "Among its events so far: ")
                    + and(parts) + ".");
        }
        return new KnowledgeCard(id(c), name, name, "Event", 1, KnowledgeDate.key(c.from(), 1, 1), c.lat(), c.lon(), qs,
                List.copyOf(links), List.of(name), List.of(), false);
    }

    // -------------------------------------------------------------------------------------------- words

    /** The description that may be said in this year: the whole line once it is true, else its early part. */
    static String lineFor(CardStore.Card c, int year) {
        if (c.line() == null || c.line().isBlank()) {
            return "";
        }
        if (c.lineFrom() == CardStore.UNKNOWN || year >= c.lineFrom()) {
            return c.line();
        }
        return c.early() == null ? "" : c.early();
    }

    /** The words of offices that a line can name ("queen of ..." and the office "monarch of ..." match). */
    private static final Set<String> RULER_WORDS = Set.of("king", "queen", "emperor", "empress", "pharaoh", "monarch",
            "sultan", "sultana", "shah", "tsar", "czar", "tsarina", "caliph", "khan", "khagan", "ruler", "regent",
            "maharaja", "raja", "rajah", "emir", "amir", "nawab", "nizam", "shogun", "doge", "sovereign");

    /**
     * The line starts with an office ("president of the United States", "queen of the United Kingdom") that the person
     * holds in this year: "the president", not "a president".
     */
    static boolean holdsNow(CardStore.Card c, String line, int year) {
        String l = line.toLowerCase(Locale.ROOT);
        int of = l.indexOf(" of ");
        if (of <= 0 || of > 30 || l.startsWith("the ") || l.startsWith("a ") || l.startsWith("an ")) {
            return false;
        }
        String head = l.substring(0, of);
        String last = head.substring(head.lastIndexOf(' ') + 1);
        for (CardStore.Office o : c.offices()) {
            if (o.from() == CardStore.UNKNOWN || o.from() > year || (o.to() != CardStore.UNKNOWN && o.to() <= year)) {
                continue;
            }
            String ol = o.name().toLowerCase(Locale.ROOT);
            boolean sameWord = ol.contains(last);
            boolean bothRulers = RULER_WORDS.contains(last) && java.util.Arrays.stream(ol.split("[^a-z]+"))
                    .anyMatch(RULER_WORDS::contains);
            if (sameWord || bothRulers) {
                return true;
            }
        }
        return false;
    }

    static String kindWord(int kind) {
        return switch (kind) {
            case CardStore.COMPANY -> "company";
            case CardStore.SCHOOL -> "school";
            case CardStore.PAPER -> "newspaper";
            case CardStore.PARTY -> "political party";
            case CardStore.ARMY -> "fighting force";
            case CardStore.SOCIETY -> "society";
            case CardStore.ORDER -> "religious order";
            default -> "group";
        };
    }

    /** "a", "an", "the" or nothing before a description ("an Italian polymath", "the first ...", "one of ..."). */
    static String article(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        if (l.matches("^\\d+(st|nd|rd|th)\\b.*")) {
            return "the "; // "the 13th Roman Emperor"
        }
        if (l.isEmpty() || Character.isDigit(l.charAt(0))) {
            return "";
        }
        for (String w : new String[]{"the ", "a ", "an ", "one ", "his ", "her ", "their ", "some ", "any "}) {
            if (l.startsWith(w)) {
                return "";
            }
        }
        for (String w : new String[]{"first ", "last ", "only ", "second ", "third ", "eldest ", "youngest ", "founder ",
                "co-founder "}) {
            if (l.startsWith(w)) {
                return "the ";
            }
        }
        return vowelSound(l) ? "an " : "a ";
    }

    static boolean vowelSound(String l) {
        for (String w : new String[]{"uni", "use", "usu", "uru", "uga", "ukr", "uti", "eu", "one", "once", "ura"}) {
            if (l.startsWith(w)) {
                return false;
            }
        }
        for (String w : new String[]{"hour", "honest", "honor", "honour", "heir"}) {
            if (l.startsWith(w)) {
                return true;
            }
        }
        return "aeiou".indexOf(l.charAt(0)) >= 0;
    }

    /** "a member of the House of Commons", "President of the United States", "a professor". */
    static String officeWord(String office) {
        String o = office.trim();
        if (o.isEmpty()) {
            return o;
        }
        String l = o.toLowerCase(Locale.ROOT);
        if (l.startsWith("member ") || (Character.isLowerCase(o.charAt(0)) && !l.contains(" of ") && !l.startsWith("the "))) {
            return article(o) + o;
        }
        return o;
    }

    /**
     * A kinsman as this card may name them in this year: the name, and only the life years the card
     * can know. A marriage in 1300 does not know that the wife died in 1329 (the card was wrong:
     * "married to Gemma Donati, 1266-1329" on a card dated 1300). Years before the common era read
     * as years too: "1559-1550 BCE", not "-1550--1559".
     */
    static String kinName(com.eternalengine.civilization.Kinship.Kinsman k, int year) {
        if (!k.named()) {
            return "someone the record does not name";
        }
        boolean born = k.birth() != com.eternalengine.civilization.Kinship.UNKNOWN && k.birth() <= year;
        boolean died = k.death() != com.eternalengine.civilization.Kinship.UNKNOWN && k.death() <= year;
        if (born && died) {
            return k.name() + ", " + yearSpan(k.birth(), k.death());
        }
        if (born) {
            return k.name() + ", born " + yearText(k.birth());
        }
        if (died) {
            return k.name() + ", died " + yearText(k.death());
        }
        return k.name();
    }

    /** "1265-1321", "1559-1550 BCE", "1310 BCE-1290" - two years written the way years are written. */
    static String yearSpan(int from, int to) {
        if (from < 0 && to < 0) {
            return yearText(Math.min(from, to)).replace(" BCE", "") + "-" + yearText(Math.max(from, to));
        }
        return yearText(from) + "-" + yearText(to);
    }

    /**
     * A marriage's years as far as this card can know them: the start years always (a marriage the
     * card is reporting has begun), the end year only once it has come. Empty where the record
     * gives neither.
     */
    static String wedYears(com.eternalengine.civilization.Kinship.Marriage m, int year) {
        boolean from = m.from() != com.eternalengine.civilization.Kinship.UNKNOWN && m.from() <= year;
        boolean to = m.to() != com.eternalengine.civilization.Kinship.UNKNOWN && m.to() <= year;
        if (from && to) {
            return yearSpan(m.from(), m.to());
        }
        if (from) {
            return "from " + yearText(m.from());
        }
        if (to) {
            return "until " + yearText(m.to());
        }
        return "";
    }

    static String yearWord(int year, boolean around) {
        return (around ? "around " : "") + yearText(year);
    }

    static String yearText(int y) {
        return LocalCards.yearText(y);
    }

    static String ago(int years) {
        if (years <= 0) {
            return ", this year";
        }
        if (years == 1) {
            return ", last year";
        }
        return ", " + years + " years ago";
    }

    /** Years from one year to a later one; there is no year 0 (1 BCE is followed by AD 1). */
    static int span(int from, int to) {
        return to - from - (from < 0 && to > 0 ? 1 : 0);
    }

    static int knownDate(CardStore.Card c) {
        return c.known() == CardStore.UNKNOWN ? KnowledgeDate.ALWAYS : KnowledgeDate.key(c.known(), 1, 1);
    }

    private static void add(List<KnowledgeCard.Question> qs, String id, String label, String text) {
        if (text != null && !text.isBlank()) {
            qs.add(new KnowledgeCard.Question(id, label, 1,
                    List.of(new KnowledgeCard.Answer(KnowledgeDate.ALWAYS, KnowledgeCard.OPEN, text, null))));
        }
    }
}

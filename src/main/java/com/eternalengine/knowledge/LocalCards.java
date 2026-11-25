package com.eternalengine.knowledge;

import com.eternalengine.civilization.EventNews;
import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.civilization.HistoryGazetteer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Basic cards for any place and year, built from the history data the mod already has (the same lines
 * villagers have said since step 12): this place, the land and its ruler, this year's news, famous people.
 *
 * <p>These cards have no dates inside the year (the data is by year), so each question has one answer.
 * Ids are stable ({@code lc_place_hyderabad}), so notes from two talks about the same place join up.
 */
public final class LocalCards {

    public static final String PLACE = "lc_place_";
    public static final String STATE = "lc_state_";
    public static final String EVENT = "lc_event_";
    public static final String PEOPLE = "lc_people_";
    /** The "this year's news" card (step R2-e1). */
    public static final String NEWS = "lc_news_";

    /**
     * What the history data says here: the cards, and which card answers each everyday topic. Step 27: the ids of the
     * people and groups named here ({@code listed}: cards of {@link PlaceCards}, found through
     * {@link TalkService.Ctx#card}).
     */
    public record Local(Map<String, KnowledgeCard> cards, String newsCard, String rulerCard, String placeCard,
                        String famousCard, String greeting, List<String> listed) {
        public static final Local EMPTY = new Local(Map.of(), null, null, null, null, "", List.of());
    }

    private LocalCards() {
    }

    public static Local build(HistoricalPresence.Result r, double lat, double lon) {
        return build(r, lat, lon, false);
    }

    /**
     * @param dropThisYearsEvent inside a knowledge pack (step 18b): the history data dates events by year only, so
     *     an event that starts this year may not have happened yet on today's date ("Operation Polo" in July 1948,
     *     two months early). The pack's dated cards and news speak for that time instead. Events that started in
     *     an earlier year are still told.
     */
    public static Local build(HistoricalPresence.Result r, double lat, double lon, boolean dropThisYearsEvent) {
        if (r == null) {
            return Local.EMPTY;
        }
        Map<String, KnowledgeCard> cards = new LinkedHashMap<>();
        int always = KnowledgeDate.ALWAYS;
        // Step 27 (C10, C12, C22): the people talked about here and the groups people know, each with a card of its own.
        PlaceCards.Lists lists = PlaceCards.Lists.EMPTY;
        try {
            lists = PlaceCards.lists(lat, lon, r.year());
        } catch (Throwable ignored) {
            // No card file: the lines of the history data alone.
        }
        String placeId = null, stateId = null, eventId = null, peopleId = null;
        List<String> eventLinks = new ArrayList<>();

        String placeName = r.site().map(h -> h.site().displayName()).orElse("");
        // Step 29 (C4): the name in that year (Bombay in 1990, Edo in 1700); without a site, the town that stands here.
        String shownName = EventNews.placeName(r);
        if (shownName.isBlank() && r.site().isEmpty() && r.presentMap().isEmpty()) {
            shownName = EventNews.townHere(r).filter(EventNews.TownHere::isSite)
                    .map(t -> t.town().nameIn(r.year())).orElse("");
        }
        String placeLine = EventNews.placeLine(r);
        if (!shownName.isBlank() && shownName.equals(shownName.toLowerCase(java.util.Locale.ROOT))) {
            placeLine = placeLine.replace(shownName, nice(shownName)); // "This is hyderabad" shown as "Hyderabad"; display only
        }
        String townLine = EventNews.townLine(r); // step 29 (C4-C6): people, founding, former name, the town around
        if (!placeLine.isBlank()) {
            String title = shownName.isBlank() ? "This place" : nice(shownName);
            String key = !placeName.isBlank() ? placeName : !shownName.isBlank() ? shownName : round(lat) + "_" + round(lon);
            placeId = PLACE + slug(key);
            List<KnowledgeCard.Question> qs = new ArrayList<>();
            add(qs, "what", "What is this place?", townLine.isBlank() ? placeLine : placeLine + " " + townLine);
            add(qs, "life", "How do people live here?", EventNews.wayOfLifeLine(r));
            add(qs, "speak", "What do people speak and believe?", EventNews.cultureLine(r));
            add(qs, "trade", "What do people trade?", EventNews.tradeLine(r));
            add(qs, "roads", "Where do the roads go?", EventNews.roadLine(r));
            add(qs, "groups", "What groups are there here?", PlaceCards.groupsText(lists)); // step 27
            List<CardStore.Card> past = List.of();
            try {
                past = PlaceCards.pastEvents(lat, lon, r.year()); // step 28: what happened here before
            } catch (Throwable ignored) {
                // No event cards: no past to tell.
            }
            add(qs, "past", "What happened here before?", PlaceCards.pastText(past));
            add(qs, "province", "Which province is this?", EventNews.provinceLine(r)); // step 29 (C3)
            add(qs, "head", "Who runs this town?", EventNews.headLine(r));             // step 29 (C2)
            add(qs, "tech", "What new things are there here?", EventNews.technologyLine(r)); // step 30a (C16)
            add(qs, "read", "Can people here read and write?", EventNews.readLine(r));        // step 30b (C27, C14)
            add(qs, "measure", "How do people here measure things?", EventNews.measureLine(r)); // step 30b (C26)
            add(qs, "wealth", "Are people here rich or poor?", EventNews.wealthLine(r));       // step 30b (C17)
            add(qs, "holy", "What are the holy places and festivals here?", EventNews.holyLine(r)); // step 30b (C13)
            double pLat = r.site().map(h -> h.site().lat()).orElse(lat);
            double pLon = r.site().map(h -> h.site().lon()).orElse(lon);
            List<String> groupIds = new ArrayList<>();
            for (CardStore.Card c : lists.groups()) {
                groupIds.add(PlaceCards.id(c));
            }
            for (CardStore.Card c : past) {
                groupIds.add(PlaceCards.id(c));
            }
            cards.put(placeId, new KnowledgeCard(placeId, title, null, "Place", 1, always, pLat, pLon,
                    qs, List.copyOf(groupIds), placeName.isBlank() ? List.of() : List.of(placeName), List.of(), false));
        }

        String rulerLine = EventNews.rulerLine(r);
        if (!rulerLine.isBlank() && r.ruler().isPresent()) {
            String state = r.ruler().get().ruler();
            stateId = STATE + slug(state);
            // Step 27: the ruler's name becomes a link to the ruler's own card, when the cards know that person.
            List<String> rulerIds = new ArrayList<>();
            try {
                String head = EventNews.rulerName(r).orElse(null);
                CardStore.Card who = head == null ? null : PlaceCards.ruler(head, r.year());
                int at = head == null ? -1 : rulerLine.indexOf(head);
                if (who != null && at >= 0) {
                    String id = PlaceCards.id(who);
                    rulerLine = rulerLine.substring(0, at) + "[[" + id + "|" + PlaceCards.shown(head) + "]]"
                            + rulerLine.substring(at + head.length());
                    rulerIds.add(id);
                }
            } catch (Throwable ignored) {
                // No card file: the plain line.
            }
            List<KnowledgeCard.Question> qs = new ArrayList<>();
            add(qs, "who", "Who rules it?", rulerLine);
            // Step 29 (C28): alliances, allegiance, vassals, shared rulers, the realm it belongs to.
            String ties = "";
            try {
                ties = com.eternalengine.civilization.StateRelations.get().line(state, r.year());
            } catch (Throwable ignored) {
                // No relations file: no ties to tell.
            }
            add(qs, "ties", "Which states is it tied to?", ties);
            cards.put(stateId, new KnowledgeCard(stateId, nice(state), null, "State", 1, always, Double.NaN, Double.NaN,
                    qs, List.copyOf(rulerIds), List.of(state), List.of(), false));
        }

        if (r.event().isPresent() && !(dropThisYearsEvent && r.event().get().event().start() >= r.year())) {
            HistoryGazetteer.Event e = r.event().get().event();
            String rumor = EventNews.rumor(e, r.year());
            if (!rumor.isBlank() && e.name() != null && !e.name().isBlank()) {
                eventId = EVENT + slug(e.id() == null || e.id().isBlank() ? e.name() : e.id());
                List<KnowledgeCard.Question> qs = new ArrayList<>();
                // Step 28: an event with a card of its own: its name opens the card, and two of its answers come along.
                CardStore.Card ec = null;
                try {
                    ec = PlaceCards.eventOf(e.id());
                } catch (Throwable ignored) {
                    // No event cards.
                }
                KnowledgeCard full = null;
                if (ec != null && ec.knownIn(r.year())) {
                    int at = rumor.indexOf(e.name());
                    if (at >= 0) {
                        rumor = rumor.substring(0, at) + "[[" + PlaceCards.id(ec) + "|" + PlaceCards.shown(e.name()) + "]]"
                                + rumor.substring(at + e.name().length());
                    }
                    full = PlaceCards.event(ec, KnowledgeDate.key(r.year(), 7, 1), lat, lon);
                    eventLinks.add(PlaceCards.id(ec));
                }
                add(qs, "what", "What is it?", rumor);
                double km = KnowledgePacks.Geo.km(lat, lon, e.lat(), e.lon());
                String where = Double.isNaN(km) ? "" : HistoryGazetteer.isRegional(e)
                        ? "All over the land, people say. Not in one town."
                        : km < 1.0d ? "Right here, in this town." : "It is " + KnowledgePacks.Geo.phrase(lat, lon, e.lat(), e.lon()) + ".";
                add(qs, "where", "Where is it?", where);
                add(qs, "when", "When was it?", whenLine(e, r.year()));
                if (full != null) {
                    for (String q : new String[]{"who", "why"}) {
                        KnowledgeCard.Question fq = full.question(q);
                        if (fq != null && !fq.answers().isEmpty()) {
                            add(qs, q, fq.label(), fq.answers().get(0).text());
                        }
                    }
                }
                cards.put(eventId, new KnowledgeCard(eventId, e.name(), null, "Event", 1, always, e.lat(), e.lon(),
                        qs, List.copyOf(eventLinks), List.of(e.name()), List.of(), false));
            }
        }

        // Step R2-e1 (2026-10-24): this year's news, from the shipped event files. Before this, the headless
        // card builder produced no news card at all - LocalCards never set it - which is why the card coverage
        // test set measured 0 of 50 towns. The data was always there: 129,232 events, and 34 of the 50 test
        // towns have one inside its own radius within two years.
        String news = "";
        try {
            // R2-7 (2026-10-27): asked by the DAY, not the year. The year-only files are no longer allowed to
            // speak about the current year at all - any day of it may still be future - so a player standing
            // in June is never told about a November battle. Dated events answer "today"; the old files answer
            // only for years that are finished.
            int todayKey = com.eternalengine.knowledge.KnowledgeDate.today();
            var event = com.eternalengine.knowledge.NewsIndex.onDay(lat, lon, todayKey);
            if (event.isPresent()) {
                var e = event.get();
                List<KnowledgeCard.Question> nq = new ArrayList<>();
                // A dated event from centuries back is the place's past, not its news (R2-7, 2026-10-27).
                add(nq, "news", e.fresh() ? "What is the news here?" : "What happened here long ago?", e.text());
                String nid = NEWS + slug(round(lat) + "_" + round(lon) + "_" + e.year());
                cards.put(nid, new KnowledgeCard(nid, e.name(), null, "Event", 1, always, e.lat(), e.lon(),
                        nq, List.of(), List.of(e.name()), List.of(), false));
                news = nid;
            }
        } catch (Throwable ignored) {
            // No event files: no news, and the greeting stands in its place.
        }

        String famous = EventNews.famousLine(r, (long) (lat * 1000) * 31 + (long) (lon * 1000) + r.year());
        String talkedAbout = PlaceCards.peopleText(lists);
        if (!talkedAbout.isBlank()) {
            // A death this year is news and comes first; the people talked about follow, each a name to ask about.
            famous = (famous.startsWith("Sad news") ? famous + " " : "") + talkedAbout;
        }
        String where = placeName.isBlank() ? round(lat) + "_" + round(lon) : placeName;
        if (!famous.isBlank()) {
            peopleId = PEOPLE + slug(where);
            List<KnowledgeCard.Question> qs = new ArrayList<>();
            add(qs, "who", "Anyone famous from here?", famous);
            List<String> ids = new ArrayList<>();
            for (CardStore.Card c : lists.people()) {
                ids.add(PlaceCards.id(c));
            }
            cards.put(peopleId, new KnowledgeCard(peopleId, "Famous people from here", null, "Group", 1, always,
                    Double.NaN, Double.NaN, qs, List.copyOf(ids), List.of("famous"), List.of(), false));
        }
        // Without news, a greeting (step 18b: inside a knowledge pack that is most days). The talk box shows the
        // date already, so not the place line's "year 1948".
        String greeting = eventId != null ? cards.get(eventId).questions().get(0).answers().get(0).text()
                : !shownName.isBlank() ? "Good day to you. This is " + nice(shownName) + "." : "";
        List<String> listed = new ArrayList<>(PlaceCards.ids(lists));
        listed.addAll(eventLinks);
        if (stateId != null && cards.get(stateId) != null) {
            listed.addAll(cards.get(stateId).links());
        }
        // The event path's news card links to the event's own card ([[ec_...]]), and a check pins that it
        // does; so it keeps priority and the year-indexed card only fills the gap where no event card exists.
        return new Local(Map.copyOf(cards), eventId != null ? eventId : (news.isBlank() ? null : news),
                stateId, placeId, peopleId,
                greeting, List.copyOf(listed));
    }

    static String whenLine(HistoryGazetteer.Event e, int year) {
        int start = e.start(), end = e.end();
        if (start == year && (end <= year + 1)) {
            return "This year.";
        }
        if (start < year && end > year) {
            return "It started in " + yearText(start) + ", and it is still going on.";
        }
        if (start < year) {
            return "That was in " + yearText(start) + ".";
        }
        return "This year.";
    }

    static String yearText(int y) {
        return y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y);
    }

    private static void add(List<KnowledgeCard.Question> qs, String id, String label, String text) {
        if (text != null && !text.isBlank()) {
            qs.add(new KnowledgeCard.Question(id, label, 1, List.of(new KnowledgeCard.Answer(KnowledgeDate.ALWAYS, KnowledgeCard.OPEN, text, null))));
        }
    }

    /** "cairo_fustat" shown as "Cairo Fustat". Display only; the data stays as it is. */
    static String nice(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (!raw.equals(raw.toLowerCase(Locale.ROOT)) || raw.contains(" ")) {
            return raw;
        }
        StringBuilder b = new StringBuilder();
        for (String part : raw.replace('_', ' ').split(" ")) {
            if (!part.isEmpty()) {
                b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return b.toString();
    }

    static String slug(String s) {
        String out = s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return out.length() > 48 ? out.substring(0, 48) : out.isEmpty() ? "x" : out;
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0d).replace('-', 'm').replace('.', 'p');
    }
}

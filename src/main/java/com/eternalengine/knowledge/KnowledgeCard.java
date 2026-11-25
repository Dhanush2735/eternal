package com.eternalengine.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One thing villagers can talk about: an event, a person, a place, a group, a family or a state.
 *
 * <p>Every answer is dated. A villager gives the last answer whose date has come, so the same question
 * gets "This morning, at four o'clock" on 13 September and "On 13 September, three days ago" on the
 * 16th, and nothing at all on the 12th. Text may hold links {@code [[cardId|shown words]]} and distances
 * {@code {d:cardId}}; the talk service turns those into clickable names and "about 240 km west of here".
 */
public final class KnowledgeCard {

    /**
     * One answer and the days it holds: from {@code from} to {@code to} (both included; {@code to} is
     * {@link #OPEN} when the answer holds until a newer one). A line that says "today", "yesterday" or "this
     * morning" holds for its own day only (step 18b: in a Terra++ world every day comes, not only the days a
     * pack was written for).
     */
    public record Answer(int from, int to, String text, String track) {
    }

    /** No end date: the line holds until a newer one starts. */
    public static final int OPEN = Integer.MAX_VALUE;

    /**
     * Words that tie a line to one day. A line with one of them must end on its own day ({@code "to"} equal to
     * {@code "from"}); {@link KnowledgePacks#validate} checks it.
     */
    public static final java.util.regex.Pattern DAY_WORDS = java.util.regex.Pattern.compile(
            "\\b(today|tonight|tomorrow|yesterday|this morning|this afternoon|this evening|last night|days ago)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    public record Question(String id, String label, int level, List<Answer> answers) {
        /**
         * The answer valid on {@code date}, or null when none has come yet or the newest one has run out (then the
         * villager is silent rather than repeating an older line that is no longer true).
         */
        public Answer at(int date) {
            Answer best = null;
            for (Answer a : answers) {
                if (a.from() <= date) {
                    best = a;
                }
            }
            return best != null && date <= best.to() ? best : null;
        }
    }

    public record Later(int at, String text) {
    }

    private final String id;
    private final String title;
    private final String shortName;
    private final String kind;
    private final int level;
    private final int from;
    private final double lat;
    private final double lon;
    private final List<Question> questions;
    private final List<String> links;
    private final List<String> aliases;
    private final List<Later> later;
    private final boolean familyTree;

    public KnowledgeCard(String id, String title, String shortName, String kind, int level, int from, double lat, double lon,
                         List<Question> questions, List<String> links, List<String> aliases, List<Later> later, boolean familyTree) {
        this.id = id;
        this.title = title;
        this.shortName = shortName == null || shortName.isBlank() ? title : shortName;
        this.kind = kind == null ? "Event" : kind;
        this.level = Math.max(1, level);
        this.from = from;
        this.lat = lat;
        this.lon = lon;
        this.questions = List.copyOf(questions);
        this.links = List.copyOf(links);
        this.aliases = List.copyOf(aliases);
        this.later = List.copyOf(later);
        this.familyTree = familyTree;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String shortName() { return shortName; }
    public String kind() { return kind; }
    /** 1: anyone in the street knows it. 2: people at court, officials, scholars. */
    public int level() { return level; }
    /** The first date villagers can know about it. */
    public int from() { return from; }
    public boolean hasLocation() { return !Double.isNaN(lat) && !Double.isNaN(lon); }
    public double lat() { return lat; }
    public double lon() { return lon; }
    public List<Question> questions() { return questions; }
    public List<String> links() { return links; }
    public List<String> aliases() { return aliases; }
    public List<Later> later() { return later; }
    public boolean familyTree() { return familyTree; }

    public boolean knownOn(int date) {
        return from <= date;
    }

    public Question question(String qid) {
        for (Question q : questions) {
            if (q.id().equals(qid)) {
                return q;
            }
        }
        return null;
    }

    /** True if the words fit this card's title, short name or one of its aliases. */
    public int matchLength(String query) {
        String s = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) {
            return 0;
        }
        int best = 0;
        List<String> names = new ArrayList<>(aliases);
        names.add(title);
        names.add(shortName);
        for (String n : names) {
            String a = n.toLowerCase(Locale.ROOT);
            if (a.isEmpty()) {
                continue;
            }
            boolean hit = s.contains(a) || (a.contains(s) && (s.length() > 2 || a.equals(s)));
            if (hit && a.length() > best) {
                best = a.length();
            }
        }
        return best;
    }

    static KnowledgeCard fromJson(JsonObject o) {
        String id = str(o, "id");
        List<Question> qs = new ArrayList<>();
        for (JsonElement qe : arr(o, "questions")) {
            JsonObject q = qe.getAsJsonObject();
            List<Answer> answers = new ArrayList<>();
            for (JsonElement ae : arr(q, "answers")) {
                JsonObject a = ae.getAsJsonObject();
                answers.add(new Answer(date(a, "from"), until(a), str(a, "text"), a.has("track") ? str(a, "track") : null));
            }
            answers.sort((x, y) -> Integer.compare(x.from(), y.from()));
            qs.add(new Question(str(q, "id"), str(q, "label"), q.has("level") ? q.get("level").getAsInt() : 1, answers));
        }
        List<Later> later = new ArrayList<>();
        for (JsonElement le : arr(o, "later")) {
            JsonObject l = le.getAsJsonObject();
            later.add(new Later(date(l, "at"), str(l, "text")));
        }
        double lat = Double.NaN, lon = Double.NaN;
        if (o.has("location") && o.get("location").isJsonObject()) {
            JsonObject loc = o.getAsJsonObject("location");
            lat = loc.get("lat").getAsDouble();
            lon = loc.get("lon").getAsDouble();
        }
        return new KnowledgeCard(id, str(o, "title"), o.has("short") ? str(o, "short") : null, str(o, "kind"),
                o.has("level") ? o.get("level").getAsInt() : 1, date(o, "from"), lat, lon, qs,
                strings(o, "links"), strings(o, "aliases"), later, o.has("familyTree") && o.get("familyTree").getAsBoolean());
    }

    static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    /** The last day a line holds ({@code "to"}), or {@link #OPEN}. */
    static int until(JsonObject o) {
        if (!o.has("to") || o.get("to").isJsonNull()) {
            return OPEN;
        }
        return KnowledgeDate.parse(o.get("to").getAsString());
    }

    static int date(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) {
            return KnowledgeDate.ALWAYS;
        }
        return KnowledgeDate.parse(o.get(k).getAsString());
    }

    static JsonArray arr(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    static List<String> strings(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr(o, k)) {
            out.add(e.getAsString());
        }
        return out;
    }
}

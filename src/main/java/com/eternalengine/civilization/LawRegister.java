package com.eternalengine.civilization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 42 (C18, law and rights by era): <b>what the law here is, and when it last changed</b>.
 *
 * <p>The pack is keyed by the <b>polity QID</b>, not by the square, and it is joined to a point
 * through {@code RulerRegister.rulerAt(lat, lon, year).wikidata()} — the state that rules here in
 * this year. That is the whole reason a country-granular source can answer a lat/lon question, and
 * it is why this register can be asked about any square of the map.
 *
 * <p>D13 (reactions to the law) and F3 (law and crime) both stand on this register, which is why it
 * exists before either of them.
 *
 * <p><b>A row nobody dated is not a row.</b> Every row in the pack carries a year the source gave,
 * and the register never fills one in. Where the pack has nothing for a polity, the register says so
 * rather than inventing a law: three of the six kinds C18 names (law codes, weapon laws, caste and
 * class rules) have no table to be read and are simply absent.
 */
public final class LawRegister {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/law.json.gz";
    public static final int UNKNOWN = Integer.MIN_VALUE;

    /** The six kinds of law C18 names, and the one step 46 added. Five are in the pack. */
    public static final int SLAVERY = 1;
    public static final int SUFFRAGE = 2;
    public static final int PUNISHMENT = 3;
    public static final int CODES = 4;
    public static final int WEAPONS = 5;
    public static final int CLASS = 6;
    /**
     * The law of the road — who may use it, and on what terms.
     *
     * <p><b>Not one of C18's six kinds; step 46 part 5 added it</b> (E10 B), because a road is not
     * a right of way and never was. 22 rows over 10 jurisdictions and 9 shapes: the cursus
     * publicus, which a man without a warrant might not ride; Rome's ban on carts in daylight; the
     * Qin axle standard; Cologne's staple right, which forbade you to pass through without
     * selling; the Rhine tolls; England's six days' statute labour; the first turnpike gate of
     * 1663; keep-left in 1835; the red flag in 1865; the Inca royal road and the Tokugawa
     * checkpoints.
     */
    public static final int ROADS = 7;

    /**
     * A dated change in the law.
     *
     * @param estimated true when the jurisdiction was matched on a loose name and not an exact one
     */
    public record Change(int kind, String kindName, int year, String text, boolean estimated) {

        /**
         * The words with one closing full stop, so a caller can drop them into a sentence of its
         * own without printing "abolished..".
         */

        /** "In 1888 slavery was abolished" — with "about" where the match was loose. */
        public String clause() {
            String t = text == null ? "" : text.trim();
            while (t.endsWith(".")) {
                t = t.substring(0, t.length() - 1);
            }
            return t + ".";
        }

        public String line() {
            return (estimated ? "In about " : "In ") + year + " " + clause();
        }
    }

    private static final LawRegister INSTANCE = new LawRegister();

    private final Map<String, List<Change>> byPolity = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final List<String> kinds = new ArrayList<>();
    private int rows;
    private boolean loaded;
    private String source = "missing";

    private LawRegister() {}

    public static LawRegister get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public String source() {
        ensureLoaded();
        return source;
    }

    public int rowCount() {
        ensureLoaded();
        return rows;
    }

    public int polityCount() {
        ensureLoaded();
        return byPolity.size();
    }

    public boolean hasData() {
        ensureLoaded();
        return rows > 0;
    }

    /** The kinds of law the pack names, for the checks. */
    public List<String> kinds() {
        ensureLoaded();
        return Collections.unmodifiableList(kinds);
    }

    /** The name the pack holds for a polity QID, or null when the pack has no such polity. */
    public String polityName(String polityQid) {
        ensureLoaded();
        return names.get(polityQid);
    }

    /** Every polity QID the pack says anything about, for the checks and for the command. */
    public java.util.Collection<String> polities() {
        ensureLoaded();
        return Collections.unmodifiableCollection(byPolity.keySet());
    }

    /**
     * The first polity whose name holds this, ignoring case; null when nothing matches.
     *
     * <p>Only for the checks and for {@code /ee}: the game always joins through
     * {@code RulerRegister} and never by name.
     */
    public String findPolity(String namePart) {
        ensureLoaded();
        if (namePart == null) {
            return null;
        }
        String want = namePart.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : names.entrySet()) {
            if (e.getValue().toLowerCase(Locale.ROOT).contains(want)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Every change in a polity's law, earliest first, or an empty list when the pack has none. */
    public List<Change> of(String polityQid) {
        ensureLoaded();
        return byPolity.getOrDefault(polityQid == null ? "" : polityQid, List.of());
    }

    /** The law of the state that rules this point in this year, earliest first. */
    public List<Change> hereAt(double lat, double lon, int year) {
        return of(com.eternalengine.civilization.WhoRuled.hit(lat, lon, year).map(h -> h.wikidata()).orElse(null));
    }

    /** The changes already in force by this year, earliest first. */
    public List<Change> inForce(String polityQid, int year) {
        List<Change> out = new ArrayList<>();
        for (Change c : of(polityQid)) {
            if (c.year() <= year) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * The last change to the law before this year, or null. This is what a person in the street
     * would call "the new law": the last thing that happened to it in living memory.
     */
    public Change latest(String polityQid, int year) {
        Change best = null;
        for (Change c : of(polityQid)) {
            if (c.year() <= year && (best == null || c.year() > best.year())) {
                best = c;
            }
        }
        return best;
    }

    /**
     * The road laws of the state that rules this point in this year, earliest first.
     *
     * <p>The answer to "may I use this road, and what does it cost" — the question E10 B exists
     * for. Empty is honest: it means no road law is recorded for this state, not that the road is
     * free.
     */
    public List<Change> roadLawsAt(double lat, double lon, int year) {
        return of(com.eternalengine.civilization.WhoRuled.hit(lat, lon, year).map(h -> h.wikidata()).orElse(null))
                .stream()
                .filter(c -> c.kind() == ROADS)
                .filter(c -> c.year() <= year)     // a law made later is not in force now
                .toList();
    }

    /**
     * Whether a road here and now is recorded as shut to ordinary travellers.
     *
     * <p>True where the last road law in force before this year says the way is closed — the
     * cursus publicus, the Inca royal road, a Tokugawa checkpoint. <b>It reads only the last row</b>,
     * so a later law that reopened the road is honoured and an earlier one is not carried forward.
     */
    public boolean roadClosedAt(double lat, double lon, int year) {
        Change last = null;
        for (Change c : roadLawsAt(lat, lon, year)) {
            if (last == null || c.year() > last.year()) last = c;
        }
        return last != null && last.text().contains("had no right of way");
    }

    /**
     * The year this polity last abolished capital punishment before this year, or null.
     *
     * <p>F3 needs it, because whether a place can still hang a man is the one question in the
     * justice pack that is <b>not</b> a reading: it is a real date in a real source. Every
     * {@link #PUNISHMENT} row in the pack is an abolition (all 121 of them, verified when the pack
     * was built), so the last such row at or before this year is the answer.
     *
     * <p><b>It cannot see a restoration.</b> The pack records abolitions and nothing else, so a
     * country that abolished, brought it back and abolished again reads as abolished from the
     * first date on. That is a limit of the source and is recorded as one.
     */
    public Integer abolitionYear(String polityQid, int year) {
        ensureLoaded();
        Integer found = null;
        for (Change c : of(polityQid)) {
            if (c.kind() == PUNISHMENT && c.year() <= year && (found == null || c.year() > found)) {
                found = c.year();
            }
        }
        return found;
    }

    /** Whether this polity had abolished capital punishment by this year. */
    public boolean abolished(String polityQid, int year) {
        return abolitionYear(polityQid, year) != null;
    }

    /** The next change to the law after this year, or null - what is coming, and nobody knows it yet. */
    public Change next(String polityQid, int year) {
        Change best = null;
        for (Change c : of(polityQid)) {
            if (c.year() > year && (best == null || c.year() < best.year())) {
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the file

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = LawRegister.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                source = "resource " + RESOURCE + " absent";
                return;
            }
            JsonObject doc;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                doc = JsonParser.parseReader(r).getAsJsonObject();
            }
            install(doc);
            source = RESOURCE;
        } catch (IOException | IllegalStateException | NullPointerException e) {
            source = "resource " + RESOURCE + " failed: " + e.getMessage();
        }
    }

    private void install(JsonObject doc) {
        Map<Integer, String> kindNames = new HashMap<>();
        for (JsonElement e : arr(doc, "kinds")) {
            JsonObject k = e.getAsJsonObject();
            kindNames.put(k.get("id").getAsInt(), k.get("name").getAsString());
            kinds.add(k.get("name").getAsString());
        }
        JsonArray polities = arr(doc, "polities");
        for (JsonElement e : arr(doc, "rows")) {
            JsonArray r = e.getAsJsonArray();
            int pi = r.get(0).getAsInt();
            if (pi < 0 || pi >= polities.size()) {
                continue;
            }
            String qid = polities.get(pi).getAsJsonArray().get(0).getAsString();
            names.putIfAbsent(qid, polities.get(pi).getAsJsonArray().get(1).getAsString());
            int kind = r.get(1).getAsInt();
            byPolity.computeIfAbsent(qid, k -> new ArrayList<>())
                    .add(new Change(kind, kindNames.getOrDefault(kind, "the law"), r.get(2).getAsInt(),
                            tidy(r.get(4).getAsString()), r.get(5).getAsInt() != 0));
            rows++;
        }
        for (List<Change> list : byPolity.values()) {
            list.sort((a, b) -> Integer.compare(a.year(), b.year()));
        }
    }

    /**
     * The source's own words with the joiner's mess taken out of them: Wikipedia writes "Slavery
     * abolished .", with a space before the full stop and sometimes a stray one at the end. This
     * changes nothing in the record and stops the register from printing "abolished ..".
     */
    private static String tidy(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+\\.", ".").trim();
        while (t.endsWith("..")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonArray() ? new JsonArray() : e.getAsJsonArray();
    }

    @Override
    public String toString() {
        ensureLoaded();
        return String.format(Locale.ROOT, "LawRegister[%d changes over %d polities, %s]",
                rows, byPolity.size(), source);
    }
}

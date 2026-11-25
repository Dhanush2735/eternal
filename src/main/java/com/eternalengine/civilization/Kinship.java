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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 41 (C11, families and dynasties): <b>who was whose parent, who married whom, and what house
 * they were of</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>C11 was <b>the book's family tree</b>, and that tree was not kinship: {@code BookService.tree}
 * lists the <i>card links</i> of a family card, which is to say the other cards Wikidata happened
 * to name on it. A player's "family" was full of people who were not related.
 * {@code DynasticSuccessionEngine} generates heirs and coups from four succession laws with no
 * bloodline to work from at all: {@code DynasticHeir} carries a claim strength, a birth year and
 * no parents.
 *
 * <h3>What this register does</h3>
 *
 * <p>It reads the kinship pack and answers five questions: {@link #parentsOf}, {@link #childrenOf},
 * {@link #spousesOf}, {@link #houseOf} and {@link #dynastyOf}.
 *
 * <p><b>Edges are stored once and answered both ways.</b> Wikidata states <i>child</i> 64,710 times
 * against <i>father</i> 27,605, so the pack holds one parent row per pair whichever way the source
 * put it, and this register reads it in whichever direction it was asked.
 *
 * <p><b>A relative with no card can still be named.</b> Only 14% of the edges have both ends on a
 * card — a famous person's father is usually not famous himself — so the pack carries the 91,168
 * relatives who have no card with their names and years. {@link Kinsman#name()} is empty when even
 * Wikidata did not name him, and the register says so rather than inventing an uncle.
 *
 * <p><b>A dynasty is derived, not asserted.</b> No source lists "the Habsburgs held the Holy Roman
 * emperorship"; the pack finds parent-and-child holders of one office and calls a connected
 * component of three or more a dynasty. That is as true of a republican <i>gens</i> — the Metelli
 * held the consulship across seven generations — as of a monarchy, and the register's wording says
 * <i>a family that held this office</i> and not <i>a hereditary throne</i>.
 */
public final class Kinship {

    public static final String RESOURCE = "/data/eternalengine/eternal/history/kinship.json.gz";
    public static final int UNKNOWN = Integer.MIN_VALUE;

    /**
     * A person named by an edge. Every one of them is either a card — and named by the cards — or a
     * relative the pack carries a name for. A handful are neither, and {@link #name()} is empty for
     * those: the register declines rather than inventing.
     */
    public record Kinsman(int qid, String name, String description, int birth, int death) {

        public boolean named() {
            return name != null && !name.isBlank();
        }

        /** "Arthur White" or, with years, "Arthur White, 1863-1929". */
        public String display() {
            if (!named()) {
                return "someone the record does not name";
            }
            if (birth == UNKNOWN && death == UNKNOWN) {
                return name;
            }
            if (death == UNKNOWN) {
                return name + ", born " + birth;
            }
            if (birth == UNKNOWN) {
                return name + ", died " + death;
            }
            return name + ", " + birth + "-" + death;
        }
    }

    /** A marriage, with the years where Wikidata gave them (51% of them). */
    public record Marriage(Kinsman spouse, int from, int to) {

        public String years() {
            if (from != UNKNOWN && to != UNKNOWN) {
                return from + "-" + to;
            }
            if (from != UNKNOWN) {
                return "from " + from;
            }
            if (to != UNKNOWN) {
                return "until " + to;
            }
            return "in a year the record does not give";
        }
    }

    /** A named house: the family item Wikidata put a person in. */
    public record House(int qid, String name, List<Integer> members) {}

    /** A family that held one office across generations: derived, not asserted. */
    public record Dynasty(String name, String officeName, int office, Integer house,
                          List<Integer> members, int from, int to, int generations) {

        public int size() {
            return members.size();
        }

        public String span() {
            if (from != UNKNOWN && to != UNKNOWN) {
                return from + "-" + to;
            }
            return "in years the record does not give";
        }
    }

    private static final Kinship INSTANCE = new Kinship();

    private static volatile NameSource nameSource;

    private Map<Integer, List<Integer>> parents = Map.of();
    private Map<Integer, List<Integer>> children = Map.of();
    private Map<Integer, List<int[]>> spouses = Map.of();
    private final Map<Integer, Kinsman> named = new HashMap<>();
    private Map<Integer, House> houseOf = new HashMap<>();
    private Map<String, House> houses = new HashMap<>();
    private List<Dynasty> dynasties = List.of();
    private List<int[]> parentEdges = List.of();
    private int count;
    private boolean loaded;
    private String source = "missing";

    private Kinship() {}

    public static Kinship get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public String source() {
        ensureLoaded();
        return source;
    }

    public int edgeCount() {
        ensureLoaded();
        return parentEdges.size();
    }

    public int marriageCount() {
        ensureLoaded();
        return spouses.size();
    }

    public int relativeCount() {
        ensureLoaded();
        return named.size();
    }

    public int houseCount() {
        ensureLoaded();
        return houses.size();
    }

    public List<Dynasty> dynasties() {
        ensureLoaded();
        return dynasties;
    }

    public boolean hasData() {
        ensureLoaded();
        return count > 0;
    }

    /** Who his parents were, as far as the record goes. Empty where it does not say. */
    public List<Kinsman> parentsOf(int qid) {
        ensureLoaded();
        return people(parents.get(qid));
    }

    /** Who his children were. */
    public List<Kinsman> childrenOf(int qid) {
        ensureLoaded();
        return people(children.get(qid));
    }

    /** Who he was married to, with the years where they are known. */
    public List<Marriage> spousesOf(int qid) {
        ensureLoaded();
        List<int[]> rows = spouses.get(qid);
        if (rows == null) {
            return List.of();
        }
        List<Marriage> out = new ArrayList<>(rows.size());
        for (int[] r : rows) {
            out.add(new Marriage(kinsman(r[0]), r[1], r[2]));
        }
        return out;
    }

    /** The house Wikidata put him in, if any. 74% of dynasties have one; 88% of people do not. */
    public House houseOf(int qid) {
        ensureLoaded();
        return houseOf.get(qid);
    }

    /** The house by its Wikidata number. */
    public House house(int qid) {
        ensureLoaded();
        return houses.get("Q" + qid);
    }

    /** The dynasties this person's house held, largest first. */
    public List<Dynasty> dynastyOf(int qid) {
        ensureLoaded();
        House h = houseOf.get(qid);
        if (h == null) {
            return List.of();
        }
        List<Dynasty> out = new ArrayList<>();
        for (Dynasty d : dynasties) {
            if (d.house() != null && d.house().intValue() == h.qid()) {
                out.add(d);
            }
        }
        return out;
    }

    /** The dynasty that held this office and had him in it, if any. */
    public List<Dynasty> dynastyHolding(int office) {
        ensureLoaded();
        List<Dynasty> out = new ArrayList<>();
        for (Dynasty d : dynasties) {
            if (d.office() == office) {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * A person named by an edge: from the pack if it carries him, because a relative with no card
     * is named there and nowhere else; otherwise from the cards, because <b>the pack does not
     * repeat the 148,510 people who already have one</b>, and a register that looked only in the
     * pack would call Elizabeth II's father "someone the record does not name".
     */
    public Kinsman kinsman(int qid) {
        ensureLoaded();
        Kinsman k = named.get(qid);
        if (k != null) {
            return k;
        }
        NameSource src = nameSource;
        if (src != null) {
            String name = src.name(qid);
            if (name != null && !name.isBlank()) {
                return new Kinsman(qid, name, "", UNKNOWN, UNKNOWN);
            }
        }
        return new Kinsman(qid, "", "", UNKNOWN, UNKNOWN);
    }

    /**
     * Where the register looks for the name of a person it does not carry itself. <b>The pack does
     * not repeat the 148,510 people who already have a card</b> — it names only the 91,168
     * relatives who have none — so without this the register would call Elizabeth II's father
     * "someone the record does not name". {@code CardStore} sets it when the cards are read.
     *
     * <p>It is a hook and not an import for two reasons: the kinship register reads a data file and
     * should not need the whole engine to answer a question about it, and the pack's own checks run
     * without the game loaded at all.
     */
    public interface NameSource {
        /** The person's name, or null when he has no card either. */
        String name(int qid);
    }

    public static void setNameSource(NameSource src) {
        nameSource = src;
    }

    private List<Kinsman> people(List<Integer> ids) {
        if (ids == null) {
            return List.of();
        }
        List<Kinsman> out = new ArrayList<>(ids.size());
        for (int q : ids) {
            out.add(kinsman(q));
        }
        return out;
    }

    // ------------------------------------------------------------------ the file

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = Kinship.class.getResourceAsStream(RESOURCE)) {
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
        for (JsonElement e : arr(doc, "named")) {
            JsonArray r = e.getAsJsonArray();
            int qid = r.get(0).getAsInt();
            named.put(qid, new Kinsman(qid, str(r, 1), str(r, 2),
                    r.get(3).isJsonNull() ? UNKNOWN : r.get(3).getAsInt(),
                    r.get(4).isJsonNull() ? UNKNOWN : r.get(4).getAsInt()));
        }
        Map<Integer, List<Integer>> par = new HashMap<>();
        Map<Integer, List<Integer>> kid = new HashMap<>();
        List<int[]> edges = new ArrayList<>();
        for (JsonElement e : arr(doc, "parent")) {
            JsonArray r = e.getAsJsonArray();
            int a = r.get(0).getAsInt();
            int b = r.get(1).getAsInt();
            par.computeIfAbsent(b, k -> new ArrayList<>()).add(a);   // a is the parent of b
            kid.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
            edges.add(new int[] {a, b});
        }
        parents = par;
        children = kid;
        parentEdges = edges;
        count = edges.size();

        Map<Integer, List<int[]>> sp = new HashMap<>();
        JsonArray keys = arr(doc, "spouse");
        JsonArray years = arr(doc, "spouseYears");
        for (int i = 0; i < keys.size() && i < years.size(); i++) {
            JsonArray pair = keys.get(i).getAsJsonArray();
            JsonArray yr = years.get(i).getAsJsonArray();
            int a = pair.get(0).getAsInt();
            int b = pair.get(1).getAsInt();
            int from = yr.get(0).isJsonNull() ? UNKNOWN : yr.get(0).getAsInt();
            int to = yr.get(1).isJsonNull() ? UNKNOWN : yr.get(1).getAsInt();
            sp.computeIfAbsent(a, k -> new ArrayList<>()).add(new int[] {b, from, to});
            sp.computeIfAbsent(b, k -> new ArrayList<>()).add(new int[] {a, from, to});
        }
        spouses = sp;

        Map<Integer, House> of = new HashMap<>();
        Map<String, House> all = new HashMap<>();
        for (JsonElement e : arr(doc, "houses")) {
            JsonArray r = e.getAsJsonArray();
            int qid = r.get(0).getAsInt();
            String name = str(r, 1);
            List<Integer> members = new ArrayList<>();
            for (JsonElement m : r.get(2).getAsJsonArray()) {
                members.add(m.getAsInt());
            }
            House h = new House(qid, name, List.copyOf(members));
            all.put("Q" + qid, h);
            for (int m : members) {
                of.putIfAbsent(m, h);
            }
        }
        houseOf = of;
        houses = all;

        List<Dynasty> dyn = new ArrayList<>();
        for (JsonElement e : arr(doc, "dynasties")) {
            JsonObject o = e.getAsJsonObject();
            List<Integer> members = new ArrayList<>();
            for (JsonElement m : o.getAsJsonArray("members")) {
                members.add(m.getAsInt());
            }
            dyn.add(new Dynasty(str(o, "name"), str(o, "officeName"), o.get("office").getAsInt(),
                    o.get("house").isJsonNull() ? null : o.get("house").getAsInt(),
                    List.copyOf(members),
                    o.get("from").isJsonNull() ? UNKNOWN : o.get("from").getAsInt(),
                    o.get("to").isJsonNull() ? UNKNOWN : o.get("to").getAsInt(),
                    o.get("generations").getAsInt()));
        }
        dynasties = List.copyOf(dyn);
    }

    /** The people in a family, nearest blood first, for the book's tree. */
    public List<Kinsman> familyOf(int qid, int limit) {
        ensureLoaded();
        Set<Integer> seen = new LinkedHashSet<>();
        List<Integer> order = new ArrayList<>();
        for (Kinsman k : parentsOf(qid)) {
            if (seen.add(k.qid())) {
                order.add(k.qid());
            }
        }
        for (Marriage m : spousesOf(qid)) {
            if (seen.add(m.spouse().qid())) {
                order.add(m.spouse().qid());
            }
        }
        for (Kinsman k : childrenOf(qid)) {
            if (seen.add(k.qid())) {
                order.add(k.qid());
            }
        }
        List<Kinsman> out = new ArrayList<>();
        for (int q : order) {
            if (out.size() >= limit) {
                break;
            }
            out.add(kinsman(q));
        }
        return out;
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonArray() ? new JsonArray() : e.getAsJsonArray();
    }

    private static String str(JsonArray r, int i) {
        return i < r.size() && !r.get(i).isJsonNull() ? r.get(i).getAsString() : "";
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Override
    public String toString() {
        ensureLoaded();
        return String.format(Locale.ROOT,
                "Kinship[%d parent edges, %d marriages, %d relatives, %d houses, %d dynasties, %s]",
                parentEdges.size(), spouses.size(), named.size(), houses.size(), dynasties.size(),
                source);
    }
}

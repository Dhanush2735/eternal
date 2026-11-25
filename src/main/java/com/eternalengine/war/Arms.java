package com.eternalengine.war;

import com.eternalengine.civilization.RulerRegister;
import com.eternalengine.money.ProductAtlas;
import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 45 part 3 (E9 B): <b>the five ways a weapon gets into a man's hand</b>, and whether he may
 * carry it once it is there.
 *
 * <p>Part A of this step answered <i>what weapons exist here</i>. This answers the question the
 * armoury cannot: <i>how did he come by that one, and what does the law here say about it?</i> It
 * is the whole of army supply in five words:
 *
 * <ol>
 *   <li><b>issued</b> — the state hands you one, and it stays theirs. You give it back.</li>
 *   <li><b>bring</b>  — you arrive already carrying the one you own.</li>
 *   <li><b>buy</b>    — you buy one, from a smith, a market or a ship.</li>
 *   <li><b>take</b>   — you take it off a beaten enemy and keep it.</li>
 *   <li><b>gift</b>   — it is given to you: by a kinsman, a lord, a patron or a will.</li>
 * </ol>
 *
 * <h2>And a sixth, which is not a way of getting one but is the law that matters most</h2>
 *
 * <p><b>carry</b> — you may go about with it.
 *
 * <p>The sixth is here because it has to be. <b>Keeping and carrying are different laws in every
 * age, and a register that merges them misreads both.</b> The Statute of Northampton 1328 never
 * touched a man's right to own a sword; it forbade him to walk into a market wearing one. The 1181
 * Assize of Arms <i>required</i> every freeman to own arms and in the same breath forbade him to
 * sell or pledge them, so {@code buy} is open and {@code gift} is shut. The Indian Arms Act 1878
 * required a licence to own a gun and exempted Europeans: {@code buy} shut for an Indian, open for
 * an Englishman, in the same place and the same year.
 *
 * <p>None of those three can be written down as "guns are legal here" or "guns are banned here",
 * which is why this register carries six flags and not one.
 *
 * <h2>Where the weapon LAWS live</h2>
 *
 * <p>Not here. They are <b>kind 5 of {@code history/law.json.gz}</b>, read by
 * {@code LawRegister}, and the two packs are meant to be read together. They are keyed differently
 * on purpose: this one by <i>area and year</i>, that one by <i>the polity that rules the square</i>,
 * because the first is a fact about warfare and the second is a fact about a state. Ask both.
 *
 * <h2>What this pack is not</h2>
 *
 * <p>It is <b>illustrative, not comprehensive</b>. There is no table of how armies were supplied to
 * be read, so every row is written by hand and names its source. A place and year with no row at
 * all falls through to the default, which is the honest answer for most of the map and most of
 * time: <i>you bring your own</i>.
 */
public final class Arms {

    public static final String RESOURCE = "/data/eternalengine/eternal/war/arms.json.gz";

    /** The five ways, in the order the pack ships them. */
    public static final int ISSUED = 0;
    public static final int BRING = 1;
    public static final int BUY = 2;
    public static final int TAKE = 3;
    public static final int GIFT = 4;
    /** Not a way of getting one: whether you may go about with it. */
    public static final int CARRY = 5;

    private static volatile Arms instance;

    /**
     * How weapons reach a hand in one area over one span of years.
     *
     * @param areas  the area codes it holds in, as indices into {@link #areas()}
     * @param shape  the shape of the law: must, free, licence, banned or class
     * @param flags  the six, 1 open and 0 shut, indexed by {@link #ISSUED}…{@link #CARRY}
     * @param polity the state this row belongs to, or null when it is a statement about a
     *               civilisation rather than about a government
     * @param forWhom who the row is about, or null when it is everybody
     */
    public record Row(String id, List<Integer> areas, int y0, int y1, String shape,
                      int[] flags, @Nullable List<String> polity, @Nullable String forWhom, String text,
                      String note, String source) {

        /** Whether this row holds in this area and year. */
        public boolean holdsIn(int areaIndex, int year) {
            return areas.contains(areaIndex) && year >= y0 && year <= y1;
        }

        /** Whether the named way is open here: 0 issued, 1 bring, 2 buy, 3 take, 4 gift, 5 carry. */
        public boolean open(int way) {
            return way >= 0 && way < flags.length && flags[way] != 0;
        }

        public boolean issued() {
            return open(ISSUED);
        }

        public boolean bring() {
            return open(BRING);
        }

        public boolean buy() {
            return open(BUY);
        }

        public boolean take() {
            return open(TAKE);
        }

        public boolean gift() {
            return open(GIFT);
        }

        public boolean carry() {
            return open(CARRY);
        }

        /**
         * Whether this row belongs to the state that rules here.
         *
         * <p>The names are fragments, because one law runs under several successive states:
         * England is the "Angevin Empire" in 1181 and the "Kingdom of England" in 1250, and Japan
         * is "Warring States Japan", then the "Tokugawa Shogunate", then the "Empire of Japan".
         */
        public boolean belongsTo(@Nullable String rulerName) {
            if (polity == null || polity.isEmpty() || rulerName == null) {
                return false;
            }
            String lower = rulerName.toLowerCase(Locale.ROOT);
            for (String p : polity) {
                if (lower.contains(p.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        /** True when the law here requires arms rather than permitting or forbidding them. */
        public boolean isADuty() {
            return "must".equals(shape);
        }

        /** True when this is the default row a place-year falls back to. */
        public boolean isDefault() {
            return "own".equals(id);
        }

        /** The ways that are open, as names, for the prose. */
        public List<String> openWays(List<String> flagNames) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < flags.length && i < flagNames.size(); i++) {
                if (flags[i] != 0) {
                    out.add(flagNames.get(i));
                }
            }
            return out;
        }
    }

    /**
     * What the answer is at a point in a year.
     *
     * @param area    the way-of-life area
     * @param row     the row in force, never null: a place-year with nothing falls to the default
     * @param ruler   the state that ruled here that year, or null when the register has none
     * @param matched false when nothing but the default applied here
     */
    public record Supply(String area, int year, Row row, @Nullable String ruler, boolean matched) {

        public boolean issued() {
            return row.issued();
        }

        public boolean bring() {
            return row.bring();
        }

        public boolean buy() {
            return row.buy();
        }

        public boolean take() {
            return row.take();
        }

        public boolean gift() {
            return row.gift();
        }

        public boolean carry() {
            return row.carry();
        }

        public boolean open(int way) {
            return row.open(way);
        }
    }

    private final List<Row> rows;
    private final List<String> areas;
    private final List<String> flagNames;
    private final List<String> flagMeanings;
    private final String problem;

    private Arms(List<Row> rows, List<String> areas, List<String> flagNames,
                 List<String> flagMeanings, String problem) {
        this.rows = rows;
        this.areas = areas;
        this.flagNames = flagNames;
        this.flagMeanings = flagMeanings;
        this.problem = problem;
    }

    // -------------------------------------------------------------------------------------------------- reading

    public static Arms get() {
        Arms a = instance;
        if (a == null) {
            synchronized (Arms.class) {
                a = instance;
                if (a == null) {
                    a = load();
                    instance = a;
                }
            }
        }
        return a;
    }

    /** Forget the pack and read it again. */
    public static Arms reload() {
        synchronized (Arms.class) {
            instance = load();
            return instance;
        }
    }

    private static Arms load() {
        List<Row> rows = new ArrayList<>();
        List<String> areas = new ArrayList<>();
        List<String> flagNames = new ArrayList<>();
        List<String> flagMeanings = new ArrayList<>();
        try (InputStream raw = Arms.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new Arms(List.of(), List.of(), List.of(), List.of(),
                        "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (JsonElement e : root.getAsJsonArray("areas")) {
                areas.add(e.getAsString());
            }
            for (JsonElement e : root.getAsJsonArray("flags")) {
                flagNames.add(e.getAsString());
            }
            for (JsonElement e : root.getAsJsonArray("flagMeaning")) {
                flagMeanings.add(e.getAsString());
            }
            List<String> shapes = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("shapes")) {
                shapes.add(e.getAsString());
            }
            for (JsonElement re : root.getAsJsonArray("rows")) {
                JsonObject o = re.getAsJsonObject();
                List<Integer> as = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("areas")) {
                    as.add(e.getAsInt());
                }
                int[] flags = new int[flagNames.size()];
                int i = 0;
                for (JsonElement e : o.getAsJsonArray("flags")) {
                    if (i < flags.length) {
                        flags[i++] = e.getAsInt();
                    }
                }
                int si = o.get("shape").getAsInt();
                rows.add(new Row(str(o, "id"), as, o.get("y0").getAsInt(), o.get("y1").getAsInt(),
                        si >= 0 && si < shapes.size() ? shapes.get(si) : "free", flags,
                        polityOf(o), nullableStr(o, "for"),
                        str(o, "text"), str(o, "note"), str(o, "source")));
            }
            return new Arms(rows, areas, flagNames, flagMeanings, null);
        } catch (Exception e) {
            return new Arms(List.of(), List.of(), List.of(), List.of(),
                    "resource " + RESOURCE + " failed: " + e);
        }
    }

    // -------------------------------------------------------------------------------------------------- asking

    /** The area codes the pack is keyed by. */
    public List<String> areas() {
        return Collections.unmodifiableList(areas);
    }

    /** The six flag names, in order: issued, bring, buy, take, gift, carry. */
    public List<String> flagNames() {
        return Collections.unmodifiableList(flagNames);
    }

    /** What each flag means, one line each, so the game can explain itself. */
    public List<String> flagMeanings() {
        return Collections.unmodifiableList(flagMeanings);
    }

    public List<Row> rows() {
        return Collections.unmodifiableList(rows);
    }

    public boolean hasData() {
        return problem == null && !rows.isEmpty();
    }

    /** Null when the pack is healthy; otherwise why not. */
    @Nullable
    public String problem() {
        return problem;
    }

    /**
     * The row in force in this area and year, or null when the pack has nothing.
     *
     * <p>The <b>last</b> matching row wins, not the first: the rows are written broadly first and
     * narrowly later, so the default ({@code own}) is row 0 and the specific law of a place and
     * reign comes after it.
     */
    @Nullable
    public Row rowIn(String area, int year) {
        return rowIn(area, year, null);
    }

    /**
     * The row in force in this area and year under the state named, or null.
     *
     * <p><b>An area is not a country, and this is the register's hardest limit.</b> {@code EAS}
     * holds both Japan and China, and in 1700 the two had opposite laws: a Japanese peasant could
     * not own a sword, a Qing subject could buy one. Both rows claim EAS 1700 and an area key
     * cannot tell them apart. So the precedence is:
     *
     * <ol>
     *   <li>rows naming the polity that rules <b>here</b>, last one written winning;</li>
     *   <li>then rows naming no polity at all, last one written winning.</li>
     * </ol>
     *
     * <p>That is why the Sword Hunt beats the Qing at Kyoto, and why the global {@code modern}
     * row — "the state issues and that is all" — does not flatten the Indian Arms Act at Delhi
     * in 1900. A row with no polity is a claim about a civilisation, not about a government, and
     * it should only be reached when nothing more specific applies.
     */
    @Nullable
    public Row rowIn(String area, int year, @Nullable String rulerName) {
        int ai = areas.indexOf(area);
        if (ai < 0) {
            return null;
        }
        Row specific = null;
        Row general = null;
        for (Row r : rows) {
            if (!r.holdsIn(ai, year)) {
                continue;
            }
            if (r.belongsTo(rulerName)) {
                specific = r;
            } else if (r.polity() == null || r.polity().isEmpty()) {
                general = r;
            }
        }
        return specific != null ? specific : general;
    }

    /**
     * How weapons reach a hand at a point in a year.
     *
     * <p>The area comes from the product atlas, the same way {@link Armoury} resolves it, so the
     * two halves of this step always agree about where you are.
     */
    public Supply hereAt(double lat, double lon, int year) {
        String area = ProductAtlas.get().place(lat, lon, year).area();
        String ruler = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year).map(h -> h.ruler()).orElse(null);
        Row r = rowIn(area, year, ruler);
        boolean matched = r != null && !r.isDefault();
        if (r == null) {
            r = fallback();
        }
        return new Supply(area, year, r, ruler, matched);
    }

    /** The default row: you bring your own. */
    public Row fallback() {
        for (Row r : rows) {
            if (r.isDefault()) {
                return r;
            }
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * The sentence for a place and year, for the lore screens.
     *
     * <p>It names the ways that are open and, where the law singled out a class, the class — because
     * the class is usually the entire point of the law.
     */
    public String describe(double lat, double lon, int year) {
        Supply s = hereAt(lat, lon, year);
        if (s.row() == null) {
            return "Nobody knows how the men here came by their weapons.";
        }
        Row r = s.row();
        StringBuilder sb = new StringBuilder();
        sb.append(r.text());
        if (r.forWhom() != null && !r.forWhom().isBlank()) {
            sb.append(" (That is the law for ").append(r.forWhom()).append(".)");
        }
        return sb.toString();
    }

    /**
     * One line naming the ways that are open, for a tooltip or a debug screen.
     *
     * <p>Printed as {@code issued, bring, buy, take, gift} and never as a bare list of numbers.
     */
    public String waysLine(double lat, double lon, int year) {
        Supply s = hereAt(lat, lon, year);
        if (s.row() == null) {
            return "unknown";
        }
        List<String> open = s.row().openWays(flagNames);
        if (open.isEmpty()) {
            return "none of them — the state issues, and that is all";
        }
        return String.join(", ", open);
    }

    /** The polity names a row belongs to, or null when it names none. */
    @Nullable
    private static List<String> polityOf(JsonObject o) {
        JsonElement e = o.get("polity");
        if (e == null || e.isJsonNull() || !e.isJsonArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement p : e.getAsJsonArray()) {
            if (!p.isJsonNull()) {
                out.add(p.getAsString());
            }
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Nullable
    private static String nullableStr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Arms[%d rows over %d areas, %s]",
                rows.size(), areas.size(), problem == null ? RESOURCE : problem);
    }
}

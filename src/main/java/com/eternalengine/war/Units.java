package com.eternalengine.war;

import com.eternalengine.civilization.RulerRegister;
import com.eternalengine.money.ProductAtlas;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Step 45 part 4 (C19): <b>units and ranks</b> — how an army here is counted, and who counts it.
 *
 * <p>Part A answered <i>what weapons exist here</i>; part B answered <i>how a man came by one</i>.
 * This is the half of C19 the plan marked as having no raw material at all: there is no table of
 * military units to be read, so every system in the pack is written by hand and names its source.
 *
 * <h2>An army is counted in whatever base the society counts in</h2>
 *
 * <p>Ask four civilisations to divide a thousand men and you get four answers, and none of them is
 * arbitrary:
 *
 * <ul>
 *   <li><b>ten</b> — the Mongols, the Inca, Ming China. The <i>mingghan</i> of a thousand was also
 *       a thousand <i>households</i>, so the regiment and the tax roll were the same list.</li>
 *   <li><b>twenty</b> — the Aztecs, because Nahuatl counts in twenties, and the <i>xiquipilli</i>
 *       of 8,000 is also the word for the pouch that held 8,000 cacao beans.</li>
 *   <li><b>three</b> — the Indian epics. The <i>patti</i> is not ten soldiers but a combined-arms
 *       team of 1 elephant, 1 chariot, 3 horse and 5 foot; triple it eight times to reach
 *       218,700.</li>
 *   <li><b>two</b> — Sparta: 36, 72, 144, 576.</li>
 *   <li><b>none at all</b> — Rome counted in eights, because a century of <i>eighty</i> kept the
 *       name of a levy of a hundred.</li>
 * </ul>
 *
 * <p><b>And one system here is not a ladder at all.</b> The Zulu <i>amabutho</i> count by
 * <b>birth year</b>: a man belongs to the regiment of the age set he was initiated with, is
 * quartered in its own <i>ikhanda</i>, wears its own shield colours, and may not marry until the
 * king releases his whole age set at once. Rome numbers you; the Zulu date you.
 *
 * <h2>The default is the honest one</h2>
 *
 * <p>For most of the map and most of time there is no unit at all — there is a war party of kin,
 * tens of men, following a man who is good at this, who stops being in charge the moment the
 * fighting stops. That is the default, and most area-years land on it. {@link Army#matched()} says
 * whether anything more specific applied, so a caller can tell a real answer from a fallback.
 */
public final class Units {

    public static final String RESOURCE = "/data/eternalengine/eternal/war/units.json.gz";

    private static volatile Units instance;

    /**
     * One rung of a ladder.
     *
     * @param local     what they called it, or null when English is the local word
     * @param size      how many men, or null when the unit is not a number of men
     * @param commander who commanded it, or null when no source names one
     */
    public record Rung(String name, @Nullable String local, @Nullable Integer size,
                       @Nullable String commander, String note) {

        /** The name they used, or the English one when the pack has no local word. */
        public String titled() {
            return local == null || local.isBlank() ? name : local;
        }

        public boolean hasSize() {
            return size != null;
        }

        public boolean hasCommander() {
            return commander != null && !commander.isBlank();
        }
    }

    /**
     * How one people divides an army.
     *
     * @param base   the number the ladder is built on: 10, 20, 3, 2, 8, or null when the system
     *               does not count by a number at all
     * @param rungs  the ladder, smallest first
     * @param polity the states it belongs to, as name fragments, or empty for a civilisation
     */
    public record Ladder(String id, String name, List<String> polity, List<Integer> areas,
                         int y0, int y1, @Nullable Integer base, List<Rung> rungs,
                         String note, String source) {

        /** Whether this system holds in this area and year. */
        public boolean holdsIn(int areaIndex, int year) {
            return areas.contains(areaIndex) && year >= y0 && year <= y1;
        }

        /**
         * Whether this system belongs to the state that rules here.
         *
         * <p>Fragments, not whole names, because one system runs under several successive states:
         * the legion serves the Republic, the Empire and Byzantium in turn.
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

        /** True when this is the default system a place-year falls back to. */
        public boolean isDefault() {
            return "warband".equals(id);
        }

        /** The ladder as numbers, for a tooltip: 10 -> 100 -> 1000 -> 10000. */
        public List<Integer> sizes() {
            List<Integer> out = new ArrayList<>();
            for (Rung r : rungs) {
                if (r.size() != null) {
                    out.add(r.size());
                }
            }
            return out;
        }

        /** The smallest rung, which is the one a player will actually be in. */
        @Nullable
        public Rung bottom() {
            return rungs.isEmpty() ? null : rungs.get(0);
        }

        /** The largest rung. */
        @Nullable
        public Rung top() {
            return rungs.isEmpty() ? null : rungs.get(rungs.size() - 1);
        }

        /**
         * The rung a commander of this many men holds: the smallest one big enough, or the top
         * rung when the number outruns the ladder.
         *
         * <p>Null only when the system counts no numbers at all — the war party and the Zulu
         * age sets are both like that, and a caller asking "what does 500 men make you?" of the
         * Zulu is asking a question the system does not have an answer to.
         */
        @Nullable
        public Rung rungFor(int men) {
            Rung best = null;
            for (Rung r : rungs) {
                if (r.size() != null && r.size() <= men) {
                    best = r;
                }
            }
            return best;
        }

        /** What a commander of this many men is called, or null when the system has no numbers. */
        @Nullable
        public String rankFor(int men) {
            Rung r = rungFor(men);
            return r == null ? null : r.commander();
        }
    }

    /**
     * What the answer is at a point in a year.
     *
     * @param system  the system in force, never null: a place-year with nothing falls to the
     *                default
     * @param ruler   the state that ruled here that year, or null when the register has none
     * @param matched false when nothing but the default applied here
     */
    public record Army(String area, int year, Ladder system, @Nullable String ruler,
                       boolean matched) {

        public List<Rung> rungs() {
            return system.rungs();
        }

        /** What a commander of this many men is called here and now. */
        @Nullable
        public String rankFor(int men) {
            return system.rankFor(men);
        }
    }

    private final List<Ladder> systems;
    private final List<String> areas;
    private final String problem;

    private Units(List<Ladder> systems, List<String> areas, String problem) {
        this.systems = systems;
        this.areas = areas;
        this.problem = problem;
    }

    // -------------------------------------------------------------------------------------------------- reading

    public static Units get() {
        Units u = instance;
        if (u == null) {
            synchronized (Units.class) {
                u = instance;
                if (u == null) {
                    u = load();
                    instance = u;
                }
            }
        }
        return u;
    }

    /** Forget the pack and read it again. */
    public static Units reload() {
        synchronized (Units.class) {
            instance = load();
            return instance;
        }
    }

    private static Units load() {
        List<Ladder> systems = new ArrayList<>();
        List<String> areas = new ArrayList<>();
        try (InputStream raw = Units.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new Units(List.of(), List.of(), "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (JsonElement e : root.getAsJsonArray("areas")) {
                areas.add(e.getAsString());
            }
            for (JsonElement se : root.getAsJsonArray("rows")) {
                JsonObject o = se.getAsJsonObject();
                List<String> polity = new ArrayList<>();
                if (o.has("polity") && !o.get("polity").isJsonNull()) {
                    for (JsonElement e : o.getAsJsonArray("polity")) {
                        if (!e.isJsonNull()) {
                            polity.add(e.getAsString());
                        }
                    }
                }
                List<Integer> as = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("areas")) {
                    as.add(e.getAsInt());
                }
                List<Rung> rungs = new ArrayList<>();
                for (JsonElement re : o.getAsJsonArray("rungs")) {
                    JsonObject g = re.getAsJsonObject();
                    JsonElement size = g.get("size");
                    rungs.add(new Rung(str(g, "name"), nullableStr(g, "local"),
                            size == null || size.isJsonNull() ? null : size.getAsInt(),
                            nullableStr(g, "commander"), str(g, "note")));
                }
                JsonElement base = o.get("base");
                systems.add(new Ladder(str(o, "id"), str(o, "name"), polity, as,
                        o.get("y0").getAsInt(), o.get("y1").getAsInt(),
                        base == null || base.isJsonNull() ? null : base.getAsInt(),
                        rungs, str(o, "note"), str(o, "source")));
            }
            return new Units(systems, areas, null);
        } catch (Exception e) {
            return new Units(List.of(), List.of(), "resource " + RESOURCE + " failed: " + e);
        }
    }

    // -------------------------------------------------------------------------------------------------- asking

    /** The area codes the pack is keyed by. */
    public List<String> areas() {
        return Collections.unmodifiableList(areas);
    }

    public List<Ladder> systems() {
        return Collections.unmodifiableList(systems);
    }

    public boolean hasData() {
        return problem == null && !systems.isEmpty();
    }

    /** Null when the pack is healthy; otherwise why not. */
    @Nullable
    public String problem() {
        return problem;
    }

    /**
     * The ladder in force in this area and year under the state named, or null.
     *
     * <p>A system naming the polity that rules here <b>outranks</b> one that names none, because
     * an area is not a country: East Asia holds both Ming China and Ashikaga Japan, and they did
     * not divide their men the same way. Within each group the last one written wins, since the
     * rows run broadly first and narrowly later.
     */
    @Nullable
    public Ladder systemIn(String area, int year, @Nullable String rulerName) {
        int ai = areas.indexOf(area);
        if (ai < 0) {
            return null;
        }
        Ladder specific = null;
        Ladder general = null;
        for (Ladder s : systems) {
            if (!s.holdsIn(ai, year)) {
                continue;
            }
            if (s.belongsTo(rulerName)) {
                specific = s;
            } else if (s.polity() == null || s.polity().isEmpty()) {
                general = s;
            }
        }
        return specific != null ? specific : general;
    }

    /**
     * How an army here is divided, in this year.
     *
     * <p>The area comes from the product atlas, the same way {@link Armoury} and {@link Arms}
     * resolve it, so all three halves of this step always agree about where you are.
     */
    public Army hereAt(double lat, double lon, int year) {
        String area = ProductAtlas.get().place(lat, lon, year).area();
        String ruler = com.eternalengine.civilization.WhoRuled.hit(lat, lon, year).map(h -> h.ruler()).orElse(null);
        Ladder s = systemIn(area, year, ruler);
        boolean matched = s != null && !s.isDefault();
        if (s == null) {
            s = fallback();
        }
        return new Army(area, year, s, ruler, matched);
    }

    /** The default system: the war party. */
    public Ladder fallback() {
        for (Ladder s : systems) {
            if (s.isDefault()) {
                return s;
            }
        }
        return systems.isEmpty() ? null : systems.get(0);
    }

    /**
     * The ladder as a line, for a tooltip or a debug screen: {@code 10 -> 100 -> 1000 -> 10000}.
     *
     * <p>Empty when the system counts no numbers, which is itself the answer worth printing for
     * the war party and for the Zulu age sets.
     */
    public String ladderLine(double lat, double lon, int year) {
        Army a = hereAt(lat, lon, year);
        if (a.system() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer n : a.system().sizes()) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(n);
        }
        return sb.toString();
    }

    /**
     * The sentence for a place and year, for the lore screens: the ladder, bottom rung first,
     * with who commanded each.
     */
    public String describe(double lat, double lon, int year) {
        Army a = hereAt(lat, lon, year);
        if (a.system() == null) {
            return "Nobody knows how the men here were counted.";
        }
        Ladder s = a.system();
        StringBuilder sb = new StringBuilder();
        sb.append(s.name()).append(": ");
        for (int i = 0; i < s.rungs().size(); i++) {
            Rung r = s.rungs().get(i);
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(r.titled());
            if (r.size() != null) {
                sb.append(" of ").append(r.size());
            }
            if (r.hasCommander()) {
                sb.append(" under a ").append(r.commander());
            }
        }
        sb.append(".");
        return sb.toString();
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
        return String.format(Locale.ROOT, "Units[%d systems over %d areas, %s]",
                systems.size(), areas.size(), problem == null ? RESOURCE : problem);
    }
}

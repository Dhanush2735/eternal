package com.eternalengine.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Step 47 part 2 (E7): <b>tools and inventions as items</b> — what a person can pick up in a year.
 *
 * <p>Step 44's products atlas is a register of what a place <i>makes and sells</i>. This is a
 * register of the thing in a person's <b>hands</b>: what it does, what it <b>replaced</b>, and what
 * in Minecraft stands for it. The two are different questions and the survey found that most of the
 * dates already existed without either of the other two.
 *
 * <h2>The rule this class exists to enforce</h2>
 *
 * <p><b>A tool's date is not its material's date.</b> Step 44 derives a product from its parent, and
 * for tools the parent is a material, so a microscope whose parent is <i>glass</i> became as old as
 * glass: <b>2500 BC in West Asia</b>. Step 46 found the same fault in {@code reindeer} (2000 BC in
 * West Africa) and in {@code donkeys} (a date shared with horses everywhere). Three instances make
 * it a class of error rather than an accident, so {@link #sane(String, int)} checks the orderings
 * that cannot be true and says so rather than trusting the row.
 *
 * <h2>Three things a product row cannot carry</h2>
 *
 * <ol>
 *   <li><b>What it replaced.</b> The printing press replaced the copyist; the tractor replaced the
 *       horse. A tool with no predecessor is a tool the register has misunderstood, and only three
 *       here have none.</li>
 *   <li><b>The vanilla item.</b> Every tool resolves to an item or says why it cannot: A13's rule
 *       from step 46, applied where it was always needed, an inventory. Four have none and their
 *       notes say so.</li>
 *   <li><b>Whether it is older than the world.</b> The warp-weighted loom is 5000 BC, and the world
 *       begins at 3000 BC, so a player at the first year finds it already there.</li>
 * </ol>
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Tools {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/tools.json.gz";

    /** The world's first year, repeated here so this class has no Minecraft dependency. */
    public static final int FIRST_YEAR = -3000;

    private static volatile Tools instance;

    /**
     * A tool.
     *
     * @param made      the year it reaches each area
     * @param replaces  what it displaced, or null when it displaced nothing
     * @param vanilla   a Minecraft item id, or {@code *_placeholder} when there is none
     * @param origin    {@code atlas:<id>} when the date was read from the products atlas
     */
    public record Tool(String id, String name, String family, Map<String, Integer> made,
                       @Nullable String replaces, String vanilla, boolean beforeWorld,
                       String origin, String note, String source) {

        public boolean hereIn(String area, int year) {
            Integer y = made.get(area);
            return y != null && y <= year;
        }

        /** Whether there is a real Minecraft item for this, or only a placeholder. */
        public boolean hasItem() {
            return vanilla != null && !vanilla.isEmpty() && !vanilla.endsWith("_placeholder");
        }

        /** Whether this tool existed before the world's first year. */
        public boolean predatesWorld() {
            return beforeWorld;
        }
    }

    private final Map<String, Tool> byId = new LinkedHashMap<>();
    private final String finding;
    private final String problem;

    private Tools(@Nullable String problem) {
        this.finding = null;
        this.problem = problem;
    }

    private Tools(JsonObject root) {
        for (JsonElement e : root.getAsJsonArray("tools")) {
            JsonObject o = e.getAsJsonObject();
            Map<String, Integer> made = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("made").entrySet()) {
                made.put(m.getKey(), m.getValue().getAsInt());
            }
            byId.put(str(o, "id"), new Tool(str(o, "id"), str(o, "name"), str(o, "family"), made,
                    o.has("replaces") && !o.get("replaces").isJsonNull() ? str(o, "replaces") : null,
                    str(o, "vanilla"), o.has("beforeWorld") && o.get("beforeWorld").getAsBoolean(),
                    str(o, "origin"), str(o, "note"), str(o, "source")));
        }
        this.finding = root.has("finding") ? str(root, "finding") : null;
        this.problem = null;
    }

    public static Tools get() {
        Tools t = instance;
        if (t == null) {
            synchronized (Tools.class) {
                t = instance;
                if (t == null) {
                    t = load();
                    instance = t;
                }
            }
        }
        return t;
    }

    private static Tools load() {
        try (InputStream raw = Tools.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) return new Tools("resource " + RESOURCE + " is absent");
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Tools(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (Exception e) {
            return new Tools("resource " + RESOURCE + " failed: " + e);
        }
    }

    // ---------------------------------------------------------------------------------------- asking

    public boolean hasData() {
        return problem == null && !byId.isEmpty();
    }

    @Nullable
    public String problem() {
        return problem;
    }

    @Nullable
    public String finding() {
        return finding;
    }

    public List<Tool> tools() {
        return List.copyOf(byId.values());
    }

    @Nullable
    public Tool tool(String id) {
        return byId.get(id);
    }

    /** The tools a person has here and now. */
    public List<Tool> toolsAt(String area, int year) {
        List<Tool> out = new ArrayList<>();
        for (Tool t : byId.values()) if (t.hereIn(area, year)) out.add(t);
        return out;
    }

    /** The families of work available here and now, for a report or a market. */
    public Set<String> familiesAt(String area, int year) {
        Set<String> out = new LinkedHashSet<>();
        for (Tool t : toolsAt(area, year)) out.add(t.family());
        return out;
    }

    /**
     * Whether a tool is here and now.
     *
     * <p>Returns false for a tool the register has never heard of rather than throwing: a caller
     * asking about an unknown tool has a bug, and a false answer is easier to see than an exception
     * inside a tick.
     */
    public boolean has(String id, String area, int year) {
        Tool t = byId.get(id);
        return t != null && t.hereIn(area, year);
    }

    /**
     * The year a tool first reaches an area, or null when it never does.
     *
     * <p>A tool dated before 3000 BC returns {@link #FIRST_YEAR}, because the register's answer for
     * "when can I get this" cannot be earlier than the first year the world has.
     */
    @Nullable
    public Integer arrival(String id, String area) {
        Tool t = byId.get(id);
        if (t == null) return null;
        Integer y = t.made().get(area);
        if (y == null) return null;
        return Math.max(FIRST_YEAR, y);
    }

    /** The tool that displaced this one, or null. */
    @Nullable
    public String replacedBy(String id, String area, int year) {
        for (Tool t : byId.values()) {
            if (t.replaces() == null || !t.replaces().toLowerCase(Locale.ROOT).contains(id)) continue;
            if (t.hereIn(area, year)) return t.id();
        }
        return null;
    }

    /**
     * Whether a tool's dates are possible against another's here.
     *
     * <p>The three orderings this register exists to catch: the microscope before the telescope's
     * lens workshops, movable type reaching Europe before China, and the treadle loom reaching
     * Europe before China. A false answer means the data is wrong, not the caller.
     */
    public boolean sane(String earlier, String later, String area) {
        Integer a = arrival(earlier, area);
        Integer b = arrival(later, area);
        if (a == null || b == null) return true;      // nothing to contradict
        return a <= b;
    }

    /** The tools with no Minecraft item, and the reason each gives. */
    public Map<String, String> withNoItem() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Tool t : byId.values()) if (!t.hasItem()) out.put(t.id(), t.note());
        return out;
    }

    /** One line for {@code /ee check}. */
    public String summary() {
        if (!hasData()) return "the tool register has no data: " + problem;
        Set<String> fams = new LinkedHashSet<>();
        int noItem = 0;
        for (Tool t : byId.values()) {
            fams.add(t.family());
            if (!t.hasItem()) noItem++;
        }
        return byId.size() + " tools over " + fams.size() + " families of work; " + noItem
                + " have no Minecraft item and say why";
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Override
    public String toString() {
        return "Tools[" + byId.size() + ", " + (problem == null ? RESOURCE : problem) + "]";
    }
}

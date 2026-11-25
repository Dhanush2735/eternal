package com.eternalengine.war;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Step 45 part 5 (A13): <b>the item resolver</b> — what goes in a soldier's hand.
 *
 * <p><b>The mod ships no weapons of its own.</b> That is a standing instruction, and this class is
 * how it is honoured: it names the items of the weapon mods a player may have installed, and falls
 * back to vanilla when they have none. Nothing here requires any mod to be present.
 *
 * <p><b>This class touches no Minecraft.</b> It resolves to a {@link Resolution} holding an item id
 * or tag as a <i>string</i>, so all of it can be exercised by a plain JDK probe. Turning that
 * string into an {@code ItemStack} is the wiring half (part 6), which is where the nineteen
 * hardcoded sites get replaced.
 *
 * <h2>Three rules</h2>
 *
 * <ol>
 *   <li><b>A tag beats an id.</b> Spartan Weaponry's own wiki records that its registry names
 *       changed between 1.16.5 and 1.19.2 and broke datapacks that had hard-coded them. A tag like
 *       {@code #spartanweaponry:spears} survives a rename; {@code spartanweaponry:spear_iron}
 *       does not.</li>
 *   <li><b>An unknown item is guessed from its name, and the guess is logged.</b> No register can
 *       list every item of every weapon mod. A wrong guess is visible and fixable; a missing
 *       weapon is not. {@code guessKind("spartanweaponry:steel_halberd")} answers {@code pike}.</li>
 *   <li><b>A missing weapon is never an error.</b> With no weapon mod installed, every kind falls
 *       back to the vanilla item the armoury already names — part A put a {@code vanilla} field on
 *       all 24 kinds, and this pack deliberately does <i>not</i> duplicate that list. It reads it.
 *       A second copy would be a second thing to keep in step.</li>
 * </ol>
 *
 * <h2>A missing weapon is also a naming problem, not only a mod problem</h2>
 *
 * <p><b>No Indian weapons exist for Java 1.20.1</b> — the research found only a Bedrock add-on
 * (talwar, pata, tabar) that cannot be used. So a talwar in 1650 Agra is a Sabre from a mod,
 * <i>carrying the name</i> talwar. {@link #localName(String, String)} is the honest fix for a gap
 * no mod fills, and it is cheaper and truer than waiting for someone to model one.
 */
public final class WeaponItems {

    public static final String RESOURCE = "/data/eternalengine/eternal/war/weapon_items.json.gz";

    private static volatile WeaponItems instance;

    /**
     * What goes in the hand.
     *
     * @param id      the item id, or the tag when {@link #isTag}— written {@code #namespace:path}
     * @param isTag   true when {@code id} is an item tag rather than an item id
     * @param name    what to call it here, or null to use the item's own name
     * @param mod     the modid it came from, or null for the vanilla fallback
     * @param mark    "recorded" or "estimated", or "vanilla" for the fallback
     * @param guessed true when the kind was guessed from an item name rather than looked up
     */
    public record Resolution(String id, boolean isTag, @Nullable String name,
                             @Nullable String mod, String mark, boolean guessed) {

        public boolean isVanilla() {
            return mod == null;
        }

        public String describe() {
            return id + (name == null ? "" : " (" + name + ")") + " [" + mark + "]";
        }
    }

    /** A weapon mod the resolver knows about. */
    public record Mod(String id, String name, String licence, List<String> needs,
                      List<String> kinds, String note, String source) {

        /** Whether this mod can be used: it is loaded and so are all the things it needs. */
        public boolean usableIn(Set<String> loadedModIds) {
            if (loadedModIds == null || !loadedModIds.contains(id)) {
                return false;
            }
            for (String need : needs) {
                if (!loadedModIds.contains(need)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** One item a mod supplies for one kind. */
    public record Entry(String mod, String kind, @Nullable String tag, @Nullable String item,
                        String mark, String note) {}

    private final List<Mod> mods;
    private final List<Entry> entries;
    private final Map<String, Map<String, String[]>> localNames;
    private final List<String[]> guessOrder;   // {token, kind}, longest token first
    private final Map<String, String> ammo;
    private final String problem;

    private WeaponItems(List<Mod> mods, List<Entry> entries,
                        Map<String, Map<String, String[]>> localNames, List<String[]> guessOrder,
                        Map<String, String> ammo, String problem) {
        this.mods = mods;
        this.entries = entries;
        this.localNames = localNames;
        this.guessOrder = guessOrder;
        this.ammo = ammo;
        this.problem = problem;
    }

    // -------------------------------------------------------------------------------------------------- reading

    public static WeaponItems get() {
        WeaponItems w = instance;
        if (w == null) {
            synchronized (WeaponItems.class) {
                w = instance;
                if (w == null) {
                    w = load();
                    instance = w;
                }
            }
        }
        return w;
    }

    /** Forget the pack and read it again. */
    public static WeaponItems reload() {
        synchronized (WeaponItems.class) {
            instance = load();
            return instance;
        }
    }

    private static WeaponItems load() {
        List<Mod> mods = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        Map<String, Map<String, String[]>> local = new LinkedHashMap<>();
        List<String[]> guess = new ArrayList<>();
        Map<String, String> ammo = new LinkedHashMap<>();
        try (InputStream raw = WeaponItems.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new WeaponItems(List.of(), List.of(), Map.of(), List.of(), Map.of(),
                        "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (JsonElement me : root.getAsJsonArray("mods")) {
                JsonObject o = me.getAsJsonObject();
                List<String> needs = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("needs")) {
                    needs.add(e.getAsString());
                }
                List<String> kinds = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("kinds")) {
                    kinds.add(e.getAsString());
                }
                mods.add(new Mod(str(o, "id"), str(o, "name"), str(o, "licence"), needs, kinds,
                        str(o, "note"), str(o, "source")));
            }
            for (JsonElement ee : root.getAsJsonArray("entries")) {
                JsonObject o = ee.getAsJsonObject();
                entries.add(new Entry(str(o, "mod"), str(o, "kind"), nullableStr(o, "tag"),
                        nullableStr(o, "item"), str(o, "mark"), str(o, "note")));
            }
            JsonObject loc = root.getAsJsonObject("localNames");
            if (loc != null) {
                for (Map.Entry<String, JsonElement> a : loc.entrySet()) {
                    Map<String, String[]> byKind = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> k : a.getValue().getAsJsonObject()
                            .entrySet()) {
                        JsonObject v = k.getValue().getAsJsonObject();
                        byKind.put(k.getKey(), new String[]{str(v, "name"), str(v, "note")});
                    }
                    local.put(a.getKey(), byKind);
                }
            }
            for (JsonElement ge : root.getAsJsonArray("guess")) {
                JsonObject o = ge.getAsJsonObject();
                String kind = str(o, "kind");
                for (JsonElement t : o.getAsJsonArray("tokens")) {
                    guess.add(new String[]{t.getAsString(), kind});
                }
            }
            // Longest token first, so "greatsword" is read before "sword". The builder ships them
            // sorted, but sorting here too means a hand-edited pack still resolves correctly.
            guess.sort(Comparator.comparingInt((String[] g) -> g[0].length()).reversed());
            for (JsonElement ae : root.getAsJsonArray("ammo")) {
                JsonObject o = ae.getAsJsonObject();
                ammo.put(o.get("mod").getAsString() + "/" + o.get("kind").getAsString(),
                        str(o, "item"));
            }
            return new WeaponItems(mods, entries, local, guess, ammo, null);
        } catch (Exception e) {
            return new WeaponItems(List.of(), List.of(), Map.of(), List.of(), Map.of(),
                    "resource " + RESOURCE + " failed: " + e);
        }
    }

    // -------------------------------------------------------------------------------------------------- asking

    public List<Mod> mods() {
        return List.copyOf(mods);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean hasData() {
        return problem == null && !entries.isEmpty();
    }

    /** Null when the pack is healthy; otherwise why not. */
    @Nullable
    public String problem() {
        return problem;
    }

    /**
     * What to put in the hand for this kind, here and now.
     *
     * <p>Picks the first usable entry for the kind, preferring an <b>item tag</b> over an item id
     * and a <b>recorded</b> id over an estimated one. When no mod supplies it — or no mod is
     * installed at all — returns the vanilla item the armoury names for this kind, which is never
     * null for any of the 24 kinds.
     *
     * @param kindId        the armoury kind, e.g. "sword"
     * @param area          the way-of-life area, which decides the local name
     * @param loadedModIds  the modids that are actually loaded, or null for none
     */
    public Resolution resolve(String kindId, @Nullable String area, int year,
                              @Nullable Set<String> loadedModIds) {
        Entry best = null;
        for (Entry e : entries) {
            if (!e.kind().equals(kindId)) {
                continue;
            }
            if (!usable(e.mod(), loadedModIds)) {
                continue;
            }
            if (best == null || better(e, best)) {
                best = e;
            }
        }
        String[] local = localNameAndNote(kindId, area);
        if (best != null) {
            String id = best.tag() != null ? best.tag() : best.item();
            return new Resolution(id, best.tag() != null, local == null ? null : local[0],
                    best.mod(), best.mark(), false);
        }
        // No mod supplies it. The armoury named a vanilla fallback for all 24 kinds; use it.
        Armoury.Kind k = Armoury.get().kind(kindId);
        String vanilla = k == null ? null : k.vanilla();
        if (vanilla == null) {
            return null;
        }
        return new Resolution(vanilla, false, local == null ? null : local[0], null, "vanilla",
                false);
    }

    /** Whether a mod is loaded and so are the things it needs. */
    private boolean usable(String modId, @Nullable Set<String> loaded) {
        if (loaded == null) {
            return false;
        }
        for (Mod m : mods) {
            if (m.id().equals(modId)) {
                return m.usableIn(loaded);
            }
        }
        return false;
    }

    /** A tag beats an id; a recorded id beats an estimated one. */
    private static boolean better(Entry a, Entry b) {
        boolean aTag = a.tag() != null;
        boolean bTag = b.tag() != null;
        if (aTag != bTag) {
            return aTag;
        }
        boolean aRec = "recorded".equals(a.mark());
        boolean bRec = "recorded".equals(b.mark());
        return aRec != bRec && aRec;
    }

    /**
     * The kind an unknown modded item belongs to, read from its own name, or null.
     *
     * <p>{@code spartanweaponry:steel_halberd} → {@code pike}. The longest matching token wins, so
     * "greatsword" is not swallowed by "sword". A caller should log the result: <b>a guess is a
     * thing to be corrected, not a thing to be trusted.</b>
     */
    @Nullable
    public String guessKind(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        // strip the namespace: only the path carries the meaning
        int colon = lower.indexOf(':');
        String path = colon >= 0 ? lower.substring(colon + 1) : lower;
        for (String[] g : guessOrder) {
            if (path.contains(g[0])) {
                return g[1];
            }
        }
        return null;
    }

    /**
     * What the people of this area called this kind of weapon, or null.
     *
     * <p>This is the fix for a gap no mod fills. Nothing supplies an Indian sword for Java 1.20.1,
     * so the sword in 1650 Agra is a modded sabre called a <i>talwar</i>.
     */
    @Nullable
    public String localName(@Nullable String kindId, @Nullable String area) {
        String[] r = localNameAndNote(kindId, area);
        return r == null ? null : r[0];
    }

    /** The local name and the note that goes with it, or null. */
    @Nullable
    public String[] localNameAndNote(@Nullable String kindId, @Nullable String area) {
        if (kindId == null || area == null) {
            return null;
        }
        Map<String, String[]> byKind = localNames.get(area);
        return byKind == null ? null : byKind.get(kindId);
    }

    /** The ammunition a mod's weapon needs, or null when it needs none that we know. */
    @Nullable
    public String ammoFor(String modId, String kindId) {
        return ammo.get(modId + "/" + kindId);
    }

    /**
     * What is missing, for {@code /ee check}: the kinds with no mod supplying them under the mods
     * that are loaded. Never an error — it is the list of things that will fall back to vanilla.
     */
    public List<String> fallingBackToVanilla(@Nullable Set<String> loadedModIds) {
        List<String> out = new ArrayList<>();
        for (Armoury.Kind k : Armoury.get().kinds()) {
            boolean any = false;
            for (Entry e : entries) {
                if (e.kind().equals(k.id()) && usable(e.mod(), loadedModIds)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                out.add(k.id());
            }
        }
        return out;
    }

    /** One line naming the mods the resolver can see, for {@code /ee check}. */
    public String modSummary(@Nullable Set<String> loadedModIds) {
        if (loadedModIds == null || loadedModIds.isEmpty()) {
            return "no weapon mods installed - every kind falls back to vanilla";
        }
        List<String> on = new ArrayList<>();
        for (Mod m : mods) {
            if (m.usableIn(loadedModIds)) {
                on.add(m.name());
            }
        }
        return on.isEmpty() ? "no supported weapon mods installed" : String.join(", ", on);
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
        return String.format(Locale.ROOT, "WeaponItems[%d mods, %d entries, %s]",
                mods.size(), entries.size(), problem == null ? RESOURCE : problem);
    }
}

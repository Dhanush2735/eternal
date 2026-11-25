package com.eternalengine.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Step 47 part 4 (E12): <b>the period wardrobe</b> — what a person wore before 1873.
 *
 * <h2>The bug this class exists to fix</h2>
 *
 * <p>{@link SkinPoolDatabase}'s 40 garments <b>all arrive in 1873 or later</b>: {@code jeans} in
 * 1873, {@code button_down} in 1900, {@code crew_tee} in 1913. So it does the only thing it can:
 *
 * <pre>
 *   if (year &lt; p.era("modern")) {
 *       return new Outfit(List.of(), false, "the year has not reached the modern wardrobe");
 *   }
 * </pre>
 *
 * <p><b>Every person in the world before 1945 is dressed in nothing.</b> The plan called E12
 * "partly done: looks by era" and it was not: it was looks by the <i>modern</i> era, and six
 * thousand years of the world were bare.
 *
 * <p>The culture pools carry a {@code lower} (dhoti, sari, tunic, robe, wrap) and a handful of
 * cultural items, so the missing half is precise and a little comic: <b>the top half of the
 * body</b>. A weaver in the Deccan in 1400 wore a dhoti and no shirt.
 *
 * <h2>The finding: the wardrobe is a technology, and its dates are the history of tailoring</h2>
 *
 * <p>Almost everything before 1300 is a <b>rectangle</b> — the tunic, the chiton, the hanfu, the
 * huipil, the poncho and the wrapper are all cloth with holes in, and none of them needs a curved
 * seam. The <b>doublet of the 1340s</b> is the first European garment <i>cut</i> to a body, and
 * everything fitted that Europe has worn since follows from that one change in a tailor's technique,
 * in the same decade as the Black Death.
 *
 * <h2>How it is used</h2>
 *
 * <p>{@link SkinPoolDatabase} consults this class only where it used to return nothing, so the
 * modern wardrobe is untouched and the six thousand years before it now have clothes.
 *
 * <p><b>This class touches no Minecraft</b>, so every check runs on a plain JDK.
 */
public final class Wardrobe {

    public static final String RESOURCE = "/data/eternalengine/eternal/names/wardrobe.json.gz";

    /** The year the modern wardrobe list starts. Before this, this class answers. */
    public static final int MODERN_WARDROBE_FROM = 1873;

    private static volatile Wardrobe instance;

    /** One period garment. */
    public record Garment(String name, String part, boolean female, int arrival,
                          List<String> cultures, List<String> roles, String mark, String note,
                          String source) {

        public boolean wornBy(String culture, int year, boolean isFemale) {
            return female == isFemale && arrival <= year && cultures.contains(culture);
        }

        public boolean suitsRole(String role) {
            return role != null && roles.contains(role.toLowerCase(Locale.ROOT));
        }
    }

    private final List<Garment> garments = new ArrayList<>();
    private final String finding;
    private final String problem;

    private Wardrobe(@Nullable String problem) {
        this.finding = null;
        this.problem = problem;
    }

    private Wardrobe(JsonObject root) {
        for (JsonElement e : root.getAsJsonArray("garments")) {
            JsonObject o = e.getAsJsonObject();
            garments.add(new Garment(str(o, "name"), str(o, "part"),
                    o.get("female").getAsBoolean(), o.get("arrival").getAsInt(),
                    strings(o, "cultures"), strings(o, "roles"), str(o, "mark"),
                    str(o, "note"), str(o, "source")));
        }
        this.finding = root.has("finding") ? str(root, "finding") : null;
        this.problem = null;
    }

    public static Wardrobe get() {
        Wardrobe w = instance;
        if (w == null) {
            synchronized (Wardrobe.class) {
                w = instance;
                if (w == null) {
                    w = load();
                    instance = w;
                }
            }
        }
        return w;
    }

    private static Wardrobe load() {
        try (InputStream raw = Wardrobe.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) return new Wardrobe("resource " + RESOURCE + " is absent");
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                return new Wardrobe(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (Exception e) {
            return new Wardrobe("resource " + RESOURCE + " failed: " + e);
        }
    }

    // ---------------------------------------------------------------------------------------- asking

    public boolean hasData() {
        return problem == null && !garments.isEmpty();
    }

    @Nullable
    public String problem() {
        return problem;
    }

    @Nullable
    public String finding() {
        return finding;
    }

    public List<Garment> garments() {
        return List.copyOf(garments);
    }

    /** Everything a person of this culture, year and sex could be wearing. */
    public List<Garment> at(String culture, int year, boolean female) {
        List<Garment> out = new ArrayList<>();
        for (Garment g : garments) if (g.wornBy(culture, year, female)) out.add(g);
        return out;
    }

    /**
     * The period garments for one part of the body, best first.
     *
     * <p>"Best" means a garment the trade is known for comes before one that merely fits the year,
     * which is the same preference the modern wardrobe applies and the reason a market street is
     * not a uniform.
     */
    public List<String> forPart(String culture, int year, boolean female, String part,
                               @Nullable String role) {
        List<String> liked = new ArrayList<>();
        List<String> any = new ArrayList<>();
        for (Garment g : at(culture, year, female)) {
            if (!g.part().equals(part)) continue;
            any.add(g.name());
            if (g.suitsRole(role)) liked.add(g.name());
        }
        if (!liked.isEmpty()) return liked;
        return any;
    }

    /**
     * The whole period outfit for a person, or an empty list when this year has nothing.
     *
     * <p>An empty answer here is what the caller must treat as "the historical dress stands": it is
     * not an error, and it is not a reason to dress somebody in 1913.
     */
    public List<String> outfitFor(String culture, int year, boolean female, @Nullable String role) {
        List<String> out = new ArrayList<>();
        for (String part : List.of("top", "dress", "outer", "bottom", "foot", "head")) {
            List<String> options = forPart(culture, year, female, part, role);
            if (!options.isEmpty()) out.add(options.get(0));
        }
        return out;
    }

    /** Whether this class has anything for a culture and year, which the caller asks first. */
    public boolean covers(String culture, int year, boolean female) {
        return !at(culture, year, female).isEmpty();
    }

    /** The earliest year this register can dress a culture, or null when it never can. */
    @Nullable
    public Integer earliestFor(String culture, boolean female) {
        Integer best = null;
        for (Garment g : garments) {
            if (!g.cultures().contains(culture) || g.female() != female) continue;
            if (best == null || g.arrival() < best) best = g.arrival();
        }
        return best;
    }

    /** One line for {@code /ee check}. */
    public String summary() {
        if (!hasData()) return "the period wardrobe has no data: " + problem;
        int before = 0;
        for (Garment g : garments) if (g.arrival() < MODERN_WARDROBE_FROM) before++;
        return garments.size() + " period garments, " + before + " of them before "
                + MODERN_WARDROBE_FROM;
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        return out;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Override
    public String toString() {
        return "Wardrobe[" + garments.size() + ", " + (problem == null ? RESOURCE : problem) + "]";
    }
}

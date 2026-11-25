package com.eternalengine.villager;

import com.eternalengine.EternalEngineMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The half of a name that the century decides.
 *
 * <h3>The gap this fills</h3>
 * The name lists answer <em>where</em> a person is from. They do not answer <em>when</em>: a Telugu
 * village in 1400 and the same village in 1900 drew from the same list, so every century used the
 * same surnames. Sub-requirement npc-2 as measured: the year picked the place's language, not the
 * century's names.
 *
 * <h3>Where the answers come from</h3>
 * The mod already owns the record it needs: {@code history/famous_people.json.gz}, 184,060 people
 * with a recorded birth year and a recorded birthplace. For each of them this register asks which
 * name list their ground belongs to (the same rule the mod uses) and which era they were born in, and
 * keeps the surname they carried. So a Deccan surname in the 1500s is a surname real people of the
 * Deccan carried in the 1500s.
 *
 * <h3>What it does not do, and why</h3>
 * <b>Sex, and what it costs.</b> The source names a sex for about a fifth of its people - in the
 * short description, "Queen of Castile", "monk of Cluny" - so an era's first names are built only
 * from that fifth, and only when at least eight names survive for a sex. Where it cannot say, the
 * list is empty and the pool's own sex-correct list stands. Surnames need no such care: a family name
 * is a family name whichever sex carries it.
 *
 * <p>Where the record is too thin to speak for an era - the ancient world is very thinly covered by
 * a modern encyclopaedia - the register says nothing for that stretch of years and the pool's own
 * surname stands. It never invents a name to fill a gap.
 */
public final class EraNames {

    /** The register, shipped in the jar and overridable from the world folder. */
    public static final String RESOURCE =
            "/data/eternalengine/eternal/names/era_names.json.gz";

    private static final EraNames INSTANCE = new EraNames();

    /**
     * One stretch of years, and what the people of that ground were called in it.
     *
     * @param surnames the family names
     * @param male     first names the record puts on men in this era, or empty when the record is
     *                 too thin to say - then the pool's own list stands
     * @param female   the same for women
     */
    public record Band(int from, int to, List<String> surnames, List<String> male,
                       List<String> female) {
        boolean holds(int year) {
            return year >= from && year < to;
        }
    }

    private final Map<String, List<Band>> bands = new HashMap<>();
    private final Map<String, List<String>> wide = new HashMap<>();
    private volatile boolean loaded;
    private String source = "missing";

    private EraNames() {
    }

    public static EraNames get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /**
     * The surnames this name list carried in this year, or an empty list when the record cannot speak
     * for that list in that year.
     *
     * @param pool a pool id from {@link VillagerNameDatabase} - {@code deccan}, {@code rome}, ...
     * @param year the year the person lives in; anything below -10000 or above 3000 finds nothing
     */
    public List<String> surnames(String pool, int year) {
        ensureLoaded();
        if (pool == null) {
            return List.of();
        }
        List<Band> list = bands.get(pool.toLowerCase(Locale.ROOT));
        if (list != null) {
            for (Band b : list) {
                if (b.holds(year)) {
                    return b.surnames();
                }
            }
        }
        List<String> fallback = wide.get(pool.toLowerCase(Locale.ROOT));
        return fallback == null ? List.of() : fallback;
    }

    /**
     * The first names this list put on one sex in this year, or an empty list when the record cannot
     * speak for it. An empty list means the caller keeps its own list - the register never invents a
     * name to fill a gap.
     */
    public List<String> givenNames(String pool, int year, boolean female) {
        Band b = bandFor(pool, year);
        if (b == null) {
            return List.of();
        }
        return female ? b.female() : b.male();
    }

    /** The band this list and year fall in, or null. */
    private Band bandFor(String pool, int year) {
        ensureLoaded();
        if (pool == null) {
            return null;
        }
        List<Band> list = bands.get(pool.toLowerCase(Locale.ROOT));
        if (list == null) {
            return null;
        }
        for (Band b : list) {
            if (b.holds(year)) {
                return b;
            }
        }
        return null;
    }

    /** Is this list known to the register at all? For the probes and `/ee check`. */
    public boolean knows(String pool) {
        ensureLoaded();
        return pool != null && bands.containsKey(pool.toLowerCase(Locale.ROOT));
    }

    public int poolCount() {
        ensureLoaded();
        return bands.size();
    }

    public String summary() {
        ensureLoaded();
        int n = 0;
        for (List<Band> b : bands.values()) {
            n += b.size();
        }
        return "era names: " + bands.size() + " name lists, " + n + " eras, from " + source;
    }

    // ------------------------------------------------------------------ loading

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = open()) {
            read(in, "jar");
        } catch (Exception e) {
            if (EternalEngineMod.getLogger() != null) {
                EternalEngineMod.getLogger().warn("[EraNames] the era layer could not be read: {}",
                        e.toString());
            }
            bands.clear();
            wide.clear();
        }
    }

    private void read(InputStream in, String from) throws Exception {
        if (in == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject pools = root.getAsJsonObject("pools");
            if (pools == null) {
                return;
            }
            source = from;
            for (Map.Entry<String, JsonElement> e : pools.entrySet()) {
                readPool(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonObject());
            }
        }
    }

    private static List<String> strings(JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return out;
        }
        for (JsonElement s : element.getAsJsonArray()) {
            out.add(s.getAsString());
        }
        return out;
    }

    private void readPool(String pool, JsonObject o) {
        List<Band> list = new ArrayList<>();
        JsonArray arr = o.getAsJsonArray("bands");
        if (arr != null) {
            for (JsonElement el : arr) {
                JsonArray b = el.getAsJsonArray();
                int from = b.get(0).getAsInt();
                int to = b.get(1).getAsInt();
                List<String> names = strings(b.get(2));
                if (names.isEmpty()) {
                    continue;
                }
                List<String> male = b.size() > 3 ? strings(b.get(3)) : List.of();
                List<String> female = b.size() > 4 ? strings(b.get(4)) : List.of();
                list.add(new Band(from, to, List.copyOf(names), List.copyOf(male),
                        List.copyOf(female)));
            }
        }
        if (!list.isEmpty()) {
            bands.put(pool, List.copyOf(list));
        }
        JsonArray w = o.getAsJsonArray("wide");
        if (w != null) {
            List<String> names = new ArrayList<>();
            for (JsonElement s : w) {
                names.add(s.getAsString());
            }
            if (!names.isEmpty()) {
                wide.put(pool, List.copyOf(names));
            }
        }
    }

    /**
     * Read the register from a world folder instead of the jar, the same order the name pack and the
     * population pack use: a pack a world ships wins over the one inside the mod.
     */
    public int loadFrom(Path dir) {
        if (dir == null) {
            return 0;
        }
        try {
            Path p = dir.resolve("era_names.json.gz");
            if (Files.isReadable(p)) {
                synchronized (this) {
                    bands.clear();
                    wide.clear();
                    try (InputStream in = Files.newInputStream(p)) {
                        read(in, "world folder");
                    }
                    loaded = true;
                }
                return bands.size();
            }
        } catch (Exception e) {
            if (EternalEngineMod.getLogger() != null) {
                EternalEngineMod.getLogger().warn("[EraNames] the world's ever layer was not read: {}",
                        e.toString());
            }
        }
        return 0;
    }

    private InputStream open() {
        InputStream in = EraNames.class.getResourceAsStream(RESOURCE);
        if (in != null) {
            source = "jar";
        }
        return in;
    }
}

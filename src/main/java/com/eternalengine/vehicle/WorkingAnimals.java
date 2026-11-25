package com.eternalengine.vehicle;

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
import java.util.zip.GZIPInputStream;

/**
 * Step 46 part 2 (D14): <b>the working animals</b> — what carried the world's goods and drew its
 * ploughs, and the places where no beast could live at all.
 *
 * <p><b>This class exists to fix one bug.</b> {@code trade/LogisticsManager} spawned two to four
 * <b>donkeys</b> for every caravan, in every era, on every continent. Three donkeys at Cusco in
 * AD 1500, when the Andes had llamas for four thousand years. Three donkeys in Australia in 1700,
 * eighty-eight years before donkeys arrived. This is the lookup that replaces it:
 * <i>which animal carries, here, in this year?</i>
 *
 * <p><b>It touches no Minecraft</b>, so every check runs on a plain JDK with no classpath.
 *
 * <h2>The carrier is not always a beast</h2>
 *
 * <p><b>The tsetse fly is the shape of African transport.</b> Alsan (2014) measures it: a
 * one-standard-deviation rise in tsetse suitability goes with a <b>23-point fall in the chance a
 * people had large domestic animals</b> and a <b>6-point fall in plough use</b>. Below the belt
 * nothing with four legs survives, so the carrier is a <b>human porter</b> — 29 kg on the head,
 * 20 km a day, tens of thousands of them on the East African caravan roads. The porter is
 * therefore the <b>default</b> answer, and the register asserts that he reaches all 17 areas so
 * that no place and year is ever left with nothing.
 *
 * <p><b>The oldest carrier needs no technology at all.</b> Litters, rafts, canoes and sledges are
 * dated −10000 in all 17 areas, four thousand years before the wheel anywhere. The first vehicle
 * is the one that is not a machine.
 *
 * <h2>Two corrections to the products atlas, recorded not hidden</h2>
 *
 * <ul>
 *   <li><b>Reindeer were dated −2000 in West Africa.</b> That is a step-44 part-C artefact — a
 *       child inheriting a parent's date. Six {@code never} entries remove them.</li>
 *   <li><b>Donkeys and horses shared a date everywhere.</b> The donkey is African, domesticated
 *       from the African wild ass; the horse is a steppe animal that reached Africa much later.
 *       The products atlas has them identical because both were derived from the same parent. In
 *       Africa the donkey is moved earlier, marked {@code estimated}, with the reason given.</li>
 * </ul>
 *
 * <h2>A belt the register will not pretend to draw</h2>
 *
 * <p>Seventeen areas cannot separate the Sahel, where camels work, from the forest, where nothing
 * survives. Three areas are marked <b>mixed</b> and the answer says so — the same honesty step
 * 44's diet had to show about the rice south and the millet north. Choosing one would be a lie
 * about the other.
 */
public final class WorkingAnimals {

    public static final String RESOURCE = "/data/eternalengine/eternal/economy/animals.json.gz";

    private static volatile WorkingAnimals instance;

    /** The jobs an animal is for. */
    public static final List<String> ROLES = List.of("port", "pack", "ride", "pull", "plough",
            "herd");

    /**
     * A working animal.
     *
     * @param roles    what it is for: port, pack, ride, pull, plough, herd
     * @param loadKg   what it carries, in kilograms
     * @param kmPerDay how far in a day, loaded
     * @param years    the year it reaches each area, read from the products atlas
     * @param never    the areas it never reached, with the reason
     * @param vanilla  the Minecraft entity that stands for it, or null when vanilla has none
     */
    public record Animal(String id, String name, String kind, List<String> roles, int loadKg,
                         int kmPerDay, String mark, Map<String, Integer> years,
                         Map<String, String> never, List<String> supersedes,
                         @Nullable String vanilla, String note, String source) {

        /** The year it reaches an area, or null when it never did. */
        @Nullable
        public Integer yearIn(String area) {
            return years.get(area);
        }

        /** Why it never reached an area, or null when it did. */
        @Nullable
        public String neverIn(String area) {
            return never.get(area);
        }

        /** Whether it has reached this area by this year. */
        public boolean hereBy(String area, int year) {
            Integer y = years.get(area);
            return y != null && y <= year;
        }

        /** Whether it can do this job at all. */
        public boolean does(String role) {
            return roles.contains(role);
        }

        public boolean isBeast() {
            return "beast".equals(kind);
        }

        public String describe() {
            return name + " (" + loadKg + " kg, " + kmPerDay + " km a day)";
        }
    }

    /** What carries here, and why that one. */
    public record Choice(Animal animal, @Nullable String whyNotBetter, boolean mixed,
                         @Nullable String mixedNote) {

        public String line() {
            String s = animal.describe();
            if (mixed && mixedNote != null) s += " — but this area is two worlds: " + mixedNote;
            if (whyNotBetter != null) s += " (" + whyNotBetter + ")";
            return s;
        }
    }

    private final List<Animal> animals;
    private final Map<String, String> mixed;
    private final String tsetse;
    private final String problem;

    private WorkingAnimals(List<Animal> animals, Map<String, String> mixed, String tsetse,
                           String problem) {
        this.animals = animals;
        this.mixed = mixed;
        this.tsetse = tsetse;
        this.problem = problem;
    }

    public static WorkingAnimals get() {
        WorkingAnimals w = instance;
        if (w == null) {
            synchronized (WorkingAnimals.class) {
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
    public static WorkingAnimals reload() {
        synchronized (WorkingAnimals.class) {
            instance = load();
            return instance;
        }
    }

    private static WorkingAnimals load() {
        List<Animal> out = new ArrayList<>();
        Map<String, String> mixed = new LinkedHashMap<>();
        try (InputStream raw = WorkingAnimals.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return new WorkingAnimals(List.of(), Map.of(), null,
                        "resource " + RESOURCE + " is absent");
            }
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(new GZIPInputStream(raw),
                    StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (JsonElement ae : root.getAsJsonArray("animals")) {
                JsonObject o = ae.getAsJsonObject();
                List<String> roles = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("roles")) roles.add(e.getAsString());
                List<String> sup = new ArrayList<>();
                if (o.has("supersedes")) {
                    for (JsonElement e : o.getAsJsonArray("supersedes")) sup.add(e.getAsString());
                }
                Map<String, Integer> years = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("years").entrySet()) {
                    years.put(e.getKey(), e.getValue().getAsInt());
                }
                Map<String, String> never = new LinkedHashMap<>();
                if (o.has("never")) {
                    for (Map.Entry<String, JsonElement> e
                            : o.getAsJsonObject("never").entrySet()) {
                        never.put(e.getKey(), e.getValue().getAsString());
                    }
                }
                out.add(new Animal(str(o, "id"), str(o, "name"), str(o, "kind"), roles,
                        o.get("loadKg").getAsInt(), o.get("kmPerDay").getAsInt(), str(o, "mark"),
                        years, never, sup, nullableStr(o, "vanilla"), str(o, "note"),
                        str(o, "source")));
            }
            if (root.has("mixed")) {
                for (Map.Entry<String, JsonElement> e
                        : root.getAsJsonObject("mixed").entrySet()) {
                    mixed.put(e.getKey(), e.getValue().getAsString());
                }
            }
            String tsetse = root.has("tsetse") ? str(root, "tsetse") : null;
            return new WorkingAnimals(out, mixed, tsetse, null);
        } catch (Exception e) {
            return new WorkingAnimals(List.of(), Map.of(), null,
                    "resource " + RESOURCE + " failed: " + e);
        }
    }

    // -------------------------------------------------------------------------------------------------- asking

    public List<Animal> animals() {
        return List.copyOf(animals);
    }

    public boolean hasData() {
        return problem == null && !animals.isEmpty();
    }

    /** Null when the pack is healthy; otherwise why not. */
    @Nullable
    public String problem() {
        return problem;
    }

    /** The areas where one area holds two transport worlds, with the note saying what they are. */
    public Map<String, String> mixed() {
        return Map.copyOf(mixed);
    }

    /** The measured effect of the tsetse fly, as one line. */
    @Nullable
    public String tsetse() {
        return tsetse;
    }

    /** One animal by id, or null. */
    @Nullable
    public Animal byId(String id) {
        for (Animal a : animals) {
            if (a.id().equals(id)) return a;
        }
        return null;
    }

    /** Every animal that has reached this area by this year and can do this job. */
    public List<Animal> able(String role, String area, int year) {
        List<Animal> out = new ArrayList<>();
        for (Animal a : animals) {
            if (a.does(role) && a.hereBy(area, year)) out.add(a);
        }
        // The most capable first: a caravan wants the biggest load that can walk.
        out.sort(Comparator.comparingInt(Animal::loadKg).reversed());
        return out;
    }

    /**
     * What carries here, in this year.
     *
     * <p>The biggest load that has reached this place by now. When nothing has — the tsetse belt
     * before the railway, the Andes before the Spanish, Australia before 1788 — the answer is the
     * <b>human porter</b>, who is everywhere and always, and who is the true default carrier of
     * the world.
     *
     * @param role the job, e.g. "pack"; "port" asks for a human carrier specifically
     */
    public Choice carrierFor(String role, String area, int year) {
        List<Animal> able = able(role, area, year);
        if (!able.isEmpty()) {
            Animal best = able.get(0);
            String whyNot = best.isBeast() ? null : "no beast here, so a person carries it";
            return new Choice(best, whyNot, mixed.containsKey(area), mixed.get(area));
        }
        // Nothing that can do this job has reached this place. Fall back to the porter, who
        // always can, and say what is missing.
        Animal porter = byId("porter");
        if (porter != null && porter.does("pack")) {
            return new Choice(porter, "no animal that can " + role
                    + " had reached this place by " + year, mixed.containsKey(area),
                    mixed.get(area));
        }
        return null;
    }

    /** What carries cargo here, in this year. The call {@code LogisticsManager} should make. */
    public Choice packAnimalFor(String area, int year) {
        return carrierFor("pack", area, year);
    }

    /** What draws a plough here, or null when nothing does. */
    @Nullable
    public Choice ploughFor(String area, int year) {
        List<Animal> able = able("plough", area, year);
        if (able.isEmpty()) return null;
        return new Choice(able.get(0), null, mixed.containsKey(area), mixed.get(area));
    }

    /**
     * How many of these animals a caravan needs to move a load, as a line for a log.
     *
     * <p>This is the number the hardcoded caravan never computed: three donkeys carry 150 kg, and
     * a camel carries 200 on its own.
     */
    public String trainFor(String area, int year, int loadKg) {
        Choice c = packAnimalFor(area, year);
        if (c == null) return "nothing can carry anything here";
        int n = Math.max(1, (loadKg + c.animal().loadKg() - 1) / c.animal().loadKg());
        return n + " × " + c.animal().name() + " for " + loadKg + " kg";
    }

    /** One line for {@code /ee check}. */
    public String summary() {
        if (!hasData()) return "the working-animal register has no data: " + problem;
        return animals.size() + " working animals; " + mixed.size()
                + " areas are two transport worlds at once";
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
        return String.format(Locale.ROOT, "WorkingAnimals[%d animals, %s]", animals.size(),
                problem == null ? RESOURCE : problem);
    }
}

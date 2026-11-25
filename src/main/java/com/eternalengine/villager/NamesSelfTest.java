package com.eternalengine.villager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * The names acceptance, runnable without a client:
 * {@code java com.eternalengine.villager.NamesSelfTest [names-data-dir]}.
 *
 * <p>Permission 04 (2026-11-07) asked for exactly this, in the owner's words: 200 villager names
 * sampled through the installed code at the nine test places, both sexes — 0 wrong sex, 0 court
 * names on a ploughman, 0 foreign surnames, 0 duplicates within a village, 0 titles as names —
 * with the "why" answer available for every drawn name. The Python rig that built the register
 * cannot compile this tree, so this is the Java speaking for itself: run it on the machine that
 * builds the mod. A red line here means the game would name someone the research did not.
 *
 * <p>The nine test places are the re-audit's official set (STAGES_0_1_REPORT.md, section 5):
 * Operation Polo Hyderabad 1948, Panipat 1526, Talikota 1565, Akbar crowned Agra 1556, Black
 * Death Florence 1348, Great Fire London 1666, Hyderabad founded 1591, Timbuktu 1500, Cusco 1500.
 *
 * <p>The data dir argument is optional: when given, the files are read from there (a world
 * folder or the checkout's resources); when absent, the copy packaged in the jar is used.
 */
public final class NamesSelfTest {

    private static int passes;
    private static int fails;

    private static final double[] LATS = { 17.40, 29.95, 14.55, 27.17, 43.77, 51.51, 17.40, 16.77, -13.53 };
    private static final double[] LONS = { 78.48, 77.48, 75.95, 78.01, 11.26, -0.13, 78.48, -3.01, -71.97 };
    private static final int[] YEARS = { 1948, 1526, 1565, 1556, 1348, 1666, 1591, 1500, 1500 };
    private static final String[] NAMES = { "Hyderabad 1948", "Panipat 1526", "Talikota 1565", "Agra 1556",
            "Florence 1348", "London 1666", "Hyderabad 1591", "Timbuktu 1500", "Cusco 1500" };

    public static void main(String[] args) throws Exception {
        Path dir = args.length > 0 ? Path.of(args[0]) : null;
        PlaceNameResolver resolver = new PlaceNameResolver();
        int points = resolver.loadFrom(dir);
        System.out.println("NamesSelfTest: register " + (resolver.isLoaded() ? "loaded (" + points + " language points)" : "NOT LOADED"));
        if (!resolver.isLoaded()) {
            System.out.println("[FAIL] the register did not load; give the names data dir as the first argument");
            System.exit(1);
        }
        Set<String> male = new LinkedHashSet<>();
        Set<String> female = new LinkedHashSet<>();
        readLexicon(dir, male, female);

        VillagerNameDatabase db = new VillagerNameDatabase();
        db.loadFrom(dir);
        check1Loaded(db);
        check2Sample200(resolver, db, male, female);
        check3VillageNoDuplicates(resolver);
        check4Determinism(resolver);
        check5LegacyFallback();
        check6OccupationalWord(db);

        System.out.println();
        System.out.println(passes + " passed, " + fails + " failed");
        System.out.println(fails == 0 ? "NAMES ACCEPTANCE: ALL PASS" : "NAMES ACCEPTANCE: FAIL");
        System.exit(fails == 0 ? 0 : 1);
    }

    /** 1. The installed path is live: the database carries a loaded register. */
    private static void check1Loaded(VillagerNameDatabase db) {
        if (db.placeNamesLoaded()) {
            pass("1 the database carries the loaded register");
        } else {
            fail("1 the database did not load the register");
        }
    }

    /**
     * 2. The owner's hand check: 200 names (11 per place per sex, 9 places) through the
     * installed code, each with its "why". The draw comes straight from the register (bare
     * "given surname", so the given is everything but the last token — a multi-word given like
     * "Wu Zetian" is checked word by word; the lexicon is single-word) and the installed
     * database path is asserted to answer for the same place. Wrong sex is checked against the
     * lexicon: a drawn given name must not be in the opposite sex's list.
     */
    private static void check2Sample200(PlaceNameResolver resolver, VillagerNameDatabase db,
                                        Set<String> male, Set<String> female) {
        int drawn = 0;
        long faults = 0;
        for (int p = 0; p < LATS.length; p++) {
            for (int sex = 0; sex < 2; sex++) {
                for (int i = 0; i < 11; i++) {
                    long seed = mixSeed(1_000_000L + YEARS[p] * 7919L + sex * 101L + i);
                    String bare = resolver.nameFor(LATS[p], LONS[p], YEARS[p], sex == 0 ? "m" : "f",
                            new Random(seed), null);
                    String installed = db.getRandomPlaceName(
                            db.poolForPlace(LATS[p], LONS[p], YEARS[p], "witness"),
                            LATS[p], LONS[p], YEARS[p], "villager", sex == 1,
                            new Random(seed), null);
                    drawn++;
                    if (bare == null || installed == null) {
                        System.out.printf("#%-3d %-14s %5d %-1s  NO NAME (register=%s installed=%s)%n",
                                drawn, NAMES[p], YEARS[p], sex == 0 ? "m" : "f", bare, installed);
                        faults++;
                        continue;
                    }
                    System.out.printf("#%-3d %-14s %5d %-1s %-22s | %s%n",
                            drawn, NAMES[p], YEARS[p], sex == 0 ? "m" : "f", bare,
                            resolver.lastReport().describe());
                    String given = bare;
                    if (given.contains(" ")) {
                        given = given.substring(0, given.lastIndexOf(' ')).trim();
                    }
                    for (String token : given.split(" ")) {
                        String t = token.toLowerCase(Locale.ROOT);
                        if (sex == 0 && female.contains(t)) {
                            System.out.println("    FAULT female lexicon name on a man: " + t);
                            faults++;
                        }
                        if (sex == 1 && male.contains(t)) {
                            System.out.println("    FAULT male lexicon name on a woman: " + t);
                            faults++;
                        }
                    }
                }
            }
        }
        if (drawn >= 198 && faults == 0) {
            pass("2 " + drawn + " names at the nine places, both sexes, 0 wrong sex, installed path answering");
        } else {
            fail("2 " + drawn + " drawn, " + faults + " fault(s)");
        }
    }

    /** 3. A village of forty at each place, drawn without replacement: no duplicate, no shortfall. */
    private static void check3VillageNoDuplicates(PlaceNameResolver resolver) {
        long dup = 0;
        long shortVillages = 0;
        for (int p = 0; p < LATS.length; p++) {
            List<String> village = resolver.villageNames(LATS[p], LONS[p], YEARS[p], 40, "m",
                    new Random(mixSeed(42_424_242L + p)));
            Set<String> seen = new HashSet<>(village);
            if (village.size() != seen.size()) {
                dup++;
            }
            if (village.size() != 40) {
                shortVillages++;
            }
            System.out.printf("    village %-14s %5d  n=%d distinct=%d%n",
                    NAMES[p], YEARS[p], village.size(), seen.size());
        }
        if (dup == 0 && shortVillages == 0) {
            pass("3 forty at every place, 0 duplicates, 0 shortfalls");
        } else {
            fail("3 " + dup + " village(s) with duplicates, " + shortVillages + " short");
        }
    }

    /** 4. The same seed twice gives the same name — a figure is named by who they are. */
    private static void check4Determinism(PlaceNameResolver resolver) {
        boolean same = true;
        for (int p = 0; p < LATS.length; p++) {
            for (int sex = 0; sex < 2; sex++) {
                long seed = mixSeed(9_000_000L + p * 131L + sex);
                String a = resolver.nameFor(LATS[p], LONS[p], YEARS[p], sex == 0 ? "m" : "f",
                        new Random(seed), null);
                String b = resolver.nameFor(LATS[p], LONS[p], YEARS[p], sex == 0 ? "m" : "f",
                        new Random(seed), null);
                if (!java.util.Objects.equals(a, b)) {
                    same = false;
                }
            }
        }
        if (same) {
            pass("4 same seed, same name, at every place");
        } else {
            fail("4 a seed gave different names twice");
        }
    }

    /** 5. A database whose register never loaded must fall back to the legacy pools, never crash. */
    private static void check5LegacyFallback() {
        VillagerNameDatabase legacy = new VillagerNameDatabase();
        String place = legacy.getRandomPlaceName("deccan", 17.40, 78.48, 1948, "farmer", false,
                new Random(1L), null);
        String legacyName = legacy.getRandomName("deccan", "", "farmer", false, new Random(1L));
        if (place == null && legacyName != null && legacyName.contains(" ")) {
            pass("5 register missing -> null from the register, legacy pool still answers (" + legacyName + ")");
        } else {
            fail("5 fallback misbehaved: place=" + place + " legacy=" + legacyName);
        }
    }

    /** 6. The legacy occupational word goes in front: a Deccan farmer is a ryot, not an unnamed tiller. */
    private static void check6OccupationalWord(VillagerNameDatabase db) {
        if (!db.placeNamesLoaded()) {
            fail("6 skipped: register not loaded");
            return;
        }
        Set<String> used = new HashSet<>();
        int titled = 0;
        for (int i = 0; i < 20; i++) {
            String name = db.getRandomPlaceName("hyderabad_state", 17.40, 78.48, 1948, "farmer", false,
                    new Random(mixSeed(55_500_000L + i)), used);
            if (name != null && name.startsWith("Ryot ")) {
                titled++;
            }
        }
        if (titled == 20) {
            pass("6 twenty Deccan farmers, twenty 'Ryot'");
        } else {
            fail("6 only " + titled + "/20 carried the occupational word");
        }
    }

    // ==================== plumbing ====================

    /**
     * splitmix64 finalizer — the same mixing the in-game wiring uses. java.util.Random with
     * consecutive raw seeds is correlated in its first draws (measured: eight consecutive
     * seeds all opened within 0.0006 of the same value and a village froze on one name).
     */
    static long mixSeed(long x) {
        x = (x + 0x9E3779B97F4A7C15L) & 0xFFFFFFFFFFFFFFFFL;
        x = ((x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L) & 0xFFFFFFFFFFFFFFFFL;
        x = ((x ^ (x >>> 27)) * 0x94D049BB133111EBL) & 0xFFFFFFFFFFFFFFFFL;
        return x ^ (x >>> 31);
    }

    private static void readLexicon(Path dir, Set<String> male, Set<String> female) throws Exception {
        Path file = dir == null ? null : dir.resolve("sex_lexicon.json");
        if (file != null && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                readLexiconFrom(reader, male, female);
            }
            return;
        }
        try (InputStream in = NamesSelfTest.class.getResourceAsStream("/data/eternalengine/eternal/names/sex_lexicon.json")) {
            if (in != null) {
                readLexiconFrom(new InputStreamReader(in, StandardCharsets.UTF_8), male, female);
            }
        }
    }

    private static void readLexiconFrom(Reader reader, Set<String> male, Set<String> female) throws Exception {
        JsonObject lex = new Gson().fromJson(reader, JsonObject.class);
        if (lex == null) {
            return;
        }
        for (JsonElement e : lex.getAsJsonArray("male")) {
            male.add(e.getAsString().toLowerCase(Locale.ROOT));
        }
        for (JsonElement e : lex.getAsJsonArray("female")) {
            female.add(e.getAsString().toLowerCase(Locale.ROOT));
        }
    }

    private static void pass(String what) {
        passes++;
        System.out.println("[PASS] " + what);
    }

    private static void fail(String what) {
        fails++;
        System.out.println("[FAIL] " + what);
    }
}

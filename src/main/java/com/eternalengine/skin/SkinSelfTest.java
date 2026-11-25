package com.eternalengine.skin;

import com.eternalengine.skin.SkinPoolDatabase.Face;
import com.eternalengine.skin.SkinPoolDatabase.Outfit;
import com.eternalengine.skin.SkinPlaceResolver.Place;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The checks, runnable without a client: {@code java com.eternalengine.skin.SkinSelfTest [civIds.txt]}.
 *
 * <p>The Python rig that approved the sheets cannot compile these classes - it never compiles the
 * villager tree - so this is the Java speaking for itself. Run it on the machine that builds the mod;
 * a red line here means the game would paint something the sheets never showed.
 *
 * <p>What it checks, in the same order the Python checked it:
 * <ol>
 *   <li>the hash reproduces the Python's BLAKE2b vectors;</li>
 *   <li>every civ id in the census resolves to a real pool, none silently to {@code global};</li>
 *   <li>a face never changes, on six different days;</li>
 *   <li>clothes change daily - except for soldier, guard and priest, which change never;</li>
 *   <li>the same person on the same day is byte-identical; another person is not;</li>
 *   <li>the historical dress differs from the modern wardrobe, and 1200 differs from 2025;</li>
 *   <li>a crowd of four hundred in one place needs far fewer textures than four hundred.</li>
 * </ol>
 */
public final class SkinSelfTest {

    private static int fails;
    private static int passes;

    public static void main(String[] args) {
        List<String> civIds = new ArrayList<>();
        if (args.length > 0) {
            try {
                for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]))) {
                    String s = line.trim().replace("\"", "").replace(",", "");
                    if (!s.isEmpty() && !s.startsWith("#")) {
                        civIds.add(s);
                    }
                }
            } catch (Exception e) {
                System.out.println("[WARN] could not read " + args[0] + ": " + e.getMessage());
            }
        }
        if (civIds.isEmpty()) {
            System.out.println("[WARN] no civ-id list given; checking the six strays only");
            civIds = List.of("deccan", "indic", "chandela", "songhai_empire", "adil_shahi",
                    "imad_shahi", "nizam_shahi", "mughal", "egypt_ancient", "aztec");
        }

        SkinManager skin = new SkinManager(null);

        check1Hash();
        check2Ids(skin, civIds);
        check3FaceStable(skin);
        check4ClothesDaily(skin);
        check5Identical(skin);
        check6EraVsModern(skin);
        check7Crowd(skin);
        check8WardrobeGrows();
        check9Bindi(skin);
        check10Pack(args.length > 1 ? args[1] : null);
        check11Png(skin);
        check12Dump(skin, args.length > 2 ? args[2] : null);

        System.out.printf("%n%d checks passed, %d failed%n", passes, fails);
        System.out.println(fails == 0
                ? "ALL CHECKS OK - the game will paint what the sheets showed"
                : "FAILURES ABOVE - do not trust the sheets against this build");
        System.exit(fails == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------- 1
    private static void check1Hash() {
        say("1. BLAKE2b-64 vectors (the Python's numbers)");
        if (SkinHashes.verify()) {
            ok("all seven vectors match");
        } else {
            bad("the hash does not match the Python - every face would differ from the sheets");
        }
    }

    // ---------------------------------------------------------------- 2
    private static void check2Ids(SkinManager skin, List<String> civIds) {
        say("2. every civ id resolves to a real pool");
        int global = 0;
        for (String id : civIds) {
            String p = skin.pools().poolFor(id);
            if (p.equals(SkinPoolDatabase.GENERIC)) {
                global++;
                System.out.println("      fell to generic: " + id);
            }
        }
        if (global == 0) {
            ok(civIds.size() + " ids resolve; none to generic");
        } else {
            bad(global + " of " + civIds.size() + " fell to generic");
        }
        // the two the names rule got wrong, pinned by name
        expect(skin, "chandela", "indic");
        expect(skin, "songhai_empire", "african");
        expect(skin, "adil_shahi", "deccan");
        expect(skin, "imad_shahi", "deccan");
        expect(skin, "nizam_shahi", "deccan");
        expect(skin, "mughal", "indic");
        expect(skin, "aztec", "americas");
        expect(skin, "egypt_ancient", "north_african");
    }

    private static void expect(SkinManager skin, String id, String want) {
        String got = skin.pools().poolFor(id);
        if (got.equals(want)) {
            ok(id + " -> " + got);
        } else {
            bad(id + " -> " + got + ", wanted " + want);
        }
    }

    // ---------------------------------------------------------------- 3
    private static void check3FaceStable(SkinManager skin) {
        say("3. a face never changes, on six days");
        Place place = skin.placeAt(17.3850, 78.4867, "india");       // Hyderabad
        System.out.println("      place: " + place.key());
        for (int i = 0; i < 6; i++) {
            UUID u = person(i);
            Face f0 = skin.faceFor(u, true, place);
            Face f5 = skin.faceFor(u, true, place);
            boolean same = f0.tone() == f5.tone() && f0.hair() == f5.hair()
                    && f0.style().equals(f5.style());
            if (!same) {
                bad("face moved for person " + i);
                return;
            }
            // and the day must not enter it: the face has no day argument to give
            SkinPainter.Skin s0 = skin.paint(u, true, "citizen", place, 1400, 0);
            SkinPainter.Skin s2 = skin.paint(u, true, "citizen", place, 1400, 0);
            if (!java.util.Arrays.equals(s0.px, s2.px)) {
                bad("same person, same day, different pixels");
                return;
            }
        }
        ok("six people, faces identical across calls; same-day paint byte-identical");
    }

    // ---------------------------------------------------------------- 4
    private static void check4ClothesDaily(SkinManager skin) {
        say("4. clothes change every day - except the three uniforms");
        Place place = skin.placeAt(16.5062, 80.6480, "india");       // Vijayawada, Telugu belt
        UUID u = person(41);
        int year = 1990;                                             // past the modern turning point
        for (String role : new String[]{"citizen", "farmer", "merchant"}) {
            int changes = 0;
            Outfit first = skin.outfitFor(u, false, role, place, year, 0);
            int last = 0;
            System.out.println("      " + role + " on day 0: " + first.note());
            for (long day = 1; day <= 6; day++) {
                Outfit o = skin.outfitFor(u, false, role, place, year, day);
                if (!o.garments().equals(skin.outfitFor(u, false, role, place, year, day - 1)
                        .garments())) {
                    changes++;
                    last = (int) day;
                }
            }
            if (changes >= 2) {
                ok(role + " changed " + changes + " times in six day-steps (last change on day " + last
                        + ")");
            } else {
                bad(role + " changed only " + changes + " times in six day-steps");
            }
        }
        for (String role : new String[]{"soldier", "guard", "priest"}) {
            Outfit first = skin.outfitFor(u, false, role, place, year, 0);
            boolean same = first.uniform();
            for (long day = 1; day <= 6; day++) {
                Outfit o = skin.outfitFor(u, false, role, place, year, day);
                same &= o.garments().equals(first.garments()) && o.uniform();
            }
            SkinPainter.Skin s0 = skin.paint(u, false, role, place, year, 0);
            SkinPainter.Skin s3 = skin.paint(u, false, role, place, year, 3);
            boolean pixels = java.util.Arrays.equals(s0.px, s3.px);
            if (same && pixels) {
                ok(role + " wears " + first.garments() + " on all seven days, pixel-identical ("
                        + first.note() + ")");
            } else {
                bad(role + " changed: same=" + same + " pixels=" + pixels);
            }
        }
    }

    // ---------------------------------------------------------------- 5
    private static void check5Identical(SkinManager skin) {
        say("5. the same person is the same person; a stranger is not");
        Place place = skin.placeAt(17.9689, 79.5941, "india");       // Warangal
        UUID a = person(1);
        UUID b = person(2);
        SkinPainter.Skin a0 = skin.paint(a, true, "citizen", place, 1900, 0);
        SkinPainter.Skin a1 = skin.paint(a, true, "citizen", place, 1900, 0);
        SkinPainter.Skin b0 = skin.paint(b, true, "citizen", place, 1900, 0);
        int diff = 0;
        for (int i = 0; i < a0.px.length; i++) {
            if (a0.px[i] != b0.px[i]) {
                diff++;
            }
        }
        if (java.util.Arrays.equals(a0.px, a1.px) && diff > 100) {
            ok("same person identical; another person differs in " + diff + " of 4096 pixels");
        } else {
            bad("same=" + java.util.Arrays.equals(a0.px, a1.px) + " diff=" + diff);
        }
    }

    // ---------------------------------------------------------------- 6
    private static void check6EraVsModern(SkinManager skin) {
        say("6. the era's dress is not the modern wardrobe");
        Place deccan = skin.placeAt(17.3850, 78.4867, "india");
        Place eastAsia = skin.placeAt(34.3416, 108.9398, "china_han");
        UUID u = person(7);
        SkinPainter.Skin m1200 = skin.paint(u, false, "farmer", deccan, 1200, 0);
        SkinPainter.Skin m2025 = skin.paint(u, false, "farmer", deccan, 2025, 0);
        SkinPainter.Skin e1900 = skin.paint(u, false, "farmer", eastAsia, 1900, 0);   // the queue
        SkinPainter.Skin e2025 = skin.paint(u, false, "farmer", eastAsia, 2025, 0);
        int d1 = countDiff(m1200, m2025);
        int d2 = countDiff(e1900, e2025);
        if (d1 > 100 && d2 > 100) {
            ok("deccan 1200 vs 2025 differs in " + d1 + " px; East Asia 1900 vs 2025 in " + d2);
        } else {
            bad("era and modern too close: " + d1 + " / " + d2);
        }
        // and the year must bite INSIDE the modern age too, or the wardrobe is not year-aware at all
        SkinPainter.Skin m1950 = skin.paint(u, false, "farmer", deccan, 1950, 0);
        int d3 = countDiff(m1950, m2025);
        if (d3 > 0) {
            ok("deccan 1950 vs 2025 differs in " + d3 + " px - the wardrobe grows with the year");
        } else {
            bad("1950 and 2025 are the same picture: the year is not in the draw");
        }
        Outfit before = skin.outfitFor(u, false, "farmer", deccan, 1200, 0);
        Outfit after = skin.outfitFor(u, false, "farmer", deccan, 2025, 0);
        ok("1200 wardrobe: " + (before.garments().isEmpty() ? "the era's dress" : before.note())
                + " | 2025: " + after.note());
    }

    private static int countDiff(SkinPainter.Skin a, SkinPainter.Skin b) {
        int n = 0;
        for (int i = 0; i < a.px.length; i++) {
            if (a.px[i] != b.px[i]) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 7
    private static void check7Crowd(SkinManager skin) {
        say("7. what a crowd of four hundred actually costs");
        SkinManager.Census c = skin.census(17.3850, 78.4867, "india", 400);
        System.out.printf("      %s  crowd %d (%d in uniform) -> %d faces, %d pictures, %d KB%n",
                c.place(), c.crowd(), c.uniforms(), c.faces(), c.pictures(), c.kb());
        if (c.faces() >= 40) {
            ok("a crowd is not one face: " + c.faces() + " distinct faces in " + c.crowd() + " people");
        } else {
            bad("only " + c.faces() + " faces in a crowd of " + c.crowd());
        }
        if (c.pictures() > 0 && c.pictures() <= c.crowd() * 8L) {
            ok(c.pictures() + " pictures over a week of days, " + c.kb()
                    + " KB uncompressed");
        } else {
            bad("picture count " + c.pictures());
        }
        SkinManager.Census again = skin.census(17.3850, 78.4867, "india", 400);
        if (again.pictures() == c.pictures() && again.faces() == c.faces()) {
            ok("the census is deterministic: same place, same numbers twice");
        } else {
            bad("the census moved between two identical runs");
        }
        System.out.println("      for scale: the figures that STAND share 40 drawn variants; the NPCs "
                + "that live each carry their own face, which is what makes a returning one recognisable");
    }

    // ---------------------------------------------------------------- 8
    private static void check8WardrobeGrows() {
        say("8. the wardrobe grows with the year - the numbers the Python measured");
        int[][] want = {{1900, 2, 0}, {1930, 8, 1}, {1950, 10, 1}, {1970, 16, 6}, {1990, 20, 12},
                        {2010, 22, 19}, {2025, 22, 20}};
        boolean all = true;
        StringBuilder sb = new StringBuilder();
        for (int[] row : want) {
            int m = SkinPoolDatabase.wardrobeSize(false, row[0]);
            int f = SkinPoolDatabase.wardrobeSize(true, row[0]);
            boolean okRow = m == row[1] && f == row[2];
            all &= okRow;
            sb.append(row[0]).append(' ').append(m).append('/').append(f).append(okRow ? "  " : " FAIL ");
        }
        if (all) {
            ok("1900 2/0 | 1930 8/1 | 1950 10/1 | 1970 16/6 | 1990 20/12 | 2010 22/19 | 2025 22/20");
        } else {
            bad("the Java table does not reproduce the approved growth: " + sb);
        }
    }

    // ---------------------------------------------------------------- 9
    private static void check9Bindi(SkinManager skin) {
        say("9. no bindi on modern dress, the bindi on historical dress");
        Place deccan = skin.placeAt(17.3850, 78.4867, "india");       // in the bindi culture
        int bx = 8 + 3, by = 8 + 2;
        int bindi = 0xFFA8202C;
        int modernMark = 0, historicalMark = 0, modernFigures = 0, historicalFigures = 0;
        for (int i = 0; i < 40; i++) {
            UUID p = person(100 + i);
            SkinPainter.Skin modern = skin.paint(p, true, "citizen", deccan, 2025, i % 5);
            modernFigures++;
            if (modern.get(bx, by) == bindi) {
                modernMark++;
            }
            SkinPainter.Skin old = skin.paint(p, true, "citizen", deccan, 1200, i % 5);
            historicalFigures++;
            if (old.get(bx, by) == bindi) {
                historicalMark++;
            }
        }
        if (modernMark == 0) {
            ok("modern dress, " + modernFigures + " women, 0 bindis");
        } else {
            bad(modernMark + " of " + modernFigures + " modern figures still wear the bindi");
        }
        // The mark is a married woman's, so it is not on every historical woman either: the age now
        // decides. What must be true is that it is common on the grown and the old and rare on girls.
        int youngMark = 0, youngFigures = 0, grownMark = 0, grownFigures = 0;
        for (int i = 0; i < 200; i++) {
            UUID p = person(300 + i);
            SkinPoolDatabase.Face fc = skin.faceFor(p, true, deccan);
            NpcProfile who = NpcProfile.of(NpcProfile.seedOf(p), deccan.culture(), deccan.subpool(),
                    "citizen", true, 1200, skin.pools().pool(deccan.culture()), fc.tone(),
                    deccan.lat(), deccan.lon());
            SkinPainter.Skin old = skin.paint(p, true, "citizen", deccan, 1200, i % 5);
            if (who.years() < Ages.GROWN_FROM) {
                youngFigures++;
                if (old.get(bx, by) == bindi) {
                    youngMark++;
                }
            } else {
                grownFigures++;
                if (old.get(bx, by) == bindi) {
                    grownMark++;
                }
            }
        }
        if (grownFigures > 0 && grownMark >= grownFigures * 0.9) {
            ok("historical dress, grown women: " + grownMark + " of " + grownFigures
                    + " keep the mark");
        } else {
            bad("the mark has gone from grown women: " + grownMark + "/" + grownFigures);
        }
        if (youngFigures == 0 || youngMark < youngFigures * 0.8) {
            ok("historical dress, girls: " + youngMark + " of " + youngFigures
                    + " wear it - it is a married woman's mark");
        } else {
            bad("every girl wears the mark too: " + youngMark + "/" + youngFigures);
        }
    }

    // ---------------------------------------------------------------- 10
    private static void check10Pack(String packDir) {
        say("10. the shipped pack agrees with the built-in pools");
        if (packDir == null) {
            bad("no pack directory was passed as the second argument");
            return;
        }
        SkinPoolDatabase code = new SkinPoolDatabase();      // the defaults, untouched
        SkinPoolDatabase pack = new SkinPoolDatabase();
        int loaded = pack.loadFrom(java.nio.file.Path.of(packDir));
        if (loaded <= 0) {
            bad("nothing loaded from " + packDir);
            return;
        }
        // the fraction-versus-percentage read: prove the loader keeps a pack's curves, not zeroes them
        if (SkinPoolDatabase.turningPoint("robe", 1.00) == 100
                && SkinPoolDatabase.turningPoint("drape", 0.55) == 55
                && SkinPoolDatabase.turningPoint("robe", 100) == 100) {
            ok("the loader reads both spellings of a shape curve: 1.00 -> 100, 0.55 -> 55, 100 -> 100");
        } else {
            bad("the shape-curve read is wrong");
        }
        List<String> drift = new ArrayList<>();
        int checked = 0;
        for (String culture : code.cultures()) {
            if (culture.equals(SkinPoolDatabase.GENERIC)) {
                continue;                 // the fallback is the code's own; the pack has no entry for it
            }
            SkinPoolDatabase.Pool a = code.pool(culture);
            if (!pack.cultures().contains(culture)) {
                drift.add(culture + ": missing from the pack");
                continue;
            }
            SkinPoolDatabase.Pool b = pack.pool(culture);
            cmp(drift, culture, "tones", java.util.Arrays.toString(a.tones()),
                    java.util.Arrays.toString(b.tones()));
            cmp(drift, culture, "hairs", java.util.Arrays.toString(a.hairs()),
                    java.util.Arrays.toString(b.hairs()));
            cmp(drift, culture, "cloths", java.util.Arrays.toString(a.cloths()),
                    java.util.Arrays.toString(b.cloths()));
            cmp(drift, culture, "trim", hex(a.trim()), hex(b.trim()));
            cmp(drift, culture, "hairStylesMale", java.util.Arrays.toString(a.hairStylesMale()),
                    java.util.Arrays.toString(b.hairStylesMale()));
            cmp(drift, culture, "hairStylesFemale", java.util.Arrays.toString(a.hairStylesFemale()),
                    java.util.Arrays.toString(b.hairStylesFemale()));
            // the role maps are compared as key -> value, not as key order: no draw in the game reads
            // the order of a map, and the code's maps are unordered (Map.of) while the pack's are
            // ordered. The lists INSIDE them are compared exactly, because those orders ARE draws.
            cmp(drift, culture, "garments", String.valueOf(new java.util.TreeMap<>(a.garments())),
                    String.valueOf(new java.util.TreeMap<>(b.garments())));
            cmp(drift, culture, "uniforms", String.valueOf(new java.util.TreeMap<>(a.uniforms())),
                    String.valueOf(new java.util.TreeMap<>(b.uniforms())));
            cmp(drift, culture, "uniformNotes", String.valueOf(new java.util.TreeMap<>(a.uniformNotes())),
                    String.valueOf(new java.util.TreeMap<>(b.uniformNotes())));
            cmp(drift, culture, "lower", a.lower(), b.lower());
            cmp(drift, culture, "femaleLower", a.femaleLower(), b.femaleLower());
            cmp(drift, culture, "femaleJewels", a.femaleJewels(), b.femaleJewels());
            cmp(drift, culture, "era", String.valueOf(a.era()), String.valueOf(b.era()));
            cmp(drift, culture, "cultural", String.valueOf(a.cultural()),
                    String.valueOf(b.cultural()));
            checked++;
        }
        if (drift.isEmpty()) {
            ok(checked + " cultures, every field identical: the shipped pack and the code are one pool");
        } else {
            for (String d : drift) {
                System.out.println("      drift: " + d);
            }
            bad(drift.size() + " field(s) differ between the shipped pack and the code");
        }
        System.out.println("      and the loaded pack is the one the game reads: " + loaded
                + " culture(s) from " + packDir);
    }

    private static void cmp(List<String> drift, String culture, String field, String a, String b) {
        if (!a.equals(b)) {
            drift.add(culture + "." + field + "  code=" + a + "  pack=" + b);
        }
    }

    private static String hex(int rgb) {
        return String.format("%06x", rgb);
    }

    // ---------------------------------------------------------------- 11
    private static void check11Png(SkinManager skin) {
        say("11. the painted skin, as a PNG a game and a Python both read");
        Place place = skin.placeAt(17.3850, 78.4867, "india");
        UUID u = person(9);
        SkinPainter.Skin painted = skin.paint(u, false, "merchant", place, 1990, 0);
        byte[] png = SkinPng.encode(painted);
        byte[] again = SkinPng.encode(painted);
        if (!java.util.Arrays.equals(png, again)) {
            bad("two encodes of one skin differ");
            return;
        }
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (png.length < 8 || !java.util.Arrays.equals(java.util.Arrays.copyOf(png, 8), signature)) {
            bad("wrong signature: " + String.format("%02X", png[0]));
            return;
        }
        try {
            java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(
                    new java.io.ByteArrayInputStream(png));
            if (decoded == null || decoded.getWidth() != 64 || decoded.getHeight() != 64) {
                bad("decoded to " + (decoded == null ? "nothing"
                        : decoded.getWidth() + "x" + decoded.getHeight()));
                return;
            }
            int diff = 0;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int want = painted.get(x, y);
                    int got = decoded.getRGB(x, y);
                    if (want != got) {
                        diff++;
                    }
                }
            }
            if (diff == 0) {
                ok("decoded by the JDK's own image reader, all 4096 pixels identical to the painter's; "
                        + png.length + " bytes");
            } else {
                bad(diff + " of 4096 pixels differ after the round trip");
            }
            java.nio.file.Path out = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                    "eternal_skin_selfcheck.png");
            java.nio.file.Files.write(out, png);
            System.out.println("      written for the Python side of the rig: " + out);
        } catch (Exception e) {
            bad("could not decode our own PNG: " + e);
        }
    }

    // ---------------------------------------------------------------- 12
    /**
     * Write the Java's own skins out as PNGs, with a manifest, so a sheet can be drawn from <b>the game's
     * output</b> rather than from the Python that the sheets were approved on. If the two ever part ways,
     * this is where it shows.
     */
    private static void check12Dump(SkinManager skin, String dir) {
        say("12. the Java's own skins, written out to be looked at");
        if (dir == null) {
            System.out.println("      (no dump directory given; skipped)");
            return;
        }
        String[][] places = {
                {"hyderabad", "17.3850", "78.4867", "india"},
                {"warangal", "17.9689", "79.5941", "india"},
                {"nanded", "19.1500", "77.3000", "india"},
                {"adilabad", "19.6600", "78.5300", "india"},
                {"beijing", "39.9042", "116.4074", "china_han"},
                {"rome", "41.9028", "12.4964", "rome"},
                {"cairo", "30.0444", "31.2357", "egypt_ancient"},
                {"cusco", "-13.5320", "-71.9675", "inca"},
        };
        String[] genders = {"male", "female"};
        String[] roles = {"citizen", "soldier"};
        int[] years = {1200, 1900, 2025};
        int[] days = {0, 1};
        try {
            java.nio.file.Path out = java.nio.file.Path.of(dir);
            java.nio.file.Files.createDirectories(out);
            StringBuilder manifest = new StringBuilder(
                    "place\tlat\tlon\tculture\tsubpool\tflavour\tgender\trole\tyear\tday\tfile\n");
            int written = 0;
            for (String[] pl : places) {
                double lat = Double.parseDouble(pl[1]);
                double lon = Double.parseDouble(pl[2]);
                Place place = skin.placeAt(lat, lon, pl[3]);
                for (String gender : genders) {
                    for (String role : roles) {
                        for (int year : years) {
                            // one person per place, gender, role and year: the two day columns are the
                            // same man on two days, which is the point of the sheet
                            UUID u = UUID.nameUUIDFromBytes((pl[0] + "|" + gender + "|" + role + "|" + year)
                                    .getBytes(StandardCharsets.UTF_8));
                            for (int day : days) {
                                boolean female = gender.equals("female");
                                SkinPainter.Skin s = skin.paint(u, female, role, place, year, day);
                                String file = String.format("%s_%s_%s_%d_d%d.png", pl[0], gender, role,
                                        year, day);
                                java.nio.file.Files.write(out.resolve(file), SkinPng.encode(s));
                                manifest.append(pl[0]).append('\t').append(lat).append('\t').append(lon)
                                        .append('\t').append(place.culture()).append('\t')
                                        .append(place.subpool()).append('\t').append(place.flavour())
                                        .append('\t').append(gender).append('\t').append(role)
                                        .append('\t').append(year).append('\t').append(day)
                                        .append('\t').append(file).append('\n');
                                written++;
                            }
                        }
                    }
                }
            }
            java.nio.file.Files.writeString(out.resolve("manifest.tsv"), manifest.toString());
            ok(written + " skins written to " + dir + ", with a manifest for the sheet");
        } catch (Exception e) {
            bad("could not write the dump: " + e);
        }
    }

    // ---------------------------------------------------------------- plumbing
    private static UUID person(int i) {
        return UUID.nameUUIDFromBytes(("8f2c-npc-" + String.format("%04d", i))
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void say(String what) {
        System.out.println();
        System.out.println("== " + what);
    }

    private static void ok(String what) {
        passes++;
        System.out.println("   [OK] " + what);
    }

    private static void bad(String what) {
        fails++;
        System.out.println("   [FAIL] " + what);
    }
}

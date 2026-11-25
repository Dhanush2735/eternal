package com.eternalengine.skin;

import com.eternalengine.skin.SkinPoolDatabase.Face;
import com.eternalengine.skin.SkinPoolDatabase.Outfit;

import java.util.List;

/**
 * Paints a 64x64 skin from a face, an era and an outfit - the Java half of the Python that drew the
 * approved sheets.
 *
 * <h3>What this is, and what it is not</h3>
 * This is the painter the game runs: the same rules, the same layers, the same pool, painted into
 * memory instead of onto a sheet. The Python ({@code skins_v2..v6}, {@code skin_pools.py}) remains the
 * reference - it draws the 42-garment wardrobe, and this carries the {@link #has(String)} subset the
 * game needs, with the rest falling to a nearest shape and said so rather than pretended.
 *
 * <h3>Layer model (vanilla 64x64)</h3>
 * base layer: head 8,8 | body 20,20 | arms 44/36,20 | legs 4/20,20 - and their backs and sides
 * second layer: hat 40,8 | jacket 20,36 | sleeves 44/52,36 | trousers 4/4,36
 *
 * <h3>The rules being painted</h3>
 * <ul>
 *   <li>the face (tone, hair colour, hair style) comes from the person and the place, never the day;</li>
 *   <li>the clothes come from the day, except for a uniform, which is one cloth always;</li>
 *   <li>a year before the culture's modern turning point keeps the era's dress, drawn from the twelve
 *       turning points - sleeves, legs, outer garment, footwear, hat;</li>
 *   <li>the historical lower garment and the jewellery come from the pool, and the bindi is NOT drawn
 *       with the modern wardrobe (the user asked for that twice).</li>
 * </ul>
 */
public final class SkinPainter {

    /** The regions of a 64x64 skin: x, y, w, h. Named as the Python names them. */
    private static final java.util.Map<String, int[]> R = new java.util.HashMap<>();

    static {
        R.put("head_front", new int[]{8, 8, 8, 8});
        R.put("head_top", new int[]{8, 0, 8, 8});
        R.put("head_back", new int[]{24, 8, 8, 8});
        R.put("head_right", new int[]{0, 8, 8, 8});
        R.put("head_left", new int[]{16, 8, 8, 8});
        R.put("hat_front", new int[]{40, 8, 8, 8});
        R.put("hat_top", new int[]{40, 0, 8, 8});
        R.put("hat_back", new int[]{56, 8, 8, 8});
        R.put("hat_right", new int[]{32, 8, 8, 8});
        R.put("hat_left", new int[]{48, 8, 8, 8});
        R.put("body_front", new int[]{20, 20, 8, 12});
        R.put("body_back", new int[]{32, 20, 8, 12});
        R.put("body_right", new int[]{16, 20, 4, 12});
        R.put("body_left", new int[]{28, 20, 4, 12});
        R.put("jacket_front", new int[]{20, 36, 8, 12});
        R.put("jacket_back", new int[]{32, 36, 8, 12});
        R.put("jacket_right", new int[]{16, 36, 4, 12});
        R.put("jacket_left", new int[]{28, 36, 4, 12});
        R.put("arm_r_front", new int[]{44, 20, 4, 12});
        R.put("arm_r_back", new int[]{52, 20, 4, 12});
        R.put("arm_r_right", new int[]{40, 20, 4, 12});
        R.put("arm_r_left", new int[]{48, 20, 4, 12});
        R.put("arm_l_front", new int[]{36, 52, 4, 12});
        R.put("arm_l_back", new int[]{44, 52, 4, 12});
        R.put("arm_l_right", new int[]{32, 52, 4, 12});
        R.put("arm_l_left", new int[]{40, 52, 4, 12});
        R.put("sleeve_r_front", new int[]{44, 36, 4, 12});
        R.put("sleeve_r_back", new int[]{52, 36, 4, 12});
        R.put("sleeve_r_right", new int[]{40, 36, 4, 12});
        R.put("sleeve_r_left", new int[]{48, 36, 4, 12});
        R.put("sleeve_l_front", new int[]{52, 52, 4, 12});
        R.put("sleeve_l_back", new int[]{60, 52, 4, 12});
        R.put("sleeve_l_right", new int[]{48, 52, 4, 12});
        R.put("sleeve_l_left", new int[]{56, 52, 4, 12});
        R.put("leg_r_front", new int[]{4, 20, 4, 12});
        R.put("leg_r_back", new int[]{12, 20, 4, 12});
        R.put("leg_r_right", new int[]{0, 20, 4, 12});
        R.put("leg_r_left", new int[]{8, 20, 4, 12});
        R.put("leg_l_front", new int[]{20, 52, 4, 12});
        R.put("leg_l_back", new int[]{28, 52, 4, 12});
        R.put("leg_l_right", new int[]{16, 52, 4, 12});
        R.put("leg_l_left", new int[]{24, 52, 4, 12});
        R.put("trouser_r_front", new int[]{4, 36, 4, 12});
        R.put("trouser_r_back", new int[]{12, 36, 4, 12});
        R.put("trouser_r_right", new int[]{0, 36, 4, 12});
        R.put("trouser_r_left", new int[]{8, 36, 4, 12});
        R.put("trouser_l_front", new int[]{4, 52, 4, 12});
        R.put("trouser_l_back", new int[]{12, 52, 4, 12});
        R.put("trouser_l_right", new int[]{0, 52, 4, 12});
        R.put("trouser_l_left", new int[]{8, 52, 4, 12});
        // the faces the template gives every part but the painter used to leave as holes:
        // the underside of the head, the tops and bottoms of the body, the arms and the legs
        R.put("head_bottom", new int[]{16, 0, 8, 8});
        R.put("hat_bottom", new int[]{48, 0, 8, 8});
        R.put("body_top", new int[]{20, 16, 8, 4});
        R.put("body_bottom", new int[]{28, 16, 8, 4});
        R.put("arm_r_top", new int[]{44, 16, 4, 4});
        R.put("arm_r_palm", new int[]{48, 16, 4, 4});
        R.put("arm_l_top", new int[]{36, 48, 4, 4});
        R.put("arm_l_palm", new int[]{40, 48, 4, 4});
        R.put("leg_r_top", new int[]{4, 16, 4, 4});
        R.put("leg_r_sole", new int[]{8, 16, 4, 4});
        R.put("leg_l_top", new int[]{20, 48, 4, 4});
        R.put("leg_l_sole", new int[]{24, 48, 4, 4});
    }

    /** The garments this painter can draw. The pool filters its lists through this. */
    private static final java.util.Set<String> SHAPES = java.util.Set.of(
            "crew_tee", "graphic_tee", "polo", "button_down", "mock_turtleneck", "hoodie", "sweatshirt",
            "cardigan", "v_neck_tee", "tank", "camisole", "crop_top", "sheath_dress", "slip_dress",
            "trench", "bomber", "shacket", "blazer", "puffer_vest", "uniform_khaki", "uniform_green",
            "vestment", "jeans", "trousers", "cargo", "joggers", "shorts", "midi_skirt", "sneakers",
            "chelsea", "loafers", "cap", "bucket_hat", "crossbody", "shoulder_bag",
            // the women's names from the user's own list: each is drawn as its cut of a shape above,
            // not aliased away - the name in the wardrobe is the name on the cloth
            "wide_jeans", "mom_denim", "pleated_w", "biker_shorts", "dad_sneakers", "oversized_shirt",
            "ribbed_cardigan", "trench_w", "oversized_blazer", "cropped_puffer");

    private SkinPainter() {
    }

    /** True when this garment has a shape here. The pool will not offer one that is not drawn. */
    public static boolean has(String garment) {
        return garment != null && SHAPES.contains(garment);
    }

    /** The canvas: 64x64, ARGB, 0 = transparent. */
    public static final class Skin {
        public final int[] px = new int[64 * 64];

        public int get(int x, int y) {
            return x < 0 || y < 0 || x > 63 || y > 63 ? 0 : px[y * 64 + x];
        }

        public void put(int x, int y, int rgb) {
            if (x >= 0 && y >= 0 && x < 64 && y < 64) {
                px[y * 64 + x] = 0xFF000000 | (rgb & 0xFFFFFF);
            }
        }

        public void putA(int x, int y, int argb) {
            if (x >= 0 && y >= 0 && x < 64 && y < 64) {
                px[y * 64 + x] = argb;
            }
        }

        public void clear(String region) {
            int[] r = R.get(region);
            for (int j = 0; j < r[3]; j++) {
                for (int i = 0; i < r[2]; i++) {
                    px[(r[1] + j) * 64 + (r[0] + i)] = 0;
                }
            }
        }

        /** Fill with the official shading: outer columns darker, top row lighter. */
        public void shadeFill(String region, int rgb, int rows) {
            int[] r = R.get(region);
            for (int j = 0; j < Math.min(rows, r[3]); j++) {
                for (int i = 0; i < r[2]; i++) {
                    int c = rgb;
                    if (i == 0 || i == r[2] - 1) {
                        c = SkinPoolDatabase.shade(rgb, -14);
                    } else if (j == 0) {
                        c = SkinPoolDatabase.shade(rgb, 10);
                    }
                    put(r[0] + i, r[1] + j, c);
                }
            }
        }

        public void band(String region, int row, int rgb) {
            int[] r = R.get(region);
            if (row < 0 || row >= r[3]) {
                return;
            }
            for (int i = 0; i < r[2]; i++) {
                put(r[0] + i, r[1] + row, rgb);
            }
        }

        public void column(String region, int col, int rgb, int fromRow, int toRow) {
            int[] r = R.get(region);
            if (col < 0 || col >= r[2]) {
                return;
            }
            for (int j = Math.max(0, fromRow); j < Math.min(toRow, r[3]); j++) {
                put(r[0] + col, r[1] + j, rgb);
            }
        }
    }

    // ------------------------------------------------------------------ curves over years
    /** A curve read at a year: straight lines between the points, held flat at both ends. */
    private static double track(double[][] pts, int year) {
        if (year <= pts[0][0]) {
            return pts[0][1];
        }
        if (year >= pts[pts.length - 1][0]) {
            return pts[pts.length - 1][1];
        }
        for (int i = 0; i + 1 < pts.length; i++) {
            double y0 = pts[i][0], v0 = pts[i][1], y1 = pts[i + 1][0], v1 = pts[i + 1][1];
            if (year >= y0 && year <= y1) {
                return y1 == y0 ? v1 : v0 + (v1 - v0) * (year - y0) / (y1 - y0);
            }
        }
        return pts[pts.length - 1][1];
    }

    /** The era's cut of one person: rows of arm, rows of leg, length of the outer garment, footwear. */
    public record Cut(int arm, int leg, int outer, String foot, double drape) {}

    /** The same curves as {@code skins_v5.py}, built from the pool's twelve turning points. */
    public static Cut cutFor(SkinPoolDatabase.Pool p, boolean female, int year) {
        return cutFor(p, female, year, -1);
    }

    /**
     * The cut of one person's clothes, leaning on how old they are.
     *
     * <p>A young labourer has his sleeves rolled and his outer garment short or none at all; an old
     * man is covered to the wrist and wrapped to the shin, and the cloth hangs looser on him. This is
     * a change of the same garment, not a different wardrobe: the place and the year still decide
     * what is worn, and the age decides how it is worn.
     *
     * @param years the person's age, or -1 for "no age known" - the place's own cut, untouched
     */
    public static Cut cutFor(SkinPoolDatabase.Pool p, boolean female, int year, int years) {
        int classical = p.era("classical"), imperial = p.era("imperial"), early = p.era("early");
        int colonial = p.era("colonial"), modern = p.era("modern");
        double robe = p.era("robe") / 100.0, drape = p.era("drape") / 100.0;
        double peak = Math.min(12.0, 12.0 * robe);

        double[][] arm, leg, outer, dr;
        if (!female) {
            arm = new double[][]{{-3000, 0}, {classical - 200, 1}, {imperial, 3}, {early, 4},
                    {colonial + 50, 4}, {modern, 3}, {modern + 25, 2}, {modern + 45, p.era("arm_end_m")}};
            leg = new double[][]{{-3000, 4}, {classical, 5}, {imperial, 7}, {early, 9}, {colonial, 10},
                    {2100, 10}};
            outer = new double[][]{{-3000, 0}, {classical - 300, 2}, {imperial, 5}, {early, peak},
                    {colonial + 40, peak}, {modern, peak * 0.5}, {modern + 30, peak * 0.15},
                    {2100, peak * 0.15}};
            dr = new double[][]{{-3000, 0}, {2100, 0}};
        } else {
            arm = new double[][]{{-3000, 0}, {classical, 1}, {imperial, 2}, {early, 4}, {modern, 4},
                    {modern + 25, p.era("arm_end_f")}, {2100, p.era("arm_end_f")}};
            leg = new double[][]{{-3000, 9}, {imperial, 10}, {2100, 10}};
            outer = new double[][]{{-3000, 0}, {classical, 1}, {imperial, 3}, {early, peak * 0.7},
                    {colonial + 20, peak * 0.7}, {modern, peak * 0.3}, {2100, peak * 0.2}};
            dr = new double[][]{{-3000, 0.0}, {classical, drape * 0.5}, {imperial, drape * 0.8},
                    {early, drape}, {colonial, drape}, {modern + 20, drape * 0.8}, {2100, drape}};
        }
        double revival = p.era("revival") / 100.0;
        if (revival > 0) {
            int revYear = (p.era("imperial") < 1100) ? 1979 : 1965;
            double a0 = track(arm, revYear), d0 = track(dr, revYear), o0 = track(outer, revYear);
            arm = append(arm, revYear, a0, revYear + 20, a0 + revival, 2100, a0 + revival);
            dr = append(dr, revYear, d0, revYear + 20, Math.min(1.0, d0 + revival * 0.5), 2100,
                    Math.min(1.0, d0 + revival * 0.5));
            outer = append(outer, revYear, o0, 2100, o0 + revival * peak * 0.15);
        }
        // the foot steps, it does not ramp: a place changes its shoes over a generation
        int shoeIn = colonial;
        int bootIn = Math.max(shoeIn, 1790);
        int bootOut = modern >= 1915 ? Math.max(bootIn + 80, modern + 10) : bootIn + 40;
        double[][] foot = {{-3000, 0}, {shoeIn - 120, 0}, {shoeIn - 40, 1}, {bootIn, 1},
                {bootIn + 40, 2}, {bootOut, 2}, {bootOut + 40, 1}, {2100, 1}};
        int step = (int) Math.max(0, Math.min(2, Math.round(track(foot, year))));
        String kind = new String[]{"sandal", "shoe", "boot"}[step];
        int armRows = (int) Math.round(track(arm, year));
        int outerRows = (int) Math.round(track(outer, year));
        double drapeNow = track(dr, year);
        if (years >= 0) {
            boolean young = years < Ages.GROWN_FROM;
            boolean old = years >= Ages.OLD_FROM;
            if (young) {              // sleeves up, the outer garment short or put by
                armRows -= 2;
                outerRows -= 2;
                drapeNow -= 0.10;
            } else if (old) {         // covered to the wrist, wrapped to the shin, hanging looser
                armRows += 2;
                outerRows += 2;
                drapeNow += 0.18;
            }
            armRows = Math.max(0, Math.min(11, armRows));
            outerRows = Math.max(0, Math.min(12, outerRows));
            drapeNow = Math.max(0.0, Math.min(1.0, drapeNow));
        }
        return new Cut(armRows, (int) Math.round(track(leg, year)), outerRows, kind, drapeNow);
    }

    /**
     * The same cut, with the trade leaning it.
     *
     * <p>The era decides the shape of the clothes; the trade decides how far down the arm and the leg
     * they come. A scholar, a scribe, an advisor and a priest keep their cloth long, because the work is
     * done sitting and the cloth is a sign of the standing; a farmer, a herder and an artisan have their
     * sleeves pushed up and their hems kilted out of the mud and the fire. Without this, six of the
     * thirteen trades were dressed by exactly the same numbers and came out as the same picture.
     *
     * @param role the trade, or null for "no trade known" - the era's own cut, untouched
     */
    public static Cut cutFor(SkinPoolDatabase.Pool p, boolean female, int year, int years, String role) {
        Cut c = cutFor(p, female, year, years);
        if (role == null) {
            return c;
        }
        int arm = c.arm(), leg = c.leg(), outer = c.outer();
        double drape = c.drape();
        switch (role.toLowerCase(java.util.Locale.ROOT)) {
            case "scholar", "scribe", "advisor", "elder", "priest", "healer", "clerk", "teacher" -> {
                arm += 1; outer += 2; drape += 0.08;
            }
            case "farmer", "herder", "artisan", "labourer", "peasant", "smith", "weaver", "potter",
                 "carpenter", "shepherd", "drover" -> {
                arm -= 1; outer -= 2; leg -= 2; drape -= 0.08;
            }
            case "merchant", "trader", "pedlar", "citizen", "townsman" -> outer += 1;
            default -> { }
        }
        return new Cut(Math.max(0, Math.min(11, arm)), Math.max(2, Math.min(12, leg)),
                Math.max(0, Math.min(12, outer)), c.foot(), Math.max(0.0d, Math.min(1.0d, drape)));
    }

    private static double[][] append(double[][] pts, double... add) {
        java.util.List<double[]> out = new java.util.ArrayList<>(List.of(pts));
        for (int i = 0; i + 1 < add.length; i += 2) {
            out.add(new double[]{add[i], add[i + 1]});
        }
        out.sort(java.util.Comparator.comparingDouble(a -> a[0]));
        return out.toArray(new double[0][]);
    }

    // ------------------------------------------------------------------ the person
    /**
     * Paint one person.
     *
     * @param p       the culture's pool
     * @param face    the face, from the person and the place
     * @param outfit  the clothes for this day, possibly empty (the era's dress applies)
     * @param colours top, outer, bottom, trim
     * @param year    the year he holds - the era's cut follows it
     * @param role    his trade, for the mark on the cloth
     */
    public static Skin paint(SkinPoolDatabase.Pool p, Face face, Outfit outfit, int[] colours,
                             int year, String role, boolean female) {
        // no seed given: take one from the face, so an old caller still paints a stable person
        long seed = ((long) face.tone() << 32) ^ (face.hair() * 0x9E3779B1L) ^ face.style().hashCode();
        return paint(p, face, outfit, colours, year, role, female, seed, null);
    }

    /**
     * Paint one person, with every part of them that varies.
     *
     * @param seed the person's own number - the same person always comes out the same, and two people
     *             of the same town, year and trade come out different
     */
    public static Skin paint(SkinPoolDatabase.Pool p, Face face, Outfit outfit, int[] colours,
                             int year, String role, boolean female, long seed, String subpool) {
        return paint(p, face, outfit, colours, year, role, female, seed, subpool, true);
    }

    /**
     * The same, with the accessories switched on or off.
     *
     * <p>Off is what a customizer's toggle needs, and what a caller needs to get back the person
     * without the sash and the pouch. Nothing else about the person changes: the same seed gives the
     * same face, the same cloth and the same jewellery.
     */
    public static Skin paint(SkinPoolDatabase.Pool p, Face face, Outfit outfit, int[] colours,
                             int year, String role, boolean female, long seed, String subpool,
                             boolean accessories) {
        return paint(p, face, outfit, colours, year, role, female, seed, subpool,
                accessories ? ACC_ALL : ACC_NONE);
    }

    /**
     * The same, with any single accessory left on or off.
     *
     * @param accessories a mask of the {@code A_*} and {@code J_*} bits above; {@link #ACC_ALL} is
     *                    everything, {@link #ACC_NONE} is nothing. A uniform trade wears none of
     *                    them whatever is asked for.
     */
    public static Skin paint(SkinPoolDatabase.Pool p, Face face, Outfit outfit, int[] colours,
                             int year, String role, boolean female, long seed, String subpool,
                             int accessories) {
        return paint(p, face, outfit, colours, year, role, female, seed, subpool, accessories,
                0.0d, 0.0d);
    }

    /**
     * The same, told where the person stands: the ground and the year are what the life table answers
     * for, so the age - and with it the grey hair, the beard and the heaviness of the face - follows
     * the place rather than a hash.
     */
    public static Skin paint(SkinPoolDatabase.Pool p, Face face, Outfit outfit, int[] colours,
                             int year, String role, boolean female, long seed, String subpool,
                             int accessories, double lat, double lon) {
        Skin s = new Skin();
        // ---- steps 1 and 2: the record, and the classification
        NpcProfile npc = NpcProfile.of(seed, cultureOf(p), subpool, role, female, year, p, face.tone(),
                lat, lon);
        SkinVariation v = npc.variation();
        int years = npc.years();                  // the person's real age: dress leans on it
        String style = npc.style();
        int tone = face.tone();
        Cut cut = cutFor(p, female, year, years, role);
        // "modern dress" means clothes this painter can actually draw. A Deccan farmer in 1400 wears a
        // kurta and a kaftan, neither of which has a shape here, and the old test called that a modern
        // outfit and fell back to a t-shirt: shorts sleeves, and the era's cut thrown away. When nothing
        // worn has a shape, the era's own dress is the honest answer.
        boolean modern = outfit != null && outfit.garments().stream().anyMatch(SkinPainter::has);
        int top = colours[0], outer = colours[1], bottom = colours[2], trim = colours[3];
        // a tunic and a pair of trousers are never cut from the same cloth. The palette can hand back
        // the same colour for both - a sub-pool gives one cloth for the whole body - and then the legs
        // dissolve into the tunic and the person is one flat colour from shoulder to ankle. Push the
        // trousers away from the tunic instead, so the seam at the waist is always visible.
        if (Math.abs(lum(top) - lum(bottom)) < 34) {
            bottom = SkinPoolDatabase.shade(bottom, lum(top) > lum(bottom) ? -46 : 46);
        }

        // the same cloth, worn for different lengths of time: a young person's dye is strong and deep,
        // an old person's has been washed pale over fifty years. This is not a change of dye lot - the
        // cloth is the same colour the place and the year chose - it is the wear on it, and it is the
        // largest thing about the body that says how old the person is.
        top = worn(top, years);
        outer = worn(outer, years);
        bottom = worn(bottom, years);
        trim = worn(trim, years);

        // ---- the body bare: tone everywhere, with the official shading
        for (String r : new String[]{"head_front", "head_back", "head_right", "head_left", "head_top",
                "body_front", "body_back", "body_right", "body_left", "arm_r_front", "arm_r_back",
                "arm_r_right", "arm_r_left", "arm_l_front", "arm_l_back", "arm_l_right", "arm_l_left",
                "leg_r_front", "leg_r_back", "leg_r_right", "leg_r_left", "leg_l_front", "leg_l_back",
                "leg_l_right", "leg_l_left"}) {
            s.shadeFill(r, tone, R.get(r)[3]);
        }
        face(s, face, tone, female, p, v);

        if (modern) {
            bottom(s, outfit.garments(), colours, bottom);
            topAndOuter(s, outfit.garments(), colours, top, outer, female);
            feet(s, outfit.garments(), tone, cut);
            if (outfit.uniform()) {
                uniform(s, outfit.garments(), tone);
            }
            mark(s, role, trim, outfit.garments());
        } else {
            eraDress(s, p, cut, tone, top, bottom, trim, female, year, role, seed, npc.subpool());
            mark(s, role, trim, null);      // the trade's mark belongs to the trade, not to the century
            // a uniform is one cloth always, in any century: a soldier does not wear the town's cloth
            // because the town's cloth happened to be chosen for him that morning
            if (outfit != null && outfit.uniform()) {
                uniform(s, outfit.garments(), tone);
            }
        }
        // the weave of the cloth the person is wearing, then the build, then the jewellery: after the
        // clothes and before the hair, so a necklace lies on the chest and not under it
        textile(s, v, top, outer, bottom, years);
        build(s, v);
        jewels(s, v, p, female, trim, modern, accessories, years);
        // the sash, the shoulder cloth, the pouch: over the clothes and the jewellery, under the hair
        boolean uniform = outfit != null && outfit.uniform();
        int clothBits = accessories
                & (A_SASH | A_SHOULDER | A_POUCH | A_HEADBAND | A_ARMLET | A_ANKLE | A_STAFF);
        accessories(s, v, p, female, trim, outer, uniform ? ACC_NONE : clothBits, years, role);
        // a working trade wears its apron: a plain panel of sacking over the front, from the collar to
        // the hem. It is the one thing on the body that a farmer, a herder and a villager never have,
        // and it is what makes an artisan read as an artisan from across a street.
        if (!modern && role != null && (accessories & A_SASH) != 0) {
            apron(s, role, trim, outer);
        }
        // the hair goes on last, over the clothes; a hat then covers the hair where there is one
        hair(s, face, female, p, v);
        // the head is covered by the wardrobe's own cap where it names one, and otherwise by the
        // culture's wrap - in every century, not only the historical ones. A priest or a soldier is
        // dressed by the modern path because his uniform has a shape, and he was losing his headwear
        // because of it: the wrap belongs to the person and the place, not to the code path.
        if (modern && (outfit.garments().contains("cap") || outfit.garments().contains("bucket_hat"))) {
            cap(s, colours[2], outfit.garments().contains("bucket_hat"));
        } else {
            headwear(s, p, face, female, top, trim, years);  // turban, wrap, hood or veil, by the pool
            hatFromCatalogue(s, cultureOf(p), subpool, year, female, seed, top, trim);
        }
        // last of all, the faces that were holes: the underside of the head, the shoulders, the
        // palms, the top of each leg and the sole of each foot. Nothing on the model is left unpainted.
        edges(s);
        // and then the layers are made visible. The vanilla model shows this person's main layer,
        // and the hat layer only while a helmet is worn - they never wear one. Everything painted
        // for the hat layer or for the second layer (jacket, sleeves, trousers) would therefore
        // stay in the texture and never reach the screen: the hat, the veil, the bun, the lashes,
        // the coat and its sleeves, the wide shoulder, the flared hem, and the long hair down the
        // back. Flattening copies each layer onto the face the model actually shows.
        flattenLayers(s);
        // the bindi follows the garment actually worn, never the calendar: modern dress means no mark,
        // historical dress means the mark - the user asked for exactly this, twice. It goes on after
        // the layers, because a headwrap's edge lies on the very row the mark sits on, and the mark
        // is the woman's, not the cloth's.
        if (!modern && female && p.femaleJewels().contains("bindi") && (accessories & J_BINDI) != 0
                && (years < 0 || years >= Ages.GROWN_FROM
                        || SkinHashes.pick("bindi|" + seed, 100) < 40)) {
            s.put(8 + 3, 8 + 2, 0xA8202C);        // the mark on the brow: historical dress only
        }
        // the nose ring tells the same story one row down: a woman's lashes ride the hat layer's outer
        // eye corners, and the ring sits at exactly that corner, so it goes on after the layers the way
        // the mark does - the ring is the woman's, not the lash's.
        if (female && p.femaleJewels().contains("nose") && (accessories & J_NOSE) != 0
                && SkinHashes.pick("nose|" + v.seed(), 100) < leaned(100, "nose", years)) {
            s.put(8 + 6, 8 + 5, 0xD8B24A);        // the ring at the left nostril, over the lash
        }
        return s;
    }


    // ------------------------------------------------------------------ the weave of the cloth
    /**
     * Dress the cloth in the weave the place knows.
     *
     * <p>A tunic from Rome and a tunic from the Deccan are the same shape and were never the same cloth:
     * one is a plain weave with two stripes down it, the other is block printed. This puts the weave on
     * every cloth pixel the person is already wearing - it does not know which garment was chosen, and
     * does not need to.
     *
     * <p>Which weaves a place knows is a fact about the place ({@link SkinVariation#PATTERNS}); how
     * strongly the person's cloth shows it is theirs.
     */
    private static void textile(Skin s, SkinVariation v, int top, int outer, int bottom,
                                int years) {
        String[] clothRegions = {"body_front", "body_back", "body_right", "body_left",
            "jacket_front", "jacket_back", "jacket_right", "jacket_left",
            "arm_r_front", "arm_r_back", "arm_r_right", "arm_r_left",
            "arm_l_front", "arm_l_back", "arm_l_right", "arm_l_left",
            "sleeve_r_front", "sleeve_r_back", "sleeve_r_right", "sleeve_r_left",
            "sleeve_l_front", "sleeve_l_back", "sleeve_l_right", "sleeve_l_left",
            "leg_r_front", "leg_r_back", "leg_r_right", "leg_r_left",
            "leg_l_front", "leg_l_back", "leg_l_right", "leg_l_left"};
        // the print fades before the cloth does: a young person's weave still shows its pattern, an old
        // person's has gone soft with washing, which is the same story the colour tells
        double show = years < 0 ? 1.0d
                : years < Ages.GROWN_FROM ? 1.15d
                : years >= Ages.OLD_FROM ? 0.45d
                : 1.0d - 0.35d * (years - Ages.GROWN_FROM) / (double) (Ages.OLD_FROM - Ages.GROWN_FROM);
        int strength = (int) Math.round(v.patternStrength() * 7 * show);
        for (String reg : clothRegions) {
            int[] r = R.get(reg);
            if (r == null) {
                continue;
            }
            for (int j = 0; j < r[3]; j++) {
                for (int i = 0; i < r[2]; i++) {
                    int c = s.get(r[0] + i, r[1] + j);
                    if ((c >>> 24) == 0) {
                        continue;
                    }
                    int rgb = c & 0xFFFFFF;
                    // only the cloth of this garment is woven; skin, trim and the person's own details
                    // are left exactly as they were
                    if (!near(rgb, top) && !near(rgb, outer) && !near(rgb, bottom)) {
                        continue;
                    }
                    int d = weave(v.pattern(), i, j, strength);
                    if (d != 0) {
                        s.put(r[0] + i, r[1] + j, SkinPoolDatabase.shade(rgb, d));
                    }
                }
            }
        }
    }

    /** One pixel of one weave, as a shade delta. */
    private static int weave(int pattern, int i, int j, int strength) {
        return switch (pattern) {
            case 1 -> i % 3 == 0 ? -strength : 0;                                   // a stripe down the cloth
            case 2 -> j % 4 == 0 ? -strength + 2 : 0;                               // a band across it
            case 3 -> (i % 2 == 0 && j % 2 == 0) ? -strength : 0;                   // a check
            case 4 -> (i % 3 == 1 && j % 3 == 1) ? strength : (i + j) % 7 == 0 ? -strength : 0;
            case 5 -> (j % 4 == 0 && (i + j) % 3 != 0) ? -strength + 1 : 0;         // ikat: a broken stripe
            case 6 -> (i + j) % 4 == 0 ? strength - 1 : 0;                          // brocade
            case 7 -> (int) Math.round(Math.sin((i + j) * 0.9) * 4) - 2;            // a wave
            case 8 -> ((i + j) % 4 == 0 || Math.floorMod(i - j, 4) == 0) ? -strength : 0;
            case 9 -> (i + j) % 2 == 0 ? -strength + 3 : 0;                          // the weave itself
            case 10 -> ((i / 2) + (j / 2)) % 2 == 0 && (i + j) % 3 == 0 ? -strength : 0;
            default -> 0;
        };
    }

    /** Is this pixel the cloth in question, give or take the official shading? */
    private static boolean near(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= 26
                && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= 26
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= 26;
    }

    // ------------------------------------------------------------------ the build
    /**
     * The build: a broad person has a shoulder where a slim one has a gap.
     *
     * <p>It works on the second layer, which is the only place a 64x64 skin can be wider than the model
     * it sits on: a broad build carries the cloth one column out over the arm, a slim build lets the arm
     * show. Nothing about the model changes, so the crowd is untouched.
     */
    private static void build(Skin s, SkinVariation v) {
        if (v.build() == 2) {
            for (String side : new String[]{"r", "l"}) {
                int[] sl = R.get("sleeve_" + side + "_front");
                int[] sb = R.get("sleeve_" + side + "_back");
                int[] arm = R.get("arm_" + side + "_front");
                if (sl != null && arm != null) {
                    for (int j = 0; j < 2; j++) {          // the yoke over the shoulder
                        s.put(sl[0], sl[1] + j, s.get(arm[0], arm[1] + j));
                        if (sb != null) {
                            s.put(sb[0] + 3, sb[1] + j, s.get(arm[0] + 3, arm[1] + j));
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the jewellery
    /**
     * The jewellery the culture's own list names: a bindi, a nose ring, bangles, earrings, a necklace.
     *
     * <p>The pool has carried the list all along - {@code bindi_nose_bangle}, {@code earring_veil} - and
     * the painter used it for one thing: the bindi, on historical dress only, which was the owner's own
     * instruction. The rest of the list is drawn here, in the metal the place uses, and a person wears
     * some of it and not all, which is what jewellery is like.
     */
    private static void jewels(Skin s, SkinVariation v, SkinPoolDatabase.Pool p, boolean female,
                               int trim, boolean modern, int mask) {
        jewels(s, v, p, female, trim, modern, mask, -1);
    }

    private static void jewels(Skin s, SkinVariation v, SkinPoolDatabase.Pool p, boolean female,
                               int trim, boolean modern, int mask, int years) {
        String list = p.femaleJewels();
        if (list == null) {
            return;
        }
        int gold = 0xD8B24A;
        int silver = 0xC9C6BE;
        int bead = blend(trim, 0xB03A3A, 0.5);
        int metal = (v.seed() % 3 == 0) ? silver : gold;
        // the owner's rule: modern dress carries no mark, historical dress does. The bindi follows the
        // garment, not the culture - so a Deccan woman in 2025 does not wear one because her
        // grandmother did.
        // the age decides what of this a person still wears: a girl has more bangles and fewer marks
        // than her grandmother, and a young man's armlet is not an old man's
        boolean young = years >= 0 && years < Ages.GROWN_FROM;
        boolean bindi = female && list.contains("bindi") && !modern && (mask & J_BINDI) != 0
                && (!young || SkinHashes.pick("bindi|" + v.seed(), 100) < 40);
        boolean nose = list.contains("nose");
        boolean bangle = list.contains("bangle");
        boolean ear = list.contains("earring");
        // whether this place wears a necklace at all is a fact about the place, not about which of
        // its ornaments the caller happens to have switched on: the necklace used to be drawn only
        // if the earrings or the bindi were *also* switched on, so asking for it alone gave nothing
        boolean wearsNecklace = v.jewelSet() > 0
                && (ear || (female && list.contains("bindi") && !modern));
        // the bindi: historical dress only, the owner's instruction, and a woman's own choice
        if (bindi && v.age() > 0) {
            s.put(8 + 3, 8 + 2, 0xA8202C);
        }
        // the necklace: on the chest, under the collar, three pixels of metal or bead
        if (wearsNecklace && (mask & J_NECKLACE) != 0
                && SkinHashes.pick("necklace|" + v.seed(), 100)
                        < leaned(female ? 100 : 45, "necklace", years)) {
            int c = v.jewelSet() == 3 ? bead : metal;
            for (int i = 3; i < 5; i++) {
                s.put(20 + i, 20 + 1, c);
            }
            s.put(20 + 2, 20 + 1, SkinPoolDatabase.shade(c, -22));
            s.put(20 + 5, 20 + 1, SkinPoolDatabase.shade(c, -22));
        }
        // the earrings: a pixel at each ear, in the metal the place uses. They belong on the jaw line
        // of the front of the head, where the ear is: the sides of the head are the hair's, and a pixel
        // put there is painted over by the hair every single time, which is how they went unseen.
        if (ear && (mask & J_EARRING) != 0
                && SkinHashes.pick("earring|" + v.seed(), 100) < leaned(100, "earring", years)) {
            s.put(8 + 0, 8 + 5, metal);
            s.put(8 + 7, 8 + 5, metal);
        }
        // the nose ring: a single ring at the left nostril
        if (nose && female && (mask & J_NOSE) != 0
                && SkinHashes.pick("nose|" + v.seed(), 100) < leaned(100, "nose", years)) {
            s.put(8 + 6, 8 + 5, gold);
        }
        // the bangles: a row of metal at each wrist, and a ring at each ankle
        if (bangle && female && (mask & J_BANGLE) != 0
                && SkinHashes.pick("bangle|" + v.seed(), 100) < leaned(62, "bangle", years)) {
            for (String side : new String[]{"r", "l"}) {
                int[] arm = R.get("arm_" + side + "_front");
                int[] leg = R.get("leg_" + side + "_front");
                if (arm != null) {
                    for (int i = 0; i < 4; i++) {
                        s.put(arm[0] + i, arm[1] + 9, (i % 2 == 0) ? metal : SkinPoolDatabase.shade(metal, -18));
                    }
                }
                if (leg != null) {
                    for (int i = 0; i < 4; i++) {
                        s.put(leg[0] + i, leg[1] + 9, SkinPoolDatabase.shade(metal, -10));
                    }
                }
            }
        }
    }

    /**
     * The accessories: the things a person puts on that are not clothes and not jewellery - the sash
     * across the chest, the cloth over one shoulder, the pouch at the hip, the band on the forehead,
     * the armlet, the band at the ankle.
     *
     * <p>Which ones a place wears, and how often, is a fact about the place; whether <i>this</i> person
     * wears one is his own business, drawn from his seed, so it never changes between days. A soldier on
     * parade wears none of them, which is what a uniform means.
     *
     * <p>Every pixel written here is one the plain body does not use, so the same method can be run in
     * reverse by whoever needs the person without them: {@code accessories(s, v, p, false, ...)} is a
     * no-op, and {@link #paint} passes the switch through.
     */
    private static void accessories(Skin s, SkinVariation v, SkinPoolDatabase.Pool p, boolean female,
                                    int trim, int outer, int mask, int years) {
        accessories(s, v, p, female, trim, outer, mask, years, null);
    }

    /**
     * The same, with the trade leaning what is carried.
     *
     * <p>Two trades in one town wear the same cloth and are told apart by what they carry: a herder has a
     * staff and a purse, an artisan a purse of tools, a merchant both a purse and a sash to keep it, a
     * scholar the cloth over the shoulder he writes on, a farmer a band round his head against the sun.
     * The shares below are the share of that trade who carry it; the place still decides the metal and
     * the cloth.
     */
    private static void accessories(Skin s, SkinVariation v, SkinPoolDatabase.Pool p, boolean female,
                                    int trim, int outer, int mask, int years, String role) {
        if (mask == ACC_NONE) {
            return;
        }
        String culture = cultureOf(p);
        long seed = v.seed();
        int body[] = R.get("body_front");
        if (body == null) {
            return;
        }
        // how common each one is here: the values are the share of people who wear it
        int sashShare = switch (culture) {
            case "deccan", "indic", "maurya", "persianate", "north_african", "african" -> 45;
            case "east_asian", "americas", "mediterranean" -> 18;
            default -> 25;
        };
        int shawlShare = switch (culture) {
            case "deccan", "indic", "maurya", "persianate" -> 40;
            case "north_african", "african", "americas" -> 30;
            default -> 15;
        };
        int pouchShare = 28;
        int bandShare = switch (culture) {
            case "deccan", "indic", "maurya", "persianate", "north_african" -> 35;
            case "east_asian" -> 20;
            default -> 12;
        };
        int armletShare = switch (culture) {
            case "deccan", "indic", "maurya", "african", "north_african" -> 30;
            default -> 10;
        };
        int ankleShare = switch (culture) {
            case "deccan", "indic", "maurya", "african" -> 34;
            default -> 8;
        };

        // the age leans every share: what a young person wears and what an old one wears are not
        // the same list, and the difference is the point
        sashShare = leaned(sashShare, "sash", years);
        shawlShare = leaned(shawlShare, "shawl", years);
        pouchShare = leaned(pouchShare, "pouch", years);
        bandShare = leaned(bandShare, "band", years);
        armletShare = leaned(armletShare, "armlet", years);
        ankleShare = leaned(ankleShare, "ankle", years);
        // and the trade leans it again: what a person carries is mostly what their work needs
        if (role != null) {
            switch (role.toLowerCase(java.util.Locale.ROOT)) {
                case "herder", "shepherd", "drover" -> { shawlShare = 100; pouchShare += 55; sashShare += 20; }
                case "artisan", "smith", "weaver", "potter", "carpenter" -> { pouchShare += 40; bandShare += 15; }
                case "merchant", "trader", "pedlar" -> { pouchShare += 45; sashShare += 25; }
                case "scholar", "scribe", "healer", "advisor", "priest", "clerk", "teacher" ->
                        { shawlShare += 30; pouchShare += 10; }
                case "farmer", "peasant", "labourer" -> { bandShare += 30; ankleShare -= 10; }
                case "soldier", "guard", "watchman" -> { sashShare += 35; armletShare += 20; }
                case "citizen", "townsman" -> { sashShare -= 12; shawlShare -= 8; }
                case "villager", "resident" -> { sashShare = 4; shawlShare = 4; pouchShare = 6;
                        bandShare = 6; armletShare = 4; ankleShare = 4; }
                default -> { }
            }
        }

        int cloth = blend(outer, trim, 0.35);
        int metal = (v.seed() % 3 == 0) ? 0xC9C6BE : 0xD8B24A;
        int leather = blend(0x6B4A32, trim, 0.2);

        // 1. the sash: a diagonal across the chest, from the left hip to the right shoulder
        if ((mask & A_SASH) != 0 && SkinHashes.pick(culture + "|sash|" + seed, 100) < sashShare) {
            for (int i = 0; i < 8; i++) {
                s.put(body[0] + i, body[1] + 10 - i, i % 3 == 0 ? SkinPoolDatabase.shade(cloth, -18) : cloth);
            }
        }
        // 2. the cloth over one shoulder: the top row of the body, front and back
        if ((mask & A_SHOULDER) != 0 && SkinHashes.pick(culture + "|shawl|" + seed, 100) < shawlShare) {
            int[] back = R.get("body_back");
            for (int i = 0; i < 8; i++) {
                s.put(body[0] + i, body[1], Cloth(s, body[0] + i, body[1], outer));
                if (back != null) {
                    s.put(back[0] + i, back[1], Cloth(s, back[0] + i, back[1], outer));
                }
            }
        }
        // 3. the pouch at the hip, on the side of the body where a hand falls
        if ((mask & A_POUCH) != 0 && SkinHashes.pick(culture + "|pouch|" + seed, 100) < pouchShare) {
            int[] side = R.get("body_right");
            if (side != null) {
                s.put(side[0] + 1, side[1] + 8, leather);
                s.put(side[0] + 1, side[1] + 9, SkinPoolDatabase.shade(leather, -14));
                s.put(side[0] + 2, side[1] + 9, SkinPoolDatabase.shade(leather, -14));
            }
        }
        // 4. the cloth tied round the head, worn on the hairline: it lies on the hair, never on the
        //    skin of the face, so a person's face stays exactly their own
        if ((mask & A_HEADBAND) != 0 && SkinHashes.pick(culture + "|band|" + seed, 100) < bandShare) {
            int[] head = R.get("head_front");
            if (head != null) {
                for (int i = 0; i < 8; i++) {
                    s.put(head[0] + i, head[1], i % 4 == 3 ? metal : blend(cloth, metal, 0.35));
                }
            }
        }
        // 5. the armlet, on one arm only: the left, as most people are right-handed
        if ((mask & A_ARMLET) != 0 && SkinHashes.pick(culture + "|armlet|" + seed, 100) < armletShare) {
            int[] arm = R.get("arm_r_front");
            if (arm != null) {
                for (int i = 0; i < 4; i++) {
                    s.put(arm[0] + i, arm[1] + 2, i % 2 == 0 ? metal : SkinPoolDatabase.shade(metal, -22));
                }
            }
        }
        // 7. the stick the old walk with: only ever in old age, and not in a doorway but in the hand
        if ((mask & A_STAFF) != 0 && SkinHashes.pick(culture + "|staff|" + seed, 100) < 62
                && ageLean("staff", years) > 0) {
            int[] arm = R.get("arm_r_front");
            int[] armB = R.get("arm_r_back");
            if (arm != null) {
                int wood = blend(0x6B4A32, 0x2B1D14, 0.35);
                for (int r = 4; r < 12; r++) {
                    s.put(arm[0] + 3, arm[1] + r, r % 4 == 0 ? SkinPoolDatabase.shade(wood, 18) : wood);
                    if (armB != null) {
                        s.put(armB[0] + 3, armB[1] + r, SkinPoolDatabase.shade(wood, -10));
                    }
                }
            }
        }
        // 6. the band at the ankle
        if ((mask & A_ANKLE) != 0 && SkinHashes.pick(culture + "|ankle|" + seed, 100) < ankleShare) {
            int[] leg = R.get("leg_r_front");
            if (leg != null) {
                for (int i = 0; i < 4; i++) {
                    s.put(leg[0] + i, leg[1] + 10, SkinPoolDatabase.shade(metal, -8));
                }
            }
        }
    }

    /** What the cloth on a shoulder looks like: the outer cloth where the clothes used one, else the trim. */
    private static int Cloth(Skin s, int x, int y, int outer) {
        int was = s.get(x, y);
        return was == 0 ? outer : blend(outer, was, 0.5);
    }

    /** The culture a pool belongs to - the pool carries its own id for exactly this sort of question. */
    // ------------------------------------------------------------------ age leans all of it

    /**
     * How much more, or less, likely a thing is at this age.
     *
     * <p>Dress is not the same at eighteen and at seventy, and the difference is not decoration: a
     * young labourer goes bare-armed and bare-ankled and ties a sash; an old man wraps a cloth round
     * his shoulders whatever the weather and leans on a stick; an old woman keeps the head covered
     * and wears fewer bangles than the girl she was. These multipliers are the difference, and they
     * are the same in every culture - what differs by place is the base share, which the caller
     * already sets.
     *
     * @param youth below twenty, old, or the years in between
     */
    static double ageLean(String kind, int years) {
        boolean young = years >= 0 && years < Ages.GROWN_FROM;
        boolean old = years >= Ages.OLD_FROM;
        return switch (kind) {
            case "sash" -> young ? 1.15 : old ? 0.85 : 1.0;
            case "shawl" -> young ? 0.55 : old ? 1.55 : 1.0;      // the old wrap up
            case "band" -> young ? 0.40 : old ? 1.45 : 1.0;       // the old keep the head covered
            case "armlet" -> young ? 1.60 : old ? 0.30 : 1.0;     // a young person's ornament
            case "ankle" -> young ? 1.70 : old ? 0.20 : 1.0;
            case "pouch" -> 1.0;
            case "bindi" -> young ? 0.35 : 1.0;
            case "necklace" -> young ? 0.85 : old ? 1.10 : 1.0;
            case "earring" -> young ? 1.25 : old ? 0.90 : 1.0;
            case "nose" -> young ? 0.40 : old ? 1.0 : 1.0;        // married women, not girls
            case "bangle" -> young ? 1.40 : old ? 0.60 : 1.0;
            case "staff" -> old ? 1.0 : 0.0;                      // only the old carry one
            case "wrap" -> young ? 0.55 : old ? 1.30 : 1.0;       // the head, by the pool's own share
            default -> 1.0;
        };
    }

    /** A share out of a hundred, leaned by age and kept inside 0..100. */
    private static int leaned(int share, String kind, int years) {
        return (int) Math.max(0, Math.min(100, Math.round(share * ageLean(kind, years))));
    }

    // ------------------------------------------------------------------ the accessories, one by one
    /**
     * The eleven things a person puts on that are not clothes: six of cloth and leather, five of
     * jewellery. Each has its own bit, so a caller can leave any one of them off - or all of them -
     * without touching the rest. The face, the hair and the clothes are never affected.
     */
    public static final int A_SASH = 1;          // a cloth across the chest, hip to shoulder
    public static final int A_SHOULDER = 1 << 1; // a cloth over one shoulder
    public static final int A_POUCH = 1 << 2;    // a purse at the hip
    public static final int A_HEADBAND = 1 << 3; // a cloth tied round the head
    public static final int A_ARMLET = 1 << 4;   // a band on the upper arm
    public static final int A_ANKLE = 1 << 5;    // a band at the ankle
    public static final int J_BINDI = 1 << 6;    // the mark on the brow
    public static final int J_NECKLACE = 1 << 7; // beads or metal at the throat
    public static final int J_EARRING = 1 << 8;  // one at each ear
    public static final int J_NOSE = 1 << 9;     // the nose ring
    public static final int J_BANGLE = 1 << 10;  // bangles at the wrists, a ring at each ankle
    public static final int A_STAFF = 1 << 11;   // the stick an old person walks with
    /** Nothing on. */
    public static final int ACC_NONE = 0;
    /** Everything on: what the world asks for. */
    public static final int ACC_ALL = (1 << 12) - 1;

    /** The names, in bit order, for anything that has to show them to a person. */
    public static final String[] ACCESSORY_NAMES = {
        "sash", "shoulder cloth", "hip pouch", "head cloth", "armlet", "ankle band",
        "bindi", "necklace", "earrings", "nose ring", "bangles", "staff"
    };

    private static String cultureOf(SkinPoolDatabase.Pool p) {
        return p.culture() == null ? "global" : p.culture();
    }

    // ------------------------------------------------------------------ the face
    /**
     * The face: the same eight by eight the template gives, but the features in it are the person's own.
     *
     * <p>Six sets of eyes, five brows, five noses, five mouths, six beards, three ages - chosen by
     * {@link SkinVariation} from the person's seed. Two men of the same town, the same year and the same
     * trade do not have the same face, which is the whole point of the class.
     */
    private static void face(Skin s, Face face, int tone, boolean female, SkinPoolDatabase.Pool p,
                             SkinVariation v) {
        int fx = 8, fy = 8;
        int white = 0xF4F2EE;
        int iris = female ? 0x233861 : 0x3A4A6B;
        int dark = SkinPoolDatabase.shade(tone, -46);
        int line = SkinPoolDatabase.shade(tone, -30);
        int brow = blend(tone, blend(face.hair(), hairColour(face, v), 0.35), 0.40);
        boolean elder = v.age() == 2;

        // the hairline: two rows of hair colour, the forehead below. It is the *aged* colour: this
        // strip used to be painted in the pool's own brown while the hair above it greyed, so an old
        // person had a young hairline a pixel above grey hair.
        int hairNow = hairColour(face, v);
        for (int i = 0; i < 8; i++) {
            s.put(fx + i, fy, hairNow);
            int row = i == 0 || i == 7 ? hairNow
                    : blend(hairNow, SkinPoolDatabase.shade(hairNow, 26), 0.4);
            // the hairline recedes with the years: at the temples the second row gives way to skin
            if (elder && (i == 0 || i == 7)) {
                row = SkinPoolDatabase.shade(tone, 10);
            }
            s.put(fx + i, fy + 1, row);
        }
        // the frame of the face, darker at the edges
        for (int j = 2; j < 6; j++) {
            s.put(fx, fy + j, line);
            s.put(fx + 7, fy + j, line);
        }
        // ---- the brow: five lines
        for (int i = 1; i < 7; i++) {
            switch (v.brow()) {
                case 1 -> s.put(fx + i, fy + 3, (i == 3 || i == 4) ? brow : SkinPoolDatabase.shade(brow, -12));
                case 2 -> { s.put(fx + i, fy + 3, brow); s.put(fx + i, fy + 2, SkinPoolDatabase.shade(brow, -20)); }
                case 3 -> s.put(fx + i, fy + 3, blend(tone, face.hair(), 0.22));
                case 4 -> s.put(fx + i, fy + 3 - (i > 4 ? 1 : 0), brow);
                default -> s.put(fx + i, fy + 3, brow);
            }
        }
        // ---- the eyes: six sets
        int el, er, row;
        switch (v.eye()) {
            case 1 -> { el = 2; er = 5; row = 5; }        // almond, set low
            case 2 -> { el = 3; er = 4; row = 5; }        // narrow, close together
            case 3 -> { el = 2; er = 5; row = 5; }        // heavy-lidded, a crease above
            case 4 -> { el = 1; er = 6; row = 4; }        // wide-set
            case 5 -> { el = 3; er = 4; row = 4; }        // close-set
            default -> { el = 2; er = 5; row = 4; }       // round
        }
        if (female) {                                     // the wide eye: white outside, iris inside
            s.put(fx + el, fy + row, white);
            s.put(fx + er, fy + row, white);
            s.put(fx + el + (v.eye() == 3 ? 0 : 1) * 0, fy + row + 1, iris);
            s.put(fx + er, fy + row + 1, iris);
            s.put(fx + el, fy + row + 1, iris);
            s.put(fx + er - 0, fy + row + 1, iris);
        } else {
            s.put(fx + el, fy + row, iris);
            s.put(fx + er, fy + row, iris);
        }
        if (v.eye() == 3) {                               // heavy lids: a dark row over each eye
            s.put(fx + el, fy + row - 1, SkinPoolDatabase.shade(tone, -24));
            s.put(fx + er, fy + row - 1, SkinPoolDatabase.shade(tone, -24));
        }
        if (elder) {                       // the marks of age: crows' feet, a lined brow, a lined mouth
            s.put(fx + el - 1, fy + row + 1, SkinPoolDatabase.shade(tone, -18));
            s.put(fx + er + 1, fy + row + 1, SkinPoolDatabase.shade(tone, -18));
            s.put(fx + 1, fy + 2, SkinPoolDatabase.shade(tone, -14));
            s.put(fx + 6, fy + 2, SkinPoolDatabase.shade(tone, -14));
            s.put(fx + 2, fy + 2, SkinPoolDatabase.shade(tone, -22));   // the brow furrowed right across
            s.put(fx + 5, fy + 2, SkinPoolDatabase.shade(tone, -22));
            s.put(fx + 2, fy + 6, SkinPoolDatabase.shade(tone, -20));   // the lines each side of the mouth
            s.put(fx + 5, fy + 6, SkinPoolDatabase.shade(tone, -20));
            s.put(fx + 2, fy + 7, SkinPoolDatabase.shade(tone, -26));   // the jaw hangs heavier
            s.put(fx + 5, fy + 7, SkinPoolDatabase.shade(tone, -26));
            s.put(fx, fy + 2, SkinPoolDatabase.shade(tone, -8));        // hollow temples
            s.put(fx + 7, fy + 2, SkinPoolDatabase.shade(tone, -8));
        } else if (v.age() == 0) {         // a young face: a fuller cheek, and not a line on it
            s.put(fx + 1, fy + 6, SkinPoolDatabase.shade(tone, 16));
            s.put(fx + 6, fy + 6, SkinPoolDatabase.shade(tone, 16));
            s.put(fx + 1, fy + 7, SkinPoolDatabase.shade(tone, 12));
            s.put(fx + 6, fy + 7, SkinPoolDatabase.shade(tone, 12));
            s.put(fx + 2, fy + 2, SkinPoolDatabase.shade(tone, 10));    // a smooth brow
            s.put(fx + 5, fy + 2, SkinPoolDatabase.shade(tone, 10));
        } else {                           // the years in between: one line of living, nothing more
            s.put(fx + 2, fy + 6, SkinPoolDatabase.shade(tone, -12));
            s.put(fx + 5, fy + 6, SkinPoolDatabase.shade(tone, -12));
        }
        // ---- the nose: five shapes, in the two pixels under the eyes
        switch (v.nose()) {
            case 1 -> { s.put(fx + 3, fy + 5, SkinPoolDatabase.shade(tone, 14));
                        s.put(fx + 4, fy + 5, SkinPoolDatabase.shade(tone, 8)); }
            case 2 -> { for (int i = 2; i < 6; i++) { s.put(fx + i, fy + 5, SkinPoolDatabase.shade(tone, -20)); }
                        s.put(fx + 3, fy + 6, SkinPoolDatabase.shade(tone, -10));
                        s.put(fx + 4, fy + 6, SkinPoolDatabase.shade(tone, -10)); }
            case 3 -> { s.put(fx + 3, fy + 5, SkinPoolDatabase.shade(tone, -26));
                        s.put(fx + 4, fy + 5, SkinPoolDatabase.shade(tone, -12));
                        s.put(fx + 4, fy + 6, SkinPoolDatabase.shade(tone, -26)); }
            case 4 -> { s.put(fx + 3, fy + 5, SkinPoolDatabase.shade(tone, 16));
                        s.put(fx + 4, fy + 5, SkinPoolDatabase.shade(tone, 16));
                        s.put(fx + 3, fy + 6, SkinPoolDatabase.shade(tone, -6));
                        s.put(fx + 4, fy + 6, SkinPoolDatabase.shade(tone, -6)); }
            default -> { s.put(fx + 3, fy + 5, SkinPoolDatabase.shade(tone, 12));
                         s.put(fx + 4, fy + 5, SkinPoolDatabase.shade(tone, 12)); }
        }
        // ---- the mouth: five lines
        switch (v.mouth()) {
            case 1 -> { for (int i = 2; i < 6; i++) { s.put(fx + i, fy + 6, face.mouth()); } }
            case 2 -> { s.put(fx + 3, fy + 6, SkinPoolDatabase.shade(face.mouth(), -14));
                        s.put(fx + 4, fy + 6, SkinPoolDatabase.shade(face.mouth(), -14)); }
            case 3 -> { s.put(fx + 3, fy + 6, face.mouth()); s.put(fx + 4, fy + 6, face.mouth());
                        s.put(fx + 2, fy + 7, SkinPoolDatabase.shade(tone, -22));
                        s.put(fx + 5, fy + 7, SkinPoolDatabase.shade(tone, -22)); }
            case 4 -> { s.put(fx + 3, fy + 6, face.mouth()); s.put(fx + 4, fy + 6, face.mouth());
                        s.put(fx + 2, fy + 6, SkinPoolDatabase.shade(tone, -18));
                        s.put(fx + 5, fy + 6, SkinPoolDatabase.shade(tone, -18)); }
            default -> { s.put(fx + 3, fy + 6, dark); s.put(fx + 4, fy + 6, dark); }
        }
        if (female && face.blush() != 0) {
            s.put(fx + 1, fy + 6, blend(tone, face.blush(), 0.35));
            s.put(fx + 6, fy + 6, blend(tone, face.blush(), 0.35));
        }
        // ---- the beard: six shapes, on the lower face
        int beardCol = blend(face.hair(), tone, 0.30);
        switch (v.beard()) {
            case 1 -> {                                   // stubble
                for (int j = 6; j < 8; j++) {
                    for (int i = 1; i < 7; i++) {
                        if ((i + j) % 2 == 0) {
                            s.put(fx + i, fy + j, blend(beardCol, tone, 0.55));
                        }
                    }
                }
            }
            case 2 -> {                                   // moustache
                for (int i = 2; i < 6; i++) {
                    s.put(fx + i, fy + 6, SkinPoolDatabase.shade(beardCol, -6));
                }
            }
            case 3 -> {                                   // goatee
                for (int i = 2; i < 6; i++) {
                    s.put(fx + i, fy + 6, SkinPoolDatabase.shade(beardCol, -6));
                }
                for (int i = 3; i < 5; i++) {
                    s.put(fx + i, fy + 7, beardCol);
                }
            }
            case 4, 5 -> {                                // full and forked
                for (int j = 6; j < 8; j++) {
                    for (int i = 1; i < 7; i++) {
                        s.put(fx + i, fy + j, j == 6 && (i == 2 || i == 5) ? beardCol
                                : SkinPoolDatabase.shade(beardCol, -8));
                    }
                }
                s.put(fx + 1, fy + 7, face.hair());
                s.put(fx + 6, fy + 7, face.hair());
                if (v.beard() == 5) {                     // forked: the mouth visible between two points
                    s.put(fx + 3, fy + 7, SkinPoolDatabase.shade(tone, -18));
                    s.put(fx + 4, fy + 7, SkinPoolDatabase.shade(tone, -18));
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ the hair
    /**
     * The hair all round the head, not only over the brow.
     *
     * <p>The template gives the head five more faces than the face itself - the crown, the two sides,
     * the back and the underside. This paints the crown and the sides and the back with hair, to the
     * length the person's style has. It is the defect the owner saw first: a head with no hair on top
     * is bald from every angle above the eyes.
     */
    private static void scalp(Skin s, Face face, boolean female, SkinVariation v) {
        int hair = hairColour(face, v);
        int dark = SkinPoolDatabase.shade(hair, -20);
        int light = SkinPoolDatabase.shade(hair, 24);
        String style = face.style();
        // how far down the head the hair comes: the shell of hair over a shaven crown is shallow,
        // a plait reaches the nape
        int deep = switch (style) {
            case "long", "braid" -> 8;
            case "loose", "plait_over", "wrapped", "wrapped_head" -> 8;
            case "turban_wrap" -> 3;                        // the wrap covers the rest, see headwear()
            case "curly" -> 6;
            case "bun" -> 7;
            default -> 5;                                   // short, short_beard
        };
        deep = Math.max(3, Math.min(8, deep + (v.hairVolume() - 2)));   // how much hair a person carries
        // the crown: a whorl, lighter at the middle and darker at the rim, so it reads as hair
        int[] top = R.get("head_top");
        for (int j = 0; j < top[3]; j++) {
            for (int i = 0; i < top[2]; i++) {
                double dx = i - (top[2] - 1) / 2.0, dy = j - (top[3] - 1) / 2.0;
                double r = Math.sqrt(dx * dx + dy * dy);
                int c = r < 1.9 ? light : r < 2.9 ? hair : dark;
                if ((i * 3 + j * 5) % 7 == 0) {
                    c = SkinPoolDatabase.shade(c, -7);
                }
                s.put(top[0] + i, top[1] + j, c);
            }
        }
        // the sides and the back, down to the hairline, and bare skin below it where the hair stops
        for (String reg : new String[]{"head_back", "head_left", "head_right"}) {
            int[] r = R.get(reg);
            for (int j = 0; j < r[3]; j++) {
                if (j >= deep) {
                    continue;                               // skin below the hairline, from the base fill
                }
                for (int i = 0; i < r[2]; i++) {
                    int c = j == 0 ? light : hair;
                    if ((i * 5 + j * 3) % 9 == 0) {
                        c = dark;
                    }
                    if (j == deep - 1) {
                        c = SkinPoolDatabase.shade(hair, -14);   // the edge of the hair
                    }
                    s.put(r[0] + i, r[1] + j, c);
                }
            }
        }
        // a woman's hair comes forward of the ears; a man's does not
        if (female) {
            for (String reg : new String[]{"head_left", "head_right"}) {
                int[] r = R.get(reg);
                for (int j = 0; j < Math.min(6, r[3]); j++) {
                    s.put(r[0] + (reg.equals("head_left") ? r[2] - 1 : 0), r[1] + j, hair);
                }
            }
        }
    }

    /**
     * The faces that made the model look hollow: the underside of the head (the neck), the top of the
     * shoulders, the palm of each hand, the top of each leg and the sole of each foot.
     *
     * <p>Each takes its colour from the face that meets it, so the garments stay consistent without
     * this method having to know which garment was chosen.
     */
    private static void edges(Skin s) {
        int neck = s.get(R.get("head_front")[0] + 3, R.get("head_front")[1] + 7);
        fill(s, "head_bottom", SkinPoolDatabase.shade(neck, -18), SkinPoolDatabase.shade(neck, -26));
        int shoulder = s.get(R.get("body_front")[0] + 3, R.get("body_front")[1]);
        fill(s, "body_top", SkinPoolDatabase.shade(shoulder, 8), SkinPoolDatabase.shade(shoulder, -6));
        int waist = s.get(R.get("body_front")[0] + 3, R.get("body_front")[1] + 11);
        fill(s, "body_bottom", SkinPoolDatabase.shade(waist, -22), SkinPoolDatabase.shade(waist, -30));
        for (String side : new String[]{"r", "l"}) {
            int[] af = R.get("arm_" + side + "_front");
            int sleeve = s.get(af[0] + 1, af[1]);
            int hand = s.get(af[0] + 1, af[1] + 11);
            fill(s, "arm_" + side + "_top", SkinPoolDatabase.shade(sleeve, 6),
                    SkinPoolDatabase.shade(sleeve, -8));
            fill(s, "arm_" + side + "_palm", SkinPoolDatabase.shade(hand, -10),
                    SkinPoolDatabase.shade(hand, -20));
            int[] lf = R.get("leg_" + side + "_front");
            int belt = s.get(lf[0] + 1, lf[1]);
            int sole = s.get(lf[0] + 1, lf[1] + 11);
            fill(s, "leg_" + side + "_top", SkinPoolDatabase.shade(belt, -10),
                    SkinPoolDatabase.shade(belt, -20));
            fill(s, "leg_" + side + "_sole", SkinPoolDatabase.shade(sole, -34),
                    SkinPoolDatabase.shade(sole, -46));
        }
    }

    /** Fill a small face with one colour, a shade darker at its edges - no region left as a hole. */
    private static void fill(Skin s, String region, int rgb, int edge) {
        int[] r = R.get(region);
        if (r == null) {
            return;
        }
        for (int j = 0; j < r[3]; j++) {
            for (int i = 0; i < r[2]; i++) {
                boolean rim = i == 0 || j == 0 || i == r[2] - 1 || j == r[3] - 1;
                s.put(r[0] + i, r[1] + j, rim ? edge : rgb);
            }
        }
    }

    // ------------------------------------------------------------------ the layers, made visible
    /**
     * Copy every painted (non-transparent) pixel of an overlay layer onto the main-layer region the
     * vanilla model actually shows for that body part.
     *
     * <p>The model shows the main layer, and the hat layer only while a helmet is worn - these people
     * never wear one, so the hat layer and the second (jacket / sleeve / trouser) layer would stay
     * painted in the texture and never reach the screen. This is the defect the owner saw: a woman's
     * long hair is drawn down the back of the jacket layer, and the model has no jacket face there,
     * so no one showed long hair; the same hid the hat, the veil, the bun, the coat and the flared hem.
     * Where the layer carries a pixel it wins - exactly the rule the game itself uses when it draws a
     * worn hat or a suit of armour over the body.
     */
    private static void flatten(Skin s, String over, String onto) {
        int[] a = R.get(over);
        int[] b = R.get(onto);
        if (a == null || b == null) {
            return;
        }
        for (int j = 0; j < a[3]; j++) {
            for (int i = 0; i < a[2]; i++) {
                int c = s.get(a[0] + i, a[1] + j);
                if (c != 0) {                       // an unpainted pixel is 0 - nothing to carry
                    s.put(b[0] + i, b[1] + j, c);
                }
            }
        }
    }

    /**
     * Make the painted layers visible: the hat layer onto the head, the second layer (jacket, sleeves,
     * trousers) onto the body, arms and legs. The main layer is already what the model shows, so it is
     * left as is. Called last, after the hair and the headwear, so the order the painter worked in -
     * hair over cloth, hat over hair - is the order that survives on the screen.
     */
    private static void flattenLayers(Skin s) {
        // the hat layer's five head faces onto the head's five faces
        flatten(s, "hat_top", "head_top");
        flatten(s, "hat_front", "head_front");
        flatten(s, "hat_back", "head_back");
        flatten(s, "hat_right", "head_right");
        flatten(s, "hat_left", "head_left");
        // the second layer's body faces onto the torso
        flatten(s, "jacket_front", "body_front");
        flatten(s, "jacket_back", "body_back");
        flatten(s, "jacket_right", "body_right");
        flatten(s, "jacket_left", "body_left");
        // the second layer's arm faces onto the arms
        for (String f : new String[]{"front", "back", "right", "left"}) {
            flatten(s, "sleeve_r_" + f, "arm_r_" + f);
            flatten(s, "sleeve_l_" + f, "arm_l_" + f);
            flatten(s, "trouser_r_" + f, "leg_r_" + f);
            flatten(s, "trouser_l_" + f, "leg_l_" + f);
        }
    }

    /**
     * The colour of a person's hair now: the pool's colour, greying as they age, and grey throughout
     * for an elder - a village has old people in it, and it never had before.
     */
    static int hairColour(Face face, SkinVariation v) {
        int base = face.hair();
        if (v.age() != 2) {
            return base;                            // a young or a grown head keeps its own colour
        }
        // not one flat grey: people grey at different rates, so one old head is iron and another is
        // white. The amount is the person's own, the fact of it is the age's.
        double amount = 0.26d + (Math.floorMod(v.seed(), 5)) * 0.09d;   // 0.26 .. 0.62
        return blend(base, 0xB9B4AC, amount);
    }

    private static void hair(Skin s, Face face, boolean female, SkinPoolDatabase.Pool p, SkinVariation v) {
        scalp(s, face, female, v);
        int hair = hairColour(face, v);
        int dark = SkinPoolDatabase.shade(hair, -22);
        int light = SkinPoolDatabase.shade(hair, 26);
        String style = face.style();
        int hx = 40, hy = 8;                       // the hat layer carries the hair (flattenLayers shows it)
        int bx = 32, by = 36;                      // the back of the jacket carries the length (flattenLayers shows it)

        switch (style) {
            case "bun", "wrapped", "wrapped_head", "turban_wrap" -> {
                if (style.equals("bun")) {
                    // a bun sits at the back of the head, not over the brow: a knot on the hat layer's
                    // back, so it is seen from behind and from above, which is where a bun is seen
                    for (int j = 0; j < 3; j++) {
                        for (int i = 2; i < 6; i++) {
                            s.put(56 + i, 8 + j, j == 0 ? light : hair);
                        }
                    }
                    for (int j = 0; j < 4; j++) {
                        for (int i = 4; i < 8; i++) {
                            s.put(40 + i, 0 + j, j == 0 ? light : hair);
                        }
                    }
                } else {
                    for (int j = 0; j < 2; j++) {
                        for (int i = 2; i < 6; i++) {
                            s.put(hx + i, hy + j, j == 0 ? light : hair);
                        }
                    }
                }
                for (int i = 0; i < 8; i++) {
                    s.put(40 + i, 0 + 0, hair);    // the top of the hat layer
                }
                if (style.equals("wrapped")) {
                    for (int i = 0; i < 8; i++) {
                        s.put(hx + i, hy + 3, SkinPoolDatabase.shade(hair, 34));
                    }
                }
                if (style.equals("turban_wrap")) {
                    for (int i = 0; i < 8; i++) {
                        s.put(8 + i, 8, SkinPoolDatabase.shade(hair, 12));
                    }
                }
            }
            case "loose", "plait_over", "braid", "long" -> {
                int len = switch (style) {
                    case "loose" -> 9;
                    case "plait_over" -> 7;
                    case "braid" -> 11;
                    default -> 8;
                };
                // the hair down the back: strands, not a chessboard. Two pixels of light and two of
                // dark down the length, and a darker edge, which is what a plait looks like from behind.
                for (int j = 0; j < Math.min(len, 12); j++) {
                    for (int i = 0; i < 8; i++) {
                        int c = hair;
                        if (i % 4 == 1) {
                            c = light;
                        } else if (i % 4 == 3) {
                            c = dark;
                        }
                        if (j == Math.min(len, 12) - 1) {
                            c = SkinPoolDatabase.shade(c, -20);       // the tip of the plait
                        }
                        s.put(bx + i, by + j, c);
                    }
                }
                if (style.equals("braid") || style.equals("plait_over")) {
                    int side = style.equals("braid") ? 22 : 25;
                    for (int j = 0; j < 9; j++) {
                        s.put(side, 36 + j, j % 2 == 0 ? hair : dark);
                    }
                }
            }
            case "curly" -> {
                for (int j = 0; j < 3; j++) {
                    for (int i = 0; i < 8; i++) {
                        s.put(8 + i, 8 + j, (i + j) % 3 == 0 ? light : hair);
                    }
                }
            }
            default -> { }                          // short and short_beard are the face's hairline
        }
        // a woman's lashes live on the hat layer's face, bending round the head - six pixels, a woman's
        if (female) {
            for (int j = 4; j < 6; j++) {
                s.put(hx + 1, hy + j, dark);
                s.put(hx + 6, hy + j, dark);
            }
            s.put(32 + 7, 8 + 5, dark);            // and round onto the sides
            s.put(48 + 0, 8 + 5, dark);
        }
        for (int i = 0; i < 8; i++) {               // one row of highlight, so the hair is not flat
            int c = s.get(8 + i, 8 + 1);
            s.put(8 + i, 8 + 1, SkinPoolDatabase.shade(c & 0xFFFFFF, 8));
        }
    }


    /**
     * How far down the arm the era's sleeve comes, in rows of the twelve.
     *
     * <p>The old painter gave every culture two rows of cloth and left the rest of the arm bare, which
     * is a sleeveless tunic in every century. The length now follows the garment the pool itself names:
     * a robe, a kameez, a paijama or a wrap has a sleeve to the wrist; a tunic, a skirt or a wrapper has
     * one to the elbow; a culture with no upper garment yet, before its first turning point, has none.
     */
    private static int sleevesFor(SkinPoolDatabase.Pool p, Cut cut) {
        java.util.List<String> longCloth = List.of("robe", "wrap", "paijama", "kameez", "kimono", "kaftan");
        boolean robed = p.lower() != null && longCloth.contains(p.lower());
        if (!robed && p.cultural() != null) {
            for (String c : p.cultural()) {
                if (longCloth.contains(c)) {
                    robed = true;
                    break;
                }
            }
        }
        int base = cut.arm();
        if (base <= 0) {
            return 0;                                     // there is no sleeve to draw yet
        }
        return robed ? 12 : Math.max(base, 6);            // to the wrist, or to the elbow
    }


    /**
     * The headwear the era's pool asks for and the old painter never drew.
     *
     * <p>The pool carries two numbers for this and neither was used: {@code wrap}, the share of the
     * culture that covers its head, and {@code keep_hat}, the share that still does in the modern day.
     * A person's own hair style settles it where it can - a person whose style is a turban wrap wears
     * one - and otherwise the culture's own share settles it, from the same face data every time, so a
     * person does not change headwear when the day changes.
     *
     * <p>Three shapes, from the cloth the person already wears: a tall turban, a low wrap, and a hood.
     * Women of a culture whose jewellery list names a veil wear the veil instead.
     */
    private static void headwear(Skin s, SkinPoolDatabase.Pool p, Face face, boolean female,
                                 int cloth, int trim) {
        headwear(s, p, face, female, cloth, trim, -1);
    }

    private static void headwear(Skin s, SkinPoolDatabase.Pool p, Face face, boolean female,
                                 int cloth, int trim, int years) {
        double wrap = p.era("wrap") / 100.0;
        double keep = p.era("keep_hat") / 100.0;
        String style = face.style() == null ? "" : face.style();
        boolean named = style.contains("turban") || style.contains("wrapped");
        int hash = Math.floorMod(face.tone() * 31 + face.hair() * 17 + Math.abs(style.hashCode()), 100);
        String lower = p.lower() == null ? "" : p.lower();
        if (female && p.femaleJewels() != null && p.femaleJewels().contains("veil")) {
            veil(s, SkinPoolDatabase.shade(cloth, 12));
            return;
        }
        // an old head is covered far oftener than a young one, in every culture the pool knows
        double lean = ageLean("wrap", years);
        if (!(named || hash < wrap * lean * 100)) {
            return;
        }
        int c = SkinPoolDatabase.shade(cloth, -12);
        int band = SkinPoolDatabase.shade(trim, -24);
        boolean tall = wrap >= 0.6 || named;
        String[] sides = {"hat_front", "hat_back", "hat_left", "hat_right"};
        int rows = tall ? 4 : 3;
        for (String r : sides) {
            int[] rr = R.get(r);
            int cover = r.equals("hat_front") ? rows : rows + 2;      // the back comes lower than the brow
            for (int j = 0; j < Math.min(cover, rr[3]); j++) {
                for (int i = 0; i < rr[2]; i++) {
                    int col = (i + j) % 5 == 0 ? SkinPoolDatabase.shade(c, -10) : c;
                    if (j == rows - 1) {
                        col = band;                                        // the band round the wrap
                    }
                    s.put(rr[0] + i, rr[1] + j, col);
                }
            }
        }
        fill(s, "hat_top", c, SkinPoolDatabase.shade(c, -14));            // the crown is covered too
        fill(s, "hat_bottom", SkinPoolDatabase.shade(c, -30), SkinPoolDatabase.shade(c, -40));
        if (tall) {                                                        // the knot at the front
            s.put(40 + 2, 8 + 0, SkinPoolDatabase.shade(trim, -8));
            s.put(40 + 3, 8 + 0, SkinPoolDatabase.shade(trim, 6));
            s.put(40 + 2, 8 + 1, SkinPoolDatabase.shade(trim, -18));
            s.put(40 + 3, 8 + 1, SkinPoolDatabase.shade(trim, -6));
        }
    }


    /**
     * The head, from the catalogue: what this place put on a head in this year.
     *
     * <p>Thirteen shapes, each drawn from the hat layer of the 64x64 skin - the layer that existed and
     * that nothing in the game ever filled. A turban is tall and banded, a headcloth is low, a fez is a
     * short cylinder with a tassel, a conical hat narrows to the crown, a straw hat has a brim, a hood
     * closes at the sides and leaves the face open, a laurel is only a band, a crown is gold with
     * points, a veil hangs, a fur hat is ragged, a snood holds the hair.
     */
    private static void hatFromCatalogue(Skin s, String culture, String subpool, int year,
                                         boolean female, long seed, int cloth, int trim) {
        Garments.Head h = Garments.head(culture, subpool, year, female, seed);
        if (h == null) {
            return;
        }
        int c = SkinPoolDatabase.shade(cloth, -10);
        int band = SkinPoolDatabase.shade(trim, -26);
        int[] front = R.get("hat_front");
        int[] back = R.get("hat_back");
        switch (h.hat()) {
            case TURBAN -> {
                fillRows(s, "hat_front", 0, 5, c, band, 5);
                fillRows(s, "hat_back", 0, 7, SkinPoolDatabase.shade(c, -6), band, 0);
                fillRows(s, "hat_left", 0, 6, SkinPoolDatabase.shade(c, -10), band, 0);
                fillRows(s, "hat_right", 0, 6, SkinPoolDatabase.shade(c, -10), band, 0);
                fill(s, "hat_top", c, SkinPoolDatabase.shade(c, -14));
                s.put(40 + 2, 8 + 1, SkinPoolDatabase.shade(trim, -4));      // the knot
                s.put(40 + 3, 8 + 1, SkinPoolDatabase.shade(trim, 8));
            }
            case HEADCLOTH -> {
                fillRows(s, "hat_front", 0, 3, c, band, 0);
                fillRows(s, "hat_back", 0, 6, SkinPoolDatabase.shade(c, -8), band, 0);
                fillRows(s, "hat_left", 0, 5, SkinPoolDatabase.shade(c, -12), band, 0);
                fillRows(s, "hat_right", 0, 5, SkinPoolDatabase.shade(c, -12), band, 0);
                fill(s, "hat_top", SkinPoolDatabase.shade(c, -4), SkinPoolDatabase.shade(c, -16));
            }
            case CAP -> {
                fillRows(s, "hat_front", 0, 2, c, band, 0);
                fillRows(s, "hat_back", 0, 3, SkinPoolDatabase.shade(c, -6), band, 0);
                fillRows(s, "hat_left", 0, 3, SkinPoolDatabase.shade(c, -10), band, 0);
                fillRows(s, "hat_right", 0, 3, SkinPoolDatabase.shade(c, -10), band, 0);
                fill(s, "hat_top", SkinPoolDatabase.shade(c, 6), c);
            }
            case FEZ -> {
                fillRows(s, "hat_front", 0, 3, SkinPoolDatabase.shade(0x9B2C2C, 0), band, 0);
                fillRows(s, "hat_back", 0, 3, SkinPoolDatabase.shade(0x9B2C2C, -12), band, 0);
                fillRows(s, "hat_left", 0, 3, SkinPoolDatabase.shade(0x9B2C2C, -20), band, 0);
                fillRows(s, "hat_right", 0, 3, SkinPoolDatabase.shade(0x9B2C2C, -20), band, 0);
                fill(s, "hat_top", SkinPoolDatabase.shade(0x9B2C2C, 10), 0x9B2C2C);
                s.put(40 + 4, 8 + 3, 0x2A2A2A);                              // the tassel
            }
            case CONICAL -> {
                for (String r : new String[]{"hat_front", "hat_back", "hat_left", "hat_right"}) {
                    int[] rr = R.get(r);
                    for (int j = 0; j < 3; j++) {
                        int inset = Math.max(0, 2 - j);                      // it narrows as it rises
                        for (int i = inset; i < rr[2] - inset; i++) {
                            s.put(rr[0] + i, rr[1] + j, j == 2 ? band : c);
                        }
                    }
                }
                int[] top = R.get("hat_top");
                for (int j = 0; j < top[3]; j++) {
                    for (int i = 2; i < top[2] - 2; i++) {
                        s.put(top[0] + i, top[1] + j, SkinPoolDatabase.shade(c, 4));
                    }
                }
            }
            case STRAW -> {
                fillRows(s, "hat_front", 0, 2, blend(0xC8A24A, trim, 0.15), band, 0);
                fillRows(s, "hat_back", 0, 4, blend(0xC8A24A, trim, 0.15), band, 0);
                fillRows(s, "hat_left", 0, 4, blend(0xB08C3A, trim, 0.15), band, 0);
                fillRows(s, "hat_right", 0, 4, blend(0xB08C3A, trim, 0.15), band, 0);
                fill(s, "hat_top", blend(0xD8B25A, trim, 0.1), band);
                int[] br = R.get("hat_front");                                // the brim, one row proud
                for (int i = 0; i < br[2]; i++) {
                    s.put(br[0] + i, br[1] + 2, SkinPoolDatabase.shade(0xB08C3A, -14));
                }
            }
            case HOOD -> {
                fillRows(s, "hat_back", 0, 8, SkinPoolDatabase.shade(c, -6), band, 0);
                fillRows(s, "hat_left", 0, 7, SkinPoolDatabase.shade(c, -12), band, 0);
                fillRows(s, "hat_right", 0, 7, SkinPoolDatabase.shade(c, -12), band, 0);
                fill(s, "hat_top", SkinPoolDatabase.shade(c, 4), SkinPoolDatabase.shade(c, -14));
                int[] fr = R.get("hat_front");                                // the face stays open
                for (int i = 0; i < fr[2]; i++) {
                    s.put(fr[0] + i, fr[1], band);
                }
            }
            case LAUREL -> {
                int[] fr = R.get("hat_front");
                for (int i = 0; i < fr[2]; i++) {
                    s.put(fr[0] + i, fr[1] + 1, i % 2 == 0 ? 0x6E8A34 : 0x88A344);
                }
                for (String r : new String[]{"hat_left", "hat_right", "hat_back"}) {
                    int[] rr = R.get(r);
                    for (int i = 0; i < rr[2]; i++) {
                        s.put(rr[0] + i, rr[1] + 1, 0x7A9638);
                    }
                }
            }
            case CROWN -> {
                int gold = 0xD4A62A;
                int[] fr = R.get("hat_front");
                for (int i = 0; i < fr[2]; i++) {
                    s.put(fr[0] + i, fr[1] + 1, gold);
                    s.put(fr[0] + i, fr[1] + 2, SkinPoolDatabase.shade(gold, -22));
                    if (i % 2 == 0) {
                        s.put(fr[0] + i, fr[1], SkinPoolDatabase.shade(gold, 18));   // the points
                    }
                }
                for (String r : new String[]{"hat_left", "hat_right", "hat_back"}) {
                    int[] rr = R.get(r);
                    for (int i = 0; i < rr[2]; i++) {
                        s.put(rr[0] + i, rr[1] + 1, gold);
                        s.put(rr[0] + i, rr[1] + 2, SkinPoolDatabase.shade(gold, -22));
                    }
                }
            }
            case VEIL -> veil(s, SkinPoolDatabase.shade(cloth, 14));
            case FILLET -> {
                for (String r : new String[]{"hat_front", "hat_back", "hat_left", "hat_right"}) {
                    int[] rr = R.get(r);
                    for (int i = 0; i < rr[2]; i++) {
                        s.put(rr[0] + i, rr[1] + 2, band);
                    }
                }
            }
            case FUR -> {
                fillRows(s, "hat_front", 0, 3, 0x4A3A28, 0x2E2318, 0);
                fillRows(s, "hat_back", 0, 5, 0x3E3020, 0x2E2318, 0);
                fillRows(s, "hat_left", 0, 5, 0x362A1C, 0x2E2318, 0);
                fillRows(s, "hat_right", 0, 5, 0x362A1C, 0x2E2318, 0);
                fill(s, "hat_top", 0x50402C, 0x32261A);
                int[] fr = R.get("hat_front");                                // a ragged edge
                for (int i = 0; i < fr[2]; i++) {
                    if (i % 3 != 1) {
                        s.put(fr[0] + i, fr[1] + 3, 0x2A2016);
                    }
                }
            }
            case SNOOD -> {
                for (String r : new String[]{"hat_back", "hat_left", "hat_right"}) {
                    int[] rr = R.get(r);
                    for (int j = 3; j < 8; j++) {
                        for (int i = 0; i < rr[2]; i++) {
                            s.put(rr[0] + i, rr[1] + j, c);
                        }
                    }
                }
                int[] bb = R.get("hat_back");                                 // the net, at the back
                for (int j = 3; j < 8; j += 2) {
                    for (int i = 0; i < bb[2]; i += 2) {
                        s.put(bb[0] + i, bb[1] + j, SkinPoolDatabase.shade(c, -20));
                    }
                }
            }
            default -> { }
        }
    }

    /** Fill the first `rows` rows of a region, with a band on the last of them and every Nth shaded. */
    private static void fillRows(Skin s, String region, int from, int rows, int c, int band, int every) {
        int[] rr = R.get(region);
        for (int j = from; j < Math.min(from + rows, rr[3]); j++) {
            for (int i = 0; i < rr[2]; i++) {
                int col = every > 0 && (i + j) % every == 0 ? SkinPoolDatabase.shade(c, -12) : c;
                if (j == Math.min(from + rows, rr[3]) - 1) {
                    col = band;
                }
                s.put(rr[0] + i, rr[1] + j, col);
            }
        }
    }

    /** A veil over the hair and the shoulders, for the cultures whose jewellery list names one. */
    private static void veil(Skin s, int cloth) {
        int c = SkinPoolDatabase.shade(cloth, -10);
        for (String r : new String[]{"hat_front", "hat_back", "hat_left", "hat_right"}) {
            int[] rr = R.get(r);
            int cover = r.equals("hat_front") ? 3 : 8;
            for (int j = 0; j < Math.min(cover, rr[3]); j++) {
                for (int i = 0; i < rr[2]; i++) {
                    s.put(rr[0] + i, rr[1] + j, j == Math.min(cover, rr[3]) - 1
                            ? SkinPoolDatabase.shade(c, -16) : c);
                }
            }
        }
        fill(s, "hat_top", SkinPoolDatabase.shade(c, 8), c);
        fill(s, "hat_bottom", SkinPoolDatabase.shade(c, -22), SkinPoolDatabase.shade(c, -34));
        for (int j = 0; j < 6; j++) {                                      // the drape over the shoulders
            for (int i = 0; i < 8; i++) {
                if (i == 3 || i == 4) {
                    continue;                                              // the face opening
                }
                s.put(56 + i, 8 + j, c);
            }
        }
    }


    /**
     * Close the top and the sole of a second layer.
     *
     * <p>A second layer is a shell, and a shell with no top face is a hole you can see through from
     * above. The reference template names these faces for every part - the "top" and "foot" of each
     * second-layer block - and the old painter left all six of them empty.
     */
    private static void closeShell(Skin s, String layer, int cloth, int sole) {
        int[][] seats = switch (layer) {
            case "jacket" -> new int[][]{{20, 32}, {28, 32}};
            case "sleeve_r" -> new int[][]{{44, 32}, {48, 32}};
            case "sleeve_l" -> new int[][]{{52, 48}, {56, 48}};
            case "trouser_r" -> new int[][]{{4, 32}, {8, 32}};
            case "trouser_l" -> new int[][]{{4, 48}, {8, 48}};
            default -> new int[][]{};
        };
        for (int k = 0; k < seats.length; k++) {
            int[] seat = seats[k];
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 4; i++) {
                    int c = k == 0 ? cloth : sole;                  // the top face, then the sole
                    s.put(seat[0] + i, seat[1] + j,
                            (i == 0 || j == 0 || i == 3 || j == 3) ? SkinPoolDatabase.shade(c, -14) : c);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the era's dress
    private static void eraDress(Skin s, SkinPoolDatabase.Pool p, Cut cut, int tone, int top,
                                 int bottom, int trim, boolean female, int year, String role,
                                 long seed, String subpool) {
        // ---- step 6, in four categories: a top, a lower cloth, a one-piece, or an outer garment.
        // The person's own number chooses which kind he reaches for first; the rest follows from it,
        // because clothes come in sets - a dhoti brings a shoulder cloth, a doublet brings hose.
        String culture = cultureOf(p);
        Garments.Garment main = Garments.pick(culture, subpool, year, role, female, seed);
        Garments.Garment upper;
        Garments.Garment lower = null;
        Garments.Garment outer = null;
        switch (main.part()) {
            case LOWER -> {
                lower = main;
                upper = "dhoti_top".equals(main.id()) || "shendyt".equals(main.id()) ? null
                        : Garments.pick(culture, subpool, year, role, female, seed ^ 0x2F1B3A7DL,
                                Garments.Part.TOP);
            }
            case OUTER -> {
                outer = main;
                upper = Garments.pick(culture, subpool, year, role, female, seed ^ 0x2F1B3A7DL,
                        Garments.Part.DRESS);
                if (upper == null) {
                    upper = Garments.pick(culture, subpool, year, role, female, seed ^ 0x2F1B3A7DL,
                            Garments.Part.TOP);
                }
            }
            case DRESS -> upper = main;
            default -> upper = main;
        }
        // an over-garment, on most days but not every day, and never over a one-piece that is itself
        // a coat (a kaftan, a djellaba, a phiran)
        boolean coatLike = upper != null && (upper.loose() && upper.part() == Garments.Part.DRESS
                && (upper.collar() == Garments.Collar.OPEN_FRONT || upper.collar() == Garments.Collar.CROSSED));
        if (outer == null && !coatLike && ((seed >>> 17) & 3) != 0) {
            outer = Garments.pick(culture, subpool, year, role, female, seed ^ 0x71C9E55BL,
                    Garments.Part.OUTER);
        }
        if (outer != null && outer == upper) {
            outer = null;
        }
        // a top garment needs something on the legs
        if (lower == null && upper != null && upper.part() == Garments.Part.TOP) {
            lower = Garments.pick(culture, subpool, year, role, female, seed ^ 0x5A1D9C3FL,
                    Garments.Part.LOWER);
        }

        // ---- the cloth. Each type takes its own colour out of the four a place dyes, so ten garments
        // off one street are not ten copies of the same cloth in a different length.
        int cloth = upper == null ? top : clothFor(top, bottom, trim, upper.id());
        int lowerC = lower != null ? clothFor(bottom, top, trim, lower.id())
                : SkinPoolDatabase.shade(cloth, -20);
        if (Math.abs(lum(cloth) - lum(lowerC)) < 30 && lower != null) {
            lowerC = SkinPoolDatabase.shade(lowerC, lum(cloth) > 90 ? -40 : 40);
        }
        if (upper != null) {
            dress(s, upper, cloth, lowerC, trim, female);
            // ---- the silhouette. A kaftan is cut wide at the shoulder, a boubou wider still, a gown
            // swings at the hem, a doublet is cut to the body. Without this every garment was the same
            // box in a different cloth, which is what the owner saw.
            Garments.Shape sh = Garments.shape(upper.id());
            if (sh.shoulder() > 0) {
                shoulder(s, upper.id(), cloth, sh.shoulder());
            }
            if (sh.flare() > 0) {
                flare(s, lowerC, cloth, sh.flare(), legRowsHint(upper));
            }
        }

        // ---- the lower cloth: the length the garment itself brings
        int legRows;
        String lowerId;
        if (lower != null) {
            legRows = Math.min(12, Math.max(0, lower.drape()));
            lowerId = lower.id();
        } else if (upper != null && upper.drape() >= 0) {
            legRows = Math.min(12, Math.max(0, upper.drape()));
            lowerId = upper.id();
        } else {
            legRows = Math.min(12, cut.leg());
            lowerId = "";
        }
        boolean wrapped = "dhoti".equals(lowerId) || "sarong".equals(lowerId) || "lungi".equals(lowerId)
                || "sari".equals(lowerId) || "wrapper_skirt".equals(lowerId)
                || "cueitl".equals(lowerId) || "shendyt".equals(lowerId) || "kaccha".equals(lowerId);
        for (String side : new String[]{"r", "l"}) {
            for (String faceName : new String[]{"front", "back", "right", "left"}) {
                int[] lr = R.get("leg_" + side + "_" + faceName);
                int tex = wrapped ? blend(lowerC, trim, 0.12) : lowerC;
                for (int j = 0; j < 12; j++) {
                    for (int i = 0; i < lr[2]; i++) {
                        int c = j < legRows ? ((i + j) % 3 == 0
                                ? SkinPoolDatabase.shade(tex, -14) : tex) : tone;
                        s.put(lr[0] + i, lr[1] + j, c);
                    }
                }
                if (legRows > 0 && legRows < 12) {              // the hem: where the cloth stops
                    for (int i = 0; i < lr[2]; i++) {
                        s.put(lr[0] + i, lr[1] + legRows - 1, SkinPoolDatabase.shade(tex, -30));
                    }
                }
                if (legRows >= 10) {                            // a pleat down a long lower cloth
                    int fold = SkinPoolDatabase.shade(tex, -18);
                    for (int j = 1; j < Math.min(legRows, 9); j++) {
                        s.put(lr[0] + 1, lr[1] + j, fold);
                    }
                }
            }
            // the footwear, from the place's own list for this year: sandals in Rome, clogs in
            // Kyoto, moccasins in Mesoamerica, wraps in the Deccan, boots where there is snow
            Garments.Shoe kind = Garments.foot(culture, year, seed).shoe();
            int leather = kind == Garments.Shoe.SANDALS ? blend(0x5A3A22, trim, 0.22)
                    : kind == Garments.Shoe.CLOGS ? blend(0x8A6A3A, bottom, 0.2)
                    : kind == Garments.Shoe.MOCCASINS ? blend(0x7A5230, bottom, 0.35)
                    : kind == Garments.Shoe.WRAPS ? blend(bottom, trim, 0.4)
                    : blend(0x4A3324, bottom, 0.25);
            int shoeUpper = SkinPoolDatabase.shade(leather, 14);
            int sole = SkinPoolDatabase.shade(leather, -28);
            int shoeRows = kind == Garments.Shoe.BOOTS ? 4 : kind == Garments.Shoe.WRAPS ? 2 : 2;
            if (kind != Garments.Shoe.BAREFOOT) {
                shoe(s, side, shoeUpper, sole, shoeRows, kind == Garments.Shoe.SANDALS);
            }
        }

        // ---- the outer garment: its own shape, from its own collar
        if (outer != null && upper != null) {
            int outerC = clothFor(bottom, top, trim, outer.id());
            boolean band = outer.collar() == Garments.Collar.SHAWL;      // a shoulder cloth
            boolean frontOnly = band || "apron".equals(outer.id());
            int rows = band ? 4 : Math.min(12, Math.max(4, cut.outer()));
            String[] faces = frontOnly ? new String[]{"body_front"}
                    : new String[]{"body_front", "body_back", "body_right", "body_left"};
            for (String r : faces) {
                int[] rr = R.get(r);
                for (int j = 0; j < rows; j++) {
                    for (int i = 0; i < rr[2]; i++) {
                        int c = outerC;
                        if (r.endsWith("right") || r.endsWith("left")) {
                            c = SkinPoolDatabase.shade(outerC, -12);
                        }
                        if (j == rows - 1) {
                            c = SkinPoolDatabase.shade(outerC, -26);          // the hem of the coat
                        }
                        s.put(rr[0] + i, rr[1] + j, c);
                    }
                }
            }
            if (outer.collar() == Garments.Collar.OPEN_FRONT) {             // two panels, a gap between
                for (int j = 1; j < rows; j++) {
                    s.put(20 + 3, 20 + j, SkinPoolDatabase.shade(upper == null ? 0 : cloth, -40));
                }
            }
            int[] fr = R.get("body_front");
            for (int i = 0; i < fr[2]; i++) {                               // a trimmed edge
                s.put(fr[0] + i, fr[1], SkinPoolDatabase.shade(trim, -36));
            }
        }

        // the women's drape, over the shoulders and the head
        if (female && cut.drape() > 0) {
            int len = Math.max(1, (int) Math.round(cut.drape() * 2));
            for (int j = 0; j < len; j++) {
                for (int i = 0; i < 8; i++) {
                    s.put(32 + i, 36 + j, SkinPoolDatabase.shade(cloth, 12));
                }
            }
            if (cut.drape() >= 0.35) {
                for (int i = 0; i < 8; i++) {
                    s.put(40 + i, 8 + 7, SkinPoolDatabase.shade(cloth, 10));
                }
            }
        }
    }


    /** A garment of one of the wanted parts, chosen from the same wardrobe, by the person's number. */
    private static Garments.Garment companion(List<Garments.Garment> opts, long seed,
                                              Garments.Part... want) {
        List<Garments.Garment> cand = new java.util.ArrayList<>();
        for (Garments.Garment g : opts) {
            for (Garments.Part w : want) {
                if (g.part() == w) {
                    cand.add(g);
                    break;
                }
            }
        }
        return cand.isEmpty() ? null : cand.get((int) Math.floorMod(seed, cand.size()));
    }


    /**
     * Wider shoulders: the garment is cut to stand off the body, so it is drawn one or two pixels
     * proud on the second layer along the top of the torso and over the shoulder cap.
     */
    private static void shoulder(Skin s, String id, int cloth, int width) {
        int proud = SkinPoolDatabase.shade(cloth, 8);
        int edge = SkinPoolDatabase.shade(cloth, -18);
        for (int w = 0; w < Math.min(width, 2); w++) {
            for (String r : new String[]{"jacket_front", "jacket_back"}) {
                int[] rr = R.get(r);
                for (int i = 0; i < rr[2]; i++) {
                    s.put(rr[0] + i, rr[1] + w, w == 0 ? edge : proud);
                }
            }
            for (String r : new String[]{"jacket_right", "jacket_left"}) {
                int[] rr = R.get(r);
                for (int i = 0; i < rr[2]; i++) {
                    for (int j = 0; j < 2; j++) {
                        s.put(rr[0] + i, rr[1] + j, SkinPoolDatabase.shade(proud, -10));
                    }
                }
            }
            // the shoulder cap, over the top row of each sleeve
            for (String side : new String[]{"r", "l"}) {
                int[] rr = R.get("sleeve_" + side + "_front");
                if (rr != null) {
                    for (int i = 0; i < rr[2]; i++) {
                        s.put(rr[0] + i, rr[1] + w, SkinPoolDatabase.shade(proud, -4));
                    }
                }
            }
        }
    }

    /**
     * A hem that swings: the cloth is drawn wider at the bottom on the trousers layer, so a gown, a
     * lehenga and a boubou stop being a straight tube. Three steps, because three is what a 4-pixel
     * leg can carry.
     */
    private static void flare(Skin s, int lower, int cloth, int amount, int rows) {
        int[] steps = {0, 1, 1, 2};
        int grow = steps[Math.min(3, amount)];
        int c = SkinPoolDatabase.shade(lower, -6);
        int edge = SkinPoolDatabase.shade(lower, -26);
        for (String side : new String[]{"r", "l"}) {
            for (String face : new String[]{"front", "back", "right", "left"}) {
                int[] rr = R.get("leg_" + side + "_" + face);
                int[] tt = R.get("trouser_" + side + "_" + face);
                if (tt == null) {
                    continue;
                }
                int from = Math.max(0, 12 - Math.max(4, rows));
                for (int j = from; j < 12; j++) {
                    for (int i = 0; i < tt[2]; i++) {
                        s.put(tt[0] + i, tt[1] + j, i == 0 || i == tt[2] - 1 ? edge : c);
                    }
                }
            }
        }
    }

    /** How many rows of leg the upper garment leaves bare, for the flare to start at the right place. */
    private static int legRowsHint(Garments.Garment g) {
        return g.drape() >= 0 ? Math.min(12, g.drape()) : 8;
    }

    /** One colour per garment type, drawn from the four a place dyes, so two types never share a cloth. */
    private static int clothFor(int top, int bottom, int trim, String id) {
        int[] palette = {top, blend(bottom, top, 0.3), trim, blend(top, trim, 0.45),
                blend(bottom, trim, 0.5)};
        int h = Math.abs(id.hashCode() + id.length() * 31);
        return blend(top, palette[h % palette.length], 0.45);
    }


    /**
     * Put one garment on the body: its cloth, its collar, its sleeves, its belt.
     *
     * <p>The same eight by twelve torso carries all of them, because a garment is a set of decisions
     * about that rectangle, not a different rectangle. That is how one page of rules becomes a wardrobe.
     */
    private static void dress(Skin s, Garments.Garment g, int cloth, int bottom, int trim, boolean female) {
        int slv = Math.min(11, Math.max(0, g.sleeve()));
        int hem = Math.min(12, Math.max(1, g.hem()));
        // ---- the cloth itself, all four faces of the torso
        for (String r : new String[]{"body_front", "body_back", "body_right", "body_left"}) {
            int[] rr = R.get(r);
            for (int j = 0; j < hem; j++) {
                for (int i = 0; i < rr[2]; i++) {
                    int c = cloth;
                    if (r.endsWith("right") || r.endsWith("left")) {
                        c = SkinPoolDatabase.shade(cloth, -12);
                    } else if (i == 0 || i == rr[2] - 1) {
                        c = SkinPoolDatabase.shade(cloth, -14);
                    }
                    s.put(rr[0] + i, rr[1] + j, c);
                }
            }
        }
        // ---- the collar: the garment's own signature, and the thing that makes a hanfu a hanfu
        int trimC = SkinPoolDatabase.shade(trim, -18);
        int edge = SkinPoolDatabase.shade(cloth, -34);
        switch (g.collar()) {
            case CROSSED -> {                                   // right side over left, the youren collar
                for (int k = 0; k < 5; k++) {
                    s.put(20 + 2 + k, 20 + k, edge);
                    s.put(20 + 3 + k, 20 + k, trimC);
                }
                for (int i = 0; i < 8; i++) {
                    s.put(20 + i, 20 + 5, SkinPoolDatabase.shade(cloth, -8));
                }
            }
            case V_NECK -> {
                for (int k = 0; k < 4; k++) {
                    s.put(20 + 3 - k / 2, 20 + k, edge);
                    s.put(20 + 4 + k / 2, 20 + k, edge);
                }
            }
            case SQUARE -> {
                for (int i = 2; i < 6; i++) {
                    s.put(20 + i, 20, trimC);
                    s.put(20 + i, 20 + 1, trimC);
                }
            }
            case STAND -> {
                for (int i = 2; i < 6; i++) {
                    s.put(20 + i, 20, trimC);
                }
                s.column("body_front", 3, edge, 1, 8);
                s.column("body_front", 4, SkinPoolDatabase.shade(cloth, 10), 1, 8);
            }
            case OPEN_FRONT -> {
                s.column("body_front", 3, edge, 0, 12);
                s.column("body_front", 4, SkinPoolDatabase.shade(cloth, 8), 0, 12);
                for (int j : new int[]{3, 6, 9}) {
                    s.put(20 + 4, 20 + j, SkinPoolDatabase.shade(trim, -6));
                }
            }
            case WRAP -> {                                      // a cloth wound over one shoulder
                for (int k = 0; k < 8; k++) {
                    s.put(20 + 1 + k, 20 + Math.min(6, k), SkinPoolDatabase.shade(cloth, 16));
                }
            }
            case BOAT -> {
                for (int i = 1; i < 7; i++) {
                    s.put(20 + i, 20, trimC);
                }
            }
            case HIGH -> {
                for (int i = 1; i < 7; i++) {
                    s.put(20 + i, 20, trimC);
                    s.put(20 + i, 20 + 1, edge);
                }
            }
            case SHAWL -> {                                     // a length of cloth over both shoulders
                for (int i = 0; i < 8; i++) {
                    s.put(20 + i, 20, SkinPoolDatabase.shade(cloth, 20));
                    s.put(20 + i, 20 + 1, SkinPoolDatabase.shade(cloth, 8));
                }
            }
            default -> { }
        }
        // ---- the sleeves, on the arm and one pixel proud where the garment hangs loose
        for (String side : new String[]{"r", "l"}) {
            for (String faceName : new String[]{"front", "back", "right", "left"}) {
                int[] rr = R.get("arm_" + side + "_" + faceName);
                for (int j = 0; j < slv; j++) {
                    for (int i = 0; i < rr[2]; i++) {
                        s.put(rr[0] + i, rr[1] + j, (j == slv - 1) ? SkinPoolDatabase.shade(cloth, -22) : cloth);
                    }
                }
                if (g.loose() || slv >= 9) {
                    int[] sl = R.get("sleeve_" + side + "_" + faceName);
                    if (sl != null) {
                        for (int j = 0; j < Math.max(2, slv); j++) {
                            for (int i = 0; i < sl[2]; i++) {
                                s.put(sl[0] + i, sl[1] + j, j == 0 ? SkinPoolDatabase.shade(cloth, 6)
                                        : SkinPoolDatabase.shade(cloth, -6));
                            }
                        }
                    }
                }
            }
        }
        if (g.loose() || slv >= 9) {
            closeShell(s, "sleeve_r", SkinPoolDatabase.shade(cloth, -6), SkinPoolDatabase.shade(cloth, -30));
            closeShell(s, "sleeve_l", SkinPoolDatabase.shade(cloth, -6), SkinPoolDatabase.shade(cloth, -30));
        }
        // ---- what holds it in
        switch (g.belt()) {
            case 1 -> {                                          // a tie
                s.band("body_front", 9, SkinPoolDatabase.shade(cloth, -26));
                s.band("body_back", 9, SkinPoolDatabase.shade(cloth, -26));
                s.put(20 + 3, 20 + 9, trimC);
                s.put(20 + 4, 20 + 9, trimC);
            }
            case 2 -> {                                          // a sash
                for (int j = 8; j < 10; j++) {
                    for (int i = 0; i < 8; i++) {
                        s.put(20 + i, 20 + j, (i + j) % 3 == 0 ? SkinPoolDatabase.shade(trim, -30) : trimC);
                    }
                }
                s.put(20 + 5, 20 + 10, trimC);                   // the end of the sash, hanging
            }
            case 3 -> {                                          // a girdle of metal
                for (int i = 0; i < 8; i++) {
                    s.put(20 + i, 20 + 9, SkinPoolDatabase.shade(trim, 10));
                }
            }
            default -> { }
        }

    }

    /**
     * A shoe, as a shoe: all four sides of the leg, the sole face, and the same shape again one pixel
     * proud on the leg's second layer, so the foot steps out of the trouser instead of being a stripe
     * painted across the shin.
     *
     * @param rows   how tall the shoe is in pixels, 1 for a sandal and 3 for a boot
     * @param straps true for an open sandal, where skin shows between the straps
     */
    private static void shoe(Skin s, String side, int upper, int sole, int rows, boolean straps) {
        int r = Math.max(1, Math.min(4, rows));
        for (String faceName : new String[]{"front", "back", "right", "left"}) {
            int[] lr = R.get("leg_" + side + "_" + faceName);
            int[] tr = R.get("trouser_" + side + "_" + faceName);
            for (int k = 0; k < r; k++) {
                int j = lr[3] - 1 - k;
                for (int i = 0; i < lr[2]; i++) {
                    int c = k == 0 ? sole : upper;
                    if (straps && k == r - 1) {
                        continue;                       // bare skin above an open sandal
                    }
                    if (!straps && k == r - 1) {
                        c = SkinPoolDatabase.shade(upper, 18);       // the collar of the shoe
                    }
                    s.put(lr[0] + i, lr[1] + j, c);
                    if (tr != null && k < 2) {
                        s.put(tr[0] + i, tr[1] + j, SkinPoolDatabase.shade(c, -6));
                    }
                }
            }
            if (faceName.equals("front")) {
                closeShell(s, "trouser_" + side, upper, sole);
            }
            if (straps) {                               // the thong between the toes, on the front only
                if (faceName.equals("front")) {
                    for (int i = 0; i < lr[2]; i++) {
                        s.put(lr[0] + i, lr[1] + lr[3] - 1, sole);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the modern wardrobe
    private static void bottom(Skin s, List<String> g, int[] colours, int bottom) {
        String raw = pick(g, new String[]{"wide_jeans", "mom_denim", "jeans", "pleated_w", "trousers",
                "cargo", "joggers", "biker_shorts", "shorts", "midi_skirt", "sheath_dress",
                "slip_dress"}, "jeans");
        boolean wide = raw.equals("wide_jeans") || raw.equals("mom_denim");
        boolean biker = raw.equals("biker_shorts");
        String b = wide ? "jeans" : raw.equals("pleated_w") ? "trousers" : biker ? "shorts" : raw;
        boolean dress = b.equals("sheath_dress") || b.equals("slip_dress");
        int[] rows = switch (b) {
            case "shorts" -> new int[]{0, 5};
            case "midi_skirt" -> new int[]{0, 8};
            case "sheath_dress" -> new int[]{0, 6};
            case "slip_dress" -> new int[]{0, 8};
            default -> new int[]{0, 12};
        };
        for (String side : new String[]{"r", "l"}) {
            for (String faceName : new String[]{"front", "back", "right", "left"}) {
                String leg = "leg_" + side + "_" + faceName;
                int[] lr = R.get(leg);
                for (int j = rows[0]; j < rows[1]; j++) {
                    for (int i = 0; i < lr[2]; i++) {
                        s.put(lr[0] + i, lr[1] + j, bottom);
                    }
                }
            }
            int[] lrf = R.get("leg_" + side + "_front");
            if (b.equals("jeans")) {
                for (int i = 0; i < lrf[2]; i++) {
                    s.put(lrf[0] + i, lrf[1], SkinPoolDatabase.shade(bottom, -34));
                    s.put(lrf[0] + i, lrf[1] + 9, SkinPoolDatabase.shade(bottom, 18));
                }
            } else if (b.equals("trousers")) {
                s.column("leg_" + side + "_front", 1, SkinPoolDatabase.shade(bottom, -24), 0, 10);
                s.column("leg_" + side + "_front", 2, SkinPoolDatabase.shade(bottom, -8), 0, 10);
            } else if (b.equals("cargo")) {
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        s.put(lrf[0] + i, lrf[1] + 3 + j, SkinPoolDatabase.shade(bottom, -18));
                    }
                }
            } else if (b.equals("joggers")) {
                s.band("leg_" + side + "_front", 8, SkinPoolDatabase.shade(bottom, -30));
                s.column("leg_" + side + "_front", 1, SkinPoolDatabase.shade(bottom, 26), 0, 9);
            } else if (wide) {
                s.column("leg_" + side + "_front", 0, SkinPoolDatabase.shade(bottom, -18), 0, 10);
                s.column("leg_" + side + "_front", 3, SkinPoolDatabase.shade(bottom, 16), 0, 10);
            } else if (biker) {
                s.band("leg_" + side + "_front", 3, SkinPoolDatabase.shade(bottom, -22));
            } else if (dress) {
                s.band("leg_" + side + "_front", rows[1] - 1, SkinPoolDatabase.shade(bottom, -26));
            }
            // the trouser layer carries the second-layer garment where there is one
            if (dress || b.equals("midi_skirt")) {
                for (String faceName : new String[]{"front", "back", "right", "left"}) {
                    String tr = "trouser_" + side + "_" + faceName;
                    int[] rr = R.get(tr);
                    for (int j = rows[0]; j < rows[1]; j++) {
                        for (int i = 0; i < rr[2]; i++) {
                            s.put(rr[0] + i, rr[1] + j, bottom);
                        }
                    }
                }
            }
        }
        // the waistband: the torso's last two rows are trousers in a vanilla skin, not skin
        for (String r : new String[]{"body_front", "body_back", "body_right", "body_left"}) {
            int[] rr = R.get(r);
            for (int j = 10; j < 12; j++) {
                for (int i = 0; i < rr[2]; i++) {
                    s.put(rr[0] + i, rr[1] + j, r.equals("body_front") ? bottom
                            : SkinPoolDatabase.shade(bottom, -10));
                }
            }
        }
    }

    private static void topAndOuter(Skin s, List<String> g, int[] colours, int top, int outer,
                                    boolean female) {
        String traw = pick(g, new String[]{"crew_tee", "graphic_tee", "polo", "button_down", "hoodie",
                "sweatshirt", "cardigan", "oversized_shirt", "ribbed_cardigan", "mock_turtleneck",
                "v_neck_tee", "tank", "camisole", "crop_top"}, "crew_tee");
        boolean oversized = "oversized_shirt".equals(traw);
        boolean ribbed = "ribbed_cardigan".equals(traw);
        String t = oversized ? "button_down" : ribbed ? "cardigan" : traw;
        int rows = t.equals("crop_top") ? 5 : t.equals("tank") || t.equals("camisole") ? 8 : 10;
        if (t.equals("tank") || t.equals("camisole")) {           // bare shoulders, then the cloth
            s.shadeFill("body_front", SkinPoolDatabase.shade(top, 0), 12);
        }
        for (String r : new String[]{"body_front", "body_back", "body_right", "body_left"}) {
            for (int j = 0; j < rows; j++) {
                int[] rr = R.get(r);
                for (int i = 0; i < rr[2]; i++) {
                    int c = top;
                    if (i == 0 || i == rr[2] - 1) {
                        c = SkinPoolDatabase.shade(top, -14);
                    } else if (j == 0) {
                        c = SkinPoolDatabase.shade(top, 10);
                    }
                    s.put(rr[0] + i, rr[1] + j, c);
                }
            }
        }
        // the sleeves and the neckline, by garment
        int sleeve = switch (t) {
            case "tank", "camisole" -> 0;
            case "crew_tee", "graphic_tee", "polo", "v_neck_tee", "crop_top" -> 2;
            default -> 4;
        };
        if (oversized) {
            sleeve = 6;                                       // a shirt that hangs off the shoulder
        }
        for (String side : new String[]{"r", "l"}) {
            for (String faceName : new String[]{"front", "back", "right", "left"}) {
                String arm = "arm_" + side + "_" + faceName;
                int[] rr = R.get(arm);
                for (int j = 0; j < sleeve; j++) {
                    for (int i = 0; i < rr[2]; i++) {
                        s.put(rr[0] + i, rr[1] + j, top);
                    }
                }
            }
        }
        if (ribbed) {
            for (int i = 1; i < 8; i += 2) {
                s.column("body_front", i, SkinPoolDatabase.shade(top, -24), 0, rows);
            }
        }
        switch (t) {
            case "polo" -> {
                s.band("body_front", 0, SkinPoolDatabase.shade(top, 24));
                s.column("body_front", 3, SkinPoolDatabase.shade(top, -40), 1, 4);
                s.column("body_front", 4, SkinPoolDatabase.shade(top, -40), 1, 4);
            }
            case "button_down", "cardigan" -> {
                s.band("body_front", 0, SkinPoolDatabase.shade(top, 26));
                s.column("body_front", 3, SkinPoolDatabase.shade(top, -44), 1, 9);
                s.column("body_front", 4, SkinPoolDatabase.shade(top, -22), 1, 9);
                for (int j : new int[]{3, 6, 9}) {
                    s.put(20 + 4, 20 + j, 0xF0F0EC);
                }
            }
            case "hoodie" -> {
                for (int j = 5; j < 8; j++) {          // the hood, down, on the hat layer's back
                    for (int i = 0; i < 8; i++) {
                        s.put(56 + i, 8 + j, SkinPoolDatabase.shade(top, j % 2 == 0 ? -8 : 0));
                    }
                }
                for (int i = 3; i < 5; i++) {
                    s.column("body_front", i, 0xEEECE6, 1, 4);
                }
                for (int i = 2; i < 6; i++) {          // the kangaroo pocket
                    s.put(20 + i, 20 + 6, SkinPoolDatabase.shade(top, -34));
                }
            }
            case "mock_turtleneck" -> {
                s.band("body_front", 0, SkinPoolDatabase.shade(top, 30));
                s.band("body_front", 1, SkinPoolDatabase.shade(top, 18));
            }
            case "graphic_tee" -> {
                int acc = SkinPoolDatabase.shade(top, -80);
                if (Math.abs(lum(acc) - lum(top)) < 60) {
                    acc = lum(top) < 128 ? 0xF6F6F2 : 0x181818;
                }
                for (int j = 2; j < 6; j++) {
                    for (int i = 2; i < 6; i++) {
                        s.put(20 + i, 20 + j, (i + j) % 2 == 0 ? acc
                                : SkinPoolDatabase.shade(acc, -20));
                    }
                }
                for (int j = 2; j < 7; j++) {
                    for (int i = 1; i < 7; i++) {
                        s.put(32 + i, 20 + j, (i + j) % 2 == 0 ? acc
                                : SkinPoolDatabase.shade(acc, -26));    // the print on the back too
                    }
                }
            }
            case "v_neck_tee" -> {
                for (int j = 0; j < 3; j++) {
                    for (int i = 3 - j; i < 5 + j; i++) {
                        s.put(20 + i, 20 + j, SkinPoolDatabase.shade(colours[0], 0));
                    }
                }
            }
            default -> { }
        }
        // the outer layer, when one is worn
        String oraw = pick(g, new String[]{"trench", "trench_w", "bomber", "shacket", "blazer",
                "oversized_blazer", "puffer_vest", "cropped_puffer"}, null);
        boolean over = "oversized_blazer".equals(oraw);
        boolean cropped = "cropped_puffer".equals(oraw);
        String o = over ? "blazer" : cropped ? "puffer_vest" : oraw;
        if (o != null) {
            int rowsO = o.equals("puffer_vest") ? (cropped ? 8 : 11) : 12;
            for (String r : new String[]{"jacket_front", "jacket_back", "jacket_right", "jacket_left"}) {
                for (int j = 0; j < rowsO; j++) {
                    int[] rr = R.get(r);
                    for (int i = 0; i < rr[2]; i++) {
                        int c = r.endsWith("front") ? outer : SkinPoolDatabase.shade(outer, -16);
                        if (o.equals("shacket") && ((i / 2) + (j / 2)) % 2 == 0) {
                            c = SkinPoolDatabase.shade(outer, -40);
                        }
                        if (o.equals("puffer_vest") && (j / 2) % 2 == 0) {
                            c = SkinPoolDatabase.shade(outer, 12);
                        }
                        s.put(rr[0] + i, rr[1] + j, c);
                    }
                }
            }
            closeShell(s, "jacket", outer, SkinPoolDatabase.shade(outer, -26));
            for (String side : new String[]{"r", "l"}) {              // the coat's sleeves
                for (String faceName : new String[]{"front", "back", "right", "left"}) {
                    String sl = "sleeve_" + side + "_" + faceName;
                    int[] rr = R.get(sl);
                    for (int j = 0; j < (o.equals("puffer_vest") ? 0 : 7); j++) {
                        for (int i = 0; i < rr[2]; i++) {
                            s.put(rr[0] + i, rr[1] + j, SkinPoolDatabase.shade(outer, -10));
                        }
                    }
                }
                if (!o.equals("puffer_vest")) {
                    closeShell(s, "sleeve_" + side, SkinPoolDatabase.shade(outer, -10),
                            SkinPoolDatabase.shade(outer, -34));
                }
            }
            switch (o) {
                case "trench" -> {
                    s.band("jacket_front", 6, SkinPoolDatabase.shade(outer, -30));
                    s.put(20 + 3, 36 + 6, 0xBCB096);
                    s.put(20 + 4, 36 + 6, 0xBCB096);
                    s.column("jacket_front", 4, SkinPoolDatabase.shade(outer, -44), 0, 12);
                }
                case "blazer" -> {
                    int lapel = over ? 3 : 2;
                    for (int j = 0; j < 5; j++) {
                        int left = 2 + Math.max(0, 2 - j);
                        for (int k = 0; k < lapel; k++) {
                            s.put(20 + left + k, 36 + j, SkinPoolDatabase.shade(outer, 30));
                        }
                    }
                }
                case "bomber" -> {
                    s.band("jacket_front", 7, SkinPoolDatabase.shade(outer, -46));
                    s.band("jacket_front", 8, SkinPoolDatabase.shade(outer, -52));
                    s.column("jacket_front", 4, SkinPoolDatabase.shade(outer, 24), 1, 7);
                }
                default -> { }
            }
        }
    }

    private static void feet(Skin s, List<String> g, int tone, Cut cut) {
        String raw = pick(g, new String[]{"dad_sneakers", "sneakers", "chelsea", "loafers"}, null);
        boolean chunky = "dad_sneakers".equals(raw);
        String f = chunky ? "sneakers" : raw;
        for (String side : new String[]{"r", "l"}) {
            if (f == null) {
                // bare feet. The old painter darkened two rows of skin and called it a shoe, which is
                // not a shoe and never was - the foot is the skin it already is, with a shadow sole.
                int[] lr = R.get("leg_" + side + "_front");
                int sole = SkinPoolDatabase.shade(tone, -34);
                for (String faceName : new String[]{"front", "back", "right", "left"}) {
                    int[] fr = R.get("leg_" + side + "_" + faceName);
                    for (int i = 0; i < fr[2]; i++) {
                        s.put(fr[0] + i, fr[1] + fr[3] - 1, sole);
                    }
                }
                continue;
            }
            int upper = switch (f) {
                case "sneakers" -> 0xECEAE6;
                case "chelsea" -> 0x3A2C22;
                default -> 0x3A2C22;
            };
            int sole = switch (f) {
                case "sneakers" -> 0xEEEEEA;
                case "chelsea" -> 0x24201E;
                default -> SkinPoolDatabase.shade(upper, 90);
            };
            shoe(s, side, upper, sole, chunky ? 4 : 3, false);
            int[] fr = R.get("leg_" + side + "_front");
            if (f.equals("loafers")) {
                s.put(fr[0] + 1, fr[1] + 10, SkinPoolDatabase.shade(upper, -30));
                s.put(fr[0] + 2, fr[1] + 10, 0xE2CA76);        // the brass bar
            }
            if (chunky) {                                      // the thick sole of a dad sneaker
                for (String faceName : new String[]{"front", "back", "right", "left"}) {
                    int[] rr = R.get("leg_" + side + "_" + faceName);
                    for (int i = 0; i < rr[2]; i++) {
                        s.put(rr[0] + i, rr[1] + rr[3] - 1, 0xE2E2DE);
                    }
                }
            }
        }
    }

    private static void cap(Skin s, int colour, boolean bucket) {
        int c = SkinPoolDatabase.shade(colour, -60);
        for (String r : new String[]{"hat_front", "hat_back", "hat_left", "hat_right"}) {
            int rows = bucket ? 3 : 2;
            for (int j = 0; j < rows; j++) {
                int[] rr = R.get(r);
                for (int i = 0; i < rr[2]; i++) {
                    s.put(rr[0] + i, rr[1] + j, c);
                }
            }
        }
        // the crown and the underside: a cap with no top is a hole with a brim
        fill(s, "hat_top", SkinPoolDatabase.shade(c, 10), c);
        fill(s, "hat_bottom", SkinPoolDatabase.shade(c, -30), SkinPoolDatabase.shade(c, -38));
        s.band("hat_front", bucket ? 3 : 2, SkinPoolDatabase.shade(c, -20));
    }

    private static void uniform(Skin s, List<String> g, int tone) {
        String u = g.contains("vestment") ? "vestment"
                : g.contains("uniform_green") ? "green" : "khaki";
        int cloth = switch (u) {
            case "vestment" -> 0xECEAE2;
            case "green" -> 0x3E4C3A;
            default -> 0x6C684C;
        };
        for (String r : new String[]{"body_front", "body_back", "body_right", "body_left"}) {
            s.shadeFill(r, cloth, 10);
        }
        s.band("body_front", 4, SkinPoolDatabase.shade(cloth, -46));    // the belt
        s.band("body_right", 4, SkinPoolDatabase.shade(cloth, -46));
        s.band("body_left", 4, SkinPoolDatabase.shade(cloth, -46));
        if (u.equals("vestment")) {
            for (int j = 1; j < 9; j++) {
                s.put(20 + 2, 20 + j, SkinPoolDatabase.shade(cloth, -30));
                s.put(20 + 5, 20 + j, SkinPoolDatabase.shade(cloth, -30));
            }
        } else {
            for (int j = 0; j < 2; j++) {                              // the breast pocket
                for (int i = 0; i < 2; i++) {
                    s.put(20 + 1 + i, 20 + 2 + j, SkinPoolDatabase.shade(cloth, -18));
                }
            }
        }
        for (String side : new String[]{"r", "l"}) {
            for (String faceName : new String[]{"front", "back", "right", "left"}) {
                String leg = "leg_" + side + "_" + faceName;
                int[] rr = R.get(leg);
                for (int j = 0; j < 10; j++) {              // rows 10-11 are the boot, left alone
                    for (int i = 0; i < rr[2]; i++) {
                        s.put(rr[0] + i, rr[1] + j, cloth);
                    }
                }
                for (int i = 0; i < rr[2]; i++) {           // the crease down the front of the leg
                    if (faceName.equals("front")) {
                        s.put(rr[0] + i, rr[1] + 9, SkinPoolDatabase.shade(cloth, -26));
                    }
                }
            }
        }
    }

    /** The trade's mark, on top of whatever cloth is worn - a strap, a pouch, a cross, beads. */
    /**
     * The mark of a trade, on the chest.
     *
     * <p>This used to be a handful of cases: a merchant, a scribe, a scholar, a healer, a priest. Every
     * other trade - a farmer, a herder, an artisan, a citizen, a villager - was given nothing at all, so
     * six of the thirteen trades came out as the identical picture, and a scribe, a scholar and an
     * advisor were the same person in different words. That is the same fault the owner found in the
     * ages: a label that never reaches the drawing.
     *
     * <p>Every trade now carries its own few pixels. They are drawn from the person's own cloth, in the
     * trim's colour, so a mark sits on the garment instead of floating: a sheaf for a farmer, a crook for
     * a herder, a hammer for an artisan, a coin for a merchant, a quill for a scribe, a scroll for a
     * scholar, a cross for a healer, a seal for an advisor, a stitch for a villager, a pin for a citizen,
     * a strap for a soldier and a guard, a rosette for a priest.
     */
    private static void mark(Skin s, String role, int trim, List<String> g) {
        if (role == null) {
            return;
        }
        // below the sash and the collar, on the belly of the garment, where nothing else is drawn
        int x = 20 + 3, y = 20 + 8;
        int c = SkinPoolDatabase.shade(trim, -38);
        int hi = SkinPoolDatabase.shade(trim, 30);
        int white = 0xE8E4DC;
        switch (role.toLowerCase(java.util.Locale.ROOT)) {
            case "farmer", "peasant", "labourer", "ploughman" -> {          // a sheaf
                dot(s, x, y, 1, 0, c); dot(s, x, y, 1, 1, c); dot(s, x, y, 1, 2, hi);
                dot(s, x, y, 0, 2, c); dot(s, x, y, 2, 2, c); dot(s, x, y, 1, 3, c);
            }
            case "herder", "shepherd", "drover" -> {                        // a crook
                dot(s, x, y, 0, 0, c); dot(s, x, y, 0, 1, c); dot(s, x, y, 0, 2, c);
                dot(s, x, y, 0, 3, c); dot(s, x, y, 1, 0, hi); dot(s, x, y, 2, 1, c);
            }
            case "artisan", "smith", "weaver", "potter", "carpenter" -> {   // a hammer
                dot(s, x, y, 0, 0, hi); dot(s, x, y, 1, 0, hi); dot(s, x, y, 2, 0, c);
                dot(s, x, y, 1, 1, c); dot(s, x, y, 1, 2, c); dot(s, x, y, 1, 3, c);
            }
            case "merchant", "trader", "pedlar", "money_changer", "paper_seller", "news_writer",
                 "town_crier" -> {                                          // a coin
                dot(s, x, y, 1, 0, hi); dot(s, x, y, 2, 0, c); dot(s, x, y, 1, 1, c);
                dot(s, x, y, 2, 1, hi); dot(s, x, y, 1, 2, c); dot(s, x, y, 2, 2, c);
            }
            case "scribe", "clerk", "reader" -> {                           // a pen, stood on its nib
                dot(s, x, y, 0, 0, c); dot(s, x, y, 0, 1, c); dot(s, x, y, 0, 2, hi);
                dot(s, x, y, 1, 3, c); dot(s, x, y, 1, 2, c);
            }
            case "scholar", "teacher" -> {                                  // a scroll, unrolled
                dot(s, x, y, 0, 0, c); dot(s, x, y, 0, 1, c); dot(s, x, y, 0, 2, c); dot(s, x, y, 0, 3, c);
                dot(s, x, y, 3, 0, c); dot(s, x, y, 3, 1, hi); dot(s, x, y, 3, 2, c); dot(s, x, y, 3, 3, c);
                dot(s, x, y, 1, 1, hi); dot(s, x, y, 2, 2, hi);
            }
            case "advisor", "elder", "chief", "noble" -> {                  // a seal
                dot(s, x, y, 1, 0, hi); dot(s, x, y, 2, 0, hi); dot(s, x, y, 1, 1, c);
                dot(s, x, y, 2, 1, c); dot(s, x, y, 1, 2, hi); dot(s, x, y, 2, 2, hi);
                dot(s, x, y, 1, 3, c);
            }
            case "healer", "midwife", "apothecary" -> {                     // the healer's cross
                dot(s, x, y, 1, 0, white); dot(s, x, y, 0, 1, white); dot(s, x, y, 1, 1, white);
                dot(s, x, y, 2, 1, white); dot(s, x, y, 1, 2, white);
            }
            case "soldier" -> {                                             // a baldric
                dot(s, x, y, 0, 3, c); dot(s, x, y, 1, 2, hi); dot(s, x, y, 2, 1, c); dot(s, x, y, 3, 0, hi);
            }
            case "guard", "watchman" -> {                                   // a baldric and a badge
                dot(s, x, y, 0, 3, c); dot(s, x, y, 1, 2, c); dot(s, x, y, 2, 1, c); dot(s, x, y, 3, 0, c);
                dot(s, x, y, 0, 0, hi); dot(s, x, y, 1, 0, hi); dot(s, x, y, 0, 1, hi);
            }
            case "priest", "monk", "imam", "rabbi" -> {                     // a rosette
                dot(s, x, y, 1, 0, hi); dot(s, x, y, 0, 1, hi); dot(s, x, y, 1, 1, white);
                dot(s, x, y, 2, 1, hi); dot(s, x, y, 1, 2, hi);
            }
            case "citizen", "townsman", "resident" -> {                     // a pin at the collar
                dot(s, x, y, 1, 0, hi); dot(s, x, y, 1, 1, c);
            }
            default -> {                                                    // a villager: a stitch
                dot(s, x, y, 0, 0, c); dot(s, x, y, 2, 0, c); dot(s, x, y, 1, 1, c);
                dot(s, x, y, 0, 2, c); dot(s, x, y, 2, 2, c);
            }
        }
    }

    /**
     * The apron of a trade that works with its hands at a bench or a fire.
     *
     * <p>The owner's report was that trades look the same. Marks are four pixels; an apron is a third of
     * the body, and it is the honest difference between an artisan and a farmer: one stands at a bench
     * in an apron, the other in the field without one.
     */
    private static void apron(Skin s, String role, int trim, int outer) {
        switch (role.toLowerCase(java.util.Locale.ROOT)) {
            case "artisan", "smith", "weaver", "potter", "carpenter", "tanner", "baker",
                 "butcher", "dyer" -> { }
            default -> {
                return;
            }
        }
        int[] f = R.get("body_front");
        int[] bk = R.get("body_back");
        if (f == null) {
            return;
        }
        int panel = SkinPoolDatabase.shade(blend(0x8A6B4A, trim, 0.25), -6);
        int edge = SkinPoolDatabase.shade(panel, -28);
        for (int j = 5; j < f[3]; j++) {
            for (int i = 1; i < f[2] - 1; i++) {
                s.put(f[0] + i, f[1] + j, (i == 1 || i == f[2] - 2) ? edge : panel);
            }
        }
        // the straps over the shoulders, and the tie at the back
        int[] sl = R.get("sleeve_r_front");
        int[] sr = R.get("sleeve_l_front");
        if (sl != null) {
            s.put(sl[0] + 1, sl[1] + 1, edge);
            s.put(sl[0] + 2, sl[1] + 1, edge);
        }
        if (sr != null) {
            s.put(sr[0] + 1, sr[1] + 1, edge);
            s.put(sr[0] + 2, sr[1] + 1, edge);
        }
        if (bk != null) {
            for (int i = 2; i < 6; i++) {
                s.put(bk[0] + i, bk[1] + 4, edge);
            }
        }
    }

    /** One pixel of a trade's mark, given from the mark's own corner. */
    private static void dot(Skin s, int x, int y, int dx, int dy, int c) {
        s.put(x + dx, y + dy, c);
    }

    // ------------------------------------------------------------------ helpers
    private static String pick(List<String> g, String[] candidates, String fallback) {
        for (String c : candidates) {
            if (g.contains(c)) {
                return c;
            }
        }
        return fallback;
    }

    private static int blend(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gg = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    /**
     * How the same dye looks after this many years of wear.
     *
     * <p>One rule for every place and century, because wear is not a fashion: a garment bought fresh is
     * deep and saturated, and a garment worn for fifty years is faded to a pale wash of itself. The
     * colour is still the colour the place chose - the light has merely gone out of it.
     *
     * @param years the person's age, or -1 for "no age known" - the colour, untouched
     */
    private static int worn(int rgb, int years) {
        if (years < 0) {
            return rgb;
        }
        double fresh = years < Ages.GROWN_FROM ? 1.0d
                : years >= Ages.OLD_FROM ? 0.0d
                : (double) (years - Ages.GROWN_FROM) / (Ages.OLD_FROM - Ages.GROWN_FROM);
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        float sat = (float) Math.min(1.0d, hsb[1] * (0.55d + 0.50d * fresh));
        float bri = (float) Math.max(0.12d, Math.min(1.0d, hsb[2] * (1.08d - 0.16d * fresh)));
        return java.awt.Color.HSBtoRGB(hsb[0], sat, bri) & 0xFFFFFF;
    }

    private static int lum(int rgb) {
        return (((rgb >> 16) & 0xFF) * 30 + ((rgb >> 8) & 0xFF) * 59 + (rgb & 0xFF) * 11) / 100;
    }
}

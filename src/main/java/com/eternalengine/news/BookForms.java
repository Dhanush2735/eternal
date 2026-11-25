package com.eternalengine.news;

import com.eternalengine.money.MarketService;
import com.eternalengine.money.ProductAtlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 22: the history book is bought, and it takes the form people keep notes in where and when it is bought: a
 * notebook of paper, a parchment book, a palm-leaf book, a birch-bark book, a bark-paper book, a papyrus roll, bamboo
 * slips, wax tablets, clay tablets or rune sticks. Where nobody writes, it is a memory aid: knotted cords in the Andes
 * and in Polynesia, a message stick in Australia, a tally stick anywhere else. Every form reads the same notes.
 *
 * <p>Which writing materials a place has comes from the product atlas (made there or brought, not yet gone: part B,
 * {@code economy/products_world.json}). A few places have their own rule, because an atlas area is large: the Aegean
 * wrote on clay (Linear A and B, about 1800 to 1200 BCE), the Norse cut runes in sticks (Greenland 985 to 1450;
 * Scandinavia 800 to 1400), south India kept to palm leaves until about 1850, and Kashmir and Gandhara wrote on birch
 * bark before palm leaves until about 1600, paper reached Europe's north later than Spain (1150; France 1300; the north
 * 1350), inland eastern and southern Africa wrote only from about 1880 (the coast and Ethiopia long before), the forest
 * kingdoms of West Africa from about 1800 (the Sahel towns long before), and the court of Kongo from about 1500. The
 * knotted cords of the Andes (khipu) are from about 2600 BCE.
 *
 * <p>The forms are listed best first: the most book-like first (paper, parchment, palm leaf, birch bark, bark paper,
 * papyrus, bamboo, wax, clay). A merchant or a trader sells up to three of them (always the cheapest among them), a
 * pedlar the cheapest, a paper seller the notebook, a newsletter writer the best. Prices are in days of a labourer's
 * pay (estimates); the seller turns them into the money of the place.
 */
public final class BookForms {

    /** One form of the history book. {@code product} is its writing material in the atlas ("" for a code rule). */
    public enum Form {
        NOTEBOOK("notebook", "Notebook", "notebooks", "paper", true,
                "A notebook of blank paper."),
        PARCHMENT("parchment", "Parchment book", "parchment books", "parchment", true,
                "A small book of blank parchment, made from skins."),
        PALM_LEAF("palm_leaf", "Palm-leaf book", "palm-leaf books", "palm_leaf", true,
                "Dried palm leaves between two boards, on a cord. The letters are scratched in and blackened."),
        BIRCH_BARK("birch_bark", "Birch-bark book", "birch-bark books", "birch_bark", true,
                "Sheets of birch bark to write or scratch on."),
        BARK_PAPER("bark_paper", "Bark-paper book", "bark-paper books", "bark_paper", true,
                "A long strip of bark paper, folded like a screen and painted with signs."),
        PAPYRUS("papyrus", "Papyrus roll", "papyrus rolls", "papyrus", true,
                "A blank roll of papyrus, made from reeds."),
        BAMBOO_SLIPS("bamboo_slips", "Bamboo slips", "bundles of bamboo slips", "bamboo_slips", true,
                "Narrow slips of bamboo tied with cord, written in columns with a brush."),
        WAX_TABLETS("wax_tablets", "Wax tablets", "sets of wax tablets", "wax_tablets", true,
                "Two wooden boards with a face of wax, and a stylus: write, smooth it, write again."),
        CLAY_TABLETS("clay_tablets", "Clay tablets", "clay tablets", "clay_tablets", true,
                "Soft clay tablets and a reed stylus to press the signs in."),
        RUNE_STICKS("rune_sticks", "Rune sticks", "rune sticks", "", true,
                "Flat sticks of wood to cut runes in with a knife."),
        KNOTTED_CORDS("knotted_cords", "Knotted cords", "knotted cords", "", false,
                "A main cord with hanging cords: a knot for each thing to remember."),
        MESSAGE_STICK("message_stick", "Message stick", "message sticks", "", false,
                "A carved stick: a mark for each thing to remember."),
        TALLY_STICK("tally_stick", "Tally stick", "tally sticks", "", false,
                "A stick to cut notches in: a notch for each thing to remember.");

        public final String id;
        public final String title;
        public final String plural;
        public final String product;
        /** True for writing; false for a memory aid (nobody writes there). */
        public final boolean writing;
        public final String lore;

        Form(String id, String title, String plural, String product, boolean writing, String lore) {
            this.id = id;
            this.title = title;
            this.plural = plural;
            this.product = product;
            this.writing = writing;
            this.lore = lore;
        }

        /** The item's registry name: the notebook is the step-20 {@code history_book}. */
        public String itemId() {
            return this == NOTEBOOK ? "history_book" : "history_book_" + id;
        }

        /** The name on the item and in the trade list. */
        public String offerName() {
            return title + " (history book)";
        }

        /** How it is used, after "Take this tally stick: " (memory aids). */
        public String howTo() {
            return switch (this) {
                case KNOTTED_CORDS -> "tie a knot for each thing you want to keep";
                case MESSAGE_STICK -> "cut a mark for each thing you want to keep";
                default -> "cut a notch for each thing you want to keep";
            };
        }

        public static Form byId(String id) {
            String k = id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            for (Form f : values()) {
                if (f.id.equals(k) || f.itemId().equals(k) || f.name().toLowerCase(Locale.ROOT).equals(k)) {
                    return f;
                }
            }
            return null;
        }
    }

    /** The order the atlas materials are tried in: the most book-like first. */
    static final Form[] ORDER = {Form.NOTEBOOK, Form.PARCHMENT, Form.PALM_LEAF, Form.BIRCH_BARK, Form.BARK_PAPER,
            Form.PAPYRUS, Form.BAMBOO_SLIPS, Form.WAX_TABLETS, Form.CLAY_TABLETS};
    /** A merchant or a trader sells at most this many forms. */
    public static final int MERCHANT_FORMS = 3;

    /** A box on the map for a span of years (inclusive). */
    record Box(String name, double minLat, double maxLat, double minLon, double maxLon, int from, int to) {
        boolean holds(double lat, double lon, int year) {
            return year >= from && year <= to && lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }
    }

    /** Linear A from about 1800 BCE and Linear B to about 1200 BCE, on clay: Crete and the Greek mainland. */
    static final Box AEGEAN = new Box("Crete and the Greek mainland", 34.5, 41.0, 19.5, 28.5, -1800, -1200);
    /** The Norse settlements of Greenland, 985 to about 1450: rune sticks. */
    static final Box NORSE_GREENLAND = new Box("the Norse farms of Greenland", 59.0, 65.5, -55.0, -42.0, 985, 1450);
    /** Scandinavia, 800 to 1400: rune sticks beside whatever else is written there. */
    static final Box SCANDINAVIA = new Box("Scandinavia", 54.5, 71.5, 4.5, 24.5, 800, 1400);
    /** South India and Sri Lanka kept to palm leaves until about 1850, paper beside them. */
    static final Box SOUTH_INDIA = new Box("south India and Sri Lanka", 5.5, 21.0, 72.0, 88.0, -300, 1849);
    /** Kashmir and Gandhara wrote on birch bark before palm leaves until about 1600. */
    static final Box NORTH_WEST = new Box("Kashmir and Gandhara", 30.0, 37.5, 66.0, 80.0, 0, 1599);
    /** No birch bark south of this latitude: the trees grow in the north. */
    static final double BIRCH_SOUTH = 23.0d;
    /**
     * The forest kingdoms of West Africa (Ife, Benin, Oyo, Asante, Dahomey), south of the savanna, before 1800: no
     * writing; paper came across the Sahara to the Sahel towns (Timbuktu, Kano), north of this box.
     */
    static final Box WEST_FOREST = new Box("the West African forest", -2.0, 9.5, -18.0, 16.0, Integer.MIN_VALUE, 1799);
    /** The kingdom of Kongo wrote Portuguese from about 1500 (King Afonso I's letters, 1506 to 1543). */
    static final Box KONGO = new Box("the kingdom of Kongo", -8.5, -4.0, 12.0, 17.0, 1500, 1699);
    /** Australia: message sticks. */
    static final Box AUSTRALIA = new Box("Australia", -44.0, -10.0, 112.0, 154.0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    /** Knotted cords (khipu) in the central Andes: from about 2600 BCE (Caral) to about 1900. */
    static final int KHIPU_FROM = -2600;
    static final int KHIPU_TO = 1900;
    /** The central Andes and their coast, Ecuador to north-west Argentina (latitude, longitude). */
    static final double[][] ANDES = {
            {2.0, -79.0}, {2.0, -76.5}, {-5.0, -77.5}, {-10.0, -75.0}, {-14.0, -71.0}, {-17.0, -66.5}, {-22.0, -64.5},
            {-28.0, -66.0}, {-35.0, -70.0}, {-35.0, -72.5}, {-28.0, -71.5}, {-18.0, -70.5}, {-14.0, -76.5},
            {-6.0, -81.5}, {-2.0, -81.0}};

    /**
     * The coast of Africa south of 2 degrees north, as (latitude, longitude): the Indian Ocean side (Mogadishu, Lamu,
     * Mombasa, Kilwa, Mozambique, Sofala, Maputo, Durban) and the Atlantic side (Gabon, the Congo mouth, Luanda,
     * Benguela, Walvis Bay, the Cape).
     */
    static final double[][] EAST_COAST = {{2.0, 45.3}, {0.0, 42.6}, {-2.0, 41.0}, {-4.0, 39.7}, {-6.0, 39.3},
            {-8.0, 39.4}, {-10.0, 39.9}, {-12.0, 40.5}, {-15.0, 40.7}, {-17.0, 38.5}, {-18.0, 36.9}, {-20.0, 34.8},
            {-22.0, 35.4}, {-24.0, 35.4}, {-26.0, 32.8}, {-28.0, 32.5}, {-30.0, 30.8}, {-34.0, 26.0}};
    static final double[][] WEST_COAST = {{2.0, 9.6}, {0.0, 9.4}, {-2.0, 9.4}, {-4.0, 11.2}, {-6.0, 12.2},
            {-8.0, 13.0}, {-10.0, 13.3}, {-12.0, 13.8}, {-14.0, 12.4}, {-16.0, 11.8}, {-18.0, 11.8}, {-20.0, 13.0},
            {-22.0, 14.0}, {-24.0, 14.5}, {-26.0, 14.9}, {-28.0, 15.9}, {-30.0, 17.2}, {-32.0, 18.3}, {-34.0, 18.4}};
    /** Inland Africa (more than 2 degrees from either coast, in the eastern and southern areas) writes from 1880. */
    static final int AFRICA_INLAND_WRITES = 1880;
    static final double INLAND_DEGREES = 2.0d;

    private BookForms() {
    }

    /** The coast's longitude at a latitude (a table from north to south, straight lines between its points). */
    static double coastAt(double[][] coast, double lat) {
        if (lat >= coast[0][0]) {
            return coast[0][1];
        }
        for (int i = 1; i < coast.length; i++) {
            if (lat >= coast[i][0]) {
                double t = (coast[i - 1][0] - lat) / (coast[i - 1][0] - coast[i][0]);
                return coast[i - 1][1] + t * (coast[i][1] - coast[i - 1][1]);
            }
        }
        return coast[coast.length - 1][1];
    }

    /**
     * True for inland eastern and southern Africa before 1880: writing (Arabic on paper, Ge'ez on parchment) stayed on
     * the Swahili coast and in Ethiopia; Great Zimbabwe, Mapungubwe, Buganda and the Luba remembered without it.
     */
    static boolean africanInterior(double lat, double lon, int year, String area) {
        if (year >= AFRICA_INLAND_WRITES || lat > 2.0d || lat < -34.0d || !("EAF".equals(area) || "MSA".equals(area))) {
            return false;
        }
        return lon < coastAt(EAST_COAST, lat) - INLAND_DEGREES && lon > coastAt(WEST_COAST, lat) + INLAND_DEGREES;
    }

    /** The first year of paper in Europe at a latitude: Spain and Italy 1150, France 1300, the north 1350 (estimates). */
    static int europePaper(double lat) {
        return lat < 44.0d ? 1150 : lat < 50.0d ? 1300 : 1350;
    }

    /** True inside the central Andes outline. */
    static boolean inAndes(double lat, double lon) {
        boolean in = false;
        for (int i = 0, j = ANDES.length - 1; i < ANDES.length; j = i++) {
            double yi = ANDES[i][0];
            double xi = ANDES[i][1];
            double yj = ANDES[j][0];
            double xj = ANDES[j][1];
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /** The writing forms people here have, best first (empty: nobody writes here). */
    public static List<Form> writing(double lat, double lon, int year) {
        List<Form> out = new ArrayList<>();
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return out;
        }
        if (NORSE_GREENLAND.holds(lat, lon, year)) {
            out.add(Form.RUNE_STICKS);
            return out;
        }
        ProductAtlas atlas = ProductAtlas.get();
        ProductAtlas.Place at = atlas.place(lat, lon, year);
        if (africanInterior(lat, lon, year, at.area())) {
            return out; // the coast writes; the inland kingdoms remember (Great Zimbabwe, Buganda, the Luba)
        }
        if ("WAF".equals(at.area()) && WEST_FOREST.holds(lat, lon, year)) {
            return out; // the forest kingdoms remembered (court historians, drums); the Sahel towns wrote
        }
        if (KONGO.holds(lat, lon, year)) {
            out.add(Form.NOTEBOOK); // Portuguese paper at the court of Kongo
        }
        for (Form f : ORDER) {
            ProductAtlas.Product p = atlas.product(f.product);
            if (p != null && !out.contains(f) && atlas.status(p, at).status().present()) {
                out.add(f);
            }
        }
        if ("EUR".equals(at.area()) && year < europePaper(lat)) {
            out.remove(Form.NOTEBOOK); // paper came north slowly: Spain 1150, France 1300, the north 1350
        }
        if (AEGEAN.holds(lat, lon, year) && !out.contains(Form.CLAY_TABLETS)) {
            out.add(Form.CLAY_TABLETS);
        }
        if (SCANDINAVIA.holds(lat, lon, year)) {
            out.add(Form.RUNE_STICKS);
        }
        if (lat < BIRCH_SOUTH) {
            out.remove(Form.BIRCH_BARK); // birches grow in the north (the Himalayas, Russia, Canada)
        }
        if (SOUTH_INDIA.holds(lat, lon, year) && out.remove(Form.PALM_LEAF)) {
            out.add(0, Form.PALM_LEAF);
        }
        if (NORTH_WEST.holds(lat, lon, year) && out.contains(Form.PALM_LEAF) && out.remove(Form.BIRCH_BARK)) {
            out.add(out.indexOf(Form.PALM_LEAF), Form.BIRCH_BARK);
        }
        return out;
    }

    /** What people here remember with when nobody writes: knotted cords, a message stick, or a tally stick. */
    public static Form memoryAid(double lat, double lon, int year) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return Form.TALLY_STICK;
        }
        if (year >= KHIPU_FROM && year <= KHIPU_TO && inAndes(lat, lon)) {
            return Form.KNOTTED_CORDS;
        }
        if (AUSTRALIA.holds(lat, lon, year)) {
            return Form.MESSAGE_STICK;
        }
        try {
            if ("PAC".equals(ProductAtlas.get().place(lat, lon, year).area())) {
                return Form.KNOTTED_CORDS;
            }
        } catch (Throwable t) {
            // the atlas is missing: a tally stick
        }
        return Form.TALLY_STICK;
    }

    /** The forms here, best first: the writing forms, or the memory aid when nobody writes. Never empty. */
    public static List<Form> here(double lat, double lon, int year) {
        List<Form> w = writing(lat, lon, year);
        return w.isEmpty() ? List.of(memoryAid(lat, lon, year)) : w;
    }

    /** The best form here (what people here keep notes in). */
    public static Form best(double lat, double lon, int year) {
        return here(lat, lon, year).get(0);
    }

    /** What a form costs, in days of a labourer's pay (estimates; paper became cheap with machines after 1800). */
    public static double days(Form f, int year) {
        return switch (f) {
            case NOTEBOOK -> year < 1800 ? 1.0d : year < 1900 ? 0.3d : 0.1d;
            case PARCHMENT -> 3.0d;
            case PAPYRUS -> 1.0d;
            case BARK_PAPER, BAMBOO_SLIPS, WAX_TABLETS -> 0.5d;
            case PALM_LEAF -> 0.3d;
            case BIRCH_BARK, CLAY_TABLETS, KNOTTED_CORDS -> 0.1d;
            case RUNE_STICKS, MESSAGE_STICK -> 0.05d;
            case TALLY_STICK -> 0.02d;
        };
    }

    /** The cheapest of these forms in this year (the first of equals). */
    static Form cheapest(List<Form> forms, int year) {
        Form best = forms.get(0);
        for (Form f : forms) {
            if (days(f, year) < days(best, year)) {
                best = f;
            }
        }
        return best;
    }

    /** True for the roles that sell the history book: merchant, trader, pedlar, paper seller, newsletter writer. */
    public static boolean sells(String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        return MarketService.buyer(r) || r.equals(NewsEras.PAPER_SELLER) || r.equals(NewsEras.NEWS_WRITER);
    }

    /**
     * The forms a seller of this role has here: a merchant or a trader up to {@link #MERCHANT_FORMS} (the best ones,
     * always with the cheapest), a pedlar the cheapest, a paper seller the notebook (or the best), a newsletter writer
     * the best. Where nobody writes, the memory aid. Empty for anyone who sells nothing.
     */
    public static List<Form> soldBy(String role, double lat, double lon, int year) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (!sells(r)) {
            return List.of();
        }
        List<Form> w = writing(lat, lon, year);
        if (w.isEmpty()) {
            return List.of(memoryAid(lat, lon, year));
        }
        if (r.equals(MarketService.PEDLAR)) {
            return List.of(cheapest(w, year));
        }
        if (r.equals(NewsEras.PAPER_SELLER)) {
            return List.of(w.contains(Form.NOTEBOOK) ? Form.NOTEBOOK : w.get(0));
        }
        if (r.equals(NewsEras.NEWS_WRITER)) {
            return List.of(w.get(0));
        }
        List<Form> out = new ArrayList<>(w.subList(0, Math.min(MERCHANT_FORMS, w.size())));
        Form cheap = cheapest(w, year);
        if (!out.contains(cheap)) {
            out.set(out.size() - 1, cheap);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ checks

    /** A place, a year and the form expected there (the world-wide test set of SYSTEMS_PLAN part 9). */
    public record Expect(String place, double lat, double lon, int year, Form form) {
    }

    public static final List<Expect> EXPECT = List.of(
            new Expect("Ur", 30.9625, 46.1031, -2500, Form.CLAY_TABLETS),
            new Expect("Giza", 29.9792, 31.1342, -2500, Form.PAPYRUS),
            new Expect("Mohenjo-daro", 27.3292, 68.1389, -2500, Form.TALLY_STICK),
            new Expect("Caral", -10.8933, -77.5203, -2500, Form.KNOTTED_CORDS),
            new Expect("Knossos", 35.2980, 25.1630, -1400, Form.CLAY_TABLETS),
            new Expect("Anyang", 36.1269, 114.3152, -1200, Form.BAMBOO_SLIPS),
            new Expect("Athens", 37.9715, 23.7257, -430, Form.PAPYRUS),
            new Expect("Pataliputra", 25.6000, 85.1333, -250, Form.PALM_LEAF),
            new Expect("Rome", 41.8925, 12.4853, 100, Form.PARCHMENT),
            new Expect("Teotihuacan", 19.6925, -98.8438, 500, Form.BARK_PAPER),
            new Expect("Chang'an", 34.2667, 108.9000, 750, Form.NOTEBOOK),
            new Expect("Baghdad", 33.3152, 44.3661, 900, Form.NOTEBOOK),
            new Expect("the Norse farms of Greenland", 61.1500, -45.5167, 1000, Form.RUNE_STICKS),
            new Expect("Cahokia", 38.6550, -90.0620, 1100, Form.TALLY_STICK),
            new Expect("Angkor", 13.4125, 103.8670, 1150, Form.PALM_LEAF),
            new Expect("Great Zimbabwe", -20.2674, 30.9338, 1300, Form.TALLY_STICK),
            new Expect("Benin City", 6.3350, 5.6037, 1500, Form.TALLY_STICK),
            new Expect("Kano", 12.0022, 8.5920, 1500, Form.NOTEBOOK),
            new Expect("Mbanza Kongo", -6.2670, 14.2400, 1550, Form.NOTEBOOK),
            new Expect("Timbuktu", 16.7666, -3.0026, 1350, Form.NOTEBOOK),
            new Expect("Tenochtitlan", 19.4326, -99.1332, 1500, Form.BARK_PAPER),
            new Expect("Cusco", -13.5320, -71.9675, 1500, Form.KNOTTED_CORDS),
            new Expect("the Amazon", -3.4653, -62.2159, 1500, Form.TALLY_STICK),
            new Expect("Vijayanagara", 15.3350, 76.4600, 1520, Form.PALM_LEAF),
            new Expect("Cusco", -13.5320, -71.9675, 1600, Form.NOTEBOOK),
            new Expect("New York", 40.7128, -74.0060, 1600, Form.TALLY_STICK),
            new Expect("Delhi", 28.6562, 77.2410, 1650, Form.NOTEBOOK),
            new Expect("Sydney", -33.8688, 151.2093, 1700, Form.MESSAGE_STICK),
            new Expect("Edo", 35.6852, 139.7528, 1700, Form.NOTEBOOK),
            new Expect("London", 51.5074, -0.1278, 1800, Form.NOTEBOOK),
            new Expect("Hyderabad", 17.3616, 78.4747, 1948, Form.NOTEBOOK),
            new Expect("Sydney", -33.8688, 151.2093, 1900, Form.NOTEBOOK));

    /** The years the random points are tried in. */
    public static final int[] CHECK_YEARS = {-3000, -1500, 0, 800, 1300, 1700, 1948};

    /** Random points on the globe (even by area) for the checks: the same every time. */
    public static List<double[]> randomPoints(int n, long seed) {
        java.util.Random random = new java.util.Random(seed);
        List<double[]> out = new ArrayList<>();
        while (out.size() < n) {
            double lat = Math.toDegrees(Math.asin(2.0d * random.nextDouble() - 1.0d));
            double lon = random.nextDouble() * 360.0d - 180.0d;
            out.add(new double[]{lat, lon});
        }
        return out;
    }

    /** One line per expected place that is wrong, or none. */
    public static List<String> wrongPlaces() {
        List<String> bad = new ArrayList<>();
        for (Expect e : EXPECT) {
            Form f = best(e.lat(), e.lon(), e.year());
            if (f != e.form()) {
                bad.add(e.place() + " " + ProductAtlas.yearText(e.year()) + ": " + f.title + ", not " + e.form().title);
            }
        }
        return bad;
    }

    /** Counts of the best form over the random points in every check year: [form ordinal] = count. */
    public static int[] census(List<double[]> points) {
        int[] n = new int[Form.values().length];
        for (int y : CHECK_YEARS) {
            for (double[] p : points) {
                n[best(p[0], p[1], y).ordinal()]++;
            }
        }
        return n;
    }

    /**
     * The rules on their own: every expected place gets its form; a merchant sells up to three forms with the cheapest
     * among them, a pedlar one, a paper seller the notebook; nobody else sells it; where nobody writes the sellers
     * have the memory aid; prices fall with machine paper. Null when all is well.
     */
    public static String selfTest() {
        List<String> bad = wrongPlaces();
        if (!bad.isEmpty()) {
            return bad.size() + " place(s) wrong: " + String.join("; ", bad);
        }
        List<Form> rome = soldBy(MarketService.MERCHANT, 41.8925, 12.4853, 100);
        if (rome.size() != MERCHANT_FORMS || rome.get(0) != Form.PARCHMENT || !rome.contains(Form.WAX_TABLETS)) {
            return "a merchant in Rome in 100 should sell a parchment book, ... and wax tablets: " + rome;
        }
        List<Form> pedlar = soldBy(MarketService.PEDLAR, 41.8925, 12.4853, 100);
        if (!pedlar.equals(List.of(Form.WAX_TABLETS))) {
            return "a pedlar in Rome in 100 should sell wax tablets only: " + pedlar;
        }
        if (!soldBy(NewsEras.PAPER_SELLER, 17.3616, 78.4747, 1948).equals(List.of(Form.NOTEBOOK))) {
            return "a paper seller in Hyderabad in 1948 should sell a notebook";
        }
        if (!soldBy("farmer", 17.3616, 78.4747, 1948).isEmpty() || !soldBy("money_changer", 17.3616, 78.4747, 1948).isEmpty()) {
            return "a farmer or a money-changer should sell no book";
        }
        if (!soldBy(MarketService.TRADER, -13.5320, -71.9675, 1500).equals(List.of(Form.KNOTTED_CORDS))) {
            return "a trader in Cusco in 1500 should have knotted cords";
        }
        if (!(days(Form.NOTEBOOK, 1500) > days(Form.NOTEBOOK, 1850) && days(Form.NOTEBOOK, 1850) > days(Form.NOTEBOOK, 1948))) {
            return "a notebook should get cheaper with machine paper";
        }
        if (memoryAid(-25.0, 134.0, 1000) != Form.MESSAGE_STICK || memoryAid(-17.5, -149.5, 1000) != Form.KNOTTED_CORDS
                || memoryAid(51.5, -0.13, -2000) != Form.TALLY_STICK) {
            return "memory aids: Australia " + memoryAid(-25.0, 134.0, 1000) + ", Tahiti " + memoryAid(-17.5, -149.5, 1000)
                    + ", London 2000 BCE " + memoryAid(51.5, -0.13, -2000);
        }
        for (Form f : Form.values()) {
            if (Form.byId(f.id) != f || Form.byId(f.itemId()) != f) {
                return "the name " + f.id + " does not find its form";
            }
        }
        return null;
    }
}

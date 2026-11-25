package com.eternalengine.knowledge;

import com.eternalengine.data.TileCache;
import com.eternalengine.data.TileStore;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Step 27 (systems C10, C12, C22): the knowledge cards of famous people and organisations, for every place and era.
 *
 * <p>The file {@code cards/cards.tiles} is built by {@code worldfill/build_cards.py} from Wikidata, cross-checked with
 * the Wikipedia year pages. It uses the step-26 tile format: the cards are cut by 5-degree boxes of the map (by home or
 * seat) and by the century in which people start to talk about them, each tile compressed on its own. Only the
 * compressed file stays in memory; a tile opens when a talk needs that land and era, inside the shared tile budget
 * ({@link TileCache#SHARED}). An index (QID to tile) finds any card by its Wikidata number, for links between cards
 * and for the history book.
 *
 * <p>A card holds facts only (names, years, places, offices, works, links); {@link PlaceCards} words them for a date.
 */
public final class CardStore {

    public static final String RESOURCE = "/data/eternalengine/eternal/cards/cards.tiles";
    /** Step 28: the event cards and the cause-and-effect graph (dataset "events"). */
    public static final String EVENTS_RESOURCE = "/data/eternalengine/eternal/cards/events.tiles";
    public static final int UNKNOWN = Integer.MIN_VALUE;
    public static final int BOX = 5;

    public static final int PERSON = 0;
    public static final int COMPANY = 1;
    public static final int SCHOOL = 2;
    public static final int PAPER = 3;
    public static final int PARTY = 4;
    public static final int ARMY = 5;
    public static final int SOCIETY = 6;
    public static final int ORDER = 7;
    /** Step 28: an event (a battle, a war, a treaty, a storm ...), in the events file. */
    public static final int EVENT = 8;

    public static final int STUDIED = 1;
    public static final int PARTY_OF = 2;
    public static final int WORKED = 3;
    public static final int SERVED = 4;
    public static final int MEMBER = 5;
    public static final int FOUNDED = 6;
    public static final int FOUNDED_BY = 7;
    // Step 28, the links of an event card (the cause-and-effect graph, C9): each is stored on both cards.
    public static final int PART_OF = 8;
    public static final int HAS_PART = 9;
    public static final int CAUSE = 10;
    public static final int EFFECT = 11;
    public static final int TOOK_PART = 12;
    public static final int WON = 13;
    /** The event that came before this one ("follows"). */
    public static final int AFTER = 14;
    /** The event that came after this one ("followed by"). */
    public static final int BEFORE = 15;

    /** An office held: its name and years ({@link #UNKNOWN} when not given). */
    public record Office(String name, int from, int to) {
    }

    /** A work and its year. */
    public record Work(String title, int year) {
    }

    /** A link to another card (a role above, the other card's QID, and the years when given). */
    public record Link(int role, int qid, int from, int to) {
    }

    /**
     * One card. People: {@code from}/{@code to} are the birth and death years; organisations: founded and closed.
     * {@code known} is the year people start to talk about it. {@code line} is the short description; it may be said
     * from {@code lineFrom} on ({@link #UNKNOWN} = always), and {@code early} before then ("" = nothing).
     */
    public record Card(int qid, int kind, int fame, String name, String line, int lineFrom, String early, int from, int to,
                       int known, int flags, double lat, double lon, String home, double deathLat, double deathLon,
                       String deathPlace, List<Office> offices, List<Work> works, List<Link> links, EventFacts event) {

        public boolean person() {
            return kind == PERSON;
        }

        /** Step 28: an event card (its facts are in {@link #event()}). */
        public boolean isEvent() {
            return kind == EVENT;
        }

        public boolean hasLocation() {
            return !Double.isNaN(lat) && !Double.isNaN(lon);
        }

        public boolean female() {
            return (flags & 4) != 0;
        }

        public boolean male() {
            return (flags & 8) != 0;
        }

        /** The first year is given only to the decade: say "around". */
        public boolean fromAround() {
            return (flags & 1) != 0;
        }

        public boolean toAround() {
            return (flags & 2) != 0;
        }

        public boolean hasDeathPlace() {
            return !Double.isNaN(deathLat) && !Double.isNaN(deathLon);
        }

        public boolean knownIn(int year) {
            return known <= year;
        }

        /**
         * People: alive in {@code year}. Dead from the year of death on (as the year pages tell a death in its year);
         * with no death year, alive up to the age of 90.
         */
        public boolean aliveIn(int year) {
            if (from != UNKNOWN && year < from) {
                return false;
            }
            if (to != UNKNOWN) {
                return year < to;
            }
            return from != UNKNOWN && year - from <= 90;
        }

        /** Organisations: founded by {@code year} and not closed before it. */
        public boolean existsIn(int year) {
            return (from == UNKNOWN || from <= year) && (to == UNKNOWN || year <= to);
        }

        /** Organisations: closed by {@code year}. */
        public boolean closedIn(int year) {
            return to != UNKNOWN && year >= to;
        }
    }

    /**
     * Step 28: what an event card adds: the deaths ({@link #UNKNOWN} when not known), the country, the other sides by
     * name, causes without a card of their own by name, and the larger event when it has no card.
     */
    public record EventFacts(int deaths, String country, String sides, String causes, String partOf) {
        public static final EventFacts NONE = new EventFacts(UNKNOWN, "", "", "", "");
    }

    /** A person known far and wide (group "f"): enough to choose, then {@link #byQid} for the card. */
    public record Far(int qid, double lat, double lon, int fame, int known, int from, int to, String name) {
        public boolean aliveIn(int year) {
            if (from != UNKNOWN && year < from) {
                return false;
            }
            if (to != UNKNOWN) {
                return year < to;
            }
            return from != UNKNOWN && year - from <= 90;
        }
    }

    private static final Object LOCK = new Object();
    private static volatile CardStore instance;
    private static volatile CardStore events;

    private final TileStore store;
    private final String problem;
    private final List<TileStore.Tile> numbered = new ArrayList<>();
    private volatile String[] offices;
    private volatile int[] qids;
    private volatile int[] tileOf;
    private volatile int[] nameHashes;
    private volatile int[] nameQids;
    private final long loadMillis;

    private CardStore(TileStore store, String problem, long loadMillis) {
        this.store = store;
        this.problem = problem;
        this.loadMillis = loadMillis;
        if (store != null) {
            for (String g : new TreeSet<>(store.groupNames())) {   // the order build_cards.py numbers the tiles in
                numbered.addAll(Arrays.asList(store.tiles(g)));
            }
        }
    }

    /** The card file of people and groups, read once (an empty store with {@link #problem()} set if it cannot be read). */
    public static CardStore get() {
        CardStore s = instance;
        if (s != null) {
            return s;
        }
        synchronized (LOCK) {
            if (instance == null) {
                instance = load(RESOURCE, "cards");
                // Step 41 (C11): the kinship register names its relatives itself, and asks the cards
                // about the 148,510 people the pack deliberately does not repeat. One line, so that
                // Elizabeth II's father is "George VI" and not "someone the record does not name".
                com.eternalengine.civilization.Kinship.setNameSource(
                        q -> {
                            Card c = instance.byQid(q);
                            return c == null ? null : c.name();
                        });
            }
            return instance;
        }
    }

    /** Step 28: the event cards and the cause-and-effect graph, read once. */
    public static CardStore events() {
        CardStore s = events;
        if (s != null) {
            return s;
        }
        synchronized (LOCK) {
            if (events == null) {
                events = load(EVENTS_RESOURCE, "events");
            }
            return events;
        }
    }

    private static CardStore load(String resource, String dataset) {
        long t0 = System.nanoTime();
        try (InputStream in = CardStore.class.getResourceAsStream(resource)) {
            if (in == null) {
                return new CardStore(null, resource + " is missing", 0L);
            }
            TileStore t = TileStore.read(in);
            if (!dataset.equals(t.dataset())) {
                return new CardStore(null, "not a " + dataset + " file: " + t.dataset(), 0L);
            }
            return new CardStore(t, null, (System.nanoTime() - t0) / 1_000_000L);
        } catch (Exception e) {
            return new CardStore(null, "cannot read " + resource + ": " + e, 0L);
        }
    }

    public boolean loaded() {
        return store != null;
    }

    /** Why the file could not be read, or null. */
    public String problem() {
        return problem;
    }

    public TileStore tileStore() {
        return store;
    }

    public long loadMillis() {
        return loadMillis;
    }

    /** Cards in the file (people and organisations). */
    public int size() {
        return store == null ? 0 : store.rows();
    }

    public int people() {
        return store == null ? 0 : store.metaInt("people", 0);
    }

    public int organisations() {
        return store == null ? 0 : store.metaInt("organisations", 0);
    }

    /** "148,541 people and 30,072 organisations in 1,014 tiles (8.2 MB)", or for the events file its events. */
    public String summary() {
        if (store == null) {
            return "no cards: " + problem;
        }
        if ("events".equals(store.dataset())) {
            return String.format(java.util.Locale.ROOT, "%,d events (%,d with no single point) in %,d tiles (%.1f MB)",
                    store.metaInt("events", 0), store.metaInt("placeless", 0), store.tileCount(),
                    store.compressedBytes() / 1048576.0d);
        }
        return String.format(java.util.Locale.ROOT, "%,d people and %,d organisations in %,d tiles (%.1f MB)", people(),
                organisations(), store.tileCount(), store.compressedBytes() / 1048576.0d);
    }

    // ------------------------------------------------------------------------------------------------ finding cards

    /** The card with this Wikidata number, or null. */
    public Card byQid(int qid) {
        if (store == null || qid <= 0) {
            return null;
        }
        index();
        int i = Arrays.binarySearch(qids, qid);
        if (i < 0) {
            return null;
        }
        int t = tileOf[i];
        if (t < 0 || t >= numbered.size()) {
            return null;
        }
        for (Card c : open(t)) {
            if (c.qid() == qid) {
                return c;
            }
        }
        return null;
    }

    /**
     * Every card whose home or seat lies within {@code km} of the point and that people start to talk about in a century
     * from {@code fromBucket} to {@code toBucket} (bucket = floor(year / 100)).
     */
    public List<Card> near(double lat, double lon, double km, int fromBucket, int toBucket) {
        List<Card> out = new ArrayList<>();
        if (store == null || Double.isNaN(lat) || Double.isNaN(lon)) {
            return out;
        }
        double dLat = km / 111.0d;
        double cos = Math.cos(Math.toRadians(Math.min(89.0d, Math.abs(lat) + dLat)));
        double dLon = cos < 0.02d ? 180.0d : Math.min(180.0d, km / (111.0d * cos));
        int y0 = (int) Math.floor((Math.max(-90.0d, lat - dLat) + 90.0d) / BOX);
        int y1 = (int) Math.floor((Math.min(89.999d, lat + dLat) + 90.0d) / BOX);
        int boxes = 360 / BOX;
        int x0 = (int) Math.floor((lon - dLon + 180.0d) / BOX);
        int x1 = (int) Math.floor((lon + dLon + 180.0d) / BOX);
        if (x1 - x0 >= boxes) {
            x0 = 0;
            x1 = boxes - 1;
        }
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int y = y0; y <= y1; y++) {
            for (int xx = x0; xx <= x1; xx++) {
                int x = Math.floorMod(xx, boxes);
                if (!seen.add(y * 1000 + x)) {
                    continue;
                }
                for (TileStore.Tile t : store.tiles("b/" + y + "/" + x, fromBucket, toBucket)) {
                    for (Card c : open(t)) {
                        if (KnowledgePacks.Geo.km(lat, lon, c.lat(), c.lon()) <= km) {
                            out.add(c);
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * The people known far and wide (50 or more articles) within {@code km}, who became known in a century from
     * {@code fromBucket} to {@code toBucket}.
     */
    public List<Far> famousNear(double lat, double lon, double km, int fromBucket, int toBucket) {
        List<Far> out = new ArrayList<>();
        if (store == null || Double.isNaN(lat) || Double.isNaN(lon)) {
            return out;
        }
        for (TileStore.Tile t : store.tiles("f", fromBucket, toBucket)) {
            List<Far> rows = TileCache.SHARED.get(store.dataset() + "|f|" + t.first(), TileCache.estimate(t), () -> {
                ByteBuffer b = ByteBuffer.wrap(store.inflate(t));
                List<Far> fs = new ArrayList<>(t.rows());
                while (b.hasRemaining()) {
                    fs.add(new Far(b.getInt(), b.getInt() / 1000.0d, b.getInt() / 1000.0d, b.getShort(), year(b.getShort()),
                            b.getInt(), b.getInt(), str(b)));
                }
                return fs;
            });
            for (Far f : rows) {
                if (KnowledgePacks.Geo.km(lat, lon, f.lat(), f.lon()) <= km) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    /**
     * People with this name (letters and digits, no accents, any case) who held the office of a head of a state or a
     * government. "Louis XVI" also finds "Louis XVI of France", "Cosimo II" finds "Cosimo II de' Medici".
     */
    public List<Card> byName(String name) {
        List<Card> out = new ArrayList<>();
        if (store == null || name == null || name.isBlank() || !store.has("n")) {
            return out;
        }
        names();
        int h = nameHash(name);
        int i = Arrays.binarySearch(nameHashes, h);
        if (i < 0) {
            return out;
        }
        while (i > 0 && nameHashes[i - 1] == h) {
            i--;
        }
        String plain = plainName(name);
        for (; i < nameHashes.length && nameHashes[i] == h; i++) {
            Card c = byQid(nameQids[i]);
            if (c != null && variants(c.name()).contains(plain) && !out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /** The ways a register may name this person (build_cards.name_variants). */
    public static List<String> variants(String name) {
        List<String> out = new ArrayList<>();
        String plain = plainName(name);
        out.add(plain);
        int of = name.indexOf(" of ");
        if (of > 0) {
            String before = plainName(name.substring(0, of).trim());
            if (!before.isEmpty() && !out.contains(before)) {
                out.add(before);
            }
        }
        String[] words = plain.split(" ");
        if (words.length >= 3) {
            String two = words[0] + " " + words[1];
            if (!out.contains(two)) {
                out.add(two);
            }
        }
        return out;
    }

    /** "Æthelred the Unready" -> "thelred the unready", "Ötzi" -> "otzi" (build_cards.norm_name does the same). */
    public static String plainName(String s) {
        String d = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFKD);
        StringBuilder sb = new StringBuilder(d.length());
        boolean space = false;
        for (int i = 0; i < d.length(); i++) {
            char ch = d.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK || Character.getType(ch) == Character.ENCLOSING_MARK
                    || Character.getType(ch) == Character.COMBINING_SPACING_MARK) {
                continue;
            }
            char lo = Character.toLowerCase(ch);
            if ((lo >= 'a' && lo <= 'z') || (lo >= '0' && lo <= '9')) {
                if (space && sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(lo);
                space = false;
            } else {
                space = true;
            }
        }
        return sb.toString();
    }

    /** FNV-1a, 32 bits, of the plain name's UTF-8 bytes (build_cards.name_hash). */
    public static int nameHash(String name) {
        int h = 0x811C9DC5;
        for (byte b : plainName(name).getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xFF);
            h *= 0x01000193;
        }
        return h;
    }

    private void names() {
        if (nameHashes != null) {
            return;
        }
        synchronized (this) {
            if (nameHashes != null) {
                return;
            }
            ByteBuffer b = ByteBuffer.wrap(store.inflate(store.tiles("n")[0]));
            int n = b.getInt();
            int[] h = new int[n];
            int[] q = new int[n];
            for (int i = 0; i < n; i++) {
                h[i] = b.getInt();
                q[i] = b.getInt();
            }
            nameQids = q;
            nameHashes = h;
        }
    }

    /** Every card of the file, tile by tile (for checks and probes; slow). */
    public void forEach(java.util.function.Consumer<Card> each) {
        if (store == null) {
            return;
        }
        for (TileStore.Tile t : numbered) {
            if (t.group().startsWith("b/") || t.group().equals("w")) {
                for (Card c : decode(store.inflate(t), t.rows(), offices())) {
                    each.accept(c);
                }
            }
        }
    }

    /** QIDs in the index (for checks). */
    public int indexSize() {
        if (store == null) {
            return 0;
        }
        index();
        return qids.length;
    }

    /** The {@code i}-th QID of the index (ascending), for checks that sample the index. */
    public int indexQid(int i) {
        index();
        return qids[i];
    }

    // ------------------------------------------------------------------------------------------------ tiles

    private List<Card> open(int tileNo) {
        return open(numbered.get(tileNo));
    }

    private List<Card> open(TileStore.Tile t) {
        // the dataset is part of the key: cards.tiles and events.tiles have groups of the same names (step 28)
        return TileCache.SHARED.get(store.dataset() + "|" + t.group() + "|" + t.first(), TileCache.estimate(t),
                () -> decode(store.inflate(t), t.rows(), offices()));
    }

    private String[] offices() {
        String[] v = offices;
        if (v == null) {
            synchronized (this) {
                if (offices == null) {
                    TileStore.Tile[] ts = store.tiles("v");
                    if (ts.length == 0) {
                        offices = new String[0];
                        return offices;
                    }
                    ByteBuffer b = ByteBuffer.wrap(store.inflate(ts[0]));
                    String[] out = new String[b.getInt()];
                    for (int i = 0; i < out.length; i++) {
                        out[i] = str(b);
                    }
                    offices = out;
                }
                v = offices;
            }
        }
        return v;
    }

    private void index() {
        if (qids != null) {
            return;
        }
        synchronized (this) {
            if (qids != null) {
                return;
            }
            TileStore.Tile[] ts = store.tiles("i");
            ByteBuffer b = ByteBuffer.wrap(store.inflate(ts[0]));
            int n = b.getInt();
            int[] q = new int[n];
            int[] t = new int[n];
            int prev = 0;
            for (int i = 0; i < n; i++) {
                prev += varint(b);
                q[i] = prev;
            }
            for (int i = 0; i < n; i++) {
                t[i] = varint(b);
            }
            tileOf = t;
            qids = q;
        }
    }

    private static int varint(ByteBuffer b) {
        int v = 0;
        int shift = 0;
        while (true) {
            int x = b.get() & 0xFF;
            v |= (x & 0x7F) << shift;
            if ((x & 0x80) == 0) {
                return v;
            }
            shift += 7;
        }
    }

    static List<Card> decode(byte[] raw, int rows, String[] officeNames) {
        ByteBuffer b = ByteBuffer.wrap(raw);
        List<Card> out = new ArrayList<>(rows);
        while (b.hasRemaining()) {
            int qid = b.getInt();
            int kind = b.get();
            int fame = b.getShort();
            String name = str(b);
            String line = str(b);
            int lineFrom = year(b.getShort());
            String early = str(b);
            int from = b.getInt();
            int to = b.getInt();
            int known = year(b.getShort());
            int flags = b.get();
            int ilat = b.getInt();
            int ilon = b.getInt();
            double lat = ilat == UNKNOWN ? Double.NaN : ilat / 1000.0d;   // step 28: an event with no single point
            double lon = ilon == UNKNOWN ? Double.NaN : ilon / 1000.0d;
            String home = str(b);
            double dLat = Double.NaN;
            double dLon = Double.NaN;
            String deathPlace = "";
            List<Office> offices = List.of();
            List<Work> works = List.of();
            if (kind == PERSON) {
                int a = b.getInt();
                int c = b.getInt();
                if (a != UNKNOWN && c != UNKNOWN) {
                    dLat = a / 1000.0d;
                    dLon = c / 1000.0d;
                }
                deathPlace = str(b);
                int n = b.get();
                if (n > 0) {
                    List<Office> os = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        int v = b.getInt();
                        os.add(new Office(v >= 0 && v < officeNames.length ? officeNames[v] : "", year(b.getShort()),
                                year(b.getShort())));
                    }
                    offices = List.copyOf(os);
                }
                n = b.get();
                if (n > 0) {
                    List<Work> ws = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        ws.add(new Work(str(b), year(b.getShort())));
                    }
                    works = List.copyOf(ws);
                }
            }
            EventFacts ev = EventFacts.NONE;
            if (kind == EVENT) {
                ev = new EventFacts(b.getInt(), str(b), str(b), str(b), str(b));
            }
            int n = b.get();
            List<Link> links = List.of();
            if (n > 0) {
                List<Link> ls = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    ls.add(new Link(b.get(), b.getInt(), year(b.getShort()), year(b.getShort())));
                }
                links = List.copyOf(ls);
            }
            out.add(new Card(qid, kind, fame, name, line, lineFrom, early, from, to, known, flags, lat, lon, home, dLat,
                    dLon, deathPlace, offices, works, links, ev));
        }
        return out;
    }

    private static int year(short s) {
        return s == Short.MIN_VALUE ? UNKNOWN : s;
    }

    private static String str(ByteBuffer b) {
        int n = b.getShort() & 0xFFFF;
        String s = new String(b.array(), b.arrayOffset() + b.position(), n, StandardCharsets.UTF_8);
        b.position(b.position() + n);
        return s;
    }
}

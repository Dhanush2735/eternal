package com.eternalengine.civilization;

import com.eternalengine.data.TileCache;
import com.eternalengine.data.TileStore;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Step 41 (D11, famous people where and when they were): <b>where a person was, and why</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>{@code FamousPeople} answers every question it is asked — {@code livingIn}, {@code diedIn},
 * {@code bornNear} — <b>by birthplace or by state</b>. A person is where they were born, for the
 * whole of their life. That is {@code SYSTEMS_PLAN.md} line 507: <i>someone born in Bombay who
 * lived in England (T. H. White) is talked about in Bombay, and the famous of a city who were born
 * elsewhere are not "from here".</i>
 *
 * <p>T. H. White's card says home Mumbai, and says he studied at Queens' College, Cambridge. The
 * second fact was in the mod all along and nothing in the mod could use it. This register is the
 * join that makes it usable.
 *
 * <h3>What a stay is</h3>
 *
 * <p>A person, a place, two years, and <b>a why</b> — because "from here" and "here now" are
 * different answers, and a register that cannot tell them apart will go on putting a man in the
 * city he was born in. Ten reasons: born, died, studied at, worked for, served in, member of,
 * party, founded, lived at, worked in.
 *
 * <p><b>An office is not a stay.</b> A position-held statement almost never carries a
 * jurisdiction, and the office item's country is a country, not somewhere a player can stand. So
 * this register cannot say where a court was; it can say who held the office and when, which is
 * what {@link Kinship}'s dynasties are for.
 *
 * <p><b>A stay that was never dated says so.</b> 32% of the rows carry the person's own birth and
 * death years because nobody dated them — a residence statement in Wikidata has a start year 18%
 * of the time. Such a row answers {@link Stay#estimated()} with true, and the printed line says
 * <i>at some point in his life</i> rather than a year nobody gave it.
 *
 * <h3>The two questions</h3>
 *
 * <ul>
 * <li>{@link #hereIn} — <i>who was here in this year?</i> This is the half that fixes line 507,
 *     and it is asked of the tiles filed by the box of the <b>place</b>.</li>
 * <li>{@link #of} — <i>where was this person?</i> The <i>homes, courts and journeys</i> half,
 *     which cannot be answered from the place boxes at all without reading the whole file, so the
 *     rows are filed a second time in groups of people.</li>
 * </ul>
 */
public final class Presence {

    public static final String TILES = "/data/eternalengine/eternal/history/presence.tiles";
    public static final int UNKNOWN = Integer.MIN_VALUE;

    /** How near a stay has to be to count as *here* when the page asks why a person is here. */
    public static final double STAY_NEAR_KM = 150.0d;

    /** The reasons a person was somewhere, in the pack's own order. */
    public static final int BORN = 1;
    public static final int DIED = 2;
    public static final int STUDIED = 3;
    public static final int WORKED_FOR = 4;
    public static final int SERVED_IN = 5;
    public static final int MEMBER_OF = 6;
    public static final int PARTY = 7;
    public static final int FOUNDED = 8;
    public static final int LIVED_AT = 9;
    public static final int WORKED_IN = 10;

    /** A place a person was: its name, and the point. 989 of the 58,375 have no name at all. */
    public record Place(String name, double lat, double lon) {
        public String label() {
            return name == null || name.isBlank() ? "here" : name;
        }
    }

    /**
     * A person at a place for a span of years, for a reason.
     *
     * @param estimated true when the years are the person's own birth and death and not the
     *                  source's: the register knows he was there, and not when
     */
    public record Stay(int qid, Place place, int from, int to, int why, boolean estimated) {

        /**
         * Had this stay begun by this year? A card dated 800 may not tell that a man died in 813:
         * only a stay that has started is in the record by then (from open, the end year is the
         * only date there is, so it must have come).
         */
        public boolean knownBy(int year) {
            if (from != UNKNOWN) {
                return from <= year;
            }
            return to == UNKNOWN || to <= year;
        }

        /** Was he there in this year? A row with one end open runs to the other end of his life. */
        public boolean covers(int year) {
            if (from != UNKNOWN && year < from) {
                return false;
            }
            return to == UNKNOWN || year <= to;
        }

        public String whyName() {
            return switch (why) {
                case BORN -> "born";
                case DIED -> "died";
                case STUDIED -> "studied at";
                case WORKED_FOR -> "worked for a body seated";
                case SERVED_IN -> "served in a body seated";
                case MEMBER_OF -> "was a member of a body seated";
                case PARTY -> "was of the party seated";
                case FOUNDED -> "founded the body seated";
                case LIVED_AT -> "lived";
                case WORKED_IN -> "worked";
                default -> "was";
            };
        }

        /**
         * A whole sentence about this stay, for the page: "He studied at Cambridge, in a year the
         * record does not give." Unlike {@link #line()} it takes a subject, because the page knows
         * whether it is talking about a man, a woman or a group and this register does not.
         */
        public String sentence(String subject) {
            return subject + " " + sentenceVerb() + place.label() + years();
        }

        /**
         * The same sentence as a page dated {@code year} may write it: a stay that is still going
         * has no end year yet ("he lived in Dove Cottage from 1799 on", not "from 1799 to 1808" on
         * a page dated 1800).
         */
        public String sentence(String subject, int year) {
            return subject + " " + sentenceVerb() + place.label() + years(year);
        }

        private String sentenceVerb() {
            return switch (why) {
                case BORN -> "was born in ";
                case DIED -> "died in ";
                case STUDIED -> "studied at ";
                case WORKED_FOR -> "worked for a body in ";
                case SERVED_IN -> "served in a body in ";
                case MEMBER_OF -> "was a member of a body in ";
                case PARTY -> "was of a party in ";
                case FOUNDED -> "founded a body in ";
                case LIVED_AT -> "lived in ";
                case WORKED_IN -> "worked in ";
                default -> "was in ";
            };
        }

        /** A year the way the cards write one: "1279 BCE", never "-1279" (seen on a pharaoh's page). */
        private static String yearText(int y) {
            return y < 0 ? Math.abs(y) + " BCE" : String.valueOf(y);
        }

        private String years(int year) {
            if (estimated) {
                return ", in a year the record does not give";
            }
            if (from == UNKNOWN && to == UNKNOWN) {
                return "";
            }
            if (to != UNKNOWN && to > year && from != UNKNOWN) {
                return " from " + yearText(from) + " on";
            }
            if (to != UNKNOWN && to > year) {
                return "";
            }
            return years();
        }

        private String years() {
            if (estimated) {
                return ", in a year the record does not give";
            }
            if (from == UNKNOWN && to == UNKNOWN) {
                return "";
            }
            if (from != UNKNOWN && to != UNKNOWN && from != to) {
                return " from " + yearText(from) + " to " + yearText(to);
            }
            if (to == UNKNOWN) {
                return " from " + yearText(from) + " on";
            }
            return from == UNKNOWN ? " until " + yearText(to) : " in " + yearText(to);
        }

        /**
         * The line, in the mod's own words. An undated stay says <i>at some point in his life</i>
         * rather than a pair of years nobody gave it, and a stay with one end open says which end
         * it is missing.
         */
        public String line() {
            StringBuilder sb = new StringBuilder(whyName()).append(' ').append(place.label());
            if (estimated) {
                sb.append(", at some point in his life");
                return sb.toString();
            }
            if (from == UNKNOWN && to == UNKNOWN) {
                return sb.toString();
            }
            if (from != UNKNOWN && to != UNKNOWN && from != to) {
                sb.append(' ').append(from).append(" to ").append(to);
            } else if (from != UNKNOWN) {
                sb.append(' ').append(from);
            } else {
                sb.append(" until ").append(to);
            }
            return sb.toString();
        }
    }

    private static final Presence INSTANCE = new Presence();

    private TileStore tiles;
    private Place[] places = new Place[0];
    private int box = 5;
    private int maxSpan = 120;
    private int qidGroups = 256;
    private boolean loaded;
    private String source = "missing";

    private Presence() {}

    public static Presence get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    /** Where the register came from, for the checks. */
    public String source() {
        ensureLoaded();
        return source;
    }

    public int rows() {
        ensureLoaded();
        return tiles == null ? 0 : tiles.rows();
    }

    public int placeCount() {
        ensureLoaded();
        return places.length;
    }

    public boolean hasData() {
        ensureLoaded();
        return tiles != null && places.length > 0;
    }

    /**
     * Who was within {@code km} of this point in this year, nearest first, each with the reason he
     * was there. This is the answer to <i>who is here now</i>, and it is not the same list as
     * {@code FamousPeople.livingIn}, which is the answer to <i>who is from here</i>.
     */
    public List<Stay> hereIn(double lat, double lon, int year, double km) {
        ensureLoaded();
        List<Stay> out = new ArrayList<>();
        if (tiles == null) {
            return out;
        }
        int ring = (int) Math.ceil(km / (box * 111.0d)) + 1;
        long cy = (long) Math.floor((lat + 90.0d) / box);
        long cx = (long) Math.floor((lon + 180.0d) / box);
        long cols = (long) Math.ceil(360.0d / box);
        int fromBucket = Math.floorDiv(year - maxSpan, 100);
        int toBucket = Math.floorDiv(year, 100);
        for (long dy = -ring; dy <= ring; dy++) {
            for (long dx = -ring; dx <= ring; dx++) {
                String group = "b/" + (cy + dy) + "/" + Math.floorMod(cx + dx, cols);
                if (!tiles.has(group)) {
                    continue;
                }
                for (TileStore.Tile t : tiles.tiles(group, fromBucket, toBucket)) {
                    for (Stay s : open(t)) {
                        if (!s.covers(year)) {
                            continue;
                        }
                        if (haversineKm(lat, lon, s.place().lat(), s.place().lon()) > km) {
                            continue;
                        }
                        out.add(s);
                    }
                }
            }
        }
        out.sort(Comparator.comparingDouble(
                s -> haversineKm(lat, lon, s.place().lat(), s.place().lon())));
        return out;
    }

    /** Everywhere this person is recorded as having been, earliest first. */
    public List<Stay> of(int qid) {
        ensureLoaded();
        List<Stay> out = new ArrayList<>();
        if (tiles == null) {
            return out;
        }
        String group = "q/" + Math.floorMod(qid, qidGroups);
        if (!tiles.has(group)) {
            return out;
        }
        for (TileStore.Tile t : tiles.tiles(group)) {
            for (Stay s : open(t)) {
                if (s.qid() == qid) {
                    out.add(s);
                }
            }
        }
        out.sort(Comparator.<Stay>comparingInt(s -> s.from() == UNKNOWN ? UNKNOWN / 2 : s.from())
                .thenComparingInt(Stay::why));
        return out;
    }

    /** The stays of one person that put him here: "from here", as opposed to "here now". */
    public List<Stay> bornNear(double lat, double lon, int year, double km) {
        return hereIn(lat, lon, year, km).stream().filter(s -> s.why() == BORN).toList();
    }

    /**
     * Why this person is at this point, in this year: his nearest stay here that covers the year,
     * or — when none of them does — his nearest stay here of any year, or null when the register
     * does not put him anywhere near. A person is in the cards because of where he was born and in
     * this register because of where he was, and the page needs the second to say why he is here.
     */
    public Stay stayNear(int qid, double lat, double lon, int year) {
        List<Stay> all = of(qid);
        Stay best = null;
        double bestKm = Double.MAX_VALUE;
        Stay anyYear = null;
        double anyKm = Double.MAX_VALUE;
        for (Stay s : all) {
            double km = haversineKm(lat, lon, s.place().lat(), s.place().lon());
            if (km > STAY_NEAR_KM) {
                continue;
            }
            if (s.covers(year) && km < bestKm) {
                best = s;
                bestKm = km;
            }
            if (km < anyKm && s.knownBy(year)) {
                anyYear = s;
                anyKm = km;
            }
        }
        return best != null ? best : anyYear;
    }

    // ------------------------------------------------------------------ the file

    /**
     * Kilometres between two points. The same haversine as {@code HistoricalPresence}, kept here
     * rather than borrowed: this register reads a data file and answers a question about it, and it
     * should not need the whole engine loaded to do so.
     */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0d;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        return 2.0d * r * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }

    private List<Stay> open(TileStore.Tile t) {
        return TileCache.SHARED.get("presence|" + t.group() + "|" + t.first(),
                TileCache.estimate(t), () -> decode(tiles.inflate(t), t.rows()));
    }

    private List<Stay> decode(byte[] raw, int rows) {
        List<Stay> out = new ArrayList<>(rows);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            for (int i = 0; i < rows; i++) {
                int qid = in.readInt();
                int pid = in.readInt();
                int from = in.readInt();
                int to = in.readInt();
                int why = in.readUnsignedByte();
                int est = in.readUnsignedByte();
                if (pid < 0 || pid >= places.length) {
                    continue;
                }
                out.add(new Stay(qid, places[pid], from, to, why, est != 0));
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    private void readPlaces() {
        if (tiles == null || !tiles.has("p")) {
            return;
        }
        for (TileStore.Tile t : tiles.tiles("p")) {
            byte[] raw = tiles.inflate(t);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
                int n = in.readInt();
                Place[] out = new Place[n];
                for (int i = 0; i < n; i++) {
                    String name = readStr(in);
                    double lat = in.readInt() / 1000.0d;
                    double lon = in.readInt() / 1000.0d;
                    out[i] = new Place(name, lat, lon);
                }
                places = out;
            } catch (IOException e) {
                return;
            }
        }
    }

    private static String readStr(DataInputStream in) throws IOException {
        int n = in.readUnsignedShort();
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = Presence.class.getResourceAsStream(TILES)) {
            if (in == null) {
                source = "resource " + TILES + " absent";
                return;
            }
            tiles = TileStore.read(in);
            box = tiles.metaInt("box", box);
            maxSpan = tiles.metaInt("maxSpan", maxSpan);
            qidGroups = tiles.metaInt("qidGroups", qidGroups);
            readPlaces();
            source = TILES;
        } catch (Exception e) {
            source = "resource " + TILES + " failed: " + e.getMessage();
            tiles = null;
        }
    }

    @Override
    public String toString() {
        ensureLoaded();
        return String.format(Locale.ROOT, "Presence[%s rows, %d places, %s]",
                tiles == null ? "no" : tiles.rows(), places.length, source);
    }
}

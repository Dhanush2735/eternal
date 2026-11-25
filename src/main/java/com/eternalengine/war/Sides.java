package com.eternalengine.war;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Step 40 (D10 armies in action): <b>who is fighting, and as what</b>.
 *
 * <h3>What was there before</h3>
 *
 * <p>{@code War} holds two lists of faction ids. That is the whole of its idea of a side: a side
 * is an integer, and two integers are either the same or different. It cannot say whether the
 * party is a state, whether it can be bargained with, whether its troops are its own or hired, or
 * whether it is fighting for a frontier or for a throne.
 *
 * <h3>What this register does</h3>
 *
 * <p>It is built on one rule taken from the source, and it is the rule most army models get
 * wrong:
 *
 * <blockquote>"conflicts between warlords when there is no state involvement qualify as violent
 * conflicts." — Peter Brecke, <i>Violent Conflicts 1400 A.D. to the Present</i></blockquote>
 *
 * <p>Brecke's Conflict Catalog — 3,708 conflicts, 1400 to 2000 — records who fought, who was on
 * each side in conflicts of more than two participants, who initiated, the start and end dates,
 * the location, the common name, the military and total fatalities, and the number of significant
 * political actors. Its threshold is 32 deaths in a year. Its dates are, in his own words, "the
 * dates when the weapons begin to clash and when they cease."
 *
 * <p>So a party need not be a state. It can be a warlord, a company, an order, a city, a people,
 * a rising — or civilians, because the catalog's definition covers "political disagreements
 * between organized groups, or between an organized group and civilians."
 *
 * <h3>The two fields that do work</h3>
 *
 * <ul>
 * <li><b>{@code willTreat}</b> — whether this kind of party can be bound by an agreement. The
 *     siege register offers a conditional surrender only to a town that can be held to one, and a
 *     conditional surrender signed by a captain whose men are owed four months' pay is not worth
 *     the parchment. This is where that comes from.</li>
 * <li><b>{@code raised}</b> — which row of the morale register tells you how this party's troops
 *     were raised, and therefore what they will put up with and how fast they will go.</li>
 * </ul>
 *
 * <h3>What this register will refuse to do</h3>
 *
 * <p><b>The catalog itself was never loaded.</b> It is a binary spreadsheet and no row of it was
 * read into this pack. What is here is the taxonomy of parties that the catalog's own description
 * of its fields admits. There are no conflicts in it, no counts and no dates, and it will not
 * print "3,708 conflicts" as if there were.
 *
 * <p>Brecke's own coverage warning applies to every row here: the catalog is "quite complete" for
 * Western Europe, Eastern Europe, North Africa, West and Central Africa and East and South Africa
 * — and not for the rest. His 1400 threshold is Luard's, and marks the point "before which the
 * quality and extent of data about many parts of the world drop off precipitously." Rows here that
 * begin before 1400 are not from the catalog at all and name their own sources.
 */
public final class Sides {

    /**
     * One kind of party: who it is, what its troops are, and whether it can be bound.
     *
     * <p>{@code raised} is always a real row id in the morale pack — the build refuses a party
     * whose troops are raised in a way the register has never heard of, because it could not
     * simulate them.
     */
    public record Party(String id, String name, String kind, int start, int end, double[] box,
                        String raised, boolean willTreat, boolean canInitiate, String scope,
                        String needs, String doing, String note, String source, String mark) {

        public boolean inForce(int year) {
            return year >= start && year <= end;
        }

        public boolean here(double lat, double lon) {
            return lat >= box[0] && lat <= box[1] && lon >= box[2] && lon <= box[3];
        }

        /** A party that cannot be held to a signature: a band, a rising, an unpaid company. */
        public boolean cannotTreat() {
            return !willTreat;
        }

        /**
         * Can a latitude, a longitude and a year guess this party?
         *
         * <p>The steppe in 1200 raises a khan's following and a European frontier in 1400 belongs
         * to a sovereign, and a map can say both. Nothing on a map can tell you that Florence is
         * a commune, that this war is dynastic, or that the troops are a hired company — so those
         * are {@code local} and have to be named by a caller who knows, exactly as a concentric
         * castle had to be in the wall register.
         */
        public boolean guessable() {
            return "general".equals(scope);
        }

        /** How its troops were raised, or null if the morale pack has no such row. */
        public Morale.Raised raisedRow() {
            return Morale.raised(raised);
        }

        /** The base worth of the troops this party fields, before anything is done to them. */
        public int cohesion() {
            Morale.Raised r = raisedRow();
            return r == null ? 40 : r.cohesion();
        }
    }

    /**
     * One side in a war: a party, and the parties standing with it.
     *
     * <p>A coalition's {@code willTreat} is the <i>slowest</i> true in the register — every party
     * in it has to agree — so a side carries its members and answers for the weakest of them.
     */
    public record Side(String id, List<Party> members) {

        /** Every party in a side has to be able to sign, or the signature binds nobody. */
        public boolean willTreat() {
            for (Party p : members) {
                if (!p.willTreat()) {
                    return false;
                }
            }
            return !members.isEmpty();
        }

        /** The party that speaks for the side: the first one that can be bargained with. */
        public Party speaker() {
            for (Party p : members) {
                if (p.willTreat()) {
                    return p;
                }
            }
            return members.isEmpty() ? null : members.get(0);
        }

        /**
         * The worth of the troops on this side, as the weakest body in it — because a line is
         * only as good as the unit that gives way.
         */
        public int cohesion() {
            int worst = 100;
            for (Party p : members) {
                worst = Math.min(worst, p.cohesion());
            }
            return members.isEmpty() ? 40 : worst;
        }

        /** How fast this side's men will go, as the worst rate among its members. */
        public double desertionPerYear(int year, double lat, double lon, boolean winning,
                                       boolean home, boolean paid) {
            double worst = 0.0d;
            for (Party p : members) {
                Morale.State st = new Morale.State(year, lat, lon, p.raised(),
                    FormationType.LINE, 2, winning, !winning, false, false, home, paid, false,
                    false, false, false, false, "conscript_national".equals(p.raised()),
                    "mercenary_company".equals(p.raised()), false, false, false);
                worst = Math.max(worst, Morale.desertionPerYear(st));
            }
            return worst;
        }
    }

    // ================================================================== the register

    private static final Sides INSTANCE = new Sides();
    private final List<Party> parties;

    private Sides() {
        List<Party> ps = new ArrayList<>();
        JsonObject jo = read(RESOURCE);
        if (jo != null && jo.has("parties")) {
            for (JsonElement e : jo.getAsJsonArray("parties")) {
                JsonObject o = e.getAsJsonObject();
                ps.add(new Party(str(o, "id"), str(o, "name"), str(o, "kind"),
                    o.get("start").getAsInt(), o.get("end").getAsInt(), box(o), str(o, "raised"),
                    o.has("willTreat") && o.get("willTreat").getAsBoolean(),
                    o.has("canInitiate") && o.get("canInitiate").getAsBoolean(),
                    str(o, "scope").isEmpty() ? "general" : str(o, "scope"), str(o, "needs"),
                    str(o, "doing"), str(o, "note"), str(o, "src"), str(o, "mark")));
            }
        }
        this.parties = Collections.unmodifiableList(ps);
    }

    public static Sides get() {
        return INSTANCE;
    }

    public static List<Party> parties() {
        return get().parties;
    }

    public static int partyCount() {
        return get().parties.size();
    }

    public static Party party(String id) {
        for (Party p : get().parties) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * The ordinary kind of party in this place and year, among the ones a map can guess.
     *
     * <p>Two rules, in this order. The <b>narrowest</b> box that covers the place wins, and only
     * then the latest start — the same rule the wall and morale registers use, so that all three
     * answer about the same place the same way. Then, among the general parties of equal reach,
     * the one that can be bound by a treaty wins, because where there is a sovereign he is the
     * default and a warlord band is the exception.
     *
     * <p><b>Local parties are left out, and that is deliberate.</b> Nothing in a latitude and a
     * year tells you that this town governs itself or that its troops are hired, so a party marked
     * {@code local} has to be named by a caller who knows. A commune that knows it is a commune
     * says so; the map does not.
     */
    public static Party partyAt(double lat, double lon, int year) {
        Party best = null;
        for (Party p : get().parties) {
            if (!p.inForce(year) || !p.here(lat, lon) || !p.guessable()) {
                continue;
            }
            if (best == null || narrower(p, best)) {
                best = p;
            }
        }
        return best;
    }

    /** Every kind of party in force in this year that could have started a war. */
    public static List<Party> aggressors(int year) {
        List<Party> out = new ArrayList<>();
        for (Party p : get().parties) {
            if (p.inForce(year) && p.canInitiate()) {
                out.add(p);
            }
        }
        return out;
    }

    /** The kinds of party that can be bargained with in this year. */
    public static List<Party> canTreat(int year) {
        List<Party> out = new ArrayList<>();
        for (Party p : get().parties) {
            if (p.inForce(year) && p.willTreat()) {
                out.add(p);
            }
        }
        return out;
    }

    /** The kinds of party that cannot, which is the more interesting list. */
    public static List<Party> cannotTreat(int year) {
        List<Party> out = new ArrayList<>();
        for (Party p : get().parties) {
            if (p.inForce(year) && !p.willTreat()) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Build a side out of party ids, dropping any the register has never heard of.
     *
     * <p>A side made of a state and a hired company cannot treat, because the company cannot, and
     * that is the whole point of {@link Side#willTreat()}.
     */
    public static Side side(List<String> ids) {
        List<Party> ms = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Party p = party(id);
                if (p != null) {
                    ms.add(p);
                }
            }
        }
        return new Side(ms.isEmpty() ? "no_one" : String.join("+", ids),
            Collections.unmodifiableList(ms));
    }

    // ================================================================== plumbing

    /** Narrower box first; then a party that can be bound; then the later start. */
    private static boolean narrower(Party p, Party best) {
        double a = area(p.box());
        double b = area(best.box());
        if (a != b) {
            return a < b;
        }
        if (p.willTreat() != best.willTreat()) {
            return p.willTreat();
        }
        return p.start() > best.start();
    }

    private static double area(double[] b) {
        return Math.max(0.01d, (b[1] - b[0]) * (b[3] - b[2]));
    }

    private static double[] box(JsonObject o) {
        double[] b = new double[4];
        if (o.has("box")) {
            JsonArray a = o.getAsJsonArray("box");
            for (int i = 0; i < 4 && i < a.size(); i++) {
                b[i] = a.get(i).getAsDouble();
            }
        }
        return b;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static JsonObject read(String resource) {
        try (InputStream in = Sides.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader r = new InputStreamReader(
                    new GZIPInputStream(in), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    private static final String RESOURCE =
        "/data/eternalengine/eternal/history/sides.json.gz";
}

package com.eternalengine.player;

import com.eternalengine.player.Permissions.Action;
import com.eternalengine.player.Permissions.Actor;
import com.eternalengine.player.Permissions.Decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Property per timeline: houses, land and shops.</b> F4.
 *
 * <h2>Why this class exists</h2>
 *
 * The survey found no claim, no deed and no boundary anywhere in the mod. A player who builds a house on a
 * branch owns nothing when the world switches back, and two players who build in one place have no rule to
 * settle it. F4 asks for exactly this, per timeline, and per timeline is the whole point:
 *
 * <p><b>A house built on a branch does not exist on the real history.</b> So a property book is per
 * (universe, branch), the same key a pocket is saved under, and every answer this class gives names the
 * timeline it is answering for. "What do I own?" without "where and when?" is not a question this class will
 * pretend to answer.
 *
 * <h2>The rules</h2>
 *
 * <ol>
 *   <li><b>One claim covers a place.</b> Two claims in one timeline may not overlap a single block, because a
 *       place with two owners is a dispute, not a deed. Across timelines there is no conflict at all: the same
 *       coordinates may be owned by different people on different branches, and both are true.</li>
 *   <li><b>One owner per claim.</b> Selling and giving record both parties and the year; the book keeps that
 *       history, so "who owned it in 1857" has an answer.</li>
 *   <li><b>What is yours, you may release, sell or reshape.</b> What is another's needs
 *       {@link Permissions.Action#EDIT_ANY_PROPERTY}: an operator. Taking a claim from somebody is
 *       confiscation, and the book records who did it and why.</li>
 *   <li><b>A claim has a size a player can walk.</b> {@value #MAX_SIDE} blocks on a side. The number is a
 *       game default, not a fact of history, and it exists because a claim the size of a province is a claim
 *       nobody can check.</li>
 * </ol>
 */
public final class Property {

    /** A game default, not history: a claim wider than this is a claim nobody can walk. */
    public static final int MAX_SIDE = 256;

    /** <b>What a place is for.</b> */
    public enum Kind {
        HOUSE("a house"),
        LAND("land"),
        SHOP("a shop");

        private final String words;

        Kind(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /**
     * <b>One deed.</b> The box is inclusive on both ends and stored sorted, so a claim is the same claim
     * however it was typed. {@code since} is the year it was claimed.
     */
    public record Claim(String id, Kind kind, String owner,
                        int x1, int y1, int z1, int x2, int y2, int z2,
                        int since, String note) {

        public int blocks() {
            return (Math.abs(x2 - x1) + 1) * (Math.abs(y2 - y1) + 1) * (Math.abs(z2 - z1) + 1);
        }

        public boolean covers(int x, int y, int z) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }

        public boolean overlaps(Claim o) {
            return o != null && x1 <= o.x2 && o.x1 <= x2
                    && y1 <= o.y2 && o.y1 <= y2
                    && z1 <= o.z2 && o.z1 <= z2;
        }

        public String line() {
            return id + " · " + kind.words() + " · " + owner + " · (" + x1 + "," + y1 + "," + z1 + ")-("
                    + x2 + "," + y2 + "," + z2 + ") · " + blocks() + " blocks · claimed " + since
                    + (note == null || note.isEmpty() ? "" : " · " + note);
        }
    }

    /**
     * <b>The outcome of claiming.</b> Either a deed, or the reason there is none: a claim half-made is a
     * dispute waiting to happen.
     */
    public record Result(Claim claim, String refused) {
        public boolean ok() {
            return claim != null;
        }
    }

    /** <b>One change of hands.</b> Every deed's history is these rows, in order. */
    public record Hand(String id, String from, String to, int year, String why) {
        public String line() {
            return id + " · " + from + " -> " + to + " · " + year + " · " + why;
        }
    }

    private final String universe;
    private final String branch;
    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final List<Hand> history = new ArrayList<>();

    /** A property book for exactly one timeline. Two books, two timelines, one pair of coordinates. */
    public Property(String universe, String branch) {
        if (universe == null || universe.trim().isEmpty() || branch == null || branch.trim().isEmpty()) {
            throw new IllegalArgumentException("a property book is per timeline: name the universe and the "
                    + "branch it answers for");
        }
        this.universe = universe.trim();
        this.branch = branch.trim();
    }

    /** Which timeline this book answers for. Every answer of this class carries it. */
    public String where() {
        return universe + "/" + branch;
    }

    /**
     * <b>Claim a place.</b> Refused if the name is taken, the box is not a box, the box is too wide to walk,
     * or any block of it is already somebody's. Nobody needs to ask permission to claim what is unclaimed:
     * {@link Permissions} calls that an act that moves only you.
     */
    public Result claim(String id, Kind kind, String owner,
                        int x1, int y1, int z1, int x2, int y2, int z2,
                        int since, String note) {
        if (id == null || id.trim().isEmpty()) {
            return new Result(null, "a deed with no name is no deed");
        }
        for (String k : claims.keySet()) {
            if (k.equalsIgnoreCase(id.trim())) {
                return new Result(null, "there is already a deed called " + k + " on " + where()
                        + "; two deeds with one name is a question");
            }
        }
        if (kind == null) {
            return new Result(null, "a deed must say whether it is a house, land or a shop");
        }
        if (owner == null || owner.trim().isEmpty()) {
            return new Result(null, "a deed must say whose it is");
        }
        String why = boxOk(x1, y1, z1, x2, y2, z2);
        if (why != null) {
            return new Result(null, why);
        }
        Claim want = sorted(id.trim(), kind, owner.trim(), x1, y1, z1, x2, y2, z2, since, note);
        for (Claim c : claims.values()) {
            if (c.overlaps(want)) {
                return new Result(null, want.id + " covers blocks that are already " + c.owner + "'s in "
                        + c.id + " on " + where() + "; one place, one deed");
            }
        }
        claims.put(want.id(), want);
        history.add(new Hand(want.id(), "nobody", want.owner(), since, "claimed"));
        return new Result(want, "");
    }

    /** <b>Give a claim up.</b> The owner may; so may an operator; a stranger may not. */
    public Decision release(Actor by, String id, int year, String why) {
        Claim c = find(id);
        if (c == null) {
            return Decision.no("there is no deed called " + id + " on " + where());
        }
        Decision may = mineOrOperator(by, c);
        if (!may.allowed()) {
            return may;
        }
        claims.remove(c.id());
        history.add(new Hand(c.id(), c.owner(), "nobody", year,
                why == null || why.trim().isEmpty() ? "released" : why.trim()));
        return Decision.yes(c.id() + " is released on " + where() + " and is nobody's again");
    }

    /**
     * <b>Sell or give a claim.</b> The owner may, an operator may, a stranger may not. Both parties and the
     * year go in the book, so the next dispute has something to read.
     */
    public Decision transfer(Actor by, String id, String to, int year, String why) {
        Claim c = find(id);
        if (c == null) {
            return Decision.no("there is no deed called " + id + " on " + where());
        }
        if (to == null || to.trim().isEmpty()) {
            return Decision.no("a deed cannot be given to nobody");
        }
        if (to.trim().equalsIgnoreCase(c.owner())) {
            return Decision.no(to.trim() + " already owns " + c.id() + " on " + where());
        }
        Decision may = mineOrOperator(by, c);
        if (!may.allowed()) {
            return may;
        }
        claims.put(c.id(), new Claim(c.id(), c.kind(), to.trim(), c.x1(), c.y1(), c.z1(), c.x2(), c.y2(),
                c.z2(), c.since(), c.note()));
        history.add(new Hand(c.id(), c.owner(), to.trim(), year,
                why == null || why.trim().isEmpty() ? "changed hands" : why.trim()));
        return Decision.yes(c.id() + " passes from " + c.owner() + " to " + to.trim() + " on " + where());
    }

    /**
     * <b>Take a claim from somebody.</b> This is confiscation: {@link Permissions} calls it an act that
     * rewrites what is recorded, and it is for operators alone. The book says who took it and why.
     */
    public Decision take(Actor by, String id, int year, String why) {
        Claim c = find(id);
        if (c == null) {
            return Decision.no("there is no deed called " + id + " on " + where());
        }
        Decision may = Permissions.mayAlone(by, Action.EDIT_ANY_PROPERTY, 1);
        if (!may.allowed()) {
            return Decision.no(may.why() + "; " + c.owner() + "'s deed is not "
                    + (by == null ? "nobody" : by.name()) + "'s to take");
        }
        claims.put(c.id(), new Claim(c.id(), c.kind(), by.name().trim(), c.x1(), c.y1(), c.z1(), c.x2(),
                c.y2(), c.z2(), c.since(), c.note()));
        history.add(new Hand(c.id(), c.owner(), by.name().trim(), year,
                why == null || why.trim().isEmpty() ? "taken" : why.trim()));
        return Decision.yes(by.name() + " takes " + c.id() + " from " + c.owner() + " on " + where()
                + (why == null || why.trim().isEmpty() ? "" : ", saying " + why.trim()));
    }

    /**
     * <b>Reshape what is yours.</b> The same rules as claiming: a proper box, walkable, and no block of
     * anybody else's. A stranger's deed needs an operator.
     */
    public Decision reshape(Actor by, String id,
                            int x1, int y1, int z1, int x2, int y2, int z2, int since) {
        Claim c = find(id);
        if (c == null) {
            return Decision.no("there is no deed called " + id + " on " + where());
        }
        Decision may = mineOrOperator(by, c);
        if (!may.allowed()) {
            return may;
        }
        String why = boxOk(x1, y1, z1, x2, y2, z2);
        if (why != null) {
            return Decision.no(why);
        }
        Claim want = sorted(c.id(), c.kind(), c.owner(), x1, y1, z1, x2, y2, z2, since, c.note());
        for (Claim o : claims.values()) {
            if (!o.id().equalsIgnoreCase(c.id()) && o.overlaps(want)) {
                return Decision.no(want.id + " would cover blocks that are already " + o.owner + "'s in "
                        + o.id + " on " + where());
            }
        }
        claims.put(c.id(), want);
        return Decision.yes(c.id() + " is now (" + want.x1() + "," + want.y1() + "," + want.z1() + ")-("
                + want.x2() + "," + want.y2() + "," + want.z2() + ") on " + where());
    }

    // ------------------------------------------------------------------ queries

    public Claim find(String id) {
        if (id == null) {
            return null;
        }
        for (Map.Entry<String, Claim> e : claims.entrySet()) {
            if (e.getKey().equalsIgnoreCase(id.trim())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** What a player owns here, and only here: the timeline is in the name of the book. */
    public List<Claim> ownedBy(String player) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims.values()) {
            if (c.owner().equalsIgnoreCase(player == null ? "" : player.trim())) {
                out.add(c);
            }
        }
        return out;
    }

    /** What covers this block, or null. One place, one deed: there is never a second answer. */
    public Claim at(int x, int y, int z) {
        for (Claim c : claims.values()) {
            if (c.covers(x, y, z)) {
                return c;
            }
        }
        return null;
    }

    /** Everything that has happened to one deed. */
    public List<Hand> historyOf(String id) {
        List<Hand> out = new ArrayList<>();
        for (Hand h : history) {
            if (h.id().equalsIgnoreCase(id == null ? "" : id.trim())) {
                out.add(h);
            }
        }
        return out;
    }

    /** The book's page: the timeline it answers for, then every deed. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(where() + " · " + claims.size() + (claims.size() == 1 ? " deed" : " deeds"));
        for (Claim c : claims.values()) {
            out.add("  " + c.line());
        }
        return out;
    }

    public static String ruleLine() {
        return "A deed is per timeline: one place, one owner, one name. What is yours you may give up or sell; "
                + "what is another's needs an operator; and the book keeps every change of hands.";
    }

    // ------------------------------------------------------------------ internals

    /**
     * The owner of a deed may act on it; anybody else may not, unless they are an operator. The first version
     * of this method fell back to {@code CLAIM_PROPERTY}, which is an act that moves only you and so allows
     * everyone: a stranger could sell, release or reshape anybody's deed. The fallback is
     * {@link Action#EDIT_ANY_PROPERTY}, which is the act of touching what is another's.
     */
    private Decision mineOrOperator(Actor by, Claim c) {
        if (by == null) {
            return Decision.no("nobody cannot act on a deed");
        }
        if (by.name() != null && by.name().trim().equalsIgnoreCase(c.owner())) {
            return Decision.yes(by.name() + " owns " + c.id() + " on " + where());
        }
        Decision op = Permissions.mayAlone(by, Action.EDIT_ANY_PROPERTY, 1);
        if (op.allowed()) {
            return op;
        }
        return Decision.no(c.id() + " on " + where() + " is " + c.owner() + "'s, and "
                + by.name() + " is " + by.rank().words() + "; " + op.why());
    }

    private static String boxOk(int x1, int y1, int z1, int x2, int y2, int z2) {
        int sx = Math.abs(x2 - x1) + 1;
        int sy = Math.abs(y2 - y1) + 1;
        int sz = Math.abs(z2 - z1) + 1;
        if (sx > MAX_SIDE || sy > MAX_SIDE || sz > MAX_SIDE) {
            return "a claim of " + sx + "x" + sy + "x" + sz + " is wider than " + MAX_SIDE
                    + " on a side, and a claim the size of a province is a claim nobody can check";
        }
        return null;
    }

    private static Claim sorted(String id, Kind kind, String owner,
                                int x1, int y1, int z1, int x2, int y2, int z2,
                                int since, String note) {
        return new Claim(id, kind, owner,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2),
                since, note == null ? "" : note.trim());
    }
}

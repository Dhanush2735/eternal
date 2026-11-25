package com.eternalengine.buildings;

import com.eternalengine.civilization.Disasters;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Step 36 (B21): <b>what the player built, and where it went</b> across year jumps and timelines.
 *
 * <p>Everything else in this mod is history: things that happened, read out of a pack. This is the
 * one system where the world is changed by somebody standing in it, and that raises the question
 * the whole step turns on. **The player builds a house in Rome in AD 100. They jump to 1400. Is
 * the house there?**
 *
 * <p>It has to be. If a year jump threw away everything a player had built, nobody would ever
 * build anything, and the jump would be a punishment rather than the point of the mod. But it
 * cannot simply be there either, untouched — because the register next door, {@link BuildingLife},
 * says that everything standing in Rome in 1400 has been through the earthquake of 1349, and a
 * player's house is not exempt from physics.
 *
 * <p>So the answer has four parts, and this class is all four:
 *
 * <ol>
 *   <li><b>The edit is remembered, not just placed.</b> Every change is recorded with the year it
 *       was made, the branch it was made on, and who made it. Building a house is not only a set
 *       of blocks in a world; it is a fact the mod keeps.</li>
 *   <li><b>A jump forward keeps it.</b> The house built in 1000 still stands in 1500, because
 *       nothing destroyed it and the world does not forget.</li>
 *   <li><b>A jump backward hides it and does not forget it.</b> Go back to 900 and the house is
 *       not there — it has not been built yet — but the record of it is untouched, and jumping
 *       forward again brings it back exactly as it was. Going back is not undoing.</li>
 *   <li><b>A branch shows only its own.</b> Edits made on a branch belong to that branch. A branch
 *       inherits what its ancestors did before the split and nothing they did after it, which is
 *       what makes a divergence a divergence.</li>
 * </ol>
 *
 * <h2>And it is subject to history like anything else</h2>
 *
 * <p>{@link #lifeOf} runs a player's building through the same {@link BuildingLife} arithmetic as
 * the Colosseum. A house built in Naples in AD 78 is a ruin by AD 80, and a player who builds in
 * the shadow of Vesuvius has been told so by the world rather than by a warning. That is the join
 * between B10 and B21, and it is the reason this class lives in the buildings package next to the
 * life model rather than over in {@code timeline}.
 *
 * <p>This is <b>per-world mutable state</b>, not a read-only register like {@link Disasters} or
 * {@link FamousBuildings}. There is deliberately no {@code get()}: the holder is the world, and
 * this object is saved and restored with it through {@link #toJson()} and {@link #fromJson}.
 */
public final class PlayerEdits {

    /** The branch every world starts on. */
    public static final String MAIN = "main";

    /** The divergence year of a branch that has not diverged: the active leaf of a chain. */
    public static final int NO_DIVERGENCE = Integer.MAX_VALUE;

    /** What a player did. */
    public enum Kind {
        /** Put something up that was not there. */
        BUILD,
        /** Pull something down, whether the world put it there or the player did. */
        DEMOLISH,
        /** Mend something that was damaged, which is how a player answers a disaster. */
        REPAIR,
        /** Add to something standing. */
        EXTEND,
        /** Name or rename it. */
        RENAME,
        /** Mark ground as theirs, without putting anything on it yet. */
        CLAIM;

        /** Does this put matter into the world, rather than take it away? */
        public boolean adds() {
            return this == BUILD || this == EXTEND;
        }
    }

    /**
     * One change a player made to the world.
     *
     * @param year     the year it was made in — the single fact that decides whether a jump shows
     *                 it or hides it
     * @param branchId the branch it was made on
     */
    public record Edit(String id, Kind kind, String name, double lat, double lon, int year,
                       String branchId, String playerUuid, String note) {

        /** Was it made by this time? The question every jump asks. */
        public boolean madeBy(int atYear) {
            return year <= atYear;
        }

        public boolean onBranch(String branch) {
            return branchId.equals(branch);
        }
    }

    /**
     * One link in the chain of branches from the root of the tree down to the one being played.
     *
     * @param divergesAt the year this branch split from its child, or {@link #NO_DIVERGENCE} for
     *                   the branch at the end of the chain
     */
    public record Branch(String id, int divergesAt) {}

    /**
     * What a year jump has to do to the world: take these out, put these in.
     */
    public record Jump(int fromYear, int toYear, List<Edit> remove, List<Edit> add) {

        public boolean isEmpty() {
            return remove.isEmpty() && add.isEmpty();
        }

        /** One line for a player, because a silent world is a confusing one. */
        public String line() {
            if (isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (!remove.isEmpty()) {
                sb.append(remove.size() == 1 ? "your " + remove.get(0).name() + " is "
                        : remove.size() + " of your buildings are ");
                sb.append(toYear < fromYear ? "not built yet here" : "no longer here");
            }
            if (!add.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(add.size() == 1 ? "your " + add.get(0).name() + " is here"
                        : add.size() + " of your buildings are here");
            }
            return sb.toString();
        }
    }

    private final Map<String, Edit> byId = new LinkedHashMap<>();

    // ------------------------------------------------------------------ recording

    /** Records a change and hands back the record, with an id of its own. */
    public Edit record(Kind kind, String name, double lat, double lon, int year,
                       String branchId, String playerUuid, String note) {
        Edit e = new Edit(kind.name().toLowerCase(Locale.ROOT) + "_"
                + UUID.randomUUID().toString().substring(0, 8),
                kind, name, lat, lon, year,
                branchId == null || branchId.isEmpty() ? MAIN : branchId,
                playerUuid == null ? "" : playerUuid, note == null ? "" : note);
        byId.put(e.id(), e);
        return e;
    }

    /** Forgets a change outright — the player's own undo, not a jump. */
    public void forget(String id) {
        byId.remove(id);
    }

    public List<Edit> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /** Every edit made on one branch, whatever year. */
    public List<Edit> onBranch(String branchId) {
        List<Edit> out = new ArrayList<>();
        for (Edit e : byId.values()) {
            if (e.onBranch(branchId)) {
                out.add(e);
            }
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ what the world shows

    /** One branch and no ancestors: the common case. */
    public List<Edit> standingIn(int year, String branchId) {
        return standingIn(year, List.of(new Branch(branchId, NO_DIVERGENCE)));
    }

    /**
     * What a player's changes amount to, in this year, on this branch — the buildings that should
     * be standing in the world.
     *
     * <p>The chain runs from the root of the branch tree down to the one being played. A branch
     * contributes its edits up to its own divergence year: what an ancestor did *before* the split
     * is shared history and carries into the child, and what it did *after* the split is not, which
     * is the whole difference between a divergence and a save game.
     *
     * <p>A {@link Kind#DEMOLISH} takes its target out of the answer from its own year on, which is
     * how a player pulls down their own house — and how they pull down one the world built.
     */
    public List<Edit> standingIn(int year, List<Branch> chain) {
        List<Edit> out = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            Branch b = chain.get(i);
            int until = b.divergesAt();
            for (Edit e : byId.values()) {
                if (!e.onBranch(b.id())) {
                    continue;
                }
                if (!e.madeBy(year) || e.year() > until) {
                    continue;
                }
                out.add(e);
            }
        }
        // a demolition takes its target out, from the year of the demolition onwards
        List<Edit> kept = new ArrayList<>();
        for (Edit e : out) {
            if (e.kind() == Kind.DEMOLISH) {
                continue;
            }
            boolean gone = false;
            for (Edit other : out) {
                // a demolition after the building went up takes it down; one before it went up
                // cannot, and is what lets a player clear a site and build again on the same spot
                if (other.kind() != Kind.DEMOLISH || other.year() < e.year()) {
                    continue;
                }
                if (samePlace(other, e)) {
                    gone = true;
                    break;
                }
            }
            if (!gone) {
                kept.add(e);
            }
        }
        return List.copyOf(kept);
    }

    /** Is this one edit standing in this year, on this branch? */
    public boolean standsIn(Edit e, int year, List<Branch> chain) {
        if (e.kind() == Kind.DEMOLISH) {
            return false;
        }
        for (Edit s : standingIn(year, chain)) {
            if (s.id().equals(e.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The diff for a jump: what to take out of the world and what to put into it.
     *
     * <p>Jumping <b>forward</b> adds what has been built since and removes what has been pulled
     * down since. Jumping <b>backward</b> removes what has not been built yet — which is the
     * behaviour this whole system exists to get right, and which is why the record is kept
     * separately from the world: the edit is still in {@link #all()}, only out of the answer.
     */
    public Jump jump(int fromYear, int toYear, List<Branch> chain) {
        List<Edit> before = standingIn(fromYear, chain);
        List<Edit> after = standingIn(toYear, chain);
        List<Edit> remove = new ArrayList<>();
        List<Edit> add = new ArrayList<>();
        for (Edit e : before) {
            if (!containsId(after, e.id())) {
                remove.add(e);
            }
        }
        for (Edit e : after) {
            if (!containsId(before, e.id())) {
                add.add(e);
            }
        }
        return new Jump(fromYear, toYear, List.copyOf(remove), List.copyOf(add));
    }

    private static boolean containsId(List<Edit> list, String id) {
        for (Edit e : list) {
            if (e.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Two edits at the same place: within a metre of latitude and longitude. */
    private static boolean samePlace(Edit a, Edit b) {
        return Math.abs(a.lat() - b.lat()) < 1.0e-5d && Math.abs(a.lon() - b.lon()) < 1.0e-5d;
    }

    // ------------------------------------------------------------------ history catches up

    /**
     * B21 through B10's eyes: what state is the player's building in, in this year?
     *
     * <p>The same arithmetic {@link BuildingLife} applies to the Colosseum, applied to a house
     * somebody put up themselves. A building put up in Naples in AD 78 is a ruin by AD 80. One put
     * up in 1755 Lisbon is damaged. One put up in a village emptied by plague decays, because the
     * player's building is no more exempt from having nobody to mend it than anyone else's.
     */
    public BuildingLife.Life lifeOf(Edit e, int year, double care) {
        return BuildingLife.of(subjectOf(e), year, care);
    }

    public BuildingLife.Life lifeOf(Edit e, int year) {
        return lifeOf(e, year, BuildingLife.DEFAULT_CARE);
    }

    static BuildingLife.Subject subjectOf(Edit e) {
        return new BuildingLife.Subject(e.name(), e.lat(), e.lon(),
                e.kind() == Kind.CLAIM ? "claim" : "house",
                e.year(), FamousBuildings.NO_END, false, false);
    }

    /** One line for a player: what their building is, and how it has fared. */
    public String describe(Edit e, int year, double care) {
        BuildingLife.Life l = lifeOf(e, year, care);
        if (!standsIn(e, year, List.of(new Branch(e.branchId(), NO_DIVERGENCE)))) {
            return e.name() + " is not here in " + yearText(year)
                    + (year < e.year() ? " — it is not built until " + yearText(e.year()) : "");
        }
        return l.line();
    }

    private static String yearText(int year) {
        return year < 0 ? (-year) + " BC" : "AD " + year;
    }

    // ------------------------------------------------------------------ saving

    /** The record of everything the player did, for the world's save file. */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Edit e : byId.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("kind", e.kind().name());
            o.addProperty("name", e.name());
            o.addProperty("lat", e.lat());
            o.addProperty("lon", e.lon());
            o.addProperty("year", e.year());
            o.addProperty("branchId", e.branchId());
            o.addProperty("playerUuid", e.playerUuid());
            o.addProperty("note", e.note());
            arr.add(o);
        }
        root.add("edits", arr);
        return root;
    }

    public static PlayerEdits fromJson(JsonObject root) {
        PlayerEdits p = new PlayerEdits();
        if (root == null || !root.has("edits")) {
            return p;
        }
        for (JsonElement el : root.getAsJsonArray("edits")) {
            JsonObject o = el.getAsJsonObject();
            Kind kind;
            try {
                kind = Kind.valueOf(o.get("kind").getAsString());
            } catch (Exception ex) {
                kind = Kind.BUILD;
            }
            Edit e = new Edit(text(o, "id"), kind, text(o, "name"),
                    o.has("lat") ? o.get("lat").getAsDouble() : 0.0d,
                    o.has("lon") ? o.get("lon").getAsDouble() : 0.0d,
                    o.has("year") ? o.get("year").getAsInt() : 0,
                    text(o, "branchId"), text(o, "playerUuid"), text(o, "note"));
            p.byId.put(e.id(), e);
        }
        return p;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}

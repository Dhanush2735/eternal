package com.eternalengine.timeline;

import com.eternalengine.terrabridge.LandSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Rupture: a new universe with other land.</b> H3's act half, and the check.
 *
 * <h2>Why this class exists</h2>
 *
 * H3 lives in Terra++ (their generator makes the other land) and its dependency is <b>check it</b>. So there
 * are two halves on this side of the seam:
 *
 * <ol>
 *   <li><b>The act</b>: breaking open a new universe. This mod names it, records which universe it broke off
 *       from and what its land is ({@link LandSpec}), refuses doubles and no-ops, and - the rule from step 51 -
 *       <b>will not rupture until the copy has been taken</b>. The copy is {@link BeforeChange}'s
 *       {@code RUPTURE} kind: a rupture is exactly the "big change" A17 was built for.</li>
 *   <li><b>The check</b>: asking the generator what it actually made ({@link Port}) and comparing it to the
 *       spec, key by key. With no generator connected, the check <b>says it cannot check</b> and passes
 *       nothing: a quiet pass would make a ruptured world with ordinary land look checked.</li>
 * </ol>
 *
 * <h2>What a rupture must change</h2>
 *
 * <p>Something. A rupture whose land is its parent's land is not a rupture and is refused. The list of what it
 * changed is computed, not declared: {@code LandSpec.changes} between parent and child, in words.
 */
public final class Ruptures {

    /**
     * <b>One universe, and where it came from.</b> {@code parent} is null only for the unbroken one.
     */
    public record Universe(String id, String parent, LandSpec.Spec land, int year, String why,
                           List<String> changes) {

        public String line() {
            return id + (parent == null ? "" : " · broke off from " + parent) + " · " + year + " · "
                    + land.describe() + (changes.isEmpty() ? "" : " · changed: " + String.join(", ", changes));
        }
    }

    /**
     * <b>The outcome of a rupture.</b> Either a universe and the copy that guards it, or every reason there
     * is none.
     */
    public record Ruptured(Universe made, String refused) {
        public boolean ok() {
            return made != null;
        }
    }

    /**
     * <b>The seam to the generator.</b> Two methods, like every seam in this project: what was made, and what
     * this bridge is.
     */
    public interface Port {
        /** What the generator says it made for this universe, or null if it has nothing to say. */
        LandSpec.Reported report(String universeId);

        /** What this bridge is, for the report. */
        String describe();
    }

    /** <b>The bridge that is not there.</b> */
    public static Port absent() {
        return new Port() {
            @Override
            public LandSpec.Reported report(String universeId) {
                return null;
            }

            @Override
            public String describe() {
                return "Terra++ is not connected, so no land can be checked";
            }
        };
    }

    /** <b>One check's outcome.</b> Every disagreement, or the reason the check could not run. */
    public record Checked(String id, List<LandSpec.Difference> diffs, List<String> refused) {
        public boolean clean() {
            return refused.isEmpty() && diffs.isEmpty();
        }

        public String line() {
            if (!refused.isEmpty()) {
                return id + ": not checked - " + String.join("; ", refused);
            }
            if (diffs.isEmpty()) {
                return id + ": the land matches what was asked";
            }
            List<String> out = new ArrayList<>();
            for (LandSpec.Difference d : diffs) {
                out.add(d.line());
            }
            return id + ": " + String.join("; ", out);
        }
    }

    private final Map<String, Universe> universes = new LinkedHashMap<>();

    private Ruptures() {
    }

    public static Ruptures empty() {
        return new Ruptures();
    }

    /**
     * <b>Declare a universe that already exists</b> (the unbroken one, or one read from a save). No copy is
     * needed to say what is already there.
     */
    public Ruptured declare(String id, String parent, LandSpec.Spec land, int year, String why) {
        if (id == null || id.trim().isEmpty()) {
            return new Ruptured(null, "a universe with no name is nowhere");
        }
        for (String k : universes.keySet()) {
            if (k.equalsIgnoreCase(id.trim())) {
                return new Ruptured(null, "there is already a universe called " + k
                        + "; two worlds with one name is a question");
            }
        }
        if (land == null) {
            return new Ruptured(null, "a universe must say what its land is");
        }
        if (parent != null && !parent.trim().isEmpty()) {
            Universe p = find(parent);
            if (p == null) {
                return new Ruptured(null, "there is no universe called " + parent + " for " + id.trim()
                        + " to have broken off from");
            }
        }
        List<String> changes = parent == null || parent.trim().isEmpty()
                ? List.of()
                : LandSpec.changes(find(parent).land(), land);
        Universe u = new Universe(id.trim(), parent == null || parent.trim().isEmpty() ? null
                : find(parent).id(), land, year, why == null ? "" : why.trim(), List.copyOf(changes));
        universes.put(u.id(), u);
        return new Ruptured(u, "");
    }

    /**
     * <b>Break open a new universe.</b> Three refusals before anything happens: no copy, no change, no name
     * twice. The copy is {@link BeforeChange}'s {@code RUPTURE} kind and must already be taken - this class
     * checks it is the right kind and still open, because step 51's rule is that the copy is taken by the code
     * doing the damage, and the damage starts here.
     */
    public Ruptured rupture(String by, String id, String parent, LandSpec.Spec land, int year, String why,
                            BeforeChange.Taken guard) {
        if (guard == null || !guard.ok()) {
            return new Ruptured(null, "a rupture needs its copy taken first (BeforeChange, a rupture is a "
                    + "big change) and there is none");
        }
        if (guard.made().kind() != BeforeChange.ChangeKind.RUPTURE) {
            return new Ruptured(null, "the copy on hand is of " + guard.made().kind().what()
                    + ", and a rupture needs a copy of " + BeforeChange.ChangeKind.RUPTURE.what());
        }
        if (parent == null || parent.trim().isEmpty()) {
            return new Ruptured(null, "a rupture breaks off from somewhere: name the parent universe");
        }
        Universe p = find(parent);
        if (p == null) {
            return new Ruptured(null, "there is no universe called " + parent + " to break off from");
        }
        if (land == null) {
            return new Ruptured(null, "a rupture must say what its land is");
        }
        if (LandSpec.changes(p.land(), land).isEmpty()) {
            return new Ruptured(null, "a rupture that changes nothing is not a rupture: the land of "
                    + p.id() + " is exactly what was asked");
        }
        Ruptured made = declare(id, p.id(), land, year,
                (why == null || why.trim().isEmpty() ? "" : why.trim())
                        + (by == null || by.trim().isEmpty() ? "" : " (by " + by.trim() + ")"));
        return made;
    }

    // ------------------------------------------------------------------ the check

    /**
     * <b>Check it.</b> H3's dependency, done honestly: ask the generator, compare key by key, name every
     * disagreement. A generator with nothing to say is a check that did not run, never a pass.
     */
    public Checked check(String id, Port port) {
        Universe u = find(id);
        if (u == null) {
            return new Checked(id, List.of(), List.of("there is no universe called " + id + " here"));
        }
        Port p = port == null ? absent() : port;
        LandSpec.Reported made = p.report(u.id());
        if (made == null) {
            return new Checked(u.id(), List.of(), List.of("nothing was reported for " + u.id() + " ("
                    + p.describe() + ")"));
        }
        return new Checked(u.id(), LandSpec.diff(u.land(), made), List.of());
    }

    // ------------------------------------------------------------------ queries

    public Universe find(String id) {
        if (id == null) {
            return null;
        }
        for (Map.Entry<String, Universe> e : universes.entrySet()) {
            if (e.getKey().equalsIgnoreCase(id.trim())) {
                return e.getValue();
            }
        }
        return null;
    }

    public List<Universe> all() {
        return List.copyOf(universes.values());
    }

    /** The universes that broke off this one. */
    public List<Universe> childrenOf(String id) {
        List<Universe> out = new ArrayList<>();
        for (Universe u : universes.values()) {
            if (u.parent() != null && u.parent().equalsIgnoreCase(id == null ? "" : id.trim())) {
                out.add(u);
            }
        }
        return out;
    }

    /** The page: every universe, its land in one line, and what its rupture changed. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(universes.size() + (universes.size() == 1 ? " universe" : " universes"));
        for (Universe u : universes.values()) {
            out.add("  " + u.line());
        }
        return out;
    }

    public static String ruleLine() {
        return "A rupture needs its copy taken first, must change the land to count, and is named once. What "
                + "the generator made is checked against what was asked, key by key; with no generator the "
                + "check says it cannot check, and passes nothing.";
    }
}

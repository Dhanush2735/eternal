package com.eternalengine.timeline;

import com.eternalengine.player.Permissions.Decision;
import com.eternalengine.terrabridge.BrokenPhysics;
import com.eternalengine.terrabridge.LandSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>The one call that makes a rupture happen.</b> H3 + H5 together.
 *
 * <h2>Why this class exists</h2>
 *
 * A rupture is five things in a fixed order, and a caller that did them out of order would get it wrong in a
 * new way every time: take the copy, break open the universe, say what its land is, say what its physics is,
 * and check the generator's work. This class is the order, once.
 *
 * <h2>The order, and what each step's failure does</h2>
 *
 * <ol>
 *   <li><b>The copy first</b> ({@link BeforeChange}, step 51). If it cannot be taken - one of the same kind
 *       is already open - <b>nothing happens</b> and the refusal names the open one.</li>
 *   <li><b>The rupture</b> ({@link Ruptures}). If it refuses - no change, no parent, the name taken - the
 *       copy is closed as answered ("it did not happen") and nothing exists.</li>
 *   <li><b>The land</b> ({@link LandSpec}) is the universe's, computed against its parent's, not declared.</li>
 *   <li><b>The physics</b> ({@link BrokenPhysics}) is recorded with the verdict, every fact naming its
 *       mechanism. Wiring a spec to a universe id is the game's storage (one flag map write); this class
 *       returns it rather than hiding it somewhere the save cannot see.</li>
 *   <li><b>The check</b> asks the generator what it made and compares key by key. With no generator the check
 *       says it cannot check: the universe still exists, but its land is <b>unchecked</b> and the verdict
 *       says so in those words.</li>
 * </ol>
 */
public final class RuptureEngine {

    /**
     * <b>What came of the call.</b> The decision, the universe made (or null), the land check, the physics
     * and what is broken about it, and the copy that guarded it.
     */
    public record Verdict(Decision decision, Ruptures.Universe made, Ruptures.Checked landCheck,
                          BrokenPhysics.Spec physics, List<String> physicsChanges, String backupId,
                          BeforeChange copiesAfter) {

        public boolean ok() {
            return decision.allowed();
        }

        public String line() {
            StringBuilder b = new StringBuilder(decision.why());
            if (made != null) {
                b.append(" | ").append(made.line());
            }
            if (landCheck != null) {
                b.append(" | ").append(landCheck.line());
            }
            if (physicsChanges != null && !physicsChanges.isEmpty()) {
                b.append(" | physics: ").append(String.join("; ", physicsChanges));
            }
            return b.toString();
        }
    }

    private RuptureEngine() {
    }

    /**
     * <b>Rupture, end to end.</b> The state the copy guards is passed in ({@code now}) because a copy of
     * nothing is a copy nobody can restore, and this class will not invent a snapshot to fill one.
     */
    public static Verdict rupture(String by, String id, String parent,
                                  LandSpec.Spec land, BrokenPhysics.Spec physics,
                                  int year, String why,
                                  BeforeChange copies, BranchSave.Snapshot now,
                                  Ruptures worlds, Ruptures.Port port) {
        // 1. The copy first: the code doing the damage takes the copy.
        if (copies == null) {
            return new Verdict(Decision.no("a rupture needs its copy taken first (BeforeChange) and no "
                    + "keeper of copies was given"), null, null, physics, List.of(), null, null);
        }
        if (now == null) {
            return new Verdict(Decision.no("a copy of nothing is a copy nobody can restore: no world state "
                    + "was given to guard"), null, null, physics, List.of(), null, copies);
        }
        BeforeChange.Taken guard = copies.take(BeforeChange.ChangeKind.RUPTURE, now,
                why == null || why.trim().isEmpty() ? "a rupture" : why.trim());
        if (!guard.ok()) {
            return new Verdict(Decision.no(guard.refused().why()), null, null, physics, List.of(), null,
                    guard.list());
        }

        // 2. The rupture itself.
        Ruptures.Ruptured made = worlds.rupture(by, id, parent, land, year, why, guard);
        if (!made.ok()) {
            // The change did not happen. The copy is answered and closed so the next attempt is not blocked
            // by a copy guarding something that never came to be.
            BeforeChange after = guard.list().complete(guard.made().id());
            return new Verdict(Decision.no(made.refused()), null, null, physics, List.of(),
                    guard.made().id(), after);
        }
        BeforeChange after = guard.list().complete(guard.made().id());

        // 3. The physics, in words that name who enforces each fact.
        List<String> physicsChanges = physics == null
                ? List.of("the physics of the unbroken world")
                : BrokenPhysics.changesFromEarth(physics);

        // 4. Check it: the generator's work against the spec, or the words that say it could not be checked.
        Ruptures.Checked check = worlds.check(made.made().id(), port == null ? Ruptures.absent() : port);
        String checkWords = check.clean()
                ? "and the generator's land matches what was asked"
                : (check.diffs().isEmpty()
                        ? "and the land could not be checked: " + String.join("; ", check.refused())
                        : "and the generator's land disagrees: " + check.line());

        return new Verdict(Decision.yes(made.made().id() + " broke off from " + parent + " in " + year
                + ", guarded by " + guard.made().id() + "; " + checkWords),
                made.made(), check, physics, physicsChanges, guard.made().id(), after);
    }

    /** The page: several ruptures read one line each. */
    public static List<String> page(List<Verdict> verdicts) {
        List<String> out = new ArrayList<>();
        for (Verdict v : verdicts == null ? List.<Verdict>of() : verdicts) {
            if (v != null) {
                out.add((v.ok() ? "" : "REFUSED ") + v.line());
            }
        }
        return out;
    }

    public static String ruleLine() {
        return "A rupture is five things in order: the copy first, the universe, its land, its physics, and "
                + "the generator's work checked. A refused rupture leaves nothing behind but a closed copy; "
                + "an unchecked one says it was unchecked.";
    }
}

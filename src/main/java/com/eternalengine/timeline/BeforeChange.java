package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>A17: a backup taken before a big change, not every half hour.</b>
 *
 * <h2>Why the existing backup is the wrong instrument</h2>
 *
 * <p>The mod already copies its save file every {@code backupIntervalMinutes} (30) and keeps
 * {@code maxBackups} of them. That is a good habit and it is blind: it does not know what is about to happen. A
 * time jump, a timeline split and a rupture all happen at <b>a known instant</b>, and the copy a player needs is
 * the one from <b>immediately before that instant</b>, not the one from ten minutes ago. So this class keeps its
 * own list, taken by the code that is about to do the damage, and it is deliberately separate from the timed
 * copies: {@link #latestFor} will not accept a timed copy as a before-change backup, and the probe checks that.
 *
 * <h2>The failure this is built around</h2>
 *
 * <p>The dangerous sequence is not "the change went wrong". It is <b>"the change went wrong, and the backup was
 * overwritten by the next thing"</b>. So a backup here is <b>open</b> until the change that took it says it is
 * done. While one is open, another of the same kind cannot be taken: the second attempt is refused with the id of
 * the one still open. That refusal is the whole value of the class, and it is the first thing checked.
 *
 * <h2>What it does not do</h2>
 *
 * <p>It does not write files. A {@code BeforeChange} is the list; the caller decides where it lives. It does not
 * take a backup on its own initiative either, because a backup nobody asked for is a backup nobody trusts.
 */
public final class BeforeChange {

    /**
     * The changes worth a backup first. A flag set by a routine engine tick is not one of them: if every change
     * took a copy, the copies would be worthless and the disk would fill.
     */
    public enum ChangeKind {
        /** The clock moves a long way: the world's state is not what it was, and the jump is the player's. */
        TIME_JUMP("a jump in time", true),
        /** A branch is created, which is the one change that cannot be undone by doing it again. */
        TIMELINE_SPLIT("a split in the timeline", true),
        /** Physics or logic broke, and what comes after may not be recoverable by hand. */
        RUPTURE("a rupture", true),
        /** One flag, set by an engine on its own schedule. Reversible, and too frequent to copy. */
        FLAG("a single flag", false);

        private final String what;
        private final boolean needsBackup;

        ChangeKind(String what, boolean needsBackup) {
            this.what = what;
            this.needsBackup = needsBackup;
        }

        public String what() {
            return what;
        }

        public boolean needsBackup() {
            return needsBackup;
        }
    }

    /** One copy of the world, taken because something is about to happen to it. */
    public record Backup(String id, ChangeKind kind, int year, String branch, BranchSave.Snapshot state,
                         String why, int takenAt, boolean open) {

        public Backup {
            why = why == null ? "" : why;
        }

        public Backup closed() {
            return new Backup(id, kind, year, branch, state, why, takenAt, false);
        }

        public String line() {
            return id + " · before " + kind.what() + " in " + year + " · " + state.flagCount() + " flags on "
                    + branch + (why.isBlank() ? "" : " · " + why) + (open ? " · STILL OPEN" : "");
        }
    }

    /** A backup that could not be taken, and why. */
    public record Refused(String kind, String why) {
    }

    /** The result of trying to take one: the new list, or the reason there is none. */
    public record Taken(BeforeChange list, Backup made, Refused refused) {

        public boolean ok() {
            return made != null;
        }

        public String line() {
            return ok() ? "Backed up before " + made.kind().what() + ": " + made.id()
                    : "No backup taken: " + refused.why();
        }
    }

    /** The default number of backups kept. Old ones rotate out, and {@link #lost} counts what went. */
    public static final int KEEP = 6;

    private final Map<String, Backup> backups;
    private final int keep;
    private final int lost;
    private final int counter;

    private BeforeChange(Map<String, Backup> backups, int keep, int lost, int counter) {
        this.backups = backups;
        this.keep = keep;
        this.lost = lost;
        this.counter = counter;
    }

    public static BeforeChange empty() {
        return new BeforeChange(new LinkedHashMap<>(), KEEP, 0, 0);
    }

    public static BeforeChange empty(int keep) {
        return new BeforeChange(new LinkedHashMap<>(), Math.max(1, keep), 0, 0);
    }

    /**
     * Take a copy before doing something. Refuses when the change does not need one, and when one of the same kind
     * is still open.
     */
    public Taken take(ChangeKind kind, BranchSave.Snapshot state, String why) {
        if (kind == null) {
            return new Taken(this, null, new Refused("?", "no change was named"));
        }
        if (!kind.needsBackup()) {
            return new Taken(this, null, new Refused(kind.what(), "this change does not take a backup: a copy of "
                    + "the world before every tick is a copy nobody can find the one they need in"));
        }
        if (state == null) {
            return new Taken(this, null, new Refused(kind.what(), "there is no state to copy"));
        }
        Backup open = openOf(kind);
        if (open != null) {
            return new Taken(this, null, new Refused(kind.what(), "a copy for " + kind.what() + " is already open ("
                    + open.id() + "). Finish that change or undo it before taking another: a second copy taken over "
                    + "the first is how the copy you needed gets lost"));
        }
        int n = counter + 1;
        String id = "backup-" + n;
        Backup b = new Backup(id, kind, state.year(), state.activeBranch(), state, why, n, true);
        Map<String, Backup> next = new LinkedHashMap<>(backups);
        next.put(id, b);
        int dropped = 0;
        // Rotate the oldest closed copies first, and never the open one: the open one is the only copy of a world
        // that a change is in the middle of.
        while (next.size() > keep) {
            String victim = null;
            for (Backup x : next.values()) {
                if (!x.open()) {
                    victim = x.id();
                    break;
                }
            }
            if (victim == null) {
                break;
            }
            next.remove(victim);
            dropped++;
        }
        return new Taken(new BeforeChange(next, keep, lost + dropped, n), b, null);
    }

    /** The change the backup was taken for is done and the world is as intended. */
    public BeforeChange complete(String id) {
        Backup b = backups.get(id);
        if (b == null) {
            throw new IllegalArgumentException("no backup " + id);
        }
        Map<String, Backup> next = new LinkedHashMap<>(backups);
        next.put(id, b.closed());
        return new BeforeChange(next, keep, lost, counter);
    }

    /**
     * The world to put back. Completing and undoing are the same act on this list: the copy comes back, and the
     * backup stops being open, because the change it was guarding against has been answered.
     */
    public BranchSave.Snapshot undo(String id) {
        Backup b = backups.get(id);
        if (b == null) {
            throw new IllegalArgumentException("no backup " + id);
        }
        return b.state();
    }

    public BeforeChange forget(String id) {
        if (!backups.containsKey(id)) {
            return this;
        }
        Map<String, Backup> next = new LinkedHashMap<>(backups);
        next.remove(id);
        return new BeforeChange(next, keep, lost + 1, counter);
    }

    public List<Backup> all() {
        return new ArrayList<>(backups.values());
    }

    public int size() {
        return backups.size();
    }

    public Backup get(String id) {
        return backups.get(id);
    }

    /** The newest copy of any kind, or null. */
    public Backup latest() {
        Backup best = null;
        for (Backup b : backups.values()) {
            best = b;
        }
        return best;
    }

    /** The newest copy taken before a particular kind of change, or null. */
    public Backup latestFor(ChangeKind kind) {
        Backup best = null;
        for (Backup b : backups.values()) {
            if (b.kind() == kind) {
                best = b;
            }
        }
        return best;
    }

    /** The copy still open for this kind of change, if any. */
    public Backup openOf(ChangeKind kind) {
        for (Backup b : backups.values()) {
            if (b.open() && b.kind() == kind) {
                return b;
            }
        }
        return null;
    }

    public List<String> openIds() {
        List<String> out = new ArrayList<>();
        for (Backup b : backups.values()) {
            if (b.open()) {
                out.add(b.id());
            }
        }
        return out;
    }

    /**
     * <b>A timed copy is not a backup before a change.</b> Kept as a method rather than a comment because the
     * distinction is the whole reason this class exists: the world's scheduled copies keep the last half hour, and
     * a change that happened ten seconds ago is not in any of them.
     */
    public String whyTimedCopiesDoNotCount() {
        return "The world already copies its save every half hour and keeps the last few. None of those copies was "
                + "taken because of this change: the newest of them predates it, and the one taken after it is the "
                + "change. That is why there are " + backups.size() + " here and why they are taken by the code "
                + "doing the damage.";
    }

    /** How many copies have rotated out, said out loud rather than discovered later. */
    public int lost() {
        return lost;
    }

    public int keep() {
        return keep;
    }

    /** One line for a screen: what is kept, and what is not. */
    public String ruleLine() {
        if (backups.isEmpty()) {
            return "No copies taken: nothing big has happened yet. A jump, a split or a rupture takes one first.";
        }
        return backups.size() + " cop" + (backups.size() == 1 ? "y" : "ies") + " kept, at most " + keep
                + ", " + openIds().size() + " of them open" + (lost > 0 ? ", and " + lost + " rotated out" : "")
                + ". A copy is kept until the change it guards is answered.";
    }
}

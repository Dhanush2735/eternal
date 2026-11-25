package com.eternalengine.timeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * <b>G7: the timeline tree, and travel between timelines.</b>
 *
 * <h2>What was wrong</h2>
 *
 * <p>Branches were a flat map with a parent id on each one. Nothing built the other direction: no children, no
 * depth, no ancestry, no cycle check. And {@code setActiveTimelineBranch} was called from <b>exactly one place</b>
 * in the whole mod, at the moment a fork is made, so a branch a player left could not be re-entered at all. G7 is
 * "the tree in the book, and travel between timelines", and both halves of that sentence had nothing behind them.
 *
 * <h2>The rule that makes travel honest</h2>
 *
 * <p><b>You cannot stand on a branch before it split.</b> A branch is a difference from its parent, and before the
 * year it diverged, that difference does not exist yet: the world in 1850 on a branch that forks in 1857 is the
 * world of the branch it came from. So a trip is not "restore the target and go", it is:
 *
 * <ol>
 *   <li>where does the target stand in {@code year}? If it had not split yet, the honest answer is the deepest
 *       ancestor that had, and the trip says so;</li>
 *   <li>if the target stands in that year, restore its save (through {@link BranchSave}, the branch's own chain
 *       of differences) and make it the active branch;</li>
 *   <li>if it has no save, refuse: a branch with no save cannot be entered, however well it is drawn in the
 *       book.</li>
 * </ol>
 *
 * <p>Step 50's rule applies to the tree as much as to a story: <b>nothing here invents a branch</b>, and a tree
 * that cannot be reached is refused rather than drawn as if it could.
 */
public final class BranchTree {

    /**
     * One branch as the book needs it: who it is, who it came from, when it split, and the one line that says why.
     *
     * @param divergenceYear the year it split from its parent; for the root, the year the world begins
     */
    public record Node(String id, String parentId, int divergenceYear, String cause, String log,
                       List<String> suppressedPins) {

        public Node {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("a branch needs an id");
            }
            cause = cause == null ? "" : cause;
            log = log == null ? "" : log;
            suppressedPins = List.copyOf(suppressedPins == null ? List.of() : suppressedPins);
        }

        public boolean isRoot() {
            return parentId == null || parentId.isBlank();
        }

        /** The line the book shows. The year is there because a tree without it says nothing useful. */
        public String line() {
            String head = id + " · " + divergenceYear + (log.isBlank() ? "" : " · " + log);
            return suppressedPins.isEmpty() ? head : head + " · " + suppressedPins.size() + " pins stopped";
        }
    }

    /** What a trip would do, before anything is restored. */
    public record Trip(String from, int year, String asked, String standOn, boolean possible, String why,
                       List<String> path) {

        public Trip {
            path = List.copyOf(path == null ? List.of() : path);
        }

        /** True when the player asked for one branch and the world will put them on another. */
        public boolean redirected() {
            return possible && standOn != null && !standOn.equals(asked);
        }

        public String line() {
            if (!possible) {
                return "You cannot be there: " + why;
            }
            if (redirected()) {
                return "In " + year + " you stand on " + standOn + " and not on " + asked + ": " + why;
            }
            return "You stand on " + standOn + " in " + year + ", by way of " + String.join(" -> ", path) + ".";
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();

    private BranchTree() {
    }

    public static BranchTree of(List<Node> list) {
        BranchTree t = new BranchTree();
        for (Node n : list == null ? List.<Node>of() : list) {
            if (n == null) {
                continue;
            }
            if (t.nodes.containsKey(n.id())) {
                throw new IllegalArgumentException("two branches with the same id: " + n.id());
            }
            t.nodes.put(n.id(), n);
        }
        return t;
    }

    /** The tree as it is stored in the save: every delta already carries who it is and who it came from. */
    public static BranchTree from(BranchSave save) {
        List<Node> list = new ArrayList<>();
        for (String id : save.ids()) {
            BranchSave.Delta d = save.get(id);
            list.add(new Node(d.branchId(), d.parentId(), d.divergenceYear(), "", d.log(), d.suppressedPins()));
        }
        return of(list);
    }

    public int size() {
        return nodes.size();
    }

    public Node get(String id) {
        return nodes.get(id);
    }

    public List<String> ids() {
        return new ArrayList<>(nodes.keySet());
    }

    /** Branches whose parent is missing from the tree. A tree with roots missing is reported, never completed. */
    public List<String> orphans() {
        List<String> out = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (!n.isRoot() && !nodes.containsKey(n.parentId())) {
                out.add(n.id() + " (its parent " + n.parentId() + " is not here)");
            }
        }
        return out;
    }

    public List<String> roots() {
        List<String> out = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (n.isRoot()) {
                out.add(n.id());
            }
        }
        return out;
    }

    public List<String> childrenOf(String parentId) {
        List<String> out = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (Objects.equals(n.parentId(), parentId)) {
                out.add(n.id());
            }
        }
        return out;
    }

    /**
     * The path from the root down to a branch, root first.
     *
     * <p>A missing parent or a loop yields the path that could be built and refuses the rest, because a path with
     * a hole in it is a path that would restore the wrong world.
     */
    public List<String> path(String id) {
        List<String> back = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String at = id;
        while (at != null) {
            Node n = nodes.get(at);
            if (n == null || !seen.add(at)) {
                break;
            }
            back.add(at);
            at = n.isRoot() ? null : n.parentId();
        }
        java.util.Collections.reverse(back);
        return back;
    }

    /** How many splits from the root. The root is 0, a branch off the root is 1, and a hole is -1. */
    public int depth(String id) {
        if (broken(id)) {
            return -1;
        }
        return path(id).size() - 1;
    }

    /** True where the tree cannot be walked to this branch from a root: a hole or a loop. */
    public boolean broken(String id) {
        List<String> p = path(id);
        if (p.isEmpty()) {
            return true;
        }
        Node first = nodes.get(p.get(0));
        return first == null || !first.isRoot() || !p.get(p.size() - 1).equals(id);
    }

    /**
     * The branch a player is actually on in a year: the deepest ancestor of {@code asked} that had already split
     * by then, or {@code asked} itself when it had.
     */
    public String branchAt(String asked, int year) {
        List<String> p = path(asked);
        String best = null;
        for (String id : p) {
            Node n = nodes.get(id);
            if (n == null) {
                continue;
            }
            if (n.isRoot() || n.divergenceYear() <= year) {
                best = id;
            }
        }
        return best;
    }

    /**
     * What a trip to a branch in a year would do, including the case where the branch had not split yet.
     *
     * @param save the world's saves, because a branch with no save cannot be entered
     */
    public Trip plan(BranchSave save, String from, String asked, int year) {
        if (!nodes.containsKey(asked)) {
            return new Trip(from, year, asked, null, false, "there is no branch " + asked + " in this world",
                    List.of());
        }
        if (broken(asked)) {
            return new Trip(from, year, asked, null, false,
                    "the tree does not connect " + asked + " to a root, so its path cannot be walked", List.of());
        }
        List<String> p = path(asked);
        String stand = branchAt(asked, year);
        if (stand == null) {
            return new Trip(from, year, asked, null, false, "no branch in that path exists in " + year, p);
        }
        if (save == null || !save.has(stand)) {
            return new Trip(from, year, asked, stand, false,
                    "there is no save for " + stand + ", so the world cannot be put back into it", p);
        }
        Node n = nodes.get(stand);
        String why = stand.equals(asked)
                ? "it split in " + n.divergenceYear() + ", and " + year + " is after that"
                : asked + " splits in " + nodes.get(asked).divergenceYear() + ", which is after " + year
                        + ", so before then you are on " + stand;
        return new Trip(from, year, asked, stand, true, why, p);
    }

    /**
     * Travel: the state to show, and the branch to make active. Returns the snapshot to load, or null when the
     * trip is impossible, with the reason in {@link #explain}.
     *
     * <p>Nothing here touches the world: it hands back a {@link BranchSave.Snapshot}, and the caller sets the
     * active branch. That is deliberate, because the one thing worse than a branch you cannot enter is a branch
     * you enter halfway.
     */
    public BranchSave.Snapshot travel(BranchSave save, String from, String asked, int year, String[] explain) {
        Trip t = plan(save, from, asked, year);
        if (explain != null && explain.length > 0) {
            explain[0] = t.line();
        }
        if (!t.possible()) {
            return null;
        }
        BranchSave.Snapshot restored = save.restore(t.standOn());
        if (restored == null) {
            if (explain != null && explain.length > 0) {
                explain[0] = "the save for " + t.standOn() + " cannot be restored: " + String.join("; ",
                        save.chain(t.standOn()).refused());
            }
            return null;
        }
        // The world's own clock is not the branch's: a branch records when it split, and the player is somewhere
        // else in time. So the year of the trip is the year asked for, and the branch is the one that stands.
        return new BranchSave.Snapshot(restored.flags(), year, t.standOn());
    }

    /** Where the player is, as a book line. */
    public String position(String activeId) {
        if (activeId == null || !nodes.containsKey(activeId)) {
            return "You are on no branch this book knows.";
        }
        List<String> p = path(activeId);
        Node n = nodes.get(activeId);
        String where = n.isRoot()
                ? "You are on " + activeId + ", the history that happened."
                : "You are on " + activeId + ", which split from " + n.parentId() + " in " + n.divergenceYear()
                        + (n.log().isBlank() ? "" : " because " + n.log()) + ".";
        return where + " That is " + Math.max(0, p.size() - 1) + " split" + (p.size() == 2 ? "" : "s")
                + " from the real history.";
    }

    /**
     * The whole tree, one branch a line, indented by depth. Roots first, children in the order they were saved,
     * and a branch whose parent is missing is shown at the top with the reason, because hiding it would make a
     * broken save look like a short one.
     */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        Set<String> drawn = new TreeSet<>();
        for (String root : roots()) {
            draw(root, 0, out, drawn);
        }
        // Anything the walk from the roots did not reach is drawn anyway, at the end, with the reason. A tree
        // that loops has no root at all, and the first version of this method drew nothing for it: a broken save
        // that looks like an empty one is worse than a broken save that looks broken.
        for (Node n : nodes.values()) {
            if (drawn.contains(n.id())) {
                continue;
            }
            String why = n.isRoot() || nodes.containsKey(n.parentId())
                    ? "this branch's ancestry loops and never reaches a root"
                    : "its parent " + n.parentId() + " is not here";
            out.add("? " + n.line() + " · " + why);
        }
        return out;
    }

    private void draw(String id, int depth, List<String> out, Set<String> drawn) {
        if (!drawn.add(id)) {
            out.add("! " + id + " (this branch appears twice in its own ancestry)");
            return;
        }
        Node n = nodes.get(id);
        if (n == null) {
            return;
        }
        out.add("  ".repeat(Math.max(0, depth)) + (depth == 0 ? "" : "- ") + n.line());
        for (String c : childrenOf(id)) {
            draw(c, depth + 1, out, drawn);
        }
    }

    /** The tree as pages, for a book that shows a fixed number of lines at a time. No line is dropped. */
    public List<List<String>> pages(int linesPerPage) {
        List<String> all = lines();
        List<List<String>> out = new ArrayList<>();
        int per = Math.max(1, linesPerPage);
        for (int i = 0; i < all.size(); i += per) {
            out.add(List.copyOf(all.subList(i, Math.min(all.size(), i + per))));
        }
        return out;
    }

    /** One line, for a screen, saying what the tree is and what it will not do. */
    public String ruleLine() {
        if (nodes.isEmpty()) {
            return "No branches: this world is on the history that happened.";
        }
        int deepest = 0;
        for (String id : nodes.keySet()) {
            deepest = Math.max(deepest, Math.max(0, path(id).size() - 1));
        }
        return nodes.size() + " branch" + (nodes.size() == 1 ? "" : "es") + ", " + (nodes.size() - roots().size())
                + " of them forks, " + deepest + " deep. A branch can be entered only from a year after it split, "
                + "and only if the world has a save of it.";
    }
}

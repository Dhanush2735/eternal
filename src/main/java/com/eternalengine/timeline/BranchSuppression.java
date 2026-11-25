package com.eternalengine.timeline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Branch-scoped event suppression, with ancestor inheritance.
 *
 * <p>Suppression answers one question: <em>in this branch, is this pin gone?</em>
 *
 * <p><b>Why inheritance matters.</b> Forking is cumulative. A player who stops 26/11 and later
 * stops Pearl Harbor must keep <em>both</em> suppressions. Without walking the parent chain, the
 * second fork would carry only Pearl Harbor and 26/11 would silently reappear on the map.
 *
 * <p>Branches form a tree rooted at {@code main}. {@code main} is the empty suppression set:
 * prime history suppresses nothing.
 *
 * <p>Thread-safe. Populated on the server thread when a divergence is created; read from the
 * overlay path on every presence query.
 */
public final class BranchSuppression {

    /** Guard against a malformed parent chain turning a lookup into an infinite walk. */
    private static final int MAX_ANCESTRY_DEPTH = 64;

    private final ConcurrentMap<String, Entry> byBranch = new ConcurrentHashMap<>();

    private static final class Entry {
        private final String parentId;
        private final Set<String> suppressed = ConcurrentHashMap.newKeySet();

        private Entry(String parentId) {
            this.parentId = parentId;
        }
    }

    public BranchSuppression() {
        register("main", null);
    }

    /** Back to prime only: every branch and every suppression gone (a new world, or /ee reset). */
    public void clear() {
        byBranch.clear();
        register("main", null);
    }

    /**
     * Declare a branch (and its parent) so suppression lookups can walk the chain.
     * Safe to call repeatedly for the same branch.
     */
    public void register(String branchId, String parentId) {
        if (branchId == null || branchId.isBlank()) {
            return;
        }
        byBranch.computeIfAbsent(branchId, id -> new Entry(parentId));
    }

    /** Record that {@code eventId} does not happen in {@code branchId}. */
    public void suppress(String branchId, String eventId) {
        if (branchId == null || branchId.isBlank() || eventId == null || eventId.isBlank()) {
            return;
        }
        Entry e = byBranch.computeIfAbsent(branchId, id -> new Entry("main".equals(id) ? null : "main"));
        e.suppressed.add(eventId);
    }

    /** Suppress many pins at once — used when a cluster of siblings goes down together. */
    public void suppressAll(String branchId, Iterable<String> eventIds) {
        if (eventIds == null) {
            return;
        }
        for (String id : eventIds) {
            suppress(branchId, id);
        }
    }

    /** Suppressions recorded <em>directly</em> on this branch, ignoring ancestors. */
    public Set<String> suppressedHere(String branchId) {
        Entry e = byBranch.get(branchId);
        return e == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(e.suppressed));
    }

    /**
     * Every pin that is gone in this branch — this branch's set plus every ancestor's.
     *
     * <p>This is the set the event overlay, the chain pump, and the chronicle must all consult.
     */
    public Set<String> effectiveSuppressed(String branchId) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : ancestorChain(branchId)) {
            Entry e = byBranch.get(id);
            if (e != null) {
                out.addAll(e.suppressed);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** True if this pin is gone in this branch, or in any branch it descends from. */
    public boolean isSuppressed(String branchId, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        for (String id : ancestorChain(branchId)) {
            Entry e = byBranch.get(id);
            if (e != null && e.suppressed.contains(eventId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This branch first in the chain, then its parent, and so on up to {@code main}.
     * Returns an empty list for an unknown branch rather than throwing — a missing branch
     * must not break a presence query.
     */
    public List<String> ancestorChain(String branchId) {
        List<String> chain = new ArrayList<>();
        String current = (branchId == null || branchId.isBlank()) ? "main" : branchId;
        int guard = 0;
        while (current != null && guard++ < MAX_ANCESTRY_DEPTH) {
            chain.add(current);
            Entry e = byBranch.get(current);
            if (e == null) {
                break;
            }
            if (e.parentId == null || e.parentId.equals(current)) {
                break;
            }
            current = e.parentId;
        }
        return chain;
    }

    /** Number of branches in this branch's ancestry, including itself. */
    public int depth(String branchId) {
        return ancestorChain(branchId).size();
    }

    public boolean hasBranch(String branchId) {
        return byBranch.containsKey(branchId);
    }

    public Set<String> branchIds() {
        Deque<String> order = new ArrayDeque<>(byBranch.keySet());
        Set<String> out = new LinkedHashSet<>(order);
        return Collections.unmodifiableSet(out);
    }

    /** Total suppressions recorded across every branch (not de-duplicated). */
    public int totalSuppressed() {
        int n = 0;
        for (Entry e : byBranch.values()) {
            n += e.suppressed.size();
        }
        return n;
    }
}

package com.eternalengine.brain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded in-memory audit log for brain proposal validation.
 */
public final class BrainAuditLog {

    private final int maxEntries;
    private final Deque<String> entries = new ArrayDeque<>();

    public BrainAuditLog() {
        this(200);
    }

    public BrainAuditLog(int maxEntries) {
        this.maxEntries = Math.max(10, Math.min(5000, maxEntries));
    }

    public synchronized void record(LlmProposal proposal, LlmValidationResult result) {
        String line = System.currentTimeMillis()
            + " proposal=" + (proposal == null ? "null" : proposal.proposalId())
            + " action=" + (proposal == null ? "null" : proposal.actionName())
            + " accepted=" + (result != null && result.accepted())
            + " reason=" + (result == null ? "no result" : result.reason());
        entries.addLast(line);
        while (entries.size() > maxEntries) {
            entries.removeFirst();
        }
    }

    public synchronized List<String> recent(int count) {
        List<String> all = new ArrayList<>(entries);
        int from = Math.max(0, all.size() - Math.max(0, count));
        return List.copyOf(all.subList(from, all.size()));
    }
}

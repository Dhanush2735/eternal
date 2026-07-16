package com.eternalengine.brain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Structured proposal from an external brain or bot process.
 *
 * This is data only. It cannot mutate the world.
 */
public final class LlmProposal {

    private final String proposalId;
    private final String actorId;
    private final String actionName;
    private final LlmAction action;
    private final Map<String, String> target;
    private final String reason;
    private final double confidence;
    private final long createdAtMs;

    public LlmProposal(String proposalId, String actorId, String actionName,
                       Map<String, String> target, String reason, double confidence) {
        this.proposalId = proposalId == null || proposalId.isBlank()
            ? "proposal_" + System.currentTimeMillis()
            : proposalId;
        this.actorId = actorId == null ? "" : actorId;
        this.actionName = actionName == null ? "UNKNOWN" : actionName.trim().toUpperCase(Locale.ROOT);
        this.action = parseAction(this.actionName);
        this.target = Collections.unmodifiableMap(new HashMap<>(target == null ? Map.of() : target));
        this.reason = reason == null ? "" : reason;
        this.confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        this.createdAtMs = System.currentTimeMillis();
    }

    private LlmAction parseAction(String name) {
        try {
            return LlmAction.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return LlmAction.UNKNOWN;
        }
    }

    public String proposalId() { return proposalId; }
    public String actorId() { return actorId; }
    public String actionName() { return actionName; }
    public LlmAction action() { return action; }
    public Map<String, String> target() { return target; }
    public String reason() { return reason; }
    public double confidence() { return confidence; }
    public long createdAtMs() { return createdAtMs; }
}

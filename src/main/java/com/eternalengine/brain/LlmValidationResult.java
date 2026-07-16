package com.eternalengine.brain;

/**
 * Result of server-side proposal validation.
 */
public final class LlmValidationResult {

    private final boolean accepted;
    private final String reason;
    private final LlmAction normalizedAction;

    private LlmValidationResult(boolean accepted, String reason, LlmAction normalizedAction) {
        this.accepted = accepted;
        this.reason = reason == null ? "" : reason;
        this.normalizedAction = normalizedAction == null ? LlmAction.UNKNOWN : normalizedAction;
    }

    public static LlmValidationResult accepted(LlmAction action, String reason) {
        return new LlmValidationResult(true, reason, action);
    }

    public static LlmValidationResult rejected(String reason) {
        return new LlmValidationResult(false, reason, LlmAction.UNKNOWN);
    }

    public boolean accepted() { return accepted; }
    public String reason() { return reason; }
    public LlmAction normalizedAction() { return normalizedAction; }
}

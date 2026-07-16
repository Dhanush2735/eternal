package com.eternalengine.vip;

/**
 * Result of validating/executing VIP takeover.
 */
public final class VipTakeoverResult {

    private final boolean success;
    private final String reason;
    private final String appliedReputationAction;

    private VipTakeoverResult(boolean success, String reason, String appliedReputationAction) {
        this.success = success;
        this.reason = reason == null ? "" : reason;
        this.appliedReputationAction = appliedReputationAction;
    }

    public static VipTakeoverResult success(String reason, String appliedReputationAction) {
        return new VipTakeoverResult(true, reason, appliedReputationAction);
    }

    public static VipTakeoverResult failure(String reason) {
        return new VipTakeoverResult(false, reason, null);
    }

    public boolean success() { return success; }
    public String reason() { return reason; }
    public String appliedReputationAction() { return appliedReputationAction; }
}

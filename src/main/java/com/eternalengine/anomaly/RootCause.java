package com.eternalengine.anomaly;

/**
 * A likely method/code path associated with an anomaly.
 */
public record RootCause(String method, String reason, double confidence) {
    public RootCause {
        if (method == null || method.isBlank()) {
            method = "unknown";
        }
        if (reason == null || reason.isBlank()) {
            reason = "No reason recorded";
        }
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
    }
}

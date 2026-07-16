package com.eternalengine.anomaly;

import com.eternalengine.telemetry.WorldDebugSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable anomaly report containing expected/actual data, evidence, and likely code causes.
 */
public final class AnomalyRecord {

    private final AnomalyType type;
    private final WorldDebugSnapshot snapshot;
    private final String expected;
    private final String actual;
    private final double confidence;
    private final List<String> evidence;
    private final List<RootCause> likelyCauses;

    public AnomalyRecord(AnomalyType type,
                         WorldDebugSnapshot snapshot,
                         String expected,
                         String actual,
                         double confidence,
                         List<String> evidence,
                         List<RootCause> likelyCauses) {
        this.type = type == null ? AnomalyType.INVALID_TERRA_PROBE : type;
        this.snapshot = snapshot;
        this.expected = expected == null ? "unknown" : expected;
        this.actual = actual == null ? "unknown" : actual;
        this.confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence == null ? List.of() : evidence));
        this.likelyCauses = Collections.unmodifiableList(new ArrayList<>(likelyCauses == null ? List.of() : likelyCauses));
    }

    public String toSummaryString() {
        String location = snapshot == null
            ? "unknown location"
            : String.format(Locale.ROOT, "x=%d y=%d z=%d lat=%.6f lon=%.6f",
                snapshot.blockX(), snapshot.blockY(), snapshot.blockZ(), snapshot.latitude(), snapshot.longitude());
        String firstCause = likelyCauses.isEmpty() ? "unknown" : likelyCauses.get(0).method();
        return String.format(Locale.ROOT,
            "%s confidence=%.2f expected=%s actual=%s at %s likely=%s",
            type, confidence, expected, actual, location, firstCause);
    }

    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toSummaryString());
        if (!evidence.isEmpty()) {
            sb.append("\n  evidence:");
            for (String item : evidence) {
                sb.append("\n    - ").append(item);
            }
        }
        if (!likelyCauses.isEmpty()) {
            sb.append("\n  likely code paths:");
            for (RootCause cause : likelyCauses) {
                sb.append(String.format(Locale.ROOT, "\n    - %s (%.2f): %s",
                    cause.method(), cause.confidence(), cause.reason()));
            }
        }
        return sb.toString();
    }

    public AnomalyType type() { return type; }
    public WorldDebugSnapshot snapshot() { return snapshot; }
    public String expected() { return expected; }
    public String actual() { return actual; }
    public double confidence() { return confidence; }
    public List<String> evidence() { return evidence; }
    public List<RootCause> likelyCauses() { return likelyCauses; }
}

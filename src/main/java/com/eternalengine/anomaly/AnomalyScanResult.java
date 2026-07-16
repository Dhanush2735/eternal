package com.eternalengine.anomaly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Result of running anomaly detection over one or more telemetry snapshots.
 */
public final class AnomalyScanResult {

    private final List<AnomalyRecord> anomalies;

    public AnomalyScanResult(List<AnomalyRecord> anomalies) {
        List<AnomalyRecord> copy = new ArrayList<>(anomalies == null ? List.of() : anomalies);
        copy.sort(Comparator.comparingDouble(AnomalyRecord::confidence).reversed());
        this.anomalies = Collections.unmodifiableList(copy);
    }

    public boolean hasAnomalies() {
        return !anomalies.isEmpty();
    }

    public List<AnomalyRecord> anomalies() {
        return anomalies;
    }

    public Map<AnomalyType, Integer> countsByType() {
        Map<AnomalyType, Integer> counts = new EnumMap<>(AnomalyType.class);
        for (AnomalyRecord record : anomalies) {
            counts.merge(record.type(), 1, Integer::sum);
        }
        return counts;
    }

    public String toSummaryString() {
        if (anomalies.isEmpty()) {
            return "No anomalies detected.";
        }
        return String.format(Locale.ROOT, "Detected %d anomalies: %s", anomalies.size(), countsByType());
    }

    public List<String> topLines(int maxLines) {
        int limit = Math.max(0, Math.min(maxLines, anomalies.size()));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            lines.add(anomalies.get(i).toSummaryString());
        }
        return lines;
    }
}

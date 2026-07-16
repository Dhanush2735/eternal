package com.eternalengine.disease;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable outbreak state.
 */
public final class DiseaseOutbreak {
    private final String outbreakId;
    private final String diseaseId;
    private final String originRegionId;
    private final int startYear;
    private final Set<String> affectedRegionIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Double> regionSeverity = new ConcurrentHashMap<>();
    private long totalDeaths;
    private boolean ended;
    private int endYear = -1;

    public DiseaseOutbreak(String outbreakId, String diseaseId, String originRegionId, int startYear) {
        this.outbreakId = outbreakId == null || outbreakId.isBlank() ? "outbreak_unknown" : outbreakId;
        this.diseaseId = diseaseId == null || diseaseId.isBlank() ? "disease_unknown" : diseaseId;
        this.originRegionId = originRegionId == null || originRegionId.isBlank() ? "global" : originRegionId;
        this.startYear = startYear;
        this.affectedRegionIds.add(this.originRegionId);
        this.regionSeverity.put(this.originRegionId, 0.25d);
    }

    public void affectRegion(String regionId, double severity) {
        if (regionId == null || regionId.isBlank()) {
            return;
        }
        affectedRegionIds.add(regionId);
        regionSeverity.put(regionId, Math.max(0.0d, Math.min(1.0d, severity)));
    }

    public void addDeaths(long deaths) {
        totalDeaths = Math.max(0L, totalDeaths + Math.max(0L, deaths));
    }

    public void end(int year) {
        ended = true;
        endYear = year;
    }

    public String outbreakId() { return outbreakId; }
    public String diseaseId() { return diseaseId; }
    public String originRegionId() { return originRegionId; }
    public int startYear() { return startYear; }
    public Set<String> affectedRegionIds() { return Collections.unmodifiableSet(affectedRegionIds); }
    public Map<String, Double> regionSeverity() { return Collections.unmodifiableMap(regionSeverity); }
    public long totalDeaths() { return totalDeaths; }
    public boolean isEnded() { return ended; }
    public int endYear() { return endYear; }
}

package com.eternalengine.buildings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Priority-based construction date inference foundation.
 */
public final class BuildingDateInferenceEngine {
    private final List<DataSource> sources = new ArrayList<>();

    public BuildingDateInferenceEngine() {
        addSource(new TagDataSource("OsmStartDateSource", 90));
        addSource(new TagDataSource("WikidataSource", 90));
        addSource(new TagDataSource("HeritageDbSource", 85));
        addSource(new RegionalDefaultSource());
    }

    public void addSource(DataSource source) {
        if (source != null) {
            sources.add(source);
            sources.sort(Comparator.comparingInt(DataSource::getConfidence).reversed());
        }
    }

    public BuildingDateResult inferConstructionDate(BuildingRecord building) {
        BuildingDateResult fallback = new BuildingDateResult(-3000, 2026, 0, "UNKNOWN", 0, "none", "No source matched");
        for (DataSource source : sources) {
            Optional<BuildingDateResult> result = source.query(building);
            if (result.isPresent()) {
                fallback = result.get();
                if (fallback.confidence() >= 60) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    public interface DataSource {
        String getName();
        int getConfidence();
        Optional<BuildingDateResult> query(BuildingRecord building);
    }

    private static final class TagDataSource implements DataSource {
        private final String name;
        private final int confidence;
        private TagDataSource(String name, int confidence) { this.name = name; this.confidence = confidence; }
        public String getName() { return name; }
        public int getConfidence() { return confidence; }
        public Optional<BuildingDateResult> query(BuildingRecord building) {
            if (building == null || building.constructionYear() == 0) return Optional.empty();
            int year = building.constructionYear();
            return Optional.of(new BuildingDateResult(year, year, year, eraForYear(year), confidence, name, "Existing construction year on record"));
        }
    }

    private static final class RegionalDefaultSource implements DataSource {
        public String getName() { return "RegionalDefaultSource"; }
        public int getConfidence() { return 12; }
        public Optional<BuildingDateResult> query(BuildingRecord building) {
            int year = building == null ? 1900 : building.constructionYear();
            if (year == 0) year = 1900;
            return Optional.of(new BuildingDateResult(year - 50, year + 50, year, eraForYear(year), 12, getName(), "Broad regional fallback"));
        }
    }

    private static String eraForYear(int year) {
        if (year >= 1945) return "MODERN";
        if (year >= 1750) return "INDUSTRIAL";
        if (year >= 1450) return "RENAISSANCE";
        if (year >= 500) return "MEDIEVAL";
        return "ANCIENT";
    }

    public record BuildingDateResult(int yearMin, int yearMax, int yearBest, String era,
                                     int confidence, String sourceUsed, String reasoning) {
        public boolean isExact() { return yearMin == yearMax; }
        public int rangeYears() { return Math.abs(yearMax - yearMin); }
    }
}

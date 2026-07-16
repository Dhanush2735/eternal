package com.eternalengine.buildings;

import com.eternalengine.core.PersistenceManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Building history database foundation.
 */
public final class BuildingHistoryDatabase {
    private final ConcurrentHashMap<String, BuildingRecord> byBuildingId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BuildingRecord> byOsmId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BuildingRecord> byWikidataId = new ConcurrentHashMap<>();
    private final SpatialIndex spatialIndex = new SpatialIndex();
    private final ConcurrentHashMap<String, List<BuildingRecord>> byCivilization = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<BuildingRecord>> byEra = new ConcurrentHashMap<>();

    public void addBuilding(BuildingRecord record) {
        if (record == null) return;
        byBuildingId.put(record.buildingId(), record);
        if (record.osmId() != null) byOsmId.put(record.osmId(), record);
        if (record.wikidataId() != null) byWikidataId.put(record.wikidataId(), record);
        spatialIndex.insert(record.buildingId(), record.latitude(), record.longitude(), record);
        byCivilization.computeIfAbsent(record.ownerCivId(), k -> new ArrayList<>()).add(record);
        byEra.computeIfAbsent(record.era(), k -> new ArrayList<>()).add(record);
    }

    public List<BuildingRecord> getBuildingsInRegion(double minLat, double maxLat, double minLon, double maxLon) {
        List<BuildingRecord> result = new ArrayList<>();
        for (SpatialIndex.IndexEntry entry : spatialIndex.queryBounds(minLat, maxLat, minLon, maxLon)) {
            if (entry.data() instanceof BuildingRecord record) {
                result.add(record);
            } else {
                BuildingRecord record = byBuildingId.get(entry.id());
                if (record != null) result.add(record);
            }
        }
        return List.copyOf(result);
    }

    public List<BuildingRecord> getBuildingsByEra(String era) {
        return List.copyOf(byEra.getOrDefault(era, List.of()));
    }

    public List<BuildingRecord> getBuildingsByCiv(String civId) {
        return List.copyOf(byCivilization.getOrDefault(civId, List.of()));
    }

    public List<BuildingRecord> getBuildingsExistingInYear(int year, double minLat, double maxLat, double minLon, double maxLon) {
        return getBuildingsInRegion(minLat, maxLat, minLon, maxLon).stream()
            .filter(record -> record.existsInYear(year))
            .toList();
    }

    public java.util.Optional<BuildingRecord> getBuilding(String id) {
        if (id == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(byBuildingId.get(id));
    }

    public List<BuildingStateChange> getBuildingHistory(String id) {
        BuildingRecord record = byBuildingId.get(id);
        return record == null ? List.of() : record.stateHistory();
    }

    public List<BuildingRecord> getFamousBuildings() {
        return byBuildingId.values().stream().filter(record -> record.culturalSignificance() >= 70).toList();
    }

    public List<BuildingRecord> getDamagedByEvent(String eventId) {
        if (eventId == null) return List.of();
        List<BuildingRecord> result = new ArrayList<>();
        for (BuildingRecord record : byBuildingId.values()) {
            boolean match = record.stateHistory().stream().anyMatch(change -> eventId.equalsIgnoreCase(change.cause()));
            if (match) result.add(record);
        }
        return List.copyOf(result);
    }

    public void updateBuilding(BuildingRecord record) {
        if (record == null) return;
        remove(record.buildingId());
        addBuilding(record);
    }

    public void remove(String buildingId) {
        BuildingRecord old = byBuildingId.remove(buildingId);
        if (old == null) return;
        if (old.osmId() != null) byOsmId.remove(old.osmId());
        if (old.wikidataId() != null) byWikidataId.remove(old.wikidataId());
        spatialIndex.remove(old.buildingId());
        byCivilization.values().forEach(list -> list.remove(old));
        byEra.values().forEach(list -> list.remove(old));
    }

    public void saveRegion(String regionId, PersistenceManager persistenceManager, Path file) {
        if (persistenceManager == null || file == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("regionId", regionId == null ? "global" : regionId);
        JsonArray array = new JsonArray();
        for (BuildingRecord record : byBuildingId.values()) {
            array.add(com.google.gson.JsonParser.parseString(record.toJson()).getAsJsonObject());
        }
        root.add("buildings", array);
        persistenceManager.saveJsonAsync(file, root, null);
    }

    public void loadRegion(PersistenceManager persistenceManager, Path file) {
        if (persistenceManager == null || file == null) return;
        JsonObject root = persistenceManager.loadJson(file);
        if (root == null || !root.has("buildings")) return;
        for (var element : root.getAsJsonArray("buildings")) {
            addBuilding(BuildingRecord.fromJson(element.toString()));
        }
    }

    public int size() { return byBuildingId.size(); }
    public SpatialIndex spatialIndex() { return spatialIndex; }
}

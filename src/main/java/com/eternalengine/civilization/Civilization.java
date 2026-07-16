package com.eternalengine.civilization;

import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Civilization definition/state foundation.
 */
public final class Civilization {
    private final String id;
    private String name;
    private String laterName;
    private int nameChangeYear;
    private String eraStart;
    private int foundingYear;
    private double capitalLat;
    private double capitalLon;
    private final List<String> territoryIds = new ArrayList<>();
    private long population;
    private long militarySize;
    private long treasury;
    private int technologyLevel;
    private String currentTechEra;
    private String religionId;
    private String governmentType;
    private final Map<String, String> relations = new HashMap<>();
    private int culturalSignificance;
    private boolean active;
    private int peakYear;
    private int fallYear;

    public Civilization(String id, String name, int foundingYear, double capitalLat, double capitalLon) {
        this.id = id == null || id.isBlank() ? "civ_unknown" : id;
        this.name = name == null || name.isBlank() ? this.id : name;
        this.laterName = this.name;
        this.nameChangeYear = Integer.MAX_VALUE;
        this.eraStart = "ANCIENT";
        this.foundingYear = foundingYear;
        this.capitalLat = capitalLat;
        this.capitalLon = capitalLon;
        this.currentTechEra = "ANCIENT";
        this.religionId = "none";
        this.governmentType = "tribal";
        this.active = true;
        this.fallYear = Integer.MAX_VALUE;
    }

    public String getCurrentName(int simYear) {
        return simYear >= nameChangeYear ? laterName : name;
    }

    public boolean isActiveInYear(int year) {
        return active && year >= foundingYear && year < fallYear;
    }

    @Nullable
    public BlockPos getCapitalBlockPos(@Nullable Level level) {
        return TerraCoordinateHelper.fromGeo(capitalLat, capitalLon, level);
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name == null ? this.name : name; }
    public String laterName() { return laterName; }
    public void setLaterName(String laterName) { this.laterName = laterName == null ? this.laterName : laterName; }
    public int nameChangeYear() { return nameChangeYear; }
    public void setNameChangeYear(int nameChangeYear) { this.nameChangeYear = nameChangeYear; }
    public String eraStart() { return eraStart; }
    public void setEraStart(String eraStart) { this.eraStart = eraStart == null ? this.eraStart : eraStart; }
    public int foundingYear() { return foundingYear; }
    public void setFoundingYear(int foundingYear) { this.foundingYear = foundingYear; }
    public double capitalLat() { return capitalLat; }
    public double capitalLon() { return capitalLon; }
    public void setCapital(double lat, double lon) { this.capitalLat = lat; this.capitalLon = lon; }
    public List<String> territoryIds() { return List.copyOf(territoryIds); }
    public void addTerritory(String territoryId) { if (territoryId != null && !territoryIds.contains(territoryId)) territoryIds.add(territoryId); }
    public long population() { return population; }
    public void setPopulation(long population) { this.population = Math.max(0L, population); }
    public long militarySize() { return militarySize; }
    public void setMilitarySize(long militarySize) { this.militarySize = Math.max(0L, militarySize); }
    public long treasury() { return treasury; }
    public void setTreasury(long treasury) { this.treasury = treasury; }
    public int technologyLevel() { return technologyLevel; }
    public void setTechnologyLevel(int technologyLevel) { this.technologyLevel = Math.max(0, technologyLevel); }
    public String currentTechEra() { return currentTechEra; }
    public void setCurrentTechEra(String currentTechEra) { this.currentTechEra = currentTechEra == null ? this.currentTechEra : currentTechEra; }
    public String religionId() { return religionId; }
    public void setReligionId(String religionId) { this.religionId = religionId == null ? this.religionId : religionId; }
    public String governmentType() { return governmentType; }
    public void setGovernmentType(String governmentType) { this.governmentType = governmentType == null ? this.governmentType : governmentType; }
    public Map<String, String> relations() { return Map.copyOf(relations); }
    public void setRelation(String civId, String relation) { if (civId != null) relations.put(civId, relation); }
    public int culturalSignificance() { return culturalSignificance; }
    public void setCulturalSignificance(int culturalSignificance) { this.culturalSignificance = Math.max(0, Math.min(100, culturalSignificance)); }
    public boolean active() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int peakYear() { return peakYear; }
    public void setPeakYear(int peakYear) { this.peakYear = peakYear; }
    public int fallYear() { return fallYear; }
    public void setFallYear(int fallYear) { this.fallYear = fallYear; }
}

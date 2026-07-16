package com.eternalengine.disease;

import com.eternalengine.core.WorldStateManager;
import com.eternalengine.incident.IncidentSeed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative disease simulation foundation.
 */
public final class DiseaseEngine {
    private final ConcurrentHashMap<String, Disease> diseaseDatabase = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DiseaseOutbreak> activeOutbreaks = new ConcurrentHashMap<>();
    private final MedicineSystem medicineSystem;
    private final EpidemicTracker epidemicTracker;

    public DiseaseEngine() {
        this(new MedicineSystem(), new EpidemicTracker());
    }

    public DiseaseEngine(MedicineSystem medicineSystem, EpidemicTracker epidemicTracker) {
        this.medicineSystem = medicineSystem == null ? new MedicineSystem() : medicineSystem;
        this.epidemicTracker = epidemicTracker == null ? new EpidemicTracker() : epidemicTracker;
        registerDefaultDiseases();
    }

    public void tick(int simYear, WorldStateManager state) {
        for (Disease disease : diseaseDatabase.values()) {
            if (disease.historicalYear() == simYear && activeOutbreaks.values().stream().noneMatch(o -> o.diseaseId().equals(disease.id()))) {
                triggerDisease(disease.id(), "global", simYear, state);
            }
        }
        for (DiseaseOutbreak outbreak : activeOutbreaks.values()) {
            if (!outbreak.isEnded()) {
                spreadDisease(outbreak, simYear, state);
            }
        }
        syncToWorldState(state);
    }

    public DiseaseOutbreak triggerDisease(String diseaseId, String regionId, int year, WorldStateManager state) {
        Disease disease = diseaseDatabase.get(diseaseId);
        if (disease == null) {
            disease = new Disease(diseaseId, diseaseId, diseaseId, DiseaseType.BACTERIAL, SpreadType.CONTACT,
                year, 0.0d, 0.0d, 100.0d, 0.05d, -1, "unknown", List.of(), 1.0d, 1.0d, 1.0d);
            diseaseDatabase.put(disease.id(), disease);
        }
        String outbreakId = "outbreak_" + IncidentSeed.stableHex(diseaseId, regionId, year).substring(0, 12);
        DiseaseOutbreak outbreak = new DiseaseOutbreak(outbreakId, disease.id(), regionId, year);
        activeOutbreaks.put(outbreakId, outbreak);
        if (state != null) {
            state.setFlag("disease.active." + disease.id(), Integer.toString(year));
        }
        return outbreak;
    }

    public void spreadDisease(DiseaseOutbreak outbreak, int year, WorldStateManager state) {
        Disease disease = diseaseDatabase.get(outbreak.diseaseId());
        if (disease == null || outbreak.isEnded()) {
            return;
        }
        List<String> current = new ArrayList<>(outbreak.affectedRegionIds());
        for (String region : current) {
            double quarantine = epidemicTracker.calculateQuarantineEffectiveness(region, year, state);
            double severity = outbreak.regionSeverity().getOrDefault(region, 0.1d);
            double spreadChance = clamp01(0.08d * disease.tradeRouteMultiplier() * disease.warMultiplier() * (1.0d - quarantine) + severity * 0.1d);
            double roll = ((IncidentSeed.stableLong(outbreak.outbreakId(), region, year, "spread") & Long.MAX_VALUE) % 10_000L) / 10_000.0d;
            if (roll < spreadChance) {
                String newRegion = region + "_spread_" + year;
                outbreak.affectRegion(newRegion, Math.min(1.0d, severity + 0.1d));
            }
            long population = state == null ? 100_000L : Math.max(1L, state.getPopulation(region));
            long deaths = Math.round(population * calculateMortality(disease.id(), region, MedicineTier.ANCIENT) * severity * 0.01d);
            outbreak.addDeaths(deaths);
        }
        if (disease.cureYear() > 0 && year >= disease.cureYear()) {
            outbreak.end(year);
        }
    }

    public double calculateMortality(String diseaseId, String regionId, MedicineTier tier) {
        Disease disease = diseaseDatabase.get(diseaseId);
        if (disease == null) {
            return 0.0d;
        }
        return clamp01(disease.baseMortalityRate() * medicineSystem.getMortalityModifier(tier, diseaseId));
    }

    public void endOutbreak(String outbreakId, int year, WorldStateManager state) {
        DiseaseOutbreak outbreak = activeOutbreaks.get(outbreakId);
        if (outbreak != null) {
            outbreak.end(year);
            if (state != null) {
                state.removeDisease(outbreakId);
            }
        }
    }

    public List<DiseaseOutbreak> getActiveDiseases() {
        return activeOutbreaks.values().stream().filter(o -> !o.isEnded()).toList();
    }

    public Optional<DiseaseOutbreak> getOutbreak(String id) {
        return Optional.ofNullable(activeOutbreaks.get(id));
    }

    public void registerDisease(Disease disease) {
        if (disease != null) {
            diseaseDatabase.put(disease.id(), disease);
        }
    }

    private void syncToWorldState(WorldStateManager state) {
        if (state == null) {
            return;
        }
        for (DiseaseOutbreak outbreak : getActiveDiseases()) {
            WorldStateManager.DiseaseState ds = new WorldStateManager.DiseaseState();
            ds.diseaseId = outbreak.outbreakId();
            ds.name = outbreak.diseaseId();
            ds.startYear = outbreak.startYear();
            ds.spreadRadius = outbreak.affectedRegionIds().size();
            ds.affectedRegions = java.util.Set.copyOf(outbreak.affectedRegionIds());
            ds.active = true;
            state.addDisease(outbreak.outbreakId(), ds);
        }
    }

    private void registerDefaultDiseases() {
        registerDisease(new Disease("black_death", "Black Death", "Bubonic Plague", DiseaseType.BACTERIAL,
            SpreadType.RAT_FLEA_HUMAN, 1347, 44.4d, 33.5d, 800.0d, 0.35d, -1,
            "quarantine", List.of("black_death"), 1.4d, 1.2d, 1.3d));
        registerDisease(new Disease("cholera", "Cholera", "Cholera", DiseaseType.BACTERIAL,
            SpreadType.WATER, 1817, 23.8d, 90.4d, 500.0d, 0.12d, 1885,
            "clean_water", List.of(), 1.2d, 1.1d, 1.4d));
        registerDisease(new Disease("influenza", "Influenza", "Influenza", DiseaseType.VIRAL,
            SpreadType.AIRBORNE, 1918, 39.0d, -77.0d, 1200.0d, 0.03d, 1945,
            "vaccination", List.of(), 1.5d, 1.5d, 1.0d));
    }

    private double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public MedicineSystem medicineSystem() { return medicineSystem; }
    public EpidemicTracker epidemicTracker() { return epidemicTracker; }
}

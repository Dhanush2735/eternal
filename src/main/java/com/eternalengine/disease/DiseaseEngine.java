package com.eternalengine.disease;

import com.eternalengine.core.WorldStateManager;
import com.eternalengine.incident.IncidentSeed;
import com.eternalengine.terrabridge.TerraBridge;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.server.level.ServerLevel;

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
    private final ContagionTransmissionBrain contagionBrain = new ContagionTransmissionBrain();
    private final QuarantineCheckpointController checkpointController = new QuarantineCheckpointController();
    private final PlagueDoctorHospitalManager hospitalManager = new PlagueDoctorHospitalManager();
    private final HistoricalDisasterEngine disasterEngine = new HistoricalDisasterEngine();

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
        Disease disease = ensureDisease(diseaseId, year);
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

    /** A disease nobody described (a typed id): a plain contact disease, so its outbreak can still spread. */
    private Disease ensureDisease(String diseaseId, int year) {
        Disease disease = diseaseDatabase.get(diseaseId);
        if (disease == null) {
            disease = new Disease(diseaseId, diseaseId, diseaseId, DiseaseType.BACTERIAL, SpreadType.CONTACT,
                year, 0.0d, 0.0d, 100.0d, 0.05d, -1, "unknown", List.of(), 1.0d, 1.0d, 1.0d);
            diseaseDatabase.put(disease.id(), disease);
        }
        return disease;
    }

    /**
     * World start (step 14): the outbreaks this world had when it was saved, with their regions, how hard
     * each is hit and the dead so far. Before, they lived in memory only and a restart cured them all.
     *
     * @return the number of outbreaks restored
     */
    public int restoreFrom(WorldStateManager state) {
        activeOutbreaks.clear();
        if (state == null) {
            return 0;
        }
        int restored = 0;
        for (WorldStateManager.DiseaseState ds : state.getAllActiveDiseases().values()) {
            if (ds == null || ds.diseaseId == null || ds.diseaseId.isBlank() || !ds.active) {
                continue;
            }
            String diseaseId = ds.name == null || ds.name.isBlank() ? "disease_unknown" : ds.name;
            ensureDisease(diseaseId, ds.startYear);
            String origin = ds.origin;
            if ((origin == null || origin.isBlank()) && ds.affectedRegions != null && !ds.affectedRegions.isEmpty()) {
                origin = ds.affectedRegions.iterator().next();
            }
            DiseaseOutbreak outbreak = new DiseaseOutbreak(ds.diseaseId, diseaseId, origin, ds.startYear);
            if (ds.affectedRegions != null) {
                for (String region : ds.affectedRegions) {
                    double severity = ds.severity == null ? 0.25d : ds.severity.getOrDefault(region, 0.25d);
                    outbreak.affectRegion(region, severity);
                }
            }
            outbreak.addDeaths(ds.deaths);
            activeOutbreaks.put(outbreak.outbreakId(), outbreak);
            restored++;
        }
        return restored;
    }

    private void syncToWorldState(WorldStateManager state) {
        if (state == null) {
            return;
        }
        // An outbreak that ended by itself (its cure year came) leaves the save too; otherwise it was
        // written as active forever and a restart would bring it back.
        for (DiseaseOutbreak outbreak : activeOutbreaks.values()) {
            if (outbreak.isEnded() && state.getDisease(outbreak.outbreakId()) != null) {
                state.removeDisease(outbreak.outbreakId());
            }
        }
        for (DiseaseOutbreak outbreak : getActiveDiseases()) {
            WorldStateManager.DiseaseState ds = new WorldStateManager.DiseaseState();
            ds.diseaseId = outbreak.outbreakId();
            ds.name = outbreak.diseaseId();
            ds.startYear = outbreak.startYear();
            ds.spreadRadius = outbreak.affectedRegionIds().size();
            ds.affectedRegions = java.util.Set.copyOf(outbreak.affectedRegionIds());
            ds.active = true;
            ds.origin = outbreak.originRegionId();
            ds.severity = new java.util.TreeMap<>(outbreak.regionSeverity());
            ds.deaths = outbreak.totalDeaths();
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

    /** Where this level is, as {latitude, longitude}, or null when the bridge cannot say.
     * Terra++ hands back {longitude, latitude}, so the two are swapped here — once, and with a
     * comment, because a latitude and a longitude swapped silently is the oldest bug there is. */
    private static double[] placeOf(ServerLevel level) {
        try {
            double[] geo = TerraCoordinateHelper.toGeo(level.getSharedSpawnPos());
            if (geo == null || geo.length < 2) {
                return null;
            }
            double lon = geo[0];
            double lat = geo[1];
            return TerraBridge.isValidLatLon(lat, lon) ? new double[] {lat, lon} : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public MedicineSystem medicineSystem() { return medicineSystem; }
    public EpidemicTracker epidemicTracker() { return epidemicTracker; }
    public ContagionTransmissionBrain contagionBrain() { return contagionBrain; }
    public QuarantineCheckpointController checkpointController() { return checkpointController; }
    public PlagueDoctorHospitalManager hospitalManager() { return hospitalManager; }
    public HistoricalDisasterEngine disasterEngine() { return disasterEngine; }

    /**
     * Step 38 (C20): the tier is read for <b>where this level is</b>, not for the year alone.
     * Before, every level on Earth in 1945 was MODERN at once, which cured people with drugs that
     * had never been near them. Where the coordinates cannot be had — a level with no bridge — the
     * old world-wide year tier stands in, and it is the only place it is still used.
     */
    public void tickLevel(ServerLevel level, int simYear) {
        if (level == null) return;
        MedicineTier tier = medicineSystem.getMedicineTier("global", simYear, 5);
        double[] geo = placeOf(level);
        if (geo != null) {
            tier = medicineSystem.tierAt(geo[0], geo[1], simYear);
        }
        contagionBrain.tick(level, tier);
        checkpointController.tick(level, contagionBrain);
        hospitalManager.tick(level, contagionBrain);
        disasterEngine.tick(level);
    }
}

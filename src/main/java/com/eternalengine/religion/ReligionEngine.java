package com.eternalengine.religion;

import com.eternalengine.core.WorldStateManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Religion simulation foundation.
 */
public final class ReligionEngine {
    private final ConcurrentHashMap<String, Religion> religions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> civReligion = new ConcurrentHashMap<>();

    public ReligionEngine() {
        registerDefaults();
    }

    public void registerReligion(Religion religion) {
        if (religion != null) {
            religions.put(religion.id(), religion);
        }
    }

    public void tick(int simYear, WorldStateManager state) {
        for (Religion religion : religions.values()) {
            if (!religion.isFounded(simYear)) {
                continue;
            }
            long growth = Math.max(1L, Math.round(religion.currentFollowers() * religion.spreadRatePerYear()));
            religion.addFollowers(growth);
            if (state != null) {
                state.setFlag("religion.followers." + religion.id(), Long.toString(religion.currentFollowers()));
                state.setFlag("religion.power." + religion.id(), Double.toString(religion.politicalPower()));
            }
        }
    }

    public Optional<Religion> getReligionAt(double lat, double lon, int year) {
        Religion best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Religion religion : religions.values()) {
            if (!religion.isFounded(year)) continue;
            double distance = roughDistance(religion.originLat(), religion.originLon(), lat, lon);
            if (distance < bestDistance) {
                best = religion;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public void convertPopulation(String civId, String religionId, double fraction, WorldStateManager state) {
        if (civId == null || religionId == null || !religions.containsKey(religionId)) {
            return;
        }
        civReligion.put(civId, religionId);
        Religion religion = religions.get(religionId);
        religion.addFollowers(Math.round((state == null ? 1000L : state.getPopulation(civId)) * Math.max(0.0d, Math.min(1.0d, fraction))));
        if (state != null) {
            WorldStateManager.CivilizationState civ = state.getCivilization(civId);
            if (civ != null) {
                civ.religionId = religionId;
                state.setCivilization(civId, civ);
            }
            state.setFlag("civ.religion." + civId, religionId);
        }
    }

    public boolean declareHolyWar(String civId, String targetCivId, String religionId, WorldStateManager state) {
        Religion religion = religions.get(religionId);
        if (religion == null || !religion.allowsHolyWar()) {
            return false;
        }
        if (state != null) {
            state.setFlag("holy_war." + civId + "." + targetCivId, religionId);
        }
        return true;
    }

    public boolean excommunicate(String civId, String targetCivId, WorldStateManager state) {
        String religionId = civReligion.get(civId);
        if (religionId == null) {
            return false;
        }
        Religion religion = religions.get(religionId);
        if (religion != null) {
            religion.adjustPoliticalPower(-5.0d);
        }
        if (state != null) {
            state.setFlag("excommunicated." + targetCivId, religionId);
        }
        return true;
    }

    public Optional<Religion> getReligionOf(String civId) {
        return Optional.ofNullable(religions.get(civReligion.get(civId)));
    }

    public long getFollowers(String religionId) {
        Religion religion = religions.get(religionId);
        return religion == null ? 0L : religion.currentFollowers();
    }

    public List<Religion> allReligions() {
        return List.copyOf(religions.values());
    }

    private double roughDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = (lon2 - lon1) * Math.cos(Math.toRadians(lat1));
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    private void registerDefaults() {
        registerReligion(new Religion("buddhism", "Buddhism", -500, 26.0d, 85.0d,
            0.015d, 100_000L, 40.0d, List.of(), List.of(), false, false));
        registerReligion(new Religion("christianity", "Christianity", 30, 31.8d, 35.2d,
            0.020d, 100_000L, 50.0d, List.of(), List.of(), true, false));
        registerReligion(new Religion("islam", "Islam", 610, 21.4d, 39.8d,
            0.025d, 100_000L, 55.0d, List.of(), List.of(), true, true));
    }
}

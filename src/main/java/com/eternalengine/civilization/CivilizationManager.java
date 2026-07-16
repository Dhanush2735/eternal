package com.eternalengine.civilization;

import com.eternalengine.core.WorldStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Civilization registry foundation.
 */
public final class CivilizationManager {
    private final ConcurrentHashMap<String, Civilization> civilizations = new ConcurrentHashMap<>();
    private final TerritoryManager territoryManager;

    public CivilizationManager() {
        this(new TerritoryManager());
    }

    public CivilizationManager(TerritoryManager territoryManager) {
        this.territoryManager = territoryManager == null ? new TerritoryManager() : territoryManager;
    }

    public void addCivilization(Civilization civilization, WorldStateManager state) {
        if (civilization == null) {
            return;
        }
        civilizations.put(civilization.id(), civilization);
        syncCivilization(civilization, state);
    }

    public Optional<Civilization> getCivilization(String id) {
        return Optional.ofNullable(civilizations.get(id));
    }

    public Optional<Civilization> getCivilizationAt(double lat, double lon, int year) {
        Optional<String> owner = territoryManager.getOwner(lat, lon, year);
        return owner.flatMap(this::getCivilization).filter(civ -> civ.isActiveInYear(year));
    }

    public List<Civilization> getActiveCivilizations() {
        return List.copyOf(civilizations.values().stream().filter(Civilization::active).toList());
    }

    public List<Civilization> getActiveCivilizations(int year) {
        return List.copyOf(civilizations.values().stream().filter(civ -> civ.isActiveInYear(year)).toList());
    }

    public Civilization spawnCivilization(String civId, int year, WorldStateManager state) {
        Civilization civ = civilizations.computeIfAbsent(civId,
            id -> new Civilization(id, id, year, 0.0d, 0.0d));
        civ.setActive(true);
        civ.setFoundingYear(Math.min(civ.foundingYear(), year));
        syncCivilization(civ, state);
        return civ;
    }

    public void removeCivilization(String id, String reason, int year, WorldStateManager state) {
        Civilization civ = civilizations.get(id);
        if (civ == null) {
            return;
        }
        civ.setActive(false);
        civ.setFallYear(year);
        if (state != null) {
            WorldStateManager.CivilizationState st = state.getCivilization(id);
            if (st != null) {
                st.active = false;
                state.setCivilization(id, st);
            }
            state.setFlag("civ.fall." + id, reason == null ? Integer.toString(year) : reason);
        }
    }

    public Optional<Civilization> mergeCivilizations(String id1, String id2, String newId, WorldStateManager state) {
        Civilization a = civilizations.get(id1);
        Civilization b = civilizations.get(id2);
        if (a == null || b == null) {
            return Optional.empty();
        }
        Civilization merged = new Civilization(newId, a.name() + "-" + b.name(), Math.min(a.foundingYear(), b.foundingYear()),
            a.capitalLat(), a.capitalLon());
        merged.setPopulation(a.population() + b.population());
        merged.setTreasury(a.treasury() + b.treasury());
        civilizations.put(merged.id(), merged);
        a.setActive(false);
        b.setActive(false);
        syncCivilization(merged, state);
        return Optional.of(merged);
    }

    public List<String> ids() {
        return new ArrayList<>(civilizations.keySet());
    }

    public TerritoryManager territoryManager() {
        return territoryManager;
    }

    private void syncCivilization(Civilization civ, WorldStateManager state) {
        if (state == null || civ == null) {
            return;
        }
        WorldStateManager.CivilizationState st = new WorldStateManager.CivilizationState();
        st.id = civ.id();
        st.name = civ.name();
        st.active = civ.active();
        st.population = civ.population();
        st.treasury = civ.treasury();
        st.techLevel = civ.technologyLevel();
        st.religionId = civ.religionId();
        st.governmentType = civ.governmentType();
        state.setCivilization(civ.id(), st);
    }
}

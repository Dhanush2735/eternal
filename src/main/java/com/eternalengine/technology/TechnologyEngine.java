package com.eternalengine.technology;

import com.eternalengine.core.WorldStateManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Technology unlock and era transition foundation.
 */
public final class TechnologyEngine {
    private final TechnologyTree technologyTree;
    private final ConcurrentHashMap<String, Set<String>> civTechs = new ConcurrentHashMap<>();

    public TechnologyEngine() {
        this(new TechnologyTree());
    }

    public TechnologyEngine(TechnologyTree technologyTree) {
        this.technologyTree = technologyTree == null ? new TechnologyTree() : technologyTree;
    }

    public void tick(int simYear, WorldStateManager state) {
        if (state == null) {
            return;
        }
        for (String civId : state.getAllCivilizationIds()) {
            double speed = calculateResearchSpeed(civId, state);
            if (speed > 0.5d) {
                unlockBestAvailable(civId, state);
            }
        }
    }

    public boolean unlockTechnology(String civId, String techId, WorldStateManager state) {
        if (civId == null || techId == null) {
            return false;
        }
        Technology tech = technologyTree.get(techId).orElse(null);
        if (tech == null || !hasPrerequisites(civId, tech)) {
            return false;
        }
        Set<String> techs = civTechs.computeIfAbsent(civId, id -> ConcurrentHashMap.newKeySet());
        boolean added = techs.add(tech.id());
        if (added && state != null) {
            WorldStateManager.CivilizationState civ = state.getCivilization(civId);
            if (civ != null) {
                civ.techLevel = Math.max(civ.techLevel, tech.level());
                state.setCivilization(civId, civ);
            }
            state.setFlag("tech.unlocked." + civId + "." + tech.id(), Integer.toString(tech.level()));
            state.setFlag("tech.era." + civId, tech.era().name());
        }
        return added;
    }

    public int getTechLevel(String civId) {
        int level = 0;
        for (String techId : getUnlockedTechs(civId)) {
            Technology tech = technologyTree.get(techId).orElse(null);
            if (tech != null) level = Math.max(level, tech.level());
        }
        return level;
    }

    public String getTechEra(String civId) {
        TechEra era = TechEra.STONE_AGE;
        for (String techId : getUnlockedTechs(civId)) {
            Technology tech = technologyTree.get(techId).orElse(null);
            if (tech != null && tech.era().ordinal() > era.ordinal()) {
                era = tech.era();
            }
        }
        return era.name();
    }

    public boolean hasUnlocked(String civId, String techId) {
        return getUnlockedTechs(civId).contains(techId);
    }

    public Set<String> getUnlockedTechs(String civId) {
        return Set.copyOf(civTechs.getOrDefault(civId, Set.of()));
    }

    public double calculateResearchSpeed(String civId, WorldStateManager state) {
        long population = state == null ? 0L : state.getPopulation(civId);
        double populationFactor = Math.min(1.0d, population / 1_000_000.0d);
        double treasuryFactor = state == null ? 0.0d : Math.min(1.0d, Math.max(0.0d, state.getTreasury(civId) / 100_000.0d));
        return Math.max(0.0d, populationFactor * 0.6d + treasuryFactor * 0.4d);
    }

    public TechnologyTree tree() {
        return technologyTree;
    }

    private boolean hasPrerequisites(String civId, Technology tech) {
        Set<String> unlocked = civTechs.getOrDefault(civId, Set.of());
        return unlocked.containsAll(tech.prerequisites());
    }

    private void unlockBestAvailable(String civId, WorldStateManager state) {
        Set<String> unlocked = new HashSet<>(getUnlockedTechs(civId));
        for (Technology tech : technologyTree.all()) {
            if (!unlocked.contains(tech.id()) && unlocked.containsAll(tech.prerequisites())) {
                unlockTechnology(civId, tech.id(), state);
                return;
            }
        }
    }
}

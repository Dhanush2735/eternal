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
        // Step 21: the date can be moved back (/ee date): what nobody had in that year is taken away again.
        forgetAfter(simYear, state);
        for (String civId : state.getAllCivilizationIds()) {
            double speed = calculateResearchSpeed(civId, state);
            if (speed > 0.5d) {
                unlockBestAvailable(civId, state, simYear);
            }
        }
    }

    /** Unlock without a date check (the old form): only for callers that have already checked the year. */
    public boolean unlockTechnology(String civId, String techId, WorldStateManager state) {
        return unlockTechnology(civId, techId, state, Integer.MAX_VALUE);
    }

    /**
     * Step 21: unlock a technology for a civilization in this year. Refused before the first year it is known anywhere
     * (see {@link Technology#firstYear()}), or without its prerequisites.
     */
    public boolean unlockTechnology(String civId, String techId, WorldStateManager state, int year) {
        if (civId == null || techId == null) {
            return false;
        }
        Technology tech = technologyTree.get(techId).orElse(null);
        if (tech == null || !tech.knownIn(year) || !hasPrerequisites(civId, tech)) {
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

    private void unlockBestAvailable(String civId, WorldStateManager state, int year) {
        Set<String> unlocked = new HashSet<>(getUnlockedTechs(civId));
        for (Technology tech : technologyTree.all()) {
            if (!unlocked.contains(tech.id()) && tech.knownIn(year) && unlocked.containsAll(tech.prerequisites())) {
                unlockTechnology(civId, tech.id(), state, year);
                return;
            }
        }
    }

    /**
     * Step 21: take away every technology nobody had yet in this year (after the date was moved back), from every
     * civilization, with its flag; the civilization's level and era follow what is left.
     *
     * @return how many were taken away
     */
    public int forgetAfter(int year, WorldStateManager state) {
        int taken = 0;
        for (var entry : civTechs.entrySet()) {
            String civId = entry.getKey();
            Set<String> techs = entry.getValue();
            boolean changed = false;
            for (String techId : Set.copyOf(techs)) {
                Technology tech = technologyTree.get(techId).orElse(null);
                if (tech != null && !tech.knownIn(year)) {
                    techs.remove(techId);
                    changed = true;
                    taken++;
                    if (state != null) {
                        state.removeFlag("tech.unlocked." + civId + "." + techId);
                    }
                }
            }
            if (changed && state != null) {
                state.setFlag("tech.era." + civId, getTechEra(civId));
                WorldStateManager.CivilizationState civ = state.getCivilization(civId);
                if (civ != null) {
                    civ.techLevel = Math.min(civ.techLevel, getTechLevel(civId));
                    state.setCivilization(civId, civ);
                }
            }
        }
        return taken;
    }

    /**
     * Step 21: the rule on a made-up civilization across the years of the world-wide test set; null when nothing is
     * ever unlocked before its first known year and a move back takes later ones away, else what broke.
     */
    public static String selfTest() {
        TechnologyEngine engine = new TechnologyEngine();
        String civ = "selftest_civ";
        int[] years = {-2500, -500, 1, 1000, 1500, 1900, 2000};
        for (int year : years) {
            for (int i = 0; i < 20; i++) {
                engine.unlockBestAvailable(civ, null, year);
            }
            for (String id : engine.getUnlockedTechs(civ)) {
                Technology t = engine.tree().get(id).orElse(null);
                if (t != null && !t.knownIn(year)) {
                    return t.name() + " unlocked in " + year + ", before " + t.firstLabel();
                }
            }
        }
        if (!engine.hasUnlocked(civ, "computing")) {
            return "by 2000 the made-up civilization still has no computing: " + engine.getUnlockedTechs(civ);
        }
        if (engine.unlockTechnology("other_civ", "agriculture", null, -10000)) {
            return "agriculture unlocked in 10000 BCE";
        }
        engine.forgetAfter(1500, null);
        if (engine.hasUnlocked(civ, "steam_power") || engine.hasUnlocked(civ, "computing")
                || !engine.hasUnlocked(civ, "printing_press")) {
            return "moving back to 1500 left " + engine.getUnlockedTechs(civ);
        }
        return null;
    }
}

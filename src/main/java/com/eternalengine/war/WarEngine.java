package com.eternalengine.war;

import com.eternalengine.buildings.BlockUpdateQueue;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.incident.IncidentSeed;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative war engine managing both virtual campaigns and
 * physical field combat and siege operations.
 */
public final class WarEngine {
    private final ConcurrentHashMap<String, War> activeWars = new ConcurrentHashMap<>();
    private final ArmyManager armyManager;
    private final BattleSimulator battleSimulator;
    private final SiegeEngineController siegeController;

    public WarEngine() {
        this(new ArmyManager(), new BattleSimulator(), new SiegeEngineController());
    }

    public WarEngine(ArmyManager armyManager, BattleSimulator battleSimulator) {
        this(armyManager, battleSimulator, new SiegeEngineController());
    }

    public WarEngine(ArmyManager armyManager, BattleSimulator battleSimulator, SiegeEngineController siegeController) {
        this.armyManager = armyManager == null ? new ArmyManager() : armyManager;
        this.battleSimulator = battleSimulator == null ? new BattleSimulator() : battleSimulator;
        this.siegeController = siegeController == null ? new SiegeEngineController() : siegeController;
    }

    public void tick(int simYear, WorldStateManager state) {
        armyManager.tick(simYear);
        for (War war : activeWars.values()) {
            if (war.status() == WarStatus.ACTIVE) {
                war.increaseExhaustion(0.02d);
            }
        }
        syncToWorldState(state);
    }

    /**
     * Ticks physicalized military cohorts and active siege operations in the world.
     */
    public void tickLevel(ServerLevel level, BlockUpdateQueue queue) {
        if (level == null) return;
        armyManager.tickCohorts(level);
        siegeController.tick(level, queue);
    }

    public War declareWar(String attackerCivId, String defenderCivId, String cause, int simYear, WorldStateManager state) {
        String warId = "war_" + IncidentSeed.stableHex(attackerCivId, defenderCivId, cause, simYear).substring(0, 12);
        War war = new War(warId, attackerCivId + " vs " + defenderCivId,
            List.of(attackerCivId), List.of(defenderCivId), simYear, cause);
        activeWars.put(warId, war);
        if (state != null) {
            WorldStateManager.WarState ws = new WorldStateManager.WarState();
            ws.warId = warId;
            ws.name = war.name();
            ws.attackers = java.util.Set.of(attackerCivId);
            ws.defenders = java.util.Set.of(defenderCivId);
            ws.startYear = simYear;
            ws.casualties = 0;
            ws.active = true;
            ws.cause = cause;
            state.addWar(warId, ws);
        }
        return war;
    }

    public void endWar(String warId, int simYear, WorldStateManager state) {
        War war = activeWars.get(warId);
        if (war != null) {
            war.end(simYear);
        }
        if (state != null) {
            state.removeWar(warId);
        }
    }

    public Optional<War> getWar(String warId) {
        return Optional.ofNullable(activeWars.get(warId));
    }

    public List<War> getActiveWars() {
        return List.copyOf(activeWars.values());
    }

    public List<War> getWarsInvolving(String civId) {
        List<War> result = new ArrayList<>();
        for (War war : activeWars.values()) {
            if (war.alliedFactionIds().contains(civId) || war.opposedFactionIds().contains(civId)) {
                result.add(war);
            }
        }
        return List.copyOf(result);
    }

    public boolean isAtWar(String civ1, String civ2) {
        for (War war : activeWars.values()) {
            boolean c1A = war.alliedFactionIds().contains(civ1);
            boolean c1D = war.opposedFactionIds().contains(civ1);
            boolean c2A = war.alliedFactionIds().contains(civ2);
            boolean c2D = war.opposedFactionIds().contains(civ2);
            if ((c1A && c2D) || (c1D && c2A)) {
                return true;
            }
        }
        return false;
    }

    private void syncToWorldState(WorldStateManager state) {
        if (state == null) {
            return;
        }
        for (War war : activeWars.values()) {
            if (war.status() == WarStatus.ENDED) {
                continue;
            }
            WorldStateManager.WarState ws = new WorldStateManager.WarState();
            ws.warId = war.warId();
            ws.name = war.name();
            ws.attackers = java.util.Set.copyOf(war.alliedFactionIds());
            ws.defenders = java.util.Set.copyOf(war.opposedFactionIds());
            ws.startYear = war.startYear();
            ws.casualties = (int) Math.min(Integer.MAX_VALUE, war.priorCasualties()
                    + war.battles().stream().mapToLong(b -> b.attackerLosses() + b.defenderLosses()).sum());
            ws.active = true;
            ws.cause = war.triggerEventId();
            ws.exhaustion = war.warExhaustion();
            state.addWar(war.warId(), ws);
        }
    }

    /**
     * World start (step 14): the wars this world was fighting when it was saved. The engine kept its wars
     * in memory only, so every restart ended them all without a peace, and in single player a war of the
     * world played before went on in the next one. Battles and armies are not part of the save; the war
     * itself, its sides, cause, exhaustion and casualty count are.
     *
     * @return the number of wars restored
     */
    public int restoreFrom(WorldStateManager state) {
        activeWars.clear();
        if (state == null) {
            return 0;
        }
        int restored = 0;
        for (WorldStateManager.WarState ws : state.getAllActiveWars().values()) {
            if (ws == null || ws.warId == null || ws.warId.isBlank() || !ws.active) {
                continue;
            }
            War war = new War(ws.warId, ws.name,
                ws.attackers == null ? List.of() : List.copyOf(ws.attackers),
                ws.defenders == null ? List.of() : List.copyOf(ws.defenders),
                ws.startYear, ws.cause);
            war.increaseExhaustion(ws.exhaustion);
            war.setPriorCasualties(ws.casualties);
            activeWars.put(war.warId(), war);
            restored++;
        }
        return restored;
    }

    public ArmyManager armyManager() { return armyManager; }
    public BattleSimulator battleSimulator() { return battleSimulator; }
    public SiegeEngineController siegeController() { return siegeController; }
}

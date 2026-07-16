package com.eternalengine.war;

import com.eternalengine.core.WorldStateManager;
import com.eternalengine.incident.IncidentSeed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative war engine foundation.
 */
public final class WarEngine {
    private final ConcurrentHashMap<String, War> activeWars = new ConcurrentHashMap<>();
    private final ArmyManager armyManager;
    private final BattleSimulator battleSimulator;

    public WarEngine() {
        this(new ArmyManager(), new BattleSimulator());
    }

    public WarEngine(ArmyManager armyManager, BattleSimulator battleSimulator) {
        this.armyManager = armyManager == null ? new ArmyManager() : armyManager;
        this.battleSimulator = battleSimulator == null ? new BattleSimulator() : battleSimulator;
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
            ws.casualties = (int) war.battles().stream().mapToLong(b -> b.attackerLosses() + b.defenderLosses()).sum();
            ws.active = true;
            state.addWar(war.warId(), ws);
        }
    }

    public ArmyManager armyManager() { return armyManager; }
    public BattleSimulator battleSimulator() { return battleSimulator; }
}

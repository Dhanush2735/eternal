package com.eternalengine.war;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual army manager foundation.
 */
public final class ArmyManager {
    private final ConcurrentHashMap<String, Army> armies = new ConcurrentHashMap<>();

    public Optional<Army> getArmy(String armyId) {
        return Optional.ofNullable(armies.get(armyId));
    }

    public List<Army> getArmiesOf(String civId) {
        List<Army> result = new ArrayList<>();
        for (Army army : armies.values()) {
            if (army.ownerCivId().equals(civId)) {
                result.add(army);
            }
        }
        return List.copyOf(result);
    }

    public void addArmy(Army army) {
        if (army != null) {
            armies.put(army.armyId(), army);
        }
    }

    public void removeArmy(String armyId) {
        if (armyId != null) {
            armies.remove(armyId);
        }
    }

    public void tick(int simYear) {
        for (Army army : armies.values()) {
            Army.MovementOrder order = army.currentOrder();
            if (order != null) {
                army.moveToward(order.targetLat(), order.targetLon(), 1.0d / 365.0d);
            }
        }
    }

    public double calculateSupplyLine(String armyId) {
        Army army = armies.get(armyId);
        if (army == null) {
            return 0.0d;
        }
        return army.supplyLevel() / 100.0d;
    }

    public void issueOrder(String armyId, Army.MovementOrder order) {
        Army army = armies.get(armyId);
        if (army != null) {
            army.setCurrentOrder(order);
        }
    }

    public int size() {
        return armies.size();
    }
}

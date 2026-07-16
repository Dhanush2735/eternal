package com.eternalengine.trade;

import com.eternalengine.core.WorldStateManager;

/**
 * Economy foundation for treasury income/expense calculations.
 */
public final class EconomyEngine {

    public void tick(int simYear, WorldStateManager state) {
        if (state == null) {
            return;
        }
        for (String civId : state.getAllCivilizationIds()) {
            long tax = calculateTaxRevenue(civId, state);
            applyIncome(civId, tax, "tax", state);
            long militaryCost = calculateMilitaryCost(civId, state);
            applyExpense(civId, militaryCost, "military", state);
        }
    }

    public long calculateTaxRevenue(String civId, WorldStateManager state) {
        if (state == null || civId == null) {
            return 0L;
        }
        WorldStateManager.CivilizationState civ = state.getCivilization(civId);
        if (civ == null) {
            return 0L;
        }
        return Math.max(0L, Math.round(civ.population * 0.02d));
    }

    public long calculateMilitaryCost(String civId, WorldStateManager state) {
        if (state == null || civId == null) {
            return 0L;
        }
        int activeWars = state.getAllActiveWars().size();
        return activeWars == 0 ? 0L : activeWars * 1_000L;
    }

    public void applyIncome(String civId, long amount, String source, WorldStateManager state) {
        if (state == null || civId == null || amount <= 0) {
            return;
        }
        state.setTreasury(civId, state.getTreasury(civId) + amount);
        state.setFlag("economy.last_income." + civId, source + ":" + amount);
    }

    public void applyExpense(String civId, long amount, String source, WorldStateManager state) {
        if (state == null || civId == null || amount <= 0) {
            return;
        }
        state.setTreasury(civId, state.getTreasury(civId) - amount);
        state.setFlag("economy.last_expense." + civId, source + ":" + amount);
    }

    public boolean isBankrupt(String civId, WorldStateManager state) {
        return state != null && state.getTreasury(civId) < 0L;
    }

    public long getTreasury(String civId, WorldStateManager state) {
        return state == null ? 0L : state.getTreasury(civId);
    }
}

package com.eternalengine.capability;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface for player simulation data capability.
 */
public interface IPlayerSimulationData {
    
    // ========== VIP Data ==========
    
    /**
     * Get assigned VIP ID, or null if not a VIP
     */
    String getVipId();
    
    /**
     * Set assigned VIP ID
     */
    void setVipId(String vipId);
    
    /**
     * Check if player is a VIP
     */
    boolean isVip();
    
    // ========== Positive Reputation ==========
    
    /**
     * Get positive reputation value for a dimension
     */
    int getPositiveRep(String dimension);
    
    /**
     * Set positive reputation value for a dimension
     */
    void setPositiveRep(String dimension, int value);
    
    /**
     * Modify positive reputation (add/subtract)
     */
    void modifyPositiveRep(String dimension, int delta);
    
    /**
     * Get all positive reputation values
     */
    Map<String, Integer> getAllPositiveRep();
    
    // ========== Negative Reputation ==========
    
    /**
     * Get negative reputation value for a dimension
     */
    int getNegativeRep(String dimension);
    
    /**
     * Set negative reputation value for a dimension
     */
    void setNegativeRep(String dimension, int value);
    
    /**
     * Modify negative reputation (add/subtract)
     */
    void modifyNegativeRep(String dimension, int delta);
    
    /**
     * Get all negative reputation values
     */
    Map<String, Integer> getAllNegativeRep();
    
    // ========== Regional/Faction Reputation ==========
    
    int getRegionalRep(String regionId);
    void setRegionalRep(String regionId, int value);
    Map<String, Integer> getAllRegionalRep();
    
    int getFactionRep(String factionId);
    void setFactionRep(String factionId, int value);
    Map<String, Integer> getAllFactionRep();
    
    // ========== Action History ==========
    
    /**
     * Record a player action
     */
    void recordAction(String actionType, int year, String details);
    
    /**
     * Get recent actions
     */
    List<ActionRecord> getRecentActions(int count);
    
    /**
     * Get actions in year range
     */
    List<ActionRecord> getActionsInRange(int startYear, int endYear);
    
    // ========== Civilization Membership ==========
    
    String getCivilizationId();
    void setCivilizationId(String civId);
    
    // ========== Special Flags ==========
    
    boolean isObserver();
    void setObserver(boolean observer);
    
    boolean isHistorian();
    void setHistorian(boolean historian);
    
    // ========== Tutorial Progress ==========
    
    int getTutorialStep();
    void setTutorialStep(int step);
    boolean isTutorialComplete();
    void completeTutorial();
    
    // ========== Serialization ==========
    
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
    
    /**
     * Copy data from another instance (for respawn)
     */
    void copyFrom(IPlayerSimulationData other);
    
    /**
     * Action record class
     */
    record ActionRecord(String actionType, int year, String details, long timestamp) {
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", actionType);
            tag.putInt("year", year);
            tag.putString("details", details);
            tag.putLong("timestamp", timestamp);
            return tag;
        }
        
        public static ActionRecord fromNBT(CompoundTag tag) {
            return new ActionRecord(
                tag.getString("type"),
                tag.getInt("year"),
                tag.getString("details"),
                tag.getLong("timestamp")
            );
        }
    }
}

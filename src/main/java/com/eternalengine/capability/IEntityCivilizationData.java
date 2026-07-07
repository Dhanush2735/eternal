package com.eternalengine.capability;

import net.minecraft.nbt.CompoundTag;

/**
 * Interface for entity civilization data capability.
 * Used for villagers and other civilization-aware entities.
 */
public interface IEntityCivilizationData {
    
    /**
     * Get civilization ID this entity belongs to
     */
    String getCivilizationId();
    
    /**
     * Set civilization ID
     */
    void setCivilizationId(String civId);
    
    /**
     * Get entity role (FARMER, MERCHANT, GUARD, SCHOLAR, etc.)
     */
    String getRole();
    
    /**
     * Set entity role
     */
    void setRole(String role);
    
    /**
     * Get entity name
     */
    String getEntityName();
    
    /**
     * Set entity name
     */
    void setEntityName(String name);
    
    /**
     * Get loyalty to current leader (0-100)
     */
    int getLoyalty();
    
    /**
     * Set loyalty
     */
    void setLoyalty(int loyalty);
    
    /**
     * Modify loyalty
     */
    void modifyLoyalty(int delta);
    
    /**
     * Check if entity is virtualized (stored as data, not spawned)
     */
    boolean isVirtual();
    
    /**
     * Set virtual flag
     */
    void setVirtual(boolean virtual);
    
    /**
     * Get the simulation year when this entity was last updated
     */
    int getLastKnownYear();
    
    /**
     * Set last known year
     */
    void setLastKnownYear(int year);
    
    /**
     * Serialize to NBT
     */
    CompoundTag serializeNBT();
    
    /**
     * Deserialize from NBT
     */
    void deserializeNBT(CompoundTag nbt);
}

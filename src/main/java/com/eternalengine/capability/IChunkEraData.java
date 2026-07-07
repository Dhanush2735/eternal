package com.eternalengine.capability;

import net.minecraft.nbt.CompoundTag;

import java.util.Set;

/**
 * Interface for chunk era data capability.
 * Stores era, ownership, and building information per chunk.
 */
public interface IChunkEraData {
    
    /**
     * Get the era of this chunk
     */
    String getEra();
    
    /**
     * Set the era
     */
    void setEra(String era);
    
    /**
     * Get the owning civilization ID
     */
    String getOwnerCivId();
    
    /**
     * Set the owning civilization
     */
    void setOwnerCivId(String civId);
    
    /**
     * Get building IDs in this chunk
     */
    Set<String> getBuildingIds();
    
    /**
     * Add a building ID
     */
    void addBuildingId(String buildingId);
    
    /**
     * Remove a building ID
     */
    void removeBuildingId(String buildingId);
    
    /**
     * Get war damage level (0-100)
     */
    int getWarDamage();
    
    /**
     * Set war damage level
     */
    void setWarDamage(int damage);
    
    /**
     * Get the simulation year when this chunk was last updated
     */
    int getLastUpdatedYear();
    
    /**
     * Set last updated year
     */
    void setLastUpdatedYear(int year);
    
    /**
     * Serialize to NBT
     */
    CompoundTag serializeNBT();
    
    /**
     * Deserialize from NBT
     */
    void deserializeNBT(CompoundTag nbt);
}

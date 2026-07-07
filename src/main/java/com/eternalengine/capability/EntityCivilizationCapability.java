package com.eternalengine.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of entity civilization data capability.
 */
public class EntityCivilizationCapability implements IEntityCivilizationData {
    
    private String civilizationId = null;
    private String role = "PEASANT";
    private String entityName = null;
    private int loyalty = 50;
    private boolean virtual = false;
    private int lastKnownYear = 0;
    
    @Override
    public String getCivilizationId() {
        return civilizationId;
    }
    
    @Override
    public void setCivilizationId(String civId) {
        this.civilizationId = civId;
    }
    
    @Override
    public String getRole() {
        return role;
    }
    
    @Override
    public void setRole(String role) {
        this.role = role != null ? role : "PEASANT";
    }
    
    @Override
    public String getEntityName() {
        return entityName;
    }
    
    @Override
    public void setEntityName(String name) {
        this.entityName = name;
    }
    
    @Override
    public int getLoyalty() {
        return loyalty;
    }
    
    @Override
    public void setLoyalty(int loyalty) {
        this.loyalty = Math.max(0, Math.min(100, loyalty));
    }
    
    @Override
    public void modifyLoyalty(int delta) {
        setLoyalty(this.loyalty + delta);
    }
    
    @Override
    public boolean isVirtual() {
        return virtual;
    }
    
    @Override
    public void setVirtual(boolean virtual) {
        this.virtual = virtual;
    }
    
    @Override
    public int getLastKnownYear() {
        return lastKnownYear;
    }
    
    @Override
    public void setLastKnownYear(int year) {
        this.lastKnownYear = year;
    }
    
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        
        if (civilizationId != null) {
            nbt.putString("civId", civilizationId);
        }
        nbt.putString("role", role);
        if (entityName != null) {
            nbt.putString("name", entityName);
        }
        nbt.putInt("loyalty", loyalty);
        nbt.putBoolean("virtual", virtual);
        nbt.putInt("lastYear", lastKnownYear);
        
        return nbt;
    }
    
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("civId")) {
            civilizationId = nbt.getString("civId");
        }
        if (nbt.contains("role")) {
            role = nbt.getString("role");
        }
        if (nbt.contains("name")) {
            entityName = nbt.getString("name");
        }
        loyalty = nbt.getInt("loyalty");
        virtual = nbt.getBoolean("virtual");
        lastKnownYear = nbt.getInt("lastYear");
    }
    
    /**
     * Capability provider
     */
    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        
        private final EntityCivilizationCapability capability = new EntityCivilizationCapability();
        private final LazyOptional<IEntityCivilizationData> optional = LazyOptional.of(() -> capability);
        
        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return EternalEngineCapabilities.ENTITY_CIVILIZATION.orEmpty(cap, optional);
        }
        
        @Override
        public CompoundTag serializeNBT() {
            return capability.serializeNBT();
        }
        
        @Override
        public void deserializeNBT(CompoundTag nbt) {
            capability.deserializeNBT(nbt);
        }
    }
}

package com.eternalengine.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of chunk era data capability.
 */
public class ChunkEraCapability implements IChunkEraData {
    
    private String era = "UNKNOWN";
    private String ownerCivId = null;
    private final Set<String> buildingIds = new HashSet<>();
    private int warDamage = 0;
    private int lastUpdatedYear = 0;
    
    @Override
    public String getEra() {
        return era;
    }
    
    @Override
    public void setEra(String era) {
        this.era = era != null ? era : "UNKNOWN";
    }
    
    @Override
    public String getOwnerCivId() {
        return ownerCivId;
    }
    
    @Override
    public void setOwnerCivId(String civId) {
        this.ownerCivId = civId;
    }
    
    @Override
    public Set<String> getBuildingIds() {
        return new HashSet<>(buildingIds);
    }
    
    @Override
    public void addBuildingId(String buildingId) {
        if (buildingId != null) {
            buildingIds.add(buildingId);
        }
    }
    
    @Override
    public void removeBuildingId(String buildingId) {
        buildingIds.remove(buildingId);
    }
    
    @Override
    public int getWarDamage() {
        return warDamage;
    }
    
    @Override
    public void setWarDamage(int damage) {
        this.warDamage = Math.max(0, Math.min(100, damage));
    }
    
    @Override
    public int getLastUpdatedYear() {
        return lastUpdatedYear;
    }
    
    @Override
    public void setLastUpdatedYear(int year) {
        this.lastUpdatedYear = year;
    }
    
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        
        nbt.putString("era", era);
        if (ownerCivId != null) {
            nbt.putString("owner", ownerCivId);
        }
        
        ListTag buildingsTag = new ListTag();
        for (String id : buildingIds) {
            buildingsTag.add(StringTag.valueOf(id));
        }
        nbt.put("buildings", buildingsTag);
        
        nbt.putInt("warDamage", warDamage);
        nbt.putInt("lastYear", lastUpdatedYear);
        
        return nbt;
    }
    
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("era")) {
            era = nbt.getString("era");
        }
        if (nbt.contains("owner")) {
            ownerCivId = nbt.getString("owner");
        }
        
        buildingIds.clear();
        if (nbt.contains("buildings")) {
            ListTag buildingsTag = nbt.getList("buildings", Tag.TAG_STRING);
            for (int i = 0; i < buildingsTag.size(); i++) {
                buildingIds.add(buildingsTag.getString(i));
            }
        }
        
        warDamage = nbt.getInt("warDamage");
        lastUpdatedYear = nbt.getInt("lastYear");
    }
    
    /**
     * Capability provider
     */
    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        
        private final ChunkEraCapability capability = new ChunkEraCapability();
        private final LazyOptional<IChunkEraData> optional = LazyOptional.of(() -> capability);
        
        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return EternalEngineCapabilities.CHUNK_ERA.orEmpty(cap, optional);
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

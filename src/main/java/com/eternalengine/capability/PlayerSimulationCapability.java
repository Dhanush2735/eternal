package com.eternalengine.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of player simulation data capability.
 * Persists across death and syncs with client.
 */
public class PlayerSimulationCapability implements IPlayerSimulationData {
    
    // VIP data
    private String vipId = null;
    
    // Reputation data
    private final Map<String, Integer> positiveRep = new ConcurrentHashMap<>();
    private final Map<String, Integer> negativeRep = new ConcurrentHashMap<>();
    private final Map<String, Integer> regionalRep = new ConcurrentHashMap<>();
    private final Map<String, Integer> factionRep = new ConcurrentHashMap<>();
    
    // Action history
    private final List<ActionRecord> actionHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY = 1000;
    
    // Civilization
    private String civilizationId = null;
    
    // Flags
    private boolean observer = false;
    private boolean historian = false;
    
    // Tutorial
    private int tutorialStep = 0;
    private boolean tutorialComplete = false;
    
    public PlayerSimulationCapability() {
        // Initialize default reputation values
        initializeDefaults();
    }
    
    private void initializeDefaults() {
        // Positive dimensions all start at 0
        positiveRep.put("REP_MILITARY", 0);
        positiveRep.put("REP_POLITICAL", 0);
        positiveRep.put("REP_ECONOMIC", 0);
        positiveRep.put("REP_RELIGIOUS", 0);
        positiveRep.put("REP_CULTURAL", 0);
        positiveRep.put("REP_PERSONAL", 0);
        
        // Negative dimensions all start at 0
        negativeRep.put("NEG_INFAMY", 0);
        negativeRep.put("NEG_COWARDICE", 0);
        negativeRep.put("NEG_TREACHERY", 0);
        negativeRep.put("NEG_CORRUPTION", 0);
        negativeRep.put("NEG_CRUELTY", 0);
        negativeRep.put("NEG_INCOMPETENCE", 0);
        negativeRep.put("NEG_HERESY", 0);
        negativeRep.put("NEG_DISHONOR", 0);
        negativeRep.put("NEG_TYRANNY", 0);
        negativeRep.put("NEG_GREED", 0);
        negativeRep.put("NEG_WEAKNESS", 0);
        negativeRep.put("NEG_PARANOIA", 0);
        negativeRep.put("NEG_HUBRIS", 0);
        negativeRep.put("NEG_INSTABILITY", 0);
    }
    
    // ========== VIP Implementation ==========
    
    @Override
    public String getVipId() {
        return vipId;
    }
    
    @Override
    public void setVipId(String vipId) {
        this.vipId = vipId;
    }
    
    @Override
    public boolean isVip() {
        return vipId != null && !vipId.isEmpty();
    }
    
    // ========== Positive Reputation ==========
    
    @Override
    public int getPositiveRep(String dimension) {
        return positiveRep.getOrDefault(dimension, 0);
    }
    
    @Override
    public void setPositiveRep(String dimension, int value) {
        positiveRep.put(dimension, Math.max(0, Math.min(100, value)));
    }
    
    @Override
    public void modifyPositiveRep(String dimension, int delta) {
        setPositiveRep(dimension, getPositiveRep(dimension) + delta);
    }
    
    @Override
    public Map<String, Integer> getAllPositiveRep() {
        return new HashMap<>(positiveRep);
    }
    
    // ========== Negative Reputation ==========
    
    @Override
    public int getNegativeRep(String dimension) {
        return negativeRep.getOrDefault(dimension, 0);
    }
    
    @Override
    public void setNegativeRep(String dimension, int value) {
        negativeRep.put(dimension, Math.max(0, Math.min(100, value)));
    }
    
    @Override
    public void modifyNegativeRep(String dimension, int delta) {
        setNegativeRep(dimension, getNegativeRep(dimension) + delta);
    }
    
    @Override
    public Map<String, Integer> getAllNegativeRep() {
        return new HashMap<>(negativeRep);
    }
    
    // ========== Regional/Faction Reputation ==========
    
    @Override
    public int getRegionalRep(String regionId) {
        return regionalRep.getOrDefault(regionId, 0);
    }
    
    @Override
    public void setRegionalRep(String regionId, int value) {
        regionalRep.put(regionId, Math.max(-100, Math.min(100, value)));
    }
    
    @Override
    public Map<String, Integer> getAllRegionalRep() {
        return new HashMap<>(regionalRep);
    }
    
    @Override
    public int getFactionRep(String factionId) {
        return factionRep.getOrDefault(factionId, 0);
    }
    
    @Override
    public void setFactionRep(String factionId, int value) {
        factionRep.put(factionId, Math.max(-100, Math.min(100, value)));
    }
    
    @Override
    public Map<String, Integer> getAllFactionRep() {
        return new HashMap<>(factionRep);
    }
    
    // ========== Action History ==========
    
    @Override
    public void recordAction(String actionType, int year, String details) {
        ActionRecord record = new ActionRecord(actionType, year, details, System.currentTimeMillis());
        actionHistory.add(record);
        
        // Trim history if too large
        while (actionHistory.size() > MAX_HISTORY) {
            actionHistory.remove(0);
        }
    }
    
    @Override
    public List<ActionRecord> getRecentActions(int count) {
        int start = Math.max(0, actionHistory.size() - count);
        return new ArrayList<>(actionHistory.subList(start, actionHistory.size()));
    }
    
    @Override
    public List<ActionRecord> getActionsInRange(int startYear, int endYear) {
        return actionHistory.stream()
            .filter(a -> a.year() >= startYear && a.year() <= endYear)
            .toList();
    }
    
    // ========== Civilization ==========
    
    @Override
    public String getCivilizationId() {
        return civilizationId;
    }
    
    @Override
    public void setCivilizationId(String civId) {
        this.civilizationId = civId;
    }
    
    // ========== Flags ==========
    
    @Override
    public boolean isObserver() {
        return observer;
    }
    
    @Override
    public void setObserver(boolean observer) {
        this.observer = observer;
    }
    
    @Override
    public boolean isHistorian() {
        return historian;
    }
    
    @Override
    public void setHistorian(boolean historian) {
        this.historian = historian;
    }
    
    // ========== Tutorial ==========
    
    @Override
    public int getTutorialStep() {
        return tutorialStep;
    }
    
    @Override
    public void setTutorialStep(int step) {
        this.tutorialStep = step;
    }
    
    @Override
    public boolean isTutorialComplete() {
        return tutorialComplete;
    }
    
    @Override
    public void completeTutorial() {
        this.tutorialComplete = true;
        this.tutorialStep = 7;
    }
    
    // ========== Serialization ==========
    
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        
        // VIP
        if (vipId != null) {
            nbt.putString("vipId", vipId);
        }
        
        // Positive rep
        CompoundTag posTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : positiveRep.entrySet()) {
            posTag.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("positiveRep", posTag);
        
        // Negative rep
        CompoundTag negTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : negativeRep.entrySet()) {
            negTag.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("negativeRep", negTag);
        
        // Regional rep
        CompoundTag regTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : regionalRep.entrySet()) {
            regTag.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("regionalRep", regTag);
        
        // Faction rep
        CompoundTag facTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : factionRep.entrySet()) {
            facTag.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("factionRep", facTag);
        
        // Action history (last 100 only)
        ListTag historyTag = new ListTag();
        int start = Math.max(0, actionHistory.size() - 100);
        for (int i = start; i < actionHistory.size(); i++) {
            historyTag.add(actionHistory.get(i).toNBT());
        }
        nbt.put("actionHistory", historyTag);
        
        // Civilization
        if (civilizationId != null) {
            nbt.putString("civilizationId", civilizationId);
        }
        
        // Flags
        nbt.putBoolean("observer", observer);
        nbt.putBoolean("historian", historian);
        
        // Tutorial
        nbt.putInt("tutorialStep", tutorialStep);
        nbt.putBoolean("tutorialComplete", tutorialComplete);
        
        return nbt;
    }
    
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // VIP
        if (nbt.contains("vipId")) {
            vipId = nbt.getString("vipId");
        }
        
        // Positive rep
        if (nbt.contains("positiveRep")) {
            CompoundTag posTag = nbt.getCompound("positiveRep");
            for (String key : posTag.getAllKeys()) {
                positiveRep.put(key, posTag.getInt(key));
            }
        }
        
        // Negative rep
        if (nbt.contains("negativeRep")) {
            CompoundTag negTag = nbt.getCompound("negativeRep");
            for (String key : negTag.getAllKeys()) {
                negativeRep.put(key, negTag.getInt(key));
            }
        }
        
        // Regional rep
        if (nbt.contains("regionalRep")) {
            CompoundTag regTag = nbt.getCompound("regionalRep");
            for (String key : regTag.getAllKeys()) {
                regionalRep.put(key, regTag.getInt(key));
            }
        }
        
        // Faction rep
        if (nbt.contains("factionRep")) {
            CompoundTag facTag = nbt.getCompound("factionRep");
            for (String key : facTag.getAllKeys()) {
                factionRep.put(key, facTag.getInt(key));
            }
        }
        
        // Action history
        if (nbt.contains("actionHistory")) {
            ListTag historyTag = nbt.getList("actionHistory", Tag.TAG_COMPOUND);
            actionHistory.clear();
            for (int i = 0; i < historyTag.size(); i++) {
                actionHistory.add(ActionRecord.fromNBT(historyTag.getCompound(i)));
            }
        }
        
        // Civilization
        if (nbt.contains("civilizationId")) {
            civilizationId = nbt.getString("civilizationId");
        }
        
        // Flags
        observer = nbt.getBoolean("observer");
        historian = nbt.getBoolean("historian");
        
        // Tutorial
        tutorialStep = nbt.getInt("tutorialStep");
        tutorialComplete = nbt.getBoolean("tutorialComplete");
    }
    
    @Override
    public void copyFrom(IPlayerSimulationData other) {
        this.deserializeNBT(other.serializeNBT());
    }
    
    /**
     * Capability provider
     */
    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        
        private final PlayerSimulationCapability capability = new PlayerSimulationCapability();
        private final LazyOptional<IPlayerSimulationData> optional = LazyOptional.of(() -> capability);
        
        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return EternalEngineCapabilities.PLAYER_SIMULATION.orEmpty(cap, optional);
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

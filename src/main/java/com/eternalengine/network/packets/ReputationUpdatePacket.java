package com.eternalengine.network.packets;

import com.eternalengine.EternalEngineMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Packet sent from server to client containing player reputation data.
 * Contains both positive and negative reputation dimensions.
 */
public class ReputationUpdatePacket implements BasePacket {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    // Positive reputation dimensions (0-100 each)
    private Map<String, Integer> positiveRep;
    
    // Negative reputation dimensions (0-100 each)
    private Map<String, Integer> negativeRep;
    
    // Regional reputation (per region)
    private Map<String, Integer> regionalRep;
    
    // Faction reputation (per faction)
    private Map<String, Integer> factionRep;
    
    // Active obligations
    private java.util.List<String> activeObligations;
    
    // Active reputation traps
    private java.util.List<String> activeTraps;
    
    public ReputationUpdatePacket() {
        this.positiveRep = new HashMap<>();
        this.negativeRep = new HashMap<>();
        this.regionalRep = new HashMap<>();
        this.factionRep = new HashMap<>();
        this.activeObligations = new java.util.ArrayList<>();
        this.activeTraps = new java.util.ArrayList<>();
    }
    
    public ReputationUpdatePacket(Map<String, Integer> positiveRep,
                                   Map<String, Integer> negativeRep,
                                   Map<String, Integer> regionalRep,
                                   Map<String, Integer> factionRep,
                                   java.util.List<String> activeObligations,
                                   java.util.List<String> activeTraps) {
        this.positiveRep = positiveRep != null ? positiveRep : new HashMap<>();
        this.negativeRep = negativeRep != null ? negativeRep : new HashMap<>();
        this.regionalRep = regionalRep != null ? regionalRep : new HashMap<>();
        this.factionRep = factionRep != null ? factionRep : new HashMap<>();
        this.activeObligations = activeObligations != null ? activeObligations : new java.util.ArrayList<>();
        this.activeTraps = activeTraps != null ? activeTraps : new java.util.ArrayList<>();
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        // Encode positive rep
        buf.writeInt(positiveRep.size());
        for (Map.Entry<String, Integer> entry : positiveRep.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        
        // Encode negative rep
        buf.writeInt(negativeRep.size());
        for (Map.Entry<String, Integer> entry : negativeRep.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        
        // Encode regional rep
        buf.writeInt(regionalRep.size());
        for (Map.Entry<String, Integer> entry : regionalRep.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        
        // Encode faction rep
        buf.writeInt(factionRep.size());
        for (Map.Entry<String, Integer> entry : factionRep.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        
        // Encode obligations
        buf.writeInt(activeObligations.size());
        for (String obligation : activeObligations) {
            buf.writeUtf(obligation);
        }
        
        // Encode traps
        buf.writeInt(activeTraps.size());
        for (String trap : activeTraps) {
            buf.writeUtf(trap);
        }
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        // Decode positive rep
        int positiveCount = buf.readInt();
        positiveRep = new HashMap<>(positiveCount);
        for (int i = 0; i < positiveCount; i++) {
            positiveRep.put(buf.readUtf(64), buf.readInt());
        }
        
        // Decode negative rep
        int negativeCount = buf.readInt();
        negativeRep = new HashMap<>(negativeCount);
        for (int i = 0; i < negativeCount; i++) {
            negativeRep.put(buf.readUtf(64), buf.readInt());
        }
        
        // Decode regional rep
        int regionalCount = buf.readInt();
        regionalRep = new HashMap<>(regionalCount);
        for (int i = 0; i < regionalCount; i++) {
            regionalRep.put(buf.readUtf(64), buf.readInt());
        }
        
        // Decode faction rep
        int factionCount = buf.readInt();
        factionRep = new HashMap<>(factionCount);
        for (int i = 0; i < factionCount; i++) {
            factionRep.put(buf.readUtf(64), buf.readInt());
        }
        
        // Decode obligations
        int obligationCount = buf.readInt();
        activeObligations = new java.util.ArrayList<>(obligationCount);
        for (int i = 0; i < obligationCount; i++) {
            activeObligations.add(buf.readUtf(256));
        }
        
        // Decode traps
        int trapCount = buf.readInt();
        activeTraps = new java.util.ArrayList<>(trapCount);
        for (int i = 0; i < trapCount; i++) {
            activeTraps.add(buf.readUtf(256));
        }
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        // Client-side handling - update reputation display
        LOGGER.debug("[Client] Received reputation update");
    }
    
    // Getters
    public Map<String, Integer> getPositiveRep() { return positiveRep; }
    public Map<String, Integer> getNegativeRep() { return negativeRep; }
    public Map<String, Integer> getRegionalRep() { return regionalRep; }
    public Map<String, Integer> getFactionRep() { return factionRep; }
    public java.util.List<String> getActiveObligations() { return activeObligations; }
    public java.util.List<String> getActiveTraps() { return activeTraps; }
    
    // Helper methods
    public int getPositive(String dimension) {
        return positiveRep.getOrDefault(dimension, 0);
    }
    
    public int getNegative(String dimension) {
        return negativeRep.getOrDefault(dimension, 0);
    }
}

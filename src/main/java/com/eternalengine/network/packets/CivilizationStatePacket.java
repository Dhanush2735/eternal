package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Packet containing civilization state data.
 */
public class CivilizationStatePacket implements BasePacket {
    
    private String civId;
    private String name;
    private long population;
    private long treasury;
    private int techLevel;
    private String techEra;
    private String religionId;
    private String governmentType;
    private boolean isActive;
    private Map<String, Integer> relations;
    private String[] territories;
    
    public CivilizationStatePacket() {
        this.relations = new HashMap<>();
        this.territories = new String[0];
    }
    
    public CivilizationStatePacket(String civId, String name, long population, long treasury,
                                    int techLevel, String techEra, String religionId,
                                    String governmentType, boolean isActive,
                                    Map<String, Integer> relations, String[] territories) {
        this.civId = civId;
        this.name = name;
        this.population = population;
        this.treasury = treasury;
        this.techLevel = techLevel;
        this.techEra = techEra;
        this.religionId = religionId;
        this.governmentType = governmentType;
        this.isActive = isActive;
        this.relations = relations != null ? relations : new HashMap<>();
        this.territories = territories != null ? territories : new String[0];
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(civId != null ? civId : "");
        buf.writeUtf(name != null ? name : "");
        buf.writeLong(population);
        buf.writeLong(treasury);
        buf.writeInt(techLevel);
        buf.writeUtf(techEra != null ? techEra : "");
        buf.writeUtf(religionId != null ? religionId : "");
        buf.writeUtf(governmentType != null ? governmentType : "");
        buf.writeBoolean(isActive);
        
        buf.writeInt(relations.size());
        for (Map.Entry<String, Integer> entry : relations.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        
        buf.writeInt(territories.length);
        for (String territory : territories) {
            buf.writeUtf(territory);
        }
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        civId = buf.readUtf(64);
        name = buf.readUtf(256);
        population = buf.readLong();
        treasury = buf.readLong();
        techLevel = buf.readInt();
        techEra = buf.readUtf(32);
        religionId = buf.readUtf(64);
        governmentType = buf.readUtf(64);
        isActive = buf.readBoolean();
        
        int relCount = buf.readInt();
        relations = new HashMap<>(relCount);
        for (int i = 0; i < relCount; i++) {
            relations.put(buf.readUtf(64), buf.readInt());
        }
        
        int terrCount = buf.readInt();
        territories = new String[terrCount];
        for (int i = 0; i < terrCount; i++) {
            territories[i] = buf.readUtf(64);
        }
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        // Client-side: update civilization display
    }
    
    // Getters
    public String getCivId() { return civId; }
    public String getName() { return name; }
    public long getPopulation() { return population; }
    public long getTreasury() { return treasury; }
    public int getTechLevel() { return techLevel; }
    public String getTechEra() { return techEra; }
    public String getReligionId() { return religionId; }
    public String getGovernmentType() { return governmentType; }
    public boolean isActive() { return isActive; }
    public Map<String, Integer> getRelations() { return relations; }
    public String[] getTerritories() { return territories; }
}

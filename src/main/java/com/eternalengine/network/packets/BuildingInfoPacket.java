package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet containing building information.
 */
public class BuildingInfoPacket implements BasePacket {
    
    private String buildingId;
    private String osmId;
    private String wikidataId;
    private String name;
    private double latitude;
    private double longitude;
    private int constructionYear;
    private String constructionEra;
    private String originalPurpose;
    private String currentPurpose;
    private String currentState;
    private int damagePercent;
    private String ownerCivId;
    private int culturalSignificance;
    private List<StateChange> stateHistory;
    
    public BuildingInfoPacket() {
        this.stateHistory = new ArrayList<>();
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(buildingId != null ? buildingId : "");
        buf.writeUtf(osmId != null ? osmId : "");
        buf.writeUtf(wikidataId != null ? wikidataId : "");
        buf.writeUtf(name != null ? name : "");
        buf.writeDouble(latitude);
        buf.writeDouble(longitude);
        buf.writeInt(constructionYear);
        buf.writeUtf(constructionEra != null ? constructionEra : "");
        buf.writeUtf(originalPurpose != null ? originalPurpose : "");
        buf.writeUtf(currentPurpose != null ? currentPurpose : "");
        buf.writeUtf(currentState != null ? currentState : "");
        buf.writeInt(damagePercent);
        buf.writeUtf(ownerCivId != null ? ownerCivId : "");
        buf.writeInt(culturalSignificance);
        
        buf.writeInt(stateHistory.size());
        for (StateChange change : stateHistory) {
            buf.writeInt(change.year);
            buf.writeUtf(change.type);
            buf.writeUtf(change.description);
        }
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        buildingId = buf.readUtf(128);
        osmId = buf.readUtf(64);
        wikidataId = buf.readUtf(32);
        name = buf.readUtf(256);
        latitude = buf.readDouble();
        longitude = buf.readDouble();
        constructionYear = buf.readInt();
        constructionEra = buf.readUtf(32);
        originalPurpose = buf.readUtf(64);
        currentPurpose = buf.readUtf(64);
        currentState = buf.readUtf(32);
        damagePercent = buf.readInt();
        ownerCivId = buf.readUtf(64);
        culturalSignificance = buf.readInt();
        
        int count = buf.readInt();
        stateHistory = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StateChange change = new StateChange();
            change.year = buf.readInt();
            change.type = buf.readUtf(32);
            change.description = buf.readUtf(512);
            stateHistory.add(change);
        }
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        // Client-side: display building info GUI
    }
    
    // Getters
    public String getBuildingId() { return buildingId; }
    public String getName() { return name; }
    public int getConstructionYear() { return constructionYear; }
    public String getCurrentState() { return currentState; }
    public List<StateChange> getStateHistory() { return stateHistory; }
    
    public static class StateChange {
        public int year;
        public String type;
        public String description;
    }
}

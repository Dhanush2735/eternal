package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Packet sent from client to server to request specific data.
 */
public class RequestDataPacket implements BasePacket {
    
    public enum RequestType {
        SIMULATION_STATE,
        REPUTATION,
        VIP_STATUS,
        BUILDING_INFO,
        CIVILIZATION_INFO,
        HISTORY_LOG,
        MAP_OVERLAY
    }
    
    private RequestType requestType;
    private String targetId; // Building ID, Civ ID, etc.
    private int yearStart; // For history log requests
    private int yearEnd;
    
    public RequestDataPacket() {}
    
    public RequestDataPacket(RequestType requestType, String targetId) {
        this.requestType = requestType;
        this.targetId = targetId;
    }
    
    public RequestDataPacket(RequestType requestType, int yearStart, int yearEnd) {
        this.requestType = requestType;
        this.yearStart = yearStart;
        this.yearEnd = yearEnd;
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(requestType);
        buf.writeUtf(targetId != null ? targetId : "");
        buf.writeInt(yearStart);
        buf.writeInt(yearEnd);
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        requestType = buf.readEnum(RequestType.class);
        targetId = buf.readUtf(256);
        yearStart = buf.readInt();
        yearEnd = buf.readInt();
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        
        // Process request and send appropriate response packet
        // DataRequestHandler.handleRequest(player, this);
    }
    
    public RequestType getRequestType() { return requestType; }
    public String getTargetId() { return targetId; }
    public int getYearStart() { return yearStart; }
    public int getYearEnd() { return yearEnd; }
}

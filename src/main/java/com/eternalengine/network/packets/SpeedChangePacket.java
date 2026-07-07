package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Packet sent when simulation speed changes.
 */
public class SpeedChangePacket implements BasePacket {
    
    private int newSpeed;
    private int previousSpeed;
    private String reason;
    private boolean isAutoSlowdown;
    private boolean isVipLock;
    private boolean isOverridable;
    
    public SpeedChangePacket() {}
    
    public SpeedChangePacket(int newSpeed, int previousSpeed, String reason,
                              boolean isAutoSlowdown, boolean isVipLock, boolean isOverridable) {
        this.newSpeed = newSpeed;
        this.previousSpeed = previousSpeed;
        this.reason = reason;
        this.isAutoSlowdown = isAutoSlowdown;
        this.isVipLock = isVipLock;
        this.isOverridable = isOverridable;
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(newSpeed);
        buf.writeInt(previousSpeed);
        buf.writeUtf(reason != null ? reason : "");
        buf.writeBoolean(isAutoSlowdown);
        buf.writeBoolean(isVipLock);
        buf.writeBoolean(isOverridable);
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        newSpeed = buf.readInt();
        previousSpeed = buf.readInt();
        reason = buf.readUtf(512);
        isAutoSlowdown = buf.readBoolean();
        isVipLock = buf.readBoolean();
        isOverridable = buf.readBoolean();
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        // Client-side: update speed display
    }
    
    public int getNewSpeed() { return newSpeed; }
    public int getPreviousSpeed() { return previousSpeed; }
    public String getReason() { return reason; }
    public boolean isAutoSlowdown() { return isAutoSlowdown; }
    public boolean isVipLock() { return isVipLock; }
    public boolean isOverridable() { return isOverridable; }
}

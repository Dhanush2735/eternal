package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Base interface for all Eternal Engine network packets.
 * All packets must implement encode, decode, and handle methods.
 */
public interface BasePacket {
    
    /**
     * Encode the packet data to the buffer
     */
    void encode(FriendlyByteBuf buf);
    
    /**
     * Decode the packet data from the buffer
     */
    void decode(FriendlyByteBuf buf);
    
    /**
     * Handle the packet on the receiving side
     */
    void handle(NetworkEvent.Context context);
}

package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Packet sent from client to server when a player votes for speed change.
 */
public class SpeedVotePacket implements BasePacket {
    
    private int votedSpeed;
    private boolean isOverrideVote; // Voting to override current slowdown
    private boolean isRestoreVote; // Voting to restore slowdown
    
    public SpeedVotePacket() {}
    
    public SpeedVotePacket(int votedSpeed, boolean isOverrideVote, boolean isRestoreVote) {
        this.votedSpeed = votedSpeed;
        this.isOverrideVote = isOverrideVote;
        this.isRestoreVote = isRestoreVote;
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(votedSpeed);
        buf.writeBoolean(isOverrideVote);
        buf.writeBoolean(isRestoreVote);
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        votedSpeed = buf.readInt();
        isOverrideVote = buf.readBoolean();
        isRestoreVote = buf.readBoolean();
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        
        // Process vote through SimulationClockManager
        // SimulationClockManager.getInstance().processVote(player, this);
    }
    
    public int getVotedSpeed() { return votedSpeed; }
    public boolean isOverrideVote() { return isOverrideVote; }
    public boolean isRestoreVote() { return isRestoreVote; }
}

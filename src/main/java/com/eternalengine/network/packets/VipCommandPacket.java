package com.eternalengine.network.packets;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IPlayerSimulationData;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.vip.VipSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Packet sent from client to server when a VIP player issues a command.
 */
public class VipCommandPacket implements BasePacket {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private String commandType; // MILITARY, POLITICAL, ECONOMIC, RELIGIOUS
    private String commandId;   // MOVE_ARMY, DECLARE_WAR, etc.
    private String targetId;    // Target civ, army, location, etc.
    private Map<String, String> parameters;
    
    public VipCommandPacket() {
        this.parameters = new HashMap<>();
    }
    
    public VipCommandPacket(String commandType, String commandId, String targetId, 
                            Map<String, String> parameters) {
        this.commandType = commandType;
        this.commandId = commandId;
        this.targetId = targetId;
        this.parameters = parameters != null ? parameters : new HashMap<>();
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(commandType != null ? commandType : "");
        buf.writeUtf(commandId != null ? commandId : "");
        buf.writeUtf(targetId != null ? targetId : "");
        
        buf.writeInt(parameters.size());
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        commandType = buf.readUtf(64);
        commandId = buf.readUtf(64);
        targetId = buf.readUtf(256);
        
        int paramCount = buf.readInt();
        parameters = new HashMap<>(paramCount);
        for (int i = 0; i < paramCount; i++) {
            parameters.put(buf.readUtf(64), buf.readUtf(512));
        }
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        
        LOGGER.info("[VipCommand] {} issued command: {} / {} -> {}",
            player.getName().getString(), commandType, commandId, targetId);
        
        if (!SimulationEngine.isInitialized()) {
            return;
        }
        IPlayerSimulationData data = EternalEngineCapabilities.getPlayerData(player);
        if (!data.isVip()) {
            LOGGER.warn("[VipCommand] Rejected command from non-VIP player {}", player.getName().getString());
            return;
        }
        VipSlot vip = SimulationEngine.getInstance().getVipSystem().getVip(data.getVipId()).orElse(null);
        if (vip == null) {
            LOGGER.warn("[VipCommand] Rejected command because VIP slot {} is missing", data.getVipId());
            return;
        }
        Map<String, Object> params = new HashMap<>(parameters);
        params.put("targetId", targetId == null ? "" : targetId);
        var result = SimulationEngine.getInstance().getVipCommandSystem()
            .executeCommand(vip, commandId, params, player.getServer(), com.eternalengine.core.WorldStateManager.getInstance());
        if (!result.success()) {
            LOGGER.warn("[VipCommand] Command rejected: {}", result.message());
        }
    }
    
    // Getters
    public String getCommandType() { return commandType; }
    public String getCommandId() { return commandId; }
    public String getTargetId() { return targetId; }
    public Map<String, String> getParameters() { return parameters; }
    public String getParameter(String key) { return parameters.get(key); }
}

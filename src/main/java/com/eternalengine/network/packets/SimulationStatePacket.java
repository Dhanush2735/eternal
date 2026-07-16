package com.eternalengine.network.packets;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet sent from server to client containing simulation state.
 * Sent on player login and when major state changes occur.
 */
public class SimulationStatePacket implements BasePacket {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private int currentYear;
    private int effectiveSpeed;
    private int baseSpeed;
    private String activeTimelineBranch;
    private boolean isPaused;
    private boolean isSlowdownActive;
    private String slowdownReason;
    private List<String> activeEvents;
    
    // Default constructor for packet registration
    public SimulationStatePacket() {
        this.activeEvents = new ArrayList<>();
    }
    
    // Full constructor
    public SimulationStatePacket(int currentYear, int effectiveSpeed, int baseSpeed,
                                  String activeTimelineBranch, boolean isPaused,
                                  boolean isSlowdownActive, String slowdownReason,
                                  List<String> activeEvents) {
        this.currentYear = currentYear;
        this.effectiveSpeed = effectiveSpeed;
        this.baseSpeed = baseSpeed;
        this.activeTimelineBranch = activeTimelineBranch;
        this.isPaused = isPaused;
        this.isSlowdownActive = isSlowdownActive;
        this.slowdownReason = slowdownReason;
        this.activeEvents = activeEvents != null ? activeEvents : new ArrayList<>();
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(currentYear);
        buf.writeInt(effectiveSpeed);
        buf.writeInt(baseSpeed);
        buf.writeUtf(activeTimelineBranch != null ? activeTimelineBranch : "main");
        buf.writeBoolean(isPaused);
        buf.writeBoolean(isSlowdownActive);
        buf.writeUtf(slowdownReason != null ? slowdownReason : "");
        
        buf.writeInt(activeEvents.size());
        for (String event : activeEvents) {
            buf.writeUtf(event);
        }
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        currentYear = buf.readInt();
        effectiveSpeed = buf.readInt();
        baseSpeed = buf.readInt();
        activeTimelineBranch = buf.readUtf(256);
        isPaused = buf.readBoolean();
        isSlowdownActive = buf.readBoolean();
        slowdownReason = buf.readUtf(512);
        
        int eventCount = buf.readInt();
        activeEvents = new ArrayList<>(eventCount);
        for (int i = 0; i < eventCount; i++) {
            activeEvents.add(buf.readUtf(256));
        }
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        if (EternalEngineConfig.COMMON.debugLogging.get()) {
            LOGGER.debug("[Client] Received simulation state: year={}, speed={}x", 
                currentYear, effectiveSpeed);
        }
    }
    
    // Getters
    public int getCurrentYear() { return currentYear; }
    public int getEffectiveSpeed() { return effectiveSpeed; }
    public int getBaseSpeed() { return baseSpeed; }
    public String getActiveTimelineBranch() { return activeTimelineBranch; }
    public boolean isPaused() { return isPaused; }
    public boolean isSlowdownActive() { return isSlowdownActive; }
    public String getSlowdownReason() { return slowdownReason; }
    public List<String> getActiveEvents() { return activeEvents; }
}


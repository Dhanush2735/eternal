package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet containing VIP status for a player.
 */
public class VipStatusPacket implements BasePacket {
    
    private boolean isVip;
    private String vipId;
    private String vipName;
    private String civilizationId;
    private int authorityLevel;
    private int age;
    private int health;
    private int historicalDeathYear;
    private List<String> availableCommands;
    private int armySize;
    private long treasury;
    private int armyLoyalty;
    
    public VipStatusPacket() {
        this.availableCommands = new ArrayList<>();
    }
    
    public VipStatusPacket(boolean isVip, String vipId, String vipName, String civilizationId,
                           int authorityLevel, int age, int health, int historicalDeathYear,
                           List<String> availableCommands, int armySize, long treasury, int armyLoyalty) {
        this.isVip = isVip;
        this.vipId = vipId;
        this.vipName = vipName;
        this.civilizationId = civilizationId;
        this.authorityLevel = authorityLevel;
        this.age = age;
        this.health = health;
        this.historicalDeathYear = historicalDeathYear;
        this.availableCommands = availableCommands != null ? availableCommands : new ArrayList<>();
        this.armySize = armySize;
        this.treasury = treasury;
        this.armyLoyalty = armyLoyalty;
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(isVip);
        buf.writeUtf(vipId != null ? vipId : "");
        buf.writeUtf(vipName != null ? vipName : "");
        buf.writeUtf(civilizationId != null ? civilizationId : "");
        buf.writeInt(authorityLevel);
        buf.writeInt(age);
        buf.writeInt(health);
        buf.writeInt(historicalDeathYear);
        
        buf.writeInt(availableCommands.size());
        for (String cmd : availableCommands) {
            buf.writeUtf(cmd);
        }
        
        buf.writeInt(armySize);
        buf.writeLong(treasury);
        buf.writeInt(armyLoyalty);
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        isVip = buf.readBoolean();
        vipId = buf.readUtf(64);
        vipName = buf.readUtf(128);
        civilizationId = buf.readUtf(64);
        authorityLevel = buf.readInt();
        age = buf.readInt();
        health = buf.readInt();
        historicalDeathYear = buf.readInt();
        
        int cmdCount = buf.readInt();
        availableCommands = new ArrayList<>(cmdCount);
        for (int i = 0; i < cmdCount; i++) {
            availableCommands.add(buf.readUtf(64));
        }
        
        armySize = buf.readInt();
        treasury = buf.readLong();
        armyLoyalty = buf.readInt();
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        // Client-side: update VIP UI
    }
    
    // Getters
    public boolean isVip() { return isVip; }
    public String getVipId() { return vipId; }
    public String getVipName() { return vipName; }
    public String getCivilizationId() { return civilizationId; }
    public int getAuthorityLevel() { return authorityLevel; }
    public int getAge() { return age; }
    public int getHealth() { return health; }
    public int getHistoricalDeathYear() { return historicalDeathYear; }
    public List<String> getAvailableCommands() { return availableCommands; }
    public int getArmySize() { return armySize; }
    public long getTreasury() { return treasury; }
    public int getArmyLoyalty() { return armyLoyalty; }
}

package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet containing history log entries for a time range.
 */
public class HistoryLogPacket implements BasePacket {
    
    private int startYear;
    private int endYear;
    private List<HistoryEntry> entries;
    
    public HistoryLogPacket() {
        this.entries = new ArrayList<>();
    }
    
    public HistoryLogPacket(int startYear, int endYear, List<HistoryEntry> entries) {
        this.startYear = startYear;
        this.endYear = endYear;
        this.entries = entries != null ? entries : new ArrayList<>();
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(startYear);
        buf.writeInt(endYear);
        
        buf.writeInt(entries.size());
        for (HistoryEntry entry : entries) {
            buf.writeInt(entry.year);
            buf.writeUtf(entry.type);
            buf.writeUtf(entry.title);
            buf.writeUtf(entry.description);
            buf.writeUtf(entry.involvedCivs);
            buf.writeBoolean(entry.isDivergence);
            buf.writeBoolean(entry.playerCaused);
        }
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        startYear = buf.readInt();
        endYear = buf.readInt();
        
        int count = buf.readInt();
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            HistoryEntry entry = new HistoryEntry();
            entry.year = buf.readInt();
            entry.type = buf.readUtf(64);
            entry.title = buf.readUtf(256);
            entry.description = buf.readUtf(2048);
            entry.involvedCivs = buf.readUtf(512);
            entry.isDivergence = buf.readBoolean();
            entry.playerCaused = buf.readBoolean();
            entries.add(entry);
        }
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        // Client-side: display history log
    }
    
    public int getStartYear() { return startYear; }
    public int getEndYear() { return endYear; }
    public List<HistoryEntry> getEntries() { return entries; }
    
    public static class HistoryEntry {
        public int year;
        public String type;
        public String title;
        public String description;
        public String involvedCivs;
        public boolean isDivergence;
        public boolean playerCaused;
    }
}

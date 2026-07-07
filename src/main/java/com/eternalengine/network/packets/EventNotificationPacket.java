package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Packet sent to notify clients of major historical events.
 */
public class EventNotificationPacket implements BasePacket {
    
    private String eventId;
    private String eventName;
    private String eventType;
    private String severity;
    private int year;
    private double latitude;
    private double longitude;
    private String description;
    private boolean requiresSlowdown;
    private String[] involvedCivs;
    
    public EventNotificationPacket() {
        this.involvedCivs = new String[0];
    }
    
    public EventNotificationPacket(String eventId, String eventName, String eventType,
                                    String severity, int year, double latitude, double longitude,
                                    String description, boolean requiresSlowdown, String[] involvedCivs) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventType = eventType;
        this.severity = severity;
        this.year = year;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.requiresSlowdown = requiresSlowdown;
        this.involvedCivs = involvedCivs != null ? involvedCivs : new String[0];
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(eventId != null ? eventId : "");
        buf.writeUtf(eventName != null ? eventName : "");
        buf.writeUtf(eventType != null ? eventType : "");
        buf.writeUtf(severity != null ? severity : "");
        buf.writeInt(year);
        buf.writeDouble(latitude);
        buf.writeDouble(longitude);
        buf.writeUtf(description != null ? description : "");
        buf.writeBoolean(requiresSlowdown);
        
        buf.writeInt(involvedCivs.length);
        for (String civ : involvedCivs) {
            buf.writeUtf(civ);
        }
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        eventId = buf.readUtf(64);
        eventName = buf.readUtf(256);
        eventType = buf.readUtf(64);
        severity = buf.readUtf(32);
        year = buf.readInt();
        latitude = buf.readDouble();
        longitude = buf.readDouble();
        description = buf.readUtf(1024);
        requiresSlowdown = buf.readBoolean();
        
        int civCount = buf.readInt();
        involvedCivs = new String[civCount];
        for (int i = 0; i < civCount; i++) {
            involvedCivs[i] = buf.readUtf(64);
        }
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        // Client-side: display event notification
    }
    
    // Getters
    public String getEventId() { return eventId; }
    public String getEventName() { return eventName; }
    public String getEventType() { return eventType; }
    public String getSeverity() { return severity; }
    public int getYear() { return year; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getDescription() { return description; }
    public boolean requiresSlowdown() { return requiresSlowdown; }
    public String[] getInvolvedCivs() { return involvedCivs; }
}

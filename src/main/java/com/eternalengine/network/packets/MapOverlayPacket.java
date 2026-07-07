package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet containing map overlay data for rendering on minimap/worldmap mods.
 */
public class MapOverlayPacket implements BasePacket {
    
    private String overlayType; // TERRITORY, WAR_FRONT, TRADE_ROUTE, DISEASE, ARMY
    private double minLat;
    private double minLon;
    private double maxLat;
    private double maxLon;
    private List<OverlayPoint> points;
    
    public MapOverlayPacket() {
        this.points = new ArrayList<>();
    }
    
    public MapOverlayPacket(String overlayType, double minLat, double minLon,
                            double maxLat, double maxLon, List<OverlayPoint> points) {
        this.overlayType = overlayType;
        this.minLat = minLat;
        this.minLon = minLon;
        this.maxLat = maxLat;
        this.maxLon = maxLon;
        this.points = points != null ? points : new ArrayList<>();
    }
    
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(overlayType != null ? overlayType : "");
        buf.writeDouble(minLat);
        buf.writeDouble(minLon);
        buf.writeDouble(maxLat);
        buf.writeDouble(maxLon);
        
        buf.writeInt(points.size());
        for (OverlayPoint point : points) {
            buf.writeDouble(point.latitude);
            buf.writeDouble(point.longitude);
            buf.writeInt(point.color);
            buf.writeFloat(point.intensity);
            buf.writeUtf(point.label != null ? point.label : "");
            buf.writeUtf(point.ownerId != null ? point.ownerId : "");
        }
    }
    
    @Override
    public void decode(FriendlyByteBuf buf) {
        overlayType = buf.readUtf(32);
        minLat = buf.readDouble();
        minLon = buf.readDouble();
        maxLat = buf.readDouble();
        maxLon = buf.readDouble();
        
        int count = buf.readInt();
        points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            OverlayPoint point = new OverlayPoint();
            point.latitude = buf.readDouble();
            point.longitude = buf.readDouble();
            point.color = buf.readInt();
            point.intensity = buf.readFloat();
            point.label = buf.readUtf(128);
            point.ownerId = buf.readUtf(64);
            points.add(point);
        }
    }
    
    @Override
    public void handle(NetworkEvent.Context context) {
        // Client-side: render overlay on map
    }
    
    public String getOverlayType() { return overlayType; }
    public List<OverlayPoint> getPoints() { return points; }
    
    public static class OverlayPoint {
        public double latitude;
        public double longitude;
        public int color;
        public float intensity;
        public String label;
        public String ownerId;
    }
}

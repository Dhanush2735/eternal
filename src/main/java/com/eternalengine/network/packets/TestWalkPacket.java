package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client. Turns 226: the test's walk order - waypoints as (dx, dz) offsets from where
 * the player stands on arrival, plus a speed in blocks per tick. An empty list means stop.
 * Handled by {@code client.TestPilot}, the pilot that gives the runtime suite its legs.
 */
public class TestWalkPacket implements BasePacket {

    private double[][] points = new double[0][2];
    private double speed = 0.26d;

    /** Required by the packet handler's decoder. */
    public TestWalkPacket() {
    }

    /** {@code points} is n rows of {dx, dz}; empty means stop the pilot. */
    public TestWalkPacket(double[][] points, double speed) {
        this.points = (points == null) ? new double[0][2] : points;
        this.speed = speed;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(points.length);
        for (double[] p : points) {
            buf.writeDouble(p.length > 0 ? p[0] : 0.0d);
            buf.writeDouble(p.length > 1 ? p[1] : 0.0d);
        }
        buf.writeDouble(speed);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        points = new double[Math.max(0, Math.min(n, 256))][2];
        for (int i = 0; i < points.length; i++) {
            points[i][0] = buf.readDouble();
            points[i][1] = buf.readDouble();
        }
        speed = buf.readDouble();
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        double[][] pts = points;
        double spd = speed;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.eternalengine.client.TestPilot.walk(pts, spd));
    }
}

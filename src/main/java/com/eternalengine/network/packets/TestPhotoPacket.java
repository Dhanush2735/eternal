package com.eternalengine.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: take a screenshot now, under this name (turn 223). The runtime suite's photo
 * route sends one of these at every stop of the era route, so the test captures its own evidence
 * instead of asking the player to press F2 and describe what they see.
 */
public class TestPhotoPacket implements BasePacket {

    private String name = "";

    /** Required by the packet handler's decoder. */
    public TestPhotoPacket() {
    }

    public TestPhotoPacket(String name) {
        this.name = name == null ? "" : name;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name, 128);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        name = buf.readUtf(128);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        String shot = name;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.eternalengine.client.TestCamera.capture(shot));
    }
}

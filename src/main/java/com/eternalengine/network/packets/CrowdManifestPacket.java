package com.eternalengine.network.packets;

import com.eternalengine.client.CrowdManifestStore;
import com.eternalengine.population.ChunkPopulation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * The crowd, as a recipe.
 *
 * <p>The server does not send the crowd — it sends what the crowd <em>is</em>: the place's population
 * and extent, the concentration, the heart, and one line per chunk in the player's view saying how
 * many people stand in it. The client rebuilds the figures from that, at exactly the places the
 * server would have put them, because every step of the placement is a pure function of the seed.
 *
 * <p>Measured ({@code ChunkPopulation.Manifest.sizeBytes()}): 166 bytes for a 64-block view of
 * Secunderabad, and the same 166 whether the recipe describes 13 figures or 3,465 — about one
 * hundredth of what four hundred spawned entities cost to send, and sent only when the player's
 * chunk, the place or the year changes, not on every tick.
 *
 * <p>This is the transport half of the crowd's drawing route. The renderer that reads the recipe and
 * builds the mesh is client-side work that is not written yet; this packet, its decoding and its
 * client-side store are what it will read from.
 */
public class CrowdManifestPacket implements BasePacket {

    private ChunkPopulation.Manifest manifest;

    /** Required by the packet handler's decoder. */
    public CrowdManifestPacket() {
    }

    public CrowdManifestPacket(ChunkPopulation.Manifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        // One wire format, and it is the tested one: the manifest encodes itself (ChunkPopulation),
        // the packet only carries the bytes. A second format here would be a second thing to be
        // wrong about, and the one thing that cannot be run on the machine this was written on.
        byte[] bytes = manifest == null ? new byte[0] : manifest.encode();
        buf.writeByteArray(bytes);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        byte[] bytes = buf.readByteArray(64 * 1024);
        manifest = bytes.length == 0 ? null : ChunkPopulation.Manifest.decode(bytes);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        // Client side: hold the recipe for the renderer to build from.
        CrowdManifestStore.accept(manifest);
    }

    public ChunkPopulation.Manifest getManifest() {
        return manifest;
    }
}

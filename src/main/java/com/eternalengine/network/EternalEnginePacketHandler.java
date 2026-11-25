package com.eternalengine.network;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.network.packets.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

public class EternalEnginePacketHandler {

    private static final Logger LOGGER = EternalEngineMod.getLogger();
    // Step 18: "2" (the talk packets). Step 18c: "3" (the date line). Step 18f: "4" (the date line removed again, at the
    // user's wish; the messages after it moved up one). A client must have the same mod version as the server.
    // Step 21: protocol 7 (the talk view carries "ended": "… has left."). A game and a server must have the same build.
    private static final String PROTOCOL_VERSION = "8";

    private static SimpleChannel CHANNEL;
    private static int packetId = 0;

    public static void register() {
        CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(EternalEngineMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

        // Server -> Client
        registerPacket(SimulationStatePacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(ReputationUpdatePacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(SpeedChangePacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(EventNotificationPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(CivilizationStatePacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(HistoryLogPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(BuildingInfoPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(MapOverlayPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(VipStatusPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(CrowdManifestPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(TalkViewPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(EraPanelPacket.class, NetworkDirection.PLAY_TO_CLIENT); // the sheet's trade screen and the person register
        // Step 19c-19e: the trade screen, the newspaper and the map (new packets: protocol 5).
        registerPacket(TradeViewPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(PaperViewPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(MapViewPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        registerPacket(BookViewPacket.class, NetworkDirection.PLAY_TO_CLIENT); // step 20
        // Turn 223: the runtime suite's own camera (one shot request per frame it wants).
        registerPacket(TestPhotoPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        // Turn 226: the runtime suite's own legs (the pilot walks the player through each stop).
        registerPacket(TestWalkPacket.class, NetworkDirection.PLAY_TO_CLIENT);

        // Client -> Server
        registerPacket(VipCommandPacket.class, NetworkDirection.PLAY_TO_SERVER);
        registerPacket(SpeedVotePacket.class, NetworkDirection.PLAY_TO_SERVER);
        registerPacket(RequestDataPacket.class, NetworkDirection.PLAY_TO_SERVER);
        registerPacket(TalkActionPacket.class, NetworkDirection.PLAY_TO_SERVER);
        registerPacket(BookActionPacket.class, NetworkDirection.PLAY_TO_SERVER); // step 20

        LOGGER.info("[EE Network] Registered {} packets", packetId);
    }

    private static <T extends BasePacket> void registerPacket(Class<T> packetClass, NetworkDirection direction) {
        try {
            CHANNEL.messageBuilder(packetClass, packetId++, direction)
                .encoder(BasePacket::encode)
                .decoder(buf -> {
                    try {
                        T packet = packetClass.getDeclaredConstructor().newInstance();
                        packet.decode(buf);
                        return packet;
                    } catch (Exception e) {
                        LOGGER.error("[EE Network] Failed to decode packet {}: {}", packetClass.getSimpleName(), e.getMessage());
                        try {
                            return packetClass.getDeclaredConstructor().newInstance();
                        } catch (Exception reflectionError) {
                            throw new IllegalStateException("Unable to construct fallback packet " + packetClass.getSimpleName(), reflectionError);
                        }
                    }
                })
                .consumerMainThread((packet, contextSupplier) -> {
                    net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
                    packet.handle(context);
                    context.setPacketHandled(true);
                })
                .add();

        } catch (Exception e) {
            LOGGER.error("[EE Network] Failed to register packet {}: {}", packetClass.getSimpleName(), e.getMessage());
        }
    }

    public static void sendToPlayer(BasePacket packet, ServerPlayer player) {
        if (CHANNEL == null || packet == null || player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAll(BasePacket packet) {
        if (CHANNEL == null || packet == null) return;
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToDimension(BasePacket packet, ResourceKey<Level> dimension) {
        if (CHANNEL == null || packet == null || dimension == null) return;
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }

    public static void sendToServer(BasePacket packet) {
        if (CHANNEL == null || packet == null) return;
        CHANNEL.sendToServer(packet);
    }

    public static void sendToNear(BasePacket packet, double x, double y, double z, double radius, ResourceKey<Level> dimension) {
        if (CHANNEL == null || packet == null || dimension == null) return;
        CHANNEL.send(
            PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, radius, dimension)),
            packet
        );
    }

    public static SimpleChannel getChannel() {
        return CHANNEL;
    }
}
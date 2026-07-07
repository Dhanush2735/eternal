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
    private static final String PROTOCOL_VERSION = "1";

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

        // Client -> Server
        registerPacket(VipCommandPacket.class, NetworkDirection.PLAY_TO_SERVER);
        registerPacket(SpeedVotePacket.class, NetworkDirection.PLAY_TO_SERVER);
        registerPacket(RequestDataPacket.class, NetworkDirection.PLAY_TO_SERVER);

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
                        return null;
                    }
                })
                .consumerMainThread((packet, contextSupplier) -> {
                    net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();

                    if (packet != null) {
                        packet.handle(context);
                    }

                    context.setPacketHandled(true);
                })
                .add();

        } catch (Exception e) {
            LOGGER.error("[EE Network] Failed to register packet {}: {}", packetClass.getSimpleName(), e.getMessage());
        }
    }

    public static void sendToPlayer(BasePacket packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAll(BasePacket packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToDimension(BasePacket packet, ResourceKey<Level> dimension) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }

    public static void sendToServer(BasePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToNear(BasePacket packet, double x, double y, double z, double radius, ResourceKey<Level> dimension) {
        CHANNEL.send(
            PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, radius, dimension)),
            packet
        );
    }

    public static SimpleChannel getChannel() {
        return CHANNEL;
    }
}
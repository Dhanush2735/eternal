package com.eternalengine.chrononexus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Paradox Loop & Grandfather Decay Controller for Phase 5 (The Loop Closers).
 * Tracks the live countdown to deliver the Time Machine back to Year -3000 BC.
 * If the countdown expires before delivery, triggers the Grandfather Paradox,
 * permanently erasing the player from existence.
 */
public final class ParadoxLoopController {

    private boolean active = false;
    private int countdownSeconds = 900; // 15 minutes default
    private int tickCounter = 0;
    private BlockPos targetDeliveryPos = new BlockPos(0, 70, 0);
    private double deliveryRadius = 16.0D;

    private final Map<UUID, Boolean> playerDelivered = new HashMap<>();
    private final Map<UUID, Boolean> playerErased = new HashMap<>();

    public void startCountdown(int seconds, BlockPos targetPos, Iterable<ServerPlayer> players) {
        this.countdownSeconds = Math.max(10, seconds);
        this.targetDeliveryPos = targetPos != null ? targetPos : new BlockPos(0, 70, 0);
        this.active = true;
        this.tickCounter = 0;
        this.playerDelivered.clear();
        this.playerErased.clear();

        for (ServerPlayer player : players) {
            playerDelivered.put(player.getUUID(), false);
            playerErased.put(player.getUUID(), false);
            player.sendSystemMessage(Component.literal("§c§l[PARADOX WARNING] §eOperation Ouroboros initiated!"));
            player.sendSystemMessage(Component.literal("§7You must secretly deliver the Time Machine to Year -3000 BC within §6"
                + countdownSeconds + " seconds §7or be erased by a Grandfather Paradox."));
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.7F, 1.2F);
        }
    }

    public void tick(ServerLevel level) {
        if (!active || level == null) return;
        tickCounter++;

        // Decrement timer every 20 ticks (1 second)
        if (tickCounter >= 20) {
            tickCounter = 0;
            countdownSeconds--;

            // Warning broadcast at milestones
            if (countdownSeconds == 600 || countdownSeconds == 300 || countdownSeconds == 120 || countdownSeconds == 60 || countdownSeconds == 30 || countdownSeconds == 10) {
                for (ServerPlayer player : level.players()) {
                    if (!playerDelivered.getOrDefault(player.getUUID(), false) && !playerErased.getOrDefault(player.getUUID(), false)) {
                        player.sendSystemMessage(Component.literal("§c[PARADOX COUNTDOWN] §4" + countdownSeconds + " seconds §cremaining before timeline collapse!"));
                        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.AMBIENT, 1.0F, 0.5F);
                    }
                }
            }

            // Expiration: Grandfather Paradox Erasure
            if (countdownSeconds <= 0) {
                active = false;
                triggerGrandfatherErasure(level);
            }
        }
    }

    public boolean deliverMachine(ServerPlayer player) {
        if (!active || player == null) return false;
        UUID uuid = player.getUUID();
        if (playerErased.getOrDefault(uuid, false)) return false;

        // Check proximity to target delivery coordinates
        double distSq = player.distanceToSqr(targetDeliveryPos.getX(), targetDeliveryPos.getY(), targetDeliveryPos.getZ());
        if (distSq <= deliveryRadius * deliveryRadius) {
            playerDelivered.put(uuid, true);
            player.sendSystemMessage(Component.literal("§a§l[PARADOX SECURED] §fYou secretly deposited the Time Machine into the ancient ruins!"));
            player.sendSystemMessage(Component.literal("§7Your past self has discovered the craft. The causal loop is permanently sealed."));
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

            // Check if all players in session have fulfilled the loop
            if (isAllDelivered()) {
                active = false;
                player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§6§l[CHRONO-NEXUS] §aAll causal loops sealed! The timeline achieves Quantum Permanence."),
                    false
                );
            }
            return true;
        } else {
            player.sendSystemMessage(Component.literal("§c[Delivery Failed] You are too far from the -3000 BC crash site: ("
                + targetDeliveryPos.getX() + ", " + targetDeliveryPos.getY() + ", " + targetDeliveryPos.getZ() + ")"));
            return false;
        }
    }

    private void triggerGrandfatherErasure(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            UUID uuid = player.getUUID();
            boolean delivered = playerDelivered.getOrDefault(uuid, false);
            if (!delivered) {
                playerErased.put(uuid, true);
                player.sendSystemMessage(Component.literal("§4§l[CAUSAL COLLAPSE] §cYour origin was never established. The timeline rejects your existence."));
                player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.5F);

                // Eliminate player via fellOutOfWorld damage
                player.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);

                level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§4[Chrono-Nexus] §c" + player.getName().getString()
                        + " was erased from existence by a Grandfather Paradox."),
                    false
                );
            }
        }
    }

    public boolean isAllDelivered() {
        if (playerDelivered.isEmpty()) return false;
        for (boolean delivered : playerDelivered.values()) {
            if (!delivered) return false;
        }
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(int seconds) {
        this.countdownSeconds = seconds;
    }

    public BlockPos getTargetDeliveryPos() {
        return targetDeliveryPos;
    }

    public void setTargetDeliveryPos(BlockPos pos) {
        this.targetDeliveryPos = pos;
    }

    public boolean isPlayerDelivered(UUID uuid) {
        return playerDelivered.getOrDefault(uuid, false);
    }

    public boolean isPlayerErased(UUID uuid) {
        return playerErased.getOrDefault(uuid, false);
    }
}

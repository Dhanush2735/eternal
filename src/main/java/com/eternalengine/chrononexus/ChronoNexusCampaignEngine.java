package com.eternalengine.chrononexus;

import com.eternalengine.chrononexus.entity.RivalVariantEntity;
import com.eternalengine.chrononexus.item.ChronoItems;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.entity.EternalEngineEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * The Central Campaign Engine for Chrono-Nexus: The Temporal Paradox.
 * Coordinates Exploration vs Story mode, 7-phase campaign progression,
 * rival incursions, the Year -3000 BC paradox loop, and multiple moral endings.
 */
public final class ChronoNexusCampaignEngine {

    private ChronoGameMode mode = ChronoGameMode.EXPLORATION;
    private ChronoNexusCampaignPhase currentPhase = ChronoNexusCampaignPhase.PHASE_1_DAWN_OF_TIME;
    private final ParadoxLoopController paradoxController = new ParadoxLoopController();
    private ChronoEnding resolvedEnding = null;
    private boolean campaignActive = false;

    public void tick(ServerLevel level) {
        if (level == null) return;

        // Paradox countdown processing
        if (currentPhase == ChronoNexusCampaignPhase.PHASE_5_TEMPORAL_PARADOX && paradoxController.isActive()) {
            paradoxController.tick(level);
        }
    }

    public void startCampaign(MinecraftServer server) {
        this.mode = ChronoGameMode.STORY;
        this.currentPhase = ChronoNexusCampaignPhase.PHASE_1_DAWN_OF_TIME;
        this.campaignActive = true;
        this.resolvedEnding = null;

        // Initialize simulation year to -3000 BC
        if (SimulationClockManager.isInitialized()) {
            SimulationClockManager.getInstance().setCurrentYear(-3000);
        }

        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Equip with primitive starting tools and Chrono Black Box
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(Items.STONE_AXE));
            // Step 45 part 6: the starting weapon is the melee weapon of where the campaign
            // begins, not a stone sword for every century and continent alike.
            player.getInventory().add(com.eternalengine.war.WarGearStacks.stack(
                    com.eternalengine.war.WarGearStacks.kitFor(player, "infantry",
                            com.eternalengine.war.WarGearStacks.currentYear()).item("melee")));
            player.getInventory().add(new ItemStack(ChronoItems.CHRONO_BLACK_BOX.get()));

            player.sendSystemMessage(Component.literal("§6§l=================================================="));
            player.sendSystemMessage(Component.literal("§e§l  CHRONO-NEXUS: THE TEMPORAL PARADOX"));
            player.sendSystemMessage(Component.literal("§6§l=================================================="));
            player.sendSystemMessage(Component.literal("§7You awaken in §eYear -3000 BC§7. No memory. No coordinates."));
            player.sendSystemMessage(Component.literal("§bCurrent Objective: §f" + currentPhase.getObjective()));
            player.playNotifySound(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
    }

    public void setPhase(ChronoNexusCampaignPhase phase, MinecraftServer server) {
        this.currentPhase = phase;
        if (server == null) return;

        server.getPlayerList().broadcastSystemMessage(
            Component.literal("§6[Chrono-Nexus] §eAdvanced to §l" + phase.getTitle().toUpperCase(Locale.ROOT)
                + "§r\n§bObjective: §f" + phase.getObjective()),
            false
        );

        // Phase specific automations
        if (phase == ChronoNexusCampaignPhase.PHASE_1_DAWN_OF_TIME) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getInventory().add(new ItemStack(ChronoItems.TIME_MACHINE.get()));
            }
        } else if (phase == ChronoNexusCampaignPhase.PHASE_4_DIMENSIONAL_BREACH) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getInventory().add(new ItemStack(ChronoItems.DIMENSION_MACHINE.get()));
            }
        } else if (phase == ChronoNexusCampaignPhase.PHASE_5_TEMPORAL_PARADOX) {
            BlockPos targetPos = new BlockPos(0, 70, 0);
            if (!server.getPlayerList().getPlayers().isEmpty()) {
                targetPos = server.getPlayerList().getPlayers().get(0).blockPosition();
            }
            paradoxController.startCountdown(900, targetPos, server.getPlayerList().getPlayers());
        }
    }

    public void advancePhase(MinecraftServer server) {
        int nextNum = currentPhase.getPhaseNumber() + 1;
        if (nextNum <= 7) {
            setPhase(ChronoNexusCampaignPhase.fromNumber(nextNum), server);
        }
    }

    public RivalVariantEntity spawnRival(ServerLevel level, ServerPlayer targetPlayer) {
        if (level == null) return null;
        RivalVariantEntity rival = EternalEngineEntities.RIVAL_VARIANT.get().create(level);
        if (rival != null) {
            BlockPos pos = targetPlayer != null ? targetPlayer.blockPosition().offset(8, 0, 8) : new BlockPos(0, 70, 0);
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            int spawnY = Math.max(pos.getY(), surfaceY);
            rival.moveTo(pos.getX() + 0.5D, spawnY, pos.getZ() + 0.5D, 0.0F, 0.0F);
            if (targetPlayer != null) {
                rival.setMirroredPlayer(targetPlayer);
            }
            level.addFreshEntity(rival);
            level.playSound(null, pos.getX(), spawnY, pos.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.0F, 1.2F);
        }
        return rival;
    }

    public void triggerEnding(ChronoEnding ending, MinecraftServer server) {
        this.resolvedEnding = ending;
        this.currentPhase = ChronoNexusCampaignPhase.PHASE_7_THE_CHOICE;
        this.campaignActive = false;

        if (server == null) return;

        server.getPlayerList().broadcastSystemMessage(
            Component.literal("§d§l=================================================="),
            false
        );
        server.getPlayerList().broadcastSystemMessage(
            Component.literal("§5§l  MULTIVERSAL RESOLUTION: " + ending.getTitle().toUpperCase(Locale.ROOT)),
            false
        );
        server.getPlayerList().broadcastSystemMessage(
            Component.literal("§7" + ending.getDescription()),
            false
        );
        server.getPlayerList().broadcastSystemMessage(
            Component.literal("§d§l=================================================="),
            false
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    public ChronoGameMode getMode() {
        return mode;
    }

    public void setMode(ChronoGameMode mode) {
        this.mode = mode;
    }

    public ChronoNexusCampaignPhase getCurrentPhase() {
        return currentPhase;
    }

    public ParadoxLoopController getParadoxController() {
        return paradoxController;
    }

    public ChronoEnding getResolvedEnding() {
        return resolvedEnding;
    }

    public boolean isCampaignActive() {
        return campaignActive;
    }

    public void setCampaignActive(boolean active) {
        this.campaignActive = active;
    }
}

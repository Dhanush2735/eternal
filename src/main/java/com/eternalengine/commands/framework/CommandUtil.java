package com.eternalengine.commands.framework;

import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.WorldStateManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Shared command helpers for future subsystem command modules.
 */
public final class CommandUtil {
    private CommandUtil() {
    }

    public static int success(CommandSourceStack source, String message, boolean broadcast) {
        if (source == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal(message == null ? "" : message), broadcast);
        return 1;
    }

    public static int failure(CommandSourceStack source, String message) {
        if (source == null) {
            return 0;
        }
        source.sendFailure(Component.literal(message == null ? "Command failed" : message));
        return 0;
    }

    public static Optional<ServerPlayer> player(CommandSourceStack source) {
        if (source == null || !(source.getEntity() instanceof ServerPlayer player)) {
            return Optional.empty();
        }
        return Optional.of(player);
    }

    public static int currentYear() {
        if (SimulationClockManager.isInitialized()) {
            return SimulationClockManager.getInstance().getCurrentYear();
        }
        if (WorldStateManager.isInitialized()) {
            return WorldStateManager.getInstance().getCurrentYear();
        }
        return -3000;
    }

    public static String formatYear(int year) {
        return year < 0 ? Math.abs(year) + " BCE" : year + " CE";
    }
}

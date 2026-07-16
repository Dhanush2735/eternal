package com.eternalengine.vip;

import com.eternalengine.core.WorldStateManager;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;

/**
 * VIP command validation/execution foundation.
 */
public final class VipCommandSystem {
    private final AuthoritySystem authoritySystem;

    public VipCommandSystem(AuthoritySystem authoritySystem) {
        this.authoritySystem = authoritySystem == null ? new AuthoritySystem() : authoritySystem;
    }

    public CommandResult executeCommand(VipSlot vip, String commandType, Map<String, Object> params,
                                        MinecraftServer server, WorldStateManager state) {
        if (vip == null) {
            return CommandResult.failure("VIP is missing");
        }
        if (commandType == null || commandType.isBlank()) {
            return CommandResult.failure("Command type is missing");
        }
        if (!validateAuthority(vip, commandType)) {
            return CommandResult.failure("VIP authority too low for " + commandType);
        }
        Map<String, Object> effects = new HashMap<>();
        effects.put("vipId", vip.vipId());
        effects.put("command", commandType);
        if (state != null) {
            state.setFlag("vip.command." + vip.vipId() + "." + commandType, Long.toString(System.currentTimeMillis()));
        }
        return CommandResult.success("Executed VIP command foundation path: " + commandType, effects);
    }

    public boolean validateAuthority(VipSlot vip, String commandType) {
        int required = switch (commandType) {
            case "MOVE_ARMY", "OPEN_TRADE_ROUTE", "BUILD_INFRASTRUCTURE" -> 3;
            case "DECLARE_WAR", "OFFER_PEACE", "SIGN_TREATY" -> 6;
            case "CALL_HOLY_WAR", "EXCOMMUNICATE" -> 7;
            default -> 1;
        };
        return vip != null && vip.authorityLevel() >= required;
    }

    public AuthoritySystem authoritySystem() {
        return authoritySystem;
    }

    public record CommandResult(boolean success, String message, Map<String, Object> effects) {
        public static CommandResult success(String message, Map<String, Object> effects) {
            return new CommandResult(true, message, effects == null ? Map.of() : Map.copyOf(effects));
        }
        public static CommandResult failure(String message) {
            return new CommandResult(false, message, Map.of());
        }
    }
}

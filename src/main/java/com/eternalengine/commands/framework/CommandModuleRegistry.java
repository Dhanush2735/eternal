package com.eternalengine.commands.framework;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry used by the future modular /ee command tree.
 */
public final class CommandModuleRegistry {
    private final List<CommandModule> modules = new ArrayList<>();

    public void add(CommandModule module) {
        if (module != null) {
            modules.add(module);
        }
    }

    public List<CommandModule> modules() {
        return Collections.unmodifiableList(modules);
    }

    public void registerAll(CommandDispatcher<CommandSourceStack> dispatcher, String rootName) {
        if (dispatcher == null) {
            return;
        }
        String safeRoot = rootName == null || rootName.isBlank() ? "ee" : rootName;
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(safeRoot);
        for (CommandModule module : modules) {
            module.register(root);
        }
        dispatcher.register(root);
    }
}

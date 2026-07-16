package com.eternalengine.commands.framework;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * A modular contributor to the /ee command tree.
 *
 * Future command refactors should move one subsystem at a time from the current
 * monolithic EternalEngineCommands class into CommandModule implementations.
 */
public interface CommandModule {
    String name();

    void register(LiteralArgumentBuilder<CommandSourceStack> root);
}

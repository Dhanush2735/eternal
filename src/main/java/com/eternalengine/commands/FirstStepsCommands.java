package com.eternalengine.commands;

import com.eternalengine.commands.framework.CommandModule;
import com.eternalengine.player.FirstSteps;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * <b>The first CommandModule: {@code /ee firststeps}, the new player's route.</b> J5.
 *
 * <ul>
 *   <li>{@code /ee firststeps} - the page: every step, what it teaches, what is done, what is next.</li>
 *   <li>{@code /ee firststeps done} - the player says they did the step they were on (the guide
 *       serves the player, it does not watch them; the hooks that could count facts automatically
 *       are noted in the README as future wiring).</li>
 *   <li>{@code /ee firststeps graduate} - the graduation line, earned once every step is counted.</li>
 *   <li>{@code /ee firststeps reset} - <b>refused:</b> notes are written once, there is no reset.</li>
 * </ul>
 *
 * <p>This is the first module on the {@link com.eternalengine.commands.framework.CommandModuleRegistry}
 * framework; it registers under the {@code /ee} root Brigadier already has.
 */
public final class FirstStepsCommands implements CommandModule {

    @Override
    public String name() {
        return "firststeps";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("firststeps")
                .executes(ctx -> show(ctx.getSource()))
                .then(Commands.literal("done")
                        .executes(ctx -> done(ctx.getSource())))
                .then(Commands.literal("graduate")
                        .executes(ctx -> graduate(ctx.getSource())))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource()))));
    }

    private static void send(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }

    private static int show(CommandSourceStack source) {
        for (String line : FirstSteps.page(source.getTextName())) {
            send(source, line);
        }
        return 1;
    }

    private static int done(CommandSourceStack source) {
        String player = source.getTextName();
        FirstSteps.Step next = FirstSteps.next(player);
        if (next == null) {
            send(source, "nothing is left: every step is counted. "
                    + "/ee firststeps graduate claims the line.");
            return 1;
        }
        FirstSteps.Answer answer = FirstSteps.mark(player, next.doneWhen().name());
        if (answer.ok()) {
            send(source, answer.line());
        } else {
            send(source, "no: " + answer.refused());
        }
        return 1;
    }

    private static int graduate(CommandSourceStack source) {
        FirstSteps.Answer answer = FirstSteps.graduate(source.getTextName());
        if (answer.ok()) {
            send(source, answer.line());
        } else {
            send(source, "no: " + answer.refused());
        }
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        send(source, "no: notes are written once. A finished step is never un-finished and there is "
                + "no reset. Start a new character if you want to walk the route again.");
        return 1;
    }
}

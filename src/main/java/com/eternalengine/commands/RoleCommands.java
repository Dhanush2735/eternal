package com.eternalengine.commands;

import com.eternalengine.commands.framework.CommandModule;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.villager.RoleDaySystem;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Step R1's test levers (permission 05): the role day, on demand.
 *
 * <ul>
 *   <li>{@code /ee role} — the page: the year, the clock, the prayer hour, and the people near you
 *       by their work class and their state.</li>
 *   <li>{@code /ee role prayer} — the prayer hour comes now: the street stops for 30 game-minutes.</li>
 *   <li>{@code /ee role wave} — the workday goes out now: the bell and the shift head to work for a
 *       game-hour, so the wave does not have to wait for 6 in the morning.</li>
 * </ul>
 */
public final class RoleCommands implements CommandModule {

    @Override
    public String name() {
        return "role";
    }

    @Override
    public void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("role")
                .executes(ctx -> page(ctx.getSource()))
                .then(Commands.literal("prayer")
                        .executes(ctx -> prayer(ctx.getSource())))
                .then(Commands.literal("wave")
                        .executes(ctx -> wave(ctx.getSource()))));
    }

    private static void send(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }

    private static int page(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        long t = Math.floorMod(level.getDayTime(), 24000L);
        int hh = (6 + (int) (t / 1000L)) % 24;
        int mm = (int) ((t % 1000L) * 6L / 10L);
        int year = WorldStateManager.getInstance().getCurrentYear();
        boolean praying = RoleDaySystem.prayingNow(level);
        send(source, "the role day (step R1)");
        send(source, "year " + year + " · " + (hh / 10) + (hh / 10 < 2 ? " " : "") + (hh % 10)
                + ":" + (mm / 10) + (mm / 10 < 6 ? " " : "") + (mm % 10)
                + " · prayer " + (praying ? "on now" : "off"));
        Entity e = source.getEntity();
        int bell = 0, shift = 0, market = 0;
        int prayingN = 0, way = 0, post = 0, free = 0;
        int n = 0;
        for (HumanNpcEntity npc : level.getEntitiesOfClass(HumanNpcEntity.class,
                e.getBoundingBox().inflate(48.0), x -> x.isAlive())) {
            n++;
            String cls = RoleDaySystem.workClass(npc.getRole());
            if ("market".equals(cls)) market++;
            else if ("shift".equals(cls)) shift++;
            else bell++;
            String s = RoleDaySystem.stateOf(npc.getId());
            switch (s) {
                case "praying": prayingN++; break;
                case "on the way": way++; break;
                case "at post": post++; break;
                default: free++;
            }
        }
        send(source, "near you: " + n + " — bell " + bell + " · shift " + shift + " · market " + market);
        send(source, "praying " + prayingN + " · on the way " + way + " · at post " + post + " · free " + free);
        send(source, "levers: /ee role prayer (the hour comes now) · /ee role wave (the workday goes out now)");
        return 1;
    }

    private static int prayer(CommandSourceStack source) {
        RoleDaySystem.forcePrayer(500L); // 30 game-minutes
        send(source, "the prayer hour comes now — the street stops and turns for 30 game-minutes");
        return 1;
    }

    private static int wave(CommandSourceStack source) {
        RoleDaySystem.forceWave(1000L); // a game-hour
        send(source, "the workday goes out now — the bell and the shift head to work for a game-hour");
        return 1;
    }
}

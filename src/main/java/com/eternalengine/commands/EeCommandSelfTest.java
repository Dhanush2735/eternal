package com.eternalengine.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game self-test: {@code /ee test all} runs every Eternal Engine command
 * (except itself and {@code /ee reset confirm}) and reports PASS/FAIL.
 */
public final class EeCommandSelfTest {

    private static final int PART_SIZE = 40;

    /**
     * Ordered sample invocations. Keep {@code /ee test} and {@code /ee reset}
     * out of this list so the suite cannot recurse or wipe the world.
     */
    static final String[] COMMANDS = {
            "ee help",
            "ee status",
            "ee year",
            "ee speed status",
            "ee performance",
            "ee data paths",
            "ee data install",
            "ee data load",
            "ee event load",
            "ee civ list",
            "ee civ active",
            "ee event list",
            "ee event pending",
            "ee year set -500",
            "ee year set -3000",
            "ee speed set 25",
            "ee speed set 10",
            "ee observer",
            "ee override",
            "ee slowdown",
            "ee rep",
            "ee rep apply trade",
            "ee achievements",
            "ee actions recent",
            "ee probe",
            "ee probe 0 0",
            "ee probe 100 200",
            "ee scan chunk",
            "ee scan chunk 4",
            "ee anomaly",
            "ee anomaly probe",
            "ee anomaly chunk",
            "ee anomaly chunk 4",
            "ee affairs",
            "ee affairs global",
            "ee affairs war",
            "ee affairs disease",
            "ee affairs timeline",
            "ee affairs debug",
            "ee incident test",
            "ee vehicle test",
            "ee building",
            "ee building history",
            "ee building add-test test_colosseum Colosseum",
            "ee building info test_colosseum",
            "ee building infer test_colosseum",
            "ee building nearby 500",
            "ee building anomalies 500",
            "ee building damage test_colosseum 25 Siege",
            "ee building plan-test test_colosseum repair",
            "ee morph status",
            "ee morph jump 100",
            "ee morph landmark PARTHENON",
            "ee morph grammar rome TEMPLE",
            "ee morph jump -3000",
            "ee blockqueue status",
            "ee blockqueue clear",
            "ee history -3000",
            "ee history 0",
            "ee timeline",
            "ee timeline branch",
            "ee timeline divergences",
            "ee timeline rifts",
            "ee timeline roll pyramids_giza",
            "ee timeline diverge pyramids_giza player_intervention",
            "ee timeline cascade pyramids_giza player_intervention",
            "ee codex",
            "ee codex tree",
            "ee codex atlas",
            "ee codex dossier",
            "ee codex chronicle",
            "ee event info pyramids_giza",
            "ee event trigger pyramids_giza",
            "ee event prevent pyramids_giza Peace_Treaty",
            "ee divergence checks",
            "ee divergence confidence pyramids_giza",
            "ee divergence mark pyramids_giza 95",
            "ee civ info rome",
            "ee civ create sparta Spartan_Commonwealth",
            "ee territory create-test terr_rome_latium rome",
            "ee territory owner 419000 125000",
            "ee relation score rome carthage",
            "ee relation set rome carthage ALLIED",
            "ee relation score sumer egypt_ancient",
            "ee relation set sumer egypt_ancient ALLIED",
            "ee tech list",
            "ee tech status rome",
            "ee tech unlock rome agriculture",
            "ee tech unlock rome writing",
            "ee religion list",
            "ee religion convert rome christianity",
            "ee religion holywar rome carthage christianity",
            "ee religion excommunicate rome carthage",
            "ee class tension rome",
            "ee urban ratio rome",
            "ee population get indus_valley",
            "ee population set indus_valley 250000",
            "ee agriculture food indus_valley",
            "ee agriculture boost indus_valley",
            "ee agriculture climate indus_valley 120",
            "ee climate list",
            "ee climate active",
            "ee climate region indus_valley",
            "ee climate temp indus_valley 150",
            "ee climate precip indus_valley 200",
            "ee famine list",
            "ee famine trigger indus_valley",
            "ee famine end indus_valley",
            "ee migration list",
            "ee migration process",
            "ee economy treasury rome",
            "ee economy add rome 5000",
            "ee economy subtract rome 2000",
            "ee trade list",
            "ee trade open rome carthage CARAVAN",
            "ee trade logistics",
            "ee trade prices",
            "ee trade prices rome",
            "ee trade caravan route_rome_carthage",
            "ee trade ambush-test",
            "ee trade ship-test",
            "ee disease list",
            "ee disease status",
            "ee disease trigger bubonic_plague rome",
            "ee disease quarantine rome",
            "ee disease cordon rome",
            "ee disease doctor-test",
            "ee disease hospital-test",
            "ee disease infect-test",
            "ee disease infect-test black_death",
            "ee disaster VOLCANIC_WINTER",
            "ee war list",
            "ee war army test",
            "ee war declare rome carthage Territorial_expansion",
            "ee war cohorts",
            "ee war deploy legio_1",
            "ee war deploy legio_1 SHIELD_WALL",
            "ee war deploy legio_1 SHIELD_WALL 20",
            "ee war battle-test rome carthage",
            "ee war siege-test rome carthage",
            "ee sovereign court rome",
            "ee sovereign petition rome grain_subsidy",
            "ee sovereign treaty rome carthage ALLIANCE",
            "ee sovereign megaproject AQUEDUCT",
            "ee sovereign doctrine rome PHALANX",
            "ee sovereign tax rome 15",
            "ee sovereign debase rome",
            "ee sovereign coup rome Brutus",
            "ee ecs status",
            "ee ecs populate rome 100",
            "ee ecs benchmark 1000",
            "ee map sync",
            "ee audio atmosphere",
            "ee heraldry list",
            "ee heraldry give rome",
            "ee ecs flush",
            "ee brain availability",
            "ee brain config",
            "ee brain remote-start",
            "ee brain queue",
            "ee brain audit",
            "ee brain test",
            "ee brain affairs-propose",
            "ee brain anomaly-propose",
            "ee web status",
            "ee web start",
            "ee web url",
            "ee web stop",
            "ee bot connect agent_alpha",
            "ee bot briefing agent_alpha villager",
            "ee bot proposals agent_alpha",
            "ee bot proposal-test agent_alpha survey",
            "ee year set -90",
            "ee vip list",
            "ee vip info julius_caesar",
            "ee vip successors julius_caesar",
            "ee vip claim julius_caesar",
            "ee speed vote 15",
            "ee vip tag-self julius_caesar",
            "ee vip release",
            "ee vip takeover-test julius_caesar DUEL",
            "ee vip release",
            "ee villager virtual-count",
            "ee villager explain soldier",
            "ee villager nearby",
            "ee villager nearby 64",
            "ee villager who",
            "ee villager here",
            "ee villager spawn-test rome soldier",
            "ee villager vip-spawn-test julius_caesar",
            "ee npc virtual-count",
            "ee npc explain scholar",
            "ee npc nearby",
            "ee npc nearby 64",
            "ee npc who",
            "ee npc spawn-test rome scholar",
            "ee npc vip-spawn-test julius_caesar",
            "ee vip kill julius_caesar Ides_of_March",
            "ee vip clear-self-tag",
            "ee chrono status",
            "ee chrono start",
            "ee chrono mode STORY",
            "ee chrono phase 2",
            "ee chrono advance",
            "ee chrono mode EXPLORATION",
            "ee chrono rival",
            "ee chrono rival spawn",
            "ee chrono paradox start 120",
            "ee chrono paradox deliver",
            "ee chrono ending INFINITE_HORIZON",
            "ee chrono ending SOVEREIGN_CYCLE",
            "ee chrono ending SEVERED_KNOT",
            "ee debug core",
            "ee backup",
            "ee data status",
            "ee speed status",
            "ee performance"
    };

    private EeCommandSelfTest() {
    }

    public static int partCount() {
        return (COMMANDS.length + PART_SIZE - 1) / PART_SIZE;
    }

    public static int execute(CommandSourceStack source, String mode) {
        if ("list".equals(mode) || "help".equals(mode)) {
            return executeList(source);
        }
        boolean verbose = "all-verbose".equals(mode) || "verbose".equals(mode);
        return runRange(source, 0, COMMANDS.length, verbose);
    }

    public static int executePart(CommandSourceStack source, int part) {
        int parts = partCount();
        if (part < 1 || part > parts) {
            source.sendFailure(Component.literal("Part must be 1-" + parts));
            return 0;
        }
        int from = (part - 1) * PART_SIZE;
        int to = Math.min(COMMANDS.length, from + PART_SIZE);
        source.sendSuccess(() -> Component.literal("Running /ee test part " + part + "/" + parts
                + " (" + (from + 1) + "-" + to + ")"), false);
        return runRange(source, from, to, true);
    }

    private static int executeList(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== /ee test ===")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(
                COMMANDS.length + " commands. Run as OP 2+ player. Does not include /ee reset."), false);
        source.sendSuccess(() -> Component.literal("/ee test all — run every command, print FAILs + summary"), false);
        source.sendSuccess(() -> Component.literal("/ee test all verbose — also print each PASS"), false);
        source.sendSuccess(() -> Component.literal("/ee test part <1-" + partCount() + "> — run a 40-command slice"), false);
        source.sendSuccess(() -> Component.literal("/ee test list — this help"), false);
        int shown = Math.min(12, COMMANDS.length);
        for (int i = 0; i < shown; i++) {
            final int n = i;
            source.sendSuccess(() -> Component.literal("  " + (n + 1) + ". /" + COMMANDS[n])
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        source.sendSuccess(() -> Component.literal("  ... " + COMMANDS.length + " total"), false);
        return 1;
    }

    private static int runRange(CommandSourceStack source, int from, int to, boolean verbose) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("No Minecraft server on this command source."));
            return 0;
        }

        String playerName = source.getTextName();
        CaptureSource capture = new CaptureSource();
        CommandSourceStack runner = source.withSource(capture).withPermission(4);

        int pass = 0;
        int fail = 0;
        List<String> failures = new ArrayList<>();

        source.sendSuccess(() -> Component.literal("Eternal Engine command self-test starting ("
                + (to - from) + " commands)...").withStyle(ChatFormatting.AQUA), false);

        for (int i = from; i < to; i++) {
            String raw = COMMANDS[i].replace("{player}", playerName);
            if (raw.startsWith("ee test")) {
                continue;
            }
            capture.clear();
            int result;
            String error = "";
            try {
                result = server.getCommands().getDispatcher().execute(raw, runner);
            } catch (CommandSyntaxException e) {
                result = 0;
                error = e.getMessage() == null ? "parse error" : e.getMessage();
            } catch (Exception e) {
                result = 0;
                error = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            }
            String last = capture.lastMessage();
            boolean ok = result > 0;
            if (ok) {
                pass++;
                if (verbose) {
                    String preview = last.isEmpty() ? "ok" : trim(last, 80);
                    source.sendSuccess(() -> Component.literal("[PASS] /" + raw + "  " + preview)
                            .withStyle(ChatFormatting.GREEN), false);
                }
            } else {
                fail++;
                String reason = !error.isEmpty() ? error : (last.isEmpty() ? "returned " + result : last);
                reason = trim(reason, 120);
                String line = "[FAIL] /" + raw + "  " + reason;
                failures.add(line);
                source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.RED), false);
            }
        }

        final int passFinal = pass;
        final int failFinal = fail;
        source.sendSuccess(() -> Component.literal("=== /ee test done: PASS " + passFinal
                + "  FAIL " + failFinal + "  of " + (passFinal + failFinal) + " ===")
                .withStyle(failFinal == 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        if (failFinal > 0 && !verbose) {
            source.sendSuccess(() -> Component.literal("Re-run /ee test all verbose or /ee test part <n> for PASS lines.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return fail == 0 ? 1 : 0;
    }

    private static String trim(String text, int max) {
        String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, max) + "...";
    }

    private static final class CaptureSource implements CommandSource {
        private final List<String> messages = new ArrayList<>();

        void clear() {
            messages.clear();
        }

        String lastMessage() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
        }

        @Override
        public void sendSystemMessage(Component message) {
            if (message != null) {
                messages.add(message.getString());
            }
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}

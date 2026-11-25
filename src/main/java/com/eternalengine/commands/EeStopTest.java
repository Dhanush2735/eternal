package com.eternalengine.commands;

import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.here.HereAndNow;
import com.eternalengine.telemetry.WorldDebugSnapshot;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code /ee test here} — the whole stop procedure, one command.
 *
 * <h3>The twelve steps this replaces</h3>
 * Walking the era route by hand is twelve commands per stop, and five of the twelve must be in the
 * right order or the answer is measured against the wrong year, or read before the ground exists:
 *
 * <ol>
 *   <li>{@code /ee date <y> 1 1} — the clock, first;</li>
 *   <li>read the year back;</li>
 *   <li>{@code /tpll <lat> <lon>} — move (route stops only);</li>
 *   <li>hold the ground open ({@code forceload}, here {@code ServerLevel.setChunkForced});</li>
 *   <li>wait until the chunk really exists and has a surface — measured, not slept on;</li>
 *   <li>release the hold;</li>
 *   <li>{@code /ee here} — the card;</li>
 *   <li>{@code /ee probe} — the Terra++ snapshot;</li>
 *   <li>{@code /ee villager nearby 64};</li>
 *   <li>{@code /ee villager who};</li>
 *   <li>{@code /ee building nearby 500};</li>
 *   <li>{@code /ee civ info <the id the card names>}.</li>
 * </ol>
 *
 * <p>Steps 7–12 are not re-implemented here: they are <b>dispatched through Minecraft's own dispatcher</b>,
 * so what a player sees is exactly what typing them produces. What this class adds is the sequencing, the
 * waiting, a machine verdict, and a row of evidence in the world's own folder
 * ({@code ee_stop_tests.tsv}).
 *
 * <h3>Why it is a state machine and not a loop with sleeps</h3>
 * Everything here runs on the server thread, and a server thread may not sleep: a {@code Thread.sleep}
 * in a command freezes the tick loop the chunk generator is trying to use, which is the very thing being
 * waited for. So each stage is scheduled with {@link TickTask} and re-checks the world when it runs. The
 * chunk wait gives up after {@code STOP_WAIT_TICKS} and says so — "the ground did not arrive" is a
 * finding, not a crash.
 *
 * <h3>What it will not do</h3>
 * Move the clock backwards. {@code /ee date} refuses and so does this; a year earlier than now is a
 * fork of the world, and that decision stays with the player. It reports the year it found and tells you
 * the command that does move it.
 */
public final class EeStopTest {

    /** The route the era sheet gives, by id: name, latitude, longitude, year. */
    private static final Map<String, Object[]> STOPS = new LinkedHashMap<>();

    static {
        stop("R1", "Ur", 30.9626, 46.1031, -2500);
        stop("R2", "Giza", 29.9792, 31.1342, -2500);
        stop("R3", "Mohenjo-daro", 27.3294, 68.1386, -2500);
        stop("R4", "Anyang", 36.1267, 114.3133, -1200);
        stop("R5", "Athens", 37.9838, 23.7275, -430);
        stop("R6", "Pataliputra", 25.5941, 85.1376, -300);
        stop("R7", "Rome", 41.9028, 12.4964, 100);
        stop("R8", "Teotihuacan", 19.6925, -98.8438, 500);
        stop("R9", "Chang'an", 34.3416, 108.9398, 750);
        stop("R10", "Baghdad", 33.3152, 44.3661, 900);
        stop("R11", "Greenland's Norse farms", 61.1544, -45.5156, 1000);
        stop("R12", "Cahokia", 38.6550, -90.0610, 1100);
        stop("R13", "Angkor", 13.4125, 103.8670, 1150);
        stop("R14", "Great Zimbabwe", -20.2674, 30.9337, 1300);
        stop("R15", "Timbuktu", 16.7735, -3.0074, 1350);
        stop("R16", "the Sahara", 23.2833, 5.5333, 1000);
        stop("R17", "Tenochtitlan", 19.4326, -99.1332, 1500);
        stop("R18", "Cusco", -13.5319, -71.9675, 1500);
        stop("R19", "the Amazon", -3.1190, -60.0217, 1500);
        stop("R20", "Vijayanagara", 15.3350, 76.4600, 1520);
        stop("R21", "Delhi", 28.6139, 77.2090, 1650);
        stop("R22", "Edo", 35.6762, 139.6503, 1700);
        stop("R23", "London", 51.5074, -0.1278, 1700);
        stop("R24", "Paris", 48.8566, 2.3522, 1789);
        stop("R25", "Sydney", -33.8688, 151.2093, 1850);
        stop("R26", "New York", 40.7128, -74.0060, 1920);
        stop("R27", "Hyderabad", 17.3850, 78.4867, 1948);
        stop("R28", "Lagos", 6.5244, 3.3792, 1960);
        stop("R29", "Moscow", 55.7558, 37.6173, 1980);
        stop("R30", "Sao Paulo", -23.5505, -46.6333, 2020);
        stop("R31", "Siberia", 62.0355, 129.6755, 1900);
    }

    private static void stop(String id, String name, double lat, double lon, int year) {
        STOPS.put(id, new Object[]{name, lat, lon, year});
    }

    /** How long to wait for the ground to exist before giving up on it. */
    private static final int STOP_WAIT_TICKS = 20 * 180;
    /** Chunks held open around the player while the ground is built: 4 chunks = 9×9 = 144 blocks' radius. */
    private static final int HOLD_CHUNKS = 4;
    /** Ids that name no civilization; the same list the spawner and the checks use. */
    private static final java.util.Set<String> NOT_A_CIV =
            java.util.Set.of("neutral", "witness", "present", "ohm", "rival_dimension");

    private EeStopTest() {
    }

    /** The two leaves this adds to {@code /ee test}: {@code here} and {@code stop <id>}. */
    public static LiteralArgumentBuilder<CommandSourceStack> hereNode() {
        return Commands.literal("here")
                .executes(ctx -> start(ctx.getSource(), null, Integer.MIN_VALUE))
                .then(Commands.argument("year", IntegerArgumentType.integer(-300000, 3000))
                        .executes(ctx -> start(ctx.getSource(), null,
                                IntegerArgumentType.getInteger(ctx, "year"))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> stopNode() {
        return Commands.literal("stop")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> start(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id").toUpperCase(Locale.ROOT),
                                Integer.MIN_VALUE)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> listNode() {
        return Commands.literal("stops")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "The era route, " + STOPS.size() + " stops. /ee test stop <id>, or stand anywhere"
                                    + " and /ee test here [year]."), false);
                    for (Map.Entry<String, Object[]> e : STOPS.entrySet()) {
                        Object[] s = e.getValue();
                        ctx.getSource().sendSuccess(() -> Component.literal("  " + e.getKey() + "  " + s[0]
                                + "  " + s[1] + ", " + s[2] + "  " + HereAndNow.yearText((Integer) s[3])), false);
                    }
                    return 1;
                });
    }

    // ── the run ──────────────────────────────────────────────────────────────────────────────────

    /** One stop's evidence, filled as the stages complete. */
    private static final class Run {
        final ServerPlayer player;
        /** Where the report is printed: the player's own chat, or the console for a stand-in run. */
        final CommandSourceStack out;
        /** True when a route stop was run from the console with a stand-in actor. */
        final boolean standIn;
        final String stopId;
        final String placeName;
        int year;
        final List<String> notes = new ArrayList<>();
        String card = "";
        String verdict = "cannot judge";
        String detail = "";
        long startedAt = System.currentTimeMillis();
        int waitedTicks = 0;
        boolean groundReady;
        double lat;
        double lon;

        Run(ServerPlayer player, CommandSourceStack out, boolean standIn, String stopId,
            String placeName, int year) {
            this.player = player;
            this.out = out;
            this.standIn = standIn;
            this.stopId = stopId;
            this.placeName = placeName;
            this.year = year;
        }
    }

    private static int start(CommandSourceStack source, String stopId, int askedYear) {
        boolean standIn = false;
        ServerPlayer player;
        if (source.getEntity() instanceof ServerPlayer p) {
            player = p;
        } else if (stopId != null && source.getServer() != null
                && source.getServer().overworld() != null) {
            // A route stop knows where it is, so it does not need somebody to stand there: the console can
            // run it with a stand-in actor and read the whole report where the command came from. That is
            // what makes the route automatable over RCON. "Here" still needs a body -- see below.
            ServerLevel overworld = source.getServer().overworld();
            player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(overworld);
            standIn = true;
        } else {
            source.sendFailure(Component.literal("/ee test here needs a player: the card is about where"
                    + " somebody stands. From console, use /ee test stop <id> (a route stop knows where it"
                    + " is) or /ee check all."));
            return 0;
        }
        if (!SimulationEngine.isInitialized()) {
            source.sendFailure(Component.literal("The simulation is not running yet. Wait for the server to"
                    + " finish starting, then try again."));
            return 0;
        }
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("No server behind this command."));
            return 0;
        }

        String name = "here";
        int year = askedYear;
        if (stopId != null) {
            Object[] s = STOPS.get(stopId);
            if (s == null) {
                source.sendFailure(Component.literal("No stop called '" + stopId + "'. /ee test stops lists"
                        + " them all (" + STOPS.size() + ")."));
                return 0;
            }
            name = (String) s[0];
            if (year == Integer.MIN_VALUE) {
                year = (Integer) s[3];
            }
        }
        if (year == Integer.MIN_VALUE) {
            year = currentYear(source);
        }

        // The report goes where the request came from: the player's chat, or the console.
        CommandSourceStack out = standIn ? source : player.createCommandSourceStack();
        Run run = new Run(player, out, standIn, stopId, name, year);
        say(out, "§6=== /ee test " + (stopId == null ? "here " + year : stopId + " (" + name + ")") + " ==="
                + (standIn ? " §8(console run; a stand-in actor carries the position)§r" : ""));
        say(out, "§8the twelve steps, in order: clock, year, move, hold, wait, release, card,"
                + " probe, people, villager, buildings, civilisation§r");
        stageClock(server, run, 1);
        return 1;
    }

    // 1–2. the clock, then read it back
    private static void stageClock(MinecraftServer server, Run run, int round) {
        server.tell(new TickTask(1, () -> {
            CommandSourceStack src = run.out;
            int now = currentYear(src);
            if (run.year < now) {
                // The guardrail, kept: the clock only goes forward, and this is where a player learns it.
                src.sendFailure(Component.literal("Step 1 of 12 — refused: it is "
                        + HereAndNow.yearText(now) + " and this stop is " + HereAndNow.yearText(run.year)
                        + ". The clock only goes forward (/ee date). To read a past year without moving"
                        + " the clock, use /ee history " + run.year + ", or /ee date " + run.year + " 1 1"
                        + " if you mean to fork the world."));
                run.verdict = "cannot judge";
                run.detail = "the clock was not moved: " + HereAndNow.yearText(now) + " → "
                        + HereAndNow.yearText(run.year) + " is backwards";
                write(run, src);
                return;
            }
            if (run.year > now) {
                say(src, "1/12  setting the clock to " + HereAndNow.yearText(run.year));
                SimulationClockManager.getInstance().setCurrentYear(run.year);
                // the year is read back on the next tick: setting it may jump Terra++'s own clock too
                server.tell(new TickTask(5, () -> stageClockReadback(server, run, round)));
            } else {
                say(src, "1/12  the clock is already " + HereAndNow.yearText(now) + " — nothing to set");
                stageClockReadback(server, run, round);
            }
        }));
    }

    private static void stageClockReadback(MinecraftServer server, Run run, int round) {
        CommandSourceStack src = run.out;
        int year = currentYear(src);
        if (Math.abs(year - run.year) > 2 && round < 6) {
            // Terra++'s calendar can land a year or two away from the number asked; ask again instead of
            // pretending, and give up saying what it really is.
            stageClock(server, run, round + 1);
            return;
        }
        say(src, "2/12  the year reads back " + HereAndNow.yearText(year));
        run.year = year;
        stageMove(server, run);
    }

    // 3. move (route stops only), then 4. hold the ground
    private static void stageMove(MinecraftServer server, Run run) {
        CommandSourceStack src = run.out;
        if (run.stopId == null) {
            double[] geo = TerraCoordinateHelper.toGeo(run.player.blockPosition());
            if (geo == null) {
                fail(src, run, "Terra++ cannot say where you are standing, so there is no place to read.");
                return;
            }
            run.lat = geo[1];
            run.lon = geo[0];
            say(src, "3/12  standing at " + fmt(run.lat) + ", " + fmt(run.lon) + " — no jump needed");
            stageHold(server, run);
            return;
        }
        Object[] s = STOPS.get(run.stopId);
        double lat = (Double) s[1];
        double lon = (Double) s[2];
        run.lat = lat;
        run.lon = lon;
        BlockPos pos = TerraCoordinateHelper.fromGeo(lat, lon, run.player.level());
        if (pos == null) {
            fail(src, run, "Terra++ cannot place " + fmt(lat) + ", " + fmt(lon) + " on this world.");
            return;
        }
        say(src, "3/12  jumping to " + run.placeName + " — block " + pos.getX() + ", " + pos.getZ());
        run.player.teleportTo((ServerLevel) run.player.level(), pos.getX() + 0.5d, pos.getY() + 1.0d,
                pos.getZ() + 0.5d, run.player.getYRot(), run.player.getXRot());
        // A stand-in actor cannot always be moved (it has no connection to move through). The reading
        // does not depend on the body: run.lat/run.lon are the stop's own coordinates, and everything
        // that follows is measured from those. But say which case it is, rather than implying a player
        // is standing there when none is.
        BlockPos after = run.player.blockPosition();
        boolean moved = Math.abs(after.getX() - pos.getX()) < 64 && Math.abs(after.getZ() - pos.getZ()) < 64;
        if (!moved) {
            say(src, "      (the stand-in could not be moved to the stop; the reading is taken at the"
                    + " stop's own coordinates " + fmt(lat) + ", " + fmt(lon) + " regardless)");
        }
        server.tell(new TickTask(10, () -> stageHold(server, run)));
    }

    // 4. hold, then 5. wait for the ground (measured), then 6. release
    private static void stageHold(MinecraftServer server, Run run) {
        CommandSourceStack src = run.out;
        ServerLevel level = run.player.serverLevel();
        BlockPos p = run.player.blockPosition();
        int cx = p.getX() >> 4;
        int cz = p.getZ() >> 4;
        int held = 0;
        for (int dx = -HOLD_CHUNKS; dx <= HOLD_CHUNKS; dx++) {
            for (int dz = -HOLD_CHUNKS; dz <= HOLD_CHUNKS; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
                held++;
            }
        }
        say(src, "4/12  holding " + held + " chunks open around you while the ground is built");
        stageWait(server, run, cx, cz, held);
    }

    private static void stageWait(MinecraftServer server, Run run, int cx, int cz, int held) {
        server.tell(new TickTask(20, () -> {
            CommandSourceStack src = run.out;
            ServerLevel level = run.player.serverLevel();
            run.waitedTicks += 20;
            int solid = solidSamples(level, cx, cz);
            boolean ready = solid > 0;
            if (ready) {
                run.groundReady = true;
                release(level, cx, cz);
                say(src, "5/12  the ground is there — " + solid + "/9 sample columns have real ground, "
                        + hereThing(level, run) + " (waited " + (run.waitedTicks / 20) + " s)");
                say(src, "6/12  the hold is released");
                server.tell(new TickTask(10, () -> stageRead(server, run)));
                return;
            }
            if (run.waitedTicks >= STOP_WAIT_TICKS) {
                release(level, cx, cz);
                run.groundReady = false;
                run.verdict = "cannot judge";
                run.detail = "the ground never arrived: " + (run.waitedTicks / 20)
                        + " s and the chunk is still empty";
                say(src, "§c5/12  the ground did not arrive after " + (run.waitedTicks / 20)
                        + " s — giving up on this stop§r");
                say(src, "      try: walk closer first, raise the view distance, or /ee scan chunk 8"
                        + " to see what the chunk holds");
                say(src, "6/12  the hold is released");
                write(run, src);
                return;
            }
            stageWait(server, run, cx, cz, held);
        }));
    }

    /**
     * How many of nine points in the chunk have ground: the chunk exists at FULL status and there is a
     * block that is neither air nor bedrock under the world's own heightmap. Zero means "not built yet".
     */
    private static int solidSamples(ServerLevel level, int cx, int cz) {
        int solid = 0;
        try {
            var chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) {
                return 0;
            }
            for (int dx : new int[]{2, 8, 14}) {
                for (int dz : new int[]{2, 8, 14}) {
                    int x = (cx << 4) + dx;
                    int z = (cz << 4) + dz;
                    // The heightmap, not Terra's own number: on a plain world Terra's number can sit
                    // below the build limit and the clamp would find bedrock at the bottom and call an
                    // empty chunk "ready". The heightmap is the world's own answer for "where is the top".
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                            x, z);
                    if (y <= level.getMinBuildHeight() + 2) {
                        continue;
                    }
                    var block = level.getBlockState(new BlockPos(x, y - 1, z));
                    if (!block.isAir() && !block.is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                        solid++;
                    }
                }
            }
        } catch (Throwable ignored) {
            // an unreadable chunk is not ground
        }
        return solid;
    }

    private static void release(ServerLevel level, int cx, int cz) {
        for (int dx = -HOLD_CHUNKS; dx <= HOLD_CHUNKS; dx++) {
            for (int dz = -HOLD_CHUNKS; dz <= HOLD_CHUNKS; dz++) {
                level.setChunkForced(cx + dx, cz + dz, false);
            }
        }
    }

    private static String hereThing(ServerLevel level, Run run) {
        try {
            BlockPos p = stopBlock(run);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                    p.getX(), p.getZ());
            String ground = level.getBlockState(new BlockPos(p.getX(), y - 1, p.getZ()))
                    .getBlock().getName().getString();
            return "ground is " + ground + " at y=" + (y - 1);
        } catch (Throwable t) {
            return "ground check unavailable";
        }
    }

    /** The card, as text, from the same class {@code /ee here} prints. */
    private static String cardText(Run run) {
        try {
            return HereAndNow.read(run.lat, run.lon, run.year).text();
        } catch (Throwable t) {
            return "(the reading failed: " + t + ")";
        }
    }

    /** The block the stop sits at, whatever body asked for the reading. */
    private static BlockPos stopBlock(Run run) {
        BlockPos pos = TerraCoordinateHelper.fromGeo(run.lat, run.lon, run.player.serverLevel());
        if (pos != null) {
            return pos;
        }
        BlockPos p = run.player.blockPosition();
        double[] xz = TerraCoordinateHelper.fromGeo(run.lat, run.lon);
        return xz == null ? p
                : new BlockPos((int) Math.floor(xz[0]), p.getY(), (int) Math.floor(xz[1]));
    }

    private static String probeText(Run run) {
        try {
            BlockPos at = stopBlock(run);
            WorldDebugSnapshot s = WorldDebugSnapshot.capture(run.player.serverLevel(), at.getX(), at.getZ());
            return s.valid() ? s.toMultilineString().replace('\n', ' ') : "probe: " + s.failureReason();
        } catch (Throwable t) {
            return "probe failed: " + t;
        }
    }

    private static List<String> peopleText(Run run) {
        List<String> out = new ArrayList<>();
        try {
            ServerLevel level = run.player.serverLevel();
            BlockPos at = stopBlock(run);
            AABB box = new AABB(at).inflate(64.0d);
            var npcs = level.getEntitiesOfClass(HumanNpcEntity.class, box);
            out.add("people within 64 blocks of " + at.getX() + ", " + at.getZ() + ": " + npcs.size());
            int shown = 0;
            for (HumanNpcEntity npc : npcs) {
                if (shown++ >= 8) {
                    out.add("  ... and " + (npcs.size() - 8) + " more");
                    break;
                }
                out.add("  " + npc.getName().getString() + " - " + npc.getRole()
                        + " of " + npc.getCivilizationId());
            }
            if (npcs.isEmpty()) {
                out.add("  (nobody is standing within 64 blocks of this spot)");
            }
        } catch (Throwable t) {
            out.add("people failed: " + t);
        }
        return out;
    }

    private static List<String> buildingText(Run run) {
        List<String> out = new ArrayList<>();
        try {
            var idx = SimulationEngine.getInstance().getBuildingHistoryDatabase().spatialIndex();
            var hits = idx.queryRadius(run.lat, run.lon, 500);
            out.add("buildings within 500 m: " + hits.size());
            int shown = 0;
            for (var e : hits) {
                if (shown++ >= 8) {
                    break;
                }
                out.add("  " + e.id() + "  " + e.lat() + ", " + e.lon());
            }
        } catch (Throwable t) {
            out.add("buildings failed: " + t);
        }
        return out;
    }

    // 7-12. the six commands, dispatched exactly as a player would type them
    private static void stageRead(MinecraftServer server, Run run) {
        String civId = namedCivilization(run.out, run);
        CommandSourceStack src = run.out;
        // Each command is dispatched as a player would type it, so the player sees the real thing; and
        // each is ALSO read directly, so the run leaves a record even when nobody is watching a chat
        // window (a console run's reply closes before these stages finish).
        say(src, "7/12  the card:");
        dispatch(server, src, "ee here");
        run.card = cardText(run);
        for (String line : run.card.split("\n")) {
            if (!line.isBlank()) {
                LOG.info("[EE test]   {}", line);
            }
        }
        say(src, "8/12  the probe:");
        dispatch(server, src, "ee probe");
        LOG.info("[EE test]   {}", probeText(run));
        say(src, "9/12  the people:");
        dispatch(server, src, "ee villager nearby 64");
        for (String line : peopleText(run)) {
            LOG.info("[EE test]   {}", line);
        }
        say(src, "10/12 the villager you are looking at:");
        dispatch(server, src, "ee villager who");
        LOG.info("[EE test]   (this one needs somebody standing in front of you; the run cannot see"
                + " what you are looking at)");
        say(src, "11/12 the buildings:");
        dispatch(server, src, "ee building nearby 500");
        for (String line : buildingText(run)) {
            LOG.info("[EE test]   {}", line);
        }
        say(src, "12/12 the civilisation the card names -> " + (civId == null ? "(none)" : civId) + ":");
        if (civId != null) {
            dispatch(server, src, "ee civ info " + civId);
        }
        stageVerdict(server, run, civId);
    }

    // the machine half of the judgement: the same four questions the procedure asks, answered from data
    private static void stageVerdict(MinecraftServer server, Run run, String civId) {
        CommandSourceStack src = run.out;
        List<String> wrong = new ArrayList<>();
        String summary;

        boolean alive = false;
        String civName = "(none)";
        var civs = SimulationEngine.getInstance().getCivilizationManager();
        if (civId != null) {
            var civ = civs.getCivilization(civId);
            if (civ.isEmpty()) {
                wrong.add("the card names '" + civId + "', which is no civilization in any pack");
            } else {
                civName = civ.get().name() + " (" + HereAndNow.yearText(civ.get().foundingYear())
                        + "–" + HereAndNow.yearText(civ.get().fallYear()) + ")";
                alive = civ.get().isActiveInYear(run.year);
                if (!alive) {
                    wrong.add(civName + " did not stand in " + HereAndNow.yearText(run.year));
                }
            }
        }

        int people = 0;
        int outside = 0;
        String firstOutside = null;
        try {
            ServerLevel level = run.player.serverLevel();
            AABB box = new AABB(stopBlock(run)).inflate(64.0d);
            for (HumanNpcEntity npc : level.getEntitiesOfClass(HumanNpcEntity.class, box)) {
                String id = npc.getCivilizationId();
                if (id == null || id.isBlank() || NOT_A_CIV.contains(id.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                people++;
                var c = civs.getCivilization(id);
                if (c.isEmpty() || !c.get().isActiveInYear(run.year)) {
                    outside++;
                    if (firstOutside == null) {
                        firstOutside = npc.getName().getString() + " carries " + id;
                    }
                }
            }
        } catch (Throwable ignored) {
            wrong.add("the people could not be read");
        }
        if (outside > 0) {
            wrong.add(outside + " of " + people + " people within 64 blocks are outside their"
                    + " civilization's years (" + firstOutside + ")");
        }

        if (wrong.isEmpty()) {
            run.verdict = "pass";
            summary = "the answer holds: " + civName + " in " + HereAndNow.yearText(run.year)
                    + (people > 0 ? ", and all " + people + " people around you belong to civilizations alive"
                    + " that year" : ", and nobody nearby is carrying a civilization that did not stand");
        } else {
            run.verdict = "fail";
            summary = String.join("; ", wrong);
        }
        run.detail = summary;
        say(src, "§6=== verdict: " + run.verdict.toUpperCase(Locale.ROOT) + " ===§r " + summary);
        say(src, "§8the look — buildings, dress, crowd, road — is yours to judge; this command scores the"
                + " data. Evidence row written to ee_stop_tests.tsv in the world folder.§r");
        write(run, src);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Runs a real command through the dispatcher so the output is the player's own. Returns its chat. */
    private static String dispatch(MinecraftServer server, CommandSourceStack src, String command) {
        try {
            server.getCommands().performPrefixedCommand(src, command);
            return command;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("  (" + command + " failed: " + t.getMessage() + ")"));
            return "";
        }
    }

    /** The civilization the card names, asked of the same authority the spawner uses. */
    private static String namedCivilization(CommandSourceStack src, Run run) {
        try {
            var civs = SimulationEngine.getInstance().getCivilizationManager();
            String id = com.eternalengine.civilization.HistoricalPresence
                    .query(civs, run.lat, run.lon, run.year).spawnCivId();
            return id == null || id.isBlank() || NOT_A_CIV.contains(id.toLowerCase(Locale.ROOT)) ? null : id;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int currentYear(CommandSourceStack src) {
        try {
            if (SimulationClockManager.isInitialized()) {
                return SimulationClockManager.getInstance().getCurrentYear();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            return WorldStateManager.getInstance().getCurrentYear();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** One row of evidence, appended to the world's own folder so it travels with the save. */
    private static void write(Run run, CommandSourceStack src) {
        try {
            Path dir = run.player.server.overworld().getServer().getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT);
            Path file = dir.resolve("ee_stop_tests.tsv").toAbsolutePath().normalize();
            boolean fresh = !Files.exists(file);
            StringBuilder row = new StringBuilder();
            if (fresh) {
                row.append("when\tstop\tplace\tyear\tlat\tlon\tground_ready\twaited_s\tverdict"
                        + "\tdetail\tcard\n");
            }
            row.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\t')
                    .append(run.stopId == null ? "here" : run.stopId).append('\t')
                    .append(run.placeName).append('\t')
                    .append(run.year).append('\t')
                    .append(fmt(run.lat)).append('\t')
                    .append(fmt(run.lon)).append('\t')
                    .append(run.groundReady).append('\t')
                    .append(run.waitedTicks / 20).append('\t')
                    .append(run.verdict).append('\t')
                    .append(oneLine(run.detail)).append('\t')
                    .append(oneLine(run.card)).append('\n');
            Files.createDirectories(file.getParent());
            Files.writeString(file, row.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            say(src, "§8evidence: " + file + "§r");
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("eternalengine")
                    .info("[stop test] could not write the evidence row: {}", String.valueOf(t));
        }
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static void fail(CommandSourceStack src, Run run, String why) {
        run.verdict = "cannot judge";
        run.detail = why;
        src.sendFailure(Component.literal(why));
        write(run, src);
    }

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("eternalengine");

    /**
     * Says a line to whoever asked, and writes it to the server log as well.
     *
     * <p>The log copy is not decoration. The stage machine answers across several ticks, and a console
     * (RCON) reply is closed when the command returns — anything said afterwards would land on the next
     * command's reply, or nowhere. Logging every line means a run leaves a complete record even when it
     * is driven by a script with no player watching, and it is what the evidence row points at.
     */
    private static void say(CommandSourceStack src, String line) {
        String clean = line.replaceAll("\u00a7.", "");
        LOG.info("[EE test] {}", clean);
        src.sendSuccess(() -> Component.literal(line), false);
    }
}

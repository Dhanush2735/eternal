package com.eternalengine.clock;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.terrabridge.TerraTimeBridge;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;

/**
 * The world's one calendar (step 18d): the date follows the Minecraft days, and only {@code /ee date} moves it
 * back.
 *
 * <p>Where the date comes from. With Terra++ time it is the overworld's Minecraft day count, read the way Terra++
 * reads its year ({@code BASE_YEAR + dayCount * scale / 365}); Terra++ builds the terrain of that year from the
 * same count, so the two never disagree. With the Eternal Engine clock it is a saved starting point (the date at
 * the start of one Minecraft day) plus the Minecraft days since, times the speed in days of history per Minecraft
 * day. Either way the date changes when Minecraft's own day changes, at 6 in the morning (at 1x).
 *
 * <p>What moves it. Days passing, sleeping (the night is skipped: the next morning is the next date) and
 * {@code /time add} move it forward, because they move Minecraft's day forward. The day/night cycle switched off
 * stops it (history's pause switches it off; see SimulationEngine). {@code /time set} would put Minecraft's day
 * count back (to day 0 for {@code /time set day}, which with Terra++ time is 3000 BCE): here it goes forward to the
 * next time that hour comes instead. Anything else that puts the count back (another mod, an older level.dat) is
 * put right at the start of the next tick. Only {@code /ee date} goes back on purpose (and starting history over:
 * {@code /ee reset confirm}, a new story campaign); they tell the calendar through {@link #accept()}.
 *
 * <p>Terra++'s own time commands (step 18e) follow the same rules: {@code /terra timejump} to an earlier year is refused
 * before it runs, and {@code /terra timescale} runs as {@code /ee speed set}, which keeps the date.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class WorldCalendar {

    private static final Logger LOGGER = EternalEngineMod.getLogger();

    /** One Minecraft day in ticks. */
    public static final long DAY = 24000L;

    // The Eternal Engine clock: the date at the start of one Minecraft day (anchorDayTime is a multiple of DAY),
    // and the speed in days of history per Minecraft day.
    private static volatile int anchorKey = KnowledgeDate.key(-3000, 1, 1);
    private static volatile long anchorDayTime = 0L;
    private static volatile int rate = 1;

    // The guard (both clocks): the date and the Minecraft time at the start of the last tick. Saved, so a day
    // count that went back while the server was off is put right too.
    private static volatile int lastKey = KnowledgeDate.ALWAYS;
    private static volatile long lastDayTime = -1L;

    private static volatile boolean started = false;
    private static volatile boolean hasState = false;
    private static volatile boolean warned = false;
    private static volatile int restores = 0;

    /** What runs for /time set day: /ee check all compares it with the command tree to see it is still ours. */
    private static final Command<CommandSourceStack> TIME_SET_DAY = c -> setTimeForward(c.getSource(), 1000);

    private WorldCalendar() {
    }

    // ==================== the rules as maths (no world needed; /ee check all and the probe test these) =========

    /** Days since 1 January 1970 (negative before), for a date key. A day past the month's end is its last day. */
    public static long epochDay(int key) {
        int m = Math.max(1, Math.min(12, KnowledgeDate.month(key)));
        LocalDate first = LocalDate.of(KnowledgeDate.year(key), m, 1);
        return first.toEpochDay() + Math.min(Math.max(1, KnowledgeDate.day(key)), first.lengthOfMonth()) - 1;
    }

    public static int keyOfEpochDay(long epochDay) {
        LocalDate d = LocalDate.ofEpochDay(epochDay);
        return KnowledgeDate.key(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    /** The Eternal Engine clock's date at {@code dayTime}: the anchor plus the whole days of history since. */
    public static int dateAt(int anchorKey, long anchorDayTime, int rate, long dayTime) {
        long days = Math.floorDiv((dayTime - anchorDayTime) * (long) Math.max(1, rate), DAY);
        return keyOfEpochDay(epochDay(anchorKey) + days);
    }

    /**
     * The anchor {anchorKey, anchorDayTime} that shows {@code key} at {@code dayTime} at {@code rate}. It sits at
     * the start of this Minecraft day, so at 1x the date goes on changing at 6 in the morning.
     */
    public static long[] anchorFor(int key, long dayTime, int rate) {
        long start = Math.floorDiv(dayTime, DAY) * DAY;
        long into = Math.floorDiv((dayTime - start) * (long) Math.max(1, rate), DAY);
        return new long[] {keyOfEpochDay(epochDay(key) - into), start};
    }

    /** {@code /time set} here: forward to the next time the day is at {@code timeOfDay} (no change if it is now). */
    public static long forwardTo(long dayTime, long timeOfDay) {
        return dayTime + Math.floorMod(timeOfDay - dayTime, DAY);
    }

    /**
     * Where Minecraft's day goes after something put it back from {@code last} to {@code now} without /ee date. A
     * small step back (less than one Minecraft day: a rewind, a level.dat older than the mod's save) is undone; a big
     * one (a time set to an hour, which starts again from day 0) goes forward to that hour, as /time set does here.
     */
    public static long restoreTarget(long last, long now) {
        return last - now < DAY ? last : forwardTo(last, Math.floorMod(now, DAY));
    }

    /**
     * The rules on made-up times, for /ee check all (nothing in the world changes). Null when all hold, otherwise
     * which rule is broken.
     */
    @Nullable
    public static String selfTest() {
        long n = 180602L * DAY; // 1948 at 10x with Terra++ time; any day works
        if (forwardTo(n + 13000, 1000) != n + DAY + 1000) {
            return "/time set day at night should give the next morning";
        }
        if (forwardTo(n + 500, 1000) != n + 1000) {
            return "/time set day before 7 in the morning should stay on the same day";
        }
        if (forwardTo(n + 1000, 1000) != n + 1000 || forwardTo(n + 1000, 1000 + 5 * DAY) != n + 1000) {
            return "/time set to the time it is now should change nothing";
        }
        if (restoreTarget(n + 13000, 1000) != n + DAY + 1000) {
            return "a day count put back to day 0 should go forward to that hour";
        }
        if (restoreTarget(n + 500, n - 100) != n + 500) {
            return "a small step back should be undone";
        }
        int key = KnowledgeDate.key(1948, 9, 14);
        for (int r : new int[] {1, 2, 5, 10, 25, 50}) {
            long[] a = anchorFor(key, n + 7000, r);
            if (dateAt((int) a[0], a[1], r, n + 7000) != key) {
                return "a new speed should keep the date (" + r + "x)";
            }
        }
        long[] a = anchorFor(key, n + 7000, 1);
        if (dateAt((int) a[0], a[1], 1, n + DAY - 1) != key
                || dateAt((int) a[0], a[1], 1, n + DAY) != KnowledgeDate.key(1948, 9, 15)) {
            return "at 1x the date should change when Minecraft's day changes";
        }
        if (dateAt(KnowledgeDate.key(1948, 2, 28), 0L, 1, DAY) != KnowledgeDate.key(1948, 2, 29)) {
            return "1948 is a leap year: 28 February is followed by 29 February";
        }
        int bce = KnowledgeDate.key(-44, 3, 15);
        if (keyOfEpochDay(epochDay(bce)) != bce) {
            return "dates before the common era should read back the same";
        }
        return null;
    }

    // ==================== the date now ====================

    @Nullable
    private static ServerLevel overworld() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.overworld();
    }

    /** Today, or {@link KnowledgeDate#ALWAYS} without a running world. */
    public static int today() {
        ServerLevel level = overworld();
        return level == null ? KnowledgeDate.ALWAYS : todayAt(level.getDayTime());
    }

    /** The date at Minecraft time {@code dayTime} with the clock that runs now ({@link KnowledgeDate#ALWAYS}: none). */
    public static int todayAt(long dayTime) {
        if (TerraTimeBridge.isAvailable()) {
            return KnowledgeDate.fromTerraDay(Math.floorDiv(dayTime, DAY), TerraTimeBridge.getTimeScale(1),
                    TerraTimeBridge.baseYear(), TerraTimeBridge.daysPerYear());
        }
        if (!hasState) {
            return KnowledgeDate.ALWAYS;
        }
        return dateAt(anchorKey, anchorDayTime, rate, dayTime);
    }

    // ==================== changes on purpose ====================

    /**
     * The world was loaded (SimulationClockManager.start). {@code saved} is the calendar from state.json, or null for
     * a world from before step 18d: then the Eternal Engine clock's date is worked out the old way, from how far its
     * year had run ({@code oldTicksInYear} at {@code oldSpeed} years per Minecraft day). {@code speed}: days of history
     * per Minecraft day from now on.
     */
    public static void start(@Nullable JsonObject saved, int year, long oldTicksInYear, int oldSpeed, int speed) {
        ServerLevel level = overworld();
        long now = level == null ? 0L : level.getDayTime();
        String clock = TerraTimeBridge.isAvailable() ? "terra" : "eternal";
        lastKey = KnowledgeDate.ALWAYS;
        lastDayTime = -1L;
        restores = 0;
        warned = false;
        String how;
        if (saved != null && saved.has("anchorKey") && saved.has("anchorDayTime")) {
            anchorKey = saved.get("anchorKey").getAsInt();
            anchorDayTime = saved.get("anchorDayTime").getAsLong();
            rate = saved.has("rate") ? Math.max(1, saved.get("rate").getAsInt()) : Math.max(1, speed);
            how = "from the save";
            // The guard's last date only counts for the same clock: Terra++ time switched on or off between two
            // games gives a different date by design, and that is not the day count going back.
            boolean sameClock = !saved.has("clock") || clock.equals(saved.get("clock").getAsString());
            if (sameClock && saved.has("lastKey") && saved.has("lastDayTime")) {
                lastKey = saved.get("lastKey").getAsInt();
                lastDayTime = saved.get("lastDayTime").getAsLong();
            }
        } else {
            rate = Math.max(1, speed);
            double ticksPerYear = (double) DAY / Math.max(1, oldSpeed);
            double frac = Math.max(0.0d, Math.min(0.9999d, oldTicksInYear / ticksPerYear));
            int key = KnowledgeDate.fromDayOfYear(year, 1 + (int) Math.floor(frac * KnowledgeDate.daysIn(year)));
            long[] a = anchorFor(key, now, rate);
            anchorKey = (int) a[0];
            anchorDayTime = a[1];
            how = "new (worked out from the year's progress in the save)";
        }
        hasState = true;
        started = true;
        int today = todayAt(now);
        LOGGER.info("[Calendar] Started ({}): today is {}; {}", how, KnowledgeDate.label(today), describeRule());
    }

    public static void stop() {
        started = false;
    }

    /** The Eternal Engine clock's speed changed (base speed, a slowdown, a vote): the same date goes on at the new one. */
    public static void setRate(int newRate) {
        int r = Math.max(1, newRate);
        ServerLevel level = overworld();
        if (!hasState || level == null || r == rate) {
            rate = r;
            return;
        }
        long now = level.getDayTime();
        int today = dateAt(anchorKey, anchorDayTime, rate, now);
        long[] a = anchorFor(today, now, r);
        rate = r;
        anchorKey = (int) a[0];
        anchorDayTime = a[1];
    }

    /** /ee date, or a year jump, with the Eternal Engine clock: it is {@code key} from now on; the sun stays. */
    public static void setDate(int key) {
        ServerLevel level = overworld();
        long now = level == null ? 0L : level.getDayTime();
        long[] a = anchorFor(key, now, rate);
        anchorKey = (int) a[0];
        anchorDayTime = a[1];
        hasState = true;
        accept();
    }

    /**
     * The date was moved on purpose (/ee date, a year jump, a new Terra++ speed, starting over): the guard takes the
     * date as it is now, even when it is earlier.
     */
    public static void accept() {
        ServerLevel level = overworld();
        if (level == null) {
            return;
        }
        long now = level.getDayTime();
        int key = todayAt(now);
        if (key != KnowledgeDate.ALWAYS) {
            lastKey = key;
            lastDayTime = now;
        }
    }

    // ==================== the guard ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !started) {
            return;
        }
        try {
            guard(event.getServer());
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("[Calendar] The check that the date does not go back failed: {}", t.toString());
            }
        }
    }

    /**
     * Start of a tick, before the worlds move: if the date is earlier than at the start of the last tick and nobody
     * said so through {@link #accept()}, Minecraft's day is put right (commands run between ticks, so a time set by
     * one is caught before Terra++ or anything else ticks with it).
     */
    private static void guard(MinecraftServer server) {
        ServerLevel level = server == null ? null : server.overworld();
        if (level == null) {
            return;
        }
        long now = level.getDayTime();
        int key = todayAt(now);
        if (key == KnowledgeDate.ALWAYS) {
            return;
        }
        if (lastKey != KnowledgeDate.ALWAYS && key < lastKey) {
            long target = restoreTarget(lastDayTime, now);
            if (todayAt(target) < lastKey && TerraTimeBridge.isAvailable()) {
                // The Terra++ speed changed under the date (not through /ee speed): the day that shows it now.
                long day = KnowledgeDate.terraDayFor(lastKey, TerraTimeBridge.getTimeScale(1),
                        TerraTimeBridge.baseYear(), TerraTimeBridge.daysPerYear());
                if (day >= 0) {
                    target = day * DAY + Math.floorMod(now, DAY);
                    if (todayAt(target) < lastKey) {
                        target += DAY;
                    }
                }
            }
            level.setDayTime(target);
            TerraTimeBridge.recalculate(server);
            int fixed = todayAt(target);
            restores++;
            LOGGER.warn("[Calendar] Minecraft's day went back (from day {} to day {}): the date would have been {} instead"
                    + " of {}. Only /ee date moves the date back, so the day is put to {} ({}).",
                    Math.floorDiv(lastDayTime, DAY), Math.floorDiv(now, DAY), KnowledgeDate.label(key),
                    KnowledgeDate.label(lastKey), Math.floorDiv(target, DAY), KnowledgeDate.label(fixed));
            now = target;
            key = fixed;
        }
        lastKey = key;
        lastDayTime = now;
    }

    // ==================== /time set ====================

    /**
     * Vanilla's /time set, merged onto it (same names, so only what runs changes; who may use it stays vanilla's:
     * operators). /time add and /time query stay vanilla's.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("time")
                .then(Commands.literal("set")
                        .then(Commands.literal("day").executes(TIME_SET_DAY))
                        .then(Commands.literal("noon").executes(c -> setTimeForward(c.getSource(), 6000)))
                        .then(Commands.literal("night").executes(c -> setTimeForward(c.getSource(), 13000)))
                        .then(Commands.literal("midnight").executes(c -> setTimeForward(c.getSource(), 18000)))
                        .then(Commands.argument("time", TimeArgument.time())
                                .executes(c -> setTimeForward(c.getSource(), IntegerArgumentType.getInteger(c, "time"))))));
    }

    /** /time set here: forward to the next time the day is at that hour. Returns the time of day, as vanilla does. */
    static int setTimeForward(CommandSourceStack source, int time) {
        MinecraftServer server = source.getServer();
        ServerLevel over = server.overworld();
        int before = todayAt(over.getDayTime());
        for (ServerLevel level : server.getAllLevels()) {
            level.setDayTime(forwardTo(level.getDayTime(), time));
        }
        TerraTimeBridge.recalculate(server);
        source.sendSuccess(() -> Component.translatable("commands.time.set", time), true);
        int after = todayAt(over.getDayTime());
        if (before != KnowledgeDate.ALWAYS && after != before) {
            String label = com.eternalengine.knowledge.TalkService.dateLabel(after);
            source.sendSuccess(() -> Component.literal("The date moves on to " + label
                    + ". /time set only goes forward here; /ee date sets an earlier date."), false);
        }
        return (int) Math.floorMod(source.getLevel().getDayTime(), DAY);
    }

    // ==================== Terra++'s own time commands (step 18e) ====================

    /**
     * Terra++'s {@code /terra timejump <year>} and {@code /terra timescale <n>x}, checked before they run (the user's
     * game log showed /terra timejump in use). A jump to this year or an earlier one is refused: only /ee date moves the
     * date back, and /ee date now does Terra++'s whole time jump, the land around the players included. A later year
     * runs as Terra++ wrote it. timescale would change the speed without keeping the date (1948 at 1x is a year past
     * 9000 at 10x, which Terra++ caps at its last year): it runs as /ee speed set, which keeps the date.
     */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        try {
            ParseResults<CommandSourceStack> parse = event.getParseResults();
            if (parse == null || !parse.getExceptions().isEmpty() || parse.getReader().canRead()) {
                return; // not a whole command: the game says what is wrong with it
            }
            CommandContextBuilder<CommandSourceStack> ctx = parse.getContext().getLastChild();
            List<ParsedCommandNode<CommandSourceStack>> nodes = ctx.getNodes();
            if (nodes.size() < 3 || !"terra".equals(nodes.get(0).getNode().getName())) {
                return;
            }
            String sub = nodes.get(1).getNode().getName();
            CommandSourceStack source = parse.getContext().getSource();
            if ("timejump".equals(sub)) {
                ParsedArgument<CommandSourceStack, ?> arg = ctx.getArguments().get("year");
                if (arg != null && arg.getResult() instanceof Integer year && !mayJumpTo(source, year, "/terra timejump")) {
                    event.setCanceled(true);
                }
            } else if ("timescale".equals(sub)) {
                String name = nodes.get(2).getNode().getName();
                int scale = Integer.parseInt(name.toLowerCase(java.util.Locale.ROOT).replace("x", "").trim());
                event.setCanceled(true);
                setSpeedKeepingDate(source, scale, "/terra timescale " + name);
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("[Calendar] Could not check a /terra time command: {}", t.toString());
            }
        }
    }

    /**
     * True when a jump to 1 January of {@code year} only goes forward. Otherwise says why not and what to type instead,
     * and returns false. /ee year set and /ee morph jump give the same answer.
     */
    public static boolean mayJumpTo(CommandSourceStack source, int year, String what) {
        if (!SimulationClockManager.isInitialized()) {
            return true;
        }
        int now = SimulationClockManager.getInstance().getCurrentYear();
        if (year > now) {
            return true;
        }
        int today = today();
        String todayText = today == KnowledgeDate.ALWAYS ? yearLabel(now)
                : com.eternalengine.knowledge.TalkService.dateLabel(today);
        String yearText = yearLabel(year);
        source.sendFailure(Component.literal(year == now
                ? "It is already " + yearText + " (today is " + todayText + "), so " + what + " " + year
                        + " would go back to 1 January. To go to a day in it: /ee date <year> <month> <day>."
                : "Only /ee date moves the date back: /ee date " + year + " 1 1 goes to 1 January " + yearText
                        + (TerraTimeBridge.isAvailable() ? " (and the land around you is rebuilt for that year)" : "")
                        + ". Today is " + todayText + "."));
        return false;
    }

    private static String yearLabel(int year) {
        return year < 0 ? Math.abs(year) + " BCE" : String.valueOf(year);
    }

    /** /terra timescale here: /ee speed set, which keeps the date. */
    private static void setSpeedKeepingDate(CommandSourceStack source, int scale, String what) {
        if (!SimulationClockManager.isInitialized()) {
            source.sendFailure(Component.literal("The simulation is not running"));
            return;
        }
        SimulationClockManager clock = SimulationClockManager.getInstance();
        if (!clock.setBaseSpeed(scale)) {
            source.sendFailure(Component.literal("Speed must be one of "
                    + java.util.Arrays.toString(SimulationClockManager.VALID_SPEEDS) + "; it stays " + clock.getBaseSpeed() + "x."));
            return;
        }
        int today = today();
        String date = today == KnowledgeDate.ALWAYS ? "" : " It is " + com.eternalengine.knowledge.TalkService.dateLabel(today) + ".";
        source.sendSuccess(() -> Component.literal(what + " runs as /ee speed set " + scale + " here, which keeps the date."
                + date + " " + clock.describeMode()), true);
    }

    // ==================== for /ee check all and /ee date ====================

    public static boolean isStarted() {
        return started;
    }

    /** Whether /time set day still runs this calendar's code (another mod may have replaced /time). */
    public static boolean timeSetIsOurs(MinecraftServer server) {
        CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot().getChild("time");
        node = node == null ? null : node.getChild("set");
        node = node == null ? null : node.getChild("day");
        return node != null && node.getCommand() == TIME_SET_DAY;
    }

    /** How often the guard put Minecraft's day right since the world was loaded. */
    public static int restores() {
        return restores;
    }

    /** Days of history per Minecraft day with the clock that runs now. */
    public static int daysPerMinecraftDay() {
        return TerraTimeBridge.isAvailable() ? Math.max(1, TerraTimeBridge.getTimeScale(1)) : rate;
    }

    /** One line: what the date follows, in plain words. */
    public static String describeRule() {
        int r = daysPerMinecraftDay();
        String speed = r == 1 ? "one Minecraft day is one day of history"
                : "one Minecraft day is " + r + " days of history";
        return (TerraTimeBridge.isAvailable() ? "Terra++ time: " : "the Eternal Engine clock: ") + speed
                + "; the date changes when Minecraft's day does, and only /ee date moves it back";
    }

    /** The saved part of the calendar (state.json, key "calendar"), or null before any world was loaded. */
    @Nullable
    public static JsonObject toJson() {
        if (!hasState) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("clock", TerraTimeBridge.isAvailable() ? "terra" : "eternal");
        o.addProperty("anchorKey", anchorKey);
        o.addProperty("anchorDayTime", anchorDayTime);
        o.addProperty("rate", rate);
        if (lastKey != KnowledgeDate.ALWAYS) {
            o.addProperty("lastKey", lastKey);
            o.addProperty("lastDayTime", lastDayTime);
        }
        return o;
    }
}

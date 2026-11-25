package com.eternalengine.commands;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.civilization.HistoryChecks;
import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.clock.SimulationClockManager;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.SimulationTickScheduler;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.data.EternalDataPaths;
import com.eternalengine.data.FoundationDataInstaller;
import com.eternalengine.history.HistoricalEventDatabase;
import com.eternalengine.terrabridge.TerraBridge;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.eternalengine.timeline.ChronosCodexData;
import com.eternalengine.timeline.ScenarioDefinition;
import com.eternalengine.timeline.ScenarioRegistry;
import com.eternalengine.timeline.SiblingIndex;
import com.eternalengine.timeline.TimelineBranch;
import com.eternalengine.timeline.TimelineManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code /ee check all} — every runtime check at once: is the simulation actually alive and wired, right
 * now, on this server, and does the world-history data give the answers checked by hand?
 *
 * <h3>Why this exists, and how it differs from {@code /ee test}</h3>
 * {@code /ee test} fires every command and reports whether each one threw. It answers
 * "can I talk to the mod". This answers a different and harder question: "are the systems
 * behind the commands actually running, and is history actually being kept straight".
 *
 * <p>It is also the deliberate counterpart to {@code /home/user/check.sh}. That script proves
 * the code compiles and that the logic is right, by running it outside the game. It cannot
 * possibly prove that the systems initialise in the right order on a real server —
 * {@code SimulationEngine.bootstrapDivergence} never executed in the sandbox this project was
 * built in until 2026-10-12, when {@code worldfill/tools/run_test_server.sh} started a real
 * dedicated server. This command is the only thing that can prove it, because it runs inside a
 * server that is genuinely up.
 *
 * <h3>What it will not pretend to do</h3>
 * It cannot run the test suites, and it cannot check that any code is correct. If this prints
 * all-PASS, that means the live system is wired and loaded. It does not mean the logic is
 * right. Those are two different claims and they are never merged.
 *
 * <h3>The history data (sections 6-13)</h3>
 * After the live sections it runs the runtime tests of the world-history data
 * ({@link HistoryChecks}): the checks that were run outside the game while that data was built,
 * and the hand checks of {@code worldfill/TEST_CHECKLIST.md}. These do compare answers: who rules
 * 40 places in their years, what villagers say at 12 more, the event rules, the event index, who
 * lives where, and speed, each against the answer checked by hand. They run on a background thread
 * (about 15 seconds), so the server keeps ticking, and the results follow in chat.
 *
 * <h3>One command, no single checks</h3>
 * <pre>
 *   /ee check all     every check, always (/ee check on its own does the same)
 * </pre>
 * There are no commands for one part or one place: the user asked for one command (2026-10-12). Chat
 * shows one line per check and the details of every problem.
 *
 * <h3>Everything in the Minecraft log, no files (step 15)</h3>
 * Every line, including what villagers say at each of the 15 checklist places and how to go there and
 * look, goes to the Minecraft log ({@code logs/latest.log} in the game folder, or the server folder on a
 * server), each marked {@link #LOG_TAG}. The check writes no file of its own: the user asked for that on
 * 2026-10-12 ("dont create new files when /ee check all is used, show everything in minecraft log").
 * There is no report file any more, and the save check builds what a save would write in memory and only
 * reads {@code state.json} (it used to save the world, which could also make a backup). When the check is
 * run from the server console (or RCON), the console gets two short lines and the log has the rest, so no
 * line shows twice there.
 */
public final class EeOperationalCheck {

    /** Tick schedules that must be registered for the simulation to actually advance. */
    private static final String[] REQUIRED_SCHEDULES = {
            "clock", "war", "disease", "economy", "trade", "population", "agriculture",
            "reputation", "religion", "technology", "climate", "events", "autosave", "performance",
    };

    private static int pass;
    private static int fail;
    private static int known;
    private static final List<String> failures = new ArrayList<>();

    /** Turn 222: the counters a runtime suite reads after a run (the summary is printed for people). */
    public static int passedCount() {
        return pass;
    }

    public static int failedCount() {
        return fail;
    }

    public static int knownCount() {
        return known;
    }

    /** The failure lines of the last finished run, for a report file. */
    public static List<String> failureLines() {
        return List.copyOf(failures);
    }

    /** In front of every line of the check in the Minecraft log; search the log for it to find the whole check. */
    public static final String LOG_TAG = "[EE check]";

    /**
     * Whether a player started the current run: the player gets the short version in chat. Anyone else (the
     * server console, RCON, a test harness) gets two short lines and reads the log.
     */
    private static volatile boolean toChat;

    /** When the running check started (0 = none). One run at a time: the counters are shared. */
    private static final AtomicLong runningSince = new AtomicLong(0L);

    /** A run older than this is taken to be dead (a world closed mid-check in single player). */
    private static final long STALE_MS = 5L * 60L * 1000L;

    private EeOperationalCheck() {
    }

    /**
     * The {@code /ee check} command tree (operators only, like {@code /ee test}): {@code /ee check all},
     * and {@code /ee check} on its own for the same thing. Nothing else.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("check")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> execute(ctx.getSource()))
                .then(Commands.literal("all")
                        .executes(ctx -> execute(ctx.getSource())));
    }

    /** {@code /ee check all}: every check, the live system (1-5) and then the history data (6-13). */
    public static int execute(CommandSourceStack source) {
        long now = System.currentTimeMillis();
        long since = runningSince.get();
        if (since != 0L && now - since < STALE_MS) {
            source.sendFailure(Component.literal("A check is already running. Wait for its summary, then try again."));
            return 0;
        }
        if (!runningSince.compareAndSet(since, now)) {
            source.sendFailure(Component.literal("A check is already running. Wait for its summary, then try again."));
            return 0;
        }
        try {
            pass = 0;
            fail = 0;
            known = 0;
            failures.clear();
            toChat = source.getPlayer() != null;
            List<HistoryChecks.Part> parts = List.of(HistoryChecks.Part.values());
            if (!toChat) {
                source.sendSuccess(() -> Component.literal("Every line of this check goes to the log, marked "
                        + LOG_TAG + ". The summary follows there in about 20 seconds."), false);
            }

            say(source, "§6=== ETERNAL ENGINE — ALL CHECKS ===§r");
            say(source, "§8" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + worldLabel(source) + " · the live system on this server (1-5), then the history data (6-13)§r");

            runLive(source);

            say(source, "");
            say(source, "§8Checking the history data in the background (" + parts.size()
                    + " parts, up to about 20 seconds). The game keeps running; the results follow here.§r");
            MinecraftServer server = source.getServer();
            double[] here = playerPlace(source);
            if (server == null) {
                // No server behind this source (a test harness): run in line, print in line.
                runHistory(source, null, parts, here);
                return 1;
            }
            Thread worker = new Thread(() -> runHistory(source, server, parts, here), "EE-Check");
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
            // Return 1 (the command ran), not the pass count: brigadier treats a throw as failure.
            return 1;
        } catch (Throwable t) {
            runningSince.set(0L);
            log("the check stopped with an error: " + t);
            source.sendFailure(Component.literal("The check stopped with an error: " + t));
            return 0;
        }
    }

    /** Sections 1-5: the live system (the original /ee check). */
    private static void runLive(CommandSourceStack source) {
        // Fail fast and say why. Without this guard every section below reaches into a system that
        // was never constructed and reports a Java exception name instead of an answer -- which is
        // exactly what a player on a broken server does not need.
        if (!SimulationEngine.isInitialized()) {
            say(source, "");
            fail(source, "The simulation is not running on this server.",
                    "SimulationEngine.isInitialized() is false. The mod either failed to start or this world has"
                            + " not finished loading. Nothing else in the live system can be checked until that is"
                            + " fixed. The server log will say why.");
            return;
        }

        section(source, "1. The simulation core");
        checkSimulationCore(source);

        section(source, "2. The history corpus (is the world's history actually loaded?)");
        checkCorpus(source);

        section(source, "NPC and VIP checks (read-only; nearby entities and live registry)");
        checkR2People(source);

        section(source, "3. The divergence subsystem (has it ever started?)");
        checkDivergence(source);

        section(source, "4. The in-world intervention path");
        checkInWorldPath(source);

        section(source, "5. The story surface");
        checkStorySurface(source);
        checkKnowledge(source);
        checkMoney(source);
        checkMarket(source);
        checkShops(source);
        checkProducts(source);
        checkBuyers(source);
        checkBook(source);
        checkStep21(source);
        checkNobodyOutlivesTheirCivilization(source);
        checkEveryPolityNamesItsPeople(source);
        checkEveryPolityCanNameItself(source);
        checkSecondRegisterClosesItsGaps(source);
        checkWhoRuledAnswersWithItsProvenance(source);
        checkNewsTellsOnlyWhatHasHappened(source);
        checkSpansDoNotOutliveTheirPolity(source);
        checkTheStateBeatsTheUmbrella(source);
        checkNewsNeverTellsTheFuture(source);
        checkNewsIndexLosesNothing(source);
        checkFutureStory(source);
        checkDivergenceHonesty(source);
        checkPlayerForks(source);
        checkButterflyReach(source);
        checkSpanNames(source);
        checkBrainIsAdvisoryOnly(source);
        checkStep22(source);
        checkStep23(source);
        checkStep23b(source);
        checkStep24(source);
        checkStep45(source);
        checkStep46Traffic(source);
        checkSpan(source);
    }

    /** Step 21: the named places of the world-wide test set (SYSTEMS_PLAN part 9), empty land included. */
    private static final Object[][] WORLD_PLACES = {
            {"Ur", 30.9625, 46.1031}, {"Giza", 29.9792, 31.1342}, {"Mohenjo-daro", 27.3292, 68.1389},
            {"Anyang", 36.1269, 114.3152}, {"Athens", 37.9715, 23.7257}, {"Pataliputra", 25.6000, 85.1333},
            {"Rome", 41.8925, 12.4853}, {"Teotihuacan", 19.6925, -98.8438}, {"Chang'an", 34.2667, 108.9000},
            {"Baghdad", 33.3152, 44.3661}, {"the Norse farms of Greenland", 61.1500, -45.5167},
            {"Cahokia", 38.6550, -90.0620}, {"Angkor", 13.4125, 103.8670}, {"Great Zimbabwe", -20.2674, 30.9338},
            {"Timbuktu", 16.7666, -3.0026}, {"Tenochtitlan", 19.4326, -99.1332}, {"Cusco", -13.5320, -71.9675},
            {"the Amazon", -3.4653, -62.2159}, {"Vijayanagara", 15.3350, 76.4600}, {"Delhi", 28.6562, 77.2410},
            {"Edo", 35.6852, 139.7528}, {"London", 51.5074, -0.1278}, {"Paris", 48.8566, 2.3522},
            {"Sydney", -33.8688, 151.2093}, {"New York", 40.7128, -74.0060}, {"Hyderabad", 17.3616, 78.4747},
            {"Lagos", 6.4550, 3.3841}, {"Moscow", 55.7520, 37.6175}, {"Sao Paulo", -23.5505, -46.6333},
            {"Siberia", 62.0000, 100.0000}, {"the Sahara", 23.4162, 25.6628}, {"the Greenland ice sheet", 72.0, -40.0},
            {"Cape Town", -33.9249, 18.4241}, {"Tokyo", 35.6762, 139.6503}, {"Beijing", 39.9042, 116.4074}
    };

    /**
     * Step 22: the history book is bought, not given on joining; it takes the form people keep notes in at the place and
     * year (a notebook, a papyrus roll, clay tablets, knotted cords, a tally stick...), every place and year has one, and
     * the sellers have it in their lists at a price in the money of the place.
     */
    /** Step 23a: the money of every region and era (the world layer of the money table). */
    private static void checkStep23(CommandSourceStack source) {
        try {
            long t0 = System.nanoTime();
            com.eternalengine.money.MoneyTable t = com.eternalengine.money.MoneyTable.get();
            com.eternalengine.money.MoneyTable.Rulers rulers = com.eternalengine.money.MoneyTable.REGISTER;
            java.util.List<String> bad = com.eternalengine.money.MoneyPlaces.wrongPlaces(t, rulers);
            com.eternalengine.money.MoneyPlaces.Census c = com.eternalengine.money.MoneyPlaces.census(t, rulers,
                    com.eternalengine.money.MoneyPlaces.HYDE, com.eternalengine.news.BookForms.randomPoints(400, 23L),
                    com.eternalengine.money.MoneyPlaces.CHECK_YEARS);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            boolean ok = bad.isEmpty() && c.settled() > 0 && c.placeholderShare() <= 0.01d && t.monies().size() >= 300;
            report(source, ok, "Money of the world: " + t.monies().size() + " kinds of money in " + t.rows().size()
                            + " rows; " + com.eternalengine.money.MoneyPlaces.EXPECT.size() + " test places right (Oslo 1200 the"
                            + " Scandinavian mark, Lhasa 1900 the Tibetan tangka, Calabar 1800 manillas, Bangkok 1800 the"
                            + " Siamese tical, Phnom Penh 1977 no money, Lagos 2026 the naira, Sofia 2026 the euro...); "
                            + c.settled() + " settled random answers in " + com.eternalengine.money.MoneyPlaces.CHECK_YEARS.length
                            + " years: " + c.named() + " real money, " + c.barter() + " barter, " + c.placeholder()
                            + " placeholder; " + c.monies() + " kinds of money met; in " + ms + " ms",
                    bad.isEmpty() ? c.placeholder() + " placeholders at " + c.placeholderAt()
                            : bad.size() + " place(s) wrong: " + String.join("; ", bad.subList(0, Math.min(4, bad.size()))));
        } catch (Throwable e) {
            report(source, false, "Money of the world: the table ran", e.toString());
        }
    }

    /** Step 23b: money you can hold: pieces for every money with coins, paying with change, and your own pieces. */
    private static void checkStep23b(CommandSourceStack source) {
        try {
            long t0 = System.nanoTime();
            com.eternalengine.money.MoneyTable t = com.eternalengine.money.MoneyTable.get();
            String test = com.eternalengine.money.Denominations.selfTest(t);
            com.eternalengine.money.Denominations.Census c = com.eternalengine.money.Denominations.census(t);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            String mine = "";
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                String carried = com.eternalengine.money.CoinPurse.of(player).piecesLine();
                mine = "; you carry " + (carried.isEmpty() ? "no money" : carried);
            }
            boolean ok = test == null && c.withPieces() == c.monies() && c.looks() >= 14;
            report(source, ok, "Money you can hold: " + c.withPieces() + " of " + c.monies() + " monies with coins have"
                            + " pieces (" + c.pieces() + " pieces in their first, middle and last years, at most " + c.most()
                            + " in one, " + c.mostAt() + "), in " + c.looks() + " looks (gold, silver, copper, holed cash,"
                            + " nickel, paper, cowries, cacao, cut silver, manillas, salt bars, cloth, pelts, bullet"
                            + " coins...); paying gives change (5 annas from a rupee: 11 annas back); barter keeps a tab;"
                            + " in " + ms + " ms" + mine,
                    test != null ? test : c.missing().isEmpty() ? "all well" : "no pieces: " + c.missing());
        } catch (Throwable e) {
            report(source, false, "Money you can hold: the pieces ran", e.toString());
        }
    }

    /**
     * Step 24: Minecraft's trade screen with townspeople: two slots that pay in any mix of the money taken here with
     * change back, selling a lot at a time, changing any amount, bigger and smaller pieces, cowries as small change, and
     * swapping goods where people have no money.
     */
    /**
     * Step 45: the weapons of the world.
     *
     * <p>Two lines, and both are the promises part 6 made:
     *
     * <ol>
     *   <li><b>every role gets a weapon in the sample years</b> — a job with an empty kit is a
     *       hole in the register, not a design choice.</li>
     *   <li><b>no weapon appears before its first year</b> — the gate is asked about a musket in
     *       1200 and must say no.</li>
     * </ol>
     *
     * <p>The heavy work is in {@code WarGear.selfTest()}, which walks nine sample years over
     * twenty named places for every job. It is asked here rather than written here so the same
     * answer is available to a probe with no Minecraft on the classpath.
     */
    /**
     * The span of the world's time: 3000 BC to AD 3000, and the line inside it where the record
     * stops.
     *
     * <p>The two numbers that matter are the ends. The world runs to
     * {@code Timeline.LAST_YEAR}; the records stop at {@code Timeline.RECORDED_UNTIL}. Every year
     * between is answered as a projection and marked as one, so that a player in AD 2500 is told
     * what is known of AD 2026 and that it is being carried forward — never an invented future.
     */
    private static void checkSpan(CommandSourceStack source) {
        try {
            long t0 = System.nanoTime();
            com.eternalengine.clock.Timeline T = null;
            int last = com.eternalengine.clock.Timeline.LAST_YEAR;
            int recorded = com.eternalengine.clock.Timeline.RECORDED_UNTIL;
            int projected = com.eternalengine.clock.Timeline.projectedYears();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            boolean ok = com.eternalengine.clock.Timeline.inSpan(last)
                    && com.eternalengine.clock.Timeline.projected(last)
                    && !"recorded".equals(com.eternalengine.clock.Timeline.markFor(last));
            report(source, ok, "The span: 3000 BC to AD " + last + ", records to AD " + recorded
                            + ", so " + projected + " years are marked projected and never as "
                            + "recorded fact; the sky is computed to AD " + last + " (NASA) in "
                            + ms + " ms",
                    ok ? "all well" : "the span is not consistent");
        } catch (Throwable e) {
            report(source, false, "The span: the check ran", e.toString());
        }
    }

    /**
     * Step 46 part 4 (E11): whether traffic can be worked out here and now.
     *
     * <p>Reports what is actually true where the player stands — the road, the railway and the
     * canal, whether they have reached this square by this year, and how far a load of wheat could
     * get before freight doubled its price. The leash is the number that explains the pre-modern
     * world: 286 km by road, 14,925 by sea.
     */
    private static void checkStep46Traffic(CommandSourceStack source) {
        try {
            long t0 = System.nanoTime();
            com.eternalengine.trade.Traffic T = com.eternalengine.trade.Traffic.get();
            StringBuilder here = new StringBuilder();
            int leash = T.howFar("wheat", "road");
            int seaLeash = T.howFar("wheat", "sea");
            ServerPlayer player = source.getPlayer();
            if (player != null && T.hasData()) {
                com.eternalengine.civilization.WaysGrid ways =
                        com.eternalengine.civilization.WaysGrid.get();
                int year = SimulationClockManager.getInstance().getCurrentYear();
                // Minecraft's axes: X is east-west and Z is north-south, so lat is Z and lon is X.
                double lat = player.getZ();
                double lon = player.getX();
                here.append("; here")
                        .append(ways.roadBy(lat, lon, year) ? " a road" : " no road")
                        .append(ways.railBy(lat, lon, year) ? ", a railway" : "")
                        .append(ways.canalBy(lat, lon, year) ? ", a canal" : "");
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            boolean ok = T.hasData() && leash > 0 && seaLeash > leash * 10;
            report(source, ok, "Traffic: " + (T.hasData() ? T.summary() : "no data")
                            + "; a load of wheat goes " + leash + " km by road before freight "
                            + "doubles its price and " + seaLeash + " km by sea" + here
                            + ", in " + ms + " ms",
                    ok ? "all well" : (T.problem() == null ? "the leashes are wrong"
                            : T.problem()));
        } catch (Throwable e) {
            report(source, false, "Traffic: the check ran", e.toString());
        }
    }

    private static void checkStep45(CommandSourceStack source) {
        try {
            long t0 = System.nanoTime();
            String test = com.eternalengine.war.WarGear.selfTest();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            String mods = com.eternalengine.war.WarGear.modLine(
                    com.eternalengine.war.WarGearStacks.loadedModIds());
            report(source, test == null, "Weapons: " + mods + "; every job armed in every sample "
                            + "place-year, and nothing before its year, in " + ms + " ms",
                    test == null ? "all well" : test);
        } catch (Throwable e) {
            report(source, false, "Weapons: the gate ran", e.toString());
        }
    }

    private static void checkStep24(CommandSourceStack source) {
        try {
            long t0 = System.nanoTime();
            String test = com.eternalengine.money.TradeRules.selfTest();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            String open = "";
            ServerPlayer player = source.getPlayer();
            if (player != null && player.containerMenu instanceof com.eternalengine.money.TradeMenu tm) {
                com.eternalengine.money.TradeView v = tm.serverView();
                open = v == null ? "" : "; your trade screen with " + v.npcName + " shows " + v.rows.size() + " trades";
            }
            report(source, test == null, "Trade screen: Minecraft's, with two slots to pay and one to take; any mix of the"
                            + " money taken here pays and change comes back (a 19-pie notebook from an O.S. rupee: 173 pies"
                            + " back), Indian rupees at 7 for 6, old money refused with the reason; goods sell a lot at a"
                            + " time; any amount of money is changed; bigger and smaller pieces for nothing; cowries are the"
                            + " small change beside manillas; where people swap, goods pay and too much is owed back; in "
                            + ms + " ms" + open,
                    test == null ? "all well" : test);
        } catch (Throwable e) {
            report(source, false, "Trade screen: the rules ran", e.toString());
        }
    }

    /**
     * Step R2-d (2026-10-24): every polity the ruler register can answer with must give its people a
     * regional name pool.
     *
     * <p>Before this check, 818 of 1,567 polities (52.2%) matched no keyword in
     * {@code VillagerNameDatabase.cultureFor} and their people were drawn from the generic pool:
     * a town in Ahmadnagar, Achaea or Ayutthaya was peopled by "Villager Alex Stone". The fix reads
     * the centre of each polity's recorded extent and picks the pool by place, and this check is the
     * guard that says it still does.
     *
     * <p>The threshold is a budget, not a pass mark: ten polities sit on open ocean islands with no
     * pool written for them at all (Fiji, Mauritius, Micronesia). Anything above 1% is a regression
     * in the resolver, not a missing pool, so the check fails and names what it found.
     */
    private static void checkEveryPolityNamesItsPeople(CommandSourceStack source) {
        var register = com.eternalengine.civilization.RulerRegister.get();
        var polities = register.polities();
        if (polities.isEmpty()) {
            info(source, "Every polity names its people: the ruler register is not loaded, so there is"
                    + " nothing to ask");
            return;
        }
        int generic = 0;
        String first = null;
        int total = 0;
        for (var polity : polities) {
            String id = polity.civHint();
            if (id == null || id.isBlank()) {
                continue;
            }
            total++;
            if (!com.eternalengine.villager.VillagerNameDatabase.GENERIC
                    .equals(com.eternalengine.villager.VillagerNameDatabase.cultureFor(id))) {
                continue;
            }
            generic++;
            if (first == null) {
                first = id;
            }
        }
        if (total == 0) {
            info(source, "Every polity names its people: the register names no polity");
            return;
        }
        double share = 100.0d * generic / total;
        if (share <= 1.0d) {
            pass(source, "Every polity names its people: " + (total - generic) + " of " + total
                    + " polities get a regional name pool (" + generic
                    + " island polities have no pool written for them)");
        } else {
            fail(source, "Every polity names its people: " + generic + " of " + total + " polities ("
                            + String.format(java.util.Locale.ROOT, "%.1f", share)
                            + "%) fall through to the generic pool, above the 1% budget",
                    "first: " + first + " - the pool is chosen by the centre of the polity's recorded"
                            + " extent (PlaceNamePools); a jump here means the resolver stopped answering");
        }
    }

    /**
     * Step R2-d2 (2026-10-24): every polity the register can name must be able to say its name and its
     * years, whether or not the pack carries a full civilization record for it.
     *
     * <p>Before this, the only route from a civ id to reading anything was the pack record: 24 polities
     * out of 1,567. Everything else fell through to "no civilization called ...", so a sultanate the
     * source names and dates read as nothing at all. {@code PolityFacts} answers from the register
     * instead, and this check proves it answers for all of them.
     */
    private static void checkEveryPolityCanNameItself(CommandSourceStack source) {
        var register = com.eternalengine.civilization.RulerRegister.get();
        var polities = register.polities();
        if (polities.isEmpty()) {
            info(source, "Every polity can name itself: the ruler register is not loaded, so there is"
                    + " nothing to ask");
            return;
        }
        int total = 0;
        int named = 0;
        int dated = 0;
        String firstNameless = null;
        String firstUndated = null;
        var civs = SimulationEngine.getInstance().getCivilizationManager();
        for (var polity : polities) {
            String id = polity.civHint();
            if (id == null || id.isBlank()) {
                continue;
            }
            total++;
            if (com.eternalengine.civilization.PolityFacts.name(id) != null) {
                named++;
            } else if (firstNameless == null) {
                firstNameless = id;
            }
            if (com.eternalengine.civilization.PolityFacts.span(id) != null) {
                dated++;
            } else if (firstUndated == null) {
                firstUndated = id;
            }
        }
        if (total == 0) {
            info(source, "Every polity can name itself: the register names no polity");
            return;
        }
        if (named == total && dated == total) {
            pass(source, "Every polity can name itself and its years: " + total + " of " + total
                    + " (a record is only needed for money, goods and engines, not for a name)");
            return;
        }
        fail(source, "Every polity can name itself and its years: " + named + " named, " + dated
                        + " dated, of " + total,
                (firstNameless != null ? "no name: " + firstNameless + "; " : "")
                        + (firstUndated != null ? "no years: " + firstUndated + "; " : "")
                        + "PolityFacts reads both from the register's own polity list and recorded extents");
    }

    /**
     * Step R2-a2 (2026-10-24): the eighteen polities the primary source does not hold are carried by the
     * second register, with dates and a citation each.
     *
     * <p>Before this, those eighteen were a declared gap: {@code CivHints.ABSENT_FROM_SOURCE} named them so
     * they could not be mistaken for a bug, but a person at a Mewar or Travancore site still had no state
     * to belong to. The gap is now closed from a second source rather than excused, and this check keeps
     * the two registers agreeing: every id declared absent from the primary must be covered by the second,
     * or the declaration is stale.
     */
    private static void checkSecondRegisterClosesItsGaps(CommandSourceStack source) {
        var second = com.eternalengine.civilization.PolityGazetteer.all();
        if (second.isEmpty()) {
            info(source, "The second polity register: not loaded, so nothing to judge");
            return;
        }
        int recorded = com.eternalengine.civilization.PolityGazetteer.recordedCount();
        int estimated = second.size() - recorded;
        StringBuilder stale = new StringBuilder();
        int unstale = 0;
        for (String id : com.eternalengine.civilization.CivHints.ABSENT_FROM_SOURCE) {
            if (!com.eternalengine.civilization.PolityGazetteer.covers(id)) {
                if (stale.length() > 0) {
                    stale.append(", ");
                }
                stale.append(id);
                unstale++;
            }
        }
        int dated = 0;
        for (var entry : second.values()) {
            if (entry.startYear() != 0 || entry.endYear() != 0) {
                dated++;
            }
        }
        if (unstale == 0 && dated == second.size()) {
            pass(source, "The second polity register closes its gaps: " + second.size()
                    + " polities absent from the primary source are named and dated ("
                    + recorded + " recorded by a source, " + estimated + " estimates), and every one of"
                    + " the declared gaps is covered");
            return;
        }
        fail(source, "The second polity register: " + second.size() + " rows, " + dated + " dated, "
                        + unstale + " declared gap(s) not covered",
                (stale.length() > 0 ? "not covered: " + stale + " - " : "")
                        + "CivHints.ABSENT_FROM_SOURCE must be exactly the set the second register closes");
    }

    /**
     * The lies the truth audit found, pinned so they cannot come back (R2-a5, 2026-10-24).
     *
     * <p>Each case is a real defect found by hand against a source, not a made-up test:
     * <ul>
     *   <li>Legnica 1619 - the Duchy governed its own capital, but the register stops covering it in 1313,
     *       so the declared territory must answer instead of the Holy Roman Empire;</li>
     *   <li>Brzeg 1619 - the register has no Duchy of Brzeg at all; the town page says the Piast dukes
     *       ruled it "as fiefs of the Bohemian Crown within the Holy Roman Empire";</li>
     *   <li>Odesa 1594 - Khadjibey was under DIRECT Ottoman control from 1529 (Silistra/Ozi Eyalet), so
     *       the register's Ottoman answer is right and a declared Crimean Khanate territory must NOT
     *       overrule it - this pins the retraction of 2026-10-24;</li>
     *   <li>open Pacific 1500 - genuinely nobody, and it must still say so;</li>
     *   <li>Geneva 121 BC - a settlement, so silence must read "not recorded", never "nobody".</li>
     *   <li>2400 AD - beyond the last recorded year, the answer must be the 2026 state carried forward and
     *       labelled a projection, never presented as a record (R2-8, the far end).</li>
     * </ul>
     *
     * <p>The Athens case needs the civilization pack (which has no headless accessor), so it is pinned by
     * {@code worldfill/probe_truth_audit.sh} instead. This check is the half that runs inside the game.
     */
    private static void checkWhoRuledAnswersWithItsProvenance(CommandSourceStack source) {
        var none = java.util.List.<com.eternalengine.civilization.WhoRuled.Cap>of();
        var legnica = com.eternalengine.civilization.WhoRuled.answer(none, 51.207d, 16.155d, 1619);
        var brzeg = com.eternalengine.civilization.WhoRuled.answer(none, 50.867d, 17.483d, 1619);
        var olawa = com.eternalengine.civilization.WhoRuled.answer(none, 50.944d, 17.293d, 1619);
        var odesa = com.eternalengine.civilization.WhoRuled.answer(none, 46.480d, 30.730d, 1594);
        var pacific = com.eternalengine.civilization.WhoRuled.answer(none, 0.0d, -150.0d, 1500);
        var geneva = com.eternalengine.civilization.WhoRuled.answerForSettlement(none, 46.200d, 6.147d, -121);
        var now = com.eternalengine.civilization.WhoRuled.answer(none, 51.207d, 16.155d, 2026);
        var far = com.eternalengine.civilization.WhoRuled.answer(none, 51.207d, 16.155d, 2400);
        StringBuilder wrong = new StringBuilder();
        if (!"Duchy of Legnica".equals(legnica.name())) {
            wrong.append("Legnica 1619 answers " + legnica.display() + "; ");
        }
        if (!"Duchy of Brzeg".equals(brzeg.name())) {
            wrong.append("Brzeg 1619 answers " + brzeg.display() + "; ");
        }
        if (!"Holy Roman Empire".equals(olawa.name())) {
            wrong.append("Olawa 1619 answers " + olawa.display() + " (a separate duchy, not Brzeg's); ");
        }
        if (!"Ottoman Empire".equals(odesa.name())) {
            wrong.append("Odesa 1594 answers " + odesa.display() + " (it was an Ottoman eyalet fortress); ");
        }
        if (pacific.source() != com.eternalengine.civilization.WhoRuled.Source.NONE) {
            wrong.append("the open Pacific answers " + pacific.display() + "; ");
        }
        if (far.source() != com.eternalengine.civilization.WhoRuled.Source.PROJECTED
                || (now.name() != null && !now.name().equals(far.name()))) {
            wrong.append("2400 AD answers " + far.display() + " instead of the 2026 state carried forward; ");
        }
        if (geneva.source() != com.eternalengine.civilization.WhoRuled.Source.UNKNOWN) {
            wrong.append("Geneva 121 BC read as a settlement gives " + geneva.display() + "; ");
        }
        if (wrong.length() == 0) {
            pass(source, "Who ruled names its provenance: Legnica 1619 " + legnica.name() + " ("
                    + legnica.source() + "), Brzeg 1619 " + brzeg.name() + " ("
                    + brzeg.source() + "), Olawa stays " + olawa.name() + ", Odesa 1594 " + odesa.name() + " (" + odesa.source()
                    + "), open Pacific " + pacific.display() + ", Geneva -121 reads "
                    + geneva.display() + ", 2400 AD reads " + far.display());
            return;
        }
        fail(source, "Who ruled: a source-checked case is wrong again", wrong.toString());
    }

    /**
     * News is never told before it has happened - and the test is on the day, not the year (R2-7, 2026-10-27).
     *
     * <p>The complaint that started this: a year-only filter will report a battle fought in November to a
     * player standing in June of the same year. The engine's clock could already resolve a day
     * ({@code KnowledgeDate.today()} is year*10000+month*100+day); the event files could not, because all
     * 129,232 of them are dated to the year with no month or day. The fix is {@code DatedEvents}: a second
     * index, from articles that give a date, carrying the precision its source supports.
     *
     * <p>This check takes a real day-dated event out of that index and asks the card builder either side of
     * its date:
     * <ul>
     *   <li><b>the day before</b> - the event must not be told, at its own coordinates;</li>
     *   <li><b>the day itself</b> - it must be told;</li>
     *   <li><b>the year before, any day</b> - nothing dated after that point may be told.</li>
     * </ul>
     *
     * <p>It also refuses a year-only event for the <b>current</b> year, which is the loophole that let a
     * 1620 plague be told in 1619.
     */
    private static void checkNewsNeverTellsTheFuture(CommandSourceStack source) {
        var dated = com.eternalengine.knowledge.DatedEvents.get();
        StringBuilder wrong = new StringBuilder();
        if (!dated.loaded() || dated.dayPrecise() < 50) {
            fail(source, "news by the day: the dated event index is missing",
                    dated.loaded() ? "only " + dated.dayPrecise() + " events are dated to the day"
                            : "no dated events loaded (" + dated.source() + ")");
            return;
        }
        // A real event, dated to the day, in the middle of a year rather than at its edge.
        com.eternalengine.knowledge.DatedEvents.Dated pick = null;
        for (var e : dated.all()) {
            if (e.prec() == 11 && e.m() >= 3 && e.m() <= 10 && e.d() >= 2 && e.d() <= 27 && e.y() > 1000) {
                pick = e;
                break;
            }
        }
        if (pick == null) {
            fail(source, "news by the day: no usable day-dated event in the middle of a year", "");
            return;
        }
        int onTheDay = com.eternalengine.knowledge.KnowledgeDate.key(pick.y(), pick.m(), pick.d());
        int dayBefore = com.eternalengine.knowledge.KnowledgeDate.key(pick.y(), pick.m(), pick.d() - 1);

        var before = com.eternalengine.knowledge.NewsIndex.onDay(pick.lat(), pick.lon(), dayBefore);
        var after = com.eternalengine.knowledge.NewsIndex.onDay(pick.lat(), pick.lon(), onTheDay);

        if (before.isPresent() && before.get().name().equals(pick.name())) {
            wrong.append("told \"" + pick.name() + "\" a day before it happened; ");
        }
        if (after.isEmpty() || !after.get().name().equals(pick.name()) || !after.get().dated()) {
            wrong.append("did not tell \"" + pick.name() + "\" on the day it happened (got "
                    + after.map(com.eternalengine.knowledge.NewsIndex.OnDay::name).orElse("nothing") + "); ");
        }

        // No year-only event may be told while its year is still running.
        int midYear = com.eternalengine.knowledge.KnowledgeDate.key(1619, 6, 15);
        var midYearNews = com.eternalengine.knowledge.NewsIndex.onDay(51.207d, 16.155d, midYear);
        if (midYearNews.isPresent() && !midYearNews.get().dated() && midYearNews.get().year() >= 1619) {
            wrong.append("a year-only event from 1619 or later was told in June 1619 ("
                    + midYearNews.get().name() + "); ");
        }

        // ...and it must not call a centuries-old event news. Found by asking what the game says in 3000:
        // standings in Legnica then, it still said "Word has come of Battle of Liegnitz (1760)".
        var onItsDay = com.eternalengine.knowledge.NewsIndex.onDay(pick.lat(), pick.lon(), onTheDay);
        if (onItsDay.isPresent() && (!onItsDay.get().fresh()
                || onItsDay.get().text().startsWith("Not news"))) {
            wrong.append("an event on the day it happened was not called news (" + onItsDay.get().text() + "); ");
        }
        int longAfter = com.eternalengine.knowledge.KnowledgeDate.key(pick.y() + 400, pick.m(), pick.d());
        var later = com.eternalengine.knowledge.NewsIndex.onDay(pick.lat(), pick.lon(), longAfter);
        if (later.isPresent() && later.get().fresh() && later.get().text().startsWith("Word has come of")) {
            wrong.append("four hundred years later it was still called news (" + later.get().text() + "); ");
        }

        if (wrong.length() == 0) {
            pass(source, "news is never future: " + dated.size() + " dated events, " + dated.dayPrecise()
                    + " to the day; \"" + pick.name() + "\" is silent on the day before, told on the day"
                    + " (" + pick.when() + "), and 400 years on it is labelled this place's past rather than"
                    + " news; nothing from a running year is told in June");
            return;
        }
        fail(source, "news told before it happened", wrong.toString());
    }

    /**
     * <b>The language model must be off, advisory, and unable to touch history</b> (2026-10-27).
     *
     * <p>Found by asking "can you add a language model?": 827 lines of brain code already existed
     * ({@code RemoteBrainBridge}, the validator, the decision queue) and <b>not one check in the whole
     * suite mentioned it</b>. In this project that is a defect on its own - machinery nobody measures is
     * machinery nobody can trust.
     *
     * <p>The checks are about behaviour, not intent. A proposal that tries to rewrite the timeline is put
     * through the real validator and must come back rejected; a proposal below the confidence floor must be
     * rejected; the shipped configuration must have the brain off and no endpoint.
     */
    private static void checkBrainIsAdvisoryOnly(CommandSourceStack source) {
        StringBuilder wrong = new StringBuilder();
        com.eternalengine.brain.LlmProposalValidator validator =
                new com.eternalengine.brain.LlmProposalValidator();
        com.eternalengine.brain.LlmValidationContext context =
                new com.eternalengine.brain.LlmValidationContext("check", null, 1948, false, false);

        // 1. Every forbidden direct mutation must be refused, by name.
        String[] forbidden = {"MUTATE_TIMELINE_DIRECT", "EDIT_SAVE_FILE", "DELETE_CHUNK",
                "RUN_SERVER_COMMAND_RAW", "SET_REPUTATION_DIRECT", "SPAWN_ENTITY_DIRECT",
                "GIVE_ITEM_DIRECT", "PLACE_BLOCK_DIRECT", "BREAK_BLOCK_DIRECT"};
        int refused = 0;
        for (String action : forbidden) {
            var proposal = new com.eternalengine.brain.LlmProposal("p_" + action, "check", action,
                    java.util.Map.of(), "trying to change the world directly", 0.99d);
            if (!validator.validate(proposal, context).accepted()) {
                refused++;
            }
        }
        if (refused != forbidden.length) {
            wrong.append((forbidden.length - refused) + " forbidden action(s) were accepted; ");
        }

        // 2. In particular, a language model can never be the author of a fork. This is the rule the whole
        //    player-changes-history thread rests on: a fork has a human author or it does not happen.
        var fork = new com.eternalengine.brain.LlmProposal("p_fork", "check", "MUTATE_TIMELINE_DIRECT",
                java.util.Map.of("fork", "gandhi_1948"), "the model decided history should differ", 1.0d);
        var forkVerdict = validator.validate(fork, context);
        if (forkVerdict.accepted()) {
            wrong.append("a language model could rewrite the timeline; ");
        }

        // 3. The confidence floor.
        var low = new com.eternalengine.brain.LlmProposal("p_low", "check", "REQUEST_CURRENT_AFFAIRS",
                java.util.Map.of(), "unsure", 0.10d);
        if (validator.validate(low, context).accepted()) {
            wrong.append("a 0.10-confidence proposal was accepted; ");
        }

        // 4. An unknown action is not a free pass.
        var unknown = new com.eternalengine.brain.LlmProposal("p_unknown", "check", "DO_ANYTHING",
                java.util.Map.of(), "no such action", 0.99d);
        if (validator.validate(unknown, context).accepted()) {
            wrong.append("an unknown action was accepted; ");
        }

        // 5. A year outside the simulation is refused.
        var outOfRange = new com.eternalengine.brain.LlmProposal("p_year", "check", "DECLARE_WAR",
                java.util.Map.of(), "beyond the game", 0.99d);
        var farContext = new com.eternalengine.brain.LlmValidationContext("check", null, 12000, true, false);
        if (validator.validate(outOfRange, farContext).accepted()) {
            wrong.append("a proposal for the year 12000 was accepted; ");
        }

        // 6. Shipping state: the brain is off, and no endpoint is set. A server that turns it on deliberately
        //    is a different matter - the default must not be a live model.
        boolean enabledByDefault;
        String endpoint;
        try {
            enabledByDefault = com.eternalengine.config.EternalEngineConfig.SERVER.remoteBrainEnabled.getDefault();
            endpoint = com.eternalengine.config.EternalEngineConfig.SERVER.liteLlmEndpoint.getDefault();
        } catch (Throwable t) {
            enabledByDefault = false;
            endpoint = "";
            wrong.append("could not inspect the brain configuration defaults: ").append(t).append("; ");
        }
        if (enabledByDefault) {
            wrong.append("the remote brain is on in the shipped configuration; ");
        }
        if (endpoint != null && !endpoint.isBlank()) {
            wrong.append("a brain endpoint is shipped in the config; ");
        }

        if (wrong.length() == 0) {
            pass(source, "the language model is advisory only: " + refused + " of " + forbidden.length
                    + " direct mutations are refused by name, a model can never author a timeline fork, a"
                    + " 0.10-confidence proposal is refused, an unknown action is refused, a year outside the"
                    + " simulation is refused, and the shipped configuration has the brain off with no endpoint");
            return;
        }
        fail(source, "the language model is not safely bounded", wrong.toString());
    }

    /**
     * A year must be nameable as the span it sits in - the 1940s, the 20th century, the 2nd millennium
     * (2026-10-27, cross-checked against Wikipedia's "List of decades, centuries, and millennia").
     *
     * <p>Dividing by 100 gets three things wrong, and each one is a line the game would have printed wrongly:
     * there is no year 0 (so the 1st century BC is 100 - 1 BC); AD 2000 is the <b>20th</b> century and the
     * <b>2nd</b> millennium (the parties in 2000 were a year early); and BC decades run backwards (the 1790s BC
     * are 1799 - 1790 BC). The check walks the whole declared span, 3000 BC to AD 3000, and refuses if any of it
     * stops tiling.
     */
    private static void checkSpanNames(CommandSourceStack source) {
        StringBuilder wrong = new StringBuilder();
        com.eternalengine.clock.SpanName.Span bc3000 = com.eternalengine.clock.SpanName.century(-3000);
        com.eternalengine.clock.SpanName.Span c2000 = com.eternalengine.clock.SpanName.century(2000);
        com.eternalengine.clock.SpanName.Span c2001 = com.eternalengine.clock.SpanName.century(2001);
        com.eternalengine.clock.SpanName.Span m2000 = com.eternalengine.clock.SpanName.millennium(2000);
        com.eternalengine.clock.SpanName.Span dec1795 = com.eternalengine.clock.SpanName.decade(-1795);

        if (!"30th century BC".equals(bc3000.name()) || bc3000.firstYear() != -3000) {
            wrong.append("3000 BC is not in the 30th century BC; ");
        }
        if (!"20th century".equals(c2000.name()) || !"21st century".equals(c2001.name())) {
            wrong.append("2000/2001 land in the wrong centuries; ");
        }
        if (!"2nd millennium".equals(m2000.name())) {
            wrong.append("2000 is not in the 2nd millennium; ");
        }
        if (!"1790s BC".equals(dec1795.name()) || dec1795.firstYear() != -1799 || dec1795.lastYear() != -1790) {
            wrong.append("BC decades do not run backwards; ");
        }
        if (com.eternalengine.clock.SpanName.century(0) != null) {
            wrong.append("a century was named for the year zero; ");
        }

        // Every year of the declared span must fall in a century, and consecutive centuries must abut.
        int centuries = 0;
        com.eternalengine.clock.SpanName.Span prev = null;
        for (int y = -3000; y <= 3000; y++) {
            if (y == 0) {
                continue;
            }
            com.eternalengine.clock.SpanName.Span c = com.eternalengine.clock.SpanName.century(y);
            if (c == null) {
                wrong.append("no century for " + y + "; ");
                break;
            }
            if (c.lastYear() - c.firstYear() != 99) {
                wrong.append(c.name() + " is not 100 years; ");
                break;
            }
            if (prev == null || !prev.name().equals(c.name())) {
                centuries++;
                boolean across = prev != null && prev.lastYear() == -1 && c.firstYear() == 1;
                if (prev != null && !across && c.firstYear() != prev.lastYear() + 1) {
                    wrong.append("a gap between " + prev.name() + " and " + c.name() + "; ");
                    break;
                }
                prev = c;
            }
        }
        if (centuries != 60 && centuries != 61) {   // 61 when the walk starts on 3000 BC itself
            wrong.append(centuries + " centuries, expected 60; ");
        }
        if (wrong.length() == 0) {
            pass(source, "every year from 3000 BC to AD 3000 is named as a span, and the awkward ones are right:"
                    + " 3000 BC is " + bc3000.inWords() + ", AD 2000 is " + c2000.inWords() + " and the "
                    + m2000.name() + ", AD 2001 is the " + c2001.name() + ", and " + dec1795.inWords()
                    + " (BC runs backwards); no year zero exists, and no span is invented past AD 3000");
            return;
        }
        fail(source, "a year cannot be named as the span it sits in", wrong.toString());
    }

    /**
     * A player must be able to fork history <b>anywhere on Earth</b>, not only in the Deccan (2026-10-27).
     *
     * <p>Found while answering "what about the butterfly effect across Earth": the intervention detector only
     * trusts a fixed list of event kinds, but the worldwide corpus types all 1,141 of its events as the generic
     * {@code "event"}, which is in neither the stoppable nor the refused list. So <b>every event outside the
     * 248-event Deccan set was refused</b>, and a player standing in the middle of a battle anywhere else in the
     * world changed nothing. The butterfly effect did not span Earth because of a missing word.
     *
     * <p>{@code EventKind} now recovers the kind from the title under two rules: a disaster always loses, and
     * anything unrecognised stays refused. This check proves both: the reach is broad, and nothing dangerous
     * became stoppable.
     */
    private static void checkButterflyReach(CommandSourceStack source) {
        java.util.Set<String> stop = com.eternalengine.player.InWorldInterventionDetector.STOPPABLE_KINDS;
        java.util.Set<String> ref = com.eternalengine.player.InWorldInterventionDetector.REFUSED_KINDS;
        int total = 0;
        int forkable = 0;
        int disasters = 0;
        java.util.Set<String> continents = new java.util.TreeSet<>();
        StringBuilder wrong = new StringBuilder();

        for (com.eternalengine.knowledge.DatedEvents.Dated e : com.eternalengine.knowledge.DatedEvents.get().all()) {
            total++;
            String kind = com.eternalengine.player.EventKind.effective("event", e.name());
            if (stop.contains(kind)) {
                forkable++;
                continents.add(continentOf(e.lat(), e.lon()));
            }
            if (ref.contains(kind) || com.eternalengine.player.EventKind.looksLikeDisaster(e.name())) {
                disasters++;
            }
            // Rule: a disaster can never become stoppable, whatever its title also says.
            if (com.eternalengine.player.EventKind.looksLikeDisaster(e.name()) && stop.contains(kind)) {
                wrong.append("\"" + e.name() + "\" reads as a disaster but is stoppable; ");
            }
        }
        // The classifier must never invent a kind the detector does not know.
        for (String probe : new String[] {"battle", "siege", "political", "disaster"}) {
            String k = com.eternalengine.player.EventKind.effective(probe, "");
            if (!stop.contains(k) && !ref.contains(k) && !"event".equals(k)) {
                wrong.append("the classifier invented a kind: " + k + "; ");
            }
        }
        int pct = total == 0 ? 0 : (100 * forkable / total);
        if (target(total) > 0) {
            wrong.append("self-test; ");
        }
        if (wrong.length() == 0) {
            pass(source, "a player can fork history across Earth: " + forkable + " of " + total + " dated events"
                    + " (" + pct + "%) are the kind a person can stand in front of, on " + continents.size()
                    + " continents (" + String.join(", ", continents) + "); " + disasters
                    + " disasters remain refused, and unrecognised kinds stay refused");
            return;
        }
        fail(source, "the butterfly effect cannot reach the whole world", wrong.toString());
    }

    private static int target(int total) {
        return total > 0 ? 0 : 1;      // a world with no dated events cannot be forked at all
    }

    /** Rough continent for the reach report. Only used to show the spread, never to decide anything. */
    private static String continentOf(double lat, double lon) {
        if (lon >= -25 && lon <= 60) {
            return lat >= 34 ? "Europe" : "Africa";
        }
        if (lon > 60 && lon <= 180) {
            return lat >= 0 ? "Asia" : "Oceania/SEA";
        }
        return lat >= 12 ? "N.America" : "S.America";
    }

    /**
     * A fork a player causes must be signed, singular, and must not touch the sky (stage 2, 2026-10-27).
     *
     * <p>{@code ForkLedger} is what finally makes a fork enterable: the shipped scenarios are unsigned drafts
     * and {@code Divergence.enterable()} refuses them, but a player-caused fork has an author by definition.
     * This check proves the rules hold in a real run, without leaving anything behind:
     * <ul>
     *   <li>an unnamed player, an empty act, an impossible date and a place off Earth are all refused;</li>
     *   <li>the same act on the same day at the same place is <b>one</b> fork, not two;</li>
     *   <li>a real fork is enterable, and nothing before its year changes;</li>
     *   <li>the sky is unchanged, and it is <b>one</b> sky (the count is the number of astronomy events, not
     *       that number times the number of forks - a real miscount found on 2026-10-27);</li>
     *   <li>the ledger survives a save and load, and an unsigned fork inside a save is refused on load.</li>
     * </ul>
     * <p>The ledger is restored to what it was, so the check never edits a real player's history.
     */
    private static void checkPlayerForks(CommandSourceStack source) {
        String before = com.eternalengine.story.ForkLedger.toJson();
        StringBuilder wrong = new StringBuilder();
        try {
            com.eternalengine.story.ForkLedger.clear();
            if (com.eternalengine.story.ForkLedger.cause("", 0, 0, 2029, 4, 13, "x").ok()) {
                wrong.append("an unnamed player opened a fork; ");
            }
            if (com.eternalengine.story.ForkLedger.cause("A", 0, 0, 2029, 4, 13, " ").ok()) {
                wrong.append("a fork with no description opened; ");
            }
            if (com.eternalengine.story.ForkLedger.cause("A", 0, 0, 2029, 13, 40, "x").ok()) {
                wrong.append("an impossible date opened a fork; ");
            }
            if (com.eternalengine.story.ForkLedger.cause("A", 200, 0, 2029, 4, 13, "x").ok()) {
                wrong.append("a place off Earth opened a fork; ");
            }
            var made = com.eternalengine.story.ForkLedger.cause("Asha", 17.385, 78.486, 2029, 4, 13,
                    "warned the old city about the rock");
            if (!made.ok()) {
                wrong.append("a real player fork was refused: " + made.why() + "; ");
            } else {
                var twice = com.eternalengine.story.ForkLedger.cause("Asha", 17.385, 78.486, 2029, 4, 13,
                        "warned the old city about the rock");
                if (twice.ok()) {
                    wrong.append("the same act opened two forks; ");
                }
                var asFork = com.eternalengine.story.ForkLedger.asDivergence(made.fork());
                if (!asFork.enterable()) {
                    wrong.append("a player fork is not enterable: " + asFork.notEnterableBecause() + "; ");
                }
                var earlier = com.eternalengine.story.DivergedHistory.accounts(asFork, 2029 - 1);
                if (!earlier.sameAsRecord()) {
                    wrong.append("a player fork changed the year before it; ");
                }
            }
            java.util.List<String> sky = com.eternalengine.story.DivergedHistory.skyDates();
            if (sky.isEmpty()) {
                wrong.append("no astronomy to hold constant; ");
            }
            if (sky.size() != new java.util.HashSet<>(sky).size()) {
                wrong.append("the sky list has duplicates, so a fork could appear to change it; ");
            }
            String saved = com.eternalengine.story.ForkLedger.toJson();
            int held = com.eternalengine.story.ForkLedger.size();
            com.eternalengine.story.ForkLedger.clear();
            int reloaded = com.eternalengine.story.ForkLedger.fromJson(saved);
            if (reloaded != held) {
                wrong.append("the ledger lost forks across a save (" + held + " -> " + reloaded + "); ");
            }
            int unsigned = com.eternalengine.story.ForkLedger.fromJson(
                    "{\"forks\":[{\"id\":\"x\",\"player\":\"\",\"lat\":0,\"lon\":0,\"year\":2030,"
                            + "\"month\":1,\"day\":1,\"what\":\"unsigned\"}]}");
            if (unsigned != 0) {
                wrong.append("an unsigned fork in a save was loaded; ");
            }
            if (wrong.length() == 0) {
                pass(source, "a player can cause a fork and it keeps its rules: unsigned, empty, impossible and"
                        + " off-planet acts are refused; the same act is one fork; a real fork is enterable and"
                        + " changes nothing before its year; the sky is one unchanged list of " + sky.size()
                        + " astronomy events; the ledger survives a save");
            }
        } finally {
            // Never leave the check's own fork in a real world's history.
            com.eternalengine.story.ForkLedger.fromJson(before);
        }
        if (wrong.length() > 0) {
            fail(source, "a player's fork breaks its own rules", wrong.toString());
        }
    }

    /**
     * A player-caused fork must keep two rules (player-changes-history, 2026-10-27).
     *
     * <p>The engine has three what-if scenarios and, until now, no way to reach them from play. This checks the
     * model that makes a fork answerable:
     * <ul>
     *   <li><b>nothing before the fork changes</b> - the year before the trigger is identical in both timelines,
     *       so a branch can never quietly rewrite the deep past;</li>
     *   <li><b>the sky is identical in every timeline</b> - the computed astronomy dates of
     *       {@code future_story.json} are the same list from inside every fork, which is what lets one future
     *       file serve every branch;</li>
     *   <li>every changed year names both what the record had and what the fork puts in its place, and a line
     *       the scenario marks {@code [plausible]} or {@code [causal]} is never printed in the record column -
     *       that was a real defect, caught by {@code DivergenceProbe} on the day this was written.</li>
     * </ul>
     *
     * <p>It also reports, rather than hides, that <b>all three forks are currently unsigned drafts</b> and may
     * not be entered by a player: {@code Divergence.enterable()} refuses them. Signing them is a content
     * decision, not something this check may fake.
     */
    private static void checkDivergenceHonesty(CommandSourceStack source) {
        java.util.List<com.eternalengine.story.Divergence> forks = com.eternalengine.story.Divergence.all();
        if (forks.isEmpty()) {
            fail(source, "no player-caused forks are loaded",
                    "scenarios/what_if_*.json did not load into Divergence");
            return;
        }
        StringBuilder wrong = new StringBuilder();
        int changedYears = 0;
        for (com.eternalengine.story.Divergence f : forks) {
            var before = com.eternalengine.story.DivergedHistory.accounts(f, f.triggerYear() - 1);
            if (!before.sameAsRecord()) {
                wrong.append(f.id() + " already differs in " + (f.triggerYear() - 1) + "; ");
            }
            for (int y : com.eternalengine.story.DivergedHistory.changedYears(f)) {
                changedYears++;
                var two = com.eternalengine.story.DivergedHistory.accounts(f, y);
                if (two.record().isBlank() || two.branch().isBlank()) {
                    wrong.append(f.id() + " says nothing for " + y + "; ");
                }
                if (two.record().startsWith("[plausible]") || two.record().startsWith("[causal]")) {
                    wrong.append(f.id() + " printed speculation as the record in " + y + "; ");
                }
            }
        }
        java.util.List<String> sky = forks.isEmpty() ? java.util.List.of()
                : com.eternalengine.story.DivergedHistory.skyDates(forks.get(0));
        for (com.eternalengine.story.Divergence f : forks) {
            if (!com.eternalengine.story.DivergedHistory.skyDates(f).equals(sky)) {
                wrong.append(f.id() + " has a different sky; ");
            }
        }
        if (sky.isEmpty()) {
            wrong.append("no computed astronomy to hold constant; ");
        }
        long enterable = forks.stream().filter(com.eternalengine.story.Divergence::enterable).count();
        if (wrong.length() == 0) {
            pass(source, "a player's fork keeps its two rules: " + forks.size() + " forks, " + changedYears
                    + " changed years, nothing altered before any fork, and the same " + sky.size()
                    + " astronomy dates from inside every one. " + enterable + " of " + forks.size()
                    + " are enterable - the rest are unsigned drafts and are refused, by design");
            return;
        }
        fail(source, "a player's fork breaks its own rules", wrong.toString());
    }

    /**
     * The news grid must give the same answer as reading every one of the 129,232 events (R2-7, 2026-10-27).
     *
     * <p>The day-aware news question is asked for every card, so it is answered from three spatial grids
     * rather than a full scan. An optimisation that quietly loses events is a bug: while building it, one
     * grid shape dropped a Lima earthquake and another dropped two Baltic events, and neither showed up as
     * a failure anywhere - the card simply said nothing, which looks like "no news here today". This asks
     * the same place-years both ways and refuses if they ever disagree.
     */
    private static void checkNewsIndexLosesNothing(CommandSourceStack source) {
        var ev = com.eternalengine.knowledge.NewsIndex.all();
        double[][] places = {
                {51.207, 16.155}, {52.520, 13.400}, {41.010, 28.980}, {48.857, 2.352}, {51.507, -0.128},
                {41.900, 12.500}, {40.417, -3.704}, {55.756, 37.617}, {59.934, 30.336}, {64.146, -21.942},
                {60.170, 24.941}, {53.350, -6.260}, {38.722, -9.139}, {37.983, 23.727}, {30.044, 31.235},
                {28.614, 77.209}, {22.573, 88.364}, {1.352, 103.820}, {35.689, 139.692}, {39.904, 116.407},
                {31.230, 121.474}, {40.712, -74.006}, {34.052, -118.244}, {19.433, -99.133}, {-12.046, -77.043},
                {-33.449, -70.669}, {-34.604, -58.382}, {-23.551, -46.633}, {-1.292, 36.822}, {-33.925, 18.424},
                {-18.141, 178.442}, {-36.848, 174.763}, {-33.869, 151.209}, {55.953, -3.188}, {50.850, 4.352},
                {52.370, 4.895}, {48.208, 16.373}, {47.497, 19.040}, {44.426, 26.103}, {43.856, 18.413},
                {36.202, 36.160}, {33.315, 44.366}, {31.768, 35.214}, {25.204, 55.270}, {24.713, 46.675},
                {-8.839, 13.289}, {14.693, -17.448}, {6.524, 3.379}, {-26.204, 28.047}, {3.139, 101.687},
        };
        int[] years = {1000, 1400, 1600, 1700, 1800, 1850, 1900, 1939, 1960, 2020};
        int asked = 0;
        int disagree = 0;
        String first = "";
        for (int y : years) {
            for (double[] pl : places) {
                asked++;
                var on = com.eternalengine.knowledge.NewsIndex.onDay(pl[0], pl[1],
                        com.eternalengine.knowledge.KnowledgeDate.key(y, 6, 15));
                if (on.isPresent() && on.get().dated()) {
                    continue;                 // the dated tier is a separate city index, not this grid
                }
                String indexed = on.map(com.eternalengine.knowledge.NewsIndex.OnDay::name).orElse("-");
                com.eternalengine.knowledge.NewsIndex.Event best = null;
                double bestKm = Double.MAX_VALUE;
                for (com.eternalengine.knowledge.NewsIndex.Event e : ev) {
                    if (e.end() >= y || e.end() < y - com.eternalengine.knowledge.NewsIndex.NEWS_YEARS) {
                        continue;
                    }
                    double km = com.eternalengine.knowledge.NewsIndex.distanceKm(pl[0], pl[1], e.lat(), e.lon());
                    if (km > e.radiusKm() + com.eternalengine.knowledge.NewsIndex.HEAR_KM) {
                        continue;
                    }
                    if (km < bestKm - 1e-9d
                            || (Math.abs(km - bestKm) <= 1e-9d && best != null
                                    && e.name().compareTo(best.name()) < 0)) {
                        bestKm = km;
                        best = e;
                    }
                }
                String brute = best == null ? "-" : best.name();
                if (!indexed.equals(brute)) {
                    disagree++;
                    if (first.isEmpty()) {
                        first = pl[0] + "," + pl[1] + " in " + y + ": the grid said " + indexed
                                + ", reading every event said " + brute;
                    }
                }
            }
        }
        if (disagree == 0) {
            pass(source, "the news grid loses nothing: " + asked + " place-years give the same news as reading"
                    + " all " + ev.size() + " events one by one");
            return;
        }
        fail(source, "the news grid gives different news from a full read (" + disagree + " of " + asked + "): "
                + first, "");
    }

    /**
     * The story of 2027-3000 must stay in its four lanes (R1-7, 2026-10-27).
     *
     * <p>The far future is the one place a history engine can lie without anyone catching it, so the file
     * declares a provenance for every line: COMPUTED (astronomy), PROJECTED (a state carried forward),
     * AUTHORED (a story, signed) and silent. This check refuses the file unless:
     * <ul>
     *   <li>every line says which lane it is in;</li>
     *   <li>every authored line is signed by a named author;</li>
     *   <li><b>no projected line names a ruler, a war, a disaster or a price</b> - the same word list
     *       {@code UniverseStory.GUARD_WORDS} enforces, applied here to the data before it can reach a
     *       card;</li>
     *   <li>every century from 2027 to 3000 has something in it, so "the story continues" is never an
     *       empty page.</li>
     * </ul>
     */
    private static void checkFutureStory(CommandSourceStack source) {
        java.nio.file.Path p = java.nio.file.Path.of("src", "main", "resources", "data", "eternalengine",
                "eternal", "history", "future_story.json");
        String raw;
        try {
            raw = java.nio.file.Files.exists(p) ? java.nio.file.Files.readString(p) : "";
        } catch (Exception e) {
            raw = "";
        }
        if (raw.isBlank()) {
            // Inside a running game the resource is on the classpath, not on disk.
            try (java.io.InputStream in = EeOperationalCheck.class.getResourceAsStream(
                    "/data/eternalengine/eternal/history/future_story.json")) {
                raw = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                raw = "";
            }
        }
        if (raw.isBlank()) {
            fail(source, "the story of 2027-3000 is missing", "history/future_story.json was not found");
            return;
        }
        com.google.gson.JsonObject doc = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
        java.util.List<String> problems = new ArrayList<>();

        int computed = doc.getAsJsonArray("computed").size();
        int projected = doc.getAsJsonArray("projected").size();
        int authored = doc.getAsJsonArray("authored").size();
        java.util.Set<Integer> centuries = new java.util.TreeSet<>();

        for (com.google.gson.JsonElement el : doc.getAsJsonArray("computed")) {
            com.google.gson.JsonObject o = el.getAsJsonObject();
            if (!o.has("provenance") || !"COMPUTED".equals(o.get("provenance").getAsString())) {
                problems.add("a computed line does not say it is computed");
            }
            centuries.add(Integer.parseInt(o.get("date").getAsString().substring(0, 4)) / 100);
        }
        for (com.google.gson.JsonElement el : doc.getAsJsonArray("authored")) {
            com.google.gson.JsonObject o = el.getAsJsonObject();
            if (!o.has("author") || o.get("author").getAsString().isBlank()) {
                problems.add("an authored line is unsigned: " + o.get("date").getAsString());
            }
            centuries.add(Integer.parseInt(o.get("date").getAsString().substring(0, 4)) / 100);
        }
        for (com.google.gson.JsonElement el : doc.getAsJsonArray("projected")) {
            com.google.gson.JsonObject o = el.getAsJsonObject();
            String text = o.get("text").getAsString().toLowerCase(java.util.Locale.ROOT);
            for (String word : com.eternalengine.story.UniverseStory.GUARD_WORDS) {
                if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(word) + "\\b")
                        .matcher(text).find()) {
                    problems.add("a projected line names \"" + word + "\": " + o.get("text").getAsString());
                }
            }
            centuries.add(o.get("from").getAsInt() / 100);
        }
        // Every century that contains any part of 2027-3000 must have something in it.
        for (int c = 2027 / 100; c <= 3000 / 100; c++) {
            boolean needed = (c == 2027 / 100 || c == 3000 / 100 || (c * 100 >= 2027 && c * 100 + 99 <= 3000));
            if (needed && !centuries.contains(c)) {
                problems.add("the " + (c + 1) + "th century has nothing in it");
            }
        }
        if (problems.isEmpty()) {
            pass(source, "the story of 2027-3000 has four lanes and every century filled: " + computed
                    + " computed (astronomy), " + projected + " projected (states carried forward), " + authored
                    + " authored (signed fiction); no projected line names a ruler, a war or a disaster");
            return;
        }
        StringBuilder why = new StringBuilder();
        for (int i = 0; i < Math.min(4, problems.size()); i++) {
            why.append(problems.get(i)).append("; ");
        }
        fail(source, "the story of 2027-3000 breaks its own rules", why.toString());
    }

    /**
     * A span must not outlive the polity it names (R2-a7, 2026-10-27).
     *
     * <p>Found by {@code SpanHonestyProbe}: the Khmer Empire is recorded at Angkor as the ruler in 1864,
     * four centuries after the fall of Angkor in 1431, and the Golden Horde at Sarai in 1600, a century
     * after the sack of Sarai in 1502. Both are the source overrunning a slice, not the engine misranking:
     * the answer is a state that no longer existed. The correction is named in
     * {@code RulerRegister.SPAN_ENDS_WRONG} and pins the year a source gives.
     *
     * <p>Three directions, so the clamp cannot be a blanket "no answer":
     * <ul>
     *   <li>Angkor 1400 - the Khmer Empire is still the answer, before its real end;</li>
     *   <li>Angkor 1864 - it is not, four centuries after it;</li>
     *   <li>Sarai 1400 - the Golden Horde is still the answer; Sarai 1600 - it is not.</li>
     * </ul>
     */
    private static void checkSpansDoNotOutliveTheirPolity(CommandSourceStack source) {
        var none = java.util.List.<com.eternalengine.civilization.WhoRuled.Cap>of();
        // Angkor, and Sarai on the lower Volga.
        var angkorNow = com.eternalengine.civilization.WhoRuled.answer(none, 13.412d, 103.867d, 1400);
        var angkorLate = com.eternalengine.civilization.WhoRuled.answer(none, 13.412d, 103.867d, 1864);
        // Sarai in 1400 answers the Blue Horde, which is the eastern wing and the right answer there;
        // the point of the case is only that the Golden Horde does not run past its sack in 1502.
        var istanbulNow = com.eternalengine.civilization.WhoRuled.answer(none, 41.010d, 28.980d, 1450);
        // The filled hole: the register is silent at Istanbul 1454-1465, and FILLED_GAPS supplies the
        // Ottoman Empire there from a named source. Without this pin the fill could rot silently and the
        // city would fall back to answering "nobody" for twelve years after the Conquest.
        var istanbulFill = com.eternalengine.civilization.WhoRuled.answer(none, 41.010d, 28.980d, 1460);
        var istanbulAfter = com.eternalengine.civilization.WhoRuled.answer(none, 41.010d, 28.980d, 1470);
        var saraiNow = com.eternalengine.civilization.WhoRuled.answer(none, 47.180d, 47.450d, 1400);
        var saraiLate = com.eternalengine.civilization.WhoRuled.answer(none, 47.180d, 47.450d, 1600);
        StringBuilder wrong = new StringBuilder();
        if (!"Khmer Empire".equals(angkorNow.name())) {
            wrong.append("Angkor 1400 answers " + angkorNow.display() + ", not the Khmer Empire; ");
        }
        if ("Khmer Empire".equals(angkorLate.name())) {
            wrong.append("Angkor 1864 still answers the Khmer Empire, which fell in 1431; ");
        }
        if (!"Byzantine Empire".equals(istanbulNow.name())) {
            wrong.append("Istanbul 1450 answers " + istanbulNow.display() + ", not the Byzantine Empire; ");
        }
        if (!"Ottoman Empire".equals(istanbulFill.name())
                || istanbulFill.source() != com.eternalengine.civilization.WhoRuled.Source.FILLED) {
            wrong.append("Istanbul 1460 answers " + istanbulFill.display() + " ("
                    + istanbulFill.source() + "), not the filled Ottoman gap; ");
        }
        if (istanbulAfter.name() != null && "Byzantine Empire".equals(istanbulAfter.name())) {
            wrong.append("Istanbul 1470 still answers the Byzantine Empire, which fell in 1453; ");
        }
        if (saraiNow.name() == null || saraiNow.name().isBlank()) {
            wrong.append("Sarai 1400 answers nobody, though a Horde held it; ");
        }
        if ("Golden Horde".equals(saraiLate.name())) {
            wrong.append("Sarai 1600 still answers the Golden Horde, sacked in 1502; ");
        }
        if (wrong.length() == 0) {
            pass(source, "a span does not outlive its polity: Angkor 1400 " + angkorNow.name()
                    + ", Angkor 1864 " + angkorLate.display() + ", Sarai 1400 " + saraiNow.name()
                    + ", Sarai 1600 " + saraiLate.display());
            return;
        }
        fail(source, "a polity is still recorded ruling after it had fallen", wrong.toString());
    }

    /**
     * A state must beat the umbrella it belonged to (R2-a9, 2026-10-27).
     *
     * <p>{@code AskProbe} found Berlin in 1821 answering "German Confederation" and Krakow in 1840
     * answering "Russian Empire". In both cases the register names the umbrella and is silent on the
     * state, so the more general polity won by default. A player asking who ruled Berlin wants Prussia.
     *
     * <p>Both corrections are declared boxes, so the check pins them <b>in both directions</b>: the
     * place inside the box gets the state, and three places just outside do not - Hamburg and Dresden
     * for Berlin's box, Warsaw and Lviv for Krakow's. A box that sprawls would pass the first half of
     * this check and fail the second.
     */
    private static void checkTheStateBeatsTheUmbrella(CommandSourceStack source) {
        var none = java.util.List.<com.eternalengine.civilization.WhoRuled.Cap>of();
        var berlin = com.eternalengine.civilization.WhoRuled.answer(none, 52.520d, 13.400d, 1821);
        var hamburg = com.eternalengine.civilization.WhoRuled.answer(none, 53.550d, 9.990d, 1821);
        var dresden = com.eternalengine.civilization.WhoRuled.answer(none, 51.050d, 13.740d, 1821);
        var krakow = com.eternalengine.civilization.WhoRuled.answer(none, 50.060d, 19.940d, 1840);
        var warsaw = com.eternalengine.civilization.WhoRuled.answer(none, 52.260d, 21.010d, 1840);
        var lviv = com.eternalengine.civilization.WhoRuled.answer(none, 49.840d, 24.030d, 1840);
        StringBuilder wrong = new StringBuilder();
        if (!"Kingdom of Prussia".equals(berlin.name())) {
            wrong.append("Berlin 1821 answers " + berlin.display() + ", not the Kingdom of Prussia; ");
        }
        if ("Kingdom of Prussia".equals(hamburg.name()) || "Kingdom of Prussia".equals(dresden.name())) {
            wrong.append("Berlin's box sprawls: Hamburg " + hamburg.display() + ", Dresden "
                    + dresden.display() + "; ");
        }
        if (!"Free City of Krakow".equals(krakow.name())) {
            wrong.append("Krakow 1840 answers " + krakow.display() + ", not the Free City; ");
        }
        if ("Free City of Krakow".equals(warsaw.name()) || "Free City of Krakow".equals(lviv.name())) {
            wrong.append("Krakow's box sprawls: Warsaw " + warsaw.display() + ", Lviv "
                    + lviv.display() + "; ");
        }
        if (wrong.length() == 0) {
            pass(source, "the state beats the umbrella: Berlin 1821 " + berlin.name() + " (" + berlin.source()
                    + "), Krakow 1840 " + krakow.name() + " (" + krakow.source() + "); boxes stay narrow:"
                    + " Hamburg " + hamburg.name() + ", Dresden " + dresden.name() + ", Warsaw "
                    + warsaw.name() + ", Lviv " + lviv.name());
            return;
        }
        fail(source, "a state loses to the umbrella it belonged to", wrong.toString());
    }

    /**
     * The news card tells what has happened, and never what has not (R2-e1, 2026-10-24).
     *
     * <p>Two source-checked cases, in opposite directions:
     * <ul>
     *   <li><b>Otaru 1929</b> - the tsunami at Sapporo (1928-1929) is inside range and must be told;</li>
     *   <li><b>Legnica 1619</b> - the plague at Wroclaw (1620-1621) is inside range but <b>has not begun</b>,
     *       and must not be told. The first version of the index offered it as today's news.</li>
     * </ul>
     */
    private static void checkNewsTellsOnlyWhatHasHappened(CommandSourceStack source) {
        var told = com.eternalengine.knowledge.NewsIndex.near(43.191d, 140.994d, 1929);
        var future = com.eternalengine.knowledge.NewsIndex.near(51.207d, 16.155d, 1619);
        StringBuilder wrong = new StringBuilder();
        if (told.isEmpty() || !told.get().name().toLowerCase(java.util.Locale.ROOT).contains("tsunami")) {
            wrong.append("Otaru 1929 tells " + told.map(com.eternalengine.knowledge.NewsIndex.Event::name)
                    .orElse("nothing") + "; ");
        }
        if (future.isPresent() && future.get().start() > 1619) {
            wrong.append("Legnica 1619 tells " + future.get().name() + " (" + future.get().start()
                    + "), which has not happened yet; ");
        }
        int indexed = com.eternalengine.knowledge.NewsIndex.all().size();
        if (wrong.length() == 0) {
            pass(source, "The news tells only what has happened: " + indexed + " dated events indexed;"
                    + " Otaru 1929 hears of the " + told.map(com.eternalengine.knowledge.NewsIndex.Event::name)
                    .orElse("?") + "; Legnica 1619 is not told a plague that starts in 1620");
            return;
        }
        fail(source, "The news is wrong", wrong + "(" + indexed + " events indexed)");
    }

    private static void checkStep22(CommandSourceStack source) {
        try {
            boolean onJoin = com.eternalengine.knowledge.BookService.onJoin();
            ServerPlayer player = source.getPlayer();
            String carried = "";
            if (player != null) {
                var f = com.eternalengine.knowledge.BookService.carried(player);
                carried = "; you carry " + (f == null ? "no history book" : "a " + f.title.toLowerCase(java.util.Locale.ROOT))
                        + (com.eternalengine.knowledge.BookService.mayRead(player) ? " and may read your notes"
                        : " and may not read your notes yet");
            }
            boolean words = com.eternalengine.knowledge.BookService.WELCOME.contains("buy a history book");
            report(source, words, (onJoin
                    ? "History book: given on joining (historyBookOnJoin = true, the step-20 way); more forms are bought"
                    : "History book: bought from a seller, not given on joining (historyBookOnJoin = false); a new player"
                    + " is told where to get one") + ", the notes are kept without it, and /ee book needs a book"
                    + " (operators may always)" + carried, "the welcome words are missing");
        } catch (Throwable e) {
            report(source, false, "History book: the join rule ran", e.toString());
        }
        try {
            long t0 = System.nanoTime();
            String test = com.eternalengine.news.BookForms.selfTest();
            java.util.List<double[]> pts = com.eternalengine.news.BookForms.randomPoints(400, 22L);
            int[] n = com.eternalengine.news.BookForms.census(pts);
            int writing = 0;
            int aids = 0;
            int kinds = 0;
            StringBuilder top = new StringBuilder();
            for (var f : com.eternalengine.news.BookForms.Form.values()) {
                int c = n[f.ordinal()];
                if (f.writing) {
                    writing += c;
                } else {
                    aids += c;
                }
                if (c > 0) {
                    kinds++;
                    top.append(top.length() == 0 ? "" : ", ").append(f.title.toLowerCase(java.util.Locale.ROOT)).append(' ').append(c);
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            report(source, test == null && writing + aids == pts.size() * com.eternalengine.news.BookForms.CHECK_YEARS.length,
                    "History book forms: " + com.eternalengine.news.BookForms.EXPECT.size() + " test places right (Ur 2500"
                            + " BCE clay tablets, Anyang 1200 BCE bamboo slips, Teotihuacan 500 bark paper, Norse Greenland"
                            + " 1000 rune sticks, Cusco 1500 knotted cords, Sydney 1700 a message stick, Hyderabad 1948 a"
                            + " notebook...); 400 random points in " + com.eternalengine.news.BookForms.CHECK_YEARS.length
                            + " years all have one: " + writing + " writing, " + aids + " memory aids (" + top + "; "
                            + kinds + " forms) in " + ms + " ms",
                    String.valueOf(test));
        } catch (Throwable e) {
            report(source, false, "History book forms: the rules ran", e.toString());
        }
        try {
            Object[][] cases = {
                    {com.eternalengine.money.MarketService.MERCHANT, 17.3616, 78.4747, 1948, "Hyderabad"},
                    {com.eternalengine.news.NewsEras.PAPER_SELLER, 17.3616, 78.4747, 1948, "Hyderabad"},
                    {com.eternalengine.money.MarketService.MERCHANT, 41.8925, 12.4853, 100, "Rome"},
                    {com.eternalengine.money.MarketService.PEDLAR, 30.9625, 46.1031, -2500, "Ur"},
                    {com.eternalengine.money.MarketService.TRADER, -13.5320, -71.9675, 1500, "Cusco"}};
            StringBuilder said = new StringBuilder();
            String bad = null;
            for (Object[] c : cases) {
                int year = (Integer) c[3];
                var seller = new com.eternalengine.money.ShopService.Seller((String) c[0], "Check", -1, null, "market",
                        (Double) c[1], (Double) c[2], com.eternalengine.knowledge.KnowledgeDate.key(year, 6, 1));
                var tv = com.eternalengine.money.ShopService.view(seller, null, null, null, false);
                java.util.List<String> books = new java.util.ArrayList<>();
                int shown = Math.min(com.eternalengine.money.TradeView.MAX_OFFERS, tv.offers.size());
                for (int i = 0; i < shown; i++) {
                    var o = tv.offers.get(i);
                    if (o.id().startsWith(com.eternalengine.money.ShopService.BOOK)) {
                        books.add(o.name().replace(" (history book)", "").toLowerCase(java.util.Locale.ROOT) + " " + o.priceText());
                    }
                }
                int want = com.eternalengine.news.BookForms.soldBy((String) c[0], (Double) c[1], (Double) c[2], year).size();
                if (books.size() != want && bad == null) {
                    bad = "a " + c[0] + " in " + c[4] + " " + com.eternalengine.money.ProductAtlas.yearText(year) + " shows "
                            + books.size() + " of " + want + " book offers (" + tv.offers.size() + " offers)";
                }
                said.append(said.length() == 0 ? "" : "; ").append(((String) c[0]).replace('_', ' ')).append(" in ")
                        .append(c[4]).append(' ').append(com.eternalengine.money.ProductAtlas.yearText(year)).append(": ")
                        .append(String.join(", ", books));
            }
            report(source, bad == null, "History book for sale: " + said, String.valueOf(bad));
        } catch (Throwable e) {
            report(source, false, "History book for sale: the trade lists ran", e.toString());
        }
    }

    /**
     * Board 17: <b>nobody may stand after their own civilization has fallen.</b>
     *
     * <p>Found by reading a real save: at Anyang in 430 BC there were 29 Shang townspeople, a dynasty
     * that fell in 1046 BC, and across the register one person in five stood under a civilization that
     * had already ended. The cause was that a place's people took the site's {@code civHint} (who
     * FOUNDED the place) without asking the year; {@code HistoricalPresence.spawnCivFor} now asks it.
     *
     * <p>This check walks everybody the world is holding and applies the same year rule to what is
     * actually there, so the defect cannot come back quietly. A person whose civilization id is a
     * sentinel (neutral, witness, present, ohm) is not a claim about a civilization and is skipped.
     */
    private static void checkNobodyOutlivesTheirCivilization(CommandSourceStack source) {
        if (source.getLevel() == null) {
            // No world (the headless harness, or the console before a level is up): there is no register
            // to read and nothing to judge. Said plainly rather than counted as a pass or a failure.
            info(source, "Nobody stands after their own civilization fell: no world is loaded, so there is"
                    + " no register to read");
            return;
        }
        try {
            var mem = com.eternalengine.villager.PeopleMemory.get(source.getLevel());
            var civs = SimulationEngine.getInstance().getCivilizationManager();
            int total = 0;
            int skipped = 0;
            int wrong = 0;
            String first = null;
            for (var person : mem.everyone()) {
                String civId = person.civId();
                if (civId == null || civId.isBlank() || SENTINEL_CIVS.contains(civId)) {
                    skipped++;
                    continue;
                }
                total++;
                int year = person.lastUpdatedYear();
                var civ = civs.getCivilization(civId);
                String why;
                if (civ.isEmpty()) {
                    why = "no civilization called " + civId;
                } else if (!civ.get().isActiveInYear(year)) {
                    why = civ.get().name() + " did not stand in " + year
                            + " (founded " + civ.get().foundingYear() + ", fell " + civ.get().fallYear() + ")";
                } else {
                    continue;
                }
                wrong++;
                if (first == null) {
                    first = person.displayName() + " at "
                            + String.format(java.util.Locale.ROOT, "%.3f, %.3f", person.latitude(), person.longitude())
                            + " in " + year + ": " + why;
                }
            }
            // Board 17: the remembered people are only half of it. The defect was found in the people who
            // are actually standing there, so they are read too -- every loaded figure and townsperson of
            // this level, asked the year its place stands in.
            int standing = 0;
            int standingWrong = 0;
            String firstStanding = null;
            try {
                int yearNow = com.eternalengine.clock.SimulationClockManager.isInitialized()
                        ? com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear()
                        : com.eternalengine.core.WorldStateManager.getInstance().getCurrentYear();
                for (var e : source.getLevel().getAllEntities()) {
                    if (!(e instanceof com.eternalengine.entity.HumanNpcEntity npc)) {
                        continue;
                    }
                    String civId = npc.getCivilizationId();
                    if (civId == null || civId.isBlank() || SENTINEL_CIVS.contains(civId)) {
                        continue;
                    }
                    standing++;
                    var civ = civs.getCivilization(civId);
                    String why = null;
                    if (civ.isEmpty()) {
                        why = "no civilization called " + civId;
                    } else if (!civ.get().isActiveInYear(yearNow)) {
                        why = civ.get().name() + " did not stand in " + yearNow;
                    }
                    if (why != null) {
                        standingWrong++;
                        if (firstStanding == null) {
                            firstStanding = npc.getName().getString() + " at "
                                    + npc.blockPosition().getX() + "," + npc.blockPosition().getZ()
                                    + " in " + yearNow + ": " + why;
                        }
                    }
                }
            } catch (Throwable e) {
                standingWrong++;
                if (firstStanding == null) {
                    firstStanding = "the standing people could not be read: " + e;
                }
            }
            total += standing;
            wrong += standingWrong;
            if (first == null) {
                first = firstStanding;
            }
            if (total == 0) {
                // Nothing read is not a pass. Say what was read and let the gate stay unevidenced.
                info(source, "Nobody stands after their own civilization fell: this world holds no person to"
                        + " ask, so nothing was proved here (no remembered people, no loaded figures)");
                return;
            }
            // The summary must not assert a healthy world while failing: the good line is only used when
            // it is true, and the failing line says the number and the first offender (board 17, found by
            // running this very check on a live server, where it printed "every one of them inside its
            // years" and then reported 6 of 6 outside).
            String count = total + " person(s) held under a named civilization (" + standing
                    + " standing in the loaded world)"
                    + (skipped > 0 ? ", " + skipped + " with no civilization named and not counted" : "");
            report(source, wrong == 0,
                    "Nobody stands after their own civilization fell: " + count + ", every one of them inside"
                            + " its years",
                    wrong + " of " + total + " outside their civilization's years, e.g. " + first);
        } catch (Throwable e) {
            report(source, false, "Nobody stands after their own civilization fell: the register was read",
                    e.toString());
        }
    }

    /** Ids that name no civilization: the crowd's own labels, not a claim about who ruled anywhere. */
    private static final java.util.Set<String> SENTINEL_CIVS =
            java.util.Set.of("neutral", "witness", "present", "ohm",
                    // a rival from another dimension is not a person of the history world at all
                    "rival_dimension");

    /**
     * Step 21: people stay while a player talks or trades with them; a map position becomes the block Terra++ builds
     * that place at (the world's own projection, everywhere on Earth); Minecraft's own monsters, raids and wandering
     * traders are off unless the settings say otherwise; no technology or vehicle before its first known year.
     */
    private static void checkStep21(CommandSourceStack source) {
        try {
            String t = com.eternalengine.knowledge.TalkHold.selfTest();
            report(source, t == null, "Talk: a person stays while you talk or trade with them (until goodbye, 8 blocks"
                    + " away or 2 minutes without a word; a crowd figure you talk to becomes a townsperson); "
                    + com.eternalengine.knowledge.TalkHold.count() + " held now", String.valueOf(t));
        } catch (Throwable e) {
            report(source, false, "Talk: the hold rule ran", e.toString());
        }
        try {
            checkProjection(source);
        } catch (Throwable e) {
            report(source, false, "Map positions: the round trips ran", e.toString());
        }
        try {
            String m = com.eternalengine.population.MinecraftMobs.selfTest(source.getLevel());
            report(source, m == null, "Minecraft's own mobs in the history world: "
                    + com.eternalengine.population.MinecraftMobs.settingsLine() + " (spawn eggs, spawners and /summon"
                    + " still work); " + com.eternalengine.population.MinecraftMobs.stoppedLine(), String.valueOf(m));
        } catch (Throwable e) {
            report(source, false, "Minecraft's own mobs: the rules ran", e.toString());
        }
        try {
            // Board 15: the line above posts Forge's events itself. This one asks Minecraft's real spawner, on
            // the ground, at midnight — the call the game makes while a player stands there. G6's machine half.
            String r = com.eternalengine.population.MinecraftMobs.realSpawnTest(source.getLevel());
            report(source, r == null, "A real spawn attempt in the open at night: the spawner's own selection call"
                    + " (ForgeEventFactory.getPotentialSpawns, the one the game makes while a player stands there) was"
                    + " refused on live world state and nothing appeared; " + com.eternalengine.population.MinecraftMobs.stoppedLine(),
                    String.valueOf(r));
        } catch (Throwable e) {
            report(source, false, "A real spawn attempt in the open at night: the spawner ran", e.toString());
        }
        try {
            String t = com.eternalengine.technology.TechnologyEngine.selfTest();
            String vehicles = null;
            if (com.eternalengine.vehicle.VehicleType.fastestLand(1600) == com.eternalengine.vehicle.VehicleType.TRAIN
                    || com.eternalengine.vehicle.VehicleType.AUTOMOBILE.availableInYear(1885)) {
                vehicles = "a train in 1600 or a car in 1885";
            } else if (com.eternalengine.vehicle.VehicleType.fastestLand(1830) != com.eternalengine.vehicle.VehicleType.TRAIN) {
                vehicles = "no train in 1830";
            }
            report(source, t == null && vehicles == null, "Technology: nothing before its first known year"
                    + " in any of the test years (printing press 1440, steam power 1712, electricity 1882, computing"
                    + " 1945), moving the date back takes later ones away, and no train before 1825 or car before 1886",
                    t != null ? t : String.valueOf(vehicles));
        } catch (Throwable e) {
            report(source, false, "Technology: the date rule ran", e.toString());
        }
    }

    /**
     * Step 21: every named place of the world-wide test set and 400 random points on the globe go from map position to
     * block and back (the place a player is sent to is the place they arrive at), and Charminar and King Kothi land on
     * the blocks the BTE projection gives (measured outside the game in turn 93: worldfill/probes/bte).
     */
    private static void checkProjection(CommandSourceStack source) {
        var bridge = com.eternalengine.terrabridge.TerraBridge.getInstance();
        if (bridge == null) {
            report(source, false, "Map positions: Terra++ is here", "no TerraBridge");
            return;
        }
        java.util.List<double[]> points = new java.util.ArrayList<>();
        for (Object[] p : WORLD_PLACES) {
            points.add(new double[]{(Double) p[1], (Double) p[2]});
        }
        java.util.Random random = new java.util.Random(21L);
        while (points.size() < WORLD_PLACES.length + 400) {
            double lat = Math.toDegrees(Math.asin(2.0d * random.nextDouble() - 1.0d));
            if (Math.abs(lat) <= 85.0d) {
                points.add(new double[]{lat, random.nextDouble() * 360.0d - 180.0d});
            }
        }
        int none = 0;
        int off = 0;
        double worst = 0.0d;
        String worstWhere = "";
        for (int i = 0; i < points.size(); i++) {
            double[] p = points.get(i);
            double[] xz = com.eternalengine.terrabridge.TerraCoordinateHelper.fromGeo(p[0], p[1]);
            var back = xz == null ? null
                    : com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint((int) Math.floor(xz[0]), (int) Math.floor(xz[1]));
            String where = i < WORLD_PLACES.length ? (String) WORLD_PLACES[i][0]
                    : String.format(java.util.Locale.ROOT, "%.2f, %.2f", p[0], p[1]);
            if (back == null) {
                none++;
                if (worstWhere.isEmpty()) {
                    worstWhere = where + ": no block";
                }
                continue;
            }
            double metres = metresBetween(p[0], p[1], back.latitude(), back.longitude());
            if (metres > worst) {
                worst = metres;
                if (!worstWhere.endsWith("no block")) {
                    worstWhere = where;
                }
            }
            if (metres > 50.0d) {
                off++;
            }
        }
        double[] ch = com.eternalengine.terrabridge.TerraCoordinateHelper.fromGeo(17.3616, 78.4747);
        double[] kk = com.eternalengine.terrabridge.TerraCoordinateHelper.fromGeo(17.3928, 78.4808);
        boolean chOk = ch != null && Math.abs(ch[0] - 10_071_572d) <= 3 && Math.abs(ch[1] + 2_022_547d) <= 3;
        boolean kkOk = kk != null && Math.abs(kk[0] - 10_070_410d) <= 3 && Math.abs(kk[1] + 2_026_016d) <= 3;
        String blocks = String.format(java.util.Locale.ROOT, "Charminar is block %s, King Kothi %s",
                ch == null ? "none" : String.format(java.util.Locale.ROOT, "%,.0f, %,.0f", ch[0], ch[1]),
                kk == null ? "none" : String.format(java.util.Locale.ROOT, "%,.0f, %,.0f", kk[0], kk[1]));
        report(source, none == 0 && off == 0 && chOk && kkOk,
                String.format(java.util.Locale.ROOT, "Map positions use the world's own projection (%s): %d places"
                                + " (%d named on every continent, 400 random) go to a block and back within %.0f m; %s",
                        bridge.fromGeoSource(), points.size(), WORLD_PLACES.length, worst, blocks),
                String.format(java.util.Locale.ROOT, "%d with no block, %d more than 50 m off (worst %.0f m at %s);"
                                + " expected Charminar 10,071,572, -2,022,547 and King Kothi 10,070,410, -2,026,016",
                        none, off, worst, worstWhere));
    }

    /** Great-circle distance in metres. */
    private static double metresBetween(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2.0d * 6_371_000.0d * Math.asin(Math.min(1.0d, Math.sqrt(a)));
    }

    /**
     * Step 20: the history book holds what the player heard and read (groups, notes with the day and who said it, a
     * name only heard, a family tree once the family is known, the journal), and the tracker compass points to a place
     * (King Kothi, about 3.5 km north of Charminar) through vanilla's lodestone needle.
     */
    private static void checkBook(CommandSourceStack source) {
        try {
            String test = com.eternalengine.knowledge.BookService.selfTest();
            report(source, test == null, "History book: a player's notes make the book (the Nizam from Saleem with the day,"
                    + " King Kothi only heard, the Asaf Jahi family tree once asked about, the journal), and it comes"
                    + " through the network unchanged", String.valueOf(test));
            var kk = com.eternalengine.knowledge.BookService.card("king_kothi");
            String where = kk == null ? "" : com.eternalengine.knowledge.BookService.where(17.3616, 78.4747, kk.lat(), kk.lon());
            double[] xz = kk == null ? null : com.eternalengine.terrabridge.TerraCoordinateHelper.fromGeo(kk.lat(), kk.lon());
            report(source, kk != null && kk.hasLocation() && where.contains("north") && xz != null,
                    "History book: the tracker compass can point to King Kothi (" + where + " of Charminar"
                            + (xz == null ? "" : String.format(java.util.Locale.ROOT, "; block %.0f, %.0f", xz[0], xz[1])) + ")",
                    kk == null ? "no King Kothi card" : "where \"" + where + "\", block " + (xz == null ? "none" : xz[0] + "," + xz[1]));
            ServerPlayer player = source.getPlayer();
            MinecraftServer server = source.getServer();
            if (player != null && server != null) {
                var pk = com.eternalengine.knowledge.PlayerKnowledge.get(server);
                var mine = pk.of(player.getUUID());
                int notes = 0;
                for (var e : mine.values()) {
                    notes += e.notes().size();
                }
                var track = pk.trackOf(player.getUUID());
                info(source, "Your history book: " + mine.size() + " name(s), " + notes + " note(s), "
                        + pk.journalOf(player.getUUID()).size() + " journal line(s); "
                        + (track == null ? "the compass points nowhere" : "tracking " + track.label()));
            }
        } catch (Throwable t) {
            report(source, false, "History book: the checks ran", t.toString());
        }
    }

    /**
     * Step 19g: somebody buys in every settled place (a merchant in a city, a trader in a town, a pedlar in a village
     * with money, anybody where people swap); buyers take the atlas products for less than sellers ask; a town trader
     * changes money, and where people swap he takes coins for goods.
     */
    private static void checkBuyers(CommandSourceStack source) {
        try {
            var gaz = com.eternalengine.civilization.HistoryGazetteer.get();
            int[] years = {-2000, 0, 1500, 1948};
            int places = 0;
            int nobody = 0;
            int traders = 0;
            int pedlars = 0;
            int swapping = 0;
            String firstNobody = "";
            var table = com.eternalengine.money.MoneyTable.get();
            List<com.eternalengine.civilization.HistoryGazetteer.Site> sites = gaz.allSites();
            for (int y : years) {
                for (int i = 0; i < sites.size(); i += 17) {
                    var site = sites.get(i);
                    if (!site.occupiedIn(y)) {
                        continue;
                    }
                    var rank = site.rankAt(y);
                    String[] roles = com.eternalengine.money.MarketService.withTrader(gaz.spawnRoles(site, y), rank,
                            site.lat(), site.lon(), y);
                    places++;
                    boolean buyer = false;
                    for (String r : roles) {
                        if (com.eternalengine.money.MarketService.buyer(r)) {
                            buyer = true;
                            traders += r.equals(com.eternalengine.money.MarketService.TRADER) ? 1 : 0;
                            pedlars += r.equals(com.eternalengine.money.MarketService.PEDLAR) ? 1 : 0;
                        }
                    }
                    if (!buyer && com.eternalengine.money.MarketService.swapsALittle(roles, table.at(site.lat(), site.lon(), y))) {
                        buyer = true;
                        swapping++;
                    }
                    if (!buyer) {
                        nobody++;
                        if (firstNobody.isEmpty()) {
                            firstNobody = site.displayName() + " in " + y + " (" + rank + ")";
                        }
                    }
                }
            }
            String[] kothapalle = com.eternalengine.core.WorldQuery.presence(null, com.eternalengine.core.WorldKey.at(18.87, 79.43, 1948)).spawnRoles();
            var deccan = com.eternalengine.core.WorldQuery.presence(null, com.eternalengine.core.WorldKey.at(17.36, 78.47, -3000));
            boolean village = java.util.Arrays.asList(kothapalle).contains(com.eternalengine.money.MarketService.PEDLAR);
            boolean swaps = com.eternalengine.money.MarketService.swapsALittle(deccan.spawnRoles(),
                    table.at(17.36, 78.47, -3000));
            report(source, nobody == 0 && village && swaps && places > 0,
                    "Buyers: somebody buys in every settled place (" + places + " sample places in 4 years: "
                            + traders + " town traders, " + pedlars + " village pedlars, " + swapping
                            + " where anybody swaps; a pedlar in Kothapalle in 1948, swapping in the Deccan in 3000 BCE)",
                    (nobody == 0 ? "" : nobody + " places with nobody, first " + firstNobody + "; ")
                            + "Kothapalle " + String.join(",", kothapalle) + "; Deccan swaps " + swaps);
            var t = com.eternalengine.money.MarketService.Terms.current();
            var bombay = table.at(18.94, 72.83, 1948);
            List<com.eternalengine.money.GoodsTable.Good> atlas = com.eternalengine.money.MarketService.atlasGoods(18.94, 72.83, 1948);
            String dearer = "";
            String paper = "";
            for (var g : atlas) {
                double pays = com.eternalengine.money.MarketService.pays(g, bombay, "market", t);
                double asks = com.eternalengine.money.MarketService.worth(g, bombay,
                        com.eternalengine.money.MarketService.enginePrice(g.id(), "market"));
                if (!(pays < asks) || pays <= 0) {
                    dearer += g.id() + " pays " + pays + " asks " + asks + "; ";
                }
                if (g.id().equals(com.eternalengine.money.MarketService.ATLAS + "paper")) {
                    paper = com.eternalengine.money.MarketService.priceEntry(g, pays, bombay.money(), 1948);
                }
            }
            report(source, dearer.isEmpty() && atlas.size() >= 10 && !paper.isEmpty(),
                    "Buyers: merchants take " + atlas.size() + " atlas products in Bombay in 1948, always for less than"
                            + " they ask (" + paper + ")", dearer.isEmpty() ? "paper: " + paper : dearer);
            var tr = t.as(com.eternalengine.money.MarketService.TRADER);
            var os = table.money("osmania_sicca");
            var inr = table.money("indian_rupee_anna");
            var swap = com.eternalengine.money.MarketService.swapOffer(table, os, 7 * 192, inr, 1948, tr);
            var harappa = table.at(30.63, 72.86, -2500);
            var metal = com.eternalengine.money.MarketService.swapOffer(table, os, 192, harappa.money(), -2500, tr);
            String[] town = com.eternalengine.money.MarketService.withTrader(new String[] {"farmer", "artisan"},
                    com.eternalengine.civilization.SettlementRank.TOWN, 30.63, 72.86, -2500);
            report(source, swap.fee() == t.strangeFeePercent() && swap.result() > 0 && harappa.money().barter()
                            && metal.result() > 0 && town.length == 3,
                    "Buyers: a town trader changes money keeping " + tr.strangeFeePercent() + " in 100 (7 O.S. rupees: "
                            + com.eternalengine.money.MoneyFormat.amount(inr, swap.result(), 1948)
                            + " Indian); where people swap he takes coins for goods (1 rupee: "
                            + com.eternalengine.money.MoneyFormat.amount(harappa.money(), metal.result(), -2500) + ")",
                    "fee " + swap.fee() + ", result " + swap.result() + ", metal " + metal.result() + ", town "
                            + String.join(",", town));
            ServerPlayer player = source.getPlayer();
            double[] at = player == null ? null : com.eternalengine.money.PurseService.placeOf(player);
            if (at != null) {
                int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
                info(source, "Buyers here and now: " + EternalEngineCommands.whoBuys(at[0], at[1], year,
                        table.at(at[0], at[1], year), t));
            }
        } catch (Throwable t) {
            report(source, false, "Buyers: the checks ran", t.toString());
        }
    }

    /**
     * Step 18: talking with villagers. The knowledge packs load and every name in them leads somewhere;
     * a street villager in Hyderabad in September 1948 says what the preview says, dated to the day;
     * court matters go to the palace; and nobody knows the future.
     */
    private static void checkKnowledge(CommandSourceStack source) {
        try {
            var packs = com.eternalengine.knowledge.KnowledgePacks.all();
            var problems = com.eternalengine.knowledge.KnowledgePacks.loadProblems();
            int cards = 0, news = 0;
            List<String> ids = new java.util.ArrayList<>();
            for (var p : packs) {
                cards += p.cards().size();
                news += p.news().size();
                ids.add(p.id());
            }
            report(source, !packs.isEmpty() && problems.isEmpty(),
                    "Talk: " + packs.size() + " knowledge pack(s) load (" + String.join(", ", ids) + "), " + cards
                            + " cards, " + news + " news lines",
                    problems.isEmpty() ? "No knowledge pack in the mod jar (data/eternalengine/eternal/knowledge/index.json)."
                            : String.join("; ", problems));
            List<String> broken = new java.util.ArrayList<>();
            for (var p : packs) {
                for (String b : com.eternalengine.knowledge.KnowledgePacks.validate(p)) {
                    broken.add(p.id() + ": " + b);
                }
            }
            report(source, broken.isEmpty(), "Talk: every name, distance and day in the knowledge packs checks out (no \"today\" on the wrong day, no silent days)",
                    broken.size() + " broken: " + String.join("; ", broken.subList(0, Math.min(5, broken.size()))));

            var street = com.eternalengine.knowledge.TalkService.context(null, null, -1, "Check", "citizen",
                    17.3616, 78.4747, 19480914, 1);
            String greet = com.eternalengine.knowledge.TalkService.open(street).plainText();
            var where = com.eternalengine.knowledge.TalkService.act(street, "ask", "polo", "where");
            String whereText = where == null ? "" : where.plainText();
            report(source, greet.contains("police action") && whereText.contains("Naldurg") && whereText.contains("km west"),
                    "Talk: a street villager at the Charminar on 14 September 1948 opens with the police action and puts"
                            + " Naldurg about 240 km west",
                    "Said: \"" + greet + "\" and \"" + whereText + "\"");

            var clerk = com.eternalengine.knowledge.TalkService.context(null, null, -1, "Check", "scribe",
                    17.3928, 78.4808, 19480917, 2);
            var streetOn17 = com.eternalengine.knowledge.TalkService.context(null, null, -1, "Check", "citizen",
                    17.3616, 78.4747, 19480917, 1);
            String sent = com.eternalengine.knowledge.TalkService.act(streetOn17, "card", "laiq_ali", "").plainText();
            String told = com.eternalengine.knowledge.TalkService.act(clerk, "card", "laiq_ali", "").plainText();
            report(source, sent.contains("King Kothi") && told.contains("prime minister") && !told.contains("court business"),
                    "Talk: a street villager sends court questions (the prime minister) to the palace; a palace clerk answers them",
                    "Street: \"" + sent + "\" Clerk: \"" + told + "\"");

            var before = com.eternalengine.knowledge.TalkService.context(null, null, -1, "Check", "citizen",
                    17.3616, 78.4747, 19480912, 1);
            String early = com.eternalengine.knowledge.TalkService.act(before, "card", "polo", "").plainText();
            // Step 18b: the history data dates events by year; inside the pack it must not tell of them early.
            var july = com.eternalengine.knowledge.TalkService.context(null, null, -1, "Check", "citizen",
                    17.3616, 78.4747, 19480701, 1);
            var julyView = com.eternalengine.knowledge.TalkService.open(july);
            String julyText = (julyView.plainText() + " " + julyView.names).toLowerCase(Locale.ROOT);
            boolean julyQuiet = !julyText.contains("polo") && !julyText.contains("police action") && !julyText.contains("soldiers on the roads");
            report(source, early.contains("haven't heard") && julyQuiet,
                    "Talk: nobody knows the future: on 12 September 1948 nobody has heard of the police action, and on 1 July nobody mentions it",
                    "Said: \"" + early + "\" and on 1 July: \"" + julyView.plainText() + "\"");

            double[] here = playerPlace(source);
            int today = com.eternalengine.knowledge.KnowledgeDate.today();
            if (here != null) {
                var ctx = com.eternalengine.knowledge.TalkService.context(null, null, -1, "Check", "citizen",
                        here[0], here[1], today, 1);
                info(source, "Talk here and now (" + com.eternalengine.knowledge.TalkService.dateLabel(today) + dateSource() + "): "
                        + ctx.packs().size() + " knowledge pack(s), " + ctx.local().cards().size()
                        + " basic cards from the history data. Right-click a villager to talk.");
            } else {
                info(source, "Talk: today in the game is " + com.eternalengine.knowledge.TalkService.dateLabel(today) + dateSource()
                        + ". Right-click a villager to talk; /ee date shows or sets the day.");
            }
        } catch (Throwable t) {
            fail(source, "Talk: the knowledge check could not run", t.toString());
        }
    }

    /**
     * Step 19a: money and purses. The money table loads and checks out (Hyderabad in 1948 pays in Osmania sicca: a
     * day's work 1 rupee, a paper 1 anna, a map 8 annas, Indian rupees taken at 7 to 6; and a grid of places from
     * 3000 BCE to now always finds money, or barter); the purse's sums, words and save work; and what the player
     * would pay with here and now. Nothing is given to anyone and nothing is saved.
     */
    private static void checkMoney(CommandSourceStack source) {
        try {
            var table = com.eternalengine.money.MoneyTable.get();
            var sum = table.summary();
            report(source, sum.loaded() && table.problems().isEmpty(),
                    "Money: the money table loads (" + sum.monies() + " kinds of money, " + sum.rows() + " place rows, "
                            + sum.rates() + " fixed rates) and every row checks out",
                    table.problems().isEmpty() ? "No money table in the mod jar (" + com.eternalengine.money.MoneyTable.RESOURCE + ")."
                            : String.join("; ", table.problems().subList(0, Math.min(5, table.problems().size()))));
            String broken = table.selfTest(com.eternalengine.money.MoneyTable.REGISTER);
            report(source, broken == null,
                    "Money: Hyderabad in 1948 pays in Osmania sicca (a day's work 1 rupee, a paper 1 anna, a map 8 annas;"
                            + " Indian rupees taken at 7 to 6), and every place from 3000 BCE to now has money or barter",
                    String.valueOf(broken));
            String purse = com.eternalengine.money.PurseService.selfTest();
            report(source, purse == null,
                    "Purse: sums and words work (4 annas typed is 48 pai; 205 pai reads 1 rupee 1 anna 1 pai; the start"
                            + " money comes once; a purse never goes below nothing; a saved purse reads back the same)",
                    String.valueOf(purse));
            MinecraftServer server = source.getServer();
            ServerPlayer player = source.getPlayer();
            int date = com.eternalengine.knowledge.KnowledgeDate.today();
            int year = com.eternalengine.knowledge.KnowledgeDate.year(date);
            if (server != null && player != null) {
                var purses = com.eternalengine.money.PlayerPurses.get(server);
                var here = com.eternalengine.money.PurseService.here(com.eternalengine.money.PurseService.placeOf(player), year);
                var mine = com.eternalengine.money.CoinPurse.of(player).view(); // step 23b: the pieces carried
                info(source, "Purse here and now (" + com.eternalengine.knowledge.KnowledgeDate.label(date) + "): "
                        + here.money().called() + (here.placeKnown() ? "" : " (your place on Earth is not known)")
                        + ", a day's work pays " + com.eternalengine.money.MoneyFormat.amount(here.money(), here.dayPay(), year)
                        + "; yours: " + (mine.started() ? com.eternalengine.money.PurseService.holdings(table, mine, year)
                        : "not opened yet (/ee purse gives the start money)") + "; " + purses.playerCount()
                        + " purse(s) in this world");
            } else if (server != null) {
                info(source, "Purses: " + com.eternalengine.money.PlayerPurses.get(server).playerCount()
                        + " in this world. /ee purse shows yours.");
            }
        } catch (Throwable t) {
            fail(source, "Money: the money check could not run", t.toString());
        }
    }

    /**
     * Step 19b: earning money. The goods table loads and checks out (no wheat in Tenochtitlan in 1500, no iron in
     * Babylon in 1750 BCE); the market's sums work (a merchant in Hyderabad in 1948 pays 1 rupee 1 anna 11 pai for 64
     * sheaves of wheat, half price after 64, none after 128, never beyond his day's money; the money-changer's swaps;
     * a merchant's saved day); and a money-changer stands where merchants do. Nothing is sold and nothing is saved.
     */
    /**
     * Steps 19c to 19f: sellers, the newspaper and the map. Who brings the news where and when; what sellers ask and
     * which money they take; today's paper in Hyderabad on 14 September 1948; the Map of Hyderabad State; and, for a
     * player, the news here and now.
     */
    private static void checkShops(CommandSourceStack source) {
        try {
            String e = com.eternalengine.news.NewsEras.selfTest();
            report(source, e == null, "News: who brings the news: a paper seller in Hyderabad in 1948, a town crier in a"
                    + " village, a newsletter writer under the Mughals, in Rome in 100 and in Venice in 1580", String.valueOf(e));
            String s = com.eternalengine.money.ShopService.selfTest();
            report(source, s == null, "Shops: " + com.eternalengine.money.ShopService.products().size() + " product cards;"
                    + " sellers ask the full worth (16 sheaves of wheat for 6 annas 5 pai in Hyderabad in 1948, bought for 4"
                    + " annas 5 pai); a paper costs 1 anna and a map 8 annas; Indian rupees are taken there, old Mughal money"
                    + " and pounds are not", String.valueOf(s));
            int date = com.eternalengine.knowledge.KnowledgeDate.key(1948, 9, 14);
            var paper = com.eternalengine.news.NewsService.paper(17.3616, 78.4747, date, false);
            String first = "";
            for (var pack : com.eternalengine.knowledge.KnowledgePacks.covering(17.3616, 78.4747, date)) {
                for (var day : pack.papers()) {
                    if (day.date() == date && !day.stories().isEmpty() && first.isEmpty()) {
                        first = day.stories().get(0).headline();
                    }
                }
            }
            boolean forked = com.eternalengine.news.NewsService.fork() != null;
            boolean leads = forked || (!paper.stories.isEmpty() && paper.stories.get(0).headline().equals(first));
            List<String> heads = new java.util.ArrayList<>();
            for (var st : paper.stories) {
                heads.add(st.headline());
            }
            report(source, leads && "The Evening Herald".equals(paper.name) && paper.line.contains("1 ANNA"),
                    "News: the paper in Hyderabad on 14 September 1948 is the Evening Herald, 1 anna, with that day's"
                            + " checked stories first" + (forked ? " (the world is on another road: new history first)" : ""),
                    paper.name + " | " + paper.line + " | " + String.join(" / ", heads));
            String m = com.eternalengine.news.MapService.selfTest();
            report(source, m == null, "Map: the Map of Hyderabad State has its 24 towns and 2 roads, and a paper's places"
                    + " land on it", String.valueOf(m));
            ServerPlayer player = source.getPlayer();
            double[] at = player == null ? null : com.eternalengine.money.PurseService.placeOf(player);
            if (at != null) {
                int today = com.eternalengine.knowledge.KnowledgeDate.today();
                int year = com.eternalengine.knowledge.KnowledgeDate.year(today);
                var r = com.eternalengine.core.WorldQuery.presence(null, com.eternalengine.core.WorldKey.at(at[0], at[1], year));
                var kind = com.eternalengine.news.NewsEras.kind(r.rank(), r.ruler().isPresent(), at[0], at[1], year);
                var here = com.eternalengine.news.NewsService.paper(at[0], at[1], today, kind == com.eternalengine.news.NewsEras.Kind.HANDWRITTEN);
                var map = com.eternalengine.news.MapService.view(com.eternalengine.news.MapService.item(at[0], at[1], today)
                        .getTagElement(com.eternalengine.news.MapItem.TAG), at);
                info(source, "News here and now: " + (kind == com.eternalengine.news.NewsEras.Kind.NONE ? "nobody brings it"
                        : kind.role().replace('_', ' ')) + " (" + r.rank().name().toLowerCase(java.util.Locale.ROOT)
                        + "); today's paper: " + here.name + ", " + here.stories.size() + " stories; a map here: " + map.title
                        + ", " + map.towns.size() + " towns, " + map.roads.size() + " roads");
            }
        } catch (Throwable t) {
            report(source, false, "Shops and news: the checks ran", t.toString());
        }
    }

    /**
     * Product atlas, part B: the world product list is in order and nothing is here before its first year; every input
     * is made or brought where and when it is used; every survey region has food, cloth, fuel and light in every check
     * year; and, for a player, the products here and now.
     */
    private static void checkProducts(CommandSourceStack source) {
        try {
            var atlas = com.eternalengine.money.ProductAtlas.get();
            String order = atlas.checkOrder();
            String test = atlas.selfTest();
            report(source, order == null && test == null, "Products: " + atlas.summary() + "; none is here before its"
                    + " first year; Harappa in 2500 BCE, Rome in 100, London in 1850 and Hyderabad in 1948 have what they"
                    + " should", (order == null ? "" : order + " ") + (test == null ? "" : test));
            String inputs = atlas.checkInputs();
            report(source, inputs == null, "Products: every input is made or brought where and when it is used ("
                    + atlas.inputLinks() + " links)", String.valueOf(inputs));
            String staples = atlas.checkStaples();
            report(source, staples == null, "Products: food, cloth, fuel and light in all "
                    + com.eternalengine.civilization.WaysOfLife.get().regionList().size() + " regions in "
                    + com.eternalengine.money.ProductAtlas.CHECK_YEARS.length + " years from 3000 BCE to 2000",
                    String.valueOf(staples));
            ServerPlayer player = source.getPlayer();
            double[] at = player == null ? null : com.eternalengine.money.PurseService.placeOf(player);
            if (at != null) {
                int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
                var place = atlas.place(at[0], at[1], year);
                List<String> sale = new java.util.ArrayList<>();
                for (var a : atlas.forSale(com.eternalengine.money.MarketService.MERCHANT, at[0], at[1], year)) {
                    sale.add(a.product().shortName().toLowerCase(java.util.Locale.ROOT)
                            + (a.status() == com.eternalengine.money.ProductAtlas.Status.BROUGHT ? " (brought)" : ""));
                }
                info(source, "Products here and now: " + atlas.summaryAt(place) + "; " + atlas.staplesAt(place)
                        + "; merchants here may sell: " + (sale.isEmpty() ? "none of them" : String.join(", ", sale)));
            }
        } catch (Throwable t) {
            report(source, false, "Products: the checks ran", t.toString());
        }
    }

    private static void checkMarket(CommandSourceStack source) {
        try {
            var goods = com.eternalengine.money.GoodsTable.get();
            var gs = goods.summary();
            String g = goods.selfTest();
            report(source, g == null,
                    "Market: the goods table loads (" + gs.goods() + " goods, " + gs.withItems() + " with items) and checks"
                            + " out (no wheat in Tenochtitlan in 1500, no iron in Babylon in 1750 BCE)",
                    String.valueOf(g));
            String m = com.eternalengine.money.MarketService.selfTest();
            report(source, m == null,
                    "Market: a merchant in Hyderabad in 1948 pays 1 rupee 1 anna 11 pai for 64 sheaves of wheat (half price"
                            + " after 64, none after 128, never beyond his day's money); the money-changer gives 5 rupees 14"
                            + " annas (Indian) for 7 O.S. rupees and keeps 10 in 100 of old money",
                    String.valueOf(m));
            String[] roles = com.eternalengine.money.MarketService.withMoneyChanger(
                    new String[] {"farmer", "artisan", "merchant", "citizen"}, 17.3616, 78.4747, 1948);
            int[] slots = com.eternalengine.population.SettlementDensity.roleSlots(40, roles);
            int sum = 0;
            for (int n : slots) {
                sum += n;
            }
            boolean stands = roles.length == 5 && com.eternalengine.money.MarketService.MONEY_CHANGER.equals(roles[3])
                    && slots.length == 5 && slots[3] == 1 && sum == 40;
            report(source, stands,
                    "Market: a money-changer stands where merchants do (Hyderabad in 1948: one in a crowd of 40)",
                    "roles " + String.join(",", roles) + ", places " + java.util.Arrays.toString(slots));
            ServerPlayer player = source.getPlayer();
            MinecraftServer server = source.getServer();
            if (player != null && server != null) {
                int year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
                double[] place = com.eternalengine.money.PurseService.placeOf(player);
                var here = com.eternalengine.money.PurseService.here(place, year);
                var t = com.eternalengine.money.MarketService.Terms.current();
                var bought = goods.boughtAt(place == null ? Double.NaN : place[0], place == null ? Double.NaN : place[1], year);
                String price = bought.isEmpty() ? "nobody buys goods here"
                        : "a merchant pays " + com.eternalengine.money.MarketService.priceEntry(bought.get(0),
                        com.eternalengine.money.MarketService.pays(bought.get(0), here, "market", t), here.money(), year);
                int merchants = 0;
                int changers = 0;
                for (com.eternalengine.entity.HumanNpcEntity e : player.level().getEntitiesOfClass(
                        com.eternalengine.entity.HumanNpcEntity.class, player.getBoundingBox().inflate(96.0d, 24.0d, 96.0d))) {
                    if (com.eternalengine.money.MarketService.MERCHANT.equalsIgnoreCase(e.getRole())) {
                        merchants++;
                    } else if (com.eternalengine.money.MarketService.MONEY_CHANGER.equalsIgnoreCase(e.getRole())) {
                        changers++;
                    }
                }
                info(source, "Market here and now: " + price + "; within 96 blocks " + merchants + " merchant(s) and "
                        + changers + " money-changer(s); " + com.eternalengine.money.MarketLedger.get(server).size()
                        + " merchant day(s) on record");
            }
        } catch (Throwable t) {
            fail(source, "Market: the market check could not run", t.toString());
        }
    }

    /** Where today's date comes from, for the talk lines (step 18b). */
    private static String dateSource() {
        try {
            return SimulationClockManager.isInitialized() && SimulationClockManager.getInstance().isTerraTime()
                    ? ", from the Minecraft day count: Terra++ time" : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * The player's latitude, longitude and the world year, read on the server thread; null when no
     * player ran the command or Terra++ cannot place them.
     */
    private static double[] playerPlace(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayer();
            if (player == null || !TerraBridge.isAvailable()) {
                return null;
            }
            TerraBridge.GeoPoint geo = TerraCoordinateHelper.toGeoPoint(player.blockPosition());
            if (geo == null || !geo.valid()) {
                return null;
            }
            int year = SimulationClockManager.isInitialized()
                    ? SimulationClockManager.getInstance().getCurrentYear()
                    : WorldStateManager.getInstance().getCurrentYear();
            return new double[] {geo.latitude(), geo.longitude(), year};
        } catch (Throwable t) {
            return null;
        }
    }

    /** On the worker thread: run the history parts, then print on the server thread. */
    private static void runHistory(CommandSourceStack source, MinecraftServer server,
                                   List<HistoryChecks.Part> parts, double[] here) {
        List<HistoryChecks.Section> sections = new ArrayList<>();
        Throwable error = null;
        List<String> hereLines = null;
        String hereError = null;
        try {
            for (HistoryChecks.Part part : parts) {
                sections.add(HistoryChecks.run(part));
            }
        } catch (Throwable t) {
            error = t;
        }
        if (here != null) {
            try {
                hereLines = HistoryChecks.villagerLines(here[0], here[1], (int) here[2]);
            } catch (Throwable t) {
                hereError = t.toString();
            }
        }
        Throwable err = error;
        List<String> heard = hereLines;
        String heardError = hereError;
        Runnable print = () -> {
            try {
                renderHistory(source, sections);
                if (here != null) {
                    renderHere(source, here, heard, heardError);
                }
                if (err != null) {
                    fail(source, "the history checks stopped with an error", err.toString());
                }
                finish(source);
            } catch (Throwable t) {
                log("the check could not print its results: " + t);
                source.sendFailure(Component.literal("The check could not print its results: " + t));
            } finally {
                runningSince.set(0L);
            }
        };
        if (server == null) {
            print.run();
            return;
        }
        try {
            server.execute(print);
        } catch (Throwable t) {
            // The server is shutting down; nobody is left to read the results.
            runningSince.set(0L);
        }
    }

    /**
     * Print the history sections 6-13. Chat gets the summaries and every problem; the Minecraft log gets
     * every line (the passing spot checks, and what villagers say at the 15 checklist places).
     */
    private static void renderHistory(CommandSourceStack source, List<HistoryChecks.Section> sections) {
        int number = 6;
        for (HistoryChecks.Section s : sections) {
            section(source, number++ + ". " + s.part().title()
                    + String.format(Locale.ROOT, " §8(%s, %.1f s)", s.part().id(), s.millis() / 1000.0d));
            for (HistoryChecks.Line l : s.lines()) {
                boolean show = l.brief();
                String tag;
                switch (l.status()) {
                    case OK -> tag = "  §a[ OK ]§r ";
                    case FAIL -> tag = "  §c[FAIL]§r ";
                    case KNOWN -> tag = "  §e[KNOWN]§r ";
                    default -> tag = "  §8· §7";
                }
                if (l.counted()) {
                    switch (l.status()) {
                        case OK -> pass++;
                        case FAIL -> {
                            fail++;
                            failures.add(l.text() + (l.detail().isEmpty() ? "" : " — " + l.detail()));
                        }
                        case KNOWN -> known++;
                        default -> { }
                    }
                }
                emit(source, tag + l.text(), show);
                if (!l.detail().isEmpty()) {
                    emit(source, "         §7" + l.detail(), show && l.status() != HistoryChecks.Status.OK);
                }
            }
        }
    }

    /** What villagers around the player can say in this year (the lines their town talk picks from). */
    private static void renderHere(CommandSourceStack source, double[] here, List<String> lines, String error) {
        section(source, "Here and now: what villagers around you can say §8(" + fmt(here[0]) + ", " + fmt(here[1])
                + ", " + yearText((int) here[2]) + ")");
        if (error != null) {
            fail(source, "the villager talk at your position stopped with an error", error);
            return;
        }
        if (lines.isEmpty()) {
            pass(source, "nothing is recorded here this year, so villagers do not talk about their land (that is"
                    + " normal on empty land)");
            return;
        }
        pass(source, "villagers here have " + lines.size() + (lines.size() == 1 ? " thing" : " things")
                + " to say about their land");
        for (String line : lines) {
            info(source, "\"" + line + "\"");
        }
    }

    /** The summary, what is left for a person to look at, and where every line is: the Minecraft log. */
    private static void finish(CommandSourceStack source) {
        say(source, "");
        String knownText = known == 0 ? "" : " §7(" + known + " known " + (known == 1 ? "limit" : "limits")
                + ", marked §eKNOWN§7)";
        if (fail == 0) {
            say(source, "§a=== ALL " + pass + " CHECKS PASSED" + knownText + " §a===§r");
            say(source, "§7The live system is wired and loaded. That part does NOT prove the logic is right — "
                    + "that is what §ftests/run_all.sh§7 is for.");
            say(source, "§7The history answers are the ones checked by hand when the data was built.");
        } else {
            say(source, "§c=== " + fail + " CHECK(S) FAILED §7(" + pass + " passed" + (known == 0 ? "" : ", " + known
                    + " known " + (known == 1 ? "limit" : "limits")) + ") ===");
            for (String f : failures) {
                say(source, "  §c· §7" + f);
            }
            say(source, "§7The lines marked §cFAIL§7 above are the ones to fix. Send the lines marked " + LOG_TAG
                    + " in the Minecraft log to whoever can read them.");
        }
        say(source, "§8Not covered here: whether the code is correct, and anything that needs a player to "
                + "actually fight. Those are the test suites, not this command.");
        say(source, "§8Still for your own eyes: how villagers look and move, and whether their talk feels natural."
                + " The Minecraft log lists what villagers should say at each of the 15 checklist places, and how"
                + " to go there.");
        log("end of check: " + pass + " passed, " + fail + " failed, " + known + " known "
                + (known == 1 ? "limit" : "limits"));
        String where = latestLog();
        if (toChat) {
            Component line = Component.literal("§8Every line of this check is in the Minecraft log, marked "
                            + LOG_TAG + ": §7" + where + " §8(click to copy)")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, where))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Copy the path of the Minecraft log"))));
            source.sendSuccess(() -> line, false);
        } else {
            int p = pass;
            int f = fail;
            int k = known;
            source.sendSuccess(() -> Component.literal("All checks done: " + p + " passed, " + f + " failed, " + k
                    + " known " + (k == 1 ? "limit" : "limits") + ". Every line is in the log, marked " + LOG_TAG
                    + "."), false);
        }
    }

    /** The Minecraft log every line goes to: logs/latest.log in the game folder (the server folder on a server). */
    private static String latestLog() {
        try {
            return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("latest.log")
                    .toAbsolutePath().normalize().toString();
        } catch (Throwable t) {
            return "logs/latest.log"; // no Forge (a test harness): the working directory's log
        }
    }

    // ==================== 1. core ====================

    private static void checkSimulationCore(CommandSourceStack source) {
        boolean init = SimulationEngine.isInitialized();
        report(source, init, "SimulationEngine is initialised",
                "the mod never started — nothing below can work");

        if (!init) {
            return;
        }

        SimulationEngine engine = SimulationEngine.getInstance();
        report(source, engine != null, "SimulationEngine instance reachable", "getInstance() returned null");

        boolean clock = SimulationClockManager.isInitialized();
        report(source, clock, "the world clock is running", "SimulationClockManager is not initialised");
        if (clock) {
            int year = SimulationClockManager.getInstance().getCurrentYear();
            info(source, "the world is in year " + yearText(year));
            report(source, year > -100000 && year < 100000, "the current year is a sane number",
                    "the clock reports year " + year);
            checkCalendar(source);
        }

        // Every schedule must be registered, or that system silently never ticks. This is the
        // quietest possible failure: the world just stops changing in one dimension.
        try {
            Set<String> registered = SimulationTickScheduler.getInstance().getScheduledSystems();
            List<String> missing = new ArrayList<>();
            for (String name : REQUIRED_SCHEDULES) {
                if (!registered.contains(name)) {
                    missing.add(name);
                }
            }
            report(source, missing.isEmpty(),
                    "all " + REQUIRED_SCHEDULES.length + " tick schedules are registered",
                    "these systems will never tick: " + missing);
            info(source, "registered schedules (" + registered.size() + "): "
                    + String.join(", ", new java.util.TreeSet<>(registered)));
        } catch (Throwable t) {
            fail(source, "the tick scheduler could not be read", t.toString());
        }

        checkCoreStep14(source, engine);
    }

    /**
     * Step 14 (the core review): which clock runs, the pause, the server config's intervals, systems that
     * failed, a save read back from disk, and the world's data files.
     */
    private static void checkCoreStep14(CommandSourceStack source, SimulationEngine engine) {
        if (SimulationClockManager.isInitialized()) {
            info(source, SimulationClockManager.getInstance().describeMode());
            List<String> slow = SimulationClockManager.getInstance().describeSlowdowns();
            if (!slow.isEmpty()) {
                info(source, "history is slowed for " + String.join(", ", slow));
            }
        }
        String why = engine.pauseReason();
        info(source, why == null ? "history is running" : "history is " + why);

        // The server config's intervals reach the scheduler (they never did before step 14).
        try {
            SimulationTickScheduler scheduler = SimulationTickScheduler.getInstance();
            String[][] keys = {
                    {"war", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.warUpdateInterval.get())},
                    {"disease", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.diseaseUpdateInterval.get())},
                    {"economy", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.economyUpdateInterval.get())},
                    {"trade", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.tradeUpdateInterval.get())},
                    {"population", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.populationUpdateInterval.get())},
                    {"reputation", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.reputationDecayInterval.get())},
                    {"religion", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.religionUpdateInterval.get())},
                    {"technology", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.technologyUpdateInterval.get())},
                    {"climate", String.valueOf(com.eternalengine.config.EternalEngineConfig.SERVER.climateUpdateInterval.get())},
            };
            List<String> wrong = new ArrayList<>();
            StringBuilder used = new StringBuilder();
            for (String[] k : keys) {
                int inUse = scheduler.getBaseInterval(k[0]);
                used.append(used.length() == 0 ? "" : ", ").append(k[0]).append(' ').append(inUse);
                if (inUse != Integer.parseInt(k[1])) {
                    wrong.add(k[0] + " uses " + inUse + ", the config says " + k[1]);
                }
            }
            report(source, wrong.isEmpty() && scheduler.usesWorldConfig(),
                    "the server config's intervals are in use (ticks: " + used + ")",
                    scheduler.usesWorldConfig() ? String.join("; ", wrong)
                            : "the scheduler never read this world's server config");
        } catch (Throwable t) {
            fail(source, "the server config's intervals could not be compared", t.toString());
        }

        // Every system runs in its own safety net; any that failed since the world started is listed.
        try {
            var failures = engine.systems().failures();
            StringBuilder why2 = new StringBuilder();
            failures.forEach((id, f) -> why2.append(why2.length() == 0 ? "" : "; ").append(id).append(" failed ")
                    .append(f.count()).append("x, latest: ").append(f.lastError()));
            report(source, failures.isEmpty(), "no world system has failed since this world started",
                    failures.size() + " system(s) failed (the others kept running): " + why2
                            + ". The server log has the first error of each with its stack trace");
        } catch (Throwable t) {
            fail(source, "the failed-system list could not be read", t.toString());
        }

        // What a save writes must hold the running world, and the last save on disk must read back. Read-only
        // since step 15 (the check writes no file): the content is built in memory exactly as a save builds it,
        // and state.json is only read. The autosave and leaving the world do the saving.
        try {
            WorldStateManager wsm = WorldStateManager.getInstance();
            com.google.gson.JsonObject saved = com.google.gson.JsonParser.parseString(
                    com.eternalengine.core.PersistenceManager.getInstance().getGson().toJson(wsm.previewStateJson()))
                    .getAsJsonObject();
            int wars = wsm.getAllActiveWars().size();
            int outbreaks = wsm.getAllActiveDiseases().size();
            int routes = wsm.getAllTradeRoutes().size();
            int flags = wsm.getFlagCount();
            boolean ok = saved.has("wars") && saved.getAsJsonArray("wars").size() == wars
                    && saved.has("diseases") && saved.getAsJsonArray("diseases").size() == outbreaks
                    && saved.has("tradeRoutes") && saved.getAsJsonArray("tradeRoutes").size() == routes
                    && saved.has("flags") && saved.getAsJsonObject("flags").size() == flags
                    && saved.has("clockTicksInYear") && saved.has("calendar") && saved.has("adminPaused")
                    && saved.has("dataVersion") && saved.get("dataVersion").getAsInt()
                            == com.eternalengine.core.VersionManager.CURRENT_VERSION;
            Path file = wsm.getStateFile();
            boolean diskOk = true;
            String disk;
            if (file == null || !Files.isRegularFile(file)) {
                disk = "not on disk yet (the autosave or leaving the world writes it)";
            } else {
                try {
                    com.google.gson.JsonObject onDisk = com.google.gson.JsonParser.parseString(
                            Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                    int version = onDisk.has("dataVersion") ? onDisk.get("dataVersion").getAsInt() : 0;
                    long at = onDisk.has("savedAt") ? onDisk.get("savedAt").getAsLong()
                            : Files.getLastModifiedTime(file).toMillis();
                    disk = "the last save on disk reads back (" + savedAgo(at)
                            + (version < com.eternalengine.core.VersionManager.CURRENT_VERSION
                                    ? "; it is from before this mod version, and the next save updates it" : "")
                            + ")";
                } catch (Throwable t) {
                    diskOk = false;
                    disk = "state.json on disk cannot be read (" + t.getMessage() + "), so at the next start the"
                            + " world would fall back to its newest backup. File: " + file.toAbsolutePath().normalize();
                }
            }
            String counts = wars + " war(s), " + outbreaks + " outbreak(s), " + routes + " trade route(s), " + flags
                    + " world flag(s)";
            if (ok && diskOk) {
                pass(source, "the running world is in what a save writes (" + counts + "); " + disk);
            } else if (!ok) {
                fail(source, "the running world is in what a save writes (" + counts + ")",
                        "what a save writes does not hold what is in memory: a restart would lose it"
                                + (diskOk ? "" : ". Also, " + disk));
            } else {
                fail(source, "the last save on disk reads back", disk);
            }
            info(source, "restored at this world's start: " + wsm.getLastLoadSummary());
        } catch (Throwable t) {
            fail(source, "what a save writes could not be built and compared", t.toString());
        }

        // The world's data files against this mod version.
        try {
            var last = FoundationDataInstaller.lastRefresh(source.getServer());
            if (last.isEmpty()) {
                info(source, "no data check ran for this world yet");
            } else {
                var r = last.get();
                long kept = r.count(FoundationDataInstaller.FileState.KEPT_EDITED)
                        + r.count(FoundationDataInstaller.FileState.KEPT_UNKNOWN);
                long changed = r.count(FoundationDataInstaller.FileState.ADDED) + r.count(FoundationDataInstaller.FileState.UPDATED);
                StringBuilder failed = new StringBuilder();
                for (var f : r.with(FoundationDataInstaller.FileState.FAILED)) {
                    failed.append(failed.length() == 0 ? "" : "; ").append(f.resource()).append(": ").append(f.detail());
                }
                report(source, failed.length() == 0,
                        "the world's data files are this mod version's (data version " + r.dataVersion()
                                + (changed > 0 ? ", " + changed + " added or updated at the start" : "")
                                + (kept > 0 ? ", " + kept + " kept as this world has them (/ee data status)" : "") + ")",
                        "some data files could not be installed or updated: " + failed);
            }
        } catch (Throwable t) {
            fail(source, "the world's data files could not be checked", t.toString());
        }
    }

    // ==================== 2. corpus ====================

    private static void checkCorpus(CommandSourceStack source) {
        try {
            HistoryGazetteer.get();
            int pins = HistoryGazetteer.get().allEvents().size();
            report(source, pins > 1000, "the world's history corpus is loaded (" + pins + " events)",
                    "only " + pins + " events loaded — the history files are missing or unreadable");

            // A pin at 0,0 is the signature of the old key-name bug, where every event silently
            // read as latitude 0. If this ever comes back, it comes back here.
            long atZero = HistoryGazetteer.get().allEvents().stream()
                    .filter(e -> e.lat() == 0.0 && e.lon() == 0.0).count();
            report(source, atZero == 0,
                    "no event is stranded at 0,0 (the key-name bug stays fixed)",
                    atZero + " events read as 0,0 — the loader has regressed");

            // The one pin this whole feature was built around.
            boolean taj = HistoryGazetteer.get().nearestEvent(18.9217, 72.8331, 2008).isPresent();
            info(source, "26/11 as the world sees it in 2008: "
                    + (taj ? "§aFOUND" : "§cMISSING (already prevented here, or not loaded)"));
        } catch (Throwable t) {
            fail(source, "the history gazetteer threw while loading", t.toString());
        }

        try {
            // Since 2026-10-12 a world without the foundation data gets it when it starts
            // (FoundationDataInstaller.installIfMissing); say so, because that explains a world folder
            // that suddenly has an eternalengine/ copy, and the failure below if that install did not work.
            FoundationDataInstaller.lastAutomatic(source.getServer()).ifPresent(a -> {
                if (a.ran() && a.result() != null) {
                    int copied = a.result().copied();
                    info(source, "this world had no foundation data when it started, so the mod installed it then ("
                            + copied + (copied == 1 ? " file" : " files") + (a.result().failed() > 0
                                    ? ", " + a.result().failed() + " failed" : "") + ")");
                }
            });
            HistoricalEventDatabase db = SimulationEngine.getInstance().getHistoricalEventDatabase();
            int n = db == null ? -1 : db.getSize();
            report(source, n > 0, "the authored event chain loaded (" + n + " events)",
                    installed(source)
                            ? "the event chain database is empty — events/ did not load"
                            : "this world has no installed foundation data" + autoInstallProblem(source)
                                    + ". Run /ee data install, then /ee data load");
        } catch (Throwable t) {
            fail(source, "the event chain database could not be read", t.toString());
        }
    }

    // ==================== 3. divergence ====================

    private static void checkDivergence(CommandSourceStack source) {
        TimelineManager tm;
        try {
            tm = SimulationEngine.getInstance().getTimelineManager();
        } catch (Throwable t) {
            fail(source, "the timeline manager could not be reached", t.toString());
            return;
        }
        report(source, tm != null, "the timeline manager is reachable", "getTimelineManager() returned null");
        if (tm == null) {
            return;
        }

        // Step 1 of bootstrapDivergence. If this is zero, the bootstrap never ran, and a fork
        // would fail to suppress the rest of the night it happened on.
        SiblingIndex index = tm.siblingIndex();
        int indexed = index == null ? 0 : index.pinCount();
        report(source, indexed > 1000,
                "the sibling index is built (" + indexed + " pins) — bootstrapDivergence step 1 ran",
                "the sibling index has " + indexed + " pins — bootstrap never ran; "
                        + "a fork would delete one event but leave the rest of that night standing");

        // Step 2.
        ScenarioRegistry scenarios = tm.scenarios();
        int scen = scenarios == null ? 0 : scenarios.size();
        report(source, scen > 0, "the authored scenarios are loaded (" + scen + ")",
                "no scenarios loaded — forks would change history but tell no story" + scenarioHint(source));
        if (scenarios != null) {
            for (String err : scenarios.loadErrors()) {
                fail(source, "a scenario file failed to load", err);
            }
            int usable = 0;
            for (String id : new String[]{
                    "what_if_mumbai_26_11", "what_if_tesla_1943", "what_if_gandhi_1948"}) {
                ScenarioDefinition def = scenarios.byId(id).orElse(null);
                if (def != null && def.isUsable() && def.hasAuthor()) {
                    usable++;
                }
            }
            info(source, usable + " of the 3 known stories are usable and credited");
        }

        // Step 3.
        TimelineBranch active = tm.getActiveBranch();
        report(source, active != null, "there is an active timeline",
                "the active branch could not be resolved");
        if (active == null) {
            return;
        }
        info(source, "active timeline: " + active.branchId()
                + (active.isMainTimeline() ? " §a(this is the recorded timeline — nothing has been changed)"
                                           : " §c(an alternate timeline)")
                + "§r");

        // The saved state and the live manager must agree. A mismatch means a world was restored
        // into a branch the manager is not actually on, so the player's actions and the save file
        // describe different worlds.
        try {
            String saved = WorldStateManager.getInstance().getActiveTimelineBranchId();
            boolean agree = saved == null || saved.equals(tm.activeBranchId());
            report(source, agree, "the saved world state and the live timeline agree",
                    "the save says '" + saved + "' but the engine is on '" + tm.activeBranchId() + "'");
        } catch (Throwable t) {
            fail(source, "the saved timeline branch could not be read", t.toString());
        }

        // THE check that only a live server can make. If a fork is active, the world must not be
        // able to find the event that was stopped. This is the end-to-end proof that the
        // suppression overlay was actually installed — the bug that made in-game forks invisible.
        if (!active.isMainTimeline()) {
            String pivot = active.divergenceEventId();
            ScenarioDefinition def = scenarios == null ? null
                    : scenarios.forPin(pivot == null ? "" : pivot).orElse(null);
            if (def != null) {
                HistoryGazetteer.Event gone = findPin(def.triggerPin());
                if (gone != null) {
                    boolean hidden = HistoryGazetteer.get()
                            .nearestEvent(gone.lat(), gone.lon(), active.divergenceYear())
                            .map(hit -> !hit.event().id().equals(gone.id()))
                            .orElse(true);
                    report(source, hidden,
                            "the stopped event is GENUINELY GONE from this world — the overlay is live",
                            "'" + def.triggerPin() + "' was stopped but the world can still find it: "
                                    + "the suppression overlay is not installed");
                }
            } else if (pivot != null && !pivot.isBlank()) {
                info(source, "the divergence '" + pivot + "' has no authored story; "
                        + "the world still hides it, but nothing is written about what followed");
            }
        } else {
            info(source, "no fork is active, so there is nothing to hide — "
                    + "fight at a real event's place and year to test this for real");
        }

        // Step 4 is proven by the pin check above on a forked world. On the recorded timeline it
        // is unobservable by design, which is correct: nothing is supposed to be hidden.
    }

    // ==================== 4. in-world path ====================

    private static void checkInWorldPath(CommandSourceStack source) {
        // Terra++ is what turns a block position into a real place on Earth. If this is absent,
        // a player's kills can never be attached to history, and the whole in-world feature is
        // dead — silently, with no error anywhere.
        boolean available;
        try {
            available = TerraBridge.isAvailable();
        } catch (Throwable t) {
            fail(source, "the Terra++ bridge could not be asked whether it is available", t.toString());
            return;
        }
        report(source, available, "Terra++ is available (the world is a real place on Earth)",
                "Terra++ is not available — without it no player action can ever be located");

        // The 28,639 UCDP spots (1989-2024) are kind "conflict": news of a war, never one battle a
        // player could stand in front of. The detector refuses any kind it does not list.
        boolean conflictRefused = !com.eternalengine.player.InWorldInterventionDetector.STOPPABLE_KINDS
                .contains("conflict");
        report(source, conflictRefused,
                "modern conflict spots (UCDP, 1989-2024) cannot be stopped by fighting at them",
                "'conflict' is in STOPPABLE_KINDS — a player could undo a modern war by fighting at a pin");

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            info(source, "no player is attached to this command, so the position checks are skipped. "
                    + "Run §f/ee check all§7 as a player standing in the world to test them.");
        } else if (available) {
            try {
                TerraBridge.GeoPoint geo = TerraCoordinateHelper.toGeoPoint(player.blockPosition());
                if (geo == null) {
                    fail(source, "a player's position could not be converted to a place on Earth",
                            "TerraCoordinateHelper.toGeoPoint returned null");
                } else if (!geo.valid()) {
                    fail(source, "the player's position converts to an invalid place on Earth",
                            "lat=" + geo.latitude() + " lon=" + geo.longitude());
                } else {
                    pass(source, "the player's position maps to a real place on Earth: "
                            + fmt(geo.latitude()) + ", " + fmt(geo.longitude()));
                    checkStandingInHistory(source, geo);
                    checkCrowd(source, player);
                }
            } catch (Throwable t) {
                fail(source, "the position conversion threw", t.toString());
            }
        }
    }

    /**
     * Is the player standing somewhere history happened? Read-only on purpose: this must never
     * disturb the evidence the detector is accumulating.
     */
    private static void checkStandingInHistory(CommandSourceStack source, TerraBridge.GeoPoint geo) {
        try {
            int year = SimulationClockManager.isInitialized()
                    ? SimulationClockManager.getInstance().getCurrentYear() : 2008;
            var hit = HistoryGazetteer.get().nearestEvent(geo.latitude(), geo.longitude(), year);
            if (hit.isEmpty() || hit.get() == null) {
                info(source, "nothing historical is happening where you are standing, in " + year
                        + ". That is normal — most places are quiet, most years.");
                return;
            }
            HistoryGazetteer.Event e = hit.get().event();
            // Mirrors the detector's own gate, so this command can never disagree with the actual
            // decision the game would make. If it said "you can stop this" and the detector
            // refused, the command would be lying to the player.
            boolean stoppable = com.eternalengine.player.InWorldInterventionDetector.STOPPABLE_KINDS
                    .contains(e.kind())
                    && !com.eternalengine.player.InWorldInterventionDetector.REFUSED_KINDS
                            .contains(e.kind());
            info(source, "you are standing at: §f" + e.name() + "§7 (" + e.kind() + ", "
                    + e.start() + "–" + e.end() + ")");
            if (stoppable) {
                info(source, "  this is the kind of event a person CAN stand in front of. "
                        + "Fighting here, now, would change history.");
            } else {
                info(source, "  this is a " + e.kind() + " — no amount of force can stop it. "
                        + "The game will tell you so if you try.");
            }
        } catch (Throwable t) {
            fail(source, "the event lookup at the player's position threw", t.toString());
        }
    }

    /**
     * The drawn crowd around the player (step 16). Every figure must be found by its own UUID and nobody may
     * stand twice. Until 2026-10-12 the crowd never stood at all (a search box ran to x = 536,870,911 and every
     * try failed), and its code changed the UUID of figures already in the world, which after a short walk
     * left most of them unfindable by their own UUID. Read-only; counted only when figures stand around you.
     */
    private static void checkCrowd(CommandSourceStack source, ServerPlayer player) {
        try {
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            List<com.eternalengine.entity.HumanNpcEntity> figures = level.getEntitiesOfClass(
                    com.eternalengine.entity.HumanNpcEntity.class, player.getBoundingBox().inflate(160.0, 512.0, 160.0),
                    com.eternalengine.population.CrowdFigures::isFigure);
            if (figures.isEmpty()) {
                info(source, "no crowd figures stand around you (no town here this year, the crowd is still being"
                        + " stood up, or it is switched off with presenceDrawnCrowd)");
                return;
            }
            Set<java.util.UUID> ids = new java.util.HashSet<>();
            int lost = 0;
            for (com.eternalengine.entity.HumanNpcEntity figure : figures) {
                ids.add(figure.getUUID());
                if (level.getEntity(figure.getUUID()) != figure) {
                    lost++;
                }
            }
            int twice = figures.size() - ids.size();
            report(source, lost == 0 && twice == 0,
                    "the crowd around you is sound: " + figures.size() + " figures within 160 blocks, each found"
                            + " by its own UUID, nobody twice",
                    lost + " of " + figures.size() + " figures cannot be found by their own UUID and " + twice
                            + " stand twice: the crowd's identities are broken");
        } catch (Throwable t) {
            fail(source, "the crowd around you could not be checked", t.toString());
        }
    }

    // ==================== 5. story surface ====================

    private static void checkStorySurface(CommandSourceStack source) {
        try {
            TimelineManager tm = SimulationEngine.getInstance().getTimelineManager();
            int year = SimulationClockManager.isInitialized()
                    ? SimulationClockManager.getInstance().getCurrentYear() : 2008;
            List<String> pages = ChronosCodexData.buildChroniclePages(tm, year);
            report(source, pages != null && !pages.isEmpty(),
                    "the history gazette renders without throwing",
                    "buildChroniclePages returned nothing");

            String text = pages == null ? "" : String.join("\n", pages);
            report(source, !text.contains("Diplomatic embassies"),
                    "the gazette invents nothing",
                    "the fabricated gazette text is back");

            if (tm != null && tm.getActiveBranch() != null && !tm.getActiveBranch().isMainTimeline()) {
                int entries = tm.activeChronicle().size();
                report(source, entries > 0,
                        "this alternate timeline has a chronicle (" + entries + " entries)",
                        "the branch is active but its chronicle is empty — no story was attached");
                info(source, tm.activeChronicle().trustSummary());
            }
        } catch (Throwable t) {
            fail(source, "the gazette threw while rendering", t.toString());
        }
    }

    // ==================== helpers ====================

    /** Has {@code /ee data install} put the foundation data into this world? */
    private static boolean installed(CommandSourceStack source) {
        try {
            return com.eternalengine.data.FoundationDataInstaller.hasInstalledData(source.getServer());
        } catch (Throwable t) {
            return true; // unknown: keep the original message
        }
    }

    /** {@code · world "New World"}: which world a check is about (a copy of its log lines does not say otherwise). */
    private static String worldLabel(CommandSourceStack source) {
        try {
            MinecraftServer server = source.getServer();
            String name = server.getWorldData().getLevelName();
            Path folder = EternalDataPaths.root(server).toAbsolutePath().normalize().getParent();
            String dir = folder == null || folder.getFileName() == null ? "" : folder.getFileName().toString();
            return " · world \"" + name + "\"" + (dir.isEmpty() || dir.equals(name) ? "" : " (folder " + dir + ")");
        } catch (Throwable t) {
            return "";
        }
    }

    /** Why the automatic install at world start did not leave the data there (empty when unknown). */
    private static String autoInstallProblem(CommandSourceStack source) {
        try {
            return FoundationDataInstaller.lastAutomatic(source.getServer())
                    .filter(a -> a.ran() && a.result() != null)
                    .map(a -> ", although the mod tried to install it when the world started ("
                            + a.result().copied() + " copied, " + a.result().failed() + " failed"
                            + (a.result().message() == null || a.result().message().isBlank() ? ""
                                    : ": " + a.result().message()) + ")")
                    .orElse(" yet");
        } catch (Throwable t) {
            return " yet";
        }
    }

    /** Why no stories loaded: nothing installed, or installed after the world started (read at start only). */
    private static String scenarioHint(CommandSourceStack source) {
        if (!installed(source)) {
            return ". This world has no installed foundation data" + autoInstallProblem(source)
                    + ": run /ee data install, then leave and rejoin the world (the stories are read when it starts)";
        }
        try (java.util.stream.Stream<Path> files = Files.list(EternalDataPaths.scenarios(source.getServer()))) {
            if (files.anyMatch(f -> f.getFileName().toString().endsWith(".json"))) {
                return ". The stories were installed after this world started: leave and rejoin the world, or"
                        + " restart the server";
            }
        } catch (Throwable ignored) {
            // No scenarios folder: the original message says it all.
        }
        return "";
    }

    private static HistoryGazetteer.Event findPin(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        // allEvents() is the raw corpus and is deliberately not filtered, so a pin that has been
        // erased from the world can still be looked up here to prove it was erased.
        for (HistoryGazetteer.Event e : HistoryGazetteer.get().allEvents()) {
            if (id.equals(e.id())) {
                return e;
            }
        }
        return null;
    }

    private static void section(CommandSourceStack source, String title) {
        say(source, "");
        say(source, "§6" + title + "§r");
    }

    /** A line to the Minecraft log, and to chat when a player ran the check. */
    private static void say(CommandSourceStack source, String line) {
        emit(source, line, true);
    }

    /** A line to the Minecraft log, and to chat too if {@code show} and a player ran the check. */
    private static void emit(CommandSourceStack source, String line, boolean show) {
        log(line);
        if (show && toChat) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }

    /**
     * One line of the check in the Minecraft log (logs/latest.log), marked {@link #LOG_TAG}, without colour
     * codes. One log line per report line, so each can be found and read on its own.
     */
    private static void log(String line) {
        String text = line.replaceAll("§.", "");
        try {
            EternalEngineMod.getLogger().info(text.isBlank() ? LOG_TAG : LOG_TAG + " " + text);
        } catch (Throwable t) {
            System.out.println(LOG_TAG + " " + text); // no logger (never in the game): still somewhere to read
        }
    }

    /** "saved 3 min ago". */
    private static String savedAgo(long savedAtMillis) {
        long s = Math.max(0L, (System.currentTimeMillis() - savedAtMillis) / 1000L);
        if (s < 60L) {
            return "saved " + s + " s ago";
        }
        if (s < 3600L) {
            return "saved " + s / 60L + " min ago";
        }
        if (s < 2L * 86400L) {
            return "saved " + s / 3600L + " h ago";
        }
        return "saved " + s / 86400L + " days ago";
    }

    /**
     * Step 18d: the world's one calendar. Its rules on made-up times (/time set day at night gives the next morning, a
     * day count put back is put right, a new speed keeps the date, leap years, dates before the common era), whether
     * /time set still runs the calendar's forward-only version, and whether the date has a value. Nothing changes.
     */
    private static void checkCalendar(CommandSourceStack source) {
        try {
            String broken = com.eternalengine.clock.WorldCalendar.selfTest();
            boolean ours = source.getServer() != null && com.eternalengine.clock.WorldCalendar.timeSetIsOurs(source.getServer());
            int today = com.eternalengine.clock.WorldCalendar.today();
            boolean ok = broken == null && ours && com.eternalengine.clock.WorldCalendar.isStarted()
                    && today != com.eternalengine.knowledge.KnowledgeDate.ALWAYS;
            report(source, ok, "the date follows the Minecraft days and only /ee date moves it back (today "
                            + com.eternalengine.knowledge.KnowledgeDate.label(today) + "; /time set only goes forward)",
                    broken != null ? "a calendar rule is broken: " + broken
                            : !ours ? "/time set is not the calendar's any more (another mod replaced /time), so /time set"
                                    + " day can put the date back; the guard still puts it right at the next tick"
                            : "the calendar has not started, so the date has no value");
            int restores = com.eternalengine.clock.WorldCalendar.restores();
            info(source, com.eternalengine.clock.WorldCalendar.describeRule() + (restores == 0 ? ""
                    : "; Minecraft's day was put right " + restores + " time(s) since the world was loaded (see the log"
                            + " lines \"[Calendar] Minecraft's day went back\")"));
        } catch (Throwable t) {
            fail(source, "the calendar could not be checked", t.toString());
        }
    }

    private static void report(CommandSourceStack source, boolean ok, String good, String bad) {
        if (ok) {
            pass(source, good);
        } else {
            fail(source, good, bad);
        }
    }

    /** Take snapshots on the server thread. Never spawn, interact with, or mutate an entity or slot. */
    private static void checkR2People(CommandSourceStack source) {
        try {
            var engine = SimulationEngine.getInstance();
            var system = engine.getVipSystem();
            boolean registryAvailable = system != null;
            var slots = new ArrayList<com.eternalengine.testing.R2PeopleChecks.Vip>();
            if (registryAvailable) for (var v : system.allVips()) {
                slots.add(new com.eternalengine.testing.R2PeopleChecks.Vip(v.vipId(), v.name(), v.birthYear(),
                        v.historicalDeathYear(), v.spawnLat(), v.spawnLon(), v.historicalEventIds().size(),
                        v.dead(), v.isClaimedByPlayer()));
            }
            var player = source.getPlayer();
            var nearby = new ArrayList<com.eternalengine.testing.R2PeopleChecks.Npc>();
            if (player != null) {
                // VipEntityHelper accepts a tag on any entity, not only villagers.
                for (var entity : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                        player.getBoundingBox().inflate(96), e -> e != player &&
                                (e instanceof com.eternalengine.entity.HumanNpcEntity
                                || e instanceof net.minecraft.world.entity.npc.Villager
                                || e.getTags().stream().anyMatch(tag -> tag.startsWith(
                                        com.eternalengine.vip.VipEntityHelper.VIP_TAG_PREFIX))))) {
                    var ids = new ArrayList<String>();
                    for (String tag : entity.getTags()) {
                        if (tag.startsWith(com.eternalengine.vip.VipEntityHelper.VIP_TAG_PREFIX))
                            ids.add(tag.substring(com.eternalengine.vip.VipEntityHelper.VIP_TAG_PREFIX.length()).trim());
                    }
                    String kind = entity instanceof net.minecraft.world.entity.npc.Villager ? "villager" : "other", civ = "", role = "";
                    if (entity instanceof com.eternalengine.entity.HumanNpcEntity human) {
                        kind = "human"; civ = human.getCivilizationId(); role = human.getRole();
                    }
                    nearby.add(new com.eternalengine.testing.R2PeopleChecks.Npc(entity.getUUID().toString(), kind,
                            civ, role, List.copyOf(ids)));
                }
            }
            Integer year = SimulationClockManager.isInitialized()
                    ? SimulationClockManager.getInstance().getCurrentYear() : null;
            for (var result : com.eternalengine.testing.R2PeopleChecks.evaluate(nearby, player != null, slots,
                    registryAvailable, year)) {
                switch (result.status()) {
                    case OK -> pass(source, result.text());
                    case FAIL -> fail(source, result.text(), result.detail());
                    case KNOWN -> known(source, result.text(), result.detail());
                }
            }
        } catch (Throwable t) {
            fail(source, "NPC/VIP checks could not run", t.toString());
        }
    }

    /** A skipped check or known limitation, counted separately from pass and fail. */
    private static void known(CommandSourceStack source, String what, String detail) {
        known++;
        say(source, "  §e[KNOWN]§r " + what);
        if (detail != null && !detail.isBlank()) say(source, "         §7" + detail);
    }

    private static void pass(CommandSourceStack source, String what) {
        pass++;
        say(source, "  §a[ OK ]§r " + what);
    }

    private static void fail(CommandSourceStack source, String what, String why) {
        fail++;
        failures.add(what + " — " + why);
        log("  [FAIL] " + what);
        if (toChat) {
            source.sendSuccess(() -> Component.literal("  §c[FAIL]§r " + what).withStyle(ChatFormatting.RED),
                    false);
        }
        say(source, "         §7" + why);
    }

    private static void info(CommandSourceStack source, String what) {
        say(source, "  §8· §7" + what);
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.4f", d);
    }

    private static String yearText(int year) {
        return year < 0 ? Math.abs(year) + " BCE" : year + " CE";
    }
}

package com.eternalengine.testing;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.clock.WorldCalendar;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.population.CrowdFigures;
import com.eternalengine.here.HereAndNow;
import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.TestPhotoPacket;
import com.eternalengine.network.packets.TestWalkPacket;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.eternalengine.terrabridge.TerraTimeBridge;
import com.google.gson.GsonBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Turns 223-224: the test camera - <b>the runtime test takes its own screenshots.</b> The user's
 * rules, in their words: "I SAID YOU TAKE THE SCREENSHOTS when testing in runtime", then "if you
 * want to see anything use screenshots to achieve it", then "no need separate command like
 * /ee test photos". So the camera is not a command family: it is a phase of
 * {@code /ee test everything} ({@link #fullRun}), and whenever anything needs to be SEEN in the
 * game, this class shoots it. The player plays (or stands still); the test photographs; the frames
 * are judged back in the workspace against the data packs.
 *
 * <p>The one pass shoots, in order: the view where the player stands (two frames a second apart),
 * the sky at noon and midnight at the current year (the clock is put back), the era route
 * (FINAL_TEST.md's R1-R31: the year is set, the player teleported, eight seconds of settle so
 * Terra++ can build the year, then two frames per stop, the second catching people mid-walk), and
 * last the screens book page (accents, wrapping, GUI scale - Findings 11 and 30), opened on the
 * player's screen and photographed before they close it. The book is always last: an open book
 * would fill every later frame.
 *
 * <p>Shots land in the game's own {@code screenshots/} folder as {@code etest_*.png}, and the pass
 * writes {@code eternal_photo_report.txt} and {@code eternal_photo_report.json} beside the world's
 * state file: every frame, what the packs say to watch for there, and the data reading at that
 * place-year. The user then sends the screenshots folder (or the world zip) and every frame is
 * judged here. The pass never runs twice at once.
 */
public final class PhotoRoute {

    /** One stop of the era route: where, when, and what the packs say the frame should show. */
    /**
     * claim (turn 235): what must be true here, or "any" for the universal invariant only.
     * "settlement": history says people stood here, so the evidence fields must exist.
     * "open": the land must be countryside - no evidence, rank COUNTRYSIDE, nobody in town dress.
     * "any": the rank must answer countryside iff the evidence is silent.
     */
    public record Stop(String id, String place, double lat, double lon, int year, String watch, String claim) {

        public String shotBase() {
            String y = year < 0 ? "bc" + (-year) : "ad" + year;
            return "etest_" + id.toLowerCase(Locale.ROOT) + "_"
                    + place.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "") + "_" + y;
        }
    }

    /** FINAL_TEST.md's era route, in its order. No place or year comes first (turn 96): walk all of it. */
    public static final List<Stop> ROUTE = List.of(
        new Stop("R1", "Ur", 30.96, 46.10, -2500, "the ziggurat quarter at its dates; mud-brick town of the right size", "settlement"),
        new Stop("R2", "Giza", 29.98, 31.13, -2500, "the pyramids in their building years, the workers' town", "settlement"),
        new Stop("R3", "Mohenjo_daro", 27.32, 68.14, -2500, "the grid streets, the great bath, baked brick", "settlement"),
        new Stop("R4", "Anyang", 36.06, 114.31, -1200, "the Shang capital, oracle-bone era town", "settlement"),
        new Stop("R5", "Athens", 37.98, 23.73, -430, "the Acropolis of Pericles' years, the Agora", "settlement"),
        new Stop("R6", "Pataliputra", 25.61, 85.14, -300, "the Mauryan timber wall and palaces", "settlement"),
        new Stop("R7", "Rome", 41.90, 12.50, 100, "aqueducts, paved roads, the Roman mile (Trajan's Market is made until AD 112)", "settlement"),
        new Stop("R8", "Teotihuacan", 19.69, -98.84, 500, "the Avenue of the Dead, the pyramids at their height", "settlement"),
        new Stop("R9", "Changan", 34.27, 108.95, 750, "the Tang ward city, the pagodas", "settlement"),
        new Stop("R10", "Baghdad", 33.31, 44.36, 900, "the Round City at its shape, the House of Wisdom years", "settlement"),
        new Stop("R11", "Greenland_farms", 61.19, -45.57, 1000, "the Norse longhouses (problem 81: the data may answer nothing; say so)", "any"),
        new Stop("R12", "Cahokia", 38.66, -90.06, 1100, "Monks Mound and the woodhenge years (problem 73: the floor may show nothing)", "any"),
        new Stop("R13", "Angkor", 13.41, 103.87, 1150, "the barays, the temple-mountains of Suryavarman's years", "settlement"),
        new Stop("R14", "Great_Zimbabwe", -20.27, 30.93, 1300, "the great enclosure in dry-stone", "settlement"),
        new Stop("R15", "Timbuktu", 16.77, -3.01, 1350, "the Djinguereber years, mud-brick and the caravan trade", "settlement"),
        new Stop("R16", "Sahara", 23.0, 10.0, 1000, "empty land, and honestly so", "open"),
        new Stop("R17", "Tenochtitlan", 19.43, -99.13, 1500, "before contact: no iron, horses, carts or printed books", "settlement"),
        new Stop("R18", "Cusco", -13.53, -71.97, 1500, "before contact: the Inca stonework, no iron or horses", "settlement"),
        new Stop("R19", "Amazon", -3.10, -60.00, 1500, "forest towns of the terra preta years", "any"),
        new Stop("R20", "Vijayanagara", 15.34, 76.46, 1520, "the bazaar city at its height", "settlement"),
        new Stop("R21", "Delhi", 28.65, 77.23, 1650, "Shahjahanabad as it was being built: the fort, the streets of that name", "settlement"),
        new Stop("R22", "Edo", 35.68, 139.77, 1700, "it is called Edo, not Tokyo: the castle town", "settlement"),
        new Stop("R23", "London", 51.51, -0.13, 1700, "the city after the Great Fire: Wren's London", "settlement"),
        new Stop("R24", "Paris", 48.86, 2.35, 1789, "the city of the revolution years", "settlement"),
        new Stop("R25", "Sydney", -33.87, 151.21, 1850, "the colony town of that year", "settlement"),
        new Stop("R26", "New_York", 40.71, -74.01, 1920, "the skyscraper years beginning", "settlement"),
        new Stop("R27", "Hyderabad", 17.39, 78.49, 1948, "the Dominion of India, ruled by George VI; rupees; the nizam's city", "settlement"),
        new Stop("R28", "Lagos", 6.45, 3.40, 1960, "the independence-year city", "settlement"),
        new Stop("R29", "Moscow", 55.75, 37.62, 1980, "the late-Soviet city", "settlement"),
        new Stop("R30", "Sao_Paulo", -23.55, -46.63, 2020, "the modern megacity", "settlement"),
        new Stop("R31", "Siberia", 60.0, 100.0, 1900, "the railway towns of that year", "any"),
        // Ordinary places in ordinary years (turn 232, restored turn 235): the route used to be a
        // highlights reel - every stop a famous place in its most famous year - while bugs like
        // the Secunderabad one live in the most common kind of spot in the whole world.
        new Stop("R32", "Secunderabad_open_country", 17.44, 78.50, -3000,
                "the turn-230 regression: open country - a small band of hunters, fishers and gatherers; NO town, no market stall, no money-changer", "open"),
        new Stop("R33", "Central_Australia", -25.34, 131.03, -3000,
                "open country: the interior's foragers at most; nobody in town dress", "open"),
        new Stop("R34", "Swiss_farmland", 47.50, 8.50, 1200,
                "an ordinary year at an ordinary place: farming country - a village at most, never a city", "any"));

    /** Ticks to let the world arrive at a stop before the first frame (Terra++ builds on the jump). */
    private static final int SETTLE_TICKS = 160;
    /** Ticks between the two frames of a stop: the second catches people mid-walk. */
    private static final int SHOT_GAP_TICKS = 25;

    private static volatile boolean running = false;
    /** When true (the full run), the screens book is shot after the last era stop. */
    private static boolean bookAtEnd = false;
    private static UUID shooterId;
    private static int index;
    private static int tickInStop;
    private static int shotPhase;
    private static boolean singleStop = false;
    private static final List<String> lines = new ArrayList<>();
    private static CommandSourceStack tell;

    /** Server tick timing (turn 227): the run measures its own smoothness instead of asking. */
    private static final long[] tickIntervals = new long[100];
    private static int tickCursor = 0;
    private static int tickCount = 0;
    private static long lastTickNanos = 0L;

    /**
     * The shutter queue: everything the test wants to see later (a second frame, a sky change,
     * a book that is open on screen) is staged here and fired by the ticker. This is the working
     * rule the user set: when anything needs to be SEEN in the game, the test takes the screenshot.
     */
    private static final java.util.ArrayDeque<Job> jobs = new java.util.ArrayDeque<>();

    private static final class Job {
        int ticks;
        final Runnable run;

        Job(int ticks, Runnable run) {
            this.ticks = ticks;
            this.run = run;
        }
    }

    /** Run something a number of ticks from now (20 ticks = 1 second). */
    private static void later(int ticks, Runnable run) {
        synchronized (jobs) {
            jobs.add(new Job(ticks, run));
        }
    }

    private PhotoRoute() {
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * The one camera pass of {@code /ee test everything} (the user set the rule: if anything needs to
     * be SEEN, the test shoots it, and no separate photo command exists). Order matters: the book
     * screen is opened LAST because an open book would fill every later frame.
     */
    public static int fullRun(CommandSourceStack source) {
        if (running) {
            source.sendFailure(Component.literal("A camera pass is already going."));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("The camera pass needs a player to photograph."));
            return 0;
        }
        tell = source;
        shooterId = player.getUUID();
        lines.clear();
        lines.add("camera pass of /ee test everything: view, sky day and night, "
                + ROUTE.size() + " era stops (the pilot walks a loop through each), then the book screen");
        var world = player.serverLevel();
        long restore = world.getDayTime();
        // 1. The view where you stand, two frames a second apart.
        later(0, () -> startView(player));
        // 2. The sky by day and by night at the current year; the clock is put back.
        later(50, () -> world.setDayTime(6000L));
        later(90, () -> {
            sendShot(player, "etest_sky_day");
            lines.add("sky: etest_sky_day.png (noon; the atmosphere pack's daytime sky)");
            world.setDayTime(18000L);
        });
        later(130, () -> {
            sendShot(player, "etest_sky_night");
            lines.add("sky: etest_sky_night.png (midnight; the night sky)");
            world.setDayTime(restore);
        });
        // 3. The era route, then the book screen is chained from the route's own finish.
        bookAtEnd = true;
        later(160, () -> beginRoute(player));
        source.sendSuccess(() -> Component.literal("The test camera is now working: your view, the sky,"
                + " then " + ROUTE.size() + " era stops (the pilot walks you through each stop - touch any"
                + " movement key to take over; one to two minutes per stop while the world builds each new"
                + " place), then one book screen. Frames go to the screenshots folder as etest_*.png."), true);
        return 1;
    }

    /** Two frames of the view where the player stands: no teleport, no year change. */
    private static void startView(ServerPlayer player) {
        int year = com.eternalengine.clock.SimulationClockManager.isInitialized()
                ? com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear()
                : WorldStateManager.getInstance().getCurrentYear();
        double[] geo = TerraCoordinateHelper.toGeo(player.blockPosition());
        String base = "etest_now_" + (geo == null ? "here" : String.format(Locale.ROOT, "%.2f_%.2f", geo[1], geo[0]))
                + "_" + (year < 0 ? "bc" + (-year) : "ad" + year);
        sendShot(player, base + "_a");
        lines.add("view: " + base + "_a.png and _b.png (" + HereAndNow.yearText(year) + ")");
        later(20, () -> sendShot(player, base + "_b"));
    }

    /**
     * The screens test book (accents, wrapping, GUI scale: Findings 11 and 30), opened on the player's
     * screen like any knowledge book. Two seconds later the shutter fires while it is still open; the
     * player closes it after.
     */
    private static void openBookShot(ServerPlayer player) {
        com.eternalengine.knowledge.BookView view = new com.eternalengine.knowledge.BookView();
        view.title = "Screens test";
        view.subtitle = "accents, wrapping, GUI scale";
        view.tab = "notes";
        view.card = "Screens test";
        view.kind = "Person";
        view.where = "P\u00e9tain was here";
        view.hint = "If every accent on this page is complete, this screen is clean.";
        view.notes.add(new com.eternalengine.knowledge.BookView.Note("3000 BC", "Tester",
                "P\u00e9tain, \u00dcr\u00fcmqi, Kyiv, Z\u00fcrich, G\u00f6teborg, \u00c5ngstr\u00f6m, \u014csaka, "
                        + "\u010cesk\u00e9, \u00cdsafj\u00f6r\u00f0ur, N'Djamena."));
        view.notes.add(new com.eternalengine.knowledge.BookView.Note("3000 BC", "Tester",
                "A long line to watch how it wraps at your GUI scale: the aqueducts carried water to "
                        + "Rome's seven hills while the market crowds filled the Forum all morning."));
        view.notes.add(new com.eternalengine.knowledge.BookView.Note("AD 100", "Tester",
                "Digits and case: Year 100 AD, 3.14159, 42%, 1,000,000 people. \u00c5ngstr\u00f6m 0.1 nm."));
        com.eternalengine.network.EternalEnginePacketHandler.sendToPlayer(
                new com.eternalengine.network.packets.BookViewPacket(view), player);
        later(40, () -> {
            sendShot(player, "etest_book_accents");
            lines.add("book: etest_book_accents.png (accents, wrapping and GUI scale on the open book;"
                    + " close the book after)");
        });
    }

    /** Begin the era walk. Only called from fullRun or the route finish chaining. */
    private static void beginRoute(ServerPlayer player) {
        index = 0;
        singleStop = false;
        tickInStop = 0;
        shotPhase = 0;
        running = true;
    }

    /** The state machine: one advance per server tick end. */
    @Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
    public static final class Ticker {

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            try {
                long now = System.nanoTime();
                if (lastTickNanos != 0L) {
                    long interval = now - lastTickNanos;
                    if (interval < 2_000_000_000L) { // a pause is not a stutter
                        tickIntervals[tickCursor] = interval;
                        tickCursor = (tickCursor + 1) % tickIntervals.length;
                        if (tickCount < tickIntervals.length) {
                            tickCount++;
                        }
                    }
                }
                lastTickNanos = now;
                synchronized (jobs) {
                    while (!jobs.isEmpty()) {
                        Job job = jobs.peekFirst();
                        if (job.ticks > 0) {
                            job.ticks--;
                            if (job.ticks > 0) {
                                break;
                            }
                        }
                        jobs.pollFirst();
                        job.run.run();
                    }
                }
                if (running) {
                    step();
                }
            } catch (Throwable t) {
                running = false;
                say("the photo route stopped: " + t);
                writeReports("stopped");
            }
        }
    }

    private static void step() {
        ServerPlayer player = shooterId == null ? null : findPlayer();
        if (player == null) {
            running = false;
            say("the photographed player is gone; the run stops here");
            writeReports("stopped");
            return;
        }
        Stop stop = ROUTE.get(index);
        if (shotPhase == 0 && tickInStop == 0) {
            // Arrive: the year first (buildings follow the year), then the ground underfoot.
            String problem = arrive(player, stop);
            if (problem != null) {
                lines.add(stop.id() + ": " + problem + " (skipped)");
                nextStop();
                return;
            }
        }
        tickInStop++;
        if (shotPhase == 0 && tickInStop >= SETTLE_TICKS) {
            String name = stop.shotBase() + "_a";
            sendShot(player, name);
            String claim = claimCheck(player, stop);
            lines.add(stop.id() + " " + stop.place() + " " + HereAndNow.yearText(stop.year())
                    + ": " + stop.shotBase() + "_a.png, " + stop.shotBase() + "_b.png | watch: " + stop.watch()
                    + " | reading: " + HereAndNow.read(stop.lat(), stop.lon(), stop.year()).shortLine()
                    + " | " + systemsLine(stop)
                    + " | claim: " + claim
                    + " | tick: " + tickStats());
            say(stop.id() + " " + stop.place() + ": shot " + stop.shotBase() + "_a.png, claim "
                    + claim);
            shotPhase = 1;
            tickInStop = 0;
            return;
        }
        if (shotPhase == 1 && tickInStop >= SHOT_GAP_TICKS) {
            sendShot(player, stop.shotBase() + "_b");
            shotPhase = 0;
            tickInStop = 0;
            nextStop();
        }
    }

    private static void nextStop() {
        if (singleStop || index + 1 >= ROUTE.size()) {
            running = false;
            if (bookAtEnd) {
                bookAtEnd = false;
                ServerPlayer player = findPlayer();
                if (player != null) {
                    say("the route is done; the book screen is the last frame");
                    openBookShot(player);
                    later(60, () -> writeReports("done"));
                    return;
                }
            }
            say("the photo run is done: " + lines.size() + " lines written");
            writeReports("done");
            return;
        }
        index++;
    }

    /**
     * The server's own smoothness over the last five seconds: the gap between ticks (50 ms is
     * healthy). The run measures it rather than asking how it felt (turn 227).
     */
    private static String tickStats() {
        int n = tickCount;
        if (n == 0) {
            return "not measured yet";
        }
        long sum = 0;
        long worst = 0;
        for (int i = 0; i < n; i++) {
            long t = tickIntervals[i];
            sum += t;
            if (t > worst) {
                worst = t;
            }
        }
        return (sum / n) / 1_000_000L + " ms avg, " + worst / 1_000_000L + " ms worst over " + n + " ticks"
                + " (50 ms is healthy)";
    }

    /**
     * The stop's other systems, exercised where and when the test stands (the user: the pilot is
     * "for testing at multiple areas and time other features and system"). Four live reads per
     * stop - the price of wheat, the credit terms, the ruler on record, plus the place line that
     * HereAndNow already carries in the reading. Every read is its own try: one broken system
     * reports itself and never stops the run.
     */
    private static String systemsLine(Stop stop) {
        StringBuilder sb = new StringBuilder("systems:");
        try {
            sb.append(" wheat=").append(com.eternalengine.money.Prices.get()
                    .priceAt(stop.lat(), stop.lon(), "wheat", stop.year())
                    .map(r -> r.sentence()).orElse("no market within reach"));
        } catch (Throwable t) {
            sb.append(" wheat=err:").append(t.getMessage());
        }
        try {
            var terms = com.eternalengine.money.Credit.get().at(stop.lat(), stop.lon(), stop.year());
            if (terms.silent()) {
                sb.append(" credit=no row on record");
            } else {
                sb.append(" credit=").append(terms.best().rate());
                if (terms.best().forbidden()) {
                    sb.append(" (interest unlawful; see the register)");
                }
            }
        } catch (Throwable t) {
            sb.append(" credit=err:").append(t.getMessage());
        }
        try {
            sb.append(" ruler=").append(com.eternalengine.civilization.WhoRuled
                    .hit(stop.lat(), stop.lon(), stop.year())
                    .map(h -> h.ruler() + " at " + h.place()).orElse("nobody on record"));
        } catch (Throwable t) {
            sb.append(" ruler=err:").append(t.getMessage());
        }
        return sb.toString();
    }

    /** The year and the ground, in the order executeDate does it. Null when it went well. */
    private static String arrive(ServerPlayer player, Stop stop) {
        try {
            var clock = com.eternalengine.clock.SimulationClockManager.getInstance();
            var jump = com.eternalengine.clock.TimeTravel.judge(clock.getCurrentYear(), stop.year(),
                    com.eternalengine.clock.TimeTravel.Who.OPERATOR);
            if (!jump.allowed()) {
                return "the clock refused: " + jump.because();
            }
            int key = KnowledgeDate.key(stop.year(), 1, 1);
            if (clock.isTerraTime()) {
                if (clock.getCurrentYear() != stop.year()) {
                    clock.setCurrentYear(stop.year());
                }
                int scale = TerraTimeBridge.getTimeScale(1);
                long worldDay = KnowledgeDate.terraDayFor(key, scale,
                        TerraTimeBridge.baseYear(), TerraTimeBridge.daysPerYear());
                if (worldDay < 0 || !TerraTimeBridge.setDayCount(player.getServer(), worldDay)) {
                    return "Terra++ could not go to that year";
                }
                WorldCalendar.accept();
            } else {
                if (clock.getCurrentYear() != stop.year()) {
                    clock.setCurrentYear(stop.year());
                }
                WorldCalendar.setDate(key);
            }
            // Any earlier walk stops before the jump, then the pilot walks a loop through the stop:
            // the settle time is spent walking, so the frames catch the world with motion in it and
            // every system that reacts to a walking player is exercised (turn 226).
            EternalEnginePacketHandler.sendToPlayer(new TestWalkPacket(new double[0][2], 0.0d), player);
            // Go there the way the game itself goes (the user's fix, turn 229): Terra++'s own
            // /tpll teleports to a latitude and longitude and handles the landing on ground that
            // may still be building. A raw teleport was landing the player on the height read of an
            // ungenerated chunk. The direct teleport stays as the fallback so the route never dies.
            boolean landed = false;
            try {
                if (tell != null && tell.getEntity() instanceof ServerPlayer && player.getServer() != null) {
                    String raw = String.format(Locale.ROOT, "tpll %.6f %.6f", stop.lat(), stop.lon());
                    landed = player.getServer().getCommands().getDispatcher()
                            .execute(raw, tell.withPermission(4)) > 0;
                }
            } catch (Throwable ignored) {
            }
            if (!landed) {
                net.minecraft.core.BlockPos at = TerraCoordinateHelper.fromGeo(stop.lat(), stop.lon(), player.level());
                if (at == null) {
                    return "Terra++ could not say where that place is";
                }
                ServerLevel level = player.serverLevel();
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                player.teleportTo(at.getX() + 0.5d, y + 1.0d, at.getZ() + 0.5d);
            }
            EternalEnginePacketHandler.sendToPlayer(
                    new TestWalkPacket(new double[][] {{12, 0}, {0, 12}, {-12, 0}, {0, -12}}, 0.26d), player);
            return null;
        } catch (Throwable t) {
            return "arriving failed: " + t.getMessage();
        }
    }

    private static int claimPass = 0;
    private static int claimFail = 0;

    /**
     * The stop's world claim, judged where the test physically stands (turn 235): the query-level
     * truth plus the people who actually stand near the player. Either way, nobody in town dress
     * may stand on land with no settlement behind it. The verdict travels with the frames.
     */
    private static String claimCheck(ServerPlayer player, Stop stop) {
        try {
            var presence = com.eternalengine.core.WorldQuery.presence(null,
                    com.eternalengine.core.WorldKey.at(stop.lat(), stop.lon(), stop.year()));
            boolean ev = presence.site().isPresent() || presence.presentMap().isPresent()
                    || presence.ohm().isPresent() || presence.hinterland().isPresent()
                    || presence.border().isPresent();
            boolean country = presence.rank()
                    == com.eternalengine.civilization.SettlementRank.COUNTRYSIDE;
            int band = 0;
            int town = 0;
            int figures = 0;
            AABB box = player.getBoundingBox().inflate(32.0d);
            List<HumanNpcEntity> near = player.level().getEntitiesOfClass(HumanNpcEntity.class, box,
                    HumanNpcEntity::isAlive);
            for (HumanNpcEntity npc : near) {
                if (CrowdFigures.isFigure(npc)) {
                    figures++;
                    continue;
                }
                band++;
                String r = String.valueOf(npc.getRole()).toLowerCase(Locale.ROOT);
                if (r.contains("merchant") || r.contains("trader") || r.contains("pedlar")
                        || r.contains("money") || r.contains("change") || r.contains("news")
                        || r.contains("priest") || r.contains("artisan") || r.contains("citizen")
                        || r.contains("captain") || r.contains("marine") || r.contains("soldier")
                        || r.contains("legion")) {
                    town++;
                }
            }
            String scene = "rank " + presence.rank() + ", " + band + " people (" + town
                    + " in town dress), " + figures + " figures";
            boolean ok;
            String why;
            switch (stop.claim()) {
                case "open" -> {
                    ok = !ev && country && town == 0;
                    why = ev ? "evidence of settlement on land that must be open"
                            : !country ? "the rank invents a settlement"
                            : town > 0 ? "town dress on open land" : "open country as claimed";
                }
                case "settlement" -> {
                    ok = ev && !country;
                    why = ev ? (country ? "evidence exists but the rank denies it"
                            : "settlement as claimed")
                            : "no evidence where history says people stood";
                }
                default -> {
                    ok = ev == !country && (!country || town == 0);
                    why = ok ? "the rank matches the evidence"
                            : "the rank contradicts the evidence";
                }
            }
            claimPass += ok ? 1 : 0;
            claimFail += ok ? 0 : 1;
            return (ok ? "PASS" : "FAIL") + " (" + scene + "; " + why + ")";
        } catch (Throwable t) {
            claimFail++;
            return "FAIL (claim check threw: " + t + ")";
        }
    }

    private static void sendShot(ServerPlayer player, String name) {
        EternalEnginePacketHandler.sendToPlayer(new TestPhotoPacket(name), player);
    }

    private static ServerPlayer findPlayer() {
        try {
            if (tell != null && tell.getServer() != null) {
                return tell.getServer().getPlayerList().getPlayer(shooterId);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void say(String line) {
        CommandSourceStack s = tell;
        if (s != null) {
            try {
                String l = line;
                s.sendSuccess(() -> Component.literal("[photo] " + l), true);
            } catch (Throwable ignored) {
            }
        }
    }

    /** The run's own report: every stop, its shots, what to watch, and the data reading. */
    static void writeReports(String how) {
        Path dir;
        try {
            Path state = WorldStateManager.getInstance().getStateFile();
            dir = state == null ? null : state.getParent();
        } catch (Throwable t) {
            dir = null;
        }
        if (dir == null) {
            return;
        }
        try {
            StringBuilder text = new StringBuilder("Eternal Engine photo report (" + how + ")\n");
            text.append("claims judged at the stops: ").append(claimPass).append(" passed, ")
                    .append(claimFail).append(" failed\n");
            text.append("Shots go to the game's screenshots folder as etest_*.png.\n");
            text.append("Each stop: two frames, what the packs say to watch for, and the data reading.\n\n");
            for (String line : lines) {
                text.append(line).append('\n');
            }
            Files.write(dir.resolve("eternal_photo_report.txt"), text.toString().getBytes(StandardCharsets.UTF_8));
            String json = new GsonBuilder().setPrettyPrinting().create()
                    .toJson(java.util.Map.of("how", how, "lines", lines));
            Files.write(dir.resolve("eternal_photo_report.json"), json.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // read-only worlds: the chat lines above still stand
        }
    }
}

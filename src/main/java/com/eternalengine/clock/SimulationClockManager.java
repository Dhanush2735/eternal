package com.eternalengine.clock;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.terrabridge.TerraTimeBridge;
import com.eternalengine.network.packets.SimulationStatePacket;
import com.eternalengine.network.packets.SpeedChangePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SimulationClockManager - Controls simulation time and speed.
 * 
 * Speed (step 18d): days of history per Minecraft day, the same number with both clocks (Terra++ time calls it
 * its scale). 1x = one day per Minecraft day (the default for new worlds), 5x, 10x, 25x, 50x. Before step 18d the
 * Eternal Engine clock counted years per Minecraft day (10x was a year every 2 real minutes), so its date could
 * not follow the Minecraft days. The date itself is {@link WorldCalendar}'s: it follows the Minecraft day count,
 * and only /ee date moves it back.
 * 
 * 1 MC day = 20 real minutes = 24000 ticks
 * 
 * Speed Priority (highest to lowest):
 * 1. Unoverridable VIP lock
 * 2. Player override active
 * 3. Auto slowdown from events
 * 4. Overridable VIP lock
 * 5. Base speed setting
 */
public class SimulationClockManager {
    
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    
    private static SimulationClockManager instance;
    private static boolean initialized = false;
    
    // Ticks per MC day
    public static final int TICKS_PER_DAY = 24000;
    
    // Valid speed values
    public static final int[] VALID_SPEEDS = {1, 5, 10, 25, 50};
    
    // Current state
    private final AtomicInteger currentYear = new AtomicInteger(-3000);
    private final AtomicInteger baseSpeed = new AtomicInteger(10);
    private final AtomicInteger effectiveSpeed = new AtomicInteger(10);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    
    // Tick tracking for year progression
    private final AtomicLong ticksInCurrentYear = new AtomicLong(0);
    
    // Auto slowdown
    private final Map<String, SlowdownEntry> activeSlowdowns = new ConcurrentHashMap<>();
    
    // VIP locks
    private final Map<String, VipLockEntry> vipLocks = new ConcurrentHashMap<>();
    
    // Player override
    private final AtomicBoolean overrideActive = new AtomicBoolean(false);
    private final AtomicInteger overrideSpeed = new AtomicInteger(0);
    private final AtomicLong overrideExpireTime = new AtomicLong(0);
    private final Map<UUID, Integer> speedVotes = new ConcurrentHashMap<>();
    
    // Auto slowdown event definitions (step 14: by year range). The years of each event are a judgement of
    // when it was at its height; the speed is the one the original table gave it. A slowdown is on while
    // the world's year is inside its range, on the Eternal Engine clock only: in Terra++ time the year is
    // the Minecraft day count times the Terra++ scale, and changing that scale would move the year itself.
    private static final Map<String, SlowdownConfig> EVENT_SLOWDOWNS = new LinkedHashMap<>();
    
    static {
        // Event -> (name, maxSpeed, first year, last year)
        EVENT_SLOWDOWNS.put("FALL_OF_ROME", new SlowdownConfig("the fall of the Western Roman Empire", 5, 455, 476));
        EVENT_SLOWDOWNS.put("MONGOL_INVASION", new SlowdownConfig("the Mongol conquests of Genghis Khan", 5, 1206, 1227));
        EVENT_SLOWDOWNS.put("BLACK_DEATH", new SlowdownConfig("the Black Death", 5, 1346, 1353));
        EVENT_SLOWDOWNS.put("RENAISSANCE", new SlowdownConfig("the Renaissance", 10, 1400, 1600));
        EVENT_SLOWDOWNS.put("INDUSTRIAL_REVOLUTION", new SlowdownConfig("the Industrial Revolution", 10, 1760, 1840));
        EVENT_SLOWDOWNS.put("FRENCH_REVOLUTION", new SlowdownConfig("the French Revolution", 5, 1789, 1799));
        EVENT_SLOWDOWNS.put("NAPOLEONIC_WARS", new SlowdownConfig("the Napoleonic Wars", 5, 1803, 1815));
        EVENT_SLOWDOWNS.put("WW1", new SlowdownConfig("the First World War", 5, 1914, 1918));
        EVENT_SLOWDOWNS.put("RUSSIAN_REVOLUTION", new SlowdownConfig("the Russian Revolution and Civil War", 5, 1917, 1922));
        EVENT_SLOWDOWNS.put("WW2", new SlowdownConfig("the Second World War", 5, 1939, 1945));
        EVENT_SLOWDOWNS.put("HOLOCAUST", new SlowdownConfig("the Holocaust", 2, 1941, 1945));
        EVENT_SLOWDOWNS.put("COLD_WAR", new SlowdownConfig("the Cold War", 5, 1947, 1991));
        EVENT_SLOWDOWNS.put("CUBAN_MISSILE_CRISIS", new SlowdownConfig("the Cuban Missile Crisis", 1, 1962, 1962));
    }

    /** Something that wants to know when the world's year changes (step 14). Runs on the server thread. */
    @FunctionalInterface
    public interface YearListener {
        void yearChanged(int oldYear, int newYear);
    }

    private final List<YearListener> yearListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** The year the listeners were last told about (Integer.MIN_VALUE: not yet). */
    private volatile int announcedYear = Integer.MIN_VALUE;
    
    private SimulationClockManager() {}
    
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[Clock] Already initialized");
            return;
        }
        
        instance = new SimulationClockManager();
        initialized = true;
        
        LOGGER.info("[Clock] Initialized");
    }
    
    public static SimulationClockManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("SimulationClockManager not initialized!");
        }
        return instance;
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Start the clock
     */
    public void start() {
        // Terra++ historical time is the source of truth when available.
        WorldStateManager wsm = WorldStateManager.getInstance();
        currentYear.set(TerraTimeBridge.getCurrentYear(wsm.getCurrentYear()));
        baseSpeed.set(TerraTimeBridge.getTimeScale(wsm.getSimulationSpeed()));
        
        // Where the clock was inside the year when the world was saved (the Eternal Engine clock; Terra++
        // time keeps its own position in the Minecraft day count).
        ticksInCurrentYear.set(Math.max(0L, wsm.getClockTicksInYear()));
        activeSlowdowns.clear();
        speedVotes.clear();

        // Step 18d: the one calendar. The Eternal Engine clock's date is a saved point plus the Minecraft days since
        // (a world from before step 18d gets it from how far its year had run); the year is the calendar's.
        WorldCalendar.start(wsm.getSavedCalendar(), currentYear.get(), ticksInCurrentYear.get(), baseSpeed.get(),
            baseSpeed.get());
        if (!TerraTimeBridge.isAvailable()) {
            int today = WorldCalendar.today();
            if (today != KnowledgeDate.ALWAYS) {
                currentYear.set(KnowledgeDate.year(today));
                wsm.setCurrentYear(currentYear.get());
            }
        }
        announcedYear = currentYear.get();

        running.set(true);
        paused.set(false);
        
        // Step 18e: start from the base speed, so the log only says "Speed changed" when a slowdown or lock really
        // changes it (it said "10x -> 1x" at every start: 10 was this field's first value, not the world's).
        effectiveSpeed.set(baseSpeed.get());
        updateYearSlowdowns(currentYear.get(), false);
        calculateEffectiveSpeed();
        if (!TerraTimeBridge.isAvailable()) {
            WorldCalendar.setRate(effectiveSpeed.get());
        }
        
        LOGGER.info("[Clock] Started at year {}, speed {}x. {}", currentYear.get(), effectiveSpeed.get(), describeMode());
    }
    
    /**
     * Stop the clock
     */
    public void stop() {
        running.set(false);
        
        // Save state
        WorldStateManager.getInstance().setCurrentYear(currentYear.get());
        WorldStateManager.getInstance().setSimulationSpeed(baseSpeed.get());
        WorldCalendar.stop();
        
        LOGGER.info("[Clock] Stopped at year {}", currentYear.get());
    }
    
    /**
     * Pause the clock
     */
    public void pause() {
        paused.set(true);
    }
    
    /**
     * Resume the clock
     */
    public void resume() {
        paused.set(false);
    }
    
    /**
     * Process a tick - called every server tick
     */
    public void tick() {
        if (!running.get()) {
            return;
        }
        // Step 18d: the year is the calendar's, with either clock, also while paused: the date follows the
        // Minecraft days, and a /time add moves it even then. Year listeners hear of it below, at most once a tick.
        syncYearFromCalendar();
        announceYearChange();
        if (paused.get()) {
            return;
        }
        
        // Check if override has expired
        if (overrideActive.get() && System.currentTimeMillis() > overrideExpireTime.get()) {
            overrideActive.set(false);
            overrideSpeed.set(0);
            speedVotes.clear();
            calculateEffectiveSpeed();
            broadcastSpeedChange("Override expired");
        }
        
        if (TerraTimeBridge.isAvailable()) {
            int terraScale = TerraTimeBridge.getTimeScale(baseSpeed.get());
            if (terraScale != baseSpeed.get()) {
                baseSpeed.set(terraScale);
                WorldStateManager.getInstance().setSimulationSpeed(terraScale);
                calculateEffectiveSpeed();
            }
        }
    }

    /** The year of the calendar's date (Terra++ time: the Terra++ year, which is made of the same day count). */
    private void syncYearFromCalendar() {
        int year;
        if (TerraTimeBridge.isAvailable()) {
            year = TerraTimeBridge.getCurrentYear(currentYear.get());
        } else {
            int today = WorldCalendar.today();
            if (today == KnowledgeDate.ALWAYS) {
                return;
            }
            year = KnowledgeDate.year(today);
        }
        if (year != currentYear.get()) {
            currentYear.set(year);
            WorldStateManager.getInstance().setCurrentYear(year);
            LOGGER.debug("[Clock] The year is now {}", year);
            checkSlowdownExpiration();
        }
    }
    
    /**
     * Set base speed (admin only)
     */
    public boolean setBaseSpeed(int speed) {
        return applyBaseSpeed(speed, "Admin set base speed");
    }

    /**
     * Set the base speed. In Terra++ time this is the Terra++ time scale, and the world's day count is moved
     * so the date stays where it is (step 18d; step 14 kept only the year): the year is day count times scale,
     * so a new scale alone made it jump (10x to 25x in 1948 gave a year past 9000).
     *
     * @return false when the speed is not one of {@link #VALID_SPEEDS}
     */
    private boolean applyBaseSpeed(int speed, String reason) {
        if (!isValidSpeed(speed)) {
            LOGGER.warn("[Clock] Invalid speed: {}", speed);
            return false;
        }
        
        int actualSpeed = speed;
        if (TerraTimeBridge.isAvailable()) {
            MinecraftServer server = SimulationEngine.isInitialized() ? SimulationEngine.getInstance().getServer() : null;
            actualSpeed = TerraTimeBridge.setTimeScaleKeepingDate(server, speed, baseSpeed.get());
            currentYear.set(TerraTimeBridge.getCurrentYear(currentYear.get()));
            WorldCalendar.accept();
        }
        baseSpeed.set(actualSpeed);
        WorldStateManager.getInstance().setSimulationSpeed(actualSpeed);
        calculateEffectiveSpeed();
        broadcastSpeedChange(reason);
        return true;
    }
    
    /**
     * Register an event slowdown
     */
    public void registerSlowdown(String eventId, int maxSpeed, long durationMs) {
        SlowdownEntry entry = new SlowdownEntry(eventId, maxSpeed, 
            System.currentTimeMillis() + durationMs, false);
        activeSlowdowns.put(eventId, entry);
        
        calculateEffectiveSpeed();
        broadcastSpeedChange("Event: " + eventId);
        
        LOGGER.info("[Clock] Slowdown registered for {} (max {}x, {}ms)", 
            eventId, maxSpeed, durationMs);
    }
    
    /**
     * Register a VIP lock
     */
    public void registerVipLock(String lockId, int maxSpeed, boolean overridable) {
        VipLockEntry entry = new VipLockEntry(lockId, maxSpeed, overridable);
        vipLocks.put(lockId, entry);
        
        calculateEffectiveSpeed();
        broadcastSpeedChange("VIP: " + lockId);
        
        LOGGER.info("[Clock] VIP lock registered: {} (max {}x, overridable: {})",
            lockId, maxSpeed, overridable);
    }
    
    /**
     * Remove a VIP lock
     */
    public void removeVipLock(String lockId) {
        vipLocks.remove(lockId);
        calculateEffectiveSpeed();
        broadcastSpeedChange("VIP lock removed: " + lockId);
    }
    
    /**
     * Process a speed vote from a VIP player
     */
    public VoteOutcome processVote(ServerPlayer player, int votedSpeed) {
        if (player == null) {
            return VoteOutcome.refused("no player");
        }
        if (!isValidSpeed(votedSpeed)) {
            return VoteOutcome.refused("speed must be one of " + Arrays.toString(VALID_SPEEDS));
        }
        if (!isVip(player)) {
            return VoteOutcome.refused("only VIP players can vote on speed");
        }
        
        speedVotes.put(player.getUUID(), votedSpeed);

        // Step 14: the votes are counted. The vote "resolution by the VIP layer" never existed, so a
        // vote was stored and nothing ever happened. A speed wins when more than half of the VIP players
        // who are online voted for it; votes of players who left or are no longer VIP do not count.
        MinecraftServer server = player.getServer();
        Set<UUID> eligible = onlineVips(server);
        eligible.add(player.getUUID());
        VoteOutcome outcome = tally(speedVotes, eligible);
        speedVotes.keySet().retainAll(eligible);
        if (outcome.winner() != null) {
            int speed = outcome.winner();
            speedVotes.clear();
            Runnable apply = () -> {
                if (applyBaseSpeed(speed, "VIP vote") && server != null) {
                    server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "§6The VIP players voted: history now runs at " + speed + "x.§r"), false);
                }
            };
            if (server != null && !server.isSameThread()) {
                server.execute(apply); // a vote packet can arrive on the network thread
            } else {
                apply.run();
            }
        }
        return outcome;
    }

    /**
     * Count the votes of the eligible players. A speed wins with more than half of {@code eligible}.
     * Pure, for tests.
     */
    public static VoteOutcome tally(Map<UUID, Integer> votes, Set<UUID> eligible) {
        Map<Integer, Integer> counts = new TreeMap<>();
        for (Map.Entry<UUID, Integer> v : votes.entrySet()) {
            if (eligible.contains(v.getKey()) && v.getValue() != null) {
                counts.merge(v.getValue(), 1, Integer::sum);
            }
        }
        int needed = eligible.size() / 2 + 1;
        Integer winner = null;
        for (Map.Entry<Integer, Integer> c : counts.entrySet()) {
            if (c.getValue() >= needed) {
                winner = c.getKey();
            }
        }
        return new VoteOutcome(true, null, winner, counts, eligible.size(), needed);
    }

    /** The result of one vote: refused (with why), or the count so far and the winner if there is one. */
    public record VoteOutcome(boolean accepted, String problem, Integer winner, Map<Integer, Integer> tally,
                              int eligible, int needed) {
        static VoteOutcome refused(String why) {
            return new VoteOutcome(false, why, null, Map.of(), 0, 0);
        }
    }

    private static boolean isVip(ServerPlayer player) {
        try {
            return com.eternalengine.capability.EternalEngineCapabilities.getPlayerData(player).isVip();
        } catch (Throwable t) {
            return false;
        }
    }

    private static Set<UUID> onlineVips(MinecraftServer server) {
        Set<UUID> out = new HashSet<>();
        if (server == null) {
            return out;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isVip(p)) {
                out.add(p.getUUID());
            }
        }
        return out;
    }
    
    /**
     * Activate player override
     */
    public void activateOverride(int speed, long durationMs) {
        if (!isValidSpeed(speed)) {
            return;
        }
        
        // Check for unoverridable VIP locks
        for (VipLockEntry lock : vipLocks.values()) {
            if (!lock.overridable) {
                LOGGER.info("[Clock] Cannot override - unoverridable VIP lock active");
                return;
            }
        }
        
        overrideActive.set(true);
        overrideSpeed.set(speed);
        overrideExpireTime.set(System.currentTimeMillis() + durationMs);
        
        calculateEffectiveSpeed();
        broadcastSpeedChange("Player override");
    }
    
    /**
     * Calculate effective speed based on priority
     */
    private void calculateEffectiveSpeed() {
        int speed = baseSpeed.get();
        String reason = "Base speed";
        
        // Priority 5: Base speed (already set)
        
        // Priority 4: Overridable VIP locks
        for (VipLockEntry lock : vipLocks.values()) {
            if (lock.overridable && lock.maxSpeed < speed) {
                speed = lock.maxSpeed;
                reason = "VIP lock: " + lock.lockId;
            }
        }
        
        // Priority 3: Auto slowdowns
        for (SlowdownEntry entry : activeSlowdowns.values()) {
            if (entry.maxSpeed < speed) {
                speed = entry.maxSpeed;
                reason = "Event: " + entry.eventId;
            }
        }
        
        // Priority 2: Player override
        if (overrideActive.get()) {
            speed = overrideSpeed.get();
            reason = "Player override";
        }
        
        // Priority 1: Unoverridable VIP locks
        for (VipLockEntry lock : vipLocks.values()) {
            if (!lock.overridable && lock.maxSpeed < speed) {
                speed = lock.maxSpeed;
                reason = "VIP lock (unoverridable): " + lock.lockId;
            }
        }
        
        int oldSpeed = effectiveSpeed.getAndSet(speed);
        if (oldSpeed != speed) {
            // Step 18d: the same date goes on at the new speed (the Eternal Engine clock's calendar moves its
            // starting point; Terra++ time keeps its own scale, see applyBaseSpeed).
            if (!TerraTimeBridge.isAvailable()) {
                WorldCalendar.setRate(speed);
            }
            LOGGER.info("[Clock] Speed changed: {}x -> {}x ({})", oldSpeed, speed, reason);
        }
    }
    
    /**
     * Check for expired slowdowns
     */
    private void checkSlowdownExpiration() {
        long now = System.currentTimeMillis();
        
        activeSlowdowns.entrySet().removeIf(entry -> {
            if (entry.getValue().expireTime < now) {
                LOGGER.info("[Clock] Slowdown expired: {}", entry.getKey());
                return true;
            }
            return false;
        });
        
        calculateEffectiveSpeed();
    }
    
    /**
     * Broadcast speed change to all players
     */
    private void broadcastSpeedChange(String reason) {
        SpeedChangePacket packet = new SpeedChangePacket(
            effectiveSpeed.get(),
            baseSpeed.get(),
            reason,
            !activeSlowdowns.isEmpty(),
            !vipLocks.isEmpty(),
            vipLocks.values().stream().allMatch(l -> l.overridable)
        );
        
        EternalEnginePacketHandler.sendToAll(packet);
    }
    
    /**
     * Check if speed value is valid
     */
    private boolean isValidSpeed(int speed) {
        for (int valid : VALID_SPEEDS) {
            if (valid == speed) return true;
        }
        return false;
    }
    
    // Getters
    
    public int getCurrentYear() {
        int year = TerraTimeBridge.getCurrentYear(currentYear.get());
        if (year != currentYear.get()) {
            currentYear.set(year);
            WorldStateManager.getInstance().setCurrentYear(year);
        }
        return year;
    }
    
    /**
     * Go to 1 January of {@code year}, on purpose: it may be earlier (step 18d: only /ee date and starting history
     * over call this for an earlier year; /ee year set and /ee morph jump only go forward). The calendar takes it.
     */
    public void setCurrentYear(int year) {
        boolean jumped = SimulationEngine.isInitialized()
            && TerraTimeBridge.jumpToYear(SimulationEngine.getInstance().getServer(), year);
        int actualYear = jumped ? TerraTimeBridge.getCurrentYear(year) : year;
        if (!TerraTimeBridge.isAvailable()) {
            WorldCalendar.setDate(KnowledgeDate.key(year, 1, 1));
        }
        currentYear.set(actualYear);
        ticksInCurrentYear.set(0);
        WorldStateManager.getInstance().setCurrentYear(actualYear);
        WorldCalendar.accept();
    }

    // ==================== step 14: year changes, slowdowns, mode ====================

    /** Register a listener for year changes (called on the server thread, at most once per tick). */
    public void addYearListener(YearListener listener) {
        if (listener != null) {
            yearListeners.add(listener);
        }
    }

    /**
     * Tell the listeners when the year moved since the last tick: the clock advanced it, Terra++ time
     * moved on, or an operator set it. Also switches the event slowdowns for the new year.
     */
    private void announceYearChange() {
        int now = currentYear.get();
        int before = announcedYear;
        if (before == now) {
            return;
        }
        announcedYear = now;
        if (before == Integer.MIN_VALUE) {
            return;
        }
        updateYearSlowdowns(now, true);
        for (YearListener listener : yearListeners) {
            try {
                listener.yearChanged(before, now);
            } catch (Throwable t) {
                LOGGER.warn("[Clock] A year listener failed for {} -> {}: {}", before, now, t.toString());
            }
        }
    }

    /**
     * Switch the event slowdowns for {@code year}: on inside an event's years, off outside them. Terra++
     * time never gets one (see {@link #EVENT_SLOWDOWNS}).
     */
    private void updateYearSlowdowns(int year, boolean announce) {
        boolean terra = TerraTimeBridge.isAvailable();
        List<String> started = new ArrayList<>();
        List<String> ended = new ArrayList<>();
        for (Map.Entry<String, SlowdownConfig> e : EVENT_SLOWDOWNS.entrySet()) {
            SlowdownConfig c = e.getValue();
            boolean inside = !terra && year >= c.fromYear() && year <= c.toYear();
            boolean active = activeSlowdowns.containsKey(e.getKey());
            if (inside && !active) {
                activeSlowdowns.put(e.getKey(), new SlowdownEntry(e.getKey(), c.maxSpeed(), Long.MAX_VALUE, true));
                started.add(e.getKey());
            } else if (!inside && active && activeSlowdowns.get(e.getKey()).expireTime() == Long.MAX_VALUE) {
                activeSlowdowns.remove(e.getKey());
                ended.add(e.getKey());
            }
        }
        if (started.isEmpty() && ended.isEmpty()) {
            return;
        }
        calculateEffectiveSpeed();
        broadcastSpeedChange(started.isEmpty() ? "Event over: " + ended : "Event: " + started);
        LOGGER.info("[Clock] Year {}: slowdowns started {} ended {}; speed {}x", year, started, ended,
            effectiveSpeed.get());
        if (!announce || !SimulationEngine.isInitialized() || SimulationEngine.getInstance().getServer() == null) {
            return;
        }
        MinecraftServer server = SimulationEngine.getInstance().getServer();
        for (String key : started) {
            SlowdownConfig c = EVENT_SLOWDOWNS.get(key);
            server.getPlayerList().broadcastSystemMessage(Component.literal("§6History slows down for " + c.name()
                + " (" + yearText(c.fromYear()) + "–" + yearText(c.toYear()) + "): at most " + c.maxSpeed()
                + "x until it is over.§r"), false);
        }
        for (String key : ended) {
            SlowdownConfig c = EVENT_SLOWDOWNS.get(key);
            server.getPlayerList().broadcastSystemMessage(Component.literal("§6" + capitalise(c.name())
                + " is over; history runs at " + effectiveSpeed.get() + "x.§r"), false);
        }
    }

    private static String yearText(int year) {
        return year < 0 ? (-year) + " BC" : String.valueOf(year);
    }

    private static String capitalise(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Event slowdowns on right now, as "name (years, Nx)". */
    public List<String> describeSlowdowns() {
        List<String> out = new ArrayList<>();
        for (SlowdownEntry e : activeSlowdowns.values()) {
            SlowdownConfig c = EVENT_SLOWDOWNS.get(e.eventId());
            out.add(c == null ? e.eventId() + " (" + e.maxSpeed() + "x)"
                : c.name() + " (" + yearText(c.fromYear()) + "–" + yearText(c.toYear()) + ", " + c.maxSpeed() + "x)");
        }
        return out;
    }

    public long getTicksInCurrentYear() {
        return ticksInCurrentYear.get();
    }

    /** True when the year comes from Terra++ time (the Terra++ fork with its time switched on). */
    public boolean isTerraTime() {
        return TerraTimeBridge.isAvailable();
    }

    /**
     * Real minutes one world year takes now, with the Minecraft day of 20 minutes: 365 / speed Minecraft days per
     * year with both clocks (step 18d; the Eternal Engine clock used to run speed years per Minecraft day).
     */
    public double realMinutesPerYear() {
        int speed = isTerraTime() ? getBaseSpeed() : effectiveSpeed.get();
        return 365.0d / Math.max(1, speed) * 20.0d;
    }

    /** One line: which clock runs and how fast, for /ee speed status, /ee check all and the log. */
    public String describeMode() {
        double minutes = realMinutesPerYear();
        String rate = minutes >= 120.0d
            ? String.format(Locale.ROOT, "%.1f real hours", minutes / 60.0d)
            : String.format(Locale.ROOT, "%.1f real minutes", minutes);
        if (isTerraTime()) {
            int scale = Math.max(1, getBaseSpeed());
            return "Clock: Terra++ time (the year follows the Minecraft day count, scale " + scale + "x): 1 year = "
                + String.format(Locale.ROOT, "%.1f", 365.0d / scale) + " Minecraft days = " + rate + ".";
        }
        int speed = Math.max(1, effectiveSpeed.get());
        return "Clock: the Eternal Engine clock at " + speed + "x (base " + baseSpeed.get() + "x; the date follows the"
            + " Minecraft days, " + (speed == 1 ? "one day" : speed + " days") + " of history per Minecraft day): 1 year = "
            + String.format(Locale.ROOT, "%.1f", 365.0d / speed) + " Minecraft days = " + rate + ".";
    }
    
    public int getBaseSpeed() {
        int speed = TerraTimeBridge.getTimeScale(baseSpeed.get());
        if (speed != baseSpeed.get()) {
            baseSpeed.set(speed);
            WorldStateManager.getInstance().setSimulationSpeed(speed);
        }
        return speed;
    }
    
    public int getEffectiveSpeed() {
        return effectiveSpeed.get();
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public boolean isPaused() {
        return paused.get();
    }
    
    public boolean isSlowdownActive() {
        return !activeSlowdowns.isEmpty();
    }
    
    public boolean isVipLockActive() {
        return !vipLocks.isEmpty();
    }
    
    public boolean isOverrideActive() {
        return overrideActive.get();
    }
    
    // Inner classes
    
    private record SlowdownEntry(String eventId, int maxSpeed, long expireTime, boolean overridable) {}
    
    private record VipLockEntry(String lockId, int maxSpeed, boolean overridable) {}
    
    private record SlowdownConfig(String name, int maxSpeed, int fromYear, int toYear) {}
}

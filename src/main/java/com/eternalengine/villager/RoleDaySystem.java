package com.eternalengine.villager;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.core.WorldStateManager;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.knowledge.TalkHold;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The role day — the town's keeper (step R1, permission 05).
 *
 * <p>The crowd's head is the day machine: the body's needs, and whatever the town writes for it. This
 * is the layer above it: the town's clocks. A role changes a person's day — the bell people leave for
 * work at dawn in one wave and come home at dusk, the shift people keep their own hours, the sellers
 * keep their stand and their own market mood, and at the prayer hour (in the years the custom is kept)
 * the whole street stops and turns to the qibla.
 *
 * <p>What it is, and is not:
 * <ul>
 *   <li>It decides <em>when</em> and <em>where</em>. The person's own navigation does the walking —
 *       a target, not a new pathfinder; a path is issued only when the last one is done (or the
 *       person was led off course), so a district never re-plans at once.</li>
 *   <li>It touches movement and facing only. It never spawns, despawns, rehomes, retints, or renames.
 *       The crowd's count, spacing, spread, and look are untouched (directive 01).</li>
 *   <li>It yields to what outranks it: an event scene's own people (the scene tag), a talk in
 *       progress (the talk hold), and the frozen far crowd, which gets the prayer hour as a head turn
 *       only.</li>
 * </ul>
 *
 * <p>In-game test levers ({@link com.eternalengine.commands.RoleCommands}): {@code /ee role} the page,
 * {@code /ee role prayer} the hour comes now, {@code /ee role wave} the workday wave goes out now.
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class RoleDaySystem {

    private RoleDaySystem() {
    }

    /** The qibla from the Deccan (bearing ~276° from true north): Minecraft yaw 96 (west, a hair north). */
    public static final float QIBLA_YAW = 96.0f;

    /**
     * The prayer hours, in Minecraft time (1000 ticks = one game-hour; tick 0 = 6:00):
     * 07:00, 18:00, 21:00.
     */
    private static final long[] PRAY_STARTS = {1000L, 12000L, 15000L};
    /** Each prayer, in ticks: 24 game-minutes = 20 real seconds. */
    private static final long PRAY_LEN = 400L;

    /** The bell day: 06:00–18:00. The shift day: 08:00–16:00. Ticks from 6:00. */
    private static final long BELL_START = 0L, BELL_END = 12000L;
    private static final long SHIFT_START = 2000L, SHIFT_END = 10000L;

    /** The years the prayer custom is kept (the record's span; the far ends are not scored). */
    private static final int PRAY_FROM = 700, PRAY_TO = 2050;

    /** How far the keeper reaches from a player, in blocks. */
    private static final double REACH = 48.0;

    /** The home keys the spawner writes on each person (its TAG_HOME_X / TAG_HOME_Z are private). */
    private static final String HOME_X = "eternalengine.homeX";
    private static final String HOME_Z = "eternalengine.homeZ";

    public enum Kind { PRAY, GO, HOLD }

    private static final class Dir {
        final Kind kind;
        final double x, z, speed;

        Dir(Kind kind, double x, double z, double speed) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.speed = speed;
        }
    }

    /** What the town says to each person right now. The entity's hook reads this every tick. */
    private static final Map<Integer, Dir> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Set<Integer> IN_RANGE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** The last target issued to each person, and when — {targetX, targetZ, issueTick}. */
    private static final Map<Integer, double[]> LAST_ISSUE = new ConcurrentHashMap<>();

    /** People who must be stopped exactly once — the hook calls stop() once, never per tick. */
    private static final Set<Integer> MUST_STOP = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // The test levers: the hour and the wave, on demand.
    private static volatile long forcedPrayUntil = 0L;
    private static volatile long forcedWorkUntil = 0L;

    /** /ee role prayer: the prayer hour runs until {@code ticks} (game ticks). */
    public static void forcePrayer(long ticks) {
        forcedPrayUntil = now() + ticks;
    }

    /** /ee role wave: everyone's workday is "now" until {@code ticks} (game ticks). */
    public static void forceWave(long ticks) {
        forcedWorkUntil = now() + ticks;
    }

    public static boolean prayerForced() {
        return now() < forcedPrayUntil;
    }

    public static boolean waveForced() {
        return now() < forcedWorkUntil;
    }

    private static long now() {
        ServerLevel level = overworld();
        return level == null ? 0L : level.getGameTime();
    }

    @Nullable
    private static ServerLevel overworld() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.overworld();
    }

    /** The prayer hour now: the manual lever, or the window at a year that keeps the custom. */
    public static boolean prayingNow(ServerLevel level) {
        long gt = level.getGameTime();
        if (gt < forcedPrayUntil) {
            return true;
        }
        int year = WorldStateManager.getInstance().getCurrentYear();
        if (year < PRAY_FROM || year > PRAY_TO) {
            return false;
        }
        long t = Math.floorMod(level.getDayTime(), 24000L);
        for (long start : PRAY_STARTS) {
            if (t >= start && t < start + PRAY_LEN) {
                return true;
            }
        }
        return false;
    }

    /**
     * The role's work class. The sellers keep their stand (their day is their own market mood, v2);
     * the station and carriage people keep the shift; the hands of the workshops keep the bell; the
     * rest split by their role, stably.
     */
    public static String workClass(@Nullable String role) {
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (com.eternalengine.money.MarketService.buyer(r) || r.equals("money_changer")
                || com.eternalengine.news.NewsEras.newsRole(r)) {
            return "market";
        }
        if (r.contains("porter") || r.contains("station") || r.contains("rail")
                || r.contains("carriage") || r.contains("driver") || r.contains("tonga")
                || r.contains("cart")) {
            return "shift";
        }
        if (r.contains("mill") || r.contains("weav") || r.contains("loom") || r.contains("worker")
                || r.contains("labour") || r.contains("mason") || r.contains("smith")
                || r.contains("carpenter") || r.contains("builder")) {
            return "bell";
        }
        return (Math.abs(r.hashCode()) % 2 == 0) ? "bell" : "shift";
    }

    /** The work spot: a stable offset from the person's home, per person. */
    static double[] workSpot(HumanNpcEntity npc) {
        CompoundTag tag = npc.getPersistentData();
        double hx = homeXZ(tag, npc, true);
        double hz = homeXZ(tag, npc, false);
        double a = (npc.getId() * 137.0) % (2.0 * Math.PI);
        return new double[] {hx + Math.cos(a) * 10.0, hz + Math.sin(a) * 10.0};
    }

    private static double homeXZ(CompoundTag tag, HumanNpcEntity npc, boolean x) {
        String key = x ? HOME_X : HOME_Z;
        return tag.contains(key) ? (double) tag.getInt(key) : (x ? npc.getX() : npc.getZ());
    }

    // ==================== the keeper's tick ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.isStopped()) {
            return;
        }
        ServerLevel level = server.overworld();
        if (level == null || level.getGameTime() % 10L != 0) {
            return;
        }
        long gt = level.getGameTime();
        long t = Math.floorMod(level.getDayTime(), 24000L);
        boolean praying = prayingNow(level);
        boolean working = gt < forcedWorkUntil;
        IN_RANGE.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (HumanNpcEntity npc : level.getEntitiesOfClass(HumanNpcEntity.class,
                    player.getBoundingBox().inflate(REACH), n -> n.isAlive())) {
                CompoundTag tag = npc.getPersistentData();
                if (tag.getBoolean(PresenceLocalSpawner.TAG_EVENT)) {
                    continue; // the scene has its own people
                }
                if (TalkHold.busy(npc)) {
                    continue; // a talk outranks the day
                }
                IN_RANGE.add(npc.getId());
                if (praying) {
                    set(npc, new Dir(Kind.PRAY, 0.0, 0.0, 0.0));
                    if (npc.isNoAi()) {
                        // the frozen far crowd turns its head only — the AI step never runs for it
                        npc.setYRot(QIBLA_YAW);
                        npc.setYHeadRot(QIBLA_YAW);
                        npc.setYBodyRot(QIBLA_YAW);
                    }
                    continue;
                }
                if (npc.isNoAi()) {
                    clear(npc);
                    continue;
                }
                if ("market".equals(workClass(npc.getRole()))) {
                    clear(npc); // the seller keeps the stand; his day is his own market mood
                    continue;
                }
                boolean bell = "bell".equals(workClass(npc.getRole()));
                boolean inWork = working || (bell
                        ? (t >= BELL_START && t < BELL_END)
                        : (t >= SHIFT_START && t < SHIFT_END));
                double tx, tz;
                if (inWork) {
                    double[] spot = workSpot(npc);
                    tx = spot[0];
                    tz = spot[1];
                } else {
                    tx = homeXZ(tag, npc, true);
                    tz = homeXZ(tag, npc, false);
                }
                double dx = tx - npc.getX();
                double dz = tz - npc.getZ();
                if (dx * dx + dz * dz < 100.0) {
                    set(npc, new Dir(Kind.HOLD, 0.0, 0.0, 0.0));
                } else {
                    set(npc, new Dir(Kind.GO, tx, tz, 0.9D));
                }
            }
        }
        // out of reach is no longer ours to say
        BY_ENTITY.keySet().removeIf(id -> !IN_RANGE.contains(id));
        MUST_STOP.removeIf(id -> !IN_RANGE.contains(id));
        LAST_ISSUE.keySet().removeIf(id -> !IN_RANGE.contains(id));
    }

    // ==================== the entity's hook ====================

    /**
     * Called from the person's {@code customServerAiStep}, after the talk hold, and only when no talk
     * holds the person — so a talk still wins. Honours what the town said.
     */
    public static void step(HumanNpcEntity npc) {
        Dir d = BY_ENTITY.get(npc.getId());
        if (d == null) {
            return;
        }
        var nav = npc.getNavigation();
        switch (d.kind) {
            case PRAY:
                if (MUST_STOP.remove(npc.getId())) {
                    nav.stop();
                }
                double fx = npc.getX() - Math.sin(Math.toRadians(QIBLA_YAW)) * 10.0;
                double fz = npc.getZ() + Math.cos(Math.toRadians(QIBLA_YAW)) * 10.0;
                npc.getLookControl().setLookAt(fx, npc.getY(), fz, 30.0f, 30.0f);
                break;
            case GO: {
                double dx = d.x - npc.getX();
                double dz = d.z - npc.getZ();
                double dist2 = dx * dx + dz * dz;
                // We never ask the navigator whether its last path is done. We keep the last target
                // we gave, and when: the target changed (the wave), the person was led more than
                // sixteen blocks off course, or twenty real seconds passed and they still have more
                // than four blocks to walk (the path died, or a stall held them).
                double[] last = LAST_ISSUE.get(npc.getId());
                long nowTick = npc.level().getGameTime();
                boolean targetChanged = last == null
                        || Math.abs(last[0] - d.x) > 0.5 || Math.abs(last[1] - d.z) > 0.5;
                boolean pathExpired = last != null && dist2 > 16.0 && nowTick - last[2] > 400L;
                if (targetChanged || dist2 > 256.0 || pathExpired) {
                    nav.moveTo(d.x, npc.getY(), d.z, d.speed);
                    LAST_ISSUE.put(npc.getId(), new double[] {d.x, d.z, (double) nowTick});
                }
                break;
            }
            case HOLD:
                if (MUST_STOP.remove(npc.getId())) {
                    nav.stop();
                }
                break;
            default:
        }
    }

    /** What the town is saying to this person right now (the /ee role page). */
    public static String stateOf(int entityId) {
        Dir d = BY_ENTITY.get(entityId);
        if (d == null) {
            return "free";
        }
        switch (d.kind) {
            case PRAY:
                return "praying";
            case GO:
                return "on the way";
            case HOLD:
                return "at post";
            default:
                return "free";
        }
    }

    private static void set(HumanNpcEntity npc, Dir d) {
        Dir old = BY_ENTITY.get(npc.getId());
        if (old != null && old.kind == d.kind
                && (d.kind != Kind.GO || (Math.abs(old.x - d.x) < 0.5 && Math.abs(old.z - d.z) < 0.5))) {
            return;
        }
        BY_ENTITY.put(npc.getId(), d);
        if (d.kind == Kind.GO) {
            MUST_STOP.remove(npc.getId()); // a new path replaces the stop
        } else {
            MUST_STOP.add(npc.getId()); // stop exactly once when the hook honours this
        }
    }

    private static void clear(HumanNpcEntity npc) {
        BY_ENTITY.remove(npc.getId());
        MUST_STOP.remove(npc.getId());
        LAST_ISSUE.remove(npc.getId());
    }
}

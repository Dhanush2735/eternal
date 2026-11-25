package com.eternalengine.civilization;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.core.SimulationEngine;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Whose people these are, asked again of everyone who is already in the world.
 *
 * <h3>The mistake this fixes</h3>
 * Board 17 taught the spawner to ask <em>who holds this ground in this year</em> instead of
 * <em>who founded this place</em>. That repairs every person drawn from then on — and leaves every
 * person already standing in a saved world exactly as the old rule made them. A player who opens the
 * world they played last week still finds the Shang at Anyang in 430 BC, and telling them to walk
 * about until the crowd is re-drawn is not a fix; it is homework.
 *
 * <p>So the same question is asked of the people who are already there. Whenever a person enters a
 * level — loaded from a save, spawned by the crowd, recalled from a place's memory — their
 * civilization is checked against the year the world is standing in. If the civilization named is one
 * that had not yet begun, or has already fallen, they are given the civilization that does hold that
 * ground in that year, or none, by the same rule the spawner uses
 * ({@link HistoricalPresence#query(CivilizationManager, double, double, int)}). Nothing else about
 * them is touched: their name, age, role, clothes and memory of the place stay.
 *
 * <h3>Why the join of a level, and not a sweep</h3>
 * A sweep runs when somebody remembers to run it, and misses every chunk that loads later. A person
 * is in the world exactly when they join it, so the check happens there, once per person, on the
 * server thread, in the same tick as the join — cheap enough to do always (one map lookup in the
 * ordinary case, where the named civilization is alive) and impossible to forget.
 *
 * <p>The people a place remembers but is not standing up ({@link com.eternalengine.villager.PeopleMemory})
 * are records rather than entities, so they never reach the join hook; they are corrected where they
 * are held, in the same minute's pass ({@link #reconcileRemembered(ServerLevel)}).
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class WhoBelongsHere {

    /** Ids that name no civilization: a crowd label, or a rival from another dimension. */
    public static final Set<String> NOT_A_CIVILIZATION =
            Set.of("neutral", "witness", "present", "ohm", "rival_dimension");

    private static final AtomicLong CORRECTED = new AtomicLong();

    private WhoBelongsHere() {
    }

    /** How many people this server has corrected since it started. */
    public static long corrected() {
        return CORRECTED.get();
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof HumanNpcEntity npc) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            reconcile(npc, level, currentYearOr(level));
        } catch (Throwable ignored) {
            // A person is never prevented from entering the world by a bookkeeping error: the check
            // reports them either way, and the spawner is already right.
        }
    }

    /** Corrects one person in place. Returns the id they now carry. */
    public static String reconcile(HumanNpcEntity npc, ServerLevel level, int year) {
        String now = npc.getCivilizationId();
        String right = rightCivFor(npc.blockPosition().getX(), npc.blockPosition().getZ(), year, now);
        if (right != null && !right.equals(now)) {
            npc.setCivilizationId(right);
            CORRECTED.incrementAndGet();
            org.slf4j.LoggerFactory.getLogger("eternalengine").debug(
                    "[Civilization] {} was {} after its time; now {} in {}",
                    npc.getName().getString(), now, right, year);
            return right;
        }
        return now;
    }

    /**
     * The id this person should carry: their own if it names a civilization that stands in that year
     * (or names no civilization at all), and otherwise the answer the spawner would give today.
     * Returns {@code null} when the world has no civilization manager yet, which leaves the person
     * untouched rather than guessed at.
     */
    public static String rightCivFor(int blockX, int blockZ, int year, String current) {
        CivilizationManager civs = manager();
        if (civs == null) {
            return null;
        }
        double[] geo = TerraCoordinateHelper.toGeo(blockX, blockZ);
        // A place the projection cannot name (NaN, or off the Earth) is not evidence about who lives
        // there. Leave the person exactly as they are and let the in-game check report them: wiping a
        // name because a coordinate lookup failed would be worse than the defect being repaired.
        if (geo == null || geo.length < 2 || !Double.isFinite(geo[0]) || !Double.isFinite(geo[1])
                || Math.abs(geo[0]) > 90.0d || Math.abs(geo[1]) > 180.0d) {
            return null;
        }
        return rightCivFor(civs, geo[0], geo[1], year, current);
    }

    /**
     * The rule itself, as a pure function of the tables, so it can be tested without a world: keep the
     * civilization a person carries if it stands in that year (or names none at all), and otherwise
     * give them the answer the spawner would give at that place and year.
     */
    public static String rightCivFor(CivilizationManager civs, double lat, double lon, int year, String current) {
        if (civs == null) {
            return null;
        }
        if (current == null || current.isBlank()) {
            return null;
        }
        String id = current.toLowerCase(Locale.ROOT);
        if (NOT_A_CIVILIZATION.contains(id)) {
            return current;
        }
        var civ = civs.getCivilization(id);
        if (civ.isPresent() && civ.get().isActiveInYear(year)) {
            return current;
        }
        String answer = HistoricalPresence.query(civs, lat, lon, year).spawnCivId();
        return answer == null || answer.isBlank() ? "neutral" : answer;
    }

    /**
     * Every person standing in every level, asked again. Called once a minute by the simulation
     * clock's tick, because the year moves on and a person drawn in one century must not still be
     * standing in another.
     */
    public static int reconcileLoaded(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int changed = 0;
        int year = currentYear();
        for (ServerLevel level : server.getAllLevels()) {
            changed += reconcileAll(level, year);
            changed += reconcileRemembered(level);
        }
        return changed;
    }

    /**
     * The same question for the people a place remembers but is not standing up — the district's
     * absentees. They are records, not entities, so nothing brings them past the join hook; they are
     * corrected where they are held, against the year their own record was last kept.
     */
    public static int reconcileRemembered(ServerLevel level) {
        CivilizationManager civs = manager();
        if (civs == null) {
            return 0;
        }
        int changed = 0;
        try {
            for (var person : com.eternalengine.villager.PeopleMemory.get(level).everyone()) {
                String now = person.civId();
                if (now == null || now.isBlank() || NOT_A_CIVILIZATION.contains(now.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                var civ = civs.getCivilization(now.toLowerCase(Locale.ROOT));
                int year = person.lastUpdatedYear();
                if (civ.isPresent() && civ.get().isActiveInYear(year)) {
                    continue;
                }
                String answer = HistoricalPresence.query(civs, person.latitude(), person.longitude(), year)
                        .spawnCivId();
                person.setCivId(answer == null || answer.isBlank() ? "neutral" : answer);
                changed++;
            }
            if (changed > 0) {
                com.eternalengine.villager.PeopleMemory.get(level).setDirty();
            }
        } catch (Throwable ignored) {
            // see onJoinLevel
        }
        return changed;
    }

    /** Every person standing in the level, asked again. Used after the year is moved. */
    public static int reconcileAll(ServerLevel level, int year) {
        int changed = 0;
        try {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof HumanNpcEntity npc) {
                    String was = npc.getCivilizationId();
                    String now = reconcile(npc, level, year);
                    if (!java.util.Objects.equals(was, now)) {
                        changed++;
                    }
                }
            }
        } catch (Throwable ignored) {
            // see onJoinLevel
        }
        return changed;
    }

    /** The year the world is standing in, whichever clock is answering. */
    public static int currentYear() {
        return currentYearOr(null);
    }

    private static int currentYearOr(ServerLevel level) {
        try {
            if (com.eternalengine.clock.SimulationClockManager.isInitialized()) {
                return com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear();
            }
        } catch (Throwable ignored) {
            // fall through to the world state
        }
        try {
            return com.eternalengine.core.WorldStateManager.getInstance().getCurrentYear();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static CivilizationManager manager() {
        try {
            if (!SimulationEngine.isInitialized()) {
                return null;
            }
            return SimulationEngine.getInstance().getCivilizationManager();
        } catch (Throwable ignored) {
            return null;
        }
    }
}

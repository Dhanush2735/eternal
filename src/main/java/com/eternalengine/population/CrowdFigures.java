package com.eternalengine.population;

import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.villager.PresenceLocalSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The crowd, drawn.
 *
 * <h3>The mistake this fixes</h3>
 * Forty ticked people in a district is not a crowd. It is a group — forty figures over a
 * twenty-eight block square is 0.014 people per square metre, while a real market square runs at 0.5
 * to 1.0, and no amount of tuning a cap can close a gap that size: a server that ticks every person
 * a city shows would fall over long before the square looked busy.
 *
 * <p>A crowd, therefore, is a <em>drawing</em>. The people who think are the ones you can talk to;
 * the people who only stand there are the crowd you see. This class stands the second kind up:
 * figures from the same {@link HumanNpcEntity}, in the same district, in the same era's clothes,
 * with their AI switched off — so the square is full, and nothing in it is thinking.
 *
 * <h3>Where they stand</h3>
 * Everywhere in the place, not in one square. The place's population is divided over the chunks of
 * its area ({@link ChunkPopulation}), so the crowd covers the whole settlement and a player meets
 * people wherever they walk inside it — which is what a place with a hundred thousand people in it
 * should feel like. The division is the honest one (Secunderabad, 1948: 0.32 people in an average
 * chunk, a person every 28 metres) and {@code presenceCrowdConcentration} says how much of it
 * gathers at the place's heart, where the market is.
 *
 * <p>Inside a chunk, figures stand on {@link CrowdDensity}'s plan: one figure per grid cell, so no
 * two people occupy the same block and a chunk never looks like a heap. The plan is a pure function
 * of the world seed and the chunk, so a player who walks away and comes back finds the same crowd.
 *
 * <p>Only the chunks within {@code presenceCrowdInterestBlocks} of a player are stood up at all. A
 * place of four hundred thousand chunks costs nothing to have; standing in it costs one view.
 *
 * <h3>What a figure costs</h3>
 * <table>
 *   <tr><th></th><th>living (ticked)</th><th>figure (drawn)</th></tr>
 *   <tr><td>goal checks per tick</td><td>up to 4 goals, ~25/s each</td><td><b>0</b> — no goals, no navigation, no path search</td></tr>
 *   <tr><td>physics</td><td>walks, falls, collides</td><td>stands; no gravity, no travel</td></tr>
 *   <tr><td>spawning</td><td>every 2 seconds, in knots</td><td>at most {@value #PER_CYCLE} per 2 seconds, then never again</td></tr>
 *   <tr><td>when the player leaves</td><td>vanilla despawn past 128 blocks</td><td>same rule — the figures go with the crowd</td></tr>
 * </table>
 *
 * <p>A figure never moves under its own power and is never woken: {@code setNoAi(true)} is what this
 * class does to every one of them, and {@link #sweep} is the only thing that ever removes them.
 */
public final class CrowdFigures {

    /** Set on every drawn figure, and on nothing else in the world. */
    public static final String TAG_FIGURE = "eternalengine.crowdFigure";

    /** The year the crowd was drawn for: a place's people change with the year, so its crowd does. */
    public static final String TAG_FIGURE_YEAR = "eternalengine.crowdFigureYear";

    /** Which place in its chunk's crowd plan this figure occupies, so it can be put back exactly. */
    public static final String TAG_FIGURE_SLOT = "eternalengine.crowdFigureSlot";

    /** How many figures the chunk's plan held when this one was placed: the plan is that plus a seed. */
    public static final String TAG_FIGURE_PLAN = "eternalengine.crowdFigurePlan";

    /** The chunk this figure belongs to, which is also the crowd plan it was placed from. */
    public static final String TAG_FIGURE_CHUNK_X = "eternalengine.crowdChunkX";
    public static final String TAG_FIGURE_CHUNK_Z = "eternalengine.crowdChunkZ";

    /** The district this figure belongs to. The same two keys the living people carry. */
    /** Where the player was when the crowd last re-arranged itself for their eyes. */
    public static final String TAG_WATCHED_X = "eternalengine.crowdWatchedX";
    public static final String TAG_WATCHED_Z = "eternalengine.crowdWatchedZ";

    public static final String TAG_FIGURE_CX = "eternalengine.presenceDistrictX";
    public static final String TAG_FIGURE_CZ = "eternalengine.presenceDistrictZ";

    /**
     * How many figures may be stood up in one two-second cycle. A crowd of four hundred is drawn in
     * about half a minute, at a distance where nobody is watching it arrive; standing up four hundred
     * entities in one tick would be a spike the client notices.
     */
    private static final int PER_CYCLE = 32;

    /**
     * Step 21 (known problem 6, one slow spawner cycle after a jump): places in a chunk's plan found to have no ground
     * (water, a cliff; in the sandbox, the flat world beyond the laid ground) are not looked up again for five minutes,
     * and one cycle gives up after {@link #MISSES_PER_CYCLE} refused places. A refused place did not use up the cycle's
     * budget of 32, so a crowd beside a lake or the sea looked up every place of every chunk in view, every cycle, and a
     * place with no ground was looked up once more for every figure waiting to be moved: 20,803 ground look-ups in one
     * cycle on the sandbox server (254 ms).
     */
    private static final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap NO_GROUND =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private static final long NO_GROUND_TICKS = 20L * 60L * 5L;
    static final int MISSES_PER_CYCLE = 256;

    /** The key of one place of one chunk's plan: 22 bits for each chunk coordinate (the whole Earth), 10 for the place. */
    static long placeKey(int chunkX, int chunkZ, int slot) {
        return ((long) (chunkX & 0x3FFFFF) << 32) | ((long) (chunkZ & 0x3FFFFF) << 10) | (slot & 0x3FF);
    }

    private static boolean noGroundThere(ServerLevel level, long key) {
        long until = NO_GROUND.get(key);
        return until != 0L && level.getGameTime() < until;
    }

    private static void markNoGround(ServerLevel level, long key) {
        if (NO_GROUND.size() > 200_000) {
            NO_GROUND.clear();
        }
        NO_GROUND.put(key, level.getGameTime() + NO_GROUND_TICKS);
    }

    /** Step 21: how many places are remembered as having no ground, for the readouts. */
    public static int placesWithoutGround() {
        return NO_GROUND.size();
    }

    /** How close one person may stand to another. A person is 0.6 wide, so this leaves a shoulder. */
    private static final double FIGURE_CLEARANCE = 1.2d;

    /**
     * How many figures may be moved to a new place in one cycle. Standing a figure up builds an
     * entity, writes its tags and sends it to every client; moving one writes a position and its
     * tags. A whole crowd can be moved in a cycle, which is what lets a turn re-form the crowd in
     * one go rather than leaving the old fan standing for half a minute beside the new one.
     */
    private static final int MOVE_PER_CYCLE = 512;

    /** A figure pushed further than this off its place is put back on it. */
    private static final double DRIFT_BLOCKS = 1.0d;

    /** How far out a person's own walk goes from the place they belong to. */
    private static final double ROUTINE_BLOCKS = 7.0d;

    /**
     * How far a walking figure moves in one cycle (2 s). Slower than a villager's own walk, which is
     * all a figure needs: it is somebody crossing their own corner of the world, not a chase.
     */
    private static final double ROUTINE_STEP = 1.2d;

    /**
     * Beyond this, a figure is not walking anywhere — it has been moved to another place, or has just
     * been built there. Walks are step-limited; relocations are not, and never happen in view.
     */
    private static final double RELOCATE_BLOCKS = 4.0d;


    /** A chunk is 16×16 blocks; the crowd's unit of division is one chunk. */
    private static final int CHUNK_SIDE = ChunkPopulation.CHUNK_SIDE;

    /**
     * How far the crowd fills its chunk: half a chunk, less a fifth of a block, so the crowd of one
     * chunk meets the crowd of the next with no lane between them.
     */
    private static final double CHUNK_PLAN_HALF = CHUNK_SIDE / 2.0d - 0.1d;

    private CrowdFigures() {}

    /**
     * Keeps a district's crowd standing: corrects it to the drawing the player is looking at, puts
     * back the ones that have been shoved, and stands up only what the crowd does not already have.
     *
     * <p>The crowd's own control never deletes a figure. When the drawing moves — the player turns,
     * walks, or the place's people change with the year — the figures the drawing no longer wants in
     * a chunk are released and taken by the chunks that are short, and only a place that no released
     * figure can stand in is filled with a newly built one. Retiring figures at all is the business
     * of {@link #sweep}, which removes the ones from another year, another place, or out of reach of
     * every player; it is not the business of this method.
     *
     * @return how many figures were stood up, plus how many were moved to a new place, this cycle
     */
    public static int ensure(ServerLevel level, ServerPlayer player, BlockPos heart, SettlementRank rank,
                             long population, double placeRadiusKm, int year, double interestBlocks,
                             boolean settled) {
        if (level == null || player == null || heart == null || !drawnCrowdOn()) {
            return 0;
        }
        // The place's people, divided over the chunks of its area: what each chunk within reach of
        // this player actually carries. The division is the honest one — Secunderabad in 1948 is
        // 0.32 people in an average chunk — and the concentration decides how much of it gathers at
        // the heart, where the market is.
        int interest = (int) Math.max(CrowdDensity.MIN_RADIUS, interestBlocks);
        // Where the crowd stands: the place's people at the place's own built density, in the built
        // area that density implies, anchored on the player's chunk. A player inside the place is
        // standing in its built part — the fields are elsewhere, not under their feet. The vanilla
        // 24-block bubble is enforced per figure by PresenceLocalSpawner.figureSpot, not by leaving
        // whole chunks empty: an empty chunk is a hole you can see across, and 24 blocks asked for a
        // 48-block square hole.
        int anchorX = (player.blockPosition().getX() >> 4 << 4) + CHUNK_SIDE / 2;
        int anchorZ = (player.blockPosition().getZ() >> 4 << 4) + CHUNK_SIDE / 2;
        double density = ChunkPopulation.builtDensityPerKm2(rank, population, placeRadiusKm)
                * crowdedness();
        ChunkPopulation.View view = ChunkPopulation.viewOfCore(level.getSeed(), population, placeRadiusKm,
                density, anchorX, anchorZ, player.getX(), player.getZ(), interest);
        if (view.total() <= 0) {
            return 0;
        }
        // What the client will actually draw. The model may say five hundred people stand in this
        // view; the frame may afford four hundred. The budget is spent where the player is looking —
        // the ground behind them is not on screen — so the same four hundred read as a crowd ahead
        // instead of a thin ring all round. The total is unchanged; only which of the place's own
        // people are drawn moves.
        int[] shown = ChunkPopulation.viewTowards(view, figuresCap(), player.getX(), player.getZ(),
                player.getYRot(), PresenceLocalSpawner.minSpawnBlocks());
        if (!settled) {
            // Open country (turn 230): the model's people are out there somewhere in the region,
            // but at most a scattered few stand near the player. A village-sized drawing over a
            // camp was the flaw the user found at Secunderabad in 3000 BC.
            int allowed = 4;
            for (int i = 0; i < shown.length; i++) {
                if (shown[i] > allowed) {
                    shown[i] = allowed;
                }
                allowed -= shown[i];
                if (allowed <= 0) {
                    for (int j = i + 1; j < shown.length; j++) {
                        shown[j] = 0;
                    }
                    break;
                }
            }
        }

        List<HumanNpcEntity> standing = figuresIn(level, player.blockPosition(), interest + CHUNK_SIDE);

        // Who is standing where, by the chunk each figure belongs to.
        int[] here = new int[view.size()];
        List<List<HumanNpcEntity>> inChunk = new ArrayList<>();
        for (int i = 0; i < view.size(); i++) {
            List<HumanNpcEntity> list = figuresInChunk(standing, view.chunkX(i), view.chunkZ(i));
            inChunk.add(list);
            here[i] = list.size();
        }

        // The drawing moves — the player turns, walks, or the place's people change with the year —
        // and the crowd is corrected to it. Nothing is thrown away: a figure the drawing no longer
        // wants in its chunk is released to take a place in one that is short, and a chunk that is
        // short takes a released figure before a new one is ever built.
        //
        // With one exception, and it is the one a player notices. A crowd that has been seen stays
        // where it is: figures are only ever re-arranged while the player is standing still and
        // looking somewhere else. Walk on and the people you passed keep standing where they stood,
        // and the ground ahead is filled with new ones; walk back and the same people are still
        // there, in the same places, because nothing moved them in the meantime.
        // A chunk already standing near the count the drawing wants keeps what it has, so the crowd
        // does not reshuffle under the player's feet on every step. A chunk the drawing has emptied
        // is still emptied: that is the fan turning, not jitter.
        for (int i = 0; i < view.size(); i++) {
            shown[i] = CrowdDensity.settledCount(shown[i], here[i]);
        }
        // Standing still, or walking? A crowd is only re-arranged for eyes that have stopped.
        CompoundTag watched = player.getPersistentData();
        int watchedX = watched.getInt(TAG_WATCHED_X);
        int watchedZ = watched.getInt(TAG_WATCHED_Z);
        boolean still = Math.abs(player.blockPosition().getX() - watchedX) <= 2
                && Math.abs(player.blockPosition().getZ() - watchedZ) <= 2;
        watched.putInt(TAG_WATCHED_X, player.blockPosition().getX());
        watched.putInt(TAG_WATCHED_Z, player.blockPosition().getZ());

        int[][] rec = CrowdDensity.reconcile(shown, here);
        if (!still) {
            // On the move: the crowd stays standing, and a short chunk is filled with a new figure
            // rather than with somebody taken from the ground the player has just walked past.
            for (int i = 0; i < view.size(); i++) {
                rec[i][1] = 0;
            }
        }
        List<HumanNpcEntity> released = new ArrayList<>();
        for (int i = 0; i < view.size(); i++) {
            List<HumanNpcEntity> list = inChunk.get(i);
            int keep = rec[i][0];
            int surplus = rec[i][1];
            if (surplus > 0) {
                // Whoever is out of the player's view leaves first: a figure that moves where nobody
                // is looking is a figure nobody sees move. The ones kept are the front of the list and
                // the ones released its end, so the most visible come first (until 2026-10-12 this
                // sorted the other way round and released the nearest figures in view).
                list.sort((a, b) -> Integer.compare(awayRank(b, player), awayRank(a, player)));
                for (int k = keep; k < list.size(); k++) {
                    released.add(list.get(k));
                }
            }
            // The ones that stay are put on the plan the chunk has now, so the crowd stands where the
            // drawing says and not where the plan that placed them used to say.
            long seed = chunkSeed(level, view.chunkX(i), view.chunkZ(i));
            // Which places the ones who stay take. The same ones a watcher would meet first, so the
            // crowd a returning player finds is the crowd that was left, in the same places.
            // On the move nobody is re-placed: the crowd that has been seen stays where it stands (each
            // kept figure keeps its own place). Standing still, the kept figures take the places a watcher
            // meets first, each keeping its own place when that is one of them.
            int[] keepSlots = still
                    ? assignKept(list, keep, ChunkPopulation.nearestSlots(level.getSeed(), view.chunkX(i),
                            view.chunkZ(i), player.getX(), player.getZ(), keep))
                    : heldSlots(list, keep);
            for (int k = 0; k < keep && k < keepSlots.length; k++) {
                putOnPlan(level, list.get(k), seed, shown[i], keepSlots[k], view.chunkX(i),
                        view.chunkZ(i), year, heart);
            }
        }
        // Figures standing outside every chunk of this view keep their own drift correction.
        java.util.Set<HumanNpcEntity> spoken = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        for (List<HumanNpcEntity> list : inChunk) {
            spoken.addAll(list);
        }
        for (HumanNpcEntity figure : standing) {
            if (!spoken.contains(figure)) {
                putBack(level, figure, year);
            }
        }

        int budget = PER_CYCLE;
        int moved = 0;
        int placed = 0;
        int misses = 0;
        for (int i = 0; i < view.size(); i++) {
            int need = rec[i][2];
            if (need <= 0) {
                continue;
            }
            int want = shown[i];
            int chunkX = view.chunkX(i);
            int chunkZ = view.chunkZ(i);
            long seed = chunkSeed(level, chunkX, chunkZ);
            List<CrowdDensity.Figure> plan = CrowdDensity.planSquare(seed,
                    ChunkPopulation.CHUNK_CAPACITY, CHUNK_PLAN_HALF);
            List<double[]> occupied = occupiedInChunk(level, chunkX, chunkZ,
                    inChunk.get(i).subList(0, rec[i][0]));
            // A figure that has walked off along its day still holds its own place in the plan, and the
            // place is its identity: standing somebody new there would be the same person twice.
            for (HumanNpcEntity figure : inChunk.get(i)) {
                int held = figure.getPersistentData().getInt(TAG_FIGURE_SLOT);
                if (held >= 0 && held < plan.size()) {
                    occupied.add(new double[] {plan.get(held).dx(), plan.get(held).dz()});
                }
            }
            // freeSlots wants a plan and a count; the chunk's plan is the crowd, and only its free
            // places are used, so a figure is never stood on top of somebody already there.
            // Filled from the near side outward: the places a watcher meets first get somebody
            // first, so the crowd grows towards the player and never away from them.
            int[] slots = ChunkPopulation.nearestFirst(plan,
                    CrowdDensity.freeSlots(plan, occupied, FIGURE_CLEARANCE, need),
                    player.getX() - (chunkX * CHUNK_SIDE + CHUNK_SIDE / 2.0d),
                    player.getZ() - (chunkZ * CHUNK_SIDE + CHUNK_SIDE / 2.0d));
            for (int slot : slots) {
                if (released.isEmpty() && budget <= 0) {
                    break;
                }
                if (misses >= MISSES_PER_CYCLE) {
                    break; // step 21: enough refused places for one cycle; the next cycle goes on
                }
                // Step 21: the place is looked up once, before anybody is moved or stood up there.
                long key = placeKey(chunkX, chunkZ, slot);
                if (noGroundThere(level, key)) {
                    continue;
                }
                CrowdDensity.Figure spot = plan.get(slot);
                double[] at = standPoint(level, chunkX, chunkZ, slot,
                        chunkX * CHUNK_SIDE + spot.dx() + CHUNK_SIDE / 2.0d,
                        chunkZ * CHUNK_SIDE + spot.dz() + CHUNK_SIDE / 2.0d);
                BlockPos found = PresenceLocalSpawner.figureSpot(level, (int) Math.round(at[0]),
                        (int) Math.round(at[1]), player.getBlockY());
                if (found == null) {
                    misses++;
                    if (PresenceLocalSpawner.lastFigureRefusal() == PresenceLocalSpawner.SPOT_NO_GROUND) {
                        markNoGround(level, key);
                    }
                    continue;
                }
                boolean handled = false;
                if (moved < MOVE_PER_CYCLE) {
                    for (int r = 0; r < released.size(); r++) {
                        if (putOnPlan(level, released.get(r), seed, want, slot, chunkX, chunkZ,
                                year, heart)) {
                            released.remove(r);
                            moved++;
                            handled = true;
                        }
                        // Step 21: either way, one try per place. Every reason putOnPlan refuses belongs to the place
                        // (no ground, its own person already stands, Minecraft refused the body), so the next waiting
                        // figure would be refused too, each after a ground look-up of its own.
                        break;
                    }
                }
                if (handled || budget <= 0) {
                    continue;
                }
                BlockPos pos = found; // step 21: looked up once, above
                if (stand(level, pos, CrowdDensity.facing(seed, slot), slot, want, year, heart,
                        chunkX, chunkZ)) {
                    placed++;
                    budget--;
                }
            }
        }
        return placed + moved;
    }

    /**
     * The places the kept figures of a chunk take, in the order of {@code list}: a figure whose own place is
     * one of the wanted places keeps it, so a figure is never exchanged for another person in a place it
     * already holds; the remaining wanted places go to the others in order.
     */
    static int[] assignKept(List<HumanNpcEntity> list, int keep, int[] wanted) {
        int n = Math.min(Math.min(keep, wanted.length), list.size());
        int[] out = new int[n];
        java.util.Arrays.fill(out, -1);
        boolean[] given = new boolean[wanted.length];
        for (int k = 0; k < n; k++) {
            int held = list.get(k).getPersistentData().getInt(TAG_FIGURE_SLOT);
            for (int s = 0; s < wanted.length; s++) {
                if (!given[s] && wanted[s] == held) {
                    out[k] = held;
                    given[s] = true;
                    break;
                }
            }
        }
        int next = 0;
        for (int k = 0; k < n; k++) {
            if (out[k] >= 0) {
                continue;
            }
            while (next < wanted.length && given[next]) {
                next++;
            }
            if (next < wanted.length) {
                out[k] = wanted[next];
                given[next] = true;
            }
        }
        return out;
    }

    /** Each kept figure's own place, in the order of {@code list}. */
    private static int[] heldSlots(List<HumanNpcEntity> list, int keep) {
        int n = Math.min(keep, list.size());
        int[] out = new int[n];
        for (int k = 0; k < n; k++) {
            out[k] = list.get(k).getPersistentData().getInt(TAG_FIGURE_SLOT);
        }
        return out;
    }

    /** The figures of one chunk, out of the ones standing near this player. */
    private static List<HumanNpcEntity> figuresInChunk(List<HumanNpcEntity> standing,
                                                       int chunkX, int chunkZ) {
        List<HumanNpcEntity> out = new ArrayList<>();
        for (HumanNpcEntity figure : standing) {
            CompoundTag tag = figure.getPersistentData();
            if (tag.getInt(TAG_FIGURE_CHUNK_X) == chunkX && tag.getInt(TAG_FIGURE_CHUNK_Z) == chunkZ) {
                out.add(figure);
            }
        }
        return out;
    }

    /**
     * How much the player would see of a figure moving: figures outside the wedge the player is
     * looking down rank first, farthest of those first, then the ones in view, farthest first. The
     * crowd re-forms behind the player's back, not in front of their eyes.
     */
    private static int awayRank(HumanNpcEntity figure, ServerPlayer player) {
        double dx = figure.getX() - player.getX();
        double dz = figure.getZ() - player.getZ();
        double distance = Math.hypot(dx, dz);
        double yaw = Math.toRadians(player.getYRot());
        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);
        double cos = distance <= 1.0e-6d ? 1.0d
                : (dx * facingX + dz * facingZ) / distance;
        boolean inView = cos >= Math.cos(
                Math.toRadians(ChunkPopulation.DRAWN_WEDGE_DEGREES / 2.0d));
        double capped = Math.min(distance, 900.0d);
        return inView ? (int) (1_000.0d + (900.0d - capped)) : (int) (900.0d - capped);
    }

    /**
     * Where a figure stands <em>now</em>: its place in the chunk's plan, walked out along the day's
     * own route, kept inside its own chunk.
     *
     * <p>The place is still what names the person; the walk is what they are doing with the day. Both
     * come out of the same identity, so the person who walks is the person the place belongs to.
     */
    private static double[] standPoint(ServerLevel level, int chunkX, int chunkZ, int slot,
                                       double homeX, double homeZ) {
        long personSeed = CrowdDensity.identity(level.getSeed(), chunkX, chunkZ, slot)
                .getMostSignificantBits();
        double centreX = chunkX * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
        double centreZ = chunkZ * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
        // The day's own rule, fitted inside the person's chunk by the same arithmetic the checks use.
        double[] point = CrowdDensity.routinePointIn(level.getSeed(), personSeed, homeX, homeZ,
                level.getGameTime(), ROUTINE_BLOCKS, centreX, centreZ, CHUNK_PLAN_HALF - 0.5d);
        double limit = CHUNK_PLAN_HALF - 0.5d;
        point[0] = Math.max(centreX - limit, Math.min(centreX + limit, point[0]));
        point[1] = Math.max(centreZ - limit, Math.min(centreZ + limit, point[1]));
        return point;
    }

    /**
     * Moves a figure towards a point: a step if it is a walk, straight there if it is a relocation.
     *
     * <p>The difference matters to the eye. A person crossing their own corner moves a little every
     * cycle and reads as walking; a figure taken from one chunk to another has been out of sight the
     * whole time, and arriving instantly is both cheaper and invisible. The ground is checked at the
     * destination by the caller: a step is never taken onto ground that would not hold a person.
     */
    private static void stepTowards(ServerLevel level, HumanNpcEntity figure, double x, double z,
                                    int y, float facing) {
        double dx = x - figure.getX();
        double dz = z - figure.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance > RELOCATE_BLOCKS) {
            figure.moveTo(x, y, z, facing, 0.0f);
            return;
        }
        if (distance <= 0.05d && Math.abs(figure.getY() - y) <= 0.5d) {
            return;
        }
        if (distance <= ROUTINE_STEP) {
            figure.moveTo(x, y, z, facing, 0.0f);
            return;
        }
        double step = ROUTINE_STEP / distance;
        // A walk keeps the person facing the way they are going; a standing person keeps their face.
        figure.moveTo(figure.getX() + dx * step, y, figure.getZ() + dz * step,
                (float) Math.toDegrees(Math.atan2(-dx, dz)), 0.0f);
    }

    /**
     * Stands an existing figure on its place in the chunk's plan, and writes down which place that is.
     *
     * <p>Used both for a figure that keeps its place — the plan under it may have moved, so its place
     * is the plan's new one — and for a figure released from a chunk the drawing no longer wants and
     * taken by one that is short. The entity is the same entity: the crowd's control moves figures,
     * it does not make or unmake them.
     *
     * @return false when the ground there will not hold anybody, or the player's own 24 blocks cover
     *         it, in which case the figure stays exactly where it is
     */
    private static boolean putOnPlan(ServerLevel level, HumanNpcEntity figure, long seed, int planSize,
                                     int slot, int chunkX, int chunkZ, int year, BlockPos heart) {
        // The chunk's own plan, fixed by the ground: the same places on every visit, whether one
        // figure lives here or seventy. A place is not re-rolled when the count changes around it.
        List<CrowdDensity.Figure> plan = CrowdDensity.planSquare(seed, ChunkPopulation.CHUNK_CAPACITY,
                CHUNK_PLAN_HALF);
        if (slot < 0 || slot >= plan.size()) {
            return false;
        }
        CrowdDensity.Figure place = plan.get(slot);
        double homeX = chunkX * CHUNK_SIDE + place.dx() + CHUNK_SIDE / 2.0d;
        double homeZ = chunkZ * CHUNK_SIDE + place.dz() + CHUNK_SIDE / 2.0d;
        double[] walked = standPoint(level, chunkX, chunkZ, slot, homeX, homeZ);
        BlockPos pos = PresenceLocalSpawner.figureSpot(level, (int) Math.round(walked[0]),
                (int) Math.round(walked[1]), figure.getBlockY());
        if (pos == null) {
            return false;
        }
        // A figure stands for the place it stands on. Moving a figure to another place makes it that
        // place's person, so a player who comes back to a spot finds the ground's own people there
        // and not a face that wandered in from somewhere else -- and no place can ever be answered
        // for by two people, because the place has exactly one identity to give.
        java.util.UUID identity = CrowdDensity.identity(level.getSeed(), chunkX, chunkZ, slot);
        if (!identity.equals(figure.getUUID())) {
            // Another place's person. A live entity's UUID must never change: the level finds entities
            // by it, and the client is never told. (Until 2026-10-12 this called setUUID; after a short
            // walk most figures could no longer be found by their own UUID, some UUIDs stood twice, and
            // every new figure for such a place was refused with a "UUID already exists" warning.) So
            // that place's own person is stood up there and this figure leaves -- on screen the same as
            // the jump a move to another place always was.
            if (level.getEntity(identity) != null) {
                return false; // that place's person already stands somewhere
            }
            if (!stand(level, pos, CrowdDensity.facing(seed, slot), slot, planSize, year, heart, chunkX,
                    chunkZ)) {
                return false;
            }
            figure.discard();
            return true;
        }
        CompoundTag tag = figure.getPersistentData();
        tag.putInt(TAG_FIGURE_YEAR, year);
        tag.putInt(TAG_FIGURE_SLOT, slot);
        tag.putInt(TAG_FIGURE_PLAN, planSize);
        tag.putInt(TAG_FIGURE_CHUNK_X, chunkX);
        tag.putInt(TAG_FIGURE_CHUNK_Z, chunkZ);
        tag.putInt(TAG_FIGURE_CX, heart.getX());
        tag.putInt(TAG_FIGURE_CZ, heart.getZ());
        // A step if this is a walk, straight there if the figure has been moved to another place.
        stepTowards(level, figure, pos.getX() + 0.5d, pos.getZ() + 0.5d, pos.getY(),
                CrowdDensity.facing(seed, slot));
        return true;
    }

    /**
     * Removes the figures that should not be standing any more: the ones from another year, and the
     * ones standing where the world has nobody at all.
     *
     * <p>Deliberately not removed: figures the player has walked away from. A crowd that empties
     * itself behind you is a crowd you cannot come back to, and the figures are the one part of the
     * population that a player meets face to face. They are left standing where they are; the
     * year's rule is what retires them, when the world's people have moved on.
     */
    public static int sweep(ServerLevel level, ServerPlayer player, int year, boolean occupied,
                            double interestBlocks) {
        if (level == null || player == null) {
            return 0;
        }
        double reach = Math.max(interestBlocks + CHUNK_SIDE * 2.0d, 64.0d);
        List<HumanNpcEntity> figures = figuresIn(level, player.blockPosition(), reach);
        int removed = 0;
        for (HumanNpcEntity figure : figures) {
            CompoundTag tag = figure.getPersistentData();
            // Here a figure is retired for one reason: the crowd it belongs to is another year's. Not for
            // standing in another place's district: the crowd near the player is the crowd in front of
            // them, and a figure is re-tagged to the place it stands in when it is put on that place's
            // plan. Far from every player a figure goes too, in PresenceLocalSpawner.forgetFar (step 17):
            // that once made a place come back with strangers, but since step 16 a figure's identity is
            // its place, so the same people stand there again when a player returns.
            boolean stale = !occupied || tag.getInt(TAG_FIGURE_YEAR) != year;
            if (stale) {
                figure.discard();
                removed++;
            }
        }
        return removed;
    }

    /** How many figures this place should be showing, for the readouts and the tests. */
    public static int targetFigures(long population, SettlementRank rank) {
        return CrowdDensity.target(population, rank, figuresCap());
    }

    public static boolean isFigure(HumanNpcEntity npc) {
        return npc != null && npc.getPersistentData().getBoolean(TAG_FIGURE);
    }

    // ==================== the figures themselves ====================

    /** One person, standing, not thinking, in the chunk the place's population put them in. */
    private static boolean stand(ServerLevel level, BlockPos pos, float facing, int slot, int planSize,
                                 int year, BlockPos heart, int chunkX, int chunkZ) {
        java.util.UUID identity = CrowdDensity.identity(level.getSeed(), chunkX, chunkZ, slot);
        if (level.getEntity(identity) != null) {
            // This place's person already stands (perhaps walked off along their day): never the same
            // person twice. Minecraft would refuse the second one anyway, with a warning in the log.
            return false;
        }
        HumanNpcEntity figure = null;
        try {
            if (EternalEngineEntities.HUMAN_NPC != null && EternalEngineEntities.HUMAN_NPC.isPresent()) {
                figure = EternalEngineEntities.HUMAN_NPC.get().create(level);
            }
        } catch (Throwable ignored) {
            // the registry is not up yet: the crowd waits for the next cycle
        }
        if (figure == null) {
            return false;
        }
        // Who this is: not a fresh face, but the person who belongs to this place in this chunk's
        // plan. The client chooses the skin from the UUID, so this one line is the difference
        // between coming back to the same crowd and coming back to strangers.
        figure.setUUID(identity); // a new entity, not yet in the level: its UUID may still be set
        figure.moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, facing, 0.0f);
        // The whole point: no goals, so no goal checks, no navigation, no path search — a figure
        // costs a body and nothing else. It also stops them falling, which is why the ground is
        // checked before they are stood up.
        figure.setNoAi(true);
        figure.setSilent(true);
        figure.setInvulnerable(true);
        figure.setPersistenceRequired();
        figure.setCustomNameVisible(false);
        // Step 21: the year, as townspeople carry it. A client of a dedicated server dresses people by it (it has no
        // clock of its own), and a figure a player talks to keeps its clothes when it becomes a townsperson.
        figure.setEra("year_" + year);
        CompoundTag tag = figure.getPersistentData();
        tag.putBoolean(TAG_FIGURE, true);
        tag.putInt(TAG_FIGURE_YEAR, year);
        tag.putInt(TAG_FIGURE_SLOT, slot);
        tag.putInt(TAG_FIGURE_PLAN, planSize);
        tag.putInt(TAG_FIGURE_CHUNK_X, chunkX);
        tag.putInt(TAG_FIGURE_CHUNK_Z, chunkZ);
        tag.putInt(TAG_FIGURE_CX, heart.getX());
        tag.putInt(TAG_FIGURE_CZ, heart.getZ());
        return level.addFreshEntity(figure);
    }

    /**
     * Puts a figure back on its own place in its chunk's plan. Nothing is expected to move a figure,
     * but a player walking through the crowd shoves it a little, and a crowd that drifts apart over
     * an evening is the one thing a crowd must not do. The plan is rebuilt from the chunk and the
     * count the figure was placed with, so the place it goes back to is the place it came from.
     */
    private static void putBack(ServerLevel level, HumanNpcEntity figure, int year) {
        CompoundTag tag = figure.getPersistentData();
        if (tag.getInt(TAG_FIGURE_YEAR) != year) {
            return; // sweep's business, not this method's
        }
        int planSize = tag.getInt(TAG_FIGURE_PLAN);
        int slot = tag.getInt(TAG_FIGURE_SLOT);
        if (planSize <= 0 || slot < 0) {
            return;
        }
        int chunkX = tag.getInt(TAG_FIGURE_CHUNK_X);
        int chunkZ = tag.getInt(TAG_FIGURE_CHUNK_Z);
        List<CrowdDensity.Figure> plan = CrowdDensity.planSquare(
                chunkSeed(level, chunkX, chunkZ), ChunkPopulation.CHUNK_CAPACITY, CHUNK_PLAN_HALF);
        if (slot >= plan.size()) {
            return;
        }
        CrowdDensity.Figure place = plan.get(slot);
        double homeX = chunkX * CHUNK_SIDE + place.dx() + CHUNK_SIDE / 2.0d;
        double homeZ = chunkZ * CHUNK_SIDE + place.dz() + CHUNK_SIDE / 2.0d;
        double[] walked = standPoint(level, chunkX, chunkZ, slot, homeX, homeZ);
        double dx = figure.getX() - walked[0];
        double dz = figure.getZ() - walked[1];
        if (dx * dx + dz * dz > DRIFT_BLOCKS * DRIFT_BLOCKS) {
            // The walk's own point, so the drift correction and the day's route agree: a person out
            // for the day is not "drifted" and is not dragged back home for it.
            stepTowards(level, figure, walked[0], walked[1], figure.getBlockY(), figure.getYRot());
        }
    }

    /** The figures standing in a box around a point, alive, which are figures and nothing else. */
    private static List<HumanNpcEntity> figuresIn(ServerLevel level, BlockPos centre, double reach) {
        AABB box = new AABB(
                centre.getX() - reach, centre.getY() - reach, centre.getZ() - reach,
                centre.getX() + reach, centre.getY() + reach, centre.getZ() + reach);
        // Step 21: a figure somebody is talking to is never moved, re-placed or swept. (A figure a player talks to becomes
        // a townsperson at once, PresenceLocalSpawner.adoptFigure; this keeps any figure held all the same.)
        return level.getEntitiesOfClass(HumanNpcEntity.class, box, npc -> isFigure(npc) && npc.isAlive()
                && !com.eternalengine.knowledge.TalkHold.busy(npc));
    }

    /**
     * Every place in one chunk that is already taken: the figures standing in it and the living
     * people they are crowding around. The living are real people with names and answers, and a
     * figure must never be stood inside one.
     */
    private static List<double[]> occupiedInChunk(ServerLevel level, int chunkX, int chunkZ,
                                                  List<HumanNpcEntity> figures) {
        List<double[]> taken = new ArrayList<>();
        double centreX = chunkX * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
        double centreZ = chunkZ * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
        for (HumanNpcEntity figure : figures) {
            CompoundTag tag = figure.getPersistentData();
            if (tag.getInt(TAG_FIGURE_CHUNK_X) == chunkX && tag.getInt(TAG_FIGURE_CHUNK_Z) == chunkZ) {
                taken.add(new double[] {figure.getX() - centreX, figure.getZ() - centreZ});
            }
        }
        for (HumanNpcEntity npc : level.getEntitiesOfClass(HumanNpcEntity.class, occupiedBox(chunkX, chunkZ),
                npc -> !isFigure(npc) && npc.isAlive())) {
            taken.add(new double[] {npc.getX() - centreX, npc.getZ() - centreZ});
        }
        return taken;
    }

    /**
     * Where {@link #occupiedInChunk} looks for the living: the chunk and half a chunk around it, at every
     * height (cubic-chunk worlds go far up and down).
     *
     * <p>AABB takes (minX, minY, minZ, maxX, maxY, maxZ). Until 2026-10-12 this box was written as
     * (x, x, y, y, z, z), so it ran from the chunk east to x = 536,870,911. Minecraft's entity search walks a
     * box one 16-block slice of X at a time, and past x = 33,554,416 the slice key it packs into a long
     * overflows: every call walked one to two million empty slices (10 to 40 ms on the sandbox test server)
     * and then threw "Start element (9223367638808264704) is larger than end element
     * (-9223372036854775808)". The spawner failed every time a chunk needed figures, in every world, and no
     * figure was ever placed. {@code worldfill/probes/CrowdBoxProbe.java} checks this box against
     * Minecraft's own entity storage.
     */
    static AABB occupiedBox(int chunkX, int chunkZ) {
        double centreX = chunkX * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
        double centreZ = chunkZ * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
        return new AABB(centreX - CHUNK_SIDE, Integer.MIN_VALUE / 4, centreZ - CHUNK_SIDE,
                centreX + CHUNK_SIDE, Integer.MAX_VALUE / 4, centreZ + CHUNK_SIDE);
    }

    /** The crowd plan inside a chunk is a pure function of the world seed and the chunk. */
    private static long chunkSeed(ServerLevel level, int chunkX, int chunkZ) {
        return CrowdDensity.placeSeed(level.getSeed(), chunkX, chunkZ);
    }

    // ==================== config ====================

    /** Whether a crowd is drawn at all. Off means the old behaviour: the living few, and no more. */
    private static boolean drawnCrowdOn() {
        try {
            return com.eternalengine.config.EternalEngineConfig.COMMON.presenceDrawnCrowd.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /** The most figures drawn at once around one player. A client-side budget, not a server one. */
    private static int figuresCap() {
        try {
            return com.eternalengine.config.EternalEngineConfig.COMMON.presenceDrawnFiguresMax.get();
        } catch (Throwable t) {
            return CrowdDensity.MAX_FIGURES;
        }
    }

    /**
     * How much more than the average a chunk at the place's heart carries. 0 is the plain division.
     *
     * <p>Public because the spawner needs the same value to build the crowd manifest it sends, and a
     * second reader of the same setting is a second place for it to drift.
     */
    public static double concentration() {
        try {
            return com.eternalengine.config.EternalEngineConfig.COMMON.presenceCrowdConcentration.get();
        } catch (Throwable t) {
            return ChunkPopulation.DEFAULT_CONCENTRATION;
        }
    }

    /**
     * How much denser than their place's own built density the figures stand.
     *
     * <p>This is what {@code presenceCrowdConcentration} now means: it used to say how much more the
     * place's heart carried than its edge, and it is read as a multiplier instead — 40 (the default)
     * is the built density itself, 400 is ten times it, and 0 or anything negative is the built
     * density too. The population is not changed by it: a denser crowd covers less ground.
     */
    public static double crowdedness() {
        double c = concentration();
        if (c <= 0.0d) {
            return 1.0d;
        }
        return Math.max(0.25d, Math.min(10.0d, c / ChunkPopulation.DEFAULT_CONCENTRATION));
    }

    /** How far from a player the crowd is stood up at all, in blocks. */
    /**
     * How far from every player a figure stays standing (step 17): vanilla's 128 blocks, or more when the crowd is
     * stood up farther out (presenceCrowdInterestBlocks), so a figure is never taken where it would be stood up
     * again at once.
     */
    public static double farBlocks() {
        return Math.max(128.0d, interestBlocks() + CHUNK_SIDE * 4.0d);
    }

    private static double interestBlocks() {
        try {
            return com.eternalengine.config.EternalEngineConfig.COMMON.presenceCrowdInterestBlocks.get();
        } catch (Throwable t) {
            return 64;
        }
    }

}

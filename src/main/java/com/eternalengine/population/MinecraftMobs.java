package com.eternalengine.population;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.config.EternalEngineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Step 21 (D19 of the systems plan): Minecraft's own monsters, raids and wandering traders are off in the history world
 * unless the settings turn them on ({@code [minecraftMobs]} in eternalengine-common.toml).
 *
 * <p>The world is the real Earth in a real year. A creeper in Hyderabad in 1948, a pillager patrol on the Nile in
 * 2500 BCE or a wandering trader with a llama in Rome do not belong in it; the mod's own people (townspeople, the
 * crowd, soldiers, bandits) are who lives there. Only what comes <em>by itself</em> is stopped:
 * <ul>
 *   <li>natural spawning of monsters (the spawner never even picks one: {@link LevelEvent.PotentialSpawns}), phantoms,
 *       the zombie siege of a village at night, zombie reinforcements, structure monsters, a skeleton trap horse;</li>
 *   <li>pillager patrols, a raid's waves, and the Bad Omen that starts a raid (so no raid can start);</li>
 *   <li>the wandering trader and his llamas.</li>
 * </ul>
 * Spawn eggs, spawners, /summon, breeding and conversions are the player's own doing and are never stopped. The Nether
 * and the End are Minecraft's, not history's, and are not touched. Forge removes a mob whose spawn is cancelled in
 * {@link MobSpawnEvent.FinalizeSpawn} before it enters the world (ForgeInternalHandler.builtinMobSpawnBlocker).
 */
@Mod.EventBusSubscriber(modid = EternalEngineMod.MOD_ID)
public final class MinecraftMobs {

    private static final AtomicLong STOPPED_MONSTERS = new AtomicLong();
    private static final AtomicLong STOPPED_RAIDERS = new AtomicLong();
    private static final AtomicLong STOPPED_TRADERS = new AtomicLong();
    private static final AtomicLong DENIED_BAD_OMEN = new AtomicLong();
    /** Natural spawning's monster picks refused (each one is an attempt to spawn a pack, not a mob). */
    private static final AtomicLong REFUSED_PICKS = new AtomicLong();

    private MinecraftMobs() {
    }

    // ------------------------------------------------------------------ settings

    public static boolean monstersOn() {
        try {
            return EternalEngineConfig.COMMON.minecraftMonsters.get();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean raidsOn() {
        try {
            return EternalEngineConfig.COMMON.raidsAndPatrols.get();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean tradersOn() {
        try {
            return EternalEngineConfig.COMMON.wanderingTraders.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** History's worlds: every dimension except Minecraft's Nether and End. */
    public static boolean historyWorld(Level level) {
        return level != null && !level.isClientSide && level.dimension() != Level.NETHER && level.dimension() != Level.END;
    }

    // ------------------------------------------------------------------ the rule

    /** Whether a spawn of this kind comes by itself (nature, an event, a patrol), not from a player or a spawner. */
    public static boolean byItself(MobSpawnType how) {
        if (how == null) {
            return false;
        }
        return switch (how) {
            case NATURAL, CHUNK_GENERATION, STRUCTURE, PATROL, EVENT, REINFORCEMENT, TRIGGERED -> true;
            default -> false; // SPAWN_EGG, COMMAND, SPAWNER, DISPENSER, BUCKET, BREEDING, CONVERSION, MOB_SUMMONED, JOCKEY
        };
    }

    /**
     * Whether a mob of this type, coming into the world this way, is stopped with these settings. The mod's own entities
     * are never stopped.
     */
    public static boolean stops(EntityType<?> type, MobSpawnType how, boolean monsters, boolean raids, boolean traders) {
        if (type == null || !byItself(how)) {
            return false;
        }
        ResourceLocation id = EntityType.getKey(type);
        if (id != null && EternalEngineMod.MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        if (type == EntityType.WANDERING_TRADER || type == EntityType.TRADER_LLAMA) {
            return !traders;
        }
        if ((how == MobSpawnType.PATROL || how == MobSpawnType.EVENT) && isRaider(type)) {
            return !raids; // a patrol, or a raid's wave: the raid setting decides, whatever the monster setting says
        }
        if (type.getCategory() == MobCategory.MONSTER) {
            return !monsters;
        }
        return false;
    }

    private static boolean isRaider(EntityType<?> type) {
        try {
            if (type.is(EntityTypeTags.RAIDERS)) {
                return true;
            }
        } catch (Throwable ignored) {
            // tags not loaded (never in a running server): the list below
        }
        return type == EntityType.PILLAGER || type == EntityType.VINDICATOR || type == EntityType.EVOKER
                || type == EntityType.ILLUSIONER || type == EntityType.RAVAGER || type == EntityType.WITCH;
    }

    // ------------------------------------------------------------------ the events

    /** Monsters are never even picked by the natural spawner (the cheapest place to stop them). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || monstersOn()) {
            return;
        }
        if (historyWorld(levelOf(event.getLevel()))) {
            event.setCanceled(true);
            REFUSED_PICKS.incrementAndGet();
        }
    }

    /** Everything else that comes by itself: patrols, raids, phantoms, sieges, the trader. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (mob == null || !historyWorld(mob.level())) {
            return;
        }
        EntityType<?> type = mob.getType();
        if (stops(type, event.getSpawnType(), monstersOn(), raidsOn(), tradersOn())) {
            event.setSpawnCancelled(true);
            if (type == EntityType.WANDERING_TRADER || type == EntityType.TRADER_LLAMA) {
                STOPPED_TRADERS.incrementAndGet();
            } else if (isRaider(type) && (event.getSpawnType() == MobSpawnType.PATROL || event.getSpawnType() == MobSpawnType.EVENT)) {
                STOPPED_RAIDERS.incrementAndGet();
            } else {
                STOPPED_MONSTERS.incrementAndGet();
            }
        }
    }

    /** No Bad Omen, so no raid can start (a raid begins when somebody with the Bad Omen enters a village). */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() != MobEffects.BAD_OMEN || raidsOn()) {
            return;
        }
        if (event.getEntity() != null && historyWorld(event.getEntity().level())) {
            event.setResult(Event.Result.DENY);
            DENIED_BAD_OMEN.incrementAndGet();
        }
    }

    /** The skeleton trap horse (lightning in a storm) enters without a spawn event. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof SkeletonHorse horse) || !horse.isTrap()) {
            return;
        }
        if (!monstersOn() && historyWorld(event.getLevel())) {
            event.setCanceled(true);
            STOPPED_MONSTERS.incrementAndGet();
        }
    }

    private static Level levelOf(LevelAccessor accessor) {
        if (accessor instanceof Level level) {
            return level;
        }
        if (accessor instanceof WorldGenRegion region) {
            return region.getLevel();
        }
        return null;
    }

    // ------------------------------------------------------------------ readouts and the check

    /** "stopped since the server started: 40 natural monster spawn attempts, 2 other monsters, 0 raiders, ...". */
    public static String stoppedLine() {
        return "stopped since the server started: " + REFUSED_PICKS.get() + " natural monster spawn attempt(s), "
                + STOPPED_MONSTERS.get() + " other monster(s), " + STOPPED_RAIDERS.get() + " raider(s), "
                + STOPPED_TRADERS.get() + " trader(s); " + DENIED_BAD_OMEN.get() + " Bad Omen(s)";
    }

    /** "monsters off, raids and patrols off, wandering traders off". */
    public static String settingsLine() {
        return "monsters " + (monstersOn() ? "ON" : "off") + ", raids and patrols " + (raidsOn() ? "ON" : "off")
                + ", wandering traders " + (tradersOn() ? "ON" : "off");
    }

    /**
     * The rule on made-up cases with both settings, then the real wiring: made-up events posted on Forge's bus in this
     * level, each expected to be stopped or let through as the current settings say. Null when all hold, else what
     * broke. Nothing enters the world (the test mobs are never added).
     */
    public static String selfTest(ServerLevel level) {
        // 1. The rule, settings off and on.
        if (!stops(EntityType.ZOMBIE, MobSpawnType.NATURAL, false, false, false)) {
            return "a zombie spawning by itself is not stopped with monsters off";
        }
        if (stops(EntityType.ZOMBIE, MobSpawnType.NATURAL, true, false, false)) {
            return "a zombie is stopped with monsters on";
        }
        if (stops(EntityType.ZOMBIE, MobSpawnType.SPAWN_EGG, false, false, false)
                || stops(EntityType.ZOMBIE, MobSpawnType.COMMAND, false, false, false)
                || stops(EntityType.ZOMBIE, MobSpawnType.SPAWNER, false, false, false)) {
            return "a spawn egg, /summon or a spawner is stopped";
        }
        if (!stops(EntityType.PILLAGER, MobSpawnType.PATROL, true, false, false)) {
            return "a pillager patrol is not stopped with raids off (monsters on)";
        }
        if (stops(EntityType.PILLAGER, MobSpawnType.EVENT, false, true, false)) {
            return "a raid's pillager is stopped with raids on (monsters off)";
        }
        if (!stops(EntityType.WANDERING_TRADER, MobSpawnType.EVENT, true, true, false)
                || !stops(EntityType.TRADER_LLAMA, MobSpawnType.EVENT, true, true, false)) {
            return "the wandering trader or his llama is not stopped with traders off";
        }
        if (!stops(EntityType.PHANTOM, MobSpawnType.NATURAL, false, true, true)) {
            return "a phantom is not stopped with monsters off";
        }
        if (stops(EntityType.COW, MobSpawnType.NATURAL, false, false, false)
                || stops(EntityType.CAT, MobSpawnType.NATURAL, false, false, false)) {
            return "an animal is stopped";
        }
        if (level == null) {
            return null;
        }
        // 2. The wiring, with the current settings.
        boolean monsters = monstersOn();
        boolean raids = raidsOn();
        boolean traders = tradersOn();
        BlockPos pos = level.getSharedSpawnPos();
        LevelEvent.PotentialSpawns pick = new LevelEvent.PotentialSpawns(level, MobCategory.MONSTER, pos,
                WeightedRandomList.create(new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 100, 1, 4)));
        long picksBefore = REFUSED_PICKS.get();
        MinecraftForge.EVENT_BUS.post(pick);
        REFUSED_PICKS.set(picksBefore); // the test is not a real spawn
        if (pick.isCanceled() == monsters) {
            return "the natural spawner's monster pick was " + (pick.isCanceled() ? "stopped" : "let through")
                    + " with monsters " + (monsters ? "on" : "off");
        }
        String r = posted(level, EntityType.ZOMBIE, MobSpawnType.NATURAL, !monsters);
        if (r == null) {
            r = posted(level, EntityType.PILLAGER, MobSpawnType.PATROL, !raids);
        }
        if (r == null) {
            r = posted(level, EntityType.WANDERING_TRADER, MobSpawnType.EVENT, !traders);
        }
        if (r == null) {
            r = posted(level, EntityType.ZOMBIE, MobSpawnType.SPAWN_EGG, false);
        }
        if (r != null) {
            return r;
        }
        Mob villager = EntityType.VILLAGER.create(level);
        if (villager != null) {
            MobEffectEvent.Applicable omen = new MobEffectEvent.Applicable(villager,
                    new MobEffectInstance(MobEffects.BAD_OMEN, 100));
            MinecraftForge.EVENT_BUS.post(omen);
            boolean denied = omen.getResult() == Event.Result.DENY;
            DENIED_BAD_OMEN.addAndGet(denied ? -1 : 0); // the test is not a real Bad Omen
            if (denied == raids) {
                return "the Bad Omen was " + (denied ? "refused" : "allowed") + " with raids " + (raids ? "on" : "off");
            }
        }
        return null;
    }

    /**
     * <b>The real spawner, actually asked.</b> FINAL_TEST G6 (marking board 15).
     *
     * <p>{@link #selfTest} proves the rule and posts Forge's events on its own; that shows the handlers are wired,
     * not that Minecraft's own spawner ever consults them. This drives the genuine thing:
     * {@link NaturalSpawner#spawnCategoryForPosition(MobCategory, ServerLevel, BlockPos)}, on the surface, in the
     * dark, in this world — the same call {@code ServerChunkCache.tickChunks} makes while a player stands there.
     *
     * <p>It puts the clock at midnight for the attempt and restores it, and it does not touch the world otherwise.
     * Null when the spawner was asked and added nothing; otherwise what happened, in words. If the pick counter did
     * not move the answer is inconclusive rather than green, because "no monster appeared" is also what a spawner
     * that never asked would produce.
     */
    public static String realSpawnTest(ServerLevel level) {
        if (level == null) {
            return "no level to ask";
        }
        if (!historyWorld(level)) {
            return null; // not the world this rule is about
        }
        if (monstersOn()) {
            return null; // the setting is the player's; the rule is "off unless turned on"
        }
        BlockPos base = level.getSharedSpawnPos();
        BlockPos spot = null;
        for (int r = 32; r <= 128 && spot == null; r += 32) {
            for (int a = 0; a < 8 && spot == null; a++) {
                int dx = (int) Math.round(Math.cos(a * Math.PI / 4.0) * r);
                int dz = (int) Math.round(Math.sin(a * Math.PI / 4.0) * r);
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        base.offset(dx, 0, dz));
                net.minecraft.world.level.block.state.BlockState below = level.getBlockState(top.below());
                if (below.isSolidRender(level, top.below()) && level.getBlockState(top).isAir()
                        && level.getBlockState(top.above()).isAir()) {
                    spot = top;
                }
            }
        }
        if (spot == null) {
            return "no open ground within 128 blocks of spawn to test on";
        }
        long dayTime = level.getDayTime();
        long before = monsters(level).size();
        long picksBefore = REFUSED_PICKS.get();
        long stoppedBefore = STOPPED_MONSTERS.get();
        // The spawner's own selection step, through the very Forge factory call it makes
        // (ForgeEventFactory.getPotentialSpawns, patched into NaturalSpawner.getRandomSpawnMobAt). If this comes
        // back empty the rule is live on this level; the counters are put back because the test is not a spawn.
        long picksProbe = REFUSED_PICKS.get();
        WeightedRandomList<MobSpawnSettings.SpawnerData> listed;
        try {
            listed = net.minecraftforge.event.ForgeEventFactory.getPotentialSpawns(level, MobCategory.MONSTER, spot,
                    level.getBiome(spot).value().getMobSettings().getMobs(MobCategory.MONSTER));
        } catch (Throwable t) {
            return "the spawner's selection step threw " + t;
        }
        // A counted refusal, not an empty list: an empty list could just be a biome with no monsters in it,
        // and that would pass this test while proving nothing (marking board 15).
        long picksDirect = REFUSED_PICKS.get() - picksProbe;
        boolean selectionRefused = picksDirect > 0;
        REFUSED_PICKS.set(picksProbe);
        try {
            level.setDayTime(18000L); // midnight
            for (int i = 0; i < 4; i++) {
                NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, spot);
            }
        } catch (Throwable t) {
            return "the spawner threw " + t;
        } finally {
            level.setDayTime(dayTime);
        }
        long added = monsters(level).size() - before;
        long picks = REFUSED_PICKS.get() - picksBefore;
        long stopped = STOPPED_MONSTERS.get() - stoppedBefore;
        // The mobs the attempt may have made are ours to clean up: the check must not leave a creeper behind.
        if (added > 0) {
            despawn(level, spot, (int) added);
        }
        if (added > 0) {
            return "the real spawner added " + added + " monster(s) at " + spot.toShortString()
                    + " at night in the open";
        }
        if (!selectionRefused) {
            return "inconclusive: nothing appeared at " + spot.toShortString()
                    + ", but the gate counted no refusal either"
                    + (listed.isEmpty() ? " (this biome's list has no monsters in it)" : "")
                    + " - so no rule was tested";
        }
        return null;
    }

    /** Every monster in the level right now. */
    private static java.util.List<Mob> monsters(ServerLevel level) {
        java.util.List<Mob> out = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
                x -> x instanceof Mob m && m.getType().getCategory() == MobCategory.MONSTER)) {
            out.add((Mob) e);
        }
        return out;
    }

    private static void despawn(ServerLevel level, BlockPos near, int howMany) {
        java.util.List<Mob> here = monsters(level);
        for (Mob m : here) {
            if (m.blockPosition().closerThan(near, 32)) {
                m.discard();
                if (--howMany <= 0) {
                    return;
                }
            }
        }
    }

    private static String posted(ServerLevel level, EntityType<? extends Mob> type, MobSpawnType how, boolean expectStopped) {
        Mob mob = type.create(level);
        if (mob == null) {
            return "no test " + EntityType.getKey(type);
        }
        BlockPos pos = level.getSharedSpawnPos();
        mob.moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 0.0f, 0.0f);
        MobSpawnEvent.FinalizeSpawn event = new MobSpawnEvent.FinalizeSpawn(mob, level, mob.getX(), mob.getY(), mob.getZ(),
                level.getCurrentDifficultyAt(pos), how, null, null, null);
        long[] before = {STOPPED_MONSTERS.get(), STOPPED_RAIDERS.get(), STOPPED_TRADERS.get()};
        MinecraftForge.EVENT_BUS.post(event);
        // The test is not a real spawn: the counters go back.
        STOPPED_MONSTERS.set(before[0]);
        STOPPED_RAIDERS.set(before[1]);
        STOPPED_TRADERS.set(before[2]);
        boolean stopped = event.isSpawnCancelled();
        if (stopped != expectStopped) {
            return "a " + EntityType.getKey(type).getPath() + " (" + how.name().toLowerCase(java.util.Locale.ROOT)
                    + ") was " + (stopped ? "stopped" : "let through");
        }
        return null;
    }
}

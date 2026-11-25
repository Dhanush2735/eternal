package com.eternalengine.villager;

import com.eternalengine.civilization.CivilizationManager;
import com.eternalengine.civilization.EventNews;
import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.civilization.HistoryGazetteer;
import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.network.EternalEnginePacketHandler;
import com.eternalengine.network.packets.CrowdManifestPacket;
import com.eternalengine.people.Scenes;
import com.eternalengine.population.ChunkPopulation;
import com.eternalengine.population.CrowdDensity;
import com.eternalengine.population.CrowdFigures;
import com.eternalengine.population.PopulationResolver;
import com.eternalengine.population.SettlementDensity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two local systems:
 * <ol>
 *   <li>Town occupancy — people if a place is occupied this year.</li>
 *   <li>Event scene — extra civilians who spread that year's news. Never attackers.</li>
 * </ol>
 *
 * <p>This is the original spawner. Its two systems, its roles, its tags, its per-year lifecycle and
 * its caps — four townsfolk, the scene's own role list — are unchanged. Two things did change, and
 * both now follow vanilla mob spawning instead of being measured from the player:
 *
 * <table>
 *   <tr><th>vanilla</th><th>here</th></tr>
 *   <tr><td>nothing spawns within 24 blocks of a player</td><td>{@link PresenceSpawning#MIN_SPAWN_BLOCKS}</td></tr>
 *   <tr><td>spawn distance is 128 blocks</td><td>{@link PresenceSpawning#MAX_SPAWN_BLOCKS}</td></tr>
 *   <tr><td>spawn position must be a loaded chunk with valid ground</td><td>{@link #isSpawnable}</td></tr>
 *   <tr><td>beyond 128 blocks, gone now</td><td>{@link #vanillaDespawn}</td></tr>
 *   <tr><td>past 32 blocks and 600 ticks away, one chance in 800 per tick</td><td>{@link PresenceSpawning#shouldDespawn}</td></tr>
 * </table>
 *
 * <p>The population resolver is consulted for exactly one thing: <b>how many</b>. It answers how many
 * people live in this place in this year, {@link SettlementDensity} turns that into a rendered count,
 * and this class places that many — in knots of two to four, the way vanilla spawns a pack, at
 * positions vanilla would allow. Everything else about them is this class's own: the composition of
 * roles, the tags, the per-year lifecycle and the original offsets.
 */
public final class PresenceLocalSpawner {

    public static final String TAG_PRESENCE = "eternalengine.presence";
    public static final String TAG_EVENT = "eternalengine.eventScene";
    public static final String TAG_YEAR = "eternalengine.presenceYear";

    /** Ticks this NPC has spent outside the 32-block bubble — vanilla's despawn delay. */
    private static final String TAG_AWAY_TICKS = "eternalengine.presenceAwayTicks";

    /** Where a person's own walk starts from: where they were first stood up. */
    /**
     * Step 21: a crowd figure a player talked to, now a townsperson. They belong to the district (and are remembered
     * there), but stand where they were met, so the district's edge does not take them away.
     */
    public static final String TAG_MET = "eternalengine.metByPlayer";

    /**
     * Step 21 (known problem 5, "after a long jump townspeople come back slowly"): why spots for townspeople were refused
     * in the last cycle: [0] the chunk is not loaded, [1] inside a player's 24-block bubble, [2] no ground that can hold
     * somebody, [3] too close to another group placed in the same cycle. Counted only while townspeople are placed.
     */
    private static final int[] TOWN_REFUSED = new int[4];
    private static boolean countingTown;
    private static volatile String lastTownCycle = "";

    /** Step 21: the last cycle's townspeople line ("have 3 of 21; stood up 8 ..."), for the sandbox and the log. */
    public static String lastTownCycle() {
        return lastTownCycle;
    }

    private static void townLine(ServerPlayer player, String line) {
        lastTownCycle = line;
        try {
            if (com.eternalengine.config.EternalEngineConfig.COMMON.debugLogging.get()) {
                LOGGER.info("[Spawner] Townspeople around {}: {}", player.getGameProfile().getName(), line);
            }
        } catch (Throwable ignored) {
            // the config is not loaded (never in a running game)
        }
    }

    private static final String TAG_HOME_X = "eternalengine.homeX";
    private static final String TAG_HOME_Z = "eternalengine.homeZ";

    /** How far out a living person's own walk goes. */
    private static final double ROUTINE_BLOCKS = 6.0d;

    /** The district this NPC belongs to, so it can be kept in it. */
    private static final String TAG_DISTRICT_X = "eternalengine.presenceDistrictX";
    private static final String TAG_DISTRICT_Z = "eternalengine.presenceDistrictZ";

    // what the client was last told about the crowd in front of this player, so it is told again
    // only when the answer has changed
    private static final String TAG_CROWD_CHUNK_X = "eternalengine.crowdSentChunkX";
    private static final String TAG_CROWD_CHUNK_Z = "eternalengine.crowdSentChunkZ";
    private static final String TAG_CROWD_YEAR = "eternalengine.crowdSentYear";
    private static final String TAG_CROWD_HEART_X = "eternalengine.crowdSentHeartX";
    private static final String TAG_CROWD_HEART_Z = "eternalengine.crowdSentHeartZ";
    private static final String TAG_CROWD_POPULATION = "eternalengine.crowdSentPopulation";

    /** How far past the district's edge somebody may stroll before they are replaced inside it. */
    private static final int DISTRICT_MARGIN = 16;

    private static final int PERIOD_TICKS = 40;
    /**
     * A town NPC that is not already speaking starts a line of town talk with chance 1 in this
     * many per spawner cycle (every 40 ticks): about once every 40 seconds each, so a square of
     * ten people has someone talking every few seconds without everyone talking at once.
     */
    private static final int TOWN_TALK_ONE_IN = 20;
    /** Step 19d: a town crier calls out the news about one cycle in six (every few seconds). */
    private static final int CRIER_ONE_IN = 6;

    // Step 18e: where a slow cycle's time goes, written to the log. The user's game stood still about 8 seconds right
    // after joining, and the world-system timing named this spawner (830 ms in one cycle on the sandbox server when a
    // player arrives). One line per slow cycle, at most once a minute.
    private static final org.slf4j.Logger LOGGER = com.eternalengine.EternalEngineMod.getLogger();
    private static final long SLOW_CYCLE_MS = 250L;
    private static final String[] PHASES = {"finding the place", "the people already here", "the district",
            "how many people live here", "keeping people in the district", "townspeople", "the drawn crowd",
            "an event scene"};
    private final long[] phaseNanos = new long[PHASES.length];
    private int phase;
    private long phaseMark;
    private long lastSlowLog;
    private int crowdChanged;
    /** Townspeople and scene people stood up in this cycle (server thread only). */
    private static int stoodUp;
    // Turn 89: the ground look-ups of this cycle, for the slow-cycle line. The user's 7.5-second freeze was spent
    // in them: each one asked the land-cover map too and could wait half a second (fixed in TerraBridge).
    private static int groundLookups;
    private static long groundNanos;
    private static long groundSlowestNanos;
    /** Turn 89: where the townspeople part of a cycle goes: the roles, the crowd plan, finding places, standing up. */
    private static final long[] townNanos = new long[4];
    private static final String[] TOWN_PARTS = {"the roles", "the crowd plan", "finding places", "standing people up"};
    /**
     * Turn 89: one cycle stands people up for at most this long; the rest come in the next cycles, two seconds
     * apart. The first pack is always placed, so a place still fills up; a slow look-up then costs one pack's
     * wait, never a whole crowd's.
     */
    private static final long TOWNSPEOPLE_BUDGET_NS = 50_000_000L;

    public void tick(ServerLevel level, int year, VillagerManager villagers, CivilizationManager civs) {
        if (level == null || villagers == null || level.getGameTime() % PERIOD_TICKS != 0) {
            return;
        }
        if (!com.eternalengine.civilization.HistoryChecks.historyReady()) {
            return; // the history data is still loading in the background (step 17): no freeze, people come next cycle
        }
        for (ServerPlayer player : level.players()) {
            java.util.Arrays.fill(phaseNanos, 0L);
            phase = 0;
            phaseMark = System.nanoTime();
            stoodUp = 0;
            crowdChanged = 0;
            groundLookups = 0;
            groundNanos = 0L;
            groundSlowestNanos = 0L;
            java.util.Arrays.fill(townNanos, 0L);
            tickAround(level, player, year, villagers, civs);
            phase(phase);
            reportSlowCycle(player);
        }
        forgetFar(level, year);
    }

    /** The time since the last mark goes to the current part; {@code next} is the part that starts now. */
    private void phase(int next) {
        long now = System.nanoTime();
        phaseNanos[phase] += now - phaseMark;
        phaseMark = now;
        phase = next;
    }

    private void reportSlowCycle(ServerPlayer player) {
        long total = 0L;
        for (long n : phaseNanos) {
            total += n;
        }
        long ms = total / 1_000_000L;
        long now = System.currentTimeMillis();
        if (ms < SLOW_CYCLE_MS || now - lastSlowLog < 60_000L) {
            return;
        }
        lastSlowLog = now;
        StringBuilder parts = new StringBuilder();
        for (int i = 0; i < PHASES.length; i++) {
            parts.append(i == 0 ? "" : ", ").append(PHASES[i]).append(' ').append(phaseNanos[i] / 1_000_000L).append(" ms");
        }
        StringBuilder town = new StringBuilder();
        for (int i = 0; i < TOWN_PARTS.length; i++) {
            town.append(i == 0 ? "" : ", ").append(TOWN_PARTS[i]).append(' ').append(townNanos[i] / 1_000_000L)
                    .append(" ms");
        }
        LOGGER.warn("[Spawner] One cycle around {} took {} ms: {}. Townspeople: {}. Stood up {} townspeople; {} crowd"
                + " figures stood up or moved; {} ground look-ups took {} ms (the slowest {} ms).",
                player.getGameProfile().getName(), ms, parts, town, stoodUp, crowdChanged, groundLookups,
                groundNanos / 1_000_000L, groundSlowestNanos / 1_000_000L);
    }

    /**
     * Once a cycle, over everybody this spawner stood up anywhere in the level (step 17). Past 128 blocks from
     * every player a townsperson or event scene is written into the place's memory and taken out (vanilla's instant
     * rule; vanilla's own despawn no longer touches them, see HumanNpcEntity.removeWhenFarAway), and a crowd figure
     * is taken out (its place gives it back the same identity when a player returns). The per-player pass only
     * sees people within reach of a player; this one keeps nobody standing, thinking or piling up beyond it.
     */
    private static void forgetFar(ServerLevel level, int year) {
        List<HumanNpcEntity> owned = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof HumanNpcEntity npc && npc.isAlive()
                    && (isPresenceNpc(npc) || isEventNpc(npc) || CrowdFigures.isFigure(npc))) {
                owned.add(npc);
            }
        }
        double figureFar = CrowdFigures.farBlocks();
        for (HumanNpcEntity npc : owned) {
            double distanceSq = nearestPlayerDistanceSq(level, npc);
            if (CrowdFigures.isFigure(npc)) {
                if (distanceSq > figureFar * figureFar && !com.eternalengine.knowledge.TalkHold.busy(npc)) {
                    npc.discard();
                }
            } else if (PresenceSpawning.farBeyond(distanceSq)) {
                remember(level, npc, year);
            }
        }
    }

    private void tickAround(
            ServerLevel level,
            ServerPlayer player,
            int year,
            VillagerManager villagers,
            CivilizationManager civs
    ) {
        var geo = TerraCoordinateHelper.toGeoPoint(player.blockPosition());
        HistoricalPresence.Result result = com.eternalengine.core.WorldQuery.presence(civs,
                com.eternalengine.core.WorldKey.at(player.level(), player.blockPosition(), year));
        phase(1);
        // The query has to reach the whole spawn distance: a person may stand up to 128 blocks away,
        // and an entity outside the query is one this system can neither count, keep nor remove.
        AABB box = player.getBoundingBox().inflate(spawnDistance() + 8.0d);
        List<HumanNpcEntity> nearby = level.getEntitiesOfClass(HumanNpcEntity.class, box, HumanNpcEntity::isAlive);
        String era = "year_" + year;

        // Vanilla despawn, ahead of everything else, so a person the player walked away from goes the
        // way a mob would instead of standing in the world forever.
        vanillaDespawn(level, nearby, year);
        phase(2);

        // Where this place's crowd belongs. The site's own centre when the gazetteer names one in
        // reach, otherwise a grid-snapped district near the player. Both systems below use it, so a
        // town and an event scene gather in the same place instead of each clustering on the player.
        BlockPos district = districtCenter(level, player, result);
        phase(3);

        // How many people live here this year. Resolved once, before either layer uses it: it is the
        // number of people in the place, and both the living few and the drawn crowd are readings of
        // it. The resolver supplies the number and nothing else — the placing, the tagging and the
        // capping of both layers are the spawner's and CrowdFigures' own business.
        PopulationResolver.Result population = PopulationResolver.get().resolve(
                result.site().map(h -> h.site().id()).orElse(null),
                result.lat(), result.lon(), year, result.rank());
        phase(4);

        // Keep the crowd in its district before counting what is here: a wanderer out past the edge
        // is gone, and the count loop below replaces them inside.
        enforceDistrict(level, nearby, year);
        settleDistant(level, nearby);

        // Nameplates are part of why a crowd reads as countable: a floating name over every head
        // turns "some people over there" into a list you can read off one by one. Vanilla shows a
        // name when you are close enough to care and no further; so does this. It only ever touches
        // the NPCs this spawner owns.
        applyNameTagDistance(level, nearby);

        phase(5);
        if (!result.occupied()) {
            for (HumanNpcEntity npc : nearby) {
                if (isPresenceNpc(npc) && !isEventNpc(npc)) {
                    remember(level, npc, year);
                }
            }
        } else if (district != null) {
            ensureTownNpcs(level, villagers, nearby, result, population, year, era, player, district);
        } else {
            townLine(player, "no district yet: none of the spots tried 24 to " + spawnDistance()
                    + " blocks away is in a loaded chunk");
        }
        if (!result.occupied()) {
            townLine(player, "nobody lives here in " + year);
        }
        phase(6);

        // The crowd, drawn. A group of forty is not a crowd; the crowd is the figures standing
        // around them, with their AI off. The population is divided over the chunks of the place's
        // own area, so the crowd covers the whole settlement and the player meets people wherever
        // they walk inside it — not only in one square. Sweep first: figures from another year, or
        // outside every player's neighbourhood, go the way the living people go. Then stand up the
        // ones this view is missing, thirty-two at a time, never inside a player's bubble.
        // How far this place extends: the area its people are divided over. Same data as the number.
        double placeRadiusKm = PopulationResolver.get().radiusKm(
                result.site().map(h -> h.site().id()).orElse(null), result.lat(), result.lon());
        CrowdFigures.sweep(level, player, year, result.occupied(), crowdInterestBlocks());
        if (result.occupied() && district != null) {
            crowdChanged = CrowdFigures.ensure(level, player, district, result.rank(), population.population(),
                    placeRadiusKm, year, crowdInterestBlocks(), result.settled());
            sendCrowdManifest(level, player, district, result.rank(), population.population(),
                    placeRadiusKm, year);
        }

        phase(7);
        if (result.event().isEmpty()) {
            for (HumanNpcEntity npc : nearby) {
                if (isEventNpc(npc)) {
                    remember(level, npc, year);
                }
            }
            return;
        }

        HistoryGazetteer.Event event = result.event().get().event();
        String rumor = EventNews.rumor(event, year);
        String civId = result.occupied() ? result.spawnCivId() : "witness";
        // Step 39 (D9): the scene is not four keyword tests on the event's effect string. It is
        // what is on here and now, cast from the people this place and year actually has. The
        // clock is the level's own, and the crowd is the settlement's own number.
        long ticks = level.getDayTime();
        Scenes.Where here = Scenes.Where.of(result.lat(), result.lon(), year)
                .at((int) ((ticks % 24000L) / 1000L))
                .town(population.population() > 1000)
                .folk((int) Math.min(Integer.MAX_VALUE, Math.max(1L, population.population())))
                .holding(Scenes.wordsOf(event.kind(), event.effect(), event.name()));
        Scenes.Occasion occasion = Scenes.occasion(here);
        if (occasion != null) {
            townLine(player, occasion.line());
        } else {
            townLine(player, "no scene here: " + Scenes.find(eventSceneId(here)) + " could not "
                    + "be cast from " + population.population() + " people");
        }
        String[] sceneRoles = sceneRoles(occasion);
        Set<String> have = new HashSet<>();
        int eventCount = 0;
        for (HumanNpcEntity npc : nearby) {
            if (!isEventNpc(npc) || !npc.isAlive()) {
                continue;
            }
            if (npc.getPersistentData().getInt(TAG_YEAR) != year) {
                remember(level, npc, year);
                continue;
            }
            eventCount++;
            have.add(npc.getRole() == null ? "" : npc.getRole().toLowerCase(java.util.Locale.ROOT));
            if (!npc.isSpeaking() && !rumor.isBlank()) {
                npc.setSpeech(rumor);
            }
        }
        // The scene's own ground, inside the district and chosen the way vanilla chooses a spawn
        // position. Null means no candidate qualified this cycle; then the scene waits for the next
        // one, as a mob would.
        if (district == null) {
            return;
        }
        BlockPos anchor = spotInDistrict(level, district, eventCount + 1);
        if (anchor == null) {
            return;
        }
        for (int i = 0; i < sceneRoles.length && eventCount < sceneRoles.length; i++) {
            String role = sceneRoles[i];
            String key = role.toLowerCase(java.util.Locale.ROOT);
            if (have.contains(key)) {
                continue;
            }
            BlockPos pos = anchor.offset((i % 2) * -3 - 2, 0, (i / 2) * 3 + 3);
            java.util.UUID uuid = standPerson(villagers, level, civId, role, pos, anchor, year);
            if (uuid == null) {
                continue;
            }
            var ent = level.getEntity(uuid);
            if (ent instanceof HumanNpcEntity npc) {
                npc.setEra(era);
                npc.setSpeech(rumor);
                CompoundTag tag = npc.getPersistentData();
                tag.putBoolean(TAG_EVENT, true);
                tag.putInt(TAG_YEAR, year);
                tag.putInt(TAG_HOME_X, pos.getX());
                tag.putInt(TAG_HOME_Z, pos.getZ());
                have.add(key);
                eventCount++;
            }
        }
    }

    private void ensureTownNpcs(
            ServerLevel level,
            VillagerManager villagers,
            List<HumanNpcEntity> nearby,
            HistoricalPresence.Result result,
            PopulationResolver.Result population,
            int year,
            String era,
            ServerPlayer player,
            BlockPos district
    ) {
        Map<String, Integer> haveByRole = new HashMap<>();
        int existing = 0;
        for (HumanNpcEntity npc : nearby) {
            if (!isPresenceNpc(npc) || isEventNpc(npc)) {
                continue;
            }
            if (npc.getPersistentData().getInt(TAG_YEAR) != year) {
                remember(level, npc, year);
                continue;
            }
            existing++;
            haveByRole.merge(roleKey(npc), 1, Integer::sum);
            // Step 19d: the town crier calls out the day's news now and then.
            if (!npc.isSpeaking() && com.eternalengine.news.NewsEras.TOWN_CRIER.equalsIgnoreCase(npc.getRole())
                    && level.getRandom().nextInt(CRIER_ONE_IN) == 0) {
                String call = com.eternalengine.news.NewsService.crierCall(result.lat(), result.lon(),
                        com.eternalengine.knowledge.KnowledgeDate.today(), level.getGameTime() / 1200L);
                if (!call.isBlank()) {
                    npc.setSpeech(call);
                }
                continue;
            }
            if (com.eternalengine.news.NewsEras.TOWN_CRIER.equalsIgnoreCase(npc.getRole())) {
                continue; // a crier calls out the news, not town talk
            }
            // Town talk: now and then a townsperson says something about their land - who rules
            // it, what people speak and believe, a famous person of the land, how people live.
            if (!npc.isSpeaking() && level.getRandom().nextInt(TOWN_TALK_ONE_IN) == 0) {
                String talk = EventNews.townTalk(result,
                        npc.getUUID().getLeastSignificantBits() ^ (level.getGameTime() / 1200L));
                if (!talk.isBlank()) {
                    npc.setSpeech(talk);
                }
            }
        }
        long partStart = System.nanoTime();
        String civId = result.spawnCivId();
        String[] roles = result.spawnRoles();
        townNanos[0] += System.nanoTime() - partStart;
        if (roles.length == 0) {
            return;
        }

        // How many people belong here. The population resolver answers how many live in this place
        // in this year; SettlementDensity turns that number into a rendered count. This is the one
        // place the population figure is used, and it is used only as a number -- the placing,
        // tagging, capping and despawning below are the spawner's own, and follow Minecraft's mob
        // spawning rules.
        int budget = Math.min(SettlementDensity.npcCap(population.population(), result.rank()),
                config("presenceCrowdCeiling", SettlementDensity.ceiling(result.rank())));
        if (!result.settled()) {
            // Open country, no settlement stands here (turn 230, the Secunderabad doubt): a small
            // band of the countryside people the talk describes, never a village.
            budget = Math.min(budget, 3);
            townLine(player, "open country in " + year + ": hunters, fishers and gatherers;"
                    + " no town stands here");
        }
        int missing = budget - existing;
        if (missing <= 0) {
            townLine(player, "have " + existing + " of " + budget + ": full");
            return;
        }
        int[] slots = SettlementDensity.roleSlots(budget, roles);
        int packSize = SettlementDensity.packSize(result.rank());
        int packs = SettlementDensity.packsPerCycle(missing, result.rank());

        // Where the living should stand. The people you can talk to are the ones you should meet
        // first, so they take the crowd's own places on the near side and the drawn crowd closes
        // behind them. A place that has no ground under it, or that is inside a player's bubble, is
        // refused and the district draw below takes over, exactly as before.
        List<CrowdDensity.Figure> crowd = null;
        int[] preferred = null;
        partStart = System.nanoTime();
        if (configFlag("presenceDrawnCrowd", true)) {
            int figures = CrowdDensity.target(population.population(), result.rank(),
                    config("presenceDrawnFiguresMax", CrowdDensity.MAX_FIGURES));
            double radius = CrowdDensity.radiusFor(figures, districtRadius());
            long planSeed = level.getSeed() ^ ((long) district.getX() << 32) ^ district.getZ();
            crowd = CrowdDensity.plan(planSeed, figures, radius);
            preferred = CrowdDensity.nearestSlots(crowd, player.getX() - district.getX(),
                    player.getZ() - district.getZ(), budget);
        }

        townNanos[1] += System.nanoTime() - partStart;
        int spawned = 0;
        java.util.List<int[]> taken = new java.util.ArrayList<>();
        long placingStarted = System.nanoTime();
        java.util.Arrays.fill(TOWN_REFUSED, 0);
        countingTown = true;
        int packsTried = 0;
        boolean outOfTime = false;
        String stoppedBy = "";
        try {
        for (int pack = 0; pack < packs && spawned < missing; pack++) {
            if (pack > 0 && System.nanoTime() - placingStarted > TOWNSPEOPLE_BUDGET_NS) {
                outOfTime = true;
                break; // turn 89: the rest stand up in the next cycles
            }
            packsTried++;
            // One anchor per knot, inside the district core, at a position vanilla would allow:
            // loaded chunk, ground that can hold somebody, outside every player's 24-block bubble,
            // and clear of the knots already placed this cycle.
            partStart = System.nanoTime();
            BlockPos anchor = crowdPlace(level, district, crowd, preferred,
                    existing + spawned + pack, taken);
            if (anchor == null) {
                anchor = spotInDistrict(level, district, existing + spawned + pack + 1, taken);
            }
            townNanos[2] += System.nanoTime() - partStart;
            if (anchor == null) {
                stoppedBy = "; no spot for the next group";
                break;
            }
            taken.add(new int[] {anchor.getX(), anchor.getZ()});
            for (int member = 0; member < packSize && spawned < missing; member++) {
                String role = pickRole(roles, slots, haveByRole);
                if (role == null) {
                    break;
                }
                // The original arrangement of people around the anchor, unchanged: the same
                // offsets the spawner has always used, now relative to the pack instead of to you.
                BlockPos pos = anchor.offset((member % 2) * 3 - 1, 0, (member / 2) * 3 + 2);
                partStart = System.nanoTime();
                java.util.UUID uuid = standPerson(villagers, level, civId, role, pos, district, year);
                townNanos[3] += System.nanoTime() - partStart;
                if (uuid == null) {
                    continue;
                }
                var ent = level.getEntity(uuid);
                if (ent instanceof HumanNpcEntity npc) {
                    npc.setEra(era);
                    CompoundTag tag = npc.getPersistentData();
                    tag.putBoolean(TAG_PRESENCE, true);
                    tag.putInt(TAG_YEAR, year);
                    tag.putInt(TAG_DISTRICT_X, district.getX());
                    tag.putInt(TAG_DISTRICT_Z, district.getZ());
                    // Where their own day starts from, so a person left alone still moves with the
                    // world's clock instead of standing where they were left for two Minecraft days.
                    tag.putInt(TAG_HOME_X, pos.getX());
                    tag.putInt(TAG_HOME_Z, pos.getZ());
                    haveByRole.merge(role.toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
                    spawned++;
                }
            }
        }
        } finally {
            countingTown = false;
        }
        townLine(player, String.format(java.util.Locale.ROOT, "have %d of %d; stood up %d this cycle in %d group(s) of"
                        + " up to %d (%d allowed)%s%s; spots refused: chunk not loaded %d, a player's bubble %d, no ground"
                        + " %d, too close to another group %d", existing, budget, spawned, packsTried, packSize, packs,
                outOfTime ? "; the cycle's 50 ms were used up" : "", stoppedBy, TOWN_REFUSED[0], TOWN_REFUSED[1],
                TOWN_REFUSED[2], TOWN_REFUSED[3]));
    }

    /**
     * The next role that still has room, so the crowd keeps the composition the gazetteer asked for
     * — a market's merchants and a temple's priests, not thirty copies of whichever role came
     * first. Falls back to any role with room, then gives up, which ends the pack.
     */
    private static String pickRole(String[] roles, int[] slots, Map<String, Integer> haveByRole) {
        for (int i = 0; i < roles.length; i++) {
            String role = roles[i];
            if (role == null || role.isBlank()) {
                continue;
            }
            String key = role.toLowerCase(java.util.Locale.ROOT);
            if (haveByRole.getOrDefault(key, 0) < slots[i]) {
                return role;
            }
        }
        return null;
    }

    private static String roleKey(HumanNpcEntity npc) {
        return npc.getRole() == null ? "" : npc.getRole().toLowerCase(java.util.Locale.ROOT);
    }

    // ==================== vanilla rules ====================

    /**
     * A place inside the district, for callers outside the ticker — the {@code /ee villager here}
     * command — so a commanded crowd gathers the way an ambient one does instead of at the player.
     */
    public static BlockPos spawnPlacement(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) {
            return null;
        }
        BlockPos district = districtCenter(level, player, null);
        return district == null ? null : spotInDistrict(level, district, 1);
    }

    /** Ground beneath, two blocks of clear space, no liquid: what vanilla's spawn check amounts to. */
    private static boolean isSpawnable(ServerLevel level, BlockPos pos) {
        var ground = level.getBlockState(pos.below());
        var feet = level.getBlockState(pos);
        var head = level.getBlockState(pos.above());
        return !ground.isAir()
                && feet.getFluidState().isEmpty()
                && head.getFluidState().isEmpty()
                && feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty();
    }

    /**
     * Vanilla's despawn, run once per cycle instead of once per tick.
     *
     * <p>Two rules, both vanilla's: past 128 blocks from every player, gone immediately; past 32
     * blocks, after 600 ticks away, one chance in 800 per tick. The second is a random draw on
     * purpose — mobs trickle away while you explore rather than vanishing in unison.
     *
     * <p>Event NPCs are the exception, and the same exception vanilla makes for a name-tagged mob:
     * while their event is on, the scene is not scattered by the random rule. The instant rule still
     * applies, so a scene no player can reach again is not kept forever.
     */
    private static void vanillaDespawn(ServerLevel level, List<HumanNpcEntity> nearby, int year) {
        for (HumanNpcEntity npc : nearby) {
            boolean event = isEventNpc(npc);
            if (!isPresenceNpc(npc) && !event) {
                continue;
            }
            double distanceSq = nearestPlayerDistanceSq(level, npc);
            CompoundTag tag = npc.getPersistentData();
            if (event) {
                if (PresenceSpawning.farBeyond(distanceSq)) {
                    remember(level, npc, year);
                }
                continue;
            }
            int away = distanceSq > (double) PresenceSpawning.RANDOM_DESPAWN_BLOCKS
                    * PresenceSpawning.RANDOM_DESPAWN_BLOCKS
                    ? tag.getInt(TAG_AWAY_TICKS) + PERIOD_TICKS
                    : 0;
            tag.putInt(TAG_AWAY_TICKS, away);
            int roll = level.random.nextInt(PresenceSpawning.DESPAWN_ONE_IN);
            if (PresenceSpawning.shouldDespawn(distanceSq, roll, away, PERIOD_TICKS)) {
                remember(level, npc, year);
            }
        }
    }

    private static double nearestPlayerDistanceSq(ServerLevel level, HumanNpcEntity npc) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            best = Math.min(best, player.distanceToSqr(npc));
        }
        return best;
    }

    /**
     * The cheapest thing a crowd can do: stop thinking while nobody can see it.
     *
     * <p>Two separate savings, both measured against this crowd's own numbers:
     *
     * <ul>
     *   <li><b>Settled.</b> District crowd members give up the one goal that runs a path search,
     *       keeping the watching and glancing that make them read as alive. That is A2, and it
     *       applies everywhere — a person standing in the square should not be pathing.</li>
     *   <li><b>Frozen.</b> Past {@code presenceSettleBlocks} from every player, NoAI goes on and
     *       the rest of the AI stops too: goal selection, navigation, sensing. It is cleared the
     *       moment a player comes back inside, which is the same 32 blocks vanilla refuses to
     *       despawn inside — so the people who are awake are exactly the people in play.</li>
     * </ul>
     *
     * <p>Event NPCs are exempt, as they are everywhere else in this spawner: a festival scene is
     * meant to move.
     *
     * <p>This runs in the cycle that already walks this list, so it costs one distance check per
     * person per forty ticks.
     */
    private static void settleDistant(ServerLevel level, List<HumanNpcEntity> nearby) {
        boolean settleGoals = configFlag("presenceSettleGoals", true);
        double settleBlocks = config("presenceSettleBlocks", PresenceSpawning.RANDOM_DESPAWN_BLOCKS);
        if (!settleGoals && settleBlocks >= spawnDistance()) {
            return; // neither saving is switched on
        }
        for (HumanNpcEntity npc : nearby) {
            if (!isPresenceNpc(npc) || isEventNpc(npc)) {
                continue;
            }
            // NoAI also stops them falling, so ask the ground check the spawner already has and
            // wake anyone standing on nothing.
            boolean groundGone = !isSpawnable(level, npc.blockPosition());
            boolean frozen = PresenceSpawning.shouldSettle(
                    nearestPlayerDistanceSq(level, npc), settleBlocks, groundGone);
            // Turn 221 (the user's doubt 2: "most of the npc are not moving"): only the frozen give
            // up their walk. This used to settle everyone in reach, which broke routineStep's own
            // promise - "in somebody's sight: let them walk for real" - so nobody near a player ever
            // walked. A seller keeps its stand (directions to it stay true) and event NPCs are
            // exempt everywhere. The distant crowd still settles and freezes: that saving stands.
            if (settleGoals) {
                npc.setSettled(frozen || keepsStand(npc));
            }
            routineStep(level, npc);
            if (npc.isNoAi() != frozen) {
                npc.setNoAi(frozen);
            }
        }
    }

    /**
     * Show a nameplate only within {@code presenceNameTagBlocks} of a player.
     *
     * <p>Applies to presence and event NPCs only — the people this spawner put there. Everything
     * else in the world (VIPs, plague doctors, caravans, bandits) keeps whatever the code that
     * spawned it decided. A speaking NPC keeps its label at any distance: the bubble is the news,
     * and it is only ever set on somebody the player is meant to hear from.
     */
    private static void applyNameTagDistance(ServerLevel level, List<HumanNpcEntity> nearby) {
        int reach = nameTagBlocks();
        double reachSq = (double) reach * reach;
        for (HumanNpcEntity npc : nearby) {
            if (!isPresenceNpc(npc) && !isEventNpc(npc)) {
                continue;
            }
            boolean visible = npc.isSpeaking() || nearestPlayerDistanceSq(level, npc) <= reachSq;
            if (npc.isCustomNameVisible() != visible) {
                npc.setCustomNameVisible(visible);
            }
        }
    }

    /**
     * Where this place's crowd belongs: a district in the world, not a ring around the player.
     *
     * <ol>
     *   <li>If the gazetteer names a site within reach, the crowd belongs at <b>that site's own
     *       centre</b> — the town square is the town square, whether or not anybody is looking.</li>
     *   <li>Otherwise the player is in the place's hinterland, so the district is a point 24 to 128
     *       blocks away, snapped to a grid so it does not crawl along with the player.</li>
     * </ol>
     *
     * @return the district centre, or null when no site could be placed and nothing qualified near
     *         the player
     */
    private static BlockPos districtCenter(ServerLevel level, ServerPlayer player,
                                           HistoricalPresence.Result result) {
        BlockPos site = result == null ? null : siteCenter(level, player, result);
        int min = minSpawnBlocks();
        int max = spawnDistance();
        if (site != null && site.distToCenterSqr(player.position()) <= (double) max * max) {
            return site;
        }

        int centreX = PresenceSpawning.snapToGrid(player.getBlockX(), PresenceSpawning.DISTRICT_GRID);
        int centreZ = PresenceSpawning.snapToGrid(player.getBlockZ(), PresenceSpawning.DISTRICT_GRID);
        long seed = level.getSeed() ^ ((long) centreX << 32) ^ centreZ;
        for (int attempt = 0; attempt < PresenceSpawning.ATTEMPTS; attempt++) {
            PresenceSpawning.Spot spot = PresenceSpawning.spot(seed, player.getId(), attempt, min, max);
            int x = PresenceSpawning.snapToGrid(
                    player.getBlockX() + (int) Math.round(spot.dx()), PresenceSpawning.DISTRICT_GRID);
            int z = PresenceSpawning.snapToGrid(
                    player.getBlockZ() + (int) Math.round(spot.dz()), PresenceSpawning.DISTRICT_GRID);
            // The district has to sit outside the bubble, or every spot inside it would be illegal.
            if (Math.abs(x - player.getBlockX()) < min || Math.abs(z - player.getBlockZ()) < min) {
                continue;
            }
            if (level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
                return new BlockPos(x, player.getBlockY(), z);
            }
        }
        return null;
    }

    /**
     * The centre of the nearest named site, when the gazetteer has one for this place and year. Step 18e: only its map
     * position (projection maths); the ground height there is read only when that spot's chunk is already loaded, and the
     * player's own height stands in otherwise. Reading it anyway made Minecraft load or generate the site's chunk on the
     * spot, mostly for nothing (a site's centre is usually beyond the spawn distance, and then the district near the
     * player is used): 777 ms of one cycle on the sandbox server when a player arrived, and in the user's game, where
     * that means Terra++ building real terrain, the game stood still about 8 seconds right after joining.
     */
    private static BlockPos siteCenter(ServerLevel level, ServerPlayer player, HistoricalPresence.Result result) {
        return result.site().map(h -> {
            double[] xz = TerraCoordinateHelper.fromGeo(h.site().lat(), h.site().lon());
            if (xz == null) {
                return null;
            }
            int x = (int) Math.floor(xz[0]);
            int z = (int) Math.floor(xz[1]);
            BlockPos at = new BlockPos(x, player.getBlockY(), z);
            return level.hasChunkAt(at) ? new BlockPos(x, TerraCoordinateHelper.getSurfaceY(level, x, z), z) : at;
        }).orElse(null);
    }

    /**
     * A place inside the district this person may stand: in the core, on the ground, with room for a
     * head, in a loaded chunk, and never inside the 24-block bubble around any player.
     *
     * <p>The bubble is why a crowd is spawned before you reach it rather than as you watch. Stand in
     * the middle of the square and nobody new appears beside you — but the people already standing
     * there stay, because vanilla's rules govern spawning, not existence, and you are standing in a
     * crowd you walked into. That is how a vanilla village looks when you arrive.
     */
    private static BlockPos spotInDistrict(ServerLevel level, BlockPos center, int index) {
        return spotInDistrict(level, center, index, java.util.List.of());
    }

    /**
     * A knot position inside the district core that is not already taken by another knot this
     * cycle. Two knots on the same spot render as one group and count as one person, so the
     * attempt is rejected and redrawn — the only difference from a solo call.
     */
    private static BlockPos spotInDistrict(ServerLevel level, BlockPos center, int index,
                                           java.util.List<int[]> taken) {
        int core = PresenceSpawning.coreRadius(districtRadius());
        long seed = level.getSeed() ^ ((long) center.getX() << 32) ^ center.getZ() ^ 0x5DEECE66DL;
        for (int attempt = 0; attempt < PresenceSpawning.ATTEMPTS; attempt++) {
            PresenceSpawning.Spot spot = PresenceSpawning.spotInDisc(seed, index, attempt, core);
            int x = center.getX() + (int) Math.round(spot.dx());
            int z = center.getZ() + (int) Math.round(spot.dz());
            if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
                refused(0);
                continue;
            }
            if (!PresenceSpawning.clearOf(x, z, taken, PresenceSpawning.KNOT_CLEARANCE)) {
                refused(3);
                continue;
            }
            if (insideAnyPlayerBubble(level, x, z)) {
                refused(1);
                continue;
            }
            BlockPos pos = new BlockPos(x, groundY(level, x, z), z);
            if (isSpawnable(level, pos)) {
                return pos;
            }
            refused(2);
        }
        return null;
    }

    /**
     * The ground height a person would stand at, at x, z. Every ground look-up of this spawner goes through here, so
     * the slow-cycle line can say how long they took (turn 89).
     */
    private static int groundY(ServerLevel level, int x, int z) {
        long start = System.nanoTime();
        int y = TerraCoordinateHelper.getPlacementY(level, x, z);
        long took = System.nanoTime() - start;
        groundLookups++;
        groundNanos += took;
        groundSlowestNanos = Math.max(groundSlowestNanos, took);
        return y;
    }

    /**
     * The crowd place a living person takes this pack, or null when that place cannot be used.
     *
     * <p>The crowd plan is the same one {@link CrowdFigures} stands its figures on, so a living
     * person is never dropped on top of a figure: the plan's places are one person each.
     */
    private static BlockPos crowdPlace(ServerLevel level, BlockPos district,
                                       List<CrowdDensity.Figure> crowd, int[] preferred, int index,
                                       java.util.List<int[]> taken) {
        if (crowd == null || preferred == null || index >= preferred.length) {
            return null;
        }
        int slot = preferred[index];
        if (slot < 0 || slot >= crowd.size()) {
            return null;
        }
        CrowdDensity.Figure place = crowd.get(slot);
        BlockPos pos = figureSpot(level, district.getX() + (int) Math.round(place.dx()),
                district.getZ() + (int) Math.round(place.dz()), district.getY());
        if (pos == null) {
            return null;
        }
        if (PresenceSpawning.clearOf(pos.getX(), pos.getZ(), taken, PresenceSpawning.KNOT_CLEARANCE)) {
            return pos;
        }
        refused(3);
        return null;
    }

    private static void refused(int why) {
        figureRefusal = why + 1;
        if (countingTown) {
            TOWN_REFUSED[why]++;
        }
    }

    /** Step 21: why the last {@link #figureSpot} refused (server thread only). */
    public static final int SPOT_OK = 0;
    public static final int SPOT_NOT_LOADED = 1;
    public static final int SPOT_BUBBLE = 2;
    public static final int SPOT_NO_GROUND = 3;
    private static int figureRefusal;

    /** Step 21: why the last figureSpot call refused: SPOT_NOT_LOADED, SPOT_BUBBLE, SPOT_NO_GROUND, or SPOT_OK. */
    public static int lastFigureRefusal() {
        return figureRefusal;
    }

    /**
     * A place a drawn figure may be stood up on, or null.
     *
     * <p>The same three rules the living obey, because a crowd that appears out of nowhere next to
     * the player is no better for not thinking: the chunk must be loaded, the ground must hold
     * somebody, and no player may be within vanilla's 24-block bubble. A figure never needs a
     * clearance check — its place in the crowd plan already is its clearance.
     */
    public static BlockPos figureSpot(ServerLevel level, int x, int z, int referenceY) {
        figureRefusal = SPOT_OK;
        if (level == null || !level.hasChunkAt(new BlockPos(x, referenceY, z))) {
            refused(0);
            return null;
        }
        if (insideAnyPlayerBubble(level, x, z)) {
            refused(1);
            return null;
        }
        BlockPos pos = new BlockPos(x, groundY(level, x, z), z);
        if (isSpawnable(level, pos)) {
            return pos;
        }
        refused(2);
        return null;
    }

    /**
     * Take a person out of the world and keep them.
     *
     * <p>Every removal this spawner makes goes through here. Nothing is deleted any more: a person
     * who leaves is written into the place's memory — the same UUID, the same name, the same
     * civilization, role, era and gender — and the place hands them back the next time it needs
     * somebody. A wanderer is remembered at the district they wandered away from, not at the field
     * they happen to be standing in when the check catches them.
     */
    private static void remember(ServerLevel level, HumanNpcEntity npc, int year) {
        // Step 21: nobody leaves while a player talks or trades with them. Every removal of this spawner comes through
        // here (the vanilla despawn, the district's edge, a place with nobody, another year's people, an event's end,
        // far from every player), so this one check covers them all; the person goes on a later cycle, once the talk is
        // over, if the reason still holds.
        if (com.eternalengine.knowledge.TalkHold.busy(npc)) {
            return;
        }
        CompoundTag tag = npc.getPersistentData();
        if (tag.contains(TAG_DISTRICT_X)) {
            VillagerVirtualizer.remember(level, npc, year,
                    tag.getInt(TAG_DISTRICT_X), tag.getInt(TAG_DISTRICT_Z));
        } else {
            VillagerVirtualizer.remember(level, npc, year);
        }
    }

    /**
     * Stand a person up at a spot: somebody this place remembers, or — only when it remembers
     * nobody — a new person invented by the name database.
     *
     * <p>This order is the whole answer to "I want the old NPC": the memory is consulted first, so
     * the second, tenth and hundredth visit to a place meet the people who were there before.
     */
    private static java.util.UUID standPerson(VillagerManager villagers, ServerLevel level,
                                              String civId, String role, BlockPos pos,
                                              BlockPos place, int year) {
        // Step 25 (known problem 15): only somebody of the same people and about the same time comes back. A Roman of
        // AD 100 does not stand in Rome in 2026 (nor an Italian of 2026 in AD 100) with his old label; he stays
        // remembered for when the place is his again. Within the same people, a new state name (the Dominion of India
        // becoming the Republic in 1950) keeps the people and gives them the land's name now.
        java.util.function.Predicate<VirtualVillager> fits = person -> sameTimeAndPeople(person, civId, year);
        VirtualVillager old = VillagerVirtualizer.remembered(level, place.getX(), place.getZ(), role, fits);
        // Step 21: a remembered person whose UUID already stands in the world (a townsperson met in the crowd goes back
        // to the memory, and their place in the crowd can stand them up again as a figure) is that person already: the
        // record is spent, and the next one is asked for. Minecraft would refuse the second entity anyway.
        for (int tries = 0; old != null && level.getEntity(old.entityUuid()) != null && tries < 4; tries++) {
            old = VillagerVirtualizer.remembered(level, place.getX(), place.getZ(), role, fits);
        }
        if (old != null && civId != null && !civId.isBlank() && !civId.equals(old.civId())) {
            old.setCivId(civId);
        }
        if (old != null && level.getEntity(old.entityUuid()) != null) {
            old = null;
        }
        if (old != null) {
            java.util.UUID uuid = villagers.instantiate(old, pos, level);
            if (uuid != null) {
                stoodUp++;
                return uuid;
            }
            // the ground did not take them: back into the memory rather than lost
            PeopleMemory.get(level).remember(PeopleMemory.placeKey(place.getX(), place.getZ()), old);
        }
        java.util.UUID made = villagers.spawnVillager(civId, role, pos, level);
        if (made != null) {
            stoodUp++;
        }
        return made;
    }

    /** Remembered people come back within this many years of the year they were met in (step 25). */
    static final int REMEMBERED_YEARS = 100;

    /**
     * Step 25: whether a remembered person belongs here now: the same people (the same name list as this place's
     * civilization now) and met within {@link #REMEMBERED_YEARS} of this year. A person whose year is unknown fits
     * on the people alone.
     */
    static boolean sameTimeAndPeople(VirtualVillager person, String civId, int year) {
        if (person == null) {
            return false;
        }
        String era = person.era();
        if (era != null && era.startsWith("year_")) {
            try {
                if (Math.abs(Integer.parseInt(era.substring(5)) - year) > REMEMBERED_YEARS) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                // an era without a year: judged on the people alone
            }
        }
        if (civId == null || civId.isBlank() || civId.equals(person.civId())) {
            return true;
        }
        return VillagerNameDatabase.cultureFor(civId).equals(VillagerNameDatabase.cultureFor(person.civId()));
    }

    /**
     * The day a living NPC has when nobody is watching them.
     *
     * <p>A person beyond the settle distance has their goals switched off — that is what makes a
     * crowd of talkable people affordable — and a chunk nobody is near does not tick at all. So a
     * person left alone for two Minecraft days used to be exactly where they were left, which is what
     * "why didn't he move?" is about. Their position is now a function of the world clock as well:
     * home in the morning, out for the day, home at night, along a route that is theirs and on a day
     * that is the world's.
     *
     * <p>Only while they are settled, and only when nobody is near enough to watch it happen: a
     * person the player is close to walks on their own feet, with their own goals, as they always did.
     */
    private static void routineStep(ServerLevel level, HumanNpcEntity npc) {
        CompoundTag tag = npc.getPersistentData();
        if (!tag.contains(TAG_HOME_X) || !tag.contains(TAG_HOME_Z) || !npc.isNoAi()) {
            return;
        }
        if (keepsStand(npc)) {
            return; // step 19c: sellers keep their stand, so directions to them stay true
        }
        if (com.eternalengine.knowledge.TalkHold.busy(npc)) {
            return; // step 21: somebody in a talk stays where they are
        }
        double reach = nameTagBlocks();
        if (nearestPlayerDistanceSq(level, npc) <= reach * reach) {
            return; // in somebody's sight: let them walk for real
        }
        double[] point = CrowdDensity.routinePoint(level.getSeed(),
                npc.getUUID() == null ? 0L : npc.getUUID().getMostSignificantBits(),
                tag.getInt(TAG_HOME_X) + 0.5d, tag.getInt(TAG_HOME_Z) + 0.5d,
                level.getGameTime(), ROUTINE_BLOCKS);
        double dx = point[0] - npc.getX();
        double dz = point[1] - npc.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance <= 0.05d) {
            return;
        }
        // A step a cycle, and only onto ground that would hold a person: the same ground check the
        // spawner uses for everybody else.
        double step = Math.min(1.0d, distance) / distance;
        int x = (int) Math.round(npc.getX() + dx * step);
        int z = (int) Math.round(npc.getZ() + dz * step);
        BlockPos pos = new BlockPos(x, groundY(level, x, z), z);
        if (!isSpawnable(level, pos)) {
            return;
        }
        npc.moveTo(x + 0.5d, pos.getY(), z + 0.5d, npc.getYRot(), npc.getXRot());
    }

    /**
     * Step 19c: the people with a stand: the merchant, the money-changer and the people who bring the news (step 19g:
     * and the town trader and the village pedlar).
     */
    static boolean keepsStand(HumanNpcEntity npc) {
        String r = npc.getRole() == null ? "" : npc.getRole().toLowerCase(java.util.Locale.ROOT);
        return com.eternalengine.money.MarketService.buyer(r) || r.equals("money_changer")
                || com.eternalengine.news.NewsEras.newsRole(r);
    }

    /** Vanilla's no-spawn bubble, applied to every player rather than only the nearest one. */
    private static boolean insideAnyPlayerBubble(ServerLevel level, int x, int z) {
        double min = minSpawnBlocks();
        double minSq = min * min;
        for (ServerPlayer other : level.players()) {
            double dx = other.getX() - (x + 0.5d);
            double dz = other.getZ() - (z + 0.5d);
            if (dx * dx + dz * dz < minSq) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keep the crowd where the place is. Anyone who has strolled past the district's edge is
     * removed, and the count loop replaces them inside it on a later cycle. Without this a crowd
     * dissolves over a few minutes — the entities have a wander goal, and thirty wanderers become
     * thirty scattered individuals, which is the one thing a crowd is not.
     */
    private static void enforceDistrict(ServerLevel level, List<HumanNpcEntity> nearby, int year) {
        int radius = districtRadius() + DISTRICT_MARGIN;
        for (HumanNpcEntity npc : nearby) {
            if (!isPresenceNpc(npc) || isEventNpc(npc)) {
                continue;
            }
            CompoundTag tag = npc.getPersistentData();
            if (!tag.contains(TAG_DISTRICT_X) || tag.getBoolean(TAG_MET)) {
                continue; // step 21: a person met in the crowd stands where they were met
            }
            double dx = npc.getX() - tag.getInt(TAG_DISTRICT_X);
            double dz = npc.getZ() - tag.getInt(TAG_DISTRICT_Z);
            if (!PresenceSpawning.withinDisc(dx, dz, radius)) {
                remember(level, npc, year);
            }
        }
    }

    private static int districtRadius() {
        try {
            return Math.max(8, com.eternalengine.config.EternalEngineConfig.COMMON
                    .presenceDistrictRadius.get());
        } catch (Throwable t) {
            return 28;
        }
    }

    /**
     * How far a figure may be from every player before it is forgotten. It has to clear the whole of
     * a district — spawn distance plus the district's radius plus a margin — so that a crowd is never
     * half-forgotten and immediately rebuilt, which would be pure churn.
     */
    /**
     * Sends the crowd's recipe to the client that is standing in it — the crowd itself is never sent,
     * because a crowd that is a pure function of a seed does not need to be.
     *
     * <p>Only when the recipe has actually changed: the player's chunk, the place, or the year. A
     * player standing still receives nothing at all; a player walking receives 166 bytes a chunk of
     * movement, which is a hundredth of what four hundred spawned entities cost to send even once.
     */
    private static void sendCrowdManifest(ServerLevel level, ServerPlayer player, BlockPos district,
                                          SettlementRank rank, long population, double placeRadiusKm,
                                          int year) {
        try {
            int chunkX = player.blockPosition().getX() >> 4;
            int chunkZ = player.blockPosition().getZ() >> 4;
            // The crowd's anchor is the player's chunk, not the district's heart: the crowd is the
            // place's built area anchored where the player stands. One manifest a chunk of movement.
            int anchorX = (chunkX << 4) + 8;
            int anchorZ = (chunkZ << 4) + 8;
            CompoundTag tag = player.getPersistentData();
            if (tag.getInt(TAG_CROWD_CHUNK_X) == chunkX && tag.getInt(TAG_CROWD_CHUNK_Z) == chunkZ
                    && tag.getInt(TAG_CROWD_YEAR) == year
                    && tag.getInt(TAG_CROWD_HEART_X) == anchorX
                    && tag.getInt(TAG_CROWD_HEART_Z) == anchorZ
                    && tag.getLong(TAG_CROWD_POPULATION) == population) {
                return; // the recipe on the client still describes this view
            }
            double density = ChunkPopulation.builtDensityPerKm2(rank, population, placeRadiusKm)
                    * CrowdFigures.crowdedness();
            ChunkPopulation.Manifest manifest = ChunkPopulation.manifestOfCore(level.getSeed(), population,
                    placeRadiusKm, density, anchorX, anchorZ, player.getX(), player.getZ(),
                    crowdInterestBlocks(), CrowdFigures.crowdedness(), drawnFiguresMax(), year,
                    player.getYRot(), minSpawnBlocks());
            EternalEnginePacketHandler.sendToPlayer(new CrowdManifestPacket(manifest), player);
            tag.putInt(TAG_CROWD_CHUNK_X, chunkX);
            tag.putInt(TAG_CROWD_CHUNK_Z, chunkZ);
            tag.putInt(TAG_CROWD_YEAR, year);
            tag.putInt(TAG_CROWD_HEART_X, anchorX);
            tag.putInt(TAG_CROWD_HEART_Z, anchorZ);
            tag.putLong(TAG_CROWD_POPULATION, population);
        } catch (Throwable ignored) {
            // a client that cannot be told is a client that draws what it already has
        }
    }

    /** The client's drawing budget, read the same way the crowd reads it. */
    private static int drawnFiguresMax() {
        return (int) config("presenceDrawnFiguresMax", 400);
    }

    /** How far from a player the crowd is stood up: the chunk division's window on the world. */
    private static double crowdInterestBlocks() {
        return config("presenceCrowdInterestBlocks", 64);
    }

    private static double forgetDistance() {
        return spawnDistance() + districtRadius() + 16.0d;
    }

    private static int nameTagBlocks() {
        try {
            return com.eternalengine.config.EternalEngineConfig.COMMON.presenceNameTagBlocks.get();
        } catch (Throwable t) {
            return 24;
        }
    }

    /** Never closer than this. Vanilla's own number, and the answer to "not beside the player". */
    /**
     * Vanilla's no-spawn radius around a player, in blocks. Public because the crowd's drawing has
     * to know it too: a figure cannot stand inside this, so the budget is not spent there.
     */
    public static int minSpawnBlocks() {
        return config("presenceMinSpawnBlocks", PresenceSpawning.MIN_SPAWN_BLOCKS);
    }

    /** The spawn distance, and therefore the instant-despawn distance. Vanilla: 128. */
    private static int spawnDistance() {
        try {
            return Math.max(minSpawnBlocks() + 8,
                    com.eternalengine.config.EternalEngineConfig.COMMON.presenceSpawnDistance.get());
        } catch (Throwable t) {
            return PresenceSpawning.MAX_SPAWN_BLOCKS;
        }
    }

    /** Config read that cannot take the spawner down if the config is not loaded yet. */
    /** A config flag, read the same tolerant way {@link #config} reads numbers. */
    private static boolean configFlag(String name, boolean fallback) {
        try {
            if ("presenceSettleGoals".equals(name)) {
                return com.eternalengine.config.EternalEngineConfig.COMMON.presenceSettleGoals.get();
            }
            if ("presenceDrawnCrowd".equals(name)) {
                return com.eternalengine.config.EternalEngineConfig.COMMON.presenceDrawnCrowd.get();
            }
            return fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int config(String name, int fallback) {
        try {
            if ("presenceMinSpawnBlocks".equals(name)) {
                return com.eternalengine.config.EternalEngineConfig.COMMON.presenceMinSpawnBlocks.get();
            }
            if ("presenceSettleBlocks".equals(name)) {
                return com.eternalengine.config.EternalEngineConfig.COMMON.presenceSettleBlocks.get();
            }
            if ("presenceCrowdCeiling".equals(name)) {
                return com.eternalengine.config.EternalEngineConfig.COMMON.presenceCrowdCeiling.get();
            }
            if ("presenceDrawnFiguresMax".equals(name)) {
                return com.eternalengine.config.EternalEngineConfig.COMMON.presenceDrawnFiguresMax.get();
            }
            if ("presenceCrowdInterestBlocks".equals(name)) {
                return com.eternalengine.config.EternalEngineConfig.COMMON.presenceCrowdInterestBlocks.get();
            }
            return fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /**
     * Step 39 (D9): the people standing in the scene, as roles the villager tables know.
     *
     * <p>This used to be {@code sceneRoles(String effect)}: four keyword tests on the event's
     * effect string, handing back two or three copies of {@code guard}, {@code citizen} or
     * {@code merchant}. It could not tell a coronation from a cattle market and knew neither the
     * year, the ground, nor who was available. The register now decides all three, and decides
     * too when a scene simply cannot be cast here - a band of twenty-five foragers does not hold
     * a coronation, and an empty street is a truer answer than a farmer in a borrowed robe.
     *
     * @param occasion the scene the register has cast, or null when there is none
     * @return one role per person who could actually be cast; empty when there is no scene
     */
    private static String[] sceneRoles(Scenes.Occasion occasion) {
        if (occasion == null) {
            return new String[0];
        }
        java.util.List<String> roles = new java.util.ArrayList<>();
        for (Scenes.Person p : occasion.people()) {
            if (p.work() != null) {
                roles.add(Scenes.roleOf(p.part()));
            }
        }
        return roles.toArray(new String[0]);
    }

    /** Which scene id the event here would set off, or "none" - for the talk box only. */
    private static String eventSceneId(Scenes.Where w) {
        String id = Scenes.eventScene(w.eventText());
        return id.isEmpty() ? "none" : id;
    }

    /**
     * Step 21: a crowd figure a player talks to becomes a townsperson: the same entity, face and place, with the name the
     * player hears, its AI switched on (so it can turn to the player), mortal like everybody else, and tagged as one
     * of this spawner's people of the figure's year and district. From now on the crowd's re-arranging and sweeping
     * (which handle figures only) never touch it; it leaves the way townspeople leave, into the place's memory, once
     * the player has gone.
     *
     * @return true if the figure became a townsperson
     */
    public static boolean adoptFigure(HumanNpcEntity npc, String name) {
        if (npc == null || !CrowdFigures.isFigure(npc)) {
            return false;
        }
        CompoundTag tag = npc.getPersistentData();
        int year = tag.getInt(CrowdFigures.TAG_FIGURE_YEAR);
        tag.remove(CrowdFigures.TAG_FIGURE);
        tag.remove(CrowdFigures.TAG_FIGURE_YEAR);
        tag.remove(CrowdFigures.TAG_FIGURE_SLOT);
        tag.remove(CrowdFigures.TAG_FIGURE_PLAN);
        tag.remove(CrowdFigures.TAG_FIGURE_CHUNK_X);
        tag.remove(CrowdFigures.TAG_FIGURE_CHUNK_Z);
        tag.putBoolean(TAG_PRESENCE, true);
        tag.putBoolean(TAG_MET, true);
        tag.putInt(TAG_YEAR, year);
        // The district keys are the ones the figure already carries (CrowdFigures.TAG_FIGURE_CX/CZ are the same keys).
        tag.putInt(TAG_HOME_X, npc.getBlockX());
        tag.putInt(TAG_HOME_Z, npc.getBlockZ());
        npc.setNoAi(false);
        npc.setInvulnerable(false);
        npc.setSilent(false);
        if (name != null && !name.isBlank()) {
            npc.setCustomName(net.minecraft.network.chat.Component.literal(HumanNpcEntity.withRoleMark(name, npc.getRole())));
        }
        npc.setCustomNameVisible(true);
        npc.setEra("year_" + year);
        npc.getNavigation().stop();
        return true;
    }

    /** Step 21: whether this person was a crowd figure a player talked to. */
    public static boolean isMet(HumanNpcEntity npc) {
        return npc != null && npc.getPersistentData().getBoolean(TAG_MET);
    }

    public static boolean isPresenceNpc(HumanNpcEntity npc) {
        return npc != null && npc.getPersistentData().getBoolean(TAG_PRESENCE);
    }

    public static boolean isEventNpc(HumanNpcEntity npc) {
        return npc != null && npc.getPersistentData().getBoolean(TAG_EVENT);
    }
}

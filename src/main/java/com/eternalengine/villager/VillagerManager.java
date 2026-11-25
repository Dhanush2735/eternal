package com.eternalengine.villager;

import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IEntityCivilizationData;
import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import com.eternalengine.terrabridge.TerraTimeBridge;
import com.eternalengine.vip.VipEntityHelper;
import com.eternalengine.vip.VipSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villager/person manager foundation.
 * Uses Player-style HumanNpcEntity as the primary humanoid actor in the simulation.
 */
public final class VillagerManager {
    private final ConcurrentHashMap<String, List<UUID>> civVillagers = new ConcurrentHashMap<>();
    private final VillagerNameDatabase nameDatabase;
    private final VillagerVirtualizer virtualizer;

    public VillagerManager() {
        this(new VillagerNameDatabase(), new VillagerVirtualizer());
    }

    public VillagerManager(VillagerNameDatabase nameDatabase, VillagerVirtualizer virtualizer) {
        this.nameDatabase = nameDatabase == null ? new VillagerNameDatabase() : nameDatabase;
        this.virtualizer = virtualizer == null ? new VillagerVirtualizer() : virtualizer;
    }

    /**
     * Put a person the place remembers back in the world, at a spot.
     *
     * <p>Not a lookalike: the virtualizer restores the UUID the person had, and everything the game
     * shows about a person — the skin the client picks, the name over their head, their
     * civilization, role, era and gender — follows from the record. The memory is emptied of them
     * as they are stood up, so the same person is never put in the world twice.
     *
     * @return the person's id, or null when the ground would not take them
     */
    public UUID instantiate(VirtualVillager person, BlockPos pos, ServerLevel level) {
        if (person == null || pos == null || level == null) {
            return null;
        }
        UUID uuid = virtualizer.instantiateVillager(person, pos, level);
        if (uuid != null) {
            civVillagers.computeIfAbsent(person.civId(), id -> new ArrayList<>()).add(uuid);
        }
        return uuid;
    }

    /**
     * Step 18: a name for someone who has none (a crowd figure), from the name list of the place, seeded by who
     * they are, so the same figure always has the same name. Nothing is stored.
     *
     * <p>Permission 04: the place-true register answers first (the language spoken where they
     * stand, era- and sex-bound); the legacy pools answer only where it cannot. The seed is
     * mixed before it reaches the random: java.util.Random with consecutive raw seeds is
     * correlated in its first draws (measured in the headless acceptance run), and figure seeds
     * come out in sequence.
     */
    public String nameFor(String civId, String roleType, boolean female, double lat, double lon, int year, long seed) {
        String pool = civId;
        Random rng = new Random(mixSeed(seed));
        if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
            pool = nameDatabase.poolForPlace(lat, lon, year, civId);
            String placeName = nameDatabase.getRandomPlaceName(pool, lat, lon, year, roleType, female, rng, null);
            if (placeName != null) {
                return placeName;
            }
        }
        return nameDatabase.getRandomName(pool, "", roleType, female, rng);
    }

    public UUID spawnVillager(String civId, String roleType, BlockPos pos, ServerLevel level) {
        if (pos == null || level == null) {
            return null;
        }
        
        int y = Math.max(pos.getY(), TerraCoordinateHelper.getPlacementY(level, pos.getX(), pos.getZ()));
        // Gender is decided here, once, and everything downstream follows from it: the name comes
        // from the matching name list instead of always from the male one, and the skin is picked
        // from the entity's UUID. Nothing used to set this at all, so every person in the world was
        // "neutral" -- which the renderer read as male and the name pool ignored entirely.
        boolean female = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
        int year = com.eternalengine.clock.SimulationClockManager.isInitialized()
                ? com.eternalengine.clock.SimulationClockManager.getInstance().getCurrentYear()
                : TerraTimeBridge.getCurrentYear(2026);
        // The name fits the place (step 17, and Permission 04 on top of it): the place-true
        // register answers for the language spoken here, era- and sex-bound; the legacy pools
        // answer only where it cannot. A per-chunk ledger keeps the names inside one village
        // distinct — forty people drawn with replacement would collide, however the weights are set.
        String name = null;
        String pool = civId;
        var geo = TerraCoordinateHelper.toGeoPoint(pos);
        double lat = geo == null ? Double.NaN : geo.latitude();
        double lon = geo == null ? Double.NaN : geo.longitude();
        if (geo != null) {
            pool = nameDatabase.poolForPlace(lat, lon, year, civId);
            String placeName = nameDatabase.getRandomPlaceName(pool, lat, lon, year, roleType, female,
                    new Random(mixSeed(System.nanoTime() ^ (pos.getX() * 31L + pos.getZ()))), usedNamesFor(pos));
            if (placeName != null) {
                name = placeName;
            }
        }
        if (name == null) {
            name = nameDatabase.getRandomName(pool, "", roleType, female, new Random());
        }
        String era = "year_" + year;

        // Try spawning HumanNpcEntity first
        HumanNpcEntity humanNpc = null;
        try {
            if (EternalEngineEntities.HUMAN_NPC != null && EternalEngineEntities.HUMAN_NPC.isPresent()) {
                humanNpc = EternalEngineEntities.HUMAN_NPC.get().create(level);
            }
        } catch (Throwable ignored) {}

        if (humanNpc != null) {
            humanNpc.moveTo(pos.getX() + 0.5d, y, pos.getZ() + 0.5d, 0.0f, 0.0f);
            humanNpc.setCustomName(Component.literal(HumanNpcEntity.withRoleMark(name, roleType)));
            humanNpc.setCustomNameVisible(true);
            humanNpc.setCivilizationId(civId);
            humanNpc.setRole(roleType);
            humanNpc.setEra(era);
            humanNpc.setGender(female ? "female" : "male");
            humanNpc.setLoyalty(50);

            IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(humanNpc);
            if (data != null) {
                data.setCivilizationId(civId);
                data.setRole(roleType);
                data.setEntityName(name);
                data.setLoyalty(50);
                data.setVirtual(false);
            }
            level.addFreshEntity(humanNpc);
            civVillagers.computeIfAbsent(civId == null ? "neutral" : civId, id -> new ArrayList<>()).add(humanNpc.getUUID());
            return humanNpc.getUUID();
        }

        // Fallback to vanilla Villager if custom entity fails to instantiate
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        villager.moveTo(pos.getX() + 0.5d, y, pos.getZ() + 0.5d, 0.0f, 0.0f);
        villager.setCustomName(Component.literal(HumanNpcEntity.withRoleMark(name, roleType)));
        villager.setCustomNameVisible(true);
        IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(villager);
        if (data != null) {
            data.setCivilizationId(civId);
            data.setRole(roleType);
            data.setEntityName(name);
            data.setLoyalty(50);
            data.setVirtual(false);
        }
        level.addFreshEntity(villager);
        civVillagers.computeIfAbsent(civId == null ? "neutral" : civId, id -> new ArrayList<>()).add(villager.getUUID());
        return villager.getUUID();
    }

    /**
     * The bare names (given + surname, no title) already spoken in a chunk — the ledger behind
     * "no duplicate inside a village" (Permission 04). One set per chunk; a chunk stops holding
     * more than 128 names (a village is at most a few dozen, so past that the memory is stale
     * and starts over), and the whole map clears past 1024 chunks so a long world keeps neither
     * the names nor the memory.
     */
    private final ConcurrentHashMap<Long, Set<String>> chunkNameLedger = new ConcurrentHashMap<>();

    private Set<String> usedNamesFor(BlockPos pos) {
        long chunkKey = ((long) Math.floorDiv(pos.getX(), 16) << 32) ^ (Math.floorDiv(pos.getZ(), 16) & 0xFFFFFFFFL);
        if (chunkNameLedger.size() > 1024) {
            chunkNameLedger.clear();
        }
        Set<String> used = chunkNameLedger.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet());
        if (used.size() >= 128) {
            used.clear();
        }
        return used;
    }

    /**
     * splitmix64 finalizer. java.util.Random uses the seed as its state, so consecutive raw
     * seeds (which is what a per-figure or per-spawn counter produces) give correlated first
     * draws — measured in the headless run: eight consecutive seeds all opened within 0.0006
     * of the same value, and a village froze on one name. Mixing breaks that; the output is
     * still a pure function of the seed, so the same figure still gets the same name.
     */
    private static long mixSeed(long x) {
        x = (x + 0x9E3779B97F4A7C15L) & 0xFFFFFFFFFFFFFFFFL;
        x = ((x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L) & 0xFFFFFFFFFFFFFFFFL;
        x = ((x ^ (x >>> 27)) * 0x94D049BB133111EBL) & 0xFFFFFFFFFFFFFFFFL;
        return x ^ (x >>> 31);
    }

    public UUID spawnVipVillager(VipSlot vip, BlockPos pos, ServerLevel level) {
        if (vip == null) {
            return null;
        }
        UUID uuid = spawnVillager(vip.civilizationId(), vip.roleType().name(), pos, level);
        if (uuid == null) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        if (entity != null) {
            VipEntityHelper.tagVipEntity(entity, vip);
        }
        return uuid;
    }

    public int getVillagerCount(String civId) {
        return civVillagers.getOrDefault(civId, List.of()).size();
    }

    public List<Entity> getVillagersNearby(BlockPos pos, int radiusBlocks, ServerLevel level) {
        if (pos == null || level == null) {
            return List.of();
        }
        List<Entity> result = new ArrayList<>();
        net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(pos).inflate(Math.max(1, radiusBlocks));
        for (HumanNpcEntity npc : level.getEntitiesOfClass(HumanNpcEntity.class, aabb)) {
            result.add(npc);
        }
        for (Villager villager : level.getEntitiesOfClass(Villager.class, aabb)) {
            result.add(villager);
        }
        return List.copyOf(result);
    }

    public boolean killVillager(UUID villagerUuid, String cause, ServerLevel level) {
        if (villagerUuid == null || level == null) {
            return false;
        }
        Entity entity = level.getEntity(villagerUuid);
        if (entity == null) {
            return false;
        }
        entity.kill();
        return true;
    }

    public VillagerNameDatabase nameDatabase() { return nameDatabase; }

    /**
     * Load the name pools from the world folder, falling back to the copy in the jar.
     *
     * <p>The names pack was shipped and installed and then never read, so only the two hardcoded
     * pools existed and every other civ produced "Villager Alex Stone".
     */
    public int loadNames(java.nio.file.Path dir) {
        return nameDatabase.loadFrom(dir);
    }
    public VillagerVirtualizer virtualizer() { return virtualizer; }
}

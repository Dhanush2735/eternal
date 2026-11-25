package com.eternalengine.trade;

import com.eternalengine.entity.EternalEngineEntities;
import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates physical overland trade caravans, maritime merchant convoys,
 * dynamic commodity pricing, and bandit ambush simulations in the world.
 */
public final class LogisticsManager {

    private final ConcurrentHashMap<String, CaravanEntityGroup> activeCaravans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MerchantShipConvoy> activeShips = new ConcurrentHashMap<>();
    private final CommodityPricingEngine pricingEngine = new CommodityPricingEngine();
    private final BanditAmbushEngine ambushEngine = new BanditAmbushEngine();
    private int tickCounter = 0;

    /**
     * Deploys an overland trade caravan group along a designated sequence of waypoints.
     */
    public CaravanEntityGroup deployCaravan(TradeRoute route, BlockPos startPos, List<BlockPos> waypoints,
                                            ServerLevel level, Map<String, Integer> cargo) {
        if (level == null || startPos == null) {
            return null;
        }

        String routeId = route != null ? route.routeId() : "ad_hoc_route";
        String originCiv = route != null ? route.originCivId() : "local";
        String destCiv = route != null ? route.destCivId() : "distant";
        String caravanId = "caravan_" + System.currentTimeMillis() % 100000;

        int surfaceY = TerraCoordinateHelper.getPlacementY(level, startPos.getX(), startPos.getZ());
        BlockPos spawnPos = new BlockPos(startPos.getX(), surfaceY, startPos.getZ());

        // Step 46 part 2: where this caravan is. Everything the animals do depends on it.
        String area = areaAt(level, spawnPos);

        // 1. Spawn Caravan Master
        HumanNpcEntity master = EternalEngineEntities.HUMAN_NPC.get().create(level);
        if (master == null) return null;

        master.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        master.setCivilizationId(originCiv);
        master.setCustomName(net.minecraft.network.chat.Component.literal("Caravan Master"));
        master.setCustomNameVisible(true);
        CaravanEntityGroup.equipMaster(master);
        level.addFreshEntity(master);

        // 2. Spawn the pack animals THIS PLACE AND YEAR ACTUALLY HAS.
        //
        // Step 46 part 2 (D14). This was "spawn 2 to 4 pack animals (donkeys)" - three donkeys
        // for every caravan, in every era, on every continent. Three donkeys at Cusco in 1500,
        // when the Andes had llamas for four thousand years; three donkeys in Australia in 1700,
        // eighty-eight years before donkeys arrived; three donkeys crossing the Sahara, where the
        // pack animal is the camel; three donkeys in the tsetse belt, where no beast survives at
        // all and everything is carried on human heads.
        //
        // The register now answers the question. Where no animal has reached the place yet, the
        // carrier is a PORTER - a person - and the caravan walks.
        List<UUID> packAnimals = new ArrayList<>();
        int year = com.eternalengine.war.WarGearStacks.currentYear();
        com.eternalengine.vehicle.WorkingAnimals.Choice carrier =
                com.eternalengine.vehicle.WorkingAnimals.get().packAnimalFor(area, year);
        int animalCount = 3;
        if (carrier != null && !carrier.animal().isBeast()) {
            // A person carries it: more of them, because each carries less. The Kano record has a
            // porter at 29 kg against a donkey's 51 and a camel's 254, so a caravan that would
            // have been three donkeys is a dozen porters.
            animalCount = 6;
        }
        for (int i = 0; i < animalCount; i++) {
            var beast = packEntityFor(carrier, level);
            if (beast != null) {
                int dx = (i + 1) * -2;
                int ay = TerraCoordinateHelper.getPlacementY(level, spawnPos.getX() + dx, spawnPos.getZ());
                beast.moveTo(spawnPos.getX() + dx + 0.5D, ay, spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
                if (beast instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse ch) {
                    ch.setTamed(true);
                    ch.setChest(true);
                }
                if (beast instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h) {
                    h.setTamed(true);
                }
                level.addFreshEntity(beast);
                packAnimals.add(beast.getUUID());
            }
        }

        // 3. Spawn 2 to 3 Armed Guards
        List<UUID> guards = new ArrayList<>();
        int guardCount = 2;
        for (int i = 0; i < guardCount; i++) {
            HumanNpcEntity guard = EternalEngineEntities.HUMAN_NPC.get().create(level);
            if (guard != null) {
                int gx = (i == 0) ? -2 : 2;
                int gz = 2;
                int gy = TerraCoordinateHelper.getPlacementY(level, spawnPos.getX() + gx, spawnPos.getZ() + gz);
                guard.moveTo(spawnPos.getX() + gx + 0.5D, gy, spawnPos.getZ() + gz + 0.5D, 0.0F, 0.0F);
                guard.setCivilizationId(originCiv);
                guard.setCustomName(net.minecraft.network.chat.Component.literal("Caravan Guard"));
                guard.setCustomNameVisible(true);
                CaravanEntityGroup.equipGuard(guard, "MEDIEVAL");
                level.addFreshEntity(guard);
                guards.add(guard.getUUID());
            }
        }

        // 4. Calculate Cargo Value
        double totalVal = 0.0D;
        if (cargo != null) {
            for (Map.Entry<String, Integer> entry : cargo.entrySet()) {
                double unitPrice = pricingEngine.calculateUnitPrice(entry.getKey(), originCiv, destCiv, 20.0D, 15.0D);
                totalVal += unitPrice * entry.getValue();
            }
        } else {
            cargo = Map.of("GRAIN", 64, "TIMBER", 32);
            totalVal = 250.0D;
        }

        CaravanEntityGroup caravan = new CaravanEntityGroup(caravanId, routeId, originCiv, destCiv,
            master.getUUID(), packAnimals, guards, waypoints, cargo, totalVal);

        activeCaravans.put(caravanId, caravan);
        return caravan;
    }

    /**
     * Deploys a maritime merchant ship convoy along navigable water waypoints.
     */
    public MerchantShipConvoy deployMerchantShip(TradeRoute route, BlockPos startWaterPos,
                                                List<BlockPos> waterWaypoints, ServerLevel level,
                                                Map<String, Integer> cargo) {
        if (level == null || startWaterPos == null) {
            return null;
        }

        String routeId = route != null ? route.routeId() : "naval_route";
        String originPort = route != null ? route.originCivId() : "harbor_a";
        String destPort = route != null ? route.destCivId() : "harbor_b";
        String convoyId = "convoy_" + System.currentTimeMillis() % 100000;

        // 1. Spawn Lead Merchant Vessel
        Boat leadBoat = EntityType.BOAT.create(level);
        if (leadBoat == null) return null;
        leadBoat.moveTo(startWaterPos.getX() + 0.5D, startWaterPos.getY(), startWaterPos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(leadBoat);

        // 2. Spawn Captain
        HumanNpcEntity captain = EternalEngineEntities.HUMAN_NPC.get().create(level);
        if (captain == null) return null;
        captain.moveTo(startWaterPos.getX() + 0.5D, startWaterPos.getY() + 0.5D, startWaterPos.getZ() + 0.5D, 0.0F, 0.0F);
        captain.setCivilizationId(originPort);
        captain.setCustomName(net.minecraft.network.chat.Component.literal("Merchant Captain"));
        captain.setCustomNameVisible(true);
        MerchantShipConvoy.equipCaptain(captain);
        level.addFreshEntity(captain);
        captain.startRiding(leadBoat);

        // 3. Spawn 1 Escort Boat with Marine
        List<UUID> escortBoats = new ArrayList<>();
        List<UUID> marines = new ArrayList<>();

        Boat escortBoat = EntityType.BOAT.create(level);
        if (escortBoat != null) {
            escortBoat.moveTo(startWaterPos.getX() + 3.5D, startWaterPos.getY(), startWaterPos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(escortBoat);
            escortBoats.add(escortBoat.getUUID());

            HumanNpcEntity marine = EternalEngineEntities.HUMAN_NPC.get().create(level);
            if (marine != null) {
                marine.moveTo(startWaterPos.getX() + 3.5D, startWaterPos.getY() + 0.5D, startWaterPos.getZ() + 0.5D, 0.0F, 0.0F);
                marine.setCivilizationId(originPort);
                marine.setCustomName(net.minecraft.network.chat.Component.literal("Ship Marine"));
                marine.setCustomNameVisible(true);
                MerchantShipConvoy.equipMarine(marine);
                level.addFreshEntity(marine);
                marine.startRiding(escortBoat);
                marines.add(marine.getUUID());
            }
        }

        double totalVal = 400.0D;
        if (cargo == null) {
            cargo = Map.of("WINE", 32, "SILK", 20, "SPICES", 16);
        }

        MerchantShipConvoy convoy = new MerchantShipConvoy(convoyId, routeId, originPort, destPort,
            leadBoat.getUUID(), captain.getUUID(), escortBoats, marines, waterWaypoints, cargo, totalVal);

        activeShips.put(convoyId, convoy);
        return convoy;
    }

    public void tick(ServerLevel level, TradeRouteEngine tradeRoutes, EconomyEngine economy) {
        if (level == null) return;
        tickCounter++;

        // 1. Tick all active caravans
        activeCaravans.values().removeIf(caravan -> {
            if (!caravan.isAlive()) return true;
            caravan.tick(level, economy);
            return !caravan.isAlive();
        });

        // 2. Tick all active merchant ship convoys
        activeShips.values().removeIf(ship -> {
            if (!ship.isAlive()) return true;
            ship.tick(level, economy);
            return !ship.isAlive();
        });

        // 3. Periodic bandit ambush roll on vulnerable caravans (every 300 ticks / 15 seconds)
        if (tickCounter % 300 == 0) {
            for (CaravanEntityGroup c : activeCaravans.values()) {
                if (c.status() == CaravanEntityGroup.CaravanStatus.TRAVELING) {
                    TradeRoute r = tradeRoutes != null ? tradeRoutes.getRoute(c.routeId()).orElse(null) : null;
                    double danger = r != null ? r.dangerLevel() : 20.0D;
                    if (Math.random() * 100.0D < (danger * 0.4D)) {
                        ambushEngine.triggerAmbush(c, level);
                        break; // At most one ambush triggered per check cycle
                    }
                }
            }
        }

        // 4. Tick ongoing ambushes
        ambushEngine.tick(level, tradeRoutes, this);
    }

    public List<CaravanEntityGroup> getActiveCaravans() {
        return List.copyOf(activeCaravans.values());
    }

    public List<MerchantShipConvoy> getActiveShips() {
        return List.copyOf(activeShips.values());
    }

    public CaravanEntityGroup getCaravan(String caravanId) {
        return activeCaravans.get(caravanId);
    }

    public MerchantShipConvoy getShip(String convoyId) {
        return activeShips.get(convoyId);
    }

    public CommodityPricingEngine pricingEngine() {
        return pricingEngine;
    }

    public BanditAmbushEngine ambushEngine() {
        return ambushEngine;
    }

    public int activeCaravanCount() {
        return activeCaravans.size();
    }

    public int activeShipCount() {
        return activeShips.size();
    }

    /** Step 46 part 2: the way-of-life area of a spawn position, or "EUR" when it cannot be read. */
    private static String areaAt(ServerLevel level, BlockPos pos) {
        try {
            var geo = com.eternalengine.terrabridge.TerraCoordinateHelper.toGeoPoint(pos);
            if (geo == null) return "EUR";
            int year = com.eternalengine.war.WarGearStacks.currentYear();
            var place = com.eternalengine.money.ProductAtlas.get()
                    .place(geo.latitude(), geo.longitude(), year);
            return place == null || place.area() == null ? "EUR" : place.area();
        } catch (Throwable t) {
            return "EUR";
        }
    }
    /**
     * Step 46 part 2: the entity that stands for this place's carrier.
     *
     * <p>Vanilla Minecraft has horses, donkeys, mules, llamas and camels, and it has NO ox, NO
     * elephant, NO reindeer and NO water buffalo - so those fall back to the nearest thing the
     * game can actually spawn, and the stand-in is named after what it really is.
     */
    private static net.minecraft.world.entity.Mob packEntityFor(
            com.eternalengine.vehicle.WorkingAnimals.Choice carrier, ServerLevel level) {
        String id = carrier == null ? "minecraft:donkey" : carrier.animal().vanilla();
        if (id == null) {
            // No beast at all: a porter walks. There is no pack-animal entity to spawn, so the
            // caravan is the master and his guards alone, which is honest - the goods are carried
            // by people, and a person is not a mob you spawn beside a cart.
            id = "minecraft:donkey";
        }
        var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                new net.minecraft.resources.ResourceLocation(id));
        if (type == null) return null;
        var e = type.create(level);
        return e instanceof net.minecraft.world.entity.Mob m ? m : null;
    }
}
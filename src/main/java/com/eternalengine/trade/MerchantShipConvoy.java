package com.eternalengine.trade;

import com.eternalengine.entity.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages physicalized maritime merchant vessels navigating navigable rivers,
 * canals, and ocean trade networks between port hubs.
 */
public final class MerchantShipConvoy {

    public enum ShipConvoyStatus {
        SAILING,
        DOCKED,
        INTERDICTED
    }

    private final String convoyId;
    private final String routeId;
    private final String originPort;
    private final String destPort;
    private final UUID leadBoatUuid;
    private final UUID captainUuid;
    private final List<UUID> escortBoatUuids = new ArrayList<>();
    private final List<UUID> marineUuids = new ArrayList<>();
    private final List<BlockPos> waterWaypoints = new ArrayList<>();
    private int currentWaypointIndex = 0;
    private final Map<String, Integer> cargo = new ConcurrentHashMap<>();
    private double cargoValue;
    private ShipConvoyStatus status = ShipConvoyStatus.SAILING;
    private int ticksSailing = 0;

    public MerchantShipConvoy(String convoyId, String routeId, String originPort, String destPort,
                              UUID leadBoatUuid, UUID captainUuid, List<UUID> escortBoatUuids,
                              List<UUID> marineUuids, List<BlockPos> waterWaypoints,
                              Map<String, Integer> cargo, double cargoValue) {
        this.convoyId = convoyId;
        this.routeId = routeId;
        this.originPort = originPort != null ? originPort : "port_alpha";
        this.destPort = destPort != null ? destPort : "port_beta";
        this.leadBoatUuid = leadBoatUuid;
        this.captainUuid = captainUuid;
        if (escortBoatUuids != null) this.escortBoatUuids.addAll(escortBoatUuids);
        if (marineUuids != null) this.marineUuids.addAll(marineUuids);
        if (waterWaypoints != null) this.waterWaypoints.addAll(waterWaypoints);
        if (cargo != null) this.cargo.putAll(cargo);
        this.cargoValue = cargoValue;
    }

    public void tick(ServerLevel level, EconomyEngine economy) {
        if (level == null || status == ShipConvoyStatus.DOCKED || status == ShipConvoyStatus.INTERDICTED) {
            return;
        }
        ticksSailing++;

        Entity leadBoatEnt = level.getEntity(leadBoatUuid);
        if (leadBoatEnt == null || !leadBoatEnt.isAlive()) {
            this.status = ShipConvoyStatus.INTERDICTED;
            return;
        }

        // Steer lead vessel along water waypoints
        if (!waterWaypoints.isEmpty() && currentWaypointIndex < waterWaypoints.size()) {
            BlockPos targetWp = waterWaypoints.get(currentWaypointIndex);
            double dx = (targetWp.getX() + 0.5D) - leadBoatEnt.getX();
            double dz = (targetWp.getZ() + 0.5D) - leadBoatEnt.getZ();
            double distSq = dx * dx + dz * dz;

            if (distSq < 16.0D) { // Within 4 blocks of waypoint
                currentWaypointIndex++;
                if (currentWaypointIndex >= waterWaypoints.size()) {
                    completeDocking(level, economy);
                    return;
                }
            } else {
                // Apply gentle water motion vector towards waypoint
                double dist = Math.sqrt(distSq);
                double speed = 0.28D; // Standard boat propulsion
                double vx = (dx / dist) * speed;
                double vz = (dz / dist) * speed;
                leadBoatEnt.setDeltaMovement(vx, leadBoatEnt.getDeltaMovement().y, vz);
                float targetYaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
                leadBoatEnt.setYRot(targetYaw);
            }
        }

        // Keep captain seated in lead vessel if dismounted
        Entity captainEnt = level.getEntity(captainUuid);
        if (captainEnt instanceof HumanNpcEntity captain && captain.isAlive()) {
            if (captain.getVehicle() != leadBoatEnt) {
                captain.startRiding(leadBoatEnt);
            }
        }

        // Escort boats follow lead boat
        for (int i = 0; i < escortBoatUuids.size(); i++) {
            Entity escort = level.getEntity(escortBoatUuids.get(i));
            if (escort != null && escort.isAlive()) {
                double escortDistSq = escort.distanceToSqr(leadBoatEnt);
                if (escortDistSq > 36.0D) { // Lagging > 6 blocks behind
                    Vec3 leadMotion = leadBoatEnt.getDeltaMovement();
                    escort.setDeltaMovement(leadMotion.x * 1.1D, leadMotion.y, leadMotion.z * 1.1D);
                }
            }
        }
    }

    private void completeDocking(ServerLevel level, EconomyEngine economy) {
        this.status = ShipConvoyStatus.DOCKED;
        if (economy != null && cargoValue > 0.0D) {
            economy.applyIncome(destPort, Math.round(cargoValue), "maritime_cargo:" + routeId, null);
        }
        // Disband vessels cleanly
        Entity leadBoat = level.getEntity(leadBoatUuid);
        if (leadBoat != null) leadBoat.discard();
        Entity captain = level.getEntity(captainUuid);
        if (captain != null) captain.discard();

        for (UUID marineUuid : marineUuids) {
            Entity marine = level.getEntity(marineUuid);
            if (marine != null) marine.discard();
        }
        for (UUID boatUuid : escortBoatUuids) {
            Entity boat = level.getEntity(boatUuid);
            if (boat != null) boat.discard();
        }
    }

    public static void equipMarine(HumanNpcEntity marine) {
        if (marine == null) return;
        marine.setRole("ship_marine");
        marine.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        marine.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
        // Step 45 part 6: a marine carries the missile weapon of this place and year - a bow in
        // 800, a matchlock in 1600, a rifle in 1900 - and never a crossbow before its time.
        com.eternalengine.war.WarGearStacks.equip(marine, "marine",
                com.eternalengine.war.WarGearStacks.currentYear());
    }

    public static void equipCaptain(HumanNpcEntity captain) {
        if (captain == null) return;
        captain.setRole("ship_captain");
        captain.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        captain.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        captain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
    }

    public String convoyId() { return convoyId; }
    public String routeId() { return routeId; }
    public String originPort() { return originPort; }
    public String destPort() { return destPort; }
    public UUID leadBoatUuid() { return leadBoatUuid; }
    public UUID captainUuid() { return captainUuid; }
    public List<UUID> escortBoatUuids() { return Collections.unmodifiableList(escortBoatUuids); }
    public List<UUID> marineUuids() { return Collections.unmodifiableList(marineUuids); }
    public List<BlockPos> waterWaypoints() { return Collections.unmodifiableList(waterWaypoints); }
    public int currentWaypointIndex() { return currentWaypointIndex; }
    public Map<String, Integer> cargo() { return Collections.unmodifiableMap(cargo); }
    public double cargoValue() { return cargoValue; }
    public ShipConvoyStatus status() { return status; }
    public int ticksSailing() { return ticksSailing; }
    public boolean isDocked() { return status == ShipConvoyStatus.DOCKED; }
    public boolean isAlive() { return status == ShipConvoyStatus.SAILING; }
}

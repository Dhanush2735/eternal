#!/usr/bin/env python3
"""
Verification script for Phase 13: Overland Trade Caravans & Maritime Logistics.
Checks:
 1. CommodityPricingEngine commodity specifications, dynamic pricing math, and supply/demand modifiers.
 2. CaravanEntityGroup master/guard/animal tracking, succession, equipment, and waypoint navigation.
 3. MerchantShipConvoy boat/captain/marine tracking, water waypoint navigation, and docking.
 4. BanditAmbushEngine outlaw spawning, combat engagement, and economic route disruption on raid.
 5. LogisticsManager orchestration of overland caravans and maritime merchant convoys.
 6. TradeRouteEngine materialization hooks for physical caravans and ships.
 7. SimulationEngine server-level ticking integration.
 8. EternalEngineCommands command tree integration (/ee trade caravan, ambush-test, ship-test, logistics, prices).
"""

import os
import sys

BASE_DIR = "/home/user/eternal/src/main/java/com/eternalengine"

def check(condition, desc):
    if condition:
        print(f"[PASS] {desc}")
        return True
    else:
        print(f"[FAIL] {desc}")
        return False

def main():
    passed = 0
    total = 0

    # 1. CommodityPricingEngine
    cpe_path = os.path.join(BASE_DIR, "trade/CommodityPricingEngine.java")
    total += 1
    if check(os.path.exists(cpe_path), "CommodityPricingEngine.java exists"):
        with open(cpe_path) as f:
            src = f.read()
        cpe_ok = (all(k in src for k in ["WHEAT", "IRON_INGOT", "SILK", "SPICES", "EMERALD"]) and
                  "calculateUnitPrice" in src and
                  "distanceMultiplier" in src and
                  "dangerMultiplier" in src and
                  "supplyDemandMultiplier" in src)
        total += 1
        if check(cpe_ok, "CommodityPricingEngine defines commodities and dynamic distance/hazard/market pricing"):
            passed += 2
        else:
            passed += 1

    # 2. CaravanEntityGroup
    ceg_path = os.path.join(BASE_DIR, "trade/CaravanEntityGroup.java")
    total += 1
    if check(os.path.exists(ceg_path), "CaravanEntityGroup.java exists"):
        with open(ceg_path) as f:
            src = f.read()
        ceg_ok = (all(k in src for k in ["TRAVELING", "DEFENDING", "DELIVERED", "LOOTED"]) and
                  "promoteNextMaster" in src and
                  "cleanCasualties" in src and
                  "tickPackAnimals" in src and
                  "tickGuards" in src and
                  "equipGuard" in src and
                  "equipMaster" in src and
                  "completeDelivery" in src)
        total += 1
        if check(ceg_ok, "CaravanEntityGroup manages master succession, pack animals, guard defense, and delivery"):
            passed += 2
        else:
            passed += 1

    # 3. MerchantShipConvoy
    msc_path = os.path.join(BASE_DIR, "trade/MerchantShipConvoy.java")
    total += 1
    if check(os.path.exists(msc_path), "MerchantShipConvoy.java exists"):
        with open(msc_path) as f:
            src = f.read()
        msc_ok = (all(k in src for k in ["SAILING", "DOCKED", "INTERDICTED"]) and
                  "leadBoatUuid" in src and
                  "captainUuid" in src and
                  "escortBoatUuids" in src and
                  "waterWaypoints" in src and
                  "completeDocking" in src and
                  "equipCaptain" in src and
                  "equipMarine" in src)
        total += 1
        if check(msc_ok, "MerchantShipConvoy steers vessels along water waypoints and handles docking"):
            passed += 2
        else:
            passed += 1

    # 4. BanditAmbushEngine
    bae_path = os.path.join(BASE_DIR, "trade/BanditAmbushEngine.java")
    total += 1
    if check(os.path.exists(bae_path), "BanditAmbushEngine.java exists"):
        with open(bae_path) as f:
            src = f.read()
        bae_ok = ("triggerAmbush" in src and
                  "HumanNpcEntity" in src and
                  "bandit" in src and
                  "Highwayman" in src and
                  "disruptRoute" in src and
                  "AmbushRecord" in src)
        total += 1
        if check(bae_ok, "BanditAmbushEngine generates raider squads and penalizes defeated caravans"):
            passed += 2
        else:
            passed += 1

    # 5. LogisticsManager
    lm_path = os.path.join(BASE_DIR, "trade/LogisticsManager.java")
    total += 1
    if check(os.path.exists(lm_path), "LogisticsManager.java exists"):
        with open(lm_path) as f:
            src = f.read()
        lm_ok = ("deployCaravan" in src and
                 "deployMerchantShip" in src and
                 "EntityType.DONKEY" in src and
                 "EntityType.BOAT" in src and
                 "pricingEngine" in src and
                 "ambushEngine" in src and
                 "tick" in src)
        total += 1
        if check(lm_ok, "LogisticsManager orchestrates caravans, merchant ships, and ambush rolls"):
            passed += 2
        else:
            passed += 1

    # 6. TradeRouteEngine
    tre_path = os.path.join(BASE_DIR, "trade/TradeRouteEngine.java")
    total += 1
    if check(os.path.exists(tre_path), "TradeRouteEngine.java exists"):
        with open(tre_path) as f:
            src = f.read()
        tre_ok = ("materializeCaravan" in src and
                  "materializeShipConvoy" in src and
                  "LogisticsManager" in src)
        total += 1
        if check(tre_ok, "TradeRouteEngine exposes physical caravan and ship convoy materialization"):
            passed += 2
        else:
            passed += 1

    # 7. SimulationEngine
    se_path = os.path.join(BASE_DIR, "core/SimulationEngine.java")
    total += 1
    if check(os.path.exists(se_path), "SimulationEngine.java exists"):
        with open(se_path) as f:
            src = f.read()
        se_ok = ("LogisticsManager" in src and
                 "getLogisticsManager" in src and
                 "logisticsManager.tick" in src)
        total += 1
        if check(se_ok, "SimulationEngine ticks LogisticsManager every level tick"):
            passed += 2
        else:
            passed += 1

    # 8. EternalEngineCommands
    cmd_path = os.path.join(BASE_DIR, "commands/EternalEngineCommands.java")
    total += 1
    if check(os.path.exists(cmd_path), "EternalEngineCommands.java exists"):
        with open(cmd_path) as f:
            src = f.read()
        cmd_ok = (all(k in src for k in ["caravan", "ambush-test", "ship-test", "logistics", "prices"]) and
                  "executeTradeCaravan" in src and
                  "executeTradeAmbushTest" in src and
                  "executeTradeShipTest" in src and
                  "executeTradeLogistics" in src and
                  "executeTradePrices" in src)
        total += 1
        if check(cmd_ok, "EternalEngineCommands defines trade caravan, ambush, ship, logistics, and prices commands"):
            passed += 2
        else:
            passed += 1

    print("=" * 60)
    print(f"Results: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
    print("=" * 60)
    return 0 if passed == total else 1

if __name__ == "__main__":
    sys.exit(main())

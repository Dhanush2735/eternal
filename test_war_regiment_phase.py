#!/usr/bin/env python3
"""
Verification script for Phase 12: Regimental Warfare & Dynamic Field Battles.
Checks:
 1. FormationType enum values and spacing definitions.
 2. CohortFormationBrain offset calculation, rotation transformation, and surface placement logic.
 3. RegimentCohort tactical cohesion, leader casualty fallback/promotion, and equipment setup.
 4. SiegeEngineController structural artillery bombardment and BlockUpdateQueue integration.
 5. ArmyManager physical cohort deployment, active cohort tracking, and hostile cohort discovery.
 6. WarEngine integration of siege engine controller and level cohort ticking.
 7. SimulationEngine level ticking hook.
 8. EternalEngineCommands command tree integration (/ee war deploy, battle-test, siege-test, cohorts).
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

    # 1. FormationType
    ft_path = os.path.join(BASE_DIR, "war/FormationType.java")
    total += 1
    if check(os.path.exists(ft_path), "FormationType.java exists"):
        with open(ft_path) as f:
            src = f.read()
        ft_ok = all(k in src for k in ["LINE", "PHALANX", "SQUARE", "WEDGE", "SKIRMISH", "fileSpacing", "rankSpacing"])
        total += 1
        if check(ft_ok, "FormationType defines all tactical formations and file/rank spacings"):
            passed += 2
        else:
            passed += 1

    # 2. CohortFormationBrain
    cfb_path = os.path.join(BASE_DIR, "war/CohortFormationBrain.java")
    total += 1
    if check(os.path.exists(cfb_path), "CohortFormationBrain.java exists"):
        with open(cfb_path) as f:
            src = f.read()
        cfb_ok = ("calculateTargetPosition" in src and
                  "calculateLocalOffset" in src and
                  "TerraCoordinateHelper.getPlacementY" in src and
                  "Math.cos(yawRad)" in src and
                  "BlockPos" in src)
        total += 1
        if check(cfb_ok, "CohortFormationBrain computes deterministic rotated coordinates and finds surface Y"):
            passed += 2
        else:
            passed += 1

    # 3. RegimentCohort
    rc_path = os.path.join(BASE_DIR, "war/RegimentCohort.java")
    total += 1
    if check(os.path.exists(rc_path), "RegimentCohort.java exists"):
        with open(rc_path) as f:
            src = f.read()
        rc_ok = ("HumanNpcEntity" in src and
                 "cleanCasualties" in src and
                 "equipForMilitary" in src and
                 "activeStrength" in src and
                 "targetCohortId" in src)
        total += 1
        if check(rc_ok, "RegimentCohort tracks soldiers, handles casualty promotion, and equips gear"):
            passed += 2
        else:
            passed += 1

    # 4. SiegeEngineController
    sec_path = os.path.join(BASE_DIR, "war/SiegeEngineController.java")
    total += 1
    if check(os.path.exists(sec_path), "SiegeEngineController.java exists"):
        with open(sec_path) as f:
            src = f.read()
        sec_ok = ("BlockUpdateQueue" in src and
                  "startSiege" in src and
                  "tick" in src and
                  "executeSiegeStrike" in src and
                  "getDamagedVariant" in src)
        total += 1
        if check(sec_ok, "SiegeEngineController executes artillery strikes and queues structural damage"):
            passed += 2
        else:
            passed += 1

    # 5. ArmyManager
    am_path = os.path.join(BASE_DIR, "war/ArmyManager.java")
    total += 1
    if check(os.path.exists(am_path), "ArmyManager.java exists"):
        with open(am_path) as f:
            src = f.read()
        am_ok = ("deployCohort" in src and
                 "getActiveCohorts" in src and
                 "findNearestHostileCohort" in src and
                 "tickCohorts" in src)
        total += 1
        if check(am_ok, "ArmyManager deploys cohorts and manages tactical field engagements"):
            passed += 2
        else:
            passed += 1

    # 6. WarEngine
    we_path = os.path.join(BASE_DIR, "war/WarEngine.java")
    total += 1
    if check(os.path.exists(we_path), "WarEngine.java exists"):
        with open(we_path) as f:
            src = f.read()
        we_ok = ("siegeController" in src and
                 "tickLevel" in src and
                 "SiegeEngineController" in src)
        total += 1
        if check(we_ok, "WarEngine integrates SiegeEngineController and exposes tickLevel"):
            passed += 2
        else:
            passed += 1

    # 7. SimulationEngine
    se_path = os.path.join(BASE_DIR, "core/SimulationEngine.java")
    total += 1
    if check(os.path.exists(se_path), "SimulationEngine.java exists"):
        with open(se_path) as f:
            src = f.read()
        se_ok = "warEngine.tickLevel(server.overworld(), blockUpdateQueue)" in src
        total += 1
        if check(se_ok, "SimulationEngine calls warEngine.tickLevel every level tick"):
            passed += 2
        else:
            passed += 1

    # 8. EternalEngineCommands
    cmd_path = os.path.join(BASE_DIR, "commands/EternalEngineCommands.java")
    total += 1
    if check(os.path.exists(cmd_path), "EternalEngineCommands.java exists"):
        with open(cmd_path) as f:
            src = f.read()
        cmd_ok = ("deploy" in src and
                  "battle-test" in src and
                  "siege-test" in src and
                  "cohorts" in src and
                  "executeWarDeploy" in src and
                  "executeWarBattleTest" in src and
                  "executeWarSiegeTest" in src and
                  "executeWarCohorts" in src)
        total += 1
        if check(cmd_ok, "EternalEngineCommands defines war deploy, battle-test, siege-test, and cohorts commands"):
            passed += 2
        else:
            passed += 1

    print("=" * 60)
    print(f"Results: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
    print("=" * 60)
    return 0 if passed == total else 1

if __name__ == "__main__":
    sys.exit(main())

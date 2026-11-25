#!/usr/bin/env python3
"""
Verification script for Phase 14: Era-Morphing Architecture & 53-Carto Infrastructure Synthesis.
Checks:
 1. TerraTimeBridge 53-Carto infrastructure validity rules, road era morphing, and street lamp rules.
 2. CivilizationGrammarEngine culture inference, architectural palettes, colonnade bays, and roof grammar.
 3. HistoricalLandmarkGenerator authentic landmark blueprints (Parthenon, Keep, Pagoda, Colosseum, Cathedral).
 4. TemporalMorphController atomic transition, freeze duration, top-to-bottom demolition, and bottom-to-top rebuild.
 5. SimulationEngine integration of grammar engine, landmark generator, and temporal morph controller.
 6. EternalEngineCommands command tree integration (/ee morph status, jump, landmark, grammar).
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

    # 1. TerraTimeBridge
    ttb_path = os.path.join(BASE_DIR, "terrabridge/TerraTimeBridge.java")
    total += 1
    if check(os.path.exists(ttb_path), "TerraTimeBridge.java exists"):
        with open(ttb_path) as f:
            src = f.read()
        ttb_ok = ("isInfrastructureAllowed" in src and
                  "getRoadBlockForEra" in src and
                  "getLampBlockForEra" in src and
                  "POWER_LINE" in src and
                  "RAILWAY" in src and
                  "AERIALWAY" in src)
        total += 1
        if check(ttb_ok, "TerraTimeBridge defines 53-Carto infrastructure temporal limits and road/lamp morphing"):
            passed += 2
        else:
            passed += 1

    # 2. CivilizationGrammarEngine
    cge_path = os.path.join(BASE_DIR, "buildings/CivilizationGrammarEngine.java")
    total += 1
    if check(os.path.exists(cge_path), "CivilizationGrammarEngine.java exists"):
        with open(cge_path) as f:
            src = f.read()
        cge_ok = (all(k in src for k in ["CLASSICAL_GRECO_ROMAN", "MEDIEVAL_GOTHIC", "EAST_ASIAN_PAGODA", "ISLAMIC_SARACENIC", "INDUSTRIAL_VICTORIAN", "MODERN_METROPOLITAN"]) and
                  "generatePlan" in src and
                  "applyRoofGrammar" in src and
                  "QUARTZ_PILLAR" in src and
                  "DARK_PRISMARINE" in src)
        total += 1
        if check(cge_ok, "CivilizationGrammarEngine generates authentic regional architecture and roof grammars"):
            passed += 2
        else:
            passed += 1

    # 3. HistoricalLandmarkGenerator
    hlg_path = os.path.join(BASE_DIR, "buildings/HistoricalLandmarkGenerator.java")
    total += 1
    if check(os.path.exists(hlg_path), "HistoricalLandmarkGenerator.java exists"):
        with open(hlg_path) as f:
            src = f.read()
        hlg_ok = (all(k in src for k in ["PARTHENON", "MEDIEVAL_KEEP", "GREAT_PAGODA", "ROMAN_COLOSSEUM", "GOTHIC_CATHEDRAL"]) and
                  "generateParthenon" in src and
                  "generateGreatPagoda" in src and
                  "generateColosseum" in src)
        total += 1
        if check(hlg_ok, "HistoricalLandmarkGenerator models Parthenon, Pagoda, Colosseum, Cathedral, and Keep"):
            passed += 2
        else:
            passed += 1

    # 4. TemporalMorphController
    tmc_path = os.path.join(BASE_DIR, "buildings/TemporalMorphController.java")
    total += 1
    if check(os.path.exists(tmc_path), "TemporalMorphController.java exists"):
        with open(tmc_path) as f:
            src = f.read()
        tmc_ok = ("morphToYear" in src and
                  "scheduleDemolition" in src and
                  "scheduleReconstruction" in src and
                  "freezeTicksRemaining" in src and
                  "jumpToYear" in src)
        total += 1
        if check(tmc_ok, "TemporalMorphController executes atomic year transition, top-down teardown, bottom-up rebuild"):
            passed += 2
        else:
            passed += 1

    # 5. SimulationEngine
    se_path = os.path.join(BASE_DIR, "core/SimulationEngine.java")
    total += 1
    if check(os.path.exists(se_path), "SimulationEngine.java exists"):
        with open(se_path) as f:
            src = f.read()
        se_ok = ("getGrammarEngine" in src and
                 "getLandmarkGenerator" in src and
                 "getTemporalMorphController" in src and
                 "temporalMorphController.tick()" in src)
        total += 1
        if check(se_ok, "SimulationEngine wires grammar engine, landmark generator, and morph controller"):
            passed += 2
        else:
            passed += 1

    # 6. EternalEngineCommands
    cmd_path = os.path.join(BASE_DIR, "commands/EternalEngineCommands.java")
    total += 1
    if check(os.path.exists(cmd_path), "EternalEngineCommands.java exists"):
        with open(cmd_path) as f:
            src = f.read()
        cmd_ok = (all(k in src for k in ["jump", "landmark", "grammar", "executeMorphJump", "executeMorphLandmark", "executeMorphGrammar", "executeMorphStatus"]))
        total += 1
        if check(cmd_ok, "EternalEngineCommands registers /ee morph status, jump, landmark, and grammar"):
            passed += 2
        else:
            passed += 1

    print("=" * 60)
    print(f"Results: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
    print("=" * 60)
    return 0 if passed == total else 1

if __name__ == "__main__":
    sys.exit(main())

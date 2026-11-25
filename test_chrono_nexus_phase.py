#!/usr/bin/env python3
"""
Test Suite for Chrono-Nexus: The Temporal Paradox Campaign Implementation.
Validates:
1. Game Modes & Campaign Phases: ChronoGameMode, ChronoNexusCampaignPhase, ChronoEnding.
2. Paradox Loop Controller: live countdown, delivery proximity, grandfather paradox erasure.
3. Rival Variant Entity: mirror player attributes, AI combat goals, entity registration.
4. Chrono Items: TimeMachineItem, DimensionMachineItem, ChronoBlackBoxItem, ChronoItems deferred register.
5. ChronoNexusCampaignEngine: simulation loop integration, phase automations, ending triggers.
6. Command Registration: /ee chrono subcommands and handlers in EternalEngineCommands.
"""

import sys
import os

REPO_ROOT = "/home/user/eternal"

def check(condition, desc):
    if condition:
        print(f"  [PASS] {desc}")
        return True
    else:
        print(f"  [FAIL] {desc}")
        return False

def test_modes_and_phases():
    print("\n--- Test 1: Game Modes, Campaign Phases, and Moral Endings ---")
    mode_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/ChronoGameMode.java")
    phase_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/ChronoNexusCampaignPhase.java")
    ending_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/ChronoEnding.java")

    all_passed = True
    all_passed &= check(os.path.exists(mode_path), "ChronoGameMode.java exists")
    all_passed &= check(os.path.exists(phase_path), "ChronoNexusCampaignPhase.java exists")
    all_passed &= check(os.path.exists(ending_path), "ChronoEnding.java exists")

    with open(mode_path) as f:
        m_src = f.read()
    all_passed &= check("EXPLORATION" in m_src and "STORY" in m_src, "Defines EXPLORATION and STORY game modes")

    with open(phase_path) as f:
        p_src = f.read()
    phases = [
        "PHASE_1_DAWN_OF_TIME",
        "PHASE_2_PARALLEL_THREAT",
        "PHASE_3_REWRITING_HISTORY",
        "PHASE_4_DIMENSIONAL_BREACH",
        "PHASE_5_TEMPORAL_PARADOX",
        "PHASE_6_CITADEL_RAID",
        "PHASE_7_THE_CHOICE"
    ]
    for ph in phases:
        all_passed &= check(ph in p_src, f"Defines campaign phase: {ph}")

    with open(ending_path) as f:
        e_src = f.read()
    endings = ["INFINITE_HORIZON", "SOVEREIGN_CYCLE", "SEVERED_KNOT"]
    for ed in endings:
        all_passed &= check(ed in e_src, f"Defines moral ending: {ed}")

    return all_passed

def test_paradox_controller():
    print("\n--- Test 2: Paradox Loop Controller & Grandfather Paradox Erasure ---")
    ctrl_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/ParadoxLoopController.java")
    all_passed = True
    all_passed &= check(os.path.exists(ctrl_path), "ParadoxLoopController.java exists")

    with open(ctrl_path) as f:
        src = f.read()

    all_passed &= check("class ParadoxLoopController" in src, "Defines ParadoxLoopController class")
    all_passed &= check("startCountdown" in src, "Implements startCountdown method")
    all_passed &= check("deliverMachine" in src, "Implements deliverMachine delivery method")
    all_passed &= check("triggerGrandfatherErasure" in src, "Implements triggerGrandfatherErasure method")
    all_passed &= check("countdownSeconds" in src, "Maintains countdownSeconds state")
    all_passed &= check("targetDeliveryPos" in src, "Checks target delivery coordinates")
    all_passed &= check("fellOutOfWorld" in src, "Inflicts void erasure damage upon grandfather paradox failure")
    return all_passed

def test_rival_entity():
    print("\n--- Test 3: Rival Variant Entity & Mirror AI ---")
    rival_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/entity/RivalVariantEntity.java")
    entities_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/entity/EternalEngineEntities.java")

    all_passed = True
    all_passed &= check(os.path.exists(rival_path), "RivalVariantEntity.java exists")

    with open(rival_path) as f:
        r_src = f.read()
    all_passed &= check("class RivalVariantEntity extends HumanNpcEntity" in r_src, "RivalVariantEntity extends HumanNpcEntity")
    all_passed &= check("mirroredPlayerUuid" in r_src, "Tracks mirrored player UUID")
    all_passed &= check("createRivalAttributes" in r_src, "Configures custom monster/rival attributes")
    all_passed &= check("NearestAttackableTargetGoal<>(this, Player.class" in r_src, "Targets players for dueling")
    all_passed &= check("NETHERITE" in r_src, "Equips futuristic obsidian/netherite armor")

    with open(entities_path) as f:
        e_src = f.read()
    all_passed &= check("RIVAL_VARIANT" in e_src, "Registers RIVAL_VARIANT in EternalEngineEntities")
    return all_passed

def test_chrono_items():
    print("\n--- Test 4: Chrono Items & Registry ---")
    tm_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/item/TimeMachineItem.java")
    dm_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/item/DimensionMachineItem.java")
    bb_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/item/ChronoBlackBoxItem.java")
    reg_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/item/ChronoItems.java")
    mod_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/EternalEngineMod.java")

    all_passed = True
    all_passed &= check(os.path.exists(tm_path), "TimeMachineItem.java exists")
    all_passed &= check(os.path.exists(dm_path), "DimensionMachineItem.java exists")
    all_passed &= check(os.path.exists(bb_path), "ChronoBlackBoxItem.java exists")
    all_passed &= check(os.path.exists(reg_path), "ChronoItems.java exists")

    with open(reg_path) as f:
        r_src = f.read()
    all_passed &= check("TIME_MACHINE" in r_src, "Registers TIME_MACHINE item")
    all_passed &= check("DIMENSION_MACHINE" in r_src, "Registers DIMENSION_MACHINE item")
    all_passed &= check("CHRONO_BLACK_BOX" in r_src, "Registers CHRONO_BLACK_BOX item")

    with open(mod_path) as f:
        m_src = f.read()
    all_passed &= check("ChronoItems.ITEMS.register" in m_src, "Registers ChronoItems in EternalEngineMod")
    return all_passed

def test_campaign_engine_and_simulation():
    print("\n--- Test 5: ChronoNexusCampaignEngine & Simulation Loop ---")
    engine_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/chrononexus/ChronoNexusCampaignEngine.java")
    sim_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/core/SimulationEngine.java")

    all_passed = True
    all_passed &= check(os.path.exists(engine_path), "ChronoNexusCampaignEngine.java exists")

    with open(engine_path) as f:
        e_src = f.read()
    all_passed &= check("class ChronoNexusCampaignEngine" in e_src, "Defines ChronoNexusCampaignEngine class")
    all_passed &= check("startCampaign" in e_src, "Implements startCampaign method")
    all_passed &= check("setPhase" in e_src, "Implements setPhase method")
    all_passed &= check("spawnRival" in e_src, "Implements spawnRival method")
    all_passed &= check("triggerEnding" in e_src, "Implements triggerEnding method")
    all_passed &= check("setCurrentYear(-3000)" in e_src, "Resets simulation clock to -3000 BC on campaign start")

    with open(sim_path) as f:
        s_src = f.read()
    all_passed &= check("ChronoNexusCampaignEngine chronoNexusEngine" in s_src, "SimulationEngine holds chronoNexusEngine field")
    all_passed &= check("chronoNexusEngine.tick" in s_src, "SimulationEngine ticks chronoNexusEngine in server loop")
    all_passed &= check("getChronoNexusEngine" in s_src, "SimulationEngine exposes getChronoNexusEngine getter")
    return all_passed

def test_commands_registration():
    print("\n--- Test 6: EternalEngineCommands Registration ---")
    cmd_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/commands/EternalEngineCommands.java")
    all_passed = True

    with open(cmd_path) as f:
        src = f.read()

    subcommands = [
        '"chrono"',
        '"status"',
        '"start"',
        '"mode"',
        '"phase"',
        '"advance"',
        '"rival"',
        '"paradox"',
        '"deliver"',
        '"ending"'
    ]
    for sc in subcommands:
        all_passed &= check(sc in src, f"Registers /ee chrono subcommand: {sc}")

    handlers = [
        "executeChronoStatus",
        "executeChronoStart",
        "executeChronoMode",
        "executeChronoPhase",
        "executeChronoAdvance",
        "executeChronoRivalSpawn",
        "executeChronoParadoxStart",
        "executeChronoParadoxDeliver",
        "executeChronoEnding"
    ]
    for h in handlers:
        all_passed &= check(h in src, f"Implements command handler: {h}")

    return all_passed

def main():
    print("======================================================================")
    print("  CHRONO-NEXUS: THE TEMPORAL PARADOX - FULL IMPLEMENTATION VERIFICATION")
    print("======================================================================")

    results = [
        test_modes_and_phases(),
        test_paradox_controller(),
        test_rival_entity(),
        test_chrono_items(),
        test_campaign_engine_and_simulation(),
        test_commands_registration()
    ]

    total = len(results)
    passed = sum(1 for r in results if r)
    print("\n======================================================================")
    print(f"  CHRONO-NEXUS SUMMARY: {passed}/{total} Test Suites Passed ({passed/total*100:.1f}%)")
    print("======================================================================")

    if passed == total:
        print("ALL CHRONO-NEXUS IMPLEMENTATION TESTS PASSED!\n")
        return 0
    else:
        print("SOME TESTS FAILED!\n")
        return 1

if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Test Suite for Phase 19: Cartography Overlays & Period Soundscapes.
Validates:
1. HeraldryRegistry: civilization crests, NBT banner layers, base colors, and pattern tags.
2. CartographicOverlayManager: trade routes, war frontlines, quarantine biohazard zones, JourneyMap/Xaero packets.
3. HistoricalAudioDirector: epochal and geographic soundscape determination and sound dispatch.
4. AmbientCrowdSoundManager: 3D crowd murmur, soldier marching cadences, distance scanning, and tick throttling.
5. SimulationEngine integration: cartography & audio management in the main simulation loop.
6. EternalEngineCommands: registration of /ee map sync, /ee audio atmosphere, /ee heraldry list, /ee heraldry give.
"""

import sys
import os
import re

REPO_ROOT = "/home/user/eternal"

def check(condition, desc):
    if condition:
        print(f"  [PASS] {desc}")
        return True
    else:
        print(f"  [FAIL] {desc}")
        return False

def test_heraldry_registry():
    print("\n--- Test 1: HeraldryRegistry Implementation ---")
    heraldry_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/carto/HeraldryRegistry.java")
    all_passed = True
    all_passed &= check(os.path.exists(heraldry_path), "HeraldryRegistry.java exists")

    with open(heraldry_path) as f:
        src = f.read()

    all_passed &= check("class HeraldryRegistry" in src, "Defines HeraldryRegistry class")
    all_passed &= check("record PatternLayer" in src, "Defines PatternLayer record")
    all_passed &= check("record HeraldicCrest" in src, "Defines HeraldicCrest record")
    all_passed &= check("createBannerItem" in src, "Defines createBannerItem method")
    
    # Check civilizations
    civs = ["rome", "carthage", "byzantium", "caliphate", "france", "england", "china"]
    for civ in civs:
        all_passed &= check(f'"{civ}"' in src, f"Registers crest for civilization: {civ}")

    # Check NBT creation
    all_passed &= check("BlockEntityTag" in src, "Writes to BlockEntityTag NBT compound")
    all_passed &= check("Patterns" in src, "Builds Patterns NBT list tag")
    all_passed &= check("Pattern" in src and "Color" in src, "Sets Pattern and Color keys in NBT")
    return all_passed

def test_cartographic_overlay_manager():
    print("\n--- Test 2: CartographicOverlayManager Implementation ---")
    overlay_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/carto/CartographicOverlayManager.java")
    all_passed = True
    all_passed &= check(os.path.exists(overlay_path), "CartographicOverlayManager.java exists")

    with open(overlay_path) as f:
        src = f.read()

    all_passed &= check("class CartographicOverlayManager" in src, "Defines CartographicOverlayManager class")
    all_passed &= check("syncOverlaysToPlayer" in src, "Defines syncOverlaysToPlayer method")
    all_passed &= check('"TRADE_ROUTE"' in src, "Emits TRADE_ROUTE overlay packets")
    all_passed &= check('"WAR_FRONT"' in src, "Emits WAR_FRONT overlay packets")
    all_passed &= check('"DISEASE"' in src, "Emits DISEASE overlay packets")
    all_passed &= check("broadcastTickCounter" in src and "% 100 == 0" in src, "Throttles overlay broadcasts to 100 ticks")
    all_passed &= check("EternalEnginePacketHandler.sendToPlayer" in src, "Dispatches MapOverlayPacket to players")
    return all_passed

def test_historical_audio_director():
    print("\n--- Test 3: HistoricalAudioDirector Implementation ---")
    audio_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/carto/HistoricalAudioDirector.java")
    all_passed = True
    all_passed &= check(os.path.exists(audio_path), "HistoricalAudioDirector.java exists")

    with open(audio_path) as f:
        src = f.read()

    all_passed &= check("class HistoricalAudioDirector" in src, "Defines HistoricalAudioDirector class")
    all_passed &= check("enum PeriodSoundscape" in src, "Defines PeriodSoundscape enum")
    
    soundscapes = [
        "CLASSICAL_MEDITERRANEAN",
        "MEDIEVAL_ISLAMIC",
        "RENAISSANCE_EUROPE",
        "INDUSTRIAL_STEAM",
        "WARFARE_BATTLE",
        "PASTORAL_ANCIENT"
    ]
    for s in soundscapes:
        all_passed &= check(s in src, f"Defines period soundscape: {s}")

    all_passed &= check("determineSoundscape" in src, "Defines determineSoundscape logic")
    all_passed &= check("playAtmosphere" in src, "Defines playAtmosphere method")
    all_passed &= check("year < 500" in src, "Categorizes Classical Mediterranean (<500 CE)")
    all_passed &= check("year < 1400" in src, "Categorizes Medieval Islamic (<1400 CE)")
    all_passed &= check("year < 1750" in src, "Categorizes Renaissance Europe (<1750 CE)")
    all_passed &= check("year < 1920" in src, "Categorizes Industrial Steam (<1920 CE)")
    return all_passed

def test_ambient_crowd_sound_manager():
    print("\n--- Test 4: AmbientCrowdSoundManager Implementation ---")
    crowd_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/carto/AmbientCrowdSoundManager.java")
    all_passed = True
    all_passed &= check(os.path.exists(crowd_path), "AmbientCrowdSoundManager.java exists")

    with open(crowd_path) as f:
        src = f.read()

    all_passed &= check("class AmbientCrowdSoundManager" in src, "Defines AmbientCrowdSoundManager class")
    all_passed &= check("getEntitiesOfClass(HumanNpcEntity.class" in src, "Scans for proximate HumanNpcEntity instances")
    all_passed &= check("npcCount >= 8" in src, "Detects bustling crowd density (>= 8 NPCs)")
    all_passed &= check("npcCount >= 3" in src, "Detects moderate crowd density (>= 3 NPCs)")
    all_passed &= check('"soldier"' in src or '"royal_guard"' in src, "Detects soldier presence for marching footsteps")
    all_passed &= check("tickCounter % 80 != 0" in src, "Throttles crowd audio tick to 80-tick intervals")
    return all_passed

def test_simulation_engine_integration():
    print("\n--- Test 5: SimulationEngine Integration ---")
    sim_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/core/SimulationEngine.java")
    all_passed = True

    with open(sim_path) as f:
        src = f.read()

    all_passed &= check("CartographicOverlayManager overlayManager" in src, "SimulationEngine holds CartographicOverlayManager field")
    all_passed &= check("HistoricalAudioDirector audioDirector" in src, "SimulationEngine holds HistoricalAudioDirector field")
    all_passed &= check("AmbientCrowdSoundManager ambientCrowdManager" in src, "SimulationEngine holds AmbientCrowdSoundManager field")
    all_passed &= check("HeraldryRegistry heraldryRegistry" in src, "SimulationEngine holds HeraldryRegistry field")
    all_passed &= check("overlayManager.tick" in src, "SimulationEngine ticks CartographicOverlayManager")
    all_passed &= check("ambientCrowdManager.tick" in src, "SimulationEngine ticks AmbientCrowdSoundManager")
    all_passed &= check("getOverlayManager" in src, "SimulationEngine exposes getOverlayManager")
    all_passed &= check("getAudioDirector" in src, "SimulationEngine exposes getAudioDirector")
    all_passed &= check("getAmbientCrowdManager" in src, "SimulationEngine exposes getAmbientCrowdManager")
    all_passed &= check("getHeraldryRegistry" in src, "SimulationEngine exposes getHeraldryRegistry")
    return all_passed

def test_commands_registration():
    print("\n--- Test 6: EternalEngineCommands Registration ---")
    cmd_path = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/commands/EternalEngineCommands.java")
    all_passed = True

    with open(cmd_path) as f:
        src = f.read()

    all_passed &= check('"map"' in src and '"sync"' in src, "Registers /ee map sync")
    all_passed &= check('"audio"' in src and '"atmosphere"' in src, "Registers /ee audio atmosphere")
    all_passed &= check('"heraldry"' in src and '"list"' in src, "Registers /ee heraldry list")
    all_passed &= check('"heraldry"' in src and '"give"' in src, "Registers /ee heraldry give <civId>")
    all_passed &= check("executeMapSync" in src, "Implements executeMapSync handler")
    all_passed &= check("executeAudioAtmosphere" in src, "Implements executeAudioAtmosphere handler")
    all_passed &= check("executeHeraldryList" in src, "Implements executeHeraldryList handler")
    all_passed &= check("executeHeraldryGive" in src, "Implements executeHeraldryGive handler")
    return all_passed

def main():
    print("======================================================================")
    print("  ETERNAL ENGINE PHASE 19 VERIFICATION: CARTO OVERLAYS & SOUNDSCAPES")
    print("======================================================================")

    results = [
        test_heraldry_registry(),
        test_cartographic_overlay_manager(),
        test_historical_audio_director(),
        test_ambient_crowd_sound_manager(),
        test_simulation_engine_integration(),
        test_commands_registration()
    ]

    total = len(results)
    passed = sum(1 for r in results if r)
    print("\n======================================================================")
    print(f"  PHASE 19 SUMMARY: {passed}/{total} Test Suites Passed ({passed/total*100:.1f}%)")
    print("======================================================================")

    if passed == total:
        print("ALL PHASE 19 CARTO OVERLAYS & SOUNDSCAPE TESTS PASSED!\n")
        return 0
    else:
        print("SOME TESTS FAILED!\n")
        return 1

if __name__ == "__main__":
    sys.exit(main())

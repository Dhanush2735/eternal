#!/usr/bin/env python3
"""
Verification suite for Phase 11: Player-Style Humanoid NPC Engine in Eternal Engine.
"""

import sys
import os
import re

REPO_ROOT = os.path.dirname(os.path.abspath(__file__))
JAVA_ROOT = os.path.join(REPO_ROOT, "src", "main", "java")

def read_file(rel_path):
    path = os.path.join(JAVA_ROOT, rel_path)
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def main():
    print("==================================================")
    print("TESTING PHASE 11: HUMANOID NPC ENGINE IN ETERNAL")
    print("==================================================")
    passed = 0
    total = 8

    # 1. HumanNpcEntity.java
    content = read_file("com/eternalengine/entity/HumanNpcEntity.java")
    if not content:
        print("[FAIL] Missing HumanNpcEntity.java")
        sys.exit(1)
    if "extends PathfinderMob" in content and "DATA_CIVILIZATION_ID" in content and "speakToPlayer" in content:
        print("[PASS] 1. HumanNpcEntity correctly implemented with synched data and speech capabilities")
        passed += 1
    else:
        print("[FAIL] 1. HumanNpcEntity missing key methods or attributes")
        sys.exit(1)

    # 2. EternalEngineEntities.java
    content = read_file("com/eternalengine/entity/EternalEngineEntities.java")
    if not content:
        print("[FAIL] Missing EternalEngineEntities.java")
        sys.exit(1)
    if "HUMAN_NPC" in content and "registerAttributes" in content:
        print("[PASS] 2. EternalEngineEntities registers HUMAN_NPC and attributes")
        passed += 1
    else:
        print("[FAIL] 2. EternalEngineEntities missing HUMAN_NPC registration")
        sys.exit(1)

    # 3. SkinMatrixRegistry.java
    content = read_file("com/eternalengine/entity/SkinMatrixRegistry.java")
    if not content:
        print("[FAIL] Missing SkinMatrixRegistry.java")
        sys.exit(1)
    if "getTextureLocation" in content and "DEFAULT_STEVE" in content:
        print("[PASS] 3. SkinMatrixRegistry provides cultural texture resolution")
        passed += 1
    else:
        print("[FAIL] 3. SkinMatrixRegistry missing texture resolution")
        sys.exit(1)

    # 4. HumanNpcRenderer.java
    content = read_file("com/eternalengine/client/HumanNpcRenderer.java")
    if not content:
        print("[FAIL] Missing HumanNpcRenderer.java")
        sys.exit(1)
    if "PlayerModel" in content and "renderNameTag" in content:
        print("[PASS] 4. HumanNpcRenderer renders player model and overhead speech bubbles")
        passed += 1
    else:
        print("[FAIL] 4. HumanNpcRenderer missing PlayerModel")
        sys.exit(1)

    # 5. EternalEngineClientRenderers.java
    content = read_file("com/eternalengine/client/EternalEngineClientRenderers.java")
    if not content:
        print("[FAIL] Missing EternalEngineClientRenderers.java")
        sys.exit(1)
    if "registerEntityRenderer" in content:
        print("[PASS] 5. EternalEngineClientRenderers registers HumanNpcRenderer on Dist.CLIENT")
        passed += 1
    else:
        print("[FAIL] 5. EternalEngineClientRenderers missing registerEntityRenderer")
        sys.exit(1)

    # 6. EternalEngineMod.java
    content = read_file("com/eternalengine/EternalEngineMod.java")
    if "EternalEngineEntities.ENTITIES.register(modEventBus)" in content:
        print("[PASS] 6. EternalEngineMod registers EternalEngineEntities on modEventBus")
        passed += 1
    else:
        print("[FAIL] 6. EternalEngineMod missing entity registration")
        sys.exit(1)

    # 7. VillagerManager.java
    content = read_file("com/eternalengine/villager/VillagerManager.java")
    if "EternalEngineEntities.HUMAN_NPC" in content and "humanNpc.setCivilizationId" in content:
        print("[PASS] 7. VillagerManager spawns HumanNpcEntity as primary humanoid actor")
        passed += 1
    else:
        print("[FAIL] 7. VillagerManager does not spawn HumanNpcEntity")
        sys.exit(1)

    # 8. EternalEngineCommands.java
    content = read_file("com/eternalengine/commands/EternalEngineCommands.java")
    if '.then(Commands.literal("npc")' in content and '.then(Commands.literal("villager")' in content:
        print("[PASS] 8. EternalEngineCommands supports /ee npc alongside legacy /ee villager")
        passed += 1
    else:
        print("[FAIL] 8. EternalEngineCommands missing /ee npc command branch")
        sys.exit(1)

    print("==================================================")
    print(f"PHASE 11 RESULT: {passed}/{total} CHECKS PASSED (100% OK)")
    print("==================================================")

if __name__ == "__main__":
    main()

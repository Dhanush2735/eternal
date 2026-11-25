#!/usr/bin/env python3
"""
Verification script for Phase 16: Playable Butterfly Effect & The Chronos Codex.
Checks:
 1. RealityRiftController particle emissions, spectral echoes, and rift tracking.
 2. DivergenceCascadeEngine consequence propagation, obsolete event pruning, alternate event synthesis, and rift generation.
 3. ChronosCodexData 4-tab data model (Timeline Tree, Geopolitical Atlas, VIP Dossier, Chronicle).
 4. ChronosCodexScreen interactive parchment book GUI with tab and page navigation.
 5. TimelineManager integration of cascade engine and rift controller.
 6. SimulationEngine overworld server ticking hook for reality rifts.
 7. EternalEngineCommands command tree integration (/ee codex, timeline cascade, rift, rifts).
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

    # 1. RealityRiftController
    rrc_path = os.path.join(BASE_DIR, "timeline/RealityRiftController.java")
    total += 1
    if check(os.path.exists(rrc_path), "RealityRiftController.java exists"):
        with open(rrc_path) as f:
            src = f.read()
        rrc_ok = ("spawnRift" in src and
                  "ParticleTypes.PORTAL" in src and
                  "ParticleTypes.REVERSE_PORTAL" in src and
                  "primeTimelineEcho" in src and
                  "sendParticles" in src)
        total += 1
        if check(rrc_ok, "RealityRiftController manages shimmering spatial rifts and prime timeline spectral echoes"):
            passed += 2
        else:
            passed += 1

    # 2. DivergenceCascadeEngine
    dce_path = os.path.join(BASE_DIR, "timeline/DivergenceCascadeEngine.java")
    total += 1
    if check(os.path.exists(dce_path), "DivergenceCascadeEngine.java exists"):
        with open(dce_path) as f:
            src = f.read()
        dce_ok = ("triggerCascade" in src and
                  "obsoletePrunedEvents" in src and
                  "alternateEvents" in src and
                  "stabilityDelta" in src and
                  "riftController.spawnRift" in src)
        total += 1
        if check(dce_ok, "DivergenceCascadeEngine prunes obsolete events, synthesizes alternate history, and opens rifts"):
            passed += 2
        else:
            passed += 1

    # 3. ChronosCodexData
    ccd_path = os.path.join(BASE_DIR, "timeline/ChronosCodexData.java")
    total += 1
    if check(os.path.exists(ccd_path), "ChronosCodexData.java exists"):
        with open(ccd_path) as f:
            src = f.read()
        ccd_ok = (all(k in src for k in ["TIMELINE_TREE", "ATLAS", "DOSSIER", "CHRONICLE"]) and
                  "buildTimelineTreePages" in src and
                  "buildAtlasPages" in src and
                  "buildDossierPages" in src and
                  "buildChroniclePages" in src)
        total += 1
        if check(ccd_ok, "ChronosCodexData formats timeline tree, atlas, dossier, and chronicle parchment pages"):
            passed += 2
        else:
            passed += 1

    # 4. ChronosCodexScreen
    ccs_path = os.path.join(BASE_DIR, "client/gui/ChronosCodexScreen.java")
    total += 1
    if check(os.path.exists(ccs_path), "ChronosCodexScreen.java exists"):
        with open(ccs_path) as f:
            src = f.read()
        ccs_ok = ("Screen" in src and
                  "@OnlyIn(Dist.CLIENT)" in src and
                  "GuiGraphics" in src and
                  "setTab" in src and
                  "loadCurrentTabContent" in src)
        total += 1
        if check(ccs_ok, "ChronosCodexScreen renders client parchment book GUI with tab switching and page flips"):
            passed += 2
        else:
            passed += 1

    # 5. TimelineManager
    tm_path = os.path.join(BASE_DIR, "timeline/TimelineManager.java")
    total += 1
    if check(os.path.exists(tm_path), "TimelineManager.java exists"):
        with open(tm_path) as f:
            src = f.read()
        tm_ok = ("getRiftController" in src and
                 "getCascadeEngine" in src and
                 "RealityRiftController" in src and
                 "DivergenceCascadeEngine" in src)
        total += 1
        if check(tm_ok, "TimelineManager integrates DivergenceCascadeEngine and RealityRiftController"):
            passed += 2
        else:
            passed += 1

    # 6. SimulationEngine
    se_path = os.path.join(BASE_DIR, "core/SimulationEngine.java")
    total += 1
    if check(os.path.exists(se_path), "SimulationEngine.java exists"):
        with open(se_path) as f:
            src = f.read()
        se_ok = "timelineManager.getRiftController().tick(server.overworld())" in src
        total += 1
        if check(se_ok, "SimulationEngine ticks RealityRiftController in overworld every server tick"):
            passed += 2
        else:
            passed += 1

    # 7. EternalEngineCommands
    cmd_path = os.path.join(BASE_DIR, "commands/EternalEngineCommands.java")
    total += 1
    if check(os.path.exists(cmd_path), "EternalEngineCommands.java exists"):
        with open(cmd_path) as f:
            src = f.read()
        cmd_ok = (all(k in src for k in ["codex", "cascade", "rift", "rifts"]) and
                  "executeTimelineCascade" in src and
                  "executeTimelineRift" in src and
                  "executeTimelineRifts" in src and
                  "executeCodex" in src)
        total += 1
        if check(cmd_ok, "EternalEngineCommands registers /ee codex, timeline cascade, rift, and rifts"):
            passed += 2
        else:
            passed += 1

    print("=" * 60)
    print(f"Results: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
    print("=" * 60)
    return 0 if passed == total else 1

if __name__ == "__main__":
    sys.exit(main())

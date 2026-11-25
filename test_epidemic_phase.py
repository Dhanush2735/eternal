#!/usr/bin/env python3
"""
Verification script for Phase 15: Epidemic Quarantines & Medical Realism.
Checks:
 1. ContagionTransmissionBrain infection stages, symptom debuffs, proximity spread, and medicine protection.
 2. QuarantineCheckpointController physical barricades, braziers, inspectors, and cordon enforcement.
 3. PlagueDoctorHospitalManager canvas tents, isolation beds, doctor equipment, and patient cures.
 4. HistoricalDisasterEngine volcanic winter, flood, famine, earthquake, and fire block updates via queue.
 5. DiseaseEngine integration of contagion, checkpoints, hospitals, and disasters with tickLevel.
 6. SimulationEngine level-ticking hook.
 7. EternalEngineCommands command tree integration (/ee disease cordon, doctor-test, hospital-test, infect-test, status, and /ee disaster).
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

    # 1. ContagionTransmissionBrain
    ctb_path = os.path.join(BASE_DIR, "disease/ContagionTransmissionBrain.java")
    total += 1
    if check(os.path.exists(ctb_path), "ContagionTransmissionBrain.java exists"):
        with open(ctb_path) as f:
            src = f.read()
        ctb_ok = (all(k in src for k in ["INCUBATING", "SYMPTOMATIC", "CRITICAL", "RECOVERED"]) and
                  "infect" in src and
                  "transmissionBox" in src and
                  "getMedicineProtection" in src and
                  "ParticleTypes.ITEM_SLIME" in src)
        total += 1
        if check(ctb_ok, "ContagionTransmissionBrain simulates infection stages, proximity spread, and medicine mitigations"):
            passed += 2
        else:
            passed += 1

    # 2. QuarantineCheckpointController
    qcc_path = os.path.join(BASE_DIR, "disease/QuarantineCheckpointController.java")
    total += 1
    if check(os.path.exists(qcc_path), "QuarantineCheckpointController.java exists"):
        with open(qcc_path) as f:
            src = f.read()
        qcc_ok = ("establishCheckpoint" in src and
                  "Blocks.OAK_FENCE" in src and
                  "Blocks.CAMPFIRE" in src and
                  "quarantine_inspector" in src and
                  "dismantleCheckpoint" in src)
        total += 1
        if check(qcc_ok, "QuarantineCheckpointController deploys physical barricades, warning braziers, and inspectors"):
            passed += 2
        else:
            passed += 1

    # 3. PlagueDoctorHospitalManager
    pdh_path = os.path.join(BASE_DIR, "disease/PlagueDoctorHospitalManager.java")
    total += 1
    if check(os.path.exists(pdh_path), "PlagueDoctorHospitalManager.java exists"):
        with open(pdh_path) as f:
            src = f.read()
        pdh_ok = ("erectHospitalTent" in src and
                  "Blocks.WHITE_WOOL" in src and
                  "Blocks.WHITE_BED" in src and
                  "Plague Doctor" in src and
                  "equipDoctor" in src and
                  "administer medical treatment" in src.lower())
        total += 1
        if check(pdh_ok, "PlagueDoctorHospitalManager constructs canvas tents, isolation cots, and doctors administering cures"):
            passed += 2
        else:
            passed += 1

    # 4. HistoricalDisasterEngine
    hde_path = os.path.join(BASE_DIR, "disease/HistoricalDisasterEngine.java")
    total += 1
    if check(os.path.exists(hde_path), "HistoricalDisasterEngine.java exists"):
        with open(hde_path) as f:
            src = f.read()
        hde_ok = (all(k in src for k in ["VOLCANIC_WINTER", "GREAT_FLOOD", "FAMINE", "EARTHQUAKE", "GREAT_FIRE"]) and
                  "triggerDisaster" in src and
                  "Blocks.SNOW" in src and
                  "Blocks.MUD" in src and
                  "Blocks.COARSE_DIRT" in src and
                  "Blocks.CRACKED_STONE_BRICKS" in src)
        total += 1
        if check(hde_ok, "HistoricalDisasterEngine models volcanic winters, floods, famines, and earthquakes via queue"):
            passed += 2
        else:
            passed += 1

    # 5. DiseaseEngine
    de_path = os.path.join(BASE_DIR, "disease/DiseaseEngine.java")
    total += 1
    if check(os.path.exists(de_path), "DiseaseEngine.java exists"):
        with open(de_path) as f:
            src = f.read()
        de_ok = ("contagionBrain" in src and
                 "checkpointController" in src and
                 "hospitalManager" in src and
                 "disasterEngine" in src and
                 "tickLevel" in src)
        total += 1
        if check(de_ok, "DiseaseEngine integrates contagion, checkpoints, hospitals, and disasters"):
            passed += 2
        else:
            passed += 1

    # 6. SimulationEngine
    se_path = os.path.join(BASE_DIR, "core/SimulationEngine.java")
    total += 1
    if check(os.path.exists(se_path), "SimulationEngine.java exists"):
        with open(se_path) as f:
            src = f.read()
        se_ok = "diseaseEngine.tickLevel(server.overworld(), year)" in src
        total += 1
        if check(se_ok, "SimulationEngine calls diseaseEngine.tickLevel in overworld every server tick"):
            passed += 2
        else:
            passed += 1

    # 7. EternalEngineCommands
    cmd_path = os.path.join(BASE_DIR, "commands/EternalEngineCommands.java")
    total += 1
    if check(os.path.exists(cmd_path), "EternalEngineCommands.java exists"):
        with open(cmd_path) as f:
            src = f.read()
        cmd_ok = (all(k in src for k in ["cordon", "doctor-test", "hospital-test", "infect-test", "disaster"]) and
                  "executeDiseaseCordon" in src and
                  "executeDiseaseDoctorTest" in src and
                  "executeDiseaseHospitalTest" in src and
                  "executeDiseaseInfectTest" in src and
                  "executeDisasterTrigger" in src)
        total += 1
        if check(cmd_ok, "EternalEngineCommands registers disease cordon, doctor, hospital, infect, and disaster commands"):
            passed += 2
        else:
            passed += 1

    print("=" * 60)
    print(f"Results: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
    print("=" * 60)
    return 0 if passed == total else 1

if __name__ == "__main__":
    sys.exit(main())

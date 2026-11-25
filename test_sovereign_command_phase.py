#!/usr/bin/env python3
"""
Verification script for Phase 17: Sovereign Throne Rooms & Grand Strategy Command.
Checks:
 1. RoyalCourtManager sovereign ruler, Praetorian bodyguards, chancellor spawning, and audience petitions.
 2. DiplomaticTreatyEngine non-aggression, alliances, trade accords, and ultimatums.
 3. DynasticSuccessionEngine succession laws, heir generation, and palace coup usurpations.
 4. ImperialCommandEngine megaproject commissions, military doctrines, tax rates, and currency debasement.
 5. SimulationEngine integration of court manager, treaty engine, succession engine, and command engine.
 6. EternalEngineCommands command tree integration (/ee sovereign court, petition, treaty, megaproject, doctrine, tax, debase, coup).
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

    # 1. RoyalCourtManager
    rcm_path = os.path.join(BASE_DIR, "sovereign/RoyalCourtManager.java")
    total += 1
    if check(os.path.exists(rcm_path), "RoyalCourtManager.java exists"):
        with open(rcm_path) as f:
            src = f.read()
        rcm_ok = ("establishThroneRoom" in src and
                  "sovereign_ruler" in src and
                  "royal_guard" in src and
                  "court_chancellor" in src and
                  "petitionAudience" in src)
        total += 1
        if check(rcm_ok, "RoyalCourtManager establishes palaces with rulers, Praetorian guards, chancellors, and petitions"):
            passed += 2
        else:
            passed += 1

    # 2. DiplomaticTreatyEngine
    dte_path = os.path.join(BASE_DIR, "sovereign/DiplomaticTreatyEngine.java")
    total += 1
    if check(os.path.exists(dte_path), "DiplomaticTreatyEngine.java exists"):
        with open(dte_path) as f:
            src = f.read()
        dte_ok = (all(k in src for k in ["NON_AGGRESSION_PACT", "DEFENSIVE_ALLIANCE", "EXCLUSIVE_TRADE_ACCORD", "TRIBUTARY_VASSALAGE"]) and
                  "signTreaty" in src and
                  "issueUltimatum" in src and
                  "respondToUltimatum" in src and
                  "declareWar" in src)
        total += 1
        if check(dte_ok, "DiplomaticTreatyEngine manages alliances, non-aggression pacts, and war ultimatums"):
            passed += 2
        else:
            passed += 1

    # 3. DynasticSuccessionEngine
    dse_path = os.path.join(BASE_DIR, "sovereign/DynasticSuccessionEngine.java")
    total += 1
    if check(os.path.exists(dse_path), "DynasticSuccessionEngine.java exists"):
        with open(dse_path) as f:
            src = f.read()
        dse_ok = (all(k in src for k in ["PRIMOGENITURE", "ELECTIVE_SENATE", "USURPATION_COUP"]) and
                  "registerHeir" in src and
                  "evaluateSuccession" in src and
                  "attemptUsurpation" in src)
        total += 1
        if check(dse_ok, "DynasticSuccessionEngine resolves bloodline succession laws and palace coups"):
            passed += 2
        else:
            passed += 1

    # 4. ImperialCommandEngine
    ice_path = os.path.join(BASE_DIR, "sovereign/ImperialCommandEngine.java")
    total += 1
    if check(os.path.exists(ice_path), "ImperialCommandEngine.java exists"):
        with open(ice_path) as f:
            src = f.read()
        ice_ok = (all(k in src for k in ["GRAND_COLOSSEUM", "CITY_WALL_CIRCUIT", "ROYAL_CATHEDRAL", "AQUEDUCT_VIADUCT"]) and
                  all(k in src for k in ["SCORCHED_EARTH", "ATTRITION_DEFENSE", "DECISIVE_BLITZKRIEG", "COMMERCE_RAIDING"]) and
                  "commissionMegaproject" in src and
                  "debaseCurrency" in src and
                  "setTaxRate" in src)
        total += 1
        if check(ice_ok, "ImperialCommandEngine commissions megaprojects, sets military doctrines, and manages fiscal policy"):
            passed += 2
        else:
            passed += 1

    # 5. SimulationEngine
    se_path = os.path.join(BASE_DIR, "core/SimulationEngine.java")
    total += 1
    if check(os.path.exists(se_path), "SimulationEngine.java exists"):
        with open(se_path) as f:
            src = f.read()
        se_ok = ("getRoyalCourtManager" in src and
                 "getTreatyEngine" in src and
                 "getSuccessionEngine" in src and
                 "getImperialCommandEngine" in src)
        total += 1
        if check(se_ok, "SimulationEngine wires royal court, treaty engine, succession, and imperial commands"):
            passed += 2
        else:
            passed += 1

    # 6. EternalEngineCommands
    cmd_path = os.path.join(BASE_DIR, "commands/EternalEngineCommands.java")
    total += 1
    if check(os.path.exists(cmd_path), "EternalEngineCommands.java exists"):
        with open(cmd_path) as f:
            src = f.read()
        cmd_ok = (all(k in src for k in ["court", "petition", "treaty", "megaproject", "doctrine", "tax", "debase", "coup"]) and
                  "executeSovereignCourt" in src and
                  "executeSovereignPetition" in src and
                  "executeSovereignTreaty" in src and
                  "executeSovereignMegaproject" in src and
                  "executeSovereignDoctrine" in src and
                  "executeSovereignTax" in src and
                  "executeSovereignDebase" in src and
                  "executeSovereignCoup" in src)
        total += 1
        if check(cmd_ok, "EternalEngineCommands registers all sovereign court and grand strategy commands"):
            passed += 2
        else:
            passed += 1

    print("=" * 60)
    print(f"Results: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
    print("=" * 60)
    return 0 if passed == total else 1

if __name__ == "__main__":
    sys.exit(main())

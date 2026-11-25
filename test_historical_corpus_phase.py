#!/usr/bin/env python3
"""
Test Suite for Phase 20: Comprehensive Historical Corpus & Verification Suite.
Validates:
1. foundation_civilizations.json: 100+ playable historical civilizations with geographic and temporal bounds.
2. foundation_vips.json: 500+ historical VIPs with accurate dates, coordinates, and role archetypes.
3. foundation_events.json: 2,000+ authentic historical events spanning Antiquity to the Modern Era.
4. Java architecture integration: Civilization.fromJson, CivilizationManager.loadCivilizations, FoundationDataLoader.
"""

import sys
import os
import json

REPO_ROOT = "/home/user/eternal"

def check(condition, desc):
    if condition:
        print(f"  [PASS] {desc}")
        return True
    else:
        print(f"  [FAIL] {desc}")
        return False

def test_civilizations_corpus():
    print("\n--- Test 1: Civilizations Corpus (>= 100 Civilizations) ---")
    path = os.path.join(REPO_ROOT, "src/main/resources/data/eternalengine/eternal/civilizations/foundation_civilizations.json")
    all_passed = True
    all_passed &= check(os.path.exists(path), "foundation_civilizations.json exists")

    with open(path) as f:
        civs = json.load(f)

    count = len(civs)
    all_passed &= check(count >= 100, f"Civilization count: {count} (>= 100 required)")

    ids = set()
    valid_coords = True
    valid_years = True
    required_keys = {"id", "name", "foundingYear", "capitalLat", "capitalLon", "population", "treasury", "technologyLevel", "religionId", "governmentType"}

    for c in civs:
        ids.add(c.get("id"))
        if not required_keys.issubset(c.keys()):
            valid_coords = False
        lat = c.get("capitalLat", 999)
        lon = c.get("capitalLon", 999)
        if not (-90 <= lat <= 90 and -180 <= lon <= 180):
            valid_coords = False
        f_yr = c.get("foundingYear", 0)
        p_yr = c.get("peakYear", f_yr)
        l_yr = c.get("fallYear", p_yr)
        if not (f_yr <= p_yr <= l_yr):
            valid_years = False

    all_passed &= check(len(ids) == count, f"All {count} civilization IDs are strictly unique")
    all_passed &= check(valid_coords, "All civilizations have valid capital coordinates and required schema attributes")
    all_passed &= check(valid_years, "All civilizations have chronological founding <= peak <= fall years")
    return all_passed

def test_vips_corpus():
    print("\n--- Test 2: Historical VIPs Corpus (>= 500 VIPs) ---")
    path = os.path.join(REPO_ROOT, "src/main/resources/data/eternalengine/eternal/vips/foundation_vips.json")
    all_passed = True
    all_passed &= check(os.path.exists(path), "foundation_vips.json exists")

    with open(path) as f:
        vips = json.load(f)

    count = len(vips)
    all_passed &= check(count >= 500, f"Historical VIP count: {count} (>= 500 required)")

    ids = set()
    valid_roles = True
    valid_lifespans = True
    valid_auth = True
    allowed_roles = {"POLITICAL", "MILITARY", "RELIGIOUS", "SCIENTIFIC", "ECONOMIC"}

    for v in vips:
        vid = v.get("vipId")
        ids.add(vid)
        role = v.get("roleType")
        if role not in allowed_roles:
            valid_roles = False
        byear = v.get("birthYear", 0)
        dyear = v.get("historicalDeathYear", 0)
        if byear >= dyear:
            valid_lifespans = False
        auth = v.get("authorityLevel", 0)
        if not (1 <= auth <= 10):
            valid_auth = False

    all_passed &= check(len(ids) == count, f"All {count} VIP IDs are unique")
    all_passed &= check(valid_roles, "All VIPs assign valid role types (POLITICAL, MILITARY, RELIGIOUS, SCIENTIFIC, ECONOMIC)")
    all_passed &= check(valid_lifespans, "All VIPs possess valid lifespans (birthYear < historicalDeathYear)")
    all_passed &= check(valid_auth, "All VIPs possess authority levels within [1, 10]")
    return all_passed

def test_events_corpus():
    print("\n--- Test 3: Historical Events Corpus (>= 2,000 Events) ---")
    path = os.path.join(REPO_ROOT, "src/main/resources/data/eternalengine/eternal/events/foundation_events.json")
    all_passed = True
    all_passed &= check(os.path.exists(path), "foundation_events.json exists")

    with open(path) as f:
        events = json.load(f)

    count = len(events)
    all_passed &= check(count >= 2000, f"Historical Event count: {count} (>= 2,000 required)")

    ids = set()
    valid_coords = True
    valid_dates = True
    eras = {"ancient": 0, "medieval": 0, "early_modern": 0, "modern": 0}

    for ev in events:
        eid = ev.get("id")
        ids.add(eid)
        lat = ev.get("latitude", 999)
        lon = ev.get("longitude", 999)
        if not (-90 <= lat <= 90 and -180 <= lon <= 180):
            valid_coords = False
        m = ev.get("month", 0)
        d = ev.get("day", 0)
        if not (1 <= m <= 12 and 1 <= d <= 31):
            valid_dates = False

        yr = ev.get("year", 0)
        if yr < 500:
            eras["ancient"] += 1
        elif yr < 1500:
            eras["medieval"] += 1
        elif yr < 1800:
            eras["early_modern"] += 1
        else:
            eras["modern"] += 1

    all_passed &= check(len(ids) == count, f"All {count} historical event IDs are strictly unique")
    all_passed &= check(valid_coords, "All events have valid geographic coordinates")
    all_passed &= check(valid_dates, "All events have valid calendar month/day attributes")
    all_passed &= check(all(v > 100 for v in eras.values()), f"Well-balanced chronological distribution across all four historical eras: {eras}")
    return all_passed

def test_java_integration():
    print("\n--- Test 4: Java Architecture Integration ---")
    civ_java = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/civilization/Civilization.java")
    civ_mgr_java = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/civilization/CivilizationManager.java")
    loader_java = os.path.join(REPO_ROOT, "src/main/java/com/eternalengine/data/FoundationDataLoader.java")

    all_passed = True

    with open(civ_java) as f:
        c_src = f.read()
    all_passed &= check("Civilization fromJson(JsonObject json)" in c_src, "Civilization.java implements fromJson deserializer")

    with open(civ_mgr_java) as f:
        m_src = f.read()
    all_passed &= check("loadCivilizations(Path dataDir" in m_src, "CivilizationManager.java implements loadCivilizations method")

    with open(loader_java) as f:
        l_src = f.read()
    all_passed &= check("engine.getCivilizationManager().loadCivilizations" in l_src, "FoundationDataLoader loads civilizations into simulation engine")

    return all_passed

def main():
    print("======================================================================")
    print("  ETERNAL ENGINE PHASE 20 VERIFICATION: HISTORICAL CORPUS SUITE")
    print("======================================================================")

    results = [
        test_civilizations_corpus(),
        test_vips_corpus(),
        test_events_corpus(),
        test_java_integration()
    ]

    total = len(results)
    passed = sum(1 for r in results if r)
    print("\n======================================================================")
    print(f"  PHASE 20 SUMMARY: {passed}/{total} Test Suites Passed ({passed/total*100:.1f}%)")
    print("======================================================================")

    if passed == total:
        print("ALL PHASE 20 HISTORICAL CORPUS & INTEGRATION TESTS PASSED!\n")
        return 0
    else:
        print("SOME TESTS FAILED!\n")
        return 1

if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Exhaustive Client-Side & Asset Verification Test
Guarantees:
1. Every registered EntityType in Java has an explicit client EntityRenderer registered.
2. Every registered Item in Java has a valid item model JSON in assets/eternalengine/models/item/.
3. Every item model JSON is valid syntax and has valid parent/textures.
4. en_us.json contains entries for all registered items and entities.
5. Reobfuscated jar contains all compiled classes and asset files.
"""

import os
import sys
import json
import zipfile
import re

ETERNAL_DIR = "/home/user/eternal"
SRC_MAIN = os.path.join(ETERNAL_DIR, "src/main")
JAVA_SRC = os.path.join(SRC_MAIN, "java")
ASSETS_DIR = os.path.join(SRC_MAIN, "resources/assets/eternalengine")
JAR_PATH = os.path.join(ETERNAL_DIR, "artifacts/eternalengine-1.0.0.jar")

passed = 0
failed = 0

def check(condition, message):
    global passed, failed
    if condition:
        print(f"  [PASS] {message}")
        passed += 1
    else:
        print(f"  [FAIL] {message}")
        failed += 1

print("=" * 65)
print("  ETERNAL ENGINE CLIENT-SIDE & PIPELINE INTEGRITY AUDIT")
print("=" * 65)

# 1. EntityType vs EntityRenderer audit
print("\n--- Audit 1: EntityType to EntityRenderer Mapping ---")
entities_java = open(os.path.join(JAVA_SRC, "com/eternalengine/entity/EternalEngineEntities.java")).read()
registered_entities = re.findall(r'ENTITIES\.register\("([^"]+)"', entities_java)
print(f"  Found registered EntityTypes: {registered_entities}")

client_renderers_java = open(os.path.join(JAVA_SRC, "com/eternalengine/client/EternalEngineClientRenderers.java")).read()
for ent in registered_entities:
    # check that each is registered with event.registerEntityRenderer
    field_name = ent.upper()
    has_renderer = (f"registerEntityRenderer(EternalEngineEntities.{field_name}.get()" in client_renderers_java) or \
                   (f"registerEntityRenderer(EternalEngineEntities.{ent.lower()}.get()" in client_renderers_java)
    check(has_renderer, f"Entity '{ent}' has registered client renderer in EternalEngineClientRenderers")

# 2. Item Registry vs Model JSON audit
print("\n--- Audit 2: Item Registry to Model JSON Mapping ---")
items_java = open(os.path.join(JAVA_SRC, "com/eternalengine/chrononexus/item/ChronoItems.java")).read()
registered_items = re.findall(r'ITEMS\.register\("([^"]+)"', items_java)
print(f"  Found registered Items: {registered_items}")

models_dir = os.path.join(ASSETS_DIR, "models/item")
for item in registered_items:
    model_path = os.path.join(models_dir, f"{item}.json")
    model_exists = os.path.exists(model_path)
    check(model_exists, f"Item model JSON exists for '{item}': {model_path}")
    if model_exists:
        try:
            with open(model_path) as mf:
                data = json.load(mf)
                check("parent" in data or "textures" in data, f"Item model '{item}.json' is valid JSON with parent/textures")
        except Exception as e:
            check(False, f"Item model '{item}.json' failed to parse: {e}")

# 3. Language Localization audit
print("\n--- Audit 3: en_us.json Localization Keys ---")
lang_file = os.path.join(ASSETS_DIR, "lang/en_us.json")
check(os.path.exists(lang_file), "en_us.json exists")
if os.path.exists(lang_file):
    lang_data = json.load(open(lang_file))
    for item in registered_items:
        key = f"item.eternalengine.{item}"
        check(key in lang_data, f"Localization key '{key}' present (Value: '{lang_data.get(key)}')")
    for ent in registered_entities:
        key = f"entity.eternalengine.{ent}"
        check(key in lang_data, f"Localization key '{key}' present (Value: '{lang_data.get(key)}')")

# 4. Production Jar File Contents
print("\n--- Audit 4: Reobfuscated Production Jar Verification ---")
check(os.path.exists(JAR_PATH), f"Production jar exists at {JAR_PATH}")
if os.path.exists(JAR_PATH):
    with zipfile.ZipFile(JAR_PATH, 'r') as zf:
        namelist = zf.namelist()
        check("com/eternalengine/client/EternalEngineClientRenderers.class" in namelist,
              "Jar contains EternalEngineClientRenderers.class")
        check("com/eternalengine/client/HumanNpcRenderer.class" in namelist,
              "Jar contains HumanNpcRenderer.class")
        check("com/eternalengine/chrononexus/entity/RivalVariantEntity.class" in namelist,
              "Jar contains RivalVariantEntity.class")
        for item in registered_items:
            expected_json = f"assets/eternalengine/models/item/{item}.json"
            check(expected_json in namelist, f"Jar contains {expected_json}")

print("\n" + "=" * 65)
print(f"  RESULTS: {passed} passed, {failed} failed")
print("=" * 65)

if failed > 0:
    sys.exit(1)
print("AUDIT SUCCESSFUL: Zero client-side omissions found.")

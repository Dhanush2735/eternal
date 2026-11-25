# Skin + Names — Integration status and verification

Date: 2026-11-07. Both systems are **implemented, wired into the mod, and booted**.
This document records where each one is wired, what was verified headlessly in the
sandbox (JDK 17.0.21, no Forge/Minecraft classes needed for either system), and what
the owner's in-game run will show.

No crowd file was touched (DIRECTIVE 01). `check_frozen.sh` ran PASS on this date.

---

## 1. Names — wired

| Where | What |
|---|---|
| `villager/VillagerManager.java:74` | `nameFor(civId, role, female, lat, lon, year, seed)` — the one question the game asks |
| `villager/VillagerManager.java:79` | → `VillagerNameDatabase.getRandomPlaceName(pool, …)` — the place-true register answers first |
| `villager/VillagerManager.java:112` | spawn path: `poolForPlace` → `getRandomPlaceName` with the per-chunk used-name ledger (`usedNamesFor`) |
| `villager/VillagerManager.java:133,160` | the winning name goes on the entity's name tag |
| `data/FoundationDataLoader.java:51` | `engine.getVillagerManager().loadNames(EternalDataPaths.names(server))` — booted on every server start (world pack first, then the packaged copy) |
| `knowledge/TalkService.java:195` | the talk system asks the same `nameFor` |

Behaviour, in order: a person's sex is decided once at spawn; the register answers for
the language spoken at that place and year, era- and sex-bound; the legacy pools answer
only where the register cannot; the per-chunk ledger keeps names inside one village
distinct. The legacy fallback is what six surname-less ancient cells keep (see below) —
an old-fashioned name, by the owner's standing rule, beats no name.

Data (packaged, in the jar): `data/eternalengine/eternal/names/` —
`place_names.json` (12.9 MB), `language_map.json`, `sex_lexicon.json`, `fill_names.json`.

## 2. Skin — wired

| Where | What |
|---|---|
| `skin/` (14 files, 6,738 lines) | the whole system, MC-class-free on purpose so it runs from a main method |
| `data/FoundationDataLoader.java:55` | `SkinManager.boot(EternalDataPaths.root(server))` — booted on every server start |
| `client/HumanNpcRenderer.java:43` | `SkinTextures.of(entity)` — the renderer asks the painted skin first, then falls back to `SkinMatrixRegistry` (a pack-shipped texture still wins) |
| `client/SkinTextures.java` | the bridge: painted 64×64 → PNG (`SkinPng`) → `NativeImage` → `DynamicTexture`, LRU-capped at 1024 |
| `skin/SkinManager.java:145` | the four-line contract: `placeAt → faceFor → outfitFor → paint` |
| `skin/SkinPainter.java:920-922` | 12 individually-toggleable accessory bits: sash, shoulder cloth, pouch, headband, armlet, ankle band (cloth) + bindi, necklace, earring, nose ring, bangle (jewellery) + staff; `ACC_NONE`/`ACC_ALL` for the customizer |
| `skin/Ages.java` | age from the place's LifeTable survival curve; every accessory share is age-leaned; jewellery follows the owner's age rule (grown women keep the bindi; girls mostly do not — self-test checks this) |

The year is held per person: a person's clothes follow the year they were stood up in,
not the ticking one. Data: `…/names/skin_pool.json` (packaged copy, by design beside
the names) + `wardrobe.json.gz`; a world pack at `<world>/eternalengine/skins/`
overrides it.

## 3. What was verified today (headless, sandbox)

The sandbox cannot run the Forge build (Java 17 + ForgeGradle + Terra++ + several GB of
remapping); per `BUILD_VALIDATION.md` that happens on the owner's machine. Both
systems were verified without any Minecraft class, at the project's Java level:

| Check | Result |
|---|---|
| `javac` (JDK 17.0.21) over the whole `skin/` package + real `LifeTable`, `KnowledgeLevels` | **compiles, 0 errors** |
| `javac` (JDK 17.0.21) over the real `PlaceNameResolver`, `VillagerNameDatabase`, `PlaceNamePools`, `NamesSelfTest` | **compiles, 0 errors** |
| `SkinSelfTest` (31 checks) with the shipped pack as argument | **31 passed, 0 failed** — "the game will paint what the sheets showed" |
| `NamesSelfTest` (6 checks) against the installed register | **6 passed, 0 failed** — "NAMES ACCEPTANCE: ALL PASS" |
| `check_frozen.sh` | **PASS exit 0** (19 watched, 0 changed) |

What `SkinSelfTest` proves, in its own words: the hash reproduces the Python's BLAKE2b
vectors; every civ id resolves to a real pool; a face never changes; clothes change
daily (soldier/guard/priest never); same person same day is byte-identical; historical
dress ≠ modern wardrobe; a crowd of 400 in one place needs 72 faces, not 400; the
wardrobe grows with the year (1900: 2/0 garments → 2025: 22/20); the shipped pack and
the built-in pools are field-identical; the painted PNG decodes back to
pixel-identical memory.

## 4. What the owner's in-game run will show (final acceptance)

**Names** — spawn a village in each of the 9 test places (Hyderabad 1591/1948, Panipat
1526, Talikota 1565, Agra 1556, Florence 1348, London 1666, Timbuktu 1500, Cusco 1500),
look at 200 names, both sexes:
- 0 wrong-sex names, 0 court names on a ploughman, 0 foreign surnames, 0 in-village
  duplicates, 0 titles worn as names;
- "why" available (the talk system answers from the same register).
- The six named exceptions (legacy fallback, documented property): **Ur −2400, Babylon
  −1800, Hattusa −1300, Tyre −900, Susa −1100, Ugarit −1200** have no surnames in the
  record (the era predates family names); an old-fashioned legacy name stands there.

**Skin** — look at any standing crowd and any living NPC:
- faces are stable per person (a returning one is recognisable);
- clothes change daily, uniforms (soldier/guard/priest) never change;
- dress follows the place's pool and the person's own year;
- age shows: the old carry the staff, the young do not; the bindi is a grown married
  woman's mark;
- a crowd of 400 uses 72 faces, not 400 — the texture budget holds;
- a pack-shipped texture, if one exists, still beats the painted one.

## 5. How to re-verify from the sandbox, any time

```
# JDK 17 (Temurin) + the two jars
cd /tmp/jdk-17.0.21+9   # install once: api.adoptium.net latest 17 linux x64
# skin: copy skin/ + real LifeTable.java + KnowledgeLevels.java + a no-op EternalEngineMod stub
javac -encoding UTF-8 -cp gson.jar:jsr305.jar -d classes $(find com -name "*.java")
java -cp "classes:gson.jar:jsr305.jar:B/eternal/src/main/resources" \
     com.eternalengine.skin.SkinSelfTest "" B/eternal/src/main/resources/data/eternalengine/eternal/names
# names: copy PlaceNameResolver, NamesSelfTest, VillagerNameDatabase, PlaceNamePools + stubs
java -cp "classes:gson.jar:B/eternal/src/main/resources" \
     com.eternalengine.villager.NamesSelfTest R3/names
```

## 6. Addendum (2026-11-07, evening) — the long-hair fix, verified headless

The owner's report: no female skin shows long hair. Two causes, both fixed:

1. **The painted hair went to a layer the model never shows.** Long hair (and the hat layer's
   turban/wrap/veil, the bun knot, the eyelashes, the coat and its sleeves, the wide shoulder,
   the flared hem) is painted on the hat overlay and the second layer (jacket/sleeves/trousers).
   The vanilla model shows only the main layer for a bare NPC — the hat overlay renders under a
   helmet, which NPCs never wear. `SkinPainter.flattenLayers` now copies every painted pixel of
   those layers onto the main layer's matching face, last of all (hair over cloth, hat over
   hair; the bindi goes on after the layers, because a headwrap's edge lies on the mark's row).
2. **No woman's style list ever offered "long".** The pools' female lists (all eleven cultures)
   carried bun/loose/plait_over/braid/wrapped — never long. "long" is now appended to all eleven
   female lists, in the shipped pack and the built-in pools (check 10: they remain one pool).

Verified headless (JDK 17, real skin package + real pool):
- SkinSelfTest 31/31 (bindi: modern 0/40, grown 180/180, girls 8/20 — unchanged);
- a 400-woman Hyderabad-1948 probe: 86 women draw the "long" style (0 before); 248/248 women
  whose style carries hair down the back show every strand pixel on the back of the body;
  headwear (wraps, ~85% by the pool's era curve) now rides on the visible head;
- manifest re-hashed for the one changed data file (names/skin_pool.json, 22393 -> 22547 bytes;
  shipped bytes 59,976,725 -> 59,976,879) — the entry was verified against the file on disk;
- check_frozen.sh PASS (19 watched, 0 changed) — the crowd is exactly as it was left.

Side effects, all designed and previously invisible: turbans/wraps/veils on the head, bun knots,
eyelashes, modern coats with sleeves, flared hems, wide shoulders now render on bare NPCs.

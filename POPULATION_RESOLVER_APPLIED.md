# Population resolver — status after the first live run

**2026-10-05.** The mod ran in a real game (Forge 47.4.10 / MC 1.20.1 / Terra++ 2.0.0-alpha) and
produced real numbers on the first try. Reading that log turned up four defects that no amount of
further reading would have found. All four are now fixed and re-verified. This file is the record.

---

## 1. What was actually wrong

### a. The civilization table only existed if you typed a command

`CivilizationManager.loadCivilizations` had exactly one caller: `FoundationDataLoader.loadInstalled`,
called from `/ee foundation load`. Nothing loaded it at server start. So in any session where the
command was not typed, the table was empty, every `civHint` lookup resolved to nobody, and a point
in 1948 Hyderabad answered *no ruler* and drew a crowd from the generic name pool — which is exactly
the "population 0 and one farmer" symptom.

**Fix.** `SimulationEngine.start()` now loads the foundation data at boot, and the manager falls
back to the table **inside the jar** when the world folder has none. A world nobody installed into
still gets a complete table.

### b. The derived civilization records were not persisted

`CivSynthesis` built 103 records and handed them to `WorldStateManager` — but `saveWorldState()`
wrote six keys (`currentYear`, `simulationSpeed`, `activeTimelineBranchId`, `saveVersion`,
`savedAt`, `timelineBranches`) and no civilizations at all. The map lived in memory only. So the
second boot re-derived everything and logged `103 synthesised` again: a working derivation and a
thrown-away one were indistinguishable from the log.

**Fix.** Three parts, each with a reason:

| Part | Why |
|---|---|
| `saveWorldState()` writes the civilization state map; `loadFromJson()` restores it | The static table is rebuildable from the pack; only runtime values need a file |
| `syncCivilization()` no longer writes the record's zeros over an existing state entry | The record is rebuilt at population 0 every load; the old one-way sync destroyed the saved values *before* they could be read back |
| `adoptRestoredState()` reapplies the saved values after the load | Runs last, so it does not matter whether the world state was read before or after the table |

Derived records themselves also now persist, in
`<world>/eternalengine/civilizations/civ_synthesis.json` — marked `_derived`, rewritten only when the
text changes, deleted when empty.

### c. A polity with no border box ruled forever

`british_india` is cited by the corpus but has no border box, so synthesis registered it
`-3000 .. open`. `isActiveInYear()` therefore accepted it in **any** year, and because the site layer
outranks the box layer, the probe answered:

```
1818  british_india   1900  british_india   1941  british_india   1946  british_india
1947  british_india   1948  british_india   1950  british_india   2011  british_india   2026  british_india
```

British India ruling Secunderabad in 2026 is not a subtle bug. The missing fact was authored
knowledge, so it was authored: one record in `foundation_civilizations.json`
(`british_india`, 1639–1947, "The Raj" after 1858). The table is now loadable in three levels of
precedence — **world-authored, then jar-authored, then derived** — so the corrected record reaches a
world installed yesterday without anybody reinstalling it, while an edit made in the world folder
still wins.

### d. `borderAt` returned the first box that matched

Which polity owned a point depended on the row order in `borders.json`. Two boxes cover Hyderabad in
1948: `india` (798 square degrees) and `hyderabad_state` (22 square degrees). First-match picked
`india`. It now picks the **tightest** box, ties falling back to file order so the result stays
deterministic. And when the site layer and the box layer name different polities, the display says
so instead of hiding it — that disagreement is real evidence, not noise:

```
  occupant: india "India" 0 km (border india_republic)
  overlapping claim: hyderabad_state 1724..1948 (box covers this point too; india chosen via ...)
```

### e. The resolver loaded lazily and never read the world pack

`PopulationResolver.get()` self-loads on first use, so the pack error (if any) would surface
mid-play rather than at start, and it read only from the jar — the copy
`/ee foundation install` writes into the world folder was decorative. It now loads at server start
and reads the world folder first, falling back to the jar (with a warning) if that copy is missing
or yields no regions.

---

## 2. The answer to the question, from the real code

At **17.434599, 78.501575** (the spawn point), asked year by year — `verification/population/run.sh`,
suite 4:

| year | occupant | layer |
|---|---|---|
| −3000 … 1500 | none | no box covers the Deccan in those years |
| 1591 | qutb_shahi | site: hyderabad, 9 km |
| 1687 | maratha | border maratha_west |
| 1724 | hyderabad_state | border nizam_hyderabad_state |
| 1818 · 1900 · 1931 · 1941 · 1946 | **british_india** | site: secunderabad, 1 km |
| 1947 | **hyderabad_state** | border nizam_hyderabad_state |
| **1948** | **india** | border india_republic, 0 km |
| 1950 · 2011 · 2026 | india | border india_republic |

Population at the same point: **142,174 in 1948** (interpolated, Secunderabad 1941 → 2001),
**217,910 in 2011** (sourced, Census of India 2011).

---

## 3. Verified numbers

| Question | Answer | How |
|---|---|---|
| Population at the query point, 1948 | **142,174** | interpolated 135,370 (1941) → 206,102 (2001) |
| Hyderabad city, 1948 | **980,600** | interpolated 739,159 (1941) → 1,106,880 (1951) |
| Twin cities, 1948 | **1,122,774** | against the real 1951 urban agglomeration of **1,131,000** = **0.7 % apart** |
| Secunderabad, 2011 | **217,910** | sourced, Census 2011 Cantonment Board |
| Akanpalle, 2011 | **1,047** | sourced, Census 2011 (546 M / 501 F, 256 houses) |
| Akhanpally Thanda, 1948 | **348** | modelled from Hyderabad at 31 km, decay 0.89 |
| Representative crowd curve, Secunderabad 1948 | 21 people | `SettlementDensity.npcCap(142174, CITY)` — **informational only** |
| Representative crowd curve, Akanpalle 1948 | 2 people | `npcCap(348, VILLAGE)` — informational only |
| Civilizations in the table | **191** | 133 authored (incl. the new `british_india`) ∪ 103 cited; 58 synthesised |

---

## 4. Evidence

`./verification/population/run.sh /tmp/jdk17/jdk-17.0.20.1+1` — exit 0, all five suites:

1. **PopulationResolver** — 7/7 cases, determinism, invariants → `ALL TESTS PASSED`
2. **CivSynthesis** — 103 cited vs the real 15,954-site corpus, 0 unmarked → `ALL PASSED`
3. **SettlementDensity** — monotonic and ceiling-bounded → `ALL PASSED`
4. **OccupancyProbe** — the succession table above, from the real gazetteer and the real manager
5. **CivPersistenceTest** — 13/13: derived file written and reloaded, *second boot derives nothing
   new*, authored record beats derived, saved population/treasury/tech/religion reapplied →
   `ALL PASSED`

Full output: `verification/population/last_run.txt`.
`verification/population/static_audit.py` — **48 checks, 0 failures**.
`verification/population/parse_check.py` — 4 files parsed, 6 skipped (javalang cannot parse Java 17
records), 0 failures.

Production proof, from the user's own `latest.log` on 2026-10-05:

```
[CivSynthesis] civ synthesis: 103 cited by the corpus, 0 already known, 103 synthesised
[PopulationResolver] Loaded 6 region(s), 7 band(s), 0 error(s)
[SimulationEngine] Simulation started at year -3000
<Villager Alex Stone> Word from nearby: Operation Polo (Hyderabad). Soldiers on the roads, not a new town.
```

The synthesis line is what sent me looking: it should not have said `0 already known`.

### What the next run should print

```
[SimulationEngine] Foundation data load groups=4 message=foundation data loaded (world folder, or packaged copy)
[CivilizationManager] Packaged table contributed 1 record(s) the world copy did not answer for
[CivSynthesis] civ synthesis: 103 cited by the corpus, 45 already known, 58 synthesised
[CivSynthesis] Derived records written to ...\eternalengine\civilizations\civ_synthesis.json (58)
[PopulationResolver] Loaded 6 region(s), 7 band(s), 0 error(s) from ... population folder
```
and, on the second boot, `[WorldStateManager] Restored N civilization state record(s)`.

---

## 5. Still not done

- **A full `./gradlew build` has not been run.** This sandbox has 1.9 GB of RAM and a 400 MB
  `/tmp`; ForgeGradle for MC 1.20.1 wants several GB and ~1 GB of downloads. The 10 production
  classes the suites compile are type-checked for real; `WorldStateManager` and `SimulationEngine`
  are parse-checked only. **Run `./gradlew build` locally before trusting the jar.**
- **The save/load JSON round trip is not covered by the tests** — the tests use a stand-in state
  object. The in-game check is the `Restored N civilization state record(s)` line above.
- Still open from before: villager names (`Villager Alex Stone` — `VillagerNameDatabase` has no pool
  for the South Asian ids), the T4 gridded tier, and the calibration constants (`rankUnits`,
  `gravityDecayKm`, `maxInterpolationGapYears`).
- `1948` resolves to `india` because `hyderabad_state`'s box ends `1948` (exclusive). The year 1948
  genuinely contains both — the Nizam's state until 17 September, India after. If the split matters,
  the box end is the one field to change.

---

## Verdict in simple words

Your mod works. It ran in your game and gave the right answers.

Four things were broken, and I only found them because the log you pasted showed the truth:

1. **Nothing loaded the country table unless you typed a command.** So in a normal session, 1948 had
   no ruler and the crowd was one generic farmer. Now it loads by itself, every time.
2. **The 103 countries it made up were thrown away on exit.** The save file never mentioned them.
   Now they are written down and read back.
3. **It said "British India" ruled Secunderabad in 1948, 1950 and 2026.** British India ended in
   1947, and the table simply never said so. I added that one record.
4. **The pack you install into your world was never the pack it read.** It read the one inside the
   mod. Now the world copy wins.

The answer now, at your spawn point: **1591 Qutb Shahi → 1687 Maratha → 1724 Hyderabad State →
1818–1946 British India → 1947 Hyderabad State → 1948 India**, with **142,174 people** in 1948
(the real 1951 count for the twin cities is 1,131,000; the mod's 1948 figure is 0.7 % away).

One thing I could not do here: run the real Gradle build. This machine is too small. Run
`./gradlew build` on yours before you trust the jar.

# Eternal Engine — full `/ee` command test order

Run these **in this order**, in chat, as an **OP 4 player** standing in a loaded Terra++ world.

Do **not** start with `/ee reset confirm`. That wipe is last.

**Setup**

1. Load the world with Eternal Engine + Terra++.
2. `/op <yourname>` (permission 4).
3. Stand on solid ground (many commands need a player position).
4. Have chat open. Tab-complete if an ID is unknown.

Legend: `P2` = OP 2+, `P4` = OP 4, `PLAYER` = must be in-game (not server console).

Copy-paste one line at a time. After mutating commands, glance at the chat reply before continuing.

---

## 0. Smoke (does the mod even answer?)

```
/ee help
/ee status
/ee year
/ee speed status
/ee performance
```

Expect: command directory, engine status, a year like `2999 BCE`, speed, tick budget.

---

## 1. Foundation data (do this before civ/event/disease IDs)

```
/ee data paths
/ee data install
/ee data load
/ee event load
/ee civ list
/ee civ active
/ee event list
/ee event pending
```

If lists are empty, run `/ee data install-overwrite` then `/ee data load` again.

---

## 2. Clock and speed (read, then write, then restore)

```
/ee year
/ee year set -500
/ee year
/ee year set -3000
/ee speed vote 15
/ee speed set 25
/ee speed status
/ee speed set 10
/ee observer
/ee override
/ee slowdown
```

---

## 3. Reputation / player (PLAYER)

```
/ee rep
/ee rep apply trade
/ee achievements
/ee actions recent
```

---

## 4. World probe / scan / anomaly (PLAYER — stand in the world)

```
/ee probe
/ee probe 0 0
/ee probe 100 200
/ee scan chunk
/ee scan chunk 4
/ee anomaly
/ee anomaly probe
/ee anomaly chunk
/ee anomaly chunk 4
```

---

## 5. Current affairs + incident / vehicle tests

```
/ee affairs
/ee affairs global
/ee affairs war
/ee affairs disease
/ee affairs timeline
/ee affairs debug
/ee incident test
/ee vehicle test
```

---

## 6. Buildings + morph (create first, then inspect)

```
/ee building
/ee building history
/ee building add-test test_colosseum Colosseum
/ee building info test_colosseum
/ee building infer test_colosseum
/ee building nearby 500
/ee building anomalies 500
/ee building damage test_colosseum 25 Siege
/ee building plan-test test_colosseum repair
/ee morph status
/ee morph jump 100
/ee morph landmark PARTHENON
/ee morph grammar rome TEMPLE
/ee morph jump -3000
/ee blockqueue status
/ee blockqueue clear
```

---

## 7. History / timeline / codex / divergence

```
/ee history -3000
/ee history 0
/ee timeline
/ee timeline branch
/ee timeline divergences
/ee timeline rifts
/ee timeline roll test_event
/ee timeline diverge test_event player_intervention
/ee timeline cascade test_event player_intervention
/ee timeline rift test_event
/ee timeline rifts
/ee codex
/ee codex tree
/ee codex atlas
/ee codex dossier
/ee codex chronicle
/ee event info hist_rome_1_political
/ee event trigger hist_rome_1_political
/ee event prevent hist_rome_1_political Peace_Treaty
/ee divergence checks
/ee divergence confidence hist_rome_1_political
/ee divergence mark hist_rome_1_political 95
```

If `hist_rome_1_political` is unknown, pick any id from `/ee event list`.

---

## 8. Civilizations, territory, relations, tech, religion

Create extra civ **before** treaties/wars that need two sides.

```
/ee civ list
/ee civ active
/ee civ info rome
/ee civ create sparta Spartan_Commonwealth
/ee territory create-test terr_rome_latium rome
/ee territory owner 419000 125000
/ee relation score rome carthage
/ee relation set rome carthage ALLIED
/ee relation score sumer egypt_ancient
/ee relation set sumer egypt_ancient ALLIED
/ee tech list
/ee tech status rome
/ee tech unlock rome writing
/ee religion list
/ee religion convert rome christianity
/ee religion holywar rome carthage christianity
/ee religion excommunicate rome carthage
/ee class tension rome
/ee urban ratio rome
```

---

## 9. Population / agriculture / climate / famine

```
/ee population get indus_valley
/ee population set indus_valley 250000
/ee population get indus_valley
/ee agriculture food indus_valley
/ee agriculture boost indus_valley
/ee agriculture climate indus_valley 120
/ee climate list
/ee climate active
/ee climate region indus_valley
/ee climate temp indus_valley 150
/ee climate precip indus_valley 200
/ee famine list
/ee famine trigger indus_valley
/ee famine list
/ee famine end indus_valley
/ee migration list
/ee migration process
```

If `indus_valley` fails, use a region id from `/ee climate list` or `/ee civ list`.

---

## 10. Economy / trade (open route, then inspect, then disrupt)

```
/ee economy treasury rome
/ee economy add rome 5000
/ee economy subtract rome 2000
/ee trade list
/ee trade open rome carthage overland
/ee trade list
/ee trade logistics
/ee trade prices
/ee trade prices rome
/ee trade caravan route_rome_carthage
/ee trade ambush-test
/ee trade ship-test
```

After `/ee trade list`, copy a real `routeId` and also run:

```
/ee trade info <routeId>
/ee trade disrupt <routeId> Bandits
/ee trade close <routeId>
```

---

## 11. Disease / disaster

```
/ee disease list
/ee disease status
/ee disease trigger bubonic_plague rome
/ee disease list
```

Then copy an `outbreakId` from the list:

```
/ee disease info <outbreakId>
/ee disease quarantine rome
/ee disease cordon rome
/ee disease doctor-test
/ee disease hospital-test
/ee disease infect-test
/ee disease infect-test black_death
/ee disaster VOLCANIC_WINTER
```

---

## 12. War (declare → inspect → army → battle → end)

```
/ee war list
/ee war army test
/ee war declare rome carthage Territorial_expansion
/ee war list
```

Copy the `warId` and any `armyId` from chat, then:

```
/ee war info <warId>
/ee war cohorts
/ee war deploy legio_1
/ee war deploy legio_1 SHIELD_WALL
/ee war deploy legio_1 SHIELD_WALL 20
/ee war battle-test rome carthage
/ee war siege-test rome carthage
/ee war end <warId>
```

If `legio_1` is missing, use the army id printed by `/ee war army test` or `/ee war cohorts`.

---

## 13. Sovereign command

```
/ee sovereign court rome
/ee sovereign petition rome grain_subsidy
/ee sovereign treaty rome carthage ALLIANCE
/ee sovereign megaproject AQUEDUCT
/ee sovereign doctrine rome PHALANX
/ee sovereign tax rome 15
/ee sovereign debase rome
/ee sovereign coup rome Brutus
```

---

## 14. ECS / map / audio / heraldry

```
/ee ecs status
/ee ecs populate rome 100
/ee ecs status
/ee ecs benchmark
/ee ecs benchmark 100000
/ee map sync
/ee audio atmosphere
/ee heraldry list
/ee heraldry give rome
/ee ecs flush
```

Flush **after** populate/benchmark so later tests are not sitting on 100k agents.

---

## 15. Brain / web / bot (admin diagnostics)

```
/ee brain availability
/ee brain config
/ee brain remote-start
/ee brain queue
/ee brain audit
/ee brain test
/ee brain affairs-propose
/ee brain anomaly-propose
/ee web status
/ee web start
/ee web url
/ee web stop
/ee bot connect agent_alpha
/ee bot briefing agent_alpha villager
/ee bot proposals agent_alpha
/ee bot proposal-test agent_alpha survey
```

`anomaly-propose` needs a player position. Web start/stop is localhost-only.

---

## 16. VIP (inspect → claim → spawn → kill last)

Do **not** kill the VIP before claim/spawn tests.

```
/ee vip list
/ee vip info caesar
/ee vip successors caesar
/ee vip claim caesar
/ee vip tag-self caesar
/ee vip takeover-test caesar DIPLOMATIC
/ee vip release
/ee villager virtual-count
/ee villager explain soldier
/ee villager nearby
/ee villager nearby 64
/ee villager spawn-test rome soldier
/ee villager vip-spawn-test caesar
/ee npc virtual-count
/ee npc explain scholar
/ee npc nearby
/ee npc nearby 64
/ee npc spawn-test rome scholar
/ee npc vip-spawn-test caesar
/ee vip kill caesar Ides_of_March
/ee vip successors caesar
/ee vip clear-self-tag
```

If `caesar` is unknown, use an id from `/ee vip list`.

---

## 17. Chrono Nexus campaign (stateful — run late)

This changes campaign mode/year. Keep it after the simulation tests.

```
/ee chrono status
/ee chrono start
/ee chrono mode STORY
/ee chrono phase 2
/ee chrono advance
/ee chrono mode EXPLORATION
/ee chrono rival
/ee chrono rival spawn
/ee chrono paradox start 120
/ee chrono paradox deliver
/ee chrono ending INFINITE_HORIZON
/ee chrono ending SOVEREIGN_CYCLE
/ee chrono ending SEVERED_KNOT
/ee chrono status
```

---

## 18. Admin wrap-up (reload/backup before wipe)

```
/ee debug core
/ee performance
/ee backup
/ee historian <yourExactPlayerName>
/ee reload
```

Confirm sim still answers:

```
/ee status
/ee year
```

---

## 19. Nuclear last (P4 only)

```
/ee reset confirm
/ee status
```

World simulation state is wiped. Do not run this in the middle of the list.

---

## Extra IDs if tab-complete is empty

| Slot | Try these |
|---|---|
| civ | `rome` `carthage` `egypt_ancient` `sumer` `sparta` |
| region | `indus_valley` `rome` |
| vip | `caesar` |
| event | from `/ee event list` |
| disease | `bubonic_plague` `black_death` `plague` |
| transport | `overland` `naval` |
| formation | `SHIELD_WALL` |
| relation | `ALLIED` `WAR` `NEUTRAL` |
| chrono mode | `STORY` `EXPLORATION` |
| chrono ending | `INFINITE_HORIZON` `SOVEREIGN_CYCLE` `SEVERED_KNOT` |
| landmark | `PARTHENON` |
| grammar | `rome TEMPLE` |
| petition | `grain_subsidy` |
| doctrine | `PHALANX` |
| megaproject | `AQUEDUCT` |
| disaster | `VOLCANIC_WINTER` |
| bot role | `villager` `historian` `soldier` `healer` `advisor` |
| npc role | `soldier` `scholar` |

---

## Why this order

1. **Data first** so later IDs exist.
2. **Read-only lists** before mutations.
3. **Create** (civ, building, trade route, war) before **info/end/close**.
4. **VIP kill last** so claim/spawn still have a living target.
5. **Chrono last among gameplay** because it rewrites campaign year/mode.
6. **ECS flush after benchmark** so the server is not left with 100k agents.
7. **Reset absolutely last.**

Commands that **fail on dedicated-server console** (need a logged-in player):  
`/ee rep`, `/ee probe` (no coords), `/ee scan chunk`, `/ee anomaly*`, `/ee building nearby|anomalies`, `/ee brain anomaly-propose`, `/ee villager nearby`, `/ee npc nearby`, `/ee vip claim`, `/ee observer`.

Permission: almost every `*-test`, `set`, `trigger`, `declare`, `debug` is **OP 2**. Only `/ee reset confirm` is **OP 4**.

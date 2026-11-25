# Linking the world to the NPCs

**The question.** Eternal Engine already simulates affairs, agriculture, civilization, climate, war,
trade, disease, technology, religion, reputation, dynasties, epidemics, buildings, timelines — and
none of it reaches the people walking in front of you. The plan below links the two, without
rewriting either side: the world keeps its equations, the NPC keeps vanilla's brain, and a thin
**link layer** carries one into the other.

Every claim in this document about the code is machine-checked by
`verification/population/alternatives/npc_link_audit.py` (writes `npc_link_audit.tsv`). The counts
below are that script's output, not estimates.

---

## 1. Where the two halves stand today

The script asks one question of each subsystem: **does any file under `villager/`, `entity/` or
`ecs/` mention it?** (Whose land this is etc. is a claim about the source, so it is a grep, not a
sentence.)

**5 of 24 subsystems have an NPC reader. 19 do not.** And each of the 5 is read by exactly one file:

| subsystem | the NPC file that reads it | what the person does with it |
|---|---|---|
| affairs | `entity/HumanNpcEntity.java` | `speakToPlayer` — a real briefing when you right-click |
| civilization | `villager/PresenceLocalSpawner.java` | whose land this is: which civ a person is born into |
| population | `villager/PresenceLocalSpawner.java` | the resolver's number: how many people belong here |
| authority | `villager/VillagerManager.java` | who may command (VIPs) |
| clock | `villager/VillagerManager.java` | the year a person is stamped with, and the day |

**Not linked at all:** agriculture · climate · trade · war · disease · religion · technology ·
reputation · sovereign (court, treaties, succession, imperial orders) · timeline · history · incident ·
anomaly · buildings · vehicle · carto · chrononexus · **ecs** · **brain**.

Two of those deserve naming plainly, because they are the two halves of the promise:

- **the ECS is not linked** — no file in `villager/` or `entity/` mentions `AgentVirtualizationEngine`
  or `StructOfArraysPopulationEcs`. The thousands-of-agents layer and the villagers you meet are two
  separate populations that never reconcile. One person has two representations and they do not talk.
- **the brain is not linked** — `MindcraftBridge` and `ServerDecisionQueue` are read from
  `commands/` only. The door exists; nothing in the world knocks on it.

So the ordinary farmer, in the year the harvest fails, in a place with an army on the road and a
quarantine gate at the market, is **told none of it**: the famine exists in `FoodData`, the army in
`RegimentCohort`, the gate in `QuarantineCheckpointController`, and the person knows only the year
and the name of the civ.

---

## 2. The three sockets that already exist

The plan attaches to machinery that is already built and already runs. This is the whole reason the
link can be thin.

| socket | where it is | what it already does |
|---|---|---|
| **the NPC tick, with the place resolved** | `SimulationEngine.processTick()` → `presenceLocalSpawner.tick(overworld, year, villagers, civs)`, period **40 ticks** (`PERIOD_TICKS`) | resolves the player's geo point, the historical place, the district centre, and the resolver's population number — **once per place, per year, before anything else happens** |
| **the decision drain, which does nothing yet** | `SimulationEngine.processValidatedBrainDecisions()` — `serverDecisionQueue.drain(8)` | pulls up to 8 validated proposals a tick, logs "awaiting subsystem handler", **and drops them** |
| **the physical counterparts** | `RegimentCohort` · `CaravanEntityGroup` · `MerchantShipConvoy` · `PlagueDoctorHospitalManager` · `QuarantineCheckpointController` · `RoyalCourtManager` · `VipSystem` | these systems already put **real entities and blocks** in the world for war, trade, plague and court — an intent has somewhere physical to arrive |
| plus the identity layer | `PeopleMemory` · `VillagerVirtualizer` · `StructOfArraysPopulationEcs` | a person can be remembered exactly, and thousands can be held without entities |

The link is therefore **not** a new simulation. It is: read the world's own numbers into a small
digest, turn the digest into one intent per person, and land that intent on a verb that exists.

---

## 3. What each world system offers a person

The real calls — the left column is what the engine already exposes today, the right column is what a
person can want because of it. (Phase numbers are the build order in §5.)

### 3.1 The four the question named, in full

| # | world system | the person senses (real call) | the person's intent it changes | lands on | phase |
|---|---|---|---|---|---|
| 1 | **agriculture** | `AgricultureEngine.getFoodSecurity(region)` · `getYield(region, year)` · `famineSystem().activeFamineRegions()` · `cropSystem().getCropType(region, year, techEra)` · `FoodData.foodSecurity()` | sow, reap, go to market, hoard, **leave for the city**, or queue at the granary; in a famine year the farmer's work window shortens and his speech turns to grain | the day's walk target (`routinePoint`), the speech line, and — at famine scale — `MigrationSystem`'s number | 1 |
| 2 | **climate** | `ClimateEngine.getTemperatureModifier(region, year)` · `getPrecipModifier` · `isLittleIceAgeActive(year)` · `getActiveClimateEvents(year)` · `ClimateEvent.yieldImpactPercent()` | work indoors in the cold, shelter when the rain is heavy, expect thin harvests in a little-ice-age decade; the **day's length itself** bends with the season | `routineVector` length and `dayOut` window (`CrowdDensity`), the walk in `routineStep` | 1 |
| 3 | **civilization** | `HistoricalPresence.query(...)` (already read) · `TerritoryManager.getOwner(lat, lon, year)` · `isContested(territory)` · `FactionRelationshipSystem.getRelationship / isAtWar / isAllied / hasTradeAgreement` · `EventNews.rumor(event, year)` · `HistoryGazetteer` | whose subject they are, who their enemies and allies are, what they have heard about their own ground, and whether this border is in dispute | the civ a person is stamped with, their speech, and who they may grudge | 2 |
| 4 | **affairs** | `CurrentAffairsEngine.createBriefing(state, level, pos, role, scope, incidents)` → `CurrentAffairsFact(category, text, priority)`; `BotBriefingProvider.getBriefing(...)` · `explain(...)`; `CurrentAffairsMemory.recent(region, n)` | what they believe is going on, **in their own role's words** — the same engine already writes a briefing per role, it is simply never asked by the world | `speakToPlayer`, and the rumours two NPCs pass on | 2 |

### 3.2 The rest of the world

| # | world system | the person senses (real call) | the intent | phase |
|---|---|---|---|---|
| 5 | **disease** | `DiseaseEngine.getActiveDiseases()` · `EpidemicTracker.detectEpidemicThreshold(outbreak)` · `checkQuarantineZone(region)` · `MedicineSystem.getMedicineTier(civ, year, tech)` · `PlagueDoctorHospitalManager` tents · `QuarantineCheckpointController` gates and `inspectorUuids` | doctors make for the hospital tent, inspectors man the gate, the healthy avoid the market, the sick go home | 2 |
| 6 | **trade** | `LogisticsManager.getActiveCaravans()` / `getActiveShips()` / `activeCaravanCount()` · `CommodityPricingEngine.calculateUnitPrice(commodity, origin, dest, …)` · `getRegionalStock` / `getRegionalDemand` · `EconomyEngine.isBankrupt(civ, state)` · `getTreasury` | merchants meet the caravan at the gate, porters carry, the town sells dear in a shortage, a bankrupt realm means unpaid work | 3 |
| 7 | **war** | `WarEngine.getWarsInvolving(civ)` · `isAtWar(a, b)` · `getActiveWars()` · `ArmyManager` · active sieges (`WarEngine.tickLevel`) | soldiers turn out, civilians leave the road, a contested border makes people wary of strangers | 3 |
| 8 | **population** | `PopulationResolver.Result` (already read) · `MigrationSystem` · `ClassSystem.updateDistribution` · `UrbanizationSystem` | how many are here, how crowded the place is, whether people are arriving or leaving | 3 |
| 9 | **reputation** | `ReputationEngine.overallPositive(data)` / `overallNegative` / `fearLevel` / `loyaltyLevel` | **the player's standing decides how they treat you**: bow, keep their distance, refuse, or tip you off | 4 |
| 10 | **sovereign** | `RoyalCourtManager.getCourt(civId)` → `ThroneRoom.rulerUuid/bodyguardUuids/chancellorUuid` · `DiplomaticTreatyEngine` · `DynasticSuccessionEngine` · `ImperialCommandEngine` | courtiers attend the ruler, a succession puts guards on the street, a treaty means celebrating and an imperial order means obeying | 4 |
| 11 | **technology** | `TechnologyEngine.getTechEra(civ)` · `getTechLevel(civ)` · `hasUnlocked(civ, tech)` | what they carry, wear and say is of its era; a new unlock changes the town's work | 4 |
| 12 | **religion** | `CivilizationState.religionId` · `ReligionEngine` | go to worship on the day, argue about the faith of a neighbouring realm | 4 |
| 13 | **incident / anomaly / history / timeline / buildings / vehicle / carto** | `IncidentRecord.type/kind/severity/regionId/year` · `AnomalyDetectionEngine` result · `HistoricalEventDatabase` · `EventChainEngine` · `TimelineManager` active branch · `BuildingHistoryDatabase` + `BuildingDateInferenceEngine` at their own feet · `VehicleEngine` · `CartographicOverlayManager` | a person can witness a local incident and repeat it; an anomaly is something to stare at and report; a building knows its own date; a timeline branch rewrites what they remember | 4 |
| 14 | **ecs** | `StructOfArraysPopulationEcs` flags (`MATERIALISED / GENDER_FEMALE / SICK / ACTIVE`), 32 B a person | the same intent table drives the thousands who have no entity | 2 |
| 15 | **brain** | `LlmProposal` + `LlmProposalValidator` + `ForbiddenLlmAction` + `ServerDecisionQueue` + `BrainAuditLog` | an outside brain proposes an intent for a named person; the table falls back to its own answer when no brain is configured | 4 |

---

## 4. The link layer itself — three small pieces

New code, all in one new package `com.eternalengine.link`. Nothing here is a simulation; it is a
**reader, a table, and a router**.

### 4.1 `WorldDigest` — the world, as one person could know it

One object per **(place, year)**, built where the place and the year are already resolved
(`PresenceLocalSpawner.tickAround`), then cached. Fields, all from the calls above:

```
year · regionId · civId · ownerContested · place · rank · population
climate:  tempModifier · precipModifier · littleIceAge · activeEvent · yieldImpact
farm:     foodSecurity · yield · cropType · famine
trade:    caravansHere · caravanInbound · priceIndex · treasury · bankrupt
war:      warsInvolving · atWarWith · siegeNear
disease:  outbreaks · medicineTier · quarantineHere · hospitalHere
people:   classes · urbanShare   (ClassSystem / UrbanizationSystem)
faith:    religionId · techEra · techLevel
court:    ruler · successionOpen · treatySigned
memory:   recentIncidents · anomalyHere · timelineBranch
```

Rules that make it cheap and honest:

- **Built once per year per place**, not per person and not per tick. Between builds it is immutable.
- **Refreshed early** when an event fires (a famine starts, a war is declared, a caravan arrives) —
  the same year, the same place, a new digest; the old one keeps the caches warm.
- **Pure**: the digest only reads. `WorldDigestTest` asserts that the same world state gives the same
  digest and that no engine is ticked by building one.
- The resolver's number is copied in **as a number**, never recomputed (the standing rule).

### 4.2 `NpcIntent` + `IntentTable` — one intent, from the person and the world

A **closed set** of verbs an NPC can mean, deliberately small and all landable:

```
GO_HOME · GO_WORK · GO_MARKET · GO_WORSHIP · GO_SHELTER · LEAVE_TOWN
FLEE · GUARD · TURN_OUT · CARRY · TRADE · TEND_SICK · QUEUE_AT_GATE · BAR_THE_GATE
TELL · WATCH · MOURN · CELEBRATE · WAIT
```

`IntentTable.of(person, digest, hour, player)` returns **exactly one** of them, deterministically —
a plain rule table, no learning, no randomness beyond the person's own seed (so it is testable and
the same world state gives the same life). The order of the rules is the priority:

| condition (first match wins) | intent | why this order |
|---|---|---|
| quarantine gate here, not an inspector | `QUEUE_AT_GATE` / `BAR_THE_GATE` | plague beats work |
| outbreak active in region | `TEND_SICK` if doctor, else `GO_HOME` | the sick go in doors |
| war on this ground, soldier | `TURN_OUT` | the regiment is already physical |
| war near, civilian | `FLEE` / `LEAVE_TOWN` | civilians do not fight |
| caravan at the gate, merchant | `TRADE` / `CARRY` | the road is already on the map |
| famine year, farmer | `GO_MARKET` (sell dear, hoard) | hunger before faith |
| famine year, other | `LEAVE_TOWN` if the place is big enough to walk to | the world's number moves, not the entity |
| worship day | `GO_WORSHIP` | |
| night | `GO_HOME` | **already built (task 27)** |
| nothing else | `GO_WORK` at the day's walk target | **already built** |

The bottom two rows are the point: the day the project already has becomes the *fallback*, and the
world's state is what displaces it. Nothing about the day's cost changes when no world fact is
active — a digest with nothing to say produces exactly today's behaviour.

### 4.3 `NpcDecisionRouter` — where an intent lands

The router is the only place that touches a person, and it uses **verbs that exist**:

| intent family | verb (already in the mod) |
|---|---|
| any movement intent | the person's day target: `CrowdDensity.routinePoint` + `CrowdFigures.stepTowards` / `PresenceLocalSpawner.routineStep` — a new *target*, not a new brain |
| any speech intent | `HumanNpcEntity.speakToPlayer`'s line, or the spawner's `TELL` between two people |
| any identity intent (fleeing, dying, leaving) | `PeopleMemory.remember` → `VillagerVirtualizer` replica — never a bare discard |
| ECS members | the same intent written into the SoA flags (`StructOfArraysPopulationEcs`) |
| an outside brain's accepted proposal | `SimulationEngine.processValidatedBrainDecisions()` gets its missing handler: person by UUID → intent → this table → the same verbs |

---

## 5. The build, in four steps — each with a gate that proves it

The order is **by what is already physical in the world**: the systems that already put blocks and
entities down come first, because the link makes those objects mean something instead of adding new
ones. Each step is small enough to land in one pass, and each ends in a check that can fail.

**Phase 0 — the drain gets a handler** *(smallest real step; no AI)*
`processValidatedBrainDecisions()` currently logs and drops. Give it `NpcDecisionRouter`: a decision
names a person (UUID) and an intent; the router finds the person — living first, `PeopleMemory`
second — and applies the intent. **Gate:** a command-issued decision moves a named person and is
audited in `BrainAuditLog`; the queue drains to empty; the rig shows the router never invents a
person.

**Phase 1 — sense: `WorldDigest` + climate + agriculture** *(the two the question named first)*
Build the digest in `tickAround` (place and year already there); wire **agriculture** and **climate**
into it; make the day's window and the walk target read it. **Gate:** `npc_link_audit.py` turns
**agriculture** and **climate** to LINKED; `run.sh` gains digest tests (same world → same digest;
nothing ticked); a probe prints the digest for the 4 places across 1901→2026 and a famine year's
effect on the day's window in blocks.

**Phase 2 — decide: the intent table, with disease** *(the intent table is the heart; disease is its
sharpest case because the hospital and the gate are already physical)*
`NpcIntent` + `IntentTable`, wired to the digest; **disease**, **civilization**, **affairs** and the
**ECS** read it too. **Gate:** the table is *total* (every role × condition has an outcome — asserted
by enumerating the table in `run.sh`); six scenario assertions (famine · outbreak · caravan · war ·
night · quarantine) each fixing the intent **and** the verb it lands on; the ECS flags change for a
materialised person and back.

**Phase 3 — act in the loop: trade, war, population**
Round-robin the intent step at the 40-tick period (bounded: K people a cycle), refresh the digest on
the year boundary, and let caravans and armies displace work. **Gate:** measured cost — digest build
µs and intents-per-cycle µs printed by a probe, plus a rig assertion that a quiet digest costs
nothing; `npc_link_audit.py` LINKED for trade, war, population; `static_audit.py` clean.

**Phase 4 — the player's half: reputation, sovereign, religion, technology, history, the brain**
`reputation` decides how a person treats the player (fear, loyalty, refusal); the court, the treaty,
the succession and the calendar become things people do; an accepted `LlmProposal` becomes a real
intent through the router. **Gate:** with no brain configured, behaviour is identical (the no-op
bridge test); with one configured, the proposal appears in the audit log and the person acts;
`npc_link_audit.py` shows **24 of 24 LINKED**.

The audit script is not just documentation — **it is the acceptance test**: the plan is done when it
says every subsystem has an NPC reader, and it is impossible to claim a link without the file.

---

## 6. Cost, on the record

The arithmetic from the project's own constants (not a measurement — the measurement comes with
Phase 1's probe):

- `TICKS_PER_DAY = 24_000`; `ticksPerYear = 24_000 / speed`; default speed **10** → **2,400 ticks a
  year**, a year every ~2 minutes of play.
- Therefore **one Minecraft day is 10 simulation years** at the default speed. This is the strongest
  argument for the digest: *the world changes ten times while a person walks one day*, so a person
  who does not read the year is a person ten years out of date.
- The NPC tick is already staggered: `PERIOD_TICKS = 40` → 600 passes a Minecraft day, 60 passes a
  simulation year at speed 10.
- **Digest**: 1 build per place per year = 1 per 2,400 ticks; a Minecraft day is 24,000 ticks, so
  that is **10 builds per Minecraft day per place**; 4 places ≈ 40. (The earlier draft of this
  section said 24 - it had divided 24,000 by 1,000 instead of by ticksPerYear = 24,000 / speed.
  At speed 10 a day is 10 years, so 10 builds, not 24.) Each build is ~40 map lookups and no tick of any engine.
- **Intents**: at the day boundary for a whole place (already a once-a-day pass), plus a bounded
  round-robin — with K = 8 a cycle, 400 people are re-asked every 2,000 ticks (~100 s), 1/12th of the
  year. Nothing in this plan runs per person per tick.
- What it must beat: the mod already measured **400 ticked figures ≈ 9.5 ms a frame**. The link may
  not be measurable next to that; the probe in Phase 3 asserts it.

---

## 7. What this plan does not do

- **It does not simulate an unloaded world.** A person's day is still a function of the world clock
  (task 27); the digest is built when the place is loaded, from numbers the sim already has.
- **It does not give people needs.** Hunger, thirst and fatigue are a separate question — and worth
  doing *after* this, because a want is only interesting once the world has something to say about it.
- **It does not make the LLM think every tick.** The brain is a proposal source on top of the table,
  read at a day's cadence, and the table answers whenever no brain is configured.
- **It does not touch the crowd's size, the ceiling, the 24-block rule, or the drawing.** The link
  changes what a person *wants*, not how many stand there.
- **It does not link the two virtualisation layers by itself.** Phase 2 writes the intent into the ECS
  flags; making one person identical across `PeopleMemory` and an ECS slot is its own small job, and
  the audit now shows it as UNLINKED so it cannot be forgotten.

---

## 8. Verdict in simple words

The world and the people are two machines that have never been introduced. Your engines already know
the harvest, the cold, the plague, the war, the road and the price; the person you meet knows the year
and the name of his civ — **5 of 24 systems reach him, each through a single file, and 19 reach nobody
at all**, including the thousands-agent ECS and the whole brain door. The fix is not to make the world
smaller or the NPC smarter; it is a **link layer of three small pieces**: a *digest* (the world as one
person could know it, built once per place per year — and a Minecraft day is ten sim years, so that is
not a stale number), an *intent table* (the world's state displaces today's wander with one of twenty
verbs, first match wins), and a *router* that lands the intent on movement, speech, memory or the ECS
— all verbs the project already has. Four steps: the decision drain gets a handler, then agriculture
and climate, then the intent table with disease, then trade and war, and the player's half last. The
acceptance test already exists — the audit script flips a subsystem to LINKED only when a named file
reads it, so the plan cannot be declared finished by anybody's word, only by the code.

**First real step, when you say go:** Phase 0 — the drain's missing handler — then Phase 1's digest
with agriculture and climate, both proven in the rig and drawn on a panel.

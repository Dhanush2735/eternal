# Eternal Engine 100× Transformation Master Plan
**Version:** 2.0.0-PROPRIETARY  
**Target Platform:** Minecraft Forge 1.20.1 (Forge 47.2.0+, Java 17)  
**Host Foundation:** Terra++ 2.0.0-alpha (`terra3`) with 53 OSM Carto Types  
**Target Mod ID:** `eternalengine` (`com.eternalengine`)  

---

## Executive Summary: The 100× Paradigm Shift

Eternal Engine currently possesses a robust, thread-safe architectural foundation comprising 186 Java classes across 35 packages. It successfully implements abstract mathematical simulations of historical warfare, trade networks, demographic shifts, epidemiologic outbreaks, religious spreads, and timeline divergences.

However, in its current state, **the simulation lives predominantly as abstract state in memory and text inside `/ee` command outputs.** A player exploring Paris in 1789, Rome in 50 BCE, or Hyderabad in 1920 is met with empty streets, generic vanilla villagers, static modern terrain, and no physical manifestation of the epic historical events occurring around them.

This Master Plan details the engineering roadmap to transform Eternal Engine into the **world's first living, 1:1 scale historical open-world simulation**:

```
                       CURRENT STATE                              100× TRANSFORMED STATE
┌──────────────────────────────────────────────┐          ┌──────────────────────────────────────────────┐
│ • Armies are numbers in Army.java            │          │ • Physical regimental cohorts marching       │
│ • Trade routes are static coordinate lists   │          │ • Living merchant caravans & cargo galleons  │
│ • Villagers use vanilla Minecraft bodies     │   ───►   │ • Cultural player-model NPCs in period garb  │
│ • 7×7 box buildings with generic wood roofs  │          │ • Era-specific architectural grammars        │
│ • World terrain is static modern OSM         │          │ • Dynamic 53-Carto infrastructure morphing   │
│ • Timeline divergences are text log entries  │          │ • Interactive Chronos Codex & Reality Rifts  │
│ • Simulation caps at ~200 Minecraft entities │          │ • 100,000-agent zero-lag bytecode ECS        │
└──────────────────────────────────────────────┘          └──────────────────────────────────────────────┘
```

---

## Core Invariants & Architectural Tenets

1. **The Physicalization Principle**: If a player is within visual or operational range (128 blocks), simulation state must never remain an abstract number. It must materialize as physical entities, soundscapes, structures, or visual effects.
2. **Terra++ as Absolute Ground Truth**: Real-world elevations, coordinates, biomes, and OpenStreetMap geometry flow strictly from Terra++. Eternal Engine never synthesizes fake geography.
3. **Server-Authoritative Multithreading**: All simulation computations run asynchronously in background thread pools (`AsyncTaskManager`), writing validated state to the main server thread via `ServerDecisionQueue` and `BlockUpdateQueue` within a strict **10ms tick budget**.
4. **Advisory-Only Remote LLM Gateway**: LLM interactions rely exclusively on secure remote HTTPS endpoints (`LiteLLM` primary, `Portkey` secondary). LLM responses are strictly advisory and cannot mutate world data, run console commands, or spawn blocks without subsystem schema validation.
5. **Zero-Lag 3-Tier Virtualization**: Global demographics (millions of historical humans) operate as compact 48-byte structs in memory, stepping through time off-thread, materializing as high-fidelity `HumanNpcEntity` actors only when proximate to players.

---

## Pillar 1: Living Humanoid NPC Architecture & Cultural Skin Matrix

### 1.1 Technical Problem
Vanilla Minecraft `Villager` entities shatter historical immersion. They lack player-style movement, armor/clothing layers, gender/cultural diversity, and dynamic facial/outfit rendering.

### 1.2 Architectural Solution: `HumanNpcEntity`
Create a custom entity extending `PathfinderMob` rendered with `PlayerModel<HumanNpcEntity>` supporting both Steve (default) and Alex (slim) arm geometries.

```
                  ┌──────────────────────────────────────────────┐
                  │          HumanNpcEntity (Server)             │
                  │ - UUID, Name, CultureId, Era, Role, Class    │
                  │ - Loyalty, Health, Morale, DailyRoutineState │
                  │ - Synced EntityData: SkinHash, GarmentLayer  │
                  └──────────────────────┬───────────────────────┘
                                         │ Network Sync
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │         HumanNpcRenderer (Client)            │
                  │ - PlayerModel<HumanNpcEntity>                │
                  │ - Dynamic Cultural Texture Compositor        │
                  │ - Historical Garment & Equipment Layers      │
                  │ - Ambient Billboard Speech Bubble Overlay    │
                  └──────────────────────────────────────────────┘
```

### 1.3 Cultural Skin & Garment Matrix (`SkinMatrixRegistry`)
A deterministic procedural texture compositing engine that generates skins based on `civilizationId`, `historicalEra`, and `socialClass`:

```
Base Biological Texture (Skin tone, eyes, hair by ethnicity)
  + Undergarment Layer (Tunic, linen shirt, dhoti, subligaculum)
  + Period Overgarment (Toga, waistcoat, sherwani, kimono, armor)
  + Headwear / Accessory (Laurel wreath, turban, tri-corn hat, kabuto)
  = Final Composite 64×64 PNG (Cached in LocalSkinStorage)
```

| Civilization | Era | Peasant / Worker | Merchant / Artisan | Nobility / Ruler | Military |
|---|---|---|---|---|---|
| **Roman** | Classical (100 CE) | Rough wool tunic | Embroidered stola | Tyrian purple toga | Segmentata & Galea |
| **Ottoman** | Early Modern (1550) | Cotton salvar & fez | Silk caftan | Jeweled turban & robe | Janissary bork & armor |
| **Mughal** | Imperial (1650) | Khadi kurta | Angrakha & pagri | Jamawar sherwani | Chahar-Aina steel armor |
| **British** | Victorian (1870) | Cap & suspenders | Tailcoat & bowler | Double-breasted frock | Redcoat shako & webbing |
| **Japanese** | Edo (1700) | Straw mino & kosode | Kasuri haori | Kamishimo & daisho | Samurai o-yoroi |

### 1.4 Circadian Routine Engine (`NpcBehaviorBrain`)
Every materialized NPC follows a continuous 24-hour schedule driven by Minecraft game time (`world.getDayTime()`):
- **06:00 – 08:00 (Awakening & Morning Prayer)**: NPCs exit beds in residential houses, visit neighborhood shrines/temples/churches (`RELIGION`).
- **08:00 – 12:00 (Productive Labor)**: Farmers till `FARMLAND`, blacksmiths work anvils, scholars congregate in `CAMPUS` quads, dockworkers patrol `PIER` docks.
- **12:00 – 13:00 (Midday Sustenance)**: Gathering at community wells, bakeries, or street food stalls.
- **13:00 – 17:00 (Trade & Industry)**: Merchants set up stalls on `ROAD`/`FOOTWAY` sidewalks, transactions affect local `EconomyEngine` prices.
- **17:00 – 21:00 (Socializing & Taverns)**: Congregate in common squares (`PARK`, taverns), discuss current affairs from `CurrentAffairsEngine`.
- **21:00 – 06:00 (Curfew & Sleep)**: Return to assigned residential beds, lock doors, night watchmen patrol with torches/lanterns.

### 1.5 Ambient Dialogue & Factual LLM Integration
When players right-click or walk within 5 blocks of an NPC, an ambient floating speech bubble renders above the NPC's head. The dialogue is generated on the fly via `LiteLLM`/`Portkey` using a strict factual prompt injection:
```json
{
  "speaker": "Marcus, Roman Baker",
  "location": "Rome, Regio VIII Forum Romanum",
  "year": "64 CE",
  "recent_events": ["Great Fire extinguished 3 days ago", "Emperor Nero blaming Christians"],
  "economic_context": "Grain shipment from Alexandria delayed by storm",
  "instruction": "Speak in 1-2 authentic, concise sentences matching your class and era. Never mention modern concepts or breaking character."
}
```

---

## Pillar 2: Dynamic Era-Morphing Architecture & Terra++ 53-Carto Synthesis

### 2.1 The Infrastructure Evolution Problem
Terra++ generates modern Earth infrastructure (asphalt roads, power lines, concrete bridges). In 500 BCE or 1700 CE, modern infrastructure looks completely broken.

### 2.2 Synthesized Infrastructure Pipeline
Eternal Engine will command Terra++'s newly implemented **53 Structure Types** and `TerraTimeBridge` to morph the physical landscape dynamically based on the current simulation year:

```
[SimulationClockManager: Year changes from 2026 to 1750]
                           │
                           ▼
[TerraTimeBridge.jumpToYear(1750)]
                           │
                           ▼
[TerraChunkGenerator + OsmStructureGenerator (terra3)]
  ├── ROAD: "motorway" / "primary" ──► Morphed into Cobblestone / Packed Dirt Highway
  ├── POWER_LINE / PYLON          ──► Suppressed entirely (pre-1880)
  ├── AERIALWAY                   ──► Suppressed (pre-1900)
  ├── RAILWAY                     ──► Suppressed (pre-1825); in 1860, renders early wooden ties + iron rail
  ├── WATER_STREAM (Canals)       ──► Retained & enhanced with towpaths
  ├── RUNWAY / APRON              ──► Suppressed (pre-1910)
  └── STREET_LAMP                 ──► Morphed from Sea Lantern to Iron Lantern / Torch Post
```

### 2.3 Procedural Historical Architectural Grammar (`CivilizationGrammarEngine`)
Replace the simplistic 7×7 box planner in `EraBuildingPlanner.java` with a modular grammar engine that parses real OSM building footprints into authentic regional architecture:

```
                    Building Footprint (OSM Polygon from Terra++)
                                        │
                                        ▼
                  ┌───────────────────────────────────────────┐
                  │    Civilization Architectural Parser      │
                  │   (Culture + Year + Building Type)        │
                  └─────────────────────┬─────────────────────┘
                                        │
               ┌────────────────────────┼────────────────────────┐
               ▼                        ▼                        ▼
      [Greco-Roman Classical]     [Gothic / Medieval]     [East Asian Pagoda]
      - Fluted quartz columns     - Flying buttresses     - Bracket sets (dougong)
      - Impluvium courtyard       - Pointed stone arches  - Curved ceramic tile eaves
      - Terracotta pantiles       - Leaded stained glass  - Post-and-beam timber frame
      - Low-pitch pediment        - Steep timber roofs    - Central wooden pillar
```

### 2.4 Atomic Temporal Rebuilding (`TemporalMorphController`)
To prevent visual glitching, floating roofs, or half-demolished ghost structures when changing years:
1. When year jump begins, freeze player chunk ticking for 20 ticks.
2. Scan all blocks tagged in `GeneratedBlockOwnershipIndex`.
3. Filter out blocks belonging to structures that do not exist in the target year.
4. Issue an asynchronous demolition order through `BlockUpdateQueue` (top-to-bottom).
5. Trigger Terra++ historical chunk generation pass to construct period-accurate replacements (bottom-to-top).

---

## Pillar 3: Physicalized Warfare, Regimental Combat & Dynamic Sieges

### 3.1 From Math Rolls to Physical Regiments
Instead of resolving battles via integer subtractions in `BattleSimulator.java`, wars between civilizations mobilize real physical armies:

```
                     ┌─────────────────────────────────────────┐
                     │          WarEngine (Macro State)        │
                     │  - Belligerents, Casus Belli, War Goal  │
                     │  - Frontlines, Strategic Objectives     │
                     └────────────────────┬────────────────────┘
                                          │ Mobilize
                                          ▼
                     ┌─────────────────────────────────────────┐
                     │       Army / RegimentEntityGroup        │
                     │  - Leader VIP, Moral, Food, Supply Line │
                     │  - 32–128 Physical HumanNpcEntity Units │
                     └────────────────────┬────────────────────┘
                                          │ Formations
                   ┌──────────────────────┴──────────────────────┐
                   ▼                                             ▼
          [Infantry Cohort]                             [Cavalry Squadron]
       - Shield wall / Phalanx                       - Flanking pathfinding
       - Pila / Javelin volleys                      - Lance charges & skirmish
       - Coordinated melee push                      - Harassment of supply lines
```

### 3.2 Formation Pathfinding & Collision Physics (`CohortFormationBrain`)
- Units in a regiment maintain deterministic formation offsets relative to the standard bearer (e.g., $4 \times 8$ grid or hollow square).
- Movement vectors follow the standard bearer; individual entities pathfind while repelling neighbors using soft steering behaviors.
- **Era-Specific Tactics**:
  - *Ancient*: Phalanx / Testudo formation (high frontal projectile resistance, slow turning speed).
  - *Medieval*: Feudal vanguard (heavy cavalry shock charge followed by peasant levies).
  - *Early Modern*: Pike and shot (alternating ranks of musketeers firing rolling volleys protected by pikemen).
  - *Industrial*: Line infantry and artillery batteries establishing defensive earthworks.

### 3.3 Dynamic Sieges & Tactical Structural Damage
When an army besieges an enemy settlement:
1. **Siege Engine Materialization**: The army deploys trebuchets, catapults, battering rams, or siege towers (custom multipart entities).
2. **Structural Destruction via `BlockUpdateQueue`**:
   - Artillery projectiles detonate on impact, creating localized block damage on outer city walls (`WALL`) and gatehouses.
   - Damaged blocks are recorded in `BuildingHistoryDatabase` with an intact/damage bitmask.
3. **Breach Assault & Surrender**:
   - Once wall thickness drops below critical threshold, soldiers charge the breach.
   - If civilian casualties or morale drop below threshold, the city governor raises a white flag, transitioning the settlement to occupied status.
4. **Post-War Reconstruction**:
   - Following peace treaty execution in `WarEngine`, a reconstruction fund is allocated; laborers repair damaged walls block-by-block over several in-game weeks.

---

## Pillar 4: Overland Caravans, Maritime Fleets & Physical Logistics

### 4.1 Physical Trade Caravans (`CaravanEntityGroup`)
Trade routes defined in `TradeRouteEngine.java` currently exist only as theoretical lines. They will now spawn physical logistics units:
- **Caravan Composition**:
  - 1 Lead Caravan Master (`HumanNpcEntity`).
  - 3–8 Pack Animals (Camels in arid zones, Donkeys/Mules in temperate, Yaks in alpine, Oxen wagons in lowlands).
  - 2–4 Armed Caravan Guards (`HumanNpcEntity` with crossbows/swords/rifles).
- **Route Navigation**:
  - Caravans navigate along verified Terra++ OSM road networks (`ROAD`, `TRACK`, `BRIDLEWAY`), avoiding impassable topography.
  - Caravans make scheduled overnight stops at roadside caravanserais, taverns, or hamlets.

### 4.2 Maritime Shipping & Port Networks
- Navigable waterways (`waterway=river`, `waterway=canal`, natural oceans) spawn historic sailing vessels:
  - Ancient Mediterranean: Roman Triremes & Phoenician merchant galliots.
  - Medieval: Hanseatic Cogs & Viking longships.
  - Age of Sail: Spanish Caravels, Dutch Fluyts, British East Indiamen.
  - Industrial: Steam paddle-wheelers & ironclad freighters.
- Ships moor at OSM `PIER` structures, where cargo dockworkers physically load and unload crates of grain, spices, timber, and ore.

### 4.3 Highwaymen, Piracy & Economic Shocks
- Caravans passing through territories with low government authority (`AuthoritySystem.java`) or active wars face ambush by bandit squads.
- Players can:
  - **Escort**: Sign mercenary contracts with merchants for gold and reputation rewards.
  - **Raid**: Intercept enemy supply caravans to loot trade goods and induce local famines in enemy cities.
  - **Invest**: Fund new trade expeditions, establishing new trade posts and opening exotic commodity imports.

---

## Pillar 5: The Playable Butterfly Effect (Divergent Timelines & The Chronos Codex)

### 5.1 The Divergence Mechanics
Eternal Engine's `AlternativeTimelineEngine` provides the theoretical underpinning. We elevate this into a core playable mechanic:

```
                                [PRIME TIMELINE: 1914]
                        Assassination of Archduke Franz Ferdinand
                                           │
                       ┌───────────────────┴───────────────────┐
                       │                                       │
                [Historical Path]                    [Player Intervenes]
             - World War I erupts                 - Assassin intercepted in Sarajevo
             - Fall of 4 Empires                  - Archduke survives & ascends throne
             - Rise of modern nation-states       - Austro-Hungarian trialist reform
                       │                                       │
                 (Timeline Alpha)                        (Timeline Beta)
```

### 5.2 Deterministic Butterfly Propagation (`DivergenceCascadeEngine`)
When an anchor event is prevented:
1. `DivergenceDetector` registers the deviation and assigns a unique `BranchId` (e.g., `branch-1914-sarajevo-peace`).
2. An asynchronous cascade recalculates future dependent events in `HistoricalEventDatabase`:
   - Future events with satisfied prerequisites are queued.
   - Obsolete events (e.g., Treaty of Versailles) are cancelled.
   - Alternate events (e.g., "Balkan Trade Federation formed 1918") are loaded from `foundation_events.json`.
3. The global geopolitical map updates dynamically: border changes, diplomatic alliances, and tech trees shift according to the alternate reality.

### 5.3 The In-Game Chronos Codex (Interactive Journal GUI)
An illustrated, book-style GUI rendered client-side (`ChronosCodexScreen`):
- **Timeline Tree Tab**: Visual branching diagram showing the prime timeline vs. active divergence branches, highlighting player-instigated changes.
- **Geopolitical Atlas Tab**: Dynamic full-screen parchment map displaying current nation boundaries, troop dispositions, and plague hotspots.
- **Dossier Tab**: Biographies of discovered historical VIPs, their current alive/dead status, and player reputation standing with each court.
- **Chronicle Tab**: Daily generated newspaper or royal gazette summarizing current global affairs.

### 5.4 Reality Rifts & Temporal Anomalies
If a player causes excessive historical divergence in a localized area:
- A shimmering visual particle rift appears at the epicenter of divergence (e.g., the street corner in Sarajevo).
- Players interacting with the rift can look through it to observe a ethereal spectral hologram of what occurred in the Prime Timeline.

---

## Pillar 6: Dynastic Sovereign Court & Grand Strategy Command Interface

### 6.1 The Sovereign Throne Room
Historical rulers (`VipSlot`) no longer wander aimlessly. They reside in capital palace complexes:
- Surrounded by elite royal guards and court ministers.
- Foreign ambassadors physically approach the throne to deliver diplomatic ultimatums or alliance proposals.
- Players with sufficient reputation can request an audience to petition for titles, lands, or military support.

### 6.2 Sovereign Mode (When Player Usurps or Claims a VIP Slot)
Upon claiming a throne through `VipTakeoverSystem` (inheritance, election, or coup), the player unlocks the **Imperial Command Dashboard** (`/ee crown` or Crown Scepter item):
- **Imperial Megaprojects**:
  - Select an area of land and commission grand monuments (e.g., Coliseum, Notre-Dame, City Wall circuit, Aqueduct).
  - The project enqueues into `BlockUpdateQueue`, spawning wooden scaffolding, stonecutters, and supply carts that build the structure over real-time days.
- **Grand Military Mobilization**:
  - Draw marching arrows on the geopolitical atlas to dispatch regiments to frontlines.
  - Set military doctrines: *Scorched Earth*, *Defensive Attrition*, *Decisive Blitzkrieg*.
- **Fiscal & Monetary Policy**:
  - Adjust tax rates (high taxes yield treasury coin but degrade citizen loyalty and spark peasant revolts).
  - Debase currency purity in the mint (instant gold injection, but causes rapid inflation and trade decline).

---

## Pillar 7: Zero-Lag 100,000-Agent Virtualization Engine

### 7.1 The Performance Ceiling
Minecraft servers drop below 20 TPS when handling more than 300 ticking entities. Simulating realistic historical populations requires managing **hundreds of thousands of individuals simultaneously**.

### 7.2 The 3-Tier Entity Component System (ECS)
We implement a hyper-compact, off-heap or array-backed Bytecode ECS architecture:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ TIER 1: GLOBAL MACRO SIMULATION (~100,000+ Agents across Earth)             │
│ • Stored as flat primitive arrays (struct-of-arrays):                       │
│   long[] entityId, int[] latLonPacked, short[] ageCulture, byte[] roleState │
│ • Memory footprint: Exactly 32 bytes per human (3.2 MB for 100,000 agents!) │
│ • Ticked off-thread every 10 seconds: Demographics, health, migration.      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Player approaches within 192 blocks
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TIER 2: RADAR PROXIES (64 to 192 blocks from player)                        │
│ • Lightweight non-ticking positional representations.                       │
│ • No Minecraft entity object allocated; rendered client-side as billboard   │
│   sprites or map dots via lightweight network packet.                       │
│ • Zero physics collision, zero AI pathfinding overhead.                    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Player approaches within 64 blocks
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TIER 3: LOCAL MATERIALIZATION (< 64 blocks from player)                     │
│ • Instantiated as full ServerLevel HumanNpcEntity.                          │
│ • Full pathfinding, cultural skins, equipment, and speech capabilities.     │
│ • When player moves beyond 64 blocks, state (health, equipment, position)   │
│   flushes back into Tier 1 arrays, and entity is cleanly despawned.         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.3 Performance Benchmarks & Budgets
- **Tick Budget**: Total simulation time spent on the main server thread must never exceed **8.0 milliseconds per tick**, leaving 42.0 milliseconds for vanilla Minecraft world ticks.
- **Memory Overhead**: Complete 100K-agent demographic state requires less than **64 MB RAM**.
- **Garbage Collection**: Struct-of-arrays layout produces **zero heap allocations** during normal macro-simulation ticks, eliminating GC pauses.

---

## Pillar 8: Audiovisual Immersion & Cartographic Map Overlays

### 8.1 Cartographic Minimap Integration
Native, zero-crash integration with **JourneyMap** and **Xaero's World Map / Minimap**:
- **Geopolitical Borders**: Dynamic colored polygon borders overlaying empire territories with sovereignty labels.
- **War Frontlines**: Red dashed lines indicating contested territory and active siege zones.
- **Trade Convoys**: Animated golden wagon/ship icons moving along historical routes.
- **Epidemic Hotspots**: Translucent biohazard/red fog zones indicating quarantined cities.

### 8.2 Geographically & Epochally Grounded Soundscapes
A client-side audio director (`HistoricalAudioDirector`) that synthesizes period-appropriate ambient audio based on the player's exact GPS coordinates and historical year:
- **Classical Mediterranean (Rome/Athens, 100 BCE)**: Lyres, coastal breeze, marble chisel strikes, distant market chatter in Latin/Greek.
- **Medieval Islamic World (Baghdad/Cairo, 1100 CE)**: Oud and ney melodies, call to prayer echoes, bustling bazaar hubbub.
- **Renaissance Europe (Venice/Florence, 1500 CE)**: Harpsichord, church bells, canal water lapping against stone piers.
- **Industrial Age (London/Pittsburgh, 1880 CE)**: Steam piston chugs, metallic hammer clangs, factory whistles.
- **Active Warfare**: Distant cannon rumbles, war horn blasts, clashing steel, and cavalry hooves.

---

## 10-Phase Implementation Roadmap

```
PHASE 11: Humanoid NPC Engine & Cultural Skin Matrix
    ├── Register HumanNpcEntity & deferred entity registers
    ├── Implement PlayerModel client renderer with 3D garment layers
    ├── Build SkinMatrixRegistry with regional texture compositor
    └── Eliminate all vanilla Villager dependencies in VillagerManager

PHASE 12: Regimental Warfare & Dynamic Field Battles
    ├── CohortFormationBrain for disciplined unit formations
    ├── Artillery & Siege Engine entities with physical ballistics
    ├── Tactical destructibility connected to BlockUpdateQueue
    └── Frontline territorial capture mechanics

PHASE 13: Overland Trade Caravans & Maritime Logistics
    ├── CaravanEntityGroup spawner along Terra++ OSM highways
    ├── Merchant ship navigation along waterways and piers
    ├── Dynamic commodity pricing & supply line interdiction
    └── Bandit ambush encounters in low-authority zones

PHASE 14: Era-Morphing Architecture & 53-Carto Infrastructure Synthesis
    ├── TerraTimeBridge automatic road/bridge/lamp morphing
    ├── CivilizationGrammarEngine replacing 7×7 box buildings
    ├── Historical landmark generation from real OSM footprints
    └── TemporalMorphController for glitch-free year transitions

PHASE 15: Epidemic Quarantines & Medical Realism
    ├── Visible city gate quarantine checkpoints & barricades
    ├── Contagion transmission between proximate HumanNpcEntities
    ├── Plague doctors, hospital tents, and medicine tier effects
    └── Historical disaster incidents (famines, floods, volcanic winters)

PHASE 16: Playable Butterfly Effect & The Chronos Codex
    ├── Interactive ChronosCodexScreen book GUI with timeline trees
    ├── DivergenceCascadeEngine for dynamic alternate history
    ├── Spatial Reality Rift shaders at historical pivot points
    └── Full-screen historical parchment map overlay

PHASE 17: Sovereign Throne Rooms & Grand Strategy Command
    ├── Royal court palaces with resident VIP rulers and bodyguards
    ├── Imperial Command Dashboard for Megaproject construction
    ├── Geopolitical diplomacy: Alliances, treaties, ultimatums
    └── Dynastic succession, heir generation, and usurpation coups

PHASE 18: Zero-Lag 100,000-Agent Virtualization Engine
    ├── Compact 32-byte struct-of-arrays ECS population model
    ├── Tier-2 radar proxy billboards (64–192 blocks)
    ├── Seamless Tier-3 materialization & state flushing (<64 blocks)
    └── Continuous 20 TPS stress verification suite

PHASE 19: Cartography Overlays & Period Soundscapes
    ├── JourneyMap and Xaero's minimap border & route layers
    ├── HistoricalAudioDirector for coordinate-based period music
    ├── Ambient 3D directional crowd noise & environmental FX
    └── Dynamic banner crests and heraldry for civilizations

PHASE 20: Comprehensive Historical Corpus & Verification Suite
    ├── 2,000+ authentic historical events in foundation_events.json
    ├── 500+ historical VIPs with accurate dates and biographies
    ├── 100+ playable historical civilizations with territorial bounds
    └── Automated end-to-end integration test framework
```

---

## Verification & Diagnostic Metrics

Every phase will be validated through dedicated Python and JUnit test suites verifying:
1. **Zero Heap Allocation in Hot Loops**: Ensuring garbage collection does not spike during entity materialization or macro-simulation ticks.
2. **20.0 TPS Stability**: Running the integrated server under load with 10,000 virtual agents, 5 active wars, and 20 trade caravans.
3. **No Below-Surface Block Placement**: Strict adherence to `TerraCoordinateHelper.getSurfaceY()` across all building grammars and siege damage.
4. **LLM Safety Invariant**: Zero unvalidated proposals reaching server subsystems; 100% remote HTTPS failover from LiteLLM to Portkey.

This Master Plan represents the definitive engineering blueprint to elevate Eternal Engine into an unrivaled masterpiece of computational history and real-world simulation.

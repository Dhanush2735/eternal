# Phase 12: Regimental Warfare & Dynamic Field Battles

## 1. Overview & Vision
Phase 12 transforms Eternal Engine's warfare simulation from purely virtual macro-level statistical calculations into materialized 3D field engagements rendered directly in the Minecraft world using the `HumanNpcEntity` player-model engine. 

Armies can now muster physical **Regiment Cohorts** that advance across the Terra++ terrain in structured military formations (Phalanx, Line, Wedge, Square, Skirmish), maintain cohesive ranks around their commander / standard bearer, handle chain-of-command casualty promotions in real time, and clash with opposing cohorts. In addition, siege warfare is introduced with the **SiegeEngineController**, delivering ballistic artillery strikes that safely breach enemy fortifications through the asynchronous `BlockUpdateQueue`.

---

## 2. Core Architectural Components

### A. Tactical Formation Geometry (`FormationType.java`)
Defines the mathematical geometry and tactical profiles for cohort formations:
- **`LINE`**: Wide front (2 ranks deep) for maximum direct missile or melee frontage (`fileSpacing = 1.2m`, `rankSpacing = 1.5m`).
- **`PHALANX`**: Dense defensive/offensive infantry block (up to 4 ranks deep, `fileSpacing = 0.9m`, `rankSpacing = 1.0m`).
- **`SQUARE`**: Hollow defensive perimeter against flanking and cavalry encircling (`fileSpacing = 1.3m`, `rankSpacing = 1.3m`).
- **`WEDGE`**: Piercing arrowhead formation, with ranks expanding symmetrically outwards (`fileSpacing = 1.4m`, `rankSpacing = 1.5m`).
- **`SKIRMISH`**: Dispersed loose-order arrangement minimizing vulnerability to artillery and area damage (`fileSpacing = 2.5m`, `rankSpacing = 2.5m`).

### B. Formation Brain Math (`CohortFormationBrain.java`)
Calculates deterministic 3D world target positions for each soldier index relative to the cohort's commander standard bearer:
1. **Local Grid Layout**: Computes `(localRight, localForward)` relative offsets based on rank, file, and formation geometry.
2. **Yaw Rotation**: Rotates local offsets around the commander's facing angle:
   $$\Delta x = \text{localRight} \cdot \cos(\theta) - \text{localForward} \cdot \sin(\theta)$$
   $$\Delta z = \text{localRight} \cdot \sin(\theta) + \text{localForward} \cdot \cos(\theta)$$
3. **Terrain Height Sampling**: Queries `TerraCoordinateHelper.getPlacementY(level, targetX, targetZ)` to ensure every soldier is positioned cleanly on the surface of Terra++ topography.

### C. Cohort Unit & Chain-of-Command (`RegimentCohort.java`)
Represents an active, living military regiment in a `ServerLevel`:
- **Commander Standard Bearer**: Unit index 0 serves as the anchor and direction indicator for the entire regiment.
- **Battlefield Succession**: If the commander falls in battle, `promoteNextLeader` instantly selects the next surviving soldier in line, re-equips them as commander, and re-anchors the formation without disbanding the cohort.
- **Era-Appropriate Equipment**: Automatically outfits soldiers based on their civilization's technological era (`ANCIENT`, `MEDIEVAL`, `GUNPOWDER`, `INDUSTRIAL`, `MODERN`), equipping swords, bows, shields, crossbows, iron/chainmail armor, and banners.
- **Tactical Clashing**: Acquires hostile target cohorts, advances toward opposing anchor positions, and commands individual soldier entities to attack opposing soldiers when within range.

### D. Fortification Artillery Siege Engine (`SiegeEngineController.java`)
Coordinates artillery bombardment against defended structures:
- Calculates ballistic impact coordinates around target fortifications with spread radii.
- Produces sound and particle feedback (`GENERIC_EXPLODE`, `EXPLOSION`).
- Routes all structural block damage safely through the thread-safe `BlockUpdateQueue` above ground elevation (`surfaceY`).
- Degrades stone blocks progressively:
  - `STONE_BRICKS` $\rightarrow$ `CRACKED_STONE_BRICKS`
  - `CRACKED_STONE_BRICKS` $\rightarrow$ `COBBLESTONE`
  - `COBBLESTONE` $\rightarrow$ `GRAVEL`
  - Final breach $\rightarrow$ `AIR` / enqueued fire.

### E. Army Manager & Lifecycle Integration (`ArmyManager.java` & `WarEngine.java`)
- `ArmyManager.deployCohort(...)`: Instantiates, gears, and deploys full cohorts into the active world.
- `ArmyManager.tickCohorts(ServerLevel)`: Ticks all active cohorts, cleans casualties, adjusts formations, and executes tactical maneuvers.
- `WarEngine.tickLevel(ServerLevel, BlockUpdateQueue)`: Hooked directly into `SimulationEngine.onServerTick`, advancing cohort tactics and siege artillery strikes every server tick.

---

## 3. In-Game Commands (`/ee war ...`)

| Command | Permission | Description |
|---|---|---|
| `/ee war deploy <armyId> [formation] [count]` | OP Level 2 | Spawns a physical cohort at the player's position in specified formation (`LINE`, `PHALANX`, `SQUARE`, `WEDGE`, `SKIRMISH`). |
| `/ee war battle-test <civ1> <civ2>` | OP Level 2 | Spawns two opposing 10-man cohorts facing each other in formation and engages them in dynamic combat. |
| `/ee war siege-test <attacker> <defender>` | OP Level 2 | Initiates an artillery bombardment on fortifications 16 blocks in front of the player, breaching walls via `BlockUpdateQueue`. |
| `/ee war cohorts` | All Players | Lists all active physical cohorts, their unit strength, anchor positions, and current formations. |

---

## 4. Test Verification

The standalone test script `/home/user/eternal/test_war_regiment_phase.py` validates 16 critical verification assertions across all Phase 12 components:
- `FormationType` definitions and rank/file spacing metrics.
- `CohortFormationBrain` deterministic rotation transformation and surface placement.
- `RegimentCohort` casualty promotion, soldier tracking, and military outfitting.
- `SiegeEngineController` ballistic strikes and `BlockUpdateQueue` structural modification.
- `ArmyManager` physical cohort deployment and hostile cohort targeting.
- `WarEngine` and `SimulationEngine` level ticking integration.
- `EternalEngineCommands` registration of `/ee war deploy`, `battle-test`, `siege-test`, and `cohorts`.

```bash
python3 test_war_regiment_phase.py
# Results: 16/16 tests passed (100.0%)
```

# Phase 16: Playable Butterfly Effect & The Chronos Codex

## 1. Overview & Vision
Phase 16 elevates Eternal Engine's alternative timeline system into a core interactive game mechanic:
- **Playable Butterfly Effect (`DivergenceCascadeEngine`)**: When a player intervenes to prevent an anchor historical event, consequences ripple dynamically through the future. Downstream events that depended on the prevented crisis are pruned, authentic alternate historical events are synthesized, and geopolitical stability adjusts across empires.
- **Spatial Reality Rifts (`RealityRiftController`)**: A shimmering tear in spacetime appears at the exact real-world coordinates of the historical divergence, emitting portal particles and whispering spectral echoes of what transpired in the Prime Timeline.
- **The Chronos Codex (`ChronosCodexData` & `ChronosCodexScreen`)**: An illustrated, interactive parchment book GUI allowing players to explore branching timeline trees, geopolitical cartographic atlases, historical VIP dossiers, and daily global newspaper chronicles.

---

## 2. Core Architectural Components

### A. Divergence Cascade Engine (`DivergenceCascadeEngine.java`)
Manages the propagation of alternative history when anchor events are prevented:
1. **Deterministic Branch Seeding**:
   - Computes deterministic seed hashes from event keys, intervention causes, and years, spawning a dedicated `TimelineBranch`.
2. **Consequence Chain Analysis & Event Pruning**:
   - *Example: Preventing Archduke Franz Ferdinand's assassination (Sarajevo 1914)*:
     - **Prunes Obsolete Events**: `outbreak_ww1_1914`, `battle_somme_1916`, `treaty_versailles_1919`.
     - **Synthesizes Alternate Events**: *Trialist Imperial Constitutional Reform* (1915) granting autonomy to Slavic populations; *Danubian Economic League Accord* (1918) forming an early central European common market.
     - **Shifts Geopolitical Stability**: $+25\%$ stability bonus, preserving imperial structures and averting total war.
   - *Example: Preventing Julius Caesar's assassination (Ides of March 44 BCE)*:
     - **Prunes Obsolete Events**: `liberators_civil_war_43bc`, `battle_actium_31bc`, `fall_roman_republic_27bc`.
     - **Synthesizes Alternate Events**: *Senate Reconciliation Decree* (43 BCE); *Parthian Frontier Settlement* (40 BCE).
3. **Automated Rift Spawning**:
   - Directly instantiates a spatial `RealityRift` at the player's coordinates at the moment of cascade.

### B. Spatial Reality Rifts (`RealityRiftController.java`)
Renders physical anomalies in the world where history was rewritten:
- **Particle Aesthetics**: Emits multi-layered swirling portal vortices (`PORTAL`, `REVERSE_PORTAL`, `END_ROD`) at the rift anchor.
- **Prime Timeline Spectral Whispers**: When a player walks within 3.5 blocks of the rift, the tear reveals what occurred in the original continuum:
  > *"§d[Reality Rift]§r Through the shimmering tear in spacetime, you perceive: Archduke Franz Ferdinand and Sophie are struck by assassin bullets on the street corner of Sarajevo. Four empires collapse in the ensuing World War. In this reality, they survived."*

### C. The Chronos Codex (`ChronosCodexData.java` & `ChronosCodexScreen.java`)
Interactive client GUI with 4 dedicated parchment tabs:
1. **Timeline Tree Tab**:
   - Visual branch navigation displaying parent links, divergence years, prevented events, and alternate timeline milestones.
2. **Geopolitical Atlas Tab**:
   - Displays real-time civilization statistics, populations, treasuries, and territorial statuses.
3. **VIP Dossier Tab**:
   - Historical biographies, active alive/deceased statuses, and player diplomatic standings.
4. **Historical Chronicle Tab**:
   - In-game gazette newspaper reporting global affairs, commercial caravan trade, frontlines, and epidemiological alerts.

### D. Simulation Lifecycle & Server Hook
- Hooked into `SimulationEngine.onServerTick`:
  ```java
  if (server.overworld() != null) {
      timelineManager.getRiftController().tick(server.overworld());
  }
  ```

---

## 3. In-Game Commands (`/ee codex` & `/ee timeline ...`)

| Command | Permission | Description |
|---|---|---|
| `/ee codex [tab]` | All Players | Opens the Chronos Codex or displays specified tab (`tree`, `atlas`, `dossier`, `chronicle`). |
| `/ee timeline cascade <eventId> <cause>` | OP Level 2 | Triggers a dynamic divergence cascade, prunes downstream history, synthesizes alternate events, and opens a Reality Rift. |
| `/ee timeline rift [eventId]` | OP Level 2 | Spawns a shimmering Reality Rift with prime timeline echoes at player coordinates. |
| `/ee timeline rifts` | All Players | Lists all active spatial reality rifts and their locations. |

---

## 4. Test Verification

The standalone test script `/home/user/eternal/test_butterfly_codex_phase.py` validates 14 critical assertions:
- `RealityRiftController` particle emissions, spectral echoes, and proximity detection.
- `DivergenceCascadeEngine` consequence propagation, obsolete event pruning, alternate event synthesis, and rift generation.
- `ChronosCodexData` 4-tab data formatting (Timeline Tree, Atlas, Dossier, Chronicle).
- `ChronosCodexScreen` interactive client parchment book GUI with tab switching and page flips.
- `TimelineManager` integration of cascade engine and rift controller.
- `SimulationEngine` overworld server ticking hook for reality rifts.
- `EternalEngineCommands` registration of `/ee codex`, `timeline cascade`, `rift`, and `rifts`.

```bash
python3 test_butterfly_codex_phase.py
# Results: 14/14 tests passed (100.0%)
```

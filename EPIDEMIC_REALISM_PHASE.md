# Phase 15: Epidemic Quarantines & Medical Realism

## 1. Overview & Vision
Phase 15 physicalizes epidemiological history and disaster phenomena in Eternal Engine. Abstract statistical disease mortality is transformed into real-time in-world events:
- **Physical Contagion Transmission**: Airborne and droplet infection spreads dynamically between proximate `HumanNpcEntity` citizens with observable symptoms and stage progression.
- **Sanitary Cordons & Gate Barricades**: Quarantined settlements establish visible physical checkpoints with fence barriers, warning braziers, and sanitary inspectors repelling symptomatic travelers.
- **Plague Doctors & Field Hospitals**: Procedural canvas isolation tents with medical cots are deployed, garrisoned by plague doctors and physicians who examine patients and administer curative treatments.
- **Historical Disaster Phenomena**: Simulates authentic global catastrophes (such as the 536 CE volcanic winter, great floods, crop famines, and tectonic earthquakes) safely through the `BlockUpdateQueue`.

---

## 2. Core Architectural Components

### A. Proximity Contagion Transmission (`ContagionTransmissionBrain.java`)
Tracks and simulates individual infection lifecycles across living `HumanNpcEntity` instances:
1. **Four-Stage Infection Lifecycle**:
   - `INCUBATING`: Asymptomatic latent period ($<200$ ticks).
   - `SYMPTOMATIC`: Observable illness ($200$–$800$ ticks); slowness debuff applied, accompanied by coughing/sneeze particles (`ITEM_SLIME`).
   - `CRITICAL`: Severe systemic distress ($800$–$1400$ ticks); weakness debuff applied, fever smoke particles (`SMOKE`).
   - `RECOVERED`: Full resolution with permanent acquired immunity against the disease strain.
2. **Proximity Spread**:
   - Symptomatic and critical entities emit contagion within a 3.5-meter radius.
   - Transmission probability scales dynamically with medicine tier:
     $$\text{SpreadChance} = 0.06 \times (1.0 - \text{MedicineProtection})$$
   - Higher medicine tiers provide substantial protection: Modern ($90\%$), Industrial ($65\%$), Renaissance ($40\%$), Medieval ($20\%$), Ancient ($15\%$).

### B. Quarantine Cordon & Gate Barricades (`QuarantineCheckpointController.java`)
Erects physical quarantine containment cordons when a region is placed under quarantine:
- **Physical Barricades**: Spans roadways and city entrances with wooden barriers (`OAK_FENCE`) and warning smoke braziers (`CAMPFIRE`) enqueued through `BlockUpdateQueue`.
- **Sanitary Cordon Inspectors**: Spawns 2 `HumanNpcEntity` inspectors equipped with lanterns, leather armor, and inspection canes.
- **Active Border Enforcement**: Detects symptomatic or critical travelers approaching the barrier and pushes them back into the containment zone, preventing pathogen escape.
- **Clean Demolition**: When the epidemic ends, `dismantleCheckpoint` removes barricades and clears inspectors cleanly.

### C. Plague Doctors & Hospital Tents (`PlagueDoctorHospitalManager.java`)
Coordinates clinical intervention and emergency medical response:
- **Field Hospital Isolation Tents**: Constructs 5×5 canvas wool tents with patient cots (`WHITE_BED`), cauldrons, and medicinal supplies above surface ground elevation.
- **Medical Personnel**:
  - *Medieval / Renaissance*: Plague Doctors outfitted with beak masks (`LEATHER_HELMET`), black protective robes, examination canes (`STICK`), and medicinal herbs.
  - *Industrial / Modern*: Physicians equipped with medical coats and potions.
- **Patient Rounds**: Doctors navigate toward proximate infected citizens ($10$m search radius) and administer remedies, immediately transitioning critical infections to `RECOVERED` and restoring health with `REGENERATION` effects.

### D. Historical Disaster Incidents (`HistoricalDisasterEngine.java`)
Simulates cataclysms safely above ground elevation via `BlockUpdateQueue`:
- **`VOLCANIC_WINTER`** (e.g. 536 CE, 1816 CE): Obscures the sun with atmospheric ash particles, lowers ground temperature, freezes water surfaces, and covers terrain with frost snow layers (`SNOW`).
- **`GREAT_FLOOD`**: Waterlogs low-lying fields, turning terrain into saturated mud (`MUD`).
- **`FAMINE`**: Degrades fertile cropland (`FARMLAND` $\rightarrow$ `COARSE_DIRT`) and simulates crop failure.
- **`EARTHQUAKE`**: Shakes masonry, cracking stone brick fortifications (`CRACKED_STONE_BRICKS`).
- **`GREAT_FIRE`**: Enqueues fire hazard blocks along combustible structures.

### E. Simulation Lifecycle & Level Ticking
- Hooked into `SimulationEngine.onServerTick`:
  ```java
  if (EternalEngineConfig.COMMON.enableDisease.get() && server.overworld() != null) {
      diseaseEngine.tickLevel(server.overworld(), year);
  }
  ```

---

## 3. In-Game Commands (`/ee disease ...` & `/ee disaster ...`)

| Command | Permission | Description |
|---|---|---|
| `/ee disease cordon <regionId>` | OP Level 2 | Establishes a physical quarantine cordon with barricades and inspectors at player coordinates. |
| `/ee disease doctor-test` | OP Level 2 | Spawns a period-accurate Plague Doctor equipped with cane and medicine. |
| `/ee disease hospital-test` | OP Level 2 | Constructs a field hospital isolation tent with beds and physician. |
| `/ee disease infect-test [diseaseId]` | OP Level 2 | Infects nearby `HumanNpcEntity` citizens to test real-time contagion spread and symptoms. |
| `/ee disease status` | All Players | Displays active in-world infections, immune citizens, cordons, field hospitals, and disasters. |
| `/ee disaster <type>` | OP Level 2 | Triggers a historical disaster (`VOLCANIC_WINTER`, `GREAT_FLOOD`, `FAMINE`, `EARTHQUAKE`, `GREAT_FIRE`). |

---

## 4. Test Verification

The standalone test script `/home/user/eternal/test_epidemic_phase.py` validates 14 critical assertions:
- `ContagionTransmissionBrain` infection stages, symptom debuffs, proximity spread, and medicine tier mitigations.
- `QuarantineCheckpointController` physical barricades, braziers, inspectors, and pushback mechanics.
- `PlagueDoctorHospitalManager` canvas tents, isolation beds, doctor equipment, and patient cures.
- `HistoricalDisasterEngine` volcanic winter, flood, famine, earthquake, and fire block updates via queue.
- `DiseaseEngine` integration of contagion, checkpoints, hospitals, and disasters with `tickLevel`.
- `SimulationEngine` overworld server tick integration.
- `EternalEngineCommands` registration of `/ee disease cordon`, `doctor-test`, `hospital-test`, `infect-test`, `status`, and `/ee disaster`.

```bash
python3 test_epidemic_phase.py
# Results: 14/14 tests passed (100.0%)
```

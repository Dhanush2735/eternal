# Phase 17: Sovereign Throne Rooms & Grand Strategy Command

## 1. Overview & Vision
Phase 17 elevates historical governance in Eternal Engine from passive background simulation into a rich grand-strategy gameplay loop:
- **Capital Palace Throne Rooms (`RoyalCourtManager`)**: Sovereign rulers (`VipSlot`) no longer wander aimlessly. They reside in capital throne rooms surrounded by Praetorian bodyguards and court chancellors receiving foreign ambassadors and player petitions.
- **Geopolitical Diplomacy & Treaties (`DiplomaticTreatyEngine`)**: Nations formalize bilateral Non-Aggression Pacts, Defensive Alliances, Exclusive Trade Accords, and issue coercive ultimatums backed by military force.
- **Dynastic Succession & Coups (`DynasticSuccessionEngine`)**: Models royal bloodlines, heir legitimacy, succession laws (Primogeniture, Elective Senate), and pretender palace coups.
- **Imperial Command Dashboard (`ImperialCommandEngine`)**: Sovereign rulers and players claiming the throne commission imperial megaprojects, set strategic military doctrines, and dictate fiscal/monetary policies.

---

## 2. Core Architectural Components

### A. Sovereign Throne Rooms (`RoyalCourtManager.java`)
Garrisons capital palaces with living court figures:
- **Sovereign Ruler**: Spawns as a crowned `HumanNpcEntity` wearing a Golden Helmet crown, Diamond armor, and wielding a Golden Sword scepter.
- **Praetorian Royal Bodyguards**: Stationed flanking the throne equipped with iron armor, swords, and shields, protecting the crown against assassins and pretenders.
- **Imperial Chancellor**: Court diplomat holding diplomatic ledgers who manages foreign embassies.
- **Audience Petitions (`petitionAudience`)**: Players can approach the throne to petition for titles, lands, or military cohorts, evaluated by the sovereign.

### B. Geopolitical Diplomacy & Ultimatums (`DiplomaticTreatyEngine.java`)
Coordinates international diplomacy and treaty networks:
- **Four Bilateral Treaty Types**:
  - `NON_AGGRESSION_PACT`: Mutual ceasefire and border peace guarantee.
  - `DEFENSIVE_ALLIANCE`: Mutual defense against external aggressors (+40 diplomatic standing).
  - `EXCLUSIVE_TRADE_ACCORD`: Preferential tariff reductions and commodity flow.
  - `TRIBUTARY_VASSALAGE`: Annual tribute payment transferred to sovereign treasury.
- **Coercive Ultimatums**:
  - Nations issue ultimatums demanding tribute, territory, or disarmament.
  - **Resolution**: If accepted, diplomatic stability rises. If rejected, the treaty engine instantly issues a casus belli and declares war via `WarEngine`.

### C. Dynastic Succession & Coups (`DynasticSuccessionEngine.java`)
Governs crown succession and internal rebellions:
- **Succession Laws**:
  - `PRIMOGENITURE`: Eldest heir ascends the throne.
  - `ELECTIVE_SENATE`: Heir with highest legitimacy elected by court nobles.
  - `USURPATION_COUP`: Palace coup or military overthrow.
- **Palace Coups (`attemptUsurpation`)**: Ambitious pretenders and players can orchestrate coups; military-backed coups possess higher success probability, rewriting dynastic rulers and shifting regional stability.

### D. Imperial Command Dashboard (`ImperialCommandEngine.java`)
Unlocks sovereign grand strategy mechanics:
1. **Imperial Megaprojects**:
   - Rulers commission grand civic and defensive monuments (`GRAND_COLOSSEUM`, `CITY_WALL_CIRCUIT`, `ROYAL_CATHEDRAL`, `AQUEDUCT_VIADUCT`).
   - Deducts treasury costs, enqueues foundations and wooden scaffolding above ground elevation via `BlockUpdateQueue`, and spawns Imperial Masons.
2. **Grand Strategic Military Doctrines**:
   - `SCORCHED_EARTH`: Denies resources and burns infrastructure before retreating.
   - `ATTRITION_DEFENSE`: Entrenches regiments behind fortifications.
   - `DECISIVE_BLITZKRIEG`: Rapid concentrated cohort assaults against enemy capitals.
   - `COMMERCE_RAIDING`: Interdicts enemy trade caravans and shipping convoys.
3. **Fiscal & Monetary Policy**:
   - **Imperial Taxation**: Tax rates dynamically adjustable from $5\%$ to $50\%$.
   - **Currency Debasement**: Rulers can dilute coin precious metal content in the imperial mint for an immediate $+4000$ gold injection, at the cost of triggering economic inflation flags.

---

## 3. In-Game Commands (`/ee sovereign ...`)

| Command | Permission | Description |
|---|---|---|
| `/ee sovereign court <civId>` | OP Level 2 | Establishes a capital palace throne room with sovereign ruler, Praetorian guards, and chancellor at player coordinates. |
| `/ee sovereign petition <civId> <type>` | All Players | Requests an audience with the sovereign to petition for titles, land, or military aid. |
| `/ee sovereign treaty <civA> <civB> <type>` | OP Level 2 | Signs a bilateral treaty (`NON_AGGRESSION_PACT`, `DEFENSIVE_ALLIANCE`, `EXCLUSIVE_TRADE_ACCORD`, `TRIBUTARY_VASSALAGE`). |
| `/ee sovereign megaproject <type>` | OP Level 2 | Commissions an Imperial Megaproject (`GRAND_COLOSSEUM`, `CITY_WALL_CIRCUIT`, `ROYAL_CATHEDRAL`, `AQUEDUCT_VIADUCT`), deploying scaffolding and masons. |
| `/ee sovereign doctrine <civId> <doctrine>` | OP Level 2 | Sets strategic military doctrine (`SCORCHED_EARTH`, `ATTRITION_DEFENSE`, `DECISIVE_BLITZKRIEG`, `COMMERCE_RAIDING`). |
| `/ee sovereign tax <civId> <rate>` | OP Level 2 | Sets imperial tax rate percentage ($5\%$ to $50\%$). |
| `/ee sovereign debase <civId>` | OP Level 2 | Debases currency purity in the imperial mint: immediate $+4000$ gold injection with inflation consequences. |
| `/ee sovereign coup <civId> <pretender>` | OP Level 2 | Orchestrates a palace coup attempting to seize the throne. |

---

## 4. Test Verification

The standalone test script `/home/user/eternal/test_sovereign_command_phase.py` validates 12 critical assertions:
- `RoyalCourtManager` sovereign ruler, Praetorian bodyguards, chancellor spawning, and audience petitions.
- `DiplomaticTreatyEngine` non-aggression, alliances, trade accords, and ultimatums.
- `DynasticSuccessionEngine` succession laws, heir generation, and palace coup usurpations.
- `ImperialCommandEngine` megaproject commissions, military doctrines, tax rates, and currency debasement.
- `SimulationEngine` wiring of court manager, treaty engine, succession, and imperial command engines.
- `EternalEngineCommands` registration of all sovereign court and grand strategy commands.

```bash
python3 test_sovereign_command_phase.py
# Results: 12/12 tests passed (100.0%)
```

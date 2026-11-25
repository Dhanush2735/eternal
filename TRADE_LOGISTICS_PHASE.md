# Phase 13: Overland Trade Caravans & Maritime Logistics

## 1. Overview & Vision
Phase 13 elevates Eternal Engine's economic simulation from static macro calculations into dynamic, physicalized trade logistics directly rendered in the Minecraft world:
- Overland trade routes now deploy **`CaravanEntityGroup`** units traversing real-world Terra++ road networks with caravan masters, pack animals, and armed guards.
- Maritime trade routes deploy **`MerchantShipConvoy`** vessels navigating navigable waterways, rivers, and coastal ocean paths to dock at pier structures.
- Market values fluctuate dynamically via **`CommodityPricingEngine`**, responding to distance freight costs, danger markups, and local supply/demand imbalances.
- Perilous routes and low-authority regions trigger highwaymen raids via **`BanditAmbushEngine`**, introducing combat encounters, escort opportunities, and economic route disruptions.

---

## 2. Core Architectural Components

### A. Dynamic Commodity Pricing (`CommodityPricingEngine.java`)
Tracks and prices 13 foundational trade commodities across 5 strategic categories:
- **Staples**: `WHEAT`, `BREAD`, `TIMBER`, `STONE`
- **Strategic & Fuel**: `COAL`, `IRON_INGOT`, `COPPER_INGOT`
- **Luxuries**: `WOOL`, `WINE`, `SILK`, `SPICES`
- **Precious Goods**: `GOLD_INGOT`, `EMERALD`

Prices are dynamically computed based on:
$$\text{UnitPrice} = \text{BasePrice} \times (1 + \text{DistanceKm} \times 0.0003) \times (1 + \text{DangerLevel} \times 0.008) \times \text{clamp}\left(\frac{\text{Demand}}{\text{Supply}}, 0.4, 3.0\right)$$

### B. Overland Caravan Groups (`CaravanEntityGroup.java`)
Materializes complete trade caravans moving across the terrain:
- **Lead Caravan Master**: A `HumanNpcEntity` outfitted in merchant attire, carrying navigation tools, who leads the expedition along road waypoints.
- **Pack Animals**: 2–4 pack animals (Donkeys/Mules/Camels/Llamas) equipped with chests, following the master.
- **Caravan Guards**: 2–3 armed `HumanNpcEntity` guards equipped with chainmail armor, iron swords, and shields, holding defensive flank formations.
- **Chain of Command**: If the master falls in battle, the senior guard is immediately promoted to caravan master. If all guards and the master fall, the caravan transitions to `LOOTED`.
- **Delivery**: Upon reaching the destination terminal waypoint, the cargo value is deposited into the destination civilization's treasury and the expedition is cleanly concluded.

### C. Maritime Merchant Convoys (`MerchantShipConvoy.java`)
Materializes waterborne trade along navigable waterways, rivers, and ocean corridors:
- **Lead Vessel**: Primary merchant boat carrying the Merchant Captain (`HumanNpcEntity`).
- **Escort Vessels**: Flanking escort boats carrying Ship Marines equipped with crossbows and armor.
- **Waterway Navigation**: Smoothly propels and steers vessels along river/ocean waypoints.
- **Harbor Docking**: Detects proximity to port/pier coordinates, unloads cargo value into the local port economy, and transitions to `DOCKED`.

### D. Highwaymen & Bandit Ambush Engine (`BanditAmbushEngine.java`)
Simulates supply line interdiction and wilderness hazards:
- Regularly evaluates active caravans against route danger levels and civilization authority scores.
- Spawns coordinated squads of 2–4 `HumanNpcEntity` Highwaymen equipped with iron axes, bows, and leather armor in an ambush perimeter.
- **Resolution**:
  - If guards eliminate the bandits, the caravan returns to `TRAVELING` status and continues its route.
  - If bandits defeat the caravan guards and master, the cargo is looted, and the parent `TradeRoute` suffers a disruption penalty (`tradeRoutes.disruptRoute(...)`).

### E. Central Logistics Coordinator (`LogisticsManager.java`)
- Tracks all active `CaravanEntityGroup` and `MerchantShipConvoy` instances.
- Executes world ticking, waypoint updates, and periodic ambush rolls.
- Integrated into `SimulationEngine.onServerTick`, advancing logistics every server tick in the overworld.

---

## 3. In-Game Commands (`/ee trade ...`)

| Command | Permission | Description |
|---|---|---|
| `/ee trade caravan <routeId>` | OP Level 2 | Materializes an overland trade caravan with master, donkeys, and guards at the player's position. |
| `/ee trade ambush-test` | OP Level 2 | Spawns a squad of highwaymen to ambush the nearest active trade caravan in real time. |
| `/ee trade ship-test [routeId]` | OP Level 2 | Spawns a maritime merchant convoy with captain and marine escort at water coordinates. |
| `/ee trade logistics` | All Players | Displays a real-time status summary of active caravans, merchant ships, cargo values, and ongoing ambushes. |
| `/ee trade prices [civId]` | All Players | Displays current market prices and categories for all registered trade commodities. |

---

## 4. Test Verification

The standalone test script `/home/user/eternal/test_trade_logistics_phase.py` validates 16 critical assertions:
- `CommodityPricingEngine` price calculations, distance multipliers, and supply/demand ratio bounds.
- `CaravanEntityGroup` master succession, casualty cleanup, pack animal following, and guard protection.
- `MerchantShipConvoy` vessel navigation, captain/marine outfitting, and harbor docking.
- `BanditAmbushEngine` highwayman spawning, combat engagement, and economic route disruption on raid.
- `LogisticsManager` orchestration of physical overland caravans and merchant ships.
- `TradeRouteEngine` materialization hooks.
- `SimulationEngine` server-level ticking integration.
- `EternalEngineCommands` registration of `/ee trade caravan`, `ambush-test`, `ship-test`, `logistics`, and `prices`.

```bash
python3 test_trade_logistics_phase.py
# Results: 16/16 tests passed (100.0%)
```

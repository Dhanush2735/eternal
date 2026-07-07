# Eternal Engine - Minecraft Forge Mod

Historical Earth simulation for Minecraft, built on Terra++.

## Build Requirements

- Minecraft: 1.20.1
- Forge: 47.2.0
- Java: 17
- Gradle: 8.8+
- **Required:** Terra++ 2.0+

## Project Structure

```
mod-src/
├── build.gradle              # Gradle build configuration
├── gradle.properties         # Build properties
├── settings.gradle           # Gradle settings
└── src/main/
    ├── java/com/eternalengine/
    │   ├── EternalEngineMod.java           # Main mod class
    │   ├── config/
    │   │   └── EternalEngineConfig.java    # Forge config (Common/Server/Client)
    │   ├── terrabridge/
    │   │   ├── TerraBridge.java            # Single point of Terra++ access
    │   │   ├── TerraCoordinateHelper.java  # Coordinate conversion utilities
    │   │   └── TerraOsmBridge.java         # OSM data access
    │   ├── compatibility/
    │   │   └── ModCompatibilityChecker.java # Optional mod detection
    │   ├── core/
    │   │   ├── SimulationEngine.java       # Master simulation controller
    │   │   ├── WorldStateManager.java      # Thread-safe state storage
    │   │   ├── AsyncTaskManager.java       # Async task handling
    │   │   ├── PersistenceManager.java     # File I/O with atomic writes
    │   │   ├── SimulationTickScheduler.java # Staggered system updates
    │   │   ├── PerformanceMonitor.java     # Performance tracking
    │   │   └── VersionManager.java         # Data migrations
    │   ├── network/
    │   │   ├── EternalEnginePacketHandler.java # Network registration
    │   │   └── packets/
    │   │       ├── BasePacket.java
    │   │       ├── SimulationStatePacket.java
    │   │       ├── ReputationUpdatePacket.java
    │   │       ├── SpeedChangePacket.java
    │   │       ├── VipCommandPacket.java
    │   │       ├── EventNotificationPacket.java
    │   │       ├── CivilizationStatePacket.java
    │   │       ├── HistoryLogPacket.java
    │   │       ├── BuildingInfoPacket.java
    │   │       ├── MapOverlayPacket.java
    │   │       ├── VipStatusPacket.java
    │   │       ├── SpeedVotePacket.java
    │   │       └── RequestDataPacket.java
    │   ├── capability/
    │   │   ├── EternalEngineCapabilities.java
    │   │   ├── IPlayerSimulationData.java
    │   │   ├── PlayerSimulationCapability.java
    │   │   ├── IEntityCivilizationData.java
    │   │   ├── EntityCivilizationCapability.java
    │   │   ├── IChunkEraData.java
    │   │   └── ChunkEraCapability.java
    │   ├── clock/
    │   │   └── SimulationClockManager.java  # Time and speed control
    │   └── commands/
    │       └── EternalEngineCommands.java   # All /ee commands
    └── resources/
        ├── META-INF/
        │   └── mods.toml                    # Mod metadata
        └── pack.mcmeta                      # Resource pack meta
```

## Files Created (Foundation)

### Build System
- `build.gradle` - Complete Forge Gradle configuration with dependencies
- `gradle.properties` - Version numbers and mod properties
- `settings.gradle` - Plugin management
- `mods.toml` - Mod metadata and dependency declarations
- `pack.mcmeta` - Resource pack metadata

### Core Classes
- `EternalEngineMod.java` - Main mod class with initialization
- `EternalEngineConfig.java` - Full config with Common/Server/Client sections

### Terra++ Integration
- `TerraBridge.java` - Single point of Terra++ access
- `TerraCoordinateHelper.java` - Coordinate conversion utilities
- `TerraOsmBridge.java` - OpenStreetMap data access

### Simulation Core
- `SimulationEngine.java` - Master controller
- `WorldStateManager.java` - Thread-safe state storage
- `AsyncTaskManager.java` - Async task handling with priority queue
- `PersistenceManager.java` - Atomic file I/O with backups
- `SimulationTickScheduler.java` - Staggered system updates
- `PerformanceMonitor.java` - Per-system performance tracking
- `VersionManager.java` - Data version migrations

### Network
- `EternalEnginePacketHandler.java` - Packet registration
- 12 packet types for client-server communication

### Capabilities
- Player simulation data (VIP, reputation, actions)
- Entity civilization data (role, loyalty)
- Chunk era data (buildings, damage)

### Clock & Commands
- `SimulationClockManager.java` - Speed control with auto-slowdown
- `EternalEngineCommands.java` - Full /ee command tree

## Remaining Implementation (Not Yet Created)

The specification requires many more systems. Here's what still needs to be implemented:

### Simulation Systems (50+ classes)
- `history/` - HistoricalEventDatabase, EventChainEngine, TimelineManager
- `civilization/` - CivilizationManager, FactionRelationshipSystem, TerritoryManager
- `population/` - PopulationEngine, MigrationSystem, ClassSystem
- `disease/` - DiseaseEngine, MedicineSystem, EpidemicTracker
- `war/` - WarEngine, BattleSimulator, ArmyManager, SiegeSystem, etc.
- `trade/` - TradeRouteEngine, EconomyEngine, CurrencySystem
- `agriculture/` - AgricultureEngine, FamineSystem, CropSystem
- `religion/` - ReligionEngine, ReformationSystem, etc.
- `technology/` - TechnologyEngine, TechnologyTree
- `climate/` - ClimateEngine, VolcanicSystem
- `era/` - EraSystem, EraAnchorSystem, EraBuildingGenerator
- `buildings/` - BuildingHistoryDatabase, BuildingEvolutionEngine
- `vip/` - VipSystem, VipCommandSystem, AuthoritySystem
- `reputation/` - ReputationEngine, ActionEffects, RedemptionSystem
- `player/` - PlayerActionDetector, DivergenceDetector, TutorialSystem
- `villager/` - VillagerManager, VillagerVirtualizer
- `narrative/` - NarrativeTemplateEngine, OllamaIntegration

### UI/Client (CLIENT SIDE ONLY)
- HUD classes (ReputationHud, SimulationHud)
- GUI classes (VipCommandGui, HistoryBookGui)
- Map overlay integration

### Data Files
- Event JSON files (stone_age_events.json through modern_events.json)
- Civilization JSON files
- VIP JSON files (21 era-specific files)
- Disease, religion, trade route JSONs
- Name databases (20+ regional files)
- Alternative timeline branches
- Famous buildings database
- Technology tree
- Narrative templates
- Localization (en_us.json)

## Building

```bash
cd mod-src
./gradlew build
```

The compiled mod will be in `build/libs/`.

## Notes

- Terra++ is a HARD DEPENDENCY - the mod will crash on startup if not present
- All optional mods are null-checked and fail gracefully
- Server-side simulation only - clients receive display packets
- Total tick budget: 10ms maximum
- Entity virtualization beyond 128 blocks
```

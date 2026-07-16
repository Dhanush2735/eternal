# Eternal Engine Build Handoff

Generated as of Phase 10C.

This document is the continuation guide for the Eternal Engine Forge mod. It records the architecture, completed phases, safety rules, repository context, and next recommended work.

---

## 1. Project identity

```text
Project: Eternal Engine
Mod ID: eternalengine
Main package: com.eternalengine
Minecraft: 1.20.1
Forge: 47.2.0
Java: 17
Required dependency: Terra++ / terraplusplus 2.0.0-alpha currently local jar
```

The current project lives at:

```text
/home/user/eternal
```

Reference/prototype logic lives at:

```text
/home/user/eternal_reference
```

The reference project is not production code. It validates logic only.

---

## 2. Critical architecture rules

### Terra++ is the truth source

Do not recreate Terra++ logic in Eternal.

All real world facts must come from:

```text
TerraBridge
TerraCoordinateHelper
WorldDebugSnapshot
ChunkTelemetryScanner
Terra++ / TerraDensityFunctions / EsaWorldCoverIndex
```

Do not use temporary scratch scripts as truth for real code.

### Multiplayer server is authoritative

Clients and bots may request or propose actions, but the server validates all effects.

### LLM is advisory only

The LLM/Mindcraft/Ollama path is:

```text
server facts -> bot/LLM -> LlmProposal -> LlmProposalValidator -> ServerDecisionQueue -> subsystem handler later
```

LLM must never directly:

```text
place blocks
break blocks
edit saves
set reputation directly
mutate timeline directly
run raw server commands
spawn entities directly
give items directly
```

### World mutation rule

Phase work so far mostly avoids world mutation. Future block placement must use a validated queue and only place above Terra++ surface.

---

## 3. Completed phases

```text
Phase 0A: Terra++ Truth Bridge
Phase 0B: Debug Snapshot + Chunk Telemetry
Phase 0C: Anomaly Detection + Root Cause Mapper
Phase 0D: Incident Creator + Difficulty Scaler
Phase 0E: Reputation + VIP Takeover Foundation
Phase 0F: Current Affairs Foundation
Phase 0G: Multiplayer-safe LLM Brain Bridge
Phase 0H: Vehicle Foundation
Phase 0I: Persistence + Async Safety Hardening
Phase 0J: Build / Compile Stabilization Pass
Phase 1A: Core Server Integration
Phase 1B: Command Integration for Foundation Systems
Phase 1C: Compile-compatibility cleanup pass
Phase 2A: Timeline Seed + Alternative Branch Foundation
Phase 2B: Timeline Integration + Commands
Phase 2C: Historical Event Foundation
Phase 2D: Historical Event Integration and Commands
Phase 3A: War Foundation
Phase 3B: War Integration and Commands
Phase 3C: Disease Foundation
Phase 3D: Disease Integration and Commands
Phase 3E: Trade + Economy Foundation
Phase 3F: Trade + Economy Integration and Commands
Phase 3G: Agriculture + Famine Foundation
Phase 3H: Agriculture + Famine Integration and Commands
Phase 3I: Population + Migration Foundation
Phase 3J: Population Integration and Commands
Phase 4A: Civilization + Territory Foundation
Phase 4B: Civilization Integration and Commands
Phase 4C: Compile-risk audit and cleanup
Phase 5A: VIP System Foundation
Phase 5B: VIP Takeover Event Integration and Commands
Phase 5C: Player Action Detector Foundation
Phase 5D: Player Action Integration Commands + Divergence Testing
Phase 6A: Bot Current-Affairs + Mindcraft Bridge Foundation
Phase 6B: Bot Bridge Integration and Commands
Phase 6C: Server-local Ollama Brain Bridge Foundation
Phase 6D: Brain Proposal Processing Commands
Phase 7A: Building History + Spatial Index Foundation
Phase 7B: Building Integration and Commands
Phase 7C: Building Anomaly Rules
Phase 8A: Religion + Technology Foundation
Phase 8B: Religion + Technology Integration and Commands
Phase 9A: Climate Foundation
Phase 9B: Climate Integration and Commands
Phase 10A: Compile-risk Audit + Cleanup Checkpoint
Phase 10B: Packet + Capability Compile-risk Audit
Phase 10C: Full static source audit + handoff document
```

---

## 4. Current Java package inventory

Static audit at Phase 10C:

```text
Java files: 166
Brace issues: none
```

Package counts:

```text
affairs: 8
agriculture: 4
anomaly: 8
bot: 3
brain: 12
buildings: 5
capability: 7
civilization: 5
climate: 3
clock: 1
commands: 1
compatibility: 1
config: 1
core: 7
disease: 9
history: 5
incident: 7
network: 14
player: 6
population: 7
religion: 2
reputation: 5
technology: 4
telemetry: 2
terrabridge: 3
timeline: 6
trade: 4
vehicle: 7
vip: 10
war: 8
```

---

## 5. Important implemented systems

### Terra truth / telemetry

```text
TerraBridge
TerraCoordinateHelper
WorldDebugSnapshot
ChunkTelemetryScanner
```

Provides F3-style large-scale telemetry and real Terra++ fact access.

### Anomaly detection

```text
AnomalyType
AnomalyRecord
RootCauseMapper
AnomalyDetectionEngine
BuildingAnomalyDetector
```

Detects terrain/surface/building anomalies and maps them to likely methods/code paths.

### Incidents

```text
IncidentKind
IncidentType
DifficultyScaler
IncidentSeed
IncidentContext
IncidentRecord
IncidentCreatorEngine
```

Negative incidents scale with Minecraft difficulty.

### Reputation and VIP

```text
ReputationEngine
ActionReputationEffects
VipSystem
VipSlot
VipTakeoverSystem
VipCommandSystem
AuthoritySystem
```

VIP takeover through commands is implemented; later actual entity death integration should call the same path.

### Current affairs and bots

```text
CurrentAffairsEngine
CurrentAffairsBriefing
BotCurrentAffairsService
MindcraftBridge
OllamaBrainBridge
```

Bots explain server-provided facts and return structured proposals only.

### Timeline and history

```text
TimelineSeed
TimelineBranch
TimelineManager
AlternativeTimelineEngine
HistoricalEvent
HistoricalEventDatabase
EventChainEngine
```

Event prevention can create deterministic alternative timeline branches.

### Simulation domains

```text
WarEngine
DiseaseEngine
TradeRouteEngine
EconomyEngine
AgricultureEngine
PopulationEngine
MigrationSystem
ClassSystem
UrbanizationSystem
CivilizationManager
TerritoryManager
ReligionEngine
TechnologyEngine
ClimateEngine
BuildingHistoryDatabase
```

These are foundations, not full production-depth simulations yet.

---

## 6. Key commands currently available

The command root is:

```text
/ee
```

Important commands:

```text
/ee status
/ee probe
/ee scan chunk [step]
/ee anomaly [probe|chunk]
/ee affairs [global|war|disease|timeline|debug]
/ee building [history|add-test|nearby|info|infer|damage|anomalies]
/ee timeline [branch|divergences|roll|diverge]
/ee event [list|pending|info|trigger|prevent|load]
/ee disease [list|info|trigger|quarantine]
/ee war [list|info|declare|end|army test]
/ee trade [list|info|open|close|disrupt]
/ee economy [treasury|add|subtract]
/ee agriculture [food|boost|climate]
/ee climate [list|active|region|temp|precip]
/ee famine [list|trigger|end]
/ee population [get|set]
/ee migration [process|list]
/ee civ [list|active|info|create]
/ee relation [score|set]
/ee territory [owner|create-test]
/ee vip [list|info|claim|release|takeover-test|kill|successors]
/ee actions recent
/ee divergence [checks|confidence|mark]
/ee bot [connect|briefing|proposals|proposal-test]
/ee brain [availability|queue|audit|test|affairs-propose|anomaly-propose]
```

---

## 7. Compile and environment notes

Use Java 17.

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew clean compileJava --no-daemon --max-workers=1
```

The Arena sandbox can download/use Java 17 temporarily, but full ForgeGradle MCP remapping exceeded sandbox memory and killed the Gradle daemon before real Java source compilation. This is an environment limit, not confirmed project source failure.

Run compile locally on a machine with at least 4GB RAM, preferably 6-8GB.

If compile fails, fix errors in small groups and rerun.

---

## 8. Current audit notes

Phase 10C static audit:

```text
Java source brace balance: OK
No Fabric imports found
No System.out found
No OpenAI/Claude/Gemini/API-key direct references found
No TODO/FIXME/placeholder/stub matches found in normal source scan
```

Remaining `return null;` matches exist in safe/fallback APIs such as `TerraBridge`, `TerraCoordinateHelper`, `PersistenceManager`, and `TerraOsmBridge`. These are expected nullable/failure returns, not packet decoder nulls.

Packet/capability cleanup already fixed:

```text
Network decoder no longer returns null
Packet send methods guard null channel/packet
Player capability access no longer orElseThrow-crashes
SimulationStatePacket fake config helper removed
SpeedVotePacket routes to SimulationClockManager
VipCommandPacket validates VIP before routing
```

---

## 9. Known risks to watch

### EternalEngineCommands.java is very large

It currently centralizes all commands. It works for phase velocity, but should later be split into subsystem command classes.

Recommended future refactor:

```text
commands/AnomalyCommands.java
commands/BuildingCommands.java
commands/TimelineCommands.java
commands/WarCommands.java
commands/DiseaseCommands.java
commands/VipCommands.java
commands/BotBrainCommands.java
```

### TerraOsmBridge still uses Overpass directly

This is acceptable only as optional/fallback. Future work should prefer Terra++ internal OSM/OHM indexes and cache/offline data.

### World mutation systems not yet implemented

Block placement, building generation, and visual/entity materialization are not yet implemented. When added, they must use main-thread queues and Terra++ surface placement.

### Data depth is foundational

Many systems have foundation/default data only. Real historical accuracy requires JSON data packs for events, VIPs, civilizations, religions, technology, buildings, climate, names, etc.

---

## 10. Next recommended phases

### Phase 11A: Command refactor foundation

Split the huge command file into subsystem classes or at least helper registration methods.

### Phase 11B: Data JSON foundation

Add starter JSON files for:

```text
historical events
VIPs
civilizations
religions
technologies
climate events
building examples
narrative templates
```

### Phase 11C: Local compile fix pass

Run compile locally and paste first errors. Fix in batches.

### Phase 12A: World-safe BlockUpdateQueue

Before any building/vehicle materialization, implement block queue with:

```text
main-thread only placement
max blocks per tick
chunk loaded checks
never below surface
```

### Phase 12B: Actual VIP entity/death integration

Connect `LivingDeathEvent` VIP tags/entities to:

```text
VipTakeoverSystem
VipSystem.assignAfterTakeover
ReputationEngine
Timeline/Event systems
```

### Phase 12C: Mindcraft external bridge transport

Replace in-memory `MindcraftBridge` with local file/WebSocket/HTTP transport if needed, preserving proposal validation.

---

## 11. Instructions for next AI/session

1. Read this file first.
2. Read `BUILD_VALIDATION.md`.
3. Do not generate all remaining systems at once.
4. Continue in phases.
5. At the start of each phase, inspect the real project files relevant to that phase.
6. Do not use temporary scratch logic as Terra++ truth.
7. Keep LLM as advisory only.
8. After each phase, run static checks:

```bash
python3 - <<'PY'
from pathlib import Path
root=Path('src/main/java')
issues=[]
for p in root.rglob('*.java'):
    s=p.read_text(errors='ignore'); bal=0
    for i,ch in enumerate(s):
        if ch=='{': bal+=1
        elif ch=='}': bal-=1
        if bal<0:
            issues.append((str(p),'negative',i)); break
    if bal!=0: issues.append((str(p),'balance',bal))
print('brace_issues', issues[:50])
PY

grep -RIn "TODO\|FIXME\|placeholder\|Placeholder\|stub\|System\.out\|net.fabricmc\|OpenAI\|Claude\|Gemini\|api_key\|apikey\|token\|secret" src/main/java | head -120
```

9. If local compile errors are available, prioritize compile fixes before new features.

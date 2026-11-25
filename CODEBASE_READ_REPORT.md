# Codebase Read Report — Eternal Engine + Terra3

*Generated from a direct read of the extracted archive. Every number and claim below came from a
command run against the files on disk, not from the project's own documents. Where the project docs
disagree with the code, the code wins and the disagreement is called out.*

---

## 1. What the archive contains

| Project | Size | Java files | Java lines | Role |
|---|---|---|---|---|
| `eternal/` | 56 MB | 270 | 43,259 | The mod: `com.eternalengine`, mod id `eternalengine` |
| `terra3/` | 9.5 MB | 136 | 45,634 | Terra++ 2.0.0-alpha fork + vendored OpenCubicChunks |

Both are git repositories. `eternal` has 3 commits (`Initial commit`, `eternal`, `files`).

File census in `eternal/` (excluding `.git` and `.gradle`):

```
270 java      26 html      3 svg
 53 md        22 py        3 jar
 44 json     14 png        2 gradle
 10 mcfunction 6 txt       2 properties
```

Build target: Minecraft 1.20.1, Forge 47.2.0, Java 17. `terraplusplus` is declared
`mandatory = true` in `mods.toml`, and the jar is vendored at
`eternal/libs/terraplusplus-2.0.0-alpha.jar` (10,724,026 bytes). Optional deps: `mca`,
`minecolonies`, `create`, `sereneseasons` (all `mandatory = false`).

---

## 2. Where the weight actually is

Lines of Java per package, descending:

```
 5010   6   commands          1272   9   trade
 3709  12   population        1165   7   capability
 3212   7   core              1119  13   disease
 2923   7   skin              1072  12   war
 2467  12   civilization       827  13   brain
 2339   8   villager           790   7   player
 2135  16   timeline           751  11   vip
 1878   5   terrabridge        737   5   history
 1599  13   buildings          670  10   chrononexus
 1363  15   network            615   4   sovereign
                               605   1   config
                               563   4   entity
                               547   8   anomaly
                               487   4   ecs
                               468   1   web
                               462   1   clock
                               431   4   carto
                               425   8   affairs
                               418   5   client
                               413   7   incident
                               396   5   reputation
                               382   2   telemetry
                               282   7   vehicle
                               260   4   agriculture
                               206   3   data
                               205   3   climate
                               197   2   religion
                               188   1   compatibility
                               176   3   bot
                               175   4   technology
```

Two things fall out of this immediately:

**The command layer is the biggest single subsystem.** `EternalEngineCommands.java` is 236,482
bytes on its own — larger than any other file in either project. The `/ee` command tree is where
most of the observable behaviour lives.

**There is a long tail of thin packages.** `religion` (2 files, 197 lines), `technology` (4 files,
175 lines), `bot` (3 files, 176 lines), `climate` (3 files, 205 lines), `agriculture` (4 files,
260 lines). Read directly, `religion/Religion.java` is a 73-line immutable data record with
`addFollowers` / `adjustPoliticalPower` and a `clamp100` helper. `ReligionEngine` is 124 lines.
These are foundations, not finished systems — and that matches the master plan's own complaint
that "the simulation lives predominantly as abstract state in memory and text inside `/ee` command
outputs."

The heavy end is where the real engineering happened: `population` (3,709 lines, including
`ChunkPopulation.java` at 56,580 bytes and `PopulationResolver.java` at 32,173), `skin` (2,923
lines of procedural texture compositing), and `villager` (2,339 lines, including
`PresenceLocalSpawner.java` at 45,592 bytes).

---

## 3. Static checks, run against the real source

### Brace balance (the project's own check from the handoff doc)

Scanned all 270 `.java` files under `src/main/java`:

```
java files scanned: 270
brace_issues: []
```

**Zero issues.** No negative balance, no unclosed file.

### Forbidden-pattern scan

The handoff doc's grep for `TODO|FIXME|placeholder|stub|System.out|net.fabricmc|OpenAI|Claude|
Gemini|api_key|apikey|token|secret` returns **24 matches**. All 24 were read. All are benign:

- `brain/RemoteBrainBridge.java` and `config/EternalEngineConfig.java` mention
  "LiteLLM/OpenAI-compatible" **in comments and config help text** describing an OpenAI-compatible
  HTTP endpoint. No SDK import, no hardcoded key.
- `chrononexus/ParadoxLoopController.java` and `ChronoNexusCampaignPhase.java` match on the word
  "secretly" — quest flavour text ("You must secretly deliver the Time Machine to Year -3000 BC").
- `System.out` appears only in `skin/SkinSelfTest.java`, a standalone offline harness, not in
  gameplay code.

**No Fabric imports. No hardcoded credentials. No API keys in source.**

---

## 4. The handoff doc is out of date — in your favour

`ETERNAL_ENGINE_BUILD_HANDOFF.md` ends with "Instructions for next AI/session" and lists Phase
12A/12B/12C as the next work. **Two of those three are already built.** Verified file by file:

### Phase 12A — world-safe `BlockUpdateQueue`: ✅ DONE

`src/main/java/com/eternalengine/buildings/BlockUpdateQueue.java` (184 lines) implements every
requirement the handoff lists:

| Requirement | Evidence |
|---|---|
| Main-thread only placement | `public int tick(MinecraftServer server, int maxBlocks)` with the javadoc "Must be called from the main server thread." |
| Max blocks per tick | `int budget = Math.max(0, maxBlocks <= 0 ? DEFAULT_MAX_BLOCKS_PER_TICK : maxBlocks);` then `while (processedThisTick < budget)` |
| Chunk loaded checks | `ServerLevel level = server.getLevel(task.dimension());` before placement |
| Never below surface | `int surfaceY = TerraCoordinateHelper.getSurfaceY(level, pos.getX(), pos.getZ()); if (pos.getY() <= surfaceY) { ... skip ... }` |

It is wired into the engine: `SimulationEngine.java:127` declares
`private final BlockUpdateQueue blockUpdateQueue;`. It also has priority ordering, a monotonic
sequence number, per-chunk `cancel(dimension, chunkX, chunkZ)`, and atomic counters.

### Phase 12B — VIP entity/death integration: ✅ DONE

The handoff asks to "connect `LivingDeathEvent` VIP tags/entities to `VipTakeoverSystem`,
`VipSystem.assignAfterTakeover`, `ReputationEngine`, Timeline/Event systems." That chain exists
end to end:

```
PlayerActionDetector.java:48    @SubscribeEvent
                                public static void onLivingDeath(LivingDeathEvent event)
PlayerActionDetector.java:137   VipTakeoverResult result = SimulationEngine.getInstance()
                                    .getVipTakeoverSystem()...
PlayerActionDetector.java:140   SimulationEngine.getInstance().getVipSystem()
                                    .assignAfterTakeover(player, vip.vipId(), year, ...)
VipSystem.java:104              public boolean assignAfterTakeover(ServerPlayer, String, int, String)
SimulationEngine.java:181       this.vipTakeoverSystem = new VipTakeoverSystem(this.reputationEngine);
```

The same takeover path is also reachable from a command (`EternalEngineCommands.java:3623-3629`).
`VipTakeoverSystem` takes the `ReputationEngine` in its constructor, so the reputation side of the
chain is connected too.

### Phase 12C — Mindcraft external bridge transport: ❌ STILL OPEN

`src/main/java/com/eternalengine/bot/MindcraftBridge.java` is 91 lines and is exactly what the
handoff says to replace: three `ConcurrentHashMap`s (connected bots, latest briefings, pending
proposal queues) with no file, WebSocket, or HTTP transport. This is **the one genuinely
unstarted Phase 12 item.**

---

## 5. The README is stale — it undersells the project badly

`README.md` has a section titled **"Remaining Implementation (Not Yet Created)"** listing "50+
classes." I checked 36 of the named classes against disk. **31 of them exist.**

The five that genuinely do not exist under those names:

| Claimed missing | Actual state |
|---|---|
| `ReformationSystem` | Not present anywhere. No file, no reference. **Genuine gap.** |
| `VolcanicSystem` | Not present. Only `player/InWorldInterventionDetector.java` mentions volcanoes at all. **Genuine gap.** |
| `NarrativeTemplateEngine` | Not present. **No file in either project matches `*Narrative*` at all.** The `narrative/` package does not exist. **Genuine gap.** |
| `EraSystem` | Renamed/reshaped — the era work lives in `buildings/EraBuildingPlanner.java`, `capability/ChunkEraCapability.java`, and `vehicle/VehicleEra.java`. Not a gap. |
| `ActionEffects` | No class by that name; the effects work is folded into `reputation/ReputationEngine` (5 files, 396 lines). Partial gap. |

The README also predates `HumanNpcEntity` and `HumanNpcRenderer` — both exist
(`entity/HumanNpcEntity.java`, `client/HumanNpcRenderer.java`) and the renderers are registered.

**Practical consequence: if you hand this repo to a fresh session and tell it to read the README,
it will rebuild ~31 classes that already exist.** The README needs rewriting before it is used as
an onboarding document.

---

## 6. Event-bus wiring — verified correct

`grep -RIn "EVENT_BUS.register"` returns only two explicit registrations:

```
EternalEngineMod.java:92     MinecraftForge.EVENT_BUS.register(this);
SimulationEngine.java:215    MinecraftForge.EVENT_BUS.register(this);
```

That looks alarming at first — five other classes carry `@SubscribeEvent` handlers but no explicit
`register()` call. It is not a bug. Those classes are annotated with
`@Mod.EventBusSubscriber`, which Forge scans and auto-registers during mod construction:

```
capability/EternalEngineCapabilities.java:25   @Mod.EventBusSubscriber(modid = MOD_ID)
entity/EternalEngineEntities.java:17           @Mod.EventBusSubscriber(bus = Bus.MOD)
player/PlayerDataManager.java:20               @Mod.EventBusSubscriber(modid = MOD_ID)
player/PlayerActionDetector.java:36            @Mod.EventBusSubscriber(modid = MOD_ID)
client/EternalEngineClientRenderers.java:13    @Mod.EventBusSubscriber(bus = Bus.MOD, value = Dist.CLIENT)
```

Handler counts: `PlayerActionDetector` 5, `EternalEngineCapabilities` 4, `PlayerDataManager` 3,
`EternalEngineMod` 3, `EternalEngineEntities` 1, `SimulationEngine` 1
(`onServerTick`, running at END phase), `EternalEngineClientRenderers` 1.

*Note on method: an earlier pass over this repo read the two explicit `register()` calls as
evidence that `PlayerActionDetector.onLivingDeath` was dead code and that VIP death handling was
unwired. That conclusion was wrong — it did not account for `@Mod.EventBusSubscriber`
auto-registration. The chain is live.*

---

## 7. Data content

44 JSON files. The history packs are strongly region-weighted toward the Deccan / Telangana:

```
history/present_hyderabad_metro.json     history/deccan_rulers.json
history/present_telangana.json           history/deccan_events.json
history/present_kamareddy.json           history/indus.json
history/south_asia.json                  history/egypt.json
history/china.json                       history/mediterranean.json
history/mesopotamia.json                 history/americas.json
history/afro_islamic.json                history/events.json
history/events_world.json                history/borders.json
history/index.json
```

Plus `civilizations/foundation_civilizations.json`, `names/foundation_names.json`,
`names/skin_pool.json`, `religions/foundation_religions.json`,
`buildings/foundation_buildings.json`, `events/foundation_events.json`,
`templates/current_affairs_templates.json`, `data_source_config.json`.

`THREE_REQUIREMENTS.md` documents the population model in measured detail — `PopulationResolver`
log-interpolates the census series for a place and year, `ChunkPopulation` spreads that count over
the place's own chunks, and a seeded rounding conserves the total to within a tenth of a percent.
Worked numbers from that doc, for scale: Secunderabad 1948 at 142,174 people over 441,786 chunks is
0.322 people per chunk, one figure every 28.2 m; modern Hyderabad 2026 at 7,677,018 over the same
extent is 9.775 per chunk, one every 5.1 m. The network cost is claimed at **166 bytes for a
64-block view regardless of crowd size**, because the manifest describes the place rather than the
figures.

---

## 8. Terra3

`terra3/` is `net.buildtheearth.terraminusminus` at version `2.0.0-alpha`, `archivesBaseName =
'terraplusplus'`, with **OpenCubicChunks vendored into the same source tree**
(`io.github.opencubicchunks.cubicchunks` and `.cubicworldgen`, including the full mixin sets for
client, core, lighting, network, server, and storage). This is the fork that gives the world
infinite height, which the historical-terrain morphing depends on.

26 `*_APPLIED.md` documents record completed fixes: bridge system, bridge water generation, road /
building offset bugs, cold-start batching, full OSM+OHM coverage, six dimensions, strict hierarchy,
time-travel fixes, paradox rupture, universe forging.

`world_report/report.txt` is a passing self-test:

```
WORLD CHECK selftest-build: PASS — synthetic region written (24576 bytes, 4 chunks)
WORLD CHECK region:         PASS — r.0.0.mca: 4 chunks parsed, 0 failed
WORLD CHECK selftest-verify:PASS — all 4 chunks: surface block AND height exactly match ground truth
WORLD CHECK surface:        PASS — surface coverage 100% (1024 columns)
WORLD CHECK colors:         PASS — 0% surface blocks without palette color
WORLD CHECK render:         PASS — world_report/map_r.0.0.png (512x512, y 62..70)
WORLD CHECK era:            PASS — modern=256  1900s=0  victorian/roman=256  medieval=0 -> modern
WORLD CHECK timing:         PASS — processed in 0.1s
WORLD CHECK selftest-census:PASS — all 4 block counts == 256
```

**Repo hygiene:** `terra3/` root carries ~26 `javac.YYYYMMDD_HHMMSS.args` files, a
`.terraminusminus_backup_pre_prune.tar.gz`, and both repos carry `.gradle/` caches and packed git
objects. That is most of the 66 MB. These should be gitignored and removed.

---

## 9. What I could not verify

**Nothing in either project has been compiled.** This sandbox has OpenJDK 11
(`openjdk version "11" 2018-09-25`); the build requires Java 17, and ForgeGradle 6.x additionally
needs network access to Maven plus several gigabytes of heap for MCP remapping. So:

- Brace balance, imports, and wiring are verified by reading the source.
- **Type correctness is not verified.** A missing method, a wrong signature, or an incompatible
  Forge API call would not show up in any check above.
- The project's own `BUILD_VALIDATION.md` already anticipates this: *"full ForgeGradle/MCP
  remapping may exceed the sandbox memory limit... an environment limit rather than a confirmed
  Java source error."*

To close that gap: install a JDK 17 and run
`./gradlew clean compileJava --no-daemon --max-workers=1` in `eternal/`, then fix errors in small
groups.

---

## 10. Recommended next actions, in order

1. **Rewrite `README.md`.** It claims 31 existing classes are missing. This is the single highest
   risk item in the repo, because it misleads every future session.
2. **Update `ETERNAL_ENGINE_BUILD_HANDOFF.md`** to mark 12A and 12B done and leave 12C as the sole
   open item, so the next session doesn't rebuild `BlockUpdateQueue` or re-wire VIP death.
3. **Get one successful compile.** Install JDK 17 locally and run `compileJava`. Everything else is
   guesswork until a compiler has read this code.
4. **Phase 12C — Mindcraft transport.** Replace the three `ConcurrentHashMap`s in
   `MindcraftBridge.java` with a file/WebSocket/HTTP transport, preserving the existing
   proposal-validation contract (`MindcraftBridgeContract`).
5. **Close the three genuine content gaps:** `ReformationSystem`, `VolcanicSystem`, and the entire
   missing `narrative/` package.
6. **Clean the repos:** gitignore and delete the `javac.*.args` files, `.gradle/`, the backup
   tarball, and the 64 MB zip in `downloads/`.

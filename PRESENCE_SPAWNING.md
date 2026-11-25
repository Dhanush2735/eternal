# Presence NPCs — back to the original spawner, spawning the way Minecraft does

**2026-10-05.** Three points, three changes.

## 1. "I like the original NPC spawning system"

I got the pristine original out of `main-copy.zip` and restored it. The diff against what is on disk
now:

```
original 194 lines, now 358 lines, 187 lines byte-identical
```

Everything that made the original itself is untouched: the two systems (town occupancy, event scene),
the role lists, the tags, the per-year lifecycle, the original offsets, and the original caps —
`spawned < 4` for townsfolk and `sceneRoles(event.effect())` for the scene. The whole of my last-turn
design is gone:

| removed | why |
|---|---|
| `PresencePlacement.java` (ring, 75° view guard, 12 ordered bearings) | invented to dodge the player; vanilla's 24-block rule does it properly |
| staggered arrivals (2 per cycle) | vanilla spawns a group at once |
| keep radius 72 + straggler cleanup | vanilla's despawn rules replace it |
| `presenceKeepBlocks`, `presenceMaxSpawnPerCycle` config keys | concepts that no longer exist |
| the auto-announced news line | the news belongs to the NPCs, as in the original |

The result is a smaller diff to the original than what it replaced, and the parts I did change are
changes vanilla already makes.

## 2. "PopulationResolver is used only for population number"

The spawner no longer touches it. Also removed: the `SettlementDensity.npcCap` call that drove the
town cap — that was mine from an earlier pass, and it was the wrong idea.

| | before | now |
|---|---|---|
| `PopulationResolver` in the spawner | resolved population to size the crowd | **not called at all** |
| `SettlementDensity` in the spawner | `npcCap(142174, CITY)` = 21 people | **not called at all** |
| crowd size | a function of population | the original's cap of four, one per role |

`PopulationResolver` keeps its job: it answers *how many people live here in this year*, labelled with
how it knows — source, interpolation, model, floor. `/ee population at <lat> <lon> [year]`,
`/ee population explain` and the `/ee who` line all still use it. `SettlementDensity` survives as the
published curve (and its test), now labelled in its own javadoc as **information, not a spawn input** —
it answers "what would a crowd that represents this population look like", which is a useful thing to
know and not a thing that spawns anything.

Both class javadocs that used to describe population-driven spawning were corrected, along with the
two rows in `POPULATION_RESOLVER_APPLIED.md`.

## 3. "NPC spawning system should take reference from minecraft mobs spawning"

Two decisions in the spawner were measured from the player: where people appear, and when they leave.
Both now follow vanilla, in a new pure class `villager/PresenceSpawning.java` whose constants are the
contract:

| vanilla rule | value | source |
|---|---|---|
| nothing spawns within this many blocks of a player | **24** | [mob spawning mechanics](https://blog.bithole.dev/blogposts/mob-spawning/) |
| spawn distance, and instant despawn beyond it | **128** | same |
| no random despawn closer than this | **32** | same |
| random despawn chance per tick past 32 blocks | **1 in 800** | same |
| ticks with no player in range before that starts | **600** (30 s) | [despawn rules](https://guide.astroworldmc.com/how-mob-spawning-works-minecraft) |
| spawn position must be a loaded chunk with valid ground | — | ground beneath, two blocks of clear space, no liquid |

How this lands in the code, in the original's terms:

```java
// before: the anchor was the player
AABB box = player.getBoundingBox().inflate(SCAN_BLOCKS);
BlockPos base = player.blockPosition();
ensureTownNpcs(level, villagers, nearby, result, year, era, base);
    BlockPos pos = base.offset((i % 2) * 3 - 1, 0, (i / 2) * 3 + 2);

// now: the anchor is a vanilla spawn position 24..128 blocks out, on ground that can hold somebody
AABB box = player.getBoundingBox().inflate(spawnDistance() + 8.0d);
BlockPos anchor = spawnAnchor(level, player);
ensureTownNpcs(level, villagers, nearby, result, year, era, player);
    BlockPos pos = anchor.offset((i % 2) * 3 - 1, 0, (i / 2) * 3 + 2);
```

The original's arrangement of people around the anchor is preserved exactly — only the anchor moved.
`spawnAnchor` tries twelve candidate positions and returns null if none qualifies, in which case
nobody appears that cycle, which is what vanilla does too.

Despawn, in `vanillaDespawn`:

- past **128 blocks** from every player → discarded immediately;
- past **32 blocks**, after **600 ticks** away → one chance in **800** per tick, so people trickle
  away while you explore instead of vanishing in unison;
- inside 32 blocks → never;
- **event NPCs are exempt**, exactly the way a name-tagged mob is — the scene stays for its event.
  They are not exempt from the instant rule, so a scene no player can reach again is not kept forever
  (that leak was real: nothing else could ever find them).

Two consequences worth stating plainly:

- **The entity query had to grow to the spawn distance.** A person may now stand 128 blocks away; the
  old 48-block query would have missed them entirely, spawning a fresh set every cycle and never
  removing the old ones.
- **`/ee villager here` uses the same rule** (`PresenceLocalSpawner.spawnPlacement`). `/ee villager
  spawn-test` still spawns at your feet — it is explicitly the debug command.

Config, `[presence]` in `config/eternalengine-common.toml`:

| key | default | range | note |
|---|---|---|---|
| `presenceMinSpawnBlocks` | 24 | 8–96 | vanilla's number. Lower it to bring people closer |
| `presenceSpawnDistance` | 128 | 48–192 | vanilla's number; also the instant-despawn distance |

## Verification

`./verification/population/run.sh` — six suites, all pass. Suite 4 is new:

```
  no spawn within 24 blocks (vanilla)                  PASS
  spawn distance 128 blocks (vanilla)                  PASS
  random despawn only past 32 blocks (vanilla)         PASS
  one chance in 800 per tick (vanilla)                 PASS
  600 ticks away before it starts (vanilla)            PASS
scanned 720 candidate positions: closest 24.2, furthest 128.0
  nothing inside the 24-block bubble                   PASS
  nothing beyond the spawn distance                    PASS
mean squared distance 8495 vs 8480 expected
mean distance 87.98 vs 87.86 expected
  spread over the ring's area, not the rim             PASS
  distance distribution matches the closed form        PASS
fraction beyond the area midpoint: 0.503 (expect ~0.5)
  no pile-up at the inner edge                         PASS
  200 samples give 200 different places                PASS
  inside 32 blocks: never despawns                     PASS
  past 128 blocks: despawns at once                    PASS
  in the band, before 600 ticks: stays                 PASS
  in the band, after 600 ticks, unlucky roll: goes     PASS
  in the band, after 600 ticks, lucky roll: stays      PASS
random despawns per 800 cycles: 40 (expect 40)
  the rate is vanilla's 1-in-800 per tick              PASS
```

The constants are asserted, so a later edit cannot quietly stop being vanilla's behaviour — and one
test caught me writing the wrong expectation: the mean radius of a uniform ring is 87.86 (the closed
form ∫√), **not** √(midpoint of squares) = 92.1. The code was right; my check was wrong.

`static_audit.py` — **CLEAN, 66 checks**, including `!PopulationResolver.get()` and
`!SettlementDensity.` in the spawner (the population resolver must stay out of spawning), and
`!PresencePlacement` / `!TAG_HEARD` (the removed design must stay removed).
`parse_check.py` — all 15 modified files parse clean under javac.

Full output: `verification/population/last_run.txt`.

## One thing I did not touch, and you should decide

`carto/AmbientCrowdSoundManager` still scales its murmur and chatter thresholds with
`simulatedPopulation(player)` — the resolver's number. It is not NPC spawning, so I left it alone. But
it is the one other place where the resolver's number drives behaviour rather than being displayed.
Say the word and I will take it out; the ambient bed can keep its original thresholds instead.

## Verdict in simple words

The original spawner is back — same two systems, same roles, same caps, same tags. 187 lines of your
code are byte-identical to the original.

Two things about it changed, and both now work the way Minecraft mobs do: **people appear 24 to 128
blocks away on real ground** (never closer than 24, so never beside you, and never on water or inside a
wall), and **they leave the way mobs leave** — instantly past 128 blocks, and gradually past 32 after
half a minute, except the event scene, which stays put while its event is on, like a name-tagged mob.

The population number is no longer used to decide anything about spawning. It just answers "how many
people live here", for the commands and for you.

One open question: the crowd *sound* still gets louder with the resolver's number. Tell me if you want
that gone too.

# Hardware and settings guide (I10)

*Every number here names its source. Measured numbers come from this mod's own walk (steps 26 and
57); defaults are what ships in the config; the floor and the comfortable line are computed from
both; the last two limits are Minecraft's own.*

## Memory

The memory floor is a sum, not a feeling. It is **2,496 MB**, computed as:

| What | MB | Source |
|---|---|---|
| Minecraft itself, before any mod | 2,048 | game default |
| This mod's data registers | 128 | measured (step 26) |
| This mod's cache budget | 64 | game default (config) |
| Headroom for the rest of the pack and the JVM | 256 | computed |
| **Floor** | **2,496** | **computed** |
| Comfortable line (floor + 1,024) | **3,520** | computed |

So: **allocate about 4 GB.** Below 2,496 MB the game may start, but the caches and the registers
will be fighting; between 2,496 and 3,520 is workable; at 3,520 or more the caches stay inside their
64 MB budget and never need to trim.

## View distance

**8 chunks (128 blocks) is the recommended line.** It is not a coincidence: 128 blocks is exactly
this mod's numbers-only line (the step 57 distances table). At that distance:

- within **24 blocks**: people are drawn at full detail, with nameplates (presenceNameTagBlocks 24);
- at **64 blocks**: the draw thins, the crowd becomes figures;
- past **128 blocks** (8 chunks): people, vehicles and armies are counts only. The population engine
  still remembers them; nobody is deleted;
- at **160 blocks or your client's own view, whichever is nearer**: people are hidden. The client's
  view distance is the last word.

A short distance (4 chunks, 64 blocks) suits a laptop and keeps everything at full detail up close.
A long distance (12 chunks and up) pays for a lot of empty ground while this mod still hides people
at 160 blocks. Minecraft's own client draws at most 32 chunks; past that the number is a wish, not a
setting.

## Settings that cost (and what they cost)

| Setting | Shipped | Range | What it costs |
|---|---|---|---|
| presenceDrawnCrowd | true | on/off | figures stand around the living few; each costs a draw call but no tick |
| presenceDrawnFiguresMax | 400 a district | 25 to 600 | a client-side budget: figures are sent to your client and drawn |
| presenceCrowdCeiling | 40 in one place | 8 to 64 | bodies collide, tick physics and get sent to your client; measure with /debug before raising it |
| presenceCrowdConcentration | 40 | 0 to 400 | higher gathers the crowd and costs the client more to draw |
| presenceNameTagBlocks | 24 | 4 to 128 | nameplates are drawn only within this distance |
| maxTickBudgetMs | 10 | 1 to 50 | the measured walk spends well under 0.1 ms a lookup, so 10 ms is a whole crowd's headroom |

Two things are Minecraft's own and no setting changes them: a custom payload over **32,767 bytes**
never arrives (it is not slow, it is refused), and a client draws at most **32 chunks**.

## If the game stutters

Measure first, tune second (this mod's own walk is the offline version of that rule):

1. **Spark** first: a profiler that changes nothing. This is also how the live 30-minute walk gets
   measured (I2).
2. Then **ModernFix** and **FerriteCore** (memory). Both were checked against this mod's code paths
   and pack with confidence.
3. Then **Entity Culling** and **Embeddium** (the client), and **Canary** (the server). These change
   paths this mod uses, so each owes a live test before you ship the pack; the exact tests are in
   `PerformanceMods` (I9): stand in a packed square with Entity Culling on and off; walk from a crowd
   into a market with Embeddium; take a time jump with Canary and watch the rebuild wait reach "the
   wait is over".

Checked here is not checked live: the first three were checked against this mod's own code and pack
with confidence; the last three each name the live test they owe. No claim in this guide is a live
check that did not happen.

# Every alternative for a fuller crowd

> **Status update: A2 and A3 are built** (see `THE_SOLUTION.md`). Everything else in this list
> remains a choice: A1 is one constant, B3/B2 are two constants each, B1 is the project.

You asked for the whole list, not just the one I offered. Here it is — twelve routes, grouped by
what they actually give you. Everything marked **measured** was produced by running this mod's own
classes outside Minecraft (rig: `verification/population/alternatives/`, re-runnable, not part of the
jar). Everything else says so in plain words.

Picture: `crowd_alternatives_2d.html` — four panels, same 256-block view, same scale.

**Headline of the measuring.** The virtual tier is cheap enough to be a non-issue: **0.06 ms to
tick a population of 100,000 agents, 0.49 ms per player to scan for the ones near them, 3.2 MB of
memory for the lot.** The cost was never the problem. What is missing is entirely visual — and
there is one more thing nobody would guess, at the bottom of B3.

---

## A. More real, touchable people

| # | what it is | what you'd see | what it costs | what's missing | the catch |
|---|---|---|---|---|---|
| **A1** | Raise the ceiling — **done, then corrected**: the curve is now 10 / 20 / 40 and a knob caps it, but 35–40 people is a **group**, not a crowd: 0.014 people per m² against 0.5–1.0 for a real market. The ceiling is now only the number of people who *think*; the crowd is A5 | 35 people in Secunderabad, 40 in Hyderabad | The people are cheap now; the bodies are not. Measured: at the ceiling the crowd still spends less on thinking than 35 used to | Nothing further, unless you want more than 40 | Past about 40 the square clips rather than crowds, and the field's own NPC mods degrade at 40–50 |
| **A2** | Keep the body, drop the pathfinding: crowd members keep looking-at-player and idle glancing, lose the stroll and door-opening | People standing in the square who turn their heads and watch you pass | Pathfinding is the expensive part of a mob; removing it is the cheapest way to afford more people | A flag on the entity plus the goal set chosen at spawn — two files | They stop walking. A market is mostly people standing, but not entirely — keep a few wanderers |
| **A3** | Freeze the far ones: past ~32 blocks set NoAI, clear it when you return | No difference. A frozen person 40 blocks away looks exactly like a standing one | Nothing while you are not looking at them — which is what vanilla itself does: mobs outside simulation distance do not tick | ~15 lines in the spawner's existing 40-tick cycle, which already walks the nearby list | Their state is a snapshot while frozen |
| **A5** | Stand the crowd up as non-AI figures on the crowd plan — **built**: `CrowdFigures` + `CrowdDensity` | **377 figures + 35 living = 412 people** in Secunderabad, 1948, at 0.328 per m² — the square you cannot count | **Zero goal checks at every distance** (measured): the figures are `HumanNpcEntity` with `setNoAi(true)`, so no goals, no navigation, no path search. Thirty-two are stood up per two-second cycle and forgotten past the spawn distance | Bodies: 412 entities still collide and are still sent to the client, which is why `presenceDrawnFiguresMax` exists | Nothing — it is running. Tune the cap to your own client: `E` in F3 is the number to watch |
| **A6** | Raise the drawing ceiling — **researched 2026-10**, not built: a 3-piece crowd model (~1,000 figures), billboards (~3,200), or a batched renderer (thousands, ~10 draw calls). Client mods the player installs today (Embeddium, EntityCulling, ImmediatelyFast) already halve the cost of the 400 we ship | 1,000 to 5,000 figures in the same district | **Measured by others:** 1,000 cows 21 → 82 FPS batched; 5,000 player-shaped bodies 12 → ~92 FPS batched; 300 chickens = 319,200 draw calls/s unbatched | A model + entity type (small), a renderer (medium), a client batching project (large) | `CROWD_CEILING_RESEARCH.md`, picture `crowd_ceiling_2d.html` |
| **A4** | Raise the server's own knobs (`entityBroadcastRangePercentage`, simulation distance) | Entities stay visible and alive further out | Global — it treats the whole world the same, not just our crowd | Nothing. A settings change | Affects farms and every other mob. Not a crowd feature |

**Why A2 and A3 matter:** they make the crowd you already have cheaper, and cheaper is what pays
for A1. That is *real, touchable* people, and it is a day of work, not a project.

## B. People that look real but are not entities

| # | what it is | what you'd see | what it costs | what's missing | the catch |
|---|---|---|---|---|---|
| **B1** | Finish the virtual crowd — draw the proxy points as silhouettes (A5 made this optional rather than the only route to hundreds) | A thick crowd in the district, hundreds of figures, visible from any distance | **Measured:** 0.49 ms per player per tick at 100,000 agents; 0.06 ms to tick the whole population; 3.2 MB memory. Drawing 500 of them is nothing | **Everything visual.** Nothing draws the points today: `collectProxiesNearPlayer` has no caller and the client registers only the two NPC renderers | Silhouettes are not people — no names, no talking, no hitting. And the materialisation controller that would turn them real within 64 blocks has **no total cap**: it converts ten per tick until done, so walking in would spawn the whole crowd at once and end exactly where A1's ceiling is |
| **B2** | Feed it a district-shaped crowd. `populateMassive` scatters agents 10–360 blocks around you; put them in the district | The crowd is where you would look for it | None. One method | **Measured:** 100,000 scattered agents put only **29** in view. The same code with people in a district returns **500** — the cap | None. This is a precondition for B1, not an extra |
| **B3** | Re-band it to the walk-in range. The proxy band is 64–192 blocks; vanilla's own shell for mobs is 24–128 | **Measured, and this is the one nobody would guess.** As written, a crowd of 5,000 is full at 48–90 blocks, **half at 40, and completely dark at 30 and 24** — the exact spot where you stand when you arrive and look at it. With 24–128 it stays full all the way in | None. Two constants | Nothing | None — but without it, B1 gives you a crowd you can only see from far away. It is a condition, not a nicety |
| **B4** | Client-only impostors: the server sends a count and a seed, the client draws the shapes itself | The same crowd, no server entities at all | Zero server cost for the crowd | A small packet and a renderer | Pure scenery unless swapped for real people up close |

## C. No new people — just make the place read as busy

| # | what it is | what you'd see | what it costs | what's missing | the catch |
|---|---|---|---|---|---|
| **C1** | Props: stalls, carts, crates, fires, animals in pens — real blocks | A place that is used by people even when they are not in shot | Block placement only; the mod already queues blocks at 500/tick | A decoration pass. `buildings/BlockUpdateQueue` exists but nothing calls it for this | Scenery. It does not fill a square |
| **C2** | Event surges: the festival path already spawns a one-off cluster with roles — scale it for a few minutes | The square fills for a market day or festival, then empties | Temporary; the people despawn when it ends | A longer role list and a duration. The mechanism already exists | Temporary by design |
| **C3** | Sound: the ambient bed already rides the same 0–1 scale as the crowd | Nothing. It is heard | Already paid | Nothing — done | Sound does not make a picture |

## D. Not a crowd, but you should know it exists

| # | what it is | why it matters |
|---|---|---|
| **D1** | `VillagerVirtualizer` — a real dematerialise/rematerialise path that keeps individuals across distance | If anyone (or any doc) says the mod virtualises villagers, this is the code they mean. `virtualizeVillager` has **no callers at all**, so the store can never fill. It preserves people; it does not add any. It is not wired |

---

## What the four panels show

| panel | what it is |
|---|---|
| **A. Today** | 29 real people, one district r28, three blocks apart |
| **B. Ceiling raised** | 64 real people, same district, same code — A1, one constant |
| **C. Virtual, band as written** | 500 proxy points at a district of 5,000 — and the near half of the district is *empty*, because it sits inside the band's 64-block inner edge |
| **D. Virtual, vanilla's 24–128** | The same crowd, same player, the whole district shown. This is B3, and it costs two numbers |

## If I were choosing

1. **A2 and A3** — make the crowd cheaper. Real people, a day of work. *Done.*
2. **A1** — spend what A2/A3 saved on more real people. *Done, and not enough: it caps the people
   who think, not the people you see.*
3. **A5** — stand the crowd up as figures with no AI. *Built: this is the answer to "not sufficient as
   a crowd".*
4. **B2 and B3** — both small, both prerequisites for B1.
5. **B1** — the renderer, if figures ever cost too much; no longer the only route to hundreds.
6. **C** — decoration on top of whichever of the above you pick.

## Verdict in simple words

There are four kinds of answer. More real people, and you can afford more than I first said if you
stop them walking around so much. A virtual crowd — the cost is nothing, the work is all in the
drawing, and as the code stands today that crowd would be invisible exactly when you walk up to it.
Scenery and sound, which make a place feel used without adding anyone. And one piece of code that
already exists, that someone might tell you is doing this job, and that is not wired to anything.

Nothing here is impossible. Two of the twelve are one-line changes, four are small, one is a real
project, and the rest are decoration.

**What was actually chosen, and why:** A5 — non-AI figures on the crowd plan — because it is the only
route where the crowd is made of *the same people* as the living: one class, one flag different, no new
renderer, nothing new to ship. B1's renderer would be cheaper still and remains open. The full
comparison with costs is in `CROWD_DRAWN.md`; the picture is `crowd_figures_2d.html`.

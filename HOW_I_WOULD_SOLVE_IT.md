# How I would solve it

*The three requirements, answered one at a time: the number, the cost, the look. What is already
running, what I would build next, and what it costs — with the game's own numbers.*

> **Answered against the three requirements:** `THREE_REQUIREMENTS.md` + `three_requirements_2d.html`
> — the number (requirement 1), the cost three ways including the recipe's 166 bytes (requirement 2),
> and the look as spacing plus density (requirement 3), with the four steps and their state.
>
> **Built since this was written:** the population is now divided over the chunks of the place's own
> area, as asked — see **`CHUNK_CROWD.md`** and the picture `chunk_crowd_2d.html`. Secunderabad 1948
> is 142,174 people over 441,786 chunks: 0.322 in an average chunk, 13 in the market's chunk, 530 in
> a 64-block view. The route table below still stands for what comes after that.

---

## 1. The population number drives the crowd — *built and verified*

`PopulationResolver` answers one question — **how many people live here this year** — and that single
number drives both layers, and nothing else does:

| layer | how many | rule |
|---|---|---|
| **living** | 5 · 6 · 15 · 35 · 40 by size | `SettlementDensity.npcCap(population, rank)`, capped by `presenceCrowdCeiling` |
| **drawn** | 25 · 32 · 94 · 377 · 400 | `CrowdDensity.figuresFor(population, rank)` — the square root, clamped |

| place | population | living | drawn today | share of the place visible |
|---|---|---|---|---|
| Akhanpally Thanda, 1948 | 348 | 5 | 25 | 7.2% |
| Akanpalle, 2011 | 1,047 | 6 | 32 | 3.1% |
| Maheswaram mandal, 2011 | 8,795 | 15 | 94 | 1.1% |
| Secunderabad, 1948 | 142,174 | 35 | 377 | 0.27% |
| modern Hyderabad, 2026 | 7,677,018 | 40 | 400 | 0.005% |

The resolver supplies the number; it places nobody. Placement, tagging, caps and despawn are the
spawner's and `CrowdFigures`' own. That is verified, not asserted: suite 9 of `run.sh` (all passed)
and 22 wiring checks in `static_audit.py` (CLEAN, 157 checks).

## 2. Minimum cost — the budget, layer by layer

The cost of a crowd is in **three different pockets**, and they are not the same size.

| pocket | who pays | today's cost | can it be cut? |
|---|---|---|---|
| **thinking** | server, per tick | **0** for the crowd — the figures have no goals, no navigation, no path search; the living few are the only ones that think, and only inside 32 blocks | already at the floor |
| **per-figure drawing** | client, **every frame, per figure** | measured by others at **23.8 µs** a figure: 400 figures = **9.5 ms — 57% of a 60 FPS frame** | **yes — this is the wall, and this is where the work goes** |
| **collision + packets** | server, per entity | 412 entities collide, are tracked and are saved | yes, by the design below: 377 entities → **1** |

The second row is the whole story. 400 was never a simulation limit; it was **9.5 of the 16.7
milliseconds** a 60 FPS frame has. At 1,000 figures the arithmetic already says 143% of a frame —
impossible, as written.

### The lever: stop paying per figure, per frame

Our figures **never move and never animate**. That is not a detail — it is the entire opportunity.
A moving, animated mob must be rebuilt, posed and re-submitted every frame; a crowd that stands
still can be built **once** and drawn as recorded geometry:

| | today: 377 entities | the design: one recorded mesh |
|---|---|---|
| server entities | 377 (collide, tick, save, get sent) | **1** |
| client work per frame | 377 figures × per-figure cost | **one submission of cached geometry** |
| when it is built | every frame | **once**, when the crowd changes (year, size, district) |
| geometry | 377 separate models | 99,756 quads for a 2,771-figure city = 12.5 MB, **about 25 chunk sections of terrain** |
| evidence it works | — | Polarium (instanced player bodies): 5,000 in view, **12 → ~92 FPS**. Forge's own `FastTESR` documentation: *every instance coalesced into one draw call per frame* |

A normal render distance already draws **thousands** of chunk sections. Twenty-five of them, once,
is a rounding error — and it is the difference between a crowd of 400 and a crowd of 2,771.

### What that costs to build

The design needs no server work beyond what exists and no new simulation:

1. **The plan is already deterministic.** `CrowdDensity.plan(worldSeed, figures, radius)` returns the
   same coordinates from the same two numbers — that is why it was written that way. So the client
   does not need 377 positions sent to it: it needs `seed`, `figures`, `radius` and a year, and it
   rebuilds the identical crowd itself.
2. **One entity, or none.** Either a `CrowdDistrictEntity` (server keeps one row of NBT: seed,
   figures, radius, year) or a hidden block entity at the district centre — the seam is the same.
3. **A client renderer** that builds the crowd's boxes into one vertex buffer from the plan, using a
   small skin atlas (the nine vanilla skins already chosen client-side from the UUID), and draws it
   as one submission. Rebuilt only when the plan changes.
4. **The living stay exactly as they are** — real entities, names, speech, collisions, the near
   places nearest the player. They are the people you can talk to; the recorded crowd is what you
   look at.

The honest caveat: item 3 is client code I cannot compile in this workspace (`./gradlew build` is
yours), so it is a plan with numbers, not a shipped change. What *is* shipped still works exactly as
before if this is never built.

## 3. It looks like a genuine crowd in an area — *built, and it scales*

The rule that makes it read as a crowd is **density**, not head count: the drawn crowd keeps
**0.33 people per m²** and grows outward, so a village is a huddle and a city is a square you cannot
cross. With the recorded mesh, the count stops being capped at 400 and the population is shown
truthfully — here is every test place, from the game's own code (`ceiling_projection.txt`):

| place | population | living | drawn today | drawn, population-true | crowd radius | people per m² | geometry |
|---|---|---|---|---|---|---|---|
| Akhanpally Thanda, 1948 | 348 | 5 | 25 | **25** | 5.2 blocks | 0.300 | 112 KB |
| Akanpalle, 2011 | 1,047 | 6 | 32 | **32** | 5.8 blocks | 0.300 | 144 KB |
| Maheswaram mandal, 2011 | 8,795 | 15 | 94 | **94** | 10.0 blocks | 0.300 | 423 KB |
| Secunderabad, 1948 | 142,174 | 35 | 377 | **377** | 20.0 blocks | 0.300 | 1,696 KB |
| Secunderabad, 2026 | 217,910 | 36 | 400 | **467** | 22.3 blocks | 0.300 | 2,101 KB |
| Hyderabad, 1948 | 980,600 | 40 | 400 | **990** | 32.4 blocks | 0.300 | 4,455 KB |
| modern Hyderabad, 2026 | 7,677,018 | 40 | 400 | **2,771** | 40.0 blocks | 0.551 | 12,469 KB |

Two things fall out of that table, and both are the point:

- **The ceiling disappears where it mattered.** Secunderabad gains a third more people; Hyderabad
  1948 goes from 400 to 990; modern Hyderabad from 400 to 2,771.
- **Density stays real, or gets *more* real.** Every place sits at 0.33/m² until the district
  radius (40 blocks, the city setting) is full; modern Hyderabad then lands at **0.551/m² — inside
  the 0.5–1.0 band a real market runs at.** The densest case is the most realistic case.

Pictures, both drawn from coordinates the game's own code printed:
`crowd_figures_2d.html` (the drawn crowd at 400) and `crowd_ceiling_2d.html` (what moves the cap).

---

## The option space, before choosing

| # | route | ceiling | cost to build | what it gives up |
|---|---|---|---|---|
| **1** | **what ships today:** figures as entities, 8-piece model each | 400 | done | — |
| **2** | client performance mods the player installs (Embeddium, EntityCulling, ImmediatelyFast) + vanilla's Entity Distance slider | 400, at up to half the cost | **nothing** | nothing |
| **3** | a 3-piece crowd model (our own entity, no hat/jacket layers) | ~1,000 | a model + a texture | some silhouette detail |
| **4** | **one recorded mesh per district** — the design in §2 | **population-true (~2,800)** | a client renderer + one server entity type | the crowd is scenery: you walk *through* it, you talk to the living few |
| **5** | billboards: one quad a figure, all in one batch | ~3,200 | a renderer, figures flat on the camera | no 3D body at arm's length |
| **6** | bake figures into the terrain's geometry (Chisels-and-Bits route) | tens of thousands | a block + model pass | they stop being figures at all |
| **7** | a full instanced renderer (the Polarium route) | thousands, moving | a client project, months | nothing visually; most work |

**I choose 4, with 2 shipped as a note and 3 as the cheap fallback.** Route 4 wins on every axis that
matters here: it is the cheapest *per figure drawn* (built once, drawn once), it keeps the crowd in
the same coordinate system our tests already verify, it needs no shaders and no mod dependency, and
it makes the population number honest all the way to a real metropolis. Route 7 is strictly more
general, and strictly more work, for a crowd that does not need to move.

## What I would do, in order

1. **Nothing, today**: the crowd already works — 377 figures standing in Secunderabad at a real
   crowd's density, at zero goal checks. Write the client-mod note into the pack.
2. **Recorded mesh (§2)** — the real fix, and the only one that removes the 400 wall. First commit:
   `CrowdDistrictEntity` + `CrowdPlan` serialisation (seed, figures, radius, year). Second: the
   client renderer. Third: retire `CrowdFigures`' entity-per-figure path to a fallback flag.
3. **3-piece model** if the renderer is too much for now: ~2.7× the ceiling for a model file.
4. **LOD last, not first**: with static geometry there is nothing to level — the whole crowd is one
   draw. LOD only matters while figures are entities.

## Verdict in simple words

The number is already right: one population figure decides the crowd, and nothing else. The cost is
already as low as it can go *for thinking* — zero goal checks, measured. What is left is drawing, and
the bill there is brutal and simple: every figure drawn costs about **24 microseconds of every
frame**, so 400 figures is **57% of a 60-FPS frame**. That is the wall — not the simulation, not the
server, the drawing.

My answer is to stop drawing them one at a time, every frame. Our crowd never moves, so it never has
to be redrawn — build the whole square **once** into recorded geometry (a metropolis is 100,000
quads: about twenty-five chunks of terrain, drawn as one) and let the frame pay nothing per person.
Then the crowd tells the truth about the population: 377 in Secunderabad in 1948, 990 in Hyderabad,
2,771 in the city today — still packed at a real market's density, and still with only the handful
near you actually alive.

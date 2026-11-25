# Breaking the 400-figure ceiling

*Web research, October 2026. What the wall actually is, what other projects measured against it, and
which of their answers apply to us.*

---

## The wall, stated exactly

Our crowd stops at `presenceDrawnFiguresMax = 400` figures, and not because of the simulation. The
figures have no AI: **0 goal checks**, measured, at any distance. The wall is the **drawing**.

Every visible figure is drawn the way the game draws any entity: each piece of its model is a
separate draw call, every frame, for every figure. A player-shaped model is **8 pieces** (head, body,
arms, legs, hat, jacket — the Minecraft Forums arithmetic below uses a chicken at 8 and a horse at
39). So our 400 figures are:

> **400 × 8 = 3,200 draw calls per frame**, re-issued 60+ times a second.

That is the number to attack. And it is the *only* number — everything below is about taking it down.

## What other people measured

| who | what they drew | result | what it tells us |
|---|---|---|---|
| **Too Many Entities** (Modrinth, 2024) | 700 cows drawn as-is → 50 FPS; the same 700 with only 100 rendered → 195 FPS | **a 4× FPS change from drawing fewer bodies**, with every cow still alive | bodies are not the cost, bodies *drawn* are |
| **ImmediatelyFast** (hone.gg) | 1,000 cows: **21 FPS → 82 FPS** | batching the same draw calls | the calls, not the models, are the price |
| **Minecraft Forums** (wintercat, 2023) | 300 chickens: 2,700 → 133 FPS; worked out as **23.8 µs per chicken per frame**, 8 pieces each = **319,200 draw calls per second** | 1,000 → 21 FPS | the arithmetic is published, not estimated |
| **Polarium** (Fabric, 2026) | 5,000 player-shaped figures with skins, armour and name tags: **12 FPS → ~92 FPS**; 1,000: 33 → ~130 (and ~350 on a clean instance); 300: 55 → ~260 | thousands of figures in **~10 draw calls** | the wall is climbable, and this is the proof |
| **Post-Mortem/PuppetPlayers-class fake players** | player-shaped NPCs, packets only | no server entity at all | drawing was never the server's problem |
| **Vanilla "Entity Distance"** slider | one player's base: 200% → 50 FPS, **50% → 110 FPS**, 500% → 69, 100% → 98 | | the player can already halve the crowd and double the frame rate |
| **Bits and Chisels / Chisels and Bits** | entire builds out of 16³-per-block microblocks | "barely any performance hit"; greedy meshing + the Fabric renderer API | block-shaped geometry is visible thousands at a time *because it is built into chunk meshes, not drawn as entities* |

Two of those are answers, not observations. Everything else is confirmation of the same law.

## The answer: the wall is per-object draw calls, and both ways past it are about batching

**Route 1 — batch the figures (the Polarium route).** Upload each model's shape to the GPU *once*,
send only a small block per figure per frame (where it is, how its head/body/arms/legs are turned),
and let the GPU place every part by instancing. Polarium does exactly this for player-shaped bodies
and got **5,000 figures at ~92 FPS**. Extrapolated to us: our 3,200 draw calls collapse to roughly
**a handful**, and 400 stops being a cap at all — it becomes a setting.

**Route 2 — stop drawing figures as entities (the Chisels-and-Bits route).** Figures that are baked
into chunk geometry are drawn with the terrain, in a handful of draw calls per chunk, and Minecraft
is *very* good at that: microblock builders report entire structures of thousands of sub-blocks with
"barely any performance hit". The tunnel is cut by hand: a figure stops being an entity, so it is not
hit, not named, not pushed, and has no skin — it is a statue of one.

## The whole option space, ranked by cost to us

Listed before choosing, as always; each row says what it buys and what it costs **in this repo**.

| # | route | ceiling it gives | cost to build here | what you give up |
|---|---|---|---|---|
| 1 | **What we ship** (figures as entities, 8 pieces each) | 400 figures, ~3,200 draw calls | done | — |
| 2 | **Client performance mods** the player can install today: Embeddium (Sodium for Forge 1.20.1), EntityCulling, ImmediatelyFast, vanilla Entity Distance | same 400 figures, **+60–290% FPS** measured in the sources | **nothing** — a modpack note | nothing; they only draw faster |
| 3 | **A cheaper crowd model** — our own entity type / model with 3 pieces (no hat, no swinging arms, no jacket layer) | ~1,000 figures for the same draw calls | a model + a texture; a new entity type beside `HumanNpcEntity` | some of the silhouette's detail |
| 4 | **Billboards** — one quad per figure, drawn in one batch like a particle (`RenderType` batched, or display entities) | ~3,200 figures for the same draw calls | a renderer for one entity type; the figure is flat on the camera axis | no 3D body; reads as a crowd at distance, not at arm's length |
| 5 | **A batched/instanced crowd renderer** — the Polarium route, ours, for `HumanNpcEntity` | **thousands**, ~10 draw calls, full bodies and skins | client shader + renderer work, a project; Forge 1.20.1 means porting the idea, not the mod | nothing visually; a real project |
| 6 | **Bake figures into the world** — block-shaped "crowd" blocks sharing the chunk mesh, Chisels-and-Bits style | tens of thousands, essentially free to draw | a block + a model pass; we already have a `BlockUpdateQueue` at 500 blocks/tick with no caller for this | they are not people any more: no hitbox, no name, no beard |
| 7 | **Server-side limits**: entity broadcast range %, `entity-tracking-range-entity`, spawn limits | cheaper server, not more figures | config, in `server.properties` / Paper-style configs | none for a modded setup of our shape |
| 8 | **Server software features**: Purpur mob-AI toggles, Paper's max-entity-collisions, per-player mob-spawns, villager lobotomise | more *thinking* entities, not more *drawn* ones | different server jar | not our problem: our figures do not think |

## What I would actually do, in order

1. **Today, no code:** ship the client-mod recommendation with the crowd. Embeddium, EntityCulling,
   ImmediatelyFast — the sources measure a doubled frame rate on entity-heavy scenes for that stack
   alone, and vanilla's Entity Distance slider is already in the video settings.
2. **Small:** a 3-piece crowd model (route 3). ~2.7× the figures for the same calls, and it is only a
   model file plus one entity type. **This is the cheapest real ceiling raise we can ship ourselves.**
3. **Medium:** billboards (route 4) when figures start being seen from far away, which is most of the
   time — the eye cannot tell a flat figure from a body at forty blocks, and it buys 8×.
4. **The project:** a batched renderer (route 5) only if a place must show *thousands*. That is the
   Polarium route: months, client-side, and the measured result is already on the table for whoever
   wants it.

## What this research does **not** claim

- No mod or server is shown here drawing **thousands of thinking, pathfinding people**. Every large
  crowd in the sources is either not thinking or not drawn one-by-one. Our two-layer design (a few
  live, many drawn) is still the pattern everyone converges on.
- The 2.7× and 8× above are **arithmetic on the measured draw calls per figure** (8 pieces ÷ 3, and
  ÷ 1), not measurements of our own build. The diagram says so on its face; so does this file.
- Polarium is a Fabric mod for 26.2. Its *technique* is portable; its code is not a drop-in for our
  Forge 1.20.1 project, and no equivalent Forge 1.20.1 mod was found.

## Verdict in simple words

The 400 was never a limit on how many people can exist in the square — it is a limit on how many
**conversations with the graphics card** we do per frame. Every visible figure costs about eight of
them, every frame, so 400 figures is 3,200 per frame and that is where the frame rate goes.

Two ways past it, both proven by other people: **batch the figures** (send the shape once, let the
GPU place thousands — measured at 5,000 player-shaped bodies and 92 FPS), or **stop drawing them as
figures at all** (bake them into the world's geometry — microblock builders do thousands with barely
a hit). Before either, there is a free one: the client-side mods players can install today make the
same 400 figures cost half as much, and vanilla's own entity-distance slider doubles what an
entitled machine can show.

So the order is: tell players about the mods, then give the crowd a 3-piece model (~1,000 figures for
free), then billboards (~3,200), and only build a batching renderer if a square ever needs more than
that. The wall is real, it is measured, and it has been climbed before — it just costs work, and the
work is in the drawing, not in the crowd.

# A group is not a crowd

*What the complaint was, what a crowd actually is in Minecraft terms, and what is now running.*

> **Superseded in part:** the crowd is no longer stood up in one district disc. It is divided over the
> chunks of the place's whole area — see **`CHUNK_CROWD.md`** and `chunk_crowd_2d.html`. The figure
> count, the AI-free entity and the 24-block bubble all still hold.

---

## The complaint, and why it was right

I shipped a "crowd" of 35 to 40 people and called it done. It is not a crowd. In the same 28-block
district:

| measure | 35 living people | what a real crowd is |
|---|---|---|
| people per m² | **0.014** | 0.5 – 1.0 at a market |
| how far short | — | **35×** |
| can you count them? | one by one, in a few seconds | no |

No cap closes a gap that size. A city of 142,174 people showing 35 of them is 0.02% visible, and a
server that ticked every person a city shows would fall over long before the square looked busy. So
the count a server can afford was never the crowd, and calling it one was the mistake.

## What is running now

Two layers, one population number, and only one of them thinks.

| layer | how many | what they cost | what you see |
|---|---|---|---|
| **living** | the curve: 5 · 6 · 15 · 35 · 40 by size | goals, collisions, nameplates | the people you can talk to |
| **figures** | √population, clamped 25 … 400 | **no AI at all** | the crowd standing around them |

| place | population | living | figures | crowd radius | people/m² with figures | people/m² living only |
|---|---|---|---|---|---|---|
| Akhanpally Thanda, 1948 | 348 | 5 | 25 | 5.2 blocks | 0.360 | 0.002 |
| Akanpalle, 2011 | 1,047 | 6 | 32 | 5.8 blocks | 0.356 | 0.002 |
| Maheswaram mandal, 2011 | 8,795 | 15 | 94 | 10.0 blocks | 0.348 | 0.006 |
| Secunderabad, 1948 | 142,174 | 35 | **377** | 20.0 blocks | **0.328** | 0.014 |
| Hyderabad, 1948 | 980,600 | 40 | 400 | 20.6 blocks | 0.330 | 0.016 |

The drawn crowd sits at a constant **0.33 people per square metre**, so a bigger place is a *wider*
crowd, not a tighter one: a village is a huddle five blocks across, a city is a square twenty blocks
across that you cannot count. Every panel in `crowd_figures_2d.html` is drawn from these coordinates.

---

## The whole option space, before choosing

Every route to "hundreds of people on screen", with what it costs and what it needs. This is the
list I chose from, not a list written after the choice.

| # | route | what you see | ticks? | entities on the server | new code it needs | verdict |
|---|---|---|---|---|---|---|
| 1 | raise the living cap and spawn hundreds of AI NPCs | a crowd | yes — ~14,000 goal checks/s at 400 | 400 full mobs | none | **rejected on measurement**: 35 already costs 1,240 goal checks/s; the field's own numbers (MineColonies: 200 NPCs → 20→12 TPS) say this ends at a slideshow |
| 2 | **non-AI figures from the mod's own NPC class** | the mod's own people, in their era's clothes, standing | **no goals, no navigation, no path search** | 377 | none — one flag on an existing class | **chosen** |
| 3 | display-entity rigs (`item_display` / `block_display`) | a figure only if built from ~6 displays | no tick at all | ~2,260 for 377 figures | none, but the rig data | kept as an alternative; more entities than people, and it reads worse at distance |
| 4 | packet-only fake players (`ClientboundPlayerInfoUpdatePacket` + spawn packets) | a perfect player model | nothing server-side | **0** | packet code + spawn/despawn management | kept as an alternative; nothing on the server means nothing to collide with, nothing saved, and skin handling is yours |
| 5 | one custom entity whose renderer draws N figures | whatever the renderer draws | 1 entity | **1** | a renderer, a model, and the client sync | the cheapest of all, and the largest new-code surface; no renderer ships in this repo to build on |
| 6 | keep 35 and call it a gathering | a group | yes | 35 | none | **rejected**: it does not answer the complaint |

Route 2 wins because it is the only one that is *the same people*: a figure is a `HumanNpcEntity` —
the class the living already are — with `setNoAi(true)`. Same model, same skins, same names data, one
flag different. Nothing new to render, nothing new to ship, and no second appearance to keep in step
with the first.

## What a figure is, exactly

- **No goals.** `setNoAi(true)` on the entity: no goal selector work, no navigation, no A* — the three
  costs that made the AI bill. It also stops them falling, so the ground is checked before they stand.
- **Silent, invulnerable, persistent, nameless.** Nothing rattles a crowd of 400; nothing sends 400
  nameplates at your screen; a figure does not despawn while the place exists.
- **One place each.** Figures stand on `CrowdDensity.plan()` — one per grid cell at the crowd's own
  density, deterministic from the world seed, so the same square fills the same way every visit.
- **The living take the near places.** `CrowdDensity.nearestSlots()` puts the thinking people on the
  side you arrive from and the drawn crowd closes behind them.
- **Never beside the player.** A figure is refused inside any player's 24-block bubble, in an unloaded
  chunk, or on ground that cannot hold somebody — the same three rules the living obey.
- **Not the living.** A figure carries `eternalengine.crowdFigure` and *not* the presence tag, so it
  is never counted in the living budget, never settled, never named, and never enters the villager
  books.
- **Thirty-two at a time.** A crowd of 400 is stood up 32 figures per two-second cycle — about half a
  minute, at a distance where nobody watches it arrive — instead of a 400-entity spike in one tick.
- **Shoved, not scattered.** A player walking through the crowd pushes bodies; any figure more than a
  block off its place is put back on it. A crowd that drifts apart over an evening is not a crowd.
- **Forgotten, not accumulated.** Vanilla's own rule: past `spawnDistance + districtRadius + 16` from
  every player, the figures go, exactly as a mob 128 blocks away goes. A world does not keep every
  crowd anyone ever visited.

## The bill

| | 35 living (what we shipped) | 377 figures + 35 living (now) |
|---|---|---|
| goal checks per tick, player at 16 blocks | 2,640 | 2,640 — the figures add **0** |
| goal checks per tick, player at 32 blocks | 480 | 480 — the figures add **0** |
| goal checks per tick, player at 64 blocks | 0 | 0 — the figures add **0** |
| spawn cost | knots every cycle | ≤ 32 figures per cycle, then nothing |
| bodies | 35 | 412 |

The saving is in the AI, not the bodies: 412 bodies still collide and are still sent to your client.
That is why the figure count is a knob and not a constant.

## Knobs

| key | default | what it does |
|---|---|---|
| `presenceDrawnCrowd` | **true** | false goes back to the living people being all anyone sees |
| `presenceDrawnFiguresMax` | **400** | 25–600; the most figures drawn in one district — a client-side budget |
| `presenceCrowdCeiling` | 40 | still caps the people who think; it no longer has anything to do with the crowd you see |

### Why 400 and not more

400 is a **drawing** limit, not a simulation one: the figures have no AI, so 400 costs zero goal
checks. What it costs is draw calls — a player-shaped model is 8 pieces, each its own draw call,
every frame: 3,200 per frame at the cap. Other people have measured this exact wall and climbed it:
batching the same figures took 5,000 player-shaped bodies from 12 FPS to ~92, and baking geometry
into the world's chunks makes thousands of sub-blocks nearly free. The whole option space, with
sources, is in `CROWD_CEILING_RESEARCH.md`; the picture is `crowd_ceiling_2d.html`.

## How it was checked

| check | result |
|---|---|
| `run.sh` (9 suites) | **all passed** — suite 9 is the crowd itself: √pop and the clamps, rank floors, radius, density, no holes and no clumps, first batch fills the middle, second batch takes new places, the living nearest the player, the drawing cap |
| `static_audit.py` | **CLEAN, 157 checks** — 22 of them new: figures never think, never counted as the living, never in the villager books, never capped by the thinking cap, always on the same plan as the living, always swept when stale, always refused inside the player bubble |
| bite test | each new guard was broken on purpose and each one caught it: `setNoAi(false)`, density 0.9, the spawner's crowd call pointed elsewhere, a figure tagged as a living person |
| `parse_check.py` | 0 syntax failures, all 22 modified files parse |
| the diagram | `crowd_figures_2d.html`, drawn from `crowd_plan.txt` — 928 real coordinates from `CrowdDensity.plan()` |

In-game, the numbers to look at are `/debug`'s `tick > entity` (the figures must add nothing on top of
the living) and `E` in the F3 screen (that is where 400 non-ticking entities show up — they are bodies,
and bodies are what the client draws).

## Verdict in simple words

A group of forty was never a crowd, and the ceiling was never going to be one — you cannot make
0.014 people per square metre look busy by tuning a number. So the crowd is no longer simulated. The
few people who think are the ones you talk to; the two hundred to four hundred people who make the
square look like a square are *drawn* there: the same people, in the same clothes, with their AI
switched off, so they cost no thinking at all, only a body.

Secunderabad in 1948 now shows 377 figures plus its 35 living — 412 people at 0.33 per square metre,
against 0.014 before: twelve times the people and twenty-three times the density. The thinking bill
did not move at all — zero goal checks were added, because nothing new in the square has a goal to
check.

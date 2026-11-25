# How I would solve it — and what is now built

Three requirements, one system each. **Requirements 1 and 3 were built and verified earlier; the
middle one — minimum cost — was built this turn.** Nothing below is a proposal.

Picture: `the_solution_2d.html` — the three requirements, and the cost curve measured over this
crowd.

Verification after this turn's work: **8 suites, all passed** (145 PASS lines); `static_audit.py`
**CLEAN, 128 checks**; `parse_check.py` **20 files, 0 syntax failures**.

---

## 1. The population number comes from the resolver — *built earlier*

One call, one number, used once:

```java
PopulationResolver.Result population = PopulationResolver.get().resolve(
        result.site().map(h -> h.site().id()).orElse(null),
        result.lat(), result.lon(), year, result.rank());
int budget = SettlementDensity.npcCap(population.population(), result.rank());
```

| place, year | resolved population | people placed |
|---|---|---|
| Akhanpally Thanda, 1948 | 348 | 5 |
| Akanpalle, 2011 | 1,047 | 6 |
| Maheshwaram mandal | 8,795 | 15 |
| Secunderabad, 1948 | 142,174 | 35 |
| Hyderabad, 1948 | 980,600 | 40 |

Nothing else is derived from the figure: the spawner does its own placing, tagging, capping and
despawning, under Minecraft's own rules. Suite 5 covers the curve, suite 1 the resolver.

## 2. Minimum cost — *built this turn*

Two saving mechanisms, both in the code now:

**Settled (A2).** A district crowd member keeps the two cheap goals — look at the player, glance
around — and gives up the one goal of five that runs an A\* path search. It also stops mid-step
rather than finishing the walk, so the square reads as people standing there rather than people
caught leaving. Only people this spawner placed are ever settled; VIPs, caravans, bandits, plague
doctors and event scenes keep the behaviour they were written with.

**Frozen (A3).** Past `presenceSettleBlocks` (32) from every player, vanilla's own `NoAI` goes on:
goal selection, navigation and sensing all stop. It comes back the moment a player is inside again.
32 is deliberately vanilla's *no-random-despawn* radius — you pay for the thinking of exactly the
people the game treats as in play — and it is the same trick vanilla already plays at the
simulation-distance boundary. Anyone whose ground has been dug out is woken by the spawner's
existing ground check, because NoAI also stops a mob falling.

Both run inside the cycle that already walks the nearby list three times a cycle, so the whole
mechanism costs one distance check per person per 40 ticks.

### Measured over this crowd

| you stand | before: 35 people | A2+A3: 35 people | A2+A3: 40 people (the ceiling) |
|---|---|---|---|
| 64 blocks | 3,500 | 0 | 0 |
| 48 blocks | 3,500 | 160 | 160 |
| 32 blocks | 3,500 | 480 | 800 |
| 24 blocks | 3,500 | 1,360 | 1,680 |
| 16 blocks | 3,500 | 2,640 | 3,040 |
| 8 blocks | 3,500 | 2,800 | 3,200 |

Figures are goal-checks per second — how often the game asks one of a person's goals whether it
wants to run. A count of work, not milliseconds; the milliseconds need your machine (§ below).

- Walking in, the crowd costs **65% less** on average than it did, and **zero** beyond 32 blocks.
- **Even at the ceiling of 40, the crowd spends less on thinking than 35 did before this turn's work** — 1480 goal-checks/s across an approach against the old 3,500. Be precise about what that is: goal checks, the thing that was measured. The *bodies* — physics, collisions, being tracked and sent to clients — are untouched by A2 and A3 and scale with the head count, so five more people is five more bodies. What the work bought is that the extra people are cheap.
- Past 32 blocks from everyone, the crowd costs **nothing at all**.

### Evidence this is the right lever

Not my theory: a server measured one mob species (the cod) at **50.1% of CPU time, 41% of it the
goal selector**, and gained 5 TPS by switching that AI off. A performance mod that only *throttles*
wander pathfinding took a modded pack from **38 → 30 ms** average and **95 → 45 ms** worst. Vanilla
itself does not tick mobs outside the simulation distance at all.

## 3. It looks like a crowd in an area — *built earlier, the ceiling was not enough*

> **Correction, after shipping it:** an area and a raised ceiling were still not a crowd. Thirty-five
> people in this district is 0.014 people per m², where a market runs 0.5–1.0. Section 4 below is what
> actually fixed it: the crowd is now *drawn* — hundreds of figures with their AI off — and the curve
> in section 1 is only the number of people who **think**.

- One **district** (radius 28) at the settlement's own site centre, or a grid-snapped point nearby.
  The whole crowd stands inside it — **354× the old local density**, nearest neighbour 3 blocks
  instead of 35.
- **Knots** of four on the spawner's original offsets, three blocks apart inside a knot, five blocks
  of clearance between knots so nobody doubles up on a block.
- **You walk into it.** Nothing spawns inside your 24-block bubble — vanilla's rule, untouched — so
  the square is already full when you arrive, and the people already there stay.

---

## 4. The crowd is drawn, not simulated — *built this turn*

The people who think are capped at 5 · 6 · 15 · 35 · 40 by size. The people you *see* are √population
clamped 25 … 400, stood up as `HumanNpcEntity` figures with `setNoAi(true)`: the same people, in the
same era's clothes, with no goals, no navigation and no path search — the three costs that made the
AI bill. Secunderabad, 1948 goes from 35 people to **377 figures + 35 living = 412**, at 0.33 per
square metre instead of 0.014.

- One plan (`CrowdDensity.plan`) is shared by both layers, so a figure and a living person never want
  the same place; the living take the places nearest the player.
- Figures are stood up 32 per two-second cycle, refused inside any player's 24-block bubble, put back
  on their place if shoved, and forgotten when no player is near — vanilla's own despawn distance,
  scaled by the district.
- The measured cost of the crowd: **zero goal checks**, at every distance. The bodies still collide.

The alternatives were measured before choosing and are listed in `CROWD_DRAWN.md`: display-entity
rigs (no tick, ~6 entities per figure), packet-only fake players (0 server entities, nothing saved),
and one custom entity with a renderer (cheapest of all, largest new-code surface). The full detail,
the tables and the option space are in that file; the picture is `crowd_figures_2d.html`.

### Still open

The figure count is a client-side budget (`presenceDrawnFiguresMax`, default 400). The measured
number in-game is `E` in F3 and `tick > entity` in `/debug`: how many bodies your machine happily
draws, on your hardware, is the one number this repo cannot answer.

The 400 itself is a **drawing** limit, not a simulation one — 3,200 draw calls per frame at the cap,
because a figure is drawn as 8 separate pieces. What it takes to raise it is researched and measured
elsewhere: `CROWD_CEILING_RESEARCH.md` (picture: `crowd_ceiling_2d.html`) — client performance mods
for free, a 3-piece crowd model for ~2.7×, billboards for ~8×, and a batched renderer for thousands.

## Knobs

| key | default | what it does |
|---|---|---|
| `presenceDistrictRadius` | 28 | the area a crowd occupies; smaller means denser-looking for the same count |
| `presenceSettleBlocks` | 32 | past this, a crowd member stops thinking; 8–96, a large value disables it |
| `presenceSettleGoals` | true | false restores vanilla wandering, at roughly twice the cost |
| `presenceCrowdCeiling` | 40 | the most people in one place **who think**; caps the density curve, 8–64 |
| `presenceDrawnCrowd` | true | draw the crowd: figures around the living, with no AI |
| `presenceDrawnFiguresMax` | 400 | the most figures drawn in one district, 25–600 — a client-side budget |
| `presenceMinSpawnBlocks` / `presenceSpawnDistance` | 24 / 128 | vanilla's own spawn rules, kept |

## How to check the real cost

`/debug start`, walk from 64 blocks out into a district, `/debug stop`, read `tick > entity` in the
report — once with the change and once without. That is the number that matters, and it is on your
machine, not in this document.

Note: NoAI is saved with the entity, so a world saved while a crowd is frozen loads frozen; the
spawner re-evaluates every cycle, so it wakes on the same tick a player comes close.

## Verdict in simple words

The number was already right: the resolver's figure decides how many people appear, and nothing
else. The place was already right: they gather in one district, you walk into a square that is
already full, and they stay. What was wrong was the bill — every person was deciding what to do
twenty times a second, even 60 blocks away with your back turned.

Now they decide only when you can see them, and in the square they stand and watch instead of pacing
about. Same people, same rules, same crowd — and the doubled crowd spends less on thinking than today's does. Beyond 32 blocks, it costs nothing at all. The bodies are still bodies: the saving is real and it is in the AI, not in the physics.

Hundreds of people are now drawn rather than spawned: 377 figures stand in Secunderabad around its
35 living, at a real crowd's density, and none of them is thinking. The square is full when you walk
into it, and the bill did not move.

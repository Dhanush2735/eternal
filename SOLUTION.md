# How I would solve it — the number, the cost, and the crowd you can see

Three requirements, one chain. Everything here is computed from this turn's `SolutionProbe`, which runs
the real `PopulationResolver` and the real `ChunkPopulation` — the same code the mod runs — and writes
`solution_chain.tsv`. The drawing is `solution_2d.html`; the tables are `solution_tables.md`.

The chain has four links and only the last one is new work:

```
place + year -> PopulationResolver -> the number            (built, tested)
             -> radiusKm + ChunkPopulation -> people a chunk (built, tested)
             -> Manifest -> 166 bytes, change-only           (built, tested)
             -> the client builds the crowd as one mesh      (NOT WRITTEN)
```

## Requirement 1 — PopulationResolver supplies the number, for any year

**How.** Nothing else supplies a population number. `resolve(place, year, rank)` is a lookup in a
loaded pack, cached per place-year, and it answers in tiers that say where the answer came from: the
census record itself (SOURCED), the resolver's own interpolation between records (INTERPOLATED), or a
model built from calibration constants (MODELLED). Every figure the player sees is multiplied out of
that one number — the number is never inflated to make the crowd look better, and the crowd is never
counted to make the number look right.

**What the number is actually worth, measured.** I hid each of the project's census records in turn and
asked every candidate rule to rebuild it from the records that remained (leave-one-out; 13 of the 16
records are testable). The resolver scores **17.3% mean error / 5.7% median** on the records it answers
from its own region's series — better than arithmetic (24.1%), absolute-rate (27.4%), carry-forward
(30.2%) and carry-backward (80.9%) on the same rows. Its between-records rule is the best rule there is
for this data. That is the answer to "which formula": the project already has the right one.

**Where it is not good, and the two fixes.** Overall it scores 215.0% mean because of two refusals
inside `interpolate`, both measured:

| defect | what the engine does | what it should do | measured |
|---|---|---|---|
| a bracket wider than `maxInterpolationGapYears` (60) | refuses the two records it has and drops to a gravity model | log-lerp between them anyway | Secunderabad 2001: **+2,588.6% -> -1.2%**, and no other tested row changes |
| the year is outside the records | carries the nearest record flat for up to 60 years | extend at the nearest pair's own rate, still capped at 60 years | ends improve 21.3% -> 16.1% mean; **Hyderabad 1901 gets worse, +4.1% -> -73.8%** |

The first is free and has no regression anywhere in the pack (its widest bracket is exactly 60 years, so
today's numbers do not move). The second is a trade, and I would take it only with that one row known.

**One more measured gap, not recommended.** Atraf-i-Balda's four gazetteer records (355,787 in 1881 to
612,493 in 1941, over 847 settlements) and Hyderabad State's one are read by nothing: `findRegion` only
considers `settlement` regions. 4,701 place rows sit inside its 90 km circle; of 118 sampled, **118 were
answered MODELLED and 0 from those records**. Reading them would score 2.3% across those four records,
but that is a district total with no per-village census to check it against, so it stays a finding.

**For any year** is the part that already works: 2026 for 15,954 places, 1800, 1900, 1948, 2500 — the
resolver answers every one, with the tier named. Row three of the panel shows what a 2026 answer looks
like: Hyderabad 7,677,018 (interpolated) and Secunderabad 217,910 (interpolated).

## Requirement 2 — minimum cost

The number costs nothing to have: a lookup, cached. The cost is the drawing, and it is only paid where a
player can see:

- **Nothing ticks.** A drawn figure has no goals, no navigation, no physics — the server does no work
  per figure. The living NPCs (5–40, with names, skins and answers) are untouched, and the drawn crowd
  never spawns an entity at all.
- **The transport is a recipe, not a crowd.** `Manifest.encode()` is 21 bytes of header plus 3 bytes a
  chunk: **166 bytes** for the 45-chunk view, sent only when the view changes. Four
  hundred spawned entities would be kilobytes, resent every time a player walks back.
- **The whole area exists as arithmetic.** Only chunks within 64 blocks of a player are stood
  up at all; the rest of a 400,000-chunk place costs a number in a map.
- **The frame is the only real cost, and it is measured**: the Hyderabad view of 3,465 figures is
  82.5 ms a frame if it were ticked entities (23.8 µs a figure) — five frames a second. As recorded
  geometry it is 124,740 quads, 15,592 KB, 31 chunk sections, **one mesh and one draw call**,
  built once and reused. That is the difference between feasible and not.
- **When the client cannot afford the view**, `sampledView` thins to the 400-figure budget by
  largest-remainder: every chunk keeps its relative density, so the crowd thins everywhere at once
  instead of losing its far half.

| what | cost | how measured |
|---|---|---|
| the population number | 0 per tick | a lookup in a loaded pack, cached per place-year |
| server ticks spent on the crowd | 0 | the drawn figures have no AI, no goals, no navigation; the living NPCs are untouched |
| the transport | 166 bytes | `Manifest.encode()` = 21 bytes + 3 a chunk, 45 chunks, sent only when the view changes |
| the same crowd as spawned entities | 82.5 ms a frame | 3,465 figures x 23.8 us |
| the same crowd as recorded geometry | one draw call | 124,740 quads, 15,593 KB, 31 chunk sections, built once |
| what the client draws when it cannot afford the view | the 400 budget | `sampledView` keeps every chunk's relative density and thins everywhere at once |

## Requirement 3 — so that it can look like a genuine population in an area

This is a **density** question, not a count question, and the probe measures it. The average density of
the real population over the real extent is hopeless as a visual:

| place, 2026 | people | extent | average density | in a 64-block view, spread evenly |
|---|---|---|---|---|
| Hyderabad | 7,677,018 | 8 km | 0.0382 a m² | 439 figures |
| Secunderabad | 217,910 | 6 km | 0.0019 a m² | 23 figures — one person every 457 m² |

One person every 457 m² is not a crowd and never will be. People are not spread over a
district; they are gathered where they live. That gathering is what `concentration` models — a chunk at
the heart carries `1 + c·exp(−(d/100 m)²)` times what a chunk at the edge does, normalised so the
population is still exactly the population. The measurement:

| what the player sees | Secunderabad, conc 40 (today) | Secunderabad, conc 200 | Hyderabad, conc 40 |
|---|---|---|---|
| figures in view | 751 | 3,328 | 3,465 |
| one person every, within 32 blocks | 14.3 m² | 3.4 m² | 3.3 m² |
| heart chunk | 20 a chunk | 77 (the cap) | 77 (the cap) |

0.30 people a m² within 32 blocks is a real crowd — a market square, shoulder to shoulder. That is the
number that decides whether it *looks* like a population, and it is reachable from the resolver's own
number with one setting.

Three things the probe settles that opinion could not:

- **the view saturates at 3,465 figures** — 45 chunks × 77 a chunk. Both cities
  reach it; a bigger population cannot put more people in front of one player, and does not need to.
  Beyond that, the crowd is the district seen from above, which is thin on purpose.
- **a village stays a village.** Akanpalle 2026 (1,047 people, 3 km): 16 figures at
  concentration 40, 150 at 800. Nobody has to be lied to for a hamlet to look like a hamlet.
- **the extent matters as much as the number.** Maheswaram mandal is a town with no settlement region,
  so it gets the 10 km default radius: even at concentration 800 its heart chunk holds 46
  people, because 75,525 people spread over 314 km² cannot gather. A wrong extent dilutes every figure —
  the fix is a rank-based extent, not a bigger number.

What I checked and threw away: **placing the crowd at the packs' named localities.** Hyderabad's 8 km
radius contains 530 named localities, and they occupy 526 distinct 16 m chunks — one each, because they
are the district's settlements and not its buildings. They carry no 16-metre structure, so they cannot
place a crowd better than the model already does. Measured, not assumed.

| place, 2026 | the resolver's number | tier | extent | people a m2 | figures in view (conc 40) | one person every | at the cap? | frame as entities |
|---|---|---|---|---|---|---|---|---|
| hyderabad | 7,677,018 | interpolated | 8 km | 0.0382 | 3,465 | 3.3 m2 | yes, 77 a chunk | 82.5 ms |
| secunderabad | 217,910 | interpolated | 6 km | 0.0019 | 751 | 14.3 m2 | no, 20 | 17.9 ms |
| maheswaram_mandal | 75,525 | modelled | 10 km | 0.0002 | 98 | 108.5 m2 | no, 3 | 2.3 ms |
| akanpalle | 1,047 | interpolated | 3 km | 0.0000 | 16 | 800.0 m2 | no, 1 | 0.4 ms |

| place | even division (conc 0) | conc 40 | conc 200 | conc 800 | the view's ceiling |
|---|---|---|---|---|---|
| hyderabad | 439 | 3,465 | 3,465 | 3,465 | 3,465 |
| secunderabad | 23 | 751 | 3,328 | 3,465 | 3,465 |
| maheswaram_mandal | 3 | 98 | 455 | 1,714 | 3,465 |
| akanpalle | 2 | 16 | 61 | 150 | 3,465 |

## The option space, with what it cost

| decision | the options | what was chosen, and why |
|---|---|---|
| which rule produces the number | the resolver; log-lerp; arithmetic; constant-rate; constant-absolute; carry-forward; carry-backward; the two fixes (leave-one-out over the 13 testable census records) | the resolver - it is the only one that reads the records, and where it has records it is the best of them (5.7% median). Its two refusals are the measured defect, not the formula |
| where the crowd stands | one square at the heart; evenly over the extent; gathered over the extent (concentration); weighted by the packs' named localities | gathered over the extent. A single square was rejected at 0.014 a m2; even division measures 1 person every 457 m2 at Secunderabad and is invisible; the locality list was checked and carries no 16 m structure (530 localities over 526 distinct chunks) |
| how strongly they gather | concentration 0 ... 400 (the config's range) | one value cannot serve both a village and a city: the probe shows a city reaching the crowd density at 200 and a village reaching 4 figures a chunk at 800 |
| how the crowd is drawn | ticked entities; recorded geometry as one mesh; instanced draws; a batching TESR | recorded geometry. Measured: entities cost 23.8 us each, so the 3,465-figure view cannot be ticked; one mesh is one draw call and 15.6 MB |
| what the client draws when the view is too big | draw the nearest N; draw a uniform sample; thin every chunk by the same factor | thin every chunk by the same factor (`sampledView`, largest-remainder) - the crowd thins everywhere at once instead of losing its far half |
| what the NPC layer does | spawn NPCs to fill the area; leave vanilla alone | leave it alone: 5-40 living people with names and answers, never where the player stands (vanilla's 24-block minimum). The crowd is a drawing, not a spawn |

## What I would not do

- **Not the formula** `total2026 − (2026 − year) × averagePopulation`: it is exact at 2026 and at no
  other year. The world empties by 1948 and Secunderabad is gone in 0.65 years, because the average
  population is a level and the formula needs a rate.
- **Not one constant increase for all places** (267,338× spread between a village's and a city's rate),
  and **not an average population per place** as the growth term — 15,263 of the 15,954 averages are
  averages of modelled values, so they rest on nothing new.
- **Not a cap raised until it looks busy.** Forty ticked NPCs over a 28-block square is 0.014 people a m²;
  no cap closes a 20× density gap. The crowd is a drawing, and the density is the thing to get right.
- **Not the ring/view-guard spawner** (rejected earlier, deleted — not restored), and **not NPCs spawning
  beside the player**: the vanilla 24-block minimum stays, and the drawn crowd spawns nothing at all.
- **Not a change to the engine that I have not measured.** The two fixes above are measured and *not*
  applied; the resolver is byte-for-byte what it was.

## What is built, and what is not

| piece | state |
|---|---|
| `PopulationResolver` - the number | written, compiled, tested; 253 assertions in `run.sh` |
| `ChunkPopulation` - the division, capacity, view, manifest | written, compiled, tested; the numbers in the panels above are its output, run this turn |
| `CrowdManifestPacket` - the transport | written, compiled, tested; 166 bytes, change-only |
| `CrowdManifestStore` (client) | written, parses the manifest; **nothing draws from it yet** |
| the renderer that builds the crowd as geometry | **not written** |
| the two resolver fixes | measured by leave-one-out, **not applied** - the engine is unchanged |
| `SolutionProbe` | written this turn, compiled, run; `solution_chain.tsv` |
| the NPC spawning system | untouched, vanilla's own |

The one piece that decides whether this is real is the last: **nothing draws yet.** The number, the
division, the capacity, the manifest and the store are written and tested; the renderer that turns a
manifest into a mesh is not written. Until it is, requirement 3 is measured on paper and not on screen —
which is exactly what I would build next, and the numbers above say what it has to hit: a 64-block view
of at most 3,465 figures, 124,740 quads for the biggest of them, one draw call.

## Verdict in simple words

- The game already has the right population rule; it just refuses to use its own records twice. Fixing
  that is two small changes, measured here, one of them free.
- The cheap way to stand a crowd up is a 166-byte recipe, not four hundred entities: the server sends the
  recipe, the client builds the people. Nothing in the crowd thinks, so nothing in the crowd costs the
  tick.
- The reason a city does not look like a city today is not the number — it is that the people are spread
  over the whole district. Gather the same people where people actually gather and the heart reads at
  0.30 a m², which is a crowd you would believe.
- A view can hold 3,465 figures; both cities reach that ceiling, and a village shows a handful. That
  is the honest picture: crowded where you stand, thin where you do not.

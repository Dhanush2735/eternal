# Spawn it where the player is — the built-core crowd, measured

You said the number is not enough on its own, and that at least the game should look like a genuine
population in an area, and to try spawning it in the player's point of view. This is that, measured
end to end through the mod's own code: `PovCrowdProbe` runs the real `PopulationResolver` and the real
`ChunkPopulation`, and every number below comes out of `alternatives/pov_crowd.tsv`.

**The number is not touched.** It is still the resolver's, for any year, in any tier. What changed is
*where the crowd stands*.

## Why it looked empty — the measurement, not an opinion

The old division spread a place's people evenly over its whole extent and then gathered them at the
place's heart. Honest, and invisible anywhere else. Walking out from Secunderabad's heart, in the same
64-block view:

| where the player stands | figures in view, old division |
|---|---|
| at the heart | 751 |
| 250 blocks (250 m) out | **29** |
| 6 km out | **10** |

Akanpalle, a village of 1,047 people: **16** at the heart and
**0** two hundred and fifty blocks away. The population was there; the crowd was not,
because people do not live at a district's average density. Secunderabad's average is
33.3 m² a person — one person every fourteen metres, spread over a cantonment and its
fields. Nobody lives like that.

## What I changed

People live in a **built area**, at the density their kind of place really carries — and the built area
is the size that density implies for that population. Three constants, one rule:

| rank | built density | one figure stands on |
|---|---|---|
| village | 6,000 a km² | 167 m² |
| town | 12,000 a km² | 83 m² |
| city | 30,000 a km², or the place's own average if that is denser | 33 m² |

Three consequences, all measured:

1. **The built core has a radius that falls out of the population.** Secunderabad: 217,910
   people at 30,000 a km² is a core 1.52 km across; Akanpalle's 1,047 at
   6,000 is a core 0.24 km across. Hyderabad's own average is 38,182 a km² — denser than
   the city figure — so its core is the whole 8 km place (8.00 km), because that city really
   is that dense everywhere inside it.
2. **The crowd is anchored on the player's chunk.** Wherever a player stands inside the place, they are
   standing in its built part; the fields are elsewhere. Same walk as above, crowdedness 3:
   **1,128** figures at 250 m, **1,130** at 6 km. Akanpalle at 250 m:
   **228** — the village is now around the player instead of behind them. The drawing
   shows the same player, the same moment, both ways: 5 km from Secunderabad's heart the player's
   window holds **1,130** figures anchored on them, and **0** anchored at the
   heart — the core reaches 1.52 km from the heart, and they are standing 3.5 km outside it. Akanpalle
   at 250 m: **228** against **0**.
3. **Vanilla's 24 blocks stay empty — one figure at a time.** The rule is applied to each
   figure on its own, never to a chunk and never to the view: a figure is refused only if *it* stands
   inside 24 blocks of a player. In the view the drawing below uses, 31 figures are
   planned in the chunks whose centres fall inside those 24 blocks — and not one of them
   stands. The rest of the view (217 figures in the 24-to-48 band, 248 within 48)
   is untouched by the rule. The emptiness beside you is exactly the 24 blocks the rule asks
   for, and nothing wider.

## The gaps between the groups — removed

You said the gaps between the groups made the place look less populated, and to remove them
completely. There were three of them, and all three were mine, not the population's:

| what was wrong | before | now |
|---|---|---|
| each chunk's crowd was a **disc of radius 7** inside a 16-block chunk | worst empty spot inside a full chunk **6.70 blocks** | **3.00 blocks** |
| the chunk the player stood in was **dropped whole** — a 48-block square of nobody | 31 figures thrown away that could stand (45 chunks, 347 figures) | all 49 chunks kept, 378 figures |
| the view kept a chunk only if its **centre** was inside the interest radius, which leaves a staircase edge | biggest empty circle in the view, at crowdedness 10: **5.2 blocks** | **2.3 blocks** |

The first row compares like with like: one chunk carrying the same 23 figures, laid out the old way and
the new way. The old way was the lane that ran all round the disc and the corner hole over four blocks
wide; the new way fills the chunk. The third row is the same view at the same dial step, measured over
the whole crowd. The middle row is the chunk-drop rule applied to today's view, so it says exactly what
that rule costs now — 4 chunks and 31 figures that the population had already given us.

Walking Secunderabad's chunk edges in the drawing below — the lines the lanes used to run along:

| measure | before | now |
|---|---|---|
| share of a chunk edge with a figure within 3 blocks | 16% | **57%** |
| longest stretch of one chunk edge with nobody within 3 blocks | 43.0 blocks | **18.5 blocks** |

In the 80x80-block window drawn here, 144 figures stood before and 138
stand now (of 193 planned, 55 fall inside your own
24 and do not stand). That is six fewer standing *right beside you*, and it is the price of
filling the chunk: a crowd that covers its chunk corner to corner also covers the corner nearest you,
where your own 24 blocks refuse it. Everywhere past that — which is the whole rest of the
crowd, and the reason for the change — filling the chunk is pure gain.

Akanpalle, the village, is the honest counterweight: 28 figures in that window before,
26 now. Of 1,047 people the view holds 74 figures over its 49 chunks —
one figure every thirteen blocks or so — and **no arrangement of that many people closes a spacing like
that: it is the village's own population, not a gap in it.** What was removable there — the lane, the
dropped chunk, the staircase edge — is gone as well; the village simply does not have the people to
make a solid crowd in a view this size, and now it no longer pretends to.

## Drawn where you are looking — the second thing you asked for

Anchoring the crowd on your chunk was only half of it. The other half: the crowd was spread evenly
in **all** directions, and half of it stands where the camera is not. Standing still and looking
straight ahead, the ground on screen is a wedge about 100 degrees across, and everything drawn
outside it was spent on nobody.

So the drawing now spends its budget along the line of sight. Each chunk is weighed by how much of
its ground is in front of you — a chunk square in the view gets a full share, one seen edge-on gets
a few figures, one behind you gets none while there is anywhere in front to put them — and the wedge
is where the figures stand. **The total drawn does not move, the population does not move, the
ceiling does not move: only which of the place's own figures are drawn.**

| place, crowdedness 1 | figures drawn | in your field of view, before | after | |
|---|---|---|---|---|
| Hyderabad | 400 | 120 | **358** | 3.0× |
| Secunderabad | 378 | 111 | **342** | 3.1× |
| Maheswaram mandal | 150 | 44 | **134** | 3.0× |
| Akanpalle | 74 | 22 | **66** | 3.0× |

At crowdedness 3, Akanpalle goes from 68 figures in view to
202 of its 228 — a village street with people down it.

The nearest figure stands **24 blocks** away in Secunderabad and
**24** in Akanpalle: the 24-block rule is what sets that, and it is untouched. The
crowd is not thinned to pay for this either — the same 378 figures are drawn, in
19 chunks instead of 49, the busiest of them carrying 26.

Two things this is not. It does not invent people: Akanpalle at crowdedness 1 still shows
66 figures in view, because the village has 1,047 people and that is
what they look like. And it is a drawing, so when you turn the crowd re-forms in front of you at the
next re-lay — which is exactly what the spawner already does every cycle, putting the standing
figures back in the pool and standing them up again from the plan.

## Can the crowd be controlled without deleting anybody? — yes, and this is it

The gap you saw was closed by deleting figures last time, and that was a choice in one line of my own
code, not something the model needed. There are four ways to keep the drawing and the crowd in step,
and only one is both quiet and honest:

| option | the gap | deleted | built | reading |
|---|---|---|---|---|
| leave the surplus standing | stays — two crowds | 0 | 0 | quiet, and wrong |
| delete the surplus | closed | **378 a turn** | 378 a turn | closed, and wasteful |
| **move the surplus into the places that are short** | closed | **0** | **0** | **built now** |
| park the surplus out of reach for later | closed | 0 | 0 | the same as moving, plus a pen the world keeps loaded |

Three numbers per chunk and no fourth, and they are `CrowdDensity.reconcile`: **kept, released,
needed**. A figure whose chunk the drawing no longer wants is *released*, never removed; a chunk that
is short takes a released figure before a new one is ever built; and only a place no released figure
can stand in gets a newly built one. A figure the player can see is released last, and figures outside
the wedge the player is looking down are released first — the crowd re-forms behind their back.

Measured through that rule on the probe's real views, a full turn at crowdedness 1:

| place | standing before | after | kept | moved | built | removed |
|---|---|---|---|---|---|---|
| Secunderabad | 756 — two crowds | 378 | 0 | **378** | **0** | **0** |
| Akanpalle | 148 | 74 | 0 | **74** | **0** | **0** |

The worst gap inside the crowd goes from **24.0 blocks to 3.1**. A
quarter turn is gentler, because the two fans overlap: 44 figures keep the place they already
have and 334 move — still building none. And a move is cheaper than a build: it writes a
position and some tags where building a figure also makes an entity and sends it to every client,
which is why a turn settles in one cycle instead of arriving over half a minute.

What still removes a figure, and should: `sweep` alone — a figure from another year, or one standing
where the world has nobody at all. The old reasons are gone: not for being far from the player, not for
standing in a neighbouring place's district, and not for the player walking away. That is a life cycle,
not crowd control, and `sweep` is now the *only* place in the spawner that removes anybody.
`verification/population/crowd_control_check.py` reads the file and fails if a `discard()` appears
anywhere else; it is run with the other gates.

## Why he did not move, and what he does now

A person you left and came back to after two Minecraft days was standing exactly where you left them.
Three things were true and each one alone was enough.

1. **A figure has no goals.** That is the whole reason a crowd is affordable — no goal checks, no
   navigation, no path search — and it also means nothing ever moves one: a figure stands where the
   placement put it until the drawing changes.
2. **The placement only changed when the drawing did.** Turn, walk, or a new year: the crowd was
   re-arranged, and the re-arrangement was the only motion the crowd ever had. Walking away — which is
   what the report describes — changes nothing about the drawing, because the crowd's own control now
   leaves a walking player's crowd alone.
3. **The living were frozen on purpose.** Past `presenceSettleBlocks` a presence NPC has its goals
   switched off, and `routineStep`'s own predecessor was the only thing that ever moved one — a
   "settle" that exists precisely so a distant crowd costs nothing.

And the fourth, which made the other three permanent: **a chunk nobody is near does not tick at all**,
so being away did not let a timer run. Two Minecraft days passed in the world's clock and nobody's
position could ever have noticed.

**What they do now.** A person's position is a function of the world clock. Home is the place the
ground gave them — for a figure, the plan slot whose identity names them; for a living NPC, the spot
they were first stood up on (`TAG_HOME_X`/`TAG_HOME_Z`). Their day's route turns three quarters of a
turn a day, gives or takes a jitter, so two days later they are never in the same corner of their own
space by accident. Dawn and dusk are the two walks: out over 200 ticks at a walking pace, back over
200 ticks at the other end of the day. Nothing is stored, nothing ticks, nothing is simulated while
nobody is looking — the walk is arithmetic on `level.getGameTime()`, so it costs exactly nothing and
still happens.

Two Minecraft days later, measured off the engine's own rule over the probe's real views:

| place (2026) | people | moved two blocks or more by day 3 | median move | out at midday |
|---|---|---|---|---|
| Hyderabad | 400 | **99%** | 5.9 blocks | 99% of them |
| Secunderabad | 378 | **98%** | 5.8 blocks | 98% of them |
| Maheswaram mandal | 150 | **97%** | 5.5 blocks | 97% of them |
| Akanpalle | 74 | **96%** | 5.4 blocks | 95% of them |

A figure walks only inside its own chunk — the walk is shrunk to the room it has and turned back if
the room only allows a shuffle — because the place in the chunk is what names the person, and a
person who wandered into the next chunk would be somebody else's person. A living NPC walks inside
six blocks of their home, stepping one block a cycle and only onto ground that would hold a person,
and only while they are settled and nobody is within the name-tag distance: a person you are close
enough to watch walks on their own feet.

**What this costs.** Two constants and one deterministic function. No stored route, no per-person
state added to the world, no tick of any kind while nobody is looking. The one behaviour it changes
for a player standing in the crowd is the reason it exists: come back after two Minecraft days and
the person is not where you left them.

## The choice: never removed, or removed and remembered

Two ways to promise that the NPC you meet is the NPC you meet again, and they are not the same
promise.

**Option 1 — once spawned, never removed.** Free to promise, expensive to keep. Nothing that has been
stood up may ever leave, so the world carries every figure it has ever shown. Measured on the engine's
own numbers, walking 2,048 blocks across Hyderabad leaves **6,800** figures standing, of which
**6,400** are behind the player; Secunderabad is 6,392 and Akanpalle
1,269. And revisiting one place over time compounds it: three visits to Hyderabad — 1948, 2001
and now — would leave **378 + 378 + 400 = 1,156** figures standing at once, and in
Secunderabad 378 + 378 + 378 = 1,134. Worse than the count, the machinery stops
working: vanilla's despawn and the district check cannot remove a wanderer, so a crowd scatters and can
never be re-formed — the crowd you kept is no longer a crowd.

**Option 2 — removed, but remembered.** The one that is built. A person who leaves is written into the
place's memory first, and the place hands them back: the same UUID — so the same skin, because the
client picks the face from the UUID — the same name, the same civilization, role, era and gender, and
the same standing. It costs 13 fields a person, at most 64 a place, written with the world's own save.
The first half is the crowd's own control, which never deletes a figure (378 moved, zero
removed on a full turn); the second half is `PeopleMemory`, and the check that runs it.

## Coming back to the same crowd

Walk away and come back and you found strangers: every figure was built with a fresh random identity,
the crowd's places were re-rolled whenever the count around them changed, and `sweep` threw figures
away for standing out of reach of the player. Three things were wrong at once; all three are fixed,
and this is the measurement.

**A place names the person.** A figure's identity is now a function of the ground — the world's seed,
the chunk, and the place in that chunk's plan (`CrowdDensity.identity`) — and the mod picks the face
from that identity, so the person standing in a place is the same person every time the place is
looked at. On the same spot, in the same view:

| place (2026) | figures drawn | same people back | the old way | same bodies after a 256-block round trip |
|---|---|---|---|---|
| Hyderabad | 400 | **400** | 0 | **400 of 400** |
| Secunderabad | 378 | **378** | 0 | **378 of 378** |
| Maheswaram mandal | 150 | **150** | 0 | **150 of 150** |
| Akanpalle | 74 | **74** | 0 | **74 of 74** |

**The plan belongs to the ground.** A chunk's places are one fixed lattice, the same whether one figure
lives there or seventy-seven, and a figure is given the place in it that a watcher would meet first.
Which places are *filled* still follows the drawing; the places themselves no longer move when the
count around them does.

**A crowd that has been seen is not re-arranged for a player who is walking.** This is the part that
was really costing you your people: the crowd used to chase the drawing step by step, so a walk to the
edge of the place dragged the crowd off the ground it had been standing on. Now the re-arranging
happens only for eyes that have stopped. Walk on and the ground ahead is filled with its own people
(1,834 figures stood up over the round trip, 0 moved) while the crowd
behind stays standing where it stood — which is why the walk home finds it, body for body. Nothing is
removed in the meantime, and 1,834 figures are standing in the world at the end of it: the
city you walked through, still there.

**Meeting it on the way in.** Coming towards the crowd you meet its near side first — 0
figures in the first sixteen blocks (the 24-block vanilla bubble is empty on purpose), then
70, 109 and 160 in the strips of ground after that, 378
distinct people and not one of them shown twice. Akanpalle the same way:
0 / 14 / 24 / 31.

## The dial, and its ceiling

`presenceCrowdConcentration` used to say how much more the heart carried than the edge. It is now a
multiplier on the place's own built density — 40 (the default) is the built density itself, 400 is ten
times it:

| place, 2026 | the resolver's number | x1 | x3 | x10 | one figure stands on, at x10 |
|---|---|---|---|---|---|
| Hyderabad | 7,677,018 | 478 | 1,438 | 3,773 | 3.3 m² |
| Secunderabad | 217,910 | 378 | 1,129 | 3,763 | 3.3 m² |
| Maheswaram mandal | 75,525 | 150 | 453 | 1,506 | 3.3 m² |
| Akanpalle | 1,047 | 74 | 228 | 753 | 3.3 m² |

These counts are the model's — how many people the view holds. What gets **drawn** is the client's
budget, 400 by default, and it is now spent along the line of sight, as above.

At x1 you are looking at a real density: 26 m² a person in Hyderabad, 33 in Secunderabad, 167 in a
village. At x10 a chunk is holding its full 77 figures — one person every 3.3 m² — which is a
crowd, shoulder to shoulder, and it is the ceiling: the view tops out at 3,773 figures.

The ceiling is not decoration. The first run of this probe showed Hyderabad's built core summing to
**−21.2%** of its population at x10, because 97.7 people wanted to stand in a 256 m² chunk and the cap
is 77: past that the cap stops being a budget and starts eating people. The density is now held at
3.3 m² a person, and the check below is exact again.

## The conservation check

The population is not inflated to fill the view. Every chunk's people are the density times the
chunk's ground, the whole built core is summed chunk by chunk through the same lottery the crowd
placement uses, and it is the resolver's number:

| place | the resolver's number | the built core, summed | difference |
|---|---|---|---|
| Hyderabad | 7,677,018 | 7,677,224 | +0.003% |
| Secunderabad | 217,910 | 217,875 | -0.016% |
| Maheswaram mandal | 75,525 | 75,508 | -0.023% |
| Akanpalle | 1,047 | 1,055 | +0.764% |

The residue is the per-chunk lottery — a chunk holding a third of a person shows one a third of the
time, which is how a fraction survives being counted in whole people.

## The one thing this model assumes, said plainly

Anchoring the core on the player means **you are always standing in the built part of the place**. A
player 5 km from Secunderabad's heart — outside its 1.52 km core, in what are fields —
still sees the built density (1,130 figures at x3). The fixed-core alternative is in the
same probe: anchor the core at the heart instead, and that same player sees
**0** — the fields are fields, and the town is behind them 3 km away.

Both are measured; the mod ships the player-anchored one, because you asked for the feel, and because
the number it shows is still the place's own density and still a small sample of the place's own
population: at x3, Secunderabad's view holds 1,129 of its 217,910 — 0.4% of
the place at a time. What it does not claim is that the whole cantonment stands at city density; it
claims the part you are standing in does, which is where people actually are.

## What this costs

Unchanged: the figures still have no goals, no navigation and no physics; the transport is still
88 bytes a view, sent only when the view changes; the chunk capacity is still 77 and the client
budget still thins the view if it cannot draw it. The difference is only the count: at x1 a city view
holds a few hundred figures, at x10 a few thousand — which is 9.5 ms to 82.5 ms a frame *if they were
ticked entities*, and one mesh and one draw call as recorded geometry. That is the whole reason the
crowd is a drawing.

## Status — what is built, what is not

| piece | state |
|---|---|
| `ChunkPopulation` — `viewOfCore`, `corePopulation`, the density ceiling | **written, compiled, run this turn** |
| `CrowdFigures.ensure` — stands the crowd up in the core around the player, using `CrowdDensity.planSquare` | **written, compiled** (stub compile with the real classes) |
| `ChunkPopulation` — the view now takes a chunk whose **nearest point** is inside the interest radius, so the crowd's edge follows the radius instead of a staircase | **written, compiled, run** |
| `ChunkPopulation.viewTowards` — the budget spent along the line of sight | **written, compiled, run**: 10 new assertions in `run.sh`, and the probe's own numbers |
| `CrowdFigures.ensure` — stands the crowd up with `viewTowards` | **written, compiled** (stub compile; your Gradle build has not seen it) |
| `CrowdDensity.identity` + `CrowdFigures.stand`/`putOnPlan` — the place names the person | **written, compiled**; 7 new assertions in `run.sh` |
| `CrowdDensity.settledCount` — a chunk standing near what is asked keeps what it has | **written, compiled**; 5 new assertions in `run.sh` |
| `CrowdDensity.placeSeed` + the fixed plan in `putOnPlan`/`putBack` | **written, compiled** (stub compile) |
| `CrowdFigures.sweep` — distance and district rules gone; the place is no longer an argument | **written, compiled**; `crowd_control_check.py` still CLEAN |
| the rule that a walking player re-arranges nobody | **written, compiled** (stub compile); measured in `PovCrowdProbe`'s AWAY rows |
| `CrowdDensity.reconcile` — kept, released, needed; and `ensure` moving figures instead of deleting them | **written, compiled**, with 9 new assertions in `run.sh` |
| `crowd_control_check.py` — proves the spawner's only delete is `sweep`'s | **written, run: CLEAN** |
| `PresenceLocalSpawner` — the manifest now carries the player's yaw | **written, compiled** (stub compile) |
| `PeopleMemory` — the people a place remembers, kept in the world's save (`SavedData`) | **written, compiled, run**: pack B, and 12 assertions in `MemoryCheck` |
| `CrowdDensity.dayOut` / `routineVector` / `routinePointIn` — the day, fitted to a chunk | **written, compiled, run**: 10 new assertions in `run.sh`, and `PovCrowdProbe`'s DAY rows |
| `CrowdFigures.standPoint` / `stepTowards` — a figure walks its day, a step a cycle | **written, compiled** (stub compile) |
| `PresenceLocalSpawner.routineStep` + `TAG_HOME_X`/`TAG_HOME_Z` — a settled living person's day | **written, compiled** (pack B; needs a live world to tick) |
| `VirtualVillager.save`/`load`, `gender`, `era` — everything a person is | **written, compiled, run** |
| `VillagerVirtualizer.remember` / `remembered` / the restored UUID | **written, compiled, run** |
| `PresenceLocalSpawner` — all seven removals remember; both spawn paths ask the memory first | **written, compiled** (pack B; the run would need a live world) |
| `crowd_control_check.py` — extended: no removal without a remember | **written, run: CLEAN** |
| `typecheck/compile_b.sh` + `MemoryCheck` | **written, run**: the villager package compiles and the promise passes |
| `PovCrowdProbe` | **written, compiled, run**; `pov_crowd.tsv` |
| `run.sh` | **301 assertions, all passing** — 10 new this change, on the day a person has |
| `typecheck/typecheck.sh` | compiles both packs and runs both checks: the manifest call and the memory |
| `typecheck/typecheck.sh` | compiles the two files your Gradle build rejected, and runs the call |
| `alternatives/rig.sh` | compiles and runs every probe, including this one |
| the renderer that would draw these figures | **not written** — nothing draws yet |
| `PopulationResolver` | **unchanged** — the number is the same number |

The honest reading: the server side of "spawn in the player's POV" is now real code, compiled, and
measured — figures are placed in the built core around the player, conservatively, with the bubble
respected. What is still missing is the last link: the client renderer. Until that exists the figures
are still spawned as NPC entities with their AI off, which is the expensive way to draw them — 3,149
figures at x10 is 75 ms a frame, not a frame. At x1 (378 figures, 7.5 ms) it is
playable today as entities; at x3 and above it wants the mesh.

## Verdict in simple words

- It looked empty because the crowd was spread over the whole district. Now the people stand where
  people stand — in the built part, at a real density — and the built part is as big as the population
  makes it.
- The crowd is anchored on you: walk anywhere in the place and the town is around you, not 3 km behind
  you. The vanilla 24-block bubble stays empty, so nobody spawns on top of you.
- The number is untouched: the whole built core still sums to the resolver's population, chunk by
  chunk, to within a rounding error.
- You get a dial, and it has a ceiling: x1 is a real density (33 m² a person in Secunderabad), x10 is
  shoulder to shoulder (3.3 m² each) and the view tops out at 3,773 figures.
- At x1 this is playable as plain NPC entities today. Above that it needs the renderer — which is
  still the one piece not written.
- The crowd is drawn where you are looking now, not in a ring all round you. Same population, same
  number of figures drawn — 111 of Secunderabad's 378 were in your field
  of view before, **342** are now.
- The crowd can be kept in step with the drawing without deleting anybody: the surplus is **moved**
  into the places the drawing wants, so a turn leaves one crowd, builds 0 figures and
  removes 0 — 378 figures simply walk to a new place, out of your view.
- Walk away and come back and it is the same crowd: the person standing in a place is the person that
  place belongs to, so on the same spot **378 of 378** figures are faces
  you have already met (it was 0). A 256-block round trip moves
  0 of the crowd you left and finds **378 of 378** of it
  home again, body for body.
- He did not move because nothing in the mod made time pass for a person: figures have no goals by
  design, a living person past the settle distance has theirs switched off, and a chunk nobody is
  near does not tick. Now a person's position is a function of the world clock — home, a walk out,
  and back at night — so two Minecraft days later **98%** of Secunderabad's crowd has
  moved two blocks or more, at no stored cost and no ticks.
- A removed NPC is not lost. Every removal writes the person into the place's memory first — same
  UUID, same name, same skin, same role — and the memory goes with the world's save. The check that
  proves it removes a person, saves, reloads and brings the identical person back: 12 of 12.
- Option 1, "never removed", was measured and not chosen: 2,048 blocks of walking would leave
  6,800 figures standing (6,400 of them behind you), and nothing could ever be
  thinned or re-formed.
- The gaps between the groups are gone: the lane round each chunk's crowd, the chunk you stood in
  being thrown away, and the staircase edge of the crowd are all removed and measured
  (6.70 → 3.00 blocks empty inside a chunk). The one empty circle left is the
  24 vanilla keeps around you, and that one is on purpose.

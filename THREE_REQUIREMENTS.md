# how I would solve it — the three requirements

*The population the resolver gives, divided over the place's own area; carried at a cost that does not
scale with the crowd; and looked at from inside the place, at the spacing and the density a genuine
population has.*

Picture: **`three_requirements_2d.html`**. Numbers: `verification/population/alternatives/chunk_crowd.txt`
(printed by `ChunkCrowdProbe`, which calls the mod's compiled classes) and `ceiling_numbers.txt`
(every outside measurement, each naming its source).

---

## Requirement 1 — PopulationResolver supplies the genuine number

`PopulationResolver` resolves one number for one place in one year, from the census series, by
log-interpolation between the recorded years. The place's size comes from the same pack: its own
`radiusKm` (Secunderabad 6 km, Hyderabad 8 km, Atraf-i-Balda 90 km). `ChunkPopulation` divides the
number over the chunks of that area — nothing else supplies a number, and every person is placed
somewhere.

| place | population | extent | area | chunks | people in a chunk | one person every | shown over all chunks |
|---|---|---|---|---|---|---|---|
| Akhanpally Thanda, 1948 | 348 | 2.0 km | 12.6 km² | 49,087 | 0.007 | 190 m | 344 |
| Akanpalle, 2011 | 1,047 | 2.0 km | 12.6 km² | 49,087 | 0.021 | 110 m | 1,031 |
| Maheswaram mandal, 2011 | 8,795 | 3.0 km | 28.3 km² | 110,447 | 0.080 | 57 m | 8,836 |
| **Secunderabad, 1948** | **142,174** | **6.0 km** | **113.1 km²** | **441,786** | **0.322** | **28.2 m** | **142,255** |
| Secunderabad, 2026 | 217,910 | 6.0 km | 113.1 km² | 441,786 | 0.493 | 22.8 m | 217,800 |
| Hyderabad, 1948 | 980,600 | 8.0 km | 201.1 km² | 785,398 | 1.249 | 14.3 m | 980,962 |
| modern Hyderabad, 2026 | 7,677,018 | 8.0 km | 201.1 km² | 785,398 | 9.775 | 5.1 m | 7,677,265 |

The conserved count is the seeded rounding's: a chunk carrying 0.322 people shows one about a third of
the time, so the total is the population within a tenth of a percent. The rule is arithmetic, not
sampling of a stored list — nothing is held per person, per chunk or per place.

## Requirement 2 — the genuine population at minimum cost

Three separate costs, each measured.

**The wire.** The crowd does not travel. The recipe does: world seed, year, population, extent,
concentration, heart, and three bytes per chunk in the view — `ChunkPopulation.Manifest`, encoded and
opted into the test suite. Measured: **166 bytes for a 64-block view** — the same 166 whether it
describes 13 figures or 3,465, because it describes the *place*, not the figures. Four hundred spawned
entities are many kilobytes of packet, re-sent every time a player walks back to them.

*Status, precisely:* the encoder, its size and its **round trip** (encode → decode → same chunks,
including a place west of the origin) are **built and tested**; the packet that carries it, its
registration and the client-side store are **written and parse-checked** —
`network/packets/CrowdManifestPacket.java` (which carries the tested bytes rather than a second
format of its own), registered in `EternalEnginePacketHandler`, sent from the spawner only when the
player's chunk, the place or the year changes, and held on the client in
`client/CrowdManifestStore.java`. What is **not** written is the renderer that reads the store and
builds the crowd's mesh — that is the rest of step 3.

**The server.** Nothing is simulated. Figures are the mod's own `HumanNpcEntity` with no AI, no goals
and no navigation; the division is a handful of arithmetic over the 45–50 chunks in the view; 32
figures are stood up a cycle; a chunk with nobody in it, an unloaded chunk, or a chunk with no ground
is skipped and retried. The server cost of a crowd of 3,465 is the same as a crowd of 13.

**The frame.** This is where figures cost, and it is measured by others, quoted with sources:

| drawn as | today | population-true |
|---|---|---|
| entities, 23.8 µs a figure | 400 → 9.5 ms (57% of a 60-FPS frame) | 3,465 → 82.5 ms (five frames) |
| recorded geometry, built once | 400 → 1,800 KB, 3.6 chunk sections | 3,465 → 124,740 quads, 15,593 KB, 31 sections, **one mesh** |

`400` today is `presenceDrawnFiguresMax`; it exists because entities are the drawing mechanism. Built
as geometry the cap is not a cost, it is a choice — 31 chunk sections is a hillside. The build cost is
~0.6 ms for 400 figures, once, when the player arrives (projection at 1.5 µs a figure, labelled as
such). Outside precedents: Polarium 5,000 bodies 12 → 92 FPS in ~10 draw calls; ImmediatelyFast 1,000
cows 21 → 82; Forge's own FastTESR batches every instance into one combined draw call.

## Requirement 3 — "at least it can look like a genuine population in an area"

The look is two different quantities and the numbers say both.

**Spread across the area.** One person every **190 m** in a hamlet, **110 m** in a village of a
thousand, **57 m** in a mandal town, **28 m** in Secunderabad 1948, **23 m** in Secunderabad 2026,
**14 m** in Hyderabad 1948, **5 m** in modern Hyderabad. A settlement is *walked through* — and with
the crowd divided over the whole area that is now literally true: a player meets people wherever they
walk inside the place, not only at its centre.

**Gathered where a population gathers.** Held at the density `CeilingProbe` measured for a real crowd
(0.551 people/m²), the crowd covers:

| place | population-true figures | ground they fill | as one mesh |
|---|---|---|---|
| Akhanpally Thanda, 1948 | 13 | 5.4 blocks across | 59 KB |
| Akanpalle, 2011 | 31 | 8.4 blocks | 140 KB |
| Maheswaram mandal, 2011 | 119 | 16.6 blocks | 536 KB |
| Secunderabad, 1948 | 491 | 33.6 blocks | 2,210 KB |
| Secunderabad, 2026 | 754 | 41.8 blocks | 3,393 KB |
| Hyderabad, 1948 | 1,919 | 66.6 blocks | 8,636 KB |
| modern Hyderabad, 2026 | 3,465 | 89.4 blocks | 15,593 KB |

So the honest verdict on requirement 3: **a crowd at a genuine density, in the 64-block view around
the player, at every place tested — for a hamlet exactly as much as for a metropolis.** What the 400
cap does today is 400 figures in a 30-block disc at 0.551/m² (blue ring in the picture): a real crowd,
but the same real crowd in every big city. The population-true crowd fills 34–89 blocks instead
(amber ring). The cap is not a modelling limit; it is the price of drawing people as entities, and
that is exactly the one thing left to change.

## The four steps

| # | step | state | what it is |
|---|---|---|---|
| 1 | the number and the area | **built** | `PopulationResolver.radiusKm` + `ChunkPopulation`: 0.322 a chunk, conserved, tested |
| 2 | the view's crowd as a recipe, anchored on the player | **built (transport included)** | `manifestOf` → 166 bytes, round-trip tested; `CrowdManifestPacket` registered and sent only on change; client store holds it. Left: the renderer that builds from it |
| 3 | the drawing mechanism | **next — and the build stops here** | a client renderer that reads the manifest and builds the crowd as recorded geometry — one mesh, one draw call, no cap. Client-side and new; the server side of it already exists and is tested |
| 4 | the living people | unchanged | 5–40 with names, skins and answers — the interaction layer, tested in the shell, never what carries the crowd |

**Where the crowd stands, 2026-10-06.** The division changed, because the old one was invisible away
from a place's heart: measured, Secunderabad showed 751 figures at the heart and **29** two hundred and
fifty metres out. The crowd is now the place's people at the place's own built density — 6,000 a km² for
a village, 12,000 for a town, 30,000 for a city — in the built area that density implies (Secunderabad's
217,910 people make a 1.52 km core), anchored on the player's chunk, with vanilla's 24-block bubble
empty. The population is untouched: the whole built core re-sums to the resolver's number to within
0.8%, through the real placement. One consequence worth knowing: anchored on the player, the window is
the same wherever they stand inside the core, and a player outside the core sees the built density with
the fields around it — the alternative, a core fixed at the heart, is measured in the same probe (831
figures against 0 at 5 km) and is drawn in `pov_crowd_2d.html`. Details: `POV_CROWD.md`.

**Build state, 2026-10-06.** The two server-side files of step 2 had never been through a compiler:
the rig compiles `ChunkPopulation` and `CrowdDensity` out of `population/`, and nothing out of `villager/`,
so the first compiler to see them was the Gradle build, and it rejected two lines —
`PresenceLocalSpawner.java:698` (`config(name, fallback)` returns an `int` while the concentration is a
`double`) and `CrowdFigures.java:271` (`Level` has no `getSeed()`). Both are fixed and both are now
checked by `verification/population/alternatives/typecheck/typecheck.sh`, which compiles the patched
`CrowdFigures.java` and the exact manifest call against faithful stubs, and runs the call. It prints
`manifest: 45 chunks, 400 figures, 166 bytes`. That is a stub compile, not a Forge build: the real
build is the one on the machine with the mod.

Step 3 is deliberately last and deliberately alone: everything before it is arithmetic the mod already
runs, and everything after it is exactly one file of rendering. The other routes (display-entity rigs,
packet-only fake players, keeping the entity cap and raising `presenceDrawnFiguresMax`) are still in
`HOW_I_WOULD_SOLVE_IT.md` §4, with their measured costs; none of them reaches the density in the table
above without either thousands of entities or thousands of packets.

## Is it possible? — the boundary, as a number

The three requirements are possible in the form asked for, and impossible in one specific
literal form. Both sides are measured; the row below is printed by `ChunkCrowdProbe`
(`chunk_crowd.txt`, `feasible` lines) at one density and one figure model.

| what | figures | geometry as one mesh | 60 FPS vertex rate | as entities (23.8 µs each) |
|---|---|---|---|---|
| today's cap | 400 | 1,800 KB · 3.6 sections | 3 M/s | 9.5 ms a frame |
| Secunderabad 1948, the whole view | 491 | 2,210 KB · 4.4 sections | 4 M/s | 11.7 ms |
| **modern Hyderabad, the whole view** | **3,465** | **15,593 KB · 31.1 sections** | **30 M/s** | **82.5 ms** |
| every person in modern Hyderabad, as bodies | 7,677,018 | 32.9 GB · 68,982 sections | 66,329 M/s | 182.7 s a frame |

* **A view is possible.** 3,465 figures are 31 chunk sections, one mesh, 30 M vertices a second — and
  the precedent is measured, not hoped: Polarium draws 5,000 player-shaped bodies at 92 FPS in about
  ten draw calls; ImmediatelyFast takes 1,000 cows from 21 to 82 FPS; Forge's FastTESR coalesces every
  instance of a render type into one draw call.
* **A city as bodies is not.** The last row is plotted for scale: 34 GB of vertex data and 183 seconds
  of work for one frame at 60 FPS. No machine, no renderer, no mod.
* **And it is not needed, which is why the design is what it is.** Sight is bounded: wherever the
  player stands, the crowd that must exist is one view. Everything outside it is arithmetic — 0.322
  people a chunk, conserved, and not one body. That is the difference between *divide the population,
  then draw the view* and *spawn the population*, and it is the reason this is feasible at all.

The one thing that was never claimed possible: **showing every inhabitant at once.** Minecraft draws a
few thousand figures; Hyderabad has millions. The design makes the millions real where it matters —
the resolver's number, the division, the spacing — and makes the few thousand the part the eye sees.

## Verification

* **suite 10**, `ChunkPopulationTest` — division, conservation, chunk capacity, seeded rounding
  (441,786 chunks → 142,295 people), sampler, view, footprint radius, and the manifest (166 bytes for
  45 chunks and 400 figures, encoding size exact).
* the recipe's round trip — encode/decode identity, chunk for chunk, including a place west and north
  of the origin, and a refusal on nonsense bytes.
* `run.sh` — **270 PASS, 0 FAIL**, including 7 assertions on the chunk plan that removed the
  gaps and 10 on the sight-line drawing; `parse_check.py` — **25 files, 0 syntax
  failures**; `static_audit.py` — **CLEAN**, now including the transport: the packet registered, the
  recipe sent only when it changes, the tested byte format reused, the client store.

## Drawn where the player is looking

The crowd was anchored on the player's chunk but spread evenly all round them, so most of it stood
where the camera is not. The drawing now spends its budget along the line of sight:
`ChunkPopulation.viewTowards` weighs each chunk by how much of its ground is inside the player's wedge
(a hundred degrees across — the ground a screen shows) and outside vanilla's 24-block bubble, fills the
chunks in front up to the ceiling, and falls back on what is nearest only if the front cannot hold
everybody. `CrowdFigures.ensure` stands the crowd up from it, and the manifest carries the same
allocation, so the client draws the same crowd.

Measured with a fixed budget of 400 figures: **111 → 342** of Secunderabad's drawn crowd is in the
player's field of view, **22 → 66** in Akanpalle. The total drawn is unchanged, the population is
unchanged, and the nearest figure still stands 24 blocks away — the bubble is now respected by the
*drawing* as well, so the budget is no longer spent on figures the spawner would refuse. The manifest
a view needs also fell from 178 to 67 bytes, because the crowd it describes lives in fewer chunks.

## Crowd control without deleting anybody

Keeping the drawing and the crowd in step used to mean throwing figures away. It does not have to:
three numbers per chunk — **kept, released, needed** (`CrowdDensity.reconcile`) — say who keeps a
place, who is let go to take one somewhere else, and who is still wanted. A chunk the drawing no
longer wants releases its surplus; a chunk that is short takes a released figure before a new one is
ever built. Measured on the probe's real views, a full turn at crowdedness 1: Secunderabad goes from
756 figures standing (two crowds, a 24.0-block gap between them) to 378 standing in one crowd — with
0 figures built, 0 removed, and 378 moved, released farthest-from-the-player's-eyes first. Akanpalle
the same: 148 → 74 standing, 74 moved, 0 built.

Removing a figure is now `sweep`'s job alone — a figure from another year, or one standing where the
world has nobody at all. Distance from the player, standing in a neighbouring place's district, and the
player walking away are no longer reasons to remove anybody.
`verification/population/crowd_control_check.py` reads the spawner and fails the build if a
`discard()` appears anywhere else; it currently reports CLEAN.

## The same people, on the way back

Walk off and come back and you used to find strangers. Three things were wrong at once: every figure
was built with a fresh random identity (the mod picks the face from the identity), a chunk's places
were re-rolled whenever the count around them changed, and `sweep` threw figures away for being out of
reach. The person standing in a place is now a function of the ground — the world seed, the chunk, and
the place in that chunk's plan (`CrowdDensity.identity`) — the plan itself is one fixed lattice per
chunk, and the crowd's own control only re-arranges anything while the player is standing still.

Measured through the probe's real views, at crowdedness 1: coming back to the same spot, **400 of 400**
figures in Hyderabad are people whose ground it is, **378 of 378** in Secunderabad, **150 of 150** in
Maheswaram and **74 of 74** in Akanpalle — against **0 of every one of them** under the old scheme.
A 256-block round trip moves **0** of the crowd you left and finds all of it home, body for body;
it stands up 1,834 new figures in Secunderabad over the trip, because the ground ahead fills with its
own people and nothing is taken from behind. The panel is in `pov_crowd_2d.html` and the table in
`POV_CROWD.md`.

## Why the NPC did not move

Reported after the memory work: see an NPC, return two Minecraft days later, and he has not moved.
Four things were true at once, and each one alone was enough. A figure has **no goals** — that is what
makes a crowd of hundreds affordable — so nothing ever moves one. The placement only ever changed when
the *drawing* did (a turn, a walk, a new year), and after the last change a walking player's crowd is
left alone on purpose. A living NPC past `presenceSettleBlocks` has its goals switched off by design.
And a chunk nobody is near **does not tick at all**, so no timer could have run while the player was
away: two Minecraft days passed and nobody's position could notice.

Now a person's position is a function of the world clock. `CrowdDensity.dayOut` gives the day — home,
out over 200 ticks at dawn, back over 200 ticks at dusk; `routineVector` gives each person a route
that turns three quarters of a turn a day, so two days later they are never in the same corner by
accident; `routinePointIn` fits that walk inside the chunk its owner belongs to, shrinking it to the
room and turning it back when the room only allows a shuffle. A figure steps along it a cycle at a
time (`CrowdFigures.stepTowards`), and a settled living NPC walks the same day inside six blocks of
their own home (`PresenceLocalSpawner.routineStep`), stepping only onto ground that would hold a
person and only while nobody is within the name-tag distance — a person you can watch walks on their
own feet. Measured: two Minecraft days later, **99%** of Hyderabad's crowd has moved two blocks or
more (median 5.9 blocks), 98% in Secunderabad, 97% in Maheswaram, 96% in Akanpalle, and 95-99% of them
are out at midday and home at night. It costs two constants and arithmetic on `level.getGameTime()`:
nothing stored, nothing ticked, nothing simulated while nobody is looking.

## Removed, but remembered — the choice the spawner now makes

Two ways to promise that the NPC you meet is the NPC you meet again, and the engine takes the second.

**Never removed** is free to promise and expensive to keep: nothing may ever leave, so a 2,048-block
walk across Hyderabad leaves **6,800** figures standing, 6,400 of them behind the player, and three
visits to the same place across the years leave 1,156 standing at once. And the machinery stops
working — vanilla's despawn and the district check can no longer remove a wanderer, so a crowd scatters
and can never be re-formed.

**Removed, but remembered** is what is built, in three pieces: `PeopleMemory` — the people a place has
lost, 13 fields each, at most 64 a place, kept in the world's own save; `VillagerVirtualizer.remember`
— called before every removal, storing the UUID, name, civilization, role, era and gender; and the
restored UUID on the way back, which is the same skin, because the client picks the face from the
UUID. The living are asked for first: `standPerson` consults the memory before anybody new is invented,
and an invented person is the last resort.

`crowd_control_check.py` now reads every file that removes a person and fails the build unless the
removal is a remember — or `sweep` (a life cycle), or the ECS hand-off, which writes the person's
state into its own store. `typecheck/compile_b.sh` compiles the villager package for real, and
`MemoryCheck` removes a person, saves the world, reloads it and brings the identical person back:
12 of 12, including the same name, the same skin, the same role and the same standing.

## Verdict in simple words

The first two requirements are done and they are done the way the instruction asked: the resolver's
number, divided over the settlement's own area, so that Secunderabad in 1948 is 0.322 people in an
average chunk and one person every 28 metres, and the whole crowd's recipe encodes to **166
bytes** — and now travels: `CrowdManifestPacket` carries those bytes to the client, sent only when the
view changes. Nothing simulated, nothing per-tick. The third one is the one that costs: at a
genuine crowd density the figures are all there — 491 for Secunderabad 1948, 3,465 for modern
Hyderabad — but the game currently draws them as **entities**, and entities cost 23.8 µs each per
frame, which is why 400 is the cap and why every big city looks the same in the middle. Draw the same
figures as **recorded geometry built once** — one mesh, 31 chunk sections, one draw call — and the cap
disappears, the crowd covers 34–89 blocks instead of 30, and a walk through the fields still finds one
person every 5 to 190 metres depending on where you are. That single change of drawing mechanism is
all that stands between this and a genuine population that looks genuine in the game.

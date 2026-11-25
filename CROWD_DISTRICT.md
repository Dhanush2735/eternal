# The crowd stands in a place

**Request:** *"at least can the game look like a genuine crowd in an area?"*
**Short answer: yes — 35 people stand in one 28-block district, three blocks apart, instead of four people scattered over a 128-block ring.**

> **Correction, after shipping it:** this document measures how *tightly the same 35 people* can be packed,
> and at the time I called that density. It is not density in the sense that matters: the square is the
> same size however close together the 35 stand, so 35 people over 2,463 m² is **0.014 people per m²**
> whatever the knots do — 26.1 m² of ground each, against 0.5–1.0 per m² for a real market crowd. The
> **427×** below is 35 people packed into a small part of the square instead of spread through it: it
> makes the group look better, and it does not make it a crowd. What made it a crowd is
> `CROWD_DRAWN.md`: 377 figures drawn around the same 35 living, at 0.328 per m².

Picture: `crowd_area_2d.html` (open it — three panels, same scale).
Verified by: `verification/population/run.sh` (8 suites, all passed), `static_audit.py` (CLEAN, 116 checks), `parse_check.py` (19 files).

---

## What changed

The count was already right — the resolver's number decides how many people appear. What was
wrong was *where they stood*: the spawner placed each person on a ring 24–128 blocks out from
the player. Four people over 49,662 m² is one figure every 35 blocks. That is not a crowd; it is
a walk through an empty morning.

Now the spawner picks **a district** and puts the whole crowd inside it.

| | before | now |
|---|---|---|
| where they stand | anywhere on a ring around you, 24–128 blocks | inside one disc, radius 28 blocks |
| how many | 4, always | 10 / 20 / 40, from the resolver's number |
| ground per person | 12,416 m² | **26.1 m²** |
| nearest neighbour | 35.1 blocks | **3.0 blocks** |
| closest pair | 24.3 blocks | 2.8 blocks — nobody shares a block |
| extent | spread over 128 blocks | **all inside 20 blocks of the centre** |

Same number of people in the same place; measured from the coordinates the spawner's own code
prints, not estimated.

**427× the local density.** That is the whole trick: the eye reads density, not head count.

---

## How a district is chosen

1. **The settlement's own centre wins,** when the settlement is within the spawn distance. The
   gazetteer already knows the lat/lon of every site; `TerraCoordinateHelper.fromGeo` turns that
   into a block position. A crowd at Akanpalle gathers at Akanpalle.
2. **Otherwise** (nothing historical nearby) a point is drawn 24–128 blocks out and **snapped to
   a 32-block grid.** The snapping matters: an unsnapped centre moves with the player, so the
   people already standing there would fall outside their own district and churn. A snapped
   centre is a fixed place in the world.
3. The whole crowd is placed in the **core** — `round(28 × 0.66) = 18` blocks — and then leashed
   to the full 28. Tight in the middle, room at the edge.
4. Anyone found beyond `radius + 16` is discarded and replaced inside.

## How the people are arranged

The original arrangement is untouched: each knot is the spawner's own 2×2 offset pattern, three
blocks between neighbours — the same offsets that were always there. **Only the anchor moved,**
from a ring around you to a disc on the ground. 35 people now stand in 12 knots: four where there is
room, fewer when a knot has to fit beside its neighbours.

**One fix made this turn:** two knots could land on ground that was already taken, and two people
on one block render as *one* person. The spawner now redraws an anchor that comes within
`KNOT_CLEARANCE = 5` blocks of a knot already placed this cycle. Measured on the current draw:
35 people on **35 distinct blocks, 0 clashes**, nothing closer than 2.8 blocks. Suite 6 proves the
rule does work by showing what the same draw gives without it.

## Walking into it

Minecraft will not spawn a mob within 24 blocks of you, and that rule is kept exactly. So a crowd
is something you **walk into**: you approach the district, cross into it, and the people already
standing there stay — because vanilla's rule governs *spawning*, not *existence*. The close-up
panel draws both circles: the blue bubble where nothing may appear, and the red district just
beyond it. Nothing new spawns in front of your face; the square is already full when you arrive.

---

## What this cannot do

Forty real entities is where the curve stops, and it is not a cautious number: the popular NPC mods
report their comfort zone at 40–50 and degrade past it, and a 200-person colony halves a server's
tick rate. Everything in this document is real entities: they can be seen, named, talked to, and
killed — and at 40 of them the square is 25 m² per person, which is crowded without clipping.

A crowd of hundreds cannot be real entities. It would have to be virtual — this codebase has a
**dormant** path for exactly that (`populateMassive` plus `RadarProxyController`, ≤500 points),
and it is **not switched on**. That is the next piece of work if you want it; it is one system's
worth of effort, and it needs your go-ahead.

---

## Files

| file | what |
|---|---|
| `crowd_area_2d.html` | the 2D view — three panels, drawn from measured coordinates |
| `villager/PresenceSpawning.java` | vanilla's rules + district geometry (`spotInDisc`, `clearOf`, `coreRadius`, `snapToGrid`) |
| `villager/PresenceLocalSpawner.java` | the spawner: district choice, knot placement, the count loop |
| `verification/population/PresenceSpawningTest.java` | suite 6 — ring distribution, core containment, knot clearance |
| `verification/population/static_audit.py` | 116 checks, including "every bare call resolves to a method in the file" |

## Verdict in simple words

Before, the game gave you four people dotted far apart around you — it felt empty, because it was.
Now the people gather in one place: a square, a market, a temple yard, a village lane. You see a
cluster of figures standing near each other from a long way off, and when you walk in, they are
already there and they stay. Secunderabad in 1948 gets thirty-five of them; Hyderabad gets forty.
Forty is the ceiling for real, touchable people — and it is where the field's own NPC mods say
performance starts to go. Hundreds would have to be drawn, not spawned; that route now exists in
vanilla (display entities) and it is not started.

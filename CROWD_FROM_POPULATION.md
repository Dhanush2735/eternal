# How the population number becomes a genuine crowd

**2026-10-05.** Your question, answered and built: the resolver supplies **one number**, a documented
curve turns that number into a crowd, and the spawner places it under Minecraft's own rules. Four
entities was the old cap; a city now stands twenty-nine to thirty-two people in eight knots.

---

## The design, in one line

> The resolver gives the number. A curve turns the number into **how many, in knots of what size, in
> what role mix**. The spawner places them where vanilla would allow. The same number drives how loud
> the place is — so a place that resolves as a metropolis is both more crowded *and* louder, and the
> part that cannot be entities is carried by distance, unnamed faces and sound.

Five parts, each with a reason:

### 1. How many — the count curve

`SettlementDensity.npcCap(population, rank)`. Logarithmic, because a server dies long before a
thousand ticking entities, and because perception is logarithmic too. Floors and ceilings per rank:
**8/16/32** for village/town/city.

| place | population | rank | people | knots |
|---|---|---|---|---|
| Akhanpally Thanda, 1948 | 348 | village | **5** | 3 × 2 |
| Akanpalle, 2011 (real census) | 1,047 | village | **5** | 3 × 2 |
| Maheswaram mandal | 8,795 | town | **12** | 4 × 3 |
| Secunderabad, 1948 | 142,174 | city | **29** | 8 × 4 |
| Secunderabad, 2026 | 217,910 | city | **29** | 8 × 4 |
| Mohenjo-daro, −2500 | 22,993 | city | **25** | 7 × 4 |
| Hyderabad, 1948 | 980,600 | city | **32** | 8 × 4 |
| modern Hyderabad | 7,677,018 | city | **32** | 8 × 4 |

Why 32 as the ceiling and not 200: vanilla Minecraft runs **70 monsters and 10 creatures** around one
player and stays smooth. A human NPC is more expensive than a zombie — it strolls, looks at you,
wears armour layers, carries a name. 32 sits at about half of vanilla's most expensive category.

### 2. In knots — because a crowd is clumped

Vanilla spawns mobs in **packs** of two to four at one position. So does this: 2 per knot in a
village, 4 in a city. Thirty people spread evenly over a two-hundred-block ring would read as thirty
isolated figures, which is the opposite of a crowd; eight knots of four read as a market square.

### 3. Not all at once — arrival

`packsPerCycle` places at most four knots per cycle. A village is complete in one cycle; a
metropolis fills in two — **four seconds** — instead of thirty-two bodies appearing in a single tick.

### 4. In the right mix — composition

`roleSlots(29, 4)` → **8, 7, 7, 7**. The gazetteer's roles are kept as the composition of the crowd
(a market's merchants, a temple's priests) rather than one role repeated thirty times. The original's
rule — one person per role, no duplicates — becomes the *proportion* rule.

### 5. Placed where vanilla allows

Each knot gets its own anchor from `spawnAnchor`: **24 to 128 blocks** from the player, loaded chunk,
solid ground beneath, two blocks of clear space, no liquid. Then the original's own offsets arrange
the knot's members around the anchor — the arrangement this spawner has always used, relative to the
knot instead of to you. Despawn is unchanged: instant past 128 blocks, `1 in 800` per tick past 32
after 600 ticks away, and the event scene exempt like a name-tagged mob.

### And the sound rides the same scale

This is what makes 32 people read as a city of 142,174 rather than as 32 people. One number — the
same saturation the count curve uses — drives volume and how often the bed speaks:

| place | intensity | volume | speaks every |
|---|---|---|---|
| Akanpalle (village) | 0.50 | 0.24 | 80 ticks (4 s) |
| Secunderabad, 1948 | 0.86 | 0.36 | 51 ticks (2.5 s) |
| Hyderabad, 1948 | 1.00 | 0.40 | 40 ticks (2 s) |

Before this, the sound had two hard thresholds (100,000 people *or* 8 bodies; 15,000 *or* 3), so a
hamlet and a metropolis could sound identical whenever they landed on the same side of a number.

---

## What is in the code now

| file | what it does |
|---|---|
| `population/SettlementDensity.java` | the count curve, knots, arrival rate, role split, and the 0–1 perceptual scale |
| `villager/PresenceLocalSpawner.java` | reads the resolver once per cycle for the count; places people in packs under vanilla rules |
| `carto/AmbientCrowdSoundManager.java` | continuous bed from the shared scale |

The spawner consults the resolver for **exactly one thing — how many**, which is the arrangement you
asked for: the resolver supplies the number, the original spawner does the rest.

---

## The honest limit, and the next step past it

**Entities stop scaling here.** Not because 32 is a magic number — it is a tick budget — but because
the marginal body costs AI, collision and network traffic. Three ways past it, in increasing order of
work:

1. **Raise the ceiling.** `SettlementDensity.ceiling(...)`. One line. Vanilla's headroom above 32 is
   real; try 48 and watch the tick time. This is the whole of the change if 32 is not enough.
2. **Continuous sound + distance + no names** (built). At 120 blocks a knot is three pixels and
   audible; you cannot count what you cannot name.
3. **Proxies — the crowd that is not an entity.** The mod already ships the machinery:
   `StructOfArraysPopulationEcs` holds 32-byte agents, `RadarProxyController` collects the ones
   64–192 blocks out, `AgentMaterializationController` swaps agents into real entities when you
   approach. It is **not running in play**: `populateMassive` has one caller, and no client code
   renders a radar proxy. Wiring it would mean filling the ECS from the same population number
   (one agent per N residents), rendering proxies as distant silhouettes, and letting materialisation
   do the rest — a city would then show hundreds of figures merging into individuals as you walk in.
   That is the design the code was written for; it is a substantial piece of client rendering work,
   not a tweak. **I have not built it.**

I would do (3) only if you want crowds that are genuinely uncountable at range. Everything short of
that is now in place.

---

## Verification

`./verification/population/run.sh` — **eight suites, all pass.** Suite 5 now checks the real places:

```
Akanpalle, 2011 (real)     village   1,047  5      3 pack(s) of 2
Maheswaram mandal          town      8,795  12     4 pack(s) of 3
Secunderabad, 1948         city      142,174 29    4 pack(s) of 4
Hyderabad, 1948            city      980,600 32    4 pack(s) of 4
29 people over 4 roles -> 8 7 7 7
intensity: Akanpalle 0.50, Secunderabad 1948 0.86, Hyderabad 1948 1.00
DENSITY: ALL PASSED
```

`static_audit.py` — **CLEAN, 94 checks**, including the new wiring: `spawner reads the population
number`, `count comes from the density curve`, `people arrive in vanilla-style packs`, `the four-cap
is gone`, `sound rides the same 0..1 scale`, `the old sound thresholds are gone`.
`parse_check.py` — 19 modified files parse clean.

One test failure caught on the way, and it was mine: I asserted Akhanpally Thanda (348 people) renders
4; the curve says 5, and the curve is right — `2 + 6·(log₁₀348/6) = 4.54`.

---

## Verdict in simple words

**How I'd do it:** one number goes in, a crowd comes out.

The resolver says "142,174 people live here in 1948". A curve turns that into **29 people, in eight
knots of four, in a mix of four roles**, and the spawner puts them where Minecraft would put mobs —
between 24 and 128 blocks away, on real ground, a knot at a time. The same number sets how loud the
place is: a village whispers, a city of a hundred thousand murmurs constantly.

So a city now stands **29 to 32 people in eight knots** instead of four in one clump, a town stands
**12**, and a village of 348 stands **5** — all of them with different faces, real Deccan names, and
no name tags until you walk up close.

**The one thing this doesn't do:** make a hundred thousand visible bodies. Nothing can — a Minecraft
server cannot run a hundred thousand entities, and vanilla's own cap is 70. Past 32 people the crowd
is carried by sound, distance and being unnamed. If you want crowds that are genuinely uncountable at
range, the mod already has dormant machinery for exactly that — a virtual population with silhouettes
at distance that turn into real people as you walk in. Wiring it is real work, and I'd want your
go-ahead before starting it.

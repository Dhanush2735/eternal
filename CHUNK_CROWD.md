# the population, divided over the chunks of the place's area

The instruction was: **use PopulationResolver for the population number and divide it equally between
the chunks of the settlement's area** — their example, Secunderabad 1948 = 142,174, divided for each
chunk of Secunderabad's area. This is that approach, built, measured and wired into the mod.

The picture: **`chunk_crowd_2d.html`** (open it in a browser) — drawn from `verification/population/alternatives/chunk_crowd.txt`,
which is the run output of `ChunkCrowdProbe`, which calls `ChunkPopulation` itself.

---

## 1. The division, for real

`PopulationResolver` gives the number. The same data gives the area: the population pack's own
`radiusKm` for the place (Secunderabad 6 km, Hyderabad 8 km — the extents that come with the census
rows the number is built from). A new class, `population/ChunkPopulation`, puts the two together.

| place | population | extent | area | chunks | **people in a chunk** | **one person every** |
|---|---|---|---|---|---|---|
| Akhanpally Thanda, 1948 | 348 | 2.0 km | 12.6 km² | 49,087 | 0.007 | 190 m |
| Akanpalle, 2011 | 1,047 | 2.0 km | 12.6 km² | 49,087 | 0.021 | 110 m |
| Maheswaram mandal, 2011 | 8,795 | 3.0 km | 28.3 km² | 110,447 | 0.080 | 57 m |
| **Secunderabad, 1948** | **142,174** | **6.0 km** | **113.1 km²** | **441,786** | **0.322** | **28.2 m** |
| Secunderabad, 2026 | 217,910 | 6.0 km | 113.1 km² | 441,786 | 0.493 | 22.8 m |
| Hyderabad, 1948 | 980,600 | 8.0 km | 201.1 km² | 785,398 | 1.249 | 14.3 m |
| modern Hyderabad, 2026 | 7,677,018 | 8.0 km | 201.1 km² | 785,398 | 9.775 | 5.1 m |

The user's own case lands exactly: 142,174 ÷ 441,786 chunks = **0.322 people in an average chunk of
Secunderabad**, which is one person every 28 metres. A chunk is 16×16 blocks = 256 m², so that is
0.00126 people per m².

**Show both bases, because the data has two.** Secunderabad's 6 km is a disc of 113.1 km²; the same
row's 2011 census quotation gives the city's area as 22.81 km² (50,333 households). Divided over the
quoted area instead, the same population would be 1.6 people in a chunk of the *built-up* city. We
divide over `radiusKm`, the disc the pack states, because it is the only extent the pack states — and
the concentration in §3 is what stands the people in the built-up part of it rather than sprinkling
them across the fields.

## 2. The honest problem with dividing equally

An equal division is thin. A strip of forty chunks running out from the market, drawn by the code
itself (`chunk_crowd.txt`, `strip` lines):

| place | 40 chunks, equal division | same 40 chunks, gathered |
|---|---|---|
| Akhanpally Thanda, 1948 | 0 people | 1 |
| Akanpalle, 2011 | 0 | 4 |
| Maheswaram mandal, 2011 | 4 | 19 |
| Secunderabad, 1948 | 12 | **82** |
| Secunderabad, 2026 | 17 | **126** |
| Hyderabad, 1948 | 47 | **323** |
| modern Hyderabad, 2026 | 393 | **1,076** |

Two things follow, and both are real code, not commentary.

**Rounding must not eat the people.** 0.322 people in a chunk, rounded per chunk, is *nobody* in every
chunk — a hundred and forty-two thousand people become zero. So the whole people are handed out first
and the fraction is decided by the chunk's own stable hash: a chunk carrying 0.322 people shows one
person about a third of the time. Measured over the place's real 441,786 chunks
(`ChunkPopulationTest`): **142,295 people shown, 0.3221 a chunk** — the population, within a tenth of
a percent, and the same chunk always shows the same person.

**Where people are matters more than how many.** The mean is 0.322 and that is far too thin to look
like anything. `presenceCrowdConcentration` (default **40**) says how much of the population gathers
at the place's heart — the market, the fort, the point the place is named for. It moves people, it
never invents them: the shares are normalised, so the total is the population at every setting.

## 3. What a player standing somewhere actually gets

The two layers, stated plainly, because they are not the same number and neither is the other:

* **residency** — the resolver's population divided over the area: 0.322 people in an average chunk of
  Secunderabad. This is where people live.
* **the visible crowd** — the figures inside the player's 64-block view (`presenceCrowdInterestBlocks`,
  about 49 chunks). This is what the player sees.
* **the bridge between them** — concentration (the market's chunks carry 13 people each, the
  countryside's carry 0.322) and the drawing budget (`presenceDrawnFiguresMax`, 400), which thins the
  planned figures while keeping every chunk's share, so the crowd thins evenly rather than losing its
  far half.

| place | heart's chunk | view at the market | the same view, in the fields | drawn (cap 400) |
|---|---|---|---|---|
| Akhanpally Thanda, 1948 | 0 | 11 | 0 | 11 |
| Akanpalle, 2011 | 1 | 32 | 1 | 32 |
| Maheswaram mandal, 2011 | 3 | 127 | 4 | 127 |
| **Secunderabad, 1948** | **13** | **530** | **16** | **400** |
| Secunderabad, 2026 | 20 | 812 | 24 | 400 |
| Hyderabad, 1948 | 51 | 2,064 | 61 | 400 |
| modern Hyderabad, 2026 | 77 | 3,773 | 476 | 400 |

The concentration ladder for Secunderabad 1948, from the code:

| concentration | heart's chunk | the 64-block view | what that view is |
|---|---|---|---|
| 0 | 0 people | 16 people | the division itself: evenly spread, honest, and almost empty |
| **40 (default)** | **13** | **530** | a busy market — 400 drawn, the client's cap |
| 241 | 73 | 2,949 | the chunk packed full at crowd density — needs the recorded-mesh route |

So the equal division the user asked for **is** the model, and it works — the number and the area are
the same fact, the people are conserved, and a village of 348 people is not a crowd while a city of
142,174 is. What it cannot do on its own is put anybody in front of the player, because 0.322 in a
chunk is nobody in a chunk; concentration is the single knob that turns the honest average into a
market square, and the drawing budget decides how much of it is stood up.

**The map (`chunk_crowd_2d.html`, middle panel):** 61×61 chunks around Secunderabad's market, one cell
per chunk, coloured by the count the model puts in it — from the probe's own `cell` lines. 1,264 of
the 3,721 chunks in that window carry figures, 2,512 people in all: a bright market disc at 13 people
a chunk, a fading ring, and one person per chunk out in the fields.

## 4. What changed in the mod (a real change, wired end to end)

| file | change |
|---|---|
| `population/ChunkPopulation.java` | **new**: the division, the concentration, the seeded rounding, the per-chunk view, the budget sampler |
| `population/PopulationResolver.java` | `radiusKm(siteId, lat, lon)` — the place's extent, from the same lookup as its number (`Result` untouched) |
| `population/CrowdFigures.java` | rewritten: each figure belongs to a **chunk** (tags `crowdChunkX/Z`, `crowdFigurePlan`); the crowd is stood up chunk by chunk across the whole place instead of in one district disc; `sweep` forgets by the player's neighbourhood, not by the district |
| `villager/PresenceLocalSpawner.java` | reads the place's extent from the resolver, passes it to `ensure`, new interest knob |
| `config/EternalEngineConfig.java` | `presenceCrowdConcentration` (40, 0..400), `presenceCrowdInterestBlocks` (64, 32..160) |

The figures are still the same entity as before: the mod's own `HumanNpcEntity` with **no AI**
(`setNoAi(true)`), silent, invulnerable, no nameplate, never inside vanilla's 24-block bubble — the
drawing layer is unchanged in kind, only in *where* the crowd is. Nothing in the living-NPC path
changes: `PopulationResolver` still supplies the number and the living count is untouched.

## 5. The option space (listed before choosing, as asked)

| # | route | what it gives | why not / when |
|---|---|---|---|
| A | divide equally, stop there | the honest average: 0.322 a chunk | **measured**: 0 figures in a 40-chunk strip at the market — honest and empty |
| **B** | **divide equally, then gather by concentration** | **0.322 residency, 13/chunk at the market, 530 in view, 400 drawn** | **chosen** — the user's division, plus the one knob it needs |
| C | divide over the *built-up* polygon (22.81 km²) | 1.6 a chunk in the city proper | the history packs carry no polygons and the population pack carries no built-up extent — no data to divide over |
| D | keep the old single district disc | 377 figures in a 20-block square | what was already rejected: a crowd in one square, nothing anywhere else |
| E | divide over 4×4-chunk neighbourhoods | the same crowd, coarser | identical arithmetic, less control; chunk granularity is finer for free |
| F | concentration 241 + recorded mesh renders | the chunk packed to crowd density, 2,949 in view at 0.5–1.0/m² | the end state, and it needs the renderer from `HOW_I_WOULD_SOLVE_IT.md` route 4 — client code, not this change |
| G | divide per *household*, not per person | matches the census (50,333 households) | needs household extents the packs do not have; people are what the resolver resolves |

## 6. Cost

Nothing runs per chunk that is not near a player. The division is arithmetic over the chunks in the
view — **49 chunks** at the default 64-block interest radius — and the spawner walks those 49, not
441,786. Figures are placed ≤32 a cycle (the existing `PER_CYCLE`), only in chunks with nobody
standing in them, never in a player's 24-block bubble, and an unloaded chunk or a chunk with no
ground is skipped and retried next cycle. The figures have no AI, so they cost a body and nothing
else: **400 drawn figures × 23.8 µs = 9.5 ms** of a 60 FPS frame, which is what the 400 cap is for.
A bigger place does not cost more — the budget is per view, not per settlement.

## 7. Verification

* `verification/population/ChunkPopulationTest.java` — **suite 10**, all passed: 40 checks over the
  division, the conserved population, the chunk capacity, the seeded rounding (441,786 chunks →
  142,295 people), the sampler and the view.
* `run.sh` — **10 suites, 229 PASS, 0 FAIL** (unfiltered output in the run).
* `parse_check.py` — **23 files, 0 syntax failures**; `ChunkPopulation` is javac-compiled, and was
  shown as MODIFIED.
* `static_audit.py` — **CLEAN**: all wiring present, including the three new chain checks (division
  per chunk · thinning, not truncation · the extent from the resolver).

## 7a. Since this was written — the three requirements, answered

The same division is also answered against the three requirements as a standing reframe: see
**`THREE_REQUIREMENTS.md`** and the picture `three_requirements_2d.html`. What is new there: the
view's crowd now travels as a **recipe** — `ChunkPopulation.Manifest`, measured at **166 bytes** for a
64-block view, the same size whether it describes 13 figures or 3,465 — and the population-true
geometry is measured both ways: as entities, 3,465 figures cost 82.5 ms a frame (five frames of work);
as one recorded mesh, 124,740 quads, 15,593 KB, 31 chunk sections, one draw call.

## 7b. The reverse question — every place, current year

Field-by-field audit of what is stored: **`FIELD_GUIDE.md`**.


See **`PLACES_2026.md`** and the list `places_2026_2d.html`: all **15,954** places the project carries,
resolved for 2026, searchable and sortable. It found that the project has three *measured* anchors
(Hyderabad city, Secunderabad, Akanpalle) and 15,951 places modelled from them — 683 inherit a city's
whole population outright, 552 hold a million or more, and the three fallback tiers, not the census
series, are where the work now is.

## 8. Verdict in simple words

Yes — dividing the resolver's population over the chunks of the settlement's area is the right model,
and it is now the model the mod uses. Secunderabad 1948 is 142,174 people over 441,786 chunks: 0.322
people in an average chunk, one person every 28 metres, in a chunk of 256 m². That number is honest
and it is far too thin to be seen, for one reason worth saying out loud: **a city's people are not
spread evenly, they are gathered, and 0.322 in a chunk is nobody in a chunk.** So the division is the
*budget*, and one knob — how much of it gathers at the market — turns it into the crowd: at the
default the market's chunks hold 13 people each, a 64-block view holds 530, and the client draws 400
of them; walk out into the fields of the same town and the view holds 16. The settlement's area is
covered end to end, the population is conserved, the number still comes from PopulationResolver, and
nothing spawns beside the player. If the market at 13 people a chunk is still not a crowd, the ladder
says exactly where the ceiling is and what it costs: concentration 241 packs the chunk to its 77 and
puts 2,949 in the view — that is the recorded-mesh route, and it is renderer work, not arithmetic.

> Step 29 note: to save snapshot space the big list is now stored gzipped as `places_2026_2d.html.gz` (unzip it to open it in a browser).

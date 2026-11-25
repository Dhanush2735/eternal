# The average population of every place

Yes — all 15,954, and here they are. **The average population** of a place is the mean of the resolver's answers across the years that place existed, from its `occupiedStart` to 2026 (or to its end, whichever comes first), sampled on a grid of at most 64 points. It is a different number from the *average increase per year* in `CONSTANT_INCREASE.md`: that one is people per year, this one is people.

Computed by `verification/population/alternatives/AveragePopulationProbe.java`; the full list is `places_average_2026.tsv`, and the visual is `average_population_2d.html`.

## What the 15,954 averages look like

| | people |
|---|---:|
| smallest (deir_el_medina, -1550..2026) | 88 |
| 25th percentile | 180 |
| **median** | **219** |
| 75th percentile | 286 |
| 90th percentile | 466 |
| 99th percentile | 932,760 |
| largest (new_delhi, 1911..2026) | 2,894,594 |

**Summed, every place's average is 515,251,538 people** — the standing population of the modelled world, averaged over the time each place has existed.

| rank | places | median average | largest average |
|---|---:|---:|---:|
| village | 15,547 | 216 | 1,207,939 |
| town | 299 | 6,907 | 1,042,415 |
| city | 108 | 489,696 | 2,894,594 |

91% of the places average between 100 and 500 people. The villages that average more than a million are real rows — they are the Hyderabad and Secunderabad localities sitting inside a city's disc and inheriting its numbers.

## What each average rests on

| the samples that answered it | places |
|---|---:|
| on a census row | 341 |
| between census rows | 350 |
| model only | 15,263 |

This is the honest heart of the answer. The resolver can be asked for any year and it always answers — but for **15,263 of 15,954 places every sample comes from the model** (era bands, rank-size decay and gravity), not from a record. Another 350 sit between census rows of a region they fall inside. Only 341 places have a single sample answered by an actual census row, and nearly all of those are localities inside the Hyderabad or Secunderabad disc: the number is the city's, applied to the locality.

So: an average population exists for each and every place, and for 96% of them it is an average of modelled numbers. That is a real number with a real definition — and it inherits every strength and weakness of the model underneath it.

## Why the window matters (and what an average can hide)

The average depends on which years are counted, and the model's own behaviour inside those years is not smooth in the way a curve is:

* **Inside an era, the number ramps.** The floor pick hashes `(place, year)` with the year as the last term, so it moves one step of the band per year — about band width over 1,000. That is why `new_delhi` climbs from 2,611,411 to 3,177,778 across its window rather than jittering: an average over that ramp is stable and meaningful.
* **At an era boundary, it steps.** The band itself changes — for a city, 1800–1900 is 40,000–800,000 and 1900–present is 80,000–5,000,000 — so the number jumps by up to the difference in a single year.
* **When the rank changes, it leaps, and the average hides it.** Golconda is the clearest case in the project: occupied 1200..1687 with an urban window 1518..1687.

| Golconda | resolver says | tier |
|---|---:|---|
| 1500, village | 307 | era band, village 100–400 |
| 1680, city (inside the urban window) | 350,551 | era band, city 20,000–400,000 |

Its single average over 1200..1687 mixes 318 village-level years with 169 city-level years. The average is not wrong — it is literally the mean of the answers — but it describes no year the place ever had. The same is true of every average here: it is a summary of a history, and the history stays in the file.

## How to use these numbers

| if you want | use |
|---|---|
| the population of a place in a year | ask the resolver for that year — not this file |
| a single standing figure per place | the average here, with the rank and the window stated beside it |
| how fast a place grows | `CONSTANT_INCREASE.md` — people per year, a different column |
| to know whether to trust a number | the tier column: `sourced`, `interpolated`, `modelled`, `floor` |

## Verdict in simple words

Yes: every one of the 15,954 places now has an average population — 219 people for the median place, 216 for a median village, 6,907 for a median town, 489,696 for a median city, up to 2,894,594 for New Delhi across its window.

But the number deserves its label. For 15,263 places the average is an average of **modelled** values, because the project holds exactly 16 census numbers and only three places have any of them. It is exact where they exist and an artefact of the bands where they do not — and where a place changes rank inside its window, the average describes no year it ever had.

## Status: what is built

| piece | state |
|---|---|
| `AveragePopulationProbe.java` | **written, compiled, run** — 15,954 rows + summary |
| `places_average_2026.tsv` | **written** — window, samples, average, min, max, tier counts, source |
| `average_population_2d.html` | **written** — the distribution, the split, the step |
| `YearSlice.java` | **written, compiled, run** — one place, year by year |
| this document | **written** |
| the engine | **not changed.** No new field, no new file format: the average is an analysis of what `PopulationResolver` already answers. |

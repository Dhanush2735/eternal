# The constant increase, computed and tested

Reverse approach: instead of asking the resolver for a year, take today's number for every place and
work back to a single constant — **the average population increase per year** — then use it as
`people(place, year) = increase × (year − startYear)`.

Everything below is measured. The population comes from `PopulationResolver`; the start year from the
gazetteer (which fills an estimated start for every row that has none); the censuses from
`population_south_asia.json`, the only stored population in the project.

Produced by `verification/population/alternatives/ConstantIncreaseProbe.java`.
The list of all 15,954 places is `places_increase_2d.html`; the check is `constant_increase_2d.html`.

---

## 1. The only place "accurate" can come from: the 10 measured gaps

| region | from → to | years | from | to | +people/year | +%/year |
|---|---|---|---:|---:|---:|---:|---:|
| hyderabad | 1901 → 1931 | 30 | 448,466 | 466,894 | **614** | 0.13% |
| hyderabad | 1931 → 1941 | 10 | 466,894 | 739,159 | **27,226** | 4.70% |
| hyderabad | 1941 → 1951 | 10 | 739,159 | 1,106,880 | **36,772** | 4.12% |
| hyderabad | 1951 → 2001 | 50 | 1,106,880 | 5,742,036 | **92,703** | 3.35% |
| hyderabad | 2001 → 2011 | 10 | 5,742,036 | 7,677,018 | **193,498** | 2.95% |
| secunderabad | 1941 → 2001 | 60 | 135,370 | 206,102 | **1,179** | 0.70% |
| secunderabad | 2001 → 2011 | 10 | 206,102 | 217,910 | **1,181** | 0.56% |
| atraf_i_balda | 1881 → 1891 | 10 | 355,787 | 389,784 | **3,400** | 0.92% |
| atraf_i_balda | 1891 → 1901 | 10 | 389,784 | 420,702 | **3,092** | 0.77% |
| atraf_i_balda | 1901 → 1941 | 40 | 420,702 | 612,493 | **4,795** | 0.94% |

**How steady is "the constant" in each place?** The spread between the smallest and largest per-year
increase that place ever had:

| region | absolute increase | per-capita growth |
|---|---|---|
| secunderabad | **×1.002** (1,179 → 1,181) | ×1.26 |
| atraf_i_balda | ×1.55 | ×1.23 |
| hyderabad | **×315** (614 → 193,498) | ×35 |

This is the whole answer in one table. Secunderabad's growth really was a constant increase — 1,179
people a year for sixty years, then 1,181 a year for the next ten. Hyderabad's was not: its yearly
increase changed by 315× across its record, because the *rate* changed (0.13% a year before 1931,
4.70% after it).

---

## 2. The constant, for every place

`increase = resolve(place, 2026) / (2026 − occupiedStart)`

| the constant | people/year |
|---|---:|
| smallest | 0.0223 |
| 25th percentile | 0.0863 |
| **median** | **0.1113** |
| 75th percentile | 1.1809 |
| 99th percentile | 18,410 |
| largest (hyderabad_sindh) | 29,755.88 |

| rank | places | median constant | largest |
|---|---:|---:|---:|
| village | 15,547 | 0.1086 | 23,123.55 |
| town | 299 | 28.7091 | 20,748.70 |
| city | 108 | 2,766.73 | 29,755.88 |

**The sum of every place's constant is 8,964,268 people a year** — that is what the whole modelled
world gains annually under this model. 11,388 of the 15,954 places gain less than one person a year;
564 gain more than a thousand.

Where the numbers come from, per place:

| the tier that answered | places |
|---|---:|
| scaled from Secunderabad (rank-size by distance) | 10,358 |
| scaled from Akanpalle | 2,735 |
| scaled from Hyderabad | 1,283 |
| an era band (a range pick, no record in range) | 887 |
| inherits Hyderabad's own number | 475 |
| inherits Secunderabad's own number | 208 |
| the nearest record to it | 8 |

So 14,901 of the 15,954 constants are derived from **three** places' measured numbers. That is the
existing state of the data, not a new defect — but the constant model makes it visible, because every
one of those places now carries its own extrapolation.

---

## 3. Using it year by year

Summed over all 15,954 places, the constant model against what the resolver itself answers for those
years:

| year | constant model | resolver | ratio |
|---:|---:|---:|---:|
| 1800 | 1,949,848,553 | 56,278,638 | **34.6×** |
| 1900 | 2,840,941,869 | 477,661,620 | **5.9×** |
| 1948 | 3,270,160,971 | 730,689,138 | **4.5×** |
| 2000 | 3,736,223,544 | 2,938,240,593 | 1.3× |
| 2026 | 3,969,294,508 | 3,969,294,510 | 1.0× (equal by construction) |

Both sides agree today, because the constant was taken from today. Going back, the constant model
inflates the world: **1.95 billion people in 1800** where the resolver says 56 million. The reason is
structural, not arithmetic — a line anchored at a founding year of 1591 or −1346 puts population into
centuries that had none.

---

## 4. The accuracy test: the line against the censuses

`Secunderabad` — constant 973/year, start 1802:

| census | actual | constant line | gap |
|---|---:|---:|---:|
| 1941 | 135,370 | 135,221 | **−0.1%** |
| 2001 | 206,102 | 193,590 | −6.1% |
| 2011 | 217,910 | 203,318 | −6.7% |

Three censuses over 70 years, never more than 7% out. **This is the model working.**

`Hyderabad` — constant 17,648/year, start 1591:

| census | actual | constant line | gap |
|---|---:|---:|---:|
| 1901 | 448,466 | 5,470,978 | **+1120%** |
| 1931 | 466,894 | 6,000,428 | +1185% |
| 1941 | 739,159 | 6,176,911 | +736% |
| 1951 | 1,106,880 | 6,353,394 | +474% |
| 2001 | 5,742,036 | 7,235,810 | +26% |
| 2011 | 7,677,018 | 7,412,293 | −3.4% |

**This is the model failing**, and the failure has a cause you can point at: Hyderabad did not grow
until the twentieth century, so a line drawn from 1591 puts millions of people in 1901.

---

## 5. What happens if one constant is used for every place

The median constant is 0.1113 people a year. The place that needs it most is `hyderabad_sindh` at
29,755.88 a year — **267,338× larger**. One constant for all 15,954 places is not a simplification,
it is a different world.

Applied the other way — the biggest city constant to a village:

> **Amarna** starts at −1346. Grown on the largest city constant (29,755.88/year) for its 3,372
> years, it would hold **100,336,840 people** today.

---

## Verdict in simple words

A constant increase per year is **exactly right for places that grew steadily, and badly wrong for
places that did not** — and this project can tell you which is which, because it holds the censuses
that decide it. Secunderabad's censuses sit on a straight line to within 7% over seventy years;
Hyderabad's do not, off by 700% in 1941.

So the "very accurate average population increase" you asked for exists, but it is **per place, not
one number**: 0.11 people a year for a median village, 2,767 a year for a median city, and up to
29,756 a year for the largest — a 267,338× spread. Used per place it reproduces today exactly and the
past poorly; used as one global number it is nonsense for 15,953 of the 15,954 places.

The honest version of "constant increase" that survives its own test is the one the resolver already
uses between censuses: a constant **rate**, applied from a real starting number. The constant
*absolute* increase only wins where the census rows show a straight line — and that is checkable
before it is used.

---

## Status: what is built

| piece | state |
|---|---|
| `ConstantIncreaseProbe.java` | **written, compiled, run** — 15,954 rows + summary |
| `places_increase_2026.tsv` | **written** — every place, its start year, its constant, the tier that answered |
| `places_increase_2d.html` | **written** — the searchable list, 15,954 rows, sortable by the constant |
| `constant_increase_2d.html` | **written** — the two-city check, census points against the constant line |
| this document | **written** |
| the engine | **NOT changed.** `PopulationResolver` still interpolates log-linearly between records and falls back to the era bands outside them. Nothing in the mod reads a constant increase — the model exists in this analysis, not in the game. |

If the constant model should replace the resolver's interpolation, that is a change inside
`PopulationResolver.resolve` (a ramp per place instead of interpolation), and it would need the year
sweep re-run: this document already shows what it would do to 1948 and 1800.

> Step 29 note: to save snapshot space the big list is now stored gzipped as `places_increase_2d.html.gz` (unzip it to open it in a browser).

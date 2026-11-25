# Population Derivation Phase
## Getting approximate population wherever sourced data is incomplete or absent

---

## 1. The problem, stated plainly

Eternal Engine has **zero population data at the settlement level**. A grep for
`"population"` across all thirteen history packs returns no hits. Population is
tracked only per-civilization, as a single founding-era figure plus a peak year,
and 58 of the 101 civilization IDs that the history packs actually cite do not
exist as records at all.

The consequence is that standing in Secunderabad in 1948 resolves to a city that
is correctly named, correctly dated and correctly ranked — with **no population
whatsoever**. `PresenceLocalSpawner` then falls back to a hard cap of four NPCs,
because there is no number for the cap to be a function of.

This is not a data-acquisition problem. It is a **fallback-chain problem**. The
project already solved the identical problem for *years*: `SettlementYearEstimator`
derives an occupancy start for undated places from gravity, diffusion lag and
parent boxes, and labels the result `confidence=estimated`. Population needs the
same treatment, with the same discipline.

---

## 2. Policy — three rules

1. **Never zero where a place is occupied.** If a sourced site is occupied in
   year *Y*, a population exists. Returning 0 is a modelling failure, not an
   accurate statement.
2. **Never silently precise.** Every value carries a `source` and a
   `confidence`. Nothing is presented as a record when it is a derivation.
3. **Never invented upward.** Derivations are anchored to real sourced values.
   Where no anchor exists, the value is bounded by a documented default band and
   labelled `inferred` — never presented as a measurement.

The output of the resolver is therefore always a **value plus a label**:

```
population≈41200 (modelled: rank-size from Hyderabad anchor)
population≈180 (inferred: village floor band, 1800-1900)
```

---

## 3. The fallback ladder

`PopulationResolver.resolve(lat, lon, year, site)` walks the tiers in order and
returns the **first** tier that produces a value. Ordering mirrors the occupancy
precedence already used by `HistoricalPresence`.

| Tier | Name | Source | Method | Label | Coverage |
|---|---|---|---|---|---|
| T0 | **Sourced** | Curated census / gazetteer series | Direct lookup | `sourced` | The places you author |
| T1 | **Interpolated** | Two sourced points for the same place | Log-linear interpolation between years | `interpolated` | Places with 2+ sourced years |
| T2 | **Rank-size** | The settlement corpus itself | Zipf/rank-size fit across the local hierarchy, anchored to the nearest sourced city | `modelled` | Every occupied place |
| T3 | **Gravity** | Nearby dated cities | Weighted pull, scaled by rank and era | `estimated` | Every occupied place |
| T4 | **Gridded density** | HYDE / raster population | Density x catchment area | `gridded` | Any coordinate, any year |
| T5 | **Parent polity** | Civ record population | polity_pop x urban_ratio / settlement_count | `inferred` | Anywhere inside a civ's span |
| T6 | **Floor band** | Era x rank defaults (below) | Documented minimum band | `floor` | Always |

**T6 is the guarantee.** It cannot fail, so the resolver can never return nothing.

---

## 4. Tier 6 — the default bands (this is the "no data at all" answer)

When every other tier misses, the value comes from a documented era x rank band.
These are **calibration constants, not researched figures** — they exist to
produce a plausible order of magnitude and an honest label.

| Era | Village | Town | City |
|---|---|---|---|
| −3000 … −1000 | 40 – 150 | 500 – 2 000 | 5 000 – 30 000 |
| −1000 … 0 | 50 – 200 | 800 – 3 000 | 8 000 – 60 000 |
| 0 … 1000 | 60 – 250 | 1 000 – 4 000 | 10 000 – 100 000 |
| 1000 … 1500 | 80 – 300 | 1 500 – 6 000 | 15 000 – 150 000 |
| 1500 … 1800 | 100 – 400 | 2 000 – 10 000 | 20 000 – 400 000 |
| 1800 … 1900 | 150 – 700 | 3 000 – 20 000 | 40 000 – 800 000 |
| 1900 … present | 200 – 1 500 | 5 000 – 40 000 | 80 000 – 5 000 000 |

Within a band, the value is placed by a deterministic hash of `(siteId, year)` so
results are **stable across restarts and identical on every machine** — the same
discipline the rest of the mod already follows for divergence seeds.

---

## 5. Tier 2 in detail — deriving from data already shipped

This tier needs **no new dataset**. The corpus already contains a real settlement
hierarchy:

| Pack | Villages | Towns | Cities | Total |
|---|---|---|---|---|
| `present_telangana.json` | 13 156 | 117 | 14 | 13 287 |
| `present_hyderabad_metro.json` | 1 666 | 173 | 2 | 1 841 |

That ratio *is* a rank-size distribution. Method:

1. Rank the occupied settlements in the region by `rank`, then by distance to the
   nearest sourced city.
2. Fit `population(r) = P1 / r^alpha` with `alpha` in the usual 0.8–1.1 band,
   constrained so village/town/city counts reproduce the observed ratio.
3. Anchor `P1` to the nearest **sourced** city population if one exists; otherwise
   defer to Tier 3.
4. Tag every derived value `modelled`.

Effect: all 15 000+ named places get a stable, internally consistent number
immediately, and the ratio between a village and its regional capital is
realistic rather than arbitrary.

---

## 6. Data format

New directory: `data/eternalengine/eternal/population/`

```json
{
  "id": "population_south_asia",
  "title": "Sourced population series, South Asia cores",
  "note": "Sourced rows only. Everything else is derived by PopulationResolver and labelled.",
  "defaults": {
    "note": "Era x rank fallback bands. Calibration constants, not records.",
    "bands": [
      { "fromYear": -3000, "toYear": -1000, "village": [40, 150],
        "town": [500, 2000], "city": [5000, 30000] }
    ]
  },
  "regions": [
    {
      "id": "hyderabad",
      "lat": 17.3616,
      "lon": 78.4747,
      "radiusKm": 15,
      "series": [
        { "year": 1591, "population": 0,    "source": "founding, Charminar [verify]" },
        { "year": 1941, "population": 0,    "source": "Census of India 1941 [verify]" }
      ]
    }
  ]
}
```

Rules for authors:
- Only put a row in `series` if it can be attributed. Anything unattributed goes
  in `bands`, not `series`.
- `source` is mandatory on every series row. Empty source = the row is rejected
  at load with a logged error, exactly as `HistoricalEventDatabase.validateChains`
  rejects broken chains.
- Zero or missing `population` on a series row is treated as **unset**, never as
  the value zero.

---

## 7. Integration points

| File | Change | Purpose |
|---|---|---|
| `population/PopulationResolver.java` | **New** | The ladder. Mirror of `SettlementYearEstimator`. |
| `civilization/HistoricalPresence.java` | Extend `Result` with `population()` + `populationConfidence()` | One query returns who, when, and how many |
| `villager/PresenceLocalSpawner.java` | Cap becomes `f(population, rank, era)` instead of `spawned < 4` | Big places spawn more people |
| `carto/AmbientCrowdSoundManager.java` | Trigger on the **resolved population**, not `nearbyNpcs.size()` | A 100 k city sounds like one while 7 NPCs stand there |
| `population/PopulationEngine.java` | Seed per-place regions at load; keep civ-level as the aggregate | Real per-place simulation instead of civ-level only |
| `commands/EternalEngineCommands.java` | `/ee who` prints `population≈X (label)`; new `/ee population explain <lat> <lon> <year>` | Visibility and debuggability |
| `ecs/AgentVirtualizationEngine.java` | Auto-seed from resolved population at the player's location | Fills the engine that is currently manual-only |

---

## 8. Phases

| Phase | Work | Effort | Unlocks |
|---|---|---|---|
| **P0** | `PopulationResolver` skeleton + floor bands + `/ee who` shows `population≈X (floor)`. Civ table auto-synthesised from dangling `civHint`/`civId` references, years taken from matching border boxes. | ½ day | **No location ever reports zero again.** 58 missing polities resolved. |
| **P1** | Tier 2 rank-size fit from corpus | 1–2 days | Every one of the 15 000+ places gets a consistent number |
| **P2** | Wire crowd cap + crowd sound to the resolved number | 1 day | 1948 Secunderabad *feels* populated |
| **P3** | Curated census series for N key cities (Tier 0/1) | 2–3 days | Real anchors; every derived value gets more plausible |
| **P4** | HYDE/gridded tier (Tier 4) | 3–5 days | Any coordinate, any year, including prehistory and empty countryside |
| **P5** | Validation harness + regression fixtures | 2 days | Confidence in the approximations |

P0 alone changes the behaviour you observed. P0–P2 is roughly a week and delivers
the visible result.

---

## 9. Validation — how to know the approximations are sane

Invariants, checked in a unit test over the whole corpus:

1. **Occupied implies positive.** Every occupied site with a resolved population
   has `population > 0`. No exceptions.
2. **Rank monotonic.** Within one region and year, `city > town > village`.
3. **Bounded growth.** No adjacent-year change exceeds a factor of 1.5 unless an
   event or modifier explains it.
4. **Precedence.** A `sourced` value always beats a `modelled` one for the same
   place and year.
5. **Determinism.** Resolving the same `(id, year)` twice returns byte-identical
   results.

Accuracy, measured honestly:

**Hold-out testing.** Take the curated Tier-0 points, hide 20 %, run the resolver,
and report **median absolute percentage error per tier**. Expected shape of the
result:

| Tier | Expected accuracy |
|---|---|
| T0 sourced | exact by construction |
| T1 interpolated | a few percent within a decade |
| T2 rank-size | order of magnitude right (factor 2–3) |
| T3 gravity | rough (factor 3–5) |
| T4 gridded | depends on raster, usually factor 2 |
| T6 floor | a *presence*, not a measurement |

That table is the deliverable of P5, and it should be published in the repo so
nobody mistakes a `floor` value for a census.

Regression fixtures to pin permanently:

```
Secunderabad  1948   -> city,   should be order 10^5
Mohenjo-daro -2500   -> city,   should be order 10^4
Rakhigarhi   -3000   -> village, should be order 10^2
Haryana      -250    -> village (Maurya hinterland)
Hyderabad     0      -> village (Satavahana border)
```

---

## 10. What this buys

After P0–P2, standing in Secunderabad in 1948:

- the city belongs to a polity that exists, with a name pool
- it has a population number, labelled with how it was derived
- the number of people you meet scales with that number
- the street sounds like a city of that size

None of it is a census. All of it is anchored, labelled and reproducible — which
is the same standard the project already holds itself to for years.

---

# Appendix A — Worked example: Akhanpally, 1948

The test case: a village the corpus knows about but no source counts.
Latitude 17.088, longitude 78.389 (revenue village) and 17.090/78.390 (hamlet).

## A.1 What is on record

| Item | Value | Year | Source type |
|---|---|---|---|
| Akanpalle village population | **1,047** (546 M / 501 F), 256 households | 2011 | Census of India (village code 574779) |
| Akanpalle SC share | 341 (33 %) · ST share | 313 (30 %) | 2011 | Census |
| Mandal (Maheswaram) population | 65,125 across 33 villages | 2011 | Census |
| Nearby Nagaram (same mandal) | 3,379 → 3,948 | 2001 → 2011 | Census (local decadal rate +16.8 %) |
| **Atraf-i-Balda district** | **612,493** (83.66 % Hindu, 12.90 % Muslim) | **1941** | Census of Hyderabad State 1941 |
| Atraf-i-Balda district | 420,702 across 847 villages | 1901 | Imperial Gazetteer |
| Asafnagar taluk | 56,928 across 97 villages | 1901 | Imperial Gazetteer |
| Hyderabad State | 16,338,534 | 1941 | Census of Hyderabad State 1941 |
| Telangana region | 8,711,866 | 1941 | Census of Hyderabad State 1941 |

Administrative position in 1948: **Atraf-i-Balda district, Hyderabad State** — the
Nizam's *sarf-i-khas* crown district around the capital. It ends in September 1948,
merged into the new Hyderabad district (the ~1948 Police Action). Chain: Atraf-i-Balda
→ Hyderabad district (1948) → Hyderabad Rural renamed **Ranga Reddy** (1978) →
Maheswaram mandal today.

So the village does not move. Only the label above it changes, twice, in the same year.

## A.2 Deriving 1948 — two independent methods

**Method A — back-cast from the 2011 census using real local decadal rates.**
Starting at 1,047 (2011) and stepping back with regional rates (16.8 % for
2001–2011, measured on a village in the same mandal; ~18–22 % per decade earlier):

```
2011  1,047
2001    896
1991    760
1981    633
1971    519
1961    425
1951    370
1941    339
1948   ~356      (+4.9 % for the seven years, Hyderabad State 1941→1951 rate)
```

**Method B — district average, scaled by relative size.**
Atraf-i-Balda 1901: 420,702 across 847 villages = **497 per village**.
District growth 1901→1941: ×1.456 → **723 per village in 1941**.
Akanpalle sits at 0.531 of its mandal's average village size (1,047 vs 1,974), so:

```
1941  ≈ 723 × 0.531 =  384
1948  ≈ 384 × 1.049 = ~402
```

**Convergence: 356 (Method A) and 402 (Method B) — a band of roughly 350–400.**

Point estimate for 1948: **≈ 380 people, about 70–80 households.**
(At 5–6 persons per household, larger than today's 4.1, which is normal for 1948.)

## A.3 What the fallback band would have said

The village floor band for 1900–present is **200–1,500**. The derived value of ~380
falls inside it, and the district-level check agrees: 612,493 people across the
villages of the district give an average per-village figure of the same order.

So for this case the floor band would have been **adequate but unnecessary** — two
real anchors exist and bracket the year. The band is the guarantee for the places
where no such anchors exist, not the primary method here.

## A.4 What the mod currently reports

| | Mod today | Derived reality |
|---|---|---|
| Resolved occupant | none (no civ record for `india` / Hyderabad State) | Atraf-i-Balda, Hyderabad State → India, Sept 1948 |
| Population | **0** (no region exists to hold a number) | **≈ 380** |
| NPCs spawned | 1 (village → farmer) | 1 of ~380 |
| Founding year | estimated 1503 | village attested by Golconda-era temples (1518–1687) |

## A.5 Why this case matters

This is the cleanest demonstration of the whole policy. The village is:

- **known** to the corpus (an OSM named place, one of 13,287 in Telangana);
- **undated** by any source, so its founding is already estimated by gravity;
- **uncounted** at village level before 2001, yet still **derivable within ±15 %**
  for 1948 from a district census, an Imperial Gazetteer village count, and one
  neighbouring village's decadal rate.

The label for the 1948 value is therefore `estimated`, and the derivation is
reproducible from cited figures. Nothing was invented.

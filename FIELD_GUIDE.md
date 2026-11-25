# the field guide — every field, what it holds, and who reads it

> **There is a fuller version of this document:** `FIELD_DOSSIER.md` — all 96 fields at all 12 levels, with computed coverage, every value or the top values, ranges, defaults, and the call-site line that reads each one. Browsable as `field_dossier_2d.html`.


> What the fields *should* be — the schema these packs would need to be beyond argument — is in
> **`PERFECT_JSON.md`**, with the executable schema at `schemas/eternal-pack-v2.schema.json` and a lint
> that already fails six rules on the data described below.

Audited from the shipped packs: **11 history packs** (13 site fields, 4 record types), **1 population
pack** (regions and their census rows), and the pack-level blocks. Every count below is the real number
of rows carrying that field; every "who reads it" is a real call site.

Two numbers first, because one of them corrects an earlier one:

* **15,954 place rows, but 15,129 distinct places.** 825 ids appear in two packs — all 599 of
  `present_kamareddy`'s places are also inside `present_telangana`, and 226 of
  `present_hyderabad_metro`'s are too. The packs overlap by design (one district broken out of a
  state), but nothing deduplicates them, so a district place is counted twice in the list.
* **Not one place row stores a population.** The project's entire stored population data is **16
  numbers** under **6 regions**, in one file.

---

# A. the place row — 13 fields, 15,954 rows

Loaded by `HistoryGazetteer.loadPack` (line ~490) into the `Site` record (line 57).

| field | rows | holds | values actually present | when it is missing |
|---|---|---|---|---|
| **`id`** | 15,954 (100%) | the key: what the resolver is asked with, what traits attach to | 15,129 distinct (825 shared between two packs) | defaults to `"site"` — every anonymous row would share one identity |
| **`lat`** | 15,954 (100%) | latitude, decimal degrees | 14,746 distinct; Andhra/Telangana cluster 13–19 N | **row is dropped** — a site without lat/lon is skipped |
| **`lon`** | 15,954 (100%) | longitude, decimal degrees | 14,691 distinct; 77–79.5 E for the cluster | as above |
| **`occupiedEnd`** | 15,954 (100%) | the year the place stopped being occupied; **9999 = "still there"** | 82 distinct; 15,826 are 9999, the rest 400 · 1200 · 800 · -1900 … | defaults to `Integer.MAX_VALUE` — "forever" |
| **`civHint`** | 15,954 (100%) | which polity/culture it is attributed to — a *hint*, not a government | 101 distinct: `india` 15,729 · `indus_valley` 14 · `egypt_ancient` 11 · `mughal` 10 · `sumer` 8 · `maurya` 7 | falls back to the **pack id** |
| **`confidence`** | 15,954 (100%) | how well the row itself is known | 3 distinct: `estimated` 15,727 · `high` 156 · `medium` 71 | `"sourced"` for historical packs, `"estimated"` for present ones |
| **`rank`** | 15,896 (99.6%) | village / town / city — and it **wins** over the urban window | `village` 15,426 · `town` 331 · `city` 139 | computed: city if inside `urbanStart..urbanEnd`, else village; famous undated sites default city |
| **`note`** | 15,824 (99.2%) | one sentence of provenance | 93 distinct, mostly one per pack: *"OSM named place in Telangana. Start year estimated at load"* (13,287), *"OccupiedEnd extended: the named place is still a city"* (8) | absent — no note field on the record |
| **`name`** | 15,727 (98.6%) | display name | 11,685 distinct; repeats: `Lambadi Thanda` ×59, `Venkatapur` ×43, `Rampur` ×35 | falls back to the **id** |
| **`partOf`** | 15,727 (98.6%) | which container it belongs to | `telangana` 13,287 · `hyderabad_metro` 1,841 · `kamareddy` 599 · **`ramareddy` 2** (a stray — nothing else uses that name) | empty list |
| **`occupiedStart`** | **227 (1.4%)** | the year it began | 141 distinct: -300 ×13, -600 ×8, -3000 ×7, -500 ×6, 800 ×5 … | **present packs get `UNKNOWN`**; historical rows without it are **dropped** unless the pack is present-only. Never "beginning of time", by design |
| **`urbanStart`** | 12 (0.08%) | when it became a town/city | -2600 ×7, -2400, -2800, 1518 … | `null` — rank then comes from `rank` or the default |
| **`urbanEnd`** | 12 (0.08%) | when it stopped being one | -1900 ×8, -500, 1687 … | `null` |

**Added by the loader, not stored per row** — identical for every site in a pack:

* `packId` — which pack it came from (used for the fallback `civHint`).
* `radiusKm` — **the pack's `spawnRadiusKm`: 30 km for the historical packs, 8 km for the three present
  packs.** This is a *spawn* radius, not the settlement's area. Note the trap: the `Site` record
  declares a `radiusKm` field, and the JSON never sets it, so every site in a pack shares one number.
* `presentOnly` — from the pack's flag (`true` for the three present packs; a site can also set it).
* `name` — filled from the id when absent.

**Declared but never present in any pack:** a per-site `radiusKm`, a per-site `presentOnly`, a
population, an area, households, or any census year. `HistoryGazetteer.Site` declares `radiusKm`; no
pack writes it.

---

# B. the event — 15,167 records, three different shapes

The largest dataset in the project after the places, and the least known. Loaded into the `Event`
record (line 115).

| shape | records | fields | example |
|---|---|---|---|
| **full** (`events_world.json`) | **14,934** | `id, name, lat, lon, start, end, kind, effect, radiusKm, confidence` | a Wikidata battle: `kind: battle`, `effect: military_presence`, `radiusKm: 20`, `confidence: wikidata` |
| **short** (`events.json` + the 8 regional packs) | 233 | `id, name, lat, lon, year, kind, effect, radiusKm, confidence` | a curated event with one year |
| **minimal** | 10 | `id, name, lat, lon, year` | — |

Value distributions across all of them:

| field | values |
|---|---|
| `kind` | `battle` 8,035 · `siege` 2,404 · `disaster` 2,294 · `incident` 2,222 · `political` 61 · (none) 10 · `military` 1 |
| `effect` | `military_presence` 10,423 · `lockdown` 3,143 · `memorial` 1,373 · `political` 61 · `disruption` 17 |
| `radiusKm` | `20` 8,001 · `15` 2,410 · `30` 2,255 · `8` 2,210 · `12` 65 · `25` 55 |
| `confidence` | `wikidata` 14,934 · `high` 83 · (none) 10 |

**Who reads it:** `HistoryGazetteer.nearestEvent(lat, lon, year)` — a place within an event's
`radiusKm`, in its active years, gets the event; the spawner then uses it for the event scene and
`PlayerActionDetector` uses it to gate player actions. `activeIn(year)` accepts either a
`start..end` window or a single `year`.

---

# C. the trait — 51 records

| field | holds | values |
|---|---|---|
| `id` | the trait's name | 51 traits |
| `kind` | what sort of thing it is | `architecture` 32 · `invention` 10 · `introduction` 8 · `discovery` 1 |
| `from` / `until` | the years it is active | — |
| `sites` | the **site ids** it attaches to | list of ids — this is what ties traits to places |
| `note` | 5 of the 51 carry one | — |

**Who reads it:** `traitsFor(siteId, year)` → `spawnRoles(site, year)`, which is what gives a place's
inhabitants their *roles* (a pottery site spawns potters, and so on). So a trait is the only thing in
the packs that changes **what people do**, and it is the only consumer of `id` as a join key.

---

# D. the border — 16 polity boxes

| field | holds | example |
|---|---|---|
| `id`, `civId` | the box and its polity | `maurya_north_india` / `maurya` |
| `kind` | always `box` | — |
| `start` / `end` | the years the polity held this box | -322 … -185 |
| `minLat` / `maxLat` / `minLon` / `maxLon` | the rectangle | 21.0–34.5 N, 70–88 E |
| `rank` | what the ground is treated as inside the box | **`village` on all 16** — quiet ground, no invented cities |
| `note` | the caveat | *"Gangetic + northwest core (includes Haryana). Not the far south."* |

**Who reads it:** `borderAt(lat, lon, year)` → `HistoricalPresence`, which uses it as the last tier of
context: inside Maurya's box in 250 BCE, the game knows which polity you are standing in without
claiming a settlement there.

---

# E. the region and its census row — the only place a population is stored

`population/population_south_asia.json`. **6 regions, 16 rows.**

| region field | holds | values |
|---|---|---|
| `id` / `name` | the key and the label | `hyderabad` / "Hyderabad city" |
| `kind` | what sort of thing it is | `settlement` 3 · `aggregate` 2 · `polity` 1 |
| `rank` | its rank | `city` 3 · `town` 2 · `village` 1 |
| `radiusKm` | **its extent** — the only real area in the project | Hyderabad 8 · Secunderabad 6 · Akanpalle 3 · Maheswaram 20 · Atraf-i-Balda 90 · Hyderabad State 250 |
| `lat` / `lon` | the centre used by the region lookup | 17.3616/78.4747, … |
| `settlementCount` | how many places the aggregate covers | Maheswaram 33 · Atraf-i-Balda 847 |
| `startYear` / `endYear` | for a polity, its life | Hyderabad State 1724–1948 |
| `series` | the census rows themselves | see below |

Each row inside `series`:

| field | holds |
|---|---|
| `year` | the census year |
| `population` | the figure |
| **`source`** | the attribution, verbatim — *"Census of India 1941, Hyderabad district Secunderabad block: 102,147 Hindu + 21,552 Muslim + 11,671 other"* |

**The 16 rows, in full:**

| region | year | population | what the source says |
|---|---|---|---|
| Hyderabad city | 1901 | 448,466 | Census of India 1901 |
| | 1931 | 466,894 | Census of India 1931 |
| | 1941 | 739,159 | Census of India 1941 Vol XXI, the Nizam's Dominions |
| | 1951 | 1,106,880 | Census of India 1951 |
| | 2001 | 5,742,036 | 2001, Hyderabad urban agglomeration |
| | 2011 | 7,677,018 | 2011, Hyderabad urban agglomeration |
| Secunderabad | 1941 | 135,370 | 1941 census block: 102,147 Hindu + 21,552 Muslim + 11,671 other |
| | 2001 | 206,102 | 2001, Secunderabad Cantonment Board civil population |
| | 2011 | 217,910 | 2011, Cantonment Board (22.81 km², 50,333 households) |
| Akanpalle | 2011 | 1,047 | 2011 village code 574779: 546 M / 501 F, 256 households |
| Maheswaram mandal | 2011 | 65,125 | 2011, Ranga Reddy (33 villages, 14,684 households) |
| Atraf-i-Balda | 1881 | 355,787 | Imperial Gazetteer of India 1909 |
| | 1891 | 389,784 | Imperial Gazetteer 1909 |
| | 1901 | 420,702 | Imperial Gazetteer 1909 — 847 villages |
| | 1941 | 612,493 | Census of India 1941 (Hyderabad State) |
| Hyderabad State | 1941 | 16,338,534 | Census of India 1941 Vol XXI — a **polity**, not a place |

**Who reads it:** `PopulationResolver.resolve(siteId, lat, lon, year, rank)` — find which region the
point falls in, take an exact row if the year matches, else log-interpolate between the two bracketing
rows (if within 60 years), else fall back to the tiers.

---

# F. the pack-level blocks — where the model's constants live

Not per place, but they decide what a place gets:

| block | holds | why it matters |
|---|---|---|
| `id`, `title` | the pack's identity | |
| `presentOnly` | `true` for the three present packs | decides the default `confidence` and how ranks are assigned |
| `spawnRadiusKm` | **30** (historical) / **8** (present) | becomes every site's `radiusKm` — a spawning radius, not an area |
| `comparedSources` | which sources the pack was compared against | provenance |
| `rejected` | sources/rows deliberately not used | provenance — tells you what was *not* taken |
| `phases` | cultural phases with years | used for civ attribution |
| `unknownOnPurpose` | what the pack refuses to guess | an explicit honesty list |
| `note` (population pack) | *"SOURCED ROWS ONLY… Everything the resolver returns that is NOT in this file is derived"* | the contract of the file |
| `boundaryCaveat` | *"City figures change meaning across decades as municipal boundaries grow"* | why Hyderabad 1941 ≠ Hyderabad 2011 as a measurement |
| `defaults.bands` | era × rank ranges, e.g. 1800–1900 city 40,000–800,000 | the FLOOR tier: *"CALIBRATION CONSTANTS, not records"* |
| `calibration.rankUnits` | village 1.0 · town 26.0 · city 2500.0 | how a number is scaled across ranks |
| `calibration.gravityDecayKm` 250 · `gravityRadiusKm` 250 · `maxInterpolationGapYears` 60 | the model's constants | *"Not data; changing them changes every modelled value"* |

---

# Verdict in simple words

Every place row holds **where it is, how long it lasted, who it is attributed to, and how sure the
author was** — plus a name and a parent for the three present packs. Nothing else. That is 13 fields,
and only four of them are ever filled for the historical packs (a start year is missing from 98.6% of
all rows).

Everything that *looks* like data about people — the populations — lives in one file: **6 regions, 16
census rows**, each row a year, a number and the sentence it came from. Around those six numbers sit
the model's constants: the era bands, the rank units, the 60-year interpolation allowance, the 250 km
gravity radius.

So the honest reading of "what is stored" is: **geography and history are stored, person by person and
place by place; populations are stored sixteen times; and everything else the game shows about how many
people live anywhere is a model on top of those sixteen numbers.**

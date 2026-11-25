# every place the project carries, with its 2026 population

The reverse of the usual question: instead of asking what one place holds, this asks **every site the
project has** what the resolver says about it for the current year.

* **The list:** `places_2026_2d.html` — all **15,954** places, searchable and sortable, with the tier
  that produced each number stated in the row. (6.9 MB: it is a real table, not a picture.)
* **The raw table:** `verification/population/alternatives/place_inventory_2026.tsv` — the same rows,
  tab-separated, printed by `PlaceInventoryProbe` which calls `PopulationResolver` itself.
* **The year:** 2026, the current year in the game's clock terms. Any year works — the probe takes it
  as an argument (`java -cp … PlaceInventoryProbe 1948`).

---

## The list

**15,954 place rows — 15,129 distinct places**, because 825 ids appear in two packs (all 599 of
`present_kamareddy`'s places also sit inside `present_telangana`, and 226 of the metro pack's do).
Every count on this page is rows, unless it says places.

From 11 packs: `present_telangana` 13,287 · `present_hyderabad_metro` 1,841 ·
`present_kamareddy` 599 · `south_asia` 147 · and seven world packs totalling 80. Plus 16 polity boxes
in `borders.json`, which are not places and are not resolved.

**1,637 different numbers** between them. Not 15,954 different numbers — and that is the finding.

## How each number was made

Read from the resolver's own source string for each row, so this table is a measurement, not a
classification:

| how the number was made | places | what it means |
|---|---|---|
| scaled down from Secunderabad by distance | **10,358** | a Telangana village is a fraction of Secunderabad's 217,910 |
| scaled down from Akanpalle by distance | **2,735** | the same, with a 1,047-person hamlet as the anchor |
| scaled down from Hyderabad city by distance | **1,283** | a town 86 km away is 5.7 million people |
| inherits Hyderabad city outright | **475** | every place inside the 8 km disc holds the city's 7,677,018 |
| inherits Secunderabad outright | **208** | every place inside the 6 km disc holds 217,910 |
| an era band for villages | **788** | a range pick: "a presence, not a measurement" |
| an era band for cities or towns | **114** | a range pick, and these are the ones that overshoot |
| nearest record to it | **8** | a real record held inside the gap allowance |

**Not one of the 15,954 places resolves from its own census record.** The three anchors are Hyderabad
city, Secunderabad and Akanpalle; everything else is a distance from one of them.

## The twenty largest numbers, and who holds them

| number | held by | place | how it was made |
|---|---|---|---|
| **7,677,018** | **475 places** | Abids (and 474 more) | inside Hyderabad's 8 km disc — the city's own number |
| 5,716,214 | 1 | Mahbubnagar | rank-size from Hyderabad city, 86 km, decay 0.74 |
| 5,628,579 | 1 | Nalgonda | rank-size from Hyderabad city, 91 km |
| 5,080,730 | 1 | Miryalaguda | rank-size from Hyderabad city, 128 km |
| 4,955,676 | 1 | mathura | era band for cities 1900..present |
| 4,926,126 | 1 | nashik | era band |
| 4,891,652 | 1 | ujjain | era band |
| 4,842,402 | 1 | vellore | era band |
| 4,738,979 | 1 | kollam | era band |
| 4,734,054 | 1 | trichy | era band |
| 4,645,405 | 1 | mysore | era band |
| 4,606,006 | 1 | varanasi | era band |
| 4,514,475 | 1 | kalaburagi | rank-size from Hyderabad city, 175 km |
| 4,503,004 | 1 | kurnool | rank-size from Hyderabad city |

The pattern in one line: **the biggest numbers in the list are the least resolved.** Seven of the
fourteen are era bands, four are distance decay from a city a hundred kilometres away, and the single
biggest is inherited 475 times over.

## The three things this list exposes

1. **A city's population is handed to every locality inside it.** 475 places in the Hyderabad metro
   pack — Abids, Afzalgunj, AC Guards, Adarsh Nagar … — all show **7,677,018**. A neighbourhood of
   20,000 people is told it holds the whole city's population. The same for the 208 places inside
   Secunderabad's 6 km disc: all 217,910.
2. **Distance decay puts millions in district towns.** Mahbubnagar **5,716,214**, Nalgonda
   **5,628,579**, Miryalaguda **5,080,730** — places whose real populations are in the hundreds of
   thousands. 552 places in the list hold a million or more.
3. **Era bands overshoot, and can be read two ways for the same place.** Secunderabad in 1840 is
   **1,050,000** as a city (INFERRED, from the Atraf-i-Balda district average scaled by rank) and
   **10,920** as a town — and 1,050,000 is larger than the same place's own 2026 number. Mathura as a
   city in 2026 gets 4,955,676 from an era band while its own census future record is far smaller.

## What each of those is worth fixing

(The format-level version of this list — six rules, an executable schema and a migration order — is
**`PERFECT_JSON.md`**.)

| defect | places affected | fix |
|---|---|---|
| city total handed to every locality inside it | 683 | divide the city's number over its localities — the chunk division already in `ChunkPopulation`, applied to the metro pack |
| decay producing city-sized numbers for towns | 552 hold ≥1M | cap decay by the place's own rank and the pack's own settlement size; a village anchor should not produce a 5-million town |
| era bands overshooting a place's own record | 114 city/town bands | clamp any band answer by the place's own first/last census record — a place cannot hold more before its first record than at it |

None of the three is a teleport problem or a crowd problem: they are all in the **fallback tiers**, and
all three are the same mistake in different clothes — *a number that belongs to one place being used
for another*.

## What the rows actually store

Every field of every record type, with its values and the call site that reads it: **`FIELD_GUIDE.md`**.


Audited field by field across all 15,954 rows: see **`place_fields_2d.html`** and
`verification/population/alternatives/place_fields.txt`. In short — a place row stores **where it is**,
**how long it lasted**, **who it is attributed to**, and for the present packs its **name** and the
**city it is part of**. It stores **no population**, no area, no households, and no census year of its
own. Only 227 of the 15,954 rows (1.4%) even carry an `occupiedStart` — the three present packs hold
15,727 places with no start date at all. The numbers live in one file: **16 census rows under 6
regions**, each region carrying its own extent.

## Verdict in simple words

The list is real: 15,954 places, each with the number the resolver gives it for 2026, sortable and
searchable in `places_2026_2d.html`. But the list also shows something important: **only 1,637 of
those numbers are different from each other, and none of them comes from the place's own record.** The
three anchors are Hyderabad, Secunderabad and Akanpalle; a village 40 km out is a scaled fraction of
one of them, and a town 86 km out can be given 5.7 million people. So the honest summary is: the
project has 15,954 places *mapped*, three places *measured*, and everything else *modelled* from those
three — which is fine for a game, but it is not yet a set of populations, and this list is the first
time it has been visible in one place.

> Step 29 note: to save snapshot space the big list is now stored gzipped as `places_2026_2d.html.gz` (unzip it to open it in a browser).

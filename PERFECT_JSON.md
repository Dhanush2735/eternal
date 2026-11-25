# a plan for the perfect pack JSON

*A schema, not a style guide. Every rule below is executable — `schemas/eternal-pack-v2.schema.json`
is a JSON Schema, `schemas/example_telangana_v2.json` is a worked example that validates against it,
and `verification/population/validate_packs.py` runs the rules against the packs as they are today.*

**The lint result: 22 rules, 6 fail, 2 warn, 13 pass** (`verification/population/pack_report.txt`).
Those six failures are the case for v2; the 13 passes are what v2 must not break.

Picture: `json_plan_2d.html`.

---

## 1. What "perfect" means here

Not "every place has its own population". Most villages on earth never had a census; inventing numbers
for them is exactly what the current packs do, and it is what produced a 1,050,000-person Secunderabad
in 1840. **Perfect means every number says what it is**, and the format makes it impossible to say
nothing:

| the number is | the file must say | example |
|---|---|---|
| a measurement | the citation, the year, and **what footprint it counts** | Census 2011, cantonment board, 22.81 km² |
| a model output | which model, from which anchor, with which clamp | rank-size decay from Hyderabad city, clamped by rank |
| a range | that it is a range, with both ends | era band 1800–1900, city: 40,000–800,000 |

A file that cannot say which of those three it is should fail to load. That is the whole design.

## 2. The six rules the current data fails

| # | rule | what fails today | what v2 adds |
|---|---|---|---|
| R1 | every id unique across all packs | **825 ids belong to two packs**, covering 1,650 rows (all 599 Kamareddy places also sit inside Telangana; 226 metro ones too) | `uid` = `pack:id`; the ids stay, the ambiguity goes |
| R6 | no sentinel years | **15,826 rows use `occupiedEnd: 9999`** to mean "still there" | `occupied.present: true` with `until: null`; a sentinel can never be read as a year |
| R9 | `partOf` names a declared container | **2 rows say `ramareddy`** — a container nothing else uses | references must resolve; a stray value fails the build |
| R18 | a model may not exceed its region's own record | **3 regions**: Secunderabad could be modelled at 3.7× its own 1941 record, Akanpalle at **764×** its 2011 record, Maheswaram at 12.3× | `derivation.clampToRecordMax` — no modelled year may exceed the largest record of the same region |
| R19 | every place says where its people come from | **0 of 15,954 places** name a source; inheritance is computed by proximity, which is how 475 localities silently came to hold a whole city | `population: {region, role, distanceKm}` with `role ∈ own · inside · near · scaled · none` |
| R21 | generated packs say what generated them | **0 of 15 files** say it; `events_world.json`'s 14,934 Wikidata rows are indistinguishable from hand-curated ones | `generator: {tool, version, created, inputs[sha256]}` |

## 3. The schema, in one page

One pack = one kind of record. Every pack: `schema`, `id`, `title`, `kind`, `sources`, `records`.

**`place`** — `id`, `uid`, `name`, `lat`, `lon`, `precisionM`, `rank`, `partOf[]`, `occupied{from,until,present}`, `urban{…}`, `polity`, `population{region,role,distanceKm}`, `roles[]`, `provenance{source,confidence}`.

**`population`** — `id`, `name`, `recordKind` (**settlement · aggregate · polity** — a polity of 16.3 M and a cantonment of 135,370 are not the same sort of thing), `rank`, `extent{shape,radiusKm}` (**the only real area in the format**), `members`, `series[]{year,people,source,footprint}`, `derivation{between,maxGapYears,beyond,clampToRecordMax}`, `provenance`.

**`event`** — one shape for all of them: `eventKind` (battle · siege · disaster · incident · political · military), `effect`, `radiusKm`, `when{from,until}`, `provenance`. Today's packs hold **three shapes at once**, and 10 rows carry none of the fields that make an event an event.

**`trait`** — `traitKind`, `when`, `sites[]` (place uids that must resolve — the only join key in the data).

**`border`** — `polity`, `when`, `box`, `rank` (what the ground is treated as inside the box; all 16 shipped borders say `village`, and v2 keeps that explicit).

**`model`** — the calibration constants in their own file, with their own version: `rankUnits` (village 1 · town 26 · city 2500), `bands[]`, `gravityDecayKm`, `maxInterpolationGapYears`. *They are not history*; today they live inside a data pack, where a data update can silently change every modelled number in the game.

## 4. Rules the plan deliberately does **not** impose

A schema earns trust by refusing to demand things that cannot be true:

* **No population on a place that has no record.** `population.role: none` is a legal, honest answer. A village with no census says so.
* **No coordinates precision nobody has.** `precisionM` is required to be honest, not small.
* **No "sourced" for generated rows.** `confidence ∈ measured · estimated · modelled`, and `modelled` requires the model's file and version.
* **No polygon mandate.** `extent.shape` allows `disc`, `box` and `polygon`; a 6 km disc is what Secunderabad's own pack can justify, so that is what it may say.
* **No renaming of working data.** `uid` adds identity; `id`, names and coordinates stay exactly as they are.

## 5. Cost of the change, measured

| item | today | under v2 |
|---|---|---|
| the 13,287-place pack as object rows | **4,685 KB** | **1,311 KB columnar — 72% smaller** for the same data |
| the entire population record | 16 rows, **1,899 bytes** | same 16 rows, plus `footprint` and `derivation` — a few hundred bytes more, and it becomes readable |

Columnar rows are an option the schema allows (one `columns` object instead of `records[]`) and a
generated pack may use it; nothing the engine reads changes.

## 6. Migration, in the order that cannot break the game

| step | what | risk |
|---|---|---|
| 1 | add `schema` + `generator` blocks to all 15 files | none — loaders ignore unknown keys |
| 2 | add `uid` to every place; keep every row and id | none |
| 3 | `9999` → `occupied.present: true` (15,826 rows), one mechanical pass | low — loader change is one line |
| 4 | write `population{region, role, distanceKm}` per place, computed once offline | low — v2 records what the resolver already does silently |
| 5 | move `defaults.bands` and `calibration` out into `model.json` | medium — the resolver must read the new file |
| 6 | turn on `clampToRecordMax` and rank-capped decay, then re-run `PlaceInventoryProbe` | **behaviour change** — numbers will move, and the inventory must be regenerated and re-checked |

After each step: `python3 verification/population/validate_packs.py` — the rule that was failing must
flip to PASS, and the other rules must stay exactly as they are. That is the whole acceptance test, and
it is already written.

## Verdict in simple words

A perfect pack is not one where every place has its own number — most of the world never had a census,
and pretending otherwise is what the current files do. A perfect pack is one where **every number says
what it is**: a measurement with its citation and footprint, or a model output with the model named, or
a range admitted as a range.

The plan is one schema with 22 rules, a worked example that validates, and a lint that already runs.
Six rules fail on today's data — colliding ids, 15,826 sentinel years, one stray container, three
regions whose fallback could outrun their own records, no place stating where its people come from, and
no file saying what generated it. Each fix is a field the schema already defines, and the six steps are
ordered so the game keeps running at every one of them.

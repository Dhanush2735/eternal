# Accurate history packs

Sourced **sites + year ranges + traits**. Not template `hist_*` events.

| Pack | Region |
|---|---|
| `indus.json` | Harappan sites |
| `mesopotamia.json` | Sumer, Akkad, Babylon, Assyria |
| `egypt.json` | Nile cores |
| `china.json` | Shang–Qing cores |
| `mediterranean.json` | Minoan, Greece, Rome, Carthage, Byzantium |
| `south_asia.json` | Named cities + Maurya–modern cores (not a census of every town) |
| `americas.json` | Olmec, Maya, Teotihuacan, Aztec, Inca |
| `afro_islamic.json` | Kush, Aksum, Mali, caliphates, Ottoman |
| `present_kamareddy.json.gz` | 599 named OSM places; **years estimated** |
| `present_hyderabad_metro.json.gz` | Hyderabad–Secunderabad OSM names; **years estimated** |
| `present_telangana.json.gz` | **13,287** OSM city/town/village/hamlet in Telangana; **years estimated** |

Spawn uses a site within ~30 km that is occupied this year. Capital 400 km bubble is **not** spawn.

## Part 1 (village vs city)

A site can be a **village** (occupied, not urban) or a **city** (inside `urbanStart`–`urbanEnd`), or an explicit `"rank"`.

`/ee villager here` spawns:

- village → 1 farmer
- town → farmer + artisan
- city → farmer, artisan, merchant, citizen

Example: Rakhigarhi / Balu at **−3000** = village. Mohenjo-daro at **−2500** = city.

## Part 2 (more towns + trait jobs)

Second-tier sites added (Deir el-Medina, Pompeii, Farmana already, Ujjain, Timbuktu/Jenne-jeno, Palenque, …).

Active **traits** on a site change jobs: dock/trade → merchant, brick/drain/pyramid → artisan.

## Part 3 (approx empire cores)

`borders.json` boxes, used only if no site is within 30 km. Countryside = village of that civ.

- Haryana **−250** → Maurya village (not empty)
- Hyderabad **0** → Satavahana village
- Haryana **2026** → India village (not Mughal)

These are **cores, not real frontiers**.

## Part 4 (OHM cache)

Dated OHM `place=city/town/village` from Terra++ disk cache.

## Part 5 (undated names — estimated years)

Do **not** lock undated map names to modern years only.

`SettlementYearEstimator`:

1. **Gravity** — inverse-square pull from dated cities/towns within ~250 km  
2. **Diffusion lag** — villages later than that city (farther → later)  
3. **Parent box** — if no city pulls, earliest overlapping kingdom box (not “India 1947” if an older box exists)

`/ee who` prints `confidence=estimated`. This is a **model**, not a sourced founding.

Undated OHM named places use the same estimator (not year 0, not 2011-only).

Order: sourced site → estimated named place → dated OHM → unnamed hinterland → empire box → nobody.

## Part 6 (events)

Sourced **point incidents**, not towns.

- Fields: `lat`, `lon`, `year` or `start`/`end`, `radiusKm`, `kind`, `effect`
- Active only that year (or start–end) and within radius
- `/ee who` prints `event:` overlay
- `effect`: `note` | `lockdown` | `memorial` | `disruption` | `political` | `military_presence`
- **Does not** found a city. **Does not** spawn attackers.
- World wars = local battle pins, not every village.
- `events.json` = curated high-confidence pins (26/11, 9/11, major wars).
- `events_world.json.gz` = Wikidata named events with coords + years (~15k, −3000…now). Missing coords/year = no overlay.
- `events_wikidata_more.json.gz` = 6,040 more Wikidata events missing from `events_world.json.gz`: 3,725 battles, 845 sieges, 907 treaties (placed by own coordinates or a specific P276 location, never a country or region centre) and 563 war theatres (one pin per cluster of a war's own battles, radius 60–300 km, lasting while those battles were fought). Built by `worldfill/build_wikidata.py`.
- `events_wikipedia.json.gz` = 19,368 events from English Wikipedia year, decade and century pages (3000 BC…2026), one-year pins, geocoded via Wikidata. Events already in `events_world.json.gz`, `events_wikidata_more.json.gz` or `events.json` are left out. Built by `harvest/` v2 (`harvest/FIXES.md`).
- Packs listed as `*.json.gz` are gzipped JSON; `HistoryGazetteer` reads them through gzip.
- No source → no event. Not a fake file of every incident on Earth.
## Part 7 (world-fill places)

Long-lived dated places from open datasets, so the map is not empty between events. Built by
`worldfill/build_places.py`; see `worldfill/README.md`.

- `places_pleiades.json.gz` = 18,677 ancient places from Pleiades: settlements, villas, forts, road stations, sanctuaries. Each covers its attested date range, capped at AD 640. Rank: city (urban / DARE major), town (settlement, polis, port), village (villa, fort, station, sanctuary).
- `places_chgis.json.gz` = 10,446 county-level seats of China, 763 BC…1911, from CHGIS V6. Rank: town. A seat still standing in 1911 stays open to the present.
- `places_reba.json.gz` = 1,787 city records: 1,433 cities from Reba et al. 2016, split at AD 641 and 1500 so each era gets its own civHint. Each runs from its first recorded population to the next survey year after its last. Rank: city.
- A curated site above always wins: a new place within 10 km loses exactly the years the curated site covers. A Reba city named by Pleiades (within 5 km) starts after AD 640: ancient name in antiquity, modern name after.
- civHint is always one of the 133 authored civilizations, so a place only claims a polity in years that polity existed. China uses the dynasty of the record's years.
- Towns and cities here also grow Part 5's **estimated hinterland** (unnamed countryside villages within 80 km). That, not the places themselves, is most of the new presence.
- `"estimatorAnchor": false` on these packs: Part 5's settlement-year estimator does not pull undated places toward them. The 14,902 estimated Telangana years are unchanged, byte for byte.
- Scans skip anything whose latitude alone is out of range before computing a distance (`HistoryGazetteer.beyondByLatitude`). The skip is exact: no answer changes. Load stays about 1–2 s with 46,864 sites, and a query takes about 0.5 ms.
- Sources and licences: `ATTRIBUTION.md`.

## Part 8 (rulers everywhere, and estimated people)

Who ruled the ground, and whether anyone lived on it, for the whole world. Built by
`worldfill/build_rulers.py`, `worldfill/hyde_grid.py` and `worldfill/build_hyde_resource.py`; see
`worldfill/README.md`.

- `world_rulers.json.gz` = Cliopatria v0.2.1 for the whole world, with the Deccan record's rule (the smallest plain polity covering the point that year). It has 62,647 places on 0.5-degree cell centres, 8,451 timelines and 1,544 polities, in compact "format 2". `RulerRegister` loads it alongside `deccan_rulers.json.gz`, and the nearest recorded place still wins. Cells that hold a Deccan place are left to the Deccan record. Checked against it: the same ruler in 97% of sampled place-years.
- `../population/hyde_density.bin.gz` = HYDE 3.2.1 people per km² for every 0.5-degree land cell, at 28 time slices from 3000 BC to AD 2017, read by `HydeGrid`.
- **Presence:**
  - A Deccan ruler counts as presence, as before.
  - A world-record ruler counts only where the population model has people on that spot.
  - People with no recorded ruler count too (civ `neutral`).
  - On land with at least 2 people per km², every spot has people. On thinner land, a fixed share of 0.05-degree tiles does: density ÷ 2, chosen by a hash of the tile. Below 0.01 per km², nobody.
  - `/ee who` labels all of this "estimated people … a population model, not a record".
- `PopulationResolver` T4 GRIDDED: a village with no curated figure gets people per km² × 10 km². It is tried after the district apportionment (T5), so curated Telangana figures keep answering. Towns and cities keep the era band.
- World polities are **not** added to the civilization table. Every civilization there is a region that the population, economy and migration engines iterate. NPCs still carry the polity id.
- `VillagerNameDatabase.cultureFor` knows many more polity names, so NPC names fit the region: 56% → 8% of world cell-years fall back to generic names. This also fixed Songhai and Ghana, which drew East Asian names because "song" and "han" hid inside their names.

## Part 9 (the categories under 50%: Wikipedia first)

Three more event packs, built by `worldfill/build_regional.py`, `build_hazards.py` and
`build_wars.py` from an offline copy of English Wikipedia (dump 2026-10-01, read through
`worldfill/wpdump.py`, so no API rate limit applies):

- `events_wikipedia_regional.json.gz`: year-page lines the v2 harvest skipped because they name
  no exact spot. Every link in the line is tried: a city or site (normal local event), else a
  region, state or country. States use their Cliopatria extent in that year, and the land a
  modern country named in an old event was ruled by then. Region and state names in unlinked
  text are learned from the pages' own links ("Egyptian" -> Ancient Egypt).
- `events_hazards.json.gz`: epidemics, plague, famines, earthquakes, tsunamis, eruptions and
  floods from Wikipedia's list pages first. NOAA NCEI, the Smithsonian GVP and the Biraben
  plague data fill in only where Wikipedia has nothing of that kind nearby. Kinds are specific
  (`plague`, `earthquake`, ...) and all are in `InWorldInterventionDetector.REFUSED_KINDS`.
- `events_wars.json.gz`: wars from the "List of wars" pages, placed by the war article's
  infobox location, else by the defeated or victorious side's state that year.

**Regional pins.** A pin with `"confidence": "wikipedia_region"` stands for a whole region or
country, not a spot (radius 60-800 km). `HistoryGazetteer.nearestEvent` returns one only when
no local pin contains the point; `nearestLocalEvent` (used by the intervention locator) never
returns one, so regional news can never be "stopped" from one spot, nor hide a local event.
`EventNews.rumor` words them as news from afar ("Travellers say the whole land is talking
about ...").

## Part 10 (the 0% categories, from data already in the workspace)

Built by `worldfill/build_ucdp.py`, `build_polity_facts.py`, `build_people.py` and
`build_decades.py` from files that were already downloaded. The raw copies were deleted
afterwards (`worldfill/CLEANUP_STEP6.md`).

**Two more event packs** (listed in `index.json`):

- `events_ucdp.json.gz`: modern conflicts 1989–2024 from the UCDP Georeferenced Event Dataset
  25.1. There are 28,639 local pins, one per conflict, year and province. Each sits at the
  death-weighted centre of that year's events, and its radius reaches 90% of them (median 50 km).
  The pins hold 92% of UCDP's recorded deaths. Events that UCDP can place only to a country are
  left out, because the regional war news already covers them. Kind `conflict` is not in
  `STOPPABLE_KINDS`, so it can never be "stopped". The effect is `lockdown` for violence against
  civilians and `military_presence` otherwise.
- `events_wikipedia_decades.json.gz`: 1,763 events from 280 decade and century pages
  ("1640s", "11th century BC") that the year-page harvests never read. 23 more, such as
  millennium pages, have no year range the builder reads. They are placed like Part 9's
  regional pack. Lines without a year of their own are left out, because the page's first year
  could be off by up to 99 years. 839 lines were already in the mod.

**Two registers** (read directly, not gazetteer packs):

- `polity_facts.json.gz`, read by `LeaderRegister` and keyed by the same Wikidata ids that
  `RulerRegister` answers with. It has 1,185 states:
  - languages for 997 states and religions for 911, from each state's own Wikipedia infobox;
  - capitals for 1,043;
  - heads of state for 913 states (4,824 reigns) and heads of government for 499 reigns.

  Reigns come from three sources:
  - infobox `leaderN`/`year_leaderN` and `deputyN`;
  - year-page accession lines ("Peter I becomes King of Portugal"), ending at the next accession,
    the ruler's recorded death, or after 30 years (8 for elected leaders);
  - year-page death lines that name a top title ("Basil II, Byzantine emperor"), running from the
    previous holder's death.

  Nothing is guessed. Between known reigns the register has no name, and villagers say nothing.
  The year pages are sometimes loose (a prince called "Mughal emperor"). Lines that name a
  relation ("son of …") are dropped, but a few wrong names remain.
- `famous_people.json.gz`, read by `FamousPeople`. It has 72,594 people from the Births and Deaths
  sections of the year pages, each tied to the state of their home land in their active years.
  The home land comes from the nationality word ("Scottish poet"), a title ("King of France") or
  a linked place.

**Java:**
- `RulerRegister.Hit.wikidata()`.
- `HistoryGazetteer` builds a per-year event index at load, so a lookup scans only that year's
  pins. On 10,062 test queries it gave the same answers as before, about 240× faster.
- `HistoricalPresence.describe` (`/ee who`) adds:
  - head of state and head of government;
  - the state's languages, religions and capital;
  - famous people of the land alive now, and those who died this year;
  - way of life, an estimate from the population model labelled "not a record".
- `EventNews.rulerLine`, `cultureLine`, `famousLine`, `wayOfLifeLine` and `townTalk` produce the
  villager lines: "This land belongs to the Mughal Empire, ruled by Aurangzeb (emperor of India)."
  "People here speak Persian. The main faith here is Islam." "Sad news this year: …, has died."
- `CurrentAffairsEngine` adds these as briefing facts.
- `PresenceLocalSpawner` gives each town NPC a 1-in-20 chance per cycle of saying one of them.
  That is about once every 40 seconds per person.

**Ways of life** have no source on disk. They are an estimate from HYDE density, the place's rank
and the era: farmers in villages, herders, hunters and gatherers, or crafts and trade in towns.
They are always labelled as an estimate.

**Trade routes** are not included: no trade-route data was on disk.

## Part 11 (fetching the 0% categories: new sources)

Downloaded for this part (raw copies in `~/.cache`, deleted after the build):
ArchaeoGLOBE, Glottolog 5.3, the Sciences Po notable-people database, the Correlates of War
religion data, Itiner-e, and Wikipedia's state-leader lists and trade-route articles.

| File | What | From |
|---|---|---|
| `ways_of_life.json.gz` (new) | 146 world regions × 10 dates (8050 BC – AD 1850). Each rates hunting and gathering, shifting farming, intensive farming, herding and towns as none, minimal, common or widespread. | ArchaeoGLOBE (CC0) |
| `local_languages.json.gz` (new) | 6,709 living languages with a map point, 137 of them major | Glottolog 5.3 (CC BY 4.0); living status from SIL's ISO 639-3 table |
| `famous_people.json.gz` (grown) | 183,754 people (was 72,594). 148,586 have their exact birthplace (`radiusKm` 25). | + Sciences Po notable-people database (CC BY-SA 4.0) |
| `polity_facts.json.gz` (grown) | 1,094 states with named rulers (15,324 reigns, 4,338 heads of government). Faith shares every 5 years 1945–2010 for 219 states (`relY`). | + Wikipedia "List of state leaders in …" (62 century/decade pages); + Correlates of War World Religion Data |
| `trade_routes.json.gz` (new) | 14,769 Roman road sections (simplified to ~1 km); 10 trade routes with 364 towns | Itiner-e (CC BY 4.0); Wikipedia route articles |

Notes on the data:
- The state-leader lists give exact spans. For each state they replace the rougher reigns from
  accession and death lines that overlap them.
- Route years come from each article's own lead. Routes whose lead gives no dates were left out:
  Amber Road, Uttarapatha, Dakshinapatha and Indian Ocean trade.
- A road counts from its Itiner-e construction date, else from 100 BC. It is "in use" until its
  own end date, else AD 500, and an "old Roman road" after that, until 1500.

**Java:**
- New `WaysOfLife`, `LocalLanguages` and `TradeRoutes`.
- `LeaderRegister.Facts.religionSharesIn(year)`.
- `FamousPeople.bornNear(...)`.

**`EventNews` villager lines:**
- `cultureLine`:
  - From 1500, it also names the people's language when the state's differs: "The court speaks
    Persian; people here speak Telugu." From 1800 it says "The government uses …".
  - From 1945, faiths come with their measured shares.
  - Languages and faiths are skipped when the state's Wikidata id points at another state's
    article (the Roman Empire shares one with the Byzantine Empire).
- `famousLine` also says "X, the poet, was born near here" (within 15 km).
- New `tradeLine`: "Merchants on the Silk Road pass through here."
- New `roadLine`: "A Roman road runs through here, from Tarracinae to Rome."
- `wayOfLifeLine` uses ArchaeoGLOBE first (up to 1899), then the population estimate.

**Also wired in:**
- `/ee who` shows each new line with its source.
- The briefing facts gain `trade` and `road`.
- Town talk picks from all six kinds of line.

## Part 12 (filling the gaps a player notices)

Built by `worldfill/fix_state_ids.py`, `fill_rulers.py`, `add_estimated_routes.py` and
`fill_famous_gaps.py` (see `worldfill/README.md`, step 8). Raw copies in `~/.cache`, deleted after the build.

| File | What changed | From |
|---|---|---|
| `world_rulers.json.gz` | 92 states' Wikidata ids repaired (names and borders unchanged): Han China, Joseon, the Roman Republic and Empire, the Byzantine Empire, the Kingdom of France, the Sasanian Empire, China's Zhou-era states, and modern states that shared an id (South Korea, South Vietnam, the Republic of the Congo, Guinea, Taiwan) or carried an older state's (Ukraine, Mexico, Japan, Syria, Denmark, Slovakia …) | hand-checked against English Wikipedia (dump 2026-10-01) |
| `polity_facts.json.gz` | 1,165 states with named rulers (was 1,094), 17,700 reigns (was 15,324). Colonies name their home country's head of state. 1,050 states with languages, 961 with faiths, 230 with measured faith shares | + ~160 Wikipedia ruler-list pages; Correlates of War (re-run) |
| `trade_routes.json.gz` | 14 routes with 411 towns (was 10 and 364): + Dakshinapatha, Uttarapatha, Amber Road, Indian Ocean trade, with **estimated** years | Wikipedia route articles |
| `famous_people.json.gz` | 184,193 people (+439 for lands that had nobody famous in a period); 30,574 re-tagged to their repaired state | + Wikidata (CC0) via QLever |

Notes on the data:
- A list from a country's own ruler page (for example "List of British monarchs") replaces the rows it
  overlaps; a doubtful list only fills years with no ruler. Lists that continue with pretenders are cut at
  the end of the monarchy.
- The Roman Republic names the year's two consuls: "C. Julius Caesar and M. Calpurnius Bibulus (Consuls)".
- An estimated route's `years_from` starts with "estimated:" and quotes the dated sentences it rests on.

**Java:** `RulerRegister.rulerAt` — when the nearest grid place has no ruler that year, the nearest
place that has one wins if it is at most 20 km farther (`NEIGHBOUR_KM`). Rome lies between two grid
cells; the tie used to go to an empty coastal one, so nobody ruled Rome in antiquity.

## Part 13 (checking it all in the game: `/ee check all`)

`/ee check all` (operators; `/ee check` alone does the same) runs every runtime test at once: the five
live sections of the running server, then the history data of Parts 6-12. There are no single checks.
The history code is `civilization/HistoryChecks.java`, without Minecraft classes, so
`worldfill/probe_check.sh` runs the very same checks outside the game.

| Section | What it checks |
|---|---|
| 1-5 | Clock and schedules, the history corpus and the authored event chain, the stories and timelines, Terra++, the gazette |
| Here and now | What villagers where the player stands can say this year (only when a player runs it) |
| 6 | Every register and all 24 packs of `index.json` load, with at least the step-8 counts (a broken pack is otherwise skipped without a word) |
| 7 | The 15 places of `worldfill/TEST_CHECKLIST.md`: state, head of state, trade route; in the report also every other villager line there, and the `/tpll` and `/ee year set` commands to go and look |
| 8 | 25 more places whose rulers were fixed in step 8 (3 are documented limits, shown as KNOWN) |
| 9 | Villager lines at the 12 standard places, and how often ten villagers repeat themselves |
| 10 | A local event always beats regional news, and only a local event can be stood in |
| 11 | The per-year event index gives the answers of a full search |
| 12 | Who lives where at 400 land points x 13 years, and the 15,727 estimated founding years, unchanged |
| 13 | First-load time, and the time of one place's full answer (each place the best of three tries) |

Chat shows one line per check (details only for failures). The full report, every line, is written to
the world folder as `eternalengine/checks/ee_check_<date>_<time>.txt`; the path in chat copies on click.
Sections 6-13 run on a background thread, so the server keeps ticking for the ~20 seconds they take.

A world that has no foundation data gets the shipped copy when it starts (since step 11:
`FoundationDataInstaller.installIfMissing`, called by `SimulationEngine.start` before the installed data is
loaded and the stories are read), exactly what `/ee data install` does; a world that has its copy is left as
it is. Section 2 says when that happened, and the report's second line names the world. If the event chain
or the stories still FAIL, the line says why the install did not work and what to type.

On 2026-10-12 it ran on a real Forge 1.20.1 dedicated server in the sandbox (`worldfill/tools/run_test_server.sh`,
flat world, no player): all 87 checks passed, plus the 3 known limits.

The answers to compare with are the ones checked by hand when the data was built (2026-10-12). After a
deliberate data change, run `worldfill/probe_check.sh`, check the new answers, and update the expected
values at the top of `HistoryChecks.java`.

## Part 14 (step 25: the history data fix)

`worldfill/fix_history_data.py` corrected `world_rulers.json.gz`, `deccan_rulers.json.gz`, `polity_facts.json.gz`,
`local_languages.json.gz`, `famous_people.json.gz`, seven event packs and `../economy/money_table.json` in groups; every
change is a line of `worldfill/step25_changes.csv`, and `--undo` restores the files exactly. In short:

- The border map goes on to 2026; names by period (Dominion of India 1947–1949, the United Kingdom, Kingdom of Denmark
  after 1814 …); occupations are not annexations (Czechoslovakia 1969–1990 was not the Soviet Union); the Nizam's
  Hyderabad State until 1948; Bombay and Madras as places of their own; coastal squares take their neighbours' state.
- `polity_facts.json.gz`: prime ministers moved to the head-of-government list; claimants and non-rulers removed; empty
  years filled from Wikidata's office holders; two new optional fields, read by `LeaderRegister`:
  - `relP`: `[[start, end, faith], ...]`, the main faith of the year (England is Anglican from 1534);
  - `relFolk`: `{"faith": ..., "box": [latMin, latMax, lonMin, lonMax]}`, what most people followed where the court
    followed another faith ("The court follows Islam; most people here follow Hinduism").
- `RulerRegister.lastYear()`: later years are answered as the last recorded one.


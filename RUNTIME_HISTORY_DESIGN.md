# Runtime History Retrieval — What Exists, What Doesn't, What To Build

*Question asked: instead of collecting all history from the web up front and bundling it, can the
mod retrieve history by **location and year at runtime**, the way it retrieves OSM?*

*Short answer: **it already does**, for buildings and places. The event/border/narrative layer is the
part still bundled as static JSON. Every claim below was checked against the source on disk.*

---

## 1. The runtime retrieval pipeline already exists in terra3

`terra3/src/main/java/net/buildtheearth/terraplusplus/generator/historical/OhmStructureIndex.java`
(1,121 lines) queries **OpenHistoricalMap** — OSM's data model with `start_date` / `end_date`
temporal tags — through its own Overpass instance.

### Location: bbox tiling at fetch time

```java
// OhmStructureIndex.java:62-63
"https://overpass-api.openhistoricalmap.org" + "/api/interpreter";

// OhmStructureIndex.java:497-501
String bbox = String.format("%.6f,%.6f,%.6f,%.6f",
    minLat, minLon, minLat + TILE_DEG, minLon + TILE_DEG);

// OhmStructureIndex.java:506-...
String queryStr = "[out:json][timeout:25];("
    + "way[\"building\"](" + bbox + ");"
    + "relation[\"building\"](" + bbox + ");"
    + "way[\"building:part\"](" + bbox + ");"
    + "way[\"highway\"](" + bbox + ");"
    + "way[\"railway\"~\"^(rail|light_rail|subway|tram|...)$\"](" + bbox + ");"
    + "way[\"bridge\"](" + bbox + ");"
    + "way[\"barrier\"~\"^(wall|fence|hedge|retaining_wall|city_wall|guard_rail)$\"](" + bbox + ");"
    + "way[\"landuse\"~\"^(farmland|forest|meadow|...)$\"](" + bbox + ");"
    + ...
```

### Cache-first, network only on miss

```java
// OhmStructureIndex.java:180-183 (javadoc)
// Disk cache for ALL needed tiles is checked here
// before any tile is submitted to the thread pool.
// Only truly missing tiles trigger network fetches.

// OhmStructureIndex.java:196-198 — source cooldown is retryable, not a successful empty tile
if (System.currentTimeMillis() < pauseUntilMs) return null;

// OhmStructureIndex.java:242-256 — only missing keys are submitted
for (long key : missingKeys) { ... pending.computeIfAbsent(key, k ->
    CompletableFuture.supplyAsync(() -> fetchTile(k, tLat, tLon), dlPool)); }
```

Cache layout: memory `ConcurrentHashMap` → disk at
`TerraCachePaths.dir("ohm_structures_v2")` (the `v2` suffix is noted as "widened full-coverage
query") → network. There is also a `memCache.remove(key, t)` invalidation path at line 227 to break
a stall where a tile was cached empty and never refetched (the `complete=0` stall mentioned in the
comment at line 224).

### Year: filtered client-side, because Overpass can't

Overpass QL has no temporal filter, so the tile is fetched whole and visibility is computed locally:

```java
// OhmStructureIndex.java:681-694
getTag(tags, "start_date");
getTag(tags, "end_date");
int startYear = HistoricalDateParser.parse(startDate);
int endYear   = HistoricalDateParser.parse(endDate);
→ new OhmStructure(..., startYear, endYear, ...)

// OhmStructureIndex.java:94-103 (on the OhmStructure record)
public boolean isVisibleAt(int year) {
    return HistoricalDateParser.isVisibleAt(startYear, endYear, year, false);
}
public int yearsUntilAppear(int currentYear) {
    return HistoricalDateParser.yearsUntilAppear(startYear, currentYear, false);
}

// HistoricalDateParser.java:164-180
public static boolean isVisibleAt(int startYear, int endYear, int currentYear, boolean ...) {
    if (startYear == UNKNOWN_YEAR) { effectiveStart = ... }
    ...
    if (endYear != UNKNOWN_YEAR && currentYear >= endYear) return false;
}
```

`HistoricalDateParser` constants: `UNKNOWN_YEAR = Integer.MIN_VALUE`, `DEFAULT_OSM_YEAR = 2000`,
`DEFAULT_OHM_YEAR = Integer.MIN_VALUE`.

### Structures with no date at all

`OsmDateInferencer.java` runs an 8-tier fallback chain, documented in its header:

```
1. OSM start_date tag (explicit)
2. OSM opening_date / construction_date (explicit)
3. OSM heritage / historic tags (inferred old)
4. OSM building subtype (inferred era)
5. OSM material / height / levels tags (context)
6. OSM road surface (inferred era)
7. Geographic region default (last resort)
8. Absolute fallback = 1900
```

It carries hardcoded lat/lon boxes for region defaults (North America, Western Europe, Eastern
Europe/Russia west, and more). This is what makes sparse OHM coverage survivable.

### Named-entity facts, also at runtime

`WikiDataLookup.java` (668 lines):

```java
// WikiDataLookup.java:36-38
"https://query.wikidata.org/sparql";
"https://en.wikipedia.org/w/api.php";

// :198  public WikiResult lookupCached(String name)          — blocking, cache-first
// :217  public void prefetch(String name, ...)               — "Non-blocking prefetch — fires and forgets"
// :224  if (memCache.containsKey(key)) return;               — dedupe
// :262  // Cache negative result so we don't re-query the same name repeatedly
// :290  private WikiResult queryWikidata(String name, double lat, double lon)
// :294  String sparql = "SELECT ?item ?label ..."
```

### Full list of runtime endpoints in terra3

```
https://overpass-api.openhistoricalmap.org   ← historical features (the important one)
https://overpass-api.de/api/interpreter      ← OSM, primary mirror
https://overpass.kumi.systems/api/interpreter← OSM, mirror
https://maps.mail.ru/osm/tools/overpass/api/interpreter ← OSM, mirror
https://api.openstreetmap.org/api/0.6/map.json
https://data.osmbuildings.org/0.2/anonymous/tile/15/16384/16384.json
https://data.source.coop/vida/google-microsoft-osm-open-buildings/flatgeobuf/by_country_s2/country_iso
https://esa-worldcover.s3.eu-central-1.amazonaws.com
https://minedbuildings.z5.web.core.windows.net/global-buildings/dataset-links.csv
https://overturemaps-extras-us-west-2.s3.us-west-2...
https://portal.opentopography.org/API/globaldem
https://query.wikidata.org/sparql
https://en.wikipedia.org/w/api.php
https://s3.amazonaws.com/elevation-tiles-prod/terrarium
https://tile.nextzen.org/tilezen/terrain/v1/256/terrarium/10/512/512.png
https://api.github.com
```

Three Overpass mirrors is deliberate resilience, and the source-cooldown in `OhmStructureIndex` is
the matching rate-limit defence.

---

## 2. Eternal already consumes it

### `OhmPresence` — location + year, reading terra3's cache

`eternal/src/main/java/com/eternalengine/terrabridge/OhmPresence.java` (251 lines):

```java
// :17   "OHM occupancy from Terra++ disk cache only. Does not call Overpass."
// :31   public record Hit(..., int startYear, int endYear, double distanceKm, ...)
// :42   public static Optional<Hit> nearestDated(double lat, double lon, int year)
// :50   public static Optional<Hit> nearestPresent(double lat, double lon, int year)
// :54   private static Optional<Hit> nearest(double lat, double lon, int year, boolean undatedOk)
```

`nearest()` computes a tile window around the query point and scans the disk cache:

```java
int minI = (int) Math.floor((lat - SEARCH_DEG) / TILE_DEG);
int maxI = (int) Math.floor((lat + SEARCH_DEG) / TILE_DEG);
...
for (int i = minI; i <= maxI; i++)
  for (int j = minJ; j <= maxJ; j++) {
      long key = ((long) i << 32) | (j & 0xffffffffL);
      Path file = dir.resolve(Long.toHexString(key) + ".json");
      ...parseElement(el.getAsJsonObject(), lat, lon, year, undatedOk);
      // keeps the hit with the smallest distanceKm
  }
```

Year filtering happens in `parseElement` (:129-132): `if (year < start) skip;` and
`if (end != UNKNOWN && year >= end) skip;`.

Cache path is resolved reflectively (:240-248):
`Class.forName("net.buildtheearth.terraplusplus.util.TerraCachePaths")` → `dir("ohm_structures_v2")`,
falling back to `${user.dir}/terra_cache/ohm_structures_v2`.

**This is a clean separation and should be preserved:** terra3 owns the network, eternal owns the
queries. Adding a second HTTP client inside eternal would duplicate retry/cooldown logic.

### `HistoricalPresence` — the consumer

```java
// civilization/HistoricalPresence.java:101-106
Optional<OhmPresence.Hit> presentMap = site.isPresent() ? ...
    : OhmPresence.nearestPresent(lat, lon, year);
Optional<OhmPresence.Hit> ohm = site.isPresent() || presentMap.isPresent() ? ...
    : OhmPresence.nearestDated(lat, lon, year);
```

It prefers a curated site, then an undated present-map place, then a dated OHM feature.

### `TerraTimeBridge` — one clock, not two

`terrabridge/TerraTimeBridge.java` (170 lines) does not run its own year. It commands terra3's:

```java
// :22-23 — reflection targets (verified: these classes exist at exactly these names in terra3)
"net.buildtheearth.terraplusplus.generator.historical.TerraTimeManager"
"net.buildtheearth.terraplusplus.generator.historical.PersistentChunkRefreshIndex"

// jumpToYear(server, targetYear):
int scale      = Math.max(1, getTimeScale(1));
int baseYear   = readStaticInt(TERRA_TIME_MANAGER, "BASE_YEAR", -3000);
int daysPerYear= readStaticInt(TERRA_TIME_MANAGER, "DAYS_PER_YEAR", 365);
long yearsFromBase = (long) targetYear - baseYear;
long worldDay = (yearsFromBase * daysPerYear) / scale;
level.setDayTime(worldDay * 24000L + currentTimeOfDay);
TerraTimeManager.forceRecalculate(level);
PersistentChunkRefreshIndex.advanceRefreshGeneration(level, actualYear);  // regenerates terrain
```

Also here: `isInfrastructureAllowed(structureType, year)` — a technology gate (railway ≥ 1825,
motorway ≥ 1924, power line ≥ 1882, solar/wind ≥ 1970, aeroway ≥ 1910, pipeline ≥ 1865) — plus
`getRoadBlockForEra(highwayTag, year)` and `getLampBlockForEra(year)` mapping to period block
states.

---

## 3. Where it stops: the event layer is still static

`eternal/src/main/java/com/eternalengine/history/HistoricalEventDatabase.java` (166 lines) is
bundle-only. No network, no location query.

```java
:33  public void loadFromFiles(Path dataDir)
:40    stream.filter(path -> path.toString().endsWith(".json")).forEach(this::loadFileSafely);
:59  public Optional<HistoricalEvent> getEvent(String id)
:63  public List<HistoricalEvent> getEventsInYear(int year)
:67  public List<HistoricalEvent> getEventsInYearRange(int start, int end)
:74  public List<HistoricalEvent> getEventsInRegion(String regionId)   // ← regionId, not lat/lon
:78  public List<HistoricalEvent> getEventsOfType(String type)
:82  public List<HistoricalEvent> getChainedEvents(String eventId)
:97  public List<HistoricalEvent> getPendingEvents(int currentYear, WorldStateManager state, ...)
```

The API is *shaped* for year and region, but everything behind it is the 17 pre-authored JSON packs
in `data/eternalengine/eternal/history/`. There is no `getEventsNear(lat, lon, radiusKm, year)` and
no fetch path.

`data_source_config.json` shows the hooks were anticipated but left off:

```json
{
  "version": 1,
  "offlineFirst": true,
  "allowOverpassFallback": false,
  "allowWikidataFallback": false,
  "terraPlusPlusRequired": true,
  "localBrainOnly": true,
  ...
}
```

### Coverage summary

| Layer | Runtime by (location, year)? | Evidence |
|---|---|---|
| Buildings, roads, rail, bridges, walls, landuse | ✅ **Working** | `OhmStructureIndex` → OHM Overpass by bbox; `isVisibleAt(year)` |
| Terrain / elevation / land cover | ✅ **Working** | terrarium, ESA WorldCover, opentopography |
| Named-place facts (founding, inception, labels) | ✅ **Working in terra3**, ❌ not exposed to eternal | `WikiDataLookup` is terra3-internal; `OhmPresence` reads OHM only |
| Undated-place estimation | ✅ **Working** | `OsmDateInferencer` 8-tier chain; `OhmPresence.nearestPresent` |
| Population by place/year | ⚠️ Static data, runtime math | `PopulationResolver` log-interpolates a census series from the bundled packs |
| Era / technology gating | ⚠️ Hardcoded thresholds | `TerraTimeBridge.isInfrastructureAllowed` switch statement |
| **Events** (battles, epidemics, treaties, successions) | ❌ **Static JSON only** | `HistoricalEventDatabase.loadFromFiles` |
| **Political borders by year** | ❌ **Static JSON only** | `history/borders.json` |
| **Narrative** | ❌ **Does not exist** | no `narrative/` package anywhere in either repo |

---

## 4. Proposed design

Build `HistoricalFactResolver` mirroring the `PopulationResolver` contract — *resolve one fact for
one (lat, lon, year)* — with the same three tiers `OhmStructureIndex` already proves out:
memory `ConcurrentHashMap` → disk under `TerraCachePaths.dir(...)` → network, with a source cooldown
and negative-result caching.

Reuse `WikiDataLookup`'s HTTP discipline rather than writing a second client. Keep terra3 as the
only component that touches the network.

### Priority order

**1. Borders by year.** Highest value per unit of work. "Whose realm is this in 1687?" is one
resolvable query, and it makes `FactionRelationshipSystem`, `civilization/`, and the flag/heraldry
work in `carto/HeraldryRegistry` genuinely location-aware instead of region-string driven.
Candidate sources: **CShapes** (political unit boundaries with from/to years, historical GIS
standard) or Wikidata SPARQL over territorial entities with `P580`/`P582`.

**2. Events by (bbox, year window).** Wikidata SPARQL: `P607` (conflict), `P580`/`P582`
(start/end time), `P625` (coordinate location), filtered to a radius and a year range. This replaces
hand-authored packs like `deccan_events.json` with data that exists for places nobody wrote about.
Wikidata coverage for South Asian pre-colonial history is genuinely thin, so the bundled packs stay
as the fallback — the runtime layer supplements them, it does not replace them.

**3. Population seeding.** Keep `PopulationResolver`'s interpolation and the seeded-rounding
conservation (verified in `THREE_REQUIREMENTS.md`: 166-byte manifest, total conserved to within a
tenth of a percent — that logic is correct and worth preserving). Change only how the census series
gets *seeded*: allow a runtime gazetteer lookup to populate a place the bundled packs never
mentioned, instead of returning nothing.

**4. Expose `WikiDataLookup` to eternal.** It already exists and is battle-tested. A thin
`terrabridge/` accessor following the `OhmPresence` pattern (reflection + cache-path lookup, no new
HTTP client) gets named-place facts into the mod for near-zero cost.

**5. Narrative.** This is the one layer that should *not* be fetched. It should be generated from
whatever structured facts the layers above return — which is what the missing `narrative/` package
is for, and what the advisory-only LLM gateway in `brain/` is designed to do.

---

## 5. Constraints that will shape the design

**Overpass cannot filter by time.** Every temporal query is necessarily "fetch the whole tile,
filter locally." That is already what terra3 does, and it means the disk cache is the feature, not
an optimisation. Do not try to push year filters into the query string.

**`query.wikidata.org` rate-limits aggressively.** `WikiDataLookup` already has the correct
defences — negative-result caching (:262) and non-blocking `prefetch` (:217). Reuse them. A naive
per-chunk synchronous SPARQL query will get the server 429'd within minutes.

**OHM coverage is uneven and the Deccan packs exist because of it.** `OsmDateInferencer`'s tier-8
fallback is a hardcoded `1900`. That is not a bug to remove — it is the offline fallback doing its
job. `offlineFirst: true` in `data_source_config.json` should stay the default; the runtime layers
should be additive.

**Tick budget.** The master plan sets a 10 ms tick budget. All resolution must stay on
`AsyncTaskManager` threads with results returning via the existing queue — the same way
`BlockUpdateQueue` keeps placement on the main thread only.

**Cache poisoning.** `OhmStructureIndex` already had one bug of this class (the `complete=0` stall
at :224-227, where an empty tile was cached and never refetched). Any new cached layer needs the
same invalidation path, or a transient upstream failure becomes a permanent empty world.

---

## 6. What is NOT verified here

The source-level facts above are all checked against the files on disk. The following are **my own
recommendations, not verified against the code or live-tested**:

- That **CShapes** is the right border source, and what its actual coverage is for the Deccan in the
  1600s–1900s.
- That the specific Wikidata properties listed (`P607`, `P580`, `P582`, `P625`) yield usable density
  for South Asian pre-colonial events.
- What OHM actually returns for a given place and year right now.

All three are cheap to test. A single Overpass query against
`overpass-api.openhistoricalmap.org` for a Hyderabad bbox, and a single SPARQL query for events near
Golconda in a year window, would give real density numbers before any code is written.

Also unverified: **nothing in either project has been compiled.** This sandbox has OpenJDK 11; the
build requires Java 17 plus network for ForgeGradle's MCP remapping. So no claim here is
type-checked.

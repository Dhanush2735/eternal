# Hyderabad pilot — implementation status

Started 3 November 2026. Target: Hyderabad-area Earth replica and time-aware history from 3000 BCE to 3000 CE, then review before worldwide expansion. **The implemented slice is a read-only preview; it does not generate or alter a Minecraft world.**

## Implemented

- `src/main/java/com/eternalengine/hyderabad/PilotYears.java`: standalone boundary/future classification. It does not connect to the game clock and does not prove historical evidence exists. Engine year 0 is explicitly flagged as ambiguous; it is not automatically translated or skipped.
- `src/main/resources/data/eternalengine/eternal/hyderabad/charminar_osm_2026-11-03.xml.gz`: lossless OpenStreetMap API 0.6 snapshot of a small box around Charminar, not the whole city. The accompanying metadata records WGS84 bounds, source URL, checksums and attribution. API map ways may extend outside the requested box; relations may be incomplete.
- `src/main/java/com/eternalengine/hyderabad/PilotOsmSnapshot.java`: read-only, securely parsed loader for OSM building/part and highway **way geometry**. It checks node references, closed building ways, duplicates and coordinates; the bundled reader verifies the gzip hash and restricts use to the snapshot year, 2026. The loader does not generate blocks, infer road width or building heights, stitch relations, or reconstruct an earlier landscape. Historic dates of mapped features require separate evidence.
- `tests/run_hyderabad_pilot_tests.sh` compiles the two standalone Java classes with Java 11 or later and tests their boundaries, OSM hashes/attribution, 22 way geometries (4 building-tagged, 7 building-part-tagged and 12 highway-tagged; categories overlap), missing nodes, malformed features, immutable results, and rejection of external XML entities. It also round-trips 212 way vertices through the existing copied BTE projection in a headless probe. This does not verify the actual running Terra++ generator, world coordinates or rendering.

Data: © OpenStreetMap contributors, [copyright and attribution](https://www.openstreetmap.org/copyright), [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/). Raw source: [OSM map API for this box](https://api.openstreetmap.org/api/0.6/map?bbox=78.4742,17.3613,78.4752,17.3621). The API URL gives current data, while the packaged file preserves the fetched snapshot.

## Geographic scope correction

The earlier plan described current GHMC as including Malkajgiri. Administrative reorganisation makes that too simplistic: a February 2026 [report on GHMC, Cyberabad and Malkajgiri corporations](https://www.deccanchronicle.com/southern-states/telangana/ghmc-cyberabad-malkajgiri-municipal-corporations-zones-and-circles-1936494) describes them separately. The pilot should include the broader Hyderabad urban area including Malkajgiri, but its exact polygon needs verified official boundary data; the sample box is **not** a boundary. Historical administrative boundaries must not be inferred from current boundaries.

## Safety and remaining work

- The existing `WorldCalendar`/`KnowledgeDate` combination uses negative BCE labels but Java's `LocalDate` has an astronomical year zero; the BCE/CE mapping must be reconciled and tested before connecting the pilot year policy. No existing calendar behavior was changed.
- Existing `TerraOsmBridge.parseElement` does not populate way geometry; the new reader is a separate pilot path and has not yet been connected to the renderer or terrain generator. Use Terra++'s active generator transform, not a guessed projection; a copied headless probe is not proof of live alignment.
- Existing temporal demolition can enqueue air blocks; player edits and saved-world safety need an audited protection mechanism before any automatic rebuilding. No demolition or world edits were invoked.
- Terra++ replacement build failed earlier; full mod compilation, generator integration, Minecraft tests and natural NPC/VIP spawning have not been completed.
- OSM is present-day source data. It does not establish what existed in any prior year or what will exist in the future. Full historical research, roads, terrain, buildings, people and events across the whole area remain undone.
- User approved using part of displayed storage capacity. The Hyderabad pilot's `HYDERABAD_PILOT_LIMIT_BYTES` is 130,000,000 bytes, leaving about 4.2 million bytes below 128 MiB. Existing R2 collectors retain their 125,000,000-byte guards. Snapshots remain best-effort; this is not a guarantee of capacity.

## Next implementation steps

1. Obtain and verify an authoritative current pilot boundary including the chosen Hyderabad/Malkajgiri area. Gather a geographically representative, attributed OSM baseline in small bounded batches under the pilot storage limit.
2. Build a read-only placement preview using the active Terra++ transform; compare known ground locations in a test world before any block writes.
3. Design safe, reversible placement that respects player builds, then implement and live-test a small area before broadening coverage.
4. Add source-backed dates and uncertainty for selected features, then build out history, ordinary NPCs and non-fabricated VIP roles across the entire time range. Label future scenarios separately.

## Research and implementation sequence (updated 2026-11)
The user initially directed research first and subsequently requested that implementation start. See `../hyderabad_research/PROGRESS.md`, the pinned source archives, and `research_candidates.json`. The first collection contains 43 distinct source revisions (including one explicitly excluded unrelated namesake) and 22 provisional research claims. The first 50 BCE year titles redirect to a single century article; **zero years are fully collected**. None of this research has been installed in active gameplay JSON or made spawnable.

## Source-code integration: read-only live-world preview

- `PilotProjectedPreview.java` turns each mapped way vertex into world X/Z using an injected projection, returning immutable geometry, feature categories and bounds. Any invalid or missing vertex rejects the whole preview. It performs no chunk access or writes.
- The new operator command `/ee hyderabad preview` is registered under `/ee`. It requires the simulation clock to report **2026**, the bundled snapshot year, and requires `TerraBridge` to be bound to the command source's actual level generator. Every vertex must use the bridge's world projection and pass its existing approximate round-trip check. The command reports counts, X/Z bounds and a Charminar boundary vertex, not a teleport point. It prints OpenStreetMap attribution. If these checks fail, it refuses; it does not fall back to a guessed API projection.
- This command previews coordinates and counts in chat; it **does not render buildings or roads**, change blocks, protect player structures, or create NPCs. A successful round trip is not proof of precise live alignment. OSM snapshot ways can extend outside the tiny requested bounding box.
- `bash eternal/tests/run_hyderabad_pilot_tests.sh` passes the year, snapshot, projected-geometry and copied headless BTE tests. `bash eternal/tests/run_hyderabad_command_mock_test.sh` compiles and exercises the command using mock Minecraft/Brigadier/Terra classes, including permissions and fail-closed cases. The mock does not prove compatibility with actual Forge or the game.
- Full mod compilation and an in-game test have **not** been performed for this change: this environment currently has Java 11, while Minecraft 1.20.1/Forge needs Java 17, and the replacement Terra++ build previously failed. No distributable JAR has been produced. Test only after a successful Java 17 build in a separate backup/test world.

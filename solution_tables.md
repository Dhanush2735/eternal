The places, and what the view holds
| place, 2026 | the resolver's number | tier | extent | people a m2 | figures in view (conc 40) | one person every | at the cap? | frame as entities |
|---|---|---|---|---|---|---|---|---|
| hyderabad | 7,677,018 | interpolated | 8 km | 0.0382 | 3,465 | 3.3 m2 | yes, 77 a chunk | 82.5 ms |
| secunderabad | 217,910 | interpolated | 6 km | 0.0019 | 751 | 14.3 m2 | no, 20 | 17.9 ms |
| maheswaram_mandal | 75,525 | modelled | 10 km | 0.0002 | 98 | 108.5 m2 | no, 3 | 2.3 ms |
| akanpalle | 1,047 | interpolated | 3 km | 0.0000 | 16 | 800.0 m2 | no, 1 | 0.4 ms |

What gathering does to the count
| place | even division (conc 0) | conc 40 | conc 200 | conc 800 | the view's ceiling |
|---|---|---|---|---|---|
| hyderabad | 439 | 3,465 | 3,465 | 3,465 | 3,465 |
| secunderabad | 23 | 751 | 3,328 | 3,465 | 3,465 |
| maheswaram_mandal | 3 | 98 | 455 | 1,714 | 3,465 |
| akanpalle | 2 | 16 | 61 | 150 | 3,465 |

The cost, measured
| what | cost | how measured |
|---|---|---|
| the population number | 0 per tick | a lookup in a loaded pack, cached per place-year |
| server ticks spent on the crowd | 0 | the drawn figures have no AI, no goals, no navigation; the living NPCs are untouched |
| the transport | 166 bytes | `Manifest.encode()` = 21 bytes + 3 a chunk, 45 chunks, sent only when the view changes |
| the same crowd as spawned entities | 82.5 ms a frame | 3,465 figures x 23.8 us |
| the same crowd as recorded geometry | one draw call | 124,740 quads, 15,593 KB, 31 chunk sections, built once |
| what the client draws when it cannot afford the view | the 400 budget | `sampledView` keeps every chunk's relative density and thins everywhere at once |

The option space
| decision | the options | what was chosen, and why |
|---|---|---|
| which rule produces the number | the resolver; log-lerp; arithmetic; constant-rate; constant-absolute; carry-forward; carry-backward; the two fixes (leave-one-out over the 13 testable census records) | the resolver - it is the only one that reads the records, and where it has records it is the best of them (5.7% median). Its two refusals are the measured defect, not the formula |
| where the crowd stands | one square at the heart; evenly over the extent; gathered over the extent (concentration); weighted by the packs' named localities | gathered over the extent. A single square was rejected at 0.014 a m2; even division measures 1 person every 457 m2 at Secunderabad and is invisible; the locality list was checked and carries no 16 m structure (530 localities over 526 distinct chunks) |
| how strongly they gather | concentration 0 ... 400 (the config's range) | one value cannot serve both a village and a city: the probe shows a city reaching the crowd density at 200 and a village reaching 4 figures a chunk at 800 |
| how the crowd is drawn | ticked entities; recorded geometry as one mesh; instanced draws; a batching TESR | recorded geometry. Measured: entities cost 23.8 us each, so the 3,465-figure view cannot be ticked; one mesh is one draw call and 15.6 MB |
| what the client draws when the view is too big | draw the nearest N; draw a uniform sample; thin every chunk by the same factor | thin every chunk by the same factor (`sampledView`, largest-remainder) - the crowd thins everywhere at once instead of losing its far half |
| what the NPC layer does | spawn NPCs to fill the area; leave vanilla alone | leave it alone: 5-40 living people with names and answers, never where the player stands (vanilla's 24-block minimum). The crowd is a drawing, not a spawn |

What is built
| piece | state |
|---|---|
| `PopulationResolver` - the number | written, compiled, tested; 253 assertions in `run.sh` |
| `ChunkPopulation` - the division, capacity, view, manifest | written, compiled, tested; the numbers in the panels above are its output, run this turn |
| `CrowdManifestPacket` - the transport | written, compiled, tested; 166 bytes, change-only |
| `CrowdManifestStore` (client) | written, parses the manifest; **nothing draws from it yet** |
| the renderer that builds the crowd as geometry | **not written** |
| the two resolver fixes | measured by leave-one-out, **not applied** - the engine is unchanged |
| `SolutionProbe` | written this turn, compiled, run; `solution_chain.tsv` |
| the NPC spawning system | untouched, vanilla's own |

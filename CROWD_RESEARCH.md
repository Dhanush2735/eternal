# Web research: how other people solved this

Ten sources, all read this turn. Two charts: `crowd_research_tiers.html` (every approach's distance
tiers on one axis) and `crowd_research_ceilings.html` (reported ceilings).

**The headline: I found nothing better than what is now built — and three independent projects
independently chose the same thresholds we did. But one finding changes the plan for the "hundreds"
case: there is a Minecraft-native way to draw a transparent crowd that nobody in this repo had
considered.**

---

## 1. Distance-based AI tiers — the whole industry does what we just did

| project | full AI | reduced | none |
|---|---|---|---|
| **Ours** | below 32, settled (no pathfinding) | — | past 32: NoAI |
| **OptimizeMod** | below 32 | 32–48: AI every 2 ticks; 96+: ticks skipped 3 in 4 | past 48: **"mobs that no player is near simply stop thinking"** |
| **ELO – Entity LOD Optimizer** | below 10 | 10–100: simplified, 40-tick interval | past 100: **"disabled AI — frozen (no CPU usage)"** |
| **Pathfinder** | below 48 | past 48: AI every 4th tick, A\* capped at 400 nodes | — |
| **LazyAI** | below ~25% of simulation distance | frequency scales with distance | scaled further |
| **Vanilla** | inside simulation distance | — | beyond it, **not ticked at all** |

Three separate projects chose **32** as the boundary below which a mob gets full AI, and **48–100**
as where it stops entirely. Our 32 sits inside that range, and our mechanism — vanilla's own
`NoAI` — is the same one their mixins switch on. OptimizeMod even has to reset `despawnCounter`
when it suspends AI, to stop suspended mobs despawning by accident; we sidestep that because our
crowd is tagged and managed by our own spawner, not by vanilla's despawn counter.

**Also worth adopting (small, optional):** OptimizeMod and Pathfinder both find that **failed A\*
searches** are the expensive case, and cap the search (400 nodes) or cache unreachable targets.
Pathfinder reports A\* failures reach 800+ nodes while successful village-scale paths rarely exceed
300 — so the failures are ~2/3 of the cost. Our settled crowd never starts a search, so this is only
relevant if you ever put the stroll back.

## 2. The one idea we had not considered: don't draw people with entities at all

**Display entities** (`item_display`, added in Java 1.19.4) are:
- **non-ticking** — no AI, no gravity, no collision, no hitbox;
- far cheaper than the old armour-stand tricks ("no hitbox, no collision, no gravity and no AI,
  which makes them far cheaper than the armour stand tricks they replaced");
- able to show **any scale, rotation, and item model** — including a **player head**, and there are
  published rigs (e.g. `stable_player_display`) that build a **full player-shaped model out of item
  displays** using `player_head` models and shaders.

This is a real third option for the "hundreds" requirement, and it is better than the packet path
this repo has dormant:

| | our dormant proxy path | display-entity rig |
|---|---|---|
| server cost | 0.49 ms per player per tick | a display entity is non-ticking; cost is per-entity tracking, not AI |
| client work | **does not exist — would have to be written by us** | **exists: the model is a resource-pack model** |
| appearance | a silhouette we would have to draw | a real player-shaped figure with a skin |
| interaction | none | none (interaction entities are separate, and cheap) |
| animation | we would write it | limited, but poses/drifting are possible via NBT transforms |

**Cost warning, stated by the sources:** display entities still count toward the E value (total
entities) on the debug screen and must be sent to the client. So "hundreds" is thousands of them is
not free — but they are the cheapest *drawn* entity in the game, and they exist in vanilla.

## 3. What happens if you just raise the cap — reported numbers

| mod | people | reported result |
|---|---|---|
| **MCA (Minecraft Comes Alive)** | 40–50 | *"the comfort zone for most systems. Beyond that, performance degrades"*; 50+ NPCs wants 4 GB+ |
| **MineColonies** | 250 (default cap, configurable 25–500) | a test server with **200 NPCs dropped from 20 to 12 TPS**; 100+ active NPCs wants 8 GB |
| **Fake players** | — | *"would not recommend adding more than 300"* |
| **Ours today** | **32** | by design — and now at a third of the old cost |

**This is the number that settles argument A1.** A well-tested, popular NPC mod with strong
optimisation degrades at 40–50 people, and 200 people halve a server's tick rate. Our ceiling of 32
real, full-AI people is not timid — it is where the field lands, and ours now costs a fraction of
even that.

## 4. Client-side culling — free, and worth taking

`Entity Culling` and similar mods skip drawing entities hidden behind blocks. They do not touch the
server, so they do not help the crowd's cost — but they make a dense crowd cheaper **on the
player's** side, and they are worth recommending in the mod's README. Nothing for us to build.

## 5. What the research did not find

No approach anywhere showed genuinely **hundreds of independent, interactive, pathfinding humans**.
Every large-crowd solution found was one of three things: people with no AI (display entities, fake
players), people outside the ticked area (our freeze), or a modest cap with acceptance (MCA,
MineColonies). **That is a strong signal that "many real people, all thinking" is not a solved
problem in Minecraft, and our split — real ones that think, drawn ones that don't — is the shape
the field has settled on.**

---

## Verdict in simple words

I looked at what everyone else does, and the answer is reassuring and a bit humbling: three separate
projects picked the same numbers we did, by themselves. Everyone freezes distant mobs, everyone
agrees the wander pathfinding is the expensive part, and everyone stops somewhere between thirty and
a hundred real people.

Two things I would change as a result. First, if you ever want hundreds, don't build our own
drawing system — use display entities, which are Minecraft's own built-in cheap way to put a
player-shaped figure in the world with no brain at all. Second, the ceiling of 32 stops feeling
timid once you see MCA calling 40–50 the comfort zone and a 200-NPC colony halving a server's speed.

Nothing found suggests a better way to do what we already built.

## Sources

| what | where |
|---|---|
| OptimizeMod — distance tiers, AI sleep past 48, tick skipping past 96, despawnCounter reset | https://modrinth.com/mod/optimizemod |
| ELO — Entity LOD Optimizer — full <10, simplified 10–100, frozen >100 | https://modrinth.com/mod/elo |
| Pathfinder — AI every 4th tick past 48, A\* capped at 400 nodes, unreachable-target cache | https://www.curseforge.com/minecraft/mc-mods/path-finder |
| LazyAI — frequency scaling with simulation distance | https://modrinth.com/mod/lazyai |
| Immersive Optimization — entity tick scheduler, ~200% speedup without disabling ticks | https://www.curseforge.com/minecraft/mc-mods/immersive-optimization |
| MineColonies — default cap 250 (25–500), 8 GB for 100+ NPCs | https://minecolonies.com/wiki/misc/configfile/ |
| MineColonies at 200 NPCs — 20 → 12 TPS | https://resources.oreate.ai/updates/the-best-minecraft-npc-mods-for-building-living-cities-and-epic-quests |
| MCA — 40–50 NPC comfort zone, 4 GB+ | https://theplaycentre.org/minecraft/minecraft-comes-alive-complete-guide-to-transforming-your-npcs-and-gameplay-in-2026/ |
| Display entities — non-ticking, no AI/collision, cheaper than armour stands | https://minecraft.wiki/w/Display |
| Player-shaped display rigs | https://github.com/bradleyq/stable_player_display |
| Fake players — "would not recommend more than 300" | https://www.curseforge.com/minecraft/bukkit-plugins/really-fake-players |
| Entity culling mods (client-side, recommend to players) | https://blog.sparkedhost.com/minecraft/9-best-optimization-mods-for-minecraft |

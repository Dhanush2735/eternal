# Freezing a crowd in unloaded chunks — cheap or expensive?

**Short answer: neither. It's storage.**

An entity in an unloaded chunk is not in the game at all. It is bytes in the region file: not in
memory, not ticked, not visible, not touchable. That is the cheapest state anything in Minecraft can
be in — and it is also one you never see, so it is not a state you can *do* anything with.

The question only becomes interesting in the state where you *can* see them.

Picture: `npc_chunk_cost_2d.html` (four states, ranked).

---

## The four states, cheapest first

| state | ticks | in memory | visible | touchable | what it is |
|---|---|---|---|---|---|
| **Unloaded chunk** | 0 | no | no | no | bytes on disk |
| **Loaded, not ticking** (lazy chunk — inside render distance, outside simulation distance) | 0 | yes | **no** | no | object alive, never ticked |
| **Ticking, frozen** (NoAI) | baseTick, physics, tracking | yes | yes | yes | body real, thinking stopped |
| **Ticking, with AI** | all of the above **plus 5 goals**, one of which path-searches | yes | yes | yes | today, always |

Two things here surprise people, and both matter:

**1. A crowd in a lazy chunk is invisible.** Java only sends entities to your client from *ticking*
chunks. So a crowd parked just outside simulation distance isn't a crowd — it's empty ground you
can see through. You would walk up to a village that looks deserted until the chunks start ticking.

**2. Unloaded is not "cost paid", it's "cost deferred".** Saving a chunk with hundreds of entities
inside means serialising all of them at unload; loading it means deserialising and then ticking all
of them at once. Absence is free; the return is a burst.

## Where our crowd actually lives

**Our crowd never reaches the first two states, by design.**

- The spawner only ever places a person in a **loaded** chunk (`hasChunkAt` guards in
  `spotInDistrict` and `districtCenter`), and only 24–128 blocks from you. A crowd is always born in
  the bottom state.
- When you walk away it is **deleted, not parked**: past 128 blocks it is discarded outright; past
  32 blocks it is discarded by chance — 1 in 800 a tick, after 30 seconds. Both are vanilla's rules,
  both already implemented, both verified in suite 6.

So there are no stale crowds sitting in your world, no save cost, no reload burst, and no memory
held for people you can't see. The vanillas despawn rules are, in this sense, already a
cost-management strategy: they guarantee the crowd exists only while somebody could be looking at it.

Worth noting for peace of mind: our NPC is registered as `MobCategory.MISC`, so it does not eat the
monster or creature mob caps. Parking a crowd would not starve vanilla's own spawning.

## If you wanted people who wait while you are away

Two options, and they are not close:

**Park real entities** (keep them alive, let the chunk unload). You get a free absence and a bill on
return: NBT written at unload, all of them ticked at load, RAM the whole time, invisible whenever the
chunk isn't ticking. This is the worst combination — you pay to keep them *and* you can't see them.

**Virtual agents** — what the dormant ECS path does. Measured here: exactly **32 bytes each**;
100,000 people in 3.2 MB, no entity, no chunk, no save file, and a scan of all 100,000 for the ones
near a player takes **0.49 ms**. There is no chunk state at all: an agent is "there" in the data
whether the world around it is loaded or not, and it costs the same wherever it is. That is the only
route to a crowd that persists across the map for free.

The trade is unchanged from the alternatives list: virtual agents are cheap and always present but
invisible until something draws them (B1), and they cannot be interacted with.

## The one sentence

Unloaded chunks cost nothing because nothing exists there — which is why a crowd you can't see is
free, and why keeping one there buys you nothing either. Freezing only means something in the state
where you *can* see them, and there it is cheap but not free: the body still falls, collides and
gets drawn; only the thinking stops.

## Verdict in simple words

An NPC in an unloaded chunk is not frozen — it's not there. It's a note in a file: no cost, no
presence, no use. The moment you can see someone, the chunk is loaded and ticking, and *then*
freezing does what we said: it stops the thinking while leaving the body real. And our crowd never
sits in that parked state anyway — when you leave, it's deleted, exactly as vanilla deletes any mob
you've walked away from. Nothing to clean up, nothing paid for people you can't see.

If you ever want people who wait for you across the map, the answer isn't parking them — it's the
32-byte virtual agents, which have no chunk at all.

# Two questions: why is the crowd countable, and what happened to names and skins

**2026-10-05.** Both questions had a real defect behind them. The second was worse than the first.

---

## 1. Why is the crowd countable?

Because a crowd in this mod is only ever a handful of real entities, and everything that usually
makes a crowd *uncountable* is either not wired or too coarse. Concretely, right now:

| what the player sees / hears | what drives it | why it reads as countable |
|---|---|---|
| the people themselves | the spawner: **4 townsfolk** (one per role), up to 3 scene roles | you can count four |
| a name floating over every head | `VillagerManager` sets `setCustomNameVisible(true)` at spawn | you can read them off one by one |
| their faces | `SkinMatrixRegistry` returned **Steve for everyone** | four identical clones is not a crowd |
| the sound bed | `AmbientCrowdSoundManager`: two thresholds — 100,000 people, or 15,000 | nothing between "chatter" and "bustle" |
| anyone further than the spawned group | `AgentVirtualizationEngine` / `RadarProxyController` | **nothing populates the ECS in play** |

That last row is the interesting one. The mod has a whole three-tier crowd design — a Struct-of-Arrays
population, a "radar proxy" layer for agents 64–192 blocks out, materialisation on approach — and it
is **not running in a normal game**:

- `populateMassive(...)` has exactly one caller, a command. Nothing fills the array during play.
- So `collectProxiesNearPlayer(...)` always returns nothing.
- And nothing client-side renders a radar proxy anyway, so even a filled array would be invisible.

That is why the crowd is countable: the only channel that exists is discrete entities, capped at four
by the original design you asked me to keep.

**What I changed this turn** (without touching spawn counts): the four people no longer share one
face — nine vanilla skins, spread evenly — and a nameplate now only floats within 24 blocks. At 24–128
blocks out you see figures, not a list of names. Up close you get the name, the role and the face.

**What would finish the job, if you want it** — each is a real change, not a tweak:

1. **Wire the crowd to the population number, for sound and visuals only.** The resolver already gives
   a figure per place per year, and `AmbientCrowdSoundManager` already uses it — but as two thresholds.
   A continuous bed (density → volume, layers, sporadic calls) makes a city of 142,174 *sound* like
   one while four bodies stand in it.
2. **Fill the ECS from the population at the border box scale**, and render radar proxies as distant
   silhouettes. That is the design the code was written for; it is a substantial piece of work, and it
   is the only way a crowd can be genuinely uncountable.
3. **Raise the cap.** The original's four per player. Cheap, and it changes the feel you liked.

---

## 2. What happened to NPC names and skin?

Both systems existed, and both had been quietly broken since before this project started.

### Names

```
<Villager Alex Stone> Word from nearby: Operation Polo (Hyderabad). Soldiers on the roads...
```

That line is not a missing feature — it is what the code was guaranteed to produce, twice over:

1. **`names/foundation_names.json` is installed into the world and was never read by anything.**
   `FoundationDataInstaller` copies it to `<world>/eternalengine/names/`, and no code opened it.
2. The only pools that existed were the two hardcoded in `VillagerNameDatabase`: `rome` and `maurya`.
   Every other id — `india`, `hyderabad_state`, `british_india`, `witness`, all 103 the corpus cites —
   fell through to `global`, whose pool is **Alex, Sam, Stone, River** and the title **Villager**.

So: Alex Stone, in Deccan Hyderabad, in 1948.

**Fixed:**

- The pack is loaded at server start, world folder first, jar as fallback — the same shape as the
  population pack and the civilization table.
- A civ with no pool of its own now resolves by **culture group** rather than to `global`. Verified
  against the real corpus: **all 103 cited civ ids now resolve to a real pool**, none to generic.
- The Deccan pool is written out in full, with period occupational words: a cultivator was a **ryot**,
  a trader a **vepari**, a soldier a **sipahi**, a scribe a **munshi**, a priest a **purohit**.

What that produces now, at 17.4346, 78.5016 in 1948:

```
hyderabad_state  farmer    -> Ryot Rajanna Baig          (pool deccan)
india            citizen   -> Sri Ahmed Pawar            (pool deccan)
british_india    merchant  -> Setty Yellaiah Ansari      (pool deccan)
qutb_shahi       guard     -> Sipahi Hasan Rao           (pool deccan)
kakatiya         artisan   -> Shilpi Mohammed Sharma     (pool deccan)
mughal                     -> (pool indic)
ming                       -> (pool east_asian)
```

300 draws for one civ give **211 distinct names**. Gender is now set at spawn — it never was, so the
female name lists were unreachable and every NPC was "neutral" — which is why `Ryot Rahimunnisa
Kulkarni` could not happen before and can now.

### Skins

`SkinMatrixRegistry` did this:

```java
ResourceLocation candidate = new ResourceLocation(MOD_ID, path);   // worked out
if ("female".equals(gender)) return DEFAULT_ALEX;                 // ...and discarded
return DEFAULT_STEVE;
```

The candidate path was **computed and then thrown away on the next line** — it was never even checked
for existence. And the mod ships **no human textures at all** (`assets/eternalengine/` contains only
`lang/` and `models/`). So every NPC in every era, in every country, was Steve. The gender branch
never fired either, because nothing set gender.

**Fixed:**

- The candidate path is **used** when the texture exists, so a resource pack can add real period skins
  by dropping files in — no code change.
- Otherwise the NPC takes one of **Minecraft's nine default skins** — steve, alex, ari, efe, kai,
  makena, noor, sunny, zuri — picked from its UUID. Nothing to install: this is the same mechanism the
  game uses for a player who never chose a skin.
- The cache key now includes the entity's UUID. Before, even a working pick would have given every
  person of one culture and role the *same* skin.

Measured over 5,000 NPCs: **all nine appear, 10.3 % to 11.8 % each**, and a given NPC keeps its face
across restarts.

### The nameplate rule

A floating name over every head is most of what makes a crowd countable, so it is now distance-gated
in `[presence] presenceNameTagBlocks` (default **24**). A speaking NPC keeps its label at any distance
— the bubble is the news, and it is only ever set on someone you are meant to hear. This applies only
to the NPCs the spawner owns; VIPs, plague doctors, caravans and bandits are untouched.

---

## Verification

`./verification/population/run.sh` — **eight suites, all pass** (was six):

```
=== 2. Villager names ===      NAME DATABASE: ALL PASSED
=== 3. Skin variety ===        SKIN VARIETY: ALL PASSED
=== 4. CivSynthesis ===        CIV SYNTHESIS: ALL PASSED
=== 6. Presence spawning ===   VANILLA SPAWNING: ALL PASSED
=== 8. Civ persistence ===     CIV PERSISTENCE: ALL PASSED
```

Two new suites and a shared reader:

- `VillagerNameDatabaseTest` + `CorpusIds` — reads the real corpus and asserts every cited civ
  resolves to a real pool. It is what caught the 42 civs I had missed (`teotihuacan`, `zimbabwe`,
  `minoan`, `pala`, `holkar`…), which is the point of running tests instead of trusting a list.
- `SkinVarietyTest` — the nine names, the even spread, stability per UUID, path shape, sanitisation.

`static_audit.py` — **CLEAN, 85 checks** (was 66), including `names pack is loaded at boot`,
`candidate texture is actually used`, `skin choice is per NPC`, `gender is set at spawn` and
`nameplates are distance-gated`.
`parse_check.py` — **19 modified files parse clean** under javac. It was reporting 15 while silently
skipping a file I had deleted; both the count and the missing entry are fixed.

Full output: `verification/population/last_run.txt`.

**A compile error and a bad expectation, both caught by running:** `strings(JsonElement)` was missing
(a role-title map is an array, not a `JsonObject`), and a test asserted "mean distance 92.1" for a
uniform ring when the closed form gives 87.86 — the code was right, my arithmetic was not.

---

## Verdict in simple words

**Why is the crowd countable?** Because the crowd is four real people with the same face, a name
floating over each one, and nothing else carrying the weight — the sound only has two settings, and
the big crowd engine the mod already has never runs in a normal game. I fixed the faces and the
nameplates; making a crowd truly uncountable means wiring the crowd engine or the sound, and those
are bigger jobs I can do if you want them.

**What happened to names and skin?** Nothing happened to them — they were never working. The name
file the mod installs into your world was never opened, so everyone outside Rome and Maurya fell back
to "Alex, Sam, Stone, River" with the title Villager. And the skin code worked out the right file name
and then threw it away, always returning Steve; the mod ships no textures of its own, so nobody ever
had a different face.

Now: a farmer in Hyderabad in 1948 is **Ryot Rajanna Baig**, a soldier is **Sipahi Hasan Rao**, a
trader is **Setty Yellaiah Ansari** — and the four people standing there have four different faces.

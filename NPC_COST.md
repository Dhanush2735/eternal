# How to make an NPC cost less

> **Status: built.** A2 and A3 are implemented and verified — see `THE_SOLUTION.md`.
> The code below matches what is in the repo now; only the "how to check it" section is still yours to run.

**The doubt:** a person is a person — how does a flag make one cheaper?

**The answer:** the cost was never the *person*. It is the **thinking**, twenty times a second, for
every one of them. An NPC that exists but does not think is still a real body in the world — you can
see it, name it, talk to it, hit it. It just isn't deciding anything. Stop the deciding and the cost
goes; the body stays.

Picture: `npc_cost_2d.html` (four sections, with the numbers).

---

## 1. What one of our NPCs does 20 times a second

From `HumanNpcEntity.registerGoals()` — five goals, checked every tick:

| goal | what it is | cost |
|---|---|---|
| `FloatGoal` | swim up out of water | cheap; only acts in water |
| `OpenDoorGoal` | open a door in the way | cheap; a block lookup |
| **`WaterAvoidingRandomStrollGoal`** | **pick a spot ~10 blocks off and walk there** | **the expensive one: an A\* path search, then a path followed node by node** |
| `LookAtPlayerGoal` | turn to face you | cheap; one angle |
| `RandomLookAroundGoal` | glance about | cheap |

**Exactly one of the five runs a path search, and it is the one that costs.** That is the whole
structure of the bill.

## 2. What removing it does, measured on our own crowd

35 people, at 20 AI ticks per second each = **700 ticks/s, always** — including while the crowd is
64 blocks away and you cannot see it.

| you stand | awake of 35 | AI ticks/s | vs before |
|---|---|---|---|
| 64 blocks | 0 | 0 | 100% less |
| 48 blocks | 2 | 160 | 95% less |
| 32 blocks | 6 | 480 | 86% less |
| 24 blocks | 17 | 1,360 | 61% less |
| 16 blocks | 33 | 2,640 | 25% less |
| 8 blocks | 35 | 2,800 | 20% less |

Average across an approach: **1240 ticks/s, 65% less**, and **zero** beyond 32 blocks. A count of AI
ticks is a proxy for cost, not milliseconds — see §6.

## 3. The two changes

**A3 — freeze whoever nobody can see.** Vanilla's own `NoAI`, set by the spawner past 32 blocks and
cleared as you come back. Vanilla does exactly this at the simulation-distance boundary; this is the
same rule at a distance you can see.

**A2 — stop the crowd pacing.** A crowd member in a district keeps looking around and stops picking
destinations. One goal removed from one entity. Three blocks apart in a square is also the worst
place to walk about: people bump, paths block, and A\* re-plans — which is the expensive failure
mode, not the cheap one.

## 4. Not my theory — other people's measurements

| evidence | number |
|---|---|
| A server with one mob type's AI switched off (the cod) | that mob was **50.1% of CPU time, 41% of it the goal selector**; NoAI gained **5 TPS** |
| Mobtimizations (a performance mod), All the Mods 7 | **38 → 30 ms** average, **95 → 45 ms** worst tick, from throttling wander pathfinding and targeting |
| Mobtimizations, DeceasedCraft | **32 → 24 ms** average |
| The Pathfinder mod | throttles AI to **every 4th tick** beyond 48 blocks, caps A\* at **400 nodes** — because failed searches reach 800+ |
| Vanilla itself | mobs beyond the simulation distance **are not ticked at all** |

Removing wander pathfinding alone has taken 20–50% off a modded server's tick load. I am not
proposing anything exotic.

## 5. The change itself — two files, about 25 lines

```java
// ---- 1. HumanNpcEntity: a crowd member stands and watches, instead of wandering ----
private Goal stroll = new WaterAvoidingRandomStrollGoal(this, 0.7D);

/** A crowd member in a district. It keeps looking around, but stops picking
    a destination: the only goal of the five that runs a path search. */
public void setSettled(boolean settled) {
    if (settled == this.settled) return;
    this.settled = settled;
    if (settled) goalSelector.removeGoal(stroll);
    else         goalSelector.addGoal(2, stroll);
}

// ---- 2. PresenceLocalSpawner: freeze whoever nobody can see ----
/** Past this many blocks, a crowd member stops thinking. Vanilla's own trick,
    at a distance the player can see: it wakes before you are close enough to tell. */
private static final int SETTLE_BLOCKS = 32;

private static void settleDistant(ServerLevel level, List<HumanNpcEntity> nearby) {
    double radiusSq = (double) SETTLE_BLOCKS * SETTLE_BLOCKS;
    for (HumanNpcEntity npc : nearby) {
        if (!isPresenceNpc(npc) || isEventNpc(npc)) continue;
        npc.setSettled(true);
        // NoAI also stops them falling, so anyone whose ground vanished wakes up
        // and sorts itself out -- using the ground check the spawner already has.
        boolean groundGone = !isSpawnable(level, npc.blockPosition());
        boolean frozen = !groundGone
                && nearestPlayerDistanceSq(level, npc) > radiusSq;
        if (npc.isNoAi() != frozen) npc.setNoAi(frozen);
    }
}
// called from the cycle that already walks this list, next to vanillaDespawn(...)
```

No new system. `setNoAi` is vanilla; the spawner already walks this list three times a cycle (to
enforce the district and hide nameplates); `isSpawnable` and `nearestPlayerDistanceSq` already
exist. Nothing here touches spawning, capping, or the population number.

### The honest caveats

- **NoAI also stops them falling** (documented behaviour). So a frozen person whose ground is dug
  out would hang in the air. The `groundGone` line above wakes anyone in that state, and the crowd
  is only ever placed on valid ground — but it is worth knowing the freeze is not free of edges.
- **A frozen person does not turn their head.** Irrelevant at 32+ blocks, and they wake before you
  can tell the difference.
- **Throttling is the gentler variant.** The Pathfinder mod runs distant AI every 4th tick instead
  of stopping it. Slightly more cost, slightly smoother. If you prefer that, it is the same hook
  with a tick counter.

## 6. How you check the real milliseconds

I cannot measure milliseconds here — there is no Minecraft in this sandbox, only the mod's own
classes. On your machine:

1. Open a world with a district nearby.
2. `/debug start`, walk from 64 blocks out into the square, `/debug stop`.
3. Read the `tick > entity` section of the report.
4. Do it with the change and without.

That number is the answer, and it is on your machine, not in this document.

## Verdict in simple words

A crowd is expensive because every person is deciding what to do, twenty times a second — and one
of those decisions, "go for a wander", is far more expensive than all the others put together,
because it makes the game search the world for a route. Nobody is watching when they're far away, so
let them stop deciding. And in the square, let them stand and look around instead of pacing.

Nothing about the person changes. They're still there, still solid, still named. They just stop
thinking when it doesn't matter — the same trick the game itself pulls on every mob outside your
simulation distance. Measured on our crowd, that's 65% less thinking during a walk-in and none at
all beyond 32 blocks. Other people have done the same thing and taken a quarter to half of a
server's load off with it.

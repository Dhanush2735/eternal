# My choice

**Ship what is built. Change one number only if you want more people, and make it 40, not 64. If
you ever want hundreds, use Minecraft's display entities — not the renderer I was going to write.**

---

## 1. Ship it

The three requirements are met and verified:

| requirement | state |
|---|---|
| the resolver's number decides the crowd | built, suite 1 + 5 |
| minimum cost | built this turn, suite 6 |
| it looks like a crowd in an area | built, suite 6 |

8 suites pass, `static_audit.py` is CLEAN at 129 checks, 20 files parse clean. Nothing else is
needed to answer what you asked. The remaining step is yours: `./gradlew build`, then `/debug` once to
see the milliseconds.

## 2. Measure before raising anything

The one number I cannot produce here is milliseconds. Stand 64 blocks from a district,
`/debug start`, walk in, `/debug stop`, read `tick > entity`. Do it with the change and without.

**Why this matters before touching the ceiling:** what I measured is AI — goal checks. What A2 and A3
reduce is AI. What they do *not* reduce is the body: physics, collisions, being tracked and sent to
your client. That part scales straight up with the head count, and a crowd inside a small district
also means more pairs of bodies near enough to collide — which is quadratic, and the classic way a
crowd turns into a lag spike.

So the honest position: the *thinking* is cheaper by a third, the *bodies* cost what bodies always
cost. Raising the ceiling is not free, and it is not double either.

## 3. If you want more people: raise to 40, not 64

One constant, and I would stop at 40–48:

- **MCA**, a well-tested NPC mod, calls **40–50 the comfort zone** and says performance degrades past
  it. MineColonies at 200 halved a server's tick rate. That is the field's limit, and those mods let
  their people pathfind — ours do not, so we sit above their limit, not below it.
- **32 → 40** is a 25% richer square for a cost increase well under what the AI saving just bought
  back. **32 → 64** doubles the bodies to buy a picture that is already dense: 40 people in that
  district is 25 m² each, 64 is 16 m² each — crowded to the point of clipping into each other.

If you raise it, the change belongs in `SettlementDensity` — one number — and suite 5's table moves
with it. Config already exposes the district radius, so density can be tuned in-game too.

## 4. If you want hundreds, later: display entities

The research changed my mind here. I was going to point you at the dormant packet path and a renderer
we would write. Minecraft already has a cheaper answer:

- `item_display` is **non-ticking** — no AI, no physics, no collision, no hitbox;
- published rigs build a **full player-shaped figure** out of item displays with a real skin;
- the model lives in a resource pack, so the client work is already done by other people.

It is still not free — displays count toward the entity total and are sent to clients — but it is the
cheapest *drawn* entity in the game and it exists in vanilla. That is the route to a square with
hundreds of figures, and it is a smaller project than the one I described.

## 5. What I would not do

| | why not |
|---|---|
| **A4** — server-wide entity/simulation ranges | changes every mob and farm in the world to help our crowd |
| **C1** props, **C2** festival surges | real work, neither adds people; revisit only if the square still feels thin after a crowd is standing in it |
| **D1** `VillagerVirtualizer` | dead code with no callers; resurrecting it gives two competing ways to do one job |
| **A custom crowd renderer** | superseded by display entities |

## 6. One correction I owe

In `THE_SOLUTION.md` I wrote that "64 people cost 18% less than today's 29". That was measured on AI
goal checks, and I stated it as if it were the whole bill. It is not: the bodies are unaffected by
A2 and A3. The document now says what is true — the doubled crowd spends less on *thinking*; the
total is a wash rather than a saving.

---

## Verdict in simple words

My choice: stop here and ship. The crowd looks right, comes from the real population number, and
thinks only when you can see it — that is what you asked for, and none of the research turned up a
better way to do it.

If you want it richer, raise the ceiling from 32 to 40, not 64 — the people in the square do not
walk around any more, so we can hold more of them than the mods that do, but bodies still cost money
and a packed square is where lag comes from.

And if you ever want hundreds, don't let me write a renderer. Minecraft already has non-ticking
display entities that can wear a player's shape, and that is the cheap, sane route.

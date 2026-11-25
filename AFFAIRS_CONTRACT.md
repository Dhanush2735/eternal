# The affairs contract — one page

**Status: planning. Nothing here is built.** This is the whole of what a person says when clicked,
settled by the planning session. It is written so it can be checked, not just read.

## 1. The trigger

- **Empty main hand + right-click** → he talks. **With an item in hand**, the item is used on him
  (the person must fall through to `super.mobInteract`). Today the person talks on any click and the
  item is swallowed — that is the bug this replaces.
- One click = **a short line over his head** + **the account in chat**.
- **Only when clicked.** No ambient speech, no murmur, no per-tick work.

## 2. The two switches

| switch | what it reads | what it does |
|---|---|---|
| **the world** | `WorldStateManager.getActiveTimelineBranchId()` — `"main"` is the true world | true world = **the record only**; any branch = **as it is lived** |
| **the year** | the person's own `era = "year_<year>"` | he speaks the year he was stood up in, for ever; his body still follows the live clock |

## 3. What may be said — the source gate

| source | true world | alternate timeline |
|---|---|---|
| the record: 13 event files, **15,027** events | **yes, up to his held year** | yes |
| the climate record (Little Ice Age 1300–1850, Year Without a Summer 1816) | yes | yes |
| census rows (Hyderabad **448,466** in 1901) | yes | yes |
| borders, and a place's identity (there is a place here, this is its name) | yes | yes |
| the simulation's own doings — incidents, wars, outbreaks, famines, caravans | **no** | **yes** |
| technology unlocks (`TechnologyEngine` unlocks by research, not by year) | **no** | yes |
| modelled population, reputation, divergences, anomalies | **no** | yes |
| present-day pack rank ("we are a town") — the local packs are `presentOnly`, 2026 data | **no** | the branch's own computed state instead |

A man of 1901 can never tell you 1947: **the held year is also the horizon.**

## 4. Reach and slots (my choice, from the data)

- **Reach = 100 km.** Everything the record holds in that circle is available.
- **Slots:** up to **two** record facts for the year — one **nearest**, one **greatest** — plus
  **exactly one line of the past** (your decision 6; it is the guaranteed line).
- "Greatest" is **kind weight × radiusKm**, so a siege outranks a skirmish and a wide disaster outranks
  a narrow one.

Why 100 km, and why the cap: near Hyderabad the record holds **5 events within 100 km** and only
**7 years of 1,027** (1000–2026) have anything at all. Near Paris it holds **138 — up to 14 in one
year**. One radius serves both only because the slots are capped and the pick is by significance, not
by whatever is found first.

## 5. Voice — the trade picks the lens

The person's own role (already on the entity, already restored by the replica) maps onto the seven
lenses that exist:

| the project's roles | lens |
|---|---|
| farmer, citizen, weaver, artisan | VILLAGER |
| merchant, trader, caravan hand | MERCHANT |
| soldier, guard | SOLDIER |
| priest | PRIEST |
| healer, doctor | HEALER |
| scholar, historian | HISTORIAN |
| official, noble, courtier | ADVISOR |

The **fact stays one fact**; the lens decides the category it prefers, the words it uses, and whether
the fact is good or bad news *for that trade*. A price rise is bad in a labourer's mouth and good in a
merchant's.

## 6. No two people alike — and no person unstable

- The **lens picks the category** (a soldier never opens with the price of grain).
- The **person picks the fact** by a hash of his UUID over his lens's eligible facts — deterministic,
  no new storage.
- He **repeats himself** while the fact set is unchanged; a new fact **displaces the oldest**.
- Where the record has only one fact, two people of the same trade and place can still sound alike.
  The difference then comes from the past line and from where they stand. **Nobody invents a fact to
  sound different.**

## 7. Knowledge ceiling

Every fact carries an audience level — **common · civic · scholarly · occult** — and every lens has a
ceiling. A farmer or a soldier stops at common/civic; the scholar and the advisor reach scholarly; the
occult level (timelines, anomalies, divergences) exists only in a branch, and only for those lenses.

## 8. The shape of one click

```
over the head   one short line, ≤ 60 characters, always carrying his year
in chat         2–5 facts, each with its year and its place, in his trade's words
```

## 9. Never

- No ambient speech. No per-tick cost for talking. **No new storage** — the held year is already saved,
  the fact set is rebuilt from the record each click.
- In the true world, **no sentence that is not in the record** — no simulated famine, no unlocked tech,
  no modelled number.
- **A rumour may never touch the resolver's number** (standing rule from the crowd work). In the true
  world a rumour may only be a record fact retold; in a branch, the incident's own polarity is used.

## 10. How it is proved (plain Java, no Minecraft)

Feed the checker 400 identities drawn from the project's own role pools and a fixed fact set, and
assert: no two people in one district share an opening line while the pool allows it; one person is
stable across two clicks; a new fact changes his line and drops the oldest; no line rises above its
lens's ceiling; the headline is ≤ 60 characters; the account is 2–5 facts; in the true world **no line
ever comes from a non-record source**; and **no line is dated after the person's held year**.

## 11. Known and accepted

- **In the true world a quiet year is the normal year.** Here the record is 5 events in 100 km across
  all years; the loud years are the ones the record mentions. That is the history being thin, not the
  code.
- The record is **military and natural** — battle 8,035, siege 2,404, disaster 2,294, incident 2,222,
  political 61 of 15,027. Its good news, in the true world, is mostly **political turnings** (Bastille
  1789, Armistice 1918, Independence 1947, Fall of Saigon 1975, Berlin Wall 1989) — read as good or bad
  by the trade that says them.
- Your rule "in a quiet year, less past and more good" therefore means: **lean on the best thing the
  record holds in reach, and on the place's identity** — never on an unrecorded good.
- Reach is **one constant**. If 100 km is wrong, changing that number is the whole change.

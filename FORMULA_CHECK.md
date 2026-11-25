# The proposed formula, checked

The formula, as given:

```
people(place, year) = peopleAt2026(place) − (2026 − year) × averagePopulation(place)
```

Applied to every one of the 15,954 places, with the average population taken from
`AVERAGE_POPULATION.md`'s own column, and printed next to what `PopulationResolver` answers for the
same year. Computed by `verification/population/alternatives/FormulaCheckProbe.java`; the visual is
`formula_check_2d.html`; the full per-place output is `formula_check.tsv` (127,632 rows).

---

## 1. The modelled world, year by year

| year | the formula | the resolver | ratio |
|---:|---:|---:|---:|
| 2026 | 3,969,294,510 | 3,969,294,510 | 1.0000 |
| 2025 | 3,469,858,437 | 3,968,880,552 | 0.8743 |
| 2020 | 1,215,786,667 | 3,966,808,266 | 0.3065 |
| 2011 | 6,658,599 | 3,963,085,373 | 0.0017 |
| 2001 | 502,481 | 3,028,179,233 | 0.0002 |
| 1948 | **0** | 730,689,138 | 0.0000 |
| 1900 | **0** | 477,661,620 | 0.0000 |
| 1800 | **0** | 56,278,638 | 0.0000 |

Places still holding anyone, year by year: 15,954 → 15,954 (2026) → **5,401 (2025)** → 1,417 (2020) →
**14 (2011)** → **1 (2001)** → 0 (1948).

In 2025 — one year back — the formula has already lost half a billion people and a third of the
places. By 2011 it has fourteen places left. By 1948 the world is empty.

Without the floor at zero, the arithmetic says −3.76 billion in 2011, −8.91 billion in 2001 and
**−112.5 billion in 1800**.

---

## 2. One place, so the shapes are visible

Secunderabad: average population **335,622**, 2026 number **217,910**.

| year | the formula | the resolver | the resolver's tier |
|---:|---:|---:|---|
| 2026 | 217,910 | 217,910 | interpolated |
| 2025 | **0** | 217,910 | interpolated |
| 2020 | **0** | 217,910 | interpolated |
| 2011 | **0** | 217,910 | **sourced — an actual census** |
| 2001 | **0** | 206,102 | **sourced — an actual census** |
| 1948 | **0** | 142,174 | interpolated |
| 1900 | **0** | 135,370 | interpolated |
| 1800 | **0** | 672 | floor band |

A place whose average (335,622) is larger than its present population (217,910) reaches zero in
**0.65 years** — before the end of 2025 — and is held at zero for the whole of the history the project
owns, including the census years where the real number is known.

Hyderabad behaves the same way with a longer rope: average 1,051,430 against a 2026 number of
7,677,018, so it survives 7.3 years — zero from 2019 onward. In 2011 the formula says 0; the 2011
census in the project says 7,677,018.

---

## 3. How long each place lasts

| years until the formula reaches zero | |
|---|---:|
| smallest | 0.00 |
| 10th percentile | 0.24 |
| 25th percentile | 0.27 |
| **median** | **0.35** |
| 75th percentile | 2.64 |
| 99th percentile | 9.11 |
| largest (vijayawada) | 28.7 |

| | places |
|---|---:|
| zero within 1 year | **10,553** |
| zero within 5 years | 14,156 |
| zero within 50 years | 15,954 (all) |
| survives to 1900 | **0** |

The longest-lived places under the formula are those where the average happens to be small next to
today's number: vijayawada (28.7 years), the Hyderabad bazaars (16–17 years), varanasi (16.4). The
shortest-lived are the ancient cities the model rates far higher in the past than today — eridu
(2026 number 789, average 1,106,832), nippur, uruk, ur, machu_picchu — all zero in under a hundredth of
a year.

---

## 4. Why it collapses — and it is not a small adjustment

An average **population** is a level: people. The formula needs a rate: people per year. Dividing one
level by another (implicitly, by "per year") gives a number with the wrong units, and the error is not
a few percent — measured across the project, **the average is on average 3.2× the place's own 2026
number**, because the era bands make most places *shrink* toward the present (a village band's upper
bound is larger than the village the model gives it today). So the formula subtracts a multiple of the
place itself every year.

Stated cleanly: for a place that grew in a straight line from nothing, the average over its life is
exactly **half** its final number. Subtracting the average each year back therefore removes the whole
of today's population in about **two years** — not two centuries. The formula is that, everywhere,
with the numbers each place happens to have.

---

## 5. The one-word fix

Keep the shape — anchor at 2026, subtract a per-year amount — and replace the *level* with a *rate*:

```
people(place, year) = peopleAt2026(place) − (2026 − year) × averageIncreasePerYear(place)
```

`averageIncreasePerYear` is the column already computed two steps back in `CONSTANT_INCREASE.md`:
`peopleAt2026 ÷ (2026 − startOfThePlace)`. The two forms are algebraically the same line —
`peopleAt2026 × (year − start) ÷ (2026 − start)` — so the place starts at zero when it starts and holds
its real number today.

What that does, on the same years:

| year | the formula as proposed | with the rate instead | the resolver |
|---:|---:|---:|---:|
| 2026 | 3,969,294,510 | 3,969,294,510 | 3,969,294,510 |
| 2025 | 3,469,858,437 | 3,960,330,242 | 3,968,880,552 |
| 2020 | 1,215,786,667 | 3,915,508,170 | 3,966,808,266 |
| 2011 | 6,658,599 | 3,834,833,098 | 3,963,085,373 |
| 2001 | 502,481 | 3,745,187,830 | 3,028,179,233 |
| 1948 | 0 | 3,270,160,971 | 730,689,138 |
| 1900 | 0 | 2,840,941,869 | 477,661,620 |
| 1800 | 0 | 1,949,848,553 | 56,278,638 |

The fixed version never empties the world, is smooth, is exact at 2026, and — as
`CONSTANT_INCREASE.md` measured — is accurate to within 7% for a place that actually grew steadily
(Secunderabad's real censuses). Its cost is the opposite failure: it inflates the deep past, 34.6× the
resolver's answer at 1800, because a straight line from a founding year puts people where there were
none.

---

## Verdict in simple words

The formula works for exactly one year — 2026 — and erases nearly everything after that. Not because
of a rounding slip: it uses the *average population* where it needs the *average increase per year*.
The average population of a place is a number of people, often larger than the place's own population
today, so subtracting it annually reaches zero in a median of **0.35 years**; Secunderabad is gone
before the end of 2025, and the whole world is empty by 1948.

Swap one word and it works: **average increase per year** instead of **average population**. That
column already exists, it is exact at 2026, it keeps the world populated at every year, and it
reproduces Secunderabad's real censuses to within 7%. What it cannot do — and the resolver's
interpolation can — is bend: it cannot follow a place that grew slowly for centuries and then
exploded, because a straight line has no bend in it.

---

## Status: what is built

| piece | state |
|---|---|
| `FormulaCheckProbe.java` | **written, compiled, run** — the formula per place, against the resolver, at 8 years |
| `formula_check.tsv` | **written** — 127,632 rows: every place × every checked year |
| `formula_check_2d.html` | **written** — the world over time and Secunderabad, three series each |
| this document | **written** |
| the engine | **NOT changed.** The formula is not wired into anything; `PopulationResolver` still answers as it did. |

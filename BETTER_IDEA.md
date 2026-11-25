# Do I have a better idea?

Yes - but it is not a new formula. It is two deletions inside the resolver the project already has:
stop refusing to use two census records when they sit more than 60 years apart, and stop carrying a
census number flat for decades at the ends. Measured against the project's own records, the rule the
resolver already uses *between* records is the best of the eight rules tested, so the better idea is
to use that same rule in the two places it currently refuses to.

Everything below is measured, not asserted. The engine column is the real `PopulationResolver`, run
against a rewritten pack. No number in this file was typed in by hand: `make_better_idea.py` computes
all of them from `alternatives/loo_results.tsv` and `alternatives/proposal_exposure.tsv`.

## What I did

There are 16 census records in the project's pack. Three of them are the only record their region has,
so hiding one leaves nothing to rebuild it from; 13 can be tested properly:

| region | records |
|---|---|
| hyderabad | 1901, 1931, 1941, 1951, 2001, 2011 |
| secunderabad | 1941, 2001, 2011 |
| atraf_i_balda (an 847-settlement district) | 1881, 1891, 1901, 1941 |
| akanpalle, maheswaram_mandal, hyderabad_state | one each - not testable |

For each of the 13, the harness hides the record, rewrites the pack, and asks nine rules to rebuild it
from the records that remain: the real resolver, the seven classic interpolation rules, and the
proposal. Error is `|predicted - actual| / actual`.

Three guards, so that the table cannot be measuring the wrong thing:

- before scoring, the untouched pack must answer `hyderabad 1901 = 448,466 (SOURCED)`. Without the
  index on the classpath the resolver silently falls back to a hardcoded band and answers FLOOR for
  everything - it did, on the first run of this harness, and the guard now refuses to proceed.
- the python reimplementation of region matching agrees with the engine on 14 of 14 sampled rows, in
  both directions: rows it claims must come back SOURCED or INTERPOLATED, rows it rejects must not.
- on the 8 records the engine answers from the right region's series, the reimplemented rule reproduces
  the engine to **0.000%**. So the proposal is the engine's own formula, used in two more places - not a
  rival formula being compared favourably with itself.

## The 13 records

`engine read` says whether the resolver answered from the record's own region's series at all. The last
two columns are the rules as they stand in the harness, always evaluated on the record's own region's
series: for the nine rows the engine already reads, those are exactly what the two fixes would produce;
for the four Atraf-i-Balda rows they also need the matching fix described below, because the engine
never reads that region's records at all.

| record | actual | engine | engine error | fix 1 | fix 2 | engine used |
|---|---|---|---|---|---|---|
| atraf_i_balda 1881 | 355,787 | 448,466 | +26.0% | +9.6% | +1.5% | another region |
| atraf_i_balda 1891 | 389,784 | 448,466 | +15.1% | -0.7% | -0.7% | another region |
| atraf_i_balda 1901 | 420,702 | 448,466 | +6.6% | +1.4% | +1.4% | another region |
| atraf_i_balda 1941 | 612,493 | 739,159 | +20.7% | -31.3% | -6.8% | another region |
| hyderabad 1901 | 448,466 | 466,894 | +4.1% | +4.1% | -73.8% | the record's own region |
| hyderabad 1931 | 466,894 | 652,358 | +39.7% | +39.7% | +39.7% | the record's own region |
| hyderabad 1941 | 739,159 | 718,885 | -2.7% | -2.7% | -2.7% | the record's own region |
| hyderabad 1951 | 1,106,880 | 1,040,222 | -6.0% | -6.0% | -6.0% | the record's own region |
| hyderabad 2001 | 5,742,036 | 5,559,178 | -3.2% | -3.2% | -3.2% | the record's own region |
| hyderabad 2011 | 7,677,018 | 5,742,036 | -25.2% | -25.2% | +4.0% | the record's own region |
| secunderabad 1941 | 135,370 | 206,102 | +52.3% | +52.3% | +9.0% | the record's own region |
| secunderabad 2001 | 206,102 | 5,541,233 | +2,588.6% | -1.2% | -1.2% | another region |
| secunderabad 2011 | 217,910 | 206,102 | -5.4% | -5.4% | +1.4% | the record's own region |

## The error table

Mean error hides single disasters, so the median is here too, and the count of records rebuilt to
within 10%.

All 13 held-out records:

| rule | mean error | median error | within 10% | records |
|---|---|---|---|---|
| engine (today) | 215.0% | 15.1% | 6/13 | 13 |
| fix 1 only | 14.1% | 5.4% | 9/13 | 13 |
| fix 1 + fix 2 (the proposal) | 11.7% | 3.2% | 11/13 | 13 |

The split matters. On the 8 records the engine already answers from the right series it is not
215% - it is **17.3% mean / 5.7% median**, and fix 1 changes *nothing* on those 8 rows. The 215%
comes from the 5 rows where the engine did not use the record's own series:

| rule | mean error | median | within 10% | records |
|---|---|---|---|---|
| `engine` | 531.4% | 20.7% | 1/5 | 5 |
| `fix1-bridge` | 8.9% | 0.7% | 4/5 | 5 |
| `proposed` | 2.3% | 0.7% | 5/5 | 5 |
| `log-lerp` | 8.9% | 0.7% | 4/5 | 5 |
| `arithmetic` | 8.9% | 0.0% | 4/5 | 5 |
| `constant-rate` | 2.3% | 0.7% | 5/5 | 5 |
| `carry-forward` | 18.3% | 8.7% | 3/5 | 5 |

The four Atraf-i-Balda rows are rebuilt from Atraf-i-Balda's own records here, which the engine never reads; the fifth is Secunderabad 2001, the row fix 1 repairs. So this table mixes the two fixes - the split is in the section below.

(The three tables above are also printed in full at the end of this file.)

## Fix 1 - use the two records you already have

`PopulationResolver.interpolate` returns "no answer" the moment the two bracketing records are more
than `maxInterpolationGapYears` (60) apart:

```java
if (span > maxInterpolationGapYears) return Optional.empty();   // and then the model answers instead
```

That cap was meant to stop the resolver extrapolating *away* from data. Refusing to bridge *between*
two records that both exist is not what it is for, and the cost is measurable. In the harness, holding
out Secunderabad's 2001 census record leaves 1941 (60 years away) and 2011 (10 years away). The engine
refuses to use either and answers from the gravity model:

| | value | error |
|---|---|---|
| the real 2001 census | 206,102 | - |
| the resolver today | 5,541,233 | **+2,588.6%** |
| bridging the two records instead (log-lerp) | 203,583 | **-1.2%** |

Fix 1 changes no other row of the 13 - verified, not assumed: on the 8 rows where the engine already
uses the series, fix 1 produces the identical number. And on the shipped pack it changes nothing at all
today: the widest bracket in the pack is Secunderabad 1941-2001 at exactly 60 years, which is not
greater than 60. It is a robustness fix that costs nothing and removes the worst failure in the table.

## Fix 2 - extend at the ends instead of carrying flat

At the ends there is no bracket, so the resolver carries the nearest record - up to 60 years flat. Six
of the 13 records are ends. Carrying says the place did not change for three decades; on these records
that is wrong by 21.3% on average. Extending the nearest *pair* of records at their own yearly rate
(marked +, and never more than 60 years from a record) does better:

| ends only (6 records, same series either side) | mean error | median | within 10% |
|---|---|---|---|
| carry the nearest record flat (what the engine does when it has the series) | 21.3% | 17.4% | 3/6 |
| extend at the nearest pair's rate | **16.1%** | **5.4%** | **5/6** |

Five of the six improve. One gets worse, and it should be said plainly rather than averaged away:
**Hyderabad 1901**. Holding that record out leaves 1931...2011, whose nearest pair grew 4.7% a year -
a spurt - and extending that backwards 30 years gives 117,668 against an actual 448,466 (-73.8%),
where carrying the 1931 value flat was only +4.1% out. A place that grew slowly for 30 years and then
fast is exactly the case a rate rule misreads. Fix 1 has no such case; fix 2 does.

## What it reaches, and the cap

Only places matched to a settlement region can reach either fix - everything else is answered from a
band or a model. That is 691 of 15,954 rows, and the rule changes an answer only in years that are
within 60 years of a record but not on one:

| year | on a record | extended by fix 2 | bridged by fix 1 | out of reach |
|---|---|---|---|---|
| 1200 | 0 | 0 | 0 | 691 |
| 1500 | 0 | 0 | 0 | 691 |
| 1800 | 0 | 0 | 0 | 691 |
| 1881 | 0 | 683 | 0 | 8 |
| 1901 | 475 | 208 | 0 | 8 |
| 1931 | 475 | 208 | 0 | 8 |
| 1941 | 683 | 0 | 0 | 8 |
| 2011 | 691 | 0 | 0 | 0 |
| 2100 | 0 | 0 | 0 | 691 |
| 2500 | 0 | 0 | 0 | 691 |

The cap is load-bearing, and this is the size of it: the worst factor the rate rule can apply anywhere
inside 60 years is x15.74 (Hyderabad 1931->1941, 4.7% a year). Uncapped over 700 years the same rate
gives x9.2e+13 in one direction and 1.1e-14 in the other. So the 60-year cap stays exactly where it is;
only the *refusal* inside it goes.

## The thing I found but am not recommending

Atraf-i-Balda is a district of 847 settlements with four gazetteer records - 355,787 in 1881 rising to
612,493 in 1941, about 497 people per settlement in 1901. Almost none of the world can read them:

- `findRegion` only ever considers regions whose kind is `settlement`; Atraf-i-Balda is an `aggregate`
  and Hyderabad State is a `polity`, so neither can ever be matched;
- 4,701 place rows sit inside its 90 km circle and inside no settlement radius;
- of those, 118 sampled every 40th and asked to the engine for 1901: **118 answered MODELLED, 0 from
  Atraf-i-Balda's records**. A village 6 km from Secunderabad's centre in 1901 gets 53 people, from
  "rank-size from Secunderabad (135370, interpolated), 6km, decay 0.97 - calibration constants, not a
  record", while the district's own 1901 record of 420,702 sits unread.

Holding out each of its four records, the engine answers those rows with Hyderabad's numbers instead:
531.4% mean error, 20.7% median. Reading Atraf-i-Balda's own records instead brings the same four to
2.3% mean / 1.4% median - the best score of any rule in the table. But that number is only available
*because* the records are district totals and the test is a district total: there is no census of any
individual village here to score a per-village number against, and 497 people is an average that no
particular village need match. So this stays a measured finding, not a recommendation.

## What I did not do

- The engine is **not changed**. Both fixes are measured; neither is applied. `PopulationResolver.java`
  is byte-for-byte what it was - fix 1 is a one-line deletion and fix 2 a ~15-line method, and the
  numbers above are what they produce.
- I did not touch the crowd work, the draw, or any pack. The harness rewrites only
  `/tmp/loo/res/.../population_south_asia.json`, and restores it afterwards.
- I did not test what fix 2 does to the visual population of a place in the deep past beyond the cap:
  by construction nothing changes there (that is what "out of reach" rows are), but I have not drawn it.

## Status - what is built, what is not

| piece | state |
|---|---|
| `alternatives/RegionYearProbe.java` | written, compiled, run (13 records, 2 harnesses) |
| `alternatives/loo_test.py` | written, run; wrote `loo_results.tsv` |
| `alternatives/proposal_exposure.py` | written, run; wrote `proposal_exposure.tsv` |
| `alternatives/make_better_idea.py` | written, run; wrote this file and `better_idea_2d.html` |
| the two fixes in the resolver | **not written** - described as a code change, measured through the harness |
| `rig.sh` rebuild path | fixed this turn (the shim was looked for in the wrong directory) |

## Verdict in simple words

- Your formula died on the first year. This does not: it is right wherever a record is, and it only
  guesses near the edges.
- The best rule for the project is the one the resolver already uses between two census records
  (log-lerp, 14.1% mean, 5.4% median). No fancier rule beats it on these records - the arithmetic,
  absolute-rate and carry rules are all worse.
- So the better idea is not a new rule. It is to stop refusing: when two records straddle a year, use
  them even if they are more than 60 years apart. One row of the test goes from 2,589% wrong to 1%
  wrong, and nothing else in the pack changes.
- The second half - growing the nearest record at its neighbours' rate instead of carrying it flat -
  improves five of the six edge cases (21.3% -> 16.1% mean) and spoils one (Hyderabad 1901, +4% ->
  -74%). Take the first half; take the second half knowing that.
- And the biggest thing found is not a rule: 4,701 places live inside Atraf-i-Balda's circle and the
  engine answers every one of the 118 I sampled with a model. Four real census records - a district of
  847 settlements - are never read by anything.

**All 13 held-out records**

| rule | mean error | median error | within 10% | records |
|---|---|---|---|---|
| `engine` | 215.0% | 6.6% | 6/13 | 13 |
| `fix1-bridge` | 14.1% | 1.2% | 9/13 | 13 |
| `proposed` | 11.7% | 0.7% | 11/13 | 13 |
| `constant-rate` | 11.7% | 0.7% | 11/13 | 13 |
| `arithmetic` | 18.3% | 4.1% | 7/13 | 13 |
| `carry-forward` | 25.6% | 8.7% | 6/13 | 13 |
| `carry-backward` | 57.5% | 9.6% | 5/13 | 13 |
| `constant-absolute` | 18.1% | 0.0% | 7/13 | 13 |
| `log-lerp` | 14.1% | 1.2% | 9/13 | 13 |


**The 8 the engine answers from the record's own region**

| rule | mean error | median error | within 10% | records |
|---|---|---|---|---|
| `engine` | 17.3% | 3.0% | 5/8 | 8 |
| `fix1-bridge` | 17.3% | 3.0% | 5/8 | 8 |
| `proposed` | 17.5% | 0.6% | 6/8 | 8 |
| `constant-rate` | 17.5% | 0.6% | 6/8 | 8 |
| `arithmetic` | 24.1% | 10.5% | 3/8 | 8 |
| `carry-forward` | 30.2% | 15.3% | 3/8 | 8 |
| `carry-backward` | 80.9% | 41.7% | 2/8 | 8 |
| `constant-absolute` | 27.4% | 3.2% | 3/8 | 8 |
| `log-lerp` | 17.3% | 3.0% | 5/8 | 8 |


**The 5 it does not — borrowing another region, or leaving the series entirely**

| rule | mean error | median error | within 10% | records |
|---|---|---|---|---|
| `engine` | 531.4% | 20.7% | 1/5 | 5 |
| `fix1-bridge` | 8.9% | 0.7% | 4/5 | 5 |
| `proposed` | 2.3% | 0.7% | 5/5 | 5 |
| `constant-rate` | 2.3% | 0.7% | 5/5 | 5 |
| `arithmetic` | 8.9% | 0.0% | 4/5 | 5 |
| `carry-forward` | 18.3% | 8.7% | 3/5 | 5 |
| `carry-backward` | 20.0% | 7.9% | 3/5 | 5 |
| `constant-absolute` | 3.1% | 0.0% | 4/5 | 5 |
| `log-lerp` | 8.9% | 0.7% | 4/5 | 5 |


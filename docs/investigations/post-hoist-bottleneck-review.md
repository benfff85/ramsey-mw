# Post-Hoist Bottleneck Review — where the fleet's time goes now

**Date:** 2026-07-28
**Author:** Ben Ferenchak + Claude
**Status:** Findings measured on the live system; fixes queued and being applied one at a time (progress table at the end).
**Context:** `pair-move-hoist-review.md` closed with "making one stage of a pipeline 30× faster does not make the
pipeline faster; it relocates the bottleneck, usually somewhere nobody was measuring." This doc is the follow-up
measurement — two days after the hoist deployed — of where it actually relocated to.

---

## Method

Everything below is measured on the running stack (campaign 10, m4-max fleet, 14 workers), not estimated:

- QM stage-transition timestamps over a 90-minute window (1,463 advances) and a 40-minute window (1,172 advances)
  for the latency breakdowns.
- Worker-1 logs for the hoist ramp/fill (n=228 engagements).
- `docker stats`, Dragonfly `INFO commandstats`, MySQL `EXPLAIN` + `SHOW PROFILES`, and direct `curl` timings
  against the middleware.

Where a number is an estimate rather than a measurement it says so explicitly.

## The wall-clock budget

Two regimes, and they are not equally expensive:

| | count | mean | share of wall-clock |
|---|---|---|---|
| **Full-sweep stages** (all 392.5M units) | 375 (26%) | **13.01 s** | **90.6%** |
| Short stages (advance on improvement) | 1,088 (74%) | 0.46 s | 9.4% |

979 stage advances/hour. Fleet throughput ~29M units/s across 14 workers at ~95% busy, so a 392.5M sweep takes
13.0 s and **stage duration ≈ sweep duration** — QM and middleware overhead is invisible on a full sweep.

Anatomy of one 13.01 s full sweep, per worker:

```
2.60 s   unhoisted ramp before the hoist gate opens    20%   <- does 1.7% of the units
0.41 s   co-operative slice fill                        3%
9.35 s   hoisted sweep                                 72%   <- does 98% of the units
0.65 s   per-cycle overhead (HTTP + Redis, 5% of busy)
```

The headline: **~23% of every full sweep is the hoist ramp**, and because full sweeps are 90.6% of wall-clock,
that is ~21% of the fleet's total time.

---

## Finding 1 — `getActiveStages()` is a full table scan on `stage`

**Severity: high. Cost: one DDL statement.**

`GET /stages?status=ACTIVE` takes **49–70 ms** and returns 244 bytes. `GET /fleets/m4-max/active-stage` returns
the *same stage* in **1–4 ms**.

Both go through the same repository method, `StageRepo.findByCampaignIdAndStatus`:

```java
@Query("SELECT s FROM Stage s WHERE " +
       "(:campaignId IS NULL OR s.campaignId = :campaignId) AND " +
       "(:status IS NULL OR s.status = :status)")
```

- **Fleet path** (`FleetService.resolveActiveStageUncached`) passes a real `campaignId` → uses
  `idx_stage_campaign_status(campaign_id, status)` → 0.135 ms.
- **QM path** (`MiddlewareClient.getActiveStages`) passes **null** → the `:campaignId IS NULL` disjunction makes
  the leading index column unusable → full scan.

```
EXPLAIN SELECT * FROM stage WHERE status='ACTIVE';
 -> Filter: (stage.`status` = 'ACTIVE')  (cost=21914 rows=107764)
     -> Table scan on stage  (cost=21914 rows=215529)

SHOW PROFILES;  -- 0.045 s
```

The index added during the hoist work (`FleetService` documents it as "11.8ms → 0.135ms") fixed the worker path
and left the QM path scanning. The QM calls it from five sites — `StageProgressionMonitor.java:61` (1/s tick),
`:79` (`adoptAfterSettle`, per settle fire), `:411` (perturbation, 1/60s), `:520` (`isStillActive`, per stage
advance), `:683` (`ensureActiveStageInitialized`, 1/5s) — about **1.8 calls/s**, and **twice on the critical path
of every stage advance**.

The QM's own log timestamps isolate it. Total progression is 80.7 ms mean, and the single `isStillActive` call is
most of it:

| span | mean | median |
|---|---|---|
| trigger → "Progressing via" | 0.4 ms | 0.0 ms |
| "Progressing via" → "Created new graph" | 10.2 ms | 8.0 ms |
| **"Created new graph" → "Marking stage"** (`isStillActive`, nothing else) | **51.5 ms** | **49.0 ms** |
| "Marking stage" → "now active" | 18.5 ms | 20.0 ms |
| **total trigger → active** | **80.7 ms** | 79.0 ms |

**It degrades over time.** `stage` is at 215,529 rows and grows ~23,500/day at the current cadence, so the scan
cost climbs ~11%/day. This is the same failure mode the 2026-07-26 hoist work already hit once ("a full-table-scan
on `stage`, 11.8ms → 0.135ms with an index") — it was fixed on one path only.

**Fix:**
1. `CREATE INDEX idx_stage_status ON stage (status)` — schema only, no code, applies to both the live DB and
   `database/init-script.ddl`.
2. Separately: `switchToNewStage` calls `isStillActive` to re-check a stage the caller was just handed. Either
   pass the already-fetched list down or use a targeted `GET /stages/{id}`. The index makes it cheap; removing it
   makes it free.

**Expected gain:** descent stages ~0.46 s → ~0.35 s, i.e. a ~1.3× faster basin descent — and descents are where
the search actually reaches new minima. Plus ~8% of a core returned in mw/MySQL. Small in total wall-clock
(descents are 9.4%), large in search progress per hour.

---

## Finding 2 — the hoist gate costs 6× more than the fill it protects against

**Severity: highest wall-clock impact.**

`HOIST_MIN_STAGE_INDEX = 5_000_000` (`worker.rs:67`) holds the fleet on the seeded kernel until the fleet-wide
claimed index passes 5M. Its sizing comment reasons from *hoisted* throughput — "at that rate a wall stage crosses
5M in ~0.24s (1.3% of an ~18s sweep)" — but the units before the gate necessarily run **unhoisted**, at roughly
1/20th of that rate. Measured on worker-1 (n=228):

| | value |
|---|---|
| stage setup → `Hoist ENGAGED` | mean **2.60 s**, median 2.92 s, p90 4.61 s |
| slice fill duration | mean **0.407 s** |
| engage index | median 5.37M of 392.5M = **1.7% of the work** |

So 20% of the stage is spent doing 1.7% of the work. CPU accounting per stage:

```
ramp:  14 workers x 2.60 s = 36.4 core-seconds     (spent avoiding the fill)
fill:  14 workers x 0.41 s =  5.7 core-seconds     (the thing being avoided)
```

**The gate is still correct for descent stages** — those die at 0.46 s, well before 5M, and genuinely cannot
amortise a fill. This is not an argument for lowering the constant; it is an argument for making it regime-aware.

### The structural bug: one flag, two decisions

`worker.rs:666` uses a single boolean for two things that have completely different costs:

1. **Use the hoisted path.** Nearly free in both regimes. Mid-descent the bound-skip (`max_new < 0 → continue`,
   `worker.rs:828`) rejects units from the per-edge counts alone, *before* any table value is read, so an empty
   table costs nothing. Near the floor the threshold sits at/above `base_total`, the skip never fires, and the
   table is exactly what is wanted.
2. **Proactively fill a slice.** 2,476 **uncapped** traversals ≈ 0.41 s. This is the only expensive part, and the
   only part that needs a gate.

The gate exists for (2) and is charging us for (1). **Splitting them is correct independent of which predictor
is chosen**, and is the part of this fix with no judgement call in it.

### Choosing the predictor

Payoff is strongly asymmetric: a correct call saves the 2.60 s ramp; a wrong call costs a 0.41 s fill (which
roughly doubles a 0.46 s descent stage). **Break-even accuracy is 13.6%.**

Regime autocorrelation over 1,463 stages:

```
P(next FULL | prev FULL)  = 72.7%   (272 of 374)
P(next FULL | prev SHORT) =  9.4%   (102 of 1088)
base rate                 = 25.6%
FULL runs: mean 3.6, max 17     SHORT runs: mean 10.7, max 961
```

Policies scored on the same 5,382 s window:

| policy | full sweeps pre-filled | short stages wrongly filled | net saving |
|---|---|---|---|
| today — fixed 5M index | 0 / 375 | 0 | baseline |
| **just set the gate to 0** | 375 | 1,088 | +529 s (**9.8%**) |
| **fill iff prev stage was a full sweep** | 272 | 102 | +665 s (**12.4%**) |
| **skip-rate probe** | ~375 | ~0 | +933 s (**17.3%**) |

Two conclusions worth recording:

- **Simply lowering the constant is the worst of the three fixes** — beaten by keeping a gate and making it
  smarter. Do not "tune `HOIST_MIN_STAGE_INDEX`" and call it done.
- The earlier "~20%" estimate in this doc's first draft assumed *perfect* prediction. The real range is 12–17%.

**Skip-rate probe:** over a worker's first ~4,096 units on the kernel path (~29 ms), count the fraction hitting
the bound-skip. High → descent, do not fill. Low → wall, fill now. It measures the thing that actually matters
rather than a proxy, catches the regime-entry stages the prev-stage signal misses, and needs no history or
cross-service change. **Its risk:** on a fresh stage `best_results` is empty → `top_threshold` is `None` →
`early_limit = i32::MAX` → the bound-skip *cannot* fire → every stage reads as a wall. The probe must be gated on
`top_threshold.is_some()`, and how quickly that becomes true is the thing to measure before trusting it.

**Recommended sequencing:** ship the prev-stage signal first (~15 lines, worker-local, cannot fail in a novel
way, banks 12.4%), measure, then decide whether the probe's extra ~5% justifies the extra state. They compose —
prev-stage as the prior, probe to catch regime entry.

**Validation:** `hoist_equiv_replay` full-stage byte-identical replay. The change is *pure scheduling* — it alters
only *when* values are computed, never what they are — so the replay should be identical by construction, and any
diff is a real bug rather than a tolerance question.

---

## Finding 3 — 2 of 16 hoist slices are never filled

**Severity: moderate. Cost: small worker change.**

`HOIST_FILL_SLICES = 16` (`worker.rs:75`) against a 14-worker fleet (the `m1` fleet is `PAUSED` in the `fleet`
table). `claim_hoist_slice` hands out `(INCR - 1) mod 16` (`redis_client.rs:639`), so each graph gets slices 0–13
claimed and **14 and 15 never are**.

Confirmed in the logs: the maximum coverage ever observed is 34,669 / 39,621 = **exactly 87.5% = 14/16**. Median
coverage at first engage is 14,861 (37.5%); the `refresh_budget` re-checks pull it up over subsequent batches but
ceiling at 87.5%.

The 4,953 orphaned edges are then filled on demand by *every* worker. The ~2,476 blue ones are needed by all 14
(blue is the inner loop of `SEQUENTIAL_WITH_SINGLES`) at ~147 µs each:

```
2,476 x 147 us = 0.36 s per worker per stage  ->  ~5 core-seconds/stage  ~=  3% of fleet CPU
```

The existing comment ("a fleet smaller than this just leaves some slices unpublished, which costs nothing beyond
filling those edges on demand as before") is true per-worker but misses that the *same* orphaned slices are
re-filled by all 14 workers on every stage.

**Fix:** do not hardcode slices to fleet size — the m1 fleet may resume, and Vast bursts change the count. Make
the refresh path self-healing: when `wants_refresh()` finds gaps and the budget is half spent, claim and fill a
**missing** slice rather than only re-reading published ones.

---

## Finding 4 — the hoisted inner loop still carries pre-hoist scaffolding

**Severity: potentially the largest, but must be measured, not argued.**

The hoisted sweep is 72% of a full stage: 387M units / 14 workers / 9.35 s = **~351 ns/unit ≈ 1,400 cycles** on an
M4 P-core. The arithmetic per unit is two array loads, ≤4 bit tests, three adds and a compare. That gap is worth
investigating.

Four things in the per-unit path were sized when a unit cost 13.5 µs and are first-order now that it costs 351 ns:

1. **Per-unit heap allocation.** `let edges_to_flip = vec![...]` (`worker.rs:806`) allocates and frees for every
   one of 392M units. Profiled at 0.2% in the 2026-07-15 round and explicitly rejected as an optimisation then —
   correctly, at 13.5 µs/unit. That same ~27 ns is now **~8%** of a unit.
2. **Trait-object dispatch + integer division.** `enumerator.index_to_work_unit(idx)` (`worker.rs:805`) is an
   indirect call through `Box<dyn WorkEnumerator>` (never inlined), returns a 3-word enum, and performs an i64
   **division and modulo** by `blue_count` per unit. A red-outer/blue-inner loop removes all three.
3. **`cross_pairs` is branchy** (`hoist.rs:66`): 4 iterations × 5 equality guards before each bit test, entirely to
   handle the 1.42% shared-vertex case. The 98.6% disjoint case needs 4 plain bit tests; specialising it and
   testing "shares a vertex" once per pair hoists nearly all of that out.
4. **Loop-invariants re-fetched per unit.** For a fixed red edge `r`, `D_r`, `destroyed(r)` and the
   `red_adj[x]` / `red_adj[y]` rows are constant across all 19,810 blue partners, but `pair_created` →
   `single_created` re-does `&mut self` + UNKNOWN branch + bounds check on both edges every unit.

**Proposed shape:** iterate the claimed range red-outer / blue-inner; hoist `D_r`, `destroyed_r` and the two
adjacency rows; walk `C_b` and `edge_counts[b]` linearly (already adjacent in memory under
`SEQUENTIAL_WITH_SINGLES` — see the strategy's own doc comment).

**Deliberately no speedup number here.** This repo has twice recorded a confident estimate in exactly this area
collapsing on measurement (the "2× kernel win" whose benchmark never aborted, and the "20× pruning win" whose
soundness precondition failed in production), and `pair-move-hoist-review.md` §4 shows three benchmark windows on
the same code giving 6.8×, 17.0× and 362.9×. **Measure integrated over a whole sweep**, not in a window.

**Gates before this goes near production:**
- `single-flip-check/src/bin/pair_hoist_check.rs` `bench`, integrated over the whole sweep.
- `single-flip-check/src/bin/hoist_equiv_replay.rs` — full 392M-unit byte-identical replay.
- `single-flip-check/src/bin/kernel_equiv_replay.rs` if the kernel or `Graph` is touched at all.

---

## Smaller items

| Item | Measurement | Verdict |
|---|---|---|
| **`TARGET_BATCH_LOOP_MILLIS = 200`** (`worker.rs:98`) | 5% of worker wall-clock is per-cycle overhead (~11.6 ms of one fleet HTTP call + 3 Redis round trips per batch) | Short batches existed for stage-change responsiveness, but `STAGE_CHECK_INTERVAL_UNITS = 4096` now lets a worker abandon mid-batch, so batch length no longer gates that. Raising to ~500 ms cuts overhead to ~2%. Watch tail over-claim (`MAX_FETCH_SIZE` 1M × 14 = 3.6% of a stage). |
| **Straggler delay** | 28 starts in 40 min, **zero** reached the 3 s limit; each costs up to one 1 s QM poll | ~1% of wall-clock. The QM only learns `processed_count` on its 1 s tick. Low priority. |
| **Abandoned work** | worker-1 discarded ~2.7M units in 10 min against ~1.26B processed | **0.2% — not a hot spot. Do not spend effort here.** |
| **Redis / Dragonfly** | 3.65% CPU, 323 MB, hit ratio 95.3% | Not a bottleneck. |
| **`graph` table growth** | 226,022 rows / **9.91 GB**, ~1.0 GB/day at current cadence | Not a performance problem (PK lookups are 3 ms), but still no retention policy. Tracked in `july-2026-next-steps.md`. |
| **P/E-core skew** | per-worker throughput 1.6–2.3M u/s, uniform across all 14 | No imbalance despite 14 workers on 12P+4E. Leave the worker count alone. |

---

## Fix queue (one at a time, measure between each)

| # | Fix | Expected | Status |
|---|---|---|---|
| 1 | `idx_stage_status` on `stage(status)` (+ DDL) | ~1.3× descent rate; kills a cost that grows 11%/day | **DONE 2026-07-28 — ~1.45× descent rate, confirmed on n≈1,780 in a post-kick descent** |
| 2 | Regime-aware hoist gate | **12.4% (prev-stage signal) → 17.3% (skip-rate probe)**; see below | queued |
| 3 | Self-healing hoist slice fill | ~3% fleet CPU | queued |
| 4 | Hoisted inner-loop restructure | unknown — prototype + measure first | queued |
| 5 | `TARGET_BATCH_LOOP_MILLIS` 200 → 500 | ~3% fleet CPU | queued |
| 6 | Drop the redundant `isStillActive` fetch | small; folds into #1 | queued |
| 7 | **Re-sweep `STAGE_ADOPT_SETTLE_MS`** | now ~80% of the descent-stage floor; its 100 ms sweep predates fix 1 | **new — surfaced by fix 1** |

### Baselines to re-measure after each fix

Captured 2026-07-28 before any change, so each fix can be scored against the same numbers:

| metric | baseline | how to re-measure |
|---|---|---|
| `EXPLAIN SELECT * FROM stage WHERE status='ACTIVE'` | Table scan, 215,529 rows, cost 21,914 | `EXPLAIN` in `ramsey-dev` |
| MySQL query time | 45.2 ms | `SET profiling=1; … ; SHOW PROFILES` |
| `GET /stages?status=ACTIVE` (HTTP) | 49–70 ms | `curl -w '%{time_total}'` ×6 |
| QM `isStillActive` span | 51.5 ms mean | "Created new graph" → "Marking stage" in QM logs |
| QM total progression (trigger → active) | 80.7 ms mean | trigger line → "now active" in QM logs |
| stage-advance rate | 979 /hour | count "now active" over a ≥60-min window |
| short-stage mean / full-sweep mean | 0.46 s / 13.01 s | split "now active" deltas at 8 s |
| hoist ramp / fill | 2.60 s / 0.41 s | worker-1 "edge counts for graph" → "Hoist ENGAGED" → "Hoist fill" |
| hoist table coverage | median 37.5%, max 87.5% | "N of 39621 known" in worker logs |
| per-worker throughput / busy | ~2.1M u/s, 95% busy | worker "Throughput:" lines |

### Fix 1 outcome (2026-07-28)

Applied online, no restart, no lock, zero errors in QM/mw/worker logs:

```sql
ALTER TABLE `ramsey-dev`.stage ADD INDEX idx_stage_status (status), ALGORITHM=INPLACE, LOCK=NONE;
```

Added to `database/init-script.ddl` for all three schemas. Note `ramsey-test.stage` and `ramsey.stage` do not
exist on the live instance, so only `ramsey-dev` was altered; the DDL still defines the index everywhere for
fresh installs. `database/README.md` gained an "Applying Schema Changes to a Running Instance" section with a
dated log, since there is no migration tool and the DDL alone would silently diverge from live.

**Confirmed — these are mix-independent and all moved as predicted:**

| metric | before | after |
|---|---|---|
| `EXPLAIN` | Table scan, 215,529 rows, cost 21,914 | Index lookup, 1 row, cost 0.35 |
| MySQL query time | 45.0 ms | **0.06 ms** |
| `GET /stages?status=ACTIVE` (HTTP) | 49–70 ms | **2–9.5 ms** |
| QM `isStillActive` span | 51.5 ms mean | **0.9 ms** |
| QM progression (trigger → active) | 80.7 / 79.0 ms (mean/median) | **30.2 / 22.0 ms** |
| adopt-settle span floor (p10) | 153 ms | **131 ms** |
| **full-sweep stage mean** | 13.01 s | **12.99 s** |

Full sweeps being unchanged is the *expected* result, not a disappointment — they are sweep-bound, not
progression-bound, so nothing in this fix should have touched them. It doubles as a control: worker throughput
(1.89–2.13M u/s, 92–99% busy) and fleet CPU (~95%) are also unchanged, confirming nothing regressed.

Total saving on the descent critical path is **~72–79 ms/stage** (50.5 ms from progression + 22 ms from the
adopt-settle floor). That is **less than the ~110 ms predicted**: the prediction assumed both call sites cost
the full ~50 ms, but only `isStillActive` did — `adoptAfterSettle`'s call sits in a path where it was partly
overlapped. Descent stages should go ~0.46 s → ~0.39 s (median ~0.25 → ~0.17 s), i.e. ~1.2× not ~1.3×.

**Not confirmed — the throughput number.** The measurement window is not comparable to the baseline:

| | baseline (90 min) | post-fix (26 min) |
|---|---|---|
| full-sweep share of advances | 25.6% | **80.6%** |
| short-stage mean | 0.46 s | 3.36 s |

The campaign moved from a descending regime into a wall between the two windows. That also makes "short stage"
mean different things in each — in a descent it is an improvement found in the singles block (~0.25 s), in a wall
it is one found partway through a sweep (seconds). So the raw rate (979 → 324 stages/hr) and any mix-adjusted
reconstruction from it are **both meaningless here**, and are deliberately not quoted as a result. The search is
healthy: 3 new basin minima in 26 min vs 6 in 90 min.

**RESOLVED 2026-07-28 02:00Z — descent window measured.** A `PERTURBATION_BASIN_STALE_STAGES` kick fired (503
stages past basin floor 25,779), throwing the graph to 1.45M cliques and starting a free-fall descent —
**1,788 advances, 99% latency-bound descent stages**, which is exactly the regime this fix targets:

| span | before | after (n≈1,780) |
|---|---|---|
| `isStillActive` | 51.5 ms | **0.64 ms** (max 6.0) |
| QM progression (trigger → active) | 80.7 / 79.0 ms | **24.8 / 26.0 ms** |
| adopt-settle `active → IMPROVEMENT FOUND`, **p10** | 153 ms | **105 ms** |
| adopt-settle, median | 167 ms | 117 ms |
| descent-stage duration, median | 0.25 s | **0.14 s** |

**The two halves now reconcile.** `(167 − 117) + (80.7 − 24.8) = 50 + 55.9 ≈ 106 ms` saved per advance, against an
observed stage-duration drop of 110 ms. So the **original ~110 ms prediction was right and the mid-course
revision down to "72–79 ms" was wrong** — the wall-regime window simply had too few `IMPROVEMENT FOUND` events to
exercise the `adoptAfterSettle` half of the path. Recorded because the error was in the *measurement window*, not
the analysis: a regime that doesn't exercise a code path cannot bound its cost.

**What to attribute to the fix.** The rigorous figure is the **p10 floor**, which is mix-independent: a
descent-stage floor is settle-timer + QM work, and it went **~181 ms → ~125 ms ≈ 1.45×**. The observed median
0.25 → 0.14 s (1.79×) additionally includes regime — a post-kick free-fall finds improvements faster than the
plateau-descent the baseline was taken in. **Attributable descent speedup: ~1.45×.**

### Fix 1 relocated the bottleneck — `STAGE_ADOPT_SETTLE_MS` is now the dominant term

With QM progression down to ~25 ms, the **100 ms settle timer is now ~80% of the descent-stage floor** (p10 105 ms,
of which ~100 ms is the timer). `STAGE_ADOPT_SETTLE_MS` was swept and 100 ms chosen as best (332 stages/min vs 120
at 500 ms — see the compose comment), but **that sweep was run when QM progression cost ~80 ms**, so the
quality-vs-rate tradeoff it balanced has moved. Re-sweeping it is now a first-class candidate, added to the queue
as fix 7. This is the third instance in this codebase of the same pattern: *every speedup relocates the
bottleneck.*

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

1. **Use the hoisted path.** Mid-descent the bound-skip (`max_new < 0 → continue`, `worker.rs:828`) rejects units
   from the per-edge counts alone, *before* any table value is read. Near the floor the threshold sits at/above
   `base_total`, the skip never fires, and the table is exactly what is wanted.
2. **Proactively fill a slice.** 2,476 **uncapped** traversals ≈ 0.41 s.

> **CORRECTION (2026-07-28, before implementing).** The first draft of this section claimed splitting the two was
> "correct independent of which predictor is chosen" and that engaging unconditionally was free. **That is wrong,
> and the split was dropped from the shipped fix.** `single_created` is uncapped *by necessity* — one entry is
> reused across ~19,810 partners with different abort limits, so it can never early-abort the way the kernel does.
> Engaging with an empty table therefore **is** the fill, just lazily and unsharded (39,621 × ~147 µs ≈ 5.8
> core-seconds against 0.41 s for the sharded version). The bound-skip only protects the *pair* block *after* a
> threshold exists; the singles block at stage start has none and pays uncapped either way. Fix 2 as shipped
> changes only the predictor and leaves `engage`/`fill` coupled.

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

### MEASURED 2026-07-28 — the hypothesis above is WRONG. 1.08×, not a large multiple.

Built as `pair_hoist_check loop` (new mode): the full restructure — red-outer/blue-inner walk, no
per-unit `Vec`, no trait dispatch, no div/mod, `D_r` + destroyed-count + both adjacency rows hoisted
out of the inner loop, linear `C_b` walk, unguarded 4-bit-test disjoint path. Integrated over 24
geometrically-spaced points across a whole sweep, graph 222120 (25,618), `nice -n 15`:

```
sweep-wide per-unit: hoisted 0.2616 us   restructured 0.2414 us
decision equivalence: IDENTICAL at every sample point
FULL SWEEP: hoisted 103 s  vs  restructured 95 s  =>  1.08x
```

**Then the cost split, which is the actual finding:**

```
fast path            0.0044 us/unit  (  2%)
X/Y correction       0.2370 us/unit  ( 98%)
slow-path share of evaluated units: 13.69%   =>  ~1.73 us per correction
```

**The hoisted inner loop is 98% correction and 2% everything else.** Every item in the hypothesis
above — the `Vec`, the dispatch, the div/mod, the branchy `cross_pairs` — lives in the 2%. The fast
path costs 0.0044 µs/unit, about 18 cycles; there is nothing left to win there. Fix 4 as specified is
**dead** — 1.08× is not worth shipping, and the restructure is not being merged.

This is the **fourth** confident estimate in this area to collapse on measurement (after the "2×
kernel win", the "20× pruning win", and this doc's own ~20% hoist-gate estimate, which went the other
way). The lesson is sharpening: in this codebase, *reason about which fraction of the cost a change
touches before reasoning about how much faster it makes that fraction.*

### Where fix 4 actually points: bound the correction instead of computing it

`count_through_both` builds `P = ⋂ adj[w]` over the 3–4 forced vertices and enumerates
(k−|S|)-cliques in it — ~1.73 µs, 13.69% of units, 98% of the loop.

The slow-path *fraction* is structural (~50% colour density; 6.13% all-red + 6.13% all-blue + 1.42%
shared-vertex) and cannot be reduced. So the lever is not computing the correction **exactly** when a
bound suffices:

- `created = base_val − correction`, `correction ≥ 0`.
- A unit is rejected when `created > early_limit`, i.e. when `correction < base_val − early_limit`.
- So an **upper bound** on the correction is enough to reject.
- Near the floor `early_limit ≈ broken ≈ 36` while `base_val = C_b + D_r ≈ 1,637`, so accepting needs
  a correction above ~1,600 — enormous for cliques through 4 specific vertices.
- `|P|` (a few row ANDs + a popcount, ~0.01 µs) already bounds it: `correction ≤ C(|P|, k−|S|)`,
  and `count_in_p` already early-returns when `|P| < k−|S|`.

**Hypothesis (UNMEASURED — do not act on it before testing):** a cheap `|P|`-derived bound rejects
most slow-path units without enumerating, collapsing the 98%. Cheap next test: instrument the
distribution of actual correction values and of `|P|` against `base_val − early_limit` on a real
sweep, and count what fraction the bound would reject. That is a measurement, not a build — and given
the record above it must come first.

### MEASURED 2026-07-28 — 60× on the sweep, and the `|P|` bound above was the wrong one

The `C(|P|, k−|S|)` bound sketched above **would barely have fired**: at ~50% density four seed
vertices give `|P| ≈ 17`, and `C(17,4) = 2,380` — larger than the ~1,591 it must beat. A better bound
falls straight out of the identity and costs *nothing*:

> `X` counts red k-cliques in `R ∪ {b}` containing **both** `r` and `b`; `C_b` counts red k-cliques in
> `R ∪ {b}` containing `b`. Every clique counted by `X` is counted by `C_b`, so **`X ≤ C_b`**. In the
> all-red case `Y = 0`, hence `created = C_b − X + D_r ≥ D_r`. Symmetrically `Y ≤ D_r` gives
> `created ≥ C_b` when the cross pairs are all blue.

So **`D_r > early_limit` (all-red) or `C_b > early_limit` (all-blue) proves rejection** — one
comparison on two values already in registers. This is exact algebra, not a heuristic: it cannot
change a result, only skip work provably destined for rejection.

`pair_hoist_check bound`, graph 222120 (25,618), 492,747 slow-path units across 12 sweep positions:

```
C_b  min 297  p1 784  p5 784        <- the left tail the earlier review never published
D_r  min 281  p1 784  p5 784           early_limit near the floor is ~46

bound would reject:  492,220 / 492,747  = 99.89%
exact path rejects:  492,747 / 492,747  = 100.00%
correction values:   min 0  p50 7  p90 39  p99 149  max 427
SOUNDNESS:           0 violations
```

Corrections are tiny (median 7) against a bound of ~800, and in 493K sampled units computing the
correction exactly **never changed an outcome**. Wired into the timed loop and integrated over a whole
sweep, with the accepted-set equivalence check active:

```
hoisted        0.2634 us/unit  ->  103 s sweep
restructured   0.2357 us/unit  ->   92 s   (1.12x)
BOUNDED        0.0044 us/unit  ->  1.7 s   (60.0x)     decisions IDENTICAL at every sample point
```

**Do not read 60× as a fleet number.** A stage is 9.71 s of which only ~8.7 s is the sweep:

| sweep speedup | stage | stage-level gain | sweep's share of the new stage |
|---|---|---|---|
| 60× | 1.15 s | **8.5×** | 13% |
| 30× | 1.29 s | 7.5× | 23% |
| 10× | 1.87 s | 5.2× | 47% |

Amdahl ceiling with fill + ramp fixed at ~1.0 s is **9.7×**. The result is robust — even a 10× sweep
still gives 5.2× — but it **relocates the bottleneck onto the fill and the residual ramp**, which
become ~87% of the stage. Fix 3 (self-healing slice fill) and the residual ramp go from ~3% items to
the main event the moment this lands. That is the fifth consecutive instance of the pattern, and this
time it is predicted in advance rather than discovered afterwards.

**Caveats before building:**
- Measured on **one** graph, near the floor, where `early_limit ≈ 46` against `C_b` min 297 — a huge
  margin. Mid-descent `early_limit` is far larger and the bound fires less; that regime is covered by
  the existing bound-skip, but confirm rather than assume.
- 0.11% of slow-path units still need the exact correction. The enumeration path stays.
- Gate on `hoist_equiv_replay` — the change is exact, so a byte-identical full-stage replay is a real
  test, not a formality.

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
| 2 | Regime-aware hoist gate | 12.4% predicted | **DONE 2026-07-28 — 1.34× on full sweeps, better than predicted** |
| 3 | Self-healing hoist slice fill | ~3% fleet CPU | queued |
| 4 | Hoisted inner-loop restructure | unknown — prototype + measure first | **REJECTED 2026-07-28 — measured 1.08×; the loop is 98% X/Y correction, 2% everything else** |
| 4b | **Bound the X/Y correction instead of computing it** | unmeasured; targets the 98% | **new — supersedes 4** |
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

### Fix 2 outcome (2026-07-28) — shipped and validated

`HoistGate` (worker PR #94), image built and all 14 workers recreated. Measured over a 775 s window,
104 advances, 77 of them full sweeps:

| metric | baseline | after |
|---|---|---|
| ramp to `Hoist ENGAGED` (mean / median) | 2.60 / 2.92 s | **0.61 / 0.00 s** |
| engage index (median) | 5.37 M | **0.017 M** |
| worker throughput | 2.1 M u/s | **2.72 M u/s (1.29×)** |
| **full-sweep stage** | 13.01 s | **9.71 s (1.34×)** |
| busy | 95% | 95% |

Eager on **81%** of engagements; a median ramp of 0.00 s means it engages on the very first batch.

**It beat its own estimate (12.4% predicted, ~30% delivered)** because the policy model scored savings
only on correctly-predicted stages at the 72.7% base autocorrelation, whereas a sustained wall runs a
denser ~81–87% hit rate. Expect the win to be regime-dependent: near the full ramp saving in a wall,
closer to 12% in a mixed regime. Recorded as a case where the *modelling* was conservative rather than
optimistic — the opposite failure to fix 4.

Unchanged and still open: hoist table coverage at engage is median 53%, max 34,669 = **87.5% = 14/16
slices**, exactly as finding 3 predicts. Fix 3 is untouched by this change.

## Running scoreboard

| stage cost | at review start | now |
|---|---|---|
| full-sweep stage | 13.01 s | **9.71 s** |
| descent-stage floor | ~181 ms | **~125 ms** |
| worker throughput | 2.1 M u/s | **2.72 M u/s** |

Remaining budget of a 9.71 s full sweep: ~0.4 s fill, ~0.6 s residual ramp, ~8.7 s hoisted sweep — of
which **98% is the X/Y correction**. Everything else in the queue (fixes 3, 5, 7) is ~3% each. The only
item left with order-of-magnitude potential is **4b**, and it is a measurement before it is a build.

### Fix 4b validation (2026-07-28)

Implemented as `HoistTables::pair_created_bounded` on `feature/bound-pair-correction`.

**Unit level** — the inequality everything rests on is brute-forced rather than argued:

| test | what it proves |
|---|---|
| `corrections_never_exceed_the_single_edge_counts` | `X ≤ C_b` and `Y ≤ D_r` by brute force over every pair of four small graphs. If this were ever false the bound could reject an improving move — the only failure that would silently lose search progress. |
| `bounded_matches_unbounded_at_every_limit` | every pair × 9 limits straddling the true value: `Some(v)` iff `created ≤ limit`, and `v` exact. Exercises both sides of every branch. |
| `unlimited_bound_never_rejects_and_stays_exact` | inert at `i32::MAX`, which is what an unthresholded stage uses. |

**Production scale** — `hoist_equiv_replay` driving the bounded path, full stage, graph 222120:

```
392,495,525 units, threshold = base + 6 (production-tight)
  bound-rejected:  53,697,034  (13.681% of units, 100.00% of slow path)
  kernel CPU 2674.5 s   hoist CPU 37.6 s   =>  71.1x
  stream hash kernel: 0x1fa6992592611803
  stream hash hoist : 0x1fa6992592611803
  per-unit mismatches: 0
  VERDICT: EQUIVALENT
```

**Gap in that run, and how it was closed.** Graph 222120 is the deeply-walled incumbent, so at a
production-tight threshold **`non-exceeded units: 0`** — not one unit in the whole 392M space was
accepted. The replay therefore validated the *reject* stream exhaustively but never compared an exact
*accepted* count. A second replay at a deliberately loose threshold (`slack 2000`, which puts the
limit above the typical `created ≈ 1,637`) forces the accept path at production scale. Worth recording
as a methodology note: **a replay on a walled graph cannot validate the accept path, because there is
nothing to accept.** Any future use of this harness as a gate should check `non_exceeded > 0` before
claiming coverage.

**Accept-path replay (loose threshold, `slack 2000`) — the complementary half:**

```
20,000,000 units
  non-exceeded units:  19,984,011   (99.9% ACCEPTED -- exact counts compared and hashed)
  bound-rejected:      2,322        (0.08% of slow path -- bound correctly stays out of the way)
  kernel CPU 5243.6 s   hoist CPU 25.9 s   =>  202.4x
  stream hash kernel: 0x27dbd600cee955d9
  stream hash hoist : 0x27dbd600cee955d9
  per-unit mismatches: 0
  VERDICT: EQUIVALENT
```

The two replays are complementary by construction: the tight-threshold run bound-rejects **100%** of the
slow path and accepts nothing, the loose-threshold run bound-rejects **0.08%** and accepts 99.9%. Between
them every branch of `pair_created_bounded` is exercised against the shipped kernel at production scale.

---

# Part 2 — what a worker's time is actually made of (2026-07-28, post-4b)

Everything above optimised things that turned out to be small, twice. This section is the instrumented
answer to "where does the time go", so the next change does not have to guess.

## The one rule this document keeps violating

**Reason about which FRACTION of the cost a change touches before reasoning about how much faster it
makes that fraction.** Five estimates in this area have now collapsed, every one for the same reason:

| estimate | predicted | actual | why it failed |
|---|---|---|---|
| "2× kernel win" (pre-session) | 2× | wash | benchmark never aborted |
| "20× dirty-edge pruning" (pre-session) | 20× | unsound | precondition false in production |
| hoist-gate (fix 2) | 12.4% | ~30% | model credited only correctly-predicted stages |
| inner-loop restructure (fix 4) | "large multiple" | **1.08×** | optimised 2% of the loop |
| batch ceiling (fix 5) | busy 82→92% | busy 82→80% | right saving, wrong mechanism |

The failure is never the arithmetic — it is applying it to the wrong denominator.

## Instrumented cost breakdown

`HoistTables` now counts on-demand fills and their wall-clock, reported in the worker's throughput
line. Measured over 300 s, 14 workers (M1 paused for a clean baseline), ~118 stage setups:

```
busy               260.1 s  (87% of wall)
  FILLS            121.9 s  (47% of busy, 41% of wall)
  EVALUATE         138.2 s  (53% of busy, 46% of wall)
non-busy            39.9 s  (13% of wall)

787,910 fills @ 155 us, 6,677 per stage
  37% sharded slice fill (2,476 edges)
  63% ON-DEMAND misses inside the loop  (495,742)
coverage at engage: median 22,289 / 39,621 (56%)
```

**Building the table costs nearly as much as using it.** This was completely invisible before: an
on-demand fill happens *inside* the unit loop, so it counts as "busy" and is indistinguishable from
evaluation in the throughput line.

### Why on-demand misses are front-loaded (and why more polling will not fix it)

Under `SEQUENTIAL_WITH_SINGLES`, `pair_index = red_idx × blue_count + blue_idx` — blue is the **inner**
loop. A 1.6M-unit batch spans ~81 red edges × **all 19,810 blue edges**, so a worker needs essentially
the *entire* blue table inside its **first batch**. Coverage at that moment is 56%, so the misses are
all paid up front, before any `refresh_budget` re-check can help.

That kills the obvious fix. Raising `refresh_budget` (12 → N) polls *later*, and later is too late.
Anything that helps has to raise coverage **before the first batch** — i.e. wait briefly after
publishing, not poll more often afterwards.

## Fix 4 re-evaluated: same code, same harness, opposite verdict

Fix 4 was rejected at 1.08× because the scaffolding it removes was 2% of a loop that was 98%
correction. **4b deleted the correction, so its denominator changed.** Re-measured against current
production (scaffolding **+** bound), across both regimes, 24 sample points each:

| regime | graph | production | restructured | ratio |
|---|---|---|---|---|
| **wall** | 276750 (25,604) | 0.0254 µs/unit | 0.0042 µs/unit | **6.1×** |
| **descent** | 274667 (600,047) | 0.0314 µs/unit | 0.0095 µs/unit | **3.3×** |

Decisions IDENTICAL at every sample point in both. The scaffolding cost is unchanged at ~0.021 µs/unit
— it was 8% of the unit, it is now 83%.

**Lesson: a rejected optimisation is only rejected against the cost structure it was measured in.**
Re-check rejections after anything that changes the mix.

## Which lever is bigger — and a correction

Fills are 41% of wall and evaluate is 46%, which initially read as "fix the fill first". That was
wrong: **fills can only be removed (÷1), evaluate gets a 6.1× multiplier.**

| change | stage lift |
|---|---|
| restructure only | **1.63×** |
| fill fix only (on-demand → 0) | 1.34× |
| **both** | **2.79×** |

A multiplier on a slightly smaller share beats a subtraction on a slightly larger one. Recorded
because the error took two passes to catch, in a section explicitly about not making that error.

## Where the harness misleads

Harness per-unit (0.0254 µs) is **3.3× faster than production** (0.084 µs busy-adjusted), despite
being `nice -n 15` on a saturated box. The cause is that **the harness pre-fills the whole table and
production does not** — so harness ratios describe the loop *with a warm table*, and production pays
on-demand fills on top. Any harness ratio should be applied to the *evaluate* share only, never to a
whole stage.

## Queue after this section

| # | item | targets | est. |
|---|---|---|---|
| 4 (revived) | inner-loop restructure | 46% of wall, ×6.1 | **1.63×** |
| 3' | coverage before first batch (wait, not poll) | 26% of wall | 1.34× |
| 7 | re-sweep `STAGE_ADOPT_SETTLE_MS` | descent floor | ~3% |

`HOIST_FILL_SLICES` vs fleet size is **resolved incidentally**: with the M1 online, 22 claimants
against 16 slices push coverage to 100% max. It regresses to 87.5% whenever the fleet is smaller than
the slice count, so the constant is still wrong in principle, just not binding today.

---

# Part 3 — the hoist gate saga, and where this leaves the project (2026-07-29/30)

## What shipped after Part 2

| # | change | measured |
|---|---|---|
| 5 | `MAX_FETCH_SIZE` 1M → 4M (ceiling had become binding) | throughput +25%, full sweeps 5.27 → 3.86 s |
| 6 | stack buffer replacing the per-unit `vec![]` | evaluate/unit 56.3 → 40.1 ns |
| 7 | wait-for-coverage + self-healing slice gap fill | misses/hoisted stage 3,780 → 2,094; fill 0.99 → 0.78 s |
| 8 | **hoist gate 5M → 50k, `HoistGate` deleted (−238 lines)** | **throughput 0.635 → 2.668 M u/s** |
| — | ILS: basin-stale 500 → 1000, escalation cap x32 → **x64** | see below |
| — | `idx_stage_status`, docs, UI constant alignment | — |

## The gate saga — the most instructive failure in this document

The gate exists so a stage that advances immediately does not pay ~0.6 s building a per-edge table
it will not reuse. It was set at a **fleet index of 5,000,000**, derived from *hoisted* throughput
("a wall stage crosses 5M in ~0.24s") — but every unit before the gate is by definition **unhoisted**,
so the real cost was **2.6 s near the floor and 35–41 s post-kick**, per stage.

Three attempts, in order:

1. **Predict from stage depth** (Part 2, fix 2). Marked a stage "deep" if it reached the 5M *engage*
   gate. But engaging makes a stage ~20× faster, so it advances **sooner** — around 3M — records
   itself shallow, and disarms the next stage. *The signal punished the stages where the hoist
   worked.* 42% of stages paid full ramp.
2. **Amortisation threshold + hysteresis.** Split "did this fill pay for itself" (250k) from the
   engage gate, and required a run of 8 shallow stages before dropping eagerness. Got to 4%.
3. **Predict from the QM's exhausted/improved outcome.** Semantically the cleanest — ground truth,
   speed-independent — and **the worst of the three: 100% of stages paid full ramp.** Exhaustion
   proxies *regime*; the gate needs *amortisation*. At high clique count a stage advancing on an
   improvement still churns millions of units and amortises fine.

**The answer was to lower the constant.** The space is `[0, 39_621)` singles then ~392.5M pairs; past
~50k every unit is a pair, which is what the hoist is for. Singles need no gate either way — `created`
for a single flip *is* the table entry.

| | 5M + depth | 5M + QM outcome | **50k gate** |
|---|---|---|---|
| full-ramp stages | 4% | 100% | **0%** |
| median engage index | 14,000 | 5.0M | **68,036** |
| ramp to engage | — | — | **0.30 s** |
| throughput | 0.635 M u/s | 0.451 M u/s | **2.668 M u/s** |

Two things worth carrying forward:

- **The decisive number was available three steps early.** `ramp 35.4 s vs wasted fill 0.61 s = 58:1`
  means break-even is a **1.7%** chance a stage is worth it. At that ratio the correct gate is
  "almost always engage" and *any* predictor is a liability. That was measured, written down, and
  then ignored in favour of building a better predictor.
- **The depth predictor was also engaging at the wrong place.** Its "eager" stages fired at a median
  index of 14,000 — *inside* the singles block — so workers began filling slices while still
  competing for singles work. That is why 4%-full-ramp still ran 4.2× slower than a 50k gate.

## Running scoreboard (session end)

| | at review start | now |
|---|---|---|
| full-sweep stage | 13.01 s | **~4.7 s** |
| per-worker throughput | 2.1 M u/s | ~10 M u/s near floor |
| descent-stage floor | ~181 ms | ~125 ms |
| all-time best graph | 25,758 (as documented) | **25,604** (graph 276750) |

## Proposed next steps, ranked

### 1. Storage is now the binding operational constraint — NOT throughput

`graph` went **9.91 GB → 20.68 GB in one day** (226,022 → 462,670 rows). At ~5,700 stages/hour and
~44 KB per graph row that is **~6–11 GB/day**, and there is still no retention policy. A week of this
is ~100 GB.

Every stage writes a full 39,621-char bitstring, but consecutive stages differ by **1–2 edge flips**.
Options, cheapest first: store flips-from-parent instead of full bitstrings for non-milestone graphs;
or keep full data only for kick seeds, basin floors and incumbents and prune the rest on a schedule.
This is the one item that will stop the search if ignored, and it got worse *because* the engine work
succeeded.

### 2. The ILS staleness trigger has a structural gap

`PERTURBATION_BASIN_STALE_STAGES` counts stages since the basin's **own** floor improved. In a bad
basin, tiny improvements are always available and each one resets the clock — so the mechanism that
exists to rescue a stuck search is disabled by the search being stuck. Measured over campaign 10's
71 kicks: **4 of 71 basins (5.6%) stalled >10× above the incumbent while still resetting the clock**,
one for 18,412 stages (~15 h) at 30.6× the incumbent.

Raising the window to 1000 lengthens that tail. The fix is an **incumbent-relative** guard — kick if a
basin cannot get within N× of the incumbent within N stages — not a larger absolute count.

### 3. Re-run the settle-timer A/B properly

`STAGE_ADOPT_SETTLE_MS` 100 → 50 was **catastrophic** (descent flatlined at ~836K where the 100 ms
baseline reached its 25,721 floor in 43 min), which is itself informative: the settle window does real
selection work rather than adding latency. That makes **lengthening** it the interesting direction, and
200 is currently deployed but **unmeasured** — its trial ran entirely inside a 15-hour stall and
measured "stranded in a bad basin", not the setting. Judge on **floor reached per kick cycle**, never
stage rate.

### 4. Remaining engine items — small, and now clearly secondary

- Blue-only slice striping (74–91% of on-demand misses are blue). **Requires a versioned Redis key**
  (`hoist_shard_v2:`) — it changes the slice→edge mapping, and a fleet on an older image writing the
  old layout into the same keys would silently corrupt tables.
- Don't reset `current_fetch_size` on stage change when the stage is likely hoisted (~3%).
- Full red-outer/blue-inner loop restructure: measured **~1.10×** in production (not the 7.3× the
  harness shows — the harness/production gap is a *fixed* ~28.6 ns offset, not a ratio). Not worth
  ~150 lines in the hottest loop.

### 5. Methodological note for whoever picks this up

Six estimates in this document collapsed on measurement, every one for the same reason: applying a
speedup ratio to the wrong denominator. The rule, stated once more because it keeps being violated —
**work out which fraction of the cost a change touches before working out how much faster it makes
that fraction.** The corollary learned the hard way in Part 3: when the payoff asymmetry is large
(58:1), stop optimising the decision and just take the cheap side always.

---

# Part 4 — stage turnover was the bottleneck, not the inner loop (2026-08-24)

## What this part settles

Part 3 left "remaining engine items" as the queue and treated throughput as roughly done. That was
right about the inner loop and wrong about the fleet: the largest single non-compute cost was
**stage-turnover latency**, and it had been sitting in plain sight in a docstring the whole time.
`StageAdoptScheduler` says the poll interval "also floors the stage duration" — true of the
improvement path, which is why the settle timer exists, and equally true of the **exhaustion** path,
which never got an event.

## Finding 5 — the 1 s progression poll quantised every exhausted stage

The queue manager only learned a stage was finished on its next `checkForProgression` tick. Workers
that had exhausted the work space sat idle until then.

The signature is unmistakable in the `stage` table. Lifetime of exhaustion-driven stages
(`updated_date - created_date`, stages > 1500 ms), bucketed by `ms mod 1000`, n = 446:

| ms mod 1000 | 0–99 | 100–899 (8 buckets) | 900–999 |
|---|---|---|---|
| count | 69 | 153 total | 224 |

**66% of stages ended within 100 ms of a whole second**, where a distribution not pinned to the tick
puts 20% there. Every QM "fully processed" log line in a sample of twelve landed at `.409` ± 6 ms —
the same sub-second phase, i.e. the scheduler tick, not the work.

Mean exhaustion-driven stage: **4128 ms**, of which ~500 ms was waiting for the tick. Exhaustion
stages were 446/600 of stages and the overwhelming majority of wall-clock, so this was
**~11.6% of fleet wall-clock spent with every worker idle and nothing left to claim.**

### Why the earlier `STAGE_PROGRESSION_FREQUENCY_MS` A/B missed it

1000 → 100 was A/B'd on 2026-08-23 and read NULL (−1.3σ on throughput, idle unchanged), which is
why the compose comment concludes "exhaustion detection was NOT the source of the ~13% worker idle".
That conclusion was wrong, but the measurement was not: polling 10× more often recovers the latency
**and** spends CPU on a box already saturated by 14 workers, and the two cancel. The A/B could only
ever have shown the net of those. Judging it on stage rate rather than units/s would not have helped
either — both move together here.

The lesson generalises the Part 3 corollary: **an A/B that changes two things at once cannot falsify
a hypothesis about one of them.** The quantisation histogram above is the measurement that should
have been run first — it is one SQL query, it needs no deploy, and it isolates the variable.

### The fix: event-driven completion (worker #112, QM #84)

The worker whose report carries `processed_count` over `total_pairs` publishes the stage id on a new
`stage_exhausted_events` channel. `INCRBY` is atomic, so exactly one worker sees the crossing and
the QM gets one event per stage rather than one per straggler.

Deliberately **not** the settle-timer treatment `best_result_events` gets, and the asymmetry is the
whole point:

| | new best | stage complete |
|---|---|---|
| what it means | weakest qualifying improvement so far | every unit evaluated **and** reported |
| can something better still arrive? | yes | **no** — the top-N set is final |
| therefore | wait out a settle window for a better step | act immediately |

A settle window on completion would buy zero quality while the whole fleet idles. Hence a separate
channel rather than a second reason to arm the same timer.

**The event cannot cause a wrong advance.** It is only a trigger: `checkStageNow()` runs the same
per-stage logic under the same per-campaign lock as the polling loop, and `handleExhaustedStage`
independently re-checks `isStageExhausted` and `isFullyProcessed`. A spurious, duplicated or late
event produces a no-op check. The polling loop remains the fallback, so a dropped pub/sub message
costs latency, never correctness.

### Measured

| | before | after |
|---|---|---|
| stages within 100 ms of a whole second | 66% (n=446) | **21% (n=122)** — flat is 20% |
| mean exhaustion-driven stage | 4128 ms | **3880 ms** |
| stage rate | 18.60/min (sd 2.26, n=15) | 21.40/min (sd 2.05, n=15), **+15.1%, 3.5σ** |

The first rate window read +7.5% at 1.9σ, but its tail overlapped local `--release` test runs
competing for the same cores. Over a clean 15-minute window the effect is **+15.1% (3.5σ)**, and
over the last 8 minutes with no local compute at all, **+20.3% (3.8σ)** — the per-minute series
trends upward across the window exactly as the contamination clears. Take +15% as the conservative
figure and note the ceiling is higher.

Predicted 11.6%, delivered 15–20%, which is the rare direction for this document. The prediction
counted only the quantisation itself; faster turnover also shortens the window in which workers sit
in batch-tail idle, so the two compound. What the event does *not* touch, and what bounds any
further gain here: batch-tail skew (a worker that exhausts early still waits out the slowest peer's
in-flight batch), QM progression (~25 ms) and stage seeding.

## Finding 6 — per-stage Redis keys never expired (worker #111)

`processed_count`, `stage_work_index` and `best_results` are deleted by the QM on stage advance, but
in-flight workers recreate them immediately afterwards. Adding the `processed_count` delete (QM #83)
cut that leak by only **19%** — stragglers recreated it on 81% of stages. **Deletion cannot win that
race; expiry can.** Every worker write path now sets a refreshing 1 h TTL.

Measured over 137 stages with half the fleet upgraded: **3.90 → 1.80 keys/stage**, and what remains
now expires rather than accumulating forever.

Two things worth keeping:

- **`stage_work_index` still reads `ttl=-1` in production and that is expected.** `SET` clears a
  TTL; `INCR` and `ZADD` preserve one. A worker on an older image issuing a bare `SET` strips the
  TTL a new worker just set, which is why only this key looks immortal while the other two show
  3600. It resolves when every fleet on the campaign is upgraded. The TTL now rides on the `SET`
  itself (`SET ... EX`) so there is no window where the key is written without one.
- **The TTL introduced a stall risk, which is fixed in the same PR.** `stage_work_index` is the key
  the QM reads to *detect* exhaustion, and once the last unit is claimed no successful claim writes
  it again. A fleet paused between full-claim and advance would have lost the key and stalled
  unrecoverably. The exhausted branch refreshes too. Workers stop asking about a stage as soon as it
  advances, so this cannot keep a dead stage alive.

## Finding 7 — three cost hypotheses, measured and closed

Recorded because the methodology note in Part 3 is about estimates that collapse; these collapsed
*before* anything was built, which is the cheaper place for it to happen.

- **Skipping the per-cycle middleware call.** Part 2 costed the cycle preamble at "~22 ms", which
  would make the fleet active-stage call ~11% of a 184 ms batch and worth caching against the
  pub/sub announcements. Measured directly: **2.2 ms mean, 1.1 ms median, 8.2 ms max** — about 1% of
  a batch. Not worth the fleet-repoint staleness. Do not revisit without re-measuring the endpoint.
- **Shrinking the batch to cut tail skew.** Total loss ≈ `c/(c+T) + T/(2S)` for per-cycle overhead
  `c`, batch work `T`, stage `S`. With `c ≈ 3 ms` and `S ≈ 3300 ms` the optimum is `T ≈ 140 ms`
  against the deployed `TARGET_BATCH_LOOP_MILLIS = 200`: 4.5% → 4.2%, i.e. **~0.3%**. The 200 ms
  target was chosen when `c` was ~22 ms and is still near-optimal at the new `c`. Leave it.
- **On-demand fill cost is smaller than the log line suggests.** "N on-demand fills costing X (10% of
  busy)" counts `fills`, which *includes* the worker's co-operative slice share (`slice`). True
  on-demand misses are the separate `misses` figure, ~30% of `fills` ≈ **3% of busy**. The other 7%
  is the unavoidable rebuild of the ~8,983 entries (23% of the table) that a stage's flip
  invalidates. Blue-only slice striping (Part 3, item 4) therefore targets ~3%, not ~10%.

## Finding 8 — the hoist carry is now validated at production scale (worker #113)

`carry_forward` keeps every entry whose `cross_pairs` relation to the flipped edges is Mixed in both
graphs. Too permissive by any margin and a stale `created` is served to every unit of the stage
**and published to peers**, with nothing downstream to catch it. Its coverage was exhaustive but only
at n=9/10, k=4/5.

Now checked on three consecutive real campaign-3 base graphs at **n=282, k=8** — a single-edge
advance, a pair-edge advance, and the two-advances-at-once case a worker hits when it misses an
announcement — comparing all 39,621 entries against a fresh rebuild. **Zero mismatches.**
Mutation-checked rather than assumed: dropping the `cross_pairs` terms produces **2,399**
disagreements on the single-edge case, so the test has teeth. ~43 s for all three:

```
cargo test --release --test carry_forward_production_scale -- --ignored
```

## Finding 9 — the sequential inner-loop restructure, closed for good (worker #108)

An independently proposed full red-outer/blue-inner restructure claimed 4.2×. Reviewed in detail: no
correctness flaw, same results as the current loop. But the harness/production gap here is a **fixed
per-unit offset, not a ratio**, so a speedup measured against the harness's much smaller denominator
does not carry over. Production: **~1.05×**, consistent with the ~1.10× already recorded in Part 3
item 4. Not worth ~150 lines in the hottest loop in the codebase. PR closed and branch deleted; this
is the second independent measurement of the same idea, so treat it as settled.

## Running scoreboard (Part 4 end)

| | Part 3 end | now |
|---|---|---|
| mean exhaustion-driven stage | 4128 ms | **3880 ms** |
| stage rate (campaign 3, 14 workers) | 18.60/min | **21.40/min** (+15.1%, 3.5σ) |
| worker idle | 9–17% of wall | **~7%** |
| per-stage Redis key growth | 3.90 keys/stage | **1.80** (half the fleet upgraded), and now expiring |
| hoist carry validated at | n=9/10, k=4/5 | **n=282, k=8, real campaign graphs** |

## Queue after Part 4

1. **Storage retention is still the binding operational constraint** (Part 3 item 1). Untouched, and
   every throughput win makes it worse. This is the one that stops the search.
2. **The ILS staleness trigger gap** (Part 3 item 2). Untouched. Currently parked behind
   `PERTURBATION_BASIN_STALE_STAGES: 50000`.
3. **Upgrade the M1 fleet.** It is running neither Part 4 change, and its bare `SET` strips
   `stage_work_index` TTLs on campaign 3, so finding 6's leak fix is only half-deployed.
4. **Remaining turnover cost is batch-tail skew**, and finding 7 shows batch resizing cannot recover
   it. Anything further here needs a different mechanism, not a tuned constant.

---

# Part 5 — the measurement protocol, written down because Part 4 was measured badly (2026-08-24)

Part 4's engineering conclusions stand. Its *numbers* were produced by a process that then generated
three consecutive wrong answers in a row, and this part exists so the next person does not repeat it.

## What went wrong

Chasing a stage-tail optimisation, the following were reported in sequence, each confidently, each
wrong:

1. "the taper is a −9.6% regression" — the deploy changed **two** things (the taper *and* the
   redis 1.6.0 bump that had landed on `develop` in between).
2. "redis 1.6.0 is the regression" — it is **+4.0% faster**.
3. "the taper is a −13.5% regression" — it is **indistinguishable from zero**.

Four distinct defects produced those, and each is cheap to avoid:

- **An uncontrolled second fleet.** `m1` was mapped to the same campaign and contributing ~15% of
  fleet throughput. It contaminates campaign stage rate obviously — but also *per-worker* throughput,
  because work it claims is work local workers then sit idle waiting for. Pausing it moved local idle
  from ~8.7% to ~3.6%. **Pause every other fleet before measuring** (`fleet.campaign_id = NULL`, a DB
  update, no redeploy).
- **Sampling during the restart ramp.** A recreated worker starts with a cold hoist table and needs
  **~4 minutes** to reach steady state. Readings taken at 2–3 minutes understated throughput by
  ~20% (4.8–5.6 M u/s against a true 6.0–6.3). **Discard the first 4 minutes, always.**
- **A fragile parser.** Extracting fields with several `grep`s joined by `paste` silently
  mis-aligns when any log line lacks a clause, and the misalignment shifts every later field. It
  reported 98 samples in a 7-minute window and 28 in a 9-minute one. **One regex per line, skip
  lines that do not match.**
- **No control.** Two runs of the *identical* build 30 minutes apart differ by **4.9σ** (6.304 vs
  6.210 M u/s). The environment drifts ~1.5% on that timescale, so anything smaller than that is not
  attributable without an interleaved control. **Re-measure the baseline after the variant**, and if
  the repeat does not reproduce, discard both.

## The protocol

Per-worker `units/sec` from the workers' own throughput lines, not campaign stage rate — stage rate
mixes in every other fleet on the campaign.

1. Pause all other fleets on the campaign.
2. Deploy variant, **discard 4 minutes**, measure 10 (`n ≈ 275` across 14 workers).
3. Deploy baseline again, same treatment.
4. Report the variant against the **adjacent** baseline, and state the baseline-to-baseline drift.

`collect.py` in the session scratchpad implements steps 2–4.

## Results under the protocol

| config | units/sec/worker | idle % of wall | batches/30s | ms/batch |
|---|---|---|---|---|
| redis 1.5.0, no taper | 6.060 ± 0.016 | 4.02 | 159.9 | 181.0 |
| redis 1.6.0, no taper | **6.304 ± 0.014** | 3.64 | — | — |
| redis 1.6.0 + taper | 6.174 ± 0.020 | **2.33** | 169.0 | 174.2 |
| redis 1.6.0, no taper (repeat) | 6.210 ± 0.013 | 3.15 | 161.7 | 180.3 |

**redis 1.6.0 is +4.0% over 1.5.0** (11.5σ). Not a regression. The release notes showed no plausible
mechanism for a regression, which should have been enough to suspect the measurement first.

## The stage-tail taper: mechanism works, net effect zero — NOT merged

The idea: a stage ends when its slowest worker finishes, so a worker claiming a full batch as the
space drains holds the fleet idle for that batch. The claim script therefore hands out at most
`1/8` of what is left, floored at `1/8` of a batch so the drain terminates.

It does exactly what it was designed to do — **idle 3.15% → 2.33%** (3.7σ), busy 96.8% → 97.6%,
`ms/batch` 180 → 174 — and the gain is then **entirely consumed by its own overhead**: batches/30s
rise 161.7 → 169.0, and each extra claim costs a Redis round trip plus a full worker cycle. Net
−0.6% at 1.5σ against a baseline that drifts 1.5%.

So it is not merged. Two things to know before anyone tries again:

- **The prize is smaller than it looks.** The tapered region is `8 × batch ≈ 7.5 M` of a 392 M unit
  space — **1.9%**, about 100 ms of fleet time. Sizing this against *total idle* rather than against
  the work actually sitting in the tapered region is the same denominator error Part 3 warns about.
- **There is a feedback path.** `next_fetch_size` divides elapsed time by the units *actually*
  granted, so a tapered batch's fixed overhead inflates its apparent per-unit cost and the worker
  shrinks its request — which shrinks `minimum = batch/8` — which shrinks the next grant. Any retry
  must take the floor from a **stage-level** constant (e.g. `total_pairs / 1000`), never from the
  requesting worker's adaptive batch. The unit test missed this entirely because it drains the space
  with a constant batch size instead of driving it through `next_fetch_size`.

What survives and is worth keeping: the live-Redis test asserting that claims **tile `[0, total)`
exactly once** — no gaps, no overlaps, ending exactly at the total. A gap is a silently skipped work
unit and an overlap is duplicated effort, and neither is visible downstream. Mutation-checked both
directions.

## Scoreboard correction

Part 4's stage-rate figures (+15.1%, +20.3%) were measured with `m1` active on the same campaign and
so are not attributable to the event-driven change alone. The change's own evidence is unaffected and
still stands: the quantisation histogram (66% → 21% of stages ending within 100 ms of a whole second)
is a structural measurement of queue-manager behaviour, and the drop in worker idle is per-process.
The honest summary is **"removed the poll quantisation, direction confirmed, magnitude not cleanly
measured"** rather than a specific percentage.


# Part 6 — worker count was 3.6% left on the table (2026-08-24)

Workers are single-threaded, so N of them cap at N×100% CPU. Measured on the M4 Max (16 logical:
12P+4E) at the deployed 14: **workers took 1343% of 1600% available and every other service on the
box took 13%** — about 2.4 cores idle, with the workers themselves already at 96% each and therefore
unable to absorb it.

Swept under the Part 5 protocol (m1 paused, 5-minute ramp discarded, fleet throughput sampled 10×30 s
per config, and 14 run both first and last to detrend drift — which turned out to be 3.1%, larger
than the effect being measured, so the bracketing mattered):

| workers | fleet M units/sec | vs interpolated 14-worker baseline |
|---|---|---|
| 12 | 80.67 ± 0.62 | −9.0% |
| 14 | 86.62 ± 0.61 (first), 89.28 ± 0.69 (last) | baseline |
| 16 | **90.43 ± 0.54** | **+3.6%** |
| 18 | 91.01 ± 0.87 | +3.5% |

Marginal gain per added worker: +7.3, +2.5, +0.6. Saturated at 16 — the last two workers land on
efficiency cores and contribute little, and beyond that more containers are pure overhead. **16 is
deployed.**

Worth noting what this says about the shape of the problem: it is a 3.6% win with zero code risk,
found by looking at `docker stats` rather than at the code. The engine has been optimised hard; the
*deployment* had not been looked at once. Re-sweep on any new host rather than carrying 16 over.

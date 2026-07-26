# Review: pair-move hoist proposal — measured verdict

**Reviews:** `pair-move-hoist-proposal.md` (2026-07-25)
**Date:** 2026-07-25
**Harness:** `single-flip-check/src/bin/pair_hoist_check.rs` (modes: `verify`, `fastpath`, `bench`,
`profile`, `integrate`, `precompute`). It calls the shipped worker kernel
(`get_new_cliques_with_limit`) and the shipped `CliqueCollection` / enumerators, so "production"
in every table below is the real thing, not a re-implementation.

## Outcome (2026-07-26) — built, deployed, and measured

Shipped as `ramsey-worker-rust/src/hoist.rs`. What the review got right, and what it missed:

| Review said | Actually happened |
|---|---|
| identity is exact | confirmed — full-stage replay over all 392,495,529 units, zero mismatches |
| §2 pseudocode mishandles shared-vertex pairs | confirmed — fixed before shipping |
| fast path 86.32% | confirmed live |
| ~29× on a sweep | the kernel win was real but **invisible until the plumbing was fixed** |
| don't cap the precompute | held |
| shard the fill | built; 31–81% of the table now comes from peers |

**The reviewer's biggest miss:** it treated ~29× as the deliverable. In production the kernel gain
was hidden behind fixed per-cycle costs that had never mattered before, and each one had to be
found and removed in turn — a full-table-scan on `stage` (11.8ms → 0.135ms with an index), a
per-cycle fleet HTTP call, a batch size that could not adapt, and finally a 1-second sleep on
transient stage races that left the fleet at 19% CPU. That last one was a pre-existing bug that
only became reachable once stages advanced quickly.

Lesson worth carrying: **making one stage of a pipeline 30× faster does not make the pipeline
faster; it relocates the bottleneck, usually somewhere nobody was measuring.**

Also of note: two of the review's own conclusions were later overturned by better measurement —
`MAX_INCREMENTAL_FLIPS` ("not worth changing", measured only in the wall, actually 51% fallback in
descents) and the sharded fill ("3% peer coverage", which turned out to be a broken metric reading
the pre-publish sweep). Both were caught by checking ground truth rather than the instrument.

## Verdict (as written at review time)

**Build it — the approach is sound and worth substantially more than proposed.** Measured
**~29× on a full stage sweep** (single core), versus the proposal's 6.5×–15× estimate.

Three corrections, one of them a real bug:

1. The §2 pseudocode is **wrong for shared-vertex pairs** and silently corrupts 1.4% of the space.
2. The direct-X/Y slow path is not an "upper end" nice-to-have — it is **where essentially all of
   the win lives**, and it is *simpler* than the fallback it replaces.
3. Capping the precompute saves nothing and is unsafe. Don't cap.

---

## 1. The identity is correct

`created = (C_b − X) + (D_r − Y)`, with `X>0 ⟹ cross pairs all red`, `Y>0 ⟹ all blue`, and X,Y
never both non-zero. Checked against the production kernel, uncapped, exact equality:

| graph | mono-8 cliques | pairs sampled | `created` mismatches | X/Y vs brute force | both X,Y > 0 | MIXED with X or Y > 0 |
|---|---|---|---|---|---|---|
| 26994 (all-time best) | 25,758 | 64,000 | **0** | **0** | **0** | **0** |
| 8644 | 25,840 | 20,000 | **0** | **0** | **0** | **0** |

X and Y were also computed a second, independent way (enumerate all cliques through the edge, then
filter for containment) and agreed everywhere. Answer to §5 Q1: **no counterexample; the algebra
holds.**

## 2. BUG — the §2 pseudocode mishandles shared-vertex pairs

```rust
let cross_all_red  = red(x,u) && red(x,v) && red(y,u) && red(y,v);   // "shared-vertex: 1 test"
let cross_all_blue = !red(x,u) && !red(x,v) && !red(y,u) && !red(y,v);
```

The annotation is wrong: the 4-test form does **not** degenerate to the single needed test. With
`y == u`:

- `red(y,u)` = `red(y,y)` = self-loop = **false** → `cross_all_red` is always false.
- `red(x,u)` = `red(x,y)` = `r`, which is red = true → `!red(x,u)` = false → `cross_all_blue` is
  always false.

Both false ⇒ classified **MIXED** ⇒ fast path taken with X,Y assumed zero. Measured on graph 26994:
**63 of 63** sampled shared-vertex pairs produced a wrong `created`, errors up to **+182 cliques**.
This is exactly failure mode §3.1, present in the proposal's own pseudocode.

**Fix:** cross pairs are the internal pairs of the forced vertex set that are neither `r` nor `b`.
Excluding the degenerate pair *and* the two that collapse onto `r`/`b` leaves exactly one cross pair
in the shared case, and all four when disjoint. With that fix the identity is exact (table above).

Shared-vertex pairs are **1.42%** of the space (the proposal says 2.8% — 2× high, immaterial), and
they are always slow-path, never fast.

## 3. Fast-path fraction: 86.32%, and flat (answers §5 Q2)

Computed **exactly** over all 392,455,908 pairs (765 ms), not sampled:

| class | count | share |
|---|---|---|
| FAST (cross pairs mixed) | 338,759,397 | **86.318%** |
| slow — cross all RED | 24,072,506 | 6.134% |
| slow — cross all BLUE | 24,057,327 | 6.130% |
| slow — shared vertex | 5,566,678 | 1.418% |

The §5 Q2 worry — that cardinality-descending enumeration correlates cross-pair colors and depresses
the fast path early — **is unfounded**: 85.20% over the first 100K units, 86.48% over the first 1M,
86.32% overall. A 1.1pp dip at the very head, nothing more.

It is also not basin-specific: the live mid-descent graph 53980 (394,915 cliques) gives **86.326%**,
essentially identical. The fraction is a structural consequence of the ~50% color density.

## 4. Where the win actually is

Cost is **extremely front-loaded** — production per-unit varies **38×** across a sweep, because the
cardinality-descending enumeration puts the expensive edges first. Hoisted cost is flat.

| position in pair sweep | production µs/unit | hoisted µs/unit | speedup |
|---|---|---|---|
| head (first units) | 158.7 | 0.353 | **450×** |
| 1% in | 19.9 | 0.262 | 76× |
| 10% in | 9.3 | 0.258 | 36× |
| 30%–99% (the tail) | ~4.2 | ~0.25 | ~17× |
| **integrated over the whole sweep** | **7.79** | **0.255** | **29×** |

Two independent integrations agree: 100 uniform windows → 8.075 µs / 29.1×; 24 geometrically-spaced
points, trapezoid → 7.791 µs / 28.9×.

**A single-window benchmark would have been badly misleading here** — three different windows gave
6.8×, 17.0×, and 362.9×. This is the same trap that produced the two collapsed estimates in §6.

### The direct-X/Y slow path is the whole story

| slow-path strategy | speedup (whole sweep) |
|---|---|
| fall back to today's seeded re-traversal (§2 main proposal) | **~7×** — matches the 6.5× estimate |
| compute X/Y directly (§2 "upper end", estimated 15×) | **~29×** |

The retraversal variant is Amdahl-capped: the 13.7% slow path is exactly the *expensive* 13.7%, so
it dominates. Computing X/Y directly costs ~1 µs instead of ~100 µs, and there is a simplification
the proposal misses:

> **No graph mutation is needed.** X counts red k-cliques containing both `r` and `b` in `R ∪ {b}`.
> Seed on the union vertex set S (3 or 4 vertices), take `P = ⋂_{w∈S} adj[w] \ S`, and count
> (k−|S|)-cliques in P. The pretended edge `b` has *both* endpoints in S, which are cleared out of
> P, so every edge induced on P is an original edge — the base adjacency is used unchanged. Same
> argument for Y in the complement.

So the "slow" path is: 4 row-ANDs, a couple of bit-clears, and a small count. No `flip_edges`, no
kernel call, no unflip. **It is simpler than the fallback it replaces**, which inverts the §2
recommendation to ship the simple version first.

Correctness was checked continuously, not just at the end: accepted-result sets and threshold
trajectories were identical between production and both hoisted variants at every sample point and
across 2M+ contiguous units.

## 5. Don't cap the precompute (answers §5 Q3)

| | time | per edge |
|---|---|---|
| capped at 1024 | 5.837 s | 147.3 µs |
| uncapped | 5.840 s | 147.4 µs |

**Capping saves 0.05%.** The cost is neighbourhood exploration, not clique counting, so the abort
never fires early enough to matter. And capping is *unsafe*: 1,171 edges exceed a 1024 cap
(max `C_b` = 2,386), while the abort limit `threshold − 1 − base_total + destroyed` can reach ~2,970
given a max per-edge destroyed count of 1,481. A capped entry used as an exact value under-reports
`created` → false accept.

Uncapped costs the same and **deletes failure mode §3.2 entirely**. Recommendation: rebuild
uncapped per stage, share via Redis exactly like the existing 318 KB per-edge table (these are
2 × 39,621 × 4 B = 317 KB). No incremental-update machinery needed — see below for why.

Distribution on graph 26994: `C_b` mean 818.7 / median 784 / max 2,386; `D_r` mean 818.1 / median
784 / max 2,299. (The proposal's ~817 figure is confirmed, and is per *single* flip, so a typical
pair creates ~1,637.)

## 6. Fleet-level caveat the proposal misses

Once the sweep costs 0.255 µs/unit, it takes ~7 s across 14 workers — at which point a **5.8 s
serialized precompute is no longer negligible**. It roughly halves the win:

| build strategy | precompute | sweep (14 workers) | total | fleet speedup |
|---|---|---|---|---|
| today | — | 218 s | 218 s | 1× |
| single elected builder (today's edge-counts pattern) | 5.8 s | 7.1 s | 12.9 s | **~17×** |
| sharded across the fleet | 0.4 s | 7.1 s | 7.5 s | **~29×** |

The precompute is 39,621 *independent* seeded traversals — embarrassingly parallel. **Shard it**;
the difference between 17× and 29× is worth the extra coordination.

Why no incremental update is needed: the target regime is the near-floor wall, where a stage sweeps
the whole space (minutes). A 5.8 s (or 0.4 s sharded) rebuild is ~1% overhead there. In the fast
descent regime — where stages advance every ~0.18 s and an incremental path would matter — the
bound-skip already fires on nearly every unit (threshold sits far below `base_total`), so the sweep
is already cheap and the hoist is not needed. The two regimes do not overlap.

**Bonus:** the singles block becomes free as well. `created` for a single flip *is* the precomputed
table entry, so the first 39,621 units of every stage collapse to a lookup.

## 7. Measurement caveats

- Run with `nice -n 15` alongside the live 14-worker fleet on a 16-core M4 Max, so **absolute** µs
  are inflated by contention. Both loops were measured under identical conditions in the same
  process, so the **ratios** are fair. Sweep-wide production per-unit measured 7.79 µs against the
  proposal's stated 13.5 µs — same ballpark.
- Benchmarked against a realistic near-floor threshold: swept the singles block first, which set
  `threshold = base_total + 11`, exactly as a live stage does. The bound-skip never fired
  (`skipped=0`), matching §4's note that it is unreachable near the floor.
- The hoisted loop still allocates the per-unit `Vec<WorkUnitEdge>` that production allocates, so
  the measured speedup is if anything **conservative**.

## 8. Recommendations

1. **Fix the cross-pair test** before anything else, and unit-test the shared-vertex case
   specifically — it is 1.4% of the space and fails silently.
2. **Make the direct-X/Y slow path the primary design**, not a follow-up. It is both faster and
   simpler than the retraversal fallback.
3. **Do not cap** the precompute.
4. **Shard the precompute** across the fleet (17× → 29×).
5. Gate on the existing `kernel_equiv_replay` full-stage harness, as every prior kernel change was.
6. On §5 Q5 (is per-unit optimization the right frame): at ~29× a full sweep drops from ~3.6 min to
   ~8 s fleet-wide, which makes a 500-stage wall ~1 hour instead of ~2 days. That is a large enough
   change in the cost of a wall that it is worth taking *before* revisiting the move space — it
   makes the strategic experiments cheaper to run.

# Proposal: hoist per-edge clique counts out of the pair-move inner loop

**Status:** proposal, not implemented. Seeking review before building.
**Date:** 2026-07-25
**Expected gain:** ~5–15× worker throughput in the regime the search currently lives in.
**Risk profile:** the change is *exact* (bit-identical results), so the risk is implementation bugs, not lost search quality.

This document is self-contained: it defines the problem, the current algorithm, the proposed
change with a correctness proof, the measured numbers behind the estimates, and what has already
been ruled out. No other context is required to review it.

---

## 1. What the system does

We are searching for a 2-coloring of the edges of the complete graph on 282 vertices (K₂₈₂) that
contains **no monochromatic K₈** (no 8 vertices whose 28 connecting edges are all the same color).
Finding one would prove the Ramsey bound **R(8,8) ≥ 283**, which has stood unbeaten for decades.

- **n = 282** vertices, **C(282,2) = 39,621** edges, each colored RED or BLUE.
- **k = 8**.
- **Objective** `f(G)` = number of monochromatic 8-cliques (red ones + blue ones). We minimize it.
  `f(G) = 0` would be the witness. Current best ever found: **25,758**. Current working graph:
  ~25,892.
- The coloring is kept **balanced** (equal red/blue counts): currently 19,811 red, 19,810 blue.

### Search structure

The search is a distributed local search. State advances in **stages**:

1. A stage has a fixed **base graph** `G` with known count `f(G)` (call it `base_total`).
2. A fleet of ~15 worker processes evaluates candidate **moves** from `G`. Each move is one
   **work unit**.
3. When a move improving on `base_total` is found (or the space is exhausted), the queue manager
   promotes the best result to be the next stage's base graph. Stages advance one move at a time.

### The move space (one stage)

Two move classes, enumerated as one indexed space so workers can claim disjoint index ranges:

| move class | definition | count |
|---|---|---|
| **singles** | flip one edge (either color) | 39,621 |
| **pairs** | flip one RED edge **and** one BLUE edge (balance-preserving) | 19,811 × 19,810 ≈ 392,455,910 |
| | **total work units per stage** | **≈ 392,495,531** |

Pairs dominate the space by 4 orders of magnitude, so this proposal is about pairs.

### What a worker computes per work unit

For a move that flips edge set `F`, the derived graph's count is computed **incrementally**:

```
count(G_derived) = base_total − destroyed + created
```

- **`destroyed`** = number of mono cliques of `G` that contain an edge of `F`. Read from a
  precomputed per-edge table (`edge_counts[e]` = how many mono cliques contain edge `e`), so this
  is a couple of array lookups. **Cheap.**
- **`created`** = number of mono cliques of the *derived* graph that contain an edge of `F`. This
  requires actual enumeration: a **seeded Bron–Kerbosch traversal** rooted at the flipped edge's
  endpoints. Only cliques through a flipped edge can be new, so the traversal is local, but it is
  still ~99% of all worker CPU time.

**Early abort.** Workers know the current best-known result for the stage (a "threshold"). If the
running `created` count rises high enough that the move cannot beat the threshold, the traversal
aborts and the unit is discarded. Concretely the abort limit is
`limit = threshold − 1 − base_total + destroyed`.

### The key measured asymmetry

For a *typical* pair move at the current clique level:

| quantity | value | how obtained |
|---|---|---|
| cliques **destroyed** per edge | **~18** | exact: 25,892 cliques × 28 pairs ÷ 39,621 edges |
| cliques **created** by a random pair flip | **~817** | measured (19,810 sampled flips on the live graph) |

Creating ~817 while destroying ~18 means **almost every pair move is far worse than the base
graph**, so almost every unit hits the abort limit quickly and is discarded. This is why
throughput is high per unit but the space is enormous.

### Where the time goes (measured)

- Fleet throughput: **~1.04M units/sec** (14 workers on one M4 Max, each ~93% CPU = saturated).
- Per-unit average: **~13.5 µs** (derived: 1.04M/s ÷ 14 workers).
- **One full stage sweep = 392M units ≈ 6.3 minutes.**
- When the search is "walled" (no improving move exists), it sweeps the whole space, advances to a
  least-bad move, and sweeps again. A 500-stage wall ≈ **52 hours** of sweeping.

That 6.3 min/stage is the dominant cost of the entire project.

---

## 2. The proposed change

### Intuition

Pairs are enumerated as (red edge `r`) × (blue edge `b`). Today, for every one of the ~392M
combinations, we run a fresh traversal. But the work splits into a part that depends only on `b`
and a part that depends only on `r` — **plus a correction that is almost always zero**. Precompute
the two per-edge parts once per stage (39,621 traversals), then most of the 392M units become a
few bit tests and two array lookups.

### Notation

- `R` = set of red edges, `B` = set of blue edges.
- Move: pick `r ∈ R`, `b ∈ B`; flip both. So `r` becomes blue and `b` becomes red.
- Derived graph: `R' = (R \ {r}) ∪ {b}` and `B' = (B \ {b}) ∪ {r}`.
- `created` = (# red k-cliques through `b` in `R'`) + (# blue k-cliques through `r` in `B'`).
  (No other clique can be new: a new clique must use a newly-colored edge.)

### Precompute (once per stage, on the base graph)

```
for every blue edge b:  C_b = # red  k-cliques through b in  R ∪ {b}     # pretend b is red
for every red  edge r:  D_r = # blue k-cliques through r in  B ∪ {r}     # pretend r is blue
```

That is 39,621 seeded traversals — the same primitive already used per unit today.

### The identity

Because `R' = (R ∪ {b}) \ {r}`:

```
# red k-cliques through b in R'  =  C_b − X ,
   where X = # red k-cliques through b in R ∪ {b} that ALSO contain edge r
```

and symmetrically, because `B' = (B ∪ {r}) \ {b}`:

```
# blue k-cliques through r in B' =  D_r − Y ,
   where Y = # blue k-cliques through r in B ∪ {r} that ALSO contain edge b
```

So:

```
created = (C_b − X) + (D_r − Y)
```

### When are X and Y zero? (the cheap test)

`X > 0` requires a single clique containing **both** edges `r` and `b`, all of whose internal pairs
are red (in `R ∪ {b}`). Let `r = (x,y)` and `b = (u,v)`.

- **Disjoint case** (`x,y,u,v` all distinct — the overwhelming majority): such a clique must contain
  all four vertices, so all 6 internal pairs must be red. Two are given (`xy = r` is red, `uv = b` is
  red by construction). The other four are the **cross pairs** `xu, xv, yu, yv`. Therefore:
  **if any cross pair is BLUE, then X = 0.**
- **Shared-vertex case** (e.g. `y = u`, so 3 distinct vertices): the clique must contain `{x, y, v}`;
  pairs `xy = r` and `yv = b` are red, leaving **one** cross pair `xv`. **If `xv` is BLUE, X = 0.**
- `r` and `b` cannot be the same edge (different colors).

By the identical argument in the complement, `Y = 0` unless **all** cross pairs are BLUE.

**Consequence:** since every cross pair is either red or blue, and there is always ≥1 cross pair,
`X` and `Y` can never both be non-zero. Either the cross pairs are all red (only `X` can be
non-zero), all blue (only `Y` can), or mixed (**both are zero**).

### The fast path

```
FAST PATH (cross pairs mixed):        created = C_b + D_r          exactly, no traversal
SLOW PATH (cross pairs all one color): fall back to today's seeded traversal
```

Cost on the fast path: ≤4 adjacency bit tests + 2 array lookups + a compare. Call it ~50 ns versus
~13.5 µs today.

### Estimated fast-path frequency

Red/blue density is ~50% by construction (balanced coloring). For a disjoint pair with 4 cross
pairs, assuming rough independence:

```
P(all 4 red)  ≈ (1/2)^4 = 6.25%      -> slow path (X may be non-zero)
P(all 4 blue) ≈ (1/2)^4 = 6.25%      -> slow path (Y may be non-zero)
P(mixed)      ≈ 87.5%                -> FAST PATH
```
Shared-vertex pairs (≈2.8% of pairs, only 1 cross pair) are always slow path. Net fast path
**≈ 85%**. **This is an estimate, not a measurement** — see §5, question 2. It assumes cross-pair
colors are roughly independent, which may not hold in a heavily optimized graph.

### Estimated speedup

```
today:    13.5 µs/unit
proposed: 0.85 × 0.05 µs + 0.15 × 13.5 µs ≈ 2.07 µs/unit   ->  ~6.5×
```
Upper end (~15×) requires also making the slow path cheaper — instead of a full re-traversal,
compute `X` (or `Y`) directly by seeding from the 3–4 shared vertices, which enumerates only the
cliques containing *both* edges (a far smaller set).

### Precompute cost (small, and cappable)

Each precomputed value can be **capped**: we only need the exact value when it is below the abort
limit; anything larger just means "reject". Since a typical `C_b` is ~817 and limits are ~20–40,
capping each traversal at e.g. 1024 makes the precompute cheap, and the common case aborts after a
few dozen cliques.

- Uncapped cost measured at ~124–190 µs per seeded traversal → 39,621 × ~190 µs ≈ **7.5 s/stage**
  (2% of a 377 s sweep) as a pessimistic bound.
- With capping, expected to be **well under 1 s/stage**.
- The tables are small: 39,621 × 4 bytes ≈ **158 KB**, so they can be shared between workers over
  Redis exactly as the existing per-edge count table already is (318 KB blob, 5-minute TTL).
- **Incremental option:** consecutive stages differ by exactly one edge flip (verified in
  production). The existing per-edge count table is already updated incrementally for this reason
  (measured 1,146× cheaper than rebuilding: 378 µs vs 434 ms). The same locality argument applies
  to `C_b`/`D_r`, so most entries carry over untouched.

### Pseudocode for the inner loop

```rust
// once per stage
let c_blue: Vec<u32> = precompute_C_for_each_blue_edge(base_graph, k, CAP);
let d_red:  Vec<u32> = precompute_D_for_each_red_edge (base_graph, k, CAP);

// per work unit (r, b)
let (x, y) = endpoints(r);
let (u, v) = endpoints(b);

let cross_all_red  = red(x,u) && red(x,v) && red(y,u) && red(y,v);   // shared-vertex: 1 test
let cross_all_blue = !red(x,u) && !red(x,v) && !red(y,u) && !red(y,v);

let created = if !cross_all_red && !cross_all_blue {
    c_blue[b] + d_red[r]                 // FAST PATH: exact, no traversal
} else {
    seeded_traversal_as_today(r, b)      // SLOW PATH: ~15% of units
};

let count = base_total - destroyed(r, b) + created;   // destroyed = cached lookups
```

---

## 3. Why this is safe

The change is an **algebraic identity**, not an approximation or a heuristic prune. It computes the
same `created` value the current code computes, so every work result, every threshold decision, and
every stage transition is **bit-identical**. It cannot cause the search to miss an improving move.

This distinguishes it from a rejected alternative (see §4) that was 12× faster but provably skipped
real improvements.

### Failure modes to guard

1. **Shared-vertex case handled as if disjoint.** Testing 4 cross pairs when only 1 exists (or
   indexing a non-existent vertex pair) would silently corrupt counts. Needs explicit tests.
2. **Cap truncation.** If a capped `C_b` is used as an exact value when the true value was needed
   (i.e. below the limit), counts are wrong. Cap must be provably above any usable limit, or the
   code must treat "capped" as a distinct "reject" state rather than a number.
3. **Stale precompute.** `C_b`/`D_r` are properties of the base graph; if a stage advances and the
   tables are not rebuilt/updated, every unit is wrong. (There is precedent: a cache in this code
   was being wiped on every stage advance, silently defeating cross-stage reuse for a long time.)
4. **Density assumption wrong** → fast-path fraction far below 85% → little gain. This is a
   performance risk, not a correctness risk, and is cheap to measure first (§5).

### Validation plan (mirrors what the codebase already does)

- **Unit tests** on small graphs (n≈10, k=4/5) asserting `created` from the hoisted path equals a
  brute-force recount, over both colors, disjoint and shared-vertex cases, and capped/uncapped.
- **Production-scale equivalence harness**: for a real 282-vertex base graph, iterate a large
  random sample of `(r,b)` pairs and assert hoisted `created` == today's `created`, exactly.
- **Full-stage replay**: the repo has a harness that replays an entire real stage and requires
  byte-identical output (same results, same hashes). Any kernel change must pass it. This is the
  gate that has validated previous kernel work.
- **Offline prototype before production code:** measure the true fast-path fraction and the real
  per-unit costs on the live graph, and only proceed if it clears a threshold (say ≥3×).

---

## 4. What has already been ruled out (measured — please don't re-propose)

Two similar-sounding ideas were tested and killed this week. Reviewers should know why.

| idea | result | why it failed |
|---|---|---|
| **Dirty-edge pruning** ("only re-check moves near the last flip") | **12× faster but UNSOUND** | Sound only if the previous stage swept the *whole* space. In practice stages stop as soon as they find an improvement, so ~95% of the space was never examined and cannot be assumed non-improving. Replayed against 38 real consecutive stage transitions: the move the search actually adopted lay inside the "dirty" set only **57.9%** of the time (**51.7%** for improving moves). It would silently skip ~half the search's progress. |
| **Red-edge sharing** (hoist only the `r`-dependent half) | superseded | The first benchmark said "1.00× ceiling", but **that benchmark was buggy** — it used a traversal primitive with no early abort, so its abort simulation never aborted. The corrected analysis is what produced *this* proposal: both halves can be hoisted, not just one, and the validity condition is the cheap cross-pair test. |
| **Bound-skip** (skip units whose cached bound already excludes them) | implemented, **no measurable gain** (1,036,833 vs 1,041,750 u/s) | Near the floor the threshold sits at/above `base_total`, making the skip condition unreachable. Kept because it is correct and free. |
| Faster QM polling / worker polling / longer settle windows | all measured, now ≤0.1% of a stage | Stage *overhead* was already reduced ~10× by other work (see §6); the remaining cost is the sweep itself. |

Other things already done, so they are not the answer here: the per-edge count table is built
counts-only and updated incrementally (1,146×), it is shared between workers over Redis, and stage
adoption is driven by a pub/sub-armed settle timer rather than blind polling.

---

## 5. Questions for reviewers

1. **Is the identity correct?** Specifically `created = (C_b − X) + (D_r − Y)` and the claim that
   `X > 0` requires all cross pairs red (and `Y > 0` requires all cross pairs blue), including the
   shared-vertex case and the fact that `X`, `Y` cannot both be non-zero. Any counterexample?
2. **Is the ~85% fast-path estimate plausible?** It assumes cross-pair colors are roughly
   independent at ~50% density. But this graph is *heavily optimized* to avoid mono cliques, and
   pairs are enumerated in order of clique participation (highest first), which may correlate
   cross-pair colors and depress the fast-path fraction — possibly badly, in the earliest and most
   important part of the sweep. How would you measure this cheaply and honestly?
3. **Precompute strategy:** rebuild `C_b`/`D_r` per stage (capped, ~1 s) and share via Redis, or
   update incrementally from the previous stage (one flip apart) reusing existing delta machinery?
   The incremental path is faster but adds a second consistency invariant to get wrong.
4. **Slow path:** is it worth computing `X`/`Y` directly (seed from the 3–4 shared vertices,
   enumerating only cliques containing both edges) instead of falling back to a full seeded
   traversal? That is what would take this from ~6× to ~15×.
5. **Is there something better we are missing?** The fundamental situation: ~392M candidate moves
   per stage, of which essentially all are rejected because a random flip creates ~45× more cliques
   than it destroys, and the search advances one move at a time. Given that, is per-unit
   optimization even the right frame, or should the *move space itself* be reconsidered (sampling,
   a different neighborhood, or a different acceptance rule)? Note that changing the space changes
   search semantics, not just speed, so it is a strategy decision rather than an optimization.

---

## 6. Context: recent measured work (so reviewers can calibrate)

For scale, the same codebase recently had these changes measured and shipped:

- **Counts-only build**: stop materializing the clique list and edge→clique index that the hot path
  never reads (~300 MB of allocation per stage per worker removed). 1.29× on the build itself.
- **Incremental count update**: derive the per-edge table from the previous stage's (one flip apart)
  instead of rebuilding — **378 µs vs 434 ms = 1,146×**, bit-identical (total and all 79,524 entries).
  This removed a ~430 ms serialized head from every stage.
- **Redis sharing + builder election** so 15 workers don't each rebuild the same table.
- **Pub/sub-armed settle timer** replacing blind polling for stage adoption; swept the window and
  measured 100 ms best (332 stages/min, 150k cliques/min) vs 500 ms (120 stages/min, 43k
  cliques/min).

Net effect on a post-perturbation descent: stage gaps fell from ~1.00 s to ~0.18 s, and the working
graph descended from 2.67M mono-cliques to ~25.9k. The remaining cost is the near-floor sweep this
proposal targets.

**A caution for reviewers, learned the hard way twice this week:** two confident estimates in this
area (a "2× kernel win" and a "20× pruning win") both collapsed when measured — one because the
benchmark silently didn't abort, one because its soundness precondition didn't hold in production.
Please treat the numbers in §2 marked *estimate* with suspicion, and prefer a measured prototype
over an argued one.

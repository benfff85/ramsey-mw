# 2-Vertex Joint Re-Optimization — Investigation

**Date:** 2026-06-13
**Author:** Ben Ferenchak + Claude
**Status: NO HIT on graph 8348 (campaign 2 best, 775,642) — robustly firmed.** A validated 2-vertex joint optimizer found zero improvement across **214 vertex pairs**: 14 targeted (6 lowest-coupling/"marginal", 8 highest-coupling) plus a 200-pair uniform-random firming sweep. Rule of three puts the improvable-pair fraction under ~1.5%. This is the next rung above the row-lock result — **strong evidence (a large sample, though not the exhaustive certainty of the row sweep)** that small-window local moves do not break this wall. Next: strategic reset / diversification (multi-seed).

**Related documents:**
- `row-optimization-investigation.md` — the 1-vertex window; resolved row-locked. This is the 2-vertex window.
- `sat-solver-investigation.md` — windowed/local MaxSAT (Tier 2); the k-vertex generalization, the natural escalation from here.
- `engine-optimization-review.md` §7 — strategic layer.
- Tools/data: `single-flip-check/src/bin/joint_pilot.rs`, `pair_coupling.rs`, `clique_census.rs`; logs in `/tmp/joint_*.log`.

---

## What the joint move is

Re-optimize TWO vertices' rows (and the edge between them) simultaneously. Against the fixed graph G−{u,v}, every mono 8-clique through u or v decomposes into:
- **single-star** (through one vertex): that vertex + a mono 7-clique of G−{u,v}, fully colored by its row — one clause per mono 7-clique, over u's row block (`a`) and over v's row block (`b`). Width 7.
- **joint** (through both): {u,v} + a mono 6-clique S of G−{u,v}, with the u–v edge `e` and u,v's 6+6 connections to S all the clause color — one clause per mono 6-clique. Width 13.

f = violated clauses = cliques through u **or** v; total = const + f (exact). The edge `e` (in every joint clause) is handled by a 2-iteration outer loop (e=red, e=blue): with e fixed, only that color's joint clauses can fire, and e is a constant-satisfied literal — so the inner problem is a variable-width weighted-MaxSAT over the 2·(n−2) row bits, solved by the same incremental-counter + SATLike engine validated for row-opt.

This reaches **coordinated cross-star rewrites** expressible by neither the production engine nor single-vertex row-opt: it can move both rows together through configurations where any single-row change worsens the count but the joint change improves it.

## Tooling and validation (all PASS)

`joint_pilot.rs` (one-off crate, no repo/system interaction). Feasibility from `clique_census.rs`: graph 8348 has 8.88M mono 7-cliques and **25.5M mono 6-cliques** → a per-pair DB of ~8.4M single-star + ~24.4M joint ≈ 29M clauses, ~2.3 s build. Validation:
- **Anchor** (n=11, k=4): brute-forced the entire joint assignment space; search reaches the true optimum (all 4 cliques through the pair eliminated), spliced recount exact.
- **Counters at 282v** (pair 159,179): f(incumbent) = 41,813 = independent through-pair clique enumeration; **40 random assignments every one matched a from-scratch brute clique count**. The 29M-clause incremental machinery is provably correct.

## Results — 0 improvements across 14 pairs

Seed strategy 1, **marginal pairs** (the 4 vertices where row-opt's best single flip was only +2: 159, 179, 187, 29 → all 6 pairs), 180 s/edge-color:

| pair | through-both (coupling) | f₀ | best delta |
|---|---|---|---|
| all 6 pairs | ~200 (LOW — below median 534) | 41.8–43.8K | **0** |

Seed strategy 2, **highest-coupling pairs** (most shared 8-cliques, via `pair_coupling.rs`), 180 s/edge-color:

| pair | shared cliques | f₀ | best delta |
|---|---|---|---|
| (25,111) | 1385 | 44,294 | 0 |
| (171,185) | 1353 | 44,296 | 0 |
| (49,110) | 1335 | 43,212 | 0 |
| (203,253) | 1316 | 42,509 | 0 |
| (162,216) | 1313 | 42,890 | 0 |
| (51,205) | 1308 | 42,555 | 0 |
| (47,220) | 1302 | 42,399 | 0 |
| (125,227) | 1298 | 43,868 | 0 |

**Total: 0 / 14 pairs improved.** Every pair, both edge colors, ~183–189K SATLike moves: nothing below the incumbent. Two structural observations:
- **Greedy from the incumbent made 0 improving steps** on the incumbent edge color for all 14 pairs — not even a single coordinated bit-flip improves.
- **Flipping the edge always worsened** the count (start_f rose), and greedy from there never recovered below the incumbent. The two stars are jointly at a strict local optimum at every tested pair.

### Firming sweep — 200 random pairs (2026-06-13)

The 14 targeted pairs bracket the coupling extremes (low/marginal and highest); to cover the broad middle and firm the negative, a uniform-random sample of **200 distinct pairs** (4 parallel shards, 60 s/edge-color, `joint_pilot sample` mode) was run. Result: **0 / 200 improved**, no nonzero deltas anywhere, sampled incumbent objectives spanning start_f 41,765–46,255 (a wide coupling spread). Combined total: **0 of 214 pairs**.

Rule of three: 0 events in 200 random trials → 95% upper bound ≈ 3/200 = **1.5%** of pairs improvable. At most ~600 of 39,621 pairs could improve, and those would have to be neither the highest-coupling, nor the marginal, nor anywhere in the random middle — a shrinking and implausible hiding spot. The 2-vertex-joint move is **robustly locked** on graph 8348.

## Honest scope

This is still a **sample, not an exhaustive sweep** (214 of 39,621 pairs; the row sweep was exhaustive over all 282 vertices). It is not a proof that *no* pair improves. But three independent seed strategies — closest-to-improving-as-singles, highest-coordination, and uniform-random — all came up empty with the same strict-local-optimum signature (greedy makes 0 improving steps; the edge flip only worsens). A full pair sweep is impractical by hand (~39.6K pairs × ~minutes ≈ weeks serial). The negative is now firm enough to act on.

## Verdict and recommendation

Across graph 8348 every efficiently-searchable local move shape is now empty: all single flips (engine + census), all within-star pairs and all-depth single-row rewrites (exhaustive row sweep), and 2-vertex joint moves across 214 pairs (targeted + random). The 775,642 wall is exceptionally robust to local search — consistent with SA, tabu, and VDS having walled out earlier on this lineage.

Two forward paths:
1. **k-vertex windowed MaxSAT** (`sat-solver-investigation.md` Tier 2) — the documented next rung: exactly/near-exactly solve windows of 3+ vertices. Qualitatively stronger than local search, but each rung costs more (window selection over C(282,k); larger per-window solves) and the prior two rungs (1- and 2-vertex) found nothing — diminishing-returns bet.
2. **Strategic reset / diversification** — accept 775,642 as a deep local floor for the Paley(281)+1 seed and diversify: multiple independent descents from different seeds, aggressive restarts, or a structurally different construction. The accumulated negative evidence (every local method walls out here) now favors this read.

The validated `joint_pilot` tooling is reusable on future best graphs (re-run `pair_coupling` + `joint_pilot` when a new wall forms). Do not build production integration of the joint move without a hit first.

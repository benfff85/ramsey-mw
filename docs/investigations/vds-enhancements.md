# Variable-Depth Search (VDS) Enhancement Plan

**Date:** 2026-05-02
**Author:** Ben Ferenchak + Claude
**Status:** Active — VDS implemented and operational; enhancement phases identified to extend reach

**Prior investigations referenced:**
- `deep-analysis-path-forward.md` — VDS as the highest-leverage algorithmic upgrade
- `simulated-annealing-investigation.md` — incremental evaluation lessons that apply directly to VDS

---

## Goal

Extend VDS to consistently find multi-edge improvements that the exhaustive 2-flip search cannot reach. VDS is the closest analog to Lin-Kernighan for this problem; reaching its theoretical capability requires three phases of refinement beyond the initial implementation.

---

## Diagnosis of the Initial Implementation

The baseline VDS run (from `vds.rs::search_depth`) had three issues that limited its effectiveness.

### 1. Static Candidate Set at Depths 2-4 (critical)

`search_depth` calls `rank_edges_by_participation(working_graph, clique_collection, branching_factor)` at every recursive level. Because `clique_collection` is the **original** (not updated for prior flips in the current sequence), this returns the **same top-K edges every time**. The only variation is filtering edges already in the current sequence.

**Impact:** The search explores combinations of ~220 distinct edges total (200 shuffled first-edges + the same 20 at depth ≥ 2). This is a narrow search that misses the core LK insight: each subsequent flip should be a *compensating move* local to the disruption from the previous flip.

### 2. Permissive Pruning (high impact)

A `worsening_tolerance` of 50,000 means no subtree is ever pruned when basin widths are on the order of tens. Every candidate at every depth is evaluated regardless of cumulative delta.

**Impact:** Large portions of the search tree are wasted on doomed paths. With reasonable tolerance (~500–1,000), entire branches get cut, reallocating budget to deeper or wider productive search.

### 3. Redundant Ranking Computation (performance)

`rank_edges_by_participation` is invoked tens of thousands of times per VDS run with identical inputs. Each call iterates all 39,621 edges, scores them, sorts the result, and takes the top-K. The result is the same every call.

**Impact:** Significant fraction of runtime wasted on redundant computation.

---

## Phase 1: Quick Wins (no algorithmic change)

### 1a. Cache the ranking — compute once per run

Replace the per-call `rank_edges_by_participation` inside `search_depth` with a pre-computed list passed from `run_vds`. The root call already computes the top-K; pass that list (and a separate top-`branching_factor` list for deeper levels) into the recursion.

### 1b. Tighten worsening_tolerance to a basin-scale value

For a problem oscillating in basins of ~40 cliques, `worsening_tolerance ≈ 500` enables real pruning while still allowing exploration. Any path that worsens by more than ~10× the basin gets cut.

**Expected result:** same search quality (the ranking was already static), 30–40% faster, real pruning data to inform Phase 2 tuning.

---

## Phase 2: Locality-Aware Candidate Selection (the real fix)

At depth ≥ 2, instead of selecting the global top-K by participation, select candidates based on **locality to previously flipped edges**:

1. Collect all edges that share a vertex with any previously flipped edge in the current sequence
2. Among those, rank by participation in **surviving** cliques (original count minus destroyed set)
3. Take top-K from this local neighborhood

This is the core LK principle: each step in the chain is a compensating move that targets the disruption caused by the previous step. With degree ~141 at 282 vertices, each flipped edge touches ~280 other edges via its two endpoints, so the local neighborhood is substantial.

**Expected result:** much more diverse search paths. Each first-edge fans out into a different local neighborhood at depth 2, instead of all converging on the same global top-K.

The current implementation in `vds.rs::get_neighborhood_candidates` already does locality-aware selection at depth 3+ (see worker doc). Phase 2 extends this principle to depths 2 and beyond and ensures the participation ranking accounts for the destroyed-set adjustment.

---

## Phase 3: Deeper Search with Pruning

With locality-aware selection and real pruning:

- Increase `max_depth` from 4 to 8–12
- Reduce `top_first_edges` to 50–100
- Reduce `branching_factor` to 10–15
- The locality constraint naturally limits the tree, so deeper search becomes tractable

This is where VDS's real power lies — depth 4 is shallow for a problem stuck at a 2-flip local minimum. LK for TSP routinely uses depth 10–20. The Ramsey landscape has enough structure to benefit from comparable depths.

---

## Appendix: Runtime Budget at Baseline

At `max_depth=4`, `top_first_edges=200`, `branching_factor=20` (early profile):

| Component | Calls | Cost per call | Total |
|-----------|-------|---------------|-------|
| `rank_edges_by_participation` | ~84,000 | ~2ms (39K edges scored + sort) | ~168s |
| `compute_delta` (BK calls) | ~1.4M | ~0.4ms | ~570s |
| destroyed-set management | ~1.4M | ~7µs | ~10s |
| **Total observed** | | | **~790s** |

After caching the ranking: ~84,000 calls eliminated, saving ~21% of runtime.
After tightening tolerance: even 50% pruning of depth-4 saves ~285s.
After Phase 2 + 3 combined: depth 8 should be reachable in roughly the same wall-clock budget.

---

*Generated for the Ramsey project — May 2026*

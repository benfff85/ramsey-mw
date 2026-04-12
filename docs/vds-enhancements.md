# VDS (Variable-Depth Search) Enhancement Plan

**Date:** 2026-04-12
**Author:** Ben Ferenchak + Claude
**Status:** Active — first VDS run completed, identified design issues, implementing fixes

**Prior investigations referenced:**
- `deep-analysis-path-forward.md` — VDS proposed as Recommendation #1 (Lin-Kernighan style)
- `simulated-annealing-investigation.md` — SA shut down; incremental evaluation lessons apply here

---

## Current VDS Results

First production run on stage 4982 (base CC=980,088):
- **790 seconds** (~13 minutes) per run
- **1,426,900 nodes visited**
- **0 branches pruned** (worsening_tolerance=50,000 is too generous)
- **No improvement found**
- Parameters: max_depth=4, top_first_edges=200, branching_factor=20

## System Context

The exhaustive search has broken through the 980,299 wall documented in `deep-analysis-path-forward.md`:
- **All-time best: 980,071** (stage 4968, 2026-04-11)
- Oscillating 980,071-980,089 for 14 stages (~28 hours) since
- The big drop from ~980,170 to ~980,071 happened around stages 4962-4968

---

## Identified Issues

### 1. Static Candidate Set at Depths 2-4 (Critical)

`search_depth` (vds.rs:268) calls `rank_edges_by_participation(working_graph, clique_collection, branching_factor)` at every recursive level. Because `clique_collection` is the **original** (not updated for prior flips), this returns the **same top-20 edges every time**. The only variation is filtering edges already in the current sequence (line 272).

**Impact:** The search explores combinations of ~220 distinct edges total (200 shuffled first-edges + the same 20 at depth 2-4). This is a very narrow search that misses the core LK insight: each subsequent flip should be a *compensating move* local to the disruption from the previous flip.

### 2. Zero Pruning (High Impact)

`worsening_tolerance=50,000` means no subtree is ever pruned. The entire oscillation basin is ~40 cliques wide, so a tolerance of 50K is 1,250x too generous. Every candidate at every depth is evaluated regardless of how badly the cumulative delta looks.

**Impact:** 1.4M nodes visited with 0 pruned. With reasonable tolerance (~500-1000), large portions of the search tree would be cut, allowing either faster completion or reallocation of that budget to deeper/wider search.

### 3. Redundant Ranking Computation (Performance)

`rank_edges_by_participation` is called ~84,000 times per VDS run (1 root + 200 at depth 2 + 4,000 at depth 3 + 80,000 at depth 4). Each call iterates all 41,328 edges, scores them, sorts the Vec, and takes top-K. Since the clique_collection doesn't change, every call returns the same result.

**Impact:** ~30% of runtime wasted on redundant computation.

---

## Implementation Plan

### Phase 1: Quick Wins (No Algorithmic Change)

**1a. Cache the ranking — compute once per run, not per recursive call**

Replace the per-call `rank_edges_by_participation` inside `search_depth` with a pre-computed list passed from `run_vds`. The root call already computes the top-200; pass a separate top-20 (or top-`branching_factor`) list for deeper levels.

**1b. Tighten worsening_tolerance to 500**

Change from 50,000 to 500. This enables real pruning — any path that worsens by more than 500 cliques (well beyond the observed basin width of ~40) gets cut. This alone should reduce nodes_visited significantly and allows increasing depth or branching.

**Expected result:** Same search quality (the ranking was already static), 30-40% faster, real pruning data to inform Phase 2 tuning.

### Phase 2: Locality-Aware Candidate Selection (The Real Fix)

At depth 2+, instead of selecting the global top-20 by participation, select candidates based on **locality to previously flipped edges**:

1. Collect all edges that share a vertex with any previously flipped edge
2. Among those, rank by participation in *surviving* cliques (original count minus destroyed set)
3. Take top-K from this local neighborhood

This is the core LK principle: each step in the chain is a compensating move that targets the disruption caused by the previous step. For a 288-vertex graph with degree ~143, each flipped edge touches ~287 other edges per vertex endpoint, so the local neighborhood is substantial.

**Expected result:** Much more diverse search paths. Each first-edge fans out into a different local neighborhood at depth 2, rather than all converging on the same global top-20.

### Phase 3: Deeper Search with Pruning

With locality-aware selection and real pruning:
- Increase max_depth from 4 to 8-12
- Reduce top_first_edges to 50-100
- Reduce branching_factor to 10-15
- The locality constraint naturally limits the tree, so deeper search becomes tractable

This is where VDS's real power lies — depth 4 is shallow for a problem stuck at a 2-flip local minimum. LK for TSP routinely uses depth 10-20.

---

## Appendix: Runtime Budget Analysis

At max_depth=4, top_first_edges=200, branching_factor=20:

| Component | Calls | Cost per call | Total |
|-----------|-------|---------------|-------|
| rank_edges_by_participation | ~84,000 | ~2ms (41K edges scored + sort) | ~168s |
| compute_delta (BK calls) | ~1,426,900 | ~0.4ms | ~570s |
| destroyed set management | ~1,426,900 | ~7us | ~10s |
| **Total observed** | | | **790s** |

After caching the ranking: ~84,000 calls eliminated, saving ~168s (~21% of runtime).
After tightening tolerance: depends on pruning ratio, but even 50% pruning of depth-4 saves ~285s.

---

*Generated for the Ramsey project — April 2026*

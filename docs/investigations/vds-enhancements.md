# Variable-Depth Search (VDS) Enhancement Plan

**Date:** 2026-05-02 (updated 2026-05-03)
**Author:** Ben Ferenchak + Claude
**Status: RETIRED 2026-05-03.** VDS at full Phase 3 settings produced zero improvements across 51,994 attempts in 3 hours. Decision: do not reactivate without new structural evidence. Details below.

---

## Final Result (2026-05-03 ~15:30 UTC, ~3 hours after deploy)

| Metric | VDS (8 workers) | Exhaustive (6 workers) |
|--------|------------------|------------------------|
| Total work | **51,994 VDS attempts** | **736,398 top-50 adds** |
| Improvements / contributions | **0** improvements, **0 top-50 adds** | All 8 stage advances |
| Per-worker rate | ~6,500 attempts/3h, 0 success | ~123,000 top-50 adds/3h |

**Stage progression during the experiment:**
- 12:33:51 → stage 7121 (791,081 cliques)
- 12:34:51 → stage 7122 (791,077, drop of 4)
- 12:55:51 → stage 7123 (791,016, drop of 61)
- 13:25:51 → stage 7124 (790,974, drop of 42)
- 14:16:21 → stage 7125 (790,961, drop of 13)
- 15:04:21 → stage 7126 (790,957, drop of 4)
- 15:09:21 → stage 7127 (790,955, drop of 2)
- 15:27:21 → stage 7128 (790,924, drop of 31)

Total improvement during the experiment: **791,100 → 790,924, drop of 176 cliques over 3 hours**, all attributable to the exhaustive fleet.

### Per-worker comparison

VDS worker per-attempt cost was actually fine — ~6,500 attempts per worker over 3 hours = ~36 attempts/min/worker. The problem isn't throughput; it's that the search produced no improvements at any depth.

Each VDS run logged `nodes_visited` averaging 700–1,000 with `branches_pruned` 80–90% of those. The tighter `worsening_tolerance=500` was cutting the search tree very aggressively — but loosening it was the planned fallback if zero improvements happened, and we never even needed to run that fallback because the result was already conclusive.

### What this rules out

- **Phase 3 settings.** Max depth 12, top_first_edges 80, branching_factor 12, tolerance 500 — the literature-recommended VDS configuration produced zero improvements at our scale.
- **The "Phase 2 was shipped but never seriously run" hypothesis.** It HAS now been seriously run. 51,994 attempts is not a sample-size problem.
- **The "VDS just needs more workers" hypothesis.** 8 workers with 0 contributions vs. 6 workers driving all 8 stage advances tells you the algorithm isn't competitive even at modest scale.

### What this does NOT rule out (but is unlikely to matter)

- Looser `worsening_tolerance` (1,500 or 3,000) might let chains traverse deeper basins. We did not run this fallback because the primary Phase 3 result was already decisive — but it's the one knob we didn't actually try.
- Different start_depth, larger top_first_edges, or longer chains might find moves that current settings miss. Possible in theory; no literature evidence it would work for Ramsey-shaped problems.

### Why VDS is structurally a poor fit at our operating point

The Ramsey landscape at 282 vertices and ~791K cliques does not appear to have the chain-of-compensating-moves structure that Lin-Kernighan exploits in TSP. Improvements at our scale come from finding *different basins*, not from deeper traversals within the basin we're in. VDS is designed for the latter.

This matches the broader Ramsey heuristic-search literature, which is dominated by tabu + clique-guided approaches (Exoo, Pullan, others) rather than depth-extended local search. Lin-Kernighan-style search for Ramsey numbers has no notable published track record.

---

## Action Taken

- Compose change pending: revert `ramsey-worker-rust-vds` to `scale: 0` and `ramsey-worker-rust` (exhaustive) back to 14.
- VDS code remains in the repo but is hereby permanently mothballed at the application level. **Do not recommend reactivating without new structural evidence** (e.g., a different problem regime, a fundamentally different algorithm dressed in the VDS chassis, or a published result demonstrating VDS effectiveness on a comparable Ramsey search).

---

---

## Current Experiment (deployed 2026-05-03 ~12:25 UTC)

Phase 1a (cache global ranking) and Phase 2 (locality-aware candidate selection at depth 2+) were already shipped in code. The Phase 1b/3 *settings* had never been applied in production — `ramsey-worker-rust-vds` had been running at `scale: 0`. We are now running the full Phase 3 settings with 8 workers as the final fair evaluation before deciding VDS's fate.

### Config now live in production

| Knob | Prior value | Current value | Phase 3 recommendation |
|------|-------------|---------------|------------------------|
| `VDS_MAX_DEPTH` | 8 | **12** | 8–12 |
| `VDS_TOP_FIRST_EDGES` | 200 | **80** | 50–100 |
| `VDS_BRANCHING_FACTOR` | 15 | **12** | 10–15 |
| `VDS_WORSENING_TOLERANCE` | 1000 | **500** | ~500 |
| `VDS_START_DEPTH` | 3 | 3 | unchanged |

### Fleet composition

- `ramsey-worker-rust-vds`: scale 0 → **8**
- `ramsey-worker-rust` (exhaustive): scale 14 → **6**
- Total compute unchanged (14 workers split 8 VDS + 6 exhaustive for A/B comparison)

### Initial observations (~6 min after deploy)

- All 8 VDS workers healthy, no errors across the fleet
- Each worker processes ~1 VDS attempt every 1.5–1.8s
- Per-attempt cost: `nodes_visited` ~700–1000, `branches_pruned` ~80–90% of those (pruning very active)
- **Zero VDS-attributed improvements yet** — too early to draw conclusions
- Exhaustive fleet (now 6 workers) still healthy; producing top-50 results for stage 7120 normally

### Concern worth tracking

`nodes_visited` averaging 700–1000 per run despite `max_depth=12` suggests the tighter `worsening_tolerance=500` may be cutting the search tree very aggressively. If after 24 hours there are zero VDS-attributed improvements but the exhaustive fleet kept producing them at its usual rate, the most likely explanation is that pruning is too tight to allow VDS to find the multi-edge chains it's designed to find. Before fully retiring VDS, worth one fallback experiment: `VDS_WORSENING_TOLERANCE=1500` for another 24 hours.

---

## 24-Hour Decision Checklist (target: 2026-05-04 ~12:30 UTC)

Before deciding VDS's fate, gather the following from MySQL and worker logs:

### From MySQL (campaign 2)

```sql
-- Stage advances since experiment start
SELECT stage_id, base_graph_id, g.clique_count, s.created_date
FROM stage s JOIN graph g ON s.base_graph_id = g.graph_id
WHERE s.campaign_id = 2 AND s.created_date >= '2026-05-03 12:25:00'
ORDER BY s.stage_id;
```

Compute:
- **Stage advance rate**: stages/hour over the 24-hour window
- **Clique-count drop rate**: total cliques eliminated / 24h
- Compare to baseline (the ~10–60 min between advances and 1–80 cliques/stage observed before the experiment)

### From Redis / worker logs

```bash
# Total VDS attempts and improvements found across all 8 workers
for c in $(docker ps --filter "name=ramsey-worker-rust-vds" --format "{{.Names}}"); do
  echo "=== $c ==="
  total=$(docker logs --since 24h "$c" 2>&1 | grep -c "VDS finished")
  improved=$(docker logs --since 24h "$c" 2>&1 | grep -c "VDS finished: improvement found")
  echo "attempts: $total, improvements: $improved"
done
```

Also check `Added to top-50 results` lines — these are the actual contributions to the top-N sorted set, regardless of which worker mode found them.

### Per-worker improvement attribution

Each VDS worker logs its own improvements. Compare:
- Improvements attributed to VDS workers (sum across the 8 VDS containers)
- Improvements attributed to exhaustive workers (sum across the 6 exhaustive containers)
- Per-worker improvement rate (improvements / running-hours)

If VDS per-worker improvement rate ≥ exhaustive per-worker rate, VDS is competitive and should scale up. If significantly below, VDS is wasting compute.

### Decision matrix

| Outcome | Action |
|---------|--------|
| VDS per-worker improvement rate ≥ exhaustive | Scale VDS up (e.g., 8 → 12), reduce exhaustive further |
| VDS rate is 50–100% of exhaustive | Hold current 8/6 split, run another week to confirm |
| VDS rate is 10–50% of exhaustive | Try fallback (`VDS_WORSENING_TOLERANCE=1500`) for 24h before retiring |
| VDS rate < 10% of exhaustive, or zero improvements at all | Retire VDS — set `scale: 0`, reallocate the 8 worker slots back to exhaustive |
| `nodes_visited` is ≤ 500 average and zero improvements | Pruning too aggressive — try fallback above before retiring |

### Side-channel: are basin sizes consistent with our tolerance?

If the experiment fails entirely, also worth logging the `worsening_tolerance=500` against actual observed basin widths in `work_result` deltas. If real basin widths are ≫ 500, the pruning fundamentally can't allow VDS chains to traverse them.

---

## After This Experiment

- **If VDS works:** Update `vds-enhancements.md` with confirmed settings; update `may-2026-next-steps.md` to demote tabu+clique-guided slightly (still build it, but VDS becomes the higher-priority allocation).
- **If VDS doesn't work:** Add a note here explicitly stating it has been retired with the data backing the decision, then never recommend reactivating it without new structural evidence.

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

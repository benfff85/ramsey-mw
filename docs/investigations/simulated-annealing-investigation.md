# Simulated Annealing — Design Requirements and Lessons Learned

**Date:** 2026-05-02
**Author:** Ben Ferenchak + Claude
**Status:** Deferred — SA worker exists but is currently disabled. Documented requirements for any future re-enablement.

**Prior investigations referenced:**
- `deep-analysis-path-forward.md` — strategic context
- `sat-solver-investigation.md` — clique-guided mutation as a related Tier 1 idea

---

## Why SA Is Interesting for This Problem

The exhaustive 2-flip search is complete within the 2-flip neighborhood but cannot, by definition, escape 2-flip local minima. Simulated annealing accepts probabilistic worsening moves and can — in principle — traverse between basins of attraction.

For the 282-vertex search aiming at R(8,8) ≥ 283, SA is a natural candidate to escape the 2-flip wall when the exhaustive engine eventually plateaus. The Ramsey-number literature historically relies heavily on SA-type heuristics for exactly this reason (Exoo, Radziszowski, others).

---

## Implementation Requirements

A useful SA worker must satisfy three constraints. The first SA implementation in this codebase failed at all three; any re-enablement must address them.

### 1. Incremental clique evaluation

Each iteration must compute the delta from the existing `CliqueCollection`:

```rust
let broken = clique_collection.get_count_of_cliques_containing_edges(&edges_to_flip);
graph.flip_edges(&edges_to_flip);
let (new, _) = get_new_cliques_with_limit(&graph, clique_size, &edges_to_flip, i32::MAX);
let new_clique_count = current_clique_count - broken + new;
```

This is roughly **1000× faster per iteration** than calling `get_cliques_comprehensive()` (full Bron-Kerbosch). Without this, total iterations are bounded in the thousands; with it, millions are practical within a few hours.

**Subtlety: stale collections.** After accepting a move, the `CliqueCollection` reflects the previous graph state. Three options:

| Option | Cost | Accuracy |
|--------|------|----------|
| Rebuild collection on every accept | Expensive | Exact |
| Use stale collection as approximation | Cheap | Approximate; broken-count drifts |
| Periodic rebuild (every N accepts) | Compromise | Bounded drift |

Option 3 is the pragmatic choice. The seeded Bron-Kerbosch for **new** cliques is always exact (it operates on the live graph); only the **broken** count drifts as the collection ages.

### 2. Scale-appropriate temperature schedule

Temperature must match the basin scale of the actual search. At ~792K cliques the observed oscillation basin width is ~40 cliques. A starting temperature of 1000 — 25× the basin depth — accepts essentially random moves and produces a meaningless random walk. Recommended:

- **Starting temperature: ~50** (matching observed basin width, slightly larger to enable basin escape)
- **Cooling rate: ~0.999** geometric
- **Reheat cycles:** cool to ~5, reheat to 50, repeat. Avoids permanent commitment to a single basin.

### 3. Perturbation size matched to escape requirements

A 2-edge-pair flip is the same neighborhood the exhaustive search has already explored. SA needs **larger perturbations** to do useful work:

- Recommended: 5–20 edge perturbations per iteration (not 2–4)
- Edge selection: weighted by inverse clique participation (low-participation edges are less disruptive to break)
- Alternative: clique-guided selection — pick a random surviving clique, flip one of its 28 edges (this is the WalkSAT-inspired variant from `sat-solver-investigation.md`)

---

## Quantified Comparison

| Configuration | Iterations | Time per iter | Total time | Effective neighborhood |
|---------------|------------|---------------|------------|------------------------|
| Naive (full recount, T=1000, 4 edges) | 15,000 | ~5s | ~20 hours | Same as 2-flip exhaustive |
| Required (incremental, T=50, 10 edges) | 10,000,000 | ~5ms | ~14 hours | Far beyond 2-flip exhaustive |

The correct configuration has roughly the **same wall-clock budget** but covers **~700× more iterations** through a **vastly larger neighborhood**.

---

## Past Attempt — What Failed and Why

A previous SA implementation (`sa.rs`) ran for 45,000+ iterations across multiple parameter configurations and found zero improvements. Root causes:

1. **Full `get_cliques_comprehensive()` per iteration** — limited iterations to thousands instead of millions
2. **Initial temperature of 1000** vs basin width of ~40 — accepted essentially random worsening moves throughout the schedule
3. **2-edge-pair perturbations** — same neighborhood as the exhaustive search that had already proven the basin a 2-flip local minimum

The infrastructure remains in place; only the algorithmic configuration was wrong.

---

## Recommended Restart Path

1. **Wire incremental evaluation** into `sa.rs` (replace `get_cliques_comprehensive` call with the broken/new delta formula above).
2. **Set initial temperature to 50**, cooling rate 0.999, with reheat cycles at temp=5.
3. **Set perturbation size to 5–20** edges per iteration, mixed red/blue.
4. **Use clique-guided selection**: randomly pick a surviving 8-clique, flip one of its edges, complete the perturbation by picking opposing-color edges with low participation.
5. **Periodic CliqueCollection rebuild** every 1,000 accepts to bound drift in broken-count estimates.

This is a **2-day rewrite**, not a new feature. All necessary infrastructure (`CliqueCollection`, seeded BK, edge-flip primitives) already exists.

---

## Comparison to Other Worker Types

| Dimension | Exhaustive | SA (corrected) | VDS |
|-----------|-----------|----------------|-----|
| Search space | All 2-edge flips | Random multi-edge jumps | Guided multi-edge sequences |
| Coverage | Complete (2-flip) | Stochastic | Stochastic + structured |
| Per-step depth | 2 edges | 5–20 edges | 3–8 edges |
| Selection | Deterministic | Weighted random / clique-guided | Participation-ranked + random |
| Acceptance | Best result wins | Metropolis | Any improvement over base |
| Best fit | Steady-state mining | Basin escape | Local multi-flip improvements |

SA's unique value is **basin escape via stochastic worsening**. Neither exhaustive nor VDS can deliberately accept a worsening move in the way SA's Metropolis criterion enables.

---

## Appendix A: Incremental Evaluation Subtlety

After accepting `k` flips since the last collection rebuild:

- **Broken count** from `CliqueCollection` is an over-count: it counts cliques that no longer exist (already broken by prior accepted flips)
- **New count** from seeded BK is exact: it counts only cliques in the current graph that contain at least one freshly flipped edge

The correct adjustment maintains a `destroyed` set of clique hashes from prior accepts and subtracts overlap from the broken count. The VDS implementation already does this (`vds.rs::compute_delta`); the SA path can reuse the same primitive.

For SA without the destroyed-set machinery, periodic rebuilds (every 1,000 accepts) bound the drift to the typical accept-rate × accumulation interval.

---

*Generated for the Ramsey project — May 2026*

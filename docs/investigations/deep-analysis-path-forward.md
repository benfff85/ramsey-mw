# Strategic Analysis: Path Forward for R(8,8) ≥ 283

**Date:** 2026-05-02
**Author:** Ben Ferenchak + Claude
**Status:** Active — primary search progressing, additional search modes under evaluation

**Prior investigations referenced:**
- `paley-graph-investigation.md` — Paley(281) seed construction
- `simulated-annealing-investigation.md` — SA design requirements and lessons
- `sat-solver-investigation.md` — SAT/MaxSAT analysis and tier recommendations
- `vds-enhancements.md` — Variable-depth search enhancement plan

---

## Goal

Find a 2-coloring of K₂₈₂ with no monochromatic K₈, which would establish **R(8,8) ≥ 283**. This requires reducing the best 282-vertex graph from ~792,000 monochromatic 8-cliques down to 0.

The search begins from Paley(281) extended by one vertex (the natural seed — see `paley-graph-investigation.md`) and uses iterative edge-flip mutations to drive the clique count down.

---

## Current State

| Metric | Value |
|--------|-------|
| Vertices | 282 |
| Edges | C(282,2) = 39,621 |
| Total search pairs per stage | ~392.5M (R × B) |
| Best known clique count | 791,938 |
| Leader stage | 7065 |
| Total stages completed (leader campaign) | 1,123 |
| Recent improvement rate | ~15 cliques/stage |
| Active workers (exhaustive) | 14 |
| Active population campaigns | 8 |

The improvement rate is healthy — recent stages drop 3–20 cliques apiece. The 2-flip neighborhood is still producing genuine improvements, so the exhaustive engine remains the primary driver of progress.

---

## Why 282 Is the Right Target

R(8,8) ≥ 282 is established by Paley(281) (zero monochromatic 8-cliques on 281 vertices). The next achievable bound is R(8,8) ≥ 283, which requires a clique-free 2-coloring on 282 vertices — adding exactly one vertex to the Paley construction.

The combinatorial scale is meaningfully smaller than larger n:

| Parameter | Value |
|-----------|-------|
| C(282, 2) edges | 39,621 |
| C(282, 8) potential 8-subsets | ~3.18 × 10¹³ |
| Average cliques per edge at 792K | ~560 |

A naive lower bound on flips needed: `792,000 / 560 ≈ 1,414` if each flip broke unique cliques and created none. With the new-clique-creation effect this is a substantial underestimate, but it gives a sense of the scale.

---

## Search Strategies and Trade-offs

### Exhaustive 2-flip search (primary engine)

Each stage tests every (red_edge × blue_edge) pair (~392.5M) using incremental Bron-Kerbosch with early termination via the top-N threshold. When a pair improves the count, the resulting graph becomes the next stage's base.

- **Strengths:** complete coverage of the 2-flip neighborhood, deterministic, scales horizontally
- **Weaknesses:** cannot escape 2-flip local minima; once the neighborhood plateaus, exhaustion fallback advances to "least-bad" alternatives, producing oscillation

This is the only mode currently active in production.

### Variable-depth search (deepening)

Lin-Kernighan-style chained flips with locality-aware candidate selection at each depth. Designed to find improvements invisible to 2-flip search by allowing transient worsening within a chain that nets improvement at the end. See `vds-enhancements.md` for the active enhancement plan.

### Population search (8 active campaigns)

Eight independent 282-vertex base graphs evolve as separate campaigns, each running the exhaustive search on its own descent. Currently they function as parallel restarts; genuine genetic-algorithm crossover between them is not yet implemented and is a high-leverage TODO.

### Simulated annealing (deferred)

SA was implemented but produced no improvements at the per-iteration cost of full Bron-Kerbosch recount. The fix is well-understood (incremental evaluation via `CliqueCollection`, larger perturbations, scale-appropriate temperature). See `simulated-annealing-investigation.md` for the design requirements before SA can usefully run again.

### SAT / MaxSAT (window-based)

Direct SAT encoding is infeasible (~6.4 × 10¹³ clauses for the full problem). Local MaxSAT on 100–200-edge windows is feasible and untried. See `sat-solver-investigation.md` for the tier recommendations.

---

## Recommended Sequencing

The exhaustive engine is producing a steady ~15 cliques/stage. New modes should be additive, not replacement.

1. **Finish VDS Phase 2 + 3** (`vds-enhancements.md`) — locality-aware candidate selection at depth ≥ 2, then deepen with proper pruning. Highest-impact algorithmic upgrade.
2. **Tabu search worker** — never been built; well-suited to the oscillation pattern that emerges late in stages. ~3 days in Rust.
3. **Genuine GA crossover** between the 8 population campaigns. Vertex-partition recombination across divergent lineages. Diversity is real and currently unused.
4. **Local MaxSAT prototype** — Tier 2 from `sat-solver-investigation.md`. Find optimal multi-edge moves within 100–200-edge windows.
5. **ML edge ranker** — XGBoost on existing `work_result` history, deployed as a `WorkEnumerationStrategy` that prioritizes promising edge pairs. Trained for ~$0–5 of compute on M4 Max.
6. **Symmetry exploitation** — nauty/bliss FFI for automorphism reduction; even modest factors compound on stuck stages.

---

## Honest Assessment

R(8,8) ≥ 283 is an open question. Whether a 282-vertex K₈-free 2-coloring exists is unknown; if it does not, no algorithm can find it.

- R(8,8) ≥ 282 — proven (Paley(281))
- R(8,8) ≤ 6,090 — best known upper bound
- No proven lower bound between 282 and 6,090 exists

The 791,938 → 0 trajectory is steep, but the current rate is positive. Realistic outcomes:

| Approach | Realistic best case | Likely outcome |
|----------|---------------------|----------------|
| Continued exhaustive search | Steady decrease until 2-flip plateau | Eventual stall at some local minimum |
| VDS Phase 2 + 3 | Progress past the 2-flip wall | 10–100 cliques per session beyond the local min |
| Tabu search | Find a different basin at similar depth | Diversifies the trajectory |
| Local MaxSAT | Multi-edge improvements unreachable by smaller neighborhoods | Periodic step-changes if solver finds productive windows |
| Reaching 0 cliques | Establishes R(8,8) ≥ 283 | Long shot — requires structural breakthrough or sustained large-scale compute |

The honest framing: this work **maps the landscape and produces steady incremental records**. A breakthrough is a long-shot byproduct, not the only success metric.

---

## Appendix: Computational Constraints

| Constraint | Value | Notes |
|------------|-------|-------|
| Hardware | M4 Max MacBook, 48GB RAM | Local primary |
| Burst | Vast.ai EPYC spot (~$0.04/hr × 32-core) | Used for occasional pushes |
| Storage | MySQL + Dragonfly, all local | |
| Workers | 14 Rust exhaustive @ 250K batch size | |

---

*Generated for the Ramsey project — May 2026*

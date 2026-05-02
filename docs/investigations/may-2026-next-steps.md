# Next Steps — May 2026

**Date:** 2026-05-02
**Author:** Ben Ferenchak + Claude
**Status:** Forward-looking — prioritized backlog of algorithmic, infrastructural, and ML upgrades for the R(8,8) ≥ 283 search

**Related documents:**
- `deep-analysis-path-forward.md` — strategic context
- `vds-enhancements.md` — VDS Phase 1/2/3 plan
- `simulated-annealing-investigation.md` — SA design requirements
- `sat-solver-investigation.md` — local MaxSAT plan
- `paley-graph-investigation.md` — seed-graph rationale

---

## Where the Search Stands

The leader campaign sits at **791,938 cliques** on 282 vertices and is still finding 2-flip improvements at ~15 cliques/stage. The exhaustive engine is healthy. Seven additional population campaigns exist in the database but have stalled — workers have effectively converged on the leader campaign.

This document lays out the next 3–6 months of work, prioritized by expected impact relative to effort. It is intentionally additive: nothing here proposes shutting down the working exhaustive engine.

---

## Tier 0 — Cleanup (1 day, do first)

### 0.1 Decide the fate of the 7 stalled population campaigns

Stages haven't transitioned in ~12 days; work indices are frozen at 0.6%–9% complete. Two paths:

- **Retire them.** `UPDATE campaign SET status='INACTIVE' WHERE campaign_id IN (3,4,5,6,7,8,9)` and clear their stale Redis keys (`stage_config:*`, `processed_count:*`, `best_results:*`).
- **Wire them into a real GA.** Implement crossover (see Tier 2.3); the dead forks become a working population.

Either choice is fine; leaving them in their current state burns memory and database rows for no benefit.

### 0.2 Capture a baseline snapshot

Before any new search mode lands, record current best graph + clique count + stage history. Future improvements need a fixed comparison point.

---

## Tier 1 — High Leverage, Low-to-Medium Effort

### 1.1 Finish VDS Phase 2 + 3 *(highest expected payoff, ~1 week)*

The VDS worker is the closest thing to Lin-Kernighan for this problem. Phase 1 is complete; Phase 2 (locality-aware candidate selection at every depth) is the unlock. See `vds-enhancements.md` for the full plan.

**Why this is highest priority:** every other algorithmic improvement either reuses VDS infrastructure or solves an adjacent problem. VDS Phase 2+3 directly attacks the failure mode the exhaustive engine cannot — escaping the 2-flip wall via coordinated multi-edge moves.

**Concrete deliverables:**
- Locality-aware candidate selection at depth ≥ 2 (currently only depth ≥ 3)
- Tighten `worsening_tolerance` to a basin-scale value (~500)
- Cache the global ranking once per run
- Increase `max_depth` to 8–12 once Phases 1+2 are validated

### 1.2 Tabu search worker mode *(~3 days)*

Never been built. Well-suited to the oscillation pattern that emerges late in stages. The exhaustion fallback is a crude form of tabu (it picks "least bad" moves when stuck); a proper tabu search formalizes this with memory.

**Algorithm sketch:**
```
tabu_list = []  # (edge, expiry_iteration)
best_ever = current.clique_count

for iter in 0..max_iterations:
    move = best_non_tabu_2flip(graph, tabu_list)
    apply(move); record(move.edges, iter + tabu_tenure)
    if graph.clique_count < best_ever:
        best_ever = graph.clique_count; submit(graph)
```

Tabu tenure starts at ~200 (≈ √num_edges); make it adaptive (longer when no improvement, shorter when improving).

### 1.3 Clique-guided mutation worker mode *(~1 day)*

WalkSAT-inspired. Pick a random surviving 8-clique; for each of its 28 edges, compute the delta if flipped; flip the best (or accept probabilistically). Reuses `CliqueCollection` + incremental delta machinery.

Cheapest experiment in the backlog. Run it first to see whether targeted single-edge flips beat the existing enumeration strategies.

### 1.4 Symmetry exploitation via nauty/Traces FFI *(~2 days)*

The evolved 282-vertex graphs likely have small but non-trivial automorphism groups. Even a 2–10× search-space reduction is meaningful on stuck stages. nauty handles 282-vertex graphs in seconds.

Implementation: wrap nauty's C API in a thin Rust crate, compute the graph's orbits at stage start, and modify the enumeration strategy to skip orbit-equivalent edge pairs.

---

## Tier 2 — Medium Effort, Speculative Payoff

### 2.1 Local MaxSAT window optimizer *(~3–5 days)*

See `sat-solver-investigation.md` for the full analysis. Pick 100–200 high-participation edges, encode the affected cliques + new-clique constraints, run RC2 or Open-WBO, apply the solution, verify with comprehensive Bron-Kerbosch.

Most "SAT-like" approach that's actually feasible at this scale. Python + `pysat` is the fastest implementation path; Rust + `varisat` is the alternative.

**Risk:** window selection. If the solver's window doesn't contain the right edges, the result is wasted compute. Hot-edge selection from `CliqueCollection` is the obvious heuristic; orbit-aware selection (after Tier 1.4) is better.

### 2.2 Fix and re-enable SA *(~2 days)*

See `simulated-annealing-investigation.md` for the three required changes:
1. Incremental evaluation via `CliqueCollection` (not full Bron-Kerbosch per iteration)
2. Initial temperature ~50 (matching basin width), not 1000
3. Perturbation size 5–20 edges (not 2–4)

After the rewrite, SA can do millions of iterations in the same wall-clock budget the original implementation spent on thousands.

### 2.3 Real GA crossover for the population campaigns *(~1 week)*

Currently the 7 fork campaigns are independent restarts; their diversity is wasted. Implement vertex-partition recombination:

```
parents: G1, G2 (best graphs from two campaigns)
partition: V = A ⊔ B (random vertex partition, ~50/50)
child:
    edges within A: from G1
    edges within B: from G2
    edges A-B: random selection from {G1 edges, G2 edges}
```

Periodically (every N stages), recombine the top-K campaigns into new offspring base graphs. Promote the best offspring to a campaign slot.

### 2.4 Variable-depth search with NN-scored candidates *(~2 weeks; Year 2 milestone)*

After the ML edge ranker (Tier 3.1) is operational, swap the participation-based ranking inside VDS for the NN's predicted improvement score. This is the AlphaZero-pattern application: NN guides tree search, tree search generates training data, NN improves.

This is significant infrastructure but the payoff is large — every search mode benefits from a better edge-quality signal.

---

## Tier 3 — ML Track (Year 1–5 ramp)

### 3.1 XGBoost edge-pair ranker *(~1 week)*

**Year 1 milestone.** Train on existing `work_result` history; treat each (edges_to_flip, delta) pair as a training example. Features per edge:

- Endpoint degrees (red and blue)
- Common neighbor count (red/blue)
- Clique participation count from `CliqueCollection`
- Local clustering coefficient
- Distance to recently successful flips (graph distance, not Euclidean)
- Color of the edge

Output: predicted delta for the pair.

Deploy as a new `WorkEnumerationStrategy::ML_RANKED` that orders work-unit indices by predicted improvement. Workers process high-probability pairs first; combined with early termination, this can ~2× effective throughput even at modest accuracy.

**Cost:** $0 (trains on M4 Max in hours), retrains weekly.

### 3.2 GNN state representation *(~2 months; Year 2 milestone)*

Replace XGBoost with a small GIN/GAT (~few million parameters) that takes the full graph context, not just per-edge features. M4 Max with Metal acceleration handles training at this scale; Vast.ai burst (~$5–20) for hyperparameter sweeps.

### 3.3 Neural-guided MCTS / VDS *(~3–6 months; Year 3 milestone)*

NN scores edge candidates → VDS uses NN-scored ordering for branch selection → tree search becomes AlphaZero-style. Self-supervision: every successful improvement adds to the training set.

This is the architecture that broke through in TSP (DeepMind, 2023), AlphaTensor (2022), AlphaDev (2023), and chip placement (Google, 2021). It is genuinely the same shape of problem.

### 3.4 Generative graph models for seed candidates *(~6+ months; Year 4–5)*

Train a diffusion model on the trajectory of low-clique 282-vertex graphs. Sample new candidate base graphs from the learned distribution; locally optimize. ML equivalent of "better starting graphs" — and unlike Paley, the model learns what *low-clique structure for K₈* looks like specifically.

---

## Tier 4 — Infrastructure & Sustainability

### 4.1 Vast.ai burst deployment script *(~1 day)*

CPU-only spot instances at $0.02–0.08/hr. A 32-core EPYC for 8 hours costs ~$0.32. Already partially documented; just needs the deploy script + checkpoint/resume hooks for spot interruption handling.

Budget guideline: $5–10/month covers significant burst capacity beyond local compute.

### 4.2 Checkpoint/resume for stage progress *(~1 day)*

Trivial fix via Dragonfly's built-in RDB snapshotting:

```yaml
# In docker/main/ramsey-compose.yml
command: dragonfly --maxmemory 7gb --save 300 1 --dbfilename dump.rdb --dir /data
volumes:
  - dragonfly-data:/data
```

This alone prevents losing 8,500-batch stage progress on reboot.

### 4.3 Tests on the Bron-Kerbosch and BitMatrix code paths *(~2 days)*

Currently 0% coverage on the algorithm core. An off-by-one in `BitMatrix::next_set_bit` or a clique-count mismatch could silently invalidate millions of work units. Highest-value tests:

- `BitMatrix`: bit boundary cases (0, 63, 64, 281), cardinality, `and_assign`, `next_set_bit` wraparound
- `Graph::from_bitstring`: small known graph (K₅) → verify adjacency
- `get_all_cliques`: K₅ has 1 5-clique; Petersen has 0 3-cliques
- `get_new_cliques_with_limit`: flip an edge in K₅, verify count change
- `Enumerator`: `index_to_edge_pair` is bijective (no dups, no gaps)
- Property test: random graphs satisfy `get_all_cliques == get_cliques_comprehensive`

### 4.4 Generalize beyond R(8,8) *(~1 week, longer-term lever)*

Make the system parameterized for any R(s,t). R(5,5) is more accessible (current bounds 43 ≤ R(5,5) ≤ 48) and has an active community. Wins there are publishable results that stress-test the tooling on a problem with more known structure. This is the single highest-leverage decision for a 20-year horizon: it transforms the project from a single-target solver into an open Ramsey computational platform.

### 4.5 Open-source and document *(~2 weeks, ongoing)*

The Ramsey community is small (Radziszowski's survey is the central reference; Heule's SAT work is the gold standard). A working distributed system with a public leaderboard would attract collaborators. Even Heule's R(5,5) push relied on community contributions.

Concrete steps: write a clear README, sanitize the deploy scripts, publish to GitHub with a permissive license, post to r/math and the appropriate mailing lists.

---

## Recommended Execution Sequence

```
Month 1 (Tier 1)
├── Week 1:    Tier 0 cleanup + 1.3 clique-guided mutation prototype
├── Week 2-3:  Tier 1.1 — VDS Phase 2 + 3
└── Week 4:    Tier 1.2 — tabu search worker + 1.4 nauty integration

Month 2 (Tier 2 + ML start)
├── Week 5-6:  Tier 2.1 — local MaxSAT prototype
├── Week 7:    Tier 2.2 — fix and re-enable SA
└── Week 8:    Tier 3.1 — XGBoost edge ranker (data prep + training)

Month 3 (consolidate + diversify)
├── Week 9:    Tier 4.1 + 4.2 — Vast.ai deploy + Dragonfly persistence
├── Week 10:   Tier 4.3 — algorithm tests
├── Week 11:   Tier 2.3 — GA crossover + reactivate population campaigns
└── Week 12:   Tier 3.1 — XGBoost deployed as WorkEnumerationStrategy

Month 4-6 (ML buildout + community)
├── Tier 3.2 — GNN state representation
├── Tier 4.4 — generalize beyond R(8,8)
└── Tier 4.5 — open-source and publish first results
```

---

## What This Plan Does NOT Include

Deliberately deferred:

- **Quantum approaches.** See the Reddit/literature analysis; the most-cited concrete approach (Gaitan-Clark adiabatic) does not beat classical methods, and useful quantum hardware is out of budget. Worth tracking as background but not investing in.
- **Direct SAT encoding of the full problem.** ~6.4 × 10¹³ clauses. Infeasible.
- **Larger vertex counts (n > 282).** The next bound R(8,8) ≥ 283 is the natural target. Pushing toward n > 282 requires resolving 282 first.
- **Replacing the exhaustive engine.** It still produces ~15 cliques/stage of progress. Add capability around it, don't tear it out.

---

## Success Metrics

- **Short-term (3 months):** breakthrough below 790,000 cliques via at least one of {VDS Phase 2+3, tabu search, local MaxSAT, GA crossover}
- **Medium-term (1 year):** breakthrough below 750,000 cliques; ML edge ranker deployed and measurably outperforming participation-based ranking
- **Long-term (5 years):** R(8,8) ≥ 283 published, OR clear evidence the search has converged on a local minimum significantly below 750K with structural understanding of why
- **Sustained-term (20 years):** generalized platform supporting multiple R(s,t) targets; ML stack mature enough that adding a new target is hours of work

---

*Generated for the Ramsey project — May 2026*

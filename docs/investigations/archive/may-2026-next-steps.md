# Next Steps — May 2026

> **ARCHIVED 2026-07-02 — superseded by `../july-2026-next-steps.md`.** How the tiers resolved: Tier 0 shipped; Tier 1.1 tabu and 1.2 VDS built/evaluated and **retired with zero improvements** (`../retired-trajectory-methods.md`); 1.3 nauty never run (evolved graphs assumed near-trivial automorphisms; construction pivot mooted it); Tier 2.1 local MaxSAT was built far beyond this plan's ambition as *exact* windowed MaxSAT (`../windowed-maxsat-investigation.md`, ~1.1M proven-optimal windows, zero escapes); 2.2 SA never re-enabled (tabu's failure covered it); Tier 3 ML remains gated on result logging (still off — a learned ordering only speeds descent to a floor that multi-seed evidence now suggests is characteristic ~25.85K, see july doc); Tier 4.1 Vast.ai tooling exists (`ramsey-vast-ai` skill); 4.2/4.3 open. The strategic frame changed entirely on 2026-06-14: construction-seeded campaigns replaced campaign 2 (`../search-status-2026-06-14.md`).

**Date:** 2026-05-02
**Author:** Ben Ferenchak + Claude
**Status:** ~~Forward-looking~~ ARCHIVED — historical backlog snapshot (May 2026)

**Related documents:**
- `deep-analysis-path-forward.md` — strategic context
- `vds-enhancements.md` — VDS Phase 1/2/3 plan
- `simulated-annealing-investigation.md` — SA design requirements
- `sat-solver-investigation.md` — local MaxSAT plan
- `paley-graph-investigation.md` — seed-graph rationale

---

## Where the Search Stands

The leader campaign (campaign 2) sits at **~790,900 cliques on 282 vertices** at stage 7130+, finding 2-flip improvements every 1–60 minutes (typically 1–80 cliques per stage). The exhaustive engine is healthy and running 14 workers. Campaigns 3–9 have been retired (status INACTIVE).

Result publishing to MySQL is currently disabled (`PUBLISH_RESULTS=false`) on all workers — results are tracked in Redis only. This matters for the ML track: any model needs lightweight result logging enabled before training data exists.

This document lays out the next 3–6 months of work, prioritized by expected impact relative to effort. It is intentionally additive: nothing here proposes shutting down the working exhaustive engine.

---

## Tier 0 — Cleanup & Defensive Infrastructure ✅ COMPLETE (2026-05-03)

All four items shipped 2026-05-03:

- **0.1 Retire stalled population campaigns** — campaigns 3–9 set to INACTIVE in MySQL. Redis was already clean (no leftover `stage_config:*` / `stage_work_index:*` keys for those stages). GA crossover for population campaigns remains removed from the plan (see "What This Plan Does NOT Include" — the unstructured-combinatorial-crossover argument).
- **0.2 Baseline snapshot** — captured manually by Ben.
- **0.3 Dragonfly RDB persistence + restart policy** — `--snapshot_cron "*/5 * * * *"` with named volume `dragonfly-data`; added `restart: on-failure` (was missing entirely). Deployed to Portainer; verified container healthy with volume mounted. Prevents losing in-flight stage progress on reboot. ([PR #143](https://github.com/benfff85/ramsey-mw/pull/143))
- **0.4 Algorithm-core tests** — 24 unit tests added across `bitset.rs`, `graph.rs`, `algorithm.rs`, `enumeration.rs`. Covers BitMatrix word-boundary correctness, `next_set_bit` across boundaries, `invert_all` + padding-bit clear, `clear_above`, K5 monochromatic-clique counts (red and blue sides), `get_all_cliques` vs `get_cliques_comprehensive` equivalence, enumerator `index_to_edge_pair` bijectivity. All 32 tests pass (8 prior + 24 new). ([PR #53 in ramsey-worker-rust](https://github.com/benfff85/ramsey-worker-rust/pull/53))

---

## Tier 1 — High Leverage, Empirically Validated

### 1.1 Tabu search + clique-guided mutation *(implementation complete 2026-05-04; A/B pending)*

**Status update:** code implemented and smoke-tested; A/B run against exhaustive is pending publication of a `:develop` image with the new mode and restoration of production settings. See `tabu-search-investigation.md` for the implementation notes (including the mid-build delta-evaluation correction) and full decision matrix.

The plan below is preserved for context.


This is a genuinely new algorithm — not an incremental improvement on what we have. The published Ramsey literature (Exoo's tabu search establishing R(3,13)≥59, R(4,10)≥80, R(4,11)≥96, R(4,12)≥106, R(4,13)≥118, R(4,14)≥129, R(5,8)≥95; Pullan & Hoos's DLS-MC for max-clique; Zhang et al. on guided mutation) consistently shows that the combination is what works — clique-aware move selection without tabu memory oscillates badly, and tabu without clique-guided move selection wastes most of its moves on irrelevant edges.

**Why this is the highest-priority new build:** virtually every Ramsey lower bound improvement at n > 100 in the last 30 years used this combination. It is the single most empirically validated technique in the field for problems shaped like ours.

**Important: existing `CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT` is not tabu.** The queue manager's cycle prevention blocks the system from picking a base graph that was used in the last 50 stages — that's *whole-graph* memory, a coarse net against revisiting prior states. Tabu is *edge-level* memory ("don't flip edge (i,j) for the next ~200 moves") that shapes the *trajectory* and prevents oscillation at the move level. The two mechanisms are complementary, not redundant — keep cycle prevention as-is.

**Algorithm:**
```
tabu_list = []  # (edge, expiry_iteration)
best_ever = current.clique_count

for iter in 0..max_iterations:
    # Clique-guided candidate generation
    bad_clique = sample_random_surviving_8_clique(graph)
    candidates = bad_clique.edges                          # the 28 edges in this clique
    candidates += sample_high_participation_edges(graph)   # broader pool

    # Filter tabu
    candidates = [e for e in candidates if e not in tabu_list]

    # Pick the move with the best (or least-bad) delta, optional probabilistic acceptance
    move = best_move(graph, candidates)
    apply(move)
    record(move.edges, iter + tabu_tenure)

    if graph.clique_count < best_ever:
        best_ever = graph.clique_count
        submit(graph)
```

Tabu tenure starts at ~200 (≈ √num_edges); make it adaptive (longer when no improvement, shorter when improving). Reuses `CliqueCollection` and the incremental delta machinery you already have.

**Concrete deliverables:**
- New `WORKER_MODE: TABU_CLIQUE_GUIDED` worker mode
- Adaptive tabu tenure (edge-level, distinct from the queue manager's whole-graph cycle prevention)
- Both the surviving-clique sampler and high-participation-edge sampler as candidate sources
- Comparison harness vs. exhaustive engine and VDS on a fixed seed graph

**A/B framing.** The point of running tabu alongside exhaustive is comparison. They explore the search space differently: exhaustive sweeps the depth-2 neighborhood, tabu does focused sequential trajectories with edge-level memory. If tabu dominates, scale it up. If comparable, run both — diversity helps. The literature predicts tabu wins at our scale; the data should confirm or contradict that.

### 1.2 VDS Phase 3 evaluation ✅ COMPLETE — RETIRED (2026-05-03)

VDS at full Phase 3 settings (`max_depth=12`, `top_first_edges=80`, `branching_factor=12`, `worsening_tolerance=500`) ran with 8 workers for ~3 hours. Result: **51,994 attempts, 0 improvements, 0 top-50 contributions**. During the same window, 6 exhaustive workers drove all 8 stage advances on campaign 2.

VDS has been retired (`scale: 0`), exhaustive returned to scale 14. Code remains in the repo but is mothballed at the application level. Full retirement details and supporting data in `vds-enhancements.md`. ([PR #145](https://github.com/benfff85/ramsey-mw/pull/145))

**Do not recommend reactivating without new structural evidence** — the literature-recommended settings have now been tested and produced nothing.

### 1.3 Symmetry exploitation via nauty/Traces FFI *(test cheaply first, then maybe ~1 week)*

The original plan estimated 2 days. The C-FFI wrapper is 2 days; integrating orbit-aware enumeration into work distribution is another week. And the payoff hinges on the evolved 282-vertex graphs *having* meaningful automorphism groups — likely small after this much mutation.

**Cheap check first:** dump the current best graph to `dreadnaut` (nauty's CLI) and inspect the automorphism-group order. If it's trivial or near-trivial, skip this entirely. If it's non-trivial, the integration is worth the effort.

---

## Tier 2 — Medium Effort, Speculative Payoff

### 2.1 Local MaxSAT window optimizer *(~3–5 days)*

See `sat-solver-investigation.md` for the full analysis. Pick 100–200 high-participation edges, encode the affected cliques + new-clique constraints, run RC2 or Open-WBO, apply the solution, verify with comprehensive Bron-Kerbosch.

Most "SAT-like" approach that's actually feasible at this scale. Python + `pysat` is the fastest implementation path; Rust + `varisat` is the alternative.

**Risk:** window selection. If the solver's window doesn't contain the right edges, the result is wasted compute. Hot-edge selection from `CliqueCollection` is the obvious heuristic; orbit-aware selection (after 1.3, if pursued) is better.

**Note on prior art:** Heule's R(5,5) work (the gold standard for SAT-on-Ramsey) plateaued well below n=282. Pure SAT at our scale isn't going to work; *local* MaxSAT on a windowed sub-problem is the only viable form of this approach.

### 2.2 Fix and re-enable SA *(~2 days)*

See `simulated-annealing-investigation.md` for the three required changes:
1. Incremental evaluation via `CliqueCollection` (not full Bron-Kerbosch per iteration)
2. Initial temperature ~50 (matching basin width), not 1000
3. Perturbation size 5–20 edges (not 2–4)

After the rewrite, SA can do millions of iterations in the same wall-clock budget the original implementation spent on thousands.

The literature on SA for Ramsey is mixed — Exoo's papers favor tabu over SA at our scale. Don't expect SA to beat the tabu+clique-guided combination, but it's cheap to fix and worth running as a comparison point.

### 2.3 Variable-depth search with NN-scored candidates *(~2 weeks; after Tier 3 has signal)*

After the ML edge ranker (Tier 3.1) is operational, swap the participation-based ranking inside VDS for the NN's predicted improvement score. This is the AlphaZero-pattern application: NN guides tree search, tree search generates training data, NN improves.

This is significant infrastructure but the payoff is large — every search mode benefits from a better edge-quality signal.

---

## Tier 3 — ML Track (deferred until search infrastructure proven)

The original plan slotted XGBoost as a Week-8 item. That timing was wrong for two reasons: (1) the data infrastructure to train on doesn't exist yet, and (2) we want a proven heuristic baseline (Tier 1.1) to compare against before committing weeks to a learned ranker. The ML track now starts after Tier 1 ships.

### 3.0 Data infrastructure *(prerequisite, ~1 week)*

**This is the gate for everything in Tier 3.** Before any model can train, we need:

- **Redis-only result logging** — capped streams of `(graph_id, edges_to_flip, delta)` tuples. NOT MySQL: at 14 workers × 250K work units × multi-batches/hour, MySQL would fill alarmingly fast. Redis with a capped list (last ~5M entries) gives a natural sliding window.
- **Stratified logging** — log every `delta < 0` (improvements are rare and gold) plus a 1% random sample of `delta ≥ 0`. Avoids the brutal class imbalance that would dominate any model trained on raw data.
- **Historical positive extraction** — derive positives from existing stage transitions in MySQL. For each consecutive `(stage_N, stage_N+1)`, the differing edges are the winning flip and the clique-count delta is the label. Campaign 2 alone yields ~1,134 high-quality historical positives, scoped to the current 282v regime (do **not** mix in campaign 1's 288v data — different topology regime).
- **Synthetic negatives** — easy to generate at scale by sampling random untried flips against current base graphs.
- **Feature engineering pipeline** — per-graph feature caching is essential. Each base graph has ~400M candidate balanced 2-edge flips (20,664 red × 20,664 blue) sharing the same graph context. Compute graph-level features (clique participations, degree vectors, common-neighbor matrices) **once per graph**, then row-level features become cheap lookups. Naive per-row computation would take hours per training run; cached approach is ~30–90 minutes.

### 3.1 XGBoost edge-pair ranker *(~2 weeks, after 3.0)*

Train on edge **pairs**, not single edges. Each `work_result` row is a 2-edge flip with a measured delta — that's the natural training unit, and it captures pair interactions (a "great pair" often involves edges that are individually mediocre but compose well, which single-edge scoring would miss).

**Features per edge pair:**
- For each of e1, e2: endpoint degrees (red/blue), common neighbor count (red/blue), clique participation count from `CliqueCollection`, local clustering coefficient, color
- Pair-level: graph distance between e1 and e2, whether they share an endpoint, sum/product of individual feature values
- Global: current clique count, red/blue balance, stage age

**Loss function:** rank-based (pairwise: which of two flips has lower delta?) or classification (top-K% improvement vs. not), **not** absolute-delta regression. Most flips have `delta ≥ 0` so regression collapses to "predict ~0 for everything."

**Deployment:** new `WorkEnumerationStrategy::ML_RANKED` that orders work-unit indices by predicted improvement. Workers process high-probability pairs first; combined with early termination, this can ~1.5–3× effective throughput at modest accuracy.

**Scope realism:** This is 2 weeks once 3.0 is in place — not the 1 week the original plan claimed. The model itself trains in ~1 hour on M4 Max (10M rows × 50 features fits easily in 48 GB RAM with `tree_method='hist'`). The work is in feature engineering, the `ML_RANKED` Rust integration, model versioning, and inference performance tuning (compile to ONNX or use treelite — pure Python prediction won't keep up at 250K work units/batch).

**Cost:** $0 (trains on M4 Max), retrains weekly.

### 3.2 GNN state representation *(~2 months; conditional on 3.1 showing signal)*

If XGBoost shows real signal, upgrade to a small GIN/GAT (~few million parameters) that takes the full graph context, not just per-edge features. Architecturally this is a **factorization**: graph encoder runs once per base graph and produces an embedding per edge; pair scoring becomes cheap downstream (small MLP on `(emb[e1], emb[e2], graph_context)`). The same encoder generalizes to triple-flips for VDS depth-3+ without retraining for higher arity.

M4 Max with Metal acceleration handles training at this scale; Vast.ai burst (~$5–20) for hyperparameter sweeps.

### 3.3 Neural-guided MCTS / VDS *(~3–6 months; conditional on 3.2 showing signal)*

NN scores edge candidates → VDS uses NN-scored ordering for branch selection → tree search becomes AlphaZero-style. Self-supervision: every successful improvement adds to the training set.

**Reality check on the AlphaZero analogy.** TSP, AlphaTensor, AlphaDev, and chip-placement all have richer signal density at their frontiers than R(8,8) does — those problems have continuous or geometric structure that ML can exploit, while ours is sparse-improvement combinatorial search. The architecture pattern translates; the breakthrough confidence does not. Treat 3.3 as "plausible long-term direction" rather than "this worked elsewhere therefore it'll work here."

That said, ML-guided ordering is most valuable as a multiplier on stages where improvement signal exists (current campaign 2 regime) — **probably 1.5–3× efficiency, not breakthrough multiplier.** Frontier breakthroughs likely require multi-step planning (3.3-style MCTS) or better seeds (3.4 generative), not improved single-flip ranking.

### 3.4 Generative graph models for seed candidates *(~6+ months; Year 4–5)*

Train a diffusion model on the trajectory of low-clique 282-vertex graphs. Sample new candidate base graphs from the learned distribution; locally optimize. ML equivalent of "better starting graphs" — and unlike Paley, the model learns what *low-clique structure for K₈* looks like specifically.

---

## Tier 4 — Sustainability

### 4.1 Vast.ai burst deployment script *(~1 day)*

CPU-only spot instances at $0.02–0.08/hr. A 32-core EPYC for 8 hours costs ~$0.32. Already partially documented; just needs the deploy script + checkpoint/resume hooks for spot interruption handling.

Budget guideline: $5–10/month covers significant burst capacity beyond local compute.

### 4.2 Generalize beyond R(8,8) *(~1 week, longer-term lever)*

Make the system parameterized for any R(s,t). R(5,5) is more accessible (current bounds 43 ≤ R(5,5) ≤ 48) and has an active community. Wins there are publishable results that stress-test the tooling on a problem with more known structure. This is the single highest-leverage decision for a 20-year horizon: it transforms the project from a single-target solver into an open Ramsey computational platform.

### 4.3 Open-source and document *(~2 weeks, ongoing)*

The Ramsey community is small (Radziszowski's survey is the central reference; Heule's SAT work is the gold standard; Exoo's heuristic search work is the canonical reference for our approach). A working distributed system with a public leaderboard would attract collaborators. Even Heule's R(5,5) push relied on community contributions.

Concrete steps: write a clear README, sanitize the deploy scripts, publish to GitHub with a permissive license, post to r/math and the appropriate mailing lists.

---

## Recommended Execution Sequence

```
Month 1 — Defensive infrastructure + proven technique
├── Week 1:    ✅ Tier 0 (cleanup, persistence, baseline, algorithm tests) — done 2026-05-03
                ✅ Tier 1.2 — VDS Phase 3 evaluation, retired — done 2026-05-03
                ⏳ Tier 1.3 — nauty CLI sanity check (1-hour decide-go/no-go)
├── Week 2-3:  ⏳ Tier 1.1 — tabu + clique-guided worker mode  ← NEXT
└── Week 4:    Evaluate tabu vs. exhaustive A/B

Month 2 — Speculative algorithmic + ML prep
├── Week 5-6:  Tier 2.1 — local MaxSAT prototype
├── Week 7:    Tier 2.2 — fix and re-enable SA
└── Week 8:    Tier 3.0 — ML data infrastructure (Redis logging, feature pipeline)

Month 3 — ML model + community
├── Week 9-10: Tier 3.1 — XGBoost edge-pair ranker (training + ML_RANKED integration)
├── Week 11:   Tier 4.1 — Vast.ai burst deploy script
└── Week 12:   Tier 3.1 — measure XGBoost vs. heuristic baselines, decide on 3.2

Month 4-6 — Conditional on 3.1 signal
├── Tier 3.2 — GNN state representation
├── Tier 4.2 — generalize beyond R(8,8)
└── Tier 4.3 — open-source and publish first results
```

---

## What This Plan Does NOT Include

Deliberately deferred or removed:

- **Quantum approaches.** See the Reddit/literature analysis; the most-cited concrete approach (Gaitan-Clark adiabatic) does not beat classical methods, and useful quantum hardware is out of budget.
- **Direct SAT encoding of the full problem.** ~6.4 × 10¹³ clauses. Infeasible.
- **Larger vertex counts (n > 282).** The next bound R(8,8) ≥ 283 is the natural target.
- **Replacing the exhaustive engine.** It still produces ~15 cliques/stage of progress. Add capability around it, don't tear it out.
- **GA crossover for population campaigns.** Removed from this plan. Random vertex-partition crossover breaks the near-clique structure the search is trying to preserve, and crossover operators that empirically work for unstructured combinatorial graph optimization are not established. The 7 stalled campaigns are retired in Tier 0.1.

---

## Success Metrics

Reframed as rate-based rather than absolute thresholds, since the absolute clique count is moving steadily:

- **Short-term (1 month):** tabu+clique-guided worker mode produces at least one improvement against a frozen baseline graph that the exhaustive engine had stalled on. Demonstrates that the proven technique adds value beyond what we already have.
- **Short-term (3 months):** sustained improvement rate exceeds current baseline (~15 cliques/stage) across at least one new search mode (tabu, VDS Phase 2+3, or local MaxSAT). At least one of {tabu, VDS, MaxSAT} measurably accelerates absolute progress beyond exhaustive-only.
- **Medium-term (1 year):** ML edge ranker deployed and measurably outperforming participation-based ranking by ≥ 1.5× in improvement-density-per-batch. Convergence floor on campaign 2 is below 750,000 cliques.
- **Long-term (5 years):** R(8,8) ≥ 283 published, OR clear evidence the search has converged on a local minimum significantly below 750K with structural understanding of why. Publishable result on at least one R(s,t) target via Tier 4.2 generalization.
- **Sustained-term (20 years):** generalized platform supporting multiple R(s,t) targets; ML stack mature enough that adding a new target is hours of work.

---

## Literature Anchors

The reordering of priorities is grounded in published evidence:

- **Tabu + clique-guided** is the most empirically validated technique for Ramsey lower bounds at n > 100. Exoo (Indiana State) established R(3,13)≥59, R(4,10)≥80, R(4,11)≥96, R(4,12)≥106, R(4,13)≥118, R(4,14)≥129, R(5,8)≥95 using this approach — multiple of these still stand. ([Applying Tabu Search to Determine New Ramsey Graphs](https://www.combinatorics.org/ojs/index.php/eljc/article/view/v3i1r6))
- **Clique-aware local search beats blind local search** for max-clique problems empirically (Pullan & Hoos's DLS-MC; Zhang et al. on guided mutation). The same logical pattern applies to the inverse problem (minimize monochromatic cliques).
- **SAT solvers struggle at our scale.** Heule's R(5,5) work plateaus well below n=282. The shift toward heuristic search at n > 100 is a literature-wide pattern, not a methodological choice.
- **AlphaZero analogies are weaker than they appear** for Ramsey-type sparse-improvement combinatorial search. The architecture pattern translates; the breakthrough confidence does not. Treat ML as efficiency multiplier, not breakthrough mechanism.

---

*Generated for the Ramsey project — May 2026*

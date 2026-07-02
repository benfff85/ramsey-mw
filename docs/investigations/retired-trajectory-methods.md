# Retired Trajectory Methods — SA, Tabu+Clique-Guided, VDS (consolidated record)

**Date:** 2026-07-02 (consolidates three investigation docs from April–May 2026)
**Author:** Ben Ferenchak + Claude
**Status:** All three methods RETIRED with zero improvements. **Do not reactivate any of them without new structural evidence.** This doc replaces `simulated-annealing-investigation.md`, `tabu-search-investigation.md`, and `vds-enhancements.md` (full plans, smoke logs, and step-by-step build notes remain in git history; per-method algorithmic references remain in `../workers/`).

---

## The shared verdict

Three trajectory metaheuristics — simulated annealing, tabu search with clique-guided candidate generation, and variable-depth (Lin-Kernighan-style) search — were built, tuned, and A/B-tested against the exhaustive engine at the campaign-2 operating point (282 vertices, ~790K mono-8-cliques, balanced-pair move shape). **Combined: millions of iterations, zero improvements, while the concurrent exhaustive fleet produced every stage advance.**

The common diagnosis: at this graph density, progress comes from finding *different basins*, not from smarter/deeper trajectories within the current one. The landscape is too rugged for narrow-trajectory escape — a conclusion later **hardened to proven-optimal status** by the exact windowed-MaxSAT certification (~1.1M windows, zero escapes, `windowed-maxsat-investigation.md`) and by the construction-side finding that even a 28×-better basin (Paley+1 reseed) re-walls the same way (`search-status-2026-06-14.md`).

| Method | Retired | Evidence | Concurrent exhaustive |
|---|---|---|---|
| Simulated annealing | 2026-04-02 (analysis 05-02) | 45K+ iterations, 0 improvements | steady stage advances |
| VDS (Lin-Kernighan-style) | 2026-05-03 | 51,994 attempts / 3 h / 8 workers, 0 improvements, 0 top-50 adds | 6 workers drove all 8 advances (−176) |
| Tabu + clique-guided | 2026-05-06 | 1,351 trajectories / 6.75M iterations / 48.5 h / 4 workers, 0 improvements, 0 negative deltas | 10 workers, −1,430 |

All three worker modes remain in the codebase (compose services at `scale: 0`); re-building is cheap if new structural evidence ever appears.

---

## Simulated annealing

**What failed:** the original `sa.rs` ran 45,000+ iterations across multiple configurations with zero improvements. Root causes, in order: (1) full `get_cliques_comprehensive()` per iteration — ~1000× the cost of incremental delta evaluation, capping total iterations in the thousands; (2) starting temperature 1000 against an observed basin width of ~40 cliques — a meaningless random walk; (3) 2-edge perturbations — the same neighborhood the exhaustive engine had already proven empty.

**The corrected design (never rebuilt, deliberately):** incremental broken/new delta via `CliqueCollection` + seeded Bron-Kerbosch with periodic rebuilds; T₀ ≈ 50 with 0.999 cooling and reheat cycles; 5–20-edge clique-guided perturbations. Estimated 2-day rewrite. It was not pursued because tabu — the *stronger* literature candidate of the same family — subsequently failed decisively with all of those lessons already applied. SA's unique capability (Metropolis acceptance of worsening moves) has no evidence of value at this operating point.

---

## VDS — variable-depth (Lin-Kernighan-style) search

**Final experiment (2026-05-03):** full literature-recommended Phase-3 settings (`max_depth=12`, `top_first_edges=80`, `branching_factor=12`, `worsening_tolerance=500`), 8 VDS workers vs 6 exhaustive for ~3 hours: **51,994 VDS attempts, 0 improvements, 0 top-50 contributions**; the 6 exhaustive workers drove all 8 stage advances (791,100 → 790,924). Per-attempt throughput was fine (~36/min/worker); the search simply found nothing at any depth.

**What it rules out:** the Phase-3 configuration, the "never seriously run" hypothesis, and the "just needs more workers" hypothesis. The one untried knob was a looser worsening tolerance (1,500–3,000) — moot given the decisive primary result.

**Structural diagnosis:** the Ramsey landscape at 282v/~790K cliques lacks the chain-of-compensating-moves structure Lin-Kernighan exploits in TSP; the literature has no notable VDS-for-Ramsey track record (it is dominated by tabu approaches — which also failed here, see below).

**Prior enhancement work (Phases 1–2, shipped before the final experiment):** ranking-cache (the per-depth candidate ranking was static — computing it once saved ~21% runtime) and locality-aware candidate selection at depth ≥ 2 (each flip's candidates drawn from the neighborhood its predecessor disrupted — the core LK principle). Correct fixes; didn't change the outcome.

---

## Tabu search + clique-guided mutation

The most literature-validated technique for Ramsey lower bounds at n > 100 (Exoo's R(3,13)≥59 through R(5,8)≥95 line) — which is exactly why its clean failure matters.

**Build (2026-05-04):** `src/tabu.rs` (615 lines, 11 unit tests), `WORKER_MODE=TABU_CLIQUE_GUIDED`. Balanced-pair moves; candidates = edges of a sampled surviving 8-clique + top-participation pool; edge-level tabu memory with adaptive tenure + aspiration; random-perturbation diversification. Smoke runs surfaced one implementation lesson worth keeping (below) and zero improvements; a defensible retune (tenure 200 → 30, per the narrow-candidate-pool argument and Exoo/Pullan-Hoos practice) preceded the long run.

**Final experiment (2026-05-04 → 05-06):** 4 tabu workers (+10 exhaustive) for 48.5 h: **1,355 runs started / 1,351 finished, ~6.75M iterations, 6,755 diversification cycles — zero improvements and zero locally-negative deltas.** Every trajectory ended `delta_from_initial=0`; no run ever reset its stagnation counter. Concurrent exhaustive: 789,974 → 788,544 (−1,430). Retired per the pre-registered decision matrix.

**What it forecloses:** balanced-pair tabu with clique-guided candidates at tractable parameters (pool ≤ 10, ≤ ~10K iters/run, tenure 30–200) at this operating point. **What it doesn't:** other neighborhood shapes, other candidate policies, fundamentally different method families. The empirical finding is that Exoo-scale tabu wins do not transfer to n=282 at ~790K cliques with balanced-pair moves.

---

## Cross-cutting lessons (the reusable part)

1. **Incremental delta evaluation is mandatory.** Full Bron-Kerbosch recount per move is ~100–1000× too slow. The validated pattern (from the tabu build, reused since):
   ```rust
   // exact per-move delta via edge-seeded BK, no CliqueCollection staleness:
   let destroyed = get_new_cliques(graph, k, &edges);  // pre-flip: mono-cliques containing the edges
   graph.flip_edges(&edges);
   let created = get_new_cliques(graph, k, &edges);    // post-flip
   graph.flip_edges(&edges);                            // restore
   let delta = created - destroyed;
   ```
   The static `CliqueCollection` is fine for *candidate generation* (staleness acceptable) but never for *move scoring*.
2. **Whole-graph cycle prevention ≠ edge-level tabu.** The QM's `processed_graph_hashes` blocks revisiting graphs; tabu memory shapes trajectories. Complementary — and neither substitutes for the other.
3. **Zero-improvement evidence compounds.** Each method was retired on a pre-registered decision matrix, not vibes; three independent families failing identically (plus the later exact-window certification) is what justified the strategic pivot to construction-based seeding (`search-status-2026-06-14.md`).
4. **Algorithmic references for any future re-run:** `../workers/simulated-annealing-worker.md`, `../workers/tabu-clique-guided-worker.md`, `../workers/variable-depth-search-worker.md`. Full original plans/logs: git history of the three replaced docs (removed 2026-07-02).

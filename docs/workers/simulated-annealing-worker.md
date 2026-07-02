# Simulated Annealing (SA) Worker

> **Retired.** SA found zero improvements (45K+ iterations) and was never re-enabled: the "corrected design" (incremental evaluation, basin-scale temperature, larger perturbations) was overtaken by tabu — the stronger technique of the same family — failing decisively with those lessons applied (0 improvements in 6.75M iterations). All three trajectory methods are retired; consolidated record + do-not-reactivate rationale: [`docs/investigations/retired-trajectory-methods.md`](../investigations/retired-trajectory-methods.md). This doc is preserved as the algorithmic reference. Compose service remains at `scale: 0`.

## Overview

The SA worker uses a metaheuristic approach to escape local minima that the exhaustive search cannot reach. Unlike the exhaustive worker which evaluates all 2-edge flips deterministically, SA performs random multi-edge mutations and probabilistically accepts worsening moves early in the schedule, allowing it to traverse through higher-energy states to potentially find better basins.

## High-Level Strategy

Each SA run executes a complete annealing schedule starting from the current stage's base graph:

1. Initialize temperature to `initial_temp`.
2. For each iteration (up to `max_iterations`):
   a. Cool temperature: `temp *= cooling_rate`.
   b. Pick a random number of edge pairs in `[min_pairs, max_pairs]`.
   c. Select that many red edges and blue edges via weighted sampling.
   d. Flip all selected edges simultaneously.
   e. Recount cliques comprehensively (full Bron-Kerbosch).
   f. Accept or reject via the Metropolis criterion.
3. Report the best graph found across all iterations.

### Edge Selection: Clique-Guided Weighted Sampling

Edge selection is not uniform random. The worker builds participation-weighted edge lists from the base graph's `CliqueCollection`:

- Weight = `1 / (clique_participation + 1)`
- Edges involved in fewer cliques get higher weight.
- This biases selection toward edges whose flipping is less disruptive, increasing the chance of finding moves that actually reduce clique count.

Red and blue edges are sampled separately, with equal counts from each (maintaining the total edge count). Sampling is without replacement within each iteration.

### Acceptance Criterion (Metropolis)

- If `delta <= 0` (clique count decreased or stayed same): always accept.
- If `delta > 0` (clique count increased): accept with probability `exp(-delta / temp)`.
- At high temperatures, large worsening moves are accepted frequently, enabling exploration.
- As temperature decreases, only small worsening moves are accepted, converging toward local optima.

### State Management

When a move is accepted, the flipped edges are moved between the red and blue edge lists (red edges become blue, blue become red). Their weights are preserved from the initial computation -- they are not recomputed during the run. This is a deliberate approximation: recomputing the full clique collection after each flip would be prohibitively expensive, and the initial weights provide a stable heuristic throughout the schedule.

### Result Submission

After the schedule completes, if the best graph found improves on the initial state and beats the current top-N threshold, it is submitted as a full graph bitstring (not as edge flips, since SA may apply hundreds of mutations). The worker uses `add_sa_result_to_top_results()` which stores the complete graph state.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `WORKER_MODE` | -- | Set to `SIMULATED_ANNEALING` |
| `SA_MAX_ITERATIONS` | `100000` | Total iterations per annealing schedule |
| `SA_INITIAL_TEMP` | `1000.0` | Starting temperature |
| `SA_COOLING_RATE` | `0.999` | Geometric cooling factor per iteration |
| `SA_MIN_PAIRS` | `2` | Minimum red+blue edge pairs per mutation |
| `SA_MAX_PAIRS` | `5` | Maximum red+blue edge pairs per mutation |
| `TOP_RESULTS_COUNT` | `10` | Size of the top-N sorted set in Redis |

## Typical Configuration (Docker Compose)

```yaml
ramsey-worker-rust-sa:
  image: benferenchak/ramsey-worker-rust:develop
  environment:
    WORKER_MODE: SIMULATED_ANNEALING
    SA_MAX_ITERATIONS: 15000
    SA_INITIAL_TEMP: 1000.0
    SA_COOLING_RATE: 0.999
    SA_MIN_PAIRS: 2
    SA_MAX_PAIRS: 2
    TOP_RESULTS_COUNT: 50
    WORKER_COUNT: 1
  scale: 0  # Currently disabled
```

## Sample Log Output

```
SA starting: vertex_count=282, clique_size=8, initial_cliques=792000, threshold=none,
  max_iterations=15000, initial_temp=1000.00, cooling_rate=0.9990, min_pairs=2, max_pairs=2
SA iter 1/15000: temp=999.00, pairs=2, new_cliques=792412, accepted=true, current=792412, best=792000
SA iter 2/15000: temp=998.00, pairs=2, new_cliques=792285, accepted=true, current=792285, best=792000
...
SA iter 15000/15000: temp=0.00, pairs=2, new_cliques=792301, accepted=false, current=792150, best=792000
SA finished: initial_cliques=792000, best_cliques=792000, improved=false
```

Note: SA logs every iteration, which produces high log volume. Each run takes several minutes for the full schedule.

## Performance Characteristics

- Each iteration in the current implementation requires a full `get_cliques_comprehensive()` call (complete Bron-Kerbosch enumeration), which is the dominant cost.
- At 282 vertices / 8-cliques, each iteration takes ~1-5ms, so a 15,000-iteration schedule runs in ~30-60 seconds.
- SA explores a fundamentally different dimension than exhaustive search: multi-edge mutations with probabilistic acceptance vs. guaranteed-optimal single-pair evaluation.
- The `min_pairs=2` setting ensures SA explores at least 4-edge flips (2 red + 2 blue), since the exhaustive search already covers all 2-edge (1 red + 1 blue) combinations.

## Current Status

SA is **retired** (scale: 0, do not reactivate without new structural evidence — see the banner at the top). The consolidated record (`docs/investigations/retired-trajectory-methods.md`) preserves the corrected-design spec (incremental evaluation via `CliqueCollection`, basin-scale temperature schedule, 5–20-edge perturbations) for any future re-run under a genuinely different regime. The infrastructure is otherwise intact.

## Key Source Files

| File | Purpose |
|------|---------|
| `ramsey-worker-rust/src/sa.rs` | `run_sa()` -- complete SA implementation |
| `ramsey-worker-rust/src/sa.rs` | `build_edge_list()` -- weighted edge list construction |
| `ramsey-worker-rust/src/sa.rs` | `weighted_sample()` -- weighted sampling without replacement |
| `ramsey-worker-rust/src/worker.rs` | `cycle_simulated_annealing()` -- worker loop integration |
| `ramsey-worker-rust/src/algorithm.rs` | `get_cliques_comprehensive()` -- full clique recount used each iteration |
| `docs/investigations/retired-trajectory-methods.md` | Consolidated retirement record (original investigation doc in git history) |

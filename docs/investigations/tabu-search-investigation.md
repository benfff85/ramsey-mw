# Tabu Search + Clique-Guided Mutation — Implementation Plan

**Date:** 2026-05-03 (updated 2026-05-04)
**Author:** Ben Ferenchak + Claude
**Status:** Implemented; smoke-tested locally; A/B against exhaustive pending. See "Implementation Notes" below.

**Related documents:**
- `may-2026-next-steps.md` — Tier 1.1, the proven-technique build this plan implements
- `vds-enhancements.md` — VDS retirement context; tabu replaces VDS as the next algorithmic experiment
- `simulated-annealing-investigation.md` — SA design lessons (incremental evaluation, balanced moves) that apply directly here

---

## Implementation Notes (2026-05-04)

The plan below was followed substantially as written, with one mid-build correction worth recording.

### What was built

- `src/tabu.rs` (615 lines, 11 unit tests) — `TabuConfig`, `TabuRunResult`, `run_tabu`, plus internal `TabuList` and `DestroyedSet` helpers
- Worker integration: `WORKER_MODE=TABU_CLIQUE_GUIDED` slot, `cycle_tabu_search` mirroring `cycle_simulated_annealing`, full bitstring submission to top-N (long trajectories may walk far from base)
- Compose: `ramsey-worker-rust-tabu` service at `scale: 0`, image tag `tabu-test` for local smoke testing (the published `:develop` tag does not yet contain TABU_CLIQUE_GUIDED)

### Mid-build correction: per-pair delta evaluation

The plan specified `evaluate_pair_delta` would use `get_new_cliques_with_limit` for incremental delta. The first build mistakenly used `get_cliques_comprehensive` (full Bron-Kerbosch recount) instead, and the smoke test exposed the cost: a single tabu run with `pool_size=5, max_iterations=5000` did not complete in 14+ minutes on the production graph (282v, ~790K 8-cliques). With `pool_size=20`, full recount per pair would have made each iteration prohibitively expensive.

**Fix:** swapped `evaluate_pair_delta` to use two `get_new_cliques` calls (the no-limit wrapper around `get_new_cliques_with_limit`):

```rust
fn evaluate_pair_delta(graph, clique_size, red, blue) -> i32 {
    let edges = [red.clone(), blue.clone()];
    let destroyed = get_new_cliques(graph, clique_size, &edges); // pre-flip
    graph.flip_edges(&edges);
    let created = get_new_cliques(graph, clique_size, &edges);   // post-flip
    graph.flip_edges(&edges);                                    // restore
    created - destroyed
}
```

**Why this works:** `get_new_cliques_with_limit` is edge-seeded Bron-Kerbosch — it counts only monochromatic cliques containing the seeded edges in their current color. Pre-flip call gives the count of cliques *destroyed* by the move (mono-cliques currently containing those edges); post-flip call gives the count *created* (mono-cliques in the new graph containing those edges in their new color). Difference is exact delta. Same primitive the exhaustive engine uses, orders of magnitude cheaper than full enumeration.

**Why this doesn't introduce staleness issues:** the function operates on the live graph state, not on the static `CliqueCollection`. The CliqueCollection is used only for candidate generation (sampling surviving cliques + ranking by participation), where some staleness is acceptable. Move scoring is exact regardless of trajectory length.

A unit test (`evaluate_pair_delta_matches_full_recount`) verifies the incremental delta exactly equals `(post_flip_count - pre_flip_count)` from a full recount on a small graph.

### Smoke Test Findings (2026-05-04)

All smoke runs were against campaign 2 (~790K cliques on 282 vertices) using the local `:tabu-test` image at scale 1.

| Run | `pool_size` | `max_iter` | `tenure` | `restart_after` | Wall-clock | Improvements |
|-----|-------------|------------|----------|-----------------|------------|--------------|
| 1 (pre-fix) | 5 | 5,000 | 200 | 1,000 | did not complete in 14+ min | — |
| 2 (post-fix) | 5 | 5,000 | 200 | 1,000 | ~9 min | 0 |
| 3 | 5 | 5,000 | 200 | 1,000 | ~9 min | 0 |
| 4 | 10 | 10,000 | 200 | 2,000 | ~18 min | 0 |

**The delta-evaluation fix works.** Run 1's full-recount per pair eval was 100× too expensive; switching to edge-seeded `get_new_cliques` brought per-iteration cost into the right range. After the fix, all runs completed cleanly with zero crashes or anomalies.

**Per-iteration cost is fairly insensitive to pool size.** Both pool=5 and pool=10 ran at roughly 30–110ms/iter — much less variance than the projected 4× from pair-eval count alone. The likely explanation: each diversification triggers a full `get_cliques_comprehensive` recount, and with 5 diversifications per smoke run that recount cost is comparable to the per-iteration scoring cost. This is good news for production scaling — pool=10 with `max_iter=5000` runs in ~10 min, comparable to one stage advance cadence.

**Wider pool didn't change the outcome.** Doubling pool size (4× more pair candidates per iteration) and doubling iteration budget produced no improvements. Three independent runs at smoke settings, on two different active stages, all returned 0 improvements with 5 diversifications each.

**Concurrent context — exhaustive was advancing stages.** During the smoke runs, exhaustive workers advanced campaign 2 from stage 7162 (790,395 cliques) to 7167 (790,332). Tabu found 0; exhaustive contributed all the progress. Same failure pattern as the retired VDS experiment.

### Parameter Retune (2026-05-04, before multi-day run)

The most likely cause of zero improvements at smoke settings: **`base_tabu_tenure=200` was too aggressive**. The classical `sqrt(num_edges)` heuristic for tabu tenure assumes a candidate pool that's a meaningful fraction of the action space. With 39,621 edges total, a tenure of 200 would be fine if the active candidate pool were thousands — but our pool is ~30 edges per iteration (clique-seeded ~28 + top-participation ~10). Tenuring 200 edges across both colors blocks a sizeable fraction of the high-quality candidate space. Exoo's papers and Pullan-Hoos DLS-MC use much shorter tenures (typically 7–50) for problems with similarly-narrow active pools.

| Param | Smoke | Multi-day | Rationale |
|-------|-------|-----------|-----------|
| `pool_size` | 10 | **10** | Practical ceiling — pool=20 was projected at 24 hr/run |
| `max_iterations` | 10,000 | **5,000** | ~10 min/run; bounds stage staleness to one run |
| `base_tabu_tenure` | 200 | **30** | Classical SAT-tabu range; blocks immediate cycles without over-restricting candidate pool |
| `max_tabu_tenure` | 400 | **60** | 2× base, conventional |
| `restart_after` | 2,000 | **1,000** | 5 diversification cycles per run, more aggressive |
| `diversification_pair_count` | 15 | **20** | Larger jolt to escape stuck regions |

### Multi-Day Deployment (2026-05-04)

Compose updated with the retuned settings; fleet split adjusted:

- `ramsey-worker-rust` (exhaustive): 14 → **10 workers**
- `ramsey-worker-rust-tabu`: 0 → **4 workers** (still on the local `:tabu-test` tag)

Total compute unchanged. Persistent monitor watching all 4 tabu workers for `improvements=N≥1`, `Added to top-50` lines, panics, or any error signature, polling every 60s.

**Stage refresh strategy:** the existing dispatcher checks `redis.has_stage_config(stage_id)` before each cycle. When the queue manager advances a stage, the previous stage's `stage_config` key is removed from Redis, the dispatcher's check fails, the worker calls `clear_stage_cache()`, and the next cycle re-fetches the active stage from middleware. Max staleness = one tabu run ≈ 10 minutes. No in-loop polling required.

### Open Questions Going Into the Multi-Day Run

1. **Will tenure=30 unlock improvements?** The retune is the most defensible change to make based on the smoke evidence; if it still produces 0 improvements over a few days, the question shifts from "are we tuning it wrong?" to "does this algorithm shape work at our operating point?"
2. **At what iteration count does tabu typically find its first improvement at our scale?** No literature reference cleanly answers this for a 282-vertex graph at 790K cliques. Several days of running will give us an empirical baseline.
3. **Image durability.** The `:tabu-test` tag exists only on the local Docker daemon. If the stack is recreated and Portainer triggers a pull, those workers will fail to start. Either push the tag to Docker Hub or merge a `:develop` image with the new mode before any prolonged absence.

### Decision Criteria for Multi-Day Run

- **Tabu produces ≥ 1 improvement during the run, at any rate**: continue and consider scaling up. The algorithm works at our scale; tuning becomes the next question.
- **Tabu produces 0 improvements over 48+ hours**: retire alongside VDS. Same evidence, same outcome — neither narrow-trajectory technique escapes basins at this graph density. Update docs and revert fleet to 14 exhaustive.
- **Errors, panics, or container restarts loop**: stop and debug before judging the algorithm.

---

## Goal

Build a `TABU_CLIQUE_GUIDED` worker mode that runs Exoo-style tabu search with clique-guided candidate generation, slotting into the existing worker pipeline alongside `EXHAUSTIVE`, `SIMULATED_ANNEALING`, and `VARIABLE_DEPTH_SEARCH` modes. After implementation, run an A/B against the exhaustive fleet at our current operating point (campaign 2, ~790K cliques on 282 vertices).

This is the most empirically validated heuristic-search technique for Ramsey lower bounds at n > 100 (Exoo's R(3,13)≥59, R(4,10)≥80, R(4,11)≥96, R(4,12)≥106, R(4,13)≥118, R(4,14)≥129, R(5,8)≥95). It is the first algorithmic build since the search left the exhaustive plateau where the prior probability of meaningful gain is high enough to justify the engineering cost.

---

## Why the Combination, Not the Components

Per the Ramsey heuristic-search literature, tabu *and* clique-guided candidate generation must be done together:

- **Clique-guided alone (no tabu memory):** oscillates badly. Flip edge A to break clique X; that creates clique Y; flip edge A again to fix Y (which recreates X). Tight infinite-loop.
- **Tabu alone (no clique guidance):** wastes most candidate evaluations on edges that don't participate in any current bad clique. Moves are costly (delta-evaluation via `get_new_cliques_with_limit` is a real cost) so candidate quality matters.
- **Combination:** tabu memory shapes the *trajectory*, clique guidance shapes the *candidate set*. Both are necessary.

**Orthogonal to existing infrastructure:** the queue manager's `CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT=50` is whole-graph memory at the stage level. Tabu is edge-level memory at the move level. They don't conflict; keep both.

---

## Algorithm Specification

### Move shape

A single tabu move is a **balanced pair**: one red flip + one blue flip, preserving the red/blue edge balance the search requires (we operate on graphs where |red − blue| ∈ {0, 1}). This matches the exhaustive engine's work-unit shape.

### Per-iteration loop

```
state:
    graph                    # current graph being mutated
    clique_collection        # CliqueCollection updated as moves apply
    tabu_red, tabu_blue      # HashMap<(u16,u16), u64>: edge → expiry iteration
    best_graph               # bitstring of best graph seen so far
    best_clique_count
    iter_since_improvement   # for adaptive tenure + restart

per iteration:
    1. Generate candidate red edges:
         - All edges in a randomly sampled surviving k-clique (k = clique_size = 8)
           → pulled via clique_collection.get_random_surviving_clique()
         - PLUS top-N high-participation red edges (N = config.candidate_pool_size, e.g. 20)
         - Filter out anything in tabu_red

    2. Generate candidate blue edges:
         - Top-N high-participation blue edges (likely to NOT create new monochromatic
           cliques when added)
         - Optionally restrict to blue edges incident to vertices touched by the red
           candidates' endpoints (locality)
         - Filter out anything in tabu_blue

    3. Score all (red, blue) pairs:
         - For each pair, compute delta via get_new_cliques_with_limit()
         - Track pair with minimum delta (best move)

    4. Apply best move:
         - Flip both edges in graph
         - Update clique_collection incrementally (or recompute if cheaper)
         - Add red edge to tabu_red with expiry = iter + tenure
         - Add blue edge to tabu_blue with expiry = iter + tenure
         - Periodically prune expired tabu entries

    5. Update best:
         - If new clique_count < best_clique_count, update best_graph and reset
           iter_since_improvement
         - If new clique_count beats Redis top-N threshold, submit to top-N

    6. Adaptive tenure / diversification:
         - If iter_since_improvement > restart_after, perturb graph (random batch
           of balanced flips), clear tabu lists, reset counter
```

### Adaptive tabu tenure

Standard recipe (Glover-style):
- Base tenure: ~`sqrt(num_edges)` ≈ 200 for our 39,621-edge graph
- Increase by 10% when `iter_since_improvement` exceeds threshold
- Reset to base when an improvement is found
- Cap at 2×base to prevent runaway

### Diversification (escape from stuck regions)

If `iter_since_improvement > restart_after` (e.g. 5,000 iterations):
- Apply a random balanced perturbation (e.g. 10–20 balanced pair flips)
- Clear both tabu lists
- Reset `iter_since_improvement = 0`
- Continue search from the perturbed state

### Aspiration criterion

Standard tabu refinement: a tabu move is allowed if it would produce a new global best. This prevents tabu memory from blocking obviously-good moves.

```
if move is tabu but new_clique_count < best_clique_count:
    accept anyway (aspiration)
```

---

## Architecture / Integration

### Where the code lives

New module `src/tabu.rs`, parallel to `src/sa.rs` and `src/vds.rs`. Public API:

```rust
pub struct TabuConfig {
    pub max_iterations: u64,
    pub base_tabu_tenure: usize,
    pub max_tabu_tenure: usize,
    pub restart_after: u64,
    pub candidate_pool_size: usize,
    pub diversification_pair_count: usize,
    pub random_seed: Option<u64>,
}

pub struct TabuRunResult {
    pub best_graph_bitstring: String,
    pub best_clique_count: i32,
    pub edges_to_flip: Vec<WorkUnitEdge>,  // edges differing from base_graph
    pub improved: bool,
    pub iterations_completed: u64,
    pub improvements_found: u32,
    pub diversifications_triggered: u32,
}

pub fn run_tabu(
    base_graph: &Graph,
    clique_size: usize,
    config: &TabuConfig,
    clique_collection: &CliqueCollection,
    threshold: Option<i32>,
) -> TabuRunResult;
```

This signature mirrors `run_sa` and `run_vds` exactly so worker integration is minimal.

### Worker integration

`src/main.rs`:
- Parse `WORKER_MODE=TABU_CLIQUE_GUIDED` flag (third sibling of `sa_mode`/`vds_mode`)
- Parse new env vars: `TABU_MAX_ITERATIONS`, `TABU_BASE_TENURE`, `TABU_RESTART_AFTER`, `TABU_CANDIDATE_POOL_SIZE`, `TABU_DIVERSIFICATION_PAIRS`, `TABU_RANDOM_SEED`
- Log mode + config on startup

`src/worker.rs`:
- New `tabu_mode: bool` field on `Worker` struct
- New `tabu_config: TabuConfig` field
- Add branch in `process_work` cycle dispatcher (currently `if sa_mode → ... else if vds_mode → ... else → counter_based`)
- New method `cycle_tabu_search(stage_id)` modeled after `cycle_variable_depth_search`:
  1. Load stage config from Redis
  2. Build `base_graph` + `CliqueCollection`
  3. Get current top-N threshold
  4. Call `run_tabu(...)` for one budget worth of iterations
  5. If `result.improved` and beats threshold, submit to Redis top-N via `add_to_top_results`
  6. Increment `processed_count` by 1 (one tabu run = one work unit, matching VDS convention)

### Configuration (env vars)

```yaml
ramsey-worker-rust-tabu:
  image: benferenchak/ramsey-worker-rust:develop
  environment:
    RAMSEY_API_URL: http://ramsey-mw:8080/api/ramsey
    RAMSEY_CAMPAIGN_ID: 2
    REDIS_HOST: redis
    REDIS_PORT: 6379
    WORKER_MODE: TABU_CLIQUE_GUIDED
    TABU_MAX_ITERATIONS: 50000        # iterations per work unit
    TABU_BASE_TENURE: 200             # ~sqrt(num_edges)
    TABU_MAX_TENURE: 400              # cap at 2x base
    TABU_RESTART_AFTER: 5000          # iterations without improvement → diversify
    TABU_CANDIDATE_POOL_SIZE: 20      # per-color pool size before pairing
    TABU_DIVERSIFICATION_PAIRS: 15    # pair flips on diversification
    TOP_RESULTS_COUNT: 50
    WORKER_COUNT: 1
  ...
  scale: 0  # toggled to N when running A/B
```

---

## Implementation Steps

Estimated total: **~5–7 days of focused engineering**, breaking down as:

### Step 1 — Add `TabuConfig` + `run_tabu` skeleton (~0.5 day)
- Create `src/tabu.rs` with the struct definitions and a no-op `run_tabu` that returns the input graph unchanged
- Wire into `worker.rs` and `main.rs` so a `TABU_CLIQUE_GUIDED` worker boots cleanly and logs config
- No real algorithm yet — verify the plumbing works end-to-end against a stage

### Step 2 — Candidate generation (~1 day)
- `clique_collection::get_random_surviving_clique() -> Option<&[usize]>` if not already exposed
- Implement red-candidate sampler (clique-edges + top-participation pool)
- Implement blue-candidate sampler (top-participation, optionally locality-restricted)
- Unit tests: candidate set is non-empty when surviving cliques exist; respects tabu filter; deduplicates

### Step 3 — Move scoring + best-move selection (~1 day)
- Reuse `get_new_cliques_with_limit` for delta evaluation per pair
- Score all (red × blue) candidate pairs, return the best
- Unit test: on a hand-constructed K5-ish graph, best move matches exhaustive enumeration over the same candidate set

### Step 4 — Tabu memory + adaptive tenure (~1 day)
- `TabuList` struct wrapping `HashMap<(u16, u16), u64>` with `add`, `is_tabu`, `prune_expired`, `clear`
- Adaptive tenure logic in `run_tabu` main loop
- Aspiration criterion (allow tabu move if new global best)
- Unit tests: tabu correctly blocks repeats; aspiration overrides; tenure adapts

### Step 5 — Diversification + main loop integration (~0.5 day)
- Random balanced perturbation (N pair flips) when `iter_since_improvement > restart_after`
- Clear tabu, reset counter, continue
- Unit test: diversification increments counter and produces a different graph state

### Step 6 — CliqueCollection update strategy (~1 day)
- **Decision point:** does `CliqueCollection` support efficient incremental update on edge flip, or does each move require a full recount?
- If incremental update is cheap: do that
- If full recount is required: profile to see if it dominates runtime; if so, recount only every N iterations and accept staleness (Exoo's papers suggest this is fine for tabu)
- This is the highest-uncertainty step; budget extra time

### Step 7 — Integration test against a real stage (~0.5 day)
- Pull current `base_graph` from MySQL
- Run `run_tabu` against it with production-like config but small `max_iterations` (e.g. 1,000)
- Verify: no panics, no stale clique counts, `result.improved` flag matches actual `get_cliques_comprehensive` recount
- Compare to exhaustive engine's behavior on the same starting graph

### Step 8 — Compose service definition + Portainer deploy (~0.5 day)
- Add `ramsey-worker-rust-tabu` service to `docker/main/ramsey-compose.yml`
- Initially `scale: 0`
- Push compose to Portainer
- Verify image pulls, container starts, logs look clean

### Step 9 — A/B run + analysis (~1–2 days observe, separate from build time)
- Bump `ramsey-worker-rust-tabu` to scale 4–6, reduce `ramsey-worker-rust` (exhaustive) by the same amount to keep total compute constant
- Run for 24–48 hours
- Decision criteria below

---

## Testing Strategy

### Unit tests (in `src/tabu.rs`, mirroring patterns from `vds.rs`)

- `tabu_list_blocks_repeats`
- `tabu_list_aspiration_overrides`
- `tabu_tenure_adapts_with_no_improvement`
- `candidate_pool_excludes_tabu_edges`
- `candidate_pool_includes_clique_edges`
- `best_move_minimizes_delta_on_known_graph`
- `diversification_changes_graph_state_and_clears_tabu`
- `run_tabu_terminates_within_budget`

### Integration tests

- Run `run_tabu` with a tiny budget (e.g. 100 iterations) against a 5-vertex graph; verify it produces a valid balanced graph
- Property test: any state reached by tabu is balanced (|red - blue| matches initial parity)

### End-to-end smoke test

After Step 7, run a single tabu cycle in dev against stage 7130's graph. Verify:
- Worker logs show iteration progress
- `processed_count` increments in Redis
- If improvement found: appears in `best_results:7130` sorted set
- No panic, no Redis or HTTP errors

---

## A/B Run Configuration

Mirror the VDS Phase 3 experiment's structure:

| Pool | Image | Count | Workers per host |
|------|-------|-------|------------------|
| `ramsey-worker-rust` (exhaustive) | `:develop` | reduce 14 → 8–10 | 1 each |
| `ramsey-worker-rust-tabu` | `:develop` | scale 0 → 4–6 | 1 each |

Total compute unchanged. Run for **24–48 hours** alongside exhaustive, log every improvement attribution by container.

### Metrics to capture

From per-container logs (`docker logs --since 24h`):
- Total tabu iterations completed (sum across the tabu fleet)
- `Added to top-50` count attributable to tabu vs. exhaustive
- Improvements per worker per hour (rate comparison)
- Diversifications triggered per worker (proxy for "stuck rate")
- Mean wall-clock per tabu run

From MySQL:
- Stage advances during the experiment window
- Clique-count drop attributable to the run

### Decision matrix

| Outcome | Action |
|---------|--------|
| Tabu per-worker improvement rate ≥ exhaustive | Scale tabu up further; reduce exhaustive correspondingly |
| Tabu rate is 50–100% of exhaustive | Hold split, run another week to tighten the comparison |
| Tabu rate is 10–50% of exhaustive | Tune one of: candidate pool size, tenure, or restart_after; run another 24h |
| Tabu rate < 10% of exhaustive, or zero improvements | Investigate before retiring — could be incremental-update bug, candidate pool too small, or the algorithm genuinely doesn't help. Don't retire silently like a failed VDS run; debug first since the literature predicts this should work. |

### What we expect, based on literature

Exoo's tabu search at comparable sizes typically produces improvements at **~0.5–2× the rate** of exhaustive search per worker. Expect tabu to be at least competitive; if it's dramatically worse, suspect implementation bug rather than algorithm failure.

---

## Open Questions / Risks

1. **CliqueCollection incremental update.** The biggest uncertainty in the build. If it's not supported, full recount per iteration may dominate runtime. Spike on this in Step 6 before committing to the full schedule.

2. **Pair-shaped vs single-shaped moves.** Exoo's papers operate on single-edge flips because they search for graphs with 0 cliques (where edge balance is unconstrained). Our problem requires balanced graphs, so we use pair moves. This is a design choice that diverges from the literature; we may need to validate empirically that pair-shaped tabu inherits the same properties.

3. **Iteration budget per work unit.** Set to 50,000 in the initial config. Too small means Redis I/O overhead dominates; too large means a stage advance during a tabu run wastes the unfinished work. The right value depends on per-iteration cost (TBD in Step 6 profiling).

4. **Surviving-clique sampling efficiency.** `clique_collection.get_random_surviving_clique()` needs to be O(1) or at worst O(log n). If not, sampler dominates. Worth checking before Step 2.

5. **Cross-platform RNG determinism.** For reproducibility in unit tests, use `StdRng::seed_from_u64` like VDS does.

---

## Definition of Done

- [ ] `src/tabu.rs` exists with `run_tabu`, `TabuConfig`, `TabuRunResult`, and unit tests covering the items in the testing strategy
- [ ] `WORKER_MODE=TABU_CLIQUE_GUIDED` boots cleanly and runs at least one cycle against a real stage in dev
- [ ] `ramsey-worker-rust-tabu` service defined in compose, deployable to Portainer
- [ ] `cargo test --lib` passes (no regressions in existing tests)
- [ ] A/B run executed for ≥ 24 hours with metrics captured in this doc
- [ ] Decision recorded: scale up, hold, tune, or debug

---

*Generated for the Ramsey project — May 2026*

# Variable-Depth Search (VDS) Worker

## Overview

The VDS worker performs structured, guided tree search through sequences of edge flips, inspired by the Lin-Kernighan heuristic for TSP. Unlike the exhaustive worker (which evaluates all 2-edge flips) or SA (which makes random jumps), VDS chains together multi-flip sequences where each subsequent flip targets the local neighborhood disrupted by prior flips.

VDS is designed to complement the exhaustive search by exploring the 3+ edge flip space that exhaustive workers cannot reach. With `start_depth=3`, VDS begins every run from a random 3-flip starting point, ensuring it only explores territory that no other worker type covers.

## High-Level Strategy

Each VDS run follows this structure:

1. **Rank edges** by clique participation score (how many cliques each edge belongs to). Take the top `top_first_edges` as the candidate pool.
2. **Sample** `branching_factor` random first edges from this pool.
3. For each sampled first edge:
   a. **Build a random prefix** of `start_depth - 1` additional flips by picking one random neighborhood edge at each level.
   b. **Begin branching search** from the prefix endpoint, exploring up to `max_depth` total flips.
   c. At each depth, select `branching_factor` candidates from the local neighborhood (edges incident to vertices already touched by the flip sequence).
   d. **Prune** branches where the cumulative clique count worsens by more than `worsening_tolerance` relative to the prefix endpoint.
   e. **Track** the best sequence that improves on `base_clique_count`.
4. **Verify** any improvement via comprehensive Bron-Kerbosch recount before submission.

### The Prefix: Why start_depth Matters

The exhaustive workers already evaluate every possible 2-edge flip. Without `start_depth`, VDS would redundantly search the same 1- and 2-flip space. By setting `start_depth=3`:

- Depth 1: one random first edge from the top-200 pool (no branching)
- Depth 2: one random neighborhood edge (no branching)
- Depth 3-8: branching search begins, with `branching_factor` candidates per level

Each run starts from a different random 3-flip state, then searches for additional flips that bring the total clique count below the original base. The prefix path is different every run, ensuring broad coverage of the 3+ flip space.

### Randomized Candidate Selection

Diversity between runs is achieved through two mechanisms:

1. **First-edge sampling**: The top `top_first_edges` (200) edges are ranked by global clique participation, shuffled, and `branching_factor` (15) are sampled per run. Different first edges are tried each run.

2. **Neighborhood candidate shuffling**: At each search depth, `get_neighborhood_candidates()` collects all edges incident to vertices touched by the current flip sequence, ranks them by surviving clique participation, takes a 3x quality-filtered pool, shuffles it, and returns `branching_factor` candidates. This ensures each run explores different subtrees while keeping candidates focused on high-participation edges.

### Worsening Tolerance and the Tolerance Base

The `worsening_tolerance` parameter controls how far the search can deviate above the tolerance baseline before a branch is pruned. Crucially, the tolerance is measured from the **cumulative clique count at the end of the prefix** (not the original base graph count). This is important because:

- The random 2-flip prefix may put the count significantly above the original base.
- If tolerance were measured from the original base, most branches would be immediately pruned.
- By measuring from the prefix endpoint, the search can explore the local landscape around each random 3-flip state.

Improvements are still tracked against the **original** `base_clique_count` -- a result is only reported if the full flip sequence (prefix + search) produces fewer cliques than the unmodified base graph.

### Incremental Clique Counting

VDS uses the same incremental delta computation as the exhaustive worker:

```
delta = -broken_cliques + new_cliques
```

Where `broken_cliques` are read from the `CliqueCollection` (adjusted for previously destroyed cliques in the sequence) and `new_cliques` are computed via seeded Bron-Kerbosch on the flipped graph. The graph is flipped and unflipped for each evaluation without modification to the CliqueCollection, enabling backtracking.

A `destroyed` set tracks which cliques from the original CliqueCollection have been invalidated by prior flips in the sequence, ensuring the broken count is not double-counted.

### Verification

When an improvement is found, VDS performs a comprehensive recount (`get_cliques_comprehensive()`) on the fully-flipped graph to verify the incremental delta tracking was accurate. If there is a mismatch (which would indicate a bug in the destroyed-set tracking), the result is discarded and logged as a verification failure.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `WORKER_MODE` | -- | Set to `VARIABLE_DEPTH_SEARCH` |
| `VDS_MAX_DEPTH` | `4` | Maximum total flips in a sequence (including prefix) |
| `VDS_TOP_FIRST_EDGES` | `500` | Size of the first-edge candidate pool (ranked by participation) |
| `VDS_BRANCHING_FACTOR` | `20` | Candidates explored per depth level + first edges sampled per run |
| `VDS_WORSENING_TOLERANCE` | `100` | Max allowed worsening from the tolerance base before pruning |
| `VDS_START_DEPTH` | `1` | Depth at which branching begins (prefix edges are random, unbranched) |
| `VDS_RANDOM_SEED` | None | Fixed seed for reproducibility (omit for OS entropy) |
| `TOP_RESULTS_COUNT` | `10` | Size of the top-N sorted set in Redis |

## Typical Production Configuration (Docker Compose)

```yaml
ramsey-worker-rust-vds:
  image: benferenchak/ramsey-worker-rust:develop
  environment:
    WORKER_MODE: VARIABLE_DEPTH_SEARCH
    VDS_MAX_DEPTH: 8
    VDS_TOP_FIRST_EDGES: 200
    VDS_BRANCHING_FACTOR: 15
    VDS_WORSENING_TOLERANCE: 1000
    VDS_START_DEPTH: 3
    TOP_RESULTS_COUNT: 50
    WORKER_COUNT: 1
  scale: 1
```

## Sample Log Output

```
[2026-05-02T14:49:06.025Z] VDS starting: vertex_count=282, clique_size=8, base_cliques=792000,
  max_depth=8, top_first_edges=200, branching_factor=15, worsening_tolerance=1000, start_depth=3
[2026-05-02T14:49:13.344Z] VDS finished: no improvement found, elapsed_ms=7317,
  nodes_visited=8610, branches_pruned=8018
[2026-05-02T14:49:13.385Z] Processed 1 work items in 8426ms
```

When an improvement is found:
```
VDS verified: base=792000, final=791995, delta=-5, sequence_len=4,
  elapsed_ms=12345, nodes_visited=9500, branches_pruned=8800
```

## Performance Characteristics (Observations)

After several thousand exploratory runs with the production settings below:

| Metric | Value |
|--------|-------|
| Avg search time per run | ~8.2s |
| Avg nodes visited per run | ~7,340 |
| Avg branches surviving pruning | ~500 (out of ~7,340 visited) |
| Prune rate | ~93% |
| Distinct `nodes_visited` values | 476+ across observed runs |
| Runs per minute | ~7 |
| Improvements found | 0 at depth 4 with the static-candidate baseline |

The high diversity in `nodes_visited` values (476 distinct values across 7,000 runs) confirms that each run explores genuinely different territory. The consistent ~93% prune rate indicates the tolerance is well-calibrated: selective enough to focus computation but permissive enough for meaningful depth exploration.

## Relationship to Other Worker Types

| Dimension | Exhaustive | SA | VDS |
|-----------|-----------|-----|-----|
| Search space | All 2-edge flips | Random multi-edge jumps | Guided multi-edge sequences |
| Coverage | Complete (for 2-flip) | Stochastic | Stochastic |
| Depth | 2 edges | N edges (random) | 3-8 edges (structured) |
| Selection | Deterministic enumeration | Weighted random | Participation-ranked + random |
| Acceptance | Best result wins | Metropolis criterion | Any improvement over base |
| Unique value | Guarantees no 2-flip improvement is missed | Can escape local minima via temperature | Finds structured multi-flip improvements in local neighborhoods |

VDS occupies the middle ground: more structured than SA (each flip builds on the previous one's neighborhood) but capable of exploring deeper than exhaustive search. The `start_depth=3` setting ensures zero overlap with exhaustive workers.

## Tuning Guidance

- **`worsening_tolerance`**: Higher values allow deeper exploration but increase per-run time. At current optimization depth, 1000 gives ~93% prune rate and ~8s runs. Values above 2000 may cause runs to exceed 20s.
- **`top_first_edges`**: Larger pools give more diverse starting points. 200 is a good balance; above 500 includes low-participation edges that are unlikely to lead to improvements.
- **`branching_factor`**: Controls both first-edge sampling and per-level branching. 15 gives ~7 runs/minute. Increasing to 20-25 would deepen search but reduce throughput.
- **`max_depth`**: 8 is generous; most productive exploration happens at depths 3-5. The worsening tolerance naturally limits effective depth.
- **`start_depth`**: Should be >= 3 when running alongside exhaustive workers to avoid redundant coverage. Setting to 4+ would narrow the search further but skip potentially valuable 3-flip combinations.

## Key Source Files

| File | Purpose |
|------|---------|
| `ramsey-worker-rust/src/vds.rs` | `run_vds()` -- main entry point, prefix building, first-edge sampling |
| `ramsey-worker-rust/src/vds.rs` | `build_prefix_and_search()` -- random prefix construction before branching |
| `ramsey-worker-rust/src/vds.rs` | `search_depth()` -- recursive branching search with tolerance pruning |
| `ramsey-worker-rust/src/vds.rs` | `get_neighborhood_candidates()` -- locality-aware candidate selection with 3x pool shuffling |
| `ramsey-worker-rust/src/vds.rs` | `rank_edges_by_participation()` -- global edge ranking for first-edge pool |
| `ramsey-worker-rust/src/vds.rs` | `compute_delta()` -- incremental clique count change for a single flip |
| `ramsey-worker-rust/src/worker.rs` | `cycle_variable_depth_search()` -- worker loop integration |
| `docs/vds-enhancements.md` | Earlier VDS design notes and enhancement ideas |

# Exhaustive Worker

## Overview

The exhaustive worker is the primary workhorse of the Ramsey system. It systematically evaluates every possible 2-edge flip (one red edge + one blue edge) on the current base graph, looking for flips that reduce the total clique count. This is a brute-force approach that guarantees no 2-edge improvement is missed within a given stage.

The exhaustive search is what drives stage progression: once all pairs have been evaluated, the queue manager advances to the next stage using the best result found.

## High-Level Strategy

Given a graph with R red edges and B blue edges, there are R x B possible (red, blue) edge pairs. Each pair represents a candidate mutation: flip the red edge to blue and the blue edge to red simultaneously. The worker evaluates each pair by computing how the flip changes the total clique count using the Bron-Kerbosch algorithm.

The search space is divided across multiple workers using Redis atomic counters. Each worker claims a batch of index ranges, converts indices to edge pairs via the enumeration strategy, and processes them independently. This allows horizontal scaling with no coordination overhead beyond the atomic counter.

### Work Distribution

1. The queue manager seeds a Redis key `stage_work_index:{stageId}` with the starting index (0).
2. Each worker atomically increments this counter by its `WORK_UNIT_FETCH_COUNT` (batch size) via `INCRBY`.
3. The returned value defines the exclusive range `[prev, prev + batch_size)` of work unit indices.
4. The worker converts each index to an edge pair using the configured enumeration strategy.
5. When the counter exceeds `total_pairs`, the stage is fully claimed.

### Enumeration Strategies

Edge pairs can be enumerated in different orders, controlled by `WORK_ENUMERATION_STRATEGY`:

- **BASIC** -- Simple row-major iteration. Red edges and blue edges are listed in vertex-pair order. Index `i` maps to `red[i / blue_count]` + `blue[i % blue_count]`. No prioritization.
- **DUAL_EDGE_CARDINALITY** (current default) -- Both red and blue edge lists are sorted by cardinality descending before enumeration. Cardinality is the count of same-colored edges adjacent to the edge's endpoints. High-cardinality edges participate in more cliques, so evaluating them first means improvements are found earlier in the stage, which benefits the top-N tracking.

### Clique Evaluation

For each edge pair, the worker computes the resulting clique count using an incremental approach:

1. Look up how many existing cliques contain the edges being flipped (the "broken" count) from the pre-built `CliqueCollection`.
2. Compute `created` — the cliques the flip forms. **Since 2026-07-26 this is normally derived
   algebraically rather than traversed** (see below).
3. Compute: `new_total = base_total - broken + created`.

### Deriving `created` without traversing (`hoist.rs`)

A pair move flips red edge `r` and blue edge `b`, so `R' = (R ∪ {b}) \ {r}` and
`B' = (B ∪ {r}) \ {b}`. Only a newly-coloured edge can be in a new clique, giving

```
created = (C_b − X) + (D_r − Y)
```

where `C_b` / `D_r` depend on ONE edge and the base graph — exactly what flipping that edge alone
would create — and `X` / `Y` count cliques containing BOTH edges. A clique containing both must
contain every vertex of both, so all its internal pairs share a colour: `X > 0` needs the cross
pairs all red, `Y > 0` needs them all blue, and when they are mixed BOTH vanish. **That fast path
is ~86% of the pair space and needs no traversal at all.** The correction, when needed, seeds on
the 3–4 forced vertices and needs no graph mutation either.

`C_b` / `D_r` are memoised per base graph and filled co-operatively: each worker computes one
slice of the edge space and publishes it to Redis, keyed by GRAPH id, so a fleet pools the work.
The table is a memo, so a missing slice costs time and never correctness.

The seeded Bron-Kerbosch path (`get_new_cliques_with_limit`) remains as the fallback and as the
reference the hoisted path is validated against — bit-identical over a full 392M-unit stage. Set
`HOIST_ENABLED=false` to run on it exclusively.

The `CliqueCollection` is built once per stage by enumerating all cliques of the target size (8-cliques for 282 vertices) using a comprehensive Bron-Kerbosch pass. It provides O(1) lookup of which cliques contain a given edge, enabling the incremental formula above.

### Early Termination

When `PUBLISH_RESULTS=false` (the default for local deployments), the worker uses early termination during Bron-Kerbosch. If the new clique count exceeds the current top-N threshold, enumeration stops immediately. This dramatically reduces computation for clearly-bad mutations -- the worker only does full counting for mutations that have a chance of being competitive.

On the hoisted path there is nothing to terminate early: `created` is computed exactly from table
lookups, so the abort the kernel would have taken becomes a single comparison. The bound-skip that
precedes both paths (reject from the per-edge counts alone, before any evaluation) still applies
and does most of the work mid-descent.

### Top-N Result Tracking

Results are tracked in a Redis sorted set `best_results:{stageId}` of size `TOP_RESULTS_COUNT` (default 50). The queue manager reads this set when deciding the best result for stage advancement.

> **Semantics since 2026-06-19 (best-novel cache):** the insert Lua rejects graphs already in the global `processed_graph_hashes` set (`SISMEMBER` at insert time), so `best_results` holds only *novel* graphs; and the early-termination threshold reads **slot 0** (the best novel result) rather than the worst entry — only record-breakers insert. This tightened threshold produced a ~2.8–3× stage-throughput gain and removed "all top-N visited" dead ends. Design + shipped outcome: `docs/investigations/best-novel-threshold-plan.md` (worker PR #69, QM PR #73).

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `WORKER_MODE` | `EXHAUSTIVE` | Omit or set to `EXHAUSTIVE` for this mode |
| `WORK_UNIT_FETCH_COUNT` | `50000` | Batch size per Redis claim |
| `WORK_UNIT_PUBLISH_COUNT` | `50000` | Batch size for MySQL result submission |
| `WORK_UNIT_POLL_FREQ` | `1000` | Poll interval (ms) when no work available |
| `PUBLISH_RESULTS` | `true` | Whether to write results to MySQL (set `false` for top-N only mode) |
| `TOP_RESULTS_COUNT` | `10` | Size of the top-N sorted set in Redis |
| `WORKER_COUNT` | `$(nproc)` | Number of worker processes per container (Dockerfile-level, defaults to CPU count) |

## Typical Production Configuration (Docker Compose)

```yaml
ramsey-worker-rust:
  image: benferenchak/ramsey-worker-rust:develop
  environment:
    RAMSEY_API_URL: http://ramsey-mw:8080/api/ramsey
    RAMSEY_CAMPAIGN_ID: 2
    REDIS_HOST: redis
    REDIS_PORT: 6379
    WORK_UNIT_FETCH_COUNT: 250000
    PUBLISH_RESULTS: "false"
    TOP_RESULTS_COUNT: 50
    WORKER_COUNT: 1
  scale: 14
```

## Sample Log Output

```
[2026-05-02T14:37:17.343Z] Processed 250000 work items in 285444ms
[2026-05-02T14:38:13.528Z] Processed 250000 work items in 56184ms
[2026-05-02T14:48:22.651Z] Added to top-50 results for stage 7065: clique_count=791944
[2026-05-02T14:48:25.932Z] Added to top-50 results for stage 7065: clique_count=791942
```

The first batch of a new stage takes longer (~285s) because it includes building the graph and clique collection. Subsequent batches process in ~55-65s. "Added to top-50" lines appear when a mutation beats the current worst in the top-N set.

## Performance Characteristics

- At 282 vertices with the DUAL_EDGE_CARDINALITY strategy, the total search space per stage is ~392.5 million edge pairs.
- With 14 workers each claiming 250K batches, a full stage exhaustion takes ~80–90 minutes.
- The first batch per stage is ~4-5x slower due to clique collection construction.
- Graph and clique collection are cached across stages when the base graph is reused.

## Key Source Files

| File | Purpose |
|------|---------|
| `ramsey-worker-rust/src/worker.rs` | `cycle_counter_based()` -- main work loop |
| `ramsey-worker-rust/src/enumeration.rs` | Work index to edge pair conversion, enumeration strategies |
| `ramsey-worker-rust/src/algorithm.rs` | `get_new_cliques_with_limit()` -- Bron-Kerbosch with early termination |
| `ramsey-worker-rust/src/clique_collection.rs` | Per-edge clique participation index |
| `ramsey-worker-rust/src/redis_client.rs` | `claim_work_range()`, `add_to_top_results()` |
| `ramsey-worker-rust/src/graph.rs` | `Graph` struct, `flip_edges()`, bitstring serialization |
| `ramsey-worker-rust/src/bitset.rs` | `BitMatrix` for fast adjacency lookups |

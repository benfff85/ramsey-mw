Ramsey Project - Enhancement Roadmap

# Ramsey Enhancement Roadmap
Practical improvements for finding R(8,8) lower bounds — optimized for local MacBook development with occasional burst compute on vast.ai
Low Budget • Local-First • Occasional Cloud Bursts

## Current State Assessment
Where the project stands today
Architecture
Solid
Clean 4-service separation, counter-based Redis work distribution, well-designed Bron-Kerbosch with early termination
Rust Worker
Optimized
BitMatrix on stack (no heap), LTO fat, single codegen unit, native CPU targeting, early termination with threshold
Search Strategy
Exhaustive
Brute-force all edge pairs per stage. Works but may plateau once easy improvements are found
Test Coverage
0%
No tests in any service. Algorithm correctness relies entirely on manual validation
Graph Size
R(8,8) / 288v
41,328 edges, ~427M edge pairs per stage (R×B, not C(41328,2)). Counter-based distribution handles this well
Local Efficiency
Overhead
Full Docker stack (MySQL + Redis + 14 workers) is heavy for MacBook. Simpler local mode would help iteration speed

## Priority Matrix
Enhancements ranked by impact-to-effort ratio under your budget constraints
Priority | Enhancement | Impact | Effort | Cost |
P1 | X-tracking + pivot selection in seeded BK | Speedup on deep recursion; requires restoring X | ~2 days | $0 |
P1 | Multi-edge flip mutations (3+ edges) | Escape local minima, find better graphs | ~2 days | $0 |
P1 | Simulated annealing / tabu search mode | Find improvements without exhaustive search | ~3 days | $0 |
P2 | Lightweight local mode (no Docker) | Faster iteration, lower resource overhead | ~2 days | $0 |
P2 | Checkpoint/resume for long runs | Never lose progress on crash or reboot | ~1 day | $0 |
P2 | vast.ai deploy script + spot interruption handling | Cheap burst compute when needed | ~1 day | $1-5/run |
P2 | Core algorithm tests | Confidence in correctness, catch regressions | ~1 day | $0 |
P3 | Parallelism within single worker process | Better CPU utilization per container | ~2 days | $0 |
P3 | Graph symmetry exploitation | Reduce search space by automorphism group | ~5 days | $0 |
P3 | Result analytics and heatmaps | Understand which edges matter most | ~2 days | $0 |

## 1 Algorithm Improvements
The highest ROI changes — make each CPU cycle count for more

### Pivot Selection Requires X-Tracking in Seeded BK
$0 Rust Algorithm
**Note: **Pivot selection cannot be added to `bron_kerbosch_count_no_x_with_limit `as-is — it would silently undercount cliques. The reason: the no-X variant achieves correctness by clearing `p.clear(v) `after each branch, so subsequent siblings don't re-count cliques involving `v `. Pivot selection prunes candidates from P by skipping vertices in N(pivot), but those vertices may form valid k-cliques among themselves that don't include the pivot. Without X to recover them, they're missed.
- **Concrete failure case: **R={v1,v2}, P={a,b,c} forming a triangle, clique_size=4. With pivot b, P\N(b)={b} so only b is branched. Finds {v1,v2,a,b} and {v1,v2,b,c} but **misses {v1,v2,a,c} **— a k-clique entirely within N(b) that doesn't contain b.
- **To do this correctly: **Restore X tracking to the seeded BK function (add a `x: &mut BitMatrix `parameter, maintain it through recursion). With X properly maintained, the pivot invariant holds: any k-clique not containing pivot must include a vertex from P\N(pivot), and cliques covered by X have already been counted.
- **Cost: **One extra BitMatrix (40 bytes) per stack frame, plus X-intersection operations per recursion. Given the seeded search already has a small P (common neighbors of the flipped edge), the overhead may not be worth it.
- **The existing algorithm `bron_kerbosch_count_inplace `**already tracks X correctly and would support pivot selection — but it's only used in `get_cliques_comprehensive `, not in the hot path.
Speedup
Effort

### Multi-Edge Flip Mutations (3+ Edges)
$0 Rust Algorithm
Currently each work unit flips exactly 2 edges (1 red + 1 blue). This limits the search to a 2-flip neighborhood. Once the system plateaus with no 2-flip improvement, it can only progress via the exhaustion fallback. Adding 3-edge or 4-edge flip modes dramatically expands the reachable neighborhood.
- **3-edge flips: **Choose 2 red + 1 blue or 1 red + 2 blue. Search space grows cubically but finds improvements unreachable by 2-flips.
- **4-edge flips: **2 red + 2 blue. Huge search space but can be sampled stochastically rather than exhaustively.
- **Implementation: **Add a new `WorkEnumerationStrategy `(e.g., `TRIPLE_FLIP `) and a corresponding enumerator. The Bron-Kerbosch algorithm already handles arbitrary edge lists via `flipped_edges: &[WorkUnitEdge] `.
- **Sampling: **For 3+ flips, exhaustive enumeration is infeasible. Use randomized sampling — each worker claims index ranges that map to pseudo-random edge tuples via a deterministic hash.
Impact
Effort

### Simulated Annealing / Tabu Search Mode
$0 Rust Algorithm
The current strategy is exhaustive: enumerate all edge pairs for a stage, find the best, advance. This is thorough but extremely slow when improvements are rare. A stochastic local search can find improvements orders of magnitude faster by exploring promising directions instead of brute-forcing everything.
- **Simulated Annealing: **Start from current best graph. Randomly flip 1-3 edges, accept if clique count improves, accept with decreasing probability if it worsens. Temperature schedule controls exploration vs exploitation.
- **Tabu Search: **Maintain a tabu list of recently flipped edges. Only consider non-tabu moves. This prevents cycling and forces exploration of new regions.
- **Implementation: **New worker mode that runs independently — no Redis counter needed. Each worker does its own random walk. Report improvements back to Redis `best_results `sorted set exactly as today.
- **Hybrid approach: **Run SA workers alongside exhaustive workers. SA finds improvements fast; exhaustive proves optimality of a neighborhood.
Why this matters for R(8,8)
The Ramsey number search landscape has many local minima connected by narrow paths. Exhaustive 2-flip search can get stuck for billions of work units before the exhaustion fallback triggers. SA with temperature restarts can traverse between basins of attraction much faster. Research on Ramsey lower bounds (e.g., Exoo, Radziszowski) has historically relied on SA-like heuristics for exactly this reason.
Impact
Effort

### Exploit Graph Symmetries (Automorphism Pruning)
$0 Rust Algorithm
Many edge pairs produce isomorphic derived graphs. If the base graph has a non-trivial automorphism group, you can reduce the search space by only considering one representative from each orbit of edge pairs under the automorphism action.
- **Approach: **Compute the automorphism group of the base graph at stage start (use nauty/Traces via FFI, or a simpler degree-sequence-based equivalence).
- **Simpler version: **Even without full automorphism computation, grouping edges by their vertex degree pair can skip redundant work. Two edges (u,v) and (u',v') with deg(u)=deg(u') and deg(v)=deg(v') in both colors often produce equivalent results.
- **Estimated reduction: **2-50x depending on graph symmetry. Paley-type constructions (common starting points for Ramsey) have large automorphism groups.
Impact
Effort

### Incremental Clique Counting (Skip Full Bron-Kerbosch)
$0 Rust Algorithm
You already compute `CliqueCollection `and use it for the "broken cliques" optimization. This can be pushed further: when flipping 2 edges, you only need to count cliques that *contain at least one flipped edge *. Your `get_new_cliques_with_limit `already seeds BK from flipped edges, which is good. The next step is to also use the pre-computed clique collection to bound the "new cliques" count before running BK at all.
- **Upper bound filter: **For a pair of flipped edges (e1, e2), compute the maximum possible new cliques by counting common neighbors of e1's and e2's endpoints. If `base_total - broken + upper_bound ≥ threshold `, skip BK entirely.
- **Common neighbor intersection: **Using your BitMatrix, this is just 2-3 AND operations + a popcount. Much cheaper than launching BK.
- **Expected skip rate: **30-70% of work units on dense graphs, each saving the full BK recursion cost.
Speedup
Effort

## 2 Infrastructure & Developer Experience
Run faster locally, deploy smarter to vast.ai

### Lightweight Local Mode (Single Binary, No Docker)
$0 Rust Infra
For local development and testing on your MacBook, the full Docker stack (MySQL + Redis/Dragonfly + middleware + queue-manager + 14 workers + UI) is heavyweight. A standalone Rust binary that embeds the core loop would let you iterate on algorithm changes in seconds.
- **What it looks like: **A `--standalone `mode in the Rust worker that reads a graph from a JSON/text file, runs the search locally, and writes the best result to stdout or a file. No network, no Redis, no MySQL.
- **Use case: **Quick algorithm experiments. Change the BK pivot logic, `cargo run --release -- --standalone --graph input.json `, see results in seconds.
- **Bonus: **Also useful for benchmarking. Currently `perf.sh `queries MySQL after a 600s delay — a standalone mode gives instant feedback.
- **Keep the distributed mode for production runs **(MacBook full-stack or vast.ai). This is additive, not a replacement.
DX
Effort

### Checkpoint/Resume for Long Runs
$0 Rust Infra
A stage with ~427M edge pairs at 50K/batch takes ~8,500 batches. If your MacBook sleeps, reboots, or you need to stop the stack, all in-progress Redis state is lost and the stage restarts from index 0.
- **Approach: **Periodically snapshot the `stage_work_index `and `best_results `sorted set to a local file (JSON or MessagePack). On restart, restore these keys before workers begin.
- **Where: **Queue Manager is the natural place — it already polls these keys. Add a `@Scheduled `task that dumps state every N minutes, and a startup hook that restores if a checkpoint file exists.
- **Alternative: **Use Dragonfly's built-in snapshotting (RDB) — just add `--save 300 1 `to the Dragonfly command to auto-save every 5 minutes. This is the simplest option.
One-line fix for Dragonfly persistence

```
# In ramsey-compose.yml, change:
command: dragonfly --maxmemory 7gb
# To:
command: dragonfly --maxmemory 7gb --save 300 1 --dbfilename dump.rdb --dir /data
# And add a volume:
volumes:
  - dragonfly-data:/data
```

Impact
Effort

### vast.ai Burst Compute Scripts
$1-5/run Infra
vast.ai offers CPU-only instances at $0.01-0.05/hr (spot) for multi-core machines. A 32-core machine for 2 hours costs ~$0.10-0.20. This is ideal for burst runs when your MacBook isn't enough.
- **Deploy script: **A shell script that provisions a vast.ai instance, pulls your Docker images, connects to your existing Redis/MySQL (via Tailscale or WireGuard), and starts N worker containers.
- **Spot interruption: **vast.ai spots can be interrupted. With checkpoint/resume (above), this is fine — workers just resume from the last claimed index.
- **Architecture: **Keep MySQL + Redis on your MacBook (or a cheap always-on instance). vast.ai workers only need outbound HTTP to your middleware and Redis.
- **Budget guide: **

### vast.ai Cost Estimates (CPU-Only Spot)
Machine Type | Cores | Hourly | Per 8hr Run | Workers |
AMD EPYC 7282 | 16 | $0.02 | $0.16 | 16 |
AMD EPYC 7542 | 32 | $0.04 | $0.32 | 32 |
Intel Xeon Gold | 48 | $0.08 | $0.64 | 48 |
2x above (multi-instance) | 96 | $0.16 | $1.28 | 96 |

With 96 workers at 50K units/batch, you process ~5M work units/sec. A full stage (~427M pairs) completes in ~90 seconds.
Impact
Effort

### Optional SQLite Mode for Local Development
$0 Java Infra
MySQL takes ~500MB RAM just idling. For local development (especially with `PUBLISH_RESULTS=false `), the database is only used for campaign/stage/graph metadata — a handful of rows. An embedded SQLite or H2 Spring profile would eliminate the MySQL container entirely.
- **New profile: **`application-local-lite.yml `with H2 in-memory or SQLite file database
- **Benefit: **`docker compose up `needs only Redis + workers. Or even just `mvn spring-boot:run `with embedded everything.
- **When to use MySQL: **Production runs, multi-machine setups, or when `PUBLISH_RESULTS=true `
DX
Effort

## 3 Performance Tuning
Squeeze more throughput from existing hardware

### Rayon Parallelism Within Worker Process
$0 Rust
Each worker container runs a single-threaded compute loop (tokio is used for async I/O but BK runs on one thread). With 14 containers on a MacBook, you have 14 OS processes with separate memory maps, Redis connections, and HTTP clients. A single process using Rayon for the inner loop would be more efficient.
- **Change: **In `cycle_counter_based `, replace the `for idx in start_index..end_index `loop with `rayon::par_iter `. Each BK call is independent and the Graph can be cloned per thread (it's ~14KB on the stack via BitMatrix — cheap to clone).
- **Benefit: **1 process with N threads vs N processes. Saves ~100-200MB RAM from duplicate Redis connections, HTTP clients, and graph caches. Better CPU cache utilization.
- **Caveat: **The `graph.flip_edges() `/ BK / `graph.flip_edges() `pattern mutates the graph. Each thread needs its own copy. Since Graph is Vec<BitMatrix> with Copy BitMatrix, cloning is fast.
- **Docker change: **Instead of `scale: 14 `with `WORKER_COUNT: 1 `, use `scale: 1 `with `WORKER_COUNT: 14 `(or auto-detect nproc with Rayon's default).
Efficiency
Effort

### SIMD-Accelerated BitMatrix Operations
$0 Rust
Your BitMatrix is 5 x u64 = 40 bytes = 320 bits. On Apple M4, NEON can process 128 bits per instruction. The inner loop of Bron-Kerbosch calls `and_assign `, `cardinality `, `is_empty `, and `next_set_bit `millions of times. SIMD can 2-3x these operations.
- **Approach 1 (easy): **Widen BitMatrix to 4 x u128 (or use `std::simd `with nightly Rust). The compiler may auto-vectorize the unrolled loops you already have.
- **Approach 2 (manual): **Use `core::arch::aarch64 `NEON intrinsics for AND, popcount, and leading-zeros on 128-bit vectors. 5 words becomes 3 NEON operations instead of 5 scalar ones.
- **Approach 3 (pragmatic): **Just check if the compiler is already vectorizing. Compile with `RUSTFLAGS="-C target-cpu=native" `and inspect with `cargo asm `. The M4 Max may already be doing this.
How to check current codegen

```
# Install cargo-show-asm
cargo install cargo-show-asm

# Check the hot function
cargo asm --release ramsey_worker_rust::bitset::BitMatrix::and_assign

# Look for NEON instructions (ldp, stp, and.16b, cnt.8b)
# If you see scalar AND/POPCNT, there's room to improve
```

Speedup
Effort

### Batch Redis Operations & Larger Claims
$0 Rust Infra
Each work cycle: 1 Redis call to claim range + 1 call to get threshold + 1 call per top-N submission + 1 call for processed count = at least 4 Redis round-trips per batch. With 14 workers at 50K batch size, that's ~56 Redis calls/sec. Not a bottleneck yet, but optimizing reduces latency between batches.
- **Pipeline the cycle: **Use Redis MULTI/EXEC or pipeline to combine claim_work_range + get_top_results_threshold into a single round-trip.
- **Tune batch size: **With early termination and the threshold optimization, most work units are skipped quickly. Consider increasing `WORK_UNIT_FETCH_COUNT `to 200K-500K to reduce claim frequency.
- **Amortize threshold fetches: **Cache the threshold locally for ~1 second instead of fetching per batch. It changes slowly.
Throughput
Effort

### Binary Graph Encoding
$0 Rust Java
Graph edge_data is stored as a 41,328-character ASCII string of '0' and '1'. This is 41KB per graph. As a packed bitstring, it would be ~5KB. In Redis (stage_config JSON), the graph travels as a 41KB string on every stage init.
- **Encoding: **Base64-encode the packed bits: 41,328 bits → 5,166 bytes → ~6.9KB base64. 6x smaller.
- **Where it matters most: **The `stage_config:{stageId} `JSON blob. Smaller = faster Redis GETs, less memory.
- **DB storage: **Change `edge_data TEXT `to `edge_data MEDIUMBLOB `for packed binary. Or keep TEXT with base64.
- **Low priority: **This is a nice-to-have. The current 41KB is fine for correctness. Only matters at scale or if storing many graphs.
Savings
Effort

## 4 Observability & Analytics
Understand what the system is doing and make smarter search decisions

### Edge Impact Heatmap in UI
$0 Python
Track which edges appear most frequently in the best results across stages. This reveals structural patterns: edges that consistently reduce clique count when flipped are "high-impact" edges. This information can guide future search strategies.
- **Data source: **The `best_results:{stageId} `sorted set already stores `edgesToFlip `for each top result. Aggregate across all stages.
- **Visualization: **A 288x288 heatmap in the Streamlit UI where cell (i,j) color intensity = number of times edge (i,j) appeared in a best result. Use Plotly's `go.Heatmap `.
- **Insight: **Clusters of hot edges might indicate structural bottlenecks. Edges that never appear in improvements might be "frozen" (part of a necessary structure).
- **Actionable: **Feed this data back into the enumeration strategy — prioritize edges that historically produced improvements.
Insight
Effort

### Per-Worker Throughput & Skip Rate Metrics
$0 Rust Python
The current UI shows aggregate throughput. More granular metrics would help tune batch sizes, identify stragglers, and measure the effectiveness of early termination.
- **Metrics to track: **Per-worker: work units/sec, BK calls/sec, skip rate (early termination %), average BK depth. Aggregate: total throughput, estimated time to stage completion.
- **Implementation: **Workers publish metrics to a Redis hash `worker_metrics:{clientId} `every heartbeat. UI reads and displays.
- **Skip rate is key: **If early termination skips 90% of work units, that's a 10x effective speedup. Tracking this tells you whether the threshold optimization is working and when it kicks in (after top-N fills up).
Insight
Effort

### Stage Transition History & Timing
$0 Java Python
Add a log of stage transitions with timing: how long each stage took, what fraction was explored before improvement was found, and whether it was an improvement or exhaustion transition.
- **Data: **Store in the `stage.details `TEXT field (already exists but seems unused): JSON with `duration_seconds `, `work_units_processed `, `exploration_fraction `, `transition_type `(IMPROVEMENT vs EXHAUSTION).
- **UI: **New chart showing stage duration distribution. Short stages = easy improvements. Long stages = approaching a plateau.
- **Actionable: **If stages are consistently hitting exhaustion, it's a signal to switch to SA/tabu search or increase flip count.
Insight
Effort

## 5 Code Quality & Reliability
Protect against regressions and catch bugs before they waste compute time

### Core Algorithm Tests (Rust)
$0 Rust
The most dangerous gap: zero tests on the Bron-Kerbosch implementation, bitset operations, and enumeration strategies. An off-by-one in `BitMatrix::next_set_bit `or a clique counting error could silently produce wrong results for millions of work units.
- **Critical tests (add first): **
  - `BitMatrix `: set/get/clear boundary conditions (bit 0, 63, 64, 287), cardinality, and_assign, invert_all, next_set_bit wraparound
  - `Graph::from_bitstring `: known small graph (e.g., K5) → verify adjacency matrix
  - `get_all_cliques `: K5 has 1 5-clique, Petersen graph has 0 3-cliques, etc.
  - `get_new_cliques_with_limit `: flip an edge in K5, verify clique count change
  - `Enumerator `: verify index_to_edge_pair is bijective (no duplicates, no gaps)
- **Property tests (add second): **Use `proptest `crate. For random graphs, verify `get_all_cliques(graph) == get_cliques_comprehensive(graph) `(different code paths, same result).
Safety
Effort

### Startup Configuration Validation
$0 Rust Java
Missing or invalid environment variables cause silent failures or runtime panics deep in the run loop. Validate everything at startup.
- **Rust worker: **Validate `RAMSEY_API_URL `is reachable, `REDIS_HOST:REDIS_PORT `is connectable, `WORK_UNIT_FETCH_COUNT > 0 `, campaign exists, etc. Fail fast with clear error messages.
- **Java services: **Use `@ConfigurationProperties `with `@Validated `annotations + JSR-303 constraints. Spring will fail startup immediately if a required property is missing.
- **Quick win: **In the Rust worker's `main.rs `, add a `validate_config() `function that runs before entering the work loop.
Reliability
Effort

### Flyway Database Migrations
$0 Java
Schema is managed via manual DDL scripts in `database/ `. If you add a column or index, you need to remember to run it on every environment. Flyway makes this automatic and versioned.
- **Setup: **Add `flyway-core `+ `flyway-mysql `to `pom.xml `. Move DDL scripts to `src/main/resources/db/migration/V1__initial.sql `. Spring Boot auto-runs migrations on startup.
- **Benefit: **Schema changes are versioned, repeatable, and auditable. Crucial if you add new tables (e.g., for analytics).
- **Note: **Low urgency if the schema is stable. But worth doing before adding new features that need schema changes.
Reliability
Effort

## 6 Research Directions
Longer-term ideas that could unlock step-change improvements

### Better Starting Graphs (Algebraic Constructions)
$0 Research
The search quality depends heavily on the initial graph. Current best lower bounds for R(8,8) use algebraically constructed graphs (Paley graphs, circulant graphs, graphs from finite geometry). Starting from a better construction could reduce the number of stages needed.
- **Paley graphs: **Quadratic residues over GF(q). The Paley graph of order 281 (prime) is a natural starting point for R(8,8) ≥ 282.
- **Circulant graphs: **Defined by a connection set S ⊂ Z_n. Systematic search over S can find good starting points.
- **Hybrid: **Use algebraic constructions as seeds, then apply your mutation system to improve them. This is the standard approach in Ramsey number research.
- **Implementation: **A simple Python or Rust script that generates candidate starting graphs and evaluates their clique counts. Feed the best into your existing pipeline.
Potential
Effort

### ML-Guided Edge Selection
$2-10 training Research
Use historical best results to train a lightweight model that predicts which edge flips are likely to reduce clique count. This turns exhaustive search into guided search.
- **Features per edge: **Endpoint degrees (red/blue), common neighbor count, local clustering coefficient, distance to previously successful flips.
- **Model: **A small GNN or even a gradient-boosted tree (XGBoost). Doesn't need to be accurate — even 2x better than random selection doubles effective search speed.
- **Training data: **Your existing work results. Each stage's best results are positive examples; random samples are negatives.
- **vast.ai for training: **A single GPU instance for 1-2 hours (~$0.50-2.00) can train a small model. Inference runs on CPU during worker execution.
- **Integration: **New enumeration strategy that orders edge pairs by predicted improvement. Workers process high-probability pairs first.
Potential
Effort

### Population-Based Search (Genetic Algorithm)
$0 Rust Research
Instead of improving a single graph per stage, maintain a population of K good graphs. Crossover (swap subgraphs between two parents) and mutation (edge flips) produce offspring. Select the best for the next generation.
- **Crossover operator: **For two parent graphs G1, G2, define a random vertex partition V = A ∪ B. Take edges within A from G1, edges within B from G2, edges between A and B randomly from either.
- **Why it helps: **Maintains diversity. Your current system can get stuck in one basin of attraction. A population of 10-50 graphs explores multiple regions simultaneously.
- **Implementation: **A new "GA mode" in the queue manager. Instead of one active stage, maintain K stages with different base graphs. Periodically combine the best into new offspring stages.
- **Budget-friendly: **Population of 10-20 is enough. Each "generation" runs the existing worker pipeline on each member.
Potential
Effort

## Suggested Implementation Order
A practical sequence that maximizes value at each step

```

Phase 1: Quick Wins (1 week)                              Phase 2: Search Improvements (2 weeks)
┌─────────────────────────────────┐                       ┌──────────────────────────────────────┐
│  1. Add Rust algorithm tests    │                       │  5. Simulated annealing worker mode  │
│     - BitMatrix, BK, enumerator │                       │     - New --sa flag in Rust worker   │
│                                 │                       │     - Temperature schedule tuning    │
│  2. X-tracking + pivot for BK   │                       │                                      │
│     - no-X variant can't pivot  │                       │  6. Multi-edge flip (3-edge)         │
│                                 │                       │     - New enumerator + sampling      │
│  3. Dragonfly persistence       │                       │                                      │
│     - One-line compose change   │                       │  7. Edge impact heatmap in UI        │
│                                 │                       │     - Aggregate best_results data    │
│  4. Standalone Rust mode        │──────────────────────>│                                      │
│     - --standalone CLI flag     │                       │  8. vast.ai deploy script            │
└─────────────────────────────────┘                       └──────────────────────────────────────┘
                                                                            │
                                                                            v
Phase 3: Optimization (2 weeks)                           Phase 4: Research (ongoing)
┌─────────────────────────────────┐                       ┌──────────────────────────────────────┐
│  9. Rayon parallelism in worker │                       │  13. Algebraic starting graphs       │
│     - Single process, N threads │                       │      - Paley/circulant generators    │
│                                 │                       │                                      │
│  10. Upper-bound BK skip filter │                       │  14. Population-based / GA search    │
│      - Common neighbor pruning  │                       │      - Multi-graph stage management  │
│                                 │                       │                                      │
│  11. Graph symmetry pruning     │                       │  15. ML-guided edge selection        │
│      - Degree-pair equivalence  │                       │      - Train on historical results   │
│                                 │                       │                                      │
│  12. Worker metrics + skip rate │                       │  16. Larger vertex counts             │
│      - Redis hash per worker    │──────────────────────>│      - Generalize BITSET_SIZE        │
└─────────────────────────────────┘                       └──────────────────────────────────────┘

```

### Budget Summary
Phases 1-3 are entirely free (code changes only). Phase 4 research items involving vast.ai training are $2-10 total. Regular vast.ai burst runs for exhaustive search cost $0.10-1.00 per session depending on scale. A realistic monthly budget of $5-10 gives you significant burst capacity on top of your MacBook's local compute.
Generated for the Ramsey project — March 2026
Based on analysis of ramsey-mw, ramsey-queue-manager, ramsey-worker-rust, and ramsey-ui
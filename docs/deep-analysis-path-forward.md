# Deep Analysis: Path Forward for R(8,8) Lower Bound

**Date:** 2026-04-05  
**Author:** Ben Ferenchak + Claude  
**Status:** Strategic analysis — the exhaustive 2-flip search has reached a hard wall; fundamentally different techniques are needed

**Prior investigations referenced:**
- `simulated-annealing-investigation.md` — Enhancement roadmap; SA proposed, implemented, failed, post-mortem added
- `sat-solver-investigation.md` — SAT/MaxSAT analysis; clique-guided mutation and local MaxSAT proposed (not yet implemented)
- `paley-graph-investigation.md` — Paley graph starting points investigated and closed

---

## Executive Summary

The system has proven, through 18.4 billion exhaustive tests, that **no single 2-edge-flip can improve the current best graph** (980,299 cliques). The search has been stuck at this local minimum for 43 stages. The improvement rate has collapsed from ~22 cliques/stage (early) to effectively zero (now). Continuing the current approach will produce no further progress.

Three avenues have been explored and closed:
- **Exhaustive 2-flip search** — stuck at local minimum, zero improvements in 18.4B tests
- **Simulated annealing** — 45K+ iterations, zero improvements; shut down 2026-04-02 (see `simulated-annealing-investigation.md` for SA proposal, SA outcome post-mortem added there)
- **Paley graph starting points** — Paley(281) can't extend to 288v; Paley(289) has 11x more cliques (see `paley-graph-investigation.md`)

Additionally, the SAT solver investigation (`sat-solver-investigation.md`) analyzed SAT/MaxSAT approaches and concluded direct encoding is infeasible at this scale, but identified two actionable ideas: **clique-guided mutation** (WalkSAT-inspired, Tier 1) and **local MaxSAT window optimization** (Tier 2). Neither has been implemented yet.

The path forward requires techniques that can **escape local minima through coordinated multi-edge moves**, not just search harder within the 2-flip neighborhood.

---

## The Data: What 4,859 Stages Tell Us

### The Complete Improvement Trajectory

| Stage Range | Best CC at End | Drop per 100 Stages | Cumulative Drop | Hamming Dist from Start |
|-------------|----------------|---------------------|-----------------|------------------------|
| 1-1000 | 1,028,376 | ~2,150 | 23,541 | 768 |
| 1000-2000 | 1,014,821 | ~1,350 | 37,096 | 1,166 |
| 2000-3000 | 1,001,378 | ~1,340 | 50,539 | 1,558 |
| 3000-4000 | 986,609 | ~1,477 | 65,308 | 1,940 |
| 4000-4400 | 982,925 | ~1,270 | 68,992 | ~2,080 |
| 4400-4700 | 980,332 | ~775 | 71,585 | ~2,160 |
| 4700-4800 | 980,299 | ~403 | 71,618 | 2,168 |
| **4800-4859** | **980,299** | **~0** | **71,618** | **2,168** |

Key observations:

1. **The improvement rate has been decelerating throughout**, but the collapse at stage ~4800 is qualitatively different — it's not slowing down, it's **stopped**.

2. **The Hamming distance plateaued at 2,168** (~5.25% of edges). The system is no longer making net progress — it's flipping edges back and forth in a cycle of 13 hot positions.

3. **Total clique reduction: 71,618** out of 1,051,917 initial = 6.8% reduction. The remaining 980,299 are structurally entrenched.

### The Local Minimum Basin

Analysis of stages 4810-4830 around the best-ever graph (4816, CC=980,299) reveals the system is trapped in a tiny basin:

- Every consecutive stage differs by **exactly 2 edges** (one red→blue, one blue→red)
- The same ~13 edge positions cycle repeatedly: `(10,152)`, `(11,75)`, `(38,268)`, `(47,86)`, `(80,91)`, `(108,168)`, `(134,275)`, `(152,243)`, `(213,217)`, and a few others
- Clique counts oscillate between 980,299 and 980,339 — a range of only 40
- 30 out of 59 recent transitions are **regressions** (CC goes up) — the exhaustion fallback is picking "least bad" alternatives, not improvements
- **The system hasn't found a genuine all-time-best improvement since stage 4816 — 43 stages and 18.4 billion mutations ago**

### Graph Structural Properties

The best graph (4816, CC=980,299) at 288 vertices:

| Property | Value | Notes |
|----------|-------|-------|
| Red edges | 20,664 | Exactly 50.0% — mathematically optimal |
| Blue edges | 20,664 | |
| Red degree range | 140-146 | Tight, near-constant |
| Red degree mean | 143.5 | = (288-1)/2 exactly |
| Red degree std | 1.16 | Extremely uniform |
| Red triangles/vertex | 4,871-5,246 | Mean 5,077.5, std 71.3 |
| Common red neighbors (adj pairs) | 65-77 | Mean ~71 |
| Common red neighbors (non-adj) | 66-79 | Mean ~72 |
| Cliques vs random expectation | 12.4% | 8x better than random |
| Edges changed from initial | 2,168 | 5.25% of total |
| All vertices modified | Yes | Changes spread across all 288 |

The graph is **extremely uniform** — not strongly regular (lambda/mu vary slightly) but close. The "hot" vertices from oscillation have average triangle counts, average degrees — nothing distinguishes them structurally. The optimization has smoothed out any obvious hotspots.

---

## Why Each Previous Approach Failed

### Exhaustive 2-Flip Search (Current System)

**What it does:** Tests all ~427 million (red_edge × blue_edge) pairs per stage. For each pair, flips both edges, counts the resulting cliques using seeded Bron-Kerbosch with early termination, and records any improvement.

**Why it's stuck:** The graph is at a **2-flip local minimum**. For every possible swap of one red edge and one blue edge, the resulting graph has ≥ 980,299 cliques. This has been exhaustively verified. The only way to improve is through coordinated multi-edge moves where intermediate states may be worse.

**The exhaustion fallback problem:** When no improvement exists, the system picks the "least bad" result from the top-50 sorted set and advances to it as the new base. This creates oscillation — the system bounces between ~980,299 and ~980,339 without making progress. The cycle prevention set (92 graph hashes) prevents exact revisits, but the system finds equivalent-quality alternatives to cycle through.

### Simulated Annealing

**What it did:** Starting from the base graph, randomly flip 2 red + 2 blue edges per iteration (4 edges total), accept improvements always, accept worsenings with probability e^(-delta/temp). Temperature: 1000 → ~0 over 15,000 iterations.

**Why it failed (45K+ iterations, zero improvements):**

1. **Full recount per iteration.** Each iteration calls `get_cliques_comprehensive()` — a full Bron-Kerbosch over all 288 vertices, both colors. This is the same cost as the Python notebook's clique counter. At ~1-5 seconds per iteration, 15K iterations ≈ 4-20 hours. Only 3 SA schedules ran before shutdown.

2. **Temperature schedule wrong for the problem scale.** Initial temp 1000 means accepting delta=+1000 with p≈37%. But the entire oscillation range is only 40 cliques. A temp of 1000 is accepting moves that are 25x the basin depth — the system random-walks wildly without direction. By the time temp drops to ~40 (the basin scale), it's already ~8,000 iterations in and cooling is too fast to find an escape path.

3. **Fixed edge count (4 edges) too small.** SA flipped 2 red + 2 blue, which is essentially the same neighborhood as 2-flip search but evaluated differently. If the 2-flip landscape has no improving moves, random 2-pair flips won't find them either. The neighborhood needs to be MUCH larger (10-50 simultaneous flips) to escape the basin.

4. **No incremental evaluation.** The existing CliqueCollection infrastructure (edge-to-clique mapping) already supports O(1) "broken clique" counting. Combined with seeded BK for "new cliques" (which only examines neighborhoods of flipped edges), each SA iteration could be ~1000x cheaper. This would enable millions of iterations instead of thousands.

### Paley Graphs

**What we tried:** Paley(281) has 0 eight-cliques at 281 vertices. Paley(289) has 11.2M at 289 vertices.

**Why they don't help:** Detailed in `paley-graph-investigation.md`. The 7-vertex gap between 281 and 288 causes an exponential explosion of cliques. Adding even one random vertex to Paley(281) creates 100M+ eight-cliques. Paley(289) minus one vertex still has ~10.9M cliques. The current evolved graph at 980K is already 11x better than Paley(289).

---

## The Core Problem: What Does "980,299 Cliques" Mean?

To understand what's needed, consider the structure of the remaining cliques:

- **980,299 monochromatic 8-cliques** exist in the current graph
- Each involves 8 vertices and C(8,2)=28 edges (all the same color)
- There are 41,328 total edges
- On average, each edge participates in `980,299 × 28 / 41,328 ≈ 664` cliques

**To eliminate all cliques, every one of the 980,299 must have at least one edge flipped.** This is a hitting set problem: find a set of edges to flip such that every clique contains at least one flipped edge, AND no new cliques are created by the flips.

The hitting set alone (ignoring new clique creation) has a lower bound: if each flipped edge breaks ~664 cliques, you'd need at least `980,299 / 664 ≈ 1,476` edge flips. But you've already flipped 2,168 edges from the initial graph and still have 980K cliques — so the "new clique creation" effect is substantial. Each flip breaks some cliques but creates others.

**This is why local search is hard:** the improvement landscape is a narrow ridge where each step must simultaneously break cliques without creating equivalent new ones. At 980K cliques, there's very little room to maneuver.

---

## Recommended Approaches (Ranked)

### 1. Variable-Depth Search (Lin-Kernighan Style) — Highest Priority

**Concept:** Instead of evaluating single 2-flips independently, make a **sequence** of flips, tracking the cumulative effect. Each step in the sequence may worsen the graph, but the total sequence improves it.

**Why this is the best bet:** This is exactly the technique that solved analogous "stuck at local minimum" problems in combinatorial optimization. The TSP (Traveling Salesman Problem) was stuck at 2-opt local minima until Lin-Kernighan introduced variable-depth search in 1973 — this unlocked massive improvements. Graph coloring, SAT solving, and VLSI routing all use variants.

**Algorithm:**
```
start from current graph G (CC=980,299)
for each candidate first flip e1:
    apply e1, compute delta1
    for each candidate second flip e2 (guided by clique structure):
        apply e2, compute cumulative delta1+delta2
        if delta1+delta2 < best_found:
            record this sequence
        for each candidate third flip e3:
            apply e3, compute delta1+delta2+delta3
            if < best_found: record
            ...continue to depth k (typically 5-15)
        undo e3
    undo e2
undo e1
apply the best sequence found
```

**Key advantages:**
- Finds improvements invisible to 2-flip search (e.g., flip A worsens by +3, flip B improves by -7, net -4)
- Uses existing incremental counting infrastructure (CliqueCollection for broken, seeded BK for new)
- Search depth is adaptive — go deeper when promising, prune early when not
- Each sequence evaluation is O(depth × edge_neighborhood) not O(n^k)

**Implementation:**
- New worker mode: `VARIABLE_DEPTH_SEARCH`
- Reuse existing `CliqueCollection` and `get_new_cliques_with_limit`
- Maintain running graph state with flip/unflip (already supported)
- Guided edge selection: prioritize edges with high clique participation (from CliqueCollection)
- Depth limit: start at 5, increase if finding improvements

**Estimated effort:** 3-5 days in Rust. Most infrastructure exists.

**Expected impact:** HIGH. This directly addresses the failure mode (2-flip local minimum) with a proven technique from combinatorial optimization.

### 2. Efficient Tabu Search — High Priority

**Concept:** At each step, make the **best available** 2-flip move, even if it worsens the graph. Maintain a tabu list of recently flipped edges (size ~100-500) to prevent cycling. Track the best-ever graph seen during the walk.

**Why this helps:** The current exhaustion fallback is a crude form of this — it picks the least-bad alternative when stuck. Tabu search formalizes this with memory, preventing the 13-edge oscillation pattern observed in stages 4810-4860. By forcing exploration of new edge positions, it can traverse between basins.

**Key difference from SA:** Tabu search is **deterministic and systematic**. SA accepts random worsening moves; tabu search makes the **best** move available (even if worsening) and uses memory to ensure diversity. For graph coloring problems specifically, tabu search has consistently outperformed SA in the literature.

**Implementation:**
```
graph = best_known (980,299)
tabu_list = []  # (edge, expiry_iteration)
best_ever = 980,299

for iteration in 0..max_iterations:
    # Evaluate all non-tabu 2-flips (or a large sample)
    best_move = find_best_non_tabu_move(graph, tabu_list)
    apply(best_move)
    tabu_list.add(best_move.edges, iteration + tabu_tenure)
    
    if graph.clique_count < best_ever:
        best_ever = graph.clique_count
        save(graph)
```

**Crucial parameter: tabu tenure.** Too short (10) = cycles quickly. Too long (10,000) = can't revisit useful edges. Literature suggests `sqrt(num_edges) ≈ 200` as starting point for graph problems. Can be adaptive: increase tenure when no improvement, decrease when finding improvements.

**Estimated effort:** 2-3 days in Rust.

**Expected impact:** MEDIUM-HIGH. Well-suited for escaping the observed oscillation pattern. May find improvements within a few thousand iterations that exhaustive search missed.

### 3. Incremental SA (Fix the Existing Implementation) — Medium Priority

> **Note:** This is a refinement of the existing `sa.rs` worker mode, not a new idea. The SAT investigation (`sat-solver-investigation.md`) independently identified the same core fix as its "clique-guided mutation" Tier 1 recommendation. The SA failure post-mortem in `simulated-annealing-investigation.md` documents the three root causes. This section consolidates those findings into a concrete fix plan.

**Three fixes needed in `sa.rs`:**

1. **Incremental evaluation** (line 104: replace `get_cliques_comprehensive` call): Use `CliqueCollection.get_count_of_cliques_containing_edges()` for broken cliques + seeded BK for new cliques. ~1000x faster per iteration. This is the same incremental approach the exhaustive workers already use — SA just wasn't wired to it.

2. **Larger perturbations**: Flip 5-20 edges per iteration (not 2). The current config (`SA_MIN_PAIRS: 2, SA_MAX_PAIRS: 2`) is essentially the same neighborhood as the exhaustive 2-flip search. Needs 10-50 edge perturbations to escape the basin.

3. **Appropriate temperature schedule**: Start temp at ~50 (matching the observed oscillation range of 40 cliques), not 1000. Use reheat cycles: cool to temp=5, reheat to 50, repeat. The original temp of 1000 accepted moves 25x the basin depth — essentially random-walking.

**Quantified difference:**
- Original: 15K iterations × ~5 seconds = 20 hours, tiny neighborhood
- Fixed: 10M iterations × ~0.005 seconds = 14 hours, massive neighborhood

**Estimated effort:** 2 days (modify existing `sa.rs`).

**Expected impact:** MEDIUM. Depends on landscape structure beyond the current basin. The incremental CliqueCollection approach has a staleness tradeoff — see Appendix A for details.

### 4. Multi-Start Population Search — Medium Priority

**Concept:** Fork the best graph into 10-50 independent copies. Apply random perturbations (flip 10-50 random edge pairs) to each. Run the existing exhaustive 2-flip search on each fork independently. The diverse starting points land in different basins, one of which may be deeper than the current one.

**Why this helps:** The current search followed a single greedy path from the initial graph. There may be entirely different 288-vertex graphs with < 980K cliques that are unreachable from the current graph via small mutations. Random restarts from perturbed copies explore the broader landscape.

**Implementation:**
- No new algorithm needed — use existing infrastructure
- Create multiple campaigns or stages with different base graphs
- Each base = current best + random 10-50 edge perturbation
- Run standard workers on each

**Key insight:** The perturbation size matters enormously. Too small (2-5 edges) = stay in the same basin. Too large (1000+ edges) = randomize away all structure. The sweet spot is probably 20-100 edges (roughly 0.05-0.25% of total).

**Estimated effort:** 1 day (manual setup, no code changes needed).

**Expected impact:** MEDIUM. Depends on whether better basins exist nearby.

### 5. Clique-Guided Mutation (WalkSAT-Style) — Low-Medium Priority

> **Note:** This was first proposed as Tier 1 in the SAT investigation (`sat-solver-investigation.md`). Included here for completeness and updated with structural analysis findings.

**Concept:** Instead of testing random edge pairs, **target edges that participate in the most cliques**. Pick a random clique, pick the edge in it with highest participation count, flip it along with a compensating edge.

**Why this helps:** The current DUAL_EDGE_CARDINALITY enumerator prioritizes high-cardinality edges, but it still tests all pairs exhaustively. Clique-guided mutation is more focused: it always attacks the densest clique clusters first.

**Implementation:** Already partially built — `CliqueCollection` tracks per-edge clique participation. Need a new worker mode that:
1. Picks a random 8-clique
2. For each of its 28 edges, evaluates the net effect of flipping
3. Applies the best flip (or a weighted random choice)

**Estimated effort:** 1-2 days.

**Expected impact:** LOW-MEDIUM. The structural analysis in this document shows the graph is extremely uniform — degree range 140-146, triangle counts nearly constant, hot vertices are structurally average. This suggests cliques are spread uniformly rather than clustered, which limits the advantage of targeted selection over random. The SAT doc's companion suggestion of **clique overlap analysis** (Tier 1, item 2) would help determine this before investing implementation effort.

### 6. Exhaustive 3-Flip Search (Sampled) — Low Priority

**Concept:** Instead of testing all 427M 2-flip pairs, sample from the 8.8 trillion 3-flip combinations. Use the existing incremental infrastructure to evaluate each sample.

**Why this is low priority:** 3-flip search is strictly more powerful than 2-flip, but 8.8T combinations can't be exhausted. Random sampling hits only a tiny fraction, and without guidance, most samples will be worse than the base. The variable-depth search (Recommendation #1) achieves the same "coordinated multi-edge moves" effect more efficiently through its tree-search structure.

**Estimated effort:** 1 day (new enumerator + sampling strategy).

**Expected impact:** LOW. Random 3-flip sampling is unlikely to find what guided variable-depth search would find.

---

## What Almost Certainly Won't Work

1. **Continuing the current exhaustive 2-flip search.** Proven futile — 18.4B tests, zero improvements. The system will oscillate indefinitely via exhaustion fallback.

2. **Paley or algebraic graph construction.** Thoroughly investigated (`paley-graph-investigation.md`). No known algebraic construction produces fewer than ~980K cliques at 288 vertices. The evolved graph already outperforms all of them.

3. **SAT/MaxSAT on the full problem.** 10^14 clauses, completely infeasible (`sat-solver-investigation.md`). Local MaxSAT on small windows (100-200 edges) remains viable but lower priority than variable-depth search.

4. **SA with current implementation.** The existing `sa.rs` using full recount is ~1000x too expensive per iteration (`simulated-annealing-investigation.md`, SA outcome section). Must be fixed before re-attempting — see Recommendation #3 above.

5. **Increasing worker count.** More workers search the same exhausted neighborhood faster. The bottleneck is the search strategy, not throughput.

6. **Optimistic extrapolation.** The improvement rate is not "slow" — it's **zero**. No amount of patience will produce a 2-flip improvement from a 2-flip local minimum.

---

## Honest Assessment: Can We Reach 0?

**The mathematical question "Is R(8,8) ≥ 289?" is open.** Nobody knows if a valid 2-coloring of K₂₈₈ without monochromatic K₈ exists. If it doesn't, no algorithm can find it.

**What we know:**
- R(8,8) ≥ 282 (proven by Paley(281))
- R(8,8) ≤ 6,090 (known upper bound)
- The gap is enormous (282 to 6,090)
- Nobody has proven R(8,8) ≥ 283, let alone ≥ 289

**Our graph at 980,299 cliques is 12.4% of random expectation** — significantly better than random, but still very far from zero. The remaining cliques are deeply embedded in the graph's structure.

**Realistic outcomes for the recommended approaches:**

| Approach | Realistic best case | Likely outcome |
|----------|-------------------|----------------|
| Variable-depth search | Break below 980,000 | Find improvements of 10-100 cliques beyond 2-flip minimum |
| Tabu search | Find new basin at ~979,000-980,000 | Explore diverse region, possibly find slightly better minimum |
| Fixed SA | Reach ~978,000-980,000 range | Discover landscape structure beyond current basin |
| Population search | Find a different local minimum, possibly better | Most forks converge to similar quality |
| Reaching 0 cliques | Would be a major mathematical result | Very unlikely with local search alone |

**The honest path:** These techniques can likely push below 980,000 and perhaps significantly lower, but reaching zero would require either a breakthrough in graph construction theory or an extremely deep search with sophisticated methods. The computational approach is best viewed as **exploring the landscape and making incremental records**, not as a guaranteed path to R(8,8) ≥ 289.

---

## Suggested Implementation Order

```
Phase 1: Escape the Basin (1-2 weeks)
├── 1. Implement variable-depth search in Rust worker
│     - New worker mode: VARIABLE_DEPTH
│     - Reuse CliqueCollection + seeded BK
│     - Depth limit 5-10, guided edge selection
│
├── 2. Implement tabu search in Rust worker  
│     - New worker mode: TABU
│     - Tabu tenure ~200, adaptive
│     - Track best-ever during walk
│
└── 3. Fix SA implementation (existing sa.rs)
      - Incremental evaluation via CliqueCollection (not full recount)
      - Larger perturbations (5-20 edges, up from 2)
      - Temperature schedule: 50→5 with reheats (down from 1000→0)

Phase 2: Diversify (1 week)
├── 4. Launch 10-20 population forks
│     - Perturb best graph by 20-100 random edges each
│     - Run standard 2-flip on each fork
│
└── 5. Analyze results from Phase 1
      - Which technique found improvements?
      - What does the landscape look like beyond the current basin?
      - Are there multiple distinct basins at ~980K?

Phase 3: Deepen (ongoing)
├── 6. Combine best techniques
│     - e.g., variable-depth search from tabu-discovered positions
│     - Population + variable-depth hybrid
│
└── 7. Scale to burst compute
      - Deploy best-performing search mode to Vast.ai
      - Multiple independent searches on different starting points
```

---

## Appendix A: The SA Fix in Detail

The current SA implementation in `sa.rs` (line 104) calls:
```rust
let new_clique_count = get_cliques_comprehensive(&mut current_graph, clique_size);
```

This is a full Bron-Kerbosch traversal — both red and blue — on the entire 288-vertex graph. Cost: several seconds per call.

The fix: build a CliqueCollection at the start of each SA run, then use it incrementally:
```rust
// Instead of full recount:
let broken = clique_collection.get_count_of_cliques_containing_edges(&edges_to_flip);
current_graph.flip_edges(&edges_to_flip);
let (new, _) = get_new_cliques_with_limit(&mut current_graph, clique_size, &edges_to_flip, i32::MAX);
let new_clique_count = current_clique_count - broken + new;
```

**However, there's a subtlety:** After accepting a move, the CliqueCollection is stale — it reflects the old graph, not the new one. Options:
1. **Rebuild CliqueCollection after each accept** — expensive but accurate
2. **Use stale collection as approximation** — fast but introduces error over time
3. **Rebuild periodically** (every 100-1000 accepts) — compromise

For SA, option 2 is fine initially (the approximation is good for nearby graphs), with periodic rebuilds (option 3) for long runs. The broken-clique count from a stale collection is an approximation, but the seeded BK for new cliques is always exact since it operates on the actual graph.

## Appendix B: Variable-Depth Search Implementation Sketch

```rust
fn variable_depth_search(
    graph: &mut Graph,
    clique_collection: &CliqueCollection,
    clique_size: usize,
    max_depth: usize,
) -> Option<(Vec<WorkUnitEdge>, i32)> {
    let base_cc = clique_collection.total() as i32;
    let mut best_sequence: Vec<WorkUnitEdge> = Vec::new();
    let mut best_delta = 0; // Must beat this (strictly negative = improvement)
    
    // Get edges sorted by clique participation (highest first = most impactful)
    let candidates = get_sorted_edge_candidates(graph, clique_collection);
    
    for (i, first_edge) in candidates.iter().enumerate().take(1000) {
        let mut sequence = vec![first_edge.clone()];
        let mut cumulative_delta = 0;
        
        // Apply first flip
        graph.flip_edges(&[first_edge.clone()]);
        let broken = clique_collection.get_count_of_cliques_containing_edges(&[first_edge.clone()]);
        let (new, _) = get_new_cliques_with_limit(graph, clique_size, &[first_edge.clone()], i32::MAX);
        cumulative_delta = -broken + new;
        
        // Recurse to find improving continuations
        search_deeper(
            graph, clique_collection, clique_size,
            &mut sequence, cumulative_delta,
            &mut best_sequence, &mut best_delta,
            max_depth - 1, &candidates,
        );
        
        // Undo first flip
        graph.flip_edges(&[first_edge.clone()]);
    }
    
    if best_delta < 0 {
        Some((best_sequence, best_delta))
    } else {
        None
    }
}
```

## Appendix C: System State Snapshot (2026-04-05)

| Metric | Value |
|--------|-------|
| Campaign | 1 — R(8,8), 288 vertices, clique size 8 |
| Active stage | 4859, base graph 4859, CC=980,322 |
| All-time best | Graph 4816, CC=980,299 |
| Total stages completed | 4,858 |
| Stages since last improvement | 43 |
| Work units since last improvement | ~18.4 billion |
| Workers | 15 Rust (exhaustive only) |
| Cycle prevention set | 92 hashes |
| Edge split | 20,664 red / 20,664 blue (exact 50%) |
| Edges changed from initial | 2,168 (5.25%) |
| Graph uniformity | Near-constant degree (140-146), triangle count (4871-5246) |
| Campaign started | 2025-03-20 |
| Campaign duration | ~12.5 months |

---

*Generated for the Ramsey project — April 2026*  
*Based on comprehensive analysis of 4,859 stages of history, graph structure, algorithm code, and Redis/MySQL state*

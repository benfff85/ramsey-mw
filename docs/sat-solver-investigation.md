# SAT Solver Investigation — Ramsey R(8,8) Search

**Date:** 2026-04-04  
**Author:** Ben Ferenchak + Claude  
**Status:** Analysis complete — SAT has niche value but is not a silver bullet at this scale

---

## Background

The Ramsey project searches for a 2-coloring of the complete graph K₂₈₈ that minimizes the number of monochromatic 8-cliques, targeting zero (which would prove R(8,8) ≥ 289). The current best graph has **~980,316 cliques** at stage 4846, down from 980,666 at stage 4755 roughly a week ago. Progress comes through exhaustive 2-edge-flip search across 14 parallel workers, each processing ~250K work units per minute.

A colleague suggested that a SAT (Boolean satisfiability) solver might help. This document investigates whether SAT, MaxSAT, or related constraint-solving techniques could complement or replace the current exhaustive/mutation-based search strategy.

---

## What Is SAT Solving?

A SAT solver takes a Boolean formula in conjunctive normal form (CNF) — an AND of OR-clauses over Boolean variables — and either finds a satisfying assignment or proves none exists. Modern CDCL (conflict-driven clause learning) solvers like CaDiCaL, Kissat, and MiniSat can handle problems with millions of variables and tens of millions of clauses through intelligent backtracking, unit propagation, and learned clause management.

**MaxSAT** extends SAT to optimization: given hard clauses (must satisfy) and soft clauses (want to satisfy), find an assignment that satisfies all hard clauses and maximizes the number of satisfied soft clauses. Solvers like Open-WBO, RC2, and EvalMaxSAT handle this.

**Pseudo-Boolean (PB) optimization** generalizes further, allowing weighted linear constraints over Boolean variables. Solvers like RoundingSAT handle `minimize Σ wᵢxᵢ subject to constraints`.

---

## The Natural SAT Encoding of Ramsey

The textbook encoding of "does a 2-coloring of Kₙ with no monochromatic Kₖ exist?" is:

- **Variables:** One Boolean variable per edge: `x_{i,j}` for each `{i,j}` with `i < j`. True = red, false = blue.
- **Clauses:** For every k-subset S ⊆ V, add two clauses:
  - "Not all red": at least one edge in S is blue → `¬x_{a,b}` for some `{a,b} ∈ C(S,2)`
  - "Not all blue": at least one edge in S is red → `x_{a,b}` for some `{a,b} ∈ C(S,2)`

### The Scale Problem for R(8,8) on 288 Vertices

| Quantity | Formula | Value |
|----------|---------|-------|
| Variables (edges) | C(288,2) | 41,328 |
| 8-subsets of vertices | C(288,8) | **~2.4 × 10¹⁴** |
| Clauses (2 per 8-subset) | 2 × C(288,8) | **~4.8 × 10¹⁴** |
| Literals per clause | C(8,2) = 28 | 28 |

**The direct encoding is completely infeasible.** 480 trillion clauses cannot be stored in memory, let alone solved. Even generating the clause file would take years. For comparison:

| Problem | Variables | Clauses | Status |
|---------|-----------|---------|--------|
| R(3,3,3) = 17 (Codish 2016) | 816 | ~31K | Solved in minutes |
| Pythagorean triples (Heule 2016) | 7,825 | ~31M | Solved (200 CPU-years, 200TB proof) |
| Schur(5) = 161 (Heule 2017) | 805 | ~1.7M | Solved in hours |
| R(4,5) on 24v (partial results) | 276 | ~95K | Tractable |
| **R(8,8) on 288v** | **41,328** | **~4.8 × 10¹⁴** | **Completely infeasible** |

The variable count (41K) is fine for modern SAT solvers. The clause count is the killer — it's 10 million times larger than the largest SAT problems ever solved.

**Verdict: Direct SAT encoding of the full R(8,8) problem is not viable. Not now, not with foreseeable hardware.**

---

## Why SAT Worked for Smaller Ramsey Problems

SAT solvers have been used successfully for Ramsey-adjacent problems, but always at much smaller scales:

- **R(4,t) values:** Clique size 4 means C(n,4) subsets, which grows polynomially (n⁴/24). For n=24, C(24,4) = 10,626 — very manageable.
- **Schur numbers / Pythagorean triples:** These have linear variable counts and polynomial clause counts.
- **Small Ramsey confirmations:** Verifying R(5,5) ≤ 48 involves C(48,5) = 1,712,304 clauses — tight but feasible.

The exponent in C(n,k) is **k**, your clique size. At k=8, the clause count explodes beyond tractability for any n > ~50-60 vertices. Your problem has n=288.

---

## Approaches That Could Work (Partially)

While the full problem is infeasible, there are SAT-based techniques that could help with **subproblems** within your existing search framework.

### Approach 1: Local Region MaxSAT Optimization

**Idea:** Instead of SAT-solving the entire graph, fix most edges and optimize a small neighborhood.

1. Select a "window" of W candidate edges to potentially flip (the free variables)
2. All other edges are fixed (constants, not variables)
3. Encode only the cliques that involve at least one free edge
4. Use MaxSAT to find the combination of flips that maximizes net clique reduction

**Scaling analysis for window size W:**

With 980K total cliques and 41,328 edges, each edge participates in roughly `980,000 × C(8,2) / 41,328 ≈ 664` cliques on average (each 8-clique has 28 edges). So:

| Window (W edges) | Variables | Affected existing cliques | New clique constraints | Feasible? |
|-------------------|-----------|--------------------------|------------------------|-----------|
| 10 | 10 | ~6,600 | ~few thousand | Trivial |
| 50 | 50 | ~33,000 | ~tens of thousands | Easy |
| 200 | 200 | ~130,000 | ~hundreds of thousands | Feasible |
| 500 | 500 | ~330,000 | ~millions | Pushing limits |
| 1,000 | 1,000 | ~660,000 | ~tens of millions | Borderline |

The "new clique constraints" column is harder to estimate — you need clauses preventing the creation of new 8-cliques among the flipped edges. This requires enumerating potential 8-cliques that could form, which depends on the graph structure.

**Why this is better than exhaustive 2-flip search:** Your current system tests all C(20664,1) × C(20664,1) ≈ 427M single-pair mutations one at a time. A MaxSAT solver with a window of 200 edges can find the **optimal combination** of flips within that window — potentially flipping 5, 10, or 50 edges simultaneously. This searches a space of 2²⁰⁰ ≈ 10⁶⁰ combinations, but SAT solvers navigate it efficiently through constraint propagation.

**The catch:** You need to enumerate which 8-cliques could be _created_ by flipping edges in the window. For each potential 8-subset that includes vertices from free edges, you must check if flipping those edges would complete a monochromatic clique. This enumeration is the bottleneck — not the SAT solving itself.

**Implementation sketch:**
```
1. Pick W edges (biased toward high-participation edges from CliqueCollection)
2. For each existing 8-clique containing ≥1 free edge:
     Add soft clause: "break this clique" (flip at least one of its free edges)
3. For each potential new 8-clique that could form from flips:
     Add hard clause: "don't create this clique" (don't flip all needed edges)
4. Run MaxSAT solver (RC2, Open-WBO)
5. Apply the solution: flip the indicated edges
6. Verify with full Bron-Kerbosch count
```

**Estimated impact:** Could find multi-edge improvements that 2-flip search cannot reach. The quality depends entirely on window selection — picking the right 200 edges is itself a hard problem.

**Estimated effort:** ~3-5 days. Requires a Python or Rust MaxSAT integration, clique enumeration for the window, and window selection heuristics.

**Risk:** Window selection may miss the right neighborhood. The solver finds the local optimum within the window, but if the window doesn't contain the right edges, it's wasted effort.

### Approach 2: SAT-Based Clique Destruction

**Idea:** Target individual cliques for destruction. For each monochromatic 8-clique, at least one of its 28 edges must be flipped to break it. But flipping an edge might create new cliques. Frame this as a set-cover / hitting-set problem.

**Encoding:**
- Variables: `flip_e` for each edge e (should we flip it?)
- For each existing monochromatic 8-clique C: `OR(flip_e : e ∈ C)` — at least one edge must flip
- For each potential new 8-clique C' that would be created: `NOT AND(flip_e : e ∈ C' that need flipping)` — don't create it
- Objective: minimize total flips (or minimize net clique change)

This is essentially a weighted partial MaxSAT / hitting set problem. The 980K "break this clique" clauses are soft constraints. The "don't create new cliques" are hard constraints.

**Problem:** The 980K existing cliques give 980K soft clauses — fine. But computing the "don't create" constraints requires enumerating all potential new 8-cliques across the entire graph, which brings us back to the C(288,8) problem. You'd need to restrict the flip candidates to a manageable subset (back to Approach 1).

### Approach 3: Incremental SAT for Neighborhood Certification

**Idea:** Use SAT not to find improvements, but to **prove that no improvement exists** within a neighborhood, allowing you to skip it.

Currently, exhaustive search tests all 427M edge pairs. If a SAT solver could prove "no combination of flips among these 1,000 edges improves the graph," you could skip large chunks of the search space.

**Problem:** Proving unsatisfiability is typically harder than finding satisfying assignments. And the windows would need to be small enough to encode efficiently but large enough to be useful. This is unlikely to beat the existing early-termination threshold optimization, which already skips ~90% of work units via a simple comparison.

### Approach 4: Symmetry Breaking via SAT Preprocessing

**Idea:** Use SAT-based techniques to identify symmetries in the current graph, then eliminate redundant edge pairs from the search space.

If the graph has an automorphism group of order k, then only 1/k of the edge pairs need to be tested — the rest produce isomorphic results. Computing the automorphism group is itself a graph isomorphism problem, but tools like nauty/Traces can handle 288-vertex graphs in seconds.

**This isn't really a SAT problem** — it's better handled by dedicated graph automorphism tools. But SAT-based symmetry breaking (adding clauses that enforce a canonical form) is a well-known technique that could be combined with Approach 1.

### Approach 5: WalkSAT / Local Search SAT

**Idea:** WalkSAT, GSAT, and similar local-search SAT algorithms work by starting from a random assignment and flipping variables to reduce the number of unsatisfied clauses. This is conceptually very similar to what your system already does (start from a graph, flip edges to reduce cliques).

**The key difference:** WalkSAT uses the clause structure to make intelligent flip decisions. Instead of trying random edges, it picks an unsatisfied clause (a remaining clique) and flips one of the edges in that clause. This is a targeted strategy that focuses mutations on edges that actually participate in cliques.

**The catch:** WalkSAT still needs the full clause set. With 480 trillion clauses, this is infeasible. However, you could implement the *spirit* of WalkSAT without the SAT encoding: pick a random remaining clique, pick a random edge in it, evaluate what happens if you flip that edge. Your CliqueCollection already supports this — it tracks which cliques each edge participates in.

**This is worth implementing as a search heuristic even without a SAT solver.** It's essentially "clique-guided mutation" — a middle ground between random edge selection and exhaustive search.

---

## Comparison: SAT vs. Current Approach vs. Other Techniques

| Technique | Search space per step | Neighborhood size | Can escape local minima? | Compute cost |
|-----------|----------------------|-------------------|--------------------------|-------------|
| **Exhaustive 2-flip** (current) | 427M pairs, tested one at a time | 2 edges | No (strict downhill) | ~3-7 days/stage |
| **Simulated annealing** (tried, failed) | Random 2-pair mutations | 2 edges | In theory yes | ~2 hrs/schedule |
| **Local MaxSAT (W=200)** | 2²⁰⁰ combinations, solved optimally | 200 edges | Within window, yes | Minutes per window |
| **WalkSAT-inspired heuristic** | Targeted single-edge flips | 1 edge (guided) | With randomization | Seconds per step |
| **Multi-edge exhaustive (3-flip)** | ~10¹⁵ triples, sampled | 3 edges | No | Days (sampled) |
| **Genetic algorithm / population** | Crossover + mutation | Varies | Yes (population diversity) | Depends on pop size |

---

## Honest Assessment: Will SAT Help You Reach 0 Cliques?

**No.** No SAT-based approach will take you from 980K cliques to 0. Here's why:

1. **The full problem is an open mathematical question.** Whether R(8,8) ≥ 289 is unknown. If a SAT solver could answer this, it would resolve a 50+ year open problem in combinatorics. The problem size (C(288,8) ≈ 10¹⁴ clauses) is fundamentally beyond current SAT technology.

2. **You're 980K cliques away from zero.** Even eliminating 1 clique per stage (which is faster than current pace), you'd need ~1 million stages. The landscape at this depth appears to be a vast plateau where 2-flip improvements are vanishingly rare — the SA investigation confirmed this.

3. **The problem may be unsatisfiable.** It's possible that no valid 2-coloring of K₂₈₈ without monochromatic K₈ exists (i.e., R(8,8) ≤ 288). If so, no algorithm can succeed.

**What SAT CAN do:**

- **Find better multi-edge moves** via local MaxSAT (Approach 1). This is the most promising application — it directly addresses the limitation of 2-flip search by optimizing over larger neighborhoods.
- **Inspire better heuristics** (Approach 5). The WalkSAT concept of "pick an unsatisfied clause, flip a variable in it" translates directly to "pick a remaining clique, flip an edge in it." This is a cheap, implementable heuristic.
- **Provide theoretical grounding** for understanding why the search is stuck. The clique constraint structure may reveal that certain cliques form an "unsatisfiable core" — a set of cliques that cannot all be simultaneously eliminated by any local modification.

---

## Recommendation

### What to implement (if anything)

**Tier 1 — Low effort, high insight (1-2 days):**

1. **Clique-guided mutation heuristic** (WalkSAT-inspired, no SAT solver needed)
   - Pick a random 8-clique from the current graph
   - For each of its 28 edges, compute the net clique change if that edge is flipped
   - Flip the best edge (or accept probabilistically, SA-style)
   - Repeat
   - This is essentially what WalkSAT does, translated to graph language
   - Can be implemented as a new worker mode using existing CliqueCollection infrastructure
   - **Cost:** $0, ~1 day of Rust work

2. **Clique overlap analysis** (diagnostic, not SAT)
   - How interconnected are the 980K cliques? How many share edges?
   - If cliques form dense clusters sharing many edges, flipping one edge breaks many cliques (good — search should focus there)
   - If cliques are mostly disjoint, each flip breaks ~1 clique (bad — you need ~980K independent flips, which is nearly impossible without also creating new cliques)
   - This analysis tells you whether the problem is "nearly satisfiable" or "deeply unsatisfiable"
   - **Cost:** $0, ~half a day of analysis

**Tier 2 — Medium effort, speculative payoff (3-5 days):**

3. **Local MaxSAT window optimizer**
   - Select 100-200 high-participation edges as free variables
   - Enumerate affected cliques (existing and potential new)
   - Run RC2 or Open-WBO MaxSAT solver
   - Apply the optimal flip combination
   - This is the most "SAT-like" approach that's actually feasible
   - Requires Python (pysat library) or Rust (varisat crate) integration
   - **Cost:** $0, ~3-5 days

**Tier 3 — Don't bother:**

4. ~~Direct SAT encoding~~ — Infeasible at C(288,8) scale
5. ~~Full WalkSAT on CNF~~ — Same clause-count problem
6. ~~SAT-based symmetry breaking~~ — Use nauty/Traces directly instead, they're faster and purpose-built

### Comparison to other roadmap items

| Enhancement | Expected clique reduction | Effort | Recommendation |
|-------------|--------------------------|--------|----------------|
| Clique-guided mutation (Tier 1) | Small per-step, but targeted | 1 day | **Do this first** |
| Local MaxSAT optimizer (Tier 2) | Potentially large (multi-edge) | 3-5 days | Try after Tier 1 |
| Multi-edge flip (3+) from roadmap | Medium | 2 days | Still valuable |
| Graph symmetry pruning | No clique reduction, faster search | 5 days | Complementary |
| Better starting graphs (Paley/circulant) | Potentially huge (fresh start) | 2-3 days | **Most likely path to breakthrough** |

---

## The Elephant in the Room: Starting Graph Quality

The most impactful thing SAT-adjacent research tells us about Ramsey lower bounds is this: **the starting graph matters enormously.** The known best lower bounds for Ramsey numbers almost always come from algebraically constructed graphs (Paley graphs, circulant graphs, graphs from finite fields), not from local search on arbitrary starting points.

Your current graph has 980K cliques. The best-known constructions for R(8,8) candidates (Paley graphs on prime orders near 288, circulant graphs with carefully chosen connection sets) may start with significantly fewer cliques. If a Paley graph on 281 vertices (GF(281), a prime) starts with 500K cliques instead of 980K, you've saved yourself hundreds of thousands of stages of local search.

**This is not a SAT problem — it's a number theory / finite geometry problem.** But it's arguably more impactful than any SAT technique for your goal.

---

## Summary

| Question | Answer |
|----------|--------|
| Can SAT solve R(8,8) directly? | **No.** ~10¹⁴ clauses, completely infeasible. |
| Can SAT help find better moves? | **Maybe.** Local MaxSAT on 100-200 edge windows could find multi-edge improvements. |
| Can SAT-inspired heuristics help? | **Yes.** Clique-guided mutation (WalkSAT-style) is cheap and directly implementable. |
| Is SAT better than your current approach? | **Not as a replacement.** As a complement to exhaustive search, local MaxSAT could find moves that 2-flip search misses. |
| What's the single best next step? | **Clique overlap analysis** to understand the constraint landscape, then **clique-guided mutation** to exploit it. |
| What's the most likely path to a breakthrough? | **Better starting graphs** from algebraic constructions, not SAT. |

---

## Appendix: Current System State (2026-04-04)

- **Campaign:** Active, R(8,8) on 288 vertices, clique size 8
- **Stage:** 4846, base graph 4846, clique count 980,316
- **Strategy:** DUAL_EDGE_CARDINALITY
- **Total pairs per stage:** 427,000,896 (20,664 red × 20,664 blue)
- **Progress this stage:** 32.5M / 427M pairs processed (~7.6%)
- **Workers:** 15 Rust workers (14 exhaustive + previously 1 SA, SA now shut down)
- **Throughput:** ~250K work units per worker per minute
- **Recent behavior:** Best results in 980,300-980,430 range; many "already processed" graphs being cycled through; stage transitions occurring via exhaustion fallback
- **SA outcome:** Shut down 2026-04-02 after 45K+ iterations yielded zero improvements from base 980,666 graph

---

*Generated for the Ramsey project — April 2026*
*Based on analysis of the running system, SA investigation results, and SAT/constraint-solving literature*

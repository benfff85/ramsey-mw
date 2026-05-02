# SAT Solver Investigation — Ramsey R(8,8) ≥ 283

**Date:** 2026-05-02
**Author:** Ben Ferenchak + Claude
**Status:** Analysis complete — direct SAT infeasible; local MaxSAT and clique-guided heuristics viable

---

## Background

The Ramsey project searches for a 2-coloring of K₂₈₂ that minimizes monochromatic 8-cliques, targeting zero (which would establish R(8,8) ≥ 283). The current best graph has **~792,000 cliques** at the leader stage, descended from a Paley(281) + 1-vertex seed via iterative edge-flip mutation.

This document analyzes whether SAT, MaxSAT, or related constraint-solving techniques could complement or replace the current mutation-based search.

---

## What Is SAT Solving?

A SAT solver takes a Boolean formula in conjunctive normal form (CNF) and either finds a satisfying assignment or proves none exists. Modern CDCL solvers (CaDiCaL, Kissat, MiniSat) handle millions of variables and tens of millions of clauses.

**MaxSAT** extends SAT to optimization: hard clauses (must satisfy) and soft clauses (want to satisfy). Solvers like Open-WBO, RC2, and EvalMaxSAT.

**Pseudo-Boolean (PB) optimization** generalizes further with weighted linear constraints. RoundingSAT is a representative solver.

---

## The Natural SAT Encoding

The textbook encoding of "does a 2-coloring of K_n with no monochromatic K_k exist?":

- **Variables:** one Boolean per edge, `x_{i,j}` for each `{i,j}` with `i < j`. True = red, false = blue.
- **Clauses:** for every k-subset S ⊆ V, two clauses:
  - "Not all red": at least one edge in S is blue
  - "Not all blue": at least one edge in S is red

### Scale for R(8,8) on 282 Vertices

| Quantity | Formula | Value |
|----------|---------|-------|
| Variables (edges) | C(282,2) | 39,621 |
| 8-subsets of vertices | C(282,8) | **~3.18 × 10¹³** |
| Clauses (2 per 8-subset) | 2 × C(282,8) | **~6.36 × 10¹³** |
| Literals per clause | C(8,2) = 28 | 28 |

**Direct SAT encoding is infeasible.** 60+ trillion clauses cannot be stored in memory, let alone solved. For comparison:

| Problem | Variables | Clauses | Status |
|---------|-----------|---------|--------|
| R(3,3,3) = 17 (Codish 2016) | 816 | ~31K | Solved in minutes |
| Pythagorean triples (Heule 2016) | 7,825 | ~31M | Solved (200 CPU-years, 200TB proof) |
| Schur(5) = 161 (Heule 2017) | 805 | ~1.7M | Solved in hours |
| **R(8,8) on 282v** | **39,621** | **~6.36 × 10¹³** | **Completely infeasible** |

The variable count (40K) is fine for modern solvers. The clause count is the killer.

**Verdict: Direct SAT encoding of the full problem is not viable. Not now, not with foreseeable hardware.**

---

## Why SAT Worked for Smaller Ramsey Problems

SAT solvers have been used successfully for smaller Ramsey-adjacent problems:

- **R(4,t) values:** Clique size 4 gives polynomial clause growth.
- **Schur / Pythagorean triples:** Linear variables, polynomial clauses.
- **R(5,5) ≤ 48 verification:** C(48,5) = 1.7M clauses — tight but feasible.

The exponent in C(n,k) is **k**. At k=8, clause count explodes beyond tractability for any n > ~50–60. Our problem has n=282.

---

## Approaches That Could Work (Partially)

While the full problem is infeasible, SAT-based techniques can help with **subproblems**.

### Approach 1: Local Region MaxSAT Optimization (Tier 2 — viable)

**Idea:** Fix most edges; optimize a small window.

1. Select W candidate edges as free variables.
2. All other edges are constants.
3. Encode only the cliques involving at least one free edge.
4. Use MaxSAT to find the flip combination that maximizes net clique reduction.

**Scaling for window size W** (with ~792K cliques and 39,621 edges, each edge participates in ~560 cliques):

| Window (W edges) | Variables | Affected existing cliques | New clique constraints | Feasible? |
|-------------------|-----------|--------------------------|------------------------|-----------|
| 10 | 10 | ~5,600 | ~thousands | Trivial |
| 50 | 50 | ~28,000 | ~tens of thousands | Easy |
| 200 | 200 | ~112,000 | ~hundreds of thousands | Feasible |
| 500 | 500 | ~280,000 | ~millions | Pushing limits |
| 1,000 | 1,000 | ~560,000 | ~tens of millions | Borderline |

**Why this beats 2-flip exhaustive search:** The exhaustive engine tests all C(R,1) × C(B,1) ≈ 392M single-pair mutations. A MaxSAT solver with W=200 free edges can find the **optimal combination** — potentially 5, 10, or 50 simultaneous flips — within a search space of 2²⁰⁰ ≈ 10⁶⁰. Constraint propagation makes this tractable.

**The catch:** Enumerating which 8-cliques could be **created** by flipping window edges is itself the bottleneck — not the SAT solving.

**Implementation sketch:**
```
1. Pick W edges (biased toward high-participation edges from CliqueCollection)
2. For each existing 8-clique containing ≥1 free edge:
     Add soft clause: "break this clique" (flip ≥1 free edge in it)
3. For each potential new 8-clique formed by flipping free edges:
     Add hard clause: "don't create this clique"
4. Run MaxSAT solver (RC2, Open-WBO)
5. Apply solution; verify with full Bron-Kerbosch count
```

**Estimated effort:** 3–5 days. Python with `pysat` is the fastest path; Rust via `varisat` is alternative.

**Risk:** Window selection. If the right edges aren't in the window, the solver finds a local optimum that doesn't help. Hot-edge selection from `CliqueCollection` is the obvious heuristic.

### Approach 2: SAT-Based Clique Destruction (subsumed by Approach 1)

Frame the problem as weighted hitting set: each existing 8-clique requires ≥1 of its 28 edges to be flipped, while no new 8-clique is created. The "don't create new cliques" constraints require enumeration over potential 8-subsets — back to the C(282,8) problem unless restricted to a window.

In practice, this collapses to Approach 1 with a different selection heuristic.

### Approach 3: WalkSAT-Inspired Clique-Guided Mutation (Tier 1 — cheap and viable)

**Idea:** Without any SAT solver, borrow WalkSAT's strategy directly.

1. Pick a random surviving 8-clique from the current graph
2. For each of its 28 edges, compute the delta if that edge is flipped
3. Flip the best edge (or accept probabilistically à la SA)
4. Repeat

This is the "clique-guided mutation" pattern: instead of random-flipping or exhaustive-flipping, **target edges that participate in actual cliques**. The `CliqueCollection` already tracks per-edge clique participation, so this is implementable as a new worker mode with no external solver dependency.

**Estimated effort:** 1–2 days. Reuses the existing `CliqueCollection` and incremental delta machinery.

### Approach 4: Symmetry Breaking via SAT Preprocessing

**Idea:** Identify graph automorphisms; eliminate redundant edge pairs from the search.

This isn't really a SAT problem — graph automorphism is best handled by **nauty/Traces**, which can handle 282-vertex graphs in seconds. SAT-based symmetry breaking adds clauses enforcing canonical form; it could combine with Approach 1 to eliminate symmetric solutions.

**Recommendation:** use nauty/Traces directly via FFI. ~2 days; even modest symmetry factors compound on stuck stages.

### Approach 5: Incremental SAT for Neighborhood Certification (low priority)

Use SAT to **prove no improvement exists** within a neighborhood — allowing the search to skip it. Proving unsatisfiability is typically harder than finding a model, and the existing early-termination threshold already skips ~90% of work units cheaply. Unlikely to beat the current approach.

---

## Comparison: SAT vs. Other Techniques

| Technique | Search space per step | Neighborhood size | Can escape local minima? | Compute cost |
|-----------|----------------------|-------------------|--------------------------|-------------|
| **Exhaustive 2-flip** (primary) | 392M pairs, tested individually | 2 edges | No (strict downhill) | ~hours/stage |
| **Simulated annealing** (deferred) | Random multi-edge mutations | 5–20 edges | In theory yes | Hours/run |
| **Local MaxSAT (W=200)** | 2²⁰⁰ combinations, solved optimally | 200 edges | Within window, yes | Minutes per window |
| **Clique-guided mutation** | Targeted single-edge flips | 1 edge (guided) | With randomization | Seconds per step |
| **Variable-depth search** | Tree-search over multi-flip chains | 3–8 edges | Yes, structurally | Seconds/run |

---

## Honest Assessment: Will SAT Help Reach 0 Cliques?

**No SAT-based approach will take the search from ~792K cliques to 0 by itself.** Reasons:

1. **The full problem is open mathematics.** Whether R(8,8) ≥ 283 is unknown. The problem size (~6.4 × 10¹³ clauses) is fundamentally beyond current SAT capability.

2. **The remaining ~792K cliques are deeply embedded.** Even at 1 clique per stage (faster than current pace), reaching zero would require ~800K stages.

3. **The problem may be unsatisfiable.** It's possible that no clique-free 2-coloring of K₂₈₂ exists.

**What SAT CAN do:**

- **Find better multi-edge moves** via local MaxSAT (Approach 1). Directly addresses the limitation of 2-flip search by optimizing over larger neighborhoods.
- **Inspire better heuristics** (Approach 3). The WalkSAT pattern translates directly into the clique-guided mutation worker mode.
- **Provide theoretical grounding** for understanding why the search stalls. Unsatisfiable cores within a window — clique sets that cannot all be eliminated by any local modification — would be diagnostic information.

---

## Recommendation

### Tier 1 — Low effort, high insight (1–2 days)

1. **Clique-guided mutation worker mode** (WalkSAT-inspired, no SAT solver needed)
   - Pick a random surviving 8-clique
   - For each of its 28 edges, compute the net delta if flipped
   - Flip the best edge (or accept probabilistically)
   - Reuse existing `CliqueCollection` and incremental delta machinery
   - **Cost:** $0, ~1 day in Rust

2. **Clique overlap analysis** (diagnostic)
   - How interconnected are the ~792K cliques? How many share edges?
   - Tells us whether the problem is "nearly satisfiable" (cliques cluster densely) or "deeply unsatisfiable" (cliques are nearly disjoint)
   - **Cost:** $0, ~half a day

### Tier 2 — Medium effort, speculative payoff (3–5 days)

3. **Local MaxSAT window optimizer**
   - 100–200 high-participation edges as free variables
   - Enumerate affected and potential-new cliques
   - Run RC2 or Open-WBO
   - Most "SAT-like" approach that's actually feasible
   - **Cost:** $0, ~3–5 days

### Tier 3 — Don't bother

- Direct SAT encoding (infeasible)
- Full WalkSAT on CNF (same clause-count problem)
- SAT-based symmetry breaking (use nauty/Traces directly)

---

## Comparison to Other Roadmap Items

| Enhancement | Expected impact | Effort | Recommendation |
|-------------|------------------|--------|----------------|
| Clique-guided mutation (Tier 1) | Small per-step, but targeted | 1 day | **Cheap experiment first** |
| Local MaxSAT optimizer (Tier 2) | Potentially large (multi-edge) | 3–5 days | **Most promising SAT-adjacent** |
| Variable-depth search Phase 2+3 | Large (escape 2-flip wall) | 1 week | **Highest-leverage algorithmic upgrade** |
| Tabu search worker | Medium (basin diversification) | 3 days | Worth implementing |
| Population GA crossover | Medium-high (population diversity) | 1 week | Underutilized resource |
| Graph symmetry pruning (nauty) | No clique reduction; faster search | 2 days | Complementary |

---

## Summary

| Question | Answer |
|----------|--------|
| Can SAT solve R(8,8) directly? | **No.** ~6.4 × 10¹³ clauses, infeasible. |
| Can SAT help find better moves? | **Maybe.** Local MaxSAT on 100–200-edge windows could find multi-edge improvements. |
| Can SAT-inspired heuristics help? | **Yes.** Clique-guided mutation (WalkSAT-style) is cheap and directly implementable. |
| Is SAT a replacement for the current approach? | **No.** As a complement, local MaxSAT could find moves that 2-flip search misses. |
| Single best next step? | **Clique-guided mutation** (Tier 1) — cheapest experiment. **Variable-depth search Phase 2+3** is higher-leverage but heavier lift. |

---

## Appendix: System State Snapshot

| Metric | Value |
|--------|-------|
| Vertices | 282 |
| Clique size | 8 |
| Total pairs per stage | ~392.5M |
| Best clique count | 791,938 |
| Recent improvement rate | ~15 cliques/stage |
| Active workers | 14 Rust exhaustive |
| Active campaigns | 8 (1 leader + 7 population forks) |

---

*Generated for the Ramsey project — May 2026*

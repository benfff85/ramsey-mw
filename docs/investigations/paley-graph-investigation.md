# Paley Graphs as Seed Construction for the 282-Vertex Search

**Date:** 2026-05-02
**Author:** Ben Ferenchak + Claude
**Status:** Reference — documents the seed-graph rationale for the R(8,8) ≥ 283 search

---

## Background

Paley graphs are a classical Ramsey-theoretic construction. For a prime p with p ≡ 1 (mod 4), the Paley graph on p vertices connects vertices i and j when (i − j) is a quadratic residue mod p. The result is self-complementary and vertex-transitive: red and blue subgraphs are isomorphic and every vertex has identical local structure.

For R(8,8), Paley(281) is the natural anchor: 281 is prime, 281 ≡ 1 (mod 4), and Paley(281) has **zero monochromatic 8-cliques**. This is the standard proof that R(8,8) ≥ 282.

The 282-vertex search begins exactly here: take Paley(281), add one vertex, and minimize the resulting clique count toward zero. Reaching zero would establish R(8,8) ≥ 283.

---

## Paley(281) Properties

Generated and stored in `graph_paley` table (graph_id=1).

**Eight-clique count: 0 (red: 0, blue: 0)**

Subclique structure:

| Clique size (k) | Red k-cliques | Blue k-cliques | Total |
|-----------------|---------------|----------------|-------|
| 3 | 452,410 | 452,410 | 904,820 |
| 4 | 3,737,300 | 3,737,300 | 7,474,600 |
| 5 | 11,133,220 | 11,133,220 | 22,266,440 |
| 6 | 11,015,200 | 11,015,200 | 22,030,400 |
| 7 | **2,989,840** | **2,989,840** | **5,979,680** |
| 8 | 0 | 0 | 0 |

Nearly 3 million monochromatic red 7-cliques exist. The graph sits exactly at the edge — one wrong move away from an avalanche of 8-cliques. Red and blue counts are identical at every level, confirming self-complementarity.

---

## What Happens When You Add One Vertex

Stored as graph_id=3 in `graph_paley` (Paley(281) + 1 random vertex, ~140 red / ~141 blue edges).

**Eight-clique count: 100,000,000+ (counting did not complete)**

After one hour of computation on Apple M4 Max, the Bron-Kerbosch counter found over 100 million red 8-cliques rooted at vertex 0 alone — and the sweep had not yet moved past the first vertex. Full count was projected to be in the hundreds of millions to low billions. Counting was abandoned.

### Why the explosion is so violent

Since Paley(281) has zero 8-cliques among its 281 original vertices and those edges are unchanged, **every 8-clique in the 282-vertex extension must include the new vertex**. Each new clique has the form:

```
{new_vertex, v₁, v₂, v₃, v₄, v₅, v₆, v₇}
```

where {v₁, ..., v₇} is a monochromatic 7-clique within the new vertex's same-colored neighborhood.

The new vertex has ~140 red edges. Paley(281) contains 2,989,840 red 7-cliques. With random edge assignment, each existing vertex is a red neighbor with probability 140/281 ≈ 49.8%, so the chance a particular red 7-clique falls entirely within the red neighborhood is:

```
(140/281)⁷ ≈ 0.78%
```

Naive expected red 8-cliques from direct conversion: `2,989,840 × 0.0078 ≈ 23,350`

The empirical count (100M+ from one vertex alone) is orders of magnitude beyond this estimate, because:

1. **Combinatorial cascade.** The 11M red 6-cliques can combine with neighborhood vertices to form 7-cliques that didn't exist in Paley(281), each becoming a new 8-clique.
2. **Dense neighborhood subgraphs.** The ~140 red neighbors induce a Paley-like dense subgraph where near-cliques abound.

The vertex-transitive symmetry of Paley graphs means each vertex's neighborhood is structurally identical and equally clique-rich. A random extension is the worst case.

---

## Why This Defines the Search Problem

Adding the new vertex without exploding the clique count is itself a constraint problem:

- 281 binary variables (which existing vertices the new vertex connects to with red vs blue)
- One constraint per existing 7-clique: the new vertex must NOT be same-color-connected to all 7 members of any monochromatic 7-clique

That's ~6M constraints (3M red + 3M blue) over 281 variables — a SAT problem of moderate size. Whether it is satisfiable is equivalent to asking whether R(8,8) ≥ 283: an open question.

In practice, no satisfying assignment has been found by direct SAT solving on the constraint set, so the search proceeds via a different route: **start from any extension** (initial graph at 100M+ cliques) **and apply iterative mutation to drive the count toward zero**. Every stage of the campaign reduces the number of cliques further; the descent from 100M to ~792K covers more than two orders of magnitude.

---

## Paley(289) Reference Point

Paley(289) is also stored (graph_id=2). 289 = 17², 289 ≡ 1 (mod 4).

**Eight-clique count: 11,246,724 (red: 5,623,362, blue: 5,623,362)**

This is referenced as a structural comparison: another self-complementary algebraic construction in the same vertex-count regime has 11.2M cliques. The 282-vertex search has driven a Paley(281)+1 seed below that level, demonstrating the local-search approach can outperform algebraic regularity in raw clique count.

---

## Implications for Search Direction

1. **Paley(281) is the natural seed**, not a competitor. Adding a vertex is the actual problem.
2. **The first vertex is the hardest decision.** All ~6M 7-clique constraints must be respected to keep the new vertex's contribution minimal. Random assignment loses; smart assignment plus local optimization is the practical path.
3. **The graph's algebraic structure decays under mutation.** The vertex-transitivity of Paley(281) is broken by the first random extension and never recovered. From that point on, the graph is purely an evolved combinatorial object — there is no closed-form structure to exploit.
4. **Vertex-transitivity is not preserved**, but locally-uniform structure may be. Empirically, evolved graphs at this depth show tight degree distributions and near-constant triangle counts — these uniformity signals are worth tracking as the search proceeds.

---

## Appendix: graph_paley Table Contents

| graph_id | vertices | clique_count | Description |
|----------|----------|--------------|-------------|
| 1 | 281 | 0 | Paley(281) — proves R(8,8) ≥ 282 |
| 2 | 289 | 11,246,724 | Paley(289) — structural reference |
| 3 | 282 | 100M+ (incomplete) | Paley(281) + 1 random vertex |

---

*Generated for the Ramsey project — May 2026*

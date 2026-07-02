# Paley Graphs as Seed Construction for the 282-Vertex Search

**Date:** 2026-05-02
**Author:** Ben Ferenchak + Claude
**Status:** Reference — documents the seed-graph rationale for the R(8,8) ≥ 283 search

> **UPDATE 2026-06-27 — literature confirms Paley(281) is the WORLD-RECORD base, and a non-circular base-hunt locked it.** R(8,8) ≥ 282 is exactly Paley(281) being K8-free (Radziszowski survey item 2.3.j; credited Burling–Reyner 1972; **unbeaten ~50 years**). 282 isn't prime, so the Paley route can't reach a 282-vertex witness. A non-circular base-improvement hunt (minimize mono-7 on Paley(281) under hard mono-8 = 0, `wmaxsat_pilot base-sweep`) found **0 improvements across ~325k exact windows up to |F|=64** — Paley(281) is locally locked even off the circulant family. See **`windowed-maxsat-investigation.md`** and `search-status-2026-06-14.md` (06-27 update). Full literature record: memory `reference_r88_literature`.

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

> ⚠️ **This section's headline number is WRONG — see "UPDATE 2026-06-14" below.** The true count for a random extension is **~44,000** (structurally bounded by Paley's 5,979,680 mono-7-cliques, since every 8-clique must pass through the new vertex), and a row-optimized extension is ~27K. The "100M+" came from a buggy/misread count. The section is preserved because its *qualitative* point (the extension, not Paley, is the whole problem) is right; the cascade arithmetic below is not.

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

## UPDATE 2026-06-14 — The optimized single-vertex extension is ~27K, not 100M (and 28× better than the campaign)

The "100M+" figure above is a **counting error** (likely non-maximal overcount or a bug): every 8-clique in a one-vertex extension must contain the new vertex and corresponds to a distinct mono 7-clique of Paley(281) that the new vertex monochromatically completes, so the count is **bounded by 5,979,680** (the mono-7-clique total) and is far lower for any reasonable row. Measured directly (tool `single-flip-check/src/bin/paley_gen.rs` + `clique_census.rs`, generator validated: it reproduces Paley(281)'s exact 5,979,680 mono-7 / 0 mono-8 counts):

- **A random extension has only ~44,000 mono 8-cliques** (seed 1: 44,062), matching the doc's own naive estimate (~23K per color), not 100M.
- **Row-optimizing just the new vertex against fixed Paley(281)** (validated `rowopt_pilot vertex`, the MaxSAT local-search the original direct-SAT attempt lacked) drops it to **~27,401** (8 random starts: 27,401–28,815, all recount-verified). All cliques are through the new vertex; Paley(281) is left intact.

**The headline comparison:** a few minutes of "extend Paley(281) optimally" yields a 282-vertex graph with **27,401** mono 8-cliques — versus **775,623** after two months and 2,431 stages of the full-mutation campaign (which started at 817,828 and descended only ~5%). The campaign's mutate-all-edges strategy destroyed Paley's structure and wandered ~28× higher than the construction it started from. **The search has been running in the wrong region.**

Caveats: (1) ~27K is a robust local-min band for single-vertex row-opt, not zero — reaching zero (R(8,8) ≥ 283) is not achieved this way and remains open. (2) Reducing below ~27K requires mutating Paley(281)'s own edges, which risks the documented avalanche. (3) But as a **seed**, 27K dominates 775K by the objective.

### Descent probe — the 27K seed is a deep LOCKED minimum (avalanche confirmed)

To test whether 27K is exploitable (descends toward 0) or a narrow well (blows up), a row-opt sweep was run on a sample of the 27K graph's vertices (0, 40, 80, 120, 160, 200, 240, 280, 281):

- **All 8 sampled Paley vertices (0–280) are row-locked** — re-optimizing any of their rows yields zero improvement (delta 0, distance 0), and **their best single edge flip *worsens* the count by +170 to +605** (avalanche signature: touching Paley's knife's-edge structure explodes cliques).
- Only the **new vertex (281)** has any give, and it is near its floor — a fresh row-opt squeezed 27,401 → 27,085 (−316) once via SATLike but greedy reproduces no further gain. The descent is confined to the new vertex's row and is essentially exhausted at ~27K.

**Conclusion: the Paley(281)+optimized-vertex graph (~27K) is a deep, strict local minimum — the same fundamental wall as the campaign's 775K graph, just 28× lower.** Local edge-flip search caps out at both. Re-seeding the campaign here would bank a 28× better best-known floor but would *not* descend toward zero (the engine's singles/pairs would lock immediately, as on graph 8348). Reaching zero (R(8,8) ≥ 283) is not accessible by local search from either basin; it would require a base construction with fewer mono-7-cliques than Paley(281) while keeping zero mono-8-cliques (open/hard), or exact methods (infeasible at 5.98M-clause scale).

### Reseed outcome (2026-06-14) — the construction seed DESCENDS via balanced pairs

Acting on the finding, campaign 2 (775,642) was retired and a **new campaign (id 10) was seeded from the 27,401 graph** (`single-flip-check/results/paley282_opt.txt`): all workers + QM stopped, graph 8389 / campaign 10 / stage 8389 inserted, campaign 2 set INACTIVE, `RAMSEY_CAMPAIGN_ID` 2→10 in compose, Portainer stack 7 redeployed. The QM seeded Redis from the graph (worker `total_pairs` guard passed); 14 workers + QM came up clean.

**The engine descends from the 27K seed** — 27,401 → 27,390 → 27,339 → … → 27,214 in the first ~6 minutes, ~30 s/advance, genuine improvements. This refines the descent-probe conclusion: the probe only tested *row rewrites* and *single flips* (both locked, avalanche), but the production engine's **balanced-pair** moves (one red→blue + one blue→red, preserving balance) were never probed and they descend freely. So the construction reseed *worked*: it dropped the active search ~28× below campaign 2 and into a productive basin. Where it plateaus is the new open question (a deeper wall will presumably reform; the local-search ceiling characterization above still applies — reaching zero still needs a better base than Paley(281) or exact methods).

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

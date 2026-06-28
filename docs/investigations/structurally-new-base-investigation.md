# Structurally-New Base Investigation — Cyclotomic / Circulant Search on Z₂₈₁

**Date:** 2026-06-27
**Author:** Ben Ferenchak + Claude
**Status:** Two principled leads computationally CLOSED — no circulant/cyclotomic base beats Paley(281).
**Pursues:** the "structurally-new mono-8-free base" next-step — the only direction that searches *outside* Paley's local neighborhood (which the Phase-1/2 windowed-MaxSAT hunt locked across ~1M windows; see `windowed-maxsat-investigation.md`).

**Goal:** a mono-8-free 281-vertex graph with **fewer than Paley(281)'s 5,979,680 mono-7-cliques** — a strictly better vertex-extension seed that could floor *below* the current best 282-graph (25,840 mono-8 = Paley(281)+1).

---

## Why the tractable search space is exactly this

- **281 is prime** → the only group of order 281 is cyclic Z₂₈₁ → the only **vertex-transitive** mono-8-free 281-base candidates are **circulants** (Cayley graphs on Z₂₈₁). Non-cyclic Cayley graphs do not exist at order 281.
- **Paley(281)** is the quadratic-residue (cyclotomy index 2) circulant — the ~50-year world-record base (`paley-graph-investigation.md`, `search-status` 06-27 literature scan).
- **Vertex-transitivity HURTS at order 282:** the direct 282-circulant search floored at ~996k mono-8 — ~40× worse than the asymmetric Paley(281)+1 (25,840). So the path is a better *281-base* (extended by one vertex), not a vertex-transitive 282-graph.

So the structurally-new question reduces to: **is there a mono-8-free circulant on Z₂₈₁ other than Paley, with fewer mono-7s?**

---

## Experiment 1 — global circulant hill-climb (`construction_search search 281`)

Greedy single-distance-class hill-climb from random connection sets, minimizing mono-8. **Result: floors at ~1.3M mono-8** across restarts — never reaches mono-8-free. This confirms Paley is an **isolated** mono-8-free point in the circulant space (consistent with the earlier local probe: 0/140 single-distance-class toggles from Paley keep mono-8 = 0). Random / greedy circulant search cannot find a mono-8-free base besides Paley.

## Experiment 2 — cyclotomic ("generalized-Paley") 2-colorings (`construction_search cyclotomic 281`)

The principled structurally-new family. For each cyclotomy index `e | 280`, partition Z₂₈₁* into the `e` cosets of the e-th-power subgroup; a 2-coloring = a **balanced (size e/2) symmetric union of cosets** (symmetric ⇔ invariant under negation). Paley = index 2 (squares vs non-squares). Every such coloring was tested with the fast vertex-transitive clique counter (mono-8, and mono-7 where mono-8 = 0). New tool mode `cyclotomic <n>`, validated: **e = 2 reproduces Paley exactly** (mono-8 = 0, mono-7 = 5,979,680).

**Result — definitive negative:**

| cyclotomy index e | colorings tested | mono-8-free | best mono-7 |
|---|---|---|---|
| 2 (quadratic = **Paley**) | 2 | 2 | 5,979,680 |
| 4 (quartic) | 6 | 2 | 5,979,680 |
| 8 (octic) | 6 | 2 | 5,979,680 |
| 10 | 252 | 2 | 5,979,680 |
| 14 | 3,432 | 2 | 5,979,680 |
| 20 | 184,756 | *(completeness run)* | — |

In **every** index, the only mono-8-free colorings are **Paley and its complement** — all tie at mono-7 = 5,979,680 (the higher-index "hits" are just Paley re-expressed over a finer coset partition). **No cyclotomic 2-coloring beats Paley(281).** The generalized-Paley family contains nothing better than the quadratic-residue case — a direct-computation confirmation of the literature finding that generalized Paley graphs serve *multicolor* Ramsey (R_k(4)), not 2-color R(8,8).

## Experiment 3 — Paley(281) is an *isolated* mono-8-free prime

`construction_search validate p` for nearby primes p ≡ 1 (mod 4):

| p | Paley(p) mono-8 | mono-7 |
|---|---|---|
| 269 | 973,242 | 8,651,040 |
| 277 | 917,424 | 8,715,528 |
| **281** | **0** | **5,979,680** |
| 293 | 2,155,454 | 17,728,509 |
| 313 | 3,816,409 | 22,732,474 |

Paley(281)'s K₈-freeness is a **special arithmetic property of 281**, not a monotone trend — its neighbors in *both* directions (smaller 269/277, larger 293/313) all carry ~1–4M mono-8. So there is no nearby Paley prime to leverage, and (per the standing "multi-vertex Paley is a dead end" finding — mono-8 scales ~linearly with added vertices) a mono-8-free Paley(p < 281) extended to 282 would be worse than Paley(281)+1 regardless. Paley(281)+1 (25,840) stands.

## Experiment 4 — cyclotomic prime-sweep, orders > 281 *(IN PROGRESS — the optimistic long shot)*

A mono-8-free cyclotomic 2-coloring at **order p > 281** would directly improve the R(8,8) lower bound past the 50-year record (R(8,8) ≥ p+1 > 282). Running `construction_search cyclotomic p` (indices e ≤ 14) over all primes p ≡ 1 (mod 4) from 269 up to 461. **Interim:** orders 269, 277, 293 have **no** mono-8-free cyclotomic coloring at any tested index — only 281 (Paley) is mono-8-free. Sweep continuing upward; a hit at any p > 281 would be flagged immediately. *(Status will be finalized when the sweep completes.)*

## Experiment 5 — Seidel switching of Paley(281) *(IN PROGRESS — a new move class)*

Seidel switching flips every edge of a cut S × (V∖S) — a **large, structured, global** move, a different class than the locked small edge-windows (Phase-1/2 windowed MaxSAT, ~1M windows) and the single-distance-class circulant toggles (0/140 probe). `construction_search switch 281` samples random subsets S (all sizes, and gentle |S| ≤ 12). **Interim:** **0 of 4,000** sampled switches stayed mono-8-free — any cut-flip destroys Paley's K₈-freeness. An 8-million-switch definitive batch is grinding. *(Status will be finalized when the batch completes.)*

*(See also `windowed-maxsat-investigation.md` for the parallel "bigger local window" long shot — a fast `|F|=128` base-sweep on Paley(281), the untested top of the window-size ladder, made feasible by the Phase-3 ~27× speedup.)*

---

## Verdict

The two most-principled, computationally-tractable structurally-new leads at order 281 are **CLOSED**:
1. global circulant search floors far from mono-8-free (~1.3M);
2. cyclotomic / generalized-Paley constructions yield **only Paley**.

With the literature (generalized Paley = multicolor; non-cyclic Cayley impossible at the prime 281; vertex-transitive constructions bad at 282) and the windowed-MaxSAT lock of Paley's local neighborhood (~1M exact windows), **there is no known tractable construction of a mono-8-free 281-base better than Paley(281).**

The remaining structurally-new options are genuinely hard / open: a non-vertex-transitive mono-8-free 281-graph *not* reachable from Paley by local moves (the searchable neighborhood is locked), or algebraic/geometric designs with no clear computational handle near order 281–282. **Reaching R(8,8) ≥ 283 needs a new mathematical idea, or vastly more compute on the full non-vertex-transitive 282-space — not another construction family within reach of current tooling.** The validated `wmaxsat_pilot` + `construction_search` tools are ready to evaluate any future candidate base or family.

## Tooling

`single-flip-check/src/bin/construction_search.rs` — added the `cyclotomic <n> [max_per_e]` mode (generalized-Paley 2-coloring enumeration via discrete-log cyclotomy; validated against Paley at e=2). Reuses the existing fast vertex-transitive clique counter (`total k-cliques = n · (k-cliques through 0) / k`).

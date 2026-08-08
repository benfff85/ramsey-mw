# The Frozen Paley Core — what campaign 10 was actually searching, and the split criterion

**Date:** 2026-08-08
**Author:** Ben Ferenchak + Claude
**Status:** CLOSED — the Paley(281)+1 construction is characterised. Its search space is a single
281-bit row, not 39,621 edges; the base itself is a rigid, isolated point; and reaching zero from it
requires a structural property (`max K7-free induced subgraph >= 141`) that it misses by ~36 vertices.
**Supersedes in part:** the "single/dual edge flip move class" framing in
`multiseed-basin-program-results.md`, and the coverage claims in `windowed-maxsat-investigation.md`
and `joint-reoptimization-investigation.md` — see §4.
**Tooling:** `single-flip-check/src/bin/k7free.rs` (new), `results/k7free/RUNBOOK.md` (full run log).

---

## 1. The measurement that started it

The 281-vertex core of **every** record graph the project has produced is bit-identical to Paley(281):

| graph | mono-8 | core-vs-Paley(281) Hamming |
|---|---|---|
| 276750 (incumbent) | 25,604 | **0** |
| 222120 | 25,618 | **0** |
| 26994 | 25,758 | **0** |
| 8644 | 25,840 | **0** |
| `paley282_opt` (original seed) | 27,401 | **0** |
| 53980 (mid-descent) | 394,915 | 753 |
| 274667 (post-kick) | 600,047 | 1,675 |

**519,000 stages did not change one edge of the core.** Every graph with a damaged core is 15–23x
worse. `rowopt_pilot` on graph 276750 confirms it directly: `clauses = 5,979,680` (exactly Paley's
mono-7 total) and `f0 = 25,604` — i.e. **all 25,604 cliques pass through the added vertex 281.**

## 2. So the search space is 281 bits, and the fleet was sweeping 392M units to explore it

With the core pinned, a work unit can only be productive if **both** its flipped edges lie in vertex
281's row (136 red x 145 blue), or it is a single flip within the row:

```
work units per stage       : 392,495,511
units that keep core intact:      20,001   (0.00510%)
   -> 1 productive unit per 19,623 evaluated
```

Everything else provably damages Paley and is rejected. The exhaustive engine has been re-proving
that ~392M times per stage.

This also explains the ILS behaviour. All 31 kicks fired from the same graph 276750; epoch floors
landed in 25,629–25,796; and of the five epoch-floor graphs whose `edge_data` survived the retention
prune, every one sits **14–23 edges** from the incumbent (and 20–33 from each other) after a kick
that flipped 3,840. The descent's job after a kick is to **rebuild Paley(281) and re-optimise the
row** — ~6,000 stages of a 14-worker fleet to redo a row optimisation. Every graph within 150 of the
incumbent that survived the prune is within **28 edges** of it.

## 3. The exact objective

With the core = Paley(281) (K8-free in both colours, so no mono-8 avoids the new vertex), writing `A`
/ `B` for the added vertex's red / blue neighbourhoods:

```
mono-8 count = #(red K7 induced on A) + #(blue K7 induced on B)
```

Paley is self-complementary, so both terms are "K7 induced on a vertex subset". Reaching **zero**
therefore requires `A` and `B` to both be K7-free, and `|A| + |B| = 281` forces

> **max |K7-free induced subgraph of Paley(281)| >= ceil(281/2) = 141.**

Measured: **105** (converged over 5 days, 6 seeds). Rigorously **<= 214** (see §5). Required: 141.

For calibration, a random row gives ~46,700 mono-8; the best ever found (25,604) is only **1.86x**
better — consistent with Paley's quasirandomness (eigenvalues 140, 7.88, -8.88).

## 4. This re-reads three of the project's "locked" results

**They sampled uniformly over an edge set that is 99.3% frozen.**

- **Windowed MaxSAT.** `select_window` seeds from a mono-8 clique's 28 edges — and since the core is
  K8-free, every mono-8 clique contains vertex 281, so exactly **7** of those 28 touch the row. The
  padding is uniform random over all 39,621 edges (0.71% row). So even at |F| = 128 a window frees
  **~7.7 of the 281 row bits**. Growing |F| grew coverage of edges that cannot move. The
  "~1.1M proven-optimal windows, zero escapes" result certifies the **core**, not the row.
- **2-vertex joint re-optimisation.** 300 *random* vertex pairs; expected number touching vertex 281
  is `300 x 281/39,621 = 2.1`. It essentially never tested the only movable structure.
- **The multi-seed basin program.** Described as exploring "single + dual edge flips". In fact all six
  seeds were Paley(281)+row constructions, so all six explored the same 281-bit row space from
  different starting rows. The ~3.2% floor spread is row-optimisation variance, not basin diversity.

None of those conclusions is *wrong* — but their scope is much narrower than stated.

## 5. What is rigorously proved

Direct SAT on "does Paley(281) have a K7-free induced subgraph of size >= s?" (cadical,
symmetry-broken; vertex-transitivity makes fixing vertex 0 sound for UNSAT):

| s | 245 | 240 | 235 | 230 | 225 | 220 | 215 | 210 |
|---|---|---|---|---|---|---|---|---|
| UNSAT in | 30s | 31s | 69s | 120s | 503s | 3,221s | 20,812s | (24h cap, unresolved) |

**RESULT: `max K7-free(Paley(281)) <= 214`.**

Runtime doubles every **2.0** steps of `s` in this regime, which puts `s = 141` at ~10^8 core-years.
Not reachable.

## 6. Rigidity — the result that closes the base direction

`k7free rigid`, exhaustive over every edge:

```
Paley(17):  0 of    136 single edge flips preserve mono-K4-freeness
Paley(281): 0 of 39,340 single edge flips preserve mono-K8-freeness
```

**Paley(281) is an isolated point in the space of valid K8-free bases.** Every single flip creates a
monochromatic K8 *in the base itself*. Flipping a blue edge red needs only a red K6 somewhere in the
~70-vertex common red neighbourhood, and Paley has far too many K6s for that to be avoidable.

Paley(17) — the unique R(4,4) witness — behaves identically, which is the expected signature.

This retro-explains the documented "avalanche" (+170 to +605 per core flip): that is not a count
effect, it is the base ceasing to be a legal base. Same for the 0/140 circulant-toggle result.

## 7. Phase transition — the criterion is not vacuous

The identical question on every Paley prime with omega = 7 (the same as Paley(281)):

| p | 149 | 157 | 181 | 193 | 197 | 229 | 241 | 269 | **281** |
|---|---|---|---|---|---|---|---|---|---|
| maxK7free / p | >0.50 | 0.535 | 0.486 | 0.487 | 0.457 | 0.389 | 0.390 | 0.349 | **0.363** |
| regime | SAT | SAT | transition | transition | UNSAT | UNSAT | UNSAT | UNSAT | **UNSAT** |

The half-threshold really is achievable up to p ~ 157 and really does stop below 181. **281 sits well
clear of the transition** — it is comfortably impossible, not marginally so.

## 8. Do NOT retry (all measured this week)

| route | why it fails |
|---|---|
| **Expander mixing / degree reduction** | needs `M6 >= 66` for s=141; measured `M6 >= 72`. Only bounds `s <= 152`. |
| **Pseudorandom clique-counting lemmas** | need `lam <~ d^(r-1)/n^(r-2) = 4.3` against `lam = 8.88`; **fails at r=7** — which is exactly why Paley(281) can be K8-free at all. |
| **WLOG-0 decomposition** `1 + f6(N(0)) + f7(Nbar(0))` | measured `1 + 72 + 87 = 160 > 141`. Too lossy. |
| **Partition / disjoint-K7 bounds** | `>= 225` and `>= 241`. Far too weak. |
| **Implicit hitting set (MaxHS scheme)** | degenerates: the core set grew to **77%** of the full K7 hypergraph, so the "relaxation" became the full problem inside a subprocess. Harvest-rate tuning (20/100/500/4000 per round) does not fix it — the bound only rises once the core set is nearly complete. |
| **Exact extension formulation** (281 vars, 5.98M clauses, no cardinality) | better-posed and validated against R(3,3)=6 and R(4,4)=18, but **every p from 137 to 269 timed out at 2h**; only p=113 solved. Not an easier question. |
| **Single-flip base search with the split objective** | the neighbourhood is EMPTY (§6). |

## 9. Validation record

| check | result |
|---|---|
| Paley(281) K7 / K8 count | 2,989,840 / 0 — matches the documented mono-7 total and rowopt's clause count |
| Paley(281)[N(0)] K6 count | 74,480 = 2,989,840 x 7 / 281 exactly |
| clique detector vs enumeration | agrees on **all 8,192 subsets** of Paley(13), r=3 and r=4 |
| size-CNF encoding | SAT exactly at the exhaustive max, UNSAT exactly one above — Paley(13) r=3 (7), Paley(17) r=3 (8), Paley(17) r=4 (17), Paley(5) r=3 (5) |
| IHS | matches exhaustive ground truth at **both** boundaries on six anchors |
| extension CNF | reproduces **R(3,3) = 6** and **R(4,4) = 18** as UNSAT |
| base hunt | on Paley(17) target 9 it correctly FAILS (success would imply R(4,4) >= 19) and the output is still K4-free both ways |
| every SAT model | independently re-verified by recomputing K_r-freeness from the model |

## 10. Where this leaves R(8,8) >= 283 — the glue construction

Ramsey numbers do **not** forbid the split. `|A|, |B| <= R(7,8) - 1` with `|A| + |B| = 281` forces
`R(7,8) >= 142`, and `R(7,8) >= 217` is known constructively. The obstruction is specific to
Paley(281)'s structure, not general.

That points at a construction the project has never tried. Writing `V(282) = A + B + {v}`:

| part | must satisfy | i.e. |
|---|---|---|
| **A** (red nbrs of v) | no red K7, no blue K8 | a **(7,8)-Ramsey graph** |
| **B** (blue nbrs of v) | no blue K7, no red K8 | an **(8,7)-Ramsey graph** |
| **cross** | no mono-K8 spanning A and B | the only free part |

Both halves can be taken **off the shelf** — R(7,8) >= 217 means explicit 216-vertex (7,8)-graphs
exist and any induced subgraph of one still qualifies. The split is free in
`|A| in [65, 216]`, `|B| = 281 - |A|` (both parts must fit under 216), so there are ~150 distinct
configurations, not one. The entire difficulty localises into the `|A| x |B|` cross edges — at a
balanced split, 141 x 140 = **19,740** free bits, with the hard within-part structure chosen rather
than evolved.

### 10.1 Falsification test — RUN 2026-08-08, and it kills the glue SEARCH

Clique counts modelled as `C(n,i) * 2^-C(i,2) * s_i` with the suppression `s_i` calibrated against
real Paley(109)/Paley(281) counts (ratios 0.97 / 0.85–0.94 / 0.57–0.81 / 0.22–0.56 / 0.25 at
i = 3..7) and rounded **favourably to the construction**. Expected mixed mono-K8s under a random
glue, red + blue:

| \|A\| | \|B\| | cross bits | nats available | E[mixed mono-K8] |
|---|---|---|---|---|
| 65 | 216 | 14,040 | 9,732 | 2.07e6 |
| 141 | 140 | 19,740 | 13,683 | **3.39e6** |
| 216 | 65 | 14,040 | 9,732 | 1.69e6 |

A random glue therefore starts at ~1.7–3.4M mono-8 — only **2–4x better than a fully random
282-colouring** (6.7e6) and **~100x WORSE than Paley+1's starting point** (46,700 for a random row,
25,604 optimised). First moment: expected number of zero-mono-K8 glues is `e^(nats - E)` ≈ `e^-3.4e6`.

For a glue *search* to be viable, `E` would have to fall below the ~13,683 nats of freedom in the
cross edges — a **248x** reduction, i.e. every clique count in both halves smaller by a factor of 16.
Paley's own suppression at i=6 is 0.30; this needs ~0.019. Not plausible.

**Verdict: searching the cross edges is dead.** The interface has ~19,740 degrees of freedom against
~10^6 cross constraints; it is hopelessly over-constrained.

### 10.2 The caveat that matters — and what it means

**The first moment does NOT prove no glue exists.** The identical argument applied to the whole
problem says: 39,621 bits = 27,463 nats against ~5.9e6 expected mono-K8 for a random 281-colouring,
so "no zero-colouring exists" — yet **Paley(281) achieves exactly zero.** Structure beats the random
bound by an unbounded factor. So the test rules out *random and locally-searched* glue, not a
*structured* one.

But that is precisely the point: the decomposition told us **what** to look for and does **not** make
it easier. A structured interface between two Ramsey halves is the same open problem in new clothing,
and we have no candidate structure for it. The difficulty was never in the halves — those come off
the shelf — it is in making the whole object algebraically coherent, which is exactly what Paley does
and what nothing else known does.

**Do not build the glue search.** Revisit only with a specific algebraic candidate for the interface,
not with a solver.

### 10.3 What survives

The screening criterion. `k7free search <p> 7` scores any candidate base by its split in seconds, and
`k7free rigid` reports whether the base has any legal single-flip neighbourhood at all. Both are
validated (§9). Any future base — from any source — can be triaged in minutes instead of being handed
to a fleet for months. That is the reusable output of this investigation.

## 11. Methodological note

Two estimates in this work collapsed on measurement, both for the reason this repo has now recorded
seven times:

1. **"~250 core-days to prove s=141", later ~10^8 core-years.** The fit was taken from the *easy* end
   of the ladder (doubling every 5.1 steps) and the hard regime doubles every 2.0. Extrapolating a
   difficulty curve from outside the regime you care about is the same error as applying a speedup
   ratio to the wrong denominator.
2. **"IHS avoids the full hypergraph."** It does not; the core set grew to 77% of it.

The standing rule from `post-hoist-bottleneck-review.md` — *work out which fraction of the cost a
change touches before working out how much faster it makes that fraction* — has a companion:
**never fit a cost curve outside the regime you intend to operate in.**

---

*The Paley(281)+1 construction is now characterised rather than merely stuck: a 281-bit search space,
an isolated and unmovable base, and a structural requirement it misses by ~36 vertices. Reaching
R(8,8) >= 283 needs a different base, and the glue construction in §10 is the first concrete
candidate the project has had that is not Paley-derived.*

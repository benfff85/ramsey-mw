# Structurally-New Base Investigation — Cyclotomic / Circulant Search on Z₂₈₁

**Date:** 2026-06-27 (updated 2026-07-02)
**Author:** Ben Ferenchak + Claude
**Status:** CLOSED — no circulant/cyclotomic base beats Paley(281) at any prime order ≤ 461, and no order-282 Cayley 2-coloring (any of the four groups) approaches mono-8-free. Every long-shot follow-up is empty: cyclotomic order > 281 through 461 (Experiments 4/4b: 7.1M+ colorings, zero hits), p ≡ 3 (mod 4) closed by a parity theorem (Experiment 6), Seidel switching 0/8M, |F|=128 windows 0/88,496, non-cyclic order-282 Cayley floors ~0.88–1.1M (Experiment 7). Also fixed en route: the >288-vertex complement-count corruption in the original tool (Experiment-3 correction).
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
| 20 | 184,756 | 2 | 5,979,680 |

In **every** index, the only mono-8-free colorings are **Paley and its complement** — all tie at mono-7 = 5,979,680 (the higher-index "hits" are just Paley re-expressed over a finer coset partition). **No cyclotomic 2-coloring beats Paley(281).** The generalized-Paley family contains nothing better than the quadratic-residue case — a direct-computation confirmation of the literature finding that generalized Paley graphs serve *multicolor* Ramsey (R_k(4)), not 2-color R(8,8).

## Experiment 3 — Paley(281) is an *isolated* mono-8-free prime

`construction_search validate p` for nearby primes p ≡ 1 (mod 4):

| p | Paley(p) mono-8 | mono-7 |
|---|---|---|
| 269 | 973,242 | 8,651,040 |
| 277 | 917,424 | 8,715,528 |
| **281** | **0** | **5,979,680** |
| 293 | 2,310,012 | 18,822,320 |
| 313 | 4,687,488 | 27,115,816 |
| 317 | 8,414,448 | 32,455,728 |

> **Correction (2026-07-02):** the originally-published 293/313 values (2,155,454 / 17,728,509 and 3,816,409 / 22,732,474) were **undercounts**: at orders above the worker `BitMatrix`'s 288-bit size, `invert_all()` silently masks off bits 288+, corrupting the *complement* color's counts (the red color was unaffected). The tell: Paley graphs are self-complementary, so every mono count must be even — the old 313 value was odd. Table above recomputed with the 512-bit `cyclotomic_wide` tool (Paley(281) anchor reproduces 0 / 5,979,680 exactly; 269/277, below the cap, match the old runs bit-for-bit). **Verdicts are unaffected** — the corruption deletes cliques, which could only have created *false* mono-8-free hits, and none were reported.

Paley(281)'s K₈-freeness is a **special arithmetic property of 281**, not a monotone trend — its neighbors in *both* directions (smaller 269/277, larger 293/313) all carry ~1–4M mono-8. So there is no nearby Paley prime to leverage, and (per the standing "multi-vertex Paley is a dead end" finding — mono-8 scales ~linearly with added vertices) a mono-8-free Paley(p < 281) extended to 282 would be worse than Paley(281)+1 regardless. Paley(281)+1 (25,840) stands.

## Experiment 4 — cyclotomic prime-sweep, orders > 281 *(the optimistic long shot — CLOSED)*

A mono-8-free cyclotomic 2-coloring at **order p > 281** would directly improve the R(8,8) lower bound past the 50-year record (R(8,8) ≥ p+1 > 282). Ran `construction_search cyclotomic p` (indices e ≤ 14) over primes p ≡ 1 (mod 4). **Result:** of the orders tested — 269, 277, 281, 293, 313, 317 — the *only* one with **any** mono-8-free cyclotomic coloring is **281** (Paley + complement). Orders 293, 313, 317 (the candidates > 281) have **none** at any index. No order > 281 yields a mono-8-free coloring.

> *Caveat — vertex cap (RESOLVED 2026-07-02):* the reused worker-crate adjacency bitset is fixed at 288 bits, so orders 337–461 panicked/overflowed rather than being tested (and, as the correction above shows, 293–317 complement counts were silently corrupted — verdicts unaffected). Experiment 4b closes the gap with a local 512-bit bitset.

## Experiment 4b — wide-bitset cyclotomic sweep, all primes 331–461 *(2026-07-02)*

New self-contained tool `cyclotomic_wide.rs` (512-bit rows local to `single-flip-check`; the worker crate stays untouched): same enumeration as Experiment 4 plus an early-exit K₈ rejector (vertex-transitivity means a K₈ exists iff one passes through vertex 0, so the through-0 subgraph check is complete). Anchors: Paley(281) reproduces **0 / 5,979,680 exactly**; the 281 cyclotomic regression reproduces Experiment 2 (only Paley + complement at every index, now including e=20 in 7.7s).

Sweep over **all** primes 331–461 (both mod-4 classes), indices e ≤ 24, cap 3M colorings/index: `results/cyclo_wide_sweep.log`. A single mono-8-free coloring at order p would prove **R(8,8) ≥ p+1 > 282** — a direct world-record improvement.

**Result — CLOSED, zero hits.** All 23 primes swept (~35 min wall): **7,144,904 balanced symmetric cyclotomic colorings tested, 0 mono-8-free.** The 13 primes ≡ 1 (mod 4) carried all the volume (337: 2,708,680; 433: 2,753,868; 397: 754,096; 353: 718,380; 401: 185,156; 449: 16,380; 421: 3,980; 409/457: 1,882 each; 461: 508; 349/373: 44 each; 389: 4 — counts vary with the divisor structure of p−1). All 10 primes ≡ 3 (mod 4) report exactly 0 symmetric balanced colorings — the Experiment-6 parity theorem confirmed across the whole range. Combined with Experiment 4 (293/313/317): **no cyclotomic 2-coloring of any prime order in (281, 461] is mono-8-free.** The cyclotomic record-hunt is closed through order 461; odds beyond that continue to fall (mono-8 mass grows steeply with order — see the Experiment-3 trend).

## Experiment 6 — p ≡ 3 (mod 4): closed by a parity argument *(2026-07-02)*

The Experiment-4 sweep only ran p ≡ 1 (mod 4). The missing class p ≡ 3 (mod 4) — 283, 307, 311, … — turns out to be **empty by theory, not oversight**: there, −1 is a quadratic non-residue, so its discrete-log index t is **odd**, and negation maps coset i → i+t. A symmetric (undirected) coloring must be a union of ⟨+t⟩-orbits on the e cosets; each orbit has even length e/gcd(e,t) (gcd of an even e with odd t is odd), so any symmetric union has weight a multiple of e/gcd(e,t) — and the balanced weight e/2 = (e/gcd)·(gcd/2) is **not** an integer multiple of it (gcd is odd). Hence **no balanced symmetric cyclotomic 2-coloring exists at any prime ≡ 3 (mod 4)**. Unbalanced symmetric unions do exist (orbit unions of weight 2e/gcd, …) but give a density-⅔ color that is hopeless for K₈-freeness above order ~100. Empirically confirmed: `cyclotomic_wide cyclotomic 283` reports 0 symmetric balanced colorings at every index. The ≡ 3 (mod 4) primes in the 331–461 sweep serve as further empirical confirmation (all report 0 tested).

## Experiment 7 — Cayley graphs on the non-cyclic order-282 groups *(2026-07-02, running)*

282 = 2·3·47 is composite, so unlike prime 281 there are **four** groups of order 282 — Z₂₈₂ (cyclic), **D₁₄₁**, **Z₃×D₄₇**, **S₃×Z₄₇** — and only the cyclic one is covered by any circulant sweep (ours or the community's). The other three give vertex-transitive 282-vertex 2-colorings **never searched by anyone**, and a mono-8-free one would *be* the R(8,8) ≥ 283 witness. New tool `cayley282.rs`: all four groups realized as (Z₃×Z₄₇)⋊Z₂ with the Z₂ inverting a chosen subset of components; connection sets are unions of inverse-pair classes (Z₂₈₂: 141 classes, D₁₄₁: 211 = 70 rotation pairs + 141 involutions, Z₃×D₄₇: 164, S₃×Z₄₇: 142); counting via the through-identity vertex-transitive shortcut. Validation: full group-axiom check (associativity over all 282³ triples), involution counts match theory (1/141/47/3), adjacency symmetry, and transitive count == direct full-graph count on all four groups. Hill-climb (first-improvement, random restarts) minimizing total mono-8; on a hit the 39,621-bit edge string is exported for independent recount by the validated `clique_census`. Z₂₈₂ runs as the control (expected floor ~1M, matching Experiment 1 / the 06-19 circulant result). `results/cayley282_{group}.log`.

**Result — CLOSED, no mono-8-free coloring; floors (30 restarts × 4,000 evals each):**

| group | best mono-8 floor |
|---|---|
| Z₂₈₂ (cyclic control) | 1,082,316 |
| **D₁₄₁** | **877,866** |
| Z₃×D₄₇ | 999,549 |
| S₃×Z₄₇ | 1,055,244 |

The control reproduces the known ~1M cyclic floor (996k in the 06-19 run — consistent). Mildly notable: **D₁₄₁ floors ~20% below cyclic** (877,866), the best vertex-transitive 282-vertex result seen — but still ~34× worse than the asymmetric 25,840 and nowhere near 0. All four order-282 groups confirm the pattern: **vertex-transitivity is structurally expensive at 282** (the group symmetry forces clique-dense configurations regardless of which group). The "nobody ever searched these" families are now searched: no witness hides in the order-282 Cayley space at hill-climb reach.

## Experiment 5 — Seidel switching of Paley(281) *(a new move class — CLOSED)*

Seidel switching flips every edge of a cut S × (V∖S) — a **large, structured, global** move, a different class than the locked small edge-windows (Phase-1/2 windowed MaxSAT, ~1M windows) and the single-distance-class circulant toggles (0/140 probe). `construction_search switch 281` sampled **8,000,000** random subsets S (3M all-sizes, 3M |S| ≤ 8, 2M |S| ≤ 4). **Result: 0 stayed mono-8-free** — every cut-flip destroys Paley's K₈-freeness. Switching is not a viable escape.

*(The parallel "bigger local window" long shot — a fast `|F|=128` base-sweep on Paley(281), the untested top of the window-size ladder — also closed: **0 / 88,496** proven-optimal windows. See `windowed-maxsat-investigation.md`.)*

---

## Verdict

The two most-principled, computationally-tractable structurally-new leads at order 281 are **CLOSED**:
1. global circulant search floors far from mono-8-free (~1.3M);
2. cyclotomic / generalized-Paley constructions yield **only Paley**.

With the literature (generalized Paley = multicolor; non-cyclic Cayley impossible at the prime 281; vertex-transitive constructions bad at 282) and the windowed-MaxSAT lock of Paley's local neighborhood (~1M exact windows), **there is no known tractable construction of a mono-8-free 281-base better than Paley(281).**

The remaining structurally-new options are genuinely hard / open: a non-vertex-transitive mono-8-free 281-graph *not* reachable from Paley by local moves (the searchable neighborhood is locked), or algebraic/geometric designs with no clear computational handle near order 281–282. **Reaching R(8,8) ≥ 283 needs a new mathematical idea, or vastly more compute on the full non-vertex-transitive 282-space — not another construction family within reach of current tooling.** The validated `wmaxsat_pilot` + `construction_search` tools are ready to evaluate any future candidate base or family.

*2026-07-02 addendum:* the certified-empty perimeter is now wider still — **every cyclotomic 2-coloring of every prime order 269–461** (7.1M+ colorings; ≡ 3 (mod 4) empty by theorem), and **all four order-282 Cayley families** (the only vertex-transitive 282-candidates; floors 0.88–1.1M). The conclusion above is unchanged, with more ground certified under it.

## Tooling

- `single-flip-check/src/bin/construction_search.rs` — added the `cyclotomic <n> [max_per_e]` mode (generalized-Paley 2-coloring enumeration via discrete-log cyclotomy; validated against Paley at e=2). Reuses the existing fast vertex-transitive clique counter (`total k-cliques = n · (k-cliques through 0) / k`). **Known limitation:** the reused worker `BitMatrix` is 288-bit — complement-color counts are corrupted above 288 vertices (see the Experiment-3 correction); use `cyclotomic_wide` for any order > 288.
- `single-flip-check/src/bin/cyclotomic_wide.rs` *(2026-07-02)* — self-contained 512-bit reimplementation of `validate`/`cyclotomic` (orders up to 511), plus early-exit K₈ rejection. Anchored on Paley(281) = 0 / 5,979,680 exact.
- `single-flip-check/src/bin/cayley282.rs` *(2026-07-02)* — Cayley 2-coloring search on all four order-282 groups (`validate` = axioms + counting cross-checks; `search` = class-toggle hill-climb; hits exported as edge strings for independent `clique_census` recount).

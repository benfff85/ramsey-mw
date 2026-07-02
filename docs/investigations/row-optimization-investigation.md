# Vertex-Row Re-Optimization — Investigation

**Date:** 2026-06-12 (pilot) → 2026-06-13 (full sweep)
**Author:** Ben Ferenchak + Claude
**Status: RESOLVED 2026-06-13 — graph 8348 (campaign 2 best, 775,642 cliques) is ROW-LOCKED.** A full 282-vertex sweep found zero improving rows. The single-vertex move class is exhausted on this lineage; the next productive move space must be multi-vertex. **Do not re-run the sweep on graph 8348.** The tool is validated and reusable on *future* best graphs when a deeper wall forms.

**Related documents:**
- `sat-solver-investigation.md` — windowed/local MaxSAT (Tier 2); row-opt is its degenerate 1-vertex window, and this result motivates widening it
- `engine-optimization-review.md` — strategic layer §7 listed row-opt as the next move space; this note resolves it
- `archive/deep-analysis-path-forward.md` / `archive/may-2026-next-steps.md` — strategic roadmap of the era (both archived; live roadmap: `july-2026-next-steps.md`)
- One-off tool & data: `single-flip-check/src/bin/rowopt_pilot.rs`, inputs/logs in `single-flip-check/results/`

---

## TL;DR — what was tried and why we should not repeat it

We asked, for the current best graph: **can re-choosing any single vertex's entire 281-bit row reduce the monochromatic 8-clique count?** This is a 2^281 neighborhood per vertex that *subsumes* every single edge flip and every within-star edge pair (same-color pairs included — which the production engine never searches), plus arbitrary-depth coordinated rewrites of one vertex's star.

Answer on graph 8348: **no, for all 282 vertices.** Not one vertex has even a *neutral* single flip, and ~134K SATLike moves per vertex found nothing better at any depth. The graph is a strict, deep, single-vertex-star local minimum — fully consistent with the live engine advancing only +2 per stage by exhaustion fallback.

**Operational takeaways:**
1. Don't expect singles, within-star pairs, or single-row rewrites to break the 775,642 wall — that entire class is empty here.
2. Don't re-run the sweep on 8348; the answer won't change without a longer budget than is justified.
3. The next move space is genuinely **multi-vertex** (2-vertex joint re-optimization → windowed MaxSAT). See "Strategic implication" below.
4. The tool is correct and fast (~1.1 s clause build/vertex); **re-run it on the new best graph if a deeper wall ever forms.**

---

## The formulation (exact, validated)

Fix a vertex v. Every monochromatic 8-clique through v is exactly a monochromatic 7-clique in G−v whose seven vertices are *all* colored the clique's color by v's row. The other 281 vertices' mutual edges are untouched when v's row is rewritten, so for vertex v:

- The clause set is **static**: one clause per mono 7-clique of G−v (~8.66M clauses at 282v), each tagged red or blue.
- `f(row)` = number of violated clauses = mono 8-cliques through v. **Total cliques = (cliques avoiding v, a constant) + f(row)** — an *exact* objective, not a heuristic.
- The incumbent row is a known-good warm start (~21–24K violations out of ~8.66M).

This is the **1-vertex window** of local MaxSAT. It's also the classic "one-vertex extension" move from Ramsey lower-bound constructions, applied to the evolved core.

## The tool

`single-flip-check/src/bin/rowopt_pilot.rs` (one-off crate, path-dep on the worker crate; **not in any production repo**). Modes:

- `paley17` — correctness anchor (below).
- `known <stages_tsv> <stage_from> [budget_s]` — recover a known historical answer.
- `vertex <bits_file> <n> <k> <v,v,...> [budget_s]` — merit/sweep scan.

Per vertex: build static clause DB → exact incremental match-counters with make/break bookkeeping → greedy best-bit descent → SATLike (WalkSAT over violated clauses with additive clause weighting, WP=0.15) from the incumbent plus two perturbed restarts (Hamming 8 and 32). Any reported improvement is re-validated by a from-scratch Bron-Kerbosch recount of the spliced full graph before being believed. ~1.1 s build, ~134K moves in a 120 s budget, ~420 MB/vertex.

---

## Phase 1 — Pilot (2026-06-12): machinery validated, merit signal mixed

Three layers, each against ground truth. **All PASS.**

1. **Paley(17) anchor.** Damaged Paley(17) (R(4,4)-extremal), row-opt one vertex, and brute-forced all 2^16 candidate rows: the optimizer reached the true exhaustive optimum. Clause reduction matched brute-force mono-K4 counts on 150 random rows.
2. **Known-answer recovery (campaign 1, 288v), 3/3.** Three historical within-star wins reproduced exactly by the counters *and* rediscovered by search, with exact spliced recounts:
   - stage 3273→3274, vertex 204, **−213**
   - stage 1639→1640, vertex 229, **−181** — found even though **every** single flip at that star worsens (best +5); proves the search punches through singles-locked landscapes.
   - stage 146→147, vertex 2, **−178**
   Corollary: 3273/146 were reachable as two chained greedy singles — campaign 1's within-star pair wins were largely chained singles, and campaign 1 never searched singles at all.
3. **Stage 8319 merit scan (10 stars × 120 s).** All four known census singles rediscovered from both endpoints (−4/−4/−3/−2; recounts hit 775,838/839/840 exactly — 775,838 = stage 8320's real production value). The hot-edge stars (v53/v167, all singles ≥ +10) found nothing. **No depth-≥2 win surfaced** on the ten tested stars.

Pilot verdict: the tool is exact and capable; the unique value of row-opt over production (same-color within-star pairs + depth ≥3) was *unproven* on 8319's sample — motivating the full sweep on the *current* best graph.

## Phase 2 — Full sweep (2026-06-13): graph 8348 is row-locked

**Why 8348:** the campaign 2 wall re-formed at **775,642** cliques (graph 8348, found 2026-06-12 19:10 UTC). Stages 8349–8351 then advanced only +2 each (~95 min apart) by exhaustion fallback — full `DUAL_EDGE_CARDINALITY_WITH_SINGLES` sweeps (both-color singles + ~392M red×blue pairs) finding zero genuine improvements.

**Run:** `rowopt_pilot vertex graph_8348_edge_data.txt 282 8 0..281 120`, all 282 vertices, 120 s each, serial, ~9.4 h wall clock under `caffeinate`. Input = MySQL `graph_id` 8348.

### Results

| Metric | Value |
|---|---|
| Vertices swept | 282 / 282 |
| Improvements found | **0** |
| Best improvement | none — every vertex stayed at its incumbent row (distance 0) |
| Base enumeration | 775,642 (matches MySQL graph 8348) |
| Clause DB per vertex | ~8.66M mono 7-cliques, 1.1 s build |
| SATLike moves per vertex | mean 134,488 (min 128,048, max 145,184) |
| **Verdict** | **Row-locked** — no single-vertex row rewrite, at any depth, reduces the count within budget |

### Validation gates — all held

- **Base count** = 775,642, exactly matching MySQL `graph_id` 8348.
- **Cross-check invariant:** per-vertex incumbent counts (cliques through v) sum to **6,205,136 = exactly 8 × 775,642**. Each 8-clique is counted once through each of its 8 vertices, so this independently confirms all 282 clause databases were built correctly — every per-vertex reduction, not just the total.
- **No spurious improvements:** zero; the search never beat any incumbent, so no recounts were triggered.

### How deep the basin is

- **Not one vertex has an improving single-bit move.** Best single-bit flip across all 282 vertices: min **+2** (worsens), median +48, mean +49, max +118. Zero vertices have even a *neutral* (delta-0) single bit.
- **No deeper single-star rewrite helps.** ~134K SATLike moves/vertex with clause weighting and two perturbed restarts; every run returned to (or never left) the incumbent. Calibration: the pilot's hardest known positive (stage 1639's −181, through an all-worsening-singles landscape) was found in **52,800 moves** — here 2.5× that budget found nothing, uniformly across all 282 vertices.
- **Most marginal stars** (best single +2): vertices **159, 179, 187, 29** — natural seeds for the next move space.

### Interpretation

The single-flip + both-color-singles + balanced-pair engine has driven campaign 2 into a basin where the **entire single-vertex-star move class is exhausted**: every single flip, every within-star pair (same-color included), and every arbitrary-depth rewrite of one vertex's 281-bit row. This matches the live engine exactly (stages 8349–8351, +2 by exhaustion only).

**Honest caveat:** local search cannot *certify* optimality — 2^281 per vertex is uncountable and 134K samples is a vanishing fraction. But the budget was calibrated against a known-hard positive, the result is uniform across all 282 vertices, and it converges with independent evidence (the production engine is equally stuck). "Row-locked" is the correct operational conclusion, not a proof of optimality.

---

## Strategic implication — widen the window

Row-opt is the 1-vertex window of local MaxSAT. The clean sweep says that window is empty here, so the next productive move space must be **genuinely multi-vertex** — coordinated rewrites spanning two or more stars *simultaneously*, which neither the production engine nor row-opt can reach. Direct next rung:

- **2-vertex joint re-optimization** — ✅ DONE 2026-06-13 (`joint-reoptimization-investigation.md`): the 2-vertex window was built, validated, and run on 14 best-seeded pairs (marginal + highest-coupling). **0 hits.** Not exhaustive (14 of 39,621 pairs), but both principled seed strategies came up empty with the same strict-local-optimum signature.
- **windowed MaxSAT** proper (`sat-solver-investigation.md` Tier 2): k-vertex windows solved exactly/near-exactly — the remaining algorithmic rung, now with 1- and 2-vertex windows both empty below it (diminishing-returns bet).

This investigation converts "campaign 2 is probably stuck" into "campaign 2 is **row-locked at 775,642**"; the follow-on joint investigation extends that to 2-vertex windows. With every small-window local move empty, the live strategic question is **escalate to windowed MaxSAT vs. diversify the search (multi-seed / different construction)** — see the joint doc's verdict.

## When to reuse this tool

Re-run the `vertex` mode sweep on the **then-current best graph** whenever a new, deeper wall forms (export the best graph's `edge_data` from MySQL, point the tool at it). It's cheap (~1.1 s build/vertex; ~9.4 h serial at 120 s/vertex, or far less with across-vertex parallelism or a shorter budget). A future graph with different structure could have an improvable row even though 8348 does not. Do **not** build production integration of row-opt unless a sweep actually finds a hit; injection of any hit would go through the manual stage-seed path (MySQL graph+stage insert, Redis re-seed, **fleet restart** — see the stale-config ops lesson in the 2-flip-wall notes).

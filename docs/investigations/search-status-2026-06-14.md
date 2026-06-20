# Search Status & Findings — June 2026 (consolidated)

**Date:** 2026-06-14
**Author:** Ben Ferenchak + Claude
**Purpose:** Single entry-point summary of the June 9–14 investigation campaign. Read this first; it points to the detailed docs and lists what is settled and what NOT to retry. Goal throughout: a 282-vertex 2-coloring with zero monochromatic 8-cliques (would prove **R(8,8) ≥ 283**).

---

## Current state (as of 2026-06-14)

- **Active campaign: 10** (created 2026-06-14), seeded from **Paley(281) + one row-optimized vertex = 27,401 mono-8-cliques**. Fast descent via balanced-pair moves to **27,058 (−343 in ~10 min), then re-walled** there (confirmed plateau ~22:30Z; exhaustion-advancing to slightly-worse graphs). Best-known 282-graph is now **27,058**, ~28.6× below campaign 2. Campaign left running (may grind slowly lower via exhaustion-fallback over hours, as campaign 2's tail did).
- **Campaign 2 (best 775,642) is RETIRED / INACTIVE.** It had wandered into a generic local-minimum basin ~28× worse than the construction it started from. Preserved in the DB (reversible) but no longer the search.
- **Best-known 282-graph is now the campaign-10 lineage (~27K and dropping)**, not 775,642. The verified seed graph is saved at `single-flip-check/results/paley282_opt.txt`.

## The headline finding

**A few minutes of "extend Paley(281) optimally" beat two months of full-mutation descent by ~28×.**
- Paley(281) (281 vertices) is monochromatic-8-clique-free — the standard proof R(8,8) ≥ 282. The entire 282-vertex problem is *how to add the 282nd vertex*.
- Campaign 2's strategy (random extension, then mutate **all** edges) destroyed Paley's structure and only descended ~5% (817,828 → 775,642) before locking.
- Keeping Paley(281) **fixed** and row-optimizing only the new vertex's 281-bit row yields **~27,401** mono-8-cliques (8 random starts: 27.4–28.8K, recount-verified). Re-seeding the live search here dropped it 28× and it descends.
- Detail + the corrected "100M+ → ~44K random / ~27K optimized" counting error: `paley-graph-investigation.md`.

## What is settled — and what NOT to retry

Everything below was established with validated, cross-checked tooling (every clique enumeration matched independent counts). **Do not re-run these:**

1. **Campaign 2's wall graph (8348, 775,642) is locked against every efficiently-searchable local move:**
   - all single edge flips (engine sweeps + the June-10 census) — `engine-optimization-review.md`
   - all within-star pairs and all-depth single-row rewrites — **exhaustive 282-vertex row sweep, 0/282** — `row-optimization-investigation.md`
   - 2-vertex joint moves — **0/214 pairs** (marginal + highest-coupling + 200 random; rule-of-three < 1.5%) — `joint-reoptimization-investigation.md`
2. **Participation-ordered enumeration: REJECTED** (campaign-1 backtest, winners uniform in participation rank). Keep `DUAL_EDGE_CARDINALITY`. A *learned* ranker is the only credible ordering upgrade (needs result logging, still off). — `engine-optimization-review.md`
3. **Simulated annealing, tabu+clique-guided, VDS: all RETIRED** (each: tens of thousands to millions of iterations, zero improvements). — `simulated-annealing-investigation.md`, `tabu-search-investigation.md`, `vds-enhancements.md`
4. **Do not restart/continue campaign 2.** It is a wandered dead basin dominated 28× by the campaign-10 construction.
5. **Do not trust the legacy "Paley(281)+1 = 100M+ cliques" claim** — that was a counting error; a random extension is ~44K, an optimized one ~27K.
6. **Do not conclude the 27K seed is "locked" from the row-opt/single-flip probe.** Those moves avalanche (+170 to +605), but the engine's *balanced-pair* moves descend — proven live by campaign 10.

## Tooling (one-off crate `single-flip-check/`, NOT in any product repo; all validated, reusable)

| Binary | Purpose |
|---|---|
| `clique_census.rs` | u64 mono-k-clique counts (validated vs Paley(281)'s known totals) |
| `paley_gen.rs` | generate Paley(p) / Paley(p)+vertex bitstrings (reproduces Paley(281) exactly) |
| `rowopt_pilot.rs` | single-vertex row re-optimization (MaxSAT local search); `ROWOPT_SAVE` env writes the optimized graph |
| `joint_pilot.rs` | 2-vertex joint re-optimization (`anchor`/`counters`/`pair`/`seed`/`sample` modes) |
| `pair_coupling.rs` | rank vertex pairs by shared 8-cliques (joint seeding) |
| `campaign1_ordering_backtest.rs`, `star_enrichment.rs` | participation-ordering backtest, star-enrichment scan |

Reuse pattern: when a new wall forms on the active best graph, export its `edge_data` from MySQL and re-run the relevant tool. Re-seeding production = stop workers+QM, insert graph/campaign/stage, set old campaign INACTIVE, change `RAMSEY_CAMPAIGN_ID` in compose, redeploy stack 7 (env-only). The QM seeds Redis from the graph; the worker `total_pairs` guard enforces lockstep.

## Open problems / candidate future directions

- **Reaching zero (R(8,8) ≥ 283) is still open.** Local edge-flip search caps at deep minima far from zero (775K wandered; ~27K constructed — and even 27K is row-locked, only pair-descendable). Getting near zero would need either a **base construction with fewer mono-7-cliques than Paley(281) while keeping zero mono-8** (hard/open), or **exact methods** (infeasible at ~6M-clause scale).
- **Where campaign 10 plateaus** is the immediate unknown (under monitoring 2026-06-14).
- **k-vertex windowed MaxSAT** (`sat-solver-investigation.md` Tier 2) — the next move-shape rung, but 1- and 2-vertex windows are both empty, so diminishing returns.
- **Result logging (Tier 3.0)** is still off — it gates any learned enumeration ordering.
- **Cheap config wins** (worker `WORK_UNIT_FETCH_COUNT` 250K→50K, `EXHAUSTION_DELAY_MS` 60→15s, `CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT` 50→200, pin Dragonfly off `:latest`) are documented and staged but **not yet shipped**. — `engine-optimization-review.md`

---

*Generated for the Ramsey project — 2026-06-14. Supersedes the strategic framing in `engine-optimization-review.md` §7 and `deep-analysis-path-forward.md` where they differ.*

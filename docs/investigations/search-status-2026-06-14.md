# Search Status & Findings — June 2026 (consolidated)

**Date:** 2026-06-14 (updated 2026-06-19, then 2026-06-27)
**Author:** Ben Ferenchak + Claude
**Purpose:** Single entry-point summary of the June 9–27 investigation campaign. Read this first; it points to the detailed docs and lists what is settled and what NOT to retry. Goal throughout: a 282-vertex 2-coloring with zero monochromatic 8-cliques (would prove **R(8,8) ≥ 283**).

---

## UPDATE 2026-06-27 — literature scan, vreduce bust, EXACT windowed MaxSAT (Phases 1–3)

**The 06-19 "suggested next steps" below were executed — every one is now closed.** Both the best 282-graph (8644) and Paley(281) are **EXACT-locked across ~427k+ proven-optimal windows**, the literature confirms **Paley(281) is the ~50-year world-record base**, and the vertex-reduction construction idea busted. Detailed writeup of the windowed solver: **`windowed-maxsat-investigation.md`**.

### 1. Literature scan (next-step #5 — DONE)
- **R(8,8): 282 ≤ R(8,8) ≤ 1518.** The lower bound **282 = Paley(281) being K8-free** (Radziszowski *Small Ramsey Numbers* DS1.16, item 2.3.j: if the order-p Paley graph, p = 4t+1 prime, is K_k-free then R(k,k) ≥ p+1; here p = 281). Credited to **Burling & Reyner (1972)** — **unbeaten ~50 years**. Campaign 10's base graph *is* the world-record construction, not an approximation of it.
- **282 is not prime**, so the Paley route structurally cannot reach a 282-vertex witness (the next prime ≡ 1 mod 4 is 293, whose Paley graph contains a K8). The community already swept circulant colorings of order 282–286 and found nothing (Exoo–Tatarevic) — matching our own `construction_search.rs` (282-circulant floor ~1M).
- **The natural generalizations don't apply to 2-color R(8,8):** generalized / k-th-power-residue Paley graphs (Dawsey–McCarthy, arXiv 2006.14716) target **multicolor** R_k(4)=R(4,…,4); the 2024 Mathon-type and k-th-power Paley-**digraph** papers (arXiv 2408.04067, 2311.02135) improve directed/multicolor numbers; analog/complete MaxSAT for Ramsey *construction* (arXiv 1801.06620) caps at ~K43 (R(5,5) region). Wolfram/MathWorld gives no R(8,8) bound (defers to Radziszowski). Reddit is bot-walled (unscrapeable by the automated tools).
- **Upshot:** reaching R(8,8) ≥ 283 is a genuine ~50-year-open problem; there is **no known better-than-Paley 2-color base near 282**. (Full record: memory `reference_r88_literature`.)

### 2. vreduce — vertex-reduction construction experiment (BUST)
Take a 288-vertex campaign-1 graph (source graph 5032, 980,066 mono-8), **remove the 6 highest-8-clique-participation vertices** to induce a 282-vertex graph, then perturb/rebalance to balanced colorings (5 strategies). Tools: bins `vreduce_analysis.rs`, `vreduce_perturb.rs`; results in MySQL table `graph_vreduce` (2,200 rows). **Result: best 817,827 mono-8, all in 817K–827K** — ~32× worse than 25,840, and worse than retired campaign 2 (775,642). Evolved 288/282 graphs live in a far worse region than Paley(281)+1; vertex-reduction does not escape it. **Do not re-run.**

### 3. EXACT windowed complete-MaxSAT (next-steps #1 & #3 — DONE)
New tool `single-flip-check/src/bin/wmaxsat_pilot.rs` — a **complete**-solver (z3) windowed move-finder, the qualitative upgrade over all prior *incomplete* probes (SA/tabu/VDS/row-opt/joint): per window it returns a **proven optimum** — it finds an escape, or **certifies none exists**. Recount-as-truth (z3 only proposes; every reported delta is the difference of two full clique recounts) makes any reported improvement impossible to fake. Built subagent-driven TDD, opus-reviewed. Detail + spec/plan: **`windowed-maxsat-investigation.md`**.
- **Phase 1 (minimize mono-8 on graph 8644):** |F|=32 → 0/600; |F|=48 → **0/~101,682 exact windows**. **Exact-locked up to |F|=48** (each window subsumes all single/pair/row/2-vertex moves inside it).
- **Phase 2 (281-base hunt: minimize mono-7 subject to hard mono-8 = 0, on Paley(281)):** explores the **non-circular** mono-8-free space the circulant probe never tested. Ladder all LOCKED: |F| = 8/16/24/32/48/64 → **0/~325k exact windows**. **Paley(281) exact-locked to non-circular mono-7-reducing moves up to |F|=64.** (A high-volume fast |F|=32 run continues.)
- **Phase 3 (through-F speedup, ~27×):** enumerate cliques *through* the window directly + screen the delta from clauses + full recount only to confirm candidate hits; opus-validated verdict-equivalent, saved hits still recount-confirmed. New bottleneck: z3 subprocess-launch.

### Updated verdict — now EXACT (proven-optimal), not just heuristic
| Avenue | Result |
|---|---|
| Local search on 8644, all sizes incl. **exact windowed MaxSAT to |F|=48** | **locked** (0/~102k exact windows) |
| Better 281-base via single circulant move from Paley | locked (0/140) |
| Better 281-base via **non-circular exact windowed MaxSAT to |F|=64** | **locked** (0/~325k exact windows) |
| Direct mono-8-free 282-circulant | floor ~1M |
| Vertex-reduction from a 288v campaign-1 graph (vreduce) | floor ~817K (bust) |
| Any **known** better-than-Paley 2-color base near 282 (literature) | none — R(8,8) ≥ 282 unbeaten ~50 yr |

### Next steps — only the structurally-new path remains open
The exact certification removes any remaining hope that *bigger local windows* help. The single highest-upside direction is **structurally-new mono-8-free base families** — generalized Paley / k-th-power-residue graphs, Cayley graphs on non-cyclic symmetry groups, algebraic/geometric designs — each evaluated with the now-validated `wmaxsat_pilot` + construction tooling. Compute-efficiency lever if more local search is wanted: a batched / in-process MaxSAT solver (z3 subprocess-launch is now the per-window floor).

### Ops
Long local sweeps must be launched via `( nohup … & )` (reparent to init/PID 1) to survive IDE/Claude restarts; macOS has no `setsid`; foreground `sleep` is blocked in the Claude Code env. Fully detached ⇒ no harness completion notification ⇒ detect completion via `done:` lines in the per-shard logs.

---

## UPDATE 2026-06-19 — current state, new findings, next steps

**Best-known 282-graph: 25,840 mono-8-cliques** (graph 8644, found 2026-06-16; campaign 10). The construction-seed basin descended 27,401 → 27,058 → **25,840** via balanced-pair moves, then re-walled; it now cycles a plateau ~25,9xx (the 25,840 basin itself is explored-and-blocked by cycle-prevention, so the live grind wanders just above it).

### What we did since 06-14

1. **Shipped the best-novel best-results cache (Option B).** The per-stage `best_results` set now filters visited graphs at insert time (`SISMEMBER processed_graph_hashes` in the worker's insert Lua) and the early-exit threshold tracks the best *novel* result (slot 0, not the 50th). Effects: **~2.8–3× faster grind** (tighter early-exit → far more cheap skips; measured 6.48 → 18.4 stages/h on the same 14 workers), **dead-end-proof** (no more "all top-N visited" stalls), and **early-adopt** (progress on the best novel improvement instead of grinding each stage to full exhaustion). Worker-only; QM consumes the now-novel-only set unchanged. Rust↔Java SHA-256 hash parity locked by shared test vectors. Plan + shipped outcome: `best-novel-threshold-plan.md`. (Merged: worker #69, QM #73, UI #11/#12; compose synced #160.)

2. **Re-ran the 2-vertex joint probe on the NEW best basin (graph 8644, 25,840)** — the open follow-up to caveat #6 below (the 27K probe tested only row-opt/single-flips, pre-descent). Result: **0/300 random pairs improved, every one at distance 0** (~30k SATLike moves/pair couldn't take a single improving step). The descended 25,840 basin is **locked to 2-vertex joint moves**, exactly like graph 8348. Since a joint solve subsumes single-vertex, **local search of every practical size is now confirmed exhausted on this basin too.**

3. **Built + ran the circulant construction analysis** (new tool `construction_search.rs`):
   - **Paley(281) is circulant-locally-optimal** — toggling each of its 140 distance-classes: **0/140 keep mono-8 = 0**. No better 281-base is one structured circulant move away (Paley is extremal in its own family).
   - **Direct n=282 circulant search** (a mono-8-free 282-circulant would be R(8,8) ≥ 283 outright): hill-climb from 60 random restarts floored at **~996k mono-8** — **~40× worse than the asymmetric 25,840**. The cyclic symmetry (141 DOF vs 39,621 free edges) forces structures dense with 8-cliques. No mono-8-free circulant found.
   - Confirmed the Paley+optimized-vertex construction (campaign 10's origin): vertex-row floor **~27,085–27,401** (the row optimizer is validated to reach the *exhaustive* optimum on the Paley(17) anchor), full descent **25,840** — **nothing crosses 25k**.

### Updated verdict — every tested door is closed

| Avenue | Result |
|---|---|
| Asymmetric local search, all sizes (single / pair / row / 2-vertex joint) | **locked at 25,840** (graph 8644, joint 0/300) |
| Better 281-base via single circulant move from Paley | **locked** (0/140) |
| Direct mono-8-free 282-circulant | **floor ~1M** (~40× worse than 25,840) |

**Honest framing:** the gap from 25,840 to 0 is enormous; *incremental* search of any move size cannot close it. Reaching 0 needs a *structurally new* mono-8-free 282-vertex graph — the open problem itself. And we don't know one exists: if R(8,8) = 282, no 282-coloring is mono-8-free, and the minimum mono-8 over all 282-graphs (what the search actually finds) is simply > 0.

### Suggested next steps (prioritized; none guaranteed)

*Could shave below 25,840 — reuse tools, run niced alongside the grind:*
1. **k ≥ 3 windowed MaxSAT** — the one untested local rung. "2-vertex subsumes single" does **not** extend to 3-vertex; a 3-coordinated move can escape where 2 cannot. The ruggedness (all 300 joint pairs at distance 0) makes it a long shot, but it's the clean next thing. Generalize `joint_pilot`.
2. **Multi-seed construction basins** — we descended exactly ONE seed (27,401 → 25,840). Generate several *diverse* mono-8-free-base + optimized-vertex seeds and descend each; a different basin may floor lower. (Deprioritized in May; worth revisiting now that everything else is closed.)
3. **Complete MaxSAT/SAT solver on a window** — everything run so far is *incomplete* local search (SATLike/WalkSAT). A complete solver (CaDiCaL/MaxHS) on a moderate window can find — or *prove absent* — improvements local search misses.

*The only real path to 0 (open problem, long-shot):*
4. **Other structured base families** — cyclotomic / generalized-Paley circulants (kth-power-residue connection sets, targeted vs the random search), Cayley graphs on non-cyclic symmetry groups, design/algebraic/geometric constructions.
5. **Literature scan** — confirm Paley(281) is genuinely the best-known mono-8-free base (Radziszowski's *Small Ramsey Numbers* survey); a better near-282 construction or search technique may already exist. Highest expected-value-per-minute.

### New tooling / docs since 06-14
- `construction_search.rs` — circulant (Cayley on Z_n) search; modes `validate` / `probe` / `search`; fast vertex-transitive counting (total k-cliques = n·(through-0)/k); validated on Paley(281) (mono-8=0, mono-7=5,979,680).
- Skill **`ramsey-pilot-tools`** (`.claude/skills/`) — documents every `single-flip-check` tool (build/run, `nice`+shard pattern, graph export, standing findings).
- `best-novel-threshold-plan.md` — Option B design + shipped outcome.

---

## Current state (as of 2026-06-14 — historical; superseded by the 2026-06-19 update above)

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

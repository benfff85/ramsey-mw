# Engine Optimization Review — June 2026

**Date:** 2026-06-12
**Author:** Ben Ferenchak + Claude

---

## Kernel optimization round 2026-07-15 (READ THIS BEFORE ATTEMPTING WORKER PERF WORK)

A profile-driven optimization pass that supersedes several guesses in the June
review below. **Net result: the worker is ~1.40× faster** (validated), deployed
to the M4-Max fleet 2026-07-15 (~1.45× measured live on identical deep-wall
stages; M1 not yet redeployed). What was learned, so it isn't re-litigated:

**Profiling verdict (macOS `sample` on the replay harness, production-tight
threshold regime): ~99.4% of worker CPU is inside `bron_kerbosch_count_no_x_with_limit`.**
Per-unit `vec![]` allocation was 0.2%, enumeration mapping ~0.05%, `flip_edges`
0.1%. So the June-review intuition that per-unit **fixed costs** (the two
`graph.invert()` calls, Vec allocation) were the lever was **half right**: dual
adjacency (below) helped because it removed a fixed cost, but past that, the
kernel *is* the workload — early-abort units still count tens of cliques before
bailing. A planned "kill the per-unit Vec allocation" change was **measured and
rejected** (it optimizes 0.2%).

**What shipped (all validated IDENTICAL by full-stage replay — see the harness
note at the end of this section):**
1. **Leaf popcount shortcut** (PR #75): at `|R| == k−1`, P is the common
   neighborhood of R, so each candidate completes exactly one k-clique — one
   popcount replaces |P| recursions. Alone: **1.08×**.
2. **Dual adjacency** (PR #77): `Graph` maintains `complement_adjacency` in
   lockstep; `invert()` becomes an O(1) `mem::swap` instead of an O(n·words)
   rebuild that ran **twice per work unit**. Cumulative: **1.15×** (this is the
   fixed-cost win, biggest in the tight-threshold regime).
3. **Micro-opts** (PR #78 commit 1): `depth` parameter replaces recomputed
   `r.cardinality()`; **R and X sets dropped entirely** from the counting kernels
   (R was write-only; X is maximality bookkeeping that all-k-clique counting never
   reads — duplicate prevention comes from the shrinking candidate set); one
   popcount per node serves all prunes; destructive `iter_set_bits` replaces
   `next_set_bit(v+1)` rescans. Cumulative: **1.35×**.
4. **k−2 level inline** (PR #78 commit 2): at `depth == k−2` every child takes the
   leaf shortcut, so fold it into an AND+popcount loop — removes the recursion
   tree's most numerous call layer. Cumulative: **1.40×**.

**PGO: evaluated and REJECTED.** Trained on both regimes, `-Cprofile-use`
rebuild, full-stage A/B: 543.0s vs 537.4s — a wash (−1%, noise). One small,
already-inlined, branch-predictable hot function is exactly the case PGO cannot
improve. **Do not retry PGO** unless the hot code changes shape substantially.

**Config dials shipped same day** (compose): `EXHAUSTION_DELAY_MS` 60000→15000
(both QMs), `WORK_UNIT_POLL_FREQ` 5000→1000 (workers) — per-stage-tail overhead,
now a larger fraction of the faster ~3.6-min sweeps.

**Only remaining kernel candidate:** red-edge sharing (consecutive units share the
red edge, so its blue-side contribution could be computed once per red edge —
potentially ~2× on kernel work) — but it carries real monochromatic-interaction
complexity and needs a design pass; not attempted yet.

**Validation harness (reusable — use it for ANY future kernel/graph change):**
`single-flip-check/src/bin/kernel_equiv_replay.rs` replays every work unit of a
real already-processed stage (graph 15491, 392,495,519 units) through the new
code and a frozen byte-for-byte copy of the **entire old stack** (old Graph + old
kernel), asserting identical `(exceeded, count)` on every unit. Six rounds of
changes, all byte-identical, hashes stable. `cargo bin` = the reference for
"did I break counting."

Deploy playbook (M4-Max fleet, established pattern): local `docker build` →
`docker compose -p ramsey up -d --no-deps --force-recreate ramsey-worker-rust` at
a stage boundary; then merge to develop so CI republishes Hub `:develop`. **The M1
runs a separate pull and lags** — recreate its workers to pick up kernel gains.
Repo hygiene: **never `git add -A` in `ramsey-worker-rust`** — it sweeps in the
untracked `vreduce_analysis.rs` experiment (happened 2026-07-15, fixed in 339ed83);
stage files explicitly.

---

**Status:** PARTIALLY RESOLVED (status pass 2026-07-02). Where each recommendation landed: **#1** mid-batch stage-death/threshold refresh — never shipped; economics changed again with the best-novel cache (stages now exhaust in ~2–5 min), still a valid small win if worker code is ever touched. **#2** participation ordering — REJECTED by the backtest below (the backtest itself remains this doc's lasting value). **#3** result logging — still OFF; still gates any learned ordering (see `july-2026-next-steps.md` for why it's deprioritized). **#4** incremental CliqueCollection across stages — never shipped. **#6** dials: `CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT` is now 5000 (far past the suggested 200); `EXHAUSTION_DELAY_MS` now 15000 and `WORK_UNIT_POLL_FREQ` now 1000 (both shipped 2026-07-15, see the kernel round section above); Dragonfly still on `:latest`. **§7** strategic layer — fully superseded by `search-status-2026-06-14.md` and successors.

**Related documents:**
- `archive/may-2026-next-steps.md` — strategic roadmap of the era (archived; Tier structure referenced below)
- `single-flip-assessment.md` context: the 2026-06-10 census that motivated the singles strategy
- `../workers/exhaustive-worker.md` — engine reference

---

## Context

Campaign 2 stalled at 775,842 cliques in late May (six full ~392M-pair sweeps on May 24 with zero improvements). The June 10 single-flip census showed the wall was an artifact of the balanced-pair move shape; `DUAL_EDGE_CARDINALITY_WITH_SINGLES` (all R+B single flips enumerated ahead of the pair sweep) shipped June 11–12. Within the first 16 hours the search dropped 196 cliques (~290/day vs ~24/day pre-shutdown), with singles and pairs contributing equally and single flips repeatedly perturbing the graph into fresh, productive 2-flip basins.

The new regime changes the engine's economics: stages now advance in anywhere from 30 seconds (census chains) to ~95 minutes (full sweeps), so per-stage fixed costs and staleness windows matter more than they did at May's cadence. This review covers engine-level optimizations; new move spaces (vertex-row re-optimization, windowed MaxSAT, same-color pairs) remain tracked in the strategic docs.

## Recommendations (by value-per-effort)

### 1. Mid-batch stage-death and threshold refresh *(small worker change, immediate)*

Workers check `stage_config` existence and fetch the top-N threshold only **between** 250K-unit batches (~50s of compute). Consequences at current cadence:

- When a stage advances mid-batch, every worker finishes its claimed range against the dead stage — up to ~50s × 14 workers wasted per advance (~3 worker-hours/day at 28 advances/day).
- A freshly lowered threshold (e.g., right after a singles-census win) doesn't tighten other workers' early termination until their next batch.

Fix: inside the batch loop, every ~25K units re-check `has_stage_config` (abandon if gone) and re-fetch the threshold. ~20 lines, one cheap Redis call per check. Alternatively/additionally, reduce `WORK_UNIT_FETCH_COUNT` 250K → 50–100K (config-only; Redis INCRBY overhead stays negligible).

### 2. Participation-ordered enumeration *(REJECTED by the campaign 1 backtest below — kept for the record)*

`DUAL_EDGE_CARDINALITY` orders edges by a static proxy: the count of same-colored edges adjacent to the edge's endpoints. Every worker already holds the **exact** per-edge clique-participation index in its `CliqueCollection`, built deterministically from the same stage graph — a strictly more informative signal at zero additional cost. Because the queue manager advances on the *first* improvement, time-to-first-improvement drives stage cadence, and enumeration order drives time-to-first-improvement.

Cardinality and participation correlate, and the June 10 census warned that improving edges are *mid*-participation rather than top — so this needed empirical grounding before any code change. See the campaign 1 backtest below.

### 3. Enable lightweight result logging *(Tier 3.0 gate, near-zero cost)*

`work_result` remains empty (`PUBLISH_RESULTS=false`): no training data has ever been collected. Per the roadmap's Tier 3.0 design: capped Redis streams logging every improvement plus a ~0.5% sample of non-improvements. This is the prerequisite for a *learned* enumeration ordering (the production-grade version of #2) and for any future ML-guided 3-flip work. Cheapest to start while the search is productive and improvements are frequent.

### 4. Incremental `CliqueCollection` update across stages *(moderate effort; value scales with chain frequency)*

`clear_stage_cache()` discards graph + collection on every stage advance; all 14 workers rebuild from the bitstring (~3–5s each, ~70 worker-seconds per stage). Consecutive base graphs differ by 1–2 edges, and `edge_to_cliques` already indexes exactly the cliques a flip destroys; created cliques come from one seeded enumeration. An `apply_flips()` (diff new bitstring against cached, remove/add cliques) makes stage turnover ~milliseconds. Matters most during census chains (advances every ~30s); also the primitive a future vertex-reoptimization worker needs.

### 5. Counting-kernel cautions *(document, profile before touching)*

- **Bron-Kerbosch pivoting is invalid here.** Pivoting prunes branches that lead to non-maximal cliques; this system counts **all** k-cliques, not maximal ones. Worth a comment in `algorithm.rs` so future optimization attempts don't silently break counting. (The regression suite's brute-force cross-checks would catch it.)
- `BitMatrix` (fixed 5×u64 words, word-level AND/popcount, `target-cpu=native`, LTO fat) is near the practical floor for 282–288 vertices. Profile (`cargo flamegraph` on a replayed batch) before touching kernels.
- The one structural idea with real headroom: consecutive work units share the red edge, so the red edge's blue-side count could be computed once per red edge (~2× on half the BK work) — but monochromatic-interaction corrections add complexity, and early termination already skips most full counts. Revisit only if long full sweeps become the norm again, with profile data.

### 6. Operational dials *(config-only)*

- `CYCLE_PREVENTION_GRAPH_LOOKBACK_COUNT` 50 → 200: fifty stages was ~2 days of memory at May's cadence but is now under a day; longer memory is cheap oscillation insurance (hashes in a Redis set).
- `EXHAUSTION_DELAY_MS` 60000 → 15000: exhaustion stages already cost a full sweep; the delay only adds latency to the fallback advance.

### 7. Strategic layer (unchanged, for when this vein thins)

Recent stage gaps are lengthening (~1.5h sweeps reappearing within the first day) — the wall will reform at some deeper count. The documented next move spaces:

- **Vertex-row re-optimization** — ⛔ INVESTIGATED & RESOLVED 2026-06-13 (`row-optimization-investigation.md`): a full 282-vertex sweep of the best graph (8348, 775,642) found **zero** improving rows. The entire single-vertex-star move class (all single flips, all within-star pairs, all-depth single-row rewrites) is exhausted on this lineage. The graph is **row-locked**; this is not the way forward at 775,642.
- **Windowed MaxSAT** (optimal multi-edge moves; `sat-solver-investigation.md` Tier 2) — now the leading candidate, since the row-lock result says improving moves must be genuinely multi-vertex. Direct next rung: **2-vertex joint re-optimization** (the 2-vertex window, generalizing the validated row-opt tooling).
- **Same-color pairs** (requires handling the seeded-BK double-count documented by `same_color_seed_pairs_count_shared_cliques_twice` in the worker regression suite) — note that *within-star* same-color pairs are already covered (and found empty) by the row sweep; only *cross-star* same-color pairs remain unexplored here.

---

## Campaign 1 backtest: would participation ordering have found winners earlier?

**Question (Ben, 2026-06-12):** for every improving (non-exhaustion) stage transition in campaign 1 (288 vertices, 5,073 stages, 1.05M → 980K cliques), identify the winning flipped pair and compare its work-unit index under the production cardinality ordering vs. the proposed participation ordering.

**Method.** One-off tool `single-flip-check/src/bin/campaign1_ordering_backtest.rs` (reads the worker crate; repos untouched). For each consecutive stage pair with strictly decreasing clique count and exactly one red→blue + one blue→red differing edge: fully enumerate the base graph's 8-cliques (exact per-edge participation, verified against the stored `clique_count`), compute per-edge cardinality the way `DualCardinalityEnumerator` does, and reconstruct each ordering's pair index `red_pos × blue_count + blue_pos` with the production tie-break (lexicographic pre-order, stable descending sort).

**Sampling bias (important).** Historical winners are the pairs the cardinality ordering reached *first* under multi-worker sweep — they are early-in-cardinality-order by construction. The comparison is therefore biased **against** participation ordering: it measures whether the *same* winner would have surfaced earlier, and cannot credit participation ordering for different improving pairs it would have reached sooner. Results are a conservative lower bound; a live A/B (time-to-first-improvement over ~20 stages) is the decisive test.

**Results (4,637 improving transitions; 379s on 12 threads; every base-graph enumeration matched the stored `clique_count` exactly — incidentally re-validating the Bron-Kerbosch stack against 4,637 historical counts).**

| Metric | Cardinality (production) | Participation (proposed) |
|---|---|---|
| Winner ranked earlier | — | **27.1%** of transitions (0 ties) |
| Winner index p10 | 8.5M | 41.8M |
| Winner index median | **93.0M (21.8% of sweep)** | **226.7M (53.1% of sweep)** |
| Winner index p90 | 273.1M | 385.5M |
| Winner index mean | 117.6M | 219.8M |

(Total pairs per stage ≈ 427.0M at 288v.)

**Verdict: participation ordering is REJECTED.** The decisive observation is the shape, not just the comparison: historical winners sit at **median 53% of the participation-ordered sweep — statistically indistinguishable from uniform**. If clique participation carried real signal about where improving flips live, winners would cluster early in participation order even under this backtest's selection bias; they don't. This independently confirms the June 10 census observation that improving edges are mid-participation: edges in many cliques destroy many but also create many, and the net delta is what matters — no static popularity metric sees it.

Two corollaries:

1. **Keep `DUAL_EDGE_CARDINALITY`'s ordering.** Its winners-at-22%-median is partly selection bias (winners are by construction what the production order found first), so this doesn't prove cardinality is *good* — but there is no evidence participation is better, and strong evidence it is not.
2. **The only credible route to a better ordering is a learned one** — which strengthens recommendation #3 (result logging). A ranker trained on actual deltas can model the destroy-vs-create balance that static metrics cannot; this backtest's CSV (`single-flip-check/results/campaign1_ordering_backtest.csv`, 4,637 labeled winners with both rankings) is a first labeled artifact for that work.

---

*Generated for the Ramsey project — June 2026*

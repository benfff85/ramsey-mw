# Investigations — index

Goal throughout: a 282-vertex 2-coloring with zero monochromatic K₈ ⇒ **R(8,8) ≥ 283**. Best-known 282-graph: **25,604** mono-8 (graph 276750, campaign 10, 2026-07-28). Superseded values still referenced by older docs, newest first: 25,618 (graph 222120, 2026-07-27), 25,758 (graph 26994), 25,840 (graph 8644). **Anything reading "nothing has ever gone below 25,840" predates 2026-07-27 and is no longer true.**

**Start here:** `july-2026-next-steps.md` (live roadmap) → `search-status-2026-06-14.md` (consolidated findings + do-not-retry list).

## Live / current

| Doc | What it is |
|---|---|
| `july-2026-next-steps.md` | **Live roadmap** — multi-seed basin program (campaigns 11+), ranked queue |
| `post-hoist-bottleneck-review.md` | **Live perf work** — measured wall-clock budget after the hoist shipped; ranked fix queue (stage-status index, regime-aware hoist gate, slice fill, inner loop) with before/after baselines |
| `fleet-abstraction-plan.md` | **SHIPPED** — `RAMSEY_CAMPAIGN_ID` env-pinning replaced by a DB `fleet` table (platform→campaign); single campaign-agnostic QM. Repointing/pausing is a DB update, not a redeploy. Phase 2 (perturbation/ILS) built on it and is also live. |
| `pair-move-hoist-proposal.md` | **SHIPPED** — the original proposal for hoisting per-edge clique counts out of the pair-move inner loop |
| `pair-move-hoist-review.md` | **SHIPPED** — measured review of that proposal (found a shared-vertex bug in its pseudocode), plus the outcome: what the kernel change actually bought once the surrounding bottlenecks were removed |
| `search-status-2026-06-14.md` | Consolidated June findings hub; do-not-retry list; supersedes older strategy docs |
| `structurally-new-base-investigation.md` | All construction families certified empty: cyclotomic (every prime order ≤ **1021**; p ≡ 3 mod 4 closed by parity theorem), Seidel switching, all four order-282 Cayley groups |
| `windowed-maxsat-investigation.md` | Exact (complete-solver) windowed MaxSAT: ~1.1M proven-optimal windows, zero escapes, |F| up to 128 |

## Closed investigations (records; conclusions still load-bearing)

| Doc | Verdict |
|---|---|
| `paley-graph-investigation.md` | Paley(281) is the ~50-year world-record base; the whole 282-problem is the added vertex (early "100M+" count corrected in-doc to ~44K random / ~27K optimized) |
| `row-optimization-investigation.md` | Graph 8348 row-locked (exhaustive 282-vertex sweep, 0 improving rows); tool (`rowopt_pilot`) still actively used to generate construction seeds |
| `joint-reoptimization-investigation.md` | 2-vertex joint moves locked (0/214 pairs); tool (`joint_pilot`) reusable on future walls |
| `retired-trajectory-methods.md` | SA, tabu+clique-guided, VDS — all retired, zero improvements across millions of iterations; **do not reactivate** (consolidates three 2026-04/05 docs, originals in git history) |
| `sat-solver-investigation.md` | Direct SAT infeasible (~10¹³ clauses); the windowed-MaxSAT proposal here was built (see live docs) |
| `engine-optimization-review.md` | Participation ordering REJECTED (campaign-1 backtest — the lasting artifact); config-win statuses in header |
| `best-novel-threshold-plan.md` | SHIPPED 2026-06-19 — best-novel threshold design + outcome (~3× stage throughput); reference for the insert-Lua/threshold semantics |

## `archive/`

Superseded strategy/roadmap snapshots kept for the historical record (each carries a banner explaining how it resolved): `may-2026-next-steps.md`, `deep-analysis-path-forward.md`.

## Related

- `../workers/` — per-worker-mode algorithmic references (exhaustive is live; SA/tabu/VDS are retired, banners at top).
- Research tooling lives in the `single-flip-check/` crate (separate, not committed); usage documented in the `ramsey-pilot-tools` Claude skill.

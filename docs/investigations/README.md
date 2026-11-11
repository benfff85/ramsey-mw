# Investigations: index

Goal: a 2-colouring of K282 with no monochromatic K8, which proves **R(8,8) ≥ 283**. None has been found.

## Current state (2026-11-11)

- **Best 282-vertex colouring: 24,927 monochromatic K8** (graph 5578937, in the database since 2026-11-02). It is
  Paley(281) plus one vertex whose row is invariant under an order-14 multiplier group. A two-day fleet run from it
  (campaign 17, 1.48M stages) found nothing lower; campaign 17 is retired.
- **Paley(281) has no K8-free one-vertex extension** (exact, 2026-10-11). No descent that keeps the Paley core
  reaches zero, so the fleet is paused and the work is exact-method research.
- **That research is not in this repository.** It lives in `single-flip-check/docs/` at the workspace root (the
  crate is not under version control). Start with:
  - `2026-11-11-thirty-day-plan.md`: the current plan and its progress log;
  - `2026-10-26-prescribed-automorphism-screen.md`: the symmetry screens (Cayley, block, primitive, coset);
  - `2026-10-30-symmetric-row-record.md`: the 24,927 record;
  - `2026-10-10-completion-uniqueness.md`: the no-extension proof;
  - `2026-10-19-literature-scan.md`.

## Documents here

| Document | What it is |
|---|---|
| `search-history.md` | **The fleet-era search, April – August 2026, in one place**: record progression, every move class and construction tried, the multi-seed and kick results, and the do-not-repeat list. Read its scope note first |
| `frozen-paley-core-investigation.md` | Why campaign 10 was only ever searching one vertex's row: the frozen core, the K7-free split criterion, Paley's rigidity. Its banners carry the September and October corrections |
| `structurally-new-base-investigation.md` | Construction families found empty: cyclotomic colourings for every prime order to 1021, Seidel switching, the four order-282 Cayley groups (hill-climb, not exact) |
| `paley-graph-investigation.md` | Paley(281) as the base: why it is the record construction, and the corrected extension counts |
| `retired-trajectory-methods.md` | Simulated annealing, tabu and variable-depth search: built, measured, retired |
| `engine-performance.md` | **Worker and fleet performance in one place**: what shipped and its measured gain, how the hoist, row selector and best-novel threshold work, what was rejected |
| `post-hoist-bottleneck-review.md` | The detailed measurement log behind `engine-performance.md`, parts 1–15 (2026-07-28 to 2026-10-03) |
| `fleet-throughput-timeline.html` (+ `.test.mjs`) | Chart of fleet throughput over the same period |
| `fleet-abstraction-plan.md` | Specification of the `fleet` table and API (shipped July 2026); source comments point here |
| `disk-cleanup-2026-09-09.md` | One disk-space incident; `database/README.md` points here |
| `../workers/exhaustive-worker.md` | How the production worker enumerates and evaluates work units |

Operational runbooks (Dragonfly upgrade, graph delta lineage) are in `docs/superpowers/plans/` at the workspace
root, and the schema and prune procedure in `database/README.md`.

## Removed on 2026-11-11

Merged into `search-history.md`: the June status hub, the May and July roadmaps, the May strategy note, and the
row-optimisation, joint re-optimisation, windowed-MaxSAT, direct-SAT and multi-seed write-ups. Merged into
`engine-performance.md`: the June engine review, the best-novel threshold plan, the hoist proposal and its review.
Deleted outright: the three retired workers' algorithm references, the unbuilt clique-guided perturbation
proposal, and the plan and spec for the throughput timeline. All are in git history before that date.

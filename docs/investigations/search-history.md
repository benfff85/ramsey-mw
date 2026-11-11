# Search history, April – August 2026

One record of what the fleet-era search tried, what each attempt established, and what not to repeat. It replaces
nine documents (the June status hub, the May and July roadmaps, the May strategy note, and the row-optimisation,
joint re-optimisation, windowed-MaxSAT, direct-SAT and multi-seed write-ups). Their full text is in git history
before 2026-11-11.

Work after August is exact-method research in `single-flip-check/docs/` at the workspace root; see `README.md` here
for the current state. **Read the scope note first.**

## Scope note: what these results do and do not show

Several results below were written as "locked" or "closed". Two later findings narrow them.

- **The core never moved.** Through all of campaign 10 the first 281 vertices stayed bit-identical to Paley(281).
  Only one vertex's 281-bit row was ever in play. Window, pair and joint samples drawn uniformly over all edges
  therefore sampled an edge set that was 99.3% frozen (`frozen-paley-core-investigation.md`).
- **Sampled is not exhaustive.** "0 of N windows improved" bounds what that sample could reach. It does not
  exclude larger or coordinated moves, and it says nothing about colourings far from Paley.

What does stand, proved later by exact methods: Paley(281) has no one-vertex extension that is K8-free, so no
descent that keeps the Paley core can reach zero (`single-flip-check/docs/2026-10-10-completion-uniqueness.md`).

## Record progression

| date | mono-K8 count | graph | how |
|---|---|---|---|
| to 2026-06-13 | 775,642 | 8348 (campaign 2) | two months of full-edge mutation from a random extension |
| 2026-06-14 | 27,401 → 27,058 | campaign 10 seed | Paley(281) + one row-optimised vertex, then pair moves |
| 2026-06-16 | 25,840 | 8644 | pair-move descent |
| 2026-07-20 | 25,835, then 25,758 | 26922, 26994 | first perturbation kicks (ILS) |
| 2026-07-27 | 25,618 | 222120 | x32 kick |
| 2026-07-28 | 25,604 | 276750 | x32 kick |
| 2026-09-07 | 25,439 | 3334326 | campaign 10, last fleet record |
| 2026-10-30 | **24,927** | 5578937 | offline: Paley(281) + a row invariant under an order-14 multiplier group |

The single biggest step was the first: keeping Paley(281) fixed and optimising only the new vertex beat two months
of unconstrained mutation by a factor of 28. A random extension of Paley(281) has about 44,000 mono-K8; an early
"100M+" figure was a counting error.

## What was tried

### Trajectory methods (April – May): retired

Simulated annealing, tabu with clique-guided candidates, and variable-depth search each ran tens of thousands to
millions of iterations with zero improvements. Detail in `retired-trajectory-methods.md`.

### Local moves on the campaign-2 graph (June)

Graph 8348 (775,642), all with tools in `single-flip-check/`:
- **Single edge flips:** none improves (engine sweeps and a full census).
- **One vertex's whole row** (`rowopt_pilot`, SATLike local search): 0 of 282 vertices; not even a neutral flip.
- **Two vertices jointly** (`joint_pilot`): 0 of 214 pairs (14 targeted, 200 random).
- **Participation-ordered enumeration:** rejected. A backtest on 4,637 campaign-1 winners found them uniform in
  participation rank (median at 53% of the ordered sweep).
- The row and joint probes are local search (about 134,000 SATLike moves per vertex, calibrated on a known hard
  positive). They cannot certify optimality; the exact windows below are what turned "could not escape" into a
  proof for the windows sampled.
- The May "two-flip wall" was an artefact of the move shape: campaign 1 never searched single flips, and adding
  singles of both colours (2026-06-12) restarted the descent.

### The Paley + 1 construction and its descent (June – July)

- Campaign 10 was seeded from Paley(281) plus a row-optimised vertex. Row-optimum seeds land at 27,085–27,800.
- The descended graph 8644 (25,840): 0 of 300 random vertex pairs improved under joint re-optimisation.
- **Multi-seed program (campaigns 11–15, 2026-07-02 to 07-13).** Five further seeds, pairwise Hamming distance
  122–157. Effort is in stages, never wall-clock.

| campaign | floor | at stage | total stages | terminal wall (stages) |
|---|---|---|---|---|
| 10 (original seed) | 25,840 | 256 | 8,642 | 8,386 |
| 11 | 25,881 | 123 | 463 | 340; re-run to a 500-stage wall: no change |
| 13 | 25,996 | 926 | 1,209 | 283; re-run: 25,932 (graph 21856), then a 508-stage wall |
| 14 | 26,185 | 87 | 425 | 338 |
| 15 | 26,385 | 842 | 1,369 | 527 |
| 12 | 26,677 | 97 | 392 | 295 |
| 16 (a seventh seed) | 26,821 | | | banked 2026-07-18 |

  - Plain descent from a fresh seed floors at 25.8–26.8 thousand. None went below the original 25,840.
  - Descents come in waves: campaign 13 broke a 69-stage wall for −699, campaign 15 broke walls of 458 and 291
    stages. A floor counts as terminal only after a 500-stage wall; campaigns 11–14 were first banked earlier.
- **Perturbation kicks (ILS, from 2026-07-20)** did go lower: four records down to 25,604. Then 22 kicks over
  218,786 stages produced nothing. x32 kicks returned to a 91-unit band just above the incumbent in 15 of 16
  cases; x64 kicks stranded at 30–53 times the incumbent in 5 of 5 and were reverted. Kicks fire on basin
  staleness (`PERTURBATION_BASIN_STALE_STAGES`), not a fixed stage count.
- **Clique-guided kicks were proposed (2026-07-31) and never built**, so kick shape is untested, not closed. What
  the scoping established, for anyone who picks it up:
  - The hoist's per-edge table cannot supply participation: it is keyed to the graph being swept, expires in
    seconds, and holds cliques a flip would create, not cliques an edge is in.
  - Bias the choice, do not take the top edges. A greedy kick is close to a descent step and walks back into the
    basin.
  - The staleness clock is not broken. A doomed descent and a healthy one both keep setting new minima, so no
    clock tells them apart; do not "fix" it.
  - An incumbent-relative guard was measured and rejected: healthy basins sit at 51–61 times the incumbent at
    stage 1,000 and stranded ones at 68–80 times, too thin a margin on 15 and 6 samples.
  - Judge any kick change by the floor reached per kick cycle, never by stage rate. The control band is the 15
    healthy x32 epochs, 25,705–25,796.

### Exact windows (June – July)

`wmaxsat_pilot` solves a window of |F| edges to a proven optimum with z3; every reported improvement is the
difference of two full recounts.

| target | objective | window sizes | exact windows | improved |
|---|---|---|---|---|
| graph 8644 (25,840) | fewer mono-K8 | 32, 48 | about 102,000 | 0 |
| Paley(281) | fewer mono-K7, no mono-K8 | 8 to 64 | about 325,000 | 0 |
| Paley(281), fast path | same | 32, 64, 128 | 392,457; 191,186; 88,496 | 0 |
| graph 15491 (25,881) | fewer mono-K8 | 48 | 5,920 | 0 |

About 1.1 million exact windows in all. A through-window enumeration made each window 27 times cheaper; the z3
process launch (0.1–0.15 s) is then the cost.

### Direct SAT (May)

Encoding the whole 282-vertex problem needs about 10^13 clauses (one per 8-set and colour) and was ruled out. The
windowed form above, and later the symmetry-reduced and cube-and-conquer forms in `single-flip-check`, are what
that analysis led to.

### Other constructions (June – July)

- **Vertex reduction:** deleting the 6 most clique-heavy vertices of a 288-vertex campaign-1 graph and rebalancing
  gives 817,827 at best.
- **Circulants:** Paley(281) is locally optimal among circulants (0 of 140 single distance-class toggles keep it
  K8-free). A hill-climb over 282-vertex circulants floors near 1,000,000.
- Cyclotomic classes, Seidel switching and the order-282 Cayley groups: `structurally-new-base-investigation.md`.
- **Glue of a (7,8) and an (8,7) graph:** proposed and dropped the same day on a first-moment estimate. That
  estimate does not rule out a structured interface (it would "rule out" Paley(281) too).

### Literature (June)

282 ≤ R(8,8) ≤ 1518 (an early note here quoted a stale upper bound of 6,090). The lower bound is Paley(281) being
K8-free (Burling and Reyner, 1972). 282 is not prime, the next Paley prime 293
contains a K8, and Exoo and Tatarevic found no circulant colouring of order 282–286. The current literature scan
is `single-flip-check/docs/2026-10-19-literature-scan.md`.

## Proposed in this period and never run

These appear in the old roadmaps. None was tried, so none is evidence, and none should be assumed closed.

- Automorphism pruning with nauty (assumed useless on evolved graphs with trivial symmetry).
- A learned or ML-ranked enumeration order (gated on result logging, which was never switched on).
- Genetic crossover between campaigns. The eight-campaign "population" of spring 2026 was independent forks, with
  no crossover, and was retired on 2026-05-03.
- The "corrected" simulated-annealing design (incremental evaluation, basin-scale temperature).
- An in-process MaxSAT solver for larger exact windows ("Phase 4"). The later CaDiCaL-based exact work in
  `single-flip-check` took its place.
- A pivot to R(5,5): set aside, because the complete methods that work at k = 5 do not transfer to k = 8.

## Do not repeat

1. SA, tabu or VDS in their built form.
2. Row, pair or joint probes on graphs 8348, 8644 or 15491, and exact windows on 8644 or Paley(281) up to |F| = 128.
   (Graph 15491's exact check is thin: 5,920 windows at |F| = 48 against about 102,000 for 8644.) Do not build
   joint moves into production without a hit from the pilot first.
3. Campaign 2, or anything seeded from an unconstrained 282- or 288-vertex graph (vertex reduction included).
4. Participation-ordered enumeration.
5. Fresh Paley + row-optimum seeds expecting a lower basin: seven seeds, none below the first.
6. x64 kicks.
7. Any Paley-core descent as a route to zero. It can lower the count; it cannot reach a witness.

## Conventions that came out of this period

- Compare campaign effort in stages, not wall-clock: throughput changed by orders of magnitude.
- Refer to campaigns by id.
- A clique count is trusted when an independent counter agrees (`clique_census`, the notebook oracle).
- Experiments live in `single-flip-check/`; production code changes are separate decisions.

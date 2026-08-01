# July 2026 — Next Steps (active roadmap)

**Date:** 2026-07-02 (evening)
**Author:** Ben Ferenchak + Claude
**Supersedes:** `archive/may-2026-next-steps.md` as the live roadmap. Strategic background: `search-status-2026-06-14.md` (read first), `structurally-new-base-investigation.md` (all construction long shots closed through order 461 + all four order-282 Cayley groups), `windowed-maxsat-investigation.md` (~1.1M exact windows, zero escapes).

**Goal unchanged:** a 282-vertex 2-coloring with zero monochromatic K₈ → **R(8,8) ≥ 283**. Best-known 282-graph: **25,604 mono-8 (graph 276750, campaign 10, 2026-07-28)**. The 25,840 figure below is the state as of 2026-07-02 and has since been beaten three times (25,758 → 25,618 → 25,604), all by ILS perturbation kicks on campaign 10.

---

## CURRENT STATE (2026-07-31) — read this first; everything below is an older snapshot

- **The kick MAGNITUDE axis is exhausted.** 22 kicks over 218,786 stages have produced **zero**
  improvement — the incumbent 25,604 (graph 276750) was set at stage 267,359, *before* the whole
  sequence. Outcome is bimodal: x32 kicks return to a 91-unit band (25,705–25,796) just above the
  incumbent in 15 of 16 cases, while x64 kicks strand at 30–53× for 17–27k stages in 5 of 5. There is
  no magnitude that lands somewhere new and good.
- **`PERTURBATION_ESCALATION_CAP` reverted x64 → x32** (2026-07-31, mw #184). The x64 tier had been
  added 07-29 because kicks were pinned at x32 — but pinning was the *symptom* of kicks not working.
  A 3840-pair kick peaks at ~4.6M against a random-coloring expectation of ~6.7M, i.e. it discards
  the incumbent rather than perturbing it. Stranded epochs had consumed 49% of all stages since the
  first kick.
- **`PERTURBATION_BASIN_STALE_STAGES` is 1000**, not 500 (raised 07-29). The clock is **not**
  malfunctioning and needs no change: stranded epochs showed trailing stalls of 1,019 / 1,004 / 796 /
  1,017 stages and fired on schedule. Staleness *cannot* detect a doomed descent early, because a
  doomed and a healthy descent both keep setting genuine new minima. The incumbent-relative guard
  that would distinguish them was measured and **rejected** (~12% margin, n=15/6) — see
  `clique-guided-perturbation-proposal.md` §6.3 before re-deriving it.
- **Next proposed move: kick SHAPE, not size** — `clique-guided-perturbation-proposal.md`. Bias the
  kicked edges by mono-8-clique participation instead of choosing uniformly. Queue-manager-only.
  **§8.1 defines a half-day falsification test (participation histogram on graph 276750) to run
  before any search-affecting code is written.**
- **Storage: retention policy now exists.** The `graph` table was 20.68 GB with 17.16 GB of
  `edge_data`; the 07-30 prune nulled 445,163 superseded bitstrings, leaving 0.74 GB live and 18.38
  GB reusable. `clique_count` (the trajectory the UI plots) is untouched — rows are NULLed, never
  deleted. Procedure, retention rules, and the high-water-mark guard are in `database/README.md`.
  Re-run weekly; growth is ~2–4 GB/day.

## CURRENT STATE (2026-07-26) — an older snapshot; where it disagrees with the above, the above wins

- **All-time best is now 25,604** (graph 276750, 2026-07-28 11:01), set by a perturbation kick on
  campaign 10. Still not a witness (≠ 0). Progression of record-holders: 25,840 (graph 8644,
  06-16) → 25,758 (graph 26994) → 25,618 (graph 222120, 07-27) → **25,604** (graph 276750, 07-28).
  The last two arrived within a day of each other, both from x32 kicks off the then-incumbent.
- **Perturbation kicks now fire on BASIN STALENESS, not a fixed wall.** The clock runs from the
  basin's own floor, so a descent still finding new minima is never cut off. The old fixed
  `PERTURBATION_WALL_STAGES` truncated seven campaign-10 descents mid-free-fall.
  `PERTURBATION_BASIN_STALE_STAGES` is 500 (the gap between successive new basin minima reaches
  249 stages near the floor, so a smaller window kicks basins before they reach their floor).
- **The exhaustive worker no longer traverses for most work units.** `created` is derived
  algebraically from memoised per-edge counts (`hoist.rs`); ~86% of pairs need no traversal at
  all. See `pair-move-hoist-review.md` for the measured outcome.
- **Fleet throughput is now measured in the tens of millions of units/sec**, up from ~1M. A full
  392M-unit sweep takes seconds rather than minutes, which changed what "a stage" costs and
  invalidated several constants sized for the old regime (batch size, exhaustion delay, the
  hoist gate). Expect that to keep happening: every speedup relocates the bottleneck.
- **Storage is now the growth constraint** — the `graph` table adds ~6 GB/day at current stage
  rates. No retention policy yet.

## EARLIER SNAPSHOT (2026-07-15)

- **Multi-seed basin program: COMPLETE.** Six basin floors (25,840 / 25,881 / 25,996 / 26,185 / 26,385 / 26,677), none below the original 25,840. Full result: `multiseed-basin-program-results.md`.
- **Deep-wall re-runs (decision #1) in progress.** c11 (25,881) **confirmed terminal** — re-ran a censored basin to a full 500-stage wall, found nothing. c13 (25,996) re-run running now on the M4-Max fleet. M1 stays on campaign 10.
- **Worker kernel optimized ~1.40×** (`engine-optimization-review.md`, kernel round 2026-07-15); deployed to the M4-Max fleet, ~1.45× live. PGO evaluated and rejected. M1 not yet redeployed.
- **Floor certification launched** on graph 15491 (25,881); graph 8644 (25,840) already exact-locked to |F|=128 — don't re-do it.
- **Conventions:** compare campaign effort in **stages, not wall-clock** (throughput changes with compute allocation); refer to campaigns by **id, not opt_N** labels.
- **Lead strategic item is now §2 (Phase 4 in-process MaxSAT)** — flip-search basins have largely answered their question; the next result needs a bigger move class.

---

## Where things stand (2026-07-02, historical snapshot)

**Fleet topology (since this morning):** local 14 workers + `ramsey-queue-manager-11` run **campaign 11** (multi-seed basin program); the original QM + the remote machine's workers keep **campaign 10** grinding its ~26,0xx plateau (left ACTIVE deliberately — those workers can't be repointed right now). Two campaign-scoped QMs sharing one mw/Redis/MySQL is verified safe.

**Campaign 11, day one:** seed opt_9 (27,104, an independent Paley+1 row-optimum 149 bits from the original seed) descended to **25,881 in ~5 hours**, then walled and began the familiar upward drift. For comparison the original basin took ~2 days to floor at 25,840 (pre-best-novel-cache).

**The early headline: two independent basins, floors 25,840 vs 25,881 — 0.16% apart.** This is the first evidence that **~25.85K is a characteristic floor of the Paley(281)+1 construction itself** (set by Paley's mono-7 structure), not luck of one basin. Seeds 3–5 will confirm or refute. If confirmed, that is the program's finding: local search from *any* optimized Paley+1 seed lands at the same floor; going below needs a different construction (all tractable families now certified empty) or new math.

**Infra:** nginx TLS cert renewed 2026-07-02 (root cause: certbot sidecar had died with no restart policy — fixed with `restart: unless-stopped` live + in `nginx-proxy/docker-compose.yml`); Loki log shipping confirmed resumed from all ramsey services.

---

## The queue (ranked)

### 1. Multi-seed rotation (running)
- **Rotation 1 EXECUTED 2026-07-03 ~23:17Z.** Campaign 11 (opt_9) banked at **25,881** — its ~28 h tail-grind never re-approached the floor (cycled 25,99x–26,03x all day), unlike campaign 10's long productive tail; the best-novel cache appears to burn a basin's novel improvements in the first hours, making long tails unproductive. Campaign 12 (opt_4, 27,668, graph/stage 16004) live on the local 14 workers; QM service renamed campaign-agnostic `ramsey-queue-manager-mseed` (future rotations = env flips only).
- **Rotation 3 EXECUTED 2026-07-05 ~03:27Z.** Campaign 12 (opt_4) banked at **26,677** — never approached the first two basins' depth, cycling ~26,74x its whole final day. Campaign 13 (opt_2, 27,677, graph/stage 16573) live.
- **Rotation 4 EXECUTED 2026-07-07 ~20:30Z.** Campaign 13 (opt_2) banked at **25,996** — a **MULTI-WAVE basin**: ~8 h plateau at 27.4K, first wave to 26,695 by 06Z 07-06, ~6 h of cycling that looked like a wall, then a second breakthrough wave (~16Z 07-06) that ground to 25,996 by ~03Z 07-07, followed by a genuine 18 h wall. Two consequences: (a) the 24 h leash is too short — **c14/c15 get ~48 h with a 12 h+-wall rotation criterion**; (b) opt_4's 26,677 floor is right-censored (rotated at 28 h, may never have gotten its second wave) — revisit candidate after c15. Campaign 14 (opt_7, 27,741, graph/stage 18166) live on the local 14 workers.
- **Rotation 5 EXECUTED 2026-07-09 ~20:33Z.** Campaign 14 (opt_7) banked at **26,185** — front-loaded (27,741 → 26,185 in ~6 h) then a genuine ~42 h wall with minima drifting up (26,19x → 26,27x); the full 48 h leash was honored and no second wave came. Campaign 15 (opt_8, 27,798, graph/stage 18881) live on the local 14 workers — the FINAL queued seed.
- **Basin-floor table so far:** original seed → **25,840**; opt_9 → 25,881; opt_2 → 25,996; opt_7 → 26,185; opt_4 → 26,677 (right-censored at 28 h). Three of five within 0.6% of the all-time best; spread ~3%. opt_2's multi-wave descent (second wave at hour ~37) remains the only wall-break observed.
- **PROGRAM COMPLETE 2026-07-13 ~22:47Z.** Campaign 15 (opt_8) banked at **26,385** (@stage 842 of 1,369; terminal wall 527 stages ≥ the 500-stage criterion; THREE waves — 26,464 @91, 26,441 @550 after a 458-stage wall, 26,385 @842 after a 291-stage wall). Full stage-denominated result + censoring analysis + decision options: **`multiseed-basin-program-results.md`**. Headline: floors 25,840/25,881/25,996/26,185/26,385/26,677 (~3.2% spread), no new all-time best; c11-c14 floors are censored (terminal walls 283-340 < the 458-stage pre-wave precedent); c10's 8,386-stage waveless wall is the strongest evidence 25,840 is a genuine deep floor for this move class. Fleet decision 2026-07-13: local 14 workers moved to a DEEP-WALL RE-RUN of campaign 11 (opt_9, resumed from floor graph 15491 = 25,881; stage 20931; >=500-stage wall criterion); c15 banked INACTIVE; M1 stays on campaign 10.
- Seed inventory + provenance: `single-flip-check/results/mseed/MANIFEST.md` (5 diverse recount-verified seeds, pairwise Hamming 122–157).
- **UI multi-campaign support — FIXED & MERGED** (bug diagnosed 2026-07-03: live boxes followed the *first* ACTIVE campaign while charts followed the *highest*, producing a mixed view under >1 ACTIVE campaign). Proper fix shipped as **ramsey-ui PR #16 (merged 2026-07-05)**: per-campaign live sampling — every ACTIVE campaign gets its own campaignId-tagged tick stream, per-campaign baselines, and the whole dashboard (boxes, throughput, best-results) follows the sidebar selection. Sampling is time-sliced (1 sample/campaign/sec into a shared 21,600-slot ring), so slow campaigns get equal datapoints; a slow campaign's chart merely *looks* sparse because most of its samples are 0 u/s between remote batch publishes. Follow-up **PR #17: "Campaign Min" stat card** — the selected campaign's own clique-count floor with an at-best / "+N above min" drift indicator (the basin-wall signal used at every rotation).

### 2. Phase 4 — in-process / batched MaxSAT solver  ← NOW THE LEAD STRATEGIC ITEM (2026-07-15)
The z3 *subprocess launch* (~0.1–0.15 s/call) is the window ladder's bottleneck (`windowed-maxsat-investigation.md`, Phase 3). Replacing it with an in-process or batched complete solver makes exact windows 5–10× cheaper — enabling |F| = 192/256 rungs and instant exact evaluation of any future candidate base. The cleanest "scalable tooling for the long game" build. (Research crate only, as always.)

**Why this is now the lead item:** the multi-seed basin program is complete (`multiseed-basin-program-results.md`) and the deep-wall re-runs are confirming that single/dual edge-flip search from Paley(281)+row-opt seeds floors at ~25.8–25.9K (c11 re-run: censored basin re-run to a full 500-stage wall found *nothing*). Six basins + two re-runs, none below 25,840. The flip-search move class has largely answered its question; **the remaining leverage is a bigger move class**, and complete windowed MaxSAT is the only tool with proven-unexplored territory. Prerequisite economics just improved from the other direction too: the worker kernel is now ~1.40× faster (`engine-optimization-review.md`, kernel round 2026-07-15), so the flip fleet frees up sooner for whatever's next.

### 3. Cyclotomic closure through order ~1021 — DONE 2026-07-18
Executed: `cyclotomic_wide` extended to 1024-bit rows (`WORDS` 8→16), Paley(281) anchor re-verified (0 / 5,979,680), swept all 39 primes ≡ 1 (mod 4) in (461, 1021] (≡ 3 skipped, empty by parity theorem). **15,000,842 colorings tested, 0 mono-8-free** (~73 min, 1 core). The classical cyclotomic 2-coloring family is now **certified empty for every prime order 269–1021** (~22.1M colorings total). No bound improvement, as expected. Writeup: `structurally-new-base-investigation.md` Experiment 4c. Family closed; do not re-run higher (negligible EV).

### 4. Exact-certify the confirmed floor graphs — LAUNCHED 2026-07-15
Campaign 11 is banked (floor 25,881, graph 15491, confirmed terminal at a 500-stage wall). `wmaxsat_pilot sweep` (|F|=48, single core, niced) is **running on 15491** — the never-certified second floor. **Graph 8644 (25,840) is already exact-locked to |F|=128 across ~1.1M windows — do NOT re-certify it** (a redundant re-run was caught and stopped 2026-07-15). If 15491 also locks, the characteristic-floor claim becomes a two-graph proven-optimum result. Details + status: `windowed-maxsat-investigation.md` ("Second-floor certification").

### 5. Optional — Vast.ai burst to parallelize the basin program
Instead of serial rotation, rent an EPYC bid instance (per `ramsey-vast-ai` tooling; workers point at this mw/Redis exactly like the remote machine already does) and run basins 12–15 as concurrent campaigns (one QM each — the two-QM pattern generalizes). Compresses ~2 weeks of rotation into ~3 days for a few tens of dollars. Ben's call (costs money). Note: uncommitted local edits to `docker/vast-ai/{vast-manager.py,on-start.sh}` exist — possibly WIP toward this.

---

## Standing don'ts (unchanged)

Do not restart campaign 2 / the retired trajectory methods (SA/tabu/VDS); do not re-run vreduce; do not expect bigger local windows to escape (exact-certified empty to |F|=128); production app code stays untouched — experiments live in `single-flip-check/`. Full do-not-retry list: `search-status-2026-06-14.md`.

## Endgame framing

If seeds 3–5 land in the 25.8–25.9K band: write up "the Paley+1 construction has a characteristic mono-8 floor ≈ 25.85K under local search" with the exact-certification evidence — the strongest statement of where the 50-year frontier actually sits computationally — while the toward-zero channels stay open per the project stance: new construction mathematics, or orders-of-magnitude more compute on the full non-vertex-transitive 282-space.

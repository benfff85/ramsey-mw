# July 2026 — Next Steps (active roadmap)

**Date:** 2026-07-02 (evening)
**Author:** Ben Ferenchak + Claude
**Supersedes:** `archive/may-2026-next-steps.md` as the live roadmap. Strategic background: `search-status-2026-06-14.md` (read first), `structurally-new-base-investigation.md` (all construction long shots closed through order 461 + all four order-282 Cayley groups), `windowed-maxsat-investigation.md` (~1.1M exact windows, zero escapes).

**Goal unchanged:** a 282-vertex 2-coloring with zero monochromatic K₈ → **R(8,8) ≥ 283**. Best-known 282-graph: **25,840 mono-8 (graph 8644, 2026-06-16)**.

---

## Where things stand (2026-07-02)

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

### 2. Phase 4 — in-process / batched MaxSAT solver
The z3 *subprocess launch* (~0.1–0.15 s/call) is the window ladder's bottleneck (`windowed-maxsat-investigation.md`, Phase 3). Replacing it with an in-process or batched complete solver makes exact windows 5–10× cheaper — enabling |F| = 192/256 rungs and instant exact evaluation of any future candidate base. The cleanest "scalable tooling for the long game" build. (Research crate only, as always.)

### 3. Cyclotomic closure through order ~1021
`cyclotomic_wide`'s row is 512-bit; one constant (`WORDS = 16`) extends it to 1024. Sweep primes 463–1021 (e ≤ 24). Odds ≈ 0 (mono-8 mass grows steeply: Paley 293 → 2.3M, 317 → 8.4M), but the cost is small and it closes the entire classical cyclotomic family "through 1000" permanently. A hit at order p would prove R(8,8) ≥ p+1 — upper bound is 1518, so nothing here is *logically* excluded.

### 4. Exact-certify campaign 11's wall graph
When c11 is banked, run `wmaxsat_pilot sweep` (|F| = 48, few thousand windows) on its floor graph, as was done for 8644. If the second basin's floor is also exact-locked, the "characteristic floor" claim gets proven-optimum teeth instead of heuristic ones.

### 5. Optional — Vast.ai burst to parallelize the basin program
Instead of serial rotation, rent an EPYC bid instance (per `ramsey-vast-ai` tooling; workers point at this mw/Redis exactly like the remote machine already does) and run basins 12–15 as concurrent campaigns (one QM each — the two-QM pattern generalizes). Compresses ~2 weeks of rotation into ~3 days for a few tens of dollars. Ben's call (costs money). Note: uncommitted local edits to `docker/vast-ai/{vast-manager.py,on-start.sh}` exist — possibly WIP toward this.

---

## Standing don'ts (unchanged)

Do not restart campaign 2 / the retired trajectory methods (SA/tabu/VDS); do not re-run vreduce; do not expect bigger local windows to escape (exact-certified empty to |F|=128); production app code stays untouched — experiments live in `single-flip-check/`. Full do-not-retry list: `search-status-2026-06-14.md`.

## Endgame framing

If seeds 3–5 land in the 25.8–25.9K band: write up "the Paley+1 construction has a characteristic mono-8 floor ≈ 25.85K under local search" with the exact-certification evidence — the strongest statement of where the 50-year frontier actually sits computationally — while the toward-zero channels stay open per the project stance: new construction mathematics, or orders-of-magnitude more compute on the full non-vertex-transitive 282-space.

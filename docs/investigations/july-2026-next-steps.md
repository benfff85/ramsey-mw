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

### 1. Multi-seed rotation (running; timer set)
- **Let campaign 11 tail-grind to the 24 h mark** — campaign 10's final −1,218 all came from the post-wall exhaustion grind, and campaign 11's tail probes *below* 25,840 territory. **A rotation timer fires ~19:17 on 2026-07-03** with a guard: hold if a new all-time best (< 25,840) is actively unfolding, otherwise bank the floor and rotate.
- **Rotation kit is pre-staged:** `single-flip-check/results/mseed/ROTATION-c12.md` + `rotate_c12.sql` (campaign 12 = seed opt_4, 27,668). Queue after that: opt_2 (27,677) → c13, opt_7 (27,741) → c14, opt_8 (27,798) → c15. ~1–2 days per basin at current cadence.
- Seed inventory + provenance: `single-flip-check/results/mseed/MANIFEST.md` (5 diverse recount-verified seeds, pairwise Hamming 122–157).

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

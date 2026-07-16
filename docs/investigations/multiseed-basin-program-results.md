# Multi-seed basin program — results

**Ran:** 2026-07-02 → 2026-07-13 (campaigns 11–15 serial on the local 14-worker fleet;
campaign 10 concurrent on the second machine). **All effort figures are stage counts**
(1 stage = one full ~392M-unit sweep of a base graph); wall-clock is not comparable
across campaigns because compute allocation varied.

## Question

Campaign 10's 25,840 was the only basin ever descended from a Paley(281)+rowopt seed.
Is ~25.85K a characteristic floor of the construction, or basin luck? Five diverse
seeds (pairwise Hamming 122–157, floors 27,104–27,798) were each given their own
campaign to sketch the floor distribution.

## Final table (stage-denominated)

| Campaign | Seed | Floor | Floor @stage | Total stages | Terminal wall (stages) |
|---|---|---|---|---|---|
| c10 | original | **25,840** | 256 | 8,642 | 8,386 — no wave ever |
| c11 | opt_9 | 25,881 | 123 | 463 | 340 |
| c13 | opt_2 | 25,996 | 926 | 1,209 | 283 |
| c14 | opt_7 | 26,185 | 87 | 425 | 338 |
| c15 | opt_8 | 26,385 | 842 | 1,369 | **527 — banked at the ≥500 criterion** |
| c12 | opt_4 | 26,677 | 97 | 392 | 295 |

Distribution: 25,840 / 25,881 / 25,996 / 26,185 / 26,385 / 26,677 — spread ≈ 3.2%,
three of six within 0.6% of the best. **No new all-time best.** The original seed's
25,840 sits at the favorable tail but is not an outlier.

## Multi-wave descents (the program's second finding)

Two of five new basins broke through walls that looked terminal:

- **opt_2 (c13):** wave-1 floor 26,695 @569 → wave 2 broke after a **69-stage** wall
  → 25,996 @926. Gain: **−699**.
- **opt_8 (c15):** wave-1 floor 26,464 @91 → wave 2 after a **458-stage** wall →
  26,441 @550 (−23) → wave 3 after a **291-stage** wall → 26,385 @842 (−56).

Observed pre-wave walls span 69–458 stages, which motivated the banking criterion
used for c15: **a floor counts as terminal only after a ≥500-stage wall.**

## The censoring caveat

c11, c12, c13, c14 were all retired under earlier time-based leashes with terminal
walls of **283–340 stages — every one shorter than the 458-stage pre-wave precedent.**
Their floors are lower bounds on nothing: any of them might have had another wave.
opt_2's −699 second wave shows the hidden gains can be large, so the banked spread
(especially opt_4's 26,677) should be read as "floor found within ~300 wall stages,"
not "floor of the basin." Only c10 (8,386-stage wall) and c15 (527) meet the ≥500 bar.

By the same token, c10's 8,386-stage waveless wall is the strongest evidence in hand
that 25,840 is a genuine deep floor **for this move class** (single + dual edge flips
with the best-novel cache).

## Decision points (fleet is still grinding c15's 527-stage wall — marginal value now)

1. **Deep-wall re-runs of censored basins** — restart campaigns from the banked best
   graphs of opt_9 (25,881, walled at 340) and/or opt_2 (25,996, walled at 283), the
   two closest to the all-time best, and hold them to the ≥500-stage criterion.
   Budget ≈ 500–800 stages each. This is the cheapest path to a possible sub-25,840.
2. **Fresh seeds** — widen the distribution sample (7th, 8th seed). More variance,
   but every new basin so far floored above 25,840.
3. **M1 / campaign-10 reassignment** — c10 is 8,386 stages past its floor; the M1's
   marginal value there is near zero. Repointing it at a deep-wall re-run doubles
   program throughput at no cost to the all-time best (already banked).
4. **Parallel basins on burst compute** — the roadmap §3 Vast option, if serial
   patience becomes the bottleneck.

## Deep-wall re-run results (2026-07-13 → ongoing)

Decision-point #1 was executed: re-run the near-best censored basins to the
≥500-stage criterion, resuming each campaign from its banked floor graph (the
global processed-hash set prevents base revisits, so a resumed campaign explores
only genuinely new derived graphs).

| Campaign (basin) | Banked floor | Re-run result | Wall since new floor |
|---|---|---|---|
| c11 (25,881) | 25,881 | **no improvement — terminal** | 500 stages (≈840 across both runs) |
| c13 (25,996) | 25,996 | **improved to 25,932** (−64, at re-run stage 113; graph 21856) | 508 stages — **CONFIRMED** |

**The two re-runs landed opposite — and that's the finding.** c11 was censored at
340 stages; re-running to a full 500-stage wall found **nothing** (terminal, like
c10). c13 was censored at 283 stages; re-running **cashed in the censoring** —
it dropped 25,996 → 25,932 early (stage 113), then walled for 508 stages. So
censoring is real and *sometimes* pays (c13) and *sometimes* doesn't (c11):
**censored ≠ reliably improvable, but ≠ certainly locked either.**

**The load-bearing result, though:** even after cashing in the most-censored
near-best basin, **25,932 is still above 25,840.** Three near-best basins are now
deep-walled — 25,840 (c10), 25,881 (c11), 25,932 (c13) — and **none reaches the
all-time best.** This *strengthens* the conclusion that 25,840 is a genuine deep
floor of Paley(281)+row-opt under single/dual edge flips: re-running censored
basins to deep walls moves the near-best floors around within ~0.4% but never
below the original. The remaining censored basins (c14 26,185, c12 26,677) are
far enough above 25,840 that even a c13-sized (−64) or c13-original-sized (−699)
wave wouldn't reach it — so further re-runs have low expected value. Move-class
change (Phase 4) remains the lead.

**Strategic read after c11 (updated if c13 also confirms):** six basin floors +
two deep-wall re-runs, nothing below 25,840. The accumulating evidence is that
this construction family (Paley(281)+row-opt) under this move class (single/dual
edge flips) floors at ~25.8–25.9K. More starting points — fresh seeds *or* more
re-runs — increasingly re-sample the same answer. The highest-value next move is
a **different move class** (Phase 4 in-process/batched MaxSAT, the one lever with
proven-unexplored territory), not more basins. Complementary cheap step: exact
window-certify the confirmed floor graphs (see `windowed-maxsat-investigation.md`
— 25,840 already locked to |F|=128; 25,881 certification launched 2026-07-15).

## Fleet performance context (why re-runs finished faster than the program)

The worker kernel was optimized mid-program (2026-07-15): the deep-wall re-runs
run at **~1.4× the pre-2026-07-15 stage rate** (see
`engine-optimization-review.md`, "Kernel optimization round 2026-07-15"), so a
500-stage wall verdict now lands in ~9h instead of ~13h. All stage-effort figures
in this doc are stage counts, not wall-clock, precisely because throughput changed
during the program.

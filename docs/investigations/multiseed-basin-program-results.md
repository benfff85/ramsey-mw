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

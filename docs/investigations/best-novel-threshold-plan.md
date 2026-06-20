# Best-Novel Threshold + Cross-Worker Learning — Implementation Plan

**Status:** ✅ SHIPPED 2026-06-19 — Phases 1–2 implemented, deployed to the live 14-worker stack, and validated. Merged to `develop` (worker PR #69, QM PR #73). · **Originally planned:** 2026-06-16

## Status / outcome (2026-06-19)

**Shipped, worker-only** (lower risk than the original 3-phase split — the QM needed no code change):
- **Phase 1 (hash parity):** `ramsey-worker-rust/src/hash.rs` (`derived_graph_hash`/`graph_hash`) byte-for-byte matches the QM `GraphHashUtil`; shared SHA-256 vectors asserted in **both** repos' tests (worker 5 tests, QM `GraphHashUtilTest`).
- **Phase 2 (novel-only cache):** insert Lua (`add_to_top_results`) now does `SISMEMBER processed_graph_hashes` and rejects visited graphs; `get_top_results_threshold` reads **slot 0** (best novel, active after the first entry). Worker computes the derived hash only at the rare record-breaker insert sites.
- **No QM code change:** the QM already walks `best_results` for the best *unprocessed* graph and already early-adopts a novel improvement in SCENARIO 1 — a novel-only set makes slot 0 always-novel, so it "just works." Only a parity test was added to the QM.

**Deviations from the original plan (all deliberate):**
- **Kept `TOP_RESULTS_COUNT=50`** (did not drop to 5). The u/s win comes from threshold=slot0, not K; with slot-0 semantics only record-breakers insert, so ZSETs naturally hold ~20–25 entries anyway. No compose change needed.
- **No fold-into-claim Lua, no `THRESHOLD_REFRESH_UNITS`** — per-batch `get_top_results_threshold` (now slot 0) + own-insert tightening was sufficient; deferred as premature.
- **No stage-start threshold seed** — plateau-escape risk; deferred per §15.

**Deploy:** built the worker image locally (arm64 generic, matches production `:develop`), recreated the 14 workers via `docker compose --no-deps --force-recreate` (redis/mw/qm untouched), then merged to `develop` so CI rebuilds the canonical Hub `:develop` (durability — the running local image would otherwise revert on the next reboot/pull).

**Validation (live):** insert-Lua exercised on live Dragonfly (novel insert / K-trim / visited-reject); production hashes consistent at n=282 (no worker↔QM mismatch); post-deploy stage ZSETs clean (0 visited / 23 entries); the "improvement but already processed → exhaustion" limbo **disappeared** on clean stages; **early-adopt fired** (bases descended 25959→25943→25927 with ~30s-apart progressions, impossible via 9-min exhaustion); 14 workers healthy, ~700k u/s. Still cycling the ~25,9xx plateau (this speeds/de-risks the grind, it does not escape the basin — as predicted in §15).

**Lesson — transitional contamination:** recreating workers *mid-stage* leaves the in-flight stage's ZSET with whatever the old (non-filtering) workers had inserted (saw a stale visited slot-0 on the stage created during the recreate). Harmless — the QM's unprocessed-fallback handled it and it cleared on the next progression — but for a clean cutover, prefer recreating at a stage boundary.

---


This is "Option B" from the throughput discussion. It changes the per-stage
`best_results` sorted set from "top-50 graphs by clique count" to "best *novel*
(unvisited) graphs," and makes that set the shared learning channel through
which all workers tighten their early-exit threshold. The net effects are:
higher work-unit throughput (u/s), robustness against dead-ends, an early-adopt
fast path that stops grinding stages to full exhaustion, and a simpler QM.

---

## 1. Motivation & live evidence

### The u/s ramp
With `PUBLISH_RESULTS=false`, a work unit is "retired" either **cheaply**
(early-termination bails when its base count can't beat the current top-N
threshold) or **expensively** (full clique count). Observed: u/s starts ~200k/s
on a fresh stage and climbs toward ~1M/s by stage end. Cause: the early-exit
threshold tightens as the `best_results` cache fills with better graphs.

Two sources of slack in today's design:
1. A fresh stage starts with an empty set → threshold `None` → **full-count
   everything** until the set fills to 50 (slow opening).
2. The threshold is the **50th-best** score (worst of the kept set), not the
   best — so even once active it's looser than it could be.

### The dead-end / cycle evidence (stage 8712, 2026-06-17 UTC)
Captured live: stage 8712 fully processed (392,495,529 / 392,495,529), 50
best_results clustered 25,840–25,999, all on base graph 8712 (count 25,920).
The QM logged for ~5.5 min, every 30s:

```
Improvement found (cliques=25840) but graph already processed, treating as exhaustion case
```

SCENARIO 1 only checks the **single best** (25,840) — a real 80-clique
improvement, but already-visited (an ancestor). It can't adopt-on-improvement,
so it falls through to the exhaustion path. At exhaustion it walked the list to
the **best unprocessed** result, `cliques=25918` (rank 8 — ranks 1–7 were all
visited), and progressed 8712 → 8713.

Trajectory around it (graph_id : clique_count):
```
8704:25915 8705:25918 8706:25913 8707:25923 8708:25915
8709:25908 8710:25912 8711:25923 8712:25920 8713:25918
```
The search has descended **27,401 → ~25,910 since the campaign-10 reseed**
(~5.4%) but is now **cycling on a plateau ~25,915**: the genuinely-better basin
(~25,840) is explored-and-blocked, so each stage can only reach marginal novel
moves, and it burns a full 392M-unit stage to take a ~2-clique step.

### What this tells us
- Keeping "top-50 **by count**" only survives a visited cluster **shallower than
  50**. Here it was 7 deep — comfortable, but in a cycling basin the visited
  cluster *deepens over time*. The day it exceeds 50, the count-only set is
  all-visited → a true dead-end.
- The system wastes whole stages grinding to exhaustion when a usable novel
  improvement already exists in the set.

Tracking **best-novel** instead of **best-by-count** fixes both, and tightening
the threshold to the best-novel reclaims the u/s slack.

---

## 2. Current design (baseline)

- `best_results:{stageId}` — Redis ZSET, score = clique_count, trimmed to
  `TOP_RESULTS_COUNT` (currently **50**, set via the shared env var on QM +
  all workers; middleware does not read it).
- **Worker** (`worker.rs`, `redis_client.rs`):
  - Per batch: `get_top_results_threshold(stage, N)` → score at slot **N-1**
    (the 50th = worst kept). `None` if set has < N entries.
  - Per unit: `early_limit` derived from threshold → bail (`continue`) if the
    unit can't beat it. If it qualifies (`count < threshold`), call
    `add_to_top_results` (Lua: `ZADD` + `ZREMRANGEBYRANK` trim + read back
    threshold), and tighten the local `top_threshold` from the return.
- **QM** (`StageProgressionMonitor.java`):
  - SCENARIO 1 (improvement): checks the single best; if it improves on base
    **and is novel**, progress immediately. If best is visited → "exhaustion
    case."
  - Exhaustion: walk the set for the **first unprocessed** result; progress to
    it; add its hash to `processed_graph_hashes`.
  - Visited keyed by `GraphHashUtil.computeDerivedGraphHash(base, edgesToFlip)`
    = **SHA-256** of the derived edge-data bitstring; membership in the
    unbounded `processed_graph_hashes` Redis set.

---

## 3. Goal

Make `best_results:{stageId}` hold the **best *novel* graphs**, use the
**best-novel** as the early-exit threshold, and have all workers learn each
other's best-novel through that shared set — cheaply.

Outcomes:
- **Higher u/s:** threshold = best-novel (tightest) and active after the *first*
  novel result (not the 50th).
- **No dead-ends:** progression candidate is always novel, regardless of how
  deep the visited cluster grows.
- **Early adopt:** progress as soon as a novel improvement appears, instead of
  grinding to exhaustion.
- **Simpler QM:** no "walk the list for first unprocessed"; no "improvement but
  visited" limbo.

---

## 4. Shared-state model

`best_results:{stageId}` stays a ZSET scored by clique_count, contract changed:

- **Novel-only.** A graph enters only if its derived hash is **not** in
  `processed_graph_hashes` (checked inside the insert Lua — authoritative, no
  worker-cache skew).
- **Threshold = slot 0** (global best-novel), not slot N-1. Early-exit bails on
  anything that can't beat the best-novel → tightest → max u/s; goes active
  after the **first** novel result.
- **Trim depth `K = 5`** (down from 50). Slots 1…4 are recent novel
  record-breakers, kept only as a QM fallback buffer (defense-in-depth).

This is a *small delta* on the existing Lua + a novelty check + a threshold-slot
change — not a rewrite.

---

## 5. Cross-worker learning — the cadence (the core question)

**Backbone: Lua + re-pull the threshold at batch fetch.** Refinements that make
it near-optimal without Pub/Sub:

1. **Novelty check inside the insert Lua** (`SISMEMBER processed_graph_hashes`)
   → the shared set is authoritatively novel; no per-worker visited cache to
   keep fresh, no skew with the QM.
2. **Fold the threshold read into the claim Lua** → a batch stays **one
   round-trip**, not two. The worker learns the global best-novel in the same
   call it already makes to claim work.
3. **Free in-batch tightening via the insert Lua's return value** → a worker
   that's finding improvements self-updates its threshold. Crucially, the
   workers that *most* need a tight threshold (those finding many near-best
   graphs) are exactly the ones inserting, so they stay fresh for free. A worker
   in a "dry spell" is bailing on everything anyway, so its slightly-stale
   threshold barely matters.
4. **Optional mid-batch refresh** `THRESHOLD_REFRESH_UNITS` (re-read slot 0
   every R units). Default off (= fetch size). Lower only if descent-phase A/B
   shows staleness waste.

**Staleness budget:** batch = 250,000 units; per-worker ~25k–250k u/s → a batch
is **1–10 s**. So per-batch re-pull = at most ~seconds stale. On a plateau the
threshold is ~static (free). In fast descent the gap is a few seconds; (3)
covers the active workers and (4) covers the rest.

**Why not Pub/Sub:** the threshold can only ever be as tight as best-found-so-far
and moves slowly within a stage; sub-second propagation buys negligible u/s
while adding an async subscriber + message handling to the hot loop. Revisit
only if measurement proves staleness is the binding constraint.

---

## 6. The Lua scripts

**Insert** (extends today's `add_to_top_results`) — keys
`best_results:{stage}`, `processed_graph_hashes`; args `score`, `json`, `hash`,
`K`:
```lua
if redis.call('SISMEMBER', KEYS[2], ARGV[3]) == 1 then
  return {0, -2}                       -- visited: reject, signal "skip"
end
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
redis.call('ZREMRANGEBYRANK', KEYS[1], ARGV[4], -1)        -- trim to K
local best = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')  -- slot 0
return {1, tonumber(best[2])}          -- kept, new best-novel threshold
```
Only **qualifying units** (record-breakers under the tight threshold) reach
this, so the `SISMEMBER` + worker-side hash cost is negligible in aggregate.

**Claim + threshold** (extends `claim_work_range`) — return the work range
**and** `ZRANGE best_results 0 0 WITHSCORES` in one invocation.

---

## 7. Worker changes (`worker.rs`, `redis_client.rs`)

- `get_top_results_threshold` → read **slot 0**; init `top_threshold` from the
  claim Lua's returned best-novel.
- In-loop: early-exit math unchanged, but `top_threshold` is now best-novel. On
  a qualifying unit: compute the **derived-graph SHA-256** (§9), call the insert
  Lua; on `kept` update `top_threshold` from the return; on `skip (-2)` just
  `continue`.
- Cross-worker freshness: (a) per-batch pull via claim Lua, (b) free tightening
  via insert returns, (c) optional `THRESHOLD_REFRESH_UNITS`.
- No local visited-set needed (Lua is authoritative).

---

## 8. QM changes (`StageProgressionMonitor.java`)

- ZSET is now guaranteed novel → **simplify progression**: SCENARIO 1 checks
  **slot 0**; if `slot0.count < base.count`, **adopt immediately** (folds in the
  "stop grinding to exhaustion" win — the 8712 case). Exhaustion: take slot 0.
- **Keep a defensive re-verify**: re-run `isGraphAlreadyProcessed` on the chosen
  graph; on the rare miss fall to slot 1…K-1. This makes the worker hash a pure
  *optimization* — if it's ever wrong, the system degrades to correct-but-slower,
  never incorrect.
- `processed_graph_hashes` ownership unchanged (QM adds on progression).

---

## 9. Hash decision — KEEP SHA-256

The visited key stays **SHA-256** of the derived edge-data bitstring (matching
`GraphHashUtil`). Rationale:

- **It's not on the hot path.** We hash only record-breakers (dozens–hundreds
  per stage), not the 392M units. At ~30 µs each that's single-digit ms/stage —
  optimizing it buys nothing. (If we ever hashed *every* unit it'd be ~hours/
  stage and we'd need both a faster hash and a cheaper input — but the tight
  threshold design specifically avoids that.)
- **Free cross-language byte-identical agreement.** Worker (Rust) and QM (Java)
  must emit identical digests or cycle-prevention silently breaks. SHA-256 is a
  rigid spec; every impl matches. A non-crypto hash (XXH3/wyhash) *can* match
  but only with pinned variant/seed/endianness — easy to get subtly wrong, for a
  speed win we don't need.
- **Effectively zero collision risk.** A collision = a novel graph silently
  flagged visited and skipped — possibly the solution graph. 256 bits makes that
  impractical; a 64-bit hash's ~10⁻⁶ lifetime risk is needless here.

**If** hashing ever profiles hot, the right lever is feeding SHA-256 the
**packed bits (~5 KB)** instead of the 39,621-char `'0'/'1'` string (~8× less
data, still cross-language-safe) — not a weaker algorithm. Either change
requires re-seeding `processed_graph_hashes` (QM supports reseed-from-MySQL), so
it's a coordinated migration, not a drop-in.

### Hash parity (required before rollout)
Worker must reproduce `computeDerivedGraphHash` byte-for-byte: take base
`edge_data`, flip the unit's edges at upper-triangular index
`v1*(n-1) − v1*(v1+1)/2 + v2 − 1`, SHA-256 the UTF-8 `'0'/'1'` string,
lowercase-hex. **Deliverable: a shared test vector** (fixed graph + fixed flips,
assert Rust digest == Java digest) checked into both repos and run in CI. A
mismatch makes everything "look novel" — caught by the QM re-verify (graceful)
but it kills the benefit, so gate rollout on this test.

---

## 10. Config

- `TOP_RESULTS_COUNT`: **50 → 5** (shared env var; worker trim depth + threshold
  buffer, QM fallback depth — keep equal as today).
- New optional `THRESHOLD_REFRESH_UNITS` (worker), default = fetch size (off).

---

## 11. Rollout

- Semantic change to **shared Redis state** → deploy **QM + all workers
  together**, ideally at a stage boundary. Mixed old/new workers would let old
  workers insert visited graphs — tolerable (QM re-verify) but avoid.
- Surgical Portainer deploy (pre-pull images, `pullImage=false`), confirm at a
  fresh stage. **Never push compose to Portainer without explicit confirmation.**

---

## 12. Validation & measurement

- **Offline first:** extend the `single-flip-check` pilot harness to replay a
  real stage's work against the new threshold/novelty logic and confirm it picks
  the same progression graph the live QM did (e.g. reproduce 8712 → 25918) with
  the visited filter active.
- **Hash vectors** (§9) green in CI.
- **A/B live:** the metric is **stage wall-clock + best-clique trajectory**, NOT
  raw u/s. Expect (i) higher early-stage u/s, (ii) shorter stages in
  visited-heavy regions via early-adopt, (iii) no dead-ends as visited clusters
  deepen.

---

## 13. Risks & mitigations

- **Plateau / escape moves (the current regime!).** When no novel move beats
  base, the ZSET naturally starts empty → `None` → full-count until the first
  novel (uphill) result, then tightens to the least-uphill novels. Escape moves
  are preserved **as long as you don't aggressively seed** the threshold below
  where they live. So **do not** seed the stage-start threshold to `base − 0`;
  keep the natural None→tighten behavior (or seed `base + margin` only if you
  later add seeding — see Future).
- **Hash skew:** QM re-verify (§8) → graceful degradation.
- **Buffer too small:** `K=5` + re-verify; bump K if logs show re-verify walking
  past slot 0.

---

## 14. Phasing

- **Phase 0 (optional, hours):** QM-only early-adopt — make SCENARIO 1 walk the
  top-N for the best *novel* improvement (what exhaustion already does). Pure QM,
  reversible, kills the exhaustion-grind immediately; de-risks before touching
  workers.
- **Phase 1:** hash parity (Rust impl + shared test vector in CI).
- **Phase 2:** insert Lua + claim Lua + worker threshold=slot0 + `K=5`; QM
  simplification + re-verify.
- **Phase 3:** deploy QM + workers together at a stage boundary; A/B; then tune
  `THRESHOLD_REFRESH_UNITS` only if descent-phase staleness shows up.

---

## 15. Future / open questions

- **Stage-start threshold seed.** Seeding from base count would kill the slow
  opening entirely, but breaks plateau escape unless seeded with an uphill
  margin. Defer until the descent regime (not plateau) is the common case, or
  make the margin adaptive.
- **Hashing the packed bits** (§9) if hashing ever profiles hot.
- **The strategic problem is the plateau, not the engine.** This plan makes the
  existing single/double-flip descent *faster and more robust*; it does not
  escape the ~25,9xx local-minimum basin. That needs a different move class
  (multi-vertex / construction-path), tracked elsewhere.

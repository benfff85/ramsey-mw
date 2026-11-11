# Engine performance: what shipped, what it bought, what failed

A summary of the worker and fleet performance work, June – October 2026. It replaces four design documents (the
June engine review, the best-novel threshold plan, and the pair-move hoist proposal and review; full text in git
history before 2026-11-11). The measured, part-by-part log is `post-hoist-bottleneck-review.md`, and
`fleet-throughput-timeline.html` charts the same period.

**Before any performance work:** profile first, change one thing, re-measure the baseline next to the variant, and
re-profile after every win. The bottleneck moved at least five times in this record.

## Timeline

| date | change | measured effect |
|---|---|---|
| 2026-06-12 | Single edge flips of both colours added to the move set | search revived after the two-flip wall |
| 2026-06-19 | **Best-novel threshold** (worker #69, QM #73): the per-stage best set rejects already-visited graphs, and the early-exit threshold is the best novel result | about 3× stage rate (6.48 → 18.4 stages/h on 14 workers); no more "all top-N visited" stalls |
| 2026-07-15 | **Kernel round** (worker #75, #77, #78): leaf popcount shortcut, dual adjacency, R and X sets dropped, k−2 level inlined | 1.40× per worker, validated identical on a full 392M-unit stage |
| 2026-07-18 | **Fleet abstraction**: a `fleet` table maps platform to campaign; one campaign-agnostic queue manager; `campaign.status` derived | repointing is an API call, not a redeploy (`fleet-abstraction-plan.md`) |
| 2026-07-26 | **Pair-move hoist** (`hoist.rs`): `created` for a pair flip derived from memoised per-edge counts; 86% of pairs need no traversal | about 29× on a sweep in the harness; stage time 186 s → 23 s once the surrounding costs were removed |
| 2026-07-28 | Stage-status index; regime-aware hoist gate; shared slice fill | removed an 11.8 ms full table scan per stage advance |
| 2026-08-24 | **Event-driven stage turnover** (worker #112, QM #84); per-stage Redis TTLs; worker count 14 → 16 | +15–20% stage rate; +3.6% |
| 2026-08-24 to 08-26 | Hoist carry across stage advances, two kernel pushes, compressed candidate set, GPU-assisted corrections run concurrently | fleet 88 → 322 M units/s |
| 2026-08-31 | GPU shape-packed dispatch; dense-64 Metal correction | +7.5%; +37% in A/B/A/B on campaign 3 |
| 2026-10-03 | **Row selector** (`separable.rs`, worker #129–#131): exact row-level retirement of pair units that cannot beat the threshold | +116% on campaign 10 (644 → 1,342 M units/s) |
| 2026-10-07 | Graph delta lineage (`GRAPH_STORAGE_MODE=DELTA`); queue-manager kick check no longer fetches the full series; Dragonfly 2.0, snapshots every 8 h | stage rate 4.84 → 6.6 per second; MySQL writes 83 → 13.7 GB/day |
| 2026-10-08 | Per-cycle middleware poll removed (worker #136, #137); GPU workers 10 → 16 (worker #138); JDBC chatter removed (mw #224) | +12.6% stage rate (6.96 → 7.84 per second); statements 642 → 211 per second |

State at the last measurement (2026-10-08): the fleet is bound by stage turnover, not compute. About 45% of CPU
is idle and a stage takes roughly 0.13 s. The fleet has been paused since 2026-11-09.

## How the pieces work

- **Best-novel threshold.** The insert script checks `processed_graph_hashes` and rejects visited graphs, so slot 0
  of `best_results` is always novel and is the early-exit threshold. The Rust and Java SHA-256 graph hashes are
  pinned to shared test vectors. The queue manager needed no change.
- **Hoist.** `created = (C_b − X) + (D_r − Y)`, where C_b and D_r are per-edge counts and X, Y are corrections that
  are non-zero only when the cross pairs are all one colour. The table is filled co-operatively and published to
  Redis per graph, and carried across a stage advance for every entry whose cross-pair relation to the flipped
  edges is Mixed in both graphs. That predicate is accuracy-critical: nothing downstream catches a stale entry.
- **Row selector.** Retires whole rows of pair units whose best possible value cannot beat the threshold. On
  Paley-structured graphs it retires about 99.9% of pair units.
- **GPU.** Metal workers run beside CPU workers. It pays only as concurrent workers, never as an offload from a
  CPU worker, and the mix is a balance of two resources.

## Validation rules

- Any kernel or graph change is replayed against a full already-processed stage
  (`single-flip-check/src/bin/kernel_equiv_replay.rs`, `hoist_equiv_replay.rs`) and must match unit for unit.
- The external oracle is the database: recorded clique counts recounted by an independent counter. It is the only
  test that caught a wrong kernel.
- Carry-forward is checked at production scale: all 39,621 entries against a fresh rebuild on real 282-vertex
  graphs (`cargo test --release --test carry_forward_production_scale -- --ignored`).

## Measured and rejected

- Profile-guided optimisation (a wash), a stage-tail taper (mechanism works, net zero), `TARGET_CPU` builds,
  removing the per-unit allocation (0.2% of time), the sequential inner-loop restructure (1.05×), Postgres.
- GPU offload from a CPU worker (0.94×); 18 workers on 16 cores; adding Docker workers once turnover-bound (+1%).
- Running the M1 fleet on the M4's campaign: −42%, because its slower batches gate each stage.
- Participation-ordered enumeration (see `search-history.md`).
- An incumbent-relative guard for doomed kicks (about 12% margin, too small).

## Lessons

- Making one stage of a pipeline 30 times faster relocates the bottleneck, usually to something nobody measured:
  after the hoist it was a table scan, a per-cycle HTTP call, a fixed batch size, then a one-second sleep.
- Look at deployment before code. Worker count, a launch agent restarting with no environment, and a 1 s poll each
  cost more than most kernel changes gained.
- Gains do not add. Measure the end-to-end number and treat per-change figures as attribution.
- Two conclusions in the hoist review were overturned by checking ground truth instead of the instrument.

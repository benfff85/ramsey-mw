# Fleet Throughput Timeline — Design Spec

## Purpose

Create a durable, standalone HTML investigation that shows how Ramsey fleet throughput evolved from the legacy Java worker through the Rust worker and the subsequent performance work. The unit throughout is **work units per second (WU/s)**.

The report must distinguish controlled local benchmarks from all-fleet historical evidence so a configuration-specific measurement is never presented as a total-fleet result.

## Deliverable

`docs/investigations/fleet-throughput-timeline.html`

The file is self-contained: data, CSS, JavaScript, and the chart renderer are all embedded. It must open directly from disk without a server or network access.

## Evidence model

### Controlled measurements

The milestone line uses the measured values recorded in the investigation documents. Each point stores its rate, configuration scope, source location, and confidence:

| Era | Measurement treatment |
|---|---|
| Legacy Java | Clearly marked stage-cadence equivalent; early records do not retain a processed-work counter. |
| Initial Rust | Stage-cadence equivalent from the initial Rust deployment period. |
| Post-Rust improvements | Directly measured fleet or worker rates from the investigation notes. |
| Shape packing / dense-64 | Controlled ABAB M4 composite rate; explicitly marked M1-paused, not all-fleet. |

### All-fleet stage-progression evidence

The database does **not** retain a historical processed-work counter. `details = source: EXHAUSTIVE`
identifies the worker mode that produced the selected graph; it does not mean that the preceding
stage fully swept its work universe. The accurate per-stage `processed_count` expires after stage
turnover, and the durable `processed_total:{campaignId}` counter does not keep a time series.

The report therefore shows a separate, clearly labelled **stage-equivalent WU/s** diagnostic for
fixed six-hour windows:

```
stage-equivalent WU/s = sum(campaign.total_pairs for ordinary stage transitions) / 21,600 seconds
```

Perturbation-created stages are excluded. This aggregation naturally combines concurrent
campaigns/fleets, but it is a cadence proxy: early-adopt stages may have processed only part of
their nominal work universe. It must never be plotted as, or used to claim, actual total fleet
throughput. It is supporting operational context for the controlled WU/s measurements.

## Interaction and visual design

- A dark, instrumentation-inspired visual system: deep blue-green background, phosphor mint primary trace, warm amber milestone accents, and restrained grid lines.
- A responsive custom SVG chart with a logarithmic WU/s axis. No external chart library or font is required.
- Controls for controlled milestones, the distinctly labelled all-fleet stage-equivalent proxy, and both.
- Hover/focusable milestones show the exact rate, scope, commit/source, and a concise explanation.
- A milestone rail and evidence ledger provide the full text when dense labels would obscure the chart.
- A methodology section explains the conversion, source limits, and why the two measurement series are not conflated.

## Data sources

1. `ramsey-worker-rust` Git history for commit hashes, timestamps, and optimization labels.
2. `docs/investigations/post-hoist-bottleneck-review.md`, `pair-move-hoist-proposal.md`, and `search-status-2026-06-14.md` for controlled measurements.
3. Read-only MySQL aggregation of `stage` transition cadence and `campaign.total_pairs` for six-hour all-fleet stage-equivalent evidence windows.

## Quality gates

1. Validate all displayed rates and source labels against their note/commit before embedding.
2. Run `git diff --check`.
3. Serve or open the standalone file locally and inspect it in a browser at desktop and narrow widths.
4. Verify chart controls, tooltip behavior, the stage-equivalent caveat, and text accessibility without external network access.

## Non-goals

- No live database/API connection from the HTML file.
- No claim that an M4-only test measures total all-fleet capacity.
- No conversion of stages-per-hour to WU/s unless a full-sweep/equivalent assumption is explicitly disclosed.

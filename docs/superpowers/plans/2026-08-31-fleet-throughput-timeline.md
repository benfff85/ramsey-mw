# Fleet Throughput Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Build a self-contained interactive HTML report that charts Ramsey throughput history in work units per second and makes the evidence and scope of every optimization explicit.

**Architecture:** One HTML file owns the semantic content, embedded measurement JSON, CSS tokens, and a custom SVG renderer. A small Node assertion script validates the embedded data and essential offline/semantic properties; browser automation validates the rendered desktop and narrow views.

**Tech Stack:** Static HTML5, embedded CSS, vanilla JavaScript/SVG, Node.js built-in assert/fs, Playwright CLI for visual and interaction QA.

**Spec:** docs/superpowers/specs/2026-08-31-fleet-throughput-timeline.md

## Global Constraints

- Keep all displayed rates in WU/s; use M WU/s only as a display abbreviation.
- Keep the report self-contained: no CDN, API, database, image, font, or network request.
- Mark every M4-only / M1-paused benchmark with its scope; never call it all-fleet.
- Label database-derived values as a stage-equivalent cadence proxy, never actual total fleet throughput.
- Explain that early-adopt stages may contain only partial work and that historical processed counters are unavailable.
- Preserve unrelated dirty files; stage only files created for this report.

---

## File Structure

- docs/investigations/fleet-throughput-timeline.html — semantic report, embedded source data, custom SVG rendering, controls, tooltip, ledger, and methodology.
- docs/investigations/fleet-throughput-timeline.test.mjs — dependency-free assertions for the report's embedded JSON and required disclosure/interaction hooks.

### Task 1: Establish validated evidence data and document skeleton

**Files:**

- Create: docs/investigations/fleet-throughput-timeline.html
- Create: docs/investigations/fleet-throughput-timeline.test.mjs

**Interfaces:**

- Produces a JSON script element with id="timeline-data" containing { milestones, stageEvidence }.
- Each milestone has { date, title, rate, scope, kind, commit, source, note }.
- Each stage-evidence point has { date, rate, label } and represents a fixed six-hour all-fleet stage-equivalent cadence window.

- [x] **Step 1: Write the failing data-contract test**

Create fleet-throughput-timeline.test.mjs with this executable contract:

~~~js
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const html = await readFile(new URL('./fleet-throughput-timeline.html', import.meta.url), 'utf8');
const match = html.match(/<script id="timeline-data" type="application\/json">([\s\S]*?)<\/script>/);
assert.ok(match, 'timeline-data JSON must be embedded in the report');
const { milestones, stageEvidence } = JSON.parse(match[1]);

assert.ok(milestones.length >= 10, 'the report must cover Java, Rust, and subsequent optimization milestones');
assert.ok(stageEvidence.length >= 8, 'the report must include all-fleet stage-equivalent evidence around optimization dates');
for (const point of [...milestones, ...stageEvidence]) {
  assert.ok(Number.isFinite(point.rate) && point.rate > 0, 'invalid rate for ' + (point.title ?? point.label));
  assert.match(point.date, /^202[5-6]-\d{2}-\d{2}/, 'invalid date for ' + (point.title ?? point.label));
}
assert.equal(
  [...milestones].map((point) => point.date).join(','),
  [...milestones].sort((a, b) => a.date.localeCompare(b.date)).map((point) => point.date).join(','),
  'milestones must be chronological',
);
assert.ok(milestones.some((point) => point.scope.includes('M1 paused')), 'M4-only ABAB results need an M1-paused scope');
~~~

- [x] **Step 2: Run the test and confirm the scaffold is absent**

Run: node docs/investigations/fleet-throughput-timeline.test.mjs

Expected: failure because fleet-throughput-timeline.html does not yet exist.

- [x] **Step 3: Build the semantic skeleton and source dataset**

Create the report with a main element containing header, section#chart-panel, section#milestone-ledger, and section#methodology. Embed a timeline-data JSON block that includes these controlled milestones:

~~~js
[
  { date: '2025-12-16', title: 'Java worker era', rate: 3389, kind: 'legacy-equivalent' },
  { date: '2025-12-21', title: 'Rust worker deployed', rate: 949000, kind: 'legacy-equivalent' },
  { date: '2026-07-25', title: 'Pre-hoist baseline', rate: 1040000, kind: 'controlled' },
  { date: '2026-07-27', title: 'Hoisted pair evaluation', rate: 29000000, kind: 'measured-fleet' },
  { date: '2026-08-24', title: 'Carried hoist table', rate: 93610000, kind: 'controlled' },
  { date: '2026-08-24', title: 'Counting-kernel push', rate: 132000000, kind: 'measured-fleet' },
  { date: '2026-08-25', title: 'Compressed candidate kernel', rate: 172000000, kind: 'measured-fleet' },
  { date: '2026-08-25', title: 'GPU hybrid optimum', rate: 274300000, kind: 'measured-fleet' },
  { date: '2026-08-26', title: 'Compressed single-edge fill', rate: 321900000, kind: 'measured-fleet' },
  { date: '2026-08-31', title: 'Shape-packed GPU dispatch', rate: 415545000, kind: 'controlled', scope: 'M4 composite; M1 paused' },
  { date: '2026-08-31', title: 'Dense-64 Metal correction', rate: 559418000, kind: 'controlled', scope: 'M4 composite; M1 paused' }
]
~~~

Set source metadata exactly as follows: Rust deployment uses 0d41cd8 plus the compose switch 3d45c3f; the pre-hoist baseline cites pair-move-hoist-proposal.md lines 88-90; the hoist cites cb1269b and 7b8be0b plus post-hoist-bottleneck-review.md lines 30-34; carry cites 2c7c560 and the Part 7 result; kernel work cites d0a9155, 1fa26e6, and 6eed90b; GPU cites 4285fb0 and 1231775; single-fill cites 2698d26; the two 2026-08-31 results cite Parts 13 and 14 and are marked uncommitted working-tree deployments.

Embed database-derived six-hour **stage-equivalent** samples immediately before and after the relevant deployment dates. Each sample uses ordinary non-perturbation stage transitions, sum(total_pairs) / 21,600, and a label stating that it is a cadence proxy rather than actual WU/s.

- [x] **Step 4: Run the data-contract test**

Run: node docs/investigations/fleet-throughput-timeline.test.mjs

Expected: pass, validating positive chronological WU/s measurements and the M1-paused disclosure.

- [x] **Step 5: Commit the validated evidence layer**

~~~bash
git add docs/investigations/fleet-throughput-timeline.html docs/investigations/fleet-throughput-timeline.test.mjs
git commit -m "docs: add fleet throughput evidence timeline"
~~~

### Task 2: Render the interactive, offline SVG visualization

**Files:**

- Modify: docs/investigations/fleet-throughput-timeline.html
- Modify: docs/investigations/fleet-throughput-timeline.test.mjs

**Interfaces:**

- Consumes the timeline-data object from Task 1.
- Produces renderChart(mode), where mode is both, milestones, or stage-evidence.
- Produces accessible buttons with data-mode and an aria-pressed state.

- [x] **Step 1: Extend the test with the expected interaction hooks**

Append these assertions to the Node test:

~~~js
assert.match(html, /id="throughput-chart"/, 'the chart SVG mount must exist');
assert.match(html, /data-mode="both"/, 'both-traces control must exist');
assert.match(html, /data-mode="milestones"/, 'milestones control must exist');
assert.match(html, /data-mode="stage-evidence"/, 'stage-equivalent evidence control must exist');
assert.match(html, /function renderChart\(mode\)/, 'chart rendering must be encapsulated in renderChart');
assert.doesNotMatch(html, /https?:\/\//, 'the report must not depend on a network resource');
~~~

- [x] **Step 2: Run the test and confirm interaction hooks are absent**

Run: node docs/investigations/fleet-throughput-timeline.test.mjs

Expected: failure on the first missing chart/control assertion.

- [x] **Step 3: Implement the chart and controls**

Use inline CSS variables --void: #07131a, --slate: #102b36, --grid: #214552, --phosphor: #53e3c2, --amber: #f0b35a, and --paper: #d8e8e8. Build renderChart(mode) around an SVG viewBox and use this log-rate mapping:

~~~js
const yForRate = (rate) => {
  const min = Math.log10(1_000);
  const max = Math.log10(1_000_000_000);
  return bottom - ((Math.log10(rate) - min) / (max - min)) * plotHeight;
};
~~~

Render a stepped phosphor line for measured milestones and a visually separate dashed mint trace for stage-equivalent evidence. Add mouse/focus handlers that populate a visible tooltip with title, formatted rate, scope, source, and caveat. Wire each button to set aria-pressed, update the chart, and retain keyboard accessibility.

- [x] **Step 4: Run the test and inspect script execution**

Run: node docs/investigations/fleet-throughput-timeline.test.mjs

Expected: pass.

Then run: playwright-cli -s=ramsey-throughput open file:///Users/benferenchak/IdeaProjects/ramsey/ramsey-mw/docs/investigations/fleet-throughput-timeline.html

Expected: page loads with the title, chart, controls, and no console errors.

- [x] **Step 5: Commit the interactive visualization**

~~~bash
git add docs/investigations/fleet-throughput-timeline.html docs/investigations/fleet-throughput-timeline.test.mjs
git commit -m "docs: render interactive fleet throughput chart"
~~~

### Task 3: Add evidence ledger, disclosure, and visual QA

**Files:**

- Modify: docs/investigations/fleet-throughput-timeline.html
- Modify: docs/investigations/fleet-throughput-timeline.test.mjs

**Interfaces:**

- Consumes the same milestone/evidence data and emits the ledger from it so chart and table cannot drift.
- Provides formatRate(rate) that renders WU/s and M WU/s consistently.

- [x] **Step 1: Extend the test for mandatory disclosure text**

Append:

~~~js
for (const requiredText of [
  'stage-equivalent',
  'does not represent actual total fleet throughput',
  'early-adopt stages may contain only partial work',
  'M1 paused',
  'stage-cadence equivalent',
]) {
  assert.ok(html.includes(requiredText), 'missing disclosure: ' + requiredText);
}
assert.match(html, /function formatRate\(rate\)/, 'one formatter must own WU/s display');
~~~

- [x] **Step 2: Run the test and confirm the disclosure gap**

Run: node docs/investigations/fleet-throughput-timeline.test.mjs

Expected: failure for the first missing disclosure or formatter.

- [x] **Step 3: Complete the ledger and methodology**

Render the milestone rail and a semantic table from the milestone records. Add the exact stage-equivalent formula, the legacy-Java limitation, and the M4/M1 scope note. Ensure the table identifies direct controlled A/B results, operational all-fleet observations, and the stage-equivalent proxy separately.

- [x] **Step 4: Run static and browser verification**

~~~bash
node docs/investigations/fleet-throughput-timeline.test.mjs
git diff --check
playwright-cli -s=ramsey-throughput resize 1440 1000
playwright-cli -s=ramsey-throughput snapshot
playwright-cli -s=ramsey-throughput eval "document.querySelector('[data-mode=stage-evidence]').click()"
playwright-cli -s=ramsey-throughput snapshot
playwright-cli -s=ramsey-throughput resize 390 844
playwright-cli -s=ramsey-throughput snapshot
playwright-cli -s=ramsey-throughput console
playwright-cli -s=ramsey-throughput close
~~~

Expected: all Node assertions pass, no whitespace errors, controls change the visible trace, the narrow layout remains readable, and browser console output is empty.

- [x] **Step 5: Commit the completed report**

~~~bash
git add docs/investigations/fleet-throughput-timeline.html docs/investigations/fleet-throughput-timeline.test.mjs
git commit -m "docs: complete fleet throughput timeline report"
~~~

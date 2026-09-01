import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const reportUrl = new URL('./fleet-throughput-timeline.html', import.meta.url);

test('the report embeds ordered, positive WU/s milestones with scoped M4-only benchmarks', async () => {
  const html = await readFile(reportUrl, 'utf8');
  const match = html.match(/<script id="timeline-data" type="application\/json">([\s\S]*?)<\/script>/);
  assert.ok(match, 'the report must embed its measurement data');

  const { milestones, stageEvidence } = JSON.parse(match[1]);
  assert.ok(milestones.length >= 11, 'expected Java, Rust, and all material optimization milestones');
  assert.ok(stageEvidence.length >= 8, 'expected stage-progression evidence around deployment dates');

  const requiredTitles = [
    'Java worker era',
    'Rust worker deployed',
    'Hoisted pair evaluation',
    'GPU hybrid optimum',
    'Dense-64 Metal correction',
  ];
  assert.deepEqual(requiredTitles, requiredTitles.filter((title) => milestones.some((point) => point.title === title)));

  for (const point of [...milestones, ...stageEvidence]) {
    assert.ok(Number.isFinite(point.rate) && point.rate > 0, `invalid rate for ${point.title ?? point.label}`);
    assert.match(point.date, /^202[5-6]-\d{2}-\d{2}/, `invalid date for ${point.title ?? point.label}`);
  }

  const dates = milestones.map((point) => point.date);
  assert.deepEqual(dates, [...dates].sort(), 'milestones must be chronological');

  const m4Only = milestones.filter((point) => point.title.includes('GPU'));
  assert.ok(m4Only.some((point) => point.scope.includes('M1 paused')), 'M4-only results must disclose that M1 was paused');
});

test('the report provides an offline SVG chart with selectable evidence layers', async () => {
  const html = await readFile(reportUrl, 'utf8');

  assert.match(html, /<svg[^>]+id="throughput-chart"/, 'the report must render its chart in an SVG');
  assert.match(html, /data-mode="both"/, 'the combined-layer control is missing');
  assert.match(html, /data-mode="milestones"/, 'the measured-throughput control is missing');
  assert.match(html, /data-mode="stage-evidence"/, 'the stage-equivalent control is missing');
  assert.match(html, /function renderChart\(mode\)/, 'the chart renderer is missing');
  assert.doesNotMatch(html, /<(?:script|link|img|iframe)[^>]+(?:src|href)=["']https?:/i, 'the report must not load an external resource');
});

test('the report suppresses the browser fallback favicon request', async () => {
  const html = await readFile(reportUrl, 'utf8');
  assert.match(html, /<link rel="icon" href="data:,">/, 'the self-contained page must declare an empty data favicon');
});

test('the ledger distinguishes measured, controlled, and legacy evidence', async () => {
  const html = await readFile(reportUrl, 'utf8');

  assert.match(html, /<th>Measurement<\/th>/, 'the evidence ledger needs a measurement-type column');
  assert.match(html, /kindName\(point\.kind\)/, 'the ledger must derive its measurement type from the shared dataset');
  assert.match(html, /cell\.className = 'measurement'/, 'the ledger must style the measurement type separately from provenance');

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
});

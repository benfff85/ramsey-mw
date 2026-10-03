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
    'Sustained early Rust era',
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

test('the early Rust anchor uses sustained stage evidence instead of a switchover outlier', async () => {
  const html = await readFile(reportUrl, 'utf8');
  const match = html.match(/<script id="timeline-data" type="application\/json">([\s\S]*?)<\/script>/);
  const { milestones, events } = JSON.parse(match[1]);
  const sustainedRust = milestones.find((point) => point.title === 'Sustained early Rust era');

  assert.deepEqual(
    { date: sustainedRust?.date, rate: sustainedRust?.rate, kind: sustainedRust?.kind },
    { date: '2026-03-01', rate: 58194, kind: 'legacy-equivalent' },
    'the plotted Rust-era anchor must use the 577-stage sustained window',
  );
  assert.match(sustainedRust.source, /577 campaign-1 stage rows/, 'the sustained Rust source must retain its sample size');
  assert.ok(events.some((event) => event.title === 'Rust worker deployed' && event.date === '2025-12-21'), 'the Dec 21 deployment must remain visible as an unplotted event');
  assert.ok(!milestones.some((point) => point.rate === 949000), 'a single 449-second stage must not remain a plotted Rust milestone');
  assert.doesNotMatch(html, /949k/i, 'the superseded one-stage rate must not remain displayed in the report');
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

test('the chart lets readers switch its vertical axis between log and linear scales', async () => {
  const html = await readFile(reportUrl, 'utf8');

  assert.match(html, /data-scale="log"/, 'the log-scale selector is missing');
  assert.match(html, /data-scale="linear"/, 'the linear-scale selector is missing');
  assert.match(html, /let activeScale = 'log';/, 'log must remain the initial chart scale');
  assert.match(html, /activeScale === 'linear'/, 'the renderer must branch for a linear axis');
  assert.match(html, /scaleControls\.forEach/, 'the scale selector must re-render the chart');
});

test('the report stays focused on one chart and one compact evidence table', async () => {
  const html = await readFile(reportUrl, 'utf8');

  assert.equal((html.match(/<section\b/g) ?? []).length, 2, 'the page must contain only the chart and evidence-table sections');
  assert.match(html, /<table id="evidence-table">/, 'the compact evidence table is missing');
  assert.match(html, /<th>Rate<\/th>/, 'the table must retain the WU\/s rate');
  assert.match(html, /<th>Evidence<\/th>/, 'the table must retain provenance');
  assert.doesNotMatch(html, /id="event-list"/, 'the separate event rail should not be rendered');
  assert.doesNotMatch(html, /id="proxy-list"/, 'the separate stage-progression list should not be rendered');
  assert.doesNotMatch(html, /id="methodology"/, 'the separate methodology panel should not be rendered');
});

test('the report suppresses the browser fallback favicon request', async () => {
  const html = await readFile(reportUrl, 'utf8');
  assert.match(html, /<link rel="icon" href="data:,">/, 'the self-contained page must declare an empty data favicon');
});

test('the compact ledger keeps rate and evidence boundaries visible', async () => {
  const html = await readFile(reportUrl, 'utf8');

  assert.doesNotMatch(html, /<th>Measurement<\/th>/, 'measurement type belongs in compact evidence copy, not its own column');
  assert.doesNotMatch(html, /<th>Scope<\/th>/, 'scope belongs in compact evidence copy, not its own column');
  assert.match(html, /point\.commit \+ ' · ' \+ point\.source/, 'the table must derive concise provenance from the shared dataset');

  for (const requiredText of [
    'stage-equivalent',
    'M1 paused',
    'stage-cadence equivalent',
  ]) {
    assert.ok(html.includes(requiredText), 'missing disclosure: ' + requiredText);
  }
  assert.match(html, /function formatRate\(rate\)/, 'one formatter must own WU/s display');
});

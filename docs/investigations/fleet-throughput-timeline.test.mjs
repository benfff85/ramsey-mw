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

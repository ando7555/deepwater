import test from 'node:test';
import assert from 'node:assert/strict';
import { ageFrom, cleanLanguages, fitWithin, languagesText, visibilitySummary, MAX_LANGUAGES } from './profileTools.js';

test('age is counted in whole years and the birthday itself counts', () => {
  const day = new Date(Date.UTC(2026, 10, 27));
  assert.equal(ageFrom('1990-11-27', day), 36);
  assert.equal(ageFrom('1990-11-28', day), 35);
  assert.equal(ageFrom('2008-12-01', day), 17);
  assert.equal(ageFrom('', day), null);
  assert.equal(ageFrom('27/11/1990', day), null);
});

test('languages drop blanks and duplicates and need a level', () => {
  const { list, problems } = cleanLanguages([
    { language: ' Polish ', level: 'NATIVE' }, { language: 'polish', level: 'BASIC' }, { language: '', level: 'GOOD' },
    { language: 'Danish', level: '' },
  ]);
  assert.deepEqual(list, [{ language: 'Polish', level: 'NATIVE' }]);
  assert.deepEqual(problems, ['Choose how well you know Danish.']);
  const many = Array.from({ length: MAX_LANGUAGES + 1 }, (_, i) => ({ language: `L${i}`, level: 'GOOD' }));
  assert.match(cleanLanguages(many).problems[0], /up to 10/);
  assert.equal(languagesText(list), 'Polish (native)');
});

test('photos are scaled down to fit, never up', () => {
  assert.deepEqual(fitWithin(4000, 3000, 1080), { width: 1080, height: 810 });
  assert.deepEqual(fitWithin(600, 800, 1080), { width: 600, height: 800 });
  assert.deepEqual(fitWithin(0, 10, 1080), { width: 0, height: 0 });
});

test('the privacy summary says exactly what Amber shows', () => {
  assert.match(visibilitySummary(null)[2].text, /not joined Amber/);
  const amber = { joined: true, visible: true, shareLanguages: true, shareLearning: false, shareHarbor: true, photosVisibility: 'CONNECTED' };
  const text = visibilitySummary(amber)[2].text;
  assert.match(text, /languages you know/);
  assert.match(text, /that you use Harbor/);
  assert.doesNotMatch(text, /Echo/);
  assert.match(text, /only after you both say yes/);
  assert.match(visibilitySummary({ ...amber, visible: false })[2].text, /paused/);
});

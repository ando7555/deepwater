import test from 'node:test';
import assert from 'node:assert/strict';
import { COURSE_LANGUAGES, EXAM_IDS, PRODUCTS, initials, languageExample, languageName, languageOfExam, productName } from './config.js';
import { KEYS, read, readChoice, readJson, write, writeJson } from './storage.js';

test('languages come from one list', () => {
  assert.equal(languageName('da'), 'Danish');
  assert.equal(languageName('de'), 'German');
  assert.equal(languageName('pl'), 'Polish');
  assert.equal(languageName('xx'), 'xx');
  assert.deepEqual(COURSE_LANGUAGES.map(language => language.code), ['da', 'de']);
  assert.equal(languageExample('de'), 'die Katze');
});

test('products have ids, names and hints', () => {
  assert.deepEqual(PRODUCTS.map(product => product.id), ['echo', 'harbor', 'amber']);
  for (const product of PRODUCTS) assert.ok(product.name && product.hint && product.line);
  assert.equal(productName('harbor'), 'Harbor');
});

test('initials', () => {
  assert.equal(initials('Sofia Berg'), 'SB');
  assert.equal(initials('  anna  '), 'A');
  assert.equal(initials(''), '?');
  assert.equal(initials(null), '?');
});

test('storage never throws, even when the browser blocks it', () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, get() { throw new Error('SecurityError'); } });
  try {
    assert.equal(read(KEYS.product, { fallback: 'echo' }), 'echo');
    assert.equal(write(KEYS.product, 'harbor'), false);
  } finally {
    if (original) Object.defineProperty(globalThis, 'localStorage', original); else delete globalThis.localStorage;
  }
});

test('storage round-trips values and rejects unknown choices', () => {
  const memory = new Map();
  globalThis.localStorage = { getItem: key => memory.get(key) ?? null, setItem: (key, value) => memory.set(key, String(value)), removeItem: key => memory.delete(key) };
  try {
    writeJson('k', { a: 1 });
    assert.deepEqual(readJson('k'), { a: 1 });
    memory.set('broken', '{not json');
    assert.equal(readJson('broken', { fallback: 'safe' }), 'safe');
    write(KEYS.product, 'neon');
    assert.equal(readChoice(KEYS.product, PRODUCTS.map(product => product.id), 'echo'), 'echo');
    write(KEYS.product, null);
    assert.equal(memory.has(KEYS.product), false);
  } finally { delete globalThis.localStorage; }
});

test('Danish and German have a course and exam practice; Polish has B1 exam practice only', () => {
  assert.deepEqual(EXAM_IDS, ['danish-pd3', 'german-b1', 'polish-b1']);
  assert.equal(languageOfExam('german-b1').code, 'de');
  assert.equal(languageOfExam('danish-pd3').code, 'da');
  assert.equal(languageOfExam('polish-b1').course, false);
  assert.equal(languageOfExam(null), undefined);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { COURSE_LANGUAGES, PRODUCTS, THEME_IDS, initials, languageExample, languageName, productName } from './config.js';
import { KEYS, read, readChoice, readJson, write, writeJson } from './storage.js';

test('languages come from one list', () => {
  assert.equal(languageName('da'), 'Danish');
  assert.equal(languageName('de'), 'German');
  assert.equal(languageName('pl'), 'Polish');
  assert.equal(languageName('xx'), 'xx');
  assert.deepEqual(COURSE_LANGUAGES.map(language => language.code), ['da', 'de']);
  assert.equal(languageExample('de'), 'die Katze');
});

test('products and themes have ids, names and hints', () => {
  assert.deepEqual(PRODUCTS.map(product => product.id), ['echo', 'harbor', 'amber']);
  for (const product of PRODUCTS) assert.ok(product.name && product.hint && product.line);
  assert.equal(productName('harbor'), 'Harbor');
  assert.deepEqual(THEME_IDS, ['deep', 'amber', 'light']);
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
    assert.equal(read(KEYS.theme, { fallback: 'deep' }), 'deep');
    assert.equal(write(KEYS.theme, 'light'), false);
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
    write(KEYS.theme, 'neon');
    assert.equal(readChoice(KEYS.theme, THEME_IDS, 'deep'), 'deep');
    write(KEYS.theme, null);
    assert.equal(memory.has(KEYS.theme), false);
  } finally { delete globalThis.localStorage; }
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { answeredIn, appendDictation, lengthNote, progress, wordCount } from './examTools.js';

test('words are counted with Polish letters and without punctuation', () => {
  assert.equal(wordCount('Zażółć gęślą jaźń!'), 3);
  assert.equal(wordCount('  Cześć, to ja — Tomek. '), 4);
  assert.equal(wordCount(''), 0);
  assert.equal(wordCount(undefined), 0);
});

test('progress counts only items with a non-blank answer', () => {
  const exam = { parts: [{ items: [{ id: 'g1' }, { id: 'g2' }] }, { items: [{ id: 'w1' }] }] };
  assert.equal(answeredIn(exam.parts[0], { g1: '0', g2: '  ' }), 1);
  assert.deepEqual(progress(exam, { g1: '0', w1: 'Cześć' }), { done: 2, total: 3 });
});

test('the length note compares with the target range', () => {
  const item = { minWords: 3, maxWords: 4 };
  assert.equal(lengthNote(item, 'Ala ma'), '2 words · aim for 3–4');
  assert.equal(lengthNote(item, 'Ala ma kota.'), '3 words · good length');
  assert.equal(lengthNote(item, 'a b c d e f'), '6 words · a bit long, aim for up to 4');
  assert.equal(lengthNote({}, 'a b'), '2 words');
});

test('dictation is appended with one space', () => {
  assert.equal(appendDictation('Mam na imię Ana.', ' mieszkam w Danii '), 'Mam na imię Ana. mieszkam w Danii');
  assert.equal(appendDictation('', 'Cześć'), 'Cześć');
  assert.equal(appendDictation('Cześć', ''), 'Cześć');
});

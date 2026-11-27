import test from 'node:test';
import assert from 'node:assert/strict';
import { addInterest, profileProblems, intentWords, initial, isShared, coverFor, railPosition, turnText, CLOSING_NOTES, COVERS, MAX_INTERESTS } from './amberTools.js';

test('interests are trimmed, deduplicated and limited', () => {
  assert.deepEqual(addInterest([], '  Board   games ').list, ['Board games']);
  assert.deepEqual(addInterest(['Jazz'], 'jazz').list, ['Jazz']);
  assert.match(addInterest([], 'a, b').problem, /without commas/);
  const full = Array.from({ length: MAX_INTERESTS }, (_, i) => `i${i}`);
  assert.equal(addInterest(full, 'one more').list.length, MAX_INTERESTS);
  assert.match(addInterest(full, 'one more').problem, /up to 12/);
});

test('a profile needs an adult who confirms it, an area and an intent', () => {
  const ok = { displayName: 'Anna', age: '29', confirmAdult: true, area: 'Copenhagen', intents: ['DATING'] };
  assert.deepEqual(profileProblems(ok), []);
  assert.ok(profileProblems({ ...ok, age: '17' }).some(p => p.includes('adults')));
  assert.ok(profileProblems({ ...ok, confirmAdult: false }).some(p => p.includes('Confirm')));
  assert.ok(profileProblems({ ...ok, intents: [] }).some(p => p.includes('looking for')));
});

test('small display helpers', () => {
  assert.equal(intentWords(['FRIENDS', 'DATING']), 'Dating, New friends');
  assert.equal(initial(' mia'), 'M');
  assert.equal(isShared('Hiking', ['hiking']), true);
});

test('a person keeps the same cover colours everywhere', () => {
  assert.deepEqual(coverFor('Mia'), coverFor(' mia '));
  assert.ok(COVERS.includes(coverFor('Lukas')));
  assert.ok(COVERS.includes(coverFor('')));
});

test('the card row says which card is in view', () => {
  assert.deepEqual(railPosition(0, 300, 4), { index: 0, label: '1 of 4' });
  assert.deepEqual(railPosition(610, 300, 4), { index: 2, label: '3 of 4' });
  assert.deepEqual(railPosition(9999, 300, 4), { index: 3, label: '4 of 4' });
  assert.deepEqual(railPosition(0, 0, 0), { index: 0, label: '' });
});

test('a missing age asks for the birth date', () => {
  const form = { displayName: 'Anna', age: null, confirmAdult: true, area: 'Aarhus', intents: ['DATING'] };
  assert.ok(profileProblems(form).some(p => p.includes('birth date')));
});

test('the conversation line says whose turn it is, and nudges after three quiet days', () => {
  const now = new Date('2026-11-27T12:00:00Z');
  const person = { displayName: 'Mia' };
  assert.equal(turnText({ person, lastMessageMine: null }, now), 'New: say hello');
  assert.equal(turnText({ person, lastMessageMine: true, lastMessageAt: '2026-11-20T12:00:00Z' }, now), 'Waiting for Mia');
  assert.equal(turnText({ person, lastMessageMine: false, lastMessageAt: '2026-11-26T12:00:00Z' }, now), 'Your turn');
  assert.equal(turnText({ person, lastMessageMine: false, lastMessageAt: '2026-11-23T12:00:00Z' }, now), 'Your turn: reply, or close kindly');
  assert.equal(CLOSING_NOTES.length, 3);
});

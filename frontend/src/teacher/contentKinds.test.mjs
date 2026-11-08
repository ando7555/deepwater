import test from 'node:test';
import assert from 'node:assert/strict';
import { CONTENT_KINDS, KIND_IDS, kindById, kindOfLesson, pick } from './contentKinds.js';

test('every kind has a label, a line and valid exercise templates', () => {
  for (const kind of CONTENT_KINDS) {
    assert.ok(kind.label && kind.line, kind.id);
    for (const exercise of kind.exercises) assert.ok(['SELECT', 'TEXT', 'TRANSFER'].includes(exercise.taskType), `${kind.id} ${exercise.taskType}`);
  }
  assert.deepEqual(KIND_IDS, ['grammar', 'writing', 'vocabulary', 'lyrics', 'conversation', 'listening']);
});

test('pick returns the text for the learner language and falls back to Danish', () => {
  assert.equal(pick({ da: 'ikke', de: 'nicht' }, 'de'), 'nicht');
  assert.equal(pick({ da: 'ikke', de: 'nicht' }, 'da'), 'ikke');
  assert.equal(pick({ da: 'ikke' }, 'de'), 'ikke');
  assert.equal(pick('plain', 'de'), 'plain');
  assert.equal(pick(undefined, 'da'), '');
});

test('the kind of an existing lesson is read back from its skill key or audio', () => {
  assert.equal(kindOfLesson({ skillKey: 'danish.lyrics.song-lyrics.kim-larsen' }), 'lyrics');
  assert.equal(kindOfLesson({ skillKey: 'danish.nouns.en-or-et' }), '');
  assert.equal(kindOfLesson({ skillKey: 'x', audio: { mediaType: 'MUSIC' } }), 'listening');
  assert.equal(kindOfLesson({ skillKey: 'danish.conversation.deposit' }), 'conversation');
});

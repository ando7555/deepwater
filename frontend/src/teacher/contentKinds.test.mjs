import test from 'node:test';
import assert from 'node:assert/strict';
import { CONTENT_KINDS, KIND_IDS, kindById, kindOfLesson, pick } from './contentKinds.js';
import { lessonForKind, lessonProblems, suggestSkillKey, toDraftInput, fromLesson } from './lessonDraft.js';

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

test('a lesson started from a kind has its topic, kind and example prompts, and nothing saved from the placeholders', () => {
  const form = lessonForKind(kindById('grammar'), 'de');
  assert.equal(form.kind, 'grammar');
  assert.equal(form.topic, 'Grammar');
  assert.equal(form.exercises.length, 3);
  assert.match(pick(form.exercises[0].placeholder, 'de'), /nicht/);
  assert.match(pick(form.exercises[0].placeholder, 'da'), /ikke/);
  assert.equal(form.exercises[0].prompt, '');
  form.title = 'Negation with nicht'; form.objective = 'Say no'; form.explanation = 'nicht at the end'; form.sourceReference = 'own';
  form.exercises = form.exercises.map(exercise => ({ ...exercise, prompt: 'p', expectedAnswer: exercise.taskType === 'SELECT' ? 'A' : exercise.taskType === 'TEXT' ? 'nicht' : '', options: exercise.taskType === 'SELECT' ? [{ key: 'A', text: 'a' }, { key: 'B', text: 'b' }] : [] }));
  assert.deepEqual(lessonProblems(form), []);
  const input = toDraftInput(form);
  assert.equal(input.skillKey, 'german.grammar.negation-with-nicht');
  assert.equal(JSON.stringify(input).includes('placeholder'), false);
});

test('the kind of an existing lesson is read back from its skill key or audio', () => {
  assert.equal(kindOfLesson({ skillKey: 'danish.lyrics.song-lyrics.kim-larsen' }), 'lyrics');
  assert.equal(kindOfLesson({ skillKey: 'danish.nouns.en-or-et' }), '');
  assert.equal(kindOfLesson({ skillKey: 'x', audio: { mediaType: 'MUSIC' } }), 'listening');
  assert.equal(fromLesson({ skillKey: 'danish.conversation.deposit', exercises: [] }).kind, 'conversation');
  assert.equal(suggestSkillKey({ languageCode: 'da', kind: 'writing', topic: 'Writing', title: 'A message to a neighbour' }), 'danish.writing.a-message-to-a');
});

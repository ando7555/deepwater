import { test } from 'node:test';
import assert from 'node:assert/strict';
import { emptyLesson, emptyExercise, withTaskType, levelOf, splitLevel, suggestSkillKey, lessonProblems, toDraftInput, fromLesson, parseLessonJson, lessonToJson } from './lessonDraft.js';

function filledLesson() {
  const form = emptyLesson('da');
  Object.assign(form, { title: 'en or et', stage: 'First words', topic: 'Nouns', objective: 'Use en and et.', explanation: 'Most nouns take en.', sourceReference: 'Deepwater original (2026-11-02)' });
  form.exercises[0].prompt = 'Which is right?';
  form.exercises[0].options[0].text = 'en kop';
  form.exercises[0].options[1].text = 'et kop';
  form.exercises[0].feedbackIncorrect = 'Kop is an en-word.';
  form.exercises[1].prompt = 'Write the article: ___ æble';
  form.exercises[1].expectedAnswer = 'et';
  form.exercises[1].acceptedAnswers = 'et æble\n\n  ';
  form.exercises[2].prompt = 'Write three things you can see.';
  return form;
}

test('a new lesson lists what is missing', () => {
  const problems = lessonProblems(emptyLesson());
  assert.ok(problems.includes('The lesson title is missing.'));
  assert.ok(problems.includes('Exercise 1 needs a prompt.'));
  assert.ok(problems.includes('Exercise 1: every option needs text.'));
  assert.ok(problems.includes('Exercise 2 needs the expected answer.'));
});

test('a filled lesson has no problems and becomes a LessonDraftInput', () => {
  const form = filledLesson();
  assert.deepEqual(lessonProblems(form), []);
  const input = toDraftInput(form);
  assert.equal(input.level, 'A1 · first words');
  assert.equal(input.skillKey, 'danish.nouns.en-or-et');
  assert.equal(input.revisionOf, undefined);
  assert.deepEqual(input.exercises[0], { taskType: 'SELECT', prompt: 'Which is right?', expectedAnswer: 'A', options: [{ key: 'A', text: 'en kop' }, { key: 'B', text: 'et kop' }], feedbackIncorrect: 'Kop is an en-word.' });
  assert.deepEqual(input.exercises[1], { taskType: 'TEXT', prompt: 'Write the article: ___ æble', expectedAnswer: 'et', acceptedAnswers: ['et æble'] });
  assert.deepEqual(input.exercises[2], { taskType: 'TRANSFER', prompt: 'Write three things you can see.' });
});

test('a choice exercise must have its correct option ticked', () => {
  const form = filledLesson();
  form.exercises[0].expectedAnswer = 'Z';
  assert.ok(lessonProblems(form).includes('Exercise 1: tick the correct option.'));
});

test('typed exercises take at most ten extra answers', () => {
  const form = filledLesson();
  form.exercises[1].acceptedAnswers = Array.from({ length: 11 }, (_, i) => `answer ${i}`).join('\n');
  assert.ok(lessonProblems(form).includes('Exercise 2: at most 10 extra accepted answers.'));
});

test('changing the kind keeps the prompt and resets the answers', () => {
  const exercise = { ...emptyExercise('SELECT'), prompt: 'Keep me', expectedAnswer: 'B' };
  const typed = withTaskType(exercise, 'TEXT');
  assert.equal(typed.prompt, 'Keep me');
  assert.equal(typed.expectedAnswer, '');
  assert.deepEqual(typed.options, []);
  assert.equal(typed.uid, exercise.uid);
});

test('levels round-trip between band + stage and the course-path form', () => {
  assert.equal(levelOf({ band: 'A2', stage: 'Getting Around' }), 'A2 · getting around');
  assert.equal(levelOf({ band: 'B1', stage: '' }), 'B1');
  assert.deepEqual(splitLevel('A1 · first words'), { band: 'A1', stage: 'First words' });
  assert.deepEqual(splitLevel('Beginner'), { band: 'A1', stage: '' });
});

test('skill keys are ascii slugs with the language first', () => {
  assert.equal(suggestSkillKey({ languageCode: 'de', topic: 'Sätze bauen', title: 'Zeit zuerst, dann das Verb und mehr' }), 'german.saetze-bauen.zeit-zuerst-dann-das');
});

test('an existing lesson loads back into the form, as a revision when asked', () => {
  const lesson = {
    id: 'lesson-1', languageCode: 'da', title: 'Hello', level: 'A1 · first words', topic: 'Greetings', objective: 'Say hi', explanation: 'Hej', sourceReference: 'Own', skillKey: 'danish.hello',
    exercises: [
      { taskType: 'SELECT', prompt: 'Pick', expectedAnswer: 'B', acceptedAnswers: [], feedbackCorrect: 'Yes', feedbackIncorrect: '', options: [{ key: 'A', text: 'Farvel' }, { key: 'B', text: 'Hej' }] },
      { taskType: 'TEXT', prompt: 'Type', expectedAnswer: 'hej', acceptedAnswers: ['hej hej', 'hejsa'], feedbackCorrect: '', feedbackIncorrect: 'Hint', options: [] },
      { taskType: 'TRANSFER', prompt: 'Own', expectedAnswer: null, acceptedAnswers: [], feedbackCorrect: '', feedbackIncorrect: '', options: [] },
    ],
  };
  const form = fromLesson(lesson, { revisionOf: lesson.id });
  assert.equal(form.revisionOf, 'lesson-1');
  assert.equal(form.stage, 'First words');
  assert.equal(form.exercises[0].expectedAnswer, 'B');
  assert.equal(form.exercises[1].acceptedAnswers, 'hej hej\nhejsa');
  assert.deepEqual(lessonProblems(form), []);
  assert.equal(toDraftInput(form).revisionOf, 'lesson-1');
  assert.equal(toDraftInput(fromLesson(lesson)).revisionOf, undefined);
});

test('lesson JSON imports and exports without losing fields', () => {
  const json = lessonToJson(filledLesson());
  const form = parseLessonJson(json);
  assert.equal(lessonToJson(form), json);
  assert.throws(() => parseLessonJson('{ not json'), /not valid JSON/);
  assert.throws(() => parseLessonJson('{"title":"x"}'), /"exercises" list/);
});

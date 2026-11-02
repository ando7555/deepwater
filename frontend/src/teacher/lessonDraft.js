/*
 * The lesson builder's data model: plain functions with no React in them, so the rules can be
 * unit-tested and reused (for example by a future import tool). The shape they produce is the
 * GraphQL LessonDraftInput that the backend validates again on save.
 */

export const TASK_TYPES = [
  { id: 'SELECT', label: 'Choose one', line: 'The learner picks the right option. Graded automatically.' },
  { id: 'TEXT', label: 'Type the answer', line: 'The learner types a word or phrase. Graded against the answers you accept.' },
  { id: 'TRANSFER', label: 'Own example', line: 'The learner writes a new sentence of their own. Saved for reflection, not graded.' },
];

export const BANDS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

/** Stage names already used on the Danish course path; a teacher can type any other. */
export const STAGE_SUGGESTIONS = ['First words', 'First sentences', 'Getting around', 'Small talk'];

const OPTION_KEYS = 'ABCDEFGHIJ';
const LIMITS = { title: 240, topic: 120, objective: 500, explanation: 3000, sourceReference: 500, skillKey: 120, prompt: 1000, answer: 500, optionText: 500 };
let uidCounter = 0;

function uid() {
  uidCounter += 1;
  return `x${uidCounter}`;
}

export function emptyOption(index) {
  return { key: OPTION_KEYS[index] ?? String(index + 1), text: '' };
}

/** One exercise in the form. acceptedAnswers is kept as text, one answer per line. */
export function emptyExercise(taskType = 'SELECT') {
  return {
    uid: uid(), taskType, prompt: '',
    expectedAnswer: taskType === 'SELECT' ? 'A' : '',
    acceptedAnswers: '', feedbackCorrect: '', feedbackIncorrect: '',
    options: taskType === 'SELECT' ? [emptyOption(0), emptyOption(1)] : [],
  };
}

/** A new lesson starts with one exercise of each kind, in the order a lesson usually runs. */
export function emptyLesson(languageCode = 'da') {
  return {
    revisionOf: '', languageCode, title: '', band: 'A1', stage: '', topic: '', objective: '',
    explanation: '', sourceReference: '', skillKey: '',
    exercises: [emptyExercise('SELECT'), emptyExercise('TEXT'), emptyExercise('TRANSFER')],
  };
}

/** Switches an exercise's kind, keeping the prompt and feedback but resetting the answer fields. */
export function withTaskType(exercise, taskType) {
  if (exercise.taskType === taskType) return exercise;
  const fresh = emptyExercise(taskType);
  return { ...fresh, uid: exercise.uid, prompt: exercise.prompt, feedbackCorrect: exercise.feedbackCorrect, feedbackIncorrect: exercise.feedbackIncorrect };
}

/** "A1" + "First words" → "A1 · first words", the form the course path groups by. */
export function levelOf(form) {
  const stage = (form.stage || '').trim();
  return stage ? `${form.band} · ${stage.toLowerCase()}` : form.band;
}

/** "A1 · first words" → { band: "A1", stage: "First words" }. Unknown bands fall back to A1. */
export function splitLevel(level) {
  const [band = '', stage = ''] = (level || '').split('·').map(part => part.trim());
  const known = BANDS.find(candidate => candidate.toLowerCase() === band.toLowerCase());
  return { band: known || 'A1', stage: stage ? stage[0].toUpperCase() + stage.slice(1) : '' };
}

export function slug(text) {
  const ascii = { æ: 'ae', ø: 'oe', å: 'aa', ä: 'ae', ö: 'oe', ü: 'ue', ß: 'ss' };
  return (text || '').toLowerCase().replace(/[æøåäöüß]/g, letter => ascii[letter])
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

/** A skill key from the language, topic and title, e.g. "danish.nouns.en-or-et". */
export function suggestSkillKey(form) {
  const language = form.languageCode === 'de' ? 'german' : 'danish';
  const parts = [language, slug(form.topic), slug(form.title).split('-').slice(0, 4).join('-')].filter(Boolean);
  return parts.join('.').slice(0, LIMITS.skillKey);
}

function acceptedList(text) {
  return (text || '').split('\n').map(line => line.trim()).filter(Boolean);
}

function tooLong(value, limit) {
  return (value || '').trim().length > limit;
}

/** Plain-language problems the teacher must fix before saving; mirrors the backend's checks. */
export function lessonProblems(form) {
  const problems = [];
  const need = (value, limit, name) => {
    if (!(value || '').trim()) problems.push(`${name} is missing.`);
    else if (tooLong(value, limit)) problems.push(`${name} is longer than ${limit} characters.`);
  };
  need(form.title, LIMITS.title, 'The lesson title');
  need(form.topic, LIMITS.topic, 'The topic');
  need(form.objective, LIMITS.objective, 'What the learner will be able to do');
  need(form.explanation, LIMITS.explanation, 'The explanation');
  need(form.sourceReference, LIMITS.sourceReference, 'The source and permission note');
  if (tooLong(form.skillKey, LIMITS.skillKey)) problems.push(`The skill key is longer than ${LIMITS.skillKey} characters.`);
  if (!form.exercises.length) problems.push('Add at least one exercise.');
  if (form.exercises.length > 20) problems.push('A lesson can have at most 20 exercises.');
  form.exercises.forEach((exercise, index) => {
    const label = `Exercise ${index + 1}`;
    if (!exercise.prompt.trim()) problems.push(`${label} needs a prompt.`);
    else if (tooLong(exercise.prompt, LIMITS.prompt)) problems.push(`${label}: the prompt is longer than ${LIMITS.prompt} characters.`);
    if (exercise.taskType === 'SELECT') {
      if (exercise.options.length < 2 || exercise.options.length > 10) problems.push(`${label} needs between 2 and 10 options.`);
      if (exercise.options.some(option => !option.text.trim())) problems.push(`${label}: every option needs text.`);
      if (exercise.options.some(option => tooLong(option.text, LIMITS.optionText))) problems.push(`${label}: an option is longer than ${LIMITS.optionText} characters.`);
      if (!exercise.options.some(option => option.key === exercise.expectedAnswer)) problems.push(`${label}: tick the correct option.`);
    }
    if (exercise.taskType === 'TEXT') {
      if (!exercise.expectedAnswer.trim()) problems.push(`${label} needs the expected answer.`);
      else if (tooLong(exercise.expectedAnswer, LIMITS.answer)) problems.push(`${label}: the expected answer is longer than ${LIMITS.answer} characters.`);
      const accepted = acceptedList(exercise.acceptedAnswers);
      if (accepted.length > 10) problems.push(`${label}: at most 10 extra accepted answers.`);
      if (accepted.some(answer => answer.length > LIMITS.answer)) problems.push(`${label}: an accepted answer is longer than ${LIMITS.answer} characters.`);
    }
  });
  return problems;
}

/** The GraphQL LessonDraftInput for a form. Empty optional fields are left out. */
export function toDraftInput(form) {
  const text = value => (value || '').trim();
  const feedback = exercise => ({
    ...(text(exercise.feedbackCorrect) ? { feedbackCorrect: text(exercise.feedbackCorrect) } : {}),
    ...(text(exercise.feedbackIncorrect) ? { feedbackIncorrect: text(exercise.feedbackIncorrect) } : {}),
  });
  return {
    ...(text(form.revisionOf) ? { revisionOf: text(form.revisionOf) } : {}),
    languageCode: form.languageCode,
    title: text(form.title),
    level: levelOf(form),
    topic: text(form.topic),
    objective: text(form.objective),
    explanation: text(form.explanation),
    sourceReference: text(form.sourceReference),
    skillKey: text(form.skillKey) || suggestSkillKey(form),
    exercises: form.exercises.map(exercise => {
      const base = { taskType: exercise.taskType, prompt: text(exercise.prompt) };
      if (exercise.taskType === 'TRANSFER') return { ...base, ...feedback(exercise) };
      if (exercise.taskType === 'SELECT') {
        return { ...base, expectedAnswer: exercise.expectedAnswer, options: exercise.options.map(option => ({ key: option.key, text: text(option.text) })), ...feedback(exercise) };
      }
      const accepted = acceptedList(exercise.acceptedAnswers);
      return { ...base, expectedAnswer: text(exercise.expectedAnswer), ...(accepted.length ? { acceptedAnswers: accepted } : {}), ...feedback(exercise) };
    }),
  };
}

/** Form state for an existing lesson or a LessonDraftInput; `revisionOf` marks it as a revision of that lesson. */
export function fromLesson(lesson, { revisionOf = '' } = {}) {
  const { band, stage } = splitLevel(lesson.level);
  return {
    revisionOf, languageCode: lesson.languageCode === 'de' ? 'de' : 'da',
    title: lesson.title || '', band, stage, topic: lesson.topic || '', objective: lesson.objective || '',
    explanation: lesson.explanation || '', sourceReference: lesson.sourceReference || '', skillKey: lesson.skillKey || '',
    exercises: (lesson.exercises || []).map(exercise => {
      const taskType = TASK_TYPES.some(type => type.id === exercise.taskType) ? exercise.taskType : 'TEXT';
      const options = taskType === 'SELECT' ? (exercise.options || []).map((option, index) => ({ key: option.key || OPTION_KEYS[index], text: option.text || '' })) : [];
      return {
        uid: uid(), taskType, prompt: exercise.prompt || '',
        expectedAnswer: taskType === 'TRANSFER' ? '' : (exercise.expectedAnswer || (taskType === 'SELECT' ? options[0]?.key || 'A' : '')),
        acceptedAnswers: (exercise.acceptedAnswers || []).join('\n'),
        feedbackCorrect: exercise.feedbackCorrect || '', feedbackIncorrect: exercise.feedbackIncorrect || '',
        options: options.length ? options : (taskType === 'SELECT' ? [emptyOption(0), emptyOption(1)] : []),
      };
    }),
  };
}

/** Reads lesson JSON in the TEACHER_GUIDE shape into the form. Throws a readable error for bad input. */
export function parseLessonJson(text) {
  let parsed;
  try { parsed = JSON.parse(text); } catch { throw new Error('That is not valid JSON. Check for a missing comma or quote.'); }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('The JSON must be one lesson object.');
  if (!Array.isArray(parsed.exercises)) throw new Error('The lesson needs an "exercises" list.');
  return fromLesson(parsed, { revisionOf: parsed.revisionOf || '' });
}

export function lessonToJson(form) {
  return JSON.stringify(toDraftInput(form), null, 2);
}

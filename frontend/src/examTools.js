// Plain helpers for the Polish B1 practice exam screen (no React), covered by examTools.test.mjs.

/** Words in a written or spoken answer, counted the way the backend rubric counts them (letters only). */
export function wordCount(text) {
  return (String(text ?? '').match(/\p{L}+/gu) || []).length;
}

/** How many items of a part have an answer. */
export function answeredIn(part, answers) {
  return part.items.filter(item => String(answers[item.id] ?? '').trim() !== '').length;
}

/** Total answered items and total items across the exam. */
export function progress(exam, answers) {
  const total = exam.parts.reduce((sum, part) => sum + part.items.length, 0);
  const done = exam.parts.reduce((sum, part) => sum + answeredIn(part, answers), 0);
  return { done, total };
}

/** A short note under a text box: how the length compares with the target range. */
export function lengthNote(item, text) {
  const count = wordCount(text);
  if (!item.minWords) return `${count} words`;
  if (count < item.minWords) return `${count} words · aim for ${item.minWords}–${item.maxWords}`;
  if (count > item.maxWords * 1.3) return `${count} words · a bit long, aim for up to ${item.maxWords}`;
  return `${count} words · good length`;
}

/** Appends a new piece of dictated speech to what is already in the box. */
export function appendDictation(current, spoken) {
  const before = String(current ?? '').trim();
  const added = String(spoken ?? '').trim();
  if (!added) return before;
  return before ? `${before} ${added}` : added;
}

export const LEVEL_TEXT = {
  'B1+': 'Strong B1',
  B1: 'B1',
  A2: 'A2, getting close to B1',
  'A1-A2': 'A1 to A2',
};

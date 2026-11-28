// Plain helpers for the exam practice screen (no React), covered by examTools.test.mjs.

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

/**
 * The level estimate in words, for an exam that tests `target`:
 * "B1+" -> "Strong B1", "A2" for a B1 exam -> "A2, getting close to B1", "A2-B1" -> "A2 to B1".
 */
export function levelText(estimate, target) {
  if (!estimate) return '';
  if (estimate.endsWith('+')) return `Strong ${estimate.slice(0, -1)}`;
  if (estimate.includes('-')) return estimate.replace('-', ' to ');
  if (target && estimate !== target) return `${estimate}, getting close to ${target}`;
  return estimate;
}

/** "100 PLN" / "1,249 DKK" / "€25": a whole monthly price in its currency. */
export function priceText(amount, currency) {
  const number = Number(amount).toLocaleString('en-US');
  return currency === 'EUR' ? `€${number}` : `${number} ${currency}`;
}

/** The two lines under an offer: the launch price and what it becomes later. */
export function offerLines(offer, launchBuyers) {
  return {
    now: `${priceText(offer.launchPrice, offer.currency)} / month`,
    note: `Launch price for the first ${launchBuyers} learners, who keep it. Later ${priceText(offer.fullPrice, offer.currency)} / month.`,
  };
}

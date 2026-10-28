// Mirrors the backend card schedule (EchoCardService.BOX_INTERVAL_DAYS).
export const BOX_INTERVAL_DAYS = [0, 1, 3, 7, 14, 30];
const DAY = 24 * 60 * 60 * 1000;

/**
 * Estimated chance of recalling a card now, from a simple forgetting curve:
 * R = (1 + t / (9 S))^-1, where S is the card's current interval (stability) and t the time since
 * its last review. R is 90% when the card falls due. An estimate for motivation, not a measurement.
 */
export function memoryStrength(card, now = Date.now()) {
  const stabilityDays = Math.max(BOX_INTERVAL_DAYS[card.box] || 0, 0.5);
  const lastReview = new Date(card.nextReviewAt).getTime() - (BOX_INTERVAL_DAYS[card.box] || 0) * DAY;
  const elapsedDays = Math.max(0, (now - lastReview) / DAY);
  return 1 / (1 + elapsedDays / (9 * stabilityDays));
}

/** Lower case, no punctuation, single spaces: forgiving on typing, strict on the letters æ, ø, å, ä, ö, ü and ß. */
export function normaliseAnswer(text) {
  return (text || '').toLocaleLowerCase().replace(/[.,!?;:"“”„'’()\-–]/g, ' ').replace(/\s+/g, ' ').trim();
}

/** Number of single-letter edits between two strings (Levenshtein), for "almost" feedback. */
export function editDistance(a, b) {
  const row = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let previous = row[0]; row[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const current = row[j];
      row[j] = Math.min(row[j] + 1, row[j - 1] + 1, previous + (a[i - 1] === b[j - 1] ? 0 : 1));
      previous = current;
    }
  }
  return row[b.length];
}

/** Compares a typed recall with the card: exact, close (one or two letters off), or not yet. */
export function checkRecall(typed, expected) {
  const a = normaliseAnswer(typed), b = normaliseAnswer(expected);
  if (a === b) return 'exact';
  return a && editDistance(a, b) <= Math.max(1, Math.floor(b.length / 12)) ? 'close' : 'miss';
}

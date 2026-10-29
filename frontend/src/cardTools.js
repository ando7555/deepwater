// Small pure helpers for phrase cards: pasted lists, noun articles and sentence tiles.

export const SPECIAL_LETTERS = { da: ['æ', 'ø', 'å'], de: ['ä', 'ö', 'ü', 'ß'] };

const ARTICLES = { da: ['en', 'et'], de: ['der', 'die', 'das'] };

/** The article a noun card starts with ("en kat" → "en"), so Echo can show and say it with the word. */
export function articleOf(front, languageCode) {
  const first = (front || '').trim().split(/\s+/)[0]?.toLocaleLowerCase();
  return (ARTICLES[languageCode] || []).includes(first) && front.trim().includes(' ') ? first : null;
}

const SEPARATORS = ['\t', ';', ' – ', ' — ', ' - ', ' = ', '='];

/**
 * Turns pasted lines into card pairs. Each line is "phrase<separator>meaning"; tab, semicolon, a dash
 * with spaces or "=" all work. Returns the pairs plus the lines that could not be read, by line number.
 */
export function parseCardList(text, swap = false) {
  const pairs = [], problems = [];
  (text || '').split(/\r?\n/).forEach((raw, index) => {
    const line = raw.trim();
    if (!line) return;
    const separator = SEPARATORS.find(item => line.includes(item));
    const [left, ...rest] = separator ? line.split(separator) : [line];
    const front = (swap ? rest.join(separator) : left).trim(), back = (swap ? left : rest.join(separator)).trim();
    if (!front || !back) problems.push({ line: index + 1, text: line, reason: 'needs a phrase and its meaning' });
    else if (front.length > 300 || back.length > 300) problems.push({ line: index + 1, text: line, reason: 'is longer than 300 characters' });
    else pairs.push({ front, back });
  });
  return { pairs, problems };
}

/** Words of a phrase in a shuffled order that is never the original one (when that is possible). */
export function shuffledTiles(front, random = Math.random) {
  const words = (front || '').trim().split(/\s+/).filter(Boolean);
  const tiles = words.map((word, index) => ({ word, index }));
  for (let attempt = 0; attempt < 8; attempt++) {
    for (let i = tiles.length - 1; i > 0; i--) { const j = Math.floor(random() * (i + 1)); [tiles[i], tiles[j]] = [tiles[j], tiles[i]]; }
    if (tiles.some((tile, position) => tile.index !== position)) break;
  }
  return tiles;
}

/** True when a card has enough words to be rebuilt from tiles. */
export const canBuild = card => (card?.front || '').trim().split(/\s+/).length >= 3;

/** Plain words for what a review just did to the schedule, so nothing changes silently. */
export function scheduleNote(grade, card) {
  if (grade === 'CLOSE') return 'Close counts. Same step, back tomorrow for one more look.';
  if (grade === 'MISSED') return card.box === 0 ? 'Back for another look today. Misses never wipe out progress.' : 'One step back, not to the start. It comes back sooner.';
  const days = [0, 1, 3, 7, 14, 30][card.box] ?? 30;
  return days ? `One step deeper. Next look in ${days} day${days === 1 ? '' : 's'}.` : 'Saved.';
}

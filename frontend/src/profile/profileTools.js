// Plain rules for the Deepwater profile screens, kept out of the components so node:test can check them.

export const LEVELS = [
  { id: 'NATIVE', label: 'Native' },
  { id: 'FLUENT', label: 'Fluent' },
  { id: 'GOOD', label: 'Good' },
  { id: 'BASIC', label: 'Basic' },
];
export const MAX_LANGUAGES = 10;
export const MAX_PHOTOS = 4;
export const COMMON_LANGUAGES = ['English', 'Danish', 'German', 'Polish', 'Armenian', 'Arabic', 'Ukrainian', 'Romanian', 'Spanish',
  'Portuguese', 'Turkish', 'Persian', 'Russian', 'Hindi', 'Chinese', 'French', 'Italian', 'Swedish', 'Norwegian'];

export function levelLabel(id) {
  return LEVELS.find(level => level.id === id)?.label ?? id;
}

/** "Polish (native) · English (fluent)" */
export function languagesText(languages = []) {
  return languages.map(item => `${item.language} (${levelLabel(item.level).toLowerCase()})`).join(' · ');
}

/** Whole years on `today` for an ISO birth date, or null. Mirrors PersonProfileService.ageOn. */
export function ageFrom(birthDate, today = new Date()) {
  if (!birthDate || !/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) return null;
  const [year, month, day] = birthDate.split('-').map(Number);
  let age = today.getUTCFullYear() - year;
  const beforeBirthday = today.getUTCMonth() + 1 < month || (today.getUTCMonth() + 1 === month && today.getUTCDate() < day);
  if (beforeBirthday) age -= 1;
  return age;
}

/** Drops empty rows and case-insensitive duplicates; returns { list, problems }. Mirrors the server's checks. */
export function cleanLanguages(rows = []) {
  const seen = new Set();
  const list = [];
  const problems = [];
  for (const row of rows) {
    const language = (row.language ?? '').trim().replace(/\s+/g, ' ');
    if (!language) continue;
    if (language.length > 40) { problems.push('Keep each language name under 40 characters.'); continue; }
    if (!LEVELS.some(level => level.id === row.level)) { problems.push(`Choose how well you know ${language}.`); continue; }
    const key = language.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    list.push({ language, level: row.level });
  }
  if (list.length > MAX_LANGUAGES) problems.push(`List up to ${MAX_LANGUAGES} languages.`);
  return { list, problems };
}

/** The size that fits inside max × max without enlarging, keeping the shape. */
export function fitWithin(width, height, max) {
  if (!width || !height) return { width: 0, height: 0 };
  const scale = Math.min(1, max / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/** One line per product: who can see what from the Deepwater profile. `amber` is the Amber profile, or null. */
export function visibilitySummary(amber) {
  const lines = [
    { product: 'Echo', text: 'Shows nothing to other people. Your lessons, answers and cards are only yours.' },
    { product: 'Harbor', text: 'Shows nothing to other people or to employers. Harbor never contacts anyone for you.' },
  ];
  if (!amber?.joined) {
    lines.push({ product: 'Amber', text: 'You have not joined Amber, so nobody sees your profile there.' });
    return lines;
  }
  const shown = ['first name', 'age', 'city', 'bio', 'interests'];
  if (amber.shareLanguages) shown.push('languages you know');
  if (amber.shareLearning) shown.push('what you learn in Echo, with levels');
  if (amber.shareHarbor) shown.push('that you use Harbor');
  const photos = amber.photosVisibility === 'MEMBERS' ? 'photos to everyone who sees your profile' : 'photos only after you both say yes';
  lines.push({
    product: 'Amber',
    text: amber.visible ? `Members see your ${joinWords(shown)}, and ${photos}.` : 'Your Amber profile is paused, so nobody sees it right now.',
  });
  return lines;
}

function joinWords(words) {
  return words.length < 2 ? words.join('') : `${words.slice(0, -1).join(', ')} and ${words.at(-1)}`;
}

/*
 * What the app offers, as data. Screens read these lists instead of spelling names, hints and
 * languages inline, so adding a product, theme or language is a change here, not in five places.
 */

export const PRODUCTS = [
  { id: 'echo', name: 'Echo', hint: 'Languages', tag: 'Danish, German or Polish',
    line: 'Short lessons that explain the pattern, reviews just before you would forget, and Polish B1 state-exam practice with an examiner that says if you look ready.' },
  { id: 'harbor', name: 'Harbor', hint: 'Jobs', tag: 'Find a job in Denmark',
    line: 'Upload your CV, see matched jobs in Denmark and the Nordics, with visa and language filters.' },
  { id: 'amber', name: 'Amber', hint: 'Connections', tag: 'Meet people, by choice',
    line: 'Dating and new friends. Every suggestion says why it appears, and chat opens only when you both say yes.' },
];

/** Shown on the welcome page only; it cannot be opened yet. */
export const COMING_SOON = [
  { id: 'fashion', name: 'AI Fashion', tag: 'Coming soon', line: 'How you look and how others see you, with an AI stylist. We are building it next.' },
];

export const PRODUCT_IDS = PRODUCTS.map(product => product.id);

export function productName(id) {
  return PRODUCTS.find(product => product.id === id)?.name ?? '';
}

/**
 * Echo's languages. `course` languages have lessons and cards; Polish is B1 state-exam practice.
 * `example` is the placeholder shown when a learner adds a card.
 */
export const ECHO_LANGUAGES = [
  { code: 'da', name: 'Danish', course: true, example: 'en kat' },
  { code: 'de', name: 'German', course: true, example: 'die Katze' },
  { code: 'pl', name: 'Polish', hint: 'B1 exam', course: false },
];
export const COURSE_LANGUAGES = ECHO_LANGUAGES.filter(language => language.course);

export function languageName(code) {
  return ECHO_LANGUAGES.find(language => language.code === code)?.name ?? code ?? '';
}

export function languageExample(code) {
  return ECHO_LANGUAGES.find(language => language.code === code)?.example ?? '';
}

/** "Sofia Berg" -> "SB"; empty names give "?". */
export function initials(name) {
  const letters = (name || '').split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0].toUpperCase()).join('');
  return letters || '?';
}

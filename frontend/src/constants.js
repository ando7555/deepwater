import { COURSE_LANGUAGES, languageName } from './app/config.js';

/** Values shared by learner and teacher screens. */
export const LANGUAGES = COURSE_LANGUAGES.map(({ code, name }) => ({ code, name }));
export { languageName };

/** Editorial bands for audio lessons: curation guidance, not a formal CEFR placement. */
export const BAND_LABELS = { A0_A1: 'A0–A1 · Beginner', A2: 'A2 · Elementary', B1_B2: 'B1–B2 · Intermediate', C1_C2: 'C1–C2 · Advanced' };

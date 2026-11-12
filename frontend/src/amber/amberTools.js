// Plain rules for the Amber screens, kept out of the components so node:test can check them.

export const INTENTS = [
  { id: 'DATING', label: 'Dating' },
  { id: 'FRIENDS', label: 'New friends' },
];

export const REPORT_REASONS = [
  { id: 'UNWANTED_MESSAGES', label: 'Unwanted or offensive messages' },
  { id: 'FAKE_PROFILE', label: 'Fake profile' },
  { id: 'FEELS_UNSAFE', label: 'Feels unsafe' },
  { id: 'UNDERAGE', label: 'May be under 18' },
  { id: 'OTHER', label: 'Something else' },
];

export const MAX_INTERESTS = 12;

export function intentWords(ids = []) {
  return INTENTS.filter(intent => ids.includes(intent.id)).map(intent => intent.label).join(', ');
}

/** Adds one interest typed by the person. Returns { list, problem }; the list is unchanged when there is a problem. */
export function addInterest(list, typed) {
  const clean = (typed ?? '').trim().replace(/\s+/g, ' ');
  if (!clean) return { list, problem: '' };
  if (clean.includes(',')) return { list, problem: 'Add interests one at a time, without commas.' };
  if (clean.length > 30) return { list, problem: 'Keep each interest under 30 characters.' };
  if (list.some(item => item.toLowerCase() === clean.toLowerCase())) return { list, problem: '' };
  if (list.length >= MAX_INTERESTS) return { list, problem: `Choose up to ${MAX_INTERESTS} interests.` };
  return { list: [...list, clean], problem: '' };
}

/** What still blocks saving the profile, in plain words. Mirrors the server's checks. */
export function profileProblems(form) {
  const problems = [];
  if (!form.displayName?.trim()) problems.push('Add the first name others will see.');
  const age = Number(form.age);
  if (!Number.isInteger(age) || age < 18) problems.push('Amber is only for adults (18 or older).');
  if (!form.confirmAdult) problems.push('Confirm that you are 18 or older.');
  if (!form.area?.trim()) problems.push('Choose your area, at city level.');
  if (!form.intents?.length) problems.push('Choose what you are looking for.');
  if ((form.about ?? '').length > 300) problems.push('Keep the short intro under 300 characters.');
  return problems;
}

/** The first letter shown in the avatar circle. Amber has no photos in this version. */
export function initial(name) {
  return (name ?? '?').trim().charAt(0).toUpperCase() || '?';
}

export function isShared(interest, shared = []) {
  return shared.some(item => item.toLowerCase() === interest.toLowerCase());
}

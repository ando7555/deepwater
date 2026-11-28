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

/** The fixed goodbyes for ending a conversation. Same sentences as AmberConnections.CLOSING_NOTES. */
export const CLOSING_NOTES = [
  { id: 'NOT_A_MATCH', label: 'Thank you for the conversation. I don’t feel we are a match, and I wish you well.' },
  { id: 'NO_TIME', label: 'I don’t have time for Amber right now. Thank you, and take care.' },
  { id: 'MET_SOMEONE', label: 'I have met someone. Thank you for the conversation, and good luck.' },
];

/**
 * A gentle line under a conversation: whose turn it is. After 3 quiet days it suggests answering or closing kindly,
 * which is Amber's answer to ghosting. Returns '' when there is nothing to say.
 */
export function turnText(connection, now) {
  if (connection.lastMessageMine == null) return 'New: say hello';
  if (connection.lastMessageMine) return `Waiting for ${connection.person.displayName}`;
  const quietDays = connection.lastMessageAt ? (now - new Date(connection.lastMessageAt)) / 86400000 : 0;
  return quietDays >= 3 ? 'Your turn: reply, or close kindly' : 'Your turn';
}

/** What still blocks saving the profile, in plain words. Mirrors the server's checks. `age` comes from the birth date. */
export function profileProblems(form) {
  const problems = [];
  if (!form.displayName?.trim()) problems.push('Add the first name others will see.');
  const age = form.age == null || form.age === '' ? NaN : Number(form.age);
  if (Number.isNaN(age)) problems.push('Add your birth date. Amber is only for adults (18 or older).');
  else if (!Number.isInteger(age) || age < 18) problems.push('Amber is only for adults (18 or older).');
  if (!form.confirmAdult) problems.push('Confirm that you are 18 or older.');
  if (!form.area?.trim()) problems.push('Choose your area, at city level.');
  if (!form.intents?.length) problems.push('Choose what you are looking for.');
  if ((form.about ?? '').length > 500) problems.push('Keep the bio under 500 characters.');
  return problems;
}

/** The first letter shown in the avatar circle when there is no photo to show. */
export function initial(name) {
  return (name ?? '?').trim().charAt(0).toUpperCase() || '?';
}

export function isShared(interest, shared = []) {
  return shared.some(item => item.toLowerCase() === interest.toLowerCase());
}

/** Deepwater blue gradient pairs for the profile cover (the logo's blues and sea green), used when no photo is shown,
 *  so each person gets a steady colour of their own. */
export const COVERS = [
  ['#5ad0ff', '#1e7bf0'], ['#1e7bf0', '#0a3a9c'], ['#5fe0c0', '#1e6fe0'],
  ['#3fb6f0', '#163259'], ['#7ad8ff', '#2d5590'], ['#2aa7c9', '#0a1a36'],
];

/** The same name always gets the same cover, so a person looks the same in Discover, Invitations and chat. */
export function coverFor(name) {
  const text = (name ?? '').trim().toLowerCase();
  let hash = 0;
  for (const char of text) hash = (hash * 31 + char.codePointAt(0)) >>> 0;
  return COVERS[hash % COVERS.length];
}

/** "2 of 5" for the horizontal card row, from the scroll position. */
export function railPosition(scrollLeft, cardWidth, count) {
  if (!count) return { index: 0, label: '' };
  const index = cardWidth > 0 ? Math.min(count - 1, Math.max(0, Math.round(scrollLeft / cardWidth))) : 0;
  return { index, label: `${index + 1} of ${count}` };
}

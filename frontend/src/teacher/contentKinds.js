/*
 * The kinds of content a teacher can add. Each kind is a template: the words on the card, the
 * default topic, and placeholder text for the fields and exercises in the learner's language.
 * The kind is stored in the skill key's second segment (danish.grammar.negation), so the
 * backend and the lesson shape stay unchanged.
 */

const by = (da, de) => ({ da, de });

export const CONTENT_KINDS = [
  {
    id: 'grammar', label: 'Grammar', topic: 'Grammar',
    line: 'One rule with examples: en or et, word order, negation with ikke or nicht.',
    objective: by('Say what they do not do, with ikke in the right place.', 'Say what they do not do, with nicht in the right place.'),
    explanation: by(
      'Danish puts ikke after the verb: Jeg spiser ikke kød. With two verbs it goes after the first one: Jeg kan ikke komme i dag.',
      'German puts nicht at the end of a short sentence: Ich komme nicht. With a second verb, nicht goes before it: Ich kann heute nicht kommen.',
    ),
    exercises: [
      { taskType: 'SELECT', placeholder: by('Which sentence puts ikke in the right place?', 'Which sentence puts nicht in the right place?') },
      { taskType: 'TEXT', placeholder: by('Make it negative: Jeg spiser kød. → Jeg spiser ___ kød.', 'Make it negative: Ich komme. → Ich komme ___.') },
      { taskType: 'TRANSFER', placeholder: by('Write two things you do not do on Sundays.', 'Write two things you do not do on Sundays.') },
    ],
  },
  {
    id: 'writing', label: 'Writing', topic: 'Writing',
    line: 'The learner writes something real: a message, a form, a short text.',
    objective: by('Write a three-line message to a neighbour.', 'Write a three-line message to a neighbour.'),
    explanation: by(
      'A short Danish message has three parts: a greeting (Hej Mette), one clear sentence (Der er kommet en pakke til dig), and a sign-off (Hilsen Anna).',
      'A short German message has three parts: a greeting (Hallo Lena), one clear sentence (Ein Paket für dich ist angekommen), and a sign-off (Viele Grüße, Anna).',
    ),
    exercises: [
      { taskType: 'SELECT', placeholder: by('Which line is the sign-off?', 'Which line is the sign-off?') },
      { taskType: 'TEXT', placeholder: by('Finish the greeting: ___ Mette (Hi Mette)', 'Finish the greeting: ___ Lena (Hi Lena)') },
      { taskType: 'TRANSFER', placeholder: by('Write a message to your neighbour: a parcel came for them.', 'Write a message to your neighbour: a parcel came for them.') },
    ],
  },
  {
    id: 'vocabulary', label: 'Vocabulary & phrases', topic: 'Vocabulary',
    line: 'Words and set phrases for one situation: the bakery, the bus, the doctor.',
    objective: by('Order at a bakery and ask for the bill.', 'Order at a bakery and ask for the bill.'),
    explanation: by(
      'At the counter, Danes keep it short: En kaffe, tak. Hvad koster det? Må jeg bede om regningen? Tak at the end makes the order polite.',
      'At the counter, keep it short: Einen Kaffee, bitte. Was kostet das? Die Rechnung, bitte. Bitte makes the order polite.',
    ),
    exercises: [
      { taskType: 'SELECT', placeholder: by('You want the bill. What do you say?', 'You want the bill. What do you say?') },
      { taskType: 'TEXT', placeholder: by('Type the Danish for “a coffee, please”.', 'Type the German for “a coffee, please”.') },
      { taskType: 'TRANSFER', placeholder: by('Order something you drink every day.', 'Order something you drink every day.') },
    ],
  },
  {
    id: 'lyrics', label: 'Song lyrics', topic: 'Song lyrics',
    line: 'Lines from a song: the learner fills the missing word and hears the rhythm of the language. Only lyrics you have the right to use.',
    objective: by('Sing the chorus and use its key phrase in a sentence.', 'Sing the chorus and use its key phrase in a sentence.'),
    explanation: by(
      'Say why this song: what the chorus repeats, which sound or word to listen for, and what the key phrase means in everyday Danish.',
      'Say why this song: what the chorus repeats, which sound or word to listen for, and what the key phrase means in everyday German.',
    ),
    source: 'Song title, who wrote it, and your right to use these lines (your own text, public domain, or written permission).',
    exercises: [
      { taskType: 'TEXT', placeholder: by('Fill the missing word from the chorus: …', 'Fill the missing word from the chorus: …') },
      { taskType: 'TEXT', placeholder: by('Fill the missing word from the second verse: …', 'Fill the missing word from the second verse: …') },
      { taskType: 'TRANSFER', placeholder: by('Write your own line in the same rhythm.', 'Write your own line in the same rhythm.') },
    ],
  },
  {
    id: 'conversation', label: 'Conversation & negotiation', topic: 'Conversation',
    line: 'A dialogue at work, in a shop, with a landlord: asking, bargaining, saying no politely.',
    objective: by('Ask for a lower deposit and say no politely.', 'Ask for a lower deposit and say no politely.'),
    explanation: by(
      'Danes soften a no with desværre and a reason: Det kan jeg desværre ikke, fordi … Then an offer: Kan vi sige 10.000?',
      'A German no is softened with leider and a reason: Das geht leider nicht, weil … Then an offer: Können wir 1.000 sagen?',
    ),
    exercises: [
      { taskType: 'SELECT', placeholder: by('The landlord wants a higher deposit. What do you say first?', 'The landlord wants a higher deposit. What do you say first?') },
      { taskType: 'TEXT', placeholder: by('Complete the polite no: Det kan jeg desværre ___.', 'Complete the polite no: Das geht leider ___.') },
      { taskType: 'TRANSFER', placeholder: by('Negotiate a later deadline with your manager in three lines.', 'Negotiate a later deadline with your manager in three lines.') },
    ],
  },
  {
    id: 'listening', label: 'Music or podcast', topic: 'Listening',
    line: 'A levelled listening lesson from a recording, with a rights check. Longer form.',
    exercises: [],
  },
];

export const KIND_IDS = CONTENT_KINDS.map(kind => kind.id);

export function kindById(id) {
  return CONTENT_KINDS.find(kind => kind.id === id) || null;
}

/** Text for the learner's language from a {da, de} pair; plain strings pass through. */
export function pick(value, languageCode) {
  if (!value) return '';
  if (typeof value === 'string') return value;
  return value[languageCode === 'de' ? 'de' : 'da'] || value.da || '';
}

/** The kind of an existing lesson, read from its skill key (language.kind.topic), or 'listening' for audio. */
export function kindOfLesson(lesson) {
  if (lesson?.audio) return 'listening';
  const segment = (lesson?.skillKey || '').split('.')[1];
  return KIND_IDS.includes(segment) ? segment : '';
}

# Polish B1 practice exam, Nibble brand switch, Harbor job families

Owner ask, 2026-11-08: Polish language practice based on the parts of the Polish B1 state exam, with an AI agent that reviews
answers, marks the level and says whether the learner looks eligible for B1; a switch between the Deepwater and Nibble
names; Harbor and the AI Fashion idea visible on the welcome page; the complicated teacher upload removed; content
categorisation reused for Harbor.

## Polish B1 practice exam

- Five parts, in the order of the state exam (egzamin certyfikatowy z języka polskiego jako obcego, level B1):
  listening (Rozumienie ze słuchu), grammar (Poprawność gramatyczna), reading (Rozumienie tekstów pisanych),
  writing (Pisanie) and speaking (Mówienie).
- Items are Deepwater's own practice items in `backend/src/main/resources/exam/polish-b1.json`, not official tasks.
  The screen and the result say "practice estimate, not an official result".
- Listening uses the browser's Polish voice (pl-PL), two plays per recording. Speaking uses the browser's speech
  recognition where it exists (Chrome, Edge) to write down the answer; otherwise the learner types. Only the words are
  marked, never pronunciation.
- Closed parts are marked by the answer key. Writing and speaking are marked on four criteria of 0 to 25 each
  (task, coherence, vocabulary, grammar).
- Pass rule: every part needs at least `EXAM_PASS_PERCENT` (default 60). The official threshold should be checked on
  certyfikatpolski.pl before any claim is made; the setting exists so it can be changed without code.
- Level estimate: B1+ (all parts pass, average 85% or more), B1 (all parts pass), A2 (average within 20 points of the
  pass mark), A1-A2 otherwise.

### The examiner

- `ANTHROPIC_API_KEY` set on the backend: one Claude API call (official Java SDK, model `EXAM_AI_MODEL`, default
  `claude-opus-5-5`, structured output) marks all open answers. Scores are clamped, levels come from a fixed list,
  unknown item ids are dropped, and the learner text sits in an `<answer>` block the instructions say never to obey.
- No key, or the AI call fails: a fixed rubric marks the open answers (length, linking words, word variety, Polish
  letters, topic words). It cannot judge grammar, so grammar is capped at 15 of 25, and the result says it was marked
  by fixed rules. Any answer the AI skips is marked by the rubric.
- Learner answers are sent to Anthropic only when the key is set. The privacy text should say so before launch.

### API

`GET /api/v1/exam/polish-b1` (tasks, no answer keys), `POST /api/v1/exam/polish-b1/attempts` with
`{"answers": {"g1": "1", "w1": "text"}}`, `GET /api/v1/exam/polish-b1/attempts` and `/attempts/{id}`.
Attempts are stored in `exam_attempts` and deleted with the account.

## Brand switch

`Deepwater | Nibble` in the header of the welcome page and the app. It changes only the parent name, mark and tagline,
stored per browser. The mouse mark (`frontend/public/brand/nibble.svg`) is an original drawing, not Tom and Jerry's
Jerry or Nibbles, which belong to Warner Bros. "Nibble" needs a trademark search before launch.

## Welcome page

Four cards: Echo, Polish B1, Harbor and AI Fashion (coming soon). The headline frames the motivation:
learn the language, land the job.

## Teacher workspace

Only "Review & publish" is left. The in-browser lesson builder, exercise editor and music/podcast form were removed;
lessons arrive as drafts from the course import. The GraphQL `saveEchoLessonDraft` mutation still exists for imports.

## Harbor job families

Each posting gets a job family (Product & leadership, Data & AI, Cloud & DevOps, Frontend & full-stack, Backend, Other)
and a local-language tag (required, a plus, English is enough, not stated), read with fixed rules from the posting.
Filters: `category` and `language=NO_LOCAL_REQUIRED`. When Danish or German helps, the job card points to Echo.

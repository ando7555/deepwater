# Ideas from Amalie Teaches and Duolingo for Echo

Date: 2026-10-28. Requested by the product owner: take useful content and UI ideas from
amalieteaches.com (not its visual style), add a moving 3D element in Deepwater style (the owner first said a globe, then clarified: the Deepwater logo in 3D, not a globe), and take
Duolingo-like features "the paradoxical way": turn Duolingo's known drawbacks into Echo strengths.

Status legend: **Built** = in this PR. **Proposal** = not built, needs an owner decision.
**Observed** = seen in a source listed below. Nothing here is user-tested.

## Sources and limits

- **2026-10-28:** amalieteaches.com was blocked by the cloud network policy, so the first
  rounds below rest **only** on two screenshots the owner shared (home page and Practice page).
- **2026-10-29:** after the owner allowed the domain, `www.amalieteaches.com` was fetched
  (the bare `amalieteaches.com` is still refused by the proxy). Only the public home page is
  readable without an account. That page ships the site's whole interface text catalogue
  (171 screen namespaces, about 3,400 labels, for the student, teacher and admin areas), which
  shows what each screen does. **No lesson text, word lists, audio or prices are public:** they
  sit behind sign-in, and no account was created. The public travel story for Denmark is an
  empty placeholder. See [the third round](#third-round-full-site-read-2026-10-29). Nothing was
  copied into the product; that section summarises structure and method in our own words.
- Duolingo drawbacks come from published learner research and critiques, listed under
  [References](#references), found by web search on 2026-10-28 and summarised, not re-read in
  full. Treat them as pointers, not settled findings. Some are anecdotal and are marked so.

## What the Amalie Teaches screenshots show (Observed)

1. **Home page:** a large 3D globe you can drag, with glowing pins in Europe joined by dashed
   routes, and the hint "Drag to explore · Tap a pin". Top navigation has four items:
   Practice, Lessons, Games, Explore, plus Profile, notifications and messages.
2. **Practice page:** "Choose how you want to practise today", with four modes on photo cards:
   - Read: "Recognise the word."
   - Listen: "Hear it, then write it."
   - Speak: "Say it out loud."
   - Write: "Spell it in Danish."
3. Under the cards: "Create flashcards", "My flashcards" and "Warm up with matching".

Visual style (olive and cream colours, script logo, stock photos) is deliberately **not** used.

## What Echo takes from it

| Idea | Echo version | Status |
|---|---|---|
| Draggable 3D hero on the home page | The **Deepwater mark in 3D** instead of a globe: the lid, the two water layers (drawn as dots) and the sounding line with five nodes, turning slowly and draggable. It follows the Echo, Amber and light themes. On the learner home page it doubles as a **depth gauge**: the lit nodes show mastery of the current lesson ("50% deep in this lesson"). Auto-rotation stops on hover and for `prefers-reduced-motion`. | Built |
| Practice by skill (Read, Listen, Speak, Write) | Practice hub over existing task types (see below). | Built |
| Learner-made flashcards | My cards with a calm five-box schedule (see below). | Built |
| Matching warm-up | A one-minute matching round from the learner's own cards. | Built |
| Four-item navigation | Today, Practice, My cards and Progress. No Games tab (see Duolingo below). | Built |

## Duolingo drawbacks turned into Echo strengths (principles; the calm-progress and no-reset parts are built)

| Duolingo drawback | Evidence | Echo's opposite |
|---|---|---|
| Streaks can turn learning into "keep the streak alive". | Learner interview studies [1][2] | **No streak guilt.** Reward evidence of learning (patterns mastered, due reviews cleared). A missed day reschedules reviews instead of resetting a counter. |
| Little explicit grammar explanation. | Learner studies [1][2] | **Explain why.** Every Echo lesson already carries an explanation of how the sentence is built. Keep that as a rule. |
| "You get good at playing Duolingo, not at speaking." | Podcast analysis, anecdotal [5] | **Transfer tasks.** Echo's TRANSFER exercise asks the learner to use the pattern in their own situation; make it the end of every lesson. |
| Unrealistic sentences. | Learner studies [1][2] | **Real voices.** Music and podcast lessons used with the creator's permission, reviewed by a teacher. |
| Effectiveness claims rest on company-commissioned studies. | Published critique of Duolingo's own efficacy papers [1][3][4] | **Honest progress.** Show the learner's own mastery and review data, and make no "one semester in N hours" claims until Echo has independent evaluation. |
| AI conversation practice feels canned. | Podcast analysis, anecdotal [5] | **Teacher-reviewed AI.** AI drafts exercises, and a teacher approves them before learners see them (the plan already in the architecture docs). |
| Games and leagues compete for attention. | Learner studies [1][2] | **Calm by default.** Short warm-ups are fine, but there are no leaderboards. Progress is compared with your past self only. |

## Echo MVP capabilities built on this PR

The owner asked (2026-10-28) to turn these ideas into capabilities that make a good Echo MVP
and keep the idea open for Amber. **Built:**

| Capability | What the learner gets | How it works |
|---|---|---|
| Section tabs | Today, Practice, My cards, Progress | The existing lesson view is now "Today". |
| Practice hub | Choose Read, Listen, Speak or Write | Reuses graded task types: Read = SELECT, Write = TEXT, Speak = TRANSFER, Listen = audio lessons. Due and weakest lessons come first. Speak does not record or grade the voice; the learner says the sentence aloud, writes it and self-checks, as TRANSFER already does. |
| My cards | Save your own phrases and review them | New `echo_cards` table and GraphQL `echoCards`, `saveEchoCard`, `reviewEchoCard`, `deleteEchoCard`. Five-box Leitner schedule: 1, 3, 7, 14, 30 days. "Not yet" moves a card back **one** box, never to the start. Cards belong to one learner and one language and are deleted with the account. |
| Matching warm-up | A one-minute round from your own cards | Needs at least three cards. Uses due cards first. Nothing is graded or stored. |
| Calm progress | Depth, patterns mastered, reviews ready, accuracy, cards settled | No streaks, no leagues. The 3D mark shows the average practice estimate across the language's lessons. |

Not built: speech capture or pronunciation grading, a matching mode that uses teacher content,
and reminders or notifications.

## Second round: profile and memory-based practice (Built)

The owner asked (2026-10-28) for a profile page, a phone number at sign-up, more interactive
and memory-based practice, and a product that is "better than Busuu, Memrise, Amalie Teaches
or Duolingo". This round builds:

- **Profile:** name, optional phone (normalised, 6–15 digits), city and country where the
  person lives, first language, and why they are learning. The details are stored in
  `person_profiles` on the Deepwater account rather than in Echo, so a future Amber profile
  can reuse them. They are private, never shown to others, and deleted with the account.
  The sign-up form also takes an optional phone number.
- **Daily review:** one mixed session of due cards and graded exercises, interleaved, with a
  progress bar and a summary at the end.
- **Typing from memory** (recall, not just recognition), with forgiving checks: case,
  punctuation and one small slip are allowed, but æ, ø and å must be right.
- **Hear it:** the browser's own voice reads phrases aloud in Danish or German.
- **Keyboard shortcuts:** Space, 1, 2 and H.
- **One tap to save a missed answer as a card.**
- **Memory meter:** an estimated chance of recall from a simple forgetting curve. It is an
  estimate for motivation, not a measurement.

**Not claimed:** Echo has not been compared with these apps on any learner. The intended
differences are design choices, to be validated with users:

| Their common pattern | Echo's choice |
|---|---|
| Streaks and leagues (Duolingo) | No streaks. A miss steps back one level, never to zero. |
| Large generic decks (Memrise) | Teacher-reviewed lessons plus the learner's own phrases from real life. |
| Course-first structure (Busuu) | Pattern explanations, transfer to your own sentences, and calm spaced review. |
| Video teacher brand (Amalie Teaches, from screenshots) | Real voices from music and podcasts, used only with the creator's permission. |

## Keeping the idea open for Amber (Proposal)

Amber is the future connections product. People can join it without Echo, and it receives
only opt-in, coarse Echo signals (owner decisions, 2026-10-17). Nothing below is built for Amber.

- **Shared now:** `DeepwaterMark3D` and the calm-progress pattern read the theme tokens,
  so they already render in Amber's warm theme. `ExerciseCard`, `PracticeHub` and the card
  review take their content as props and contain no Echo-only logic.
- **Same principles, different product:** depth over volume, no streak guilt, and a miss
  that steps back instead of resetting. For Amber this could mean no infinite feed, no
  swipe streaks, and slow, consent-first introductions.
- **Possible reuse, to decide later:** a practice hub for conversation skills, such as
  asking a good first question, and saved "conversation starters" on the same calm
  schedule. Practising in Danish could be an opt-in coarse signal Amber may show.
- **Deliberately not generalised yet:** the card tables stay `echo_*`. Following the project
  rule "generalise only when evidence supports it", a shared module should wait until
  Amber has a confirmed use for it.

## Suggested next step

Measure whether learners return for due reviews without streak pressure, for example the
share of due cards and lessons reviewed within two days. Then pick the next proposal.

The 2026-10-29 read of the site's public page is summarised in the next section.

## Third round: full site read (2026-10-29)

The owner asked for the site's content **and the UI components Echo could reuse**. Everything
under "Observed" comes from the public page's interface text (labels, hints, empty states);
it shows what a screen does, not how it looks or how well it works. Screens behind sign-in
were not opened. "Echo today" was checked against `main` on 2026-10-29. Reuse means reusing
the idea in Deepwater's own visual style, never the site's look or wording.

### What the platform is (Observed)

A multi-teacher platform, not a single course: each teacher gets a site (subdomain) with
their own students. Four student areas: **Practice, Lessons, Games, Explore**. Teachers have
a word bank, vocabulary modules, activity packs, a calendar, student profiles and analytics.
A platform admin area handles invitations, sites, support and a waitlist. Levels are tagged
by CEFR (A1 to C2 colour tokens exist).

### Content model (Observed)

| Content | Fields and rules seen |
|---|---|
| Word (shared word bank) | Danish, English, word class (noun, verb, expression), gender (en/et or not applicable), Danish example plus its translation, pronunciation tip, recorded audio. A word is shared: editing it updates everywhere and keeps learner progress. |
| Vocabulary module | Named set of words, e.g. "At the café". Built by pasting pairs or importing CSV (up to 1,000 words, 2 MB) with column mapping and a review step that flags missing translations. Nothing is assigned until the teacher approves and picks students. |
| Reading activity | A Danish passage plus a glossary; the editor insists every word in the passage has a meaning. The learner taps any word to see it. |
| Dialogue activity | Turns: the teacher's Danish line, an English prompt for the learner's reply, a model answer. |
| Sentence activity | A correct Danish sentence, given to learners as shuffled word tiles; optional teaching note. |
| Quiz | Multiple choice per lesson, one correct option, optional explanation and topic. Can be imported as a JSON file. |
| Starter pack | Copyable ready-made pack: a morning reading with glossary, a café dialogue, sentence building and 12 words. Copies become the teacher's private drafts. |
| Lesson material | Teacher-written class page with sections, a quiz and flashcards linked to a module. |

The only Danish visible publicly is placeholder examples (greetings, "a coffee", "the bill",
"an apple"). There are **no word lists or lessons to gather** without an account.

### Practice catalogue (Observed)

The practice hub groups 16 modes under four skills, each with an estimated length in minutes
and a "Soon" or "Later" badge for modes not yet live:

- **Read:** match the words; tap to translate (read a short text, tap unknown words);
  flashcards; real-life Danish (menus, signs, train announcements).
- **Listen:** listen and choose; hear it, write it; similar sounds (tell near-identical words
  apart); everyday dialogues.
- **Speak:** a café dialogue one line at a time; say it in Danish (see English, say Danish);
  answer out loud (questions about you and your day); conversation partner with feedback.
- **Write:** build a sentence (word order); spelling with æ, ø, å at hand; spot the mistake;
  write a message with feedback from the teacher.

### Interaction details worth noting (Observed)

1. **Flashcard session:** direction switch (Danish to English, English to Danish, audio to
   Danish); answer by flipping, typing or speaking; three self-grades: *Missed it*, *I was
   close*, *I knew it*. "Close" is flagged for the teacher to look at.
2. **Danish letters:** buttons that insert æ, ø, å at the cursor, with keys 1, 2, 3.
3. **Audio:** play button with adjustable playback speed.
4. **Study before you practise:** a word list view with gender, example, translation and
   pronunciation before the cards start.
5. **Filters:** pick a module or word category; "N active filters".
6. **Clear ends:** "Set complete", "You're caught up", "Nothing due", words to revisit,
   "Next N words", "Keep practising".
7. **Schedule honesty:** results say whether they changed the review schedule: first attempts
   on due words in matching update it; reading, dialogue and sentence activities say
   "Practice only, your review schedule is unchanged". Speaking practice records completion
   only, and says the transcript is not saved or scored.
8. **Leitner boxes:** every word starts as New; correct moves it forward with a longer gap;
   wrong sends it back to New. A "How boxes work" explainer is in the session.
9. **Needs another look:** words stuck in New, words wrong twice or more, with the learner's
   last wrong answer shown and copyable.
10. **Learner-made cards:** named sets, paste a list (tab or semicolon auto-detected, swap
    columns, preview with line-level problems), undo on remove, a per-side length limit,
    "your teacher can see your cards and progress".
11. **Quiz feedback:** the right answer and why straight after each question; "Retry missed".
12. **Robust saving:** "Answer saved but overview could not refresh, retry", "Result not
    saved, retry saving" instead of silent loss.
13. **Teacher view of a learner:** deck by box, 7-day flashcard accuracy, due now, recent misses
    with what they wrote, accepted near-misses, dismiss words the learner already knows.
14. **Live lessons:** booking from teacher availability (morning, afternoon, evening;
    Google Calendar invites), video call with whiteboard, private teacher notes that autosave,
    a To do / In progress / Done learning board.
15. **Games and Explore:** "Roll and talk" (a die gives a speaking question), match pairs, a
    shared Street View walk through Danish streets, and a travel globe with country stories.
16. **Streak:** shown in practice and on the teacher's student profile.

### Reuse for Echo (Proposals unless marked)

| Site component | Echo today | Recommendation |
|---|---|---|
| Practice hub by skill | Built: Read, Listen, Speak, Write over SELECT, audio, TRANSFER and TEXT tasks | **Reuse.** Add a time estimate per mode and an honest "Soon" badge. Next modes worth building first, because they need no speech grading: *build a sentence* (tiles), *tap to translate* reading with a glossary, *hear it, write it*. |
| Three-way self-grade with "close" | Cards have two grades; typed answers already accept one small slip as "close" | **Reuse.** Add "I was close": keep the card in its box and bring it back sooner. Keep Echo's rule that a miss steps back one box rather than to New (deliberate difference). |
| Direction switch on cards | One direction | **Reuse.** Danish to English, English to Danish, and audio to Danish using the existing browser voice. |
| Danish letter buttons | Typing requires æ, ø, å to be right but gives no help | **Reuse, small.** Insert buttons plus keys; check they do not clash with Echo's 1 and 2 shortcuts. |
| Playback speed | `speak()` has a fixed rate of 0.9 | **Reuse, small.** Normal and slow. |
| Word detail (gender, example, pronunciation tip) | Cards hold phrase and meaning only | **Reuse** as optional fields on cards and lesson words; gender (en/et) matters for Danish. |
| Paste a list into cards | Cards are added one at a time | **Reuse.** Paste pairs with separator detection and a preview of problem lines. Same parser can serve teacher import later. |
| Needs another look | Save-a-miss-as-card exists; no "stuck" list | **Reuse.** List cards missed twice or more with the learner's last wrong answer. Fits Echo's honest progress view. |
| "Practice only" vs "schedule updated" messages | Not stated | **Reuse.** Say after every activity whether it changed the review schedule. Cheap and builds trust. |
| Retry missed at end of a set | Daily review shows a summary | **Reuse.** One-tap "retry missed". |
| Teacher content tools (word bank, CSV import, draft and publish, preview that saves nothing, starter packs) | Echo has a teacher review flow for lessons | **Later, for the teacher-reviewed AI plan.** The draft, preview, publish pattern is the right shape for AI-drafted exercises a teacher approves. |
| Teacher view of a learner's misses | Not built | **Later.** Needs a teacher role and learner consent. |
| Booking, video call, whiteboard, learning board | Not built | **Do not copy for the MVP.** This is a tutoring business; Echo is self-study first. Revisit only if Deepwater adds live teachers. |
| Streaks, Roll and talk, Street View walk, travel globe | Echo has no streaks; owner rejected a globe | **Do not copy** streaks or the globe. Roll and talk could become a later speaking prompt deck. |

### Content to gather

None could be gathered: the site's lessons and words are behind sign-in, and copying a
teacher's material would need her permission anyway. If the owner wants Amalie's actual
material in Echo, the way is to ask her for permission or a partnership, not to scrape it.

## Duolingo UI components (2026-10-29)

The owner asked for the same content and reusable-component view of Duolingo, on this PR.
**duolingo.com is blocked** by the cloud network policy, so the app was not opened. What follows
comes from web search results only (Duolingo's own blog, a fan wiki and press), summarised, not
re-read in full, and possibly out of date [6][7][8][9][10]. No Duolingo content was copied.

### Components seen in those sources (Observed, secondhand)

| Component | What it does |
|---|---|
| Learning path | One winding path of units and lessons instead of a skill tree; practice sessions sit in the path; old words come back in later lessons instead of skills "breaking" [7][8]. |
| Exercise types | Tap the pairs (match a grid); tap what you hear (word bank, replay); type what you hear (with a slow "turtle" replay); speak this sentence; arrange words; complete the translation; mark the correct meaning [6][9]. |
| Practice hub | Speak, Listen, Mistakes, Stories, Radio and Words modes; mistakes practice and all-speaking lessons for paying users [9][10]. |
| Legendary | A harder re-run of a finished unit for extra XP, with few mistakes allowed [10]. |
| Hearts, now Energy | Hearts were lost on mistakes; Energy (tested from July 2025) drains on every exercise and refills with gems, ads or streaks of correct answers; unlimited for paying users [11]. |
| XP, leagues, streaks | Points per lesson, leaderboards against other learners, daily streaks [12]. |

### Reuse for Echo (Proposals)

| Duolingo component | Recommendation for Echo |
|---|---|
| Slow replay ("turtle") | **Reuse.** Same as the playback speed idea from Amalie Teaches; one small button on every audio prompt. |
| Tap what you hear / type what you hear | **Reuse.** Echo already has read-aloud and typed recall; combining them gives a listening dictation mode with no new backend. |
| Arrange words | **Reuse.** Same as Amalie Teaches' "build a sentence"; build it once for both ideas. |
| Mistakes practice | **Reuse, free.** Echo's "save a miss as a card" plus the proposed "needs another look" list cover it; keep it free for everyone, not a paid extra. |
| Old words returning inside new lessons | **Reuse.** Mix due cards into lessons, as Echo's daily review already does. |
| Single path | **Consider later.** A visible path helps beginners see what is next, but must show patterns mastered, not just nodes passed. |
| Legendary re-runs | **Do not copy.** Mastery in Echo comes from spaced review, not a one-off hard run. |
| Hearts or Energy | **Do not copy.** Limiting practice when learners make mistakes or practise a lot works against learning; Echo never caps practice. |
| Leagues and streaks | **Do not copy.** Already an Echo principle (see the table above). |

To check these against the real app, add `duolingo.com` and `www.duolingo.com` to the allowed
domains; only threads started after the change can reach it.

## Brain Biscuit Learning (2026-10-29)

The owner also asked for https://brainbiscuitlearning.com/. Its public pages (home, method,
levels, recorded courses and the PlayWord game page) were read on 2026-10-29 [13]. Signed-in
areas (dashboard, lessons, the game itself) were not opened. Facts below are what the pages
say, summarised; nothing was copied.

### What it is (Observed)

An online Danish school (says it has taught since 2010) with three products:

- **Live classes:** weekly, small groups of at most three, narrow age bands; children 4 to 18
  and adults. The adult plan bundles a weekly class with recorded lessons (USD 69.90 a month).
- **Recorded courses:** short video lessons of 5 to 10 minutes by one teacher, followed in
  order but all unlocked from day one; 170+ lessons, 1,000+ sentences, 850+ picture
  exercises. Beginner and Intermediate are live, Advanced is "coming soon". USD 19.90 a month
  per course, 29.90 for both. Progress, streaks, badges and a certificate on completion.
- **PlayWord (Ordjagten):** a word-hunt game (USD 1.99 first month, then 4.99, free with a course).

**Method, as the site states it:** visual first (pictures carry meaning instead of
translation drills), interactive themed lessons with short, frequently changing activities,
material adjusted to age, level and interests, speaking, reading and writing from day one,
and themes that come back over time. The pages give no lesson sequence, no review schedule
and no feedback method.

**Levels:** five named levels described by what the learner can do: *First words*, *First
sentences*, *Small talk*, *Confident talker*, *Fluent and polished*. No CEFR mapping is given.
Live students are placed by a test plus age and time zone.

**PlayWord details:** find Danish words in a letter grid; a picture and English gloss show the
target (for example a cat, "en kat"); found words go into a personal **word box** with their
article; ten boards per level, finishing one unlocks the next. Levels follow grammar, not
topics: Beginner = nouns with en/et (the article is spoken aloud), next level adds verbs shown
with "at", top level adds adjectives in base form. Native audio throughout; a mascot button
reveals the word.

### Reuse for Echo (Proposals)

| Brain Biscuit idea | Recommendation for Echo |
|---|---|
| Picture as the cue instead of English | **Reuse.** Add an optional image to cards and exercises so a learner can recall Danish from a picture, not only from English. Needs an image source with clear rights. |
| Article always attached to the noun (en/et), spoken aloud | **Reuse.** Store and show gender with every noun, as also proposed from Amalie Teaches; read the article aloud with the word. Gender is a classic Danish difficulty. |
| Levels named by what you can do | **Reuse.** Label Echo's progress with can-do names in our own words (for example "first sentences", "small talk") alongside any CEFR tag, so progress means something to a beginner. |
| Levels that follow grammar (nouns, then verbs, then adjectives) | **Reuse as sequencing input.** Fits the project's prerequisite-aware sequencing idea; test it rather than assume it. |
| Personal word box filled by playing | **Reuse.** Words found in a game or a reading go straight into My cards with their article. |
| Short lessons (5 to 10 minutes), all unlocked | **Reuse.** Show an estimated length on every lesson and never lock content behind earlier steps. |
| Themes that come back | **Already in Echo** through spaced review; keep it explicit in the lesson plan. |
| Word-hunt grid game | **Later, maybe.** Fun but slow per word; only worth it if it feeds the word box and the review schedule. |
| Streaks and badges | **Do not copy** streaks (Echo principle). A completion certificate could be fine later. |
| Live small-group classes | **Not for the MVP.** Echo is self-study first; same call as for Amalie Teaches' booking. |

## Drawbacks of Amalie Teaches and Brain Biscuit, turned into Echo features (Built, 2026-10-29)

The owner asked to take the drawbacks of both sites, combine them and build. "Drawback" here
means a weakness visible in what each site publishes (listed above), not a tested finding.

| Drawback seen | Where | Echo's opposite | Status |
|---|---|---|---|
| A wrong answer sends a word back to New | Amalie Teaches | A miss steps back one box only, and a new **"I was close"** grade keeps the box and brings the card back tomorrow. | Built |
| Learners practise what a teacher assigned; content sits behind a teacher or a subscription | Both | Learners make their own cards, one at a time or by **pasting a list** (tab, semicolon, " - " or "="; up to 100 lines, checked line by line before saving). Nothing is locked. | Built |
| Recorded video lessons are watched, not recalled | Brain Biscuit | Every card can be reviewed in **five directions**: phrase to meaning, meaning to phrase, type from memory, **hear it and write it**, and **build the sentence** from word tiles. The daily review rotates them. | Built |
| Speaking is recorded as "done" without feedback, and no review schedule is described | Both | After every grade Echo says in plain words what changed ("One step deeper. Next look in 7 days."). | Built |
| The learner cannot see what keeps going wrong (on Amalie's site the teacher sees the misses) | Amalie Teaches | **Keeps slipping**: cards missed twice or more, with the learner's own last wrong answer, visible to the learner. | Built |
| Danish letters need a special keyboard | General | **æ, ø, å** buttons (ä, ö, ü, ß for German) next to every typing box. | Built |
| Audio only at normal speed | General | **Slow** replay next to every Hear button. | Built |
| Noun gender left to memory (Brain Biscuit teaches en/et only inside a paid game) | Brain Biscuit | Cards starting with en/et (der/die/das) show an **en-word / et-word badge**; the article is read aloud with the noun. | Built |
| Streaks and badges | Both | Still none in Echo. | Unchanged |
| Pictures as cues, can-do level names | Brain Biscuit strengths | Not built: images need a source with clear rights, and level names need an owner decision. | Proposal |

Backend: `echo_cards` gains `miss_count` and `last_miss`; GraphQL adds `saveEchoCards` and a
`grade` (`MISSED`, `CLOSE`, `KNEW`) plus optional `answer` on `reviewEchoCard` (the old
`knewIt` still works). None of this is tested with learners yet.

## References

Owner's screenshots of amalieteaches.com, shared in the project thread on 2026-10-28.
Public home page of https://www.amalieteaches.com/ and its interface text catalogue, fetched 2026-10-29.

1. CALL-EJ article on Duolingo: https://callej.org/index.php/journal/article/download/419/347/1013
2. Isaacson, Duolingo learner study: https://tfulib.repo.nii.ac.jp/record/794/files/p87-99%20Jonathan%20Isaacson.pdf
3. Technology Assisted Language Education (2024): https://doaj.org/article/cb110689f72d4ec09a48037fba7b99ac
4. "Does Duolingo Work?": https://jatck.substack.com/p/does-duolingo-work
5. Podcast analysis (anecdotal): https://www.podengine.ai/articles/we-analyzed-300-podcasts-on-duolingoheres-the-core-debate
6. Duolingo Wiki, Exercise (fan-run): https://duolingo.fandom.com/wiki/Exercise
7. Duolingo blog, new home screen design (2022): https://blog.duolingo.com/new-duolingo-home-screen-design
8. UX Collective, critique of the 2022 path redesign: https://uxdesign.cc/down-the-wrong-path-the-disaster-of-the-latest-duolingo-ui-update-a4cdd1e6ea1c
9. Duolingo blog, guide to Practice Hub: https://blog.duolingo.com/guide-to-duolingo-practice-hub/
10. Duolingo blog, ways to practice: https://blog.duolingo.com/ways-to-practice-in-duolingo
11. Android Authority on the Energy system (2025): https://www.androidauthority.com/quitting-duolingo-energy-system-3599842/
12. UC+ Dansk, Duolingo overview: https://ucplusdansk.dk/en/duolingo-what-is-it/
13. Brain Biscuit Learning public pages, read 2026-10-29: https://brainbiscuitlearning.com/ , /method, /levels, /recorded-classes, /ordjagten

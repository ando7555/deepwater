# How competitor language apps schedule and adapt, and what Echo should learn from it

Date: 2026-10-29. Requested by the product owner: "do deep research for the MVP further process,
based on other competitive language learning apps, to understand algorithm drawbacks, to schedule
further phases to develop and have good quality of code." Marketing is paused for now.

This note covers **learning algorithms** (scheduling, mastery estimation, sequencing, adaptive
difficulty, motivation mechanics). UI and content ideas are in
[2026-10-28-amalieteaches-and-duolingo.md](2026-10-28-amalieteaches-and-duolingo.md). The phased
plan that follows from this note is [docs/product/ROADMAP.md](../ROADMAP.md).

Status legend: **Verified** = read in this repository's code on 2026-10-29 (commit `fa67205`).
**Primary** = read in the vendor's own repository, paper or documentation. **Secondary** = from
search-result summaries, reviews or press, not re-read in full. **Anecdotal** = single user reviews.
**From memory** = general knowledge not re-checked in this session. Nothing in this note has been
tested with Echo learners.

## Sources and limits

- The cloud network policy blocks `duolingo.com`, `support.babbel.com` and `docs.ankiweb.net`
  (all three failed to resolve on 2026-10-29). Facts from those sites come from web-search
  summaries of the pages, so they are marked **Secondary**.
- GitHub was reachable: the Duolingo half-life regression repository and the open-source FSRS
  benchmark and algorithm wiki were read directly (**Primary**).
- No account was created on any app. No app was used hands-on.
- Busuu, Memrise and Babbel publish little about their algorithms. What is written below about
  them is what their help pages and reviewers say, not their code.

## 1. The algorithms, app by app

### Duolingo

| What | How it works | Evidence |
|---|---|---|
| Half-life regression (HLR), 2016 | Models the chance of recalling a word as `p = 2^(-Δ/h)`: `Δ` is time since the word was last practised, `h` is a learned "half-life" per learner and word, fitted from the learner's history (times seen, times correct). Duolingo reports 45%+ lower prediction error than baselines and +12% activity in an A/B test, then used it for its word-strength meters. Code is MIT-licensed; 13 million learning traces are public. | Primary (paper P16-1174, GitHub repo); A/B numbers Secondary (Duolingo blog) |
| Birdbrain, from about 2020 | Estimates both learner ability and item difficulty (item-response-theory style) and predicts whether a learner will get a given exercise right. The session generator proposes about 200 candidate exercises and picks and orders them by predicted success, aiming at a "not too easy, not too hard" band (about 70% success is quoted in an interview). Retrained nightly; first run in shadow mode before it shaped lessons. | Secondary (Duolingo blog via search, VentureBeat, IEEE Spectrum) |
| Skill decay ("cracked" skills) | Old skills used to visibly decay and ask to be refreshed. The fan wiki lists decay as discontinued. | Secondary (fan wiki, no date) |
| Linear path, 2022 | Replaced the skill tree with one path. Reports of advanced learners' progress being scrambled or sent back to beginner units. | Secondary, partly sponsored news; Anecdotal reviews |
| Hearts / Energy, streaks, leagues | Mistakes cost lives (later Energy) on the free tier; streaks and leagues drive daily return. Covered in the 2026-10-28 note with references. | Secondary |

**Drawbacks for a learner:** the most refined part (HLR, Birdbrain) optimises **predicted
correctness and engagement**, which is not the same as being able to speak. The visible
memory signal (decay) was removed, so learners cannot see what is fading. Punishing mistakes
(hearts) discourages the effortful guessing that retrieval research says helps. Course
restructures can break a learner's history because progress is tied to course layout rather
than to the items themselves.

### Anki (and SuperMemo SM-2)

| What | How it works | Evidence |
|---|---|---|
| SM-2 (Anki's long-time default) | Each card has an interval and an "ease" multiplier. "Good" multiplies the interval by ease; a failure resets the interval and lowers ease. Anki's FAQ names the common complaint "low interval hell": repeated failures leave a card with tiny intervals for good. Andy Matuschak's note adds that lowering ease after a chance failure slows a card "forever even if you always remember it". | Secondary (Anki FAQ via search); essay Primary |
| FSRS (optional in Anki, from memory since 23.10) | Three numbers per card: **Retrievability** R (chance of recall now), **Stability** S (days until R falls to 90%), **Difficulty** D (1–10). FSRS-6 forgetting curve: `R(t,S) = (1 + factor·t/S)^(-w20)` with `factor = 0.9^(-1/w20) - 1`, 21 trainable parameters fitted per user from review history. The learner picks a **desired retention** (e.g. 90%) and the interval is the time at which predicted R falls to it. | Primary (FSRS wiki on GitHub) |
| Open benchmark | `srs-benchmark` scores algorithms on about 350 million reviews from 9,999 Anki collections by log loss, calibration RMSE and AUC. FSRS-6 log loss 0.346 vs HLR 0.469 (lower is better); FSRS-7 better still. SM-2 numbers are not on the README. | Primary (GitHub README) |

**Drawbacks for a learner:** Anki is a tool, not a course: it has no content order, no
explanations, and the learner must build or import cards. SM-2 punishes failure in a way that
compounds. FSRS needs a few hundred to a few thousand reviews of history to fit personal
parameters well (from memory; defaults are used before that). Four grade buttons confuse new
users (Anecdotal, widely repeated).

### Babbel

| What | How it works | Evidence |
|---|---|---|
| Review Manager | Six "knowledge levels". Level 1 is new, level 6 is long-term memory. A correct review moves an item up one level. Example intervals from Babbel's help page: next day, 4 days, 7 days, 14 days, 60 days, 6 months. Intervals are "based on how often you've reviewed it and how often you get it right". What a wrong answer does is not stated in the sources found. | Secondary (Babbel support page via search; page blocked) |
| Lessons | Fixed, teacher-written lesson sequence with grammar tips; you cannot advance until an answer is right. | Secondary (reviews) |

**Drawbacks:** reviewers call exercises and reviews repetitive, and revisiting earlier
lessons is hard (Secondary, Trustpilot summaries and review sites). The schedule is a fixed
ladder; nothing suggests it is fitted to the learner. Effectiveness claims rest on a
company-funded study (Secondary).

### Busuu

| What | How it works | Evidence |
|---|---|---|
| Vocabulary review | Words are rated weak, medium or strong from drill and quiz results, with a decay over time and spaced repetition. Reviews quiz words in context, and words missed more often come back more often. | Secondary (review sites) |
| Community feedback | Native speakers correct learners' written and spoken answers (from memory; this is Busuu's best-known feature). | From memory |

**Drawbacks:** one learner reports a word stuck at "weak" despite correct reviews, and adding
lesson words to a saved list is "very inconsistent" (Anecdotal, one review on two sites). Most
complaints overall are about billing and the limited free plan (Secondary, automated analysis
of 657 reviews: 66.5% positive). An opaque strength label that does not move is the failure
Echo should avoid.

### Memrise

| What | How it works | Evidence |
|---|---|---|
| Spaced repetition | Reviewers describe effective spaced repetition "without configuration". | Secondary |
| Community courses | User-made courses were removed from the main apps on 2024-03-31 and moved to a separate site (promised until the end of 2025); user word lists were being brought back in 2026. | Secondary (Memrise help pages via search) |

**Drawbacks:** App Store reviews call the removal of community courses a major loss; the AI
conversation exercises are called repetitive; premium is called expensive (Secondary,
automated review summaries). Lesson: when learners have invested in their own material,
taking it away destroys trust.

### Smaller apps

- **Lingvist, Clozemaster:** cloze (fill-the-gap) vocabulary in sentences, frequency-ordered.
  Reviewers say it teaches vocabulary but "not the whole language", multiple-choice
  distractors are sometimes nonsense, and it is hard for absolute beginners (Secondary). Danish
  was not on Lingvist's list in the review found.
- **Speakly:** records the learner's voice for self-comparison only, with no speech
  recognition; a reviewer says this "doesn't really help pronunciation" (Secondary). Echo's
  Speak mode is the same today (self-check), so the same criticism applies to Echo.
- **Amalie Teaches, Brain Biscuit Learning:** covered in the 2026-10-28 note. Neither publishes
  a scheduling algorithm.

## 2. What the research literature supports

These are the well-established findings any scheduler should respect. They are general
memory research, not language-app studies, and not validated for Echo.

1. **Spacing beats massing.** Cepeda et al. 2006 (Psychological Bulletin, 317 experiments)
   found spaced practice improves retention, and the best gap grows with how long you need to
   remember. (Primary abstract via search.)
2. **Retrieval beats re-reading** (the testing effect; Roediger & Karpicke 2006, later
   meta-analyses). The benefit holds even when a retrieval attempt fails, **if corrective
   feedback follows**. (Secondary.)
3. **Forgetting is gradual and predictable enough to model.** HLR and FSRS both model recall
   as a decaying probability, and the open benchmark shows that fitting the curve to history
   predicts recall better than fixed ladders. (Primary.)
4. **A chance failure is not proof an item is hard.** Penalties that compound (SM-2 ease) hurt
   long-term scheduling. (Primary essay, Secondary Anki FAQ.)

## 3. Echo's own algorithm today (Verified in code)

Echo has **two separate schedulers** with different rules.

**A. Lesson patterns** (`EchoService.submitPractice`, table `echo_skill_states`)

- Mastery starts at 0 and moves **+0.2 per correct, -0.2 per wrong** graded answer, clamped
  to 0–1. The frontend calls a pattern "mastered" / "settled" at 0.8
  (`CalmProgress.jsx`, `CoursePath.jsx`, `DailyReview.jsx`).
- The next review is 1, 3, 7 or 14 days, chosen by the count of consecutive correct answers.
- Findings:
  1. **Massed answers count as spaced evidence.** Every submit moves mastery and the
     interval, whenever it happens. Four correct answers in a row within one minute reach 0.8
     ("mastered") and a 7-day interval; a fifth gives 1.0 and 14 days. This is the opposite of
     finding 1 above, and it lets the progress screen overstate learning.
  2. **One miss resets the run to the start.** `correct_streak` goes to 0 and the next review
     is 1 day, however long the pattern was known. This is SM-2's "reset" problem, and it
     contradicts Echo's own card rule (a miss steps back one box) and the "a bad day never
     wipes out progress" principle.
  3. **Mastery never fades.** A pattern at 1.0 stays 1.0 after six months away. It is a
     counter, not an estimate of recall, so it cannot be checked against what learners
     actually remember.
  4. TRANSFER answers and audio confidence are stored but do not affect mastery. That is a
     reasonable choice for unscored writing, not a defect.

**B. Phrase cards** (`EchoCardService`, table `echo_cards`)

- Boxes 0–5 with intervals 0, 1, 3, 7, 14, 30 days. "Knew" moves up one box, "I was close"
  keeps the box and returns tomorrow, "Not yet" moves down one box.
- Findings:
  5. This is a sound Leitner design and already avoids the reset and ease-hell problems.
  6. **No review history is kept.** Each review overwrites `review_box`, `next_review_at` and
     `reviewed_at`; only a miss count and the last miss survive. Without a log of
     (item, time since last review, grade) Echo can never measure whether its schedule works,
     fit FSRS or HLR, or compare against a baseline. This is the single biggest gap for the
     owner's "reusable intelligence" goal (project context section 4, steps 4–5).

**C. Code-quality observations relevant to the plan** (Verified)

- Scheduling rules live inside a 561-line `EchoService` next to SQL and HTTP errors, and
  use `Instant.now()` directly, so they cannot be unit-tested with a fixed clock.
- The 0.8 "mastered" threshold is repeated in three frontend files and is not known to the
  backend.
- The schema is evolved with `create/alter ... if not exists` in `schema.sql`; there is no
  migration tool. Tests run on H2 while production runs on Postgres.
- `spring-kafka` is a dependency but unused (Kafka is not needed for the MVP).
- CI runs backend tests and a frontend build. The frontend has no tests and no linter.

## 4. Drawbacks turned into Echo rules (Proposals)

| Competitor drawback | Echo rule |
|---|---|
| Duolingo optimises predicted correctness and engagement. | Optimise **delayed recall**: the score that matters is "did the learner remember it after a gap". Engagement is a guardrail, not the goal. |
| Duolingo removed visible decay; Busuu's strength label gets stuck. | Show an **honest, moving memory estimate** per item that fades with time and rises with spaced success, labelled as an estimate. |
| Hearts and Energy punish mistakes. | **Mistakes are free.** A miss gives corrective feedback and reschedules; it never blocks practice. |
| SM-2 reset and ease hell; Echo's own lesson streak reset. | **A miss steps back one level, never to the start**, for every item type. |
| Echo counts massed repeats as learning. | **Only the first graded answer per item per day moves the schedule.** Later same-day answers are practice and are logged, but do not raise mastery. |
| Fixed ladders (Babbel, Echo cards) cannot tell if they work. | **Log every review** and evaluate the schedule offline against a baseline before changing it. Adopt FSRS or HLR only if it predicts Echo learners' recall better. |
| Duolingo path restructures scrambled progress; Memrise removed user content. | **Progress belongs to items, not to course layout.** Learner cards and history are never removed by a content change. |
| Cloze apps teach words but not the language; Speakly self-recording does not help pronunciation. | Keep explained patterns and transfer tasks. Do not claim Speak practises pronunciation until speech feedback exists. |
| Effectiveness claims rest on company-funded studies. | No effectiveness claims until a learner pilot with a delayed test. |

## 5. What this does not settle (Open questions)

- Which target retention suits beginners (e.g. 85% vs 90%) is a product choice with a cost in
  daily review load; it needs pilot data.
- Whether lesson patterns and phrase cards should share one memory model. The proposal in the
  roadmap says yes (one scheduler, two item types), but that is a design choice, not evidence.
- How many reviews Echo needs before a fitted model beats a fixed ladder. FSRS uses defaults
  until history exists; the threshold for Echo is unknown.

## References

1. Settles & Meeder 2016, "A Trainable Spaced Repetition Model for Language Learning", ACL: https://aclanthology.org/P16-1174
2. Duolingo HLR code and data (MIT): https://github.com/duolingo/halflife-regression
3. Duolingo blog, "How we learn how you learn": https://blog.duolingo.com/how-we-learn-how-you-learn (blocked here; via search)
4. Duolingo blog, "Learning how to help you learn: introducing Birdbrain": https://blog.duolingo.com/learning-how-to-help-you-learn-introducing-birdbrain (blocked here; via search)
5. VentureBeat, "How Duolingo uses AI in every part of its app": https://venturebeat.com/ai/how-duolingo-uses-ai-in-every-part-of-its-app
6. IEEE Spectrum, "How Duolingo's AI learns what you need to learn": https://spectrum.ieee.org/duolingo
7. Duolingo fan wiki on decay and Korben on the course overhaul (Secondary): https://korben.info/en/duolingo-overhauled-courses-alienated-users.html
8. FSRS algorithm wiki: https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm
9. SRS benchmark: https://github.com/open-spaced-repetition/srs-benchmark
10. Anki FAQ, "What spaced repetition algorithm does Anki use?": https://docs.ankiweb.net/faqs/what-spaced-repetition-algorithm (blocked here; via search)
11. Andy Matuschak, "Simple spaced repetition algorithms mistreat expected failures": https://notes.andymatuschak.org/zKmMYFJ7xhbddLaxTfLYgdB
12. Babbel help, "Memorizing vocabulary": https://support.babbel.com/hc/en-us/articles/360037496932-Memorizing-vocabulary (blocked here; via search)
13. Babbel reviews: https://www.trustpilot.com/review/babbel.com , https://www.alllanguageresources.com/babbel-review/
14. Busuu reviews: https://www.mezzoguild.com/busuu-review/ , https://justuseapp.com/en/app/379972533/busuu-learn-to-speak-german/reviews , https://kimola.com/reports/busuu-language-learning-app-review-141517
15. Memrise help and community courses: https://explore.memrise.com/help , https://explore.memrise.com/community-courses , https://kimola.com/reports/uncover-the-impact-memrise-apps-user-feedback-report-app-store-us-147885
16. Lingvist and Clozemaster reviews: https://discoverdiscomfort.com/?p=1070 , https://ling-app.com/blog/clozemaster-review/
17. Speakly review: https://www.langoly.com/speakly-review/
18. Cepeda et al. 2006, Psychological Bulletin 132(3): https://pubmed.ncbi.nlm.nih.gov/16719566/
19. Roediger & Karpicke 2017 review of the testing effect: https://moodle2.units.it/pluginfile.php/572734/mod_resource/content/1/testing.pdf

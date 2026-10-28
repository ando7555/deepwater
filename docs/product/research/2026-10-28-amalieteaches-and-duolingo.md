# Ideas from Amalie Teaches and Duolingo for Echo

Date: 2026-10-28. Requested by the product owner: take useful content and UI ideas from
amalieteaches.com (not its visual style), add a moving 3D element in Deepwater style (the owner first said a globe, then clarified: the Deepwater logo in 3D, not a globe), and take
Duolingo-like features "the paradoxical way": turn Duolingo's known drawbacks into Echo strengths.

Status legend: **Built** = in this PR. **Proposal** = not built, needs an owner decision.
**Observed** = seen in a source listed below. Nothing here is user-tested.

## Sources and limits

- amalieteaches.com could not be fetched: the cloud environment's network policy blocks the
  domain, and a web search did not index it. Everything below about the site comes **only** from
  two screenshots the owner shared in the project thread on 2026-10-28 (home page and Practice
  page). No lesson text, vocabulary lists or prices were seen. Nothing was copied from the site.
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

Not built: speech capture or pronunciation grading, saving a card straight from a lesson,
a matching mode that uses teacher content, and reminders or notifications.

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

To read the full site, add `amalieteaches.com` and `www.amalieteaches.com` to the cloud
environment's allowed domains and start a new thread.

## References

Owner's screenshots of amalieteaches.com, shared in the project thread on 2026-10-28.

1. CALL-EJ article on Duolingo: https://callej.org/index.php/journal/article/download/419/347/1013
2. Isaacson, Duolingo learner study: https://tfulib.repo.nii.ac.jp/record/794/files/p87-99%20Jonathan%20Isaacson.pdf
3. Technology Assisted Language Education (2024): https://doaj.org/article/cb110689f72d4ec09a48037fba7b99ac
4. "Does Duolingo Work?": https://jatck.substack.com/p/does-duolingo-work
5. Podcast analysis (anecdotal): https://www.podengine.ai/articles/we-analyzed-300-podcasts-on-duolingoheres-the-core-debate

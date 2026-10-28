# Ideas from Amalie Teaches and Duolingo for Echo

Date: 2026-10-28. Requested by the product owner: take useful content and UI ideas from
amalieteaches.com (not its visual style), add a moving 3D globe in Deepwater style, and take
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
| Draggable globe with pins | Dotted 3D globe in Deepwater blue (follows the Echo, Amber and light themes). Pins mark where Echo's languages are official: Danish in Copenhagen and Tórshavn, German in Berlin, Vienna and Zürich. Dashed routes join cities of the same language. Tapping a pin **picks that language**: on sign-up it becomes the onboarding default, in onboarding it sets the language, on the home page it switches the learning language. A row of text buttons does the same for keyboard and screen-reader users. Auto-rotation stops on hover and for `prefers-reduced-motion`. | Built |
| Practice by skill (Read, Listen, Speak, Write) | A "Practice" entry point that lets the learner choose a skill. Echo already has recognition (multiple choice), typed answers and listening (audio lessons with gist and focus steps); Speak would need speech capture and a grading decision. | Proposal |
| Learner-made flashcards | Let learners save a sentence from a lesson as a card that enters the same spaced-review schedule as lessons. | Proposal |
| Matching warm-up | A one-minute matching round from items that are due for review, before the main lesson. | Proposal |
| Four-item navigation | Practice, Lessons, Explore (globe and culture), and Progress instead of Games (see Duolingo below). | Proposal |

## Duolingo drawbacks turned into Echo strengths (Proposal)

| Duolingo drawback | Evidence | Echo's opposite |
|---|---|---|
| Streaks can turn learning into "keep the streak alive". | Learner interview studies [1][2] | **No streak guilt.** Reward evidence of learning (patterns mastered, due reviews cleared). A missed day reschedules reviews instead of resetting a counter. |
| Little explicit grammar explanation. | Learner studies [1][2] | **Explain why.** Every Echo lesson already carries an explanation of how the sentence is built. Keep that as a rule. |
| "You get good at playing Duolingo, not at speaking." | Podcast analysis, anecdotal [5] | **Transfer tasks.** Echo's TRANSFER exercise asks the learner to use the pattern in their own situation; make it the end of every lesson. |
| Unrealistic sentences. | Learner studies [1][2] | **Real voices.** Music and podcast lessons used with the creator's permission, reviewed by a teacher. |
| Effectiveness claims rest on company-commissioned studies. | Published critique of Duolingo's own efficacy papers [1][3][4] | **Honest progress.** Show the learner's own mastery and review data, and make no "one semester in N hours" claims until Echo has independent evaluation. |
| AI conversation practice feels canned. | Podcast analysis, anecdotal [5] | **Teacher-reviewed AI.** AI drafts exercises, and a teacher approves them before learners see them (the plan already in the architecture docs). |
| Games and leagues compete for attention. | Learner studies [1][2] | **Calm by default.** Short warm-ups are fine, but there are no leaderboards. Progress is compared with your past self only. |

## Suggested next step

Pick one proposal for a spec. The smallest valuable one is probably the **Practice hub with
Read, Listen and Write** (Speak later), because Echo's backend already grades those task types.

To read the full site, add `amalieteaches.com` and `www.amalieteaches.com` to the cloud
environment's allowed domains and start a new thread.

## References

Owner's screenshots of amalieteaches.com, shared in the project thread on 2026-10-28.

1. CALL-EJ article on Duolingo: https://callej.org/index.php/journal/article/download/419/347/1013
2. Isaacson, Duolingo learner study: https://tfulib.repo.nii.ac.jp/record/794/files/p87-99%20Jonathan%20Isaacson.pdf
3. Technology Assisted Language Education (2024): https://doaj.org/article/cb110689f72d4ec09a48037fba7b99ac
4. "Does Duolingo Work?": https://jatck.substack.com/p/does-duolingo-work
5. Podcast analysis (anecdotal): https://www.podengine.ai/articles/we-analyzed-300-podcasts-on-duolingoheres-the-core-debate
- Globe land data: Natural Earth 1:50m land (public domain), via the world-atlas npm package.

# Echo roadmap from the MVP onward

Date: 2026-10-29. Status: **Proposal** for the product owner to approve, change or reorder.
Evidence: [competitor algorithm research](research/2026-10-29-competitor-learning-algorithms.md)
and [Amalie Teaches and Duolingo research](research/2026-10-28-amalieteaches-and-duolingo.md).

This updates section 7 of the
[materials-based proposal](DEEPWATER_MATERIALS_BASED_PROPOSAL.md#7-implementation-roadmap).
Phases 0–3 of that table are largely built (content review workflow, per-skill state, review
boxes, end-to-end web flow, Danish A1 course drafts). This roadmap replaces its phases 4 and 5
for Echo; Amber and AI fashion discovery (its phases 6 and 7) are unchanged and come after.

## Principles for every phase

1. **MVP-level scope.** Each phase fixes a learner-visible problem or produces evidence. No
   feature is added because a competitor has it.
2. **Delayed recall is the outcome that counts.** Engagement numbers are guardrails.
3. **Evidence before claims.** Nothing is called "better than Duolingo / Busuu / Memrise /
   Amalie Teaches" until a pilot measures it.
4. **Modular monolith, no Kafka, no AI calls** until a phase below names a reason.
5. **Code quality rules** (section "Engineering rules") apply to every PR from Phase 1.

## Phase 1: One honest memory model (recommended next)

**Problem.** Echo's lesson scheduler counts massed repeats as learning (four quick correct
answers make a pattern "mastered"), resets to day 1 after one miss, and never lets mastery
fade. Phrase cards keep no review history. So the progress screen can overstate learning and
Echo cannot measure whether its schedule works. (Verified in code; see research note section 3.)

**Scope.**

1. Append-only **review log** for both item types (lesson pattern and phrase card): learner,
   item type and id, grade, time since the previous review of that item, the interval that
   was scheduled, and timestamp. No answer text is copied into it.
2. **One scheduler** in plain Java (no Spring, no SQL, a `Clock` passed in) used by both
   lessons and cards. It keeps today's card ladder (0, 1, 3, 7, 14, 30 days) as the baseline.
3. **Same-day rule:** only the first graded answer per item per learner per day moves the
   schedule and mastery. Later answers that day are logged as practice.
4. **Miss rule:** a miss steps back one level, never to the start, for lessons as well as cards.
5. **Fading estimate:** the learner sees an estimated chance of remembering each pattern
   today, which falls with time since the last review, labelled as an estimate. The
   "mastered" threshold lives in the backend once, not in three frontend files.
6. **Offline evaluation command** that replays the review log and reports, per scheduler, the
   predicted vs actual recall (log loss and calibration by bucket). For now it runs on
   synthetic test histories; real data comes in Phase 3.

**Acceptance criteria.**

- AC1-1 Four correct answers to the same pattern within one day raise mastery once, not four
  times, and leave the next review at the first interval. (Automated test.)
- AC1-2 A pattern at the 14-day level that is missed goes to the 7-day level, not to 1 day.
  (Automated test.)
- AC1-3 A card and a lesson pattern with the same history get the same next interval.
  (Automated test on the shared scheduler.)
- AC1-4 Every graded lesson answer and every card review writes exactly one review-log row with
  a correct time-since-last-review; deleting an account deletes its rows. (Automated test.)
- AC1-5 A pattern last reviewed 60 days ago shows a lower estimate than the same pattern
  reviewed yesterday. (Automated test with a fixed clock.)
- AC1-6 The evaluation command prints log loss and a calibration table for the baseline on a
  checked-in synthetic dataset, and its output is asserted by a test.
- AC1-7 Existing learners keep their current mastery and next review dates after the change
  (migration test on a copy of the schema).
- AC1-8 Scheduler package has no Spring or SQL imports, enforced by an architecture test.

**Out of scope.** FSRS or HLR, AI, speech recognition, reminders, new content types.

**Risks.** Changing how mastery moves may lower numbers learners already see; the release note
must say why. The same-day rule needs a day boundary. The profile has no time zone today (only city and
country), so the proposal is the browser's time zone sent with each answer, falling back to
UTC (open question for the owner).

## Phase 2: Content approved and a first learner pilot

**Problem.** The Danish A1 course exists only as teacher drafts, and no design choice has been
tested with a real learner.

**Scope.** Owner or teacher approves the drafts; a short delayed-recall check (a few items
from earlier lessons, asked 7+ days later) is added to the daily review; a consented pilot of
5–15 adult Danish beginners runs for 4 weeks with a written protocol fixed before it starts.

**Acceptance criteria (draft).** Protocol and consent text approved by the owner before the
first learner joins; at least 5 learners complete 3 weeks; the report gives delayed-recall
accuracy, review completion, weekly return and the learners' top complaints, with no
comparison claim against other apps.

## Phase 3: Evidence-driven scheduling

**Problem.** A fixed ladder cannot adapt to a learner or an item. HLR and FSRS predict recall
better on other apps' data (Primary: open benchmark), but that is not proof for Echo.

**Scope.** Using the pilot's review log, compare the Phase 1 baseline with FSRS default
parameters (and optionally HLR) in the evaluation command. Adopt the candidate behind a
setting only if it is better on log loss and calibration on held-out Echo reviews. Add a
learner-facing "how much do you want to remember" setting only if the pilot shows review load
is a complaint. Check the licence of any FSRS implementation before use.

**Acceptance gate.** Written comparison on real Echo data; owner decision recorded as an ADR.

## Phase 4: Adaptive difficulty and sequencing

Within a session, choose the next item so the learner succeeds most of the time but not always
(a target band to be set from pilot data, not copied from Duolingo's quoted ~70%), interleave
due reviews with new material, and respect lesson prerequisites. Rule-based first, explained to
the learner ("this came back because…").

## Phase 5: AI with a teacher in the loop

The plan already in the architecture docs: AI drafts exercises and accepted-answer variants,
a teacher approves before learners see them, behind a provider port with a non-AI fallback.
Speech feedback is considered here, and only then may Speak be described as pronunciation
practice.

## Phase 6: Reuse for Amber

Only after Phases 1–3 show the memory model works for Echo: extract the learner-model and
scheduler package as a shared Deepwater module with product-independent interfaces.

## Engineering rules (from Phase 1 onward)

These are proposals for the owner to approve; each is cheap to start.

| Rule | Why | First step |
|---|---|---|
| Learning rules live in plain Java classes with a passed-in `Clock`, no SQL or Spring. | Rules become unit-testable and reusable for Amber. | Phase 1 scheduler package. |
| Architecture tests (ArchUnit) guard module boundaries. | Keeps the modular monolith modular. | Rule: scheduler package has no framework imports. |
| Schema changes go through versioned migrations (Flyway), not `alter ... if not exists`. | Repeatable upgrades on Postgres, testable data migrations. | Baseline migration from today's `schema.sql`. |
| One integration test suite runs on Postgres (Testcontainers) in CI. | Production is Postgres, tests are H2 today. | Run the existing MVP end-to-end test on Postgres. |
| Frontend has unit tests (Vitest) and a linter in CI. | The frontend has no tests now. | Tests for `memory.js` and `cardTools.js`. |
| Coverage report (JaCoCo) in CI, no hard threshold at first. | See what is untested before setting a gate. | Publish the report as a CI artifact. |
| Remove unused dependencies (`spring-kafka`). | Kafka is not in the MVP; less to patch. | One-line build change. |
| Split `EchoService` (561 lines) by responsibility as code is touched. | Lessons, grading and scheduling change for different reasons. | Phase 1 moves scheduling out. |
| Every PR: spec or issue, tests named after acceptance criteria, ADR for major decisions. | Matches the existing delivery plugin and Definition of Done. | Already in `deepwater-developer`. |

## Decisions needed from the owner

1. Approve, reorder or cut these phases.
2. Phase 1 same-day rule day boundary: the learner's browser time zone (proposal) or UTC.
3. Who can approve the A1 course drafts and recruit pilot learners (Phase 2).

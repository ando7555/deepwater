# Deepwater product scope and acceptance

**Status:** product definition draft 0.1  
**Decision needed:** review the scope and acceptance gates before treating the current demo as an MVP.  
**Product family:** Deepwater  
**First application:** Echo  
**Second application:** Amber  
**Future application:** AI fashion (working label only)

> The evidence-based product, algorithm, acceptance, and delivery proposal is now
> in [Deepwater materials-based proposal](DEEPWATER_MATERIALS_BASED_PROPOSAL.md).
> That proposal uses the supplied Danish exports and should be the primary review
> document; this earlier draft captures the initial product framing.

## Product intent

Deepwater helps people understand patterns, express themselves, and make more
intentional choices. Echo applies this to language learning. Amber applies it to
mutual human connection. A future fashion application may apply it to personal
style and wardrobe choices.

The products can share a way of helping a person reason through a choice. They do
not share private content, user scores, recommendations, or product rules by
default. The cross-product idea is a reusable interaction method, not one universal
ranking model.

## Shared analytical method

Use this as a hypothesis to validate, not as a claim that learning and dating are
the same problem:

1. **Frame the intent.** What is the person trying to learn, express, or decide?
2. **Gather allowed evidence.** Use reviewed lesson material, explicit preferences,
   or user-selected wardrobe context. Record provenance and access scope.
3. **Find a pattern or candidate.** Compare relevant structures or constraints.
   Separate observed evidence from an AI-generated hypothesis.
4. **Explain the comparison.** Show what is similar, what differs, and how certain
   the system is. Let the person inspect the reason.
5. **Offer a reversible choice.** Practise, revise, save, pass, or request a
   connection. A recommendation never acts on the person's behalf.
6. **Learn from explicit feedback.** Use correction, confidence, preference edits,
   or the outcome the person chooses to share. Allow reset and deletion.

### Product-specific interpretation

| Stage | Echo | Amber | Future AI fashion |
|---|---|---|---|
| Intent | Learn a construct or communicate a meaning in Danish | Meet someone under chosen boundaries and intent | Choose an outfit for a user-stated occasion or preference |
| Evidence | Teacher materials, reviewed examples, learner response | Profile fields and preferences each person chose to disclose | User-selected garments, occasion, style preferences, and constraints |
| Analysis | Compare grammatical function and form across languages; identify a transferable pattern and its limits | Identify explicit compatibility in intent/preferences; explain possible shared ground without predicting attraction | Compare outfit options against explicit constraints and explain trade-offs |
| Human choice | Predict, practise, revise, and self-rate | Pass, hide, send an invitation, accept, or decline | Keep, edit, or reject a suggestion |
| Feedback | Delayed transfer, correction, confidence, and learner reflection | Preference edits and explicit safety/experience feedback | Preference edits and selection/rejection reasons |
| Must not do | Present an unreviewed hypothesis as a grammar fact | Infer consent or human worth; expose lesson answers; open chat without mutual acceptance | Rank bodies or infer sensitive traits; use an image without consent |

No Amber feature should optimize for message volume or time spent as a proxy for
healthy connection. No model may convert a person's refusal into a negative score.

## Scope and sequence

### Foundation: define before building

- Review this product scope, method, domain boundaries, and acceptance criteria.
- Import only the Danish class material the user supplies and is permitted to use.
- Inventory source, author/teacher attribution, date, language level, intended
  audience, and reuse restrictions for every imported lesson.
- Decide the first learner profile, first grammar/pronunciation objectives, and
  comparison languages from the supplied material rather than assuming them.
- Agree which AI tasks are allowed and how a human reviews language facts.

### Echo first: language-learning MVP

In scope:

- Danish learner onboarding with a self-selected goal and baseline confidence.
- A small, reviewed sequence of lessons derived from supplied class material.
- Each lesson has a real communicative intent, an explained structure, at least
  one evidence-backed cross-language comparison when suitable, a limitation or
  counterexample, active recall, original learner production, and reflection.
- Progress evidence that distinguishes exposure, recall, supported production,
  and later transfer. No single activity count is described as mastery.
- An authoring/review path that stores source provenance and review status.

Out of scope for the first Echo pilot unless later accepted: open-ended AI tutoring,
automated pronunciation scoring, a full CEFR curriculum, unrestricted lesson
imports, social discovery, and claims of fluency from a short test.

### Amber second: mutual-connection MVP

Start only after Echo's analytical method and consent/data boundaries have been
reviewed. In scope for a separate release:

- Adults create a distinct, optional Amber profile and choose visibility,
  preferences, and connection intent.
- Suggestions use only fields each person explicitly chose to share and display
  the reasons in plain language.
- Either person can pass or withdraw. A private conversation opens only after
  both people explicitly accept the same invitation.
- Block, report, unmatch, profile visibility, account deletion, and abuse response
  are designed and tested before a public pilot.
- Echo contributes only coarse, opt-in skill signals if the user chooses; raw
  answers, class notes, recordings, and teacher messages stay in Echo.

Out of scope: scraped profiles, inferred attraction or personality, unsolicited
messages, location precision beyond the user's chosen coarse area, cross-product
sharing by default, and automated eligibility decisions.

### AI fashion later

Keep this as a bounded future product. Do not store fashion or body-image data in
Echo or Amber schemas. First validate whether users want help with wardrobe
organization, outfit combinations, style vocabulary, or another specific job.
Image handling, retention, consent, and fairness require their own acceptance
criteria before implementation.

## Echo lesson and learning algorithm

The first algorithm is evidence-led and explainable. It should compare linguistic
structures, not merely translate isolated words.

### Lesson representation

Each reviewed lesson should include:

- target language, learner-facing meaning, communicative situation, and level;
- construct and grammatical function (for example modality, word order, tense,
  agreement, case, or aspect) as chosen by the lesson author;
- one or more source-backed example sentences with translation and provenance;
- structural annotation for each example (constituents, order, morphology, and
  relevant sound/stress features where applicable);
- a comparison relation: same function, similar form, partial transfer, false
  friend, or meaningful contrast;
- an explanation of the shared pattern, an exception or boundary, and confidence;
- retrieval prompt, production task, rubric, answer feedback, and delayed-transfer
  prompt;
- reviewer, review date, material rights/permission, and version.

### Learner loop

1. Elicit prior knowledge and confidence before showing the explanation.
2. Present a short situation and ask the learner to predict how the target
   language expresses the intended meaning.
3. Compare the learner's prediction with reviewed examples and a cross-language
   structural map. Name both transfer opportunities and differences.
4. Ask for a new expression without copying the source example.
5. Give feedback against a visible, lesson-specific rubric. Separate meaning,
   structure, form, and register; cite the example or rule behind each correction.
6. Ask the learner to self-rate confidence and optionally revise.
7. Revisit the construct later in a different context. Track supported practice
   separately from delayed transfer.

The MVP must not claim a reliable automated grammar/pronunciation judgment until
an evaluated model or rubric demonstrates it. Initially, AI may help an author
draft a comparison or feedback candidate, but a qualified reviewer must approve
it before it becomes learner-facing content. AI output must link to the approved
lesson evidence and abstain when evidence is missing or conflicting.

### Measurement

Primary Echo learning measure: performance on a new, delayed task for the same
construct, scored with a reviewed rubric. Compare with the learner's baseline and
report sample size and uncertainty. Supporting measures: recall, error patterns,
self-rated confidence calibration, lesson completion, and voluntary return.
Do not equate streaks, session time, or AI confidence with learning.

## Acceptance criteria

These are product acceptance gates. The current local prototype does not meet them
all. Each criterion should become an executable test, review checklist, or pilot
protocol before the corresponding release.

### A. Content trust and source handling

**AC-CONTENT-1 — traceable lesson fact**  
Given a learner-facing grammar or pronunciation claim, when a reviewer opens the
lesson record, then the claim links to its example/source, has an identified
reviewer and version, and is either approved or withheld from learners.

**AC-CONTENT-2 — preserve uncertainty**  
Given two examples that appear similar across languages, when their structures or
usage differ, then the comparison labels the shared function and the difference;
it must not state that forms are interchangeable.

**AC-CONTENT-3 — material permission**  
Given imported class material, when it is published to learners, then its source,
author/teacher attribution, permission/rights status, and allowed audience are
recorded. Unclear rights block publication.

**AC-CONTENT-4 — review gate**  
Given an AI-drafted explanation with no reviewer approval, when a learner requests
the lesson, then the draft is not served as authoritative learning content.

### B. Echo learning outcomes

**AC-ECHO-1 — goal and baseline**  
Given a new learner, when onboarding is complete, then the learner has selected a
goal and target-language level or has explicitly marked it unknown; the system
records a baseline confidence/knowledge check without presenting it as a grade.

**AC-ECHO-2 — analytical comparison**  
Given a reviewed lesson with a valid comparison, when the learner opens it, then
the learner can see the intended meaning, target structure, comparison structure,
what transfers, what differs, and the source/review status.

**AC-ECHO-3 — original production**  
Given a completed explanation, when the learner practises, then the task requires
an original expression in a new or varied context and records the response against
the lesson version and rubric.

**AC-ECHO-4 — honest feedback**  
Given submitted work, when the system returns feedback, then every correction is
traceable to a reviewed rule/example or is labeled as a tentative suggestion; the
learner can disagree, revise, or skip without losing progress.

**AC-ECHO-5 — transfer evidence**  
Given a learner has practised a construct, when a later transfer task is due, then
the task differs from the worked example and progress distinguishes its result
from lesson completion and supported practice.

**AC-ECHO-6 — no mastery overclaim**  
Given limited or missing assessment evidence, when the learner views progress,
then the system shows what was observed and does not claim mastery or fluency.

### C. Amber consent and safety

**AC-AMBER-1 — separate opt-in**  
Given an Echo account, when its owner has not opted into Amber, then no Amber
profile is visible and no Echo lesson response is available to Amber.

**AC-AMBER-2 — explainable suggestion**  
Given two eligible profiles, when Amber suggests a possible connection, then it
shows the explicit shared preferences/intent that produced the suggestion and
provides pass/hide controls.

**AC-AMBER-3 — two-sided acceptance**  
Given one person has sent an invitation, when the recipient has not accepted,
then neither person can access a private conversation. After both explicitly
accept, the conversation opens; either can leave or block at any time.

**AC-AMBER-4 — decline is final for that invite**  
Given a recipient declines or passes, when the sender views state, then no private
reason or sensitive recipient data is exposed and no further message is sent by
the system for that invitation.

**AC-AMBER-5 — safety controls**  
Given a person blocks or reports another account, when the action is confirmed,
then discovery and contact are stopped according to the published policy, the
report is routed to an authorized review process, and the reporter sees a clear
confirmation without exposing the report to the reported person.

**AC-AMBER-6 — data minimization**  
Given a user has not explicitly shared a language skill with Amber, when a
suggestion is computed, then the skill and underlying Echo activity are excluded.

### D. Shared product/platform invariants

**AC-PLATFORM-1 — user control**  
Given an AI-generated analysis or recommendation in any Deepwater application,
when the user sees it, then its reason and uncertainty are available and the user
may edit, reject, or dismiss it; the system cannot perform a consequential action
without explicit user action.

**AC-PLATFORM-2 — domain isolation**  
Given an operation in Echo, Amber, or future fashion, when authorization is
checked, then that product's policy and data scope are enforced. A shared account
does not imply shared content access.

**AC-PLATFORM-3 — deletion and export**  
Given an authenticated person requests an export or deletion, when the request is
accepted, then the system shows its scope/status and applies the documented
retention rules across primary records and derived projections.

**AC-PLATFORM-4 — auditability**  
Given a consent, visibility, moderation, or AI publication state changes, then the
system records who/what changed it, when, the applicable version, and the reason
without logging secrets or unnecessary lesson/message contents.

### E. MVP quality gates

- A new user can complete the Echo core lesson flow on supported web browsers using
  keyboard and screen reader, without horizontal overflow at mobile widths.
- Required API paths have authenticated/unauthenticated tests, input validation,
  authorization tests, and SQL persistence tests.
- Seed/sample records are unmistakably labeled and cannot be mistaken for real
  people or used for real invitations.
- Product analytics use documented events with consent and data minimization;
  no message contents or raw lesson answers are sent to analytics by default.
- The release has a privacy notice, support/report route, backup/restore check,
  deployment rollback plan, and a named owner for incidents.

## Product metrics and guardrails

| Product | Outcome measure | Guardrails |
|---|---|---|
| Echo | Delayed transfer on new prompts, change from baseline, retention of concepts | Incorrect correction rate, reviewer disagreement, confidence miscalibration, accessibility completion gaps |
| Amber | Voluntary mutually accepted connections and user-reported quality | Reports/blocks, unwanted-contact rate, response pressure, disparate exposure, ease of leaving |
| AI fashion | User-selected suggestions kept or edited for a stated need | Body-image harms, privacy incidents, representation gaps, unwanted image retention |
| Shared | User control, successful export/deletion, clear explanations | Unauthorized cross-product access, unexplained recommendations, unresolved safety reports |

Set numerical pilot targets only after a baseline, target cohort, duration, sample
size, and decision owner are agreed. Do not fabricate thresholds in advance.

## Open decisions to resolve with the Danish class material

1. Which Danish learner level and first communicative goals should the pilot serve?
2. Which comparison language(s) are useful for this learner group and supported by
   the materials? Is English a bridge language, target language, or neither?
3. Which source formats will be supplied (Notion export, PDFs, teacher notes,
   audio, exercises), and what reuse permissions apply?
4. Who can review grammar/pronunciation claims and approve learner-facing lessons?
5. What evidence can realistically measure transfer in a small pilot?
6. What is Amber's first audience, boundaries, age/eligibility handling, moderation
   staffing, and incident response before any public release?
7. What fashion user problem is valuable enough to justify a third data domain?

### Preparing the Notion export

From a lesson page in Notion, use the page menu (`•••`) → **Export** → choose
**Markdown & CSV** and include subpages when the material is organized into a
course. Upload the resulting ZIP here. PDF is also useful when page layout or
handouts matter. Include linked images/audio only when you have permission to
reuse them. A share link works only if a Notion integration is connected and the
page is accessible in this task; do not share workspace credentials.

For each lesson, useful context is: course/unit name, learner level, lesson goal,
grammar or pronunciation topic, original Danish example, teacher's explanation,
translation or comparison language (if already supplied), exceptions/corrections,
source attribution, and whether the material may be adapted into a product. You can
send a small representative lesson first; we can design the extraction template
before processing the rest. Remove student names, private messages, grades, or
other personal learner records unless they are essential and you explicitly want
them considered.

## Release decision

The local code demo is not the product acceptance baseline. A release candidate is
ready for review only when the relevant criteria above are mapped to test IDs or a
review protocol, the evidence is attached, known failures are listed, and the
product owner explicitly accepts any remaining gaps. Cloud deployment remains a
separate user-confirmed step.

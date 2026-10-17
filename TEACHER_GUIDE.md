# Echo teacher workflow and coding guide

This guide is for the product owner acting as Echo's teacher and content approver. The app does not generate course material. Learners only see lessons that you deliberately publish.

A fresh local database imports the existing Danish starter package from `backend/src/main/resources/echo/catalog/v2-time-first.da.json` into SQL as a **draft**. If you already had this starter lesson in your local database, the additive schema update leaves it in `DRAFT`. Review it in the teacher queue and publish it yourself before using it as a learner. No German lesson is included.

## Enable your teacher account

In PowerShell, set the teacher email before starting the backend:

```powershell
$env:DEEPWATER_TEACHER_EMAIL = 'your-email@example.com'
./gradlew.bat :backend:bootRun
```

Register using that exact email, or sign out and sign back in if the account already exists. The API compares the signed-in account email with this server-side setting. A role supplied by the browser is never trusted. Leave the setting empty to disable teacher tools.

## Add and approve a lesson

1. Sign in with the configured teacher account and finish learner onboarding so Echo opens the workspace.
2. Choose **Open workspace** under **Teacher workspace**.
3. Add a normal lesson as JSON using the shape below, or use **Create a music or podcast draft** for an audio lesson. Choose Danish or German. Record the source and permission/provenance; do not paste private messages or material you do not have permission to use.
4. Save the draft. It is visible in your queue, but learners cannot see it.
5. Review the language, explanation, expected answers, feedback, and source. Select **I approve & publish** only when you have checked the whole lesson.
6. Switch your learner-language selector to preview the published language as a learner.

```json
{
  "languageCode": "de",
  "title": "A teacher-approved lesson title",
  "level": "A1",
  "topic": "German sentence structure",
  "objective": "Describe one observable pattern the learner can use.",
  "explanation": "Explain the pattern, its scope, and one useful example.",
  "sourceReference": "Name the permitted source, page or class date, and rights/permission note.",
  "skillKey": "german.sentence-structure.time-fronting.v1",
  "exercises": [
    {
      "taskType": "SELECT",
      "prompt": "Choose the sentence that follows the pattern.",
      "expectedAnswer": "A",
      "feedbackCorrect": "Explain why this answer follows the pattern.",
      "feedbackIncorrect": "Point the learner back to the relevant structure.",
      "options": [
        { "key": "A", "text": "Teacher-checked example sentence." },
        { "key": "B", "text": "Teacher-checked contrast sentence." }
      ]
    },
    {
      "taskType": "TEXT",
      "prompt": "Complete a short teacher-checked sentence: ___",
      "expectedAnswer": "the main accepted answer",
      "acceptedAnswers": ["another correct answer", "a second correct variant"],
      "feedbackCorrect": "Explain the cue that makes the answer fit.",
      "feedbackIncorrect": "Offer a structural hint, not just the answer."
    },
    {
      "taskType": "TRANSFER",
      "prompt": "Write a new example and check it against the pattern."
    }
  ]
}
```

The sample sentences above are placeholders, not verified German teaching content. Replace them with examples you have checked before approving. `SELECT` and `TEXT` use a deterministic answer key; keep the expected answer aligned with the exercise. Matching ignores capitals, punctuation and extra spaces. For `TEXT`, list any other correct answers in the optional `acceptedAnswers` (up to 10). An answer that is close but not accepted (only the special letters differ, the right words are in the wrong order, or one letter is off) is still marked incorrect, and the learner sees a short suggestion labelled as automatic. The suggestion never reveals the answer. `TRANSFER` is stored for reflection and is not automatically graded.

## Create a music or podcast learning path

The teacher workspace includes a dedicated audio lesson form. Select **Music** or
**Podcast**, the target language, and one editorial band: A0–A1 beginner, A2
elementary, B1–B2 intermediate, or C1–C2 advanced. This band is curation guidance,
not certified CEFR placement. Rate speech rate, vocabulary density, idiom density,
pronunciation/reduction load, and topic familiarity separately from 1 to 5. Echo
presents these dimensions separately rather than calculating one opaque difficulty
score.

Write a short context prompt, a first-listen gist question, and a focused prompt
for a phrase, sound, or structure. For a grammar point noticed in a song, compare
it with an approved everyday-language example and label poetic, colloquial, or
dialect forms. For a podcast, identify relevant topic/register/speech features
without treating one speaker as the standard for a whole language.

In **Source permissions**, select a rights basis and enable each permitted use
separately. A source-page link is mandatory for publication in this version. Set a
direct playback URL only when in-app playback is allowed; otherwise learners get
the external source link. Transcript text is accepted and exposed to learners only
when **Echo may display this transcript or lyric excerpt** is checked. Add a
permission record or terms reference and an expiry date if one applies. Unknown or
expired rights leave the lesson as a private draft; approval will be rejected
until the source-link permission and evidence are verified. Saving a draft does
not itself establish rights.

After saving, read the whole lesson in **Lesson queue**, verify each prompt, source,
and permission, then explicitly choose **I approve & publish**. Learners see only
published material whose rights have not expired. Audio reflections record the
step type, transcript-support use, confidence, and response length; Echo does not
retain the free-text reflection or automatically score listening, grammar, or
accent. Echo suggests an audio item using the learner's stated goal and starting
familiarity, explains the reason, and asks for a context-supported repeat when the
learner reports low confidence on the first gist listen. A permitted transcript
unlocks after that first gist step.

The audio form does not fetch or copy media. It supports a direct browser audio
control only for a permitted direct audio URL; otherwise it links out to the source
page. No song or podcast corpus is bundled with the repository.

## Revise a published lesson

Create a new draft with the same fields and add `"revisionOf": "<published-lesson-id>"`. Copy the id from the teacher queue. The API assigns the next version number and preserves lesson order. Publishing the revision archives its parent so learners see the newly approved version only. The older row remains in the teacher queue for traceability. A draft cannot be edited in place in this slice; create a fresh draft or a revision.

## Where the code lives

- `frontend/src/main.jsx` — onboarding, language switch, learner course, and teacher workspace. The JSON form sends structured fields to GraphQL; it does not decide correctness.
- `backend/src/main/resources/graphql/schema.graphqls` — typed API contract, including the learner and teacher operations.
- `backend/src/main/java/com/deepwater/api/DeepwaterGraphQlController.java` — maps API calls to services and checks teacher identity for authoring actions.
- `backend/src/main/java/com/deepwater/echo/EchoService.java` — deterministic lesson/audio persistence, rights validation, review-state transitions, learner filtering, answer checking, and review scheduling.
- `backend/src/main/java/com/deepwater/platform/PlatformService.java` — accounts, sessions, and the server-side teacher-email check.
- `backend/src/main/java/com/deepwater/platform/DemoData.java` — idempotently imports the JSON starter package into SQL as a draft, never as published course content.
- `backend/src/main/resources/schema.sql` — relational tables and safe additive columns for existing local data.
- `backend/src/test/java/com/deepwater/MvpEndToEndTest.java` — tests draft visibility, teacher authorization, audio rights/transcript gates, publication, language choice, and practice.

## A good first code contribution

The manual JSON form is deliberately a narrow first authoring tool. A useful next contribution is a content-import validator that reads a JSON file, validates it against the same rules as `EchoService.validateDraft`, and reports row/field errors before saving. Keep validation in a shared backend class and call it from both GraphQL and import code; do not duplicate answer-grading rules in React. Add tests for malformed language codes, missing provenance, invalid option keys, and transfer exercises with answer keys.

To run local checks from the repository root:

```powershell
./gradlew.bat :backend:test
cd frontend
npm run build
```

## Current limitations

- Danish and German are selectable, but there is no German source corpus bundled. German appears as a clear empty state until you author and publish its first lesson.
- The current diagnostic quiz was removed because it was Danish-only and hardcoded. Learners choose their own starting familiarity. A future diagnostic should be lesson-backed, language-specific, and teacher-approved.
- `SELECT` and `TEXT` support one exact answer in this slice. Accepted variants, richer grammar checks, and AI suggestions need separate design and tests. Audio reflections are not automatically scored; selected content still requires human language and rights review.
- The audio interface has no built-in licensed catalog, transcript importer, provider SDK integration, speech recognition, pronunciation scoring, or full audio analytics. It supports reviewed source links and permitted direct audio URLs only.
- This local prototype is not yet production identity, privacy, or deployment infrastructure.

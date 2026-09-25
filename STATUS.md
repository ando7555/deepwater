# Deepwater implementation status

This branch contains a local, end-to-end Echo MVP slice. It is a review build,
not a public release or a teacher-approved Danish course.

Implemented in this slice:

- Account registration and sign-in with per-account Echo records.
- Danish goal and self-reported familiarity onboarding, plus a three-question
  diagnostic that selects a guided, standard, or challenge route. The result is
  not presented as a CEFR placement.
- One source-linked A0 word-order lesson based on the 5 August class note, with
  teacher-review status visible in the UI.
- Deterministic multiple-choice and exact short-answer checks; free-writing
  transfer is stored without automatic correctness or AI grading.
- Per-account attempts, a bounded mastery estimate, and 1/3/7/14-day review
  scheduling. GraphQL exposes no answer key and requires authentication.
- The supplied logo assets and existing Deepwater visual direction remain in use.

Not included or still required:

- Danish teacher approval of lesson wording, examples, and diagnostic answers;
  this lesson is marked `Draft — Danish teacher review required`.
- More lessons, reviewed audio/pronunciation support, a content authoring/review
  workflow, delayed-transfer study, validated assessment, and accessibility/user
  testing with learners.
- Production privacy controls such as export/deletion, rate limiting, operational
  monitoring, retention policy, and production identity hardening.
- PostgreSQL verification, deployment infrastructure, Kafka, Azure Cosmos/vector
  retrieval, or AI integration. None is needed for this first local slice.
- Amber and AI fashion flows; they remain separately scoped future products.

Run locally from the repository root with Java 25 and Node 22:

```powershell
./gradlew.bat :backend:bootRun
```

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5173
```

Open `http://127.0.0.1:5173`. The backend defaults to a local H2 database. Do not
enter sensitive personal, relationship, or class-message data into this prototype.

Verification on 25 September 2026: `:backend:test` and `:backend:bootJar` pass;
`npm run build` passes. A live local smoke flow verified registration, the
starting diagnostic, the source-linked lesson, a correct answer, mastery update,
and progress retrieval. The review demo is open at `http://localhost:5173/` and
currently uses the backend on `127.0.0.1:8082` with an in-memory database; its
demo records disappear when that backend process stops.

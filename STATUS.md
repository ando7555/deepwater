# Deepwater MVP implementation status

The local Echo slice now supports teacher-controlled content for Danish and German. Lessons are stored in SQL; a JSON catalog package seeds one Danish lesson as an unapproved draft. Learners only receive published records matching their selected language.

Implemented:

- Danish/German selection at onboarding and a persistent language switch in the learner workspace.
- A learner profile with a personal goal and self-described starting familiarity. The former three-question Danish-only diagnostic was removed.
- A teacher workspace gated by the server-side DEEPWATER_TEACHER_EMAIL setting.
- One existing Danish starter lesson imported as a teacher-only draft on an empty database.
- Structured lesson drafts with language, source reference, skill key, learning explanation, objectives, and typed exercises.
- Explicit teacher publication, immutable revision creation, version numbers, and archiving of the previous published revision.
- Authenticated learner filtering so drafts are hidden; absent course content is shown as an honest empty state.
- Deterministic exercise grading and per-account practice history/review estimates.
- An implementation walkthrough for the teacher/product owner in TEACHER_GUIDE.md.

Still needed:

- The teacher must review and approve the Danish starter lesson. There is no German source content bundled; German becomes useful after a teacher-authored lesson is published.
- A language-specific diagnostic and multi-answer accepted-answer rules.
- A richer teacher authoring UI or file importer, review history screen, and reversible unpublish action.
- Production privacy controls, identity hardening, rate limiting, monitoring, accessibility/user testing, PostgreSQL verification, and a deployment environment.
- Kafka, Azure Cosmos/vector retrieval, and model integration; none is required for this small content-reviewed MVP.
- Amber and AI fashion remain separately scoped later products.

The project is not a public release and must not contain sensitive learner or relationship data. Deployment remains a separate decision after tests and review.

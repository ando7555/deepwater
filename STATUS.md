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
- Deterministic exercise grading and per-account practice history/review estimates. Typed exercises accept several teacher-listed answers, and near misses (special letters, word order, one-letter typo) get a tentative suggestion without revealing the answer.
- Teacher audio authoring for music and podcasts, with editorial bands from A0–A1 through C1–C2, separate listening-demand ratings, source/provenance and rights fields, and before-listening/gist/focus prompts.
- Server-side publish checks require a verified rights basis and learner source-link permission, enforce playback/transcript permissions independently, hide expired items, and prevent unapproved lessons from learner queries.
- Learner music/podcast and level filters, direct audio controls only when explicitly allowed, source links, optional rights-approved transcripts, four guided listening steps, confidence capture, and audio-attempt counts.
- Explainable audio recommendations use the learner's stated goal and starting familiarity; low-confidence first-listen reflections prompt a context-supported replay. A permitted transcript unlocks only after the gist step.
- Audio reflections store response length and support/confidence metadata rather than raw free text, reducing the chance of retaining copyrighted lyrics or personal notes.
- End-to-end tests cover unapproved visibility, unverified-rights publication rejection, cleared publishing, transcript exposure, and learner evidence ownership.
- An implementation walkthrough for the teacher/product owner in TEACHER_GUIDE.md.
- Account safety: Google sign-in, five failed password sign-ins per email lock that email out for 15 minutes, signing in with Google removes an unverified password set on the same email, and learners can permanently delete their account and learning records.

Still needed:

- The teacher must review and approve the Danish starter lesson. There is no German source content bundled; German becomes useful after a teacher-authored lesson is published.
- The supplied song lesson has no audio attachment, and its commercial-song reference does not establish lyric/audio rights. No media source is bundled; only teacher-created or explicitly rights-cleared music and podcast sources should be published.
- Review the new audio flow with the teacher and source owner, including actual permission evidence and player/transcript behavior for a first music and podcast lesson.
- A language-specific diagnostic.
- A richer teacher authoring UI or file importer, review history screen, and reversible unpublish action.
- Email verification for password sign-up, data export, wider rate limiting (per IP, registration), monitoring, accessibility/user testing, PostgreSQL verification, and a deployment environment.
- Kafka, Azure Cosmos/vector retrieval, and model integration; none is required for this small content-reviewed MVP.
- Amber and AI fashion remain separately scoped later products.

The project is not a public release and must not contain sensitive learner or relationship data. Deployment remains a separate decision after tests and review.

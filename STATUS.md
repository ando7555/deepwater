# Deepwater MVP implementation status

The local Echo slice now supports teacher-controlled content for Danish and German. Lessons are stored in SQL; JSON catalog files seed one Danish lesson and four German lessons as unapproved drafts. Learners only receive published records matching their selected language.

Implemented:

- Danish/German selection at onboarding and a persistent language switch in the learner workspace.
- A learner profile with a personal goal and self-described starting familiarity. The former three-question Danish-only diagnostic was removed.
- A teacher workspace gated by the server-side DEEPWATER_TEACHER_EMAIL setting.
- One Danish starter lesson and four German lessons (V2 word order, sein, articles, café orders) imported as teacher-only drafts on an empty database. The seed loader reads every file in echo/catalog/.
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

- The teacher must review and approve the Danish starter lesson and the four German draft lessons. Until a lesson is published, learners see an empty state for that language.
- The supplied song lesson has no audio attachment, and its commercial-song reference does not establish lyric/audio rights. No media source is bundled; only teacher-created or explicitly rights-cleared music and podcast sources should be published.
- Review the new audio flow with the teacher and source owner, including actual permission evidence and player/transcript behavior for a first music and podcast lesson.
- A language-specific diagnostic.
- A richer teacher authoring UI or file importer, review history screen, and reversible unpublish action.
- Email verification for password sign-up, data export, wider rate limiting (per IP, registration), monitoring, accessibility/user testing, and PostgreSQL verification. See Deployment below for what runs today.
- Kafka, Azure Cosmos/vector retrieval, and model integration; none is required for this small content-reviewed MVP.
- Amber and AI fashion remain separately scoped later products.

## Deployment

Echo runs on Railway as two services, a backend and a frontend. The GitHub repository is connected to Railway, and Railway reports each deploy as a commit status on GitHub. That is the only deployment evidence checked so far. The live URLs were not reached from the review environment.

Verified from Railway's commit statuses (checked 2026-10-17):

- Backend (backend-production-c912.up.railway.app): Railway redeploys it on every merge to main. Merges dc04367 (#99, Google sign-in) and 49a136e (#101, accepted answers) both deployed successfully. For the latest state, see the "deepwater - backend" status on main's newest commit.
- Frontend (frontend-production-3165.up.railway.app): last deployed successfully at 5b5e0b8 on 2026-10-12. Railway has not deployed it since. Four later commits changed frontend/ (aa12691, 143759f, 0a993ce, 150d992: Google sign-in and the accepted-answer hints), so the live frontend is behind main.
- Deepwater CI passes on main. CI runs backend tests, bootJar and the frontend build. It does not build the Docker images or deploy.

Configured in code (commits 2686ec4, 36c59f3, 5b5e0b8, c819a67, plus the Google sign-in settings from #99):

- Root Dockerfile builds the backend jar with Gradle 9.1.0 on Temurin 25 and runs it with SERVER_ADDRESS=0.0.0.0. The port comes from Railway's PORT variable (local default 8081).
- frontend/Dockerfile builds the Vite app with an optional VITE_API_URL build argument and serves it from nginx on port 80.
- Database credentials fall back from DB_USER/DB_PASSWORD to Railway's PGUSER/PGPASSWORD. The JDBC URL still comes only from DB_URL.
- Extra CORS origins can be added with DEEPWATER_CORS_ALLOWED_ORIGINS (comma-separated), next to the built-in localhost origins.
- Google sign-in reads GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI and FRONTEND_BASE_URL. The defaults point to localhost.

Not verified against the running services:

- That the backend health endpoint (/actuator/health) responds and the frontend loads and reaches the backend's GraphQL API.
- That DB_URL is set to a JDBC PostgreSQL URL. Railway's Postgres add-on provides DATABASE_URL and PG* variables, not DB_URL. Without DB_URL, the backend falls back to an H2 file inside the container, and data is lost on redeploy.
- That VITE_API_URL was set to the backend's public URL when the frontend was built. The nginx image has no /graphql proxy, so the empty default only works behind the Vite dev server.
- That DEEPWATER_CORS_ALLOWED_ORIGINS includes the frontend domain, DEEPWATER_TEACHER_EMAIL is set, and the Google sign-in variables point to the Railway URLs.
- Why the frontend service is not redeploying on pushes to main (watch paths, auto-deploy setting or a failed build that reported no status).
- Public-exposure settings: GraphiQL is enabled in all environments, rate limiting covers only password sign-in, and there is no monitoring yet.

The project is not a public release and must not contain sensitive learner or relationship data. Any public deployment remains a separate decision after tests and review.

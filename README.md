# Deepwater

Deepwater is a product family built around learning, expression, and intentional
choice:

- **Echo** — learn languages analytically by comparing structures and practising
  meaningful expressions. Danish class material supplied by the product owner
  will define the first real lesson corpus.
- **Amber** — a separate, consent-first connection and dating application planned
  after the Echo method and platform boundaries are reviewed.
- **AI fashion** — a future, isolated product idea; scope and name remain open.

The supplied logo and visual direction are retained. The current branch builds a
local Echo MVP slice; it is not a deployable or teacher-approved product. Amber
and AI fashion remain later, separately gated applications.

## Product and architecture documents

- [Materials-based product, algorithm, acceptance criteria, architecture, and roadmap](docs/product/DEEPWATER_MATERIALS_BASED_PROPOSAL.md)
- [Product scope, shared analytical method, and measurable acceptance criteria](docs/product/DEEPWATER_PRODUCT_SCOPE_AND_ACCEPTANCE.md)
- [Target architecture and Mermaid system/workflow diagrams](docs/architecture/DEEPWATER_TARGET_ARCHITECTURE.md)
- [Architecture overview](ARCHITECTURE.md)
- [Implementation status and known gaps](STATUS.md)
- [Teacher workflow and coding guide](TEACHER_GUIDE.md)

## Building features with Claude Code

The `deepwater-developer/` folder is a Claude Code plugin that turns a GitHub issue into a
feature plan, code, and test evidence the way this project expects. Install it once, then type
`/deepwater-developer:feature <issue number>` in Claude Code (CLI or the IntelliJ plugin).
Step-by-step instructions: [deepwater-developer/README.md](deepwater-developer/README.md).

## Run the local proof of flow

Requirements: Java 25 and Node.js/npm.

In one terminal from the repository root:

```powershell
./gradlew.bat :backend:bootRun
```

In a second terminal:

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5173
```

Open `http://127.0.0.1:5173`. By default, the API uses a file-backed H2 database
under `backend/data`. Docker Compose defines PostgreSQL for future integration;
PostgreSQL has not been verified in this workspace. Do not put real personal,
dating, or lesson data in this prototype.

## Sign-in and email settings

The backend reads these optional environment variables:

| Variable | Purpose |
|---|---|
| `DEEPWATER_TEACHER_EMAIL` | The one account that gets the teacher workspace. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | Google sign-in. The redirect URI ends in `/api/v1/auth/google/callback`. |
| `FRONTEND_BASE_URL` | Where Google sign-in and email confirmation links send people back to. |
| `POSTMARK_SERVER_TOKEN`, `MAIL_FROM` | Sends the confirm-your-email message on sign-up. `MAIL_FROM` must be a sender signature or domain verified in Postmark. Without them, sign-up still works and no email is sent. |

## Echo MVP flow

1. Optionally set `DEEPWATER_TEACHER_EMAIL` before starting the backend, then
   register using that email to enable the teacher workspace. See
   [TEACHER_GUIDE.md](TEACHER_GUIDE.md).
2. As a teacher, author Danish or German lessons as structured JSON drafts and
   review them before explicitly publishing. Learners cannot see drafts.
3. As a learner, choose Danish or German during onboarding, set a personal goal,
   and select a starting familiarity. The learner can switch languages later.
4. Study only published lessons, practise teacher-defined checks, and track a
   simple per-skill estimate and 1/3/7/14-day review schedule.

There is no German corpus bundled: choosing German shows an empty state until
you author and approve the first lesson. A Danish starter lesson imports as a
teacher-only draft from a JSON catalog file and must be approved before it appears.
The fixed Danish quiz was removed
because it would be incorrect for German. This prototype does not generate
course content or use private messages/unlicensed media.

The local app now includes a rights-gated music and podcast lesson path. A teacher
can create private audio drafts, set a beginner-to-advanced editorial band and
listening demands, record source permissions, then explicitly approve publication.
Learners can filter approved material, listen before revealing a permitted
transcript, complete staged reflections, and track listening evidence separately.
No audio catalog is bundled; the supplied commercial-song reference does not grant
reuse rights. See the [teacher guide](TEACHER_GUIDE.md) for the workflow.

## Development direction

Read and agree the product acceptance criteria before broadening implementation.
The next Echo lesson design should be grounded in supplied Danish class material
with source provenance and review status. Amber and AI fashion remain separate
product modules with separate data and consent boundaries. Cloud deployment is
not part of local startup and requires a separate, explicit release decision.

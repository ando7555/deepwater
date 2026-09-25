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

## Echo MVP flow

1. Register or sign in.
2. Set a learning goal and answer three short Danish starting-check questions.
   Echo selects a guided, standard, or challenge route. This is not a CEFR score.
3. Study a Danish main-clause word-order pattern grounded in the supplied 5 August
   lesson notes, with the source shown in the interface.
4. Answer a multiple-choice check and a short completion exercise. Deterministic
   feedback updates a simple mastery estimate and schedules review after 1, 3, 7,
   or 14 days. Write a transfer example that is saved without automatic grading.

The lesson is visibly marked **Draft — Danish teacher review required**. This
prototype does not include private teacher messages, song lyrics, or unlicensed
images, and it does not call an AI model. See `STATUS.md` for validation and gaps.

## Development direction

Read and agree the product acceptance criteria before broadening implementation.
The next Echo lesson design should be grounded in supplied Danish class material
with source provenance and review status. Amber and AI fashion remain separate
product modules with separate data and consent boundaries. Cloud deployment is
not part of local startup and requires a separate, explicit release decision.

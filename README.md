# Deepwater

Deepwater is a product family built around learning, expression, and intentional
choice:

- **Echo** — learn languages analytically by comparing structures and practising
  meaningful expressions. Danish class material supplied by the product owner
  will define the first real lesson corpus.
- **Amber** — a separate, consent-first connection and dating application planned
  after the Echo method and platform boundaries are reviewed.
- **AI fashion** — a future, isolated product idea; scope and name remain open.

The logo marks are supplied design assets. The current implementation is a local
proof of flow, not the acceptance-ready product or a deployable MVP.

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

## What the prototype demonstrates

- account registration and login;
- two sample Danish comparison cases and saved learner practice;
- an Amber profile, visibly labeled sample profiles, and invitation state changes.

The practice response is a reflection prompt, not automated language assessment.
Sample people are fictional and cannot receive invitations. See `STATUS.md` for
the known failing GraphQL authorization-error test and for features not yet wired.

## Development direction

Read and agree the product acceptance criteria before broadening implementation.
The next Echo lesson design should be grounded in supplied Danish class material
with source provenance and review status. Amber and AI fashion remain separate
product modules with separate data and consent boundaries. Cloud deployment is
not part of local startup and requires a separate, explicit release decision.

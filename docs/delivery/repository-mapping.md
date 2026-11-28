# Repository mapping: Deepwater

> Project settings for the spec-driven delivery method (plugin `deepwater-developer`, in this
> repository under `ai-workspace/deepwater-developer/`). The method itself is generic; everything that is true
> only of this repository is written here, so it can be reviewed and changed on its own. Refresh
> it with `/deepwater-developer:init` or edit it by hand whenever the repository changes.

## Ticket source

- Tracker: GitHub (`origin` is `https://github.com/ando7555/deepwater`)
- Read a ticket: `gh issue view <n> --comments`, or the issue text the user pastes
- Ticket id in file names: the issue number; a plan without an issue uses `draft`

## Where things live

| Method term | In this repository |
| --- | --- |
| Specification (feature plan) | `docs/specs/<issue-number>-<capability>.md`, for example `docs/specs/107-lesson-draft-suggestions.md`; drafts `docs/specs/draft-<capability>.md` until the issue exists |
| Architecture decision records | `docs/architecture/adr/<nnnn>-<decision>.md` (first one: `0001-spec-driven-delivery-plugin.md`) |
| Interface contracts | GraphQL: `backend/src/main/resources/graphql/schema.graphqls`. OpenAPI, MCP, AG-UI: none yet; create `api-contracts/<protocol>/` when that protocol gets its first consumer |
| Modules | Gradle, subproject `backend` (`settings.gradle`); packages carry the boundaries today: `com.deepwater.echo`, `com.deepwater.platform`, `com.deepwater.api`. `frontend/` is a separate npm project |
| Root instructions | `README.md`, `ARCHITECTURE.md`, `STATUS.md`, `TEACHER_GUIDE.md` (no `CLAUDE.md` or `AGENTS.md` yet) |
| Pipeline | `.github/workflows/ci.yml`: backend `gradle :backend:test :backend:bootJar` on JDK 25; frontend `npm ci && npm run build`; SonarCloud quality gate |

## Verification commands, narrowest first

```text
./gradlew :backend:compileJava :backend:compileTestJava
./gradlew :backend:test --tests '<FocusedTestClass>'
./gradlew :backend:test :backend:bootJar
cd frontend && npm ci && npm run build        # only when frontend/ changed
```

On Windows use `gradlew.bat`. Tests share the default in-memory H2 database; a test that needs
isolation uses its own `jdbc:h2:mem:` URL and must not publish lessons into the shared one.

## Current state the method must respect

- Inbound adapters today: GraphQL (`DeepwaterGraphQlController`), REST per product
  (`/api/v1/...`), MCP (`/mcp`, tools in `com.deepwater.tools.DeepwaterTools`) and AG-UI-named SSE events
  for Ask Deepwater (`/api/v1/assistant/runs`). Do not add another protocol without a consumer.
- AI calls sit behind ports (`AssistantModel`, `WritingAssessor`) with a deterministic or "off" fallback;
  Amber members' data is never sent to a model.
- Kafka is declared in `backend/build.gradle` but unused; not required for the MVP.
- The `backend` packages are not yet split into domain / application / adapter. Apply the
  brownfield strategy one use case at a time when an issue touches it; no big-bang refactor.

## AI workspace

- The owner's board of tasks for the five AI roles lives in the app (`com.deepwater.workspace`, account menu >
  AI workspace) and is reached from Claude Code through the MCP tools `workspace_tasks`,
  `workspace_propose_task`, `workspace_start_task` and `workspace_submit_evidence` (see `.mcp.json`).
- Everything for the AI workspace starts at `ai-workspace/README.md`. The roles are agents in
  `ai-workspace/deepwater-developer/agents/` and data in
  `ai-workspace/roles.json`; `AgentRolesInStepTest` keeps the two in step.
- An agent works only on an APPROVED task assigned to its role, calls `workspace_start_task` before changing
  code and `workspace_submit_evidence` when the pull request is open. It never approves, merges or deploys.

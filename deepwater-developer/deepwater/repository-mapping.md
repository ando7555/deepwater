# Repository mapping: Deepwater

Project-specific settings for the `spec-driven-delivery` method. The method itself
(`skills/spec-driven-delivery/`) is generic; everything that is true only of the Deepwater
repository (`ando7555/deepwater`) lives in this folder so it can be reviewed and changed on its own.

## Names used by the method and what they are here

| Method term | In this repository |
| --- | --- |
| Ticket / issue | GitHub issue: `gh issue view <n> --comments`, or the issue text the user pastes |
| `documentation/specs/<ticket-id>-<capability>.md` | `docs/specs/<issue-number>-<capability>.md` (see `specs-convention.md`) |
| `documentation/architecture/adr/` | `docs/architecture/adr/<nnnn>-<decision>.md` |
| `api-contracts/graphql/` | `backend/src/main/resources/graphql/schema.graphqls` |
| `api-contracts/openapi/`, `api-contracts/mcp/`, `api-contracts/ag-ui/` | Create `api-contracts/<protocol>/` only when that protocol gets its first consumer |
| Maven module | Gradle subproject `backend`; packages carry the boundaries today (`com.deepwater.echo`, `com.deepwater.platform`, `com.deepwater.api`) |
| Root instructions | `README.md`, `ARCHITECTURE.md`, `STATUS.md`, `TEACHER_GUIDE.md` (no `CLAUDE.md` or `AGENTS.md` yet) |
| Pipeline | `.github/workflows/ci.yml` (backend: `gradle :backend:test :backend:bootJar` on JDK 25; frontend: `npm ci && npm run build`) |

## Verification commands, narrowest first

```text
./gradlew :backend:compileJava :backend:compileTestJava
./gradlew :backend:test --tests '<FocusedTestClass>'
./gradlew :backend:test :backend:bootJar
cd frontend && npm ci && npm run build        # only when the frontend changed
```

Tests share the default in-memory H2 database; a test that needs isolation uses its own
`jdbc:h2:mem:` URL and must not publish lessons into the shared one.

## Current state the method must respect

- Inbound adapters today: GraphQL (`DeepwaterGraphQlController`) and REST for auth
  (`AuthController`). No MCP, no AG-UI. Do not add one without a consumer.
- No AI provider dependency in the code. The architecture docs plan an AI provider port with a
  deterministic fallback; Amber user data is never sent to a model.
- Kafka is declared in `backend/build.gradle` but unused; not required for the MVP.
- The `backend` packages are not yet split into domain / application / adapter. Apply the
  brownfield strategy one use case at a time when an issue touches it; no big-bang refactor.

## Using the method in another repository

Copy this folder, rename it after the project, and fill the two tables and the command list from
that repository's root instructions and build files. The skills read
`${CLAUDE_PLUGIN_ROOT}/deepwater/repository-mapping.md`; point them at the new file.

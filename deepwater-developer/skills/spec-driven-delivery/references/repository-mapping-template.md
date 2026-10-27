# Repository mapping: <project name>

> Project settings for the spec-driven delivery method (plugin `deepwater-developer`). The method
> itself is generic; everything that is true only of this repository is written here, so it can
> be reviewed and changed on its own. Created by `/deepwater-developer:init`; edit it by hand
> whenever the repository changes.

## Ticket source

- Tracker: `<GitHub | GitLab | pasted text>` (decided from the `origin` remote)
- Read a ticket: `<gh issue view <n> --comments | glab issue view <n> --comments | the user pastes the text>`
- Ticket id in file names: the issue number; a plan without a ticket uses `draft`

## Where things live

| Method term | In this repository |
| --- | --- |
| Specification (feature plan) | `<docs/specs>/<ticket-id>-<capability>.md`; drafts `<docs/specs>/draft-<capability>.md` |
| Architecture decision records | `<docs/architecture/adr>/<nnnn>-<decision>.md` |
| Interface contracts | `<path of the .graphqls / OpenAPI / api-contracts files, or "none yet">` |
| Modules | `<build tool and module names, or "single module">` |
| Root instructions | `<the CLAUDE.md, AGENTS.md, README.md, CONTRIBUTING.md, ARCHITECTURE.md ... that exist>` |
| Pipeline | `<CI file and what it runs>` |

## Verification commands, narrowest first

```text
<compile>
<one focused test class>
<the whole suite and build, as the pipeline runs it>
<other parts of the repository, marked "only when <folder> changed">
```

<Notes the commands need: shared test database, required environment, slow suites. Leave out
if there are none.>

## Current state the method must respect

- <Inbound interfaces that exist today, and which must not be added without a consumer.>
- <Dependencies declared but unused, package layout not yet split, planned ports.>
- <Anything else a careful developer would tell a new colleague before they touch the code.>

---

## Discovery rules: how to fill this file when it does not exist yet

Used by `/deepwater-developer:init`, and by every other command when the file is missing. Read,
do not guess: every line above must point at something that exists, with the file or command
output that shows it. Do not run the build during discovery.

1. **Ticket source.** `git remote get-url origin`. Host contains `github` → GitHub, read a
   ticket with `gh issue view <n> --comments` (a URL works too). Host contains `gitlab` → GitLab,
   read with `glab issue view <n> --comments`. Any other host, no remote, the CLI missing, or
   `gh auth status` / `glab auth status` not logged in → the user pastes the ticket text; write
   that down so nobody is surprised later.
2. **Specifications.** The first existing folder among `docs/specs/`, `documentation/specs/`,
   `specs/`; otherwise `docs/specs/`, created with the first plan.
3. **ADRs.** The first existing folder among `docs/architecture/adr/`, `docs/adr/`,
   `documentation/architecture/adr/`, `doc/adr/`, `adr/`; otherwise `docs/architecture/adr/`.
4. **Contracts.** Files matching `*.graphqls`, `*.graphql`, `openapi*.y*ml`, `swagger*.y*ml`,
   and anything under `api-contracts/`. None → "none yet; create `api-contracts/<protocol>/`
   when a protocol gets its first consumer".
5. **Build tool and commands.** Prefer the wrapper (`./gradlew`, `./mvnw`) over a global
   install; on Windows the wrapper is `gradlew.bat` / `mvnw.cmd`.
   - `gradlew` or `build.gradle(.kts)`: Gradle. Modules from `settings.gradle(.kts)`; prefix
     tasks with `:<module>:` when there are modules. Commands:
     `./gradlew compileJava compileTestJava`, `./gradlew test --tests '<FocusedTestClass>'`,
     `./gradlew test build` (or the tasks the pipeline runs).
   - `mvnw` or `pom.xml`: Maven. Modules from `<modules>`; add `-pl <module> -am` when there are
     modules. Commands: `./mvnw -q test-compile`, `./mvnw -q test -Dtest=<FocusedTestClass>`,
     `./mvnw -q verify`.
   - `package.json`: `npm ci`, then the `lint`, `test` and `build` scripts that exist. When this
     is a secondary part of the repository (a `frontend/` folder beside a Java backend), mark it
     "only when `<folder>` changed".
   - Anything else: take the commands from the pipeline file.
6. **Root instructions.** `CLAUDE.md`, `AGENTS.md`, `README.md`, `CONTRIBUTING.md`,
   `ARCHITECTURE.md`, `STATUS.md`, then any other root `*.md` that says how to work here.
7. **Pipeline.** `.github/workflows/*.yml`, `.gitlab-ci.yml`, `Jenkinsfile`,
   `azure-pipelines.yml`, `bitbucket-pipelines.yml`, `.circleci/config.yml`. Read what it runs;
   the full-verification command above should match it.
8. **Current state.** From the build file and the main source tree: inbound adapters present
   (controllers, resolvers, MCP tools, UI), dependencies declared but unused, whether packages or
   modules already follow domain / application / adapter, AI provider dependencies. Write only
   what you saw, each line with the file that shows it.

# How to use deepwater-developer

Step-by-step instructions for the product owner and anyone else working on Deepwater with Claude
Code in IntelliJ. Everything below also works in the plain `claude` CLI.

## 1. Prerequisites (once)

1. Install the Claude Code CLI and sign in: open a terminal and run `claude`. In IntelliJ, install
   the "Claude Code [Beta]" plugin from the JetBrains marketplace; it uses that CLI
   (Settings → Tools → Claude Code → "Claude command" if `claude` is not on `PATH`).
2. Install and sign in the GitHub CLI so the plugin can read issues: `gh auth login`.
   Without it you paste the issue text instead of the number.
3. Have the project's toolchain ready for verification: for Deepwater, JDK 25 and the Gradle
   wrapper (`./gradlew`), Node 22 for the frontend.

## 2. Install the plugin (once per machine)

Open the Deepwater project in IntelliJ, open the Claude Code tool window (or a terminal with
`claude`), and type:

```text
/plugin marketplace add <absolute path to>/deepwater/deepwater-developer
/plugin install deepwater-developer@deepwater
```

Choose scope **user** (available in every project on this machine) or **project** (this clone
only). Example path on Windows: `C:\Users\<you>\source\repos\deepwater\deepwater-developer`.

Check it loaded: `/plugin` shows `deepwater-developer` as enabled, and typing `/deepwater-` offers
the three commands.

After the plugin's files change (a pull from `main` that touches `deepwater-developer/`), run
`/plugin marketplace update deepwater` and reinstall, or restart Claude Code.

To try an edited version without installing: `claude --plugin-dir ./deepwater-developer` from the
repository root, then `/reload-plugins` after each edit.

## 3. Daily workflow

### Deliver an issue: `/deepwater-developer:feature`

```text
/deepwater-developer:feature 107
```

What happens, phase by phase, in your session:

| Phase | What Claude does | What you do |
| --- | --- | --- |
| A Clarify | Reads the issue and comments, extracts problem, outcome, actors, scope, interface, constraints; lists unknowns | Answer questions it cannot resolve from the issue |
| B Inspect | Reads `README.md`, `ARCHITECTURE.md`, `STATUS.md`, the GraphQL schema, services and tests the issue touches; classifies each as REUSE / EXTEND / WRAP / REFACTOR / REPLACE / NEW | Nothing, unless it misread something |
| C Specify | Writes `docs/specs/107-<capability>.md` as the 18-section feature plan: metadata, outcome, actors, existing-system discovery and reuse decisions, business rules and contracts, Given/When/Then scenarios, DDD design, AI classification, implementation plan, KISS/YAGNI/DRY, security, observability, risks and open questions | Read the plan. Decide the open questions. This is the main review point |
| D Implement | Domain rule → use case → fake port → tests → the one required adapter → contract test → real adapter | Nothing; it stops if a decision blocks it |
| E Verify | Runs `./gradlew :backend:compileJava`, the focused test class, then the full `:backend:test` and pastes the real output | Read the output |
| F Report | Ticks section 17 of the plan, fills the review record, then reports files changed, test output, definition-of-done lines met / not met, remaining open decisions | Review the diff in IntelliJ, then commit and push yourself, or say "commit and push" |

You can pass a description instead of a number (`/deepwater-developer:feature "accept
several answers for typed exercises"`); the spec is then written as `docs/specs/draft-<capability>.md`.

### Only the specification: `/deepwater-developer:spec`

```text
/deepwater-developer:spec 107
```

Phases A to C only. Use it to agree on scope before any code is written, or to turn a vague issue
into a testable one. It ends with the open decisions and a one-line "implementation-ready or not".

### Check a branch before a pull request: `/deepwater-developer:dod`

```text
/deepwater-developer:dod 107
```

Compares the current branch with the spec and the definition of done, runs the verification
commands, and lists what is met, what is not, and what to fix. It changes nothing.

### Run it as an autonomous agent

When you do not want to sit through the phases, hand the whole issue to the agent and read its
report at the end:

```text
@agent-deepwater-developer:spec-driven-feature implement issue 107
```

It has read, search, edit and shell tools, follows the same method, and stops at the specification
if an open decision blocks it. It never commits or pushes.

## 4. What the plugin will not do

- Commit, push, open pull requests or merge. You stay in control of git.
- Invent requirements. Unknowns become questions in the spec's "Open decisions".
- Put business logic into adapters, add frameworks, services, Kafka, CQRS or AI orchestration
  without a current requirement, or move deterministic rules into prompts.
- Claim a test passed without showing the output.

## 5. Adjusting it

- Project-specific paths, commands and current-state notes: `deepwater/repository-mapping.md`.
  Change this when the repository layout or build changes.
- The method itself: `skills/spec-driven-delivery/SKILL.md`; its references (full architecture
  overview, spec template, ADR template, definition of done) are beside it.
- Wording of the commands: `skills/feature|spec|dod/SKILL.md`; of the agent: `agents/`.

Validate after editing: `claude plugin validate ./deepwater-developer`.

## 6. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `/deepwater-developer:...` not offered | Plugin not installed or disabled: `/plugin`, check the Installed tab and Errors tab |
| "gh: command not found" or auth error in Phase A | Install `gh` and run `gh auth login`, or paste the issue text as the argument |
| Verification step says a command cannot run | JDK 25 or Node missing on this machine; install them or run the commands yourself and paste the result |
| The spec lands in the wrong folder | Update `deepwater/repository-mapping.md` |
| Old plugin version after pulling changes | `/plugin marketplace update deepwater`, reinstall, or restart Claude Code |

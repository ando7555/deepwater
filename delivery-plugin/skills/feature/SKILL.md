---
name: feature
description: Deliver a GitHub issue end to end with the spec-driven method, in the current session so the user can answer open decisions. Use when the user says "/feature", "implement issue 123", "deliver #123" or gives an issue description to build.
argument-hint: <issue-number | issue URL | short description>
disable-model-invocation: true
skills:
  - spec-driven-delivery
---

Deliver this request with the `spec-driven-delivery` method, phase by phase, in this session:

> $ARGUMENTS

Work through the phases below. At the end of each phase, show a short summary and continue; stop
and ask only when an open decision blocks the next phase or when a step cannot be undone.

## Phase A: Clarify

- If the argument is a number or GitHub URL, fetch the issue: `gh issue view <n> --comments`.
  If `gh` is not installed or not authenticated, say so and ask the user to paste the issue text.
- Extract problem, outcome, actors, scope, out of scope, required interface, data classification,
  compatibility, non-functional requirements, definition of done. Classify the change (new
  capability, behavior change, bug fix, integration change, AI behavior change).
- List unknowns as questions. Do not invent answers.

## Phase B: Inspect

- Read the root `CLAUDE.md` / `AGENTS.md`, `README.md`, `ARCHITECTURE.md`, `STATUS.md` when
  present, then trace the code, contracts (`backend/src/main/resources/graphql/schema.graphqls`,
  REST controllers), tests and CI the issue touches.
- Classify each affected component: REUSE, EXTEND, WRAP, REFACTOR, REPLACE, NEW.

## Phase C: Specify

- Write `docs/specs/<issue-number>-<capability>.md` from
  `${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/spec-template.md`.
  With no issue number, use `docs/specs/draft-<capability>.md` and say so.
- Include Given/When/Then scenarios, contracts for only the interface the issue requires, the error
  contract, and the deterministic-versus-AI classification of each rule.
- Show the open decisions to the user. If any blocks implementation, stop here and wait.

## Phase D: Implement the smallest vertical slice

Order: domain rule and value objects → use case (command, result, port) → fake outbound port →
domain and use-case tests → the one required inbound adapter → contract test → real adapter →
integration test. Keep adapters thin; one use case serves every protocol.

## Phase E: Verify

Run the narrowest checks first and paste the real output:

```text
./gradlew :backend:compileJava :backend:compileTestJava
./gradlew :backend:test --tests '<FocusedTestClass>'
./gradlew :backend:test :backend:bootJar
cd frontend && npm ci && npm run build      # only when the frontend changed
```

If a command cannot run in this environment, say which and why instead of skipping silently.

## Phase F: Report

Give: files changed, specification path, test output, each line of
`${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/definition-of-done.md` marked met or
not met, and remaining open decisions. Do not commit or push unless the user asks.

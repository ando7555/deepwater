---
name: feature
description: Deliver a ticket (GitHub issue, GitLab issue or pasted description) end to end with the spec-driven method, in the current session so the user can answer open decisions. Use when the user says "/feature", "implement issue 123", "deliver #123" or gives a feature description to build.
argument-hint: <ticket number | ticket URL | short description>
disable-model-invocation: true
skills:
  - spec-driven-delivery
---

Deliver this request with the `spec-driven-delivery` method, phase by phase, in this session:

> $ARGUMENTS

First read the repository mapping, `docs/delivery/repository-mapping.md` at the repository root:
it says how tickets are read here, where specifications, ADRs and contracts live, and which
commands verify a change. If the file does not exist, derive those answers with the discovery
rules at the end of
`${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/repository-mapping-template.md`,
tell the user you did, and suggest `/deepwater-developer:init` for next time. Then work through
the phases below. At the end of each phase, show a short summary and continue; stop and ask only
when an open decision blocks the next phase or when a step cannot be undone.

## Phase A: Clarify

- If the argument is a number or a URL, read the ticket the way the mapping says: GitHub
  `gh issue view <n> --comments`, GitLab `glab issue view <n> --comments`. If the CLI is not
  installed or not logged in, say so and ask the user to paste the ticket text. If the argument
  is a description, that is the ticket.
- Extract problem, outcome, actors, scope, out of scope, required interface, data classification,
  compatibility, non-functional requirements, definition of done. Classify the change (new
  capability, behavior change, bug fix, integration change, AI behavior change).
- List unknowns as questions. Do not invent answers.

## Phase B: Inspect

- Read the root instruction files named in the repository mapping, then trace the code,
  contracts, tests and pipeline the ticket touches.
- Classify each affected component: REUSE, EXTEND, WRAP, REFACTOR, REPLACE, NEW.

## Phase C: Specify

- Write the specification at the path the mapping gives, from the 18-section feature plan
  `${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/feature-plan-template.md`.
  Fill sections 1 to 13, 15 and 16 now (status `DRAFT`); section 10 is the implementation plan
  for Phase D; sections 14, 17 and 18 are completed in Phases E and F. Mark a section
  "not applicable" rather than leaving placeholders. Without a ticket number, use the mapping's
  draft naming and say so.
- Include Given/When/Then scenarios, contracts for only the interface the ticket requires, the
  error contract, and the deterministic-versus-AI classification of each rule.
- Show the open decisions to the user. If any blocks implementation, stop here and wait.

## Phase D: Implement the smallest vertical slice

Order: domain rule and value objects → use case (command, result, port) → fake outbound port →
domain and use-case tests → the one required inbound adapter → contract test → real adapter →
integration test. Keep adapters thin; one use case serves every protocol.

## Phase E: Verify

Run the mapping's verification commands, narrowest first (compile, focused test class, full
suite), and paste the real output. If a command cannot run in this environment, say which and why
instead of skipping silently. Record the commands and results in section 14 of the plan.

## Phase F: Report

Tick what is true in section 17 of the plan, fill section 18 with the merge or pull request link
once it exists, and set the status to `IMPLEMENTING` or `DONE`. Then give: files changed,
specification path, test output, each line of
`${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/definition-of-done.md` marked met or
not met, and remaining open decisions. Do not commit or push unless the user asks.

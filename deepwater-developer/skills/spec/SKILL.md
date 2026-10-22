---
name: spec
description: Write or update only the feature specification (docs/specs/<issue>-<capability>.md) for a GitHub issue, with existing-behavior inspection and Given/When/Then scenarios, without implementing anything. Use when the user says "/spec", "write the spec for #123" or wants a specification reviewed before coding.
argument-hint: <issue-number | issue URL | short description>
disable-model-invocation: true
skills:
  - spec-driven-delivery
---

Produce the specification only, using the `spec-driven-delivery` method, for:

> $ARGUMENTS

1. Fetch the issue with `gh issue view <n> --comments` when the argument is a number or URL; if
   `gh` is unavailable, ask the user to paste the issue text.
2. Inspect the existing behavior the issue touches (entry points, use cases, domain rules,
   clients, tests, CI) and classify each component REUSE / EXTEND / WRAP / REFACTOR / REPLACE / NEW.
3. Write `docs/specs/<issue-number>-<capability>.md` from
   `${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/spec-template.md`
   (`docs/specs/draft-<capability>.md` when there is no issue number). Fill every section or mark
   it "none"; never leave template placeholders.
4. Decide for every rule whether it is deterministic (domain), a workflow (use case), external
   (port) or needs AI (orchestration with schema validation), and say why.
5. Finish with the list of open decisions for the product owner and a one-line recommendation on
   whether the issue is implementation-ready. Do not change code, commit or push.

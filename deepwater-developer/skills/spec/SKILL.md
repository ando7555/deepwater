---
name: spec
description: Write or update only the feature specification for a GitHub issue, with existing-behavior inspection and Given/When/Then scenarios, without implementing anything. Use when the user says "/spec", "write the spec for #123" or wants a specification reviewed before coding.
argument-hint: <issue-number | issue URL | short description>
disable-model-invocation: true
skills:
  - spec-driven-delivery
---

Produce the specification only, using the `spec-driven-delivery` method, for:

> $ARGUMENTS

1. Read `${CLAUDE_PLUGIN_ROOT}/deepwater/repository-mapping.md` for where the specification goes
   and which root files describe this repository.
2. Fetch the issue with `gh issue view <n> --comments` when the argument is a number or URL; if
   `gh` is unavailable, ask the user to paste the issue text.
3. Inspect the existing behavior the issue touches (entry points, use cases, domain rules,
   clients, tests, CI) and classify each component REUSE / EXTEND / WRAP / REFACTOR / REPLACE / NEW.
4. Write the specification at the mapped path from the 18-section feature plan
   `${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/feature-plan-template.md`. Fill
   sections 1 to 13, 15 and 16 with status `READY_FOR_REVIEW` (or `DRAFT` while questions are open);
   leave 14, 17 and 18 for implementation. Mark a section "not applicable" rather than leaving
   placeholders.
5. Decide for every rule whether it is deterministic (domain), a workflow (use case), external
   (port) or needs AI (orchestration with schema validation), and say why.
6. Finish with the list of open decisions for the product owner and a one-line recommendation on
   whether the issue is implementation-ready. Do not change code, commit or push.

---
name: spec
description: Write or update only the feature specification for a ticket (GitHub issue, GitLab issue or pasted description), with existing-behavior inspection and Given/When/Then scenarios, without implementing anything. Use when the user says "/spec", "write the spec for #123" or wants a specification reviewed before coding.
argument-hint: <ticket number | ticket URL | short description>
disable-model-invocation: true
skills:
  - spec-driven-delivery
  - mentor
---

Produce the specification only, using the `spec-driven-delivery` method, for:

> $ARGUMENTS

Speak in the `mentor` voice (loaded): plain words, the reason behind each step, one question at a
time, honest results. If the person is learning, explain more; if they are in a hurry, keep it short.

1. Read `docs/delivery/repository-mapping.md` at the repository root for how tickets are read
   here, where the specification goes and which root files describe this repository. If it does
   not exist, derive those answers with the discovery rules at the end of
   `${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/repository-mapping-template.md`,
   say so, and suggest `/deepwater-developer:init`.
2. Read the ticket when the argument is a number or URL (GitHub `gh issue view <n> --comments`,
   GitLab `glab issue view <n> --comments`); if the CLI is unavailable, ask the user to paste the
   ticket text.
3. Inspect the existing behavior the ticket touches (entry points, use cases, domain rules,
   clients, tests, pipeline) and classify each component REUSE / EXTEND / WRAP / REFACTOR /
   REPLACE / NEW.
4. Write the specification at the mapped path from the 18-section feature plan
   `${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/feature-plan-template.md`. Fill
   sections 1 to 13, 15 and 16 with status `READY_FOR_REVIEW` (or `DRAFT` while questions are open);
   leave 14, 17 and 18 for implementation. Mark a section "not applicable" rather than leaving
   placeholders.
5. Decide for every rule whether it is deterministic (domain), a workflow (use case), external
   (port) or needs AI (orchestration with schema validation), and say why.
6. Finish with the list of open decisions for the product owner and a one-line recommendation on
   whether the ticket is implementation-ready. Do not change code, commit or push.

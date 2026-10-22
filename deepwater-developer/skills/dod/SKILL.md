---
name: dod
description: Check the current branch's changes against the spec-driven definition of done and the specification they implement, and report what is met, what is not, and what to fix. Use when the user says "/dod", "is this done", "review against the spec" or before opening a pull request.
argument-hint: [issue-number or spec path]
disable-model-invocation: true
skills:
  - spec-driven-delivery
---

Review the working tree and branch changes against the definition of done.

Target: $ARGUMENTS (when empty, find the specification in the specs folder named by
`${CLAUDE_PLUGIN_ROOT}/deepwater/repository-mapping.md` that matches the changed files, or say
that none exists).

1. Run `git status --short` and `git diff --stat main...HEAD` (fall back to `git diff --stat` when
   there is no `main`). List the changed files.
2. Read the matching specification. If none exists, that is the first finding.
3. For each line of
   `${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/definition-of-done.md`, mark it
   **met**, **not met** or **not applicable**, with the file or test that proves it. Specifically
   check that no adapter (MCP tool, REST controller, GraphQL resolver, UI) carries business logic
   that belongs in a use case, that domain code imports no framework, database or model-provider
   classes, and that no secret, token, personal datum or production payload entered code, tests,
   docs or logs.
4. Run the mapping's verification commands, narrowest first, and paste the result lines. Say
   plainly if a command could not run.
5. Report findings ordered by severity, then the list of fixes needed before a pull request. Do
   not fix, commit or push unless the user asks.

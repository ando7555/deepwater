---
name: dod
description: Check the current branch's changes against the spec-driven definition of done and the specification they implement, and report what is met, what is not, and what to fix. Use when the user says "/dod", "is this done", "review against the spec" or before opening a merge or pull request.
argument-hint: [ticket number or specification path]
disable-model-invocation: true
skills:
  - spec-driven-delivery
  - mentor
---

Review the working tree and branch changes against the definition of done.

Speak in the `mentor` voice (loaded): plain words, the reason behind each step, one question at a
time, honest results. If the person is learning, explain more; if they are in a hurry, keep it short.

Target: $ARGUMENTS (when empty, find the specification in the specs folder named by
`docs/delivery/repository-mapping.md` that matches the changed files, or say that none exists).
If the mapping file does not exist, derive the specs folder and the verification commands with
the discovery rules at the end of
`${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/repository-mapping-template.md`
and say so.

1. Find the default branch (`main` or `master`, from `git symbolic-ref refs/remotes/origin/HEAD`
   when available). Run `git status --short` and `git diff --stat <default>...HEAD` (fall back
   to `git diff --stat` when there is no default branch). List the changed files.
2. Read the matching specification (a feature plan). If none exists, that is the first finding.
   Compare its section 17 checklist and section 14 evidence with what the branch contains.
3. For each line of
   `${CLAUDE_PLUGIN_ROOT}/skills/spec-driven-delivery/references/definition-of-done.md`, mark it
   **met**, **not met** or **not applicable**, with the file or test that proves it. Specifically
   check that no adapter (MCP tool, REST controller, GraphQL resolver, UI) carries business logic
   that belongs in a use case, that domain code imports no framework, database or model-provider
   classes, and that no secret, token, personal datum or production payload entered code, tests,
   docs or logs.
4. Run the mapping's verification commands, narrowest first, and paste the result lines. Say
   plainly if a command could not run.
5. Report findings ordered by severity, then the list of fixes needed before a merge or pull
   request. Do not fix, commit or push unless the user asks.

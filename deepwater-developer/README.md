# Deepwater developer plugin

A Claude Code plugin that runs the spec-driven delivery method for Java/Spring features: GitHub
issue → inspection of existing code → specification → smallest vertical slice behind one use case →
narrow-then-wide verification → definition of done. It works in the Claude Code CLI and in the
Claude Code plugin for IntelliJ / JetBrains, which runs that same CLI in the IDE terminal.

## What it adds

| Item | Invoke as | Does |
| --- | --- | --- |
| `feature` skill | `/deepwater-developer:feature 123` or `/deepwater-developer:feature "short description"` | Runs all phases (clarify, inspect, specify, implement, verify, report) in your session, pausing on open decisions |
| `spec` skill | `/deepwater-developer:spec 123` | Writes only `docs/specs/<issue>-<capability>.md`; no code changes |
| `dod` skill | `/deepwater-developer:dod [123]` | Checks the current branch against the definition of done and the spec |
| `spec-driven-feature` agent | `@agent-deepwater-developer:spec-driven-feature implement issue 123`, or `claude --agent deepwater-developer:spec-driven-feature` | The same method as an autonomous subagent with read, search, edit and shell tools; it reports back instead of asking mid-way |
| `spec-driven-delivery` skill | loaded automatically by the above | The method itself plus references: full architecture overview, spec template, ADR template, definition of done |

The agent and skills never commit or push; you do that (or ask explicitly).

## Install in IntelliJ

The JetBrains plugin runs the `claude` CLI, so plugins are installed from the Claude Code prompt
inside the IDE terminal. From the Deepwater project root:

```text
/plugin marketplace add ando7555/deepwater
/plugin install deepwater-developer@deepwater
```

Pick the **project** scope in the install panel so the plugin is recorded in `.claude/settings.json`
for this repository. Newer builds (2.1.275+) can do it in one step:

```text
/plugin install deepwater-developer --marketplace ando7555/deepwater
```

From a shell instead:

```bash
claude plugin marketplace add ando7555/deepwater --scope project
claude plugin install deepwater-developer@deepwater --scope project
```

For a private clone, the marketplace is fetched with your git credentials (`gh auth login` or an
SSH key). Third-party marketplaces do not auto-update: run `/plugin marketplace update deepwater`
after the plugin changes.

## Try it without installing

```bash
claude --plugin-dir ./deepwater-developer
```

Then `/deepwater-developer:feature 123`. After editing plugin files, `/reload-plugins`. Validate the
manifests with `claude plugin validate ./deepwater-developer` and `claude plugin validate .`.

## Requirements

- Claude Code CLI on `PATH` (IntelliJ: Settings → Tools → Claude Code → Claude command).
- `gh` CLI authenticated, so the skills can read issues with `gh issue view`. Without it, paste the
  issue text as the argument.
- JDK 25 and Gradle for the verification phase (`./gradlew :backend:test`).

## Layout

```text
deepwater-developer/
├── .claude-plugin/plugin.json
├── agents/spec-driven-feature.md
└── skills/
    ├── feature/SKILL.md
    ├── spec/SKILL.md
    ├── dod/SKILL.md
    └── spec-driven-delivery/
        ├── SKILL.md
        └── references/
            ├── architecture-overview.md
            ├── spec-template.md
            ├── adr-template.md
            └── definition-of-done.md
```

The repository root `.claude-plugin/marketplace.json` makes `ando7555/deepwater` a marketplace that
lists this plugin.

## Mapping to this repository

The method was written for a generic project (GitLab tickets, `documentation/`, Maven modules).
`skills/spec-driven-delivery/SKILL.md` maps those to GitHub issues, `docs/specs/`,
`docs/architecture/adr/`, the Gradle `backend` subproject and `.github/workflows/ci.yml`.

## Later: tools in Java

Today the agent's tools are Claude Code's own (read, search, edit, shell, GitHub through `gh`).
When a delivery step needs logic that belongs in Deepwater (for example validating a spec against
the GraphQL schema), it can be exposed as an MCP tool from the Spring Boot backend and added to
this plugin through `.mcp.json`, following the same port-and-adapter rule as every other feature.

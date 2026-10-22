# deepwater-developer

One self-contained module: a Claude Code plugin that runs the spec-driven delivery method for
Java/Spring features (GitHub issue → inspection of existing code → specification → smallest
vertical slice behind one use case → narrow-then-wide verification → definition of done), plus the
Deepwater-specific settings it needs. It works in the Claude Code CLI and in the Claude Code
plugin for IntelliJ / JetBrains, which runs that same CLI in the IDE.

**How to use it: see [USAGE.md](USAGE.md).**

## What is in the module

```text
deepwater-developer/
├── README.md                    this file
├── USAGE.md                     step-by-step instructions
├── .claude-plugin/
│   ├── plugin.json              plugin manifest (name: deepwater-developer)
│   ├── marketplace.json         makes this folder installable as a marketplace
│   └── icon.png
├── agents/
│   └── spec-driven-feature.md   the method as an autonomous subagent
├── skills/                      COMMON: the method, independent of any project
│   ├── feature/SKILL.md         /deepwater-developer:feature <issue>
│   ├── spec/SKILL.md            /deepwater-developer:spec <issue>
│   ├── dod/SKILL.md             /deepwater-developer:dod [issue]
│   └── spec-driven-delivery/
│       ├── SKILL.md             the method (owner's agents.md, generic)
│       └── references/
│           ├── architecture-overview.md   the full spec-driven multi-interface architecture
│           ├── spec-template.md
│           ├── adr-template.md
│           └── definition-of-done.md
└── deepwater/                   PROJECT-SPECIFIC: everything true only of this repository
    ├── repository-mapping.md    where specs, ADRs, contracts live; verification commands; current state
    ├── specs-convention.md      naming of docs/specs/ files
    └── adr-0001-spec-driven-delivery-plugin.md   why this form was chosen
```

The split is deliberate: `skills/` and `agents/` contain only the generic method and read the
project-specific facts from `deepwater/repository-mapping.md`. To use the method in another
repository, copy the module, replace the `deepwater/` folder, and point the skills at the new
mapping file.

## Commands and agent

| Item | Invoke as | Does |
| --- | --- | --- |
| `feature` | `/deepwater-developer:feature 123` | All phases in your session: clarify, inspect, specify, implement, verify, report; pauses on open decisions |
| `spec` | `/deepwater-developer:spec 123` | Only the specification; no code changes |
| `dod` | `/deepwater-developer:dod [123]` | Checks the current branch against the definition of done and the spec |
| `spec-driven-feature` agent | `@agent-deepwater-developer:spec-driven-feature implement issue 123` | The same method autonomously, with read, search, edit and shell tools |

The agent and skills never commit or push.

## Install

From the Claude Code prompt in IntelliJ or a terminal:

```text
/plugin marketplace add <absolute path to>/deepwater/deepwater-developer
/plugin install deepwater-developer@deepwater
```

Or, to try it without installing, from the repository root:

```bash
claude --plugin-dir ./deepwater-developer
```

Validate after changes: `claude plugin validate ./deepwater-developer`.

## Requirements

- Claude Code CLI on `PATH` (IntelliJ: Settings → Tools → Claude Code → Claude command).
- `gh` CLI authenticated, so the skills can read issues; otherwise paste the issue text.
- The project's toolchain for the verification phase (Deepwater: JDK 25, Gradle wrapper, Node 22).

## Later: tools in Java

Today the agent's tools are Claude Code's own (read, search, edit, shell, GitHub through `gh`).
When a delivery step needs logic that belongs in Deepwater (for example validating a spec against
the GraphQL schema), it can be exposed as an MCP tool from the Spring Boot backend and added to
this plugin through `.mcp.json`, following the same port-and-adapter rule as every other feature.

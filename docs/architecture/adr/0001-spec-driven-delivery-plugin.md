# ADR-0001: Spec-driven delivery through a Claude Code plugin

## Status

Proposed

## Context

Deepwater features are delivered with AI assistance (Claude Code in IntelliJ and in project
threads). Until now the delivery method lived in chat context and in people's heads: there was no
fixed place for a feature specification, no definition of done, and no rule that one application
use case serves every interface (GraphQL today; MCP, REST or AG-UI later). The product owner wrote
a reusable method (spec-driven, DDD, hexagonal, KISS/YAGNI/DRY, Spring AI boundaries) and asked
for it to run as a plugin with an agent and tools, usable from IntelliJ.

## Decision drivers

- The method must run where the code is edited (IntelliJ or a terminal) with real tools: read,
  search, edit, the project's build, the project's issue tracker.
- The same plugin must serve other repositories, on GitHub or GitLab, without being copied and
  edited.
- Business logic must stay vendor-independent and behind ports; AI output is untrusted input.
- Small team: no new runtime, service or API key handling for the delivery tooling itself.
- The method should be versioned with the code and installable by anyone cloning the repository.

## Options considered

### Option A: Claude Code plugin in the repository (chosen)

Pros:
- Works in the Claude Code JetBrains plugin and CLI today; installs from this repository as a
  marketplace.
- The agent gets file, search, edit and shell tools without any code to write.
- Method, templates and definition of done are versioned beside the code.

Cons:
- Tied to Claude Code as the assistant runtime.
- No Java logic yet; tool behavior is prompt-defined.

### Option B: Native IntelliJ Platform plugin

Pros:
- Independent of any assistant product.

Cons:
- Weeks of Kotlin work, own model API key handling, no built-in code-editing tools; duplicates
  what the assistant runtime already provides.

### Option C: Spring Boot MCP server exposing delivery tools

Pros:
- Deepwater-owned Java tools, testable with the same rules as product code.

Cons:
- Nothing to expose yet; building the server first would violate YAGNI. Can be added to Option A
  later through the plugin's `.mcp.json`.

## Decision

Add `deepwater-developer/` to the repository as one self-contained, project-independent module:
the Claude Code plugin (skills, agent, references) and its own marketplace manifest. Nothing in
the plugin names this repository, GitHub or Gradle; it works in any repository and reads tickets
from GitHub, GitLab or pasted text. Everything that is specific to a repository lives in that
repository, in `docs/delivery/repository-mapping.md`, created by `/deepwater-developer:init` and
reviewed like any other document. Deepwater's own file is at that path. Specifications live in
`docs/specs/<issue-number>-<capability>.md`; ADRs in `docs/architecture/adr/`. The plugin's
agent and skills never commit or push.

## Consequences

- Every feature starts with a spec file and ends with a definition-of-done check; pull requests
  can link both.
- GraphQL stays the only inbound adapter until a consumer needs another; the "one use case, many
  adapters" rule now has a written home.
- The `backend` code does not yet follow the domain / application / adapter package split; the
  brownfield strategy in the method (characterize, extract, wrap) applies one use case at a time
  when an issue touches it. No big-bang refactor.

## Migration and rollback

Introduced by adding one folder and one settings file; no runtime change. Rollback is deleting
`deepwater-developer/` and `docs/delivery/`.

## Evidence

- `deepwater-developer/README.md` for installation and usage.
- `claude plugin validate` output in the pull request that introduces the plugin.

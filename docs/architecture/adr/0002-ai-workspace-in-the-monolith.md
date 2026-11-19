# ADR-0002: The AI workspace lives in the Deepwater monolith, with agents in Claude Code

## Status

Proposed (2026-11-19)

## Context

Issue #53 (August 2026) planned an "AI Team Workspace" as a separate Go service with Kafka events, voice and seven
AI roles. On 2026-11-19 the owner asked for an AI workspace connected to the deepwater-developer plugin, then said
to leave out PitchMind, Go and Kafka, and named five roles: product manager, AI architect, ML engineer,
reliability engineer and algorithm developer.

## Decision

- The workspace is a package in the existing Spring Boot backend (`com.deepwater.workspace`) with a React screen,
  owner only. It stores tasks and an activity log in the same database with JDBC.
- The AI roles are Claude Code agents in the deepwater-developer plugin. They run on the owner's computer and reach
  the workspace through the existing MCP server with four `workspace_` tools.
- Approval, rejection and acceptance are owner-only REST calls. No tool offers them.

## Options considered

1. **Separate Go service with Kafka (the August plan).** Rejected by the owner. It would also add a second runtime,
   a broker and a deploy for one user.
2. **GitHub Issues and Projects as the only workspace.** Cheap, but approval binding, per-role permissions and
   evidence rules cannot be enforced there, and it does not show inside Deepwater.
3. **In the monolith, agents in Claude Code (chosen).** Reuses sign-in, the MCP server and the plugin; one deploy;
   every rule enforced in one service and tested.

## Consequences

- Agents run only while the owner runs Claude Code. There is no server-side agent runner (#86 stays future work).
- The role list exists in `roles.json` and as agent files; `AgentRolesInStepTest` fails when they drift.
- MCP access uses the owner's 7-day session token from an environment variable until long-lived tokens exist.

# Feature plan: AI workspace (first version)

## 1. Ticket metadata

- Ticket: `ando7555/deepwater#53` (AI Team Workspace backlog), re-scoped on 2026-11-19
- Title: AI workspace: one board for the owner's AI team, connected to deepwater-developer
- Owner: Andranik (product owner)
- Product/domain owner: Andranik
- Target release: next frontend and backend deploy after merge (owner's "deploy")
- Status: `IMPLEMENTING`
- Related tickets/MRs: #77-#89 (original workspace slices), #84 #90 #91 #92 closed as not planned (Go, Kafka);
  #105 #108 (deepwater-developer plugin); #120 (MCP server and Ask Deepwater)

## 2. Business outcome

### Problem

Work on Deepwater is spread over chat threads, GitHub issues and the owner's head. The owner wants "an AI
workspace": one place where an AI team (product manager, AI architect, ML engineer, reliability engineer,
algorithm developer) proposes and builds work, connected to the deepwater-developer plugin, so Claude Code becomes
a first "vibe coding IDE" for developing the Ask Deepwater assistant and for speeding up Deepwater generally.

The August backlog (#53) planned a separate Go service, Kafka events, voice and seven roles. On 2026-11-19 the
owner said: "not pitchmind plan go kafka and go get rid of this part" and listed the five roles above.

### Desired outcome

- The owner opens **AI workspace** from the account menu and sees a board: Proposed, Approved, In progress,
  In review, Done.
- AI roles in Claude Code (deepwater-developer agents) read the board, propose tasks, start their approved task and
  hand in evidence through the Deepwater MCP server.
- Every approved task carries a hand-off text that starts `/deepwater-developer:feature` with the task inside.
- Only the owner approves, rejects, accepts or sends back.

### Scope

- Task lifecycle with approval binding, optimistic concurrency, safe retries and an activity log (#83, #85).
- Five role contracts with per-role tool permissions; no role can approve (#79).
- Board, task detail, new task, AI team and "Connect Claude Code" screens (#80).
- MCP workspace tools and a project `.mcp.json` (#82 read-only context is already served by the existing tools).
- Voice room (#81, owner 2026-11-19: "we should speak with audio with those agents ... very important"): the browser
  turns speech into text (Web Speech API), one role answers from its contract through the existing assistant
  orchestration with only its own workspace tools, and the browser reads the answer aloud. Speaking over the agent
  stops it. Nothing spoken can approve.
- Evidence required for review (#87, #88 in part).

### Out of scope

- Go, Kafka, PitchMind (owner, 2026-11-19). Server-side speech or realtime speech-to-speech models (browser speech
  first; revisit if recognition quality is not good enough). Running agents on the server (#86): agents run in the
  owner's Claude Code, on the owner's computer, under the owner's eyes.
- Creating GitHub pull requests from the server. Agents open PRs with their own GitHub access as today.
- Long-lived MCP tokens (tracked in AI_ASSISTANT_AND_MCP.md next steps).

## 3. Actors, interfaces, and authorization

- Actors: the owner (human), five AI roles (Claude Code agents acting with the owner's token).
- Required interface(s): REST (`/api/v1/workspace`) for the screen; MCP (`/mcp`) for the agents.
- Required scope(s)/role(s): the account whose email equals `deepwater.workspace.owner-email`
  (`DEEPWATER_WORKSPACE_OWNER_EMAIL`, default `DEEPWATER_TEACHER_EMAIL`). Everyone else gets 403 and does not see
  the workspace tools in `tools/list`.
- Data classification: `INTERNAL` (task text; no personal data of members).
- Confirmation required for side effects: `YES`, approve and accept are owner-only moves.

## 4. Existing-system discovery

### Repository evidence

- Modules/packages inspected: `com.deepwater.tools`, `com.deepwater.mcp`, `com.deepwater.assistant`,
  `com.deepwater.platform`, `frontend/src/main.jsx`, `frontend/src/AppHeader.jsx`, `frontend/src/ui`.
- Existing entry points: `McpController` (`tools/list`, `tools/call`), `AssistantController`.
- Existing application services/use cases: `PlatformService.requireAccount`, `DeepwaterTools.call`.
- Existing outbound integrations: none needed.
- Existing API/interface contracts: MCP tool registry `DeepwaterTools`.
- Existing tests: `McpServerTest`, `AssistantTest`, `ToolSettingsTest`.
- Existing architecture/security rules: tools run as the token's account; no tool reads other members' data.

### Compatibility constraints

- Existing behavior to preserve: the nine member tools and their order; Ask Deepwater's tool list.
- Backward-compatibility requirement: `YES` (MCP clients already connected).
- Migration or deprecation requirement: new tables only (`workspace_tasks`, `workspace_events`).

### Reuse decision

| Component | Decision | Evidence/reason | Required regression test |
|---|---|---|---|
| `DeepwaterTools` | `EXTEND` | one registry, two doors; add four owner-only tools and per-account listing | `McpServerTest` (member list unchanged) |
| `AssistantService` | `EXTEND` | must not offer workspace tools to the model | `AssistantTest` |
| `PlatformService.requireAccount` | `REUSE` | same session tokens | `WorkspaceApiTest` |
| `com.deepwater.workspace` | `NEW` | no task or approval concept exists | `WorkspaceApiTest` |
| deepwater-developer agents | `NEW` | five role agents beside `spec-driven-feature` | `AgentRolesInStepTest` |

## 5. Specification

### Business rules

1. Only the owner account can read or change workspace data (403 otherwise).
2. Status moves: PROPOSED → APPROVED or REJECTED; APPROVED → IN_PROGRESS; IN_PROGRESS → IN_REVIEW;
   IN_REVIEW → DONE or back to IN_PROGRESS. Any other move is 409.
3. Approve, reject, accept and send back exist only as owner REST calls; no MCP tool offers them.
4. An approval is bound to a SHA-256 of the task text. Editing an approved task with different text moves it back to
   PROPOSED; starting a task whose text no longer matches its approval is refused.
5. An agent may start or hand in only a task assigned to its own role, and only with a tool its contract lists.
6. IN_REVIEW needs evidence of at least 10 characters (PR link and test lines).
7. Repeating a move that already happened returns the task unchanged and logs nothing.
8. Owner moves send the version they saw; a different version is 409.

### Input contract

```text
title: string, required, ≤ 120
product: ECHO | AMBER | HARBOR | ASK_DEEPWATER | AI_FASHION | PLATFORM (case-insensitive)
roleId: product-manager | ai-architect | ml-engineer | reliability-engineer | algorithm-developer
objective: string, required, ≤ 2000
acceptanceCriteria: string[1..12], each ≤ 500
version (owner moves): int, required
```

### Successful output contract

```text
TaskView { id, title, product, roleId, roleName, objective, acceptanceCriteria[], status, proposedBy,
           approvedAt, evidence, version, createdAt, updatedAt, handoff (APPROVED/IN_PROGRESS only), events[] }
```

### Error contract

| Condition | Application category | Client-safe result |
|---|---|---|
| Invalid input | `VALIDATION_ERROR` | 400 ProblemDetail with a plain sentence |
| Not the owner / role not allowed | `FORBIDDEN` | 403 |
| Illegal move or stale version | `CONFLICT` | 409 "Reload it and try again." |
| Unknown task | `NOT_FOUND` | 404 |
| Unexpected failure | `INTERNAL_ERROR` | MCP: "The tool failed on the Deepwater side." |

## 6. Acceptance scenarios

```gherkin
Feature: AI workspace

  Scenario: Only the owner can open the workspace
    Given a signed-in member who is not the owner
    When they ask for the workspace tasks or list MCP tools
    Then they get 403 and no workspace_ tool is listed

  Scenario: The five roles are contracts and none can approve
    Given the owner
    When they open the AI team
    Then they see product-manager, ai-architect, ml-engineer, reliability-engineer, algorithm-developer
    And no role lists an approve tool

  Scenario: A task reaches done only through the owner's approval and evidence
    Given the product-manager agent proposes a task for the ml-engineer
    When the ml-engineer tries to start it before approval
    Then it is refused
    When the owner approves it with a stale version
    Then it is refused with 409
    When the owner approves it
    Then it has a hand-off starting "/deepwater-developer:feature"
    When another role tries to start it
    Then it is refused
    When the ml-engineer starts it twice and hands in "done"
    Then it is in progress once and the hand-in is refused for missing evidence
    When the ml-engineer hands in a PR link and test lines and the owner accepts
    Then the task is DONE with the activity PROPOSED, APPROVED, STARTED, SUBMITTED, ACCEPTED

  Scenario: Editing an approved task needs a new approval
    Given an approved task
    When the owner changes its title
    Then it is PROPOSED again and cannot be started
```

Evidence: `backend/src/test/java/com/deepwater/workspace/WorkspaceApiTest.java` (one test per scenario, plus bad
input), `AgentRolesInStepTest`, `frontend/src/workspace/workspaceTools.test.mjs`.

## 7. DDD and application design

- Bounded context: Workspace (internal tooling), separate package `com.deepwater.workspace`.
- Aggregate root(s): WorkspaceTask. Value objects: TaskStatus, Actor. Invariants: rules 2, 4, 6, 8.
- Domain events: appended to `workspace_events` (activity log, not a message bus).
- Transaction boundary: one move per transaction; conditional update on version.

## 8. Interface contracts

- REST: `POST /api/v1/workspace/roles/{roleId}/talk` (voice room turn: `{messages}` → `{roleName, text, toolsUsed}`),
  `GET /api/v1/workspace/access|roles|tasks|tasks/{id}`, `POST /tasks`, `PUT /tasks/{id}`,
  `POST /tasks/{id}/approve|reject|start|evidence|accept|send-back`.
- MCP: `workspace_tasks` (READ), `workspace_propose_task`, `workspace_start_task`, `workspace_submit_evidence`
  (WRITE, own task only). Confirmation: approval happens in the screen, not in the tool.

## 9. AI and Spring AI behavior

Board: `DETERMINISTIC`. Voice room: `AI_ASSISTED` through the existing `AssistantModel` port (Claude with
`ANTHROPIC_API_KEY`, or Ollama); without a model it says it is off. The role prompt is built from `roles.json`; tool
calls run with the role id set by our code (`WorkspaceVoice.toolsFor`), so a role cannot act as another. Shares the
assistant's hourly limit. Coding agents are Claude Code sessions on the owner's computer.

## 10. Implementation plan

| Step | Files/modules | Evidence required | Owner |
|---|---|---|---|
| 1 | this plan | owner review | Claude |
| 2 | `schema.sql`, `workspace/*` | `WorkspaceApiTest` | Claude |
| 3 | `DeepwaterTools`, `McpController`, `AssistantService` | `McpServerTest`, `AssistantTest` | Claude |
| 4 | `deepwater-developer/agents/*.md`, `roles.json` | `AgentRolesInStepTest` | Claude |
| 5 | `frontend/src/workspace/*`, `main.jsx`, `AppHeader.jsx` | `npm test`, `npm run build`, screenshots | Claude |
| 6 | `.mcp.json`, docs | review | Claude |

## 11. KISS, YAGNI, and DRY review

- KISS: one package, JDBC like the rest of the backend, no new dependency.
- YAGNI: no Go, Kafka, voice, server-side agent runner or GitHub writes from the server.
- DRY: one tool registry for MCP; role data in one JSON with the agents kept in step by a test.

## 12. Security, privacy, and resilience

- Inbound authentication: session token; owner check on every call.
- Tenant/data boundary: owner only; no member data in tasks.
- Input limits: see section 5. Idempotency: rule 7. Concurrency: rule 8.
- Prompt injection: task text is data. Agents are told so in their contract; no tool can approve.
- The session token in `.mcp.json` comes from the `DEEPWATER_TOKEN` environment variable, never the file.

## 13. Observability and audit

- Audit event: `workspace_events` (actor, action, detail, time), shown as "Activity" in the task detail.
- Metrics/alerts: not required for an owner-only tool.

## 14. Test plan and evidence

- [x] Domain and use-case behaviour through `WorkspaceApiTest` (5 tests)
- [x] Adapter contract: MCP list per account, tool calls, REST status codes
- [x] Regression: `McpServerTest`, `AssistantTest`, `ToolSettingsTest`
- [x] Voice room: `WorkspaceVoiceRoomTest` (scripted model, real tools)
- [x] Frontend rules: `workspaceTools.test.mjs`

Commands/evidence are pasted in the pull request.

## 15. ADR and migration

ADR required: `YES`, `docs/architecture/adr/0002-ai-workspace-in-the-monolith.md`.
Rollback: revert the merge; the two new tables are unused by everything else.

## 16. Risks and open questions

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Owner email setting not set on Railway | MEDIUM | defaults to `DEEPWATER_TEACHER_EMAIL`; set `DEEPWATER_WORKSPACE_OWNER_EMAIL` if different | Andranik |
| 7-day session token for MCP | LOW | env variable only; long-lived tokens are a next step | Claude |

Open questions:

- [ ] Is the owner account the same as the teacher account on Railway? — owner: Andranik
- [ ] Should the remaining #53 tickets (#77-#83, #85-#89) be closed as covered by this version? — owner: Andranik

## 17. Definition of done

- [ ] Specification approved and linked to the ticket.
- [x] Existing behavior and compatibility constraints are documented.
- [x] Domain rules and use case are implemented independently of transport.
- [x] Required interface contract is tested.
- [x] Unit, contract and security tests pass.
- [x] No credentials or production data are included.

## 18. Review record

- Product/domain approval: pending (Andranik)
- Implementation MR: see the pull request linked from #53

# Deepwater AI workspace

One place for everything you use to build Deepwater with AI: the **deepwater-developer** tool (a Claude Code
plugin), your **five AI agents**, and how both connect to the **AI workspace** screen in the app.

```
 You (voice or text)                                         You (keyboard, in IntelliJ or a terminal)
        │                                                                     │
        ▼                                                                     ▼
 Deepwater app > AI workspace                         Claude Code + deepwater-developer plugin
   Board · Talk · New task · AI team · Connect           /deepwater-developer:feature, :spec, :dod, :course, :help
        │  approve / accept (only you)                     five role agents (product-manager, ai-architect, ...)
        ▼                                                                     │
 Deepwater backend  ◄────────────── Deepwater MCP server (/mcp) ──────────────┘
   tasks, roles, activity log        workspace_tasks · workspace_propose_task · workspace_start_task ·
                                     workspace_submit_evidence  (no tool can approve)
```

## What is in this folder

| Path | What it is |
| --- | --- |
| [`deepwater-developer/`](deepwater-developer/README.md) | The Claude Code plugin: mentor, the "Become an AI engineer" course, spec-driven delivery (`:init`, `:spec`, `:feature`, `:dod`, `:help`, `:course`) and the agents |
| [`deepwater-developer/agents/`](deepwater-developer/agents/) | Your AI team as Claude Code agents: `product-manager`, `ai-architect`, `ml-engineer`, `reliability-engineer`, `algorithm-developer` (plus `spec-driven-feature`) |
| [`roles.json`](roles.json) | The same five roles as data for the app (purpose, does, never, hands back, allowed tools). The backend build copies it in; `AgentRolesInStepTest` fails if it and the agent files drift apart |
| [`deepwater-developer/START-HERE.md`](deepwater-developer/START-HERE.md) | One page for someone new to Claude Code |

Everything else lives where its build or convention needs it, and is listed here so you never have to search:

| Part | Where |
| --- | --- |
| Workspace backend (tasks, approvals, voice room, roles) | `backend/src/main/java/com/deepwater/workspace/` |
| MCP tools the agents use | `backend/src/main/java/com/deepwater/tools/DeepwaterTools.java` (`workspace_*`) |
| Workspace screen | `frontend/src/workspace/` (Workspace.jsx, VoiceRoom.jsx, workspaceTools.js) |
| Claude Code ↔ Deepwater connection | [`../.mcp.json`](../.mcp.json) (reads `DEEPWATER_TOKEN` from your environment) |
| Plan, decision, settings | [`docs/specs/53-ai-workspace.md`](../docs/specs/53-ai-workspace.md), [`docs/architecture/adr/0002-ai-workspace-in-the-monolith.md`](../docs/architecture/adr/0002-ai-workspace-in-the-monolith.md), [`docs/delivery/repository-mapping.md`](../docs/delivery/repository-mapping.md) |
| Tickets | GitHub issue #53 (workspace backlog) |

## Start in five minutes

1. **Install or update the plugin** (inside Claude Code, in this repository):
   ```
   /plugin marketplace add <path-to>/deepwater/ai-workspace/deepwater-developer
   /plugin install deepwater-developer@deepwater
   ```
   Had it installed from the old path (`deepwater/deepwater-developer`)? Remove it first:
   `/plugin marketplace remove deepwater`, then add the new path above.
2. **Connect Claude Code to your board.** Sign in to the app, open the browser console, run
   `sessionStorage.getItem('deepwater-token')` and put the value in an environment variable `DEEPWATER_TOKEN`
   (never in a file or chat). Open Claude Code in the repository folder and approve the `deepwater` MCP server.
3. **Work.** In the app: AI workspace > Talk (voice) or New task, approve on the Board, press "Copy for Claude
   Code" and paste it into Claude Code. Or in Claude Code: *"use the product-manager agent to read the workspace
   board"*.

## Rules that never change

- Only you approve, reject, accept, merge or deploy. Agents propose, build and hand in evidence.
- An approval is for the exact task text; if the text changes, it comes back to you.
- A task reaches review only with a pull request link and real test lines.
- No Go, no Kafka, no PitchMind in this workspace (owner decision, 2026-11-19).

## AI model for the voice room

The Talk tab uses the backend's assistant model: `ASSISTANT_PROVIDER=anthropic` with `ANTHROPIC_API_KEY`, or
`ASSISTANT_PROVIDER=ollama` with `OLLAMA_BASE_URL` and `OLLAMA_MODEL` (for example Hermes on your own PC through a
Cloudflare tunnel). Without one, it answers that the assistant is switched off.

**Free option (Gemini):** set `ASSISTANT_PROVIDER=gemini` and `OPENAI_COMPAT_API_KEY=<key from Google AI Studio>`.
The model defaults to `gemini-3.5-flash-lite` (change with `OPENAI_COMPAT_MODEL`). On Google's free tier your prompts may be
used by Google to improve its products. `ASSISTANT_PROVIDER=groq` works the same way with a Groq key, and
`ASSISTANT_PROVIDER=openai-compatible` takes any OpenAI-style API (`OPENAI_COMPAT_BASE_URL`, `_MODEL`, `_COMPANY`).

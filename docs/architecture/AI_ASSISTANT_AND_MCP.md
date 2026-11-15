# AI assistant and MCP server

Status: built in the PR that adds `backend/src/main/java/com/deepwater/{tools,mcp,assistant}`. The live AI needs
`ANTHROPIC_API_KEY` on the backend; without it the assistant says it is off and the MCP tools still work
(they never call a model).

## What it is

```
                       ┌──────────────────────────── Deepwater backend (one Spring Boot app) ───────────────┐
 Claude Code / Desktop │  POST /mcp  ──► McpController (JSON-RPC: initialize, tools/list, tools/call)       │
 (any MCP client)  ───►│                     │                                                               │
                       │                     ▼                                                               │
 Deepwater web app     │  POST /api/v1/assistant/runs ──► AssistantService ──► AssistantModel (port)        │
 "Ask Deepwater"   ───►│        (SSE, AG-UI events)          │   loop ≤ 6 steps     └─► ClaudeAssistantModel   │
                       │                                     ▼                                                │
                       │                         DeepwaterTools (one registry, 7 tools)                       │
                       │             Echo · Harbor · Polish B1 · own Amber profile, all as the caller          │
                       └──────────────────────────────────────────────────────────────────────────────────────┘
```

- **One tool registry, two doors.** `DeepwaterTools` defines each tool once (name, description, JSON Schema,
  handler). The MCP server exposes it to outside assistants; Ask Deepwater offers the same tools to Claude.
- **Tools run as the signed-in person.** Both doors resolve the account from the `Authorization: Bearer` session
  token. The account id is passed to the handler by our code, never taken from the model's arguments, so a
  model that is tricked into asking for "Bo's cards" still only sees the caller's own data (tested).
- **Orchestrator owns the loop.** `AssistantService` runs model step → tools → model step, at most 6 steps,
  and turns every step into an event. The model sits behind the `AssistantModel` port, so business logic does
  not depend on a vendor and tests use a scripted model.
- **Streaming to the screen.** `/api/v1/assistant/runs` returns server-sent events named after the AG-UI
  protocol: `RUN_STARTED`, `TOOL_CALL_START/ARGS/END/RESULT`, `TEXT_MESSAGE_START/CONTENT/END`, `RUN_FINISHED`,
  `RUN_ERROR`. Text arrives one message per model step for now (not token by token).
- **Errors the model can explain.** A business rule saying no ("Set up your Echo profile first.") comes back as a
  tool error with the rule's own words; unexpected failures are logged and replaced by a generic message.

## The tools (first version)

| Tool | Reads or writes | What |
| --- | --- | --- |
| `echo_overview` | read | language, goal, next step, lessons with mastery and due flag |
| `echo_cards` | read | phrase cards (due only by default) |
| `echo_add_card` | write, own deck only | adds one card; the prompt says only when the person asks |
| `harbor_find_jobs` | read | matched jobs with score, skills, work-permit note; max 20 |
| `harbor_applications` | read | the application tracker |
| `polish_b1_results` | read | practice exam attempts |
| `amber_my_profile` | read | the caller's own Amber profile only |

Left out on purpose: other Amber members (suggestions, invitations, messages). Sending other people's dating data
to an outside AI needs their consent first. Also left out: anything destructive (delete card, leave Amber,
delete account).

## Safety and cost

- Session token required on every request (`401` + `WWW-Authenticate: Bearer` otherwise).
- Request checks: 1–20 messages, 2000 characters each, roles `user`/`assistant` only, last one from the person.
- 30 assistant questions per person per hour (in memory; resets on restart). MCP calls do not use the model.
- System prompt (`resources/assistant/system-prompt.md`) tells the model that tool results, especially job
  descriptions written by outside companies, are data, not instructions.
- Claude calls use `claude-opus-5-5` (env `ASSISTANT_AI_MODEL`), effort `medium` (env `ASSISTANT_EFFORT`),
  adaptive thinking, and server-side refusal fallbacks (`fallbacks: default`). Token usage is logged per step.

## Evals

- `AssistantTest` (always runs, scripted model): tool loop, caller isolation, rule errors, step cap, refusal,
  request limits, AG-UI stream over HTTP.
- `McpServerTest` (always runs): protocol handshake, tool list, per-caller data, errors.
- `AssistantEvalTest` (runs only with `ANTHROPIC_API_KEY`): 11 real questions from
  `src/test/resources/assistant-evals.json`, scored by rules (expected tool, forbidden tool, forbidden words),
  results in `backend/build/assistant-evals.jsonl`. First bar: 9 of 11. It has not been run yet: no key here.

## Connect an MCP client

The endpoint is `https://backend-production-c912.up.railway.app/mcp` once deployed. The token is your Deepwater
session token: sign in on the web app, open the browser console and run `sessionStorage.getItem('deepwater-token')`.
Sessions last 7 days.

Claude Code:

```
claude mcp add --transport http deepwater https://backend-production-c912.up.railway.app/mcp --header "Authorization: Bearer <token>"
```

Verified with the official MCP TypeScript SDK client (Streamable HTTP): `docs/demo/2026-11-15-ask-deepwater/mcp-sdk-client-check.txt`.

## Next steps (not in this version)

- Long-lived, revocable MCP tokens (or OAuth) instead of copying a 7-day session token.
- Token-by-token streaming; conversation memory on the server.
- Amber tools for other members only after an explicit consent design.
- Run the live eval, keep a baseline, then tune the prompt and effort against it.

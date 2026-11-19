# AI assistant and MCP server

Status: built in PR #120 (`backend/src/main/java/com/deepwater/{tools,mcp,assistant}`). The assistant needs a
model: hosted Claude (`ANTHROPIC_API_KEY`) or a local model on Ollama (`ASSISTANT_PROVIDER=ollama`). Without one it
says it is off. The MCP tools work either way: they never call a model.

## What it is

```
                       ┌──────────────────────────── Deepwater backend (one Spring Boot app) ───────────────┐
 Claude Code / Desktop │  POST /mcp  ──► McpController (JSON-RPC: initialize, tools/list, tools/call)       │
 (any MCP client)  ───►│                     │                                                               │
                       │                     ▼                                                               │
 Deepwater web app     │  POST /api/v1/assistant/runs ──► AssistantService ──► AssistantModel (port)        │
 "Ask Deepwater"   ───►│        (SSE, AG-UI events)          │   loop ≤ 6 steps     └─► ClaudeAssistantModel   │
                       │                                     ▼                                                │
                       │                         DeepwaterTools (one registry, 9 tools)                       │
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
| `danish_pd3_results` | read | Danish PD3 practice exam attempts |
| `german_b1_results` | read | German B1 practice exam attempts |
| `amber_my_profile` | read | the caller's own Amber profile only |
| `workspace_tasks` | read, owner only | the owner's AI workspace board (see `docs/specs/53-ai-workspace.md`) |
| `workspace_propose_task` | write, owner only | an AI role proposes a task; it waits for the owner's approval |
| `workspace_start_task` | write, owner only | the assigned role starts an approved task |
| `workspace_submit_evidence` | write, owner only | the assigned role hands in work with a PR link and test lines |

The four `workspace_` tools are listed over MCP only to the workspace owner and are never offered to Ask Deepwater.
No tool approves, rejects or accepts: those are the owner's moves in the workspace screen.

Left out on purpose: other Amber members (suggestions, invitations, messages). Sending other people's dating data
to an outside AI needs their consent first. Also left out: anything destructive (delete card, leave Amber,
delete account).

## Settings (nothing below needs a code change)

| Environment variable | Default | What it changes |
| --- | --- | --- |
| `ASSISTANT_PROVIDER` | `auto` | `auto` (Claude if a key is set, else off), `anthropic`, `ollama`, `off` |
| `ANTHROPIC_API_KEY` | (empty) | key for hosted Claude |
| `ASSISTANT_AI_MODEL` / `ASSISTANT_EFFORT` | `claude-opus-5-5` / `medium` | Claude model and effort |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | `http://localhost:11434` / `hermes3` | local model on Ollama |
| `ASSISTANT_TIMEOUT_SECONDS` | `120` | how long one model step may take |
| `ASSISTANT_MAX_STEPS` | `6` | model steps per question |
| `ASSISTANT_MAX_HISTORY` / `ASSISTANT_MAX_MESSAGE_CHARS` | `20` / `2000` | request limits |
| `ASSISTANT_RUNS_PER_HOUR` | `30` | questions per person per hour |
| `ASSISTANT_SYSTEM_PROMPT` | `classpath:assistant/system-prompt.md` | the model's instructions; `file:/path/prompt.md` works too |
| `ASSISTANT_SUGGESTIONS` | three starter questions | comma-separated starter questions in the chat panel |
| `DEEPWATER_TOOLS_ENABLED` | (all) | comma-separated tool names offered to the assistant and over MCP |

The chat panel reads `GET /api/v1/assistant` (on/off, model, tool titles, starter questions, message limit), so
those are not hard-coded in the frontend either.

## Local AI with Ollama (Hermes)

```
ollama pull hermes3
ASSISTANT_PROVIDER=ollama OLLAMA_MODEL=hermes3 java -jar backend.jar      # or: gradle :backend:bootRun
```

`OllamaAssistantModel` calls Ollama's `POST /api/chat` with tools and `stream: false`, following Ollama's API
documentation (tool calls come back as `message.tool_calls[].function{name, arguments}`, results go back as
`role: tool` with `tool_name`). It is tested against a stand-in server that answers in that shape; it has **not
yet been run against a real Ollama** (this sandbox cannot download Ollama or models). Limits to know:
- The Railway backend cannot reach an Ollama on your own PC. Local AI is for running Deepwater locally, or for a
  server where Ollama runs next to the backend.
- Small local models choose tools less reliably than Claude. Measure with the eval below before relying on it.

## Permission before data leaves (App Store 5.1.2(i))

When the answering model is run by an outside company (Claude: Anthropic), each person agrees once, in the chat
panel, with an unticked box: "Send my questions and my Deepwater data to Anthropic". The server stores it in
`assistant_consents` (account, company, time) and refuses a run with 403 until it is there; the screen only asks.
`PUT /api/v1/assistant/consent` gives it, `DELETE` withdraws it ("Stop sending my data" link in the panel), and
`GET /api/v1/assistant` reports `sharesDataWith` and `consentGiven`. A different company asks again. A self-hosted
Ollama shares with nobody, so no question is asked. Deleting the account deletes the row. MCP is not gated: there
the person connects their own AI client to their own data.

## Safety and cost

- Session token required on every request (`401` + `WWW-Authenticate: Bearer` otherwise).
- Request checks: 1–20 messages, 2000 characters each, roles `user`/`assistant` only, last one from the person.
- 30 assistant questions per person per hour (in memory; resets on restart). MCP calls do not use the model.
- System prompt (`resources/assistant/system-prompt.md`) tells the model that tool results, especially job
  descriptions written by outside companies, are data, not instructions.
- Claude calls use adaptive thinking and server-side refusal fallbacks (`fallbacks: default`). Token usage is
  logged per step for both providers.

## Evals

- `AssistantTest` (always runs, scripted model): tool loop, caller isolation, rule errors, step cap, refusal,
  request limits, AG-UI stream over HTTP.
- `McpServerTest` (always runs): protocol handshake, tool list, per-caller data, errors.
- `OllamaAssistantModelTest` (always runs): request and reply shape against a stand-in Ollama, provider setting.
- `ToolSettingsTest` (always runs): `deepwater.tools.enabled` really narrows the tools.
- `AssistantEvalTest` (runs only with `ASSISTANT_EVAL=true`): 11 real questions from
  `src/test/resources/assistant-evals.json`, scored by rules (expected tool, forbidden tool, forbidden words),
  results in `backend/build/assistant-evals.jsonl`. First bar: 9 of 11. Works with Claude or with
  `ASSISTANT_PROVIDER=ollama`, so you can compare a local Hermes against Claude on the same questions.
  It has not been run yet (no key and no Ollama here).

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

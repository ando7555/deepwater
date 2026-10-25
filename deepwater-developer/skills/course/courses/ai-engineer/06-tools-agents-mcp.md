# 06 Tools, agents and MCP

**Time:** about 5 hours. **Prerequisites:** 04 (05 helps).

## Why this matters

"Agent" is the most overused word in AI. Sometimes a model that chooses its own steps is exactly
right; most of the time a fixed workflow in your code is cheaper, faster and easier to test. You
need to know how tools and agents work, and when to say no.

## Talk it through

- "Describe the steps of your feature. Which steps could you write down in advance?" (Those belong
  in code. Only the open-ended part may justify an agent.)
- "What is the worst thing the model could do if it could call every tool you give it?"

## Core ideas

- **Tool use (function calling):** you describe tools with a name, a description and a JSON
  schema. The model answers with a request to call a tool with arguments; *your code* validates the
  arguments, runs the tool, and sends the result back. The model never runs anything itself.
- **The loop:** call model → if it asks for tools, run them and send results → repeat until it
  answers. Limit the number of rounds, the time and the cost.
- **Workflow before agent:** a single call, then a fixed chain of calls, then an agent. Choose an
  agent only when the steps cannot be known in advance, the value justifies the cost, and mistakes
  can be caught (tests, review, undo).
- **Human in the loop:** any tool that changes something that matters (publish, send, pay, delete)
  needs an approval step outside the model.
- **MCP (Model Context Protocol):** a standard way to offer tools and data to any AI client
  (Claude Code, IDEs, other assistants). In a Spring application an MCP server is just another
  inbound adapter that calls the same use case as REST or GraphQL; no business logic in the
  adapter. Build one when an AI client needs your system, not before.
- **Agent frameworks** (Spring AI, LangChain4j, vendor agent SDKs) save loop code; put them behind
  your ports like any other library.

## Lab

1. Turn "draft exercises" (module 03) into a tool with a strict JSON schema. Build a tool loop
   through `ModelGateway` or the provider's SDK: the model may call `find_passages` (module 05, or
   a stub) and `draft_exercises`; your code validates every argument.
2. Add limits: at most five rounds, a total timeout, and a token budget. Test that each limit stops
   the loop cleanly (with the fake gateway scripting the model's tool calls).
3. Make publishing impossible from the loop: the result is a draft that a teacher approves through
   the normal application flow. Show the test that proves it.
4. Discuss with the mentor whether this feature *needs* the loop at all, or a fixed workflow. Write
   the decision.
5. Optional: expose the drafting use case as an MCP tool and call it from Claude Code.

## Check your understanding

- "The model asked to call `publish_lesson` with valid arguments. What stops it?" (It is not in the
  tool list; if it were, your code would require a human approval.)
- "Why is an MCP server an adapter and not a new service?" (It is one more entry point to the same
  use case; the rules live in the use case.)

## Done when

- [ ] A tool loop with validated arguments and enforced limits exists; the mentor ran the tests for
      each limit.
- [ ] A test proves nothing is published without human approval.
- [ ] A written decision: agent, fixed workflow or single call, with the reason.

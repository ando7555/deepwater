# Course: Become an AI engineer

**For:** a developer who already ships backend software (written for Java and Spring; the ideas
work in any language) and wants to build AI features that are correct, safe and affordable.
**Not for:** training models from scratch. This course is about *using* models well inside real
products, which is what most AI engineering jobs are.
**Time:** about 25 to 35 hours, in sessions of 45 to 90 minutes. Nine modules.
**Needs:** a computer with the project's JDK, Git, and one of: an API key for any provider, a
company AI gateway, or Ollama on your laptop (free). Module 02 starts with a fake model, so you can
begin with nothing.

## What you will be able to do at the end

1. Explain how a language model produces an answer, and reason about tokens, context, effort,
   latency and cost before you write code.
2. Call any model provider from Java through one port, switch providers by configuration, and test
   your logic without a network.
3. Treat prompts as versioned code, and get structured output you validate like any other input.
4. Build an evaluation set and make every AI change by comparing numbers, not impressions.
5. Decide when retrieval, tools, an agent or MCP are justified, and keep a human in the loop.
6. Ship an AI feature with secrets, privacy, timeouts, retries, cost limits and a deterministic
   fallback, through a specification, tests and a definition of done.

## Modules

| # | Module | Level earned | Hours |
| --- | --- | --- | --- |
| 00 | [Welcome and setup](00-welcome-and-setup.md) | | 1 |
| 01 | [How language models behave](01-how-models-behave.md) | | 3 |
| 02 | [Call any model from Java](02-call-any-model.md) | **Level 1: AI integrator** after 00-02 | 4 |
| 03 | [Prompts as code and structured output](03-prompts-and-structured-output.md) | | 4 |
| 04 | [Evaluate before you trust](04-evaluation.md) | **Level 2: AI practitioner** after 03-04 | 5 |
| 05 | [Retrieval with provenance](05-retrieval.md) (optional if your product has no documents) | | 4 |
| 06 | [Tools, agents and MCP](06-tools-agents-mcp.md) | | 5 |
| 07 | [Production: safety, cost, failure](07-production.md) | **Level 3: AI engineer** after 05-07 | 4 |
| 08 | [Capstone: ship an AI feature](08-capstone.md) | **Certificate** | 6+ |

Levels are milestones the mentor records in your progress file. The certificate needs every
required module complete with evidence, and the capstone pull request.

## How a session works

You type `/deepwater-developer:course`. The mentor reads your progress, reminds you where you
stopped, and teaches the next small step. You write the code; the mentor reviews it, asks
questions, runs your tests to see the truth, and records what you showed. Every session ends with
what you did, what you understood, and one next step.

## The running example

If you do not bring your own project, the labs use one example from the Deepwater product Echo (a
language-learning app): **an assistant that drafts practice exercises for a teacher to review**.
A teacher gives a grammar topic and a level; the model drafts exercises as structured JSON; the
application validates them; the teacher approves or edits; nothing reaches learners without that
approval. It is small, it is real, and it touches every module: prompts, structured output,
evaluation, retrieval from lesson sources, a tool, cost limits and human review.

## House rules

- Your keys stay in environment variables. Never paste a key into the chat; if it happens, revoke
  it and make a new one.
- Use made-up or public data in labs. Never send personal data to a model in this course.
- The mentor never claims a result it did not see, and neither should you. "It works" means "I ran
  it and here is the output".

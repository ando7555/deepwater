# 02 Call any model from Java

**Time:** about 4 hours. **Prerequisites:** 00, 01. **Earns Level 1: AI integrator** with 00 and 01.

## Why this matters

The first AI call in a codebase decides its future. Call a vendor SDK straight from a service and
you have married that vendor: tests need the network, switching costs a rewrite, and comparing
providers is impossible. One interface (a port) and small adapters cost an hour and keep every
door open.

## Talk it through

- "How do you call a payment provider or a database from your domain code today?" (Through an
  interface, a repository, a client bean. Same idea here.)
- "What would you need to change to try a cheaper provider next month?"
- "How do you test code that calls the model, without paying for every test run?"

## Core ideas

- **The port** (`ModelGateway`): one method, `complete(request)`, plain Java records in and out,
  no vendor types. The use case depends only on it.
- **Adapters:** `ClaudeGateway` (Anthropic's official Java SDK), `OpenAiCompatibleGateway` (one
  HTTP adapter for OpenAI, Ollama, OpenRouter, Groq, Mistral, Gemini's compatible endpoint and most
  company gateways), `FakeModelGateway` (tests and demos).
- **Configuration chooses:** `AI_PROVIDER`, `AI_MODEL`, `AI_BASE_URL`, and the *name* of the key
  variable. In Spring Boot, one `@Configuration` with `@Bean ModelGateway`.
- **One error type** with a `retryable` flag: 429 and 5xx retry later, 4xx never.
- **Timeouts on every call.** A model call can take tens of seconds; your HTTP thread pool cannot
  wait forever.

The reference implementation (compiled, with four passing tests) is in the plugin at
`skills/ai-provider-integration/references/java/`. The provider table with base URLs is
`skills/ai-provider-integration/references/providers.md`. Show these only after the learner has
tried step 1 and 2 themselves.

## Lab

1. **The port.** The learner writes `ModelGateway` with request and completion records. Review:
   no vendor imports, validation in the record constructor, token counts in the result.
2. **The fake first.** Write `FakeModelGateway` and one use-case test with it (for example: "the
   exercise drafter sends the topic and level in the prompt"). Run it. It must pass with the
   network cable pulled.
3. **The OpenAI-compatible adapter.** Build the request JSON (`model`, `max_tokens`, `messages`
   with a `system` message first), send it with `java.net.http.HttpClient` and a timeout, parse
   `choices[0].message.content` and `usage`. Test it against a local `HttpServer` that returns a
   canned answer, including a 429 case.
4. **The Claude adapter** (if they have Claude access, or as a reading exercise): the official SDK,
   `AnthropicOkHttpClient.fromEnv()`, `MessageCreateParams.builder().model(...).maxTokens(...)`,
   system prompt in its own field, text from the content blocks, usage from the response.
5. **Configuration.** `ModelGateways.fromEnvironment(System.getenv())`, or a Spring
   `@Configuration`. Switch between fake, Ollama and a hosted provider without changing code, and
   show one live call through two different providers.

## Check your understanding

- "Your use-case test failed after you switched to Ollama. Is that a test problem?" (The use-case
  test should use the fake and not care. A failing live call is an adapter or configuration issue,
  found by a separate, explicit integration run.)
- "Why does the configuration hold the *name* of the key variable and not the key?" (So the value
  never sits in a properties file or in Git; each environment sets the variable.)
- "A provider returns 400 for one request. Should you retry?" (No: the request is wrong; fix it.)

## Done when

- [ ] The port, the fake and at least one real adapter exist in the learner's repository.
- [ ] The mentor ran the tests and saw them pass without a network (record the result line).
- [ ] One live call worked through configuration only, or the learner chose to stay on the fake
      for now (record which).
- [ ] The learner explained why the use case depends on the port and not the SDK.

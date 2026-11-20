---
name: ai-provider-integration
description: Add AI to an application without tying it to one vendor - one ModelGateway port with swappable adapters for Claude, OpenAI, any OpenAI-compatible service (Ollama, OpenRouter, Groq, Mistral, Gemini's compatible endpoint, company gateways) and a fake for tests; provider chosen by configuration, keys only from environment variables. Use when the user wants to call a language model from their code, switch or compare providers, add a local model, or when a specification's AI section needs a provider design.
---

# Provider-independent AI integration

The application talks to **a model**, never to a vendor. Business code depends on one small
interface (the port). Each provider is an adapter behind it. Which adapter runs is configuration.
This keeps the domain testable without a network, lets you compare providers with the same
evaluation set, and means a price change or an outage is a configuration change, not a rewrite.

This is the hexagonal rule of the `spec-driven-delivery` method applied to AI: the model gateway
is an outbound port, like a repository or a payment client.

## The shape

```text
use case (your code)  ──>  ModelGateway (port, your interface)
                              ├── FakeModelGateway          tests, demos, no key
                              ├── ClaudeGateway             Anthropic Java SDK
                              └── OpenAiCompatibleGateway   OpenAI, Ollama, OpenRouter, Groq,
                                                            Mistral, Gemini (compatible endpoint),
                                                            company gateways: same HTTP format
```

A complete, compiled and tested Java version is in `${CLAUDE_SKILL_DIR}/references/java/`:
`main/` holds the port, the three adapters and `ModelGateways` (chooses the adapter from
environment variables); `test/` holds four JUnit tests that run without a network (a local HTTP
server stands in for the provider). `build.gradle.example` lists the two dependencies. It was
compiled and its tests run against `com.anthropic:anthropic-java:2.34.0` and Jackson 2.20 on JDK 25.
Copy it into the learner's project and adapt the package name; in Spring Boot, turn
`ModelGateways` into a `@Configuration` class with one `@Bean ModelGateway`.

Which providers exist, their base URLs, where their model lists are, and what differs between
them is in `${CLAUDE_SKILL_DIR}/references/providers.md`. Read it before naming a provider value.

## Rules

1. **Keys come from the environment**, never from code, `application.properties` in Git, the
   chat, or a test. The adapter reads the variable; the configuration names the variable. If a
   key appears in a chat or a commit, tell the person to revoke it in the provider's console now.
2. **Model ids, prices and limits change.** Take them from the provider's documentation at the
   time you write the configuration, and write the date and source next to it. Do not invent a
   model name.
3. **One exception type out of the port** (`ModelGatewayException` with `retryable`), so callers
   handle failure the same way for every provider. Retry only retryable failures, a bounded
   number of times, with a timeout on every call.
4. **Never log prompts or responses that can contain personal data**, and never put a provider's
   response body in an exception message.
5. **Validate what comes back.** Model output is untrusted input: parse it against a schema,
   reject or repair, and never execute it.
6. **Have a deterministic fallback** for every AI feature: what the application does when the
   model is down, too slow, too expensive or wrong. Often that is "show the human the empty form".
7. **Choose a provider with numbers.** Run the same evaluation set against two adapters and
   compare quality, latency and cost per completed task before switching.
8. **Do not add a framework you do not need.** The port is ~40 lines. Spring AI, LangChain4j and
   similar libraries are fine choices when you want their extras (streaming helpers, vector stores,
   tool calling across providers); put them behind the same port.

## Using it in a specification

In section 9 ("AI and Spring AI behavior") of the feature plan, record: the port and which
adapter is the default, the configuration variables, the model id with its source and date, the
output schema, the evaluation set, the timeout, retry and cost limits, the fallback, and what data
is allowed to leave the system.

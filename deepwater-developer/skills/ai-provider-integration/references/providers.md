# Providers and how to reach them

> Checked 2026-10-25. Base URLs and formats are stable, but model ids, prices and limits change
> often: always confirm on the provider's own pages before you configure one, and write the date
> next to the value you chose. Nothing below is a price or a recommendation of a specific model.

## Two adapters cover almost everything

| Provider | Adapter | `AI_BASE_URL` | Key variable (suggested name) | Where to find model ids |
| --- | --- | --- | --- | --- |
| Anthropic (Claude) | `ClaudeGateway` (official Java SDK) | not needed | `ANTHROPIC_API_KEY` (the SDK reads it) | <https://docs.claude.com> → Models overview |
| OpenAI | `OpenAiCompatibleGateway` | `https://api.openai.com/v1` | `OPENAI_API_KEY` | <https://platform.openai.com/docs/models> |
| Ollama (runs on your computer, free, no key) | `OpenAiCompatibleGateway` | `http://localhost:11434/v1` | none | `ollama list` after `ollama pull <model>`; <https://ollama.com/library> |
| OpenRouter (one key, many vendors) | `OpenAiCompatibleGateway` | `https://openrouter.ai/api/v1` | `OPENROUTER_API_KEY` | <https://openrouter.ai/models> |
| Groq | `OpenAiCompatibleGateway` | `https://api.groq.com/openai/v1` | `GROQ_API_KEY` | <https://console.groq.com/docs/models> |
| Mistral | `OpenAiCompatibleGateway` | `https://api.mistral.ai/v1` | `MISTRAL_API_KEY` | <https://docs.mistral.ai> |
| Google Gemini (OpenAI-compatible endpoint) | `OpenAiCompatibleGateway` | `https://generativelanguage.googleapis.com/v1beta/openai/` | `GEMINI_API_KEY` | <https://ai.google.dev/gemini-api/docs/models> |
| A company AI gateway (LiteLLM, Azure API Management, ...) | usually `OpenAiCompatibleGateway` | ask the team that runs it | ask the team | ask the team |

Claude is also available through Amazon Bedrock, Google Cloud Vertex AI and Microsoft Foundry;
each has its own client in the Anthropic SDK and its own authentication. Use those when your
company already buys AI through that cloud. Several providers also offer an OpenAI-compatible
endpoint for their own models; prefer the vendor's native SDK when you need features the
compatible format does not carry (Claude's thinking, citations or prompt caching, for example).

## Current Claude model ids (from Anthropic's documentation, 2026-10-06)

`claude-opus-5-5` (default choice for hard work), `claude-sonnet-5-5` (faster, cheaper, strong at
everyday coding and agents), `claude-haiku-5-5` (cheapest, for simple high-volume tasks),
`claude-fable-5-1` (most capable, highest price). Use the exact strings, without date suffixes.
Check <https://docs.claude.com> for the current list before you rely on this.

## What differs between providers, even with the same format

- **Token counting and limits:** each vendor counts tokens differently; the same text costs a
  different number of tokens. Read `usage` from each response instead of estimating.
- **System prompt:** the OpenAI format puts it as a `system` message; Claude has a separate
  `system` field. The adapters hide this.
- **Parameters:** sampling parameters (`temperature`, `top_p`) are not accepted by every current
  model; newer models use an *effort* setting instead. Keep such settings out of the port until
  you need them, and add them per adapter.
- **Structured output:** most providers can force JSON that matches a schema, each with its own
  request field. Validate on your side anyway.
- **Errors:** 429 means slow down (retry later), 5xx is the provider's problem (retry), 4xx is
  your request (do not retry). Every adapter maps these to `ModelGatewayException.retryable`.
- **Data use and region:** read each provider's data-retention and training terms, and where
  your data is processed, before sending anything that is not public.

## Try it in five minutes without a key

1. Install Ollama from <https://ollama.com>, then in a terminal: `ollama pull <a small model from
   the library>` and `ollama serve` if it is not already running.
2. Set `AI_PROVIDER=openai-compatible`, `AI_BASE_URL=http://localhost:11434/v1`,
   `AI_MODEL=<the model you pulled>`, and leave `AI_API_KEY_ENV` empty.
3. Run your code. Switching to Claude later is `AI_PROVIDER=claude`, `AI_MODEL=claude-opus-5-5`
   and `ANTHROPIC_API_KEY` set in your run configuration. No code change.

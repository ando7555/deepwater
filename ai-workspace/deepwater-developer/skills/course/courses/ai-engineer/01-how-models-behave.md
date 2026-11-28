# 01 How language models behave

**Time:** about 3 hours. **Prerequisites:** 00.

## Why this matters

You cannot design around a component you do not understand. Most AI bugs come from three
surprises: the model forgets what did not fit in its context, it answers confidently when it does
not know, and it costs money per token in *and* out. Learn these once and many design choices
become obvious.

## Talk it through

- "If you ask the same question twice, do you expect the same answer? Why or why not?"
- "What do you think you pay for when you call a model?"
- "Have you seen a model invent something? What was it?"

Let the learner guess first; then teach the ideas below and come back to their guesses.

## Core ideas

- **Tokens.** Models read and write pieces of words. In English a token is about four characters;
  other languages and code often need more. Every limit and every price is in tokens.
- **Next-token prediction.** The model writes one token at a time, each chosen from a probability
  distribution conditioned on everything before it. That is why wording, order and examples in the
  prompt change the answer, and why answers vary between runs.
- **Context window.** The model sees only what is in the request: system prompt, conversation,
  documents, tool results. It has no memory between calls unless your code sends the history
  again. Bigger context costs more and can dilute attention.
- **Training cut-off and hallucination.** The model knows public text up to a date. When it lacks
  facts it still produces fluent text. Design so that facts come from your data (module 05) and
  are checked (module 04).
- **Thinking and effort.** Current models can reason before answering. Many providers expose an
  *effort* or *reasoning* setting: more effort is slower and costs more, and helps on hard tasks.
  Older sampling knobs like `temperature` are not accepted by every current model.
- **Cost and latency.** Cost ≈ input tokens × input price + output tokens × output price (prices
  per million tokens, on the provider's pricing page). Output tokens are usually several times
  more expensive than input, and they are what makes a call slow. Caching repeated prompt prefixes
  can cut input cost a lot on providers that support it.
- **Model sizes.** Vendors sell tiers (for Claude: Haiku, Sonnet, Opus, Fable). Start with a
  strong model to learn what is possible, then measure whether a cheaper tier or lower effort
  holds quality on your evaluation set (module 04).

## Lab

Use the learner's model access, or skip the live parts with "fake first" and do the math only.

1. Take one realistic request from their problem (or: "Draft three German exercises on verb-second
   word order for A1 learners"). Send it three times and save the three answers. What differs?
2. Read the token counts the provider returned (`usage` in the response). Compute the cost of one
   call with today's price from the provider's pricing page, then the cost of 10,000 calls a month.
   Write the source and date of the price next to the number.
3. Make the prompt twice as long with irrelevant text. What happens to cost, latency and quality?
4. Ask the model a question about something that happened last week, or about a made-up library
   method. Save the answer. Discuss: how would your product notice this?

## Check your understanding

- "Your feature sends a 3,000-token document and gets a 500-token answer. Which half dominates the
  price, and which dominates the latency?" (Depends on prices; often input dominates cost for long
  documents, output dominates latency.)
- "Why does a chat app 'remember' earlier messages?" (The application sends them again on every
  call.)
- "Name one way to reduce hallucinated facts that does not involve a better model." (Give the facts
  in the context with sources, constrain the output, verify against data, let the model say
  "unknown".)

## Done when

- [ ] Three answers to the same request are saved and the learner explained the differences.
- [ ] A cost estimate per call and per month, with price source and date, is in the progress file.
- [ ] The learner explained context, tokens and hallucination in their own words.

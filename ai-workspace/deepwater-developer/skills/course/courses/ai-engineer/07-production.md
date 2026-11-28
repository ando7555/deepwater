# 07 Production: safety, cost, failure

**Time:** about 4 hours. **Prerequisites:** 02, 03, 04. **Earns Level 3: AI engineer** with 05
(or its documented skip) and 06.

## Why this matters

The demo works; production is different. Providers have outages and rate limits, bills grow
quietly, data leaks through logs, and users find inputs nobody tested. An AI engineer is the person
who makes the feature boring to operate.

## Talk it through

- "The provider is down for an hour. What does your user see?"
- "Your bill doubled this month. How do you find out why?"
- "Which logs would contain a learner's text today?"

## Core ideas

- **Deterministic fallback:** every AI feature needs a non-AI behaviour: the empty form, a
  template, "try again later", a queue for a human. Decide it in the specification.
- **Timeouts, retries, circuit breaker:** timeout every call; retry only retryable errors, a few
  times with backoff; stop calling a failing provider for a while (Resilience4j in Spring). A
  second provider through module 02's port is a real option, tested with the eval set.
- **Cost control:** record tokens and cost per call (from `usage`), per feature and per user;
  set limits (per request `max_tokens`, per user per day, a monthly budget alert in the provider
  console). Use caching of repeated prompt prefixes where the provider supports it, a smaller
  model or lower effort where the eval set allows, batch APIs for work that can wait.
- **Privacy and data:** send the minimum; never personal data unless a decision and the provider's
  terms allow it; no prompts or responses with personal data in logs; know the provider's
  retention and region.
- **Security:** keys in a secret store, rotated; model output never executes; inputs are
  untrusted (prompt injection, module 03); rate-limit your own AI endpoints.
- **Observability:** per call log the feature, prompt version, model, tokens, latency, outcome,
  never the content by default. Watch error rate, latency and cost per completed task.
- **Release:** feature flag, small rollout, the eval set in CI for prompt and model changes,
  human review where errors are costly.

## Lab

1. Add a timeout, bounded retries and a fallback to the exercise drafter. Test each with the fake
   gateway (slow, failing, 429).
2. Record tokens and estimated cost per call (price source and date in configuration). Add a daily
   per-user limit and a test for it.
3. Review the logs: prove with a test or a log capture that no exercise text or teacher input is
   logged by default.
4. Write a one-page runbook: what to do when the provider is down, the bill spikes, or teachers
   report bad drafts.

## Check your understanding

- "Retry on 400?" (No.) "Retry forever on 503?" (No: bounded, with backoff, then fallback.)
- "Which single number would you put on a dashboard for this feature, and why?" (Cost per
  approved draft, or approval rate: it joins quality and cost.)

## Done when

- [ ] Timeout, retries and fallback exist and are tested; the mentor ran the tests.
- [ ] Token and cost recording plus a per-user limit exist and are tested.
- [ ] No sensitive content in default logs (evidence: test or log capture).
- [ ] A runbook is in the repository.

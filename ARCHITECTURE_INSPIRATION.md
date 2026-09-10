# Deepwater - Architecture Inspiration

## Status and provenance

The previous products were discontinued on 2026-09-10. Their application code,
roadmaps, branding, and deployment configuration have been removed for a new
product, Deepwater, with two applications. The new product's purpose, application boundaries,
and technology choices remain undecided.

This is the only retained project document. It preserves engineering knowledge, not a backlog to finish the old
products. Sources are the former modular architecture document, both planning
backlogs, the Go architecture note, and the refactoring article in `docs/posts`.
Their original versions and application code remain in Git at commit
`2e0411d474ff275e30ff0cbd416839f7c1ba0620`.

The architecture and backlog documents describe proposals. The refactoring
article reports previous changes; those claims were not retested during cleanup.
No separate technical-debt register was found in the reviewed documentation.

## Debt lessons to carry forward

| Earlier concern | How it helps the new product | Evidence to require when applicable |
| --- | --- | --- |
| Business logic and transport responsibilities mixed together | Keep HTTP handlers thin; put use cases and business rules behind clear interfaces | Rule tests independent of HTTP and persistence |
| Duplicate pagination and enum parsing, reported in the refactoring article | Extract small, domain-neutral helpers only when duplication is real | Boundary cases for parsing and pagination |
| Large controllers and string-based roles, reported in the article | Give modules a clear responsibility and model constrained values explicitly | Invalid values rejected at API and domain boundaries |
| Provider-specific AI code leaking into business flows | Use a provider-neutral interface; keep prompts and output validation owned by the relevant feature | Contract tests for success, invalid output, timeout, and unavailable provider |
| Swallowed AI failures and inconsistent response parsing, reported in the article | Make failure and fallback behavior explicit and observable | Users and logs can distinguish generated, fallback, and failed results |
| Broad common/shared modules | Share stable technical primitives; keep domain policy with its owner | Shared packages do not import application-specific business modules |
| Environment and identity setup needing stabilization in the backlog | Separate local convenience from production configuration; enforce permissions on the server | Configuration validation and tests for forged privilege requests |
| Ambitious service, messaging, and migration plans | Start with one complete user flow; introduce infrastructure for demonstrated needs | A decision record explains cost, alternatives, and operational benefit |

These concerns are lessons from the former project, not confirmed defects in the
new product and not claims that every old concern remained unresolved.

## One product, two applications

1. Define each application's users, main task, and ownership before choosing a stack.
2. Agree on shared product concepts and API contracts. Two applications do not
   automatically require two backends or separate programming languages.
3. Consider shared identity and visual components when both applications need them.
   Keep authorization enforced by the backend for each operation.
4. Give each data model an owner. Avoid two applications independently editing the
   same records without an explicit contract.
5. Select a modular backend or separate services based on actual deployment and
   scaling requirements. The former Java, Go, Neo4j, and Kafka choices are not inherited.
6. Build one end-to-end flow first, then extend the second application using the
   agreed contracts.

## Optional lessons if the new product uses AI automation

- Define inputs, structured outputs, tool permissions, budgets, cancellation, and
  failure behavior for each automated role.
- Treat retrieved documents as data, never as permission to execute actions.
- Model long-running work with explicit legal state transitions. Handle retries
  without duplicating side effects and record execution evidence.
- Bind consequential approvals to the actual action and parameters.
- Add durable queues, event replay, and audit storage when the chosen workflow
  needs them; they are not requirements for an unspecified MVP.
- Keep deterministic calculations separate from generated explanations.

## Working method and future debt register

Use small tasks with an objective, scope, acceptance criteria, and relevant
verification. Record commands actually run and their results. Check domain rules
with unit tests, interfaces with integration tests, and critical user journeys
end to end once they exist. Benchmark before choosing a migration or optimization.

Create debt entries only after the new implementation reveals a concrete issue:

| ID | Observed problem and evidence | User or maintenance impact | Proposed action | Priority / owner | Completion evidence |
| --- | --- | --- | --- | --- | --- |

There are no new-product debt entries yet. Product definition is the next step.

# Spec-driven multi-interface architecture

> Reference material for the `spec-driven-delivery` skill. Written for a generic Java/Spring
> project. What its placeholders (ticket, `documentation/`, Maven modules) are in a given
> repository is written in that repository's `docs/delivery/repository-mapping.md`, or derived
> from the repository when that file does not exist yet.

## Purpose

This document defines a reusable architecture for Java and Spring applications that expose the
same business capabilities through MCP, REST, GraphQL, and future AG-UI interfaces.

It is intended for both greenfield and existing systems. The design favors a modular monolith at
first, explicit domain boundaries, small use cases, and adapters around the application core. A
capability can be extracted into a separate service later when there is a clear operational or
ownership reason.

The examples use placeholders such as `<capability>`, `<operation>`, `<external-system>`, and
`<ticket-id>`. Replace them with project-specific names without copying real credentials,
production data, tokens, or personal information.

## Decision summary

Use the following default:

```text
Modular monolith
+ Domain-driven design
+ Hexagonal architecture
+ Specification-first delivery
+ MCP/REST/GraphQL/AG-UI as inbound adapters
+ Ports and adapters for external systems
+ Centralized security and observability
+ Optional AI orchestration layer
+ Automated contract, architecture, security, and quality gates
```

The most important rule is:

```text
Many interfaces, one application use-case model.
```

MCP tools, REST controllers, GraphQL resolvers, and AG-UI actions must not each implement the
same business workflow independently.

## Architectural goals

The architecture should provide:

- Consistent behavior regardless of the entry protocol.
- A domain model independent of Spring, MCP, REST, GraphQL, and UI frameworks.
- Small, testable use cases with explicit input and output models.
- Safe integration with external systems and model providers.
- A clear path from a ticket to an executable specification and production evidence.
- Simple local development and fast module-scoped feedback.
- Explicit support for existing logic and brownfield migration.
- Reusable security, audit, logging, error, and resilience patterns.
- The ability to add AI capabilities without forcing every use case to depend on an LLM.

## Non-goals

Do not introduce these by default:

- A separate microservice for every MCP tool.
- An internal LLM for deterministic business operations.
- Event sourcing when ordinary state persistence is sufficient.
- CQRS when reads and writes do not have materially different requirements.
- A generic framework that hides simple use cases.
- A shared domain model across unrelated bounded contexts.
- Duplicate implementations for MCP, REST, GraphQL, and UI callers.

Apply complexity only when a current requirement, measured constraint, or explicit architecture
decision justifies it.

# 1. Target architecture

```text
┌─────────────────────────────────────────────────────────────────────┐
│                         Client channels                             │
│                                                                     │
│  AI assistant       MCP client       REST client       GraphQL/UI   │
│       │                 │                │                │         │
└───────┼─────────────────┼────────────────┼────────────────┼─────────┘
        │                 │                │                │
        ▼                 ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Inbound adapters                            │
│                                                                     │
│  AG-UI adapter     MCP tool adapter   REST controller   GraphQL     │
│                                                                     │
│  - Protocol mapping                                                 │
│  - Authentication context                                           │
│  - Input schema validation                                          │
│  - Output/error mapping                                             │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Application/use-case layer                       │
│                                                                     │
│  <OperationUseCase>                                                 │
│  <QueryUseCase>                                                     │
│  <CommandUseCase>                                                   │
│                                                                     │
│  - Orchestrates a business workflow                                 │
│  - Applies authorization policy                                     │
│  - Coordinates ports                                                │
│  - Defines transaction boundary                                     │
│  - Returns application result                                       │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                            Domain                                   │
│                                                                     │
│  Aggregates | Entities | Value objects | Domain services            │
│  Business rules | Domain events | Domain errors                     │
│                                                                     │
│  No dependency on Spring, MCP, REST, GraphQL, databases, or LLMs    │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                ┌──────────────┴──────────────┐
                ▼                             ▼
┌──────────────────────────┐      ┌──────────────────────────────────┐
│ Inbound ports            │      │ Outbound ports                   │
│                          │      │                                  │
│ Use-case interfaces      │      │ RepositoryPort                   │
│ Query interfaces         │      │ <ExternalSystem>Port             │
│ Command interfaces       │      │ AuditPort                        │
└──────────────────────────┘      │ ModelGatewayPort                 │
                                  │ EventPublisherPort               │
                                  └──────────────┬───────────────────┘
                                                 │
                                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Outbound adapters                           │
│                                                                     │
│ REST clients | GraphQL clients | Database repositories              │
│ Message publishers | Search clients | Model providers | Audit store │
└─────────────────────────────────────────────────────────────────────┘

Cross-cutting concerns: security, correlation IDs, logging, metrics, tracing,
configuration, resilience, rate limits, data protection, and health checks.
```

## Dependency rule

Dependencies point toward the application and domain core:

```text
Inbound adapter ───────▶ application port/use case ───────▶ domain
Outbound adapter ──────▶ outbound port defined by core
```

The domain and application layers must not import:

- MCP annotations or protocol classes
- Spring MVC or GraphQL resolver classes
- AG-UI protocol classes
- REST client implementations
- Database SDKs
- OAuth2 implementation classes
- Vendor-specific model SDKs

Framework code belongs at the edges.

# 2. Repository structure

Use a structure that makes boundaries visible. A multi-module build may use separate modules; a
smaller service can use the same boundaries as packages.

```text
project/
├── documentation/
│   ├── architecture/
│   │   ├── adr/
│   │   ├── context.md
│   │   └── container.md
│   ├── specs/
│   │   └── <ticket-id>-<capability>.md
│   └── delivery/
│       └── pipeline.md
│
├── api-contracts/
│   ├── openapi/
│   ├── graphql/
│   ├── mcp/
│   └── ag-ui/
│
├── <capability>-domain/
│   └── src/main/java/.../<capability>/domain/
│
├── <capability>-application/
│   └── src/main/java/.../<capability>/application/
│
├── <capability>-adapter-inbound-mcp/
├── <capability>-adapter-inbound-rest/
├── <capability>-adapter-inbound-graphql/
├── <capability>-adapter-inbound-ag-ui/
│
├── <capability>-adapter-outbound-<system>/
├── shared-security/
├── shared-observability/
├── shared-audit/
└── bootstrap/
    └── SpringBootApplication
```

For a smaller application, use packages instead:

```text
com.example.application
├── capability
│   ├── domain
│   ├── application
│   ├── adapter.inbound.mcp
│   ├── adapter.inbound.rest
│   ├── adapter.inbound.graphql
│   └── adapter.outbound
├── security
├── observability
└── configuration
```

Choose modules when dependency isolation matters. Choose packages when module overhead would slow a
small team. The logical boundaries must exist in either form.

# 3. Spec-driven development lifecycle

Every feature starts with a specification, not with a controller, MCP tool, prompt, or database
table.

```text
Ticket
    ↓
Discovery of existing behavior
    ↓
Executable feature specification
    ↓
Domain and boundary design
    ↓
API/interface contracts
    ↓
Test design
    ↓
Implementation
    ↓
Focused verification
    ↓
Integration and security verification
    ↓
Deployment evidence
    ↓
Operational feedback
```

## Step 1: Read and classify the ticket

Capture:

- Business problem and user outcome.
- Actor and authorization context.
- Bounded context or owning capability.
- Inputs and outputs.
- Existing behavior that must remain compatible.
- Non-functional requirements.
- Data classification and privacy constraints.
- Required interfaces: MCP, REST, GraphQL, AG-UI, batch, or events.
- Acceptance criteria and measurable evidence.

Classify the change:

```text
New capability       → design a new bounded context or extend an existing one
Behavior change      → preserve compatibility and add regression specifications
Bug fix              → reproduce first, then specify expected correction
Integration change   → isolate behind or extend an outbound port
AI behavior change   → version prompt/skill/config and add evaluation evidence
```

## Step 2: Inspect the existing system before designing

For a brownfield feature, do not assume the requested behavior is greenfield. Trace:

1. Existing API routes and MCP tools.
2. Existing GraphQL schema and resolvers.
3. Existing UI or AG-UI event contracts.
4. Existing application services and use cases.
5. Existing domain rules and validators.
6. Existing outbound clients and repositories.
7. Existing security policies and scopes.
8. Existing audit and observability behavior.
9. Existing tests and fixtures.
10. Existing deployment and rollback behavior.

Record the results in the feature specification:

```text
Existing behavior found:
- <current entry point>
- <current use case or service>
- <current external integration>
- <current test coverage>

Reuse decision:
- Reuse: <component and reason>
- Extend: <component and compatibility plan>
- Replace: <component and migration plan>
- New: <component and reason reuse is not suitable>
```

## Step 3: Write the feature specification

Create:

```text
documentation/specs/<ticket-id>-<capability>.md
```

Minimum structure:

```markdown
# <Feature title>

## Problem

## Scope

## Out of scope

## Actors and authorization

## Existing behavior

## Business rules

## Input contract

## Output contract

## Error contract

## Acceptance criteria

## Non-functional requirements

## Compatibility and migration

## Observability and audit

## Test evidence

## Open decisions
```

## Step 4: Convert acceptance criteria into executable scenarios

Use Given/When/Then scenarios:

```gherkin
Feature: <capability operation>

  Scenario: Authorized caller receives a successful result
    Given an authorized caller
    And valid input for <operation>
    And the required external dependency is available
    When the caller invokes <operation>
    Then the use case returns the expected result
    And a sanitized audit event is recorded

  Scenario: Invalid input is rejected
    Given an authorized caller
    And invalid <input-field>
    When the caller invokes <operation>
    Then the request is rejected with a validation error
    And the external dependency is not called

  Scenario: Unauthorized caller is rejected
    Given a caller without the required scope or role
    When the caller invokes <operation>
    Then the request is rejected
    And the use case is not executed

  Scenario: External dependency is unavailable
    Given valid input
    And the external dependency is unavailable
    When the caller invokes <operation>
    Then a safe dependency error is returned
    And no secret or internal stack trace is exposed
```

## Step 5: Define the domain before the transport

Identify:

- Bounded context.
- Aggregate boundaries.
- Entities and value objects.
- Invariants.
- Domain services.
- Domain events, only if they are needed.
- Commands and queries.
- Ownership of data.

Example:

```text
<OperationRequest>
  ├── <BusinessIdentifier>
  ├── <ValidatedAttribute>
  └── <OperationOptions>

<OperationResult>
  ├── status
  ├── outcome
  └── safe reference
```

Keep domain objects meaningful. Do not create a domain object only to wrap a transport DTO.

## Step 6: Define ports and adapters

Define an inbound use-case port:

```text
<OperationUseCase>
    execute(<OperationCommand>) → <OperationResult>
```

Define outbound ports only for dependencies the use case needs:

```text
<ExternalSystemPort>
    perform(<ExternalRequest>) → <ExternalResponse>

<AuditPort>
    record(<AuditEvent>)
```

Then implement adapters:

```text
MCP adapter     → OperationUseCase
REST adapter    → OperationUseCase
GraphQL adapter → OperationUseCase
AG-UI adapter   → OperationUseCase

REST client     → ExternalSystemPort
GraphQL client  → ExternalSystemPort
Database        → RepositoryPort
Model provider  → ModelGatewayPort
```

## Step 7: Define interface contracts

The business operation should have one canonical semantic contract, mapped into each protocol.

### MCP contract

```json
{
  "name": "<operationName>",
  "description": "<when the AI client should use this operation>",
  "inputSchema": {
    "type": "object",
    "required": ["<required-field>"],
    "properties": {
      "<required-field>": {
        "type": "string"
      }
    }
  }
}
```

### REST contract

```text
POST /api/<version>/<capability>/<operation>
```

Define the request, response, validation errors, authorization errors, dependency errors, and
correlation headers in OpenAPI.

### GraphQL contract

```graphql
input <OperationInput> {
  <requiredField>: String!
}

type <OperationResult> {
  status: <OperationStatus>!
  reference: String
}

type Mutation {
  <operationName>(input: <OperationInput>!): <OperationResult>!
}
```

### AG-UI contract

AG-UI should be an interaction adapter, not a second business layer. Define:

- User action or command.
- Streamed progress events.
- Tool-call events.
- Approval or confirmation events.
- Partial result events.
- Completion event.
- Error event.
- Cancellation behavior.

```text
AG-UI event
    ↓
AG-UI adapter
    ↓
Application use case
    ↓
AG-UI progress/result events
```

Use the same use case and authorization policy as MCP, REST, and GraphQL.

## Step 8: Implement the smallest vertical slice

Implement in this order:

1. Specification and acceptance scenarios.
2. Domain value objects and invariants.
3. Application command/query and use-case port.
4. Use-case implementation.
5. Mock or fake outbound port.
6. Unit tests for domain and use case.
7. One required inbound adapter.
8. Contract tests for that adapter.
9. Real outbound adapter.
10. Integration tests.
11. Additional interfaces only when required.

Do not implement all protocols before proving one complete vertical slice.

## Step 9: Add interfaces incrementally

When another interface is requested:

```text
Existing use case
      │
      ├── MCP adapter
      ├── REST adapter
      ├── GraphQL adapter
      └── AG-UI adapter
```

If an adapter requires business logic that does not fit the existing use case, stop and review the
specification. The difference may indicate:

- A missing use case.
- A separate bounded context.
- A protocol-specific presentation concern.
- An incorrect attempt to share incompatible workflows.

## Step 10: Verify and produce evidence

For every feature, preserve evidence for:

- Specification review.
- Domain and architecture review.
- Unit tests.
- Adapter contract tests.
- Security tests.
- Integration tests.
- Static analysis.
- Dependency and vulnerability scans.
- Performance tests where required.
- Deployment and rollback validation.
- Monitoring and alert verification.

# 4. DDD implementation guidance

## Bounded contexts

A bounded context is a boundary around a coherent business model and vocabulary.

Create a new bounded context when:

- The business terms have a different meaning.
- The rules change independently.
- Data ownership is different.
- A separate team owns the capability.
- Integration and release boundaries are materially different.

Do not split a context merely because it has multiple tools or endpoints.

## Aggregates

Use an aggregate to protect a consistency boundary.

Guidelines:

- One aggregate has one root.
- Update state through the root.
- Keep transactions around aggregate invariants.
- Reference other aggregates by identity.
- Do not load a large object graph without need.
- Avoid making every database row an aggregate.

## Value objects

Use value objects for values with meaning or validation:

```text
<CaseIdentifier>
<CountryCode>
<ConfidenceScore>
<CorrelationId>
<OperationStatus>
```

A value object should validate its own invariant where practical.

## Entities

Use entities when identity and lifecycle matter. Do not use entities for every DTO.

## Domain services

Use a domain service when a rule:

- Is business logic.
- Does not naturally belong to one entity or value object.
- Requires multiple domain objects.
- Is independent of infrastructure.

## Application services

Application services coordinate the workflow:

```text
validate command
→ load required state
→ call domain behavior
→ call outbound ports
→ persist changes
→ publish/audit result
```

They should not become a dumping ground. If an application service grows too large, split by use
case or introduce a domain service with a clear rule.

# 5. KISS, YAGNI, and DRY rules

## KISS: Keep it simple

Prefer:

- One clear use case over a generic workflow engine.
- A typed request object over a map of values.
- A direct adapter over unnecessary abstraction layers.
- Explicit configuration over magic discovery.
- A simple synchronous call when asynchronous processing is not required.
- Standard Spring mechanisms before custom frameworks.

A design is too complex when a new developer cannot trace a request from the adapter to the use
case and back without a diagram.

## YAGNI: You are not going to need it

Do not add:

- GraphQL because a future client might need it.
- AG-UI streaming before a user interaction requires it.
- LLM orchestration for deterministic rules.
- Event sourcing before audit/history requirements justify it.
- A generic repository for one persistence operation.
- A microservice before independent deployment is necessary.
- A plugin system before there are real plugins.

Document future extension points, but implement them only when a ticket requires them.

## DRY: Do not repeat yourself

Share stable knowledge, not coincidental text.

Good reuse:

- One application use case for several inbound protocols.
- One authorization policy for equivalent operations.
- One domain rule used by all callers.
- One outbound port for one external capability.
- Shared error and correlation-ID handling.

Avoid premature reuse of:

- Similar DTOs with different lifecycle or security semantics.
- Unrelated domain concepts with similar fields.
- Protocol-specific response models.
- Test fixtures that hide important differences.

A useful rule:

```text
Duplicate twice while learning; extract when the third use is proven equivalent.
```

# 6. AI and Spring AI design

## Separate deterministic logic from probabilistic logic

```text
Deterministic business rule → Domain service
External operation           → Application use case + adapter
LLM interpretation           → AI orchestration service
Prompt/skill definition      → Versioned AI artifact
```

Do not move a deterministic validation rule into a prompt.

## Model gateway

If a use case needs a model, depend on a port:

```text
ModelGatewayPort
    complete(<ModelRequest>) → <ModelResponse>
```

The application should not directly depend on a vendor SDK. The adapter owns:

- Model selection.
- Request and response mapping.
- Timeout and retry limits.
- Token and cost limits.
- Safety configuration.
- Provider-specific error mapping.
- Redaction and telemetry.

## Prompt and skill versioning

Store AI artifacts separately from Java behavior where possible:

```text
ai-artifacts/
├── prompts/
├── skills/
├── tool-descriptions/
├── evaluation-cases/
└── changelog/
```

Every AI behavior change should identify:

- Artifact version.
- Model/deployment version.
- Input data assumptions.
- Expected output schema.
- Evaluation set.
- Safety and fallback behavior.
- Rollback version.

## AI output handling

Treat model output as untrusted input:

1. Parse against a strict schema.
2. Validate required fields and allowed values.
3. Apply domain rules independently.
4. Reject unsafe or incomplete output.
5. Record a sanitized evaluation reference.
6. Never execute arbitrary model-generated commands.

```text
Model output
    ↓
Schema parser
    ↓
Validation
    ↓
Domain rules
    ↓
Approved application action
```

## MCP tool design for AI clients

A tool description should state:

- What the tool does.
- When the model should use it.
- When the model must not use it.
- Required inputs.
- Side effects.
- Whether confirmation is required.
- Expected result semantics.

Write tools as narrow business capabilities, not as generic database or HTTP proxies.

# 7. Security and data protection

## Inbound security

Every protected interface must validate:

- Signature and key trust.
- Issuer.
- Audience.
- Expiration and not-before timestamps.
- Required delegated scope.
- Required roles, if applicable.
- Tenant or organization boundary.

Apply the same policy regardless of whether the call comes through MCP, REST, GraphQL, or AG-UI.

## Outbound security

External calls use a dedicated client configuration and token flow:

```text
MCP/REST/GraphQL/AG-UI caller token
        ≠
External-system access token
```

The application obtains and caches downstream tokens internally. Never ask an AI client to provide
an external system's client secret or bearer token.

## Sensitive data rules

Never log or commit:

- Access tokens.
- Client secrets.
- Authorization headers.
- Complete JWTs.
- Personal documents.
- Unredacted prompts containing personal data.
- Raw model responses containing sensitive data.
- Full request or response bodies unless explicitly approved and sanitized.

Use synthetic data in tests and documentation.

# 8. Error and resilience design

Define stable error categories at the application boundary:

```text
VALIDATION_ERROR
UNAUTHORIZED
FORBIDDEN
NOT_FOUND
CONFLICT
DEPENDENCY_UNAVAILABLE
TIMEOUT
RATE_LIMITED
INTERNAL_ERROR
```

Map each protocol separately:

```text
Application error
    ├── MCP tool error
    ├── REST problem response
    ├── GraphQL error extension
    └── AG-UI error event
```

Do not expose stack traces, tokens, provider details, SQL, or internal URLs to clients.

Every outbound call should define:

- Connect timeout.
- Read timeout.
- Maximum response size where appropriate.
- Retryable error categories.
- Maximum retry count.
- Backoff policy.
- Circuit-breaker behavior if required.
- Idempotency behavior.

# 9. Testing strategy

Use the test pyramid:

```text
                 ┌──────────────────────┐
                 │ End-to-end tests     │
                 └──────────────────────┘
              ┌────────────────────────────┐
              │ Integration/contract tests │
              └────────────────────────────┘
          ┌────────────────────────────────────┐
          │ Application and adapter tests      │
          └────────────────────────────────────┘
      ┌────────────────────────────────────────────┐
      │ Domain unit tests                          │
      └────────────────────────────────────────────┘
```

## Required test levels

### Domain tests

Verify invariants, value-object validation, domain transitions, boundary conditions.

### Application tests

Verify use-case orchestration, authorization decisions, port calls, transaction behavior, error
mapping, idempotency. Use mocks or fakes for outbound ports.

### Inbound adapter tests

Verify each required protocol:

- MCP tool registration and input schema.
- MCP tool invocation and error behavior.
- REST status codes and OpenAPI compatibility.
- GraphQL schema and resolver behavior.
- AG-UI event sequence, cancellation, and failure events.

### Outbound adapter tests

Verify request mapping, authentication configuration, timeout behavior, response mapping, retry
behavior, external error mapping. Use deterministic stubs or test containers. Do not call real
identity or production services from unit tests.

### Architecture tests

Enforce:

- No cycles between bounded contexts.
- Domain does not depend on adapters.
- MCP/REST/GraphQL adapters do not call repositories directly.
- Tools and controllers call application use cases.
- No field injection.
- Naming and package rules.
- No unauthorized dependency on vendor SDKs.

### AI evaluation tests

For AI-enabled behavior, add schema conformance tests, golden examples, adversarial inputs,
prompt-injection tests, hallucination/fabrication checks, refusal and fallback tests, and
regression evaluation against the prior artifact version.

# 10. Ticket-to-feature workflow

## Phase A: Clarify

```text
Ticket accepted
    ↓
Confirm actor, outcome, scope, and non-goals
    ↓
Identify owning bounded context
    ↓
Identify existing behavior and compatibility constraints
```

Deliverables: refined ticket, open questions, initial impact map, security and data
classification.

## Phase B: Specify

```text
Ticket
  ├── feature specification
  ├── Given/When/Then scenarios
  ├── domain rules
  ├── interface contract
  ├── error contract
  ├── observability requirements
  └── test evidence plan
```

A ticket is not implementation-ready until the behavior can be tested without relying on prose
such as "works as expected".

## Phase C: Design

Produce only the design needed for the ticket: context map, sequence diagram, port and adapter
list, ADR if a meaningful trade-off exists, migration plan for existing logic, rollback plan.

## Phase D: Implement

Use the smallest vertical slice:

```text
Domain rule
  → use case
  → fake port
  → focused test
  → required adapter
  → contract test
  → real adapter
```

## Phase E: Verify

Run the narrowest relevant checks first, then expand:

```text
1. Formatter and compile
2. Domain/application tests
3. Adapter contract tests
4. Security tests
5. Integration tests
6. Architecture tests
7. Full verification
8. Scan and deployment evidence
```

## Phase F: Review and release

Review:

- Does the implementation match the specification?
- Is existing behavior preserved?
- Is the use case shared across interfaces?
- Are KISS/YAGNI/DRY rules respected?
- Are sensitive values protected?
- Are logs and audit events safe?
- Is rollback possible?
- Is operational documentation complete?

# 11. CI/CD pipeline template

Use a staged pipeline. Exact templates and environment names come from the owning platform.

```text
validate → compile → unit-test → contract → integration → quality → security
→ package → deploy-dev → smoke-test → deploy-test → acceptance → deploy-prod
```

| Stage | Checks |
| --- | --- |
| validate | YAML, dependency, secret, spec-format checks |
| compile | Build and generated sources |
| unit-test | Domain and application tests |
| contract | MCP, REST, GraphQL, AG-UI contracts |
| integration | External adapters and persistence |
| quality | Format, Checkstyle, Error Prone, NullAway, coverage |
| security | SAST, dependency CVE, secrets, container/image scan |
| package | Versioned artifact/container |
| deploy-dev | Automatic deployment to development |
| smoke-test | Health, auth, MCP/REST/GraphQL happy path |
| deploy-test | Manual or policy-controlled promotion |
| acceptance | Evidence and approval |
| deploy-prod | Controlled release with rollback |

## Pipeline gates

- **Specification gate:** ticket ID is associated with the change; feature specification exists;
  acceptance criteria are testable; API/interface contracts are versioned; breaking changes are
  explicitly approved.
- **Build gate:** compilation succeeds; generated sources are reproducible; dependency versions are
  controlled; formatting and static analysis pass.
- **Test gate:** unit tests pass; contract tests pass for every exposed interface; security tests
  cover unauthorized and forbidden behavior; integration tests cover outbound failures;
  architecture tests pass; AI evaluations pass when AI behavior changes.
- **Security gate:** no secrets detected; dependencies have acceptable vulnerability status;
  container/image scan passes; sensitive logs are reviewed; access policies are reviewed.
- **Deployment gate:** versioned artifact is available; configuration is externalized; secret
  references resolve from the approved secret manager; health and readiness checks pass; smoke
  tests pass; rollback artifact and instructions exist.

# 12. Brownfield implementation strategy

## Strangler sequence

```text
Existing endpoint/tool
       ↓
Characterize existing behavior with tests
       ↓
Extract pure validation/domain rules
       ↓
Introduce application use-case interface
       ↓
Move existing logic behind the use case
       ↓
Change existing adapter to call the use case
       ↓
Add new MCP/REST/GraphQL/AG-UI adapters
       ↓
Remove duplicated legacy path after migration evidence
```

## Brownfield rules

- Do not rewrite working behavior without a ticket requirement.
- Add characterization tests before refactoring.
- Preserve existing API contracts unless a versioned breaking change is approved.
- Keep old and new paths observable during migration.
- Migrate one use case at a time.
- Reuse existing validators and clients where their contracts are correct.
- Wrap legacy code behind a port rather than spreading legacy dependencies.
- Record architectural compromises in an ADR with a removal condition.

# 13. Example: ticket to a reusable operation

Input ticket:

```text
Ticket: <ticket-id>

As a <caller>
I want to <business outcome>
So that <business value>

Existing logic:
- <existing endpoint/tool/service>
- <existing validation>
- <existing external dependency>

New interface required:
- MCP / REST / GraphQL / AG-UI / multiple
```

Derived design:

```text
<OperationTool> or <OperationController> or <OperationResolver>
                    │
                    ▼
             <OperationUseCase>
                    │
                    ├── <BusinessRule>
                    ├── <ExternalSystemPort>
                    └── <AuditPort>
```

Example operation flow:

1. Caller authenticates.
2. Adapter validates protocol-level input.
3. Adapter maps input to `<OperationCommand>`.
4. Use case checks authorization policy.
5. Domain validates business invariants.
6. Use case calls outbound ports.
7. Adapter maps result to the protocol response.
8. Audit event is recorded without sensitive content.
9. Metrics and trace attributes are emitted.

Definition of done: see `definition-of-done.md` beside this file.

# 14. ADR template

See `adr-template.md` beside this file. Create an ADR when a decision affects boundaries,
protocols, persistence, AI behavior, security, or operations.

# 15. Practical implementation checklist

Before coding:

- [ ] Read the ticket and identify the business outcome.
- [ ] Find existing behavior, contracts, tests, and integrations.
- [ ] Confirm the bounded context and data owner.
- [ ] Write the feature specification.
- [ ] Write executable acceptance scenarios.
- [ ] Decide whether this is deterministic, AI-assisted, or AI-generated behavior.
- [ ] Choose the smallest required interface.
- [ ] Record an ADR only if a significant trade-off exists.

During coding:

- [ ] Implement domain rules independently of Spring.
- [ ] Implement one focused application use case.
- [ ] Define narrow outbound ports.
- [ ] Keep MCP, REST, GraphQL, and AG-UI adapters thin.
- [ ] Reuse stable rules, not merely similar code.
- [ ] Avoid speculative frameworks and abstractions.
- [ ] Add safe timeouts and error mapping.
- [ ] Add correlation IDs and sanitized audit events.
- [ ] Protect tokens, secrets, prompts, and personal data.

Before merge:

- [ ] Specification and implementation agree.
- [ ] Existing behavior has regression coverage.
- [ ] Unit tests pass.
- [ ] Contract tests pass.
- [ ] Security tests pass.
- [ ] Architecture tests pass.
- [ ] AI evaluation passes if applicable.
- [ ] Formatting and static analysis pass.
- [ ] Dependency and secret scans pass.
- [ ] Documentation and rollback instructions are complete.

# 16. Recommended first implementation path

For a new capability, use this order:

```text
1. Start with one bounded context.
2. Define one use case.
3. Implement domain validation.
4. Expose one interface required by the ticket.
5. Add an outbound adapter only where needed.
6. Add audit, security, and observability.
7. Add contract and integration tests.
8. Add other interfaces only when consumers require them.
9. Add AI orchestration only when deterministic logic is insufficient.
10. Extract a service only when operational boundaries justify it.
```

This keeps the design reusable without turning every feature into a platform project.

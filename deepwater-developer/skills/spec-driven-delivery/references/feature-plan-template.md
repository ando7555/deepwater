# Feature plan

> Copy this template to the specification path the repository mapping gives (by default
> `docs/specs/<ticket-id>-<capability>.md`) before implementation.
> Complete the plan from the issue and repository evidence. Do not add credentials,
> tokens, personal data, or production payloads.

## 1. Ticket metadata

- Ticket: `<project>#<issue-id>`
- Title: `<ticket title>`
- Owner: `<team or person>`
- Product/domain owner: `<owner>`
- Target release: `<release or unknown>`
- Status: `DRAFT | READY_FOR_REVIEW | APPROVED | IMPLEMENTING | DONE`
- Related tickets/MRs: `<links or none>`

## 2. Business outcome

### Problem

What problem is being solved?

### Desired outcome

What should users or systems be able to do after this change?

### Scope

- `<in-scope item>`

### Out of scope

- `<explicitly excluded item>`

## 3. Actors, interfaces, and authorization

- Actors: `<user, service, AI client, scheduled process>`
- Required interface(s): `MCP | REST | GraphQL | AG-UI | event | batch`
- Required scope(s)/role(s): `<scope or role>`
- Data classification: `PUBLIC | INTERNAL | CONFIDENTIAL | PERSONAL`
- Confirmation required for side effects: `YES | NO`

## 4. Existing-system discovery

Complete this section before designing new code.

### Repository evidence

- Modules/packages inspected: `<paths>`
- Existing entry points: `<controllers, tools, resolvers, consumers>`
- Existing application services/use cases: `<classes or none>`
- Existing domain rules/validators: `<classes or none>`
- Existing outbound integrations: `<clients, repositories, ports>`
- Existing API/interface contracts: `<OpenAPI, GraphQL, MCP, events>`
- Existing tests: `<test classes/scenarios>`
- Existing architecture/security rules: `<paths or none>`

### Compatibility constraints

- Existing behavior to preserve: `<behavior>`
- Backward-compatibility requirement: `YES | NO`
- Migration or deprecation requirement: `<details or none>`

### Reuse decision

Select one for each relevant component:

- `REUSE` — use unchanged because its contract is suitable.
- `EXTEND` — add behavior while preserving the existing contract.
- `WRAP` — isolate legacy behavior behind a port.
- `REFACTOR` — improve structure with characterization tests first.
- `REPLACE` — migrate with an explicit compatibility and rollback plan.
- `NEW` — no suitable existing behavior exists.

| Component | Decision | Evidence/reason | Required regression test |
|---|---|---|---|
| `<component>` | `REUSE/EXTEND/WRAP/REFACTOR/REPLACE/NEW` | `<reason>` | `<test>` |

## 5. Specification

### Business rules

1. `<rule stated in testable terms>`

### Input contract

```text
<field>: <type>, required/optional, validation and data classification
```

### Successful output contract

```text
<status and safe result fields>
```

### Error contract

| Condition | Application category | Client-safe result |
|---|---|---|
| Invalid input | `VALIDATION_ERROR` | `<message/code>` |
| Missing permission | `FORBIDDEN` | `<message/code>` |
| Dependency failure | `DEPENDENCY_UNAVAILABLE` | `<message/code>` |
| Unexpected failure | `INTERNAL_ERROR` | `<message/code>` |

## 6. Acceptance scenarios

Use Given/When/Then. Each scenario must have executable test evidence.

```gherkin
Feature: <capability>

  Scenario: Authorized caller receives a successful result
    Given <precondition>
    And <additional precondition>
    When <caller invokes operation>
    Then <expected result>
    And <safe audit event is recorded>

  Scenario: Invalid input is rejected
    Given <authorized caller>
    When <caller submits invalid input>
    Then <validation error is returned>
    And <downstream dependency is not called>

  Scenario: Unauthorized caller is rejected
    Given <caller lacks the required permission>
    When <caller invokes operation>
    Then <unauthorized or forbidden result is returned>
    And <use case is not executed>

  Scenario: Downstream dependency fails
    Given <valid input>
    And <dependency is unavailable>
    When <caller invokes operation>
    Then <safe dependency error is returned>
    And <secret or internal details are not exposed>
```

## 7. DDD and application design

- Bounded context: `<context>`
- Aggregate root(s): `<aggregate or none>`
- Entities: `<entities or none>`
- Value objects: `<value objects or none>`
- Invariants: `<rules protected by the domain>`
- Domain services: `<services or none>`
- Domain events: `<events or none>`
- Command/query: `<command or query name>`
- Transaction boundary: `<boundary>`

### Use case

```text
<OperationUseCase>
  input:  <OperationCommand>
  output: <OperationResult>
  ports:  <required outbound ports>
```

### Ports and adapters

```text
MCP/REST/GraphQL/AG-UI adapter
    -> <OperationUseCase>
    -> domain rules
    -> <OutboundPort>
    -> external adapter
```

Keep adapters thin. Do not put business rules, repository access, OAuth2 handling, or model
provider code directly in MCP tools, controllers, resolvers, or UI handlers.

## 8. Interface contracts

Implement only interfaces required by this ticket. Use one application use case for equivalent
MCP, REST, GraphQL, and AG-UI operations.

### MCP

- Tool name: `<tool name>`
- Description: `<when to use and when not to use>`
- Side effects: `NONE | READ | WRITE | DESTRUCTIVE`
- Input schema: `<path or inline definition>`
- Confirmation: `REQUIRED | NOT_REQUIRED`

### REST

- Method/path: `<method> /api/<version>/<capability>`
- Request/response contract: `<OpenAPI path>`
- Compatibility/versioning: `<details>`

### GraphQL

- Query/mutation: `<name>`
- Schema contract: `<schema path>`

### AG-UI

- Action: `<action>`
- Events: `<started, progress, result, error, cancelled>`
- Cancellation behavior: `<behavior>`

## 9. AI and Spring AI behavior

Select one:

- `DETERMINISTIC` — no model is needed.
- `AI_ASSISTED` — model proposes or interprets; domain rules decide.
- `AI_GENERATED` — model produces content subject to schema and policy validation.

- Model/provider: `<provider or not applicable>`
- Prompt/skill artifact: `<versioned path or not applicable>`
- Expected output schema: `<schema>`
- Fallback behavior: `<behavior>`
- Evaluation cases: `<path or not applicable>`
- Token/cost limits: `<limits>`

Treat model output as untrusted input: parse it against a strict schema, apply deterministic
validation, and never execute arbitrary model-generated commands.

## 10. Implementation plan

Implement the smallest vertical slice in this order:

1. `<specification and acceptance scenarios>`
2. `<domain values, entities, or invariants>`
3. `<application command/query and use case>`
4. `<fake outbound port and focused tests>`
5. `<required inbound adapter>`
6. `<adapter contract tests>`
7. `<real outbound adapter>`
8. `<integration and security tests>`
9. `<documentation and operational configuration>`

| Step | Files/modules | Evidence required | Owner |
|---|---|---|---|
| 1 | `<paths>` | `<test/review evidence>` | `<owner>` |

## 11. KISS, YAGNI, and DRY review

### KISS

- Can the request be traced from adapter to use case without unnecessary indirection?
- Is the simplest suitable Spring mechanism being used?
- Is asynchronous processing genuinely required?

### YAGNI

- Which requested future capability is deliberately not implemented yet?
- Are MCP, REST, GraphQL, or AG-UI adapters limited to actual requirements?
- Are event sourcing, CQRS, microservices, or LLM orchestration justified by this ticket?

### DRY

- Which stable business rules are shared?
- Are similar but semantically different DTOs intentionally separate?
- Is reuse based on equivalent behavior rather than coincidental field names?

## 12. Security, privacy, and resilience

- Inbound authentication: `<issuer/audience/scope/role policy>`
- Outbound authentication: `<port and OAuth2/client policy>`
- Tenant/data boundary: `<boundary>`
- Input limits: `<limits>`
- Timeout: `<duration>`
- Retryable errors: `<categories>`
- Retry limit/backoff: `<policy>`
- Idempotency: `<key and behavior>`
- Sensitive data handling: `<redaction/minimization>`
- Rate limit/circuit breaker: `<policy or not required>`

Never log tokens, secrets, authorization headers, complete JWTs, raw sensitive prompts, or
unredacted request/response bodies.

## 13. Observability and audit

- Correlation ID: `<source and propagation>`
- Metrics: `<names and dimensions>`
- Traces: `<spans and safe attributes>`
- Audit event: `<event type and safe fields>`
- Dashboard/alert: `<link or not required>`
- Operational runbook: `<link or not required>`

## 14. Test plan and evidence

- [ ] Domain unit tests
- [ ] Application/use-case tests
- [ ] Inbound adapter contract tests
- [ ] Outbound adapter tests with deterministic stubs
- [ ] Validation and error-path tests
- [ ] Authentication and authorization tests
- [ ] Architecture tests
- [ ] Integration tests
- [ ] End-to-end/smoke test where required
- [ ] AI schema and evaluation tests where applicable
- [ ] Performance test where required
- [ ] Secret and dependency scans

Commands/evidence:

```text
<focused test command>
<contract test command>
<full verification command>
<CI job or report link>
```

## 15. ADR and migration

ADR required: `YES | NO`

If yes:

- ADR: `<path and number>`
- Decision: `<summary>`
- Alternatives rejected: `<alternatives and reasons>`

Brownfield migration:

```text
characterize existing behavior
  -> introduce/use existing application port
  -> move legacy path behind the port
  -> add new adapter
  -> compare old/new behavior
  -> remove legacy path only after evidence
```

Rollback:

- Deployment rollback: `<procedure>`
- Data/config rollback: `<procedure>`
- Feature flag or compatibility switch: `<switch or none>`

## 16. Risks and open questions

### Risks

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| `<risk>` | `LOW/MEDIUM/HIGH` | `<mitigation>` | `<owner>` |

### Open questions

- [ ] `<question>` — owner: `<owner>`, due: `<date>`

## 17. Definition of done

- [ ] Specification approved and linked to the ticket.
- [ ] Existing behavior and compatibility constraints are documented.
- [ ] Bounded context and ownership are confirmed.
- [ ] Domain rules and use case are implemented independently of transport.
- [ ] Required interface contract is versioned and tested.
- [ ] Existing logic is reused, wrapped, extended, or replaced intentionally.
- [ ] Unit, contract, security, integration, and architecture tests pass as applicable.
- [ ] AI evaluation passes when AI behavior changes.
- [ ] Timeouts, error mapping, idempotency, and sensitive-data handling are reviewed.
- [ ] Logs, metrics, traces, and audit events are safe.
- [ ] Formatting, static analysis, dependency, and secret scans pass.
- [ ] Documentation and rollback instructions are complete.
- [ ] No generated files, unrelated refactoring, credentials, or production data are included.

## 18. Review record

- Specification reviewer: `<name/date>`
- Architecture reviewer: `<name/date>`
- Security reviewer: `<name/date or not required>`
- Product/domain approval: `<name/date>`
- Implementation MR: `<link>`
- Final evidence: `<links>`

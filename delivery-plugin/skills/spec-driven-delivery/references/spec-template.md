# <Feature title>

Issue: #<issue-number> · Capability: <capability> · Status: Draft | Approved | Implemented

## Problem

What is wrong or missing today, for whom, and why it matters.

## Scope

## Out of scope

## Actors and authorization

Who calls this, through which interface, with which scope, role, or tenant boundary.

## Existing behavior

```text
Existing behavior found:
- <current entry point>
- <current use case or service>
- <current external integration>
- <current test coverage>

Reuse decision:
- Reuse: <component and reason>
- Extend: <component and compatibility plan>
- Wrap / Refactor / Replace: <component and migration plan>
- New: <component and reason reuse is not suitable>
```

## Business rules

Deterministic rules first. Mark any rule that needs AI interpretation and why.

## Input contract

## Output contract

## Error contract

Which of `VALIDATION_ERROR, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, DEPENDENCY_UNAVAILABLE,
TIMEOUT, RATE_LIMITED, INTERNAL_ERROR` can occur and how each protocol presents it.

## Acceptance criteria

```gherkin
Feature: <capability operation>

  Scenario: Authorized caller receives a successful result
    Given an authorized caller
    And valid input for <operation>
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

## Non-functional requirements

Latency, volume, cost, availability, data retention.

## Compatibility and migration

## Observability and audit

Correlation ID, metrics, log lines (sanitized), audit event shape.

## Test evidence

Which tests prove which scenario, and the command that runs them.

## Open decisions

Questions for the product owner. Do not guess them.

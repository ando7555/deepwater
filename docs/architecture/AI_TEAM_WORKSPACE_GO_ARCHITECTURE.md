# AI Team Workspace Go Architecture

## Purpose

AI Team Workspace is an isolated engineering workspace that helps developers plan,
implement, test, review, and learn software engineering tasks with AI assistance.

## Scope

The first version manages development tasks and their lifecycle.
It does not replace PitchMind and does not modify the existing Java backend.

## Initial Architecture

The system will use a modular Go backend with domain-driven boundaries:

- `domain`: business rules and entities
- `application`: use cases
- `adapters`: HTTP and external interfaces
- `infrastructure`: persistence, configuration, and messaging
- `cmd`: application startup

## Relationship With PitchMind

AI Team Workspace is developed independently in Go.
PitchMind Java remains unchanged until a future migration assessment proves
that migration is technically and operationally justified.

## Code Location

All Go implementation will live under:

`ai-team-workspace/`

The existing Java backend and frontend remain outside this module.

## API Boundary

The first service exposes a small HTTP API for creating, listing, and updating
workspace tasks.

HTTP handlers must not contain domain rules. They call application use cases.

## Persistence Decision

The first learning version uses in-memory storage.
A database will be introduced only after the domain and API behavior are tested.

## Testing Strategy

- Domain rules use unit tests.
- Application use cases use mock repositories.
- HTTP endpoints use integration tests.
- End-to-end tests are added after the first API flow works.

## Non-Goals

- No PitchMind Java migration yet.
- No AI provider integration yet.
- No Kafka or Neo4j integration yet.
- No production deployment yet.

## Definition Of Done

Task #90 is complete when the architecture is documented, the boundaries are
understood, and the design is ready for the first small Go service.
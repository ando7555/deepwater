# Feature plan: teacher can unpublish a published lesson (reversible)

> Method: `deepwater-developer:spec-driven-delivery`. Repository settings:
> `docs/delivery/repository-mapping.md`. Sections 14, 17 and 18 are left for implementation.
> Evidence below was read from branch `claude/project-thread-ukbqjc` (PR #105, same backend code as `main`) at commit `f4ef78b` on 2026-10-23.

## 1. Ticket metadata

- Ticket: `ando7555/deepwater#106` (ticket text pasted by the user; `gh` is not logged in here)
- Title: [Echo] Teacher can unpublish a published lesson (reversible)
- Owner: Andranik (`ando7555`)
- Product/domain owner: Andranik (product owner); teacher content decisions: the configured teacher account
- Target release: unknown (MVP, local Echo slice)
- Status: `DONE` pending review (code written, backend tests, bootJar and frontend build pass; see section 14). Open decisions OQ-1 to OQ-5 were answered by the product owner for this run: the recommended answer for each (section 16).
- Related tickets/MRs: PR #105 (this plugin, unrelated code); `STATUS.md` "Still needed" lists the action

## 2. Business outcome

### Problem

A teacher can only move a lesson forward (`saveEchoLessonDraft` -> `publishEchoLesson`). Once a lesson
is published the only way to change what learners see is to publish a revision, which archives the old
one. A lesson published by mistake, with a wrong answer key, or with a rights doubt on its audio
source cannot be taken away from learners.

### Desired outcome

A teacher can take a published lesson out of the learner view and can publish it again later,
without losing lesson content or learners' practice history.

### Scope

- Teacher-only action, using the same teacher check as publishing (`PlatformService.requireTeacher`).
- Learners stop seeing the unpublished lesson in their lesson list and audio recommendations.
- The lesson stays visible to the teacher and can be published again.
- Learners' existing attempts and review history are kept.
- Needed to make the above true (found in discovery, see 4 and 5): learners can no longer submit
  practice or audio evidence against an unpublished lesson; the teacher screen gets Unpublish and
  Publish-again buttons.

### Out of scope

- Deleting lessons.
- Review history screen.
- Notifying learners.
- Unpublishing several lessons at once, scheduled unpublish, a stated reason or note field (see OQ-4).

## 3. Actors, interfaces, and authorization

- Actors: the teacher (account whose email equals `deepwater.teacher.email`); learners (affected, not callers).
- Required interface(s): GraphQL only (the only interface the teacher workspace uses). No REST, MCP or AG-UI: no consumer.
- Required role: teacher, enforced server-side by `requireTeacher` in the resolver, exactly like `publishEchoLesson`.
- Data classification: INTERNAL (lesson content, teacher id in audit). No learner personal data is read or written.
- Confirmation required for side effects: YES in the UI (a confirm step before Unpublish, because learners lose access at once). The API call itself is a single explicit mutation and needs no second call.

## 4. Existing-system discovery

### Repository evidence

- Modules/packages inspected: `backend/src/main/java/com/deepwater/{echo,platform,api}`, `backend/src/main/resources/{schema.sql,graphql/schema.graphqls}`, `backend/src/test/java/com/deepwater`, `frontend/src/main.jsx`, `STATUS.md`.
- Existing entry points: `DeepwaterGraphQlController.publishEchoLesson` (line 67) and `saveEchoLessonDraft` call `echo.publish/saveDraft` after `platform.requireTeacher(...)`. Teacher queries: `teacherLessons`. Learner: `echoHome`, `chooseEchoLanguage`, `startEcho`, `submitEchoPractice`, audio evidence mutation.
- Existing application services: `EchoService` (one 534-line class holding use cases, SQL and records; no domain/application/adapter split yet).
- Existing domain rules: lifecycle is an unconstrained string column `echo_lessons.review_status` with values `DRAFT`, `PUBLISHED`, `ARCHIVED` (no check constraint, no enum). `publish` moves DRAFT -> PUBLISHED, archives the parent revision, and runs `validateAudioForPublication` (rights basis, link permission, rights evidence, expiry). `saveDraft` only accepts a revision parent that is `PUBLISHED`.
- Existing outbound integrations: `JdbcTemplate` only. `audit_events(id, event_type, aggregate_id varchar(36), occurred_at)` exists; practice and audio evidence write to it, `publish` does not.
- Existing contracts: `schema.graphqls` (`publishEchoLesson(lessonId: ID!): TeacherLesson!`; `TeacherLesson.reviewStatus: String!`).
- Existing tests: `MvpEndToEndTest` (draft -> publish -> learner practice -> revision archives parent; audio rights; ownership), `AcceptedAnswersTest`, `AccountSafetyTest`, `GoogleSignInTest`, `EmailVerificationTest`. No unit test of `EchoService` lifecycle in isolation.
- Existing architecture/security rules: `ARCHITECTURE.md`, `docs/architecture/DEEPWATER_TARGET_ARCHITECTURE.md`; "teacher approval before learners see content" is the rule the unpublish action must keep true.

Learner visibility evidence (all filter `review_status='PUBLISHED'`, so a new non-published status is hidden without further change):

| Learner surface | Where | Status filter |
|---|---|---|
| Lesson list and review-due hint | `EchoService.home` SQL (line 37-43) | `l.review_status='PUBLISHED'` |
| Audio recommendation | `chooseAudioRecommendation` works on that same list | inherited |
| Audio evidence | `audioEvidence` (line ~436) | `l.review_status='PUBLISHED'` |
| Text/choice practice | `practice` (line ~370) | **none**: looks the exercise up by id only |

**Gap found:** `practice()` does not check lesson status. A learner holding an exercise id (an open tab, a bookmark, a guessed `<lessonId>-<n>`) can still submit answers and move mastery and review dates for an unpublished lesson, and today also for a DRAFT or ARCHIVED one. Without fixing this, "learners stop seeing the lesson" is true on screen but not on the API.

### Compatibility constraints

- Existing behavior to preserve: draft -> publish -> revision -> archive flow (`MvpEndToEndTest`); learner queries return only published lessons; teacher `teacherLessons` returns every lesson including its new status; attempts, `echo_skill_states` and `echo_audio_attempts` are never touched by unpublish.
- Backward-compatibility requirement: YES. New mutation and a new status value only. `reviewStatus` stays a String, so existing GraphQL clients keep working. The frontend currently shows the raw status text and a button only for `DRAFT`, so an unknown status is harmless there.
- Migration or deprecation requirement: none. No schema change is needed (column is a free `varchar(24)`; `UNPUBLISHED` is 11 characters). No data migration.

### Reuse decision

| Component | Decision | Evidence/reason | Required regression test |
|---|---|---|---|
| `PlatformService.requireTeacher` | REUSE | Same check as publishing; ticket requires it | Learner calling unpublish gets FORBIDDEN; anonymous gets the existing auth error |
| `home` learner SQL and audio recommendation | REUSE | Already `PUBLISHED`-only; no change | Learner home no longer lists the lesson or recommends its audio after unpublish |
| `audioEvidence` status filter | REUSE | Already `PUBLISHED`-only | Audio evidence on an unpublished lesson returns "Published audio lesson not found." |
| `practice()` | EXTEND | Add "lesson must be PUBLISHED" to the exercise lookup (and the audio expiry rule is not its concern) | Characterize current behavior first (published lesson still accepted, mastery unchanged); then unpublished/draft/archived rejected with 404; submitted attempts before unpublish remain |
| `EchoService.publish` | EXTEND | Accept `UNPUBLISHED` as well as `DRAFT` as a source state, keep the audio rights check, skip parent archiving when republishing the lesson itself | Existing MvpEndToEnd revision test unchanged and green; republish with expired rights fails |
| `EchoService` new `unpublish` method | NEW | No existing behavior; sits next to `publish`, same transaction style | Unit and end-to-end tests in section 6 |
| `saveDraft` (revision parent rule) | REUSE, with one decision | Revision requires a `PUBLISHED` parent; unpublishing the parent while a revision draft exists is handled in publish (rule 7), see OQ-2 | EXTEND: a revision draft may also be created from an `UNPUBLISHED` lesson (found by `/dod`, see rule 8) |
| `teacherLessons` | REUSE | Already returns all statuses | Unpublished lesson listed with its exercises and audio |
| `audit_events` | EXTEND | Add two event types; reuse the table as is | Event row written for each state change |
| GraphQL schema | EXTEND | Add one mutation | Schema contract test via the end-to-end tests |
| Frontend teacher queue | EXTEND | Button only for `DRAFT` today (main.jsx line ~520) | `npm run build`; manual check |
| `echo_lessons` schema | REUSE | `review_status` is an unconstrained varchar | none |

## 5. Specification

### Lesson lifecycle (domain rule, deterministic)

```text
DRAFT --publish--> PUBLISHED --unpublish--> UNPUBLISHED --publish--> PUBLISHED
                       |
                       +--(a revision is published)--> ARCHIVED  (terminal, as today)
```

`UNPUBLISHED` is a new status, kept separate from `ARCHIVED`. `ARCHIVED` means "superseded by a
newer revision" and must never be republishable; `UNPUBLISHED` means "withdrawn by the teacher,
may return". Reusing `ARCHIVED` would make a replaced lesson look restorable.

### Business rules

1. Only the teacher can unpublish or republish. Checked in the resolver before the use case runs.
2. Unpublish applies only to a lesson whose status is `PUBLISHED`. Otherwise: not found if the id is unknown, conflict if it exists in another status. Unpublishing an already `UNPUBLISHED` lesson is rejected, not silently accepted, so the UI cannot show a false success (see OQ-3 for the idempotent alternative).
3. Unpublish changes only `review_status` to `UNPUBLISHED`. Lesson text, exercises, answer keys, audio rights fields, `version`, `revision_of`, `sort_order`, `skill_key`, `created_by`, `approved_by` and `approved_at` stay as they are. `content_status` text is set to `Unpublished by teacher` so the teacher view is honest.
4. Learners: an `UNPUBLISHED` lesson is absent from `echoHome.lessons`, from the audio recommendation (it is chosen from that list), and from the "review due" hint. `chooseEchoLanguage` and `startEcho` return the same filtered list.
5. Learners cannot submit practice (`submitEchoPractice`) or audio evidence for a lesson that is not `PUBLISHED`. Response: the existing not-found errors ("Learning exercise not found." / "Published audio lesson not found."), which do not reveal that the lesson exists.
6. Existing `echo_attempts`, `echo_audio_attempts` and `echo_skill_states` rows are not read, changed or deleted by unpublish or republish. Mastery and review dates are per `skill_key` and stay valid; a learner who returns after republish continues where they stopped.
7. Republish: `publish` accepts a lesson in `DRAFT` or `UNPUBLISHED`. For `UNPUBLISHED` it re-runs `validateAudioForPublication` (rights can have expired or been withdrawn while the lesson was down, which is the stated reason to unpublish) and refuses if another revision in the same lineage is currently `PUBLISHED` (conflict, "A newer revision of this lesson is published."). It sets `approved_by` and `approved_at` to the republishing teacher and time. It does not archive a parent (it is not a new revision).
8. Revisions and unpublish (recommended, OQ-2): if a lesson has an unpublished-then-revised lineage, the teacher chooses one version to bring back; the other stays as it is. Concretely: publishing a `DRAFT` revision whose parent is `UNPUBLISHED` is allowed and moves the parent to `ARCHIVED` (today it requires a `PUBLISHED` parent and fails). Without this, unpublishing a lesson that has a pending revision draft would make that draft unpublishable. For the same reason, `saveDraft` accepts an `UNPUBLISHED` lesson as the parent of a new revision, so a teacher can withdraw a lesson, fix it as revision v+1 and publish the fix.
9. Unpublish is atomic: one conditional `update ... where id=? and review_status='PUBLISHED'`; if it changes 0 rows the use case returns the not-found/conflict error. A concurrent second call therefore cannot succeed twice.
10. Every state change writes an audit event: `ECHO_LESSON_UNPUBLISHED` and `ECHO_LESSON_REPUBLISHED`, with the lesson id as `aggregate_id`. Lesson ids are at most 29 characters in the seed files and UUIDs (36) otherwise, so they fit `varchar(36)`; teacher id is not stored in the existing columns (see 13).
11. Out of scope kept out: no deletion, no learner notification, no reason text.

### Input contract

```text
unpublishEchoLesson(lessonId: ID!)   // required, non-blank, at most 120 chars (column width); teacher bearer token
publishEchoLesson(lessonId: ID!)     // unchanged signature; now also accepts an UNPUBLISHED lesson
```

### Successful output contract

```text
TeacherLesson (existing type) with reviewStatus "UNPUBLISHED" after unpublish, "PUBLISHED" after republish.
No learner data, no answer keys beyond what the teacher view already returns.
```

### Error contract

| Condition | Application category | Client-safe result |
|---|---|---|
| Blank or over-long lesson id | `VALIDATION_ERROR` | 400 "Choose a lesson to unpublish." |
| Not signed in / not the teacher | `FORBIDDEN` | existing "Teacher access is not enabled for this account." (or existing sign-in error); use case not executed |
| Lesson id unknown | `NOT_FOUND` | 404 "Published lesson not found." |
| Lesson exists but is not `PUBLISHED` (draft, archived, already unpublished) | `CONFLICT` | 409 "Only a published lesson can be unpublished." |
| Republish with a newer revision already published | `CONFLICT` | 409 "A newer revision of this lesson is published." |
| Republish with rights expired/unverified | `CONFLICT` | existing audio-rights message |
| Database failure | `DEPENDENCY_UNAVAILABLE` / `INTERNAL_ERROR` | existing generic server error; no SQL text |

These reuse `ResponseStatusException` exactly as `publish` does today; no new exception type.

## 6. Acceptance scenarios

```gherkin
Feature: Teacher unpublishes and republishes an Echo lesson

  Scenario: Teacher unpublishes a published lesson
    Given a published German lesson and a learner who has answered one of its exercises
    When the teacher calls unpublishEchoLesson for it
    Then the result has reviewStatus "UNPUBLISHED"
    And teacherLessons still lists it with all exercises
    And an ECHO_LESSON_UNPUBLISHED audit event exists for the lesson id

  Scenario: Learners stop seeing it
    Given the lesson was just unpublished
    When the learner opens echoHome
    Then the lesson is not in lessons
    And it is not the recommended audio lesson (audio case)
    And the learner's attempt count and mastery are unchanged

  Scenario: Learner cannot keep practising it
    Given a learner still holds an exercise id of the unpublished lesson
    When the learner submits an answer or audio evidence
    Then the existing not-found error is returned
    And no attempt row is written

  Scenario: Teacher republishes
    Given an unpublished lesson
    When the teacher calls publishEchoLesson for it
    Then reviewStatus is "PUBLISHED"
    And the learner sees it again with the earlier mastery and review date intact

  Scenario: Republish re-checks audio rights
    Given an unpublished audio lesson whose rights expired meanwhile
    When the teacher republishes it
    Then a 409 audio-rights error is returned and the lesson stays UNPUBLISHED

  Scenario: Non-teacher is rejected
    Given a signed-in learner or no token
    When unpublishEchoLesson is called
    Then FORBIDDEN or the sign-in error is returned
    And the lesson stays PUBLISHED and no audit event is written

  Scenario: Wrong state is rejected
    Given a DRAFT, ARCHIVED or already UNPUBLISHED lesson, or an unknown id
    When the teacher calls unpublishEchoLesson
    Then 409 (or 404 for unknown) is returned and nothing changes

  Scenario: Revision lineage stays consistent
    Given lesson A published and a revision draft B of A
    When the teacher unpublishes A and then publishes B
    Then B is PUBLISHED and A is ARCHIVED
    And republishing A afterwards is rejected with "A newer revision of this lesson is published."

  Scenario: Existing flow is unchanged
    Given the current draft -> publish -> revision -> archive end-to-end test
    Then it passes without edits
```

## 7. DDD and application design

- Bounded context: Echo (language learning), package `com.deepwater.echo`.
- Aggregate root: Lesson (`echo_lessons` with its exercises and optional audio data). Attempts and skill states belong to the learner and are not part of this aggregate.
- Entities / value objects: lesson, exercise; new enum `LessonStatus` (`DRAFT, PUBLISHED, UNPUBLISHED, ARCHIVED`) names the states and documents the lifecycle.
- Invariants: transitions as in the lifecycle diagram; only `PUBLISHED` lessons are learner-visible and practisable; audio rights are valid at every moment a lesson is published; attempts are never deleted by a status change.
- Domain services: none. The transitions are enforced by conditional SQL updates in `EchoService` so they stay atomic; a separate predicate table was written first and removed after `/dod` found it was only used by its own test (duplicated rules that could drift).
- Domain events: none emitted; audit rows only (no Kafka, per repository mapping).
- Command: `UnpublishLessonCommand(teacherId, lessonId)`; extended `PublishLessonCommand`.
- Transaction boundary: one `@Transactional` method per use case (status update, parent archive when needed, audit insert commit together).

### Use case

```text
UnpublishLesson
  input:  teacherId, lessonId
  output: TeacherLesson
  ports:  JdbcTemplate (today; no port interface yet)
```

### Ports and adapters

```text
GraphQL resolver DeepwaterGraphQlController.unpublishEchoLesson
    -> platform.requireTeacher(authorization)
    -> EchoService.unpublish(teacherId, lessonId)
    -> status rules (conditional update)
    -> JdbcTemplate -> echo_lessons, audit_events
```

Brownfield strategy: do not split `EchoService` for this ticket. Add `unpublish` beside `publish`,
put the transition rules in a small pure class, and note that extracting a repository port
is a later, separate refactor with characterization tests.

## 8. Interface contracts

### MCP
Not applicable. No consumer.

### REST
Not applicable. The teacher workspace uses GraphQL.

### GraphQL

- Mutation: `unpublishEchoLesson(lessonId: ID!): TeacherLesson!`
- Schema contract: `backend/src/main/resources/graphql/schema.graphqls`, next to `publishEchoLesson`. `reviewStatus` stays `String!`; documented values become `DRAFT | PUBLISHED | UNPUBLISHED | ARCHIVED`.
- Frontend (`frontend/src/main.jsx`, teacher queue): show "Unpublish" for `PUBLISHED` (with a confirm: "Learners will stop seeing this lesson immediately. Their progress is kept.") and "Publish again" for `UNPUBLISHED`; keep "I approve & publish" for `DRAFT`. Reuse the existing `gql`, `setNotice`, `setError`, `loadTeacherLessons`, `loadHome` pattern of `publishDraft`.

### AG-UI
Not applicable.

## 9. AI and Spring AI behavior

`DETERMINISTIC`. A status change driven by a teacher decision needs no model. Model/provider, prompt
artifact, output schema, fallback, evaluation cases and token limits: not applicable. No AI call is
added, consistent with the repository state (no AI dependency yet).

## 10. Implementation plan

1. Add this specification; get answers to OQ-1 to OQ-4.
2. Characterization tests for current `practice()` (published ok) and `publish` (revision archives parent, audio rights).
3. `LessonStatus` enum naming the states (lifecycle in its Javadoc).
4. `EchoService.unpublish`, extend `publish` (UNPUBLISHED source, lineage check, parent-UNPUBLISHED archive), add `PUBLISHED` filter to `practice()`, audit rows.
5. GraphQL: schema line + resolver method (thin, teacher check first).
6. End-to-end tests (section 6 scenarios) in a new `UnpublishLessonTest` using its own `jdbc:h2:mem:` URL (the shared in-memory DB must not receive extra published lessons, per the repository mapping).
7. Frontend buttons and confirm.
8. `STATUS.md`: move "reversible unpublish action" from *Still needed* to *Implemented*; `TEACHER_GUIDE.md`: one short section on unpublish/republish.

| Step | Files/modules | Evidence required | Owner |
|---|---|---|---|
| 2-4 | `echo/EchoService.java`, new `echo/LessonStatus.java` | `./gradlew :backend:test --tests 'UnpublishLessonTest'` | implementing developer |
| 5 | `api/DeepwaterGraphQlController.java`, `graphql/schema.graphqls` | end-to-end GraphQL calls in the new test | implementing developer |
| 6 | `src/test/java/com/deepwater/UnpublishLessonTest.java` | full `:backend:test :backend:bootJar` green | implementing developer |
| 7 | `frontend/src/main.jsx` | `cd frontend && npm ci && npm run build`; manual check of the three buttons | implementing developer |
| 8 | `STATUS.md`, `TEACHER_GUIDE.md` | diff review | product owner |

## 11. KISS, YAGNI, and DRY review

### KISS
One new mutation, one new status value, no schema migration, no new table, no async work, no new class beyond a tiny status helper.

### YAGNI
Not built: reason/note field, bulk or scheduled unpublish, learner notification, history screen, soft-delete, MCP/REST adapters, event publication, a repository port, a general state-machine library.

### DRY
Shared: `requireTeacher`, `validateAudioForPublication`, `teacherLessons` lookup, `ResponseStatusException` style, existing status filters. Two checks on "is this lesson PUBLISHED" now live in `practice()` and `audioEvidence()`; both stay as SQL conditions (equivalent behavior) rather than a premature shared helper.

## 12. Security, privacy, and resilience

- Inbound authentication: bearer session token; teacher check by configured email, server-side, in the resolver (as for publish).
- Outbound authentication: not applicable.
- Tenant/data boundary: single-teacher deployment; learner data is not read by the use case.
- Input limits: `lessonId` at most 120 characters, non-blank.
- Timeout / retry / rate limit: not required for a single-row update; repeated calls are safe because the conditional update succeeds only once.
- Idempotency: unpublish of a non-published lesson returns 409 (rule 2); republish of a published lesson returns 404 "Draft lesson not found" as today.
- Sensitive data handling: audit rows hold lesson ids only; no emails, answers or tokens in logs.
- Information leak: learner-facing errors for practice stay the existing not-found messages.

## 13. Observability and audit

- Correlation ID: not present in the repository today; not added here.
- Metrics/traces/dashboard/runbook: not required for this MVP.
- Audit event: `ECHO_LESSON_UNPUBLISHED`, `ECHO_LESSON_REPUBLISHED`; `aggregate_id` = lesson id; `occurred_at` by default. The acting teacher is not stored because the table has no actor column; with a single configured teacher this is acceptable (OQ-4 asks whether to add a column; it would be a schema change).

## 14. Test plan and evidence

- `backend/src/test/java/com/deepwater/UnpublishLessonTest.java` (own `jdbc:h2:mem:` URL, GraphQL end to end, 9 tests): unpublish, hide, keep progress, republish and audit rows; draft and archived exercises rejected in `practice()`; audio recommendation and audio evidence after unpublish; republish with expired rights; non-teacher and anonymous; wrong state, unknown and invalid id; revision published while the parent is unpublished; revision created from an unpublished lesson; republish of an unpublished revision leaves its archived parent alone.
- `MvpEndToEndTest` and the other existing tests are unchanged and are the regression check for the `practice()` and `publish` changes.
- Not applicable: outbound adapter, AI, architecture tests (none exist in the repo), performance.
- Not automated: the frontend buttons and confirm dialog (manual check, section 18).

Commands and results (2026-10-23, cloud container with OpenJDK 25; `gradlew` is not executable in git, so it is run as `sh ./gradlew`):

```text
sh ./gradlew :backend:compileJava :backend:compileTestJava         BUILD SUCCESSFUL
sh ./gradlew :backend:test --tests 'UnpublishLessonTest'          BUILD SUCCESSFUL, 9 tests, 0 failures
sh ./gradlew :backend:test :backend:bootJar                       BUILD SUCCESSFUL, 30 tests in 8 classes, 0 failures, 0 errors
cd frontend && npm ci && npm run build                            built (vite), index-DIngy1pC.js
```

## 15. ADR and migration

ADR required: NO. The decision (new `UNPUBLISHED` status distinct from `ARCHIVED`) is small, reversible and recorded here.

Brownfield migration: none. Characterize `practice()` and `publish` first (step 2), then extend.

Rollback:

- Deployment rollback: redeploy the previous backend. Lessons left `UNPUBLISHED` would then be invisible to learners and to the old teacher button (it only acts on `DRAFT`).
- Data/config rollback: `update echo_lessons set review_status='PUBLISHED' where review_status='UNPUBLISHED'` restores visibility (only after the teacher agrees). No schema change to undo.
- Feature flag: none.

## 16. Risks and open questions

### Risks

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| `practice()` has no status check, so unpublish would hide a lesson on screen but still accept answers | HIGH | Rule 5, characterization test first | implementer |
| Adding the check changes behavior for DRAFT/ARCHIVED exercises too (today accepted) | LOW | That is the intended rule ("teacher approval before learners see content"); call it out in the PR | implementer |
| Unpublish while a revision draft exists strands the draft | MEDIUM | Rule 8 | implementer |
| Republish after rights lapsed | MEDIUM | Rule 7 re-runs audio validation | implementer |
| A learner mid-session sees an error when the lesson disappears | LOW | Frontend shows the error text and reloads home; no notification (out of scope) | implementer |
| `EchoService` grows further (534 lines) | LOW | Split later, one use case at a time | product owner |
| Audit table cannot record the actor or a reason | LOW | OQ-4 | product owner |

### Open questions

Answered by the product owner's side for this run (2026-10-23): "take your recommended answer for every open question".

- [x] OQ-1 Separate `UNPUBLISHED` status, not reusing `ARCHIVED`: YES.
- [x] OQ-2 Revision B may be published while its parent A is unpublished; A becomes `ARCHIVED`: YES (rule 8).
- [x] OQ-3 Unpublishing an already unpublished lesson: error (409), not silent success.
- [x] OQ-4 No reason field and no actor column for now (YAGNI; one teacher).
- [x] OQ-5 The `PUBLISHED` check in `practice()` also blocks draft and archived exercises: YES.

Implementation note on rule 7: a lesson archived by a newer revision is not `UNPUBLISHED`, so the
"A newer revision of this lesson is published." conflict is raised in `publish` when the id is not a
draft or unpublished lesson but a published lesson has it as `revision_of`. Any other id that cannot
be published still returns "Draft lesson not found." (unchanged).

## 17. Definition of done

- [x] The ticket and specification agree (decisions recorded in section 16).
- [x] Existing behavior was inspected and classified.
- [x] The bounded context and data owner are documented (Echo, `echo_lessons`).
- [x] One application use case owns the workflow (`EchoService.unpublish`; `publish` extended).
- [x] Required interfaces call the same use case (GraphQL only, as the ticket requires).
- [ ] Domain code has no framework dependency: not met, the rules run as SQL in a Spring service (brownfield, as planned); `LessonStatus` itself has none.
- [ ] Outbound dependencies are behind narrow ports: not met, `JdbcTemplate` is used directly as today (planned in section 7).
- [x] Acceptance, security and integration tests: 9 GraphQL end-to-end tests pass; no separate contract test beyond them.
- [x] AI output: not applicable (deterministic).
- [x] Timeouts, errors, retries and idempotency reviewed (sections 5 and 12).
- [x] Logs and audit events contain no sensitive values (lesson ids only).
- [ ] Formatting, static analysis, architecture tests and scans: none exist in the repo; SonarCloud runs in CI on the pull request.
- [x] Documentation and rollback plans complete (`STATUS.md`, `TEACHER_GUIDE.md`, section 15); no migration needed.
- [x] No unrelated or generated files are included.
- [x] Committed and pushed only after the definition-of-done review.

## 18. Review record

- Pull request: opened against `claude/project-thread-ukbqjc` (PR #105, the plugin), so the diff shows only this ticket.
- `/deepwater-developer:dod` review: findings 1 (stale sections 14/17), 2 (revision of an unpublished lesson) and 3 (unused status predicates) fixed; 4 and 5 accepted as documented.
- Reviewer: Andranik (pending).
- Manual check (2026-10-23, Chromium via Playwright against `bootJar` + Vite dev server, H2 in memory): teacher signs in, publishes the seeded Danish draft, selects Unpublish, accepts the confirm text "Learners will stop seeing this lesson immediately. Their progress is kept.", sees status `UNPUBLISHED`, the notice and a Publish again button, selects it and sees `PUBLISHED`. Learner-side hiding is covered by the automated tests, not clicked.

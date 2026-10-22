# Feature specifications

One file per GitHub issue: `docs/specs/<issue-number>-<capability>.md`, for example
`docs/specs/107-lesson-draft-suggestions.md`. Drafts without an issue use `draft-<capability>.md`
until the issue exists.

Specifications are written before code, from the template in
`delivery-plugin/skills/spec-driven-delivery/references/spec-template.md`, usually with
`/delivery-plugin:spec <issue>` or `/delivery-plugin:feature <issue>`. A pull request links the
specification it implements. See ADR-0001 in `docs/architecture/adr/`.

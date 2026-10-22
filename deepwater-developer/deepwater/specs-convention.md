# Feature specifications in Deepwater

One file per GitHub issue: `docs/specs/<issue-number>-<capability>.md`, for example
`docs/specs/107-lesson-draft-suggestions.md`. Drafts without an issue use
`docs/specs/draft-<capability>.md` until the issue exists.

Specifications are written before code, from the template in
`skills/spec-driven-delivery/references/spec-template.md`, usually with
`/deepwater-developer:spec <issue>` or `/deepwater-developer:feature <issue>`. A pull request links
the specification it implements. ADRs go to `docs/architecture/adr/<nnnn>-<decision>.md`; the
decision to adopt this method is `adr-0001-spec-driven-delivery-plugin.md` in this folder and
moves to `docs/architecture/adr/` once accepted.

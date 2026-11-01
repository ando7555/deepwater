# Harbor — your CV becomes a talent profile, matched to jobs in any country

Status: MVP built on branch `claude/project-thread-6sijou` (2026-11-01). Requested by the product owner.
Not deployed. Design choices are untested with real job seekers.

## Why the name

Deepwater is the parent brand; a harbor is where you land when you move to a new country.
The product's hardest question is exactly that one: *can I work there, and will they sponsor me?*

## Problem

A person moving abroad (for example to Denmark, Sweden, Norway or Switzerland) must read every posting
to learn (1) whether it fits their skills and (2) whether they could legally take it. Job boards rarely
answer the second question for them.

## What the MVP does (verified by tests and screenshots)

| Step | What happens | Where |
| --- | --- | --- |
| Upload CV | PDF (PDFBox) or text, max 5 MB, or paste text. Fixed rules (no AI) read headline, summary, years, ~80 known skills and language names. | `CvExtractor`, `SkillCatalog` |
| Talent profile | The person checks and corrects the draft, and adds citizenship *group* (EU/EEA, Swiss, other, prefer not to say), target countries and remote preference. | `TalentProfileService` |
| Matched jobs | Each posting is scored 0–100 with reasons: up to 60 for skills, 25 for title vs headline, 15 for a target country or acceptable remote. Visa never changes the score. | `JobMatcher`, `JobSearchService` |
| Visa filters | Each posting carries what its own text says about sponsorship (offered / refused / not mentioned) and what the person needs (no permit / permit / check). Filters: open to me, sponsorship offered, no permit needed, hide refusals. | `VisaSignals`, `WorkPermitRules` |
| Permit guide | Short summary and official site for DK (SIRI), SE (Migrationsverket), NO (UDI), CH (SEM). Not legal advice. | `WorkPermitRules.GUIDES` |
| Apply | Opens the original posting; Harbor never sends anything to employers. The person tracks status (saved, applied, interview, offer, rejected, withdrawn) and a note. | `ApplicationService` |
| Privacy | CV text, profile and applications are private, can be deleted (CV separately), and are deleted with the Deepwater account. | `PlatformService.deleteAccount` |

## Where job data comes from

* **Sample postings (always on):** 18 postings in `backend/src/main/resources/harbor/sample-jobs.json`, written for the demo.
  The companies are invented and labelled "(sample)"; there is no employer behind them.
* **Arbeitnow (off by default):** the public, key-free Arbeitnow job board API (mostly Germany/EU).
  Turn on with `HARBOR_ARBEITNOW_ENABLED=true`. Refreshes at most every 10 minutes, 3 pages.
  The parser is unit-tested against the documented response shape, but it has **not** been run against the live API:
  the cloud build environment blocks that host. Public sources disagree on whether the API's `visa_sponsorship`
  filter works, so Harbor reads sponsorship from each posting's text and only trusts a per-posting field if present.

"Any country" is the goal, not the current reach. Candidate sources to add next (not built):
Adzuna API (many countries, free key), JobTech JobSearch (Sweden's public employment service, open),
NAV (Norway, public token), EURES (no open API found yet). Scraping LinkedIn/Indeed/Jobindex is ruled out (terms of use).

## API (REST, needs a Deepwater session)

`GET/PUT /api/v1/harbor/profile`, `POST /api/v1/harbor/cv` (multipart `file`), `POST /api/v1/harbor/cv/text`,
`DELETE /api/v1/harbor/cv`, `GET /api/v1/harbor/jobs?q&country&remote&visa&minScore&source&max`,
`GET /api/v1/harbor/sources`, `POST /api/v1/harbor/jobs/refresh`,
`GET /api/v1/harbor/applications`, `PUT|DELETE /api/v1/harbor/applications/{jobId}`.

## Decisions taken in this MVP (proposals until the owner confirms)

1. Separate product in the same modular monolith and the same Deepwater account; a product switch (Echo / Harbor) in the header.
2. Rule-based CV reading and matching first, so results are explainable and testable; an AI provider can later improve extraction behind the planned provider port, with these rules as the non-AI fallback and baseline.
3. Store only a citizenship group, never a nationality.
4. "Apply" = deep link + private tracker. No automatic applications.

## Open questions for the owner

* Which job sources to license or connect first, and in which countries.
* Should Harbor have its own sign-in page and landing, or stay inside the shared app?
* AI-drafted cover letters / CV tailoring: wanted, and with which provider?
* Business model (job seekers, employers, or relocation partners).

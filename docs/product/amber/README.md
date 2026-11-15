# Amber: first version

Owner ask (2026-11-12): "implement amber now".

Amber is Deepwater's connections product: dating and new friends, where every suggestion explains
itself and a conversation opens only when both people say yes. This first version follows the Amber
MVP and acceptance criteria already written in
[DEEPWATER_PRODUCT_SCOPE_AND_ACCEPTANCE.md](../DEEPWATER_PRODUCT_SCOPE_AND_ACCEPTANCE.md)
(section "Amber second" and AC-AMBER-1..6) and the design concept in the shared folder
(`branding/amber/amber-concept.html`).

## What is confirmed and what is a proposal

| Item | Status |
|---|---|
| Amber is a separate product in the Deepwater family, for dating and connections | Owner, 2026-10-17 |
| Joinable without Echo; Echo shares only a coarse, opt-in signal (the language being learned) | Owner, 2026-10-17 |
| Consent path: explained suggestions, pass/hide, invitation, chat only after mutual yes, block/report | Written in the scope doc and concept page; this PR builds it. Not yet reviewed by the owner as a spec. |
| Adults only (18+), self-declared age plus a confirmation tick | Proposal (scope doc says "adults"; no age verification exists) |
| Two intents only: Dating and New friends | Proposal, from the concept page |
| Suggestion rule: must share at least one intent; area and interests are reasons, not filters | Proposal |
| Pass comes back after 30 days, Hide is permanent | Proposal |
| Max 10 unanswered invitations, max 20 messages a minute | Proposal (anti-spam) |
| Messages refresh every 8 seconds; no push or email notifications | Proposal (no websockets for the MVP) |
| Messages stay stored after a conversation ends (for report review), deleted when either person leaves Amber or deletes the account | Proposal; retention policy is the owner's decision |
| Reports are stored with status OPEN; there is no reviewer screen or moderation team yet | Gap; the scope doc says Amber must not launch publicly until moderation is staffed |
| No photos, no age-range preference, no distance | Not built; owner decisions |

## Rules (AmberRules.java)

1. Two visible members can be suggested to each other only when they share at least one intent.
2. Each overlap becomes a sentence the person reads: shared intent, same area (exact text match,
   case-insensitive), shared interests, and the same Echo language when **both** switched that on.
3. Order: same area first, then more shared interests, then same language. No hidden score.
4. Not suggested: yourself, anyone blocked in either direction, anyone you passed in the last 30 days
   or hid, anyone with an open invitation with you, anyone who declined your invitation.
5. A hidden profile ("Visible in discovery" off) neither appears nor sees suggestions.

## Privacy and safety

- Other members see a random `memberId`, never the account id or email.
- Unknown, hidden and blocked members get the same "not available" answer, so a block cannot be probed.
- A decline shows the sender only "Closed", with no reason or time.
- Block ends any invitation or conversation; report also blocks. The reported person is never told.
- Blocks are stored by account, so leaving and rejoining Amber does not undo someone else's block.
- "Leave Amber" removes the profile, invitations, messages and pass/hide choices; the Deepwater
  account, Echo and Harbor stay. Account deletion also removes blocks and the person's own reports.
- No AI is involved. Amber data is not sent to any model.

## API (`/api/v1/amber`, Deepwater sign-in)

`GET/PUT/DELETE /profile` · `GET /suggestions` · `POST /suggestions/{memberId}/pass|hide` ·
`POST /invitations` · `GET /invitations` · `POST /invitations/{id}/accept|decline|withdraw` ·
`GET /connections` · `GET/POST /connections/{id}/messages` · `POST /connections/{id}/leave` ·
`POST /safety/block` · `POST /safety/report`

## Tests

`AmberRulesTest` (rules) and `AmberApiTest` (one test per AC-AMBER-1..6 plus account deletion);
`frontend/src/amber/amberTools.test.mjs`.

## Before a public pilot (from the scope doc)

Moderation process and reviewer screen for reports, published safety policy, age assurance beyond
self-declaration, abuse response staffing, data export, and a test with real users.

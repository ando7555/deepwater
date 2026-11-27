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
| One Deepwater profile: Amber reads name, birth date (age), city, bio, languages and photos from the account's profile | Owner, 2026-11-27 ("ten profil będzie cały dla Deepwater") |
| Photos: up to 4, visible only after both say yes unless the person chooses "everyone in Amber" | Owner chose the default, 2026-11-27 |
| Optional card facts, each switched on by the person: languages known (on by default), Echo languages and levels (off), "Uses Harbor" (off) | Owner ask 2026-11-27; defaults are a proposal |
| Language exchange reasons, "Close kindly" goodbyes, whose-turn line | Proposal, from [competitor-pain-points.md](competitor-pain-points.md) |
| Photos are not moderated by anyone | Gap; needs an owner decision before a public launch |
| No age-range preference, no distance | Not built; owner decisions |

## Rules (AmberRules.java)

1. Two visible members can be suggested to each other only when they share at least one intent.
2. Each overlap becomes a sentence the person reads: shared intent, same area (exact text match,
   case-insensitive), shared interests, and the same Echo language when **both** switched that on.
3. Order: same area first, then more shared interests, then same language. No hidden score.
4. Not suggested: yourself, anyone blocked in either direction, anyone you passed in the last 30 days
   or hid, anyone with an open invitation with you, anyone who declined your invitation.
5. A hidden profile ("Visible in discovery" off) neither appears nor sees suggestions.
6. Language exchange: "Speaks X, which you are learning" when the other person shows their languages and speaks
   X natively or fluently (the viewer's own learning is used only for the viewer); "Is learning X, which you
   speak" only when the other person shows what they learn. Ranked after shared interests.
7. People whose Deepwater profile lost a required field (first name others see, age, city) are not suggested.

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
`GET /connections` · `GET /connections/closed` · `GET/POST /connections/{id}/messages` ·
`POST /connections/{id}/leave` (optional `{"note": "NOT_A_MATCH" | "NO_TIME" | "MET_SOMEONE"}`) ·
`GET /members/{memberId}/photos/{photoId}` · `POST /safety/block` · `POST /safety/report`

Deepwater profile and account (`/api/v1/account`): `GET/POST /photos` · `GET/DELETE /photos/{id}` ·
`POST /photos/{id}/first` · `GET /export` · `POST /sign-out-everywhere`. Text fields of the profile:
GraphQL `myProfile` / `updateMyProfile` (publicName, birthDate, bio, languages).

## Tests

`AmberRulesTest` (rules), `AmberApiTest` (one test per AC-AMBER-1..6 plus account deletion),
`AmberSharedProfileApiTest` (shared profile, photos rule, opt-in fields, language exchange, close kindly),
`AccountManagementApiTest` (data download, sign out everywhere, deletion);
`frontend/src/amber/amberTools.test.mjs`.

## Before a public pilot (from the scope doc)

Moderation process and reviewer screen for reports, published safety policy, age assurance beyond
self-declaration, abuse response staffing, data export, and a test with real users.

# UI quality pass: clean, calm, reliable (2026-11-15)

Owner ask: "make deepwater product very reliable, inspired by Apple, UX/UI elegant, clean and reusable components",
and earlier "a lot of hard coded code and settings, we need to make dynamic".
"Inspired by Apple" was read as qualities (restraint, clarity, consistency, obvious next action), not a copied look.

## How it was checked

Same ten screens captured before and after on a local build (backend on an in-memory database, test accounts only),
desktop 1280 px and phone 390 px: sign-in, Echo today, cards, progress, Polish B1, Harbor, Amber, profile,
Harbor with the server returning a 502 HTML page, and Echo with the network cut. The script also measures
horizontal overflow on every screen. Screenshots: shared folder `branding/screenshots/2026-11-15-ui-quality/`.

## What was found (before)

| Area | Finding |
| --- | --- |
| Reliability | Four separate copies of fetch + token + error handling (main, Harbor, Amber, Polish exam). None handled the server being down: Railway's 502 page is HTML, so screens showed "The request could not be completed." or "Unexpected token <". A dropped connection showed the raw "Failed to fetch". |
| Reliability | No retry anywhere. A failed first load left a dead screen; in Echo the only thing left on the page was "Delete my account". |
| Reliability | An expired session was never detected; calls just failed one by one. |
| Reliability | No crash guard: a bug in one product would blank the whole app. |
| Reliability | Half-written Polish exam answers were lost when switching to Danish or to Harbor and back. Echo's banners followed you into Harbor and Amber. |
| Hard-coded | `code === 'da' ? 'Danish' : 'German'` in 8 places (Polish missing in some); the Echo GraphQL selection copied 3 times; product names and hints in 3 lists; storage keys spelled in 12 places, each with its own try/catch. |
| Visual | 18 different font sizes, 11 corner radii, 9 breakpoints; gradient buttons that jump on hover; one-off colours in rules. |
| Phone | Header stacked brand switch, theme swatches, avatar and "Sign out" in a column: about 330 px before any content. The 3D depth gauge pushed Echo's tabs below the first screen. Amber's four tabs were cut off ("My profile" hidden). |
| Layout | No horizontal overflow on any captured screen, before or after. |

## What changed (after)

- **One API client** (`frontend/src/app/http.js`): offline, timeout (20 s), server error (including HTML 502), session expired and
  "check your input" each become an `ApiError` with a plain sentence. A 4xx keeps the server's own sentence ("Use 12–72 characters.");
  a 5xx never shows internals. An expired session signs out and says why.
- **Loading and error states**: `Loading` (quiet skeleton) and `ErrorState` (plain words + "Try again" when a retry can help) in Echo, Harbor,
  Harbor jobs and tracker, Amber (profile, discover, invitations, conversations) and the Polish exam.
- **Crash guard**: each product runs inside `ErrorBoundary`; a crash shows "Harbor stopped working… Try again" instead of a blank page.
- **Input that survives**: Polish exam answers and the Echo onboarding goal are kept per tab (`useDraft`) until handed in.
  Banners can be dismissed and are cleared when you switch product.
- **Calmer header**: brand, product switch, and one account button. Theme, brand (Deepwater | Nibble), profile and sign-out moved into the account menu.
  On phones the header is two rows instead of five.
- **Config, not code**: `app/config.js` holds products, themes and Echo languages; `app/storage.js` holds every storage key and never throws;
  `echoQueries.js` holds the Echo selection once. `main.jsx` lost the sign-in page, header and account deletion into their own files (649 → about 500 lines).
- **Design tokens**: type scale (9 sizes), radius scale (5), space and motion tokens, two breakpoints (600, 860). Buttons are solid accent with
  a soft hover, no jump. One-off hex colours moved to tokens, except the theme swatches, which preview each theme.
- **Phone**: depth gauge sits beside its caption; tabs with four items form a 2 × 2 grid.
- **Tests**: `npm test` now renders the shared components (`ui/ui.test.mjs`) and covers the API client and config/storage: 33 tests, all passing.

## Kept as they were

3D Deepwater mark, no streaks/hearts/leagues, Deepwater | Nibble toggle, the three themes, all product copy.

## Known limits and open points

- `echoHome` answers without a valid session (by design on the backend), so an expired session is noticed on the next Echo action, not on load.
- The session token still lives in sessionStorage (closing the tab signs out); moving to httpOnly cookies is a backend change, not done here.
- Not tested with real users or a screen reader; checked by markup tests and screenshots only.
- Uppercase "eyebrow" labels (ECHO · DANISH · …) are kept; tracking was reduced. Sentence case would be calmer still; owner's call.

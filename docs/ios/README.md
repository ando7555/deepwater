# Deepwater on iPhone

Status: **proposal + first build setup**. Nothing here has run on a real iPhone or in the iOS
simulator yet. The `iOS app build` GitHub workflow compiles the Xcode project on a macOS runner;
treat the app as unverified until that workflow is green and someone has opened it on a phone.

## Two routes, one codebase

Both routes reuse the existing React frontend in `frontend/`. No screen is rewritten.

| | 1. Home-screen web app (PWA) | 2. App Store app (Capacitor) |
|---|---|---|
| What the user does | Opens the website in Safari, Share, "Add to Home Screen" | Installs "Deepwater" from the App Store |
| Apple account | Not needed | Apple Developer Program, USD 99 per year |
| Mac with Xcode | Not needed | Needed to sign and upload (or a cloud Mac service) |
| App Store review | None | Yes, see the risks below |
| Updates | Every frontend deploy | Web code ships inside the app: new build and review per release |
| Ready when | After this PR is merged and the frontend is deployed | After the owner's account, signing and review |

Recommendation: ship route 1 now (it costs nothing) and use route 2 when there is a reason to be
in the App Store.

## What this PR adds

- `frontend/public/manifest.webmanifest`, `apple-touch-icon.png`, `app-icons/`: name, icon and
  full-screen mode for "Add to Home Screen".
- `frontend/index.html`: manifest link, Apple home-screen tags, `viewport-fit=cover`.
- `frontend/src/style.css`: body padding from `env(safe-area-inset-*)` so the notch and home bar
  do not cover content. It is zero in a normal browser.
- `frontend/capacitor.config.json` and `frontend/ios/`: the Capacitor 8 Xcode project
  (Swift Package Manager, no CocoaPods), app id `com.deepwater.app`, name "Deepwater",
  icon and launch screen from the Deepwater mark, microphone and speech-recognition texts for the
  speaking practice.
- `frontend/src/platform.js` (+ test): detects the iOS app and hides "Continue with Google" there.
- `backend/.../DeepwaterApplication.java` (+ `IosAppCorsTest`): the API accepts the iOS app's
  origin `capacitor://localhost`.
- `.github/workflows/ios.yml`: unsigned simulator build on `macos-latest`.

### How the iOS app talks to the backend

Inside the app the pages load from `capacitor://localhost`, not from Railway. `npm run ios:build`
therefore bakes the full backend URL into the bundle (`VITE_API_URL`, default
`https://backend-production-c912.up.railway.app`) and copies `dist/` into the Xcode project.
The backend allows that origin through the CORS list, like `localhost:5173` for local development.

In Spring terms: the frontend is a static bundle; Capacitor is a thin native shell
(`WKWebView`) that serves it locally, much like Tomcat serving `static/`, and the API calls go
cross-origin to the Spring Boot backend.

## TestFlight without a Mac (owner has an Apple Developer account and an iPhone, no Mac)

`.github/workflows/ios-testflight.yml` builds, signs and uploads the app on a GitHub macOS runner.
Apple signs it in the cloud with an App Store Connect API key, so no Mac and no certificate files are
needed. Everything below is done in a web browser (Windows PC or iPhone). Never paste the key or ids in chat.

1. **Team ID**: developer.apple.com > Account > Membership details > Team ID (10 characters).
2. **App ID**: developer.apple.com > Certificates, Identifiers & Profiles > Identifiers > + > App IDs > App,
   Bundle ID (explicit) `com.deepwater.app`. If it is taken, pick another (for example
   `com.<yourname>.deepwater`) and add it as the repository *variable* `IOS_BUNDLE_ID`.
3. **App record**: appstoreconnect.apple.com > Apps > + > New App: iOS, a name (must be unique on the
   App Store; "Deepwater" may be taken, so try for example "Deepwater: Learn & Work"), language, the bundle id
   from step 2, SKU `deepwater-ios`, full access.
4. **API key**: App Store Connect > Users and Access > Integrations > App Store Connect API > Team Keys > +,
   access **Admin** (needed for cloud signing). Note the **Key ID** and the **Issuer ID**, and download the
   `.p8` file (Apple lets you download it only once). In Users and Access, make sure your user has
   "Access to Cloud Managed Distribution Certificate" (the account holder normally has it).
5. **GitHub secrets**: github.com/ando7555/deepwater > Settings > Secrets and variables > Actions >
   New repository secret: `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, and `ASC_KEY_P8` (open the
   `.p8` in Notepad and paste the whole text, including the BEGIN/END lines).
6. **Run it**: Actions > iOS TestFlight upload > Run workflow. GitHub shows that button only for workflows
   on `main`, so this works after this PR is merged.
7. **Install**: App Store Connect > your app > TestFlight. The build appears after Apple's processing
   (often 10 to 30 minutes). Add yourself under Internal Testing, install the TestFlight app on the iPhone,
   and open Deepwater from there.

Each run uses the GitHub run number as the build number, so every upload is accepted as a new build.
Not verified yet: no Apple keys exist in this repo, so the signed archive and upload steps have never run.
Signing problems in CI are common on the first try; the run log names the failing step.

## Steps only the owner can do (route 2, with a Mac)

1. Join the Apple Developer Program (USD 99 per year, individual or organisation).
2. Get Xcode 26 or later on a Mac (own Mac, borrowed Mac, or a cloud Mac / CI service).
   Since April 2026, uploads to App Store Connect must be built with the iOS 26 SDK.
3. On the Mac:
   ```bash
   cd frontend
   npm ci
   npm run ios:build      # web build with the Railway API URL, then copy into ios/
   npm run ios:open       # opens Xcode
   ```
   In Xcode: App target > Signing & Capabilities > choose your team. If `com.deepwater.app` is
   taken, change the bundle id there (and `appId` in `capacitor.config.json`).
4. Run on a real iPhone, then Product > Archive > Distribute > App Store Connect > TestFlight.
5. In App Store Connect: privacy policy URL, privacy labels, age rating questionnaire,
   screenshots, review notes with a demo account.

## App Store review risks (checked against the App Store Review Guidelines, 2026-11-15)

- **4.2 Minimum functionality**: an app must offer more than "a repackaged website". Echo's
  practice, cards and speaking exercises are app-like, but a pure web wrapper can still be
  rejected. Adding native value later (notifications for daily review, offline cards) lowers the risk.
- **4.8 Login services**: apps that use Google Sign-In for the main account must also offer an
  equivalent privacy-friendly login (for example Sign in with Apple). The iOS app hides Google
  sign-in and offers email sign-in only. Google sign-in also leaves the app and would return to
  the website, not the app.
- **5.1.1(v) Account deletion**: required. Deepwater already has Delete account on the profile page.
- **Amber**: 1.2 requires filtering, reporting, blocking and a published contact for
  user-generated content; 4.3(b) says new dating apps are accepted only if they offer a
  "meaningfully different or improved experience"; the age rating must be answered honestly.
  Amber has block and report, but who reviews reports is still an open owner decision.
  Leaving Amber out of the first App Store version is the lower-risk option.
- **5.1.2(i) AI**: personal data shared with third-party AI (the Polish B1 examiner, the
  Ask Deepwater assistant) must be disclosed and the user must give explicit permission first.

## Owner choice (2026-11-15): App Store now, with Amber

Added to this PR so the app can pass review with Amber in it:

| Apple rule | What Deepwater now does | Where |
|---|---|---|
| 1.2 filter objectionable material | Amber profile text and messages are checked against a word list (extend it without code changes) | `backend/.../amber/AmberContentFilter.java`, `backend/src/main/resources/amber/blocked-words.txt` |
| 1.2 report + timely response | Reviewer screen in Amber: open reports with what the reported person wrote; Dismiss or Remove from Amber (removal deletes their Amber data and blocks rejoining) | `AmberModeration.java`, `frontend/src/amber/AmberReports.jsx` |
| 1.2 block | Already there (block and report in one step) | Amber |
| 1.2 published contact | "Privacy & contact" link in every footer | `frontend/public/privacy.html` |
| 5.1.1(i) privacy policy in app | Same page; also the URL for App Store Connect: `<frontend URL>/privacy.html` | `frontend/public/privacy.html` |
| 5.1.2(i) explicit permission for third-party AI | Polish B1 hand-in has an unticked box; without it the fixed rubric marks and nothing goes to Anthropic (server-side check too) | `PolishExam.jsx`, `ExamService.submit(..., aiConsent)` |

Still open:
- **Contact email**: `privacy.html` says `CONTACT_EMAIL_TO_BE_SET`. Do not submit until it is a real, monitored address.
- **Reviewer**: for now the teacher account (`DEEPWATER_TEACHER_EMAIL`) reviews Amber reports. Owner to confirm.
- **Ask Deepwater assistant (PR #120)** needs the same AI permission step before it ships in the app.
- The word list is short and English/Danish/Polish only; it is a first filter, reports catch the rest.

## App Store Connect checklist (owner, at submission)

- **Age rating**: answer the questionnaire honestly; Amber is dating for adults, so expect 18+.
- **Privacy labels** (data linked to the user, not used for tracking): contact info (name, email, phone optional),
  user content (messages, CV, profile text, exam answers, audio is not stored), identifiers (user id),
  sensitive info is not collected. No third-party advertising or analytics.
- **Privacy policy URL**: `https://frontend-production-3165.up.railway.app/privacy.html` (after deploy).
- **Review notes**: a demo account (email + password) that already has an Amber profile, a second demo
  account so the reviewer can see an invitation and chat, and a line that Amber is 18+ with block, report
  and human review. Guideline 2.1 requires a working demo account.
- **4.3(b) note**: explain what is different about Amber (no swiping or scores, plain-word reasons,
  invitation first, chat only after both say yes, Echo learning context).

## Known limits

- Not run on a real iPhone or simulator from this environment (Linux). The workflow is the only
  build check.
- The session token lives in `sessionStorage`; in the home-screen web app and the iOS app the
  user may need to sign in again after the app is closed. Changing that is a security decision.
- Google sign-in in the home-screen web app is untested; email sign-in is the safe path.
- Speech recognition inside the iOS app's web view is untested.

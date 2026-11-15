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

## Steps only the owner can do (route 2)

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

## Known limits

- Not run on a real iPhone or simulator from this environment (Linux). The workflow is the only
  build check.
- The session token lives in `sessionStorage`; in the home-screen web app and the iOS app the
  user may need to sign in again after the app is closed. Changing that is a security decision.
- Google sign-in in the home-screen web app is untested; email sign-in is the safe path.
- Speech recognition inside the iOS app's web view is untested.

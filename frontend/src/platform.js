// Where Deepwater is running. Kept as plain functions so they can be tested with node:test.

// True inside the iOS app built with Capacitor. Capacitor's native shell adds window.Capacitor;
// a normal browser, and the home-screen web app, do not have it.
export function isNativeApp(win = typeof window === 'undefined' ? undefined : window) {
  return Boolean(win?.Capacitor?.isNativePlatform?.());
}

// Google sign-in leaves the app for Google's page and returns to the website, not to the iOS app,
// and the App Store asks for an equivalent private login option next to Google (guideline 4.8).
// Until both are handled, the iOS app offers email sign-in only.
export function showGoogleSignIn(win) {
  return !isNativeApp(win);
}

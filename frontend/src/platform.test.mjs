import test from 'node:test';
import assert from 'node:assert/strict';
import { isNativeApp, showGoogleSignIn } from './platform.js';

test('a normal browser is not the native app and shows Google sign-in', () => {
  assert.equal(isNativeApp({}), false);
  assert.equal(showGoogleSignIn({}), true);
});

test('no window at all (tests, server) is not the native app', () => {
  assert.equal(isNativeApp(undefined), false);
});

test('the Capacitor iOS shell is the native app and hides Google sign-in', () => {
  const win = { Capacitor: { isNativePlatform: () => true } };
  assert.equal(isNativeApp(win), true);
  assert.equal(showGoogleSignIn(win), false);
});

test('Capacitor loaded on the web reports not native', () => {
  assert.equal(isNativeApp({ Capacitor: { isNativePlatform: () => false } }), false);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, MESSAGES, errorFromFailure, errorFromResponse, gql, request } from './http.js';

const reply = (status, body, type = 'application/json') => async () => new Response(
  body === undefined ? null : typeof body === 'string' ? body : JSON.stringify(body), { status, headers: { 'Content-Type': type } });

test('4xx keeps the server sentence, because it says what to fix', () => {
  const error = errorFromResponse(400, { detail: 'Use 12–72 characters.' });
  assert.equal(error.message, 'Use 12–72 characters.');
  assert.equal(error.kind, 'client');
  assert.equal(error.retryable, false);
});

test('5xx never shows internals and can be retried', () => {
  const error = errorFromResponse(502, null);
  assert.equal(error.message, MESSAGES.unavailable);
  assert.equal(error.retryable, true);
  assert.equal(errorFromResponse(500, { detail: 'NullPointerException at …' }).message, MESSAGES.unavailable);
});

test('401 means the session ended, unless the call was a sign-in', () => {
  assert.equal(errorFromResponse(401, { detail: 'Sign in to continue.' }).kind, 'session');
  const signIn = errorFromResponse(401, { detail: 'Wrong email or password.' }, { authenticated: false });
  assert.equal(signIn.message, 'Wrong email or password.');
});

test('network failures and timeouts become plain sentences', () => {
  assert.equal(errorFromFailure(new TypeError('Failed to fetch')).message, MESSAGES.offline);
  assert.equal(errorFromFailure(Object.assign(new Error('x'), { name: 'AbortError' })).kind, 'timeout');
  const original = new ApiError('kept');
  assert.equal(errorFromFailure(original), original);
});

test('request: JSON in, JSON out; 204 gives null', async () => {
  let sent;
  const data = await request('/api/v1/x', { method: 'PUT', body: { a: 1 }, fetchImpl: async (url, init) => { sent = { url, init }; return reply(200, { ok: true })(); } });
  assert.deepEqual(data, { ok: true });
  assert.equal(sent.init.headers['Content-Type'], 'application/json');
  assert.equal(sent.init.body, '{"a":1}');
  assert.equal(await request('/x', { fetchImpl: reply(204) }), null);
});

test('request: an HTML error page (Railway 502) is a clear message, not "Unexpected token <"', async () => {
  await assert.rejects(request('/x', { fetchImpl: reply(502, '<html>Bad gateway</html>', 'text/html') }), { message: MESSAGES.unavailable });
  await assert.rejects(request('/x', { fetchImpl: reply(200, '<html>oops</html>', 'text/html') }), { message: MESSAGES.unavailable });
});

test('request: a dropped connection is an offline error', async () => {
  await assert.rejects(request('/x', { fetchImpl: async () => { throw new TypeError('Failed to fetch'); } }), { kind: 'offline' });
});

test('request: gives up after the timeout', async () => {
  const hang = (url, init) => new Promise((resolve, reject) => init.signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' }))));
  await assert.rejects(request('/x', { fetchImpl: hang, timeoutMs: 10 }), { kind: 'timeout' });
});

test('gql: GraphQL errors arrive with status 200 and still throw', async () => {
  await assert.rejects(gql('{ x }', {}, { fetchImpl: reply(200, { errors: [{ message: 'Card not found.' }], data: null }) }), { message: 'Card not found.' });
  assert.deepEqual(await gql('{ x }', {}, { fetchImpl: reply(200, { data: { x: 1 } }) }), { x: 1 });
});

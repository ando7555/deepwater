import { session } from './storage.js';

/*
 * One HTTP client for the whole app. Every screen used to carry its own copy of fetch + token +
 * error handling, and none of them coped with the server being down (Railway's 502 page is HTML,
 * not JSON), a dropped connection ("Failed to fetch") or an expired session. This file does.
 */

const env = typeof import.meta !== 'undefined' && import.meta.env ? import.meta.env : {};
/** Empty VITE_API_URL keeps calls on the frontend origin, where Vite can proxy them. */
export const API = env.VITE_API_URL ?? '';
export const TIMEOUT_MS = 20000;
/** Fired on window when the server says the session is no longer valid; the app signs out. */
export const SESSION_EXPIRED = 'deepwater-session-expired';

export class ApiError extends Error {
  constructor(message, { status = 0, kind = 'server', detail } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    /** offline | timeout | session | server | client */
    this.kind = kind;
    this.detail = detail;
  }
  get retryable() { return this.kind === 'offline' || this.kind === 'timeout' || (this.kind === 'server' && this.status >= 500); }
}

export const MESSAGES = {
  offline: 'We can’t reach Deepwater right now. Check your connection, then try again.',
  timeout: 'Deepwater is taking too long to answer. Please try again in a moment.',
  session: 'Your session has ended. Please sign in again.',
  unavailable: 'Deepwater is having a problem on our side. Your work is safe; please try again in a minute.',
  generic: 'That didn’t work. Please try again.',
};

/**
 * Turns an HTTP status and a parsed body (or null when the body was not JSON) into an ApiError
 * with a sentence a person can act on. Server-written `detail` messages are kept for 4xx, because
 * they say what to fix ("Use 12–72 characters."); 5xx details are internal and never shown.
 */
export function errorFromResponse(status, body, { authenticated = true } = {}) {
  const detail = body?.detail || body?.message || body?.errors?.[0]?.message;
  if (status === 401 && authenticated) return new ApiError(MESSAGES.session, { status, kind: 'session', detail });
  if (status >= 500) return new ApiError(MESSAGES.unavailable, { status, kind: 'server', detail });
  if (detail) return new ApiError(detail, { status, kind: status ? 'client' : 'server', detail });
  return new ApiError(MESSAGES.generic, { status, kind: status >= 400 ? 'client' : 'server' });
}

/** Maps anything fetch can throw to an ApiError. */
export function errorFromFailure(failure) {
  if (failure instanceof ApiError) return failure;
  if (failure?.name === 'AbortError' || failure?.name === 'TimeoutError') return new ApiError(MESSAGES.timeout, { kind: 'timeout' });
  return new ApiError(MESSAGES.offline, { kind: 'offline', detail: failure?.message });
}

async function parse(response) {
  if (response.status === 204) return null;
  const text = await response.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return undefined; }
}

/**
 * request('/api/v1/harbor/profile', { method: 'PUT', body: {...} })
 * `body` is sent as JSON; `form` (FormData) as multipart. `auth: false` for sign-in calls.
 */
export async function request(path, { method = 'GET', body, form, auth = true, fetchImpl = globalThis.fetch, timeoutMs = TIMEOUT_MS } = {}) {
  const token = auth ? session.token() : null;
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const controller = typeof AbortController === 'undefined' ? null : new AbortController();
  const timer = controller ? setTimeout(() => controller.abort(), timeoutMs) : null;
  let response;
  try {
    response = await fetchImpl(`${API}${path}`, {
      method, headers, signal: controller?.signal,
      body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
    });
  } catch (failure) {
    throw errorFromFailure(failure);
  } finally {
    if (timer) clearTimeout(timer);
  }
  const payload = await parse(response);
  if (!response.ok) {
    const error = errorFromResponse(response.status, payload ?? null, { authenticated: Boolean(token) });
    if (error.kind === 'session' && typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(SESSION_EXPIRED));
    throw error;
  }
  if (payload === undefined) throw new ApiError(MESSAGES.unavailable, { status: response.status, kind: 'server' });
  return payload;
}

/** A client for one REST area: const harbor = restClient('/api/v1/harbor'); harbor('/profile'). */
export function restClient(base) {
  return (path = '', options) => request(`${base}${path}`, options);
}

/** GraphQL over the same client. GraphQL reports errors with status 200, so those are checked too. */
export async function gql(query, variables = {}, options = {}) {
  const payload = await request('/graphql', { method: 'POST', body: { query, variables }, ...options });
  if (payload?.errors?.length) {
    const first = payload.errors[0];
    const unauthenticated = /unauthori[sz]ed|sign in to continue|not signed in/i.test(first.message || '') || first.extensions?.classification === 'UNAUTHORIZED';
    if (unauthenticated && session.token()) {
      if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(SESSION_EXPIRED));
      throw new ApiError(MESSAGES.session, { status: 401, kind: 'session', detail: first.message });
    }
    throw new ApiError(first.message || MESSAGES.generic, { status: 200, kind: 'client', detail: first.message });
  }
  return payload?.data;
}

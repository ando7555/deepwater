/**
 * Browser storage that never throws. Private windows, blocked cookies and full quotas make
 * localStorage/sessionStorage throw; the app then keeps working for the visit and simply forgets.
 */

/** Every key the app stores, in one place, so nothing is spelled twice. */
export const KEYS = {
  token: 'deepwater-token',
  user: 'deepwater-user',
  theme: 'deepwater-theme',
  brand: 'deepwater-brand',
  product: 'deepwater-product',
  echoLanguage: 'deepwater-echo-language',
  draft: id => `deepwater-draft:${id}`,
};

function area(kind) {
  try { return kind === 'session' ? globalThis.sessionStorage : globalThis.localStorage; } catch { return undefined; }
}

export function read(key, { kind = 'local', fallback = null } = {}) {
  try { const value = area(kind)?.getItem(key); return value ?? fallback; } catch { return fallback; }
}

export function write(key, value, { kind = 'local' } = {}) {
  try {
    const store = area(kind);
    if (!store) return false;
    if (value === null || value === undefined) store.removeItem(key);
    else store.setItem(key, value);
    return true;
  } catch { return false; }
}

export function readJson(key, { kind = 'local', fallback = null } = {}) {
  const raw = read(key, { kind });
  if (raw === null) return fallback;
  try { return JSON.parse(raw); } catch { return fallback; }
}

export function writeJson(key, value, options) {
  return write(key, value === null || value === undefined ? null : JSON.stringify(value), options);
}

/** A stored value that must be one of `allowed` (ids); anything else gives `fallback`. */
export function readChoice(key, allowed, fallback, options) {
  const value = read(key, options);
  return allowed.includes(value) ? value : fallback;
}

/** The signed-in session lives in sessionStorage: closing the tab signs out. */
export const session = {
  token: () => read(KEYS.token, { kind: 'session' }),
  user: () => readJson(KEYS.user, { kind: 'session' }),
  save(account) { write(KEYS.token, account.token, { kind: 'session' }); writeJson(KEYS.user, account, { kind: 'session' }); },
  saveUser(user) { writeJson(KEYS.user, user, { kind: 'session' }); },
  clear() { write(KEYS.token, null, { kind: 'session' }); write(KEYS.user, null, { kind: 'session' }); },
};

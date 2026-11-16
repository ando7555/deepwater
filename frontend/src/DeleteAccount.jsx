import React, { useState } from 'react';
import { request } from './app/http.js';
import { Notice } from './ui/index.jsx';

/** Permanent account deletion, behind a typed confirmation of the account's email. */
export default function DeleteAccount({ email, onDeleted }) {
  const [confirmEmail, setConfirmEmail] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function remove(event) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      await request('/api/v1/auth/account', { method: 'DELETE', body: { confirmEmail } });
      onDeleted();
    } catch (deleteError) { setError(deleteError.message); } finally { setBusy(false); }
  }

  return <details className="delete-account">
    <summary>Delete my account</summary>
    <form onSubmit={remove}>
      <p>This permanently deletes your account, learning profile, answers and progress. It cannot be undone.</p>
      <label>Type <b>{email}</b> to confirm<input required type="email" autoComplete="off" value={confirmEmail} onChange={event => setConfirmEmail(event.target.value)} /></label>
      <button disabled={busy || confirmEmail.trim().toLowerCase() !== email.toLowerCase()}>{busy ? 'Deleting…' : 'Delete permanently'}</button>
      {error && <Notice tone="error">{error}</Notice>}
    </form>
  </details>;
}

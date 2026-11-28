import React, { useEffect, useState } from 'react';
import { API, request } from '../app/http.js';
import { session } from '../app/storage.js';
import { Button, Notice, Panel, SectionHeading } from '../ui/index.jsx';
import { visibilitySummary } from './profileTools.js';

/** Account management: who sees what, a copy of your data, and signing out of every device. Deletion sits below it. */
export default function AccountSettings({ onSignedOut }) {
  const [amber, setAmber] = useState(undefined);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [asking, setAsking] = useState(false);

  useEffect(() => { request('/api/v1/amber/profile').then(setAmber).catch(() => setAmber(null)); }, []);

  const download = async () => {
    setBusy('export'); setError('');
    try {
      const token = session.token();
      const response = await fetch(`${API}/api/v1/account/export`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error('Your data could not be prepared. Please try again.');
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement('a');
      link.href = url;
      link.download = `deepwater-data-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) { setError(e.message); } finally { setBusy(''); }
  };

  const signOutEverywhere = async () => {
    setBusy('devices'); setError('');
    try { await request('/api/v1/account/sign-out-everywhere', { method: 'POST' }); onSignedOut(); }
    catch (e) { setError(e.message); setBusy(''); }
  };

  return <Panel className="account-settings" aria-labelledby="account-title">
    <SectionHeading id="account-title" eyebrow="ACCOUNT" title="Privacy and account"
      lead="One Deepwater account and one profile for Echo, Harbor and Amber. Here is what each product shows to other people." />
    <ul className="visibility-list">
      {amber !== undefined && visibilitySummary(amber).map(line => <li key={line.product}><b>{line.product}</b><span>{line.text}</span></li>)}
    </ul>
    <div className="account-actions">
      <div>
        <b>Download your data</b>
        <p>A file with everything Deepwater keeps about you: profile, languages, Echo progress, Harbor profile and applications, practice exams, and what you wrote in Amber.</p>
        <Button variant="quiet" disabled={busy === 'export'} onClick={download}>{busy === 'export' ? 'Preparing…' : 'Download my data'}</Button>
      </div>
      <div>
        <b>Sign out everywhere</b>
        <p>Ends every session on every phone and computer, including this one. Use it if you lost a device or shared a password.</p>
        {!asking && <Button variant="quiet" onClick={() => setAsking(true)}>Sign out on all devices</Button>}
        {asking && <div className="amber-row">
          <Button variant="danger" disabled={busy === 'devices'} onClick={signOutEverywhere}>Sign out everywhere now</Button>
          <Button variant="link" onClick={() => setAsking(false)}>Cancel</Button>
        </div>}
      </div>
    </div>
    {error && <Notice tone="error">{error}</Notice>}
  </Panel>;
}

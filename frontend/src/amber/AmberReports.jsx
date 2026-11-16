import React, { useState } from 'react';
import { formatDate } from '../api.js';
import { restClient } from '../app/http.js';
import { Button, EmptyState, Notice, Panel, Pill, SectionHeading } from '../ui/index.jsx';
import { REPORT_REASONS } from './amberTools.js';

// The reviewer's screen for Amber reports (only the teacher account sees it for now).
// Each report shows what the reported person wrote; the reviewer dismisses it or removes them from Amber.

const moderation = restClient('/api/v1/amber/moderation/reports');

const reasonLabel = id => REPORT_REASONS.find(reason => reason.id === id)?.label || id;

export default function AmberReports() {
  const [reports, setReports] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function load() {
    setError('');
    try { setReports(await moderation()); } catch (e) { setError(e.message); }
  }

  async function decide(id, action) {
    setBusy(true); setError('');
    try { await moderation(`/${encodeURIComponent(id)}`, { method: 'POST', body: { action } }); await load(); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  return <Panel className="amber-reports">
    <SectionHeading eyebrow="AMBER · REVIEWER" title={reports ? `Reports · ${reports.length} open` : 'Reports'}
      lead="Read what was reported and decide. Removing someone deletes their Amber profile and chats and stops them rejoining; their Deepwater account stays. They are never told who reported them."
      action={<Button variant="quiet" onClick={load}>{reports ? 'Refresh' : 'Open reports'}</Button>} />
    {error && <Notice tone="error">{error}</Notice>}
    {reports && !reports.length && <EmptyState title="No open reports.">New reports appear here.</EmptyState>}
    {reports?.map(report => <article className="queue-item" key={report.id}>
      <div className="queue-item-head">
        <div><b>{report.displayName || 'Already left Amber'}</b>
          <span className="queue-meta">{report.area ? `${report.area} · ` : ''}reported {formatDate(report.createdAt)}{report.timesReported > 1 ? ` · reported ${report.timesReported} times in total` : ''}</span></div>
        <div className="queue-pills"><Pill tone="warn">{reasonLabel(report.reason)}</Pill></div>
      </div>
      {report.note && <p><b>Reporter's note.</b> {report.note}</p>}
      {report.about && <p><b>Their intro.</b> {report.about}</p>}
      {report.interests && <p><b>Their interests.</b> {report.interests.split(',').join(', ')}</p>}
      <details className="queue-detail-toggle">
        <summary>Their messages to the reporter ({report.messages.length})</summary>
        <div className="queue-detail">{report.messages.length
          ? report.messages.map((message, index) => <p key={index}>{message}</p>)
          : <p>No messages.</p>}</div>
      </details>
      <div className="queue-actions">
        <Button variant="quiet" disabled={busy} onClick={() => decide(report.id, 'DISMISS')}>Dismiss report</Button>
        <Button disabled={busy} onClick={() => decide(report.id, 'REMOVE')}>Remove from Amber</Button>
      </div>
    </article>)}
  </Panel>;
}

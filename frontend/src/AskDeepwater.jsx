import { useEffect, useRef, useState } from 'react';
import { API } from './api.js';
import { applyEvent, historyForServer, labelsFromStatus, parseSse } from './assistantTools.js';
import { Button, Notice } from './ui/index.jsx';

/** "Ask Deepwater": a chat panel that streams the assistant's steps and answer from /api/v1/assistant/runs. */
export function openAskDeepwater() {
  window.dispatchEvent(new Event('deepwater-ask-open'));
}

export default function AskDeepwater() {
  const [open, setOpen] = useState(false);
  const [chat, setChat] = useState([]);
  const [question, setQuestion] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  const [agree, setAgree] = useState(false);
  const threadId = useRef(crypto.randomUUID());
  const end = useRef(null);
  const token = sessionStorage.getItem('deepwater-token');

  useEffect(() => {
    if (!open || status !== null) return;
    fetch(`${API}/api/v1/assistant`, { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.ok ? r.json() : { enabled: false }).then(setStatus).catch(() => setStatus({ enabled: false }));
  }, [open, status, token]);
  const labels = labelsFromStatus(status);
  const maxChars = status?.maxMessageChars || 2000;
  // A hosted AI company sees the questions and the data the tools read, so the person agrees first (stored on the server).
  const needsConsent = Boolean(status?.sharesDataWith) && !status?.consentGiven;

  async function setConsent(given) {
    setError('');
    const response = await fetch(`${API}/api/v1/assistant/consent`, {
      method: given ? 'PUT' : 'DELETE', headers: { Authorization: `Bearer ${token}` },
    }).catch(() => null);
    if (!response?.ok) { setError('Could not save your choice. Please try again.'); return; }
    setAgree(false);
    setStatus(current => ({ ...current, consentGiven: given }));
  }
  useEffect(() => { end.current?.scrollIntoView?.({ block: 'end' }); }, [chat]);
  // The header's "Ask AI" button (and anything else) opens the panel with this event.
  useEffect(() => {
    const show = () => setOpen(true);
    window.addEventListener('deepwater-ask-open', show);
    return () => window.removeEventListener('deepwater-ask-open', show);
  }, []);

  async function ask(text) {
    const content = text.trim();
    if (!content || busy) return;
    setError(''); setQuestion(''); setBusy(true);
    const next = [...chat, { id: crypto.randomUUID(), role: 'user', content }];
    setChat(next);
    try {
      const response = await fetch(`${API}/api/v1/assistant/runs`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ threadId: threadId.current, messages: historyForServer(next, { maxChars }) }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.detail || 'The assistant could not answer just now.');
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        const parsed = parseSse(buffer + decoder.decode(value, { stream: true }));
        buffer = parsed.rest;
        parsed.events.forEach(event => setChat(current => applyEvent(current, event, labels)));
      }
    } catch (askError) { setError(askError.message); } finally { setBusy(false); }
  }

  if (!open) return <button type="button" className="ask-launcher" onClick={() => setOpen(true)} aria-label="Ask Deepwater">
    <span aria-hidden="true">✦</span> Ask Deepwater</button>;

  return <aside className="ask-panel panel" aria-label="Ask Deepwater">
    <header className="ask-head">
      <div><p className="eyebrow">AI ASSISTANT</p><h2>Ask Deepwater</h2></div>
      <Button variant="quiet" onClick={() => setOpen(false)} aria-label="Close the assistant">Close</Button>
    </header>
    {status?.enabled === false && <Notice>The AI is switched off on this server for now. You can still try it: it will tell you so.</Notice>}
    <p className="ask-privacy">It reads only your own Echo, Harbor and Amber data, and never other Amber members.</p>
    {needsConsent ? <div className="ask-consent">
      <p>To answer, Ask Deepwater sends your questions and the Deepwater data it looks up for you (your cards, jobs,
        applications, exam results and Amber profile) to <strong>{status.sharesDataWith}</strong>, the company that runs the AI.
        They use it only to answer you. You can stop this at any time here.</p>
      <label className="ask-consent-check">
        <input type="checkbox" checked={agree} onChange={event => setAgree(event.target.checked)} />
        Send my questions and my Deepwater data to {status.sharesDataWith}
      </label>
      <Button disabled={!agree} onClick={() => setConsent(true)}>Allow and continue</Button>
      <a className="ask-consent-link" href="/privacy.html" target="_blank" rel="noreferrer">Privacy policy</a>
      {error && <Notice tone="error">{error}</Notice>}
    </div> : <>
    <div className="ask-log" aria-live="polite">
      {chat.length === 0 && <div className="ask-suggestions">
        {(status?.suggestions || []).map(s => <button key={s} type="button" className="ask-suggestion" onClick={() => ask(s)}>{s}</button>)}
      </div>}
      {chat.map(m => m.role === 'tool'
        ? <p key={m.id} className={`ask-step ${m.state}`}>{m.state === 'running' ? '…' : m.state === 'error' ? '!' : '✓'} {m.label}{m.detail ? `: ${m.detail}` : ''}</p>
        : <p key={m.id} className={`ask-msg ${m.role}`}>{m.content}</p>)}
      {busy && <p className="ask-step running" role="status">Thinking…</p>}
      <span ref={end} />
    </div>
    {error && <Notice tone="error">{error}</Notice>}
    <form className="ask-form" onSubmit={event => { event.preventDefault(); ask(question); }}>
      <label className="sr-only" htmlFor="ask-input">Your question</label>
      <input id="ask-input" value={question} maxLength={maxChars} placeholder="Ask about your cards, jobs or exam…"
        onChange={event => setQuestion(event.target.value)} disabled={busy} />
      <Button type="submit" disabled={busy || !question.trim()}>Ask</Button>
    </form>
    {status?.sharesDataWith && <button type="button" className="ask-consent-link" onClick={() => setConsent(false)}>
      Stop sending my data to {status.sharesDataWith}</button>}
    </>}
  </aside>;
}

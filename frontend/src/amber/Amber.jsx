import React, { useCallback, useEffect, useRef, useState } from 'react';
import { API } from '../api.js';
import { Button, Choice, EmptyState, Field, FormGrid, Notice, Panel, Pill, ProblemList, SectionHeading, Tabs } from '../ui/index.jsx';
import { INTENTS, REPORT_REASONS, addInterest, initial, intentWords, isShared, profileProblems } from './amberTools.js';

// Amber: connection by choice. Suggestions explain themselves, and a conversation opens only when both say yes.

async function amber(path, { method = 'GET', body } = {}) {
  const token = sessionStorage.getItem('deepwater-token');
  const headers = { Authorization: `Bearer ${token}` };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${API}/api/v1/amber${path}`, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.detail || 'The request could not be completed.');
    error.status = response.status;
    throw error;
  }
  return payload;
}

export default function Amber() {
  const [profile, setProfile] = useState(null);
  const [view, setView] = useState('discover');
  const [incoming, setIncoming] = useState(0);
  const [error, setError] = useState('');

  const refreshCount = useCallback(() => {
    amber('/invitations').then(list => setIncoming(list.incoming.length)).catch(() => {});
  }, []);

  useEffect(() => { amber('/profile').then(setProfile).catch(e => setError(e.message)); }, []);
  useEffect(() => { if (profile?.joined) refreshCount(); }, [profile?.joined, view, refreshCount]);

  if (error && !profile) return <Notice tone="error">{error}</Notice>;
  if (!profile) return <p role="status" className="loading">Loading Amber…</p>;

  if (!profile.joined) return <div className="amber-app">
    <AmberIntro />
    <ProfileForm profile={profile} onSaved={next => { setProfile(next); setView('discover'); }} />
  </div>;

  return <div className="amber-app">
    <section className="welcome amber-welcome">
      <p className="eyebrow">AMBER · CONNECTION, BY CHOICE</p>
      <h1>Hi {profile.displayName}. <span className="amber-accent">Nothing opens until you both say yes.</span></h1>
    </section>
    <Tabs label="Amber sections" value={view} onChange={setView} items={[
      { id: 'discover', label: 'Discover' },
      { id: 'invitations', label: 'Invitations', badge: incoming || undefined },
      { id: 'connections', label: 'Conversations' },
      { id: 'profile', label: 'My profile' },
    ]} />
    {view === 'discover' && <Discover profile={profile} goToProfile={() => setView('profile')} />}
    {view === 'invitations' && <Invitations onChanged={refreshCount} openConversations={() => setView('connections')} />}
    {view === 'connections' && <Conversations />}
    {view === 'profile' && <ProfileForm profile={profile} onSaved={setProfile} onLeft={() => amber('/profile').then(setProfile)} />}
  </div>;
}

function AmberIntro() {
  return <section className="welcome amber-welcome">
    <p className="eyebrow">AMBER · BY DEEPWATER</p>
    <h1>Connection, <span className="amber-accent">by choice</span>.</h1>
    <p className="intro">Every suggestion tells you why it appears, and a conversation opens only when both people say yes. Your Deepwater account is not on Amber until you create a profile here.</p>
    <ul className="amber-principles">
      <li><b>Nothing hidden</b><span>Every suggestion shows the shared choices behind it. No scores, no hidden ranking.</span></li>
      <li><b>Both say yes</b><span>No messages until both people accept the same invitation.</span></li>
      <li><b>Leave anytime</b><span>Pass, hide, block or report in one step, with no penalty.</span></li>
      <li><b>Echo stays in Echo</b><span>Your lessons are never shared. "Learning Danish" shows only if you switch it on.</span></li>
    </ul>
  </section>;
}

// ------------------------------------------------------------------ profile

function ProfileForm({ profile, onSaved, onLeft }) {
  const joined = profile.joined;
  const [form, setForm] = useState({
    displayName: profile.displayName ?? '', age: profile.age ?? '', confirmAdult: joined, area: profile.area ?? '',
    intents: profile.intents ?? [], interests: profile.interests ?? [], about: profile.about ?? '',
    visible: profile.visible ?? true, shareLearning: profile.shareLearning ?? false,
  });
  const [typed, setTyped] = useState('');
  const [problem, setProblem] = useState('');
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const set = (key, value) => setForm(current => ({ ...current, [key]: value }));
  const problems = profileProblems(form);

  const add = () => {
    const next = addInterest(form.interests, typed);
    setProblem(next.problem);
    if (!next.problem) { set('interests', next.list); setTyped(''); }
  };

  const save = async event => {
    event.preventDefault();
    setTried(true); setMessage(''); setError('');
    if (problems.length) return;
    setBusy(true);
    try {
      const saved = await amber('/profile', { method: 'PUT', body: { ...form, age: Number(form.age) } });
      setMessage(joined ? 'Saved.' : '');
      onSaved(saved);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };

  return <Panel as="form" className="amber-card" onSubmit={save} noValidate>
    <SectionHeading eyebrow={joined ? 'What you share' : 'Step 1 of 1'} title={joined ? 'Your Amber profile' : 'Create your Amber profile'}
      lead="Amber uses only what is on this form to make suggestions. Other people see your first name, age, area, what you are looking for, your interests and your intro." />
    <FormGrid>
      <Field label="First name" maxLength={40} value={form.displayName} onChange={e => set('displayName', e.target.value)} autoComplete="given-name" />
      <Field label="Age" type="number" min={18} max={120} inputMode="numeric" value={form.age} onChange={e => set('age', e.target.value)} />
      <Field label="Area" hint="City level only, for example Copenhagen. Never your address." maxLength={80} value={form.area} onChange={e => set('area', e.target.value)} />
      <div className="ui-field">
        <span className="ui-label">Looking for</span>
        <div className="amber-intents">
          {INTENTS.map(intent => <Choice key={intent.id} checked={form.intents.includes(intent.id)}
            onChange={e => set('intents', e.target.checked ? [...form.intents, intent.id] : form.intents.filter(id => id !== intent.id))}>{intent.label}</Choice>)}
        </div>
      </div>
      <div className="ui-field wide">
        <span className="ui-label">Interests <small className="optional"> · up to 12</small></span>
        {form.interests.length > 0 && <div className="harbor-chips">
          {form.interests.map(item => <span key={item} className="harbor-chip">{item}<button type="button" aria-label={`Remove ${item}`}
            onClick={() => set('interests', form.interests.filter(other => other !== item))}>×</button></span>)}
        </div>}
        <div className="amber-add">
          <input aria-label="Add an interest" placeholder="Hiking, jazz, cooking…" value={typed} maxLength={30}
            onChange={e => setTyped(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); add(); } }} />
          <Button variant="quiet" onClick={add}>Add</Button>
        </div>
        {problem && <small className="amber-problem">{problem}</small>}
      </div>
      <Field label="Short intro" optional as="textarea" wide rows={3} maxLength={300} value={form.about} onChange={e => set('about', e.target.value)}
        hint={`${form.about.length}/300`} />
    </FormGrid>

    <div className="amber-switches">
      <Toggle checked={form.visible} onChange={value => set('visible', value)} title="Visible in discovery"
        detail="Turn off to pause. While you are hidden, suggestions pause for you too." />
      <Toggle checked={form.shareLearning} disabled={!profile.echoLearning} onChange={value => set('shareLearning', value)}
        title={profile.echoLearning ? `Show "learning ${profile.echoLearning}" from Echo` : 'Show what you learn in Echo'}
        detail={profile.echoLearning ? 'Off by default. Only the language name is shared. Lesson answers are never shared.' : 'You have no Echo course yet, so there is nothing to show.'} />
    </div>

    {!joined && <Choice checked={form.confirmAdult} onChange={e => set('confirmAdult', e.target.checked)}>I am 18 or older.</Choice>}
    {tried && <ProblemList problems={problems} />}
    {error && <Notice tone="error">{error}</Notice>}
    {message && <Notice tone="good">{message}</Notice>}
    <div className="amber-row">
      <Button type="submit" disabled={busy}>{busy ? 'Saving…' : joined ? 'Save profile' : 'Join Amber'}</Button>
    </div>
    {joined && <LeaveAmber onLeft={onLeft} />}
  </Panel>;
}

function Toggle({ checked, onChange, title, detail, disabled }) {
  return <label className={`amber-toggle${disabled ? ' disabled' : ''}`}>
    <span><b>{title}</b><small>{detail}</small></span>
    <input type="checkbox" role="switch" checked={checked} disabled={disabled} onChange={e => onChange(e.target.checked)} />
  </label>;
}

function LeaveAmber({ onLeft }) {
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState('');
  const leave = async () => {
    try { await amber('/profile', { method: 'DELETE' }); onLeft(); } catch (e) { setError(e.message); }
  };
  return <div className="amber-leave">
    {!asking && <Button variant="link" onClick={() => setAsking(true)}>Leave Amber</Button>}
    {asking && <>
      <p>Leaving deletes your Amber profile, invitations and conversations. Your Deepwater account, Echo and Harbor stay.</p>
      <div className="amber-row"><Button variant="danger" onClick={leave}>Leave Amber</Button><Button variant="quiet" onClick={() => setAsking(false)}>Stay</Button></div>
    </>}
    {error && <Notice tone="error">{error}</Notice>}
  </div>;
}

// ------------------------------------------------------------------ discover

function Discover({ profile, goToProfile }) {
  const [list, setList] = useState(null);
  const [paused, setPaused] = useState('');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [reporting, setReporting] = useState(null);

  const load = useCallback(() => {
    amber('/suggestions').then(next => { setList(next); setPaused(''); })
      .catch(e => { if (e.status === 409) setPaused(e.message); else setError(e.message); });
  }, []);
  useEffect(load, [load, profile.visible]);

  const act = async (person, action) => {
    setError(''); setNotice('');
    try {
      if (action === 'invite') {
        await amber('/invitations', { method: 'POST', body: { memberId: person.memberId } });
        setNotice(`Invitation sent to ${person.displayName}. If ${person.displayName} accepts, a conversation opens for both of you.`);
      } else {
        await amber(`/suggestions/${person.memberId}/${action}`, { method: 'POST' });
        setNotice(action === 'pass' ? `${person.displayName} may come back in 30 days.` : `You will not see ${person.displayName} again.`);
      }
      setList(current => current.filter(item => item.person.memberId !== person.memberId));
    } catch (e) { setError(e.message); }
  };

  if (paused) return <EmptyState title="Suggestions are paused" action={<Button variant="quiet" onClick={goToProfile}>Open my profile</Button>}>{paused}</EmptyState>;
  if (error && !list) return <Notice tone="error">{error}</Notice>;
  if (!list) return <p role="status" className="loading">Finding people…</p>;

  return <div className="amber-stack">
    {notice && <Notice tone="good">{notice}</Notice>}
    {error && <Notice tone="error">{error}</Notice>}
    {list.length === 0 && <EmptyState title="No suggestions right now">Amber only suggests people who are looking for the same kind of connection as you. Adding interests or changing what you are looking for can widen it.</EmptyState>}
    {list.map(item => <Panel as="article" key={item.person.memberId} className="amber-card amber-person-card">
      <PersonHeader person={item.person} />
      <div className="harbor-chips">
        {item.person.interests.map(interest => <span key={interest} className={`amber-chip${isShared(interest, item.sharedInterests) ? ' match' : ''}`}>{interest}</span>)}
      </div>
      {item.person.about && <p className="amber-about">{item.person.about}</p>}
      <div className="amber-why">
        <b>Why you are seeing {item.person.displayName}</b>
        <ul>{item.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>
      </div>
      <div className="amber-actions">
        <Button variant="quiet" onClick={() => act(item.person, 'pass')}>Pass</Button>
        <Button variant="quiet" onClick={() => act(item.person, 'hide')}>Hide</Button>
        <Button onClick={() => act(item.person, 'invite')}>Send invite</Button>
      </div>
      <p className="amber-note">Passing does not lower anyone's visibility, and {item.person.displayName} is never told. <button type="button" className="link" onClick={() => setReporting(item.person)}>Block or report</button></p>
      {reporting?.memberId === item.person.memberId && <SafetySheet person={item.person} onClose={done => { setReporting(null); if (done) setList(current => current.filter(other => other.person.memberId !== item.person.memberId)); }} />}
    </Panel>)}
  </div>;
}

function PersonHeader({ person }) {
  return <div className="amber-person">
    <span className="amber-avatar" aria-hidden="true">{initial(person.displayName)}</span>
    <div>
      <h3>{person.displayName}, {person.age}</h3>
      <p>{person.area} · {intentWords(person.intents)}{person.learning ? ` · Learning ${person.learning}` : ''}</p>
    </div>
  </div>;
}

// ------------------------------------------------------------------ invitations

function Invitations({ onChanged, openConversations }) {
  const [data, setData] = useState(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const load = useCallback(() => { amber('/invitations').then(setData).catch(e => setError(e.message)); }, []);
  useEffect(load, [load]);

  const act = async (invitation, action) => {
    setError(''); setNotice('');
    try {
      await amber(`/invitations/${invitation.id}/${action}`, { method: 'POST' });
      setNotice(action === 'accept' ? `You both said yes. You can now message ${invitation.person.displayName}.`
        : action === 'decline' ? `Declined. ${invitation.person.displayName} sees only that the invitation is closed, never a reason.`
          : 'Invitation withdrawn.');
      load(); onChanged();
    } catch (e) { setError(e.message); }
  };

  if (error && !data) return <Notice tone="error">{error}</Notice>;
  if (!data) return <p role="status" className="loading">Loading invitations…</p>;

  return <div className="amber-stack">
    {notice && <Notice tone="good">{notice} {notice.startsWith('You both') && <button type="button" className="link" onClick={openConversations}>Open conversations</button>}</Notice>}
    {error && <Notice tone="error">{error}</Notice>}
    <SectionHeading level={2} title="Invited you" lead="Nothing opens until you accept. Declining is private." />
    {data.incoming.length === 0 && <EmptyState title="No invitations waiting" />}
    {data.incoming.map(invitation => <Panel as="article" key={invitation.id} className="amber-card">
      <PersonHeader person={invitation.person} />
      {invitation.person.about && <p className="amber-about">{invitation.person.about}</p>}
      {invitation.reasons.length > 0 && <div className="amber-why"><b>What you share</b><ul>{invitation.reasons.map(r => <li key={r}>{r}</li>)}</ul></div>}
      <div className="amber-actions two">
        <Button variant="quiet" onClick={() => act(invitation, 'decline')}>Decline</Button>
        <Button onClick={() => act(invitation, 'accept')}>Accept</Button>
      </div>
    </Panel>)}
    <SectionHeading level={2} title="You invited" />
    {data.outgoing.length === 0 && <EmptyState title="No invitations sent" />}
    {data.outgoing.map(invitation => <Panel as="article" key={invitation.id} className="amber-card amber-sent">
      <PersonHeader person={invitation.person} />
      {invitation.status === 'PENDING'
        ? <div className="amber-row"><Pill tone="soft">Waiting for an answer</Pill><Button variant="link" onClick={() => act(invitation, 'withdraw')}>Withdraw</Button></div>
        : <div className="amber-row"><Pill tone="muted">Closed</Pill><small>This invitation will not open a conversation.</small></div>}
    </Panel>)}
  </div>;
}

// ------------------------------------------------------------------ conversations

function Conversations() {
  const [list, setList] = useState(null);
  const [open, setOpen] = useState(null);
  const [error, setError] = useState('');
  const load = useCallback(() => { amber('/connections').then(setList).catch(e => setError(e.message)); }, []);
  useEffect(load, [load]);

  if (error && !list) return <Notice tone="error">{error}</Notice>;
  if (!list) return <p role="status" className="loading">Loading conversations…</p>;
  if (open) return <Chat connection={open} onBack={() => { setOpen(null); load(); }} />;

  return <div className="amber-stack">
    {list.length === 0 && <EmptyState title="No conversations yet">A conversation opens when someone accepts your invitation, or you accept theirs.</EmptyState>}
    {list.map(connection => <button type="button" key={connection.id} className="amber-thread" onClick={() => setOpen(connection)}>
      <PersonHeader person={connection.person} />
      <span className="amber-open">Open</span>
    </button>)}
  </div>;
}

function Chat({ connection, onBack }) {
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const [closed, setClosed] = useState(false);
  const [error, setError] = useState('');
  const [safety, setSafety] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const end = useRef(null);
  const name = connection.person.displayName;

  const load = useCallback(() => {
    amber(`/connections/${connection.id}/messages`).then(setMessages)
      .catch(e => { if (e.status === 404) setClosed(true); else setError(e.message); });
  }, [connection.id]);
  useEffect(() => {
    load();
    // No live socket in this first version: the conversation refreshes every 8 seconds while it is open.
    const timer = setInterval(load, 8000);
    return () => clearInterval(timer);
  }, [load]);
  useEffect(() => { end.current?.scrollIntoView?.({ block: 'nearest' }); }, [messages.length]);

  const send = async event => {
    event.preventDefault();
    if (!text.trim()) return;
    setError('');
    try {
      const sent = await amber(`/connections/${connection.id}/messages`, { method: 'POST', body: { body: text } });
      setMessages(current => [...current, sent]); setText('');
    } catch (e) { if (e.status === 404) setClosed(true); else setError(e.message); }
  };

  const leave = async () => {
    try { await amber(`/connections/${connection.id}/leave`, { method: 'POST' }); onBack(); } catch (e) { setError(e.message); }
  };

  return <Panel className="amber-card amber-chat">
    <div className="amber-row spread">
      <Button variant="link" onClick={onBack}>← Conversations</Button>
      <div className="amber-row">
        <Button variant="quiet" onClick={() => setLeaving(true)}>Leave</Button>
        <Button variant="quiet" onClick={() => setSafety(true)}>Block or report</Button>
      </div>
    </div>
    <PersonHeader person={connection.person} />
    {leaving && <div className="amber-why">
      <b>Leave this conversation?</b>
      <p>It closes for both of you, and neither of you can write again. {name} is not told why.</p>
      <div className="amber-row"><Button variant="danger" onClick={leave}>Leave conversation</Button><Button variant="quiet" onClick={() => setLeaving(false)}>Stay</Button></div>
    </div>}
    {safety && <SafetySheet person={connection.person} onClose={done => { setSafety(false); if (done) setClosed(true); }} />}
    <div className="amber-messages" aria-live="polite">
      <p className="amber-note center">You both said yes. Either of you can leave or block at any time.</p>
      {messages.map(message => <p key={message.id} className={`amber-bubble ${message.mine ? 'me' : 'them'}`}>{message.body}</p>)}
      <span ref={end} />
    </div>
    {closed
      ? <Notice>This conversation is closed.</Notice>
      : <form className="amber-compose" onSubmit={send}>
        <input aria-label={`Message ${name}`} placeholder={`Message ${name}`} value={text} maxLength={1000} onChange={e => setText(e.target.value)} />
        <Button type="submit" disabled={!text.trim()}>Send</Button>
      </form>}
    {error && <Notice tone="error">{error}</Notice>}
  </Panel>;
}

// ------------------------------------------------------------------ safety

function SafetySheet({ person, onClose }) {
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [done, setDone] = useState('');
  const [error, setError] = useState('');

  const submit = async report => {
    setError('');
    try {
      if (report) await amber('/safety/report', { method: 'POST', body: { memberId: person.memberId, reason, note } });
      else await amber('/safety/block', { method: 'POST', body: { memberId: person.memberId } });
      setDone(report
        ? `Done. ${person.displayName} can no longer see or contact you, and will not know you reported them. Your report is saved for review.`
        : `Done. ${person.displayName} can no longer see or contact you, and is not told.`);
    } catch (e) { setError(e.message); }
  };

  if (done) return <div className="amber-sheet" role="status"><b>{done}</b><Button variant="quiet" onClick={() => onClose(true)}>Close</Button></div>;

  return <div className="amber-sheet">
    <h3>Block or report {person.displayName}</h3>
    <p>Blocking hides you from each other and ends any invitation or conversation. A report also blocks.</p>
    <fieldset className="amber-reasons">
      <legend>Report a reason (optional for blocking)</legend>
      {REPORT_REASONS.map(option => <Choice key={option.id} type="radio" name={`reason-${person.memberId}`} checked={reason === option.id}
        onChange={() => setReason(option.id)}>{option.label}</Choice>)}
    </fieldset>
    {reason && <Field label="What happened?" optional as="textarea" rows={2} maxLength={1000} value={note} onChange={e => setNote(e.target.value)} />}
    {error && <Notice tone="error">{error}</Notice>}
    <div className="amber-row">
      <Button variant="danger" disabled={!reason} onClick={() => submit(true)}>Block and report</Button>
      <Button variant="quiet" onClick={() => submit(false)}>Block only</Button>
      <Button variant="link" onClick={() => onClose(false)}>Cancel</Button>
    </div>
  </div>;
}

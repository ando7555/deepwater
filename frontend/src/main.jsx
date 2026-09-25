import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

const GRAPHQL = 'http://127.0.0.1:8081/graphql';
async function gql(query, variables = {}, token = sessionStorage.getItem('deepwater-token')) {
  const response = await fetch(GRAPHQL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ query, variables }),
  });
  const payload = await response.json();
  if (!response.ok || payload.errors?.length) throw new Error(payload.errors?.[0]?.message || 'The request could not be completed.');
  return payload.data;
}

function Auth({ onLogin }) {
  const [mode, setMode] = useState('register');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      if (mode === 'register') {
        await fetch('http://127.0.0.1:8081/api/v1/auth/register', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name, email, password }),
        }).then(async (r) => { const body = await r.json(); if (!r.ok) throw new Error(body.detail || body.message || 'Registration failed.'); onLogin(body); });
      } else {
        const response = await fetch('http://127.0.0.1:8081/api/v1/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });
        const body = await response.json();
        if (!response.ok) throw new Error(body.detail || body.message || 'Sign in failed.');
        onLogin(body);
      }
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  return <section className="access"><img className="logo hero-logo" src="/deepwater-logo-warm.png" alt="Deepwater" />
    <p className="eyebrow">THROUGH EXPRESSION, CONNECT THE WORLD</p><h1>Learn. Express. Connect.</h1>
    <p>Echo helps you learn languages through meaningful situations. Amber gives people room to meet through mutual interest.</p>
    <form onSubmit={submit}>
      {mode === 'register' && <label>Your name<input required maxLength="160" autoComplete="name" value={name} onChange={e => setName(e.target.value)} /></label>}
      <label>Email<input required type="email" autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} /></label>
      <label>Password<input required type="password" minLength="12" maxLength="72" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} value={password} onChange={e => setPassword(e.target.value)} /><small>Use 12–72 characters.</small></label>
      <button disabled={busy}>{busy ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Sign in'}</button>
    </form>
    {error && <p role="alert" className="error">{error}</p>}
    <button className="link" onClick={() => { setError(''); setMode(mode === 'register' ? 'login' : 'register'); }}>{mode === 'register' ? 'Already have an account? Sign in' : 'Create an account'}</button>
  </section>;
}

function App() {
  const [user, setUser] = useState(() => { try { return JSON.parse(sessionStorage.getItem('deepwater-user')); } catch { return null; } });
  const [tab, setTab] = useState('echo');
  const [cases, setCases] = useState([]); const [selected, setSelected] = useState(null);
  const [answer, setAnswer] = useState(''); const [feedback, setFeedback] = useState(null);
  const [profiles, setProfiles] = useState([]); const [invitations, setInvitations] = useState([]);
  const [hasProfile, setHasProfile] = useState(false); const [city, setCity] = useState('');
  const [statement, setStatement] = useState(''); const [notice, setNotice] = useState('');
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);

  function login(account) {
    sessionStorage.setItem('deepwater-token', account.token);
    sessionStorage.setItem('deepwater-user', JSON.stringify(account)); setUser(account);
  }
  function logout() {
    sessionStorage.removeItem('deepwater-token'); sessionStorage.removeItem('deepwater-user');
    setUser(null); setCases([]); setProfiles([]); setInvitations([]);
  }
  async function loadAmber() {
    setError('');
    try {
      const data = await gql(`query AmberHome { hasAmberProfile amberProfiles { id name city statement demo } amberInvitations { id status incoming otherPerson } }`);
      setHasProfile(data.hasAmberProfile); setProfiles(data.amberProfiles); setInvitations(data.amberInvitations);
    } catch (e) { setError(e.message); }
  }
  useEffect(() => {
    if (!user) return;
    gql(`query EchoHome { echoCases { id title targetLanguage targetExample comparisonLanguage comparisonExample bridgeLanguage bridgeExample insight } }`)
      .then(data => setCases(data.echoCases)).catch(e => setError(e.message));
  }, [user]);

  async function practice(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const data = await gql(`mutation Practice($caseId: ID!, $answer: String!) { practiceEcho(caseId: $caseId, answer: $answer) { answer feedback transferReady } }`, { caseId: selected.id, answer });
      setFeedback(data.practiceEcho); setAnswer(data.practiceEcho.answer); setNotice('Your practice answer has been saved to your account.');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function createProfile(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await gql(`mutation CreateProfile($city: String!, $statement: String!) { createAmberProfile(city: $city, statement: $statement) { id } }`, { city, statement });
      await loadAmber();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function invite(profileId) {
    setBusy(true); setError(''); setNotice('');
    try { const data = await gql(`mutation Invite($profileId: ID!) { inviteAmber(profileId: $profileId) { status message } }`, { profileId }); setNotice(data.inviteAmber.message); }
    catch (e) { setError(e.message); } finally { setBusy(false); await loadAmber(); }
  }
  async function respond(invitationId, accept) {
    setBusy(true); setError(''); setNotice('');
    try { const data = await gql(`mutation Respond($invitationId: ID!, $accept: Boolean!) { respondToAmberInvitation(invitationId: $invitationId, accept: $accept) { status message } }`, { invitationId, accept }); setNotice(data.respondToAmberInvitation.message); }
    catch (e) { setError(e.message); } finally { setBusy(false); await loadAmber(); }
  }

  if (!user) return <Auth onLogin={login} />;
  const logo = '/deepwater-logo-warm.png';
  return <main className={tab}><header><img className="logo" src={logo} alt={tab === 'echo' ? 'Deepwater Echo' : 'Deepwater Amber'} />
    <span className="user">{user.name}<button className="link" onClick={logout}>Sign out</button></span></header>
    <nav><button className={tab === 'echo' ? 'active' : ''} onClick={() => { setTab('echo'); setError(''); }}>Echo</button>
      <button className={tab === 'amber' ? 'active' : ''} onClick={() => { setTab('amber'); setError(''); loadAmber(); }}>Amber</button></nav>
    {error && <p role="alert" className="error">{error}</p>}{notice && <p role="status" className="notice">{notice}</p>}
    {tab === 'echo' && <section><p className="eyebrow">ECHO · LANGUAGE THROUGH MEANING</p><h2>Learn a pattern by living it.</h2>
      {!selected ? <div className="profiles">{cases.map(item => <article key={item.id}><h3>{item.title}</h3><p>{item.targetLanguage} · compare with {item.comparisonLanguage}</p><button onClick={() => { setSelected(item); setFeedback(null); setAnswer(''); }}>Open learning case</button></article>)}</div> : <>
        <button className="link" onClick={() => { setSelected(null); setFeedback(null); }}>← All cases</button><h3>{selected.title}</h3>
        <div className="result"><p><b>{selected.targetLanguage}:</b> {selected.targetExample}</p><p><b>{selected.comparisonLanguage}:</b> {selected.comparisonExample}</p><p><b>{selected.bridgeLanguage}:</b> {selected.bridgeExample}</p><p>{selected.insight}</p></div>
        <form onSubmit={practice}><label>Express this meaning in your own words<textarea required maxLength="2000" value={answer} onChange={e => setAnswer(e.target.value)} placeholder="Write an original expression…" /></label><button disabled={busy || !answer.trim()}>{busy ? 'Saving…' : 'Save practice'}</button></form>
        {feedback && <div className="result"><b>Practice saved</b><p>{feedback.feedback}</p><small>This MVP stores your answer; feedback is a learning prompt, not an automated grammar score.</small></div>}
      </>}</section>}
    {tab === 'amber' && <section><p className="eyebrow">AMBER · MUTUAL INTEREST</p><h2>Connection begins with choice.</h2>
      {!hasProfile ? <><p>Create a brief profile before discovering other people. Sample profiles are clearly marked.</p><form onSubmit={createProfile}><label>City<input required maxLength="160" value={city} onChange={e => setCity(e.target.value)} placeholder="Copenhagen" /></label><label>A little about you<textarea required maxLength="500" value={statement} onChange={e => setStatement(e.target.value)} placeholder="What would you enjoy sharing with someone?" /></label><button disabled={busy}>Create Amber profile</button></form></> : <>
        <h3>Invitations</h3>{invitations.length === 0 && <p>No invitations yet. Send one when you find someone interesting.</p>}
        {invitations.map(inviteItem => <article className="invitation" key={inviteItem.id}><b>{inviteItem.incoming ? inviteItem.otherPerson + ' invited you' : 'You invited ' + inviteItem.otherPerson}</b><p>Status: {inviteItem.status}</p>{inviteItem.incoming && inviteItem.status === 'PENDING' && <div className="actions"><button disabled={busy} onClick={() => respond(inviteItem.id, true)}>Accept</button><button className="secondary" disabled={busy} onClick={() => respond(inviteItem.id, false)}>Decline</button></div>}</article>)}
        <h3>People to discover</h3><div className="profiles">{profiles.map(person => <article key={person.id}><h3>{person.name}</h3><small>{person.city}{person.demo ? ' · DEMO PROFILE' : ''}</small><p>“{person.statement}”</p><button disabled={busy || person.demo} onClick={() => invite(person.id)}>{person.demo ? 'Sample only' : 'Send invitation'}</button></article>)}</div>
      </>}</section>}
    <footer>Through expression, connect the world.</footer></main>;
}

createRoot(document.getElementById('root')).render(<App />);

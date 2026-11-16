import React, { useEffect, useState } from 'react';
import DeepwaterMark3D from './DeepwaterMark3D.jsx';
import { Brand } from './AppHeader.jsx';
import { BRANDS, BrandSwitch, useBrand } from './brand.jsx';
import { COMING_SOON, PRODUCTS } from './app/config.js';
import { API, request } from './app/http.js';
import { ThemeSwitch } from './app/theme.jsx';
import { showGoogleSignIn } from './platform.js';
import { Notice } from './ui/index.jsx';

/** The welcome page: what Deepwater offers on the left, create account or sign in on the right. */
export default function Auth({ onLogin, message, onChooseProduct }) {
  const [brand] = useBrand();
  const [mode, setMode] = useState('register');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Google sign-in returns here with a one-time code (or an error) that is exchanged for a session.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('login_code');
    const googleError = params.get('login_error');
    if (!code && !googleError) return;
    window.history.replaceState({}, '', window.location.pathname);
    if (googleError) {
      setError(googleError === 'cancelled' ? 'Google sign-in was cancelled.' : 'Google sign-in did not complete. Please try again.');
      return;
    }
    setBusy(true);
    request('/api/v1/auth/google/exchange', { method: 'POST', body: { code }, auth: false })
      .then(onLogin)
      .catch(exchangeError => setError(exchangeError.kind === 'client' ? 'Google sign-in expired. Please try again.' : exchangeError.message))
      .finally(() => setBusy(false));
  }, []);

  async function submit(event) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      const endpoint = mode === 'register' ? 'register' : 'login';
      onLogin(await request(`/api/v1/auth/${endpoint}`, {
        method: 'POST', auth: false, body: mode === 'register' ? { name, email, password, phone } : { email, password },
      }));
    } catch (error) { setError(error.message); } finally { setBusy(false); }
  }

  return <main className="auth-shell">
    <header className="auth-header"><Brand /><div className="header-tools"><BrandSwitch /><ThemeSwitch /></div></header>
    <div className="auth-grid">
    <section className="pitch">
      {brand === 'nibble'
        ? <img className="pitch-mascot" src="/brand/nibble.svg" alt="Nibble, our mouse examiner" width="200" height="200" />
        : <DeepwaterMark3D size={260} label="Deepwater mark in 3D. Drag to turn it." />}
      <p className="eyebrow">Learn the language. Find the job. Make it home.</p>
      <h1>Learn the language.<br /><span>Land the job.</span></h1>
      <p className="pitch-lead">Learn Danish, German or Polish, practise for the Polish B1 exam, and find a job in Denmark and the Nordics. One account opens all of it.</p>
      <div className="welcome-products" role="list" aria-label="What you can do here">
        {PRODUCTS.map(item => <button key={item.id} type="button" role="listitem" className="welcome-product"
          onClick={() => { onChooseProduct(item.id); document.getElementById('access-title')?.scrollIntoView({ behavior: 'smooth' }); }}>
          <b>{item.name}</b><span className="pill">{item.tag}</span><p>{item.line}</p></button>)}
        {COMING_SOON.map(item => <div key={item.id} role="listitem" className="welcome-product soon"><b>{item.name}</b><span className="pill muted">{item.tag}</span><p>{item.line}</p></div>)}
      </div>
      <p className="eyebrow welcome-why">Why Echo is different</p>
      <ul className="pitch-points">
        <li><b>Understand, don’t memorise</b><span>Every lesson explains why a sentence is built the way it is.</span></li>
        <li><b>Built around your memory</b><span>Type phrases from memory, hear them read aloud, and see each one come back just before you’d forget it.</span></li>
        <li><b>No streak guilt</b><span>Miss a day and your reviews simply move. A slip steps back one level, never to zero.</span></li>
      </ul>
    </section>
    <section className="access panel" aria-labelledby="access-title">
    <h2 id="access-title">{mode === 'register' ? `Create your ${BRANDS[brand].name} account` : 'Welcome back'}</h2>
    <p className="access-sub">{mode === 'register' ? 'One account for Echo (Danish, German, Polish), Harbor and Amber.' : 'Sign in to continue where you left off.'}</p>
    <form onSubmit={submit}>
      {mode === 'register' && <label>Your name<input required maxLength="160" autoComplete="name" value={name} onChange={event => setName(event.target.value)} /></label>}
      <label>Email<input required type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} /></label>
      {mode === 'register' && <label><span>Phone <small className="optional">optional</small></span><input type="tel" maxLength="24" autoComplete="tel" placeholder="+45 12 34 56 78" value={phone} onChange={event => setPhone(event.target.value)} /><small>Only for your account. Never shown to others or used for marketing.</small></label>}
      <label>Password<input required type="password" minLength="12" maxLength="72" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} /><small>Use 12–72 characters.</small></label>
      <button disabled={busy}>{busy ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Sign in'}</button>
    </form>
    {error && <Notice tone="error">{error}</Notice>}
    {message && <Notice tone="good">{message}</Notice>}
    {showGoogleSignIn() && <>
      <div className="auth-divider"><span>or</span></div>
      <a href={`${API}/api/v1/auth/google`} className="google-btn">Continue with Google</a>
    </>}
    <button className="link" onClick={() => { setError(''); setMode(mode === 'register' ? 'login' : 'register'); }}>{mode === 'register' ? 'Already have an account? Sign in' : 'Create an account'}</button>
  </section>
    </div>
    <footer>{BRANDS[brand].name} · {BRANDS[brand].tagline} · <a href="/privacy.html">Privacy &amp; contact</a></footer>
  </main>;
}

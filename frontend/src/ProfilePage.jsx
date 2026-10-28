import React, { useEffect, useState } from 'react';
import { gql } from './api.js';
import DeepwaterMark3D from './DeepwaterMark3D.jsx';

const FIELDS = 'name email emailVerified phone city country nativeLanguage about';
const EMPTY = { name: '', phone: '', city: '', country: '', nativeLanguage: '', about: '' };

function initials(name) {
  return (name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0].toUpperCase()).join('');
}

/** The person's own details. They belong to the Deepwater account, so a future Amber profile can reuse them. */
export default function ProfilePage({ home, cards, onSaved }) {
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    gql(`query { myProfile { ${FIELDS} } }`).then(data => {
      setProfile(data.myProfile);
      setForm(Object.fromEntries(Object.keys(EMPTY).map(key => [key, data.myProfile[key] || ''])));
    }).catch(loadError => setError(loadError.message));
  }, []);

  const update = key => event => { setForm(current => ({ ...current, [key]: event.target.value })); setStatus(''); };

  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setStatus('');
    try {
      const data = await gql(`mutation($input: PersonProfileInput!) { updateMyProfile(input: $input) { ${FIELDS} } }`, { input: form });
      setProfile(data.updateMyProfile);
      setForm(Object.fromEntries(Object.keys(EMPTY).map(key => [key, data.updateMyProfile[key] || ''])));
      setStatus('Saved.'); onSaved?.(data.updateMyProfile);
    } catch (saveError) { setError(saveError.message); }
    finally { setBusy(false); }
  }

  const depth = home.lessons.length ? home.lessons.reduce((total, lesson) => total + lesson.mastery, 0) / home.lessons.length : 0;
  const place = [profile?.city, profile?.country].filter(Boolean).join(', ');

  return <section className="profile-page" aria-labelledby="profile-title">
    <div className="profile-card panel">
      <div className="avatar" aria-hidden="true">{initials(form.name || profile?.name)}</div>
      <div className="profile-summary">
        <p className="eyebrow">YOUR PROFILE</p>
        <h2 id="profile-title">{profile?.name || 'Your profile'}</h2>
        <p>{place || 'Add where you live so practice can use places you know.'}</p>
        <p className="profile-meta">{profile?.email}{profile && <span className={`pill ${profile.emailVerified ? '' : 'soft'}`}>{profile.emailVerified ? 'Email confirmed' : 'Email not confirmed'}</span>}</p>
      </div>
      <figure className="depth-mark">
        <DeepwaterMark3D size={150} depth={depth} label={`Overall depth ${Math.round(depth * 100)}%`} />
        <figcaption>{Math.round(depth * 100)}% deep · {cards.length} cards</figcaption>
      </figure>
    </div>

    <form className="profile-form panel" onSubmit={save}>
      <h3>About you</h3>
      <div className="form-grid">
        <label>Name<input required maxLength="160" autoComplete="name" value={form.name} onChange={update('name')} /></label>
        <label><span>Phone <small>optional</small></span><input type="tel" maxLength="24" autoComplete="tel" placeholder="+45 12 34 56 78" value={form.phone} onChange={update('phone')} /></label>
        <label><span>City where you live <small>optional</small></span><input maxLength="120" autoComplete="address-level2" value={form.city} onChange={update('city')} /></label>
        <label><span>Country <small>optional</small></span><input maxLength="80" autoComplete="country-name" value={form.country} onChange={update('country')} /></label>
        <label><span>Your first language <small>optional</small></span><input maxLength="60" list="first-languages" value={form.nativeLanguage} onChange={update('nativeLanguage')} />
          <datalist id="first-languages">{['English', 'Arabic', 'Armenian', 'Polish', 'Ukrainian', 'Romanian', 'Spanish', 'Portuguese', 'Turkish', 'Persian', 'Russian', 'Hindi', 'Chinese'].map(name => <option key={name} value={name} />)}</datalist>
        </label>
        <label className="wide"><span>Why you are learning <small>optional</small></span><textarea maxLength="500" value={form.about} onChange={update('about')}
          placeholder={`For example: talk with my partner’s family in ${home.profile.languageCode === 'de' ? 'German' : 'Danish'}`} /></label>
      </div>
      <div className="profile-actions"><button disabled={busy || !profile}>{busy ? 'Saving…' : 'Save profile'}</button>
        {status && <span role="status" className="saved-note">{status}</span>}</div>
      {error && <p role="alert" className="error">{error}</p>}
      <p className="privacy-note"><b>Private by default.</b> Only you can see these details. Echo does not show them to other learners, and
        they are deleted with your account. Your phone number is not used for marketing.</p>
    </form>
  </section>;
}

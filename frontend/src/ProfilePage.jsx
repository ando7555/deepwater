import React, { useEffect, useState } from 'react';
import { gql } from './api.js';
import DeepwaterMark3D from './DeepwaterMark3D.jsx';
import { initials, languageName } from './app/config.js';
import { usePhoto } from './app/photos.js';
import { Notice } from './ui/index.jsx';
import PhotoManager from './profile/PhotoManager.jsx';
import LanguagesEditor from './profile/LanguagesEditor.jsx';
import AccountSettings from './profile/AccountSettings.jsx';
import { ageFrom, cleanLanguages } from './profile/profileTools.js';

const FIELDS = 'name email emailVerified phone city country nativeLanguage about publicName birthDate age bio languages { language level } photos { id position }';
const TEXT = { name: '', phone: '', city: '', country: '', nativeLanguage: '', about: '', publicName: '', birthDate: '', bio: '' };

function formFrom(profile) {
  return { ...Object.fromEntries(Object.keys(TEXT).map(key => [key, profile[key] || ''])), languages: profile.languages.map(item => ({ ...item })) };
}

/**
 * The one Deepwater profile, shared by Echo, Harbor and Amber. The top part is what a product may show to other
 * people (today only Amber, after joining); the private part is never shown to anyone.
 */
export default function ProfilePage({ home, cards = [], onSaved, onSignedOut }) {
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ ...TEXT, languages: [] });
  const [photos, setPhotos] = useState([]);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [problems, setProblems] = useState([]);

  useEffect(() => {
    gql(`query { myProfile { ${FIELDS} } }`).then(data => {
      setProfile(data.myProfile); setForm(formFrom(data.myProfile)); setPhotos(data.myProfile.photos);
    }).catch(loadError => setError(loadError.message));
  }, []);

  const update = key => event => { setForm(current => ({ ...current, [key]: event.target.value })); setStatus(''); };

  async function save(event) {
    event.preventDefault(); setError(''); setStatus('');
    const languages = cleanLanguages(form.languages);
    setProblems(languages.problems);
    if (languages.problems.length) return;
    setBusy(true);
    try {
      const input = { ...form, languages: languages.list };
      const data = await gql(`mutation($input: PersonProfileInput!) { updateMyProfile(input: $input) { ${FIELDS} } }`, { input });
      setProfile(data.updateMyProfile); setForm(formFrom(data.updateMyProfile)); setPhotos(data.updateMyProfile.photos);
      setStatus('Saved.'); onSaved?.(data.updateMyProfile);
    } catch (saveError) { setError(saveError.message); }
    finally { setBusy(false); }
  }

  const lessons = home?.lessons ?? [];
  const depth = lessons.length ? lessons.reduce((total, lesson) => total + lesson.mastery, 0) / lessons.length : 0;
  const place = [profile?.city, profile?.country].filter(Boolean).join(', ');
  const age = ageFrom(form.birthDate);

  return <section className="profile-page" aria-labelledby="profile-title">
    <div className="profile-card panel">
      <ProfileAvatar photo={photos[0]} name={form.publicName || form.name || profile?.name} />
      <div className="profile-summary">
        <p className="eyebrow">YOUR DEEPWATER PROFILE · ECHO · HARBOR · AMBER</p>
        <h2 id="profile-title">{profile?.publicName || profile?.name || 'Your profile'}{profile?.age != null ? `, ${profile.age}` : ''}</h2>
        <p>{place || 'Add where you live so practice can use places you know.'}</p>
        <p className="profile-meta">{profile?.email}{profile && <span className={`pill ${profile.emailVerified ? '' : 'soft'}`}>{profile.emailVerified ? 'Email confirmed' : 'Email not confirmed'}</span>}</p>
      </div>
      {home && <figure className="depth-mark">
        <DeepwaterMark3D size={150} depth={depth} label={`Overall depth ${Math.round(depth * 100)}%`} />
        <figcaption>{Math.round(depth * 100)}% deep · {cards.length} cards</figcaption>
      </figure>}
    </div>

    <form className="profile-form panel" onSubmit={save}>
      <h3>Shown where you choose</h3>
      <p className="privacy-note">One profile for every Deepwater product. Echo and Harbor never show it to anyone. Amber shows these details only after you join Amber, and you choose there what else to add.</p>
      <div className="form-grid">
        <label><span>First name others see <small>optional</small></span><input maxLength="40" autoComplete="given-name" value={form.publicName} onChange={update('publicName')} /></label>
        <label><span>Birth date <small>{age != null && age >= 0 ? `${age} years` : 'optional'}</small></span><input type="date" autoComplete="bday" value={form.birthDate} onChange={update('birthDate')} /></label>
        <label><span>City where you live <small>optional</small></span><input maxLength="120" autoComplete="address-level2" value={form.city} onChange={update('city')} /></label>
        <label className="wide"><span>Bio <small>optional · {form.bio.length}/500</small></span><textarea maxLength="500" value={form.bio} onChange={update('bio')}
          placeholder="A few lines about you: what you enjoy, what you are looking forward to." /></label>
        <div className="wide"><span className="field-label">Languages you know <small>optional</small></span>
          <LanguagesEditor value={form.languages} onChange={languages => { setForm(current => ({ ...current, languages })); setStatus(''); }} /></div>
      </div>

      <h3>Private, only for you</h3>
      <div className="form-grid">
        <label>Full name<input required maxLength="160" autoComplete="name" value={form.name} onChange={update('name')} /></label>
        <label><span>Phone <small>optional</small></span><input type="tel" maxLength="24" autoComplete="tel" placeholder="+45 12 34 56 78" value={form.phone} onChange={update('phone')} /></label>
        <label><span>Country <small>optional</small></span><input maxLength="80" autoComplete="country-name" value={form.country} onChange={update('country')} /></label>
        <label><span>Your first language <small>optional</small></span><input maxLength="60" list="known-languages" value={form.nativeLanguage} onChange={update('nativeLanguage')} /></label>
        <label className="wide"><span>Why you are learning <small>optional</small></span><textarea maxLength="500" value={form.about} onChange={update('about')}
          placeholder={`For example: talk with my partner’s family in ${languageName(home?.profile?.languageCode ?? 'da')}`} /></label>
      </div>
      <div className="profile-actions"><button disabled={busy || !profile}>{busy ? 'Saving…' : 'Save profile'}</button>
        {status && <span role="status" className="saved-note">{status}</span>}</div>
      {problems.map(problem => <Notice key={problem} tone="error">{problem}</Notice>)}
      {error && <Notice tone="error">{error}</Notice>}
      <p className="privacy-note"><b>Private by default.</b> Your full name, email, phone, country and why you are learning are never shown to other people, and everything is deleted with your account. Your phone number is not used for marketing.</p>
    </form>

    <section className="profile-form panel" aria-labelledby="photos-title">
      <h3 id="photos-title">Photos</h3>
      {profile && <PhotoManager photos={photos} onChange={setPhotos}
        note="Up to 4 photos. Only Amber shows them, and by default only after you and the other person both say yes. Location and camera details are removed when you upload." />}
    </section>

    {onSignedOut && <AccountSettings onSignedOut={onSignedOut} />}
  </section>;
}

function ProfileAvatar({ photo, name }) {
  const url = usePhoto(photo ? `/api/v1/account/photos/${photo.id}` : null);
  if (url) return <img className="avatar photo" src={url} alt="" />;
  return <div className="avatar" aria-hidden="true">{initials(name)}</div>;
}

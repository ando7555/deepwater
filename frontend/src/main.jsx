import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import DeepwaterMark3D from './DeepwaterMark3D.jsx';
import ExerciseCard from './ExerciseCard.jsx';
import PracticeHub from './PracticeHub.jsx';
import PhraseCards, { MatchingWarmUp } from './PhraseCards.jsx';
import CalmProgress from './CalmProgress.jsx';
import DailyReview from './DailyReview.jsx';
import ProfilePage from './ProfilePage.jsx';
import { API, gql, formatDate } from './api.js';
import './style.css';

const THEMES = [
  { id: 'deep', label: 'Deep blue', mark: '/brand/mark.svg' },
  { id: 'amber', label: 'Amber', mark: '/brand/mark-amber.svg' },
  { id: 'light', label: 'Light', mark: '/brand/mark-light.svg' },
];

function readTheme() {
  try { return THEMES.some(theme => theme.id === localStorage.getItem('deepwater-theme')) ? localStorage.getItem('deepwater-theme') : 'deep'; } catch { return 'deep'; }
}

function applyTheme(id) {
  if (id === 'deep') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', id);
}

function useTheme() {
  const [theme, setTheme] = useState(readTheme);
  useEffect(() => {
    applyTheme(theme);
    try { localStorage.setItem('deepwater-theme', theme); } catch { /* storage unavailable; theme still applies for this visit */ }
    window.dispatchEvent(new CustomEvent('deepwater-theme', { detail: theme }));
  }, [theme]);
  useEffect(() => {
    const sync = event => setTheme(event.detail);
    window.addEventListener('deepwater-theme', sync);
    return () => window.removeEventListener('deepwater-theme', sync);
  }, []);
  return [theme, setTheme];
}

function ThemeSwitch() {
  const [theme, setTheme] = useTheme();
  return <div className="theme-switch" role="group" aria-label="Color theme">
    {THEMES.map(option => <button key={option.id} type="button" className={`swatch swatch-${option.id}`} aria-pressed={theme === option.id} aria-label={option.label} title={option.label} onClick={() => setTheme(option.id)} />)}
  </div>;
}

function Brand({ product = 'Echo' }) {
  const [theme] = useTheme();
  const mark = THEMES.find(option => option.id === theme)?.mark ?? THEMES[0].mark;
  return <span className="brand"><img src={mark} alt="" width="40" height="40" /><span className="brand-text"><b>{product}</b><small>by Deepwater</small></span></span>;
}

function Auth({ onLogin, message }) {
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
    fetch(`${API}/api/v1/auth/google/exchange`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code }),
    }).then(async response => {
      if (!response.ok) throw new Error('Google sign-in expired. Please try again.');
      onLogin(await response.json());
    }).catch(exchangeError => setError(exchangeError.message)).finally(() => setBusy(false));
  }, []);

  async function submit(event) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      const endpoint = mode === 'register' ? 'register' : 'login';
      const response = await fetch(`${API}/api/v1/auth/${endpoint}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(mode === 'register' ? { name, email, password, phone } : { email, password }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.detail || body.message || 'Sign in failed.');
      onLogin(body);
    } catch (error) { setError(error.message); } finally { setBusy(false); }
  }

  return <main className="auth-shell">
    <header className="auth-header"><Brand /><ThemeSwitch /></header>
    <div className="auth-grid">
    <section className="pitch">
      <DeepwaterMark3D size={260} label="Deepwater mark in 3D. Drag to turn it." />
      <p className="eyebrow">Danish and German, one pattern at a time</p>
      <h1>Hear the pattern.<br /><span>Make it yours.</span></h1>
      <p className="pitch-lead">Echo shows you how a language works, lets you practise it in real situations, and schedules reviews so each pattern sticks.</p>
      <ul className="pitch-points">
        <li><b>Understand, don’t memorise</b><span>Every lesson explains why a sentence is built the way it is.</span></li>
        <li><b>Reviewed by a teacher</b><span>Lessons are published only after a language teacher approves them.</span></li>
        <li><b>Built around your memory</b><span>Type phrases from memory, hear them read aloud, and see each one come back just before you’d forget it.</span></li>
        <li><b>No streak guilt</b><span>Miss a day and your reviews simply move. A slip steps back one level, never to zero.</span></li>
        <li><b>Learn with real voices</b><span>Music and podcast lessons, used only with the creator’s permission.</span></li>
      </ul>
    </section>
    <section className="access panel" aria-labelledby="access-title">
    <h2 id="access-title">{mode === 'register' ? 'Create your Echo account' : 'Welcome back'}</h2>
    <p className="access-sub">{mode === 'register' ? 'Choose a language and a goal after you sign up.' : 'Sign in to continue where you left off.'}</p>
    <form onSubmit={submit}>
      {mode === 'register' && <label>Your name<input required maxLength="160" autoComplete="name" value={name} onChange={event => setName(event.target.value)} /></label>}
      <label>Email<input required type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} /></label>
      {mode === 'register' && <label><span>Phone <small className="optional">optional</small></span><input type="tel" maxLength="24" autoComplete="tel" placeholder="+45 12 34 56 78" value={phone} onChange={event => setPhone(event.target.value)} /><small>Only for your account. Never shown to others or used for marketing.</small></label>}
      <label>Password<input required type="password" minLength="12" maxLength="72" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} /><small>Use 12–72 characters.</small></label>
      <button disabled={busy}>{busy ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Sign in'}</button>
    </form>
    {error && <p role="alert" className="error">{error}</p>}
    {message && <p role="status" className="notice">{message}</p>}
    <div className="auth-divider"><span>or</span></div>
    <a href={`${API}/api/v1/auth/google`} className="google-btn">Continue with Google</a>
    <button className="link" onClick={() => { setError(''); setMode(mode === 'register' ? 'login' : 'register'); }}>{mode === 'register' ? 'Already have an account? Sign in' : 'Create an account'}</button>
  </section>
    </div>
    <footer>Deepwater · Go deeper.</footer>
  </main>;
}

const HOME_QUERY = `query EchoHome {
  echoHome {
    onboarded nextAction totalAttempts correctAttempts audioAttempts recommendedAudioLessonId recommendedAudioReason
    profile { goal selfReportedLevel languageCode diagnosticScore startingRoute }
    lessons { id title level topic objective explanation sourceReference contentStatus mastery attempts nextReviewAt due
      exercises { id position taskType prompt lastCorrect options { key text } }
      audio { mediaType sourceTitle creator sourceUrl playbackUrl editorialBand topicTags speechRate vocabularyDensity idiomDensity pronunciationLoad topicFamiliarity beforeListeningPrompt gistPrompt focusPrompt transcriptAllowed transcriptText rightsExpiresAt }
    }
  }
}`;

const ROUTE_COPY = 'We’ll begin with the experience level you chose and adjust as you practise.';
const BAND_LABELS = { A0_A1: 'A0–A1 · Beginner', A2: 'A2 · Elementary', B1_B2: 'B1–B2 · Intermediate', C1_C2: 'C1–C2 · Advanced' };
const NEW_AUDIO_DRAFT = {
  languageCode: 'da', mediaType: 'MUSIC', title: '', sourceTitle: '', creator: '', sourceUrl: '', playbackUrl: '',
  editorialBand: 'A0_A1', topicTags: '', skillKey: '', objective: '', explanation: '', sourceReference: '',
  speechRate: 2, vocabularyDensity: 2, idiomDensity: 2, pronunciationLoad: 2, topicFamiliarity: 3,
  beforeListeningPrompt: '', gistPrompt: '', focusPrompt: '', transcriptText: '', rightsBasis: 'UNVERIFIED',
  linkAllowed: false, playbackAllowed: false, transcriptAllowed: false, rightsReference: '', rightsExpiresAt: '',
};

function AudioDraftForm({ draft, update, busy, onSubmit }) {
  return <form className="audio-draft-form" onSubmit={onSubmit}>
    <h3>Create a music or podcast draft</h3>
    <p>Build a levelled listening lesson. It remains private until your review and approval.</p>
    <div className="audio-form-grid">
      <label>Learning language<select value={draft.languageCode} onChange={e => update('languageCode', e.target.value)}><option value="da">Danish</option><option value="de">German</option></select></label>
      <label>Medium<select value={draft.mediaType} onChange={e => update('mediaType', e.target.value)}><option value="MUSIC">Music</option><option value="PODCAST">Podcast</option></select></label>
      <label>Editorial level<select value={draft.editorialBand} onChange={e => update('editorialBand', e.target.value)}>{Object.entries(BAND_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select><small>Teacher-curated guide, not a formal CEFR placement.</small></label>
      <label>Echo lesson title<input required maxLength="240" value={draft.title} onChange={e => update('title', e.target.value)} /></label>
      <label>Audio title<input required maxLength="240" value={draft.sourceTitle} onChange={e => update('sourceTitle', e.target.value)} /></label>
      <label>Artist / host / creator<input required maxLength="240" value={draft.creator} onChange={e => update('creator', e.target.value)} /></label>
      <label>Topic tags<input required maxLength="500" placeholder="daily life, work, relationships" value={draft.topicTags} onChange={e => update('topicTags', e.target.value)} /></label>
      <label>Source page URL<input type="url" required placeholder="https://…" value={draft.sourceUrl} onChange={e => update('sourceUrl', e.target.value)} /></label>
      <label>Direct audio URL, only if playback is permitted<input type="url" placeholder="https://…/audio.mp3" value={draft.playbackUrl} onChange={e => update('playbackUrl', e.target.value)} /></label>
      <label>Skill key<input required maxLength="120" placeholder="danish.audio.gist.beginner" value={draft.skillKey} onChange={e => update('skillKey', e.target.value)} /></label>
      <label>Learning objective<input required maxLength="500" value={draft.objective} onChange={e => update('objective', e.target.value)} /></label>
    </div>
    <label>Teacher explanation<textarea required maxLength="3000" value={draft.explanation} onChange={e => update('explanation', e.target.value)} /></label>
    <p className="audio-rating-help">Rate the recording and lesson context from 1 (low) to 5 (high). These are separate signals, not one difficulty score.</p>
    <div className="audio-form-grid audio-ratings">
      {[['speechRate','Speech rate'],['vocabularyDensity','Vocabulary density'],['idiomDensity','Idiom density'],['pronunciationLoad','Pronunciation / reductions'],['topicFamiliarity','Topic familiarity for beginners']].map(([key, label]) => <label key={key}>{label}<select value={draft[key]} onChange={e => update(key, e.target.value)}>{[1,2,3,4,5].map(n => <option key={n} value={n}>{n}</option>)}</select></label>)}
    </div>
    <div className="audio-form-grid">
      <label>Before listening: context or prediction prompt<textarea required maxLength="1000" value={draft.beforeListeningPrompt} onChange={e => update('beforeListeningPrompt', e.target.value)} /></label>
      <label>Listen for gist<textarea required maxLength="1000" value={draft.gistPrompt} onChange={e => update('gistPrompt', e.target.value)} /></label>
      <label>Focus prompt for a phrase, sound, or structure<textarea required maxLength="1000" value={draft.focusPrompt} onChange={e => update('focusPrompt', e.target.value)} /></label>
    </div>
    <fieldset className="rights-fieldset"><legend>Source permissions · confirm each use separately</legend>
      <label>Rights basis<select value={draft.rightsBasis} onChange={e => update('rightsBasis', e.target.value)}><option value="UNVERIFIED">Not checked yet · draft only</option><option value="ORIGINAL">Original / teacher-created</option><option value="LICENSED">Licensed for this use</option><option value="PROVIDER_TERMS">Provider terms permit this use</option></select></label>
      <label className="choice"><input type="checkbox" checked={draft.linkAllowed} onChange={e => update('linkAllowed', e.target.checked)} /><span>The source page may be linked for learners.</span></label>
      <label className="choice"><input type="checkbox" checked={draft.playbackAllowed} onChange={e => update('playbackAllowed', e.target.checked)} /><span>The direct audio URL may be played in Echo.</span></label>
      <label className="choice"><input type="checkbox" checked={draft.transcriptAllowed} onChange={e => { update('transcriptAllowed', e.target.checked); if (!e.target.checked) update('transcriptText', ''); }} /><span>Echo may display this transcript or lyric excerpt.</span></label>
      {draft.transcriptAllowed && <label>Authorized transcript / excerpt<textarea maxLength="5000" value={draft.transcriptText} onChange={e => update('transcriptText', e.target.value)} /><small>Only enter transcript text when your permission covers displaying it to learners.</small></label>}
      <label>Rights evidence or permission reference<input required maxLength="500" placeholder="License, permission record, or terms reviewed" value={draft.rightsReference} onChange={e => update('rightsReference', e.target.value)} /></label>
      <label>Rights review / expiry date (optional)<input type="date" value={draft.rightsExpiresAt} onChange={e => update('rightsExpiresAt', e.target.value)} /></label>
    </fieldset>
    <button disabled={busy}>{busy ? 'Saving draft…' : 'Save private audio draft'}</button>
    <small>Unverified, expired, or unauthorized audio cannot be published. Echo does not fetch or copy the source media.</small>
  </form>;
}

function App() {
  const [user, setUser] = useState(() => { try { return JSON.parse(sessionStorage.getItem('deepwater-user')); } catch { return null; } });
  const [home, setHome] = useState(null);
  const [goal, setGoal] = useState('');
  const [languageCode, setLanguageCode] = useState('da');
  const [selfReportedLevel, setSelfReportedLevel] = useState('new');
  const [answers, setAnswers] = useState({});
  const [selfChecks, setSelfChecks] = useState({});
  const [results, setResults] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [verifyMessage, setVerifyMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [teacherLessons, setTeacherLessons] = useState([]);
  const [teacherMode, setTeacherMode] = useState(false);
  const [draftJson, setDraftJson] = useState('');
  const [audioDraft, setAudioDraft] = useState(NEW_AUDIO_DRAFT);
  const [selectedLessonId, setSelectedLessonId] = useState('');
  const [audioFilter, setAudioFilter] = useState('ALL');
  const [bandFilter, setBandFilter] = useState('ALL');
  const [audioStages, setAudioStages] = useState({});
  const [audioAnswers, setAudioAnswers] = useState({});
  const [audioConfidence, setAudioConfidence] = useState({});
  const [transcriptShown, setTranscriptShown] = useState({});
  const [audioResults, setAudioResults] = useState({});
  const [tab, setTab] = useState('today');
  const [cards, setCards] = useState([]);
  const [warmUp, setWarmUp] = useState(false);
  const [reviewing, setReviewing] = useState(false);

  function login(account) {
    sessionStorage.setItem('deepwater-token', account.token);
    sessionStorage.setItem('deepwater-user', JSON.stringify(account));
    setUser(account);
  }

  // The confirmation email links here with ?verify_email=<token>.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('verify_email');
    if (!token) return;
    window.history.replaceState({}, '', window.location.pathname);
    fetch(`${API}/api/v1/auth/verify-email`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }),
    }).then(async response => {
      if (!response.ok) throw new Error((await response.json().catch(() => ({}))).detail || 'This confirmation link did not work.');
      setVerifyMessage('Your email is confirmed.');
      setUser(current => {
        if (!current) return current;
        const updated = { ...current, emailVerified: 'true' };
        sessionStorage.setItem('deepwater-user', JSON.stringify(updated));
        return updated;
      });
    }).catch(verifyError => setVerifyMessage(verifyError.message));
  }, []);

  async function resendVerification() {
    setVerifyMessage('');
    const response = await fetch(`${API}/api/v1/auth/verify-email/resend`, {
      method: 'POST', headers: { Authorization: `Bearer ${sessionStorage.getItem('deepwater-token')}` },
    });
    setVerifyMessage(response.ok ? `A new link is on its way to ${user.email}.`
      : (await response.json().catch(() => ({}))).detail || 'A new link could not be sent.');
  }

  function logout() {
    sessionStorage.removeItem('deepwater-token');
    sessionStorage.removeItem('deepwater-user');
    setUser(null); setHome(null); setResults({});
  }

  async function loadHome() {
    setLoading(true); setError('');
    try { setHome((await gql(HOME_QUERY)).echoHome); }
    catch (loadError) { setError(loadError.message); }
    finally { setLoading(false); }
  }

  useEffect(() => { if (user) loadHome(); }, [user]);

  const CARD_FIELDS = 'id front back lessonId box nextReviewAt due';
  async function loadCards() {
    try { setCards((await gql(`query { echoCards { ${CARD_FIELDS} } }`)).echoCards); }
    catch (cardsError) { setError(cardsError.message); }
  }

  useEffect(() => { if (home?.onboarded) loadCards(); }, [home?.onboarded, home?.profile?.languageCode]);

  async function saveCard(front, back, lessonId = null) {
    setBusy(true); setError(''); setNotice('');
    try {
      await gql(`mutation($front:String!,$back:String!,$lessonId:ID){ saveEchoCard(front:$front, back:$back, lessonId:$lessonId) { id } }`,
        { front: front.slice(0, 300), back: back.slice(0, 300), lessonId });
      await loadCards(); setNotice('Card saved. It’s ready for its first review.');
      return true;
    } catch (saveError) { setError(saveError.message); return false; }
    finally { setBusy(false); }
  }

  async function reviewCard(card, knewIt) {
    setBusy(true); setError('');
    try {
      await gql(`mutation($id:ID!,$knewIt:Boolean!){ reviewEchoCard(cardId:$id, knewIt:$knewIt) { id } }`, { id: card.id, knewIt });
      await loadCards();
    } catch (reviewError) { setError(reviewError.message); }
    finally { setBusy(false); }
  }

  async function deleteCard(card) {
    setError('');
    try { await gql(`mutation($id:ID!){ deleteEchoCard(cardId:$id) }`, { id: card.id }); await loadCards(); }
    catch (deleteError) { setError(deleteError.message); }
  }

  function lessonOf(exercise) {
    return home?.lessons.find(item => item.exercises.some(candidate => candidate.id === exercise.id));
  }

  // A missed answer becomes a card: the prompt on the front, the teacher's feedback on the back.
  function saveMissAsCard(exercise, result) {
    return saveCard(exercise.prompt, result.feedback, lessonOf(exercise)?.id || null);
  }

  function openLesson(lessonId) {
    setAudioFilter('ALL'); setBandFilter('ALL'); setSelectedLessonId(lessonId); setTab('today');
  }

  async function start(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const data = await gql(`mutation StartEcho($goal: String!, $languageCode: String!, $level: String!) {
        startEcho(goal: $goal, languageCode: $languageCode, selfReportedLevel: $level) {
          onboarded nextAction totalAttempts correctAttempts audioAttempts recommendedAudioLessonId recommendedAudioReason profile { goal selfReportedLevel languageCode diagnosticScore startingRoute }
          lessons { id title level topic objective explanation sourceReference contentStatus mastery attempts nextReviewAt due exercises { id position taskType prompt lastCorrect options { key text } } audio { mediaType sourceTitle creator sourceUrl playbackUrl editorialBand topicTags speechRate vocabularyDensity idiomDensity pronunciationLoad topicFamiliarity beforeListeningPrompt gistPrompt focusPrompt transcriptAllowed transcriptText rightsExpiresAt } }
        }
      }`, { goal, languageCode, level: selfReportedLevel });
      setHome(data.startEcho); setNotice(`${languageCode === 'da' ? 'Danish' : 'German'} is selected. Your starting route uses your self-described experience; this is not a formal level test.`);
    } catch (startError) { setError(startError.message); }
    finally { setBusy(false); }
  }

  async function loadTeacherLessons() {
    const data = await gql(`query { teacherLessons { id languageCode title level topic objective explanation sourceReference skillKey reviewStatus version revisionOf exercises { position taskType prompt expectedAnswer acceptedAnswers feedbackCorrect feedbackIncorrect options { key text } } audio { mediaType sourceTitle creator sourceUrl playbackUrl editorialBand topicTags speechRate vocabularyDensity idiomDensity pronunciationLoad topicFamiliarity beforeListeningPrompt gistPrompt focusPrompt transcriptAllowed transcriptText rightsBasis linkAllowed playbackAllowed rightsReference rightsExpiresAt } } }`);
    setTeacherLessons(data.teacherLessons);
  }

  function updateAudioDraft(key, value) {
    setAudioDraft(current => ({ ...current, [key]: value }));
  }

  async function saveAudioLessonDraft(event) {
    event.preventDefault(); setError(''); setNotice(''); setBusy(true);
    try {
      const bandLabel = BAND_LABELS[audioDraft.editorialBand];
      const sourceReference = audioDraft.sourceReference.trim() || `${audioDraft.creator} — ${audioDraft.sourceTitle} (${audioDraft.sourceUrl})`;
      const input = {
        languageCode: audioDraft.languageCode,
        title: audioDraft.title,
        level: bandLabel,
        topic: audioDraft.topicTags,
        objective: audioDraft.objective,
        explanation: audioDraft.explanation,
        sourceReference,
        skillKey: audioDraft.skillKey,
        exercises: [{ taskType: 'TRANSFER', prompt: 'Create an original expression that uses this lesson’s target. Do not copy lines from the recording.' }],
        audio: {
          mediaType: audioDraft.mediaType, sourceTitle: audioDraft.sourceTitle, creator: audioDraft.creator,
          sourceUrl: audioDraft.sourceUrl, ...(audioDraft.playbackUrl ? { playbackUrl: audioDraft.playbackUrl } : {}),
          editorialBand: audioDraft.editorialBand, topicTags: audioDraft.topicTags,
          speechRate: Number(audioDraft.speechRate), vocabularyDensity: Number(audioDraft.vocabularyDensity),
          idiomDensity: Number(audioDraft.idiomDensity), pronunciationLoad: Number(audioDraft.pronunciationLoad),
          topicFamiliarity: Number(audioDraft.topicFamiliarity), beforeListeningPrompt: audioDraft.beforeListeningPrompt,
          gistPrompt: audioDraft.gistPrompt, focusPrompt: audioDraft.focusPrompt,
          ...(audioDraft.transcriptAllowed && audioDraft.transcriptText ? { transcriptText: audioDraft.transcriptText } : {}),
          rightsBasis: audioDraft.rightsBasis, linkAllowed: audioDraft.linkAllowed,
          playbackAllowed: audioDraft.playbackAllowed, transcriptAllowed: audioDraft.transcriptAllowed,
          rightsReference: audioDraft.rightsReference, ...(audioDraft.rightsExpiresAt ? { rightsExpiresAt: audioDraft.rightsExpiresAt } : {}),
        },
      };
      const data = await gql(`mutation($input: LessonDraftInput!) { saveEchoLessonDraft(input: $input) { id title reviewStatus } }`, { input });
      setNotice(`Saved “${data.saveEchoLessonDraft.title}” as a private ${audioDraft.mediaType.toLowerCase()} lesson draft. It stays hidden until you approve it.`);
      await loadTeacherLessons();
    } catch (saveError) { setError(saveError.message); }
    finally { setBusy(false); }
  }

  async function saveDraft(event) {
    event.preventDefault(); setError(''); setNotice('');
    try {
      const input = JSON.parse(draftJson);
      const data = await gql(`mutation($input: LessonDraftInput!) { saveEchoLessonDraft(input: $input) { id title languageCode reviewStatus } }`, { input });
      setNotice(`Saved “${data.saveEchoLessonDraft.title}” as a private teacher draft.`);
      await loadTeacherLessons();
    } catch (saveError) { setError(saveError.message); }
  }

  async function publishDraft(id) {
    setError(''); setNotice('');
    try {
      const data = await gql(`mutation($id: ID!) { publishEchoLesson(lessonId: $id) { title reviewStatus } }`, { id });
      setNotice(`You approved and published “${data.publishEchoLesson.title}”.`);
      await loadTeacherLessons(); await loadHome();
    } catch (publishError) { setError(publishError.message); }
  }

  async function unpublishLesson(item) {
    if (!window.confirm('Learners will stop seeing this lesson immediately. Their progress is kept.')) return;
    setError(''); setNotice('');
    try {
      const data = await gql(`mutation($id: ID!) { unpublishEchoLesson(lessonId: $id) { title reviewStatus } }`, { id: item.id });
      setNotice(`“${data.unpublishEchoLesson.title}” is unpublished. Learners no longer see it.`);
      await loadTeacherLessons(); await loadHome();
    } catch (unpublishError) { setError(unpublishError.message); }
  }

  async function chooseLanguage(code) {
    setError(''); setNotice(''); setLanguageCode(code);
    try {
      const data = await gql(`mutation($languageCode: String!) { chooseEchoLanguage(languageCode: $languageCode) {
        onboarded nextAction totalAttempts correctAttempts audioAttempts recommendedAudioLessonId recommendedAudioReason profile { goal selfReportedLevel languageCode diagnosticScore startingRoute }
        lessons { id title level topic objective explanation sourceReference contentStatus mastery attempts nextReviewAt due exercises { id position taskType prompt lastCorrect options { key text } } audio { mediaType sourceTitle creator sourceUrl playbackUrl editorialBand topicTags speechRate vocabularyDensity idiomDensity pronunciationLoad topicFamiliarity beforeListeningPrompt gistPrompt focusPrompt transcriptAllowed transcriptText rightsExpiresAt } }
      } }`, { languageCode: code });
      setHome(data.chooseEchoLanguage); setNotice(`Switched to ${code === 'da' ? 'Danish' : 'German'}.`);
    } catch (changeError) { setError(changeError.message); }
  }

  async function submitExercise(event, exercise) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const data = await gql(`mutation SubmitEchoPractice($exerciseId: ID!, $answer: String!, $selfCheck: Boolean) {
        submitEchoPractice(exerciseId: $exerciseId, answer: $answer, transferSelfCheck: $selfCheck) {
          exerciseId answer graded correct feedback hint mastery nextReviewAt transfer
        }
      }`, {
        exerciseId: exercise.id,
        answer: answers[exercise.id] || '',
        selfCheck: exercise.taskType === 'TRANSFER' ? Boolean(selfChecks[exercise.id]) : null,
      });
      setResults(current => ({ ...current, [exercise.id]: data.submitEchoPractice }));
      await loadHome();
    } catch (submitError) { setError(submitError.message); }
    finally { setBusy(false); }
  }

  async function submitAudioStep(event, lesson, taskType) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    const transcriptUsed = Boolean(transcriptShown[lesson.id]);
    try {
      const data = await gql(`mutation($lessonId:ID!,$taskType:String!,$answer:String!,$transcriptUsed:Boolean!,$confidence:Int!) {
        submitEchoAudioEvidence(lessonId:$lessonId,taskType:$taskType,answer:$answer,transcriptUsed:$transcriptUsed,confidence:$confidence) {
          saved taskType transcriptUsed confidence nextTaskType repeatSuggested feedback
        }
      }`, { lessonId: lesson.id, taskType, answer: audioAnswers[lesson.id] || '', transcriptUsed,
        confidence: Number(audioConfidence[lesson.id] || 3) });
      setAudioResults(current => ({ ...current, [lesson.id]: data.submitEchoAudioEvidence }));
      setAudioStages(current => ({ ...current, [lesson.id]: data.submitEchoAudioEvidence.nextTaskType }));
      setAudioAnswers(current => ({ ...current, [lesson.id]: '' }));
      await loadHome();
    } catch (stepError) { setError(stepError.message); }
    finally { setBusy(false); }
  }

  if (!user) return <Auth onLogin={login} message={verifyMessage} />;

  function renderExercise(exercise, step, heading) {
    return <ExerciseCard key={exercise.id} exercise={exercise} step={step} heading={heading}
      languageName={home.profile.languageCode === 'de' ? 'German' : 'Danish'}
      answer={answers[exercise.id]} setAnswer={value => setAnswers(current => ({ ...current, [exercise.id]: value }))}
      selfCheck={selfChecks[exercise.id]} setSelfCheck={value => setSelfChecks(current => ({ ...current, [exercise.id]: value }))}
      result={results[exercise.id]} busy={busy} onSubmit={submitExercise} onSaveCard={saveMissAsCard} />;
  }

  const filteredLessons = (home?.lessons || []).filter(item =>
    (audioFilter === 'ALL' || item.audio?.mediaType === audioFilter)
    && (bandFilter === 'ALL' || item.audio?.editorialBand === bandFilter));
  const recommendedAudio = filteredLessons.find(item => item.id === home?.recommendedAudioLessonId) || null;
  const lesson = filteredLessons.find(item => item.id === selectedLessonId) || recommendedAudio || filteredLessons[0];
  const percent = lesson ? Math.round(lesson.mastery * 100) : 0;
  const dueCount = cards.filter(card => card.due).length + (home?.lessons || []).filter(item => item.due && !item.audio).length;
  const startingRoute = home?.profile?.startingRoute;
  const orderedExercises = lesson?.exercises ? [...lesson.exercises].sort((a, b) => {
    if (startingRoute === 'CHALLENGE') return (a.taskType === 'TRANSFER' ? -1 : 0) - (b.taskType === 'TRANSFER' ? -1 : 0) || a.position - b.position;
    return a.position - b.position;
  }) : [];

  return <main className="app-shell">
    <header className="site-header">
      <Brand />
      <div className="account"><ThemeSwitch /><button type="button" className="link account-name" onClick={() => setTab('profile')}>{user.name}</button><button className="link" onClick={logout}>Sign out</button></div>
    </header>

    {error && <p role="alert" className="banner error">{error}</p>}
    {notice && <p role="status" className="banner notice">{notice}</p>}
    {verifyMessage && <p role="status" className="banner notice">{verifyMessage}</p>}
    {user.emailVerified === 'false' && <p role="status" className="banner notice">Please confirm your email: we sent a link to {user.email}. <button className="link" onClick={resendVerification}>Send a new link</button></p>}
    {loading && !home && <p role="status" className="loading">Loading your Echo workspace…</p>}

    {home && !home.onboarded && <section className="onboarding panel">
      <p className="eyebrow">ECHO · YOUR LANGUAGE START</p>
      <h1>Start with what you want to say.</h1>
      <p className="intro">Choose a language and tell Echo what you want to be able to express. Your self-described experience sets a gentle starting point.</p>
      <form onSubmit={start}>
        <label>Language<select value={languageCode} onChange={event => setLanguageCode(event.target.value)}><option value="da">Danish</option><option value="de">German</option></select></label>
        <label>What would you like to express in {languageCode === 'da' ? 'Danish' : 'German'}?<textarea required maxLength="300" value={goal} onChange={event => setGoal(event.target.value)} /></label>
        <label>How familiar does {languageCode === 'da' ? 'Danish' : 'German'} feel today?
          <select value={selfReportedLevel} onChange={event => setSelfReportedLevel(event.target.value)}>
            <option value="new">I’m new to {languageCode === 'da' ? 'Danish' : 'German'}</option><option value="some">I know a few things</option><option value="comfortable">I can manage simple conversations</option>
          </select>
        </label>
        <button disabled={busy}>{busy ? 'Preparing your route…' : 'Build my starting route'}</button>
      </form>
    </section>}

    {home?.onboarded && <>
      <section className="welcome">
        <div className="welcome-row"><div><p className="eyebrow">ECHO · {home.profile.languageCode === 'de' ? 'GERMAN' : 'DANISH'} · LEARN THROUGH EXPRESSION</p>
        <h1>Make the pattern yours.</h1>
        <p className="intro">{home.nextAction}</p></div>{lesson && <figure className="depth-mark">
          <DeepwaterMark3D size={170} depth={lesson.mastery} label={`Depth gauge: ${percent}% mastered in ${lesson.title}`} />
          <figcaption>{percent}% deep in this lesson</figcaption>
        </figure>}<label className="language-switch">Learning language<select value={home.profile.languageCode} onChange={event => chooseLanguage(event.target.value)}><option value="da">Danish</option><option value="de">German</option></select></label></div>
        <div className="route-note"><b>{startingRoute} route</b><span>{ROUTE_COPY}</span></div>
      </section>

      <nav className="learn-tabs" aria-label="Echo sections">
        {[['today', 'Today'], ['practice', 'Practice'], ['cards', 'My cards'], ['progress', 'Progress'], ['profile', 'Profile']].map(([id, label]) =>
          <button type="button" key={id} className={tab === id ? 'active' : ''} aria-current={tab === id ? 'page' : undefined}
            onClick={() => setTab(id)}>{label}{id === 'cards' && cards.some(card => card.due) ? ` · ${cards.filter(card => card.due).length}` : ''}</button>)}
      </nav>

      {!reviewing && tab !== 'profile' && <div className="review-launch">
        <div><b>{dueCount ? `${dueCount} ${dueCount === 1 ? 'thing is' : 'things are'} ready to review` : 'Your memory is up to date'}</b>
          <span>{dueCount ? 'A short mixed session of cards and exercises. About five minutes.' : 'Practise anyway, or add a card from something you heard today.'}</span></div>
        <button type="button" onClick={() => setReviewing(true)}>Start daily review</button>
      </div>}

      {reviewing && <DailyReview lessons={home.lessons} cards={cards} languageCode={home.profile.languageCode} results={results}
        renderExercise={renderExercise} onReviewCard={reviewCard} busy={busy} onClose={() => setReviewing(false)} />}

      {warmUp && <MatchingWarmUp cards={cards} languageCode={home.profile.languageCode} onClose={() => setWarmUp(false)} />}

      {tab === 'profile' && <ProfilePage home={home} cards={cards} onSaved={saved => {
        const next = { ...user, name: saved.name }; setUser(next); sessionStorage.setItem('deepwater-user', JSON.stringify(next));
      }} />}

      {tab === 'practice' && <PracticeHub lessons={home.lessons} renderExercise={renderExercise} onOpenLesson={openLesson}
        dueCards={cards.filter(card => card.due).length} onReviewCards={() => setTab('cards')}
        canWarmUp={cards.length >= 3} onWarmUp={() => setWarmUp(true)} />}

      {tab === 'cards' && <PhraseCards cards={cards} languageCode={home.profile.languageCode} languageName={home.profile.languageCode === 'de' ? 'German' : 'Danish'}
        busy={busy} onSave={saveCard} onReview={reviewCard} onDelete={deleteCard} />}

      {tab === 'progress' && <CalmProgress home={home} cards={cards} />}

      {tab === 'today' && <div className="workspace-grid">
        <section className="lesson panel">
          <div className="audio-library-filters">
            <div><span className="eyebrow">LEARN WITH REAL VOICES</span><h3>Music &amp; podcasts</h3></div>
            <label>Medium<select value={audioFilter} onChange={event => setAudioFilter(event.target.value)}><option value="ALL">All lessons</option><option value="MUSIC">Music</option><option value="PODCAST">Podcasts</option></select></label>
            <label>Suggested level<select value={bandFilter} onChange={event => setBandFilter(event.target.value)}><option value="ALL">All levels</option>{Object.entries(BAND_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select></label>
          </div>
          {home.lessons.length > 1 && filteredLessons.length > 0 && <label className="lesson-picker">Choose a lesson<select value={lesson?.id || ''} onChange={event => setSelectedLessonId(event.target.value)}>{filteredLessons.map(item => <option key={item.id} value={item.id}>{item.audio ? `${item.audio.mediaType} · ${BAND_LABELS[item.audio.editorialBand]} · ` : ''}{item.title}</option>)}</select></label>}
          {filteredLessons.length === 0 && <div className="audio-empty"><b>{audioFilter === 'ALL' ? 'No approved lessons for this language yet.' : `No approved ${audioFilter.toLowerCase()} lessons in this level yet.`}</b><p>Your teacher is building a reviewed collection. Choose another filter, or return when new material is approved.</p></div>}
          {lesson && <>
            <div className="lesson-topline"><span className="pill">{lesson.level}</span><span className="pill soft">{lesson.topic}</span></div>
            <h2>{lesson.title}</h2>
            <p className="objective">{lesson.objective}</p>
            {lesson.audio && <section className="audio-experience" aria-labelledby={`audio-title-${lesson.id}`}>
              <div className="audio-heading"><div><p className="eyebrow">{lesson.audio.mediaType === 'MUSIC' ? 'ECHO · MUSIC LISTENING' : 'ECHO · PODCAST LISTENING'}</p><h3 id={`audio-title-${lesson.id}`}>{lesson.audio.sourceTitle}</h3><p>By {lesson.audio.creator}</p></div><span className="pill">{BAND_LABELS[lesson.audio.editorialBand]}</span></div>
              {home.recommendedAudioLessonId === lesson.id && <div className="audio-reason"><b>Why Echo suggested this</b><p>{home.recommendedAudioReason}</p></div>}
              <p className="audio-tags">{lesson.audio.topicTags}</p>
              <p className="audio-context"><b>Before you listen</b><br />{lesson.audio.beforeListeningPrompt}</p>
              <div className="audio-demands" aria-label="Separate listening-demand signals">
                {[['Speech rate',lesson.audio.speechRate],['Vocabulary',lesson.audio.vocabularyDensity],['Idioms',lesson.audio.idiomDensity],['Sound detail',lesson.audio.pronunciationLoad],['Topic familiarity',lesson.audio.topicFamiliarity]].map(([label,value]) => <span key={label}><small>{label}</small><b>{value}/5</b></span>)}
              </div>
              <div className="audio-player">
                {lesson.audio.playbackUrl ? <audio controls preload="none" src={lesson.audio.playbackUrl}>Your browser cannot play this audio.</audio> : <p>Open the source to listen, then return here for each guided step.</p>}
                <a href={lesson.audio.sourceUrl} target="_blank" rel="noreferrer">Open original {lesson.audio.mediaType === 'MUSIC' ? 'track' : 'episode'} ↗</a>
              </div>
              {lesson.audio.transcriptAllowed && lesson.audio.transcriptText && <div className="transcript-control">{(audioStages[lesson.id] || 'GIST') === 'GIST' && !transcriptShown[lesson.id] && <p>Listen for the gist first. The reviewed transcript unlocks after this step.</p>}{((audioStages[lesson.id] || 'GIST') !== 'GIST' || transcriptShown[lesson.id]) && <button type="button" className="link" aria-expanded={Boolean(transcriptShown[lesson.id])} onClick={() => setTranscriptShown(current => ({ ...current, [lesson.id]: !current[lesson.id] }))}>{transcriptShown[lesson.id] ? 'Hide transcript' : 'Reveal reviewed transcript'}</button>}{transcriptShown[lesson.id] && <blockquote>{lesson.audio.transcriptText}</blockquote>}</div>}
              {!lesson.audio.transcriptAllowed && <p className="transcript-note">No transcript is included for this source. Try listening first, then describe the idea in your own words.</p>}
              <ol className="audio-steps" aria-label="Audio learning steps">{['GIST','PHRASE','STRUCTURE','TRANSFER'].map((step,index) => <li key={step} className={(audioStages[lesson.id] || 'GIST') === step ? 'current' : ['GIST','PHRASE','STRUCTURE','TRANSFER'].indexOf(audioStages[lesson.id] || 'GIST') > index ? 'complete' : ''}>{index + 1}. {step === 'GIST' ? 'First listen' : step === 'PHRASE' ? 'Notice a phrase' : step === 'STRUCTURE' ? 'Notice the pattern' : 'Use it yourself'}</li>)}</ol>
              {audioStages[lesson.id] === 'DONE' ? <div className="audio-done" role="status"><b>Listening loop complete.</b><p>Your notes were used to guide this session. Echo saved the step, transcript support, and confidence—not your free-text response.</p><button type="button" onClick={() => { setAudioStages(current => ({ ...current, [lesson.id]: 'GIST' })); setTranscriptShown(current => ({ ...current, [lesson.id]: false })); }}>Listen and practise again</button></div> : <form className="audio-response" onSubmit={event => submitAudioStep(event, lesson, audioStages[lesson.id] || 'GIST')}>
                <label>{(audioStages[lesson.id] || 'GIST') === 'GIST' ? lesson.audio.gistPrompt : (audioStages[lesson.id] || 'GIST') === 'PHRASE' ? 'Write a phrase you noticed, or paraphrase it in your own words.' : (audioStages[lesson.id] || 'GIST') === 'STRUCTURE' ? lesson.audio.focusPrompt : 'Create a new, original expression using the pattern. Do not copy source lines.'}<textarea required maxLength="2000" value={audioAnswers[lesson.id] || ''} onChange={event => setAudioAnswers(current => ({ ...current, [lesson.id]: event.target.value }))} /></label>
                <label>How confident did you feel?<select value={audioConfidence[lesson.id] || 3} onChange={event => setAudioConfidence(current => ({ ...current, [lesson.id]: event.target.value }))}>{[1,2,3,4,5].map(value => <option value={value} key={value}>{value} · {value === 1 ? 'not sure' : value === 5 ? 'very sure' : 'somewhat sure'}</option>)}</select></label>
                <button disabled={busy}>{busy ? 'Saving…' : 'Save this listening step'}</button>
              </form>}
              {audioResults[lesson.id] && <p className="audio-saved" role="status">{audioResults[lesson.id].feedback}</p>}
              <small>Listening and grammar progress are tracked separately. This activity does not grade your accent or automatically decide whether your interpretation is correct.</small>
            </section>}
            <div className="explanation"><span className="eyebrow">NOTICE THE STRUCTURE</span><p>{lesson.explanation}</p></div>
            <div className="source-note"><b>Lesson source</b><span>{lesson.sourceReference}</span><small>{lesson.contentStatus}. No private class messages, song lyrics, or unlicensed images are included.</small></div>
            {!lesson.audio && <div className="exercise-list"><h3>Try the pattern</h3>
              {orderedExercises.map((exercise, index) => renderExercise(exercise, index + 1))}
            </div>}
          </>}
          {!lesson && <div className="empty-course"><span className="eyebrow">{home.profile.languageCode === 'de' ? 'GERMAN' : 'DANISH'} COURSE</span><h2>Your course is waiting for teacher review.</h2><p>{home.nextAction} Your teacher adds source material, checks every explanation and exercise, then publishes it here. No unreviewed or AI-generated lessons are shown as course content.</p></div>}
        </section>

        <aside className="progress panel">
          <p className="eyebrow">YOUR LEARNING SIGNALS</p><h2>Progress, made visible.</h2>
          <div className="mastery"><div className="mastery-label"><span>{lesson?.topic || 'Your first learning skill'}</span><b>{percent}%</b></div><div className="progress-track"><span style={{ width: `${percent}%` }} /></div><small>A practice estimate, not a fluency or CEFR score.</small></div>
          <div className="stat-grid"><div><strong>{home.totalAttempts}</strong><span>graded attempts</span></div><div><strong>{home.correctAttempts}</strong><span>correct answers</span></div><div><strong>{home.audioAttempts}</strong><span>audio reflections</span></div></div>
          <div className="review-card"><span className="eyebrow">NEXT REVIEW</span><b>{lesson?.due ? 'Ready when you are' : lesson?.nextReviewAt ? formatDate(lesson.nextReviewAt) : 'After your first check'}</b><p>{lesson?.due ? 'This pattern is ready for another short practice.' : 'Correct answers extend the interval; a missed answer brings it closer.'}</p></div>
          <div className="method-note"><b>How Echo adapts</b><p>Your chosen experience level sets a guided start. Correct and missed answers update a simple practice estimate and a 1 / 3 / 7 / 14-day review interval. Your free-writing example is saved without automatic grading.</p></div>
        </aside>
      </div>}
      {user.role === 'TEACHER' && <section className="teacher panel">
        <div className="teacher-heading"><div><p className="eyebrow">TEACHER WORKSPACE</p><h2>Review and publish Echo lessons</h2><p>Only your configured teacher account can save drafts or approve publication.</p></div><button className="practice-button" onClick={async () => { setTeacherMode(!teacherMode); if (!teacherMode) { try { await loadTeacherLessons(); } catch (e) { setError(e.message); } } }}>{teacherMode ? 'Hide workspace' : 'Open workspace'}</button></div>
        {teacherMode && <div className="teacher-content">
          <div><h3>Lesson queue</h3>{teacherLessons.length === 0 ? <p>No teacher drafts yet.</p> : teacherLessons.map(item => <article className="teacher-item" key={item.id}><div className="teacher-review"><b>{item.title} · v{item.version}</b><span>{item.languageCode.toUpperCase()} · {item.level} · {item.reviewStatus}</span><code>{item.id}</code><small><b>Source:</b> {item.sourceReference}</small><p>{item.objective}</p><p>{item.explanation}</p>{item.audio && <div className="teacher-exercise"><b>{item.audio.mediaType} · {item.audio.sourceTitle}</b><small>By {item.audio.creator} · {BAND_LABELS[item.audio.editorialBand]} · {item.audio.topicTags}</small><small>Rights: {item.audio.rightsBasis} · link {item.audio.linkAllowed ? 'allowed' : 'not allowed'} · playback {item.audio.playbackAllowed ? 'allowed' : 'not allowed'} · transcript {item.audio.transcriptAllowed ? 'allowed' : 'not allowed'}</small><small>Rights evidence: {item.audio.rightsReference}{item.audio.rightsExpiresAt ? ` · through ${item.audio.rightsExpiresAt}` : ''}</small><small>Before: {item.audio.beforeListeningPrompt} · Gist: {item.audio.gistPrompt} · Focus: {item.audio.focusPrompt}</small>{item.audio.transcriptText && <details><summary>Review transcript</summary><p>{item.audio.transcriptText}</p></details>}</div>}{item.exercises.map(exercise => <div className="teacher-exercise" key={exercise.position}><b>{exercise.position}. {exercise.taskType}: {exercise.prompt}</b>{exercise.options.map(option => <small key={option.key}>{option.key}. {option.text}{option.key === exercise.expectedAnswer ? ' · expected answer' : ''}</small>)}{exercise.taskType !== 'TRANSFER' && !exercise.options.length && <small>Expected answer: {exercise.expectedAnswer}</small>}{exercise.acceptedAnswers.length > 0 && <small>Also accepted: {exercise.acceptedAnswers.join(' · ')}</small>}<small>Correct feedback: {exercise.feedbackCorrect || '—'}</small><small>Hint: {exercise.feedbackIncorrect || '—'}</small></div>)}</div>{item.reviewStatus === 'DRAFT' && <button onClick={() => publishDraft(item.id)}>I approve & publish</button>}{item.reviewStatus === 'PUBLISHED' && <button onClick={() => unpublishLesson(item)}>Unpublish</button>}{item.reviewStatus === 'UNPUBLISHED' && <button onClick={() => publishDraft(item.id)}>Publish again</button>}</article>)}</div>
          <AudioDraftForm draft={audioDraft} update={updateAudioDraft} busy={busy} onSubmit={saveAudioLessonDraft} />
          <form onSubmit={saveDraft}><h3>Add a lesson draft</h3><p>Paste lesson content as JSON. It stays private until you press “I approve &amp; publish”.</p><textarea required value={draftJson} onChange={event => setDraftJson(event.target.value)} placeholder={'{\n  "languageCode": "de",\n  "title": "Verb-second after a time phrase",\n  ...\n}'} /><button>Save as draft</button><small>Shape: languageCode, title, level, topic, objective, explanation, sourceReference, skillKey, exercises[]. See TEACHER_GUIDE.md for a complete example.</small></form>
        </div>}
      </section>}
    </>}
    <DeleteAccount email={user.email} onDeleted={logout} />
    <footer>Echo by Deepwater · Go deeper.</footer>
  </main>;
}

function DeleteAccount({ email, onDeleted }) {
  const [confirmEmail, setConfirmEmail] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function remove(event) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      const response = await fetch(`${API}/api/v1/auth/account`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${sessionStorage.getItem('deepwater-token')}` },
        body: JSON.stringify({ confirmEmail }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.detail || body.message || 'Your account could not be deleted.');
      }
      onDeleted();
    } catch (deleteError) { setError(deleteError.message); } finally { setBusy(false); }
  }

  return <details className="delete-account">
    <summary>Delete my account</summary>
    <form onSubmit={remove}>
      <p>This permanently deletes your account, learning profile, answers and progress. It cannot be undone.</p>
      <label>Type <b>{email}</b> to confirm<input required type="email" autoComplete="off" value={confirmEmail} onChange={event => setConfirmEmail(event.target.value)} /></label>
      <button disabled={busy || confirmEmail.trim().toLowerCase() !== email.toLowerCase()}>{busy ? 'Deleting…' : 'Delete permanently'}</button>
      {error && <p role="alert" className="error">{error}</p>}
    </form>
  </details>;
}

applyTheme(readTheme());
createRoot(document.getElementById('root')).render(<App />);

import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import DeepwaterMark3D from './DeepwaterMark3D.jsx';
import ExerciseCard from './ExerciseCard.jsx';
import PracticeHub from './PracticeHub.jsx';
import PhraseCards, { MatchingWarmUp } from './PhraseCards.jsx';
import { scheduleNote } from './cardTools.js';
import CoursePath from './CoursePath.jsx';
import CalmProgress from './CalmProgress.jsx';
import DailyReview from './DailyReview.jsx';
import ProfilePage from './ProfilePage.jsx';
import Harbor from './Harbor.jsx';
import PolishExam from './PolishExam.jsx';
import Amber from './amber/Amber.jsx';
import Auth from './Auth.jsx';
import AppHeader from './AppHeader.jsx';
import DeleteAccount from './DeleteAccount.jsx';
import { BRANDS, useBrand } from './brand.jsx';
import TeacherWorkspace from './teacher/TeacherWorkspace.jsx';
import { ErrorBoundary, ErrorState, Loading, Notice, Segmented, Tabs, useDraft } from './ui/index.jsx';
import { BAND_LABELS } from './constants.js';
import { ECHO_LANGUAGES, PRODUCT_IDS, languageName, productName } from './app/config.js';
import { SESSION_EXPIRED, gql, request } from './app/http.js';
import { KEYS, read, readChoice, session, write } from './app/storage.js';
import { applyTheme, readTheme } from './app/theme.jsx';
import { formatDate } from './api.js';
import { CARD_FIELDS, ECHO_HOME_FIELDS } from './echoQueries.js';
import './style.css';

/** True when the learner last chose Polish in Echo (or used the old separate Polish B1 tab). */
function readPolishMode() {
  return read(KEYS.echoLanguage) === 'pl' || read(KEYS.product) === 'polish';
}

function EchoLanguageBar({ value, onChange }) {
  return <div className="echo-languages">
    <span className="eyebrow">Language</span>
    <Segmented label="Echo languages" value={value} onChange={onChange}
      options={ECHO_LANGUAGES.map(option => ({ id: option.code, label: <>{option.name}{option.hint && <small>{option.hint}</small>}</> }))} />
  </div>;
}

function readProduct() {
  return readChoice(KEYS.product, PRODUCT_IDS, 'echo');
}

const HOME_QUERY = `query EchoHome { echoHome { ${ECHO_HOME_FIELDS} } }`;

const ROUTE_COPY = 'We’ll begin with the experience level you chose and adjust as you practise.';

function App() {
  const [user, setUser] = useState(() => (session.token() ? session.user() : null));
  const [home, setHome] = useState(null);
  const [goal, setGoal, clearGoal] = useDraft('echo-goal', '');
  const [languageCode, setLanguageCode] = useState('da');
  const [selfReportedLevel, setSelfReportedLevel] = useState('new');
  const [answers, setAnswers] = useState({});
  const [selfChecks, setSelfChecks] = useState({});
  const [results, setResults] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [homeError, setHomeError] = useState(null);
  const [signedOutMessage, setSignedOutMessage] = useState('');
  const [verifyMessage, setVerifyMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [teacherLessons, setTeacherLessons] = useState([]);
  const [selectedLessonId, setSelectedLessonId] = useState('');
  const [audioFilter, setAudioFilter] = useState('ALL');
  const [bandFilter, setBandFilter] = useState('ALL');
  const [audioStages, setAudioStages] = useState({});
  const [audioAnswers, setAudioAnswers] = useState({});
  const [audioConfidence, setAudioConfidence] = useState({});
  const [transcriptShown, setTranscriptShown] = useState({});
  const [audioResults, setAudioResults] = useState({});
  const [tab, setTab] = useState('today');
  const [product, setProduct] = useState(readProduct);
  const [polishMode, setPolishMode] = useState(readPolishMode);
  useEffect(() => { write(KEYS.echoLanguage, polishMode ? 'pl' : 'course'); }, [polishMode]);
  const [brand] = useBrand();
  useEffect(() => { write(KEYS.product, product); }, [product]);
  const [cards, setCards] = useState([]);
  const [warmUp, setWarmUp] = useState(false);
  const [reviewing, setReviewing] = useState(false);

  function login(account) {
    session.save(account);
    setUser(account); setSignedOutMessage('');
  }

  // Any call that finds the session expired signs out here, with a sentence that says why.
  useEffect(() => {
    const expired = () => { if (session.token()) { logout(); setSignedOutMessage('Your session has ended. Please sign in again.'); } };
    window.addEventListener(SESSION_EXPIRED, expired);
    return () => window.removeEventListener(SESSION_EXPIRED, expired);
  }, []);

  /** Switching product starts clean: a banner from Echo does not follow you into Harbor. */
  function chooseProduct(next) {
    setError(''); setNotice(''); setProduct(next);
  }

  // The confirmation email links here with ?verify_email=<token>.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('verify_email');
    if (!token) return;
    window.history.replaceState({}, '', window.location.pathname);
    request('/api/v1/auth/verify-email', { method: 'POST', body: { token }, auth: false }).then(() => {
      setVerifyMessage('Your email is confirmed.');
      setUser(current => {
        if (!current) return current;
        const updated = { ...current, emailVerified: 'true' };
        session.saveUser(updated);
        return updated;
      });
    }).catch(verifyError => setVerifyMessage(verifyError.kind === 'client' ? verifyError.message || 'This confirmation link did not work.' : verifyError.message));
  }, []);

  async function resendVerification() {
    setVerifyMessage('');
    try {
      await request('/api/v1/auth/verify-email/resend', { method: 'POST' });
      setVerifyMessage(`A new link is on its way to ${user.email}.`);
    } catch (resendError) { setVerifyMessage(resendError.message || 'A new link could not be sent.'); }
  }

  function logout() {
    session.clear();
    setUser(null); setHome(null); setResults({}); setError(''); setNotice('');
  }

  async function loadHome() {
    setHomeError(null);
    try { setHome((await gql(HOME_QUERY)).echoHome); }
    catch (loadError) { setHomeError(loadError); }
  }

  useEffect(() => { if (user) loadHome(); }, [user]);

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

  async function saveCards(pairs) {
    setBusy(true); setError(''); setNotice('');
    try {
      const saved = (await gql(`mutation($cards:[EchoCardInput!]!){ saveEchoCards(cards:$cards) { id } }`, { cards: pairs })).saveEchoCards;
      await loadCards(); setNotice(`${saved.length} card${saved.length === 1 ? '' : 's'} saved. They’re in today’s review.`);
      return true;
    } catch (saveError) { setError(saveError.message); return false; }
    finally { setBusy(false); }
  }

  // grade is MISSED, CLOSE or KNEW; answer is the learner's wrong attempt, kept for "Keeps slipping".
  async function reviewCard(card, grade, answer = null) {
    setBusy(true); setError('');
    try {
      const reviewed = (await gql(`mutation($id:ID!,$grade:CardGrade!,$answer:String){ reviewEchoCard(cardId:$id, grade:$grade, answer:$answer) { box } }`,
        { id: card.id, grade, answer })).reviewEchoCard;
      setNotice(scheduleNote(grade, reviewed));
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
        startEcho(goal: $goal, languageCode: $languageCode, selfReportedLevel: $level) { ${ECHO_HOME_FIELDS} }
      }`, { goal, languageCode, level: selfReportedLevel });
      setHome(data.startEcho); clearGoal(); setNotice(`${languageName(languageCode)} is selected. Your starting route uses your self-described experience; this is not a formal level test.`);
    } catch (startError) { setError(startError.message); }
    finally { setBusy(false); }
  }

  async function loadTeacherLessons() {
    const data = await gql(`query { teacherLessons { id languageCode title level topic objective explanation sourceReference skillKey reviewStatus version revisionOf exercises { position taskType prompt expectedAnswer acceptedAnswers feedbackCorrect feedbackIncorrect options { key text } } audio { mediaType sourceTitle creator sourceUrl playbackUrl editorialBand topicTags speechRate vocabularyDensity idiomDensity pronunciationLoad topicFamiliarity beforeListeningPrompt gistPrompt focusPrompt transcriptAllowed transcriptText rightsBasis linkAllowed playbackAllowed rightsReference rightsExpiresAt } } }`);
    setTeacherLessons(data.teacherLessons);
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

  /** The Echo language bar: Polish opens the B1 exam practice; Danish and German open the course. */
  function pickEchoLanguage(code) {
    setError(''); setNotice('');
    if (code === 'pl') { setPolishMode(true); setReviewing(false); setWarmUp(false); return; }
    setPolishMode(false);
    if (home?.onboarded && home.profile.languageCode !== code) chooseLanguage(code);
    else if (!home?.onboarded) setLanguageCode(code);
  }

  async function chooseLanguage(code) {
    setError(''); setNotice(''); setLanguageCode(code);
    try {
      const data = await gql(`mutation($languageCode: String!) { chooseEchoLanguage(languageCode: $languageCode) { ${ECHO_HOME_FIELDS} } }`, { languageCode: code });
      setHome(data.chooseEchoLanguage); setNotice(`Switched to ${languageName(code)}.`);
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

  if (!user) return <Auth onLogin={login} message={signedOutMessage || verifyMessage} onChooseProduct={setProduct} />;

  function renderExercise(exercise, step, heading) {
    return <ExerciseCard key={exercise.id} exercise={exercise} step={step} heading={heading}
      languageName={languageName(home.profile.languageCode)}
      answer={answers[exercise.id]} setAnswer={value => setAnswers(current => ({ ...current, [exercise.id]: value }))}
      selfCheck={selfChecks[exercise.id]} setSelfCheck={value => setSelfChecks(current => ({ ...current, [exercise.id]: value }))}
      result={results[exercise.id]} busy={busy} onSubmit={submitExercise} onSaveCard={saveMissAsCard} />;
  }

  const filteredLessons = (home?.lessons || []).filter(item =>
    (audioFilter === 'ALL' || item.audio?.mediaType === audioFilter)
    && (bandFilter === 'ALL' || item.audio?.editorialBand === bandFilter));
  const hasAudio = (home?.lessons || []).some(item => item.audio);
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
    <AppHeader product={product} onProduct={chooseProduct} productLabel={productName(product)} user={user}
      profileOpen={product === 'echo' && tab === 'profile'} onSignOut={logout}
      onProfile={() => { chooseProduct('echo'); setPolishMode(false); setReviewing(false); setWarmUp(false); setTab('profile'); }} />

    {error && <Notice tone="error" onDismiss={() => setError('')}>{error}</Notice>}
    {notice && <Notice tone="good" onDismiss={() => setNotice('')}>{notice}</Notice>}
    {verifyMessage && <Notice tone="good" onDismiss={() => setVerifyMessage('')}>{verifyMessage}</Notice>}
    {user.emailVerified === 'false' && <Notice>Please confirm your email: we sent a link to {user.email}. <button className="link" onClick={resendVerification}>Send a new link</button></Notice>}
    <ErrorBoundary name={productName(product)} resetKey={product}>
    {product === 'echo' && <>
    {!home && (homeError ? <ErrorState title="Echo didn’t load" error={homeError} onRetry={loadHome} /> : <Loading label="Loading your Echo workspace…" />)}
    {home && tab !== 'profile' && <EchoLanguageBar value={polishMode ? 'pl' : home.onboarded ? home.profile.languageCode : languageCode} onChange={pickEchoLanguage} />}
    {polishMode && tab !== 'profile' && <PolishExam examinerName={brand === 'nibble' ? 'Nibble' : 'The examiner'} />}

    {home && !polishMode && !home.onboarded && <section className="onboarding panel">
      <p className="eyebrow">ECHO · YOUR LANGUAGE START</p>
      <h1>Start with what you want to say.</h1>
      <p className="intro">Pick a language above and tell Echo what you want to be able to express. Your self-described experience sets a gentle starting point.</p>
      <form onSubmit={start}>
        <label>What would you like to express in {languageName(languageCode)}?<textarea required maxLength="300" value={goal} onChange={event => setGoal(event.target.value)} /></label>
        <label>How familiar does {languageName(languageCode)} feel today?
          <select value={selfReportedLevel} onChange={event => setSelfReportedLevel(event.target.value)}>
            <option value="new">I’m new to {languageName(languageCode)}</option><option value="some">I know a few things</option><option value="comfortable">I can manage simple conversations</option>
          </select>
        </label>
        <button disabled={busy}>{busy ? 'Preparing your route…' : 'Build my starting route'}</button>
      </form>
    </section>}

    {home?.onboarded && tab === 'profile' && <>
      <button type="button" className="link back-link" onClick={() => setTab('today')}>← Back to learning</button>
      <ProfilePage home={home} cards={cards} onSaved={saved => {
        const next = { ...user, name: saved.name }; setUser(next); session.saveUser(next);
      }} />
    </>}

    {home?.onboarded && !polishMode && tab !== 'profile' && <>
      <section className="welcome">
        <div className="welcome-row"><div><p className="eyebrow">ECHO · {languageName(home.profile.languageCode).toUpperCase()} · LEARN THROUGH EXPRESSION</p>
        <h1>Make the pattern yours.</h1>
        <p className="intro">{home.nextAction}</p></div>{lesson && <figure className="depth-mark">
          <DeepwaterMark3D size={170} depth={lesson.mastery} label={`Depth gauge: ${percent}% mastered in ${lesson.title}`} />
          <figcaption>{percent}% deep in this lesson</figcaption>
        </figure>}</div>
        <div className="route-note"><b>{startingRoute} route</b><span>{ROUTE_COPY}</span></div>
      </section>

      <Tabs label="Echo sections" value={tab} onChange={setTab} items={[
        { id: 'today', label: 'Today' }, { id: 'practice', label: 'Practice' },
        { id: 'cards', label: 'My cards', badge: cards.filter(card => card.due).length || '' }, { id: 'progress', label: 'Progress' },
      ]} />

      {!reviewing && <div className="review-launch">
        <div><b>{dueCount ? `${dueCount} ${dueCount === 1 ? 'thing is' : 'things are'} ready to review` : 'Your memory is up to date'}</b>
          <span>{dueCount ? 'A short mixed session of cards and exercises. About five minutes.' : 'Practise anyway, or add a card from something you heard today.'}</span></div>
        <button type="button" onClick={() => setReviewing(true)}>Start daily review</button>
      </div>}

      {reviewing && <DailyReview lessons={home.lessons} cards={cards} languageCode={home.profile.languageCode} results={results}
        renderExercise={renderExercise} onReviewCard={reviewCard} busy={busy} onClose={() => setReviewing(false)} />}

      {warmUp && <MatchingWarmUp cards={cards} languageCode={home.profile.languageCode} onClose={() => setWarmUp(false)} />}

      {tab === 'practice' && <PracticeHub lessons={home.lessons} renderExercise={renderExercise} onOpenLesson={openLesson}
        dueCards={cards.filter(card => card.due).length} onReviewCards={() => setTab('cards')}
        canWarmUp={cards.length >= 3} onWarmUp={() => setWarmUp(true)} />}

      {tab === 'cards' && <PhraseCards cards={cards} languageCode={home.profile.languageCode} languageName={languageName(home.profile.languageCode)}
        busy={busy} onSave={saveCard} onSaveMany={saveCards} onReview={reviewCard} onDelete={deleteCard} />}

      {tab === 'progress' && <CalmProgress home={home} cards={cards} />}

      {tab === 'today' && <CoursePath lessons={home.lessons} currentId={lesson?.id} onOpen={id => {
        openLesson(id); requestAnimationFrame(() => document.getElementById('lesson-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
      }} />}

      {tab === 'today' && <div className="workspace-grid">
        <section className="lesson panel" id="lesson-panel">
          {hasAudio && <div className="audio-library-filters">
            <div><span className="eyebrow">LEARN WITH REAL VOICES</span><h3>Music &amp; podcasts</h3></div>
            <label>Medium<select value={audioFilter} onChange={event => setAudioFilter(event.target.value)}><option value="ALL">All lessons</option><option value="MUSIC">Music</option><option value="PODCAST">Podcasts</option></select></label>
            <label>Suggested level<select value={bandFilter} onChange={event => setBandFilter(event.target.value)}><option value="ALL">All levels</option>{Object.entries(BAND_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select></label>
          </div>}
          {hasAudio && home.lessons.length > 1 && filteredLessons.length > 0 && <label className="lesson-picker">Choose a lesson<select value={lesson?.id || ''} onChange={event => setSelectedLessonId(event.target.value)}>{filteredLessons.map(item => <option key={item.id} value={item.id}>{item.audio ? `${item.audio.mediaType} · ${BAND_LABELS[item.audio.editorialBand]} · ` : ''}{item.title}</option>)}</select></label>}
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
          {!lesson && <div className="empty-course"><span className="eyebrow">{languageName(home.profile.languageCode).toUpperCase()} COURSE</span><h2>Your course is waiting for teacher review.</h2><p>{home.nextAction} Your teacher adds source material, checks every explanation and exercise, then publishes it here. No unreviewed or AI-generated lessons are shown as course content.</p></div>}
        </section>

        <aside className="progress panel">
          <p className="eyebrow">YOUR LEARNING SIGNALS</p><h2>Progress, made visible.</h2>
          <div className="mastery"><div className="mastery-label"><span>{lesson?.topic || 'Your first learning skill'}</span><b>{percent}%</b></div><div className="progress-track"><span style={{ width: `${percent}%` }} /></div><small>A practice estimate, not a fluency or CEFR score.</small></div>
          <div className="stat-grid"><div><strong>{home.totalAttempts}</strong><span>graded attempts</span></div><div><strong>{home.correctAttempts}</strong><span>correct answers</span></div><div><strong>{home.audioAttempts}</strong><span>audio reflections</span></div></div>
          <div className="review-card"><span className="eyebrow">NEXT REVIEW</span><b>{lesson?.due ? 'Ready when you are' : lesson?.nextReviewAt ? formatDate(lesson.nextReviewAt) : 'After your first check'}</b><p>{lesson?.due ? 'This pattern is ready for another short practice.' : 'Correct answers extend the interval; a missed answer brings it closer.'}</p></div>
          <div className="method-note"><b>How Echo adapts</b><p>Your chosen experience level sets a guided start. Correct and missed answers update a simple practice estimate and a 1 / 3 / 7 / 14-day review interval. Your free-writing example is saved without automatic grading.</p></div>
        </aside>
      </div>}
      {user.role === 'TEACHER' && <TeacherWorkspace lessons={teacherLessons} busy={busy} onPublish={publishDraft} onUnpublish={unpublishLesson}
        onOpen={async () => { try { await loadTeacherLessons(); } catch (loadError) { setError(loadError.message); } }} />}
    </>}
    </>}
    {product === 'harbor' && <Harbor />}
    {product === 'amber' && <Amber />}
    </ErrorBoundary>
    {product === 'echo' && home && (tab === 'profile' || (!home.onboarded && !polishMode)) && <DeleteAccount email={user.email} onDeleted={logout} />}
    <footer>{productName(product)} by {BRANDS[brand].name} · {BRANDS[brand].tagline}</footer>
  </main>;
}

applyTheme(readTheme());
createRoot(document.getElementById('root')).render(<App />);

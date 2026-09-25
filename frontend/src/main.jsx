import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

const API = import.meta.env.VITE_API_URL || 'http://127.0.0.1:8081';
const GRAPHQL = `${API}/graphql`;

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
      const endpoint = mode === 'register' ? 'register' : 'login';
      const response = await fetch(`${API}/api/v1/auth/${endpoint}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(mode === 'register' ? { name, email, password } : { email, password }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.detail || body.message || 'Sign in failed.');
      onLogin(body);
    } catch (error) { setError(error.message); } finally { setBusy(false); }
  }

  return <main className="auth-shell"><section className="access">
    <img className="logo hero-logo" src="/deepwater-logo-warm.png" alt="Deepwater" />
    <p className="eyebrow">THROUGH EXPRESSION, CONNECT THE WORLD</p>
    <h1>Learn a pattern.<br />Make it yours.</h1>
    <p>Echo helps you notice how Danish works, practise it in context, and return to it when it is useful.</p>
    <form onSubmit={submit}>
      {mode === 'register' && <label>Your name<input required maxLength="160" autoComplete="name" value={name} onChange={event => setName(event.target.value)} /></label>}
      <label>Email<input required type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} /></label>
      <label>Password<input required type="password" minLength="12" maxLength="72" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} /><small>Use 12–72 characters.</small></label>
      <button disabled={busy}>{busy ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Sign in'}</button>
    </form>
    {error && <p role="alert" className="error">{error}</p>}
    <button className="link" onClick={() => { setError(''); setMode(mode === 'register' ? 'login' : 'register'); }}>{mode === 'register' ? 'Already have an account? Sign in' : 'Create an account'}</button>
  </section></main>;
}

const HOME_QUERY = `query EchoHome {
  echoHome {
    onboarded nextAction totalAttempts correctAttempts
    profile { goal selfReportedLevel diagnosticScore startingRoute }
    lessons { id title level topic objective explanation sourceReference contentStatus mastery attempts nextReviewAt due
      exercises { id position taskType prompt lastCorrect options { key text } }
    }
  }
}`;

const DIAGNOSTIC = [
  { id: 'v2', prompt: 'Which sentence puts the finite verb second after the time phrase?', options: [['A', 'I dag jeg arbejder hjemme.'], ['B', 'I dag arbejder jeg hjemme.'], ['C', 'Jeg i dag arbejder hjemme.']] },
  { id: 'definite', prompt: 'The class note uses “en gade”. Which is its definite form?', options: [['A', 'gade'], ['B', 'gadeen'], ['C', 'gaden']] },
  { id: 'present', prompt: 'Which form is shown as the present form of “at være” in the notes?', options: [['A', 'være'], ['B', 'er'], ['C', 'var']] },
];

const ROUTE_COPY = {
  GUIDED: 'We’ll take a guided start and keep the pattern visible as you practise.',
  STANDARD: 'You have some of the pattern already. We’ll practise it, then try a fresh example.',
  CHALLENGE: 'Your starting check suggests you can move quickly to an independent example. It is a short check, not a CEFR placement.',
};

function App() {
  const [user, setUser] = useState(() => { try { return JSON.parse(sessionStorage.getItem('deepwater-user')); } catch { return null; } });
  const [home, setHome] = useState(null);
  const [goal, setGoal] = useState('Use Danish in everyday and work conversations');
  const [selfReportedLevel, setSelfReportedLevel] = useState('new');
  const [diagnosticAnswers, setDiagnosticAnswers] = useState({});
  const [answers, setAnswers] = useState({});
  const [selfChecks, setSelfChecks] = useState({});
  const [results, setResults] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);

  function login(account) {
    sessionStorage.setItem('deepwater-token', account.token);
    sessionStorage.setItem('deepwater-user', JSON.stringify(account));
    setUser(account);
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

  async function start(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const data = await gql(`mutation StartEcho($goal: String!, $level: String!, $answers: [DiagnosticAnswerInput!]!) {
        startEcho(goal: $goal, selfReportedLevel: $level, answers: $answers) {
          onboarded nextAction totalAttempts correctAttempts profile { goal selfReportedLevel diagnosticScore startingRoute }
          lessons { id title level topic objective explanation sourceReference contentStatus mastery attempts nextReviewAt due exercises { id position taskType prompt lastCorrect options { key text } } }
        }
      }`, { goal, level: selfReportedLevel, answers: DIAGNOSTIC.map(item => ({ id: item.id, answer: diagnosticAnswers[item.id] || '' })) });
      setHome(data.startEcho); setNotice('Your starting route is ready. The three-question check is a guide, not a formal level test.');
    } catch (startError) { setError(startError.message); }
    finally { setBusy(false); }
  }

  async function submitExercise(event, exercise) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const data = await gql(`mutation SubmitEchoPractice($exerciseId: ID!, $answer: String!, $selfCheck: Boolean) {
        submitEchoPractice(exerciseId: $exerciseId, answer: $answer, transferSelfCheck: $selfCheck) {
          exerciseId answer graded correct feedback mastery nextReviewAt transfer
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

  if (!user) return <Auth onLogin={login} />;

  const lesson = home?.lessons?.[0];
  const percent = lesson ? Math.round(lesson.mastery * 100) : 0;
  const startingRoute = home?.profile?.startingRoute;
  const orderedExercises = lesson?.exercises ? [...lesson.exercises].sort((a, b) => {
    if (startingRoute === 'CHALLENGE') return (a.taskType === 'TRANSFER' ? -1 : 0) - (b.taskType === 'TRANSFER' ? -1 : 0) || a.position - b.position;
    return a.position - b.position;
  }) : [];

  return <main className="app-shell">
    <header className="site-header">
      <img className="logo" src="/deepwater-logo-warm.png" alt="Deepwater" />
      <div className="account"><span>{user.name}</span><button className="link" onClick={logout}>Sign out</button></div>
    </header>

    {error && <p role="alert" className="banner error">{error}</p>}
    {notice && <p role="status" className="banner notice">{notice}</p>}
    {loading && !home && <p role="status" className="loading">Loading your Echo workspace…</p>}

    {home && !home.onboarded && <section className="onboarding panel">
      <p className="eyebrow">ECHO · YOUR DANISH START</p>
      <h1>Start with what you want to say.</h1>
      <p className="intro">Tell Echo what Danish should help you do. A quick check chooses a starting route; it does not assign a CEFR level.</p>
      <form onSubmit={start}>
        <label>What would you like to do in Danish?<textarea required maxLength="300" value={goal} onChange={event => setGoal(event.target.value)} /></label>
        <label>How familiar does Danish feel today?
          <select value={selfReportedLevel} onChange={event => setSelfReportedLevel(event.target.value)}>
            <option value="new">I’m new to Danish</option><option value="some">I know a few things</option><option value="comfortable">I can manage simple conversations</option>
          </select>
        </label>
        <div className="diagnostic"><h2>A short starting check</h2><p>Choose the answer that feels right. You can learn from every item.</p>
          {DIAGNOSTIC.map(item => <fieldset key={item.id}><legend>{item.prompt}</legend>{item.options.map(([key, text]) => <label className="choice" key={key}><input type="radio" name={item.id} required checked={diagnosticAnswers[item.id] === key} onChange={() => setDiagnosticAnswers(current => ({ ...current, [item.id]: key }))} /> <span>{text}</span></label>)}</fieldset>)}
        </div>
        <button disabled={busy}>{busy ? 'Preparing your route…' : 'Build my starting route'}</button>
      </form>
    </section>}

    {home?.onboarded && <>
      <section className="welcome">
        <p className="eyebrow">ECHO · LEARN THROUGH EXPRESSION</p>
        <h1>Make the pattern yours.</h1>
        <p className="intro">{home.nextAction}</p>
        <div className="route-note"><b>{startingRoute} route</b><span>{ROUTE_COPY[startingRoute] || ROUTE_COPY.GUIDED}</span></div>
      </section>

      <div className="workspace-grid">
        <section className="lesson panel">
          {lesson && <>
            <div className="lesson-topline"><span className="pill">{lesson.level}</span><span className="pill soft">{lesson.topic}</span></div>
            <h2>{lesson.title}</h2>
            <p className="objective">{lesson.objective}</p>
            <div className="explanation"><span className="eyebrow">NOTICE THE STRUCTURE</span><p>{lesson.explanation}</p></div>
            <div className="source-note"><b>Lesson source</b><span>{lesson.sourceReference}</span><small>{lesson.contentStatus}. No private class messages, song lyrics, or unlicensed images are included.</small></div>
            <div className="exercise-list"><h3>Try the pattern</h3>
              {orderedExercises.map((exercise, index) => <article className="exercise" key={exercise.id}>
                <div className="exercise-heading"><span className="step">{index + 1}</span><span>{exercise.taskType === 'TRANSFER' ? 'YOUR OWN EXAMPLE' : 'PRACTICE'}</span>{exercise.lastCorrect !== null && exercise.lastCorrect !== undefined && <span className={`attempt-state ${exercise.lastCorrect ? 'good' : 'retry'}`}>{exercise.lastCorrect ? 'Correct last time' : 'Try again'}</span>}</div>
                <h4>{exercise.prompt}</h4>
                <form onSubmit={event => submitExercise(event, exercise)}>
                  {exercise.taskType === 'SELECT' && <fieldset className="exercise-options"><legend className="visually-hidden">Choose one answer</legend>{exercise.options.map(option => <label className="choice" key={option.key}><input type="radio" name={exercise.id} required checked={answers[exercise.id] === option.key} onChange={() => setAnswers(current => ({ ...current, [exercise.id]: option.key }))} /><span><b>{option.key}.</b> {option.text}</span></label>)}</fieldset>}
                  {exercise.taskType !== 'SELECT' && <label className="answer-label">{exercise.taskType === 'TRANSFER' ? 'Your sentence' : 'Missing word'}
                    {exercise.taskType === 'TRANSFER' ? <textarea required maxLength="500" value={answers[exercise.id] || ''} onChange={event => setAnswers(current => ({ ...current, [exercise.id]: event.target.value }))} placeholder="Write a new sentence in Danish…" /> : <input required maxLength="200" value={answers[exercise.id] || ''} onChange={event => setAnswers(current => ({ ...current, [exercise.id]: event.target.value }))} autoComplete="off" />}
                  </label>}
                  {exercise.taskType === 'TRANSFER' && <label className="choice self-check"><input type="checkbox" checked={Boolean(selfChecks[exercise.id])} onChange={event => setSelfChecks(current => ({ ...current, [exercise.id]: event.target.checked }))} /><span>I checked that the finite verb is in second position.</span></label>}
                  <button className="practice-button" disabled={busy || (exercise.taskType === 'TRANSFER' && !selfChecks[exercise.id])}>{busy ? 'Saving…' : exercise.taskType === 'TRANSFER' ? 'Save my example' : 'Check answer'}</button>
                </form>
                {results[exercise.id] && <div className={`feedback ${results[exercise.id].graded && results[exercise.id].correct ? 'feedback-good' : ''}`} role="status"><b>{results[exercise.id].graded ? results[exercise.id].correct ? 'That’s it' : 'Let’s look again' : 'Saved for reflection'}</b><p>{results[exercise.id].feedback}</p>{results[exercise.id].graded && <small>Practice estimate: {Math.round(results[exercise.id].mastery * 100)}% · Next review {formatDate(results[exercise.id].nextReviewAt)}</small>}</div>}
              </article>)}
            </div>
          </>}
        </section>

        <aside className="progress panel">
          <p className="eyebrow">YOUR LEARNING SIGNALS</p><h2>Progress, made visible.</h2>
          <div className="mastery"><div className="mastery-label"><span>Word-order practice</span><b>{percent}%</b></div><div className="progress-track"><span style={{ width: `${percent}%` }} /></div><small>A practice estimate, not a fluency or CEFR score.</small></div>
          <div className="stat-grid"><div><strong>{home.totalAttempts}</strong><span>graded attempts</span></div><div><strong>{home.correctAttempts}</strong><span>correct answers</span></div></div>
          <div className="review-card"><span className="eyebrow">NEXT REVIEW</span><b>{lesson?.due ? 'Ready when you are' : lesson?.nextReviewAt ? formatDate(lesson.nextReviewAt) : 'After your first check'}</b><p>{lesson?.due ? 'This pattern is ready for another short practice.' : 'Correct answers extend the interval; a missed answer brings it closer.'}</p></div>
          <div className="method-note"><b>How Echo adapts</b><p>The starting check chooses a guided, standard, or challenge route. Correct and missed answers update a simple practice estimate and a 1 / 3 / 7 / 14-day review interval. Your free-writing example is saved without automatic grading.</p></div>
        </aside>
      </div>
    </>}
    <footer>Through expression, connect the world.</footer>
  </main>;
}

function formatDate(value) {
  if (!value) return 'tomorrow';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
}

createRoot(document.getElementById('root')).render(<App />);

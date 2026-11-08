import React, { useEffect, useRef, useState } from 'react';
import { API, formatDate } from './api.js';
import { canSpeak, speak } from './speech.js';
import { LEVEL_TEXT, answeredIn, appendDictation, lengthNote, progress } from './examTools.js';
import { Button, Notice, Panel, Pill, SectionHeading, Tabs } from './ui/index.jsx';

// Polish B1 practice exam: the five parts of the state exam, marked by the answer key and by an examiner
// (the AI examiner when the server has an API key, otherwise a fixed rubric that says so).

async function exam(path = '', { method = 'GET', body } = {}) {
  const token = sessionStorage.getItem('deepwater-token');
  const headers = { Authorization: `Bearer ${token}` };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${API}/api/v1/exam/polish-b1${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.detail || 'The request could not be completed.');
  return payload;
}

const MAX_PLAYS = 2;
const Recognition = typeof window === 'undefined' ? null : window.SpeechRecognition || window.webkitSpeechRecognition;

export default function PolishExam({ examinerName = 'The examiner' }) {
  const [data, setData] = useState(null);
  const [history, setHistory] = useState([]);
  const [answers, setAnswers] = useState({});
  const [view, setView] = useState('intro');
  const [partId, setPartId] = useState('listening');
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function load() {
    try {
      const [examData, attempts] = await Promise.all([exam(), exam('/attempts')]);
      setData(examData); setHistory(attempts); setError('');
    } catch (e) { setError(e.message); }
  }
  useEffect(() => { load(); }, []);

  const set = (id, value) => setAnswers(current => ({ ...current, [id]: value }));

  async function submit() {
    setBusy(true); setError('');
    try {
      const marked = await exam('/attempts', { method: 'POST', body: { answers } });
      setResult(marked); setView('result'); window.scrollTo?.({ top: 0, behavior: 'smooth' });
      setHistory(await exam('/attempts'));
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function openAttempt(id) {
    try { setResult(await exam(`/attempts/${encodeURIComponent(id)}`)); setView('result'); } catch (e) { setError(e.message); }
  }

  function restart() { setAnswers({}); setResult(null); setPartId('listening'); setView('exam'); }

  if (!data) return <main className="exam-shell">{error ? <Notice tone="error">{error}</Notice> : <p>Loading the practice exam…</p>}</main>;
  const { exam: content, passPercent, aiExaminer } = data;
  const part = content.parts.find(p => p.id === partId) || content.parts[0];
  const { done, total } = progress(content, answers);
  const partIndex = content.parts.indexOf(part);

  return <main className="exam-shell">
    {error && <Notice tone="error">{error}</Notice>}
    {view === 'intro' && <Intro content={content} passPercent={passPercent} aiExaminer={aiExaminer} examinerName={examinerName}
      history={history} onStart={() => setView('exam')} onOpen={openAttempt} />}

    {view === 'exam' && <Panel className="exam-panel">
      <div className="exam-top">
        <SectionHeading eyebrow="POLISH B1 · PRACTICE" title={`${part.english} · ${part.name}`} lead={part.instructions} />
        <span className="exam-progress" role="status">{done} of {total} answered</span>
      </div>
      <Tabs label="Exam parts" value={part.id} onChange={setPartId} items={content.parts.map(p => ({
        id: p.id, label: p.english, badge: `${answeredIn(p, answers)}/${p.items.length}`,
      }))} />
      {part.passages?.map(passage => <article className="exam-passage" key={passage.id}><b>{passage.title}</b><p lang="pl">{passage.text}</p></article>)}
      <ol className="exam-items">
        {part.items.map(item => <li key={item.id} className="exam-item">
          {part.kind === 'CHOICE'
            ? <ChoiceItem item={item} value={answers[item.id]} onChange={value => set(item.id, value)} />
            : <OpenItem item={item} speaking={part.kind === 'SPEAKING'} value={answers[item.id] || ''} onChange={value => set(item.id, value)} />}
        </li>)}
      </ol>
      <div className="exam-nav">
        <Button variant="quiet" disabled={partIndex === 0} onClick={() => setPartId(content.parts[partIndex - 1].id)}>← Previous part</Button>
        {partIndex < content.parts.length - 1
          ? <Button onClick={() => setPartId(content.parts[partIndex + 1].id)}>Next part →</Button>
          : <Button disabled={busy} onClick={submit}>{busy ? `${examinerName} is marking…` : 'Hand in for marking'}</Button>}
      </div>
      {partIndex === content.parts.length - 1 && done < total && <p className="exam-hint">{total - done} items have no answer yet; an empty answer scores 0.</p>}
    </Panel>}

    {view === 'result' && result && <Result result={result} content={content} examinerName={examinerName} onRestart={restart} onBack={() => setView('intro')} />}
  </main>;
}

function Intro({ content, passPercent, aiExaminer, examinerName, history, onStart, onOpen }) {
  return <Panel className="exam-panel exam-intro">
    <div className="exam-hero">
      <img src="/brand/nibble.svg" alt="" width="132" height="132" />
      <div>
        <p className="eyebrow">POLISH B1 · STATE EXAM PRACTICE</p>
        <h2>Are you ready for the Polish B1 certificate?</h2>
        <p>Five parts, as in the state exam: listening, grammar, reading, writing and speaking. {examinerName} marks every part and tells you whether you look ready. Each part needs {passPercent}% or more.</p>
        <Button onClick={onStart}>Start the practice exam</Button>
      </div>
    </div>
    <ul className="exam-parts">{content.parts.map(p => <li key={p.id}><b>{p.english}</b><span lang="pl">{p.name}</span><small>{p.items.length} {p.kind === 'CHOICE' ? 'questions' : 'tasks'}</small></li>)}</ul>
    <Notice tone="info">{content.notice}</Notice>
    <p className="exam-hint">{aiExaminer
      ? 'Writing and speaking are marked by an AI examiner. It can be wrong, so treat the mark as advice, not a grade.'
      : 'The AI examiner is switched off on this server, so writing and speaking get a rough estimate from fixed rules (length, linking words, word variety). It is labelled as such in your result.'}</p>
    {history.length > 0 && <div className="exam-history"><h3>Your earlier attempts</h3>
      <ul>{history.map(a => <li key={a.id}><button type="button" className="link" onClick={() => onOpen(a.id)}>{formatDate(a.createdAt)}</button>
        <span>{a.averagePercent}% · {LEVEL_TEXT[a.levelEstimate] || a.levelEstimate}</span>
        <Pill tone={a.readyForB1 ? 'good' : 'warn'}>{a.readyForB1 ? 'Looks ready' : 'Not yet'}</Pill></li>)}</ul></div>}
  </Panel>;
}

function ChoiceItem({ item, value, onChange }) {
  const [plays, setPlays] = useState(0);
  return <fieldset className="exam-choice">
    <legend lang="pl">{item.prompt}</legend>
    {item.audio && <div className="exam-audio">
      {canSpeak
        ? <Button variant="quiet" disabled={plays >= MAX_PLAYS} onClick={() => { speak(item.audio, 'pl', 0.85); setPlays(plays + 1); }}>
            ▶ Play recording {plays ? `(${MAX_PLAYS - plays} left)` : ''}</Button>
        : <p className="exam-hint">Your browser cannot read aloud, so here is the text: <span lang="pl">{item.audio}</span></p>}
    </div>}
    {item.options.map((option, index) => <label key={option} className="choice">
      <input type="radio" name={item.id} checked={value === String(index)} onChange={() => onChange(String(index))} />
      <span lang="pl">{option}</span>
    </label>)}
  </fieldset>;
}

function OpenItem({ item, speaking, value, onChange }) {
  const [listening, setListening] = useState(false);
  const recognition = useRef(null);
  const latest = useRef(value);
  latest.current = value;

  function dictate() {
    if (listening) { recognition.current?.stop(); return; }
    const r = new Recognition();
    r.lang = 'pl-PL'; r.interimResults = false; r.continuous = true;
    r.onresult = event => {
      const spoken = Array.from(event.results).slice(event.resultIndex).map(res => res[0].transcript).join(' ');
      onChange(appendDictation(latest.current, spoken));
    };
    r.onend = () => setListening(false);
    r.onerror = () => setListening(false);
    recognition.current = r; r.start(); setListening(true);
  }
  useEffect(() => () => recognition.current?.stop(), []);

  return <div className="exam-open">
    <p className="exam-task" lang="pl">{item.prompt}</p>
    {speaking && Recognition && <Button variant="quiet" onClick={dictate} aria-pressed={listening}>{listening ? '■ Stop' : '🎙 Answer aloud'}</Button>}
    {speaking && !Recognition && <p className="exam-hint">Your browser cannot write down speech; type what you would say.</p>}
    <textarea lang="pl" rows={speaking ? 5 : 8} value={value} maxLength={4000} onChange={e => onChange(e.target.value)}
      aria-label={speaking ? 'Your spoken answer' : 'Your written answer'} placeholder={speaking ? 'What you say appears here. You can correct it.' : 'Pisz tutaj…'} />
    <small className="exam-count">{lengthNote(item, value)}</small>
  </div>;
}

function Result({ result, content, examinerName, onRestart, onBack }) {
  const items = Object.fromEntries(content.parts.flatMap(p => p.items.map(i => [i.id, i])));
  return <Panel className="exam-panel">
    <div className={`exam-verdict ${result.readyForB1 ? 'ready' : 'not-yet'}`}>
      <img src="/brand/nibble.svg" alt="" width="96" height="96" />
      <div>
        <p className="eyebrow">{examinerName.toUpperCase()} SAYS</p>
        <h2>{result.readyForB1 ? 'You look ready for B1' : 'Not ready for B1 yet'}</h2>
        <p>{result.verdict}</p>
        <p><b>Estimated level: {LEVEL_TEXT[result.levelEstimate] || result.levelEstimate}</b> · average {result.averagePercent}%
          {result.examinerLevel && <> · the AI examiner puts your writing and speaking at {result.examinerLevel}</>}</p>
        {result.examinerSummary && <p>{result.examinerSummary}</p>}
        <small>{result.markedBy === 'AI' ? 'Writing and speaking were marked by an AI examiner, which can be wrong.' : 'Writing and speaking were marked by fixed rules, not by the AI examiner.'} Practice estimate, not an official result.</small>
      </div>
    </div>
    <div className="exam-bars">{result.parts.map(p => <div key={p.id} className={`exam-bar ${p.passed ? 'pass' : 'fail'}`}>
      <span><b>{p.english}</b> <small lang="pl">{p.name}</small></span>
      <div className="exam-bar-track" style={{ '--pct': `${p.percent}%`, '--pass': `${result.passPercent}%` }} role="img" aria-label={`${p.english} ${p.percent} percent, pass mark ${result.passPercent}`}><i /></div>
      <b>{p.percent}%</b>
    </div>)}</div>
    {result.parts.map(p => <details key={p.id} className="exam-feedback" open={!p.passed}>
      <summary>{p.english}: {p.passed ? 'passed' : 'below the pass mark'}</summary>
      {p.items.map(r => <ItemFeedback key={r.id} result={r} item={items[r.id]} />)}
    </details>)}
    <div className="exam-nav"><Button variant="quiet" onClick={onBack}>Back</Button><Button onClick={onRestart}>Try a new attempt</Button></div>
  </Panel>;
}

function ItemFeedback({ result, item }) {
  if (!item) return null;
  if (!result.assessment) {
    const right = result.chosen === result.answer;
    return <p className={`exam-closed ${right ? 'right' : 'wrong'}`}><span lang="pl">{item.prompt}</span> <b>{right ? '✓' : '✗'}</b>
      {!right && <> Correct: <span lang="pl">{item.options[result.answer]}</span>{result.chosen == null ? ' (no answer)' : ''}</>}</p>;
  }
  const a = result.assessment;
  return <div className="exam-open-feedback">
    <p lang="pl"><b>{item.prompt}</b></p>
    <p><b>{a.score}/100</b> · level {a.level} · task {a.criteria.task}/25 · coherence {a.criteria.coherence}/25 · vocabulary {a.criteria.vocabulary}/25 · grammar {a.criteria.grammar}/25 <Pill tone="soft">{a.markedBy === 'AI' ? 'AI examiner' : 'fixed rules'}</Pill></p>
    {a.summary && <p>{a.summary}</p>}
    {a.strengths?.length > 0 && <ul className="exam-good">{a.strengths.map(s => <li key={s}>{s}</li>)}</ul>}
    {a.corrections?.length > 0 && <ul className="exam-fixes">{a.corrections.map(c => <li key={c.original}><s lang="pl">{c.original}</s> → <b lang="pl">{c.corrected}</b>{c.explanation && <small> {c.explanation}</small>}</li>)}</ul>}
    {a.tips?.length > 0 && <ul className="exam-tips">{a.tips.map(t => <li key={t}>{t}</li>)}</ul>}
  </div>;
}

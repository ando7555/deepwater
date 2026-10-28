import React, { useEffect, useMemo, useRef, useState } from 'react';
import { formatDate } from './api.js';
import { canSpeak, speak } from './speech.js';
import { checkRecall, memoryStrength } from './memory.js';

const BOXES = 5;

/** Five small marks, like the nodes on the Deepwater line: how deep a card has settled. */
function CardDepth({ box }) {
  return <span className="card-depth" aria-label={`Depth ${box} of ${BOXES}`}>
    {Array.from({ length: BOXES }, (_, i) => <i key={i} className={i < box ? 'on' : ''} />)}
  </span>;
}

export function MemoryMeter({ card }) {
  if (card.box === 0) return <span className="memory-meter"><small>New, or back for another look</small></span>;
  const strength = Math.round(memoryStrength(card) * 100);
  return <span className={`memory-meter${strength < 70 ? ' fading' : ''}`} title="Estimated chance you remember this now">
    <span className="memory-track"><span style={{ width: `${strength}%` }} /></span><small>Memory {strength}%</small>
  </span>;
}

export function HearButton({ text, languageCode, label = 'Hear it' }) {
  if (!canSpeak) return null;
  return <button type="button" className="hear-button" onClick={() => speak(text, languageCode)} aria-label={`${label}: ${text}`}>
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 10v4h4l5 4V6L8 10H4Zm12-1a4 4 0 0 1 0 6m2-9a8 8 0 0 1 0 12" /></svg>{label}
  </button>;
}

/**
 * One card at a time. "Flip" shows the phrase and asks for the meaning; "Type" shows the meaning and
 * asks for the phrase, which is harder and builds stronger memory. Keys: Space or Enter shows the answer,
 * 1 = not yet, 2 = I knew it, H = hear it.
 */
export function CardReview({ card, remaining, languageCode, onReview, busy, mode = 'flip' }) {
  const [shown, setShown] = useState(false);
  const [typed, setTyped] = useState('');
  const [verdict, setVerdict] = useState(null);
  const inputRef = useRef(null);

  useEffect(() => { setShown(false); setTyped(''); setVerdict(null); if (mode === 'type') inputRef.current?.focus(); }, [card?.id, mode]);

  useEffect(() => {
    if (!card) return undefined;
    function onKey(event) {
      if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA' || busy) return;
      if ((event.key === ' ' || event.key === 'Enter') && !shown && mode === 'flip') { event.preventDefault(); setShown(true); }
      else if (event.key === '1' && shown) onReview(card, false);
      else if (event.key === '2' && shown) onReview(card, true);
      else if (event.key.toLowerCase() === 'h') speak(card.front, languageCode);
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [card, shown, busy, mode, languageCode, onReview]);

  if (!card) return <div className="card-review empty"><b>No cards are due.</b><p>Cards come back on their own: after a day, then 3, 7, 14 and 30 days.</p></div>;

  function submitTyped(event) {
    event.preventDefault();
    setVerdict(checkRecall(typed, card.front)); setShown(true); speak(card.front, languageCode);
  }

  const knewIt = verdict ? verdict !== 'miss' : true;
  return <div className="card-review" aria-live="polite">
    <span className="eyebrow">{remaining} DUE · {mode === 'type' ? 'TYPE FROM MEMORY' : 'SAY THE MEANING'}</span>
    {mode === 'flip' ? <>
      <p className="card-front">{card.front}</p>
      <HearButton text={card.front} languageCode={languageCode} />
    </> : <>
      <p className="card-prompt">{card.back}</p>
      <form className="recall-form" onSubmit={submitTyped}>
        <label className="visually-hidden" htmlFor={`recall-${card.id}`}>Type the phrase</label>
        <input id={`recall-${card.id}`} ref={inputRef} value={typed} disabled={shown} autoComplete="off" spellCheck="false"
          placeholder="Type the phrase…" onChange={event => setTyped(event.target.value)} />
        {!shown && <button>Check</button>}
      </form>
    </>}
    {shown ? <>
      {mode === 'flip' ? <p className="card-back">{card.back}</p> : <div className={`recall-result ${verdict}`}>
        <b>{verdict === 'exact' ? 'Exactly right.' : verdict === 'close' ? 'Almost: one small slip.' : 'Not yet. Here it is:'}</b>
        <p className="card-front">{card.front}</p>
      </div>}
      <div className="card-actions">
        <button type="button" className="secondary" disabled={busy} onClick={() => onReview(card, false)}>Not yet <kbd>1</kbd></button>
        <button type="button" disabled={busy} onClick={() => onReview(card, true)} autoFocus={knewIt}>I knew it <kbd>2</kbd></button>
      </div>
      <small>“Not yet” moves the card back one step, never to the start.</small>
    </> : mode === 'flip' && <button type="button" onClick={() => setShown(true)}>Show the meaning <kbd>Space</kbd></button>}
  </div>;
}

function shuffle(items) {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [copy[i], copy[j]] = [copy[j], copy[i]]; }
  return copy;
}

/** A short matching round from the learner's own cards, weakest memories first. Nothing is graded or saved. */
export function MatchingWarmUp({ cards, languageCode, onClose }) {
  const round = useMemo(() => [...cards].sort((a, b) => memoryStrength(a) - memoryStrength(b)).slice(0, 5), [cards]);
  const meanings = useMemo(() => shuffle(round), [round]);
  const [picked, setPicked] = useState(null);
  const [matched, setMatched] = useState([]);
  const [miss, setMiss] = useState(null);
  const started = useRef(Date.now());
  const [seconds, setSeconds] = useState(null);

  function choose(card) {
    if (!picked) return;
    if (card.id === picked) {
      const next = [...matched, card.id];
      setMatched(next); setPicked(null); setMiss(null); speak(card.front, languageCode);
      if (next.length === round.length) setSeconds(Math.round((Date.now() - started.current) / 1000));
    } else { setMiss(card.id); setPicked(null); }
  }

  return <section className="matching panel" aria-labelledby="matching-title">
    <div className="matching-heading"><div><p className="eyebrow">WARM-UP</p><h3 id="matching-title">Match each phrase to its meaning</h3></div>
      <button type="button" className="link" onClick={onClose}>Close</button></div>
    {seconds !== null ? <p role="status" className="notice">All matched in {seconds} seconds. You’re warmed up.</p> : <div className="matching-grid">
      <div>{round.map(card => <button type="button" key={card.id} disabled={matched.includes(card.id)}
        className={`match-item${picked === card.id ? ' picked' : ''}${matched.includes(card.id) ? ' matched' : ''}`}
        aria-pressed={picked === card.id} onClick={() => { setPicked(card.id); setMiss(null); }}>{card.front}</button>)}</div>
      <div>{meanings.map(card => <button type="button" key={card.id} disabled={matched.includes(card.id)}
        className={`match-item${miss === card.id ? ' miss' : ''}${matched.includes(card.id) ? ' matched' : ''}`}
        onClick={() => choose(card)}>{card.back}</button>)}</div>
    </div>}
    {seconds === null && <small>Pick a phrase on the left, then its meaning on the right. You’ll hear each match.</small>}
  </section>;
}

export default function PhraseCards({ cards, languageCode, languageName, busy, onSave, onReview, onDelete }) {
  const [front, setFront] = useState('');
  const [back, setBack] = useState('');
  const [mode, setMode] = useState('flip');
  const due = cards.filter(card => card.due);

  async function submit(event) {
    event.preventDefault();
    if (await onSave(front, back)) { setFront(''); setBack(''); }
  }

  return <section className="phrase-cards" aria-labelledby="cards-title">
    <div className="practice-intro"><p className="eyebrow">MY CARDS</p><h2 id="cards-title">Phrases you want to keep.</h2>
      <p>Save a phrase you met in a lesson, a song or a conversation. Echo brings it back just before you would forget it.</p></div>
    <div className="cards-layout">
      <div className="panel cards-panel">
        <div className="cards-panel-heading"><h3>Review</h3>
          <div className="segmented" role="group" aria-label="Review style">
            <button type="button" aria-pressed={mode === 'flip'} onClick={() => setMode('flip')}>Flip</button>
            <button type="button" aria-pressed={mode === 'type'} onClick={() => setMode('type')}>Type from memory</button>
          </div></div>
        <CardReview card={due[0]} remaining={due.length} languageCode={languageCode} onReview={onReview} busy={busy} mode={mode} />
        <small className="shortcut-hint">Keys: Space shows the answer · 1 not yet · 2 I knew it · H hear it</small>
      </div>
      <form className="panel cards-panel" onSubmit={submit}>
        <h3>Add a card</h3>
        <label>Phrase in {languageName}<input required maxLength="300" value={front} onChange={event => setFront(event.target.value)} /></label>
        <label>What it means to you<input required maxLength="300" value={back} onChange={event => setBack(event.target.value)} /></label>
        <button disabled={busy}>Save card</button>
      </form>
    </div>
    {cards.length > 0 && <ul className="card-list">
      {cards.map(card => <li key={card.id}>
        <div><b>{card.front}</b><span>{card.back}</span></div>
        <HearButton text={card.front} languageCode={languageCode} label="Hear" />
        <MemoryMeter card={card} />
        <CardDepth box={card.box} />
        <small>{card.due ? 'Due now' : `Next ${formatDate(card.nextReviewAt)}`}</small>
        <button type="button" className="link danger-link" aria-label={`Delete card ${card.front}`} onClick={() => onDelete(card)}>Delete</button>
      </li>)}
    </ul>}
  </section>;
}

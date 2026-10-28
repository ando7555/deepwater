import React, { useMemo, useState } from 'react';
import { formatDate } from './api.js';

const BOXES = 5;

/** Five small marks, like the nodes on the Deepwater line: how deep a card has settled. */
function CardDepth({ box }) {
  return <span className="card-depth" aria-label={`Depth ${box} of ${BOXES}`}>
    {Array.from({ length: BOXES }, (_, i) => <i key={i} className={i < box ? 'on' : ''} />)}
  </span>;
}

export function CardReview({ cards, onReview, busy }) {
  const due = cards.filter(card => card.due);
  const [shown, setShown] = useState(false);
  const card = due[0];
  if (!card) return <div className="card-review empty"><b>No cards are due.</b><p>Cards come back on their own: after a day, then 3, 7, 14 and 30 days.</p></div>;
  return <div className="card-review" aria-live="polite">
    <span className="eyebrow">{due.length} DUE</span>
    <p className="card-front">{card.front}</p>
    {shown ? <>
      <p className="card-back">{card.back}</p>
      <div className="card-actions">
        <button type="button" className="secondary" disabled={busy} onClick={() => { setShown(false); onReview(card, false); }}>Not yet</button>
        <button type="button" disabled={busy} onClick={() => { setShown(false); onReview(card, true); }}>I knew it</button>
      </div>
      <small>“Not yet” moves the card back one step, never to the start.</small>
    </> : <button type="button" onClick={() => setShown(true)}>Show the meaning</button>}
  </div>;
}

function shuffle(items) {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [copy[i], copy[j]] = [copy[j], copy[i]]; }
  return copy;
}

/** A short matching round from the learner's own cards, due cards first. Nothing is graded or saved. */
export function MatchingWarmUp({ cards, onClose }) {
  const round = useMemo(() => [...cards].sort((a, b) => Number(b.due) - Number(a.due)).slice(0, 5), [cards]);
  const meanings = useMemo(() => shuffle(round), [round]);
  const [picked, setPicked] = useState(null);
  const [matched, setMatched] = useState([]);
  const [miss, setMiss] = useState(null);

  function choose(card) {
    if (!picked) return;
    if (card.id === picked) { setMatched(current => [...current, card.id]); setPicked(null); setMiss(null); }
    else { setMiss(card.id); setPicked(null); }
  }

  const done = matched.length === round.length;
  return <section className="matching panel" aria-labelledby="matching-title">
    <div className="matching-heading"><div><p className="eyebrow">WARM-UP</p><h3 id="matching-title">Match each phrase to its meaning</h3></div>
      <button type="button" className="link" onClick={onClose}>Close</button></div>
    {done ? <p role="status" className="notice">All matched. You’re warmed up.</p> : <div className="matching-grid">
      <div>{round.map(card => <button type="button" key={card.id} disabled={matched.includes(card.id)}
        className={`match-item${picked === card.id ? ' picked' : ''}${matched.includes(card.id) ? ' matched' : ''}`}
        aria-pressed={picked === card.id} onClick={() => { setPicked(card.id); setMiss(null); }}>{card.front}</button>)}</div>
      <div>{meanings.map(card => <button type="button" key={card.id} disabled={matched.includes(card.id)}
        className={`match-item${miss === card.id ? ' miss' : ''}${matched.includes(card.id) ? ' matched' : ''}`}
        onClick={() => choose(card)}>{card.back}</button>)}</div>
    </div>}
    {!done && <small>Pick a phrase on the left, then its meaning on the right.</small>}
  </section>;
}

export default function PhraseCards({ cards, languageName, busy, onSave, onReview, onDelete }) {
  const [front, setFront] = useState('');
  const [back, setBack] = useState('');

  async function submit(event) {
    event.preventDefault();
    if (await onSave(front, back)) { setFront(''); setBack(''); }
  }

  return <section className="phrase-cards" aria-labelledby="cards-title">
    <div className="practice-intro"><p className="eyebrow">MY CARDS</p><h2 id="cards-title">Phrases you want to keep.</h2>
      <p>Save a phrase you met in a lesson, a song or a conversation. Echo brings it back just before you would forget it.</p></div>
    <div className="cards-layout">
      <div className="panel cards-panel">
        <h3>Review</h3>
        <CardReview cards={cards} onReview={onReview} busy={busy} />
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
        <CardDepth box={card.box} />
        <small>{card.due ? 'Due now' : `Next ${formatDate(card.nextReviewAt)}`}</small>
        <button type="button" className="link danger-link" aria-label={`Delete card ${card.front}`} onClick={() => onDelete(card)}>Delete</button>
      </li>)}
    </ul>}
  </section>;
}

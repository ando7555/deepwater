import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { formatDate } from './api.js';
import { canSpeak, speak } from './speech.js';
import { checkRecall, memoryStrength } from './memory.js';
import { SPECIAL_LETTERS, articleOf, canBuild, parseCardList, shuffledTiles } from './cardTools.js';

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

/** Normal and slow read-aloud. Slow replay helps with Danish sounds that run together. */
export function HearButton({ text, languageCode, label = 'Hear it', slow = true }) {
  if (!canSpeak) return null;
  return <span className="hear-pair">
    <button type="button" className="hear-button" onClick={() => speak(text, languageCode)} aria-label={`${label}: ${text}`}>
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 10v4h4l5 4V6L8 10H4Zm12-1a4 4 0 0 1 0 6m2-9a8 8 0 0 1 0 12" /></svg>{label}
    </button>
    {slow && <button type="button" className="hear-button slow" onClick={() => speak(text, languageCode, 0.55)} aria-label={`Hear slowly: ${text}`}>Slow</button>}
  </span>;
}

/** en/et (or der/die/das) shown as its own badge: the article belongs to the word and has to be learned with it. */
export function ArticleBadge({ front, languageCode }) {
  const article = articleOf(front, languageCode);
  return article ? <span className="article-badge" title={`An ${article}-word: learn the article with the noun`}>{article}-word</span> : null;
}

/** Buttons that insert æ, ø, å (or ä, ö, ü, ß) at the cursor, so no special keyboard is needed. */
function LetterButtons({ languageCode, inputRef, setValue }) {
  const letters = SPECIAL_LETTERS[languageCode] || [];
  if (!letters.length) return null;
  function insert(letter) {
    const input = inputRef.current;
    const at = input?.selectionStart ?? input?.value.length ?? 0, to = input?.selectionEnd ?? at;
    setValue(value => value.slice(0, at) + letter + value.slice(to));
    requestAnimationFrame(() => { input?.focus(); input?.setSelectionRange(at + 1, at + 1); });
  }
  return <span className="letter-buttons" aria-label="Special letters">
    {letters.map(letter => <button type="button" key={letter} onClick={() => insert(letter)} aria-label={`Insert ${letter}`}>{letter}</button>)}
  </span>;
}

/** Rebuild the phrase from shuffled word tiles: practises word order, which recognition alone does not. */
function SentenceBuilder({ card, onDone }) {
  const [tiles, setTiles] = useState(() => shuffledTiles(card.front));
  const [placed, setPlaced] = useState([]);
  useEffect(() => { setTiles(shuffledTiles(card.front)); setPlaced([]); }, [card.id]); // eslint-disable-line react-hooks/exhaustive-deps
  const complete = placed.length === tiles.length && tiles.length > 0;
  useEffect(() => {
    if (complete) onDone(placed.map(tile => tile.word).join(' '));
  }, [complete]); // eslint-disable-line react-hooks/exhaustive-deps
  return <div className="sentence-builder">
    <div className="tile-row answer" aria-label="Your sentence">
      {placed.length === 0 && <small>Tap the words in the right order.</small>}
      {placed.map(tile => <button type="button" key={tile.index} className="tile placed" disabled={complete}
        onClick={() => setPlaced(placed.filter(item => item !== tile))}>{tile.word}</button>)}
    </div>
    <div className="tile-row">
      {tiles.filter(tile => !placed.includes(tile)).map(tile => <button type="button" key={tile.index} className="tile"
        onClick={() => setPlaced([...placed, tile])}>{tile.word}</button>)}
    </div>
  </div>;
}

export const REVIEW_MODES = [
  ['flip', 'Phrase → meaning'],
  ['reverse', 'Meaning → phrase'],
  ['type', 'Type from memory'],
  ['listen', 'Hear it, write it'],
  ['build', 'Build the sentence'],
];

/**
 * One card at a time, in five directions: see the phrase and recall the meaning, see the meaning and say
 * the phrase, type it, write what you hear, or rebuild it from word tiles. Three honest grades:
 * 1 = not yet (one step back), 2 = close (same step, back tomorrow), 3 = I knew it (one step deeper).
 * Keys: Space or Enter shows the answer, H hears it.
 */
export function CardReview({ card, remaining, languageCode, onReview, busy, mode = 'flip' }) {
  const [shown, setShown] = useState(false);
  const [typed, setTyped] = useState('');
  const [verdict, setVerdict] = useState(null);
  const inputRef = useRef(null);
  const activeMode = mode === 'build' && !canBuild(card) ? 'type' : mode;
  const typing = activeMode === 'type' || activeMode === 'listen';

  useEffect(() => {
    setShown(false); setTyped(''); setVerdict(null);
    if (typing) inputRef.current?.focus();
    if (card && activeMode === 'listen') speak(card.front, languageCode);
  }, [card?.id, activeMode]); // eslint-disable-line react-hooks/exhaustive-deps

  const grade = useCallback(value => onReview(card, value, value === 'MISSED' && typed.trim() ? typed.trim() : null), [card, typed, onReview]);

  useEffect(() => {
    if (!card) return undefined;
    function onKey(event) {
      if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA' || busy) return;
      if ((event.key === ' ' || event.key === 'Enter') && !shown && !typing && activeMode !== 'build') { event.preventDefault(); setShown(true); }
      else if (event.key === '1' && shown) grade('MISSED');
      else if (event.key === '2' && shown) grade('CLOSE');
      else if (event.key === '3' && shown) grade('KNEW');
      else if (event.key.toLowerCase() === 'h') speak(card.front, languageCode);
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [card, shown, busy, typing, activeMode, languageCode, grade]);

  if (!card) return <div className="card-review empty"><b>You’re caught up.</b><p>Cards come back on their own: after a day, then 3, 7, 14 and 30 days.</p></div>;

  function check(answer) {
    setTyped(answer); setVerdict(checkRecall(answer, card.front)); setShown(true);
    if (activeMode !== 'listen') speak(card.front, languageCode);
  }
  function submitTyped(event) { event.preventDefault(); check(typed); }

  const labels = { flip: 'SAY THE MEANING', reverse: 'SAY THE PHRASE', type: 'TYPE FROM MEMORY', listen: 'WRITE WHAT YOU HEAR', build: 'PUT THE WORDS IN ORDER' };
  const suggested = verdict === 'exact' ? 'KNEW' : verdict === 'close' ? 'CLOSE' : verdict === 'miss' ? 'MISSED' : null;
  return <div className="card-review" aria-live="polite">
    <span className="eyebrow">{remaining} DUE · {labels[activeMode]}</span>
    {activeMode === 'flip' && <>
      <p className="card-front">{card.front}</p>
      <ArticleBadge front={card.front} languageCode={languageCode} />
      <HearButton text={card.front} languageCode={languageCode} />
    </>}
    {(activeMode === 'reverse' || activeMode === 'type' || activeMode === 'build') && <p className="card-prompt">{card.back}</p>}
    {activeMode === 'listen' && <HearButton text={card.front} languageCode={languageCode} label="Play again" />}
    {typing && <form className="recall-form" onSubmit={submitTyped}>
      <label className="visually-hidden" htmlFor={`recall-${card.id}`}>Type the phrase</label>
      <input id={`recall-${card.id}`} ref={inputRef} value={typed} disabled={shown} autoComplete="off" spellCheck="false"
        placeholder={activeMode === 'listen' ? 'Write what you heard…' : 'Type the phrase…'} onChange={event => setTyped(event.target.value)} />
      {!shown && <button>Check</button>}
    </form>}
    {typing && !shown && <LetterButtons languageCode={languageCode} inputRef={inputRef} setValue={setTyped} />}
    {activeMode === 'build' && !shown && <SentenceBuilder card={card} onDone={check} />}
    {shown ? <>
      {activeMode === 'flip' && <p className="card-back">{card.back}</p>}
      {activeMode === 'reverse' && <><p className="card-front">{card.front}</p><ArticleBadge front={card.front} languageCode={languageCode} /><HearButton text={card.front} languageCode={languageCode} /></>}
      {verdict && <div className={`recall-result ${verdict}`}>
        <b>{verdict === 'exact' ? 'Exactly right.' : verdict === 'close' ? 'Almost: one small slip.' : 'Not yet. Here it is:'}</b>
        <p className="card-front">{card.front}</p>
        {activeMode === 'listen' && <p className="card-back">{card.back}</p>}
        <ArticleBadge front={card.front} languageCode={languageCode} />
      </div>}
      <div className="card-actions">
        <button type="button" className={`secondary${suggested === 'MISSED' ? ' suggested' : ''}`} disabled={busy} onClick={() => grade('MISSED')}>Not yet <kbd>1</kbd></button>
        <button type="button" className={`secondary${suggested === 'CLOSE' ? ' suggested' : ''}`} disabled={busy} onClick={() => grade('CLOSE')}>I was close <kbd>2</kbd></button>
        <button type="button" disabled={busy} onClick={() => grade('KNEW')} autoFocus={suggested !== 'MISSED'}>I knew it <kbd>3</kbd></button>
      </div>
      <small>Not yet = one step back, never to the start · Close = same step, back tomorrow</small>
    </> : !typing && activeMode !== 'build' && <button type="button" onClick={() => setShown(true)}>Show the answer <kbd>Space</kbd></button>}
  </div>;
}

/** Cards missed twice or more, with the learner's last wrong answer: the words that keep slipping. */
export function KeepsSlipping({ cards, languageCode }) {
  const slipping = cards.filter(card => card.missCount >= 2).sort((a, b) => b.missCount - a.missCount);
  return <div className="panel slipping">
    <h3>Keeps slipping</h3>
    {slipping.length === 0 ? <p>Nothing is stuck. Cards you miss twice or more show up here, with what you wrote, so you can see the pattern.</p> :
      <ul>{slipping.map(card => <li key={card.id}>
        <div><b>{card.front}</b><span>{card.back}</span></div>
        {card.lastMiss && <small>You wrote “{card.lastMiss}”</small>}
        <small>Missed {card.missCount}×</small>
        <HearButton text={card.front} languageCode={languageCode} label="Hear" />
      </li>)}</ul>}
  </div>;
}

/** Paste a list from notes or a spreadsheet: one "phrase ; meaning" pair per line, checked before saving. */
function PasteList({ languageName, busy, onSaveMany }) {
  const [text, setText] = useState('');
  const [swap, setSwap] = useState(false);
  const { pairs, problems } = useMemo(() => parseCardList(text, swap), [text, swap]);
  async function submit(event) {
    event.preventDefault();
    if (pairs.length && !problems.length && await onSaveMany(pairs)) setText('');
  }
  return <form className="paste-list" onSubmit={submit}>
    <label>One pair per line: {languageName} phrase, then its meaning
      <textarea rows="6" value={text} onChange={event => setText(event.target.value)} spellCheck="false"
        placeholder={'hej ; hello\ntak ; thank you\nen kop kaffe ; a cup of coffee'} /></label>
    <small>Tab, semicolon, “ - ” or “=” between the two sides. Up to 100 lines at a time.</small>
    <label className="check"><input type="checkbox" checked={swap} onChange={event => setSwap(event.target.checked)} /> The meaning comes first on each line</label>
    {text.trim() && <p className="paste-summary">{pairs.length} ready{problems.length ? ` · ${problems.length} to fix` : ''}</p>}
    {problems.length > 0 && <ul className="paste-problems">{problems.slice(0, 5).map(problem =>
      <li key={problem.line}>Line {problem.line} {problem.reason}: “{problem.text.slice(0, 60)}”</li>)}</ul>}
    <button disabled={busy || !pairs.length || problems.length > 0 || pairs.length > 100}>Add {pairs.length || ''} card{pairs.length === 1 ? '' : 's'}</button>
  </form>;
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

export default function PhraseCards({ cards, languageCode, languageName, busy, onSave, onSaveMany, onReview, onDelete }) {
  const [front, setFront] = useState('');
  const [back, setBack] = useState('');
  const [mode, setMode] = useState('flip');
  const [adding, setAdding] = useState('one');
  const due = cards.filter(card => card.due);

  async function submit(event) {
    event.preventDefault();
    if (await onSave(front, back)) { setFront(''); setBack(''); }
  }

  return <section className="phrase-cards" aria-labelledby="cards-title">
    <div className="practice-intro"><p className="eyebrow">MY CARDS</p><h2 id="cards-title">Phrases you want to keep.</h2>
      <p>Save a phrase you met in a lesson, a song or a conversation. Echo brings it back just before you would forget it. No teacher has to assign it, and nothing is locked.</p></div>
    <div className="cards-layout">
      <div className="panel cards-panel">
        <div className="cards-panel-heading"><h3>Review</h3>
          <label className="mode-picker">Direction
            <select value={mode} onChange={event => setMode(event.target.value)}>
              {REVIEW_MODES.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
            </select></label></div>
        <CardReview card={due[0]} remaining={due.length} languageCode={languageCode} onReview={onReview} busy={busy} mode={mode} />
        <small className="shortcut-hint">Keys: Space shows the answer · 1 not yet · 2 close · 3 I knew it · H hear it</small>
      </div>
      <div className="panel cards-panel">
        <div className="cards-panel-heading"><h3>Add cards</h3>
          <div className="segmented" role="group" aria-label="How to add">
            <button type="button" aria-pressed={adding === 'one'} onClick={() => setAdding('one')}>One card</button>
            <button type="button" aria-pressed={adding === 'list'} onClick={() => setAdding('list')}>Paste a list</button>
          </div></div>
        {adding === 'one' ? <form onSubmit={submit} className="one-card">
          <label>Phrase in {languageName}<input required maxLength="300" value={front} placeholder={languageCode === 'da' ? 'For example: en kat' : 'For example: die Katze'} onChange={event => setFront(event.target.value)} /></label>
          {articleOf(front, languageCode) && <small>Good: the article is part of the card, so you learn it with the noun.</small>}
          <label>What it means to you<input required maxLength="300" value={back} onChange={event => setBack(event.target.value)} /></label>
          <button disabled={busy}>Save card</button>
        </form> : <PasteList languageName={languageName} busy={busy} onSaveMany={onSaveMany} />}
      </div>
    </div>
    <KeepsSlipping cards={cards} languageCode={languageCode} />
    {cards.length > 0 && <ul className="card-list">
      {cards.map(card => <li key={card.id}>
        <div><b>{card.front} <ArticleBadge front={card.front} languageCode={languageCode} /></b><span>{card.back}</span></div>
        <HearButton text={card.front} languageCode={languageCode} label="Hear" slow={false} />
        <MemoryMeter card={card} />
        <CardDepth box={card.box} />
        <small>{card.due ? 'Due now' : `Next ${formatDate(card.nextReviewAt)}`}</small>
        <button type="button" className="link danger-link" aria-label={`Delete card ${card.front}`} onClick={() => onDelete(card)}>Delete</button>
      </li>)}
    </ul>}
  </section>;
}

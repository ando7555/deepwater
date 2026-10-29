import React, { useMemo, useState } from 'react';
import { CardReview } from './PhraseCards.jsx';
import { memoryStrength } from './memory.js';

const SESSION_SIZE = 10;
// Cards in a session change direction, so one phrase is met as recognition, recall, listening and word order.
const REVIEW_ROTATION = ['type', 'flip', 'listen', 'reverse', 'build'];

/**
 * Builds today's session: due cards (weakest memory first) interleaved with graded exercises
 * from due lessons, topped up with the least-practised lessons. Mixing kinds of practice
 * (interleaving) is harder in the moment and better for long-term memory.
 */
export function buildSession(lessons, cards) {
  const cardItems = cards.filter(card => card.due).sort((a, b) => memoryStrength(a) - memoryStrength(b))
    .map(card => ({ kind: 'card', id: `card-${card.id}`, card }));
  const lessonOrder = [...lessons].filter(lesson => !lesson.audio)
    .sort((a, b) => Number(b.due) - Number(a.due) || a.mastery - b.mastery);
  const exerciseItems = lessonOrder.filter(lesson => lesson.due || lesson.mastery < 0.8)
    .flatMap(lesson => lesson.exercises.filter(exercise => exercise.taskType !== 'TRANSFER')
      .map(exercise => ({ kind: 'exercise', id: `exercise-${exercise.id}`, exercise, lesson })));
  const session = [];
  while (session.length < SESSION_SIZE && (cardItems.length || exerciseItems.length)) {
    if (cardItems.length) session.push(cardItems.shift());
    if (exerciseItems.length && session.length < SESSION_SIZE) session.push(exerciseItems.shift());
  }
  return session;
}

export default function DailyReview({ lessons, cards, languageCode, results, renderExercise, onReviewCard, busy, onClose }) {
  // The queue is fixed when the session starts, so reviewing a card does not reshuffle it.
  const session = useMemo(() => buildSession(lessons, cards), []); // eslint-disable-line react-hooks/exhaustive-deps
  const [index, setIndex] = useState(0);
  const [tally, setTally] = useState({ knew: 0, close: 0, notYet: 0 });
  const item = session[index];
  const done = index >= session.length;

  async function reviewCard(card, grade, answer) {
    await onReviewCard(card, grade, answer);
    const key = grade === 'KNEW' ? 'knew' : grade === 'CLOSE' ? 'close' : 'notYet';
    setTally(current => ({ ...current, [key]: current[key] + 1 }));
    setIndex(current => current + 1);
  }

  return <section className="daily-review panel" aria-labelledby="daily-title">
    <div className="matching-heading"><div><p className="eyebrow">DAILY REVIEW</p>
      <h3 id="daily-title">{done ? 'Session complete' : `${index + 1} of ${session.length}`}</h3></div>
      <button type="button" className="link" onClick={onClose}>{done ? 'Close' : 'Stop for now'}</button></div>
    <div className="progress-track" aria-hidden="true"><span style={{ width: `${Math.round(Math.min(index, session.length) / Math.max(session.length, 1) * 100)}%` }} /></div>
    {session.length === 0 && <p>Nothing is due. Come back later, or add a few cards to build your memory.</p>}
    {!done && item?.kind === 'card' && <CardReview card={item.card} remaining={session.length - index} languageCode={languageCode}
      onReview={reviewCard} busy={busy} mode={REVIEW_ROTATION[index % REVIEW_ROTATION.length]} />}
    {!done && item?.kind === 'exercise' && <div>
      <p className="practice-source">{item.lesson.title}{item.lesson.due ? ' · review due' : ''}</p>
      {renderExercise(item.exercise, index + 1, 'REVIEW')}
      <button type="button" className="secondary next-button" disabled={!results[item.exercise.id]} onClick={() => setIndex(index + 1)}>Next</button>
    </div>}
    {done && session.length > 0 && <div className="session-summary" role="status">
      <p>You worked through {session.length} items. Cards you knew: {tally.knew}. Close: {tally.close}. For another look: {tally.notYet}.</p>
      <p>Everything you missed comes back sooner, a step back rather than to the start.</p>
    </div>}
  </section>;
}

import React from 'react';
import DeepwaterMark3D from './DeepwaterMark3D.jsx';

const MASTERED = 0.8;

/**
 * Progress without streaks or leagues: what the learner can now do, and what is due.
 * Missing a day only moves reviews; nothing resets.
 */
export default function CalmProgress({ home, cards }) {
  const lessons = home.lessons;
  const started = lessons.filter(lesson => lesson.attempts > 0);
  const mastered = lessons.filter(lesson => lesson.mastery >= MASTERED);
  const depth = lessons.length ? lessons.reduce((total, lesson) => total + lesson.mastery, 0) / lessons.length : 0;
  const dueLessons = lessons.filter(lesson => lesson.due).length;
  const dueCards = cards.filter(card => card.due).length;
  const settledCards = cards.filter(card => card.box >= 3).length;
  const accuracy = home.totalAttempts ? Math.round(home.correctAttempts / home.totalAttempts * 100) : null;

  return <section className="calm-progress" aria-labelledby="progress-title">
    <div className="progress-hero panel">
      <DeepwaterMark3D size={200} depth={depth} label={`Overall depth: ${Math.round(depth * 100)}% across ${lessons.length} lessons`} />
      <div>
        <p className="eyebrow">YOUR DEPTH</p>
        <h2 id="progress-title">{Math.round(depth * 100)}% deep</h2>
        <p>The average practice estimate across this language’s lessons. It grows with correct answers and comes back to review, not with time spent.</p>
        <p className="calm-rule"><b>No streaks.</b> Missing a day only moves your reviews. Nothing is lost.</p>
      </div>
    </div>
    <div className="stat-grid calm-stats">
      <div><strong>{mastered.length}<small> / {lessons.length}</small></strong><span>patterns at {MASTERED * 100}% or more</span></div>
      <div><strong>{started.length}</strong><span>patterns started</span></div>
      <div><strong>{dueLessons + dueCards}</strong><span>reviews ready now</span></div>
      <div><strong>{accuracy === null ? '–' : `${accuracy}%`}</strong><span>of {home.totalAttempts} checked answers right</span></div>
      <div><strong>{home.audioAttempts}</strong><span>listening steps</span></div>
      <div><strong>{settledCards}<small> / {cards.length}</small></strong><span>cards settled (3+ steps deep)</span></div>
    </div>
    <ul className="pattern-list">
      {lessons.map(lesson => <li key={lesson.id}>
        <span>{lesson.title}</span>
        <div className="progress-track"><span style={{ width: `${Math.round(lesson.mastery * 100)}%` }} /></div>
        <small>{lesson.due ? 'Review ready' : lesson.attempts ? `${Math.round(lesson.mastery * 100)}%` : 'Not started'}</small>
      </li>)}
    </ul>
    <small>Practice estimates, not a fluency or CEFR score.</small>
  </section>;
}

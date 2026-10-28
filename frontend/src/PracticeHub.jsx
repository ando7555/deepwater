import React, { useState } from 'react';

const ICONS = {
  read: <path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6S2 12 2 12Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" />,
  listen: <path d="M4 15v-3a8 8 0 0 1 16 0v3M4 15a2 2 0 0 0 2 2h1v-5H6a2 2 0 0 0-2 2Zm16 0a2 2 0 0 1-2 2h-1v-5h1a2 2 0 0 1 2 2Z" />,
  speak: <path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Zm-6 9a6 6 0 0 0 12 0M12 18v3" />,
  write: <path d="M4 20h4L19 9l-4-4L4 16v4Zm9-13 4 4" />,
};

// Each mode reuses a task type Echo already grades, so no new content or grading is needed.
export const PRACTICE_MODES = [
  { id: 'read', title: 'Read', line: 'Recognise the right sentence.', taskType: 'SELECT' },
  { id: 'listen', title: 'Listen', line: 'Hear it first, then notice the pattern.' },
  { id: 'speak', title: 'Speak', line: 'Say your own sentence out loud, then write it down.', taskType: 'TRANSFER' },
  { id: 'write', title: 'Write', line: 'Type the missing word.', taskType: 'TEXT' },
];

/** Due lessons first, then the least practised, so a session goes where it helps most. */
function byNeed(a, b) {
  return Number(b.due) - Number(a.due) || a.mastery - b.mastery;
}

export default function PracticeHub({ lessons, renderExercise, onOpenLesson, dueCards, onReviewCards, onWarmUp, canWarmUp }) {
  const [mode, setMode] = useState(null);
  const current = PRACTICE_MODES.find(item => item.id === mode);
  const ordered = [...lessons].sort(byNeed);
  const count = item => item.taskType
    ? ordered.reduce((total, lesson) => total + lesson.exercises.filter(exercise => exercise.taskType === item.taskType).length, 0)
    : ordered.filter(lesson => lesson.audio).length;

  return <section className="practice-hub" aria-labelledby="practice-title">
    <div className="practice-intro"><p className="eyebrow">PRACTICE</p><h2 id="practice-title">Choose how you want to practise today.</h2>
      <p>Every mode uses lessons your teacher approved. Patterns that are due for review come first.</p></div>
    <div className="mode-grid">
      {PRACTICE_MODES.map(item => <button type="button" key={item.id} className={`mode-card${mode === item.id ? ' active' : ''}`}
        aria-pressed={mode === item.id} onClick={() => setMode(mode === item.id ? null : item.id)}>
        <svg viewBox="0 0 24 24" aria-hidden="true">{ICONS[item.id]}</svg>
        <b>{item.title}</b><span>{item.line}</span><small>{count(item)} available</small>
      </button>)}
    </div>
    <div className="practice-shortcuts">
      <button type="button" className="link" onClick={onReviewCards}>Review my cards{dueCards ? ` (${dueCards} due)` : ''}</button>
      <button type="button" className="link" disabled={!canWarmUp} onClick={onWarmUp}
        title={canWarmUp ? undefined : 'Save at least three cards to unlock matching'}>Warm up with matching</button>
    </div>

    {current?.taskType && <div className="exercise-list practice-list"><h3>{current.title}</h3>
      {current.id === 'speak' && <p className="practice-note">Echo does not record or grade your voice. Say the sentence aloud, write what you said, and check it against the pattern.</p>}
      {ordered.flatMap(lesson => lesson.exercises.filter(exercise => exercise.taskType === current.taskType)
        .map(exercise => ({ lesson, exercise })))
        .map(({ lesson, exercise }, index) => <div key={exercise.id}>
          <p className="practice-source">{lesson.title}{lesson.due ? ' · review due' : ''}</p>
          {renderExercise(exercise, index + 1, current.title.toUpperCase())}
        </div>)}
      {count(current) === 0 && <p className="audio-empty">No {current.title.toLowerCase()} exercises are published for this language yet.</p>}
    </div>}

    {current?.id === 'listen' && <div className="practice-list"><h3>Listen</h3>
      {ordered.filter(lesson => lesson.audio).map(lesson => <article className="listen-item" key={lesson.id}>
        <div><b>{lesson.audio.sourceTitle}</b><span>{lesson.audio.mediaType === 'MUSIC' ? 'Music' : 'Podcast'} · {lesson.title}{lesson.due ? ' · review due' : ''}</span></div>
        <button type="button" onClick={() => onOpenLesson(lesson.id)}>Open</button>
      </article>)}
      {count(current) === 0 && <p className="audio-empty">No listening lessons are published for this language yet.</p>}
    </div>}
  </section>;
}

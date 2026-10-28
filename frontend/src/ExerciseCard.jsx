import React from 'react';
import { formatDate } from './api.js';

/** One lesson exercise with its answer form and feedback. Used in the lesson view and the Practice hub. */
export default function ExerciseCard({ exercise, step, heading, languageName, answer, setAnswer, selfCheck, setSelfCheck, result, busy, onSubmit }) {
  const transfer = exercise.taskType === 'TRANSFER';
  return <article className="exercise">
    <div className="exercise-heading"><span className="step">{step}</span><span>{heading || (transfer ? 'YOUR OWN EXAMPLE' : 'PRACTICE')}</span>{exercise.lastCorrect !== null && exercise.lastCorrect !== undefined && <span className={`attempt-state ${exercise.lastCorrect ? 'good' : 'retry'}`}>{exercise.lastCorrect ? 'Correct last time' : 'Try again'}</span>}</div>
    <h4>{exercise.prompt}</h4>
    <form onSubmit={event => onSubmit(event, exercise)}>
      {exercise.taskType === 'SELECT' && <fieldset className="exercise-options"><legend className="visually-hidden">Choose one answer</legend>{exercise.options.map(option => <label className="choice" key={option.key}><input type="radio" name={exercise.id} required checked={answer === option.key} onChange={() => setAnswer(option.key)} /><span><b>{option.key}.</b> {option.text}</span></label>)}</fieldset>}
      {exercise.taskType !== 'SELECT' && <label className="answer-label">{transfer ? 'Your sentence' : 'Missing word'}
        {transfer ? <textarea required maxLength="500" value={answer || ''} onChange={event => setAnswer(event.target.value)} placeholder={`Write a new sentence in ${languageName}…`} /> : <input required maxLength="200" value={answer || ''} onChange={event => setAnswer(event.target.value)} autoComplete="off" />}
      </label>}
      {transfer && <label className="choice self-check"><input type="checkbox" checked={Boolean(selfCheck)} onChange={event => setSelfCheck(event.target.checked)} /><span>I checked my example against the pattern.</span></label>}
      <button className="practice-button" disabled={busy || (transfer && !selfCheck)}>{busy ? 'Saving…' : transfer ? 'Save my example' : 'Check answer'}</button>
    </form>
    {result && <div className={`feedback ${result.graded && result.correct ? 'feedback-good' : ''}`} role="status"><b>{result.graded ? result.correct ? 'That’s it' : 'Let’s look again' : 'Saved for reflection'}</b><p>{result.feedback}</p>{result.hint && <p className="feedback-hint"><b>Suggestion:</b> {result.hint} <small>(automatic, may be wrong)</small></p>}{result.graded && <small>Practice estimate: {Math.round(result.mastery * 100)}% · Next review {formatDate(result.nextReviewAt)}</small>}</div>}
  </article>;
}

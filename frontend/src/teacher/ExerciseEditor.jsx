import React from 'react';
import { Button, Field, FormGrid, Segmented } from '../ui/index.jsx';
import { TASK_TYPES, emptyOption, withTaskType } from './lessonDraft.js';

const KIND_OPTIONS = TASK_TYPES.map(type => ({ id: type.id, label: type.label }));

/** One exercise of the lesson builder: its kind, prompt, answers and feedback. */
export default function ExerciseEditor({ exercise, index, count, onChange, onMove, onRemove }) {
  const set = key => event => onChange({ ...exercise, [key]: event.target.value });
  const kind = TASK_TYPES.find(type => type.id === exercise.taskType);
  const setOption = (position, text) => onChange({ ...exercise, options: exercise.options.map((option, i) => i === position ? { ...option, text } : option) });
  const removeOption = position => {
    const options = exercise.options.filter((_, i) => i !== position).map((option, i) => ({ ...option, key: emptyOption(i).key }));
    const expectedAnswer = options.some(option => option.key === exercise.expectedAnswer) ? exercise.expectedAnswer : options[0].key;
    onChange({ ...exercise, options, expectedAnswer });
  };

  return <fieldset className="exercise-editor">
    <div className="exercise-editor-head">
      <span className="step" aria-hidden="true">{index + 1}</span>
      <legend className="exercise-editor-title">Exercise {index + 1}</legend>
      <Segmented label={`Kind of exercise ${index + 1}`} options={KIND_OPTIONS} value={exercise.taskType} onChange={taskType => onChange(withTaskType(exercise, taskType))} />
      <div className="exercise-editor-tools">
        <Button variant="quiet" aria-label={`Move exercise ${index + 1} up`} disabled={index === 0} onClick={() => onMove(-1)}>↑</Button>
        <Button variant="quiet" aria-label={`Move exercise ${index + 1} down`} disabled={index === count - 1} onClick={() => onMove(1)}>↓</Button>
        <Button variant="link" className="danger-link" disabled={count === 1} onClick={onRemove}>Remove</Button>
      </div>
    </div>
    <small>{kind.line}</small>
    <Field as="textarea" rows="2" label="Prompt" maxLength="1000" value={exercise.prompt} onChange={set('prompt')}
      placeholder={exercise.taskType === 'TEXT' ? 'Write the article: ___ æble (an apple)' : exercise.taskType === 'SELECT' ? 'Which sentence follows the pattern?' : 'Write three things you can see right now, each with en or et.'} />

    {exercise.taskType === 'SELECT' && <div className="option-list" role="group" aria-label={`Options for exercise ${index + 1}`}>
      <span className="ui-label">Options · tick the correct one</span>
      {exercise.options.map((option, position) => <div className="option-row" key={option.key}>
        <input type="radio" name={`correct-${exercise.uid}`} aria-label={`Option ${option.key} is correct`} checked={exercise.expectedAnswer === option.key} onChange={() => onChange({ ...exercise, expectedAnswer: option.key })} />
        <b>{option.key}</b>
        <input aria-label={`Option ${option.key} text`} maxLength="500" value={option.text} onChange={event => setOption(position, event.target.value)} placeholder="Option text" />
        <Button variant="link" disabled={exercise.options.length <= 2} onClick={() => removeOption(position)}>Remove</Button>
      </div>)}
      <Button variant="quiet" disabled={exercise.options.length >= 10} onClick={() => onChange({ ...exercise, options: [...exercise.options, emptyOption(exercise.options.length)] })}>+ Option</Button>
    </div>}

    {exercise.taskType === 'TEXT' && <FormGrid>
      <Field label="Expected answer" maxLength="500" value={exercise.expectedAnswer} onChange={set('expectedAnswer')} hint="Capitals, punctuation and extra spaces are ignored when grading." />
      <Field as="textarea" rows="2" label="Also accepted" optional value={exercise.acceptedAnswers} onChange={set('acceptedAnswers')} hint="One answer per line, up to ten." />
    </FormGrid>}

    {exercise.taskType !== 'TRANSFER' && <FormGrid>
      <Field label="Feedback when right" optional maxLength="1000" value={exercise.feedbackCorrect} onChange={set('feedbackCorrect')} hint="Why this answer follows the pattern." />
      <Field label="Hint when wrong" optional maxLength="1000" value={exercise.feedbackIncorrect} onChange={set('feedbackIncorrect')} hint="A nudge towards the structure, not the answer." />
    </FormGrid>}
  </fieldset>;
}

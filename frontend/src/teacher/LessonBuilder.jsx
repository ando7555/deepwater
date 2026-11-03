import React, { useState } from 'react';
import { Button, Field, FormGrid, ProblemList, SectionHeading, Segmented } from '../ui/index.jsx';
import { LANGUAGES, languageName } from '../constants.js';
import ExerciseEditor from './ExerciseEditor.jsx';
import { kindById, pick } from './contentKinds.js';
import { BANDS, STAGE_SUGGESTIONS, TASK_TYPES, emptyExercise, lessonProblems, lessonToJson, parseLessonJson, suggestSkillKey, toDraftInput } from './lessonDraft.js';

const LANGUAGE_OPTIONS = LANGUAGES.map(language => ({ id: language.code, label: language.name }));

/**
 * A form that builds one lesson draft, five things to fill in: title, what the learner will be able
 * to do, the explanation, the exercises and the source. Level, stage, skill key and JSON import sit
 * under "More options". `initial` comes from lessonDraft.js (lessonForKind, fromLesson or
 * parseLessonJson); remount with a new key to reset. onSave receives the LessonDraftInput.
 */
export default function LessonBuilder({ initial, busy, onSave, onCancel, cancelLabel = 'Cancel' }) {
  const [form, setForm] = useState(initial);
  const [problems, setProblems] = useState([]);
  const [jsonText, setJsonText] = useState('');
  const [jsonError, setJsonError] = useState('');
  const set = key => event => setForm(current => ({ ...current, [key]: event.target.value }));
  const setExercises = exercises => setForm(current => ({ ...current, exercises }));
  const kind = kindById(form.kind);
  const language = form.languageCode;
  const suggested = suggestSkillKey(form);
  const title = form.revisionOf ? `Revise “${initial.title}”` : kind ? `Add a ${kind.label.toLowerCase()} lesson` : 'Add a lesson';

  function replaceExercise(index, exercise) {
    setExercises(form.exercises.map((item, i) => i === index ? exercise : item));
  }

  function moveExercise(index, direction) {
    const exercises = [...form.exercises];
    const [moved] = exercises.splice(index, 1);
    exercises.splice(index + direction, 0, moved);
    setExercises(exercises);
  }

  async function submit(event) {
    event.preventDefault();
    const found = lessonProblems(form);
    setProblems(found);
    if (found.length) return;
    await onSave(toDraftInput(form));
  }

  function importJson() {
    setJsonError('');
    try { setForm(parseLessonJson(jsonText)); setProblems([]); } catch (importError) { setJsonError(importError.message); }
  }

  return <form className="lesson-builder" onSubmit={submit} aria-label="Lesson builder">
    <SectionHeading level={3}
      eyebrow={form.revisionOf ? 'REVISION' : (kind?.label || 'NEW LESSON').toUpperCase()}
      title={title}
      lead={form.revisionOf ? 'Publishing this revision replaces the current version for learners.' : 'Five things to fill in. The lesson stays a private draft until you approve it under Review & publish.'}
      action={onCancel && <Button variant="link" onClick={onCancel}>{cancelLabel}</Button>} />

    <FormGrid>
      <div className="ui-field">
        <Segmented label="Language" options={LANGUAGE_OPTIONS} value={language} onChange={value => setForm(current => ({ ...current, languageCode: value }))} />
      </div>
      <Field label="Topic" maxLength="120" required value={form.topic} onChange={set('topic')} placeholder={kind?.topic || 'Nouns'} hint="One or two words, so the queue can be searched." />
      <Field wide label="Lesson title" maxLength="240" required value={form.title} onChange={set('title')} placeholder={kind ? pick(kind.objective, language) : 'en or et: learn the word with its article'} />
      <Field wide label="After this lesson the learner can…" maxLength="500" required value={form.objective} onChange={set('objective')} placeholder={kind ? pick(kind.objective, language) : 'Use en and et with everyday nouns.'} />
      <Field wide as="textarea" rows="5" label="Explain it as you would in class" maxLength="3000" required value={form.explanation} onChange={set('explanation')}
        placeholder={kind ? pick(kind.explanation, language) : ''}
        hint={`The learner reads this before the exercises. Short, with one ${languageName(language)} example.`} />
    </FormGrid>

    <div className="builder-exercises">
      <SectionHeading level={3} title="Exercises" lead="Each box is one exercise. The grey text shows the kind of question that fits; write your own in its place." />
      {form.exercises.map((exercise, index) => <ExerciseEditor key={exercise.uid} exercise={exercise} index={index} count={form.exercises.length} language={language}
        onChange={changed => replaceExercise(index, changed)} onMove={direction => moveExercise(index, direction)}
        onRemove={() => setExercises(form.exercises.filter((_, i) => i !== index))} />)}
      <div className="builder-add">
        {TASK_TYPES.map(type => <Button key={type.id} variant="quiet" disabled={form.exercises.length >= 20} onClick={() => setExercises([...form.exercises, emptyExercise(type.id)])}>+ {type.label}</Button>)}
      </div>
    </div>

    <FormGrid>
      <Field wide label="Source and permission" maxLength="500" required value={form.sourceReference} onChange={set('sourceReference')}
        placeholder={kind?.source || 'Deepwater original (2026-11-03), written for Echo'} hint="Where the material comes from and your right to use it. Learners see this line." />
    </FormGrid>

    <details className="builder-more">
      <summary>More options: level, course stage, skill key, JSON</summary>
      <FormGrid>
        <Field as="select" label="Level band" value={form.band} onChange={set('band')} hint="A guide for ordering lessons, not a CEFR placement.">
          {BANDS.map(band => <option key={band} value={band}>{band}</option>)}
        </Field>
        <Field label="Course stage" optional list="stage-suggestions" maxLength="40" value={form.stage} onChange={set('stage')} placeholder="First words" hint="Lessons with the same stage are grouped on the learner’s path." />
        <datalist id="stage-suggestions">{STAGE_SUGGESTIONS.map(stage => <option key={stage} value={stage} />)}</datalist>
        <Field wide label="Skill key" optional maxLength="120" value={form.skillKey} onChange={set('skillKey')} placeholder={suggested} hint={`Groups progress across lessons. Leave empty to use “${suggested || 'language.kind.topic'}”.`} />
        <Field wide as="textarea" label="Lesson as JSON" optional value={jsonText} onChange={event => setJsonText(event.target.value)} spellCheck="false"
          hint="The shape of the lesson files in the repository (see TEACHER_GUIDE.md). Paste one here and load it, or copy this lesson out." />
      </FormGrid>
      <div className="builder-actions">
        <Button variant="quiet" onClick={importJson}>Load JSON into the form</Button>
        <Button variant="quiet" onClick={() => setJsonText(lessonToJson(form))}>Show this lesson as JSON</Button>
        {jsonError && <small className="danger-link">{jsonError}</small>}
      </div>
    </details>

    <ProblemList problems={problems} />
    <div className="builder-actions">
      <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save as private draft'}</Button>
      <small>Next: approve it under Review & publish. Learners see it only after that.</small>
    </div>
  </form>;
}

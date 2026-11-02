import React, { useState } from 'react';
import { Button, Field, FormGrid, ProblemList, SectionHeading } from '../ui/index.jsx';
import { LANGUAGES, languageName } from '../constants.js';
import ExerciseEditor from './ExerciseEditor.jsx';
import { BANDS, STAGE_SUGGESTIONS, TASK_TYPES, emptyExercise, lessonProblems, lessonToJson, parseLessonJson, suggestSkillKey, toDraftInput } from './lessonDraft.js';

/**
 * A form that builds one lesson draft field by field. `initial` is a form object from
 * lessonDraft.js (emptyLesson, fromLesson or parseLessonJson); remount with a new key to reset.
 * onSave receives the LessonDraftInput and returns a promise; the parent reports success.
 */
export default function LessonBuilder({ initial, busy, onSave, onCancel }) {
  const [form, setForm] = useState(initial);
  const [problems, setProblems] = useState([]);
  const [jsonOpen, setJsonOpen] = useState(false);
  const [jsonText, setJsonText] = useState('');
  const [jsonError, setJsonError] = useState('');
  const set = key => event => setForm(current => ({ ...current, [key]: event.target.value }));
  const setExercises = exercises => setForm(current => ({ ...current, exercises }));
  const suggested = suggestSkillKey(form);

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
    try { setForm(parseLessonJson(jsonText)); setProblems([]); setJsonOpen(false); } catch (importError) { setJsonError(importError.message); }
  }

  return <form className="lesson-builder" onSubmit={submit} aria-label="Lesson builder">
    <SectionHeading level={3}
      eyebrow={form.revisionOf ? 'REVISION' : 'NEW LESSON'}
      title={form.revisionOf ? `Revise “${initial.title}”` : 'Add a lesson'}
      lead={form.revisionOf ? 'Publishing this revision replaces the current version for learners.' : 'Fill in the fields below. The lesson stays a private draft until you approve it in the queue.'}
      action={onCancel && <Button variant="link" onClick={onCancel}>Cancel</Button>} />

    <FormGrid>
      <Field as="select" label="Language" value={form.languageCode} onChange={set('languageCode')}>
        {LANGUAGES.map(language => <option key={language.code} value={language.code}>{language.name}</option>)}
      </Field>
      <Field label="Lesson title" maxLength="240" required value={form.title} onChange={set('title')} placeholder="en or et: learn the word with its article" />
      <Field as="select" label="Level band" value={form.band} onChange={set('band')} hint="A guide for ordering lessons, not a CEFR placement.">
        {BANDS.map(band => <option key={band} value={band}>{band}</option>)}
      </Field>
      <Field label="Course stage" optional list="stage-suggestions" maxLength="40" value={form.stage} onChange={set('stage')} placeholder="First words" hint="Lessons with the same stage are grouped on the learner’s path." />
      <datalist id="stage-suggestions">{STAGE_SUGGESTIONS.map(stage => <option key={stage} value={stage} />)}</datalist>
      <Field label="Topic" maxLength="120" required value={form.topic} onChange={set('topic')} placeholder="Nouns" />
      <Field label="Skill key" optional maxLength="120" value={form.skillKey} onChange={set('skillKey')} placeholder={suggested} hint={`Groups progress across lessons. Leave empty to use “${suggested || 'language.topic.title'}”.`} />
      <Field wide label="What the learner will be able to do" maxLength="500" required value={form.objective} onChange={set('objective')} placeholder="Use en and et with everyday nouns." />
      <Field wide as="textarea" rows="5" label="Explain the pattern" maxLength="3000" required value={form.explanation} onChange={set('explanation')}
        hint={`Shown to the learner before the exercises, in the “Notice the structure” box. Write it in the words you would use in class, with one ${languageName(form.languageCode)} example.`} />
      <Field wide label="Source and permission" maxLength="500" required value={form.sourceReference} onChange={set('sourceReference')}
        placeholder="Deepwater original (2026-11-02), written for Echo" hint="Where the material comes from and your right to use it. Learners see this line." />
    </FormGrid>

    <div className="builder-exercises">
      <SectionHeading level={3} title="Exercises" lead="One to twenty. Learners usually see a choice, then a typed answer, then their own example." />
      {form.exercises.map((exercise, index) => <ExerciseEditor key={exercise.uid} exercise={exercise} index={index} count={form.exercises.length}
        onChange={changed => replaceExercise(index, changed)} onMove={direction => moveExercise(index, direction)}
        onRemove={() => setExercises(form.exercises.filter((_, i) => i !== index))} />)}
      <div className="builder-add">
        {TASK_TYPES.map(type => <Button key={type.id} variant="quiet" disabled={form.exercises.length >= 20} onClick={() => setExercises([...form.exercises, emptyExercise(type.id)])}>+ {type.label}</Button>)}
      </div>
    </div>

    <ProblemList problems={problems} />
    <div className="builder-actions">
      <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save as private draft'}</Button>
      <Button variant="link" aria-expanded={jsonOpen} onClick={() => { setJsonOpen(!jsonOpen); if (!jsonOpen) setJsonText(lessonToJson(form)); }}>{jsonOpen ? 'Hide JSON' : 'Import or copy as JSON'}</Button>
    </div>
    {jsonOpen && <div className="builder-json">
      <Field as="textarea" label="Lesson as JSON" value={jsonText} onChange={event => setJsonText(event.target.value)} spellCheck="false"
        hint="The same shape as the lesson files in the repository and TEACHER_GUIDE.md. Paste a lesson here and load it into the form, or copy this one out." />
      <div className="builder-actions">
        <Button variant="quiet" onClick={importJson}>Load into the form</Button>
        <Button variant="quiet" onClick={() => setJsonText(lessonToJson(form))}>Refresh from the form</Button>
        {jsonError && <small className="danger-link">{jsonError}</small>}
      </div>
    </div>}
  </form>;
}

import React, { useState } from 'react';
import { Button, EmptyState, Field, Panel, Pill, SectionHeading, Segmented, Tabs } from '../ui/index.jsx';
import { BAND_LABELS, LANGUAGES, languageName } from '../constants.js';
import LessonBuilder from './LessonBuilder.jsx';
import AudioDraftPanel from './AudioDraftForm.jsx';
import { emptyLesson, fromLesson } from './lessonDraft.js';

const STATUS = {
  DRAFT: { label: 'Draft', tone: 'accent' },
  PUBLISHED: { label: 'Published', tone: 'good' },
  UNPUBLISHED: { label: 'Unpublished', tone: 'warn' },
  ARCHIVED: { label: 'Archived', tone: 'muted' },
};
const STATUS_FILTERS = [{ id: 'ALL', label: 'All' }, { id: 'DRAFT', label: 'Drafts' }, { id: 'PUBLISHED', label: 'Published' }, { id: 'UNPUBLISHED', label: 'Unpublished' }];

/**
 * The teacher's workspace: a lesson queue to review and publish, a form to add lessons,
 * and the music / podcast form. The parent owns the API calls and the success or error banners;
 * onSaveDraft(input) resolves to true when the draft was saved.
 */
export default function TeacherWorkspace({ lessons, onOpen, onSaveDraft, onPublish, onUnpublish, busy }) {
  const [open, setOpen] = useState(false);
  const [view, setView] = useState('queue');
  const [builder, setBuilder] = useState({ key: 0, initial: emptyLesson() });
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [languageFilter, setLanguageFilter] = useState('ALL');

  const drafts = lessons.filter(lesson => lesson.reviewStatus === 'DRAFT').length;
  const shown = lessons.filter(lesson => (statusFilter === 'ALL' || lesson.reviewStatus === statusFilter) && (languageFilter === 'ALL' || lesson.languageCode === languageFilter));

  function startLesson(initial) {
    setBuilder(current => ({ key: current.key + 1, initial }));
    setView('new');
  }

  async function save(input) {
    const saved = await onSaveDraft(input);
    if (saved) { startLesson(emptyLesson(input.languageCode)); setView('queue'); setStatusFilter('ALL'); }
    return saved;
  }

  async function toggle() {
    if (!open) await onOpen();
    setOpen(!open);
  }

  return <Panel className="teacher">
    <SectionHeading eyebrow="TEACHER WORKSPACE" title="Add, review and publish Echo lessons"
      lead="Only your configured teacher account can save drafts or approve publication. Learners see a lesson only after you publish it."
      action={<Button className="practice-button" onClick={toggle} aria-expanded={open}>{open ? 'Hide workspace' : 'Open workspace'}</Button>} />
    {open && <div className="teacher-views">
      <Tabs label="Teacher sections" value={view} onChange={setView} items={[
        { id: 'queue', label: 'Lesson queue', badge: drafts ? `${drafts} to review` : '' },
        { id: 'new', label: builder.initial.revisionOf ? 'Revise lesson' : 'Add a lesson' },
        { id: 'audio', label: 'Music or podcast' },
      ]} />

      {view === 'queue' && <div className="lesson-queue">
        <div className="queue-filters">
          <Segmented label="Filter by status" options={STATUS_FILTERS} value={statusFilter} onChange={setStatusFilter} />
          <Segmented label="Filter by language" options={[{ id: 'ALL', label: 'All languages' }, ...LANGUAGES.map(language => ({ id: language.code, label: language.name }))]} value={languageFilter} onChange={setLanguageFilter} />
          <Button variant="quiet" onClick={() => startLesson(emptyLesson(languageFilter === 'ALL' ? 'da' : languageFilter))}>+ New lesson</Button>
        </div>
        {!shown.length && <EmptyState title={lessons.length ? 'No lessons match these filters.' : 'No lessons yet.'}
          action={!lessons.length && <Button onClick={() => startLesson(emptyLesson())}>Add the first lesson</Button>}>
          {lessons.length ? 'Choose another status or language.' : 'Add one with the form, or import the JSON files from the repository.'}
        </EmptyState>}
        {shown.map(lesson => <LessonQueueItem key={lesson.id} lesson={lesson} busy={busy} onPublish={onPublish} onUnpublish={onUnpublish}
          onRevise={() => startLesson(fromLesson(lesson, { revisionOf: lesson.id }))} onCopy={() => startLesson(fromLesson(lesson))} />)}
      </div>}

      {view === 'new' && <LessonBuilder key={builder.key} initial={builder.initial} busy={busy} onSave={save}
        onCancel={builder.initial.revisionOf || builder.initial.title ? () => { startLesson(emptyLesson()); setView('queue'); } : undefined} />}

      {view === 'audio' && <div className="teacher-content"><AudioDraftPanel busy={busy} onSaveDraft={onSaveDraft} /></div>}
    </div>}
  </Panel>;
}

function LessonQueueItem({ lesson, busy, onPublish, onUnpublish, onRevise, onCopy }) {
  const status = STATUS[lesson.reviewStatus] || { label: lesson.reviewStatus, tone: 'soft' };
  return <article className="queue-item">
    <div className="queue-item-head">
      <div><b>{lesson.title}</b><span className="queue-meta">{languageName(lesson.languageCode)} · {lesson.level} · version {lesson.version}{lesson.audio ? ` · ${lesson.audio.mediaType.toLowerCase()}` : ''}</span></div>
      <Pill tone={status.tone}>{status.label}</Pill>
    </div>
    <p>{lesson.objective}</p>
    <details className="queue-detail-toggle">
      <summary>Read the whole lesson before you decide</summary>
      <div className="queue-detail">
        <p><b>Explanation.</b> {lesson.explanation}</p>
        <p><b>Source.</b> {lesson.sourceReference}</p>
        <small>Skill key {lesson.skillKey} · lesson id {lesson.id}</small>
        {lesson.audio && <AudioDetail audio={lesson.audio} />}
        {lesson.exercises.map(exercise => <div className="teacher-exercise" key={exercise.position}>
          <b>{exercise.position}. {exercise.taskType}: {exercise.prompt}</b>
          {exercise.options.map(option => <small key={option.key}>{option.key}. {option.text}{option.key === exercise.expectedAnswer ? ' · expected answer' : ''}</small>)}
          {exercise.taskType !== 'TRANSFER' && !exercise.options.length && <small>Expected answer: {exercise.expectedAnswer}</small>}
          {exercise.acceptedAnswers.length > 0 && <small>Also accepted: {exercise.acceptedAnswers.join(' · ')}</small>}
          {exercise.taskType !== 'TRANSFER' && <small>Feedback when right: {exercise.feedbackCorrect || '—'} · Hint when wrong: {exercise.feedbackIncorrect || '—'}</small>}
        </div>)}
      </div>
    </details>
    <div className="queue-actions">
      {lesson.reviewStatus === 'DRAFT' && <Button disabled={busy} onClick={() => onPublish(lesson.id)}>I approve &amp; publish</Button>}
      {lesson.reviewStatus === 'PUBLISHED' && <Button variant="quiet" disabled={busy} onClick={() => onUnpublish(lesson)}>Unpublish</Button>}
      {lesson.reviewStatus === 'UNPUBLISHED' && <Button disabled={busy} onClick={() => onPublish(lesson.id)}>Publish again</Button>}
      {!lesson.audio && lesson.reviewStatus !== 'ARCHIVED' && <>
        {lesson.reviewStatus !== 'DRAFT' && <Button variant="link" onClick={onRevise}>Revise</Button>}
        <Button variant="link" onClick={onCopy}>Copy as a new lesson</Button>
      </>}
    </div>
  </article>;
}

function AudioDetail({ audio }) {
  return <div className="teacher-exercise">
    <b>{audio.mediaType} · {audio.sourceTitle}</b>
    <small>By {audio.creator} · {BAND_LABELS[audio.editorialBand]} · {audio.topicTags}</small>
    <small>Rights: {audio.rightsBasis} · link {allowed(audio.linkAllowed)} · playback {allowed(audio.playbackAllowed)} · transcript {allowed(audio.transcriptAllowed)}</small>
    <small>Rights evidence: {audio.rightsReference}{audio.rightsExpiresAt ? ` · through ${audio.rightsExpiresAt}` : ''}</small>
    <small>Before: {audio.beforeListeningPrompt} · Gist: {audio.gistPrompt} · Focus: {audio.focusPrompt}</small>
    {audio.transcriptText && <details><summary>Review transcript</summary><p>{audio.transcriptText}</p></details>}
  </div>;
}

function allowed(flag) {
  return flag ? 'allowed' : 'not allowed';
}

import React, { useState } from 'react';
import { Button, EmptyState, Panel, Pill, SectionHeading, Segmented } from '../ui/index.jsx';
import { BAND_LABELS, LANGUAGES, languageName } from '../constants.js';
import { kindById, kindOfLesson } from './contentKinds.js';

const STATUS = {
  DRAFT: { label: 'Draft', tone: 'accent' },
  PUBLISHED: { label: 'Published', tone: 'good' },
  UNPUBLISHED: { label: 'Unpublished', tone: 'warn' },
  ARCHIVED: { label: 'Archived', tone: 'muted' },
};
const STATUS_FILTERS = [{ id: 'ALL', label: 'All' }, { id: 'DRAFT', label: 'Drafts' }, { id: 'PUBLISHED', label: 'Published' }, { id: 'UNPUBLISHED', label: 'Unpublished' }];

/**
 * The teacher's workspace is one place: read each lesson, then approve and publish it (or take it
 * down). Writing lessons in the browser was removed because it was too hard to use; lessons arrive
 * as drafts from the course import. The parent owns the API calls and the success or error banners.
 */
export default function TeacherWorkspace({ lessons, onOpen, onPublish, onUnpublish, busy }) {
  const [open, setOpen] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [languageFilter, setLanguageFilter] = useState('ALL');

  const drafts = lessons.filter(lesson => lesson.reviewStatus === 'DRAFT').length;
  const shown = lessons.filter(lesson => (statusFilter === 'ALL' || lesson.reviewStatus === statusFilter) && (languageFilter === 'ALL' || lesson.languageCode === languageFilter));

  async function toggle() {
    if (!open) await onOpen();
    setOpen(!open);
  }

  return <Panel className="teacher">
    <SectionHeading eyebrow="TEACHER WORKSPACE" title={drafts ? `Review & publish · ${drafts} waiting` : 'Review & publish'}
      lead="Read each lesson, then approve it. Learners see a lesson only after you publish it, and you can take it down again at any time."
      action={<Button className="practice-button" onClick={toggle} aria-expanded={open}>{open ? 'Hide lessons' : 'Open lessons'}</Button>} />
    {open && <div className="lesson-queue">
      <div className="queue-filters">
        <Segmented label="Filter by status" options={STATUS_FILTERS} value={statusFilter} onChange={setStatusFilter} />
        <Segmented label="Filter by language" options={[{ id: 'ALL', label: 'All languages' }, ...LANGUAGES.map(language => ({ id: language.code, label: language.name }))]} value={languageFilter} onChange={setLanguageFilter} />
      </div>
      {!shown.length && <EmptyState title={lessons.length ? 'No lessons match these filters.' : 'Nothing to review yet.'}>
        {lessons.length ? 'Choose another status or language.' : 'New course lessons appear here as drafts for you to approve.'}
      </EmptyState>}
      {shown.map(lesson => <LessonQueueItem key={lesson.id} lesson={lesson} busy={busy} onPublish={onPublish} onUnpublish={onUnpublish} />)}
    </div>}
  </Panel>;
}

function LessonQueueItem({ lesson, busy, onPublish, onUnpublish }) {
  const status = STATUS[lesson.reviewStatus] || { label: lesson.reviewStatus, tone: 'soft' };
  const kind = kindById(kindOfLesson(lesson));
  return <article className="queue-item">
    <div className="queue-item-head">
      <div><b>{lesson.title}</b><span className="queue-meta">{languageName(lesson.languageCode)} · {lesson.level} · {lesson.topic} · version {lesson.version}{lesson.audio ? ` · ${lesson.audio.mediaType.toLowerCase()}` : ''}</span></div>
      <div className="queue-pills">{kind && <Pill tone="soft">{kind.label}</Pill>}<Pill tone={status.tone}>{status.label}</Pill></div>
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

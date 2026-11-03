import React, { useState } from 'react';
import { Button, EmptyState, Panel, Pill, SectionHeading, Segmented, Tabs } from '../ui/index.jsx';
import { BAND_LABELS, LANGUAGES, languageName } from '../constants.js';
import LessonBuilder from './LessonBuilder.jsx';
import AudioDraftPanel from './AudioDraftForm.jsx';
import { CONTENT_KINDS, kindById, kindOfLesson } from './contentKinds.js';
import { fromLesson, lessonForKind } from './lessonDraft.js';

const STATUS = {
  DRAFT: { label: 'Draft', tone: 'accent' },
  PUBLISHED: { label: 'Published', tone: 'good' },
  UNPUBLISHED: { label: 'Unpublished', tone: 'warn' },
  ARCHIVED: { label: 'Archived', tone: 'muted' },
};
const STATUS_FILTERS = [{ id: 'ALL', label: 'All' }, { id: 'DRAFT', label: 'Drafts' }, { id: 'PUBLISHED', label: 'Published' }, { id: 'UNPUBLISHED', label: 'Unpublished' }];

/**
 * The teacher's workspace, two places: "Add content" (pick a kind, fill in a short form) and
 * "Review & publish" (the queue). The parent owns the API calls and the success or error banners;
 * onSaveDraft(input) resolves to true when the draft was saved.
 */
export default function TeacherWorkspace({ lessons, onOpen, onSaveDraft, onPublish, onUnpublish, busy }) {
  const [open, setOpen] = useState(false);
  const [view, setView] = useState('');
  const [builder, setBuilder] = useState({ key: 0, initial: null });
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [languageFilter, setLanguageFilter] = useState('ALL');

  const drafts = lessons.filter(lesson => lesson.reviewStatus === 'DRAFT').length;
  const shown = lessons.filter(lesson => (statusFilter === 'ALL' || lesson.reviewStatus === statusFilter) && (languageFilter === 'ALL' || lesson.languageCode === languageFilter));
  const current = view || (drafts ? 'queue' : 'add');
  const defaultLanguage = languageFilter === 'ALL' ? 'da' : languageFilter;

  function startLesson(initial) {
    setBuilder(current => ({ key: current.key + 1, initial }));
    setView('add');
  }

  function startKind(kindId) {
    const kind = kindById(kindId);
    startLesson(kind.id === 'listening' ? { kind: 'listening', languageCode: defaultLanguage } : lessonForKind(kind, defaultLanguage));
  }

  function backToKinds() {
    setBuilder(current => ({ key: current.key + 1, initial: null }));
  }

  async function save(input) {
    const saved = await onSaveDraft(input);
    if (saved) { backToKinds(); setView('queue'); setStatusFilter('ALL'); }
    return saved;
  }

  async function toggle() {
    if (!open) await onOpen();
    setOpen(!open);
  }

  return <Panel className="teacher">
    <SectionHeading eyebrow="TEACHER WORKSPACE" title="Add content, then approve it for learners"
      lead="Only your configured teacher account can add or publish. Learners see a lesson only after you approve it."
      action={<Button className="practice-button" onClick={toggle} aria-expanded={open}>{open ? 'Hide workspace' : 'Open workspace'}</Button>} />
    {open && <div className="teacher-views">
      <Tabs label="Teacher sections" value={current} onChange={setView} items={[
        { id: 'add', label: builder.initial?.revisionOf ? 'Revise lesson' : 'Add content' },
        { id: 'queue', label: 'Review & publish', badge: drafts ? `${drafts} to review` : '' },
      ]} />

      {current === 'add' && !builder.initial && <KindPicker onPick={startKind} />}
      {current === 'add' && builder.initial?.kind === 'listening' && <div className="teacher-content">
        <Button variant="link" onClick={backToKinds}>← Choose another kind of content</Button>
        <AudioDraftPanel busy={busy} onSaveDraft={async input => { const saved = await onSaveDraft(input); if (saved) { backToKinds(); setView('queue'); } return saved; }} />
      </div>}
      {current === 'add' && builder.initial && builder.initial.kind !== 'listening' && <LessonBuilder key={builder.key} initial={builder.initial} busy={busy} onSave={save}
        onCancel={backToKinds} cancelLabel={builder.initial.revisionOf || builder.initial.title ? 'Discard' : '← Choose another kind'} />}

      {current === 'queue' && <div className="lesson-queue">
        <div className="queue-filters">
          <Segmented label="Filter by status" options={STATUS_FILTERS} value={statusFilter} onChange={setStatusFilter} />
          <Segmented label="Filter by language" options={[{ id: 'ALL', label: 'All languages' }, ...LANGUAGES.map(language => ({ id: language.code, label: language.name }))]} value={languageFilter} onChange={setLanguageFilter} />
          <Button variant="quiet" onClick={() => { backToKinds(); setView('add'); }}>+ Add content</Button>
        </div>
        {!shown.length && <EmptyState title={lessons.length ? 'No lessons match these filters.' : 'Nothing to review yet.'}
          action={!lessons.length && <Button onClick={() => { backToKinds(); setView('add'); }}>Add the first lesson</Button>}>
          {lessons.length ? 'Choose another status or language.' : 'Add content first; it lands here as a draft for you to approve.'}
        </EmptyState>}
        {shown.map(lesson => <LessonQueueItem key={lesson.id} lesson={lesson} busy={busy} onPublish={onPublish} onUnpublish={onUnpublish}
          onRevise={() => startLesson(fromLesson(lesson, { revisionOf: lesson.id }))} onCopy={() => startLesson(fromLesson(lesson))} />)}
      </div>}
    </div>}
  </Panel>;
}

/** The first step of adding content: one card per kind, in plain words. */
function KindPicker({ onPick }) {
  return <div className="kind-picker">
    <SectionHeading level={3} title="What do you want to add?" lead="Pick one. The form that follows is short and already laid out for that kind of content, in Danish or German." />
    <div className="kind-grid" role="list">
      {CONTENT_KINDS.map(kind => <button type="button" className="kind-card" role="listitem" key={kind.id} onClick={() => onPick(kind.id)}>
        <b>{kind.label}</b>
        <span>{kind.line}</span>
      </button>)}
    </div>
  </div>;
}

function LessonQueueItem({ lesson, busy, onPublish, onUnpublish, onRevise, onCopy }) {
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

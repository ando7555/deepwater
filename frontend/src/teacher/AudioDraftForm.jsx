import React, { useState } from 'react';
import { BAND_LABELS } from '../constants.js';

const NEW_AUDIO_DRAFT = {
  languageCode: 'da', mediaType: 'MUSIC', title: '', sourceTitle: '', creator: '', sourceUrl: '', playbackUrl: '',
  editorialBand: 'A0_A1', topicTags: '', skillKey: '', objective: '', explanation: '', sourceReference: '',
  speechRate: 2, vocabularyDensity: 2, idiomDensity: 2, pronunciationLoad: 2, topicFamiliarity: 3,
  beforeListeningPrompt: '', gistPrompt: '', focusPrompt: '', transcriptText: '', rightsBasis: 'UNVERIFIED',
  linkAllowed: false, playbackAllowed: false, transcriptAllowed: false, rightsReference: '', rightsExpiresAt: '',
};

/** Builds the LessonDraftInput for a music or podcast lesson from the form state. */
export function toAudioDraftInput(audioDraft) {
  const bandLabel = BAND_LABELS[audioDraft.editorialBand];
  const sourceReference = audioDraft.sourceReference.trim() || `${audioDraft.creator} — ${audioDraft.sourceTitle} (${audioDraft.sourceUrl})`;
  return {
    languageCode: audioDraft.languageCode,
    title: audioDraft.title,
    level: bandLabel,
    topic: audioDraft.topicTags,
    objective: audioDraft.objective,
    explanation: audioDraft.explanation,
    sourceReference,
    skillKey: audioDraft.skillKey,
    exercises: [{ taskType: 'TRANSFER', prompt: 'Create an original expression that uses this lesson’s target. Do not copy lines from the recording.' }],
    audio: {
      mediaType: audioDraft.mediaType, sourceTitle: audioDraft.sourceTitle, creator: audioDraft.creator,
      sourceUrl: audioDraft.sourceUrl, ...(audioDraft.playbackUrl ? { playbackUrl: audioDraft.playbackUrl } : {}),
      editorialBand: audioDraft.editorialBand, topicTags: audioDraft.topicTags,
      speechRate: Number(audioDraft.speechRate), vocabularyDensity: Number(audioDraft.vocabularyDensity),
      idiomDensity: Number(audioDraft.idiomDensity), pronunciationLoad: Number(audioDraft.pronunciationLoad),
      topicFamiliarity: Number(audioDraft.topicFamiliarity), beforeListeningPrompt: audioDraft.beforeListeningPrompt,
      gistPrompt: audioDraft.gistPrompt, focusPrompt: audioDraft.focusPrompt,
      ...(audioDraft.transcriptAllowed && audioDraft.transcriptText ? { transcriptText: audioDraft.transcriptText } : {}),
      rightsBasis: audioDraft.rightsBasis, linkAllowed: audioDraft.linkAllowed,
      playbackAllowed: audioDraft.playbackAllowed, transcriptAllowed: audioDraft.transcriptAllowed,
      rightsReference: audioDraft.rightsReference, ...(audioDraft.rightsExpiresAt ? { rightsExpiresAt: audioDraft.rightsExpiresAt } : {}),
    },
  };
}

/** The music / podcast draft form with its own state. onSaveDraft(input) saves and resolves when done. */
export default function AudioDraftPanel({ busy, onSaveDraft }) {
  const [draft, setDraft] = useState(NEW_AUDIO_DRAFT);
  const update = (key, value) => setDraft(current => ({ ...current, [key]: value }));
  async function submit(event) {
    event.preventDefault();
    if (await onSaveDraft(toAudioDraftInput(draft))) setDraft(NEW_AUDIO_DRAFT);
  }
  return <AudioDraftForm draft={draft} update={update} busy={busy} onSubmit={submit} />;
}

function AudioDraftForm({ draft, update, busy, onSubmit }) {
  return <form className="audio-draft-form" onSubmit={onSubmit}>
    <h3>Create a music or podcast draft</h3>
    <p>Build a levelled listening lesson. It remains private until your review and approval.</p>
    <div className="audio-form-grid">
      <label>Learning language<select value={draft.languageCode} onChange={e => update('languageCode', e.target.value)}><option value="da">Danish</option><option value="de">German</option></select></label>
      <label>Medium<select value={draft.mediaType} onChange={e => update('mediaType', e.target.value)}><option value="MUSIC">Music</option><option value="PODCAST">Podcast</option></select></label>
      <label>Editorial level<select value={draft.editorialBand} onChange={e => update('editorialBand', e.target.value)}>{Object.entries(BAND_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select><small>Teacher-curated guide, not a formal CEFR placement.</small></label>
      <label>Echo lesson title<input required maxLength="240" value={draft.title} onChange={e => update('title', e.target.value)} /></label>
      <label>Audio title<input required maxLength="240" value={draft.sourceTitle} onChange={e => update('sourceTitle', e.target.value)} /></label>
      <label>Artist / host / creator<input required maxLength="240" value={draft.creator} onChange={e => update('creator', e.target.value)} /></label>
      <label>Topic tags<input required maxLength="500" placeholder="daily life, work, relationships" value={draft.topicTags} onChange={e => update('topicTags', e.target.value)} /></label>
      <label>Source page URL<input type="url" required placeholder="https://…" value={draft.sourceUrl} onChange={e => update('sourceUrl', e.target.value)} /></label>
      <label>Direct audio URL, only if playback is permitted<input type="url" placeholder="https://…/audio.mp3" value={draft.playbackUrl} onChange={e => update('playbackUrl', e.target.value)} /></label>
      <label>Skill key<input required maxLength="120" placeholder="danish.audio.gist.beginner" value={draft.skillKey} onChange={e => update('skillKey', e.target.value)} /></label>
      <label>Learning objective<input required maxLength="500" value={draft.objective} onChange={e => update('objective', e.target.value)} /></label>
    </div>
    <label>Teacher explanation<textarea required maxLength="3000" value={draft.explanation} onChange={e => update('explanation', e.target.value)} /></label>
    <p className="audio-rating-help">Rate the recording and lesson context from 1 (low) to 5 (high). These are separate signals, not one difficulty score.</p>
    <div className="audio-form-grid audio-ratings">
      {[['speechRate','Speech rate'],['vocabularyDensity','Vocabulary density'],['idiomDensity','Idiom density'],['pronunciationLoad','Pronunciation / reductions'],['topicFamiliarity','Topic familiarity for beginners']].map(([key, label]) => <label key={key}>{label}<select value={draft[key]} onChange={e => update(key, e.target.value)}>{[1,2,3,4,5].map(n => <option key={n} value={n}>{n}</option>)}</select></label>)}
    </div>
    <div className="audio-form-grid">
      <label>Before listening: context or prediction prompt<textarea required maxLength="1000" value={draft.beforeListeningPrompt} onChange={e => update('beforeListeningPrompt', e.target.value)} /></label>
      <label>Listen for gist<textarea required maxLength="1000" value={draft.gistPrompt} onChange={e => update('gistPrompt', e.target.value)} /></label>
      <label>Focus prompt for a phrase, sound, or structure<textarea required maxLength="1000" value={draft.focusPrompt} onChange={e => update('focusPrompt', e.target.value)} /></label>
    </div>
    <fieldset className="rights-fieldset"><legend>Source permissions · confirm each use separately</legend>
      <label>Rights basis<select value={draft.rightsBasis} onChange={e => update('rightsBasis', e.target.value)}><option value="UNVERIFIED">Not checked yet · draft only</option><option value="ORIGINAL">Original / teacher-created</option><option value="LICENSED">Licensed for this use</option><option value="PROVIDER_TERMS">Provider terms permit this use</option></select></label>
      <label className="choice"><input type="checkbox" checked={draft.linkAllowed} onChange={e => update('linkAllowed', e.target.checked)} /><span>The source page may be linked for learners.</span></label>
      <label className="choice"><input type="checkbox" checked={draft.playbackAllowed} onChange={e => update('playbackAllowed', e.target.checked)} /><span>The direct audio URL may be played in Echo.</span></label>
      <label className="choice"><input type="checkbox" checked={draft.transcriptAllowed} onChange={e => { update('transcriptAllowed', e.target.checked); if (!e.target.checked) update('transcriptText', ''); }} /><span>Echo may display this transcript or lyric excerpt.</span></label>
      {draft.transcriptAllowed && <label>Authorized transcript / excerpt<textarea maxLength="5000" value={draft.transcriptText} onChange={e => update('transcriptText', e.target.value)} /><small>Only enter transcript text when your permission covers displaying it to learners.</small></label>}
      <label>Rights evidence or permission reference<input required maxLength="500" placeholder="License, permission record, or terms reviewed" value={draft.rightsReference} onChange={e => update('rightsReference', e.target.value)} /></label>
      <label>Rights review / expiry date (optional)<input type="date" value={draft.rightsExpiresAt} onChange={e => update('rightsExpiresAt', e.target.value)} /></label>
    </fieldset>
    <button disabled={busy}>{busy ? 'Saving draft…' : 'Save private audio draft'}</button>
    <small>Unverified, expired, or unauthorized audio cannot be published. Echo does not fetch or copy the source media.</small>
  </form>;
}

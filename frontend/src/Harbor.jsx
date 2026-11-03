import React, { useEffect, useMemo, useState } from 'react';
import { API } from './api.js';

// Harbor: a talent profile from your CV, matched jobs with visa filters, and your own application tracker.

async function harbor(path, { method = 'GET', body, form } = {}) {
  const token = sessionStorage.getItem('deepwater-token');
  const headers = { Authorization: `Bearer ${token}` };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${API}/api/v1/harbor${path}`, {
    method, headers, body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.detail || 'The request could not be completed.');
  return payload;
}

const FOCUS_COUNTRIES = ['DK', 'SE', 'NO', 'CH'];
const STATUS_LABELS = { SAVED: 'Saved', APPLIED: 'Applied', INTERVIEW: 'Interview', OFFER: 'Offer', REJECTED: 'Rejected', WITHDRAWN: 'Withdrawn' };
const CITIZENSHIP = [
  ['UNKNOWN', 'Prefer not to say'], ['EU_EEA', 'EU / EEA citizen'], ['SWISS', 'Swiss citizen'], ['OTHER', 'Another citizenship (outside EU/EEA and Switzerland)'],
];
const VISA_FILTERS = [
  ['ANY', 'Any'], ['OPEN_TO_ME', 'Open to me (no permit needed, or sponsorship offered)'], ['SPONSORED', 'Posting offers visa sponsorship'],
  ['NO_PERMIT_NEEDED', 'I need no work permit'], ['NOT_RULED_OUT', 'Hide postings that refuse sponsorship'],
];
const VISA_BADGE = {
  OFFERED: ['good', 'Visa sponsorship offered'], NOT_OFFERED: ['warn', 'Says no visa sponsorship'], UNKNOWN: ['muted', 'Visa not mentioned'],
};
const PERMIT_BADGE = {
  NOT_NEEDED: ['good', 'No work permit needed'], NEEDED: ['warn', 'You need a work permit'], CHECK: ['muted', 'Check permit rules'],
};

export default function Harbor() {
  const [view, setView] = useState('jobs');
  const [profile, setProfile] = useState(null);
  const [sources, setSources] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([harbor('/profile'), harbor('/sources')]).then(([p, s]) => {
      setProfile(p); setSources(s);
      if (!p.skills.length) setView('profile');
    }).catch(e => setError(e.message));
  }, []);

  if (error && !profile) return <p role="alert" className="banner error">{error}</p>;
  if (!profile || !sources) return <p role="status" className="loading">Loading Harbor…</p>;

  return <div className="harbor">
    <section className="welcome">
      <p className="eyebrow">HARBOR · YOUR NEXT ROLE, IN ANY COUNTRY</p>
      <h1>Your CV becomes your <span className="harbor-accent">talent profile</span>.</h1>
      <p className="intro">Harbor reads your CV, ranks postings by how well they fit you, and tells you plainly whether you would need a work permit, so visa questions never surprise you.</p>
    </section>
    <nav className="learn-tabs" aria-label="Harbor sections">
      {[['jobs', 'Matched jobs'], ['profile', 'Talent profile'], ['applications', 'My applications']].map(([id, label]) =>
        <button key={id} type="button" className={view === id ? 'active' : ''} aria-current={view === id ? 'page' : undefined} onClick={() => setView(id)}>{label}</button>)}
    </nav>
    {error && <p role="alert" className="banner error">{error}</p>}
    {view === 'profile' && <ProfileView profile={profile} sources={sources} onSaved={next => { setProfile(next); setError(''); }} onDone={() => setView('jobs')} />}
    {view === 'jobs' && <JobsView profile={profile} sources={sources} onSources={setSources} goToProfile={() => setView('profile')} />}
    {view === 'applications' && <ApplicationsView />}
  </div>;
}

function ProfileView({ profile, sources, onSaved, onDone }) {
  const [draft, setDraft] = useState(profile);
  const [pasting, setPasting] = useState(false);
  const [pasted, setPasted] = useState('');
  const [skill, setSkill] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  useEffect(() => setDraft(profile), [profile]);
  const set = (key, value) => setDraft(current => ({ ...current, [key]: value }));

  async function run(action, done) {
    setBusy(true); setError(''); setMessage('');
    try { const next = await action(); onSaved(next); setMessage(done); } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  function upload(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    const form = new FormData(); form.append('file', file);
    run(() => harbor('/cv', { method: 'POST', form }), `Read ${file.name}. Check what Harbor found below, fix anything wrong, then save.`);
    event.target.value = '';
  }

  function save(event) {
    event.preventDefault();
    run(() => harbor('/profile', { method: 'PUT', body: {
      headline: draft.headline, summary: draft.summary, skills: draft.skills, yearsExperience: draft.yearsExperience === '' ? null : draft.yearsExperience,
      languages: draft.languages, citizenship: draft.citizenship, targetCountries: draft.targetCountries, remotePreference: draft.remotePreference,
    } }), 'Profile saved. Your matches use it right away.');
  }

  function addSkill(event) {
    event.preventDefault();
    const clean = skill.trim();
    if (clean && !draft.skills.some(s => s.toLowerCase() === clean.toLowerCase())) set('skills', [...draft.skills, clean]);
    setSkill('');
  }

  const toggleCountry = code => set('targetCountries', draft.targetCountries.includes(code) ? draft.targetCountries.filter(c => c !== code) : [...draft.targetCountries, code]);
  const otherCountries = Object.entries(sources.countries).filter(([code]) => !FOCUS_COUNTRIES.includes(code));

  return <div className="harbor-grid">
    <section className="panel harbor-card">
      <p className="eyebrow">STEP 1 · YOUR CV</p>
      <h2>Upload your CV</h2>
      <p>PDF or plain text, up to 5 MB. Harbor reads it with fixed rules (no AI): role, years, skills, languages and your summary. Your CV stays private to your account and is deleted with it.</p>
      <label className="harbor-upload"><span>{busy ? 'Reading…' : profile.hasCv ? 'Upload a newer CV' : 'Choose a CV file'}</span>
        <input type="file" accept=".pdf,.txt,.md,application/pdf,text/plain" onChange={upload} disabled={busy} /></label>
      <button type="button" className="link" onClick={() => setPasting(!pasting)}>{pasting ? 'Hide' : 'Or paste the text instead'}</button>
      {pasting && <form className="harbor-paste" onSubmit={event => { event.preventDefault(); run(() => harbor('/cv/text', { method: 'POST', body: { text: pasted } }), 'Read your pasted CV. Check what Harbor found below.'); }}>
        <textarea value={pasted} onChange={event => setPasted(event.target.value)} placeholder="Paste your CV here" maxLength="60000" required />
        <button disabled={busy}>Read this text</button>
      </form>}
      {profile.hasCv && <p className="harbor-cv-file">Current CV: <b>{profile.cvFileName}</b> · <button type="button" className="link" disabled={busy} onClick={() => run(() => harbor('/cv', { method: 'DELETE' }), 'Your CV file and its text are deleted. Your profile stays.')}>Delete my CV file</button></p>}
      {profile.cvCheck && <CvCheckCard check={profile.cvCheck} />}
      <div className="method-note"><b>What Harbor reads</b><p>Skills from a list of about 80 common ones, a role from the top of your CV, years from “8 years” or date ranges, and language names. Anything it misses, you add below.</p></div>
    </section>

    <form className="panel harbor-card harbor-profile" onSubmit={save}>
      <p className="eyebrow">STEP 2 · YOUR TALENT PROFILE</p>
      <h2>Check and correct it</h2>
      <label>Headline<input maxLength="200" value={draft.headline || ''} onChange={e => set('headline', e.target.value)} placeholder="Senior Backend Engineer" /></label>
      <label>Years of experience<input type="number" min="0" max="60" value={draft.yearsExperience ?? ''} onChange={e => set('yearsExperience', e.target.value === '' ? '' : Number(e.target.value))} /></label>
      <div className="harbor-field"><span className="harbor-label">Skills <small>{draft.skills.length} · used for matching</small></span>
        <div className="harbor-chips">{draft.skills.map(s => <span className="harbor-chip" key={s}>{s}<button type="button" aria-label={`Remove ${s}`} onClick={() => set('skills', draft.skills.filter(x => x !== s))}>×</button></span>)}
          {!draft.skills.length && <small>No skills yet. Upload a CV or add them here.</small>}</div>
        <div className="harbor-inline"><input value={skill} onChange={e => setSkill(e.target.value)} placeholder="Add a skill, e.g. Camunda" maxLength="60" onKeyDown={e => { if (e.key === 'Enter') addSkill(e); }} /><button type="button" className="secondary" onClick={addSkill}>Add</button></div>
      </div>
      <label>Languages you speak<input value={draft.languages.join(', ')} onChange={e => set('languages', e.target.value.split(',').map(s => s.trimStart()))} placeholder="English, Danish" /></label>
      <label>Citizenship<select value={draft.citizenship} onChange={e => set('citizenship', e.target.value)}>{CITIZENSHIP.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
        <small>Only the group is stored, never your nationality. It decides whether you need a work permit.</small></label>
      <div className="harbor-field"><span className="harbor-label">Where do you want to work?</span>
        <div className="harbor-chips">{FOCUS_COUNTRIES.map(code => <label key={code} className={`harbor-toggle${draft.targetCountries.includes(code) ? ' on' : ''}`}><input type="checkbox" checked={draft.targetCountries.includes(code)} onChange={() => toggleCountry(code)} />{sources.countries[code]}</label>)}</div>
        <select value="" onChange={e => { if (e.target.value) toggleCountry(e.target.value); }} aria-label="Add another country"><option value="">Add another country…</option>{otherCountries.map(([code, name]) => <option key={code} value={code}>{name}{draft.targetCountries.includes(code) ? ' ✓' : ''}</option>)}</select>
        {draft.targetCountries.filter(c => !FOCUS_COUNTRIES.includes(c)).length > 0 && <div className="harbor-chips">{draft.targetCountries.filter(c => !FOCUS_COUNTRIES.includes(c)).map(c => <span className="harbor-chip" key={c}>{sources.countries[c] || c}<button type="button" aria-label={`Remove ${c}`} onClick={() => toggleCountry(c)}>×</button></span>)}</div>}
      </div>
      <label>Remote work<select value={draft.remotePreference} onChange={e => set('remotePreference', e.target.value)}><option value="ANY">On-site or remote</option><option value="REMOTE_ONLY">Remote only</option><option value="ONSITE">On-site only</option></select></label>
      <label>Summary<textarea maxLength="2000" value={draft.summary || ''} onChange={e => set('summary', e.target.value)} /></label>
      <div className="harbor-actions"><button disabled={busy}>{busy ? 'Saving…' : 'Save my profile'}</button>{profile.skills.length > 0 && <button type="button" className="secondary" onClick={onDone}>See my matches →</button>}</div>
      {message && <p role="status" className="notice">{message}</p>}
      {error && <p role="alert" className="error">{error}</p>}
    </form>
  </div>;
}

function JobsView({ profile, sources, onSources, goToProfile }) {
  const [filters, setFilters] = useState({ q: '', country: 'ANY', visa: profile.citizenship === 'OTHER' ? 'OPEN_TO_ME' : 'ANY', remote: 'ANY', minScore: '', source: 'ALL' });
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [refreshNote, setRefreshNote] = useState('');
  const set = (key, value) => setFilters(current => ({ ...current, [key]: value }));

  const query = useMemo(() => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value !== '' && value !== 'ANY' && value !== 'ALL') params.set(key, value); });
    return params.toString();
  }, [filters]);

  async function load() {
    try { setResult(await harbor(`/jobs${query ? `?${query}` : ''}`)); setError(''); } catch (e) { setError(e.message); }
  }
  useEffect(() => { const timer = setTimeout(load, 200); return () => clearTimeout(timer); }, [query]);

  async function track(job, status) {
    try { await harbor(`/applications/${encodeURIComponent(job.id)}`, { method: 'PUT', body: { status } }); await load(); } catch (e) { setError(e.message); }
  }

  async function refresh() {
    try {
      const r = await harbor('/jobs/refresh', { method: 'POST' });
      setRefreshNote(r.message); onSources(await harbor('/sources')); await load();
    } catch (e) { setError(e.message); }
  }

  const guideCountries = filters.country === 'DK,SE,NO,CH' ? FOCUS_COUNTRIES : FOCUS_COUNTRIES.includes(filters.country) ? [filters.country]
    : profile.targetCountries.filter(c => FOCUS_COUNTRIES.includes(c));
  const guides = sources.permitGuides.filter(g => (guideCountries.length ? guideCountries : FOCUS_COUNTRIES).includes(g.code));

  return <div className="harbor-jobs">
    <div className="harbor-source-note" role="note">
      <b>Where these jobs come from</b>
      <span>{sources.sampleJobs} sample postings written for this demo (no real employer behind them){sources.liveEnabled ? `, plus ${sources.liveJobs} live postings from the public Arbeitnow job board` : '. Live postings are turned off on this server'}. </span>
      {sources.liveEnabled && <button type="button" className="link" onClick={refresh}>Refresh live postings</button>}
      {refreshNote && <small>{refreshNote}</small>}
    </div>
    {profile.skills.length === 0 && <p className="banner notice">Your profile has no skills yet, so every match score is low. <button className="link" onClick={goToProfile}>Upload your CV</button></p>}

    <form className="panel harbor-filters" onSubmit={e => e.preventDefault()} aria-label="Job filters">
      <label>Keyword<input value={filters.q} onChange={e => set('q', e.target.value)} placeholder="java, kafka, fintech…" /></label>
      <label>Country<select value={filters.country} onChange={e => set('country', e.target.value)}>
        <option value="ANY">Any country</option><option value="DK,SE,NO,CH">Nordics + Switzerland</option>
        {Object.entries(sources.countries).filter(([code]) => result?.jobsByCountry?.[code]).map(([code, name]) => <option key={code} value={code}>{name} ({result.jobsByCountry[code]})</option>)}
      </select></label>
      <label>Visa and work permit<select value={filters.visa} onChange={e => set('visa', e.target.value)}>{VISA_FILTERS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
      <label>Remote<select value={filters.remote} onChange={e => set('remote', e.target.value)}><option value="ANY">On-site or remote</option><option value="REMOTE">Remote only</option><option value="ONSITE">On-site only</option></select></label>
      <label>Match<select value={filters.minScore} onChange={e => set('minScore', e.target.value)}><option value="">Any match</option><option value="40">40% or better</option><option value="60">60% or better</option><option value="80">80% or better</option></select></label>
      <label>Source<select value={filters.source} onChange={e => set('source', e.target.value)}><option value="ALL">All sources</option><option value="SAMPLE">Sample postings</option><option value="LIVE">Live postings</option></select></label>
    </form>
    {profile.citizenship === 'UNKNOWN' && <p className="harbor-hint">Tell Harbor your citizenship group in your <button className="link" onClick={goToProfile}>talent profile</button> to see whether you need a work permit for each job.</p>}
    {error && <p role="alert" className="banner error">{error}</p>}

    <div className="harbor-results-grid">
      <section aria-label="Matched jobs" className="harbor-results">
        {result && <p className="harbor-count" role="status">{result.jobs.length} of {result.totalJobs} postings match your filters, best fit first.</p>}
        {result?.jobs.map(item => <JobCard key={item.job.id} item={item} onTrack={track} />)}
        {result && !result.jobs.length && <div className="panel harbor-card"><h3>No postings match these filters.</h3><p>Try “Any” for visa, or widen the country.</p></div>}
      </section>
      <aside className="panel harbor-card harbor-guides" aria-label="Work permit guide">
        <p className="eyebrow">WORK PERMITS</p>
        <h3>{profile.citizenship === 'OTHER' ? 'You need a permit in these countries' : profile.citizenship === 'UNKNOWN' ? 'Do you need a permit?' : 'You can work here without a permit'}</h3>
        {profile.citizenship !== 'OTHER' && profile.citizenship !== 'UNKNOWN' && <p>EU/EEA and Swiss citizens can work in the Nordics and Switzerland without a work permit. Some countries still ask you to register your stay.</p>}
        {guides.map(g => <div className="harbor-guide" key={g.code}><b>{g.name}</b><p>{g.nonEuRoute}</p><a href={g.officialUrl} target="_blank" rel="noreferrer">{g.officialSite} ↗</a></div>)}
        <small>A short summary to start from, not legal advice. Rules change, and the official site decides.</small>
      </aside>
    </div>
  </div>;
}

function Badge({ kind, children }) { return <span className={`harbor-badge ${kind}`}>{children}</span>; }

function JobCard({ item, onTrack }) {
  const { job, match, applicationStatus } = item;
  const [visaKind, visaLabel] = VISA_BADGE[job.visaSignal] || VISA_BADGE.UNKNOWN;
  const [permitKind, permitLabel] = PERMIT_BADGE[match.workPermit] || PERMIT_BADGE.CHECK;
  const sample = job.source === 'SAMPLE';
  return <article className="panel harbor-job">
    <div className="harbor-job-head">
      <div className="harbor-score" style={{ '--score': match.score }} aria-label={`Match ${match.score} percent`}><b>{match.score}%</b><small>match</small></div>
      <div className="harbor-job-title">
        <h3>{job.title}</h3>
        <p>{job.company} · {job.location || job.countryName || 'Location not given'}{job.remote ? ' · Remote' : ''}</p>
        <div className="harbor-badges">
          {sample ? <Badge kind="sample">Sample posting</Badge> : <Badge kind="muted">Arbeitnow</Badge>}
          <Badge kind={visaKind}>{visaLabel}</Badge>
          <Badge kind={permitKind}>{permitLabel}</Badge>
          {applicationStatus && <Badge kind="accent">{STATUS_LABELS[applicationStatus]}</Badge>}
        </div>
      </div>
    </div>
    <ul className="harbor-reasons">{match.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>
    <details><summary>Read the posting</summary><p className="harbor-description">{job.description}</p>{job.tags.length > 0 && <small>Tags: {job.tags.join(', ')}</small>}</details>
    <div className="harbor-actions">
      {job.url && <a className="harbor-apply" href={job.url} target="_blank" rel="noreferrer" onClick={() => { if (!applicationStatus) onTrack(job, 'SAVED'); }}>Apply on the original posting ↗</a>}
      {!applicationStatus && <button type="button" className="secondary" onClick={() => onTrack(job, 'SAVED')}>Save</button>}
      {applicationStatus !== 'APPLIED' && <button type="button" className={job.url ? 'secondary' : ''} onClick={() => onTrack(job, 'APPLIED')}>{sample ? 'Mark as applied' : 'I have applied'}</button>}
    </div>
    {sample && <small className="harbor-sample-note">Sample posting: there is no employer to send it to, so “Mark as applied” only records it in your tracker.</small>}
  </article>;
}

function ApplicationsView() {
  const [items, setItems] = useState(null);
  const [notes, setNotes] = useState({});
  const [error, setError] = useState('');
  const load = () => harbor('/applications').then(setItems).catch(e => setError(e.message));
  useEffect(() => { load(); }, []);

  async function update(item, status, note) {
    try { await harbor(`/applications/${encodeURIComponent(item.job.id)}`, { method: 'PUT', body: { status, note } }); await load(); } catch (e) { setError(e.message); }
  }
  async function remove(item) {
    try { await harbor(`/applications/${encodeURIComponent(item.job.id)}`, { method: 'DELETE' }); await load(); } catch (e) { setError(e.message); }
  }
  if (!items) return <p className="loading">Loading your applications…</p>;
  const counts = Object.keys(STATUS_LABELS).map(s => [s, items.filter(i => i.status === s).length]).filter(([, n]) => n);

  return <section className="harbor-applications">
    {error && <p role="alert" className="banner error">{error}</p>}
    <div className="panel harbor-card">
      <p className="eyebrow">YOUR TRACKER</p>
      <h2>{items.length ? `${items.length} role${items.length === 1 ? '' : 's'} you are following` : 'Nothing saved yet'}</h2>
      <p>Harbor never sends anything to employers. You apply on the original posting, then keep track here. Only you can see this list.</p>
      {counts.length > 0 && <div className="harbor-badges">{counts.map(([s, n]) => <Badge key={s} kind="accent">{STATUS_LABELS[s]}: {n}</Badge>)}</div>}
    </div>
    {items.map(item => <article className="panel harbor-job harbor-application" key={item.job.id}>
      <div className="harbor-job-title"><h3>{item.job.title}</h3><p>{item.job.company} · {item.job.location || item.job.countryName}{item.job.source === 'SAMPLE' ? ' · sample posting' : ''}</p>
        <small>Updated {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(item.updatedAt))}</small></div>
      <div className="harbor-inline">
        <label>Status<select value={item.status} onChange={e => update(item, e.target.value, null)}>{Object.entries(STATUS_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label className="harbor-note">Note<input maxLength="1000" value={notes[item.job.id] ?? item.note ?? ''} onChange={e => setNotes(n => ({ ...n, [item.job.id]: e.target.value }))} onBlur={() => { if (notes[item.job.id] !== undefined && notes[item.job.id] !== (item.note ?? '')) update(item, item.status, notes[item.job.id]); }} placeholder="Recruiter name, next step…" /></label>
      </div>
      <div className="harbor-actions">{item.job.url && <a href={item.job.url} target="_blank" rel="noreferrer">Open posting ↗</a>}<button type="button" className="link" onClick={() => remove(item)}>Stop tracking</button></div>
    </article>)}
  </section>;
}

const CHECK_ICON = { PASS: '✓', WARN: '!', FAIL: '✕' };
const CHECK_WORD = { PASS: 'Good', WARN: 'Improve', FAIL: 'Fix' };

function CvCheckCard({ check }) {
  const todo = check.items.filter(i => i.status !== 'PASS').sort((a, b) => (a.status === 'FAIL' ? 0 : 1) - (b.status === 'FAIL' ? 0 : 1));
  const done = check.items.filter(i => i.status === 'PASS');
  const verdict = check.score >= 85 ? 'Ready to send' : check.score >= 60 ? 'A few fixes first' : 'Needs work before you apply';
  const row = item => <li key={item.id} className={`cv-check-item ${item.status.toLowerCase()}`}>
    <span className="cv-check-icon" aria-label={CHECK_WORD[item.status]}>{CHECK_ICON[item.status]}</span>
    <div><b>{item.title}</b><p>{item.detail}</p></div>
  </li>;
  return <section className="cv-check" aria-label="CV check">
    <div className="cv-check-head">
      <div className="harbor-score" style={{ '--score': check.score }} aria-label={`CV check ${check.score} of 100`}><b>{check.score}</b><small>of 100</small></div>
      <div><p className="eyebrow">CV CHECK · STYLE AND FORMAT</p><h3>{verdict}</h3>
        <small>{done.length} of {check.items.length} checks pass.{todo.length > 0 ? ' Fix the points below in your CV file, then upload it again.' : ''}</small></div>
    </div>
    {todo.length > 0 && <ul className="cv-check-list">{todo.map(row)}</ul>}
    {done.length > 0 && <details><summary>{done.length} checks pass</summary><ul className="cv-check-list">{done.map(row)}</ul></details>}
    <small className="cv-check-note">Common recruiter and applicant-tracking guidelines, checked with fixed rules. A guide, not a prediction of interviews.</small>
  </section>;
}

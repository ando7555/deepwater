import React, { useCallback, useEffect, useRef, useState } from 'react';
import { restClient } from '../app/http.js';
import { usePhoto } from '../app/photos.js';
import PhotoManager from '../profile/PhotoManager.jsx';
import { ageFrom, languagesText } from '../profile/profileTools.js';
import { Button, Choice, EmptyState, ErrorState, Field, Loading, FormGrid, Notice, Panel, Pill, ProblemList, SectionHeading, Tabs } from '../ui/index.jsx';
import { CLOSING_NOTES, INTENTS, REPORT_REASONS, addInterest, coverFor, initial, intentWords, isShared, profileProblems, railPosition, turnText } from './amberTools.js';

// Amber: connection by choice. Suggestions explain themselves, and a conversation opens only when both say yes.

const amber = restClient('/api/v1/amber');

export default function Amber({ onOpenProfile }) {
  const [profile, setProfile] = useState(null);
  const [view, setView] = useState('discover');
  const [incoming, setIncoming] = useState(0);
  const [error, setError] = useState('');

  const refreshCount = useCallback(() => {
    amber('/invitations').then(list => setIncoming(list.incoming.length)).catch(() => {});
  }, []);

  const loadProfile = useCallback(() => { setError(''); amber('/profile').then(setProfile).catch(e => setError(e.message)); }, []);
  useEffect(loadProfile, [loadProfile]);
  useEffect(() => { if (profile?.joined) refreshCount(); }, [profile?.joined, view, refreshCount]);

  if (!profile) return error ? <ErrorState title="Amber didn’t load" error={error} onRetry={loadProfile} /> : <Loading label="Loading Amber…" />;

  if (!profile.joined) return <div className="amber-app">
    <AmberIntro />
    <ProfileForm profile={profile} onOpenProfile={onOpenProfile} onSaved={next => { setProfile(next); setView('discover'); }} />
  </div>;

  return <div className="amber-app">
    <section className="welcome amber-welcome">
      <p className="eyebrow">AMBER · CONNECTION, BY CHOICE</p>
      <h1 className="amber-hello">Hi {profile.displayName}. <span className="amber-accent">Nothing opens until you both say yes.</span></h1>
    </section>
    <Tabs label="Amber sections" value={view} onChange={setView} items={[
      { id: 'discover', label: 'Discover' },
      { id: 'invitations', label: 'Invitations', badge: incoming || undefined },
      { id: 'connections', label: 'Conversations' },
      { id: 'profile', label: 'My profile' },
    ]} />
    {view === 'discover' && <Discover profile={profile} goToProfile={() => setView('profile')} />}
    {view === 'invitations' && <Invitations onChanged={refreshCount} openConversations={() => setView('connections')} />}
    {view === 'connections' && <Conversations />}
    {view === 'profile' && <ProfileForm profile={profile} onOpenProfile={onOpenProfile} onSaved={setProfile} onLeft={() => amber('/profile').then(setProfile)} />}
  </div>;
}

function AmberIntro() {
  return <section className="welcome amber-welcome">
    <p className="eyebrow">AMBER · BY DEEPWATER</p>
    <h1>Connection, <span className="amber-accent">by choice</span>.</h1>
    <p className="intro">Every suggestion tells you why it appears, and a conversation opens only when both people say yes. Your Deepwater account is not on Amber until you join here.</p>
    <ul className="amber-principles">
      <li><b>Nothing hidden</b><span>Every suggestion shows the shared choices behind it. No scores, no hidden ranking, no paying to be seen.</span></li>
      <li><b>Both say yes</b><span>No messages until both people accept the same invitation. Photos open then too, unless you choose otherwise.</span></li>
      <li><b>Learn together</b><span>Amber can suggest people who speak the language you are learning, or who learn yours.</span></li>
      <li><b>No ghosting</b><span>End a conversation with one kind sentence instead of silence. Pass, hide, block or report in one step.</span></li>
      <li><b>You choose what shows</b><span>Echo levels and Harbor stay private until you switch them on, one by one.</span></li>
    </ul>
  </section>;
}

// ------------------------------------------------------------------ profile

function ProfileForm({ profile, onSaved, onLeft, onOpenProfile }) {
  const joined = profile.joined;
  const [form, setForm] = useState({
    displayName: profile.displayName ?? '', birthDate: profile.birthDate ?? '', confirmAdult: joined, area: profile.area ?? '',
    intents: profile.intents ?? [], interests: profile.interests ?? [], about: profile.about ?? '',
    visible: profile.visible ?? true, shareLearning: profile.shareLearning ?? false, shareLanguages: profile.shareLanguages ?? true,
    shareHarbor: profile.shareHarbor ?? false, photosVisibility: profile.photosVisibility ?? 'CONNECTED',
  });
  const [photos, setPhotos] = useState(profile.photos ?? []);
  const [typed, setTyped] = useState('');
  const [problem, setProblem] = useState('');
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const set = (key, value) => setForm(current => ({ ...current, [key]: value }));
  // People from the first version typed an age; it counts until they add a birth date.
  const age = form.birthDate ? ageFrom(form.birthDate) : profile.age;
  const problems = profileProblems({ ...form, age });
  const learning = profile.learningDetails ?? [];

  const add = () => {
    const next = addInterest(form.interests, typed);
    setProblem(next.problem);
    if (!next.problem) { set('interests', next.list); setTyped(''); }
  };

  const save = async event => {
    event.preventDefault();
    setTried(true); setMessage(''); setError('');
    if (problems.length) return;
    setBusy(true);
    try {
      const saved = await amber('/profile', { method: 'PUT', body: form });
      setMessage(joined ? 'Saved.' : '');
      onSaved(saved);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };

  return <Panel as="form" className="amber-card" onSubmit={save} noValidate>
    <SectionHeading eyebrow={joined ? 'What you share' : 'Join Amber'} title={joined ? 'Your Amber profile' : 'Create your Amber profile'}
      lead="Amber uses only what is on this page. Your name, birth date, city and bio are part of your one Deepwater profile, so you fill them in once for every product." />
    <FormGrid>
      <Field label="First name others see" maxLength={40} value={form.displayName} onChange={e => set('displayName', e.target.value)} autoComplete="given-name" />
      <Field label="Birth date" type="date" value={form.birthDate} onChange={e => set('birthDate', e.target.value)} autoComplete="bday"
        hint={age != null && age >= 0 ? `Others see your age: ${age}. Never the date.` : 'Others see only your age, never the date.'} />
      <Field label="City" hint="City level only, for example Copenhagen. Never your address." maxLength={80} value={form.area} onChange={e => set('area', e.target.value)} />
      <div className="ui-field">
        <span className="ui-label">Looking for</span>
        <div className="amber-intents">
          {INTENTS.map(intent => <Choice key={intent.id} checked={form.intents.includes(intent.id)}
            onChange={e => set('intents', e.target.checked ? [...form.intents, intent.id] : form.intents.filter(id => id !== intent.id))}>{intent.label}</Choice>)}
        </div>
      </div>
      <div className="ui-field wide">
        <span className="ui-label">Interests <small className="optional"> · up to 12</small></span>
        {form.interests.length > 0 && <div className="harbor-chips">
          {form.interests.map(item => <span key={item} className="harbor-chip">{item}<button type="button" aria-label={`Remove ${item}`}
            onClick={() => set('interests', form.interests.filter(other => other !== item))}>×</button></span>)}
        </div>}
        <div className="amber-add">
          <input aria-label="Add an interest" placeholder="Hiking, jazz, cooking…" value={typed} maxLength={30}
            onChange={e => setTyped(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); add(); } }} />
          <Button variant="quiet" onClick={add}>Add</Button>
        </div>
        {problem && <small className="amber-problem">{problem}</small>}
      </div>
      <Field label="Bio" optional as="textarea" wide rows={3} maxLength={500} value={form.about} onChange={e => set('about', e.target.value)}
        hint={`${form.about.length}/500`} />
    </FormGrid>

    <div className="amber-photos">
      <span className="ui-label">Photos <small className="optional"> · optional, up to 4</small></span>
      <PhotoManager photos={photos} onChange={setPhotos} />
      <fieldset className="amber-reasons">
        <legend>Who sees your photos</legend>
        <Choice type="radio" name="photos-visibility" checked={form.photosVisibility === 'CONNECTED'} onChange={() => set('photosVisibility', 'CONNECTED')}>
          Only people I connect with, after we both say yes <small>(recommended)</small></Choice>
        <Choice type="radio" name="photos-visibility" checked={form.photosVisibility === 'MEMBERS'} onChange={() => set('photosVisibility', 'MEMBERS')}>
          Everyone who sees my profile in Amber</Choice>
      </fieldset>
    </div>

    <div className="amber-switches">
      <Toggle checked={form.visible} onChange={value => set('visible', value)} title="Visible in discovery"
        detail="Turn off to pause. While you are hidden, suggestions pause for you too." />
      <Toggle checked={form.shareLanguages} disabled={!profile.languages?.length} onChange={value => set('shareLanguages', value)}
        title="Show the languages you know"
        detail={profile.languages?.length ? `${languagesText(profile.languages)}. Lets Amber suggest language partners.` : 'Add languages on your Deepwater profile first.'} />
      <Toggle checked={form.shareLearning} disabled={!learning.length} onChange={value => set('shareLearning', value)}
        title="Show what you learn in Echo, with levels"
        detail={learning.length ? `${learning.map(line => `${line.language}: ${line.level}`).join(' · ')}. Off by default. Lessons and answers are never shared.` : 'You have no Echo course or practice exam yet, so there is nothing to show.'} />
      <Toggle checked={form.shareHarbor} disabled={!profile.usesHarbor} onChange={value => set('shareHarbor', value)}
        title='Show "Uses Harbor"'
        detail={profile.usesHarbor ? 'Off by default. Only the fact that you use Harbor is shown; your CV, jobs and applications never are.' : 'You do not use Harbor yet.'} />
    </div>
    {onOpenProfile && <p className="amber-note">Languages, photos and the rest of your details live on <button type="button" className="link" onClick={onOpenProfile}>your Deepwater profile</button>.</p>}

    {!joined && <Choice checked={form.confirmAdult} onChange={e => set('confirmAdult', e.target.checked)}>I am 18 or older.</Choice>}
    {tried && <ProblemList problems={problems} />}
    {error && <Notice tone="error">{error}</Notice>}
    {message && <Notice tone="good">{message}</Notice>}
    <div className="amber-row">
      <Button type="submit" disabled={busy}>{busy ? 'Saving…' : joined ? 'Save profile' : 'Join Amber'}</Button>
    </div>
    {joined && <LeaveAmber onLeft={onLeft} />}
  </Panel>;
}

function Toggle({ checked, onChange, title, detail, disabled }) {
  return <label className={`amber-toggle${disabled ? ' disabled' : ''}`}>
    <span><b>{title}</b><small>{detail}</small></span>
    <input type="checkbox" role="switch" checked={checked} disabled={disabled} onChange={e => onChange(e.target.checked)} />
  </label>;
}

function LeaveAmber({ onLeft }) {
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState('');
  const leave = async () => {
    try { await amber('/profile', { method: 'DELETE' }); onLeft(); } catch (e) { setError(e.message); }
  };
  return <div className="amber-leave">
    {!asking && <Button variant="link" onClick={() => setAsking(true)}>Leave Amber</Button>}
    {asking && <>
      <p>Leaving deletes your Amber profile, invitations and conversations. Your Deepwater account and profile (with photos), Echo and Harbor stay.</p>
      <div className="amber-row"><Button variant="danger" onClick={leave}>Leave Amber</Button><Button variant="quiet" onClick={() => setAsking(false)}>Stay</Button></div>
    </>}
    {error && <Notice tone="error">{error}</Notice>}
  </div>;
}

// ------------------------------------------------------------------ discover

function Discover({ profile, goToProfile }) {
  const [list, setList] = useState(null);
  const [paused, setPaused] = useState('');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [reporting, setReporting] = useState(null);

  const load = useCallback(() => {
    amber('/suggestions').then(next => { setList(next); setPaused(''); })
      .catch(e => { if (e.status === 409) setPaused(e.message); else setError(e.message); });
  }, []);
  useEffect(load, [load, profile.visible]);

  const act = async (person, action) => {
    setError(''); setNotice('');
    try {
      if (action === 'invite') {
        await amber('/invitations', { method: 'POST', body: { memberId: person.memberId } });
        setNotice(`Invitation sent to ${person.displayName}. If ${person.displayName} accepts, a conversation opens for both of you.`);
      } else {
        await amber(`/suggestions/${person.memberId}/${action}`, { method: 'POST' });
        setNotice(action === 'pass' ? `${person.displayName} may come back in 30 days.` : `You will not see ${person.displayName} again.`);
      }
      setList(current => current.filter(item => item.person.memberId !== person.memberId));
    } catch (e) { setError(e.message); }
  };

  if (paused) return <EmptyState title="Suggestions are paused" action={<Button variant="quiet" onClick={goToProfile}>Open my profile</Button>}>{paused}</EmptyState>;
  if (!list) return error ? <ErrorState title="Suggestions didn’t load" error={error} onRetry={() => { setError(''); load(); }} /> : <Loading label="Finding people…" />;

  return <div className="amber-stack">
    {notice && <Notice tone="good">{notice}</Notice>}
    {error && <Notice tone="error">{error}</Notice>}
    {list.length === 0 && <EmptyState title="No suggestions right now">Amber only suggests people who are looking for the same kind of connection as you. Adding interests or changing what you are looking for can widen it.</EmptyState>}
    {list.length > 0 && <Rail label="Suggested people" count={list.length}>
    {list.map(item => <Panel as="article" key={item.person.memberId} className="amber-card amber-person-card" role="listitem">
      <Cover person={item.person} />
      <div className="amber-card-body">
      <PersonFacts person={item.person} />
      <div className="harbor-chips">
        {item.person.interests.map(interest => <span key={interest} className={`amber-chip${isShared(interest, item.sharedInterests) ? ' match' : ''}`}>{interest}</span>)}
      </div>
      {item.person.about && <p className="amber-about">{item.person.about}</p>}
      <div className="amber-why">
        <b>Why you are seeing {item.person.displayName}</b>
        <ul>{item.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>
      </div>
      <div className="amber-actions">
        <Button variant="quiet" onClick={() => act(item.person, 'pass')}>Pass</Button>
        <Button variant="quiet" onClick={() => act(item.person, 'hide')}>Hide</Button>
        <Button onClick={() => act(item.person, 'invite')}>Send invite</Button>
      </div>
      <p className="amber-note">Passing does not lower anyone's visibility, and {item.person.displayName} is never told. <button type="button" className="link" onClick={() => setReporting(item.person)}>Block or report</button></p>
      {reporting?.memberId === item.person.memberId && <SafetySheet person={item.person} onClose={done => { setReporting(null); if (done) setList(current => current.filter(other => other.person.memberId !== item.person.memberId)); }} />}
      </div>
    </Panel>)}
    </Rail>}
  </div>;
}

/**
 * A horizontal row of cards: swipe on a phone, arrows or the keyboard on a computer.
 * Cards snap into place, and the counter says which one is in view.
 */
function Rail({ label, count, children }) {
  const rail = useRef(null);
  const [position, setPosition] = useState(railPosition(0, 0, count));
  const [overflows, setOverflows] = useState(false);
  const step = () => {
    const card = rail.current?.firstElementChild;
    if (!card) return 0;
    const gap = parseFloat(getComputedStyle(rail.current).columnGap) || 0;
    return card.getBoundingClientRect().width + gap;
  };
  const measure = useCallback(() => {
    const el = rail.current;
    if (!el) return;
    setPosition(railPosition(el.scrollLeft, step(), count));
    setOverflows(el.scrollWidth > el.clientWidth + 1);
  }, [count]);
  useEffect(() => {
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure]);
  const move = direction => rail.current?.scrollBy({ left: direction * step(), behavior: 'smooth' });
  const keys = event => {
    if (event.key === 'ArrowRight') { event.preventDefault(); move(1); }
    if (event.key === 'ArrowLeft') { event.preventDefault(); move(-1); }
  };
  return <div className="amber-rail-wrap">
    <div className="amber-rail-bar">
      <span className="amber-rail-count" aria-live="polite">{overflows ? position.label : `${count} ${count === 1 ? 'person' : 'people'}`}</span>
      {overflows && <div className="amber-rail-arrows">
        <button type="button" className="amber-arrow" aria-label="Previous" disabled={position.index === 0} onClick={() => move(-1)}><Chevron left /></button>
        <button type="button" className="amber-arrow" aria-label="Next" disabled={position.index >= count - 1} onClick={() => move(1)}><Chevron /></button>
      </div>}
    </div>
    <div className="amber-rail" ref={rail} role="list" aria-label={label} tabIndex={0} onScroll={measure} onKeyDown={keys}>{children}</div>
  </div>;
}

function Chevron({ left }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={left ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
  </svg>;
}

/**
 * The top of a profile card: the person's photo when this viewer may see it, otherwise a Deepwater blue band with
 * their initial. Tapping the photo shows the next one.
 */
function Cover({ person }) {
  const [from, to] = coverFor(person.displayName);
  const ids = person.photoIds ?? [];
  const [index, setIndex] = useState(0);
  const photo = usePhoto(ids.length ? `/api/v1/amber/members/${person.memberId}/photos/${ids[index % ids.length]}` : null);
  return <div className={`amber-cover${photo ? ' has-photo' : ''}`} style={{ '--cover-a': from, '--cover-b': to }}>
    {photo
      ? <button type="button" className="amber-cover-photo" aria-label={ids.length > 1 ? `Photo ${index % ids.length + 1} of ${ids.length}. Show the next photo` : `Photo of ${person.displayName}`}
          onClick={() => setIndex(current => current + 1)}><img src={photo} alt="" /></button>
      : <span className="amber-cover-initial" aria-hidden="true">{initial(person.displayName)}</span>}
    {ids.length > 1 && <span className="amber-cover-dots" aria-hidden="true">{ids.map((id, i) => <i key={id} className={i === index % ids.length ? 'on' : ''} />)}</span>}
    <div className="amber-cover-text">
      <h3>{person.displayName}, {person.age}</h3>
      <p>{person.area} · {intentWords(person.intents)}</p>
    </div>
    {person.photosAfterYes && <span className="amber-cover-tag">{person.photoCount === 1 ? '1 photo' : `${person.photoCount} photos`} after you both say yes</span>}
  </div>;
}

/** The optional facts a person chose to show: languages, Echo levels, Harbor, and a confirmed email. */
function PersonFacts({ person }) {
  const languages = person.languages ?? [];
  const learning = person.learningDetails ?? [];
  if (!languages.length && !learning.length && !person.usesHarbor && !person.emailConfirmed) return null;
  return <dl className="amber-facts">
    {languages.length > 0 && <><dt>Speaks</dt><dd>{languagesText(languages)}</dd></>}
    {learning.length > 0 && <><dt>Learning</dt><dd>{learning.map(line => `${line.language}: ${line.level}`).join(' · ')}</dd></>}
    {(person.usesHarbor || person.emailConfirmed) && <><dt>Also</dt><dd>{[person.usesHarbor && 'Uses Harbor', person.emailConfirmed && 'Email confirmed'].filter(Boolean).join(' · ')}</dd></>}
  </dl>;
}

function Avatar({ person }) {
  const [from, to] = coverFor(person.displayName);
  const id = person.photoIds?.[0];
  const photo = usePhoto(id ? `/api/v1/amber/members/${person.memberId}/photos/${id}` : null);
  if (photo) return <img className="amber-avatar photo" src={photo} alt="" />;
  return <span className="amber-avatar" aria-hidden="true" style={{ '--cover-a': from, '--cover-b': to }}>{initial(person.displayName)}</span>;
}

function PersonHeader({ person, extra }) {
  return <div className="amber-person">
    <Avatar person={person} />
    <div>
      <h3>{person.displayName}, {person.age}</h3>
      <p>{person.area} · {intentWords(person.intents)}{person.languages?.length ? ` · Speaks ${languagesText(person.languages)}` : ''}</p>
      {extra && <p className="amber-turn">{extra}</p>}
    </div>
  </div>;
}

// ------------------------------------------------------------------ invitations

function Invitations({ onChanged, openConversations }) {
  const [data, setData] = useState(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const load = useCallback(() => { amber('/invitations').then(setData).catch(e => setError(e.message)); }, []);
  useEffect(load, [load]);

  const act = async (invitation, action) => {
    setError(''); setNotice('');
    try {
      await amber(`/invitations/${invitation.id}/${action}`, { method: 'POST' });
      setNotice(action === 'accept' ? `You both said yes. You can now message ${invitation.person.displayName}.`
        : action === 'decline' ? `Declined. ${invitation.person.displayName} sees only that the invitation is closed, never a reason.`
          : 'Invitation withdrawn.');
      load(); onChanged();
    } catch (e) { setError(e.message); }
  };

  if (!data) return error ? <ErrorState title="Invitations didn’t load" error={error} onRetry={() => { setError(''); load(); }} /> : <Loading label="Loading invitations…" />;

  return <div className="amber-stack">
    {notice && <Notice tone="good">{notice} {notice.startsWith('You both') && <button type="button" className="link" onClick={openConversations}>Open conversations</button>}</Notice>}
    {error && <Notice tone="error">{error}</Notice>}
    <SectionHeading level={2} title="Invited you" lead="Nothing opens until you accept. Declining is private." />
    {data.incoming.length === 0 && <EmptyState title="No invitations waiting" />}
    {data.incoming.length > 0 && <Rail label="Invitations to you" count={data.incoming.length}>
    {data.incoming.map(invitation => <Panel as="article" key={invitation.id} className="amber-card amber-person-card" role="listitem">
      <Cover person={invitation.person} />
      <div className="amber-card-body">
      <PersonFacts person={invitation.person} />
      {invitation.person.about && <p className="amber-about">{invitation.person.about}</p>}
      {invitation.reasons.length > 0 && <div className="amber-why"><b>What you share</b><ul>{invitation.reasons.map(r => <li key={r}>{r}</li>)}</ul></div>}
      <div className="amber-actions two">
        <Button variant="quiet" onClick={() => act(invitation, 'decline')}>Decline</Button>
        <Button onClick={() => act(invitation, 'accept')}>Accept</Button>
      </div>
      </div>
    </Panel>)}
    </Rail>}
    <SectionHeading level={2} title="You invited" />
    {data.outgoing.length === 0 && <EmptyState title="No invitations sent" />}
    {data.outgoing.map(invitation => <Panel as="article" key={invitation.id} className="amber-card amber-sent">
      <PersonHeader person={invitation.person} />
      {invitation.status === 'PENDING'
        ? <div className="amber-row"><Pill tone="soft">Waiting for an answer</Pill><Button variant="link" onClick={() => act(invitation, 'withdraw')}>Withdraw</Button></div>
        : <div className="amber-row"><Pill tone="muted">Closed</Pill><small>This invitation will not open a conversation.</small></div>}
    </Panel>)}
  </div>;
}

// ------------------------------------------------------------------ conversations

function Conversations() {
  const [list, setList] = useState(null);
  const [open, setOpen] = useState(null);
  const [error, setError] = useState('');
  const [closed, setClosed] = useState([]);
  const load = useCallback(() => {
    amber('/connections').then(setList).catch(e => setError(e.message));
    amber('/connections/closed').then(setClosed).catch(() => {});
  }, []);
  useEffect(load, [load]);

  if (!list) return error ? <ErrorState title="Conversations didn’t load" error={error} onRetry={() => { setError(''); load(); }} /> : <Loading label="Loading conversations…" />;
  if (open) return <Chat connection={open} onBack={() => { setOpen(null); load(); }} />;

  return <div className="amber-stack">
    {list.length === 0 && <EmptyState title="No conversations yet">A conversation opens when someone accepts your invitation, or you accept theirs.</EmptyState>}
    {list.map(connection => <button type="button" key={connection.id} className="amber-thread" onClick={() => setOpen(connection)}>
      <PersonHeader person={connection.person} extra={turnText(connection, new Date())} />
      <span className="amber-open">Open</span>
    </button>)}
    {closed.length > 0 && <>
      <SectionHeading level={2} title="Closed kindly" lead="These people ended a conversation and said why, so you are not left waiting." />
      {closed.map(item => <Panel as="article" key={item.id} className="amber-card amber-sent">
        <b>{item.displayName}</b><p className="amber-about">“{item.note}”</p>
      </Panel>)}
    </>}
  </div>;
}

function Chat({ connection, onBack }) {
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const [closed, setClosed] = useState(false);
  const [error, setError] = useState('');
  const [safety, setSafety] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const end = useRef(null);
  const name = connection.person.displayName;

  const load = useCallback(() => {
    amber(`/connections/${connection.id}/messages`).then(setMessages)
      .catch(e => { if (e.status === 404) setClosed(true); else setError(e.message); });
  }, [connection.id]);
  useEffect(() => {
    load();
    // No live socket in this first version: the conversation refreshes every 8 seconds while it is open.
    const timer = setInterval(load, 8000);
    return () => clearInterval(timer);
  }, [load]);
  useEffect(() => { end.current?.scrollIntoView?.({ block: 'nearest' }); }, [messages.length]);

  const send = async event => {
    event.preventDefault();
    if (!text.trim()) return;
    setError('');
    try {
      const sent = await amber(`/connections/${connection.id}/messages`, { method: 'POST', body: { body: text } });
      setMessages(current => [...current, sent]); setText('');
    } catch (e) { if (e.status === 404) setClosed(true); else setError(e.message); }
  };

  const [note, setNote] = useState('NOT_A_MATCH');
  const leave = async () => {
    try { await amber(`/connections/${connection.id}/leave`, { method: 'POST', body: { note: note || null } }); onBack(); } catch (e) { setError(e.message); }
  };

  return <Panel className="amber-card amber-chat">
    <div className="amber-row spread">
      <Button variant="link" onClick={onBack}>← Conversations</Button>
      <div className="amber-row">
        <Button variant="quiet" onClick={() => setLeaving(true)}>Leave</Button>
        <Button variant="quiet" onClick={() => setSafety(true)}>Block or report</Button>
      </div>
    </div>
    <PersonHeader person={connection.person} />
    {leaving && <div className="amber-why">
      <b>Leave this conversation?</b>
      <p>It closes for both of you, and neither of you can write again. A short, kind goodbye means {name} is not left wondering.</p>
      <fieldset className="amber-reasons">
        <legend>Your goodbye</legend>
        {CLOSING_NOTES.map(option => <Choice key={option.id} type="radio" name={`note-${connection.id}`} checked={note === option.id}
          onChange={() => setNote(option.id)}>{option.label}</Choice>)}
        <Choice type="radio" name={`note-${connection.id}`} checked={note === ''} onChange={() => setNote('')}>Leave without a note</Choice>
      </fieldset>
      <div className="amber-row"><Button variant="danger" onClick={leave}>Leave conversation</Button><Button variant="quiet" onClick={() => setLeaving(false)}>Stay</Button></div>
    </div>}
    {safety && <SafetySheet person={connection.person} onClose={done => { setSafety(false); if (done) setClosed(true); }} />}
    <div className="amber-messages" aria-live="polite">
      <p className="amber-note center">You both said yes. Either of you can leave or block at any time.</p>
      {messages.map(message => <p key={message.id} className={`amber-bubble ${message.mine ? 'me' : 'them'}`}>{message.body}</p>)}
      <span ref={end} />
    </div>
    {closed
      ? <Notice>This conversation is closed.</Notice>
      : <form className="amber-compose" onSubmit={send}>
        <input aria-label={`Message ${name}`} placeholder={`Message ${name}`} value={text} maxLength={1000} onChange={e => setText(e.target.value)} />
        <Button type="submit" disabled={!text.trim()}>Send</Button>
      </form>}
    {error && <Notice tone="error">{error}</Notice>}
  </Panel>;
}

// ------------------------------------------------------------------ safety

function SafetySheet({ person, onClose }) {
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [done, setDone] = useState('');
  const [error, setError] = useState('');

  const submit = async report => {
    setError('');
    try {
      if (report) await amber('/safety/report', { method: 'POST', body: { memberId: person.memberId, reason, note } });
      else await amber('/safety/block', { method: 'POST', body: { memberId: person.memberId } });
      setDone(report
        ? `Done. ${person.displayName} can no longer see or contact you, and will not know you reported them. Your report is saved for review.`
        : `Done. ${person.displayName} can no longer see or contact you, and is not told.`);
    } catch (e) { setError(e.message); }
  };

  if (done) return <div className="amber-sheet" role="status"><b>{done}</b><Button variant="quiet" onClick={() => onClose(true)}>Close</Button></div>;

  return <div className="amber-sheet">
    <h3>Block or report {person.displayName}</h3>
    <p>Blocking hides you from each other and ends any invitation or conversation. A report also blocks.</p>
    <fieldset className="amber-reasons">
      <legend>Report a reason (optional for blocking)</legend>
      {REPORT_REASONS.map(option => <Choice key={option.id} type="radio" name={`reason-${person.memberId}`} checked={reason === option.id}
        onChange={() => setReason(option.id)}>{option.label}</Choice>)}
    </fieldset>
    {reason && <Field label="What happened?" optional as="textarea" rows={2} maxLength={1000} value={note} onChange={e => setNote(e.target.value)} />}
    {error && <Notice tone="error">{error}</Notice>}
    <div className="amber-row">
      <Button variant="danger" disabled={!reason} onClick={() => submit(true)}>Block and report</Button>
      <Button variant="quiet" onClick={() => submit(false)}>Block only</Button>
      <Button variant="link" onClick={() => onClose(false)}>Cancel</Button>
    </div>
  </div>;
}

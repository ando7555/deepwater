import React, { useCallback, useEffect, useState } from 'react';
import { restClient } from '../app/http.js';
import { Button, EmptyState, ErrorState, Field, FormGrid, Loading, Notice, Panel, Pill, ProblemList, SectionHeading, Tabs, useForm } from '../ui/index.jsx';
import VoiceRoom from './VoiceRoom.jsx';
import { COLUMNS, PRODUCTS, actorLabel, criteriaList, groupByColumn, mcpCommand, ownerActions, productLabel, taskProblems } from './workspaceTools.js';

// The owner's AI workspace: one board for the tasks the AI roles propose and work on, the roles themselves, and
// how Claude Code (with the deepwater-developer plugin) connects to it. Only the owner sees this screen.

const api = restClient('/api/v1/workspace');

export default function Workspace({ onClose }) {
  const [view, setView] = useState('board');
  const [tasks, setTasks] = useState(null);
  const [roles, setRoles] = useState([]);
  const [open, setOpen] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(() => {
    setError('');
    Promise.all([api('/tasks'), api('/roles')]).then(([list, roster]) => { setTasks(list); setRoles(roster); })
      .catch(e => setError(e.message));
  }, []);
  useEffect(load, [load]);

  /** Every move returns the task as it is now; the board reloads so other cards stay true too. */
  async function move(task, action, body = {}) {
    setNotice('');
    try {
      const path = action === 'sendBack' ? 'send-back' : action;
      const next = await api(`/tasks/${task.id}/${path}`, { method: 'POST', body: { version: task.version, ...body } });
      setOpen(current => current && current.id === next.id ? next : current);
      load();
      return next;
    } catch (e) {
      setNotice(e.message);
      if (e.status === 409) load();
      return null;
    }
  }

  async function openTask(task) {
    try { setOpen(await api(`/tasks/${task.id}`)); } catch (e) { setNotice(e.message); }
  }

  if (!tasks) return error ? <ErrorState title="The workspace didn’t load" error={error} onRetry={load} /> : <Loading label="Loading your AI workspace…" />;
  const waiting = tasks.filter(t => t.status === 'PROPOSED' || t.status === 'IN_REVIEW').length;

  return <div className="workspace-app">
    <section className="welcome workspace-welcome">
      <p className="eyebrow">AI WORKSPACE · ONLY YOU SEE THIS</p>
      <h1>Your AI team, <span className="workspace-accent">your decisions</span>.</h1>
      <p className="intro">Talk with five AI roles by voice, and let them propose and build work through Claude Code. Nothing starts until you approve it, and nothing is done until you accept the evidence.</p>
      {onClose && <Button variant="quiet" onClick={onClose}>Back to the app</Button>}
    </section>
    <Tabs label="Workspace sections" value={view} onChange={setView} items={[
      { id: 'board', label: 'Board', badge: waiting || undefined },
      { id: 'voice', label: 'Talk' },
      { id: 'new', label: 'New task' },
      { id: 'team', label: 'AI team' },
      { id: 'connect', label: 'Connect Claude Code' },
    ]} />
    {notice && <Notice tone="error" onDismiss={() => setNotice('')}>{notice}</Notice>}
    {view === 'board' && <Board tasks={tasks} roles={roles} onOpen={openTask} onMove={move} />}
    {view === 'voice' && <VoiceRoom roles={roles} onTasksChanged={load} />}
    {view === 'new' && <NewTask roles={roles} onCreated={task => { load(); setView('board'); setOpen(task); }} />}
    {view === 'team' && <Team roles={roles} />}
    {view === 'connect' && <Connect />}
    {open && <TaskDetail task={open} roles={roles} onMove={move} onClose={() => setOpen(null)} />}
  </div>;
}

function Board({ tasks, roles, onOpen, onMove }) {
  const { groups, rejected } = groupByColumn(tasks);
  if (tasks.length === 0) return <EmptyState title="No tasks yet">Add one under “New task”, or ask a role in Claude Code to propose one with the workspace_propose_task tool.</EmptyState>;
  return <>
    <div className="workspace-board">
      {COLUMNS.map(column => <section key={column.status} className="workspace-column" aria-label={column.label}>
        <header><b>{column.label}</b><span className="count">{groups[column.status].length}</span><small>{column.hint}</small></header>
        {groups[column.status].map(task => <TaskCard key={task.id} task={task} roles={roles} onOpen={onOpen} onMove={onMove} />)}
      </section>)}
    </div>
    {rejected > 0 && <p className="muted">{rejected} rejected {rejected === 1 ? 'task is' : 'tasks are'} kept out of the board.</p>}
  </>;
}

function TaskCard({ task, roles, onOpen, onMove }) {
  const actions = ownerActions(task);
  return <article className="workspace-card">
    <button type="button" className="workspace-card-title" onClick={() => onOpen(task)}>{task.title}</button>
    <div className="workspace-card-tags"><Pill tone="soft">{productLabel(task.product)}</Pill><Pill tone="muted">{task.roleName}</Pill></div>
    <small>Proposed by {actorLabel(task.proposedBy, roles)}</small>
    {actions.includes('approve') && <div className="workspace-card-actions">
      <Button onClick={() => onMove(task, 'approve')}>Approve</Button>
      <Button variant="quiet" onClick={() => onOpen(task)}>Look first</Button>
    </div>}
    {actions.includes('accept') && <div className="workspace-card-actions">
      <Button variant="quiet" onClick={() => onOpen(task)}>Check evidence</Button>
    </div>}
    {actions.includes('handoff') && task.handoff && <div className="workspace-card-actions"><CopyButton text={task.handoff} label="Copy for Claude Code" /></div>}
  </article>;
}

function TaskDetail({ task, roles, onMove, onClose }) {
  const actions = ownerActions(task);
  const [note, setNote] = useState('');
  return <Panel className="workspace-detail" aria-label="Task detail">
    <SectionHeading eyebrow={`${productLabel(task.product)} · ${task.roleName} · ${task.status.replace('_', ' ').toLowerCase()}`} title={task.title}
      action={<Button variant="quiet" onClick={onClose}>Close</Button>} />
    <p>{task.objective}</p>
    <b>Acceptance criteria</b>
    <ul>{task.acceptanceCriteria.map(c => <li key={c}>{c}</li>)}</ul>
    {task.evidence && <><b>Evidence</b><pre className="workspace-evidence">{task.evidence}</pre></>}
    {task.handoff && <><b>Hand-off for Claude Code</b>
      <p className="muted">Paste this into Claude Code with the deepwater-developer plugin. It starts the spec-driven flow for this task and tells the agent how to report back.</p>
      <pre className="workspace-handoff">{task.handoff}</pre><CopyButton text={task.handoff} label="Copy" /></>}
    {(actions.includes('reject') || actions.includes('sendBack') || actions.includes('evidence')) &&
      <Field label={actions.includes('evidence') ? 'Evidence (pull request link and test result lines)' : 'Note for the agent'} optional={!actions.includes('evidence')}
        as="textarea" rows={3} value={note} onChange={e => setNote(e.target.value)} />}
    <div className="workspace-card-actions">
      {actions.includes('approve') && <Button onClick={() => onMove(task, 'approve')}>Approve</Button>}
      {actions.includes('reject') && <Button variant="danger" onClick={() => onMove(task, 'reject', { note })}>Reject</Button>}
      {actions.includes('start') && <Button variant="quiet" onClick={() => onMove(task, 'start')}>Mark as started</Button>}
      {actions.includes('evidence') && <Button onClick={() => onMove(task, 'evidence', { note })}>Send for review</Button>}
      {actions.includes('accept') && <Button onClick={() => onMove(task, 'accept')}>Accept as done</Button>}
      {actions.includes('sendBack') && <Button variant="quiet" onClick={() => onMove(task, 'sendBack', { note })}>Send back</Button>}
    </div>
    {task.events?.length > 0 && <><b>Activity</b>
      <ol className="workspace-activity">{task.events.map((event, i) => <li key={i}>
        <span>{actorLabel(event.actor, roles)}</span> {event.action.replace('_', ' ').toLowerCase()}
        <time dateTime={event.at}> · {new Date(event.at).toLocaleString()}</time>{event.detail && <p>{event.detail}</p>}</li>)}</ol></>}
  </Panel>;
}

function NewTask({ roles, onCreated }) {
  const [form, set] = useForm({ title: '', product: 'ASK_DEEPWATER', roleId: roles[0]?.id || '', objective: '', criteria: '' });
  const [problems, setProblems] = useState([]);
  const [saving, setSaving] = useState(false);
  async function submit(event) {
    event.preventDefault();
    const found = taskProblems(form);
    setProblems(found);
    if (found.length) return;
    setSaving(true);
    try {
      onCreated(await api('/tasks', { method: 'POST', body: { title: form.title, product: form.product, roleId: form.roleId,
        objective: form.objective, acceptanceCriteria: criteriaList(form.criteria) } }));
    } catch (e) { setProblems([e.message]); } finally { setSaving(false); }
  }
  return <Panel as="form" onSubmit={submit}>
    <SectionHeading eyebrow="NEW TASK" title="What should the team do?" lead="Tasks you add start as proposed, like the ones the agents suggest. Approve it on the board when it reads right." />
    <FormGrid>
      <Field label="Title" wide value={form.title} onChange={set('title')} placeholder="Stream Ask Deepwater replies token by token" maxLength={120} />
      <Field label="Product" as="select" value={form.product} onChange={set('product')}>{PRODUCTS.map(p => <option key={p.id} value={p.id}>{p.label}</option>)}</Field>
      <Field label="Role" as="select" value={form.roleId} onChange={set('roleId')}>{roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</Field>
      <Field label="Objective" wide as="textarea" rows={3} value={form.objective} onChange={set('objective')} placeholder="What should be true afterwards, and why it matters." />
      <Field label="Acceptance criteria" hint="One per line. Given / When / Then works well." wide as="textarea" rows={4} value={form.criteria} onChange={set('criteria')} />
    </FormGrid>
    <ProblemList problems={problems} />
    <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Propose task'}</Button>
  </Panel>;
}

function Team({ roles }) {
  return <div className="workspace-team">
    {roles.map(role => <Panel key={role.id} className="workspace-role">
      <SectionHeading eyebrow={role.id} title={role.name} lead={role.purpose} level={3} />
      <div className="workspace-role-lists">
        <div><b>Does</b><ul>{role.does.map(x => <li key={x}>{x}</li>)}</ul></div>
        <div><b>Never</b><ul>{role.doesNot.map(x => <li key={x}>{x}</li>)}</ul></div>
        <div><b>Hands back</b><ul>{role.handsBack.map(x => <li key={x}>{x}</li>)}</ul></div>
      </div>
      <small>Workspace tools: {role.tools.join(', ')}. In Claude Code: “use the {role.id} agent”.</small>
    </Panel>)}
  </div>;
}

function Connect() {
  return <Panel className="workspace-connect">
    <SectionHeading eyebrow="CONNECT CLAUDE CODE" title="Three steps, once per computer" lead="Claude Code is the IDE. The deepwater-developer plugin gives it the five roles and the spec-driven flow; the Deepwater MCP server lets those roles read and update this board." />
    <ol className="workspace-steps">
      <li><b>Install the plugin</b> from your clone of the repository (inside Claude Code):
        <pre>{'/plugin marketplace add <path-to>/deepwater/ai-workspace/deepwater-developer\n/plugin install deepwater-developer@deepwater'}</pre></li>
      <li><b>Put your session token in an environment variable.</b> In this browser, open the developer console and run <code>sessionStorage.getItem('deepwater-token')</code>. Set it as <code>DEEPWATER_TOKEN</code> in your terminal. Never paste it into a chat or a file in the repository. It lasts 7 days.</li>
      <li><b>Connect the MCP server.</b> Opening Claude Code in the deepwater folder is enough: its <code>.mcp.json</code> names the server and reads <code>DEEPWATER_TOKEN</code>; approve “deepwater” when asked. Anywhere else, run:<pre>{mcpCommand()}</pre><CopyButton text={mcpCommand()} label="Copy command" /></li>
    </ol>
    <p>Then, in Claude Code: <em>“use the product-manager agent to read the workspace board”</em>, or copy a task’s hand-off from the board.</p>
  </Panel>;
}

function CopyButton({ text, label }) {
  const [done, setDone] = useState(false);
  return <Button variant="quiet" onClick={() => navigator.clipboard?.writeText(text).then(() => { setDone(true); setTimeout(() => setDone(false), 1500); })}>
    {done ? 'Copied' : label}</Button>;
}

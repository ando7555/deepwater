// Rules for the AI workspace screen, kept out of the JSX so node:test can check them.

/** Board columns, in the order work flows. Rejected tasks are kept out of the board. */
export const COLUMNS = [
  { status: 'PROPOSED', label: 'Proposed', hint: 'Waiting for your approval' },
  { status: 'APPROVED', label: 'Approved', hint: 'Ready to hand to Claude Code' },
  { status: 'IN_PROGRESS', label: 'In progress', hint: 'An agent is working on it' },
  { status: 'IN_REVIEW', label: 'In review', hint: 'Check the evidence, then accept or send back' },
  { status: 'DONE', label: 'Done', hint: 'Accepted by you' },
];

export const PRODUCTS = [
  { id: 'ASK_DEEPWATER', label: 'Ask Deepwater' },
  { id: 'ECHO', label: 'Echo' },
  { id: 'AMBER', label: 'Amber' },
  { id: 'HARBOR', label: 'Harbor' },
  { id: 'AI_FASHION', label: 'AI Fashion' },
  { id: 'PLATFORM', label: 'Platform' },
];

export const MCP_URL = 'https://backend-production-c912.up.railway.app/mcp';

export function productLabel(id) {
  return PRODUCTS.find(product => product.id === id)?.label ?? id;
}

/** Tasks grouped by column; anything not on the board (rejected) is counted separately. */
export function groupByColumn(tasks) {
  const groups = Object.fromEntries(COLUMNS.map(column => [column.status, []]));
  let rejected = 0;
  for (const task of tasks || []) {
    if (groups[task.status]) groups[task.status].push(task);
    else rejected += 1;
  }
  return { groups, rejected };
}

/** The moves the owner can make on a task, by status. Agents never get approve, reject or accept. */
export function ownerActions(task) {
  switch (task?.status) {
    case 'PROPOSED': return ['approve', 'reject', 'edit'];
    case 'APPROVED': return ['handoff', 'start', 'edit'];
    case 'IN_PROGRESS': return ['handoff', 'evidence'];
    case 'IN_REVIEW': return ['accept', 'sendBack'];
    default: return [];
  }
}

/** "agent:ml-engineer" -> "ML engineer agent"; "owner" -> "You". */
export function actorLabel(actor, roles = []) {
  if (!actor || actor === 'owner') return 'You';
  const id = actor.replace(/^agent:/, '');
  const role = roles.find(r => r.id === id);
  return `${role ? role.name : id} agent`;
}

/** Problems that stop a new task from being sent, in the same words the server uses. */
export function taskProblems(form) {
  const problems = [];
  if (!form.title?.trim()) problems.push('Give the task a title.');
  if (form.title && form.title.trim().length > 120) problems.push('Keep the title under 120 characters.');
  if (!form.roleId) problems.push('Pick the role that should do it.');
  if (!form.objective?.trim()) problems.push('Say what the task should achieve.');
  if (criteriaList(form.criteria).length === 0) problems.push('Add at least one acceptance criterion.');
  return problems;
}

/** One criterion per line; blank lines and leading "- " are dropped. */
export function criteriaList(text) {
  return (text || '').split('\n').map(line => line.replace(/^\s*[-*]\s*/, '').trim()).filter(Boolean);
}

/** The one command that connects Claude Code to this workspace (the token is the person's own session token). */
export function mcpCommand(url = MCP_URL) {
  return `claude mcp add --transport http deepwater ${url} --header "Authorization: Bearer $DEEPWATER_TOKEN"`;
}

/** Languages for the voice room: what the browser listens for and the voice it answers in. */
export const VOICE_LANGUAGES = [
  { id: 'en-US', label: 'English' },
  { id: 'pl-PL', label: 'Polski' },
  { id: 'hy-AM', label: 'Հայերեն' },
  { id: 'da-DK', label: 'Dansk' },
];

/** The browser's speech recogniser, or null (Firefox has none: the room then works by typing). */
export function speechRecognizer(win = globalThis.window) {
  return win?.SpeechRecognition || win?.webkitSpeechRecognition || null;
}

/** The conversation as the server wants it: the last turns, user/assistant only, each within the size limit. */
export function talkHistory(turns, { maxTurns = 12, maxChars = 2000 } = {}) {
  return turns.filter(t => (t.role === 'user' || t.role === 'assistant') && t.text?.trim())
    .slice(-maxTurns).map(t => ({ role: t.role, content: t.text.trim().slice(0, maxChars) }));
}

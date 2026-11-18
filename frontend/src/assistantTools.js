// Pure helpers for the Ask Deepwater panel, kept out of the component so node:test can check them.

/** Fallback words for a running tool, used until the server's tool titles arrive (GET /api/v1/assistant). */
export const TOOL_LABELS = {
  echo_overview: 'Looking at your Echo progress',
  echo_cards: 'Checking your phrase cards',
  echo_add_card: 'Adding a phrase card',
  harbor_find_jobs: 'Searching matched jobs in Harbor',
  harbor_applications: 'Opening your application tracker',
  polish_b1_results: 'Reading your Polish B1 results',
  danish_pd3_results: 'Reading your Danish PD3 results',
  german_b1_results: 'Reading your German B1 results',
  amber_my_profile: 'Looking at your own Amber profile',
};

export function toolLabel(name, labels = {}) {
  return labels[name] || TOOL_LABELS[name] || `Using ${name}`;
}

/** Turns the server's tool list [{ name, title }] into a name -> title map. */
export function labelsFromStatus(status) {
  return Object.fromEntries((status?.tools || []).filter(t => t?.name && t?.title).map(t => [t.name, t.title]));
}

/**
 * Splits a server-sent event stream into AG-UI events. Returns the events found in `buffer`
 * and the unfinished tail to keep for the next chunk.
 */
export function parseSse(buffer) {
  const events = [];
  const blocks = buffer.split(/\r?\n\r?\n/);
  const rest = blocks.pop();
  for (const block of blocks) {
    const data = block.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
    if (!data) continue;
    try { events.push(JSON.parse(data)); } catch { /* ignore a malformed event */ }
  }
  return { events, rest };
}

/** Folds one AG-UI event into the chat: assistant text, tool steps and errors. */
export function applyEvent(chat, event, labels = {}) {
  switch (event.type) {
    case 'TEXT_MESSAGE_START':
      return [...chat, { id: event.messageId, role: 'assistant', content: '' }];
    case 'TEXT_MESSAGE_CONTENT':
      return chat.map(m => m.id === event.messageId ? { ...m, content: m.content + event.delta } : m);
    case 'TOOL_CALL_START':
      return [...chat, { id: event.toolCallId, role: 'tool', name: event.toolCallName, label: toolLabel(event.toolCallName, labels), state: 'running' }];
    case 'TOOL_CALL_RESULT':
      return chat.map(m => m.id === event.toolCallId ? { ...m, state: event.isError ? 'error' : 'done', detail: event.isError ? event.content : '' } : m);
    case 'RUN_ERROR':
      return [...chat, { id: `error-${chat.length}`, role: 'error', content: event.message }];
    default:
      return chat;
  }
}

/** The messages the server needs: only the person's questions and the assistant's answers. */
export function historyForServer(chat, { maxHistory = 20, maxChars = 2000 } = {}) {
  return chat.filter(m => (m.role === 'user' || m.role === 'assistant') && m.content.trim())
    .slice(-maxHistory).map(m => ({ role: m.role, content: m.content.slice(0, maxChars) }));
}

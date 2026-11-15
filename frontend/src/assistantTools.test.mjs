import test from 'node:test';
import assert from 'node:assert/strict';
import { applyEvent, historyForServer, labelsFromStatus, parseSse, toolLabel } from './assistantTools.js';

test('parseSse keeps an unfinished event for the next chunk', () => {
  const first = parseSse('data:{"type":"RUN_STARTED"}\n\ndata:{"type":"TEXT_MES');
  assert.deepEqual(first.events, [{ type: 'RUN_STARTED' }]);
  const second = parseSse(first.rest + 'SAGE_END","messageId":"m"}\n\n');
  assert.deepEqual(second.events, [{ type: 'TEXT_MESSAGE_END', messageId: 'm' }]);
  assert.equal(second.rest, '');
});

test('applyEvent builds the answer and the tool steps', () => {
  let chat = [{ id: 'q', role: 'user', content: 'Which cards?' }];
  chat = applyEvent(chat, { type: 'TOOL_CALL_START', toolCallId: 't1', toolCallName: 'echo_cards' });
  assert.equal(chat[1].label, 'Checking your phrase cards');
  chat = applyEvent(chat, { type: 'TOOL_CALL_RESULT', toolCallId: 't1', content: '{}', isError: false });
  assert.equal(chat[1].state, 'done');
  chat = applyEvent(chat, { type: 'TEXT_MESSAGE_START', messageId: 'a' });
  chat = applyEvent(chat, { type: 'TEXT_MESSAGE_CONTENT', messageId: 'a', delta: 'You have ' });
  chat = applyEvent(chat, { type: 'TEXT_MESSAGE_CONTENT', messageId: 'a', delta: '2 cards.' });
  assert.equal(chat[2].content, 'You have 2 cards.');
  assert.deepEqual(historyForServer(chat), [{ role: 'user', content: 'Which cards?' }, { role: 'assistant', content: 'You have 2 cards.' }]);
});

test('a failed tool shows why, and unknown tools still get a label', () => {
  let chat = applyEvent([], { type: 'TOOL_CALL_START', toolCallId: 't', toolCallName: 'echo_add_card' });
  chat = applyEvent(chat, { type: 'TOOL_CALL_RESULT', toolCallId: 't', content: 'Set up your Echo profile first.', isError: true });
  assert.equal(chat[0].state, 'error');
  assert.equal(chat[0].detail, 'Set up your Echo profile first.');
  assert.equal(toolLabel('new_tool'), 'Using new_tool');
});

test('tool titles from the server replace the built-in words', () => {
  const labels = labelsFromStatus({ tools: [{ name: 'echo_cards', title: 'Echo phrase cards' }, { name: 'x' }] });
  assert.deepEqual(labels, { echo_cards: 'Echo phrase cards' });
  const chat = applyEvent([], { type: 'TOOL_CALL_START', toolCallId: 't', toolCallName: 'echo_cards' }, labels);
  assert.equal(chat[0].label, 'Echo phrase cards');
  assert.equal(historyForServer([{ role: 'user', content: 'abcdef' }], { maxChars: 3 })[0].content, 'abc');
});

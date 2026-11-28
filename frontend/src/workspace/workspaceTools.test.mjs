import test from 'node:test';
import assert from 'node:assert/strict';
import { actorLabel, criteriaList, groupByColumn, mcpCommand, ownerActions, productLabel, taskProblems } from './workspaceTools.js';

test('tasks land in their column and rejected ones are only counted', () => {
  const { groups, rejected } = groupByColumn([
    { id: 1, status: 'PROPOSED' }, { id: 2, status: 'IN_REVIEW' }, { id: 3, status: 'REJECTED' }, { id: 4, status: 'PROPOSED' },
  ]);
  assert.deepEqual(groups.PROPOSED.map(t => t.id), [1, 4]);
  assert.deepEqual(groups.IN_REVIEW.map(t => t.id), [2]);
  assert.equal(groups.DONE.length, 0);
  assert.equal(rejected, 1);
});

test('the owner approves proposals and accepts reviewed work; done work has no moves', () => {
  assert.deepEqual(ownerActions({ status: 'PROPOSED' }), ['approve', 'reject', 'edit']);
  assert.ok(ownerActions({ status: 'APPROVED' }).includes('handoff'));
  assert.deepEqual(ownerActions({ status: 'IN_REVIEW' }), ['accept', 'sendBack']);
  assert.deepEqual(ownerActions({ status: 'DONE' }), []);
});

test('actors read as people, not ids', () => {
  const roles = [{ id: 'ml-engineer', name: 'ML engineer' }];
  assert.equal(actorLabel('owner', roles), 'You');
  assert.equal(actorLabel('agent:ml-engineer', roles), 'ML engineer agent');
  assert.equal(actorLabel('agent:unknown', roles), 'unknown agent');
});

test('criteria are one per line and a task needs title, role, objective and criteria', () => {
  assert.deepEqual(criteriaList('- first\n\n * second \n'), ['first', 'second']);
  const ok = { title: 'Stream replies', roleId: 'ml-engineer', objective: 'Faster answers', criteria: 'Text appears in 1 s' };
  assert.deepEqual(taskProblems(ok), []);
  assert.ok(taskProblems({ ...ok, criteria: ' \n ' }).some(p => p.includes('acceptance criterion')));
  assert.ok(taskProblems({ ...ok, roleId: '' }).some(p => p.includes('role')));
});

test('labels and the connect command', () => {
  assert.equal(productLabel('ASK_DEEPWATER'), 'Ask Deepwater');
  assert.match(mcpCommand(), /^claude mcp add --transport http deepwater https:\/\/.+\/mcp --header "Authorization: Bearer \$DEEPWATER_TOKEN"$/);
});

test('the voice room sends recent turns only and finds the browser recogniser', async () => {
  const { talkHistory, speechRecognizer } = await import('./workspaceTools.js');
  const turns = [{ role: 'user', text: ' hi ' }, { role: 'assistant', text: 'Hello' }, { role: 'error', text: 'x' }, { role: 'user', text: '' }];
  assert.deepEqual(talkHistory(turns), [{ role: 'user', content: 'hi' }, { role: 'assistant', content: 'Hello' }]);
  assert.equal(talkHistory(Array.from({ length: 20 }, (_, i) => ({ role: 'user', text: `m${i}` }))).length, 12);
  const Fake = function () {};
  assert.equal(speechRecognizer({ webkitSpeechRecognition: Fake }), Fake);
  assert.equal(speechRecognizer({}), null);
});

test('saved conversation turns come back labelled and in order', async () => {
  const { savedTurns } = await import('./workspaceTools.js');
  const turns = savedTurns([{ role: 'user', text: 'hi', tools: [] }, { role: 'assistant', text: 'Hello', tools: ['workspace_tasks'] }, { role: 'x', text: 'no' }], 'AI architect');
  assert.equal(turns.length, 2);
  assert.equal(turns[1].who, 'AI architect');
  assert.deepEqual(turns[1].tools, ['workspace_tasks']);
  assert.equal(turns[0].who, undefined);
  assert.deepEqual(savedTurns(null, 'x'), []);
});

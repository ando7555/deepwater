import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { Button, EmptyState, ErrorBoundary, ErrorState, Field, Loading, Menu, MenuSection, Notice, Segmented, Tabs } from './index.jsx';
import { ApiError } from '../app/http.js';

const html = element => renderToStaticMarkup(element);

test('Button defaults to type="button" so it never submits a form by accident', () => {
  assert.match(html(h(Button, null, 'Save')), /<button type="button"/);
  assert.match(html(h(Button, { type: 'submit' }, 'Send')), /type="submit"/);
  assert.match(html(h(Button, { variant: 'quiet' }, 'x')), /class="ui-button-quiet"/);
});

test('Notice: errors are alerts, the rest are status; dismiss adds a labelled close button', () => {
  assert.match(html(h(Notice, { tone: 'error' }, 'Broken')), /role="alert"/);
  assert.match(html(h(Notice, { tone: 'good' }, 'Saved')), /role="status"/);
  assert.doesNotMatch(html(h(Notice, null, 'Hi')), /banner-close/);
  const dismissible = html(h(Notice, { onDismiss: () => {} }, 'Hi'));
  assert.match(dismissible, /aria-label="Dismiss"/);
  assert.match(dismissible, /dismissible/);
});

test('Field wraps the control in its label, so clicking the label focuses the input', () => {
  const out = html(h(Field, { label: 'Email', hint: 'We never share it.', type: 'email' }));
  assert.match(out, /^<label class="ui-field"><span class="ui-label">Email<\/span><input type="email"\/><small>We never share it.<\/small><\/label>$/);
  assert.match(html(h(Field, { label: 'Phone', optional: true })), /optional/);
});

test('Tabs marks the current tab for screen readers and shows a badge', () => {
  const out = html(h(Tabs, { label: 'Sections', value: 'b', onChange() {}, items: [{ id: 'a', label: 'A' }, { id: 'b', label: 'B', badge: 3 }] }));
  assert.match(out, /aria-label="Sections"/);
  assert.match(out, /class="active" aria-current="page">B · 3</);
  assert.doesNotMatch(out, />A · /);
});

test('Segmented exposes the chosen option with aria-pressed', () => {
  const out = html(h(Segmented, { label: 'Language', value: 'de', onChange() {}, options: [{ id: 'da', label: 'Danish' }, { id: 'de', label: 'German' }] }));
  assert.match(out, /aria-pressed="false">Danish/);
  assert.match(out, /aria-pressed="true">German/);
});

test('Loading is announced politely and its shimmer is hidden from screen readers', () => {
  const out = html(h(Loading, { label: 'Loading Harbor…' }));
  assert.match(out, /role="status" aria-live="polite"/);
  assert.match(out, /ui-visually-hidden">Loading Harbor…/);
  assert.match(out, /aria-hidden="true" class="ui-skeleton"/);
});

test('ErrorState offers "Try again" only when retrying can help', () => {
  const offline = new ApiError('No connection', { kind: 'offline' });
  const rejected = new ApiError('Use 12–72 characters.', { kind: 'client', status: 400 });
  assert.match(html(h(ErrorState, { error: offline, onRetry() {} })), /Try again/);
  assert.doesNotMatch(html(h(ErrorState, { error: rejected, onRetry() {} })), /Try again/);
  assert.doesNotMatch(html(h(ErrorState, { error: offline })), /Try again/);
  assert.match(html(h(ErrorState, { error: 'Plain text', onRetry() {} })), /role="alert".*Plain text.*Try again/);
});

test('ErrorBoundary turns a crash into a message and clears itself when resetKey changes', () => {
  const crashed = ErrorBoundary.getDerivedStateFromError(new Error('boom'));
  assert.ok(crashed.failed);
  assert.equal(ErrorBoundary.getDerivedStateFromProps({ resetKey: 'harbor' }, { failed: crashed.failed, key: 'echo' }).failed, null);
  assert.equal(ErrorBoundary.getDerivedStateFromProps({ resetKey: 'echo' }, { failed: crashed.failed, key: 'echo' }), null);
  assert.equal(html(h(ErrorBoundary, { name: 'Echo' }, h('p', null, 'fine'))), '<p>fine</p>');
});

test('EmptyState and Menu render their parts', () => {
  assert.match(html(h(EmptyState, { title: 'No cards yet.' }, 'Add one.')), /<b>No cards yet.<\/b><p>Add one.<\/p>/);
  const menu = html(h(Menu, { label: 'Account', summary: 'SB' }, h(MenuSection, { title: 'Appearance' }, 'swatches')));
  assert.match(menu, /<details class="ui-menu align-end"><summary aria-label="Account">SB<\/summary>/);
  assert.match(menu, /ui-menu-title">Appearance/);
});

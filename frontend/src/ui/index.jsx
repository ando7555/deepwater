import React, { Component, useCallback, useEffect, useRef, useState } from 'react';
import { KEYS, readJson, writeJson } from '../app/storage.js';

/*
 * Deepwater UI primitives. Every piece is a thin wrapper over the existing CSS classes in
 * style.css, so screens built from these look the same as the ones built by hand before.
 * See README.md in this folder for examples.
 */

/** The panel surface. `as` picks the element: section (default), article, aside, form, div. */
export function Panel({ as: Tag = 'section', className = '', children, ...rest }) {
  return <Tag className={join('panel', className)} {...rest}>{children}</Tag>;
}

/** Eyebrow line, title and lead, with an optional action (a button or switch) on the right. */
export function SectionHeading({ eyebrow, title, lead, action, level = 2, id, className = '' }) {
  const Title = `h${level}`;
  return <div className={join('ui-heading', className)}>
    <div>
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      {title && <Title id={id}>{title}</Title>}
      {lead && <p className="ui-lead">{lead}</p>}
    </div>
    {action && <div className="ui-heading-action">{action}</div>}
  </div>;
}

/**
 * A labelled control with an optional hint. `as` is input (default), textarea or select;
 * for a select, pass the <option> elements as children. Every other prop goes to the control.
 */
export function Field({ label, hint, optional, as = 'input', wide, className = '', children, ...control }) {
  const Control = as;
  return <label className={join('ui-field', wide && 'wide', className)}>
    <span className="ui-label">{label}{optional && <small className="optional"> · optional</small>}</span>
    {as === 'select' ? <select {...control}>{children}</select> : <Control {...control} />}
    {hint && <small>{hint}</small>}
  </label>;
}

/** A radio or checkbox drawn as a selectable row. */
export function Choice({ type = 'checkbox', children, className = '', ...input }) {
  return <label className={join('choice', className)}><input type={type} {...input} /><span>{children}</span></label>;
}

/** Button variants: primary (default), quiet (outlined), link (text only), danger. Defaults to type="button". */
export function Button({ variant = 'primary', type = 'button', className = '', children, ...rest }) {
  const tone = variant === 'primary' ? '' : variant === 'link' ? 'link' : `ui-button-${variant}`;
  return <button type={type} className={join(tone, className)} {...rest}>{children}</button>;
}

/** A small status label. tone: accent (default), soft, good, warn, muted. */
export function Pill({ tone = 'accent', children, className = '' }) {
  return <span className={join('pill', tone !== 'accent' && tone, className)}>{children}</span>;
}

/** A compact row of exclusive options. options: [{ id, label }]. */
export function Segmented({ options, value, onChange, label }) {
  return <div className="segmented" role="group" aria-label={label}>
    {options.map(option => <button type="button" key={option.id} aria-pressed={value === option.id} onClick={() => onChange(option.id)}>{option.label}</button>)}
  </div>;
}

/** Section tabs. items: [{ id, label, badge }]; a badge is shown after the label. */
export function Tabs({ items, value, onChange, label, className = '' }) {
  return <nav className={join('learn-tabs', className)} aria-label={label}>
    {items.map(item => <button type="button" key={item.id} className={value === item.id ? 'active' : ''}
      aria-current={value === item.id ? 'page' : undefined} onClick={() => onChange(item.id)}>{item.label}{item.badge ? ` · ${item.badge}` : ''}</button>)}
  </nav>;
}

/**
 * A banner message. tone: info (default), good, error. Errors get role="alert"; the rest role="status".
 * Pass onDismiss to show a close button.
 */
export function Notice({ tone = 'info', children, className = '', onDismiss, ...rest }) {
  const cls = tone === 'error' ? 'error' : tone === 'good' ? 'notice' : 'ui-info';
  return <div role={tone === 'error' ? 'alert' : 'status'} className={join('banner', cls, onDismiss && 'dismissible', className)} {...rest}>
    <div className="banner-body">{children}</div>
    {onDismiss && <button type="button" className="banner-close" aria-label="Dismiss" onClick={onDismiss}>×</button>}
  </div>;
}

/** A calm placeholder while something loads. Announced to screen readers; the shimmer stops for reduced motion. */
export function Loading({ label = 'Loading…', lines = 3, className = '' }) {
  return <div role="status" aria-live="polite" className={join('ui-loading', className)}>
    <span className="ui-visually-hidden">{label}</span>
    <div aria-hidden="true" className="ui-skeleton">
      {Array.from({ length: lines }, (_, index) => <span key={index} style={{ width: `${[72, 100, 58, 86][index % 4]}%` }} />)}
    </div>
    <p aria-hidden="true">{label}</p>
  </div>;
}

/**
 * Something could not load. Says what happened in plain words and, when trying again can help,
 * offers it. `error` may be an Error (an ApiError knows whether a retry can help) or a string.
 */
export function ErrorState({ title = 'This didn’t load', error, onRetry, className = '' }) {
  const message = typeof error === 'string' ? error : error?.message;
  const canRetry = onRetry && (typeof error === 'string' || error?.retryable !== false);
  return <div role="alert" className={join('ui-error-state', className)}>
    <b>{title}</b>
    {message && <p>{message}</p>}
    {canRetry && <Button variant="quiet" onClick={onRetry}>Try again</Button>}
  </div>;
}

/**
 * Keeps one part of the app from taking the whole page down. A crash inside shows a short
 * message with "Try again" (which remounts the part) instead of a blank screen.
 * resetKey: when it changes (e.g. the product), the boundary clears itself.
 */
export class ErrorBoundary extends Component {
  constructor(props) { super(props); this.state = { failed: null, key: props.resetKey }; }
  static getDerivedStateFromError(failed) { return { failed }; }
  static getDerivedStateFromProps(props, state) {
    return props.resetKey !== state.key ? { failed: null, key: props.resetKey } : null;
  }
  componentDidCatch(failed, info) { console.error(`[${this.props.name || 'Deepwater'}] crashed`, failed, info?.componentStack); }
  render() {
    if (!this.state.failed) return this.props.children;
    return <ErrorState title={`${this.props.name || 'This part'} stopped working`}
      error="Something went wrong on this screen. Nothing you saved is lost." onRetry={() => this.setState({ failed: null })} />;
  }
}

/**
 * Loads data once (and again when `deps` change) and remembers loading, error and data.
 *   const harbor = useResource(() => api('/profile'), []);
 *   if (!harbor.data) return <ResourceStatus resource={harbor} label="Loading Harbor…" />;
 */
export function useResource(load, deps = []) {
  const [state, setState] = useState({ data: undefined, error: null, loading: true });
  const latest = useRef(0);
  const reload = useCallback(() => {
    const ticket = ++latest.current;
    setState(current => ({ ...current, loading: true, error: null }));
    return Promise.resolve().then(load).then(
      data => { if (ticket === latest.current) setState({ data, error: null, loading: false }); return data; },
      error => { if (ticket === latest.current) setState(current => ({ ...current, error, loading: false })); });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { reload(); }, [reload]);
  const setData = useCallback(update => setState(current => ({ ...current, data: typeof update === 'function' ? update(current.data) : update })), []);
  return { ...state, reload, setData };
}

/** The loading or error view for a useResource that has no data yet. */
export function ResourceStatus({ resource, label, title }) {
  if (resource.error) return <ErrorState title={title} error={resource.error} onRetry={resource.reload} />;
  return <Loading label={label} />;
}

/**
 * Like useState, but survives switching screens and reloading the tab (sessionStorage), so a
 * half-written answer is not lost. clear() forgets it once the work is submitted.
 *   const [answers, setAnswers, clearAnswers] = useDraft('polish-exam', {});
 */
export function useDraft(id, initial) {
  const key = KEYS.draft(id);
  const [value, setValue] = useState(() => readJson(key, { kind: 'session', fallback: initial }));
  useEffect(() => { writeJson(key, value, { kind: 'session' }); }, [key, value]);
  const clear = useCallback(() => { writeJson(key, null, { kind: 'session' }); setValue(initial); }, [key]); // eslint-disable-line react-hooks/exhaustive-deps
  return [value, setValue, clear];
}

/**
 * A small menu behind a button, built on <details> so it works with keyboard and screen readers
 * without extra code. Closes on Escape, on a click outside, and after choosing an item.
 */
export function Menu({ label, summary, children, className = '', align = 'end' }) {
  const ref = useRef(null);
  useEffect(() => {
    const close = event => {
      const node = ref.current;
      if (!node?.open) return;
      if (event.type === 'keydown' ? event.key === 'Escape' : !node.contains(event.target)) {
        node.open = false;
        if (event.type === 'keydown') node.querySelector('summary')?.focus();
      }
    };
    document.addEventListener('pointerdown', close);
    document.addEventListener('keydown', close);
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', close); };
  }, []);
  return <details ref={ref} className={join('ui-menu', `align-${align}`, className)}>
    <summary aria-label={label}>{summary}</summary>
    <div className="ui-menu-panel" onClick={event => { if (event.target.closest('[data-menu-close]') && ref.current) ref.current.open = false; }}>{children}</div>
  </details>;
}

/** A labelled group inside a Menu. */
export function MenuSection({ title, children }) {
  return <div className="ui-menu-section">{title && <span className="ui-menu-title">{title}</span>}{children}</div>;
}

/** A dashed box for "nothing here yet", with an optional action. */
export function EmptyState({ title, children, action, className = '' }) {
  return <div className={join('ui-empty', className)}><b>{title}</b>{children && <p>{children}</p>}{action}</div>;
}

/** A list of things to fix before a form can be submitted. */
export function ProblemList({ title = 'Before saving:', problems }) {
  if (!problems?.length) return null;
  return <div className="ui-problems" role="alert"><b>{title}</b><ul>{problems.map(problem => <li key={problem}>{problem}</li>)}</ul></div>;
}

/** Two-column form layout; a Field with `wide` spans both columns. */
export function FormGrid({ children, className = '' }) {
  return <div className={join('form-grid', className)}>{children}</div>;
}

/** A number with a caption, inside a StatGrid. */
export function Stat({ value, label }) {
  return <div><strong>{value}</strong><span>{label}</span></div>;
}

export function StatGrid({ children }) {
  return <div className="stat-grid">{children}</div>;
}

/**
 * Form state in one object. `set('title')` returns a handler that takes an event or a plain value.
 *   const [form, set, reset, replace] = useForm({ title: '' });
 *   <Field label="Title" value={form.title} onChange={set('title')} />
 */
export function useForm(initial) {
  const [form, setForm] = useState(initial);
  const set = key => valueOrEvent => {
    const target = valueOrEvent?.target;
    const value = target ? (target.type === 'checkbox' ? target.checked : target.value) : valueOrEvent;
    setForm(current => ({ ...current, [key]: value }));
  };
  return [form, set, () => setForm(initial), setForm];
}

function join(...parts) {
  return parts.filter(Boolean).join(' ');
}

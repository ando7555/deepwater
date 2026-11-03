import React, { useState } from 'react';

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

/** A banner message. tone: info (default), good, error. Errors get role="alert"; the rest role="status". */
export function Notice({ tone = 'info', children, className = '', ...rest }) {
  const cls = tone === 'error' ? 'error' : tone === 'good' ? 'notice' : 'ui-info';
  return <p role={tone === 'error' ? 'alert' : 'status'} className={join('banner', cls, className)} {...rest}>{children}</p>;
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

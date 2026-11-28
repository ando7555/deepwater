import React from 'react';
import { Button } from '../ui/index.jsx';
import { COMMON_LANGUAGES, LEVELS, MAX_LANGUAGES } from './profileTools.js';

/** Languages the person knows, each with a level. Rows are edited in place; saving cleans them (cleanLanguages). */
export default function LanguagesEditor({ value, onChange }) {
  const set = (index, key, next) => onChange(value.map((row, i) => (i === index ? { ...row, [key]: next } : row)));
  return <div className="languages-editor">
    {value.map((row, index) => <div className="language-row" key={index}>
      <input aria-label={`Language ${index + 1}`} list="known-languages" maxLength={40} placeholder="Language" value={row.language}
        onChange={e => set(index, 'language', e.target.value)} />
      <select aria-label={`How well you know ${row.language || `language ${index + 1}`}`} value={row.level} onChange={e => set(index, 'level', e.target.value)}>
        <option value="">How well?</option>
        {LEVELS.map(level => <option key={level.id} value={level.id}>{level.label}</option>)}
      </select>
      <Button variant="link" onClick={() => onChange(value.filter((_, i) => i !== index))} aria-label={`Remove ${row.language || 'this language'}`}>Remove</Button>
    </div>)}
    {value.length < MAX_LANGUAGES && <Button variant="quiet" onClick={() => onChange([...value, { language: '', level: '' }])}>Add a language</Button>}
    <datalist id="known-languages">{COMMON_LANGUAGES.map(name => <option key={name} value={name} />)}</datalist>
  </div>;
}

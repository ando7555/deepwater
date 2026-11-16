import React, { useEffect, useState } from 'react';
import { THEMES, THEME_IDS } from './config.js';
import { KEYS, readChoice, write } from './storage.js';

const EVENT = 'deepwater-theme';

export function readTheme() {
  return readChoice(KEYS.theme, THEME_IDS, 'deep');
}

export function applyTheme(id) {
  if (id === 'deep') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', id);
}

/** The colour theme, shared by every component that asks (they stay in step through a window event). */
export function useTheme() {
  const [theme, setTheme] = useState(readTheme);
  useEffect(() => {
    applyTheme(theme);
    write(KEYS.theme, theme);
    window.dispatchEvent(new CustomEvent(EVENT, { detail: theme }));
  }, [theme]);
  useEffect(() => {
    const sync = event => setTheme(event.detail);
    window.addEventListener(EVENT, sync);
    return () => window.removeEventListener(EVENT, sync);
  }, []);
  return [theme, setTheme];
}

export function themeMark(theme) {
  return (THEMES.find(option => option.id === theme) ?? THEMES[0]).mark;
}

export function ThemeSwitch() {
  const [theme, setTheme] = useTheme();
  return <div className="theme-switch" role="group" aria-label="Colour theme">
    {THEMES.map(option => <button key={option.id} type="button" className={`swatch swatch-${option.id}`} aria-pressed={theme === option.id}
      aria-label={option.label} title={option.label} onClick={() => setTheme(option.id)} />)}
  </div>;
}

import React, { useEffect, useRef, useState } from 'react';
import { LAND_ROWS } from './globe-land.js';

// Places where Echo's languages are official. Tapping a pin picks its language.
export const LANGUAGE_PINS = [
  { id: 'cph', languageCode: 'da', city: 'Copenhagen', language: 'Danish', lat: 55.68, lon: 12.57 },
  { id: 'trn', languageCode: 'da', city: 'Tórshavn', language: 'Danish', lat: 62.01, lon: -6.77 },
  { id: 'ber', languageCode: 'de', city: 'Berlin', language: 'German', lat: 52.52, lon: 13.40 },
  { id: 'vie', languageCode: 'de', city: 'Vienna', language: 'German', lat: 48.21, lon: 16.37 },
  { id: 'zrh', languageCode: 'de', city: 'Zürich', language: 'German', lat: 47.37, lon: 8.54 },
];

const RAD = Math.PI / 180;
const LAND = LAND_ROWS.flatMap(([lat, count, ...columns]) =>
  columns.map(column => toVector(lat, -180 + (column + 0.5) * 360 / count)));

function toVector(lat, lon) {
  const phi = lat * RAD, lambda = lon * RAD;
  return [Math.cos(phi) * Math.sin(lambda), Math.sin(phi), Math.cos(phi) * Math.cos(lambda)];
}

// Rotates a unit vector so that (centerLat, centerLon) faces the viewer; z > 0 is the visible side.
function rotate([x, y, z], spin, tilt) {
  const cs = Math.cos(spin), ss = Math.sin(spin);
  const x1 = x * cs - z * ss, z1 = x * ss + z * cs;
  const ct = Math.cos(tilt), st = Math.sin(tilt);
  return [x1, y * ct - z1 * st, y * st + z1 * ct];
}

// Great-circle points between two pins, lifted off the surface so the route reads as 3D.
function arcPoints(a, b, steps = 32) {
  const va = toVector(a.lat, a.lon), vb = toVector(b.lat, b.lon);
  const omega = Math.acos(Math.min(1, va[0] * vb[0] + va[1] * vb[1] + va[2] * vb[2]));
  return Array.from({ length: steps + 1 }, (_, i) => {
    const t = i / steps, lift = 1 + 0.12 * Math.sin(Math.PI * t) * Math.min(1, omega * 2);
    const wa = Math.sin((1 - t) * omega) / Math.sin(omega), wb = Math.sin(t * omega) / Math.sin(omega);
    return [0, 1, 2].map(k => (wa * va[k] + wb * vb[k]) * lift);
  });
}

const ROUTES = ['da', 'de'].flatMap(code => {
  const pins = LANGUAGE_PINS.filter(pin => pin.languageCode === code);
  return pins.slice(1).map((pin, i) => ({ languageCode: code, points: arcPoints(pins[i], pin) }));
});

function readColors() {
  const style = getComputedStyle(document.documentElement);
  const value = name => style.getPropertyValue(name).trim();
  return { ocean: value('--surface-2'), deep: value('--abyss'), land: value('--accent'), line: value('--line'),
    pin: value('--amber'), active: value('--accent'), text: value('--text') };
}

export default function Globe({ size = 320, activeLanguage, onSelect, showPinList = true, label = 'Globe showing where Danish and German are spoken' }) {
  const canvasRef = useRef(null);
  const view = useRef({ spin: 12 * RAD, tilt: 38 * RAD, drag: null, hover: false, screenPins: [] });
  const [selected, setSelected] = useState(null);

  useEffect(() => {
    const canvas = canvasRef.current, context = canvas.getContext('2d');
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let colors = readColors(), frame = 0, last = performance.now();
    const themeWatch = new MutationObserver(() => { colors = readColors(); });
    themeWatch.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

    function draw(now) {
      const state = view.current, dpr = window.devicePixelRatio || 1;
      if (canvas.width !== size * dpr || canvas.height !== size * dpr) { canvas.width = size * dpr; canvas.height = size * dpr; }
      if (!reduceMotion && !state.drag && !state.hover) state.spin -= (now - last) * 0.00012;
      last = now;
      const r = size * 0.42, c = size / 2;
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      context.clearRect(0, 0, size, size);

      // Ocean sphere with a soft light from the upper left.
      const ocean = context.createRadialGradient(c - r * 0.35, c - r * 0.4, r * 0.1, c, c, r);
      ocean.addColorStop(0, colors.ocean); ocean.addColorStop(1, colors.deep);
      context.beginPath(); context.arc(c, c, r, 0, Math.PI * 2);
      context.fillStyle = ocean; context.fill();
      context.lineWidth = 1; context.strokeStyle = colors.line; context.stroke();

      // Land as dots; nearer dots are larger and brighter, so the sphere reads as 3D.
      context.fillStyle = colors.land;
      for (const point of LAND) {
        const [x, y, z] = rotate(point, state.spin, state.tilt);
        if (z <= 0) continue;
        context.globalAlpha = 0.25 + 0.7 * z;
        context.fillRect(c + x * r - 0.8 - z * 0.6, c - y * r - 0.8 - z * 0.6, 1.6 + z * 1.2, 1.6 + z * 1.2);
      }

      // Dashed routes between cities of the same language.
      context.setLineDash([3, 4]); context.lineWidth = 1.2;
      for (const route of ROUTES) {
        context.strokeStyle = route.languageCode === activeLanguage ? colors.active : colors.text;
        context.beginPath();
        let drawing = false;
        for (const point of route.points) {
          const [x, y, z] = rotate(point, state.spin, state.tilt);
          if (z < -0.05) { drawing = false; continue; }
          context.globalAlpha = 0.35 + 0.5 * Math.max(0, z);
          if (drawing) context.lineTo(c + x * r, c - y * r); else context.moveTo(c + x * r, c - y * r);
          drawing = true;
        }
        context.stroke();
      }
      context.setLineDash([]);

      // Pins, with a pulsing halo on the learner's current language.
      state.screenPins = [];
      for (const pin of LANGUAGE_PINS) {
        const [x, y, z] = rotate(toVector(pin.lat, pin.lon), state.spin, state.tilt);
        if (z <= 0.05) continue;
        const px = c + x * r, py = c - y * r, active = pin.languageCode === activeLanguage;
        state.screenPins.push({ pin, x: px, y: py });
        const pulse = reduceMotion ? 0.5 : (Math.sin(now / 500) + 1) / 2;
        context.globalAlpha = 0.25 * z;
        context.beginPath(); context.arc(px, py, 7 + (active ? pulse * 5 : 2), 0, Math.PI * 2);
        context.fillStyle = active ? colors.active : colors.pin; context.fill();
        context.globalAlpha = 1;
        context.beginPath(); context.arc(px, py, 3.5, 0, Math.PI * 2); context.fill();
      }

      // Rim light.
      context.globalAlpha = 1;
      const rim = context.createRadialGradient(c, c, r * 0.85, c, c, r * 1.12);
      rim.addColorStop(0, 'transparent'); rim.addColorStop(0.5, `${colors.active}33`); rim.addColorStop(1, 'transparent');
      context.fillStyle = rim; context.beginPath(); context.arc(c, c, r * 1.12, 0, Math.PI * 2); context.fill();
      frame = requestAnimationFrame(draw);
    }
    frame = requestAnimationFrame(draw);
    return () => { cancelAnimationFrame(frame); themeWatch.disconnect(); };
  }, [size, activeLanguage]);

  function pinAt(event) {
    const box = canvasRef.current.getBoundingClientRect();
    const x = (event.clientX - box.left) * size / box.width, y = (event.clientY - box.top) * size / box.height;
    let nearest = null, best = 14;
    for (const item of view.current.screenPins) {
      const distance = Math.hypot(item.x - x, item.y - y);
      if (distance < best) { best = distance; nearest = item.pin; }
    }
    return nearest;
  }

  function choose(pin) {
    setSelected(pin);
    onSelect?.(pin);
  }

  function pointerDown(event) {
    view.current.drag = { x: event.clientX, y: event.clientY, spin: view.current.spin, tilt: view.current.tilt, moved: false };
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function pointerMove(event) {
    const drag = view.current.drag;
    if (!drag) { event.currentTarget.style.cursor = pinAt(event) ? 'pointer' : 'grab'; return; }
    const dx = event.clientX - drag.x, dy = event.clientY - drag.y;
    if (Math.abs(dx) + Math.abs(dy) > 3) drag.moved = true;
    view.current.spin = drag.spin - dx * 0.008;
    view.current.tilt = Math.max(-80 * RAD, Math.min(80 * RAD, drag.tilt + dy * 0.008));
  }

  function pointerUp(event) {
    const drag = view.current.drag;
    view.current.drag = null;
    if (drag && !drag.moved) { const pin = pinAt(event); if (pin) choose(pin); }
  }

  return <figure className="globe">
    <canvas ref={canvasRef} style={{ width: size, height: size }} role="img" aria-label={label}
      onPointerDown={pointerDown} onPointerMove={pointerMove} onPointerUp={pointerUp}
      onPointerEnter={() => { view.current.hover = true; }} onPointerLeave={() => { view.current.hover = false; view.current.drag = null; }} />
    <figcaption>
      <span className="globe-hint">Drag to turn the globe · tap a pin to pick a language</span>
      {showPinList && <span className="globe-pins">{LANGUAGE_PINS.map(pin => <button type="button" key={pin.id}
        className={`link globe-pin${pin.languageCode === activeLanguage ? ' active' : ''}`}
        aria-pressed={selected?.id === pin.id} onClick={() => choose(pin)}>{pin.language} · {pin.city}</button>)}</span>}
    </figcaption>
  </figure>;
}

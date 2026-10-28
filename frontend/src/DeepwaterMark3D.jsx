import React, { useEffect, useRef } from 'react';

// The Deepwater mark as a 3D object: a lid, two water layers that dip towards the centre,
// and the sounding line with five depth nodes. Units follow the 120×120 SVG mark divided by 50.
const LID = { y: 0.6, halfWidth: 1, halfDepth: 0.72 };
const LAYERS = [
  { y: 0.12, sag: 0.38, color: 'mid' },
  { y: -0.42, sag: 0.38, color: 'deep' },
];
const CENTRE = 0.2;
// Top, the lid, the centre of each water layer, and the bottom: as on the flat mark.
const NODES = [1.02, LID.y, ...LAYERS.map(layer => layer.y - layer.sag), -1.22].map(y => y + CENTRE);

// Wing-shaped plan (a superellipse), filled with dots on a grid.
function layerDots({ y, sag }) {
  const dots = [];
  for (let x = -1; x <= 1.001; x += 0.06) {
    for (let z = -0.8; z <= 0.801; z += 0.06) {
      const r = Math.abs(x) ** 1.6 + Math.abs(z / 0.8) ** 1.6;
      if (r <= 1) dots.push([x * 1.04, y + CENTRE - sag * (1 - r), z]);
    }
  }
  return dots;
}

function layerRim({ y }) {
  return Array.from({ length: 73 }, (_, i) => {
    const t = (i / 72) * Math.PI * 2, c = Math.cos(t), s = Math.sin(t);
    const k = 1 / (Math.abs(c) ** 1.6 + Math.abs(s) ** 1.6) ** (1 / 1.6);
    return [c * k * 1.04, y + CENTRE, s * k * 0.8];
  });
}

const LID_POINTS = [[-LID.halfWidth, 0, 0], [0, 0, -LID.halfDepth], [LID.halfWidth, 0, 0], [0, 0, LID.halfDepth], [-LID.halfWidth, 0, 0]]
  .map(([x, , z]) => [x, LID.y + CENTRE, z]);
const SHAPES = LAYERS.map(layer => ({ ...layer, dots: layerDots(layer), rim: layerRim(layer) }));

function readColors() {
  const style = getComputedStyle(document.documentElement);
  const value = name => style.getPropertyValue(name).trim();
  return { lid: value('--accent'), mid: value('--accent-2'), deep: value('--accent-deep'), node: value('--accent'),
    ring: value('--abyss'), dim: value('--line') };
}

/**
 * depth (0–1) lights the sounding-line nodes from the top: 0 lights one node, 1 lights all five.
 * Drag turns the mark; it turns slowly on its own unless the user prefers reduced motion.
 */
export default function DeepwaterMark3D({ size = 280, depth = 1, label = 'Deepwater mark in 3D' }) {
  const canvasRef = useRef(null);
  const view = useRef({ spin: 0.5, tilt: 0.38, drag: null, hover: false });

  useEffect(() => {
    const canvas = canvasRef.current, context = canvas.getContext('2d');
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const lit = 1 + Math.round(Math.max(0, Math.min(1, depth)) * 4);
    let colors = readColors(), frame = 0, last = performance.now();
    const themeWatch = new MutationObserver(() => { colors = readColors(); });
    themeWatch.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

    function draw(now) {
      const state = view.current, dpr = window.devicePixelRatio || 1;
      if (canvas.width !== size * dpr || canvas.height !== size * dpr) { canvas.width = size * dpr; canvas.height = size * dpr; }
      if (!reduceMotion && !state.drag && !state.hover) state.spin += (now - last) * 0.0004;
      last = now;
      const cs = Math.cos(state.spin), ss = Math.sin(state.spin), ct = Math.cos(state.tilt), st = Math.sin(state.tilt);
      const scale = size * 0.36, c = size / 2;
      // Turn around the vertical axis, tip towards the viewer, then add a little perspective.
      const project = ([x, y, z]) => {
        const x1 = x * cs + z * ss, z1 = -x * ss + z * cs;
        const y2 = y * ct - z1 * st, z2 = y * st + z1 * ct;
        const p = 3.4 / (3.4 - z2);
        return [c + x1 * scale * p, c - y2 * scale * p, z2];
      };
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      context.clearRect(0, 0, size, size);

      const front = [], back = [];
      for (const shape of SHAPES) {
        context.fillStyle = colors[shape.color];
        for (const dot of shape.dots) {
          const [x, y, z] = project(dot);
          (z > 0 ? front : back).push(() => {
            context.fillStyle = colors[shape.color];
            context.globalAlpha = 0.55 + 0.35 * z;
            context.fillRect(x - 1.3, y - 1.3, 2.6, 2.6);
          });
        }
      }

      const strokePath = (points, color, width, alpha) => {
        context.globalAlpha = alpha; context.strokeStyle = color; context.lineWidth = width; context.lineJoin = 'round';
        context.beginPath();
        points.forEach((point, i) => { const [x, y] = project(point); if (i) context.lineTo(x, y); else context.moveTo(x, y); });
        context.stroke();
      };

      // Back half first, then the sounding line, then the front half, so the line sits inside the layers.
      back.forEach(paint => paint());
      for (const shape of SHAPES) strokePath(shape.rim, colors[shape.color], 1.4, 0.9);
      strokePath(LID_POINTS, colors.lid, 2.4, 1);

      const [topX, topY] = project([0, NODES[0], 0]), [bottomX, bottomY] = project([0, NODES[4], 0]);
      context.globalAlpha = 1; context.lineCap = 'round';
      context.strokeStyle = colors.ring; context.lineWidth = 6;
      context.beginPath(); context.moveTo(topX, topY); context.lineTo(bottomX, bottomY); context.stroke();
      context.strokeStyle = colors.node; context.lineWidth = 2.2; context.stroke();

      const pulse = reduceMotion ? 0.5 : (Math.sin(now / 450) + 1) / 2;
      NODES.forEach((nodeY, i) => {
        const [x, y] = project([0, nodeY, 0]), on = i < lit, radius = i === 0 || i === 4 ? 6 : 5.2;
        if (on && i === lit - 1) {
          context.globalAlpha = 0.25 + 0.2 * pulse; context.fillStyle = colors.node;
          context.beginPath(); context.arc(x, y, radius + 4 + pulse * 4, 0, Math.PI * 2); context.fill();
        }
        context.globalAlpha = 1; context.fillStyle = colors.ring;
        context.beginPath(); context.arc(x, y, radius + 1.6, 0, Math.PI * 2); context.fill();
        context.fillStyle = on ? colors.node : colors.dim;
        context.beginPath(); context.arc(x, y, radius, 0, Math.PI * 2); context.fill();
      });

      front.forEach(paint => paint());
      context.globalAlpha = 1;
      frame = requestAnimationFrame(draw);
    }
    frame = requestAnimationFrame(draw);
    return () => { cancelAnimationFrame(frame); themeWatch.disconnect(); };
  }, [size, depth]);

  function pointerDown(event) {
    view.current.drag = { x: event.clientX, y: event.clientY, spin: view.current.spin, tilt: view.current.tilt };
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function pointerMove(event) {
    const drag = view.current.drag;
    if (!drag) return;
    view.current.spin = drag.spin + (event.clientX - drag.x) * 0.01;
    view.current.tilt = Math.max(-0.2, Math.min(1.2, drag.tilt + (event.clientY - drag.y) * 0.006));
  }

  return <canvas ref={canvasRef} className="mark-3d" style={{ width: size, height: size }} role="img" aria-label={label}
    onPointerDown={pointerDown} onPointerMove={pointerMove} onPointerUp={() => { view.current.drag = null; }}
    onPointerEnter={() => { view.current.hover = true; }} onPointerLeave={() => { view.current.hover = false; view.current.drag = null; }} />;
}

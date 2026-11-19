// Übungs-Piktogramme als SVG. Die Zeichnungen selbst kommen aus data.js (aus Pictograms.kt erzeugt);
// hier stehen nur die Zeichen-Hilfsfunktionen, die SVG-Elemente statt Canvas-Aufrufen erzeugen.
// Koordinatensystem 0..1 (viewBox="0 0 1 1"), Seitenansichten blicken nach links.

import { MOTION_OF, DEFAULT_MOTION, drawMotionWith } from './data.js';
import { groupColor } from './core.js';

const cache = new Map();

function motionSvg(m) {
  if (cache.has(m)) return cache.get(m);
  const out = [];
  const r3 = v => Math.round(v * 1000) / 1000;
  const line = (w, ...xy) => {
    const pts = [];
    for (let i = 0; i < xy.length; i += 2) pts.push(`${r3(xy[i])},${r3(xy[i + 1])}`);
    out.push(`<polyline points="${pts.join(' ')}" fill="none" stroke="#fff" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"/>`);
  };
  const circle = (x, y, r) => out.push(`<circle cx="${r3(x)}" cy="${r3(y)}" r="${r}" fill="#fff"/>`);
  const thin = (...xy) => line(0.034, ...xy);
  const api = {
    line,
    limb: (...xy) => line(0.062, ...xy),
    torso: (...xy) => line(0.085, ...xy),
    thin,
    head: (x, y, r = 0.068) => circle(x, y, r),
    plate: (x, y, r = 0.075) => circle(x, y, r),
    ring: (x, y, r) => out.push(`<circle cx="${x}" cy="${y}" r="${r}" fill="none" stroke="#fff" stroke-width="0.034"/>`),
    dumbbell: (x, y, vertical = false) => {
      if (vertical) {
        thin(x, y - 0.06, x, y + 0.06);
        line(0.07, x - 0.03, y - 0.06, x + 0.03, y - 0.06);
        line(0.07, x - 0.03, y + 0.06, x + 0.03, y + 0.06);
      } else {
        thin(x - 0.06, y, x + 0.06, y);
        line(0.07, x - 0.06, y - 0.03, x - 0.06, y + 0.03);
        line(0.07, x + 0.06, y - 0.03, x + 0.06, y + 0.03);
      }
    },
    barbellFront: (x1, x2, y) => {
      thin(x1, y, x2, y);
      line(0.07, x1 + 0.03, y - 0.06, x1 + 0.03, y + 0.06);
      line(0.07, x2 - 0.03, y - 0.06, x2 - 0.03, y + 0.06);
    },
    floor: (y = 0.84) => thin(0.2, y, 0.8, y),
    rrect: (x, y, w, h, r) => out.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="#fff"/>`),
    // Bogen wie Compose drawArc: Ellipse im Rechteck (x, y, w, h), Winkel in Grad ab 3 Uhr im Uhrzeigersinn
    arc: (x, y, w, h, startDeg, sweepDeg, stroke) => {
      const cx = x + w / 2, cy = y + h / 2, rx = w / 2, ry = h / 2;
      const a1 = (startDeg * Math.PI) / 180, a2 = ((startDeg + sweepDeg) * Math.PI) / 180;
      const p = a => `${r3(cx + rx * Math.cos(a))} ${r3(cy + ry * Math.sin(a))}`;
      out.push(`<path d="M ${p(a1)} A ${rx} ${ry} 0 ${sweepDeg > 180 ? 1 : 0} 1 ${p(a2)}" fill="none" stroke="#fff" stroke-width="${stroke}"/>`);
    },
  };
  drawMotionWith(m, api);
  const svg = out.join('');
  cache.set(m, svg);
  return svg;
}

export const motionOf = (name, group) => MOTION_OF[name] ?? DEFAULT_MOTION[group] ?? 'DUMBBELL';

/** Rundes Symbol: Kreis in Kategorie-Farbe mit Piktogramm (als HTML-String). */
export function motionIconHtml(motion, color, size) {
  return `<svg class="motion-icon" viewBox="0 0 1 1" width="${size}" height="${size}" aria-hidden="true">` +
    `<circle cx="0.5" cy="0.5" r="0.5" fill="${color}"/>${motionSvg(motion)}</svg>`;
}
export const exerciseIconHtml = (name, group, size = 44) => motionIconHtml(motionOf(name, group), groupColor(group), size);
export const muscleIconHtml = (group, size = 28) => motionIconHtml(DEFAULT_MOTION[group] ?? 'DUMBBELL', groupColor(group), size);

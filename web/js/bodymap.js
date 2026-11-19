// Körperdiagramm (Meerschweinchen) – entspricht MuscleMap in BodyMap.kt.
// Die Formen kommen aus body-data.js (erzeugt aus BodyMap.kt); hier wird nur ein <svg> daraus gebaut.

import { MUSCLES, BODY, BODY_SHAPES } from './body-data.js';
import { tr, isEnglish } from './core.js';
import { h } from './ui.js';

export const PRIMARY_COLOR = '#E24B4A';
export const SECONDARY_COLOR = '#EF9F27';

export const muscleLabel = id => (MUSCLES[id] ? (isEnglish() ? MUSCLES[id].en : MUSCLES[id].de) : id);

const COLORS = {
  light: { FUR: '#F3E6D6', PATCH: '#9C6B45', PINK: '#F2B8B5', outline: '#7A5A43', neutral: '#CFC7BE' },
  dark: { FUR: '#5C4D41', PATCH: '#3B2F27', PINK: '#B98585', outline: '#8F7A69', neutral: '#7A7068' },
};

/** Alle Pfad-Varianten einer Form: [transform, d] – gespiegelt an x = 50 und/oder in die Rückansicht verschoben. */
function placements(s) {
  const offsets = s.view === 'FRONT' ? [0] : s.view === 'BACK' ? [BODY.backOffset] : [0, BODY.backOffset];
  const out = [];
  for (const dx of offsets) {
    out.push(`translate(${dx} 0)`);
    if (s.mirror) out.push(`translate(${dx} 0) matrix(-1 0 0 1 ${BODY.width} 0)`);
  }
  return out;
}

/** SVG-Text des Diagramms. primary = rot, secondary = orange, Rest grau. */
function svg(primary, secondary) {
  const c = COLORS[document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'];
  const color = s => s.fill === 'MUSCLE'
    ? (primary.includes(s.id) ? PRIMARY_COLOR : secondary.includes(s.id) ? SECONDARY_COLOR : c.neutral)
    : s.fill === 'DARK' ? '#2B211B' : s.fill === 'LIGHT' ? '#FFFFFF' : c[s.fill];
  const parts = [];
  // 1. Außenkontur: alle Körperteile dick umranden; die Füllungen danach überdecken die Linien innen
  for (const s of BODY_SHAPES) if (!s.id) for (const t of placements(s)) {
    parts.push(`<path transform="${t}" d="${s.d}" fill="none" stroke="${c.outline}" stroke-width="2.4" stroke-linejoin="round"/>`);
  }
  // 2. Flächen (Muskeln mit dünner Fuge in Fellfarbe)
  for (const s of BODY_SHAPES) for (const t of placements(s)) {
    const stroke = s.fill === 'MUSCLE' ? ` stroke="${c.FUR}" stroke-width="0.7"` : '';
    parts.push(`<path transform="${t}" d="${s.d}" fill="${color(s)}"${stroke}/>`);
  }
  const w = BODY.backOffset + BODY.width;
  return `<svg viewBox="0 0 ${w} ${BODY.height}" role="img" aria-label="${tr('Körperdiagramm', 'Body diagram')}">${parts.join('')}</svg>`;
}

/** Körperdiagramm mit Beschriftung „Vorne/Hinten“ und Legende „Haupt: … · Hilfs: …“. */
export function muscleMap(primary, secondary = []) {
  return h('div', { class: 'muscle-map' },
    h('div', { class: 'muscle-figure', html: svg(primary, secondary) }),
    h('div', { class: 'muscle-views muted' }, h('span', null, tr('Vorne', 'Front')), h('span', null, tr('Hinten', 'Back'))),
    h('div', { class: 'muscle-legend' },
      primary.length ? h('span', null, h('i', { style: `background:${PRIMARY_COLOR}` }), tr('Haupt: ', 'Primary: ') + primary.map(muscleLabel).join(', ')) : null,
      primary.length && secondary.length ? h('span', { class: 'muted' }, '·') : null,
      secondary.length ? h('span', null, h('i', { style: `background:${SECONDARY_COLOR}` }), tr('Hilfs: ', 'Secondary: ') + secondary.map(muscleLabel).join(', ')) : null));
}

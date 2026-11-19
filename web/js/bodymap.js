// Körperdiagramm (Meerschweinchen) – entspricht MuscleMap in BodyMap.kt.
// Die Formen kommen aus body-data.js (erzeugt aus BodyMap.kt); hier wird nur ein <svg> daraus gebaut.

import { MUSCLES, BODY, BODY_SHAPES } from './body-data.js';
import { tr, isEnglish, CustomMeta } from './core.js';
import { h, chip, dialog } from './ui.js';
import { wikiFor } from './wiki.js';
import { openDialog, closeDialog, rerender } from './nav.js';
import { store } from './store.js';

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
    // Muskeln: dünne Fuge in Fellfarbe; Fell: gleichfarbiger Rand schließt die Naht an der Spiegelachse
    const stroke = s.fill === 'MUSCLE' ? ` stroke="${c.FUR}" stroke-width="0.7"`
      : s.fill === 'FUR' || s.fill === 'PATCH' ? ` stroke="${color(s)}" stroke-width="0.8"` : '';
    parts.push(`<path transform="${t}" d="${s.d}" fill="${color(s)}"${stroke}/>`);
  }
  const w = BODY.backOffset + BODY.width;
  return `<svg viewBox="0 0 ${w} ${BODY.height}" role="img" aria-label="${tr('Körperdiagramm', 'Body diagram')}">${parts.join('')}</svg>`;
}

/** Körperdiagramm mit Beschriftung „Vorne/Hinten“ und Legende „Haupt: … · Hilfs: …“. */
export function muscleMap(primary, secondary = [], { legend = true } = {}) {
  return h('div', { class: 'muscle-map' },
    h('div', { class: 'muscle-figure', html: svg(primary, secondary) }),
    h('div', { class: 'muscle-views muted' }, h('span', null, tr('Vorne', 'Front')), h('span', null, tr('Hinten', 'Back'))),
    legend ? h('div', { class: 'muscle-legend' },
      primary.length ? h('span', null, h('i', { style: `background:${PRIMARY_COLOR}` }), tr('Haupt: ', 'Primary: ') + primary.map(muscleLabel).join(', ')) : null,
      secondary.length ? h('span', null, h('i', { style: `background:${SECONDARY_COLOR}` }), tr('Hilfs: ', 'Secondary: ') + secondary.map(muscleLabel).join(', ')) : null) : null);
}

// ---------- Muskeln einer Übung ----------

/** Muskeln einer Übung: selbst gewählte (eigene Übung) oder aus dem Wiki; null = unbekannt. */
export function musclesOf(name) {
  if (CustomMeta.muscles[name]) return CustomMeta.muscles[name];
  const w = wikiFor(name);
  return w?.primary?.length ? { primary: w.primary, secondary: w.secondary ?? [] } : null;
}

/** Auswahl mit Vorschau. st = { primary: [...], secondary: [...] } wird direkt verändert. */
export function musclePicker(st) {
  const toggle = (list, other, id) => {
    if (st[list].includes(id)) st[list] = st[list].filter(x => x !== id);
    else { st[list] = [...st[list], id]; st[other] = st[other].filter(x => x !== id); }
    rerender();
  };
  const dot = c => `<i class="chip-dot" style="background:${c}"></i>`;
  return [
    muscleMap(st.primary, st.secondary, { legend: false }),
    h('b', { class: 'small-title' }, tr('Hauptmuskeln', 'Primary muscles')),
    h('div', { class: 'chips wrap' }, Object.keys(MUSCLES).map(id =>
      chip(muscleLabel(id), st.primary.includes(id), () => toggle('primary', 'secondary', id), st.primary.includes(id) ? dot(PRIMARY_COLOR) : null))),
    h('b', { class: 'small-title' }, tr('Hilfsmuskeln', 'Secondary muscles')),
    h('div', { class: 'chips wrap' }, Object.keys(MUSCLES).map(id =>
      chip(muscleLabel(id), st.secondary.includes(id), () => toggle('secondary', 'primary', id), st.secondary.includes(id) ? dot(SECONDARY_COLOR) : null))),
  ];
}

/** Dialog „Muskeln bearbeiten“ für eigene Übungen. */
export function muscleEditDialog(name) {
  const cur = CustomMeta.muscles[name];
  const st = { primary: [...(cur?.primary ?? [])], secondary: [...(cur?.secondary ?? [])] };
  openDialog(() => dialog(tr('Muskeln', 'Muscles'), musclePicker(st), [
    { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
    { label: tr('Speichern', 'Save'), onClick: () => { closeDialog(); store.setCustomMuscles(name, st); } },
  ], closeDialog));
}

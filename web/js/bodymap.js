// Körperdiagramm (Meerschweinchen) – entspricht MuscleMap in BodyMap.kt.
// Die Formen kommen aus body-data.js (erzeugt aus BodyMap.kt); hier wird nur ein <svg> daraus gebaut.

import { MUSCLES, BODY, BODY_SHAPES } from './body-data.js';
import { tr, isEnglish, CustomMeta, dayKey, fmtW, plural } from './core.js';
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
function placements(s, view = 'BOTH') {
  let offsets = s.view === 'FRONT' ? [0] : s.view === 'BACK' ? [BODY.backOffset] : [0, BODY.backOffset];
  // Nur eine Seite: deren Pfade, die Rückansicht an x = 0 verschoben
  if (view === 'FRONT') offsets = offsets.filter(dx => dx === 0);
  if (view === 'BACK') offsets = offsets.filter(dx => dx === BODY.backOffset).map(() => 0);
  const out = [];
  for (const dx of offsets) {
    out.push(`translate(${dx} 0)`);
    if (s.mirror) out.push(`translate(${dx} 0) matrix(-1 0 0 1 ${BODY.width} 0)`);
  }
  return out;
}

/** SVG-Text der Figur. colorOf(Muskel-ID) = Farbe oder null (neutral grau); view = BOTH/FRONT/BACK. */
function svg(colorOf, view = 'BOTH') {
  const c = COLORS[document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'];
  const color = s => s.fill === 'MUSCLE'
    ? (colorOf(s.id) ?? c.neutral)
    : s.fill === 'DARK' ? '#2B211B' : s.fill === 'LIGHT' ? '#FFFFFF' : c[s.fill];
  const parts = [];
  // 1. Außenkontur: alle Körperteile dick umranden; die Füllungen danach überdecken die Linien innen
  for (const s of BODY_SHAPES) if (!s.id) for (const t of placements(s, view)) {
    parts.push(`<path transform="${t}" d="${s.d}" fill="none" stroke="${c.outline}" stroke-width="2.4" stroke-linejoin="round"/>`);
  }
  // 2. Flächen (Muskeln mit dünner Fuge in Fellfarbe)
  for (const s of BODY_SHAPES) for (const t of placements(s, view)) {
    // Muskeln: dünne Fuge in Fellfarbe; Fell: gleichfarbiger Rand schließt die Naht an der Spiegelachse
    const stroke = s.fill === 'MUSCLE' ? ` stroke="${c.FUR}" stroke-width="0.7"`
      : s.fill === 'FUR' || s.fill === 'PATCH' ? ` stroke="${color(s)}" stroke-width="0.8"` : '';
    parts.push(`<path transform="${t}" d="${s.d}" fill="${color(s)}"${stroke}/>`);
  }
  const w = view === "BOTH" ? BODY.backOffset + BODY.width : BODY.width;
  return `<svg viewBox="0 0 ${w} ${BODY.height}" role="img" aria-label="${tr('Körperdiagramm', 'Body diagram')}">${parts.join('')}</svg>`;
}

/** Figur (vorne + hinten) mit „Vorne/Hinten“-Beschriftung. */
export const bodyFigure = colorOf => [
  h('div', { class: 'muscle-figure', html: svg(colorOf) }),
  h('div', { class: 'muscle-views muted' }, h('span', null, tr('Vorne', 'Front')), h('span', null, tr('Hinten', 'Back'))),
];

/** Körperdiagramm mit Beschriftung „Vorne/Hinten“ und Legende „Haupt: … · Hilfs: …“. */
export function muscleMap(primary, secondary = [], { legend = true } = {}) {
  return h('div', { class: 'muscle-map' },
    bodyFigure(id => (primary.includes(id) ? PRIMARY_COLOR : secondary.includes(id) ? SECONDARY_COLOR : null)),
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

// ---------- Heatmap: wie stark wurde jeder Muskel in einem Zeitraum trainiert? ----------

/** Farbstufen: [untere Grenze, Farbe, Beschriftung] – wie HEAT_LEVELS in BodyMap.kt. */
export const HEAT_LEVELS = [
  [0.01, '#F2C94C', '1–19 %'],
  [0.20, SECONDARY_COLOR, '20–49 %'],
  [0.50, '#E8703A', '50–79 %'],
  [0.80, PRIMARY_COLOR, '80–100 %'],
];
export const heatColor = p => HEAT_LEVELS.filter(l => p >= l[0]).pop()?.[1] ?? null;

/** Sätze je Muskel: Hauptmuskeln 1, Hilfsmuskeln 0,5 pro Satz (wie muscleScore in BodyMap.kt). */
export function muscleScore(sets) {
  const score = {}, cache = new Map();
  for (const s of sets) {
    if (!cache.has(s.exercise)) cache.set(s.exercise, musclesOf(s.exercise));
    const m = cache.get(s.exercise);
    if (!m) continue;
    for (const id of m.primary) score[id] = (score[id] ?? 0) + 1;
    for (const id of m.secondary) score[id] = (score[id] ?? 0) + 0.5;
  }
  return score;
}

/** Belastung relativ zum am stärksten trainierten Muskel (= 1). */
export function relativeLoad(score) {
  const max = Math.max(0, ...Object.values(score));
  return max ? Object.fromEntries(Object.entries(score).map(([k, v]) => [k, v / max])) : {};
}

/** Zeiträume (wie StatPeriod in BodyMap.kt). */
export const STAT_PERIODS = ['LAST', 'WEEK', 'MONTH', 'ALL'];
export const periodLabel = p => ({
  LAST: tr('Letztes Training', 'Last workout'), WEEK: tr('7 Tage', '7 days'), MONTH: tr('30 Tage', '30 days'), ALL: tr('Alle', 'All'),
})[p];
export function periodFilter(p, sets) {
  const day = 24 * 3600 * 1000, now = Date.now();
  if (p === 'LAST') {
    if (!sets.length) return [];
    const last = dayKey(Math.max(...sets.map(s => s.time)));
    return sets.filter(s => dayKey(s.time) === last);
  }
  if (p === 'WEEK') return sets.filter(s => s.time >= now - 7 * day);
  if (p === 'MONTH') return sets.filter(s => s.time >= now - 30 * day);
  return sets;
}

/** Startseite: Karte „Trainierte Muskeln“ mit Zeitraum-Umschalter, Werte in Prozent. */
export function muscleHeatCard(sets, period, onPeriod) {
  return heatCard({
    title: tr('Trainierte Muskeln', 'Trained muscles'),
    score: muscleScore(periodFilter(period, sets)),
    showSets: false,
    empty: tr('Keine Trainings in diesem Zeitraum.', 'No workouts in this period.'),
    header: h('div', { class: 'chips hscroll-chips' }, STAT_PERIODS.map(p => chip(periodLabel(p), p === period, () => onPeriod(p)))),
  });
}

/** „6,5“ bzw. „6“ – halbe Sätze kommen von Hilfsmuskeln. */
const fmtSets = v => fmtW(Math.round(v * 2) / 2);

/** Karte mit Heatmap. showSets = Summe der Sätze je Muskel (Trainingsübersicht), sonst Prozent. */
export function heatCard({ title, score, showSets, empty, header = null }) {
  const load = relativeLoad(score);
  const entries = Object.entries(score).sort((a, b) => b[1] - a[1]);
  const missing = Object.keys(MUSCLES).filter(id => !(id in load));
  const dot = c => h('i', { style: `background:${c}` });
  const total = Object.values(score).reduce((a, v) => a + v, 0);
  return h('div', { class: 'card pad-m heat-card' },
    h('h3', null, title),
    header,
    h('div', { class: 'muscle-map' }, bodyFigure(id => (load[id] ? heatColor(load[id]) : null))),
    h('div', { class: 'heat-legend' }, HEAT_LEVELS.map(([, c, label]) => h('span', null, dot(c), label))),
    entries.length
      ? [h('div', { class: 'heat-list' }, entries.map(([id, v]) => h('span', null, dot(heatColor(load[id])),
          `${muscleLabel(id)} ` + (showSets ? `${fmtSets(v)} ${v === 1 ? tr('Satz', 'set') : tr('Sätze', 'sets')}` : `${Math.round(load[id] * 100)} %`)))),
        showSets ? h('b', null, tr('Summe: ', 'Total: ') + `${fmtSets(total)} ` + tr('Muskel-Sätze', 'muscle sets') + ' · ' +
          plural(entries.length, tr('Muskel', 'muscle'), tr('Muskeln', 'muscles')) + tr(' (Hilfsmuskel zählt ½)', ' (secondary counts ½)')) : null,
        missing.length ? h('p', { class: 'muted small' }, tr('Nicht trainiert: ', 'Not trained: ') + missing.map(muscleLabel).join(', ')) : null]
      : h('p', { class: 'muted' }, empty));
}

// ---------- Muskel-Karten pro Seite (Reiter „Info“ der Übungsdetails, wie MuscleSideCard) ----------

/** Muskeln, die auf einer Seite der Figur zu sehen sind. */
export const musclesOnSide = view =>
  Object.keys(MUSCLES).filter(id => BODY_SHAPES.some(s => s.id === id && (s.view === view || s.view === 'BOTH')));

/** Karte: Titel oben, links die Figur einer Seite, rechts Haupt- und Hilfsmuskeln dieser Seite. */
export function muscleSideCard(view, primary, secondary) {
  const ids = musclesOnSide(view);
  const main = ids.filter(id => primary.includes(id));
  const help = ids.filter(id => secondary.includes(id) && !primary.includes(id));
  const group = (title, list, color) => list.length ? h('div', { class: 'side-group' },
    h('small', null, h('i', { style: `background:${color}` }), title),
    list.map(id => h('div', null, muscleLabel(id)))) : null;
  return h('div', { class: 'card pad-m glass side-card' },
    h('h3', null, view === 'FRONT' ? tr('Vorderseite', 'Front') : tr('Rückseite', 'Back')),
    h('div', { class: 'side-body' },
      h('div', { class: 'side-figure', html: svg(id => (primary.includes(id) ? PRIMARY_COLOR : secondary.includes(id) ? SECONDARY_COLOR : null), view) }),
      h('div', { class: 'side-list' },
        !main.length && !help.length ? h('p', { class: 'muted' }, tr('Auf dieser Seite arbeiten keine Muskeln mit.', 'No muscles involved on this side.')) : null,
        group(tr('Hauptmuskeln', 'Primary'), main, PRIMARY_COLOR),
        group(tr('Hilfsmuskeln', 'Secondary'), help, SECONDARY_COLOR))));
}

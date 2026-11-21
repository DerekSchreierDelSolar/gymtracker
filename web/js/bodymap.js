// Körperdiagramm (Meerschweinchen) – entspricht MuscleMap in BodyMap.kt.
// Die Formen kommen aus body-data.js (erzeugt aus BodyMap.kt); hier wird nur ein <svg> daraus gebaut.

import { MUSCLES, REGION_NAMES, BODY, BODY_SHAPES } from './body-data.js';
import { tr, isEnglish, CustomMeta, dayKey, fmtW, plural, isWork } from './core.js';
import { h, chip, dialog, horizontalBars } from './ui.js';
import { wikiFor } from './wiki.js';
import { openDialog, closeDialog, rerender } from './nav.js';
import { store } from './store.js';

export const PRIMARY_COLOR = '#E24B4A';
export const SECONDARY_COLOR = '#EF9F27';

export const muscleLabel = id => (MUSCLES[id] ? (isEnglish() ? MUSCLES[id].en : MUSCLES[id].de) : id);

// ---------- Regionen (Teile eines Muskels, „muskel.teil“) – wie REGION_NAMES/MUSCLE_REGIONS in BodyMap.kt ----------
export const regionLabel = k => (REGION_NAMES[k] ? (isEnglish() ? REGION_NAMES[k].en : REGION_NAMES[k].de) : muscleLabel(k));
export const muscleOfRegion = k => k.split('.')[0];
/** Teilflächen einer Form: [[key, Polygon], …] */
const regionPolys = s => s._polys ??= (s.regions ? s.regions.split(';').map(r => [r.split(':')[0].trim(), r.split(':')[1].trim()]) : []);
/** Alle Regionen einer Form (leer = nicht einfärbbar) */
const regionKeys = s => regionPolys(s).length ? regionPolys(s).map(r => r[0]) : s.region ? [s.region] : s.fill === 'MUSCLE' ? [s.id] : [];
/** Regionen je Muskel (ungeteilter Muskel = nur seine ID) */
const MUSCLE_REGIONS = (() => {
  const all = [...new Set(BODY_SHAPES.flatMap(regionKeys))];
  return Object.fromEntries(Object.keys(MUSCLES).map(m => {
    const r = all.filter(k => muscleOfRegion(k) === m);
    return [m, r.length ? r : [m]];
  }));
})();

export const LIGHT_COLOR = '#F2C94C';
/** Stufen der Belastung einer Region durch eine Übung: 1 Hauptarbeit, 0,6 mittel, 0,3 leicht. */
const LEVELS = [1, 0.6, 0.3];
const stageOf = v => LEVELS.reduce((a, b) => (Math.abs(b - v) < Math.abs(a - v) ? b : a));
export const levelColor = v => (!v || v <= 0 ? null : v >= 0.8 ? PRIMARY_COLOR : v >= 0.45 ? SECONDARY_COLOR : LIGHT_COLOR);
export const levelTitle = st => (st === 1 ? tr('Hauptarbeit', 'Main work') : st === 0.6 ? tr('Mittel', 'Moderate') : tr('Leicht', 'Light'));

/**
 * Belastung je Region einer Übung (m = { primary, secondary, regions? }): Hauptmuskel = alle seine Regionen 1,
 * Hilfsmuskel 0,6; nennt `regions` Regionen eines Muskels, gelten für diesen Muskel nur diese (wie MuscleSet.levels).
 */
export function levelsOf(m) {
  const out = {};
  for (const id of m.secondary ?? []) for (const r of MUSCLE_REGIONS[id] ?? []) out[r] = 0.6;
  for (const id of m.primary ?? []) for (const r of MUSCLE_REGIONS[id] ?? []) out[r] = 1;
  const own = new Set(Object.keys(m.regions ?? {}).map(muscleOfRegion));
  for (const r of Object.keys(out)) if (own.has(muscleOfRegion(r))) delete out[r];
  return Object.assign(out, m.regions ?? {});
}

/** Beschriftungen je Stufe: ganzer Muskel, wenn alle seine Regionen in der Stufe sind, sonst die Regionen. */
export function levelLabels(levels) {
  return LEVELS.map(st => [st, Object.keys(MUSCLES).flatMap(m => {
    const regs = MUSCLE_REGIONS[m];
    const inStage = regs.filter(r => levels[r] != null && stageOf(levels[r]) === st);
    return !inStage.length ? [] : inStage.length === regs.length ? [muscleLabel(m)] : inStage.map(regionLabel);
  })]).filter(([, l]) => l.length);
}

const COLORS = {
  light: { FUR: '#F3E6D6', PATCH: '#9C6B45', PINK: '#F2B8B5', outline: '#7A5A43', neutral: '#CFC7BE', TISSUE: '#C8BDB1' },
  // Im dunklen Design bleibt das Fell hell (leicht gedämpft) – wie BodyFigure in BodyMap.kt
  dark: { FUR: '#E2D3C1', PATCH: '#946642', PINK: '#E8AEAB', outline: '#B39579', neutral: '#BDB3A8', TISSUE: '#B0A599' },
};

/**
 * Alle Pfad-Varianten einer Form: [transform, gespiegelt?, dx] – gespiegelt an x = 50 und/oder in die Rückansicht verschoben.
 * dx = Verschiebung der Figur (0 = vorne bzw. einzelne Seite, BODY.backOffset = Rückansicht).
 */
function placements(s, view = 'BOTH') {
  let offsets = s.view === 'FRONT' ? [0] : s.view === 'BACK' ? [BODY.backOffset] : [0, BODY.backOffset];
  // Nur eine Seite: deren Pfade, die Rückansicht an x = 0 verschoben
  if (view === 'FRONT') offsets = offsets.filter(dx => dx === 0);
  if (view === 'BACK') offsets = offsets.filter(dx => dx === BODY.backOffset).map(() => 0);
  const out = [];
  for (const dx of offsets) {
    out.push([`translate(${dx} 0)`, false, dx]);
    if (s.mirror) out.push([`translate(${dx} 0) matrix(-1 0 0 1 ${BODY.width} 0)`, true, dx]);
  }
  return out;
}

// ---------- Plastische Optik (wie BodyFigure in BodyMap.kt: Licht von oben links) ----------

/** Punkte eines Pfads [x, y] (inkl. Kontrollpunkte) – für Begrenzungsrahmen und Lage-Tests. */
const points = d => {
  const n = d.match(/-?\d+(\.\d+)?/g).map(Number);
  return n.filter((_, i) => i % 2 === 0).map((x, i) => [x, n[2 * i + 1]]);
};
const box = s => s._box ??= (() => {
  const p = points(s.d), xs = p.map(q => q[0]), ys = p.map(q => q[1]);
  return { x0: Math.min(...xs), x1: Math.max(...xs), y0: Math.min(...ys), y1: Math.max(...ys) };
})();
/** Region-Polygon (Pfad M/L/Q/Z in 0..1 des Rahmens) → Koordinaten der ungespiegelten Form */
function polyAbs(s, poly) {
  const b = box(s), X = u => (b.x0 + u * (b.x1 - b.x0)).toFixed(2), Y = v => (b.y0 + v * (b.y1 - b.y0)).toFixed(2);
  let out = '', nums = [];
  const flush = () => { for (let i = 0; i < nums.length; i += 2) out += ` ${X(nums[i])} ${Y(nums[i + 1])}`; nums = []; };
  for (const t of poly.match(/[MLQZ]|-?\.?\d+(\.\d+)?/g)) {
    if (/[MLQZ]/.test(t)) { flush(); out += ' ' + t; } else nums.push(Number(t));
  }
  flush();
  return out.trim();
}
/** Liegt die Form ganz im Kopf (Kreis um 50/30)? Flecken dort übernehmen die Schattierung des Kopfes. */
const inHead = s => s._inHead ??= points(s.d).every(([x, y]) => Math.hypot(x - 50, y - 30) <= 24.5);
const isHead = s => s.fill === 'FUR' && !s.mirror && !s.id;
const isMuscle = s => s.fill === 'MUSCLE' || s.fill === 'DECO';
/** Farbe abdunkeln (f < 1) – für die gewölbte Muskelkante */
const darken = (hex, f) => '#' + [1, 3, 5].map(i => Math.round(parseInt(hex.slice(i, i + 2), 16) * f).toString(16).padStart(2, '0')).join('');

/** Name der Licht-Ebene einer Fläche (Muskel = Polster, Rumpf = halber Zylinder, Arm/Bein = Zylinder, Kugel, Pfote) */
function lightOf(s, mirrored) {
  if (isMuscle(s)) return mirrored ? 'mR' : 'mL';
  if (isHead(s)) return null; // Kopf-Kugel kommt zuletzt über Kopf, Flecken und Gesicht
  if (s.fill === 'PATCH') return inHead(s) ? null : 'sph';
  if (s.fill === 'FUR' || s.fill === 'TISSUE') return !s.mirror ? 'sph' : box(s).x1 >= 49 ? 'torso' : 'limb';
  if (s.fill === 'PINK' && !inHead(s)) return 'pk';
  return null;
}

// Fasern: ein Muster je vorkommender Richtung
const FIBER_ANGLES = [...new Set([0, ...BODY_SHAPES.map(s => s.fiber ?? 0)])];

/** Verläufe, Muster und Clip-Pfade einer Figur (id = eindeutiges Präfix, mehrere Figuren pro Seite). */
function defsFor(id, view) {
  const sh = '#4A2E18';
  // Umriss der Figur je Ansicht (alle Körperteile) – Muskeln wachsen nicht darüber hinaus
  const bodyClip = `<clipPath id="${id}body">${BODY_SHAPES.filter(s => !isMuscle(s))
    .flatMap(s => placements(s, view).map(([t]) => `<path transform="${t}" d="${s.d}"/>`)).join('')}</clipPath>`;
  const muscleClips = BODY_SHAPES.map((s, i) => s.detail ? `<clipPath id="${id}c${i}"><path d="${s.d}"/></clipPath>` : '').join('')
    // Teilflächen (Regionen) in Koordinaten der ungespiegelten Form
    + BODY_SHAPES.map((s, i) => regionPolys(s).map(([, poly], k) => `<clipPath id="${id}r${i}_${k}"><path d="${polyAbs(s, poly)}"/></clipPath>`).join('')).join('');
  return `<defs>${bodyClip}${muscleClips}
    ${['L', 'R'].map(side => `
      <radialGradient id="${id}m${side}" cx="${side === 'L' ? '32%' : '68%'}" cy="28%" r="80%">
        <stop offset="0" stop-color="#fff" stop-opacity=".55"/><stop offset=".35" stop-color="#fff" stop-opacity="0"/>
        <stop offset=".7" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".32"/>
      </radialGradient>`).join('')}
    <linearGradient id="${id}torso" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="${sh}" stop-opacity=".5"/><stop offset=".28" stop-color="${sh}" stop-opacity=".12"/>
      <stop offset=".55" stop-color="${sh}" stop-opacity="0"/><stop offset=".85" stop-color="#fff" stop-opacity=".22"/>
      <stop offset="1" stop-color="#fff" stop-opacity=".18"/>
    </linearGradient>
    <linearGradient id="${id}limb" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="${sh}" stop-opacity=".45"/><stop offset=".25" stop-color="${sh}" stop-opacity="0"/>
      <stop offset=".45" stop-color="#fff" stop-opacity=".3"/><stop offset=".65" stop-color="#fff" stop-opacity="0"/>
      <stop offset="1" stop-color="${sh}" stop-opacity=".38"/>
    </linearGradient>
    <radialGradient id="${id}sph" cx="40%" cy="32%" r="72%">
      <stop offset="0" stop-color="#fff" stop-opacity=".45"/><stop offset=".45" stop-color="#fff" stop-opacity="0"/>
      <stop offset=".75" stop-color="${sh}" stop-opacity="0"/><stop offset="1" stop-color="${sh}" stop-opacity=".42"/>
    </radialGradient>
    <radialGradient id="${id}pk" cx="40%" cy="30%" r="75%">
      <stop offset="0" stop-color="#fff" stop-opacity=".5"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/>
      <stop offset="1" stop-color="#8A3A35" stop-opacity=".35"/>
    </radialGradient>
    ${FIBER_ANGLES.map(a => `
      <pattern id="${id}f${a}" patternUnits="userSpaceOnUse" width="1.5" height="4" patternTransform="rotate(${a})">
        <line x1=".4" y1="0" x2=".4" y2="4" stroke="#3A1A0A" stroke-opacity=".16" stroke-width=".38"/>
      </pattern>`).join('')}
    <filter id="${id}sh" x="-20%" y="-10%" width="140%" height="130%">
      <feDropShadow dx="1.5" dy="3" stdDeviation="2.5" flood-color="#3A2415" flood-opacity=".28"/>
    </filter>
  </defs>`;
}

let figureId = 0;
/** SVG-Text der Figur. colorOf(Muskel-ID) = Farbe oder null (neutral grau); view = BOTH/FRONT/BACK. */
function svg(colorOf, view = 'BOTH') {
  const id = 'bf' + figureId++ + '-';
  const c = COLORS[document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'];
  // Muskel/Zier-Muskel: Farbe seiner Region (bei Teilflächen erst neutral, die Teile kommen darüber)
  const color = s => isMuscle(s) ? ((!regionPolys(s).length && regionKeys(s)[0] && colorOf(regionKeys(s)[0])) || c.neutral)
    : s.fill === 'DARK' ? '#2B211B' : s.fill === 'LIGHT' ? '#FFFFFF' : c[s.fill];
  const parts = [];
  // 1. Außenkontur: alle Körperteile dick umranden; die Füllungen danach überdecken die Linien innen
  for (const s of BODY_SHAPES) if (!isMuscle(s)) for (const [t] of placements(s, view)) {
    parts.push(`<path transform="${t}" d="${s.d}" fill="none" stroke="${c.outline}" stroke-width="2.4" stroke-linejoin="round"/>`);
  }
  // 2. Flächen in Reihenfolge (später liegt oben), jeweils mit Licht-Ebene
  BODY_SHAPES.forEach((s, i) => {
    for (const [t, mirrored] of placements(s, view)) {
      const col = color(s);
      if (isMuscle(s)) {
        // Muskel wächst bis an seine Nachbarn heran (Rand etwas dunkler = gewölbte Kante), aber nicht über
        // den Körperumriss hinaus; dazwischen bleibt nur eine feine Fuge
        parts.push(`<g clip-path="url(#${id}body)"><path transform="${t}" d="${s.d}" fill="${col}" stroke="${darken(col, 0.82)}" stroke-width="2.6" stroke-linejoin="round"/></g>`);
        parts.push(`<path transform="${t}" d="${s.d}" fill="${col}"/>`);
        // Teilflächen (z. B. obere Brust) in ihrer Farbe – samt gewachsenem Rand, auf die Teilfläche beschnitten
        regionPolys(s).forEach(([key], k) => {
          const rc = colorOf(key);
          if (rc) parts.push(`<g clip-path="url(#${id}body)"><g transform="${t}" clip-path="url(#${id}r${i}_${k})">`
            + `<path d="${s.d}" fill="${rc}" stroke="${darken(rc, 0.82)}" stroke-width="2.6" stroke-linejoin="round"/></g></g>`);
        });
        parts.push(`<path transform="${t}" d="${s.d}" fill="none" stroke="#4A2A18" stroke-opacity=".5" stroke-width=".45"/>`);
        parts.push(`<path transform="${t}" d="${s.d}" fill="url(#${id}f${s.fiber ?? 0})"/>`);
      } else {
        // gleichfarbiger Rand schließt die Naht zwischen den gespiegelten Hälften
        const seam = s.fill === 'FUR' || s.fill === 'PATCH' || s.fill === 'TISSUE' ? ` stroke="${col}" stroke-width="0.8"` : '';
        parts.push(`<path transform="${t}" d="${s.d}" fill="${col}"${seam}/>`);
        if (s.fill === 'TISSUE') parts.push(`<path transform="${t}" d="${s.d}" fill="url(#${id}f0)"/>`);
      }
      const light = lightOf(s, mirrored);
      if (light) parts.push(`<path transform="${t}" d="${s.d}" fill="url(#${id}${light})"/>`);
      // Anatomische Fugen: dunkle Linie mit heller Kante, auf den Muskel beschnitten
      if (s.fill === 'MUSCLE' && s.detail) {
        const b = box(s);
        const X = u => (b.x0 + u * (b.x1 - b.x0)).toFixed(2), Y = v => (b.y0 + v * (b.y1 - b.y0)).toFixed(2);
        const curves = s.detail.split(',').map(cv => {
          const [u0, v0, uc, vc, u1, v1] = cv.trim().split(' ').map(Number);
          return `M${X(u0)} ${Y(v0)} Q${X(uc)} ${Y(vc)} ${X(u1)} ${Y(v1)}`;
        }).join(' ');
        parts.push(`<g transform="${t}" clip-path="url(#${id}c${i})">`
          + `<path d="${curves}" fill="none" stroke="#fff" stroke-opacity=".45" stroke-width=".55" transform="translate(.45 .35)"/>`
          + `<path d="${curves}" fill="none" stroke="#3A1A0A" stroke-opacity=".55" stroke-width=".6" stroke-linecap="round"/></g>`);
      }
    }
  });
  // 3. Kopf als Kugel – über Kopf, Fellflecken, Augen und Nase
  for (const s of BODY_SHAPES) if (isHead(s)) for (const [t] of placements(s, view))
    parts.push(`<path transform="${t}" d="${s.d}" fill="url(#${id}sph)"/>`);
  const w = (view === 'BOTH' ? BODY.backOffset + BODY.width : BODY.width) + 2 * BODY.pad;
  // Bodenschatten unter den Füßen
  const ground = (view === 'BOTH' ? [BODY.width / 2, BODY.backOffset + BODY.width / 2] : [BODY.width / 2])
    .map(x => `<ellipse cx="${x}" cy="${BODY.height - 1}" rx="26" ry="3.2" fill="#3A2415" opacity=".13"/>`).join('');
  return `<svg viewBox="${-BODY.pad} 0 ${w} ${BODY.height + 4}" role="img" aria-label="${tr('Körperdiagramm', 'Body diagram')}">`
    + `${defsFor(id, view)}${ground}<g filter="url(#${id}sh)" >${parts.join('')}</g></svg>`;
}

/** Figur (vorne + hinten) mit „Vorne/Hinten“-Beschriftung. */
export const bodyFigure = colorOf => [
  h('div', { class: 'muscle-figure', html: svg(colorOf) }),
  h('div', { class: 'muscle-views muted' }, h('span', null, tr('Vorne', 'Front')), h('span', null, tr('Hinten', 'Back'))),
];

/**
 * Körperdiagramm einer Übung mit Beschriftung „Vorne/Hinten“ und Legende „Hauptarbeit: … · Mittel: … · Leicht: …“.
 * levels = Region → Stufe (levelsOf).
 */
export function muscleMap(levels, { legend = true } = {}) {
  return h('div', { class: 'muscle-map' },
    bodyFigure(k => levelColor(levels[k])),
    legend ? h('div', { class: 'muscle-legend' }, levelLabels(levels).map(([st, labels]) =>
      h('span', null, h('i', { style: `background:${levelColor(st)}` }), levelTitle(st) + ': ' + labels.join(', ')))) : null);
}

// ---------- Muskeln einer Übung ----------

/** Muskeln einer Übung: selbst gewählte (eigene Übung) oder aus dem Wiki; null = unbekannt. */
export function musclesOf(name) {
  if (CustomMeta.muscles[name]) return CustomMeta.muscles[name];
  const w = wikiFor(name);
  return w?.primary?.length ? { primary: w.primary, secondary: w.secondary ?? [], regions: w.regions ?? {} } : null;
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
    muscleMap(levelsOf(st), { legend: false }),
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

/**
 * Sätze je Region (ohne Aufwärmsätze): Hauptarbeit 1, mittel 0,5, leicht 0,25 pro Satz (wie regionScore in BodyMap.kt).
 */
export function regionScore(sets) {
  const score = {}, cache = new Map();
  for (const s of sets) {
    if (!isWork(s)) continue; // Aufwärmsätze zählen nicht
    if (!cache.has(s.exercise)) { const m = musclesOf(s.exercise); cache.set(s.exercise, m ? levelsOf(m) : null); }
    const levels = cache.get(s.exercise);
    if (!levels) continue;
    for (const [r, v] of Object.entries(levels)) {
      const st = stageOf(v);
      score[r] = (score[r] ?? 0) + (st === 1 ? 1 : st === 0.6 ? 0.5 : 0.25);
    }
  }
  return score;
}

/** Sätze je Muskel = seine am stärksten belastete Region. */
function muscleScoreOf(regionScore) {
  const out = {};
  for (const [r, v] of Object.entries(regionScore)) { const m = muscleOfRegion(r); out[m] = Math.max(out[m] ?? 0, v); }
  return out;
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
    score: regionScore(periodFilter(period, sets)),
    showSets: false,
    empty: tr('Keine Trainings in diesem Zeitraum.', 'No workouts in this period.'),
    // 4 gleich breite Knöpfe über die volle Breite, kurze Beschriftung (wie MuscleHeatCard in BodyMap.kt)
    header: h('div', { class: 'segments' }, STAT_PERIODS.map(p => chip(p === 'LAST' ? tr('Zuletzt', 'Latest') : periodLabel(p), p === period, () => onPeriod(p)))),
  });
}

/** „6,5“ bzw. „6“ – halbe Sätze kommen von Hilfsmuskeln. */
const fmtSets = v => fmtW(Math.round(v * 2) / 2);

/**
 * Karte mit Heatmap. score = Sätze je Region (regionScore): die Figur färbt Regionen, die Liste fasst je Muskel zusammen.
 * showSets = Summe der Sätze je Muskel (Trainingsübersicht), sonst Prozent.
 */
export function heatCard({ title, score: regions, showSets, empty, header = null }) {
  const regionLoad = relativeLoad(regions);
  const score = muscleScoreOf(regions);
  const load = relativeLoad(score);
  const entries = Object.entries(score).sort((a, b) => b[1] - a[1]);
  const dot = c => h('i', { style: `background:${c}` });
  const total = Object.values(score).reduce((a, v) => a + v, 0);
  return h('div', { class: 'card pad-m heat-card' },
    h('h3', null, title),
    header,
    h('div', { class: 'muscle-map' }, bodyFigure(k => (regionLoad[k] ? heatColor(regionLoad[k]) : null))),
    h('div', { class: 'heat-legend' }, HEAT_LEVELS.map(([, c, label]) => h('span', null, dot(c), label))),
    entries.length
      ? [showSets
          // Trainingsübersicht: Anteil jedes Muskels an der Summe (zusammen 100 %) + Sätze
          ? [h('small', { class: 'muted' }, tr('Anteil am Training · Sätze', 'Share of workout · sets')),
            horizontalBars(entries.map(([id, v]) => [muscleLabel(id), v]), v => `${Math.round((v / total) * 100)} % · ${fmtSets(v)}`)]
          // sonst Prozent relativ zum stärksten Muskel
          : h('div', { class: 'heat-list' }, entries.map(([id]) => h('span', null, dot(heatColor(load[id])),
            `${muscleLabel(id)} ${Math.round(load[id] * 100)} %`))),
        showSets ? h('b', null, tr('Summe: ', 'Total: ') + `${fmtSets(total)} ` + tr('Muskel-Sätze', 'muscle sets') + ' · ' +
          plural(entries.length, tr('Muskel', 'muscle'), tr('Muskeln', 'muscles')) + tr(' (Hilfsmuskel zählt ½)', ' (secondary counts ½)')) : null]
      : h('p', { class: 'muted' }, empty));
}

// ---------- Muskel-Karten pro Seite (Reiter „Info“ der Übungsdetails, wie MuscleSideCard) ----------

/** Muskeln, die auf einer Seite der Figur zu sehen sind. */
export const musclesOnSide = view =>
  Object.keys(MUSCLES).filter(id => BODY_SHAPES.some(s => s.id === id && (s.view === view || s.view === 'BOTH')));

/**
 * Karte: Titel oben, das Meerschweinchen einer Seite mittig und groß. levels = Region → Stufe (levelsOf).
 * showList = unten die Muskeln/Regionen der ganzen Übung nach Stufe (für die letzte Karte) – wie MuscleSideCard.
 */
export function muscleSideCard(view, levels, showList = false) {
  const group = (title, labels, color) => h('div', { class: 'side-group' },
    h('small', null, h('i', { style: `background:${color}` }), title),
    h('div', { class: 'side-names' }, labels.join(', ')));
  return h('div', { class: 'card pad-m glass side-card' },
    h('div', { class: 'side-head' },
      h('h3', null, view === 'FRONT' ? tr('Vorderseite', 'Front') : tr('Rückseite', 'Back'))),
    h('div', { class: 'side-figure', html: svg(k => levelColor(levels[k]), view) }),
    showList ? h('div', { class: 'side-list' },
      levelLabels(levels).map(([st, labels]) => group(levelTitle(st), labels, levelColor(st)))) : null);
}

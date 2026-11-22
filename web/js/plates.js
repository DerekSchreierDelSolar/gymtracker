// Scheibenrechner + Aufwärm-Rampe (Roadmap Schritt 11) – entspricht Plates.kt.

import { store } from './store.js';
import { tr, fmtW, equipmentOf, parseNum } from './core.js';
import { h, dialog, chip } from './ui.js';
import { openDialog, closeDialog, rerender } from './nav.js';

export const PLATES = [25, 20, 15, 10, 5, 2.5, 1.25];
const PLATE_COLORS = { 25: '#d32f2f', 20: '#1976d2', 15: '#fbc02d', 10: '#388e3c', 5: '#eceff1' };

/** Scheibengewicht mit bis zu 2 Nachkommastellen (1,25 statt 1,3). */
const fmtPlate = kg => ((kg * 10) % 1 === 0 ? fmtW(kg) : kg.toLocaleString(fmtW(1.5).includes(',') ? 'de-DE' : 'en-GB', { minimumFractionDigits: 2 }));

/** { perSide, rest } oder null (leichter als die Stange) – wie platesFor. */
export function platesFor(total, bar) {
  if (total < bar) return null;
  let side = (total - bar) / 2;
  const perSide = [];
  for (const p of PLATES) while (side >= p - 1e-6) { perSide.push(p); side -= p; }
  return { perSide, rest: Math.round(side * 2 * 100) / 100 };
}

/** Aufwärm-Rampe [[kg, Wdh.], …] – wie warmupRamp. */
export function warmupRamp(work, bar) {
  if (work <= 0) return [];
  const round = v => Math.floor(v / 2.5 + 0.5) * 2.5;
  const steps = work < 20 ? [[0.5, 10]] : [[0.4, 10], [0.6, 5], [0.8, 2]];
  const out = [];
  if (bar != null && work > bar * 1.5) out.push([bar, 10]);
  for (const [f, reps] of steps) {
    const w = Math.max(round(work * f), bar ?? 0);
    if (w < work && !out.some(o => o[0] === w)) out.push([w, reps]);
  }
  return out;
}

function plateDialog(total) {
  openDialog(() => {
    const bar = store.barWeight;
    const load = platesFor(total, bar);
    const counts = load ? [...load.perSide.reduce((m, p) => m.set(p, (m.get(p) ?? 0) + 1), new Map())] : [];
    return dialog(tr(`${fmtW(total)} kg auflegen`, `Load ${fmtW(total)} kg`), [
      h('b', { class: 'small-title' }, tr('Stange', 'Bar')),
      h('div', { class: 'segments' }, [20, 15, 10, 7.5].map(b =>
        chip(`${fmtW(b)} kg`, bar === b, () => { store.changeBarWeight(b); rerender(); }))),
      !load ? h('p', { class: 'error-text' }, tr('Leichter als die Stange.', 'Lighter than the bar.'))
        : !load.perSide.length && !load.rest ? h('p', null, tr('Nur die leere Stange.', 'Just the empty bar.'))
          : [h('b', { class: 'small-title' }, tr('Pro Seite:', 'Per side:')),
            h('div', { class: 'plate-row' }, h('span', { class: 'plate-bar' }),
              load.perSide.map(p => h('span', { class: 'plate', style: `height:${24 + p * 2.4}px;background:${PLATE_COLORS[p] ?? '#9e9e9e'}` }))),
            h('h3', null, counts.map(([p, n]) => (n > 1 ? `${n} × ${fmtPlate(p)}` : fmtPlate(p))).join(' + ') + ' kg'),
            load.rest > 0 ? h('p', { class: 'muted small' }, tr(`Rest ${fmtPlate(load.rest)} kg lässt sich nicht genau auflegen.`, `${fmtPlate(load.rest)} kg can't be loaded exactly.`)) : null],
    ], [{ label: 'OK', onClick: closeDialog }], closeDialog);
  });
}

function warmupDialog(work, bar, onPick) {
  const ramp = warmupRamp(work, bar);
  openDialog(() => dialog(tr(`Aufwärmen bis ${fmtW(work)} kg`, `Warm up to ${fmtW(work)} kg`), [
    h('p', { class: 'muted small' }, tr('Antippen übernimmt den Satz als Aufwärmsatz.', 'Tap to use a set as warm-up.')),
    ramp.map(([w, r], i) => h('button', { class: 'ramp-row', onclick: () => { closeDialog(); onPick(w, r); } },
      h('b', { class: 'ramp-label' }, `A${i + 1}`), h('span', { class: 'grow' }, `${fmtW(w)} kg × ${r}`),
      bar != null && w === bar ? h('small', { class: 'muted' }, tr('leere Stange', 'empty bar')) : null)),
    h('hr'),
    h('div', { class: 'ramp-row' }, h('b', { class: 'ramp-label work' }, '1.'), h('span', null, tr(`${fmtW(work)} kg – Arbeitssätze`, `${fmtW(work)} kg – work sets`))),
  ], [{ label: tr('Schließen', 'Close'), onClick: closeDialog }], closeDialog));
}

/** Knöpfe „🏋 Scheiben“ (Langhantel) und „🔥 Aufwärmen“ unter der Satz-Eingabe; onWarmup(kg, Wdh.) – wie SetHelpers. */
export function setHelpers(name, weightText, onWarmup) {
  const barbell = equipmentOf(name) === 'LANGHANTEL';
  const weight = () => parseNum(document.getElementById('in-' + name.replace(/\W+/g, '_') + '-w')?.value ?? weightText);
  return h('div', { class: 'set-helpers' },
    barbell ? h('button', { class: 'btn text', onclick: () => plateDialog(weight() ?? store.barWeight) }, tr('🏋 Scheiben', '🏋 Plates')) : null,
    h('button', { class: 'btn text', onclick: () => { const w = weight(); if (w > 0) warmupDialog(w, barbell ? store.barWeight : null, onWarmup); } }, tr('🔥 Aufwärmen', '🔥 Warm-up')));
}

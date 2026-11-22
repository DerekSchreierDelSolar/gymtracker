// Aufwärm-Rampe (Roadmap Schritt 11) – entspricht Warmup.kt.
// Der Scheibenrechner wurde auf Wunsch wieder entfernt; bei Langhantel-Übungen rechnet die Rampe mit einer 20-kg-Stange.

import { tr, fmtW, equipmentOf, parseNum } from './core.js';
import { h, dialog } from './ui.js';
import { openDialog, closeDialog } from './nav.js';

export const BAR_KG = 20;

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

/** Knopf „🔥 Aufwärmen“ unter der Satz-Eingabe; onWarmup(kg, Wdh.) – wie SetHelpers. */
export function setHelpers(name, weightText, onWarmup) {
  const bar = equipmentOf(name) === 'LANGHANTEL' ? BAR_KG : null;
  // aktueller Feldinhalt (ohne Neuzeichnen beim Tippen steht er nur im Eingabefeld)
  const weight = () => parseNum(document.getElementById('in-' + name.replace(/\W+/g, '_') + '-w')?.value ?? weightText);
  return h('div', { class: 'set-helpers' },
    h('button', { class: 'btn text', onclick: () => { const w = weight(); if (w > 0) warmupDialog(w, bar, onWarmup); } }, tr('🔥 Aufwärmen', '🔥 Warm-up')));
}

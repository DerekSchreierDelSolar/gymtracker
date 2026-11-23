// Anstrengung (RIR) + %-Tabelle zum 1RM (Roadmap Schritt 12) – entspricht Effort.kt.

import { fmtKg, roundPlate, tr, fmtW } from './core.js';
import { h, chip } from './ui.js';

/** „RIR 2“ bzw. „RIR 4+“ */
export const rirLabel = rir => 'RIR ' + (rir >= 4 ? '4+' : String(rir));

/** Chips 0 / 1 / 2 / 3 / 4+; erneut antippen = abwählen (null) – wie RirChips. */
export function rirChips(selected, onPick) {
  return h('div', { class: 'segments' }, [0, 1, 2, 3, 4].map(n =>
    chip(n === 4 ? '4+' : String(n), selected === n, () => onPick(selected === n ? null : n))));
}

/** Abfrage direkt nach dem Satz (wie RirPrompt). */
export function rirPrompt(onPick, onSkip) {
  return h('div', { class: 'card rir-prompt' },
    h('div', { class: 'row' }, h('b', { class: 'grow' }, tr('Wie viele Wdh. wären noch gegangen?', 'How many more reps could you have done?')),
      h('button', { class: 'btn text', onclick: onSkip, 'aria-label': tr('Überspringen', 'Skip') }, '✕')),
    rirChips(null, n => { if (n != null) onPick(n); }));
}

const PERCENT_REPS = [[100, '1'], [95, '2'], [90, '3–4'], [85, '5–6'], [80, '7–8'], [75, '9–10'], [70, '11–12'], [65, '13–15'], [60, '16–20']];

/** Karte „Prozente vom 1RM“ (wie OneRmTable). */
export function oneRmTable(orm) {
  if (!(orm > 0)) return null;
  return h('div', { class: 'card pad-m glass orm-table' },
    h('b', null, tr(`Prozente vom 1RM (${fmtKg(orm)})`, `Percent of 1RM (${fmtKg(orm)})`)),
    h('small', { class: 'muted' }, tr('Geschätzt aus deinem besten Satz – Wdh. als Faustregel.', 'Estimated from your best set – reps as a rule of thumb.')),
    PERCENT_REPS.map(([p, reps]) => h('div', { class: 'orm-row' },
      h('span', { class: 'muted orm-p' }, `${p} %`),
      h('b', { class: 'grow' }, `${fmtKg(p === 100 ? orm : roundPlate(orm * p / 100))}`),
      h('span', { class: 'muted' }, tr(`${reps} Wdh.`, `${reps} reps`)))));
}

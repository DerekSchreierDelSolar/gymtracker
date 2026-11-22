// Trainingsblock mit Deload (Roadmap Schritt 14) – entspricht Cycle.kt.
// store.cycle = { start (Montag), weeks } oder null; Woche weeks+1 = Deload.

import { store } from './store.js';
import { tr, startOfWeek } from './core.js';
import { h, chip } from './ui.js';

/** Woche im Block 1..weeks+1 (letzte = Deload); null = kein Block / Start in der Zukunft. */
export function cycleWeek(c = store.cycle, now = Date.now()) {
  if (!c) return null;
  const diff = startOfWeek(now) - c.start;
  if (diff < 0) return null;
  return (Math.round(diff / (7 * 86_400_000)) % (c.weeks + 1)) + 1;
}

export const isDeload = (now = Date.now()) => !!store.cycle && cycleWeek(store.cycle, now) === store.cycle.weeks + 1;
export const deloadTarget = t => (t <= 0 ? 0 : Math.ceil(t / 2));
export const deloadWeight = kg => Math.floor(kg * 0.6 / 2.5 + 0.5) * 2.5;

/** Zeile für die Wochenziel-Karte (null = kein Block). */
export function cycleLine() {
  const c = store.cycle;
  const w = cycleWeek(c);
  if (w == null) return null;
  const len = c.weeks + 1;
  return w === len ? tr('🛌 Deload-Woche – leichter trainieren, der Körper baut auf', '🛌 Deload week – train lighter, your body rebuilds')
    : tr(`📆 Block: Woche ${w} von ${len} · Deload in Woche ${len}`, `📆 Block: week ${w} of ${len} · deload in week ${len}`);
}

/** Abschnitt im Einstellungen-Dialog (wie CycleSettings). */
export function cycleSettings() {
  const c = store.cycle;
  return h('div', { class: 'cycle-settings' },
    h('div', { class: 'row' },
      h('div', { class: 'grow' }, h('b', { class: 'small-title' }, tr('Trainingsblock mit Deload', 'Training block with deload')),
        h('small', { class: 'muted' }, tr('Ein paar Wochen Aufbau, dann eine leichte Woche: halbe Ziel-Sätze, ca. 60 % Gewicht. Beugt Übertraining vor.',
          'A few weeks of building, then a light week: half the target sets, about 60 % weight. Helps prevent overtraining.')))),
    h('div', { class: 'chips' },
      chip(tr('Aus', 'Off'), !c, () => store.changeCycle(null)),
      chip(tr('An', 'On'), !!c, () => { if (!c) store.changeCycle({ start: startOfWeek(), weeks: 4 }); })),
    c ? [h('div', { class: 'stepper' },
      h('button', { class: 'step', disabled: c.weeks <= 2, onclick: () => store.changeCycle({ ...c, weeks: c.weeks - 1 }) }, '−'),
      h('span', { class: 'step-value wide' }, tr(`${c.weeks} Wochen Aufbau + 1 Deload`, `${c.weeks} weeks build + 1 deload`)),
      h('button', { class: 'step', disabled: c.weeks >= 8, onclick: () => store.changeCycle({ ...c, weeks: c.weeks + 1 }) }, '+')),
      h('small', { class: 'accent' }, cycleLine())] : null);
}

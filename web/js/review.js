// Wochen-/Monatsrückblick (Karte auf der Startseite) – entspricht Review.kt.
// Zeigt den letzten ABGESCHLOSSENEN Zeitraum (letzte Woche Mo–So bzw. letzten Kalendermonat) im Vergleich zum Zeitraum davor.

import { tr, exName, fmtW, fmtDate, startOfWeek, addDays } from './core.js';
import { h, chip } from './ui.js';
import { allSessions, sessionStats, recordEvents } from './stats.js';
import { regionScore, muscleScoreOf, relativeLoad, muscleLabel } from './bodymap.js';
import { MUSCLES } from './body-data.js';
import { nav, update } from './nav.js';
import { store } from './store.js';

/** Beginn des Zeitraums `back` Zeiträume vor dem laufenden (1 = letzte Woche bzw. letzter Monat) */
export function reviewStart(period, back, now = Date.now()) {
  if (period === 'WEEK') return addDays(startOfWeek(now), -7 * back);
  const d = new Date(now);
  return new Date(d.getFullYear(), d.getMonth() - back, 1).getTime();
}

function periodSummary(store, start, end) {
  const list = allSessions(store).filter(t => t.start >= start && t.start < end);
  const stats = list.map(t => sessionStats(store, t));
  const kcal = stats.map(s => s.kcal).filter(k => k != null);
  return {
    sessions: list.length,
    sets: stats.reduce((a, s) => a + s.sets, 0),
    volume: stats.reduce((a, s) => a + s.volume, 0),
    // nur gestartete Workouts – freie Trainings haben keine echte Dauer
    duration: list.filter(t => t.id >= 0).reduce((a, t) => a + Math.max(0, t.end - t.start), 0),
    kcal: kcal.length ? kcal.reduce((a, k) => a + k, 0) : null,
  };
}

export function review(store, period, now = Date.now()) {
  const start = reviewStart(period, 1, now), end = reviewStart(period, 0, now);
  const prev = reviewStart(period, 2, now), earlier = reviewStart(period, 4, now);
  const load = relativeLoad(muscleScoreOf(regionScore(store.sets.filter(s => s.time >= start && s.time < end))));
  const usual = muscleScoreOf(regionScore(store.sets.filter(s => s.time >= earlier && s.time < start)));
  return {
    start, end,
    now: periodSummary(store, start, end),
    before: periodSummary(store, prev, start),
    records: recordEvents(store).filter(r => r.set.time >= start && r.set.time < end),
    topMuscles: Object.entries(load).filter(([, v]) => v > 0).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([k]) => k),
    // in den 3 Zeiträumen davor trainiert, jetzt kaum (< 20 % des Spitzenmuskels)
    neglected: Object.keys(MUSCLES).filter(m => (usual[m] ?? 0) > 0 && (load[m] ?? 0) < 0.2),
  };
}

/** Veränderung: [Text, CSS-Klasse] oder null (wie deltaOf in Review.kt) */
function deltaOf(now, before, percent) {
  if (before <= 0 && now <= 0) return null;
  if (percent && before <= 0) return [tr('neu', 'new'), 'accent'];
  const diff = now - before;
  const n = Math.round(percent ? (diff / before) * 100 : diff);
  const text = (n > 0 ? '+' + n : n < 0 ? '−' + -n : '±0') + (percent ? ' %' : '');
  return [text, diff > 0 ? 'delta-up' : diff < 0 ? 'delta-down' : 'muted'];
}

const fmtHoursMinutes = ms => { const m = Math.floor(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`; };

const stat = (label, value, delta) => h('div', { class: 'review-stat' },
  h('b', null, value), h('small', { class: 'muted' }, label), h('small', { class: delta?.[1] ?? '' }, delta?.[0] ?? ' '));

/** Karte „Rückblick“ mit Umschalter; nichts, solange es in beiden Zeiträumen (Woche und Monat) kein Training gibt */
export function reviewCard() {
  const period = nav.reviewPeriod;
  const r = review(store, period);
  const empty = x => x.now.sessions === 0 && x.before.sessions === 0;
  if (empty(r) && empty(review(store, period === 'WEEK' ? 'MONTH' : 'WEEK'))) return null;
  const vs = period === 'WEEK' ? tr('ggü. Vorwoche', 'vs. week before') : tr('ggü. Vormonat', 'vs. month before');
  const recordNames = [...new Set(r.records.map(x => x.exercise))];
  return h('div', { class: 'card pad-m review-card' },
    h('h3', null, tr('Rückblick', 'Review')),
    h('div', { class: 'segments' },
      chip(tr('Letzte Woche', 'Last week'), period === 'WEEK', () => update({ reviewPeriod: 'WEEK' })),
      chip(tr('Letzter Monat', 'Last month'), period === 'MONTH', () => update({ reviewPeriod: 'MONTH' }))),
    h('small', { class: 'muted' }, `${fmtDate(r.start)} – ${fmtDate(r.end - 1)} · ${vs}`),
    r.now.sessions === 0 ? h('p', { class: 'muted' }, tr('Keine Trainings in diesem Zeitraum.', 'No workouts in this period.')) : [
      h('div', { class: 'review-grid' },
        stat(tr('Trainings', 'Workouts'), String(r.now.sessions), deltaOf(r.now.sessions, r.before.sessions, false)),
        stat(tr('Sätze', 'Sets'), String(r.now.sets), deltaOf(r.now.sets, r.before.sets, false)),
        stat(tr('Volumen', 'Volume'), `${fmtW(r.now.volume)} kg`, deltaOf(r.now.volume, r.before.volume, true)),
        stat(tr('Trainingszeit', 'Training time'), r.now.duration > 0 ? fmtHoursMinutes(r.now.duration) : '–',
          r.now.duration > 0 ? deltaOf(r.now.duration, r.before.duration, true) : null),
        stat('🔥 kcal', r.now.kcal != null ? `~${r.now.kcal}` : '–', r.now.kcal != null ? deltaOf(r.now.kcal, r.before.kcal ?? 0, true) : null),
        stat(tr('Rekorde', 'Records'), `🏆 ${r.records.length}`, null)),
      recordNames.length ? h('p', { class: 'review-line' }, h('span', null, '🏆'),
        recordNames.slice(0, 3).map(exName).join(', ') + (recordNames.length > 3 ? ' …' : '')) : null,
      r.topMuscles.length ? h('p', { class: 'review-line' }, h('span', null, '💪'), tr('Am meisten: ', 'Most: ') + r.topMuscles.map(muscleLabel).join(', ')) : null,
      r.neglected.length ? h('p', { class: 'review-line' }, h('span', null, '💤'), tr('Kaum trainiert: ', 'Barely trained: ') + r.neglected.map(muscleLabel).join(', ')) : null,
    ]);
}

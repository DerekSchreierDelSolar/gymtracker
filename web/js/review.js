// Wochen-/Monatsrückblick (Karte auf der Startseite) – entspricht Review.kt.
// Zeigt den letzten ABGESCHLOSSENEN Zeitraum (letzte Woche Mo–So bzw. letzten Kalendermonat) im Vergleich zum Zeitraum davor.

import { fmtVol, tr, exName, fmtW, fmtVolume, fmtKcal, fmtDate, startOfWeek, addDays, isWork, groupLabel } from './core.js';
import { h, chip } from './ui.js';
import { allSessions, sessionStats, recordEvents, weekdayOf, weekdayLong } from './stats.js';
import { regionScore, muscleScoreOf, relativeLoad, muscleLabel } from './bodymap.js';
import { MUSCLES } from './body-data.js';
import { nav, update } from './nav.js';
import { store } from './store.js';
import { yearReviewButton } from './wrapped.js';

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

/** Rückblick auf den Zeitraum `back` Zeiträume vor dem laufenden (1 = letzte Woche bzw. letzter Monat, 2 = vorletzter …) */
export function review(store, period, back = 1, now = Date.now()) {
  const start = reviewStart(period, back, now), end = reviewStart(period, back - 1, now);
  const prev = reviewStart(period, back + 1, now), earlier = reviewStart(period, back + 3, now);
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

/** Wie viele abgeschlossene Monate sich zurückblättern lassen: bis zum Monat des ersten Satzes (1–36), wie reviewMonths in Review.kt */
export function reviewMonths(store, now = Date.now()) {
  if (!store.sets.length) return 1;
  const a = new Date(Math.min(...store.sets.map(s => s.time))), b = new Date(now);
  const diff = (b.getFullYear() - a.getFullYear()) * 12 + b.getMonth() - a.getMonth();
  return Math.min(36, Math.max(1, diff));
}

/** „Oktober“, aus einem anderen Jahr „Dezember 2025“ */
function reviewMonthLabel(start, now = Date.now()) {
  const d = new Date(start);
  const opts = d.getFullYear() === new Date(now).getFullYear() ? { month: 'long' } : { month: 'long', year: 'numeric' };
  return d.toLocaleDateString(tr('de-DE', 'en-GB'), opts);
}

/**
 * Karte „Rückblick“ mit Umschalter Woche/Monat; bei Monat darunter wischbare Monats-Chips (letzter Monat zuerst,
 * zurück bis zum ersten Training). Nichts, solange es in beiden Zeiträumen (Woche und Monat) kein Training gibt.
 */
export function reviewCard() {
  const period = nav.reviewPeriod;
  const months = reviewMonths(store);
  const back = period === 'MONTH' ? Math.min(Math.max(1, nav.reviewMonth), months) : 1;
  const r = review(store, period, back);
  const empty = x => x.now.sessions === 0 && x.before.sessions === 0;
  if (empty(r) && empty(review(store, period === 'WEEK' ? 'MONTH' : 'WEEK'))) return null;
  const vs = period === 'WEEK' ? tr('ggü. Vorwoche', 'vs. week before') : tr('ggü. Vormonat', 'vs. month before');
  const recordNames = [...new Set(r.records.map(x => x.exercise))];
  return h('div', { class: 'card pad-m review-card' },
    h('h3', null, tr('Rückblick', 'Review')),
    h('div', { class: 'segments' },
      chip(tr('Letzte Woche', 'Last week'), period === 'WEEK', () => update({ reviewPeriod: 'WEEK' })),
      chip(tr('Monat', 'Month'), period === 'MONTH', () => update({ reviewPeriod: 'MONTH' }))),
    // Monats-Chips; der gewählte wird nach dem Neuzeichnen per data-autoscroll wieder ins Bild geholt (app.js)
    period === 'MONTH' && months > 1 ? h('div', { class: 'chips scroll-chips' },
      Array.from({ length: months }, (_, i) => i + 1).map(b => {
        const c = chip(reviewMonthLabel(reviewStart('MONTH', b)), b === back, () => update({ reviewMonth: b }));
        if (b === back && b > 1) c.dataset.autoscroll = '';
        return c;
      })) : null,
    h('small', { class: 'muted' }, `${fmtDate(r.start)} – ${fmtDate(r.end - 1)} · ${vs}`),
    r.now.sessions === 0 ? h('p', { class: 'muted' }, tr('Keine Trainingseinheiten in diesem Zeitraum.', 'No workouts in this period.')) : [
      h('div', { class: 'review-grid' },
        stat(tr('Einheiten', 'Workouts'), String(r.now.sessions), deltaOf(r.now.sessions, r.before.sessions, false)),
        stat(tr('Sätze', 'Sets'), String(r.now.sets), deltaOf(r.now.sets, r.before.sets, false)),
        stat(tr('Volumen', 'Volume'), `${fmtVol(r.now.volume)}`, deltaOf(r.now.volume, r.before.volume, true)),
        stat(tr('Trainingszeit', 'Training time'), r.now.duration > 0 ? fmtHoursMinutes(r.now.duration) : '–',
          r.now.duration > 0 ? deltaOf(r.now.duration, r.before.duration, true) : null),
        stat('🔥 kcal', r.now.kcal != null ? `~${fmtKcal(r.now.kcal)}` : '–', r.now.kcal != null ? deltaOf(r.now.kcal, r.before.kcal ?? 0, true) : null),
        stat(tr('Rekorde', 'Records'), `🏆 ${r.records.length}`, null)),
      recordNames.length ? h('p', { class: 'review-line' }, h('span', null, '🏆'),
        recordNames.slice(0, 3).map(exName).join(', ') + (recordNames.length > 3 ? ' …' : '')) : null,
      r.topMuscles.length ? h('p', { class: 'review-line' }, h('span', null, '💪'), tr('Am meisten: ', 'Most: ') + r.topMuscles.map(muscleLabel).join(', ')) : null,
      r.neglected.length ? h('p', { class: 'review-line' }, h('span', null, '💤'), tr('Kaum trainiert: ', 'Barely trained: ') + r.neglected.map(muscleLabel).join(', ')) : null,
      // Über dich (Schritt 10): unabhängig vom Zeitraum
      (me => (me.length ? [h('hr'), h('b', { class: 'small-title' }, tr('Über dich', 'About you')),
        me.map(([icon, text]) => h('p', { class: 'review-line' }, h('span', null, icon), text))] : null))(identityLines()),
      // Jahresrückblick (wrapped.js)
      yearReviewButton(),
    ]);
}

// ---------- „Über dich“ (Roadmap Schritt 10) – wie identityLines in Review.kt ----------

/** Zeilen [Symbol, Text] für „Über dich“; leer, solange es zu wenig Daten gibt. */
export function identityLines(now = Date.now()) {
  const out = [];
  const sessions = allSessions(store);
  if (sessions.length < 3) return out;
  // Wochenziel in den letzten (bis zu) 10 abgeschlossenen Wochen seit dem ersten Training
  const first = startOfWeek(Math.min(...sessions.map(t => t.start)));
  const weeks = [];
  for (let w = startOfWeek(addDays(startOfWeek(now), -7)); w >= first && weeks.length < 10; w = startOfWeek(addDays(w, -7))) weeks.push(w);
  if (weeks.length >= 4) {
    const perWeek = new Map();
    for (const t of sessions) perWeek.set(startOfWeek(t.start), (perWeek.get(startOfWeek(t.start)) ?? 0) + 1);
    const hit = weeks.filter(w => (perWeek.get(w) ?? 0) >= store.weeklyGoal).length;
    if (hit * 10 >= weeks.length * 6) out.push(['✅', tr(`${hit} von ${weeks.length} Wochen Ziel geschafft – das ist Routine`, `${hit} of ${weeks.length} weeks on target – that's a habit`)]);
    else if (hit * 10 >= weeks.length * 3) out.push(['✅', tr(`${hit} von ${weeks.length} Wochen Ziel geschafft – du bleibst dran`, `${hit} of ${weeks.length} weeks on target – you're sticking with it`)]);
  }
  const since = now - 90 * 86_400_000;
  const recent = store.sets.filter(s => isWork(s) && s.time >= since);
  const top = (items, key) => { const m = new Map(); for (const x of items) m.set(key(x), (m.get(key(x)) ?? 0) + 1); return [...m.entries()].sort((a, b) => b[1] - a[1]); };
  // Lieblingsübung: die meisten Sätze
  const fav = top(recent, s => s.exercise)[0];
  if (fav && fav[1] >= 5) out.push(['❤️', tr(`Lieblingsübung: ${exName(fav[0])} · ${fav[1]} Sätze`, `Favourite exercise: ${exName(fav[0])} · ${fav[1]} sets`)]);
  // Typische Trainingszeit + Lieblings-Wochentag
  const starts = sessions.filter(t => t.start >= since).map(t => t.start);
  if (starts.length >= 3) {
    const bucket = t => { const hr = new Date(t).getHours();
      return hr <= 10 ? tr('morgens', 'in the morning') : hr <= 14 ? tr('mittags', 'around midday') : hr <= 17 ? tr('nachmittags', 'in the afternoon')
        : hr <= 20 ? tr('abends', 'in the evening') : tr('spätabends', 'late at night'); };
    const time = top(starts, bucket)[0][0];
    const [day, n] = top(starts, weekdayOf)[0];
    const dayText = n * 10 >= starts.length * 3 ? tr(`, am liebsten ${weekdayLong(day).toLowerCase()}s`, `, mostly on ${weekdayLong(day)}s`) : '';
    out.push(['🕐', tr(`Du trainierst meist ${time}${dayText}`, `You usually train ${time}${dayText}`)]);
  }
  // Stärkste Muskelgruppe (ohne Cardio/Sonstige)
  const g = top(recent.filter(s => !['CARDIO', 'SONSTIGE'].includes(store.groupOf(s.exercise))), s => store.groupOf(s.exercise))[0];
  if (g && g[1] >= 5) out.push(['💪', tr(`Deine Stärke: ${groupLabel(g[0])}`, `Your strength: ${groupLabel(g[0])}`)]);
  return out;
}

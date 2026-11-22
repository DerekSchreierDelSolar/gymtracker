// Der Morgen danach (Roadmap Schritt 5) – entspricht Morning.kt.
// Startseite: Rückblick aufs letzte Training + Neuanfang-Effekt; Vorfreude-Satz für die Schnellstart-Karte.

import { store } from './store.js';
import { tr, plural, exName, fmtW, fmtSeconds, isTimed, isCardio, isTop, dayKey, startOfWeek, startOfToday } from './core.js';
import { allSessions, progressionSuggestion, weekdayOf, weekdayLong } from './stats.js';
import { goalProgress } from './goals.js';
import { achievements } from './achievements.js';
import { h } from './ui.js';

/** Ganze Kalendertage zwischen dem Tag von t und heute (0 = heute). */
export function daysAgo(t, now = Date.now()) {
  const noon = x => { const d = new Date(x); d.setHours(12, 0, 0, 0); return d.getTime(); };
  return Math.round((noon(now) - noon(t)) / 86_400_000);
}

/** „Heute“, „Gestern“, „Vorgestern“, sonst Wochentag („Am Donnerstag“). */
export function dayLabel(t) {
  const n = daysAgo(t);
  return n === 0 ? tr('Heute', 'Today') : n === 1 ? tr('Gestern', 'Yesterday') : n === 2 ? tr('Vorgestern', '2 days ago')
    : tr('Am ', 'On ') + weekdayLong(weekdayOf(t));
}

/** Neuanfang-Effekt: Neujahr (1.–7.1.), Monatsanfang (1.–2.), Montag – nur, solange dort noch nicht trainiert wurde. */
export function freshStart(now = Date.now()) {
  const d = new Date(now);
  const sessions = allSessions(store);
  const noneSince = t => !sessions.some(s => s.start >= t);
  const monthStart = new Date(d.getFullYear(), d.getMonth(), 1).getTime();
  const goal = store.weeklyGoal;
  if (d.getMonth() === 0 && d.getDate() <= 7 && noneSince(monthStart)) {
    return tr(`🎆 Neues Jahr, neuer Anfang – dein erstes Training ${d.getFullYear()} wartet`, `🎆 New year, fresh start – your first workout of ${d.getFullYear()} is waiting`);
  }
  if (d.getDate() <= 2 && noneSince(monthStart)) return tr('📅 Neuer Monat – leg einen starken Start hin', '📅 New month – make it a strong start');
  if (weekdayOf(now) === 1 && noneSince(startOfWeek(now))) return tr(`🌱 Neue Woche – ${plural(goal, 'Training wartet', 'Trainings warten')} auf dich`, `🌱 New week – ${plural(goal, 'workout is', 'workouts are')} waiting for you`);
  return null;
}

/** Vorfreude-Satz für ein Workout (Ziel-Übungen zuerst, Vorschlag aus progressionSuggestion); null = keine Übung mit Verlauf. */
export function anticipation(w, today = true) {
  const [wDe, wEn] = today ? ['heute', 'today'] : ['nächstes Mal', 'next time'];
  const goalEx = new Set(store.goals.filter(g => g.exercise && goalProgress(g).reachedAt == null).map(g => g.exercise));
  const dayStart = startOfToday();
  const order = w.exercises.map((e, i) => [e, i]).sort((a, b) => (goalEx.has(b[0]) - goalEx.has(a[0])) || a[1] - b[1]).map(x => x[0]);
  for (const ex of order) {
    if (isCardio(store.groupOf(ex))) continue;
    const s = progressionSuggestion(store, ex, dayStart);
    if (!s) continue;
    const last = store.setsFor(ex).filter(x => x.time < dayStart && isTop(x));
    const day = dayKey(last[0].time);
    const lastDay = last.filter(x => dayKey(x.time) === day);
    const name = exName(ex);
    if (isTimed(ex)) {
      const best = Math.max(...lastDay.map(x => x.reps));
      return tr(`Letztes Mal ${fmtSeconds(best)} ${name} – schaffst du ${wDe} ${fmtSeconds(best + 10)}?`,
        `Last time ${fmtSeconds(best)} ${name} – can you do ${fmtSeconds(best + 10)} ${wEn}?`);
    }
    const top = Math.max(...lastDay.map(x => x.weight));
    const reps = Math.max(...lastDay.filter(x => x.weight === top).map(x => x.reps));
    if (s.weight != null && s.weight > top) {
      return tr(`Letztes Mal ${fmtW(top)} kg ${name} – schaffst du ${wDe} ${fmtW(s.weight)}?`, `Last time ${fmtW(top)} kg ${name} – can you do ${fmtW(s.weight)} ${wEn}?`);
    }
    if (top <= 0) return tr(`Letztes Mal ${reps} × ${name} – ${wDe} ${reps + 1}?`, `Last time ${reps} × ${name} – ${reps + 1} ${wEn}?`);
    return tr(`Letztes Mal ${fmtW(top)} kg × ${reps} ${name} – ${wDe} ${reps + 1} Wdh.?`, `Last time ${fmtW(top)} kg × ${reps} ${name} – ${reps + 1} reps ${wEn}?`);
  }
  return null;
}

/** Karte ganz oben: Neuanfang + letztes Training (höchstens 3 Tage her) – wie MorningCard. */
export function morningCard() {
  const fresh = freshStart();
  const last = allSessions(store).reduce((a, t) => (!a || t.start > a.start ? t : a), null);
  const recent = last && daysAgo(last.start) <= 3 ? last : null;
  if (!fresh && !recent) return null;
  let body = null;
  if (recent) {
    const wins = achievements(recent);
    const top = wins.find(a => a.kind !== 'SESSION' && a.kind !== 'WEEK_COUNT');
    const session = wins.find(a => a.kind === 'SESSION');
    const title = top ? `${top.emoji} ${top.title}` : `${session.emoji} ${tr('Training geschafft', 'Workout done')}`;
    const detail = top ? [top.detail, session.detail].filter(Boolean).join(' · ') : session.detail;
    body = h('div', null, h('small', { class: 'morning-label' }, `${dayLabel(recent.start)} · ${recent.workoutName}`),
      h('b', { class: 'morning-title' }, title), detail ? h('small', { class: 'morning-detail' }, detail) : null);
  }
  return h('div', { class: 'card morning-card' }, fresh ? h('b', { class: 'morning-title' }, fresh) : null, body);
}

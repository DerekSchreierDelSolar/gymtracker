// Jahresrückblick „Dein Jahr“ (Roadmap Schritt 17) – entspricht Wrapped.kt.

import { store } from './store.js';
import { tr, exName, fmtW, fmtKcal, plural, isTop, isWork, isTimed, isCardio, dayKey, startOfWeek, groupLabel, appLanguage } from './core.js';
import { allSessions, sessionStats, recordEvents, streakFrom } from './stats.js';
import { goalProgress, goalTitle } from './goals.js';
import { badges, earnedBadges } from './badges.js';
import { xpBreakdown, levelOf, levelTitle } from './levels.js';
import { h, dialog, statBox, chip } from './ui.js';
import { openDialog, closeDialog, rerender } from './nav.js';

const yearOf = t => new Date(t).getFullYear();
const yearStart = y => new Date(y, 0, 1).getTime();
const fmtHoursMinutes = ms => { const m = Math.floor(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`; };
const count = (items, key) => { const m = new Map(); for (const x of items) m.set(key(x), (m.get(key(x)) ?? 0) + 1); return [...m.entries()].sort((a, b) => b[1] - a[1]); };

/** Abgeschlossene Jahre mit Trainings, neuestes zuerst – das laufende Jahr gibt es erst, wenn es um ist (wie Wrapped.kt). */
export const reviewYears = (now = Date.now()) =>
  [...new Set(allSessions(store).map(t => yearOf(t.start)))].filter(y => y < yearOf(now)).sort((a, b) => b - a);

/** Standardjahr: das zuletzt abgeschlossene Jahr mit Trainings; null = noch keins (Knopf ausgeblendet). */
export const defaultReviewYear = (now = Date.now()) => reviewYears(now)[0] ?? null;

/** Zahlen und Höhepunkte eines Jahres (wie yearReview). */
export function yearReview(year) {
  const from = yearStart(year), to = yearStart(year + 1);
  const inYear = t => t >= from && t < to;
  const sessions = allSessions(store).filter(t => inYear(t.start));
  const stats = sessions.map(t => sessionStats(store, t));
  const work = store.sets.filter(s => isWork(s) && inYear(s.time));
  const strength = s => !isCardio(store.groupOf(s.exercise)) && !isTimed(s.exercise);

  // Größter Sprung: bestes Gewicht im Jahr ggü. Bestwert davor (bzw. erstem Trainingstag im Jahr)
  let jump = null;
  const byEx = new Map();
  for (const s of store.sets) if (isTop(s) && inYear(s.time) && s.weight > 0 && strength(s)) byEx.set(s.exercise, [...(byEx.get(s.exercise) ?? []), s]);
  for (const [ex, list] of byEx) {
    const prior = store.sets.filter(s => s.exercise === ex && isTop(s) && s.time < from).map(s => s.weight);
    const first = list.reduce((a, s) => (s.time < a.time ? s : a));
    const before = prior.length ? Math.max(...prior) : Math.max(...list.filter(s => dayKey(s.time) === dayKey(first.time)).map(s => s.weight));
    const best = Math.max(...list.map(s => s.weight));
    if (best - before > (jump ? jump[2] - jump[1] : 0)) jump = [ex, before, best];
  }

  const weeks = new Set(sessions.map(t => startOfWeek(t.start)));
  const longest = Math.max(0, ...[...weeks].map(w => streakFrom(weeks, w).weeks));
  const earned = earnedBadges();
  const kcals = stats.map(s => s.kcal).filter(k => k != null);
  return {
    year, sessions: sessions.length,
    durationMs: sessions.filter(t => t.id >= 0).reduce((a, t) => a + t.end - t.start, 0),
    sets: work.length,
    volume: work.filter(strength).reduce((a, s) => a + s.weight * s.reps, 0),
    kcal: kcals.length ? kcals.reduce((a, b) => a + b, 0) : null,
    favorite: count(work, s => s.exercise)[0] ?? null,
    topGroup: count(work.filter(s => !['CARDIO', 'SONSTIGE'].includes(store.groupOf(s.exercise))), s => store.groupOf(s.exercise))[0]?.[0] ?? null,
    bestMonth: count(sessions, t => new Date(t.start).getMonth())[0] ?? null,
    recordJump: jump,
    records: recordEvents(store).filter(r => inYear(r.set.time)).length,
    longestStreak: longest,
    goals: store.goals.filter(g => { const r = goalProgress(g).reachedAt; return r != null && inYear(r); }),
    badges: badges().filter(b => { const t = earned.get(b.id); return t != null && inYear(t); }),
    levelFrom: levelOf(xpBreakdown(from - 1).total).level,
    levelTo: levelOf(xpBreakdown(to - 1).total).level,
  };
}

const line = (emoji, label, value) => h('div', { class: 'wrapped-line' }, h('span', { class: 'wrapped-emoji' }, emoji),
  h('div', null, h('small', { class: 'muted' }, label), h('b', null, value)));

function yearDialog(initial) {
  const st = { year: initial };
  openDialog(() => {
    const r = yearReview(st.year);
    const years = reviewYears();
    const monthName = m => new Date(r.year, m, 1).toLocaleDateString(appLanguage === 'en' ? 'en-GB' : 'de-DE', { month: 'long' });
    const tiles = [[tr('Trainings', 'Workouts'), String(r.sessions)]];
    if (r.durationMs > 0) tiles.push([tr('Trainingszeit', 'Training time'), fmtHoursMinutes(r.durationMs)]);
    tiles.push([tr('Sätze', 'Sets'), fmtKcal(r.sets)]);
    if (r.volume > 0) tiles.push([tr('Bewegt', 'Lifted'), `${fmtW(Math.round(r.volume / 100) / 10)} t`]);
    if (r.kcal != null) tiles.push(['🔥 kcal', `~${fmtKcal(r.kcal)}`]);
    tiles.push([tr('Rekorde', 'Records'), `🏆 ${r.records}`]);
    return dialog(tr(`🎉 Dein Jahr ${r.year}`, `🎉 Your year ${r.year}`), [
      years.length > 1 ? h('div', { class: 'segments' }, years.slice(0, 4).map(y => chip(String(y), y === st.year, () => { st.year = y; rerender(); }))) : null,
      !r.sessions ? h('p', null, tr('In diesem Jahr gibt es noch keine Trainings.', 'No workouts this year yet.')) : [
        h('div', { class: 'stat-grid three' }, tiles.map(([l, v]) => statBox(l, v))),
        h('hr'),
        r.favorite ? line('❤️', tr('Lieblingsübung', 'Favourite exercise'), `${exName(r.favorite[0])} · ` + plural(r.favorite[1], tr('Satz', 'set'), tr('Sätze', 'sets'))) : null,
        r.recordJump ? line('🚀', tr('Größter Sprung', 'Biggest jump'), `${exName(r.recordJump[0])}: ${fmtW(r.recordJump[1])} → ${fmtW(r.recordJump[2])} kg (+${fmtW(r.recordJump[2] - r.recordJump[1])})`) : null,
        r.topGroup ? line('💪', tr('Am meisten trainiert', 'Most trained'), groupLabel(r.topGroup)) : null,
        r.bestMonth ? line('📅', tr('Stärkster Monat', 'Best month'), `${monthName(r.bestMonth[0])} · ` + plural(r.bestMonth[1], 'Training', tr('Trainings', 'workouts'))) : null,
        r.longestStreak >= 2 ? line('🔥', tr('Längste Serie', 'Longest streak'), tr(`${r.longestStreak} Wochen in Folge`, `${r.longestStreak} weeks in a row`)) : null,
        r.levelTo > r.levelFrom ? line('⭐', 'Level', tr(`von ${r.levelFrom} auf ${r.levelTo} · ${levelTitle(r.levelTo)}`, `from ${r.levelFrom} to ${r.levelTo} · ${levelTitle(r.levelTo)}`)) : null,
        r.goals.length ? line('🎯', tr('Erreichte Ziele', 'Goals reached'), r.goals.map(goalTitle).join(', ')) : null,
        r.badges.length ? line('🏅', tr('Neue Abzeichen', 'New badges'), r.badges.map(b => b.emoji).join(' ') + ` (${r.badges.length})`) : null,
        h('hr'),
        h('b', null, tr(`Jeder Satz davon war eine Entscheidung für dich. Auf ein starkes ${r.year + 1}! 💪`, `Every one of those sets was a choice for yourself. Here's to a strong ${r.year + 1}! 💪`)),
      ],
    ], [{ label: 'OK', onClick: closeDialog }], closeDialog);
  });
}

/** Knopf unten in der Rückblick-Karte (wie YearReviewButton). */
export function yearReviewButton() {
  const y = defaultReviewYear();
  return y == null ? null : h('button', { class: 'btn text', onclick: () => yearDialog(y) }, tr(`🎉 Dein Jahr ${y} ansehen`, `🎉 See your year ${y}`));
}

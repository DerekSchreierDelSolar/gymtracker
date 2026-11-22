// Level-System (Roadmap Schritt 6) – entspricht Levels.kt.
// Punkte (XP) rückwirkend aus den vorhandenen Daten; Level n → n+1 braucht 100 + 50·(n−1) XP.

import { store } from './store.js';
import { tr, fmtKcal, isWork, startOfWeek } from './core.js';
import { allSessions, recordEvents } from './stats.js';
import { goalProgress } from './goals.js';
import { h, dialog } from './ui.js';
import { openDialog, closeDialog } from './nav.js';

export const XP = { SESSION: 50, SET: 5, RECORD: 25, WEEK_GOAL: 50, GOAL: 200 };

/** Punkte bis einschließlich `until`, aufgeschlüsselt (wie xpBreakdown). */
export function xpBreakdown(until = Infinity) {
  const sessions = allSessions(store).filter(t => t.start <= until);
  const perWeek = new Map();
  for (const t of sessions) perWeek.set(startOfWeek(t.start), (perWeek.get(startOfWeek(t.start)) ?? 0) + 1);
  const b = {
    sessions: sessions.length,
    sets: store.sets.filter(s => isWork(s) && s.time <= until).length,
    records: recordEvents(store).filter(r => r.set.time <= until).length,
    weeks: [...perWeek.values()].filter(n => n >= store.weeklyGoal).length,
    goals: store.goals.filter(g => { const r = goalProgress(g).reachedAt; return r != null && r <= until; }).length,
  };
  b.total = b.sessions * XP.SESSION + b.sets * XP.SET + b.records * XP.RECORD + b.weeks * XP.WEEK_GOAL + b.goals * XP.GOAL;
  return b;
}

export const xpForNext = level => 100 + 50 * (level - 1);

/** { level, into, needed, fraction } */
export function levelOf(xp) {
  let level = 1, rest = xp;
  while (rest >= xpForNext(level)) { rest -= xpForNext(level); level++; }
  return { level, into: rest, needed: xpForNext(level), fraction: rest / xpForNext(level) };
}

export function levelTitle(level) {
  return level >= 50 ? tr('Gym-Legende', 'Gym legend') : level >= 40 ? tr('Muskel-Meerschwein', 'Muscle guinea pig')
    : level >= 30 ? tr('Eiserner Nager', 'Iron rodent') : level >= 20 ? tr('Kraftpaket', 'Powerhouse')
      : level >= 10 ? tr('Hantel-Held', 'Dumbbell hero') : level >= 5 ? tr('Trainingsschweinchen', 'Gym piggy') : tr('Frischling', 'Rookie');
}

/** Nächstes Level mit neuem Titel (null = schon der höchste). */
export const nextTitleLevel = level => [5, 10, 20, 30, 40, 50].find(n => n > level) ?? null;

/** Level-Aufstieg durch ein Training: neues Level oder null (wie levelUpIn). */
export function levelUpIn(t) {
  const before = levelOf(xpBreakdown(t.start - 1).total).level;
  const after = levelOf(xpBreakdown(t.end).total).level;
  return after > before ? after : null;
}

function explainDialog(xp) {
  const rows = [
    [tr('Training', 'Workout'), XP.SESSION, xp.sessions], [tr('Satz (ohne Aufwärmen)', 'Set (no warm-ups)'), XP.SET, xp.sets],
    [tr('Rekord', 'Record'), XP.RECORD, xp.records], [tr('Woche mit Wochenziel', 'Week with weekly goal'), XP.WEEK_GOAL, xp.weeks],
    [tr('Erreichtes Ziel', 'Goal reached'), XP.GOAL, xp.goals],
  ];
  openDialog(() => dialog(tr('Wofür gibt es Punkte?', 'How do I earn points?'), [
    rows.map(([label, per, n]) => h('div', { class: 'row xp-row' }, h('span', { class: 'grow' }, `${label} · +${per}`), h('span', { class: 'muted' }, `${n}× = ${fmtKcal(per * n)}`))),
    h('hr'),
    h('p', { class: 'muted small' }, tr('Jedes Level braucht 50 Punkte mehr als das vorige. Titel gibt es ab Level 5, 10, 20, 30, 40 und 50.',
      'Each level needs 50 points more than the one before. New titles at levels 5, 10, 20, 30, 40 and 50.')),
  ], [{ label: 'OK', onClick: closeDialog }], closeDialog));
}

/** Schmale Karte auf der Startseite (wie LevelCard). */
export function levelCard() {
  const xp = xpBreakdown();
  if (!xp.total) return null;
  const info = levelOf(xp.total);
  return h('button', { class: 'card level-card', onclick: () => explainDialog(xp) },
    h('div', { class: 'row' }, h('span', { class: 'level-star' }, '⭐'),
      h('div', { class: 'grow' }, h('b', null, tr(`Level ${info.level} · ${levelTitle(info.level)}`, `Level ${info.level} · ${levelTitle(info.level)}`)),
        h('small', { class: 'muted' }, tr(`Noch ${info.needed - info.into} Punkte bis Level ${info.level + 1}`, `${info.needed - info.into} points to level ${info.level + 1}`))),
      h('b', { class: 'goal-short' }, `${fmtKcal(xp.total)} XP`)),
    h('div', { class: 'goal-bar level-bar' }, h('span', { style: `width:${Math.round(info.fraction * 100)}%` })));
}

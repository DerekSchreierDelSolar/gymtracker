// Abzeichen (Roadmap Schritt 7) – entspricht Badges.kt.
// Rückwirkend aus den Daten berechnet; sichtbare = Meilensteine, geheime = Überraschungen.

import { store } from './store.js';
import { tr, fmtDate, isTimed, isCardio, isWork, dayKey, startOfWeek, addDays } from './core.js';
import { allSessions, recordEvents, weekdayOf } from './stats.js';
import { goalProgress } from './goals.js';
import { h, dialog } from './ui.js';
import { openDialog, closeDialog } from './nav.js';

const B = (id, emoji, name, desc, secret = false) => ({ id, emoji, name, desc, secret });
export const badges = () => [
  B('first', '👣', tr('Erster Schritt', 'First step'), tr('Dein erstes Training', 'Your first workout')),
  B('s10', '🔟', tr('Dranbleiber', 'Sticking with it'), tr('10 Trainings', '10 workouts')),
  B('s50', '🏅', tr('Stammgast', 'Regular'), tr('50 Trainings', '50 workouts')),
  B('s100', '💯', tr('Hundert!', 'Hundred!'), tr('100 Trainings', '100 workouts')),
  B('s250', '🏛️', tr('Eisenfest', 'Iron will'), tr('250 Trainings', '250 workouts')),
  B('streak4', '🔥', tr('Vier Wochen am Stück', 'Four weeks straight'), tr('4 Wochen in Folge trainiert', 'Trained 4 weeks in a row')),
  B('streak12', '🌋', tr('Ein Vierteljahr', 'A whole quarter'), tr('12 Wochen in Folge trainiert', 'Trained 12 weeks in a row')),
  B('week10', '✅', tr('Zuverlässig', 'Reliable'), tr('10 Wochen mit erreichtem Wochenziel', '10 weeks with your weekly goal reached')),
  B('rec1', '🥇', tr('Erster Rekord', 'First record'), tr('Deine erste Bestleistung', 'Your first personal record')),
  B('rec25', '🏆', tr('Rekordjäger', 'Record hunter'), tr('25 Rekorde', '25 records')),
  B('day10t', '🏋️', tr('Schwerlast', 'Heavy duty'), tr('10 t an einem Tag bewegt', '10 t lifted in one day')),
  B('total100t', '🏗️', tr('Tonnenweise', 'By the ton'), tr('100 t insgesamt bewegt', '100 t lifted in total')),
  B('goal1', '🎯', tr('Zielstrebig', 'Determined'), tr('Ein langfristiges Ziel erreicht', 'Reached a long-term goal')),
  B('allround', '🧩', tr('Rundum', 'All-rounder'), tr('Brust, Rücken, Beine, Schultern, Bizeps und Trizeps in einer Woche', 'Chest, back, legs, shoulders, biceps and triceps in one week')),
  B('cardio30', '🫁', tr('Langer Atem', 'Staying power'), tr('30 min Cardio am Stück', '30 min of cardio in one go')),
  B('hold2', '🪨', tr('Felsenfest', 'Rock solid'), tr('2 min bei einer Zeitübung (z. B. Unterarmstütz)', '2 min on a timed exercise (e.g. plank)')),
  B('explorer', '🧭', tr('Entdecker', 'Explorer'), tr('25 verschiedene Übungen gemacht', 'Did 25 different exercises')),
  B('early', '🌅', tr('Frühaufsteher', 'Early bird'), tr('Training vor 7 Uhr', 'Workout before 7 am'), true),
  B('night', '🦉', tr('Nachteule', 'Night owl'), tr('Training nach 21 Uhr', 'Workout after 9 pm'), true),
  B('weekend', '⚔️', tr('Wochenend-Krieger', 'Weekend warrior'), tr('Samstag und Sonntag trainiert', 'Trained on Saturday and Sunday'), true),
  B('newyear', '🎆', tr('Guter Vorsatz', 'New Year\'s resolution'), tr('Training am 1. Januar', 'Workout on 1 January'), true),
  B('comeback', '🔄', tr('Comeback', 'Comeback'), tr('Nach über 30 Tagen Pause wieder trainiert', 'Back after more than 30 days off'), true),
  B('double', '✌️', tr('Doppelschicht', 'Double shift'), tr('Zwei Trainings an einem Tag', 'Two workouts in one day'), true),
  B('marathon', '⏳', tr('Marathon', 'Marathon'), tr('Training über 90 Minuten', 'Workout over 90 minutes'), true),
];

/** Erreichte Abzeichen: Map ID → Zeitpunkt (wie earnedBadges). */
export function earnedBadges() {
  const out = new Map();
  const earn = (id, t) => { if (t != null && !out.has(id)) out.set(id, t); };
  const sessions = allSessions(store).sort((a, b) => a.start - b.start);
  const work = store.sets.filter(isWork).sort((a, b) => a.time - b.time);
  const strength = work.filter(s => !isCardio(store.groupOf(s.exercise)) && !isTimed(s.exercise));

  for (const [id, n] of [['first', 1], ['s10', 10], ['s50', 50], ['s100', 100], ['s250', 250]]) earn(id, sessions[n - 1]?.start);

  // Wochen in Folge / Wochenziel
  const byWeek = new Map();
  for (const t of sessions) { const w = startOfWeek(t.start); if (!byWeek.has(w)) byWeek.set(w, []); byWeek.get(w).push(t); }
  const weeks = [...byWeek.keys()].sort((a, b) => a - b);
  let run = 0, prev = null;
  for (const w of weeks) {
    run = prev != null && startOfWeek(addDays(w, -7)) === prev ? run + 1 : 1;
    prev = w;
    if (run === 4) earn('streak4', byWeek.get(w)[0].start);
    if (run === 12) earn('streak12', byWeek.get(w)[0].start);
  }
  earn('week10', weeks.map(w => byWeek.get(w)[store.weeklyGoal - 1]?.start).filter(t => t != null)[9]);

  // Rekorde
  const records = recordEvents(store).map(r => r.set.time).sort((a, b) => a - b);
  earn('rec1', records[0]);
  earn('rec25', records[24]);

  // Volumen: 10 t an einem Tag, 100 t insgesamt
  let total = 0;
  const perDay = new Map();
  for (const s of strength) {
    const v = s.weight * s.reps;
    total += v;
    if (total >= 100_000) earn('total100t', s.time);
    const d = (perDay.get(dayKey(s.time)) ?? 0) + v;
    perDay.set(dayKey(s.time), d);
    if (d >= 10_000) earn('day10t', s.time);
  }

  const reached = store.goals.map(g => goalProgress(g).reachedAt).filter(t => t != null);
  earn('goal1', reached.length ? Math.min(...reached) : null);

  // Alle großen Muskelgruppen in einer Woche
  const main = ['BRUST', 'RUECKEN', 'BEINE', 'SCHULTERN', 'BIZEPS', 'TRIZEPS'];
  const seen = new Map();
  for (const s of work) {
    const g = store.groupOf(s.exercise);
    if (!main.includes(g)) continue;
    const w = startOfWeek(s.time);
    if (!seen.has(w)) seen.set(w, new Set());
    seen.get(w).add(g);
    if (seen.get(w).size === main.length) earn('allround', s.time);
  }

  earn('cardio30', work.find(s => isCardio(store.groupOf(s.exercise)) && s.reps >= 30)?.time);
  earn('hold2', work.find(s => isTimed(s.exercise) && !isCardio(store.groupOf(s.exercise)) && s.reps >= 120)?.time);

  // 25 verschiedene Übungen
  const distinct = new Set();
  for (const s of work) { distinct.add(s.exercise); if (distinct.size === 25) { earn('explorer', s.time); break; } }

  // Geheime
  const hour = t => new Date(t).getHours();
  earn('early', sessions.find(t => hour(t.start) < 7)?.start);
  earn('night', sessions.find(t => hour(t.start) >= 21)?.start);
  const days = new Map();
  for (const t of sessions) { const d = dayKey(t.start); if (!days.has(d)) days.set(d, []); days.get(d).push(t); }
  earn('weekend', sessions.find(t => weekdayOf(t.start) === 7 && days.has(dayKey(addDays(t.start, -1))))?.start);
  earn('newyear', sessions.find(t => { const d = new Date(t.start); return d.getMonth() === 0 && d.getDate() === 1; })?.start);
  for (let i = 1; i < sessions.length; i++) {
    if (sessions[i].start - sessions[i - 1].start > 30 * 86_400_000) { earn('comeback', sessions[i].start); break; }
  }
  const doubles = [...days.values()].filter(l => l.length >= 2).map(l => l.map(t => t.start).sort((a, b) => a - b)[1]);
  earn('double', doubles.length ? Math.min(...doubles) : null);
  earn('marathon', sessions.find(t => t.id >= 0 && t.end - t.start >= 90 * 60_000)?.start);
  return out;
}

/** Abzeichen, die in diesem Training verdient wurden (wie badgesIn). */
export function badgesIn(t, earned = earnedBadges()) {
  return badges().filter(b => { const at = earned.get(b.id); return at != null && at >= t.start && at <= t.end; });
}

function badgesDialog(earned) {
  const all = badges();
  const rank = b => (earned.has(b.id) ? 0 : !b.secret ? 1 : 2);
  // erreichte zuerst (neueste oben), dann offene, geheime zuletzt
  const sorted = all.map((b, i) => [b, i])
    .sort((a, c) => rank(a[0]) - rank(c[0]) || (earned.get(c[0].id) ?? 0) - (earned.get(a[0].id) ?? 0) || a[1] - c[1])
    .map(x => x[0]);
  openDialog(() => dialog(tr(`🏅 Abzeichen · ${earned.size} von ${all.length}`, `🏅 Badges · ${earned.size} of ${all.length}`), [
    h('p', { class: 'muted small' }, tr('❓ = geheim – wird verraten, wenn du es schaffst', '❓ = secret – revealed once you earn it')),
    h('div', { class: 'award-grid', 'data-scroll': 'badges' }, sorted.map(b => {
      const got = earned.get(b.id);
      const hidden = got == null && b.secret;
      return h('div', { class: ['award', got == null && 'locked'] },
        h('span', { class: 'award-emoji' }, hidden ? '❓' : b.emoji),
        h('b', null, hidden ? tr('Geheim', 'Secret') : b.name),
        h('small', { class: 'muted' }, hidden ? tr('Überraschung', 'Surprise') : got != null ? `${b.desc} · ${fmtDate(got)}` : b.desc));
    })),
  ], [{ label: 'OK', onClick: closeDialog }], closeDialog));
}

/** Karte auf der Startseite (wie BadgesCard). */
export function badgesCard() {
  const earned = earnedBadges();
  if (!earned.size) return null;
  const all = badges();
  const latest = all.filter(b => earned.has(b.id)).sort((a, b) => earned.get(b.id) - earned.get(a.id)).slice(0, 5);
  return h('button', { class: 'card level-card badges-card', onclick: () => badgesDialog(earned) },
    h('div', { class: 'row' },
      h('div', { class: 'grow' }, h('b', null, tr('🏅 Abzeichen', '🏅 Badges')),
        h('small', { class: 'muted' }, tr(`${earned.size} von ${all.length} · antippen für alle`, `${earned.size} of ${all.length} · tap for all`))),
      h('span', { class: 'badges-latest' }, latest.map(b => b.emoji).join(' '))));
}

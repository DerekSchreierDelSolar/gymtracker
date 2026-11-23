// Nächste Meilensteine (Roadmap Schritt 4, Zielgradient-Effekt) – entspricht Milestones.kt.
// Kleine, bald erreichbare Schritte auf der Startseite, immer positiv formuliert.

import { store } from './store.js';
import { wUnit, toUnit, isLb, toBig, bigUnit, tr, exName, fmtW, plural, isTimed, isCardio, isTop, isWork, startOfWeek, addDays } from './core.js';
import { allSessions, streakFrom } from './stats.js';
import { goalProgress, fmtAmount } from './goals.js';
import { h } from './ui.js';

const VOLUME_STEPS_T = [1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000];
const nextCountMilestone = n => [5, 10, 25, 50, 75, 100].find(m => m > n) ?? (Math.floor(n / 50) + 1) * 50;
// Schrittweite in der angezeigten Einheit: 5/10/20 kg bzw. 10/25/50 lbs (wie Milestones.kt)
const weightStep = best => (isLb() ? (best < 65 ? 10 : best < 330 ? 25 : 50) : best < 30 ? 5 : best < 150 ? 10 : 20);
const frac = (prev, cur, next) => (next <= prev ? 1 : Math.min(1, Math.max(0, (cur - prev) / (next - prev))));
const round1 = v => Math.round(v * 10) / 10;
const prevWeek = w => startOfWeek(addDays(w, -7));
const strength = n => !isCardio(store.groupOf(n)) && !isTimed(n);

/** Die nächsten erreichbaren Meilensteine, nächster zuerst: [{ emoji, title, detail, fraction, exercise }] */
export function nextMilestones(limit = 3) {
  const out = [];
  const now = Date.now();
  const sessions = allSessions(store);

  // Übungen: Ziel-Übungen + die 4 häufigsten Kraftübungen der letzten 60 Tage
  const goals = store.goals.filter(g => goalProgress(g).reachedAt == null);
  const goalEx = new Map(goals.filter(g => g.exercise).map(g => [g.exercise, g]));
  const counts = new Map();
  for (const s of store.sets) if (isWork(s) && s.time > now - 60 * 86_400_000 && strength(s.exercise)) counts.set(s.exercise, (counts.get(s.exercise) ?? 0) + 1);
  const recent = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4).map(e => e[0]);
  const exMilestones = [];
  for (const ex of [...new Set([...goalEx.keys(), ...recent])]) {
    const g = goalEx.get(ex);
    const byTime = !strength(ex);
    const top = store.sets.filter(s => s.exercise === ex && isTop(s) && s.reps > 0);
    if (!top.length) continue;
    // Gewichte in der angezeigten Einheit, damit „runde“ Werte auch in lbs rund sind
    const best = Math.max(...top.map(s => (byTime ? s.reps : toUnit(s.weight))));
    if (best <= 0) continue;
    const step = !byTime ? weightStep(best) : isCardio(store.groupOf(ex)) ? 5 : 30;
    let next = (Math.floor(best / step) + 1) * step;
    const target = g ? (byTime ? g.target : toUnit(g.target)) : null;
    if (target != null && target > best && target <= next) next = target; // nie über das eigene Ziel hinaus
    const prev = Math.max(0, next - step);
    const unit = !byTime ? v => `${fmtW(v)} ${wUnit()}` : v => fmtAmount(ex, v);
    exMilestones.push({
      emoji: g ? '🎯' : '🏋️', exercise: ex, fraction: frac(prev, best, next),
      title: tr(`Noch ${unit(next - best)} bis ${unit(next)} `, `${unit(next - best)} to ${unit(next)} `) + exName(ex),
      detail: tr(`Bisher bestes: ${unit(best)}`, `Best so far: ${unit(best)}`) + (target != null && next === target ? tr(' · dein Ziel!', ' · your goal!') : ''),
    });
  }
  out.push(...exMilestones.sort((a, b) => b.fraction - a.fraction).slice(0, 2));

  // Anzahl Trainings
  const count = sessions.length;
  if (count > 0) {
    const next = nextCountMilestone(count);
    const prev = count >= 100 ? Math.floor(count / 50) * 50 : [1, 5, 10, 25, 50, 75].filter(m => m <= count).at(-1) ?? 0;
    out.push({
      emoji: '🎉', fraction: frac(prev, count, next),
      title: tr(`Noch ${plural(next - count, 'Training', 'Trainings')} bis zum ${next}. Training`, `${plural(next - count, 'workout', 'workouts')} to workout #${next}`),
      detail: tr(`${count} Trainings bisher`, `${count} workouts so far`),
    });
  }

  // Gesamtvolumen (Tonnen)
  const volume = toBig(store.sets.filter(s => isWork(s) && strength(s.exercise)).reduce((a, s) => a + s.weight * s.reps, 0)); // t bzw. 1.000 lbs
  if (volume > 0) {
    const next = VOLUME_STEPS_T.find(v => v > volume) ?? (Math.floor(volume / 10000) + 1) * 10000;
    const prev = VOLUME_STEPS_T.filter(v => v <= volume).at(-1) ?? 0;
    out.push({
      emoji: '🏗️', fraction: frac(prev, volume, next),
      title: tr(`Noch ${fmtW(round1(next - volume))} ${bigUnit()} bis ${next} ${bigUnit()} bewegt`, `${fmtW(round1(next - volume))} ${bigUnit()} to ${next} ${bigUnit()} lifted`),
      detail: tr(`Gesamtvolumen seit Beginn: ${fmtW(round1(volume))} ${bigUnit()}`, `Total volume so far: ${fmtW(round1(volume))} ${bigUnit()}`),
    });
  }

  // Wochenziel: nur wenn genau noch eins fehlt
  const week = startOfWeek();
  const thisWeek = sessions.filter(t => t.start >= week).length;
  if (store.weeklyGoal - thisWeek === 1) {
    out.push({ emoji: '✅', title: tr('Noch 1 Training bis zum Wochenziel', '1 workout to your weekly goal'), detail: null, fraction: thisWeek / store.weeklyGoal });
  }

  // Serie: diese Woche noch nicht trainiert, aber letzte Woche → mit einem Training wächst die Serie
  if (thisWeek === 0) {
    const n = streakFrom(new Set(sessions.map(t => startOfWeek(t.start))), prevWeek(week)).weeks; // mit Joker
    if (n >= 1) out.push({ emoji: '🔥', title: tr(`Ein Training diese Woche → ${n + 1} Wochen in Folge`, `One workout this week → ${n + 1} weeks in a row`), detail: null, fraction: 0.9 });
  }

  // Cardio-Wochenziel
  for (const g of goals.filter(g => g.type === 'CARDIO_WEEK')) {
    const min = store.sets.filter(s => isWork(s) && s.time >= week && isCardio(store.groupOf(s.exercise))).reduce((a, s) => a + s.reps, 0);
    if (min < g.target) {
      out.push({
        emoji: '🫀', fraction: min / g.target,
        title: tr(`Noch ${fmtW(g.target - min)} min Cardio diese Woche`, `${fmtW(g.target - min)} min of cardio left this week`),
        detail: tr(`Dein Ziel: ${fmtW(g.target)} min pro Woche`, `Your goal: ${fmtW(g.target)} min per week`),
      });
    }
  }

  return out.map((m, i) => [m, i]).sort((a, b) => b[0].fraction - a[0].fraction || a[1] - b[1]).map(x => x[0]).slice(0, limit);
}

/** „🏁 Als Nächstes“ als Zeilen unten in der Ziele-Karte (wie MilestoneRows). */
export function milestoneRows(list) {
  return list.map(m => h('div', { class: 'goal-row' },
    h('div', { class: 'goal-head' }, h('span', { class: 'goal-emoji' }, m.emoji), h('b', { class: 'grow' }, m.title)),
    h('div', { class: 'goal-bar' }, h('span', { style: `width:${Math.round(m.fraction * 100)}%` })),
    m.detail ? h('small', { class: 'muted goal-detail' }, m.detail) : null));
}

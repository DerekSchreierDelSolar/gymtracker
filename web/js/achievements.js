// Erfolge eines Trainings (Roadmap Schritt 2) – entspricht Achievements.kt.
// Gemeinsame Grundlage für Feier im Summary, Morgen-Zeile, Abzeichen und Rückblick.
// Liefert IMMER mindestens einen Erfolg; nur Vergleiche mit der eigenen Vergangenheit, nie Kritik.

import { store } from './store.js';
import { tr, exName, fmtW, fmtDate, fmtVolume, plural, isTimed, isCardio, isTop, isWork, startOfWeek, addDays, groupLabel } from './core.js';
import { allSessions, sessionStats, streakFrom } from './stats.js';
import { goalProgress, goalTitle, fmtAmount } from './goals.js';
import { levelUpIn, levelTitle, nextTitleLevel } from './levels.js';
import { badgesIn } from './badges.js';

/** Art → [Emoji, Feiern?] – Reihenfolge = Wichtigkeit (wie AchievementKind) */
export const ACHIEVEMENT_KINDS = {
  GOAL_REACHED: ['🏆', true],
  LEVEL_UP: ['⭐', true],
  BADGE: ['🏅', true],
  RECORD: ['🥇', true],
  REP_RECORD: ['🔁', false],
  GOAL_PROGRESS: ['🎯', false],
  WEEK_GOAL: ['✅', true],
  MILESTONE: ['🎉', true],
  VOLUME_UP: ['📈', false],
  EXERCISE_UP: ['💪', false],
  MUSCLE_BACK: ['👋', false],
  STREAK: ['🔥', false],
  WEEK_COUNT: ['📅', false],
  SESSION: ['👍', false],
};
const ORDER = Object.keys(ACHIEVEMENT_KINDS);

const item = (kind, title, detail = null, exercise = null) =>
  ({ kind, title, detail, exercise, emoji: ACHIEVEMENT_KINDS[kind][0], big: ACHIEVEMENT_KINDS[kind][1] });

const isMilestone = n => [1, 5, 10, 25, 50, 75, 100].includes(n) || (n > 100 && n % 50 === 0);
const ordinal = n => tr(`${n}.`, n % 100 >= 11 && n % 100 <= 13 ? `${n}th` : `${n}${['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'}`);
const daysBetween = (a, b) => Math.floor((b - a) / 86_400_000);
const maxOf = (list, f) => (list.length ? Math.max(...list.map(f)) : null);
const fmtMetric = (ex, v) => (isCardio(store.groupOf(ex)) ? `${fmtW(v)} min` : isTimed(ex) ? `${fmtW(v)} s` : `${fmtW(v)} kg`);

/**
 * Alle Erfolge eines Trainings, wichtigste zuerst: [{ kind, emoji, big, title, detail, exercise }].
 * Vergleicht nur mit Daten VOR dem Training – funktioniert auch für ältere Trainings.
 */
export function achievements(t, stats = sessionStats(store, t)) {
  const out = [];
  const results = stats.results.filter(r => r.sets.length);
  const all = allSessions(store).sort((a, b) => a.start - b.start);
  const goals = store.goals.filter(g => g.created <= t.end);

  // 1. Ziele, die in diesem Training erreicht wurden
  for (const g of goals) {
    const reached = goalProgress(g).reachedAt;
    if (reached != null && reached >= t.start && reached <= t.end) {
      out.push(item('GOAL_REACHED', tr('Ziel erreicht: ', 'Goal reached: ') + goalTitle(g),
        tr(`Seit ${fmtDate(g.created)} drangeblieben – stark!`, `Kept at it since ${fmtDate(g.created)} – awesome!`), g.exercise ?? null));
    }
  }

  // Level-Aufstieg (levels.js)
  const lv = levelUpIn(t);
  if (lv) {
    out.push(item('LEVEL_UP', tr(`Level ${lv} erreicht!`, `Level ${lv} reached!`),
      levelTitle(lv) !== levelTitle(lv - 1) ? tr(`Neuer Titel: ${levelTitle(lv)}`, `New title: ${levelTitle(lv)}`)
        : (n => (n ? tr(`Noch ${plural(n - lv, 'Level', 'Level')} bis „${levelTitle(n)}“`, `${plural(n - lv, 'level', 'levels')} to "${levelTitle(n)}"`) : null))(nextTitleLevel(lv))));
  }

  // Neue Abzeichen (badges.js)
  for (const b of badgesIn(t)) out.push(item('BADGE', tr(`Neues Abzeichen: ${b.name} ${b.emoji}`, `New badge: ${b.name} ${b.emoji}`), b.desc));

  // 2./3./8. Je Übung: Rekord > Wiederholungsrekord > besser als letztes Mal (höchstens einer pro Übung)
  for (const r of results) {
    const ex = r.name;
    const byTime = isCardio(r.group) || isTimed(ex);
    const metric = byTime ? s => s.reps : s => s.weight;
    const now = r.sets.filter(isTop);
    if (!now.length) continue;
    const before = store.sets.filter(s => s.exercise === ex && s.time < t.start && isTop(s));
    const best = maxOf(now, metric);
    const prevBest = maxOf(before, metric);
    if (r.record && prevBest != null) {
      out.push(item('RECORD', tr('Neuer Rekord: ', 'New record: ') + exName(ex), `${fmtMetric(ex, best)} (+${fmtMetric(ex, best - prevBest)})`, ex));
      continue;
    }
    // Wiederholungsrekord: bei diesem (oder höherem) Gewicht nie so viele Wiederholungen geschafft
    if (!byTime) {
      const rep = [...now].sort((a, b) => b.weight - a.weight).find(s => {
        const prior = before.filter(p => p.weight >= s.weight);
        return prior.length && maxOf(prior, p => p.reps) < s.reps;
      });
      if (rep) {
        out.push(item('REP_RECORD', tr('Wiederholungsrekord: ', 'Rep record: ') + exName(ex),
          (rep.weight > 0 ? `${fmtW(rep.weight)} kg × ${rep.reps}` : tr(`${rep.reps} Wdh.`, `${rep.reps} reps`)) + tr(' – so viele wie nie zuvor', ' – more than ever before'), ex));
        continue;
      }
    }
    // Besser als letztes Mal (letzter Trainingstag davor)
    const prev = r.previous;
    if (!prev.length) continue;
    const prevTop = maxOf(prev, metric);
    let detail = null;
    if (best > prevTop) detail = `+${fmtMetric(ex, best - prevTop)}`;
    else if (!byTime && best === prevTop) {
      const repsNow = maxOf(now.filter(s => s.weight === best), s => s.reps);
      const repsPrev = maxOf(prev.filter(s => s.weight === best), s => s.reps) ?? 0;
      const volNow = r.sets.filter(isWork).reduce((a, s) => a + s.weight * s.reps, 0);
      const volPrev = prev.reduce((a, s) => a + s.weight * s.reps, 0);
      if (repsNow > repsPrev) detail = tr(`+${repsNow - repsPrev} Wdh. bei ${fmtW(best)} kg`, `+${repsNow - repsPrev} reps at ${fmtW(best)} kg`);
      else if (volPrev > 0 && volNow > volPrev) {
        const p = Math.round((volNow - volPrev) / volPrev * 100);
        detail = tr(`+${p} % Volumen`, `+${p} % volume`);
      }
    }
    if (detail) out.push(item('EXERCISE_UP', exName(ex), detail + tr(' ggü. letztem Mal', ' vs. last time'), ex));
  }

  // 4. Näher am Ziel (Übungsziele besser geworden, Cardio-Wochenziel in diesem Training vollgemacht)
  for (const g of goals) {
    const p = goalProgress(g);
    if (p.reachedAt != null && p.reachedAt <= t.end) continue; // schon erreicht (bzw. gerade – siehe oben)
    if (g.type === 'STRENGTH' || g.type === 'ENDURANCE') {
      const ex = g.exercise;
      const metric = g.type === 'STRENGTH' ? s => s.weight : s => s.reps;
      const now = (results.find(r => r.name === ex)?.sets ?? []).filter(s => isTop(s) && s.reps > 0);
      if (!now.length) continue;
      const before = maxOf(store.sets.filter(s => s.exercise === ex && s.time < t.start && isTop(s) && s.reps > 0), metric) ?? 0;
      const best = maxOf(now, metric);
      if (best > before) {
        const fmt = g.type === 'STRENGTH' ? v => `${fmtW(v)} kg` : v => fmtAmount(ex, v);
        out.push(item('GOAL_PROGRESS', tr('Näher dran: ', 'Getting closer: ') + goalTitle(g),
          tr(`+${fmt(best - before)} · noch ${fmt(g.target - best)}`, `+${fmt(best - before)} · ${fmt(g.target - best)} to go`), ex));
      }
    } else if (g.type === 'CARDIO_WEEK') {
      const ws = startOfWeek(t.start);
      const minutes = until => store.sets.filter(s => isWork(s) && s.time >= ws && s.time < until && isCardio(store.groupOf(s.exercise)))
        .reduce((a, s) => a + s.reps, 0);
      if (minutes(t.start) < g.target && minutes(t.end + 1) >= g.target) {
        out.push(item('GOAL_PROGRESS', tr('Cardio-Ziel dieser Woche geschafft', 'Cardio goal done this week'),
          tr(`${fmtW(g.target)} min Ausdauer – dein Herz sagt danke`, `${fmtW(g.target)} min of cardio – your heart says thanks`)));
      }
    }
  }

  // 5./11. Training Nr. x in dieser Woche, Wochenziel
  const weekStart = startOfWeek(t.start);
  const inWeek = all.filter(s => startOfWeek(s.start) === weekStart);
  const pos = inWeek.findIndex(s => s.id === t.id) + 1;
  const goal = store.weeklyGoal;
  if (pos === goal) out.push(item('WEEK_GOAL', tr('Wochenziel geschafft!', 'Weekly goal reached!'), tr(`${goal} von ${goal} Trainings diese Woche`, `${goal} of ${goal} workouts this week`)));
  else if (pos > goal) out.push(item('WEEK_COUNT', tr(`${ordinal(pos)} Training diese Woche`, `${ordinal(pos)} workout this week`), tr('Wochenziel übertroffen', 'Weekly goal beaten')));
  else if (pos > 0) out.push(item('WEEK_COUNT', tr(`${ordinal(pos)} Training diese Woche`, `${ordinal(pos)} workout this week`), tr(`Noch ${goal - pos} bis zum Wochenziel`, `${goal - pos} more to your weekly goal`)));

  // 6. Meilenstein: das wievielte Training insgesamt
  const number = all.findIndex(s => s.id === t.id) + 1;
  if (number > 0 && isMilestone(number)) {
    out.push(item('MILESTONE', number === 1 ? tr('Dein erstes Training!', 'Your first workout!') : tr(`${number}. Training!`, `Workout #${number}!`),
      number === 1 ? tr('Der schwerste Schritt ist geschafft.', 'The hardest step is done.') : tr(`${number} Trainings – das ist Routine.`, `${number} workouts – that's a habit.`)));
  }

  // 7. Mehr Volumen als beim letzten Mal dieses Workouts
  if (t.workoutId >= 0 && stats.volume > 0) {
    const last = store.sessions.filter(s => s.workoutId === t.workoutId && s.start < t.start).sort((a, b) => b.start - a.start)[0];
    const prevVol = last ? sessionStats(store, last).volume : 0;
    if (prevVol > 0 && stats.volume > prevVol) {
      const p = Math.round((stats.volume - prevVol) / prevVol * 100);
      out.push(item('VOLUME_UP', tr(`+${p} % Volumen`, `+${p} % volume`),
        tr(`${fmtVolume(stats.volume)} kg statt ${fmtVolume(prevVol)} kg beim letzten Mal`, `${fmtVolume(stats.volume)} kg vs. ${fmtVolume(prevVol)} kg last time`)));
    }
  }

  // 9. Muskelgruppe nach mindestens 14 Tagen Pause wieder trainiert
  const skip = ['CARDIO', 'SONSTIGE', 'GANZKOERPER'];
  for (const g of [...new Set(results.map(r => r.group))].filter(g => !skip.includes(g))) {
    const last = maxOf(store.sets.filter(s => s.time < t.start && isWork(s) && store.groupOf(s.exercise) === g), s => s.time);
    if (last != null && daysBetween(last, t.start) >= 14) {
      out.push(item('MUSCLE_BACK', tr(`${groupLabel(g)} wieder dabei`, `${groupLabel(g)} is back`),
        tr(`Nach ${daysBetween(last, t.start)} Tagen Pause`, `After a ${daysBetween(last, t.start)}-day break`)));
    }
  }

  // 10. Serie: erstes Training der Woche verlängert die Serie (Wochen in Folge mit Training)
  if (pos === 1) {
    const s = streakFrom(new Set(all.filter(x => x.start <= t.end).map(x => startOfWeek(x.start))), weekStart); // mit Joker
    const n = s.weeks;
    if (n >= 2) {
      out.push(item('STREAK', tr(`${n} Wochen in Folge`, `${n} weeks in a row`), s.jokers > 0
        ? tr('Deine Serie läuft weiter – der Joker hat sie gerettet 🃏', 'Your streak continues – saved by a joker 🃏')
        : tr('Deine Serie läuft weiter', 'Your streak continues')));
    }
  }

  // 12. Immer: das Training selbst
  const parts = [];
  if (stats.sets > 0) parts.push(plural(stats.sets, tr('Satz', 'set'), tr('Sätze', 'sets')));
  if (stats.volume > 0) parts.push(tr(`${fmtVolume(stats.volume)} kg bewegt`, `${fmtVolume(stats.volume)} kg moved`));
  if (stats.cardioMinutes > 0) parts.push(`${stats.cardioMinutes} min Cardio`);
  if (t.id >= 0 && t.end - t.start >= 60000) parts.push(`${Math.round((t.end - t.start) / 60000)} min`);
  out.push(item('SESSION', tr('Training geschafft', 'Workout done'), parts.join(' · ') || null));

  // stabil sortieren: innerhalb einer Art bleibt die Reihenfolge der Übungen
  return out.map((a, i) => [a, i]).sort((x, y) => ORDER.indexOf(x[0].kind) - ORDER.indexOf(y[0].kind) || x[1] - y[1]).map(x => x[0]);
}

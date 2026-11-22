// Auswertungen: entspricht Stats.kt, Progression.kt und den Berechnungen aus DashboardTab.kt.

import { STRENGTH_MET, MINUTES_PER_SET } from './data.js';
import {
  isCardio, isTimed, profileComplete, dayKey, goalCompletion, cardioMet, tr, fmtW, fmtSeconds, equipmentOf, startOfWeek, addDays, isWork, isTop,
} from './core.js';

const sum = (arr, f = x => x) => arr.reduce((a, x) => a + f(x), 0);
const max = (arr, f = x => x) => (arr.length ? Math.max(...arr.map(f)) : null);

/** Ergebnis einer Übung innerhalb eines Trainings. */
export function exerciseResult(store, name, start, end, target, freeOnly = false) {
  const group = store.groupOf(name);
  // Bestleistung: Cardio/Zeitübung = längste Dauer, Kraft = schwerstes Gewicht
  const metric = isCardio(group) || isTimed(name) ? s => s.reps : s => s.weight;
  let sets = store.setsBetween(name, start, end);
  // Freies Training: nur Sätze, die zu keinem gespeicherten Training gehören (sonst doppelt gezählt)
  if (freeOnly) sets = sets.filter(s => store.isFreeSet(s));
  // Aufwärm- und Drop-Sätze zählen nicht für Rekorde
  const best = max(sets.filter(isTop), metric);
  const prevBest = max(store.sets.filter(s => s.exercise === name && s.time < start && isTop(s)), metric);
  const record = best != null && prevBest != null && best > prevBest;
  // Sätze vom letzten Trainingstag vor diesem Training
  const before = store.setsFor(name).filter(s => s.time < start && isTop(s));
  const lastDay = before.length ? dayKey(before[0].time) : null;
  const previous = before.filter(s => dayKey(s.time) === lastDay);
  return { name, group, sets, record, target, previous, counted: Math.min(sets.filter(isWork).length, target) };
}

/** Geschätzter Verbrauch (MET-Methode); null ohne Körpergewicht. */
/** Grundumsatz kcal/Tag nach Mifflin-St-Jeor; null bei unvollständigem Profil (wie bmrKcalPerDay) */
export function bmrKcalPerDay(p, kg, time) {
  if (!profileComplete(p)) return null;
  const age = Math.max(10, new Date(time).getFullYear() - p.birthYear);
  return 10 * kg + 6.25 * p.height - 5 * age + (p.sex === 'm' ? 5 : -161);
}

/** kcal je MET-Stunde: Standard 1 kcal × kg, mit Profil Grundumsatz/24 (korrigierte MET-Methode, wie kcalPerMetHour) */
export function kcalPerMetHour(store, kg, time) {
  const bmr = bmrKcalPerDay(store.profile, kg, time);
  return bmr != null ? bmr / 24 : kg;
}

/** Verbrauch aus dem Puls (Keytel et al. 2005), wie heartRateKcal in Stats.kt; null ohne Puls/Gewicht/Geschlecht/Geburtsjahr */
export function heartRateKcal(store, t) {
  const hr = t.hr, p = store.profile, kg = store.weightAt(t.start);
  if (!hr || kg == null || !p.sex || !p.birthYear) return null;
  const age = Math.max(10, new Date(t.start).getFullYear() - p.birthYear);
  const perMin = p.sex === 'm'
    ? (-55.0969 + 0.6309 * hr.avg + 0.1988 * kg + 0.2017 * age) / 4.184
    : (-20.4022 + 0.4472 * hr.avg - 0.1263 * kg + 0.074 * age) / 4.184;
  const kcal = Math.round(perMin * (t.end - t.start) / 60000);
  return kcal > 0 ? kcal : null;
}

export function estimateKcal(store, t, results) {
  const w = store.weightAt(t.start);
  if (w == null) return null;
  const kg = kcalPerMetHour(store, w, t.start);
  const cardio = results.filter(r => isCardio(r.group));
  const cardioMinutes = sum(cardio, r => sum(r.sets.filter(isWork), s => s.reps));
  const cardioKcal = (sum(cardio, r => sum(r.sets.filter(isWork), s => s.reps) * cardioMet(r.name)) * kg) / 60;
  const strengthSets = sum(results.filter(r => !isCardio(r.group)), r => r.sets.filter(isWork).length);
  let strengthMinutes = 0;
  if (strengthSets > 0) {
    strengthMinutes = t.id >= 0
      ? Math.max((t.end - t.start) / 60000 - cardioMinutes, strengthSets * 1.0)
      : strengthSets * MINUTES_PER_SET;
  }
  return Math.round(cardioKcal + (strengthMinutes * STRENGTH_MET * kg) / 60);
}

export function sessionStats(store, t) {
  const results = t.exercises.map(e =>
    exerciseResult(store, e, t.start, t.end, t.targets[e] ?? 0, t.id < 0));
  const strength = results.filter(r => !isCardio(r.group) && !isTimed(r.name)).flatMap(r => r.sets.filter(isWork));
  const cardio = results.filter(r => isCardio(r.group)).flatMap(r => r.sets.filter(isWork));
  const sets = sum(results, r => r.sets.filter(isWork).length);
  const done = sum(results, r => r.counted);
  const total = sum(results, r => r.target);
  const completion = goalCompletion(done, total, sets);
  return {
    results, sets, done, total, completion, percent: Math.round(completion * 100),
    volume: sum(strength, s => s.weight * s.reps),
    cardioMinutes: sum(cardio, s => s.reps),
    records: results.filter(r => r.record).length,
    kcal: heartRateKcal(store, t) ?? estimateKcal(store, t, results),
    kcalFromHr: heartRateKcal(store, t) != null,
  };
}

/** Alle Trainings inkl. freier Trainings (eines pro Tag mit Sätzen ohne Workout). */
export function allSessions(store) {
  const byDay = store.freeSetsByDay();
  return [...store.sessions, ...[...byDay.keys()].map(d => store.freeSession(d, byDay)).filter(Boolean)];
}

// ---------- Dashboard ----------

/** Rekorde chronologisch: Trainingstag, dessen Bestwert alles davor übertrifft (erster Tag zählt nicht). */
export function recordEvents(store) {
  const events = [];
  const byEx = new Map();
  for (const s of store.sets.filter(isTop)) { // ohne Aufwärm-/Drop-Sätze
    if (!byEx.has(s.exercise)) byEx.set(s.exercise, []);
    byEx.get(s.exercise).push(s);
  }
  for (const [name, list] of byEx) {
    const metric = isCardio(store.groupOf(name)) || isTimed(name) ? s => s.reps : s => s.weight;
    const days = new Map();
    for (const s of list.slice().sort((a, b) => a.time - b.time)) {
      const k = dayKey(s.time);
      if (!days.has(k)) days.set(k, []);
      days.get(k).push(s);
    }
    let best = null;
    for (const day of days.values()) {
      const top = day.reduce((a, b) => (metric(b) > metric(a) ? b : a));
      if (best != null && metric(top) > best) events.push({ exercise: name, set: top });
      if (best == null || metric(top) > best) best = metric(top);
    }
  }
  return events.sort((a, b) => b.set.time - a.set.time);
}

/**
 * Nächstes Workout nach dem zuletzt trainierten: das folgende in der Liste mit mindestens einer Übung
 * (nach dem letzten wieder das erste). Noch nie trainiert oder Workout gelöscht → das erste.
 */
export function nextWorkoutAfter(workouts, lastId) {
  const i = workouts.findIndex(w => w.id === lastId);
  if (i < 0) return workouts[0] ?? null;
  for (let k = 1; k <= workouts.length; k++) {
    const w = workouts[(i + k) % workouts.length];
    if (w.exercises.length) return w;
  }
  return workouts[i];
}

// ---------- Trainingsplan (wie Plan.kt) ----------

/** Wochentag 1 = Montag … 7 = Sonntag */
export const weekdayOf = (t = Date.now()) => ((new Date(t).getDay() + 6) % 7) + 1;
export const weekdayShort = d => tr(['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'][d - 1], ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][d - 1]);
const weekdayLong = d => tr(['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'][d - 1],
  ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][d - 1]);
export const daysLabel = days => [...days].sort().map(weekdayShort).join(' · ');

/** Heute geplantes Workout, das heute noch nicht trainiert wurde */
export function dueToday(store) {
  const today = weekdayOf(), key = dayKey(Date.now());
  const done = new Set(store.sessions.filter(t => dayKey(t.start) === key).map(t => t.workoutId));
  return store.workouts.find(w => (w.days ?? []).includes(today) && w.exercises.length && !done.has(w.id)) ?? null;
}

/** Vorschlag laut Plan: { workout, title } („Heute dran“, „Morgen dran“, „Als Nächstes · Mittwoch“) oder null */
export function planPick(store) {
  const planned = store.workouts.filter(w => (w.days ?? []).length && w.exercises.length);
  if (!planned.length) return null;
  const due = dueToday(store);
  if (due) return { workout: due, title: tr('Heute dran', 'Today') };
  const today = weekdayOf();
  for (let k = 1; k <= 7; k++) {
    const d = ((today - 1 + k) % 7) + 1;
    const w = planned.find(p => p.days.includes(d));
    if (w) return { workout: w, title: k === 1 ? tr('Morgen dran', 'Tomorrow') : tr('Als Nächstes · ', 'Next · ') + weekdayLong(d) };
  }
  return null;
}

/** Wochen in Folge mit mind. einem Training (die laufende Woche zählt, wenn schon trainiert). */
export function weekStreak(store) {
  const weeks = new Set(allSessions(store).map(t => startOfWeek(t.start)));
  let w = startOfWeek();
  if (!weeks.has(w)) w = addDays(w, -7);
  let n = 0;
  while (weeks.has(w)) { n++; w = addDays(w, -7); }
  return n;
}

/** Volumen pro Woche der letzten `count` Wochen, älteste zuerst: [[wochenstart, kg], …]. */
export function weeklyVolumes(store, count) {
  const starts = [];
  let w = startOfWeek();
  for (let i = 0; i < count; i++) { starts.unshift(w); w = addDays(w, -7); }
  const strength = store.sets.filter(s => isWork(s) && !isCardio(store.groupOf(s.exercise)) && !isTimed(s.exercise));
  return starts.map(ws => {
    const we = addDays(ws, 7);
    return [ws, sum(strength.filter(s => s.time >= ws && s.time < we), s => s.weight * s.reps)];
  });
}

/** Geschätzte kcal pro Woche (letzte count Wochen, älteste zuerst); null ohne Körpergewicht – wie weeklyKcal */
export function weeklyKcal(store, count) {
  if (!store.weights.length) return null;
  const starts = [];
  let w = startOfWeek();
  for (let i = 0; i < count; i++) { starts.unshift(w); w = addDays(w, -7); }
  const sessions = allSessions(store);
  return starts.map(ws => {
    const we = addDays(ws, 7);
    return [ws, sum(sessions.filter(t => t.start >= ws && t.start < we), t => sessionStats(store, t).kcal ?? 0)];
  });
}

// ---------- Progressions-Vorschlag (doppelte Progression) ----------

export function progressionSuggestion(store, name, before) {
  if (isCardio(store.groupOf(name))) return null;
  const prev = store.setsFor(name).filter(s => s.time < before && isTop(s)); // ohne Aufwärm-/Drop-Sätze
  if (!prev.length) return null;
  const lastDay = dayKey(prev[0].time);
  const last = prev.filter(s => dayKey(s.time) === lastDay);

  if (isTimed(name)) {
    const best = max(last, s => s.reps);
    return {
      title: tr(`Ziel: ${fmtSeconds(best + 10)}`, `Goal: ${fmtSeconds(best + 10)}`),
      reason: tr(`Letztes Mal max. ${fmtSeconds(best)} – 10 s länger versuchen`, `Last time max ${fmtSeconds(best)} – try 10 s longer`),
      weight: null,
    };
  }
  const top = max(last, s => s.weight);
  const atTop = last.filter(s => s.weight === top);
  const minReps = Math.min(...atTop.map(s => s.reps));
  const maxReps = Math.max(...atTop.map(s => s.reps));
  if (top <= 0) {
    return {
      title: tr(`Ziel: ${maxReps + 1} Wdh.`, `Goal: ${maxReps + 1} reps`),
      reason: tr(`Letztes Mal max. ${maxReps} Wdh.`, `Last time max ${maxReps} reps`),
      weight: null,
    };
  }
  const eq = equipmentOf(name);
  const step = eq === 'KURZHANTEL' || eq === 'KETTLEBELL' ? 2 : 2.5;
  if (atTop.length >= 2 && minReps >= 8) {
    return {
      title: tr(`Heute ${fmtW(top + step)} kg?`, `Try ${fmtW(top + step)} kg today?`),
      reason: tr(
        `Letztes Mal ${atTop.length} Sätze mit ${fmtW(top)} kg und mind. ${minReps} Wdh. – Zeit für mehr Gewicht`,
        `Last time ${atTop.length} sets at ${fmtW(top)} kg with at least ${minReps} reps – time to go heavier`),
      weight: top + step,
    };
  }
  return {
    title: tr(`${fmtW(top)} kg, Ziel: ${maxReps + 1}+ Wdh.`, `${fmtW(top)} kg, goal: ${maxReps + 1}+ reps`),
    reason: tr('Erst bei 2 Sätzen mit 8+ Wdh. lohnt sich mehr Gewicht', 'Go heavier once you hit 8+ reps on 2 sets'),
    weight: top,
  };
}

// ---------- Pulszonen (wie Stats.kt) ----------

/** Geschätzte maximale Herzfrequenz (220 − Alter); null ohne Geburtsjahr im Profil. */
export function maxHeartRate(store, time) {
  const y = store.profile.birthYear;
  return y ? Math.max(120, 220 - (new Date(time).getFullYear() - y)) : null;
}

/** Namen der 5 Pulszonen (< 60, 60–70, 70–80, 80–90, > 90 % der max. Herzfrequenz). */
export const heartZoneNames = () => [tr('1 Aufwärmen', '1 Warm-up'), tr('2 Locker', '2 Easy'), tr('3 Aerob', '3 Aerobic'),
  tr('4 Schwelle', '4 Threshold'), tr('5 Maximal', '5 Maximum')];

/** Minuten je Pulszone (jeder Wert der Kurve = eine Minute, 0 = keine Messung). */
export function heartZoneMinutes(hr, maxHr) {
  const minutes = [0, 0, 0, 0, 0];
  for (const bpm of hr.curve) if (bpm > 0) minutes[Math.min(4, Math.max(0, Math.floor((Math.floor(bpm * 100 / maxHr) - 50) / 10)))]++;
  return minutes;
}

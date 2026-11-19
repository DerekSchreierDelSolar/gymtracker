// Gesamter Zustand + Laden/Speichern + alle Änderungen. Entspricht GymStore.kt.
// Gespeichert wird im selben JSON-Format wie die Android-App (Export/Import funktioniert in beide Richtungen),
// und zwar im localStorage des Browsers.

import { RENAMED_EXERCISES, TEMPLATES } from './data.js';
import {
  CustomMeta, catalogGroup, isCardio, setAppLanguage, appLanguage, tr, dayKey,
} from './core.js';

const KEY = 'gym_data';
const migrateName = n => RENAMED_EXERCISES[n] ?? n;

class GymStore {
  exercises = [];
  sets = [];            // { id, exercise, weight, reps, time }
  groups = {};          // Übungsname → Muskelgruppe
  workouts = [];        // { id, name, exercises: [], targets: {} }
  sessions = [];        // { id, workoutId, workoutName, exercises, start, end, targets }
  weights = [];         // { time, kg }, älteste zuerst
  restSeconds = 90;
  lastExport = 0;
  weeklyGoal = 3;
  themeMode = 'system';
  #listeners = new Set();

  constructor() {
    const text = localStorage.getItem(KEY);
    if (!text) return;
    try {
      this.#readFrom(JSON.parse(text));
      // Alte Übungsnamen wurden beim Lesen umbenannt → gleich in neuer Form speichern
      if (Object.keys(RENAMED_EXERCISES).some(k => text.includes(JSON.stringify(k)))) this.#save();
    } catch (e) {
      console.error(e);
      // Beschädigt: Kopie aufheben, bevor der nächste Speichervorgang sie überschreibt
      localStorage.setItem(`gym_data_corrupt_${Date.now()}`, text);
    }
  }

  /** Wird nach jeder Änderung aufgerufen (die App zeichnet dann neu). */
  subscribe(fn) { this.#listeners.add(fn); }
  #changed() { this.#listeners.forEach(fn => fn()); }

  #save() {
    localStorage.setItem(KEY, JSON.stringify(this.#toJson()));
    this.#changed();
  }
  saveNow() { this.#save(); }

  // ----- Laden / Speichern -----

  #readFrom(root) {
    const arr = x => (Array.isArray(x) ? x : []);
    const obj = x => (x && typeof x === 'object' && !Array.isArray(x) ? x : {});
    const intMap = o => Object.fromEntries(Object.entries(obj(o)).map(([k, v]) => [migrateName(k), Number(v) || 0]));

    for (const e of arr(root.exercises)) {
      const n = migrateName(String(e));
      if (!this.exercises.includes(n)) this.exercises.push(n);
    }
    for (const [k, g] of Object.entries(obj(root.groups))) this.groups[migrateName(k)] = g;
    for (const [k, e] of Object.entries(obj(root.customEquipment))) CustomMeta.equipment[migrateName(k)] = e;
    for (const t of arr(root.customTimed)) CustomMeta.timed.add(migrateName(t));
    for (const [k, m] of Object.entries(obj(root.customMuscles))) {
      CustomMeta.muscles[migrateName(k)] = { primary: arr(m?.primary).map(String), secondary: arr(m?.secondary).map(String) };
    }

    this.workouts = arr(root.workouts).map(o => ({
      id: o.id, name: o.name,
      exercises: arr(o.exercises).map(migrateName),
      targets: intMap(o.targets),
    }));
    const s = obj(root.settings);
    this.weeklyGoal = s.weeklyGoal ?? 3;
    this.restSeconds = s.restSeconds ?? 90;
    this.lastExport = s.lastExport ?? 0;
    this.themeMode = s.theme ?? 'system';
    setAppLanguage(s.language ?? 'de');

    this.weights = arr(root.weights).map(o => ({ time: o.time, kg: o.kg })).sort((a, b) => a.time - b.time);
    this.sessions = arr(root.sessions).map(o => ({
      id: o.id, workoutId: o.workoutId, workoutName: o.workoutName,
      exercises: arr(o.exercises).map(migrateName),
      start: o.start, end: o.end, targets: intMap(o.targets),
    }));
    this.sets = arr(root.sets).map(o => ({
      id: o.id, exercise: migrateName(o.exercise), weight: Number(o.weight), reps: Number(o.reps), time: o.time,
    }));
  }

  #toJson() {
    return {
      app: 'GymTracker',
      version: 1,
      exercises: this.exercises,
      groups: this.groups,
      customEquipment: CustomMeta.equipment,
      customTimed: [...CustomMeta.timed],
      customMuscles: CustomMeta.muscles,
      workouts: this.workouts,
      sessions: this.sessions,
      weights: this.weights,
      settings: {
        restSeconds: this.restSeconds,
        lastExport: this.lastExport,
        theme: this.themeMode,
        language: appLanguage,
        weeklyGoal: this.weeklyGoal,
      },
      sets: this.sets,
    };
  }

  // ----- Export / Import -----

  exportJson() { return JSON.stringify(this.#toJson(), null, 2); }

  /** Was in einer Sicherung steckt – null, wenn es keine gültige GymTracker-Sicherung ist. */
  inspectBackup(text) {
    try {
      const root = JSON.parse(text);
      if (!Array.isArray(root.exercises) || !Array.isArray(root.sets)) return null;
      return {
        exercises: root.exercises.length, sets: root.sets.length,
        workouts: root.workouts?.length ?? 0, sessions: root.sessions?.length ?? 0,
      };
    } catch { return null; }
  }

  /** Ersetzt alle Daten. Die bisherigen werden vorher unter „gym_data_before_import“ abgelegt. */
  importJson(text) {
    const root = JSON.parse(text); // zuerst parsen: bei Fehler bleibt alles unverändert
    const old = localStorage.getItem(KEY);
    if (old) localStorage.setItem('gym_data_before_import', old);
    this.exercises = []; this.sets = []; this.groups = {}; this.workouts = []; this.sessions = []; this.weights = [];
    CustomMeta.equipment = {}; CustomMeta.timed = new Set(); CustomMeta.muscles = {};
    this.#readFrom(root);
    this.#save();
  }

  markExported() { this.lastExport = Date.now(); this.#save(); }

  // ----- Einstellungen -----

  setRest(sec) { this.restSeconds = sec; this.#save(); }
  setTheme(mode) { this.themeMode = mode; this.#save(); }
  setLanguage(lang) { setAppLanguage(lang); this.#save(); }
  /** Muskeln einer eigenen Übung festlegen (leere Auswahl = entfernen). */
  setCustomMuscles(name, m) {
    if (m.primary.length || m.secondary.length) CustomMeta.muscles[name] = m; else delete CustomMeta.muscles[name];
    this.#save();
  }

  changeWeeklyGoal(n) { this.weeklyGoal = Math.min(14, Math.max(1, n)); this.#save(); }

  // ----- Übungen -----

  hasExercise(name) {
    const n = name.trim().toLowerCase();
    return this.exercises.some(e => e.toLowerCase() === n);
  }

  addExercise(name, group = null) {
    const n = name.trim();
    if (!n || this.hasExercise(n)) return;
    this.exercises.push(n);
    const g = group ?? catalogGroup(n);
    if (g) this.groups[n] = g;
    this.#save();
  }

  /** Eigene Übung anlegen; false, wenn der Name leer ist oder schon existiert. */
  addCustomExercise(name, group, equipment, timed, muscles = null) {
    const n = name.trim();
    if (!n || this.hasExercise(n) || catalogGroup(n)) return false;
    this.exercises.push(n);
    this.groups[n] = group;
    if (equipment) CustomMeta.equipment[n] = equipment;
    if (timed && !isCardio(group)) CustomMeta.timed.add(n);
    if (muscles && (muscles.primary.length || muscles.secondary.length)) CustomMeta.muscles[n] = muscles;
    this.#save();
    return true;
  }

  /** Gespeicherte Gruppe, sonst aus dem Katalog, sonst „Sonstige“. */
  groupOf(name) { return this.groups[name] ?? catalogGroup(name) ?? 'SONSTIGE'; }

  deleteExercise(name) {
    this.exercises = this.exercises.filter(e => e !== name);
    delete this.groups[name];
    delete CustomMeta.equipment[name];
    delete CustomMeta.muscles[name];
    CustomMeta.timed.delete(name);
    this.sets = this.sets.filter(s => s.exercise !== name);
    for (const w of this.workouts) {
      w.exercises = w.exercises.filter(e => e !== name);
      delete w.targets[name];
    }
    this.#save();
  }

  // ----- Workouts -----

  workout(id) { return this.workouts.find(w => w.id === id) ?? null; }

  addWorkout(name) {
    const n = name.trim();
    if (!n) return null;
    const w = { id: Date.now(), name: n, exercises: [], targets: {} };
    this.workouts.push(w);
    this.#save();
    return w;
  }

  /** Ziel-Sätze einer Übung im Workout (Standard 0 = optional). */
  targetFor(w, exercise) { return w.targets[exercise] ?? 0; }

  setTarget(workoutId, exercise, target) {
    const w = this.workout(workoutId);
    if (!w) return;
    w.targets[exercise] = Math.min(20, Math.max(0, target));
    this.#save();
  }

  /** Übung von Position `from` nach `to` verschieben (ohne zu speichern – das macht saveNow beim Loslassen). */
  moveInWorkout(workoutId, from, to) {
    const w = this.workout(workoutId);
    if (!w || from === to || from < 0 || to < 0 || from >= w.exercises.length || to >= w.exercises.length) return;
    const [x] = w.exercises.splice(from, 1);
    w.exercises.splice(to, 0, x);
  }

  duplicateWorkout(id) {
    const w = this.workout(id);
    if (!w) return null;
    const copy = { id: Date.now(), name: w.name + tr(' (Kopie)', ' (copy)'), exercises: [...w.exercises], targets: { ...w.targets } };
    this.workouts.push(copy);
    this.#save();
    return copy;
  }

  /** Workout aus Vorlage: fehlende Übungen werden angelegt; Ziel 3 Sätze bzw. 1 Einheit Cardio. */
  addWorkoutFromTemplate(t) {
    t.exercises.forEach(e => {
      if (!this.hasExercise(e)) { this.exercises.push(e); const g = catalogGroup(e); if (g) this.groups[e] = g; }
    });
    const w = {
      id: Date.now(), name: tr(t.de, t.en), exercises: [...t.exercises],
      targets: Object.fromEntries(t.exercises.map(e => [e, isCardio(this.groupOf(e)) ? 1 : 3])),
    };
    this.workouts.push(w);
    this.#save();
    return w;
  }

  deleteWorkout(id) {
    this.workouts = this.workouts.filter(w => w.id !== id);
    this.#save();
  }

  /** Übung im Workout an-/abhaken (fehlt sie in „Meine Übungen“, wird sie dort angelegt). */
  toggleInWorkout(id, exercise) {
    const w = this.workout(id);
    if (!w) return;
    if (w.exercises.includes(exercise)) {
      w.exercises = w.exercises.filter(e => e !== exercise);
    } else {
      if (!this.hasExercise(exercise)) { this.exercises.push(exercise); const g = catalogGroup(exercise); if (g) this.groups[exercise] = g; }
      w.exercises.push(exercise);
    }
    this.#save();
  }

  // ----- Sätze -----

  addSet(exercise, weight, reps) {
    let now = Date.now();
    // IDs müssen eindeutig sein (zwei Sätze in derselben Millisekunde)
    while (this.sets.some(s => s.id === now)) now++;
    this.sets.push({ id: now, exercise, weight, reps, time: now });
    this.#save();
  }

  updateSet(set, weight, reps) {
    const s = this.sets.find(x => x.id === set.id);
    if (!s) return;
    s.weight = weight; s.reps = reps;
    this.#save();
  }

  deleteSet(set) {
    this.sets = this.sets.filter(s => s.id !== set.id);
    this.#save();
  }

  /** Sätze einer Übung im Zeitraum, älteste zuerst. */
  setsBetween(exercise, start, end) {
    return this.sets.filter(s => s.exercise === exercise && s.time >= start && s.time <= end).sort((a, b) => a.time - b.time);
  }

  /** Alle Sätze einer Übung, neueste zuerst. */
  setsFor(exercise) {
    return this.sets.filter(s => s.exercise === exercise).sort((a, b) => b.time - a.time);
  }

  // ----- Trainings -----

  /** Training abschließen; null, wenn kein Satz eingetragen wurde (dann wird nichts gespeichert). */
  finishTraining(workout, start) {
    const end = Date.now();
    if (workout.exercises.every(e => this.setsBetween(e, start, end).length === 0)) return null;
    const t = {
      id: start, workoutId: workout.id, workoutName: workout.name, exercises: [...workout.exercises], start, end,
      targets: Object.fromEntries(workout.exercises.map(e => [e, this.targetFor(workout, e)])),
    };
    this.sessions.push(t);
    this.#save();
    return t;
  }

  session(id) { return this.sessions.find(t => t.id === id) ?? null; }

  /** Trainings eines Workouts, neuestes zuerst. */
  sessionsFor(workoutId) {
    return this.sessions.filter(t => t.workoutId === workoutId).sort((a, b) => b.start - a.start);
  }

  // ----- Freies Training (Sätze ohne gestartetes Workout) -----

  #inSession(s) {
    return this.sessions.some(t => s.time >= t.start && s.time <= t.end && t.exercises.includes(s.exercise));
  }

  /** Sätze, die zu keinem gespeicherten Training gehören, gruppiert nach Tag (JJJJMMTT). */
  freeSetsByDay() {
    const map = new Map();
    for (const s of this.sets) {
      if (this.#inSession(s)) continue;
      const k = dayKey(s.time);
      if (!map.has(k)) map.set(k, []);
      map.get(k).push(s);
    }
    return map;
  }

  /** „Virtuelles“ Training aus den freien Sätzen eines Tages; negative ID = -JJJJMMTT. */
  freeSession(day, byDay = this.freeSetsByDay()) {
    const list = byDay.get(day)?.slice().sort((a, b) => a.time - b.time);
    if (!list?.length) return null;
    const exs = [...new Set(list.map(s => s.exercise))];
    return {
      id: -day, workoutId: -1, workoutName: tr('Freies Training', 'Free workout'), exercises: exs,
      start: list[0].time, end: list[list.length - 1].time,
      targets: Object.fromEntries(exs.map(e => [e, list.filter(s => s.exercise === e).length])),
    };
  }

  /** Nur für Sätze eines freien Trainings: gehört der Satz zu keinem gespeicherten Training? */
  isFreeSet(s) { return !this.#inSession(s); }

  // ----- Körpergewicht -----

  addWeight(kg) { this.weights.push({ time: Date.now(), kg }); this.#save(); }
  deleteWeight(entry) { this.weights = this.weights.filter(w => w !== entry); this.#save(); }

  /** Gewicht zum Zeitpunkt t: letzter Eintrag davor, sonst der erste; null = nie eingetragen. */
  weightAt(t) {
    let kg = null;
    for (const w of this.weights) if (w.time <= t) kg = w.kg;
    return kg ?? this.weights[0]?.kg ?? null;
  }
}

export const store = new GymStore();
export { TEMPLATES };

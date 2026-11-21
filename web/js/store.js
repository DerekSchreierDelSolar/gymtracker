// Gesamter Zustand + Laden/Speichern + alle Änderungen. Entspricht GymStore.kt.
// Gespeichert wird im selben JSON-Format wie die Android-App (Export/Import funktioniert in beide Richtungen),
// und zwar im localStorage des Browsers.

import { RENAMED_EXERCISES, TEMPLATES } from './data.js';
import {
  CustomMeta, catalogGroup, isCardio, setAppLanguage, appLanguage, tr, dayKey, SET_TYPES, setTypeOf, isWork,
  GROUP_KEYS, EQUIPMENT_KEYS, migrateArmGroup,
} from './core.js';

const KEY = 'gym_data';
const migrateName = n => RENAMED_EXERCISES[n] ?? n;

class GymStore {
  exercises = [];
  sets = [];            // { id, exercise, weight, reps, time, type? } (type fehlt = NORMAL)
  groups = {};          // Übungsname → Muskelgruppe
  workouts = [];        // { id, name, exercises: [], targets: {} }
  sessions = [];        // { id, workoutId, workoutName, exercises, start, end, targets }
  weights = [];         // { time, kg }, älteste zuerst
  notes = {};           // Notizen: { Übung: { Tag (dayKey): Text } } – wie GymStore.notes
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

  /**
   * Fortschritt zurücksetzen (wie resetProgress in GymStore.kt): Sätze, Trainings, Notizen, Gewichte löschen;
   * Übungen, Workouts und Einstellungen bleiben. Vorheriger Stand zusätzlich unter „gym_data_before_reset“.
   */
  resetProgress() {
    const snap = { sets: this.sets, sessions: this.sessions, weights: this.weights, notes: this.notes };
    const old = localStorage.getItem(KEY);
    if (old) localStorage.setItem('gym_data_before_reset', old);
    this.sets = []; this.sessions = []; this.weights = []; this.notes = {};
    this.#save();
    return snap;
  }

  /** „Rückgängig“ nach resetProgress() */
  restoreProgress(s) {
    Object.assign(this, { sets: s.sets, sessions: s.sessions, weights: s.weights, notes: s.notes });
    this.#save();
  }

  // ----- Laden / Speichern -----

  #readFrom(root) {
    // Daten können aus einer fremden/kaputten Sicherung stammen → alles prüfen, Ungültiges überspringen
    // (wie Android: unbekannte Kategorie/Gerät ignorieren). Schlüssel wie „__proto__“ nie übernehmen,
    // sonst ließe sich über eine präparierte Datei Object.prototype verändern.
    const arr = x => (Array.isArray(x) ? x : []);
    const obj = x => (x && typeof x === 'object' && !Array.isArray(x) ? x : {});
    const objs = x => arr(x).filter(o => o && typeof o === 'object' && !Array.isArray(o));
    const entries = o => Object.entries(obj(o)).filter(([k]) => k !== '__proto__' && k !== 'constructor' && k !== 'prototype');
    const num = x => (typeof x === 'number' && Number.isFinite(x) ? x : null);
    const intMap = o => Object.fromEntries(entries(o).map(([k, v]) => [migrateName(k), Number(v) || 0]));
    const names = x => arr(x).filter(n => typeof n === 'string').map(migrateName);

    for (const n of names(root.exercises)) if (!this.exercises.includes(n)) this.exercises.push(n);
    // Katalog-Übungen ignorieren (Kategorie kommt aus dem Katalog); frühere Kategorie „ARME“ unten nach Muskeln aufteilen
    const oldArms = [];
    for (const [k, g] of entries(root.groups)) {
      const n = migrateName(k);
      if (catalogGroup(n)) continue;
      if (g === 'ARME') oldArms.push(n); else if (GROUP_KEYS.includes(g)) this.groups[n] = g;
    }
    for (const [k, e] of entries(root.customEquipment)) if (EQUIPMENT_KEYS.includes(e)) CustomMeta.equipment[migrateName(k)] = e;
    for (const t of names(root.customTimed)) CustomMeta.timed.add(t);
    for (const [k, m] of entries(root.customMuscles)) {
      CustomMeta.muscles[migrateName(k)] = { primary: arr(m?.primary).map(String), secondary: arr(m?.secondary).map(String) };
    }
    for (const n of oldArms) this.groups[n] = migrateArmGroup(CustomMeta.muscles[n]);

    this.workouts = objs(root.workouts).map(o => ({
      id: o.id, name: String(o.name ?? ''),
      exercises: names(o.exercises),
      targets: intMap(o.targets),
      // Trainingstage laut Plan: 1 = Montag … 7 = Sonntag (wie Workout.days)
      days: arr(o.days).map(Number).filter(d => d >= 1 && d <= 7),
    }));
    const s = obj(root.settings);
    this.weeklyGoal = num(s.weeklyGoal) ?? 3;
    this.restSeconds = num(s.restSeconds) ?? 90;
    this.lastExport = num(s.lastExport) ?? 0;
    this.themeMode = s.theme ?? 'system';
    setAppLanguage(s.language ?? 'de');

    this.weights = objs(root.weights).filter(o => num(o.time) != null && num(o.kg) != null)
      .map(o => ({ time: o.time, kg: o.kg })).sort((a, b) => a.time - b.time);
    for (const [ex, days] of entries(root.notes)) {
      const n = migrateName(ex);
      for (const [d, text] of entries(days)) if (text) (this.notes[n] ??= {})[d] = String(text);
    }
    this.sessions = objs(root.sessions).filter(o => num(o.start) != null && num(o.end) != null).map(o => ({
      id: o.id, workoutId: o.workoutId, workoutName: String(o.workoutName ?? ''),
      exercises: names(o.exercises),
      start: o.start, end: o.end, targets: intMap(o.targets),
    }));
    this.sets = objs(root.sets)
      .filter(o => typeof o.exercise === 'string' && num(o.weight) != null && num(o.reps) != null && num(o.time) != null)
      .map(o => ({
        id: o.id, exercise: migrateName(o.exercise), weight: o.weight, reps: o.reps, time: o.time,
        // Satz-Typ nur speichern, wenn er nicht „Normal“ ist (wie Android)
        ...(SET_TYPES.includes(o.type) && o.type !== 'NORMAL' ? { type: o.type } : {}),
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
      workouts: this.workouts.map(({ days, ...w }) => (days?.length ? { ...w, days } : w)),
      sessions: this.sessions,
      weights: this.weights,
      notes: this.notes,
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
    this.#clearAll();
    try {
      this.#readFrom(root);
    } catch (e) {
      // Halb eingelesene Daten verwerfen, alten Stand zurückholen (sonst überschreibt ihn der nächste Speichervorgang)
      this.#clearAll();
      if (old) this.#readFrom(JSON.parse(old));
      throw e;
    }
    this.#save();
  }

  #clearAll() {
    this.exercises = []; this.sets = []; this.groups = {}; this.workouts = []; this.sessions = []; this.weights = []; this.notes = {};
    CustomMeta.equipment = {}; CustomMeta.timed = new Set(); CustomMeta.muscles = {};
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
  /** Katalog-Übungen: Kategorie aus dem Katalog; eigene: gespeicherte (wie groupOf in GymStore.kt) */
  groupOf(name) { return catalogGroup(name) ?? this.groups[name] ?? 'SONSTIGE'; }

  /** Notiz einer Übung an einem Tag (dayKey), null = keine. */
  noteFor(exercise, day) { return this.notes[exercise]?.[day] ?? null; }
  /** Notiz setzen; leerer Text löscht sie. */
  setNote(exercise, day, text) {
    const t = text.trim();
    if (t) (this.notes[exercise] ??= {})[day] = t;
    else if (this.notes[exercise]) { delete this.notes[exercise][day]; if (!Object.keys(this.notes[exercise]).length) delete this.notes[exercise]; }
    this.#save();
  }

  /** Nur aus „Meine Übungen“ entfernen: Verlauf, Notizen, Kategorie und Workouts bleiben (wie removeFromList in GymStore.kt). */
  removeFromList(name) {
    const index = this.exercises.indexOf(name);
    this.exercises = this.exercises.filter(e => e !== name);
    this.#save();
    return index;
  }

  /** Aus der Liste entfernte Übung an ihren alten Platz zurück („Rückgängig“) */
  restoreToList(name, index) {
    if (this.hasExercise(name)) return;
    this.exercises.splice(Math.max(0, Math.min(index, this.exercises.length)), 0, name);
    this.#save();
  }

  /** Eigene Übungen (nicht im Katalog): in der Liste, in einem Workout oder mit Verlauf – auch aus der Liste entfernte. */
  get customNames() {
    return [...new Set([...this.exercises, ...this.workouts.flatMap(w => w.exercises), ...this.sets.map(s => s.exercise)])]
      .filter(n => !catalogGroup(n));
  }

  /** Übung komplett löschen: auch Verlauf, Notizen und aus allen Workouts. */
  deleteExercise(name) {
    delete this.notes[name];
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

  /** Trainingstag (1 = Mo … 7 = So) im Plan an-/abwählen */
  toggleDay(workoutId, day) {
    const w = this.workout(workoutId);
    if (!w) return;
    const days = w.days ?? [];
    w.days = days.includes(day) ? days.filter(d => d !== day) : [...days, day].sort();
    this.#save();
  }

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
    const copy = { id: Date.now(), name: w.name + tr(' (Kopie)', ' (copy)'), exercises: [...w.exercises], targets: { ...w.targets }, days: [] }; // Plan-Tage nicht doppelt belegen
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

  addSet(exercise, weight, reps, type = 'NORMAL') {
    let now = Date.now();
    // IDs müssen eindeutig sein (zwei Sätze in derselben Millisekunde)
    while (this.sets.some(s => s.id === now)) now++;
    this.sets.push({ id: now, exercise, weight, reps, time: now, ...(type !== 'NORMAL' ? { type } : {}) });
    this.#save();
  }

  updateSet(set, weight, reps, type = setTypeOf(set)) {
    const s = this.sets.find(x => x.id === set.id);
    if (!s) return;
    s.weight = weight; s.reps = reps;
    if (type === 'NORMAL') delete s.type; else s.type = type;
    this.#save();
  }

  /** Gelöschten Satz zurückholen („Rückgängig“) */
  restoreSet(set) {
    if (!this.sets.some(s => s.id === set.id)) this.sets.push(set);
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
      targets: Object.fromEntries(exs.map(e => [e, list.filter(s => s.exercise === e && isWork(s)).length])),
    };
  }

  /** Nur für Sätze eines freien Trainings: gehört der Satz zu keinem gespeicherten Training? */
  isFreeSet(s) { return !this.#inSession(s); }

  // ----- Körpergewicht -----

  /** time = gewählter Tag (Nachtragen); Liste bleibt nach Zeit sortiert */
  addWeight(kg, time = Date.now()) { this.weights.push({ time, kg }); this.weights.sort((a, b) => a.time - b.time); this.#save(); }
  /** Gelöschten Eintrag zurückholen („Rückgängig“) */
  restoreWeight(entry) { this.weights.push(entry); this.weights.sort((a, b) => a.time - b.time); this.#save(); }
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

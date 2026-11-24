// Gesamter Zustand + Laden/Speichern + alle Änderungen. Entspricht GymStore.kt.
// Gespeichert wird im selben JSON-Format wie die Android-App (Export/Import funktioniert in beide Richtungen),
// und zwar im localStorage des Browsers.

import { RENAMED_EXERCISES, TEMPLATES } from './data.js';
import { isLb, setWeightUnit,
  CustomMeta, catalogGroup, isCardio, setAppLanguage, appLanguage, tr, dayKey, SET_TYPES, setTypeOf, isWork,
  GROUP_KEYS, EQUIPMENT_KEYS, migrateArmGroup, validYear, validHeight,
} from './core.js';

const KEY = 'gym_data';
/** Körpermaße: Typ → [Einheit, min, max, de, en] (wie MeasureType in Measures.kt) */
export const MEASURE_TYPES = {
  WAIST: ['cm', 40, 250, 'Taille', 'Waist'],
  CHEST: ['cm', 40, 250, 'Brust', 'Chest'],
  ARM: ['cm', 10, 80, 'Arm', 'Arm'],
  HIP: ['cm', 40, 250, 'Hüfte', 'Hips'],
  THIGH: ['cm', 20, 120, 'Oberschenkel', 'Thigh'],
  BODYFAT: ['%', 2, 70, 'Körperfett', 'Body fat'],
};
/** Zieltypen wie GoalType in Goals.kt; STRENGTH/ENDURANCE gehören zu einer Übung. */
export const GOAL_TYPES = ['STRENGTH', 'ENDURANCE', 'BODYWEIGHT', 'CARDIO_WEEK', 'REGULAR'];
export const GOAL_NEEDS_EXERCISE = ['STRENGTH', 'ENDURANCE'];
const migrateName = n => RENAMED_EXERCISES[n] ?? n;

// Sicherungskopien im localStorage (vor Import/Zurücksetzen, beschädigte Stände). Sie belegen jeweils so viel
// Platz wie die Daten selbst – ist das Limit (~5 MB) erreicht, werden sie zuerst geopfert.
const COPY_KEYS = () => Object.keys(localStorage).filter(k => k === 'gym_data_before_import' || k === 'gym_data_before_reset' || k.startsWith('gym_data_corrupt_'));
/** setItem, das bei vollem Speicher erst die Kopien löscht und es dann noch einmal versucht. */
function storeItem(key, value, { dropCopies = true } = {}) {
  try { localStorage.setItem(key, value); return true; } catch { /* voll */ }
  if (!dropCopies) return false;
  for (const k of COPY_KEYS()) if (k !== key) localStorage.removeItem(k);
  try { localStorage.setItem(key, value); return true; } catch { return false; }
}

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
  profile = {};          // optional für genauere kcal: { sex: 'm'|'f', birthYear, height } – wie GymStore.profile
  measures = [];         // Körpermaße [{ time, type, value }] (measures.js) – wie GymStore.measures
  cycle = null;          // Trainingsblock mit Deload { start, weeks } (cycle.js) – wie GymStore.cycle
  goals = [];            // langfristige Ziele { id, type, exercise?, target, start, created } – wie GymStore.goals (goals.js)
  favorites = [];        // Lieblingsübungen (★, in Listen zuerst) – wie GymStore.favorites (JSON `favorites` nur wenn nicht leer)
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
      storeItem(`gym_data_corrupt_${Date.now()}`, text, { dropCopies: false });
      // Nur die 3 neuesten beschädigten Stände behalten
      Object.keys(localStorage).filter(k => k.startsWith('gym_data_corrupt_')).sort().reverse().slice(3).forEach(k => localStorage.removeItem(k));
    }
  }

  /** Wird nach jeder Änderung aufgerufen (die App zeichnet dann neu). */
  subscribe(fn) { this.#listeners.add(fn); }
  #changed() { this.#rev++; this.#listeners.forEach(fn => fn()); }

  // Zwischenspeicher für teure Auswertungen (allSessions, sessionStats, recordEvents, Abzeichen, XP …):
  // gilt bis zur nächsten Änderung (#rev) bzw. bis Mitternacht. Wie memo in GymStore.kt.
  #rev = 0;
  #memo = new Map();
  #memoRev = -1;
  #memoDay = -1;
  /** Ergebnis von fn für key (String oder Objekt, z. B. ein Training), einmal pro Datenstand berechnet. */
  memo(key, fn) {
    const day = dayKey(Date.now());
    if (this.#memoRev !== this.#rev || this.#memoDay !== day) { this.#memo.clear(); this.#memoRev = this.#rev; this.#memoDay = day; }
    if (this.#memo.has(key)) return this.#memo.get(key);
    const v = fn();
    this.#memo.set(key, v);
    return v;
  }
  /** Sätze einer Übung, älteste zuerst (Index pro Übung). */
  setsOf(exercise) {
    return this.memo('setsByExercise', () => {
      const m = new Map();
      for (const s of this.sets) { if (!m.has(s.exercise)) m.set(s.exercise, []); m.get(s.exercise).push(s); }
      for (const l of m.values()) l.sort((a, b) => a.time - b.time);
      return m;
    }).get(exercise) ?? [];
  }

  #save() {
    // Schlägt das Speichern fehl (Speicher voll), meldet app.js das – die Änderung wäre sonst beim Schließen weg
    if (!storeItem(KEY, JSON.stringify(this.#toJson()))) window.dispatchEvent(new Event('gym-save-failed'));
    this.#changed();
  }
  saveNow() { this.#save(); }

  /**
   * Fortschritt zurücksetzen (wie resetProgress in GymStore.kt): Sätze, Trainings, Notizen, Gewichte löschen;
   * Übungen, Workouts und Einstellungen bleiben. Vorheriger Stand zusätzlich unter „gym_data_before_reset“.
   */
  resetProgress() {
    const snap = { sets: this.sets, sessions: this.sessions, weights: this.weights, notes: this.notes, measures: this.measures };
    const old = localStorage.getItem(KEY);
    if (old) storeItem('gym_data_before_reset', old, { dropCopies: false });
    this.sets = []; this.sessions = []; this.weights = []; this.notes = {}; this.measures = [];
    this.#save();
    return snap;
  }

  /** „Rückgängig“ nach resetProgress() */
  restoreProgress(s) {
    Object.assign(this, { sets: s.sets, sessions: s.sessions, weights: s.weights, notes: s.notes, measures: s.measures ?? [] });
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
    // Puls eines Trainings (aus Health Connect, nur Android) – wie HeartRate.fromJson in Model.kt
    const heartRate = o => {
      if (!o || typeof o !== 'object') return null;
      const avg = Math.round(num(o.avg) ?? 0), max = Math.round(num(o.max) ?? 0);
      if (avg < 20 || avg > 250 || max < avg || max > 250) return null;
      const min = Math.min(avg, Math.max(0, Math.round(num(o.min) ?? avg)));
      const ints = a => arr(a).map(v => Math.min(250, Math.max(0, Math.round(num(v) ?? 0))));
      const curve = ints(o.curve), fine = ints(o.fine);   // fine = Ø je 10 s (Puls pro Satz), fehlt bei älteren Daten
      return fine.length ? { avg, max, min, curve, fine } : { avg, max, min, curve };
    };
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
      // Supersätze: mit der nächsten Übung verbunden (wie Workout.links)
      links: names(o.links),
    }));
    const s = obj(root.settings);
    this.weeklyGoal = num(s.weeklyGoal) ?? 3;
    this.restSeconds = num(s.restSeconds) ?? 90;
    this.lastExport = num(s.lastExport) ?? 0;
    this.themeMode = s.theme ?? 'system';
    setAppLanguage(s.language ?? 'de');
    setWeightUnit(s.unit);

    // Profil (optional): ungültige Werte überspringen (wie readFrom in GymStore.kt)
    const pr = obj(root.profile);
    this.profile = {};
    if (pr.sex === 'm' || pr.sex === 'f') this.profile.sex = pr.sex;
    if (validYear(num(pr.birthYear))) this.profile.birthYear = num(pr.birthYear);
    if (validHeight(num(pr.height))) this.profile.height = num(pr.height);

    // Körpermaße: unbekannter Typ oder Wert außerhalb des Bereichs → überspringen (wie GymStore.kt)
    this.measures = objs(root.measures).filter(o => typeof o.type === 'string' && Object.hasOwn(MEASURE_TYPES, o.type) && num(o.time) != null && num(o.value) != null
      && o.value >= MEASURE_TYPES[o.type][1] && o.value <= MEASURE_TYPES[o.type][2]).map(o => ({ time: o.time, type: o.type, value: o.value }))
      .sort((a, b) => a.time - b.time);

    // Trainingsblock (wie GymStore.kt): Wochen 2–8
    const cy = obj(root.cycle);
    this.cycle = num(cy.start) != null && Number.isInteger(cy.weeks) && cy.weeks >= 2 && cy.weeks <= 8 ? { start: cy.start, weeks: cy.weeks } : null;

    this.favorites = [...new Set(names(root.favorites))];
    // Ziele: unbekannter Typ, fehlende Übung oder Zielwert ≤ 0 → überspringen (wie GymStore.kt)
    this.goals = objs(root.goals).filter(o => GOAL_TYPES.includes(o.type) && num(o.target) > 0 &&
      (!GOAL_NEEDS_EXERCISE.includes(o.type) || typeof o.exercise === 'string')).map((o, i) => ({
      id: num(o.id) ?? i, type: o.type,
      ...(GOAL_NEEDS_EXERCISE.includes(o.type) ? { exercise: migrateName(o.exercise) } : {}),
      target: o.target, start: num(o.start) ?? 0, created: num(o.created) ?? 0,
    }));

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
      ...(heartRate(o.hr) ? { hr: heartRate(o.hr) } : {}),
    }));
    this.sets = objs(root.sets)
      .filter(o => typeof o.exercise === 'string' && num(o.weight) != null && num(o.reps) != null && num(o.time) != null)
      .map(o => ({
        id: o.id, exercise: migrateName(o.exercise), weight: o.weight, reps: o.reps, time: o.time,
        // Satz-Typ nur speichern, wenn er nicht „Normal“ ist (wie Android)
        ...(SET_TYPES.includes(o.type) && o.type !== 'NORMAL' ? { type: o.type } : {}),
        // Wdh. in Reserve (Schritt 12, wie GymSet.rir) nur wenn gültig
        ...(Number.isInteger(o.rir) && o.rir >= 0 && o.rir <= 10 ? { rir: o.rir } : {}),
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
      workouts: this.workouts.map(({ days, links, ...w }) => {
        const l = (links ?? []).filter(e => w.exercises.includes(e));
        return { ...w, ...(days?.length ? { days } : {}), ...(l.length ? { links: l } : {}) };
      }),
      sessions: this.sessions,
      weights: this.weights,
      notes: this.notes,
      settings: {
        restSeconds: this.restSeconds,
        lastExport: this.lastExport,
        theme: this.themeMode,
        language: appLanguage,
        ...(isLb() ? { unit: 'lb' } : {}),
        weeklyGoal: this.weeklyGoal,
      },
      sets: this.sets,
      ...(Object.keys(this.profile).length ? { profile: this.profile } : {}),
      ...(this.favorites.length ? { favorites: this.favorites } : {}),
      ...(this.goals.length ? { goals: this.goals } : {}),
      ...(this.cycle ? { cycle: this.cycle } : {}),
      ...(this.measures.length ? { measures: this.measures } : {}),
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
    if (old) storeItem('gym_data_before_import', old, { dropCopies: false });
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
    this.#rev++;
    this.exercises = []; this.sets = []; this.groups = {}; this.workouts = []; this.sessions = []; this.weights = []; this.notes = {};
    CustomMeta.equipment = {}; CustomMeta.timed = new Set(); CustomMeta.muscles = {};
    this.profile = {};
    this.goals = [];
    this.favorites = [];
    this.cycle = null;
    this.measures = [];
    this.weeklyGoal = 3; // Sicherung ohne „settings“ → Standard statt des alten Werts
  }

  markExported() { this.lastExport = Date.now(); this.#save(); }

  // ----- Einstellungen -----

  setRest(sec) { this.restSeconds = sec; this.#save(); }
  setTheme(mode) { this.themeMode = mode; this.#save(); }
  /** Profil ändern; null/ungültig = Feld entfernen */
  updateProfile(changes) {
    const p = { ...this.profile, ...changes };
    for (const k of Object.keys(p)) if (p[k] == null) delete p[k];
    this.profile = p; this.#save();
  }
  setLanguage(lang) { setAppLanguage(lang); this.#save(); }
  /** Gewichtseinheit "kg" | "lb" (nur Anzeige/Eingabe, gespeichert wird immer kg) – wie setUnit in GymStore.kt. */
  setUnit(u) { setWeightUnit(u); this.#save(); }
  /** Muskeln einer eigenen Übung festlegen (leere Auswahl = entfernen). */
  setCustomMuscles(name, m) {
    if (m.primary.length || m.secondary.length) CustomMeta.muscles[name] = m; else delete CustomMeta.muscles[name];
    this.#save();
  }

  changeWeeklyGoal(n) { this.weeklyGoal = Math.min(14, Math.max(1, n)); this.#save(); }

  // ----- Übungen -----

  // ----- Favoriten (★) – wie GymStore.kt -----
  isFavorite(name) { return this.favorites.includes(name); }
  /** Favorit an/aus, gibt den neuen Zustand zurück */
  toggleFavorite(name) {
    const now = !this.isFavorite(name);
    this.favorites = now ? [...this.favorites, name] : this.favorites.filter(e => e !== name);
    this.#save();
    return now;
  }

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
    // Ziele zu dieser Übung hätten sonst keine Übung mehr (wie deleteExercise in GymStore.kt)
    this.goals = this.goals.filter(g => g.exercise !== name);
    this.favorites = this.favorites.filter(e => e !== name);
    for (const w of this.workouts) {
      w.exercises = w.exercises.filter(e => e !== name);
      delete w.targets[name];
      if (w.links) w.links = w.links.filter(e => e !== name);
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

  changeCycle(c) { this.cycle = c; this.#save(); }

  /** Supersatz-Verbindungen setzen (Auflösen + Rückgängig, supersets.js) */
  setLinks(workoutId, links) {
    const w = this.workout(workoutId);
    if (!w) return;
    w.links = [...links];
    this.#save();
  }

  /** Supersatz bilden: neue Reihenfolge + Verbindungen aus groupedWorkout (supersets.js; dort berechnet, kein Kreis-Import) */
  linkSuperset(workoutId, { exercises, links }) {
    const w = this.workout(workoutId);
    if (!w) return;
    w.exercises = exercises;
    w.links = links;
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

  /**
   * Workout aus einem Training anlegen („Als Workout speichern“ nach einem freien Training, free.js):
   * Übungen in der Reihenfolge des Trainings, Ziel-Sätze = dort gemachte Arbeitssätze.
   */
  addWorkoutFrom(name, exercises, targets) {
    const n = String(name).trim();
    if (!n || !exercises.length) return null;
    exercises.forEach(e => this.addExercise(e));
    const w = {
      id: Date.now(), name: n, exercises: [...exercises],
      targets: Object.fromEntries(Object.entries(targets).map(([e, t]) => [e, Math.max(0, Math.min(20, t))])),
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
    const s = { id: now, exercise, weight, reps, time: now, ...(type !== 'NORMAL' ? { type } : {}) };
    this.sets.push(s);
    this.#save();
    return s;
  }

  /** Wdh. in Reserve setzen (null = entfernen) – wie GymStore.setRir */
  setRir(id, rir) {
    const s = this.sets.find(x => x.id === id);
    if (!s) return;
    if (rir == null) delete s.rir; else s.rir = rir;
    this.#save();
  }

  updateSet(set, weight, reps, type = setTypeOf(set), rir = set.rir ?? null) {
    const s = this.sets.find(x => x.id === set.id);
    if (!s) return;
    s.weight = weight; s.reps = reps;
    if (type === 'NORMAL') delete s.type; else s.type = type;
    if (rir == null || type === 'WARMUP') delete s.rir; else s.rir = rir;
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
    return this.setsOf(exercise).filter(s => s.time >= start && s.time <= end);
  }

  /** Alle Sätze einer Übung, neueste zuerst. */
  setsFor(exercise) {
    return this.memo('setsFor:' + exercise, () => this.setsOf(exercise).slice().reverse()).slice();
  }

  // ----- Trainings -----

  /** Training abschließen; null, wenn kein Satz eingetragen wurde (dann wird nichts gespeichert).
   *  endAt = Ende (letzter Satz, wenn das Beenden vergessen wurde), null = jetzt. */
  finishTraining(workout, start, endAt = null) {
    const end = Math.max(start, endAt ?? Date.now());
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
    return this.memo('freeSetsByDay', () => this.#computeFreeSetsByDay());
  }

  #computeFreeSetsByDay() {
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
  // ----- Ziele (goals.js) -----
  addGoal(g) { this.goals.push(g); this.#save(); }
  updateGoal(g) { const i = this.goals.findIndex(x => x.id === g.id); if (i >= 0) { this.goals[i] = g; this.#save(); } }
  /** Löscht das Ziel, gibt die Position zurück (für „Rückgängig“). */
  deleteGoal(g) { const i = this.goals.findIndex(x => x.id === g.id); if (i >= 0) { this.goals.splice(i, 1); this.#save(); } return i; }
  restoreGoal(g, i) { if (this.goals.some(x => x.id === g.id)) return; this.goals.splice(Math.min(Math.max(i, 0), this.goals.length), 0, g); this.#save(); }

  // ----- Körpermaße (measures.js) -----
  addMeasure(type, value, time = Date.now()) { this.measures.push({ time, type, value }); this.measures.sort((a, b) => a.time - b.time); this.#save(); }
  updateMeasure(m, value, time = m.time) {
    const i = this.measures.indexOf(m);
    if (i < 0) return;
    this.measures[i] = { time, type: m.type, value };
    this.measures.sort((a, b) => a.time - b.time);
    this.#save();
  }
  deleteMeasure(m) { this.measures = this.measures.filter(x => x !== m); this.#save(); }
  restoreMeasure(m) { this.measures.push(m); this.measures.sort((a, b) => a.time - b.time); this.#save(); }

  restoreWeight(entry) { this.weights.push(entry); this.weights.sort((a, b) => a.time - b.time); this.#save(); }
  deleteWeight(entry) { this.weights = this.weights.filter(w => w !== entry); this.#save(); }
  /** Gewicht und ggf. Datum eines Eintrags ändern, Liste bleibt sortiert – wie updateWeight in GymStore.kt */
  updateWeight(entry, kg, time = entry.time) {
    const i = this.weights.indexOf(entry);
    if (i < 0) return;
    this.weights[i] = { time, kg };
    this.weights.sort((a, b) => a.time - b.time);
    this.#save();
  }

  /** Gewicht zum Zeitpunkt t: letzter Eintrag davor, sonst der erste; null = nie eingetragen. */
  weightAt(t) {
    let kg = null;
    for (const w of this.weights) if (w.time <= t) kg = w.kg;
    return kg ?? this.weights[0]?.kg ?? null;
  }
}

export const store = new GymStore();
export { TEMPLATES };

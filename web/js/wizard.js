// Workout-Assistent im Bot („Erstelle mir ein Workout“) – wie WorkoutWizard.kt.
// 1. Muskeln antippen (1× rot = Hauptfokus, 2× orange = mittel, 3× gelb = leicht, 4× aus),
// 2. Geräte wählen, 3. passende Katalog-Übungen suchen und das Workout anlegen.

import { CATALOG } from './data.js';
import { tr, isCardio, EQUIPMENT_KEYS } from './core.js';
import { musclesOf, levelsOf, muscleOfRegion, muscleLabel } from './bodymap.js';

export const WIZARD_STAGES = [1, 0.6, 0.3];
export const nextStage = s => (s == null ? 1 : s === 1 ? 0.6 : s === 0.6 ? 0.3 : null);

export const wizard = {
  muscles: {},    // Muskel-ID → Stufe
  equipment: [],  // erlaubte Geräte; leer = alle
  reset() { this.muscles = {}; this.equipment = []; },
  tap(id) { const n = nextStage(this.muscles[id]); if (n == null) delete this.muscles[id]; else this.muscles[id] = n; },
};

/** Geräte, die angeboten werden (Cardiogeräte passen nicht zu Muskelzielen) */
export const equipmentChoices = () => EQUIPMENT_KEYS.filter(e => e !== 'CARDIO_GERAET');

/** Beitrag einer Übung je Muskel: Hauptmuskel 1, Hilfsmuskel 0,3 (stärkste Region zählt) */
export function contribution(name) {
  const m = musclesOf(name);
  if (!m) return {};
  const out = {};
  for (const [r, v] of Object.entries(levelsOf(m))) {
    const id = muscleOfRegion(r), w = v >= 0.8 ? 1 : 0.3;
    out[id] = Math.max(out[id] ?? 0, w);
  }
  return out;
}

/**
 * Übungen auswählen (gierig, wie WorkoutWizard.pick): Bedarf = Übungen mit dem Muskel als Hauptmuskel –
 * Hauptfokus 3, mittel 1,5, leicht 0,6 (Hilfsmuskel-Arbeit zählt 0,3). Mindestens 4 Übungen, wenn es passende gibt.
 * Jede Runde gewinnt die Übung, die den offenen Bedarf am meisten deckt (gewichtet nach Stufe);
 * Abzug für Hauptarbeit an nicht gewählten Muskeln. Pro Grundübung eine Variante, höchstens 8 Übungen.
 */
export function pickExercises(store) {
  const sel = wizard.muscles;
  if (!Object.keys(sel).length) return [];
  const need = Object.fromEntries(Object.entries(sel).map(([m, s]) => [m, s === 1 ? 3 : s === 0.6 ? 1.5 : 0.6]));
  const allowed = new Set(wizard.equipment);
  const candidates = CATALOG
    .filter(c => !isCardio(c.group) && (!allowed.size || allowed.has(c.equipment)))
    .map(c => [c, contribution(c.name)]).filter(([, k]) => Object.keys(k).length);
  const chosen = [];
  while (chosen.length < 8 && (Object.values(need).some(v => v > 0.01) || chosen.length < 4)) {
    const filling = !Object.values(need).some(v => v > 0.01); // Bedarf gedeckt, aber noch keine 4 Übungen
    let best = null, bestScore = 0.15;
    for (const [c, k] of candidates) {
      if (chosen.some(x => x.base === c.base)) continue;
      let score = 0;
      for (const [m, w] of Object.entries(k)) {
        if (need[m] != null) { if (need[m] > 0) score += Math.min(need[m], w) * sel[m]; else if (filling) score += w * sel[m] * 0.5; }
        else if (w >= 1) score -= 0.3;
      }
      if (store.hasExercise(c.name) || store.setsFor(c.name).length) score += 0.1;
      if (score > bestScore) { best = c; bestScore = score; }
    }
    if (!best) break;
    chosen.push(best);
    for (const [m, w] of Object.entries(contribution(best.name))) if (need[m] != null) need[m] -= w;
  }
  return chosen.map(c => c.name);
}

/** Name aus den stärksten gewählten Muskeln („Brust & Trizeps“), eindeutig gemacht */
export function wizardName(store) {
  const main = Object.entries(wizard.muscles).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([m]) => muscleLabel(m));
  const base = main.join(' & ') || tr('Mein Workout', 'My workout');
  if (!store.workouts.some(w => w.name === base)) return base;
  let i = 2;
  while (store.workouts.some(w => w.name === `${base} ${i}`)) i++;
  return `${base} ${i}`;
}

/** Workout anlegen (Übungen landen in „Meine Übungen“), Ziel 3 Sätze je Übung */
export function createWizardWorkout(store, exercises) {
  const w = store.addWorkout(wizardName(store));
  if (!w) return null;
  for (const e of exercises) { store.toggleInWorkout(w.id, e); store.setTarget(w.id, e, 3); }
  return store.workout(w.id);
}

/**
 * Ähnliche Übungen für die Alternative im Training (wie similarExercises in WorkoutWizard.kt):
 * Kosinus-Ähnlichkeit der Muskel-Beiträge; angezeigt wird die reine Übereinstimmung, für die Reihenfolge
 * zählen gleiche Grundübung (+0,08) und gleiche Kategorie (+0,04) etwas mehr. Ergebnis: [[Name, Prozent], …]
 */
export function similarExercises(store, name, exclude, limit = 10) {
  const me = contribution(name);
  const keys = Object.keys(me);
  if (!keys.length) return [];
  const norm = v => Math.sqrt(Object.values(v).reduce((a, x) => a + x * x, 0));
  const meNorm = norm(me), myGroup = store.groupOf(name);
  const myBase = CATALOG.find(c => c.name === name)?.base;
  const pool = [...CATALOG.map(c => c.name), ...store.customNames.filter(n => !CATALOG.some(c => c.name === n))];
  return pool
    .filter(o => o !== name && !exclude.includes(o) && isCardio(store.groupOf(o)) === isCardio(myGroup))
    .map(o => {
      const v = contribution(o);
      if (!Object.keys(v).length) return null;
      const sim = keys.reduce((a, m) => a + me[m] * (v[m] ?? 0), 0) / (meNorm * norm(v));
      let rank = sim;
      if (myBase && CATALOG.find(c => c.name === o)?.base === myBase) rank += 0.08;
      if (store.groupOf(o) === myGroup) rank += 0.04;
      return [o, sim, rank];
    })
    .filter(x => x && x[1] >= 0.55)
    .sort((a, b) => b[2] - a[2])
    // höchstens 2 Varianten je Grundübung, damit verschiedene Übungen zur Auswahl stehen
    .filter((x, _, arr) => { const b = CATALOG.find(c => c.name === x[0])?.base; return !b || arr.filter(y => CATALOG.find(c => c.name === y[0])?.base === b).indexOf(x) < 2; })
    .slice(0, limit)
    .map(([o, s]) => [o, Math.round(Math.min(1, s) * 100)]);
}

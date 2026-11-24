// Freies Training als gestartetes Training (wie FreeTraining.kt): startet leer (trainingWorkoutId = FREE_WORKOUT_ID),
// Übungen kommen im Training über die Seite „＋ Übung“ dazu (letzte Seite). Gespeichert wird es beim Beenden wie jedes
// Training, mit workoutId = -1 – wie die automatisch gebildeten freien Tage (store.freeSession).

import { store } from './store.js';
import { CATALOG } from './data.js';
import { tr, exName, fmtDate, sortByName, favoritesFirst, GROUP_KEYS, groupLabel, groupColor, isWork } from './core.js';
import { allSessions } from './stats.js';
import { exerciseIconHtml, muscleIconHtml } from './pictograms.js';
import { h, icon, chip, dialog, textField, muted, categoryRow } from './ui.js';
import { nav, update, rerender, openDialog, closeDialog, emptyFilter } from './nav.js';
import { isFav, nameWithStar } from './favorites.js';
import { matches } from './filter.js';

export const FREE_WORKOUT_ID = -1;

/** Workout des laufenden freien Trainings – nur im Speicher, nie in store.workouts. */
export const freeWorkout = exercises => ({ id: FREE_WORKOUT_ID, name: tr('Freies Training', 'Free workout'), exercises: [...exercises], targets: {}, links: [] });

/** Freie Trainings (gestartet und gespeichert oder aus losen Sätzen eines Tages), neueste zuerst. */
export const freeSessions = () => allSessions(store).filter(t => t.workoutId === FREE_WORKOUT_ID).sort((a, b) => b.start - a.start);

/** Letztes freies Training vor `before` (für „Wie letztes Mal“). */
export const lastFreeSession = (before = Infinity) => freeSessions().find(t => t.start < before) ?? null;

/** Alle Übungen, die man hinzufügen kann: Katalog + eigene. */
const allExerciseNames = () => [...CATALOG.map(c => c.name), ...store.customNames.filter(n => !CATALOG.some(c => c.name.toLowerCase() === n.toLowerCase()))];

/** Übungen ins laufende freie Training (auch in „Meine Übungen“) und zur ersten davon springen. */
export function addFree(names, extra = {}) {
  const fresh = names.filter(n => !nav.freeExercises.includes(n));
  fresh.forEach(n => store.addExercise(n));
  const list = [...nav.freeExercises, ...fresh];
  const i = names.length ? list.indexOf(names[0]) : -1;
  update({ freeExercises: list, ...(i >= 0 ? { trainingIndex: i } : {}), ...extra });
}

// Suche / geöffnete Kategorie der Seite „＋ Übung“ (nur solange die App offen ist)
let query = '', openGroup = null;

/**
 * Letzte Seite im freien Training: oben das letzte freie Training (einzeln oder alle übernehmen), dann Favoriten und
 * zum Schluss Suche bzw. Kategorien; schon hinzugefügte tragen ein ✓ (wie FreeAddPage in FreeTraining.kt).
 */
export function freeAddPage(w, start) {
  const exercises = w.exercises;
  const inTraining = n => exercises.includes(n);
  const open = n => { const i = exercises.indexOf(n); if (i >= 0) update({ trainingIndex: i }); };
  const add = list => addFree(list);
  const last = lastFreeSession(start);
  const lastNames = last ? [...new Set(last.exercises)] : [];
  const all = allExerciseNames();
  const favs = sortByName(all.filter(n => isFav(n) && !lastNames.includes(n)), exName);

  const chips = names => h('div', { class: 'chips wrap' }, names.map(n => inTraining(n)
    ? chip('✓ ' + exName(n), true, () => open(n))
    : h('button', { class: 'chip', onclick: () => add([n]) }, '＋ ' + exName(n))));
  const line = n => h('button', { class: 'pick-row', onclick: () => (inTraining(n) ? open(n) : add([n])) },
    h('span', { html: exerciseIconHtml(n, store.groupOf(n), 36) }),
    h('span', { class: 'grow' }, nameWithStar(n)),
    h('span', { class: inTraining(n) ? 'accent' : 'muted' }, inTraining(n) ? icon('check') : '+'));
  const header = text => h('h2', { class: 'section' }, text);

  let rest;
  if (query.trim()) {
    const f = { ...emptyFilter(), query };
    const hits = favoritesFirst(sortByName(all.filter(n => matches(f, n, store.groupOf(n))), exName), isFav).slice(0, 40);
    rest = hits.length ? hits.map(line) : muted(tr('Keine Übung gefunden.', 'No exercise found.'));
  } else {
    rest = h('div', { class: 'category-list' }, GROUP_KEYS.map(g => {
      const names = all.filter(n => store.groupOf(n) === g);
      if (!names.length) return null;
      return [
        categoryRow(g, groupLabel(g), muscleIconHtml(g, 44), groupColor(g), names.length, names.filter(inTraining).length,
          () => { openGroup = openGroup === g ? null : g; rerender(); }),
        openGroup === g ? favoritesFirst(sortByName(names, exName), isFav).map(line) : null,
      ];
    }));
  }

  return [
    h('h2', { class: 'headline' }, tr('Übung hinzufügen', 'Add exercise')),
    exercises.length ? null : muted(tr('Freies Training: Wähle die erste Übung – weitere kommen jederzeit hier dazu.',
      'Free workout: pick your first exercise – add more here any time.'), 'small'),
    last ? [
      header(tr('Letztes freies Training · ', 'Last free workout · ') + fmtDate(last.start)),
      chips(lastNames),
      (missing => missing.length > 1
        ? h('div', null, h('button', { class: 'btn', onclick: () => add(missing) }, icon('add', 18), tr(`Alle ${missing.length} übernehmen`, `Add all ${missing.length}`)))
        : null)(lastNames.filter(n => !inTraining(n))),
    ] : null,
    favs.length ? [header('★ ' + tr('Favoriten', 'Favorites')), chips(favs)] : null,
    header(tr('Alle Übungen', 'All exercises')),
    textField({ id: 'free-search', label: tr('Übung suchen …', 'Search exercises …'), value: query, oninput: v => { query = v; rerender(); } }),
    rest,
  ];
}

/** Neues freies Training: Suche/Kategorie der Seite „＋ Übung“ zurücksetzen */
export function resetFreePage() { query = ''; openGroup = null; }

/**
 * „Als Workout speichern“ unter einem freien Training (Übersicht): legt ein Workout mit denselben Übungen an,
 * Ziel-Sätze = gemachte Arbeitssätze (wie SaveAsWorkoutButton in FreeTraining.kt).
 */
export function saveAsWorkoutButton(t, onOpenWorkout) {
  const sets = Object.fromEntries(t.exercises.map(e => [e, store.setsBetween(e, t.start, t.end).filter(isWork).length]));
  const exercises = [...new Set(t.exercises)].filter(e => sets[e] > 0);
  if (!exercises.length) return null;
  const ask = () => {
    const st = { name: tr('Freies Training', 'Free workout') + ' ' + fmtDate(t.start) };
    openDialog(() => dialog(tr('Als Workout speichern', 'Save as workout'), [
      textField({ id: 'save-workout', label: tr('Name', 'Name'), value: st.name, oninput: v => { st.name = v; rerender(); } }),
      muted(exercises.map(exName).join(' · '), 'small'),
    ], [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      {
        label: tr('Speichern', 'Save'), disabled: !st.name.trim(),
        onClick: () => {
          const w = store.addWorkoutFrom(st.name, exercises, Object.fromEntries(exercises.map(e => [e, sets[e]])));
          closeDialog();
          if (w) onOpenWorkout(w.id);
        },
      },
    ], closeDialog));
  };
  return h('div', null,
    h('button', { class: 'btn outline wide', onclick: ask }, icon('list', 18), tr('Als Workout speichern', 'Save as workout')),
    muted(tr('Gleiche Übungen, Ziel-Sätze wie heute – zum Wiederholen.', 'Same exercises, target sets as today – to repeat it.'), 'small'));
}

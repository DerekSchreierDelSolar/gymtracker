// Navigation über einfachen Zustand – wie GymApp() in MainActivity.kt.
// Reihenfolge in app.js → render(): Übersicht > Training > Übung > Workout-Auswahl > Workout > Katalog > Startseite.
//
// Zusätzlich hängt jede „Vorwärts“-Navigation einen Eintrag an die Browser-History,
// damit die Zurück-Geste / der Zurück-Button des Browsers wie BackHandler funktioniert.

import { monthIndex, dayKey } from './core.js';

export const emptyFilter = () => ({ groups: new Set(), equipment: new Set(), query: '' });

export const nav = {
  tab: 0,
  exercise: null,        // geöffnete Übung
  catalog: false,
  wiki: false,           // Übungs-Wiki geöffnet
  bot: false,            // Bildschirm „Fragen“ geöffnet
  wikiQuery: '',
  wikiTab: 0,            // Wiki-Reiter: 0 Übungen, 1 Muskeln, 2 Supplements
  statPeriod: 'WEEK',     // Zeitraum Heatmap/Muskelgruppen auf der Startseite
  reviewPeriod: 'WEEK',   // Rückblick auf der Startseite: WEEK oder MONTH
  reviewMonth: 1,         // Rückblick-Monat: 1 = letzter Monat, 2 = vorletzter …
  workoutId: null,
  pick: false,           // Übungsauswahl fürs Workout
  pickDetail: null,      // beim Bearbeiten geöffnete Übung (Info + Ziel-Sätze + Hinzufügen)
  editInTraining: null,  // Workout wird während des Trainings bearbeitet (Wert = vorher angezeigte Übung)
  pickGroup: null,       // beim Bearbeiten geöffnete Kategorie (null = Übersicht)
  catalogGroup: null,    // im Katalog geöffnete Kategorie (null = Übersicht)
  trainingStart: null,   // laufendes Training (Startzeit)
  trainingWorkoutId: null, // Workout des laufenden Trainings (unabhängig von workoutId = angezeigtes Workout)
  trainingOpen: true,    // false = minimiert (Leiste „Training läuft“ statt Trainingsbildschirm)
  swaps: {},             // nur für das laufende Training getauscht: Original → Alternative
  trainingIndex: 0,
  restEnd: null,         // Pausen-Timer: Endzeitpunkt (ms)
  restTotal: 0,
  summaryId: null,
  celebrateId: null,     // Übersicht direkt nach dem Training → feiern (celebration.js), wie GymApp.celebrateId
  calMonth: monthIndex(Date.now()),
  calDay: dayKey(Date.now()),
  listFilter: emptyFilter(),
  catalogFilter: emptyFilter(),
  pickFilter: emptyFilter(),
  // Aufgeklappte Suche/Filter je Bildschirm
  filterUi: { list: { search: false, filters: false }, catalog: { search: false, filters: false }, pick: { search: false, filters: false } },
  dialog: null,          // Funktion, die den offenen Dialog zeichnet (null = keiner)
  menuOpen: false,
};

// ----- Laufendes Training überlebt einen Neustart der App (iOS beendet Web-Apps im Hintergrund) -----
const TRAINING_KEY = 'gym_training';
try {
  const t = JSON.parse(localStorage.getItem(TRAINING_KEY) ?? 'null');
  if (t?.trainingStart) {
    t.trainingWorkoutId ??= t.workoutId; // ältere Version: Training hing am angezeigten Workout
    delete t.workoutId;
    Object.assign(nav, t);
  }
} catch { /* ignorieren */ }

function persistTraining() {
  if (nav.trainingStart == null) localStorage.removeItem(TRAINING_KEY);
  else {
    const { trainingWorkoutId, trainingOpen, trainingStart, trainingIndex, restEnd, restTotal, swaps } = nav;
    localStorage.setItem(TRAINING_KEY, JSON.stringify({ trainingWorkoutId, trainingOpen, trainingStart, trainingIndex, restEnd, restTotal, swaps }));
  }
}

let renderer = () => {};
export function setRenderer(fn) { renderer = fn; }
export function rerender() { renderer(); }

/** Zustand ändern und neu zeichnen (ohne History-Eintrag). */
export function update(patch) {
  Object.assign(nav, patch);
  persistTraining();
  renderer();
}

let depth = 0;
/** Zu einem tieferen Bildschirm wechseln (mit History-Eintrag für „Zurück“). */
export function go(patch) {
  history.pushState({ depth: ++depth }, '');
  update({ ...patch, menuOpen: false });
}

let backHandler = () => false;
/** app.js setzt hier die Logik, was „Zurück“ im aktuellen Zustand bedeutet. */
export function setBackHandler(fn) { backHandler = fn; }

/** Zurück-Button in der App. */
export function goBack() {
  if (depth > 0) history.back(); // löst popstate aus → backHandler
  else backHandler();
}

window.addEventListener('popstate', () => {
  depth = Math.max(0, depth - 1);
  // false = Zurück wurde abgefangen (z. B. Training läuft) → History-Eintrag wiederherstellen
  if (backHandler() === false) history.pushState({ depth: ++depth }, '');
});

// ----- Dialoge -----

/** Öffnet einen Dialog. `render` wird bei jedem Neuzeichnen aufgerufen und liefert den Inhalt. */
export function openDialog(render) { update({ dialog: render, menuOpen: false }); }
export function closeDialog() { update({ dialog: null }); }

// ----- Wischgesten -----

/** Waagerechtes Wischen erkennen (für Tabs und Trainings-Seiten). */
export function onSwipe(el, onLeft, onRight) {
  let x0 = null, y0 = 0;
  el.addEventListener('touchstart', e => {
    // Nicht in waagerecht scrollbaren Leisten (Chips) auslösen
    if (e.target.closest('.hscroll, input, textarea, .drag-active')) { x0 = null; return; }
    x0 = e.touches[0].clientX; y0 = e.touches[0].clientY;
  }, { passive: true });
  el.addEventListener('touchend', e => {
    if (x0 == null) return;
    const dx = e.changedTouches[0].clientX - x0, dy = e.changedTouches[0].clientY - y0;
    x0 = null;
    if (Math.abs(dx) > 70 && Math.abs(dx) > Math.abs(dy) * 1.5) (dx < 0 ? onLeft : onRight)?.();
  }, { passive: true });
}

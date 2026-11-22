// Einstieg: welcher Bildschirm oben liegt (wie GymApp in MainActivity.kt), Neuzeichnen, Design, Takt.

import { store } from './store.js';
import { tr } from './core.js';
import { tickers } from './ui.js';
import { nav, update, setRenderer, setBackHandler, go, goBack, closeDialog } from './nav.js';
import { homeScreen } from './screens/home.js';
import { exerciseDetailScreen } from './screens/exercise.js';
import { workoutScreen, workoutPickerScreen, workoutExercisePage, catalogScreen } from './screens/workout.js';
import { wikiScreen } from './wiki.js';
import { botScreen } from './bot.js';
import { trainingScreen, summaryScreen, confirmEndTraining, keepScreenOn, unlockAudio } from './screens/training.js';

const root = document.getElementById('app');

// ---------- Aktionen, die mehrere Bildschirme betreffen ----------

function startTraining(workoutId) {
  unlockAudio(); // im Tipp-Ereignis: erlaubt später den Pausen-Ton
  go({ workoutId, trainingStart: Date.now(), trainingIndex: 0, restEnd: null, pick: false, exercise: null, swaps: {} });
}

function finishTraining() {
  const w = store.workout(nav.workoutId);
  // Getauschte Übungen speichern; Originale, die vor dem Tausch schon Sätze hatten, gehören trotzdem dazu
  const s = w ? sessionWorkout(w, nav.swaps) : null;
  if (s) s.exercises.push(...Object.keys(nav.swaps ?? {}).filter(o => !s.exercises.includes(o) && store.setsBetween(o, nav.trainingStart, Infinity).length));
  const t = s ? store.finishTraining(s, nav.trainingStart) : null;
  keepScreenOn(false);
  update({ trainingStart: null, trainingIndex: 0, restEnd: null, swaps: {}, summaryId: t?.id ?? null });
}

// ---------- Welcher Bildschirm? ----------

function currentScreen() {
  const workout = nav.workoutId != null ? store.workout(nav.workoutId) : null;
  // Negative ID = freies Training (aus den Sätzen des Tages gebaut)
  const summary = nav.summaryId == null ? null
    : nav.summaryId < 0 ? store.freeSession(-nav.summaryId) : store.session(nav.summaryId);

  if (summary) return summaryScreen(summary, goBack);
  // Während des Trainings das Workout bearbeiten (wie editInTraining in GymApp): Training läuft weiter
  if (workout && nav.trainingStart != null && nav.editInTraining != null) {
    return nav.pickDetail != null ? workoutExercisePage(workout, nav.pickDetail) : workoutPickerScreen(workout);
  }
  if (workout && nav.trainingStart != null) return trainingScreen(sessionWorkout(workout, nav.swaps), finishTraining, workout);
  if (nav.trainingStart != null) update({ trainingStart: null }); // Workout wurde gelöscht
  if (nav.exercise != null) return exerciseDetailScreen(nav.exercise);
  if (workout && nav.pick && nav.pickDetail != null) return workoutExercisePage(workout, nav.pickDetail);
  if (workout && nav.pick) return workoutPickerScreen(workout);
  if (workout) return workoutScreen(workout, () => startTraining(workout.id));
  if (nav.catalog) return catalogScreen();
  if (nav.bot) return botScreen(store);
  if (nav.wiki) return wikiScreen();
  return homeScreen(startTraining);
}

/** „Zurück“ – gleiche Reihenfolge wie die BackHandler in GymApp. false = nicht zurückgehen. */
setBackHandler(() => {
  if (nav.dialog) { closeDialog(); return false; }
  if (nav.menuOpen) { update({ menuOpen: false }); return false; }
  if (nav.summaryId != null) { update({ summaryId: null }); return true; }
  if (nav.trainingStart != null && nav.editInTraining != null) {
    if (nav.pickDetail != null) { update({ pickDetail: null }); return true; }
    if (nav.pickGroup != null) { update({ pickGroup: null }); return true; }
    backToTraining();
    return true;
  }
  if (nav.trainingStart != null) {
    const w = store.workout(nav.workoutId);
    if (w) { confirmEndTraining(w, finishTraining); return false; }
  }
  if (nav.exercise != null) { update({ exercise: null }); return true; }
  if (nav.pickDetail != null) { update({ pickDetail: null }); return true; }
  if (nav.pickGroup != null) { update({ pickGroup: null }); return true; }
  if (nav.pick) { update({ pick: false, pickDetail: null, pickGroup: null }); return true; }
  if (nav.workoutId != null) { update({ workoutId: null }); return true; }
  if (nav.catalogGroup != null) { update({ catalogGroup: null }); return true; }
  if (nav.catalog) { update({ catalog: false }); return true; }
  if (nav.bot) { update({ bot: false }); return true; }
  if (nav.wiki) { update({ wiki: false, wikiQuery: '', wikiTab: 0 }); return true; }
  return true;
});

// ---------- Neuzeichnen ----------

const scrollPositions = new Map();
let rendering = false, again = false;

function render() {
  if (rendering) { again = true; return; } // Änderung während des Zeichnens → danach nochmal
  rendering = true;
  try {
    do {
      again = false;
      // Scroll-Position und Fokus merken, damit Neuzeichnen beim Tippen nicht stört
      const oldMain = root.querySelector('main.content');
      if (oldMain) scrollPositions.set(oldMain.dataset.scroll, oldMain.scrollTop);
      const active = document.activeElement;
      const focusId = active?.id && root.contains(active) ? active.id : null;
      const sel = focusId && 'selectionStart' in active ? [active.selectionStart, active.selectionEnd] : null;

      tickers.length = 0;
      applyTheme();
      document.documentElement.lang = tr('de', 'en');
      const view = currentScreen();
      root.replaceChildren(view, nav.dialog ? nav.dialog() : '');

      const main = root.querySelector('main.content');
      if (main) main.scrollTop = scrollPositions.get(main.dataset.scroll) ?? 0;
      if (focusId) {
        const el = document.getElementById(focusId);
        if (el) {
          el.focus({ preventScroll: true });
          try { if (sel) el.setSelectionRange(sel[0], sel[1]); } catch { /* Typ ohne Auswahl */ }
        }
      }
      root.querySelectorAll('[data-autoscroll]').forEach(el => el.scrollIntoView({ block: 'nearest', inline: 'center' }));
    } while (again);
  } finally {
    rendering = false;
  }
}

setRenderer(render);
store.subscribe(render);

// ---------- Design ----------

const darkQuery = window.matchMedia('(prefers-color-scheme: dark)');
function applyTheme() {
  const dark = store.themeMode === 'dark' || (store.themeMode === 'system' && darkQuery.matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0B0B10' : '#F5F6FA');
}
darkQuery.addEventListener('change', render);

// ---------- Takt: Uhren und Timer ~4× pro Sekunde ----------

setInterval(() => { for (const fn of [...tickers]) fn(); }, 250);
// Beim Zurückkehren in die App (iOS pausiert Timer im Hintergrund) sofort aktualisieren
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') render(); });

// ---------- Offline-Fähigkeit ----------

if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  navigator.serviceWorker.register('./sw.js').catch(e => console.warn('Service Worker:', e));
}
// Browser bitten, die Daten nicht automatisch zu löschen
navigator.storage?.persist?.();

render();

/** Workout fürs laufende Training: getauschte Übungen ersetzt, Ziele übernommen (wie sessionWorkout in MainActivity.kt) */
function sessionWorkout(w, swaps = {}) {
  if (!Object.keys(swaps).length) return w;
  return {
    ...w,
    exercises: w.exercises.map(e => swaps[e] ?? e),
    targets: Object.fromEntries(Object.entries(w.targets).map(([k, v]) => [swaps[k] ?? k, v])),
  };
}

/** Bearbeiten beenden: wieder ins Training, auf die vorher angezeigte Übung (Index kann sich verschoben haben) */
function backToTraining() {
  const w = store.workout(nav.workoutId);
  const i = w ? sessionWorkout(w, nav.swaps).exercises.indexOf(nav.editInTraining) : -1;
  update({ editInTraining: null, pickGroup: null, pickDetail: null, ...(i >= 0 ? { trainingIndex: i } : {}) });
}

// Startseite mit 5 Tabs – entspricht HomeScreen.kt, DashboardTab.kt, CalendarTab.kt, BodyTab.kt („Fragen“ ist ein eigener Bildschirm).

import { store } from '../store.js';
import { fmtKg, fmtVol, fmtWU, wUnit, toUnit, parseW, isLb, weightUnit,
  tr, exName, sortByName, fmtSet, fmtW, fmtVolume, fmtKcal, fmtDate, fmtDuration, fmtShortDate, fmtWeek, plural, isCardio, isEnglish, groupLabel,
  GROUP_KEYS, startOfWeek, addDays, dayKey, monthName, weekdayDate, parseNum, appLanguage, equipmentOf, isWork,
  validYear, validHeight, profileComplete,
} from '../core.js';
import { allSessions, sessionStats, recordEvents, weekStreakInfo, weeklyVolumes, weeklyKcal, nextWorkoutAfter, planPick, dueToday } from '../stats.js';
import { exerciseIconHtml } from '../pictograms.js';
import {
  h, icon, iconBtn, chip, dialog, textField, dateField, isoDay, timeOfIsoDay, statBox, chartCard, horizontalBars, historyBars, lineChart,
  percentRing, ring, muted, toast, groupHeader, undoToast, showHint, hintUsed,
} from '../ui.js';
import { nav, go, update, rerender, openDialog, closeDialog, onSwipe } from '../nav.js';
import { screen, fab } from '../layout.js';
import { exerciseTile } from './exercise.js';
import { muscleHeatCard, periodFilter, periodLabel } from '../bodymap.js';
import { reviewCard } from '../review.js';
import { isFav } from '../favorites.js';
import { goalsCard } from '../goals.js';
import { morningCard, anticipation } from '../morning.js';
import { levelCard } from '../levels.js';
import { cycleSettings, cycleLine } from '../cycle.js';
import { measuresCard } from '../measures.js';
import { workoutList, newWorkoutDialog } from './workout.js';
import { filterActions, filterBar, matches, noFilterResults } from '../filter.js';


const TABS = () => [
  ['home', tr('Start', 'Home'), tr('Übersicht', 'Overview')],
  ['dumbbell', tr('Übungen', 'Exercises'), tr('Übungen', 'Exercises')],
  ['list', 'Workouts', 'Workouts'],
  ['calendar', tr('Kalender', 'Calendar'), tr('Kalender', 'Calendar')],
  ['body', tr('Körper', 'Body'), tr('Körper', 'Body')],
];

export function homeScreen(onStartWorkout) {
  const tab = Math.min(nav.tab, 4); // früher gab es Tab 5 („Fragen“) – alte gespeicherte Zustände abfangen
  const setTab = i => { if (i >= 0 && i < 5) update({ tab: i, menuOpen: false }); };
  const content = [dashboardTab, exerciseGrid, workoutList, calendarTab, bodyTab][tab](onStartWorkout);
  const wrap = h('div', { class: 'tab-page' }, content);
  onSwipe(wrap, () => setTab(tab + 1), () => setTab(tab - 1));

  return screen({
    title: TABS()[tab][2], large: true, key: 'tab-' + tab, // nur der Bereichsname (ohne App-Namen darunter)
    actions: [
      tab === 1 && store.exercises.length ? filterActions('list') : null,
      // Wiki und Fragen immer direkt erreichbar (wie HomeScreen.kt)
      iconBtn('book', () => go({ wiki: true }), { label: tr('Wiki', 'Wiki') }),
      iconBtn('chat', () => go({ bot: true }), { label: tr('Fragen', 'Ask') }),
      h('div', { class: 'menu-anchor' },
        iconBtn('more', () => update({ menuOpen: !nav.menuOpen }), { label: 'Menü' }),
        nav.menuOpen ? menu() : null),
    ],
    top: tab === 1 && store.exercises.length ? filterBar('list', exerciseGroups(), exerciseEquipment()) : null,
    content: wrap,
    fab: tab === 1 ? fab(tr('Übung', 'Exercise'), 'add', () => go({ catalog: true }))
      : tab === 2 ? fab('Workout', 'add', newWorkoutDialog) : null,

    bottom: h('nav', { class: 'bottom-nav' }, TABS().map(([ico, label], i) =>
      h('button', { class: ['nav-item', i === tab && 'selected'], onclick: () => setTab(i) },
        h('span', { class: 'nav-icon' }, icon(ico)), h('span', null, label)))),
  });
}

// ---------- ⋮-Menü: Einstellungen (screens/settings.js), Export, Import ----------

function menu() {
  const close = () => update({ menuOpen: false });
  return [
    h('div', { class: 'menu-backdrop', onclick: close }),
    h('div', { class: 'menu' },
      h('button', { onclick: () => go({ settings: true }) }, icon('settings', 20), tr('Einstellungen', 'Settings')),
      h('hr'),
      h('button', { onclick: () => { close(); exportData(); } }, icon('download', 20), tr('Daten exportieren', 'Export data')),
      h('button', { onclick: () => { close(); importData(); } }, icon('upload', 20), tr('Daten importieren', 'Import data')),
      h('small', { class: 'muted' }, store.lastExport === 0 ? tr('Noch nie exportiert', 'Never exported')
        : tr('Letzter Export: ', 'Last export: ') + fmtDate(store.lastExport))),
  ];
}

/** Export: auf dem iPhone über „Teilen“ (→ „In Dateien sichern“), sonst als Download. */
export async function exportData() {
  const d = new Date();
  const name = `gymtracker-backup-${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}.json`;
  const json = store.exportJson();
  const file = new File([json], name, { type: 'application/json' });
  try {
    if (navigator.canShare?.({ files: [file] })) {
      await navigator.share({ files: [file], title: 'GymTracker Backup' });
    } else {
      const url = URL.createObjectURL(file);
      const a = h('a', { href: url, download: name });
      document.body.append(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
    }
    store.markExported();
    toast(tr('Backup gespeichert', 'Backup saved'));
  } catch (e) {
    if (e.name !== 'AbortError') toast(tr('Export fehlgeschlagen: ', 'Export failed: ') + e.message);
  }
}

export function importData() {
  const input = h('input', { type: 'file', style: 'display:none' });
  input.addEventListener('change', async () => {
    const f = input.files?.[0];
    input.remove();
    if (!f) return;
    // Echte Sicherungen sind weit kleiner – eine riesige Datei würde den Browser nur einfrieren
    if (f.size > 20 * 1024 * 1024) { toast(tr('Das ist keine gültige GymTracker-Sicherung.', 'This is not a valid GymTracker backup.')); return; }
    const text = await f.text();
    const info = store.inspectBackup(text);
    if (!info) { toast(tr('Das ist keine gültige GymTracker-Sicherung.', 'This is not a valid GymTracker backup.')); return; }
    openDialog(() => dialog(tr('Sicherung importieren?', 'Import backup?'), [
      h('p', null, tr('Die Sicherung enthält:', 'The backup contains:')),
      h('ul', null,
        h('li', null, plural(info.exercises, tr('Übung', 'exercise'), tr('Übungen', 'exercises'))),
        h('li', null, plural(info.sets, tr('Satz', 'set'), tr('Sätze', 'sets'))),
        h('li', null, plural(info.workouts, 'Workout', 'Workouts')),
        h('li', null, plural(info.sessions, tr('Trainingseinheit', 'session'), tr('Trainingseinheiten', 'sessions')))),
      h('p', null, tr('Deine aktuellen Daten werden dadurch ersetzt. Eine Kopie davon wird vorher in der App abgelegt.',
        'Your current data will be replaced. A copy of it is kept in the app first.')),
    ], [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      {
        label: tr('Importieren', 'Import'), onClick: () => {
          closeDialog();
          try { store.importJson(text); toast(tr('Sicherung importiert', 'Backup imported')); } catch (e) { toast(tr('Import fehlgeschlagen: ', 'Import failed: ') + e.message); }
        },
      },
    ], closeDialog));
  });
  document.body.append(input);
  input.click();
}

// ---------- Tab 0: Übersicht ----------

const isIos = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const isStandalone = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

function dashboardTab(onStartWorkout) {
  const sessions = allSessions(store);
  const weekSessions = sessions.filter(t => t.start >= startOfWeek());
  const weekStats = weekSessions.map(t => sessionStats(store, t));
  const streakInfo = weekStreakInfo(store);
  const streak = streakInfo.weeks;
  // Joker in den letzten 2 Wochen eingesetzt → in der Karte erwähnen (wie DashboardTab.kt)
  const jokerRecent = streakInfo.lastJoker != null && streakInfo.lastJoker >= startOfWeek(addDays(startOfWeek(), -14));
  const goal = store.weeklyGoal;
  const lastSession = store.sessions.reduce((a, t) => (!a || t.start > a.start ? t : a), null);
  // Schnellstart: mit Trainingsplan „Heute dran“ bzw. das nächste geplante Workout, sonst „Weiter mit“
  // = das Workout NACH dem zuletzt trainierten (wie DashboardTab.kt)
  const pick = planPick(store);
  const next = pick?.workout ?? nextWorkoutAfter(store.workouts, lastSession?.workoutId);

  const volumes = weeklyVolumes(store, 8);
  const kcalWeeks = (k => (k && k.some(v => v[1] > 0) ? k : null))(weeklyKcal(store, 8));
  const records = recordEvents(store).slice(0, 10);
  const weekKcal = weekStats.reduce((a, s) => a + (s.kcal ?? 0), 0);
  const backupDue = store.sets.length > 0 && Date.now() - store.lastExport > 14 * 24 * 3600 * 1000;

  return h('div', { class: 'stack pad' },
    // Hinweis zum Installieren (nur iPhone/iPad im Safari-Browser)
    isIos() && !isStandalone() ? h('div', { class: 'card hint' }, h('span', { class: 'hint-emoji' }, '📲'),
      h('div', { class: 'grow' }, h('b', null, tr('Als App installieren', 'Install as app')),
        h('small', null, tr('In Safari unten auf „Teilen“ tippen, dann „Zum Home-Bildschirm“. Danach startet GymTracker wie eine normale App.',
          'In Safari tap "Share", then "Add to Home Screen". GymTracker then opens like a regular app.')))) : null,

    // Der Morgen danach: Neuanfang + letztes Training (morning.js)
    morningCard(),

    h('button', { class: 'card week-card', onclick: () => { hintUsed('weekGoal'); weeklyGoalDialog(); } },
     h('div', { class: 'week-main' },
      percentRing(Math.min(1, weekSessions.length / goal), 84),
      h('div', { class: 'grow' },
        h('small', { class: 'muted' }, tr('Wochenziel', 'Weekly goal')),
        // Ziel erreicht/übertroffen → „5 Trainings ✓“ statt „5 von 3“ (wie DashboardTab.kt)
        h('h2', null, weekSessions.length >= goal ? plural(weekSessions.length, tr('Training', 'workout'), tr('Trainings', 'workouts')) + ' ✓'
          : tr(`${weekSessions.length} von ${goal} Trainings`, `${weekSessions.length} of ${goal} workouts`)),
        h('span', null, streak > 0 ? '🔥 ' + tr(`${plural(streak, 'Woche', 'Wochen')} in Folge`, `${plural(streak, 'week', 'weeks')} in a row`)
          + (jokerRecent ? tr(' · 🃏 Joker hat sie gerettet', ' · 🃏 saved by a joker') : '')
          : tr('Starte deine Serie!', 'Start your streak!')),
        // Trainingsblock (cycle.js)
        (l => (l ? h('small', { class: 'accent' }, l) : null))(cycleLine()),
        // „tippen zum Ändern“ nur, bis das Ziel einmal geöffnet wurde (Hints)
        showHint('weekGoal') || weekSessions.length >= goal ? h('small', { class: 'muted' }, weekSessions.length >= goal
          ? tr(`Ziel ${goal}/Woche`, `Goal ${goal}/week`) + (showHint('weekGoal') ? tr(' · tippen zum Ändern', ' · tap to change') : '')
          : tr('Tippen, um das Ziel zu ändern', 'Tap to change goal')) : null)),
     // Kennzahlen dieser Woche (früher eigene Kachel „Diese Woche“, wie DashboardTab.kt)
     h('hr', { class: 'card-divider' }),
     h('div', { class: 'week-stats' },
       weekStat(tr('Sätze', 'Sets'), String(weekStats.reduce((a, s) => a + s.sets, 0))),
       weekStat(tr('Volumen', 'Volume'), `${fmtVol(weekStats.reduce((a, s) => a + s.volume, 0))}`),
       weekStat('🔥 kcal', weekKcal > 0 ? `~${fmtKcal(weekKcal)}` : '–'))),

    next ? h('div', { class: 'card primary-card', onclick: () => go({ workoutId: next.id }) },
      h('div', { class: 'grow' },
        h('small', null, pick?.title ?? tr('Weiter mit', 'Continue with')),
        h('h2', null, next.name),
        h('small', null, plural(next.exercises.length, tr('Übung', 'exercise'), tr('Übungen', 'exercises')) +
          (store.sessionsFor(next.id)[0] ? tr(' · zuletzt ', ' · last ') + fmtDate(store.sessionsFor(next.id)[0].start) : '')),
        // Vorfreude statt Pflicht: Vorschlag fürs Training (morning.js); „heute“ nur, wenn es heute dran ist
        (a => (a ? h('small', { class: 'anticipation' }, '💡 ' + a) : null))(anticipation(next, !pick || dueToday(store)?.id === next.id))),
      next.exercises.length ? h('button', {
        class: 'play-btn', 'aria-label': tr('Training starten', 'Start workout'),
        onclick: e => { e.stopPropagation(); onStartWorkout(next.id); },
      }, icon('play', 28)) : null)
      : h('div', { class: 'card pad-m' },
        h('h2', null, tr('Willkommen! 👋', 'Welcome! 👋')),
        h('p', null, tr("So geht's: 1. Workout aus einer Vorlage anlegen · 2. Training starten · 3. Sätze eintragen.", 'How it works: 1. Create a workout from a template · 2. Start training · 3. Log your sets.')),
        h('button', { class: 'btn', onclick: newWorkoutDialog }, tr('Vorlage wählen', 'Choose a template'))),

    // Level (levels.js) mit Abzeichen-Zeile – unter dem Schnellstart, damit der ohne Scrollen sichtbar ist (wie DashboardTab.kt)
    levelCard(),

    // Langfristige Ziele mit den nächsten Meilensteinen (goals.js, wie GoalsCard in Goals.kt)
    goalsCard(),

    backupDue ? h('div', { class: 'card hint' }, h('span', { class: 'hint-emoji' }, '💾'),
      h('div', { class: 'grow' }, h('b', null, tr('Backup machen', 'Make a backup')),
        h('small', null, tr('Deine Daten liegen nur auf diesem Gerät. Sichere sie regelmäßig über ⋮ → „Daten exportieren“.',
          'Your data only lives on this device. Back it up regularly via ⋮ → "Export data".'))),
      h('button', { class: 'btn text', onclick: exportData }, tr('Jetzt', 'Now'))) : null,

    // Rückblick: letzte Woche bzw. letzter Monat im Vergleich (review.js)
    store.sets.length ? reviewCard() : null,

    // Heatmap, darunter aufklappbar „Sätze pro Muskelgruppe“ (bodymap.js)
    store.sets.length ? muscleHeatCard(store.sets, nav.statPeriod, p => update({ statPeriod: p })) : null,

    // Verlauf: Volumen bzw. kcal pro Woche in einer Karte mit Umschalter (wie TrendCard)
    volumes.some(v => v[1] > 0) || kcalWeeks ? trendCard(volumes, kcalWeeks) : null,

    // Neueste Rekorde kompakt (wie RecordsCard)
    records.length ? recordsCard(records) : null);
}

/** Kleine Kennzahl in der Wochenziel-Karte (wie WeekStat). */
const weekStat = (label, value) => h('div', null, h('b', null, value), h('small', { class: 'muted' }, label));

/** Karte „Verlauf pro Woche“ (wie TrendCard in DashboardTab.kt); Umschalter nur, wenn es kcal gibt. */
let showTrendKcal = false;
function trendCard(volumes, kcalWeeks) {
  const hasVolume = volumes.some(v => v[1] > 0);
  const kcal = (showTrendKcal && kcalWeeks) || !hasVolume;
  return chartCard(tr('Verlauf pro Woche', 'Weekly trend'),
    kcal ? tr('Letzte 8 Wochen · geschätzt', 'Last 8 weeks · estimated')
      : tr('Letzte 8 Wochen · aktuelle Woche hervorgehoben', 'Last 8 weeks · current week highlighted'),
    kcalWeeks && hasVolume ? h('div', { class: 'segments trend-switch' },
      chip(tr('Volumen', 'Volume'), !kcal, () => { showTrendKcal = false; rerender(); }),
      chip('🔥 kcal', kcal, () => { showTrendKcal = true; rerender(); })) : null,
    kcal ? historyBars(kcalWeeks.map(v => v[1]), kcalWeeks.map(v => fmtWeek(v[0])), v => `~${fmtKcal(v)} kcal`)
      : historyBars(volumes.map(v => v[1]), volumes.map(v => fmtWeek(v[0])), v => `${fmtVol(v)}`));
}

/** Karte „Neueste Rekorde“: 3 Zeilen, „Mehr anzeigen“ klappt bis 10 auf (wie RecordsCard). */
let showAllRecords = false;
function recordsCard(records) {
  return h('div', { class: 'card records-card' },
    h('h3', null, tr('🏆 Neueste Rekorde', '🏆 Latest records')),
    (showAllRecords ? records : records.slice(0, 3)).map(r => {
      const g = store.groupOf(r.exercise);
      return h('div', { class: 'record-row' },
        h('span', { html: exerciseIconHtml(r.exercise, g, 32) }),
        h('div', { class: 'grow' }, h('b', null, exName(r.exercise)), h('small', { class: 'muted' }, fmtDate(r.set.time))),
        h('b', null, fmtSet(r.set, isCardio(g))));
    }),
    records.length > 3 ? h('button', { class: 'btn text show-all', onclick: () => { showAllRecords = !showAllRecords; rerender(); } },
      showAllRecords ? tr('Weniger anzeigen', 'Show less') : tr(`Mehr anzeigen (${records.length})`, `Show more (${records.length})`)) : null);
}

function weeklyGoalDialog() {
  openDialog(() => {
    const goal = store.weeklyGoal;
    return dialog(tr('Wochenziel', 'Weekly goal'), [
      h('div', { class: 'stepper big' },
        h('button', { class: 'step', disabled: goal <= 1, onclick: () => store.changeWeeklyGoal(goal - 1) }, '−'),
        h('b', { class: 'step-value' }, tr(`${goal} × pro Woche`, `${goal} × per week`)),
        h('button', { class: 'step', disabled: goal >= 14, onclick: () => store.changeWeeklyGoal(goal + 1) }, '+')),
      muted(tr('🔥 Serie = Wochen in Folge mit mindestens einem Training. 🃏 Ein Joker pro Monat überbrückt eine Woche Pause (Urlaub, krank) – die Serie bleibt.',
        '🔥 Streak = weeks in a row with at least one workout. 🃏 One joker per month bridges a week off (holiday, sick) – the streak stays.'), 'small')],
      [{ label: 'OK', onClick: closeDialog }], closeDialog);
  });
}

// ---------- Tab 1: Übungen ----------

const exerciseGroups = () => [...new Set(store.exercises.map(e => store.groupOf(e)))].sort((a, b) => GROUP_KEYS.indexOf(a) - GROUP_KEYS.indexOf(b));
function exerciseEquipment() {
  const order = ['LANGHANTEL', 'KURZHANTEL', 'MASCHINE', 'KABEL', 'KETTLEBELL', 'BAND', 'KOERPER', 'CARDIO_GERAET'];
  return order.filter(e => store.exercises.some(n => equipmentOf(n) === e));
}

function exerciseGrid() {
  if (!store.exercises.length) {
    return h('div', { class: 'pad stack' },
      h('p', null, tr('Noch keine Übungen. Wähle welche aus dem Katalog aus.', 'No exercises yet. Pick some from the catalog.')),
      h('div', null, h('button', { class: 'btn', onclick: () => go({ catalog: true }) }, tr('Übungskatalog öffnen', 'Open exercise catalog'))));
  }
  const visible = store.exercises.filter(n => matches(nav.listFilter, n, store.groupOf(n)));
  if (!visible.length) return noFilterResults('list');
  return h('div', { class: 'pad list-bottom' },
    // Hinweis auf die sonst unsichtbare Geste – nur bis zum ersten Gedrückthalten (Hints)
    showHint('list') ? muted(tr('Tippen = öffnen · gedrückt halten = weitere Aktionen', 'Tap = open · long-press = more actions'), 'small') : null,
    GROUP_KEYS.map(g => {
    const names = sortByName(visible.filter(n => store.groupOf(n) === g), exName); // alphabetisch
    if (!names.length) return null;
    return [groupHeader(g), h('div', { class: 'tile-grid' },
      names.map(n => exerciseTile(n, g, () => go({ exercise: n }), { favorite: isFav(n), onLongPress: () => { hintUsed('list'); exerciseMenu(n); } })))];
  }));
}

/**
 * Menü einer Übungskachel (gedrückt halten): Öffnen, zu einem Workout hinzufügen (✓ = schon drin, antippen = an/aus),
 * aus „Meine Übungen“ entfernen (wie ExerciseMenuSheet in HomeScreen.kt).
 */
let menuPickWorkout = false;
function exerciseMenu(name) {
  menuPickWorkout = false;
  const g = store.groupOf(name);
  const action = (ic, label, onClick, cls = '') => h('button', { class: `menu-action ${cls}`, onclick: onClick }, icon(ic), h('span', null, label));
  openDialog(() => h('div', { class: 'dialog-backdrop sheet-backdrop', onclick: e => { if (e.target === e.currentTarget) closeDialog(); } },
    h('div', { class: 'sheet', role: 'dialog' },
      h('div', { class: 'sheet-handle', onclick: closeDialog }),
      h('div', { class: 'sheet-body' },
        h('div', { class: 'menu-head' }, h('span', { html: exerciseIconHtml(name, g, 40) }), h('b', null, exName(name))),
        !menuPickWorkout ? [
          action('chevron', tr('Öffnen', 'Open'), () => { closeDialog(); go({ exercise: name }); }),
          action('add', tr('Zu Workout hinzufügen', 'Add to workout'), () => { menuPickWorkout = true; rerender(); }),
          store.isFavorite(name)
            ? action('starFilled', tr('Aus Favoriten entfernen', 'Remove from favorites'), () => { closeDialog(); store.toggleFavorite(name); })
            : action('star', tr('Als Favorit markieren', 'Mark as favorite'), () => { closeDialog(); store.toggleFavorite(name); }),
          action('delete', tr('Aus der Liste entfernen', 'Remove from list'), () => { closeDialog(); removeExercise(name); }, 'danger'),
          muted(tr('Workouts und Verlauf bleiben erhalten.', 'Workouts and history are kept.'), 'small menu-note'),
        ] : !store.workouts.length
          ? muted(tr('Noch keine Workouts – leg eins im Tab „Workouts“ an.', 'No workouts yet – create one in the "Workouts" tab.'))
          : [
            h('h3', null, tr('Zu welchem Workout?', 'Which workout?')),
            store.workouts.map(w => {
              const inside = w.exercises.includes(name);
              return h('button', {
                class: 'menu-action', onclick: () => {
                  store.toggleInWorkout(w.id, name);
                  // wie WorkoutExercisePage: Standard-Ziel 3 Sätze (Cardio 1×)
                  if (!inside) store.setTarget(w.id, name, isCardio(g) ? 1 : 3);
                },
              }, h('span', { class: 'grow' }, w.name), inside ? h('b', { class: 'primary-text' }, '✓ ' + tr('drin', 'added')) : icon('add'));
            }),
          ]))));
}

/** Aus „Meine Übungen“ entfernen, mit „Rückgängig“ (wie ExerciseGrid in HomeScreen.kt) */
function removeExercise(name) {
  const index = store.removeFromList(name);
  undoToast(tr(`„${exName(name)}“ entfernt – bleibt in Workouts und Verlauf`, `"${exName(name)}" removed – stays in workouts and history`),
    () => store.restoreToList(name, index));
}

// ---------- Tab 3: Kalender ----------

let calInfo = false; // Erklärung zur Legende aufgeklappt

function calendarTab() {
  const month = nav.calMonth;
  const year = Math.floor(month / 12), m = month % 12;
  const offset = (new Date(year, m, 1).getDay() + 6) % 7; // Montag = 0
  const daysInMonth = new Date(year, m + 1, 0).getDate();
  const todayKey = dayKey(Date.now());
  const sessions = allSessions(store);
  const stats = new Map(sessions.map(t => [t.id, sessionStats(store, t)]));
  const byDay = new Map();
  for (const t of sessions) {
    const k = dayKey(t.start);
    if (!byDay.has(k)) byDay.set(k, []);
    byDay.get(k).push(t);
  }

  // Trainingsplan: Wochentage mit geplantem Workout → Punkt; verpasst nur in der laufenden Woche (wie CalendarTab.kt)
  const planned = new Set(store.workouts.filter(w => w.exercises.length).flatMap(w => w.days ?? []));
  const weekStartKey = dayKey(startOfWeek());
  const planMark = (key, day) => {
    if (!planned.has(((offset + day - 1) % 7) + 1)) return null;
    return key >= todayKey ? 'planned' : key >= weekStartKey ? 'missed' : null;
  };
  const cells = [];
  const rows = Math.ceil((offset + daysInMonth) / 7);
  for (let i = 0; i < rows * 7; i++) {
    const day = i - offset + 1;
    if (day < 1 || day > daysInMonth) { cells.push(h('span')); continue; }
    const key = year * 10000 + (m + 1) * 100 + day;
    const list = byDay.get(key);
    const best = list ? Math.max(...list.map(t => stats.get(t.id).completion)) : null;
    cells.push(h('button', {
      class: ['day', key === nav.calDay && 'selected', key === todayKey && 'today', best != null && 'has'],
      onclick: () => update({ calDay: key }),
    }, best != null ? ring(best, 40, { stroke: 0.1 }) : null, h('span', null, String(day)),
    best == null && planMark(key, day) ? h('i', { class: ['plan-dot', planMark(key, day)] }) : null));
  }

  const monthSessions = sessions.filter(t => Math.floor(dayKey(t.start) / 100) === year * 100 + m + 1);
  let monthText;
  if (!monthSessions.length) monthText = tr('Keine Trainingseinheiten in diesem Monat', 'No workouts this month');
  else {
    const avg = Math.round((monthSessions.reduce((a, t) => a + stats.get(t.id).completion, 0) / monthSessions.length) * 100);
    const days = new Set(monthSessions.map(t => dayKey(t.start))).size;
    const n = monthSessions.length;
    const kcal = monthSessions.reduce((a, t) => a + (stats.get(t.id).kcal ?? 0), 0);
    monthText = tr(`${plural(n, 'Trainingseinheit', 'Trainingseinheiten')} an ${plural(days, 'Tag', 'Tagen')} · Ø ${avg} % abgeschlossen`,
      `${plural(n, 'workout', 'workouts')} on ${plural(days, 'day', 'days')} · avg. ${avg} % completed`) + (kcal > 0 ? ` · 🔥 ~${fmtKcal(kcal)} kcal` : '');
  }
  const daySessions = (byDay.get(nav.calDay) ?? []).slice().sort((a, b) => a.start - b.start);

  return h('div', { class: 'stack pad' },
    h('div', { class: 'month-head' },
      h('button', { class: 'icon-btn', onclick: () => update({ calMonth: month - 1 }) }, icon('chevronLeft')),
      h('h2', null, monthName(year, m)),
      h('button', { class: 'icon-btn', onclick: () => update({ calMonth: month + 1 }) }, icon('chevron'))),
    h('div', { class: 'cal-grid weekdays' }, (isEnglish() ? ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] : ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']).map(d => h('small', null, d))),
    h('div', { class: 'cal-grid' }, cells),
    h('div', { class: 'card pad-s' }, monthText),
    h('h2', { class: 'section' }, weekdayDate(nav.calDay)),
    daySessions.length ? null : muted(tr('Kein Training an diesem Tag.', 'No workout on this day.')),
    daySessions.map(t => {
      const s = stats.get(t.id);
      return h('button', { class: 'card row-card', onclick: () => go({ summaryId: t.id }) },
        percentRing(s.completion, 48),
        h('div', { class: 'grow' }, h('b', null, t.workoutName),
          h('small', { class: 'muted' }, (t.id < 0 ? plural(s.results.length, tr('Übung', 'exercise'), tr('Übungen', 'exercises')) + ' · '
            : s.total === 0 ? fmtDuration(t.end - t.start) + ' · '
              : `${s.done}/${s.total} ` + tr('Ziel-Sätze', 'target sets') + ` · ${fmtDuration(t.end - t.start)} · `) +
            plural(s.sets, tr('Satz', 'set'), tr('Sätze', 'sets')) + (s.kcal != null ? ` · 🔥 ${fmtKcal(s.kcal)} kcal` : ''))),
        icon('chevron'));
    }),
    h('div', { class: 'legend' }, [[1, '100 %'], [0.5, '≥ 50 %'], [0.25, '< 50 %']].map(([p, l]) => [ring(p, 16), h('small', null, l)]),
      planned.size ? [h('i', { class: 'plan-dot planned' }), h('small', null, tr('geplant', 'planned')), h('i', { class: 'plan-dot missed' }), h('small', null, tr('verpasst', 'missed'))] : null,
      iconBtn('info', () => { calInfo = !calInfo; rerender(); }, { label: tr('Erklärung', 'Explanation'), cls: 'info-btn' })),
    calInfo ? muted(tr('Prozent = erledigte Ziel-Sätze. Sätze ohne gestartetes Workout zählen als freies Training mit 100 %. Punkte = Trainingstage laut Plan (verpasst nur in der laufenden Woche).',
      'Percent = target sets done. Sets logged without starting a workout count as a free workout with 100 %. Dots = planned training days (missed only in the current week).'), 'small') : null);
}

// ---------- Tab 4: Körper ----------

// Eingabe leer lassen (letzter Wert nur in der Beschriftung → kein versehentlicher Doppel-Eintrag); date = Nachtragen
const bodyInput = { value: '', date: '' };

function bodyTab() {
  const weights = store.weights;
  const last = weights[weights.length - 1];
  const first = weights[0];
  const shown = weights.slice(-30);

  const add = () => {
    const kg = parseW(bodyInput.value);
    // Plausibilitätsprüfung gegen Tippfehler (z. B. 824 statt 82,4)
    if (kg != null && kg >= 20 && kg <= 400) {
      // gewählter Tag 12:00 Ortszeit (Tag stimmt in jeder Zeitzone), sonst jetzt
      const t = bodyInput.date && bodyInput.date !== isoDay(Date.now()) ? timeOfIsoDay(bodyInput.date) : Date.now();
      store.addWeight(kg, t);
      bodyInput.value = ''; bodyInput.date = '';
      document.activeElement?.blur();
    }
    else toast(isLb() ? tr('Bitte ein Gewicht zwischen 45 und 880 lbs eingeben.', 'Please enter a weight between 45 and 880 lbs.') : tr('Bitte ein Gewicht zwischen 20 und 400 kg eingeben.', 'Please enter a weight between 20 and 400 kg.'));
  };

  return h('div', { class: 'stack pad' },
    h('div', { class: 'card pad-m' },
      h('small', { class: 'muted' }, tr('Aktuelles Gewicht', 'Current weight')),
      h('div', { class: 'display' }, last ? `${fmtKg(last.kg)}` : '–'),
      first && last && weights.length >= 2
        ? muted(`${last.kg - first.kg >= 0 ? '+' : '−'}${fmtKg(Math.abs(last.kg - first.kg))} ` + tr('seit ', 'since ') + fmtDate(first.time)) : null),
    h('div', { class: 'row' },
      textField({ id: 'body-kg', label: tr(`Gewicht in ${wUnit()}`, `Weight in ${wUnit()}`) + (last ? tr(' · zuletzt ', ' · last ') + fmtWU(last.kg) : ''), value: bodyInput.value, inputmode: 'decimal', oninput: v => { bodyInput.value = v; }, onenter: add }),
      h('button', { class: 'btn', onclick: add }, tr('Eintragen', 'Log'))),
    // Datum (leer = heute), im App-Format; Zukunft gesperrt
    h('label', { class: 'date-row' }, icon('calendar', 18), h('span', null, tr('Datum', 'Date')),
      dateField({ id: 'body-date', value: bodyInput.date || isoDay(Date.now()), max: isoDay(Date.now()), onchange: v => { bodyInput.date = v; } })),
    // Wozu das Gewicht dient (kcal-Schätzung). Der Wochen-Verbrauch steht auf der Startseite, die MET-Erklärung hinter ⓘ (wie BodyTab.kt)
    h('div', { class: 'kcal-note' }, h('span', null, '🔥'),
      muted(last == null
        ? tr('Trag dein Gewicht ein, dann schätzt die App deinen Kalorienverbrauch.', 'Log your weight and the app will estimate calories burned.')
        : tr('Dein Gewicht fließt in die kcal-Schätzung ein.', 'Your weight is used for the calorie estimate.'), 'grow'),
      iconBtn('info', metDialog, { label: tr('Erklärung', 'Explanation'), cls: 'info-btn accent' })),
    // Gewichtsverlauf; Punkt antippen = Eintrag anpassen oder löschen (statt einer Liste) – wie BodyTab.kt
    weights.length ? chartCard(tr('Gewichtsverlauf', 'Weight trend'),
      `${fmtWU(Math.min(...shown.map(w => w.kg)))} – ${fmtKg(Math.max(...shown.map(w => w.kg)))}`,
      lineChart(shown.map(w => toUnit(w.kg)), wUnit(), i => { hintUsed('weight'); weightEditDialog(shown[i]); }),
      h('div', { class: 'axis' }, h('small', null, fmtShortDate(shown[0].time)), shown.length > 1 ? h('small', null, fmtShortDate(shown[shown.length - 1].time)) : null),
      showHint('weight') ? muted(tr('Punkt antippen zum Anpassen oder Löschen', 'Tap a point to edit or delete'), 'small') : null) : null,
    // Körpermaße + Körperfett (measures.js)
    measuresCard());
}

/** Wie wird der Verbrauch geschätzt? (ⓘ im Körper-Tab) */
function metDialog() {
  openDialog(() => dialog(tr('Wie wird der Verbrauch geschätzt?', 'How are calories estimated?'),
    h('p', null, tr('Nach der MET-Methode: Körpergewicht × Dauer × Intensität der Übung. Cardio wird über die eingetragenen Minuten gerechnet, Krafttraining über die Trainingsdauer. Mit vollständigem Profil (Einstellungen: Geschlecht, Geburtsjahr, Größe) nimmt die App statt des Körpergewichts deinen Grundumsatz – das ist genauer. Es bleibt eine Schätzung.',
      'With the MET method: body weight × duration × exercise intensity. Cardio uses the logged minutes, strength training the workout duration. With a complete profile (settings: sex, birth year, height) the app uses your basal metabolic rate instead of body weight, which is more accurate. It is still an estimate.')),
    [{ label: 'OK', onClick: closeDialog }], closeDialog));
}


/** Gewichtseintrag aus dem Diagramm: Gewicht und Datum anpassen oder löschen (mit „Rückgängig“) – wie WeightEditDialog in BodyTab.kt */
function weightEditDialog(entry) {
  const st = { text: fmtWU(entry.kg), day: isoDay(entry.time) };
  const save = kg => { store.updateWeight(entry, kg, st.day === isoDay(entry.time) ? entry.time : timeOfIsoDay(st.day)); closeDialog(); };
  const valid = () => { const kg = parseW(st.text); return kg != null && kg >= 20 && kg <= 400 ? kg : null; };
  openDialog(() => dialog(tr('Eintrag bearbeiten', 'Edit entry'), [
    textField({
      id: 'weight-edit', label: tr(`Gewicht in ${wUnit()}`, `Weight in ${wUnit()}`), value: st.text, inputmode: 'decimal',
      error: valid() == null ? ' ' : null,
      oninput: v => { st.text = v; rerender(); },
      onenter: () => { const kg = valid(); if (kg != null) save(kg); },
    }),
    h('label', { class: 'date-row' }, icon('calendar', 18),
      dateField({ id: 'weight-edit-date', value: st.day, max: isoDay(Date.now()), onchange: v => { st.day = v; } })),
  ], [
    { label: tr('Löschen', 'Delete'), danger: true, onClick: () => {
      closeDialog();
      store.deleteWeight(entry);
      undoToast(tr('Eintrag gelöscht', 'Entry deleted'), () => store.restoreWeight(entry));
    } },
    { label: tr('Speichern', 'Save'), disabled: valid() == null, onClick: () => { const kg = valid(); if (kg != null) save(kg); } },
  ], closeDialog));
}



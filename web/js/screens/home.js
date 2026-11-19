// Startseite mit 5 Tabs – entspricht HomeScreen.kt, DashboardTab.kt, CalendarTab.kt, BodyTab.kt.

import { store } from '../store.js';
import {
  tr, exName, fmtSet, fmtW, fmtDate, fmtDuration, fmtShortDate, plural, isCardio, isEnglish, groupLabel,
  GROUP_KEYS, startOfWeek, dayKey, monthName, weekdayDate, parseNum, appLanguage, equipmentOf,
} from '../core.js';
import { allSessions, sessionStats, recordEvents, weekStreak, weeklyVolumes } from '../stats.js';
import { exerciseIconHtml } from '../pictograms.js';
import {
  h, icon, iconBtn, chip, dialog, textField, statBox, chartCard, horizontalBars, historyBars, lineChart,
  percentRing, ring, muted, toast, groupHeader,
} from '../ui.js';
import { nav, go, update, rerender, openDialog, closeDialog, onSwipe } from '../nav.js';
import { screen, fab } from '../layout.js';
import { exerciseTile } from './exercise.js';
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
  const tab = nav.tab;
  const setTab = i => { if (i >= 0 && i < 5) update({ tab: i, menuOpen: false }); };
  const content = [dashboardTab, exerciseGrid, workoutList, calendarTab, bodyTab][tab](onStartWorkout);
  const wrap = h('div', { class: 'tab-page' }, content);
  onSwipe(wrap, () => setTab(tab + 1), () => setTab(tab - 1));

  return screen({
    title: TABS()[tab][2], subtitle: 'GymTracker', large: true, key: 'tab-' + tab,
    actions: [
      tab === 1 && store.exercises.length ? filterActions('list') : null,
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

// ---------- ⋮-Menü: Einstellungen, Export, Import ----------

function menu() {
  const close = () => update({ menuOpen: false });
  return [
    h('div', { class: 'menu-backdrop', onclick: close }),
    h('div', { class: 'menu' },
      h('button', { onclick: () => { close(); settingsDialog(); } }, icon('settings', 20), tr('Einstellungen', 'Settings')),
      h('hr'),
      h('button', { onclick: () => { close(); exportData(); } }, icon('download', 20), tr('Daten exportieren', 'Export data')),
      h('button', { onclick: () => { close(); importData(); } }, icon('upload', 20), tr('Daten importieren', 'Import data')),
      h('small', { class: 'muted' }, store.lastExport === 0 ? tr('Noch nie exportiert', 'Never exported')
        : tr('Letzter Export: ', 'Last export: ') + fmtDate(store.lastExport))),
  ];
}

/** Export: auf dem iPhone über „Teilen“ (→ „In Dateien sichern“), sonst als Download. */
async function exportData() {
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

function importData() {
  const input = h('input', { type: 'file', style: 'display:none' });
  input.addEventListener('change', async () => {
    const f = input.files?.[0];
    input.remove();
    if (!f) return;
    const text = await f.text();
    const info = store.inspectBackup(text);
    if (!info) { toast(tr('Das ist keine gültige GymTracker-Sicherung.', 'This is not a valid GymTracker backup.')); return; }
    openDialog(() => dialog(tr('Sicherung importieren?', 'Import backup?'), [
      h('p', null, tr('Die Sicherung enthält:', 'The backup contains:')),
      h('ul', null,
        h('li', null, plural(info.exercises, tr('Übung', 'exercise'), tr('Übungen', 'exercises'))),
        h('li', null, plural(info.sets, tr('Satz', 'set'), tr('Sätze', 'sets'))),
        h('li', null, plural(info.workouts, 'Workout', 'Workouts')),
        h('li', null, plural(info.sessions, tr('Training', 'session'), tr('Trainings', 'sessions')))),
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

function settingsDialog() {
  openDialog(() => dialog(tr('Einstellungen', 'Settings'), [
    h('b', { class: 'small-title' }, tr('Design', 'Theme')),
    h('div', { class: 'chips' }, [['system', 'System'], ['light', tr('☀ Hell', '☀ Light')], ['dark', tr('☾ Dunkel', '☾ Dark')]]
      .map(([m, l]) => chip(l, store.themeMode === m, () => store.setTheme(m)))),
    h('b', { class: 'small-title' }, tr('Sprache', 'Language')),
    h('div', { class: 'chips' }, [['de', 'Deutsch'], ['en', 'English']].map(([l, label]) => chip(label, appLanguage === l, () => store.setLanguage(l)))),
  ], [{ label: 'OK', onClick: closeDialog }], closeDialog));
}

// ---------- Tab 0: Übersicht ----------

const isIos = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const isStandalone = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

function dashboardTab(onStartWorkout) {
  const sessions = allSessions(store);
  const weekSessions = sessions.filter(t => t.start >= startOfWeek());
  const weekStats = weekSessions.map(t => sessionStats(store, t));
  const streak = weekStreak(store);
  const goal = store.weeklyGoal;
  const lastSession = store.sessions.reduce((a, t) => (!a || t.start > a.start ? t : a), null);
  const next = (lastSession && store.workout(lastSession.workoutId)) || store.workouts[0];

  const volumes = weeklyVolumes(store, 8);
  const since = Date.now() - 30 * 24 * 3600 * 1000;
  const perGroup = new Map();
  for (const s of store.sets) if (s.time >= since) {
    const g = store.groupOf(s.exercise);
    perGroup.set(g, (perGroup.get(g) ?? 0) + 1);
  }
  const groupItems = [...perGroup.entries()].map(([g, n]) => [groupLabel(g), n]).sort((a, b) => b[1] - a[1]);
  const records = recordEvents(store).slice(0, 5);
  const weekKcal = weekStats.reduce((a, s) => a + (s.kcal ?? 0), 0);
  const backupDue = store.sets.length > 0 && Date.now() - store.lastExport > 14 * 24 * 3600 * 1000;

  return h('div', { class: 'stack pad' },
    // Hinweis zum Installieren (nur iPhone/iPad im Safari-Browser)
    isIos() && !isStandalone() ? h('div', { class: 'card hint' }, h('span', { class: 'hint-emoji' }, '📲'),
      h('div', { class: 'grow' }, h('b', null, tr('Als App installieren', 'Install as app')),
        h('small', null, tr('In Safari unten auf „Teilen“ tippen, dann „Zum Home-Bildschirm“. Danach startet GymTracker wie eine normale App.',
          'In Safari tap "Share", then "Add to Home Screen". GymTracker then opens like a regular app.')))) : null,

    h('button', { class: 'card week-card', onclick: weeklyGoalDialog },
      percentRing(Math.min(1, weekSessions.length / goal), 84),
      h('div', { class: 'grow' },
        h('small', { class: 'muted' }, tr('Diese Woche', 'This week')),
        h('h2', null, tr(`${weekSessions.length} von ${goal} Trainings`, `${weekSessions.length} of ${goal} workouts`)),
        h('span', null, streak > 0 ? '🔥 ' + tr(`${plural(streak, 'Woche', 'Wochen')} in Folge`, `${plural(streak, 'week', 'weeks')} in a row`)
          : tr('Starte deine Serie!', 'Start your streak!')),
        h('small', { class: 'muted' }, tr('Tippen, um das Ziel zu ändern', 'Tap to change goal')))),

    next ? h('div', { class: 'card primary-card', onclick: () => go({ workoutId: next.id }) },
      h('div', { class: 'grow' },
        h('small', null, tr('Weiter mit', 'Continue with')),
        h('h2', null, next.name),
        h('small', null, plural(next.exercises.length, tr('Übung', 'exercise'), tr('Übungen', 'exercises')) +
          (store.sessionsFor(next.id)[0] ? tr(' · zuletzt ', ' · last ') + fmtDate(store.sessionsFor(next.id)[0].start) : ''))),
      next.exercises.length ? h('button', {
        class: 'play-btn', 'aria-label': tr('Training starten', 'Start workout'),
        onclick: e => { e.stopPropagation(); onStartWorkout(next.id); },
      }, icon('play', 28)) : null)
      : h('div', { class: 'card pad-m' },
        h('h2', null, tr('Willkommen! 👋', 'Welcome! 👋')),
        h('p', null, tr('Leg dein erstes Workout an – z. B. aus einer Vorlage.', 'Create your first workout – e.g. from a template.')),
        h('button', { class: 'btn', onclick: () => update({ tab: 2 }) }, tr('Zu den Workouts', 'Go to workouts'))),

    backupDue ? h('div', { class: 'card hint' }, h('span', { class: 'hint-emoji' }, '💾'),
      h('div', { class: 'grow' }, h('b', null, tr('Backup machen', 'Make a backup')),
        h('small', null, tr('Deine Daten liegen nur auf diesem Gerät. Sichere sie regelmäßig über ⋮ → „Daten exportieren“.',
          'Your data only lives on this device. Back it up regularly via ⋮ → "Export data".'))),
      h('button', { class: 'btn text', onclick: exportData }, tr('Jetzt', 'Now'))) : null,

    h('div', { class: 'stat-grid three' },
      statBox(tr('Sätze', 'Sets'), String(weekStats.reduce((a, s) => a + s.sets, 0))),
      statBox(tr('Volumen', 'Volume'), `${fmtW(weekStats.reduce((a, s) => a + s.volume, 0))} kg`),
      statBox('🔥 kcal', weekKcal > 0 ? `~${weekKcal}` : '–')),

    volumes.some(v => v[1] > 0) ? chartCard(tr('Volumen pro Woche', 'Weekly volume'),
      tr('Letzte 8 Wochen · aktuelle Woche hervorgehoben', 'Last 8 weeks · current week highlighted'),
      historyBars(volumes.map(v => v[1]), volumes.map(v => fmtShortDate(v[0])), v => `${fmtW(v)} kg`)) : null,

    groupItems.length ? chartCard(tr('Sätze pro Muskelgruppe', 'Sets per muscle group'), tr('Letzte 30 Tage', 'Last 30 days'),
      horizontalBars(groupItems, v => String(v))) : null,

    records.length ? [h('h2', { class: 'section' }, tr('Neueste Rekorde', 'Latest records')),
      records.map(r => {
        const g = store.groupOf(r.exercise);
        return h('div', { class: 'card row-card' },
          h('span', { html: exerciseIconHtml(r.exercise, g, 36) }),
          h('div', { class: 'grow' }, h('b', null, exName(r.exercise)), muted(fmtDate(r.set.time), 'small')),
          h('b', null, '🏆 ' + fmtSet(r.set, isCardio(g))));
      })] : null);
}

function weeklyGoalDialog() {
  openDialog(() => {
    const goal = store.weeklyGoal;
    return dialog(tr('Wochenziel', 'Weekly goal'),
      h('div', { class: 'stepper big' },
        h('button', { class: 'step', disabled: goal <= 1, onclick: () => store.changeWeeklyGoal(goal - 1) }, '−'),
        h('b', { class: 'step-value' }, tr(`${goal} × pro Woche`, `${goal} × per week`)),
        h('button', { class: 'step', disabled: goal >= 14, onclick: () => store.changeWeeklyGoal(goal + 1) }, '+')),
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
  return h('div', { class: 'pad list-bottom' }, GROUP_KEYS.map(g => {
    const names = visible.filter(n => store.groupOf(n) === g);
    if (!names.length) return null;
    return [groupHeader(g), h('div', { class: 'tile-grid' }, names.map(n => exerciseTile(n, g, () => go({ exercise: n }))))];
  }));
}

// ---------- Tab 3: Kalender ----------

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
    }, best != null ? ring(best, 40, { stroke: 0.1 }) : null, h('span', null, String(day))));
  }

  const monthSessions = sessions.filter(t => Math.floor(dayKey(t.start) / 100) === year * 100 + m + 1);
  let monthText;
  if (!monthSessions.length) monthText = tr('Keine Trainings in diesem Monat', 'No workouts this month');
  else {
    const avg = Math.round((monthSessions.reduce((a, t) => a + stats.get(t.id).completion, 0) / monthSessions.length) * 100);
    const days = new Set(monthSessions.map(t => dayKey(t.start))).size;
    const n = monthSessions.length;
    const kcal = monthSessions.reduce((a, t) => a + (stats.get(t.id).kcal ?? 0), 0);
    monthText = tr(`${plural(n, 'Training', 'Trainings')} an ${plural(days, 'Tag', 'Tagen')} · Ø ${avg} % abgeschlossen`,
      `${plural(n, 'workout', 'workouts')} on ${plural(days, 'day', 'days')} · avg. ${avg} % completed`) + (kcal > 0 ? ` · 🔥 ~${kcal} kcal` : '');
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
            plural(s.sets, tr('Satz', 'set'), tr('Sätze', 'sets')) + (s.kcal != null ? ` · 🔥 ${s.kcal} kcal` : ''))),
        icon('chevron'));
    }),
    h('div', { class: 'legend' }, [[1, '100 %'], [0.5, '≥ 50 %'], [0.25, '< 50 %']].map(([p, l]) => [ring(p, 16), h('small', null, l)])),
    muted(tr('Prozent = erledigte Ziel-Sätze. Sätze ohne gestartetes Workout zählen als freies Training mit 100 %.',
      'Percent = target sets done. Sets logged without starting a workout count as a free workout with 100 %.'), 'small'));
}

// ---------- Tab 4: Körper ----------

const bodyInput = { value: null };

function bodyTab() {
  const weights = store.weights;
  const last = weights[weights.length - 1];
  const first = weights[0];
  if (bodyInput.value == null) bodyInput.value = last ? fmtW(last.kg) : '';
  const weekSessions = allSessions(store).filter(t => t.start >= startOfWeek());
  const weekKcal = weekSessions.reduce((a, t) => a + (sessionStats(store, t).kcal ?? 0), 0);
  const shown = weights.slice(-30);

  const add = () => {
    const kg = parseNum(bodyInput.value);
    // Plausibilitätsprüfung gegen Tippfehler (z. B. 824 statt 82,4)
    if (kg != null && kg >= 20 && kg <= 400) { store.addWeight(kg); document.activeElement?.blur(); }
    else toast(tr('Bitte ein Gewicht zwischen 20 und 400 kg eingeben.', 'Please enter a weight between 20 and 400 kg.'));
  };

  return h('div', { class: 'stack pad' },
    h('div', { class: 'card pad-m' },
      h('small', { class: 'muted' }, tr('Aktuelles Gewicht', 'Current weight')),
      h('div', { class: 'display' }, last ? `${fmtW(last.kg)} kg` : '–'),
      first && last && weights.length >= 2
        ? muted(`${last.kg - first.kg >= 0 ? '+' : '−'}${fmtW(Math.abs(last.kg - first.kg))} kg ` + tr('seit ', 'since ') + fmtDate(first.time)) : null),
    h('div', { class: 'row' },
      textField({ id: 'body-kg', label: tr('Gewicht in kg', 'Weight in kg'), value: bodyInput.value, inputmode: 'decimal', oninput: v => { bodyInput.value = v; }, onenter: add }),
      h('button', { class: 'btn', onclick: add }, tr('Eintragen', 'Log'))),
    h('div', { class: 'card hint amber' }, h('span', { class: 'hint-emoji' }, '🔥'),
      h('div', { class: 'grow' }, h('small', null, tr('Diese Woche', 'This week')),
        h('b', null, last == null
          ? tr('Trag dein Gewicht ein, dann schätzt die App deinen Kalorienverbrauch.', 'Log your weight and the app will estimate calories burned.')
          : tr(`~${weekKcal} kcal verbrannt · ${plural(weekSessions.length, 'Training', 'Trainings')}`,
            `~${weekKcal} kcal burned · ${plural(weekSessions.length, 'workout', 'workouts')}`)))),
    weights.length >= 2 ? chartCard(tr('Gewichtsverlauf', 'Weight trend'),
      `${fmtW(Math.min(...shown.map(w => w.kg)))} – ${fmtW(Math.max(...shown.map(w => w.kg)))} kg`,
      lineChart(shown.map(w => w.kg), 'kg'),
      h('div', { class: 'axis' }, h('small', null, fmtShortDate(shown[0].time)), h('small', null, fmtShortDate(shown[shown.length - 1].time)))) : null,
    weights.length ? [h('h2', { class: 'section' }, tr('Einträge', 'Entries')),
      weights.slice().reverse().slice(0, 20).map(w => h('div', { class: 'set-row' },
        h('span', { class: 'grow' }, fmtDate(w.time)), h('b', null, `${fmtW(w.kg)} kg`),
        iconBtn('delete', () => store.deleteWeight(w), { label: tr('Löschen', 'Delete') })))] : null,
    muted(tr('Der Verbrauch ist eine Schätzung nach der MET-Methode (Körpergewicht × Dauer × Intensität der Übung). Cardio wird über die eingetragenen Minuten gerechnet, Krafttraining über die Trainingsdauer.',
      'Calories are estimated with the MET method (body weight × duration × exercise intensity). Cardio uses the logged minutes, strength training the workout duration.'), 'small'));
}

// Nach einem neuen Gewichtseintrag das Feld mit dem neuen Wert vorbelegen
store.subscribe(() => { const l = store.weights[store.weights.length - 1]; if (l && document.activeElement?.id !== 'body-kg') bodyInput.value = fmtW(l.kg); });

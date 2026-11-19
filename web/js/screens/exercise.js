// Übungs-Bausteine und Übungsdetail – entspricht ExerciseDetail.kt, Progression.kt (UI) und ExerciseTile.

import { wikiFor, wikiContent } from '../wiki.js';
import { musclesOf, muscleEditDialog, muscleSideCard } from '../bodymap.js';
import { store } from '../store.js';
import {
  tr, exName, fmtSet, fmtW, fmtDate, fmtDateTime, fmtTime, fmtSeconds, plural, isCardio, isTimed, equipmentOf,
  oneRepMax, parseNum, parseIntStrict, startOfToday, groupColor, catalogGroup, dayKey, monthIndex, startOfWeek,
  fmtShortDate, completionColor,
} from '../core.js';
import { progressionSuggestion } from '../stats.js';
import { exerciseIconHtml, exerciseImageUrl } from '../pictograms.js';
import { h, icon, iconBtn, chip, dialog, textField, progressChart, onTick, muted } from '../ui.js';
import { nav, rerender, openDialog, closeDialog, goBack } from '../nav.js';
import { screen } from '../layout.js';

// ---------- Kachel ----------

/** Kompakte Kachel: Symbol, Name (2 Zeilen), letzter Eintrag. */
export function exerciseTile(name, group, onClick, extra = {}) {
  const last = store.setsFor(name)[0];
  return h('button', {
    class: ['tile', extra.cls], onclick: onClick, 'data-name': name,
    style: `--tint:${groupColor(group)}`,
  },
  h('span', { html: exerciseIconHtml(name, group, 44) }),
  h('span', { class: 'tile-name', lang: tr('de', 'en') }, exName(name)),
  h('small', null, last ? fmtSet(last, isCardio(group)) : '–'));
}

// ---------- Vorschlag ----------

/** Karte mit Gewichts-Vorschlag und „Übernehmen“ (füllt das kg-Feld). */
export function progressionHint(name, before = startOfToday()) {
  const s = progressionSuggestion(store, name, before);
  if (!s) return null;
  return h('div', { class: 'card hint' },
    h('span', { class: 'hint-emoji' }, '💡'),
    h('div', { class: 'grow' }, h('b', null, s.title), h('small', null, s.reason)),
    s.weight != null ? h('button', {
      class: 'btn text', onclick: () => { inputState(name).weight = fmtW(s.weight); rerender(); },
    }, tr('Übernehmen', 'Apply')) : null);
}

// ---------- Eingabe eines Satzes ----------

/** Eingabewerte je Übung (bleiben erhalten, solange die App offen ist); vorbelegt mit dem letzten Satz. */
const inputs = new Map();
function inputState(name) {
  if (!inputs.has(name)) {
    const last = store.setsFor(name)[0];
    inputs.set(name, { weight: last ? fmtW(last.weight) : '', reps: last ? String(last.reps) : '', watchStart: null });
  }
  return inputs.get(name);
}

const mmss = sec => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;

/**
 * kg + Wdh. (bzw. Minuten bei Cardio, Sekunden bei Zeitübungen) mit Button.
 * onAdded: wird nach dem Eintragen aufgerufen (Trainingsmodus startet damit die Pause).
 */
export function setInput(name, onAdded = () => {}) {
  const cardio = isCardio(store.groupOf(name));
  const timed = isTimed(name);
  const st = inputState(name);
  const showWeight = !cardio && (!timed || equipmentOf(name) !== 'KOERPER');
  const idBase = 'in-' + name.replace(/\W+/g, '_');

  const add = () => {
    const w = cardio ? 0 : parseNum(st.weight) ?? (timed ? 0 : null);
    const r = parseIntStrict(st.reps);
    if (w == null || w < 0 || r == null || r <= 0) {
      document.getElementById(idBase + '-row')?.classList.add('shake');
      return;
    }
    st.watchStart = null;
    store.addSet(name, w, r);
    onAdded();
  };

  let watch = null;
  if (timed) {
    const running = st.watchStart != null;
    const shown = running ? Math.max(0, Math.floor((Date.now() - st.watchStart) / 1000)) : parseIntStrict(st.reps) ?? 0;
    const display = h('div', { class: 'stopwatch-time' }, mmss(shown));
    if (running) onTick(() => { display.textContent = mmss(Math.max(0, Math.floor((Date.now() - st.watchStart) / 1000))); });
    watch = h('div', { class: 'card stopwatch' },
      h('div', { class: 'grow' }, h('small', { class: 'muted' }, tr('Stoppuhr', 'Stopwatch')), display),
      h('button', {
        class: ['btn', running && 'danger-fill'],
        onclick: () => {
          if (running) {
            st.reps = String(Math.max(1, Math.floor((Date.now() - st.watchStart) / 1000)));
            st.watchStart = null;
          } else st.watchStart = Date.now();
          rerender();
        },
      }, running ? null : icon('play', 18), running ? tr('Stopp', 'Stop') : 'Start'));
  }

  return h('div', { class: 'set-input' }, watch,
    h('div', { class: 'row', id: idBase + '-row', onanimationend: e => e.currentTarget.classList.remove('shake') },
      showWeight ? textField({
        id: idBase + '-w', label: 'kg', value: st.weight, inputmode: 'decimal',
        oninput: v => { st.weight = v; }, onenter: add,
      }) : null,
      textField({
        id: idBase + '-r', label: cardio ? tr('Minuten', 'Minutes') : timed ? tr('Sekunden', 'Seconds') : tr('Wdh.', 'Reps'),
        value: st.reps, inputmode: 'numeric', oninput: v => { st.reps = v; }, onenter: add,
      }),
      h('button', { class: 'btn plus', onclick: add, 'aria-label': cardio ? tr('Eintragen', 'Log') : tr('Satz hinzufügen', 'Add set') }, '+')));
}

// ---------- Satz-Zeile + Bearbeiten ----------

/** Satz-Zeile; ganze Zeile antippen = bearbeiten (kein Stift-Symbol). */
export function setRow(set, number, cardio) {
  return h('div', { class: 'set-row tap', role: 'button', onclick: () => editSetDialog(set, cardio) },
    h('span', { class: 'grow' }, `${number}. ${fmtSet(set, cardio)}`),
    h('small', { class: 'muted' }, fmtTime(set.time)));
}

/** Motivierende Zeile mit dem Rekord (wie RecordLine in ExerciseDetail.kt). */
function recordLine(history, cardio, timed) {
  const today = dayKey(Date.now());
  const byTime = cardio || timed;
  const best = history.length ? history.reduce((a, b) =>
    (byTime ? b.reps > a.reps : b.weight > a.weight || (b.weight === a.weight && b.reps > a.reps)) ? b : a) : null;
  const before = history.filter(s => dayKey(s.time) !== today);
  const beatsOld = best && dayKey(best.time) === today && before.length &&
    !before.some(s => byTime ? s.reps >= best.reps : s.weight > best.weight || (s.weight === best.weight && s.reps >= best.reps));
  const [emoji, text] = !best
    ? ['💪', tr('Erstes Mal – heute setzt du deinen ersten Rekord!', 'First time – today you set your first record!')]
    : beatsOld
      ? ['🔥', tr('Neuer Rekord heute: ', 'New record today: ') + fmtSet(best, cardio) + tr(' – stark!', ' – great job!')]
      : ['🏆', tr('Rekord: ', 'Record: ') + fmtSet(best, cardio) + ` (${fmtDate(best.time)})` + tr(' – schaffst du heute mehr?', ' – can you beat it today?')];
  return h('div', { class: 'card glass record-line' }, h('span', null, emoji), h('span', null, text));
}

export function editSetDialog(set, cardio) {
  const st = { weight: fmtW(set.weight), reps: String(set.reps), confirmDelete: false };
  openDialog(() => {
    const w = cardio ? 0 : parseNum(st.weight);
    const r = parseIntStrict(st.reps);
    const valid = w != null && w >= 0 && r != null && r > 0;
    return dialog(tr('Eintrag bearbeiten', 'Edit entry'), [
      muted(fmtDateTime(set.time)),
      h('div', { class: 'row' },
        cardio ? null : textField({ id: 'edit-w', label: 'kg', value: st.weight, inputmode: 'decimal', oninput: v => { st.weight = v; rerender(); } }),
        textField({
          id: 'edit-r', label: cardio ? tr('Minuten', 'Minutes') : isTimed(set.exercise) ? tr('Sekunden', 'Seconds') : tr('Wdh.', 'Reps'),
          value: st.reps, inputmode: 'numeric', oninput: v => { st.reps = v; rerender(); },
        })),
      h('button', {
        class: 'btn text danger',
        onclick: () => { if (st.confirmDelete) { store.deleteSet(set); closeDialog(); } else { st.confirmDelete = true; rerender(); } },
      }, icon('delete', 18), st.confirmDelete ? tr('Wirklich löschen? Nochmal tippen', 'Really delete? Tap again') : tr('Eintrag löschen', 'Delete entry')),
    ], [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      { label: tr('Speichern', 'Save'), disabled: !valid, onClick: () => { store.updateSet(set, w, r); closeDialog(); } },
    ], closeDialog);
  });
}

// ---------- Bildschirm: Übung mit Sätzen & Fortschritt ----------

// Zustand der Detailansicht (wie rememberSaveable): gewählter Reiter je Übung, Fortschritt-Einstellungen
const tabOf = {};
let progBucket = 'DAY', progMetric = 'MAX';

export function exerciseDetailScreen(name) {
  const group = store.groupOf(name);
  const cardio = isCardio(group);
  const timed = isTimed(name);
  const history = store.setsFor(name);

  // Reiter: Training (Eingabe + Verlauf), Fortschritt, Info (Muskeln + Wiki)
  const tabs = [tr('Training', 'Training'), tr('Fortschritt', 'Progress'), tr('Info', 'Info')];
  const tab = Math.min(tabOf[name] ?? 0, tabs.length - 1);

  // Nach Tag gruppiert, neuester zuerst
  const days = new Map();
  for (const s of history) {
    const d = fmtDate(s.time);
    if (!days.has(d)) days.set(d, []);
    days.get(d).push(s);
  }
  const sumReps = l => l.reduce((a, s) => a + s.reps, 0);

  const deleteDialog = () => openDialog(() => dialog(tr('Übung löschen?', 'Delete exercise?'),
    h('p', null, tr(`„${exName(name)}“ und alle zugehörigen Sätze werden gelöscht.`, `"${exName(name)}" and all of its sets will be deleted.`)),
    [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      { label: tr('Löschen', 'Delete'), danger: true, onClick: () => { closeDialog(); store.deleteExercise(name); goBack(); } },
    ], closeDialog));

  let content;
  if (tab === 0) {
    content = [
      recordLine(history, cardio, timed),
      progressionHint(name),
      setInput(name),
      days.size ? h('div', null, h('h2', { class: 'section' }, tr('Verlauf', 'History')),
        muted(tr('Satz antippen zum Bearbeiten', 'Tap a set to edit'), 'small')) : null,
      [...days.entries()].map(([day, list]) => historyDayCard(day, list, cardio, timed)),
    ];
  } else if (tab === 1) content = exerciseProgress(history, cardio, timed);
  else {
    // Info: Muskeln (Vorder-/Rückseite) + Wiki
    const m = musclesOf(name);
    const custom = catalogGroup(name) == null;
    content = [
      m ? [muscleSideCard('FRONT', m.primary, m.secondary), muscleSideCard('BACK', m.primary, m.secondary, true)]
        : muted(tr('Noch keine Muskeln festgelegt.', 'No muscles set yet.')),
      custom ? h('button', { class: 'btn text', onclick: () => muscleEditDialog(name) },
        m ? tr('Muskeln bearbeiten', 'Edit muscles') : tr('Muskeln festlegen', 'Set muscles')) : null,
      wikiFor(name) ? h('div', { class: 'wiki-inline' }, wikiContent(name, { header: false, muscles: false })) : null,
    ];
  }

  return screen({
    title: exName(name), back: true, key: `ex-${name}-${tab}`, backdrop: exerciseImageUrl(name),
    titleIcon: exerciseIconHtml(name, group, 32),
    actions: [iconBtn('delete', deleteDialog, { label: tr('Löschen', 'Delete') })],
    top: h('div', { class: 'tab-row' }, tabs.map((label, i) =>
      h('button', { class: ['tab', i === tab && 'selected'], onclick: () => { tabOf[name] = i; rerender(); } }, label))),
    content: h('div', { class: 'stack pad' }, content),
  });
}

// ---------- Reiter „Fortschritt“ (wie ExerciseProgress in ExerciseDetail.kt) ----------

const BUCKETS = { DAY: ['Tage', 'Days'], WEEK: ['Wochen', 'Weeks'], MONTH: ['Monate', 'Months'] };
const METRICS = { MAX: ['Max. Gewicht', 'Max weight'], ORM: ['Geschätztes 1RM', 'Estimated 1RM'], VOLUME: ['Volumen', 'Volume'] };

function exerciseProgress(history, cardio, timed) {
  if (!history.length) return muted(tr('Noch keine Sätze eingetragen.', 'No sets logged yet.'));
  const strength = !cardio && !timed;
  const unit = cardio ? 'min' : timed ? 's' : 'kg';
  const value = l => cardio ? l.reduce((a, s) => a + s.reps, 0)
    : timed ? Math.max(...l.map(s => s.reps))
      : progMetric === 'ORM' ? Math.round(Math.max(...l.map(s => oneRepMax(s.weight, s.reps))) * 2) / 2
        : progMetric === 'VOLUME' ? l.reduce((a, s) => a + s.weight * s.reps, 0)
          : Math.max(...l.map(s => s.weight));
  const groupBy = (sets, key) => {
    const m = new Map();
    for (const s of sets) { const k = key(s.time); if (!m.has(k)) m.set(k, []); m.get(k).push(s); }
    return [...m.values()];
  };
  const chrono = history.slice().sort((a, b) => a.time - b.time);
  const key = progBucket === 'DAY' ? dayKey : progBucket === 'WEEK' ? t => dayKey(startOfWeek(t)) : monthIndex;
  const groups = groupBy(chrono, key);
  const values = groups.map(value);
  const perDay = groupBy(chrono, dayKey).map(value);
  const record = Math.max(...perDay);
  const gain = record - perDay[0];

  const title = (cardio ? tr('Minuten', 'Minutes') : timed ? tr('Längste Zeit', 'Longest hold') : tr(...METRICS[progMetric])) + ' ' +
    ({ DAY: tr('pro Trainingstag', 'per training day'), WEEK: tr('pro Woche', 'per week'), MONTH: tr('pro Monat', 'per month') })[progBucket];
  const best = strength ? history.reduce((a, b) => (b.weight > a.weight ? b : a)) : null;

  return [
    h('div', { class: 'card pad-m progress-head glass' },
      h('div', null, h('small', { class: 'muted' }, tr('Rekord', 'Record')), h('b', null, `🏆 ${fmtW(record)} ${unit}`)),
      h('div', { class: 'right' }, h('small', { class: 'muted' }, tr('seit dem ersten Training', 'since first session')),
        h('b', { style: gain > 0 ? `color:${completionColor(1)}` : null }, `${gain > 0 ? '+' : ''}${fmtW(gain)} ${unit}${gain > 0 ? ' ↑' : ''}`))),
    strength ? [
      muted(tr('Bester Satz: ', 'Best set: ') + fmtSet(best, false) + ' · ' + tr('geschätztes 1RM ', 'estimated 1RM ') +
        `${fmtW(Math.round(Math.max(...history.map(s => oneRepMax(s.weight, s.reps))) * 2) / 2)} kg`, 'small'),
      h('div', { class: 'chips wrap' }, Object.entries(METRICS).map(([k, l]) => chip(tr(...l), progMetric === k, () => { progMetric = k; rerender(); }))),
    ] : null,
    h('div', { class: 'chips' }, Object.entries(BUCKETS).map(([k, l]) => chip(tr(...l), progBucket === k, () => { progBucket = k; rerender(); }))),
    values.length >= 2 ? [
      glassify(progressChart(values, title, unit)),
      h('div', { class: 'chart-dates muted' }, h('span', null, fmtShortDate(groups[0][0].time)), h('span', null, fmtShortDate(groups.at(-1)[0].time))),
    ] : muted(tr('Ab zwei Einträgen in dieser Einteilung erscheint hier ein Diagramm.', 'A chart appears once there are two entries for this grouping.')),
  ];
}

/** Karte (oder deren erstes Kind) leicht durchsichtig machen. */
function glassify(el) {
  (el.classList?.contains('card') ? el : el.querySelector?.('.card'))?.classList.add('glass');
  return el;
}

/**
 * Karte eines Trainingstags im Verlauf (Übungsdetails und Trainingsmodus) – wie HistoryDayCard in ExerciseDetail.kt:
 * Kopfzeile mit Sätzen/Volumen, darunter die Sätze (neuester oben, antippen = bearbeiten).
 */
export function historyDayCard(day, list, cardio, timed) {
  const sumReps = l => l.reduce((a, s) => a + s.reps, 0);
  return h('div', { class: 'card pad-s glass' },
    h('b', null, `${day} · ` + (cardio
      ? plural(list.length, tr('Einheit', 'session'), tr('Einheiten', 'sessions')) + ` · ${sumReps(list)} ` + tr('Min gesamt', 'min total')
      : timed
        ? plural(list.length, tr('Satz', 'set'), tr('Sätze', 'sets')) + ' · ' + tr('gesamt ', 'total ') + fmtSeconds(sumReps(list))
        : plural(list.length, tr('Satz', 'set'), tr('Sätze', 'sets')) + ` · ${fmtW(list.reduce((a, s) => a + s.weight * s.reps, 0))} ` + tr('kg Volumen', 'kg volume'))),
    // Neuester Satz oben; Nummer bleibt die Reihenfolge im Training
    list.slice().sort((a, b) => a.time - b.time).map((s, i) => setRow(s, i + 1, cardio)).reverse());
}

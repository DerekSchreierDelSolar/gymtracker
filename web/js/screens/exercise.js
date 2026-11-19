// Übungs-Bausteine und Übungsdetail – entspricht ExerciseDetail.kt, Progression.kt (UI) und ExerciseTile.

import { store } from '../store.js';
import {
  tr, exName, fmtSet, fmtW, fmtDate, fmtDateTime, fmtTime, fmtSeconds, plural, isCardio, isTimed, equipmentOf,
  oneRepMax, parseNum, parseIntStrict, startOfToday, groupColor,
} from '../core.js';
import { progressionSuggestion } from '../stats.js';
import { exerciseIconHtml } from '../pictograms.js';
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
      h('button', { class: 'btn', onclick: add }, cardio ? tr('Eintragen', 'Log') : tr('Satz +', 'Set +'))));
}

// ---------- Satz-Zeile + Bearbeiten ----------

export function setRow(set, number, cardio) {
  return h('div', { class: 'set-row' },
    h('span', { class: 'grow' }, `${number}. ${fmtSet(set, cardio)}`),
    h('small', { class: 'muted' }, fmtTime(set.time)),
    iconBtn('edit', () => editSetDialog(set, cardio), { label: tr('Bearbeiten', 'Edit') }));
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

let show1RM = false;

export function exerciseDetailScreen(name) {
  const group = store.groupOf(name);
  const cardio = isCardio(group);
  const timed = isTimed(name);
  const history = store.setsFor(name);

  // Nach Tag gruppiert, neuester zuerst
  const days = new Map();
  for (const s of history) {
    const d = fmtDate(s.time);
    if (!days.has(d)) days.set(d, []);
    days.get(d).push(s);
  }
  const dayLists = [...days.values()];
  const metric = cardio || timed ? s => s.reps : s => s.weight;
  const best = history.length ? history.reduce((a, b) => (metric(b) > metric(a) ? b : a)) : null;
  const strengthSets = history.filter(s => s.weight > 0);
  const best1RM = cardio || timed || !strengthSets.length ? null : Math.max(...strengthSets.map(s => oneRepMax(s.weight, s.reps)));
  const sumReps = l => l.reduce((a, s) => a + s.reps, 0);
  const maxOf = (l, f) => Math.max(...l.map(f));

  let chart = null;
  if (dayLists.length >= 2) {
    const chrono = dayLists.slice().reverse();
    if (cardio) chart = progressChart(chrono.map(sumReps), tr('Minuten pro Trainingstag', 'Minutes per training day'), 'min');
    else if (timed) chart = progressChart(chrono.map(d => maxOf(d, s => s.reps)), tr('Längste Zeit pro Training', 'Longest hold per session'), 's');
    else {
      chart = h('div', null,
        h('div', { class: 'chips' },
          chip(tr('Max. Gewicht', 'Max weight'), !show1RM, () => { show1RM = false; rerender(); }),
          chip(tr('Geschätztes 1RM', 'Estimated 1RM'), show1RM, () => { show1RM = true; rerender(); })),
        show1RM
          ? progressChart(chrono.map(d => Math.round(maxOf(d, s => oneRepMax(s.weight, s.reps)) * 2) / 2),
            tr('Geschätztes 1RM pro Training', 'Estimated 1RM per session'), 'kg')
          : progressChart(chrono.map(d => maxOf(d, s => s.weight)), tr('Maximalgewicht pro Training', 'Max weight per session'), 'kg'));
    }
  }

  const deleteDialog = () => openDialog(() => dialog(tr('Übung löschen?', 'Delete exercise?'),
    h('p', null, tr(`„${exName(name)}“ und alle zugehörigen Sätze werden gelöscht.`, `"${exName(name)}" and all of its sets will be deleted.`)),
    [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      { label: tr('Löschen', 'Delete'), danger: true, onClick: () => { closeDialog(); store.deleteExercise(name); goBack(); } },
    ], closeDialog));

  return screen({
    title: exName(name), back: true, key: 'ex-' + name,
    titleIcon: exerciseIconHtml(name, group, 32),
    actions: [iconBtn('delete', deleteDialog, { label: tr('Löschen', 'Delete') })],
    content: h('div', { class: 'stack pad' },
      chart,
      best ? h('div', null,
        h('b', null, (cardio || timed ? tr('🏆 Längste Einheit: ', '🏆 Longest: ') : tr('🏆 Bestleistung: ', '🏆 Personal record: ')) + fmtSet(best, cardio)),
        best1RM != null ? muted(tr('Geschätztes 1RM: ', 'Estimated 1RM: ') + `${fmtW(Math.round(best1RM * 2) / 2)} kg`) : null) : null,
      progressionHint(name),
      setInput(name),
      dayLists.length ? h('h2', { class: 'section' }, tr('Verlauf', 'History')) : null,
      [...days.entries()].map(([day, list]) => h('div', { class: 'card pad-s' },
        h('b', null, `${day} · ` + (cardio
          ? plural(list.length, tr('Einheit', 'session'), tr('Einheiten', 'sessions')) + ` · ${sumReps(list)} ` + tr('Min gesamt', 'min total')
          : timed
            ? plural(list.length, tr('Satz', 'set'), tr('Sätze', 'sets')) + ' · ' + tr('gesamt ', 'total ') + fmtSeconds(sumReps(list))
            : plural(list.length, tr('Satz', 'set'), tr('Sätze', 'sets')) + ` · ${fmtW(list.reduce((a, s) => a + s.weight * s.reps, 0))} ` + tr('kg Volumen', 'kg volume'))),
        list.slice().sort((a, b) => a.time - b.time).map((s, i) => setRow(s, i + 1, cardio))))),
  });
}

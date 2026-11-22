// Körpermaße + Körperfett (Roadmap Schritt 15) – entspricht Measures.kt.
// store.measures = [{ time, type, value }], älteste zuerst. Körperfett aus Health Connect gibt es nur auf Android.

import { store, MEASURE_TYPES } from './store.js';
import { tr, fmtW, fmtDate, fmtShortDate, parseNum } from './core.js';
import { h, chip, textField, dialog, lineChart, dateField, isoDay, timeOfIsoDay, muted, undoToast, toast } from './ui.js';
import { openDialog, closeDialog, rerender } from './nav.js';

const label = t => tr(MEASURE_TYPES[t][3], MEASURE_TYPES[t][4]);
const unit = t => MEASURE_TYPES[t][0];
const valid = (t, v) => v != null && v >= MEASURE_TYPES[t][1] && v <= MEASURE_TYPES[t][2];

const ui = { type: 'WAIST', input: '' };

function editDialog(m) {
  const st = { text: fmtW(m.value), day: isoDay(m.time) };
  openDialog(() => {
    const v = parseNum(st.text);
    const ok = valid(m.type, v);
    return dialog(tr(`${label(m.type)} bearbeiten`, `Edit ${label(m.type)}`), [
      textField({ id: 'measure-edit', label: `${label(m.type)} (${unit(m.type)})`, value: st.text, inputmode: 'decimal', error: ok ? null : ' ',
        oninput: x => { st.text = x; rerender(); } }),
      h('label', { class: 'date-row' }, dateField({ id: 'measure-edit-date', value: st.day, max: isoDay(Date.now()), onchange: x => { st.day = x; } })),
    ], [
      { label: tr('Löschen', 'Delete'), danger: true, onClick: () => { closeDialog(); store.deleteMeasure(m); undoToast(tr('Eintrag gelöscht', 'Entry deleted'), () => store.restoreMeasure(m)); } },
      { label: tr('Speichern', 'Save'), disabled: !ok, onClick: () => {
        closeDialog();
        store.updateMeasure(m, v, st.day === isoDay(m.time) ? m.time : timeOfIsoDay(st.day));
      } },
    ], closeDialog);
  });
}

/** Karte „Körpermaße“ im Tab Körper (wie MeasuresCard). */
export function measuresCard() {
  const t = ui.type;
  const list = store.measures.filter(m => m.type === t);
  const last = list.at(-1);
  const add = () => {
    const v = parseNum(ui.input);
    if (!valid(t, v)) { toast(tr(`Bitte einen Wert zwischen ${MEASURE_TYPES[t][1]} und ${MEASURE_TYPES[t][2]} ${unit(t)} eingeben.`, `Please enter a value between ${MEASURE_TYPES[t][1]} and ${MEASURE_TYPES[t][2]} ${unit(t)}.`)); return; }
    ui.input = '';
    document.activeElement?.blur();
    store.addMeasure(t, v);
  };
  let body;
  if (!list.length) {
    body = muted(t === 'BODYFAT' ? tr('Noch kein Wert. In der Android-App kommt das Körperfett auch automatisch über Health Connect.', 'No value yet. In the Android app body fat also comes in automatically via Health Connect.')
      : tr('Noch kein Wert – am besten morgens, immer an derselben Stelle messen.', 'No value yet – measure in the morning, always at the same spot.'), 'small');
  } else {
    const first = list[0];
    const diff = last.value - first.value;
    const shown = list.slice(-30);
    body = [
      h('div', { class: 'measure-now' }, h('b', null, `${fmtW(last.value)} ${unit(t)}`),
        list.length >= 2 ? h('small', { class: 'muted' }, `${diff >= 0 ? '+' : '−'}${fmtW(Math.round(Math.abs(diff) * 10) / 10)} ${unit(t)} ` + tr('seit ', 'since ') + fmtDate(first.time)) : null),
      lineChart(shown.map(m => m.value), unit(t), i => editDialog(shown[i])),
      h('div', { class: 'axis' }, h('small', null, fmtShortDate(shown[0].time)), shown.length > 1 ? h('small', null, fmtShortDate(shown.at(-1).time)) : null),
    ];
  }
  return h('div', { class: 'card pad-m measures-card' },
    h('h3', null, tr('📏 Körpermaße', '📏 Body measurements')),
    h('div', { class: 'chips scroll-chips' }, Object.keys(MEASURE_TYPES).map(k => chip(label(k), k === t, () => { ui.type = k; ui.input = ''; rerender(); }))),
    h('div', { class: 'row' },
      textField({ id: 'measure-in', label: `${label(t)} (${unit(t)})` + (last ? tr(' · zuletzt ', ' · last ') + fmtW(last.value) : ''), value: ui.input, inputmode: 'decimal',
        oninput: v => { ui.input = v; }, onenter: add }),
      h('button', { class: 'btn', onclick: add }, tr('Eintragen', 'Log'))),
    body);
}

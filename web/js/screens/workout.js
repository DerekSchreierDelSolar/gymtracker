// Workouts, Übungsauswahl, Katalog – entspricht WorkoutScreens.kt und CatalogScreen.kt.

import { wikiButton } from '../wiki.js';
import { musclePicker } from '../bodymap.js';
import { store, TEMPLATES } from '../store.js';
import { CATALOG } from '../data.js';
import {
  tr, exName, fmtDate, fmtDateTime, fmtDuration, fmtW, plural, isCardio, isTimed, equipmentOf, catalogEntry,
  catalogGroup, GROUP_KEYS, EQUIPMENT_KEYS, groupLabel, equipmentLabel, groupColor, fmtSet,
} from '../core.js';
import { sessionStats, weekdayShort, daysLabel } from '../stats.js';
import { exerciseIconHtml, muscleIconHtml } from '../pictograms.js';
import { h, icon, iconBtn, chip, dialog, textField, groupHeader, muted, toast } from '../ui.js';
import { nav, go, rerender, openDialog, closeDialog, goBack, update } from '../nav.js';
import { screen, fab } from '../layout.js';

import { filterActions, filterBar, matches, noFilterResults } from '../filter.js';

const sortedGroups = names => [...new Set(names.map(n => store.groupOf(n)))].sort((a, b) => GROUP_KEYS.indexOf(a) - GROUP_KEYS.indexOf(b));

// ---------- Tab „Workouts“ ----------

export function workoutList() {
  if (!store.workouts.length) {
    return h('div', { class: 'pad stack' },
      h('p', null, tr('Noch keine Workouts. Ein Workout fasst mehrere Übungen zusammen, z. B. „Push-Tag“ oder „Beine“.',
        'No workouts yet. A workout groups several exercises, e.g. "Push day" or "Legs".')),
      h('div', null, h('button', { class: 'btn', onclick: newWorkoutDialog }, tr('Workout anlegen', 'Create workout'))));
  }
  return h('div', { class: 'stack pad list-bottom' }, store.workouts.map(w => {
    const last = store.sessionsFor(w.id)[0];
    return h('button', { class: 'card workout-card', onclick: () => go({ workoutId: w.id }) },
      h('div', { class: 'grow' },
        h('h2', null, w.name),
        // Trainingstage laut Plan, z. B. „📅 Mo · Do“
        w.days?.length ? h('small', { class: 'accent plan-days' }, '📅 ' + daysLabel(w.days)) : null,
        h('small', { class: 'muted' }, plural(w.exercises.length, tr('Übung', 'exercise'), tr('Übungen', 'exercises')) +
          (last ? tr(' · zuletzt ', ' · last ') + fmtDate(last.start) : '')),
        w.exercises.length ? h('div', { class: 'icon-row', html: sortedGroups(w.exercises).map(g => muscleIconHtml(g, 28)).join('') }) : null),
      icon('chevron'));
  }));
}

export function newWorkoutDialog() {
  const st = { name: '' };
  openDialog(() => dialog(tr('Neues Workout', 'New workout'), [
    textField({ id: 'new-workout', label: tr('Name, z. B. Push-Tag', 'Name, e.g. Push day'), value: st.name, oninput: v => { st.name = v; rerender(); } }),
    h('b', { class: 'small-title' }, tr('… oder Vorlage übernehmen:', '… or start from a template:')),
    h('div', { class: 'chips wrap' }, TEMPLATES.map(t => h('button', {
      class: 'chip filled',
      onclick: () => { closeDialog(); go({ workoutId: store.addWorkoutFromTemplate(t).id }); },
    }, tr(t.de, t.en)))),
  ], [
    { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
    {
      label: tr('Anlegen', 'Create'), disabled: !st.name.trim(),
      onClick: () => { const w = store.addWorkout(st.name); closeDialog(); if (w) go({ workoutId: w.id, pick: true }); },
    },
  ], closeDialog));
}

/** Trainingstage Mo–So als Chips (wie WorkoutDaysRow in Plan.kt) */
function daysRow(w) {
  return h('div', { class: 'plan-row' },
    h('b', { class: 'small-title' }, tr('Trainingstage', 'Training days')),
    muted(tr('Geplante Tage erscheinen auf der Startseite als „Heute dran“.', 'Planned days show up on the home screen as "Today".'), 'small'),
    h('div', { class: 'day-chips' }, [1, 2, 3, 4, 5, 6, 7].map(d =>
      chip(weekdayShort(d), (w.days ?? []).includes(d), () => store.toggleDay(w.id, d)))));
}

// ---------- Ein Workout ----------

export function workoutScreen(w, onStart) {
  const history = store.sessionsFor(w.id);
  const deleteDialog = () => openDialog(() => dialog(tr('Workout löschen?', 'Delete workout?'),
    h('p', null, tr(`„${w.name}“ wird gelöscht. Die Übungen und ihre Sätze bleiben erhalten.`,
      `"${w.name}" will be deleted. The exercises and their sets are kept.`)),
    [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      { label: tr('Löschen', 'Delete'), danger: true, onClick: () => { closeDialog(); store.deleteWorkout(w.id); goBack(); } },
    ], closeDialog));

  let content;
  if (!w.exercises.length) {
    content = h('div', { class: 'pad stack' },
      h('p', null, tr('Dieses Workout hat noch keine Übungen.', 'This workout has no exercises yet.')),
      h('div', null, h('button', { class: 'btn', onclick: () => go({ pick: true }) }, tr('Übungen auswählen', 'Choose exercises'))),
      daysRow(w));
  } else {
    const list = h('div', { class: 'ex-list' }, w.exercises.map(name => exerciseRow(w, name)));
    enableDrag(list, w);
    content = h('div', { class: 'pad list-bottom' },
      daysRow(w),
      muted(tr('Tipp: Am Griff rechts ziehen (oder Zeile gedrückt halten), um die Reihenfolge zu ändern.',
        'Tip: drag the handle on the right (or long-press a row) to change the order.'), 'small'),
      list,
      history.length ? [
        h('h2', { class: 'section' }, tr('Letzte Trainings', 'Recent sessions')),
        h('div', { class: 'stack' }, history.map(t => {
          const s = sessionStats(store, t);
          return h('button', { class: 'card row-card', onclick: () => go({ summaryId: t.id }) },
            h('div', { class: 'grow' },
              h('b', null, fmtDateTime(t.start)),
              h('small', { class: 'muted' }, `${s.percent} % · ${fmtDuration(t.end - t.start)} · ` +
                plural(s.sets, tr('Satz', 'set'), tr('Sätze', 'sets')) +
                (s.volume > 0 ? ` · ${fmtW(s.volume)} kg` : '') + (s.kcal != null ? ` · 🔥 ${s.kcal} kcal` : ''))),
            s.records > 0 ? h('span', null, `🏆 ${s.records}`) : null,
            icon('chevron'));
        })),
      ] : null);
  }

  return screen({
    title: w.name, back: true, key: 'workout-' + w.id,
    actions: [
      iconBtn('edit', () => go({ pick: true }), { label: tr('Übungen wählen', 'Choose exercises') }),
      iconBtn('copy', () => { const c = store.duplicateWorkout(w.id); if (c) { update({ workoutId: c.id }); toast(tr('Kopie angelegt', 'Copy created')); } }, { label: tr('Kopieren', 'Duplicate') }),
      iconBtn('delete', deleteDialog, { label: tr('Löschen', 'Delete') }),
    ],
    content,
    fab: w.exercises.length ? fab(tr('Training starten', 'Start workout'), 'play', onStart) : null,
  });
}

/** Eine Übung im Workout als Listenzeile: Bild, Name, letzter Satz + Ziel-Sätze, Griff (wie WorkoutExerciseRow in WorkoutScreens.kt) */
function exerciseRow(w, name) {
  const g = store.groupOf(name), last = store.setsFor(name)[0], target = store.targetFor(w, name);
  const sub = [last ? fmtSet(last, isCardio(g)) : '–',
    target > 0 ? tr('Ziel ', 'Goal ') + plural(target, tr('Satz', 'set'), tr('Sätze', 'sets')) : null].filter(Boolean).join(' · ');
  return h('button', {
    class: 'ex-row', 'data-name': name, style: `--tint:${groupColor(g)}`,
    onclick: e => {
      if (e.currentTarget.dataset.justDragged || e.target.closest('.drag-handle')) return; // nach dem Ziehen / am Griff nicht öffnen
      go({ exercise: name });
    },
  },
  h('span', { html: exerciseIconHtml(name, g, 44) }),
  h('div', { class: 'grow' }, h('b', null, exName(name)), h('small', null, sub)),
  h('span', { class: 'drag-handle', 'aria-label': tr('Ziehen', 'Drag') }, icon('drag')));
}

/**
 * Drag & Drop in der Liste: am Griff sofort, sonst Zeile 350 ms gedrückt halten. Die Zeile folgt dem Finger
 * senkrecht; überdeckt sie den Nachbarn zur Hälfte, tauschen beide im DOM die Plätze und der Nachbar gleitet
 * animiert an seinen neuen Platz (FLIP). Am Rand scrollt die Liste automatisch mit.
 * Erst beim Loslassen wird die neue Reihenfolge gespeichert.
 */
function enableDrag(list, workout) {
  const rows = () => [...list.querySelectorAll('.ex-row')];
  /** Nachbarzeile weich verschieben: alte Position merken, im DOM umhängen, Sprung per transform zurück und auf 0 animieren */
  const slide = (el, move) => {
    const before = el.offsetTop;
    move();
    el.style.transition = 'none';
    el.style.transform = `translateY(${before - el.offsetTop}px)`;
    void el.offsetHeight; // Layout erzwingen, damit der Startwert gilt
    el.style.transition = 'transform .18s ease-out';
    el.style.transform = '';
  };
  const scroller = () => list.closest('.content') ?? document.scrollingElement;
  for (const row of rows()) {
    let timer = null, dragging = false, sx = 0, sy = 0, lastY = 0, base = 0, scroll0 = 0, pointerId = null;
    /** Zeile an den Finger setzen (inkl. bisher automatisch gescrollter Strecke) und ggf. mit Nachbarn tauschen */
    const update = () => {
      const top = base + lastY + (scroller().scrollTop - scroll0); // gewünschte Oberkante im Listen-Koordinatensystem
      for (let i = 0; i < 20; i++) { // beim schnellen Auto-Scroll ggf. mehrere Plätze auf einmal
        const next = row.nextElementSibling, prev = row.previousElementSibling;
        if (next && top + row.offsetHeight > next.offsetTop + next.offsetHeight / 2) slide(next, () => row.before(next));
        else if (prev && top < prev.offsetTop + prev.offsetHeight / 2) slide(prev, () => row.after(prev));
        else break;
      }
      row.style.transition = 'none';
      row.style.transform = `translateY(${top - row.offsetTop}px)`;
    };
    /** Auto-Scroll: Finger nahe am oberen/unteren Rand → Liste weiterschieben, je näher am Rand, desto schneller */
    const autoScroll = () => {
      if (!dragging) return;
      const sc = scroller(), r = sc.getBoundingClientRect(), zone = 80;
      const bottom = r.bottom - 72; // der schwebende Start-Button verdeckt den unteren Rand
      let v = 0;
      if (lastY < r.top + zone) v = -Math.min(1, (r.top + zone - lastY) / zone) * 14;
      else if (lastY > bottom - zone) v = Math.min(1, (lastY - bottom + zone) / zone) * 14;
      if (v) {
        const before = sc.scrollTop;
        sc.scrollTop += v;
        if (sc.scrollTop !== before) update();
      }
      requestAnimationFrame(autoScroll);
    };
    const begin = () => {
      dragging = true;
      lastY = sy;
      base = row.offsetTop - sy; // Layout-Platz relativ zum Finger
      scroll0 = scroller().scrollTop;
      row.classList.add('dragging');
      navigator.vibrate?.(20);
      try { row.setPointerCapture(pointerId); } catch { /* egal */ }
      requestAnimationFrame(autoScroll);
    };
    const end = () => {
      clearTimeout(timer); timer = null;
      if (!dragging) return;
      dragging = false;
      row.style.transition = 'transform .15s ease-out';
      row.style.transform = '';
      row.classList.remove('dragging');
      workout.exercises.splice(0, workout.exercises.length, ...rows().map(r => r.dataset.name));
      row.dataset.justDragged = '1';
      setTimeout(() => { delete row.dataset.justDragged; store.saveNow(); }, 160);
    };
    row.addEventListener('pointerdown', e => {
      sx = e.clientX; sy = e.clientY; pointerId = e.pointerId;
      if (e.target.closest('.drag-handle')) { e.preventDefault(); begin(); return; }
      timer = setTimeout(begin, 350);
    });
    row.addEventListener('pointermove', e => {
      if (!dragging) {
        if (timer && Math.hypot(e.clientX - sx, e.clientY - sy) > 8) { clearTimeout(timer); timer = null; }
        return;
      }
      lastY = e.clientY;
      update();
    });
    row.addEventListener('pointerup', end);
    row.addEventListener('pointercancel', end);
    // Während des Ziehens nicht (von Hand) scrollen
    row.addEventListener('touchmove', e => { if (dragging) e.preventDefault(); }, { passive: false });
    row.addEventListener('contextmenu', e => e.preventDefault());
  }
}

// ---------- Übungen fürs Workout auswählen ----------

export function workoutPickerScreen(w) {
  const all = [...CATALOG.map(c => c.name), ...store.customNames.filter(n => !catalogEntry(n))]; // inkl. aus der Liste entfernter
  const visible = all.filter(n => matches(nav.pickFilter, n, store.groupOf(n)));
  return screen({
    title: tr('Übungen: ', 'Exercises: ') + w.name, back: true, key: 'pick-' + w.id,
    actions: filterActions('pick'),
    top: filterBar('pick', sortedGroups(all), EQUIPMENT_KEYS),
    content: visible.length === 0 ? noFilterResults('pick') : h('div', { class: 'pad' },
      h('p', null, plural(w.exercises.length, tr('Übung', 'exercise'), tr('Übungen', 'exercises')) + tr(' ausgewählt', ' selected')),
      GROUP_KEYS.map(g => {
        const names = visible.filter(n => store.groupOf(n) === g);
        if (!names.length) return null;
        return [groupHeader(g), names.map(name => {
          const checked = w.exercises.includes(name);
          const target = store.targetFor(w, name);
          const step = d => e => { e.stopPropagation(); store.setTarget(w.id, name, target + d); };
          return h('div', { class: 'pick-row', onclick: () => store.toggleInWorkout(w.id, name) },
            h('span', { html: exerciseIconHtml(name, g, 36) }),
            h('span', { class: 'grow' }, exName(name)),
            checked ? h('span', { class: 'stepper' },
              h('button', { class: 'step', onclick: step(-1), disabled: target <= 0 }, '−'),
              h('span', { class: 'step-value' }, target === 0 ? 'optional' : isCardio(g) ? `${target}×` : plural(target, tr('Satz', 'set'), tr('Sätze', 'sets'))),
              h('button', { class: 'step', onclick: step(1), disabled: target >= 20 }, '+')) : null,
            h('span', { class: ['checkbox', checked && 'checked'] }, checked ? icon('check', 18) : null));
        })];
      })),
  });
}

// ---------- Katalog ----------

export function catalogScreen() {
  const f = nav.catalogFilter;
  const visible = CATALOG.filter(c => matches(f, c.name, c.group));
  const custom = store.customNames.filter(n => matches(f, n, store.groupOf(n))); // inkl. aus der Liste entfernter → wieder hinzufügbar
  const byGroup = GROUP_KEYS.map(g => [g, visible.filter(c => c.group === g)]).filter(([, l]) => l.length);

  return screen({
    title: tr('Übungskatalog', 'Exercise catalog'), back: true, key: 'catalog',
    actions: [iconBtn('add', customExerciseDialog, { label: tr('Eigene Übung', 'Custom exercise') }), ...filterActions('catalog')],
    top: filterBar('catalog', GROUP_KEYS.filter(g => CATALOG.some(c => c.group === g)), EQUIPMENT_KEYS),
    content: !visible.length && !custom.length ? noFilterResults('catalog') : h('div', { class: 'pad' },
      muted(tr('Tippe auf eine Übung, um sie zu deiner Liste hinzuzufügen. Fehlt etwas? Oben mit + eine eigene Übung anlegen.',
        'Tap an exercise to add it to your list. Missing one? Create your own with + at the top.'), 'small'),
      custom.length ? [
        h('h2', { class: 'section' }, tr('Eigene Übungen', 'Custom exercises')),
        custom.map(n => {
          const g = store.groupOf(n), added = store.hasExercise(n);
          return h('div', { class: 'cat-row', role: 'button', onclick: added ? null : () => store.addExercise(n) },
            h('span', { html: exerciseIconHtml(n, g, 40) }),
            h('div', { class: 'grow' }, h('span', null, n),
              h('small', { class: 'muted' }, [groupLabel(g), equipmentOf(n) && equipmentLabel(equipmentOf(n)), isTimed(n) && tr('Zeit', 'Time')].filter(Boolean).join(' · '))),
            added ? h('span', { class: 'accent' }, icon('check')) : h('span', { class: 'add-mark' }, '+'));
        }),
      ] : null,
      byGroup.map(([g, list]) => [groupHeader(g), list.map(c => {
        const added = store.hasExercise(c.name);
        return h('div', { class: 'cat-row', role: 'button', onclick: added ? null : () => store.addExercise(c.name, c.group) },
          h('span', { html: exerciseIconHtml(c.name, g, 40) }),
          h('div', { class: 'grow' }, h('span', null, exName(c.name)), h('small', { class: 'muted' }, equipmentLabel(c.equipment))),
          wikiButton(c.name),
          h('span', { class: ['add-mark', added && 'accent'] }, added ? '✓' : '+'));
      })])),
  });
}

export function customExerciseDialog() {
  const st = { name: '', group: 'BRUST', equipment: null, timed: false, muscles: { primary: [], secondary: [] } };
  openDialog(() => {
    const exists = st.name.trim() !== '' && (store.hasExercise(st.name) || catalogGroup(st.name.trim()) != null);
    return dialog(tr('Eigene Übung', 'Custom exercise'), [
      textField({ id: 'custom-name', label: 'Name', value: st.name, error: exists ? tr('Gibt es schon', 'Already exists') : null, oninput: v => { st.name = v; rerender(); } }),
      h('b', { class: 'small-title' }, tr('Kategorie', 'Category')),
      h('div', { class: 'chips wrap' }, GROUP_KEYS.map(g => chip(groupLabel(g), st.group === g, () => { st.group = g; rerender(); }, muscleIconHtml(g, 18)))),
      h('b', { class: 'small-title' }, tr('Gerät', 'Equipment')),
      h('div', { class: 'chips wrap' }, EQUIPMENT_KEYS.map(e => chip(equipmentLabel(e), st.equipment === e, () => { st.equipment = st.equipment === e ? null : e; rerender(); }))),
      isCardio(st.group)
        ? muted(tr('Cardio wird in Minuten erfasst.', 'Cardio is logged in minutes.'), 'small')
        : [h('b', { class: 'small-title' }, tr('Messung', 'Measured by')),
          h('div', { class: 'chips' },
            chip(tr('Wiederholungen', 'Reps'), !st.timed, () => { st.timed = false; rerender(); }),
            chip(tr('Zeit (Sekunden)', 'Time (seconds)'), st.timed, () => { st.timed = true; rerender(); }))],
      h('b', { class: 'small-title' }, tr('Muskeln (optional)', 'Muscles (optional)')),
      musclePicker(st.muscles),
    ], [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      {
        label: tr('Anlegen', 'Create'), disabled: !st.name.trim() || exists,
        onClick: () => { if (store.addCustomExercise(st.name, st.group, st.equipment, st.timed, st.muscles)) closeDialog(); },
      },
    ], closeDialog);
  });
}

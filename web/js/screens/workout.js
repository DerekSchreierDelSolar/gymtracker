// Workouts, Übungsauswahl, Katalog – entspricht WorkoutScreens.kt und CatalogScreen.kt.

import { wikiButton } from '../wiki.js';
import { musclePicker } from '../bodymap.js';
import { store, TEMPLATES } from '../store.js';
import { CATALOG } from '../data.js';
import {
  tr, exName, fmtDate, fmtDateTime, fmtDuration, fmtW, plural, isCardio, isTimed, equipmentOf, catalogEntry,
  catalogGroup, GROUP_KEYS, EQUIPMENT_KEYS, groupLabel, equipmentLabel,
} from '../core.js';
import { sessionStats } from '../stats.js';
import { exerciseIconHtml, muscleIconHtml } from '../pictograms.js';
import { h, icon, iconBtn, chip, dialog, textField, groupHeader, muted, toast } from '../ui.js';
import { nav, go, rerender, openDialog, closeDialog, goBack, update } from '../nav.js';
import { screen, fab } from '../layout.js';
import { exerciseTile } from './exercise.js';
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
      h('div', null, h('button', { class: 'btn', onclick: () => go({ pick: true }) }, tr('Übungen auswählen', 'Choose exercises'))));
  } else {
    const grid = h('div', { class: 'tile-grid' }, w.exercises.map(name =>
      exerciseTile(name, store.groupOf(name), e => {
        if (e.currentTarget.dataset.justDragged) return; // Klick nach dem Ziehen ignorieren
        go({ exercise: name });
      })));
    enableDrag(grid, w);
    content = h('div', { class: 'pad list-bottom' },
      muted(tr('Tipp: Kachel gedrückt halten und ziehen, um die Reihenfolge zu ändern.', 'Tip: long-press a tile and drag it to change the order.'), 'small'),
      grid,
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

/**
 * Drag & Drop: Kachel lange drücken (350 ms), dann ziehen. Während des Ziehens werden die
 * DOM-Elemente direkt umsortiert; erst beim Loslassen wird die neue Reihenfolge gespeichert.
 */
function enableDrag(grid, workout) {
  const tilesOf = () => [...grid.querySelectorAll('.tile[data-name]')];
  for (const tile of tilesOf()) {
    let timer = null, dragging = false, sx = 0, sy = 0, gx = 0, gy = 0;
    const naturalRect = () => {
      const t = tile.style.transform;
      tile.style.transform = '';
      const r = tile.getBoundingClientRect();
      tile.style.transform = t;
      return r;
    };
    const end = () => {
      clearTimeout(timer); timer = null;
      if (!dragging) return;
      dragging = false;
      tile.classList.remove('dragging'); grid.classList.remove('drag-active');
      tile.style.transform = '';
      workout.exercises.splice(0, workout.exercises.length, ...tilesOf().map(t => t.dataset.name));
      tile.dataset.justDragged = '1';
      setTimeout(() => { delete tile.dataset.justDragged; store.saveNow(); }, 60);
    };
    tile.addEventListener('pointerdown', e => {
      sx = e.clientX; sy = e.clientY;
      timer = setTimeout(() => {
        dragging = true;
        const r = tile.getBoundingClientRect();
        gx = sx - r.left; gy = sy - r.top;
        tile.classList.add('dragging'); grid.classList.add('drag-active');
        navigator.vibrate?.(20);
        try { tile.setPointerCapture(e.pointerId); } catch { /* egal */ }
      }, 350);
    });
    tile.addEventListener('pointermove', e => {
      if (!dragging) {
        if (timer && Math.hypot(e.clientX - sx, e.clientY - sy) > 8) { clearTimeout(timer); timer = null; }
        return;
      }
      const cx = e.clientX, cy = e.clientY;
      const tiles = tilesOf();
      const target = tiles.find(t => {
        if (t === tile) return false;
        const r = t.getBoundingClientRect();
        return cx >= r.left && cx <= r.right && cy >= r.top && cy <= r.bottom;
      });
      if (target) {
        if (tiles.indexOf(tile) < tiles.indexOf(target)) target.after(tile); else target.before(tile);
      }
      // Kachel bleibt unter dem Finger, auch wenn sie im Raster an einen neuen Platz gerückt ist
      const n = naturalRect();
      tile.style.transform = `translate(${cx - gx - n.left}px, ${cy - gy - n.top}px)`;
    });
    tile.addEventListener('pointerup', end);
    tile.addEventListener('pointercancel', end);
    // Während des Ziehens nicht scrollen
    tile.addEventListener('touchmove', e => { if (dragging) e.preventDefault(); }, { passive: false });
    tile.addEventListener('contextmenu', e => e.preventDefault());
  }
}

// ---------- Übungen fürs Workout auswählen ----------

export function workoutPickerScreen(w) {
  const all = [...CATALOG.map(c => c.name), ...store.exercises.filter(n => !catalogEntry(n))];
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
  const custom = store.exercises.filter(n => !catalogGroup(n) && matches(f, n, store.groupOf(n)));
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
          const g = store.groupOf(n);
          return h('div', { class: 'cat-row' },
            h('span', { html: exerciseIconHtml(n, g, 40) }),
            h('div', { class: 'grow' }, h('span', null, n),
              h('small', { class: 'muted' }, [groupLabel(g), equipmentOf(n) && equipmentLabel(equipmentOf(n)), isTimed(n) && tr('Zeit', 'Time')].filter(Boolean).join(' · '))),
            h('span', { class: 'accent' }, icon('check')));
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

// Workouts, Übungsauswahl, Katalog – entspricht WorkoutScreens.kt und CatalogScreen.kt.

import { wikiButton, wikiFor, wikiContent } from '../wiki.js';
import { FREE_WORKOUT_ID, lastFreeSession } from '../free.js';
import { musclePicker, musclesOf, muscleSideCard, levelsOf, heatCard, workoutRegionScore } from '../bodymap.js';
import { store, TEMPLATES } from '../store.js';
import { CATALOG } from '../data.js';
import { fmtVol,
  tr, exName, sortByName, favoritesFirst, fmtDate, fmtDateTime, fmtDuration, fmtW, fmtVolume, fmtKcal, plural, isCardio, isTimed, equipmentOf, catalogEntry,
  catalogGroup, GROUP_KEYS, EQUIPMENT_KEYS, groupLabel, equipmentLabel, groupColor, fmtSet,
} from '../core.js';
import { sessionStats, weekdayShort, daysLabel } from '../stats.js';
import { exerciseIconHtml, muscleIconHtml, exerciseImageUrl } from '../pictograms.js';
import { h, icon, iconBtn, chip, dialog, textField, groupHeader, muted, toast, showHint, hintUsed, undoToast, categoryRow } from '../ui.js';
import { nav, go, rerender, openDialog, closeDialog, goBack, update } from '../nav.js';
import { screen, fab } from '../layout.js';
import { supersetHead, supersetGroups, groupedWorkout, keepLinks } from '../supersets.js';
import { favoriteBtn, favoritesHeader, nameWithStar, isFav } from '../favorites.js';

import { filterActions, filterBar, matches, noFilterResults, filterActive } from '../filter.js';

const sortedGroups = names => [...new Set(names.map(n => store.groupOf(n)))].sort((a, b) => GROUP_KEYS.indexOf(a) - GROUP_KEYS.indexOf(b));

// ---------- Tab „Workouts“ ----------

/** Fester Eintrag „Freies Training“ oben in der Workout-Liste (wie FreeTrainingCard in WorkoutScreens.kt). */
function freeTrainingCard(onStartWorkout) {
  const lastSession = lastFreeSession();
  const last = lastSession?.start;
  const lastNames = lastSession ? [...new Set(lastSession.exercises)] : [];
  return h('div', { class: 'card workout-card free-card', role: 'button', tabindex: 0, onclick: () => go({ free: true }) },
    h('span', { class: 'free-icon' }, icon('dumbbell', 26)),
    h('div', { class: 'grow' },
      h('h2', null, tr('Freies Training', 'Free workout')),
      h('small', null, tr('Einzelne Übungen ohne festen Plan', 'Single exercises without a fixed plan')),
      h('small', { class: 'free-meta' }, plural(store.exercises.length, tr('Übung', 'exercise'), tr('Übungen', 'exercises')) +
        (last ? tr(' · zuletzt ', ' · last ') + fmtDate(last) : '')),
      // Vorschau: was letztes Mal dran war
      lastNames.length ? h('small', { class: 'free-meta' }, tr('Zuletzt: ', 'Last: ') + lastNames.slice(0, 4).map(exName).join(', ') + (lastNames.length > 4 ? ' …' : '')) : null),
    // ▶ startet das freie Training (leer, Übungen kommen im Training dazu)
    onStartWorkout ? h('button', {
      class: 'play-btn', 'aria-label': tr('Training starten', 'Start workout'),
      onclick: e => { e.stopPropagation(); onStartWorkout(FREE_WORKOUT_ID); },
    }, icon('play', 24)) : icon('chevron'));
}

export function workoutList(onStartWorkout) {
  if (!store.workouts.length) {
    return h('div', { class: 'pad stack' },
      freeTrainingCard(onStartWorkout),
      h('p', null, tr('Noch keine Workouts. Ein Workout fasst mehrere Übungen zusammen, z. B. „Push-Tag“ oder „Beine“.',
        'No workouts yet. A workout groups several exercises, e.g. "Push day" or "Legs".')),
      h('div', null, h('button', { class: 'btn', onclick: newWorkoutDialog }, tr('Workout anlegen', 'Create workout'))));
  }
  return h('div', { class: 'stack pad list-bottom' }, freeTrainingCard(onStartWorkout), store.workouts.map(w => {
    const last = store.sessionsFor(w.id)[0];
    // div statt button, weil ▶ darin ein eigener Knopf ist (Knöpfe dürfen nicht verschachtelt sein)
    return h('div', { class: 'card workout-card', role: 'button', tabindex: 0, onclick: () => go({ workoutId: w.id }) },
      h('div', { class: 'grow' },
        h('h2', null, w.name),
        // Trainingstage laut Plan, z. B. „📅 Mo · Do“
        w.days?.length ? h('small', { class: 'accent plan-days' }, '📅 ' + daysLabel(w.days)) : null,
        h('small', { class: 'muted' }, plural(w.exercises.length, tr('Übung', 'exercise'), tr('Übungen', 'exercises')) +
          (last ? tr(' · zuletzt ', ' · last ') + fmtDate(last.start) : '')),
        w.exercises.length ? h('div', { class: 'icon-row', html: sortedGroups(w.exercises).map(g => muscleIconHtml(g, 28)).join('') }) : null),
      // ▶ startet das Training direkt (wie WorkoutList in WorkoutScreens.kt)
      w.exercises.length && onStartWorkout ? h('button', {
        class: 'play-btn tonal', 'aria-label': tr('Training starten', 'Start workout'),
        onclick: e => { e.stopPropagation(); onStartWorkout(w.id); },
      }, icon('play', 24)) : icon('chevron'));
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
    h('div', { class: 'segments' }, [1, 2, 3, 4, 5, 6, 7].map(d =>
      chip(weekdayShort(d), (w.days ?? []).includes(d), () => store.toggleDay(w.id, d)))));
}

// ---------- Ein Workout ----------

// Auswahlmodus „Supersatz bilden“: { id: Workout, sel: Set der angehakten Übungen } oder null
let linking = null;

// Gewählter Reiter je Workout (0 = Übungen, 1 = Überblick), übersteht das Neuzeichnen (wie workoutTab in GymApp)
const workoutTabOf = {};

/** Reiter „Überblick“: Trainingstage, beanspruchte Muskeln (Heatmap), letzte Trainingseinheiten (wie WorkoutOverview) */
function workoutOverview(w, history) {
  return h('div', { class: 'pad stack list-bottom' },
    h('div', { class: 'card pad-m' }, daysRow(w)),
    // Heatmap: welche Muskeln die Übungen dieses Workouts beanspruchen (gewichtet nach Ziel-Sätzen)
    w.exercises.length ? heatCard({
      title: tr('Beanspruchte Muskeln', 'Muscles worked'),
      score: workoutRegionScore(w),
      showSets: false,
      empty: tr('Für diese Übungen sind keine Muskeln hinterlegt.', 'No muscles known for these exercises.'),
      header: muted(tr('Aus den Übungen dieses Workouts, gewichtet nach Ziel-Sätzen (Hilfsmuskel zählt ½).',
        "From this workout's exercises, weighted by target sets (secondary counts ½)."), 'small'),
    }) : null,
    h('h2', { class: 'section' }, tr('Letzte Trainingseinheiten', 'Recent sessions')),
    history.length
      ? h('div', { class: 'stack' }, history.map(t => {
        const s = sessionStats(store, t);
        return h('button', { class: 'card row-card', onclick: () => go({ summaryId: t.id }) },
          h('div', { class: 'grow' },
            h('b', null, fmtDateTime(t.start)),
            h('small', { class: 'muted' }, `${s.percent}\u00A0% · ${fmtDuration(t.end - t.start)} · ` +
              plural(s.sets, tr('Satz', 'set'), tr('Sätze', 'sets')) +
              (s.volume > 0 ? ` · ${fmtVol(s.volume)}` : '') + (s.kcal != null ? ` · 🔥 ${fmtKcal(s.kcal)}\u00A0kcal` : ''))),
          s.records > 0 ? h('span', null, `🏆 ${s.records}`) : null,
          icon('chevron'));
      }))
      : muted(tr('Mit diesem Workout wurde noch nicht trainiert.', 'No sessions with this workout yet.')));
}

/** running = ein (minimiertes) Training mit diesem Workout läuft → „Training fortsetzen“ */
export function workoutScreen(w, onStart, running = false) {
  const history = store.sessionsFor(w.id);
  const deleteDialog = () => openDialog(() => dialog(tr('Workout löschen?', 'Delete workout?'),
    h('p', null, tr(`„${w.name}“ wird gelöscht. Die Übungen und ihre Sätze bleiben erhalten.`,
      `"${w.name}" will be deleted. The exercises and their sets are kept.`)),
    [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      { label: tr('Löschen', 'Delete'), danger: true, onClick: () => { closeDialog(); store.deleteWorkout(w.id); goBack(); } },
    ], closeDialog));

  const tab = workoutTabOf[w.id] ?? 0;
  const sel = linking?.id === w.id && tab === 0 ? linking.sel : null; // Auswahlmodus aktiv?
  let content, bottom = null;
  if (tab === 1) {
    content = workoutOverview(w, history);
  } else if (!w.exercises.length) {
    content = h('div', { class: 'pad stack' },
      h('p', null, tr('Dieses Workout hat noch keine Übungen.', 'This workout has no exercises yet.')),
      h('div', null, h('button', { class: 'btn', onclick: () => go({ pick: true }) }, tr('Übungen auswählen', 'Choose exercises'))));
  } else {
    const groups = supersetGroups(w);
    const list = h('div', { class: 'ex-list' }, w.exercises.map(name => exerciseRow(w, name, groups, sel)));
    if (!sel) enableDrag(list, w);
    if (sel) bottom = h('div', { class: 'pick-bar link-bar' },
      h('p', { class: 'link-hint' }, tr('Tippe die Übungen an, die du abwechselnd machen willst (mind. 2). Sie rücken in der Liste zusammen.',
        'Tap the exercises you want to alternate (at least 2). They will be moved next to each other.')),
      h('div', { class: 'link-buttons' },
        h('button', { class: 'btn outline', onclick: () => { linking = null; rerender(); } }, tr('Abbrechen', 'Cancel')),
        h('button', {
          class: 'btn', disabled: sel.size < 2,
          onclick: () => { linking = null; store.linkSuperset(w.id, groupedWorkout(w, sel)); },
        }, tr('Verbinden', 'Link') + (sel.size ? ` (${sel.size})` : ''))));
    content = h('div', { class: 'pad list-bottom' },
      // Bedienhinweis nur, bis einmal umsortiert wurde (Hints)
      !sel && showHint('drag') ? muted(tr('Tipp: Am Griff rechts ziehen (oder Zeile gedrückt halten), um die Reihenfolge zu ändern.',
        'Tip: drag the handle on the right (or long-press a row) to change the order.'), 'small') : null,
      list,
      // Beschrifteter Knopf statt nur Stift oben (wie WorkoutScreen.kt)
      sel ? null : h('button', { class: 'btn outline wide add-exercises', onclick: () => go({ pick: true }) }, icon('add'), tr('Übungen hinzufügen / bearbeiten', 'Add / edit exercises')),
      // Supersatz bilden → Auswahlmodus (wie in WorkoutScreens.kt)
      !sel && w.exercises.length >= 2
        ? h('button', { class: 'btn outline wide add-exercises superset-btn', onclick: () => { linking = { id: w.id, sel: new Set() }; rerender(); } },
          tr('🔗 Supersatz bilden', '🔗 Create superset'))
        : null);
  }

  return screen({
    title: w.name, back: true, key: `workout-${w.id}-${tab}`,
    // Zurück beendet zuerst den Auswahlmodus
    onBack: () => { if (sel) { linking = null; rerender(); } else goBack(); },
    bottom,
    top: h('div', { class: 'tab-row' }, [tr('Übungen', 'Exercises'), tr('Überblick', 'Overview')].map((label, i) =>
      h('button', { class: ['tab', i === tab && 'selected'], onclick: () => { workoutTabOf[w.id] = i; rerender(); } }, label))),
    actions: [
      iconBtn('copy', () => { const c = store.duplicateWorkout(w.id); if (c) { update({ workoutId: c.id }); toast(tr('Kopie angelegt', 'Copy created')); } }, { label: tr('Kopieren', 'Duplicate') }),
      iconBtn('delete', deleteDialog, { label: tr('Löschen', 'Delete') }),
    ],
    content,
    fab: w.exercises.length && !sel ? fab(running ? tr('Training fortsetzen', 'Resume workout') : tr('Training starten', 'Start workout'), 'play', onStart) : null,
  });
}

/** Eine Übung im Workout als Listenzeile: Bild, Name, letzter Satz, Ziel-Pille (antippbar), Griff (wie WorkoutExerciseRow in WorkoutScreens.kt) */
function exerciseRow(w, name, groups, sel) {
  const g = store.groupOf(name), last = store.setsFor(name)[0], target = store.targetFor(w, name);
  const sub = last ? tr('Zuletzt ', 'Last ') + fmtSet(last, isCardio(g)) : '–';
  const group = groups.find(gr => gr.includes(name));
  return h('button', {
    class: ['ex-row', group && !sel && 'superset', sel?.has(name) && 'selected'], 'data-name': name, style: `--tint:${groupColor(g)}`,
    onclick: e => {
      if (sel) { sel.has(name) ? sel.delete(name) : sel.add(name); rerender(); return; } // Auswahlmodus: an-/abhaken
      if (e.currentTarget.dataset.justDragged || e.target.closest('.drag-handle')) return; // nach dem Ziehen / am Griff nicht öffnen
      if (e.target.closest('.target-pill')) { targetDialog(w, name); return; }
      if (e.target.closest('.superset-unlink')) { // Supersatz auflösen, mit Rückgängig
        const old = [...(w.links ?? [])];
        store.setLinks(w.id, old.filter(x => !group.includes(x)));
        undoToast(tr('Supersatz aufgelöst', 'Superset unlinked'), () => store.setLinks(w.id, old));
        return;
      }
      go({ exercise: name });
    },
  },
  // Kopf „🔗 Supersatz · Auflösen“ oben in der ersten Zeile der Gruppe (nicht als eigenes Element, sonst stört es das Ziehen)
  group && group[0] === name && !sel ? supersetHead(group.length) : null,
  h('span', { html: exerciseIconHtml(name, g, 44) }),
  h('div', { class: 'grow' }, h('b', null, exName(name)), h('small', null, sub),
    // Ziel-Sätze immer sichtbar („Ziel: –“ = optional) und direkt antippbar (span, da die Zeile selbst ein button ist)
    h('span', { class: 'target-pill', role: 'button' }, tr('Ziel: ', 'Goal: ') + (target === 0 ? '–' : targetLabel(target, g)) + ' ✎')),
  sel ? h('span', { class: ['select-box', sel.has(name) && 'on'] }, sel.has(name) ? '✓' : '')
    : h('span', { class: 'drag-handle', 'aria-label': tr('Ziehen', 'Drag') }, icon('drag')));
}

/** Ziel-Sätze einer Übung direkt aus der Workout-Liste ändern (wie der Ziel-Dialog in WorkoutExerciseRow) */
function targetDialog(w, name) {
  openDialog(() => {
    const g = store.groupOf(name), target = store.targetFor(w, name);
    return dialog(exName(name), targetStepper(w, name, target, g), [{ label: 'OK', onClick: closeDialog }], closeDialog);
  });
}

/** „Ziel-Sätze  − 3 Sätze +“ (wie TargetStepper in WorkoutScreens.kt) */
function targetStepper(w, name, target, g) {
  return h('div', { class: 'pick-bar-row' },
    h('div', { class: 'grow' }, h('b', null, tr('Ziel-Sätze', 'Target sets')), muted('0 = optional', 'small')),
    h('span', { class: 'stepper' },
      h('button', { class: 'step tonal', onclick: () => store.setTarget(w.id, name, target - 1), disabled: target <= 0 }, '−'),
      h('span', { class: 'step-value wide' }, targetLabel(target, g)),
      h('button', { class: 'step tonal', onclick: () => store.setTarget(w.id, name, target + 1), disabled: target >= 20 }, '+')));
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
      hintUsed('drag');
      row.style.transition = 'transform .15s ease-out';
      row.style.transform = '';
      row.classList.remove('dragging');
      const old = [...workout.exercises];
      workout.exercises.splice(0, workout.exercises.length, ...rows().map(r => r.dataset.name));
      workout.links = keepLinks(old, workout.exercises, workout.links); // Supersätze: nur unveränderte Paare bleiben
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

/** „optional“ / „3 Sätze“ / bei Cardio „2×“ */
const targetLabel = (target, g) => target === 0 ? 'optional' : isCardio(g) ? `${target}×` : plural(target, tr('Satz', 'set'), tr('Sätze', 'sets'));

/**
 * Workout bearbeiten: schlichte Liste – oben die Übungen des Workouts, darunter alle übrigen.
 * Antippen öffnet workoutExercisePage (wie WorkoutPickerScreen in WorkoutScreens.kt).
 */
export function workoutPickerScreen(w) {
  const all = [...CATALOG.map(c => c.name), ...store.customNames.filter(n => !catalogEntry(n))]; // inkl. aus der Liste entfernter
  // Erst Kategorien, dann deren Übungen (wie WorkoutPickerScreen in WorkoutScreens.kt); mit Suche/Filter direkt alle Treffer
  const f = nav.pickFilter, group = f.groups.size === 1 ? [...f.groups][0] : null; // angetippte Kategorie = Filter-Chip (sichtbar, abwählbar)
  const overview = !filterActive(f);
  const visible = all.filter(n => matches(f, n, store.groupOf(n)));
  const row = name => {
    const g = store.groupOf(name);
    return h('button', { class: 'pick-row', onclick: () => go({ pickDetail: name }) },
      h('span', { html: exerciseIconHtml(name, g, 40) }),
      h('span', { class: 'grow' }, h('span', null, nameWithStar(name)),
        w.exercises.includes(name) ? h('small', { class: 'accent pick-target' }, '✓ ' + targetLabel(store.targetFor(w, name), g)) : null),
      h('span', { class: 'muted' }, icon('chevron')));
  };
  let content;
  if (!visible.length) content = noFilterResults('pick');
  else if (overview) {
    content = h('div', { class: 'pad' },
      w.exercises.length ? [h('h2', { class: 'section' }, tr('In diesem Workout', 'In this workout') + ` (${w.exercises.length})`), w.exercises.map(row)] : null,
      h('h2', { class: 'section' }, tr('Übung hinzufügen', 'Add exercise')),
      muted(tr('Wähle eine Kategorie – oder suche oben direkt nach einer Übung.', 'Pick a category – or search for an exercise at the top.'), 'small'),
      // Favoriten (★) direkt zum Antippen, vor den Kategorien (wie WorkoutPickerScreen)
      (() => {
        const favs = sortByName(visible.filter(isFav), exName);
        return favs.length ? [favoritesHeader(), favs.map(row), h('h2', { class: 'section' }, tr('Kategorien', 'Categories'))] : null;
      })(),
      h('div', { class: 'category-list' }, GROUP_KEYS.map(g => {
        const names = visible.filter(n => store.groupOf(n) === g);
        return names.length ? categoryRow(g, groupLabel(g), muscleIconHtml(g, 44), groupColor(g), names.length,
          names.filter(n => w.exercises.includes(n)).length, () => { f.groups = new Set([g]); go({ pickGroup: g }); }) : null;
      })));
  } else {
    content = h('div', { class: 'pad' },
      muted(tr('Tippe auf eine Übung für Infos, Ziel-Sätze und zum Hinzufügen.', 'Tap an exercise for info, target sets and to add it.'), 'small'),
      GROUP_KEYS.map(g => {
        const names = favoritesFirst(sortByName(visible.filter(n => store.groupOf(n) === g), exName), isFav); // alphabetisch, Favoriten zuerst
        return names.length ? [group == null ? groupHeader(g) : null, names.map(row)] : null;
      }));
  }
  return screen({
    title: group ? groupLabel(group) : tr('Übungen: ', 'Exercises: ') + w.name, back: true, key: 'pick-' + w.id + '-' + (group ?? ''),
    actions: filterActions('pick'),
    top: filterBar('pick', sortedGroups(all), EQUIPMENT_KEYS),
    content,
  });
}

/**
 * Übung beim Bearbeiten eines Workouts: wie der Reiter „Info“ der Übungsdetails; unten eine feste Leiste
 * mit „Zum Workout hinzufügen“ bzw. Ziel-Sätzen + Entfernen (wie WorkoutExercisePage in WorkoutScreens.kt).
 */
export function workoutExercisePage(w, name) {
  const g = store.groupOf(name), added = w.exercises.includes(name), target = store.targetFor(w, name);
  const m = musclesOf(name);
  const bar = added
    ? h('div', { class: 'pick-bar' },
      targetStepper(w, name, target, g),
      h('button', { class: 'btn text danger pick-remove', onclick: () => store.toggleInWorkout(w.id, name) }, tr('Aus dem Workout entfernen', 'Remove from workout')))
    : h('div', { class: 'pick-bar' },
      h('button', {
        class: 'btn wide',
        // Standard-Ziel beim Hinzufügen: 3 Sätze (Cardio 1×) – lässt sich direkt danach ändern
        onclick: () => { store.toggleInWorkout(w.id, name); store.setTarget(w.id, name, isCardio(g) ? 1 : 3); },
      }, icon('add'), tr('Zum Workout hinzufügen', 'Add to workout')));
  return screen({
    title: exName(name), back: true, key: `pickex-${name}`, backdrop: exerciseImageUrl(name),
    actions: [favoriteBtn(name)],
    titleIcon: exerciseIconHtml(name, g, 32),
    content: h('div', { class: 'stack pad' },
      m ? muscleSideCard('BOTH', levelsOf(m), true) // Vorder- und Rückseite nebeneinander
        : muted(tr('Noch keine Muskeln festgelegt.', 'No muscles set yet.')),
      wikiFor(name) ? h('div', { class: 'wiki-inline' }, wikiContent(name, { header: false, muscles: false })) : null),
    bottom: h('div', { class: 'bottom-area' }, bar),
  });
}

// ---------- Katalog ----------

export function catalogScreen() {
  const f = nav.catalogFilter;
  // Erst Kategorien, dann deren Übungen (wie CatalogScreen.kt); mit Suche/Filter direkt alle Treffer
  const group = f.groups.size === 1 ? [...f.groups][0] : null; // angetippte Kategorie = Filter-Chip (sichtbar, abwählbar)
  const overview = !filterActive(f);
  const visible = CATALOG.filter(c => matches(f, c.name, c.group));
  const custom = favoritesFirst(sortByName(store.customNames.filter(n => matches(f, n, store.groupOf(n)))), isFav); // Favoriten zuerst, inkl. aus der Liste entfernter → wieder hinzufügbar
  const byGroup = GROUP_KEYS.map(g => [g, favoritesFirst(sortByName(visible.filter(c => c.group === g), c => exName(c.name)), isFav, c => c.name)]).filter(([, l]) => l.length);

  let content;
  if (!visible.length && !custom.length) content = noFilterResults('catalog');
  else if (overview) {
    content = h('div', { class: 'pad' },
      muted(tr('Wähle eine Kategorie – oder suche oben direkt nach einer Übung.', 'Pick a category – or search for an exercise at the top.'), 'small'),
      h('div', { class: 'category-list' }, GROUP_KEYS.map(g => {
        const names = [...visible.filter(c => c.group === g).map(c => c.name), ...custom.filter(n => store.groupOf(n) === g)];
        return names.length ? categoryRow(g, groupLabel(g), muscleIconHtml(g, 44), groupColor(g), names.length,
          names.filter(n => store.hasExercise(n)).length, () => { f.groups = new Set([g]); go({ catalogGroup: g }); }) : null;
      })));
  } else {
    content = h('div', { class: 'pad' },
      muted(tr('Tippe auf eine Übung, um sie zu deiner Liste hinzuzufügen – nochmal tippen entfernt sie wieder. Fehlt etwas? Oben mit + eine eigene Übung anlegen.',
        'Tap an exercise to add it to your list – tap again to remove it. Missing one? Create your own with + at the top.'), 'small'),
      custom.length ? [
        h('h2', { class: 'section' }, tr('Eigene Übungen', 'Custom exercises')),
        custom.map(n => {
          const g = store.groupOf(n), added = store.hasExercise(n);
          return h('div', { class: 'cat-row', role: 'button', onclick: () => (added ? removeAgain(n) : store.addExercise(n)) },
            h('span', { html: exerciseIconHtml(n, g, 40) }),
            h('div', { class: 'grow' }, h('span', null, nameWithStar(n, n)),
              h('small', { class: 'muted' }, [groupLabel(g), equipmentOf(n) && equipmentLabel(equipmentOf(n)), isTimed(n) && tr('Zeit', 'Time')].filter(Boolean).join(' · '))),
            added ? h('span', { class: 'accent' }, icon('check')) : h('span', { class: 'add-mark' }, '+'));
        }),
      ] : null,
      byGroup.map(([g, list]) => [group == null ? groupHeader(g) : null, list.map(c => {
        const added = store.hasExercise(c.name);
        return h('div', { class: 'cat-row', role: 'button', onclick: () => (added ? removeAgain(c.name) : store.addExercise(c.name, c.group)) }, // nochmal tippen = entfernen
          h('span', { html: exerciseIconHtml(c.name, g, 40) }),
          h('div', { class: 'grow' }, h('span', null, nameWithStar(c.name)), h('small', { class: 'muted' }, equipmentLabel(c.equipment))),
          wikiButton(c.name),
          h('span', { class: ['add-mark', added && 'accent'] }, added ? '✓' : '+'));
      })]));
  }

  return screen({
    title: group ? groupLabel(group) : tr('Übungskatalog', 'Exercise catalog'), back: true, key: 'catalog-' + (group ?? ''),
    actions: [iconBtn('add', customExerciseDialog, { label: tr('Eigene Übung', 'Custom exercise') }), ...filterActions('catalog')],
    top: filterBar('catalog', GROUP_KEYS.filter(g => CATALOG.some(c => c.group === g)), EQUIPMENT_KEYS),
    content,
  });
}

export function customExerciseDialog() {
  const st = { name: '', group: 'BRUST', equipment: null, timed: false, muscles: { primary: [], secondary: [] } };
  openDialog(() => {
    const exists = st.name.trim() !== '' && (store.hasExercise(st.name) || catalogGroup(st.name.trim()) != null);
    return dialog(tr('Eigene Übung', 'Custom exercise'), [
      textField({ id: 'custom-name', label: tr('Name', 'Name'), value: st.name, error: exists ? tr('Gibt es schon', 'Already exists') : null, oninput: v => { st.name = v; rerender(); } }),
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

/** ✓ im Katalog antippen: wieder aus „Meine Übungen“ entfernen, mit „Rückgängig“ (wie removeAgain in CatalogScreen.kt) */
function removeAgain(name) {
  const index = store.removeFromList(name);
  undoToast(tr(`„${exName(name)}“ entfernt – bleibt in Workouts und Verlauf`, `"${exName(name)}" removed – stays in workouts and history`),
    () => store.restoreToList(name, index));
}

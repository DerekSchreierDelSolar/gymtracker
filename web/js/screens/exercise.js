// Übungs-Bausteine und Übungsdetail – entspricht ExerciseDetail.kt, Progression.kt (UI) und ExerciseTile.

import { setHelpers } from '../warmup.js';
import { rirLabel, rirChips, rirPrompt, oneRmTable } from '../effort.js';
import { wikiFor, wikiContent } from '../wiki.js';
import { musclesOf, muscleEditDialog, muscleSideCard, levelsOf } from '../bodymap.js';
import { store } from '../store.js';
import { fmtKg, fmtVol, fmtWU, wUnit, toUnit, parseW, isLb,
  tr, exName, fmtSet, fmtW, fmtVolume, fmtDate, fmtDateTime, fmtTime, fmtSeconds, plural, isCardio, isTimed, equipmentOf,
  oneRepMax, parseNum, parseIntStrict, startOfToday, groupColor, catalogGroup, dayKey, monthIndex, startOfWeek,
  fmtShortDate, completionColor, SET_TYPES, setTypeOf, setTypeLabel, setTypeShort, setLabels, isWork, isTop,
  appLanguage,
} from '../core.js';
import { progressionSuggestion } from '../stats.js';
import { exerciseIconHtml, exerciseImageUrl } from '../pictograms.js';
import { h, icon, iconBtn, chip, dialog, textField, progressChart, onTick, muted, undoToast, showHint, hintUsed } from '../ui.js';
import { nav, rerender, openDialog, closeDialog, goBack } from '../nav.js';
import { screen } from '../layout.js';
import { favoriteBtn, favoriteMark } from '../favorites.js';

// ---------- Kachel ----------

/** Kompakte Kachel: Symbol, Name (2 Zeilen), letzter Eintrag. */
export function exerciseTile(name, group, onClick, extra = {}) {
  const last = store.setsFor(name)[0];
  const tile = h('button', {
    class: ['tile', extra.cls], 'data-name': name,
    onclick: e => { if (tile.dataset.longPressed) delete tile.dataset.longPressed; else onClick(e); }, // nach langem Drücken kein Öffnen
    style: `--tint:${groupColor(group)}`,
  },
  h('span', { html: exerciseIconHtml(name, group, 44) }),
  tileName(exName(name)),
  h('small', null, last ? fmtSet(last, isCardio(group)) : '–'),
  extra.favorite ? favoriteMark() : null);
  if (extra.onLongPress) onLongPress(tile, extra.onLongPress);
  return tile;
}

/** „Bankdrücken (Langhantel)“ → ['Bankdrücken', 'Langhantel'] (wie splitExerciseName in HomeScreen.kt). */
export function splitExerciseName(display) {
  const i = display.lastIndexOf(' (');
  if (i <= 0 || !display.endsWith(')')) return [display, null];
  return [display.slice(0, i), display.slice(i + 2, -1)];
}

/** Kachel-Name: Grundübung + darunter klein das Gerät; feste Höhe, damit alle Kacheln gleich hoch sind. */
function tileName(display) {
  const [base, detail] = splitExerciseName(display);
  return h('span', { class: 'tile-name', lang: appLanguage },
    // Ein einzelnes Wort bleibt in einer Zeile und wird dafür kleiner (CSS: --chars, wie autoSize in ExerciseTile)
    base.includes(' ') ? h('span', { class: 'tile-base' }, base)
      : h('span', { class: 'tile-base one-word', style: `--chars:${base.length}` }, base), detail ? h('small', { class: 'tile-detail' }, detail) : null);
}

/** Gedrückt halten (0,5 s, ohne zu wischen) → fn; der Klick danach öffnet nichts (siehe exerciseTile; wie combinedClickable). */
function onLongPress(el, fn) {
  let timer = null, x = 0, y = 0;
  const cancel = () => { clearTimeout(timer); timer = null; };
  el.addEventListener('pointerdown', e => {
    delete el.dataset.longPressed; x = e.clientX; y = e.clientY;
    timer = setTimeout(() => { el.dataset.longPressed = '1'; navigator.vibrate?.(20); fn(); }, 500);
  });
  el.addEventListener('pointermove', e => { if (timer && Math.hypot(e.clientX - x, e.clientY - y) > 10) cancel(); });
  ['pointerup', 'pointercancel', 'pointerleave'].forEach(t => el.addEventListener(t, cancel));
  el.addEventListener('contextmenu', e => e.preventDefault());
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
      class: 'btn text', onclick: () => { inputState(name).weight = fmtWU(s.weight); rerender(); },
    }, tr('Übernehmen', 'Apply')) : null);
}

// ---------- Eingabe eines Satzes ----------

/** Eingabewerte je Übung (bleiben erhalten, solange die App offen ist); anfangs leer, nach jedem Satz geleert. */
const inputs = new Map();

/** Letzter Aufwärmsatz der Übung als Feldtext (nur für den ersten Satz eines Trainings), sonst leer */
function lastWarmupText(name) {
  const wu = store.setsFor(name).find(x => x.type === 'WARMUP');
  return { weight: wu ? fmtWU(wu.weight) : '', reps: wu ? String(wu.reps) : '' };
}

function inputState(name) {
  if (!inputs.has(name)) inputs.set(name, { weight: '', reps: '', watchStart: null, type: 'NORMAL' });
  return inputs.get(name);
}

const mmss = sec => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;

/**
 * kg + Wdh. (bzw. Minuten bei Cardio, Sekunden bei Zeitübungen) mit Button.
 * onAdded: wird nach dem Eintragen aufgerufen (Trainingsmodus startet damit die Pause).
 * warmupKey: im Training gesetzt (= Trainingsstart), wenn die Übung dort noch keinen Satz hat → erster Satz
 * „Aufwärmen“ (wie startWithWarmup in ExerciseDetail.kt); einmal je Training, danach bleibt die Auswahl des Nutzers.
 * Im Übungsdetail (freies Training) = „day-JJJJMMTT“, solange die Übung heute noch keinen Satz hat.
 */
export function setInput(name, onAdded = () => {}, warmupKey = null) {
  const cardio = isCardio(store.groupOf(name));
  const timed = isTimed(name);
  const st = inputState(name);
  // Neues Training: Typ Aufwärmen, Felder mit dem letzten Aufwärmsatz vorbelegen (einmal je Training)
  if (warmupKey != null && st.warmupKey !== warmupKey && !cardio && !timed) {
    st.type = 'WARMUP'; st.warmupKey = warmupKey;
    Object.assign(st, lastWarmupText(name));
  }
  const changeType = t => { st.type = t; };
  const showWeight = !cardio && (!timed || equipmentOf(name) !== 'KOERPER');
  const idBase = 'in-' + name.replace(/\W+/g, '_');

  const add = () => {
    const w = cardio ? 0 : parseW(st.weight) ?? (timed ? 0 : null);
    const r = parseIntStrict(st.reps);
    if (w == null || w < 0 || r == null || r <= 0) {
      // Felder rot + Hinweis (wie SetInput in ExerciseDetail.kt), dazu kurzes Wackeln
      st.tried = true;
      rerender();
      document.getElementById(idBase + '-row')?.classList.add('shake');
      return;
    }
    // Ungewöhnlicher Wert (Tippfehler?) → erst nachfragen
    const odd = implausibleSet(name, w, r);
    if (odd) {
      openDialog(() => dialog(tr('Wirklich eintragen?', 'Log this set?'), h('p', null, odd), [
        { label: tr('Korrigieren', 'Fix it'), onClick: closeDialog },
        { label: tr('Ja, eintragen', 'Yes, log it'), onClick: () => { closeDialog(); save(w, r); } },
      ], closeDialog));
      return;
    }
    save(w, r);
  };
  const save = (w, r) => {
    st.tried = false;
    st.watchStart = null;
    const added = store.addSet(name, w, r, st.type ?? 'NORMAL');
    // Nach einem Arbeitssatz: freiwillige Abfrage „Wdh. in Reserve“ (effort.js)
    st.rirFor = (st.type ?? 'NORMAL') !== 'WARMUP' && !cardio && !timed ? added.id : null; // Speichern hat schon neu gezeichnet → unten erneut
    st.weight = ''; st.reps = ''; // nach jedem Satz leeren
    st.type = 'NORMAL'; // nach jedem Satz wieder „Normal“
    onAdded();
    rerender();
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
      // Satz-Typ: antippen = nächster Typ (N → A → D → V); bei Cardio nicht sinnvoll
      cardio ? null : setTypeButton(st.type ?? 'NORMAL', () => {
        changeType(SET_TYPES[(SET_TYPES.indexOf(st.type ?? 'NORMAL') + 1) % SET_TYPES.length]);
        rerender();
      }),
      showWeight ? textField({
        id: idBase + '-w', label: wUnit(), value: st.weight, inputmode: 'decimal',
        oninput: v => { st.weight = v; st.tried = false; }, onenter: add,
        cls: st.tried && ((parseW(st.weight) == null && !timed) || parseW(st.weight) < 0) ? 'error' : '',
      }) : null,
      textField({
        id: idBase + '-r', label: cardio ? tr('Minuten', 'Minutes') : timed ? tr('Sekunden', 'Seconds') : tr('Wdh.', 'Reps'),
        value: st.reps, inputmode: 'numeric', oninput: v => { st.reps = v; st.tried = false; }, onenter: add,
        cls: st.tried && !((parseIntStrict(st.reps) ?? 0) > 0) ? 'error' : '',
      }),
      h('button', { class: 'btn plus', onclick: add, 'aria-label': cardio ? tr('Eintragen', 'Log') : tr('Satz hinzufügen', 'Add set') }, '+')),
    // Schnell ändern ohne Tastatur: −/+ unter den Feldern (Gewicht ±2,5 kg; Wdh./Minuten ±1, Sekunden ±5) – wie SetInput in ExerciseDetail.kt
    stepRow(cardio, showWeight, timed, st),
    st.tried ? h('small', { class: 'input-error' }, cardio ? tr('Bitte Minuten eintragen.', 'Please enter minutes.')
      : timed ? tr('Bitte Sekunden eintragen (oder Stoppuhr nutzen).', 'Please enter seconds (or use the stopwatch).')
        : tr('Bitte Gewicht und Wiederholungen eintragen.', 'Please enter weight and reps.')) : null,
    // Aufwärm-Rampe (warmup.js)
    showWeight && !timed ? setHelpers(name, st.weight, (w, r) => { st.weight = fmtWU(w); st.reps = String(r); st.type = 'WARMUP'; rerender(); }) : null,
    st.rirFor != null ? rirPrompt(n => { store.setRir(st.rirFor, n); st.rirFor = null; rerender(); }, () => { st.rirFor = null; rerender(); }) : null);
}

/**
 * Tippfehler-Schutz (wie implausibleSet in ExerciseDetail.kt): Hinweistext bei ungewöhnlichem Satz, sonst null.
 * Gewicht: mehr als 1,5× und mindestens 10 kg über dem bisherigen Höchstgewicht aus Arbeitssätzen (ohne Aufwärmen; ohne Verlauf: über 300 kg).
 */
export function implausibleSet(name, weight, reps) {
  const cardio = isCardio(store.groupOf(name)), timed = isTimed(name);
  if (cardio) return reps > 300 ? tr(`${reps} Minuten sind über 5 Stunden. Tippfehler?`, `${reps} minutes is over 5 hours. Typo?`) : null;
  if (timed && reps > 1800) return tr(`${reps} Sekunden sind über 30 Minuten. Tippfehler?`, `${reps} seconds is over 30 minutes. Typo?`);
  if (!timed && reps > 100) return tr(`${reps} Wiederholungen in einem Satz? Tippfehler?`, `${reps} reps in one set? Typo?`);
  // nur Arbeitssätze: nach einem leichten Aufwärmsatz ist der Sprung zum Arbeitsgewicht normal
  const best = Math.max(0, ...store.setsOf(name).filter(isWork).map(s => s.weight));
  if (best > 0 && weight > best * 1.5 && weight - best >= 10) return tr(
    `${fmtKg(weight)} ist deutlich mehr als dein bisheriges Höchstgewicht (${fmtKg(best)}). Tippfehler?`,
    `${fmtKg(weight)} is far more than your heaviest so far (${fmtKg(best)}). Typo?`);
  if (best === 0 && weight > 300) return tr(`${fmtKg(weight)}? Tippfehler?`, `${fmtKg(weight)}? Typo?`);
  return null;
}

// ---------- Satz-Zeile + Bearbeiten ----------

/** Umschalter für den Satz-Typ (wie SetTypeButton in ExerciseDetail.kt): Kürzel groß, Name klein. */
function setTypeButton(type, onclick) {
  return h('button', { class: ['set-type-btn', 'st-' + type], onclick, 'aria-label': tr('Satz-Typ', 'Set type') },
    h('b', null, setTypeShort(type) || 'N'), h('small', { style: `--chars: ${setTypeLabel(type).length}` }, setTypeLabel(type)));
}

/** Liste älteste zuerst → Satz-Zeilen neueste zuerst, Arbeitssätze nummeriert, Aufwärmsätze „A“. */
export function setRows(list, cardio) {
  const sorted = list.slice().sort((a, b) => a.time - b.time);
  const labels = setLabels(sorted);
  return sorted.map((s, i) => setRow(s, labels[i], cardio)).reverse();
}

/** Satz-Zeile; ganze Zeile antippen = bearbeiten (kein Stift-Symbol). `label` = „1.“ … bzw. „A“. */
export function setRow(set, label, cardio) {
  const type = setTypeOf(set);
  return h('div', { class: ['set-row', 'tap', !isWork(set) && 'warmup'], role: 'button', onclick: () => { hintUsed('set'); editSetDialog(set, cardio); } },
    h('span', { class: ['set-label', 'st-' + type] }, label),
    h('span', null, ' ' + fmtSet(set, cardio)),
    type === 'DROP' || type === 'FAILURE' ? h('span', { class: ['set-tag', 'st-' + type] }, setTypeLabel(type)) : null,
    set.rir != null ? h('small', { class: 'muted rir-tag' }, ' · ' + rirLabel(set.rir)) : null,
    h('span', { class: 'grow' }),
    h('small', { class: 'muted' }, fmtTime(set.time)));
}

/** Motivierende Zeile mit dem Rekord (wie RecordLine in ExerciseDetail.kt). */
export function recordLine(allSets, cardio, timed) {
  const history = allSets.filter(isTop); // ohne Aufwärm-/Drop-Sätze
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
  const st = { weight: fmtWU(set.weight), reps: String(set.reps), type: setTypeOf(set), rir: set.rir ?? null };
  openDialog(() => {
    const w = cardio ? 0 : parseW(st.weight);
    const r = parseIntStrict(st.reps);
    const valid = w != null && w >= 0 && r != null && r > 0;
    return dialog(tr('Eintrag bearbeiten', 'Edit entry'), [
      muted(fmtDateTime(set.time)),
      h('div', { class: 'row' },
        cardio ? null : textField({ id: 'edit-w', label: wUnit(), value: st.weight, inputmode: 'decimal', oninput: v => { st.weight = v; rerender(); } }),
        textField({
          id: 'edit-r', label: cardio ? tr('Minuten', 'Minutes') : isTimed(set.exercise) ? tr('Sekunden', 'Seconds') : tr('Wdh.', 'Reps'),
          value: st.reps, inputmode: 'numeric', oninput: v => { st.reps = v; rerender(); },
        })),
      // Satz-Typ (nicht bei Cardio)
      cardio ? null : h('div', { class: 'chips wrap' },
        SET_TYPES.map(t => chip(setTypeLabel(t), st.type === t, () => { st.type = t; rerender(); }))),
      // Wdh. in Reserve (optional, nur Kraft mit Wiederholungen) – wie EditSetDialog
      !cardio && !isTimed(set.exercise) && st.type !== 'WARMUP'
        ? [h('b', { class: 'small-title' }, tr('Wdh. in Reserve (optional)', 'Reps in reserve (optional)')), rirChips(st.rir, n => { st.rir = n; rerender(); })] : null,
      h('button', {
        class: 'btn text danger',
        // sofort löschen, unten „Rückgängig“ (wie EditSetDialog in ExerciseDetail.kt)
        onclick: () => { store.deleteSet(set); closeDialog(); undoToast(tr('Satz gelöscht', 'Set deleted'), () => store.restoreSet(set)); },
      }, icon('delete', 18), tr('Eintrag löschen', 'Delete entry')),
    ], [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      { label: tr('Speichern', 'Save'), disabled: !valid, onClick: () => { store.updateSet(set, w, r, st.type, st.rir); closeDialog(); } },
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
      // Freies Training: Übung heute noch nicht gemacht → erster Satz „Aufwärmen“ (einmal je Tag)
      setInput(name, () => {}, history.some(s => dayKey(s.time) === dayKey(Date.now())) ? null : 'day-' + dayKey(Date.now())),
      noteButton(name),
      days.size ? h('div', null, h('h2', { class: 'section' }, tr('Verlauf', 'History')),
        showHint('set') ? muted(tr('Satz antippen zum Bearbeiten', 'Tap a set to edit'), 'small') : null) : null,
      [...days.entries()].map(([day, list]) => historyDayCard(day, list, cardio, timed)),
    ];
  } else if (tab === 1) content = exerciseProgress(history, cardio, timed);
  else {
    // Info: Muskeln (Vorder-/Rückseite) + Wiki
    const m = musclesOf(name);
    const custom = catalogGroup(name) == null;
    content = [
      m ? [muscleSideCard('FRONT', levelsOf(m)), muscleSideCard('BACK', levelsOf(m), true)]
        : muted(tr('Noch keine Muskeln festgelegt.', 'No muscles set yet.')),
      custom ? h('button', { class: 'btn text', onclick: () => muscleEditDialog(name) },
        m ? tr('Muskeln bearbeiten', 'Edit muscles') : tr('Muskeln festlegen', 'Set muscles')) : null,
      wikiFor(name) ? h('div', { class: 'wiki-inline' }, wikiContent(name, { header: false, muscles: false })) : null,
    ];
  }

  return screen({
    title: exName(name), back: true, key: `ex-${name}-${tab}`, backdrop: exerciseImageUrl(name),
    titleIcon: exerciseIconHtml(name, group, 32),
    actions: [favoriteBtn(name), iconBtn('delete', deleteDialog, { label: tr('Löschen', 'Delete') })],
    top: h('div', { class: 'tab-row' }, tabs.map((label, i) =>
      h('button', { class: ['tab', i === tab && 'selected'], onclick: () => { tabOf[name] = i; rerender(); } }, label))),
    content: h('div', { class: 'stack pad' }, content),
  });
}

// ---------- Reiter „Fortschritt“ (wie ExerciseProgress in ExerciseDetail.kt) ----------

const BUCKETS = { DAY: ['Tage', 'Days'], WEEK: ['Wochen', 'Weeks'], MONTH: ['Monate', 'Months'] };
const METRICS = { MAX: ['Max. Gewicht', 'Max weight'], ORM: ['Geschätztes 1RM', 'Estimated 1RM'], VOLUME: ['Volumen', 'Volume'] };

function exerciseProgress(allSets, cardio, timed) {
  const history = allSets.filter(isTop); // Aufwärm- und Drop-Sätze zählen nicht für den Fortschritt
  if (!history.length) return muted(tr('Noch keine Sätze eingetragen.', 'No sets logged yet.'));
  const strength = !cardio && !timed;
  const unit = cardio ? 'min' : timed ? 's' : wUnit();
  const value = l => cardio ? l.reduce((a, s) => a + s.reps, 0)
    : timed ? Math.max(...l.map(s => s.reps))
      : progMetric === 'ORM' ? toUnit(Math.round(Math.max(...l.map(s => oneRepMax(s.weight, s.reps))) * 2) / 2)
        : progMetric === 'VOLUME' ? toUnit(l.reduce((a, s) => a + s.weight * s.reps, 0))
          : toUnit(Math.max(...l.map(s => s.weight)));
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
        `${fmtKg(Math.round(Math.max(...history.map(s => oneRepMax(s.weight, s.reps))) * 2) / 2)}`, 'small'),
      h('div', { class: 'chips wrap' }, Object.entries(METRICS).map(([k, l]) => chip(tr(...l), progMetric === k, () => { progMetric = k; rerender(); }))),
    ] : null,
    h('div', { class: 'chips' }, Object.entries(BUCKETS).map(([k, l]) => chip(tr(...l), progBucket === k, () => { progBucket = k; rerender(); }))),
    values.length >= 2 ? [
      glassify(progressChart(values, title, unit)),
      h('div', { class: 'chart-dates muted' }, h('span', null, fmtShortDate(groups[0][0].time)), h('span', null, fmtShortDate(groups.at(-1)[0].time))),
    ] : muted(tr('Ab zwei Einträgen in dieser Einteilung erscheint hier ein Diagramm.', 'A chart appears once there are two entries for this grouping.')),
    // Prozente vom 1RM (effort.js)
    strength ? oneRmTable(Math.round(Math.max(...history.map(s => oneRepMax(s.weight, s.reps))) * 2) / 2) : null,
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
export function historyDayCard(day, all, cardio, timed) {
  const sumReps = l => l.reduce((a, s) => a + s.reps, 0);
  const list = all.filter(isWork); // Kopfzeile ohne Aufwärmsätze
  return h('div', { class: 'card pad-s glass' },
    h('b', null, `${day} · ` + (cardio
      ? plural(list.length, tr('Einheit', 'session'), tr('Einheiten', 'sessions')) + ` · ${sumReps(list)} ` + tr('Min gesamt', 'min total')
      : timed
        ? plural(list.length, tr('Satz', 'set'), tr('Sätze', 'sets')) + ' · ' + tr('gesamt ', 'total ') + fmtSeconds(sumReps(list))
        : plural(list.length, tr('Satz', 'set'), tr('Sätze', 'sets')) + ` · ${fmtVol(list.reduce((a, s) => a + s.weight * s.reps, 0))} ` + tr('Volumen', 'volume'))),
    all.length && store.noteFor(all[0].exercise, dayKey(all[0].time))
      ? h('p', { class: 'day-note' }, '📝 ' + store.noteFor(all[0].exercise, dayKey(all[0].time))) : null,
    // Neuester Satz oben; Nummer bleibt die Reihenfolge im Training
    setRows(all, cardio));
}

/** „📝 Notiz für heute“ (wie NoteButton in ExerciseDetail.kt): antippen öffnet einen Dialog. */
export function noteButton(name) {
  const today = dayKey(Date.now());
  const note = store.noteFor(name, today);
  const open = () => {
    const st = { text: note ?? '' };
    openDialog(() => dialog(tr('Notiz', 'Note'),
      h('textarea', {
        id: 'note-text', class: 'note-input', rows: 3, value: st.text,
        placeholder: tr('z. B. Schulter zwickt, Sitz Stufe 4 …', 'e.g. shoulder twinge, seat level 4 …'),
        oninput: e => { st.text = e.target.value; },
      }),
      [
        note ? { label: tr('Löschen', 'Delete'), danger: true, onClick: () => { closeDialog(); store.setNote(name, today, ''); } } : null,
        { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
        { label: tr('Speichern', 'Save'), onClick: () => { closeDialog(); store.setNote(name, today, st.text); } },
      ].filter(Boolean), closeDialog));
  };
  return h('button', { class: ['note-btn', note && 'has-note'], onclick: open },
    h('span', null, '📝'), h('span', null, note ?? tr('Notiz für heute hinzufügen', 'Add a note for today')));
}


/** Reihe −/+ unter den Eingabefeldern; Spalten passend zu Typ-Knopf, kg, Wdh. und ＋ (siehe CSS .step-row) */
function stepRow(cardio, showWeight, timed, st) {
  const btn = (label, fn) => h('button', { class: 'step-btn', onclick: () => { fn(); rerender(); } }, label);
  const repStep = timed ? 5 : 1;
  const cols = [cardio ? null : '64px', showWeight ? '1fr' : null, '1fr', '56px'].filter(Boolean).join(' ');
  const pair = (step, change) => h('div', { class: 'step-pair' }, btn('−' + step, () => change(-1)), btn('+' + step, () => change(1)));
  return h('div', { class: 'step-row', style: `grid-template-columns:${cols}` },
    cardio ? null : h('span'),
    showWeight ? pair(isLb() ? '5' : tr('2,5', '2.5'), d => { st.weight = fmtW(Math.max(0, (parseNum(st.weight) ?? 0) + d * (isLb() ? 5 : 2.5))); }) : null,
    pair(String(repStep), d => { st.reps = String(Math.max(1, (parseIntStrict(st.reps) ?? 0) + d * repStep)); }),
    h('span'));
}

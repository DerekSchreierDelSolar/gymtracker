// Trainingsmodus und Trainingsübersicht – entspricht TrainingScreen.kt und SummaryScreen.kt.

import { wikiButton } from '../wiki.js';
import { similarExercises } from '../wizard.js';
import { heatCard, regionScore } from '../bodymap.js';
import { store } from '../store.js';
import {
  tr, exName, fmtSet, fmtW, fmtVolume, fmtDate, fmtDateTime, fmtDuration, fmtShortDate, plural, isCardio, isTimed,
  goalCompletion, completionColor, dayKey, fmtSeconds, isWork, isTop, setTypeShort, parseIntStrict,
} from '../core.js';
import { sessionStats } from '../stats.js';
import { exerciseIconHtml, exerciseImageUrl } from '../pictograms.js';
import {
  h, chip, dialog, statBox, chartCard, horizontalBars, historyBars, percentRing, onTick, muted, icon, iconBtn,
} from '../ui.js';
import { nav, update, go, openDialog, closeDialog, onSwipe, rerender } from '../nav.js';
import { screen } from '../layout.js';
import { setInput, setRows, historyDayCard, recordLine, noteButton } from './exercise.js';

// ---------- Ton, Vibration, Bildschirm an ----------

let audio = null;
/** Muss in einem Tipp-Ereignis aufgerufen werden – iOS erlaubt Ton erst nach einer Nutzeraktion. */
export function unlockAudio() {
  try {
    audio ??= new (window.AudioContext || window.webkitAudioContext)();
    if (audio.state === 'suspended') audio.resume();
  } catch { /* kein Ton verfügbar */ }
}
function beep(freq, ms, at = 0) {
  if (!audio) return;
  const t = audio.currentTime + at;
  const o = audio.createOscillator(), g = audio.createGain();
  o.frequency.value = freq;
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(0.4, t + 0.01);
  g.gain.exponentialRampToValueAtTime(0.0001, t + ms / 1000);
  o.connect(g).connect(audio.destination);
  o.start(t); o.stop(t + ms / 1000 + 0.02);
}
const tick = () => { beep(880, 80); navigator.vibrate?.(40); };
const alarm = () => { beep(1046, 250); beep(1046, 250, 0.4); navigator.vibrate?.([300, 150, 300]); };

let wakeLock = null;
async function keepScreenOn(on) {
  try {
    if (on && !wakeLock && 'wakeLock' in navigator && document.visibilityState === 'visible') {
      wakeLock = await navigator.wakeLock.request('screen');
      wakeLock.addEventListener('release', () => { wakeLock = null; });
    } else if (!on && wakeLock) {
      await wakeLock.release(); wakeLock = null;
    }
  } catch { /* nicht unterstützt */ }
}
// Nach dem Zurückkehren in die App die Sperre erneut anfordern
document.addEventListener('visibilitychange', () => { if (nav.trainingStart != null) keepScreenOn(true); });

// ---------- Pausen-Timer ----------

function startRest() {
  unlockAudio();
  if (store.restSeconds <= 0) return;
  const total = store.restSeconds * 1000;
  update({ restEnd: Date.now() + total, restTotal: total });
}
function adjustRest(delta) {
  if (nav.restEnd == null) return;
  const end = nav.restEnd + delta;
  if (end <= Date.now()) update({ restEnd: null });
  else update({ restEnd: end, restTotal: Math.max(nav.restTotal + delta, end - Date.now()) });
}

// ---------- Drei Kreise pro Übung: Wiederholungen · Pause · Sätze (wie CircleStat in TrainingScreen.kt) ----------

/** Schlanker Kreis (SVG) mit Wert in der Mitte, Beschriftung darunter. set(progress, value, label, color) aktualisiert ihn. */
function circleStat(big, onClick) {
  const size = big ? 104 : 88, sw = 5, r = (size - sw) / 2 - 4, c = 2 * Math.PI * r;
  const el = h('div', { class: 'circle-stat' },
    h('div', { class: ['circle', onClick && 'tap'], style: `width:${size}px;height:${size}px`, onclick: onClick },
      h('span', { html: `<svg width="${size}" height="${size}"><circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" class="c-track" stroke-width="${sw}"/><circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" class="c-arc" stroke-width="${sw}" stroke-linecap="round" transform="rotate(-90 ${size / 2} ${size / 2})"/></svg>` }),
      h('b', { class: big ? 'big' : null })),
    h('small'));
  el.set = (progress, value, label, color) => {
    const p = Math.min(1, Math.max(0, progress));
    const arc = el.querySelector('.c-arc');
    arc.setAttribute('stroke', color);
    arc.setAttribute('stroke-dasharray', `${c * p} ${c}`);
    arc.style.opacity = p > 0 ? 1 : 0;
    el.querySelector('.c-track').setAttribute('stroke', color);
    el.querySelector('b').textContent = value;
    el.querySelector('small').textContent = label;
  };
  return el;
}

/** Wiederholungs-Ziel (wie repsGoal in TrainingScreen.kt): [Wert, Beschriftung] oder null. */
function repsGoal(name, before) {
  const prev = store.setsFor(name).filter(s => s.time < before && isTop(s)); // ohne Aufwärm-/Drop-Sätze
  if (!prev.length) return null;
  const lastDay = dayKey(prev[0].time);
  const last = prev.filter(s => dayKey(s.time) === lastDay);
  if (isCardio(store.groupOf(name))) return [String(last.reduce((a, s) => a + s.reps, 0)), tr('Minuten zuletzt', 'Minutes last time')];
  if (isTimed(name)) return [fmtSeconds(Math.max(...last.map(s => s.reps)) + 10), tr('Zeit-Ziel', 'Time goal')];
  const top = Math.max(...last.map(s => s.weight));
  const atTop = last.filter(s => s.weight === top);
  const heavier = top > 0 && atTop.length >= 2 && Math.min(...atTop.map(s => s.reps)) >= 8;
  return [String(heavier ? 8 : Math.max(...atTop.map(s => s.reps)) + 1), tr('Wdh.-Ziel', 'Rep goal')];
}

const REST_OPTIONS = [0, 60, 90, 120, 180];
let lastTickSecond = null;
/** Mittlerer Kreis: eingestellte Pause (antippen = ändern) bzw. Countdown nach einem Satz (antippen = überspringen). */
function restCircle() {
  const el = circleStat(true, () => {
    if (nav.restEnd != null && nav.restEnd > Date.now()) update({ restEnd: null });
    else store.setRest(REST_OPTIONS[(REST_OPTIONS.indexOf(store.restSeconds) + 1) % REST_OPTIONS.length]);
  });
  const refresh = () => {
    const now = Date.now();
    if (nav.restEnd != null && nav.restEnd <= now) { // Pause vorbei
      alarm();
      lastTickSecond = null;
      update({ restEnd: null });
      return;
    }
    if (nav.restEnd != null) {
      const remaining = nav.restEnd - now;
      const secLeft = Math.ceil(remaining / 1000);
      if (secLeft <= 3 && secLeft !== lastTickSecond) { lastTickSecond = secLeft; tick(); }
      el.classList.toggle('final', remaining <= 3000);
      el.set(remaining / nav.restTotal, fmtDuration(remaining + 999), tr('Pause · tippen = weiter', 'Rest · tap to skip'), '#E8703A');
    } else {
      el.classList.remove('final');
      const sec = store.restSeconds;
      el.set(sec > 0 ? 1 : 0, sec > 0 ? fmtDuration(sec * 1000) : tr('Aus', 'Off'), tr('Pause', 'Rest'), 'var(--primary)');
    }
  };
  refresh();
  onTick(refresh);
  return el;
}

// ---------- Trainingsmodus ----------

export function confirmEndTraining(w, onFinish) {
  openDialog(() => {
    const start = nav.trainingStart;
    const total = w.exercises.reduce((a, e) => a + store.setsBetween(e, start, Infinity).filter(isWork).length, 0);
    const goalTotal = w.exercises.reduce((a, e) => a + store.targetFor(w, e), 0);
    const goalDone = w.exercises.reduce((a, e) => a + Math.min(store.setsBetween(e, start, Infinity).filter(isWork).length, store.targetFor(w, e)), 0);
    const pct = Math.round(goalCompletion(goalDone, goalTotal, total) * 100);
    const setsText = plural(total, tr('Satz', 'set'), tr('Sätze', 'sets')) + tr(' in ', ' in ') + fmtDuration(Date.now() - start);
    return dialog(tr('Training beenden?', 'Finish workout?'),
      h('p', null, total === 0
        ? tr('Du hast noch keinen Satz eingetragen – das Training wird verworfen.', "You haven't logged any sets yet – this workout will be discarded.")
        : goalTotal === 0 ? `${setsText}.`
          : `${setsText} – ` + tr(`${goalDone} von ${goalTotal} Ziel-Sätzen (${pct} %).`, `${goalDone} of ${goalTotal} target sets (${pct} %).`)),
      [
        { label: tr('Weiter trainieren', 'Keep training'), onClick: closeDialog },
        { label: tr('Beenden', 'Finish'), onClick: () => { closeDialog(); onFinish(); } },
      ], closeDialog);
  });
}

export function trainingScreen(w, onFinish) {
  keepScreenOn(true);
  const start = nav.trainingStart;
  const n = w.exercises.length;
  const index = Math.min(Math.max(0, nav.trainingIndex), Math.max(0, n - 1));
  const setIndex = i => { if (i >= 0 && i < n) update({ trainingIndex: i }); };
  const countOf = e => store.setsBetween(e, start, Infinity).filter(isWork).length; // ohne Aufwärmsätze
  const endDialog = () => confirmEndTraining(w, onFinish);

  const name = w.exercises[index];
  const clock = h('span');
  const updateClock = () => { clock.textContent = '⏱ ' + fmtDuration(Date.now() - start); };
  updateClock();
  onTick(updateClock);
  const page = h('div', { class: 'stack pad training-page' }, exercisePage(w, start, name));
  onSwipe(page, () => setIndex(index + 1), () => setIndex(index - 1));

  return screen({
    title: `${index + 1}/${n} · ${w.name}`, subtitle: clock, key: 'training-' + index,
    actions: [
      // Workout während des Trainings anpassen – Training läuft weiter
      iconBtn('edit', () => go({ editInTraining: name ?? '', pickGroup: null, pickDetail: null }), { label: tr('Workout bearbeiten', 'Edit workout') }),
      h('button', { class: 'btn text', onclick: endDialog }, tr('Beenden', 'Finish')),
    ],
    top: h('div', { class: 'training-head' },
      h('div', { class: 'hscroll' }, w.exercises.map((e, i) => {
        const count = countOf(e), goal = store.targetFor(w, e);
        const done = goal === 0 ? count > 0 : count >= goal;
        // ✓ = Ziel erreicht, • = angefangen (mind. ein Satz), sonst nur der Name (wie TrainingScreen.kt)
        const el = chip((done ? '✓ ' : count > 0 ? '• ' : '') + exName(e), i === index, () => setIndex(i));
        if (i === index) el.dataset.autoscroll = '1';
        return el;
      }))),
    content: page,
    bottom: h('div', { class: 'bottom-area' },
      h('div', { class: 'bottom-buttons' },
        h('button', { class: 'btn outline', disabled: index === 0, onclick: () => setIndex(index - 1) }, tr('‹ Vorherige', '‹ Previous')),
        index < n - 1
          ? h('button', { class: 'btn', onclick: () => setIndex(index + 1) }, tr('Nächste ›', 'Next ›'))
          : h('button', { class: 'btn', onclick: endDialog }, tr('Training beenden', 'Finish workout')))),
  });
}

function exercisePage(w, start, name) {
  const group = store.groupOf(name);
  const cardio = isCardio(group);
  const today = store.setsBetween(name, start, Infinity);
  const target = store.targetFor(w, name);
  const timed = isTimed(name);
  // Frühere Trainings dieser Übung (vor diesem Training), nach Tag gruppiert, neuester zuerst
  const previousDays = new Map();
  for (const s of store.setsFor(name)) if (s.time < start) {
    const d = fmtDate(s.time);
    if (!previousDays.has(d)) previousDays.set(d, []);
    previousDays.get(d).push(s);
  }
  const done = today.filter(isWork).length; // Aufwärmsätze zählen nicht fürs Ziel
  const reached = target > 0 && done >= target;
  const goal = repsGoal(name, start);
  const reps = circleStat(false, null);
  reps.set(1, goal?.[0] ?? '–', goal?.[1] ?? tr('Wdh.-Ziel', 'Rep goal'), 'var(--primary)');
  const sets = circleStat(false, null);
  sets.set(target > 0 ? done / target : done ? 1 : 0, target > 0 ? `${done}/${target}` : String(done),
    reached ? tr('Ziel erreicht ✓', 'Goal reached ✓') : tr('Sätze erledigt', 'Sets done'), reached ? completionColor(1) : '#E88A93');

  return [
    // Übungsname, rechts ⓘ fürs Wiki
    // ⓘ fürs Wiki, ⇄ = für heute eine ähnliche Übung (Workout bleibt unverändert)
    h('div', { class: 'training-title' }, h('h2', { class: 'grow training-name' }, exName(name)), wikiButton(name),
      iconBtn('swap', () => alternativesSheet(w, start, name), { label: tr('Alternative', 'Alternative'), cls: 'accent' })),
    originalOf(name) ? h('div', { class: 'accent swap-note' }, tr(`Heute statt „${exName(originalOf(name))}“`, `Today instead of "${exName(originalOf(name))}"`)) : null,
    recordLine(store.setsFor(name), cardio, timed),
    h('div', { class: 'circle-row' }, reps, restCircle(), sets),
    // Übung in diesem Training noch nicht gemacht → erster Satz „Aufwärmen“
    setInput(name, startRest, today.length ? null : start, cardio || timed ? null : parseIntStrict(goal?.[0] ?? '')), // Wdh. wie im Kreis
    noteButton(name),
    today.length ? [h('h2', { class: 'section' }, tr('Heute', 'Today')),
      setRows(today, cardio)] : null,
    // Verlauf: frühere Trainings dieser Übung
    previousDays.size
      ? [h('h2', { class: 'section' }, tr('Verlauf', 'History')),
        [...previousDays.entries()].map(([day, list]) => historyDayCard(day, list, cardio, timed))]
      : muted(tr('Erstes Mal – viel Erfolg!', 'First time – good luck!')),
  ];
}

// ---------- Übersicht nach dem Training ----------

export function summaryScreen(t, onDone) {
  const stats = sessionStats(store, t);
  const sessionSets = stats.results.flatMap(r => r.sets);
  const tiles = [];
  if (t.id >= 0) tiles.push([tr('Dauer', 'Duration'), fmtDuration(t.end - t.start)]);
  tiles.push([tr('Sätze', 'Sets'), String(stats.sets)]);
  if (stats.kcal != null) tiles.push([tr('🔥 Verbrauch', '🔥 Burned'), `~${stats.kcal} kcal`]);
  if (stats.volume > 0) tiles.push([tr('Volumen', 'Volume'), `${fmtVolume(stats.volume)} kg`]);
  if (stats.cardioMinutes > 0) tiles.push(['Cardio', `${stats.cardioMinutes} min`]);

  const volumeItems = stats.results
    .filter(r => !isCardio(r.group) && !isTimed(r.name) && r.sets.length)
    .map(r => [exName(r.name), r.sets.filter(isWork).reduce((a, s) => a + s.weight * s.reps, 0)])
    .sort((a, b) => b[1] - a[1]);

  let trend = null;
  if (t.workoutId >= 0) {
    const hist = store.sessionsFor(t.workoutId).filter(x => x.start <= t.start).slice(0, 8).reverse();
    if (hist.length >= 2) {
      const vols = hist.map(x => sessionStats(store, x).volume);
      const prev = vols[vols.length - 2];
      const change = prev > 0 ? Math.round(((vols[vols.length - 1] - prev) / prev) * 100) : null;
      trend = chartCard(tr(`Verlauf: ${t.workoutName}`, `Trend: ${t.workoutName}`),
        change == null ? tr('Volumen der letzten Trainingseinheiten', 'Volume of recent sessions')
          : change >= 0 ? tr(`+${change} % Volumen gegenüber letztem Mal`, `+${change} % volume vs. last time`)
            : tr(`${change} % Volumen gegenüber letztem Mal`, `${change} % volume vs. last time`),
        historyBars(vols, hist.map(x => fmtShortDate(x.start)), v => `${fmtVolume(v)} kg`));
    }
  }

  return screen({
    title: tr('Trainingsübersicht', 'Workout summary'), back: true, onBack: onDone, key: 'summary-' + t.id,
    actions: [h('button', { class: 'btn text', onclick: onDone }, tr('Fertig', 'Done'))],
    content: h('div', { class: 'stack pad' },
      h('div', null,
        h('h2', { class: 'headline' }, t.workoutName),
        muted(fmtDateTime(t.start)),
        h('div', { class: 'row-center' }, percentRing(stats.completion, 44),
          h('b', null, t.id < 0 ? tr('Freies Training – zählt als 100 %', 'Free workout – counts as 100 %')
            : stats.total === 0 ? tr('Ohne Ziel-Sätze – zählt als 100 %', 'No target sets – counts as 100 %')
              : tr(`${stats.done} von ${stats.total} Ziel-Sätzen erledigt`, `${stats.done} of ${stats.total} target sets done`)))),
      h('div', { class: 'stat-grid' }, tiles.map(([l, v]) => statBox(l, v))),
      stats.kcal == null ? muted(tr('Tipp: Trag unter „Körper“ dein Gewicht ein, dann schätzt die App den Kalorienverbrauch.',
        'Tip: enter your weight under "Body" and the app will estimate calories burned.'), 'small') : null,
      sessionSets.length ? heatCard({
        title: tr('Trainierte Muskeln', 'Trained muscles'), score: regionScore(sessionSets), showSets: true,
        empty: tr('Für diese Übungen sind keine Muskeln hinterlegt.', 'No muscles known for these exercises.'),
      }) : null,
      volumeItems.length >= 2 ? chartCard(tr('Volumen pro Übung', 'Volume per exercise'), tr('Gewicht × Wiederholungen', 'Weight × reps'),
        horizontalBars(volumeItems, v => `${fmtVolume(v)} kg`)) : null,
      trend,
      stats.records > 0 ? h('div', { class: 'card record-card' }, '🏆 ' + plural(stats.records,
        tr('neue Bestleistung', 'new personal record'), tr('neue Bestleistungen', 'new personal records')) + '!') : null,
      stats.results.map(r => {
        const cardio = isCardio(r.group);
        const met = r.target > 0 && r.sets.filter(isWork).length >= r.target;
        return h('div', { class: 'card row-card' },
          h('span', { html: exerciseIconHtml(r.name, r.group, 40) }),
          h('div', { class: 'grow' },
            h('b', { style: met ? `color:${completionColor(1)}` : null },
              r.target === 0 ? `${exName(r.name)}  ·  optional` : `${exName(r.name)}  ·  ${r.sets.filter(isWork).length}/${r.target}`),
            r.sets.length === 0
              ? muted(r.target === 0 ? tr('nicht gemacht', 'not done') : tr('übersprungen', 'skipped'), 'small')
              : [h('small', null, r.sets.map(s => fmtSet(s, cardio) + (s.type ? ` (${setTypeShort(s.type)})` : '')).join(', ')),
                !cardio && !isTimed(r.name) ? muted(tr('Volumen: ', 'Volume: ') + `${fmtVolume(r.sets.filter(isWork).reduce((a, s) => a + s.weight * s.reps, 0))} kg`, 'small') : null,
                comparisonLine(r)]),
          r.record ? h('span', { class: 'big-emoji' }, '🏆') : null);
      }),
      h('button', { class: 'btn wide', onclick: onDone }, tr('Fertig', 'Done'))),
  });
}

/** Vergleich mit dem letzten Mal: Kraft = schwerster Satz, Cardio = Minuten gesamt, Zeit = längste. */
function comparisonLine(r) {
  const sets = r.sets.filter(isTop); // ohne Aufwärm-/Drop-Sätze (wie r.previous)
  if (!r.previous.length || !sets.length) return null;
  const cardio = isCardio(r.group), timed = isTimed(r.name);
  const val = l => (cardio ? l.reduce((a, s) => a + s.reps, 0) : timed ? Math.max(...l.map(s => s.reps)) : Math.max(...l.map(s => s.weight)));
  const diff = val(sets) - val(r.previous);
  const fmtDiff = d => (cardio ? `${fmtW(d)} min` : timed ? `${Math.trunc(d)} s` : `${fmtW(d)} kg`);
  const [text, color] = diff > 0.001 ? [`▲ +${fmtDiff(diff)}`, completionColor(1)]
    : diff < -0.001 ? [`▼ −${fmtDiff(-diff)}`, null] : [`= ${tr('gleich', 'same')}`, null];
  return h('small', { class: color ? '' : 'muted', style: color ? `color:${color};font-weight:600` : null }, `${text} ` + tr('ggü. letztem Mal', 'vs. last time'));
}

export { keepScreenOn };

// ---------- Alternative für heute (wie AlternativesSheet in TrainingScreen.kt) ----------

/** Original-Übung, falls `name` im laufenden Training eine Alternative ist */
const originalOf = name => Object.entries(nav.swaps ?? {}).find(([, v]) => v === name)?.[0] ?? null;

/** Tausch eintragen: zurück zum Original entfernt den Eintrag */
function swapTo(from, to) {
  const original = originalOf(from) ?? from;
  const swaps = { ...(nav.swaps ?? {}) };
  if (to === original) delete swaps[original]; else swaps[original] = to;
  update({ swaps });
}

function alternativesSheet(w, start, name) {
  const original = originalOf(name);
  const base = original ?? name;
  const list = similarExercises(store, base, [...w.exercises, ...(original ? [original] : [])]).filter(([n]) => n !== name);
  const hasSets = store.setsBetween(name, start, Infinity).length > 0;
  const pick = to => { closeDialog(); swapTo(name, to); };
  openDialog(() => h('div', { class: 'dialog-backdrop sheet-backdrop', onclick: e => { if (e.target === e.currentTarget) closeDialog(); } },
    h('div', { class: 'sheet', role: 'dialog' },
      h('div', { class: 'sheet-handle', onclick: closeDialog }),
      h('div', { class: 'sheet-body' },
        h('h2', null, tr('Alternative für heute', 'Alternative for today')),
        muted(tr(`Gleiche Muskeln wie „${exName(base)}“. Gilt nur für dieses Training – dein Workout bleibt gleich.`,
          `Same muscles as "${exName(base)}". Only for this session – your workout stays the same.`), 'small'),
        hasSets ? muted(tr(`Deine Sätze von heute bei „${exName(name)}“ bleiben gespeichert.`, `Today's sets for "${exName(name)}" stay saved.`), 'small') : null,
        original ? h('button', { class: 'btn outline', onclick: () => pick(original) }, tr(`Zurück zu „${exName(original)}“`, `Back to "${exName(original)}"`)) : null,
        list.length ? list.map(([alt, pct]) => {
          const g = store.groupOf(alt), last = store.setsFor(alt)[0];
          return h('div', { class: 'cat-row', role: 'button', onclick: () => pick(alt) },
            h('span', { html: exerciseIconHtml(alt, g, 40) }),
            h('div', { class: 'grow' }, h('span', null, exName(alt)),
              h('small', { class: 'muted' }, tr(`${pct} % ähnlich`, `${pct} % similar`) + (last ? ' · ' + tr('zuletzt ', 'last ') + fmtSet(last, isCardio(g)) : ''))),
            wikiButton(alt));
        }) : muted(tr('Keine ähnlichen Übungen gefunden (für diese Übung sind keine Muskeln hinterlegt).', 'No similar exercises found (no muscles are set for this exercise).'))))));
}

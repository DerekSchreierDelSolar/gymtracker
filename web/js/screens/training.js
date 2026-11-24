// Trainingsmodus und Trainingsübersicht – entspricht TrainingScreen.kt und SummaryScreen.kt.

import { wikiButton } from '../wiki.js';
import { similarExercises } from '../wizard.js';
import { heatCard, regionScore } from '../bodymap.js';
import { store } from '../store.js';
import { fmtVol,
  tr, exName, fmtSet, fmtW, fmtVolume, fmtKcal, fmtDate, fmtDateTime, fmtDuration, fmtTime, fmtShortDate, plural, isCardio, isTimed,
  goalCompletion, completionColor, dayKey, fmtSeconds, isWork, isTop, setTypeShort, parseIntStrict,
} from '../core.js';
import { sessionStats, maxHeartRate, heartZoneMinutes, heartZoneNames, setHeartRates } from '../stats.js';
import { exerciseIconHtml, exerciseImageUrl } from '../pictograms.js';
import {
  h, chip, dialog, statBox, chartCard, horizontalBars, historyBars, lineChart, percentRing, onTick, muted, icon, iconBtn, showHint, hintUsed,
} from '../ui.js';
import { nav, update, go, openDialog, closeDialog, onSwipe, rerender } from '../nav.js';
import { screen } from '../layout.js';
import { setInput, setRows, historyDayCard, recordLine, noteButton } from './exercise.js';
import { achievements } from '../achievements.js';
import { achievementsCard, celebrateOnce } from '../celebration.js';
import { quoteFor, quoteBubble } from '../quotes.js';
import { supersetOf, supersetNext, supersetHint } from '../supersets.js';

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
document.addEventListener('visibilitychange', () => { if (nav.trainingStart != null && nav.trainingOpen) keepScreenOn(true); });

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
/**
 * Ablauf der Pause (3-2-1-Ticks, Ton am Ende) – app.js ruft das im Takt auf, auch wenn das Training
 * minimiert ist (wie RestTimerEffect in GymApp).
 */
export function restWatch() {
  if (nav.restEnd == null) return;
  const remaining = nav.restEnd - Date.now();
  if (remaining <= 0) { // Pause vorbei
    alarm();
    lastTickSecond = null;
    update({ restEnd: null });
    return;
  }
  const secLeft = Math.ceil(remaining / 1000);
  if (secLeft <= 3 && secLeft !== lastTickSecond) { lastTickSecond = secLeft; tick(); }
}
/** Mittlerer Kreis: eingestellte Pause (antippen = ändern) bzw. Countdown nach einem Satz (antippen = überspringen). */
function restCircle() {
  const el = circleStat(true, () => {
    if (nav.restEnd != null && nav.restEnd > Date.now()) update({ restEnd: null });
    else { hintUsed('rest'); store.setRest(REST_OPTIONS[(REST_OPTIONS.indexOf(store.restSeconds) + 1) % REST_OPTIONS.length]); }
  });
  const refresh = () => {
    const now = Date.now();
    if (nav.restEnd != null && nav.restEnd > now) { // Ton/Vibration am Ende: restWatch()
      const remaining = nav.restEnd - now;
      el.classList.toggle('final', remaining <= 3000);
      el.set(remaining / nav.restTotal, fmtDuration(remaining + 999), tr('Pause · tippen = weiter', 'Rest · tap to skip'), '#E8703A');
    } else {
      el.classList.remove('final');
      const sec = store.restSeconds;
      el.set(sec > 0 ? 1 : 0, sec > 0 ? fmtDuration(sec * 1000) : tr('Aus', 'Off'),
        showHint('rest') ? tr('Pause · tippen zum Ändern', 'Rest · tap to change') : tr('Pause ✎', 'Rest ✎'), 'var(--primary)');
    }
  };
  refresh();
  onTick(refresh);
  return el;
}

// ---------- Trainingsmodus ----------

/** Ab so langer Zeit ohne neuen Satz bietet „Beenden“ an, beim letzten Satz zu enden (wie FORGOTTEN_END_MS in TrainingState.kt). */
const FORGOTTEN_END_MS = 30 * 60_000;

/** Zeitpunkt des letzten Satzes, wenn er mehr als 30 min zurückliegt („Beenden vergessen“), sonst null. */
export function forgottenEnd(exercises, start, now = Date.now()) {
  const last = Math.max(-Infinity, ...exercises.flatMap(e => store.setsBetween(e, start, now).map(s => s.time)));
  return Number.isFinite(last) && now - last > FORGOTTEN_END_MS ? last : null;
}

export function confirmEndTraining(w, onFinish) {
  const lastSet = forgottenEnd(w.exercises, nav.trainingStart);
  openDialog(() => {
    const start = nav.trainingStart;
    const now = Date.now();
    const total = w.exercises.reduce((a, e) => a + store.setsBetween(e, start, Infinity).filter(isWork).length, 0);
    const goalTotal = w.exercises.reduce((a, e) => a + store.targetFor(w, e), 0);
    const goalDone = w.exercises.reduce((a, e) => a + Math.min(store.setsBetween(e, start, Infinity).filter(isWork).length, store.targetFor(w, e)), 0);
    const pct = Math.round(goalCompletion(goalDone, goalTotal, total) * 100);
    const setsText = plural(total, tr('Satz', 'set'), tr('Sätze', 'sets')) + tr(' in ', ' in ') + fmtDuration((lastSet ?? now) - start);
    const clock = lastSet != null ? fmtTime(lastSet) : '';
    // Verworfen wird nur ganz ohne Satz – auch reine Aufwärmsätze werden gespeichert (store.finishTraining)
    const anySet = lastSet != null || w.exercises.some(e => store.setsBetween(e, start, Infinity).length);
    return dialog(tr('Training beenden?', 'Finish workout?'),
      h('p', null, !anySet
        ? tr('Du hast noch keinen Satz eingetragen – das Training wird verworfen.', "You haven't logged any sets yet – this workout will be discarded.")
        : goalTotal === 0 ? `${setsText}.`
          : `${setsText} – ` + tr(`${goalDone} von ${goalTotal} Ziel-Sätzen (${pct}\u00A0%).`, `${goalDone} of ${goalTotal} target sets (${pct}\u00A0%).`),
        lastSet != null ? h('small', { class: 'muted', style: 'display:block;margin-top:8px' }, tr(
          `Dein letzter Satz war um ${clock} (vor ${fmtDuration(now - lastSet)}). Soll das Training dort enden? Sonst zählen Dauer und kcal bis jetzt.`,
          `Your last set was at ${clock} (${fmtDuration(now - lastSet)} ago). End the workout there? Otherwise duration and kcal count until now.`)) : null),
      lastSet != null ? [
        { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
        { label: tr('Jetzt', 'Now'), onClick: () => { closeDialog(); onFinish(null); } },
        { label: tr(`Um ${clock}`, `At ${clock}`), onClick: () => { closeDialog(); onFinish(lastSet); } },
      ] : [
        { label: tr('Weiter trainieren', 'Keep training'), onClick: closeDialog },
        { label: tr('Beenden', 'Finish'), onClick: () => { closeDialog(); onFinish(null); } },
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
    // Pfeil = Training minimieren (läuft weiter) – wie die Zurück-Geste
    back: true,
    actions: [
      // Workout während des Trainings anpassen – Training läuft weiter
      iconBtn('edit', () => go({ editInTraining: name ?? '', pickGroup: null, pickDetail: null }), { label: tr('Workout bearbeiten', 'Edit workout') }),
      h('button', { class: 'btn text', onclick: endDialog }, tr('Beenden', 'Finish')),
    ],
    top: h('div', { class: 'training-head' },
      h('div', { class: 'hscroll' }, w.exercises.map((e, i) => {
        const count = countOf(e), goal = store.targetFor(w, e);
        const done = goal === 0 ? count > 0 : count >= goal;
        // Stand auf einen Blick: ✓ = Ziel erreicht, sonst „2/4“ bzw. „2“ (wie trainingChipLabel in TrainingScreen.kt)
        const el = chip(chipLabel(exName(e), count, goal, done), i === index, () => setIndex(i));
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
    (ss => (ss ? supersetHint(ss, name) : null))(supersetOf(w, name)), // Supersatz-Hinweis
    setInput(name, () => {
      // Supersatz (supersets.js): direkt zur nächsten Übung der Gruppe, Pause erst nach der letzten
      const warmup = store.setsFor(name)[0]?.type === 'WARMUP';
      const { next, rest } = supersetNext(w, name, start, warmup);
      if (rest) startRest();
      if (next) update({ trainingIndex: w.exercises.indexOf(next) });
    }, today.length ? null : start, cardio || timed ? null : parseIntStrict(goal?.[0] ?? '')), // Wdh. wie im Kreis
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

/**
 * Leiste „Training läuft · 12:34“, solange das Training minimiert ist (wie TrainingBar in TrainingScreen.kt).
 * Antippen = zurück ins Training; zeigt auch den Pausen-Countdown.
 */
export function trainingBar(w) {
  const clock = h('b'), rest = h('span', { class: 'tb-rest' });
  const refresh = () => {
    const now = Date.now();
    clock.textContent = tr('Training läuft', 'Workout in progress') + ' · ' + fmtDuration(now - nav.trainingStart);
    const left = nav.restEnd != null ? nav.restEnd - now : 0;
    rest.textContent = left > 0 ? tr('Pause ', 'Rest ') + fmtDuration(left + 999) : '';
  };
  refresh();
  onTick(refresh);
  return h('button', { class: 'training-bar', onclick: () => go({ trainingOpen: true }) },
    icon('play'), h('span', { class: 'tb-text' }, clock, h('small', null, w.name)), rest, icon('chevron'));
}

// ---------- Übersicht nach dem Training ----------

/** Karte „Puls pro Satz“ (wie SetHeartCard in SummaryScreen.kt) */
function setHeartCard(items) {
  const groups = new Map();
  for (const it of items) (groups.get(it.set.exercise) ?? groups.set(it.set.exercise, []).get(it.set.exercise)).push(it);
  const drops = items.filter(i => i.after60 != null).map(i => i.peak - i.after60);
  return chartCard(tr('❤ Puls pro Satz', '❤ Heart rate per set'), tr('Spitze → 1 min später · vor dem nächsten Satz', 'Peak → 1 min later · before the next set'),
    [...groups].map(([ex, list]) => [
      h('b', { class: 'set-heart-ex' }, exName(ex)),
      list.map(i => h('div', { class: 'set-heart-row' },
        h('small', { class: 'muted set-heart-label' }, i.label),
        h('span', { class: 'grow' }, `${i.peak}` + (i.after60 != null ? ` → ${i.after60} (−${i.peak - i.after60})` : '') + ' bpm'),
        i.beforeNext != null ? h('small', { class: i.recovered === false ? 'error' : 'muted' },
          (i.recovered === false ? '⚠ ' : '') + tr(`vor nächstem: ${i.beforeNext}`, `before next: ${i.beforeNext}`)) : null)),
    ]),
    drops.length ? h('small', null, tr(`Ø Erholung 1 min nach dem Satz: −${Math.round(drops.reduce((a, b) => a + b, 0) / drops.length)}\u00A0bpm (über 12 normal, ab 20 gut trainiert)`,
      `Avg recovery 1 min after the set: −${Math.round(drops.reduce((a, b) => a + b, 0) / drops.length)}\u00A0bpm (above 12 normal, 20+ well trained)`)) : null,
    items.some(i => i.recovered === false) ? muted(tr('⚠ = Puls vor dem nächsten Satz noch nicht einmal halb gesunken – die Pause war evtl. zu kurz.',
      "⚠ = heart rate hadn't even dropped halfway before the next set – the rest may have been too short."), 'small') : null);
}

/** Karte „Puls“ (wie HeartRateCard in SummaryScreen.kt): Ø/max/min, Verlauf je Minute, Pulszonen mit Geburtsjahr */
function heartRateCard(t, kcalFromHr) {
  const hr = t.hr, curve = hr.curve.filter(v => v > 0), p = store.profile;
  let zones = null;
  const maxHr = maxHeartRate(store, t.start);
  if (maxHr && curve.length) {
    const minutes = heartZoneMinutes(hr, maxHr), names = heartZoneNames();
    zones = [h('b', null, tr('Pulszonen', 'Heart rate zones')),
      muted(tr(`< 60 · 60–70 · 70–80 · 80–90 · > 90 % der max. Herzfrequenz (~${maxHr}\u00A0bpm = 220 − Alter)`,
        `< 60 · 60–70 · 70–80 · 80–90 · > 90 % of max heart rate (~${maxHr}\u00A0bpm = 220 − age)`), 'small'),
      horizontalBars(names.map((n, i) => [n, minutes[i]]), v => `${v}\u00A0min`)];
  }
  return chartCard(tr('❤ Puls', '❤ Heart rate'),
    tr(`Ø ${hr.avg} · max ${hr.max} · min ${hr.min}\u00A0bpm`, `avg ${hr.avg} · max ${hr.max} · min ${hr.min}\u00A0bpm`),
    curve.length >= 2 ? lineChart(curve, 'bpm', null, { headline: `Ø ${hr.avg}\u00A0bpm`, dots: false }) : null,
    zones,
    !kcalFromHr && (!p.sex || !p.birthYear) ? muted(tr('Tipp: Mit Geschlecht und Geburtsjahr im Profil (Einstellungen) berechnet die App die kcal aus dem Puls.',
      'Tip: with sex and birth year in your profile (settings) the app calculates calories from heart rate.'), 'small') : null);
}

export function summaryScreen(t, onDone) {
  const stats = sessionStats(store, t);
  // Erfolge (achievements.js); direkt nach dem Training feiern: Konfetti bei großen Erfolgen + Ziele (wie SummaryScreen.kt)
  const celebrate = nav.celebrateId === t.id;
  const wins = achievements(t, stats);
  if (celebrate && wins.some(a => a.big)) setTimeout(() => celebrateOnce(t.id), 50);
  const sessionSets = stats.results.flatMap(r => r.sets);
  const tiles = [];
  if (t.id >= 0) tiles.push([tr('Dauer', 'Duration'), fmtDuration(t.end - t.start)]);
  tiles.push([tr('Sätze', 'Sets'), String(stats.sets)]);
  if (stats.kcal != null) tiles.push([stats.kcalFromHr ? tr('❤ Verbrauch', '❤ Burned') : tr('🔥 Verbrauch', '🔥 Burned'), `~${fmtKcal(stats.kcal)}\u00A0kcal`]);
  if (stats.volume > 0) tiles.push([tr('Volumen', 'Volume'), `${fmtVol(stats.volume)}`]);
  if (stats.cardioMinutes > 0) tiles.push([tr('Cardio', 'Cardio'), `${stats.cardioMinutes}\u00A0min`]);

  const volumeItems = stats.results
    .filter(r => !isCardio(r.group) && !isTimed(r.name) && r.sets.length)
    .map(r => [exName(r.name), r.sets.filter(isWork).reduce((a, s) => a + s.weight * s.reps, 0)])
    .filter(i => i[1] > 0) // Körpergewichtsübungen (0 kg) hätten nur einen leeren Balken
    .sort((a, b) => b[1] - a[1]);

  return screen({
    title: tr('Trainingsübersicht', 'Workout summary'), back: true, onBack: onDone, key: 'summary-' + t.id,
    actions: [h('button', { class: 'btn text', onclick: onDone }, tr('Fertig', 'Done'))],
    content: h('div', { class: 'stack pad' },
      achievementsCard(wins, celebrate, t.id),
      quoteBubble(quoteFor(t, wins, stats)), // Meerschweinchen-Spruch (quotes.js)
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
      t.hr ? heartRateCard(t, stats.kcalFromHr) : null,
      (sh => (sh.length ? setHeartCard(sh) : null))(setHeartRates(store, t, stats.results)),
      sessionSets.length ? heatCard({
        title: tr('Trainierte Muskeln', 'Trained muscles'), score: regionScore(sessionSets), showSets: true,
        empty: tr('Für diese Übungen sind keine Muskeln hinterlegt.', 'No muscles known for these exercises.'),
      }) : null,
      volumeItems.length >= 2 ? chartCard(tr('Volumen pro Übung', 'Volume per exercise'), tr('Gewicht × Wiederholungen', 'Weight × reps'),
        horizontalBars(volumeItems, v => `${fmtVol(v)}`, true)) : null,
      h('button', { class: 'btn wide', onclick: onDone }, tr('Fertig', 'Done'))),
  });
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
              h('small', { class: 'muted' }, tr(`${pct}\u00A0% ähnlich`, `${pct}\u00A0% similar`) + (last ? ' · ' + tr('zuletzt ', 'last ') + fmtSet(last, isCardio(g)) : ''))),
            wikiButton(alt));
        }) : muted(tr('Keine ähnlichen Übungen gefunden (für diese Übung sind keine Muskeln hinterlegt).', 'No similar exercises found (no muscles are set for this exercise).'))))));
}

/** Chip-Text im Training: „✓ Name“, „Name 2/4“, „Name 2“ (optional) bzw. nur „Name“ (wie trainingChipLabel). */
function chipLabel(name, count, goal, done) {
  if (done) return `✓ ${name}`;
  if (goal > 0) return `${name} ${count}/${goal}`;
  return count > 0 ? `${name} ${count}` : name;
}

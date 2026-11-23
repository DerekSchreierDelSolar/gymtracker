// Langfristige Ziele (Roadmap Schritt 1) – entspricht Goals.kt.
// Im JSON als "goals": [{ id, type, exercise?, target, start, created }].

import { store, GOAL_NEEDS_EXERCISE } from './store.js';
import { tr, exName, fmtW, fmtDate, isTimed, isCardio, isTop, isWork, startOfWeek, addDays, parseNum, sortByName } from './core.js';
import { CATALOG } from './data.js';
import { allSessions } from './stats.js';
import { exerciseIconHtml } from './pictograms.js';
import { h, dialog, textField, muted, undoToast } from './ui.js';
import { openDialog, closeDialog, rerender } from './nav.js';
import { nextMilestones, milestoneRows } from './milestones.js';

const TYPES = () => [
  ['STRENGTH', '🏋️', tr('Gewicht schaffen', 'Lift a weight'), tr('z. B. 100 kg Bankdrücken', 'e.g. 100 kg bench press')],
  ['ENDURANCE', '⏱️', tr('Zeit am Stück', 'Go the distance'), tr('z. B. 30 min laufen oder 2 min Unterarmstütz', 'e.g. run 30 min or hold a plank for 2 min')],
  ['BODYWEIGHT', '⚖️', tr('Körpergewicht', 'Body weight'), tr('z. B. 10 kg abnehmen', 'e.g. lose 10 kg')],
  ['CARDIO_WEEK', '🫀', tr('Ausdauer steigern', 'Build endurance'), tr('Cardio-Minuten pro Woche', 'Cardio minutes per week')],
  ['REGULAR', '💚', tr('Gesund bleiben', 'Stay healthy'), tr('regelmäßig dein Wochenziel schaffen', 'hit your weekly goal regularly')],
];
const typeInfo = t => TYPES().find(x => x[0] === t);
const needsExercise = t => GOAL_NEEDS_EXERCISE.includes(t);

/** Menge einer Ausdauer-Übung: Cardio in Minuten, Zeitübung in Sekunden (wie GymSet.reps). */
export const fmtAmount = (ex, v) => (isCardio(store.groupOf(ex)) ? `${fmtW(v)} min` : `${fmtW(v)} s`);
const topSets = ex => store.sets.filter(s => s.exercise === ex && isTop(s) && s.reps > 0);
const metricOf = type => (type === 'ENDURANCE' ? s => s.reps : s => s.weight);

/** Bester Wert einer Übung (Gewicht bzw. Dauer), null = noch nie gemacht. */
function bestValue(type, ex) {
  const l = topSets(ex);
  return l.length ? Math.max(...l.map(metricOf(type))) : null;
}

export function goalTitle(g) {
  switch (g.type) {
    case 'STRENGTH': return `${fmtW(g.target)} kg ${exName(g.exercise)}`;
    case 'ENDURANCE': return `${fmtAmount(g.exercise, g.target)} ${exName(g.exercise)}`;
    case 'BODYWEIGHT': return g.target < g.start ? tr(`Auf ${fmtW(g.target)} kg abnehmen`, `Get down to ${fmtW(g.target)} kg`)
      : tr(`Auf ${fmtW(g.target)} kg zunehmen`, `Get up to ${fmtW(g.target)} kg`);
    case 'CARDIO_WEEK': return tr(`${fmtW(g.target)} min Cardio pro Woche`, `${fmtW(g.target)} min cardio per week`);
    default: return tr(`In ${g.target} Wochen das Wochenziel schaffen`, `Hit the weekly goal in ${g.target} weeks`);
  }
}

const clamp = x => Math.min(1, Math.max(0, x));
const way = (start, cur, target) => (target === start ? 1 : clamp((cur - start) / (target - start)));
const pct = f => `${Math.round(f * 100)} %`;
const done = t => tr(`Geschafft am ${fmtDate(t)}`, `Achieved on ${fmtDate(t)}`);
const cardioMinutes = (from, to) => store.sets.filter(s => isWork(s) && s.time >= from && s.time < to && isCardio(store.groupOf(s.exercise)))
  .reduce((a, s) => a + s.reps, 0);

/** Fortschritt: { fraction 0..1, value, detail, reachedAt (null = offen/wiederkehrend), short } – wie goalProgress in Goals.kt */
export function goalProgress(g) {
  if (needsExercise(g.type)) {
    const ex = g.exercise;
    const fmt = g.type === 'STRENGTH' ? v => `${fmtW(v)} kg` : v => fmtAmount(ex, v);
    const metric = metricOf(g.type);
    const sets = topSets(ex);
    const cur = sets.length ? Math.max(...sets.map(metric)) : 0;
    const hits = sets.filter(s => s.time >= g.created && metric(s) >= g.target).map(s => s.time);
    const reachedAt = hits.length ? Math.min(...hits) : null;
    // Anteil am Ziel (nicht ab Start): bereits Geschafftes zählt mit – motiviert mehr als ein leerer Balken
    const fraction = reachedAt ? 1 : clamp(cur / g.target);
    return {
      fraction, reachedAt, short: pct(fraction),
      value: `${fmt(cur)} / ${fmt(g.target)}`,
      detail: reachedAt ? done(reachedAt)
        : cur > g.start && g.start > 0 ? tr(`+${fmt(cur - g.start)} seit dem Start · noch ${fmt(g.target - cur)}`, `+${fmt(cur - g.start)} since you started · ${fmt(g.target - cur)} to go`)
          : tr(`Noch ${fmt(g.target - cur)}`, `${fmt(g.target - cur)} to go`),
    };
  }
  if (g.type === 'BODYWEIGHT') {
    const lose = g.target < g.start;
    const cur = store.weights.at(-1)?.kg ?? g.start;
    const reachedAt = store.weights.find(w => w.time >= g.created && (lose ? w.kg <= g.target : w.kg >= g.target))?.time ?? null;
    const progress = lose ? g.start - cur : cur - g.start;
    const rest = fmtW(Math.abs(cur - g.target));
    const fraction = reachedAt ? 1 : way(g.start, cur, g.target);
    return {
      fraction, reachedAt, short: pct(fraction), value: `${fmtW(cur)} kg`,
      detail: reachedAt ? done(reachedAt)
        : progress > 0 ? tr(`${fmtW(progress)} kg geschafft · noch ${rest} kg`, `${fmtW(progress)} kg done · ${rest} kg to go`)
          // noch kein Fortschritt: nur den Weg nennen, nicht „falsche Richtung“ vorhalten
          : tr(`Start ${fmtW(g.start)} kg · noch ${rest} kg`, `Started at ${fmtW(g.start)} kg · ${rest} kg to go`),
    };
  }
  if (g.type === 'CARDIO_WEEK') {
    const ws = startOfWeek();
    const now = cardioMinutes(ws, addDays(ws, 7));
    // die letzten 4 abgeschlossenen Wochen (nur seit dem Anlegen) – zählt die geschafften, nicht die verpassten
    const weeks = [1, 2, 3, 4].map(i => startOfWeek(addDays(ws, -7 * i))).filter(w => addDays(w, 7) > g.created);
    const hit = weeks.filter(w => cardioMinutes(w, addDays(w, 7)) >= g.target).length;
    const fraction = clamp(now / g.target);
    return {
      fraction, reachedAt: null, short: pct(fraction),
      value: tr(`${fmtW(now)} / ${fmtW(g.target)} min diese Woche`, `${fmtW(now)} / ${fmtW(g.target)} min this week`),
      detail: now >= g.target ? tr('Diese Woche geschafft 💪', 'Done this week 💪')
        : hit > 0 ? tr(`In ${hit} der letzten ${weeks.length} Wochen geschafft`, `Done in ${hit} of the last ${weeks.length} weeks`)
          : tr(`Noch ${fmtW(g.target - now)} min diese Woche`, `${fmtW(g.target - now)} min to go this week`),
    };
  }
  // REGULAR: Wochen seit dem Anlegen mit erreichtem Wochenziel; Zeitpunkt = Training, das die Woche vollgemacht hat
  const from = startOfWeek(g.created);
  const byWeek = new Map();
  for (const t of allSessions(store).filter(t => t.start >= from).sort((a, b) => a.start - b.start)) {
    const w = startOfWeek(t.start);
    byWeek.set(w, [...(byWeek.get(w) ?? []), t]);
  }
  const doneAt = [...byWeek.keys()].sort((a, b) => a - b).map(w => byWeek.get(w)[store.weeklyGoal - 1]?.start).filter(t => t != null);
  const n = g.target;
  const reachedAt = doneAt[n - 1] ?? null;
  const k = Math.min(doneAt.length, n);
  return {
    fraction: clamp(doneAt.length / n), reachedAt, short: `${k}/${n}`,
    value: tr(`${k} von ${n} Wochen`, `${k} of ${n} weeks`),
    detail: reachedAt ? done(reachedAt) : tr(`Jede Woche mit ${store.weeklyGoal}× Training zählt`, `Every week with ${store.weeklyGoal}× training counts`),
  };
}

/** Karte „Meine Ziele“ auf der Startseite: offene Ziele zuerst, geschaffte danach, unten die nächsten Meilensteine. Antippen = bearbeiten. */
export function goalsCard() {
  const items = store.goals.map(g => [g, goalProgress(g)]).sort((a, b) => (a[1].reachedAt ? 1 : 0) - (b[1].reachedAt ? 1 : 0));
  // Meilensteine (milestones.js) – mit eigenen Zielen nur die 2 nächsten (wie GoalsCard)
  const miles = nextMilestones(items.length ? 2 : 3);
  return h('div', { class: 'card goals-card' },
    h('div', { class: 'row' }, h('b', { class: 'grow goals-title' }, tr('🎯 Meine Ziele', '🎯 My goals')),
      h('button', { class: 'btn text', onclick: () => goalDialog(null) }, tr('+ Ziel', '+ Goal'))),
    items.length ? null : miles.length
      ? muted(tr('Noch kein eigenes Ziel – z. B. 100 kg Bankdrücken oder 10 kg abnehmen.', 'No goal of your own yet – e.g. 100 kg bench press or lose 10 kg.'), 'goals-empty small')
      : muted(tr('Setz dir ein langfristiges Ziel – z. B. 100 kg Bankdrücken, 30 min laufen oder 10 kg abnehmen. Hier siehst du dann, wie nah du dran bist.',
        "Set yourself a long-term goal – e.g. 100 kg bench press, run 30 min or lose 10 kg. You'll see here how close you are."), 'goals-empty'),
    items.map(([g, p]) => goalRow(g, p, () => goalDialog(g))),
    miles.length ? [h('small', { class: 'muted goals-sub' }, tr('🏁 Als Nächstes', '🏁 Up next')), milestoneRows(miles)] : null);
}

/** Ein Ziel mit Balken (Startseite, Summary) – wie GoalRow. onClick = null: nicht antippbar. */
export function goalRow(g, p = goalProgress(g), onClick = null) {
  return h(onClick ? 'button' : 'div', { class: 'goal-row', onclick: onClick },
    h('div', { class: 'goal-head' },
      h('span', { class: 'goal-emoji' }, p.reachedAt ? '🏆' : typeInfo(g.type)[1]),
      h('b', { class: 'grow' }, goalTitle(g)),
      h('b', { class: 'goal-short' }, p.short)),
    h('div', { class: ['goal-bar', p.reachedAt && 'done'] }, h('span', { style: `width:${Math.round(p.fraction * 100)}%` })),
    h('small', { class: 'muted goal-detail' }, (p.reachedAt ? '' : p.value + ' · ') + p.detail));
}

/** Übungen, die zum Zieltyp passen: Kraft = mit Gewicht, Ausdauer = Cardio oder Zeitübung. */
const fitsType = (type, n) => (isCardio(store.groupOf(n)) || isTimed(n)) === (type === 'ENDURANCE');

/** Neues Ziel (goal = null: erst Typ wählen) bzw. bestehendes bearbeiten/löschen – wie GoalDialog in Goals.kt. */
export function goalDialog(goal) {
  const st = {
    type: goal?.type ?? null, exercise: goal?.exercise ?? null, query: '',
    text: goal && !['CARDIO_WEEK', 'REGULAR'].includes(goal.type) ? fmtW(goal.target) : '',
    steps: goal && ['CARDIO_WEEK', 'REGULAR'].includes(goal.type) ? goal.target : 0,
  };
  const pickType = t => { Object.assign(st, { type: t, exercise: null, text: '', steps: t === 'CARDIO_WEEK' ? 90 : t === 'REGULAR' ? 12 : 0 }); rerender(); };
  const currentWeight = () => store.weights.at(-1)?.kg ?? null;

  /** Fehlermeldung, '' = noch unvollständig, null = gültig */
  function error() {
    const t = st.type, n = parseNum(st.text);
    if (!t) return '';
    if (needsExercise(t)) {
      if (!st.exercise || n == null || n <= 0) return '';
      const best = bestValue(t, st.exercise);
      if (best != null && n <= best && (!goal || st.exercise !== goal.exercise)) return tr('Hast du schon geschafft – nimm ein höheres Ziel', 'Already done – pick a higher goal');
      return null;
    }
    if (t === 'BODYWEIGHT') {
      if (currentWeight() == null || n == null || n < 20 || n > 400) return '';
      if (n === currentWeight()) return tr('Das ist dein aktuelles Gewicht', "That's your current weight");
    }
    return null;
  }

  function save() {
    const t = st.type;
    if (!t || error() != null) return;
    const target = ['CARDIO_WEEK', 'REGULAR'].includes(t) ? st.steps : parseNum(st.text);
    // Start nur beim Anlegen (bzw. bei geänderter Übung) festhalten – sonst wäre der bisherige Fortschritt weg
    const keep = goal && goal.type === t && goal.exercise === (st.exercise ?? undefined);
    const start = keep ? goal.start : needsExercise(t) ? bestValue(t, st.exercise) ?? 0 : t === 'BODYWEIGHT' ? currentWeight() : 0;
    const g = {
      id: goal?.id ?? Date.now(), type: t, ...(needsExercise(t) ? { exercise: st.exercise } : {}),
      target, start, created: keep ? goal.created : Date.now(),
    };
    closeDialog();
    if (goal) store.updateGoal(g); else store.addGoal(g);
  }

  function back() {
    if (!st.type) closeDialog();
    else if (needsExercise(st.type) && st.exercise) { st.exercise = null; st.query = ''; rerender(); }
    else { st.type = null; rerender(); }
  }

  const stepper = (label, canDown, canUp, onDown, onUp) => h('div', { class: 'stepper big' },
    h('button', { class: 'step', disabled: !canDown, onclick: () => { onDown(); rerender(); } }, '−'),
    h('b', { class: 'step-value' }, label),
    h('button', { class: 'step', disabled: !canUp, onclick: () => { onUp(); rerender(); } }, '+'));

  function body() {
    const t = st.type;
    // Schritt 1: Art des Ziels
    if (!t) return TYPES().map(([key, emoji, label, hint]) => h('button', { class: 'goal-type', onclick: () => pickType(key) },
      h('span', { class: 'goal-type-emoji' }, emoji), h('span', { class: 'grow' }, h('b', null, label), h('small', { class: 'muted' }, hint))));
    const [, emoji, label] = typeInfo(t);
    const head = h('b', { class: 'small-title' }, `${emoji} ${label}`);
    // Schritt 2a: Übung wählen
    if (needsExercise(t) && !st.exercise) {
      const q = st.query.trim().toLowerCase();
      const counts = new Map();
      for (const s of store.sets) counts.set(s.exercise, (counts.get(s.exercise) ?? 0) + 1);
      const trained = [...counts.entries()].filter(([n]) => fitsType(t, n)).sort((a, b) => b[1] - a[1]).map(([n]) => n);
      const showTrained = !q && trained.length > 0;
      const list = showTrained ? trained.slice(0, 30)
        : sortByName([...new Set([...CATALOG.map(c => c.name), ...store.customNames])]
          .filter(n => fitsType(t, n) && (!q || exName(n).toLowerCase().includes(q) || n.toLowerCase().includes(q))), exName);
      return [head,
        textField({ id: 'goal-search', label: tr('Übung suchen', 'Search exercise'), value: st.query, oninput: v => { st.query = v; rerender(); } }),
        showTrained ? muted(tr('Deine häufigsten Übungen – oder oben suchen', 'Your most frequent exercises – or search above'), 'small') : null,
        h('div', { class: 'goal-pick-list', 'data-scroll': 'goal-pick' }, list.map(n => h('button', { class: 'pick-row', onclick: () => { st.exercise = n; st.text = ''; rerender(); } },
          h('span', { html: exerciseIconHtml(n, store.groupOf(n), 32) }), h('span', { class: 'grow' }, exName(n)))))];
    }
    // Schritt 2b: Zielwert
    const err = error();
    const out = [head];
    if (needsExercise(t)) {
      const best = bestValue(t, st.exercise);
      out.push(h('div', { class: 'row' },
        h('span', { html: exerciseIconHtml(st.exercise, store.groupOf(st.exercise), 32) }),
        h('span', { class: 'grow' }, exName(st.exercise),
          h('small', { class: 'muted' }, best != null ? tr('Bisher bestes: ', 'Best so far: ') + (t === 'STRENGTH' ? `${fmtW(best)} kg` : fmtAmount(st.exercise, best))
            : tr('Noch nicht gemacht', 'Not done yet'))),
        h('button', { class: 'btn text', onclick: () => { st.exercise = null; st.query = ''; rerender(); } }, tr('Ändern', 'Change'))));
    }
    if (t === 'BODYWEIGHT') out.push(h('p', null, currentWeight() != null ? tr(`Aktuell: ${fmtW(currentWeight())} kg`, `Current: ${fmtW(currentWeight())} kg`)
      : tr('Trag zuerst im Tab „Körper“ dein Gewicht ein.', 'First log your weight in the "Body" tab.')));
    if (needsExercise(t) || (t === 'BODYWEIGHT' && currentWeight() != null)) {
      const unit = t === 'ENDURANCE' ? (isCardio(store.groupOf(st.exercise)) ? tr('Minuten', 'minutes') : tr('Sekunden', 'seconds')) : 'kg';
      out.push(textField({
        id: 'goal-target', label: tr(`Ziel in ${unit}`, `Goal in ${unit}`), value: st.text, inputmode: 'decimal', error: err || null,
        oninput: v => { st.text = v.replace(/[^\d.,]/g, '').slice(0, 6); rerender(); }, onenter: save,
      }));
    }
    if (t === 'CARDIO_WEEK') out.push(
      stepper(tr(`${st.steps} min pro Woche`, `${st.steps} min per week`), st.steps > 15, st.steps < 600, () => { st.steps -= 15; }, () => { st.steps += 15; }),
      muted(tr('Alle Cardio-Übungen zählen zusammen (Laufen, Rad, Rudern …).', 'All cardio exercises count together (running, cycling, rowing …).'), 'small'));
    if (t === 'REGULAR') out.push(
      stepper(tr(`${st.steps} Wochen`, `${st.steps} weeks`), st.steps > 4, st.steps < 52, () => { st.steps -= 4; }, () => { st.steps += 4; }),
      muted(tr(`Zählt jede Woche, in der du dein Wochenziel (${store.weeklyGoal}× Training) schaffst – eine verpasste Woche macht nichts kaputt.`,
        `Every week in which you hit your weekly goal (${store.weeklyGoal}× training) counts – a missed week doesn't break anything.`), 'small'));
    return out;
  }

  openDialog(() => {
    const t = st.type;
    const canSave = t && !(needsExercise(t) && !st.exercise);
    const actions = goal
      ? [{ label: tr('Löschen', 'Delete'), danger: true, onClick: () => {
        closeDialog();
        const i = store.deleteGoal(goal);
        undoToast(tr('Ziel gelöscht', 'Goal deleted'), () => store.restoreGoal(goal, i));
      } }]
      : [{ label: t ? tr('Zurück', 'Back') : tr('Abbrechen', 'Cancel'), onClick: back }];
    if (canSave) actions.push({ label: tr('Speichern', 'Save'), disabled: error() != null, onClick: save });
    return dialog(goal ? tr('Ziel bearbeiten', 'Edit goal') : tr('Neues Ziel', 'New goal'), body(), actions, closeDialog);
  });
}

// Supersätze (Roadmap Schritt 13) – entspricht Supersets.kt.
// w.links = Übungen, die mit der NÄCHSTEN verbunden sind; aufeinanderfolgende Verbindungen = eine Gruppe.

import { store } from './store.js';
import { tr, exName, isWork } from './core.js';
import { h } from './ui.js';

/** Gruppen (mind. 2 Übungen) in Listenreihenfolge. */
export function supersetGroups(w) {
  const links = new Set(w.links ?? []);
  const out = [];
  let cur = [];
  w.exercises.forEach((ex, i) => {
    cur.push(ex);
    if (!links.has(ex) || i === w.exercises.length - 1) { if (cur.length >= 2) out.push(cur); cur = []; }
  });
  return out;
}

export const supersetOf = (w, name) => supersetGroups(w).find(g => g.includes(name)) ?? null;

/** Nach dem Umsortieren: Verbindung bleibt nur, wenn die nächste Übung dieselbe ist. */
export function keepLinks(oldOrder, newOrder, links) {
  return (links ?? []).filter(ex => {
    const a = oldOrder[oldOrder.indexOf(ex) + 1], b = newOrder[newOrder.indexOf(ex) + 1];
    return a != null && a === b;
  });
}

/** Wohin nach einem Satz? → { next: Übung oder null, rest: Pause starten? } (wie supersetNext) */
export function supersetNext(w, name, start, warmup) {
  const group = supersetOf(w, name);
  if (!group || warmup) return { next: null, rest: true };
  const i = group.indexOf(name);
  if (i < group.length - 1) return { next: group[i + 1], rest: false };
  const open = group.some(ex => {
    const target = store.targetFor(w, ex);
    return target === 0 || store.setsBetween(ex, start, Infinity).filter(isWork).length < target;
  });
  return { next: open ? group[0] : null, rest: true };
}

/**
 * Gewählte Übungen (mind. 2) zu einer Gruppe verbinden: Sie rücken an den Platz der obersten gewählten zusammen
 * (Reihenfolge untereinander bleibt) und lösen sich aus bisherigen Gruppen; andere Verbindungen bleiben,
 * solange die Übung danach dieselbe ist (wie groupedWorkout in Supersets.kt). Gibt { exercises, links } zurück.
 */
export function groupedWorkout(w, selected) {
  const sel = w.exercises.filter(e => selected.has(e));
  if (sel.length < 2) return { exercises: w.exercises, links: w.links ?? [] };
  const rest = w.exercises.filter(e => !selected.has(e));
  const insertAt = w.exercises.slice(0, w.exercises.indexOf(sel[0])).filter(e => !selected.has(e)).length;
  const order = [...rest.slice(0, insertAt), ...sel, ...rest.slice(insertAt)];
  const old = new Set(w.links ?? []);
  const oldNext = e => w.exercises[w.exercises.indexOf(e) + 1];
  const links = order.filter((e, i) => {
    const next = order[i + 1];
    return next != null && old.has(e) && !selected.has(e) && !selected.has(next) && oldNext(e) === next;
  });
  return { exercises: order, links: [...links, ...sel.slice(0, -1)] };
}

/** Kopf über einer Gruppe, oben in deren erster Listenzeile (span, weil die Zeile selbst ein button ist). */
export function supersetHead(size) {
  return h('span', { class: 'superset-head' },
    h('span', null, '🔗 ' + (size >= 3 ? tr('Riesensatz', 'Giant set') : tr('Supersatz', 'Superset')) +
      tr(' · abwechselnd, Pause erst danach', ' · alternate, rest afterwards')),
    h('span', { class: 'superset-unlink', role: 'button' }, tr('Auflösen', 'Unlink')));
}

/** Hinweis auf der Trainingsseite (wie SupersetHint). */
export function supersetHint(group, name) {
  const others = group.filter(e => e !== name).map(exName).join(' + ');
  return h('div', { class: 'superset-hint' },
    tr(`🔗 Supersatz mit ${others} – nach dem Satz geht's direkt weiter, Pause erst nach `,
      `🔗 Superset with ${others} – after the set you go straight on, rest only after `) + exName(group.at(-1)));
}

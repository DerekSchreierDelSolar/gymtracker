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

/** Verbinder unten in der Listenzeile (span, weil die Zeile selbst ein button ist). */
export function supersetLink(linked) {
  return h('span', { class: ['superset-link', linked && 'linked'], role: 'button' }, linked ? tr('🔗 Supersatz', '🔗 Superset') : tr('+ Supersatz', '+ Superset'));
}

/** Hinweis auf der Trainingsseite (wie SupersetHint). */
export function supersetHint(group, name) {
  const others = group.filter(e => e !== name).map(exName).join(' + ');
  return h('div', { class: 'superset-hint' },
    tr(`🔗 Supersatz mit ${others} – nach dem Satz geht's direkt weiter, Pause erst nach `,
      `🔗 Superset with ${others} – after the set you go straight on, rest only after `) + exName(group.at(-1)));
}

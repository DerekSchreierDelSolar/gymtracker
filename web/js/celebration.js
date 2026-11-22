// Feier im Summary (Roadmap Schritt 3, Peak-End-Regel) – entspricht Celebration.kt.
// Erfolge (achievements.js) groß oben, Konfetti + Vibration bei großen Erfolgen (nur direkt nach dem Training).

import { store } from './store.js';
import { tr } from './core.js';
import { h } from './ui.js';
import { rerender } from './nav.js';
import { goalRow } from './goals.js';

const expanded = new Set();   // Trainings-IDs mit aufgeklappter Erfolgsliste
const headlines = new Map();  // Trainings-ID → Überschrift (zufällig, aber pro Training fest)
const celebrated = new Set(); // Trainings-IDs, für die schon Konfetti lief

/** Kurze Überschrift je nach Anzahl großer Erfolge (wie headline in Celebration.kt). */
function headline(list) {
  const big = list.filter(a => a.big).length;
  const pool = big >= 2 ? [tr('Was für ein Training! 🔥', 'What a workout! 🔥'), tr('Rekordverdächtig!', 'Record-breaking!')]
    : big === 1 ? [tr('Stark gemacht!', 'Well done!'), tr('Das hat sich gelohnt!', 'That paid off!')]
      : [tr('Gut gemacht!', 'Nice work!'), tr('Wieder ein Schritt weiter', 'Another step forward'), tr('Jedes Training zählt', 'Every workout counts')];
  return pool[Math.floor(Math.random() * pool.length)];
}

/** Karte „Erfolge“: der wichtigste groß, die nächsten 5 darunter, Rest aufklappbar (wie AchievementsCard). */
export function achievementsCard(list, fresh, id) {
  if (!list.length) return null;
  const [top, ...rest] = list;
  const all = expanded.has(id);
  if (!headlines.has(id)) headlines.set(id, headline(list));
  return h('div', { class: ['card', 'wins-card', top.big && 'big'] },
    fresh ? h('small', { class: 'wins-head' }, headlines.get(id)) : null,
    h('div', { class: 'wins-top' }, h('span', { class: 'wins-top-emoji' }, top.emoji),
      h('div', { class: 'grow' }, h('b', { class: 'wins-top-title' }, top.title), top.detail ? h('span', null, top.detail) : null)),
    rest.length ? h('hr') : null,
    (all ? rest : rest.slice(0, 5)).map(a => h('div', { class: 'wins-row' }, h('span', { class: 'wins-emoji' }, a.emoji),
      h('div', { class: 'grow' }, h('b', null, a.title), a.detail ? h('small', { class: 'muted' }, a.detail) : null))),
    rest.length > 5 ? h('button', { class: 'btn text', onclick: () => { if (all) expanded.delete(id); else expanded.add(id); rerender(); } },
      all ? tr('Weniger anzeigen', 'Show less') : tr(`+${rest.length - 5} weitere`, `+${rest.length - 5} more`)) : null);
}

/** Ziele, zu denen dieses Training beigetragen hat (wie goalsTouched). */
export function goalsTouched(stats) {
  const done = stats.results.filter(r => r.sets.length);
  return store.goals.filter(g => (g.type === 'STRENGTH' || g.type === 'ENDURANCE') ? done.some(r => r.name === g.exercise)
    : g.type === 'CARDIO_WEEK' ? done.some(r => r.group === 'CARDIO')
      : g.type === 'REGULAR');
}

export function goalsCard(goals) {
  return goals.length ? h('div', { class: 'card goals-card' }, h('b', { class: 'goals-title' }, tr('🎯 Deine Ziele', '🎯 Your goals')),
    goals.map(g => goalRow(g))) : null;
}

const COLORS = ['#ffc107', '#4caf50', '#2196f3', '#e91e63', '#9c27b0', '#ff5722', '#7c83fd'];

/**
 * Konfetti einmal pro Training (ca. 3 s), reines CSS: eigene Ebene außerhalb von #app (übersteht das Neuzeichnen),
 * fängt keine Berührungen ab. Dazu Vibration (Android-Browser; iOS kann es nicht).
 */
export function celebrateOnce(id) {
  if (celebrated.has(id)) return;
  celebrated.add(id);
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
  const layer = h('div', { class: 'confetti', 'aria-hidden': 'true' });
  for (let i = 0; i < 110; i++) {
    const s = h('span');
    s.style.cssText = `left:${Math.random() * 100}%;background:${COLORS[i % COLORS.length]};` +
      `width:${6 + Math.random() * 6}px;height:${10 + Math.random() * 8}px;` +
      `--sway:${(Math.random() - 0.5) * 120}px;--spin:${(Math.random() - 0.5) * 1440}deg;` +
      `animation-delay:${Math.random() * 0.6}s;animation-duration:${2.2 + Math.random() * 1}s`;
    layer.append(s);
  }
  document.body.append(layer);
  setTimeout(() => layer.remove(), 4000);
  navigator.vibrate?.([40, 70, 40, 70, 40, 90, 160]);
}

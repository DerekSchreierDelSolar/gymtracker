// Übungs-Wiki – entspricht Wiki.kt. Inhalte: wiki-data.js (erzeugt aus app/src/main/assets/wiki/*.json).

import { WIKI } from './wiki-data.js';
import { CATALOG } from './data.js';
import { tr, exName, isEnglish, GROUP_KEYS, equipmentLabel } from './core.js';
import { exerciseIconHtml } from './pictograms.js';
import { h, icon, iconBtn, groupHeader, muted } from './ui.js';
import { nav, update, openDialog, closeDialog } from './nav.js';
import { screen } from './layout.js';

/** [de, en] → aktuelle Sprache */
const t = pair => (isEnglish() ? pair[1] : pair[0]);

const byName = new Map(CATALOG.map(c => [c.name, c]));
/** Klammertext eines Katalog-Namens: „Bankdrücken (Kurzhantel)“ → „Kurzhantel“. */
const variantKey = c => c.name.slice(c.base.length).trim().replace(/^\(|\)$/g, '');

/** Wiki-Eintrag zu einer Übung (Katalog-Name oder Grundübung), null bei eigenen Übungen. */
export function wikiFor(name) {
  return WIKI[byName.get(name)?.base ?? name] ?? null;
}

/** Anzeigename einer Grundübung. Englisch: kürzester EN-Name der Varianten (z. B. „Bench Press“). */
export function baseName(base) {
  if (!isEnglish()) return base;
  const en = CATALOG.filter(c => c.base === base).map(c => c.en).sort((a, b) => a.length - b.length)[0];
  return en ?? base;
}

/** ⓘ-Button, der das Wiki zu einer Übung öffnet (nichts, wenn es keinen Eintrag gibt). */
export function wikiButton(name) {
  if (!wikiFor(name)) return null;
  return h('button', {
    class: 'icon-btn accent', 'aria-label': tr('Anleitung', 'How to'),
    onclick: e => { e.stopPropagation(); openWiki(name); },
  }, icon('info'));
}

export const openWiki = name => openDialog(() => wikiSheet(name));

/**
 * Wiki-Blatt von unten (wie ModalBottomSheet).
 * name = Katalog-Name (nur Hinweis zu dieser Variante) oder Grundübung (Hinweise zu allen Varianten).
 */
function wikiSheet(name) {
  const entry = wikiFor(name);
  const cat = byName.get(name);
  const base = cat?.base ?? name;
  const first = cat ?? CATALOG.find(c => c.base === base);
  const notes = cat
    ? (entry.variants?.[variantKey(cat)] ? [[equipmentLabel(cat.equipment), entry.variants[variantKey(cat)]]] : [])
    : CATALOG.filter(c => c.base === base && entry.variants?.[variantKey(c)]).map(c => [exName(c.name), entry.variants[variantKey(c)]]);

  return h('div', { class: 'dialog-backdrop sheet-backdrop', onclick: e => { if (e.target === e.currentTarget) closeDialog(); } },
    h('div', { class: 'sheet', role: 'dialog' },
      h('div', { class: 'sheet-handle', onclick: closeDialog }),
      h('div', { class: 'sheet-body' },
        h('div', { class: 'wiki-head' },
          h('span', { html: exerciseIconHtml(first.name, first.group, 48) }),
          h('div', null, h('h2', null, cat ? exName(name) : baseName(base)), h('p', { class: 'accent' }, t(entry.muscles)))),
        entry.how?.length ? [
          h('h3', null, tr('Ausführung', 'How to')),
          h('ol', { class: 'wiki-list' }, entry.how.map(p => h('li', null, t(p)))),
        ] : null,
        entry.tips?.length ? [
          h('h3', null, tr('Darauf achten', 'Watch out')),
          h('ul', { class: 'wiki-list warn' }, entry.tips.map(p => h('li', null, t(p)))),
        ] : null,
        notes.length ? [
          h('h3', null, cat ? tr('Speziell mit diesem Gerät', 'With this equipment') : tr('Varianten', 'Variations')),
          notes.map(([label, p]) => h('div', { class: 'wiki-note' }, h('b', null, label), h('p', null, t(p)))),
        ] : null,
        muted(tr('Allgemeine Hinweise – ersetzen keine Einweisung. Bei Schmerzen abbrechen.',
          'General guidance – not a substitute for coaching. Stop if it hurts.'), 'small wiki-disclaimer'))));
}

/** Wiki-Übersicht: alle Grundübungen nach Muskelgruppe, mit Suche. */
export function wikiScreen() {
  const q = nav.wikiQuery.trim().toLowerCase();
  const bases = [];
  const seen = new Set();
  for (const c of CATALOG) {
    if (seen.has(c.base) || !WIKI[c.base]) continue;
    seen.add(c.base);
    if (!q || CATALOG.some(v => v.base === c.base && (v.name.toLowerCase().includes(q) || v.en.toLowerCase().includes(q)))) bases.push(c);
  }
  const byGroup = GROUP_KEYS.map(g => [g, bases.filter(c => c.group === g)]).filter(([, l]) => l.length);

  return screen({
    title: tr('Übungs-Wiki', 'Exercise wiki'), back: true, key: 'wiki',
    content: h('div', { class: 'pad' },
      h('label', { class: 'search wiki-search' }, icon('search', 20),
        h('input', {
          id: 'wiki-search', type: 'search', value: nav.wikiQuery, placeholder: tr('Übung suchen', 'Search exercise'),
          autocomplete: 'off', enterkeyhint: 'search',
          oninput: e => update({ wikiQuery: e.target.value }),
          onkeydown: e => { if (e.key === 'Enter') e.target.blur(); },
        })),
      byGroup.length ? null : muted(tr('Keine Übung gefunden.', 'No exercise found.')),
      byGroup.map(([g, list]) => [groupHeader(g), list.map(c =>
        h('button', { class: 'cat-row', onclick: () => openWiki(c.base) },
          h('span', { html: exerciseIconHtml(c.name, g, 40) }),
          h('div', { class: 'grow' }, h('span', null, baseName(c.base)), h('small', { class: 'muted' }, t(WIKI[c.base].muscles))),
          h('span', { class: 'muted' }, icon('chevron'))))])),
  });
}

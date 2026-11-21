// Filter für Übungslisten (Kategorie / Gerät / Suche) – entspricht Filter.kt.
// Innerhalb einer Dimension ODER (Brust oder Rücken), zwischen den Dimensionen UND.

import { equipmentOf, catalogEntry, groupLabel, equipmentLabel, tr } from './core.js';
import { muscleIconHtml } from './pictograms.js';
import { h, iconBtn, icon, chip } from './ui.js';
import { nav, rerender, emptyFilter } from './nav.js';

export const filterActive = f => f.groups.size > 0 || f.equipment.size > 0 || f.query.trim() !== '';

export function matches(f, name, group) {
  if (f.groups.size && !f.groups.has(group)) return false;
  if (f.equipment.size && !f.equipment.has(equipmentOf(name))) return false;
  const q = f.query.trim().toLowerCase();
  if (!q) return true;
  const en = catalogEntry(name)?.en ?? '';
  return name.toLowerCase().includes(q) || en.toLowerCase().includes(q);
}

const toggle = (set, x) => (set.has(x) ? set.delete(x) : set.add(x));

/** Lupe und Filter-Trichter für die Kopfzeile. `key`: 'list' | 'catalog' | 'pick'. */
export function filterActions(key) {
  const ui = nav.filterUi[key], f = nav[key + 'Filter'];
  const count = f.groups.size + f.equipment.size;
  return [
    iconBtn('search', () => {
      if (ui.search) f.query = ''; // Zuklappen leert die Suche – sonst würde unsichtbar weiter gefiltert
      ui.search = !ui.search;
      rerender();
      if (ui.search) document.getElementById(`search-${key}`)?.focus();
    }, { active: ui.search, label: tr('Suchen', 'Search') }),
    iconBtn('filter', () => { ui.filters = !ui.filters; rerender(); },
      { active: ui.filters, badge: count || null, label: 'Filter' }),
  ];
}

/** Aufklappbare Suchleiste und zwei waagerecht scrollbare Chip-Leisten. */
export function filterBar(key, groups, equipment) {
  const ui = nav.filterUi[key], f = nav[key + 'Filter'];
  return h('div', { class: 'filter-bar' },
    ui.search ? h('div', { class: 'search' },
      icon('search', 20),
      h('input', {
        id: `search-${key}`, type: 'search', value: f.query, placeholder: tr('Übung suchen …', 'Search exercises …'),
        autocomplete: 'off', enterkeyhint: 'search',
        oninput: e => { f.query = e.target.value; rerender(); },
        onkeydown: e => { if (e.key === 'Enter') e.target.blur(); },
      }),
      h('button', { class: 'icon-btn small', onclick: () => { f.query = ''; ui.search = false; rerender(); } }, icon('close', 20)),
    ) : null,
    ui.filters ? h('div', { class: 'filter-chips' },
      h('div', { class: 'hscroll' },
        f.groups.size || f.equipment.size
          ? chip('✕ ' + tr('Zurücksetzen', 'Reset'), false, () => { f.groups.clear(); f.equipment.clear(); rerender(); })
          : null,
        groups.map(g => chip(groupLabel(g), f.groups.has(g), () => { toggle(f.groups, g); rerender(); }, muscleIconHtml(g, 18)))),
      h('div', { class: 'hscroll' },
        equipment.map(e => chip(equipmentLabel(e), f.equipment.has(e), () => { toggle(f.equipment, e); rerender(); }))),
    ) : null,
  );
}

export function noFilterResults(key) {
  return h('div', { class: 'pad' },
    h('p', null, tr('Keine Übungen für diesen Filter.', 'No exercises match this filter.')),
    h('button', { class: 'btn text', onclick: () => { nav[key + 'Filter'] = emptyFilter(); rerender(); } },
      tr('Filter zurücksetzen', 'Reset filter')));
}


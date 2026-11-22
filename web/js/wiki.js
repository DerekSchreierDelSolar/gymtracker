// Übungs-Wiki – entspricht Wiki.kt. Inhalte: wiki-data.js (erzeugt aus app/src/main/assets/wiki/*.json).

import { WIKI } from './wiki-data.js';
import { MUSCLE_INFO } from './muscle-data.js';
import { SUPPLEMENTS } from './supplement-data.js';
import { MUSCLES } from './body-data.js';
import { CATALOG } from './data.js';
import { tr, exName, sortByName, isEnglish, isCardio, GROUP_KEYS, equipmentLabel } from './core.js';
import { exerciseIconHtml } from './pictograms.js';
import { h, icon, iconBtn, groupHeader, muted, chip } from './ui.js';
import { nav, update, openDialog, closeDialog } from './nav.js';
import { screen } from './layout.js';
import { muscleMap, musclesOf, levelsOf, bodyFigure, tappableFigure, muscleLabel, muscleOfRegion, PRIMARY_COLOR } from './bodymap.js';

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
  return h('div', { class: 'dialog-backdrop sheet-backdrop', onclick: e => { if (e.target === e.currentTarget) closeDialog(); } },
    h('div', { class: 'sheet', role: 'dialog' },
      h('div', { class: 'sheet-handle', onclick: closeDialog }),
      h('div', { class: 'sheet-body' }, wikiContent(name, { header: true, muscles: true }))));
}

/**
 * Inhalt des Wikis (für das Blatt und den Reiter „Info“ der Übungsdetails).
 * header = Symbol + Name, muscles = Körperdiagramm.
 */
export function wikiContent(name, { header, muscles }) {
  const entry = wikiFor(name);
  if (!entry) return null;
  const cat = byName.get(name);
  const base = cat?.base ?? name;
  const first = cat ?? CATALOG.find(c => c.base === base);
  const notes = cat
    ? (entry.variants?.[variantKey(cat)] ? [[equipmentLabel(cat.equipment), entry.variants[variantKey(cat)]]] : [])
    : CATALOG.filter(c => c.base === base && entry.variants?.[variantKey(c)]).map(c => [exName(c.name), entry.variants[variantKey(c)]]);
  const m = muscles ? musclesOf(name) : null;
  return [
    header ? h('div', { class: 'wiki-head' },
      h('span', { html: exerciseIconHtml(first.name, first.group, 48) }),
      h('div', null, h('h2', null, cat ? exName(name) : baseName(base)), h('p', { class: 'accent' }, t(entry.muscles)))) : null,
    m ? muscleMap(levelsOf(m), { tap: true }) : null,
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
      'General guidance – not a substitute for coaching. Stop if it hurts.'), 'small wiki-disclaimer'),
  ];
}


// ---------- Muskel-Wiki (entspricht MuscleWiki.kt), Inhalte: muscle-data.js aus assets/muscles.json ----------

/** Grundübungen (ohne Cardio), bei denen der Muskel Hauptarbeit leistet – alphabetisch */
export function mainExercisesFor(muscle) {
  const bases = Object.keys(WIKI).filter(b => CATALOG.some(c => c.base === b && !isCardio(c.group)))
    .filter(b => { const m = musclesOf(b); return m && Object.entries(levelsOf(m)).some(([k, v]) => muscleOfRegion(k) === muscle && v >= 0.8); });
  return sortByName(bases, baseName);
}

export const openMuscle = id => { if (MUSCLE_INFO[id]) openDialog(() => muscleSheet(id)); };

function muscleSheet(id) {
  return h('div', { class: 'dialog-backdrop sheet-backdrop', onclick: e => { if (e.target === e.currentTarget) closeDialog(); } },
    h('div', { class: 'sheet', role: 'dialog' },
      h('div', { class: 'sheet-handle', onclick: closeDialog }),
      h('div', { class: 'sheet-body' }, muscleContent(id))));
}

function muscleContent(id) {
  const m = MUSCLE_INFO[id];
  const fact = (label, text) => h('div', { class: 'muscle-fact' }, h('b', { class: 'accent' }, label), h('p', null, text));
  const list = (title, items, cls = '') => items?.length ? [h('h3', null, title), h('ul', { class: 'wiki-list bullets ' + cls }, items.map(p => h('li', null, t(p))))] : null;
  const exercises = mainExercisesFor(id);
  return [
    h('div', null, h('h2', null, muscleLabel(id)), h('p', { class: 'accent' }, m.latin)),
    h('div', { class: 'muscle-map' }, bodyFigure(k => (muscleOfRegion(k) === id ? PRIMARY_COLOR : null))),
    h('p', null, t(m.parts)),
    h('h3', null, tr('Anatomie', 'Anatomy')),
    fact(tr('Ursprung', 'Origin'), t(m.origin)),
    fact(tr('Ansatz', 'Insertion'), t(m.insertion)),
    fact(tr('Nerv', 'Nerve'), t(m.nerve)),
    list(tr('Funktion', 'Function'), m.function),
    m.everyday ? [h('h3', null, tr('Im Alltag', 'In everyday life')), h('p', null, t(m.everyday))] : null,
    list(tr('Häufige Beschwerden & Verletzungen', 'Common problems & injuries'), m.problems, 'warn'),
    list(tr('Dehnen', 'Stretching'), m.stretch, 'stretch'),
    list(tr('Wissenswertes', 'Good to know'), m.facts, 'facts'),
    exercises.length ? [
      h('h3', null, tr('Übungen (Hauptarbeit)', 'Exercises (main work)')),
      exercises.map(b => {
        const c = CATALOG.find(x => x.base === b);
        return h('button', { class: 'cat-row', onclick: () => openWiki(b) },
          h('span', { html: exerciseIconHtml(c.name, c.group, 36) }),
          h('div', { class: 'grow' }, h('span', null, baseName(b))),
          h('span', { class: 'muted' }, icon('chevron')));
      }),
    ] : null,
    muted(tr('Allgemeine Informationen, keine Diagnose und kein Ersatz für ärztlichen Rat. Bei starken oder anhaltenden Schmerzen, Taubheit oder Lähmungen ärztlich abklären.',
      'General information, not a diagnosis or a substitute for medical advice. See a doctor for severe or persistent pain, numbness or weakness.'), 'small wiki-disclaimer'),
  ];
}

/** Reiter „Muskeln“: antippbare Figur (ohne Suche) + Liste */
function muscleList(query) {
  const q = query.trim().toLowerCase();
  const ids = sortByName(Object.keys(MUSCLE_INFO).filter(id => MUSCLES[id] && (!q ||
    [MUSCLES[id].de, MUSCLES[id].en, MUSCLE_INFO[id].latin, ...MUSCLE_INFO[id].parts].some(s => s.toLowerCase().includes(q)))), muscleLabel);
  return [
    q ? null : h('div', { class: 'muscle-wiki-figure' }, tappableFigure(() => null, openMuscle),
      muted(tr('Muskel antippen oder aus der Liste wählen', 'Tap a muscle or pick one from the list'), 'small center')),
    ids.length ? null : muted(tr('Kein Muskel gefunden.', 'No muscle found.')),
    ids.map(id => h('button', { class: 'cat-row', onclick: () => openMuscle(id) },
      h('div', { class: 'grow' }, h('span', null, muscleLabel(id)), h('small', { class: 'muted' }, MUSCLE_INFO[id].latin)),
      h('span', { class: 'muted' }, icon('chevron')))),
  ];
}

// ---------- Supplement-Wiki (entspricht SupplementWiki.kt), Inhalte: supplement-data.js aus assets/supplements.json ----------

/** Kleinbuchstaben, Umlaute ausgeschrieben (wie Bot.norm) – für die Suche */
const norm = s => s.toLowerCase().replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss').replace(/[^a-z0-9]+/g, ' ').trim();
export const evidenceLabel = l => [tr('Kein Nutzen belegt', 'No proven benefit'), tr('Schwach belegt', 'Weak evidence'), tr('Teilweise belegt', 'Some evidence'), tr('Gut belegt', 'Well proven')][l] ?? '';
export const evidenceColor = l => ['#9E9E9E', '#EF6C00', '#F9A825', '#43A047'][l] ?? '#9E9E9E';
const evidenceBadge = l => h('span', { class: 'evidence-badge', style: `--ev:${evidenceColor(l)}` }, h('i'), evidenceLabel(l));

export const openSupplement = id => { if (SUPPLEMENTS[id]) openDialog(() => supplementSheet(id)); };

function supplementSheet(id) {
  const s = SUPPLEMENTS[id];
  const list = (title, items, cls = '') => items?.length ? [h('h3', null, title), h('ul', { class: 'wiki-list bullets ' + cls }, items.map(p => h('li', null, t(p))))] : null;
  return h('div', { class: 'dialog-backdrop sheet-backdrop', onclick: e => { if (e.target === e.currentTarget) closeDialog(); } },
    h('div', { class: 'sheet', role: 'dialog' },
      h('div', { class: 'sheet-handle', onclick: closeDialog }),
      h('div', { class: 'sheet-body' },
        h('div', null, h('h2', null, t(s.name)), h('p', { class: 'accent' }, t(s.category))),
        h('div', { class: 'row-s' }, evidenceBadge(s.evidence), s.deficiencyOnly ? h('span', { class: 'deficiency-badge' }, tr('Nur bei Mangel', 'Only if deficient')) : null),
        h('p', { class: 'supp-summary' }, t(s.summary)),
        h('h3', null, tr('Was ist das?', 'What is it?')), h('p', null, t(s.what)),
        list(tr('Wirkung', 'Effect'), s.effects),
        h('h3', null, tr('Dosierung', 'Dosage')), h('p', null, t(s.dose)),
        s.timing[0] !== '–' ? [h('h3', null, tr('Einnahme', 'When to take')), h('p', null, t(s.timing))] : null,
        list(tr('Nebenwirkungen & Wechselwirkungen', 'Side effects & interactions'), s.risks, 'warn'),
        list(tr('Gut zu wissen', 'Good to know'), s.tips, 'facts'),
        muted(tr('Allgemeine Informationen, keine Beratung. Bei Erkrankungen, Medikamenten, Schwangerschaft oder Stillzeit vorher ärztlich abklären. Wettkampfsportler: nur geprüfte Produkte (z. B. Kölner Liste) wegen Dopinggefahr.',
          'General information, not advice. With medical conditions, medication, pregnancy or breastfeeding ask a doctor first. Competitive athletes: only tested products (e.g. Kölner Liste) because of doping risk.'), 'small wiki-disclaimer'))));
}

/** Reiter „Supplements“: nach Evidenz gruppiert (gut belegt zuerst), innerhalb alphabetisch */
function supplementList(query) {
  const q = norm(query);
  const all = Object.entries(SUPPLEMENTS).filter(([, s]) => !q ||
    [...s.name, ...s.category].some(x => norm(x).includes(q)) || s.keys.some(k => norm(k).includes(q)));
  return [
    q ? null : muted(tr('Was bringt was? Einstufung nach aktueller Studienlage – das meiste erreichst du über Training, Schlaf und Ernährung.',
      'What actually works? Rated by current evidence – most results come from training, sleep and diet.'), 'small'),
    all.length ? null : muted(tr('Kein Supplement gefunden.', 'No supplement found.')),
    [3, 2, 1, 0].map(level => {
      const group = sortByName(all.filter(([, s]) => s.evidence === level), ([, s]) => t(s.name));
      return group.length ? [
        h('div', { class: 'evidence-header' }, h('i', { style: `background:${evidenceColor(level)}` }), evidenceLabel(level)),
        group.map(([id, s]) => h('button', { class: 'cat-row', onclick: () => openSupplement(id) },
          h('div', { class: 'grow' }, h('span', null, t(s.name)),
            h('small', { class: 'muted' }, (s.deficiencyOnly ? tr('Nur bei Mangel · ', 'Only if deficient · ') : '') + t(s.summary))),
          h('span', { class: 'muted' }, icon('chevron')))),
      ] : null;
    }),
  ];
}

/** Wiki-Übersicht: Reiter Übungen (alle Grundübungen nach Muskelgruppe) / Muskeln / Supplements, mit Suche. */
export function wikiScreen() {
  const tab = nav.wikiTab;
  const muscles = tab === 1;
  const q = nav.wikiQuery.trim().toLowerCase();
  const bases = [];
  const seen = new Set();
  for (const c of CATALOG) {
    if (seen.has(c.base) || !WIKI[c.base]) continue;
    seen.add(c.base);
    if (!q || CATALOG.some(v => v.base === c.base && (v.name.toLowerCase().includes(q) || v.en.toLowerCase().includes(q)))) bases.push(c);
  }
  const byGroup = GROUP_KEYS.map(g => [g, sortByName(bases.filter(c => c.group === g), c => baseName(c.base))]).filter(([, l]) => l.length);

  return screen({
    title: tr('Wiki', 'Wiki'), back: true, key: 'wiki',
    actions: [iconBtn('chat', () => update({ bot: true }), { label: tr('Fragen', 'Ask') })], // Frage an den Bot
    content: h('div', { class: 'pad' },
      h('div', { class: 'segments wiki-tabs' },
        [tr('Übungen', 'Exercises'), tr('Muskeln', 'Muscles'), 'Supplements'].map((label, i) =>
          chip(label, tab === i, () => update({ wikiTab: i, wikiQuery: '' })))),
      h('label', { class: 'search wiki-search' }, icon('search', 20),
        h('input', {
          id: 'wiki-search', type: 'search', value: nav.wikiQuery,
          placeholder: [tr('Übung suchen', 'Search exercise'), tr('Muskel suchen', 'Search muscle'), tr('Supplement suchen', 'Search supplement')][tab],
          autocomplete: 'off', enterkeyhint: 'search',
          oninput: e => update({ wikiQuery: e.target.value }),
          onkeydown: e => { if (e.key === 'Enter') e.target.blur(); },
        })),
      tab === 2 ? supplementList(nav.wikiQuery) : muscles ? muscleList(nav.wikiQuery) : [
        byGroup.length ? null : muted(tr('Keine Übung gefunden.', 'No exercise found.')),
        byGroup.map(([g, list]) => [groupHeader(g), list.map(c =>
          h('button', { class: 'cat-row', onclick: () => openWiki(c.base) },
            h('span', { html: exerciseIconHtml(c.name, g, 40) }),
            h('div', { class: 'grow' }, h('span', null, baseName(c.base)), h('small', { class: 'muted' }, t(WIKI[c.base].muscles))),
            h('span', { class: 'muted' }, icon('chevron'))))]),
      ]),
  });
}

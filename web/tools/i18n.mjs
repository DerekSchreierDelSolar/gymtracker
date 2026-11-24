// Sammelt alle deutschen Texte der App (Android + Web + Inhalte) und prüft die Übersetzungsdateien.
//
// Aufruf (im Projektordner):   node web/tools/i18n.mjs            → Bericht: was fehlt je Sprache
//                              node web/tools/i18n.mjs --todo fr  → schreibt i18n-todo/fr-*.json (fehlende Texte, je Datei ~N)
//                              node web/tools/i18n.mjs --prune    → entfernt Übersetzungen, deren deutscher Text nicht mehr vorkommt
//
// Prinzip: tr("de", "en") bleibt im Code. Für weitere Sprachen gibt es je eine Tabelle
// app/src/main/assets/i18n/<lang>.json = { "deutscher Text": "Übersetzung" } (wie gettext, Schlüssel = deutscher Text).
// Texte mit eingesetzten Werten (Kotlin "$n Sätze", JS `${n} Sätze`) stehen als Vorlage mit {0}, {1} … drin:
//   "{0} Sätze": "{0} séries"  – zur Laufzeit wird der fertige deutsche Text gegen die Vorlage gematcht.
// Fehlt eine Übersetzung, zeigt die App Englisch.

import { readFileSync, writeFileSync, readdirSync, mkdirSync, existsSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const LANGS = ['fr', 'it', 'es', 'tr'];
const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const ktDir = join(root, 'app/src/main/java/com/example/gymtracker');
const assets = join(root, 'app/src/main/assets');
const i18nDir = join(assets, 'i18n');
/** Trenner für Varianten „deutsch␟englisch“ (gleicher deutscher Text, andere Bedeutung) – wie in Translations.lookup / core.js */
const SEP = '␟';
const VARIANTS = JSON.parse(readFileSync(join(root, 'web/tools/i18n-variants.json'), 'utf8'));

// ---------- String-Literale lesen ----------

/** Kotlin-String ab text[i] (" oder """) → { parts: [Text | {expr}], end } */
function ktString(text, i) {
  const raw = text.startsWith('"""', i);
  let j = i + (raw ? 3 : 1);
  const parts = [];
  let cur = '';
  while (j < text.length) {
    if (raw ? text.startsWith('"""', j) : text[j] === '"') { j += raw ? 3 : 1; break; }
    const c = text[j];
    if (!raw && c === '\\') {
      const n = text[j + 1];
      const map = { n: '\n', t: '\t', '"': '"', '\\': '\\', $: '$', "'": "'", r: '\r', b: '\b' };
      if (n === 'u') { cur += String.fromCharCode(parseInt(text.slice(j + 2, j + 6), 16)); j += 6; continue; }
      cur += map[n] ?? n; j += 2; continue;
    }
    if (c === '$' && text[j + 1] === '{') {
      const end = matchBrace(text, j + 1, 'kt');
      if (cur) parts.push(cur); cur = '';
      parts.push({ expr: text.slice(j + 2, end).trim() });
      j = end + 1; continue;
    }
    if (c === '$' && /[A-Za-z_]/.test(text[j + 1] ?? '')) {
      let k = j + 1;
      while (/[A-Za-z0-9_]/.test(text[k])) k++;
      if (cur) parts.push(cur); cur = '';
      parts.push({ expr: text.slice(j + 1, k) });
      j = k; continue;
    }
    cur += c; j++;
  }
  if (cur) parts.push(cur);
  return { parts, end: j };
}

/** JS-String ab text[i] (' " `) */
function jsString(text, i) {
  const q = text[i];
  let j = i + 1;
  const parts = [];
  let cur = '';
  while (j < text.length) {
    const c = text[j];
    if (c === q) { j++; break; }
    if (c === '\\') {
      const n = text[j + 1];
      const map = { n: '\n', t: '\t', r: '\r', b: '\b' };
      if (n === 'u') {
        if (text[j + 2] === '{') { const e = text.indexOf('}', j); cur += String.fromCodePoint(parseInt(text.slice(j + 3, e), 16)); j = e + 1; continue; }
        cur += String.fromCharCode(parseInt(text.slice(j + 2, j + 6), 16)); j += 6; continue;
      }
      cur += map[n] ?? n; j += 2; continue;
    }
    if (q === '`' && c === '$' && text[j + 1] === '{') {
      const end = matchBrace(text, j + 1, 'js');
      if (cur) parts.push(cur); cur = '';
      parts.push({ expr: text.slice(j + 2, end).trim() });
      j = end + 1; continue;
    }
    cur += c; j++;
  }
  if (cur) parts.push(cur);
  return { parts, end: j };
}

/** Index der schließenden Klammer zu text[i] ( ( [ { ), Strings werden übersprungen */
function matchBrace(text, i, lang) {
  const open = text[i], close = { '(': ')', '[': ']', '{': '}' }[open];
  let depth = 0;
  for (let j = i; j < text.length; j++) {
    const c = text[j];
    if (c === '"' || (lang === 'js' && (c === "'" || c === '`'))) {
      j = (lang === 'kt' ? ktString(text, j) : jsString(text, j)).end - 1; continue;
    }
    if (lang === 'kt' && c === '\'') { j = text.indexOf('\'', j + (text[j + 1] === '\\' ? 3 : 2)); continue; }
    if (c === '/' && text[j + 1] === '/') { j = text.indexOf('\n', j); if (j < 0) return text.length; continue; }
    if (c === '/' && text[j + 1] === '*') { j = text.indexOf('*/', j) + 1; continue; }
    if (c === open) depth++;
    else if (c === close && --depth === 0) return j;
  }
  return text.length;
}

/** Argumente eines Aufrufs ab der öffnenden Klammer, je als Rohtext */
function splitArgs(text, open, lang) {
  const close = matchBrace(text, open, lang);
  const args = [];
  let start = open + 1, depth = 0;
  for (let j = open + 1; j < close; j++) {
    const c = text[j];
    if (c === '"' || (lang === 'js' && (c === "'" || c === '`'))) { j = (lang === 'kt' ? ktString(text, j) : jsString(text, j)).end - 1; continue; }
    if (c === '/' && text[j + 1] === '/') { j = text.indexOf('\n', j); continue; }
    if ('([{'.includes(c)) depth++;
    else if (')]}'.includes(c)) depth--;
    else if (c === ',' && depth === 0) { args.push(text.slice(start, j)); start = j + 1; }
  }
  args.push(text.slice(start, close));
  return { args: args.map(a => a.trim()), end: close };
}

/** Ausdruck aus String-Literalen, verbunden mit + → parts, sonst null */
function literalExpr(src, lang) {
  const parts = [];
  let i = 0;
  const s = src.trim();
  while (i < s.length) {
    while (/\s/.test(s[i])) i++;
    const c = s[i];
    if (!(c === '"' || (lang === 'js' && (c === "'" || c === '`')))) return null;
    const r = lang === 'kt' ? ktString(s, i) : jsString(s, i);
    parts.push(...r.parts);
    i = r.end;
    while (/\s/.test(s[i] ?? '')) i++;
    if (i >= s.length) break;
    if (s[i] !== '+') return null;
    i++;
  }
  return parts.length ? parts : null;
}

/** de- und en-Teile → Schlüssel mit {0}, {1} … (Nummern nach Reihenfolge im Deutschen) */
function toKey(deParts, enParts) {
  const exprs = [];
  const idx = e => { let k = exprs.indexOf(e); if (k < 0) { exprs.push(e); k = exprs.length - 1; } return `{${k}}`; };
  const de = deParts.map(p => (typeof p === 'string' ? p : idx(p.expr))).join('');
  const en = enParts ? enParts.map(p => (typeof p === 'string' ? p : idx(p.expr))).join('') : null;
  return { de, en, args: exprs.length };
}

/** Gleiche Platzhalter in Schlüssel und Übersetzung? */
const holes = s => [...new Set(s.match(/\{\d+\}/g) ?? [])].sort().join();

// ---------- Quellen ----------

const entries = new Map(); // de → { en, src: Set }
const skipped = [];
const mismatched = [];
function add(de, en, src) {
  if (de == null || !/\p{L}/u.test(de.replace(/\{\d+\}/g, ''))) return; // nur Zahlen/Zeichen → nichts zu übersetzen
  if (de === en && /^[\p{Lu}0-9 .×·:\-–/]+$/u.test(de)) return; // Abkürzungen wie "RIR", "1RM", "kg"
  const e = entries.get(de) ?? { en, src: new Set(), ens: new Set() };
  if (e.en == null) e.en = en;
  if (en != null) e.ens.add(en);
  e.src.add(src);
  entries.set(de, e);
}

function scanCode(file, text, lang) {
  const re = lang === 'kt' ? /(?<![\w.])tr\(/g : /(?<![\w.$])tr\(/g;
  for (const m of text.matchAll(re)) {
    const open = m.index + 2;
    const { args } = splitArgs(text, open, lang);
    if (args.length < 2) continue;
    const de = literalExpr(args[0], lang), en = literalExpr(args[1], lang);
    const line = text.slice(0, m.index).split('\n').length;
    if (!de) { skipped.push(`${file}:${line}  tr(${args[0].slice(0, 60)}`); continue; }
    const k = toKey(de, en);
    // Platzhalter nur im Englischen → der Wert im Deutschen ist ein anderer Ausdruck (z. B. deutsches plural())
    if (k.en && holes(k.en.replace(/\{(\d+)\}/g, (x, d) => (k.de.includes(x) ? x : '{' + d + '}'))) !== holes(k.de))
      mismatched.push(`${file}:${line}  ${JSON.stringify(k.de)} / ${JSON.stringify(k.en)}`);
    add(k.de, k.en, `${file}:${line}`);
  }
  if (lang === 'kt') {
    // Paare in Konstruktoren / Enum-Einträgen: WikiText("Brust", "Chest"), BRUST("Brust", "Chest", …), WorkoutTemplate("…", "…", …)
    for (const m of text.matchAll(/(?<![\w.])([A-Z][A-Za-z_]*)\(\s*"/g)) {
      const name = m[1];
      if (name === 'CatalogExercise' || name === 'Locale') continue;
      const { args } = splitArgs(text, m.index + name.length, lang);
      const lits = args.map(a => literalExpr(a, lang)).map(p => (p && p.every(x => typeof x === 'string') ? p.join('') : null));
      const line = text.slice(0, m.index).split('\n').length;
      // SetType("A", "W", "Aufwärmen", "Warm-up") → Paare (0,1), (2,3); MeasureType(unit, min, max, "Taille", "Waist") → letzte zwei
      for (let a = 0; a + 1 < lits.length; a++) {
        if (lits[a] != null && lits[a + 1] != null && /\p{L}/u.test(lits[a + 1])) { add(lits[a], lits[a + 1], `${file}:${line}`); a++; }
      }
    }
  }
}

// Sprüche: "de" to "en" (Quotes.kt)
const quotesKt = readFileSync(join(ktDir, 'Quotes.kt'), 'utf8');
for (const m of quotesKt.matchAll(/^\s*("(?:[^"\\]|\\.)*")\s+to\s+("(?:[^"\\]|\\.)*")/gm)) add(ktString(m[1], 0).parts.join(''), ktString(m[2], 0).parts.join(''), 'Quotes.kt');
// Wochentage (Plan.kt: listOf("Mo", …) / listOf("Mon", …))
{
  const plan = readFileSync(join(ktDir, 'Plan.kt'), 'utf8');
  const lists = [...plan.matchAll(/listOf\(((?:"[^"]*",?\s*){7})\)/g)].map(m => [...m[1].matchAll(/"([^"]*)"/g)].map(x => x[1]));
  for (let i = 0; i + 1 < lists.length; i += 2) lists[i].forEach((d, k) => add(d, lists[i + 1][k], 'Plan.kt'));
}
for (const f of readdirSync(ktDir)) if (f.endsWith('.kt')) scanCode(f, readFileSync(join(ktDir, f), 'utf8'), 'kt');
const GENERATED = new Set(['data.js', 'wiki-data.js', 'body-data.js', 'muscle-data.js', 'supplement-data.js', 'bot-data.js', 'image-data.js']);
for (const dir of ['web/js', 'web/js/screens']) {
  for (const f of readdirSync(join(root, dir))) {
    if (!f.endsWith('.js') || GENERATED.has(f)) continue;
    scanCode(`${dir.slice(4)}/${f}`, readFileSync(join(root, dir, f), 'utf8'), 'js');
  }
}

// Katalog: Übungsname + Grundübung
const catalogKt = readFileSync(join(ktDir, 'Catalog.kt'), 'utf8');
const catalog = [...catalogKt.matchAll(/CatalogExercise\("([^"]*)", "([^"]*)", MuscleGroup\.(\w+), Equipment\.(\w+), "([^"]*)"\)/g)]
  .map(m => ({ name: m[1], base: m[2], en: m[5] }));
for (const c of catalog) add(c.name, c.en, 'catalog');
const baseEn = new Map();
for (const c of catalog) {
  const en = c.en.replace(/ \(.*\)$/, '');
  if (!baseEn.has(c.base) || en.length < baseEn.get(c.base).length) baseEn.set(c.base, en);
}
for (const [b, en] of baseEn) add(b, en, 'catalog-base');

// Inhalte: alle [de, en]-Paare in den JSON-Dateien
function pairsIn(v, src, key = '') {
  if (Array.isArray(v)) {
    if (v.length === 2 && v.every(x => typeof x === 'string') && !['keys', 'primary', 'secondary'].includes(key)) add(v[0], v[1], src);
    else v.forEach(x => pairsIn(x, src, key));
  } else if (v && typeof v === 'object') for (const [k, x] of Object.entries(v)) pairsIn(x, src, k);
}
for (const f of readdirSync(join(assets, 'wiki'))) pairsIn(JSON.parse(readFileSync(join(assets, 'wiki', f), 'utf8')), `wiki/${f}`);
pairsIn(JSON.parse(readFileSync(join(assets, 'muscles.json'), 'utf8')), 'muscles.json');
pairsIn(JSON.parse(readFileSync(join(assets, 'supplements.json'), 'utf8')), 'supplements.json');
const bot = JSON.parse(readFileSync(join(assets, 'bot.json'), 'utf8'));
for (const q of bot.faq) add(q.a[0], q.a[1], 'bot.json');

// ---------- Bericht ----------

const mode = process.argv[2];
const sourceOf = e => [...e.src][0];
const all = [...entries.entries()];
const load = l => (existsSync(join(i18nDir, `${l}.json`)) ? JSON.parse(readFileSync(join(i18nDir, `${l}.json`), 'utf8')) : {});


if (mode === '--merge') {
  // i18n-todo/out/<lang>-*.json (fertige Pakete) in assets/i18n/<lang>.json übernehmen – nur bekannte Texte mit passenden Platzhaltern
  const outDir = join(root, 'i18n-todo', 'out');
  for (const l of LANGS) {
    const have = load(l);
    let added = 0;
    const files = existsSync(outDir) ? readdirSync(outDir).filter(f => f.startsWith(l + '-') && f.endsWith('.json')) : [];
    for (const f of files) {
      let o;
      try { o = JSON.parse(readFileSync(join(outDir, f), 'utf8')); } catch (e) { console.log(`  ${f}: kein gültiges JSON – übersprungen`); continue; }
      for (const [k, v] of Object.entries(o)) {
        if (typeof v !== 'string' || (!v.trim() && k.trim().length > 3)) continue; // leer nur bei kurzen Bindestücken (z. B. „Am “ im Türkischen)
        if (!entries.has(k)) { console.log(`  ${f}: unbekannter Schlüssel ${JSON.stringify(k).slice(0, 70)}`); continue; }
        if (holes(k) !== holes(v)) { console.log(`  ${f}: Platzhalter passen nicht ${JSON.stringify(k).slice(0, 70)}`); continue; }
        if (have[k] !== v) { have[k] = v; added++; }
      }
    }
    // Reihenfolge wie in der Quelle (stabile Diffs)
    const sorted = Object.fromEntries(all.filter(([de]) => have[de] != null).map(([de]) => [de, have[de]]));
    // Varianten „de␟en“ für gleiche deutsche Texte mit verschiedenem Englisch: von Hand (i18n-variants.json),
    // sonst bei reiner Groß-/Kleinschreibung automatisch (Anfangsbuchstabe wie im englischen Text)
    const manual = VARIANTS[l] ?? {};
    const upper = s => s.charAt(0).toLocaleUpperCase(l) + s.slice(1), lower = s => s.charAt(0).toLocaleLowerCase(l) + s.slice(1);
    for (const [de, e] of all) {
      if (e.ens.size < 2 || sorted[de] == null) continue;
      for (const v of e.ens) {
        const key = de + SEP + v;
        if (manual[key] != null) { sorted[key] = manual[key]; continue; }
        if (v.toLowerCase() !== e.en?.toLowerCase()) continue;
        const t = sorted[de];
        const want = /^\p{Lu}/u.test(v) ? upper(t) : /^\p{Ll}/u.test(v) ? lower(t) : t;
        if (want !== t) sorted[key] = want;
      }
    }
    mkdirSync(i18nDir, { recursive: true });
    writeFileSync(join(i18nDir, `${l}.json`), JSON.stringify(sorted, null, 1) + '\n');
    console.log(`${l}: ${files.length} Pakete, ${added} neu/geändert, ${Object.keys(sorted).filter(k => !k.includes(SEP)).length}/${all.length} übersetzt, ${Object.keys(sorted).filter(k => k.includes(SEP)).length} Varianten`);
  }
} else if (mode === '--todo') {
  const lang = process.argv[3];
  const size = Number(process.argv[4] ?? 15000); // Zeichen je Datei
  const have = load(lang);
  const outDir = join(root, 'i18n-todo');
  mkdirSync(outDir, { recursive: true });
  for (const f of readdirSync(outDir)) if (f.startsWith(lang + '-')) rmSync(join(outDir, f));
  let n = 0, chunk = [], len = 0;
  const flush = () => { if (!chunk.length) return; writeFileSync(join(outDir, `${lang}-${String(++n).padStart(3, '0')}.json`), JSON.stringify(chunk, null, 1)); chunk = []; len = 0; };
  // nach Quelle gruppiert, damit zusammengehörige Texte in einer Datei landen
  const missing = all.filter(([de]) => have[de] == null).sort((a, b) => sourceOf(a[1]).localeCompare(sourceOf(b[1])));
  for (const [de, e] of missing) {
    chunk.push({ de, en: e.en, src: sourceOf(e) });
    len += de.length + (e.en?.length ?? 0);
    if (len > size) flush();
  }
  flush();
  console.log(`${lang}: ${missing.length} fehlende Texte → ${n} Dateien in i18n-todo/`);
} else {
  let chars = 0;
  for (const [de] of all) chars += de.length;
  console.log(`${all.length} Texte, ${chars} Zeichen (deutsch)`);
  for (const l of LANGS) {
    const have = load(l);
    const missing = all.filter(([de]) => have[de] == null).length;
    const unused = Object.keys(have).filter(k => !entries.has(k.split(SEP)[0]));
    const bad = Object.entries(have).filter(([k, v]) => entries.has(k) && holes(k) !== holes(v));
    console.log(`${l}: ${all.length - missing} übersetzt, ${missing} fehlen, ${unused.length} unbenutzt, ${bad.length} mit falschen Platzhaltern`);
    for (const [k, v] of bad) console.log(`   Platzhalter: ${JSON.stringify(k)} → ${JSON.stringify(v)}`);
    if (mode === '--prune' && unused.length) {
      for (const k of unused) delete have[k];
      writeFileSync(join(i18nDir, `${l}.json`), JSON.stringify(have, null, 1) + '\n');
    }
  }
  mismatched.forEach(s => console.log('  Ausdrücke de ≠ en: ' + s));
  // Gleicher deutscher Text mit verschiedenem Englisch → Tabelle braucht eigene Einträge „de␟en“ (siehe Translations.lookup)
  const ambiguous = all.filter(([, e]) => e.ens.size > 1);
  if (mode === '--ambiguous') ambiguous.forEach(([de, e]) => console.log(`  mehrdeutig: ${JSON.stringify(de)} → ${[...e.ens].map(x => JSON.stringify(x)).join(' | ')}`));
  else console.log(`${ambiguous.length} deutsche Texte mit verschiedenem Englisch – Anzeige mit --ambiguous`);
  if (mode === '--skipped') skipped.forEach(s => console.log('  nicht literal: ' + s));
  else console.log(`${skipped.length} tr()-Aufrufe ohne festen Text (Variablen) – Anzeige mit --skipped`);
}

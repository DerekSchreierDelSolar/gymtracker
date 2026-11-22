// Erzeugt web/js/data.js aus den Kotlin-Quellen der Android-App.
// So gibt es Katalog, Vorlagen, MET-Werte, Umbenennungen und Piktogramme nur EINMAL (im Kotlin-Code).
//
// Aufruf (im Projektordner):   node web/tools/gen-data.mjs
// Nach jeder Änderung an Catalog.kt / Model.kt / Pictograms.kt / assets/wiki/*.json / assets/muscles.json erneut ausführen.
// Erzeugt zusätzlich web/js/wiki-data.js (Übungs-Wiki), web/js/body-data.js (Körperdiagramm)
// und kopiert die Übungsbilder nach web/img/exercises/ (Liste in web/js/image-data.js).

import { readFileSync, writeFileSync, readdirSync, mkdirSync, copyFileSync, existsSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const src = join(root, 'app/src/main/java/com/example/gymtracker');
const read = f => readFileSync(join(src, f), 'utf8');

const catalogKt = read('Catalog.kt');
const modelKt = read('Model.kt');
const pictoKt = read('Pictograms.kt');

/** Text zwischen `start` und der ersten Zeile, die nur `end` enthält. */
function block(text, start, end = ')') {
  const i = text.indexOf(start);
  if (i < 0) throw new Error('Nicht gefunden: ' + start);
  const rest = text.slice(i + start.length);
  const j = rest.search(new RegExp('^' + end.replace(/[()]/g, '\\$&') + '\\s*$', 'm'));
  return rest.slice(0, j);
}

// ----- Katalog -----
const catalog = [...block(catalogKt, 'val CATALOG = listOf(').matchAll(
  /CatalogExercise\("([^"]*)", "([^"]*)", MuscleGroup\.(\w+), Equipment\.(\w+), "([^"]*)"\)/g
)].map(m => ({ name: m[1], base: m[2], group: m[3], equipment: m[4], en: m[5] }));

const pairs = (text, re) => Object.fromEntries([...text.matchAll(re)].map(m => [m[1], m[2]]));

const cardioMet = Object.fromEntries(
  Object.entries(pairs(block(catalogKt, 'val CARDIO_MET = mapOf('), /"([^"]*)" to ([\d.]+)/g))
    .map(([k, v]) => [k, Number(v)])
);
const renamed = pairs(block(catalogKt, 'val RENAMED_EXERCISES: Map<String, String> = mapOf('), /"([^"]*)" to "([^"]*)"/g);
const strengthMet = Number(catalogKt.match(/STRENGTH_MET = ([\d.]+)/)[1]);
const minutesPerSet = Number(catalogKt.match(/MINUTES_PER_SET = ([\d.]+)/)[1]);

// ----- Model.kt: Zeitübungen, Vorlagen -----
// Achtung: Übungsnamen enthalten selbst Klammern („Dips (Körpergewicht)“) → nicht mit [^)]* suchen
const timed = [...modelKt.match(/val TIMED_CATALOG = setOf\((.*)\)\s*$/m)[1].matchAll(/"([^"]*)"/g)].map(m => m[1]);
const templates = [...block(modelKt, 'val TEMPLATES = listOf(').matchAll(
  /WorkoutTemplate\("([^"]*)", "([^"]*)", listOf\(([\s\S]*?)\n\s*\)\)/g
)].map(m => ({ de: m[1], en: m[2], exercises: [...m[3].matchAll(/"([^"]*)"/g)].map(x => x[1]) }));

// ----- Piktogramme -----
const motionOf = pairs(block(pictoKt, 'val MOTION_OF: Map<String, Motion> = mapOf('), /"([^"]*)" to Motion\.(\w+)/g);
const defaultMotion = pairs(
  pictoKt.slice(pictoKt.indexOf('fun MuscleGroup.defaultMotion()')),
  /MuscleGroup\.(\w+) -> Motion\.(\w+)/g
);

// Zeichen-Code: der `when (m) { … }`-Block aus drawMotion, Zeile für Zeile nach JS übersetzt
const drawStart = pictoKt.indexOf('    when (m) {', pictoKt.indexOf('fun DrawScope.drawMotion'));
const drawBody = pictoKt.slice(drawStart).split('\n').slice(1);
const js = [];
for (const line of drawBody) {
  if (/^    \}\s*$/.test(line)) break;           // Ende des when-Blocks
  let l = line
    .replace(/^(\s*)Motion\.(\w+) -> \{/, "$1case '$2': {")
    .replace(/drawRoundRect\(c, p\(([^,]+), ([^)]+)\), Size\(([^*]+) \* u, ([^*]+) \* u\), CornerRadius\(([^*]+) \* u\)\)/g,
      'rrect($1, $2, $3, $4, $5)')
    .replace(/drawArc\(c, ([^,]+), ([^,]+), false, p\(([^,]+), ([^)]+)\), Size\(([^*]+) \* u, ([^*]+) \* u\), style = Stroke\(([^*]+) \* u\)\)/g,
      'arc($3, $4, $5, $6, $1, $2, $7)')
    .replace(/vertical = true/g, 'true')
    .replace(/(\d)f\b/g, '$1');
  if (/^        \}\s*$/.test(l)) l = '        break; }';
  js.push(l);
}
const leftover = js.join('\n').match(/\b(drawLine|drawCircle|drawPath|Offset|Size|Stroke|Color)\b/);
if (leftover) throw new Error('Nicht übersetzter Kotlin-Code im Piktogramm: ' + leftover[0]);

// ----- Prüfungen -----
const names = new Set(catalog.map(c => c.name));
const missing = catalog.filter(c => !motionOf[c.name]).map(c => c.name);
if (missing.length) throw new Error('MOTION_OF fehlt für: ' + missing.join(', '));
for (const t of templates) for (const e of t.exercises) if (!names.has(e)) throw new Error('Vorlage mit unbekannter Übung: ' + e);

const out = `// AUTOMATISCH ERZEUGT von web/tools/gen-data.mjs aus den Kotlin-Quellen – nicht von Hand ändern!
/* eslint-disable */

export const CATALOG = ${JSON.stringify(catalog, null, 0).replace(/\},\{/g, '},\n  {')};

export const CARDIO_MET = ${JSON.stringify(cardioMet)};
export const STRENGTH_MET = ${strengthMet};
export const MINUTES_PER_SET = ${minutesPerSet};

export const RENAMED_EXERCISES = ${JSON.stringify(renamed, null, 0).replace(/","/g, '",\n  "')};

export const TIMED_CATALOG = new Set(${JSON.stringify(timed)});

export const TEMPLATES = ${JSON.stringify(templates)};

export const MOTION_OF = ${JSON.stringify(motionOf)};
export const DEFAULT_MOTION = ${JSON.stringify(defaultMotion)};

/** Zeichnet eine Bewegung mit den Hilfsfunktionen aus pictograms.js (Koordinaten 0..1). */
export function drawMotionWith(m, { line, limb, torso, thin, head, plate, ring, dumbbell, barbellFront, floor, rrect, arc }) {
    switch (m) {
${js.join('\n')}
    }
}
`;
writeFileSync(join(root, 'web/js/data.js'), out);
console.log(`data.js: ${catalog.length} Übungen, ${templates.length} Vorlagen, ${Object.keys(renamed).length} Umbenennungen, ` +
  `${new Set(Object.values(motionOf)).size} Bewegungen`);

// ----- Übungs-Wiki: JSON-Dateien aus den Android-Assets → web/js/wiki-data.js -----
const wikiDir = join(root, 'app/src/main/assets/wiki');
const wiki = {};
for (const f of readdirSync(wikiDir).filter(f => f.endsWith('.json')).sort()) {
  Object.assign(wiki, JSON.parse(readFileSync(join(wikiDir, f), 'utf8')));
}
const bases = new Set(catalog.map(c => c.base));
const noWiki = [...bases].filter(b => !wiki[b]);
if (noWiki.length) console.warn('Wiki fehlt für: ' + noWiki.join(', '));
for (const [b, e] of Object.entries(wiki)) {
  if (!bases.has(b)) throw new Error('Wiki: unbekannte Grundübung ' + b);
  for (const v of Object.keys(e.variants ?? {})) {
    if (!names.has(`${b} (${v})`)) throw new Error(`Wiki: unbekannte Variante ${b} (${v})`);
  }
}
writeFileSync(join(root, 'web/js/wiki-data.js'),
  `// AUTOMATISCH ERZEUGT von web/tools/gen-data.mjs aus app/src/main/assets/wiki/*.json – nicht von Hand ändern!\n` +
  `export const WIKI = ${JSON.stringify(wiki)};\n`);
console.log(`wiki-data.js: ${Object.keys(wiki).length} Einträge`);

// ----- Körperdiagramm (Meerschweinchen): Formen und Muskelnamen aus BodyMap.kt → web/js/body-data.js -----
const bodyKt = read('BodyMap.kt');
const muscles = Object.fromEntries([...block(bodyKt, 'val MUSCLES: Map<String, WikiText> = linkedMapOf(').matchAll(
  /"(\w+)" to WikiText\("([^"]*)", "([^"]*)"\)/g)].map(m => [m[1], { de: m[2], en: m[3] }]));
const bodyShapes = [...block(bodyKt, 'val BODY_SHAPES: List<BodyShape> = listOf(').matchAll(
  /BodyShape\((null|"\w+"), BodyView\.(\w+), "([^"]*)"([^)]*)\)/g)].map(m => ({
  id: m[1] === 'null' ? null : m[1].slice(1, -1),
  view: m[2],
  d: m[3],
  mirror: !/mirror = false/.test(m[4]),
  fill: m[4].match(/fill = BodyFill\.(\w+)/)?.[1] ?? (m[1] === 'null' ? 'FUR' : 'MUSCLE'),
  // Faserrichtung (Grad) und anatomische Fugen („u0 v0 uc vc u1 v1, …“) – siehe BodyShape in BodyMap.kt
  fiber: Number(m[4].match(/fiber = (-?[\d.]+)f/)?.[1] ?? 0),
  detail: m[4].match(/detail = "([^"]*)"/)?.[1] ?? '',
  // Regionen: ganze Fläche (region) bzw. Teilflächen „key: Polygon; …“ (regions)
  region: m[4].match(/region = "([^"]*)"/)?.[1] ?? '',
  regions: m[4].match(/regions = "([^"]*)"/)?.[1] ?? '',
}));
const regionNames = Object.fromEntries([...block(bodyKt, 'val REGION_NAMES: Map<String, WikiText> = linkedMapOf(').matchAll(
  /"([\w.]+)" to WikiText\("([^"]*)", "([^"]*)"\)/g)].map(m => [m[1], { de: m[2], en: m[3] }]));
const bodyConst = n => Number(bodyKt.match(new RegExp("const val " + n + " = ([0-9.]+)f"))[1]);
for (const s of bodyShapes) if (s.id && !muscles[s.id]) throw new Error('BodyMap: unbekannte Muskel-ID ' + s.id);
// Alle Regionsschlüssel der Formen (ungeteilte Muskeln: Muskel-ID)
const regionKeys = new Set(bodyShapes.flatMap(s => s.regions ? s.regions.split(';').map(r => r.split(':')[0].trim())
  : s.region ? [s.region] : s.fill === 'MUSCLE' ? [s.id] : []));
for (const k of regionKeys) if (k.includes('.') && !regionNames[k]) throw new Error('BodyMap: Region ohne Namen ' + k);
for (const [b, e] of Object.entries(wiki)) {
  for (const id of [...(e.primary ?? []), ...(e.secondary ?? [])]) if (!muscles[id]) throw new Error(`Wiki: unbekannte Muskel-ID ${id} bei ${b}`);
  for (const [k, v] of Object.entries(e.regions ?? {})) {
    if (!regionKeys.has(k)) throw new Error(`Wiki: unbekannte Region ${k} bei ${b}`);
    if (![1, 0.6, 0.3].includes(v)) throw new Error(`Wiki: Stufe ${v} bei ${b}/${k} (erlaubt: 1, 0.6, 0.3)`);
  }
}
writeFileSync(join(root, 'web/js/body-data.js'),
  `// AUTOMATISCH ERZEUGT von web/tools/gen-data.mjs aus BodyMap.kt – nicht von Hand ändern!\n` +
  `export const MUSCLES = ${JSON.stringify(muscles)};\n` +
  `export const REGION_NAMES = ${JSON.stringify(regionNames)};\n` +
  `export const BODY = { width: ${bodyConst('BODY_WIDTH')}, height: ${bodyConst('BODY_HEIGHT')}, backOffset: ${bodyConst('BACK_OFFSET')}, pad: ${bodyConst('BODY_PAD')} };\n` +
  `export const BODY_SHAPES = [\n${bodyShapes.map(s => '  ' + JSON.stringify(s)).join(',\n')}\n];\n`);
console.log(`body-data.js: ${bodyShapes.length} Formen, ${Object.keys(muscles).length} Muskeln`);

// ----- Muskel-Wiki: app/src/main/assets/muscles.json → web/js/muscle-data.js -----
const muscleInfo = JSON.parse(readFileSync(join(root, 'app/src/main/assets/muscles.json'), 'utf8'));
const isPair = a => Array.isArray(a) && a.length === 2 && a.every(x => typeof x === 'string');
for (const [id, m] of Object.entries(muscleInfo)) {
  if (!muscles[id]) throw new Error('muscles.json: unbekannte Muskel-ID ' + id);
  if (typeof m.latin !== 'string') throw new Error('muscles.json: latin fehlt bei ' + id);
  for (const k of ['parts', 'origin', 'insertion', 'nerve']) if (!isPair(m[k])) throw new Error(`muscles.json: ${k} = [de, en] fehlt bei ${id}`);
  if (m.everyday && !isPair(m.everyday)) throw new Error('muscles.json: everyday bei ' + id);
  for (const k of ['function', 'problems', 'stretch', 'facts']) if (m[k] && !(Array.isArray(m[k]) && m[k].every(isPair))) throw new Error(`muscles.json: ${k} bei ${id}`);
}
const noInfo = Object.keys(muscles).filter(id => !muscleInfo[id]);
if (noInfo.length) console.warn('Muskel-Wiki fehlt für: ' + noInfo.join(', '));
writeFileSync(join(root, 'web/js/muscle-data.js'),
  `// AUTOMATISCH ERZEUGT von web/tools/gen-data.mjs aus app/src/main/assets/muscles.json – nicht von Hand ändern!\n` +
  `export const MUSCLE_INFO = ${JSON.stringify(muscleInfo)};\n`);
console.log(`muscle-data.js: ${Object.keys(muscleInfo).length} Muskeln`);

// ----- Supplement-Wiki: app/src/main/assets/supplements.json → web/js/supplement-data.js -----
const supplements = JSON.parse(readFileSync(join(root, 'app/src/main/assets/supplements.json'), 'utf8'));
for (const [id, s] of Object.entries(supplements)) {
  for (const k of ['name', 'category', 'summary', 'what', 'dose', 'timing']) if (!isPair(s[k])) throw new Error(`supplements.json: ${k} = [de, en] fehlt bei ${id}`);
  for (const k of ['effects', 'risks', 'tips']) if (s[k] && !(Array.isArray(s[k]) && s[k].every(isPair))) throw new Error(`supplements.json: ${k} bei ${id}`);
  if (![0, 1, 2, 3].includes(s.evidence)) throw new Error('supplements.json: evidence 0–3 bei ' + id);
  if (!Array.isArray(s.keys) || !s.keys.length) throw new Error('supplements.json: keys fehlen bei ' + id);
}
writeFileSync(join(root, 'web/js/supplement-data.js'),
  `// AUTOMATISCH ERZEUGT von web/tools/gen-data.mjs aus app/src/main/assets/supplements.json – nicht von Hand ändern!\n` +
  `export const SUPPLEMENTS = ${JSON.stringify(supplements)};\n`);
console.log(`supplement-data.js: ${Object.keys(supplements).length} Supplements`);

// ----- Fragen-Bot: app/src/main/assets/bot.json → web/js/bot-data.js -----
const bot = JSON.parse(readFileSync(join(root, 'app/src/main/assets/bot.json'), 'utf8'));
for (const k of Object.keys(bot.targets)) if (!regionKeys.has(k) && !muscles[k]) throw new Error('bot.json: unbekannte Region/Muskel ' + k);
for (const f of bot.faq) if (f.a?.length !== 2 || !f.keys?.length) throw new Error('bot.json: Eintrag braucht keys und a = [de, en]');
for (const g of bot.workoutGroups ?? []) if (!Array.isArray(g.words) || !g.words.length || !g.muscles) throw new Error('bot.json workoutGroups: Eintrag braucht words und muscles');
for (const g of bot.workoutGroups ?? []) for (const [m, s] of Object.entries(g.muscles)) if (!muscles[m] || ![1, 0.6, 0.3].includes(s)) throw new Error('bot.json workoutGroups: unbekannter Muskel/Stufe ' + m + ' ' + s);
writeFileSync(join(root, 'web/js/bot-data.js'),
  `// AUTOMATISCH ERZEUGT von web/tools/gen-data.mjs aus app/src/main/assets/bot.json – nicht von Hand ändern!\n` +
  `export const BOT = ${JSON.stringify(bot)};\n`);
console.log(`bot-data.js: ${bot.faq.length} Fragen, ${Object.keys(bot.targets).length} Muskel-/Regionsbegriffe`);

// ----- Übungsbilder: app/src/main/assets/exercises/*.jpg → web/img/exercises/ + Liste in web/js/image-data.js -----
const imgSrc = join(root, 'app/src/main/assets/exercises');
const imgDst = join(root, 'web/img/exercises');
const imageKeys = existsSync(imgSrc) ? readdirSync(imgSrc).filter(f => f.endsWith('.jpg')).map(f => f.slice(0, -4)).sort() : [];
rmSync(imgDst, { recursive: true, force: true });
mkdirSync(imgDst, { recursive: true });
for (const k of imageKeys) copyFileSync(join(imgSrc, k + '.jpg'), join(imgDst, k + '.jpg'));
writeFileSync(join(root, 'web/js/image-data.js'),
  `// AUTOMATISCH ERZEUGT von web/tools/gen-data.mjs – nicht von Hand ändern!\n` +
  `export const EXERCISE_IMAGES = new Set(${JSON.stringify(imageKeys)});\n`);
console.log(`image-data.js: ${imageKeys.length} Übungsbilder`);

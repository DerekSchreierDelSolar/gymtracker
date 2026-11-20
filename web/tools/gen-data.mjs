// Erzeugt web/js/data.js aus den Kotlin-Quellen der Android-App.
// So gibt es Katalog, Vorlagen, MET-Werte, Umbenennungen und Piktogramme nur EINMAL (im Kotlin-Code).
//
// Aufruf (im Projektordner):   node web/tools/gen-data.mjs
// Nach jeder Änderung an Catalog.kt / Model.kt / Pictograms.kt / assets/wiki/*.json erneut ausführen.
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
}));
const bodyConst = n => Number(bodyKt.match(new RegExp("const val " + n + " = ([0-9.]+)f"))[1]);
for (const s of bodyShapes) if (s.id && !muscles[s.id]) throw new Error('BodyMap: unbekannte Muskel-ID ' + s.id);
for (const [b, e] of Object.entries(wiki)) {
  for (const id of [...(e.primary ?? []), ...(e.secondary ?? [])]) if (!muscles[id]) throw new Error(`Wiki: unbekannte Muskel-ID ${id} bei ${b}`);
}
writeFileSync(join(root, 'web/js/body-data.js'),
  `// AUTOMATISCH ERZEUGT von web/tools/gen-data.mjs aus BodyMap.kt – nicht von Hand ändern!\n` +
  `export const MUSCLES = ${JSON.stringify(muscles)};\n` +
  `export const BODY = { width: ${bodyConst('BODY_WIDTH')}, height: ${bodyConst('BODY_HEIGHT')}, backOffset: ${bodyConst('BACK_OFFSET')}, pad: ${bodyConst('BODY_PAD')} };\n` +
  `export const BODY_SHAPES = [\n${bodyShapes.map(s => '  ' + JSON.stringify(s)).join(',\n')}\n];\n`);
console.log(`body-data.js: ${bodyShapes.length} Formen, ${Object.keys(muscles).length} Muskeln`);

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

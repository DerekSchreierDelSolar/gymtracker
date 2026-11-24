// Plausibilitätsprüfung der übersetzten Pakete (i18n-todo/out) bzw. der Tabellen (assets/i18n):
// findet verrutschte Zuordnungen (Länge passt nicht zum englischen Text, andere Zahlen/Emojis) und falsche Sprache.
// Aufruf: node web/tools/i18n-check.mjs [lang …]          (prüft assets/i18n/<lang>.json)
//         node web/tools/i18n-check.mjs --out [lang …]    (prüft i18n-todo/out/<lang>-*.json gegen i18n-todo/<lang>-*.json)

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const args = process.argv.slice(2);
const outMode = args[0] === '--out';
const langs = args.filter(a => !a.startsWith('--'));
const LANGS = langs.length ? langs : ['fr', 'it', 'es', 'tr'];

// typische kurze Wörter je Sprache (für die Spracherkennung längerer Texte)
const MARKERS = {
  fr: /\b(les|des|une|est|pour|avec|ton|tes|pas|dans|sur|aux|tu|ou|mais|plus)\b/gi,
  it: /\b(il|gli|della|delle|che|per|non|una|sono|tuo|tua|dei|degli|alla|nel|più)\b/gi,
  es: /\b(el|los|las|del|que|para|una|tus|por|muy|más|pero|como|sin)\b/gi,
  tr: /\b(ve|bir|bu|için|ile|çok|daha|olarak|gibi|sen|senin|var|yok|kadar)\b/gi,
  en: /\b(the|and|you|your|with|this|that|what|how)\b/gi,
  de: /\b(der|die|das|und|mit|für|ist|nicht|dein|deine|eine|auf|zu)\b/gi,
};
const score = (s, l) => (s.match(MARKERS[l]) ?? []).length;
const nums = s => (s.replace(/\{\d+\}/g, '').match(/\d+([.,]\d+)?/g) ?? []).map(n => n.replace(',', '.')).sort().join(' ');
const emo = s => (s.match(/\p{Extended_Pictographic}/gu) ?? []).sort().join('');

function check(lang, pairs) {
  let n = 0;
  const bad = [];
  for (const { de, en, tr } of pairs) {
    n++;
    const why = [];
    const ref = en ?? de;
    if (ref.length >= 25) {
      const r = tr.length / ref.length;
      if (r < 0.45 || r > 2.6) why.push(`Länge ${r.toFixed(2)}`);
    }
    if (nums(de) !== nums(tr) && nums(ref) !== nums(tr)) why.push(`Zahlen ${nums(de)} → ${nums(tr)}`);
    if (emo(de) !== emo(tr)) why.push('Emoji');
    if (tr.length >= 60) {
      const own = score(tr, lang);
      const other = Math.max(...Object.keys(MARKERS).filter(l => l !== lang).map(l => score(tr, l)));
      if (own < other && other >= 3) why.push('Sprache?');
    }
    if (why.length) bad.push(`  ${why.join(', ')}\n    de: ${de.slice(0, 90)}\n    ${lang}: ${tr.slice(0, 90)}`);
  }
  console.log(`${lang}: ${n} geprüft, ${bad.length} auffällig`);
  bad.forEach(b => console.log(b));
}

for (const l of LANGS) {
  if (outMode) {
    const dir = join(root, 'i18n-todo');
    const pairs = [];
    for (const f of readdirSync(join(dir, 'out')).filter(f => f.startsWith(l + '-')).sort()) {
      const out = JSON.parse(readFileSync(join(dir, 'out', f), 'utf8'));
      const src = existsSync(join(dir, f)) ? JSON.parse(readFileSync(join(dir, f), 'utf8')) : [];
      for (const e of src) if (out[e.de] != null) pairs.push({ de: e.de, en: e.en, tr: out[e.de] });
    }
    check(l, pairs);
  } else {
    const t = JSON.parse(readFileSync(join(root, 'app/src/main/assets/i18n', `${l}.json`), 'utf8'));
    check(l, Object.entries(t).map(([de, tr]) => ({ de, en: null, tr })));
  }
}

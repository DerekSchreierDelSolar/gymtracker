// Fragen-Bot ohne KI – entspricht Bot.kt und BotTab.kt. Antwortet nur aus dem Übungs-Wiki (wiki-data.js)
// und einer festen Liste zu den App-Funktionen (bot-data.js, erzeugt aus app/src/main/assets/bot.json).

import { BOT } from './bot-data.js';
import { WIKI } from './wiki-data.js';
import { CATALOG } from './data.js';
import { tr, isEnglish, isCardio } from './core.js';
import { h, icon } from './ui.js';
import { rerender } from './nav.js';
import { baseName, openWiki } from './wiki.js';
import { levelsOf, levelLabels, levelTitle, regionLabel, muscleLabel, muscleOfRegion } from './bodymap.js';

// ---------- Text vergleichen ----------

/** Kleinbuchstaben, Umlaute ausgeschrieben, nur Buchstaben/Ziffern, ein Leerzeichen zwischen Wörtern */
const norm = s => s.toLowerCase().replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss')
  .replace(/[^a-z0-9]+/g, ' ').trim();
const tokens = s => norm(s).split(' ').filter(Boolean);

/** Gleiches Wort – bei längeren Wörtern auch mit 1 (ab 9 Buchstaben 2) Tippfehlern (Levenshtein) */
function close(a, b) {
  if (a === b) return true;
  const n = Math.min(a.length, b.length);
  if (n < 5) return false;
  const max = n >= 9 ? 2 : 1;
  if (Math.abs(a.length - b.length) > max) return false;
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    prev = cur;
  }
  return prev[b.length] <= max;
}

/** Kommt die Wortfolge `phrase` in `words` vor? */
function has(words, phrase) {
  if (!phrase.length || phrase.length > words.length) return false;
  for (let i = 0; i <= words.length - phrase.length; i++) if (phrase.every((p, k) => close(words[i + k], p))) return true;
  return false;
}
const hasAny = (words, list) => list.some(p => has(words, p));

const FAQ = BOT.faq.map(f => ({ keys: f.keys.map(tokens), a: f.a }));
const TARGETS = Object.entries(BOT.targets).map(([k, list]) => [k, list.map(tokens)]);
const W = Object.fromEntries(['exerciseWords', 'muscleWords', 'howWords', 'tipWords'].map(k => [k, BOT[k].map(tokens)]));

// ---------- Übungen und Muskeln erkennen ----------

/** Grundübung → Wortfolgen (deutscher Name, Teile bei „/“, englische Namen ohne Gerät) */
const EXERCISES = Object.keys(WIKI).map(base => {
  const names = [...base.split('/').map(s => s.trim()), base, ...CATALOG.filter(c => c.base === base).map(c => c.en.split(' (')[0])];
  const seen = new Set();
  return [base, names.map(tokens).filter(p => p.length && !seen.has(p.join(' ')) && seen.add(p.join(' ')))];
});

/** Beste Übereinstimmung: längste Wortfolge („Rumänisches Kreuzheben“ vor „Kreuzheben“) */
function best(entries, words) {
  let out = null;
  for (const [key, phrases] of entries) for (const p of phrases) {
    if (!has(words, p)) continue;
    const score = p.length * 1000 + p.join('').length;
    if (!out || score > out.score) out = { key, phrase: p, score };
  }
  return out;
}

const levelsOfBase = base => (WIKI[base] ? levelsOf({ primary: WIKI[base].primary ?? [], secondary: WIKI[base].secondary ?? [], regions: WIKI[base].regions ?? {} }) : {});
const t = pair => (isEnglish() ? pair[1] : pair[0]);

// ---------- Antworten ----------

const suggestions = () => [
  tr('Wie mache ich Kreuzheben?', 'How do I deadlift?'),
  tr('Übungen für obere Brust', 'Exercises for upper chest'),
  tr('Was ist ein Drop-Satz?', 'What is a drop set?'),
  tr('Wie mache ich ein Backup?', 'How do I back up?'),
  tr('Welche Muskeln trainiert Rudern sitzend?', 'Which muscles does seated row work?'),
  tr('Übungen für hintere Schulter', 'Exercises for rear delts'),
];

const greeting = () => ({
  bot: true,
  text: tr('Hallo! 👋 Ich kenne alle Übungen aus dem Wiki und die Funktionen der App. Frag mich z. B. nach der Ausführung einer Übung, nach Übungen für einen Muskel oder wie etwas in der App funktioniert.',
    "Hi! 👋 I know every exercise in the wiki and the app's features. Ask me how to do an exercise, which exercises train a muscle, or how something in the app works."),
  suggestions: suggestions(),
});

export function answer(question) {
  const words = tokens(question);
  const ex = best(EXERCISES, words);
  const target = best(TARGETS, words);
  let faqHit = null, faqScore = 0;
  for (const f of FAQ) {
    const score = f.keys.filter(k => has(words, k)).reduce((a, k) => a + k.length, 0);
    if (score > faqScore) { faqScore = score; faqHit = f; }
  }
  const asksHow = hasAny(words, W.howWords) || hasAny(words, W.tipWords) || hasAny(words, W.muscleWords);
  // „Übungen für Beinbeuger“: Muskel gemeint, obwohl es auch eine Übung „Beinbeuger“ gibt
  if (target && ex && hasAny(words, W.exerciseWords) && ex.phrase.every(w => target.phrase.some(p => close(p, w)))) return targetAnswer(target.key);
  // „Bankdrücken eintragen“ → App-Funktion, „Wie mache ich Bankdrücken?“ → Übung
  if (ex && faqHit && !asksHow) return { bot: true, text: t(faqHit.a) };
  if (ex) return exerciseAnswer(ex.key, words);
  if (target) return targetAnswer(target.key);
  if (faqHit) return { bot: true, text: t(faqHit.a) };
  return {
    bot: true,
    text: tr('Dazu weiß ich leider nichts. Ich kenne die Übungen aus dem Wiki und die Funktionen der App – probier z. B.:',
      "Sorry, I don't know about that. I know the exercises from the wiki and the app's features – try for example:"),
    suggestions: suggestions(),
  };
}

/** Ausführung, Tipps und Muskeln einer Übung (je nach Frage nur ein Teil) */
function exerciseAnswer(base, words) {
  const e = WIKI[base];
  const onlyMuscles = hasAny(words, W.muscleWords) && !hasAny(words, W.howWords) && !hasAny(words, W.tipWords);
  const onlyTips = hasAny(words, W.tipWords) && !hasAny(words, W.howWords);
  const muscles = levelLabels(levelsOfBase(base)).map(([st, labels]) =>
    (st >= 0.8 ? '🔴 ' : st >= 0.45 ? '🟠 ' : '🟡 ') + levelTitle(st) + ': ' + labels.join(', ')).join('\n');
  let text = baseName(base);
  if (muscles) text += '\n\n' + muscles;
  if (!onlyMuscles && !onlyTips && e.how?.length) text += '\n\n' + tr('Ausführung:', 'How to:') + e.how.map((x, i) => `\n${i + 1}. ${t(x)}`).join('');
  if (!onlyMuscles && e.tips?.length) text += '\n\n' + tr('Darauf achten:', 'Watch out:') + e.tips.map(x => `\n• ${t(x)}`).join('');
  // Gerät genannt (z. B. „Kurzhantel“)? Dann den Hinweis zu dieser Variante dazu
  const variant = Object.entries(e.variants ?? {}).find(([k]) => has(words, tokens(k.split('/')[0])));
  if (variant) text += '\n\n' + tr(`Hinweis (${variant[0]}): `, `Note (${variant[0]}): `) + t(variant[1]);
  return { bot: true, text, groups: [[tr('Im Wiki öffnen', 'Open in wiki'), [base]]] };
}

/** Übungen, bei denen eine Region bzw. ein Muskel Hauptarbeit leistet (und unterstützend mitarbeitet) */
function targetAnswer(key) {
  const isRegion = key.includes('.');
  const label = isRegion ? regionLabel(key) : muscleLabel(key);
  const muscle = muscleOfRegion(key);
  const levelFor = base => {
    const l = levelsOfBase(base);
    return isRegion ? l[key] ?? 0 : Math.max(0, ...Object.entries(l).filter(([r]) => muscleOfRegion(r) === key).map(([, v]) => v));
  };
  const bases = Object.keys(WIKI).filter(b => CATALOG.some(c => c.base === b && !isCardio(c.group)));
  // Bei einer Region zuerst Übungen mit eigenen Regionswerten für diesen Muskel (wie Bot.kt)
  const explicit = b => !isRegion || Object.keys(WIKI[b].regions ?? {}).some(r => muscleOfRegion(r) === muscle);
  const mainAll = bases.filter(b => levelFor(b) >= 0.8);
  const mainExplicit = mainAll.filter(explicit);
  const main = mainExplicit.length ? mainExplicit : mainAll;
  const moderate = [...new Set([...bases.filter(b => { const v = levelFor(b); return v >= 0.45 && v <= 0.8; }), ...mainAll])].filter(b => !main.includes(b))
    // gibt es Übungen mit eigenen Regionswerten, bleiben die nur „ganzer Muskel“-Übungen ganz weg
    .filter(b => !mainExplicit.length || explicit(b));
  if (!main.length && !moderate.length) return { bot: true, text: tr(`Zu ${label} kenne ich keine Übungen.`, `I don't know any exercises for ${label}.`) };
  return {
    bot: true,
    text: tr(`Übungen für ${label} – antippen öffnet das Wiki:`, `Exercises for ${label} – tap to open the wiki:`),
    groups: [
      main.length ? [tr('🔴 Hauptarbeit', '🔴 Main work'), main.slice(0, 15)] : null,
      moderate.length ? [tr('🟠 Arbeitet mit', '🟠 Assists'), moderate.slice(0, 10)] : null,
    ].filter(Boolean),
  };
}

// ---------- Tab „Fragen“ ----------

/** Verlauf (bleibt erhalten, solange die Seite offen ist) */
const history = [];
let input = '';
let scrollDown = false;

function ask(q) {
  if (!q.trim()) return;
  history.push({ bot: false, text: q.trim() }, answer(q));
  input = '';
  scrollDown = true;
  rerender();
}

export function botTab() {
  if (!history.length) history.push(greeting());
  // Nach einer neuen Antwort ans Ende scrollen (nach dem Neuaufbau des Bildschirms)
  if (scrollDown) {
    scrollDown = false;
    setTimeout(() => { const m = document.querySelector('main.content'); if (m) m.scrollTop = m.scrollHeight; }, 0);
  }
  return h('div', { class: 'chat' }, history.map(m =>
    h('div', { class: ['bubble', m.bot ? 'bot' : 'me'] },
      h('div', { class: 'bubble-text' }, m.text),
      (m.groups ?? []).map(([title, bases]) => [
        h('small', { class: 'bubble-group' }, title),
        h('div', { class: 'chips wrap' }, bases.map(b => h('button', { class: 'chip', onclick: () => openWiki(b) }, baseName(b)))),
      ]))));
}

/** Leiste unten: Vorschläge der letzten Antwort + Eingabefeld */
export function botInput() {
  const last = history[history.length - 1];
  const send = () => ask(document.getElementById('bot-input')?.value ?? input);
  return h('div', { class: 'chat-bar' },
    last?.suggestions?.length ? h('div', { class: 'chips hscroll-chips chat-suggestions' },
      last.suggestions.map(s => h('button', { class: 'chip', onclick: () => ask(s) }, s))) : null,
    h('div', { class: 'chat-input' },
      h('input', {
        id: 'bot-input', type: 'text', value: input, placeholder: tr('Frag mich etwas …', 'Ask me something …'),
        enterkeyhint: 'send', autocomplete: 'off',
        oninput: e => { input = e.target.value; },
        onkeydown: e => { if (e.key === 'Enter') { e.preventDefault(); send(); } },
      }),
      h('button', { class: 'chat-send', onclick: send, 'aria-label': tr('Senden', 'Send') }, icon('send'))));
}

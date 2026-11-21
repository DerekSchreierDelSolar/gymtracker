// Fragen-Bot ohne KI – entspricht Bot.kt und BotTab.kt. Antwortet nur aus dem Übungs-Wiki (wiki-data.js)
// und einer festen Liste zu den App-Funktionen (bot-data.js, erzeugt aus app/src/main/assets/bot.json).

import { BOT } from './bot-data.js';
import { WIKI } from './wiki-data.js';
import { CATALOG } from './data.js';
import { tr, isEnglish, isCardio, exName, equipmentLabel } from './core.js';
import { h, icon, chip } from './ui.js';
import { rerender, go } from './nav.js';
import { baseName, openWiki } from './wiki.js';
import { screen } from './layout.js';
import { levelsOf, levelLabels, levelTitle, regionLabel, muscleLabel, muscleOfRegion, levelColor, tappableFigure } from './bodymap.js';
import { MUSCLES } from './body-data.js';
import { wizard, WIZARD_STAGES, equipmentChoices, pickExercises, createWizardWorkout } from './wizard.js';

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

/** Vorschlags-Pool; gezeigt wird jeweils eine zufällige Auswahl (wie Bot.kt) */
const pool = () => [
  tr('Wie mache ich Kreuzheben?', 'How do I deadlift?'),
  tr('Übungen für obere Brust', 'Exercises for upper chest'),
  tr('Was ist ein Drop-Satz?', 'What is a drop set?'),
  tr('Wie mache ich ein Backup?', 'How do I back up?'),
  tr('Welche Muskeln trainiert Rudern sitzend?', 'Which muscles does seated row work?'),
  tr('Übungen für hintere Schulter', 'Exercises for rear delts'),
  tr('Wie plane ich Trainingstage?', 'How do I plan training days?'),
  tr('Übungen für Waden', 'Exercises for calves'),
  tr('Wie funktioniert der Gewichts-Vorschlag?', 'How does the weight suggestion work?'),
  tr('Was bedeutet optional?', 'What does optional mean?'),
  tr('Übungen für den unteren Rücken', 'Exercises for the lower back'),
];

// „Erstelle mir ein Workout“ immer zuerst, dann eine zufällige Auswahl (wie Bot.kt)
const suggestions = () => [tr('Erstelle mir ein Workout', 'Create a workout for me'), ...pool().map(s => [Math.random(), s]).sort((a, b) => a[0] - b[0]).slice(0, 4).map(x => x[1])];

/** Begrüßung; recent = zuletzt trainierte Übung (Grundübung) → passender erster Vorschlag */
const greeting = recent => ({
  bot: true,
  text: tr('Hallo! 👋 Ich kenne alle Übungen aus dem Wiki und die Funktionen der App. Frag mich z. B. nach der Ausführung einer Übung, nach Übungen für einen Muskel oder wie etwas in der App funktioniert – oder sag „Erstelle mir ein Workout“.',
    "Hi! 👋 I know every exercise in the wiki and the app's features. Ask me how to do an exercise, which exercises train a muscle, or how something in the app works – or say \"Create a workout for me\"."),
  suggestions: (s => recent ? [s[0], tr(`Wie mache ich ${recent}?`, `How do I do ${recent}?`), ...s.slice(1, 4)] : s)(suggestions()),
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

// ---------- Bildschirm „Fragen“ (über ⋮ und das Wiki; früher ein Tab) ----------

/** Verlauf (bleibt erhalten, solange die Seite offen ist) */
const history = [];
let input = '';
let scrollDown = false;

function ask(q) {
  if (!q.trim()) return;
  history.push({ bot: false, text: q.trim() });
  const words = splitCompounds(tokens(q));
  if (wantsWorkout(words)) startWizard(presetMuscles(words)); else history.push(answer(q));
  input = '';
  scrollDown = true;
  rerender();
}

// ----- Workout-Assistent (wizard.js, wie Bot.kt) -----

/**
 * „Brustworkout“ → „brust workout“, „Ganzkörpertrainingsplan“ → „ganzkoerper trainingsplan“ – nur wenn der vordere Teil
 * ein Muskel-/Gruppenwort ist („Krafttraining“ bleibt ganz, sonst würde jede Frage dazu den Assistenten starten).
 */
const splitCompounds = words => words.flatMap(w => {
  const i = ['workout', 'training', 'routine'].map(n => w.indexOf(n)).filter(i => i >= 3).sort((a, b) => a - b)[0];
  return i == null || !muscleWordSet().has(w.slice(0, i)) ? [w] : [w.slice(0, i), w.slice(i)];
});

/** „Erstelle mir ein Workout“, „Trainingsplan machen“, „create a workout“ … */
function wantsWorkout(words) {
  const verb = words.some(w => ['erstell', 'generier', 'bau', 'mach', 'zusammenstell', 'plan', 'create', 'build', 'make', 'generate'].some(p => w.startsWith(p)));
  const noun = words.some(w => ['workout', 'training', 'trainingsplan', 'plan', 'routine'].some(p => w.startsWith(p)));
  return verb && noun && !words.some(w => w === 'wie' || w === 'how'); // „Wie erstelle ich ein Workout?“ = App-Frage
}

const stageTitle = s => (s === 1 ? tr('Hauptfokus', 'Main focus') : s === 0.6 ? tr('Mittel', 'Moderate') : tr('Leicht', 'Light'));

// Ungültige Einträge überspringen (wie Bot.load)
const GROUPS = (BOT.workoutGroups ?? [])
  .filter(g => Array.isArray(g?.words) && g.muscles && typeof g.muscles === 'object')
  .map(g => ({ words: g.words.filter(w => typeof w === 'string').map(tokens).filter(p => p.length), muscles: g.muscles }));

/** Einzelwörter, die eine Muskelgruppe/einen Muskel bezeichnen („brust“, „bein“ …) – für splitCompounds */
let muscleWords = null;
const muscleWordSet = () => (muscleWords ??= new Set([...GROUPS.flatMap(g => g.words), ...TARGETS.flatMap(([, ps]) => ps)].filter(p => p.length === 1).map(p => p[0])));

/** Wörter, nach denen ein Muskel ausdrücklich nicht gemeint ist („ohne Beine“) */
const NEGATIONS = new Set(['ohne', 'kein', 'keine', 'keinen', 'nicht', 'no', 'without', 'not', 'except']);

/** Wortvergleich für die Vorauswahl: 0 = gleich, 1 = Tippfehler (erst ab 7 Buchstaben: „beide“ ≠ „beine“), null = anders */
const presetDistance = (a, b) => (a === b ? 0 : Math.min(a.length, b.length) >= 7 && close(a, b) ? 1 : null);

/**
 * Muskeln aus der Frage vorauswählen (wie presetMuscles in Bot.kt): Gruppen („workoutGroups“) und Muskel-/Regionsbegriffe
 * („targets“, Region → ganzer Muskel, Hauptfokus); längere Wortfolgen gewinnen und verbrauchen ihre Wörter, dann exakte vor Tippfehler-Treffern
 * („ruecken“ ≠ „druecken“), dann längere Wörter, bei Gleichstand die Gruppe. Verneinte Treffer („ohne Beine“) wählen nichts aus.
 */
function presetMuscles(words) {
  const hits = [];
  const scan = (phrases, muscles, group) => {
    for (const p of phrases) for (let i = 0; p.length && i + p.length <= words.length; i++) {
      const d = p.map((x, k) => presetDistance(words[i + k], x));
      if (d.every(x => x != null)) hits.push({ start: i, phrase: p, typos: d.reduce((a, x) => a + x, 0), muscles, group });
    }
  };
  for (const g of GROUPS) scan(g.words, g.muscles, true);
  for (const [key, ps] of TARGETS) scan(ps, { [muscleOfRegion(key)]: 1 }, false);
  const chars = h => h.phrase.join('').length;
  const order = (a, b) => b.phrase.length - a.phrase.length || a.typos - b.typos || chars(b) - chars(a) || b.group - a.group;
  const used = new Set(), accepted = [], out = {};
  for (const h of hits.sort(order)) {
    const range = h.phrase.map((_, k) => h.start + k);
    if (range.some(i => used.has(i))) continue;
    range.forEach(i => used.add(i));
    if (h.start === 0 || !NEGATIONS.has(words[h.start - 1])) accepted.push(h);
  }
  // Reihenfolge wie in der Frage
  for (const h of accepted.sort((a, b) => a.start - b.start))
    for (const [m, s] of Object.entries(h.muscles)) if (MUSCLES[m]) out[m] = Math.max(out[m] ?? 0, s);
  return out;
}

function startWizard(preset = {}) {
  wizard.reset();
  Object.assign(wizard.muscles, preset);
  const names = Object.entries(preset).sort((a, b) => b[1] - a[1]).map(([m]) => muscleLabel(m)).join(', ');
  history.push({
    bot: true, widget: 'MUSCLES',
    text: !names
      ? tr('Gern! Welche Muskeln willst du trainieren? Tippe in der Figur oder unten: 1× = Hauptfokus (rot), 2× = mittel (orange), 3× = leicht (gelb), 4× = wieder aus.',
        'Sure! Which muscles do you want to train? Tap the figure or the buttons: 1× = main focus (red), 2× = moderate (orange), 3× = light (yellow), 4× = off.')
      : tr(`Gern! Ich habe schon markiert: ${names}. Passe die Auswahl bei Bedarf an – tippe in der Figur oder unten: 1× = Hauptfokus (rot), 2× = mittel (orange), 3× = leicht (gelb), 4× = wieder aus.`,
        `Sure! I've already selected: ${names}. Adjust if you like – tap the figure or the buttons: 1× = main focus (red), 2× = moderate (orange), 3× = light (yellow), 4× = off.`),
  });
}

/** Schritt „einfrieren“ (Knöpfe weg) und die Auswahl als eigene Antwort festhalten */
function closeStep(summary) {
  for (let i = history.length - 1; i >= 0; i--) if (history[i].widget) { history[i] = { ...history[i], widget: null }; break; }
  history.push({ bot: false, text: summary });
}

function musclesDone() {
  closeStep(WIZARD_STAGES.map(st => {
    const names = Object.entries(wizard.muscles).filter(([, s]) => s === st).map(([m]) => muscleLabel(m));
    return names.length ? stageTitle(st) + ': ' + names.join(', ') : null;
  }).filter(Boolean).join('\n'));
  history.push({ bot: true, widget: 'EQUIPMENT', text: tr('Mit welchen Geräten? Wähle eins oder mehrere – ohne Auswahl nehme ich alle.', "Which equipment? Pick one or more – with none selected I'll use everything.") });
  scrollDown = true;
  rerender();
}

function wizardCreate(store) {
  const eq = wizard.equipment;
  closeStep(eq.length ? tr('Geräte: ', 'Equipment: ') + eq.map(equipmentLabel).join(', ') : tr('Geräte: alle', 'Equipment: any'));
  const picked = pickExercises(store);
  if (!picked.length) {
    history.push({ bot: true, widget: 'EQUIPMENT', text: tr('Dazu habe ich mit diesen Geräten keine passenden Übungen gefunden. Wähle bitte andere oder mehr Geräte.', "I couldn't find matching exercises with that equipment. Please pick other or more equipment.") });
  } else {
    const w = createWizardWorkout(store, picked);
    history.push({
      bot: true, workoutId: w.id, suggestions: [tr('Erstelle mir ein Workout', 'Create a workout for me')],
      text: tr(`Fertig! Ich habe das Workout „${w.name}“ mit ${picked.length} Übungen angelegt (je 3 Ziel-Sätze):`, `Done! I created the workout "${w.name}" with ${picked.length} exercises (3 target sets each):`)
        + '\n' + picked.map((e, i) => `${i + 1}. ${exName(e)}`).join('\n') + '\n\n'
        + tr('Du kannst es jederzeit bearbeiten – Übungen tauschen, Ziel-Sätze ändern, Reihenfolge ziehen.', 'You can edit it anytime – swap exercises, change target sets, drag to reorder.'),
    });
  }
  scrollDown = true;
  rerender();
}

/** Schritt unter der Blase; nur der letzte ist bedienbar */
function wizardStep(m, active, store) {
  if (m.widget === 'MUSCLES') {
    const sel = wizard.muscles;
    return h('div', { class: ['card pad-m wizard', !active && 'inactive'] },
      tappableFigure(r => levelColor(sel[muscleOfRegion(r)]), id => { if (active) { wizard.tap(id); rerender(); } }),
      h('div', { class: 'chips wrap' }, Object.keys(MUSCLES).map(id =>
        chip(muscleLabel(id), sel[id] != null, () => { if (active) { wizard.tap(id); rerender(); } },
          sel[id] != null ? `<i class="dot" style="background:${levelColor(sel[id])}"></i>` : null))),
      active ? h('div', { class: 'wizard-actions' }, h('button', { class: 'btn', disabled: !Object.keys(sel).length, onclick: musclesDone }, tr('Weiter', 'Next'))) : null);
  }
  if (m.widget === 'EQUIPMENT') {
    const sel = wizard.equipment;
    const toggle = e => { const i = sel.indexOf(e); if (i >= 0) sel.splice(i, 1); else sel.push(e); rerender(); };
    return h('div', { class: ['card pad-m wizard', !active && 'inactive'] },
      h('div', { class: 'chips wrap' },
        chip(tr('Egal (alle)', 'Any (all)'), !sel.length, () => { if (active) { sel.length = 0; rerender(); } }),
        equipmentChoices().map(e => chip(equipmentLabel(e), sel.includes(e), () => { if (active) toggle(e); }))),
      active ? h('div', { class: 'wizard-actions' }, h('button', { class: 'btn', onclick: () => wizardCreate(store) }, tr('Workout erstellen', 'Create workout'))) : null);
  }
  return null;
}

let wizardStore = null; // von botScreen gesetzt

export function botTab(recent = null) {
  if (!history.length) history.push(greeting(recent));
  // Nach einer neuen Antwort ans Ende scrollen (nach dem Neuaufbau des Bildschirms)
  if (scrollDown) {
    scrollDown = false;
    setTimeout(() => { const m = document.querySelector('main.content'); if (m) m.scrollTop = m.scrollHeight; }, 0);
  }
  return h('div', { class: 'chat' }, history.map((m, i) => [
    h('div', { class: ['bubble', m.bot ? 'bot' : 'me'] },
      h('div', { class: 'bubble-text' }, m.text),
      (m.groups ?? []).map(([title, bases]) => [
        h('small', { class: 'bubble-group' }, title),
        h('div', { class: 'chips wrap' }, bases.map(b => h('button', { class: 'chip', onclick: () => openWiki(b) }, baseName(b)))),
      ])),
    m.widget ? wizardStep(m, i === history.length - 1, wizardStore) : null,
    m.workoutId != null ? h('div', null, h('button', { class: 'btn', onclick: () => go({ bot: false, workoutId: m.workoutId }) }, icon('list', 18), tr('Workout öffnen', 'Open workout'))) : null,
  ]));
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

/** Bildschirm „Fragen“ (wie BotScreen in BotTab.kt): erster Vorschlag passt zur zuletzt trainierten Übung. */
export function botScreen(store) {
  wizardStore = store;
  const last = store.sets.reduce((a, s) => (!a || s.time > a.time ? s : a), null);
  const base = last ? CATALOG.find(c => c.name === last.exercise)?.base : null;
  const recent = base && WIKI[base] ? baseName(base) : null;
  return screen({
    title: tr('Fragen', 'Ask'), back: true, key: 'bot',
    content: h('div', { class: 'pad' }, botTab(recent)),
    bottom: botInput(),
  });
}

// Meerschweinchen-Sprüche im Summary (Roadmap Schritt 9) – entspricht Quotes.kt.
// Passend zum Training ausgewählt; pro Training fest (Zufall mit der Trainings-ID als Startwert).

import { store } from './store.js';
import { tr, startOfWeek } from './core.js';
import { allSessions, streakFrom } from './stats.js';
import { h } from './ui.js';

/** Spruch-Pool je Stimmung als [de, en]; {n} = Wochen in Folge. */
const QUOTES = {
  BIG: [
    ['Quiek! Das war stark – ich hab vor Aufregung mein Heu fallen lassen.', 'Squeak! That was strong – I dropped my hay in excitement.'],
    ['Neuer Rekord? Ich erzähl\'s allen Meerschweinchen im Käfig.', 'A new record? I\'m telling every guinea pig in the cage.'],
    ['Wer bist du und was hast du mit meinem Trainingspartner gemacht? 💪', 'Who are you and what have you done with my training buddy? 💪'],
    ['So viel Kraft – davon träume ich heute Nacht.', 'So much power – I\'ll dream about it tonight.'],
    ['Ich würde dir applaudieren, aber meine Pfoten sind zu kurz.', 'I\'d applaud, but my paws are too short.'],
  ],
  NORMAL: [
    ['Training erledigt. Jetzt hast du dir eine Gurke verdient. 🥒', 'Workout done. You\'ve earned a cucumber. 🥒'],
    ['Gut gemacht! Ich hab in der Zeit … geschlafen. Aber ich bin stolz auf dich.', 'Well done! I spent the time … sleeping. But I\'m proud of you.'],
    ['Jeder Satz zählt – sagt auch meine Oma, und die ist sehr weise.', 'Every set counts – my grandma says so too, and she\'s very wise.'],
    ['Muskeln wachsen beim Ausruhen. Ich trainiere das gerade sehr intensiv.', 'Muscles grow while resting. I\'m training that very hard right now.'],
    ['Du warst da. Das ist schon mehr als die meisten Meerschweinchen schaffen.', 'You showed up. That\'s more than most guinea pigs manage.'],
    ['Stark wie ein Hamster. Nur besser.', 'Strong like a hamster. Only better.'],
  ],
  SHORT: [
    ['Kurz, aber gemacht – das zählt doppelt.', 'Short, but done – that counts double.'],
    ['Besser ein kleines Training als ein großes Sofa.', 'Better a small workout than a big sofa.'],
    ['Auch Meerschweinchen machen kleine Schritte. Ganz viele davon.', 'Guinea pigs take small steps too. Lots of them.'],
  ],
  COMEBACK: [
    ['Da bist du ja wieder! Ich hab dein Wasserschälchen frisch gemacht.', 'There you are! I freshened up your water bowl.'],
    ['Willkommen zurück – die Hanteln haben dich vermisst. Ich auch.', 'Welcome back – the dumbbells missed you. So did I.'],
  ],
  STREAK: [
    ['{n} Wochen am Stück – du bist zuverlässiger als meine Futterzeiten.', '{n} weeks in a row – you\'re more reliable than my feeding times.'],
    ['Wochenziel geschafft! Den Rest der Woche darfst du Heu kauen.', 'Weekly goal done! You may chew hay for the rest of the week.'],
  ],
  EARLY: [['So früh schon wach? Ich schlaf noch ein bisschen weiter … 😴', 'Up this early? I\'ll sleep a little longer … 😴']],
  LATE: [['Training um diese Uhrzeit? Respekt, Nachteule. 🦉', 'Working out at this hour? Respect, night owl. 🦉']],
  RARE: [
    ['🌟 Goldene Gurke! Dieses Meerschweinchen zeigt sich nur echten Champions.', '🌟 Golden cucumber! This guinea pig only shows itself to true champions.'],
    ['🎩 Das vornehme Meerschweinchen verneigt sich vor deiner Leistung.', '🎩 The distinguished guinea pig bows to your performance.'],
  ],
};

/** Kleiner Zufallsgenerator mit Startwert (mulberry32), damit der Spruch pro Training gleich bleibt. */
function seeded(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Spruch fürs Training (wie quoteFor): { mood, text } */
export function quoteFor(t, wins, stats) {
  const rnd = seeded((Math.abs(t.id) * 31 + t.start) % 4294967296);
  const all = allSessions(store);
  const earlier = all.filter(x => x.start < t.start).reduce((a, x) => (!a || x.start > a.start ? x : a), null);
  const hour = new Date(t.start).getHours();
  const minutes = t.id >= 0 ? (t.end - t.start) / 60000 : Infinity;
  const mood = rnd() < 0.05 ? 'RARE'
    : earlier && t.start - earlier.start >= 14 * 86_400_000 ? 'COMEBACK'
      : wins.some(a => a.big && a.kind !== 'WEEK_GOAL') ? 'BIG'
        : wins.some(a => a.kind === 'STREAK' || a.kind === 'WEEK_GOAL') ? 'STREAK'
          : hour < 7 ? 'EARLY' : hour >= 21 ? 'LATE'
            : stats.sets <= 6 || minutes < 20 ? 'SHORT' : 'NORMAL';
  const n = streakFrom(new Set(all.filter(x => x.start <= t.end).map(x => startOfWeek(x.start))), startOfWeek(t.start)).weeks;
  // „{n} Wochen am Stück“ nur, wenn es wirklich eine Serie gibt
  const hasRecord = wins.some(a => a.kind === 'RECORD');
  const pool = QUOTES[mood].filter(([de]) => (mood !== 'STREAK' || n >= 2 || !de.includes('{n}'))
    && (hasRecord || !de.startsWith('Neuer Rekord'))); // Rekord-Spruch nur bei echtem Rekord
  const [de, en] = pool[Math.floor(rnd() * pool.length)];
  return { mood, text: tr(de, en).replace('{n}', String(n)) };
}

/** Meerschweinchen mit Sprechblase (wie QuoteBubble). */
export function quoteBubble(q) {
  return h('div', { class: 'quote' },
    h('span', { class: 'quote-pig' }, h('img', { src: 'icons/icon-192.png', alt: '' }), q.mood === 'RARE' ? h('span', { class: 'quote-spark' }, '✨') : null),
    h('p', { class: 'quote-bubble' }, q.text));
}

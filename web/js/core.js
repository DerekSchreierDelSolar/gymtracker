// Grundlagen: Sprache, Formatierung, Datum, Muskelgruppen/Geräte, Übungs-Hilfsfunktionen.
// Entspricht I18n.kt, Format.kt, Model.kt und den Datumsfunktionen der Android-App.

import { CATALOG, TIMED_CATALOG, CARDIO_MET } from './data.js';

// ---------- Sprache ----------

/** Aktuelle Sprache ("de" | "en"); wird vom Store beim Laden gesetzt. */
export let appLanguage = 'de';
export function setAppLanguage(lang) { appLanguage = lang === 'en' ? 'en' : 'de'; }
export const isEnglish = () => appLanguage === 'en';
/** Text in der aktuellen Sprache – beide Varianten stehen direkt nebeneinander im Code. */
export const tr = (de, en) => (appLanguage === 'en' ? en : de);
const locale = () => (isEnglish() ? 'en-US' : 'de-DE');

// ---------- Muskelgruppen & Geräte (Reihenfolge = enum-Reihenfolge in Kotlin) ----------

export const MUSCLE_GROUPS = {
  BRUST: { de: 'Brust', en: 'Chest', color: '#E57373' },
  RUECKEN: { de: 'Rücken', en: 'Back', color: '#64B5F6' },
  BEINE: { de: 'Beine', en: 'Legs', color: '#81C784' },
  SCHULTERN: { de: 'Schultern', en: 'Shoulders', color: '#FFB74D' },
  ARME: { de: 'Arme', en: 'Arms', color: '#BA68C8' },
  BAUCH: { de: 'Bauch', en: 'Abs', color: '#4DB6AC' },
  GANZKOERPER: { de: 'Ganzkörper', en: 'Full body', color: '#7986CB' },
  CARDIO: { de: 'Cardio', en: 'Cardio', color: '#F06292' },
  SONSTIGE: { de: 'Sonstige', en: 'Other', color: '#90A4AE' },
};
export const GROUP_KEYS = Object.keys(MUSCLE_GROUPS);
export const groupLabel = g => tr(MUSCLE_GROUPS[g].de, MUSCLE_GROUPS[g].en);
export const groupColor = g => MUSCLE_GROUPS[g].color;
export const isCardio = g => g === 'CARDIO';

export const EQUIPMENT = {
  LANGHANTEL: { de: 'Langhantel', en: 'Barbell' },
  KURZHANTEL: { de: 'Kurzhantel', en: 'Dumbbell' },
  MASCHINE: { de: 'Maschine', en: 'Machine' },
  KABEL: { de: 'Kabelzug', en: 'Cable' },
  KETTLEBELL: { de: 'Kettlebell', en: 'Kettlebell' },
  BAND: { de: 'Widerstandsband', en: 'Resistance band' },
  KOERPER: { de: 'Körpergewicht', en: 'Bodyweight' },
  CARDIO_GERAET: { de: 'Cardiogerät', en: 'Cardio machine' },
};
export const EQUIPMENT_KEYS = Object.keys(EQUIPMENT);
export const equipmentLabel = e => tr(EQUIPMENT[e].de, EQUIPMENT[e].en);

// ---------- Katalog & eigene Übungen ----------

/** Katalog-Eintrag per Name (Groß-/Kleinschreibung egal) – wie CATALOG.firstOrNull { equals(ignoreCase) }. */
const catalogIndex = new Map(CATALOG.map(c => [c.name.toLowerCase(), c]));
export const catalogEntry = name => catalogIndex.get(name.toLowerCase());
export const catalogGroup = name => catalogEntry(name)?.group ?? null;

/** Zusatzangaben zu eigenen Übungen (vom Store befüllt): Gerät und Zeitmessung. */
export const CustomMeta = { equipment: {}, timed: new Set() };

export const isTimed = name => TIMED_CATALOG.has(name) || CustomMeta.timed.has(name);
export const equipmentOf = name => catalogEntry(name)?.equipment ?? CustomMeta.equipment[name] ?? null;
/** Anzeigename: im Englischen der Katalog-Name, sonst der gespeicherte (deutsche) Name. */
export const exName = name => (isEnglish() ? catalogEntry(name)?.en ?? name : name);
export const cardioMet = name => CARDIO_MET[name] ?? 7.0;

// ---------- Formatierung ----------

/** Gewicht: ganze Zahlen ohne Nachkomma, sonst eine Stelle mit Komma (DE) bzw. Punkt (EN). */
export function fmtW(w) {
  if (w % 1 === 0) return String(Math.trunc(w));
  return w.toLocaleString(locale(), { minimumFractionDigits: 1, maximumFractionDigits: 1, useGrouping: false });
}
export const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;
const pad2 = n => String(n).padStart(2, '0');

export function fmtDate(t) {
  const d = new Date(t);
  return isEnglish()
    ? `${pad2(d.getMonth() + 1)}/${pad2(d.getDate())}/${d.getFullYear()}`
    : `${pad2(d.getDate())}.${pad2(d.getMonth() + 1)}.${d.getFullYear()}`;
}
export function fmtTime(t) {
  const d = new Date(t);
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}
export function fmtDateTime(t) {
  const d = new Date(t);
  if (isEnglish()) {
    const h = d.getHours() % 12 || 12;
    return `${fmtDate(t)}, ${h}:${pad2(d.getMinutes())} ${d.getHours() < 12 ? 'AM' : 'PM'}`;
  }
  return `${fmtDate(t)}, ${fmtTime(t)} Uhr`;
}
/** Kurzes Datum für Achsen („18.11.“ bzw. „11/18“). */
export function fmtShortDate(t) {
  const d = new Date(t);
  return isEnglish() ? `${pad2(d.getMonth() + 1)}/${pad2(d.getDate())}` : `${pad2(d.getDate())}.${pad2(d.getMonth() + 1)}.`;
}
/** Dauer als „m:ss“ bzw. „h:mm:ss“. */
export function fmtDuration(ms) {
  const sec = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  return h > 0 ? `${h}:${pad2(m)}:${pad2(s)}` : `${m}:${pad2(s)}`;
}
/** Sekunden als „45 s“ bzw. „1:30 min“. */
export const fmtSeconds = sec => (sec < 60 ? `${sec} s` : `${Math.floor(sec / 60)}:${pad2(sec % 60)} min`);

/** Text für einen Satz, eine Cardio-Einheit oder eine Zeitübung. */
export function fmtSet(s, cardio) {
  if (cardio) return `${s.reps} min`;
  if (isTimed(s.exercise)) return (s.weight > 0 ? `${fmtW(s.weight)} kg · ` : '') + fmtSeconds(s.reps);
  return `${fmtW(s.weight)} kg × ${s.reps}`;
}

/** Geschätztes 1RM nach Epley. */
export const oneRepMax = (weight, reps) => (reps <= 1 ? weight : weight * (1 + reps / 30));

/** Abschluss 0..1 aus erledigten und geforderten Ziel-Sätzen (ohne Ziel: irgendwas gemacht = 100 %). */
export const goalCompletion = (done, total, setCount) => (total === 0 ? (setCount > 0 ? 1 : 0) : done / total);

/** Grün = komplett, Orange = mind. die Hälfte, Rot = weniger. */
export const completionColor = p => (p >= 0.999 ? '#43A047' : p >= 0.5 ? '#FFA000' : '#E53935');

/** Text-Zahl mit Komma oder Punkt → Zahl (oder null). */
export function parseNum(text) {
  const t = String(text ?? '').trim().replace(',', '.');
  if (t === '') return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}
export function parseIntStrict(text) {
  const t = String(text ?? '').trim();
  return /^\d+$/.test(t) ? Number(t) : null;
}

// ---------- Datum ----------

/** Tag als Zahl JJJJMMTT (z. B. 20261118). */
export function dayKey(t) {
  const d = new Date(t);
  return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
}
/** Monat als fortlaufende Zahl (Jahr*12 + Monat 0–11). */
export function monthIndex(t) {
  const d = new Date(t);
  return d.getFullYear() * 12 + d.getMonth();
}
/** Montag 00:00 der Woche von t. */
export function startOfWeek(t = Date.now()) {
  const d = new Date(t);
  const sinceMonday = (d.getDay() + 6) % 7; // getDay: Sonntag = 0
  d.setDate(d.getDate() - sinceMonday);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}
export function addDays(t, n) {
  const d = new Date(t);
  d.setDate(d.getDate() + n);
  return d.getTime();
}
export function startOfToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}
export const monthName = (year, m) =>
  new Date(year, m, 1).toLocaleDateString(locale(), { month: 'long', year: 'numeric' });
export const weekdayDate = key => {
  const d = new Date(Math.floor(key / 10000), Math.floor(key / 100) % 100 - 1, key % 100);
  return d.toLocaleDateString(locale(), { weekday: 'long' }) + ', ' + fmtDate(d.getTime());
};

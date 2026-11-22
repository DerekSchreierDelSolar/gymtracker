// UI-Bausteine ohne Framework.
// h(tag, props, ...kinder) baut DOM-Elemente – ähnlich wie React.createElement, nur direkt echte Elemente.
// Die App zeichnet bei jeder Änderung den aktuellen Bildschirm neu (siehe app.js → render()).

import { fmtW, fmtDate, completionColor, groupLabel, groupColor, tr, plural } from './core.js';

export function h(tag, props, ...children) {
  const el = tag === 'svg' || props?.svg ? document.createElementNS('http://www.w3.org/2000/svg', tag) : document.createElement(tag);
  for (const [k, v] of Object.entries(props ?? {})) {
    if (v == null || v === false || k === 'svg') continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'html') el.innerHTML = v;
    else if (k === 'value') el.value = v;
    else if (k === 'checked') el.checked = v;
    else if (k === 'class') el.setAttribute('class', Array.isArray(v) ? v.filter(Boolean).join(' ') : v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  append(el, children);
  return el;
}
function append(el, children) {
  for (const c of children) {
    if (c == null || c === false || c === true) continue;
    if (Array.isArray(c)) append(el, c);
    else el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
}

// ---------- Linien-Icons (24×24, Farbe = Textfarbe) ----------

const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M10 21v-6h4v6"/>',
  dumbbell: '<path d="M6.5 7v10M17.5 7v10M3.5 9.5v5M20.5 9.5v5M6.5 12h11"/>',
  list: '<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4.5 6h.01M4.5 12h.01M4.5 18h.01" stroke-width="3"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  body: '<circle cx="12" cy="7" r="4"/><path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8"/>',
  more: '<path d="M12 5h.01M12 12h.01M12 19h.01" stroke-width="3.2"/>',
  drag: '<path d="M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01" stroke-width="3.4"/>',
  swap: '<path d="M4 8h14M14 4l4 4-4 4M20 16H6M10 12l-4 4 4 4"/>',
  settings: '<path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="17" cy="18" r="2"/>',
  add: '<path d="M12 5v14M5 12h14"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  chevron: '<path d="M9 5l7 7-7 7"/>',
  chevronLeft: '<path d="M15 5l-7 7 7 7"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
  copy: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"/>',
  delete: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  play: '<path d="M8 5v14l11-7z" fill="currentColor"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
  filter: '<path d="M4 5h16l-6 8v6l-4-2v-4z"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  check: '<path d="M5 12l5 5 9-10"/>',
  download: '<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>',
  upload: '<path d="M12 15V4M7 9l5-5 5 5M5 20h14"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5"/><path d="M12 7.8h.01" stroke-width="2.8"/>',
  book: '<path d="M12 6.5v13"/><path d="M12 6.5 8 5H3v13h5l4 1.5 4-1.5h5V5h-5z"/>',
  share: '<path d="M12 15V4M7 9l5-5 5 5"/><path d="M5 13v6a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-6"/>',
  chat: '<path d="M5 5h14l2 2v8l-2 2h-9l-4 3.5.5-3.5H5l-2-2V7z"/><path d="M8 11h.01M12 11h.01M16 11h.01" stroke-width="2.6"/>',
  send: '<path d="M3 11 21 3l-7 18-3-8z"/><path d="M11 13 21 3"/>',
};
export const icon = (name, size = 24) => h('span', {
  class: 'icon',
  html: `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[name]}</svg>`,
});
/** Runder Icon-Button (Kopfzeile). */
export const iconBtn = (name, onClick, extra = {}) =>
  h('button', { class: ['icon-btn', extra.active && 'active', extra.cls], onclick: onClick, 'aria-label': extra.label ?? name, id: extra.id },
    icon(name), extra.badge ? h('span', { class: 'badge' }, extra.badge) : null);

// ---------- Kleine Komponenten ----------

/** Fortschrittsring (ab 12 Uhr im Uhrzeigersinn), optional mit Text in der Mitte. */
export function ring(progress, size, { text = null, color = completionColor(progress), stroke = 0.11 } = {}) {
  const p = Math.min(1, Math.max(0, progress));
  const sw = size * stroke, r = (size - sw) / 2, c = 2 * Math.PI * r;
  const svg = `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">` +
    `<circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="${color}" stroke-opacity="0.2" stroke-width="${sw}"/>` +
    (p > 0 ? `<circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" ` +
      `stroke-dasharray="${c * p} ${c}" transform="rotate(-90 ${size / 2} ${size / 2})"/>` : '') + '</svg>';
  return h('div', { class: 'ring', style: `width:${size}px;height:${size}px` }, h('span', { html: svg }), text != null ? h('b', null, text) : null);
}
export const percentRing = (progress, size) => ring(progress, size, { text: `${Math.round(progress * 100)}%` });

export const groupHeader = g => h('div', { class: 'group-header' }, h('span', { class: 'dot', style: `background:${groupColor(g)}` }), groupLabel(g));

export const statBox = (label, value) => h('div', { class: 'card stat-box' }, h('b', null, value), h('small', null, label));

export const chip = (label, selected, onClick, lead = null) =>
  h('button', { class: ['chip', selected && 'selected'], onclick: onClick }, lead ? h('span', { html: lead }) : null, label);

export const muted = (text, cls = '') => h('p', { class: `muted ${cls}` }, text);

// ---------- Diagramme ----------

export function chartCard(title, subtitle, ...content) {
  return h('div', { class: 'card chart' }, h('h3', null, title), subtitle ? h('small', { class: 'muted' }, subtitle) : null,
    h('div', { class: 'chart-body' }, content));
}

/** Waagerechte Balken: [[label, wert], …]. */
export function horizontalBars(items, format) {
  const mx = Math.max(...items.map(i => i[1]));
  if (!(mx > 0)) return null;
  return h('div', { class: 'hbars' }, items.map(([label, v]) =>
    h('div', { class: 'hbar' }, h('span', { class: 'label' }, label),
      h('span', { class: 'track' }, h('span', { class: 'fill', style: `width:${Math.max(2, (v / mx) * 100)}%` })),
      h('span', { class: 'value' }, format(v)))));
}

/** Senkrechte Säulen; die letzte (aktuelle) ist hervorgehoben. */
export function historyBars(values, labels, format) {
  const mx = Math.max(...values);
  if (!(mx > 0)) return null;
  return h('div', { class: 'vbars' },
    h('div', { class: 'vbars-value' }, format(values[values.length - 1])),
    h('div', { class: 'vbars-area' }, values.map((v, i) =>
      h('div', { class: 'vbar-col' }, h('div', { class: ['vbar', i === values.length - 1 && 'current'], style: `height:${Math.max(2, (v / mx) * 100)}%` })))),
    h('div', { class: 'vbars-labels' }, labels.map(l => h('span', null, l))));
}

/** Liniendiagramm (SVG); beschriftet ist nur der letzte Wert. */
/** onTapPoint(i): Antippen wählt den nächsten Punkt (wie LineChart in Components.kt); dann reicht ein einzelner Punkt */
export function lineChart(values, unit, onTapPoint = null) {
  if (values.length < (onTapPoint ? 1 : 2)) return null;
  const W = 320, H = 124, pad = 7;
  const mn = Math.min(...values), mx = Math.max(...values);
  const span = mx - mn > 0 ? mx - mn : 1;
  const lo = mn - span * 0.1, hi = mx + span * 0.1, uh = H - 2 * pad;
  const step = values.length > 1 ? (W - 2 * pad) / (values.length - 1) : 0;
  const pts = values.map((v, i) => [values.length > 1 ? pad + i * step : W / 2, pad + uh - ((v - lo) / (hi - lo)) * uh]);
  const grid = [0, 1, 2].map(k => `<line x1="0" x2="${W}" y1="${pad + (uh * k) / 2}" y2="${pad + (uh * k) / 2}" class="grid"/>`).join('');
  const path = `<polyline points="${pts.map(p => p.join(',')).join(' ')}" class="line"/>`;
  const dots = pts.map((p, i) => {
    const r = i === pts.length - 1 ? 5 : 3.5;
    return `<circle cx="${p[0]}" cy="${p[1]}" r="${r + 2}" class="dot-bg"/><circle cx="${p[0]}" cy="${p[1]}" r="${r}" class="dot"/>`;
  }).join('');
  // Achse links: Werte auf Höhe der drei Hilfslinien (wie LineChart in Components.kt)
  const axis = [hi, (hi + lo) / 2, lo].map(v => h('small', null, fmtW(Math.round(v * 10) / 10)));
  return h('div', { class: 'linechart' },
    h('div', { class: 'vbars-value' }, `${fmtW(values[values.length - 1])} ${unit}`),
    h('div', { class: 'linechart-body' },
      h('div', { class: 'linechart-axis' }, axis),
      h('div', {
        class: ['grow', onTapPoint && 'tappable'], html: `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${grid}${path}${dots}</svg>`,
        onclick: onTapPoint ? e => {
          // Klickposition → SVG-Koordinate → nächster Punkt in x-Richtung
          const r = e.currentTarget.getBoundingClientRect();
          const x = ((e.clientX - r.left) / r.width) * W;
          onTapPoint(values.length > 1 ? Math.min(values.length - 1, Math.max(0, Math.round((x - pad) / step))) : 0);
        } : null,
      })));
}

export const progressChart = (values, title, unit) =>
  chartCard(title, `${fmtW(Math.min(...values))} – ${fmtW(Math.max(...values))} ${unit}`, lineChart(values, unit));

// ---------- Dialog ----------

/**
 * Dialog wie AlertDialog: Titel, Inhalt, Buttons rechts unten.
 * actions: [{ label, onClick, primary?, danger?, disabled? }]
 */
export function dialog(title, body, actions, onDismiss) {
  return h('div', { class: 'dialog-backdrop', onclick: e => { if (e.target === e.currentTarget) onDismiss(); } },
    h('div', { class: 'dialog', role: 'dialog' },
      title ? h('h2', null, title) : null,
      h('div', { class: 'dialog-body' }, body),
      h('div', { class: 'dialog-actions' }, actions.map(a =>
        h('button', { class: ['btn text', a.danger && 'danger'], onclick: a.onClick, disabled: a.disabled }, a.label)))));
}

/** Eingabefeld mit schwebender Beschriftung (wie OutlinedTextField). */
export function textField({ id, label, value, oninput, type = 'text', inputmode, error, cls = '', onenter }) {
  return h('label', { class: ['field', error && 'error', cls] },
    h('input', {
      id, type, value, inputmode, placeholder: ' ', autocomplete: 'off',
      oninput: e => oninput(e.target.value),
      onkeydown: onenter ? e => { if (e.key === 'Enter') onenter(); } : null,
    }),
    h('span', null, label),
    error ? h('small', null, error) : null);
}

// ---------- Datum ----------

/** „2026-11-21“ (Ortszeit) für <input type="date"> */
export const isoDay = t => { const d = new Date(t); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
/** „2026-11-21“ → 12:00 Ortszeit (stimmt in jeder Zeitzone, wie DayPickerDialog) */
export const timeOfIsoDay = iso => new Date(iso + 'T12:00').getTime();

/**
 * Datumsfeld, das immer im App-Format anzeigt (TT.MM.JJJJ bzw. TT/MM/JJJJ) – das native Feld zeigt sonst das
 * Format des Browsers (z. B. MM/DD/YYYY). Darüber liegt unsichtbar das echte <input type="date">: Antippen öffnet
 * den Kalender des Systems. value/max = „JJJJ-MM-TT“; onchange(iso). Zukunft sperren mit max = isoDay(Date.now()).
 */
export function dateField({ id, value, max, onchange }) {
  return h('span', { class: 'date-field' },
    h('span', { class: 'date-text' }, fmtDate(timeOfIsoDay(value))),
    h('input', {
      id, type: 'date', value, max, 'aria-label': tr('Datum', 'Date'),
      onclick: e => { try { e.target.showPicker?.(); } catch { /* ältere Browser: Standardverhalten */ } },
      oninput: e => {
        const v = e.target.value;
        if (!v || (max && v > max)) return; // leer gelöscht oder Zukunft → ignorieren
        e.target.previousSibling.textContent = fmtDate(timeOfIsoDay(v)); // ohne Neuaufbau aktualisieren
        onchange(v);
      },
    }));
}

// ---------- Takt (Stoppuhr, Trainingsuhr, Pausen-Timer) ----------

/**
 * Funktionen, die app.js ca. 4× pro Sekunde aufruft, um nur einzelne Anzeigen zu aktualisieren
 * (statt den ganzen Bildschirm neu zu zeichnen). Wird vor jedem Neuzeichnen geleert.
 */
export const tickers = [];
export const onTick = fn => tickers.push(fn);

// ---------- Toast ----------

let toastTimer = null;
export function toast(msg) {
  let el = document.getElementById('toast');
  if (!el) { el = h('div', { id: 'toast' }); document.body.append(el); }
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3000);
}

/**
 * „Gelöscht – Rückgängig“ (wie UndoBar in Undo.kt): sofort löschen, 8 s lang eine Leiste mit „Rückgängig“.
 * Liegt außerhalb von #app, übersteht also das Neuzeichnen des Bildschirms.
 */
let undoTimer = null;
export function undoToast(msg, undo) {
  document.getElementById('undo-bar')?.remove();
  clearTimeout(undoTimer);
  const close = () => { clearTimeout(undoTimer); el.remove(); };
  const el = h('div', { id: 'undo-bar', role: 'status' },
    h('span', { class: 'grow' }, msg),
    h('button', { class: 'btn text', onclick: () => { close(); undo(); } }, tr('Rückgängig', 'Undo')));
  document.body.append(el);
  undoTimer = setTimeout(close, 8000);
}

/** Kategorie als große Zeile (wie CategoryRow in Components.kt): Symbol, Name, Anzahl + davon ausgewählt, Pfeil */
export function categoryRow(groupKey, label, iconHtml, color, total, added, onClick) {
  return h('button', { class: 'card category-row', style: `--tint:${color}`, onclick: onClick },
    h('span', { html: iconHtml }),
    h('div', { class: 'grow' }, h('b', null, label),
      h('small', { class: added ? 'accent' : 'muted' }, plural(total, tr('Übung', 'exercise'), tr('Übungen', 'exercises')) +
        (added ? tr(` · ${added} ausgewählt`, ` · ${added} selected`) : ''))),
    h('span', { class: 'muted' }, icon('chevron')));
}

// ---------- Bedienhinweise nur bis zur ersten Nutzung (wie Hints.kt) ----------
// Gemerkt im localStorage `gym_hints` (gerätegebunden, nicht im Export). Schlüssel wie in Hints.kt:
// list, drag, rest, weekGoal, weight, set
const HINTS_KEY = 'gym_hints';
let hintsDone = null;
function hintsSet() {
  if (!hintsDone) {
    try { hintsDone = new Set(JSON.parse(localStorage.getItem(HINTS_KEY) ?? '[]').filter(k => typeof k === 'string')); }
    catch { hintsDone = new Set(); }
  }
  return hintsDone;
}
/** true = Hinweis noch zeigen */
export const showHint = key => !hintsSet().has(key);
/** Geste wurde benutzt → Hinweis beim nächsten Neuaufbau ausblenden */
export function hintUsed(key) {
  const s = hintsSet();
  if (s.has(key)) return;
  s.add(key);
  try { localStorage.setItem(HINTS_KEY, JSON.stringify([...s])); } catch { /* privat-Modus: nur für diese Sitzung */ }
}

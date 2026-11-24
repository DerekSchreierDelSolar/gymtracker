// Einstellungen als eigener Bildschirm (früher ein Dialog) – entspricht SettingsScreen.kt.
// Übersicht ohne langes Scrollen: Darstellung je in einer Zeile, Größeres als Zeile mit Zusammenfassung → Unterseite
// (nav.settingsPage: 'profile' | 'cycle'). Erinnerung, Health Connect und automatisches Backup gibt es nur in Android.

import { store } from '../store.js';
import { tr, plural, fmtDate, appLanguage, LANGUAGES, weightUnit, validYear, validHeight, profileComplete } from '../core.js';
import { h, icon, chip, dialog, muted, undoToast } from '../ui.js';
import { nav, go, openDialog, closeDialog } from '../nav.js';
import { screen } from '../layout.js';
import { cycleSettings, cycleWeek } from '../cycle.js';
import { exportData, importData } from './home.js';

const PAGES = {
  profile: () => tr('Profil für Kalorien', 'Profile for calories'),
  cycle: () => tr('Trainingsblock mit Deload', 'Training block with deload'),
};

export function settingsScreen() {
  const page = PAGES[nav.settingsPage] ? nav.settingsPage : null;
  return screen({
    title: page ? PAGES[page]() : tr('Einstellungen', 'Settings'),
    back: true, key: 'settings-' + (page ?? 'main'),
    content: h('div', { class: 'stack pad settings' },
      page === 'profile' ? profileSettings() : page === 'cycle' ? cycleSettings() : overview()),
  });
}

function openPage(p) {
  if (p === 'profile') {
    // Eingabetext des Profils frisch aus dem Store
    profileDraft.year = store.profile.birthYear ? String(store.profile.birthYear) : '';
    profileDraft.height = store.profile.height ? String(store.profile.height) : '';
  }
  go({ settingsPage: p }); // mit History-Eintrag → Zurück-Geste führt zur Übersicht
}

function overview() {
  const off = tr('Aus', 'Off');
  const p = store.profile, c = store.cycle;
  const profileSummary = [
    p.sex ? (p.sex === 'm' ? tr('Männlich', 'Male') : tr('Weiblich', 'Female')) : null,
    p.birthYear ? String(p.birthYear) : null,
    p.height ? `${p.height} cm` : null,
  ].filter(Boolean).join(' · ') || tr('Nicht ausgefüllt – für genauere kcal', 'Not set – for more accurate calories');
  const total = c ? c.weeks + 1 : 0;
  const cycleSummary = !c ? off : (w => (w ? tr(`An · Woche ${w} von ${total}`, `On · week ${w} of ${total}`) : tr('An', 'On')))(cycleWeek(c));

  return [
    group(tr('Darstellung', 'Appearance'),
      choiceRow(tr('Design', 'Theme'), [['system', tr('System', 'System')], ['light', tr('Hell', 'Light')], ['dark', tr('Dunkel', 'Dark')]],
        store.themeMode, m => store.setTheme(m)),
      languageRow(),
      // nur Anzeige/Eingabe – gespeichert wird immer kg
      choiceRow(tr('Einheit', 'Unit'), [['kg', 'kg'], ['lb', 'lbs']], weightUnit, u => store.setUnit(u))),
    group(tr('Training', 'Training'),
      navRow('👤', PAGES.profile(), profileSummary, () => openPage('profile')),
      navRow('📆', PAGES.cycle(), cycleSummary, () => openPage('cycle'))),
    group(tr('Daten', 'Data'),
      navRow('📤', tr('Daten exportieren', 'Export data'),
        store.lastExport === 0 ? tr('Noch nie exportiert', 'Never exported') : tr('Letzter Export: ', 'Last export: ') + fmtDate(store.lastExport),
        exportData, false),
      navRow('📥', tr('Daten importieren', 'Import data'), tr('Ersetzt die aktuellen Daten', 'Replaces the current data'), importData, false)),
    // Fortschritt zurücksetzen: Nachfrage, danach „Rückgängig“
    group(null, navRow('🗑', tr('Fortschritt zurücksetzen', 'Reset progress'), tr('Übungen und Workouts bleiben erhalten', 'Exercises and workouts are kept'),
      resetDialog, false, 'danger')),
  ];
}

/** Karte mit kleiner Überschrift darüber (wie SettingsGroup). */
const group = (title, ...rows) => h('section', { class: 'settings-group' },
  title ? h('h3', { class: 'settings-title' }, title) : null,
  h('div', { class: 'card settings-card' }, rows));

/** Beschriftung links, gleich breite Auswahl-Knöpfe rechts (wie ChoiceRow). */
const choiceRow = (label, options, selected, onSelect) => h('div', { class: 'choice-row' },
  h('span', null, label),
  h('div', { class: 'segments' }, options.map(([k, l]) => chip(l, selected === k, () => onSelect(k)))));

/** Sprache: Auswahlfeld (6 Sprachen passen nicht als Chips in eine Zeile; wie LanguageRow in SettingsScreen.kt) */
const languageRow = () => h('div', { class: 'choice-row' },
  h('span', null, tr('Sprache', 'Language')),
  h('select', { id: 'language-select', class: 'chip selected language-select', onchange: e => store.setLanguage(e.target.value) },
    LANGUAGES.map(([k, l]) => h('option', { value: k, selected: k === appLanguage }, l))));

/** Symbol, Titel, Zusammenfassung, › (wie NavRow). */
const navRow = (emoji, title, summary, onClick, chevron = true, cls = '') => h('button', { class: ['nav-row', cls], onclick: onClick },
  h('span', { class: 'nav-row-emoji' }, emoji),
  h('span', { class: 'grow' }, h('span', { class: 'nav-row-title' }, title), h('small', { class: 'muted' }, summary)),
  chevron ? h('span', { class: 'muted' }, icon('chevron')) : null);

// ---------- Unterseite Profil (wie ProfileSettings in HomeScreen.kt) ----------

// Eingabetext (auch halb getippt) – gespeichert wird nur Gültiges
const profileDraft = { year: '', height: '' };

function profileSettings() {
  const p = store.profile;
  const numInput = (id, key, max, valid, label, field) => h('label', { class: 'field' + (profileDraft[key] && !valid(Number(profileDraft[key])) ? ' error' : '') },
    h('input', {
      id, type: 'text', inputmode: 'numeric', value: profileDraft[key], autocomplete: 'off', placeholder: ' ',
      oninput: e => {
        profileDraft[key] = e.target.value.replace(/\D/g, '').slice(0, max);
        const n = Number(profileDraft[key]);
        store.updateProfile({ [field]: valid(n) ? n : null });
      },
    }),
    h('span', null, label));
  return [
    muted(tr('Mit Geschlecht, Geburtsjahr und Größe (plus Körpergewicht) wird der Verbrauch über deinen Grundumsatz genauer geschätzt.',
      'With sex, birth year and height (plus body weight) calories are estimated more precisely from your resting metabolic rate.'), 'small'),
    h('div', { class: 'chips' }, [['m', tr('Männlich', 'Male')], ['f', tr('Weiblich', 'Female')]].map(([k, l]) =>
      chip(l, p.sex === k, () => store.updateProfile({ sex: p.sex === k ? null : k })))), // erneut tippen = abwählen
    h('div', { class: 'row' },
      numInput('profile-year', 'year', 4, validYear, tr('Geburtsjahr', 'Birth year'), 'birthYear'),
      numInput('profile-height', 'height', 3, validHeight, tr('Größe (cm)', 'Height (cm)'), 'height')),
    profileComplete(p) && !store.weights.length ? muted(tr('Trag im Tab „Körper“ noch dein Gewicht ein.', 'Also log your weight in the "Body" tab.'), 'small accent') : null,
  ];
}

function resetDialog() {
  openDialog(() => dialog(tr('Fortschritt zurücksetzen?', 'Reset progress?'),
    h('p', null, ((sets, units, weights) => tr(
      `${sets}, ${units} und ${weights} werden gelöscht. Tipp: vorher „Daten exportieren“.`,
      `${sets}, ${units} and ${weights} will be deleted. Tip: "Export data" first.`))(
      plural(store.sets.length, tr('Satz', 'set'), tr('Sätze', 'sets')),
      plural(store.sessions.length, tr('Trainingseinheit', 'workout'), tr('Trainingseinheiten', 'workouts')),
      plural(store.weights.length, tr('Gewichtseintrag', 'weight entry'), tr('Gewichtseinträge', 'weight entries')))),
    [
      { label: tr('Abbrechen', 'Cancel'), onClick: closeDialog },
      {
        label: tr('Zurücksetzen', 'Reset'), danger: true, onClick: () => {
          const snap = store.resetProgress();
          closeDialog();
          undoToast(tr('Fortschritt zurückgesetzt', 'Progress reset'), () => store.restoreProgress(snap));
        },
      },
    ], closeDialog));
}

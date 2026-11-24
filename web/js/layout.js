// Bildschirm-Gerüst – entspricht Scaffold + TopAppBar in Compose.

import { h, icon } from './ui.js';
import { goBack } from './nav.js';
import { tr } from './core.js';

/**
 * title/subtitle: Kopfzeile · back: Zurück-Pfeil anzeigen · actions: Buttons rechts
 * top: fester Bereich unter der Kopfzeile (z. B. Filter) · content: scrollbarer Inhalt
 * fab: schwebender Button · bottom: feste Leiste unten · key: für Scroll-Position merken
 */
export function screen({ title, subtitle, titleIcon, back = false, onBack = goBack, actions = [], top, content, fab, bottom, key, large = false, backdrop = null }) {
  const main = h('main', { class: 'content', 'data-scroll': key ?? title }, content);
  // Schwebender Button: beim Scrollen nur noch das Symbol → verdeckt keinen Text (wie ExtendedFloatingActionButton expanded)
  if (fab instanceof Element) {
    const sync = () => fab.classList.toggle('compact', main.scrollTop > 40);
    main.addEventListener('scroll', sync, { passive: true });
    requestAnimationFrame(sync); // nach dem Wiederherstellen der Scroll-Position
  }
  return h('div', { class: 'screen' },
    // backdrop = Bild-URL: durchsichtiges Hintergrundbild hinter allem (Übungsdetail, Training)
    backdrop ? h('div', { class: 'screen-backdrop', style: `background-image:url("${backdrop}")` }) : null,
    h('header', { class: ['topbar', large && 'large'] },
      back ? h('button', { class: 'icon-btn', onclick: onBack, 'aria-label': 'Zurück' }, icon('back')) : null,
      titleIcon ? h('span', { class: 'title-icon', html: titleIcon }) : null,
      h('div', { class: 'title' }, h('h1', null, title), subtitle ? h('small', null, subtitle) : null),
      h('div', { class: 'actions' }, actions)),
    top ?? null,
    main,
    fab ?? null,
    bottom ?? null);
}

export function fab(label, iconName, onClick, id) {
  return h('button', { class: 'fab', onclick: onClick, id }, icon(iconName), h('span', null, label));
}

const NAV_TABS = () => [
  ['home', tr('Start', 'Home')],
  ['list', tr('Workouts', 'Workouts')],
  ['calendar', tr('Kalender', 'Calendar')],
  ['body', tr('Körper', 'Body')],
];

/**
 * Navigationsleiste unten (wie MainNavBar in HomeScreen.kt): 4 Tabs + Knopf „Fragen“ (kein Tab, eigener Bildschirm).
 * selected = markierter Tab (-1 = „Fragen“); onTab(i) = Tab wählen, onBot = „Fragen“ öffnen.
 */
export function bottomNav(selected, onTab, onBot) {
  return h('nav', { class: 'bottom-nav' },
    NAV_TABS().map(([ico, label], i) =>
      h('button', { class: ['nav-item', i === selected && 'selected'], onclick: () => onTab(i) },
        h('span', { class: 'nav-icon' }, icon(ico)), h('span', null, label))),
    h('button', { class: ['nav-item', selected === -1 && 'selected'], onclick: onBot },
      h('span', { class: 'nav-icon' }, icon('chat')), h('span', null, tr('Fragen', 'Ask'))));
}

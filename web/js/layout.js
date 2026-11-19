// Bildschirm-Gerüst – entspricht Scaffold + TopAppBar in Compose.

import { h, icon } from './ui.js';
import { goBack } from './nav.js';

/**
 * title/subtitle: Kopfzeile · back: Zurück-Pfeil anzeigen · actions: Buttons rechts
 * top: fester Bereich unter der Kopfzeile (z. B. Filter) · content: scrollbarer Inhalt
 * fab: schwebender Button · bottom: feste Leiste unten · key: für Scroll-Position merken
 */
export function screen({ title, subtitle, titleIcon, back = false, onBack = goBack, actions = [], top, content, fab, bottom, key, large = false, backdrop = null }) {
  return h('div', { class: 'screen' },
    // backdrop = Bild-URL: durchsichtiges Hintergrundbild hinter allem (Übungsdetail, Training)
    backdrop ? h('div', { class: 'screen-backdrop', style: `background-image:url("${backdrop}")` }) : null,
    h('header', { class: ['topbar', large && 'large'] },
      back ? h('button', { class: 'icon-btn', onclick: onBack, 'aria-label': 'Zurück' }, icon('back')) : null,
      titleIcon ? h('span', { class: 'title-icon', html: titleIcon }) : null,
      h('div', { class: 'title' }, h('h1', null, title), subtitle ? h('small', null, subtitle) : null),
      h('div', { class: 'actions' }, actions)),
    top ?? null,
    h('main', { class: 'content', 'data-scroll': key ?? title }, content),
    fab ?? null,
    bottom ?? null);
}

export function fab(label, iconName, onClick, id) {
  return h('button', { class: 'fab', onclick: onClick, id }, icon(iconName), h('span', null, label));
}

// Lieblingsübungen (★) – wie Favorites.kt: store.favorites (JSON `favorites`), in Listen zuerst
// (favoritesFirst in core.js), in gruppierten Listen eigener Abschnitt „★ Favoriten“ ganz oben.
import { tr, exName } from './core.js';
import { store } from './store.js';
import { h, icon } from './ui.js';

/** Kleiner ausgefüllter Stern (hinter Namen, auf Kacheln). */
export const favoriteMark = (size = 14) => h('span', { class: 'fav-mark', 'aria-label': tr('Favorit', 'Favorite') }, icon('starFilled', size));

/** Stern-Knopf zum Markieren (Kopfzeile). */
export function favoriteBtn(name) {
  const fav = store.isFavorite(name);
  return h('button', {
    class: ['icon-btn', fav && 'fav-on'], onclick: () => store.toggleFavorite(name),
    'aria-label': fav ? tr('Aus Favoriten entfernen', 'Remove from favorites') : tr('Als Favorit markieren', 'Mark as favorite'),
  }, icon(fav ? 'starFilled' : 'star'));
}

/** Überschrift des Favoriten-Abschnitts (wie groupHeader); mit onToggle aufklappbar (Anzahl + Pfeil, wie FavoritesHeader). */
export const favoritesHeader = ({ count = 0, expanded = true, onToggle = null } = {}) => h(onToggle ? 'button' : 'div',
  { class: 'group-header' + (onToggle ? ' fav-toggle' : ''), ...(onToggle ? { onclick: onToggle, 'aria-expanded': String(expanded) } : {}) },
  h('span', { class: 'fav-mark' }, icon('starFilled', 16)), tr('Favoriten', 'Favorites'),
  onToggle ? [h('span', { class: 'muted' }, ` (${count})`), h('span', { class: 'fav-chevron muted' + (expanded ? ' open' : '') }, icon('chevron', 20))] : null);

/** Übungsname, bei Favoriten mit Stern dahinter. */
export const nameWithStar = (name, label = exName(name)) =>
  store.isFavorite(name) ? h('span', { class: 'fav-name' }, label, ' ', favoriteMark()) : label;

/** Für favoritesFirst(list, isFav) */
export const isFav = name => store.isFavorite(name);

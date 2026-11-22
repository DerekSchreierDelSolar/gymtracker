// Service Worker: macht die App offline nutzbar.
// Strategie „Netz zuerst, sonst Cache“: Mit Internet kommt immer die neueste Version
// (Updates landen ohne Zutun auf dem Handy), ohne Internet die zuletzt geladene.
// Die Trainingsdaten selbst liegen im localStorage, nicht hier.

const CACHE = 'gymtracker-v45';
const FILES = [
  './', './index.html', './manifest.webmanifest', './css/app.css',
  './js/app.js', './js/core.js', './js/data.js', './js/store.js', './js/stats.js', './js/pictograms.js',
  './js/ui.js', './js/nav.js', './js/layout.js', './js/filter.js', './js/wiki.js', './js/wiki-data.js', './js/muscle-data.js', './js/supplement-data.js', './js/bodymap.js', './js/body-data.js', './js/image-data.js', './js/bot.js', './js/wizard.js', './js/review.js', './js/bot-data.js',
  './js/screens/home.js', './js/screens/exercise.js', './js/screens/workout.js', './js/screens/training.js',
  './icons/apple-touch-icon.png', './icons/icon-192.png', './icons/icon-512.png', './icons/favicon-32.png',
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET' || new URL(e.request.url).origin !== location.origin) return;
  e.respondWith(
    fetch(e.request)
      .then(res => {
        if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); }
        return res;
      })
      .catch(() => caches.match(e.request, { ignoreSearch: true }).then(r => r ?? caches.match('./index.html')))
  );
});

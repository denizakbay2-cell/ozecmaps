'use strict';

// ozecmaps offline shell: the page and its own files are kept, so the installed app opens even without a network.
// Everything is fetched network-first, so a new version shows up at once; the API, map tiles and other sites are
// never cached here.
const CACHE = 'ozecmaps-shell-v1';
const SHELL = [
    './',
    'manifest.json',
    'assets/app.js',
    'assets/styles.css',
    'assets/vendor/leaflet/leaflet.js',
    'assets/vendor/leaflet/leaflet.css',
    'assets/vendor/maplibre/maplibre-gl.js',
    'assets/vendor/maplibre/maplibre-gl.css',
    'assets/vendor/maplibre/leaflet-maplibre-gl.js',
    'assets/fonts/inter-latin.woff2',
    'assets/fonts/inter-latin-ext.woff2',
    'assets/img/logo-mark.jpg',
    'assets/img/logo-wordmark.png',
    'assets/icons/icon-192.png'
];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
    event.waitUntil(caches.keys()
        .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
        .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    const url = new URL(request.url);
    if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.includes('/api/')) {
        return;
    }
    event.respondWith(fetch(request)
        .then((response) => {
            if (response.ok && response.type === 'basic') {
                const copy = response.clone();
                caches.open(CACHE).then((cache) => cache.put(request, copy));
            }
            return response;
        })
        .catch(() => caches.match(request)
            .then((cached) => cached ?? (request.mode === 'navigate' ? caches.match('./') : undefined))
            .then((answer) => answer ?? Response.error())));
});

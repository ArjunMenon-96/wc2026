/* WC2026 service worker — network-first shell (no stale lock-in) + runtime caches */
const VER='v2';
const SHELL='wc2026-shell-'+VER;
const RUNTIME='wc2026-runtime-'+VER;
const SHELL_FILES=['./','./index.html','./manifest.json','./assets/emblem.svg','./assets/icon.svg'];

self.addEventListener('install',e=>{
  e.waitUntil(caches.open(SHELL).then(c=>c.addAll(SHELL_FILES)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',e=>{
  e.waitUntil(caches.keys().then(keys=>Promise.all(
    keys.filter(k=>k!==SHELL&&k!==RUNTIME).map(k=>caches.delete(k))
  )).then(()=>self.clients.claim()));
});
self.addEventListener('fetch',e=>{
  const url=e.request.url;
  /* ESPN data: network-first (fresh scores), fall back to cache offline */
  if(url.includes('site.api.espn.com')){
    e.respondWith(
      fetch(e.request).then(r=>{const cp=r.clone();caches.open(RUNTIME).then(c=>c.put(e.request,cp));return r;})
        .catch(()=>caches.match(e.request))
    );
    return;
  }
  /* flags: cache-first (immutable) */
  if(url.includes('flagcdn.com')){
    e.respondWith(caches.match(e.request).then(c=>c||fetch(e.request).then(r=>{
      const cp=r.clone();caches.open(RUNTIME).then(ch=>ch.put(e.request,cp));return r;})));
    return;
  }
  /* shell / navigation / HTML: network-first so a new build never stays stale */
  if(e.request.mode==='navigate'||url.endsWith('.html')||url.endsWith('/')){
    e.respondWith(
      fetch(e.request).then(r=>{const cp=r.clone();caches.open(SHELL).then(c=>c.put(e.request,cp));return r;})
        .catch(()=>caches.match(e.request).then(c=>c||caches.match('./index.html')))
    );
    return;
  }
  /* other static (icons/css/js): cache-first */
  e.respondWith(caches.match(e.request).then(c=>c||fetch(e.request)));
});

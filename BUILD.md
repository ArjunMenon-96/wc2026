# WC2026 Live — build & run

## Web (works now)
Static site. Serve the folder:
```
python3 -m http.server 8080
```
- App:  http://localhost:8080
- S22 device preview:  http://localhost:8080/device.html

Installable PWA (manifest + service worker): open in Chrome → "Add to Home screen".
Works offline after first load (shell + last data + flags cached).

## Android (Capacitor wrap — run when ready)
Requires Node + Android Studio/SDK. Not triggered automatically.
```
npm install
npm run cap:add      # one-time: copies web → www/, adds android project
npm run cap:sync     # after any web change
npm run cap:open     # open in Android Studio to run/build
# or headless debug apk:
npm run build:apk    # -> android/app/build/outputs/apk/debug/app-debug.apk
```
`appId` = app.wc2026.live · `appName` = WC2026 Live.

## Data
All live data is keyless ESPN public endpoints (scoreboard / standings / summary / news).
`updater.py` + `data/fixtures.json` are an offline fallback only.

## Legal
- No video streaming. Companion app: scores, fixtures, standings, commentary, news, "where to watch" (broadcaster names).
- FIFA emblem/wordmark are FIFA trademarks — `assets/emblem.svg` is your supplied asset; the placeholder mark is generic.
- Flags via flagcdn.com. Broadcaster names are factual references.

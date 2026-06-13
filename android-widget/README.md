# WC2026 home-screen widget (native Android)

Native App Widget that shows the live/next WC2026 match on the home screen.
Separate from the web app (widgets can't run HTML/JS) but shares the same
ESPN data source, palette and flags.

## Sizes
- **5×3 extra-large (default/target)** — featured match + 3 more of today's fixtures
- 4×2 large · 4×1 row · 2×2 small
- Responsive via `RemoteViews` SizeF map on Android 12+; falls back to the 5×3 layout below 12.

## Behaviour
- `WidgetUpdateWorker` fetches the ESPN scoreboard, picks the live match (else next), downloads the two flag PNGs, sets score + status.
- Refresh: system floor `updatePeriodMillis = 30 min`, plus a 15-min `WorkManager` periodic job, plus an instant refresh on app launch (call `ScoreWidget.refreshNow(context)`).

## Taps → deep link to the match
- Each tappable region fires `wc26://match/<espnEventId>`.
  - Small / row / large widgets: whole widget → the **featured** match.
  - 5×3: top area → featured match; each of the 3 fixture rows → **that** match.
- `@capacitor/app` catches it: `getLaunchUrl()` (cold start) + `appUrlOpen` listener (warm) → the web app calls `openMatch(id)`, opening that match's detail sheet.
- Manifest: add the `wc26` `VIEW` intent-filter (see `MANIFEST-SNIPPET.xml`) to `MainActivity`, and set `android:launchMode="singleTask"` on `MainActivity` so warm taps deliver via `onNewIntent`.
- Add the plugin: `@capacitor/app` (already in `package.json`).
- Shows the feed **minute** (e.g. `76'`). The ticking `~MM:SS` seconds are app-only (a widget can't tick without a battery-draining foreground service).

## Install into the Capacitor Android project
After `npm run cap:add` (generates `android/`):

1. Copy sources into the app module:
   ```
   android-widget/java/app/wc2026/live/*.kt   ->  android/app/src/main/java/app/wc2026/live/
   android-widget/res/layout/*.xml            ->  android/app/src/main/res/layout/
   android-widget/res/xml/*.xml               ->  android/app/src/main/res/xml/
   android-widget/res/drawable/*.xml          ->  android/app/src/main/res/drawable/
   android-widget/res/values/widget_colors.xml -> android/app/src/main/res/values/
   ```
2. Add the receiver from `MANIFEST-SNIPPET.xml` inside `<application>` of `android/app/src/main/AndroidManifest.xml`.
3. Add the WC26 emblem as `res/drawable/wc_emblem.xml` (vector). In Android Studio: right-click `res` → New → Vector Asset → import `assets/emblem.svg`. (Referenced by the layouts + widget preview.)
4. WorkManager dependency in `android/app/build.gradle`:
   ```gradle
   implementation "androidx.work:work-runtime-ktx:2.9.1"
   ```
5. Kotlin + coroutines must be enabled (Capacitor 6 supports Kotlin; if missing, apply `org.jetbrains.kotlin.android` and add `org.jetbrains.kotlinx:kotlinx-coroutines-android`).
6. Optional instant refresh: in `MainActivity.onResume()` call `ScoreWidget.refreshNow(this)`.

## Notes
- `ISO` map in `WidgetUpdateWorker.kt` lists all 48 teams — extend if ESPN spells a name differently.
- Network is on a background thread (`Dispatchers.IO`) — fine for `CoroutineWorker`.
- Legal: scores/flags only, same as the app. No video.

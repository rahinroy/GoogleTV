# CLAUDE.md — TV launcher engineering notes

Architecture and hard-won lessons for this project, so a fresh session (human or AI)
can continue without re-discovering everything. For user-facing setup/build/deploy, see
[`README.md`](README.md); this file is the deeper "why".

---

## What this project is

A custom **Google TV / Android TV launcher**, sideloaded (not published). Two surfaces:

1. **Home screen** — a Mac-desktop-style UI: full-screen photo-slideshow wallpaper +
   a floating "dock" belt of installed apps along the bottom that scrolls horizontally.
2. **Screensaver** — a `DreamService` crossfading through the same photos.

**Priorities, in order:** (1) performance / no scroll jank, (2) easy updates, (3) design.

Settled design decisions (don't re-litigate):
- **Native Compose for TV, not WebView** for interactive surfaces — WebView janks on weak
  TV hardware. The "update the UI without redeploying" goal is met by **remote JSON
  config**, not a webview. (A webview would be acceptable only for the non-interactive
  screensaver, if ever wanted.)
- **Kotlin + Jetpack Compose for TV** for the home screen; **native Views** for the
  screensaver slideshow (non-interactive, so Compose buys nothing there).
- **Google Photos API is not viable** for the screensaver — Google removed broad read
  scopes (2025-03-31). The path is a remote image manifest (a URL/bucket you control),
  optionally fed from a shared-album export. Bundled assets are the default.

---

## File map

```
settings.gradle.kts, build.gradle.kts, gradle.properties   root Gradle config
gradle/libs.versions.toml                                  version catalog (all deps)
gradle/wrapper/…, gradlew(.bat)                            Gradle wrapper
app/build.gradle.kts                                       module config
app/src/main/AndroidManifest.xml                           HOME intent-filter + DreamService
app/src/main/java/com/nihar/tvlauncher/
  MainActivity.kt          launcher⇄settings nav via state; launches apps; role request
  AppRepository.kt         queryRaw() (cheap) + loadApps()/loadAllForSettings()
  AppEntry.kt              AppEntry (preloaded ImageBitmap) + SettingsAppRow models
  LauncherPrefs.kt         SharedPreferences: hidden set, custom order, photo interval
  PhotoExif.kt             EXIF (GPS→geocode place, DateTimeOriginal→date) for photos
  LauncherScreen.kt        desktop UI: wallpaper slideshow + dock belt + clock + overlays
  SettingsScreen.kt        show/hide + reorder + interval + "set default launcher"
  Fonts.kt                 Elms Sans FontFamily (res/font/elms_sans.ttf) for overlays
  LauncherTheme.kt         dark theme
  screensaver/ScreensaverDreamService.kt   system screensaver entry (2-phase start)
  screensaver/SlideshowView.kt             crossfade slideshow (Views + Coil)
  screensaver/ScreensaverConfig.kt         MANIFEST_URL + asset/cache constants
  screensaver/ImageManifestRepository.kt   remote JSON manifest -> image models, w/ cache
app/src/main/res/font/elms_sans.ttf        Elms Sans variable TTF (OFL) — overlay font
app/src/main/assets/screensaver/           bundled photos (git-ignored; user-supplied)
```

---

## Key technical facts (so you don't re-derive them)

- **Launcher role:** `MainActivity` declares a `category.HOME + DEFAULT` intent-filter
  (plus a separate `LAUNCHER + LEANBACK_LAUNCHER` filter so it's always openable). That
  makes it eligible as the home app. See "Default launcher" below for the Google TV
  caveats.
- **Finding apps:** query two intents — `ACTION_MAIN + CATEGORY_LEANBACK_LAUNCHER` (TV
  apps, which have a 320×180 banner) and `+ CATEGORY_LAUNCHER` (sideloaded phone apps).
  Launch with `getLeanbackLaunchIntentForPackage()` ?: `getLaunchIntentForPackage()`.
- **Package visibility (Android 11+):** the manifest `<queries>` block is required or the
  queries return empty. We use `<queries>`, NOT `QUERY_ALL_PACKAGES`.
- **Screensaver = `DreamService`** (Android's built-in idle "daydream"), selected under
  Settings → System → Screensaver. Trigger immediately for testing:
  `adb shell am start -n com.android.systemui/.Somnambulator`.
- **Desktop/dock layout:** `LauncherScreen` is a `Box` with layers: (1) `WallpaperSlideshow`
  — a full-screen **Compose-native** Coil `Crossfade` (NOT an `AndroidView`); (2) a
  bottom-band `Brush.verticalGradient` scrim (`fillMaxHeight(0.45f)`, bottom-aligned, to
  cut overdraw); (3) `Dock` — an opaque rounded panel holding a horizontally-scrolled
  `Row` belt of tiles + a ⚙ settings tile. Banner apps → 16:9 tiles (Crop), icon apps →
  square (Fit). Clock overlay top-left, photo place/date overlay top-right.

### SCROLL PERFORMANCE — profiler-verified stack (do NOT regress these)
Tuned against `adb shell dumpsys gfxinfo <pkg>` (reset → scroll → read `Janky frames`,
percentiles, `gpu percentile`, `Slow UI thread`, `Slow bitmap uploads`, `Missed Vsync`).

1. **Lightweight tiles — the single biggest win.** `TileFrame` is a plain `Box` (manual
   focus state, `graphicsLayer` scale, conditional `border`), NOT a tv-material3 `Card`.
   The Card's Surface/interaction/indication machinery was the dominant per-frame cost
   (~88% janky with Card vs ~10% with the plain Box). NEVER put a `Card` in the belt.
2. **Preloaded `HARDWARE` bitmaps.** `AppRepository.toImageBitmap` rasterizes each icon
   ONCE (IO thread), downsampled ≤360px, then `copy(Bitmap.Config.HARDWARE)` → GPU-
   resident, never re-uploaded/evicted (`Slow bitmap uploads: 0`). Falls back to software
   + `prepareToDraw()`. NEVER go back to Coil `AsyncImage` for tiles.
3. **Opaque dock panel.** Occlusion-culls the wallpaper + scrim behind it — cut
   `gpu percentile` ~17ms→~9ms. Translucency there forces per-frame blending. Keep opaque.
4. **Wallpaper crossfade deferral.** `WallpaperSlideshow(busy = { dockScroll.isScrollInProgress })`
   pauses the heavy full-screen crossfade while the dock scrolls (it was causing periodic
   150–300ms spikes). `dockScroll` is hoisted to `LauncherScreen`, passed to `Dock`.
5. **All tiles composed up front** in a plain `Row` + `horizontalScroll` (NOT `LazyRow`).
   With lightweight tiles, Row (no mid-scroll composition) beats LazyRow (composition
   spikes as tiles enter). Small app count makes this fine.
6. **Soft-spring pivot** `BringIntoViewSpec`, `stiffness = 300f`, no bounce, centers the
   focused tile. Soft so rapid presses chain into one glide; stiffer "locks to each tile",
   too soft = laggy trail. 300f is the sweet spot.
7. **Warm-up sweep** (`warmUpBelt`, end→start) on load + each `ON_RESUME`, after
   `focusManager.clearFocus()`, so every tile draws its display list before the first real
   scroll. Visible quick auto-scroll on entry — intentional.

- **Measurement caveat:** `adb shell input keyevent` spawns a JVM per event on the TV,
  stealing main-thread CPU and inflating jank / `Slow UI thread`. Batch events
  (`input keyevent 22 22 22 …`) and trust `gpu percentile` (injection-immune). A real
  remote's HID input has none of this overhead — steady-state GPU (~9–13ms, under the
  16.7ms budget) is the honest signal.

- **Prefs / hidden / order / interval:** `LauncherPrefs` (SharedPreferences) holds the
  hidden set, custom app order (list of packages), and photo interval seconds.
  `loadApps(hidden, order)` applies order then filters hidden BEFORE rasterizing (hidden
  apps cost zero). `loadAllForSettings(hidden, order)` returns all apps in order.
- **Overlays / EXIF:** clock (top-left) + photo place/date (top-right, from
  `PhotoExif.readPhotoInfo`: EXIF GPS → `Geocoder` city/region/country, DateTimeOriginal
  → date). `WallpaperSlideshow` reports its current model via `onCurrentModel`;
  `LauncherScreen` computes `PhotoInfo` off-thread. EXIF is read only for bundled
  `file:///android_asset/...` photos (remote URLs skipped). Requires Play Services for
  the geocoder; degrades gracefully (date only, or nothing) if unavailable.
- **Remote manifest:** set `ScreensaverConfig.MANIFEST_URL` to an https URL returning JSON
  (`["url", ...]` or `{"images":[...]}`) to update photos without rebuilding. Blank URL
  (default) = bundled assets only. `ImageManifestRepository` precedence: remote →
  last-cached remote (offline) → bundled assets. Cached to `filesDir`. Requires the
  `INTERNET` permission (already in the manifest); cleartext is blocked on targetSdk 34.
- **TV Material3 is fully `@ExperimentalTvMaterial3Api`.** Handled with a module-wide
  opt-in compiler flag in `app/build.gradle.kts`
  (`-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api`).

### Version pins (in `gradle/libs.versions.toml`)
AGP 8.5.2 · Gradle 8.9 · Kotlin 2.0.20 · Compose BOM 2024.09.03 ·
androidx.tv:tv-material 1.0.0 · Coil 2.7.0 (`coil-compose` + `coil` core) ·
androidx.exifinterface 1.3.7 · coroutines 1.8.1 · compileSdk/targetSdk 34 · minSdk 26 ·
JDK 17. These are confirmed to compile together against this code.

---

## Build / deploy

`gradlew` is enough — no Android Studio UI needed, just a JDK 17 + the Android SDK on
disk (`local.properties` → `sdk.dir=...`). See [`README.md`](README.md) for the full
setup. Quick reference:

```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
adb connect <tv-ip>:<port>     # Android 11+ TVs: Wireless debugging (ports are random)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.nihar.tvlauncher/.MainActivity
```

- **Wireless debugging** ports change whenever the setting is toggled or the TV reboots.
  Re-discover a rebooted TV with `adb mdns services`, then `adb connect <ip>:<port>`. The
  connection can be flaky under heavy `pm`/`dumpsys` use — just `adb connect` again.
- **Stable debug signing (optional):** `app/build.gradle.kts` uses `signing/debug.keystore`
  if present (so `adb install -r` never hits a signature mismatch across machines),
  otherwise falls back to Gradle's auto-generated debug key. The keystore is git-ignored.

---

## Default launcher on Google TV (device-dependent, often not fully solvable)

Google TV pins its own launcher and hides the "default home app" chooser. Findings:

- **`android:priority` on a third-party HOME filter is stripped to 0.** Google's launcher
  wins at `priority=2`. Bumping our filter priority in the manifest does nothing.
- **The `ROLE_HOME` role can be granted** (`cmd package set-home-activity <pkg>/<activity>`
  or the in-app `RoleManager.createRequestRoleIntent(ROLE_HOME)` dialog, surfaced as
  "Set as default launcher" in Settings). Confirm with
  `cmd role get-role-holders android.app.role.HOME`.
- **BUT holding the role does not capture the physical Home button** on many boxes — the
  button uses priority-based resolution, not the role. The community workaround (FLauncher,
  Projectivy) is to **disable the stock launcher**:
  `adb shell pm disable-user --user 0 <stock-launcher-pkg>` (reversible with `pm enable`).
  ⚠️ On some devices disabling the stock launcher drops the Home button to a **disabled
  priority-1 "recovery" home** (a black dead-end), NOT to your launcher — test carefully
  and be ready to `pm enable` the stock launcher to recover. There is always a very-low-
  priority `FallbackHome` so the device can't be left with no home at all.
- **CRITICAL testing gotcha — two different HOME code paths:** `cmd package resolve-activity
  -c android.intent.category.HOME` and injected `input keyevent KEYCODE_HOME` use
  **priority-only** resolution and ignore the home role + disabled state, so they can make
  a working setup *look* broken (or vice-versa). Only the **physical remote Home button**
  and a **reboot** are ground truth. Don't trust injected key events for this.
- If full Home-button capture is required, the remaining lever is an **AccessibilityService**
  that watches for the stock launcher and covers it (Projectivy's method) or intercepts the
  Home key. This is invasive (needs the user to enable it under Accessibility) and
  device-specific.

---

## Home-button reliability: LMK + the TCL autostart lockdown (deep-dive)

Symptom: after ~a day the Home button stops opening this launcher; the accessibility
toggle still reads ON but does nothing until toggled off/on; reopening cold-starts.
This is **not a crash and not a memory leak in our code** — verified: `dumpsys dropbox
--print` had zero `data_app_crash` for us, the crash buffer was clean. Two real causes:

1. **Low-memory-killer reclaim.** This class of box is 2 GB RAM and already swaps into
   zram. The launcher's `am_proc_died` events fire with no crash record — it's the LMK
   evicting the backgrounded process while you use heavy apps. Originally the Compose UI
   **and** the `HomeRedirectService` shared one process, so the redirect died with the UI.
2. **TCL "TclAppBoot" autostart manager.** Logcat shows, on every rebind attempt:
   `ActivityManager: [TclAppBoot]: forbid bind service …HomeRedirectService` /
   `reason='callee_doesn't_have_OP_AUTO_START_permission', isAutoStart='true'`. TCL blocks
   the *automatic* (re)binding of our accessibility service unless the app holds the
   vendor appop `AUTO_START`. A **user-initiated** toggle via the on-screen Accessibility
   UI is allowed (that's why the in-app button works but a programmatic/boot bind doesn't).
   Crucially, **`AUTO_START` resets to `ignore` on every reboot** (confirmed across a
   reboot), and the device is not rooted, so there's no persistent allowlist we can write.

Fixes applied (see the manifest, `LauncherApp.kt`, `LauncherScreen.kt`):
- **`HomeRedirectService` runs in its own `android:process=":home"`.** Decoupled from the
  fat UI process, so LMK evicting the UI no longer kills the redirect. Validated: killed
  the UI process, `:home` survived and still relaunched us when the stock launcher
  appeared. This is the main fix — it kills the "resets after a day" (non-reboot) case.
- **Smaller footprint** so the process is a poorer LMK target: `LauncherApp` supplies a
  Coil `ImageLoader` with `allowRgb565(true)` (opaque wallpapers → 2 bytes/px) and a
  15% memory cache; the slideshow only advances while `RESUMED` (`repeatOnLifecycle`) so
  it stops decoding full-screen bitmaps in the background.
- **Grant the vendor appop** so auto-rebind is permitted (holds until the next reboot):
  `adb shell cmd appops set com.nihar.tvlauncher AUTO_START allow`.

Binding the service programmatically for testing needs a *transition* (setting the same
value is a no-op): `settings delete secure enabled_accessibility_services` then
`settings put secure enabled_accessibility_services <pkg>/<pkg>.HomeRedirectService` and
`settings put secure accessibility_enabled 1`. Confirm with
`dumpsys accessibility | grep 'Bound services'` and `ps -A | grep :home`.

**Reboot limitation (unavoidable on this box):** after a reboot `AUTO_START` is `ignore`
again and the boot-time auto-bind is forbidden, so the Home button falls back to the
stock launcher (graceful — no black screen, since we no longer disable the stock
launcher). Re-enable with the in-app "Set as default launcher" button (user-initiated
toggle, always allowed) or run `setup-home.ps1` / `setup-home.bat` from a PC. Full
zero-touch boot survival isn't achievable for a sideloaded app under TclAppBoot.

## Gotchas

- **Variable-font weight in Compose `res/font`.** Registering several `FontWeight`s that
  all point at ONE variable `.ttf` renders the file's default instance unless the resource
  `Font(resId, weight, …)` overload applies the `wght` variation (Compose UI 1.7+ does via
  its default `variationSettings`). If weights render identically on older Compose, bake a
  specific instance into its own font resource id. Elms Sans is a variable TTF used for the
  overlays via `Fonts.kt`.
- **Settings row readability under focus.** The tv-material3 `Surface` focus highlight turns
  a row's background light; rows with hardcoded light text become invisible when focused.
  Track focus (`onFocusChanged`) and switch text to a dark color when focused.
- **Do not tell the user a path exists based on a tool check** if your tools run in a
  sandbox separate from their machine — only the project folder may be shared. Anything the
  user must run has to live inside the project folder (that's why adb can be vendored to
  `./platform-tools/`).

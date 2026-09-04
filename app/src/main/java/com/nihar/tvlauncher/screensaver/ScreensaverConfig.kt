package com.nihar.tvlauncher.screensaver

/** Static configuration for the screensaver's image source. */
object ScreensaverConfig {

    /**
     * URL returning the slideshow image list as JSON. Leave blank to use ONLY the
     * photos bundled in `assets/screensaver/`.
     *
     * Accepted JSON shapes — a bare array of URLs:
     *   ["https://host/a.jpg", "https://host/b.jpg"]
     * or an object whose `images` are either URLs or per-photo records carrying the
     * EXIF the home-screen overlay needs (all three fields optional):
     *   { "images": [
     *       { "url": "https://host/a.jpg", "lat": 36.6239, "lon": -121.9403,
     *         "taken": "2023-07-22T18:36:04" }
     *   ] }
     *
     * Points at the companion photo repo, whose GitHub Action regenerates this file
     * whenever a photo is added or removed — so the TV's wallpapers update without
     * rebuilding the APK. See https://github.com/rahinroy/photos
     */
    const val MANIFEST_URL: String =
        "https://raw.githubusercontent.com/rahinroy/photos/main/screensaver.json"

    /** Folder inside `assets/` holding the bundled fallback photos (empty by default). */
    const val ASSET_DIR: String = "screensaver"

    /** Where the last successfully-fetched manifest is cached (in app filesDir). */
    const val CACHE_FILE: String = "screensaver_manifest.json"
}

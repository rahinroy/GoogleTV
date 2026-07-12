package com.nihar.tvlauncher.screensaver

/** Static configuration for the screensaver's image source. */
object ScreensaverConfig {

    /**
     * URL returning the slideshow image list as JSON. Leave blank to use ONLY the
     * photos bundled in `assets/screensaver/`.
     *
     * Accepted JSON shapes:
     *   ["https://host/a.jpg", "https://host/b.jpg"]
     *   { "images": ["https://host/a.jpg", "https://host/b.jpg"] }
     *
     * e.g. a GitHub raw URL you control:
     *   "https://raw.githubusercontent.com/<you>/<repo>/main/screensaver.json"
     */
    const val MANIFEST_URL: String = ""

    /** Folder inside `assets/` holding the bundled fallback photos. */
    const val ASSET_DIR: String = "screensaver"

    /** Where the last successfully-fetched manifest is cached (in app filesDir). */
    const val CACHE_FILE: String = "screensaver_manifest.json"
}

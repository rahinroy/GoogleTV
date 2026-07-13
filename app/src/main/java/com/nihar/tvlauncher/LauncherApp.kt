package com.nihar.tvlauncher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

/**
 * Application-wide Coil image loader, tuned for a memory-constrained TV (this box is a
 * 2 GB model already swapping into zram).
 *
 * The launcher's single biggest heap cost is full-screen wallpaper bitmaps. Two knobs
 * roughly halve that:
 *   - RGB_565 for opaque images (wallpapers have no alpha) — 2 bytes/px instead of 4.
 *     App icons keep alpha and stay ARGB_8888, so they're unaffected.
 *   - a smaller memory cache (15% of the app's memory-class vs Coil's default 25%).
 *
 * A smaller footprint keeps this process a less attractive target for the low-memory
 * killer, which was the root cause of the Home-button reverting: the LMK reclaimed our
 * process and took the accessibility redirect service down with it. (The service now
 * also runs in its own :home process — see the manifest — so the two are decoupled.)
 *
 * Coil picks this up automatically because the Application implements ImageLoaderFactory.
 * It is only ever built in the main UI process; the :home service process never touches
 * Coil, so newImageLoader() is never called there.
 */
class LauncherApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .allowRgb565(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .build()
}

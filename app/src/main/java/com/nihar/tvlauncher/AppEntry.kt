package com.nihar.tvlauncher

import androidx.compose.ui.graphics.ImageBitmap

/** A single launchable app shown in the drawer. */
data class AppEntry(
    val packageName: String,
    val label: String,
    /**
     * Preloaded artwork, already rasterized to an [ImageBitmap] so the grid can draw
     * it synchronously with zero per-scroll decoding. It's the app's wide TV banner
     * when available, otherwise its square icon.
     */
    val image: ImageBitmap,
    /** True when [image] is a wide banner (draw cropped) vs. a square icon (draw fit). */
    val isBanner: Boolean,
)

/** One row in the app-visibility settings screen. */
data class SettingsAppRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val hidden: Boolean,
    val isTvApp: Boolean,
)

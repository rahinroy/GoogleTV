package com.nihar.tvlauncher

import android.content.Context

/**
 * Persisted launcher settings: which apps are hidden, the custom tile order, and the
 * wallpaper photo interval.
 */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    // --- Hidden apps ---------------------------------------------------------------
    fun hidden(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()

    fun setHidden(packageName: String, hidden: Boolean) {
        val updated = hidden().toMutableSet()
        if (hidden) updated.add(packageName) else updated.remove(packageName)
        prefs.edit().putStringSet(KEY_HIDDEN, updated).apply()
    }

    // --- Custom tile order (list of package names) ---------------------------------
    fun order(): List<String> =
        prefs.getString(KEY_ORDER, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun setOrder(order: List<String>) {
        prefs.edit().putString(KEY_ORDER, order.joinToString("\n")).apply()
    }

    // --- Photo interval ------------------------------------------------------------
    fun photoIntervalSeconds(): Int =
        prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL).coerceIn(MIN_INTERVAL, MAX_INTERVAL)

    fun setPhotoIntervalSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_INTERVAL, seconds.coerceIn(MIN_INTERVAL, MAX_INTERVAL)).apply()
    }

    companion object {
        const val DEFAULT_INTERVAL = 9
        const val MIN_INTERVAL = 3
        const val MAX_INTERVAL = 120
        const val INTERVAL_STEP = 3

        private const val KEY_HIDDEN = "hidden_packages"
        private const val KEY_ORDER = "app_order"
        private const val KEY_INTERVAL = "photo_interval_seconds"
    }
}

package com.nihar.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Reads the set of launchable apps from [PackageManager].
 *
 * We prefer each app's TV (leanback) entry point but fall back to its regular
 * launcher entry so sideloaded phone apps still show up and are launchable.
 */
class AppRepository(context: Context) {

    private val pm: PackageManager = context.packageManager
    private val selfPackage: String = context.packageName

    /** A launchable app before any artwork has been rasterized. */
    private data class RawApp(val packageName: String, val label: String, val isTv: Boolean, val info: ResolveInfo)

    /** Query the launchable apps (deduped, sorted) without touching artwork. Cheap. */
    private suspend fun queryRaw(): List<RawApp> = withContext(Dispatchers.IO) {
        val tvIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phoneIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val tvApps = pm.queryIntentActivities(tvIntent, 0)
        val phoneApps = pm.queryIntentActivities(phoneIntent, 0)

        // Package -> entry. TV entries win over phone entries for the same package.
        val byPackage = LinkedHashMap<String, RawApp>()

        fun add(info: ResolveInfo, isTv: Boolean) {
            val pkg = info.activityInfo.packageName
            if (pkg == selfPackage) return                 // don't list ourselves
            if (!isTv && byPackage.containsKey(pkg)) return // keep the TV entry
            val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
            byPackage[pkg] = RawApp(pkg, label, isTv, info)
        }

        tvApps.forEach { add(it, isTv = true) }
        phoneApps.forEach { add(it, isTv = false) }

        byPackage.values.sortedBy { it.label.lowercase() }
    }

    /** Sort by the user's custom [order] (packages listed first, in that order); apps
     *  not in the order fall to the end alphabetically (so newly-installed apps appear). */
    private fun List<RawApp>.applyOrder(order: List<String>): List<RawApp> {
        val orderIndex = order.withIndex().associate { (i, pkg) -> pkg to i }
        return sortedWith(
            compareBy({ orderIndex[it.packageName] ?: Int.MAX_VALUE }, { it.label.lowercase() }),
        )
    }

    /**
     * The VISIBLE drawer list: applies the custom [order], skips [hidden] packages, then
     * rasterizes artwork for only the survivors. Hidden apps cost nothing.
     */
    suspend fun loadApps(hidden: Set<String>, order: List<String>): List<AppEntry> = withContext(Dispatchers.IO) {
        queryRaw()
            .applyOrder(order)
            .filter { it.packageName !in hidden }
            .map { raw ->
                val banner = if (raw.isTv) {
                    runCatching { raw.info.activityInfo.loadBanner(pm) }.getOrNull()
                } else null
                val artwork = banner ?: raw.info.loadIcon(pm)
                AppEntry(
                    packageName = raw.packageName,
                    label = raw.label,
                    image = artwork.toImageBitmap(),
                    isBanner = banner != null,
                )
            }
    }

    /**
     * ALL installed launchable apps for the settings screen (in custom [order]), each
     * tagged with whether it's hidden. Small square icons (settings is opened rarely).
     */
    suspend fun loadAllForSettings(hidden: Set<String>, order: List<String>): List<SettingsAppRow> = withContext(Dispatchers.IO) {
        queryRaw().applyOrder(order).map { raw ->
            SettingsAppRow(
                packageName = raw.packageName,
                label = raw.label,
                icon = raw.info.loadIcon(pm).toImageBitmap(maxDim = 96),
                hidden = raw.packageName in hidden,
                isTvApp = raw.isTv,
            )
        }
    }

    /** Build the intent that launches [packageName], preferring its TV entry point. */
    fun launchIntentFor(packageName: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)

    /**
     * Rasterize a drawable to an [ImageBitmap], tuned for cheap, jank-free drawing:
     *  - downsampled to at most [maxDim] px (dock tiles are small; huge textures waste
     *    upload bandwidth and fill-rate),
     *  - promoted to a `HARDWARE` bitmap so it lives in GPU memory and is never
     *    re-uploaded per frame or evicted the way software-bitmap textures are (this is
     *    what kept the belt from ever hitching). Falls back to a software bitmap with a
     *    GPU-upload hint if a hardware bitmap can't be allocated.
     */
    private fun Drawable.toImageBitmap(maxDim: Int = 360): ImageBitmap {
        val iw = if (intrinsicWidth > 0) intrinsicWidth else maxDim
        val ih = if (intrinsicHeight > 0) intrinsicHeight else maxDim
        val scale = minOf(1f, maxDim.toFloat() / maxOf(iw, ih).toFloat())
        val w = (iw * scale).roundToInt().coerceAtLeast(1)
        val h = (ih * scale).roundToInt().coerceAtLeast(1)

        val software = toBitmap(w, h, Bitmap.Config.ARGB_8888)
        val hardware = runCatching { software.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        return if (hardware != null) {
            software.recycle()
            hardware.asImageBitmap()
        } else {
            software.prepareToDraw()
            software.asImageBitmap()
        }
    }
}

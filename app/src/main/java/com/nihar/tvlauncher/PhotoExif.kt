package com.nihar.tvlauncher

import android.content.Context
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where/when a wallpaper photo was taken, derived from its EXIF metadata. */
data class PhotoInfo(val place: String?, val date: String?) {
    val hasAny: Boolean get() = !place.isNullOrBlank() || !date.isNullOrBlank()
}

private val infoCache = HashMap<String, PhotoInfo>()

/**
 * Reads EXIF from a bundled-asset wallpaper [model] (a `file:///android_asset/...` URI):
 *  - GPS → reverse-geocoded to city / region / country (best available),
 *  - DateTimeOriginal → "Month D, YYYY".
 * Remote (http) models are skipped. Cached per model. Never throws.
 */
suspend fun readPhotoInfo(context: Context, model: String): PhotoInfo = withContext(Dispatchers.IO) {
    infoCache[model]?.let { return@withContext it }

    val assetPrefix = "file:///android_asset/"
    if (!model.startsWith(assetPrefix)) return@withContext PhotoInfo(null, null)
    val assetPath = model.removePrefix(assetPrefix)

    val info = runCatching {
        context.assets.open(assetPath).use { stream ->
            val exif = ExifInterface(stream)
            val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            PhotoInfo(
                place = placeFor(context, exif),
                date = formatDate(date),
            )
        }
    }.getOrDefault(PhotoInfo(null, null))

    infoCache[model] = info
    info
}

private fun placeFor(context: Context, exif: ExifInterface): String? {
    val latLong = exif.latLong ?: return null // DoubleArray[lat, lng] or null
    if (!Geocoder.isPresent()) return null
    return runCatching {
        @Suppress("DEPRECATION")
        val addresses = Geocoder(context, Locale.getDefault())
            .getFromLocation(latLong[0], latLong[1], 1)
        val a = addresses?.firstOrNull() ?: return null
        // Best available granularity: city → region → country.
        (a.locality ?: a.subAdminArea ?: a.adminArea)?.let { local ->
            a.countryName?.let { "$local, $it" } ?: local
        } ?: a.countryName
    }.getOrNull()
}

private fun formatDate(exifDate: String?): String? {
    if (exifDate.isNullOrBlank()) return null
    return runCatching {
        val parsed = LocalDateTime.parse(
            exifDate,
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"),
        )
        parsed.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    }.getOrNull()
}

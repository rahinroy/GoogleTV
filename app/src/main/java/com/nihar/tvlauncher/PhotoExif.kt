package com.nihar.tvlauncher

import android.content.Context
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import com.nihar.tvlauncher.screensaver.ImageManifestRepository
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

private const val ASSET_PREFIX = "file:///android_asset/"

/**
 * Resolves the place/date overlay for a wallpaper [model]:
 *  - GPS → reverse-geocoded to city / region / country (best available),
 *  - capture time → "Month D, YYYY".
 *
 * Bundled assets (`file:///android_asset/...`) are read straight out of the APK.
 * Remote photos can't be — Coil owns those bytes — so their EXIF comes from the
 * manifest via [ImageManifestRepository.metaFor]. Cached per model. Never throws.
 */
suspend fun readPhotoInfo(context: Context, model: String): PhotoInfo = withContext(Dispatchers.IO) {
    infoCache[model]?.let { return@withContext it }

    val info = if (model.startsWith(ASSET_PREFIX)) {
        assetInfo(context, model.removePrefix(ASSET_PREFIX))
    } else {
        ImageManifestRepository.metaFor(model)?.let { meta ->
            PhotoInfo(
                place = placeFor(context, meta.lat, meta.lon),
                date = formatDate(meta.taken, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            )
        } ?: PhotoInfo(null, null)
    }

    infoCache[model] = info
    info
}

private fun assetInfo(context: Context, assetPath: String): PhotoInfo =
    runCatching {
        context.assets.open(assetPath).use { stream ->
            val exif = ExifInterface(stream)
            val latLong = exif.latLong // DoubleArray[lat, lng] or null
            val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            PhotoInfo(
                place = placeFor(context, latLong?.getOrNull(0), latLong?.getOrNull(1)),
                date = formatDate(date, EXIF_DATE),
            )
        }
    }.getOrDefault(PhotoInfo(null, null))

private fun placeFor(context: Context, lat: Double?, lon: Double?): String? {
    if (lat == null || lon == null) return null
    if (!Geocoder.isPresent()) return null
    return runCatching {
        @Suppress("DEPRECATION")
        val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
        val a = addresses?.firstOrNull() ?: return null
        // Best available granularity: city → region → country.
        (a.locality ?: a.subAdminArea ?: a.adminArea)?.let { local ->
            a.countryName?.let { "$local, $it" } ?: local
        } ?: a.countryName
    }.getOrNull()
}

/** EXIF's own date format, e.g. "2023:07:22 18:36:04". */
private val EXIF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

private fun formatDate(raw: String?, parser: DateTimeFormatter): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        LocalDateTime.parse(raw.trim(), parser)
            .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    }.getOrNull()
}

package com.nihar.tvlauncher.screensaver

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** EXIF a remote manifest carries for one photo. All fields are optional. */
data class PhotoMeta(val lat: Double?, val lon: Double?, val taken: String?)

/**
 * Resolves the list of screensaver image "models" (strings Coil can load).
 *
 * Precedence:
 *   1. Remote manifest at [ScreensaverConfig.MANIFEST_URL], if set and reachable.
 *   2. Last-cached remote manifest (so it still works offline).
 *   3. Photos bundled in `assets/screensaver/`.
 *
 * Bundled assets are returned as `file:///android_asset/...` URIs; remote images
 * as their http(s) URLs. Coil loads and disk-caches both.
 *
 * A remote photo's EXIF can't be read from the file (Coil owns those bytes), so the
 * manifest may carry it per entry; [metaFor] hands it to the home-screen overlay.
 */
object ImageManifestRepository {

    /** Manifest-supplied EXIF, keyed by image URL. Filled as manifests are parsed. */
    private val metaByUrl = ConcurrentHashMap<String, PhotoMeta>()

    /** EXIF the manifest declared for [url], or null for bundled/metadata-less photos. */
    fun metaFor(url: String): PhotoMeta? = metaByUrl[url]

    suspend fun resolveModels(context: Context): List<String> = withContext(Dispatchers.IO) {
        val assets = assetModels(context)
        val url = ScreensaverConfig.MANIFEST_URL
        if (url.isBlank()) return@withContext assets

        val body = runCatching { fetchManifest(url) }.getOrNull()
        if (body != null) {
            val remote = parse(body)
            if (remote.isEmpty()) return@withContext assets
            // Cache the raw body, not just the URLs, so the EXIF survives offline too.
            cacheManifest(context, body)
            return@withContext remote
        }
        // Network failed — fall back to the last good manifest, then to assets.
        readCachedManifest(context).ifEmpty { assets }
    }

    /** Fast, local-only list of the bundled photos. Used for instant first paint. */
    fun assetModels(context: Context): List<String> =
        context.assets.list(ScreensaverConfig.ASSET_DIR).orEmpty()
            .filter { it.isImageName() }
            .sorted()
            .map { "file:///android_asset/${ScreensaverConfig.ASSET_DIR}/$it" }

    private fun fetchManifest(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) return null
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Returns the image URLs, recording any per-entry EXIF into [metaByUrl] on the way.
     * Entries may be plain URL strings or `{ "url", "lat", "lon", "taken" }` objects.
     */
    private fun parse(json: String): List<String> {
        val trimmed = json.trim()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).optJSONArray("images") ?: JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                when (val entry = array.opt(i)) {
                    is JSONObject -> {
                        val url = entry.optString("url").takeIf { it.isNotBlank() } ?: continue
                        metaByUrl[url] = PhotoMeta(
                            lat = entry.optDoubleOrNull("lat"),
                            lon = entry.optDoubleOrNull("lon"),
                            taken = entry.optString("taken").takeIf { it.isNotBlank() },
                        )
                        add(url)
                    }
                    is String -> entry.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun cacheFile(context: Context) = File(context.filesDir, ScreensaverConfig.CACHE_FILE)

    private fun cacheManifest(context: Context, body: String) {
        runCatching { cacheFile(context).writeText(body) }
    }

    private fun readCachedManifest(context: Context): List<String> {
        val file = cacheFile(context)
        if (!file.exists()) return emptyList()
        return runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    private fun String.isImageName(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
    }
}
